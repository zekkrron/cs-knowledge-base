# Resume Point 3 — Incident Management / On-Call

> **Resume bullet:** "Led a 6-week on-call rotation for Malaysia & Global, cutting average
> incident resolution time by 38%."

On-call is operational work, not a single code artifact. This document maps the **failure
surfaces** of Data-Harvester to the code that governs them, so incidents can be triaged fast.
Use it as a runbook-style reference for the systems you were on-call for.

---

## 1. System failure surfaces (where incidents originate)

| Surface | Component | Typical symptom |
|---------|-----------|-----------------|
| Async job intake | `HarvesterDataRequestService`, `AsyncTaskLifeCycleService` | Report never starts; duplicate tasks |
| SQS delivery | `SqsServiceByTriggerChannelAndMode`, `@EnableSqsWithRetry` | Messages stuck / re-delivered; DLQ growth |
| Worker processing | `HarvesterConsumer` → `HarvesterProcessorImpl` → `HarvesterCoreImpl` | Task fails, "No report generators found" |
| Strategy resolution | `DataSourceGenerationStrategyFactory` / `TemplateFactory` | `IllegalStateException: No report generators found` / `Template type not found` |
| MongoDB | `ReactiveMongoTemplate`, tenant/mode qualifiers | Timeouts, connection refused, slow aggregations |
| S3 write / pre-sign | `WriterStrategy`, `S3Helper`, `HarvesterCoreImpl.updatePreSignedUrlsIfApplicable` | Upload failures, expired/broken download links |
| Config/mode | `application.yml` env vars, `@ConditionalOnExpression` | Beans not loading; wrong DB; wrong mode |
| Caching / change-detect | `HarvesterCoreImpl.processTask`, `DataChangeChecker` | Stale data served, or unnecessary regeneration |

---

## 2. The async task lifecycle (intake + dedup)

`http-server/.../service/HarvesterDataRequestService.java`:

```java
public Mono<String> triggerReport(HarvesterDataRequest req) {
    return asyncTaskLifeCycleService.acceptRequest(req, AsyncTask::new).flatMap(taskPair -> {
        boolean isExisting = taskPair.getRight();
        if (!isExisting) {
            DataGenerationEvent event = DataGenerationEvent.builder()
                    .taskId(taskPair.getLeft()).messageId(taskPair.getLeft()).build();
            return sqsServiceByTriggerChannelAndMode
                    .publish(event, req.getMode(), req.getTriggerChannel())
                    .onErrorResume(t -> asyncTaskLifeCycleService.handleException(taskPair.getLeft(), t)
                            .then(Mono.error(t)))
                    .thenReturn(taskPair.getLeft());
        }
        return Mono.just(taskPair.getLeft());   // dedup: reuse existing task
    });
}
```

Key incident-relevant behaviors:
- **Dedup**: identical requests reuse an existing task (`isExisting`) — prevents duplicate work.
- **Failure handling**: if SQS publish fails, the task is marked failed via
  `handleException(...)` and the error is propagated (so it surfaces, not silently lost).
- **Status polling**: `getStatus(asyncTaskId)` returns the `AsyncTask` — the first thing to check
  during an incident ("is the task PENDING/RUNNING/FAILED?").

---

## 3. SQS retry & concurrency (delivery reliability)

`worker/.../Worker.java` enables SQS with retry:

```java
@EnableSqsWithRetry
public class Worker { ... }
```

`application.yml` tunables that matter during incidents:

```yaml
aws:
  sqs:
    maxProcessingTimeMs: ${SQS_MAX_PROCESSING_TIME_MS:300000}   # visibility / processing window
    concurrency:
      fixed-size: ${SQS_CONCURRENCY_FIXED_SIZE:5}               # parallel consumers
    consumer:
      reporting-queue: ${TENANT_QUEUE_URL}
```

