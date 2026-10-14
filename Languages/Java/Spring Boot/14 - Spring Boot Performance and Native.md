---
tags: [languages/spring-boot, status/draft]
created: 2026-10-14
---
# Spring Boot Performance and Native

> [!abstract] What this is
> Making Spring Boot services start fast, use less memory and run efficiently: where startup time goes, **lazy initialisation**, **CDS** and the JDK **AOT cache** (Boot 3.3+/JDK 24+), **Spring AOT processing**, **GraalVM native images** (and their closed-world limits), **CRaC** checkpoint/restore, memory footprint, and the runtime hot spots that usually matter more than JVM flags. The decision this helps with: choosing between JIT + AOT cache, native image and CRaC for a given workload, and finding the real bottleneck before tuning.

---

## Why It Matters

- A typical Boot service takes several seconds to start and minutes to reach peak performance. That's fine for long-running services, painful for autoscaling bursts, serverless and scale-to-zero.
- "Spring is slow" is usually configuration: too many dependencies, OSIV, N+1 queries, verbose logging, small pools. Measure before choosing native.

---

## Where Startup Time Goes

| Phase | Cost driver | Lever |
|---|---|---|
| JVM start + class loading | Number of classes (tens of thousands with Boot) | CDS / AOT cache; fewer dependencies |
| Classpath scanning, configuration parsing | Component scanning, `@Configuration` classes, conditions | Spring AOT; narrower scanning; Boot 4 modular starters |
| Bean creation | Eager singletons, proxies, reflection | Lazy init where safe; fewer beans |
| Infrastructure init | Hibernate metamodel, connection pools, Kafka clients, migrations | Run migrations as a job; lazy connections (4.1) |
| Warm-up | Interpreter → C1 → C2 | AOT cache with profiles (JDK 25); warm-up traffic ([[02 - JIT Compilation and AOT]]) |

- Measure with **`ApplicationStartup`** (`BufferingApplicationStartup` + `/actuator/startup`) or JFR's Spring startup events: the slowest bean initialisations stand out.

---

## Options Compared

| Option | Startup | Peak throughput | Memory | Constraints | Pick when |
|---|---|---|---|---|---|
| **Plain JVM** | Seconds | Best (C2) | Highest | None | Long-running services with steady load |
| **JVM + CDS / AOT cache** | ~2× or better faster | Same as JVM | Lower | Training run; cache tied to JDK + classpath | Default improvement for containerised services |
| **JVM + Spring AOT** (`spring.aot.enabled`) | Somewhat faster | Same | Slightly lower | Same restrictions as native at build time (fixed conditions) | With AOT cache, or as a step toward native |
| **GraalVM native image** | Tens of ms | Lower than C2 (PGO narrows it) | Much lower | Closed world, long builds, reflection metadata, different diagnostics | Serverless, CLIs, scale-to-zero, many small instances |
| **CRaC** | Tens of ms, already warm | Same as JVM | JVM-level | Linux, CRaC-enabled JDK, resources closed/reopened, snapshot contains memory | Fast scale-out of warmed JVMs where the platform supports it |

---

## CDS and the AOT Cache

```bash
# 1. Extract the executable jar (better class loading, required for caching)
java -Djarmode=tools -jar app.jar extract --destination app

# 2. Training run: start the context and exit at refresh, recording the cache (JDK 25)
java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app/app.jar

# 3. Production
java -XX:AOTCache=app.aot -jar app/app.jar
```

- Spring recommends the **AOT cache** on Java 24+ and **CDS** on older JDKs. `spring.context.exit=onRefresh` makes the training run start the context and exit, without needing traffic; a training run with representative traffic additionally records method profiles for faster warm-up.
- Buildpacks can produce the cache during the image build (`BP_JVM_AOTCACHE_ENABLED=true` with Java 25+; `BP_JVM_CDS_ENABLED` for CDS).
- The cache must match the **exact JDK build and classpath**: regenerate it in CI for each image, or it's silently ignored.

---

## Spring AOT Processing

- At build time, Spring runs the application's configuration phase and **generates source code**: bean definitions as plain Java instead of reflection and parsing, plus **runtime hints** (reflection, resources, proxies, serialisation) for native images.
- Consequence: **conditions are evaluated at build time**. `@Profile` and `@ConditionalOnProperty` decisions are frozen into the generated code; changing profiles at runtime doesn't change which beans exist. Properties *values* can still change.
- Required for native images; optional on the JVM (`-Dspring.aot.enabled=true`) for a modest startup gain.

---

## GraalVM Native Images

```bash
./mvnw -Pnative native:compile           # native executable
./mvnw spring-boot:build-image -Pnative  # container image via buildpacks
```

