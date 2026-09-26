---
tags: [resume/cleartax, status/draft]
created: 2026-09-19
---
# Automated Testing

> [!abstract]
> - `resume:cleartax-testing`
> - JaCoCo + JUnit5 + `StepVerifier` are in the pom
> - **90% is not proven by the snapshot**
> - Most tests there are commented or `@Disabled`
> - Deep: [[05-automated-testing]]

## Resume line

- Malaysia e-invoicing coverage 30% → 90%, AI coding agents + JUnit5

---

## Truth

- Parent
    + JUnit Jupiter
    + `reactor-test`
    + `spring-boot-starter-test`
    + JaCoCo 0.8.9 (`prepare-agent` + `report` per module)
- `test-report` merges `jacoco.exec`
    + Excludes models / DTOs / dao / config / exceptions
    + The % is logic, not getters
    + `mvn clean verify` → `test-report/target/site/jacoco-aggregate/`
- Surefire
    + `${argLine}` **must stay** (that's the agent)
    + `runOrder=random`
    + Heap 256m–2g
- Coverage gate (`default-check` 60% complexity) is **commented out**
    + Builds do not fail on low coverage
- **What you'd actually test (MY)**
    + BFF: `EinvoiceDataReadService`, recon read service, `EinvoiceQueryBuilder`, `EInvoiceRepositoryFactory`
    + Report: `EinvoiceMalaysiaDataSource`, `MyTransactionalReconDataSource`, `MyReconDataSourceHelper`, `ReportHelper`
    + Core: `HarvesterCoreImpl`, report factory
- **How**
    + `StepVerifier` on `Mono` / `Flux`
    + Mock repos returning `Flux.just(...)`
    + `WebTestClient` on generate
    + Optional Mongo fixture `processTask` (skeleton in `SalesDataSourceTest`)
- **Snapshot reality**
    + `SalesDataSourceTest` commented
    + `HarvesterConsumerTest` `@Disabled` ("No report generators found")
    + `DataRequestControllerTest` commented
    + The 30→90 push is **not visible as green tests in this tree**
- AI
    + Generate JUnit + StepVerifier per method
    + Feed the class + collaborator interfaces
    + Chase branches (`viewSummary` composition, recon flatten, currency guards)
    + Not "AI wrote tests so 90 is true"

---

## One-minute pitch

- Reactor code is untestable if you only know `@SpringBootTest` + assert equals
- I used `StepVerifier` and mocked `Flux` so I didn't need live Mongo
- JaCoCo aggregate with honest excludes
- If they want the 90, I need the HTML from CI, not this snapshot's disabled classes

---

## Metrics

- **30% → 90%** — JaCoCo aggregate is real
    + Snapshot tests are mostly commented / `@Disabled`
    + If you cannot open the HTML, say 30→90 was the goal / CI number from internship
    + Do not grep 90 out of this tree

---

## HLD grill (this pointer)

1. Open the 90% report
    + If you cannot, say goal / CI from internship
    + Not something I can grep here
2. Did AI just generate junk?
    + You still had to fix Reactor mocks, mode conditionals
    + Consumer test dies without generators loaded
3. Why were tests disabled?
    + Worker test: factory empty unless MY beans + config load
    + Env / test-slice problem, not "tests passed"
4. Why exclude DTO from JaCoCo?
    + Otherwise 90% is Lombok
    + They will respect that *if* you say it
5. Why not enable the 60% gate?
    + Natural next step
    + Don't claim you did if it's still commented
6. What did you actually test?
    + BFF read + recon flatten + query builder
    + Report classes only if you owned that plane's tests
7. Why StepVerifier?
    + `Mono` / `Flux` without live Mongo

---

## Related Notes

- [[00 - ClearTax Ownership]]
- [[02 - Transactional Reconciliation]]
- [[05-automated-testing]]
