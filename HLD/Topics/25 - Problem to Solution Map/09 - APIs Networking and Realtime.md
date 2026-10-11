---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# APIs, Networking and Realtime

> [!abstract] What this is
> Problems at the **boundary between clients and services**: API style and evolution, errors and pagination, pushing updates to clients, WebSockets at scale, webhooks, connection cost and HTTP versions, load balancers and DNS, and moving large files.

---

## API Design

### Which API style?

| Style | Costs you | Pick when |
|---|---|---|
| **REST** | Over- or under-fetching | Default public API; cacheable, CDN-friendly |
| **GraphQL** | Expensive "join the world" queries; hard to cache | Many clients needing different shapes |
| **gRPC** | Browsers don't speak it natively | Service-to-service inside the datacenter |

- **Default:** "REST at the edge, gRPC inside."
- **Depth:** [[03 - Networking and APIs]]

### The API must change without breaking old clients

| Option | Costs you | Pick when |
|---|---|---|
| **URL versioning** (`/v1`, `/v2`) | Two code paths | Public APIs; interview default |
| **Header / dated versioning** (Stripe-style) | Harder to test; caches must vary on the header | Stable URLs, fine-grained versions |
| **Additive-only changes** + tolerant readers | Old fields live forever | Internal APIs you control; protobuf with reserved field numbers |
| **Deprecation window** | Running both for a while | Any breaking change |

- **Also:** clients ignore unknown fields and treat new enum values carefully; during rolling deploys old and new pods coexist, so only additive changes are safe mid-rollout.
- **Depth:** [[03 - Networking and APIs]], [[19 - Deployment and Infrastructure]]

### Clients can't handle errors consistently or don't know when to retry

- **Fix:** one error body (`code`, `message`, `request_id`; RFC 9457 problem+json) and a stated retry policy: retry 5xx, 429 and timeouts, and only idempotent or keyed requests, honouring `Retry-After`.
- **Depth:** [[03 - Networking and APIs]]

### A retried POST creates a duplicate

- **Fix:** `Idempotency-Key` on mutating endpoints (see [[04 - Concurrency and Correctness]]), or naturally idempotent `PUT`/`DELETE` when the client knows the full new state.
- **Depth:** [[03 - Networking and APIs]], [[08 - Reliability]]

### Feed pagination shows duplicates or skips; deep pages are slow

| Option | Costs you | Pick when |
|---|---|---|
| **Offset** (`LIMIT/OFFSET`) | Duplicates/skips when rows are inserted; scans every skipped row | Small, stable lists; admin tables with page numbers |
| **Cursor / keyset** (`WHERE (ts, id) < (?, ?)` on an index) | No jumping to page N | Default for feeds and anything large or changing |

- **Depth:** [[03 - Networking and APIs]]

### Long-running request ("takes 30 seconds")

- **Fix:** return **202 Accepted** with a job id; the client polls or gets a webhook. **Depth:** [[16 - Estimation and Decision Cheat Sheet]]

### URL shortener: 301 or 302?

| Code | Effect | Pick when |
|---|---|---|
| **301** | Browsers cache it; fewer hits, no analytics | Permanent, analytics don't matter |
| **302** | Every click reaches you | You need click analytics or may change the target |

- **Depth:** [[03 - Networking and APIs]]

---

## Pushing Updates to Clients

### The server must push updates

| Option | Costs you | Pick when |
|---|---|---|
| **Short polling** | Wasted requests | Infrequent checks (once a minute), small N |
| **Long polling** | Held requests | Fallback when streaming isn't available |
| **SSE** (one long HTTP response, auto-reconnect) | Server → client only | Order status, live scores, notifications |
| **WebSocket** | Persistent connections need a gateway/L4 LB and cross-node delivery | Both sides talk continuously (chat, presence, cursors) |
| **APNs / FCM** | Provider dependency | Mobile app closed |

- **Default:** "realtime ≠ WebSockets": SSE unless the client also streams.
- **Depth:** [[03 - Networking and APIs]], [[16 - Estimation and Decision Cheat Sheet]]

### A message must reach the node holding a user's WebSocket

| Option | Costs you | Pick when |
|---|---|---|
| **Pub/sub between nodes** (Redis pub/sub) | Fire-and-forget; one more component | Default; any node can deliver |
| **Sticky routing** (consistent hashing on user) | Rebalancing on scale; design to avoid if you can | Some in-memory per-user state |
| **Durable channel** (Redis Streams, Kafka) | More infra | Messages must survive a subscriber being offline |

- **Depth:** [[03 - Networking and APIs]], [[21 - Redis]]

### A WebSocket node dies and drops all its users

- **Fix:** clients reconnect with backoff (and jitter, or they stampede); pub/sub means any node can serve them after reconnecting.
- **Depth:** [[03 - Networking and APIs]]

---

## Webhooks

### Webhook forgery, replay or duplicates

- **Signal:** a public endpoint; providers retry; "isn't HTTPS enough?" (no: TLS secures the pipe, anyone can open one).

