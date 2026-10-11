---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
updated: 2026-09-29
---
# Interview Questions

> [!abstract] What this is
> A question bank for the databases folder, written as you'd say the answers out loud. Each answer is the full senior version compressed into a few sentences, with a link to the note that explains it in depth, from [[01 - Storage Engine and Disk IO]] through [[12 - Schema Changes and Backups]].

---

## Storage and Durability

These questions test whether you know what "committed" actually means. The answer always comes back to the write-ahead log: commit is a log record on stable storage, not a data page on disk.

| Question | Short answer | Deep link |
|---|---|---|
| A committed txn, then the box loses power. Is the data gone? | **No**, if the **WAL was `fsync`'d**. Commit means "log on stable storage," not "heap page on disk." Dirty pages in the buffer pool are lost, and **ARIES redo** puts committed work back. Uncommitted work that **stole** a page onto disk is rolled back by **ARIES undo**. If committed data really was lost, the log didn't make it to disk (or the disk lied about `fsync`). | [[01 - Storage Engine and Disk IO]] |
| Why WAL instead of fsync-ing every dirty page on commit? | WAL writes are **sequential**; data pages are **random**. One log `fsync` covers many page changes that flush later (**no-force**). Steal plus no-force is why recovery needs both redo **and** undo. | [[01 - Storage Engine and Disk IO]] |
| What is a dirty page? | A buffer-pool page that differs from what's on disk. The WAL record goes to disk first; the page flushes later. | [[01 - Storage Engine and Disk IO]] |

---

## Indexes

Index questions are about physical layout: how the tree is ordered, what a lookup costs, and which queries a given index can and can't serve.

| Question | Short answer | Deep link |
|---|---|---|
| Clustered vs secondary. Why does InnoDB hate UUID PKs? | Clustered means the table's **physical order is the PK**. A secondary index maps key → pointer; in InnoDB that pointer is the **PK**, so it takes a second lookup. A UUID v4 clustered PK makes inserts land **everywhere** in the B+ tree, causing a split storm. `bigserial`, snowflake ids or ULIDs append at the **right edge**. Postgres's heap is **not** clustered by default, so a UUID PK there is still a random unique index, but less brutal than InnoDB. | [[02 - Indexing]] |
| Why B+ tree and not a hash index for `created_at >`? | Hash indexes are equality only. B+ tree leaves are **linked**, so you find the left edge and walk. Range, `ORDER BY` and `LIMIT` all live here. | [[02 - Indexing]] |
| Composite `(user_id, created_at)`: what does it *not* help? | `WHERE created_at > ?` on its own. The leftmost-prefix rule means it serves `user_id`, or `user_id + created_at`. If the select list is only those columns, an index-only scan is possible (covering). | [[02 - Indexing]] |
| LSM vs B+ tree? | LSM appends and compacts; B+ tree updates in place. LSM for write-heavy, B+ tree for point-read transactional. | [[02 - Indexing]] |

---

## Concurrency and Isolation

These questions check whether you can name the anomaly, pick the isolation level that prevents it, and fix it without making the whole app Serializable.

Which level prevents which anomaly:

| Anomaly | Prevented at |
|---|---|
| Dirty read | Read Committed |
| Non-repeatable read | Repeatable Read |
| Phantom | Serializable (Postgres / InnoDB RR only for plain re-reads; range invariants still need Serializable or locks) |
| Write skew | True Serializable (or lock the invariant) |

| Question | Short answer | Deep link |
|---|---|---|
| Name the anomalies. Which level kills which? | See the table above. Postgres defaults to **Read Committed**. Don't make the whole app Serializable. | [[04 - Isolation and Anomalies]] |
| Two doctors both go off call. Each txn looked valid. | **Write skew** under snapshot isolation. The invariant was on a **set** of rows, not one row. Fix with Serializable, or `FOR UPDATE` on a single roster row. It's not a lost update, because they touched different rows. | [[04 - Isolation and Anomalies]] · [[14 - Coordination and Concurrency]] |
| Lost update on `qty`. What do you do? | Don't `SELECT` then `UPDATE` in the app. Use `UPDATE … SET qty = qty - 1 WHERE qty > 0`, or a `version` CAS, or `FOR UPDATE` in a **tiny** transaction. Never hold that lock across a payment call. | [[03 - Concurrency Control]] |
| Intention locks? | Table-level "I will lock a row" markers, so `LOCK TABLE` doesn't have to scan every row lock. | [[03 - Concurrency Control]] |

