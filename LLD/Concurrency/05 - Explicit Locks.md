---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Explicit Locks

> [!abstract] What this is
> The `java.util.concurrent.locks` toolkit: `ReentrantLock` (timeouts, interruptible and fair locking), `Condition` (several wait queues per lock), `ReentrantReadWriteLock` (many readers or one writer), and `StampedLock` (optimistic reads). Same guarantees as `synchronized`, more control, and you must release them yourself.

---

## `ReentrantLock`

- Behaves like `synchronized` (mutual exclusion + visibility, reentrant) but is an object you call methods on.
- **The pattern is fixed: lock, then `try`, then `unlock` in `finally`.** Forgetting `finally` leaks the lock forever if anything throws.

```java
class Inventory {
    private final ReentrantLock lock = new ReentrantLock();
    private int stock;                      // guarded by lock

    boolean reserve(int qty) {
        lock.lock();                        // outside the try: if lock() fails, don't unlock
        try {
            if (stock < qty) return false;
            stock -= qty;
            return true;
        } finally {
            lock.unlock();                  // always released
        }
    }
}
```

### What it adds

| Method | Use |
|---|---|
| `tryLock()` | Take it only if free right now; otherwise do something else |
| `tryLock(timeout, unit)` | Wait at most this long, then give up. The main deadlock-avoidance tool ([[09 - Deadlock Livelock and Starvation]]) |
| `lockInterruptibly()` | Waiting to acquire can be cancelled by `interrupt()` |
| `new ReentrantLock(true)` | **Fair**: longest-waiting thread gets it next |
| `newCondition()` | Separate wait queues for different conditions |
| `isHeldByCurrentThread()`, `getQueueLength()` | Assertions and monitoring |

```java
// Transfer between two accounts without risking deadlock: back off and retry
boolean transfer(Account from, Account to, long amount) throws InterruptedException {
    while (true) {
        if (from.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
            try {
                if (to.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                    try {
                        if (from.balance < amount) return false;
                        from.balance -= amount;
                        to.balance += amount;
                        return true;
                    } finally { to.lock.unlock(); }
                }
            } finally { from.lock.unlock(); }
        }
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));  // random backoff avoids livelock
    }
}
```

- Ordering locks by account id is simpler and usually preferred; `tryLock` is the tool when you can't impose an order.

### Fairness

- **Non-fair (default)**: a thread arriving just as the lock is released can "barge" ahead of waiters. Higher throughput, because the lock doesn't sit idle while a sleeping waiter wakes up.
- **Fair**: strict FIFO hand-off. Prevents starvation, but noticeably slower.
- Pick fair only when you have a real starvation problem or a fairness requirement.

---

## `Condition`

- A `Condition` is a wait set tied to a `Lock`: `await()` ≈ `wait()`, `signal()` ≈ `notify()`, `signalAll()` ≈ `notifyAll()`.
- The win: **one lock, several conditions**, so you wake exactly the kind of thread that can make progress.

```java
class BoundedBuffer<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Object[] items;
    private int head, tail, count;

    BoundedBuffer(int capacity) { items = new Object[capacity]; }

    void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) notFull.await();   // still a while loop
            items[tail] = item;
            tail = (tail + 1) % items.length;
            count++;
            notEmpty.signal();                              // wake ONE taker, not producers
        } finally { lock.unlock(); }
    }

    @SuppressWarnings("unchecked")
    T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) notEmpty.await();
            T item = (T) items[head];
            items[head] = null;                              // help GC
            head = (head + 1) % items.length;
            count--;
            notFull.signal();                                // wake ONE putter
            return item;
        } finally { lock.unlock(); }
    }
}
```

- Same rules as `wait`: hold the lock, `await` in a `while`, change state before signalling.
- `signal()` is safe here (unlike `notify()` on a shared monitor) because everyone waiting on `notEmpty` waits for the same thing.
- This is essentially how `ArrayBlockingQueue` is implemented.

---

## `ReentrantReadWriteLock`

- Two locks in one: **many readers** can hold the read lock together, **one writer** holds the write lock alone (no readers either).
- Worth it when reads **far outnumber** writes **and** reads are slow enough that contention matters. For tiny critical sections a plain lock is often faster.

```java
class Config {
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final Map<String, String> values = new HashMap<>();

    String get(String key) {
        rw.readLock().lock();
        try { return values.get(key); } finally { rw.readLock().unlock(); }
    }
    void put(String key, String value) {
        rw.writeLock().lock();
        try { values.put(key, value); } finally { rw.writeLock().unlock(); }
    }
}
```

| Rule | Detail |
|---|---|
| **Downgrade is allowed** | Hold write, acquire read, release write: you keep reading safely |
| **Upgrade is not** | Holding read and asking for write **deadlocks** (you wait for all readers, including yourself) |
| **Writer starvation** | With a steady stream of readers, a non-fair RW lock can starve writers; fair mode helps |

- For a read-mostly **map**, `ConcurrentHashMap` usually beats a RW-locked `HashMap` ([[07 - Concurrent Collections]]). For "replace the whole config at once", an immutable snapshot in a `volatile` field is simpler still.

---

## `StampedLock` (know it exists)

- Adds **optimistic reads**: read without locking, then check whether a writer intervened; if so, retry with a real read lock.

```java
class Point {
    private final StampedLock sl = new StampedLock();
    private double x, y;

    double distanceFromOrigin() {
        long stamp = sl.tryOptimisticRead();        // no lock taken
        double cx = x, cy = y;                       // read into locals
        if (!sl.validate(stamp)) {                   // a write happened: fall back
            stamp = sl.readLock();
            try { cx = x; cy = y; } finally { sl.unlockRead(stamp); }
        }
        return Math.hypot(cx, cy);
    }
}
```

- Very fast for read-heavy data, but **not reentrant**, no `Condition`s, and easy to misuse. Mention it; reach for it only for measured hot paths.

---

## Choosing

| Need | Pick |
|---|---|
| Simple mutual exclusion | `synchronized` |
| Timeout, `tryLock`, interruptible, fairness | `ReentrantLock` |
| Different conditions to wait on | `ReentrantLock` + `Condition`s (or a `BlockingQueue`) |
| Read-heavy, slow reads, rare writes | `ReentrantReadWriteLock` |
| Measured hot read path | `StampedLock` |
| Single variable | Atomics, not locks ([[06 - Atomics and CAS]]) |

---

## Interview Lines

> [!tip] Say it like this
> - "`lock()` goes before the `try` and `unlock()` in `finally`, always."
> - "I use `ReentrantLock` when I need `tryLock` with a timeout, interruptible waiting, fairness or more than one condition."
> - "Two `Condition`s, `notFull` and `notEmpty`, mean I can `signal` exactly the right kind of thread instead of `notifyAll`."
> - "A read-write lock helps only when reads dominate and are slow; you can downgrade from write to read but never upgrade."
> - "Fair locks prevent starvation but cost throughput, so I only use them when fairness is a requirement."

## Related Notes

- [[04 - synchronized and Monitors]] · [[09 - Deadlock Livelock and Starvation]] · [[18 - Classic Problems - Coordination]]
- [[LLD/Concurrency/README]]
