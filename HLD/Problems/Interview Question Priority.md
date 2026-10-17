---
tags: [hld, hld/problems, status/draft]
created: 2026-10-17
---
# HLD Interview Question Priority

> [!abstract] What this is
> The high-level design questions worth practising, ordered by how likely they are to come up in **SDE 1 to SDE 3 (and a bit above)** interviews in **India and similar markets** as of **October 2026**. It combines frequency counts across public question banks with what Indian product companies actually ask, and keeps **AI/LLM questions in a separate section**. Work top to bottom.

---

## How This List Was Built

- **Frequency base**: PracHub's July 2026 count of how many of 11 public question banks (Hello Interview, IGotAnOffer/Glassdoor data, ByteByteGo/Alex Xu, Exponent, Tech Interview Handbook and others) carry each prompt ([PracHub, Top 30 ranked by frequency](https://prachub.com/resources/top-30-system-design-questions-ranked-by-how-often-they-re-asked)). Mention count is a proxy for importance, not exact ask-frequency.
- **Indian-market weighting**: questions reported for Flipkart, Swiggy, Razorpay, Zepto, Meesho and similar companies ([Padhle, Apr 2026](https://padhle.substack.com/p/the-10-system-design-questions-every); [Design Gurus on Razorpay](https://www.designgurus.io/answers/detail/what-to-expect-in-the-razorpay-system-design-interview)), plus Bengaluru interview experiences on LeetCode Discuss (Uber SDE-2: product browsing with popularity; feed systems; inventory races at Amazon SDE-2). Payments, orders, inventory, delivery tracking, notifications and search are weighted up because Indian fintech, e-commerce and quick-commerce companies ask them heavily.
- **2026 trends**: AI/LLM infrastructure prompts have moved into general SWE loops, and cost, failure modes and operations are now graded explicitly ([Exponent/Aced 2026 guide](https://www.tryexponent.com/blog/system-design-interview-guide)).
- The tiers and order are my synthesis of these sources, not an official ranking. Content from the sources was rephrased for compliance with licensing restrictions.

> [!info] Level guide
> - **SDE 1**: often no HLD round, or a light one. Do Tier 1 to a solid baseline (requirements, APIs, data model, one scaling step).
> - **SDE 2**: Tier 1 deeply, plus Tier 2. Expect follow-ups on caching, consistency, idempotency and failures.
> - **SDE 3 / senior+**: everything here, with capacity math, trade-offs stated unprompted, failure modes, cost and operations.
> - Many Indian SDE 1/2 loops also have an **LLD / machine coding** round (parking lot, rate limiter class, wallet, pub-sub). That's a separate prep track ([[LLD/README]]).

---

## Tier 1: Do These First

The questions most likely to appear anywhere, and the ones Indian companies lean on most.

| # | Question | Indian flavour | Core patterns it teaches |
|---|---|---|---|
| 1 | **URL Shortener** (TinyURL/Bitly) | Flipkart, Amazon screens | Key generation (base62), read-heavy caching, capacity maths, 301 vs 302 analytics |
| 2 | **Rate Limiter** (distributed) | Razorpay, Swiggy, any API platform | Token bucket vs sliding window, atomic Redis/Lua, fail-open vs fail-closed |
| 3 | **Notification System** (push, SMS, email) | Flipkart, Meesho, Razorpay (reported senior question) | Fan-out over queues, priority lanes (OTP vs marketing), retries, DLQ, dedup, provider failover |
| 4 | **Chat / Messaging** (WhatsApp, Slack) | Common everywhere | WebSockets at scale, per-conversation ordering, delivery receipts, presence, group fan-out |
| 5 | **News Feed** (Twitter/Instagram feed) | Product companies, Uber/Meta-style loops | Fan-out on write vs read, celebrity problem, feed cache of IDs, ranking |
| 6 | **Payment System / Payment Gateway** (Stripe, Razorpay, UPI flows) | Razorpay, PhonePe, Paytm, CRED, every fintech | Idempotency keys, double-entry append-only ledger, sagas, reconciliation, webhooks |
| 7 | **Ride Hailing** (Uber/Ola) | Uber Bengaluru, Ola, Rapido | Geospatial index (geohash/H3), high-rate location writes, matching, surge |
| 8 | **Food Delivery / Order Management + Live Tracking** (Swiggy/Zomato) | Swiggy, Zomato, Zepto, Blinkit | Order state machine, sync vs async paths, real-time tracking over WebSockets, dispatch, ETA |
| 9 | **Ticket / Seat Booking** (BookMyShow, Ticketmaster) | BookMyShow, Paytm Insider, IRCTC-style | Contended inventory, seat holds with TTL, double-booking prevention, waiting room |
| 10 | **E-commerce Inventory and Flash Sale** (Big Billion Day, Zepto dark stores) | Flipkart, Amazon India, Zepto, Meesho | Atomic stock decrement, reservations with expiry, hot keys, overselling, queue-based admission |

---

## Tier 2: Very Common at SDE 2 / SDE 3

| # | Question | Indian flavour | Core patterns it teaches |
|---|---|---|---|
| 11 | **Typeahead / Autocomplete** | Flipkart, Swiggy, Zomato search boxes | Top-k per prefix, trie vs precomputed lists, freshness pipeline, sub-100 ms reads |
| 12 | **Product Search and Catalog Browsing** (Flipkart search, popular items) | Flipkart, Myntra, Meesho; Uber SDE-2 reported a "browse merchandise ranked by popularity" prompt | Elasticsearch as a read view fed by CDC/Kafka, ranking signals, facets, caching |
| 13 | **Video Streaming** (YouTube, Netflix, JioHotstar) | Hotstar-style live sports spikes | Upload and transcode pipeline, adaptive bitrate, CDN, live vs VOD |
| 14 | **Distributed Cache** (Redis-like) | Asked standalone at infra-leaning companies | Consistent hashing, eviction, hot keys, stampede protection, replication |
| 15 | **File Storage and Sync** (Dropbox/Google Drive) | Product companies, Microsoft India | Chunking, dedup, metadata store, conflict handling, multipart/resumable uploads |
| 16 | **Proximity / Nearby Search** (Yelp, nearby restaurants or dark stores) | Swiggy, Zomato, Zepto store selection | Geohash/quadtree, caching per cell, static vs moving entities |
| 17 | **Web Crawler** | Google, search-heavy teams | URL frontier, politeness, dedup with Bloom filters, distributed workers |
| 18 | **Instagram** (photo sharing) | Social/creator apps (ShareChat, Moj-style) | Feed plus media pipeline, thumbnails, CDN, metadata vs blobs |
| 19 | **Distributed Job / Task Scheduler** | Platform and fintech teams | Claiming jobs safely, at-least-once execution, retries, visibility timeouts, DLQ |
| 20 | **Leaderboard / Top-K / Trending** | Dream11, gaming, trending products | Sorted sets, sharding by score, approximate top-k, windowed counts |
| 21 | **Hotel / Travel Booking** (MakeMyTrip, OYO, Airbnb) | MakeMyTrip, OYO, Agoda India | Date-range availability, search vs booking consistency, overbooking policy |
| 22 | **Wallet and Ledger** (Paytm wallet, Splitwise-style balances) | Paytm, PhonePe, CRED | Balance consistency, idempotent transfers, ledger vs cached balance, audit |

---

## Tier 3: The Rotation

Shows up in specialised teams, senior rounds, or as follow-ups inside other questions.

| # | Question | When it comes up | Core patterns it teaches |
|---|---|---|---|
| 23 | **Distributed Message Queue** (Kafka-like) | Infra/platform roles | Partitioned log, consumer groups, offsets, delivery semantics, backpressure |
| 24 | **Distributed Key-Value Store** (Dynamo-style) | Senior, storage-adjacent teams | Partitioning, replication, quorums, CP vs AP choice |
| 25 | **Unique ID Generator** (Snowflake) | Often a sub-question | Sortable 64-bit IDs, clock skew, per-node sequences |
| 26 | **Metrics, Logging and Monitoring** (Datadog-like) | Infra/observability teams | Time-series ingest, downsampling, cardinality limits, alerting path |
| 27 | **Ad Click / Event Aggregation** | Ad-tech, analytics teams | Stream processing, watermarks, late and duplicate events, batch reconciliation |
| 28 | **Surge / Dynamic Pricing** | Swiggy, Zepto, Uber, Ola | Real-time demand/supply per zone, streaming windows, price locks at checkout |
| 29 | **Collaborative Editing** (Google Docs) | Senior, productivity tools (Atlassian, Zoho) | OT vs CRDT, central sequencer, presence, offline merge |
| 30 | **Stock Exchange / Order Matching** (Zerodha, Groww, NSE-style) | Trading and brokerage firms | In-memory single-threaded matcher, price-time priority, event-sourced recovery |
| 31 | **Google Maps / Routing and ETA** | Mapping and logistics teams | Tiles on CDN, graph partitioning, precomputed routes, live traffic weights |
| 32 | **Object Storage** (S3-like) | Storage/infra teams | Metadata vs data path, erasure coding vs replication, durability |
| 33 | **Expense Splitting** (Splitwise) | Often LLD; sometimes light HLD | Group balances, debt simplification, consistency of shared ledgers |
| 34 | **Access Control / Sharing** (Drive permissions) | SaaS and enterprise products | ACL models, permission inheritance, caching authorisation checks |
| 35 | **Project Tracker** (Jira) | Atlassian, SaaS product companies | Multi-tenant data model, workflows, search, activity streams |
| 36 | **IoT / Wearable Data Ingestion** | Device and health-tech companies | High-volume ingestion, time-series storage, offline devices, aggregation |

---

## AI and LLM Questions (Separate Track)

Asked increasingly in general SWE loops at companies shipping AI features, and as the main design round at AI-first companies. Ordered by how often they appear for SDE 2/3 roles outside pure ML teams.

| # | Question | Core patterns it teaches |
|---|---|---|
| A1 | **Customer-support chatbot on a third-party LLM** (rate limits, fallbacks, conversation state) | LLM gateway, retries and provider failover, prompt and context management, cost controls |
| A2 | **RAG / enterprise document search** (chat over company docs) | Ingestion and chunking, embeddings, vector + keyword hybrid search, permissions, grounding |
| A3 | **ChatGPT-like assistant at scale** (LLM responding to user queries) | Streaming tokens, conversation storage, rate limiting per user/tenant, model routing |
| A4 | **LLM inference / serving platform** (incl. batch inference API on a GPU cluster) | Continuous batching, KV-cache and prefix caching, GPU scheduling, queueing, TTFT vs throughput |
| A5 | **AI agent that takes actions for a user** (with safeguards) | Tool calling, permissions, human-in-the-loop approval, durable execution, audit |
| A6 | **Embedding / semantic search pipeline** (recommendations, similar items) | Batch and streaming embedding, vector index choice, re-embedding on model change |
| A7 | **AI gateway / guardrails layer** for many internal teams | Auth, quotas, PII redaction, prompt-injection defences, observability and cost attribution |
| A8 | **LLM evaluation and monitoring system** | Offline eval sets, online metrics, drift detection, LLM-as-judge pipelines |

- Related deep material already in the vault: [[Agentic/README]] (topics 00–15) and [[Agentic/Problems/README]].

---

## Suggested Order of Work

1. Tier 1 in order (10 questions). Each one introduces the patterns the rest reuse: key generation and caching, rate limiting, queues and fan-out, real-time connections, feeds, money and idempotency, geo, order state machines, contended inventory.
2. Tier 2, prioritising **12 (product search)**, **16 (proximity)** and **22 (wallet)** if targeting Indian e-commerce, food or fintech companies.
3. The AI track (A1–A4) once Tier 1 is done, if any target company ships AI features.
4. Tier 3 by target team: infra (23–27, 32), fintech/trading (28, 30), SaaS (29, 34, 35).

> [!tip] Before a specific company's loop
> Read that company's engineering blog (Swiggy Bytes, Razorpay Engineering, Flipkart Tech, Zomato, PhonePe, Uber India). Interviewers often ask a simplified version of their own system.

## Related Notes

- [[HLD/Problems/README]] · [[HLD/Topics/README]] · [[Agentic/README]] · [[LLD/README]]
