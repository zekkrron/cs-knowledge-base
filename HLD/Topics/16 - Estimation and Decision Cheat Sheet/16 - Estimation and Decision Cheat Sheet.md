---
tags: [hld/topics, status/draft]
created: 2026-09-27
updated: 2026-09-29
---
# Estimation and Decision Cheat Sheet

> [!abstract] What this is
> This is what separates "I know the boxes" from "I can defend the boxes." It has two halves: **do the arithmetic out loud**, then **let the number pick the tech**. Numbers exist to **justify one decision**; if no decision depends on it, skip the maths.
>
> Round script: [[01 - How the Round Works]] · units: [[02 - Fundamentals]]. Revise this the morning of.

---

## Part 1 — The Arithmetic

Estimation is a handful of units and five formulas. The goal isn't precision; it's getting to an order of magnitude fast enough that you can say what it means for the design.

### Units to memorise (nothing else)

A day has 86,400 seconds. Round it to **10⁵**; nobody cares. That one rounding turns every "per day" number into "per second" by dividing by 10⁵.

| Unit | Value |
|---|---|
| **1 day** | ≈ 10⁵ seconds (86,400) |
| 1 month | ≈ 2.6 × 10⁶ s |
| 1 year | ≈ 3 × 10⁷ s |
| **per-day → per-second** | divide by 10⁵ |

| Per day | ≈ QPS |
|---|---|
| 100k | **1** |
| 1M | **12** |
| 10M | **100** |
| 100M | **1.2k** |
| 1B | **12k** |

Two more that come up constantly:

- **1 KB × 1M rows = 1 GB**, and 1 KB × 1B rows = **1 TB**.
- **Peak = 2–3× average.** A spiky product (flash sale, 9am cron, match start) is **10×**, and say the word *spiky*.

### The five formulas

```
QPS         = DAU × actions_per_day / 10^5
Peak QPS    = QPS × 2..3        (×10 if bursty)
Storage     = row_bytes × rows_per_day × retention_days
              × 2   (indexes)
              × 3   (replicas, if they ask about disk bought)
Bandwidth   = QPS × payload_bytes
Concurrency = QPS × latency_seconds       <-- Little's law, the one people forget
```

**Concurrency** is the one people forget, and it's how you size pods, threads, and DB connections. It tells you how many requests are in flight at once.

```
500 QPS × 40 ms = 500 × 0.04 = 20 in flight
200 QPS × 2 s (slow LLM / HTTP) = 400 in flight   → now you need a queue or a lot of pods
```

The same formula gave `N` for the Delhivery execute workers; see [[Resume/Delhivery/02 - Catalog and Gateway]].

### How to say it

The script is always three moves: **number → pick → the trigger that changes the pick.**

> "1M DAU, say 10 writes a day each — that's 10M a day, over 10⁵ seconds, so **~100 QPS average, ~300 peak**. Row is ~200 bytes, so 2 GB a day, **~700 GB a year** before indexes. That's **one Postgres primary with a read replica**, not a sharded cluster. I'd revisit at 10× writes or a couple of TB."

> [!tip] Say the trigger out loud. "I'd revisit at X" is the single highest-value sentence in the round. It proves the choice was a judgement, not a habit.

### Worked example — read-heavy feed

A feed is the classic read-heavy case. The ratio is what justifies the cache.

```
DAU        = 5M, 20 reads + 2 writes per day
Reads      = 5M × 20 / 10^5 = 1,000 QPS avg  → ~3k peak
Writes     = 5M × 2  / 10^5 = 100 QPS avg    → ~300 peak
Read:write = 10:1                            → cache pays for itself
Egress     = 3k × 5 KB ≈ 15 MB/s             → fine
             (at 1 MB payloads: 3 GB/s       → CDN)
```

- **Pick:** PG primary + 2 replicas, Redis cache-aside with a 5-min TTL, CDN for media.
- **Revisit at:** writes > ~5k QPS (partition), or working set > the Redis box.

### Worked example — write-heavy telemetry

Telemetry is the opposite: the write volume is the whole story.

```
Samples   = 100k devices × 1 sample/s, worn 8h
          = 100k × 28,800 = 2.9 × 10^9 / day
Requests  = batched 60 s by the phone → ~1.7k requests/s, each carrying 60 samples
Raw size  = 2.9e9 × 32 B ≈ 90 GB/day   → 2.7 TB/month
```

**This is the number that kills OLTP.**

- **Pick:** metadata in PG, samples in a time-series store, **1-min rollups** for charts, raw dropped at 7–30 days.
- **Full walk:** [[03 - Wearable Device]]. Rollups and retention: [[20 - Data Processing and Analytics]].

