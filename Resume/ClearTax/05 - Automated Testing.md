---
tags: [resume/cleartax, status/draft]
created: 2026-09-19
---
# Automated Testing

> [!abstract] `resume:cleartax-testing`. JaCoCo + JUnit5 + `StepVerifier` are in the pom. **90% is not proven by the snapshot** — most tests there are commented or `@Disabled`. Deep file: [[05-automated-testing]].

## Resume line

Malaysia e-invoicing coverage 30% → 90%, AI coding agents + JUnit5.

## Truth

Parent: JUnit Jupiter, `reactor-test`, `spring-boot-starter-test`, JaCoCo 0.8.9 (`prepare-agent` + `report` per module).

`test-report` merges `jacoco.exec`, excludes models/DTOs/dao/config/exceptions so the % is logic, not getters. `mvn clean verify` → `test-report/target/site/jacoco-aggregate/`.

Surefire: `${argLine}` **must stay** (that's the agent). `runOrder=random`. Heap 256m–2g.

Coverage gate (`default-check` 60% complexity) is **commented out**. Builds do not fail on low coverage.

**What you'd actually test (MY):**

- BFF: `EinvoiceDataReadService`, recon read service, `EinvoiceQueryBuilder`, `EInvoiceRepositoryFactory`
- Report: `EinvoiceMalaysiaDataSource`, `MyTransactionalReconDataSource`, `MyReconDataSourceHelper`, `ReportHelper`
- Core: `HarvesterCoreImpl`, report factory

**How:** `StepVerifier` on `Mono`/`Flux`; mock repos returning `Flux.just(...)`; `WebTestClient` on generate; optional Mongo fixture `processTask` (skeleton in `SalesDataSourceTest`).

**Snapshot reality:** `SalesDataSourceTest` commented, `HarvesterConsumerTest` `@Disabled` ("No report generators found"), `DataRequestControllerTest` commented. The 30→90 push is **not visible as green tests in this tree**.

AI: generate JUnit + StepVerifier per method, feed the class + collaborator interfaces, chase branches (`viewSummary` composition, recon flatten, currency guards). Not "AI wrote tests so 90 is true."

## One-minute pitch

Reactor code is untestable if you only know `@SpringBootTest` + assert equals. I used `StepVerifier` and mocked `Flux` so I didn't need live Mongo. JaCoCo aggregate with honest excludes. If they want the 90, I need the HTML from CI, not this snapshot's disabled classes.

## Ugly questions

**Open the 90% report.** If you cannot, say 30→90 was the goal / CI number from internship, not something I can grep here.

**Did AI just generate junk?** You still had to fix Reactor mocks, mode conditionals, and the consumer test that dies without generators loaded.

**Why were tests disabled?** Worker test: factory empty unless MY beans + config load. That's an env/test-slice problem, not "tests passed."

**Why exclude DTO from JaCoCo?** Otherwise 90% is Lombok. They will respect that *if* you say it.

**Why not enable the 60% gate?** Natural next step. Don't claim you did if it's still commented.

## Related Notes

- [[00 - ClearTax Ownership]]
- [[02 - Transactional Reconciliation]]
- [[05-automated-testing]]
