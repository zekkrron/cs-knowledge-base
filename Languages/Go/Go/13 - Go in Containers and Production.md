---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go in Containers and Production

> [!abstract] What this is
> Running Go services well in Docker and Kubernetes: **small images** (static binaries on `scratch`/distroless), **CPU limits and `GOMAXPROCS`** (container-aware since **1.25**), **memory limits and `GOMEMLIMIT`**, GC tuning in containers, **graceful shutdown** with `signal.NotifyContext` and `http.Server.Shutdown`, health checks, timeouts, configuration, logging with `slog`, and observability. The decision this helps with: a Go deployment that isn't throttled, isn't OOM-killed and drains cleanly on every deploy. JVM and Python equivalents: [[13 - Java in Containers and Production Tuning]], [[09 - FastAPI Production Deployment]].

---

## Why It Matters

- Go's operational story is simple (one static binary, low memory, fast start) but two defaults used to bite in containers: `GOMAXPROCS` ignoring CPU limits (fixed in 1.25) and the GC ignoring memory limits (fixed only if you set `GOMEMLIMIT`).
- Missing server timeouts and shutdown handling are the usual causes of dropped requests and resource exhaustion in Go services.

---

## Images

```dockerfile
FROM golang:1.27 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download                                  # cached layer
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w -X main.version=${GIT_SHA}" -o /out/orders ./cmd/orders

FROM gcr.io/distroless/static-debian12:nonroot       # CA certs, tzdata, /etc/passwd; no shell
COPY --from=build /out/orders /orders
USER nonroot:nonroot
ENTRYPOINT ["/orders"]                               # exec form: the binary is PID 1 and gets SIGTERM
```

| Base | Size | Notes |
|---|---|---|
| `scratch` | Binary only | Add CA certificates and tzdata yourself (or `import _ "time/tzdata"`); no users |
| **distroless/static** | ~2 MB + binary | CA certs, tzdata, nonroot user; no shell: debug with ephemeral containers |
| Alpine | ~7 MB | Shell and package manager; musl matters only with cgo |

- Requires a pure-Go build (`CGO_ENABLED=0`) ([[10 - Go Modules Build and Tooling]]). Multi-arch images with `docker buildx` and `GOARCH`.

---

## CPU: GOMAXPROCS

