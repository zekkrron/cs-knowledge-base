---
tags: [languages/java, status/draft]
created: 2026-10-14
---
# JVM Diagnostics and Profiling

> [!abstract] What this is
> The toolkit and the playbooks for a misbehaving JVM in production: **thread dumps** (hangs, deadlocks, pool exhaustion), **heap dumps** (leaks, OOM), **JDK Flight Recorder** (always-on, low-overhead recording), **async-profiler** (CPU, allocation, wall-clock and lock flame graphs), `jcmd` as the entry point, **Native Memory Tracking**, and GC/safepoint logs. The decision this helps with: which tool answers which question, and the order to run them in during an incident.

---

## Why It Matters

- "The service is slow / stuck / using 100% CPU / getting OOMKilled" is a top senior interview scenario, and the answer is a procedure, not a guess.
- Most of the right flags must be set **before** the incident (heap dump on OOM, GC logs, JFR). Afterwards it's too late.

---

## Symptom → Tool

| Symptom | First tool | What you look for |
|---|---|---|
| Requests hang, throughput drops to zero | **Thread dump** (3 dumps, 5–10 s apart) | Threads `BLOCKED` on the same lock, all pool threads `WAITING` on a connection pool or socket read, deadlocks |
| High CPU | **async-profiler CPU** or JFR; `top -H` + thread dump | Hot methods; GC threads; a spinning loop |
| Latency spikes | **GC logs**, JFR (GC, safepoints, lock contention) | Long pauses, Full GCs, safepoint time, contended monitors |
| Heap grows until OOM | **Heap dump** + Eclipse MAT | Dominator tree: who retains the most memory |
| Pod OOMKilled, heap looks fine | **NMT**, `pmap`, container metrics | Metaspace, threads, direct buffers, native allocations ([[03 - JVM Memory Layout]]) |
| Allocation-heavy, frequent GCs | **async-profiler alloc** / JFR allocation events | Which code paths allocate the most |
| Slow but CPU idle | **Wall-clock profile**, thread dump | Time spent waiting: locks, I/O, pools |

---

## jcmd: the Entry Point

`jcmd` talks to a running JVM (same user, same container/namespace). It replaces most of `jstack`, `jmap`, `jinfo`.

```bash
jcmd                                   # list JVMs
jcmd <pid> help                        # available commands
jcmd <pid> Thread.print -l             # thread dump with lock info
jcmd <pid> Thread.dump_to_file -format=json /tmp/threads.json   # includes virtual threads (21+)
jcmd <pid> GC.heap_dump /tmp/heap.hprof                         # heap dump (live objects; triggers full GC)
jcmd <pid> GC.class_histogram | head -30                        # quick "what's on the heap"
jcmd <pid> VM.native_memory summary                             # needs -XX:NativeMemoryTracking=summary
jcmd <pid> VM.flags / VM.system_properties / VM.uptime
jcmd <pid> JFR.start name=incident duration=120s filename=/tmp/rec.jfr
```

- In containers: run `jcmd` inside the container (`kubectl exec`), or attach via an ephemeral debug container sharing the process namespace. Minimal images (distroless, jlink without `jdk.jcmd`) may lack the tools; plan for it.

---

## Thread Dumps

```
"http-nio-8080-exec-17" #63 daemon prio=5 os_prio=0 tid=0x00007f... nid=0x1a2b waiting on condition
   java.lang.Thread.State: TIMED_WAITING (parking)
        at jdk.internal.misc.Unsafe.park(Native Method)
        at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:269)
        at com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:151)
        at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:181)
        ...
```

| State | Meaning | Typical cause |
|---|---|---|
| `RUNNABLE` | Running or in native code (including blocking socket reads!) | Hot loops, or I/O without timeout (shows as RUNNABLE in `socketRead0`) |
| `BLOCKED` | Waiting to enter a `synchronized` monitor | Lock contention; look for the thread **holding** the monitor |
| `WAITING` / `TIMED_WAITING` | `park`, `wait`, `sleep`, `join` | Idle pool threads (normal), or waiting on a pool/queue/future |

