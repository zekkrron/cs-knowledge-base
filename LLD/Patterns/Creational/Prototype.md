---
tags: [lld/patterns/creational, status/draft]
created: 2026-08-04
---
# Prototype Pattern

> [!abstract] Create new objects by copying an existing one (the prototype) instead of building from scratch — the object clones itself. The killer reason: skip the expensive setup the original already paid for.

**External references:**
- [Refactoring.guru — Prototype](https://refactoring.guru/design-patterns/prototype)
- [Refactoring.guru — Prototype in Java](https://refactoring.guru/design-patterns/prototype/java/example)

> [!info] What Refactoring Guru under-emphasises
> RG explains the mechanics well, so this note only carries what it *doesn't* stress:
> 1. **The real motivation — expensive first-object creation.** The whole point is that building the original was costly (DB aggregation, network calls, heavy computation). Once you have it in memory, cloning skips all of that. RG barely emphasises this.
> 2. **Deep-copy hazards** — mentioned by RG but not hammered. This is where implementations actually fail (see Anti-Patterns).
> 3. **Enforcing the copy contract** — RG uses an abstract class with an abstract `clone()`. That's fine, but the general principle is: *every class in the hierarchy must be forced to provide a copy function*, whether via an interface or an abstract method on a base class that's never used directly.

## Part 1 — Core Framework

### Main Purpose

Create new objects by copying an existing object (the prototype) rather than instantiating from scratch with `new`. The cloning is delegated to the objects being cloned. The decisive use case: when creating the first object is **expensive** (DB calls, network requests, heavy math), a clone reuses that already-built state and avoids re-paying the cost.

### Recognition Signal

> [!tip] The cue
> - Object instantiation is **expensive** — multiple DB calls, network requests, or complex computation.
> - You need dozens of objects that are 99% identical and differ in only a field or two.
> - You want to hide concrete classes from the creator, or avoid a parallel hierarchy of Factory classes just to instantiate things.

### How to Implement

Enforce a copy contract across all classes in the hierarchy. Two ways:
1. **Interface** — e.g. `Copyable<T>` with a `copy()` method that every concrete class implements.
2. **Abstract base method** — if there are subclasses that are actually used (and the base is not), put an abstract `copy()` on the base class so subclasses are forced to implement it.

Then implement `copy()` using a **copy constructor** (the modern, safe way), taking care to **deep-copy** every mutable nested object.

### Key Code

```java
// 1. The Prototype interface (avoid Java's built-in Cloneable — it is flawed)
public interface Copyable<T> {
    T copy();
}

// 2. The Concrete Prototype
public class FinancialReport implements Copyable<FinancialReport> {
    private String title;
    private String exportFormat;
    private List<String> heavyData; // expensive to fetch

    public FinancialReport(String title, String format) {
        this.title = title;
        this.exportFormat = format;
        this.heavyData = fetchHeavyDataFromDB(); // 5-second operation
    }

    // A copy constructor is the modern, safe way to implement Prototype
    private FinancialReport(FinancialReport target) {
        this.title = target.title;
        this.exportFormat = target.exportFormat;
        // DEEP COPY: new list so clones don't share memory references
        this.heavyData = new ArrayList<>(target.heavyData);
    }

    private List<String> fetchHeavyDataFromDB() {
        System.out.println("Executing 5-second DB query...");
        return Arrays.asList("Row1", "Row2", "Row3");
    }

    @Override
    public FinancialReport copy() {
        return new FinancialReport(this); // calls the copy constructor
    }

    public void setTitle(String title) { this.title = title; }
}

// 3. The Client
FinancialReport baseReport = new FinancialReport("Q1 Earnings", "PDF"); // 5 seconds
FinancialReport clone = baseReport.copy();  // INSTANT — DB bypassed entirely
clone.setTitle("Q1 Earnings - Draft Version");
```

### Beyond the Basics

In Java, the built-in `Cloneable` interface and `Object.clone()` are widely considered **broken** (Joshua Bloch, of the Java Collections Framework, advises against them). A custom `copy()` method or a **copy constructor** is the gold standard.

**Why `Cloneable` is bad — in plain terms:**
- **It's an interface with no method.** `Cloneable` doesn't declare `clone()`. The actual `clone()` lives on `Object` and is `protected`. So the interface is just a *marker* — implementing it doesn't even give you a usable `clone()`; you still have to override the one on `Object`. That's backwards and confusing.
- **`Object.clone()` doesn't call your constructor.** It creates the new object through hidden native magic (bit-by-bit field copy), bypassing your constructor entirely. So any invariants or setup your constructor guarantees are silently skipped.
- **It does a shallow copy by default.** The native copy just copies field values — for object references, it copies the *reference*, not the object (see the deep-copy issue below). You end up hand-writing deep copies anyway, so the built-in bought you nothing.
- **It forces `CloneNotSupportedException`.** A checked exception you must catch even when you know cloning is fine — noise for no benefit.
- **Fragile with `final` fields.** The clone-then-fix-up approach fights `final` fields, since you can't reassign them after the native copy.

A **copy constructor** (`new FinancialReport(existing)`) avoids all of this: it runs your real constructor, you control exactly what's shallow vs deep, no checked exception, and `final` fields work normally.

### Anti-Patterns & When NOT to Use

- **The Shallow Trap.** The #1 reason Prototype fails in production. See the deep-copy explanation below.

> [!danger] Shallow copy vs deep copy — what it actually is
> An object's fields come in two kinds: **primitives** (int, boolean, ...) and **references** (pointers to other objects, like a `List`).
>
> - A **shallow copy** duplicates the field *values*. For a primitive, that copies the actual number — fine, independent. For a reference, it copies the *pointer*, not the object it points to. So `clone.list` and `original.list` now point to the **exact same list in RAM**.
> - Result: `clone.list.add("x")` also changes `original.list`, because they're literally the same list. The clone silently corrupts the original — a bug that's brutal to trace because nothing looks wrong at the copy site.
>
> A **deep copy** goes one level deeper: for every mutable referenced object, it creates a **brand-new copy** so the clone owns its own independent list (`new ArrayList<>(target.list)`). Now the two objects share nothing mutable and can't affect each other.
>
> Rule: primitives and immutables (like `String`) are safe to shallow-copy; every **mutable** nested object (lists, maps, custom mutable classes) must be deep-copied. If a deep object itself contains mutable objects, you have to deep-copy those too — all the way down.

## Part 2 — Architecture Deep Dive

### Pattern Synergy

- **Prototype + Registry.** Prototypes are often stored in a central registry (a `HashMap` of pre-initialised objects). The app requests an object by ID and the registry returns a fresh **clone**.
- **Prototype vs Factory Method.** Factory creates objects from scratch based on rules; Prototype creates them by copying existing state. A Factory sometimes *uses* a Prototype internally to stamp out new instances quickly. For the full "where the cost goes" trade-off (inheritance boilerplate vs prototype setup) with a code example, see [[Factory Pattern]] → *Factory Method vs Prototype*.

### Confused With

| Aspect | Prototype (GoF) | Spring `@Scope("prototype")` |
|---|---|---|
| What it does | Takes an existing in-memory object and clones its **state** | Tells the IoC container: don't reuse the singleton — `new` a fresh, **blank** instance every time the bean is requested |
| Source of the new object | Copy of an existing object | Built from scratch |

Knowing this semantic clash (same word, opposite mechanics) is a common LLD interview differentiator.

### Real-World Context

- **JSON serialization for deep copy (Jackson / GSON).** To guarantee a flawless 100% deep copy of a massive nested object tree, engineers serialise the object to a JSON string and parse it straight back into a new object. Slight CPU cost, but completely immune to the Shallow Trap.

### Advanced Nuances

- **The distributed stateless reality.** The pure GoF Prototype implicitly assumes a **stateful** environment (a monolith) where the base object stays alive in local RAM. In microservices, requests are stateless and load-balanced: if Server A built the base report, Server C won't have it in RAM when the user clicks "Duplicate."
- **The modern solution — serialization + distributed cache (Redis).**
  1. **Cache:** Server A generates the heavy report, serialises it to JSON, stores it in Redis with a TTL.
  2. **Clone:** the duplicate request hits Server C, which fetches the JSON from Redis and deserialises it into a fresh object in its own RAM.

  Deserialising a JSON string into a new object is the **distributed equivalent of `copy()`** — it deep-copies state across the cluster without rerunning heavy queries.

## Pattern-Specific Applications

### Prototype + Registry — cached tool contexts

A realistic use: a `ToolExecutionContext` whose base config (`baseHeaders`, `allowedEndpoints`) is **expensive** to build — fetched from DB/Redis. A **registry** loads each prototype once, then every incoming request gets a fresh **clone** (via `copy()`) as a clean baseline — no re-fetching. Runtime-only fields (`currentTraceId`, `runtimePayload`) are deliberately *not* copied, so each clone starts blank and ready.

Note the shape: an abstract base with a **copy constructor** that deep-copies the mutable fields, and each subclass chaining `super(target)` up to it — this is the "enforce copy via abstract base method" approach from *How to Implement*.

```java
// 1. Custom interface
public interface Copyable<T> { T copy(); }

// 2. Prototype base class
abstract class ToolExecutionContext implements Copyable<ToolExecutionContext> {
    public String toolId;
    public Map<String, String> baseHeaders;   // heavy: fetched from DB/Redis
    public List<String> allowedEndpoints;      // heavy: fetched from DB/Redis
    // runtime state
    public String currentTraceId;
    public String runtimePayload;

    public ToolExecutionContext(String toolId) { this.toolId = toolId; }

    // Copy constructor: explicit deep copy of mutable state
    protected ToolExecutionContext(ToolExecutionContext target) {
        if (target != null) {
            this.toolId = target.toolId;
            this.baseHeaders = new ConcurrentHashMap<>(target.baseHeaders); // deep
            this.allowedEndpoints = new ArrayList<>(target.allowedEndpoints); // deep
            // intentionally NOT copying runtimePayload / currentTraceId —
            // the clone is a clean baseline ready for a new request
        }
    }

    public abstract void execute();
}

// 3. Concrete prototype
class RestApiToolContext extends ToolExecutionContext {
    public RestApiToolContext(String toolId) { super(toolId); }

    private RestApiToolContext(RestApiToolContext target) {
        super(target); // hand the target up to the base copy constructor
    }

    @Override public ToolExecutionContext copy() {
        return new RestApiToolContext(this);
    }
    @Override public void execute() {
        System.out.println("Executing API Tool: " + toolId + " Trace: " + currentTraceId);
    }
}

// 4. The Prototype Registry
class ToolRegistry {
    private Map<String, ToolExecutionContext> cache = new ConcurrentHashMap<>();

    public void loadCacheFromRedis() { // simulated one-time expensive load
        RestApiToolContext weatherTool = new RestApiToolContext("weather-api-v1");
        weatherTool.baseHeaders = Map.of("Authorization", "Bearer ...", "Content-Type", "application/json");
        weatherTool.allowedEndpoints = List.of("api.weather.com/v1/*");
        cache.put("weather-api", weatherTool);
    }

    public ToolExecutionContext getToolContext(String toolKey) {
        ToolExecutionContext cached = cache.get(toolKey);
        return cached != null ? cached.copy() : null; // return a fresh clone
    }
}

// 5. Driver (e.g. an event consumer)
public class GatewayRouter {
    public static void main(String[] args) {
        ToolRegistry registry = new ToolRegistry();
        registry.loadCacheFromRedis();

        ToolExecutionContext execution = registry.getToolContext("weather-api");
        execution.currentTraceId = "trace-uuid-7-abc"; // mutate the clean clone safely
        execution.runtimePayload = "{ \"location\": \"Delhi\" }";
        execution.execute();
    }
}
```

Why the deep copy matters here: if `baseHeaders` were shallow-copied, two concurrent requests cloning the same prototype would share one headers map — one request mutating it would corrupt the other. The `new ConcurrentHashMap<>(...)` / `new ArrayList<>(...)` gives each clone its own.

## Field Notes

> [!note] How the registry map gets populated
> The registry is a **local in-memory cache of expensive-to-build prototypes**, usually populated at startup from a shared source (DB/Redis/config). Three ways to fill it:
> - **Eager (startup warm-load)** — load everything at boot (like `loadCacheFromRedis()` above). Requests are always fast, but startup is slower and you load prototypes you may never use.
> - **Lazy (load on first miss)** — start empty; on a cache miss, fetch + build the prototype, store it, then serve clones. Faster startup, but the first request per type pays the cost.
> - **Refresh / TTL** — if the base config can change in the source, add invalidation or a TTL so the registry doesn't serve stale prototypes forever.
>
> Distributed caveat: each server instance builds its **own** in-process registry (eager or lazy), sourced from a *shared* store. The shared store is the source of truth; the per-instance registry is just the local hot copy.

> [!note] Redis as a "shared memory tier," not shared RAM
> Redis here acts like a fast in-memory store the whole cluster shares — a good intuition. But it's network-attached and requires serialization (you can't hold a pointer into it), access is micro-to-millisecond not nanosecond, and it offers no automatic cache coherence. It's a shared *store*, not shared address space. The serialize/deserialize step in the distributed clone flow above *is* the tax of that distinction.

## Principles Served

- [[01 - SOLID Principles/05 - Dependency Inversion Principle]] — clients copy through the `Copyable` abstraction, not concrete constructors.

## Sources

- [Refactoring.guru — Prototype](https://refactoring.guru/design-patterns/prototype) (external, primary reference)
