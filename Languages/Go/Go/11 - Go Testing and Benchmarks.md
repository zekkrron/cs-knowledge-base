---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Testing and Benchmarks

> [!abstract] What this is
> Testing Go code with the standard toolchain: `go test`, **table-driven tests** and **subtests**, parallel tests, helpers and `t.Cleanup`, test fixtures (`testdata`, golden files), fakes over mocks via small interfaces, **`httptest`**, integration tests with **Testcontainers**, **fuzzing** (1.18), **benchmarks** (`b.Loop` 1.24, `-benchmem`, `benchstat`), the **race detector**, coverage, and **`testing/synctest`** (GA 1.25) for deterministic tests of time and concurrency. The decision this helps with: a fast, reliable test suite that catches concurrency bugs. General principles are in [[LLD/Foundations/09 - Testability]].

---

## Why It Matters

- Go ships testing, benchmarking, fuzzing, coverage and race detection in the toolchain: no framework choice to argue about, and interviewers expect idiomatic table tests and `-race`.
- Concurrency and time-dependent code is where Go tests get flaky; `synctest` (1.25) addresses exactly that.

---

## Table-Driven Tests and Subtests

```go
func TestParseAmount(t *testing.T) {
    tests := []struct {
        name    string
        in      string
        want    int64
        wantErr bool
    }{
        {name: "integer", in: "12", want: 1200},
        {name: "decimals", in: "12.34", want: 1234},
        {name: "negative", in: "-1", wantErr: true},
        {name: "garbage", in: "abc", wantErr: true},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            t.Parallel()                                   // safe: tt is per-iteration since 1.22
            got, err := ParseAmount(tt.in)
            if (err != nil) != tt.wantErr {
                t.Fatalf("ParseAmount(%q) error = %v, wantErr %v", tt.in, err, tt.wantErr)
            }
            if got != tt.want {
                t.Errorf("ParseAmount(%q) = %d, want %d", tt.in, got, tt.want)
            }
        })
    }
}
```

- `t.Run` creates **subtests**: individually runnable (`go test -run 'TestParseAmount/decimals'`), reported separately.
- `t.Errorf` records and continues; `t.Fatalf` stops this test (call it only from the test goroutine).
- `t.Parallel()` runs tests concurrently with other parallel tests in the package; avoid shared mutable state.
- Comparison: `reflect.DeepEqual` or `github.com/google/go-cmp/cmp` (`cmp.Diff(want, got)` for readable diffs). Many teams avoid assertion libraries; `testify` is common and acceptable.

### Helpers, cleanup and fixtures

```go
func newTestStore(t *testing.T) *Store {
    t.Helper()                                  // failures report the caller's line
    dir := t.TempDir()                          // removed automatically
    s, err := OpenStore(filepath.Join(dir, "db"))
    if err != nil {
        t.Fatal(err)
    }
    t.Cleanup(func() { s.Close() })             // runs after the test (and its subtests)
    return s
}
```

- `testdata/` directories are ignored by the build: put fixtures and **golden files** there (update with a `-update` flag).
- `t.Context()` (1.24) gives a context cancelled when the test ends; `t.Setenv`, `t.Chdir` (1.24) restore state automatically. `t.ArtifactDir()` (1.26) for files to keep with `-artifacts`.
- `TestMain(m *testing.M)` for package-level setup (containers, `goleak.VerifyTestMain`).

---

## Fakes, Interfaces and HTTP

- Define a **small interface in the consumer** and pass a hand-written fake in tests ([[04 - Go Types Interfaces and Embedding]]); generated mocks (`gomock`, `mockery`) are fine for wide interfaces but couple tests to call sequences.

```go
func TestHandlerGetOrder(t *testing.T) {
    h := NewHandler(fakeStore{orders: map[string]Order{"o-1": {ID: "o-1"}}})
    req := httptest.NewRequest(http.MethodGet, "/orders/o-1", nil)
    rec := httptest.NewRecorder()
    h.ServeHTTP(rec, req)
    if rec.Code != http.StatusOK {
        t.Fatalf("status = %d", rec.Code)
    }
}

func TestClientRetries(t *testing.T) {
    srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        w.WriteHeader(http.StatusServiceUnavailable)
    }))
    defer srv.Close()
    _, err := NewPricingClient(srv.URL).Price(t.Context(), "sku")
    if err == nil {
        t.Fatal("expected error")
    }
}
```

- `httptest.NewRecorder` for handlers, `httptest.NewServer` for real HTTP clients (timeouts, retries, error mapping) ([[07 - Gin Project Structure and Testing]]).

---

## Integration Tests

- **testcontainers-go** starts Postgres, Redis, Kafka per test package (`TestMain`) or per test; run migrations once; isolate tests with transactions or unique schemas.
- Separate slow tests with build tags (`//go:build integration`) or `testing.Short()` (`go test -short` skips them).
- `-count=1` disables test result caching when tests depend on external state.

---

## Concurrency and Time: race, synctest, goleak

