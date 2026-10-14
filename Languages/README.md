---
tags: [languages, status/draft]
created: 2026-10-14
---
# Languages

> [!abstract] What this is
> Java + Spring Boot, Python + FastAPI (+ Python for AI), and Go + Gin at the depth production interviews probe: how each **runtime** actually works (threads, scheduler, memory, GC, compilation), the **language-level** idioms and traps, and how each **framework** behaves in production. Equal depth for all four stacks.

---

## The Goal

- Answer anything an interviewer asks about these stacks in a production context, from "why doesn't `@Transactional` work on this method?" to "how does Go's scheduler avoid blocking OS threads on I/O?" to "why can one blocking call freeze a FastAPI service?".
- Every stack is studied in the same **three layers**, so they can be compared like for like:
    1. **Runtime and architecture**: execution model, threads and scheduling, memory and GC, compilation and startup, diagnostics.
    2. **Language and coding level**: types, idioms, errors, concurrency primitives, collections, the traps that show up in code review.
    3. **Framework and production**: request lifecycle, data access, configuration, security, observability, testing, deployment, performance tuning.
- Each language has a **Version History** note: the major releases and the features that defined them, so "what changed in Java 21?" or "what did Go 1.18 add?" is never a surprise.

## Versions Covered

| Stack | Latest stable (Oct 2026) | Also know |
|---|---|---|
| **Java** | JDK 27 (Sep 2026) | JDK 25 is the current **LTS** (Sep 2025); previous LTS releases 21, 17, 11, 8 |
| **Spring Boot** | 4.1 (Jun 2026), on Spring Framework 7 | 4.0 (Nov 2025); the 3.x line is out of OSS support since Jun 2026 |
| **Python** | 3.15 (Oct 2026) | Default builds still have the GIL; 3.14 made the separate free-threaded build officially supported (3.13 introduced it experimentally) |
| **FastAPI** | 0.143 (Oct 2026; still 0.x, pin exact versions) | Starlette 1.x and Pydantic v2 only (v1 dropped in 0.126–0.128); Python 3.10+; built-in OpenTelemetry since 0.142 |
| **Go** | 1.27 (Aug 2026) | 1.26 (Feb 2026); two releases a year, Go 1 compatibility promise |
| **Gin** | 1.12 | 1.11 added HTTP/3 support |

- Notes say which version introduced a feature, so they stay useful as new releases land.

---

## Structure

```
Languages/
  Java/
    Java/            JVM, memory, GC, threads, language, diagnostics
    Spring Boot/     container, web, data, security, observability, testing
  Python/
    Python/          CPython, GIL, asyncio, WSGI/ASGI, language, tooling
    FastAPI/         Starlette + Pydantic, lifecycle, DI, data, streaming, production
    Python for AI/   LLM clients, NumPy, PyTorch for inference, HF stack, serving
  Go/
    Go/              runtime, GMP scheduler, memory, GC, language, tooling
    Gin/             net/http model, Gin internals, middleware, data, production
  Cross-Language/    side-by-side comparisons
```

- Existing notes are **linked, not repeated**: Java concurrency details live in [[LLD/Concurrency/README]]; exceptions, generics, collections and DI basics in [[LLD/Foundations/README]].

---

## Syllabus

### Java / Java

