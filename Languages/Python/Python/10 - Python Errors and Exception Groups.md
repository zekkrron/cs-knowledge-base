---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Python Errors and Exception Groups

> [!abstract] What this is
> How Python reports and handles failure: the **exception hierarchy** (`BaseException` vs `Exception`, and why `KeyboardInterrupt` and `asyncio.CancelledError` sit outside `Exception`), **chaining** (`raise ... from`), notes (3.11), **`ExceptionGroup` and `except*`** (3.11) for concurrent failures, cleanup with `finally` and context managers (and 3.14's warning for `return` in `finally`), custom exception design, and logging errors properly. The decision this helps with: designing an error model for a service and handling failures from concurrent tasks without losing any of them. Design principles shared with Java are in [[LLD/Foundations/01 - Exception Handling]].

---

## Why It Matters

- `except Exception: pass` hides bugs; `except BaseException` (or a bare `except:`) breaks Ctrl-C, task cancellation and shutdown.
- With `TaskGroup` (3.11), several tasks can fail at once; exception groups are how Python reports all of them ([[05 - asyncio Internals]]).

---

## The Hierarchy

```
BaseException
├── BaseExceptionGroup
├── KeyboardInterrupt
├── SystemExit
├── GeneratorExit
├── asyncio.CancelledError          (BaseException since 3.8)
└── Exception
    ├── ExceptionGroup
    ├── ArithmeticError, LookupError (KeyError, IndexError), ValueError, TypeError
    ├── OSError (ConnectionError, TimeoutError, FileNotFoundError, ...)
    ├── RuntimeError, NotImplementedError, RecursionError
    └── your application exceptions
```

- Catch **`Exception`** (or narrower) in application code. `BaseException` subclasses are control flow: interrupting, exiting, cancelling, closing generators. Catching them must end in re-raising.
- `TimeoutError` is the built-in raised by `asyncio.timeout` (aliased since 3.11), and `socket.timeout` is an alias of it.

---

## Raising, Chaining and Notes

```python
class PaymentError(Exception):
    """Base class for payment failures."""

class CardDeclined(PaymentError):
    def __init__(self, reason: str, *, retryable: bool = False):
        super().__init__(f"card declined: {reason}")
        self.reason, self.retryable = reason, retryable

def charge(order):
    try:
        resp = gateway.charge(order.card_token, order.total)
    except httpx.HTTPStatusError as e:
        raise CardDeclined(e.response.json()["reason"]) from e      # explicit cause: __cause__
```

| Form | Traceback shows | Use |
|---|---|---|
| `raise New() from e` | "The above exception was the **direct cause** of ..." | Translating low-level errors into domain errors |
| `raise New()` inside `except` | "During handling of the above exception, **another exception occurred**" (`__context__`) | Usually accidental; add `from e` |
| `raise New() from None` | Only the new exception | Hiding irrelevant internals deliberately |
| `raise` (bare) | Original traceback | Re-raise after logging or cleanup |

- **`e.add_note("order_id=42")`** (3.11) attaches context that appears in the traceback without changing the exception type.
- Python favours **EAFP** ("easier to ask forgiveness"): `try: d[k] except KeyError:` is idiomatic, but keep `try` blocks **narrow** so they catch only the expected failure.
- **3.14** allows `except TimeoutError, ConnectionError:` without parentheses (PEP 758) when there's no `as`.

---

## Exception Groups and except* (3.11)

```python
async def refresh_all(ids: list[int]) -> None:
    try:
        async with asyncio.TaskGroup() as tg:
            for i in ids:
                tg.create_task(refresh(i))
    except* httpx.TimeoutException as group:            # handles every timeout in the group
        log.warning("timeouts: %d", len(group.exceptions))
    except* ValueError as group:
        for exc in group.exceptions:
            log.error("bad data", exc_info=exc)
    # other exception types in the group propagate as a (smaller) ExceptionGroup
```

- An **`ExceptionGroup`** carries several unrelated exceptions at once (concurrent tasks, retries, cleanup of multiple resources).
- **`except*`** matches **parts** of a group: each clause receives a sub-group of matching exceptions; unmatched ones keep propagating. Several `except*` clauses can run for one group.
- A plain `except ValueError` does **not** match a group containing a `ValueError`; once code uses `TaskGroup`, handle with `except*` or catch `ExceptionGroup` and inspect `.exceptions`.
- `group.split(predicate)` / `subgroup()` for programmatic handling; groups can nest.

---

## Cleanup

- **Context managers** first ([[08 - Decorators Generators and Context Managers]]); `try/finally` when there's no context manager.
- `finally` runs on success, exceptions, `return`, `break` and cancellation.
- **`return`/`break`/`continue` inside `finally` swallows the in-flight exception**; since **3.14** (PEP 765) this produces a `SyntaxWarning`. Don't do it.
- `else` on `try` runs only when no exception occurred: keeps the `try` block narrow.

```python
try:
    conn = pool.acquire()
except PoolTimeout:
    raise ServiceUnavailable("db busy") from None
else:
    try:
        return conn.execute(query)
    finally:
        pool.release(conn)
```

---

## Designing Errors for a Service

| Practice | Why |
|---|---|
| One **base exception per package/domain** (`PaymentError`) with specific subclasses | Callers can catch broadly or precisely |
| Carry **structured fields** (`reason`, `retryable`, ids), not just messages | Error mapping, metrics and retry logic without parsing strings |
| Translate third-party exceptions at the **boundary** (`from e`) | Callers don't depend on `httpx`/`psycopg` types |
| Map domain exceptions to HTTP/problem responses in **one** handler | Consistent API errors ([[07 - FastAPI Security and Middleware]], [[10 - Spring Validation Errors and API Design]]) |
| Return values (or `None`/result types) for **expected** outcomes on hot paths | Raising is relatively expensive; not-found is often not exceptional |
| Mark **retryable** vs permanent failures explicitly | Retry only what can succeed ([[LLD/Foundations/11 - Idempotency and Retries]]) |

### Logging errors

```python
try:
    process(order)
except PaymentError:
    log.exception("payment failed", extra={"order_id": order.id})   # logs at ERROR with traceback
    raise
```

- `log.exception` (inside `except`) or `exc_info=True` records the traceback; `log.error(str(e))` loses it.
- Log **once**, at the layer that handles the error; logging and re-raising at every layer floods logs with duplicates.
- Tracebacks reference frames and their locals: storing exceptions long-term (lists of errors in long-lived objects) keeps large objects alive ([[02 - Python Memory Management and GC]]).

---

## Warnings

- `warnings.warn("...", DeprecationWarning, stacklevel=2)` for deprecations; run tests with `-W error::DeprecationWarning` (or pytest `filterwarnings = error`) to catch them before upgrades ([[12 - Python Testing with pytest]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Bare `except:` / `except BaseException`** | Ctrl-C, cancellation and exit swallowed | Catch `Exception` or narrower; re-raise control-flow exceptions |
| **`except Exception: pass`** | Silent data corruption, invisible bugs | Handle specifically or log and re-raise |
| **Wide `try` blocks** | Catching errors from unrelated lines | Narrow `try`, use `else` |
| **Missing `from e`** | Confusing "during handling" tracebacks | Explicit chaining |
| **Plain `except` around `TaskGroup`** | Handler never matches; group propagates | `except*` or `except ExceptionGroup` |
| **`return` in `finally`** | Exceptions vanish (warning in 3.14) | Never exit `finally` early |
| **Logging at every layer** | Duplicate stack traces | Log where handled |
| **Errors as strings** | Fragile parsing of messages | Structured exception attributes |
| **Swallowing `CancelledError`** | Hung shutdowns and timeouts | Re-raise after cleanup |

---

## Interview Lines

> [!tip] Say it like this
> - "Application code catches `Exception` or narrower; `KeyboardInterrupt`, `SystemExit` and `CancelledError` are `BaseException`s, so they pass through and I re-raise them if I ever catch them."
> - "I translate low-level errors into domain exceptions with `raise ... from e`, so the cause stays in the traceback."
> - "Since 3.11 concurrent failures come back as an `ExceptionGroup`; `except*` handles each type in the group, and a plain `except` won't match it."
> - "I keep `try` blocks narrow, use context managers for cleanup, and never return from `finally`, which 3.14 now warns about."
> - "Exceptions carry structured fields like `retryable`, and one handler maps them to API responses."

## Related Notes

- [[05 - asyncio Internals]] · [[08 - Decorators Generators and Context Managers]] · [[12 - Python Testing with pytest]]
- [[LLD/Foundations/01 - Exception Handling]] · [[04 - Error Handling Compared]] · [[Languages/README]]
