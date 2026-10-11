---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Scaling Reads, Writes and Data

> [!abstract] What this is
> Problems where **one box isn't enough**: sizing, the order to try things in, read and write scaling ladders, sharding and shard keys, hot shards, hot keys and hot rows, queries that fan out to every shard, and placing keys on nodes. The numbers behind the thresholds are in [[16 - Estimation and Decision Cheat Sheet]].

---

## Sizing First

### Will one box hold this?

- **Signal:** "will it scale?", or a design that proposes Kafka and 16 shards for 200 writes/s.

| Quantity | Formula / ballpark |
|---|---|
| **Average QPS** | DAU × actions per day ÷ ~10⁵ |
| **Peak QPS** | 2–3× average (10× if spiky) |
| **Storage** | row bytes × rows × retention × ~2 (indexes) × replicas |
| **In-flight requests** | QPS × latency (Little's law): 200 QPS × 2 s = 400 concurrent |
| **Ballpark per node** | Redis 100k+ ops/s · Postgres point read 1–5 ms, tens of k cached QPS · Postgres inserts low thousands/s (more batched) · Kafka broker 100k+ msg/s · ES thousands of docs/s · app pod thousands of RPS |

- **Default:** do the arithmetic, say "ballpark", and only add components the numbers force.
- **Depth:** [[16 - Estimation and Decision Cheat Sheet]], [[02 - Fundamentals]]

### The app tier can't scale past one box

| Option | Costs you | Pick when |
|---|---|---|
| **Horizontal: stateless servers behind a load balancer** | State must live outside (sessions in Redis/DB) | App servers, almost always |
| **Vertical: bigger box** | Hardware ceiling; usually a restart | Early stage; a DB primary you don't want to shard yet |

- **Depth:** [[02 - Fundamentals]], [[19 - Deployment and Infrastructure]]

### p99 spikes on small traffic bumps (running hot)

- **Signal:** "we're at 85% CPU, we have room."
- **Fix:** plan for ~60–70% utilisation, because queueing delay grows like 1/(1 − utilisation); autoscale on latency or queue depth rather than CPU alone.
- **Depth:** [[02 - Fundamentals]]

---

## Scaling Reads

### Read QPS too high for the primary

| Read rate (ballpark) | Move | Costs you |
|---|---|---|
| Under ~1k/s | Primary is fine | Nothing |
| 1k–10k/s | **Read replicas + cache-aside** | Replica lag (name it) |
| >10k/s on a few keys | **Redis + in-process cache** for the top keys | Staleness, invalidation |
| >50k/s | **CDN / edge** if cacheable, else a **denormalised read model** (CQRS) | Eventual consistency |

- **Default:** "primary + replicas + Redis cache-aside, CDN for media; revisit when writes pass ~5k/s or the working set outgrows Redis."
- **Depth:** [[16 - Estimation and Decision Cheat Sheet]], [[08 - Replication]], [[06 - Caching]]

### Analytics or long scans load the primary

| Option | Costs you | Pick when |
|---|---|---|
| **Run them on a read replica** | Still row-oriented; heavy queries cause lag | Light reporting, first step |
| **Columnar store fed by CDC** | A pipeline to run | Real analytics |

- **Depth:** [[20 - Data Processing and Analytics]]

---

## Scaling Writes and Data Size

### Write QPS decides the storage shape

| Sustained writes | Move |
|---|---|
| Under ~1k/s | One Postgres primary; "not a scale problem" |
| 1k–5k/s | Same primary with batch inserts, `COPY`, fewer indexes, time partitions |
| 5k–20k/s | Partition and archive cold data, or buffer through Kafka and batch-write |
| Over ~20k/s | Cassandra / DynamoDB / ClickHouse / TSDB, or shard by tenant |
| Over ~100k/s | Kafka in front is mandatory |

- **Depth:** [[16 - Estimation and Decision Cheat Sheet]]

### Should I shard? (data size)

| Data size | Move |
|---|---|
| Under ~100 GB | One node. Say "I will not shard this." |
| 100 GB–1 TB | One node, right indexes, maybe time partitions |
| 1–10 TB | Declarative partitioning by time/tenant + archive to S3 |
| Over ~10 TB | Shard by tenant/user, or columnar for analytics |

- **Default ladder before sharding:** indexes and query fixes → cache → read replicas → bigger box → **then** shard. Sharding costs cross-shard joins and transactions, rebalancing and hot shards; "sharding at 50 GB" is a red flag.
- **Depth:** [[09 - Sharding]], [[16 - Estimation and Decision Cheat Sheet]]

### Relational writes outgrow one primary but need cross-entity transactions

| Option | Costs you | Pick when |
|---|---|---|
| **Sharded Postgres** (hand-rolled, Citus, Vitess) | No cross-shard joins/transactions unless co-located; resharding is yours | A clean shard key keeps transactions on one shard |
| **Distributed SQL** (Spanner, CockroachDB, Yugabyte, TiDB) | Consensus latency per write; ops cost | Cross-entity or multi-region strong writes |
| **Stay single primary** | One write box | Writes still fit |

- **Depth:** [[10 - Engine Choice]], [[09 - Sharding]]

---

## Shard Keys

### Picking the shard key

- **Rule:** pick it from the **#1 query** (and the most frequent joins), so that query hits one shard.

| Key | Good for | Pain | Pick when |
|---|---|---|---|
| **`user_id`** | All of a user's rows together | Global queries hit every shard | Most queries are scoped to one user |
| **`hash(key)`** | Even spread | Range queries on the key scatter | Point lookups, uniform load |
| **Range (`date`, `id`)** | Range scans stay local | Today's shard eats every write | Range scans dominate and writes aren't sequential |
| **Geo / tenant** | Regional or B2B | One whale tenant is a hot shard | Data belongs to a region or customer |
| **Compound** (high-cardinality first) | Spread + locality | Query must include the prefix | Mongo / Cassandra keys |

- **Depth:** [[09 - Sharding]], [[23 - MongoDB]]

### Monotonic shard key sends every new write to one shard

- **Signal:** sharding by `created_at` or an auto-increment id; one shard at 100%.
- **Fixes:** hash the key; use a compound key starting with something high-cardinality (`user_id`); in Cassandra/Dynamo add a bucket or shard suffix to the partition key.
- **Depth:** [[23 - MongoDB]], [[24 - Cassandra and DynamoDB]]

### The wrong shard key was chosen

| Option | Costs you |
|---|---|
| **Mongo `reshardCollection`** (5.0+) | Rewrites the whole collection |
| **Expand–migrate–contract into a new layout** | Several deploys, dual writes, backfill |
| **Directory / lookup table** going forward | Extra hop, see below |

- **Depth:** [[23 - MongoDB]], [[12 - Schema Changes and Backups]]

### Tenants must be movable one at a time

- **Option:** a **directory** (`tenant_id → shard_id`) instead of a formula. Move a whale by copying its data and updating one row. Costs you an extra hop, a single point of failure (replicate it), and a cached copy to keep consistent (the old shard rejects data it no longer owns, the router refreshes and retries).
- **Default:** not the first answer; use it when hashing is too blunt to place tenants individually.
- **Depth:** [[09 - Sharding]]

---

## Hot Spots

### Hot shard / hot key (celebrity user, `country = IN`, `date = today`)

- **Signal:** one shard or partition at 100% while the others idle; adding shards or partitions changes nothing, because one key always lives on one shard.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Higher-cardinality key** | Many evenly used values (`user_id` not `country`) | Redesign | Designing up front |
| **Isolate the whale** | Dedicated shard, table, topic or index for the giant | Ops overhead | A few known giants |
| **Split / salt the key** | `item_42#0..#9`, read all and merge | Read fan-out; per-key ordering lost | Viral item, write-hot key |
| **Cache the hot reads** | In-process cache, Redis, DAX | Staleness | Read-hot key |
| **Adaptive capacity** (DynamoDB, automatic) | Splits a hot partition | One key is still capped (~1k WCU / 3k RCU) | Mild skew |

- **Depth:** [[09 - Sharding]], [[24 - Cassandra and DynamoDB]], [[04 - Producer Mechanics and Delivery Guarantees]]

### Hot row: one counter or row takes thousands of writes per second

- **Signal:** likes on a viral post; every `+1` queues on one row lock; faster hardware doesn't help.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Sharded counter** | N bucket rows, increment a random one, `SUM` on read | Reads cost N rows; awkward for "never below zero" | Exact count, write-heavy (likes, views, votes) |
| **Redis `INCR` + periodic flush to the DB** | Redis absorbs increments, DB reconciled later | Durability window; Redis isn't the source of truth | Real-time feedback needed |
| **Append events and sum (event sourcing + CQRS)** | One row per event, a background job aggregates into a read model | Eventual totals, more rows | Very high write rate, or you want the audit trail |
| **Redis `DECR` gate + async reconcile** | Redis decides admission, DB catches up | Reconciliation logic | Hot stock in a flash sale |
| **Atomic `UPDATE … WHERE qty > 0` on one row** | Serialised on the row lock | Caps at one row's rate | Write rate fits one row |

- **Default:** sharded counter for exact counts, Redis `INCR` when near-real-time and approximate durability is fine.
- **Depth:** [[03 - Concurrency Control]], [[14 - Coordination and Concurrency]], [[09 - Sharding]]

### Hot stock level split across buckets must never go negative

- **Option:** decrement only a bucket that still has stock (fall back to another bucket when one is empty), or keep a single row with an atomic conditional `UPDATE` if its rate fits. No single bucket knows the real total.
- **Depth:** [[09 - Sharding]]

---

## Cross-Shard Queries

### Query on a non-shard-key column (look up by email when sharded by `user_id`)

- **Signal:** the router sends the query to every shard (**scatter-gather**); p99 is set by the slowest shard.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Put the shard key in the query** | API or flow carries `user_id` | Constrains the API | Whenever possible |
| **Lookup table** `email → user_id`, sharded by email | One extra point lookup | Extra hop; keep it in sync | Exact-match secondary keys |
| **Elasticsearch as a global secondary index** (via CDC) | Search ES, get the id, single-shard read | Eventual; extra cluster | Search and fuzzy matching |
| **Local secondary index** | Index per shard | Only helps once the shard key is known | Combined with the shard key |
| **GSI** (DynamoDB) | Engine-maintained re-keyed copy | Eventual, extra write cost | On DynamoDB |
| **Accept scatter-gather** | Broadcast and merge | Every shard works every request | Rare queries only |

- **Depth:** [[02 - Indexing]], [[09 - Sharding]], [[23 - MongoDB]]

### A cross-shard JOIN is needed

| Option | Costs you | Pick when |
|---|---|---|
| **Co-locate**: shard both tables on the same key | Key constraints | The hot joins share a key (`users` and `orders` by `user_id`) |
| **Reference tables** copied to every shard | Updates go everywhere | Small, rarely changing tables |
| **Join in the app** | Extra round trips you own | Small fan-out |
| **Denormalise** the needed fields | Sync cost | Hot query |
| **Analytics store via CDC** | Lag | Cross-cutting reports |
| ~~Network shuffle join~~ | Rows stream between machines | Avoid on the hot path |

- **Depth:** [[09 - Sharding]]

### Cross-shard aggregation or top-N

- **Option:** push the work down: each shard returns a sub-total or its local top N, and the coordinator merges (Citus, Vitess do this). Deep pagination across shards stays expensive.
- **Depth:** [[09 - Sharding]]

### Uniqueness across shards

- **Mongo:** a unique index must start with the shard key. **Elsewhere:** a lookup/uniqueness table sharded by the unique value, or a conditional insert in that table's shard (`INSERT … ON CONFLICT`, DynamoDB `attribute_not_exists`, Cassandra LWT for rare cases).
- **Depth:** [[23 - MongoDB]], [[24 - Cassandra and DynamoDB]]

### Added shards but throughput didn't improve (or p99 got worse)

| What happened | Fix |
|---|---|
| Writes concentrate on one key | Hot-key fixes above; more shards don't split a key |
| Every read is scatter-gather | Make the #1 query single-shard; fan-out to more shards is slower |
| Real bottleneck is elsewhere | Throughput ≈ min(app, network, hottest shard, coordinator) |
| 64 tiny primaries | Pick an operable N (start 2–8) |

- **Depth:** [[09 - Sharding]]

---

## Placing Keys on Nodes

### Adding or removing a node remaps almost every key

- **Signal:** `hash(key) % N`; cache hit rate collapses after scaling.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **`hash % N`** | Modulo | Changing N moves ~all keys | N never changes |
| **Consistent hashing + virtual nodes** | Keys and nodes on a ring; a key belongs to the next node clockwise | Ring/routing table; `O(log N)` lookup | Large, changing clusters (Cassandra, cache clients) |
| **Rendezvous (HRW)** | Highest `hash(key, node)` wins | `O(N)` per lookup | Small–medium node sets; natural top-k replicas |
| **Fixed slots** (Redis Cluster's 16,384) | Hash into slots, assign slot ranges to nodes | Move slots on resize | Redis Cluster |
| **Range splits** | Split a hot range in two | Hot-range risk | Range-sharded stores |

- **Default:** consistent hashing with virtual nodes.
- **Depth:** [[10 - Distributed Building Blocks]]

### One node owns a huge arc, or a failed node dumps its load on one neighbour

- **Fix:** virtual nodes (many ring positions per box, weighted by capacity), so load and failover spread evenly. Remember that a balanced ring needs **high-cardinality keys**; one hot key is still one position.
- **Depth:** [[10 - Distributed Building Blocks]]

### Multi-key command rejected in Redis Cluster

- **Fix:** hash tags (`{user:42}:cart`, `{user:42}:profile`) so related keys share a slot. Costs you: can concentrate load on one node.
- **Depth:** [[21 - Redis]]

---

## IDs

### Globally unique IDs across shards

| Option | Costs you | Pick when |
|---|---|---|
| **Snowflake** (time + worker id + sequence, 64-bit) | Needs worker ids; must refuse ids if the clock goes backwards | High-volume, time-sortable (orders, tweets) |
| **UUID v7 / ULID** | 128-bit | Sortable without a service |
| **UUID v4** | Random inserts into B-trees; unsortable | Opaque, unguessable ids in URLs |
| **Range allocator** (hand out blocks of 1,000) | Gaps on crash | Short numeric ids (URL shortener) |
| **Redis `INCR`** | Redis on the write path | Low volume |
| ~~Auto-increment per shard~~ | Collides across shards | Never across shards |

- **Depth:** [[10 - Distributed Building Blocks]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[09 - Sharding]] · [[16 - Estimation and Decision Cheat Sheet]] · [[10 - Distributed Building Blocks]] · [[08 - Replication]]
