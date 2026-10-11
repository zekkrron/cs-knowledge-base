---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Overload and Failure Handling

> [!abstract] What this is
> Problems where **a dependency is slow or down, or there's more traffic than capacity**: timeouts, retries and retry storms, circuit breakers and bulkheads, tail latency, rate limiting, throttling and load shedding, health checks, and degrading gracefully.

---

## Slow or Failing Dependencies

### A slow dependency hangs every thread

- **Signal:** a call goes from 30 ms to 30 s; the pool is exhausted; you become the slow dependency for your callers. Little's law: 10× latency means 10× in-flight requests.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Timeout on every outbound call** | Max wait per DB/Redis/HTTP/Kafka call, shorter than your caller's | Too short gives false failures | Always |
| **Deadline propagation** | Pass "800 ms left" down the chain | Every hop must honour it | Multi-hop call chains |
| **Bounded pools** sized with Little's law | `L = λ × W` | Undersized pools queue | Thread, connection, worker pools |
| **Circuit breaker** | Open after repeated failures; fail fast; half-open probe after cooldown | Callers need a fallback | Any remote dependency |
| **Bulkhead** | Separate pool per dependency | Less sharing; more pools to size | Dependencies of different importance in one process |

- **Default:** "timeouts everywhere, circuit breakers on remote calls, bulkheads around the non-critical ones."
- **Depth:** [[08 - Reliability]], [[02 - Fundamentals]]

### Transient failures reach users

| Rule | Detail |
|---|---|
| **Retry only retryable errors** | Timeouts, 503, connection resets; 429 after `Retry-After`; never 400/401/404 |
| **Retry `POST` only if idempotent or keyed** | Otherwise you double-charge |
| **Cap retries (2–3)** | Then fail or degrade |
| **Exponential backoff + jitter** | Otherwise clients retry in lockstep waves |

- **Depth:** [[08 - Reliability]], [[03 - Networking and APIs]]

### Retries amplify an outage (retry storm)

- **Signal:** 3 retries at each of 3 layers = up to 64 attempts for one user action.

| Option | Costs you | Pick when |
|---|---|---|
| **Retry at one layer only**; inner layers fail fast | One decision point | Simple default |
| **Retry budget** (~10% extra traffic) | Mesh/client support | Many services, high traffic |
| **Honour `Retry-After` with jitter** | Slower recovery for some | Always |

- **Depth:** [[08 - Reliability]]

### Fan-out request is slow because one backend is slow (tail at scale)

- **Signal:** an aggregator calls 100 backends; most requests hit someone's p99.

| Option | Costs you | Pick when |
|---|---|---|
| **Hedged requests** (send a second copy after ~p95, take the first answer) | ~5% extra load | Idempotent reads only, with a retry budget |
| **Per-call timeouts + partial results** | Page missing a section | The page is still useful without it |
| **Less fan-out** (pre-aggregate, partition so fewer shards are touched) | Data reshaping | Data can be pre-computed |
| **Fewer serial hops** (merge, cache, parallelise) | Refactoring | Always look for them |
| **Headroom** on each backend | Cost | Keep off the steep part of the latency curve |

- **Depth:** [[02 - Fundamentals]], [[08 - Reliability]]

### Hitting a p99 target across several hops

- **Budget it:** e.g. 200 ms ≈ LB 5 + app 20 + DB 5–30 + cache 1–2 + slack. Max 2–3 serial dependencies on the hot path; fan out in parallel (you pay the slowest, not the sum); no N+1 per list item.
- **Depth:** [[16 - Estimation and Decision Cheat Sheet]]

### A non-critical feature takes the site down

- **Fix:** **graceful degradation** with predefined fallbacks: search down → browse; recommendations down → popular items; Redis down → DB behind a circuit breaker.
- **Depth:** [[08 - Reliability]]

---

## Too Much Traffic

### One caller floods the API / noisy neighbour

| Option | Costs you | Pick when |
|---|---|---|
| **Rate limit at the gateway** per user / API key / IP / endpoint, 429 + `Retry-After` | Callers must handle 429 | Always, at the edge |
| **Per-tenant static quotas** | Don't track capacity changes | Fairness between customers |
| **Fair queueing** (queue per tenant, served in turn) | More machinery | Even sharing inside the global limit |
| **Adaptive limits partitioned per tenant/priority** | Complexity | Fairness *and* self-protection |
| **Keep quotas small relative to capacity** | Big tenants limited | Otherwise an 80%-of-cluster quota crowds everyone out legally |

- **Depth:** [[08 - Reliability]]

### Choosing a rate-limit algorithm

