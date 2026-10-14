---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Python Threads Processes and Executors

> [!abstract] What this is
> The concurrency tools in the standard library for the normal (GIL) build: **`threading`** and its synchronisation primitives, **`multiprocessing`** (start methods **fork / spawn / forkserver**, pickling, shared memory), and **`concurrent.futures`** executors (`ThreadPoolExecutor`, `ProcessPoolExecutor`, and 3.14's `InterpreterPoolExecutor`). Covers when to use each, how they interact with asyncio, and the production traps: fork after threads, pickling failures, per-process memory, zombie workers. The decision this helps with: picking threads vs processes vs async for a given job and configuring them safely.

---

## Why It Matters

- Picking the wrong tool is the most common Python concurrency mistake: threads for CPU work (no speedup under the GIL), processes for tiny tasks (pickling dominates), `fork` in a process with threads (deadlocks).
- Python 3.14 changed the default `multiprocessing` start method on Linux from `fork` to `forkserver`, which breaks code that relied on inherited globals.

---

## Choosing

| Workload                                                      | Tool                                        | Why                                                                    |
| ------------------------------------------------------------- | ------------------------------------------- | ---------------------------------------------------------------------- |
| Many blocking I/O calls (HTTP, DB, files) with sync libraries | **`ThreadPoolExecutor`**                    | GIL released while waiting ([[03 - The GIL and Free-Threaded Python]]) |
| Many I/O calls with async libraries                           | **asyncio**                                 | One thread, thousands of concurrent waits ([[05 - asyncio Internals]]) |
| CPU-bound pure Python                                         | **`ProcessPoolExecutor`**                   | One GIL per process: real parallelism                                  |
| CPU-bound in NumPy/native code that releases the GIL          | **Threads**                                 | Parallel without pickling                                              |
| CPU-bound, want one process, isolated state                   | `InterpreterPoolExecutor` (3.14)            | One GIL per interpreter; young ecosystem                               |
| Sync blocking call from async code                            | **`asyncio.to_thread`** / `run_in_executor` | Keeps the event loop free                                              |
| Durable background jobs                                       | Task queue (Celery, arq, Dramatiq)          | Survives restarts; retries ([[08 - FastAPI Background Work]])          |

---

## threading

```python
import threading, queue

jobs: queue.Queue[str] = queue.Queue(maxsize=1000)    # bounded: back-pressure on producers

def worker():
    while (url := jobs.get()) is not None:
        try:
            fetch(url)
        finally:
            jobs.task_done()

threads = [threading.Thread(target=worker, name=f"fetch-{i}", daemon=True) for i in range(16)]
for t in threads: t.start()
```

| Primitive | Use |
|---|---|
| `Lock` / `RLock` | Mutual exclusion (`RLock` re-entrant for the same thread) |
| `Condition` | Wait for a state change with a lock |
| `Semaphore` / `BoundedSemaphore` | Limit concurrent access (e.g. 10 calls to an API) |
| `Event` | One-shot or resettable signal (shutdown flags) |
| `Barrier` | Phase synchronisation |
| **`queue.Queue`** | Thread-safe producer/consumer handoff; bounded for back-pressure |
| `threading.local()` / `contextvars` | Per-thread state / per-context state (contextvars also work with asyncio) |

- **Daemon threads** are killed abruptly at interpreter exit (no `finally`, no flush); use non-daemon threads plus a shutdown signal for anything that must finish.
- Threads can't be killed from outside; design cancellation with events or sentinels.
- Exceptions in a thread are printed via `threading.excepthook` and otherwise lost; executors capture them in futures instead.
- Same concepts as Java's primitives ([[LLD/Concurrency/10 - Synchronizers]]), with the GIL limiting CPU parallelism.

---

## multiprocessing

### Start methods

| Method | How the child starts | Default on | Pros | Cons |
|---|---|---|---|---|
| **`fork`** | `fork()` copies the parent process | **No platform since 3.14** (was Linux default) | Fast; inherits loaded modules and data (copy-on-write) | **Unsafe with threads**: locks held by other threads are copied locked → deadlocks; inherits sockets, file descriptors, random state |
| **`spawn`** | Fresh interpreter, re-imports the main module, unpickles the target | Windows, macOS (since 3.8) | Clean state, safe | Slow start; everything passed must be picklable; `if __name__ == "__main__":` guard required |
| **`forkserver`** | A clean single-threaded server process forks children on request | **Linux since 3.14** | Safer than fork, faster than spawn | Arguments pickled like spawn |

- Since 3.12, forking a process that has **multiple threads** emits a `DeprecationWarning`: it can deadlock in the child (logging locks, malloc locks, thread pools of libraries like gRPC or CUDA runtimes).
- Code that relied on `fork` inheriting module globals now needs to pass state explicitly or request `fork` deliberately: `multiprocessing.get_context("fork")`.
- **Never fork after initialising CUDA**, gRPC channels, or database connection pools; create them in the child ([[08 - Python AI Performance Traps]]).

### Passing data

- Arguments and results are **pickled** and sent through pipes. Lambdas, local functions, open sockets, locks and many C objects aren't picklable → `PicklingError` / `AttributeError: Can't pickle local object`.
- Pickling large data each call costs more than the work for small tasks: batch work (`chunksize` in `map`), or send references (file paths, object-store keys) instead of payloads.
- Sharing without copying: `multiprocessing.shared_memory` (raw buffers, e.g. NumPy arrays backed by shared memory), memory-mapped files, or `Manager` proxies (convenient, slow: every access is IPC).

---

## concurrent.futures

```python
from concurrent.futures import ProcessPoolExecutor, as_completed

def score(batch: list[Record]) -> list[Score]: ...

if __name__ == "__main__":                      # required with spawn/forkserver
    with ProcessPoolExecutor(max_workers=os.process_cpu_count(),   # 3.13+: respects CPU affinity
                             max_tasks_per_child=500) as pool:     # recycle workers (3.11+)
        futures = [pool.submit(score, b) for b in batches(records, 1_000)]
        for f in as_completed(futures):
            try:
                save(f.result(timeout=60))
            except Exception:
                log.exception("batch failed")
```

| API | Behaviour |
|---|---|
| `submit(fn, *args)` → `Future` | `result(timeout)`, `exception()`, `cancel()` (only if not started), callbacks |
| `map(fn, iterable, chunksize=n)` | Ordered results; **raises the first exception when iterating to it**; consumes the whole input up front (3.14 adds a `buffersize` option to bound it) |
| `as_completed(futures)` | Results in completion order |
| `wait(futures, return_when=FIRST_EXCEPTION)` | Coordinated waiting |
| `shutdown(wait=True, cancel_futures=True)` | Stop accepting work, optionally cancel queued work |

- **`ThreadPoolExecutor`** default `max_workers` = `min(32, cpu_count + 4)`; its queue is **unbounded**, so a fast producer can queue millions of tasks: bound with a semaphore or batch.
- **`ProcessPoolExecutor`**: a worker that dies (segfault, OOM kill) breaks the pool (`BrokenProcessPool`); recreate it. `max_tasks_per_child` recycles workers to contain leaks and fragmentation ([[02 - Python Memory Management and GC]]).
- **`InterpreterPoolExecutor`** (3.14): runs tasks in subinterpreters, each with its own GIL, in one process; arguments are shared or copied like with processes; extension modules must support subinterpreters.
- Container CPU limits: `os.cpu_count()` reports host CPUs; **`os.process_cpu_count()`** (3.13) respects affinity, but neither reads cgroup quotas. Set worker counts explicitly from the container's CPU limit.

### With asyncio

```python
result = await asyncio.to_thread(blocking_sdk_call, arg)                     # default thread pool
result = await loop.run_in_executor(process_pool, cpu_heavy, payload)       # CPU work off the loop
```

- Keep blocking or CPU work **off the event loop**; the default executor is a thread pool shared by `to_thread` calls ([[05 - asyncio Internals]], [[02 - FastAPI Request Lifecycle and Sync vs Async]]).

---

## Free-Threaded Build (extra)

> [!info] Only on `python3.14t`
> On the free-threaded build, `ThreadPoolExecutor` runs CPU-bound Python in parallel without pickling, if dependencies support it ([[03 - The GIL and Free-Threaded Python]]). On the normal build, use processes for CPU work. Thread-safety bugs that the GIL hid show up more often: lock shared state.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Threads for CPU-bound Python** | No speedup | Process pool / native code |
| **`fork` with threads running** | Child deadlocks on inherited locks | `forkserver`/`spawn`; create threads/clients after forking |
| **Code relying on `fork` globals on 3.14+** | Missing state in children | Pass state explicitly or choose `fork` deliberately |
| **Missing `__main__` guard** | Recursive process spawning / `RuntimeError` | `if __name__ == "__main__":` |
| **Unpicklable arguments** | `PicklingError` | Module-level functions; send IDs/paths, not objects |
| **Tiny tasks to a process pool** | IPC overhead > work | Batch with `chunksize` |
| **Unbounded executor queue** | Memory growth; latency | Bounded queues/semaphores |
| **Worker crash** | `BrokenProcessPool`; lost work | Recreate pool; idempotent tasks; `max_tasks_per_child` |
| **Daemon threads doing important work** | Killed at exit without cleanup | Non-daemon + shutdown event |
| **Worker count from `os.cpu_count()` in containers** | Oversubscription and throttling | Configure from the CPU limit |

---

## Interview Lines

> [!tip] Say it like this
> - "Threads for blocking I/O, asyncio for lots of concurrent I/O with async libraries, processes for CPU-bound Python; native code that releases the GIL can use threads."
> - "Python 3.14 stopped defaulting to `fork` on Linux; it uses `forkserver`, because forking a process with threads can deadlock the child."
> - "Process pools pickle every argument and result, so I batch work and pass references for large data, and use `max_tasks_per_child` to recycle workers."
> - "Executor queues are unbounded, so I bound submissions; `map` re-raises the first exception, while `as_completed` lets me handle failures per task."
> - "From async code I push blocking calls to `asyncio.to_thread` and CPU work to a process pool, so the event loop never blocks."

## Related Notes

- [[03 - The GIL and Free-Threaded Python]] · [[05 - asyncio Internals]] · [[06 - WSGI vs ASGI and Python Servers]] · [[02 - Python Memory Management and GC]]
- [[LLD/Concurrency/08 - Executors and Thread Pools]] · [[01 - Concurrency Models Compared]] · [[Languages/README]]
