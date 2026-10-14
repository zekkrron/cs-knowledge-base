---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Python Performance and Profiling

> [!abstract] What this is
> How to find and fix performance problems in Python services on the standard (GIL) build: **profilers** (cProfile, sampling profilers like **py-spy** and 3.15's built-in **Tachyon**, Scalene, memray, line_profiler), **benchmarking** correctly, and the **optimisation ladder**: algorithms and data structures, avoiding repeated work, **vectorisation** (NumPy, Polars), faster libraries (orjson, msgspec), caching, concurrency, and **compiled extensions** (Cython, mypyc, Rust/PyO3, Numba) or PyPy. The decision this helps with: making a slow Python path fast with the least effort, based on evidence.

---

## Why It Matters

- Python's per-operation overhead is high ([[01 - CPython Execution Model]]), so the gap between a naive and a good implementation is often 10–100×, far more than any interpreter upgrade gives.
- Under the GIL, CPU-heavy code in a web worker slows every concurrent request in that process ([[03 - The GIL and Free-Threaded Python]]).

---

## Measure First

| Tool | Type | Best for |
|---|---|---|
| **`timeit`** / **pyperf** | Micro-benchmark | Comparing two small implementations; pyperf handles warm-up, calibration, outliers |
| **cProfile** (+ snakeviz, pstats) | Deterministic (every call) | Call counts and cumulative time in a test run; adds overhead that distorts small-function-heavy code |
| **Tachyon** (`python -m profiling.sampling`, 3.15) | **Statistical sampling**, built in | Low-overhead profiling of scripts or **attached running processes** by PID; wall-clock, CPU and GIL modes; flame graphs (PEP 799; backport for 3.14 on PyPI) |
| **py-spy** | Sampling, external | Attach to production processes without code changes (`py-spy top --pid`, `py-spy record -o flame.svg`), `dump` for stuck processes |
| **Scalene** | Sampling CPU + memory + GPU | Separates Python vs native time; line-level |
| **pyinstrument** | Sampling, call-tree output | Readable request-level profiles (middleware for web apps) |
| **line_profiler** | Per-line deterministic | Drilling into one known-hot function |
| **memray** | Memory (Python + native) | Allocation hot spots and leaks ([[02 - Python Memory Management and GC]]) |
| Continuous profiling (Pyroscope, Datadog, Grafana) | Always-on sampling | Comparing deploys; finding production-only hot paths |

- In 3.15, the deterministic profiler lives at `profiling.tracing` (cProfile keeps working); `sys.monitoring` (3.12) makes tracing tools and debuggers cheaper than the old `sys.settrace`.
- Profile **realistic workloads** (production-like data sizes and traffic), and confirm the problem is CPU in Python. Wall-clock profiles often show the real issue is **waiting**: DB queries, HTTP calls, lock contention.

```bash
python -m profiling.sampling --help          # 3.15: run a script or attach to a PID; flame graph output
py-spy record -o profile.svg --pid 12345 --duration 30
python -m cProfile -o out.prof app.py && snakeviz out.prof
```

---

## The Optimisation Ladder

| Rung | Typical gain | Examples |
|---|---|---|
| 1. **Do less work** | 10–1000× | Better algorithm (O(n²) → O(n log n)), `set`/`dict` lookups instead of list scans, avoid repeated parsing/queries, push filtering into SQL |
| 2. **Use built-ins and idioms** | 2–10× | Comprehensions, `sum`/`min`/`sorted` with keys, `str.join`, `collections.Counter`/`defaultdict`/`deque`, `itertools` |
| 3. **Faster libraries** | 2–10× | `orjson`/`msgspec` for JSON, Pydantic v2 (Rust core), `re` patterns precompiled, `regex`, `polars` |
| 4. **Vectorise** | 10–100× | NumPy/Polars/pandas operations on whole arrays instead of Python loops ([[02 - NumPy for Engineers]]) |
| 5. **Cache** | Varies | `functools.cache` for pure functions, Redis for shared results; watch memory and invalidation |
| 6. **Concurrency / parallelism** | ≤ cores (CPU), more for I/O | asyncio/threads for I/O; process pools for CPU ([[04 - Python Threads Processes and Executors]]) |
| 7. **Compile the hot path** | 10–100× | Cython, mypyc, **Rust via PyO3 + maturin**, Numba (numeric, JIT), C extensions |
| 8. **Different runtime** | 2–10× for pure Python | PyPy (check C-extension compatibility); free-threaded build for parallel CPU-bound threads (extra, below) |

```python
# Rung 1 + 2: from O(n·m) list scans to O(n + m)
blocked = set(blocked_ids)                                  # was: a list scanned for every order
valid = [o for o in orders if o.customer_id not in blocked]

# Rung 4: vectorised instead of a Python loop
import numpy as np
totals = prices * quantities * (1 - discounts)             # one C loop over whole arrays
```

### Common hot spots in services

| Hot spot | Fix |
|---|---|
| JSON (de)serialisation of large payloads | orjson/msgspec; Pydantic `model_validate_json` (parses in Rust) instead of `json.loads` + validate |
| ORM overhead and N+1 queries | Eager loading, bulk operations, Core/SQL for hot reads ([[05 - FastAPI Data Access with SQLAlchemy]]) |
| Logging in hot paths | Lazy `%s` formatting, level checks, no per-item debug logs |
| Regex compiled on every call (in loops) | Precompile at module level |
| Exceptions for control flow in tight loops | Check conditions instead |
| Startup / import time | `-X importtime`, lazy imports (3.15 `lazy import`) ([[01 - CPython Execution Model]]) |
| Creating clients/sessions per request | Reuse pooled clients for the app lifetime |

---

## Benchmarking Correctly

- Benchmark on the **same Python version** as production (3.11+ specialisation changes results), with realistic input sizes.
- Use **pyperf** or `timeit` with repeats; report distributions, not single runs; watch for CPU frequency scaling and noisy neighbours.
- Compare end-to-end (requests/sec, p99 latency with a load tool like locust/k6) after micro-optimisations: a 5× faster function that's 2% of request time changes nothing.

---

## Compiled Extensions in Brief

| Tool | Approach | Pick when |
|---|---|---|
| **Cython** | Python-like language with C types → C extension; can release the GIL (`with nogil`) | Numeric loops, wrapping C libraries |
| **mypyc** | Compiles type-annotated Python modules | Existing typed pure-Python code (used by mypy, Black) |
| **Rust + PyO3 + maturin** | Write the hot path in Rust, ship wheels | Parsers, serialisation, CPU-heavy logic; memory safety (Pydantic-core, orjson, Polars, tokenizers) |
| **Numba** | JIT for NumPy-style numeric functions | Array maths that doesn't vectorise cleanly |

- Native code that **releases the GIL** also lets threads run in parallel ([[03 - The GIL and Free-Threaded Python]]).
- Cost: build tooling, wheels per platform, harder debugging. Use for measured hot paths only.

---

> [!info] Free-threaded build (extra)
> On `python3.14t`, CPU-bound pure-Python work can be parallelised with threads instead of processes, avoiding pickling and per-process memory. Single-threaded code runs a little slower than on the GIL build, and every dependency must support free-threading. Measure both builds for your workload before switching.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Optimising without profiling** | Effort on code that isn't hot | Sampling profile under realistic load first |
| **cProfile on call-heavy code** | Overhead distorts results | Sampling profilers (Tachyon, py-spy) |
| **Treating I/O waits as CPU problems** | Micro-optimising code that's waiting on DB/HTTP | Wall-clock profiles; fix queries, concurrency, timeouts |
| **Python loops over large data** | Seconds where milliseconds are possible | Vectorise; move to Polars/NumPy/SQL |
| **CPU work in async handlers** | Event loop stalls; all requests slow | Process pools or native code ([[05 - asyncio Internals]]) |
| **Micro-benchmarks only** | No end-to-end improvement | Load tests with p99 latency |
| **Unbounded caches as a fix** | Memory growth | Bounded/TTL caches |
| **Rewriting everything in Rust** | Maintenance cost for small gains | Compile only measured hot spots |

---

## Interview Lines

> [!tip] Say it like this
> - "I start with a sampling profiler under realistic load: py-spy or, on 3.15, the built-in Tachyon, which can attach to a running process with almost no overhead."
> - "Most wins come from doing less work: better data structures, fewer queries, set lookups instead of list scans; then vectorising with NumPy or Polars."
> - "Faster libraries like orjson or msgspec, and Pydantic parsing JSON directly, are cheap wins for serialisation-heavy services."
> - "If a hot path is still CPU-bound, I compile it with Cython or Rust via PyO3, which can also release the GIL."
> - "I verify improvements end to end with load tests, because a faster function that's 2% of request time doesn't matter."

## Related Notes

- [[01 - CPython Execution Model]] · [[02 - Python Memory Management and GC]] · [[03 - The GIL and Free-Threaded Python]] · [[04 - Python Threads Processes and Executors]]
- [[02 - NumPy for Engineers]] · [[08 - Python AI Performance Traps]] · [[12 - JVM Diagnostics and Profiling]] · [[Languages/README]]
