---
tags: [languages/cross-language, status/draft]
created: 2026-10-14
---
# Choosing a Language for a Service

> [!abstract] What this is
> A decision guide for picking **Java/Spring Boot**, **Go/Gin (or net/http)** or **Python/FastAPI** for a new service, based on workload (I/O-bound APIs, CPU-heavy processing, streaming, ML/LLM integration, high fan-out), operational needs (startup, memory, containers, serverless), team and ecosystem, and organisational fit. Summarises each stack's strengths and costs with references to the detailed notes, and gives answer patterns for "why would you choose X?" design questions. The decision this helps with: a defensible, workload-driven language choice instead of a preference.

---

## Why It Matters

- System design interviews and architecture reviews ask "what would you build this in, and why?" Good answers name the workload properties and trade-offs; weak answers name a favourite.
- In practice, team skills and the existing platform often outweigh runtime differences; say so, but know the technical differences too.

---

## Strengths and Costs

| | **Java + Spring Boot** | **Go (+ Gin/net/http)** | **Python + FastAPI** |
|---|---|---|---|
| Sweet spot | Large domain-heavy services, enterprise integration, complex transactions | Infrastructure, networking, high-concurrency APIs, CLIs, platform tooling | ML/LLM-integrated services, data/AI APIs, rapid iteration |
| Concurrency | Virtual threads (21+): blocking code at high concurrency ([[05 - Java Platform and Virtual Threads]]) | Goroutines, simplest model ([[01 - Go Runtime and the GMP Scheduler]]) | asyncio for I/O; processes for CPU; GIL in the standard build ([[03 - The GIL and Free-Threaded Python]]) |
| CPU throughput | Highest after warm-up (C2) | High, predictable | Low for pure Python; high via native libraries |
| Startup / memory | Slowest/largest by default; AOT cache, native image help ([[14 - Spring Boot Performance and Native]]) | Fast and small | Imports dominate; memory × workers |
| Ecosystem | Spring (security, data, messaging, batch), mature libraries for everything | Strong stdlib; smaller, explicit libraries | Unmatched for ML/AI/data (PyTorch, HF, vLLM clients, Polars) |
| Data access | JPA/Hibernate, jOOQ, JDBC ([[07 - Spring Data JPA and Hibernate]]) | database/sql, pgx + sqlc ([[05 - Go Data Access]]) | SQLAlchemy 2.0 async ([[05 - FastAPI Data Access with SQLAlchemy]]) |
| Type safety | Strong static types; compile-time checks | Strong static types; simple type system | Gradual typing (mypy/pyright/Pyrefly) + Pydantic at runtime ([[09 - Python Typing]]) |
| Operational simplicity | More tuning (heap, GC, threads) ([[13 - Java in Containers and Production Tuning]]) | Simplest: static binary, two GC knobs ([[13 - Go in Containers and Production]]) | Worker/process management, blocking-call discipline ([[09 - FastAPI Production Deployment]]) |
| Main risks | Framework magic (proxies, auto-config), memory footprint | Verbose error handling, fewer high-level frameworks | Event-loop blocking, GIL for CPU work, dependency weight |

---

## By Workload

| Workload | Lean towards | Why |
|---|---|---|
| CRUD/business API with rich domain rules and transactions | **Java/Spring** (or Go) | Mature transactions, validation, security, ORM; large team conventions |
| High-concurrency gateway, proxy, websocket fan-out | **Go** (or Java WebFlux/virtual threads) | Cheap goroutines, low memory, fast start |
| LLM orchestration, RAG, agents calling model APIs | **Python** (or Go/Java with SDKs) | Python ecosystem (SDKs, eval tooling, HF); I/O-bound so asyncio fits ([[01 - Async LLM Client Patterns]]) |
| Self-hosted model inference | **Python** engine processes (vLLM) behind any API language | GPU stack is Python/C++ ([[06 - Serving Models from Python]]) |
| CPU-heavy data processing in the request path | **Java or Go**, or Python with native libs/process pools | GIL limits Python threads |
| Batch ETL / analytics | **Python** (Polars/DuckDB) or JVM (Spark/Flink) | Ecosystem; JVM for big-data frameworks |
| Serverless / scale-to-zero functions | **Go**, Python (light deps), Java native image | Startup and memory ([[05 - Compilation and Startup Compared]]) |
| Platform tooling, CLIs, Kubernetes operators | **Go** | Static binaries, Kubernetes ecosystem in Go |
| Streaming/event processing at scale | **Java** (Kafka Streams, Flink) or Go | Mature stream-processing libraries on the JVM |

---

## Organisational Factors

| Factor | Effect on the choice |
|---|---|
| Existing team skills and hiring market | Often decisive: a well-run service in the team's language beats a "better" language run badly |
| Platform support (logging, tracing, auth libraries, CI templates) | Paved roads reduce risk; new languages need that work |
| Polyglot cost | Each extra language multiplies tooling, security patching, on-call knowledge |
| Library availability for key integrations | SDKs for payment providers, cloud services, ML frameworks |
| Compliance/security requirements | FIPS crypto modules (Go 1.24+ FIPS module, Java providers), supply-chain tooling |

---

## Common Architectures

- **Polyglot by tier**: Go or Java for edge and core transactional services, Python for ML/AI services, communicating over HTTP/gRPC with shared observability and contracts.
- **Python API + Python inference** for AI-first products, with CPU-heavy or high-fan-out pieces split into Go/Java services as they grow.
- **Java monolith → modular services** in Spring Boot when the domain is large and the team is JVM-centric.

---

## Answer Pattern for Interviews

1. Name the **workload properties**: I/O vs CPU, concurrency level, latency targets, startup/scale pattern, data access style, ML needs.
2. Map them to **runtime properties**: concurrency model, GC/memory, startup, ecosystem ([[01 - Concurrency Models Compared]], [[02 - Memory and GC Compared]]).
3. Weigh **team and platform** fit.
4. State the **risks and mitigations** of the choice (e.g. "Python: no blocking calls in async code, CPU work in process pools").

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| Choosing by benchmark headlines | Little real gain, high migration cost | Choose by workload and team; measure the actual bottleneck |
| Python for CPU-bound request paths | GIL-limited throughput, latency | Native libraries, process pools, or a Go/Java service |
| Java defaults in tiny containers | Memory and startup pain | Tune heap/GC, AOT cache, or Go for tiny services |
| Go for heavy domain logic without structure | Verbose, ad hoc layering | Clear packages, sqlc, conventions ([[07 - Gin Project Structure and Testing]]) |
| Too many languages | Fragmented tooling and on-call | Limit to two or three supported stacks with paved roads |

---

## Interview Lines

> [!tip] Say it like this
> - "I'd pick by workload: Go for high-concurrency, low-footprint networking services; Java and Spring for large domain-heavy transactional systems; Python and FastAPI where ML and LLM ecosystems matter, keeping CPU work off the event loop."
> - "Since virtual threads, Java handles high concurrency with blocking code too, so the Java-versus-Go choice is more about footprint, startup and team than raw concurrency."
> - "For AI products I'd often put the API in Python next to its SDKs and eval tooling, with model serving in a dedicated vLLM tier."
> - "Team skills and platform support usually decide; I'd keep the number of languages small and give each a paved road."

## Related Notes

- [[01 - Concurrency Models Compared]] · [[02 - Memory and GC Compared]] · [[03 - Web Server Models Compared]] · [[04 - Error Handling Compared]] · [[05 - Compilation and Startup Compared]]
- [[08 - Gin vs Alternatives]] · [[Agentic/README]] · [[HLD/Topics/README]] · [[Languages/README]]
