---
tags: [languages/spring-boot, status/draft]
created: 2026-10-14
---
# Spring Boot Version History

> [!abstract] What this is
> The Spring Boot releases that matter, from 1.0 (2014) to **4.1** (June 2026), the Spring Framework generation under each, the support model, and the features that define each line. The decision this helps with: knowing what a codebase on a given Boot version can use, planning 2.x → 3.x → 4.x upgrades, and answering "what changed in Boot 3 / Boot 4?" precisely.

---

## Why It Matters

- Boot versions pin a whole stack: Spring Framework, Jakarta EE level, Hibernate, Jackson, Tomcat, Micrometer, JUnit. "We're on Boot 2.7" also means `javax.*`, Java 8/11, Hibernate 5.
- The two big migrations (**2 → 3**: `javax` → `jakarta`, Java 17; **3 → 4**: Jackson 3, modular auto-configuration, Framework 7) are common interview and real-project topics.

---

## Support Model

- A **minor release roughly every six months** (May/June and November), each with about **13 months of free OSS support**; commercial support extends it.
- Plan to move minors regularly; 4.0 loses OSS support in Dec 2026, the 3.x line lost it in Jun 2026.
- The Boot version decides the **dependency BOM** (`spring-boot-dependencies`): upgrade Boot, and hundreds of library versions move together ([[14 - Java Build and Dependencies]]).

| Boot line | Framework | Java baseline | Jakarta / Java EE | Years |
|---|---|---|---|---|
| **1.x** | 4.x | 6/7 → 8 | Java EE (`javax`) | 2014–2019 |
| **2.x** | 5.x | 8 | Java EE 8 (`javax`) | 2018–2023 |
| **3.x** | 6.x | **17** | **Jakarta EE 9/10 (`jakarta`)** | 2022–2026 |
| **4.x** | **7.x** | 17 (first-class 25) | **Jakarta EE 11** (Servlet 6.1) | 2025– |

---

## The Lines

### Boot 1 (2014–2018) — convention over configuration

- **Auto-configuration**, **starters**, embedded Tomcat/Jetty, executable fat jars, externalised config (`application.properties`), **Actuator**. Spring without XML and without an app server.

### Boot 2 (2018–2023)

| Version | Highlights |
|---|---|
| **2.0** | Framework 5: **WebFlux** and Reactor, **Micrometer** metrics, Actuator rewritten, **HikariCP** default pool, relaxed binding rules, Java 8 baseline |
| **2.1–2.2** | Lazy initialisation option, Java 11 support, JUnit 5 default |
| **2.3** | **Graceful shutdown**, **liveness/readiness** probe support, **layered jars** and buildpack images |
| **2.4** | **Config data** API: `spring.config.import`, new profile file processing |
| **2.6** | **Circular references prohibited by default** |
| **2.7** | New auto-configuration registration (`AutoConfiguration.imports`), last 2.x line |

### Boot 3 (Nov 2022–2026) — Jakarta and native

| Version | Highlights |
|---|---|
| **3.0** | **Java 17 baseline**, **`javax.*` → `jakarta.*`**, **GraalVM native image** and **AOT processing**, **Micrometer Observation** + Tracing (Sleuth replaced), **Problem Details** (RFC 7807) support, HTTP interface clients |
| **3.1** | **Docker Compose** support, **Testcontainers service connections** (`@ServiceConnection`) |
| **3.2** | **Virtual threads** (`spring.threads.virtual.enabled`), **`RestClient`**, **`JdbcClient`**, **CRaC** support, SSL bundles |
| **3.3** | **CDS** support, SBOM Actuator endpoint |
| **3.4** | **Structured logging** (ECS, Logstash, GELF formats) |
| **3.5** (May 2025) | Last 3.x minor |

### Boot 4.0 (Nov 2025) — Framework 7

