---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Designing Thread-Safe Classes

> [!abstract] What this is
> How to go from "this class has some fields" to "this class is correct under any interleaving": name the **invariant**, decide **what guards it**, choose the **lock granularity**, and keep **alien code** out of critical sections. This is the reasoning interviewers want to hear when they say "now make it thread-safe".

---

## The Procedure

- Thread safety is a property of a **class and its invariants**, not of individual lines. Work through these steps in order:
    + **1. List the state.** Every field, including the objects those fields point to (a `final List` is still mutable state).
    + **2. Write down the invariants.** Rules that must always hold across those fields: "`size` equals the number of entries", "`available + reserved == capacity`", "a seat is in at most one booking".
    + **3. Pick the cheapest strategy per piece of state** ([[02 - Race Conditions and Shared State]]):
        - Don't share it (confinement), or don't mutate it (immutability) ([[12 - Confinement and Immutability]]).
        - A single independent variable: an atomic or a `volatile` (only if writes don't depend on reads) ([[06 - Atomics and CAS]]).
        - Several variables tied by one invariant: **one lock guards all of them**.
    + **4. Every access goes through the guard.** Reads too: an unsynchronized read can see stale or half-updated state ([[03 - Java Memory Model]]).
    + **5. Document it.** `// guarded by lock` next to the field, and a class-level note saying whether the class is thread-safe.

```java
/** Thread-safe. All fields guarded by `lock`. */
public final class SeatInventory {
    private final Object lock = new Object();
    private int available;          // guarded by lock
    private int reserved;           // guarded by lock
    // invariant: available + reserved == capacity

    public boolean reserve(int n) {
        synchronized (lock) {
            if (available < n) return false;      // check and act under ONE lock
            available -= n;
            reserved += n;
            return true;
        }
    }
}
```

> [!warning] Two atomics don't make an invariant atomic
> Making `available` and `reserved` two separate `AtomicInteger`s is a classic mistake: each update is atomic, but another thread can run between them and see `available + reserved != capacity`. **One invariant, one lock.**

---

## Lock Granularity

- How much state one lock covers is a trade-off between **simplicity** and **contention**.

| Granularity | How it works | Costs you | Pick when |
|---|---|---|---|
| **One coarse lock** | A single lock for the whole object | Every operation serialises | Default; low contention, or the operations touch shared invariants |
| **Fine-grained locks** | Separate locks for independent groups of state | Deadlock risk if an operation needs two; harder to reason about | Independent state with real contention |
| **Lock striping** | N locks, each guarding a slice of buckets (`hash(key) % N`) | Operations across stripes need several locks, taken in order | Map-like structures with many keys (how early `ConcurrentHashMap` worked) |
| **Read-write lock** | Many readers or one writer | Overhead; writer starvation in some modes | Read-mostly state with expensive reads ([[05 - Explicit Locks]]) |
| **Lock-free / copy-on-write** | CAS on a reference, or swap immutable snapshots | Allocation; retry loops | Small state, read-heavy ([[06 - Atomics and CAS]]) |

- Start coarse. Split only when you can name the contention and the invariants stay intact.
- **Per-key locking** is often what an LLD problem needs: lock a seat, an account or a parking spot, not the whole system. Use a `ConcurrentHashMap<Key, Lock>` (via `computeIfAbsent`) or lock the domain object itself. Multi-key operations (transfer between two accounts) must take the locks in a **fixed order** ([[09 - Deadlock Livelock and Starvation]]).

---

## Keep Critical Sections Small and Closed

- **Short:** compute outside the lock, then lock only to read or publish the shared state.
- **No I/O, no network, no sleeping** while holding a lock: every other thread waits on your disk or your HTTP call.
- **Never call alien code under a lock.** Alien code is anything whose locking you don't control: listeners, callbacks, overridable methods, strategy objects passed in, other services. It might take another lock (deadlock), call back into you (reentrancy surprises), or simply be slow.

```java
// Bad: listeners run while we hold the lock
synchronized (lock) {
    state = newState;
    for (Listener l : listeners) l.onChange(newState);
}

// Good: snapshot under the lock, notify outside
List<Listener> toNotify;
synchronized (lock) {
    state = newState;
    toNotify = List.copyOf(listeners);
}
toNotify.forEach(l -> l.onChange(newState));
```

---

## Don't Let State Escape

- Encapsulation is what makes the guard enforceable. If a caller can reach the state without your lock, the lock means nothing.
    + **Return copies or unmodifiable views** of internal collections, never the collection itself.
    + **Don't publish `this` during construction**: starting a thread or registering a listener from the constructor lets other threads see a half-built object.
    + **Make fields `private`** and the class `final` (or design for safe subclassing), so subclasses can't access state unguarded.

---

## Composing Thread-Safe Pieces

- A class built from thread-safe parts **isn't automatically thread-safe**: a compound action across two of them is still a race.

```java
// Both maps are thread-safe, the operation is not
if (!active.containsKey(id)) {      // check
    active.put(id, session);         // act: another thread may have put in between
}
// Fix: one atomic call on the collection...
active.putIfAbsent(id, session);
// ...or one lock around both steps when they span several objects
```

- Options when a compound action spans several thread-safe objects:
    + Use the collection's own atomic methods (`putIfAbsent`, `compute`, `merge`) ([[07 - Concurrent Collections]]).
    + Put a lock in **your** class that guards the whole operation (client-side locking only works if you know which lock the component uses internally, so prefer your own).
    + Redesign so the state that changes together lives together.

---

## Interview Checklist

- When asked to make something thread-safe, say these out loud:
    + "The shared mutable state is **X**; the invariant is **Y**."
    + "**Z** guards it; every read and write goes through Z."
    + "Check-then-act and read-modify-write sequences happen inside one critical section."
    + "Critical sections do no I/O and call no external code."
    + "Multi-resource operations lock in a fixed order."
    + "Internal collections never leak out."

---

## Interview Lines

> [!tip] Say it like this
> - "I start from the invariant, then decide which lock guards it; one invariant, one lock."
> - "Two atomic fields don't give an atomic invariant across them."
> - "I start with one coarse lock and only split it when I can name the contention."
> - "I lock per seat or per account, not the whole system, and take multiple locks in id order."
> - "I never call listeners or other services while holding a lock; I snapshot and call outside."

## Related Notes

- [[02 - Race Conditions and Shared State]] · [[04 - synchronized and Monitors]] · [[09 - Deadlock Livelock and Starvation]] · [[12 - Confinement and Immutability]]
- [[LLD/Concurrency/README]]
