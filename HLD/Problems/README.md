---
tags: [hld/problems, status/draft]
created: 2026-09-22
---
# HLD Problems

> [!abstract] Interview-shaped walks. Same script every time: **requirements → numbers → APIs/schema → boxes → one deep dive → break it**. [[01 - How the Round Works]].

## Duplicates

Your list had **no exact duplicates**. Similar ≠ same:

| Pair | Why they stay separate |
|---|---|
| Seat booking vs flash inventory | Unique seat row vs **qty counter** |
| Checkout vs OMS | Funnel (cart → pay) vs **life after paid** |
| Checkout vs wallet | Orchestrates PSP vs **ledger SoR** |
| Flash vs checkout | Hot stock vs **price snapshot + session** |
| Jira vs collaborative workspace | Tickets + workflow vs **docs + versions + presence** |
| ACL vs workspace | Grants only vs **edit/presence** on top |
| Wearable vs multipart | Telemetry path vs **blob session** (wearable *uses* multipart for dumps) |

All **12** stay.

## The list

| # | Problem | Deep dive on the board |
|---|---|---|
| 1 | [[01 - Splitwise]] | Splits + pair balances in one txn |
| 2 | [[02 - Task Scheduler]] | `next_run_at` + `SKIP LOCKED` |
| 3 | [[03 - Wearable Device]] | Batch ingest, seq, TSDB vs OLTP |
| 4 | [[04 - Multipart Upload]] | Presign, ETag ack, CompleteMPU |
| 5 | [[05 - Jira]] | Issue + legal transition + ES |
| 6 | [[06 - Seat Booking]] | `free→held→sold`, no lock across PSP |
| 7 | [[07 - Wallet and Ledger]] | Ledger + webhook idempotency |
| 8 | [[08 - Resource Sharing ACL]] | Grants + inherit vs materialise |
| 9 | [[09 - Inventory and Flash Sale]] | `on_hand/reserved`, hot SKU |
| 10 | [[10 - Order Management]] | Order state machine + snapshots |
| 11 | [[11 - Collaborative Workspace]] | Optimistic version + presence |
| 12 | [[12 - E-commerce Checkout]] | Freeze + reserve + one order |

## How you walk any of them

1. Functional / NFR / **out of scope**
2. DAU → QPS → storage (only to justify one PG vs Redis vs queue)
3. 4–6 APIs, tables + FKs
4. 6–8 boxes, one write, one read
5. The invariant
6. Double-click / process death / cache lie

## Related Notes

- [[HLD]]
- [[Topics/README]]
- [[05 - Databases]]
- [[14 - Coordination and Concurrency]]
