# Resume Point 2b — E-Invoicing Expansion (Singapore) via Templates & Configurations

> **Resume bullet:** "Facilitated e-invoicing expansion to Singapore by crafting essential
> system templates and configurations."

This document explains the **template + configuration system** that makes country expansion
possible, using the fully-built **Malaysia (MY)** implementation as the reference blueprint that
a new country (e.g. **Singapore / SG**) would follow.

> Note: Singapore-specific files are not present in this repository snapshot (the SG work was
> done on a separate branch / dependent services). What *is* here is the complete, reusable
> template-and-config machinery — this doc documents that machinery and the exact steps to
> extend it to a new country.

---

## 1. The two config surfaces a country needs

Expanding e-invoicing to a new country touches **two** configuration surfaces, one per flow:

### 1.1 BFF configs (frontend data serving)

Location: `einvoice-data-read-service/src/main/resources/{COUNTRY_CODE}/{TEMPLATE_TYPE}/`

For Malaysia (`MY/`), each template folder contains:

| File | Purpose |
|------|---------|
| `EinvoiceMyFieldWithDBPath.json` | frontend field name → MongoDB document path |
| `EinvoiceMyFieldWithDbOptions.json` | frontend display value → DB enum value (e.g. "Submitted" → "SUBMITTED") |
| `NodeTypeDBPathMapping.json` | business-hierarchy node type → DB field used for access control |
| `SCHEMA_MAPPING/` | Schema Registry rules to convert DB shape → view-response shape |

Existing MY template folders:

```
einvoice-data-read-service/src/main/resources/MY/
├── EINVOICE_MY_SALES/
├── EINVOICE_MY_SALES_B2C/
├── EINVOICE_MY_SALES_CONSO/
├── EINVOICE_MY_PURCHASE/
├── EINVOICE_MY_PURCHASE_CONSO/
└── EINVOICE_MY_TRANSACTIONAL_RECON/
```

These maps are loaded in the read-service constructors and passed into `EinvoiceQueryBuilder` to
translate UI filters into MongoDB aggregation criteria (see `01` §3.2 and `02` §4).

### 1.2 Report configs (file export)

Location: `read-data-source/src/main/resources/mode/{COUNTRY_MODE}/{REPORT_TYPE}/`

For Malaysia (`EINVOICE_MY/`), each report folder contains:

| File | Purpose |
|------|---------|
| `output-schema.json` | Avro schema = output columns of the exported file |
| `report-config.json` | filter field→DB path mapping + CSV header mapping + schema-conversion config |
| `SCHEMA_MAPPING/` | Schema Registry field-transformation rules |

Existing MY report folders (14 report types):

```
read-data-source/src/main/resources/mode/EINVOICE_MY/
├── EINVOICE_MY_SALES_LITE/            ├── EINVOICE_MY_PURCHASE_LITE/
├── EINVOICE_MY_SALES_DETAILED/        ├── EINVOICE_MY_PURCHASE_DETAILED/
├── EINVOICE_MY_SALES_B2C_LITE/        ├── EINVOICE_MY_PURCHASE_CONSO_LITE/
├── EINVOICE_MY_SALES_B2C_DETAILED/    ├── EINVOICE_MY_PURCHASE_CONSO_DETAILED/
├── EINVOICE_MY_SALES_CONSO_LITE/      ├── EINVOICE_MY_PURCHASE_RECON/
├── EINVOICE_MY_SALES_CONSO_DETAILED/  ├── EINVOICE_MY_SALES_RECON/
├── EINVOICE_MY_EMAIL_SUMMARY/         └── EINVOICE_MY_TRANSACTIONAL_RECON/
```

---

## 2. The enums that anchor a country/template

### 2.1 `Mode` — the country/product selector

`data-read-service/.../enums/Mode.java`:

```java
public enum Mode {
    EINVOICE_MY("einvoice-my"),
    TDS("tds");
    // A Singapore expansion would add e.g. EINVOICE_SG("einvoice-sg")
}
```

The active mode is injected from `${MODE}` and controls **which beans load** via
`@ConditionalOnExpression(ModeAwareMongoTemplateMapping.MODE_INIT_CONDITIONAL_EINVOICE_MY)`.

### 2.2 `TemplateType` — BFF views

`data-read-service/.../enums/TemplateType.java` — one value per UI view (SALES, PURCHASE,
B2C, CONSOLIDATED, TRANSACTIONAL_RECON, …).

