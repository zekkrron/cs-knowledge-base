# Resume Point 1 — Data Retrieval Microservice (Data-Harvester)

> **Resume bullet:** "Built Data-Harvester (Java, Spring Boot), a generic & configurable
> data retrieval microservice for the Global-Einvoicing team."

This document explains *what makes it a data-retrieval microservice*, and *what specifically
makes it "generic" and "configurable"*, backed by the actual code.

---

## 1. What the service does

Data-Harvester sits between the e-invoicing frontend and the data stores. It:

1. Serves the UI with data (**BFF**): document counts, lists, and detailed views.
2. Generates downloadable reports (**async export**): CSV / Parquet / Excel to S3.

Both are "data retrieval" — one is interactive/synchronous, one is bulk/asynchronous.

---

## 2. "Generic" — how the core is decoupled from any country/product

### 2.1 Annotation-driven strategy registration

New data sources plug in via annotations; the core engine never changes.

**BFF side** — `data-read-service/.../annotations/DataReadService.java`:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataReadService {
    String[] templatesSupported();   // one service can serve many templates
}
```

Usage (`einvoice-data-read-service/.../impl/EinvoiceSalesDataReadService.java`):

```java
@DataReadService(templatesSupported = "einvoice_my_sales")
@Component
public class EinvoiceSalesDataReadService extends EinvoiceDataReadService<EInvoiceUbl> { ... }
```

**Report side** — `harvester-core/.../datasource/DataSourceGenerator.java`:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface DataSourceGenerator {
    ReportType[] reportTypes();
    Version[] version();
}
```

Usage (`read-data-source/.../einvoicemy/EinvoiceMalaysiaDataSource.java`):

```java
@DataSourceGenerator(reportTypes = {
        ReportType.EINVOICE_MY_SALES_LITE, ReportType.EINVOICE_MY_SALES_DETAILED,
        ReportType.EINVOICE_MY_PURCHASE_DETAILED, ... }, version = V1)
@ConditionalOnExpression(ModeAwareMongoTemplateMapping.MODE_INIT_CONDITIONAL_EINVOICE_MY)
public class EinvoiceMalaysiaDataSource extends ReportHelper<EInvoiceBase> { ... }
```

### 2.2 Factories that auto-collect implementations

**`TemplateFactory`** (BFF) — injects a `List<IDataReadSource>`, reads each one's annotation
via reflection, and builds a `template → service` map:

```java
@Component
public class TemplateFactory {
    private final Map<String, IDataReadSource> templateDataSourceMap = new HashMap<>();

    @Autowired
    public TemplateFactory(List<IDataReadSource> dataReadSources) {
        dataReadSources.forEach(ds -> getTemplatesSupported(ds)
                .forEach(key -> templateDataSourceMap.put(key, ds)));
    }

    public IDataReadSource getTemplateDataSource(String value) {
        value = value.toLowerCase();
        if (templateDataSourceMap.get(value) == null)
            throw new RuntimeException("Template type not found " + value);
        return templateDataSourceMap.get(value);
    }
}
```

> **Known gap (good interview point):** `TemplateFactory` uses a plain `put()`, so two services
> declaring the same template silently overwrite each other. The report-side factory below is
> stricter and throws on collisions.

**`DataSourceGenerationStrategyFactory`** (reports) — keys by `reportType + version` and
**throws on duplicate declarations**:

```java
strategyMap = reportingStrategies.stream()
    .flatMap(s -> getStrategyKeys(s).map(key -> Pair.create(key, s)))
    .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond, (x, y) -> {
        throw new IllegalStateException(String.format("%s and %s have similar declaration.",
                x.getClass().getCanonicalName(), y.getClass().getCanonicalName()));
    }));
```

### 2.3 Common interfaces = uniform orchestration

Every BFF strategy implements `IDataReadSource`:

```java
public interface IDataReadSource {
    Mono<DocumentCountSummaryResponse> viewSummary(DocumentFilterDTO f, TemplateConfigDTO c, AccessibleHierarchyWithUserDetailsDTO h);
    Flux<DocumentSummaryResponseDTO>   viewList    (DocumentFilterDTO f, TemplateConfigDTO c, AccessibleHierarchyWithUserDetailsDTO h);
    Flux<DocumentSummaryResponseDTO>   viewDetailed(DocumentFilterDTO f, TemplateConfigDTO c, AccessibleHierarchyWithUserDetailsDTO h);
}
```

Every report strategy implements `BaseDataSourceGenerationStrategy<T>` with
`extractDataStream()`, `transformDataStream()`, schema hooks, filename hooks, etc. The core
processor (`HarvesterCoreImpl`) only ever talks to these interfaces — never to concrete types.

---

## 3. "Configurable" — behavior driven by JSON, env vars, and profiles

### 3.1 Per-report JSON configs

Location: `read-data-source/src/main/resources/mode/{MODE}/{REPORT_TYPE}/`

| File | Purpose |
|------|---------|
| `output-schema.json` | Avro schema defining the output columns of a report |
| `report-config.json` | Filter config (frontend field → DB path), CSV header mapping, schema-conversion config |
| `SCHEMA_MAPPING/` | Schema Registry mapping files for field transformations |

Example `output-schema.json` (Transactional Recon):

```json
{
  "type": "record",
  "name": "EINVOICE_MY_TRANSACTIONAL_RECON",
  "fields": [
    {"name": "fileName", "type": ["null", "string"]},
    {"name": "documentNumber", "type": ["null", "string"]},
    {"name": "fieldName", "type": ["null", "string"]},
    {"name": "fieldValueCt", "type": ["null", "string"]},
    {"name": "fieldValueLhdn", "type": ["null", "string"]}
  ]
}
```

