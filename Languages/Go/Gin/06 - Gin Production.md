---
tags: [languages/gin, status/draft]
created: 2026-10-14
---
# Gin Production

> [!abstract] What this is
> Running a Gin service in production: **release mode**, wiring the engine into a properly configured **`http.Server`** with timeouts and **graceful shutdown**, **trusted proxies** and `ClientIP`, structured logging with **`slog`**, **Prometheus** metrics with route templates, **OpenTelemetry** tracing (`otelgin`), health endpoints, **configuration** and secrets, internal **pprof**, and HTTP/2 and HTTP/3 (experimental in 1.11). The decision this helps with: a Gin deployment that's secure, observable and drains cleanly. Container-level settings (`GOMAXPROCS`, `GOMEMLIMIT`, images) are in [[13 - Go in Containers and Production]].

---

## Why It Matters

- `r.Run(":8080")` is a demo: it starts a server **without timeouts** and without graceful shutdown. Default trusted-proxy settings make `ClientIP()` spoofable, which breaks rate limiting and audit logs.

---

## main.go

```go
func main() {
    cfg := mustLoadConfig()                                  // env → struct, validated; fail fast
    gin.SetMode(gin.ReleaseMode)                             // or GIN_MODE=release

    logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel}))
    slog.SetDefault(logger)

    ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
    defer stop()

    shutdownTracing := mustInitTracing(ctx, cfg)             // OTel SDK, OTLP exporter
    pool := mustOpenDB(ctx, cfg)                             // pgxpool with explicit limits ([[05 - Go Data Access]])

    r := gin.New()
    if err := r.SetTrustedProxies(cfg.TrustedProxyCIDRs); err != nil {   // e.g. the LB's subnet
        log.Fatal(err)
    }
    r.Use(gin.Recovery(), RequestID(), otelgin.Middleware("orders"), AccessLog(logger), Metrics(httpDuration))

    health := NewHealth(pool)
    r.GET("/livez", health.Live)
    r.GET("/readyz", health.Ready)
    registerAPI(r.Group("/api/v1", Auth(cfg.Verifier)), NewOrderHandler(pool))

    srv := &http.Server{
        Addr:              cfg.Addr,
        Handler:           r,
        ReadHeaderTimeout: 5 * time.Second,
        ReadTimeout:       15 * time.Second,
        WriteTimeout:      30 * time.Second,
        IdleTimeout:       90 * time.Second,
    }
    go func() {
        if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
            slog.Error("listen", "err", err)
            stop()
        }
    }()
    go servePprof(cfg.PprofAddr)                             // 127.0.0.1:6060 or internal port only

    <-ctx.Done()
    health.SetNotReady()                                     // readiness → 503
    time.Sleep(cfg.DrainDelay)                               // let the LB notice (or preStop)

    sctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
    defer cancel()
    _ = srv.Shutdown(sctx)                                   // finish in-flight requests
    pool.Close()
    _ = shutdownTracing(sctx)
}
```

- Same lifecycle as any Go server ([[01 - Go net-http Server Model]], [[13 - Go in Containers and Production]]).

---

## Trusted Proxies and ClientIP

- `c.ClientIP()` reads `X-Forwarded-For` / `X-Real-IP` **only if the direct peer is a trusted proxy**. Gin's default trusts all proxies (and warns in debug mode), so any client can forge its IP.
- Set `r.SetTrustedProxies([]string{"10.0.0.0/8"})` to the load balancer ranges, or `nil` to trust none and use `RemoteAddr`; for CDNs use `r.TrustedPlatform = gin.PlatformCloudflare` (or `PlatformGoogleAppEngine`, or a custom header set by your edge).
- 1.12 fixed handling of multiple `X-Forwarded-For` headers and always trusts XFF from Unix sockets.
- Correct client IPs matter for rate limiting, abuse detection, geo logic and audit logs.

---

## Logging

