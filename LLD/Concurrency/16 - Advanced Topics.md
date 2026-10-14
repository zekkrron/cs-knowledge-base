---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Advanced Topics

> [!abstract] What this is
> The concurrency ideas that show up as SDE-3 or interviewer-specific follow-ups: lock-free structures and the **ABA problem**, **false sharing**, memory barriers, the **actor model**, and `ForkJoinPool` internals. Know each well enough to explain it in two or three sentences and say when it matters; you rarely need to code them.

---

## Lock-Free Data Structures

- **Lock-free** means some thread always makes progress: threads never block each other; they retry with **CAS** when they lose a race ([[06 - Atomics and CAS]]).
- The classic example is the **Treiber stack**: the head is an `AtomicReference`, and push/pop retry until their CAS on the head succeeds.

```java
class LockFreeStack<T> {
    private record Node<T>(T value, Node<T> next) {}
    private final AtomicReference<Node<T>> head = new AtomicReference<>();

    void push(T v) {
        Node<T> oldHead, newHead;
        do {
            oldHead = head.get();
            newHead = new Node<>(v, oldHead);
        } while (!head.compareAndSet(oldHead, newHead));   // retry if someone changed head
    }

    T pop() {
        Node<T> oldHead;
        do {
            oldHead = head.get();
            if (oldHead == null) return null;
        } while (!head.compareAndSet(oldHead, oldHead.next()));
        return oldHead.value();
    }
}
```

- Real lock-free queues (`ConcurrentLinkedQueue`) are much harder. In practice you **use** them, you don't write them.
- **Progress levels**, from weakest to strongest: *blocking* (locks) → *obstruction-free* → *lock-free* (someone progresses) → *wait-free* (everyone progresses in bounded steps).

---

## The ABA Problem

- CAS checks "is the value still A?", not "has nothing happened since I read A?".
    + Thread 1 reads head = A and is paused.
    + Thread 2 pops A, pops B, pushes A back (same object, or a recycled node at the same address).
    + Thread 1 resumes; head is A again, so its CAS **succeeds**, but the structure underneath changed. It may install a stale `next` pointer.
- In Java, garbage collection prevents the worst case (a node can't be freed and reused while you still reference it), but ABA still bites when **values** recur (counters, states, reused pooled objects).
- **Fix:** CAS a value **plus a version stamp**, so A-then-B-then-A looks different: `AtomicStampedReference` (reference + int stamp) or `AtomicMarkableReference` (reference + boolean).

```java
AtomicStampedReference<Node> head = new AtomicStampedReference<>(null, 0);
int[] stamp = new int[1];
Node cur = head.get(stamp);
head.compareAndSet(cur, next, stamp[0], stamp[0] + 1);   // fails if the stamp moved
```

- This is the same idea as an **optimistic version column** in a database: compare the version, not just the value.

---

## False Sharing

- CPUs cache memory in **cache lines** (usually 64 bytes). If two threads write two *different* variables that happen to sit in the **same cache line**, each write invalidates the other core's copy. The variables are logically independent, but the hardware makes them contend.
- Symptom: a "perfectly parallel" counter per thread scales badly as you add threads.
- Fixes:
    + **Padding** so hot fields sit on separate lines; the JDK uses the internal `@Contended` annotation for this.
    + Use **`LongAdder`**, which already spreads its cells to avoid it ([[06 - Atomics and CAS]]).
    + Keep per-thread data in per-thread objects rather than adjacent array slots.

---

## Memory Barriers

- Compilers and CPUs **reorder** memory operations for speed. A **memory barrier** (fence) is an instruction that forbids certain reorderings across it and forces writes to become visible.
- Java hides barriers behind the memory model: a `volatile` write acts like a release barrier, a `volatile` read like an acquire barrier, and lock release/acquire likewise. **Happens-before** is the language-level guarantee these barriers implement ([[03 - Java Memory Model]]).
- `VarHandle` exposes finer-grained modes (`getAcquire`/`setRelease`, `getOpaque`, explicit `fullFence()`) for library authors. In application code, `volatile`, locks and atomics are the right level.

---

## The Actor Model

- Instead of many threads sharing state behind locks, each **actor** owns its state privately and processes **messages from its mailbox one at a time**. Actors communicate only by sending messages.
- Reasoning becomes single-threaded inside each actor: no locks, no races on its state. It's thread confinement ([[12 - Confinement and Immutability]]) made into an architecture.
- In plain Java, an actor is a single-threaded executor or a thread draining a `BlockingQueue`; frameworks include Akka/Pekko.
- Costs: everything becomes asynchronous; request/response needs correlation; a slow actor's mailbox grows (backpressure needed); and invariants across several actors need a protocol, not a lock.
- In LLD rounds, this is the "single writer" answer: "all bookings for a show go through one queue, processed by one thread, so there's no lock on the seat map."

---

## `ForkJoinPool` Internals

- One deque per worker; owners push/pop at the head (LIFO), thieves steal from the tail (FIFO) ([[14 - Fork Join and Parallel Streams]]).
- **`join()` doesn't simply block**: a joining worker helps by running other queued tasks (and, if needed, the pool compensates by activating a spare thread), which is why recursive fork/join doesn't deadlock the pool.
- **`ManagedBlocker`** lets a task tell the pool it's about to block, so the pool can add a compensating thread. It's how virtual threads' carriers and some blocking operations avoid starving the pool.
- The common pool is sized to `availableProcessors() - 1`, shared JVM-wide, and is the default for parallel streams and `CompletableFuture` async methods.

---

## Interview Lines

> [!tip] Say it like this
> - "Lock-free code retries with CAS instead of blocking; I'd use the JDK's structures rather than write my own."
> - "ABA is CAS seeing the same value after it changed and changed back; a version stamp fixes it, like an optimistic version column."
> - "False sharing is independent variables on one cache line contending in hardware; padding or `LongAdder` fixes it."
> - "The actor model replaces locks with a single owner per piece of state processing messages one at a time."

## Related Notes

- [[03 - Java Memory Model]] · [[06 - Atomics and CAS]] · [[12 - Confinement and Immutability]] · [[14 - Fork Join and Parallel Streams]]
- [[LLD/Concurrency/README]]
