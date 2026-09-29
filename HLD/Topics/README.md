---
tags: [hld/topics, status/draft]
created: 2026-09-18
updated: 2026-09-29
---
# HLD Topics

> [!abstract] What this is
> The concept layer of HLD: one **folder** per topic, so a topic can grow deeper files underneath it (Databases and Kafka already have). Full system walks live in [[HLD/Problems/README]].
>
> Folders are numbered in the order they were written, not the order to read them. Use the groups below.

---

## How the Folders Are Grouped

The numbers are stable because every note links to folders by name. Renumbering would break those links, so grouping is done here instead.

| Group | Folder | What it covers |
|---|---|---|
| **Interview technique** | [[01 - How the Round Works]] | The script, what each level is graded on, red flags |
| | [[16 - Estimation and Decision Cheat Sheet]] | Back-of-envelope maths and the "at this number, pick that" tables |
| **Foundations** | [[02 - Fundamentals]] | Latency, availability, queueing, tail latency, CAP in brief |
| | [[03 - Networking and APIs]] | DNS, TCP/TLS, HTTP versions, REST/gRPC, API contracts, webhooks |
| **Components** | [[04 - Load Balancing CDN and Gateway]] | L4/L7, global routing, health checks, CDN, service mesh |
| | [[05 - Databases]] | Storage engines through sharding, schema changes, backups. Index: [[05 - Databases/README]] |
| | [[06 - Caching]] | Cache patterns, invalidation, eviction, Redis internals |
| | [[07 - Async Messaging]] | Queues vs logs, Kafka in depth, RabbitMQ and SQS. Index: [[HLD/Topics/07 - Async Messaging/README]] |
| | [[09 - Storage Search and Geo]] | Object storage, search internals, vector search, geo indexes |
| **Distributed systems** | [[10 - Distributed Building Blocks]] | Hashing, IDs, probabilistic structures, gossip, CRDTs, repair |
| | [[14 - Coordination and Concurrency]] | Locks, leases, fencing, `SKIP LOCKED`, leader election |
| | [[17 - Consistency Time and Consensus]] | Consistency models, clocks, Raft, quorums, multi-region data |
| **Running in production** | [[08 - Reliability]] | Timeouts, retries, circuit breakers, rate limiting, disaster recovery |
| | [[11 - Services and Observability]] | Service boundaries, tracing, SLOs, alerting |
| | [[18 - Security]] | AuthN, AuthZ, encryption and keys, tenant isolation, edge protection |
| | [[19 - Deployment and Infrastructure]] | Compute, Kubernetes, autoscaling, rollouts, zero-downtime changes |
| **Data at scale** | [[20 - Data Processing and Analytics]] | OLTP vs OLAP, warehouses, batch and stream processing |
| **Practice** | [[12 - Mini Designs]] | Small designs, one hard part each |
| | [[13 - Question Bank]] | Concept grill with one-line answers |
| **Backlog** | [[15 - Out of Scope]] | Named topics that don't have a note yet |

---

## Suggested Reading Order

Read **aloud**. Silent reading does not survive a whiteboard. Each step ends with something to say or draw without notes.

| Step | Read | Then do |
|---|---|---|
| 1 | [[01 - How the Round Works]] · [[02 - Fundamentals]] · [[03 - Networking and APIs]] | Draw a request: browser → DNS → TLS → LB → service → DB. Speak it. |
| 2 | [[04 - Load Balancing CDN and Gateway]] · [[05 - Databases]] | Pick SQL vs NoSQL for: users, sessions, chat messages, analytics events. Depth: [[05 - Databases/README]]. |
| 3 | [[06 - Caching]] · [[07 - Async Messaging]] | Add a cache and a queue. Name invalidation and idempotency. Kafka depth: [[Kafka/README]]. |
| 4 | [[08 - Reliability]] · [[14 - Coordination and Concurrency]] · [[09 - Storage Search and Geo]] · [[10 - Distributed Building Blocks]] | 429, `SET NX EX`, `SKIP LOCKED`, fencing, snowflake. |
| 5 | [[17 - Consistency Time and Consensus]] | Say, for one product, which path is linearizable and which is eventual. Draw a 3-node Raft commit. |
| 6 | [[11 - Services and Observability]] · [[18 - Security]] | Put the auth checks and the trace id on the diagram from step 1. |
| 7 | [[19 - Deployment and Infrastructure]] · [[20 - Data Processing and Analytics]] | Walk a column rename with zero downtime. Say where analytics runs and why not on the primary. |
| 8 | [[12 - Mini Designs]] | URL shortener and rate limiter, 20 minutes each, then the rest. |
| 9 | [[13 - Question Bank]] | Concept grill out loud. |

---

## Quick Revision Before an Interview

When there are hours rather than days, these four give the most back.

1. [[16 - Estimation and Decision Cheat Sheet]]: the arithmetic and the "at this number, pick that" tables.
2. [[HLD/Problems/00 - Speed Run]]: all twelve walks on one page, read aloud.
3. One full deep dive: [[02 - Task Scheduler]], and [[03 - Wearable Device]] if there is time.
4. [[01 - How the Round Works]]: the script, five minutes before you walk in.

## Related Notes

- [[HLD/README]]
- [[HLD/Problems/README]]
- [[Netflix HLD]]
