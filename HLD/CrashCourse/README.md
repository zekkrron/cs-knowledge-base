---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# HLD Crash Course

> [!abstract] One-week SDE-1 HLD: **know every building block** and walk a small design out loud. Interviewers at this level almost never want Netflix. They want vocabulary, a clean diagram, one tradeoff, and one failure mode.

This folder is **coverage**, not a catalogue of FAANG case studies. The existing [[Netflix HLD]] is a stretch read if you finish early. Do not start there.

## What they actually test

SDE-1 HLD in Indian product companies (and most startups) is:

1. Can you name the pieces and say **why this one, not that one**.
2. Can you draw **client → LB → service → cache/DB → queue/workers** and trace a read and a write.
3. Can you survive follow-ups: *what if Redis dies, what if the worker runs twice, SQL or NoSQL here*.

They are **not** testing whether you memorised Twitter's fanout paper.

> [!tip] Freshers who recently sat SDE-1 loops reported the same thing: nobody asked them to design Netflix. Repeating topics were HTTP/DNS, SQL vs NoSQL, indexing, CAP, caching, load balancers, Redis, Kafka, then a small design (URL shortener / chat / notifications / e-commerce).

## How to use this in 7 days

Read **aloud**. Silent reading does not survive a whiteboard.

| Day | Read | Then do |
|---|---|---|
| 1 | [[01 - How the Round Works]] · [[02 - Fundamentals]] · [[03 - Networking and APIs]] | Draw a request: browser → DNS → TLS → LB → service → DB. Speak it. |
| 2 | [[04 - Load Balancing CDN and Gateway]] · [[05 - Databases]] | Pick SQL vs NoSQL for: users, sessions, chat messages, analytics events. One sentence each. |
| 3 | [[06 - Caching]] · [[07 - Async Messaging]] | Add cache + queue to yesterday's diagram. Name invalidation and idempotency. |
| 4 | [[08 - Reliability]] · [[14 - Coordination and Concurrency]] · [[09 - Storage Search and Geo]] · [[10 - Distributed Building Blocks]] | Answer: 429, `SET NX EX`, `SKIP LOCKED`, fencing, snowflake, consistent hashing. |
| 5 | [[11 - Services and Observability]] + first half of [[12 - Mini Designs]] | Walk **URL shortener** and **rate limiter** in 20 min each, timer on. |
| 6 | Rest of [[12 - Mini Designs]] | Walk **notifications**, **chat**, **news feed**, **file upload**. One hard part each. |
| 7 | [[13 - Question Bank]] | Concept grill out loud. Then one random mini-design from memory. Failure questions last. |

Skip anything marked **tail** unless a follow-up names it. Everything we *know exists* and are not studying this week is listed in [[15 - Out of Scope]] — not unimportant, wrong altitude.

## Folder map

| File | What it is |
|---|---|
| [[01 - How the Round Works]] | 45/60 min script, red flags, SDE-1 vs SDE-2 bar |
| [[02 - Fundamentals]] | Latency, availability, CAP, estimates, numbers to know |
| [[03 - Networking and APIs]] | DNS, TCP/HTTP, REST/gRPC, realtime, auth, pagination |
| [[04 - Load Balancing CDN and Gateway]] | L4/L7, algorithms, reverse proxy, CDN, API gateway |
| [[05 - Databases]] | SQL/NoSQL, ACID, indexes, replication, sharding |
| [[06 - Caching]] | Patterns, eviction, stampede, Redis vs Memcached |
| [[07 - Async Messaging]] | Queue vs stream, Kafka, delivery, outbox, backpressure |
| [[08 - Reliability]] | Timeouts, retries, circuit breaker, rate limit, locks |
| [[09 - Storage Search and Geo]] | Blob/S3, search, geohash |
| [[10 - Distributed Building Blocks]] | Consistent hashing, IDs, bloom, quorum, sagas |
| [[11 - Services and Observability]] | Monolith vs MS, discovery, CQRS name, logs/metrics/traces |
| [[12 - Mini Designs]] | The designs you must be able to walk |
| [[13 - Question Bank]] | Concept grill + realistic design prompts + failure questions |
| [[14 - Coordination and Concurrency]] | Locks, leases, fencing, `SKIP LOCKED`, inbox — when *not* to Redis-lock |
| [[15 - Out of Scope]] | Named, not taught this week |

## Related Notes

- [[HLD]]
- [[Netflix HLD]] — leftover-time stretch, not this week's job
- [[LLD/Interview Approach]]
