---
tags: [languages/fastapi, status/draft]
created: 2026-10-14
---
# FastAPI Testing

> [!abstract] What this is
> How to test FastAPI apps: the sync **`TestClient`** (Starlette, built on httpx) vs **`httpx.AsyncClient` with `ASGITransport`** for async tests, running the **lifespan** in tests, **`dependency_overrides`** as the main seam, database fixtures with **Testcontainers** and per-test transactions, testing auth, streaming/SSE and WebSockets, mocking outbound HTTP with **respx**, and contract tests against the generated **OpenAPI** schema. The decision this helps with: a fast, reliable test suite for an async FastAPI service. General pytest technique is in [[12 - Python Testing with pytest]].

---

## Why It Matters

- FastAPI apps are mostly wiring (routing, validation, dependencies, serialisation); bugs hide in that wiring, so tests should exercise the HTTP layer, not only services.
- Async sessions, event loops and lifespans make naive test setups flaky ("attached to a different loop", leaked connections).

---

## Two Clients

| | `fastapi.testclient.TestClient` | `httpx.AsyncClient(transport=ASGITransport(app))` |
|---|---|---|
| Test style | Sync tests (`def test_...`) | Async tests (`async def test_...`) |
| Event loop | Runs the app in its own loop (anyio portal) in a separate thread | Same loop as the test |
| Lifespan | Runs when used as a context manager (`with TestClient(app) as c:`) | **Not run by `ASGITransport`**: wrap with `asgi-lifespan`'s `LifespanManager` or call startup in fixtures |
| Async fixtures (async DB sessions) | Awkward: objects bound to the test's loop vs the client's loop | Natural: everything on one loop |
| WebSockets | `client.websocket_connect(...)` | Not supported by `ASGITransport` (use `TestClient` or a real server) |
| Pick when | Simple apps, sync tests, WebSocket tests | Async apps with async DB fixtures |

```python
# conftest.py
import pytest
from httpx import ASGITransport, AsyncClient
from asgi_lifespan import LifespanManager
from app.main import app

@pytest.fixture
async def client():
    async with LifespanManager(app):                           # run startup/shutdown
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as c:
            yield c
    app.dependency_overrides.clear()

async def test_create_order_validates(client):
    r = await client.post("/api/orders", json={"customer_id": "c1", "lines": []})
    assert r.status_code == 422
    assert r.json()["detail"][0]["loc"] == ["body", "lines"]
```

