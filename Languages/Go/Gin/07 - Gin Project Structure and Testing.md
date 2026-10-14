---
tags: [languages/gin, status/draft]
created: 2026-10-14
---
# Gin Project Structure and Testing

> [!abstract] What this is
> How to lay out and wire a Gin service so it stays testable: package layout (`cmd/`, `internal/`, by-feature packages), **layers** (transport/handlers → services → repositories), **constructor wiring** in `main` (explicit dependency injection; Wire/fx as options), consumer-defined interfaces, and testing at each layer: handler tests with **`httptest`** and Gin in test mode, service tests with fakes, repository tests with **Testcontainers**, end-to-end tests against a real server, and golden/contract tests. The decision this helps with: a structure that keeps Gin at the edge and business logic testable without HTTP.

---

## Why It Matters

- Go projects get messy in two directions: everything in `main.go`, or Java-style layering with interfaces for everything. Interviewers look for pragmatic structure and tests that don't need a running database for every case.

---

## Layout

```
orders/
  cmd/orders/main.go            # wiring: config, logger, DB pool, handlers, server, shutdown
  internal/
    config/                     # env → struct, validation
    httpapi/                    # Gin: router, middleware, handlers, request/response DTOs, error mapping
    order/                      # domain + service: Order, Service, errors (no Gin, no SQL)
    postgres/                   # repositories: sqlc-generated code + adapters
    platform/                   # logging, tracing, health helpers
  migrations/
  queries/                      # sqlc SQL files
  go.mod
```

- **`internal/`** prevents other modules from importing your packages; use it for application code.
- Organise **by feature/domain** (`order`, `payment`) rather than by technical kind (`controllers`, `models`) once the service grows; keep the domain package free of transport and storage types.
- No need for `pkg/` in services; avoid deep package trees. Package names are short and singular (`order`, not `orders_service`).

---

## Layers and Wiring

```go
// internal/order/service.go — business logic, no Gin, no SQL
type Store interface {                                   // defined by the consumer
    Get(ctx context.Context, id string) (Order, error)
    Create(ctx context.Context, o Order) error
}

type Service struct {
    store Store
    clock func() time.Time
}

func NewService(store Store, clock func() time.Time) *Service { return &Service{store: store, clock: clock} }

func (s *Service) Place(ctx context.Context, cmd PlaceOrder) (Order, error) {
    if len(cmd.Lines) == 0 {
        return Order{}, ErrEmptyOrder
    }
    o := Order{ID: uuid.NewString(), Lines: cmd.Lines, CreatedAt: s.clock()}
    return o, s.store.Create(ctx, o)
}
```

```go
// internal/httpapi/orders.go — transport only
type OrderHandler struct{ svc *order.Service }

func (h *OrderHandler) Register(g *gin.RouterGroup) {
    g.POST("/orders", h.create)
    g.GET("/orders/:id", h.get)
}

func (h *OrderHandler) create(c *gin.Context) {
    var req CreateOrderRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        _ = c.Error(NewValidationError(err)); c.Abort(); return
    }
    o, err := h.svc.Place(c.Request.Context(), req.toCommand())
    if err != nil {
        _ = c.Error(err); c.Abort(); return
    }
    c.JSON(http.StatusCreated, toOrderResponse(o))
}
```

```go
// cmd/orders/main.go — explicit constructor wiring
store := postgres.NewOrderStore(pool)
svc := order.NewService(store, time.Now)
api := httpapi.NewRouter(cfg, logger, httpapi.NewOrderHandler(svc))
```

- **Constructor injection in `main`** is the idiomatic default: explicit, compile-checked, no magic ([[LLD/Foundations/07 - Dependency Injection and Layering]]). **Wire** (compile-time codegen) or **fx** (runtime container with lifecycle hooks) help in very large services; fx's reflection-based wiring hides dependencies, so use it deliberately.
- Interfaces live where they're **consumed** (the service defines `Store`), are small, and exist because a fake or second implementation needs them ([[04 - Go Types Interfaces and Embedding]]).
- Request/response DTOs live in the transport layer; domain types don't carry `json`/`binding` tags meant for the API.
- Inject time (`clock`), ID generators and randomness for deterministic tests.

---

## Testing by Layer

