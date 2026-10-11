---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Services, Deployment and Operations

> [!abstract] What this is
> Problems about **running the system over time**: how to split services and how they talk, seeing what's happening (logs, metrics, traces, alerts, SLOs), where code runs and how it scales, shipping changes safely, and changing schemas, APIs and whole systems without downtime.

---

## Service Boundaries

### Monolith or microservices?

| Option | Costs you | Pick when |
|---|---|---|
| **Monolith** | No independent scaling | Small system or team |
| **Modular monolith** | Still one deploy | The grown-up default; keeps the option to split |
| **Microservices** (each owns its data) | Network failures, harder debugging, eventual data sharing | Different scale, lifecycle or hard isolation (payments vs feed) |
| **Split out only the odd component** (transcoding, notification fan-out) | One extra service | One part scales differently |

- **Watch out:** a **distributed monolith**: services sharing one database or deploying in lockstep. Each service owns its data.
- **Depth:** [[11 - Services and Observability]]

### Sync or async between services?

| Option | Costs you | Pick when |
|---|---|---|
| **Sync** (REST/gRPC) with timeout + circuit breaker | Caller coupled to callee latency and failures | The caller needs the result to respond |
| **Async events** | Eventual; more moving parts | "Someone else should know" |

- **Depth:** [[11 - Services and Observability]]

### Reads need a different shape or scale than writes

| Option | Costs you | Pick when |
|---|---|---|
| **CQRS**: separate denormalised read model (timelines, caches) | Eventual sync between models | Reads differ in shape or scale |
| **Event sourcing**: events are the source of truth, state is a replay | Replay and schema evolution are heavy | Full history is required (ledger, audit); don't lead with it |

- **Depth:** [[11 - Services and Observability]]

### Feed or notification fan-out, including celebrities

