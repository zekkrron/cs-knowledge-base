---
tags: [languages/fastapi, status/draft]
created: 2026-10-14
---
# FastAPI Data Access with SQLAlchemy

> [!abstract] What this is
> How a FastAPI service talks to a relational database with **SQLAlchemy 2.0**: typed declarative models (`Mapped[...]`), the 2.0 query style (`select()`), the **async engine and `AsyncSession`** on **asyncpg**, the **session-per-request** dependency, why **lazy loading fails in async** and how to load relationships eagerly, **connection pools** per worker process and fleet-wide connection budgets, PgBouncer caveats, **Alembic** migrations, and when to use a sync engine with `def` endpoints instead. The decision this helps with: wiring a database into FastAPI so sessions, transactions and connections behave correctly under load. The Spring/JPA equivalent is [[07 - Spring Data JPA and Hibernate]] and [[08 - Spring Database Connections and Migrations]].

---

## Why It Matters

- Database access is where most FastAPI latency and outages come from: blocking drivers on the event loop, exhausted pools, `MissingGreenlet` errors from lazy loads, N+1 queries, and pods × workers × pool size exceeding `max_connections`.

---

## Models and Queries (2.0 Style)

```python
from datetime import datetime
from sqlalchemy import ForeignKey, String, func, select
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship, selectinload

class Base(DeclarativeBase): ...

class Customer(Base):
    __tablename__ = "customers"
    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(320), unique=True)
    orders: Mapped[list["Order"]] = relationship(back_populates="customer")

class Order(Base):
    __tablename__ = "orders"
    id: Mapped[int] = mapped_column(primary_key=True)
    customer_id: Mapped[int] = mapped_column(ForeignKey("customers.id"), index=True)
    status: Mapped[str] = mapped_column(String(20), default="pending")
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    customer: Mapped[Customer] = relationship(back_populates="orders")

stmt = (select(Order)
        .where(Order.status == "pending")
        .options(selectinload(Order.customer))      # eager load: no lazy load later
        .order_by(Order.created_at.desc())
        .limit(50))
orders = (await session.scalars(stmt)).all()
```

- `Mapped[...]` annotations give type-checked models; `select()` statements are executed with `session.execute` / `session.scalars`. The legacy `session.query()` API still works but isn't the 2.0 style.
- **SQLModel** (Pydantic + SQLAlchemy models in one class) is convenient for small apps; for larger ones keep ORM models and Pydantic schemas separate ([[04 - Pydantic v2 Deep Dive]]).

---

## Async Engine and Session per Request

```python
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

@asynccontextmanager
async def lifespan(app: FastAPI):
    engine = create_async_engine(
        settings.database_url,                 # postgresql+asyncpg://...
        pool_size=10, max_overflow=5,          # per worker process
        pool_timeout=5, pool_pre_ping=True, pool_recycle=1800,
    )
    app.state.sessionmaker = async_sessionmaker(engine, expire_on_commit=False)
    yield
    await engine.dispose()

async def get_session(request: Request):
    async with request.app.state.sessionmaker() as session:
        yield session                          # services call session.commit() explicitly

Session = Annotated[AsyncSession, Depends(get_session)]

class OrderRepo:
    def __init__(self, session: AsyncSession): self.session = session
    async def mark_paid(self, order_id: int) -> None:
        order = await self.session.get(Order, order_id, with_for_update=True)
        order.status = "paid"

@router.post("/{order_id}/pay")
async def pay(order_id: int, session: Session) -> OrderOut:
    async with session.begin():                # transaction: commit on success, rollback on error
        await OrderRepo(session).mark_paid(order_id)
    ...
```

- **One `AsyncSession` per request**, never shared across requests or concurrent tasks (a session is not safe for concurrent use; `asyncio.gather` over one session fails).
- **`expire_on_commit=False`**: otherwise attributes expire after commit and the next access tries to reload them, which in async means implicit I/O (an error).
- Transactions: `async with session.begin():` blocks, or explicit `commit()`/`rollback()` in the service layer. Commit **before** returning the response if the client must know it succeeded ([[03 - FastAPI Dependency Injection]] on teardown timing).

### Lazy loading in async: `MissingGreenlet`

- Accessing an unloaded relationship (`order.customer`) triggers a lazy load. In async SQLAlchemy that implicit I/O can't happen inside an attribute access: you get **`MissingGreenlet`** (or `greenlet_spawn has not been called`).
- Fixes: eager-load what the use case needs (`selectinload` for collections, `joinedload` for many-to-one), `lazy="raise"` on relationships to catch accidental lazy loads in tests, or `AsyncAttrs` with `await obj.awaitable_attrs.customer` for deliberate lazy access.
- This also prevents **N+1** queries, the same problem as in JPA ([[07 - Spring Data JPA and Hibernate]]).

---

## Async or Sync?

| | Async engine + `async def` endpoints | Sync engine + `def` endpoints |
|---|---|---|
| Driver | asyncpg, psycopg 3 async, aiomysql, aiosqlite | psycopg 3/2, PyMySQL |
| Concurrency per worker | Many concurrent queries on one loop | ≤ thread pool size (40) concurrent ([[02 - FastAPI Request Lifecycle and Sync vs Async]]) |
| Lazy loading | Must be explicit | Works (still risks N+1) |
| Ecosystem | Most of SQLAlchemy; some extensions sync-only | Everything |
| Pick when | Async service, many concurrent I/O-bound requests | Existing sync codebase, CPU-light services, simplicity |