| Algorithm | Watch out | Pick when |
|---|---|---|
| **Token bucket** (refill R, burst B) | Two parameters | The default answer |
| **Fixed window** | Up to 2× burst at the window edge | Simple quotas |
| **Sliding window log** | Memory grows with traffic | Low volume, exact limits |
| **Sliding window counter** | Approximate | Good compromise at scale |
| **Leaky bucket** | Delays requests | A downstream needs a steady rate |

- **Depth:** [[08 - Reliability]], [[21 - Redis]]

### Rate limit counted in each gateway's local memory

- **Signal:** the real limit becomes limit × number of gateway nodes, and changes with autoscaling.
- **Fix:** one shared counter in Redis, checked and incremented atomically (`INCR` + TTL in a Lua script; token bucket state in the script). Approximate local limits (`limit / nodes`) only where precision doesn't matter.
- **Depth:** [[08 - Reliability]]

### Service overloaded regardless of who's calling (latency death spiral)

| Option | Costs you | Pick when |
|---|---|---|
| **Load shedding**: refuse early with 503 | Some users rejected | Already overloaded |
| **Shed by priority** (analytics before checkout) | Traffic must be classified | Mixed-importance traffic |
| **Concurrency cap** on in-flight requests | Must be sized | Protecting the service; a rate limit alone lets in-flight work grow when latency rises |
| **Adaptive concurrency limit** (AIMD, like TCP) | Blind to caller identity | Capacity changes (deploys, slow dependencies) |
| **Queue backpressure** | Producers slow down | Async pipelines |

- **Default:** "a concurrency limit and priority shedding protect the service; per-tenant rate limits at the gateway protect fairness."
- **Depth:** [[08 - Reliability]]

### What to do with the excess

| Strategy | Costs you | Pick when |
|---|---|---|
| **Reject** (429 / 503) | Caller handles it | Default |
| **Delay / pace** (leaky bucket) | Latency and memory | Smoothing short bursts |
| **Degrade** (cached, fewer results, features off) | Worse experience | Read paths |
| **Shed by priority** | Classification | Overload |

- **If you delay:** the queue must be **bounded** (max wait < client timeout), requests carry **deadlines** so expired ones are dropped, and under extreme overload a LIFO queue serves the clients still waiting.
- **Depth:** [[08 - Reliability]]

### Rejecting requests itself saturates the server

| Option | Pick when |
|---|---|
| **Client-side throttling** (own limiter, backoff, retry budget, circuit breaker) | Always alongside server-side |
| **Adaptive client-side throttling** (reject locally when accepts ≪ requests) | Heavy overload |
| **Throttle at the edge/gateway**, where rejection is cheapest | Always |
| **Throttle first in the handler**, before auth/DB work | Always |

- **Depth:** [[08 - Reliability]]

### How "slow down" is signalled

| Layer | Signal |
|---|---|
| HTTP | 429 or 503 + `Retry-After` |
| gRPC | `RESOURCE_EXHAUSTED` / `UNAVAILABLE` |
| Transport | TCP / HTTP/2 flow-control windows |
| Kafka | Broker quota delays the response (`throttle_time_ms`) |
| Pull consumers | The consumer asks for more only when ready |

- **Depth:** [[08 - Reliability]]

### A spike arrives faster than autoscaling

- **Fixes:** headroom above normal peak; pre-scale before known events; load shedding while capacity arrives; scale on request rate or queue depth rather than CPU. See [[12 - Services Deployment and Operations]].
- **Depth:** [[19 - Deployment and Infrastructure]]

---

## Health Checks

### The load balancer keeps sending traffic to broken servers

| Option | Pick when |
|---|---|
| **Active health checks** (`/healthz`) | Default; catches servers with no traffic |
| **Passive checks** (eject after repeated real errors) | Addition: servers that pass `/healthz` but fail real requests |

- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### A database blip restarts every pod

- **Fix:** **liveness** checks only the process; **readiness** checks dependencies and warm-up (failing readiness removes the pod from rotation without killing it).
- **Depth:** [[04 - Load Balancing CDN and Gateway]], [[19 - Deployment and Infrastructure]]

### Health checks get throttled and healthy nodes are marked dead

- **Fix:** exempt health checks from rate limits and shedding. **Depth:** [[08 - Reliability]]

### Do the failure mechanisms actually work?

| Option | Pick when |
|---|---|
| **Chaos engineering** (steady-state hypothesis, inject failure, limit blast radius, abort switch) | Validating resilience continuously |
| **Game days** (end-to-end scenario including people and runbooks) | Regional failover, major incidents |

- **Depth:** [[08 - Reliability]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[08 - Reliability]] · [[02 - Fundamentals]] · [[04 - Load Balancing CDN and Gateway]]
