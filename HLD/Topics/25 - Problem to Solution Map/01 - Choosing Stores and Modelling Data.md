---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Choosing Stores and Modelling Data

> [!abstract] What this is
> Problems about **where data should live and what shape it should take**: which store fits which data, when NoSQL is right, how to model documents and wide-column tables, and what to do when a second query shows up that the key wasn't designed for. Scaling those stores is in [[03 - Scaling Reads Writes and Data]]; making their queries fast is in [[02 - Query Performance and Indexing]].

---

## Picking the Store

The rule underneath every entry here: **one Postgres is the default** until a specific property of the data (write rate, shape, size, lifetime, query type) forces something else. Name that property when you leave Postgres.

### Which store for which kind of data

- **Signal:** the prompt lists several kinds of data (users, orders, sessions, chat, events, files, search) and you need to place each.

| Data | Store | Costs you | Pick when |
|---|---|---|---|
| Users, orders, bookings, money | **Postgres / MySQL** | One write primary | Default; needs transactions, constraints, ad-hoc queries |
| Sessions, cache, counters, leaderboards | **Redis** | Can lose data on failover | Losable or rebuildable, read on every request |
| Chat messages, events, IoT, logs (huge append rate) | **Cassandra / DynamoDB** | Queries must be known up front | Write firehose, key-based reads |
| Analytics events | **Kafka → warehouse** (ClickHouse, BigQuery) | Pipeline to run, not OLTP-queryable | Aggregates over many rows |
| Files, images, video | **S3**, URL/key in Postgres | Two systems | Anything over a few KB of bytes |
| Full-text / fuzzy search | **Elasticsearch**, fed async | Eventual, extra cluster | Relevance, facets, big corpus |
| Metrics, traces, logs | **Prometheus / TSDB / ClickHouse**, ELK or Loki, Jaeger | Separate ops | Time-stamped append-only points |
| Document-shaped aggregates | **MongoDB** | Weak at cross-document queries | You always load the whole aggregate |

- **Default:** Postgres for the system of record, plus exactly the extra stores the data forces, each with its reason.
- **Depth:** [[10 - Engine Choice]], [[05 - Databases]], [[07 - Data Modelling]]

### ACID vs BASE: transactional or partition-tolerant semantics

- **Signal:** "should this be SQL or NoSQL?", or a requirement that mixes per-row correctness with huge write volume.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **ACID store** (Postgres) | Transactions, constraints, one primary | Write ceiling of one box (until you shard) | Orders, money, bookings, anything with an invariant |
| **BASE store** (Cassandra, Dynamo-style) | Replicas accept writes, converge later | No single global "current value"; LWW conflicts | Partition survival and write throughput matter more than per-row truth |
| **Distributed SQL** (Spanner, CockroachDB, Yugabyte, TiDB) | Consensus per range, SQL on top | Consensus latency per write, cross-region round trips, ops cost; hot keys still hit one range | Relational writes outgrow one primary *and* still need cross-entity transactions |

- **Default:** ACID for the system of record; BASE only for data whose correctness is "roughly right, eventually".
- **Depth:** [[02 - Fundamentals]], [[10 - Engine Choice]]

### Money or a ledger

- **Signal:** payments, wallets, balances; someone proposes Cassandra "because it scales" or multi-leader writes across regions.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Append-only ledger + balance in the same transaction** (Postgres) | Every movement is an immutable row; the balance updates in the same ACID txn | Single-primary write ceiling | Always the default for money |
| **DynamoDB with condition expressions / transactions** | `version = :expected` checks, `TransactWriteItems` | ~2× capacity per transactional write | You're on AWS and need Dynamo's scale |
| **One writer region** | Money writes go to one region only | Far users pay cross-region latency | Multi-region products |
| ~~Cassandra LWT / LWW store~~ | Paxos per partition, LWW otherwise | Slow, and LWW silently drops writes | Never as the basis of a ledger |

- **Default:** "Ledger rows plus balance in one Postgres transaction, integers not floats, one writer region."
- **Depth:** [[10 - Engine Choice]], [[24 - Cassandra and DynamoDB]], [[07 - Wallet and Ledger]]

### Redis vs Memcached

- **Signal:** picking an in-memory store.

| Option | Costs you | Pick when |
|---|---|---|
| **Redis** | Single-threaded command execution | Cache *plus* locks, counters, leaderboards, pub/sub, small queues |
| **Memcached** | No types, persistence or replication | Only a simple, very high-throughput blob cache |

- **Default:** Redis. **Depth:** [[21 - Redis]]

### Cassandra vs DynamoDB

- **Signal:** you've decided on a wide-column store.

| Option | Costs you | Pick when |
|---|---|---|
| **Cassandra** | You run it; LWW conflicts; slow LWT | Very high write volume, multi-DC control, avoiding lock-in |
| **DynamoDB** | AWS lock-in, per-partition throughput limits | On AWS, zero ops, known access patterns; needs conditional writes |

