---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Errors and Panics

> [!abstract] What this is
> Go's error model: **errors are values** returned alongside results, wrapped with context using **`%w`** (1.13) and inspected with **`errors.Is`**, **`errors.As`** and **`errors.AsType`** (1.26); **sentinel errors vs typed errors vs opaque errors**; `errors.Join` (1.20) for multiple errors; where to add context and where to log; and **`panic`/`recover`** for programmer errors and the boundaries where recovering is right (HTTP handlers, goroutines). The decision this helps with: designing error handling that callers can act on and that produces useful logs, without exceptions. A cross-language comparison is in [[04 - Error Handling Compared]].

---

## Why It Matters

- Go code is a large fraction `if err != nil`; doing it well (context, wrapping, checking with `Is`/`As`) is what makes failures debuggable. Doing it badly produces "EOF" with no context, or string-matched errors that break when messages change.
- An unrecovered panic in **any goroutine** crashes the whole process; HTTP servers recover per request, but your own goroutines don't.

---

## Errors Are Values

```go
type error interface{ Error() string }

func LoadOrder(ctx context.Context, id string) (Order, error) {
    row := db.QueryRowContext(ctx, `SELECT ... FROM orders WHERE id = $1`, id)
    var o Order
    if err := row.Scan(&o.ID, &o.Total); err != nil {
        if errors.Is(err, sql.ErrNoRows) {
            return Order{}, ErrNotFound                          // translate to a domain sentinel
        }
        return Order{}, fmt.Errorf("load order %s: %w", id, err) // add context, keep the cause
    }
    return o, nil
}
```

- Return the error as the **last** result; on error, other results are zero values the caller must ignore.
- Handle errors **once**: either handle (retry, fallback, translate) or return with context. Logging *and* returning causes duplicate logs at every layer.
- Context messages are lowercase, no punctuation, and describe what *this* function was doing: `load order 42: query: connection refused`.

---

## Wrapping and Inspecting

| Function | Use |
|---|---|
| `fmt.Errorf("...: %w", err)` | Wrap with context; multiple `%w` allowed (1.20) |
| **`errors.Is(err, target)`** | Is any error in the chain **equal to** a sentinel (or matches via an `Is` method)? |
| **`errors.As(err, &target)`** | Is any error in the chain **of a type**? Extracts it |
| **`errors.AsType[T](err)`** (1.26) | Generic `As`: `if e, ok := errors.AsType[*ValidationError](err); ok` |
| `errors.Join(errs...)` (1.20) | Combine multiple errors; `Is`/`As` search all of them |
| `errors.Unwrap(err)` | One level; rarely needed directly |

