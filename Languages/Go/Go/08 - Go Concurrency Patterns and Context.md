---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Concurrency Patterns and Context

> [!abstract] What this is
> The patterns Go services are built from: **`context.Context`** (cancellation, deadlines, request-scoped values, `WithCancelCause`, `WithoutCancel`, `AfterFunc`), **bounded fan-out** with `errgroup`, **worker pools**, **pipelines**, **fan-in/fan-out**, semaphores and rate limiting, **timeouts and retries**, `singleflight` for duplicate suppression, and **goroutine leaks**: how they happen and how to find them (leak profile GA in **1.27**, `goleak` in tests). The decision this helps with: structuring concurrent work so it's bounded, cancellable and leak-free. Primitives are in [[07 - Go Concurrency Primitives]].

---

## Why It Matters

- Every goroutine a request starts must stop when the request ends; otherwise the service leaks memory and goroutines until it falls over. Context is the mechanism, and leaks are one of the most common Go production problems.
- Unbounded fan-out (one goroutine per item, thousands of items) overwhelms downstreams just like unbounded threads would.

---

## context.Context

```go
func (s *Server) handle(w http.ResponseWriter, r *http.Request) {
    ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)   // derive: inherits client disconnects
    defer cancel()                                                    // always: releases timers/resources

    order, err := s.store.Get(ctx, r.PathValue("id"))                 // pass ctx as the first parameter
    if errors.Is(err, context.DeadlineExceeded) {
        http.Error(w, "timeout", http.StatusGatewayTimeout)
        return
    }
    ...
}
```

| Function | Use |
|---|---|
| `WithCancel` / **`WithCancelCause`** (1.20) | Cancel manually; record why (`context.Cause(ctx)`) |
| `WithTimeout` / `WithDeadline` (+ `...Cause` 1.21) | Bound time for a subtree of work |
| `WithValue` | Request-scoped metadata (trace IDs, auth principal); **not** for optional parameters or dependencies |
| **`WithoutCancel`** (1.21) | Keep values but detach from parent cancellation: work that must finish after the request (audit write, async publish) |
| **`AfterFunc`** (1.21) | Run a callback when the context is done (close a connection, stop a non-context API) |

