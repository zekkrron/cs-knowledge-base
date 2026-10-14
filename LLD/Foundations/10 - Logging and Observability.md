---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Logging and Observability

> [!abstract] What this is
> Production-minded basics for LLD code: what to log and at which level, structured logs with correlation ids, never logging secrets, logging exceptions once with their stack traces, and the few metrics worth exposing. Mentioning these briefly shows you've run code in production.

---

## Log Levels

| Level | Use for | Example |
|---|---|---|
| **ERROR** | Something failed and needs attention | Payment provider unreachable after retries |
| **WARN** | Unexpected but handled; may need attention if frequent | Retrying a timed-out call; falling back to a cache |
| **INFO** | Significant business events, low volume | Booking confirmed, service started |
| **DEBUG** | Details for diagnosing, off in production by default | Computed fee breakdown |
| **TRACE** | Very fine-grained flow | Every cache lookup |

- Production usually runs at INFO; too much INFO is as useless as none.

---

## Writing Good Log Lines

- **Use a logging facade** (SLF4J) and **parameterised messages**, so strings aren't built when the level is off:

```java
private static final Logger log = LoggerFactory.getLogger(BookingService.class);

log.info("Booking confirmed bookingId={} showId={} seats={}", booking.id(), showId, seatIds.size());
log.warn("Payment retry attempt={} orderId={}", attempt, orderId);
log.error("Payment failed orderId={}", orderId, exception);   // exception last: stack trace logged
```

- **Structured, key=value (or JSON) fields** make logs searchable: `orderId=42` beats "the order failed".
- **Correlation / request id** on every line, set once per request in the logging context (MDC) and propagated to downstream calls, so one request's logs can be pulled together across services. Clear it in `finally` on pooled threads ([[LLD/Concurrency/12 - Confinement and Immutability]]).
- **Log exceptions once**, where they're handled, **with** the exception object so the stack trace is kept. Logging and rethrowing at every layer duplicates the same failure many times ([[01 - Exception Handling]]).

> [!danger] Never log secrets or personal data
> Passwords, tokens, API keys, full card numbers, OTPs and personal details must never reach logs. Mask them (`****1234`) or leave them out. Logs are copied widely and kept for a long time.

---

## Metrics Worth Exposing

- Logs answer "what happened to this request"; **metrics** answer "how is the system doing".
    + **Rate**: requests per second, per operation.
    + **Errors**: failures per second or as a percentage.
    + **Duration**: latency percentiles (p50, p95, p99).
    + **Saturation**: queue depth, pool usage, active threads, cache hit rate.
- In an LLD component (cache, rate limiter, thread pool), exposing a few counters (hits/misses, rejections, queue size) is a nice touch: "I'd expose rejection counts so we can tune the limit."

---

## Interview Lines

> [!tip] Say it like this
> - "INFO for business events, WARN for handled surprises, ERROR for failures that need attention."
> - "Logs are structured with a correlation id on every line."
> - "I log an exception once, where it's handled, with its stack trace."
> - "Secrets and personal data are never logged."
> - "I'd expose hit, miss and rejection counters on the cache and the rate limiter."

## Related Notes

- [[01 - Exception Handling]] · [[11 - Idempotency and Retries]]
- [[LLD/Foundations/README]]
