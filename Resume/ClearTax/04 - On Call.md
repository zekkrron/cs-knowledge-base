---
tags: [resume/cleartax, status/draft]
created: 2026-09-19
---
# On Call

> [!abstract] `resume:cleartax-oncall`. Intern "led 6 weeks." They will ask what you actually owned. 38% is not in the code. Deep file: [[04-incident-management-oncall]].

## Resume line

Led a 6-week on-call rotation for Malaysia & Global, −38% average incident resolution time.

## Truth

On-call is not a module. The writeup is a **runbook for Data-Harvester failure surfaces**. Use it to sound like you have seen the nights. Do not claim you rewrote New Relic.

| Surface | Symptom |
|---|---|
| `AsyncTaskLifeCycleService` | Job never starts; dupes |
| SQS + `@EnableSqsWithRetry` | Stuck / redrive / DLQ |
| Worker → `HarvesterCoreImpl` | Task FAILED |
| Factories | `No report generators found` / `Template type not found` |
| Tenant / mode Mongo | Timeout, NPE on missing qualifier |
| S3 / pre-sign | 403, dead download |
| `MODE` / conditionals | Beans missing, wrong DB |
| `DataChangeChecker` | Stale file or regen storm |

**Intake:** `triggerReport` → `acceptRequest`. New → SQS `DataGenerationEvent`. Existing → return same task id. SQS publish fail → `handleException` then error (not silent).

**Levers:** `SQS_MAX_PROCESSING_TIME_MS` (default 300s), `SQS_CONCURRENCY_FIXED_SIZE` (5), `DB_FETCH_CHUNK_SIZE` (10k), `allowDiskUse`, `PRESIGNED_URL_EXPIRY_IN_HOURS` (72), `PRESIGNED_PROXY_URL`.

**Health lie:** `management.health.db/mongo.enabled: false` — probe green, Mongo dead. Check the DB, not the actuator.

**Logs:** cache vs generate in `HarvesterCoreImpl`; factory key; `Worker.postStart()` mode. New Relic + OTel in the pom.

## One-minute pitch

Most "Global is down" tickets on this service were **routing or env**, not a mystery algorithm. Wrong `MODE` → MY strategies never load → factory throw. I (fill: I wrote a runbook / I sat the rotation / I only shadowed) treated it as: poll the task → check mode/type/version → SQS/DLQ → Mongo → S3 expiry.

## Triage (say in order)

1. `GET /internal/data/v1/task?asyncTaskId=` — PENDING / RUNNING / FAILED + error.
2. `MODE` + `reportType` + `version` (or template) registered?
3. In flight vs retry vs DLQ.
4. Tenant Mongo + disk use / chunk size.
5. S3 put + pre-sign clock.
6. Cache? `forceRefresh` to isolate.

## Ugly questions

**38% — Jira? PagerDuty? Which weeks?** Docs invent nothing. If you have the before/after, say the denominator (P1 only? all?). Else drop 38.

**Led, as an intern?** What you owned vs who was primary. "Led the rotation" vs "I was on the roster and wrote notes." Pick one.

**Malaysia & Global — two stacks?** Same Harvester, different `MODE` / queues / tenants. Global might mean other country modes + shared infra. Name what you actually paged on.

**Poison message?** Same `taskId` fails forever → DLQ. Fix payload or strategy registration; don't just up concurrency.

**Why factory throw is a feature?** Fail-fast vs wrong report of another country. Contrast `TemplateFactory` silent overwrite ([[01 - Data Harvester]]).

## Related Notes

- [[00 - ClearTax Ownership]]
- [[01 - Data Harvester]]
- [[04-incident-management-oncall]]