| # | Note | Covers |
|---|---|---|
| 00 | [[00 - Java Version History\|Java Version History]] | 8 (lambdas, streams) → 11 → 17 (records, sealed) → 21 (virtual threads, pattern matching) → 25 LTS → 27; LTS cadence |
| 01 | [[01 - JVM Architecture and Class Loading\|JVM Architecture and Class Loading]] | Bytecode, class loaders and delegation, linking, initialisation, the execution engine |
| 02 | [[02 - JIT Compilation and AOT\|JIT Compilation and AOT]] | Interpreter, C1/C2 tiered compilation, inlining, escape analysis, deoptimisation, GraalVM native image, CDS and AOT caches |
| 03 | [[03 - JVM Memory Layout\|JVM Memory Layout]] | Heap and generations, TLABs, metaspace, thread stacks, direct memory, object headers, compressed oops |
| 04 | [[04 - Java Garbage Collectors\|Java Garbage Collectors]] | G1, generational ZGC, Shenandoah, Parallel; pauses, safepoints, choosing and tuning, reading GC logs |
| 05 | [[05 - Java Platform and Virtual Threads\|Java Platform and Virtual Threads]] | 1:1 platform threads, M:N virtual threads, carriers, pinning, structured concurrency, scoped values |
| 06 | [[06 - Modern Java Language Features\|Modern Java Language Features]] | Records, sealed types, pattern matching, switch expressions, text blocks, `var` |
| 07 | [[07 - Java Collections Internals\|Java Collections Internals]] | `HashMap` (hashing, resizing, treeification), `ConcurrentHashMap`, `ArrayList`, `LinkedHashMap`, iteration traps |
| 08 | [[08 - Java Streams Optional and Functional Style\|Java Streams Optional and Functional Style]] | Laziness, collectors, parallel-stream pitfalls, `Optional` rules |
| 09 | [[09 - Java Reflection Annotations and Proxies\|Java Reflection Annotations and Proxies]] | Reflection costs, annotations, JDK dynamic proxies vs bytecode generation: the basis of Spring |
| 10 | [[10 - Java Strings Numbers and Time\|Java Strings Numbers and Time]] | String internals and interning, `BigDecimal`, `java.time`, equality traps |
| 11 | [[11 - Java IO NIO and Networking\|Java IO NIO and Networking]] | Blocking IO vs NIO selectors, `HttpClient`, sockets, buffers |
| 12 | [[12 - JVM Diagnostics and Profiling\|JVM Diagnostics and Profiling]] | Thread and heap dumps, JFR, async-profiler, `jcmd`, OOM types, leak hunting |
| 13 | [[13 - Java in Containers and Production Tuning\|Java in Containers and Production Tuning]] | Container awareness, heap sizing, GC threads vs CPU limits, startup, flags that matter |
| 14 | [[14 - Java Build and Dependencies\|Java Build and Dependencies]] | Maven vs Gradle, dependency resolution and conflicts, BOMs, reproducible builds |

### Java / Spring Boot

| # | Note | Covers |
|---|---|---|
| 00 | [[00 - Spring Boot Version History\|Spring Boot Version History]] | Boot 1 → 2 → 3 (Jakarta EE, Java 17 baseline, AOT/native) → 4 (Framework 7, modular Boot, null-safety, API versioning) → 4.1 |
| 01 | [[01 - Spring IoC and Bean Lifecycle\|Spring IoC and Bean Lifecycle]] | Container, bean definitions, scopes, lifecycle callbacks, circular dependencies |
| 02 | [[02 - Spring Boot Auto-Configuration and Config\|Spring Boot Auto-Configuration and Config]] | Conditions, starters, property sources and precedence, profiles, `@ConfigurationProperties` |
| 03 | [[03 - Spring AOP and Proxies\|Spring AOP and Proxies]] | JDK vs CGLIB proxies, self-invocation trap, why `@Transactional` / `@Async` / `@Cacheable` silently don't apply |
| 04 | [[04 - Spring MVC Request Lifecycle\|Spring MVC Request Lifecycle]] | Filters, `DispatcherServlet`, handler mapping, interceptors, message converters, Tomcat threads, virtual threads |
| 05 | [[05 - Spring WebFlux and Reactive\|Spring WebFlux and Reactive]] | Netty event loop, Reactor, backpressure, never blocking the loop, when it's worth it |
| 06 | [[06 - Spring Transactions\|Spring Transactions]] | Propagation, isolation, rollback rules, transaction boundaries, `TransactionTemplate` |
| 07 | [[07 - Spring Data JPA and Hibernate\|Spring Data JPA and Hibernate]] | Persistence context, dirty checking, lazy loading, N+1, fetch strategies, open-session-in-view, locking |
| 08 | [[08 - Spring Database Connections and Migrations\|Spring Database Connections and Migrations]] | HikariCP sizing, JDBC vs JPA, Flyway/Liquibase, read replicas |
| 09 | [[09 - Spring Security\|Spring Security]] | Filter chain, authentication flows, JWT resource server, OAuth2, method security, CSRF/CORS |
| 10 | [[10 - Spring Validation Errors and API Design\|Spring Validation Errors and API Design]] | Bean validation, `@ControllerAdvice`, problem details, API versioning |
| 11 | [[11 - Spring HTTP Clients Resilience and Messaging\|Spring HTTP Clients Resilience and Messaging]] | `RestClient`, HTTP interface clients, timeouts, Resilience4j, Kafka/RabbitMQ integration |
| 12 | [[12 - Spring Observability\|Spring Observability]] | Actuator, health groups, Micrometer metrics, OpenTelemetry tracing, structured logging |
| 13 | [[13 - Spring Boot Testing\|Spring Boot Testing]] | Test slices, Testcontainers, mocking beans, context caching |
| 14 | [[14 - Spring Boot Performance and Native\|Spring Boot Performance and Native]] | Startup, memory, lazy init, AOT processing, GraalVM native images, CRaC |

