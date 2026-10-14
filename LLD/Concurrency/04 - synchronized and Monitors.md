---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# synchronized and Monitors

> [!abstract] What this is
> Java's built-in lock: every object has a **monitor**, `synchronized` acquires it, and `wait`/`notify` let threads sleep until a condition changes. You need it fluently, both to write it and to explain why you'd often reach for `java.util.concurrent` instead.

---

## The Monitor

- Every Java object carries a hidden **monitor**: a lock plus a **wait set** (threads sleeping on that object).
- `synchronized` gives you two guarantees for the code it covers:
    + **Mutual exclusion**: only one thread at a time holds a given object's monitor.
    + **Visibility**: releasing the monitor happens-before the next acquisition of the **same** monitor, so the next holder sees every write ([[03 - Java Memory Model]]).
- Both only hold if **every** access to the shared state locks the **same** object. A reader that skips the lock can see stale data even if every writer locks.

---

## Forms of `synchronized`

| Form | Locks on | Notes |
|---|---|---|
| `synchronized void m()` | `this` | Whole method; simplest |
| `static synchronized void m()` | The `Class` object (`Foo.class`) | Separate from the instance lock: a static and an instance synchronized method **don't** exclude each other |
| `synchronized (lockObj) { ... }` | `lockObj` | Smallest critical section; choose the lock deliberately |

```java
class Account {
    private final Object lock = new Object();   // private lock object
    private long balance;                        // guarded by lock

    void deposit(long amount) {
        synchronized (lock) { balance += amount; }
    }
    boolean withdraw(long amount) {
        synchronized (lock) {                    // check AND act under one lock
            if (balance < amount) return false;
            balance -= amount;
            return true;
        }
    }
    long balance() {
        synchronized (lock) { return balance; }  // reads lock too: visibility
    }
}
```

- **Prefer a private final lock object** over `synchronized (this)` or synchronized methods in library-style code: outside code can lock on your `this` and accidentally block or deadlock you.
- **Never lock on** a value that can change (`synchronized (list)` after reassigning `list`), boxed primitives or string literals (they're shared/interned across the JVM), or `getClass()` in an inheritable class.
- Keep critical sections **short**: no I/O, no network calls, no calling unknown callbacks while holding the lock ([[13 - Designing Thread-Safe Classes]]).

### Reentrancy

- Java monitors are **reentrant**: a thread that holds a lock can acquire it again (a synchronized method calling another synchronized method on the same object). The JVM counts acquisitions and releases on the last exit.
- Without reentrancy, a subclass's synchronized method calling `super`'s synchronized method would deadlock with itself.

### Exceptions release the lock

- Leaving a synchronized block by **any** path, including an exception, releases the monitor automatically. That's the main convenience over `Lock`, where you must `unlock()` in `finally` ([[05 - Explicit Locks]]).

---

## `wait`, `notify`, `notifyAll`

- These let a thread **wait for a condition** ("queue not empty") without spinning.

| Call | Does |
|---|---|
| `wait()` | Releases the monitor and sleeps in the object's wait set; re-acquires the monitor before returning |
| `notify()` | Wakes **one** arbitrary waiting thread |
| `notifyAll()` | Wakes **all** waiting threads; each re-checks its condition |

### The three rules

1. **Hold the monitor** of the object you call them on, or you get `IllegalMonitorStateException`.
2. **Always wait in a `while` loop**, never an `if`:
    + **Spurious wakeups** are allowed: a thread can return from `wait()` with nobody having notified.
    + Another thread may have run between the notify and your re-acquiring the lock, and changed the condition back.
3. **Change the state, then notify, while holding the lock.**

```java
class BoundedBuffer<T> {
    private final Object lock = new Object();
    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;

    BoundedBuffer(int capacity) { this.capacity = capacity; }

    void put(T item) throws InterruptedException {
        synchronized (lock) {
            while (items.size() == capacity) lock.wait();    // wait while FULL
            items.addLast(item);
            lock.notifyAll();                                 // a taker may be waiting
        }
    }

    T take() throws InterruptedException {
        synchronized (lock) {
            while (items.isEmpty()) lock.wait();              // wait while EMPTY
            T item = items.removeFirst();
            lock.notifyAll();                                 // a putter may be waiting
            return item;
        }
    }
}
```

### `notify` vs `notifyAll`

- Here producers and consumers wait on the **same** object for **different** conditions ("not full" vs "not empty").
- With `notify()`, a producer could wake another producer instead of a consumer; it re-checks, goes back to sleep, and the consumer that should have run never wakes. Everyone sleeps: a **lost wakeup**.
- **Default to `notifyAll()`.** Use `notify()` only when every waiter waits for the same condition and any one of them can make progress.
- The cleaner fix is **separate condition queues** (`notFull`, `notEmpty`), which `Lock` + `Condition` gives you ([[05 - Explicit Locks]]), or simply a `BlockingQueue` ([[07 - Concurrent Collections]]).

---

## What `synchronized` Can't Do

| Missing | Why it matters | Alternative |
|---|---|---|
| **Timeout** on acquiring | Can't back off to avoid deadlock | `ReentrantLock.tryLock(timeout)` |
| **Interruptible** acquisition | A thread stuck in BLOCKED ignores interrupts | `lockInterruptibly()` |
| **Multiple condition queues** | `notifyAll` storms, lost wakeups | `Condition` |
| **Fairness** | Waiters can be overtaken indefinitely | `new ReentrantLock(true)` |
| **Read/write split** | Readers block each other | `ReentrantReadWriteLock` |
| **Lock across methods** (acquire in one, release in another) | Block-structured only | `Lock` (carefully) |

- When none of those matter, `synchronized` is simpler, can't leak (auto-release), and the JVM optimises it well. It's a perfectly good default.
- With **virtual threads**, blocking inside `synchronized` used to **pin** the carrier thread (fixed in JDK 24), so older code prefers `ReentrantLock` there ([[15 - Virtual Threads]]).

---

## Interview Lines

> [!tip] Say it like this
> - "`synchronized` gives mutual exclusion and visibility, but only if readers and writers use the same lock."
> - "I lock on a private final object, not `this`, so outside code can't interfere with my lock."
> - "`wait` always goes in a `while` loop because of spurious wakeups and conditions changing before I re-acquire."
> - "I default to `notifyAll` when waiters wait for different conditions; really I'd use two `Condition`s or a `BlockingQueue`."
> - "I'd switch to `ReentrantLock` when I need `tryLock` with a timeout, interruptible locking, fairness or several conditions."

## Related Notes

- [[05 - Explicit Locks]] · [[07 - Concurrent Collections]] · [[18 - Classic Problems - Coordination]]
- [[LLD/Concurrency/README]]
