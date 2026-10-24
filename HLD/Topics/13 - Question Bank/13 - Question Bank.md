---
tags: [hld/topics, status/draft]
created: 2026-09-18
updated: 2026-09-29
---
# Question Bank

> [!abstract] What this is
> Nobody can promise a question. What **does** show up, again and again, in HLD rounds at product companies is a **concept grill** (SQL, cache, Kafka, CAP) plus **one small design** from a short list. This note collects both, with a one-line answer and a pointer to where each is explained properly.
>
> Drill the grill until every answer is one sentence. Walk the designs in [[12 - Mini Designs]].

The list is distilled from fresher loops, Hello Interview's "in a hurry" material, Grokking's problem set, and write-ups from Indian product companies (Zomato, Swiggy, Flipkart, payments, Amazon-style). It is company-agnostic on purpose.

> [!warning] If they ask you to design **their** product, it is still one of these skeletons: notifications, feed, booking, payments, upload, realtime tracking. Map it; don't invent a new universe.

---

## A. Concept Grill — Highest Frequency

If you can answer these out loud without a diagram, you get through most HLD rounds that never turn into "Design Twitter." Each group is a table: the question, the one-line answer to say, and the note that explains it.

### Networking and HTTP

These test whether you know what actually happens between the browser and your service.

| Question | One-line answer | Where it's explained |
|---|---|---|
| Walk a request from the browser to your DB. | DNS → TCP + TLS → LB / gateway → stateless app → cache → DB, and back. | [[03 - Networking and APIs]] · [[04 - Load Balancing CDN and Gateway]] |
| DNS? HTTPS? | DNS turns a name into an IP; HTTPS is HTTP over TLS, which encrypts and proves the server's identity. | [[03 - Networking and APIs]] · [[18 - Security]] |
| TCP vs UDP. | TCP is ordered and reliable with a handshake; UDP is fire-and-forget, used where late data is useless. | [[03 - Networking and APIs]] |
| REST vs gRPC. When GraphQL. | REST for public APIs, gRPC for internal service-to-service, GraphQL when clients need flexible shapes from many sources. | [[03 - Networking and APIs]] |
| 301 vs 302. | 301 is cached forever by browsers; 302 makes every click hit you. | [[13 - URL Shortener\|URL shortener]] |
| PUT vs POST, idempotency. | PUT replaces a resource and is idempotent; POST creates and isn't, unless you add an idempotency key. | [[03 - Networking and APIs]] · [[08 - Reliability]] |
| WebSocket vs SSE vs polling. Sticky sessions? | Polling for rare updates, SSE for server → client, WebSocket for two-way; WS needs sticky routing or a pub/sub bus between pods. | [[03 - Networking and APIs]] |
| JWT vs session cookies. How do you revoke a JWT? | Sessions are server-side and easy to revoke; JWTs are stateless, so revoke with short expiry plus a denylist or refresh-token rotation. | [[18 - Security]] |
| Offset vs cursor pagination. | Offset gets slow and skips rows as data changes; a cursor (`WHERE id < last`) is stable and uses the index. | [[03 - Networking and APIs]] |

### Data

The data questions always mean "for **this** table," not in general.

| Question | One-line answer | Where it's explained |
|---|---|---|
| SQL vs NoSQL — for **this** table, not in general. | Name the access pattern first; relational with transactions by default, key-value / wide-column when the access is one key at huge scale. | [[05 - Databases]] |
| What is an index? B-tree vs what a hash index cannot do. | A sorted structure that avoids a full scan; a hash index does exact match only, no ranges or ordering. | [[05 - Databases]] |
| What does a composite index `(a,b)` actually serve? | Queries on `a`, or `a` and `b`, and ordering by `b` within an `a`. Not `b` alone. | [[05 - Databases]] |
| ACID in one minute. Isolation: dirty read, lost update. | All-or-nothing, valid state, isolated, durable; dirty read sees uncommitted data, lost update is two writers overwriting each other. | [[05 - Databases]] · [[17 - Consistency Time and Consensus]] |
| Replication: primary-replica, sync vs async, lag, failover. | Writes go to the primary and stream to replicas; async is faster but lags and can lose recent writes on failover. | [[05 - Databases]] · [[17 - Consistency Time and Consensus]] |
| When do you shard? What is a bad shard key? | When one node can't hold the writes or data; a bad key creates hot shards or forces cross-shard queries on the main path. | [[05 - Databases]] · [[16 - Estimation and Decision Cheat Sheet]] |
| Transactions across two microservices? | Saga / outbox, not 2PC. | [[07 - Async Messaging]] · [[14 - Coordination and Concurrency]] |