Example `report-config.json` (field→DB path + CSV headers):

```json
{
  "filterConfig": {
    "ctFieldDbPathMapping": {
      "documentNumber": "documentNumber",
      "documentDateTime": "documentDate",
      "fieldName": "documentLevelMisMatches.fieldLevelMisMatchList.fieldName"
    }
  },
  "csvHeaderMapping": {
    "ctFieldToHeaderMapping": {
      "documentNumber": "Document Number",
      "fieldValueCt": "Value sent to ClearTax",
      "fieldValueLhdn": "Value reported to LHDN"
    }
  }
}
```

`ReportHelper.getReportConfig()` loads these at runtime by convention:

```java
String resourcePath = String.format("mode/%s/%s/report-config.json",
        harvesterDataRequest.getMode(), harvesterDataRequest.getReportType());
```

### 3.2 Per-template BFF configs

Location: `einvoice-data-read-service/src/main/resources/{COUNTRY}/{TEMPLATE_TYPE}/`

| File | Purpose |
|------|---------|
| `EinvoiceMyFieldWithDBPath.json` | frontend field name → MongoDB path |
| `EinvoiceMyFieldWithDbOptions.json` | frontend display value → DB enum value |
| `NodeTypeDBPathMapping.json` | business-hierarchy node type → DB field (access control) |
| `SCHEMA_MAPPING/` | schema conversion rules for the view responses |

These maps are consumed by `EinvoiceQueryBuilder` to translate a UI filter into a MongoDB query
(see `02` and the query-builder section below).

### 3.3 Environment + Spring profiles

`worker/src/main/resources/application.yml` externalizes essentially everything:

```yaml
mode: ${MODE}
dbType: ${DB_TYPE:MONGO}
isCachingEnabled: ${IS_CACHING_ENABLED:false}
region: ${REGION:IND}
spring:
  data:
    mongodb:
      tenant:
        uri: ${MONGO_TENANT_URI}
aws:
  s3:      { bucket: ${AWS_BUCKET:}, region: ${AWS_REGION:ap-south-1} }
  sqs:     { consumer: { reporting-queue: ${TENANT_QUEUE_URL} } }
```

Profiles like `application-einvoice.yml`, `application-recon.yml`, `application-sales.yml` layer
mode-specific settings on top.

### 3.4 Pluggable writers

Output format is selected at runtime via `WriterStrategyFactory.getStrategy(format, version)`:
CSV, Parquet, Excel, Simple-Excel, and batch-wise Parquet writers all exist. The strategy writes
files and returns S3 URLs (`WriterOutput`).

---

## 4. The core processor (`HarvesterCoreImpl`)

`harvester-core/.../processor/HarvesterCoreImpl.java` is the generic orchestration heart of the
report flow. Key responsibilities:

- Resolve the strategy: `dataSourceGenerationStrategyFactory.getStrategy(reportType, version)`.
- Validate the request: `strategy.validateRequest(request)`.
- Caching / change detection: if `isCachingEnabled`, compares `getLastUpdateAt()` against a
  `requestHash` via `DataChangeChecker.checkDataForChange(...)` and can serve a cached response.
- `forceRefresh` bypasses cache.
- Generate file (or return a pre-written file if `supportsPreWrittenFile`).
- Optionally convert S3 URLs into **pre-signed URLs** (`updatePreSignedUrlsIfApplicable`).

```java
public Mono<HarvesterDataResponse> processTask(HarvesterDataRequest req,
                                               DataChangeChecker checker, String requestHash) {
    BaseDataSourceGenerationStrategy<?> strategy = getDataSourceGenerationStrategy(req);
    Pair<Boolean, String> validation = strategy.validateRequest(req);
    if (!validation.getKey()) return Mono.error(new IllegalArgumentException(validation.getRight()));

    if (req.getForceRefresh())
        return generateFileAndCreateResponse(strategy, req, LocalDateTime.now());

    return Mono.defer(() -> isCachingEnabled ? strategy.getLastUpdateAt(req) : Mono.just(LocalDateTime.now()))
        .defaultIfEmpty(LocalDateTime.now())
        .flatMap(lastUpdate -> {
            if (requestHash.isEmpty() || checker == null)
                return generateFileAndCreateResponse(strategy, req, lastUpdate);
            return checker.checkDataForChange(requestHash, lastUpdate)
                .switchIfEmpty(generateFileAndCreateResponse(strategy, req, lastUpdate));
        });
}
```

---

## 5. How you'd add a brand-new report type (the "generic & configurable" payoff)

1. Add the enum value(s): `ReportType` (report side) and/or `TemplateType` (BFF side).
2. Create a resource folder `mode/{MODE}/{REPORT_TYPE}/` with `output-schema.json`,
   `report-config.json`, and `SCHEMA_MAPPING/`.
3. Write one class annotated with `@DataSourceGenerator(...)` (report) or
   `@DataReadService(...)` (BFF), extending the relevant base helper.
4. (If a new DB collection) add a repository + register it in the repository factory.

No changes to `HarvesterCoreImpl`, controllers, factories, or writers. That's the definition of
"generic & configurable" in this codebase.

---

## 6. Talking points for interviews

- Reactive, non-blocking data retrieval with Project Reactor (`Flux`/`Mono`) over MongoDB.
- Annotation-driven plugin architecture (two parallel registries: BFF + report).
- Config-as-code: JSON schema/filter/header configs + env-driven Spring profiles.
- Async decoupling via SQS + async task lifecycle with dedup and caching/change-detection.
- Multi-tenant, mode-aware DB wiring.