### Python / Python

- Centred on the **standard (GIL) build** of the latest releases (3.14, 3.15). Free-threaded builds and subinterpreters are covered as extra sections, since most production Python still runs with the GIL.

| # | Note | Covers |
|---|---|---|
| 00 | [[00 - Python Version History\|Python Version History]] | 2 → 3, 3.5 async/await, 3.6–3.8 milestones, 3.10 `match`, 3.11 faster CPython, 3.12 type syntax, 3.13 experimental no-GIL and JIT, 3.14 supported free-threading and t-strings, 3.15 lazy imports and `frozendict` |
| 01 | [[01 - CPython Execution Model\|CPython Execution Model]] | Source → bytecode → eval loop, specialising adaptive interpreter, the experimental JIT, frames |
| 02 | [[02 - Python Memory Management and GC\|Python Memory Management and GC]] | Everything is an object, reference counting, cyclic GC, pymalloc, why memory isn't returned to the OS |
| 03 | [[03 - The GIL and Free-Threaded Python\|The GIL and Free-Threaded Python]] | What the GIL protects, CPU- vs I/O-bound behaviour, C extensions releasing it, free-threaded builds, subinterpreters |
| 04 | [[04 - Python Threads Processes and Executors\|Python Threads Processes and Executors]] | `threading`, `multiprocessing` (fork vs spawn, pickling), `concurrent.futures`, when to use each |
| 05 | [[05 - asyncio Internals\|asyncio Internals]] | Event loop, coroutines vs tasks vs futures, blocking the loop, `TaskGroup`, timeouts, cancellation, uvloop |
| 06 | [[06 - WSGI vs ASGI and Python Servers\|WSGI vs ASGI and Python Servers]] | WSGI sync model, ASGI async model, Gunicorn workers, Uvicorn, pre-fork, sizing workers |
| 07 | [[07 - Python Data Model\|Python Data Model]] | Dunder methods, descriptors, properties, `__slots__`, MRO, metaclasses in brief |
| 08 | [[08 - Decorators Generators and Context Managers\|Decorators Generators and Context Managers]] | Closures, late binding, generators and `yield from`, context managers, common traps |
| 09 | [[09 - Python Typing\|Python Typing]] | Type hints, generics, `Protocol`, `TypedDict`, mypy/pyright, dataclasses vs Pydantic vs attrs |
| 10 | [[10 - Python Errors and Exception Groups\|Python Errors and Exception Groups]] | Exception hierarchy, chaining, `ExceptionGroup` and `except*`, cleanup |
| 11 | [[11 - Python Packaging and Environments\|Python Packaging and Environments]] | `pyproject.toml`, uv, virtualenvs, wheels, lockfiles, dependency hell |
| 12 | [[12 - Python Testing with pytest\|Python Testing with pytest]] | Fixtures, parametrisation, mocking, async tests |
| 13 | [[13 - Python Performance and Profiling\|Python Performance and Profiling]] | cProfile, py-spy, scalene, vectorisation, C/Rust extensions, PyPy |

### Python / FastAPI

