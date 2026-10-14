---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Concurrent Collections

> [!abstract] What this is
> The thread-safe collections in `java.util.concurrent` and when to pick each: `ConcurrentHashMap` and its atomic compound methods, `CopyOnWriteArrayList`, the `BlockingQueue` family, `ConcurrentLinkedQueue`, and the skip-list sorted collections. Plus why the old `synchronized*` wrappers aren't enough.

---

## Why Not Just Wrap a `HashMap`?

| Option | Problem |
|---|---|
| Plain `HashMap` shared between threads | Not thread-safe at all: lost updates, corrupted internal structure, infinite loops in old JDKs during resize |
| `Hashtable`, `Collections.synchronizedMap(...)` | One lock for the whole map: every reader and writer queues; compound actions (`if absent then put`) still race; iteration needs manual locking |
| **`ConcurrentHashMap`** | Fine-grained locking + CAS, reads mostly lock-free, atomic compound methods, iteration never throws |

---

## `ConcurrentHashMap`

- Reads (`get`) don't lock. Writes lock only the **bucket** they touch (CAS for empty buckets), so different keys rarely contend.
- Iterators are **weakly consistent**: they never throw `ConcurrentModificationException` and may or may not show changes made during iteration.
- **No `null` keys or values**: `get` returning `null` must unambiguously mean "absent".
- `size()` is an estimate under concurrent writes; don't build logic on it.

### Use the atomic compound methods

```java
ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();

// WRONG: check-then-act across two calls
if (!counts.containsKey(word)) counts.put(word, 0);
counts.put(word, counts.get(word) + 1);              // lost updates

// RIGHT: each of these runs atomically for that key
counts.merge(word, 1, Integer::sum);                  // increment or insert 1
counts.putIfAbsent(word, 0);
counts.computeIfAbsent(userId, id -> new ArrayList<>()).add(event);  // build-on-miss
counts.compute(word, (k, v) -> v == null ? 1 : v + 1);
counts.computeIfPresent(word, (k, v) -> v > 1 ? v - 1 : null);       // returning null removes
counts.replace(word, oldValue, newValue);             // CAS on one entry
```

- The function passed to `compute*`/`merge` runs **while that bucket is locked**:
    + Keep it **short**: no I/O, no network, no slow computation (it blocks other writers to nearby keys).
    + **Never modify the same map** from inside it (can deadlock or throw).
- `computeIfAbsent(k, …).add(x)`: the `computeIfAbsent` is atomic, but the returned `ArrayList` isn't thread-safe. If several threads add to the same list, make the value a concurrent collection too (`ConcurrentLinkedQueue`, `CopyOnWriteArrayList`) or synchronize on it.
- Frequency counting under heavy load: `counts.computeIfAbsent(k, x -> new LongAdder()).increment()` ([[06 - Atomics and CAS]]).
- Bulk ops (`forEach`, `reduce`, `search` with a parallelism threshold) exist for parallel aggregation.

---

## `CopyOnWriteArrayList` / `CopyOnWriteArraySet`

- Every write **copies the whole array**; reads and iteration work on an immutable snapshot with no locking.
- Perfect for **read-very-heavy, rarely-written, small** lists: **listener/observer lists**, configuration, routing tables.
- Terrible for frequent writes or large lists (each add is `O(n)` copying).
- Iterators see the snapshot from when they started and never throw `ConcurrentModificationException`, which is exactly what you want when notifying observers that might unsubscribe mid-notification.

---

## The `BlockingQueue` Family

- A queue where `put` **waits while full** and `take` **waits while empty**. The standard tool for **producer-consumer**; it replaces hand-written `wait`/`notify` ([[18 - Classic Problems - Coordination]]).

| Method style | Full queue on insert / empty on remove |
|---|---|
| `add` / `remove` / `element` | Throw an exception |
| `offer` / `poll` / `peek` | Return `false` / `null` immediately |
| `offer(e, timeout)` / `poll(timeout)` | Wait up to the timeout |
| **`put` / `take`** | **Block until possible** (interruptible) |

| Implementation | Shape | Pick when |
|---|---|---|
| **`ArrayBlockingQueue`** | Bounded, array, one lock | Fixed capacity, predictable memory; the default bounded buffer |
| **`LinkedBlockingQueue`** | Optionally bounded (default `Integer.MAX_VALUE`!), separate put/take locks | Higher throughput; **always pass a capacity** or it's effectively unbounded |
| **`PriorityBlockingQueue`** | Unbounded, ordered by priority | Job queues where priority matters |
| **`DelayQueue`** | Unbounded; elements become available after their delay | Scheduled jobs, TTL expiry, retry-after |
| **`SynchronousQueue`** | Capacity zero: each `put` waits for a `take` | Direct hand-off (used by `Executors.newCachedThreadPool`) |
| **`LinkedTransferQueue`** | Unbounded; `transfer` waits for a consumer | Hand-off with optional buffering |
| `LinkedBlockingDeque` | Double-ended | Work stealing, LIFO/FIFO mixes |

> [!warning] Unbounded queues hide overload
> An unbounded queue never pushes back, so a slow consumer turns into growing memory and latency until the process dies. Bound it and decide what happens when full: block the producer, reject, or drop. Same lesson as backpressure in HLD.

---

## Non-Blocking Queues and Sorted Collections

| Class | Use |
|---|---|
| `ConcurrentLinkedQueue` / `ConcurrentLinkedDeque` | Lock-free unbounded queue; fast when you never need to *wait* for an element |
| `ConcurrentSkipListMap` / `ConcurrentSkipListSet` | Thread-safe **sorted** map/set (`O(log n)`), with `floorKey`, `ceilingKey`, range views. The concurrent `TreeMap`: order books, time-ordered schedules, leaderboards |

---

## Picking the Right One

| Need | Pick |
|---|---|
| Shared key-value map | `ConcurrentHashMap` |
| Shared sorted map / range queries | `ConcurrentSkipListMap` |
| Listener list, read-mostly config list | `CopyOnWriteArrayList` |
| Producer-consumer with backpressure | `ArrayBlockingQueue` / bounded `LinkedBlockingQueue` |
| Priority jobs | `PriorityBlockingQueue` (or a `PriorityQueue` under a lock) |
| Delayed / scheduled items | `DelayQueue` or `ScheduledExecutorService` |
| Fire-and-forget queue, never wait | `ConcurrentLinkedQueue` |
| Immutable shared data | `List.of`, `Map.copyOf` (no locking needed at all) |

---

## Interview Lines

> [!tip] Say it like this
> - "`synchronizedMap` locks every call but compound actions still race; `ConcurrentHashMap` gives me atomic `merge`, `compute` and `putIfAbsent`."
> - "The function inside `compute` runs under the bucket lock, so I keep it short and never touch the map from inside."
> - "Observer lists are a `CopyOnWriteArrayList`: reads and iteration are lock-free and a listener can unsubscribe mid-notify."
> - "Producer-consumer is a bounded `BlockingQueue` with `put`/`take`; I always bound it so overload pushes back."
> - "`LinkedBlockingQueue` without a capacity is effectively unbounded, which is a classic memory leak."

## Related Notes

- [[06 - Atomics and CAS]] · [[08 - Executors and Thread Pools]] · [[19 - Classic Problems - Components]]
- [[LLD/Concurrency/README]]
