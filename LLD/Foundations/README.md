---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Foundations

> [!abstract] What this is
> The craft topics that decide whether LLD code reads as "senior engineer" or "passed the tests": failure handling, value objects, modelling, state machines, generics, collections, wiring and layering, API design, testability, logging and retries. Patterns and SOLID live in their own folders; these notes are what you apply around them.

---

## Notes

| # | Note | What it covers |
|---|---|---|
| 01 | [[01 - Exception Handling]] | Hierarchy, checked vs unchecked, domain exceptions, wrapping, try-with-resources, `finally` pitfalls, result types |
| 02 | [[02 - Immutability and Value Objects]] | Entities vs value objects, immutable classes, records, `equals`/`hashCode`, `Comparable`/`Comparator` |
| 03 | [[03 - Object Modelling]] | Requirements to classes, relationships, composition over inheritance, encapsulation, aggregates |
| 04 | [[04 - Enums and State Machines]] | Enums with behaviour, transition tables, State pattern, `EnumMap`/`EnumSet` |
| 05 | [[05 - Generics]] | Bounded types, wildcards and PECS, generic methods, type erasure |
| 06 | [[06 - Collections]] | Choosing maps, lists, queues and sets; `TreeMap` tricks; iteration and mutability traps |
| 07 | [[07 - Dependency Injection and Layering]] | Constructor injection, composition root, controller/service/domain/repository layers |
| 08 | [[08 - API and Method Design]] | Naming, parameters, static factories, `Optional` and null handling |
| 09 | [[09 - Testability]] | Seams for time and randomness, fakes vs mocks, what to test first |
| 10 | [[10 - Logging and Observability]] | Levels, structured logs, correlation ids, no secrets, useful metrics |
| 11 | [[11 - Idempotency and Retries]] | Idempotency keys, state checks, a retry component with backoff and jitter |

---

## Suggested Order

1. **01 and 02** first: they're short and improve every piece of code you write afterwards.
2. **03 and 04**: the modelling core of every LLD round.
3. **07, 08, 09**: wiring, signatures and the testing follow-up.
4. **05, 06, 10, 11** mixed into pattern and problem practice.

## Related Notes

- [[LLD/Concurrency/README]]
- [[LLD/README]]
