---
tags: [hld/mini-designs, status/draft]
created: 2026-09-18
updated: 2026-10-24
---
# Mini Designs

> [!abstract] What this is
> The reusable sub-systems that HLD interview problems are assembled from. Each MD note holds everything about one block: what it solves, the knobs that change between problems, its own V0 → Vn evolution, the mechanics, decision tables, numbers, failures, twist cards, operations and a one-minute explanation.
>
> Problem notes in [[HLD/Problems/README|HLD/Problems]] only say **how they wire and tune these blocks**, with their own numbers. The writing rules and templates are in `CONVENTIONS.md` under *HLD Problems and Mini Designs*.

---

## How to Use This

- Learn an MD once, deeply. Then every problem that uses it becomes "which knobs, which numbers, which twist".
- Before a problem, read its header line (the MD chain) and open the MDs you're not fluent in.
- When an interviewer throws a twist, it almost always lands in one MD's *Twist Cards* section.

---

## Catalog

All 22 MDs are written.

### A. Ingestion, Edge and Connection

| MD | Name | Solves | Status |
|---|---|---|---|
| MD-01 | [[MD-01 - Stateful Connection and Realtime Push Mesh]] | Millions of WebSocket/MQTT/SSE connections, `user_id → gateway` routing, heartbeats, reconnection storms | written |
| MD-02 | [[MD-02 - Traffic Shaper Rate Limiter and Waiting Room]] | Token bucket / sliding window in Redis Lua, local token batching, load shedding, edge waiting room with signed admission tokens | written |
| MD-03 | [[MD-03 - Presigned Direct-to-Blob Multipart and Chunk Sync Engine]] | Uploads that bypass app servers, content-addressed chunks, resumable multipart, delta sync, CDN origin shielding | written |

### B. Concurrency, State and Money

| MD | Name | Solves | Status |
|---|---|---|---|
| MD-04 | [[MD-04 - High-Contention Reservation and TTL Lease Sweeper]] | Holding seats/rooms/drivers or decrementing stock under contention, dual TTL, sweeper | written |
| MD-05 | [[MD-05 - Immutable Double-Entry Ledger and Reconciliation Loop]] | Append-only debits/credits, idempotent transitions, balance materialisation, T+1 settlement drift | written |
| MD-06 | [[MD-06 - Distributed Saga and Compensating State Machine]] | Multi-service workflows, orchestration vs choreography, outbox/CDC, compensations, forward recovery | written |
| MD-07 | [[MD-07 - Deterministic Single-Threaded Sequencer]] | Serialising mutations per partition in memory with a replicated WAL instead of distributed locks | written |

### C. Distribution, Async and Scheduling

| MD | Name | Solves | Status |
|---|---|---|---|
| MD-08 | [[MD-08 - Hybrid Fan-Out and Celebrity Graph Dispatcher]] | Push vs pull fan-out, celebrity threshold, timeline caches of IDs | written |
| MD-09 | [[MD-09 - Multi-Lane Priority Queue Retry and Vendor Failover Hub]] | Bulkheaded lanes (OTP vs bulk), delay-queue backoff, DLQ replay, provider circuit breaking | written |
| MD-10 | [[MD-10 - Partitioned Time-Bucket Job Scheduler and Leased Worker Pool]] | Cold DB → hot sorted set / time wheel, `SKIP LOCKED` / visibility timeouts, fencing tokens, per-domain politeness | written |

### D. Search, Geo, Analytics and Caching

| MD | Name | Solves | Status |
|---|---|---|---|
| MD-11 | [[MD-11 - Multi-Tier Hot-Key Shield]] | One key taking most traffic: L1 micro-cache, singleflight, logical expiry, key splitting | written |
| MD-12 | [[MD-12 - CDC-to-Inverted-Index and Real-Time Sidecar Merger]] | DB changes streamed into Elasticsearch, live price/stock joined at query time | written |
| MD-13 | [[MD-13 - Spatial Cell Indexing and Moving-Entity Trajectory Engine]] | H3 vs QuadTree, k-ring search, polygon containment, hot-cell subdivision | written |
| MD-14 | [[MD-14 - Streaming Window Watermark and Top-K Pipeline]] | Windows, watermarks, late events, HyperLogLog / Count-Min / heavy hitters, batch recount | written |

### E. Infrastructure, Storage and Enterprise

| MD | Name | Solves | Status |
|---|---|---|---|
| MD-15 | [[MD-15 - Partitioned Append-Only Log and LSM Storage Engine]] | MemTable → WAL → SSTable compaction, zero-copy, offsets, quorum repair | written |
| MD-16 | [[MD-16 - Time-Sortable ID and Token Generator]] | Snowflake IDs, Base62 range allocation, clock-skew protection, HLC, UUIDv7 | written |
| MD-17 | [[MD-17 - ReBAC Authorisation Graph and Consistent Cache]] | Relationship tuples, inherited permissions, consistency tokens against stale-cache leaks | written |
| MD-18 | [[MD-18 - Media Transcoding DAG and Adaptive Bitrate Edge Pipeline]] | GOP segmenting, parallel transcode DAG, HLS/DASH manifests, live ingest to edge | written |