- `%v` instead of `%w` adds context **without** exposing the cause (callers can't `Is`/`As` through it): use it deliberately to hide implementation details at API boundaries.
- `==` comparison fails for wrapped errors; always use `errors.Is`.
- 1.27 `go vet` warns when wrapping a pointer to a type whose value type implements `error` (`%w` with `*E` where `E` is the error type), a latent `Is`/`As` mismatch.

---

## Three Kinds of Errors

```go
// 1. Sentinel: a package-level value for a condition callers check
var ErrNotFound = errors.New("order not found")

// 2. Typed: a struct carrying details callers act on
type ValidationError struct {
    Field, Reason string
}
func (e *ValidationError) Error() string { return e.Field + ": " + e.Reason }

// 3. Opaque: callers only know "it failed"; behaviour exposed via methods/interfaces
type temporary interface{ Temporary() bool }
```

| Kind | Pros | Cons | Pick when |
|---|---|---|---|
| **Sentinel** | Simple `errors.Is` checks | Part of your API forever; carries no data | Well-known conditions: not found, closed, EOF |
| **Typed** | Structured details (`errors.As`) | Callers depend on the type | Validation failures, HTTP status-bearing errors, retry hints |
| **Opaque** + behaviour | Minimal coupling | Less information | Library internals; "is it retryable?" checks |

- Map domain errors to transport codes in **one place** (HTTP middleware/handler helper, gRPC interceptor) ([[04 - Gin Binding Validation and Errors]]).
- Don't match on `err.Error()` strings: messages change (e.g. JSON errors on the 1.27 v2 engine).

---

## Panic and Recover

```go
func safeGo(ctx context.Context, f func(context.Context) error) {
    go func() {
        defer func() {
            if r := recover(); r != nil {
                slog.Error("goroutine panic", "panic", r, "stack", string(debug.Stack()))
            }
        }()
        if err := f(ctx); err != nil {
            slog.Error("goroutine failed", "err", err)
        }
    }()
}
```

| Use `panic` for | Don't use it for |
|---|---|
| Programmer errors and impossible states (nil map write, index out of range are runtime panics) | Expected failures: I/O, validation, not found |
| Failed initialisation that makes the program unusable (`regexp.MustCompile` at package level) | Control flow across APIs |

- `recover()` works only inside a **deferred** function in the **same goroutine** that panicked. A panic in a goroutine you started kills the process even if `main` has a recover.
- **Recover at boundaries**: `net/http` recovers per request (logs and closes the connection); Gin's `Recovery` middleware returns 500 ([[03 - Gin Middleware]]); worker goroutines need their own recover (or use `errgroup` and return errors) ([[08 - Go Concurrency Patterns and Context]]).
- Some failures can't be recovered: **`fatal error: concurrent map writes`**, out of memory, stack overflow beyond the limit, `runtime.Goexit` semantics.
- `defer` runs on return and panic, in LIFO order; use it for cleanup (`defer rows.Close()`, `defer mu.Unlock()`). Check errors from `Close` on writers (files, buffered writers) because that's where write errors surface:

```go
func writeFile(path string, data []byte) (err error) {
    f, err := os.Create(path)
    if err != nil {
        return err
    }
    defer func() {
        if cerr := f.Close(); cerr != nil && err == nil {
            err = cerr                     // named result: report Close failures
        }
    }()
    _, err = f.Write(data)
    return err
}
```

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Ignored errors (`_ =` or unchecked)** | Silent data loss | `errcheck`/linters; handle every error |
| **No context when returning** | "EOF" or "connection refused" with no clue where | Wrap with `fmt.Errorf("doing X: %w", err)` |
| **Log and return at every layer** | Duplicate, noisy logs | Log once at the boundary that handles it |
| **`==` on wrapped errors / string matching** | Checks silently fail | `errors.Is` / `errors.As` / `AsType` |
| **Typed nil returned as `error`** | Non-nil error on success | Return literal `nil` ([[04 - Go Types Interfaces and Embedding]]) |
| **Panic in an unmanaged goroutine** | Whole process crashes | Recover in goroutine wrappers; `errgroup` |
| **Panics for expected failures** | Fragile, unreadable control flow | Return errors |
| **Ignoring `Close` errors on writes** | Truncated files reported as success | Check `Close` for writers |

---

## Interview Lines

> [!tip] Say it like this
> - "Errors are values: I return them with context using `%w` and check them with `errors.Is` for sentinels and `errors.As` or 1.26's `errors.AsType` for typed errors, never with `==` or string matching."
> - "Each error is handled once: either handled or wrapped and returned; logging happens at the boundary."
> - "Sentinels for well-known conditions, typed errors when callers need details, `%v` instead of `%w` when I want to hide the cause."
> - "Panics are for programmer errors; `recover` only works in a deferred call in the same goroutine, so every goroutine I start gets its own recover or reports errors through `errgroup`."
> - "Concurrent map writes are a fatal error, not a panic, so they can't be recovered."

## Related Notes

- [[04 - Go Types Interfaces and Embedding]] · [[08 - Go Concurrency Patterns and Context]] · [[04 - Gin Binding Validation and Errors]] · [[03 - Gin Middleware]]
- [[10 - Python Errors and Exception Groups]] · [[LLD/Foundations/01 - Exception Handling]] · [[04 - Error Handling Compared]] · [[Languages/README]]
