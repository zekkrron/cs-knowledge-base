---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Diagnostics pprof and Tracing

> [!abstract] What this is
> The Go diagnostics toolkit and how to use it in production: **pprof** profiles (CPU, heap with `inuse` vs `alloc`, goroutine, **block**, **mutex**, threadcreate, and the **`goroutineleak`** profile GA in **1.27**) exposed via `net/http/pprof`, reading flame graphs (`go tool pprof -http`, flame graph default since 1.26), the **execution tracer** and the **flight recorder** (1.25), `runtime/metrics`, `GODEBUG` traces (`gctrace`, `schedtrace`), goroutine dumps, the race detector, delve, and continuous profiling. The decision this helps with: which tool answers which question, and how to expose them safely. JVM equivalent: [[12 - JVM Diagnostics and Profiling]].

---

## Why It Matters

- Go's built-in profilers are low-overhead and production-safe, which makes "profile the live service" the normal first step for CPU, memory, latency and leak problems.
- Exposing `/debug/pprof` publicly is a real security issue (memory contents, DoS via expensive profiles).

---

## Symptom → Tool

| Symptom | Tool | Look for |
|---|---|---|
| High CPU | **CPU profile** (30 s) | Wide frames in the flame graph: JSON, regex, allocation, GC (`runtime.gcBgMarkWorker`, `mallocgc`) |
| Memory grows / OOM | **Heap profile `inuse_space`**, two snapshots compared (`-diff_base`) | What holds memory now |
| High GC CPU | **Heap profile `alloc_space` / `alloc_objects`** | Who allocates the most (GC cost driver) ([[03 - Go Garbage Collector]]) |
| Goroutine count climbing | **Goroutine profile** (`debug=2` for full stacks), **`goroutineleak` profile** (1.27) | Thousands blocked at the same line; goroutines that can never wake |
| Latency high, CPU low | **Block** and **mutex** profiles, execution trace | Channel/lock waits, contention, scheduler delays, syscalls |
| Latency spikes, rare events | **Flight recorder** trace snapshot | Scheduling, GC, syscalls around the spike |
| Thread count high | threadcreate profile, `/sched/threads` metric | Blocking syscalls/cgo ([[01 - Go Runtime and the GMP Scheduler]]) |
| Process stuck | `SIGQUIT` (`kill -QUIT`) → all goroutine stacks on stderr | Deadlocks, everything waiting on one thing |

---

## Exposing pprof

```go
import _ "net/http/pprof"                        // registers /debug/pprof/* on http.DefaultServeMux

func main() {
    go func() {
        // Separate, internal-only listener: never on the public server
        log.Println(http.ListenAndServe("127.0.0.1:6060", nil))
    }()
    runtime.SetBlockProfileRate(10_000)           // sample blocking events ≥ ~10µs (off by default)
    runtime.SetMutexProfileFraction(100)          // sample 1/100 contention events (off by default)
    ...
}
```

```bash
go tool pprof -http=:8081 http://localhost:6060/debug/pprof/profile?seconds=30      # CPU, opens flame graph UI
go tool pprof -http=:8081 http://localhost:6060/debug/pprof/heap                      # inuse_space by default
go tool pprof -sample_index=alloc_space http://localhost:6060/debug/pprof/heap        # allocation hotspots
curl -s 'localhost:6060/debug/pprof/goroutine?debug=2' > goroutines.txt               # full stacks
go tool pprof http://localhost:6060/debug/pprof/goroutineleak                         # 1.27
go tool pprof -diff_base=heap1.pb.gz heap2.pb.gz                                      # what grew
```

- Importing `net/http/pprof` registers on the **default mux**: if your public server uses `http.DefaultServeMux`, the profiles are public. Use your own mux for the app and serve pprof on a localhost/internal port (reach it via `kubectl port-forward`), or behind auth.
- In Gin, mount pprof handlers on an internal router group with auth (`gin-contrib/pprof`) ([[06 - Gin Production]]).

### Reading profiles

- **Flame graph**: width = share of samples (CPU time or bytes), stacks grow upward/downward from the root; look for wide plateaus. Since 1.26 `pprof -http` opens the flame graph by default.
- `top -cum` sorts by cumulative time (including callees); `list FuncName` shows per-line costs; `peek` and `-focus`/`-ignore` filter.
- CPU profiles sample at 100 Hz via signals: low overhead (a few %), fine in production for 30 s windows.
- Heap profiles sample allocations (every 512 KB by default, `runtime.MemProfileRate`); `inuse_*` reflects the **last completed GC**.

---

## Execution Tracer and Flight Recorder

```go
fr := trace.NewFlightRecorder(trace.FlightRecorderConfig{MinAge: 10 * time.Second})  // 1.25
fr.Start()
// ... when a request exceeds its SLO:
if elapsed > slo {
    f, _ := os.Create("slow-request.trace")
    fr.WriteTo(f)                                   // last ~10 s of execution trace
    f.Close()
}
```