| Option | Pick when |
|---|---|
| **Fan-out on write** (push into each follower's timeline) | Reads ≫ writes, small follower counts |
| **Fan-out on read** (merge at read time) | Celebrities |
| **Hybrid with a named threshold** (e.g. > 10k followers → read-time) | Real systems |

- **Depth:** [[16 - Estimation and Decision Cheat Sheet]], [[11 - Services and Observability]]

### Every service needs the same mTLS, retries and metrics

| Option | Costs you | Pick when |
|---|---|---|
| **Service mesh** (Envoy sidecars + Istio/Linkerd) | Two extra hops; complex control plane | Dozens of polyglot services, strict security |
| **Shared libraries + platform DNS/LB** | Per-language config | A handful of services |

- **Depth:** [[11 - Services and Observability]], [[04 - Load Balancing CDN and Gateway]]

---

## Observability

### Debugging one request across many services

- **Fix:** a request/correlation id minted at the gateway and logged on every line, plus distributed tracing (W3C `traceparent` propagated through HTTP *and* message headers, OpenTelemetry auto-instrumentation).
- **Depth:** [[11 - Services and Observability]]

### Which signal answers which question?

| Pillar | Answers |
|---|---|
| **Logs** (with request id) | What happened to this one request |
| **Metrics** (QPS, p99, error %, saturation, queue lag) | Alerts, SLOs, dashboards |
| **Traces** | Where the time went across services |

- **Golden signals:** latency, traffic, errors, saturation (including pool and thread exhaustion).
- **Depth:** [[11 - Services and Observability]]

### Tracing everything costs too much

| Option | Costs you | Pick when |
|---|---|---|
| **Head sampling** (~1%, decided at the first service) | Blind to rare failures | High volume, overall latency picture |
| **Tail sampling** (decide after the trace ends) | Collector buffers every span | Outliers and errors matter |
| **Always keep errors** | Storage | Alongside head sampling |

- **Depth:** [[11 - Services and Observability]]

### Defining reliability targets and release risk

- **SLI** = what you measure, **SLO** = the internal target, **SLA** = the looser customer contract. The **error budget** (100% − SLO) decides when to slow risky releases.
- **Depth:** [[11 - Services and Observability]]

### Alerts are noisy or miss real outages

| Fix | Why |
|---|---|
| **Alert on symptoms** (error rate, latency SLO), not causes (CPU > 80%) | Users feel symptoms |
| **Multi-window burn-rate alerts** | Page only when it's significant *and* still happening |
| **Page vs ticket vs dashboard** | Fast burn pages; slow burn tickets; CPU, GC, queue depth on dashboards |
| **Runbook per page; delete unactionable alerts** | Alert fatigue |

- **Depth:** [[11 - Services and Observability]]

---

## Compute and Scaling

### Where should code run?

| Option | Costs you | Pick when |
|---|---|---|
| **Containers** (Kubernetes/ECS) | Medium ops | Default for long-running services at scale |
| **Serverless** | Cold starts, time limits, connection storms (put a pooler/proxy in front of the DB) | Spiky or low traffic, event glue, cron |
| **Managed PaaS** | Less control | Small teams, standard web apps |
| **VMs** | Slow boots, high ops | Special hardware, licensed software |

- **Depth:** [[19 - Deployment and Infrastructure]]

### Pods OOM-killed, throttled or overpacked

- **Fix:** set **requests** (what the scheduler reserves) and **limits** (CPU over limit is throttled, memory over limit is killed) deliberately; requests too low overpack nodes, memory limits too tight cause restart loops.
- **Depth:** [[19 - Deployment and Infrastructure]]

### Which signal to autoscale on?

| Signal | Pick when |
|---|---|
| **CPU** | Truly CPU-bound services |
| **Request rate / concurrency** | Latency-sensitive APIs, after load-testing one pod |
| **Queue depth / consumer lag** | Workers on Kafka, SQS or job queues |
| **Custom business metric** | Nothing standard fits |

- **Also:** a pod autoscaler needs a cluster autoscaler behind it (node boot delay leaves pods pending); use cooldowns (scale up fast, down slowly) to stop flapping; scale to zero only for internal tools and batch.
- **Depth:** [[19 - Deployment and Infrastructure]]

### How much capacity, and when do we run out?

- **Plan for peak** (2–3× average, 10× if spiky) with headroom; load-test one pod for a per-unit number, then the whole path (the bottleneck is often the database or a downstream that doesn't autoscale).
- **Depth:** [[19 - Deployment and Infrastructure]], [[16 - Estimation and Decision Cheat Sheet]]

---

## Shipping Changes

### Deploying without downtime and with fast rollback

| Strategy | Costs you | Pick when |
|---|---|---|
| **Rolling** | A bad version spreads gradually | Default for stateless services |
| **Canary** with automated rollback on SLO metrics (1% → 10% → 50% → 100%) | Small extra cost | High-traffic user-facing services |
| **Blue-green** | Double capacity | Instant rollback by flipping back |
| **Shadow / mirroring** | Full duplicate load; mirror writes only to a sandbox | Testing a rewrite or new engine |
| **Recreate** | Downtime | Dev, or versions can't coexist |

- **Every strategy needs:** readiness failing first, then connection draining before `SIGTERM` (see [[08 - Overload and Failure Handling]]).
- **Depth:** [[19 - Deployment and Infrastructure]]

### Releasing risky behaviour separately from the deploy

- **Fix:** **feature flags**: kill switch (faster than any rollback), gradual rollout hashed on user id, targeting (staff, region, customer). Give every flag an owner and a removal date.
- **Depth:** [[19 - Deployment and Infrastructure]]

### A config push breaks every region at once

- **Fix:** config as code (reviewed, revertable), validated runtime reloads, and staged config rollout (one cell or region first). **Depth:** [[19 - Deployment and Infrastructure]]

---

## Zero-Downtime Change

### Renaming or changing a column with old code still running

- **Fix:** **expand–migrate–contract**: add the new column → dual-write → backfill → switch reads → stop writing the old one → drop it, each shipped separately (about six deploys). Also works for table splits and moving data to a new store.
- **Depth:** [[12 - Schema Changes and Backups]], [[19 - Deployment and Infrastructure]]

### A DDL statement stalls the whole table

- **Signal:** an `ALTER` queues behind a long query, and every new query queues behind the `ALTER`.
- **Fix:** `SET lock_timeout = '2s'` and retry; avoid long transactions before migrations; run heavy changes off-peak.
- **Depth:** [[12 - Schema Changes and Backups]]

### Expensive schema operations on big tables

| Operation | Safe way |
|---|---|
| **Add an index** (Postgres) | `CREATE INDEX CONCURRENTLY` (not in a transaction; drop and retry an `INVALID` index on failure) |
| **Add an index** (MySQL) | Online `INPLACE` DDL |
| **Change a column type** | Expand–migrate–contract with a new column (widening `varchar` is metadata-only) |
| **Add `NOT NULL`** | `CHECK … NOT VALID` → `VALIDATE CONSTRAINT` → `SET NOT NULL` |
| **Add a column with a default** | Constant default is metadata-only (PG 11+); volatile defaults rewrite |
| **Busy MySQL table the engine can't change online** | `gh-ost` (binlog-driven, throttled) or `pt-online-schema-change` (triggers) |
| **Bloated Postgres table** | `pg_repack` |

- **Depth:** [[12 - Schema Changes and Backups]]

### Backfilling millions of rows without hurting the primary

- **Fix:** small primary-key-range batches, throttled (pause on replica lag or CPU), resumable (store the last key), idempotent (`WHERE col IS NULL`). Turn on dual-writes **before** the backfill starts, or rows are missed.
- **Depth:** [[12 - Schema Changes and Backups]]

### Changing an API or event schema while old and new versions are live

- **Fix:** additive-only changes with tolerant readers; version the API and run both versions until old clients drain; enforce event-schema compatibility in a schema registry. Details in [[09 - APIs Networking and Realtime]] and [[06 - Async Messaging and Workflows]].
- **Depth:** [[19 - Deployment and Infrastructure]]

### Migrating from an old system to a new one

- **Fix:** dual write → backfill → shadow reads (compare results) → cut over behind a switch you can roll back. A rewrite without this plan is a red flag.
- **Depth:** [[01 - How the Round Works]], [[12 - Schema Changes and Backups]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[11 - Services and Observability]] · [[19 - Deployment and Infrastructure]] · [[12 - Schema Changes and Backups]]
