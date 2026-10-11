---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Query Performance and Indexing

> [!abstract] What this is
> Problems where **one database is slow** for reasons inside the box: missing or wrong indexes, bad plans, N+1 patterns, storage-engine maintenance (bloat, vacuum, wraparound, LSM compaction) and connection handling. If the box itself is too small, go to [[03 - Scaling Reads Writes and Data]].

---

## Diagnosing

### "The database is slow" and nobody knows why

- **Signal:** latency complaints with no diagnosis; someone wants to add a cache or shard.

| Step | What to look at | What it tells you |
|---|---|---|
| **`EXPLAIN ANALYZE`** the slow query | Plan shape, estimated vs actual rows | Bad plan, missing index, or bad estimates |
| **Seq scan on a selective `WHERE`** | Missing index | Add one |
| **Nested loop with a huge inner side** | Missing join index or bad estimate | Index the join key or fix stats |
| **Estimate 10 rows, actual 10M** | Stale or missing statistics | `ANALYZE`, extended stats |
| **Hundreds of tiny identical queries** | N+1 from the ORM | Batch or join |
| **Buffer hit ratio collapses** | Working set > RAM, or a big scan flushed the pool | Size RAM, move scans to a replica |

- **Default:** measure with `EXPLAIN ANALYZE` before changing architecture.
- **Depth:** [[05 - Query Execution and Optimization]]

---

## Indexes

### A hot query does a full table scan

- **Fix:** index the filter column(s), plus the sort column if there's an `ORDER BY`. Costs write speed and disk. Index what you **query**, not every column.
- **Depth:** [[02 - Indexing]]

### Composite index not used, or "latest posts for user" scans and sorts

- **Signal:** index on `(user_id, created_at)` but the query filters only `created_at`; or the columns are in the wrong order.

| Rule | Why |
|---|---|
| **Leftmost prefix** | The tree is sorted by the first column, then the next; you can't start from the middle |
| **Equality columns first** (higher cardinality first among them) | Descent lands on a narrow run of leaves |
| **`ORDER BY` columns next** | Leaves are already sorted within the equality run, so no in-memory sort |
| **Range columns last** | Descent stops at the first range; later columns are only filtered during the scan |
| **Skip scan** (MySQL 8.0.13+, Postgres 18) | Partly rescues a missing low-cardinality leading column |

- **Default:** design the composite from the hot query: `WHERE` equalities → `ORDER BY` → range. In Mongo the same rule is called **ESR** (Equality, Sort, Range).
- **Depth:** [[02 - Indexing]], [[23 - MongoDB]]

### "I added an index but the planner ignores it"

| Situation | What happens | Pick when |
|---|---|---|
| ~40% of rows match | Seq scan is genuinely cheaper | Accept it |
| Medium selectivity | **Bitmap index scan** reads each page once | Planner chooses it |
| Stats are stale | Wrong estimate | `ANALYZE` |

- **Depth:** [[05 - Query Execution and Optimization]]

### Hot query reads a few columns but pays for heap / clustered-row fetches

| Option | Costs you | Pick when |
|---|---|---|
| **Covering index / index-only scan** | Bigger index | Hot query touching a few columns |
| **Keep tables vacuumed** (Postgres visibility map) | Vacuum health | Index-only scans only skip the heap for all-visible pages |

- **Depth:** [[02 - Indexing]]

### Too many indexes; writes slow and disk fills

- **Fix:** drop unused indexes; every index is maintained on every write. **Depth:** [[02 - Indexing]]

### Random UUID primary key on a clustered (InnoDB) table

- **Signal:** page splits everywhere, fat secondary indexes, cold second-hop lookups.
- **Fix:** time-ordered ids (Snowflake, ULID, UUID v7, `bigserial`) so inserts go to the right edge and secondary indexes carry a small PK. Hurts less in Postgres, where secondaries point at a heap `(page, slot)`.
- **Depth:** [[02 - Indexing]]

### Page-split storms on a busy B+ tree

| Option | Costs you | Pick when |
|---|---|---|
| **Fill factor ~70–90%** | Some wasted space | Busy OLTP indexes; slack absorbs inserts before splitting |
| **Pack tight (~100%)** | Splits on later writes | Bulk-loaded, read-only data |

- **Depth:** [[02 - Indexing]]

### Range or order query on a hash index

- **Fix:** B+ tree (the leaf chain gives order). Hash indexes are only for pure key lookups, usually in memory. **Depth:** [[02 - Indexing]]