- **Spring Framework 7**, **Jakarta EE 11** (Servlet 6.1, JPA 3.2, Bean Validation 3.1), **Hibernate 7**, **JUnit 6**, Kotlin 2.2 baseline; Java 17 still the minimum, Java 25 first-class.
- **Jackson 3** as the default JSON library (new `tools.jackson` packages; Jackson 2 kept as a deprecated compatibility option).
- **Modularised auto-configuration**: the single `spring-boot-autoconfigure` jar split into focused modules with matching starters (e.g. `spring-boot-webmvc`, `spring-boot-jdbc`); "classic" starters ease migration ([[02 - Spring Boot Auto-Configuration and Config]]).
- **Null-safety with JSpecify** annotations across the portfolio.
- **API versioning** built into Spring MVC and WebFlux ([[10 - Spring Validation Errors and API Design]]).
- **HTTP service clients** auto-configured (`@ImportHttpServices` for interface-based clients) ([[11 - Spring HTTP Clients Resilience and Messaging]]).
- **Resilience in the core framework**: `@Retryable` and `@ConcurrencyLimit` (enabled with `@EnableResilientMethods`).
- **Programmatic bean registration** with `BeanRegistrar` ([[01 - Spring IoC and Bean Lifecycle]]).
- **Undertow dropped** (not Servlet 6.1 compatible); Tomcat and Jetty remain.

### Boot 4.1 (Jun 2026)

- **gRPC** auto-configuration (server and client; Netty standalone or over the servlet container on the same port).
- **SSRF mitigation** options for auto-configured HTTP clients.
- **Lazy DataSource connections**, **context propagation for `@Async`** (tracing and MDC follow async calls), OpenTelemetry improvements, Kotlin 2.3.
- The deprecated `layertools` jar mode removed; use `-Djarmode=tools`.

---

## Upgrade Notes That Come Up

| Upgrade | What usually bites |
|---|---|
| **2.x → 3.0** | `javax` → `jakarta` imports (and every library must have a Jakarta version), Java 17, Spring Security 6 config changes (`WebSecurityConfigurerAdapter` gone), Hibernate 6 query changes, Sleuth → Micrometer Tracing, property renames |
| **3.x → 4.0** | Jackson 3 package and default changes, modular starters (missing auto-config if a module isn't on the classpath), Undertow removal, JUnit 6 / Mockito changes, `@MockBean` → `@MockitoBean`, deprecated APIs removed |
| **Any minor** | Property renames and deprecations |

- Approach: move to the **latest patch of the current minor** first, fix deprecation warnings, then upgrade; use the **`spring-boot-properties-migrator`** module at runtime to report renamed properties, and OpenRewrite recipes for mechanical code changes.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Overriding many BOM versions** | Untested combinations; upgrade pain | Let Boot manage versions; override only with reason |
| **Staying on an unsupported line** | No security patches | Upgrade minors within ~13 months |
| **Mixed `javax` and `jakarta` libraries** | `ClassNotFoundException`, beans silently missing | Use Jakarta versions of every dependency on Boot 3+ |
| **Big-bang multi-major upgrade** | Hard to bisect failures | One line at a time, with the properties migrator and full tests |

---

## Interview Lines

> [!tip] Say it like this
> - "Boot 3 moved to Java 17 and the `jakarta` namespace and added native images and Micrometer Observation; Boot 4 is built on Framework 7 and Jakarta EE 11, with Jackson 3, modular auto-configuration, API versioning and JSpecify null-safety."
> - "Boot 3.2 added virtual threads, `RestClient` and `JdbcClient`; 3.1 added Testcontainers service connections."
> - "Each minor gets about 13 months of OSS support, so I upgrade minors regularly rather than doing rare big jumps."
> - "Boot's BOM is the real version contract; I override individual library versions only with a reason."

## Related Notes

- [[01 - Spring IoC and Bean Lifecycle]] · [[02 - Spring Boot Auto-Configuration and Config]] · [[00 - Java Version History]] · [[14 - Java Build and Dependencies]]
- [[Languages/README]]