- **Depth:** [[24 - Cassandra and DynamoDB]]

### Picking the right Redis data type

- **Signal:** "we'll put it in Redis" with no structure named.

| Need | Type | Watch out |
|---|---|---|
| Cache value, counter, simple rate limit | **String** (`GET/SET`, `INCR`) | Opaque bytes |
| Cached object without JSON round-trips | **Hash** | One object per key |
| Simple queue, recent-activity feed | **List** (`LPUSH`/`BRPOP`, capped) | No ack |
| Dedupe, tags, "friends in common" | **Set** | Unordered |
| Leaderboard, delayed jobs, sliding-window limit | **Sorted set** | Memory per member |
| Small durable queue with acks and replay | **Stream** + consumer groups | One key lives on one node |
| Daily active users by dense id | **Bitmap** | Needs dense ids |
| Unique counts in a few KB | **HyperLogLog** | Approximate |
| "Drivers near me" | **Geo** (`GEOADD`/`GEOSEARCH`) | Ephemeral |

- **Depth:** [[21 - Redis]]

---

## Modelling Relational Data

### A hot read needs a join on every request

- **Signal:** a feed card or comment list joins `users` for a `username` on every render.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Stay normalised + index** | Join on an indexed FK | Join cost per read | The join is measured to be fine (usually) |
| **Denormalise** | Copy `username` onto the comment row | Write amplification; copies drift | Only after measuring the join is the bottleneck |

- **Default:** normalise; denormalise one field for one hot path when numbers say so.
- **Depth:** [[07 - Data Modelling]]

### A denormalised copy drifts (user renames)

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Fan-out update in the same transaction** | Update every copy with the source | Slow, heavy writes | Stale copy would be *wrong* |
| **Async fan-out via CDC/events** | Consumers update copies | Brief staleness | Many copies, correctness can lag |
| **Accept staleness explicitly** | Leave old copies | Stale display | Cosmetic data |

- **Depth:** [[07 - Data Modelling]], [[09 - Sharding]]

### Searching inside JSONB, arrays or full text in Postgres; "within 15 km"

| Option | Costs you | Pick when |
|---|---|---|
| **GIN** (inverted index) | Write cost | Search inside documents/arrays, `tsvector` |
| **GiST** (bounding-box tree; PostGIS) | Write cost | Geo, range overlap, exclusion constraints |
| **Partial index** | Used only when the query implies its condition | Every query carries the same filter (`deleted_at IS NULL`) |
| **Unique index** | Write cost | A value must never repeat; it's also the fastest duplicate check |

- **Depth:** [[02 - Indexing]]

---

## Modelling Documents (MongoDB)

### Embed or reference? Unbounded arrays

- **Signal:** comments embedded in a post, growing toward the 16 MB document limit.

| Option | Costs you | Pick when |
|---|---|---|
| **Embed** | Document grows with every child | Always read together, and the child list is bounded |
| **Reference by id** | Extra query or `$lookup` | Children are queried alone or grow without bound |

- **Default:** embed bounded, read-together data; reference anything unbounded.
- **Depth:** [[23 - MongoDB]]

### Hot path needs joins in Mongo

| Option | Costs you | Pick when |
|---|---|---|
| **`$lookup`** in the aggregation pipeline | Slower than SQL joins, hard across shards | Occasional queries |
| **Embed what you keep joining** | Duplication | The data is document-shaped |
| **Move it to Postgres** | Migration | The data is really relational |

- **Depth:** [[23 - MongoDB]]

### Atomic change across several Mongo documents

| Option | Costs you | Pick when |
|---|---|---|
| **Reshape so one document holds it** | Modelling effort | Default; single-document writes are atomic |
| **Multi-document transactions** | Locks, cross-shard round trips | Deliberate exceptions |

- **Depth:** [[23 - MongoDB]]

### A document store meets a cross-aggregate query

- **Signal:** orders embedded per customer, then "all order lines for SKU X".

| Option | Costs you | Pick when |
|---|---|---|
| **Second collection / table for that query** | Duplication and sync | As soon as the query appears |
| **Relational model from the start** | Less schema flexibility | You can foresee cross-aggregate queries |

- **Depth:** [[07 - Data Modelling]]

### Schema drift in a schemaless collection

- **Options:** schema validation rules in Mongo, or strict application-level discipline about each collection's shape. **Depth:** [[23 - MongoDB]]

---

## Modelling Wide-Column Data (Cassandra, DynamoDB)

The rule: **the partition key is the design**. There's no optimizer to rescue a bad query, so you design one table per query and pay in duplication.

### Designing the key

- **Signal:** a new Cassandra/DynamoDB table.