| Defence | Stops |
|---|---|
| **HMAC signature** with a shared secret, constant-time compare; rotate the secret | Forged senders |
| **Signed timestamp**, reject events older than a few minutes | Replays |
| **Dedupe on `event_id`** (unique constraint) + state check | Retries and duplicates |
| **Return 2xx fast, process from a queue** | Provider timeouts and retry storms |

- **Rule:** for payments, the webhook (processed idempotently) is the source of truth, not the browser redirect.
- **Depth:** [[03 - Networking and APIs]]

---

## Connections and Transport

### Cold connections are slow

- **Signal:** new TCP + TLS per call, about three round trips before the first byte.

| Option | Watch out | Pick when |
|---|---|---|
| **Keep-alive + connection pools** | Size with Little's law | Always: service → DB, service → service, gateway → backends |
| **TLS session resumption / 0-RTT** | 0-RTT data can be replayed | Repeat clients; idempotent requests only |
| **Terminate TLS at the edge** (CDN/LB near users) | Edge keeps warm origin connections | Global users |

- **Depth:** [[03 - Networking and APIs]]

### Head-of-line blocking and lossy networks

| Version | Costs you | Pick when |
|---|---|---|
| **HTTP/1.1** (~6 connections per host) | HOL at HTTP and TCP level | Simple internal traffic, legacy clients |
| **HTTP/2** (multiplexed streams, HPACK) | Still TCP-level HOL on packet loss | Default; required by gRPC |
| **HTTP/3 / QUIC** (UDP, per-stream loss, connection migration) | Some networks block UDP | Mobile on lossy networks; enable at the CDN |

- **Depth:** [[03 - Networking and APIs]]

### Late data is useless (voice, video, games)

- **Option:** UDP with app-level handling of loss; TCP stays the default for request/response. **Depth:** [[03 - Networking and APIs]]

---

## Load Balancers, Gateways and DNS

### L4 or L7?

| Option | Costs you | Pick when |
|---|---|---|
| **L7** | Slower; terminates HTTP | Almost every HTTP API (path routing, auth, retries) |
| **L4** | Can't inspect requests | WebSockets at scale, non-HTTP traffic, TLS passthrough |

- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### Uneven load across servers

| Algorithm | Pick when |
|---|---|
| **Round robin** | Default, similar request costs |
| **Least connections** | Mixed request costs |
| **Weighted** | Different server sizes |
| **Consistent hashing / sticky** | WebSockets or in-memory sessions (avoid if possible) |

- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### The load balancer is a single point of failure

- **Fix:** active-passive or active-active LB pairs, or (the default) a managed multi-AZ load balancer. **Depth:** [[04 - Load Balancing CDN and Gateway]]

### Sessions in process memory block horizontal scaling

| Option | Pick when |
|---|---|
| **Externalise sessions** (Redis/DB), stateless app | Default |
| **Sticky sessions** | Unavoidable in-memory state |

- **Depth:** [[02 - Fundamentals]], [[04 - Load Balancing CDN and Gateway]]

### Where auth, rate limits, TLS and WAF live

- **Default:** one **API gateway** box (auth, rate limiting, routing, request ids, TLS termination, WAF) behind a managed LB; internal calls go service-to-service directly. Add a **service mesh** only for dozens of polyglot services needing mTLS, retries and metrics uniformly.
- **Depth:** [[04 - Load Balancing CDN and Gateway]], [[11 - Services and Observability]]

### Routing users to the nearest or healthy region

| Option | Failover speed | Pick when |
|---|---|---|
| **GeoDNS** | Limited by DNS TTL (minutes) | Simple multi-region |
| **Anycast** | Seconds | CDNs, edge, DNS, DDoS absorption |
| **GSLB** (Route 53, Cloudflare LB) with health checks | Fast, health-based | Active-active multi-region |

- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### Clients keep hitting a dead instance because of DNS caching

- **Fixes:** short TTLs on records you may move; fail over at the load balancer where possible; make sure clients re-resolve (JVMs and pools that cache DNS too long keep hitting the old IP). Internal names (VPC resolver, Kubernetes DNS) follow the same rules.
- **Depth:** [[03 - Networking and APIs]]

### Services can't find each other as instances come and go

- **Fix:** service discovery: Kubernetes DNS, or a registry like Consul, with health checks and deregistration. **Depth:** [[11 - Services and Observability]]

---

## Moving Files

### Large uploads or downloads through the API servers

| Option | How it works | Pick when |
|---|---|---|
| **Presigned PUT to S3** | Pending row → client uploads directly → S3 event marks it ready (optionally virus scan) | Always for files; the DB stores only key and metadata |
| **Multipart upload** | Parts retried and uploaded in parallel | Big files, flaky networks |
| **Presigned GET** | Short-lived signed link straight to S3 | Private files |
| **Public object behind a CDN** (bucket private, CDN-only access) | Edge serves cached copies | Public content |

- **Cleanup:** expire pending rows and use S3 lifecycle rules for orphaned uploads.
- **Depth:** [[09 - Storage Search and Geo]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[03 - Networking and APIs]] · [[04 - Load Balancing CDN and Gateway]] · [[09 - Storage Search and Geo]]