### F. Scaling Foundations and AI Serving

| MD | Name | Solves | Status |
|---|---|---|---|
| MD-19 | [[MD-19 - Baseline Scaling Path]] | Stateless tier, LB, pooling, cache-aside, replicas, CDN, async offload | written |
| MD-20 | [[MD-20 - Partitioning Shard Keys and Resharding]] | Shard key choice, logical shards, online resharding, hot shards, cross-shard queries | written |
| MD-21 | [[MD-21 - Multi-Region Replication and Disaster Recovery]] | Active-passive vs active-active, RPO/RTO, fencing, home regions, CRDTs, Indian data localisation | written |
| MD-22 | [[MD-22 - LLM Inference Serving and GPU Batching]] | Continuous batching, KV/prefix cache, queueing, TTFT vs throughput, model routing (links to `Agentic/Topics/13`) | written |

---

## Problem → Mini Design Map

Numbers are from [[Interview Question Priority]]. MD-19 (baseline scaling) applies to nearly everything and is listed only where it's central.

| # | Problem | Mini designs |
|---|---|---|
| 1 | URL Shortener | MD-16, MD-11, MD-19, MD-20, MD-14, MD-02, MD-21 |
| 2 | Rate Limiter | MD-02, MD-11, MD-21 |
| 3 | Notification System | MD-09, MD-08, MD-06 |
| 4 | Chat / Messaging | MD-01, MD-08, MD-16, MD-03 |
| 5 | News Feed | MD-08, MD-11, MD-20 |
| 6 | Payment System | MD-05, MD-06, MD-09, MD-21 |
| 7 | Ride Hailing | MD-13, MD-01, MD-04, MD-10 |
| 8 | Food Delivery + Tracking | MD-06, MD-13, MD-01, MD-05 |
| 9 | Ticket / Seat Booking | MD-04, MD-02, MD-06, MD-11, MD-19, MD-20, MD-21 |
| 10 | Inventory and Flash Sale | MD-04, MD-02, MD-11, MD-06 |
| 11 | Typeahead | MD-12, MD-14 |
| 12 | Product Search and Catalog | MD-12, MD-11 |
| 13 | Video Streaming | MD-18, MD-03 |
| 14 | Distributed Cache | MD-11, MD-20 |
| 15 | File Storage and Sync | MD-03, MD-17 |
| 16 | Proximity Search | MD-13, MD-12 |
| 17 | Web Crawler | MD-10 |
| 18 | Instagram | MD-03, MD-08, MD-18, MD-11 |
| 19 | Distributed Job Scheduler | MD-10 |
| 20 | Leaderboard / Top-K | MD-14, MD-07, MD-01 |
| 21 | Hotel / Travel Booking | MD-04, MD-06 |
| 22 | Wallet and Ledger | MD-05, MD-06 |
| 23 | Distributed Message Queue | MD-15 |
| 24 | Distributed KV Store | MD-15, MD-20 |
| 25 | Unique ID Generator | MD-16 |
| 26 | Metrics, Logging, Monitoring | MD-14, MD-15 |
| 27 | Ad Click Aggregation | MD-14, MD-05 |
| 28 | Surge / Dynamic Pricing | MD-14, MD-13 |
| 29 | Collaborative Editing | MD-07, MD-01 |
| 30 | Stock Exchange | MD-07, MD-05 |
| 31 | Maps / Routing and ETA | MD-13 |
| 32 | Object Storage | MD-03, MD-15 |
| 33 | Expense Splitting | MD-05 |
| 34 | Access Control / Sharing | MD-17 |
| 35 | Project Tracker (Jira) | MD-17, MD-12, MD-08, MD-10 |
| 36 | IoT / Wearable Ingestion | MD-14 |
| A1 | Support chatbot on a third-party LLM | MD-09, MD-02, MD-22 |
| A2 | RAG / enterprise document search | MD-12, MD-17 |
| A3 | ChatGPT-like assistant | MD-01, MD-02, MD-22 |
| A4 | LLM inference platform | MD-22 |
| A5 | Agent that takes actions | MD-06 |
| A7 | AI gateway / guardrails | MD-02, MD-09 |

---

## Related Notes

- [[HLD/Problems/README]]
- [[Interview Question Priority]]
- [[01 - How the Round Works]]
- [[16 - Estimation and Decision Cheat Sheet]]
- [[25 - Problem to Solution Map]]