| Step | What you decide | Why |
|---|---|---|
| **Start from the #1 query** | Which query must be one partition read | No scans on the hot path |
| **Partition key** | Which rows live together on one node | Decides placement and hot-spotting |
| **Clustering / sort key** | Order within the partition | Range reads like "latest 50 messages" |
| **Bound the partition** | Add a time bucket, e.g. `(chat_id, month)` | Keeps partitions well under ~100 MB |

- **Depth:** [[24 - Cassandra and DynamoDB]], [[07 - Data Modelling]]

### Second access path ("every message this user sent", "posts by hashtag")

- **Signal:** Cassandra demands `ALLOW FILTERING`, DynamoDB needs a `Scan`.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Second table keyed by the new path** | App writes both tables | Write amplification; app keeps them in sync | Default in Cassandra |
| **DynamoDB GSI** | Engine maintains a re-keyed copy | Eventually consistent; extra write capacity; a throttled GSI throttles base writes | Any attribute, can be added later |
| **DynamoDB LSI** | Same partition key, alternate sort key | Creation time only; 10 GB per partition key | Need strongly consistent reads on an alternate sort |
| **Cassandra SAI** | Storage-attached secondary index | Newer; still not free | Moderate-selectivity filters |
| ~~Built-in secondary index / materialized views~~ | | Queries every node / consistency history | Avoid |

- **Default:** "Duplicate the data into a table keyed for that query." Never "we'll scan the first table".
- **Depth:** [[24 - Cassandra and DynamoDB]], [[07 - Data Modelling]]

### Unbounded partition

- **Signal:** one chat's partition grows for years; reads, compaction and repair slow down.
- **Fix:** time-bucket the partition key (`(chat_id, month)`); scroll-back reads span several buckets. On a table with an LSI, the 10 GB item-collection cap makes this mandatory.
- **Depth:** [[24 - Cassandra and DynamoDB]]

### Fetch related entities in one DynamoDB query

- **Option:** single-table design (`PK = USER#42`, `SK = PROFILE` / `ORDER#2026-10-01`), so one `Query` returns the user and their orders. Costs you: hard to evolve; access patterns must be known up front.
- **Depth:** [[24 - Cassandra and DynamoDB]]

### DynamoDB item over 400 KB / message too big for a broker

- **Fix:** **claim check**: put the payload in S3, store or send only the pointer. Extra fetch per read.
- **Depth:** [[24 - Cassandra and DynamoDB]], [[RabbitMQ and SQS]]

---

## Data Lifetime

### Short-lived data must expire on its own

- **Signal:** sessions, tokens, OTPs, short logs piling up.

| Option | Watch out | Pick when |
|---|---|---|
| **Redis `EXPIRE`** | In memory | Session- and cache-shaped data |
| **Mongo TTL index** on a date field | Background deletes, not exact | Sessions, tokens, logs in Mongo |
| **DynamoDB TTL** | Deletion is not to-the-second; filter expired items on read | Sessions, tokens on AWS |
| **Cassandra TTL + TWCS** | Time-ordered data only | Time series |
| **Expiry column + sweeper** | A job to run | Holds and reservations in SQL |

- **Depth:** [[21 - Redis]], [[23 - MongoDB]], [[24 - Cassandra and DynamoDB]]

### Keep every event vs keep only the latest state per key (Kafka)

| Option | Costs you | Pick when |
|---|---|---|
| **Retention** (`retention.ms`/`bytes`) | Old history dropped after the window | Audit, replay, "every event" topics |
| **Log compaction** (+ tombstones to delete) | History lost on purpose; compaction is eventual | Changelogs, current state per key |

- **Depth:** [[02 - Low-Level Storage and Performance IO]]

---

## Files and Blobs

### Choosing block, file or object storage

| Option | Costs you | Pick when |
|---|---|---|
| **Block** (EBS) | Attached to one machine | A database's data directory |
| **File** (EFS/NFS) | Network and locking overhead | Many servers sharing files by path; legacy apps |
| **Object** (S3) | HTTP access only | Uploads, media, backups, data lake. The default for files |

- **Depth:** [[09 - Storage Search and Geo]]

### Same file stored many times

- **Option:** **content-addressed storage**: hash the bytes (SHA-256), store one blob per hash, keep names/owners in a metadata table pointing at the hash. Enables instant uploads and chunk-level sync. Costs you: reference counting or a sweep for deletes, and a small privacy leak ("someone already has this file").
- **Depth:** [[09 - Storage Search and Geo]]

Uploading and serving the bytes themselves (presigned URLs, multipart, CDN) is in [[09 - APIs Networking and Realtime]].

## Related Notes

- [[25 - Problem to Solution Map]]
- [[10 - Engine Choice]] · [[07 - Data Modelling]] · [[24 - Cassandra and DynamoDB]] · [[23 - MongoDB]] · [[21 - Redis]]