| # | Note | Covers |
|---|---|---|
| 00 | [[00 - FastAPI Version History and Ecosystem\|FastAPI Version History and Ecosystem]] | Starlette, Pydantic v1 → v2, versioning policy (0.x, pin exactly), notable releases |
| 01 | [[01 - FastAPI Architecture\|FastAPI Architecture]] | Starlette (ASGI app, routing, middleware) + Pydantic (validation, serialisation) + OpenAPI generation |
| 02 | [[02 - FastAPI Request Lifecycle and Sync vs Async\|FastAPI Request Lifecycle and Sync vs Async]] | `async def` on the loop vs `def` in the threadpool, the blocking-call outage, threadpool limits |
| 03 | [[03 - FastAPI Dependency Injection\|FastAPI Dependency Injection]] | `Depends`, yield dependencies, scopes, sub-dependencies, overrides |
| 04 | [[04 - Pydantic v2 Deep Dive\|Pydantic v2 Deep Dive]] | Models, validators, serialisation, settings, the Rust core, performance |
| 05 | [[05 - FastAPI Data Access with SQLAlchemy\|FastAPI Data Access with SQLAlchemy]] | SQLAlchemy 2.0 async, sessions per request, pools, asyncpg, Alembic |
| 06 | [[06 - FastAPI Streaming SSE and WebSockets\|FastAPI Streaming SSE and WebSockets]] | `StreamingResponse`, SSE for LLM streams, WebSockets, disconnect handling |
| 07 | [[07 - FastAPI Security and Middleware\|FastAPI Security and Middleware]] | OAuth2/JWT, CORS, rate limiting, middleware ordering |
| 08 | [[08 - FastAPI Background Work\|FastAPI Background Work]] | `BackgroundTasks` limits vs real queues (Celery, arq, Dramatiq), lifespan resources |
| 09 | [[09 - FastAPI Production Deployment\|FastAPI Production Deployment]] | Workers, timeouts, graceful shutdown, OpenTelemetry, health checks |
| 10 | [[10 - FastAPI Testing\|FastAPI Testing]] | `TestClient`, `httpx.AsyncClient`, dependency overrides, DB fixtures |

### Python / Python for AI

| # | Note | Covers |
|---|---|---|
| 01 | [[01 - Async LLM Client Patterns\|Async LLM Client Patterns]] | Official SDKs sync vs async, bounded concurrency, retries, timeouts, streaming parsing, structured outputs |
| 02 | [[02 - NumPy for Engineers\|NumPy for Engineers]] | Arrays, dtypes, broadcasting, vectorisation, memory layout, views vs copies |
| 03 | [[03 - Embeddings and Similarity in Python\|Embeddings and Similarity in Python]] | Normalisation, batched cosine similarity, top-k, memory math |
| 04 | [[04 - PyTorch for Inference Engineers\|PyTorch for Inference Engineers]] | Tensors, devices, dtypes, `inference_mode`, batching, CUDA memory, mixed precision |
| 05 | [[05 - Hugging Face Stack\|Hugging Face Stack]] | `transformers`, tokenizers, `datasets`, PEFT/LoRA, loading quantized models |
| 06 | [[06 - Serving Models from Python\|Serving Models from Python]] | vLLM Python API and OpenAI-compatible server, GPU worker processes vs API processes |
| 07 | [[07 - Data Tooling for Evals\|Data Tooling for Evals]] | pandas vs Polars, eval datasets, result analysis |
| 08 | [[08 - Python AI Performance Traps\|Python AI Performance Traps]] | GIL with CPU-heavy pre/post-processing, process pools for tokenisation, forked workers and model memory |

### Go / Go

| # | Note | Covers |
|---|---|---|
| 00 | [[00 - Go Version History\|Go Version History]] | Go 1 compatibility promise, 1.5 concurrent GC, 1.11 modules, 1.13 error wrapping, 1.14 async preemption, 1.18 generics, 1.19 `GOMEMLIMIT`, 1.21 `slog`, 1.22 loop variables and routing, 1.23 iterators, 1.24 Swiss-table maps, 1.25 container-aware `GOMAXPROCS`, 1.26 new GC default and leak detection, 1.27 generic methods and `encoding/json/v2` |
| 01 | [[01 - Go Runtime and the GMP Scheduler\|Go Runtime and the GMP Scheduler]] | Goroutines, G-M-P, run queues, work stealing, network poller, syscalls, preemption, `GOMAXPROCS` |
| 02 | [[02 - Go Memory and Escape Analysis\|Go Memory and Escape Analysis]] | Stack growth, escape analysis, allocator size classes, struct layout and alignment |
| 03 | [[03 - Go Garbage Collector\|Go Garbage Collector]] | Concurrent tri-colour mark-sweep, write barriers, pacing, `GOGC`, `GOMEMLIMIT`, newer GC designs |
| 04 | [[04 - Go Types Interfaces and Embedding\|Go Types Interfaces and Embedding]] | Implicit interfaces, interface values, nil-interface trap, embedding, method sets, value vs pointer receivers |
| 05 | [[05 - Go Slices Maps and Strings Internals\|Go Slices Maps and Strings Internals]] | Slice headers and `append` aliasing, map internals, strings vs bytes vs runes |
| 06 | [[06 - Go Errors and Panics\|Go Errors and Panics]] | Errors as values, wrapping, `errors.Is/As`, sentinel vs typed errors, `panic`/`recover` |
| 07 | [[07 - Go Concurrency Primitives\|Go Concurrency Primitives]] | Channels, `select`, closing rules, `sync`, `sync/atomic`, `errgroup` |
| 08 | [[08 - Go Concurrency Patterns and Context\|Go Concurrency Patterns and Context]] | Worker pools, pipelines, fan-in/out, bounded concurrency, `context` cancellation and deadlines, goroutine leaks |
| 09 | [[09 - Go Generics\|Go Generics]] | Type parameters, constraints, generic methods (1.27), when not to use them |
| 10 | [[10 - Go Modules Build and Tooling\|Go Modules Build and Tooling]] | Modules, versioning, `go vet`, `go fix`, static binaries, cross-compilation, cgo costs |
| 11 | [[11 - Go Testing and Benchmarks\|Go Testing and Benchmarks]] | Table-driven tests, subtests, fuzzing, benchmarks, `testing/synctest` |
| 12 | [[12 - Go Diagnostics pprof and Tracing\|Go Diagnostics pprof and Tracing]] | CPU/heap/goroutine/block/mutex profiles, execution tracer, race detector, leak profiles |
| 13 | [[13 - Go in Containers and Production\|Go in Containers and Production]] | Small images, CPU and memory limits, GC tuning in containers, graceful shutdown |

