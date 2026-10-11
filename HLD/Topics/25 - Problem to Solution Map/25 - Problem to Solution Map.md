---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Problem to Solution Map

> [!abstract] What this is
> One place that maps every recurring design problem in the HLD notes to its standard solutions, with what each costs and when to pick it. The topic notes explain things deeply and repeat them where useful; this folder is the **index in your head**: "I see this symptom, these are my 2–4 moves, and this is the one I'd say first."
>
> Every entry links back to the note that explains it properly. Nothing here is new theory.

---

## How to Use It

Each file groups problems by the **kind of trouble** they are, not by tool, because in an interview you meet the symptom first ("one row gets 10k increments a second") and the tool second. Every problem has the same shape:

- **Signal**: how it shows up in a prompt or in production.
- **Options table**: each standard move, how it works, what it costs you, and when it's the right pick.
- **Default**: the answer to say first if nothing in the prompt pushes you elsewhere.
- **Depth**: the note to open when you need the full explanation.

When a problem genuinely belongs to two areas (a hot row is both a scaling and a concurrency problem), it lives in one file and the other points to it.

> [!tip] Revision drill
> Cover the options table, read only the heading and signal, and say the options and the default out loud. Then check.

---

## The Files

| File | What kind of trouble |
|---|---|
| [[01 - Choosing Stores and Modelling Data]] | Which store for which data, SQL vs NoSQL, money, files, document and wide-column modelling, second access paths, expiry |
| [[02 - Query Performance and Indexing]] | Slow queries, index design, planner surprises, N+1, storage-engine maintenance (bloat, vacuum, LSM), DB connections |
| [[03 - Scaling Reads Writes and Data]] | Will one box hold it, read and write ladders, sharding, shard keys, hot shards and hot rows, scatter-gather, IDs, consistent hashing |
| [[04 - Concurrency and Correctness]] | Lost updates, double booking, holds, deadlocks, write skew, isolation levels, locks and leases, job claiming, idempotency |
| [[05 - Replication Consistency and Recovery]] | Consistency per path, replica lag, failover loss, split brain, quorums, conflicts, clocks, consensus, multi-region, DR and backups |
| [[06 - Async Messaging and Workflows]] | Queue choice, outbox, exactly-once, ordering, lag and backpressure, poison messages, Kafka durability, sagas, delayed work |
| [[07 - Caching and the Edge]] | Whether and how to cache, invalidation, stampedes, hot keys, Redis failure modes, CDN behaviour |
| [[08 - Overload and Failure Handling]] | Timeouts, retries, circuit breakers, bulkheads, tail latency, rate limiting, throttling, load shedding, health checks |
| [[09 - APIs Networking and Realtime]] | API style and evolution, pagination, push, WebSockets, webhooks, connections and HTTP versions, load balancers, DNS, file transfer |
| [[10 - Search Geo and Analytics]] | Full-text search and Elasticsearch, vector search, geo queries, OLAP, batch and stream processing, approximate counting |
| [[11 - Security]] | AuthN and AuthZ, tokens, secrets, encryption, tenant isolation, abuse and edge protection, compliance |
| [[12 - Services Deployment and Operations]] | Service boundaries, observability, autoscaling, deploy strategies, feature flags, zero-downtime schema and API changes |

---

## What Is Deliberately Not Here

Interview technique (the round script, how to draw, red flags), the practice designs and the question banks stay in their own notes: [[01 - How the Round Works]], [[12 - Mini Designs]], [[13 - Question Bank]], [[05 - Databases/11 - Interview Questions]], [[08 - Interview Questions]]. Topics without a note yet are in [[15 - Out of Scope]].

## Related Notes

- [[16 - Estimation and Decision Cheat Sheet]] — the numeric thresholds ("at this number, pick that") that many defaults here rely on
- [[HLD/Topics/README]]