### 2.3 `ReportType` — export report identities

Used by `@DataSourceGenerator(reportTypes = {...})` on report data sources.

---

## 3. Excel report templates (external assets)

Some reports render into pre-built Excel templates hosted on S3, wired via `application.yml`:

```yaml
reports:
  templates:
    EinvoiceMyEmailDeliveryReport: ${EINVOICE_MY_EMAIL_DELIVERY_REPORT_TEMPLATE:http://.../Einvoice_My_Email_Delivery_Report_format_template.xlsx}
```

A new country's report templates are added here as new keys + env vars — the "system templates"
part of the resume bullet.

---

## 4. The mode-aware DB wiring

Country isolation is enforced at the persistence layer:

- `ModeAwareMongoConfiguration` / `ModeAwarePostgresConfiguration` set up connections based on
  the active mode.
- Repositories inject a mode-specific template, e.g.
  `@Qualifier(ModeAwareMongoTemplateMapping.MONGO_TEMPLATE_MODE_EINVOICE_MY)`.
- `Worker` imports the tenant Mongo configs it needs
  (`TenantMongoConfigSales`, `TenantMongoConfigPurchase`, `TenantMongoConfigEinvoiceIndia`,
  `TenantMongoConfigEinvoiceReconKSA`, …). A new country adds its own `TenantMongoConfig*`.

---

## 5. Step-by-step: add a new country (Singapore blueprint)

Following the MY pattern exactly, expanding to SG requires:

1. **Enums**
   - `Mode`: add `EINVOICE_SG("einvoice-sg")`.
   - `TemplateType`: add SG view types (e.g. `EINVOICE_SG_SALES`, `EINVOICE_SG_PURCHASE`, …).
   - `ReportType`: add SG export report types.

2. **BFF configs** under `einvoice-data-read-service/src/main/resources/SG/{TEMPLATE_TYPE}/`:
   - `EinvoiceSgFieldWithDBPath.json`, `EinvoiceSgFieldWithDbOptions.json`,
     `NodeTypeDBPathMapping.json`, `SCHEMA_MAPPING/`.

3. **Report configs** under `read-data-source/src/main/resources/mode/EINVOICE_SG/{REPORT_TYPE}/`:
   - `output-schema.json`, `report-config.json`, `SCHEMA_MAPPING/`.

4. **Data source classes**
   - BFF: a class implementing `IDataReadSource` annotated
     `@DataReadService(templatesSupported = "einvoice_sg_sales")`.
   - Report: a class extending `ReportHelper<...>` annotated
     `@DataSourceGenerator(reportTypes = {ReportType.EINVOICE_SG_SALES_LITE, ...}, version = V1)`
     and gated with the SG conditional expression.

5. **DB wiring**
   - New `TenantMongoConfig*` for SG, imported in `Worker`.
   - New mode conditional constant in `ModeAwareMongoTemplateMapping`.

6. **Excel templates (if any)** — add keys under `reports.templates` in `application.yml`.

7. **Repository + factory** — if SG uses new collections, add repository impls and register
   them in the repository factory (BFF) or rely on the report-side strategy factory (reports).

None of the core orchestration (`HarvesterCoreImpl`, controllers, `WriterStrategyFactory`,
`DataSourceGenerationStrategyFactory`, `TemplateFactory`) changes — the whole expansion is
templates + configs + a couple of annotated classes. That is exactly what "crafting essential
system templates and configurations" means in this codebase.

---

## 6. Why this design makes expansion cheap

- **Convention-based config loading**: `ReportHelper.getReportConfig()` resolves
  `mode/{mode}/{reportType}/report-config.json` automatically — drop the file in the right place
  and it's picked up.
- **Annotation-driven registration**: no central wiring to edit; the factories auto-discover new
  annotated classes at startup.
- **Schema Registry indirection**: DB-shape → view/export-shape conversions are declared as
  mapping files rather than code.
- **Mode gating**: beans for a country only load when `MODE` matches, so countries don't
  interfere with each other and can be deployed independently.

---

## 7. Talking points for interviews

- Expansion = configs + templates + annotated strategy classes, not core changes.
- Two config surfaces: BFF field/option/node mappings, and report schema/header/mapping.
- Mode-aware, multi-tenant DB isolation per country.
- Schema Registry decouples storage shape from presentation/export shape.
