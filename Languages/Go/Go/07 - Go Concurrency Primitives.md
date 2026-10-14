---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Concurrency Primitives

> [!abstract] What this is
> The building blocks of concurrent Go: **goroutines**, **channels** (unbuffered vs buffered, direction, **closing rules**, nil channels), **`select`** (multiplexing, timeouts, default), the **`sync`** package (`Mutex`, `RWMutex`, `WaitGroup` and `WaitGroup.Go` (1.25), `Once`/`OnceValue`, `Cond`, `Map`, `Pool`), **`sync/atomic`** typed atomics, and **`errgroup`**. Also the **Go memory model** (happens-before) and the **race detector**. The decision this helps with: choosing between channels and mutexes and using each without deadlocks, leaks or races. Patterns built on these are in [[08 - Go Concurrency Patterns and Context]].

---

## Why It Matters

- Go makes concurrency cheap to start and easy to get wrong: sends on closed channels panic, forgotten receivers leak goroutines, unsynchronised map access crashes the process, and data races corrupt state silently.
- "Channels or mutexes?" is a standard Go design question.

---

## Goroutines

- `go f(x)` starts `f` concurrently; arguments are evaluated immediately. Goroutines are cheap (2 KB stacks) but **not free**: each one you start must have a defined way to **finish** ([[01 - Go Runtime and the GMP Scheduler]]).
- No handle, no return value, no way to kill a goroutine from outside: communicate results through channels or shared state, and stop it with **context cancellation** ([[08 - Go Concurrency Patterns and Context]]).
- Since 1.22, loop variables are per-iteration, so `for _, x := range xs { go use(x) }` no longer shares one variable (in modules with `go ≥ 1.22`).

---

## Channels

```go
ch := make(chan Job)        // unbuffered: send blocks until a receiver takes it (synchronisation point)
buf := make(chan Job, 100)  // buffered: send blocks only when the buffer is full

func producer(out chan<- Job) { ... }   // send-only
func consumer(in <-chan Job) { ... }    // receive-only
```

| Operation | nil channel | Open channel | Closed channel |
|---|---|---|---|
| Send `ch <- v` | Blocks forever | Blocks until received / buffer space | **Panic** |
| Receive `<-ch` | Blocks forever | Blocks until a value | Returns **zero value, ok=false** immediately |
| `close(ch)` | **Panic** | Closes | **Panic** |

### Closing rules

- **Only the sender closes**, and only when no more values will be sent. Receivers never close. With multiple senders, close after all are done (a `WaitGroup` and a separate closer goroutine), or don't close and use context cancellation instead.
- Closing is a **broadcast**: every receiver blocked on the channel wakes up. That's why `close(done)` (or `ctx.Done()`) is the standard cancellation signal.
- `for v := range ch` loops until the channel is closed and drained; if nobody closes it, the loop (and goroutine) never ends.
- `v, ok := <-ch` distinguishes a real zero value from a closed channel.

### Buffered vs unbuffered

| Unbuffered | Buffered |
|---|---|
| Hand-off: sender knows the receiver has the value | Decouples bursts; sender doesn't know when it's processed |
| Natural back-pressure | Back-pressure only when full; a big buffer hides slow consumers and grows latency |
| Pick when: synchronisation, request/response | Pick when: smoothing bursts with a known bound, semaphores (`make(chan struct{}, n)`) |

---

## select

```go
select {
case job := <-jobs:
    handle(job)
case res := <-results:
    collect(res)
case <-ctx.Done():
    return ctx.Err()                      // cancellation or deadline
case <-time.After(5 * time.Second):       // timeout for this select (fine since 1.23: unreferenced timers are collected)
    return ErrTimeout
default:                                  // non-blocking: runs if nothing else is ready
}
```

- Waits on several channel operations; if several are ready, one is chosen **at random** (no priority).
- A `nil` channel case is never ready: set a channel variable to `nil` to disable that case dynamically (e.g. after its producer finished).
- `default` makes it non-blocking; a `select` with only `default` in a loop is a busy spin.
- For repeated timeouts use one `time.Timer`/`time.NewTimer` and `Reset`, or a context deadline, rather than `time.After` in a hot loop.

---

## The sync Package

| Primitive | Use | Traps |
|---|---|---|
| **`Mutex`** | Protect shared state | Not re-entrant (locking twice in one goroutine deadlocks); **never copy** after use (`go vet` copylocks); keep critical sections short, no I/O inside |
| **`RWMutex`** | Many readers, rare writers | Writer starvation of readers is prevented, but heavy writes make it slower than `Mutex`; benchmark |
| **`WaitGroup`** | Wait for a set of goroutines | `Add` before starting the goroutine; **`wg.Go(f)` (1.25)** does `Add`+`go`+`Done` correctly |
| **`Once`**, `OnceFunc`/`OnceValue`/`OnceValues` (1.21) | Lazy one-time init | A panic in `Once.Do` counts as done |
| `Cond` | Wait for a condition with a lock | Rarely needed; channels usually clearer |
| **`sync.Map`** | Keys written once and read many times, or disjoint key sets per goroutine | Slower than a mutex+map for general use; untyped |
| `Pool` | Reuse temporary objects | Items vanish at GC ([[02 - Go Memory and Escape Analysis]]) |

