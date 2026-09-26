---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# Databases

> [!abstract] Default to **Postgres**. Switch when the **access pattern** says so — not because "NoSQL scales." Know indexes, replication, and when sharding is actually justified. Consistency is per table, not per company. Depth (WAL, B+, MVCC, CBO, shards): [[README]].

## The pick (say this, then shut up)

> "Postgres for users/orders because I want transactions and arbitrary queries. If this table is a firehose of events or a 100 TB write-heavy log, I'd put *that* in Cassandra/Kafka and keep the source of truth for money in Postgres."

Broad slogans ("SQL for relationships, NoSQL for scale") are a yellow flag. Both can do both. Justify with **this query** and **this consistency**.

## Flavours of NoSQL (so you don't say "NoSQL")

| Type | Looks like | Use | Example |
|---|---|---|---|
| Key-value | `GET k → blob` | sessions, cache, feature flags | Redis, DynamoDB |
| Document | JSON documents | evolving objects, one-document aggregates | MongoDB |
| Wide-column | row key + columns | write-heavy time series, huge tables | Cassandra |
| Graph | nodes/edges | "friends of friends" as the *product* | Neo4j — rarely the first pick |

Redis as **cache** is [[06 - Caching]]. Redis as **primary store** only for data you can lose or rebuild (sessions, leaderboards).

## Data modelling

- Start **normalised** (users, orders, items). Update-once, join on read.
- **Denormalise a hot read** when you have measured it: store `username` on the comment so the feed doesn't join. Then name the pain: rename user → fanout update or accept stale names.
- NoSQL: **design the primary key for the #1 query**. `user_id` partition for "all posts by user." Then "all posts with hashtag X" is a **different table or index**, not a table scan.

## Indexes

Without an index, lookup is a full scan.

- **B-tree** — default. Equality **and** range (`WHERE created_at >`).
- **Hash** — equality only. Rarely what you ask for by name.
- **Composite** `(user_id, created_at)` — leftmost prefix matters. This index helps `user_id` and `user_id + time`, not `time` alone.
- **Covering** — index contains every column the query needs, no heap fetch.
- **Unique** — also an index. Email login.
- **Partial / GIN / GiST / geo** — "Postgres can do this" is enough unless they push.

> [!warning] Every index slows writes and uses disk. Index what you **query**, not every column.

**Secondary indexes on a sharded DB** are painful (scatter-gather). That's why "search" often means Elasticsearch, not `LIKE %foo%`.

## Transactions and isolation (SDE-1 depth)

**ACID** — Atomic, Consistent (constraints), Isolated, Durable.

Isolation, from "see garbage" to "expensive":

- **Read uncommitted** — dirty reads. Almost nobody wants this.
- **Read committed** — Postgres default. No dirty reads; still non-repeatable.
- **Repeatable read** — snapshot of the rows you read.
- **Serializable** — looks like transactions ran one at a time. Phantoms gone. Slowest.

**Lost update:** two tabs set quantity. Fix: `UPDATE … SET qty = qty - 1 WHERE qty > 0` (atomic), or **optimistic lock** (`version` column, retry on mismatch), or `SELECT FOR UPDATE`.

**Phantom:** range sees a new row on second read. Serializable or careful locking.

HLD sentence: "checkout uses a transaction + optimistic version on the stock row." Internals (MVCC, anomalies, WAL): [[03 - Concurrency Control]] · [[04 - Isolation and Anomalies]] · [[01 - Storage Engine and Disk IO]].

## Replication

**Why:** reads scale, failover, backup.

| Mode | Write | Read | Failure |
|---|---|---|---|
| Leader–follower (primary–replica) | all writes to leader | replicas for reads | promote a replica |
| Sync replica | write waits for replica | always up to date | slower writes, stronger |
| Async replica | write returns after leader | replica **lags** | can lose last writes on failover |
| Multi-leader | writes on both | conflicts | only if you must write in two regions |
| Leaderless (quorum) | W of N | R of N, `R+W>N` | Dynamo/Cassandra |

> [!tip] Default design: **one primary, async read replicas, cache in front.** Mention replica lag: "after a write, read the primary or accept a 100 ms stale read."

**Read-your-writes:** stick the user to primary for a second, or read-after-write from primary.

## Sharding (partitioning)

Split data across multiple primaries because **one box cannot take the writes or the disk**.

**Do not shard** until numbers say so (high write QPS or multi-TB). Replicas + cache first.

**Shard key is the whole design:**

- `user_id` — all of a user's data together. Great for "my orders." Bad for "global trending" (query all shards).
- Hash(key) % N — even, but **adding a shard reshuffles everything** unless you use [[10 - Distributed Building Blocks|consistent hashing]].
- Range (date, id) — easy scans, easy **hot shard** (today's partition).
- Geo / tenant — natural if the product is regional or B2B.

Pain you must name: **no cross-shard JOIN/transaction**, hot keys (celebrity user), resharding.

**Directory / lookup table** — flexible, extra hop, extra SPOF. Rarely the first answer.

## Stored procedures, N+1, connection pools

- **N+1** — fetch users, then one query per user. Fix: join or `WHERE id IN (…)`.
- **Connection pool** — DB connections are scarce. App servers share a pool (Hikari). 200 app boxes × 50 connections will melt Postgres.
- Don't propose stored procedures as architecture.

## B-tree vs LSM (if they poke "why is Cassandra good at writes")

- **B-tree** (Postgres, InnoDB) — reads find a page and update in place. Great lookups, writes cause random page splits.
- **LSM** (Cassandra, RocksDB, ES) — writes **append** to a memtable, flush to sorted files, compact later. Fast writes, reads may hit several files (bloom filters help).

One sentence: "Write-heavy firehose → LSM. Point-read transactional → B-tree."

## Picking in one sentence (drill this)

| Data | Pick |
|---|---|
| Users, orders, payments | Postgres, strong |
| Session, hot profile | Redis (cache or store) |
| Chat messages, huge append | Cassandra / Dynamo, partition by `chat_id` |
| Analytics events | Kafka → warehouse (not OLTP Postgres as the sink) |
| Search | Elasticsearch, lag OK |
| Files | S3, URL in Postgres — [[09 - Storage Search and Geo]] |

## Related Notes

- [[README]] — storage, indexes, isolation, optimizer, shard, interview Qs
- [[02 - Fundamentals]]
- [[06 - Caching]]
- [[10 - Distributed Building Blocks]]
- [[14 - Coordination and Concurrency]] — `FOR UPDATE`, `SKIP LOCKED`, version columns, write skew one-liner
