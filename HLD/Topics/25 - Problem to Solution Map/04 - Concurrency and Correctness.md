---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Concurrency and Correctness

> [!abstract] What this is
> Problems where **two things happen at once and the result is wrong**: lost updates, double booking, holds while a human pays, deadlocks, write skew and phantoms, picking an isolation level, locks and leases across machines, claiming jobs, and duplicates from retries. Hot rows (a throughput problem caused by contention) are in [[03 - Scaling Reads Writes and Data]]; transactions spanning services are in [[06 - Async Messaging and Workflows]].

---

## The Ladder

### "Two requests can act on the same thing at once": which tool?

- **Rule:** use the **cheapest tool that makes the database decide**, and climb only when it can't express the rule.

| Rung | Tool | Costs you | Pick when |
|---|---|---|---|
| 1 | **Unique constraint** (`INSERT … ON CONFLICT`) | Needs a natural unique key | One-per-thing rules (one booking per seat, one account per email) |
| 2 | **Atomic conditional `UPDATE … WHERE`** | Only for expressible updates | Counters, stock, claiming a row |
| 3 | **Optimistic `version` column** (CAS) | Retries under contention | Profiles, carts, issues; conflicts are rare |
| 4 | **Pessimistic `SELECT … FOR UPDATE`** | Blocking; deadlock risk; milliseconds only | Short transaction that must read, branch, then write |
| 5 | **State machine + expiry column** (`held_until`) | A sweeper / expiry logic | The "lock" must span time or a human |
| 6 | **Redis `SET NX EX` + fencing token** | No safety without fencing | Only when the critical section isn't inside one DB |

- **Correctness lives in the database**, not in an `if` in application code.
- **Depth:** [[14 - Coordination and Concurrency]], [[16 - Estimation and Decision Cheat Sheet]]

---

## Lost Updates and Races

### Lost update: read, modify in the app, write back

- **Signal:** two tabs both read `qty = 5` and both write `4`; stock drifts.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Atomic SQL** | `UPDATE … SET qty = qty - 1 WHERE qty > 0` | Simple arithmetic only | Default for counters and stock |
| **Optimistic version** | `WHERE id = ? AND version = 3`, bump to 4; 0 rows → 409 or retry | Retries | Richer entities, low contention |
| **`SELECT … FOR UPDATE`** | Exclusive row lock from read to commit | Writers queue; deadlock risk | Must read, decide, then write, under high contention |
| **Redis `WATCH`/Lua, ES `if_seq_no`, Dynamo condition** | The store's own compare-and-set | Retries | Same problem in those stores |

- **Default:** atomic SQL if the change is arithmetic; optimistic version otherwise.
- **Depth:** [[03 - Concurrency Control]], [[14 - Coordination and Concurrency]]

### Check-then-act race: two users click the last seat

- **Signal:** `SELECT status` then `UPDATE` in the app lets both see "available".

| Option | How it works | Pick when |
|---|---|---|
| **Condition inside one `UPDATE … WHERE`** | Check and claim are one step; a blocked writer re-checks the `WHERE` after the first commits and updates 0 rows | Any claim |
| **Unique constraint** | Second insert fails | A natural unique key exists |
| **Pessimistic lock** | Lock, then decide | Complex decisions under contention |

- **Depth:** [[08 - Reliability]], [[14 - Coordination and Concurrency]]

### Reserve something while a human pays (seat, ticket, inventory hold)

- **Signal:** checkout takes 30 seconds to 10 minutes; someone proposes a 10-minute Redis lock or an open transaction.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Row state + expiry** | `UPDATE seats SET status='held', held_by=?, hold_until=now()+10m WHERE seat_id=? AND (available OR expired)`; 1 row = you got it | Final "book" must re-check holder and expiry; refund if lapsed | Default |
| **Sweeper** | Background job releases expired holds | A job to run | When expired holds must visibly free up |
| ~~Redis lock for 10 minutes~~ | | TTL is a guess, lost on failover, invisible | Don't |
| ~~`FOR UPDATE` held during payment~~ | | Blocks everyone, ties up a connection | Don't |

- **Key idea:** the row lock lasts milliseconds; the 10-minute hold is **data** on the row, not a lock.
- **Default:** "free → held (with `hold_until`) → sold, as a compare-and-set in the database."
- **Depth:** [[08 - Reliability]], [[14 - Coordination and Concurrency]], [[06 - Seat Booking]]

