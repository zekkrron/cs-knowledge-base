---
tags: [languages/cross-language, status/draft]
created: 2026-10-14
---
# Error Handling Compared

> [!abstract] What this is
> How Java, Go and Python model failure: Java's **checked and unchecked exceptions**, Go's **errors as values** with wrapping (`%w`, `errors.Is/As/AsType`) and `panic` for programmer errors, and Python's **exceptions** with chaining and **exception groups** (3.11). Compares propagation, adding context, classifying errors, concurrent failures, cleanup (try-with-resources, `defer`, context managers), performance, and how each maps errors to HTTP responses in Spring, Gin and FastAPI. The decision this helps with: applying the same error-design principles idiomatically in each language.

---

## Why It Matters

- Interviewers ask "checked vs unchecked exceptions?", "why does Go return errors?", "what's an exception group?". The deeper question is the same in all three: how do failures carry context, get classified, and reach the right handler exactly once?

---

## At a Glance

| | Java | Go | Python |
|---|---|---|---|
| Mechanism | Exceptions (`throw`/`catch`) | **Return values** (`value, err`) | Exceptions (`raise`/`except`) |
| Compiler enforcement | **Checked exceptions** must be declared or handled | `error` results must be handled by convention (linters: `errcheck`) | None (type checkers don't track exceptions) |
| Propagation | Automatic up the stack | **Explicit** `if err != nil { return ... }` | Automatic |
| Adding context | Wrap: `new ServiceException("...", cause)` | `fmt.Errorf("doing X: %w", err)` | `raise DomainError(...) from e`, `add_note()` (3.11) |
| Classifying | `catch (SpecificException e)`, hierarchy | `errors.Is` (sentinels), `errors.As`/`AsType` (types) | `except SpecificError`, hierarchy |
| Multiple errors | Suppressed exceptions (try-with-resources) | `errors.Join` (1.20) | **`ExceptionGroup` + `except*`** (3.11) |
| Programmer errors | `RuntimeException`s (NPE, IllegalState) | **`panic`** (recover at boundaries) | Exceptions (`TypeError`, `AssertionError`) |
| Control-flow exceptions | `InterruptedException` (checked) | — | `KeyboardInterrupt`, `SystemExit`, `CancelledError` are `BaseException` |
| Cleanup | `try-with-resources`, `finally` | `defer` | `with` (context managers), `finally` |
| Cost of failure path | Stack trace capture is expensive | Cheap (a value) | Raising is relatively expensive |

---

## Java

```java
public Order load(String id) {
    try {
        return repo.findById(id).orElseThrow(() -> new OrderNotFound(id));
    } catch (DataAccessException e) {
        throw new OrderLoadFailed(id, e);            // translate + keep the cause
    }
}
```

- **Checked** exceptions (`IOException`) force handling but leak implementation details through signatures and don't compose with lambdas; modern frameworks (Spring) use **unchecked** exceptions almost exclusively ([[LLD/Foundations/01 - Exception Handling]]).
- Spring rolls back transactions only for unchecked exceptions by default ([[06 - Spring Transactions]]).
- Expected outcomes can be modelled as **sealed result types** with pattern-matching `switch` instead of exceptions ([[06 - Modern Java Language Features]]).
- HTTP mapping: `@RestControllerAdvice` + Problem Details ([[10 - Spring Validation Errors and API Design]]).

## Go

```go
func (s *Service) Load(ctx context.Context, id string) (Order, error) {
    o, err := s.store.Get(ctx, id)
    if errors.Is(err, store.ErrNoRows) {
        return Order{}, ErrNotFound
    }
    if err != nil {
        return Order{}, fmt.Errorf("load order %s: %w", id, err)
    }
    return o, nil
}
```

- Every failure point is visible in the code; the cost is verbosity and the discipline of handling each error once ([[06 - Go Errors and Panics]]).
- Traps: the **typed-nil interface** (`err != nil` for a nil pointer) ([[04 - Go Types Interfaces and Embedding]]), string-matching messages, panics in goroutines crashing the process.
- HTTP mapping: `c.Error` + one error middleware in Gin ([[04 - Gin Binding Validation and Errors]]).

## Python

```python
async def load(order_id: str) -> Order:
    try:
        return await repo.get(order_id)
    except NoResultFound:
        raise OrderNotFound(order_id) from None
    except asyncpg.PostgresError as e:
        raise OrderLoadFailed(order_id) from e
```

- EAFP style ("ask forgiveness"), narrow `try` blocks, never bare `except:` (it swallows `CancelledError` and `KeyboardInterrupt`) ([[10 - Python Errors and Exception Groups]]).
- `TaskGroup` failures arrive as `ExceptionGroup`; `except*` handles each type.
- HTTP mapping: FastAPI exception handlers ([[07 - FastAPI Security and Middleware]]).

---

## Shared Principles, Three Syntaxes

| Principle | Java | Go | Python |
|---|---|---|---|
| Translate low-level errors at boundaries | Wrap in domain exception with cause | `%w` with context, or a domain sentinel/type | `raise Domain(...) from e` |
| Classify by type/identity, not message | `catch` specific types | `errors.Is`/`As` | `except` specific types |
| Handle once; log once | Log in the advice/handler | Log in the middleware/top-level | Log in the handler; `log.exception` |
| Carry structured data | Exception fields | Typed error fields | Exception attributes |
| Mark retryability | `RetryableException` subtypes or flags | Typed errors / behaviour interfaces | Exception attributes |
| One mapping to HTTP | `@RestControllerAdvice` | Error middleware | `@app.exception_handler` |
| Concurrent failures | `StructuredTaskScope` (preview), `CompletableFuture` | `errgroup` (first error), `errors.Join` | `ExceptionGroup`/`except*` |
| Programmer bugs crash loudly | Unchecked exceptions → 500 + alert | `panic` → recovered at boundary → 500 | Uncaught exception → 500 |

---

## Failure Modes

| Failure | Java | Go | Python |
|---|---|---|---|
| Swallowed errors | Empty `catch` | `_ = f()` / unchecked `err` | `except Exception: pass` |
| Lost cause | New exception without cause | `%v` instead of `%w` (when the cause matters) | Missing `from e` |
| Wrong classification | Catching `Exception` too broadly | `==` on wrapped errors | Catching `BaseException` |
| Duplicate logs | Log and rethrow at each layer | Log and return at each layer | Same |
| Crash from concurrency | Uncaught exception in executor lost | Panic in a goroutine kills the process | Unretrieved task exceptions |

---

## Interview Lines

> [!tip] Say it like this
> - "Java propagates exceptions automatically and I translate them at layer boundaries with the cause attached; frameworks favour unchecked exceptions."
> - "Go makes errors values: explicit propagation with `%w` context and `errors.Is`/`As` to classify, with panics only for programmer errors, recovered at goroutine and request boundaries."
> - "Python uses exceptions with `raise ... from` chaining, and since 3.11 exception groups with `except*` report concurrent failures."
> - "The principles are the same in all three: translate at boundaries, classify by type not message, carry structured data, handle and log once, and map to HTTP in one place."

## Related Notes

- [[06 - Go Errors and Panics]] · [[10 - Python Errors and Exception Groups]] · [[LLD/Foundations/01 - Exception Handling]] · [[10 - Spring Validation Errors and API Design]]
- [[04 - Gin Binding Validation and Errors]] · [[07 - FastAPI Security and Middleware]] · [[Languages/README]]
