---
tags: [resume/cleartax, status/draft]
created: 2026-09-19
---
# Singapore Expansion

> [!abstract] `resume:cleartax-singapore`. Same PDF sentence as recon. This snapshot has **Malaysia folders, not Singapore**. You defend the *machinery*, or you name the other branch. Deep file: [[03-einvoicing-expansion-templates]].

## Resume line

Facilitated e-invoicing expansion to Singapore by crafting essential system templates and configurations.

## Truth

`Mode` in the snapshot: `EINVOICE_MY`, `TDS`. Comment in the writeup: SG would be `EINVOICE_SG`. **No `SG/` resource tree here.**

What *is* here — the MY blueprint you would copy:

**BFF** — `einvoice-data-read-service/src/main/resources/MY/{TEMPLATE}/`

- `EinvoiceMyFieldWithDBPath.json`
- `EinvoiceMyFieldWithDbOptions.json`
- `NodeTypeDBPathMapping.json`
- `SCHEMA_MAPPING/`

Templates present: sales, sales B2C, sales conso, purchase, purchase conso, transactional recon.

**Reports** — `read-data-source/.../mode/EINVOICE_MY/{REPORT}/` — 14 types (lite/detailed, B2C, conso, email summary, sales/purchase recon, transactional recon). Each: Avro `output-schema.json`, `report-config.json`, schema maps.

**Excel "system templates"** — S3 URLs in `application.yml` under `reports.templates.*` (e.g. email delivery xlsx). New country = new key + env var.

**DB:** `ModeAwareMongoConfiguration`, per-mode `@Qualifier`, Worker imports `TenantMongoConfig*`. New country = new config class + new `MODE_INIT_CONDITIONAL_*`.

## One-minute pitch

Singapore was not "rewrite Harvester." It was drop `Mode.EINVOICE_SG`, a `SG/` JSON tree, report folders under `EINVOICE_SG`, annotated strategies, tenant Mongo config. Factories auto-pick. If they ask to see `EinvoiceSg*.json` and I only have MY in this tree, I say that — I will not pretend the files are in the repo I studied from.

## Add-SG checklist (the interview whiteboard)

1. Enums: `Mode`, `TemplateType`, `ReportType`.
2. `resources/SG/{TEMPLATE}/` field/option/node JSON.
3. `mode/EINVOICE_SG/{REPORT}/` schema + headers.
4. `@DataReadService` + `@DataSourceGenerator` classes, SG `@ConditionalOnExpression`.
5. `TenantMongoConfig*` + Worker import.
6. Excel keys if needed.
7. Repos only if new collections.

Core / writers / `TemplateFactory` stay closed. That is the OCP story — link [[01 - Data Harvester]], do not re-explain Strategy.

## Ugly questions

**Show me the SG commit.** This snapshot does not have it. Other branch / other service. What *you* did: which files you actually added. If it was "I followed the MY checklist and someone else merged SG," say that.

**Why configs not a country table in Mongo?** Convention path `mode/{mode}/{reportType}/report-config.json` — deploy with the jar, versioned with code. Tradeoff: change needs a release.

**Would SG beans load in a MY pod?** No — mode conditionals. Wrong `MODE` env is how you get "No report generators found" ([[04 - On Call]]).

**TDS in the same binary?** Yes, `Mode.TDS`. Same trick. Don't wander into TDS unless you owned it.

## Related Notes

- [[00 - ClearTax Ownership]]
- [[01 - Data Harvester]]
- [[03-einvoicing-expansion-templates]]
