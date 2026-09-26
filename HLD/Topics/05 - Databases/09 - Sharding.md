---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
---
# Sharding

> [!abstract]
> - Split data across **multiple primaries** because one box cannot take the **writes** or the **disk**
> - Replicas + cache first. Do **not** shard because it sounds senior
> - The **shard key is the whole design**
> - More shards ≠ always more throughput — same shape as Kafka partitions
> - Hash ring: [[10 - Distributed Building Blocks]]

---

## When you shard

- High **write** QPS the primary cannot take
- Multi-TB that won’t fit / restore / vacuum on one box
- Not because “we have 50k users”

### What you try first

- Indexes + query fix — [[05 - Query Execution and Optimization]]
- Cache — [[06 - Caching]]
- Read replicas — [[08 - Replication]]
- Bigger box (vertical) until it is embarrassing

---

## The key *is* the design

| Key | Good for | Pain |
|---|---|---|
| `user_id` | “My orders,” all of a user’s rows together | “Global trending” hits **every** shard |
| `hash(key) % N` | Even spread | **Adding a shard reshuffles everything** unless consistent hashing |
| Range (`date`, `id`) | Scans of a range | **Hot shard** — today’s partition eats all writes |
| Geo / tenant | Product is regional or B2B | One huge tenant is a hot shard |

- Cross-shard **JOIN** — you don’t have one
    + app-side join, or denormalise, or don’t ask the question
- Cross-shard **transaction** — not ACID across boxes
    + 2PC is painful — [[10 - Distributed Building Blocks]]
    + saga / “don’t need it”
- Secondary index without the shard key → **scatter-gather**
    + “search” = ES — [[02 - Indexing]] · [[09 - Storage Search and Geo]]

---

## Hash vs range (the Gemini pair)

### Hash

- `hash(user_id) % N` → even if ids are sequential
- Point lookup is one shard
- Range query (“users 1000–2000”) is **all shards**
- `N` changes → almost all keys move
    + **consistent hashing** moves only a neighbour arc
    + [[10 - Distributed Building Blocks]]

### Range

- Shard A: `id 1–1M`, B: `1M–2M`, or `date=2026-09-23`
- Range scan stays on one (or few) shards
- Sequential keys / “today” → **one hot shard**
    + all inserts land on the right edge
    + the other 49 shards sit idle

---

## Hot shard / hot key

- Even shard **count** ≠ even **traffic**
- Celebrity `user_id`, `country=IN`, `date=today`
- That shard’s primary is at 100%
- The others look “fine”
- More shards **do not split a single key**
    + same as a Kafka hot partition
    + [[04 - Producer Mechanics and Delivery Guarantees]]
- Fixes
    + pick a key with **cardinality**
    + isolate the whale (own shard / own table)
    + shard a **counter** into buckets if the hot thing is a single row — [[03 - Concurrency Control]]

---

## More shards ≠ always more throughput

> [!tip] Same grill as Kafka partitions
> - Throughput is `min(app, network, hottest shard, coordinator)`
> - Parallelism is “how many shards the **query** actually hits”
> - A scatter-gather of 64 shards can be **slower** than 8

| They add shards and… | What happened |
|---|---|
| Writes are still one key | Hot shard. `N` does nothing. |
| Every read is scatter-gather | You added **fan-out**. p99 got worse. |
| Reshard with `hash % N` | Massive move. Cache wipe. |
| Cross-shard txn | 2PC / saga tax per request |
| 64 tiny primaries | More failover, more connections, more ops. Each box idle. |

- What you pick
    + shard **after** numbers
    + key that matches the #1 query
    + `N` that you can operate (start 2–8, not 256)
    + plan reshard (consistent hash / range split) **before** you are on fire

---

## Directory / lookup table

- `entity_id → shard_id` in a small store
- Flexible placement (move one tenant)
- Extra hop, extra SPOF, extra cache
- Rarely the **first** answer
- Use when tenants must be **movable** and hash is too dumb

---

## Related Notes

- [[README]]
- [[07 - Data Modelling]]
- [[08 - Replication]]
- [[10 - Distributed Building Blocks]]
- [[04 - Producer Mechanics and Delivery Guarantees]] — hot key, more-n ≠ more throughput
- [[11 - Interview Questions]]