- Never call a **sync** engine from an `async def` endpoint: it blocks the event loop.

---

## Connection Pools and Budgets

- SQLAlchemy's `QueuePool` defaults: `pool_size=5`, `max_overflow=10`, `pool_timeout=30`. Set them explicitly; fail fast (`pool_timeout` of a few seconds) instead of queueing requests for 30 s.
- **Each worker process has its own engine and pool** (created in the lifespan, after fork) ([[06 - WSGI vs ASGI and Python Servers]]):

```
max DB connections = pods × workers per pod × (pool_size + max_overflow)  (+ migrations, jobs, admin)
e.g. 10 pods × 4 workers × 15 = 600  → must stay below Postgres max_connections with headroom
```

- `pool_pre_ping=True` checks connections before use (cheap protection against connections cut by proxies/NAT); `pool_recycle` below any server/proxy idle limit.
- Set Postgres `statement_timeout`, `idle_in_transaction_session_timeout` and `lock_timeout` (via connection options), so a stuck query or forgotten transaction can't hold a connection forever.
- Pool sizing guidance is the same as for Java services: a database runs only a few concurrent queries per core efficiently ([[08 - Spring Database Connections and Migrations]]).

### PgBouncer and RDS Proxy

- Many pods × workers → put **PgBouncer (transaction pooling)** or RDS Proxy in front: thousands of client connections over a few hundred server connections.
- With transaction pooling, **asyncpg's prepared-statement cache breaks** (statements prepared on one server connection are missing on another): disable it (`connect_args={"statement_cache_size": 0}` and `prepared_statement_cache_size=0` in the URL/dialect options, or use unique names), or use session pooling. Session-level features (`SET`, advisory locks, `LISTEN`) don't work in transaction mode.
- When an external pooler does the pooling, use `NullPool` (or a small pool) in the app to avoid double pooling.

---

## Migrations with Alembic

```bash
alembic init -t async migrations                     # async env.py template
alembic revision --autogenerate -m "add order status index"
alembic upgrade head
```

- **Autogenerate** compares models with the database and drafts a migration: always **review** it (renames appear as drop+add; server defaults, enums and constraints need checking).
- Run migrations as a **separate step** (Kubernetes Job / release phase), not in every worker's lifespan: workers would race and need DDL privileges.
- Zero-downtime rules apply: **expand → migrate code → contract**, `CREATE INDEX CONCURRENTLY` (in an autocommit block), backfills in batches, `lock_timeout` ([[08 - Spring Database Connections and Migrations]]).
- Test migrations in CI against a real Postgres (Testcontainers) ([[12 - Python Testing with pytest]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Sync driver in `async def`** | Event loop blocked; worker-wide latency | Async driver, or `def` endpoints with a sync engine |
| **Lazy load in async code** | `MissingGreenlet` errors | Eager loading; `lazy="raise"`; `awaitable_attrs` |
| **`expire_on_commit=True` with async** | Implicit reloads fail after commit | `expire_on_commit=False` |
| **Sharing a session across requests or concurrent tasks** | Interleaved transactions, errors | One session per request/task |
| **Fleet connections > `max_connections`** | Connection errors on scale-out | Budget pods × workers × pool; PgBouncer |
| **asyncpg prepared statements behind PgBouncer (transaction mode)** | "prepared statement does not exist" errors | Disable statement caches; session pooling |
| **Long default `pool_timeout`** | Requests queue 30 s under load | Short timeout; fix slow queries |
| **Migrations in app startup** | Races, slow starts, broad DB privileges | Separate migration job |
| **Unreviewed autogenerate** | Data-losing drops for renames | Review every migration |
| **N+1 queries** | Latency grows with result size | `selectinload`/`joinedload`; query-count tests |

---

## Interview Lines

> [!tip] Say it like this
> - "I create the async engine in the lifespan, once per worker, and give each request its own `AsyncSession` through a dependency; sessions are never shared across concurrent tasks."
> - "In async SQLAlchemy lazy loading would be hidden I/O, so it raises `MissingGreenlet`; I eager-load with `selectinload` or `joinedload`, which also avoids N+1."
> - "Connections are per worker process, so the budget is pods times workers times pool size plus overflow; past that I put PgBouncer in front and disable asyncpg's statement cache."
> - "Sync drivers belong in `def` endpoints; in `async def` they'd block the loop."
> - "Alembic migrations are reviewed, run as a separate job, and follow expand-and-contract."

## Related Notes

- [[03 - FastAPI Dependency Injection]] · [[02 - FastAPI Request Lifecycle and Sync vs Async]] · [[04 - Pydantic v2 Deep Dive]] · [[09 - FastAPI Production Deployment]]
- [[07 - Spring Data JPA and Hibernate]] · [[08 - Spring Database Connections and Migrations]] · [[06 - WSGI vs ASGI and Python Servers]] · [[Languages/README]]
