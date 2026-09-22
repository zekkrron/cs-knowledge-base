---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# Services and Observability

> [!abstract] Start with a **modular monolith** unless the prompt forces independent scale or teams. Microservices without a story are a red flag. If you split, you owe: how they find each other, how they don't share a DB, and how you see a request across them.

## Monolith vs microservices vs modular monolith

| | When |
|---|---|
| Monolith | SDE-1 default. One deploy, one DB, transactions are easy. |
| Modular monolith | Modules with boundaries, still one process. Grown-up default. |
| Microservices | Different scale, different lifecycle, or a hard isolation (payments vs feed). Each service **owns its data**. |

> [!warning] "We'll use microservices" with 4 users is an auto-miss. Split **the one component** that needs it (video transcode, notification fanout), keep the rest together.

**Shared DB across "services"** is a monolith in a trench coat. Don't.

## How services talk

- Sync: REST/gRPC. Timeout + circuit breaker ([[08 - Reliability]]).
- Async: events ([[07 - Async Messaging]]). Prefer for "someone else should know."

**Service discovery:** k8s DNS, Consul. "The gateway routes `/orders` to the order service; inside the mesh we use DNS." Enough.

**API gateway** is the public door ([[04 - Load Balancing CDN and Gateway]]). Internal calls don't all bounce through it.

## CQRS / event sourcing (name, don't design)

- **CQRS** — write model ≠ read model. Write to order tables, read from a denormalised feed cache. You already do this when you fanout a timeline.
- **Event sourcing** — store events, rebuild state. Audit-heavy domains. Heavy. Don't lead with it.

## Observability — three pillars

| Pillar | What | Use |
|---|---|---|
| **Logs** | what happened, request id | debug a single request |
| **Metrics** | QPS, latency p99, error %, queue lag, CPU | alerts, SLOs |
| **Traces** | one request across services (span) | "why is checkout 2s" |

**Correlation / request ID** — generated at the gateway, passed in headers, on every log line. Without this, microservices are unreadable.

**SLO** — "99.9% of checkouts < 300 ms." **SLI** is the measurement. **SLA** is the contract. Alert on SLO burn, not "CPU > 80" alone.

**Golden signals:** latency, traffic, errors, saturation.

You will not install Prometheus in the interview. Draw a box: "metrics + logs + traces, request id from gateway."

## Security (the SDE-1 slice)

- TLS on the internet.
- AuthN/Z at gateway ([[03 - Networking and APIs]]).
- Secrets in a manager, not in Git.
- Least privilege on S3/DB.
- Rate limit as abuse control.
- Don't store card PANs; that's why payment gateways exist.

## Multi-region (tail)

Active-passive DR vs active-active. Data gravity and CAP become real. For SDE-1: "primary region + async replica region for disaster, RPO = replica lag." Don't design Google Spanner.

## Related Notes

- [[01 - How the Round Works]]
- [[03 - Networking and APIs]]
- [[07 - Async Messaging]]
- [[08 - Reliability]]
