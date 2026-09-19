---
tags: [lld/patterns/structural, status/draft]
created: 2026-08-04
---
# Adapter Pattern

> [!abstract] A translator that lets two incompatible interfaces work together — it wraps an existing class and exposes the interface your code expects. Purely about translation, no logic.

**External references:**
- [Refactoring.guru — Adapter](https://refactoring.guru/design-patterns/adapter)
- [Refactoring.guru — Adapter in Java](https://refactoring.guru/design-patterns/adapter/java/example)

## Part 1 — Core Framework

### Main Purpose

Bridge two incompatible interfaces. The adapter wraps an existing class (the *adaptee*) and exposes the interface your application expects (the *target*), translating calls between them.

### Recognition Signal

> [!tip] The cue
> - Your code expects a specific interface (your standard DB / payment / cache interface).
> - You must integrate a third-party library, vendor API, or legacy subsystem that does the job but with different method names / argument orders / data formats.
> - You can't (or shouldn't) modify that third-party source to match your interface.

### How to Implement

1. Identify the **target** interface your code expects and the **adaptee** you're wrapping.
2. Write an **adapter** class that `implements` the target and **holds** an adaptee instance (composition).
3. In each target method, translate the call into the adaptee's API (and translate results/exceptions back).

### Key Code

```java
// 1. Target — what your application expects
public interface PaymentProcessor {
    void processPayment(double amount, String currency);
}

// 2. Adaptee — incompatible 3rd-party code (different names + argument order)
public class StripeApi {
    public void executeCharge(String currencyCode, double totalAmount) {
        System.out.println("Stripe charged " + totalAmount + " " + currencyCode);
    }
}

// 3. Adapter — the translator
public class StripeAdapter implements PaymentProcessor {
    private final StripeApi stripeApi;
    public StripeAdapter(StripeApi stripeApi) { this.stripeApi = stripeApi; }

    @Override
    public void processPayment(double amount, String currency) {
        stripeApi.executeCharge(currency, amount);   // translate the call
    }
}

// 4. Client — stays clean, uses only the target interface
PaymentProcessor processor = new StripeAdapter(new StripeApi());
processor.processPayment(100.00, "USD");
```

### Beyond the Basics

**The golden rule: an adapter should only translate.** Take an object in, reshape it, call the external API, reshape the response, return it. Keep it as "dumb" as possible about domain logic.

### Anti-Patterns & When NOT to Use

- **Business-logic bleed.** If the adapter starts calculating tax, applying discounts, or handling retries, it's overstepped — those belong in core domain services, not the translation layer.
- **Leaky abstractions.** If the adapter lets the client catch *vendor-specific* exceptions, it's leaking. It must translate vendor exceptions into standard internal ones:

```java
// LEAKY — the vendor exception escapes, coupling every client to Stripe
public void processPayment(double amount, String currency) {
    stripeApi.executeCharge(currency, amount);   // may throw StripeRateLimitException
}
// caller is now forced to know about Stripe:
try { processor.processPayment(100, "USD"); }
catch (StripeRateLimitException e) { /* leak! client depends on the vendor */ }

// SEALED — adapter catches vendor exceptions, rethrows an internal domain exception
public void processPayment(double amount, String currency) {
    try {
        stripeApi.executeCharge(currency, amount);
    } catch (StripeRateLimitException | StripeApiException e) {
        throw new PaymentFailedException("payment failed", e);   // internal type only
    }
}
```

The point: if the vendor exception type escapes the adapter, every client must import and handle vendor types — coupling the whole app to Stripe and defeating the adapter. Exceptions must be translated just like method calls.

## Part 2 — Architecture Deep Dive

### Pattern Synergy — relations with other patterns

The clearest way to hold these is by **what each does to the interface**:

| Pattern | Effect on the interface |
|---|---|
| **Adapter** | **Different** interface (translate one shape into another) |
| **Proxy** | **Same** interface (control access — no change) |
| **Decorator** | **Same** interface, **enhanced** (adds behavior) |
| **Facade** | **New, simpler** interface over a whole subsystem |

#### Adapter vs Decorator

Adapter *changes* the interface; Decorator *keeps the same* interface and adds behavior.

```java
// Decorator — SAME interface (PaymentProcessor -> PaymentProcessor), adds logging
class LoggingPayment implements PaymentProcessor {
    private final PaymentProcessor inner;                 // wraps the SAME type it exposes
    LoggingPayment(PaymentProcessor inner) { this.inner = inner; }
    public void processPayment(double amt, String cur) {
        System.out.println("charging " + amt + " " + cur);   // added behavior
        inner.processPayment(amt, cur);                       // same interface passed through
    }
}
```

Tell them apart by the wrapped vs exposed types: Adapter wraps `StripeApi` but exposes `PaymentProcessor` (**different**); Decorator wraps `PaymentProcessor` and exposes `PaymentProcessor` (**same**).

RG adds a second, sharper distinction: *"Decorator supports recursive composition, which isn't possible when you use Adapter."* Because a decorator both **takes and returns the same interface**, you can nest decorators arbitrarily — each layer wraps another object of the same type:

```java
// Recursive composition — decorators stacked, each is still a PaymentProcessor
PaymentProcessor p =
    new LoggingPayment(                 // outermost: logs
        new RetryPayment(               // then: retries
            new StripeAdapter(new StripeApi())));  // core (an Adapter, translating once)
p.processPayment(100, "USD");           // logging -> retry -> stripe
```

An Adapter *can't* be stacked like this: its job is a **one-time translation** from interface A to interface B. Once translated there's no "adapter of an adapter of an adapter" accumulating behavior — the output type is different from the input type, so there's nothing to keep recursively wrapping. Decorators chain because input type == output type; adapters don't because input type != output type. (Note how the two coexist above: the Adapter sits at the core doing translation, and Decorators layer behavior on top.)

#### Adapter vs Proxy

Proxy keeps the **same** interface but controls *access* (lazy loading, auth, caching, rate-limiting) — it doesn't translate or add features, it gates.

```java
// Proxy — SAME interface, gates access
class AuthPaymentProxy implements PaymentProcessor {
    private final PaymentProcessor real;
    AuthPaymentProxy(PaymentProcessor real) { this.real = real; }
    public void processPayment(double amt, String cur) {
        if (!currentUserAuthorized()) throw new SecurityException("not allowed");
        real.processPayment(amt, cur);   // same interface, just guarded
    }
}
```

So for the same wrapped object: Proxy = same interface to control it, Adapter = different interface to translate it, Decorator = same interface plus extra behavior.

#### Bridge vs Adapter

The difference is **timing/intent**. **Bridge** is designed **up-front** so an abstraction and its implementation can vary independently from day one. **Adapter** is a **retrofit** — you glue together classes that *already exist* and weren't built to cooperate.

- Bridge: "I'm planning two axes of variation, so I'll separate them now." (proactive design)
- Adapter: "This vendor class already exists with the wrong shape; wrap it." (reactive fix)

Same structural look (both hold a reference to another object), opposite motivations.

#### Facade vs Adapter

**Facade** invents a **new, simpler** interface over a **whole complex subsystem** (many classes) to make it easy to use. **Adapter** makes **one** existing interface match a specific expected one — its goal is *compatibility*, not simplification.

They combine: you might wrap a messy legacy system in a **Facade** to make it usable, then write an **Adapter** so that Facade fits your new API contract. Rule of thumb: Facade *simplifies many*; Adapter *translates one*.

#### Similar structure, different problem (Bridge / State / Strategy / Adapter)

RG's closing point: *"Bridge, State, Strategy (and to some degree Adapter) have very similar structures... all based on composition, which is delegating work to other objects. However, they all solve different problems."*

Structurally they look almost identical — a class holding a reference to another object and delegating to it:

```java
class SomeContext {
    private final SomeInterface delegate;   // Bridge? State? Strategy? Adapter? — same skeleton
    SomeContext(SomeInterface delegate) { this.delegate = delegate; }
    void doWork() { delegate.handle(); }     // delegate the work
}
```

The skeleton doesn't tell you which pattern it is — the **intent** does:
- **Adapter** — the delegate has the *wrong interface*; you're translating.
- **Strategy** — the delegate is one of several *interchangeable algorithms*.
- **State** — the delegate represents the object's *current state* and it swaps *itself* over time.
- **Bridge** — the delegate is an *implementation axis* deliberately separated up-front.

RG's meta-lesson: a pattern isn't just a code shape — **naming** it (Adapter vs Strategy vs State) *communicates to other developers which problem you're solving*, even when the class diagrams are the same. Choose the name that signals your intent.

### Real-World Context — Hexagonal Architecture (Ports & Adapters)

Adapters are the backbone of Hexagonal Architecture. The application **core defines a "Port"** (a technology-agnostic interface). Each external technology gets an **"Adapter"** that satisfies that port. The core depends only on ports (respecting [[01 - SOLID Principles/05 - Dependency Inversion Principle]]); you "plug in" a technology by injecting its adapter.

```java
// PORT — defined by the core, knows nothing about any specific technology
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(String id);
}

// ADAPTER A — Postgres plugs into the port
public class PostgresOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbc;
    public void save(Order o) { jdbc.update("INSERT INTO orders ...", o.id()); }
    public Optional<Order> findById(String id) { /* SELECT ... */ return null; }
}

// ADAPTER B — Redis plugs into the SAME port
public class RedisOrderRepository implements OrderRepository {
    private final RedisClient redis;
    public void save(Order o) { redis.set("order:" + o.id(), serialize(o)); }
    public Optional<Order> findById(String id) { /* redis.get(...) */ return null; }
}

// A different port for messaging, with an AWS SQS adapter
public interface EventPublisher { void publish(DomainEvent e); }   // PORT
public class SqsEventPublisher implements EventPublisher {          // ADAPTER
    private final SqsClient sqs;
    public void publish(DomainEvent e) { sqs.sendMessage(toMessage(e)); }
}
```

Swapping Postgres → Redis, or SQS → Kafka, means writing a new adapter and injecting it — the core service code never changes. Each adapter *translates* the port's calls into that technology's specific SDK, which is exactly the Adapter pattern applied at architectural scale.

### Advanced Nuances — Class vs Object Adapter

GoF defined two forms:
- **Object Adapter** — uses **composition** (holds the adaptee instance), as in all code above.
- **Class Adapter** — uses **multiple inheritance** (inherits from both target and adaptee).

Java/C# lack multiple class inheritance, so **Object Adapters are the standard**. Favour composition anyway: an object adapter can also adapt *subclasses* of the adaptee, and avoids inheritance coupling.

## Principles Served

- [[01 - SOLID Principles/05 - Dependency Inversion Principle]] — the client/core depends on the target interface (or port), never the concrete adaptee/vendor.
- [[01 - SOLID Principles/02 - Open-Close Principle]] — add support for a new vendor/technology by adding a new adapter, without touching client code.

## Sources

- [Refactoring.guru — Adapter](https://refactoring.guru/design-patterns/adapter) (external, primary reference)
