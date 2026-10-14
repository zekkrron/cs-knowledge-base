---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Race Conditions and Shared State

> [!abstract] What this is
> Why concurrent code breaks: shared mutable state plus a sequence of steps that another thread can interleave with. The two shapes almost every bug takes (read-modify-write and check-then-act), why testing doesn't find them, and the three ways out.

---

## The Root Cause

- A **race condition** is when the result depends on the **timing** of threads, so some interleavings give a wrong answer.
- It needs all three of these at once:
    + **Shared**: two or more threads can reach the same object.
    + **Mutable**: at least one of them writes to it.
    + **Unsynchronized**: nothing forces their steps into a safe order.
- Remove **any one** of the three and the race disappears. That gives the three cures at the bottom of this note.

---

## Shape 1: Read-Modify-Write

```java
class Counter {
    private int count = 0;
    public void increment() { count++; }   // looks like one step, is three
    public int get() { return count; }
}
```

- `count++` is really **read** `count` → **add 1** → **write** back.
- Two threads interleaving:

| Step | Thread A | Thread B | `count` in memory |
|---|---|---|---|
| 1 | reads 5 | | 5 |
| 2 | | reads 5 | 5 |
| 3 | writes 6 | | 6 |
| 4 | | writes 6 | 6 (one increment lost) |

- This is the in-memory version of the database **lost update** (two tabs both write `qty = 4`). Same bug, same fixes: make the step atomic or lock around it.
- Run 1,000,000 increments across 4 threads and you'll usually end up short, by a different amount every run.

## Shape 2: Check-Then-Act

```java
if (!map.containsKey(key)) {     // check
    map.put(key, createValue()); // act: another thread may have put it in between
}

if (instance == null) {          // check
    instance = new Singleton();  // act: two threads can both create one
}

if (balance >= amount) {         // check
    balance -= amount;           // act: two withdrawals can both pass the check
}
```

- The decision is based on a fact that **may be stale by the time you act**.
- Lazy initialisation, "put if absent", and "withdraw if enough balance" are the three you'll see most.
- The fix is to make check and act **one atomic step**: hold a lock across both, or use an operation that does both (`ConcurrentHashMap.putIfAbsent`/`computeIfAbsent`, `AtomicInteger.compareAndSet`).

---

## Compound Actions on "Thread-Safe" Objects

- A class where **each method** is thread-safe can still be used unsafely, because a sequence of safe calls isn't safe as a whole.

```java
Map<String, Integer> m = Collections.synchronizedMap(new HashMap<>());
// Each call is synchronized, but the pair is not:
if (!m.containsKey("a")) m.put("a", 1);         // still a check-then-act race

List<String> list = Collections.synchronizedList(new ArrayList<>());
for (String s : list) { ... }                    // iteration is NOT protected: must lock on list
```

- Rule: **thread safety is a property of how an object is used**, not only of the object. Whenever an invariant spans several calls, one lock (or one atomic method) has to cover all of them.

---

## Data Races vs Race Conditions

- These are different words in the Java world:
    + A **data race** is a memory-model term: two threads access the same variable, at least one writes, and there's no **happens-before** ordering between them ([[03 - Java Memory Model]]). The reader may see stale or even partially-ordered values.
    + A **race condition** is a logic bug: the outcome depends on timing.
- You can have a race condition **without** a data race: every access is synchronized, but check-then-act is split across two synchronized calls.
- You can have a data race that "happens" to be harmless (e.g. racy caching of an idempotent value, like `String.hashCode`), but leave that to library authors.

---

## Why Testing Doesn't Catch Them

- The bad interleaving may need a context switch at one exact instruction; on a laptop it might happen once in a million runs, in production once a day.
- Bugs hide behind timing changes: adding a `println` or a debugger often makes them disappear (a **Heisenbug**).
- Visibility bugs depend on the JIT and CPU; code that works on x86 may break on ARM, which reorders memory more freely.
- So concurrency correctness is **argued, not tested**: you show which lock guards which state. Testing techniques that help are in [[17 - Testing Concurrent Code]].

---

## The Three Cures

| Cure | Removes | How | Example |
|---|---|---|---|
| **Don't share** (confinement) | "shared" | Keep state local to one thread: local variables, `ThreadLocal`, one owner thread | Request-scoped objects, a single-threaded event loop |
| **Don't mutate** (immutability) | "mutable" | Final fields, no setters, new object per change | `String`, records, value objects |
| **Synchronize** | "unsynchronized" | Locks, atomics, concurrent collections, `volatile` for flags | `synchronized`, `ReentrantLock`, `AtomicLong`, `ConcurrentHashMap` |

- Prefer them **in that order**. Code that doesn't share or doesn't mutate needs no reasoning about locks at all ([[12 - Confinement and Immutability]]).
- When you must synchronize, decide **which lock guards which fields** and make every access to those fields go through it ([[13 - Designing Thread-Safe Classes]]).

---

## Interview Lines

> [!tip] Say it like this
> - "A race needs shared, mutable and unsynchronized state; I remove one of the three."
> - "`count++` is read-modify-write, three steps. I'd use `AtomicInteger`, or `LongAdder` if it's very hot."
> - "Check-then-act is the other shape: put-if-absent, lazy init, withdraw-if-enough. Check and act must be one atomic step."
> - "Individually synchronized methods don't make a compound action safe; one lock has to cover the whole invariant."
> - "You can't test your way to thread safety; I argue it from which lock guards which state."

## Related Notes

- [[03 - Java Memory Model]] · [[04 - synchronized and Monitors]] · [[06 - Atomics and CAS]] · [[12 - Confinement and Immutability]]
- [[LLD/Concurrency/README]]