### Go / Gin

| # | Note | Covers |
|---|---|---|
| 00 | [[00 - Gin Version History\|Gin Version History]] | Major releases, HTTP/3 in 1.11, 1.12 changes |
| 01 | [[01 - Go net-http Server Model\|Go net/http Server Model]] | Goroutine per connection, `Handler`, server timeouts, `http.Client` reuse and body leaks, 1.22 routing |
| 02 | [[02 - Gin Architecture Routing and Context\|Gin Architecture Routing and Context]] | Radix-tree router, `*gin.Context` lifecycle, pooling, `c.Copy()` across goroutines |
| 03 | [[03 - Gin Middleware\|Gin Middleware]] | Chain, `c.Next()`/`c.Abort()`, recovery, logging, auth, ordering |
| 04 | [[04 - Gin Binding Validation and Errors\|Gin Binding Validation and Errors]] | Binding, validator tags, error handling and responses |
| 05 | [[05 - Go Data Access\|Go Data Access]] | `database/sql` pools, pgx, sqlc vs GORM, transactions with context |
| 06 | [[06 - Gin Production\|Gin Production]] | Graceful shutdown, timeouts, `slog`, Prometheus, OpenTelemetry, config |
| 07 | [[07 - Gin Project Structure and Testing\|Gin Project Structure and Testing]] | Layout, constructor wiring, `httptest` |
| 08 | [[08 - Gin vs Alternatives\|Gin vs Alternatives]] | Echo, Fiber (fasthttp caveats), chi, plain `net/http` |

### Cross-Language

| # | Note | Covers |
|---|---|---|
| 01 | Concurrency Models Compared | Java platform vs virtual threads, Go goroutines, Python threads + GIL, asyncio, event loops; 10k connections in each |
| 02 | Memory and GC Compared | JVM GCs vs Go's collector vs CPython refcounting; latency vs throughput; tuning knobs |
| 03 | Web Server Models Compared | Tomcat thread-per-request, Netty/WebFlux, Go `net/http`, WSGI pre-fork, ASGI event loop |
| 04 | Error Handling Compared | Checked/unchecked exceptions vs errors as values vs Python exceptions |
| 05 | Compilation and Startup Compared | JIT vs AOT vs interpreted; containers, serverless, autoscaling implications |
| 06 | Choosing a Language for a Service | Decision table by workload, team and operational needs |

---

## How the Notes Are Written

- Same shape as the other topic notes ([[CONVENTIONS]]): abstract → why it matters → concept sections with comparison tables → code where it teaches → **Failure Modes** → **Interview Lines** → related notes.
- Code examples in the language being discussed, kept short.
- Each feature is tagged with the version that introduced it.

## Reading Order

Java → Spring Boot → Python → FastAPI → Python for AI → Go → Gin → Cross-Language.

## Related Notes

- [[LLD/Concurrency/README]] · [[LLD/Foundations/README]] · [[HLD/Topics/README]] · [[Agentic/README]]
