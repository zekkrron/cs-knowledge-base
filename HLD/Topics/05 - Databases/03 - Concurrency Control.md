---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
---
# Concurrency Control

> [!abstract]
> - How thousands of sessions don’t corrupt one row
> - Two families: **locks (2PL)** and **versions (MVCC)**
> - Modern Postgres / InnoDB: MVCC so **readers don’t block writers**
> - You still lock on **write**; you still deadlock; you still lose updates if you read-then-write in the app
> - App recipes (`SKIP LOCKED`, Redis, fencing): [[14 - Coordination and Concurrency]]
> - Isolation *levels*: [[04 - Isolation and Anomalies]]

---

## Lock-based: two-phase locking (2PL)

### The protocol

- **Growing phase** — acquire locks, never release
- **Shrinking phase** — release locks, never acquire
- Once you have dropped any lock, you may not take another
- This is what makes a schedule **conflict-serializable** (the textbook guarantee)

### Strict 2PL (what engines actually do)

- Hold **exclusive** locks until **commit / abort**
- Stops another txn reading your dirty write and then you rolling back
- Growing/shrinking is still the idea; the shrink happens at the end

### Lock modes

| Mode | Who else can hold | Use |
|---|---|---|
| **Shared (S)** | Other S | Read |
| **Exclusive (X)** | Nobody | Write |
| **Intention shared (IS)** | | “I will S-lock a row in this table” |
| **Intention exclusive (IX)** | | “I will X-lock a row in this table” |

- Intention locks sit on the **table** (or a page) so the engine can lock the whole table without inspecting every row
    + `LOCK TABLE` in X vs a row UPDATE in IX+X
    + without IS/IX you’d walk every row to see if a table lock is safe

### Granularity

- Row < page < table < database
- Finer = more concurrency, more lock memory, more deadlock surface
- Coarser = simple, more blocking
- Interview default: **row locks** for OLTP

---

## MVCC

### The point

- A write creates a **new version** of the row (or a new tuple)
- A reader sees a version that was **committed before its snapshot**
- Readers do **not** take S-locks on every row they read (Postgres)
    + “readers don’t block writers, writers don’t block readers”
    + writers still conflict with writers on the same row

### Postgres picture (enough to say)

- Every txn has an **XID**
- A heap tuple has `xmin` (creator) and `xmax` (deleter / next writer)
- Visibility
    + you see a tuple if `xmin` committed before your snapshot and `xmax` is not a committed-before-you delete
    + this **is** the isolation level’s snapshot — [[04 - Isolation and Anomalies]]
- `UPDATE` = insert a new tuple + set `xmax` on the old one
    + old versions sit until **vacuum**
    + that is the bloat sentence

### InnoDB picture (enough to say)

- Clustered row + **undo log** versions
- A reader walks undo if the current row is too new for its snapshot
- Same idea: versions, not S-locks on reads

> [!tip] The one-liner if they poke internals
> - “Postgres uses MVCC so readers don’t block writers”
> - You do **not** draw the clog / freeze map unless they are a DB team

---

## Lost update (the one you will actually hit)

- Two tabs read `qty = 5`
- Both write `qty = 4`
- Last commit wins; one sale vanished

### Fixes (pick one, say why)

| Fix | How | When |
|---|---|---|
| Atomic SQL | `UPDATE … SET qty = qty - 1 WHERE qty > 0` | Default for counters |
| Optimistic `version` | `UPDATE … WHERE id=? AND version=3` → 0 rows = 409 | Profiles, stock, issues |
| `SELECT FOR UPDATE` | Pessimistic row X-lock until commit | Short txn, you must **read then branch** |

- Do **not** `SELECT` in the app, subtract, `UPDATE` with no predicate
- Depth of which tool in an HLD: [[14 - Coordination and Concurrency]]

---

## Deadlock

### How you get one

- T1: lock seat 1, wait for seat 2
- T2: lock seat 2, wait for seat 1
- Cycle in the **wait-for graph**

### What the engine does

- Periodically build / walk the wait-for graph
- Find a cycle → pick a **victim** (cheapest to undo: youngest, least undo)
- `ERROR: deadlock detected` → that txn **rolls back**
- The app **retries** the whole txn

### Prevention you actually do

- Lock rows in a **sorted id order**
    + both txns lock seat 1 then seat 2
    + no cycle
- Keep the txn **tiny**
    + never hold `FOR UPDATE` across a PSP call
- Timeouts
    + `lock_timeout` so you fail instead of sit
    + not a substitute for sort-order

> [!danger] Hold a row lock for 10 minutes
> - That’s not 2PL, that’s a **product bug**
> - Booking uses **state + TTL**, not an open transaction
> - [[06 - Seat Booking]] · [[14 - Coordination and Concurrency]]

---

## Hot row

- Everyone updates the **same** counter / the same seat-map row
- The lock (or the MVCC write conflict) **is** the bottleneck
- Fixes
    + shard the counter (10 buckets)
    + Redis `INCR` if you can reconcile
    + append events and sum
- Same family as a Kafka hot partition — one key, one lane
    + [[04 - Producer Mechanics and Delivery Guarantees]]
    + [[09 - Sharding]]

---

## Related Notes

- [[README]]
- [[04 - Isolation and Anomalies]]
- [[14 - Coordination and Concurrency]]
- [[01 - Storage Engine and Disk IO]] — steal means uncommitted pages can hit disk; undo exists
- [[11 - Interview Questions]]
