---
tags: [languages/cross-language, status/draft]
created: 2026-10-14
---
# Memory and GC Compared

> [!abstract] What this is
> How the JVM, Go and CPython manage memory side by side: **object layout and per-object overhead**, **allocation** (TLAB bump pointers, size-class allocators, pymalloc arenas), **collection** (generational moving collectors like G1/ZGC vs Go's concurrent non-moving mark-sweep with Green Tea vs CPython's reference counting plus a generational cycle collector), **pauses vs throughput**, **returning memory to the OS**, and the **tuning knobs** that matter in containers (`-Xmx`/`MaxRAMPercentage`, `GOGC`/`GOMEMLIMIT`, worker recycling). The decision this helps with: predicting memory behaviour and tuning each runtime under a container memory limit.

---

## Why It Matters

- "Why does the pod get OOMKilled?" has different answers per runtime: native memory beyond the JVM heap, a Go heap growing to 2× live without `GOMEMLIMIT`, Python workers that never shrink.
- GC design determines latency profiles: JVM collectors trade complexity for low pauses on huge heaps; Go keeps pauses tiny by staying simple; CPython frees most memory immediately but fragments.

---

## At a Glance

| | JVM (HotSpot) | Go | CPython (GIL build) |
|---|---|---|---|
| Primary reclamation | Tracing GC | Tracing GC | **Reference counting** (immediate) + cycle GC |
| Generational | Yes (G1, generational ZGC/Shenandoah) | **No** | Yes (cycle collector; 3.14's incremental GC reverted in 3.14.5/3.15) |
| Moving / compacting | Yes | **No** (non-moving) | No |
| Concurrent | Mostly (G1 marking; ZGC/Shenandoah marking + relocation) | Mostly (marking; short STW phases) | No (cycle GC stops the interpreter while it runs) |
| Typical pauses | G1: tens–hundreds of ms; ZGC: sub-ms | Sub-ms STW; cost shows as GC CPU and mark assist | Cycle collections can take ms–100s of ms on huge heaps |
| Allocation | TLAB bump pointer (very cheap) | Per-P size-class caches (cheap) | pymalloc pools/arenas for ≤ 512 B |
| Stack allocation | Escape analysis (scalar replacement after JIT) | **Escape analysis at compile time** (a major lever) | No: every object is a heap `PyObject` |
| Per-object overhead | 12-byte header (8 with compact headers; default in JDK 27) | No header for plain values; interfaces add 2 words | ≥ 16 bytes (refcount + type) plus type-specific fields |
| Main knobs | Collector choice, `-Xmx`/`MaxRAMPercentage`, pause target | `GOGC`, `GOMEMLIMIT` | Mostly code/data layout; `gc.freeze()`, worker recycling |

---

## Object Overhead in Practice

| Storing 1M small records (two 64-bit numbers each) | Approximate memory |
|---|---|
| Java `ArrayList<Record>` (record objects + references) | ~24–32 MB (headers + fields + references) |
| Java `long[]` pairs | 16 MB |
| Go `[]struct{A, B int64}` | **16 MB** (contiguous, no headers, no GC scanning of pointer-free data) |
| Python list of tuples of ints | **~100+ MB** (list slots + tuple objects + int objects) |
| Python NumPy `(1M, 2)` int64 array | 16 MB |

- Go and Java value-oriented layouts (arrays, pointer-free structs) are cheap; Python needs NumPy/Arrow/Polars to get there ([[02 - NumPy for Engineers]]).
- JVM compressed oops keep references at 4 bytes under ~32 GB heaps ([[03 - JVM Memory Layout]]).

---

## How Each Collects

### JVM

- **Generational, moving**: young collections copy survivors (cost ∝ live objects); old regions are marked concurrently and evacuated/compacted. No fragmentation, but write/load barriers and copying ([[04 - Java Garbage Collectors]]).
- Pick G1 by default (default everywhere since JDK 27), generational ZGC for latency-critical or very large heaps, Parallel for batch throughput.

### Go

- **Concurrent, non-moving, non-generational** tri-colour mark-sweep with write barriers; **Green Tea** (default since 1.26) scans small objects page-by-page for locality. Pauses are tiny; the cost is GC CPU (~25% of Ps during marking) and mark assists on allocating goroutines ([[03 - Go Garbage Collector]]).
- No generations, so **allocation rate** directly drives GC frequency; escape analysis keeping values on the stack is the main defence ([[02 - Go Memory and Escape Analysis]]).

### CPython

- **Reference counting** frees objects the moment the last reference disappears: deterministic, no pauses for most garbage; a generational **cycle collector** handles reference cycles ([[02 - Python Memory Management and GC]]).
- Every reference change writes the object header, which defeats copy-on-write sharing after `fork` and is why the GIL exists.
- pymalloc arenas are released only when completely empty, so memory often stays high after spikes (fragmentation).

---

## Returning Memory and RSS

| Runtime | Behaviour | Practical consequence |
|---|---|---|
| JVM | Heap committed up to `-Xmx`; G1/ZGC can uncommit idle heap periodically; non-heap (metaspace, threads, direct buffers, code cache) adds to RSS | RSS ≈ heap + 20–50% native; size the heap well below the container limit |
| Go | Scavenger returns free pages gradually (faster near `GOMEMLIMIT`); RSS lags heap drops | Use `GOMEMLIMIT`; expect gradual RSS decline |
| CPython | Arenas freed only when empty; glibc malloc arenas grow with threads | RSS plateaus after the largest spike; recycle workers (`max_requests`) |

---

## Container Tuning Recipes

| Runtime | Recipe |
|---|---|
| **JVM** | `-XX:MaxRAMPercentage=60–75` (default 25 is too low), explicit GC choice on ≤ JDK 26, cap metaspace/direct memory, measure native memory with NMT ([[13 - Java in Containers and Production Tuning]]) |
| **Go** | `GOMEMLIMIT` ≈ 90% of the container limit, `GOGC=100` (or `off` + `GOMEMLIMIT` for bursty garbage with a small live set) ([[13 - Go in Containers and Production]]) |
| **Python** | Size workers × RSS per worker within the limit; recycle workers with jitter; `gc.freeze()` before fork; keep big data in arrays/mmap; move models to one inference process ([[06 - WSGI vs ASGI and Python Servers]], [[08 - Python AI Performance Traps]]) |

---

## Diagnosing Leaks

| Runtime | Tools |
|---|---|
| JVM | Heap dumps + Eclipse MAT dominator tree, JFR allocation events, NMT for native ([[12 - JVM Diagnostics and Profiling]]) |
| Go | pprof heap `inuse_space` diffs, `alloc_space` for GC pressure, goroutine/`goroutineleak` profiles ([[12 - Go Diagnostics pprof and Tracing]]) |
| Python | `tracemalloc` snapshot diffs, memray (Python + native), `objgraph` back-references |

---

## Failure Modes

| Failure | JVM | Go | Python |
|---|---|---|---|
| OOMKilled with "small" heap | Native memory (threads, metaspace, direct buffers) | No `GOMEMLIMIT`, heap reached 2× live | Workers × RSS, fragmentation, models per worker |
| GC CPU too high | Live set near heap size, humongous allocations | High allocation rate, pointer-heavy heaps | Huge numbers of container objects (cycle GC scans) |
| Latency spikes | Full GCs, long pauses on G1 with big heaps | Mark assists under allocation bursts | Cycle collections on big heaps; GIL contention |
| Memory not returned | Committed heap kept | Gradual scavenging | Arena fragmentation |

---

## Interview Lines

> [!tip] Say it like this
> - "The JVM uses generational, moving collectors with cheap bump-pointer allocation; Go uses a concurrent non-moving mark-sweep with tiny pauses and relies on escape analysis; CPython frees most objects immediately with reference counting and uses a cycle collector for the rest."
> - "In Go the allocation rate drives GC cost because there are no generations; in Java, survival rate matters more; in Python, object count and fragmentation."
> - "Under a container limit: JVM heap at 60–75% because of native memory, Go `GOMEMLIMIT` at about 90%, Python workers sized by RSS and recycled."
> - "A million small records is about 16 MB as a Go slice of structs or a NumPy array, and over 100 MB as Python tuples."

## Related Notes

- [[03 - JVM Memory Layout]] · [[04 - Java Garbage Collectors]] · [[03 - Go Garbage Collector]] · [[02 - Python Memory Management and GC]]
- [[01 - Concurrency Models Compared]] · [[05 - Compilation and Startup Compared]] · [[Languages/README]]
