---
tags: [hld/problems, status/draft]
created: 2026-09-22
---
# HLD Problems

> [!abstract] Interview-shaped walks. Same script every time: **requirements → numbers → APIs/schema → boxes → one deep dive → break it**. [[01 - How the Round Works]].

> [!tip] Short on time? **[[00 - Speed Run]]** is all twelve in one page (20 min, read aloud), and **[[16 - Estimation and Decision Cheat Sheet]]** is the arithmetic plus the thresholds that pick the tech. Full deep dives: [[02 - Task Scheduler]], [[03 - Wearable Device]], [[04 - Multipart Upload]].

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
| 0 | [[00 - Speed Run]] | **All twelve, one page. Start here on the day** |
| 1 | [[01 - Splitwise]] | Splits + pair balances in one txn |
| 2 | [[02 - Task Scheduler]] | `next_run_at` + `SKIP LOCKED` · **full depth** |
| 3 | [[03 - Wearable Device]] | Batch ingest, seq, TSDB vs OLTP · **full depth** |
| 4 | [[04 - Multipart Upload]] | Presign, ETag ack, CompleteMPU · **full depth** |
| 5 | [[05 - Jira]] | Issue + legal transition + ES |
| 6 | [[06 - Seat Booking]] | `free→held→sold`, no lock across PSP |
| 7 | [[07 - Wallet and Ledger]] | Ledger + webhook idempotency |
| 8 | [[08 - Resource Sharing ACL]] | Grants + inherit vs materialise |
| 9 | [[09 - Inventory and Flash Sale]] | `on_hand/reserved`, hot SKU |
| 10 | [[10 - Order Management]] | Order state machine + snapshots |
| 11 | [[11 - Collaborative Workspace]] | Optimistic version + presence |
| 12 | [[12 - E-commerce Checkout]] | Freeze + reserve + one order |
| 13 | [[13 - URL Shortener]] | Permuted counter ranges, cached 302s, clicks off the hot path · **new format** |

> [!info] New format (October 2026)
> [[13 - URL Shortener]] and [[06 - Seat Booking]] are the pilots of the new format: clarify script → requirements → estimates that drive V0 → V0 → scale ladder (V1–V4, each with its trigger number) → twist cards → break it → operations → close. Shared mechanics live in [[12 - Mini Designs]]. The note template is in `CONVENTIONS.md` under *HLD Problems and Mini Designs*; how to research and prioritise the ladder and twists is in *Researching and Prioritising a Problem* below. The other notes here are in the older format until converted.

## How you walk any of them

1. Clarify, then functional / NFR (with numbers) / **out of scope**
2. Only the estimates that pick something
3. APIs, tables + FKs
4. V0: the simplest design that works, and what it can't handle
5. Climb the ladder: each step names the number that breaks the previous version and the mini design it uses
6. Expect twists off the ladder: consistency, features, failures, cost, regions, compliance, abuse
7. Break it: double-click / process death / cache lie

---

## Researching and Prioritising a Problem

Applies to every problem note in the new format. The note structure (template in `CONVENTIONS.md`) and the mini designs do not change; this procedure decides **what goes on the ladder and in the twist cards, and in what priority**.

### What research decides, and what it doesn't

- **V0** is the functional core of the prompt as interviewers actually give it. It is usually stable; research confirms it rather than rebuilds it.
- **The scale ladder (V1–Vn) is ordered by its trigger numbers**, not by how often a step is asked: you can't shard before the cache breaks. Research adds an **evidence line and a confidence** to each step and decides how much space it gets. A step nobody asks about stays if the logic needs it, kept short.
- **Twist cards are ordered by evidence.** Cards supported by reports come first; cards from recall or judgement stay, in a lower-priority group, so nothing useful is lost.
- **Mini designs are not touched** by this research. They are reusable mechanics; they only need sourced numbers.

### Procedure

1. **Collect 10–20 dated reports** of the question being asked: LeetCode Discuss, GeeksforGeeks, Blind, Glassdoor, PracHub, company prep guides; plus the major guides (Hello Interview, Grokking/DesignGurus) for what interviewers are told to probe. For each, record company, date, starting scope, the scale pushed, the component the interviewer drilled into, and every follow-up.
2. **Tally.** Each ladder step and twist card gets a count and a confidence:
    + `strong`: several independent reports.
    + `medium`: one report, or consistent guide material.
    + `judgement`: no report found; kept because the design or the Indian market needs it (regulation, Indian spikes).
3. **Annotate in place.** Add `Evidence:` with sources and confidence to each ladder step; group twist cards into **Most asked** and **Lower priority (recall / judgement)**, keeping their ids so cross-references still work.
4. **Add an `## Evidence` section** before `## Sources`: Follow-up | Reports | Count | Placed as (V0 / Vn / twist, with confidence) | Deep dive? (whether interviewers drill into it).
5. **Label numbers.** A number is either sourced (with date) or marked *assumed*. Clarify-script defaults are assumptions.
6. **Change little when the flow is right.** If the existing ladder is already a correct logical escalation and the evidence doesn't contradict it, keep its order and text; only add evidence, confidence and grouping. Rewrite a step only when the evidence shows it's wrong or missing.

## Related Notes

- [[HLD]]
- [[00 - Speed Run]]
- [[16 - Estimation and Decision Cheat Sheet]]
- [[Topics/README]]
- [[05 - Databases]]
- [[14 - Coordination and Concurrency]]
