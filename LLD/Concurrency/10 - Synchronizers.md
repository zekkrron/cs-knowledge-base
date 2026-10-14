---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Synchronizers

> [!abstract] What this is
> Ready-made coordination objects that make threads wait for each other in common patterns: `CountDownLatch` (wait for N events), `CyclicBarrier` (N threads meet repeatedly), `Semaphore` (at most N at once), plus `Phaser` and `Exchanger` to recognise. Reach for these before hand-rolling `wait`/`notify`.

---

## At a Glance

| Synchronizer | Mental model | Reusable? | Typical use |
|---|---|---|---|
| **`CountDownLatch`** | A gate that opens once a counter reaches zero | No (one-shot) | Wait for N services to start; start N threads at the same instant |
| **`CyclicBarrier`** | A meeting point: all N parties wait until everyone arrives | Yes | Phased parallel algorithms, simulations step by step |
| **`Semaphore`** | A bag of N permits | Yes | Limit concurrency: connection pools, "at most 10 calls to the API" |
| **`Phaser`** | A barrier where parties can join/leave per phase | Yes | Dynamic multi-phase work |
| **`Exchanger`** | Two threads swap objects at a meeting point | Yes | Double-buffering between a producer and a consumer |

---

## `CountDownLatch`

- Created with a count; `countDown()` decrements it; `await()` blocks until it reaches **zero**. It can't be reset.

```java
// Main thread waits until 3 services have started
CountDownLatch ready = new CountDownLatch(3);
for (Service s : services) {
    pool.execute(() -> { s.start(); ready.countDown(); });
}
if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("startup timed out");
```

```java
// Start N worker threads at the same instant (useful in concurrency tests)
CountDownLatch startGun = new CountDownLatch(1);
CountDownLatch done = new CountDownLatch(n);
for (int i = 0; i < n; i++) {
    pool.execute(() -> {
        try { startGun.await(); work(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { done.countDown(); }          // always count down, even on failure
    });
}
startGun.countDown();                          // release everyone together
done.await();
```

- Put `countDown()` in `finally`, or one failing worker leaves the waiter hanging forever.
- `countDown()` happens-before a successful `await()` returning, so the waiter sees the workers' writes.

---

## `CyclicBarrier`

- N threads call `await()`; each blocks until **all N** have arrived, then all continue together. It then **resets** for the next round.
- An optional **barrier action** runs once per round (by the last thread to arrive) before anyone is released, e.g. merging partial results.

```java
CyclicBarrier barrier = new CyclicBarrier(4, () -> grid.swapBuffers());  // runs each round
Runnable worker = () -> {
    try {
        while (!done()) {
            computeMySlice();
            barrier.await();           // wait for the other 3 slices, then next step
        }
    } catch (InterruptedException | BrokenBarrierException e) {
        Thread.currentThread().interrupt();
    }
};
```

- If one party is interrupted or times out, the barrier becomes **broken** and every waiter gets `BrokenBarrierException`. Everyone fails together instead of hanging.

| | `CountDownLatch` | `CyclicBarrier` |
|---|---|---|
| Who waits | Usually one or more *other* threads wait for workers | The workers wait for **each other** |
| Count driven by | Events (`countDown` can be called by anyone, any number of times) | Arriving threads |
| Reuse | One-shot | Resets each round |

---

## `Semaphore`

- Holds N **permits**. `acquire()` takes one (blocks if none left); `release()` gives one back. It limits **how many** threads do something at once, rather than excluding all but one.

```java
class RateLimitedClient {
    private final Semaphore permits = new Semaphore(10);   // at most 10 calls in flight

    Response call(Request r) throws InterruptedException {
        permits.acquire();
        try {
            return http.send(r);
        } finally {
            permits.release();                              // always give it back
        }
    }

    Optional<Response> tryCall(Request r) throws InterruptedException {
        if (!permits.tryAcquire(100, TimeUnit.MILLISECONDS)) return Optional.empty();  // shed load
        try { return Optional.of(http.send(r)); } finally { permits.release(); }
    }
}
```

- A semaphore with 1 permit is a mutex, but **not reentrant and not owned**: any thread may `release()`, which is both flexible and easy to misuse (releasing more than you acquired raises the permit count permanently).
- Fair mode (`new Semaphore(n, true)`) avoids starvation.
- Uses in LLD: connection/object pools, bulkheads around a dependency, limiting parallel downloads, bounding virtual-thread access to a scarce resource ([[15 - Virtual Threads]]).
- A semaphore limits **concurrency**, not **rate**: 10 permits allow 10 at once, not 10 per second. A rate limiter needs a clock (token bucket, [[19 - Classic Problems - Components]]).

---

## `Phaser` and `Exchanger` (recognise them)

- **`Phaser`**: like a `CyclicBarrier` whose number of parties can change between phases (`register`, `arriveAndDeregister`, `arriveAndAwaitAdvance`). Useful when workers join or finish at different times.
- **`Exchanger<V>`**: two threads meet and swap a value (`exchange(v)`), e.g. a filler thread hands a full buffer to a drainer and gets an empty one back.

---

## Choosing

| Need | Use |
|---|---|
| Wait until N things have happened, once | `CountDownLatch` |
| N threads proceed in lockstep rounds | `CyclicBarrier` |
| At most N at a time | `Semaphore` |
| Wait for one result | `Future` / `CompletableFuture` ([[11 - CompletableFuture]]) |
| Pass items between threads | `BlockingQueue` ([[07 - Concurrent Collections]]) |
| Rounds with a changing number of parties | `Phaser` |

---

## Interview Lines

> [!tip] Say it like this
> - "A latch is a one-shot gate for 'wait until N things happened'; a barrier is a reusable meeting point where workers wait for each other."
> - "A semaphore caps how many run at once, which is how I'd build a connection pool or a bulkhead."
> - "A semaphore limits concurrency, not rate; a per-second limit needs a token bucket."
> - "I count down and release in `finally`, otherwise one failure hangs everyone."
> - "For tests I use a start-gun latch to release all threads at once and make races more likely."

## Related Notes

- [[07 - Concurrent Collections]] · [[11 - CompletableFuture]] · [[17 - Testing Concurrent Code]] · [[19 - Classic Problems - Components]]
- [[LLD/Concurrency/README]]