> [!warning] The mistake is doing the maths and then ignoring it. If you computed 90 GB/day and still said "a `samples` table in Postgres," you failed the section you just did correctly.

---

## Part 2 — Thresholds That Pick the Tech

Once you have a number, these tables turn it into a choice. Each row is a range and what to say when you land in it. The thresholds are ballpark; say "roughly" and move on.

### Write QPS → storage shape

Writes are the harder thing to scale, so they decide the storage shape first.

| Sustained writes | Pick when | Say this |
|---|---|---|
| **< 1k/s** | Almost every product | One Postgres primary. Boring. "This is not a scale problem." |
| 1k–5k/s | Busy but steady | Still one primary: batch inserts, `COPY`, fewer indexes, partition by time |
| 5k–20k/s | Heavy ingest | Partition + archive cold data, or buffer through Kafka and write in batches |
| **> 20k/s sustained** | Telemetry, events, logs | Wrong engine: Cassandra / Dynamo / ClickHouse / TSDB, or shard by tenant |
| > 100k/s | Firehose | Kafka in front is mandatory; nothing synchronous survives |

### Read QPS

Reads scale by copying, so the answer climbs from replicas to caches to the edge.

| Reads | Pick when | Say this |
|---|---|---|
| < 1k/s | Default | Primary handles it |
| 1k–10k/s | Read-heavy | **Read replicas** + cache. Name replica lag out loud |
| > 10k/s on few keys | Hot keys | Redis, and consider an in-process cache for the top keys |
| > 50k/s | Very high read volume | CDN / edge if cacheable; otherwise a denormalised read model |

### Data size

Size decides whether sharding is even on the table. Most of the time it isn't.

| Size | Pick when | Say this |
|---|---|---|
| **< 100 GB** | Most OLTP | One node. **"I will not shard this."** |
| 100 GB – 1 TB | Growing | One node, right indexes, maybe time partitions |
| 1–10 TB | Large | Declarative partitioning by time/tenant + archive to S3 |
| **> 10 TB** | Very large | Shard by tenant/user, or columnar for analytics |

Row count and working set matter too:

- **< ~100M rows is fine in Postgres** with the right index.
- Billions of append-only rows → TSDB / Cassandra / ClickHouse.
- If the working set fits in RAM (< ~64 GB), the DB is basically memory-speed and sharding buys nothing.

> [!danger] "Sharding at 50 GB" is a named red flag in [[01 - How the Round Works]]. Sharding costs you cross-shard joins, rebalancing, and hot shards. Earn it with a number.

### Ballpark capability (for "will it hold?")

When they ask whether a box will hold, compare your peak number to these. Say **"ballpark."** Don't quote a benchmark you never ran.

| Thing | Ballpark per node |
|---|---|
| Redis | 100k+ ops/s |
| Postgres simple point read | 1–5 ms, tens of k QPS if cached |
| Postgres simple insert | low thousands/s (much more with batching) |
| Kafka broker | 100k+ msgs/s with batching |
| Elasticsearch index | thousands of docs/s |
| App pod | thousands of RPS if the work is trivial, tens if it calls an LLM |

### Latency budget

A p99 target is a budget you spend across hops. For a 200 ms p99 API it looks like this:

```
p99 200 ms  ≈  LB ~5  +  app ~20  +  DB 5–30  +  cache 1–2  +  slack
```

**Serial hops stack their tails.** Three calls each with p99 100 ms is **not** p99 100 ms overall. The rules that follow from that:

| Rule | Why |
|---|---|
| Max **2–3 serial** dependencies on the hot path | Every serial hop adds its tail |
| Fan out **in parallel**, then take the slowest | You pay the slowest, not the sum |
| Anything > ~500 ms or externally owned → **202 + job id** | Don't hold the request open |
| Never N+1 per item in a list | One call per item multiplies latency |

### Cache — yes or no

A cache pays off only under specific conditions. Size it by the **working set**, not the table; by Pareto, ~20% of rows serve ~80% of reads.

| Decision | Pick when |
|---|---|
| **Cache** | Read:write > **10:1**, the same keys are hot, and stale-by-seconds is acceptable |
| **Don't cache** | Unique-per-request queries (the cache never hits), money / stock reads, anything where stale is a bug |

Which pattern to say (full detail in [[06 - Caching]]):