```go
func AccessLog(l *slog.Logger) gin.HandlerFunc {
    return func(c *gin.Context) {
        start := time.Now()
        c.Next()
        if c.FullPath() == "/livez" || c.FullPath() == "/readyz" {
            return
        }
        l.LogAttrs(c.Request.Context(), slog.LevelInfo, "http_request",
            slog.String("method", c.Request.Method),
            slog.String("route", c.FullPath()),
            slog.Int("status", c.Writer.Status()),
            slog.Duration("latency", time.Since(start)),
            slog.String("client_ip", c.ClientIP()),
            slog.String("request_id", c.GetString("request_id")),
            slog.String("trace_id", trace.SpanFromContext(c.Request.Context()).SpanContext().TraceID().String()),
        )
    }
}
```

- JSON to stdout; route templates rather than raw paths; no query strings, tokens or bodies by default; errors logged once in the error middleware ([[04 - Gin Binding Validation and Errors]]).
- `slog.LogAttrs` with typed attributes avoids most allocations ([[02 - Go Memory and Escape Analysis]]).

---

## Metrics and Tracing

| Signal | Implementation |
|---|---|
| RED metrics per route | Histogram `http_request_duration_seconds{method,route,status}` using `c.FullPath()` ([[03 - Gin Middleware]]) |
| Go runtime | `prometheus/client_golang` Go and process collectors (goroutines, GC, memory, FDs) |
| DB pool | `db.Stats()` / pgxpool stats as gauges (in use, idle, wait count, wait duration) |
| Tracing | `go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin` server spans; `otelhttp.NewTransport` for clients; `otelpgx` for queries; W3C `traceparent` propagation |
| Profiling | pprof on an internal port; continuous profiling → PGO ([[12 - Go Diagnostics pprof and Tracing]]) |

- Expose `/metrics` on the internal port too, or protect it; never label metrics with user IDs or raw paths.

---

## Health, Config, Security

- `/livez`: process responds. `/readyz`: not draining, DB reachable (cached check with a short timeout). Keep them outside auth and logging noise.
- Config: env vars into a validated struct; secrets from mounted files/secret managers; no defaults that are unsafe in production.
- Security basics: release mode; body size limits; CORS with explicit origins (`gin-contrib/cors`); security headers; auth middleware on API groups; rate limiting keyed by trusted `ClientIP` or API key; don't serve directory listings with `Static`.
- **HTTP/2** works automatically over TLS with `http.Server`; behind a TLS-terminating LB use h2c only if the LB speaks it. **HTTP/3** support in Gin 1.11 (`RunQUIC`, via quic-go) is experimental; usually terminate HTTP/3 at the edge.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **`r.Run()` in production** | No timeouts, no graceful shutdown | Own `http.Server` + `Shutdown` |
| **Debug mode** | Verbose logs, warnings, slower | `gin.ReleaseMode` |
| **Default trusted proxies** | Spoofable `ClientIP`; bypassed rate limits | `SetTrustedProxies` / `TrustedPlatform` |
| **Raw paths as metric labels** | Cardinality explosion | `c.FullPath()` |
| **pprof/metrics on the public port** | Information exposure | Internal listener |
| **Logging query strings and bodies** | Secrets and PII in logs | Structured, allow-listed fields |
| **No readiness flip on SIGTERM** | 502s during deploys | Not-ready → drain delay → `Shutdown` |
| **Background goroutines ignoring shutdown** | Work lost or process killed mid-way | Root context to workers; wait for them |

---

## Interview Lines

> [!tip] Say it like this
> - "In production I don't use `r.Run`: the Gin engine goes into an `http.Server` with timeouts, and SIGTERM flips readiness, waits for the load balancer and calls `Shutdown`."
> - "I set trusted proxies to the load balancer's ranges, because by default `ClientIP` trusts any `X-Forwarded-For` header."
> - "Logs are JSON `slog` with route template, status, latency, request and trace IDs; metrics use `c.FullPath()` labels; tracing uses `otelgin` and `otelhttp`."
> - "pprof and metrics live on an internal port, and health checks stay outside auth."

## Related Notes

- [[01 - Go net-http Server Model]] · [[03 - Gin Middleware]] · [[05 - Go Data Access]] · [[07 - Gin Project Structure and Testing]]
- [[13 - Go in Containers and Production]] · [[12 - Go Diagnostics pprof and Tracing]] · [[09 - FastAPI Production Deployment]] · [[Languages/README]]
