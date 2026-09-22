---
tags: [hld/topics, status/draft]
created: 2026-09-18
updated: 2026-09-22
---
# HLD Topics

> [!abstract] Building-block notes for SDE-1 HLD. Each topic is a **folder** so a broker (Kafka, Rabbit, …) can grow underneath it. Walks of full systems live in [[HLD/Problems/README]].

This used to be called Crash Course. Same files, folder-per-topic so you can add depth without a 2,000-line dump.

## How to use this in 7 days

Read **aloud**. Silent reading does not survive a whiteboard.

| Day | Read | Then do |
|---|---|---|
| 1 | [[01 - How the Round Works]] · [[02 - Fundamentals]] · [[03 - Networking and APIs]] | Draw a request: browser → DNS → TLS → LB → service → DB. Speak it. |
| 2 | [[04 - Load Balancing CDN and Gateway]] · [[05 - Databases]] | Pick SQL vs NoSQL for: users, sessions, chat messages, analytics events. |
| 3 | [[06 - Caching]] · [[07 - Async Messaging]] | Add cache + queue. Name invalidation and idempotency. Kafka depth: [[Kafka/README]]. |
| 4 | [[08 - Reliability]] · [[14 - Coordination and Concurrency]] · [[09 - Storage Search and Geo]] · [[10 - Distributed Building Blocks]] | 429, `SET NX EX`, `SKIP LOCKED`, fencing, snowflake. |
| 5 | [[11 - Services and Observability]] + first half of [[12 - Mini Designs]] | URL shortener + rate limiter, 20 min each. |
| 6 | Rest of [[12 - Mini Designs]] | Notifications, chat, news feed, file upload. |
| 7 | [[13 - Question Bank]] | Concept grill out loud. |

## Folder map

| Folder | What it is |
|---|---|
| [[01 - How the Round Works]] | 45/60 min script |
| [[02 - Fundamentals]] | Latency, CAP, estimates |
| [[03 - Networking and APIs]] | DNS, HTTP, REST/gRPC, auth |
| [[04 - Load Balancing CDN and Gateway]] | L4/L7, CDN, gateway |
| [[05 - Databases]] | SQL/NoSQL, indexes, shard |
| [[06 - Caching]] | Redis patterns |
| [[07 - Async Messaging]] | Queue vs stream; **[[Kafka/README]]** |
| [[08 - Reliability]] | Timeouts, rate limit |
| [[09 - Storage Search and Geo]] | S3, ES, geohash |
| [[10 - Distributed Building Blocks]] | Hash ring, IDs, quorum |
| [[11 - Services and Observability]] | Monolith vs MS, telemetry |
| [[12 - Mini Designs]] | 20-min skeletons |
| [[13 - Question Bank]] | Grill + prompts |
| [[14 - Coordination and Concurrency]] | Locks, `SKIP LOCKED` |
| [[15 - Out of Scope]] | Named, not this week |

## Related Notes

- [[HLD]]
- [[HLD/Problems/README]]
- [[Netflix HLD]]