- **Closed world**: everything reachable is compiled ahead of time; nothing is loaded at runtime. Reflection, dynamic proxies, resources and serialisation need **reachability metadata**. Spring AOT generates most of it; libraries ship metadata (or it comes from the GraalVM reachability metadata repository); custom needs use `RuntimeHintsRegistrar`, `@RegisterReflectionForBinding`, or the **tracing agent** in tests.
- Boot 4 requires **GraalVM 25+** for native builds.
- Trade-offs:
    - Startup in tens of milliseconds and a much smaller RSS: great density for many small services and scale-to-zero.
    - Lower peak throughput than C2 for long-running hot code unless using profile-guided optimisation (Oracle GraalVM).
    - Builds take minutes and lots of memory; test the native binary in CI, because "works on JVM" doesn't imply "works native".
    - Different GC options (Serial by default; G1 in Oracle GraalVM), and JVM tooling (JFR, heap dumps) is partially supported.
- Libraries relying on runtime bytecode generation or heavy reflection may not work, or need configuration.

## CRaC (Coordinated Restore at Checkpoint)

- Takes a **checkpoint** of a running, warmed JVM process (via CRIU on Linux) and restores it later in milliseconds with JIT code already compiled.
- Spring manages open resources around checkpoint/restore through the lifecycle (`Lifecycle` beans are stopped before the checkpoint and restarted after): connection pools, servers, schedulers.
- Automatic checkpoint at startup: `-Dspring.context.checkpoint=onRefresh`.
- Considerations: needs a CRaC-enabled JDK (Azul Zulu, BellSoft Liberica) and container privileges; the snapshot contains memory contents, including **secrets** loaded at checkpoint time; anything time- or host-dependent captured at checkpoint (random seeds, hostnames, tokens) must be refreshed on restore.

---

## Memory Footprint

- Main contributors: heap (live data, caches), metaspace (classes: dependencies and generated proxies), thread stacks (Tomcat pool, executors), direct buffers (Netty), code cache ([[03 - JVM Memory Layout]]).
- Levers: fewer dependencies (Boot 4 modular starters help), virtual threads instead of large platform pools, compact object headers (opt-in on 25, default on 27), bounded caches, `MaxRAMPercentage` tuned with NMT ([[13 - Java in Containers and Production Tuning]]).

---

## Runtime Performance: What Usually Matters

| Hot spot | Fix |
|---|---|
| **Database access** (N+1, missing indexes, OSIV, chatty transactions) | Fetch plans, DTO projections, batching, indexes ([[07 - Spring Data JPA and Hibernate]]) |
| **Pool sizing** (DB, HTTP clients, executors) | Size from measurements; watch pending metrics ([[08 - Spring Database Connections and Migrations]]) |
| **Downstream latency** | Timeouts, parallel calls, caching, bulkheads ([[11 - Spring HTTP Clients Resilience and Messaging]]) |
| **Serialisation** | Avoid serialising entities (lazy loading during JSON writing); smaller DTOs; reuse `ObjectMapper`/`JsonMapper` |
| **Logging** | Avoid string building at disabled levels; async appenders; no per-request debug logging in prod |
| **Caching** | Caffeine for local, Redis for shared; explicit TTLs and size limits |
| **Threading** | Virtual threads for blocking I/O-bound services ([[04 - Spring MVC Request Lifecycle]]) |
| **Response size** | Compression (`server.compression.enabled`), pagination, HTTP/2 |

- Profile under realistic load (async-profiler, JFR) before changing anything ([[12 - JVM Diagnostics and Profiling]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Stale AOT/CDS cache** | Ignored silently; no startup gain | Generate in the image build per release |
| **Lazy init in production** | Slow first requests; config errors at runtime | Eager init; measure lazy wins |
| **Native image missing hints** | Runtime failures only in the native binary | Native tests in CI; tracing agent; `RuntimeHints` |
| **Expecting runtime profile switches with AOT/native** | Beans don't change | Decide conditions at build time; one image per configuration if needed |
| **Native for a high-throughput long-running service** | Lower peak throughput, harder diagnostics | JVM + AOT cache instead |
| **CRaC snapshot with secrets** | Credentials in image artefacts | Load secrets after restore; protect snapshots |
| **Tuning JVM flags before fixing queries** | Little gain | Profile first; fix the data layer |

---

## Interview Lines

> [!tip] Say it like this
> - "For most containerised services I'd use the JDK's AOT cache with an extracted jar: much faster startup and warm-up while keeping C2's peak performance."
> - "Spring AOT generates bean definitions and runtime hints at build time, which is what makes native images possible, but it also fixes profile and conditional decisions at build time."
> - "Native images start in milliseconds with a fraction of the memory, at the cost of a closed world, long builds and lower peak throughput, so they suit serverless and scale-to-zero."
> - "CRaC restores a warmed JVM from a checkpoint, but the snapshot contains memory, so secrets must be loaded after restore."
> - "Before startup tricks I fix the usual runtime costs: N+1 queries, open-session-in-view, pool sizing and downstream timeouts."

## Related Notes

- [[02 - JIT Compilation and AOT]] · [[13 - Java in Containers and Production Tuning]] · [[12 - JVM Diagnostics and Profiling]] · [[03 - JVM Memory Layout]]
- [[00 - Spring Boot Version History]] · [[05 - Compilation and Startup Compared]] · [[Languages/README]]