- **Go 1.25+**: `GOMAXPROCS` defaults to the **cgroup CPU limit** (rounded up) and is updated if the limit changes; CPU *requests* are ignored ([[01 - Go Runtime and the GMP Scheduler]]).
- Before 1.25, use `go.uber.org/automaxprocs`; remove it after upgrading (setting `GOMAXPROCS` explicitly disables the runtime's own adjustment).
- **No CPU limit** (requests only, a common latency-friendly setup): `GOMAXPROCS` = node CPU count, possibly 64+. That can mean more parallel GC and goroutine work than your share; consider setting `GOMAXPROCS` to roughly the CPU request.
- Watch **CFS throttling** (`container_cpu_cfs_throttled_periods_total`): GC uses 25% of GOMAXPROCS during marking, so a too-high GOMAXPROCS against a limit causes bursts and throttled periods.

---

## Memory: GOMEMLIMIT

```yaml
resources:
  requests: { cpu: "1", memory: "512Mi" }
  limits:   { cpu: "2", memory: "512Mi" }
env:
  - name: GOMEMLIMIT
    value: "460MiB"            # ~90% of the 512Mi limit; keep it in sync when the limit changes
```

- Without `GOMEMLIMIT`, the heap can reach ~2× the live heap (`GOGC=100`) before collection, so a spike can exceed the container limit → **OOMKilled**. With it, the GC works harder near the limit ([[03 - Go Garbage Collector]]).
- Set **`GOMEMLIMIT` ≈ 90%** of the memory limit, leaving room for non-heap memory: goroutine stacks are included, but cgo allocations, mmapped files and OS thread stacks outside the runtime are not.
- If the **live heap** approaches the limit, the GC thrashes (CPU capped at ~50%) and the pod eventually OOMs anyway: size memory for the live set and fix leaks ([[12 - Go Diagnostics pprof and Tracing]]).
- Requests = limits for memory; RSS can lag after heap drops (the scavenger returns memory gradually).

---

## Server Timeouts and Graceful Shutdown

```go
func main() {
    ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
    defer stop()

    srv := &http.Server{
        Addr:              ":8080",
        Handler:           router,
        ReadHeaderTimeout: 5 * time.Second,   // Slowloris protection
        ReadTimeout:       15 * time.Second,
        WriteTimeout:      30 * time.Second,  // longer (or per-handler) for streaming
        IdleTimeout:       90 * time.Second,  // > load balancer idle timeout
    }

    go func() {
        if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
            slog.Error("server failed", "err", err)
            stop()
        }
    }()

    <-ctx.Done()                                            // SIGTERM from Kubernetes
    ready.Store(false)                                      // readiness → 503
    time.Sleep(5 * time.Second)                             // or a preStop hook: let LBs stop routing

    shutdownCtx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
    defer cancel()
    if err := srv.Shutdown(shutdownCtx); err != nil {      // stop accepting, wait for in-flight requests
        slog.Error("shutdown", "err", err)
    }
    // then: stop consumers/workers, flush telemetry, close DB pools
}
```

- `http.Server` has **no timeouts by default**: always set them ([[01 - Go net-http Server Model]]).
- `Shutdown` stops listeners, closes idle connections and waits for active requests; it doesn't cancel them, so handlers must respect request contexts and have their own deadlines. WebSockets/hijacked connections need separate handling (`RegisterOnShutdown`).
- `terminationGracePeriodSeconds` > preStop delay + shutdown timeout.
- Background workers (Kafka consumers, tickers) take the root `ctx` and finish their current unit of work on cancellation ([[08 - Go Concurrency Patterns and Context]]).

---

## Health Checks

| Probe | Checks |
|---|---|
| **Liveness** `/livez` | The process responds (no dependency calls) |
| **Readiness** `/readyz` | Not shutting down, dependencies needed to serve are reachable (with short timeouts, cached) |
| **Startup** | Initial loading (caches, migrations if any) finished |

- Go starts in milliseconds, so startup probes are rarely needed unless warm-up loads data.

---

## Configuration, Logging, Observability

- Config from env vars (12-factor) parsed into a struct at startup with validation (`envconfig`, `caarlos0/env`, or manual); fail fast on invalid config. Secrets from mounted files or a secret manager, never baked into images.
- **`log/slog`** (1.21) with a JSON handler to stdout; attach request/trace IDs from context; `slog.NewMultiHandler` (1.26) to fan out; avoid logging request bodies and secrets ([[06 - Gin Production]]).
- Metrics: Prometheus `client_golang` (Go runtime + process collectors, RED metrics per route) or OpenTelemetry metrics; traces with OpenTelemetry (`otelhttp`, `otelgin`, `otelpgx`) ([[12 - Go Diagnostics pprof and Tracing]]).
- pprof on an internal port only.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Pre-1.25 Go with CPU limits** | GOMAXPROCS = node CPUs → throttling, latency | Go 1.25+ or `automaxprocs` |
| **No CPU limit on a huge node** | GOMAXPROCS = 64+, heavy GC parallelism | Set `GOMAXPROCS` near the CPU request |
| **No `GOMEMLIMIT`** | Heap spikes past the limit → OOMKilled | `GOMEMLIMIT` ≈ 90% of the limit |
| **`GOMEMLIMIT` ≈ live heap** | GC thrashing, still OOM | More memory or fewer live objects |
| **No server timeouts** | Slowloris, stuck connections, goroutine buildup | Read header/read/write/idle timeouts |
| **No graceful shutdown** | 502s and dropped requests on deploys | `NotifyContext` + readiness flip + `Shutdown` |
| **Shell-form entrypoint** | SIGTERM not delivered; SIGKILL after grace period | Exec-form `ENTRYPOINT` |
| **`scratch` without CA certs/tzdata** | TLS and time zone failures at runtime | distroless/static or embed tzdata |
| **pprof exposed publicly** | Security exposure | Internal listener |

---

## Interview Lines

> [!tip] Say it like this
> - "I ship a static `CGO_ENABLED=0` binary on distroless as a non-root user with an exec-form entrypoint, so it's a few megabytes and receives SIGTERM."
> - "Since 1.25 `GOMAXPROCS` follows the container's CPU limit automatically; before that we used automaxprocs, and without a CPU limit I set it near the request."
> - "I set `GOMEMLIMIT` to about 90% of the memory limit so the GC tightens before the kernel kills the pod; it isn't a fix for a growing live heap."
> - "`http.Server` has no timeouts by default, so I set read-header, read, write and idle timeouts."
> - "On SIGTERM: flip readiness, wait for the load balancer, `Shutdown` with a deadline, then stop workers and close pools."

## Related Notes

- [[01 - Go Runtime and the GMP Scheduler]] · [[03 - Go Garbage Collector]] · [[10 - Go Modules Build and Tooling]] · [[12 - Go Diagnostics pprof and Tracing]]
- [[01 - Go net-http Server Model]] · [[06 - Gin Production]] · [[13 - Java in Containers and Production Tuning]] · [[Languages/README]]