- Take **several dumps** a few seconds apart: a thread in the same frame every time is stuck; one that moves is busy.
- The example above is the classic **connection-pool exhaustion** signature: dozens of request threads parked in `HikariPool.getConnection` ([[08 - Spring Database Connections and Migrations]]).
- **Deadlocks**: the dump ends with "Found one Java-level deadlock" listing the cycle (monitors and `ReentrantLock`s via `-l`) ([[LLD/Concurrency/09 - Deadlock Livelock and Starvation]]).
- **High CPU recipe**: `top -H -p <pid>` finds the hottest native thread id → convert to hex → match `nid=0x...` in the thread dump. A profiler is usually faster.
- Name your threads (pool thread factories); "pool-7-thread-3" tells you nothing.

---

## Heap Dumps

- Capture automatically: **`-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps`** (a persistent volume in Kubernetes, or the dump dies with the pod). Consider **`-XX:+ExitOnOutOfMemoryError`** so the orchestrator restarts a broken JVM instead of leaving it half-alive.
- Manual: `jcmd <pid> GC.heap_dump`. It **pauses** the JVM and writes a file roughly the size of the live heap; take it from a canary or a pod out of rotation.
- Dumps contain **everything in memory**: passwords, tokens, personal data. Handle them as sensitive.

### Reading one in Eclipse MAT

1. **Leak Suspects** report for a first guess.
2. **Dominator tree**: objects sorted by **retained size** (memory freed if they were collected). A single `HashMap` or cache retaining 70% of the heap is the usual answer.
3. **Path to GC roots** (excluding weak/soft refs) from the suspect: shows *why* it's alive (a static field, a `ThreadLocal`, a listener list).
4. Compare two dumps taken minutes apart to confirm growth.

| Common leak | Signature in MAT |
|---|---|
| Unbounded cache / static map | One `HashMap`/`ConcurrentHashMap` dominating |
| `ThreadLocal` in pooled threads | Retained via `Thread` → `threadLocals` |
| Listeners/callbacks never removed | Large `ArrayList`/`CopyOnWriteArrayList` of handlers |
| Class loader leak | Many duplicate `Class` instances, old loader retained ([[01 - JVM Architecture and Class Loading]]) |
| Unclosed resources | Many `Socket`/`Inflater`/buffer objects |

---

## JDK Flight Recorder (JFR)

- A recorder built into the JVM: GC, allocation, locks, threads, I/O, exceptions, safepoints, class loading, virtual-thread pinning, plus custom application events. Overhead about **1%** with the default profile, so it can run **continuously in production**.
- Free and open since 11.

```bash
# Always-on, keep the last 6 hours / 500 MB, dump on demand or on exit
-XX:StartFlightRecording=name=continuous,maxage=6h,maxsize=500m,dumponexit=true,filename=/dumps/
jcmd <pid> JFR.dump name=continuous filename=/tmp/incident.jfr
jfr print --events jdk.GarbageCollection /tmp/incident.jfr | head
```

- Analyse in **JDK Mission Control** (JMC): automated analysis page, hot methods, allocation pressure, lock instances, GC pauses, I/O.
- **CPU-time profiling** on Linux (JEP 509, experimental in **25**): samples by actual CPU time instead of fixed intervals, more accurate CPU attribution.
- **JFR redaction** (27): sensitive environment variables, system properties and arguments are scrubbed from recordings.
- Custom events: extend `jdk.jfr.Event` to record business-level timing (e.g. "order placed") with near-zero cost when disabled.

---

## async-profiler

- Low-overhead sampling profiler using `perf_events` and HotSpot internals. Avoids **safepoint bias** (classic JVM profilers sample only at safepoints, misattributing time).
- Modes: **cpu**, **alloc** (allocation sites), **wall** (all threads, including waiting: find where time goes when CPU is idle), **lock** (contended locks), `itimer` when perf events are restricted (containers).
- Output: **flame graphs** (width = share of samples, read bottom-up for call stacks), JFR format, tree views.

