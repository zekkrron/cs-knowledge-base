---
tags: [languages/gin, status/draft]
created: 2026-10-14
---
# Gin vs Alternatives

> [!abstract] What this is
> How Gin compares with the other ways to build HTTP services in Go: **plain `net/http`** (with the 1.22 `ServeMux`), **chi** (idiomatic router on `net/http`), **Echo** (Gin-like framework), **Fiber** (Express-like, built on **fasthttp**, not `net/http`), and RPC options (**gRPC**, **Connect**). Covers performance claims vs reality, `net/http` compatibility, ecosystem, and a decision table. The decision this helps with: choosing a framework for a new Go service and defending the choice.

---

## Why It Matters

- "Why Gin and not X?" is a common design-review and interview question. Benchmarks between Go routers differ by nanoseconds per request; the real differences are API style, `net/http` compatibility and ecosystem.

---

## Comparison

| | **net/http (1.22+)** | **chi** | **Gin** | **Echo** | **Fiber** |
|---|---|---|---|---|---|
| Built on | Standard library | `net/http` | `net/http` | `net/http` | **fasthttp** |
| Handler type | `http.Handler` | `http.Handler` | `func(*gin.Context)` | `func(echo.Context) error` | `func(*fiber.Ctx) error` |
| Routing | Methods, `{param}`, `{rest...}` | Radix tree, groups, `{param}`, regex params | Radix tree, groups, `:param`, `*catchall` | Radix tree, groups | Express-style |
| Middleware | `func(http.Handler) http.Handler` | Same (std-compatible) | Gin-specific | Echo-specific | Fiber-specific |
| Binding/validation | Manual | Manual (+ libraries) | Built in (validator) | Built in (pluggable) | Built in |
| Errors | Manual | Manual | `c.Error` + middleware | **Handlers return `error`**, central `HTTPErrorHandler` | Return `error` |
| HTTP/2 | Yes | Yes | Yes (HTTP/3 experimental) | Yes | Limited (fasthttp) |
| Ecosystem | Everything | All std middleware | gin-contrib, large community | echo middleware | Fiber middleware |

### Plain net/http

- Since **Go 1.22**, `ServeMux` handles method matching and path parameters (`GET /orders/{id}`, `r.PathValue("id")`), which covered the main reason for routers ([[01 - Go net-http Server Model]]).
- Pros: zero dependencies, every library works, nothing to upgrade. Cons: no route groups, binding, validation or error helpers: you write small helpers yourself.
- Pick for: small services, internal tools, libraries, teams that prefer minimal dependencies.

### chi

- A lightweight router fully compatible with `net/http` (`http.Handler` and std middleware), with groups, sub-routers and middleware stacks.
- Pick for: teams wanting idiomatic `net/http` code with nicer routing and composition; easy to drop to the standard library anywhere.

### Gin

- Most popular, mature, fast enough, batteries for binding/validation/rendering, lots of examples.
- Costs: Gin-specific handler and middleware types, pooled-context pitfalls ([[02 - Gin Architecture Routing and Context]]), errors not returned from handlers (it relies on `c.Error` or direct responses).
- Pick for: REST/JSON APIs where team familiarity and the ecosystem matter.

### Echo

- Similar feature set to Gin; handlers **return errors**, which gives cleaner centralised error handling; pluggable binder/validator; good docs.
- Pick for: Gin-style development with an error-returning handler signature.

### Fiber

- Express-like API on **fasthttp**, which reuses buffers aggressively and **isn't `net/http`-compatible**.
- Caveats:
    - `fiber.Ctx` values (strings, byte slices from requests) are **only valid within the handler**: keeping them (in goroutines, caches) without copying causes data corruption (similar to Gin's pooling, but for request data itself).
    - `net/http` middleware and libraries (OpenTelemetry instrumentations, `httptest`, many auth libraries) need adaptors.
    - HTTP/2 support is limited; benchmark advantages rarely matter once a DB call is involved.
- Pick for: teams coming from Express who accept the ecosystem trade-off; extreme throughput for very simple endpoints.

---

## Performance Reality

- Router overhead differences between `net/http`, chi, Gin and Echo are in the **hundreds of nanoseconds** per request. A single database query takes **milliseconds**: framework choice almost never determines service throughput.
- What dominates: database access and pools, JSON encoding (Go 1.27's `encoding/json` on the v2 engine unmarshals much faster), allocations, downstream latency ([[02 - Go Memory and Escape Analysis]], [[05 - Go Data Access]]).

---

## RPC Alternatives

| Option | Use |
|---|---|
| **gRPC** (`google.golang.org/grpc`) | Service-to-service with protobuf contracts, streaming, deadlines; HTTP/2 only; needs gRPC-Web/gateway for browsers |
| **Connect** (connectrpc.com) | Protobuf APIs over plain HTTP/1.1 and HTTP/2, compatible with gRPC and browsers, on `net/http` |
| **grpc-gateway** | REST/JSON facade generated from protobuf for gRPC services |

- Common split: gRPC/Connect between internal services, REST/JSON (Gin/chi/net/http) at the public edge.

---

## Decision Table

| Situation | Pick |
|---|---|
| Small service, few routes, minimal deps | **`net/http`** (1.22+ mux) |
| Idiomatic std-compatible code with groups and middleware | **chi** |
| JSON API, team knows Gin, wants binding/validation built in | **Gin** |
| Same, but prefer handlers returning errors | **Echo** |
| Internal service-to-service with schemas and streaming | **gRPC / Connect** |
| Express-style team, simple high-throughput endpoints, accepts fasthttp caveats | **Fiber** |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Choosing a framework for benchmark numbers** | No real-world gain; ecosystem friction | Choose for API style and compatibility |
| **Fiber values kept beyond the handler** | Corrupted data | Copy strings/bytes before retaining |
| **Framework-specific types in the domain layer** | Lock-in, hard to migrate | Keep frameworks in the transport layer ([[07 - Gin Project Structure and Testing]]) |
| **Mixing several routers in one service** | Inconsistent middleware and errors | One router, adapters at the edges |

---

## Interview Lines

> [!tip] Say it like this
> - "Since Go 1.22's `ServeMux` supports methods and path parameters, plain `net/http` is enough for many services; chi adds groups while staying fully std-compatible."
> - "Gin and Echo add binding, validation and rendering; Echo's handlers return errors, Gin uses `c.Error` and middleware."
> - "Fiber is built on fasthttp, not `net/http`, so std middleware and instrumentation need adaptors and request values are only valid inside the handler."
> - "Router overhead is nanoseconds while a DB call is milliseconds, so I choose for API style and ecosystem, and keep the framework at the edge."
> - "Internally I'd use gRPC or Connect with protobuf; at the public edge, REST with Gin or chi."

## Related Notes

- [[00 - Gin Version History]] · [[01 - Go net-http Server Model]] · [[02 - Gin Architecture Routing and Context]] · [[07 - Gin Project Structure and Testing]]
- [[03 - Web Server Models Compared]] · [[06 - Choosing a Language for a Service]] · [[Languages/README]]