- The **execution tracer** records scheduling events per goroutine: when it ran, blocked, on what (channel, mutex, syscall, network), GC phases, processor utilisation. `go tool trace file.trace` opens a timeline UI.
- Answers "**why** was this request slow while the CPU was idle?": waiting for a P, blocked on a lock, stalled by mark assist, a syscall.
- Full traces are large; the **flight recorder** (1.25) keeps the last few seconds in a ring buffer so you can snapshot on demand when an anomaly happens.
- Tracing overhead dropped substantially in 1.21+ (frame-pointer unwinding); still enable selectively.
- `trace.WithRegion` / `trace.NewTask` annotate application operations in the trace; **pprof labels** (`pprof.Do(ctx, pprof.Labels("route", r))`) tag CPU samples (and since 1.27, labels appear in traceback headers).

---

## Metrics and GODEBUG

| Source | What |
|---|---|
| **`runtime/metrics`** | Stable, documented metric names: heap (`/gc/heap/live:bytes`), GC cycles and CPU (`/cpu/classes/gc/...`), goroutines (`/sched/goroutines:goroutines`, per-state counts in 1.26), threads, scheduler latency histogram (`/sched/latencies:seconds`), mutex wait (`/sync/mutex/wait/total:seconds`) |
| Prometheus `client_golang` collectors | Exposes Go runtime metrics (`go_goroutines`, `go_gc_duration_seconds`, ...) ([[06 - Gin Production]]) |
| `GODEBUG=gctrace=1` | One line per GC cycle ([[03 - Go Garbage Collector]]) |
| `GODEBUG=schedtrace=1000` | Scheduler state every second |
| `GOTRACEBACK=all` / `crash` | More goroutines in crash tracebacks; core dumps |

- Alert on: goroutine count trend, GC CPU fraction, heap live trend, scheduler latency p99, thread count.

---

## Other Tools

| Tool | Use |
|---|---|
| **Race detector** (`-race`) | Data races in tests/staging ([[07 - Go Concurrency Primitives]]) |
| **Delve** (`dlv`) | Debugger: breakpoints, goroutine inspection, core dumps (`dlv core`); remote attach only in non-prod |
| **Continuous profiling** (Pyroscope, Parca, Datadog, Grafana) | Always-on pprof collection; compare deploys; feed PGO ([[10 - Go Modules Build and Tooling]]) |
| **OpenTelemetry** | Distributed traces across services (`otelhttp`, `otelgin`) ([[06 - Gin Production]]) |
| `go build -gcflags=-m` | Escape analysis decisions ([[02 - Go Memory and Escape Analysis]]) |

---

## Playbooks

### Memory growth

1. Check `runtime/metrics`: is it **heap live**, goroutine count (stacks), or non-Go memory (cgo, mmap)?
2. Take two heap profiles minutes apart; `-diff_base` shows what grew (`inuse_space`).
3. If goroutines grow: goroutine profile grouped by stack, plus the `goroutineleak` profile.
4. Typical causes: unbounded caches/maps, sub-slices pinning big buffers, leaked goroutines holding data, unclosed response bodies ([[05 - Go Slices Maps and Strings Internals]]).

### Latency with low CPU

1. Block and mutex profiles (enable sampling first).
2. Execution trace / flight recorder snapshot of a slow window.
3. Check downstream timeouts, connection pool limits (`database/sql` `WaitCount`/`WaitDuration` stats), lock contention ([[05 - Go Data Access]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **pprof on the public mux** | Memory contents and DoS exposed | Internal listener or authenticated routes |
| **Block/mutex profiles empty** | Sampling disabled by default | `SetBlockProfileRate`, `SetMutexProfileFraction` |
| **Reading `inuse` when the question is GC cost** | Optimising the wrong thing | `alloc_space`/`alloc_objects` for GC pressure |
| **One heap snapshot** | Can't distinguish growth from steady state | Diff two snapshots |
| **Full tracing always on** | Overhead, huge files | Flight recorder, targeted windows |
| **Stripped binaries without symbols** | Unreadable profiles/crashes | Keep symbols, or store unstripped builds |
| **No goroutine metric** | Leaks found only at OOM | Export and alert on goroutine count |

---

## Interview Lines

> [!tip] Say it like this
> - "Go's pprof profiles are cheap enough to take from production: CPU for hot code, heap `inuse` for what holds memory, `alloc` for what drives GC, goroutine for leaks, block and mutex for waiting."
> - "pprof goes on an internal listener, never on the public mux, because it exposes memory contents."
> - "When latency is high but CPU is low, I use the execution tracer; since 1.25 the flight recorder keeps the last seconds so I can snapshot exactly when a slow request happens."
> - "For leaks I watch goroutine counts and use the `goroutineleak` profile, GA in 1.27, which finds goroutines blocked on things nothing can ever unblock."
> - "Continuous profiling also feeds PGO builds with real production profiles."

## Related Notes

- [[01 - Go Runtime and the GMP Scheduler]] · [[03 - Go Garbage Collector]] · [[08 - Go Concurrency Patterns and Context]] · [[13 - Go in Containers and Production]] · [[06 - Gin Production]]
- [[12 - JVM Diagnostics and Profiling]] · [[13 - Python Performance and Profiling]] · [[Languages/README]]