| Pattern | Pick when |
|---|---|
| **Cache-aside** | Default. Read miss → DB → set |
| **Write-through** | You own the write path and want the cache warm (Catalog on publish) |
| **Invalidate on write + TTL as a safety net** | Always, on top of the above. TTL alone = "briefly wrong on purpose" |

Two hot-key rules: a single key over ~1k QPS gets an in-process cache with jitter, or gets split. A **stampede** (hot key expires, everyone misses) is handled with singleflight, jittered TTLs, and serve-stale-while-revalidate.

### Queue — yes or no

A queue is for decoupling, bursts, and retries. It isn't a default "for scale."

| Decision | Pick when |
|---|---|
| **Add a queue** | The work is slow and the caller doesn't need the result, you fan out to several consumers, you must absorb a burst, or you need durable retry |
| **Don't add a queue** | The caller needs the answer now, or you added it "for scale" at 200 QPS. Then say **"this is for decoupling, not throughput"** |

| Queue vs stream | What you get | Pick when |
|---|---|---|
| SQS / Rabbit | Work items, per-message ack, DLQ, no replay | Task distribution |
| **Kafka** | Replay, several consumer groups, ordering **per key**, high throughput | Event streams with many readers |

Ordering is only guaranteed **within a partition**, so partition by the entity id. **"Exactly once" does not exist end-to-end.** Say: at-least-once delivery + **idempotent consumer** ([[07 - Async Messaging]]).

### Consistency — per path, never per system

Consistency is chosen per path, not for the whole system. The deeper treatment is in [[17 - Consistency Time and Consensus]].

| Path | Model |
|---|---|
| Money, stock count, unique seat, auth | **Strong**, single-row transaction |
| Your own profile edit | Read-your-writes (read primary after write) |
| Feed, counts, search index, analytics | **Eventual**. Name the lag ("~seconds") |

### Concurrency control ladder

Pick the lightest tool that the contention level allows. Details and code in [[14 - Coordination and Concurrency]].

| Contention | Tool |
|---|---|
| Different rows | Nothing. Row-level locks are enough |
| Same row, < ~100 writes/s | `SELECT … FOR UPDATE` or optimistic `version` |
| Job queue, many workers | **`FOR UPDATE SKIP LOCKED`** ([[14 - Coordination and Concurrency]]) |
| One hot row, > ~500 writes/s | Redis `DECR` as the gate + async reconcile, or sharded counters |
| Across services | **No 2PC.** Saga + compensation + idempotency keys |

Two rules on top of the ladder. Avoid deadlocks by **locking rows in a fixed order** (sort ids), the transfer trick in [[07 - Wallet and Ledger]]. And correctness belongs in a **DB constraint**, not an `if` in the app.

### Search, geo, IDs, files

These are quick lookups: a condition and the pick that goes with it.

| Need | Pick when | Pick |
|---|---|---|
| Text search | `LIKE '%term%'` over > ~1M rows | **Elasticsearch / OpenSearch** |
| Text search | Below ~1M rows | PG `tsvector` / trigram is honest |
| Geo "near me" | Any | Geohash / S2 cell, or Redis GEO |
| IDs | Sortable + distributed | **Snowflake** |
| IDs | Unguessable in a URL | UUIDv4 / ULID |
| IDs | Auto-increment across shards | Don't |
| Files / video / any payload > a few MB | Always | **Presigned direct to S3**. Bytes never cross the app ([[04 - Multipart Upload]]) |

The search index is **eventually consistent**. Say "a couple of seconds stale" before they ask.

### Fanout (feeds, notifications)

Fanout is decided by the ratio of reads to writes and by how many followers one author has.

