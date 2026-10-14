---
tags: [languages/cross-language, status/draft]
created: 2026-10-14
---
# Concurrency Models Compared

> [!abstract] What this is
> Side-by-side comparison of how Java, Go and Python run concurrent work: **Java platform threads** (1:1 OS threads) and **virtual threads** (M:N, Java 21+), **Go goroutines** (M:N with the GMP scheduler and netpoller), **Python threads under the GIL** (standard build), **asyncio** (single-threaded event loop), **processes**, and **reactive event loops** (Netty/WebFlux). Covers what each costs, how blocking I/O and CPU work behave, how cancellation and structured concurrency work, and the "handle 10k concurrent connections" question for each. Free-threaded Python is noted as an extra. The decision this helps with: predicting how a workload will behave on each runtime and explaining it in an interview.

---

## Why It Matters

- "How would you handle 10,000 concurrent requests in Java / Go / Python?" is a standard senior question. The right answer depends on the runtime's concurrency model, not on the framework.
- The same mistake has a different blast radius per model: a blocking call costs one thread in Java, one goroutine in Go, and **the whole event loop** in asyncio or WebFlux.

---

## The Models at a Glance

| | Java platform threads | Java virtual threads (21+) | Go goroutines | Python threads (GIL build) | Python asyncio | Reactive (Netty/WebFlux) |
|---|---|---|---|---|---|---|
| Unit | OS thread | JVM-scheduled thread | Runtime-scheduled goroutine | OS thread | Coroutine / task | Callback / operator chain |
| Mapping | 1:1 | M:N on carrier threads | M:N on Ps/Ms | 1:1 | N tasks : 1 thread | N : few event loops |
| Cost each | ~1 MB reserved stack, µs to create | ~hundreds of bytes–KB, heap stack | ~2 KB growable stack | ~OS thread | ~KB | ~bytes |
| Practical count | Thousands | Millions | Millions | Hundreds–thousands | Tens of thousands+ | Tens of thousands+ |
| Blocking I/O | Blocks the thread | Unmounts (cheap) | Parks the goroutine (netpoller) | Releases the GIL, blocks the thread | **Blocks the whole loop** | **Blocks the whole loop** |
| CPU parallelism in one process | Yes, all cores | Yes | Yes (`GOMAXPROCS`) | **No** (one thread runs Python at a time) | No | Yes (one loop per core) |
| Preemption | OS | Cooperative at blocking points (carriers preempted by OS) | Cooperative + async (1.14) | GIL switch interval (5 ms) | Only at `await` | Never inside a callback |
| Code style | Blocking | Blocking | Blocking | Blocking | `async`/`await` everywhere | Operators/callbacks |

---

## Java

