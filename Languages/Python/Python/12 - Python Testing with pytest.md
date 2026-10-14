---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Python Testing with pytest

> [!abstract] What this is
> How to test Python services with **pytest**: test discovery and **assertion rewriting**, **fixtures** (scopes, `yield` teardown, `conftest.py`, factories), **parametrisation** and markers, built-in fixtures (`tmp_path`, `monkeypatch`, `caplog`), **mocking** with `unittest.mock` (and where to patch), **async tests** (pytest-asyncio / anyio), real dependencies with **Testcontainers**, property-based testing with **Hypothesis**, and suite speed (xdist, fixture scopes). The decision this helps with: structuring a test suite that's fast, isolated and trustworthy. FastAPI-specific testing is in [[10 - FastAPI Testing]]; general testability design in [[LLD/Foundations/09 - Testability]].

---

## Why It Matters

- Python has no compiler to catch type and wiring errors, so tests (plus a type checker) are the safety net ([[09 - Python Typing]]).
- Badly scoped fixtures and over-mocking produce suites that are slow, flaky or prove nothing.

---

## Basics

```python
# tests/test_pricing.py
import pytest
from orders.pricing import apply_discount

def test_discount_applies_to_total():
    assert apply_discount(total=100, percent=10) == 90       # plain assert; pytest shows both sides on failure

def test_rejects_negative_percent():
    with pytest.raises(ValueError, match="percent"):
        apply_discount(total=100, percent=-5)
```

- Discovery: files `test_*.py` / `*_test.py`, functions `test_*`, classes `Test*` (no `__init__`).
- **Assertion rewriting**: pytest rewrites `assert` in test modules to report intermediate values; no `assertEqual` needed.
- Configure in `pyproject.toml`:

```toml
[tool.pytest.ini_options]
testpaths = ["tests"]
addopts = "-ra --strict-markers --strict-config"
filterwarnings = ["error"]            # turn warnings (deprecations) into failures
markers = ["slow: long-running tests", "integration: needs containers"]
asyncio_mode = "auto"                 # pytest-asyncio
```

---

## Fixtures

```python
# tests/conftest.py  — fixtures here are available to all tests below this directory
import pytest

@pytest.fixture
def clock():
    return FixedClock("2026-01-01T00:00:00Z")

@pytest.fixture
def order_factory():
    def make(**overrides):
        return Order(**({"id": "o-1", "customer_id": "c-1", "total_cents": 1000} | overrides))
    return make

@pytest.fixture(scope="session")
def postgres():
    with PostgresContainer("postgres:17") as pg:     # started once for the whole test session
        yield pg.get_connection_url()                 # code after yield = teardown

@pytest.fixture
def db_session(postgres):
    session = make_session(postgres)
    yield session
    session.rollback()                                # isolate each test
    session.close()

def test_order_total(order_factory, clock):
    order = order_factory(total_cents=2500)
    assert order.total_cents == 2500
```

| Scope | Created | Use |
|---|---|---|
| `function` (default) | Per test | Mutable state, per-test isolation |
| `class`, `module`, `package` | Per group | Moderately expensive shared setup |
| `session` | Once per run (per xdist worker) | Containers, app instances, expensive read-only resources |

- Fixtures request other fixtures by **parameter name**: dependency injection for tests.
- `yield` fixtures run teardown even when the test fails. Prefer them over `request.addfinalizer`.
- **Factory fixtures** (functions returning builders) beat one big shared object: each test states the data it cares about.
- Wider scopes are faster but share state: pair session-scoped resources with function-scoped **isolation** (transactions rolled back, unique keys, truncation).
- `autouse=True` fixtures apply implicitly; use sparingly (hidden setup).

---

## Parametrisation and Markers

```python
@pytest.mark.parametrize(
    ("total", "percent", "expected"),
    [(100, 0, 100), (100, 10, 90), (100, 100, 0)],
    ids=["no-discount", "ten-percent", "full"],
)
def test_discount_table(total, percent, expected):
    assert apply_discount(total=total, percent=percent) == expected
```

- Table-driven tests, like Go's ([[11 - Go Testing and Benchmarks]]). Fixtures can be parametrised too (`params=[...]`), e.g. run the same tests against two backends.
- Markers: `@pytest.mark.slow`, `skip`, `skipif(sys.version_info < (3, 14), ...)`, `xfail(strict=True)`; select with `-m "not slow"`.

---

## Built-in Fixtures

| Fixture | Use |
|---|---|
| `tmp_path` | Per-test temporary directory (`pathlib.Path`) |
| `monkeypatch` | Set env vars, attributes, dict items, `cwd`, restored afterwards |
| `caplog` | Assert on log records |
| `capsys` / `capfd` | Capture stdout/stderr |
| `request` | Introspect the requesting test, parametrised values, finalizers |

---

## Mocking

