---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Idempotency and Retries

> [!abstract] What this is
> The in-process side of reliability: making operations safe to repeat (idempotency keys, natural idempotency, state checks) and writing a reusable retry component with backoff, jitter and a retry policy. This connects LLD code to the HLD reliability ideas without needing a distributed system.

---

## Idempotency

- An operation is **idempotent** if doing it twice has the same effect as doing it once. Retries, double-clicks and redelivered messages make repeats inevitable, so any operation that may be retried must be idempotent or protected.

| Technique | How it works | Example |
|---|---|---|
| **Naturally idempotent operation** | Setting a value, not adding to it | `setStatus(SHIPPED)`, `PUT`, upsert by id |
| **State check** | Only act if not already in the target state | Already `PAID`? return the existing receipt |
| **Idempotency key** | The caller sends a unique key per logical operation; the server stores key → result and returns the stored result for repeats | `pay(request, idempotencyKey)` |
| **Dedupe on event id** | Remember processed ids | Consumers of at-least-once messages |

```java
public final class PaymentService {
    private final ConcurrentHashMap<String, CompletableFuture<Receipt>> byKey = new ConcurrentHashMap<>();
    private final PaymentGateway gateway;

    public PaymentService(PaymentGateway gateway) { this.gateway = gateway; }

    public Receipt pay(String idempotencyKey, PaymentRequest req) {
        CompletableFuture<Receipt> mine = new CompletableFuture<>();
        CompletableFuture<Receipt> existing = byKey.putIfAbsent(idempotencyKey, mine);
        if (existing != null) return existing.join();          // repeat: same result, no new charge
        try {
            Receipt r = gateway.charge(req);
            mine.complete(r);
            return r;
        } catch (RuntimeException e) {
            byKey.remove(idempotencyKey, mine);                 // let a later retry try again
            mine.completeExceptionally(e);
            throw e;
        }
    }
}
```

- `putIfAbsent` makes "first request wins" atomic, so concurrent duplicates wait for the first one's result instead of charging twice ([[LLD/Concurrency/07 - Concurrent Collections]]).
- In a real system the key → result map lives in the database with a unique constraint, and keys expire after a retention window.

---

## Retries

- Retry only failures that are likely **transient** (timeouts, connection resets, 503, "try again"); never retry validation errors or business rejections (they'll fail the same way).
- Retry only operations that are **idempotent** or protected by an idempotency key.
- **Exponential backoff with jitter**: wait 100 ms, 200 ms, 400 ms... with randomness, so many clients don't retry in lockstep. Cap the attempts and the total time.

```java
public final class Retrier {
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Predicate<Throwable> retryable;
    private final Random random;

    public Retrier(int maxAttempts, Duration baseDelay, Predicate<Throwable> retryable, Random random) {
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.retryable = retryable;
        this.random = random;
    }

    public <T> T call(Supplier<T> op) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return op.get();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts || !retryable.test(e)) throw e;
                long cap = baseDelay.toMillis() << (attempt - 1);         // exponential
                Thread.sleep(random.nextLong(cap + 1));                    // full jitter
            }
        }
    }
}

Retrier retrier = new Retrier(3, Duration.ofMillis(100),
        e -> e instanceof TransientException, new Random());
Receipt r = retrier.call(() -> paymentService.pay(key, request));
```

- Design points to mention:
    + The retry **policy** (which errors, how many attempts, backoff) is configurable, a Strategy ([[Strategy]]).
    + Inject `Random` (and a sleeper or `Clock`) to test without real delays ([[09 - Testability]]).
    + A **timeout** on each attempt, and an overall deadline.
    + Retry at **one** layer only; retries at every layer multiply load.
    + When failures persist, a **circuit breaker** stops calling the dependency for a while instead of retrying.
    + With virtual threads, `Thread.sleep` is cheap; on a platform pool, a `ScheduledExecutorService` avoids tying up a thread during backoff.

---

## Interview Lines

> [!tip] Say it like this
> - "Anything that may be retried is idempotent, through a state check or an idempotency key."
> - "Concurrent duplicates race on `putIfAbsent`, so the first one wins and the rest get its result."
> - "I retry only transient errors, with exponential backoff and jitter, capped attempts, and at one layer."
> - "The retry policy is a pluggable strategy, and I inject randomness and time so it's testable."

## Related Notes

- [[01 - Exception Handling]] · [[09 - Testability]] · [[LLD/Concurrency/07 - Concurrent Collections]]
- [[LLD/Foundations/README]]