```go
type Cache struct {
    mu    sync.RWMutex
    items map[string]Item         // guarded by mu
}

func (c *Cache) Get(k string) (Item, bool) {
    c.mu.RLock()
    defer c.mu.RUnlock()
    it, ok := c.items[k]
    return it, ok
}

func (c *Cache) Set(k string, it Item) {
    c.mu.Lock()
    c.items[k] = it
    c.mu.Unlock()
}
```

- Put the mutex next to the fields it guards and document it; methods with pointer receivers.
- Hot shared maps can be **sharded** (N maps each with its own mutex, chosen by key hash) to cut contention.

### Atomics

```go
var requests atomic.Int64          // typed atomics (1.19): no misaligned 64-bit access issues
requests.Add(1)
n := requests.Load()

var cfg atomic.Pointer[Config]     // swap immutable config snapshots without locks
cfg.Store(newCfg)
current := cfg.Load()
```

- For counters, flags and pointer swaps of immutable data. Compound invariants across several variables need a mutex. `go fix` (1.27) has an `atomictypes` modernizer for old `atomic.AddInt64(&x, ...)` code.

---

## errgroup

```go
g, ctx := errgroup.WithContext(ctx)
g.SetLimit(8)                                  // bounded concurrency
for _, id := range ids {
    g.Go(func() error {
        return fetchAndStore(ctx, id)          // first error cancels ctx for the others
    })
}
if err := g.Wait(); err != nil {               // returns the first error
    return err
}
```

- `golang.org/x/sync/errgroup`: a `WaitGroup` with error propagation, context cancellation on first error, and `SetLimit`/`TryGo` for bounded concurrency. The default choice for fan-out work in request handlers ([[08 - Go Concurrency Patterns and Context]]).

---

## Channels or Mutexes?

| Use channels for | Use mutexes/atomics for |
|---|---|
| Passing ownership of data between goroutines | Protecting shared state (caches, counters, maps) |
| Pipelines, worker pools, fan-in/out | Short critical sections |
| Signalling (done, cancellation, events) | Performance-sensitive hot paths |
| Bounding concurrency (`chan struct{}` semaphore) | Simple invariants on a struct |

- "Don't communicate by sharing memory; share memory by communicating" is guidance, not a rule. A mutex around a map is often simpler and faster than a goroutine serving it through channels.

---

## Memory Model and the Race Detector

- The **Go memory model** defines **happens-before**: a send happens before the corresponding receive completes; a channel close happens before a receive of the zero value; `Unlock` happens before the next `Lock`; `Once.Do`'s function happens before any `Do` returns; atomics are sequentially consistent.
- Without such synchronisation, there's **no guarantee** another goroutine ever sees your write, or sees writes in order. Data races on multi-word values (interfaces, slices, strings) can even produce torn, invalid values.
- **`go test -race`** (and `go build -race` for staging) instruments memory accesses and reports races with both stacks. Run it in CI; it finds only races that execute, with ~5–10× CPU and memory overhead ([[11 - Go Testing and Benchmarks]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Send on a closed channel / double close** | Panic | Only the (single) sender closes; coordinate multiple senders |
| **Nobody receives** | Sender goroutine leaks forever | Buffered result channels sized to senders, or `select` on `ctx.Done()` |
| **Range over a never-closed channel** | Goroutine never exits | Sender closes when done |
| **`wg.Add` inside the goroutine** | `Wait` returns early | `Add` before `go`, or `wg.Go` (1.25) |
| **Copied mutex** | Broken locking | Pointer receivers; `go vet` |
| **Locking twice / lock ordering** | Deadlock (`fatal error: all goroutines are asleep` only if *all* are blocked) | Consistent order; no re-entrancy; short sections |
| **Unsynchronised map/slice access** | Crash or corrupted data | Mutexes; `-race` in CI |
| **Huge buffered channels as queues** | Hidden backlog, memory, lost work on restart | Small buffers; real queues for durability |
| **`sync.Map` as a default** | Slower, untyped code | Mutex + map unless the access pattern fits |

---

## Interview Lines

> [!tip] Say it like this
> - "Unbuffered channels are a hand-off and give back-pressure; buffered channels absorb bounded bursts. Only the sender closes, closing broadcasts to all receivers, and sending on a closed channel panics."
> - "`select` waits on several channel operations, picks randomly among ready ones, and a nil channel case is disabled, which is handy for turning cases off."
> - "I use mutexes for shared state and channels for passing ownership and signalling; a mutex around a map is often simpler than a goroutine serving it."
> - "`errgroup` with `SetLimit` is my default for bounded fan-out: first error cancels the context for the rest."
> - "Without happens-before synchronisation there's no visibility guarantee, so `-race` runs in CI."

## Related Notes

- [[08 - Go Concurrency Patterns and Context]] · [[01 - Go Runtime and the GMP Scheduler]] · [[05 - Go Slices Maps and Strings Internals]] · [[11 - Go Testing and Benchmarks]]
- [[LLD/Concurrency/03 - Java Memory Model]] · [[LLD/Concurrency/10 - Synchronizers]] · [[01 - Concurrency Models Compared]] · [[Languages/README]]
