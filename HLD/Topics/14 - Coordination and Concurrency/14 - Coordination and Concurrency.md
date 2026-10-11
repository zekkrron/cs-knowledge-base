---
tags: [hld/topics, status/draft]
created: 2026-09-18
updated: 2026-09-29
---
# Coordination and Concurrency

> [!abstract] What this is
> "Don't let two people take the same seat, run the same job, or decrement the same stock." That's this note: locking, leasing, "only one winner," and database concurrency.
>
> The key decision: a **distributed lock is the last tool**, not the first. Prefer a DB constraint or a row state.

---

## Why It Exists

Any time two requests can act on the same thing at once, something has to decide who wins. Two users click the last seat. Two cron boxes both think it's time for the nightly bill run. Two browser tabs read `qty=5` and both write `4`. Without a rule, you double-sell, double-run or lose updates.

This note is about mutual exclusion, not geography. Geo-radius and "nearby users" are queries and live in [[09 - Storage Search and Geo]].

---

## The Decision Tree

Most concurrency problems are solved by the database you already have. Walk down this tree and stop at the first option that fits; say it out loud in the interview.

```mermaid
flowchart TD
    Q[Need mutual exclusion?] --> U{Natural unique key?}
    U -->|yes| C[UNIQUE / INSERT ON CONFLICT]
    U -->|no| S{Short, in one DB?}
    S -->|yes| R[Row lock or version column]
    S -->|no| L{Must span services?}
    L -->|prefer not| St[State machine + TTL column]
    L -->|forced| D[Redis SET NX EX + fencing]
```

1. **Unique constraint.** Two inserts of the same `seat_id` into a booking table: one fails. This *is* a lock. Use it.
2. **Optimistic version.** `UPDATE … WHERE id=? AND version=3`. The lost update dies. Default for stock and profiles.
3. **Pessimistic row lock.** `SELECT FOR UPDATE` in a transaction. Hold it for **milliseconds**, not while the user stares at a payment page.
4. **DB state + TTL.** `status='held', hold_until=now()+10m`. A sweeper releases expired holds. Booking, ride offers, OTPs.
5. **Redis lock.** Only when the critical section isn't inside one database: a cron across boxes, a cache stampede, "exactly one worker."

> [!warning] "I'll take a Redis lock for 10 minutes while they pay" is how you strand inventory when the process dies and you forgot expiry, or how you **double-sell** when expiry fires and the old holder still writes. See fencing below.

---

## Database Concurrency

The basics are in [[05 - Databases]]. These are the pieces that actually get follow-up questions, each with its app-level fix. MVCC, WAL and isolation depth are in [[05 - Databases/README]]; this section is the recipe. If they only poke, "Postgres uses MVCC so readers don't block writers" is still the one sentence.

**Lost update.** Two tabs read `qty=5`, both write `4`. Fix it with an atomic update `qty = qty - 1 WHERE qty > 0`, a `version` compare-and-set, or `FOR UPDATE`.

**`SELECT FOR UPDATE`.** Pessimistic: nobody else can take that row until you `COMMIT`. It can **deadlock** if transaction A locks seat 1 then seat 2 while B locks 2 then 1. Fix: always lock in **sorted ID order**, and keep the transaction tiny.

**`SKIP LOCKED`.** The job-queue trick. Workers run `SELECT … FOR UPDATE SKIP LOCKED LIMIT 1`, so locked rows are skipped instead of waited on. This is how many pollers share one `jobs` table without Redis. Name it when asked "how do workers not steal the same job."

**Write skew.** One example is enough. Under snapshot isolation, two doctors both see "on-call count = 1" and both go off duty. Each update is valid on its own, but the invariant "at least one on call" dies. Fix: **Serializable** isolation, or an explicit constraint or lock on the invariant. SSI internals won't be examined.

**Advisory locks.** Postgres `pg_advisory_lock` gives app-level locks inside the DB. Rare in HLD; prefer a row you can see.

**Hot row.** Everyone updates the same counter, so the row lock *is* the bottleneck. Fix: shard the counter, use Redis `INCR`, or append events and sum them.

A hot row occurs when a massive, highly concurrent volume of traffic attempts to update the exact same database record simultaneously—such as tens of thousands of users instantaneously clicking "Like" on a viral celebrity post, or thousands of traders bidding on the same stock.

Because relational databases use row-level locking (like the `FOR UPDATE` mechanism you just looked at) to ensure data is not corrupted, those 10,000 concurrent requests are forced into a strict, single-file line. Only one database connection can write to the row at a time. The remaining 9,999 transactions stall in a queue. This rapidly exhausts the database's connection pool, spikes system latency, and can easily cause a cascading crash.

