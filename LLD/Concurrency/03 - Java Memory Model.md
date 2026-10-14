---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Java Memory Model

> [!abstract] What this is
> The rules for **when one thread is guaranteed to see what another thread wrote**. Just enough of the JMM to reason about `volatile`, `synchronized`, safe publication, and why double-checked locking needs `volatile`. Locks give you two things, not one: mutual exclusion **and** visibility.

---

## Three Separate Problems

- Concurrency bugs come from three different properties; mixing them up is the most common confusion.

| Property | Question | Broken example |
|---|---|---|
| **Atomicity** | Can another thread see or interleave with a half-done operation? | `count++` losing updates |
| **Visibility** | Will another thread *ever* see my write? | A `stop` flag that the worker never notices |
| **Ordering** | Will another thread see my writes in the order I made them? | Seeing a reference to an object before its fields are set |

- **`volatile`** fixes visibility and ordering for one variable, **not** atomicity.
- **`synchronized` / `Lock`** fix all three for the code inside them, **if every access uses the same lock**.
- **Atomics** (`AtomicInteger`) give atomicity *and* visibility for single-variable operations.

---

## Why Visibility Breaks at All

- Each core has **caches and store buffers**; a write may sit in one core's buffer for a while before other cores see it.
- The **JIT compiler** and the **CPU** may **reorder** instructions, and may hoist a read out of a loop if it thinks nothing in the loop changes it.

```java
class Worker {
    private boolean running = true;              // NOT volatile

    void run() {
        while (running) { doWork(); }            // JIT may read 'running' once and loop forever
    }
    void stop() { running = false; }             // called from another thread
}
```

- Without synchronization the JVM is allowed to turn that loop into `if (running) while (true) doWork();`. The fix is `private volatile boolean running`.
- None of this is "a bug in Java": the JMM deliberately allows it so code can be fast. Your job is to add the happens-before edges where threads communicate.

---

## Happens-Before

- **Happens-before** (HB) is the JMM's guarantee: if action A happens-before action B, then B sees everything A did (and everything before A).
- Without an HB edge between a write and a read in different threads, the read may see an old value. That's a **data race**.
- The HB rules you need:

| Rule | Edge |
|---|---|
| **Program order** | Within one thread, each action HB the next |
| **Monitor lock** | Unlocking a monitor HB every later lock of **the same** monitor |
| **`volatile`** | A write to a volatile variable HB every later read of **that** variable |
| **Thread start** | `t.start()` HB everything the new thread does |
| **Thread join** | Everything a thread does HB another thread returning from `join()` on it |
| **Final fields** | A constructor's writes to `final` fields are visible to any thread that sees the object, if `this` didn't escape |
| **Concurrency utilities** | Putting into a `BlockingQueue`/`ConcurrentHashMap` HB taking it out; `Future.get()` sees the task's writes; `CountDownLatch.countDown()` HB `await()` returning |
| **Transitivity** | A HB B and B HB C ⇒ A HB C |

- Transitivity is what makes the "piggybacking" trick work: write normal fields, then write a volatile flag; a reader who reads the flag then sees the normal fields too.

```java
class Handoff {
    private int data;                  // normal field
    private volatile boolean ready;    // the volatile "publish" flag

    void producer() { data = 42; ready = true; }          // write data, THEN the volatile
    void consumer() { if (ready) System.out.println(data); } // read volatile, THEN data → sees 42
}
```

---

## `volatile` in Practice

- **Use it for:** a flag or state variable written by one thread and read by others (`stop` flags, configuration swapped as a whole, "initialised" markers), and for publishing an immutable object by reference.
- **It does not make compound actions atomic:** `volatileCount++` still loses updates. Use `AtomicInteger`.
- **It applies to the reference, not the object:** a `volatile List` makes the *reference* visible, not later changes inside the list.
- Cost: cheaper than a lock, but it blocks some reordering and caching, so don't sprinkle it everywhere.

| Need | Tool |
|---|---|
| One thread writes, many read, a single value | `volatile` |
| Several threads update one value (`++`, max, CAS) | `AtomicX` / `LongAdder` |
| An invariant spans several variables | A lock |

---

## Safe Publication

- **Publishing** an object means making it reachable by another thread. Publishing it **unsafely** lets the other thread see the reference before the constructor's writes.
- Safe ways to publish:
    + Initialise it in a **static initializer** (`static final Foo FOO = new Foo();`), guaranteed by class loading.
    + Store the reference in a **`volatile`** field or an **`AtomicReference`**.
    + Store it in a **`final`** field of a properly constructed object.
    + Store it in a field **guarded by a lock**, read under the same lock.
    + Put it into a **concurrent collection** or `BlockingQueue`.
- **Immutable objects with all-final fields** are safe even if published through a data race, thanks to the final-field rule. That's one reason immutability is so valuable ([[12 - Confinement and Immutability]]).

### Don't let `this` escape the constructor

```java
class Listener {
    private final int threshold;
    Listener(EventBus bus) {
        bus.register(this);   // 'this' escapes: another thread may call us before threshold is set
        this.threshold = 10;
    }
}
```

- Registering listeners, starting threads, or handing `this` to anyone inside a constructor breaks the final-field guarantee. Use a **static factory** that constructs first and registers after.

---

## Double-Checked Locking, Explained

```java
public final class Config {
    private static volatile Config instance;          // volatile is mandatory

    public static Config get() {
        Config local = instance;                       // one volatile read on the fast path
        if (local == null) {
            synchronized (Config.class) {
                local = instance;
                if (local == null) {
                    instance = local = new Config();
                }
            }
        }
        return local;
    }
}
```

- `instance = new Config()` is really: **allocate** memory → **run the constructor** → **assign** the reference. Without `volatile`, the JIT/CPU may make the assignment visible **before** the constructor's writes.
- A second thread on the fast path (no lock) could then see a non-null `instance` whose fields are still defaults.
- `volatile` adds the HB edge: the write of `instance` happens after construction, and the reader's volatile read sees a fully built object.
- In practice prefer the **holder idiom** or an **enum** (see [[Singleton]]), which get this from class loading for free.

---

## Interview Lines

> [!tip] Say it like this
> - "Locks give two guarantees: mutual exclusion and visibility. That's why reads need the lock too, not just writes."
> - "`volatile` gives visibility and ordering for one variable but not atomicity, so `volatile++` is still broken."
> - "Happens-before is the contract: unlock before lock, volatile write before volatile read, start before the thread's work, the thread's work before join."
> - "Double-checked locking needs `volatile` because the reference can be published before the constructor finishes. I'd use the holder idiom instead."
> - "Immutable objects with final fields are safe to share even without synchronization."

## Related Notes

- [[02 - Race Conditions and Shared State]] · [[04 - synchronized and Monitors]] · [[06 - Atomics and CAS]] · [[Singleton]]
- [[LLD/Concurrency/README]]
