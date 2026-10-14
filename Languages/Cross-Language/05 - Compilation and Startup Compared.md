---
tags: [languages/cross-language, status/draft]
created: 2026-10-14
---
# Compilation and Startup Compared

> [!abstract] What this is
> How code becomes running machine code in each stack and what that means operationally: **Java's JIT** (interpreter → C1 → C2, warm-up) and its AOT options (**CDS/AOT cache** from JDK 24/25, **GraalVM native image**, **CRaC**), **Go's ahead-of-time compilation** to static binaries (with **PGO**), and **CPython's bytecode interpreter** (specialising adaptive interpreter since 3.11, experimental JIT since 3.13, lazy imports in 3.15). Compares startup time, warm-up, peak performance, memory, image size and the implications for **containers, autoscaling and serverless**. The decision this helps with: choosing runtime options for latency-sensitive scale-out and short-lived workloads.

---

## Why It Matters

- Autoscaling and serverless reward fast startup and no warm-up; long-running high-throughput services reward peak performance. Each runtime sits at a different point, and each has options to move along that curve.

---

## At a Glance

| | Java (HotSpot JIT) | Java native image | Go | CPython |
|---|---|---|---|---|
| Compilation | Bytecode → JIT at runtime (profile-guided) | Closed-world AOT at build time | AOT to native, static binary | Bytecode, interpreted (specialised); experimental JIT |
| Startup | Seconds for frameworks (Spring) | Tens of ms | **Milliseconds** | 100s of ms–seconds (imports dominate) |
| Warm-up | Minutes to peak (C2) | None (no JIT) | None | Short (specialisation warms in calls) |
| Peak CPU performance | **Highest** (speculative C2) | Lower than C2 unless PGO | High, predictable | **Lowest** for pure-Python code (native libs do the heavy lifting) |
| Memory baseline | Highest (JIT, metaspace, heap headroom) | Low | **Low** | Moderate per process; × workers |
| Image size | JRE + jars (100–300 MB, smaller with jlink) | Single binary (50–100 MB) | **Single binary (10–30 MB)** on distroless | Interpreter + site-packages (often 200 MB–GBs with ML libs) |
| Build time | Fast | Slow (minutes, GBs RAM) | Fast | None (dependency install) |

---

## Java

- Interpreted bytecode is compiled by **C1** then **C2** using runtime profiles; C2 speculates and deoptimises when assumptions break, reaching the best peak performance of the three, after warm-up ([[02 - JIT Compilation and AOT]]).
- Startup levers:
    - **CDS / AOT cache** (JDK 24 class loading/linking, JDK 25 single-step `-XX:AOTCacheOutput` and method profiles): faster start and warm-up while keeping C2 peak ([[14 - Spring Boot Performance and Native]]).
    - **GraalVM native image**: closed world, millisecond start, small RSS; reflection/proxies need metadata; lower peak without PGO.
    - **CRaC**: restore a checkpointed warm JVM in milliseconds; snapshot contains memory (secrets).
- Container effect: cold JVMs serve the first minutes slower; readiness after warm-up, CPU headroom during startup ([[13 - Java in Containers and Production Tuning]]).

## Go

- Compiled ahead of time to native code: **no warm-up**, startup dominated by `init` functions and connecting to dependencies ([[10 - Go Modules Build and Tooling]]).
- `CGO_ENABLED=0` produces a static binary for `scratch`/distroless images; cross-compilation is trivial.
- **PGO** (GA 1.21): feed a production CPU profile back into the build for typically 2–14% speedups through better inlining and devirtualisation.
- No speculative optimisation: peak is usually below a warmed C2 for the same algorithm, but predictable from the first request.

## Python (CPython)

- Source → bytecode (`.pyc` cache) → evaluation loop; 3.11's **specialising adaptive interpreter** speeds hot monomorphic code; the **copy-and-patch JIT** (experimental since 3.13) adds single-digit percent so far ([[01 - CPython Execution Model]]).
- Startup is mostly **imports**: frameworks, ML libraries and SDKs can take seconds; measure with `-X importtime`; 3.15 **`lazy import`** defers them; bake model weights into images rather than downloading at start ([[08 - Python AI Performance Traps]]).
- Throughput comes from native extensions (NumPy, Pydantic-core, orjson, PyTorch), not from the interpreter ([[13 - Python Performance and Profiling]]).

---

## Implications by Deployment Type

| Deployment | Java | Go | Python |
|---|---|---|---|
| **Long-running, steady traffic** | JIT + AOT cache; peak performance wins | Default build (+PGO) | Default; scale processes; vectorise hot paths |
| **Bursty autoscaling** | AOT cache, CRaC, or native image; warm-up traffic before readiness | Excellent as-is | Keep images lean; lazy imports; preload models |
| **Serverless / scale-to-zero** | Native image or SnapStart/CRaC-style restore | Excellent | Acceptable for light deps; heavy ML deps hurt cold starts |
| **CLIs and short jobs** | Native image (or accept JVM start) | Excellent | Fine; lazy imports help |
| **Many small instances (density)** | Native image or small heaps | Excellent | Moderate (memory per process) |

---

## Failure Modes

| Failure | Java | Go | Python |
|---|---|---|---|
| Cold instances take traffic too early | Latency spikes until C2 warms | Rare | Slow first requests if lazy init/model load |
| Startup time breaks probes/autoscaling | Framework startup + JIT | Slow `init` / dependency connects | Heavy imports, model downloads |
| AOT/native assumptions | Stale AOT cache; missing reflection metadata in native | cgo breaks static builds | — |
| Image bloat | Full JDK in production images | Non-static builds needing libc | CUDA/ML wheels, dev deps in prod |

---

## Interview Lines

> [!tip] Say it like this
> - "Java interprets then JIT-compiles with profiles, so it starts slower and warms up but reaches the highest peak; the JDK's AOT cache cuts start and warm-up, and native image trades peak for millisecond startup."
> - "Go compiles ahead of time into a static binary: millisecond startup, no warm-up, tiny images, and PGO recovers some of what a JIT gets from profiles."
> - "CPython interprets bytecode with a specialising interpreter and an experimental JIT; startup is dominated by imports, and real throughput comes from native extensions."
> - "For bursty autoscaling or serverless, Go is easiest, Java needs AOT caches, CRaC or native images, and Python needs lean images and lazy imports."

## Related Notes

- [[02 - JIT Compilation and AOT]] · [[14 - Spring Boot Performance and Native]] · [[10 - Go Modules Build and Tooling]] · [[01 - CPython Execution Model]]
- [[02 - Memory and GC Compared]] · [[06 - Choosing a Language for a Service]] · [[Languages/README]]
