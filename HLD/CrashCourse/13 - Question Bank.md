---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# Question Bank

> [!abstract] Nobody can promise a question. What **does** show up, repeatedly, in SDE-1 HLD across product companies: a **concept grill** (SQL/cache/Kafka/CAP) plus **one small design** from a short list. Drill the grill until answers are one sentence. Walk the designs in [[12 - Mini Designs]].

Sources distilled from fresher loops, Hello Interview "in a hurry," Grokking's problem set, and Indian product-company write-ups (Zomato, Swiggy, Flipkart, payments, Amazon-style). Company-agnostic on purpose.

> [!warning] If they ask you to design **their** product, it is still one of these skeletons: notifications, feed, booking, payments, upload, realtime track. Map it; don't invent a new universe.

---

## A. Concept grill — highest frequency

If you can answer these out loud without a diagram, you survive most SDE-1 "HLD" rounds that never become "Design Twitter."

### Networking and HTTP

- Walk a request from the browser to your DB.
- DNS? HTTPS?
- TCP vs UDP.
- REST vs gRPC. When GraphQL.
- 301 vs 302.
- PUT vs POST, idempotency.
- WebSocket vs SSE vs polling. Sticky sessions?
- JWT vs session cookies. How do you revoke a JWT?
- Offset vs cursor pagination.

### Data

- SQL vs NoSQL — for **this** table, not in general.
- What is an index? B-tree vs what a hash index cannot do.
- What does a composite index `(a,b)` actually serve?
- ACID in one minute. Isolation: dirty read, lost update.
- Replication: primary-replica, sync vs async, lag, failover.
- When do you shard? What is a bad shard key?
- Transactions across two microservices? (saga / outbox, not 2PC)

### Cache and CDN

- Cache-aside vs write-through vs write-back.
- How do you invalidate?
- Cache stampede. Hot key. Redis is down — what happens?
- Redis vs Memcached. Name one Redis structure that is not a string.
- CDN vs Redis. Who caches HTML vs profile JSON?

### Scale and theory

- Latency vs throughput.
- Horizontal vs vertical. Stateless why.
- CAP. Give a CP example and an AP example **in the same product**.
- What is a SPOF in the diagram you just drew?
- Estimate QPS from DAU. When would you not bother?

### Queues

- Why not call email on the request thread?
- Kafka partition. Ordering guarantees. Consumer group vs two groups.
- At-least-once vs exactly-once. What do **you** do about duplicates?
- Dead letter queue. Lag.
- Dual write: DB and Kafka. (Outbox.)

### Reliability

- Timeouts and retries with jitter. What should you not retry?
- Circuit breaker.
- Rate limit algorithm. Why Redis. 429.
- Idempotency-Key on payments.
- Optimistic version vs `SELECT FOR UPDATE`. When is a Redis lock the wrong answer?
- `SKIP LOCKED` for workers. What if the lock TTL expires while you still hold the work?
- Unique constraint as mutual exclusion. Fencing token in one sentence.

### Storage / search / geo

- Why not put videos in Postgres?
- Presigned URL flow.
- Why Elasticsearch, why not `LIKE`.
- How do you query "near me"?

---

## B. Designs that actually get asked (SDE-1)

### Tier 1 — treat as certain *enough* to walk cold

These dominate intern / SDE-1 / "light HLD" loops:

1. [[12 - Mini Designs#1. URL shortener — the one they will ask|URL shortener]]
2. [[12 - Mini Designs#2. Rate limiter — the other one they will ask|Rate limiter]]
3. [[12 - Mini Designs#3. Notification system — most common "product" design|Notification system]] (email/push/SMS, sometimes "notify users that X happened")
4. [[12 - Mini Designs#5. Chat (1-1 first, then groups)|1-1 chat]]
5. "Scale **this CRUD**" on a resume feature (auth, feed of *your* app, file upload)

### Tier 2 — very common once they want a "system"

6. News feed / Twitter-lite
7. File storage / share link
8. Pastebin
9. Unique ID generator (often inside #1)
10. Autocomplete
11. E-commerce: cart / checkout / inventory
12. Ticket booking / BookMyShow-lite
13. Payments backend (state + idempotency, not PCI)

### Tier 3 — know the shape, 10-minute sketch

14. Nearby places / drivers
15. Ride matching
16. Video upload+stream (CDN + transcode)
17. Web crawler
18. Search (index pipeline)
19. Metrics / logging pipeline
20. Typeahead already in #10; leaderboard (Redis sorted set)

### Classic Grokking list (coverage, not all SDE-1)

TinyURL, Pastebin, Instagram, Dropbox, Messenger, Twitter, YouTube, typeahead, rate limiter, Twitter search, crawler, newsfeed, Yelp, Uber, Ticketmaster.

If you own Tier 1–2, Tier 3 is "same blocks, different noun."

---

## C. How they twist it (the real interview)

They rarely read Grokking's title. They say:

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

## D. Failure questions — practice these last

For whatever you drew:

1. This service instance dies mid-write.
2. Redis is unreachable.
3. Replica is 30 seconds behind, user refreshes.
4. Worker processes the same Kafka message twice.
5. One user is 10% of traffic (hot key / hot shard).
6. Queue lag grows for 40 minutes.
7. Payment succeeds, your DB write fails (or the inverse).
8. Deploy: cache flush.
9. Clock skew on two rate-limiter nodes.
10. Search index is 2 minutes stale. Is that OK?

If you cannot answer #4 and #7, you are not done.

---

## E. One-liners worth memorising

- Stateless app, state in DB/cache.
- Cache-aside, delete on write, TTL as backstop.
- At-least-once + idempotent consumer.
- Outbox if you write DB and publish.
- 302 for shorteners so analytics still see the click.
- Token bucket in Redis Lua; 429 + Retry-After.
- Postgres until the numbers hurt; shard key = primary query.
- Files to S3, metadata in DB, CDN in front.
- Fanout-on-write except celebrities.
- Saga, not 2PC, across services.

---

## F. Red-flag answers they have heard too often

- Kafka+Redis+microservices in sentence one.
- "Exactly-once, Kafka has a flag."
- Shard the 20 GB database.
- WebSockets for everything "realtime."
- SQL vs NoSQL speech with no table in mind.
- Cache with no invalidation story.
- Shared database across five services.

---

## Related Notes

- [[12 - Mini Designs]]
- [[01 - How the Round Works]]
- [[README]]
