---
tags: [languages/cross-language, status/draft]
created: 2026-10-14
---
# Web Server Models Compared

> [!abstract] What this is
> How HTTP requests are executed in the main server stacks: **Tomcat thread-per-request** (Spring MVC, with or without virtual threads), **Netty event loops** (WebFlux), **Go `net/http`** (goroutine per connection; Gin on top), **WSGI pre-fork** (Gunicorn sync/gthread workers for Django/Flask), and **ASGI event loops** (Uvicorn for FastAPI). Compares concurrency limits, what a slow downstream does to each, CPU parallelism, timeouts and graceful shutdown defaults, and sizing rules. The decision this helps with: configuring and capacity-planning a service on any of these stacks, and explaining their failure behaviour under load.

---

## Why It Matters

- The same incident ("a downstream got slow and we went down") plays out differently per server model, and so does the fix: more threads, virtual threads, timeouts, more workers, or removing a blocking call.
- Defaults differ in dangerous ways: Go's `http.Server` and FastAPI have no request timeouts; Tomcat caps at 200 threads; Gunicorn kills workers that miss heartbeats.

---

## The Models

| Stack | Execution model | Concurrency per instance | CPU parallelism |
|---|---|---|---|
| **Spring MVC on Tomcat** (platform threads) | NIO acceptor/poller + worker pool, one thread per in-flight request ([[04 - Spring MVC Request Lifecycle]]) | `threads.max` (200) | All cores |
| **Spring MVC + virtual threads** (Boot 3.2+, Java 21+) | A new virtual thread per request | Very high; limited by downstream pools | All cores |
| **Spring WebFlux on Netty** | ~cores event loops, non-blocking pipelines ([[05 - Spring WebFlux and Reactive]]) | Very high | All cores (one loop per core) |
| **Go `net/http` / Gin** | Goroutine per connection, netpoller ([[01 - Go net-http Server Model]]) | Very high | `GOMAXPROCS` |
| **WSGI: Gunicorn sync / gthread** (Django, Flask) | Pre-forked processes, one request per thread ([[06 - WSGI vs ASGI and Python Servers]]) | workers × threads (tens–hundreds) | One core per **process** (GIL) |
| **ASGI: Uvicorn** (FastAPI) | Pre-forked processes, each one event loop + a 40-thread pool for `def` endpoints ([[02 - FastAPI Request Lifecycle and Sync vs Async]]) | High per worker for async I/O | One core per worker process |

---

## A Slow Downstream (50 ms → 5 s)

| Stack | What happens |
|---|---|
| Tomcat, platform threads | All 200 workers block; throughput drops ~100×; new requests queue in the accept backlog, then time out |
| Tomcat + virtual threads | Requests pile up as cheap virtual threads; the DB/HTTP **connection pool** becomes the queue; memory grows with in-flight requests |
| WebFlux | Loops stay free if the client is non-blocking; in-flight requests pile up in memory; a blocking call anywhere freezes a loop |
| Go | Goroutines pile up cheaply; connection pools and memory become the limit; without timeouts, goroutines accumulate indefinitely |
| Gunicorn sync/gthread | All threads busy; requests queue at the socket; `--timeout` may kill workers mid-request |
| Uvicorn async | Coroutines pile up; fine **if** calls are async with timeouts; a sync client in `async def` freezes the worker |

- Universal fixes: **timeouts on every outbound call**, bounded concurrency at the scarce resource (bulkheads, semaphores, pool sizes), circuit breakers, and load shedding (503 before collapse) ([[11 - Spring HTTP Clients Resilience and Messaging]]).

---

## Defaults That Bite

| Concern | Spring Boot/Tomcat | Go `net/http` | Gunicorn | Uvicorn / FastAPI |
|---|---|---|---|---|
| Request timeout | None per request (connection timeout only) | **None** (all `Server` timeouts zero) | Worker `--timeout` 30 s (kills the worker) | **None** |
| Max concurrency | 200 threads (platform) | Unlimited | workers × threads | Unlimited per worker (`--limit-concurrency` optional) |
| Body size limit | Configurable (`max-swallow-size`, multipart limits) | None (use `MaxBytesReader`) | Proxy usually | None (proxy/`Request` handling) |
| Graceful shutdown | On by default since Boot 3.4 | Manual: `Server.Shutdown` | SIGTERM graceful with `--graceful-timeout` | `--timeout-graceful-shutdown` |
| Keep-alive vs LB | Set above LB idle timeout | `IdleTimeout` | `--keep-alive` (default 2 s: often too low behind LBs) | `--timeout-keep-alive` (default 5 s) |

