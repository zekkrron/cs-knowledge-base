---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Decorators Generators and Context Managers

> [!abstract] What this is
> Three features built on Python's function and iteration machinery: **closures and decorators** (late binding, `functools.wraps`, parametrised and async-aware decorators, caching decorators), **iterators and generators** (lazy evaluation, `yield from`, `send`/`close`, one-shot exhaustion, async generators), and **context managers** (`with`, `contextlib.contextmanager`, `ExitStack`, async context managers). The decision this helps with: writing reusable cross-cutting code and streaming pipelines correctly, and recognising the classic traps in review.

---

## Why It Matters

- Frameworks are decorators and context managers all the way down: FastAPI routes and dependencies, pytest fixtures, Flask routes, `@lru_cache`, `@retry`, database sessions and transactions.
- Generators make streaming possible (reading huge files, SSE/LLM token streams, batched DB reads) with constant memory; misused, they're silently exhausted or leak resources.

---

## Closures

```python
def make_counter():
    count = 0
    def increment():
        nonlocal count            # rebind the enclosing variable (stored in a "cell")
        count += 1
        return count
    return increment
```

- A **closure** captures **variables**, not values: inner functions see the variable's value **at call time** (late binding).

```python
handlers = [lambda: i for i in range(3)]
[h() for h in handlers]                     # [2, 2, 2]  — all see the final i

handlers = [lambda i=i: i for i in range(3)]
[h() for h in handlers]                     # [0, 1, 2]  — default argument binds the value now
```

- `nonlocal` rebinds an enclosing variable; `global` a module variable. Assigning without them creates a new local (and `UnboundLocalError` if read first).

---

## Decorators

A decorator is a callable that takes a function and returns a replacement: `@d def f` means `f = d(f)`, applied at **definition time**.

```python
import functools, time, logging

def timed(fn):
    @functools.wraps(fn)                      # keep __name__, __doc__, __wrapped__, signature for introspection
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        try:
            return fn(*args, **kwargs)
        finally:
            logging.info("%s took %.1f ms", fn.__qualname__, (time.perf_counter() - start) * 1000)
    return wrapper
```

### Parametrised and async-aware decorators

```python
import asyncio, inspect

def retry(times: int = 3, delay: float = 0.2, on: tuple[type[Exception], ...] = (ConnectionError,)):
    def decorate(fn):
        if inspect.iscoroutinefunction(fn):
            @functools.wraps(fn)
            async def async_wrapper(*args, **kwargs):
                for attempt in range(times):
                    try:
                        return await fn(*args, **kwargs)
                    except on:
                        if attempt == times - 1:
                            raise
                        await asyncio.sleep(delay * 2 ** attempt)      # never time.sleep in async code
            return async_wrapper

        @functools.wraps(fn)
        def wrapper(*args, **kwargs):
            for attempt in range(times):
                try:
                    return fn(*args, **kwargs)
                except on:
                    if attempt == times - 1:
                        raise
                    time.sleep(delay * 2 ** attempt)
        return wrapper
    return decorate

@retry(times=3, on=(httpx.TransportError,))
async def fetch_price(sku: str) -> Price: ...
```

- `@retry(...)` with arguments is a **decorator factory**: it returns the decorator.
- A sync wrapper around an `async def` returns an un-awaited coroutine and breaks timing/retry logic: detect coroutine functions and wrap appropriately.
- **Order**: decorators apply bottom-up. `@app.get(...)` above `@timed` registers the timed function; reversed, the router registers the raw one.
- Without `functools.wraps`, frameworks that inspect signatures (FastAPI, pytest, Typer) see `(*args, **kwargs)` and break ([[03 - FastAPI Dependency Injection]]).
- For type checkers, annotate decorators with `ParamSpec` so the wrapped signature is preserved ([[09 - Python Typing]]).

### Caching decorators

| Decorator | Behaviour | Trap |
|---|---|---|
| `functools.lru_cache(maxsize=128)` | Memoise by hashable arguments | On **methods** it caches `self`, keeping instances alive; `maxsize=None` is unbounded |
| `functools.cache` (3.9) | Unbounded `lru_cache` | Memory growth with distinct arguments |
| `functools.cached_property` | Compute once per instance | Not thread-safe since 3.12 (may compute twice) |
| Async caching | `lru_cache` on `async def` caches the **coroutine object**, which can be awaited only once | Use a library (`async-lru`, aiocache) or cache results explicitly |

---

## Iterators and Generators

- **Iterable**: has `__iter__` returning an iterator (lists, dicts, files). **Iterator**: has `__next__` and raises `StopIteration` when done; iterating it consumes it.
- A **generator function** (contains `yield`) returns a generator: a lazy iterator whose body runs up to the next `yield` each time a value is requested.

```python
def read_events(path: str, batch: int = 1000):
    with open(path, encoding="utf-8") as f:          # the file stays open while the generator is alive
        chunk = []
        for line in f:
            chunk.append(json.loads(line))
            if len(chunk) == batch:
                yield chunk
                chunk = []
        if chunk:
            yield chunk

for events in read_events("events.jsonl"):           # constant memory for any file size
    store(events)
```