### Holding a DB lock or transaction across a third-party call

- **Fix:** commit, make the call, then update in a new transaction (with idempotency on the call). Never hold row locks or a transaction open across network calls or user think-time.
- **Depth:** [[03 - Concurrency Control]], [[16 - Estimation and Decision Cheat Sheet]]

---

## Deadlocks

### Two transactions each hold what the other needs

- **Signal:** `deadlock detected`; T1 locks seat 1 then 2, T2 locks 2 then 1.

| Option | Costs you | Pick when |
|---|---|---|
| **Lock in sorted id order** | Discipline in every code path | Always; makes cycles impossible |
| **Tiny transactions** | More orchestration | Always |
| **`lock_timeout`** | Fails instead of waiting | Backstop, not a substitute |
| **Retry the whole transaction on deadlock error** | Wasted work | Always alongside the above |
| **Conservative 2PL** (all locks up front) | Impractical; you rarely know rows in advance | Textbook only |

- **Depth:** [[03 - Concurrency Control]]

---

## Isolation Anomalies

### Write skew: a rule across rows breaks ("at least one doctor on call")

- **Signal:** each transaction reads a set, updates a *different* row; both commit; the invariant breaks. Allowed under snapshot isolation / Repeatable Read.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Serializable** (Postgres SSI) for that transaction | Detects the dangerous pattern, aborts one with `40001` | Retries, tracking overhead | The rule is hard to express as one row and you can retry |
| **`SELECT … FOR UPDATE` on the rows the rule reads** | Blocks the second transaction | Every enforcing path must use it | You want blocking, not aborts |
| **Lock one "roster" / parent row** | Acts as a mutex for the rule | Serialises that rule | The rule spans many rows or involves inserts |
| **Constraint** (`CHECK`, unique, exclusion) | Database enforces it | Rarely expressible for set rules | When it fits |

- **Depth:** [[04 - Isolation and Anomalies]]

### Range invariant broken by phantom inserts ("no overlapping bookings")

- **Signal:** two transactions check a range, both see nothing, both insert.

| Option | Pick when |
|---|---|
| **Serializable** | Range invariants in Postgres |
| **Exclusion constraint** (GiST) | Overlap rules on ranges in Postgres |
| **Locking reads** (InnoDB next-key/gap locks via `FOR UPDATE`) | InnoDB |
| **Roster/parent row lock** | You can't lock rows that don't exist yet |

- **Watch out:** Repeatable Read stops phantoms for *repeated plain reads* in Postgres and InnoDB, not for range invariants (and InnoDB mixes snapshot and current reads).
- **Depth:** [[04 - Isolation and Anomalies]]

### Picking an isolation level

| Level | Still allows | Pick when |
|---|---|---|
| **Read Committed** (Postgres default) | Non-repeatable reads, phantoms, write skew | Most CRUD |
| **Repeatable Read** | Write skew (and range-invariant phantoms) | Reports that must not see a row change mid-transaction |
| **Serializable** | Nothing; aborts instead | Money invariants, set rules |

- **Rules:** raise isolation **per transaction**, not globally ("Serializable everywhere" means an abort storm); wrap Serializable transactions in a retry loop on `40001`; strict 2PL / Read Committed is what prevents dirty reads and cascading rollbacks.
- **Depth:** [[04 - Isolation and Anomalies]], [[03 - Concurrency Control]]

---

## Locks Across Machines

### A critical section spans machines and no DB row can guard it

- **Signal:** cron on several boxes, one indexer per shard, a cache refill.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Redis lock** | `SET key token NX EX 30`; release with a Lua compare-and-delete | No safety without fencing; can vanish on failover | Cron, stampede refill, best-effort exclusion |
| **ZooKeeper / etcd / Consul** | Ephemeral node or lease + watch | Run a consensus cluster | The lock *is* the product (primary election) |
| **Postgres advisory lock** | `pg_advisory_lock` | Invisible state | Rare; prefer a visible row |
| ~~Redlock~~ | | Controversial | Name-drop only, never for money |

- **Depth:** [[14 - Coordination and Concurrency]], [[21 - Redis]]

### A lock holder pauses past its TTL and two holders write

- **Signal:** GC pause or long work; the lock expired; the old holder wakes up and writes.

