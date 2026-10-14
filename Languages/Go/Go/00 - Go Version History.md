---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Version History

> [!abstract] What this is
> The Go releases that matter, from Go 1.0 (2012) to **Go 1.27** (Aug 2026): the **Go 1 compatibility promise**, the twice-yearly release cadence and support window, `GODEBUG` and the `go`/`toolchain` lines in `go.mod`, and the features that defined each era (concurrent GC, modules, error wrapping, async preemption, generics, `GOMEMLIMIT`, `slog`, iterators, Swiss-table maps, container-aware `GOMAXPROCS`, the Green Tea GC, generic methods and `encoding/json/v2`). The decision this helps with: knowing what a codebase on a given Go version can use and answering "what changed in Go 1.X?" precisely.

---

## Why It Matters

- Go upgrades are usually painless thanks to the compatibility promise, so teams stay current; interviewers expect recent features (generics, `slog`, iterators, `GOMAXPROCS` in containers, the new GC).
- Several behaviour changes are gated by the `go` line in `go.mod` (loop variables in 1.22, timer channels in 1.23): knowing that explains why the same code behaves differently in two modules.

---

## Release Model

- **Two releases a year**: February and August. Each major release is supported until **two newer majors** exist (about a year), with minor releases for security and critical fixes.
- **Go 1 compatibility promise** (2012): programs written for Go 1.x keep compiling and running on later 1.y releases. There is no Go 2 planned; changes ship as Go 1.x.
- **`GODEBUG`** settings keep old behaviour for changed semantics, defaulting by the module's `go` version and settable with `//go:debug` lines or `godebug` blocks in `go.mod`. Settings are eventually removed (1.27 rejects removed settings unless set to the final default).
- **`go` and `toolchain` lines** (1.21): `go 1.26` in `go.mod` is the **minimum** required version; a newer toolchain is downloaded automatically if needed (`GOTOOLCHAIN`). Since 1.26, `go mod init` writes `go 1.(N-1).0` to stay compatible with supported versions.

---

## The Eras

### Foundations (2012–2018)

| Version | Highlights |
|---|---|
| **1.0** (2012) | Compatibility promise |
| **1.5** (2015) | Compiler and runtime in Go (no C); **concurrent GC** with low pauses; `GOMAXPROCS` defaults to CPU count |
| 1.7 | **`context`** in the standard library |
| 1.8–1.9 | Sub-millisecond GC pauses; `sync.Map`; type aliases |

### Modules and errors (2018–2020)

| Version | Highlights |
|---|---|
| **1.11** (2018) | **Modules** (`go.mod`) introduced, replacing `GOPATH` (default in 1.16) |
| **1.13** (2019) | **Error wrapping**: `%w`, `errors.Is`, `errors.As`, `errors.Unwrap`; module proxy and checksum database by default |
| **1.14** (2020) | **Asynchronous preemption**: tight loops no longer block the scheduler or GC ([[01 - Go Runtime and the GMP Scheduler]]) |
| 1.16 | **`embed`**, `io/fs`, modules on by default, `GO111MODULE` era ends |

### Generics and runtime tuning (2022–2023)

| Version | Highlights |
|---|---|
| **1.18** (Mar 2022) | **Generics** (type parameters, constraints, `any`, `comparable`); **fuzzing**; workspaces (`go.work`) ([[09 - Go Generics]]) |
| **1.19** | **`GOMEMLIMIT`** soft memory limit; atomic types (`atomic.Int64`) ([[03 - Go Garbage Collector]]) |
| 1.20 | Profile-guided optimisation (preview), `errors.Join`, `context.WithCancelCause` |
| **1.21** (Aug 2023) | **`log/slog`** structured logging, `slices`/`maps`/`cmp` packages, `min`/`max`/`clear` built-ins, toolchain management, PGO GA |

### Iteration, maps and containers (2024–2025)

| Version | Highlights |
|---|---|
| **1.22** (Feb 2024) | **Per-iteration loop variables** (fixes closure capture bug, for modules with `go ≥ 1.22`); **`net/http.ServeMux` method and wildcard patterns** (`GET /orders/{id}`); `range` over integers; `math/rand/v2` |
| **1.23** (Aug 2024) | **Range-over-func iterators** (`iter.Seq`), `unique` package, timer channels unbuffered and timers collectable when unreferenced |
| **1.24** (Feb 2025) | **Swiss-table map** implementation (faster maps), generic type aliases, `os.Root` (traversal-safe file access), `testing.B.Loop`, `tool` directives in `go.mod`, `runtime.AddCleanup`, `crypto/mlkem`, FIPS 140-3 module |
| **1.25** (Aug 2025) | **Container-aware `GOMAXPROCS`** (uses the cgroup CPU limit, updates when it changes); **Green Tea GC** as an experiment; `testing/synctest` GA; runtime **trace flight recorder**; `sync.WaitGroup.Go`; vet checks for `WaitGroup.Add` misuse and `host:port` formatting |

