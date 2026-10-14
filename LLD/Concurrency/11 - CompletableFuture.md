---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# CompletableFuture

> [!abstract] What this is
> Composing asynchronous steps without blocking a thread per step: starting async work, chaining (`thenApply`, `thenCompose`), combining (`thenCombine`, `allOf`, `anyOf`), handling errors (`exceptionally`, `handle`), timeouts, and the executor trap that bites everyone. Comes up when an LLD design calls several services and wants to fan out.

---

## `Future` vs `CompletableFuture`

- A plain **`Future`** only lets you `get()` (block) or `cancel()`. To do something with the result you must tie up a thread waiting.
- A **`CompletableFuture`** (CF) lets you say **"when this finishes, do that"**: callbacks run when the value arrives, and steps compose into pipelines.

---

## Starting Work

```java
ExecutorService io = Executors.newFixedThreadPool(32);   // your own pool for blocking I/O

CompletableFuture<User>  user  = CompletableFuture.supplyAsync(() -> userService.get(id), io);
CompletableFuture<Void>  audit = CompletableFuture.runAsync(() -> auditLog.write(id), io);
CompletableFuture<Price> done  = CompletableFuture.completedFuture(cachedPrice);   // already known
```

> [!warning] The common-pool trap
> `supplyAsync(fn)` **without an executor** runs on `ForkJoinPool.commonPool()`, which has roughly one thread per core and is shared by the whole JVM (parallel streams use it too). Blocking calls there (DB, HTTP) starve everything else. **Always pass your own executor for blocking work.**

---

## Chaining

| Method | Function shape | Use |
|---|---|---|
| `thenApply(f)` | `T → U` | Transform the result (like `map`) |
| `thenCompose(f)` | `T → CompletableFuture<U>` | Next step is itself async (like `flatMap`); avoids `CF<CF<U>>` |
| `thenAccept(c)` | `T → void` | Consume the result |
| `thenRun(r)` | `() → void` | Run after, ignoring the result |
| `thenCombine(other, f)` | `(T, U) → V` | Merge two independent results |

```java
CompletableFuture<OrderView> view =
    CompletableFuture.supplyAsync(() -> orderRepo.find(orderId), io)          // async step 1
        .thenCompose(order -> CompletableFuture.supplyAsync(                   // step 2 depends on 1
                () -> paymentClient.status(order.paymentId()), io)
            .thenApply(status -> new OrderView(order, status)));               // combine
```

### `...Async` variants

- `thenApply(f)` runs `f` on **whichever thread completed the previous stage** (or the caller, if it was already done).
- `thenApplyAsync(f, executor)` runs `f` on the given executor. Use it when the step is heavy or blocking, so it doesn't run on, say, a Netty event-loop thread.

---

## Fan-Out and Fan-In

```java
CompletableFuture<Profile> profile = CompletableFuture.supplyAsync(() -> profiles.get(id), io);
CompletableFuture<List<Order>> orders = CompletableFuture.supplyAsync(() -> orderSvc.recent(id), io);
CompletableFuture<Integer> points = CompletableFuture.supplyAsync(() -> loyalty.points(id), io);

// Wait for all three in parallel, then assemble
CompletableFuture<Dashboard> dash = CompletableFuture.allOf(profile, orders, points)
    .thenApply(v -> new Dashboard(profile.join(), orders.join(), points.join()));  // join won't block: all done
```

| Combinator | Completes when | Result |
|---|---|---|
| `allOf(cfs...)` | **All** complete (or any fails) | `CF<Void>`, so read each one with `join()` afterwards |
| `anyOf(cfs...)` | **First** completes | `CF<Object>` (the first value) |
| `thenCombine` | Both of two complete | Merged value |

- Latency becomes the **slowest** call instead of the sum: the same parallel fan-out idea as in HLD tail-latency budgets.

---

## Errors

- If a stage throws, every dependent stage is skipped and the CF completes **exceptionally**, carrying the exception wrapped in a `CompletionException`.

| Method | Runs on | Can change the result? | Use |
|---|---|---|---|
| `exceptionally(ex → fallback)` | Failure only | Yes, recovers | Default value / fallback |
| `handle((value, ex) → result)` | Success **and** failure | Yes | Map both outcomes to one result |
| `whenComplete((value, ex) → ...)` | Both | No (side effect only) | Logging, metrics, cleanup |

```java
CompletableFuture<Price> price = CompletableFuture
    .supplyAsync(() -> pricingClient.quote(sku), io)
    .orTimeout(300, TimeUnit.MILLISECONDS)                         // fail if too slow (Java 9+)
    .exceptionally(ex -> {
        log.warn("pricing failed for {}", sku, ex);
        return Price.listPrice(sku);                               // degrade gracefully
    });
```

- `completeOnTimeout(fallback, t, unit)` completes with a default instead of failing.
- `join()` throws an unchecked `CompletionException`; `get()` throws checked `ExecutionException`/`InterruptedException`. In both, the real cause is `getCause()`.
- **`cancel(true)` doesn't interrupt the running task** in a CF (unlike `FutureTask`); it only marks the CF cancelled. Long work needs its own cancellation check.

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Blocking I/O on the common pool | Pass an executor |
| Calling `get()`/`join()` right after `supplyAsync` | You've made it synchronous; compose instead, block only at the very edge |
| `thenApply` returning a CF, giving `CF<CF<T>>` | `thenCompose` |
| No timeout on remote calls | `orTimeout` / `completeOnTimeout` |
| Exception swallowed because nobody observes the CF | End chains with `exceptionally`/`whenComplete`, or `join()` at the edge |
| Mutating shared state inside callbacks | Callbacks run on other threads: same thread-safety rules apply |

---

## Interview Lines

> [!tip] Say it like this
> - "`thenApply` maps a value, `thenCompose` chains another async call; `allOf` fans out so latency is the slowest call, not the sum."
> - "I always pass my own executor for blocking calls; the default common pool has about one thread per core and is shared JVM-wide."
> - "Every remote call gets `orTimeout` and an `exceptionally` fallback."
> - "`handle` sees both outcomes and can recover; `whenComplete` is for side effects only."
> - "I only `join` at the outermost edge; in the middle I compose."

## Related Notes

- [[08 - Executors and Thread Pools]] · [[10 - Synchronizers]] · [[14 - Fork Join and Parallel Streams]] · [[15 - Virtual Threads]]
- [[LLD/Concurrency/README]]
