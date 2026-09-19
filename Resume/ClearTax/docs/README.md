# Clear Data Harvester — Source Writeups

Lives under `Resume/ClearTax/docs/` (not the vault root). Defence notes (what to *say*) are the parent folder — start at [[00 - ClearTax Ownership]].

This folder documents the code behind each ClearTax experience bullet from `resume.tex`. It's
written so you can walk into an interview and speak to the actual implementation, not just the
resume summary.

## Read in this order

1. **[00-architecture-overview.md](./00-architecture-overview.md)** — start here. The whole
   system: modules, the two core flows (BFF + async report), design patterns, reactive primer,
   multi-tenancy.

## Resume bullet → document

| # | Resume bullet (ClearTax) | Document |
|---|--------------------------|----------|
| 1 | Built Data-Harvester, a generic & configurable data retrieval microservice for Global-Einvoicing | [01-data-harvester-microservice.md](./01-data-harvester-microservice.md) |
| 2a | Shipped Transactional Reconciliation in Malaysia (−57% data inconsistency) | [02-transactional-reconciliation.md](./02-transactional-reconciliation.md) |
| 2b | Facilitated e-invoicing expansion to Singapore (templates & configurations) | [03-einvoicing-expansion-templates.md](./03-einvoicing-expansion-templates.md) |
| 3 | Led a 6-week on-call rotation for Malaysia & Global (−38% resolution time) | [04-incident-management-oncall.md](./04-incident-management-oncall.md) |
| 4 | Boosted Malaysia e-invoicing test coverage 30% → 90% (AI agents + JUnit5) | [05-automated-testing.md](./05-automated-testing.md) |

## Quick facts

- **Stack:** Java 17, Spring Boot 2.7.11, Project Reactor (WebFlux), MongoDB (reactive),
  AWS SQS, AWS S3, Apache Avro, Temporal (scheduler), JaCoCo/JUnit5.
- **Two flows:** synchronous BFF (viewSummary/viewList/viewDetailed) + asynchronous report
  generation (SQS → Worker → S3).
- **Two plugin registries:** `@DataReadService` + `TemplateFactory` (BFF), and
  `@DataSourceGenerator` + `DataSourceGenerationStrategyFactory` (reports).
- **Config-driven:** JSON schema/filter/header configs per country/report type, plus
  env-driven Spring profiles and mode-aware DB wiring.
