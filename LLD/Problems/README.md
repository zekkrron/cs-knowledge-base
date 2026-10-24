---
tags: [lld/problems, status/draft]
created: 2026-10-24
---
# LLD Problems

> [!abstract] One note per LLD / machine-coding problem, researched for SDE 3-level rounds in the Indian market. Each note has a clarify script, the V0 you'd submit at minute 90, a trace, an extension ladder (E1–E4, one of them concurrency), review cards and the tests that prove it. Code lives in `LLD/Code` as two snapshots per problem (`v0`, `full`); notes link to it. Each package is self-contained, including the exceptions, CLI driver and demo clock you'd type in the round. Format and rules: `CONVENTIONS.md` → *LLD Problems*.

| # | Problem | Catalog | Extension ladder | Code |
|---|---|---|---|---|
| 1 | [[01 - Parking Lot]] | #1, P0 | E1 pricing · E2 EV slots and layouts · E3 switchable allocation · E4 many gates (CAS) | `lld.parkinglot` |
| 2 | [[02 - Rate Limiter]] | #2, P0 | E1 algorithms · E2 multi-rule with refunds · E3 decision, cost · E4 live overrides, eviction | `lld.ratelimiter` |

Order of the rest: the ranked list in [[Problem Catalog]].

## How You Walk Any of Them

1. Clarify, write requirements and out of scope.
2. Entities and who owns which rule.
3. Class design, then V0 code for the core methods and a CLI demo.
4. Trace one scenario out loud.
5. Expect the ladder: each change should add a class, not edit five.
6. Say where concurrency bites and how you'd test it.

## Related Notes

- [[Problem Catalog]]
- [[LLD/README|LLD]]
