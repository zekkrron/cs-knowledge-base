---
tags: [languages/gin, status/draft]
created: 2026-10-14
---
# Go Data Access

> [!abstract] What this is
> How Go services talk to relational databases: **`database/sql`** (a connection pool behind `*sql.DB`, `SetMaxOpenConns` and friends, `Rows` lifecycle, `QueryContext`), **pgx** (native PostgreSQL driver and `pgxpool`), the code-generation vs ORM choice (**sqlc**, **GORM**, ent, sqlx, squirrel), **transactions with context** (a `WithTx` helper, isolation, retries), pool sizing and fleet connection budgets, prepared statements behind **PgBouncer**, and migrations (goose, golang-migrate, atlas). The decision this helps with: choosing a data-access approach for a Gin service and keeping connections, transactions and queries under control. Spring and FastAPI equivalents: [[08 - Spring Database Connections and Migrations]], [[05 - FastAPI Data Access with SQLAlchemy]].

---

## Why It Matters

- Most Go service incidents involving databases are pool and lifecycle bugs: unclosed `Rows` exhausting the pool, no `QueryContext` so cancelled requests keep queries running, `MaxOpenConns` unlimited by default, transactions held across HTTP calls.
- Go has no dominant ORM; teams choose between explicit SQL with code generation and ORMs, and interviewers ask why.

---

## database/sql

```go
db, err := sql.Open("pgx", dsn)          // lazy: doesn't connect yet
if err != nil { ... }
db.SetMaxOpenConns(20)                    // DEFAULT IS UNLIMITED
db.SetMaxIdleConns(20)                    // default 2: causes connection churn under load
db.SetConnMaxLifetime(30 * time.Minute)   // below LB/proxy/DB idle limits
db.SetConnMaxIdleTime(5 * time.Minute)
if err := db.PingContext(ctx); err != nil { ... }   // fail fast at startup
```

- `*sql.DB` is a **concurrency-safe pool**, not a connection: create one per database at startup and share it.
- Defaults to change: **`MaxOpenConns` = unlimited** (a traffic spike opens hundreds of connections), **`MaxIdleConns` = 2**.
- Always use the **`...Context`** methods (`QueryContext`, `ExecContext`, `BeginTx`) with the request context, so client disconnects and deadlines cancel queries ([[08 - Go Concurrency Patterns and Context]]).

### Rows lifecycle

```go
rows, err := db.QueryContext(ctx, `SELECT id, total FROM orders WHERE customer_id = $1 LIMIT $2`, cid, limit)
if err != nil {
    return nil, fmt.Errorf("query orders: %w", err)
}
defer rows.Close()                        // returns the connection to the pool; forgetting it leaks connections
var out []Order
for rows.Next() {
    var o Order
    if err := rows.Scan(&o.ID, &o.Total); err != nil {
        return nil, err
    }
    out = append(out, o)
}
return out, rows.Err()                    // errors during iteration surface here
```

- An open `Rows` **holds a connection** until closed or fully iterated: unclosed rows are the classic pool-exhaustion leak.
- `QueryRowContext(...).Scan(...)` closes automatically; check `errors.Is(err, sql.ErrNoRows)`.
- **Always bind parameters** (`$1`, `?`); never build SQL with `fmt.Sprintf` (injection).
- `db.Stats()` exposes `InUse`, `Idle`, **`WaitCount`**, **`WaitDuration`**: export them; rising wait counts mean the pool is the bottleneck ([[06 - Gin Production]]).

---

## pgx

- **pgx** is the standard PostgreSQL driver: use it via `database/sql` (`pgx/v5/stdlib`) for portability, or natively with **`pgxpool`** for better performance and Postgres features (COPY, `LISTEN/NOTIFY`, batch queries, rich types, arrays, `jsonb`).

```go
pool, err := pgxpool.New(ctx, dsn)       // pool_max_conns etc. via DSN or pgxpool.Config
row := pool.QueryRow(ctx, `SELECT total FROM orders WHERE id = $1`, id)
```

- Statement caching: pgx prepares and caches statements per connection by default. Behind **PgBouncer in transaction mode**, use `default_query_exec_mode=simple_protocol` / `exec` modes or PgBouncer 1.21+ prepared statement support; otherwise "prepared statement does not exist" errors appear.

---

## Choosing an Approach

| Approach | What | Pros | Cons | Pick when |
|---|---|---|---|---|
| **sqlc** | Write SQL; generate type-safe Go functions and structs | Real SQL, compile-time checked, no reflection, fast | Dynamic queries (optional filters) are awkward | **Most services**: explicit SQL with type safety |
| **pgx / database/sql directly** (+ sqlx/scany for scanning) | Hand-written queries | Full control | Boilerplate, scanning errors at runtime | Small services, hot paths |
| **Query builders** (squirrel, goqu, jet) | Build SQL in code | Dynamic filters | Less readable than SQL | Search/filter-heavy endpoints |
| **GORM** | Full ORM: models, associations, hooks, auto-migrate | Fast CRUD development | Reflection overhead, hidden queries, N+1 via associations, surprising zero-value behaviour in updates | CRUD-heavy apps, teams from ORM backgrounds |
| **ent** | Schema-as-code ORM with code generation | Type-safe graph queries | Generated code volume, learning curve | Complex entity graphs |

