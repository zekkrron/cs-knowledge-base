---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Classic Problems: Coordination

> [!abstract] What this is
> The textbook problems where threads must **wait for each other**: producer-consumer (written three ways), a bounded blocking queue, printing in turns, reader-writer, and dining philosophers. Each one drills a specific tool; interviewers use them to check you can write correct waiting code from scratch.

---

## Producer-Consumer, Three Ways

- Producers put items into a bounded buffer; consumers take them. Producers must **wait when full**, consumers **wait when empty**. The same problem written three ways shows the whole progression from monitors to library code.

### 1. `wait` / `notifyAll` (intrinsic monitor)

```java
class BoundedBuffer<T> {
    private final Object[] items;
    private int head, tail, count;                 // guarded by this

    BoundedBuffer(int capacity) { items = new Object[capacity]; }

    public synchronized void put(T item) throws InterruptedException {
        while (count == items.length) wait();      // WHILE, not if: spurious wakeups, other producers
        items[tail] = item;
        tail = (tail + 1) % items.length;
        count++;
        notifyAll();                               // wake consumers (and producers; can't target)
    }

    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) wait();
        T item = (T) items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        notifyAll();
        return item;
    }
}
```

- `notifyAll`, not `notify`: producers and consumers wait on the **same** monitor, and `notify` could wake a thread of the wrong kind and leave everyone asleep ([[04 - synchronized and Monitors]]).

### 2. `ReentrantLock` + two `Condition`s

```java
class BoundedBuffer<T> {
    private final Object[] items;
    private int head, tail, count;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    BoundedBuffer(int capacity) { items = new Object[capacity]; }

    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) notFull.await();
            items[tail] = item;
            tail = (tail + 1) % items.length;
            count++;
            notEmpty.signal();                     // wake exactly one consumer
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) notEmpty.await();
            T item = (T) items[head];
            items[head] = null;
            head = (head + 1) % items.length;
            count--;
            notFull.signal();                      // wake exactly one producer
            return item;
        } finally {
            lock.unlock();
        }
    }
}
```

- Separate wait sets let you `signal` the right kind of thread instead of waking everyone ([[05 - Explicit Locks]]). This is essentially how `ArrayBlockingQueue` is written.

### 3. `BlockingQueue` (what you'd actually ship)

```java
BlockingQueue<Order> queue = new ArrayBlockingQueue<>(100);

Runnable producer = () -> {
    try {
        while (running) queue.put(nextOrder());
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
};
Runnable consumer = () -> {
    try {
        while (!Thread.currentThread().isInterrupted()) process(queue.take());
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
};
```

- **Stopping consumers cleanly:** interrupt them, or put a **poison pill** (a sentinel item, one per consumer) after the last real item, so each consumer exits when it takes one.
- Interview flow: write version 1 or 2 when asked to "implement a blocking queue", then say "in production I'd use `ArrayBlockingQueue`" ([[07 - Concurrent Collections]]).

---

## Print in Turns (Odd/Even, A-B-C)

- Two threads print 1..N alternately: thread "odd" prints odd numbers, thread "even" prints even ones. Generalises to N threads printing in rotation.

```java
class TurnPrinter {
    private final int max;
    private int next = 1;                          // guarded by this

    TurnPrinter(int max) { this.max = max; }

    void run(int remainder, int threads) {         // odd: (1, 2); even: (0, 2)
        synchronized (this) {
            while (true) {
                while (next <= max && next % threads != remainder) {
                    try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                if (next > max) { notifyAll(); return; }
                System.out.println(Thread.currentThread().getName() + ": " + next++);
                notifyAll();
            }
        }
    }
}

TurnPrinter p = new TurnPrinter(10);
new Thread(() -> p.run(1, 2), "odd").start();
new Thread(() -> p.run(0, 2), "even").start();
```

- Shared turn state, wait in a `while` on "is it my turn?", act, advance the turn, `notifyAll`. A `Semaphore` per thread (each thread releases the next one's semaphore) is a neat alternative for the N-thread rotation.

---

## Reader-Writer

- Many threads may read at once, but a writer needs exclusive access. Use a `ReentrantReadWriteLock` ([[05 - Explicit Locks]]).

```java
class PriceBook {
    private final Map<String, Long> prices = new HashMap<>();   // guarded by rw
    private final ReadWriteLock rw = new ReentrantReadWriteLock();

    Long price(String sku) {
        rw.readLock().lock();
        try { return prices.get(sku); } finally { rw.readLock().unlock(); }
    }

    void update(String sku, long price) {
        rw.writeLock().lock();
        try { prices.put(sku, price); } finally { rw.writeLock().unlock(); }
    }
}
```

- Points to raise: **writer starvation** under a constant stream of readers (fair mode helps); you can **downgrade** write → read but not upgrade read → write (deadlock); for small read-mostly state, an immutable snapshot in a `volatile` field or a `ConcurrentHashMap` is often simpler and faster ([[12 - Confinement and Immutability]]).

---

## Dining Philosophers

- Five philosophers sit around a table with one fork between each pair; each needs both neighbouring forks to eat. If everyone picks up their left fork first, they **deadlock** in a circle ([[09 - Deadlock Livelock and Starvation]]).

| Fix | How it breaks the cycle |
|---|---|
| **Resource ordering** | Always pick up the lower-numbered fork first; the last philosopher picks right-then-left, so no circular wait |
| **Limit diners** | A `Semaphore(4)`: at most 4 try to eat, so at least one gets both forks |
| **`tryLock` with timeout** | Take left, try right; if it fails, put left down and back off randomly (avoid livelock with jitter) |
| **Waiter / arbiter** | One lock or coordinator hands out both forks together |

```java
void dine(int id, ReentrantLock[] forks) {
    ReentrantLock first  = forks[Math.min(id, (id + 1) % forks.length)];
    ReentrantLock second = forks[Math.max(id, (id + 1) % forks.length)];
    first.lock();
    try {
        second.lock();
        try { eat(id); } finally { second.unlock(); }
    } finally {
        first.unlock();
    }
}
```

- The interview point isn't the philosophers; it's showing **which of the four deadlock conditions** each fix removes.

---

## Thread-Safe Singleton

- Covered in full with the patterns: holder idiom, enum singleton, and double-checked locking with `volatile` ([[Singleton]], [[03 - Java Memory Model]]).

---

## Interview Lines

> [!tip] Say it like this
> - "Waiting is always in a `while` loop on the condition, because of spurious wakeups and competing threads."
> - "With one monitor I need `notifyAll`; with a lock and two conditions I can signal exactly the right side."
> - "In production this is an `ArrayBlockingQueue`, and I stop consumers with interrupts or a poison pill."
> - "Dining philosophers is a deadlock demo; ordering the forks removes the circular wait."

## Related Notes

- [[04 - synchronized and Monitors]] · [[05 - Explicit Locks]] · [[07 - Concurrent Collections]] · [[09 - Deadlock Livelock and Starvation]] · [[19 - Classic Problems - Components]]
- [[LLD/Concurrency/README]]