Here is how the three architectural patterns eliminate that bottleneck:

**1. Shard the Counter** Instead of maintaining a single row where `likes = 10,000`, you split the counter across multiple distinct rows.

- You create a dedicated table for the counter and pre-populate it with, for example, 100 separate rows for that single post.
- When a user clicks "Like", the application randomly selects just one of those 100 rows and applies the update.
- This instantly divides the lock contention by a factor of 100. When a user loads the page, the system simply executes a rapid `SELECT SUM(count) FROM counter_shards WHERE post_id = X` to display the total.

**2. Use Redis INCR** This strategy bypasses the relational database's heavy disk I/O and locking mechanisms entirely by absorbing the traffic spike in memory.

- Redis features a built-in `INCR` command explicitly designed for atomic increments. Because Redis operates entirely in RAM and executes commands sequentially on a highly optimized single thread, it can easily process hundreds of thousands of increments per second without complex locking overhead.
- The application increments the counter in Redis in real-time, providing immediate feedback to the user. Meanwhile, a background worker periodically reads that total and flushes it to the permanent relational database (e.g., once every 5 seconds) to ensure long-term durability.

**3. Append Events and Sum Them** This pattern removes the `UPDATE` bottleneck by completely abandoning updates, shifting to an append-only Event Sourcing model.

- Instead of modifying an existing total, every single "Like" is written as a brand new, independent row (e.g., `INSERT INTO likes_events (post_id, user_id, timestamp)`).
- Relational databases can execute parallel `INSERT` operations exponentially faster than concurrent `UPDATE` operations because there are no shared row locks to fight over.
- A background process continuously consumes this stream of append events, aggregates the sum, and pushes the final number to a read-optimized cache (utilizing the CQRS pattern discussed earlier).

---

## Optimistic vs Pessimistic

Both stop lost updates. The difference is whether you assume a conflict and wait up front, or assume none and retry if you were wrong.

| | Optimistic (version / CAS) | Pessimistic (`FOR UPDATE`) |
|---|---|---|
| Conflict | Rare | Likely (same seat) |
| Hold time | None until the write | Duration of the transaction |
| On failure | Retry the transaction | Wait, or deadlock |
| Pick when | Profiles, likes: low contention | Booking the last seat: high contention |

CAS is the same idea in Redis: `WATCH`, or a Lua script that says "read tokens, write only if still N."

**CAS (Compare-And-Set / Compare-And-Swap)** is an atomic operation that updates a record only if its current state perfectly matches an expected baseline value. It bundles a read check ("Is the version still 1?") and an update ("Set the value to 4 and version to 2") into a single, indivisible step.

In database concurrency, CAS is the exact underlying mechanism used to execute an optimistic lock. The version comparison pattern discussed earlier (`UPDATE inventory SET qty = 4 WHERE item_id = 123 AND version = 1`) is a direct implementation of CAS at the SQL level.

---

## Distributed Lock: The Recipe

When the critical section really does span machines and no single DB row can guard it, a Redis lock is the practical tool. It's three parts, and each one prevents a specific bug.

```
SET lock:job:nightly <token> NX EX 30
# do work
# release only if value still == token  (Lua: if get==token then del)
```

- **`NX`**: only one winner.
- **`EX`**: if you die, the lock dies. No expiry means locked forever.
- **Token**: a random UUID. Never `DEL lock` blindly, or you delete the *next* holder's lock.

| Use | Lock? | Why |
|---|---|---|
| Leader for a cron | Yes | No DB row naturally guards it |
| Cache stampede (one refill) | Yes | Short, and losing it is harmless |
| "Only one indexer on this shard" | Yes | Cross-machine work |
| Checkout / seat booking | **No** | Use the seat row: constraint, version or hold state |

### What they will poke

Every lock recipe gets the same follow-ups. These are the answers.

| Failure | What happens | Mitigation |
|---|---|---|
| Process dies | Lock would be held forever | TTL frees it. Sweeper / `hold_until` if it's inventory |
| Work longer than TTL | Lock expires, **two holders** | Renew with a heartbeat, *or* shorter work, *or* fencing |
| Clock skew | Holders disagree on expiry | Redis TTL runs on the **server**. Don't use app clocks for expiry |
| Two Redis nodes (split) | Two lock services → two winners | Don't run "two independent Redis" as the lock |
| Deadlock | Two holders wait on each other | Lock keys in sorted order, same as the DB |

### Fencing tokens

