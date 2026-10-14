---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Testability

> [!abstract] What this is
> Designing code so it's easy to test, because "how would you test this?" is a standard LLD follow-up. Covers seams for time and randomness, fakes vs mocks vs stubs, testing behaviour rather than implementation, and what to test first in an interview design.

---

## Design for Seams

- A **seam** is a place where a test can substitute behaviour without editing the code. Constructor-injected interfaces are the main seams ([[07 - Dependency Injection and Layering]]).
- The usual hidden dependencies that make code untestable:

| Hidden dependency | Problem | Seam |
|---|---|---|
| `Instant.now()`, `System.currentTimeMillis()` | Tests can't control time (TTLs, fees by duration, rate limits) | Inject a **`Clock`** (or `LongSupplier` for `nanoTime`) |
| `new Random()`, `UUID.randomUUID()` | Non-deterministic outputs | Inject a `Random` with a seed, or an `IdGenerator` |
| `new StripeGateway()` inside a class | Calls a real service | Inject a `PaymentGateway` interface |
| Static singletons (`Config.getInstance()`) | Global state shared across tests | Inject the config object |
| Threads started internally | Timing-dependent tests | Inject an `Executor`; use `Runnable::run` in tests |

```java
class FeeCalculator {
    private final Clock clock;
    FeeCalculator(Clock clock) { this.clock = clock; }

    Money fee(Ticket t) {
        Duration parked = Duration.between(t.entryTime(), clock.instant());
        return Money.ofRupees(Math.max(1, parked.toHours()) * 50);
    }
}

// Test: fix time, no sleeping
Clock fixed = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);
Ticket t = new Ticket("T1", Instant.parse("2026-10-11T09:00:00Z"));
assertEquals(Money.ofRupees(150), new FeeCalculator(fixed).fee(t));
```

---

## Test Doubles

| Double | What it is | Pick when |
|---|---|---|
| **Fake** | A working lightweight implementation (`InMemorySeatRepository`) | Default for repositories and gateways; tests read naturally |
| **Stub** | Returns canned answers (`gateway.charge()` always declines) | Steering the code down a specific path |
| **Mock** | Verifies that certain calls happened (`verify(notifier).send(...)`) | The interaction itself is the behaviour (an email must be sent) |
| **Spy** | A real object that also records calls | Rarely; mostly legacy code |

- Prefer **fakes and stubs**; use mocks for outbound side effects you can't observe otherwise. Over-mocking ties tests to implementation details, so they break on every refactor.

---

## Test Behaviour, Not Implementation

- Assert on **observable outcomes** (returned values, state through public methods, calls to external ports), not on private fields or the order of internal calls.
- One behaviour per test, named after it: `rejectsBookingWhenSeatAlreadyHeld`, `chargesMinimumOneHour`.
- Arrange / Act / Assert structure; build test data with small helpers or builders.

---

## What to Test First (in an Interview)

- When asked how you'd test your design, name these in order:
    + **Core happy path** end to end: park → pay → exit.
    + **Invariants and illegal transitions:** can't pay twice, can't book a held seat, can't move `DELIVERED → PAID` ([[04 - Enums and State Machines]]).
    + **Boundaries:** capacity full, zero/negative quantities, exactly-at-expiry times (with an injected clock).
    + **Failure paths:** payment declined, repository throws, timeouts.
    + **Concurrency:** many threads booking the same seat; exactly one wins ([[LLD/Concurrency/17 - Testing Concurrent Code]]).

---

## Interview Lines

> [!tip] Say it like this
> - "Time, randomness and external services come in through the constructor, so tests control them."
> - "I'd inject a `Clock` instead of calling `Instant.now()`, and test TTLs without sleeping."
> - "I prefer in-memory fakes for repositories and use mocks only to verify outbound side effects."
> - "I test the invariants and illegal transitions, not private fields."

## Related Notes

- [[07 - Dependency Injection and Layering]] · [[LLD/Concurrency/17 - Testing Concurrent Code]]
- [[LLD/Foundations/README]]