- **Platform threads**: a thread per request with a pool (Tomcat's 200). Concurrency = pool size; a slow downstream drains it ([[04 - Spring MVC Request Lifecycle]]).
- **Virtual threads** (21; `synchronized` pinning fixed in 24): one virtual thread per task; blocking JDK I/O unmounts it from its carrier, so blocking code scales to very high concurrency. CPU work still runs on ~cores carriers and isn't time-sliced ([[05 - Java Platform and Virtual Threads]]).
- **Reactive** (WebFlux/Netty): a few event loops; every step must be non-blocking; gives backpressure ([[05 - Spring WebFlux and Reactive]]).
- Cancellation: `Thread.interrupt`, `Future.cancel`, structured concurrency (still preview in JDK 27).

## Go

- Goroutines on the **GMP scheduler**: Ps (= `GOMAXPROCS`, container-aware since 1.25) run Gs on Ms; network waits park goroutines on the netpoller; blocking syscalls/cgo hand off the P ([[01 - Go Runtime and the GMP Scheduler]]).
- One goroutine per connection with plain blocking code is the default server model ([[01 - Go net-http Server Model]]).
- Cancellation and deadlines through `context.Context`, cooperative; structured fan-out with `errgroup` ([[08 - Go Concurrency Patterns and Context]]).
- Shared memory needs mutexes/atomics; channels for hand-off; the race detector catches unsynchronised access ([[07 - Go Concurrency Primitives]]).

## Python (standard GIL build)

- **Threads**: real OS threads, but only one runs Python bytecode at a time. Good for **I/O-bound** work (the GIL is released while waiting) and C extensions that release it; useless for CPU-bound pure Python ([[03 - The GIL and Free-Threaded Python]]).
- **asyncio**: one event loop per thread, tasks switch only at `await`; thousands of concurrent I/O operations cheaply, but **one blocking or CPU-heavy call stalls every task** on the loop ([[05 - asyncio Internals]]).
- **Processes**: the way to use multiple cores (pre-fork servers, `ProcessPoolExecutor`), at the cost of memory per process and pickling ([[04 - Python Threads Processes and Executors]], [[06 - WSGI vs ASGI and Python Servers]]).
- Cancellation: `task.cancel()` raises `CancelledError` at an `await`; `TaskGroup` and `asyncio.timeout` (3.11) give structured concurrency.

> [!info] Free-threaded Python (extra)
> On `python3.14t`, threads run Python bytecode in parallel, making Python's thread model behave more like Java platform threads (1:1, real parallelism) at a small single-thread cost, if every dependency supports it. asyncio's rules don't change. Default deployments still use the GIL build.

---

## The 10k Concurrent Connections Question

| Runtime | Approach | What limits you next |
|---|---|---|
| **Java, platform threads** | Raise pool sizes only so far; better: virtual threads or reactive | Thread memory and context switching at ~thousands |
| **Java, virtual threads** | `spring.threads.virtual.enabled=true`; blocking code unchanged | DB pool, downstream limits, heap (stack chunks); pinning edge cases |
| **Java, WebFlux** | Netty event loops, non-blocking drivers (R2DBC, WebClient) | Any blocking call on the loop; code complexity |
| **Go** | Default `net/http`: one goroutine per connection | DB pool size, file descriptors (`ulimit`), memory per goroutine's buffers |
| **Python, asyncio (FastAPI/Uvicorn)** | One loop per worker process, async drivers everywhere; workers ≈ cores | One blocking call stalls a worker; CPU work per request; DB connections per worker |
| **Python, threads (Django/Gunicorn `gthread`)** | Workers × threads (hundreds total) | Thread count, GIL contention for CPU work; usually needs async or more processes for 10k |

- In every model, the **real bottleneck** at high concurrency is usually the scarce downstream resource (database connections, rate-limited APIs), so bound concurrency there explicitly: semaphores, pool sizes, bulkheads.

---

## CPU-Bound Work

| Runtime | Parallel CPU in one process? | How to do it |
|---|---|---|
| Java | Yes | Fixed-size platform thread pool (~cores); `ForkJoinPool`; not on virtual threads in bulk |
| Go | Yes | Goroutines with bounded concurrency (~`GOMAXPROCS`); avoid starving latency-sensitive goroutines |
| Python (GIL build) | **No** | Process pools, native code that releases the GIL (NumPy, Rust/PyO3), task queues ([[08 - Python AI Performance Traps]]) |

---

## Cancellation and Structured Concurrency

| | Java | Go | Python |
|---|---|---|---|
| Mechanism | Interrupts, `Future.cancel`, `StructuredTaskScope` (preview) | `context.Context` cancellation (cooperative) | `task.cancel()` → `CancelledError` at `await` |
| Structured groups | `StructuredTaskScope` (preview), executors with try-with-resources | `errgroup.WithContext` | `asyncio.TaskGroup` (3.11) |
| Request context propagation | `ThreadLocal`, **scoped values** (final in 25) | Explicit `ctx` parameter | `contextvars` (copied into tasks) |
| Leak detection | Thread dumps (JSON for virtual threads) | Goroutine and `goroutineleak` profiles (1.27), `goleak` | asyncio task introspection (3.14 `python -m asyncio ps`) |

---

## Failure Modes Across Models

| Failure | Java | Go | Python |
|---|---|---|---|
| Blocking call in the wrong place | Pins/holds a thread; on WebFlux stalls a loop | Holds an OS thread only for syscalls/cgo | **Stalls the asyncio loop** |
| Unbounded concurrency | Thread explosion (platform) or downstream overload (virtual) | Goroutine explosion, downstream overload | Task explosion, 429s, pool exhaustion |
| Shared-state races | JMM visibility bugs | Data races (`-race`) | Compound operations race even under the GIL |
| Lost cancellation | Interrupt swallowed | `ctx` not propagated | `CancelledError` swallowed |

---

## Interview Lines

> [!tip] Say it like this
> - "Java platform threads are 1:1 and expensive; virtual threads and goroutines are M:N and park on blocking I/O, so blocking code scales to very high concurrency in both."
> - "Python's standard build has the GIL: threads help for I/O, asyncio handles lots of concurrent I/O on one loop, and multiple cores come from multiple processes."
> - "The dangerous mistake differs: in asyncio or WebFlux one blocking call stalls everything on that loop; in Go or with virtual threads it mostly just ties up a goroutine or carrier."
> - "In every model the real limit at 10k connections is the downstream: database pools and rate limits, so I bound concurrency there."
> - "Cancellation is context in Go, `CancelledError` in asyncio, interrupts and structured concurrency in Java."

## Related Notes

- [[05 - Java Platform and Virtual Threads]] · [[01 - Go Runtime and the GMP Scheduler]] · [[03 - The GIL and Free-Threaded Python]] · [[05 - asyncio Internals]]
- [[03 - Web Server Models Compared]] · [[02 - Memory and GC Compared]] · [[LLD/Concurrency/README]] · [[Languages/README]]