- Use one async plugin consistently (pytest-asyncio with `asyncio_mode = "auto"`, or anyio's) and align fixture loop scope with test loop scope ([[12 - Python Testing with pytest]]).

---

## Dependency Overrides

```python
@pytest.fixture
def fake_pricing():
    return FakePricing(prices={"SKU-1": Decimal("9.99")})

@pytest.fixture
async def client_with_fakes(client, fake_pricing):
    app.dependency_overrides[get_pricing_client] = lambda: fake_pricing
    app.dependency_overrides[current_user] = lambda: User(id="u-1", scopes={"orders:read", "orders:write"})
    yield client
```

- Override the **original callable** passed to `Depends`/`Security`, not the `Annotated` alias ([[03 - FastAPI Dependency Injection]]).
- Overriding `current_user` skips token validation for most tests; keep a **separate set of auth tests** that send real (test-signed) tokens and assert 401/403 for missing, expired, wrong-audience and insufficient-scope tokens ([[07 - FastAPI Security and Middleware]]).
- Clear overrides after each test (in the fixture) so tests don't leak into each other.

---

## Database Tests

```python
@pytest.fixture(scope="session")
def pg_url():
    with PostgresContainer("postgres:17") as pg:
        url = pg.get_connection_url().replace("psycopg2", "asyncpg")
        run_alembic_upgrade(url)                       # migrations are tested too
        yield url

@pytest.fixture
async def session(pg_url):
    engine = create_async_engine(pg_url, poolclass=NullPool)
    async with engine.connect() as conn:
        trans = await conn.begin()                     # outer transaction per test
        s = AsyncSession(bind=conn, join_transaction_mode="create_savepoint", expire_on_commit=False)
        try:
            yield s                                    # app code may commit: commits become savepoints
        finally:
            await s.close()
            await trans.rollback()                     # everything rolled back
    await engine.dispose()

@pytest.fixture
async def db_client(client, session):
    app.dependency_overrides[get_session] = lambda: session
    yield client
```

- **Real Postgres** (Testcontainers), not SQLite: dialect, locking, JSON types and migrations differ ([[05 - FastAPI Data Access with SQLAlchemy]]).
- Per-test isolation via an outer transaction + savepoints (`join_transaction_mode="create_savepoint"`), or truncation between tests for code that manages its own transactions/connections.
- With parallel runs (pytest-xdist), one database (or schema) per worker.

---

## Outbound HTTP

```python
import respx, httpx

@respx.mock
async def test_price_lookup_handles_timeout(client):
    respx.get("https://pricing.internal/SKU-1").mock(side_effect=httpx.ReadTimeout("slow"))
    r = await client.get("/api/prices/SKU-1")
    assert r.status_code == 504
```

- **respx** fakes httpx transports: tests exercise real request building, JSON parsing, timeouts and error mapping. Prefer it over mocking the client object.
- Contract tests against real downstream schemas (Pact, schema checks) catch drift that fakes can't.

---

## Streaming, SSE and WebSockets

```python
async def test_chat_streams_tokens(client_with_fakes):
    async with client_with_fakes.stream("POST", "/api/chat", json={"messages": [...]}) as r:
        assert r.headers["content-type"].startswith("text/event-stream")
        events = [line async for line in r.aiter_lines() if line.startswith("data:")]
    assert events[-1] == "data: [DONE]"

def test_room_broadcast():
    with TestClient(app) as c, c.websocket_connect("/ws/rooms/r1?token=test") as ws:
        ws.send_json({"type": "message", "text": "hi"})
        assert ws.receive_json()["text"] == "hi"
```

- Test **disconnect behaviour** for streams: the upstream fake should observe cancellation (closed context manager) when the client stops reading early ([[06 - FastAPI Streaming SSE and WebSockets]]).

---

## Contract and Schema Tests

- Snapshot `app.openapi()` and diff it in CI: unintentional breaking changes (removed fields, changed types) show up in review.
- **Schemathesis** generates requests from the OpenAPI schema (property-based) to find 500s, validation gaps and schema mismatches.
- Test error contracts too: problem-details shape for 404/409/422/500 ([[07 - FastAPI Security and Middleware]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Lifespan not run in tests** | `app.state` missing; tests pass only with overrides | `with TestClient(app)` or `LifespanManager` |
| **Async fixtures with sync `TestClient`** | "attached to a different loop" errors | `AsyncClient` + `ASGITransport` for async apps |
| **Overrides not cleared** | Tests depend on order | Clear in fixture teardown |
| **Overriding the alias** | Override silently ignored | Override the original function |
| **SQLite in tests, Postgres in prod** | Dialect and locking bugs ship | Testcontainers Postgres |
| **Only happy-path auth tests** | Unprotected endpoints unnoticed | 401/403 tests per router; route inventory test |
| **Mocking httpx client methods** | Timeouts/serialisation untested | respx at the transport level |
| **No schema diff** | Breaking API changes reach clients | OpenAPI snapshot in CI; Schemathesis |

---

## Interview Lines

> [!tip] Say it like this
> - "For async FastAPI apps I test with `httpx.AsyncClient` over `ASGITransport` on the test's own loop, wrapped in a lifespan manager so startup runs; `TestClient` is for sync tests and WebSockets."
> - "`dependency_overrides` is the main seam: fake services and users per test, cleared afterwards, plus a separate suite of real-token auth tests."
> - "Database tests run against Testcontainers Postgres with migrations applied once, and each test inside a rolled-back transaction with savepoints."
> - "Outbound HTTP is faked with respx so timeouts and parsing are exercised."
> - "I snapshot the OpenAPI schema in CI and fuzz the API with Schemathesis to catch contract breaks and 500s."

## Related Notes

- [[12 - Python Testing with pytest]] · [[03 - FastAPI Dependency Injection]] · [[05 - FastAPI Data Access with SQLAlchemy]] · [[07 - FastAPI Security and Middleware]]
- [[13 - Spring Boot Testing]] · [[LLD/Foundations/09 - Testability]] · [[Languages/README]]
