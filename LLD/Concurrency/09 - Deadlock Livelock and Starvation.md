---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Deadlock, Livelock and Starvation

> [!abstract] What this is
> The three ways concurrent code stops making progress without crashing: **deadlock** (everyone waits forever), **livelock** (everyone is busy but nothing advances), and **starvation** (one thread never gets a turn). The four conditions behind deadlock, the standard fixes, and how to spot each.

---

## Deadlock

- Two or more threads each **hold a lock the other needs** and wait forever.

```java
// Thread 1: transfer(a, b)        Thread 2: transfer(b, a)
synchronized (from) {               // T1 locks a        T2 locks b
    synchronized (to) {             // T1 waits for b    T2 waits for a  → stuck forever
        from.debit(amount);
        to.credit(amount);
    }
}
```

### The four (Coffman) conditions

- A deadlock needs **all four** at once; break **any one** and it can't happen.

| Condition | Meaning | Break it by |
|---|---|---|
| **Mutual exclusion** | A resource can be held by one thread | Use lock-free / immutable designs (often not possible) |
| **Hold and wait** | Hold one lock while waiting for another | Acquire everything at once, or release before waiting |
| **No preemption** | Locks can't be taken away | `tryLock` with timeout: give up what you hold and retry |
| **Circular wait** | A cycle of threads, each waiting on the next | **Global lock ordering** (the main fix) |

### Fix 1: lock ordering (default)

```java
void transfer(Account from, Account to, long amount) {
    Account first  = from.id() < to.id() ? from : to;   // always lock the lower id first
    Account second = first == from ? to : from;
    synchronized (first) {
        synchronized (second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

- Every code path acquires locks in the **same global order**, so a cycle can't form. Same idea as "lock rows in sorted id order" in a database.
- Needs a stable, unique ordering key. If two objects could compare equal (e.g. `System.identityHashCode` collisions), add a tie-breaker lock.

### Fix 2: `tryLock` with timeout and backoff

- If you can't impose an order, try to get the second lock for a bounded time; on failure **release everything**, back off a random amount, and retry ([[05 - Explicit Locks]]). Random backoff matters, or both threads retry in lockstep (livelock).

### Fix 3: don't hold one lock while waiting for another

- Shrink critical sections; copy what you need out of the lock, release, then do the next step.
- **Never call alien code** (callbacks, listeners, overridable methods, other services) while holding a lock: you don't know what locks it takes ([[13 - Designing Thread-Safe Classes]]).
- Use one coarser lock if the fine-grained design is too hard to keep acyclic.

### Other deadlocks you'll meet

| Kind | Example | Fix |
|---|---|---|
| **Read lock upgrade** | Hold `readLock`, ask for `writeLock` | Release read, then take write (and re-check) |
| **Thread-pool starvation deadlock** | Tasks in a bounded pool wait on sub-tasks queued in the **same** pool | Separate pools; don't block on sub-tasks |
| **Resource deadlock** | Two threads each hold one DB connection and need a second from a pool of two | Acquire all resources up front, or size pools so it can't happen |
| **Lost wakeup** (looks like deadlock) | `notify` wakes the wrong waiter, everyone sleeps | `notifyAll` or separate `Condition`s ([[04 - synchronized and Monitors]]) |

### Detecting

- **Thread dump** (`jstack <pid>`, `jcmd <pid> Thread.print`, or Ctrl-Break): the JVM prints "Found one Java-level deadlock" with the cycle.
- `ThreadMXBean.findDeadlockedThreads()` for automated checks.
- Symptom in production: requests hang, CPU is **idle**, threads sit in `BLOCKED`/`WAITING`.

---

## Livelock

- Threads aren't blocked; they keep **reacting to each other** and retrying, so nobody makes progress. CPU is **busy**.
- Classic example: two people in a corridor both step aside the same way, again and again. In code: two threads that both release and retry on conflict at the same rhythm.
- **Fix:** add **randomness** to retries (random backoff, jitter), or make one side yield deterministically (priority by id).

---

## Starvation

- A thread is **runnable but never gets the resource** because others keep winning.
- Causes:
    + **Unfair locks**: arriving threads barge ahead of a waiting one.
    + **Readers starving writers** in a read-write lock under constant read traffic.
    + **Priority scheduling** in a queue: low-priority jobs never run while high-priority work keeps coming.
    + **A long-held lock** that others need briefly.
- **Fixes:** fair locks (`new ReentrantLock(true)`), fair RW locks, **aging** (raise priority the longer a job waits), shorter critical sections, bounded batch sizes.

---

## Telling Them Apart

| | Threads state | CPU | Progress | Fix direction |
|---|---|---|---|---|
| **Deadlock** | BLOCKED / WAITING, forever | Idle | None, ever | Break a Coffman condition (lock ordering) |
| **Livelock** | RUNNABLE, retrying | Busy | None | Randomised backoff |
| **Starvation** | Some threads never scheduled into the lock | Normal | Others progress | Fairness, aging |

---

## Interview Lines

> [!tip] Say it like this
> - "Deadlock needs mutual exclusion, hold-and-wait, no preemption and a circular wait; I break the circular wait with a global lock order, like sorting account ids."
> - "If I can't order the locks, I use `tryLock` with a timeout, release everything on failure and retry with random backoff."
> - "I never call callbacks or other services while holding a lock, because I don't know what they lock."
> - "Livelock is busy threads with no progress, fixed with jitter; starvation is one thread never winning, fixed with fairness or aging."
> - "A thread dump shows deadlock cycles directly; the symptom is hung requests with idle CPU."

## Related Notes

- [[04 - synchronized and Monitors]] · [[05 - Explicit Locks]] · [[13 - Designing Thread-Safe Classes]] · [[18 - Classic Problems - Coordination]]
- [[LLD/Concurrency/README]]
