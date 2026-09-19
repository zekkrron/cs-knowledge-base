# Clear Data Harvester — Architecture Overview

> This document is the foundation for all resume-point documentation. Every ClearTax
> resume bullet maps to concepts explained here. Read this first, then jump to the
> point-specific docs.

---

## 1. What is Data-Harvester?

Data-Harvester (`clear-data-harvester`) is a **generic, configurable backend service** built for
ClearTax's **Global E-Invoicing** team. It has two responsibilities:

1. **Backend-for-Frontend (BFF)** — serves the e-invoicing UI with real-time data:
   summary counts, paginated document lists, and detailed drill-down views.
2. **Report Generation** — asynchronously exports large datasets into files
   (CSV / Parquet / Excel) and uploads them to S3 for download.

It is written in **Java 17 + Spring Boot 2.7.11**, uses **Project Reactor** (reactive,
non-blocking streams), stores data in **MongoDB** (and PostgreSQL/DuckDB for some modes),
and is decoupled via **AWS SQS** for the async report flow.

---

## 2. Module Layout (Maven multi-module project)

| Module | Responsibility |
|--------|----------------|
| `common-util` | Shared models, constants, hashing (`XXHashUtil`), SQS event models, JSON serializers |
| `harvester-core` | Core processing engine: strategy factories, writer strategies, SQS consumers, mode-aware DB config |
| `read-data-source` | Report data-source implementations per mode/country (the "plugin" layer for report generation) |
| `worker` | SQS consumer worker; picks up jobs and runs report generation |
| `http-server` | Internal REST API to trigger report generation and poll task status |
| `arrow-server` | Apache Arrow Flight server for streaming data |
| `harvester-data-read-server` | Public REST API (BFF) for the frontend: viewSummary / viewList / viewDetailed / report generate + download |
| `data-read-service` | Generic BFF contracts: `IDataReadSource`, `TemplateFactory`, `@DataReadService`, DTOs, enums (`TemplateType`, `Mode`) |
| `einvoice-data-read-service` | E-invoicing (Malaysia) BFF implementations: query builder, repositories, mappers |
| `tds-data-read-service` | TDS-specific BFF implementations |
| `harvester-scheduler` | Temporal-based scheduling for periodic refresh workflows |
| `test-report` | Aggregated JaCoCo coverage reporting across all modules |

---

## 3. The Two Core Flows

### 3.1 BFF Flow (synchronous, low-latency)

Used to render the e-invoicing dashboards.

```
Frontend
  └─ POST /{mode}/public/documents/v1/{templateType}/viewSummary   (or /viewList, /viewDetailed)
       └─ PublicDocumentReadController
            └─ TemplateFactory.getTemplateDataSource(templateType)      ← routing by template
                 └─ EinvoiceSalesDataReadService (an IDataReadSource)   ← the strategy
                      └─ EinvoiceQueryBuilder                           ← builds Mongo aggregation pipeline
                           └─ EInvoiceDocumentService
                                └─ EInvoiceRepositoryFactory            ← routing by template → collection
                                     └─ EinvoiceSalesReadRepositoryImpl
                                          └─ ReactiveMongoTemplate.aggregate(...)   ← MongoDB
```

### 3.2 Report Generation Flow (asynchronous)

Used for "Export / Download" actions.

