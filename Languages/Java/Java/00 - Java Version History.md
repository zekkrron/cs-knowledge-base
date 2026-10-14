---
tags: [languages/java, status/draft]
created: 2026-10-14
---
# Java Version History

> [!abstract] What this is
> The Java releases that matter and what each one brought, from Java 5 to JDK 27: the **LTS** versions companies actually run (8, 11, 17, 21, 25), the release cadence, how preview features work, and which features define each era. The decision this helps with: knowing what a codebase on a given version can use, and answering "what changed in Java X?" without guessing.

---

## Why It Matters

- Most production Java runs on an **LTS** release, and upgrades are planned around them. "We're on 17, should we move to 21 or 25?" is a real conversation; the answer is a list of features, GC changes and removals.
- Many interview questions are really version questions: virtual threads (21), pinning fixes (24), records and sealed types (16/17), lambdas and streams (8), modules (9).

---

## Release Model

- **Before 2018**: big releases every few years (5 in 2004, 6 in 2006, 7 in 2011, 8 in 2014, 9 in 2017).
- **Since Java 10 (2018)**: a **feature release every six months** (March and September), numbered by version.
- **LTS (long-term support)**: one release is designated for multi-year vendor support. LTS releases so far: **8, 11, 17, 21, 25**. Since 17, LTS comes **every two years** (17 → 21 → 25 → next expected 29).
- **Preview and incubator features**: big language/API changes ship first as **preview** (enabled with `--enable-preview`) or **incubator** modules, iterate over a few releases, then become final. A feature being "in JDK 21" may mean "preview in 21, final in 23".
- **Distributions**: OpenJDK builds from many vendors (Eclipse Temurin, Amazon Corretto, Azul Zulu, Microsoft, Red Hat, Oracle). The code is essentially the same; support terms and licensing differ.

---

## The Big Eras

### Java 5 (2004) — modern Java begins

- **Generics**, **annotations**, **enums**, **autoboxing**, **varargs**, enhanced `for` loop.
- **`java.util.concurrent`** (executors, concurrent collections, locks, atomics) and a revised **Java Memory Model** (JSR-133), the foundation of everything in [[LLD/Concurrency/README]].

### Java 7 (2011)

- **try-with-resources**, multi-catch, diamond operator (`new HashMap<>()`), strings in `switch`.
- **NIO.2** (`java.nio.file`), **fork/join** framework, `invokedynamic` in the JVM (later used for lambdas).

### Java 8 (2014, LTS) — the functional era

- **Lambdas** and **method references**, **streams**, **`Optional`**, **default methods** in interfaces.
- **`java.time`** (replacing `Date`/`Calendar`), **`CompletableFuture`**.
- **Metaspace** replaced PermGen for class metadata ([[03 - JVM Memory Layout]]).
- Still widely deployed; many legacy systems never left it.

### Java 9–11 (2017–2018) — modules and the new cadence

| Version | Highlights |
|---|---|
| **9** | **Module system (JPMS)**, JShell REPL, **G1 becomes the default GC**, compact strings (Latin-1 strings use 1 byte per char), `List.of`/`Map.of` factories, `Flow` (reactive streams interfaces) |
| **10** | **`var`** for local variables; container awareness improvements |
| **11 (LTS)** | Standard **`HttpClient`** (HTTP/2), new `String` methods (`isBlank`, `strip`, `lines`, `repeat`), run single-file programs (`java Foo.java`), **ZGC** (experimental), Epsilon GC, Flight Recorder open-sourced, **Java EE and CORBA modules removed** from the JDK |

### Java 12–17 (2019–2021) — data-oriented language features

| Version | Highlights |
|---|---|
| **12–14** | **Switch expressions** (final in 14), **helpful NullPointerException messages** (14), Shenandoah GC available |
| **15** | **Text blocks** (`"""`), **ZGC and Shenandoah production-ready**, hidden classes |
| **16** | **Records**, **pattern matching for `instanceof`**, `Stream.toList()`, strong encapsulation of JDK internals by default |
| **17 (LTS)** | **Sealed classes/interfaces**, internals strongly encapsulated (no `--illegal-access`), Security Manager deprecated for removal, new random-number generator API |

- Java 17 became the **baseline for Spring Boot 3** (and Jakarta EE), which pushed most of the industry off 8 and 11.

### Java 21 (2023, LTS) — the concurrency release

- **Virtual threads** (Project Loom): lightweight threads scheduled M:N onto carrier threads, making thread-per-request scale again ([[05 - Java Platform and Virtual Threads]], [[LLD/Concurrency/15 - Virtual Threads]]).
- **Pattern matching for `switch`** and **record patterns** (deconstructing records in patterns), final.
- **Sequenced collections** (`getFirst()`, `getLast()`, `reversed()` on lists, deques, linked sets and maps).
- **Generational ZGC** (opt-in in 21).
- Previews: structured concurrency, scoped values, unnamed patterns, string templates (later withdrawn).