```bash
asprof -d 30 -e cpu   -f /tmp/cpu.html   <pid>
asprof -d 30 -e alloc -f /tmp/alloc.html <pid>
asprof -d 30 -e wall -t -f /tmp/wall.html <pid>   # -t: per thread
```

- Continuous profiling platforms (Pyroscope, Datadog, Grafana) run it or JFR always-on and keep history for comparison across deploys.

---

## Other Signals

| Need | Tool / flag |
|---|---|
| GC behaviour | `-Xlog:gc*` to a rotated file ([[04 - Java Garbage Collectors]]) |
| Safepoint pauses and time-to-safepoint | `-Xlog:safepoint` |
| Native memory breakdown | `-XX:NativeMemoryTracking=summary` + `jcmd VM.native_memory summary` (baseline + `summary.diff`) |
| Live GC/heap stats | `jstat -gcutil <pid> 1s` |
| JMX metrics | Micrometer / Actuator → Prometheus ([[12 - Spring Observability]]) |
| Production debugging without restart | Arthas or similar (trace/watch a method); use carefully |
| Remote debugging | `-agentlib:jdwp` only in non-production; never expose the port |

---

## Incident Playbooks

### Service hung

1. Capture 3 thread dumps 5–10 s apart (`jcmd Thread.print -l`, or JSON dump for virtual threads).
2. Group threads by top frame. Many in the same place = the bottleneck (pool getConnection, socket read, a lock).
3. If on a lock: find the owner and what *it* waits on. If on a pool: check pool size vs concurrency and slow queries. If in a socket read: missing timeout ([[11 - Java IO NIO and Networking]]).
4. Mitigate (restart, shed load), then fix the root cause.

### High CPU

1. Is it GC? Check GC logs / `jstat` and GC thread CPU. If yes, it's a memory problem.
2. If not, CPU flame graph for 30–60 s. Look for wide plateaus: regex, serialisation, logging, a spin loop, exceptions used for control flow.

### Memory growth / OOM

1. Read the OOM message: heap vs metaspace vs direct vs native ([[03 - JVM Memory Layout]]).
2. Heap: dominator tree in MAT; compare two dumps.
3. Native/RSS: NMT summary diff over time; thread count; direct buffer MXBeans; `MALLOC_ARENA_MAX`.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **No heap dump on OOM** | Evidence lost on restart | `HeapDumpOnOutOfMemoryError` + persistent `HeapDumpPath` |
| **Heap dump on a live, in-rotation pod** | Long pause, timeouts | Pull the instance from the load balancer first |
| **Single thread dump** | Can't tell stuck from busy | Three dumps, seconds apart |
| **Safepoint-biased profiler** | Wrong hot methods | async-profiler or JFR |
| **Tools missing in the image** | Can't diagnose in prod | Include `jcmd` (or use debug containers); enable JFR at start |
| **Dumps shared casually** | Leaks secrets and personal data | Treat dumps and recordings as sensitive artefacts |
| **Debug port open in prod** | Remote code execution risk | Never enable JDWP in production |

---

## Interview Lines

> [!tip] Say it like this
> - "For a hang I take three thread dumps a few seconds apart and group threads by top frame; dozens parked in `getConnection` means pool exhaustion, dozens in a socket read means a missing timeout."
> - "For a leak I read the dominator tree in a heap dump and follow the path to GC roots to see what keeps it alive."
> - "JFR runs continuously in production at around 1% overhead, so the data is already there when an incident starts."
> - "async-profiler avoids safepoint bias, and its wall-clock mode shows where time goes when the CPU is idle."
> - "If the pod is OOMKilled but the heap is fine, I use Native Memory Tracking, because the problem is outside the heap."

## Related Notes

- [[03 - JVM Memory Layout]] · [[04 - Java Garbage Collectors]] · [[13 - Java in Containers and Production Tuning]] · [[05 - Java Platform and Virtual Threads]]
- [[LLD/Concurrency/09 - Deadlock Livelock and Starvation]] · [[LLD/Foundations/10 - Logging and Observability]] · [[Languages/README]]