---

## Sizing Rules of Thumb

| Stack | Starting point |
|---|---|
| Tomcat platform threads | Threads ≈ target RPS × p99 latency (Little's law), with DB pool sized separately (10–30) |
| Tomcat + virtual threads | Don't size threads; size the **DB pool** and add concurrency limits for downstreams |
| WebFlux | Event loops = cores (default); bounded `flatMap` concurrency |
| Go | `GOMAXPROCS` = CPU limit (automatic in 1.25+); bound fan-out with `errgroup.SetLimit`; set `db.SetMaxOpenConns` |
| Gunicorn | sync: 2 × cores + 1 workers; gthread: cores–2×cores workers × 4–16 threads |
| Uvicorn | 1 worker per core, or 1–2 per pod and scale pods |

- Connection budget is the same calculation everywhere: **instances × processes × pool size ≤ database `max_connections`** with headroom ([[08 - Spring Database Connections and Migrations]], [[05 - FastAPI Data Access with SQLAlchemy]], [[05 - Go Data Access]]).

---

## Streaming and Long-Lived Connections

| Stack | SSE/WebSockets |
|---|---|
| Spring MVC | `SseEmitter` / WebSockets; each open stream holds a thread unless async or virtual threads |
| WebFlux | Natural fit: `Flux` streams with backpressure |
| Go | Goroutine per connection; `http.ResponseController` for flush/deadlines |
| FastAPI | `EventSourceResponse` (0.135+), WebSockets; cheap on the event loop if everything is async ([[06 - FastAPI Streaming SSE and WebSockets]]) |
| Gunicorn sync | Poor fit: each stream occupies a worker |

---

## Graceful Shutdown (Kubernetes)

- Same sequence everywhere: readiness → not ready, short preStop delay so load balancers stop routing, server stops accepting and drains in-flight requests within a timeout, then close pools and flush telemetry, all inside `terminationGracePeriodSeconds`.
- Implementation: Spring `server.shutdown=graceful`; Go `signal.NotifyContext` + `Server.Shutdown`; Uvicorn/Gunicorn graceful timeouts + ASGI lifespan shutdown ([[13 - Java in Containers and Production Tuning]], [[13 - Go in Containers and Production]], [[09 - FastAPI Production Deployment]]).

---

## Failure Modes

| Failure | Where it bites hardest |
|---|---|
| Blocking call on an event loop | WebFlux, asyncio/FastAPI |
| Thread pool exhaustion | Tomcat platform threads, Gunicorn, FastAPI `def` endpoints (40-thread limiter) |
| Unbounded in-flight requests | Go, virtual threads, async stacks: memory and downstream overload |
| Missing server timeouts | Go `net/http`, FastAPI |
| Worker killed mid-request | Gunicorn `--timeout` |
| CPU work per request | Python stacks (GIL per process) |

---

## Interview Lines

> [!tip] Say it like this
> - "Tomcat is thread-per-request with a 200-thread pool; with virtual threads the cap moves to the connection pool; WebFlux and asyncio run event loops where one blocking call stalls everything on that loop."
> - "Go runs a goroutine per connection on the netpoller, so blocking code scales, but the default server has no timeouts and nothing bounds in-flight work."
> - "Python servers get multi-core parallelism from processes: Gunicorn workers for WSGI, Uvicorn workers for ASGI, each with its own GIL and pools."
> - "When a downstream slows, every model needs the same fixes: client timeouts, bounded concurrency at the scarce resource, circuit breakers and load shedding."

## Related Notes

- [[04 - Spring MVC Request Lifecycle]] · [[05 - Spring WebFlux and Reactive]] · [[01 - Go net-http Server Model]] · [[06 - WSGI vs ASGI and Python Servers]] · [[02 - FastAPI Request Lifecycle and Sync vs Async]]
- [[01 - Concurrency Models Compared]] · [[Languages/README]]