### Java 22–24 (2024–2025)

| Version | Highlights |
|---|---|
| **22** | **Unnamed variables and patterns** (`_`), **Foreign Function & Memory API** final (replacement for JNI and `Unsafe` off-heap access), launch multi-file source programs |
| **23** | **Markdown documentation comments**, generational mode becomes ZGC's default |
| **24** | **Virtual threads no longer pin on `synchronized`** (JEP 491), **Stream gatherers** final (custom intermediate stream operations), **Class-File API** final, **ahead-of-time class loading and linking** (first Project Leyden feature, faster startup), post-quantum **ML-KEM / ML-DSA** algorithms, Security Manager permanently disabled |

### Java 25 (Sep 2025, LTS) — the current LTS

- **Scoped values** final: immutable, bounded per-thread context, the virtual-thread-friendly alternative to `ThreadLocal` ([[LLD/Concurrency/12 - Confinement and Immutability]]).
- **Compact source files and instance main methods** final: `void main() { ... }` without a class declaration for small programs and teaching.
- **Flexible constructor bodies** final: statements (validation) allowed before `super(...)`.
- **Module import declarations** final: `import module java.base;`.
- **Compact object headers** as a product feature: smaller object headers, less heap per object ([[03 - JVM Memory Layout]]).
- **Generational Shenandoah** as a product feature.
- More Project Leyden work: **AOT method profiling** and simpler AOT cache creation for faster startup and warm-up ([[02 - JIT Compilation and AOT]]).
- Key Derivation Function API; JFR CPU-time profiling (experimental).
- Structured concurrency remains in preview.

### Java 26–27 (2026) — after the LTS

- Feature releases continue the same themes: further startup/warm-up work (Leyden AOT caching), GC throughput improvements, **HTTP/3 support in `HttpClient`**, more pattern-matching previews (primitive types in patterns), continued structured concurrency previews, and removal of long-deprecated APIs such as Applets.
- **JDK 26 (Mar 2026)**: structured concurrency sixth preview (JEP 525).
- **JDK 27 (Sep 2026)** changes JVM defaults: **compact object headers on by default**, **G1 the default GC in all environments** (JEP 523; small containers no longer silently get Serial), **post-quantum hybrid key exchange in TLS 1.3**, and **JFR redaction** of sensitive values (environment variables, system properties, arguments). Structured concurrency is in its seventh preview (JEP 533). Project **Valhalla** (value classes, flattening objects without identity) is in early access for later releases.
- These are non-LTS: most teams evaluate them but deploy on 25.

---

## Upgrade Notes That Come Up

| Upgrade | What usually bites |
|---|---|
| **8 → 11** | Removed Java EE modules (JAXB, JAX-WS) need explicit dependencies; illegal reflective access warnings; GC default changed to G1 |
| **11 → 17** | Strong encapsulation breaks libraries using JDK internals via reflection; older bytecode-manipulation libraries need updates |
| **17 → 21** | Mostly smooth; adopt virtual threads carefully (pinning in `synchronized` until 24, `ThreadLocal` heavy libraries) |
| **21 → 25** | Security Manager gone; check agents and frameworks for support; benefit from compact headers, AOT caches, no virtual-thread pinning on `synchronized` |

- General approach: upgrade build tooling and frameworks first, run with deprecation and illegal-access warnings, run the full test suite and a canary in production, compare GC logs and latency.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Using a preview feature in production code** | Build needs `--enable-preview`; API changes next release | Use only final features in production |
| **Assuming a feature exists on your LTS** | Code doesn't compile on the deployed JDK | Set `--release` in the build to the target version |
| **Upgrading the JDK but not dependencies** | Reflection or bytecode libraries fail at runtime | Upgrade frameworks/agents first; test with warnings enabled |
| **Staying on a non-LTS** | Release goes out of support in six months | Deploy on LTS; evaluate non-LTS in CI |

---

## Interview Lines

> [!tip] Say it like this
> - "Java ships a feature release every six months and an LTS every two years; the LTS line is 8, 11, 17, 21 and now 25."
> - "Java 8 brought lambdas, streams and `java.time`; 17 brought records and sealed types; 21 brought virtual threads and pattern matching for switch."
> - "Java 24 removed virtual-thread pinning on `synchronized`; 25 finalised scoped values and made compact object headers a product option, which 27 turns on by default."
> - "Big features ship as previews first, so I check whether a feature is final in the version we deploy."

## Related Notes

- [[01 - JVM Architecture and Class Loading]] · [[04 - Java Garbage Collectors]] · [[05 - Java Platform and Virtual Threads]] · [[06 - Modern Java Language Features]]
- [[Languages/README]]
