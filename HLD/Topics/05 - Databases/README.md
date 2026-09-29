---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
updated: 2026-09-29
---
# Databases

> [!abstract] What this is
> This folder is the deep version of [[05 - Databases]]. The overview note is the ten-minute summary; the numbered files underneath go down to how a row sits on disk, how indexes are shaped, how concurrent transactions are kept apart, and how data is spread across machines. These are complete notes you can learn from on a cold read, and [[11 - Interview Questions]] is the "say it out loud" version.

---

## Files

Each file owns one layer of the database. Open the one that matches the question you're being asked.

| File | What it covers | Read when |
|---|---|---|
| [[05 - Databases]] | The ten-minute overview: engine pick, indexes, isolation, replication, sharding | You want the whole picture before going deep |
| [[01 - Storage Engine and Disk IO]] | How a row sits on disk, the buffer pool, WAL, crash recovery (ARIES), vacuum and bloat | They ask "what does commit actually mean?" or "why does this table keep growing?" |
| [[02 - Indexing]] | B+ trees, LSM trees, hash, clustered vs secondary, leftmost prefix | They ask why a query is slow, or why Cassandra is good at writes |
| [[03 - Concurrency Control]] | 2PL, MVCC, deadlock, lost update, hot rows | Two users are writing the same thing at the same time |
| [[04 - Isolation and Anomalies]] | RU / RC / RR / Serializable; dirty read, phantom, write skew, SSI | They ask which isolation level, or what can go wrong at each |
| [[05 - Query Execution and Optimization]] | The cost-based optimizer, joins, seq vs index vs index-only scans, N+1 | A query plan or an ORM pattern is the problem |
| [[06 - Connections and Network IO]] | Pool math, Hikari vs PgBouncer, thread-per-connection vs `epoll` | Too many app servers are talking to one database |
| [[07 - Data Modelling]] | Normalise, denormalise a hot read, NoSQL primary key design | You're drawing the tables |
| [[08 - Replication]] | Sync vs async, lag, read-your-writes, quorum | Reads need to scale, or they ask about failover |
| [[09 - Sharding]] | When not to shard, shard keys, hot shards, why more shards isn't more throughput | One primary can't take the writes or the disk |
| [[10 - Engine Choice]] | Postgres vs Cassandra vs Redis vs Elasticsearch, one sentence each | You have to justify a product pick |
| [[11 - Interview Questions]] | The grill, answered out loud | Final revision before an interview |
| [[12 - Schema Changes and Backups]] | Changing a live schema safely, backups and point-in-time recovery | They ask how you'd add a column to a huge table, or restore after a bad deploy |

A few neighbouring topics live outside this folder. Locks you take in the **app** (`SKIP LOCKED`, Redis locks, fencing tokens) are in [[14 - Coordination and Concurrency]]. CAP and PACELC are in [[02 - Fundamentals]]. The hash ring, quorums, and 2PC vs saga are in [[10 - Distributed Building Blocks]].

---

## Reading Order

The files are numbered bottom-up, so reading them in order builds each layer on the one below it.

1. [[05 - Databases]] for the overview.
2. [[01 - Storage Engine and Disk IO]] and [[02 - Indexing]], because everything else sits on pages, the WAL, and B+ trees.
3. [[03 - Concurrency Control]] then [[04 - Isolation and Anomalies]], which only make sense together.
4. [[05 - Query Execution and Optimization]] and [[06 - Connections and Network IO]] for single-box performance.
5. [[07 - Data Modelling]], [[08 - Replication]], [[09 - Sharding]] for the design decisions that show up on the whiteboard.
6. [[10 - Engine Choice]] and [[12 - Schema Changes and Backups]] for operating the thing.
7. [[11 - Interview Questions]] last, as a self-test.

---

## Related Notes

- [[05 - Databases]]
- [[HLD/Topics/README]]
- [[06 - Caching]]
- [[09 - Storage Search and Geo]]
- [[10 - Distributed Building Blocks]]
- [[14 - Coordination and Concurrency]]