| Layer | How | Speed |
|---|---|---|
| **Domain/service** | Plain Go tests with fakes for `Store`, fixed clock | ms |
| **Handlers** | `httptest.NewRecorder` + `gin.TestMode` router with fake services or real service + fake store | ms |
| **Repositories** | Testcontainers Postgres, migrations applied, sqlc queries exercised | seconds |
| **End-to-end** | Real server (`httptest.NewServer(router)`) + real DB + fake external APIs | seconds |

### Handler tests

```go
func TestCreateOrder(t *testing.T) {
    gin.SetMode(gin.TestMode)
    store := newFakeStore()
    router := httpapi.NewRouter(testConfig(), slog.New(slog.DiscardHandler),
        httpapi.NewOrderHandler(order.NewService(store, fixedClock)))

    tests := []struct {
        name       string
        body       string
        wantStatus int
    }{
        {"valid", `{"customer_id":"0b0c8a7e-3f2a-4c8e-9a51-6c1d2e3f4a5b","lines":[{"sku":"A1","quantity":2}],"currency":"EUR"}`, http.StatusCreated},
        {"empty lines", `{"customer_id":"0b0c8a7e-3f2a-4c8e-9a51-6c1d2e3f4a5b","lines":[],"currency":"EUR"}`, http.StatusBadRequest},
        {"unknown field", `{"customer_id":"x","lines":[],"currency":"EUR","oops":1}`, http.StatusBadRequest},
        {"malformed", `{`, http.StatusBadRequest},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            req := httptest.NewRequest(http.MethodPost, "/api/v1/orders", strings.NewReader(tt.body))
            req.Header.Set("Content-Type", "application/json")
            req.Header.Set("Authorization", "Bearer "+testToken(t))
            rec := httptest.NewRecorder()
            router.ServeHTTP(rec, req)
            if rec.Code != tt.wantStatus {
                t.Fatalf("status = %d, want %d; body: %s", rec.Code, tt.wantStatus, rec.Body)
            }
        })
    }
}
```

- Test through the **real router** (middleware included) so auth, binding, error mapping and status codes are covered; add explicit 401/403 cases for protected routes.
- Golden files in `testdata/` for response bodies; `go-cmp` for structured comparisons ([[11 - Go Testing and Benchmarks]]).

### Repository and integration tests

- `testcontainers-go` Postgres in `TestMain`, migrations once, each test in a transaction rolled back (or unique data); `-race` and `goleak` in CI ([[05 - Go Data Access]]).
- External HTTP dependencies: `httptest.NewServer` fakes with configurable failures (timeouts, 5xx) to test retries and error mapping ([[01 - Go net-http Server Model]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Business logic in handlers** | Untestable without HTTP; duplicated rules | Services with plain inputs/outputs |
| **Domain types bound directly from JSON** | API and domain coupled; mass-assignment risks | Transport DTOs mapped to commands |
| **Interfaces for everything, defined by providers** | Boilerplate, mocks mirroring implementation | Small consumer interfaces where needed |
| **Global DB/clients in packages** | Hidden dependencies, test interference | Constructor injection from `main` |
| **Handler tests bypassing middleware** | Auth/error mapping untested | Test via the real router |
| **SQLite or mocks instead of Postgres for repo tests** | Dialect and locking bugs ship | Testcontainers |
| **`time.Now()` and random IDs inline** | Flaky tests | Inject clock and ID generator |

---

## Interview Lines

> [!tip] Say it like this
> - "I keep Gin at the edge: handlers bind DTOs, call a service with plain types, and map errors centrally; services define the small interfaces they need, and repositories implement them with sqlc."
> - "Wiring is explicit constructor injection in `main`; I'd reach for Wire only when the graph gets big."
> - "Handlers are tested through the real router with `httptest`, so middleware, binding and error mapping are covered, including 401 and 403 cases."
> - "Repositories run against Testcontainers Postgres with migrations applied; services use fakes and an injected clock."

## Related Notes

- [[04 - Gin Binding Validation and Errors]] · [[05 - Go Data Access]] · [[06 - Gin Production]] · [[03 - Gin Middleware]]
- [[11 - Go Testing and Benchmarks]] · [[04 - Go Types Interfaces and Embedding]] · [[LLD/Foundations/07 - Dependency Injection and Layering]] · [[Languages/README]]
