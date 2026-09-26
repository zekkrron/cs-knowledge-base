---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
---
# Engine Choice

> [!abstract]
> - Default to **Postgres**. Switch when the **access pattern** says so — not because “NoSQL scales”
> - Consistency is **per table**, not per company
> - B+ vs LSM is a **write vs read** pick, not a brand pick
> - Depth of each engine lives in the other files. This is the **one-sentence drill**

---

## The pick (say this, then shut up)

> “Postgres for users/orders because I want transactions and arbitrary queries. If this table is a firehose of events or a 100 TB write-heavy log, I’d put *that* in Cassandra/Kafka and keep the source of truth for money in Postgres.”

- Both SQL and NoSQL can “scale”
- Justify with **this query** and **this consistency**

---

## Drill table

| Data | Pick | Why |
|---|---|---|
| Users, orders, payments | **Postgres**, strong | Txns, joins, constraints |
| Session, hot profile | **Redis** (cache or rebuildable store) | Tiny, TTL, `O(1)` |
| Chat messages, huge append | **Cassandra / Dynamo**, partition by `chat_id` | Write-heavy, key-shaped |
| Analytics events | **Kafka → warehouse** | Not OLTP Postgres as the sink |
| Search | **Elasticsearch**, lag OK | Inverted index. [[09 - Storage Search and Geo]] |
| Files | **S3**, URL in Postgres | [[09 - Storage Search and Geo]] |
| Graph-as-product | Neo4j / specialised | Rarely the first box on an SDE-1 board |

---

## B+ vs LSM (product sentence)

| | B+ (Postgres, InnoDB) | LSM (Cassandra, RocksDB, Lucene-ish) |
|---|---|---|
| Write | Update page in place. Random I/O, splits | Append memtable, flush, compact |
| Read | One (or few) tree walks. Predictable | May hit several files. Bloom helps |
| Best at | Point-read, txn, ad-hoc | Firehose writes, time-series-ish keys |

- Write-heavy firehose → **LSM**
- Point-read transactional → **B+**
- Internals: [[02 - Indexing]]

---

## Flavours (so you don’t say “NoSQL”)

- Key-value / document / wide-column / graph — table in [[07 - Data Modelling]]
- Redis as cache ≠ Redis as SoR
    + SoR only if you can lose or rebuild it
    + [[06 - Caching]]

---

## What you do **not** do

- One Cassandra for payments because “it scales”
- Shard Postgres on day one — [[09 - Sharding]]
- Dual-write Postgres **and** ES in the request — outbox / CDC — [[06 - Ecosystem Resilience and Design Patterns]]
- Multi-leader two regions for a ledger — [[08 - Replication]]

---

## Related Notes

- [[README]]
- [[05 - Databases]]
- [[07 - Data Modelling]]
- [[02 - Indexing]]
- [[09 - Sharding]]
- [[11 - Interview Questions]]
