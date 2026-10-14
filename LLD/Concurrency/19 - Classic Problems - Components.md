---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Classic Problems: Components

> [!abstract] What this is
> Thread-safe **building blocks** that appear as whole LLD problems or inside bigger ones: an LRU cache, a token-bucket rate limiter, a delayed/scheduled task executor, a connection pool, and a minimal thread pool. Each section gives the design decisions first, then compact code you can reproduce in an interview.

---

## Thread-Safe LRU Cache

- **Requirements:** `get` and `put` in `O(1)`, evict the least recently used entry when full, safe under concurrent access.
- **Design:** a `HashMap` for lookup plus a doubly linked list for recency, or simply `LinkedHashMap` in **access order** with `removeEldestEntry`. A `get` *modifies* recency, so reads need the lock too.

```java
public final class LruCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, V> map;          // guarded by lock
    private final ReentrantLock lock = new ReentrantLock();

    public LruCache(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {     // true = access order
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCache.this.capacity;
            }
        };
    }

    public Optional<V> get(K key) {
        lock.lock();
        try { return Optional.ofNullable(map.get(key)); }    // get reorders, so it's a write
        finally { lock.unlock(); }
    }

    public void put(K key, V value) {
        lock.lock();
        try { map.put(key, value); }
        finally { lock.unlock(); }
    }
}
```

- **Follow-ups to expect:**
    + *Hand-rolled version:* `HashMap<K, Node>` + a doubly linked list with dummy head/tail; `get` moves the node to the front, `put` inserts at the front and removes the tail when over capacity.
    + *Why not a `ReadWriteLock`?* Because `get` changes the list; it's a write.
    + *Scaling:* one lock is a bottleneck under heavy load. **Segment** the cache (N independent LRU caches chosen by `hash(key) % N`): approximate global LRU, much less contention. Production libraries (Caffeine) buffer recency updates instead.
    + *TTL:* store an expiry with each value and treat expired entries as misses (inject a `Clock`).
    + *Stampede:* when many threads miss the same key, load it once with `ConcurrentHashMap.computeIfAbsent` or a per-key `CompletableFuture`.

---

## Token-Bucket Rate Limiter

- **Requirements:** allow up to `rate` requests per second per client, with bursts up to `capacity`.
- **Design:** each bucket holds tokens that refill continuously; a request takes a token or is rejected. Refill lazily on each call from elapsed time, rather than with a background thread. One bucket per client in a `ConcurrentHashMap`.

```java
public final class TokenBucket {
    private final long capacity;
    private final double refillPerNano;
    private final LongSupplier clock;               // injected for testability
    private double tokens;                          // guarded by this
    private long lastRefill;                        // guarded by this

    public TokenBucket(long capacity, double perSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerNano = perSecond / 1_000_000_000.0;
        this.clock = nanoClock;
        this.tokens = capacity;
        this.lastRefill = nanoClock.getAsLong();
    }

    public synchronized boolean tryAcquire() {
        long now = clock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
        lastRefill = now;
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }
}

public final class RateLimiter {
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Supplier<TokenBucket> factory;

    public RateLimiter(Supplier<TokenBucket> factory) { this.factory = factory; }

    public boolean allow(String clientId) {
        return buckets.computeIfAbsent(clientId, id -> factory.get()).tryAcquire();
    }
}
```

- **Points to raise:**
    + Per-bucket locking: clients don't contend with each other; the map handles creation atomically.
    + Use `System.nanoTime` (monotonic), not wall-clock time, for elapsed time.
    + Make the algorithm a **Strategy** (token bucket, sliding window, fixed window) behind a `RateLimiter` interface ([[Strategy]]).
    + Idle buckets accumulate: evict them (TTL map or periodic sweep).
    + Across several servers, the counter must live in a shared store like Redis; that's the HLD version (HLD Topics: Reliability → Rate Limiting).
    + A `Semaphore` limits **concurrency**, not **rate** ([[10 - Synchronizers]]).

---

## Delayed / Scheduled Task Executor

- **Requirements:** `schedule(task, delay)`; tasks run at (or just after) their due time, possibly on several worker threads.
- **Design:** a `DelayQueue` (a blocking priority queue that only releases elements whose delay has expired) plus worker threads that `take()` and run. In production, `ScheduledThreadPoolExecutor` does exactly this ([[08 - Executors and Thread Pools]]).

```java
public final class Scheduler {
    private record Job(Runnable task, long dueNanos) implements Delayed {
        public long getDelay(TimeUnit unit) {
            return unit.convert(dueNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }
        public int compareTo(Delayed o) {
            return Long.compare(dueNanos, ((Job) o).dueNanos);
        }
    }

    private final DelayQueue<Job> queue = new DelayQueue<>();
    private final ExecutorService workers;

    public Scheduler(int threads) {
        workers = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) workers.execute(this::loop);
    }

    public void schedule(Runnable task, long delay, TimeUnit unit) {
        queue.put(new Job(task, System.nanoTime() + unit.toNanos(delay)));
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Job job = queue.take();             // blocks until the earliest job is due
                try { job.task().run(); }
                catch (RuntimeException e) { log(e); }   // one bad task mustn't kill the worker
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() { workers.shutdownNow(); }
}
```