```
Frontend
  └─ POST /internal/data/v1/generate  (HarvesterDataRequest)
       └─ DataRequestController → HarvesterDataRequestService
            └─ AsyncTaskLifeCycleService.acceptRequest(...)   ← creates AsyncTask, dedups
            └─ SqsServiceByTriggerChannelAndMode.publish(DataGenerationEvent)   ← enqueue

  ... (async, decoupled via SQS) ...

  worker (SQS consumer)
    └─ HarvesterConsumer → HarvesterProcessorImpl
         └─ HarvesterCoreImpl.processTask(...)
              └─ DataSourceGenerationStrategyFactory.getStrategy(reportType, version)  ← routing
                   └─ BaseDataSourceGenerationStrategy (e.g. EinvoiceMalaysiaDataSource / MyTransactionalReconDataSource)
                        ├─ extractDataStream()     ← read from Mongo (Flux)
                        └─ transformDataStream()   ← map to Avro GenericRecords
              └─ WriterStrategy.generateFilesAndGetUrlList(...)  ← CSV / Parquet / Excel → S3
         └─ HarvesterDataResponse (S3 URLs, optionally pre-signed)

Frontend
  └─ GET /internal/data/v1/task?asyncTaskId=...   ← poll status
  └─ GET /{mode}/public/v1/reports/{activityId}/response/download   ← get pre-signed S3 URL
```

---

## 4. Key Design Patterns

### 4.1 Registry + Annotation-driven Auto-Discovery + Service Locator

Both flows use the same idea: implementations **declare their own capabilities** via a custom
annotation, a factory **collects all of them at startup** and indexes them into a lookup map,
and callers **look up the right implementation at runtime**.

- **BFF side:** `@DataReadService(templatesSupported = {...})` + `TemplateFactory`
- **Report side:** `@DataSourceGenerator(reportTypes = {...}, version = V1)` + `DataSourceGenerationStrategyFactory`

### 4.2 Strategy Pattern

Each data source is a strategy with a common interface (`IDataReadSource` for BFF,
`BaseDataSourceGenerationStrategy` for reports). Behavior varies by template/report type,
but the orchestration code is generic.

### 4.3 Config-driven behavior

Field mappings, filter translations, output schemas, and CSV headers live in **JSON config
files** — not hardcoded — so new report types and countries are mostly configuration.

---

## 5. Reactive Programming (Project Reactor) primer

The codebase returns `Mono<T>` and `Flux<T>` everywhere:

- **`Mono<T>`** — an async publisher of **0 or 1** item (async `Optional`). Used for single
  responses like `viewSummary`.
- **`Flux<T>`** — an async publisher of **0..N** items (async stream). Used for lists and DB
  result streams.

Benefits realized here:
- **Streaming from MongoDB** — documents flow through as a `Flux` instead of loading everything
  into memory at once.
- **Back-pressure** — a slow consumer naturally slows the producer.
- **Non-blocking I/O** — threads are not held while waiting on the DB.

Note: at some service boundaries the code calls `.collectList().share().block()` to convert the
reactive stream back into a synchronous `List` (because parts of the BFF API return plain lists).
So the DB layer is reactive even where the controller contract is blocking.

---

## 6. Multi-tenancy & Mode-awareness

- **Mode** (`Mode` enum: `EINVOICE_MY`, `TDS`, …) selects which country/product beans load.
  Beans are guarded with `@ConditionalOnExpression(ModeAwareMongoTemplateMapping.MODE_INIT_CONDITIONAL_EINVOICE_MY)`.
- **Tenant MongoTemplate** — repositories inject `@Qualifier("tenantReactiveMongoTemplate")`,
  a per-tenant Mongo connection, so each customer's data is isolated.
- **Environment-driven config** — nearly everything in `application.yml` is `${ENV_VAR}` based
  (`MODE`, `DB_TYPE`, `REGION`, Mongo/Postgres/S3/SQS credentials, etc.).

---

## 7. Resume-point → document map

| Resume bullet | Document |
|---------------|----------|
| Built Data-Harvester, generic & configurable microservice | `01-data-harvester-microservice.md` |
| Shipped Transactional Reconciliation (Malaysia, -57% inconsistency) | `02-transactional-reconciliation.md` |
| Facilitated e-invoicing expansion to Singapore (templates & configs) | `03-einvoicing-expansion-templates.md` |
| Led 6-week on-call rotation (-38% resolution time) | `04-incident-management-oncall.md` |
| Boosted Malaysia test coverage 30% → 90% (AI agents + JUnit5) | `05-automated-testing.md` |