### Cache and CDN

Every cache answer needs an invalidation story.

| Question | One-line answer | Where it's explained |
|---|---|---|
| Cache-aside vs write-through vs write-back. | Cache-aside is the default; write-through keeps the cache warm; write-back risks data loss. | [[06 - Caching]] |
| How do you invalidate? | Delete on write, TTL as a safety net. | [[06 - Caching]] |
| Cache stampede. Hot key. Redis is down — what happens? | One refresher plus jitter; in-process cache or split the key; short timeouts and a circuit breaker. | [[06 - Caching]] · [[08 - Reliability]] |
| Redis vs Memcached. Name one Redis structure that is not a string. | Redis has types, persistence and replication; a sorted set for a leaderboard. | [[06 - Caching]] |
| CDN vs Redis. Who caches HTML vs profile JSON? | The CDN caches public, same-for-everyone content like HTML and assets; Redis caches private per-user JSON. | [[04 - Load Balancing CDN and Gateway]] · [[06 - Caching]] |

### Scale and Theory

These check whether you can reason about the diagram you just drew.

| Question | One-line answer | Where it's explained |
|---|---|---|
| Latency vs throughput. | Latency is how long one request takes; throughput is how many you finish per second. | [[02 - Fundamentals]] |
| Horizontal vs vertical. Stateless why. | Vertical is a bigger box, horizontal is more boxes; stateless apps let any box serve any request. | [[02 - Fundamentals]] |
| CAP. Give a CP example and an AP example **in the same product**. | During a partition, pick consistency or availability per path: seat booking is CP, the event listing is AP. | [[02 - Fundamentals]] · [[17 - Consistency Time and Consensus]] |
| What is a SPOF in the diagram you just drew? | Any box with one instance and no failover, often the DB primary or a single Redis. | [[08 - Reliability]] |
| Estimate QPS from DAU. When would you not bother? | `DAU × actions / 10^5`; skip it when no decision depends on the number. | [[16 - Estimation and Decision Cheat Sheet]] |

### Queues

Queue questions nearly always end at duplicates and dual writes.

| Question | One-line answer | Where it's explained |
|---|---|---|
| Why not call email on the request thread? | It's slow and can fail; the user shouldn't wait for it or see its errors. | [[07 - Async Messaging]] |
| Kafka partition. Ordering guarantees. Consumer group vs two groups. | Ordering only within a partition; one group splits the work, two groups each get every message. | [[07 - Async Messaging]] |
| At-least-once vs exactly-once. What do **you** do about duplicates? | At-least-once delivery plus an idempotent consumer. | [[07 - Async Messaging]] |
| Dead letter queue. Lag. | DLQ holds messages that keep failing; lag is how far consumers are behind the head. | [[07 - Async Messaging]] |
| Dual write: DB and Kafka. | Outbox. | [[07 - Async Messaging]] |

### Reliability

These are about what happens when a dependency misbehaves, and about who is allowed to do what at the same time.