---

## Plans and Query Shape

### Planner picks a bad plan

| Cause | Fix | Costs you |
|---|---|---|
| **Stale statistics** (table grew 10×) | `ANALYZE` / healthy autovacuum | Background work |
| **Correlated columns** (city and zip) | Extended statistics, or rewrite | Effort |
| **Generic prepared plan vs skewed parameter** | Re-plan for hot values, or split the hot case into its own query | Planning cost / code complexity |

- **Depth:** [[05 - Query Execution and Optimization]]

### A join explodes or spills

| Option | Pick when |
|---|---|
| **Index nested loop** (index on the join key) | Small outer side |
| **Hash join** (fix estimates so it's chosen) | Both sides large, equality join |
| **Sort-merge** | Inputs already ordered |
| **More work memory / filter the build side first** | Hash join spilling to disk |

- **Depth:** [[05 - Query Execution and Optimization]]

### Top-N / `ORDER BY … LIMIT` forces a full sort

- **Fix:** an index whose order matches the `ORDER BY` (after the equality columns), so the scan stops after N rows. **Depth:** [[05 - Query Execution and Optimization]]

### N+1 queries from the ORM

- **Signal:** one list query plus one query per row; hundreds of round trips.

| Option | Costs you | Pick when |
|---|---|---|
| **One `JOIN`** | Wider result rows | Relational data |
| **`WHERE id IN (…)`** batch | Two queries | Batch-fetching children |
| **Dataloader / batching layer** | Extra library | GraphQL resolvers, lazy-loading ORMs |

- **Depth:** [[05 - Query Execution and Optimization]]

### Deep pagination is slow

- **Fix:** cursor (keyset) pagination on an indexed `(timestamp, id)`; `OFFSET` scans every skipped row. Full trade-offs in [[09 - APIs Networking and Realtime]] (SQL) and [[10 - Search Geo and Analytics]] (Elasticsearch).

---

## Storage Engine and Maintenance

### "Committed" data disappears after a crash

- **Fix:** keep `fsync` on and use disks that honour flushes; a commit is only durable once its WAL record is on disk. **Depth:** [[01 - Storage Engine and Disk IO]]

### Working set doesn't fit in RAM

| Option | Costs you | Pick when |
|---|---|---|
| **Size the buffer pool** (Postgres `shared_buffers` ~25% + OS cache; InnoDB most of RAM) | RAM | First move |
| **Shrink the working set** with better indexes, smaller rows | Index cost | Scans dominate |
| **Bigger box** | Money | Before sharding |

- **Depth:** [[01 - Storage Engine and Disk IO]]

### One big scan wipes out the buffer pool

| Option | Pick when |
|---|---|
| **Scan-resistant eviction** (ring buffers, midpoint insertion) | Built into the engine |
| **Run big scans on a replica** | Reports and analytics |

- **Depth:** [[01 - Storage Engine and Disk IO]]

### Table and index bloat (Postgres)

- **Signal:** a 100 GB table holding 10 GB of live rows; update-heavy; seq scans slow down.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Tune autovacuum** | More aggressive thresholds, less throttling | Background I/O | High write rate |
| **HOT updates via lower fill factor** | Spare room lets updates stay on the page, no new index entry | Wasted space per page | Frequent updates to non-indexed columns |
| **`pg_repack`** | Online rebuild into a compact copy | Extra disk during the rebuild | Bloat already exists |
| **`VACUUM FULL`** | Rewrites the table | Exclusive lock for the whole rewrite | Maintenance windows only |
| **Short transactions** | Lets vacuum's cleanup horizon move | Discipline | Always |

- **Depth:** [[01 - Storage Engine and Disk IO]]

### A long or idle-in-transaction session blocks cleanup everywhere

- **Signal:** autovacuum runs but bloat keeps growing; InnoDB history list length climbs; a stale replication slot exists.

| Option | Costs you | Pick when |
|---|---|---|
| **`statement_timeout` / `idle_in_transaction_session_timeout`** | Some queries get killed | Always |
| **Analytics on a replica** | Replica lag | Long reads |
| **Drop stale replication slots** | Consumer must resync | Dead CDC consumers or replicas |

- **Depth:** [[01 - Storage Engine and Disk IO]], [[05 - Replication Consistency and Recovery]]

### Transaction ID wraparound (Postgres refuses writes)

- **Signal:** XID age rising toward the limit.
- **Causes and fixes:** never disable autovacuum; kill long or abandoned transactions; loosen autovacuum throttling for high write rates; avoid long exclusive locks (`ALTER`, `VACUUM FULL`) that make autovacuum skip the table. Monitor XID age.
- **Depth:** [[01 - Storage Engine and Disk IO]]

### A write firehose overwhelms B-tree in-place updates

| Option | Costs you | Pick when |
|---|---|---|
| **LSM store** (Cassandra, RocksDB) | Slower reads; compaction write amplification | Write-heavy, key-based reads |
| **Kafka → warehouse** | Not queryable as OLTP | Analytics events |
| **Batch inserts / `COPY`, fewer indexes, time partitions** | Some latency | Postgres at 1–5k writes/s |

- **Depth:** [[02 - Indexing]], [[10 - Engine Choice]], [[16 - Estimation and Decision Cheat Sheet]]

### LSM reads slow down (many SSTables per lookup)

| Option | Costs you | Pick when |
|---|---|---|
| **Bloom filters** | Memory | Always |
| **Leveled compaction** (LCS) | More write I/O | Read-heavy or frequently updated |
| **Size-tiered compaction** (STCS) | Reads touch more files; needs spare disk | Write-heavy, few updates |
| **Time-window compaction** (TWCS) | Time-ordered data only | Time series with TTL |

- **Depth:** [[02 - Indexing]], [[24 - Cassandra and DynamoDB]]

### LSM tombstone pile-up (table used as a queue)

- **Signal:** read latency climbs; Cassandra fails queries past 100,000 tombstones.

| Option | Pick when |
|---|---|
| **Model write-once-read-many** | Always the design goal |
| **TTL + TWCS instead of explicit deletes** | Expiring data |
| **Use a real queue** | It *is* a queue |
| **Tune compaction** | Deletes are unavoidable |

- **Depth:** [[02 - Indexing]], [[24 - Cassandra and DynamoDB]]

### Working set exceeds the cache in Mongo or Elasticsearch

- **Mongo:** more RAM, better indexes, smaller documents (WiredTiger cache). **Elasticsearch:** keep fast-changing fields out of the search document (merge churn), and don't aggregate on `text` fields (fielddata eats heap; use `keyword` with doc values).
- **Depth:** [[23 - MongoDB]], [[22 - Elasticsearch]]

---

## Connections

### A new database connection per request

- **Fix:** an app-side pool (Hikari, pgxpool), small N (5–20) sized with Little's law (in-flight = QPS × latency). Each Postgres connection is a process, so connections are scarce.
- **Depth:** [[06 - Connections and Network IO]]

### Too many connections from many pods

- **Signal:** 200 pods × pool 50 = 10,000 backends; `too many connections` or swapping. Serverless functions do the same thing faster.

| Option | Costs you | Pick when |
|---|---|---|
| **Shrink per-instance pools** | More queueing in the app | First move |
| **PgBouncer in transaction mode** | Breaks session features (`SET`, temp tables, some prepared statements) | Many services or instances |
| **Session mode** | Pins one backend per warm client connection, so little multiplexing | Clients that need session state |
| **Fewer, bigger pods** | Capacity planning | Over-provisioned app tier |
| **Managed DB proxy** (RDS Proxy) | Cost | Serverless callers |

- **Default:** "small pools per app, PgBouncer in transaction mode in front."
- **Depth:** [[06 - Connections and Network IO]], [[19 - Deployment and Infrastructure]]

### Pool exhaustion: requests wait for a connection

- **Fixes:** a checkout timeout (fail fast), shorter transactions, and never holding a transaction across a network call. **Depth:** [[06 - Connections and Network IO]]

### Holding tens of thousands of mostly idle clients

| Model | Costs you | Pick when |
|---|---|---|
| **Event loop** (`epoll`/`kqueue`) | One slow handler stalls every client on the loop, so heavy work goes to worker threads | Gateways, WebSockets, poolers, Redis |
| **Thread/process per connection** | Memory and context switches per client | Few clients doing heavy work (DB backends) |

- **Watch out:** an event-driven proxy in front doesn't raise how many **active** queries Postgres can run; backends and cores still cap that.
- **Depth:** [[06 - Connections and Network IO]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[02 - Indexing]] · [[05 - Query Execution and Optimization]] · [[01 - Storage Engine and Disk IO]] · [[06 - Connections and Network IO]]
