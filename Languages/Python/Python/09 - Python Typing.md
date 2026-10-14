---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Python Typing

> [!abstract] What this is
> Python's **gradual type system**: annotations that the interpreter does not enforce but **type checkers** (mypy, pyright, and the newer Rust-based Pyrefly and ty) verify, plus libraries (Pydantic, FastAPI) that read them at **runtime**. Covers modern syntax by version (`X | Y` 3.10, PEP 695 generics 3.12, `TypeIs` 3.13, deferred annotations 3.14), generics and variance, **`Protocol`** vs ABCs, `TypedDict`, `Literal`, `overload`, `ParamSpec`, `Annotated`, and how to choose between **dataclasses, attrs, Pydantic, `TypedDict` and `NamedTuple`**. The decision this helps with: typing a codebase so the checker catches real bugs, and picking the right data container for each boundary.

---

## Why It Matters

- Type checking catches whole classes of bugs before runtime (`None` handling, wrong argument types, missing awaits) and makes large Python codebases refactorable.
- FastAPI and Pydantic use type hints as the **runtime contract**: request parsing, validation and OpenAPI docs come from annotations ([[04 - Pydantic v2 Deep Dive]]).

---

## Gradual Typing in One Picture

| Fact | Consequence |
|---|---|
| Annotations are **not enforced** by CPython | `def f(x: int)` accepts `"a"` at runtime; only checkers or validators object |
| Unannotated code is `Any` to checkers | Adopt incrementally, module by module, tightening strictness over time |
| `Any` is compatible with everything in both directions | `Any` silently switches checking off; prefer `object` (accepts anything, allows nothing without narrowing) |
| Annotations are **evaluated lazily** since 3.14 (PEP 649/749) | Forward references work without quotes; `annotationlib.get_annotations()` reads them; `from __future__ import annotations` is no longer needed |

### Checkers

| Checker | Notes |
|---|---|
| **mypy** | Reference implementation, plugin system (Django, Pydantic, SQLAlchemy plugins) |
| **pyright** | Fast, powers Pylance in VS Code, strict defaults, good inference |
| **Pyrefly** (Meta) | Rust-based, fast; **1.0 stable in May 2026** |
| **ty** (Astral) | Rust-based, very fast incremental checking; still in **beta** (0.0.x) as of Oct 2026 |

- Run one in CI with a pinned version and a config (`[tool.mypy]` / `[tool.pyright]` in `pyproject.toml`); enable strict options for new modules (`disallow_untyped_defs`, `strict = true`).
- Suppress precisely: `# type: ignore[arg-type]` with the error code, not bare ignores.
- Third-party types: packages ship `py.typed`; otherwise stub packages (`types-requests`, `pandas-stubs`).

---

## Syntax by Version

```python
# 3.9+: built-in generics        3.10+: unions with |
def find(ids: list[int], cache: dict[int, str] | None = None) -> str | None: ...

# 3.12+: PEP 695 type parameters and type aliases
def first[T](items: list[T]) -> T:
    return items[0]

class Repository[T, ID]:
    def get(self, id: ID) -> T | None: ...

type JsonValue = str | int | float | bool | None | list[JsonValue] | dict[str, JsonValue]

# 3.13+: type parameter defaults (PEP 696), TypeIs narrowing (PEP 742)
from typing import TypeIs

def is_str_list(xs: list[object]) -> TypeIs[list[str]]:
    return all(isinstance(x, str) for x in xs)
```

| Feature | Version | Use |
|---|---|---|
| `Optional[X]` = `X | None` | 3.10 syntax | Must be narrowed (`if x is not None`) before use |
| `Literal["asc", "desc"]` | 3.8 | Closed sets of values |
| `Final`, `@final` | 3.8 | Constants, non-overridable methods |
| `TypedDict` (`Required`, `NotRequired` 3.11, `ReadOnly` 3.13) | 3.8+ | Typed dict shapes (JSON payloads) |
| `Self` | 3.11 | Fluent methods, alternative constructors |
| `Never` / `assert_never` | 3.11 | Exhaustiveness checks |
| `@override` | 3.12 | Catch overrides of non-existent methods |
| `TypeIs` | 3.13 | Precise user-defined narrowing (better than `TypeGuard`) |

### Exhaustiveness

```python
from typing import assert_never, Literal

Status = Literal["pending", "paid", "refunded"]

def label(s: Status) -> str:
    match s:
        case "pending":  return "Awaiting payment"
        case "paid":     return "Paid"
        case "refunded": return "Refunded"
        case _:          assert_never(s)     # checker errors if a new status isn't handled
```

---

## Generics and Variance

- Type parameters are **invariant** by default for mutable containers: `list[Dog]` is **not** a `list[Animal]` (you could append a `Cat`). Read-only views are covariant: accept `Sequence[Animal]`, `Mapping[str, Animal]`, `Iterable[Animal]`.
- Rule of thumb for parameters: accept the **most general** abstract type you need (`Iterable`, `Sequence`, `Mapping`); return **concrete** types.
- With PEP 695 syntax, variance is **inferred**; with old `TypeVar`s it's declared (`covariant=True`).
- Bounded and constrained parameters: `def max_by[T: Comparable](...)`, `def concat[S: (str, bytes)](a: S, b: S) -> S`.

---

## Protocols vs ABCs