### Current (2026)

**Go 1.26 (Feb 10, 2026)**

- **Green Tea GC on by default**: 10–40% less GC overhead in GC-heavy programs, more with vector instructions on newer amd64 CPUs ([[03 - Go Garbage Collector]]).
- Language: **`new(expr)`** (pointer to an initialised value, handy for optional fields); generic types may refer to themselves in constraints (`type Adder[A Adder[A]] interface{...}`).
- **`go fix` rebuilt around modernizers** (automated upgrades to current idioms; `//go:fix inline` for API migrations).
- Goroutine **leak profile** (experiment), cgo calls ~30% cheaper, heap base address randomisation, more slice backing stores on the stack.
- Library: `errors.AsType`, `slog.NewMultiHandler`, `crypto/hpke`, experimental `simd/archsimd`, post-quantum hybrid TLS key exchanges on by default, `reflect` iterators, scheduler metrics, pprof web UI defaults to flame graphs.

**Go 1.27 (Aug 19, 2026), the current release**

- **Generic methods**: methods can declare their own type parameters (interface methods can't, and generic methods don't satisfy interfaces) ([[09 - Go Generics]]).
- **`encoding/json/v2`** and `encoding/json/jsontext`: stricter defaults (reject invalid UTF-8 and duplicate names), much faster unmarshalling; **`encoding/json` (v1) is now backed by the v2 implementation** with behaviour preserved (error texts may differ; opt out with `GOEXPERIMENT=nojsonv2`).
- **`goroutineleak` profile GA** (`/debug/pprof/goroutineleak`) ([[12 - Go Diagnostics pprof and Tracing]]).
- New **`uuid`** package, **`crypto/mldsa`** (post-quantum signatures, also in TLS/x509), experimental portable `simd` package.
- Faster small allocations (size-specialised malloc), timer channels always synchronous (`asynctimerchan` removed), HTTP/1 response bodies drained on `Close` for better connection reuse, `httptest.NewTestServer` for `synctest`, struct literal keys may be nested field selectors.

---

## Upgrade Notes That Come Up

| Upgrade | What can bite |
|---|---|
| **→ 1.22** (module `go` line) | Loop variable semantics change: code relying on shared loop variables changes behaviour (usually fixes bugs) |
| **→ 1.23** | Timer/Ticker channel semantics (`Reset`/`Stop` no longer leave stale values) |
| **→ 1.25** | `GOMAXPROCS` drops to the container CPU limit: throughput tests on hosts with no limit vs Kubernetes with limits change; remove `automaxprocs` ([[13 - Go in Containers and Production]]) |
| **→ 1.26** | New GC default (opt-out removed in 1.27); `go mod init` writes an older `go` line |
| **→ 1.27** | JSON v1 on the v2 engine: error message text and edge cases may differ; removed `GODEBUG` settings now rejected unless set to their final value |

- Approach: bump the toolchain in CI first, run tests with `-race`, then bump the `go` line in `go.mod` (which switches `GODEBUG` defaults) as a separate change.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Staying two+ majors behind** | No security fixes | Upgrade every 6–12 months |
| **Bumping the `go` line casually** | Semantics change (loop vars, timers, GODEBUG defaults) | Separate PR; read release notes; run tests |
| **Assuming JSON error strings are stable** | Tests break on 1.27 | Assert on error types, not text |
| **Old `automaxprocs` + 1.25 defaults** | Redundant or conflicting settings | Rely on the runtime default |

---

## Interview Lines

> [!tip] Say it like this
> - "Go ships every February and August under the Go 1 compatibility promise; behaviour changes are gated by the `go` line in `go.mod` and `GODEBUG` settings."
> - "Key milestones: modules in 1.11, error wrapping in 1.13, async preemption in 1.14, generics in 1.18, `GOMEMLIMIT` in 1.19, `slog` in 1.21, per-iteration loop variables and method-aware routing in 1.22, iterators in 1.23, Swiss-table maps in 1.24."
> - "1.25 made `GOMAXPROCS` container-aware, 1.26 made the Green Tea GC the default, and 1.27 added generic methods and `encoding/json/v2`, with the old `encoding/json` now running on the v2 engine."

## Related Notes

- [[01 - Go Runtime and the GMP Scheduler]] · [[03 - Go Garbage Collector]] · [[09 - Go Generics]] · [[10 - Go Modules Build and Tooling]]
- [[00 - Java Version History]] · [[00 - Python Version History]] · [[Languages/README]]
