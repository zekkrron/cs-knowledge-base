# Resume Point 4 — Automated Testing (Coverage 30% → 90%)

> **Resume bullet:** "Boosted Malaysia e-invoicing test coverage from 30% to 90% using AI
> Coding Agents and JUnit5."

This document describes the testing infrastructure in the repo: the frameworks, the JaCoCo
coverage setup, how reactive code is tested, and how to write tests for the Malaysia e-invoicing
data sources/services.

---

## 1. Test stack

From the parent `pom.xml`:

- **JUnit 5** (JUnit Jupiter) via `spring-boot-starter-test`.
- **Reactor Test** (`reactor-test`) — `StepVerifier` for asserting on `Mono`/`Flux`.
- **Spring Boot Test** — `@SpringBootTest`, `@ActiveProfiles`, `WebTestClient` for controller
  slices.
- **JaCoCo** (`jacoco-maven-plugin` 0.8.9) — coverage instrumentation + reporting.

---

## 2. JaCoCo coverage setup

### 2.1 Per-module agent + report

Parent `pom.xml` attaches JaCoCo to every module:

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>${jacoco-maven-plugin.version}</version>
  <executions>
    <execution><id>default-prepare-agent</id><goals><goal>prepare-agent</goal></goals></execution>
    <execution><id>default-report</id><goals><goal>report</goal></goals></execution>
  </executions>
</plugin>
```

> Note: the `default-check` coverage-gate execution (60% complexity ratio) is present but
> **commented out** — so builds don't yet fail below a threshold. Enabling it is the natural
> next step once coverage is high.

### 2.2 Aggregate report (`test-report` module)

`test-report/pom.xml` merges each module's `jacoco.exec` into one aggregate report and excludes
non-logic packages so the % reflects real business-logic coverage:

```xml
<excludes>
  <exclude>**/models/**/*</exclude>
  <exclude>**/model/**/*</exclude>
  <exclude>**/dto/**/*</exclude>
  <exclude>**/dtos/**/*</exclude>
  <exclude>**/*Dto*</exclude>
  <exclude>**/dao/**/*</exclude>
  <exclude>**/config/**/*</exclude>
  <exclude>**/exception/*</exclude>
  <exclude>**/exceptions/*</exclude>
</excludes>
```

It runs two goals in `verify`:
- `report-aggregate` — combined HTML/XML coverage report.
- `merge` — merges all `**/target/jacoco.exec` into `target/aggregate.exec`.

Aggregate HTML output lives at:
`test-report/target/site/jacoco-aggregate/` (browsable per module/class).

### 2.3 Surefire (test execution)

`test-report/pom.xml` surefire config:

```xml
<argLine>${argLine} -Xms256m -Xmx2048m</argLine>   <!-- ${argLine} carries the JaCoCo agent -->
<forkCount>1</forkCount>
<runOrder>random</runOrder>                          <!-- random order flushes out inter-test coupling -->
```

> Important: `${argLine}` must be preserved — it injects the JaCoCo agent. Overwriting it (not
> appending) silently disables coverage instrumentation.

---

## 3. What "Malaysia e-invoicing" coverage targets

The high-value, logic-heavy classes to cover (all in the MY e-invoicing paths):

**BFF (`einvoice-data-read-service`)**
- `EinvoiceDataReadService` (base: viewSummary/viewList/viewDetailed, currency conversion,
  custom-field handling)
- `EinvoiceTransactionalReconReadService` (summary composition + mismatch flattening)
- `EinvoiceQueryBuilder` (aggregation pipeline construction, filter criteria, mismatch query)
- `EInvoiceRepositoryFactory` (template → repository routing)

**Report (`read-data-source`)**
- `EinvoiceMalaysiaDataSource` (query result + timezone mapping)
- `MyTransactionalReconDataSource` (extract/transform, node-type access filter, filenames)
- `MyReconDataSourceHelper` (`addFieldToRecord`, aggregation grouping)
- `ReportHelper` (schema/custom-field/CSV-header/report-config logic)

**Core (`harvester-core`)**
- `HarvesterCoreImpl` (caching, force refresh, validation, pre-signed URLs)
- `DataSourceGenerationStrategyFactory` (routing + duplicate detection)

---

## 4. Testing reactive code (patterns to use)

### 4.1 `StepVerifier` for `Mono`/`Flux`

```java
StepVerifier.create(service.viewSummary(filter, config, hierarchy))
    .assertNext(resp -> {
        assertThat(resp.getSummary().get("eInvoiceMismatchCount")).isEqualTo(3);
    })
    .verifyComplete();
```

```java
StepVerifier.create(dataSource.transformDataStream(schema, request, mismatchDoc))
    .expectNextCount(5)   // 5 flattened field-mismatch rows
    .verifyComplete();
```

### 4.2 Mocking the reactive repository

Return canned `Flux`/`Mono` from mocked repositories so tests don't need a live Mongo:

```java
when(einvoiceMyTransactionalReconRepository.findByAggregation(any()))
    .thenReturn(Flux.just(groupedReconData));
```

### 4.3 Controller slice tests

`WebTestClient` (see the commented `DataRequestControllerTest`) exercises the HTTP layer:

```java
webTestClient.post().uri("/internal/data/v1/generate")
    .bodyValue(harvesterDataRequest)
    .exchange().expectStatus().is2xxSuccessful();
```

### 4.4 Integration-style data source test

The commented `SalesDataSourceTest` shows the end-to-end pattern: insert fixture docs into a
Mongo template, run `harvesterCore.processTask(...)`, assert a non-null response. Fixtures live
in `worker/src/test/resources/` (e.g. `sales/data.json`).

---

## 5. Existing tests in the repo (current state)

The repository currently contains mostly **commented-out / disabled** test skeletons:
- `worker/.../datasource/SalesDataSourceTest.java` (commented)
- `worker/.../consumer/HarvesterConsumerTest.java` (`@Disabled` — "No report generators found")
- `http-server/.../routes/DataRequestControllerTest.java` (commented)

These are the scaffolds that the coverage push (using AI coding agents + JUnit5) would flesh out
and enable — turning skeletons into running tests across the MY BFF, recon, and core paths.

---

## 6. How to run coverage locally

```bash
# Run all tests + build the aggregate coverage report
mvn clean verify

# Aggregate HTML report:
#   test-report/target/site/jacoco-aggregate/index.html
```

For a focused module:

```bash
mvn -pl einvoice-data-read-service -am test
```

---

## 7. Using AI coding agents effectively (approach)

- Generate JUnit5 + `StepVerifier` tests per public method, feeding the agent the class under
  test plus its collaborators' interfaces.
- Prioritize branch coverage in logic-heavy methods (e.g. `viewSummary` count composition,
  `transformDataStream` document- vs line-level branches, currency conversion guards).
- Mock reactive collaborators to keep tests fast and deterministic (no live Mongo/S3/SQS).
- Keep JaCoCo excludes honest (models/DTOs/config excluded) so the % reflects real coverage.
- Use `runOrder=random` locally to catch tests that leak shared state.

---

## 8. Talking points for interviews

- JaCoCo per-module agent + aggregate merge report; meaningful excludes for models/DTOs/config.
- Reactive testing with `StepVerifier`; mocking `Flux`/`Mono` repositories.
- Controller slice tests via `WebTestClient`; integration data-source tests with Mongo fixtures.
- AI-assisted test generation focused on branch coverage of the MY e-invoicing + recon logic.
- Next step: enable the commented JaCoCo `check` gate to enforce the coverage floor in CI.
