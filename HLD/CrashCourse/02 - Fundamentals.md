---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# Fundamentals

> [!abstract] The vocabulary every follow-up is built from. If you only remember one page, remember: **latency ≠ throughput**, **availability is nines**, **CAP is a partition choice**, and **numbers decide whether you even need a queue**.

## Latency vs throughput vs scalability

- **Latency** — time for one request. User-facing. p50 vs p99 (the tail is what they feel).
- **Throughput** — how many per second. QPS / RPS / TPS.
- **Bandwidth** — bytes per second. A 1 MB image at 1000 QPS is ~1 GB/s. Different problem from "the API is slow."
- **Scalability** — can I add machines and get more of the above.

You can have high throughput and terrible latency (a huge batch job). You can have low latency and low throughput (one fat server, 10 users).

## Vertical vs horizontal

- **Vertical** — bigger box. Simple. Hits a ceiling. Failover is "the box died."
- **Horizontal** — more boxes. Need a [[04 - Load Balancing CDN and Gateway|load balancer]], and the app should be **stateless** (session in Redis/DB, not in process memory).

> [!tip] Default line: "Stateless app servers behind a load balancer, state in DB/cache." That sentence alone is half of SDE-1 scaling.

## Availability (the nines)

| Nines | Downtime / year | What it costs you |
|---|---|---|
| 99% | ~3.65 days | hobby |
| 99.9% (three) | ~8.8 hours | typical product |
| 99.99% (four) | ~52 minutes | payments, core checkout |
| 99.999% | ~5 minutes | you will not design this in 45 min |

Availability comes from **redundancy** (replicas, multi-AZ) and **failing small** (timeouts, not hanging). You cannot talk 99.99% and then have a single Postgres with no replica.

**SPOF** — single point of failure. If you drew one box that takes the site down, say how it is redundant or why you accept it (and cache around it).

## Consistency words (use these, not poetry)

- **Strong** — after a write, every read sees it. Costs coordination / latency.
- **Read-your-writes** — *you* see your write. Sessions, profile edits. Often sticky session or "read primary after write."
- **Eventual** — replicas catch up. Feeds, counts, search indexes.
- **Causal** — if B happened after A, nobody sees B without A. Rarely needed by name at SDE-1.

> [!warning] Do not pick one model for the whole product. Inventory/payments = strong. Product description / like counts = eventual.

## CAP (and the one extra letter)

In a distributed store, a **network partition** will happen. Then you choose:

- **CP** — refuse some requests rather than serve a lie. Banking ledger, seat booking.
- **AP** — keep answering; some answers are stale. Social feed, views count.

**P is not optional** once you have more than one node. "CA" is a single-node fantasy.

**PACELC** (say it if they push): if Partitioned, A vs C; **Else** Latency vs Consistency. Even with a healthy network, waiting for a quorum is slower.

Interview default: **eventual unless money, stock count, or unique booking**.

## ACID vs BASE (one line each)

- **ACID** — a transaction is atomic, leaves valid data, doesn't see dirty writes, and a committed write survives a crash. Postgres.
- **BASE** — basically available, soft state, eventual. Dynamo/Cassandra-style. You get partition survival and write throughput; you give up a single global "the row is this."

## Back-of-envelope — only when a decision needs it

Do **not** open the interview with a maths recital. Use numbers when choosing: one DB or shard, Redis or not, sync or queue.

Memorise the units, not a spreadsheet:

```
1 day ≈ 10^5 seconds
DAU × actions/day / 10^5 ≈ average QPS
Peak ≈ 2–3× average
Storage ≈ bytes/record × records/day × retention
Hot cache ≈ ~20% of data (Pareto)
```

**Numbers to know (modern, not 2010):**

| Thing | Ballpark |
|---|---|
| Memory read | ~100 ns |
| SSD | ~100 µs |
| Same-DC RPC | 0.5–2 ms |
| Redis GET | ~1 ms |
| Postgres simple read | ~1–5 ms (cached/hot) |
| Cross-continent | 50–200 ms (speed of light is real) |
| Redis | 100k+ ops/s per instance |
| Postgres | tens of k QPS if you are not stupid; **do not shard at 50 GB** |
| App server | thousands of RPS depending on work |

> [!tip] If write QPS is 200 and data is 80 GB, you do **not** need Kafka + 16 shards. A primary + read replicas + a cache is the design.

## Capacity questions they ask

- How many servers? `peak QPS / QPS per box`, then + headroom.
- How big is the DB in a year? Don't forget indexes (~2×) and replicas.
- Why is the cache this size? Working set, not the whole table.

## Related Notes

- [[01 - How the Round Works]]
- [[05 - Databases]]
- [[10 - Distributed Building Blocks]]