This is the one extra idea worth adding. Each time you acquire the lock you also get a **monotonic number** (a ZooKeeper zxid, or `INCR fence`). The storage layer **rejects writes carrying an older fence**. So even if a paused holder's lock expired and it still thinks it's the leader, its writes bounce.

Redis `SET NX EX` **alone has no fencing**. That's why people call Redlock slippery and treat ZooKeeper/etcd locks as the ones that are correct.

> [!tip] "Redis lock with a TTL for the cron. For money I wouldn't trust it; I'd put a version or fence on the row so a late holder can't commit."

**Redlock** is a multi-Redis majority lock. Name-drop it as "controversial, I won't use it for payments." Don't implement it.

**ZooKeeper / etcd / Consul** give an ephemeral node plus a watch, which is a lock with built-in liveness. It's "someone else's Raft." Use them when the lock *is* the product (leader election for a primary), not for a seat. How consensus makes these correct is in [[17 - Consistency Time and Consensus]].

Here is a breakdown of the specific concepts mentioned in the text:

**The "Zombie" Server Problem**

Imagine Server A acquires a Redis lock with a 10-second expiration (`EX 10`). Five seconds later, Server A suffers a massive CPU spike or network delay and completely freezes. Because 10 seconds pass, Redis automatically deletes the lock. Server B then safely acquires the new lock and begins updating the database. Suddenly, Server A unfreezes. Because it never formally crashed, it still believes it holds the lock and blindly writes its outdated data to the database, overwriting Server B's work and corrupting the system.

**How Fencing Tokens Fix This** A fencing token is a strictly increasing number (1, 2, 3) handed out alongside the lock.

- Server A gets the lock and Fencing Token 1.
- Server A freezes, and the lock expires.
- Server B gets the lock and Fencing Token 2, and successfully writes to the database. The database records that the latest token it has seen is 2.
- Server A wakes up and tries to write to the database using Token 1.
- The "storage layer" (the database) explicitly rejects Server A's write because 1 is older than 2. The database acts as a bouncer, bouncing the late writer away.

**Why Redis is "Slippery"** The standard Redis lock (`SET NX EX`) only manages time; it does not natively generate or validate these increasing fencing tokens. Because of this, "Redlock" (the official Redis algorithm for distributed locks) is heavily criticized by system designers. It is considered "slippery" because it cannot mathematically guarantee safety if a server freezes and wakes up late.

**ZooKeeper / etcd** Unlike Redis, systems like ZooKeeper or etcd are built on strict consensus protocols (like Raft). When they issue a lock, they natively attach a guaranteed, monotonically increasing sequence number (like a `zxid`). Because they provide this built-in fencing, they are universally trusted by engineers as "correct" tools for highly critical leader election or distributed coordination.

**The "Money" Rule**

The text concludes with a practical rule of thumb for system design:

- **For harmless tasks:** If you are running a cron job to send a nightly newsletter, a Redis lock is perfectly fine. If the zombie server bug happens and a few users get an email twice, it is not a disaster.
- **For money/critical data:** If you are processing financial transactions, you never trust a time-based Redis lock alone. You must rely on the permanent database to protect itself using a fencing token or a version column (optimistic locking), ensuring that a delayed server can never accidentally commit a rogue financial update.

A standard Redis lock relies on the `SET NX EX` command executed on a single Redis instance. If that one Redis server crashes or restarts, the lock state can be lost, compromising the system's safety. Redlock is an algorithm designed to fix this single point of failure by deploying multiple, completely independent Redis master nodes (typically five). To successfully acquire a Redlock, a client application must successfully execute the `SET NX EX` command across a majority of those independent nodes (e.g., at least three out of five) within a strict time limit. It is a distributed consensus wrapper built on top of the standard single-node command.

The random UUID token and the fencing token serve entirely different purposes and operate at different layers of your architecture to prevent different types of damage.

---

## Lease vs Lock

A **lease** is a lock that is *expected* to be renewed: "You may be the leader for 10 s; heartbeat or lose it." Kubernetes leader election, the Kafka partition leader, and "this worker owns shard 7" all work this way.

It's the same TTL idea. The extra word is **renew**, and the extra rule is: if you can't renew, you **stop writing**.

| Tool | Lifetime | Pick when |
|---|---|---|
| **Lock** | One critical section, then release | Short, one-off work (a cron run, a cache refill) |
| **Lease** | Ongoing ownership, renewed by heartbeat | A role held for a long time (leader, shard owner) |

---

## Semaphore

A lock allows one holder; a semaphore allows **at most N**. "At most 20 thumbnail workers" is a semaphore. In Redis, increment a counter with a cap, or hand out a list of tokens.

A gateway concurrency limit is a semaphore, not a rate limit. A rate limit counts requests per unit of time; a concurrency limit counts requests in flight right now.

