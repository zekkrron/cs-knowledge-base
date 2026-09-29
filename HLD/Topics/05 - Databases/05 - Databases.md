---
tags: [hld/topics, status/draft]
created: 2026-09-18
updated: 2026-09-29
---
# Databases

> [!abstract] What this is
> This is the ten-minute version of the whole databases folder. The default is **Postgres**, and you switch only when the **access pattern** says so, not because "NoSQL scales." The decisions that matter are which engine, which indexes, which isolation level, how you replicate, and whether sharding is actually justified. Consistency is chosen per table, not per company. Each section links to the deep note; the full index is in [[README]].

---

## Why It Exists

Almost every design has a source of truth, and the database is where it lives. Picking the wrong engine or the wrong shard key is expensive to undo, so interviewers probe it hard. The goal is to justify each choice with **this query** and **this consistency need**, rather than broad slogans.

---

## The Pick

Start by naming the engine and the reason, then stop talking. A good default answer sounds like this:

> "Postgres for users/orders because I want transactions and arbitrary queries. If this table is a firehose of events or a 100 TB write-heavy log, I'd put *that* in Cassandra/Kafka and keep the source of truth for money in Postgres."

Slogans like "SQL for relationships, NoSQL for scale" are a yellow flag, because both kinds of database can do both. The deeper product comparison is in [[10 - Engine Choice]].

---

## Flavours of NoSQL

"NoSQL" isn't one thing, so name the flavour. Each one is shaped around a different access pattern.

| Type | Looks like | Pick when | Example |
|---|---|---|---|
| Key-value | `GET k → blob` | Sessions, cache, feature flags | Redis, DynamoDB |
| Document | JSON documents | Evolving objects, one-document aggregates | MongoDB |
| Wide-column | Row key + columns | Write-heavy time series, huge tables | Cassandra |
| Graph | Nodes and edges | "Friends of friends" *is* the product | Neo4j, rarely the first pick |

Redis as a **cache** is covered in [[06 - Caching]]. Redis as a **primary store** is only for data you can lose or rebuild, such as sessions and leaderboards.

---

## Data Modelling

Start **normalised** (users, orders, items): each fact is updated once, and you join on read. **Denormalise a hot read** only once you've measured it, for example storing `username` on the comment so the feed doesn't need a join. When you do, name the pain: renaming a user now means a fanout update, or you accept stale names.

In NoSQL you **design the primary key for the #1 query**. A `user_id` partition key serves "all posts by user." Then "all posts with hashtag X" needs a **different table or index**, not a table scan. Depth: [[07 - Data Modelling]].

---

## Indexes

Without an index, every lookup is a full scan. An index buys faster reads at the cost of slower writes and more disk, so you index what you **query**, not every column.

| Index | What it does | Pick when |
|---|---|---|
| **B-tree** | Equality **and** range (`WHERE created_at >`) | The default |
| **Hash** | Equality only | Rarely asked for by name |
| **Composite** `(user_id, created_at)` | Leftmost prefix matters: helps `user_id` and `user_id + time`, not `time` alone | Queries filter on several columns together |
| **Covering** | Holds every column the query needs, so no heap fetch | A hot query reads only a few columns |
| **Unique** | A constraint that is also an index | Email login |
| **Partial / GIN / GiST / geo** | Specialised cases | "Postgres can do this" is enough unless they push |

> [!warning] Every index slows writes and uses disk. Index what you **query**, not every column.

**Secondary indexes on a sharded DB** are painful, because a lookup on anything other than the shard key turns into scatter-gather across every shard. That's why "search" often means Elasticsearch, not `LIKE %foo%`. Depth: [[02 - Indexing]].

---

## B-tree vs LSM

This comes up when they poke at "why is Cassandra good at writes." The two structures make opposite trade-offs.

| Structure | How writes work | Pick when | Used by |
|---|---|---|---|
| **B-tree** | Find the page and update in place. Great lookups; writes cause random page splits | Point-read transactional workloads | Postgres, InnoDB |
| **LSM** | **Append** to a memtable, flush to sorted files, compact later. Reads may hit several files (bloom filters help) | Write-heavy firehose | Cassandra, RocksDB, ES |

The one sentence: "Write-heavy firehose → LSM. Point-read transactional → B-tree." How pages, the buffer pool, and the WAL sit underneath both is in [[01 - Storage Engine and Disk IO]].

---

## Transactions and Isolation

**ACID** stands for Atomic, Consistent (constraints hold), Isolated, and Durable. Isolation is the one with a dial, and the levels run from "you can see garbage" to "expensive."

| Level | What it means | Pick when |
|---|---|---|
| **Read uncommitted** | Dirty reads allowed | Almost nobody wants this |
| **Read committed** | No dirty reads; reads can still be non-repeatable. Postgres default | Most CRUD |
| **Repeatable read** | A snapshot of the rows you read | A report must not see values shift mid-transaction |
| **Serializable** | Looks like transactions ran one at a time. Phantoms gone. Slowest | Invariants that span rows, money |

A **lost update** is two tabs both setting quantity, where the last write silently wins. The fixes are an atomic `UPDATE … SET qty = qty - 1 WHERE qty > 0`, an **optimistic lock** (a `version` column, retry on mismatch), or `SELECT FOR UPDATE`. A **phantom** is a range query that sees a new row on its second read; Serializable or careful locking stops it.

