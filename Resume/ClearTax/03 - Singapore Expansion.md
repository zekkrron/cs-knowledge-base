---
tags: [resume/cleartax, status/draft]
created: 2026-09-19
---
# Singapore Expansion

> [!abstract]
> - `resume:cleartax-singapore`
> - Same PDF sentence as recon
> - This snapshot has **Malaysia folders, not Singapore**
> - Defend the *machinery*, or name the other branch
> - Harvester / recon pointers do **not** include report generation
> - If SG work was BFF JSON + `Mode`, stay there
> - Deep: [[03-einvoicing-expansion-templates]]

## Resume line

- Facilitated e-invoicing expansion to Singapore by crafting essential system templates and configurations

---

## Truth

- `Mode` in the snapshot: `EINVOICE_MY`, `TDS`
- Writeup comment: SG would be `EINVOICE_SG`
- **No `SG/` resource tree here**
- What *is* here — the MY blueprint you would copy

### BFF (walk this)

- `einvoice-data-read-service/src/main/resources/MY/{TEMPLATE}/`
    + `EinvoiceMyFieldWithDBPath.json`
    + `EinvoiceMyFieldWithDbOptions.json`
    + `NodeTypeDBPathMapping.json`
    + `SCHEMA_MAPPING/`
- Templates present
    + sales, sales B2C, sales conso, purchase, purchase conso, transactional recon

### Report JSON (exists — not the harvester / recon claim)

- `read-data-source/.../mode/EINVOICE_MY/{REPORT}/` — 14 types
- Avro + `report-config.json`
- Only walk this on the SG bullet if **you actually added those folders**
- Runtime (SQS / worker / S3) is still not yours
- [[01 - Data Harvester]]

### Excel "system templates"

- S3 URLs in `application.yml` under `reports.templates.*`
- Config key ≠ owning generation

### DB

- `ModeAwareMongoConfiguration`
- Per-mode `@Qualifier`
- Worker imports `TenantMongoConfig*`
- New country = new config class + new `MODE_INIT_CONDITIONAL_*`

---

## Add-SG checklist (the interview whiteboard)

- **You walk (BFF — matches harvester pointer)**
    1. Enums: `Mode`, `TemplateType`
    2. `resources/SG/{TEMPLATE}/` field / option / node JSON
    3. `@DataReadService` class, SG `@ConditionalOnExpression`
    4. `TenantMongoConfig*` if new collections / mode DB
    5. Repos only if new collections
- **Only if you actually did this** (not harvester / recon, not "I built reports")
    6. `ReportType` + `mode/EINVOICE_SG/{REPORT}/` schema + headers
    7. `@DataSourceGenerator` + Worker import of the mode Mongo config
    8. Excel keys under `reports.templates`
- `TemplateFactory` stays closed
    + That is the OCP story
    + Link [[01 - Data Harvester]]
    + Do not re-explain Strategy
    + Do not walk writers / SQS

---

## One-minute pitch

- Singapore was not "rewrite Harvester"
- It was drop `Mode.EINVOICE_SG`, a `SG/` **BFF** JSON tree, `@DataReadService` + tenant Mongo config
- Factories auto-pick
- Report folders / `@DataSourceGenerator` only if you actually added them
    + Even then that is **config**, not "I owned export"
- If they ask to see `EinvoiceSg*.json` and I only have MY in this tree, I say that
- I will not pretend the files are in the repo I studied from

---

## HLD grill (this pointer)

1. Show me the SG commit
    + This snapshot does not have it
    + Other branch / other service
    + What *you* did: which files you actually added
    + If it was "I followed the MY checklist and someone else merged SG," say that
2. Why configs not a country table in Mongo?
    + Convention path `mode/{mode}/{reportType}/report-config.json`
    + Deploy with the jar, versioned with code
    + Tradeoff: change needs a release
3. Would SG beans load in a MY pod?
    + No — mode conditionals
    + Wrong `MODE` env is how you get "No report generators found"
    + [[04 - On Call]]
4. TDS in the same binary?
    + Yes, `Mode.TDS`
    + Same trick
    + Don't wander into TDS unless you owned it
5. Walk add-SG
    + Enum + `SG/` JSON + annotated class + tenant config
    + Factory stays shut
6. Report folders on the checklist?
    + Only if you added them
    + Still not "I owned generation"

---

## Related Notes

- [[00 - ClearTax Ownership]]
- [[01 - Data Harvester]]
- [[03-einvoicing-expansion-templates]]