| Control | Limits | Pick when |
|---|---|---|
| **Lock** | 1 holder | Exactly one actor |
| **Semaphore** | N concurrent holders | Protect a resource with fixed capacity |
| **Rate limit** | Requests per time window | Abuse control, fair use |

---

## Single-Flight (Request Coalescing)

When 100 requests miss the same cache key at once, single-flight lets **one** go to the DB while the others wait for its result. It's the stampede fix in [[06 - Caching]]. It's in the same family as a lock, but its lifetime is exactly one fetch.

---

## Leader Election for Cron and Schedulers

"The nightly bill run must run once" is a leader-election problem, but it rarely needs real consensus. The options below go cheapest first; pick the first one that fits. Not Raft on the whiteboard.

| Option                          | How                                                        | Pick when                            |
| ------------------------------- | ---------------------------------------------------------- | ------------------------------------ |
| 1. Cloud scheduler              | It hits **one** HTTP endpoint; the provider elects for you | Available on your platform. Cheapest |
| 2. `jobs` table + `SKIP LOCKED` | Workers claim rows                                         | You already have a DB and many jobs  |
| 3. Redis lock around the job    | `SET NX EX` + token                                        | Single job, Redis already present    |
| 4. ZooKeeper / etcd             | Ephemeral node, lease                                      | You already run the cluster          |

---

## Inbox (Pair with Outbox)

[[07 - Async Messaging]] covers the **outbox**: commit to the DB and publish in one step. The inbox is its twin on the consumer side.

The consumer writes the `event_id` into an `inbox` table **in the same transaction** as the side effect. If Kafka delivers the event twice, the second insert hits a unique violation and the consumer skips it. That's idempotency you can point at.

In this context, the **side effect** is the actual business operation or database change that the consumer is supposed to execute in response to receiving the incoming Kafka event.

To prevent your system from double-deducting inventory, you must bind your business logic (the side effect) and the logging of the event's ID into the inbox table together **within the exact same database transaction**.

```mermaid
flowchart LR
    K[Kafka event] --> C[Consumer]
    C -->|same txn| I[(inbox: event_id UNIQUE)]
    C -->|same txn| S[(Side effect)]
    I -->|duplicate| X[Skip]
```

---

## What This Note Doesn't Cover

Geo-radius, user search radius and geohash neighbours are in [[09 - Storage Search and Geo]]. Consensus internals, clocks and TrueTime are in [[17 - Consistency Time and Consensus]]. CRDTs are in [[10 - Distributed Building Blocks]]. The Redlock paper, the write-skew catalogue and 2PL proofs are in [[15 - Out of Scope]].

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Lost update** | Two writers overwrite each other | Atomic `UPDATE … WHERE`, version CAS, or `FOR UPDATE` |
| **Deadlock** | Two transactions each wait for the other's row | Lock in sorted ID order; keep transactions tiny |
| **Long-held row lock** | A lock held during payment blocks everyone | Use a hold state with `hold_until`, not a lock |
| **Write skew** | Each update valid, invariant broken | Serializable, or a constraint/lock on the invariant |
| **Hot row** | One counter row serialises all writers | Shard the counter, Redis `INCR`, or append and sum |
| **Stranded lock** | Holder dies with no expiry | Always `EX`; sweeper for DB holds |
| **Two holders** | Work outlives the TTL, or a paused holder resumes | Renew the lease; fencing tokens checked by storage |
| **Wrong-holder release** | Blind `DEL` removes someone else's lock | Token + compare-and-delete in Lua |
| **Duplicate event** | Consumer applies the side effect twice | Inbox table with unique `event_id` |

---

## Interview Lines

> [!tip] Say it like this
> - "A distributed lock is my last tool. First a unique constraint, then a version column, then a hold state with a TTL."
> - "Seats are guarded by the seat row, not by Redis. `hold_until` plus a sweeper releases abandoned holds."
> - "Workers claim jobs with `FOR UPDATE SKIP LOCKED`, so nobody steals the same job and I don't need Redis."
> - "Redis lock is `SET NX EX` with a random token, and release is compare-and-delete."
> - "For money I add a fencing token on the row, so a late lock holder can't commit."
> - "Consumers dedupe with an inbox table in the same transaction as the side effect."

## Related Notes

- [[05 - Databases]]
- [[06 - Caching]]
- [[07 - Async Messaging]]
- [[08 - Reliability]]
- [[10 - Distributed Building Blocks]]
- [[12 - Mini Designs]] — booking, ride offer TTL
- [[17 - Consistency Time and Consensus]]