- Rules: `ctx` is the **first parameter** (`ctx context.Context`), never stored in structs (except long-lived workers' own root contexts), never `nil` (use `context.Background()`/`TODO()`).
- Cancellation is **cooperative**: it only stops code that checks `ctx.Done()`/`ctx.Err()` or calls context-aware APIs (`QueryContext`, `http.NewRequestWithContext`, `select` on `ctx.Done()`).
- Cancelling a parent cancels all children; children never cancel parents.
- Value keys: unexported custom types (`type ctxKey struct{}`) to avoid collisions; typed accessor functions.

---

## Bounded Fan-Out

```go
func enrich(ctx context.Context, ids []string) ([]Profile, error) {
    out := make([]Profile, len(ids))                  // index per goroutine: no mutex needed
    g, ctx := errgroup.WithContext(ctx)
    g.SetLimit(16)                                    // at most 16 concurrent calls
    for i, id := range ids {
        g.Go(func() error {
            p, err := profiles.Get(ctx, id)
            if err != nil {
                return fmt.Errorf("profile %s: %w", id, err)
            }
            out[i] = p
            return nil
        })
    }
    return out, g.Wait()                              // first error cancels ctx for the rest
}
```

- The default pattern for "call N things concurrently in a request": bounded, cancellable, first-error propagation ([[07 - Go Concurrency Primitives]]).
- Writing to distinct slice indexes from different goroutines is race-free; appending to a shared slice is not.

---

## Worker Pool

```go
func process(ctx context.Context, jobs <-chan Job, workers int) error {
    g, ctx := errgroup.WithContext(ctx)
    for range workers {                               // range over int (1.22)
        g.Go(func() error {
            for {
                select {
                case <-ctx.Done():
                    return ctx.Err()
                case job, ok := <-jobs:
                    if !ok {
                        return nil                    // producer closed the channel: done
                    }
                    if err := handle(ctx, job); err != nil {
                        return err
                    }
                }
            }
        })
    }
    return g.Wait()
}
```

- Fixed workers reading a channel: good for long-running consumers (queues, Kafka partitions) where you want stable concurrency and per-worker resources.

## Pipeline and Fan-In

```go
func generate(ctx context.Context, ids []string) <-chan string {
    out := make(chan string)
    go func() {
        defer close(out)                              // sender closes
        for _, id := range ids {
            select {
            case out <- id:
            case <-ctx.Done():                        // without this, an abandoned pipeline leaks
                return
            }
        }
    }()
    return out
}

func merge[T any](ctx context.Context, ins ...<-chan T) <-chan T {
    out := make(chan T)
    var wg sync.WaitGroup
    for _, in := range ins {
        wg.Go(func() {                                // 1.25
            for v := range in {
                select {
                case out <- v:
                case <-ctx.Done():
                    return
                }
            }
        })
    }
    go func() { wg.Wait(); close(out) }()             // close after all senders finish
    return out
}
```

- Each stage owns and closes its output channel; every send selects on `ctx.Done()` so downstream abandonment doesn't leak upstream goroutines.
- For in-memory lazy sequences without concurrency, **iterators** (`iter.Seq`, 1.23) are simpler than channel pipelines ([[05 - Go Slices Maps and Strings Internals]]).

---

## Limiting and Smoothing

| Need | Tool |
|---|---|
| Max concurrent operations | `errgroup.SetLimit`, buffered-channel semaphore, `golang.org/x/sync/semaphore` (weighted) |
| Max rate (req/s) | `golang.org/x/time/rate` token bucket: `limiter.Wait(ctx)` |
| Deduplicate concurrent identical calls (cache stampede) | **`singleflight.Group`**: one call per key in flight, others share the result |
| Timeouts | `context.WithTimeout` per call, overall deadline per request |
| Retries | Backoff with jitter, bounded attempts, respecting `ctx`; only idempotent operations ([[LLD/Foundations/11 - Idempotency and Retries]]) |

```go
var group singleflight.Group

func (c *Cache) Get(ctx context.Context, key string) (Value, error) {
    if v, ok := c.local.Get(key); ok {
        return v, nil
    }
    v, err, _ := group.Do(key, func() (any, error) {    // N concurrent misses → 1 backend call
        return c.backend.Load(ctx, key)                  // note: uses the first caller's ctx
    })
    if err != nil {
        return Value{}, err
    }
    return v.(Value), nil
}
```

---

## Goroutine Leaks

| Cause | Example |
|---|---|
| Send with no receiver | Early `return` in the collector leaves workers blocked on `ch <- result` |
| Receive with no sender/close | `for v := range ch` where nobody closes `ch` |
| Blocking without context | `conn.Read` or `<-ch` with no deadline or cancellation path |
| Forgotten `cancel` / tickers | `time.Ticker` never stopped, contexts never cancelled |
| HTTP bodies not closed | Transport goroutines and connections held ([[01 - Go net-http Server Model]]) |

### Finding them

- **Goroutine count** metric climbing over time (`runtime.NumGoroutine`, `/sched/goroutines` metrics).
- **Goroutine profile** (`/debug/pprof/goroutine?debug=2`): stacks grouped by where they're blocked.
- **`goroutineleak` profile** (experiment in 1.26, **GA in 1.27**, `/debug/pprof/goroutineleak`): uses the GC to find goroutines blocked on primitives unreachable from any runnable goroutine, i.e. leaks that can never wake up ([[12 - Go Diagnostics pprof and Tracing]]).
- In tests: **`go.uber.org/goleak`** (`goleak.VerifyTestMain(m)`) fails tests that leave goroutines behind; `testing/synctest` for time-dependent concurrency ([[11 - Go Testing and Benchmarks]]).

```go
// The classic leak and its fix: buffer the channel to the number of senders
func firstErr(ws []Work) error {
    ch := make(chan error, len(ws))      // unbuffered here would leak senders after an early return
    for _, w := range ws {
        go func() { ch <- w.Do() }()
    }
    for range ws {
        if err := <-ch; err != nil {
            return err                    // remaining senders can still complete their send
        }
    }
    return nil
}
```

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Not deriving from `r.Context()`** | Work continues after client disconnects | Always derive from the request context |
| **Forgetting `defer cancel()`** | Timer and context resources leak (`go vet` lostcancel) | Always defer cancel |
| **Work that must outlive the request uses the request ctx** | Cancelled mid-way | `context.WithoutCancel` + own timeout |
| **Unbounded goroutines per item** | Downstream overload, memory | `SetLimit`, semaphores, worker pools |
| **Sends without `select` on `ctx.Done()`** | Leaks when consumers stop | Every blocking op is cancellable |
| **Context values as a DI container** | Hidden dependencies, untyped lookups | Explicit parameters; values only for request metadata |
| **`singleflight` with the first caller's ctx** | Others fail when the first caller cancels | Detached context with its own timeout inside `Do` |
| **Retries ignoring ctx** | Retrying after the caller gave up | Check `ctx` between attempts |

---

## Interview Lines

> [!tip] Say it like this
> - "Context carries cancellation, deadlines and request metadata down the call tree; I pass it as the first parameter, derive from the request's context, and always `defer cancel()`."
> - "Cancellation is cooperative: only code that selects on `ctx.Done()` or uses context-aware APIs actually stops."
> - "For fan-out in a request I use `errgroup.WithContext` with `SetLimit`: bounded, and the first error cancels the rest."
> - "Every goroutine needs an exit path; leaks usually come from sends nobody receives or channels nobody closes. I watch goroutine counts, use the `goroutineleak` profile that went GA in 1.27, and run `goleak` in tests."
> - "`singleflight` collapses concurrent cache misses into one backend call."

## Related Notes

- [[07 - Go Concurrency Primitives]] · [[01 - Go Runtime and the GMP Scheduler]] · [[12 - Go Diagnostics pprof and Tracing]] · [[01 - Go net-http Server Model]]
- [[05 - asyncio Internals]] · [[LLD/Concurrency/08 - Executors and Thread Pools]] · [[01 - Concurrency Models Compared]] · [[Languages/README]]