On-call levers:
- Message reprocessing → check retry attempts and DLQ.
- Slow/hung processing → `maxProcessingTimeMs` and `concurrency.fixed-size`.
- Poison messages → inspect the failing `taskId` / `messageId`.

---

## 4. Strategy resolution errors (most common triage)

Both factories throw explicit errors when a request can't be routed:

- Report side (`HarvesterCoreImpl.getDataSourceGenerationStrategy`):
  `IllegalStateException("No report generators found for the request.")`
  → cause: `reportType + version` not registered, or the mode's beans didn't load
  (`@ConditionalOnExpression` false because `MODE` env is wrong).
- BFF side (`TemplateFactory.getTemplateDataSource`):
  `RuntimeException("Template type not found " + value)`
  → cause: no `@DataReadService` implementation registered for that template.

Fast check: confirm `MODE`, `reportType`/`templateType`, and `version` in the request match a
registered strategy.

---

## 5. MongoDB incident levers

- Repositories use `@Qualifier("tenantReactiveMongoTemplate")` (BFF) or mode-specific templates
  (report). A wrong/missing qualifier → `null` template → NPE at query time.
- Large aggregations set `AggregationOptions.builder().allowDiskUse(true)` — if disabled or
  memory-limited, sorts on big datasets fail with the 100MB limit error.
- Chunked DB reads via `db-fetch-chunk-size: ${DB_FETCH_CHUNK_SIZE:10000}` — reduce if the worker
  OOMs on large tenants.
- Health checks are intentionally off for db/mongo (`management.health.db/mongo.enabled: false`),
  so liveness won't reflect DB outages — check DB directly.

---

## 6. S3 / pre-signed URL incidents

`HarvesterCoreImpl.updatePreSignedUrlsIfApplicable` generates pre-signed download URLs:

```yaml
aws:
  s3:
    presignedUrlExpiryInHours: ${PRESIGNED_URL_EXPIRY_IN_HOURS:72}
    proxy: ${PRESIGNED_PROXY_URL:https://storage.clear.in/v1}
```

Common issues: expired links (expiry window), wrong bucket/region, or proxy misconfig →
downloads 403/redirect fail.

---

## 7. Observability hooks

- **NewRelic APM** (`newrelic-api`) and **OpenTelemetry** log context are dependencies →
  traces/metrics per request.
- `HarvesterCoreImpl` logs the request/response for force refresh, cache hits, and generation:
  `"Serving from cache..."`, `"Report generated req/res..."` — grep these during triage.
- `DataSourceGenerationStrategyFactory` logs the resolved strategy `key` — confirms routing.
- `Worker.postStart()` logs the active `mode` and whether SQS consumers are enabled.

---

## 8. Suggested triage flow (runbook skeleton)

1. **Get the task**: `GET /internal/data/v1/task?asyncTaskId=...` → status + error.
2. **Routing**: does `mode/reportType/version` (or templateType) map to a registered strategy?
   Check `MODE` env and the strategy factory logs.
3. **Queue**: is the message in-flight, retried, or in DLQ? Check SQS + retry attempts.
4. **DB**: connectivity to the tenant/mode Mongo; slow aggregation (allowDiskUse / chunk size).
5. **Write/download**: S3 upload success; pre-signed URL validity/expiry.
6. **Cache**: if stale data reported, check `isCachingEnabled` + `DataChangeChecker` / force
   refresh path.

Documenting these paths (and pre-writing runbooks) is what compresses mean-time-to-resolution —
the "-38%" outcome.

---

## 9. Talking points for interviews

- Async, SQS-decoupled pipeline with dedup + explicit failure marking (`handleException`).
- Clear, fail-fast routing errors made triage deterministic.
- Mode/tenant-aware config as the most common root cause (env → bean loading → routing).
- Standard levers: SQS concurrency/visibility, Mongo allowDiskUse/chunk size, S3 pre-sign expiry.
- Observability via NewRelic + OpenTelemetry + targeted structured logs.