The HLD sentence: "checkout uses a transaction + optimistic version on the stock row." Internals are in [[03 - Concurrency Control]] (MVCC, locks), [[04 - Isolation and Anomalies]] (every anomaly, write skew), and [[01 - Storage Engine and Disk IO]] (WAL).

---

## Replication

Replication copies data to more machines so that reads scale, a failover target exists, and backups don't load the primary. The modes differ in when a write counts as done.

| Mode | Write | Read | Failure / trade-off | Pick when |
|---|---|---|---|---|
| Leader–follower (primary–replica) | All writes to the leader | Replicas serve reads | Promote a replica | The default |
| Sync replica | Write waits for the replica | Always up to date | Slower writes, stronger guarantee | You can't lose a committed write |
| Async replica | Write returns after the leader | Replica **lags** | Can lose the last writes on failover | Most read replicas |
| Multi-leader | Writes on both leaders | Conflicts to resolve | Conflict handling | Only if you must write in two regions |
| Leaderless (quorum) | W of N | R of N, with `R+W>N` | Tunable per request | Dynamo / Cassandra style |

> [!tip] Default design: **one primary, async read replicas, cache in front.** Mention replica lag: "after a write, read the primary or accept a 100 ms stale read."

**Read-your-writes** means a user always sees their own write. Either stick the user to the primary for a second after they write, or do the read-after-write from the primary. Depth: [[08 - Replication]].

---

## Sharding

Sharding (partitioning) splits data across multiple primaries because **one box can't take the writes or the disk**. **Don't shard** until the numbers say so, meaning high write QPS or multi-TB data. Try replicas and a cache first.

The **shard key is the whole design**, because it decides which queries stay on one shard.

| Shard key | Good at | Bad at | Pick when |
|---|---|---|---|
| `user_id` | All of a user's data together; "my orders" | "Global trending" means querying every shard | User-centric products |
| `hash(key) % N` | Even spread | **Adding a shard reshuffles everything** unless you use [[10 - Distributed Building Blocks\|consistent hashing]] | Even load matters most |
| Range (date, id) | Easy scans | Easy **hot shard** (today's partition) | Time-ordered scans dominate |
| Geo / tenant | Natural isolation | Uneven tenant sizes | Regional or B2B products |

The pains you must name are **no cross-shard JOIN or transaction**, hot keys (a celebrity user), and resharding. A **directory / lookup table** that maps keys to shards is flexible but adds an extra hop and an extra single point of failure, so it's rarely the first answer. Depth: [[09 - Sharding]].

---

## Queries and Connections

Two performance problems show up in nearly every design. **N+1** is fetching a list of users and then running one query per user; fix it with a join or `WHERE id IN (…)`. Depth: [[05 - Query Execution and Optimization]].

**Connection pools** matter because DB connections are scarce. App servers share a pool (Hikari), but 200 app boxes × 50 connections will melt Postgres. Depth: [[06 - Connections and Network IO]]. Separately, don't propose stored procedures as architecture.

---

## Picking in One Sentence

Drill this table until each row is automatic.

| Data | Pick |
|---|---|
| Users, orders, payments | Postgres, strong consistency |
| Session, hot profile | Redis (cache or store) |
| Chat messages, huge append | Cassandra / Dynamo, partition by `chat_id` |
| Analytics events | Kafka → warehouse (not OLTP Postgres as the sink) |
| Search | Elasticsearch, lag is OK |
| Files | S3, with the URL in Postgres. See [[09 - Storage Search and Geo]] |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Lost update** | Two writers read-then-write; one change vanishes | Atomic `UPDATE`, `version` column, or `SELECT FOR UPDATE` |
| **Replica lag** | A user writes, then reads a replica and doesn't see it | Read-your-writes from the primary, or accept the stale read |
| **Async failover** | The last acknowledged writes never reached the replica | Sync replica for data you can't lose |
| **Hot shard** | One shard (today's range, a celebrity) takes most of the load | Better shard key, split the hot key |
| **Connection exhaustion** | Too many app boxes × pool size overwhelm the DB | Smaller pools, PgBouncer |
| **Index sprawl** | Writes slow down and disk fills | Index only what you query |

---

## Interview Lines

> [!tip] Say it like this
> - "Postgres by default. I'd move a specific table off it only when its access pattern demands it."
> - "Checkout uses a transaction plus an optimistic version on the stock row."
> - "One primary, async read replicas, cache in front. After a write, read the primary or accept a 100 ms stale read."
> - "I won't shard until the write QPS or the disk size forces it, and then the shard key is the whole design."
> - "Write-heavy firehose goes to an LSM store; point-read transactional data stays on a B-tree."

## Related Notes

- [[README]]: the full folder index (storage, indexes, isolation, optimizer, sharding, interview questions)
- [[02 - Fundamentals]]
- [[06 - Caching]]
- [[10 - Distributed Building Blocks]]
- [[12 - Schema Changes and Backups]]
- [[14 - Coordination and Concurrency]]: `FOR UPDATE`, `SKIP LOCKED`, version columns, the write skew one-liner