| Question | One-line answer | Where it's explained |
|---|---|---|
| Timeouts and retries with jitter. What should you not retry? | Always set timeouts, retry with backoff and jitter; don't retry non-idempotent writes or 4xx errors. | [[08 - Reliability]] |
| Circuit breaker. | Stop calling a failing dependency for a while so it can recover and you fail fast. | [[08 - Reliability]] |
| Rate limit algorithm. Why Redis. 429. | Token bucket; Redis because the counter must be shared across nodes; reply 429 + `Retry-After`. | [[MD-02 - Traffic Shaper Rate Limiter and Waiting Room\|Rate limiter]] · [[08 - Reliability]] |
| Idempotency-Key on payments. | The client sends a key; a unique constraint makes a retry return the first result. | [[08 - Reliability]] · [[07 - Wallet and Ledger]] |
| Optimistic version vs `SELECT FOR UPDATE`. When is a Redis lock the wrong answer? | Optimistic for low contention, `FOR UPDATE` for high; a Redis lock is wrong when correctness must survive Redis failover. | [[14 - Coordination and Concurrency]] |
| `SKIP LOCKED` for workers. What if the lock TTL expires while you still hold the work? | Workers claim different rows without blocking; an expired lease means another worker can start, so writes need a fencing check. | [[14 - Coordination and Concurrency]] |
| Unique constraint as mutual exclusion. Fencing token in one sentence. | The DB rejects the second insert; a fencing token is an increasing number the storage checks so a stale lock holder is refused. | [[14 - Coordination and Concurrency]] |

### Storage, Search and Geo

These separate "where the bytes go" from "where the metadata goes."

| Question | One-line answer | Where it's explained |
|---|---|---|
| Why not put videos in Postgres? | Large blobs bloat the DB, backups and replication; put bytes in object storage and metadata in the DB. | [[09 - Storage Search and Geo]] |
| Presigned URL flow. | App signs a short-lived URL, client uploads straight to S3, then tells the app to save metadata. | [[09 - Storage Search and Geo]] · [[04 - Multipart Upload]] |
| Why Elasticsearch, why not `LIKE`. | `LIKE '%term%'` can't use a normal index and has no ranking; ES has an inverted index and relevance. | [[09 - Storage Search and Geo]] |
| How do you query "near me"? | Geohash / S2 cells or Redis GEO, then rank by distance. | [[09 - Storage Search and Geo]] |

---

## B. Designs That Actually Get Asked

The designs cluster into three tiers by how often they come up. Tier 1 should be walkable cold; Tier 3 only needs the shape.

### Tier 1 — treat as certain enough to walk cold

These dominate intern, entry-level, and "light HLD" loops.

| # | Design | Notes |
|---|---|---|
| 1 | [[13 - URL Shortener\|URL shortener]] | |
| 2 | [[MD-02 - Traffic Shaper Rate Limiter and Waiting Room\|Rate limiter]] | |
| 3 | [[MD-09 - Multi-Lane Priority Queue Retry and Vendor Failover Hub\|Notification system]] | Email / push / SMS, sometimes "notify users that X happened" |
| 4 | [[MD-01 - Stateful Connection and Realtime Push Mesh\|1-1 chat]] | |
| 5 | "Scale **this CRUD**" | A resume feature: auth, the feed of *your* app, file upload |

### Tier 2 — very common once they want a "system"

| # | Design | Full walk |
|---|---|---|
| 6 | News feed / Twitter-lite | |
| 7 | File storage / share link | [[04 - Multipart Upload]] |
| 8 | Pastebin | |
| 9 | Unique ID generator | Often inside #1 |
| 10 | Autocomplete | |
| 11 | E-commerce: cart / checkout / inventory | [[12 - E-commerce Checkout]] · [[09 - Inventory and Flash Sale]] |
| 12 | Ticket booking / BookMyShow-lite | [[06 - Seat Booking]] |
| 13 | Payments backend | State + idempotency, not PCI. [[07 - Wallet and Ledger]] |

### Tier 3 — know the shape, 10-minute sketch