```sql
-- queries/orders.sql (sqlc)
-- name: GetOrder :one
SELECT id, customer_id, total_cents, status FROM orders WHERE id = $1;

-- name: ListOrdersByCustomer :many
SELECT id, total_cents, status FROM orders WHERE customer_id = $1 ORDER BY created_at DESC LIMIT $2;
```

```go
q := db.New(pool)                                  // generated by sqlc
order, err := q.GetOrder(ctx, id)                  // typed params and result
```

- GORM traps: `Updates(struct)` skips zero-value fields (use maps or `Select`), `Save` updates all fields, `Preload` per association to avoid N+1, soft deletes on by default with `DeletedAt`, `AutoMigrate` unsuitable for production schema changes.

---

## Transactions

```go
func WithTx(ctx context.Context, pool *pgxpool.Pool, fn func(pgx.Tx) error) (err error) {
    tx, err := pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.ReadCommitted})
    if err != nil {
        return err
    }
    defer func() {
        if p := recover(); p != nil {
            _ = tx.Rollback(ctx)
            panic(p)                                // re-panic after rollback
        }
        if err != nil {
            _ = tx.Rollback(ctx)
        }
    }()
    if err = fn(tx); err != nil {
        return err
    }
    return tx.Commit(ctx)
}

err := WithTx(ctx, pool, func(tx pgx.Tx) error {
    q := db.New(tx)                                  // sqlc queries bound to the transaction
    if err := q.DebitAccount(ctx, from, amount); err != nil {
        return err
    }
    return q.CreditAccount(ctx, to, amount)
})
```

- A transaction is bound to **one connection**: every statement inside must use the `tx`, not the pool (a query on the pool runs outside the transaction and may deadlock waiting for locks the transaction holds).
- Keep transactions **short**: no HTTP calls or message publishing inside; use an **outbox** table for events ([[06 - Spring Transactions]]).
- Cancelling the context rolls the transaction back; serialization failures (`40001`) and deadlocks (`40P01`) under `REPEATABLE READ`/`SERIALIZABLE` should be **retried** around `WithTx`.
- Concurrency control: `SELECT ... FOR UPDATE`, optimistic version columns (`UPDATE ... WHERE id = $1 AND version = $2`), or atomic updates (`SET stock = stock - 1 WHERE stock > 0`).

---

## Pool Sizing and Budgets

- Size from the database's capacity, not from goroutine counts: goroutines are unlimited, connections aren't. Typical: 10–30 per instance.
- **Fleet budget**: pods × `MaxOpenConns` (+ jobs, migrations) < Postgres `max_connections` with headroom; at scale, PgBouncer/RDS Proxy ([[08 - Spring Database Connections and Migrations]]).
- Fail fast: a query that waits on the pool counts against the request deadline; watch `WaitDuration`.
- Database-side timeouts too: `statement_timeout`, `idle_in_transaction_session_timeout`, `lock_timeout`.

---

## Migrations

| Tool | Notes |
|---|---|
| **goose** | SQL or Go migrations, simple |
| **golang-migrate** | SQL up/down files, CLI and library |
| **atlas** | Declarative schema diffing, linting of risky migrations |

- Run migrations as a separate job, not on every pod start; follow **expand → migrate → contract** for zero-downtime changes; `CREATE INDEX CONCURRENTLY` outside transactions ([[08 - Spring Database Connections and Migrations]]).
- Test with Testcontainers Postgres in CI ([[07 - Gin Project Structure and Testing]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **`MaxOpenConns` unlimited** | Connection storms during spikes, DB overload | Set explicit limits |
| **`Rows` not closed** | Pool exhaustion over time | `defer rows.Close()`, check `rows.Err()` |
| **No `Context` methods** | Cancelled requests keep queries running | `QueryContext` with request context |
| **Queries on the pool inside a transaction** | Work outside the transaction, self-deadlocks | Use the `tx` for everything inside |
| **Remote calls inside transactions** | Long locks, pool exhaustion | Calls outside; outbox for events |
| **String-built SQL** | SQL injection | Bound parameters only |
| **pgx statement cache behind PgBouncer** | "prepared statement does not exist" | Simple protocol / exec mode; session pooling |
| **GORM zero-value updates** | Fields silently not updated | `Select`/maps; explicit SQL for critical updates |
| **`AutoMigrate` in production** | Unreviewed schema changes | Versioned migrations |

---

## Interview Lines

> [!tip] Say it like this
> - "`*sql.DB` is a pool shared by the whole service; its `MaxOpenConns` default is unlimited, so I set it, along with idle connections and lifetimes, and export its wait stats."
> - "Every query uses the request context, and every `Rows` is closed, because an open `Rows` holds a connection."
> - "My default is pgx with sqlc: plain SQL, generated type-safe code, no reflection. GORM speeds up CRUD but hides queries and has zero-value update traps."
> - "Transactions run through a `WithTx` helper that rolls back on error or panic, everything inside uses the `tx`, and there are no network calls inside."
> - "Behind PgBouncer in transaction mode I switch pgx off cached prepared statements."

## Related Notes

- [[04 - Gin Binding Validation and Errors]] · [[06 - Gin Production]] · [[07 - Gin Project Structure and Testing]]
- [[08 - Go Concurrency Patterns and Context]] · [[06 - Spring Transactions]] · [[05 - FastAPI Data Access with SQLAlchemy]] · [[Languages/README]]