```python
from unittest.mock import AsyncMock, create_autospec

def test_charges_card(monkeypatch):
    gateway = create_autospec(PaymentGateway, instance=True)     # spec'd: wrong method names/args fail
    gateway.charge.return_value = Receipt(id="r-1")
    service = CheckoutService(gateway=gateway)                    # constructor injection: no patching needed

    service.checkout(order)

    gateway.charge.assert_called_once_with(order.card_token, 1000)

async def test_async_client():
    client = AsyncMock(spec=PricingClient)
    client.quote.return_value = Price(1000)
    assert (await PriceService(client).total("sku")) == 1000
```

- **Patch where the name is looked up**, not where it's defined: `mock.patch("orders.service.httpx")` for `orders/service.py` doing `import httpx`; patching `httpx.get` misses `from httpx import get` imports.
- Use **`autospec`/`spec`**: plain `Mock` accepts any attribute or call, so tests pass against methods that don't exist.
- Prefer **fakes and dependency injection** over patching internals; mock at boundaries (HTTP, clocks, queues). For HTTP, `respx` (httpx) or `responses` (requests) fake the transport and exercise real serialisation.
- Time: inject clocks, or `time-machine`/`freezegun` for code you don't control.

---

## Async Tests

- **pytest-asyncio**: `asyncio_mode = "auto"` makes `async def` tests and fixtures run on an event loop; configure loop scope (`loop_scope`) when async fixtures are session-scoped so they share the loop with tests.
- **anyio's pytest plugin** (`@pytest.mark.anyio`) is an alternative used by Starlette/FastAPI; don't mix both plugins on the same tests.
- Never leave tasks running after a test: use `TaskGroup` in code under test, cancel in teardown ([[05 - asyncio Internals]]).
- Polling for async outcomes: retry assertions with a timeout instead of `sleep`.

---

## Integration Tests with Real Dependencies

- **testcontainers-python** starts Postgres, Redis, Kafka, LocalStack in Docker for tests. Session-scoped containers + per-test isolation keep it fast.
- Run migrations once per session against the container, so tests also verify migrations ([[05 - FastAPI Data Access with SQLAlchemy]]).
- Mark them (`@pytest.mark.integration`) so they can run separately or in a later CI stage.

## Property-Based Testing

```python
from hypothesis import given, strategies as st

@given(st.integers(min_value=0, max_value=10**9), st.integers(min_value=0, max_value=100))
def test_discount_never_increases_total(total, percent):
    assert 0 <= apply_discount(total=total, percent=percent) <= total
```

- **Hypothesis** generates inputs, finds failures and **shrinks** them to minimal examples; excellent for parsers, serialisation round-trips and invariants.

---

## Speed and Reliability

| Technique | Effect |
|---|---|
| **pytest-xdist** (`-n auto`) | Run tests in parallel processes; tests must not share mutable external state (unique DB schemas/keys per worker) |
| Right fixture scopes | Expensive setup once, cheap isolation per test |
| `--lf` / `--ff` | Re-run last failures first |
| `--durations=20` | Find the slowest tests |
| pytest-randomly | Random order exposes hidden inter-test dependencies |
| pytest-cov (`--cov --cov-branch`) | Coverage, as a signal rather than a target |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Session fixtures with mutable state** | Tests pass or fail depending on order | Per-test isolation; random ordering in CI |
| **Patching the wrong path** | Real code runs; network calls in tests | Patch where looked up; prefer injection |
| **Mocks without spec** | Tests pass against non-existent methods | `autospec` / `spec` |
| **Over-mocking** | Tests verify the mocks, not behaviour | Fakes, real containers for integration |
| **`sleep` in async tests** | Slow and flaky | Await events or poll with timeout |
| **Mixing pytest-asyncio and anyio plugins** | Confusing loop errors | Pick one per project |
| **Warnings ignored** | Deprecations break the next upgrade | `filterwarnings = error` |
| **Shared external state with xdist** | Random failures in parallel runs | Per-worker databases/keys |

---

## Interview Lines

> [!tip] Say it like this
> - "pytest fixtures are dependency injection for tests: requested by name, scoped from function to session, with `yield` for teardown."
> - "Expensive things like Testcontainers databases are session-scoped, and each test gets isolation through a rolled-back transaction or unique data."
> - "I patch where a name is looked up, use `autospec` so mocks match real signatures, and prefer injecting fakes over patching."
> - "Parametrize gives table-driven tests; Hypothesis finds edge cases in invariants and shrinks failures."
> - "Warnings are errors in CI, the order is randomised, and xdist runs the suite in parallel with per-worker resources."

## Related Notes

- [[10 - FastAPI Testing]] · [[09 - Python Typing]] · [[10 - Python Errors and Exception Groups]] · [[05 - asyncio Internals]]
- [[13 - Spring Boot Testing]] · [[LLD/Foundations/09 - Testability]] · [[Languages/README]]
