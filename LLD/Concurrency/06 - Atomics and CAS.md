---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Atomics and CAS

> [!abstract] What this is
> Lock-free updates to a **single variable** using the CPU's compare-and-swap instruction: `AtomicInteger`, `AtomicLong`, `AtomicReference`, the CAS retry loop, `LongAdder` for very hot counters, and the ABA problem. Use atomics when the shared state is one value; use a lock when an invariant spans several.

---

## Compare-and-Swap

- **CAS(address, expected, new)**: "if the value is still `expected`, set it to `new` and tell me I won; otherwise tell me I lost". The check and the write are **one hardware instruction**, so nothing can interleave.
- It's **optimistic**: no thread ever blocks. Losers just read the new value and **retry**.
- This is the same idea as the optimistic `version` column in databases (`UPDATE … WHERE version = 3`), at the level of one memory word.

```java
AtomicInteger counter = new AtomicInteger();
counter.incrementAndGet();          // atomic ++, returns the new value
counter.getAndAdd(5);               // atomic += 5, returns the old value
counter.compareAndSet(10, 11);      // true only if it was 10

// Any update rule, written as a CAS retry loop:
int prev, next;
do {
    prev = counter.get();
    next = Math.max(prev, observed);        // compute from a snapshot
} while (!counter.compareAndSet(prev, next)); // retry if someone changed it meanwhile

// The same loop, done for you (the function may run more than once, so keep it side-effect free):
counter.updateAndGet(v -> Math.max(v, observed));
counter.accumulateAndGet(observed, Math::max);
```

- Atomics also give **visibility**: reads and writes behave like `volatile` ([[03 - Java Memory Model]]).

---

## The Atomic Classes

| Class | Use |
|---|---|
| `AtomicInteger`, `AtomicLong` | Counters, sequence numbers, ids |
| `AtomicBoolean` | One-time flags: "has shutdown started?" via `compareAndSet(false, true)` |
| `AtomicReference<V>` | Swap a whole immutable object atomically (config snapshot, state machine state) |
| `AtomicIntegerArray`, `AtomicLongArray` | Per-slot atomic updates |
| `AtomicStampedReference` | Reference + version stamp (fixes ABA) |
| `LongAdder`, `LongAccumulator` | Very hot counters / accumulators |

### `AtomicReference` for immutable state

- When state is several fields, keep them in an **immutable object** and swap the whole object with CAS. Readers always see a consistent snapshot without any lock.

```java
record Stats(long count, long sum) {
    Stats add(long v) { return new Stats(count + 1, sum + v); }
}

class Metrics {
    private final AtomicReference<Stats> stats = new AtomicReference<>(new Stats(0, 0));
    void record(long v) { stats.updateAndGet(s -> s.add(v)); }  // CAS loop on the whole snapshot
    double mean() { Stats s = stats.get(); return s.count() == 0 ? 0 : (double) s.sum() / s.count(); }
}
```

- This scales well when updates are short. Under heavy contention, many CAS attempts fail and retry, and a lock may actually be faster.

---

## `LongAdder` for Hot Counters

- With many threads hammering one `AtomicLong`, every CAS fights over the **same cache line**: lots of retries, and the line bounces between cores.
- **`LongAdder`** keeps a **set of cells**; under contention each thread adds to its own cell, and `sum()` adds the cells up.
- It's the in-memory version of the **sharded counter** from HLD ([[03 - Concurrency Control]]): cheap writes, slightly costlier reads.

| | `AtomicLong` | `LongAdder` |
|---|---|---|
| Write under contention | Slows down (CAS retries) | Scales well |
| Read | One value | Sums all cells; not an atomic snapshot while writes continue |
| Supports CAS / `getAndIncrement` | Yes | No |
| Pick when | You need the exact current value or CAS (ids, sequence numbers) | Statistics, metrics, hit counters |

- `ConcurrentHashMap<String, LongAdder>` with `computeIfAbsent(k, x -> new LongAdder()).increment()` is the standard thread-safe frequency counter.

---

## The ABA Problem

- CAS only checks that the value **equals** the expected one, not that it **never changed**.
- Thread 1 reads `A`, gets paused. Thread 2 changes `A → B → A`. Thread 1's CAS succeeds, even though the world changed underneath it.
- Usually harmless for counters. It matters in **lock-free data structures** that reuse nodes: a stack's head pointer is `A` again but `A.next` is now different, and the CAS corrupts the structure.
- Fixes: `AtomicStampedReference` (value + version number, CAS on both), or never reusing nodes (in Java the GC already prevents most cases, since a node can't be recycled while you still reference it).

---

## Atomics vs Locks

| Situation | Use |
|---|---|
| One variable, simple update | Atomic |
| Several variables that must change together | A lock, or one immutable object in an `AtomicReference` |
| Very hot counter | `LongAdder` |
| Need to wait until something becomes true | Lock + `Condition` or a synchronizer; atomics can't block |
| Check-then-act on a map entry | `ConcurrentHashMap` atomic methods, not separate atomics |

> [!warning] Two atomics are not atomic together
> `balance.addAndGet(-x)` followed by `txCount.incrementAndGet()` is two separate atomic steps; another thread can observe the state between them. If the invariant ties them together, use one lock or one immutable snapshot.

---

## Interview Lines

> [!tip] Say it like this
> - "CAS is optimistic concurrency on one memory word: compare, swap, retry on failure. Nobody blocks."
> - "For a hot counter I'd use `LongAdder`; it's a sharded counter inside the JVM."
> - "When the state is several fields, I keep it in an immutable object and swap it with an `AtomicReference`."
> - "Two atomics updated one after another aren't atomic together; an invariant across them needs a lock."
> - "ABA matters for lock-free structures; `AtomicStampedReference` adds a version to fix it."

## Related Notes

- [[02 - Race Conditions and Shared State]] · [[07 - Concurrent Collections]] · [[16 - Advanced Topics]]
- [[LLD/Concurrency/README]]
