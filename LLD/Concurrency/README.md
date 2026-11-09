---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Concurrency

> [!abstract] What this is
> Threads, locks and concurrent design in Java, at the depth an SDE-2/3 LLD loop expects: write correct thread-safe code from scratch, explain *why* it's correct, and know which library tool replaces hand-written code in production.

---

## How the Folder Is Organised

| Tier | Notes | Goal |
|---|---|---|
| **1. Must be fluent** | 01–09 | Asked in almost every concurrent LLD problem |
| **2. Expected at SDE-2/3** | 10–15 | Common follow-ups and design discussions |
| **3. Know it exists** | 16 | Two-sentence answers for SDE-3 / interviewer-specific probes |
| **Practice** | 17–19 | Testing, then the classic problems coded end to end |

## Notes

| # | Note | What it covers |
|---|---|---|
| 00 | [[00 - Processes the JVM and the OS]] | The JVM as one OS process, 1:1 kernel threads, the JVM's own threads, the scheduler, blocking, safepoints, cores and pool sizing, cgroup CPU and memory limits, JVM exit |
| 01 | [[01 - Threads and Lifecycle]] | `Thread`/`Runnable`/`Callable`, states, `start` vs `run`, `join`, daemon threads, interruption |
| 02 | [[02 - Race Conditions and Shared State]] | Check-then-act, read-modify-write, why races hide, the three safety strategies |
| 03 | [[03 - Java Memory Model]] | Visibility, atomicity, ordering, happens-before, `volatile`, safe publication, DCL |
| 04 | [[04 - synchronized and Monitors]] | Intrinsic locks, what you lock on, reentrancy, `wait`/`notify` |
| 05 | [[05 - Explicit Locks]] | `ReentrantLock`, `tryLock`, `Condition`, `ReadWriteLock`, `StampedLock` |
| 06 | [[06 - Atomics and CAS]] | Atomic classes, CAS retry loops, `LongAdder` |
| 07 | [[07 - Concurrent Collections]] | `ConcurrentHashMap` atomic methods, `CopyOnWriteArrayList`, `BlockingQueue` family |
| 08 | [[08 - Executors and Thread Pools]] | `ExecutorService`, `ThreadPoolExecutor` parameters, rejection, shutdown, sizing |
| 09 | [[09 - Deadlock Livelock and Starvation]] | The four conditions, lock ordering, `tryLock`, diagnosis |
| 10 | [[10 - Synchronizers]] | `CountDownLatch`, `CyclicBarrier`, `Semaphore`, `Phaser`, `Exchanger` |
| 11 | [[11 - CompletableFuture]] | Composition, error handling, executors and the common-pool trap |
| 12 | [[12 - Confinement and Immutability]] | Thread confinement, `ThreadLocal` and its leak, immutable objects, snapshots |
| 13 | [[13 - Designing Thread-Safe Classes]] | Invariants and guards, lock granularity, striping, alien calls, composition |
| 14 | [[14 - Fork Join and Parallel Streams]] | Work stealing, `RecursiveTask`, when parallel streams help or hurt |
| 15 | [[15 - Virtual Threads]] | Java 21 virtual threads, pinning, structured concurrency |
| 16 | [[16 - Advanced Topics]] | Lock-free structures, ABA, false sharing, memory barriers, actors |
| 17 | [[17 - Testing Concurrent Code]] | Why tests miss races, latches, stress loops, timeouts, jcstress |
| 18 | [[18 - Classic Problems - Coordination]] | Producer-consumer three ways, print in turns, reader-writer, dining philosophers |
| 19 | [[19 - Classic Problems - Components]] | Thread-safe LRU, token bucket, scheduler, connection pool, thread pool |

---

## Suggested Order

0. **00** first: what runs underneath (process, kernel threads, scheduler, container limits).
1. **01–09** in order, ending with producer-consumer written three ways in [[18 - Classic Problems - Coordination]]. That one exercise touches nearly everything in Tier 1.
2. **12 and 13**: the design reasoning that turns tools into correct classes.
3. **10, 11, 14, 15**: the rest of Tier 2.
4. **19**: code each component from memory, then answer its follow-ups out loud.
5. **16 and 17** when the rest feels solid.

## Related Notes

- [[LLD/Foundations/README]] — exceptions, immutability, modelling and the other craft topics
- [[LLD/README]]