| Option | How it works | Pick when |
|---|---|---|
| **Fencing token** | Each grant carries an increasing number; the storage rejects lower numbers | Money, anything correctness-critical |
| **Lease with heartbeat renewal** | Stop writing if you can't renew | Long-held roles |
| **Shorter work** | Finish well inside the TTL | When possible |

- **Depth:** [[14 - Coordination and Concurrency]], [[17 - Consistency Time and Consensus]]

### Other lock bugs

| Bug | Fix |
|---|---|
| **Holder dies, lock stranded forever** | Always set a TTL; for inventory use `hold_until` + sweeper |
| **Blind `DEL` removes someone else's lock** | Random token value + compare-and-delete in Lua |
| **Clocks disagree on expiry** | Use Redis server-side TTL, never app clocks |
| **Lock key evicted under memory pressure** | `volatile-*` eviction with TTLs only on cache keys, or a separate Redis for coordination |
| **Two independent Redis nodes both grant** | Don't build the lock service from independent nodes; put truth in the DB with fencing |

- **Depth:** [[14 - Coordination and Concurrency]], [[21 - Redis]]

### Lock or lease?

| Option | Pick when |
|---|---|
| **Lock** (acquire, work, release) | One-off critical section: one cron run, one cache refill |
| **Lease** (renewed by heartbeat, lost if not renewed) | Ongoing ownership: leader, shard owner, partition leader |

- **Depth:** [[14 - Coordination and Concurrency]]

### At most N workers may use a resource

- **Option:** a **semaphore** (capped counter or token list in Redis). A lock is the N = 1 case; a rate limit caps per *time*, not concurrency.
- **Depth:** [[14 - Coordination and Concurrency]]

---

## Claiming Work

### Many workers polling one jobs table grab the same job

- **Fix:** `SELECT … FOR UPDATE SKIP LOCKED LIMIT 1`: each worker skips rows another has locked. Pair with a lease (`claimed_until`) so a dead worker's job returns to `pending` and someone else claims it.
- **Depth:** [[14 - Coordination and Concurrency]]

### Only one instance should run a scheduled job

| Option | Costs you | Pick when |
|---|---|---|
| **Cloud scheduler hitting one endpoint** | Platform-dependent | Available; cheapest |
| **Jobs table + `SKIP LOCKED`** | DB-backed | Many jobs |
| **Redis lock + token** | No fencing | Single job, Redis already present |
| **Lease in etcd/ZooKeeper + fencing** | Run the cluster | Already running one |

- **Depth:** [[14 - Coordination and Concurrency]]

### Wait hours before acting ("remind in 6 hours")

| Option | Pick when |
|---|---|
| **Durable timer row + poller with `SKIP LOCKED`** | Default |
| **Redis sorted set** (score = due time) | Redis already in use, loss tolerable |
| **Workflow engine timer** (Temporal, Step Functions) | It's part of a saga |
| ~~`sleep` in a pod or consumer~~ | Never |

- **Depth:** [[16 - Estimation and Decision Cheat Sheet]], [[21 - Redis]]

---

## Duplicates and Idempotency

### User double-clicks Pay / a retried POST charges twice

- **Fix:** an **`Idempotency-Key`** on every mutating API: the client generates one per logical operation; the server stores key → response under a unique constraint and returns the stored result on a repeat.
- **Depth:** [[08 - Reliability]], [[03 - Networking and APIs]]

### A consumer applies a side effect twice (at-least-once delivery)

| Option | How it works | Pick when |
|---|---|---|
| **Dedupe on `event_id`** | `INSERT … ON CONFLICT DO NOTHING` | Any event with a stable id |
| **Inbox table** | Insert `event_id` in the **same transaction** as the side effect | Consumers with DB side effects |
| **Upsert by natural key** | A repeat sets the same value | The effect is "set", not "add" |
| **State check** | Already `PAID`? no-op | Webhooks, state machines |

- **Depth:** [[07 - Async Messaging]], [[14 - Coordination and Concurrency]]

Exactly-once across systems is in [[06 - Async Messaging and Workflows]]; webhook dedupe and signatures are in [[09 - APIs Networking and Realtime]].

## Related Notes

- [[25 - Problem to Solution Map]]
- [[03 - Concurrency Control]] · [[04 - Isolation and Anomalies]] · [[14 - Coordination and Concurrency]] · [[08 - Reliability]]
