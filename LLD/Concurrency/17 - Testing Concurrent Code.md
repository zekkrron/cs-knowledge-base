---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Testing Concurrent Code

> [!abstract] What this is
> Why ordinary unit tests rarely catch races, and the techniques that do help: designing for testability, forcing interleavings with latches, stress loops, timeouts, and tools. Saying "I'd argue correctness from the guards, then stress-test it" is the senior answer.

---

## Why Tests Miss Races

- A race only fails under a particular **interleaving**. A test run explores one interleaving out of millions, and the scheduler on your laptop tends to pick the same ones.
- Visibility bugs depend on JIT optimisation and the CPU's memory model, so code can pass on x86 and fail on ARM ([[02 - Race Conditions and Shared State]]).
- So the primary tool is **reasoning**: state the invariant, show which lock guards it ([[13 - Designing Thread-Safe Classes]]). Tests then raise confidence and catch regressions.

---

## Design for Testability

- **Separate logic from threading.** Keep the business logic in plain single-threaded classes you can test normally; make the concurrent wrapper thin.
- **Inject the executor.** Code that takes an `Executor` can run on a same-thread executor (`Runnable::run`) in tests, making it deterministic.
- **Inject the clock.** Rate limiters, caches with TTLs and schedulers should take a `Clock` (or a time supplier), so tests advance time instantly instead of sleeping ([[LLD/Foundations/09 - Testability]]).
- **Expose state for assertions carefully**, for example a package-private snapshot method.

---

## Forcing Contention

### Start-gun latch

- Release all threads at the same instant to maximise overlap ([[10 - Synchronizers]]).

```java
@Test
void counterIsThreadSafe() throws Exception {
    int threads = 16, perThread = 10_000;
    Counter counter = new Counter();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
        pool.execute(() -> {
            try {
                start.await();
                for (int i = 0; i < perThread; i++) counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
    }
    start.countDown();
    assertTrue(done.await(10, TimeUnit.SECONDS), "workers hung");   // never wait forever
    pool.shutdown();
    assertEquals(threads * perThread, counter.get());
}
```

- An unsafe `count++` usually fails this test within a few runs; a correct `AtomicInteger` always passes.

### Stress loops

- Repeat the scenario many times (`@RepeatedTest(1000)`, or a loop inside the test) with more threads than cores. Races that show up once in 500 runs become visible.

### Deterministic interleavings

- When you need a specific order ("thread A reads, then B writes, then A writes"), use latches or barriers *inside* test hooks to step each thread to a chosen point.

---

## Always Bound the Wait

- A deadlock in a test should **fail**, not hang the build forever.
    + `await(timeout)`, `future.get(timeout)`, `assertTimeoutPreemptively`, or a test-level timeout.
    + On failure, a thread dump (`jstack`, or `ThreadMXBean.findDeadlockedThreads()`) shows who holds what ([[09 - Deadlock Livelock and Starvation]]).

---

## Tools

| Tool | What it does |
|---|---|
| **jcstress** | OpenJDK harness that runs tiny concurrent snippets billions of times and reports every observed outcome; the right tool for memory-model questions |
| **Awaitility** | Readable "wait until this becomes true, up to N seconds" assertions for asynchronous code |
| **Thread dumps / JFR** | See blocked threads, lock owners, contention and pinning in a running system |
| **Static analysis** (SpotBugs, Error Prone, `@GuardedBy`) | Flags unsynchronized access to fields documented as guarded |

---

## What to Test

- **Invariants under contention:** totals add up, no seat sold twice, no item lost or duplicated in a queue.
- **Liveness:** operations finish within a timeout; no deadlock under the expected mix.
- **Shutdown:** executors drain and stop; interrupted workers exit; resources are released.
- **Failure paths:** a worker throwing doesn't hang the others (latches counted down in `finally`).

---

## Interview Lines

> [!tip] Say it like this
> - "Concurrency correctness is argued from the guards; tests add confidence, they can't prove it."
> - "I keep business logic single-threaded and testable, and inject the executor and the clock."
> - "To provoke races I release all threads with a start-gun latch and repeat the test many times."
> - "Every wait in a test has a timeout, so a deadlock fails the test instead of hanging the build."
> - "For memory-model questions the real tool is jcstress."

## Related Notes

- [[02 - Race Conditions and Shared State]] · [[10 - Synchronizers]] · [[13 - Designing Thread-Safe Classes]]
- [[LLD/Concurrency/README]]