---

## Optimizer and Access

The optimizer questions test whether you treat the planner as a cost model rather than a bug. The standard move is `EXPLAIN ANALYZE`.

| Question | Short answer | Deep link |
|---|---|---|
| I added an index. The planner still seq-scans. Broken? | **No.** If 40% of rows match, a seq scan is cheaper. Other causes: stale stats (`ANALYZE`), or a leftmost-prefix miss. Run `EXPLAIN ANALYZE` and compare estimate vs actual. | [[05 - Query Execution and Optimization]] |
| Nested loop vs hash vs sort-merge: when? | Nested loop with an **index**: small outer side. Hash: equality, both sides large, build the smaller. Sort-merge: already ordered, or huge and sort-friendly. Nested loop with a seq-scan inner is `O(N*M)`, the failure mode. | [[05 - Query Execution and Optimization]] |
| N+1? | One query per row, usually from ORM lazy loading. Fix with a join or `IN`. | [[05 - Query Execution and Optimization]] |

---

## Connections

There's one classic connection question, and it's about multiplication: pool size times instance count.

| Question | Short answer | Deep link |
|---|---|---|
| 200 pods, pool size 50, Postgres dying. | That's 10,000 backends. Shrink the **app** pool and put PgBouncer in **transaction** mode in front. Postgres connections are processes; you can't have 10k of them. | [[06 - Connections and Network IO]] |

---

## Replication and Sharding

These questions check that you know replicas scale reads, shards scale writes, and both come with costs you should name unprompted.

| Question | Short answer | Deep link |
|---|---|---|
| After UPDATE I GET the old value. | You read a **lagging replica**. For read-your-writes, read from the primary for a short window after a write, or accept staleness only for *other* users' data. | [[08 - Replication]] |
| When do you shard? When do you not? | **Not** at 50k users when reads are the problem (use replicas and a cache). **Yes** when writes or disk don't fit one primary. The key is the design, and cross-shard joins and transactions are gone. | [[09 - Sharding]] |
| If I keep adding shards, will throughput always go up? | **No.** Same argument as Kafka partitions. Throughput is `min(app, hottest shard, scatter-gather fan-out)`. One hot key doesn't split, a 64-way scatter-gather can be **slower**, and a `hash % N` reshard moves almost everything. Pick a key with cardinality, an `N` you can operate, and consistent hashing if `N` will change. | [[09 - Sharding]] · [[04 - Producer Mechanics and Delivery Guarantees]] |
| Directory table for shards? | Flexible placement, but an extra hop and an extra SPOF. Not the first answer. | [[09 - Sharding]] |

---

## Schema Changes and Backups

Operating the database is fair game too. These test whether you can change a live schema without downtime and recover from a bad write.

| Question | Short answer | Deep link |
|---|---|---|
| How do you rename a column with zero downtime? | Expand–migrate–contract: add the new column, dual-write, backfill in batches, switch reads, stop writing the old one, then drop it, with each step shipped separately. | [[12 - Schema Changes and Backups]] |
| Replicas exist. Why do you still need backups? | A bad `DELETE` replicates everywhere. Point-in-time recovery (base backup + WAL replay) takes you back to before it. And restore drills, because an untested backup isn't a backup. | [[12 - Schema Changes and Backups]] |

---

## The Pick

The engine-choice question is really asking whether you'll justify the choice with an access pattern.

| Question | Short answer | Deep link |
|---|---|---|
| Postgres or Cassandra? | Don't say "NoSQL scales." Postgres for transactions, joins, money and ad-hoc queries. Cassandra for a firehose with **one** key-shaped query where eventual or quorum consistency is fine. Money stays in Postgres. | [[10 - Engine Choice]] |
| When would you use Spanner or CockroachDB? | When relational data with cross-entity transactions outgrows one primary. You get SQL and serializable transactions across nodes; you pay consensus latency on every write. | [[10 - Engine Choice]] |

---

## Related Notes

- [[README]]
- [[05 - Databases]]
- [[01 - Storage Engine and Disk IO]]
- [[02 - Indexing]]
- [[03 - Concurrency Control]]
- [[04 - Isolation and Anomalies]]
- [[05 - Query Execution and Optimization]]
- [[06 - Connections and Network IO]]
- [[07 - Data Modelling]]
- [[08 - Replication]]
- [[09 - Sharding]]
- [[10 - Engine Choice]]
- [[12 - Schema Changes and Backups]]