- **Hand-rolled alternative:** a `PriorityQueue` ordered by due time + a lock and a `Condition`; workers `awaitNanos(untilEarliest)` and are signalled when a new, earlier job arrives. Good to explain, longer to write.
- **Follow-ups:** periodic tasks (re-enqueue with the next due time after each run), cancellation (a flag on the job, checked before running), and durability (a scheduler that must survive restarts stores jobs in a database and claims them with `SKIP LOCKED`, the HLD version).

---

## Connection / Object Pool

- **Requirements:** at most N expensive objects (connections), borrowed and returned by many threads; borrowers wait (with a timeout) when all are in use.
- **Design:** a `BlockingQueue` of idle objects caps the count and handles waiting. Return objects in `finally`. Validate on borrow, replace broken ones.

```java
public final class Pool<T> {
    private final BlockingQueue<T> idle;
    private final Predicate<T> isHealthy;
    private final Supplier<T> factory;

    public Pool(int size, Supplier<T> factory, Predicate<T> isHealthy) {
        this.idle = new ArrayBlockingQueue<>(size);
        this.factory = factory;
        this.isHealthy = isHealthy;
        for (int i = 0; i < size; i++) idle.add(factory.get());
    }

    public T borrow(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        T obj = idle.poll(timeout, unit);           // wait for a free one, bounded
        if (obj == null) throw new TimeoutException("pool exhausted");
        return isHealthy.test(obj) ? obj : factory.get();   // replace broken objects
    }

    public void release(T obj) {
        if (!idle.offer(obj)) throw new IllegalStateException("released more than borrowed");
    }
}

// Usage: always return it
Connection c = pool.borrow(200, TimeUnit.MILLISECONDS);
try { use(c); } finally { pool.release(c); }
```

- **Points to raise:** lazy creation (a `Semaphore` for the cap + create on demand), a borrow timeout instead of waiting forever, leak detection (track borrowers and warn when an object is held too long), and wrapping the object so `close()` returns it to the pool (try-with-resources) ([[LLD/Foundations/01 - Exception Handling]]).

---

## Minimal Thread Pool

- **Requirements:** a fixed number of worker threads executing submitted tasks from a queue; graceful shutdown.
- **Design:** a bounded `BlockingQueue<Runnable>` + N workers looping on `take()`. Shutdown sets a flag, stops accepting tasks and interrupts idle workers.

```java
public final class SimpleThreadPool {
    private final BlockingQueue<Runnable> tasks;
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean shutdown;

    public SimpleThreadPool(int threads, int queueCapacity) {
        tasks = new ArrayBlockingQueue<>(queueCapacity);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::work, "pool-worker-" + i);
            workers.add(t);
            t.start();
        }
    }

    public void submit(Runnable task) {
        if (shutdown) throw new RejectedExecutionException("pool is shut down");
        if (!tasks.offer(task)) throw new RejectedExecutionException("queue full");   // backpressure
    }

    private void work() {
        while (!shutdown || !tasks.isEmpty()) {
            try {
                Runnable r = tasks.poll(100, TimeUnit.MILLISECONDS);
                if (r != null) {
                    try { r.run(); } catch (RuntimeException e) { log(e); }   // keep the worker alive
                }
            } catch (InterruptedException e) {
                if (shutdown) return;
            }
        }
    }

    public void shutdown() { shutdown = true; }                      // finish queued tasks
    public void shutdownNow() { shutdown = true; tasks.clear(); workers.forEach(Thread::interrupt); }
}
```

- **Points to raise:** a bounded queue plus a rejection policy (throw, caller-runs, drop) instead of an unbounded queue; one task throwing must not kill its worker; `volatile` shutdown flag for visibility; and "in production I'd configure a `ThreadPoolExecutor`" ([[08 - Executors and Thread Pools]]).

---

## Interview Lines

> [!tip] Say it like this
> - "An LRU `get` changes recency, so it's a write; one lock, or segments for scale."
> - "The token bucket refills lazily from elapsed monotonic time, one bucket per client in a `ConcurrentHashMap`."
> - "A scheduler is a `DelayQueue` plus workers; one failing task is caught so the worker survives."
> - "A pool is a bounded blocking queue of idle objects; borrowers wait with a timeout and always release in `finally`."
> - "My thread pool has a bounded queue and an explicit rejection policy, never an unbounded backlog."

## Related Notes

- [[07 - Concurrent Collections]] · [[08 - Executors and Thread Pools]] · [[10 - Synchronizers]] · [[13 - Designing Thread-Safe Classes]] · [[18 - Classic Problems - Coordination]]
- [[LLD/Concurrency/README]]