- **Generator expressions** `(f(x) for x in xs)` are lazy; list comprehensions are eager. Pipelines of generators process one item at a time end to end.
- `itertools` (`islice`, `chain`, `groupby`, `batched` (3.12), `tee`, `pairwise`) composes lazily.
- **`yield from sub`** delegates to a sub-generator (including `send`/`throw`), and returns its return value.
- **`send`/`throw`/`close`**: generators are coroutines in the old sense; `close()` raises `GeneratorExit` at the paused `yield`, running `finally` blocks.

### Traps

- **One-shot**: a generator can be consumed once. Passing it to two functions (or calling `list()` twice) silently gives the second consumer nothing.
- **Deferred execution**: errors and side effects happen when iterated, not when the generator is created; exceptions inside appear at the consumer.
- **Resource lifetime**: a generator holding a file or DB cursor keeps it open until exhausted, closed or garbage-collected. Abandoned generators in long-lived objects leak resources; wrap with `contextlib.closing` or consume fully.
- **Async generators** (`async def` + `yield`) are consumed with `async for`; close them explicitly (`contextlib.aclosing`, 3.10) because their cleanup needs the loop. Streaming responses in FastAPI are async generators: handle client disconnects ([[06 - FastAPI Streaming SSE and WebSockets]]).

---

## Context Managers

```python
with open(path) as f, db.transaction():        # __enter__ on entry; __exit__ always runs, even on exceptions
    ...
```

- `__exit__(exc_type, exc, tb)` runs on success, exception or `return`. Returning **`True` suppresses** the exception: a footgun if done accidentally.

### `contextlib`

```python
from contextlib import contextmanager, asynccontextmanager, ExitStack, suppress

@contextmanager
def timer(name: str):
    start = time.perf_counter()
    try:
        yield                                   # body of the with-block runs here
    finally:                                    # without try/finally, an exception skips the cleanup
        metrics.observe(name, time.perf_counter() - start)

@asynccontextmanager
async def lifespan(app):                        # FastAPI lifespan pattern
    app.state.http = httpx.AsyncClient(timeout=5)
    try:
        yield
    finally:
        await app.state.http.aclose()

with ExitStack() as stack:                      # a dynamic number of resources
    files = [stack.enter_context(open(p)) for p in paths]

with suppress(FileNotFoundError):
    os.remove(tmp_path)
```

| Tool | Use |
|---|---|
| `@contextmanager` / `@asynccontextmanager` | Write a context manager as a generator: setup, `yield`, cleanup in `finally` |
| `ExitStack` / `AsyncExitStack` | Variable numbers of resources, conditional cleanup, callbacks |
| `suppress(...)` | Ignore specific expected exceptions |
| `nullcontext()` | Optional context manager placeholder |
| `closing(obj)` / `aclosing(obj)` | Call `close()` / `aclose()` on exit |
| `chdir` (3.11), `redirect_stdout` | Scoped global changes (not thread-safe) |

- Context managers are the Python form of try/finally for resources: files, locks, transactions, DB sessions, spans, temporary state. Prefer them over manual `close()` calls ([[10 - Python Errors and Exception Groups]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Late binding in loops** | All closures see the last value | Default-argument binding or `functools.partial` |
| **Decorator without `wraps`** | Broken introspection, signatures, docs | `functools.wraps` |
| **Sync decorator on async function** | Timing/retries apply to coroutine creation, not execution | Detect coroutine functions; separate async wrapper |
| **`time.sleep` in async retry** | Event loop blocked | `await asyncio.sleep` |
| **`lru_cache` on methods / unbounded caches** | Memory leaks, instances kept alive | Bounded caches; cache at module level keyed by IDs |
| **Consuming a generator twice** | Second pass is empty | Materialise once (`list`) or recreate |
| **Abandoned generators holding resources** | Open files/cursors | `closing`/`aclosing`; consume fully |
| **`@contextmanager` without try/finally** | Cleanup skipped on exceptions | Always `try: yield finally: cleanup` |
| **`__exit__` returning truthy by accident** | Exceptions silently swallowed | Return `None`/`False` unless suppression is intended |

---

## Interview Lines

> [!tip] Say it like this
> - "A decorator is just a function that takes a function and returns a replacement at definition time; I always use `functools.wraps` so frameworks can still see the signature."
> - "Closures bind variables, not values, so lambdas created in a loop all see the last value unless I bind it with a default argument."
> - "Generators are lazy, one-shot iterators; they make streaming pipelines constant-memory, but they hold their resources open until exhausted or closed."
> - "With `@contextmanager` the cleanup must sit in a `finally` around the `yield`, or an exception skips it."
> - "`lru_cache` on a method caches `self` and leaks instances; on an async function it caches a coroutine that can only be awaited once."

## Related Notes

- [[07 - Python Data Model]] · [[05 - asyncio Internals]] · [[09 - Python Typing]] · [[10 - Python Errors and Exception Groups]]
- [[03 - FastAPI Dependency Injection]] · [[06 - FastAPI Streaming SSE and WebSockets]] · [[08 - Java Streams Optional and Functional Style]] · [[Languages/README]]