| Approach | Pick when |
|---|---|
| **Fanout-on-write** (push into each follower's list) | Reads ≫ writes and fanout is small |
| **Fanout-on-read** (merge at query time) | Celebrities (1M followers) |
| **Hybrid** | Real systems. Say hybrid and name the threshold (e.g. > 10k followers → read-time) |

### Realtime transport

Pick the cheapest transport that meets the need.

| Need | Pick |
|---|---|
| Updates ~once a minute, small N | Polling. Cheapest, be honest about it |
| Server → client only | **SSE** |
| Bidirectional, presence, cursors | **WebSocket** (+ sticky or a pub/sub bus between pods) |
| Mobile, app closed | Push service (APNs / FCM) |

---

## Part 3 — Trigger → Answer

When they say the left thing, the right thing is the expected answer. Learn this table; it is most of the follow-up round. The last column points to where each answer is explained.

| They say | You say | See |
|---|---|---|
| "User double-clicks / client retries" | `Idempotency-Key` with a **unique constraint**; return the first result | [[08 - Reliability]] |
| "Two users grab the same seat" | Unique row + `free → held → sold` CAS. DB decides, not the app | [[06 - Seat Booking]] |
| "Payment webhook fires twice" | `event_id` dedupe table + short-circuit if already terminal | [[07 - Wallet and Ledger]] |
| "Money" | Append-only **ledger** + balance in the **same** transaction. Never floats | [[07 - Wallet and Ledger]] |
| "We must never lose the write" | Write to the DB first, then publish: **outbox** / `SKIP LOCKED` relay | [[07 - Async Messaging]] |
| "Cross-service transaction" | Saga with compensations. Not 2PC | [[14 - Coordination and Concurrency]] |
| "Exactly once" | At-least-once + idempotent consumer. Say the phrase | [[07 - Async Messaging]] |
| "Hot key" | In-process cache, key splitting, or Redis in front | [[06 - Caching]] |
| "Cache expired and everything hit the DB" | Stampede → singleflight + jittered TTL + serve stale | [[06 - Caching]] |
| "Cache and DB disagree" | Invalidate on write, TTL as safety net, name the stale window | [[06 - Caching]] |
| "This takes 30 seconds" | **202 + job id**, poll or webhook. Don't hold the request | [[07 - Async Messaging]] |
| "We need to wait 6 hours" | Durable timer **row** + poller (`SKIP LOCKED`) → wake. Never `sleep` in a pod | [[02 - Task Scheduler]] |
| "Upload a 2 GB video" | Presigned multipart straight to S3 | [[04 - Multipart Upload]] |
| "Search by text" | Separate index, eventual, reindex from the SoR | [[09 - Storage Search and Geo]] |
| "Analytics / reports" | Separate OLAP store or replica. Never on the OLTP primary | [[20 - Data Processing and Analytics]] |
| "Counter / likes / views" | Redis `INCR`, flush periodically; or sharded counters. Exactness not required | [[06 - Caching]] |
| "Ordering matters" | Partition by entity key; ordering only inside a partition | [[07 - Async Messaging]] |
| "Only one worker should do this" | Lease with expiry + **fencing token**, not a plain lock | [[14 - Coordination and Concurrency]] · [[17 - Consistency Time and Consensus]] |
| "A pod died mid-work" | Lease expiry → row back to `pending` → another worker claims | [[14 - Coordination and Concurrency]] |
| "Abuse / too many requests" | Token bucket per user at the gateway, **429 + `Retry-After`** | [[08 - Reliability]] · [[18 - Security]] |
| "Replica is behind" | Name the lag; read primary for read-your-writes paths | [[17 - Consistency Time and Consensus]] |
| "Multi-region" | Out of scope unless they ask; then read-local / write-home | [[17 - Consistency Time and Consensus]] · [[08 - Reliability]] |
| "How do you know it works?" | Metrics (RED / USE), traces, the one alert that matters | [[11 - Services and Observability]] |

---

## Part 4 — Things That Cost You the Round

Most lost rounds aren't a missing fact. They're one of these habits, each of which reads as "hasn't thought about it."

| Mistake | Do this instead |
|---|---|
| Naming tech before requirements ("I'll use Kafka and microservices") | Requirements, then numbers, then tech |
| Doing the maths, then ignoring the answer | Let the number pick the box |
| Hedging with a menu ("SQL or NoSQL could work") | Pick one and name the trigger to switch |
| Sharding, microservices, or multi-region for a problem that fits on one box | Say "one box" and when you'd revisit |
| Caching everything, including money | Cache only reads where stale is OK |
| "Exactly-once" as a Kafka config | At-least-once + idempotent consumer |
| Holding a DB lock across an HTTP call to a third party | Commit, call, then update in a new transaction |
| Putting file bytes through the application server | Presigned direct to S3 |
| Silence | Narrate the arithmetic even when unsure |

> [!tip] If you genuinely do not know: **"I'd start with the simple thing — one Postgres, one cache — and the number that would make me change is X."** That answer scores. "I don't know" does not.

---

## Related Notes

- [[01 - How the Round Works]] — the script
- [[02 - Fundamentals]] — latency table, nines, CAP
- [[HLD/Problems/00 - Speed Run]] — all twelve in one page
- [[06 - Caching]] · [[07 - Async Messaging]] · [[14 - Coordination and Concurrency]]
- [[05 - Databases]] — engine choice depth
- [[17 - Consistency Time and Consensus]] · [[18 - Security]] · [[19 - Deployment and Infrastructure]] · [[20 - Data Processing and Analytics]]
