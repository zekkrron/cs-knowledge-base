---
tags: [lld/patterns/creational, status/draft]
created: 2026-08-04
---
# Singleton Pattern

> [!abstract] Ensure a class has exactly one instance globally and provide a single centralized access point to it — for shared, expensive resources or system-wide coordination where multiple instances would cause corruption, races, or resource exhaustion.

**External references:**
- [Refactoring.guru — Singleton](https://refactoring.guru/design-patterns/singleton)
- [Refactoring.guru — Singleton in Java](https://refactoring.guru/design-patterns/singleton/java/example)
- [DigitalOcean — Java Singleton best practices & examples](https://www.digitalocean.com/community/tutorials/java-singleton-design-pattern-best-practices-examples)

## Part 1 — Core Framework

### Main Purpose

Guarantee one instance of a class and a single global access point. Used to manage a shared expensive resource or coordinate system-wide state where multiple instances would cause data corruption, race conditions, or resource exhaustion.

### Recognition Signal

> [!tip] The cue
> - You need **exactly one** instance to coordinate system-wide actions (central cache manager, task scheduler).
> - You manage a **shared expensive resource** (DB connection pool, file system, hardware interface) with limited concurrent access.
> - Threading the shared object through constructors into every deeply-nested class creates massive boilerplate.

### How to Implement

1. **Private constructor** — blocks `new` from outside.
2. **Static field** holding the single instance.
3. **Static accessor** (`getInstance()`) that creates-on-first-use and returns it.
4. Make it **thread-safe** (see below) — the naive version is broken under concurrency.

### Key Code

Classic thread-safe version with **double-checked locking**:

```java
public class ConnectionPoolManager {
    // volatile is critical — prevents a half-constructed instance being seen by other threads
    private static volatile ConnectionPoolManager instance;
    private List<String> connectionPool;

    private ConnectionPoolManager() {          // private ctor blocks external 'new'
        System.out.println("Initializing the expensive connection pool...");
        this.connectionPool = new ArrayList<>();
    }

    public static ConnectionPoolManager getInstance() {
        if (instance == null) {                // 1st check — fast, no locking
            synchronized (ConnectionPoolManager.class) {
                if (instance == null) {        // 2nd check — safe, inside the lock
                    instance = new ConnectionPoolManager();
                }
            }
        }
        return instance;
    }

    public String borrowConnection() { return "Database Connection Active"; }
}
```

#### Enum Singleton (the safest hand-written form)

Joshua Bloch (*Effective Java*) recommends the **enum singleton** as the most robust manual Singleton. An enum constant is instantiated exactly once by the JVM:

```java
public enum ConnectionPoolManager {
    INSTANCE;                                  // the one and only instance

    private final List<String> connectionPool;
    ConnectionPoolManager() {                  // runs once, when the enum is loaded
        this.connectionPool = new ArrayList<>();
        // fill the pool...
    }
    public String borrowConnection() { return "Database Connection Active"; }
}

// usage
ConnectionPoolManager.INSTANCE.borrowConnection();
```

Why it's ironclad:
- **Thread-safe for free** — the JVM guarantees enum constants are created once, at class-load, with no locking code needed.
- **Serialization-safe** — deserializing an enum always returns the *same* constant (a normal class can be deserialized into a second instance unless you add `readResolve`).
- **Reflection-safe** — you cannot reflectively invoke an enum constructor to force a second instance (reflection explicitly forbids it).

The trade-off: an enum can't extend a class, and it's eager (created at load). But for a plain guaranteed-single instance, it's the strongest form.

### Eager vs Lazy Initialization

**When** the single instance is created splits the implementations into two camps.

**Eager** — created when the class is *loaded*, whether or not it's ever used. Simple and thread-safe for free (the classloader guarantees once-only), but it builds the object even if unused and can't easily take runtime params or recover from a failed construction. The `static final` field and the enum form are both eager:

```java
public class Config {
    private static final Config INSTANCE = new Config();   // built at class load
    private Config() { }
    public static Config getInstance() { return INSTANCE; }
}
```

**Lazy** — created only on the *first* `getInstance()` call. Good when construction is expensive and might not be needed. Two thread-safe ways:

1. **Double-checked locking** — the `ConnectionPoolManager` in Key Code above. Works, but needs `volatile` + the double check (ceremony).
2. **Initialization-on-demand holder idiom** — lazy *and* lock-free. The inner holder class isn't loaded until `getInstance()` first references it, and the classloader makes that load thread-safe with no `synchronized`/`volatile`:

```java
public class ConnectionPoolManager {
    private ConnectionPoolManager() { /* expensive init */ }

    private static class Holder {                    // not loaded until first referenced
        static final ConnectionPoolManager INSTANCE = new ConnectionPoolManager();
    }
    public static ConnectionPoolManager getInstance() {
        return Holder.INSTANCE;                       // triggers Holder load lazily, once
    }
}
```

| Form | Timing | Thread-safe? | Notes |
|---|---|---|---|
| `static final` field / enum | **Eager** (class load) | Yes (classloader) | Simplest; builds even if unused |
| Double-checked locking | **Lazy** (first call) | Yes (with `volatile`) | Defers cost; more ceremony |
| Holder idiom | **Lazy** (first call) | Yes (classloader) | Lazy *and* lock-free — cleanest lazy form |

Rule of thumb: cheap to build → eager (`static final`/enum) for simplicity; expensive/optional → lazy via the holder idiom (prefer it over hand-written double-checked locking).

### Beyond the Basics

**Thread safety is the silent killer.** A naive `if (instance == null) instance = new ...` (no `synchronized`) fails in a multithreaded backend: if two requests hit it at the same millisecond, you spawn two instances. Double-checked locking (with `volatile`) or the enum form are mandatory for production.

### Anti-Patterns & When NOT to Use

- **The global-variable disguise.** Using a Singleton just to avoid passing a config object through constructors. It creates **hidden dependencies** scattered across the codebase — code depends on it without declaring it.
- **State mutation (the testing nightmare).** If the Singleton holds *mutable* state (`currentUser`, `activeTransactions`), unit tests randomly fail: the instance persists across tests, so Test A's mutation leaks into Test B. (This is the historical flaw that DI Singletons fix — see below.)

## Part 2 — Architecture Deep Dive

### Pattern Synergy

- **Singleton + [[Facade]].** A Facade is often a Singleton — you only need one object representing the complex subsystem to the rest of the app.
- **Singleton + [[Factory Pattern|Factory]] / [[Abstract Factory]].** Concrete factories are frequently Singletons — you need only one `AwsResourceFactory` in memory to stamp out instances.
- **Singleton + [[Strategy]] / State (only if stateless).** If a Strategy or State object holds **no internal mutable state** — it's a pure algorithm depending only on its arguments — one shared instance can serve everyone, so make it a Singleton to save memory:

```java
// Stateless strategy — no instance fields; output depends only on the argument.
// Enforced as a real singleton via a PRIVATE constructor (not just a shared field).
public class RegularPricing implements PricingStrategy {
    public static final RegularPricing INSTANCE = new RegularPricing();
    private RegularPricing() { }                                       // blocks other 'new'
    public double calculateFinalPrice(double total) { return total; }  // pure, no state
}
// Use it polymorphically — hold it through the STRATEGY INTERFACE, not the concrete type:
PricingStrategy strategy = RegularPricing.INSTANCE;   // upcast — Strategy polymorphism intact
double price = strategy.calculateFinalPrice(100);     // caller doesn't know it's RegularPricing

// Realistically a factory/registry returns the singleton already upcast:
//   PricingStrategy s = pricingFactory.get(tier);   // internally: RegularPricing.INSTANCE
```

> [!note] Singleton and Strategy are orthogonal concerns
> Singleton governs **how many instances / how you obtain one**; Strategy governs **how you use it** (through the interface). Making a strategy a singleton does NOT remove its polymorphism — that comes from referencing it as `PricingStrategy`, not from how it's created. You'd only lose Strategy if you hardcoded `RegularPricing.INSTANCE.calc(...)` on the concrete type everywhere — and that's concrete coupling, not the singleton's fault.

> [!warning] A `static final` field alone is NOT a singleton
> `public static final X REGULAR = new X();` only gives a **shared reference** — the class can still be instantiated again elsewhere with `new X()`. A true Singleton must *enforce* one instance with a **private constructor** (as above) or an `enum`. Being stateless is what makes it *safe* to share as a singleton; the private constructor is what actually *makes* it one.

> [!warning] Only share stateless strategies
> If the strategy held per-call mutable state, sharing one instance across threads would cause the exact race described in [[Strategy]] (Advanced Nuances). Stateless → shareable singleton; stateful → a fresh instance per use.

### Confused With — Enum Singleton vs DI Singleton

Two very different answers to "give me one instance," for two different contexts.

- **Enum Singleton** — the *safest hand-written* Singleton (serialization/reflection-proof). Reach for it when an interviewer asks for the safest pure-Java Singleton.
- **DI Singleton** — what you actually use in real scalable architecture. A DI container creates and manages a single instance and **injects** it, giving you the single-instance benefit *without* global static access — and, crucially, the injected dependency is **mockable in tests**, solving the Singleton's biggest historical flaw.

```java
// Spring: default bean scope is singleton — the container makes ONE and injects it
@Service
public class ConnectionPoolManager {
    public ConnectionPoolManager() { /* init pool once at startup */ }
    public String borrowConnection() { return "Database Connection Active"; }
}

@Service
public class AppController {
    private final ConnectionPoolManager pool;   // injected singleton (constructor injection)
    public AppController(ConnectionPoolManager pool) { this.pool = pool; }
    // in a unit test you just pass a mock ConnectionPoolManager here — no global state
}
```

> [!tip] Interview line
> "Safest hand-written = enum singleton (Bloch). But in production I'd use a DI/framework singleton — same single-instance guarantee, no hidden global access, and it's mockable in tests, which fixes the classic Singleton testability problem."

### Real-World Context

- **Spring beans** — any `@Service` / `@Component` / `@Repository` is a singleton by default; the IoC container creates one on startup and injects it everywhere.
- **Logging frameworks** — Log4j / SLF4J (`LoggerFactory.getLogger()`) ensure a single log manager owns the file I/O streams.

## Field Notes

> [!note] Singleton can quietly violate DIP
> Global `getInstance()` access means classes depend on a concrete Singleton they never declared — the opposite of [[01 - SOLID Principles/05 - Dependency Inversion Principle]] (depend on injected abstractions). This is *why* the DI Singleton is preferred: it keeps the single-instance benefit while restoring explicit, mockable dependencies.

## Principles Served

- Mostly a **cautionary** pattern re: SOLID — the manual/global form tends to *violate* DIP and hurt testability. The DI-managed form keeps the single-instance benefit while respecting [[01 - SOLID Principles/05 - Dependency Inversion Principle]].

## Sources

- [Refactoring.guru — Singleton](https://refactoring.guru/design-patterns/singleton) (external, primary reference)
