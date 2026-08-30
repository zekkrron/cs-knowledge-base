---
tags: [lld/patterns/behavioural, status/draft]
created: 2026-08-04
---
# Strategy Pattern

> [!abstract] When you have a finite family of interchangeable algorithms (strategies) that each get repeated, pull each into its own class behind a common interface and swap between them at runtime.

**External references:**
- [Refactoring.guru — Strategy](https://refactoring.guru/design-patterns/strategy)
- [Refactoring.guru — Strategy in Java](https://refactoring.guru/design-patterns/strategy/java/example)

## Part 1 — Core Framework

### Main Purpose

Define a family of algorithms, encapsulate each into its own class, and make them interchangeable so the algorithm can vary independently of the Context (the client code) that uses it. Promotes [[01 - SOLID Principles/02 - Open-Close Principle]] and [[01 - SOLID Principles/05 - Dependency Inversion Principle]].

### Recognition Signal

> [!tip] The cue
> Whenever there are algorithms / ways of writing code / strategies, and each of them is being used more than once — causing code duplication — and they exist in a **finite number**, that's when you start thinking of the Strategy pattern. The other tell: core business logic polluted with a big if/else or switch that just picks variations of the same task.

### How to Implement

1. Make an **interface** for what the algorithms are about.
2. Make different **implementations** of the interface, each being an independent strategy.
3. Set up the design using **constructor injection**, then use it where you need to.

Constructor injection is not mandatory — it's just one way.

### Key Code

Shared setup — the interface and a couple of concrete strategies:

```java
interface PricingStrategy {
    double calculateFinalPrice(double orderTotal);
}
class RegularPricing implements PricingStrategy {
    public double calculateFinalPrice(double total) { return total; }
}
class PremiumMemberPricing implements PricingStrategy {
    public double calculateFinalPrice(double total) { return total * 0.90; }
}
```

**Case A — Plain Java (no singletons).** No shared instances, so the orchestrator can just **construct a fresh context per operation** and inject the chosen strategy through the constructor. The `final` field makes each context immutable and therefore thread-safe.

```java
class CheckoutService {
    private final PricingStrategy pricingStrategy;

    CheckoutService(PricingStrategy pricingStrategy) {   // constructor injection
        this.pricingStrategy = pricingStrategy;
    }
    void processOrder(double amount) {
        double finalAmount = pricingStrategy.calculateFinalPrice(amount);
        System.out.println("Processing: $" + finalAmount);
    }
}

// Orchestrator picks the strategy and injects it, per operation
class Orchestrator {
    void checkout(double amount, String tier) {
        PricingStrategy strategy = switch (tier) {
            case "PREMIUM" -> new PremiumMemberPricing();
            default        -> new RegularPricing();
        };
        new CheckoutService(strategy).processOrder(amount);
    }
}
```

**Case B — Spring Boot (singleton beans).** The service is a **singleton shared across all requests**. You must NOT store the strategy in a mutable field via a setter — that's shared mutable state (see Advanced Nuances). Two safe options, both keeping the service **stateless**:

*Option 1 — Method injection.* No strategy field at all; the boundary (controller) passes the strategy straight into the method. It lives on the thread's stack, so it's unique per request.

```java
@Service
class CheckoutService {
    // stateless — no strategy field
    public void processOrder(double amount, PricingStrategy strategy) {
        double finalAmount = strategy.calculateFinalPrice(amount);
    }
}
```

*Option 2 — Own the (injected) factory, resolve inside the method.* The factory is itself a singleton bean, injected into the service. The service resolves the strategy into a **local variable** inside the method — unique per call, thread-safe. ("Own the factory" = hold an *injected* reference to it, never `new` it.)

```java
@Service
class CheckoutService {
    private final PricingStrategyFactory factory;   // injected singleton bean

    CheckoutService(PricingStrategyFactory factory) {
        this.factory = factory;
    }
    public void processOrder(double amount, String tier) {
        PricingStrategy strategy = factory.getStrategy(tier); // local → thread-safe
        double finalAmount = strategy.calculateFinalPrice(amount);
    }
}
```

### Beyond the Basics

Since the strategy object is **upcasted**, we call the general (interface) function and runtime polymorphism runs the correct strategy — so we can **switch strategy whenever we want**. This is compile-time-safe dynamic dispatch, not runtime type-switching: the caller never writes `if (type == X)` or a `switch` on the strategy type. Strategy is precisely how you **eliminate that if-else/switch ladder** — the language itself does the dispatch (the same "magic switch" idea from [[01 - SOLID Principles/02 - Open-Close Principle]]), so adding a new strategy never means editing a branching block. The Context stays completely blind to which algorithm runs.

### Anti-Patterns & When NOT to Use

- **Fat interface.** A `Strategy` interface with many methods where a concrete strategy leaves most blank or throws `UnsupportedOperationException`. A strategy should be a single cohesive algorithm. Same problem attacked by [[01 - SOLID Principles/04 - Interface Segregation Principle]] — keep strategy interfaces narrow.
- **Static strategy (overkill).** If an algorithm never actually changes at runtime — you hardcode `new RegularPricing()` and never swap it — the pattern adds an interface, a class, and indirection for zero flexibility gained. Just use a plain method.

## Part 2 — Architecture Deep Dive

### Pattern Synergy

- **Strategy + Factory.** "Conservation of complexity": Strategy doesn't *delete* the if/else — it *relocates* it. The branching that picks the algorithm still has to live somewhere; it moves out of the core business logic into a Factory. The Factory reads the incoming payload, runs the switch, creates the correct strategy, and hands it to the Context. They're best friends in system design.
- **Where the strategy comes from** (two mechanisms):
  - **Factory** — *creates* the strategy, then it's injected into the service.
  - **IoC container / strategy registry** — a mapping of string/type → strategy object; *looks up* an already-existing strategy at runtime.

### Confused With

Strategy and State look almost identical in UML — both use composition to delegate behaviour to an inner object. The difference is **intent** and **who drives the switch**.

| Aspect | Strategy | State |
|---|---|---|
| Intent | Swap between interchangeable algorithms that do the *same* job differently | Let an object change its behaviour as its internal state changes |
| Who picks the behaviour | The client / boundary chooses and injects it | The object transitions *itself* between states |
| Lifetime | Usually chosen once and stays fixed for the operation's lifecycle | Changes dynamically during the object's life (e.g., Draft → Published) |
| Do the variants know each other? | No — strategies are independent and unaware of siblings | Often yes — one state knows and triggers the next transition |

### Real-World Context

- **Java sorting** — `Collections.sort(list, comparator)`. The `Comparator` *is* the injected strategy; it dictates the ordering algorithm while `sort` stays generic.
- **Passport.js (Node)** — built entirely on Strategy: `passport.use(new LocalStrategy(...))` vs `passport.use(new GoogleStrategy(...))`. The framework orchestrates the session; each strategy handles one auth mechanism.
- **Spring Security** — `PasswordEncoder` implementations (BCrypt, Argon2, ...) are strategies swapped behind a common interface.

### Advanced Nuances

- **Push decisions to the boundary.** The API controller or message listener is the boundary. It reads the incoming payload, decides which strategy applies, and hands a pre-configured strategy to the core. The core domain stays completely agnostic of the outside world's complexity. Decide at the edge, execute in the centre.
- **Thread-safety in singletons.** Storing the strategy in a field via `setPricingStrategy(...)` on a singleton bean creates **shared mutable state** — a classic race:
  1. Request A sets the strategy to `HolidaySaleStrategy`.
  2. Before A calls `processOrder`, Request B arrives and sets it to `PremiumMemberPricing`.
  3. Since the bean is one shared instance, B has overwritten the field A was about to use.
  4. A now runs `processOrder` with B's strategy. Wrong, non-deterministic result.

  Fix: keep the service **stateless** — pass the strategy as a method parameter, or resolve it into a **local variable** inside the method. Local variables live on the thread's stack, so each request gets its own.

## Pattern-Specific Applications

### Chess Cross-Combination

Strategy solves the cross-combination problem in chess (see [[01 - SOLID Principles/04 - Interface Segregation Principle]]).

Each core movement type becomes an interface — L, straight, diagonal, etc. become the movement types. These get coded once and reused. Since the actual movement of a piece is a **combination** of these, we mix strategies and use them, instead of rewriting the movement code every time someone needs, say, a knight-style move. This solves the cross-combination problem completely.

## Field Notes

> [!note] The Context is deliberately blind
> The whole value is that the Context (e.g., `CheckoutService`) has no idea which concrete algorithm is running. You can add a `BlackFridayStrategy` next year and never touch the Context. If you find yourself editing the Context to add a strategy, the pattern has been broken somewhere.

## Principles Served

- [[01 - SOLID Principles/02 - Open-Close Principle]] — add a new strategy by adding a class, not editing existing code.
- [[01 - SOLID Principles/05 - Dependency Inversion Principle]] — the caller depends on the strategy interface, not concrete algorithms.

## Sources

- [[01 - SOLID Principles/05 - Dependency Inversion Principle]] (motivating example — refund algorithm duplication)
- [[01 - SOLID Principles/04 - Interface Segregation Principle]] (chess cross-combination problem)