```go
func TestRateLimiterRefills(t *testing.T) {
    synctest.Test(t, func(t *testing.T) {          // 1.25: isolated "bubble" with a fake clock
        lim := NewLimiter(1, time.Second)
        if !lim.Allow() { t.Fatal("first call should pass") }
        if lim.Allow()  { t.Fatal("second call should be limited") }
        time.Sleep(time.Second)                     // advances the fake clock instantly when all goroutines block
        synctest.Wait()                             // wait until bubble goroutines are idle
        if !lim.Allow() { t.Fatal("should refill after 1s") }
    })
}
```

- **`go test -race`**: run in CI for all packages; races fail the test ([[07 - Go Concurrency Primitives]]).
- **`testing/synctest`** (experiment 1.24, GA 1.25): goroutines in the bubble use a **fake clock** that advances only when all of them are blocked, so `time.Sleep`, timers, tickers and timeouts run instantly and deterministically. 1.27 adds `synctest.Sleep` and `httptest.NewTestServer` (in-memory network usable inside bubbles).
- **`goleak`** in `TestMain` fails packages that leave goroutines running ([[08 - Go Concurrency Patterns and Context]]).
- Never use real `time.Sleep` to wait for goroutines; synchronise with channels, `WaitGroup`, `synctest.Wait`, or poll with a deadline.

---

## Fuzzing

```go
func FuzzParseAmount(f *testing.F) {
    f.Add("12.34")                                   // seed corpus
    f.Fuzz(func(t *testing.T, s string) {
        v, err := ParseAmount(s)
        if err != nil {
            return
        }
        if back := FormatAmount(v); back != Normalise(s) {   // round-trip property
            t.Errorf("round trip %q -> %d -> %q", s, v, back)
        }
    })
}
```

- `go test -fuzz=FuzzParseAmount -fuzztime=60s`: coverage-guided input generation; failing inputs are saved to `testdata/fuzz/` and become regression tests run by plain `go test`.
- Best for parsers, decoders, validators, serialisation round-trips.

---

## Benchmarks

```go
func BenchmarkEncode(b *testing.B) {
    v := sampleReport()
    for b.Loop() {                                   // 1.24: handles timer reset and keeps results alive
        if _, err := Encode(v); err != nil {
            b.Fatal(err)
        }
    }
}
```

```bash
go test -bench=Encode -benchmem -count=10 ./report > new.txt
benchstat old.txt new.txt                           # statistically compare runs
go test -bench=Encode -cpuprofile=cpu.out -memprofile=mem.out ./report
```

- **`b.Loop`** (1.24; since 1.26 it no longer prevents inlining in the loop body) replaces `for i := 0; i < b.N; i++`, avoiding setup being timed and the compiler optimising away results.
- `-benchmem` reports `allocs/op` and `B/op`: often the most useful number ([[02 - Go Memory and Escape Analysis]]).
- Run with `-count` ≥ 6–10 and compare with **`benchstat`**: single runs are noise. Use quiet machines; CPU frequency scaling and noisy neighbours distort results.
- `b.ReportAllocs()`, `b.SetBytes(n)` (throughput), `b.RunParallel` for concurrent benchmarks.

---

## Coverage

- `go test -cover`, `-coverprofile=c.out`, `go tool cover -html=c.out`; integration coverage for binaries with `go build -cover` + `GOCOVERDIR`.
- Coverage is a signal for untested areas, not a target.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **`t.Fatal` from a non-test goroutine** | Test hangs or misreports | Send errors back to the test goroutine; `errgroup` |
| **Real sleeps for synchronisation** | Slow, flaky tests | Channels, `WaitGroup`, `synctest` |
| **No `-race` in CI** | Data races ship | `go test -race ./...` |
| **Shared state in parallel tests** | Order-dependent failures | Isolated fixtures, `t.TempDir`, per-test data |
| **Mocking everything** | Tests mirror implementation | Fakes behind small interfaces; `httptest` servers |
| **Single-run benchmarks** | Noise mistaken for wins | `-count`, `benchstat` |
| **Benchmarks timing setup or optimised away** | Wrong numbers | `b.Loop` |
| **Leaked goroutines across tests** | Flakes, false leak reports | `goleak`, `t.Cleanup`, context cancellation |
| **Cached results hiding environment changes** | Stale passes | `-count=1` for integration tests |

---

## Interview Lines

> [!tip] Say it like this
> - "I write table-driven tests with `t.Run` subtests, `t.Parallel` where safe, `go-cmp` for diffs, and helpers marked with `t.Helper` and `t.Cleanup`."
> - "Dependencies are small consumer-side interfaces with hand-written fakes; HTTP code is tested with `httptest` recorders and servers."
> - "CI runs `go test -race`; concurrency and time logic use `testing/synctest`, which since 1.25 gives a fake clock that advances when every goroutine in the bubble is blocked."
> - "Parsers get fuzz tests; failing inputs are saved and become regression tests."
> - "Benchmarks use `b.Loop`, `-benchmem` and several runs compared with `benchstat`."

## Related Notes

- [[07 - Go Concurrency Primitives]] · [[08 - Go Concurrency Patterns and Context]] · [[12 - Go Diagnostics pprof and Tracing]] · [[07 - Gin Project Structure and Testing]]
- [[12 - Python Testing with pytest]] · [[13 - Spring Boot Testing]] · [[LLD/Foundations/09 - Testability]] · [[Languages/README]]