| # | Design | Notes |
|---|---|---|
| 14 | Nearby places / drivers | |
| 15 | Ride matching | |
| 16 | Video upload + stream | CDN + transcode |
| 17 | Web crawler | |
| 18 | Search | Index pipeline |
| 19 | Metrics / logging pipeline | [[20 - Data Processing and Analytics]] |
| 20 | Leaderboard | Redis sorted set. (Typeahead is already #10.) |

### The classic Grokking list

This is for coverage; not all of it is asked at entry level: TinyURL, Pastebin, Instagram, Dropbox, Messenger, Twitter, YouTube, typeahead, rate limiter, Twitter search, crawler, newsfeed, Yelp, Uber, Ticketmaster.

> [!tip] If you own Tier 1–2, Tier 3 is "same blocks, different noun."

---

## C. How They Twist It

They rarely read Grokking's title out loud. They describe a symptom, and you're expected to hear the building block underneath.

| They say | You hear |
|---|---|
| Notify users when X, only if Y | Notifications + a **gate** (check Y before send) |
| Design our upload | Presigned S3 |
| Live order / location | SSE or WS + Redis |
| Don't get DDoS'd | Rate limiter |
| Search restaurants / docs | ES + geo or inverted index |
| Double tap on pay | Idempotency |
| Celebrity posts / flash sale | Hot key, fanout hybrid, queue |
| "What if Redis dies" | Circuit breaker, degrade |
| "Kafka vs Rabbit" | Log + many groups vs task queue |

The gate-check pattern (don't notify unless some live condition is true) is just: event → **worker that reads current state** → send or requeue. Don't skip the check.

---

## D. Failure Questions — Practice These Last

Run these against whatever you drew. They're where a good design shows it was actually thought through.

| # | Scenario | Where it's explained |
|---|---|---|
| 1 | This service instance dies mid-write. | [[08 - Reliability]] · [[14 - Coordination and Concurrency]] |
| 2 | Redis is unreachable. | [[06 - Caching]] |
| 3 | Replica is 30 seconds behind, user refreshes. | [[05 - Databases]] · [[17 - Consistency Time and Consensus]] |
| 4 | Worker processes the same Kafka message twice. | [[07 - Async Messaging]] |
| 5 | One user is 10% of traffic (hot key / hot shard). | [[06 - Caching]] · [[05 - Databases]] |
| 6 | Queue lag grows for 40 minutes. | [[07 - Async Messaging]] |
| 7 | Payment succeeds, your DB write fails (or the inverse). | [[07 - Wallet and Ledger]] |
| 8 | Deploy: cache flush. | [[06 - Caching]] · [[19 - Deployment and Infrastructure]] |
| 9 | Clock skew on two rate-limiter nodes. | [[MD-02 - Traffic Shaper Rate Limiter and Waiting Room\|Rate limiter]] · [[17 - Consistency Time and Consensus]] |
| 10 | Search index is 2 minutes stale. Is that OK? | [[09 - Storage Search and Geo]] |

> [!danger] If you cannot answer #4 and #7, you are not done.

---

## E. One-Liners Worth Memorising

These are the default answers. Each one is a complete sentence you can say without elaborating, and each has a note behind it if they push.

| Say this | Behind it |
|---|---|
| Stateless app, state in DB / cache. | [[02 - Fundamentals]] |
| Cache-aside, delete on write, TTL as backstop. | [[06 - Caching]] |
| At-least-once + idempotent consumer. | [[07 - Async Messaging]] |
| Outbox if you write DB and publish. | [[07 - Async Messaging]] |
| 302 for shorteners so analytics still see the click. | [[12 - Mini Designs]] |
| Token bucket in Redis Lua; 429 + Retry-After. | [[08 - Reliability]] |
| Postgres until the numbers hurt; shard key = primary query. | [[05 - Databases]] |
| Files to S3, metadata in DB, CDN in front. | [[09 - Storage Search and Geo]] |
| Fanout-on-write except celebrities. | [[12 - Mini Designs]] |
| Saga, not 2PC, across services. | [[14 - Coordination and Concurrency]] |

---

## F. Red-Flag Answers They Have Heard Too Often

Each of these signals habit instead of judgement. The fix is usually to name the requirement or the number first.

| Red flag | Why it hurts |
|---|---|
| Kafka + Redis + microservices in sentence one. | Tech before requirements |
| "Exactly-once, Kafka has a flag." | Not true end-to-end; you still need idempotent consumers |
| Shard the 20 GB database. | Fits on one node; sharding costs joins and rebalancing |
| WebSockets for everything "realtime." | SSE or polling is often enough |
| SQL vs NoSQL speech with no table in mind. | The choice depends on the access pattern |
| Cache with no invalidation story. | Stale data with no plan |
| Shared database across five services. | Couples every service to one schema |

---

## Related Notes

- [[12 - Mini Designs]]
- [[01 - How the Round Works]]
- [[16 - Estimation and Decision Cheat Sheet]]
- [[README]]