```python
from typing import Protocol

class SupportsClose(Protocol):
    def close(self) -> None: ...

def shutdown(resources: list[SupportsClose]) -> None:   # any object with close() fits, no inheritance needed
    for r in resources:
        r.close()
```

| | `Protocol` (structural) | `abc.ABC` (nominal) |
|---|---|---|
| Conformance | Has the right methods/attributes | Explicitly subclasses / registers |
| Checked by | Type checker (and `isinstance` only with `@runtime_checkable`, shallowly) | Runtime: abstract methods prevent instantiation |
| Pick when | Duck-typed interfaces, third-party types you can't change, test fakes | Shared base behaviour, runtime enforcement, plugin bases |

- Protocols are Python's equivalent of Go's implicit interfaces ([[04 - Go Types Interfaces and Embedding]]).

---

## Typing Functions and Decorators

```python
from collections.abc import Callable, Awaitable
from typing import overload, ParamSpec, TypeVar
import functools

P = ParamSpec("P")
R = TypeVar("R")

def logged(fn: Callable[P, R]) -> Callable[P, R]:          # preserves the wrapped signature for checkers
    @functools.wraps(fn)
    def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
        return fn(*args, **kwargs)
    return wrapper

@overload
def get(key: str, default: None = None) -> str | None: ...
@overload
def get(key: str, default: str) -> str: ...
def get(key: str, default: str | None = None) -> str | None: ...
```

- `Callable[[int], str]`, `Awaitable[T]`, `Coroutine`; `ParamSpec` for decorators ([[08 - Decorators Generators and Context Managers]]); `@overload` when the return type depends on arguments.
- **`Annotated[T, metadata]`** attaches metadata that checkers ignore and libraries read: FastAPI's `Annotated[int, Query(ge=1)]`, `Annotated[Session, Depends(get_session)]`, Pydantic's constraints ([[03 - FastAPI Dependency Injection]]).
- `cast(T, x)` tells the checker to trust you, with no runtime effect: a code smell when common.

---

## Data Containers: Which One?

| Container | Validation | Mutability | Speed | Pick when |
|---|---|---|---|---|
| **`@dataclass`** | None (types are hints) | Mutable, or `frozen=True` | Fast, stdlib | Internal domain objects, value objects (`frozen=True, slots=True`) |
| **attrs** | Optional validators/converters | Configurable | Fast | Richer dataclasses (validators, converters) without runtime parsing |
| **Pydantic `BaseModel`** | **Parses and validates** at runtime (Rust core) | Mutable by default, `frozen` option | Fast for a validator; slower than dataclasses to construct | **Boundaries**: API requests/responses, config, LLM structured output, external data ([[04 - Pydantic v2 Deep Dive]]) |
| **`TypedDict`** | None | It's a plain `dict` | Fastest (no class instances) | Typing existing dict-shaped data (JSON) without converting |
| **`NamedTuple`** | None | Immutable tuple | Fast, small | Lightweight immutable records, tuple compatibility |
| `msgspec.Struct` | Fast validation/serialisation | Configurable | Very fast | High-throughput serialisation |

- Pattern: **Pydantic at the edges** (untrusted input, serialisation), **dataclasses inside** (domain logic), convert at the boundary. Don't pass Pydantic models through every layer just because they exist.
- 3.15's built-in **`frozendict`** gives an immutable, hashable mapping for constants and cache keys.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **`Any` leaking everywhere** | Checker passes, bugs remain | `object` instead of `Any`; strict mode; `warn_return_any` |
| **Assuming hints are enforced** | Bad data passes silently at runtime | Validate untrusted input with Pydantic/msgspec at boundaries |
| **Mutable container covariance assumed** | Checker errors or unsound casts | Accept `Sequence`/`Mapping`/`Iterable` in parameters |
| **Untyped decorators** | Wrapped functions lose their signatures | `ParamSpec` + `functools.wraps` |
| **Bare `# type: ignore`** | Hides unrelated future errors | Error-code-specific ignores |
| **Checker not in CI or unpinned** | Drifting results; types rot | Pinned checker in CI with a config |
| **Runtime libraries confused by annotations changes** | Libraries reading `__annotations__` directly behave differently on 3.14 | Use `annotationlib` / `typing.get_type_hints`; keep libraries current |
| **Pydantic models used as domain objects everywhere** | Validation overhead and framework coupling | Pydantic at edges, dataclasses inside |

---

## Interview Lines

> [!tip] Say it like this
> - "Python's type hints aren't enforced at runtime; checkers like mypy, pyright or Pyrefly verify them, and libraries like Pydantic and FastAPI read them to validate and document."
> - "I use `object` rather than `Any` when anything is acceptable, because `Any` turns checking off."
> - "Mutable containers are invariant, so functions accept `Sequence` or `Mapping` instead of `list` or `dict`."
> - "`Protocol` gives structural interfaces like Go's; ABCs give nominal ones with runtime enforcement."
> - "Pydantic at the boundaries for untrusted data, dataclasses inside for domain logic; `TypedDict` when the data stays a dict."
> - "3.12 added the `def f[T]` generic syntax and `type` aliases; 3.14 made annotations lazily evaluated, so forward references just work."

## Related Notes

- [[07 - Python Data Model]] · [[08 - Decorators Generators and Context Managers]] · [[10 - Python Errors and Exception Groups]]
- [[04 - Pydantic v2 Deep Dive]] · [[03 - FastAPI Dependency Injection]] · [[LLD/Foundations/05 - Generics]] · [[Languages/README]]
