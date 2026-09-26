---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
---
# Databases

> [!abstract]
> - Depth under the 10-minute HLD slice: [[05 - Databases]]
> - These are **complete notes**, not a revision sheet
> - Mouth version of the grill: [[11 - Interview Questions]]
> - Locks you take in the **app** (`SKIP LOCKED`, Redis, fencing): [[14 - Coordination and Concurrency]]
> - CAP / PACELC: [[02 - Fundamentals]]
> - Hash ring, quorum, 2PC vs saga: [[10 - Distributed Building Blocks]]

| # | Open this when they ask… |
|---|---|
| [[01 - Storage Engine and Disk IO]] | How a row sits on disk. Buffer pool. WAL. Crash recovery (ARIES). |
| [[02 - Indexing]] | B+, LSM, hash, clustered vs secondary, leftmost prefix |
| [[03 - Concurrency Control]] | 2PL, MVCC, deadlock, lost update |
| [[04 - Isolation and Anomalies]] | RU / RC / RR / Serializable. Dirty, phantom, write skew. |
| [[05 - Query Execution and Optimization]] | CBO, joins, seq vs index vs index-only, N+1 |
| [[06 - Connections and Network IO]] | Pool math. Hikari vs PgBouncer. Thread vs `epoll`. |
| [[07 - Data Modelling]] | Normalise, denormalise a hot read, NoSQL PK |
| [[08 - Replication]] | Sync vs async, lag, read-your-writes, quorum |
| [[09 - Sharding]] | When not to. Keys. Hot shard. More shards ≠ more throughput. |
| [[10 - Engine Choice]] | Postgres vs Cassandra vs Redis vs ES — one sentence each |
| [[11 - Interview Questions]] | Say it out loud |

---

## Related Notes

- [[05 - Databases]]
- [[HLD/Topics/README]]
- [[06 - Caching]]
- [[09 - Storage Search and Geo]]
- [[10 - Distributed Building Blocks]]
- [[14 - Coordination and Concurrency]]
