---
tags: [languages/java, status/draft]
created: 2026-10-14
---
# Modern Java Language Features

> [!abstract] What this is
> The language features from Java 10 to 25 that change how everyday Java is written: **`var`** (10), **switch expressions** (14), **text blocks** (15), **records** (16), **pattern matching** for `instanceof` (16) and `switch` (21), **sealed types** (17), **record patterns** (21), **unnamed variables** (22), and the 25 additions (**flexible constructor bodies**, **compact source files**, **module imports**). The decision this helps with: modelling data and variants the modern way (algebraic data types) and spotting the traps each feature has.

---

## Why It Matters

- Code on 17+ that still uses mutable POJOs with Lombok, `instanceof` + cast chains and visitor boilerplate looks dated in review and in interviews.
- Records + sealed interfaces + pattern-matching `switch` together give Java **algebraic data types** with compiler-checked exhaustiveness: a real correctness gain, not just less typing.

---

## `var` (10)

```java
var orders = new ArrayList<Order>();          // ArrayList<Order>
for (var entry : totals.entrySet()) { ... }   // Map.Entry<String, BigDecimal>
```

- Local type inference only: locals, `for` loops, lambda parameters (11). Not for fields, parameters or return types. The type is still static.
- Use when the type is obvious from the right-hand side or noisy (`Map.Entry<String, List<Order>>`). Avoid when it hides meaning (`var result = service.process();`).
- Trap: `var list = new ArrayList<>();` infers `ArrayList<Object>`.

## Switch Expressions (14)

```java
int days = switch (month) {
    case FEBRUARY -> leap ? 29 : 28;
    case APRIL, JUNE, SEPTEMBER, NOVEMBER -> 30;
    default -> 31;
};
```

- Arrow labels have **no fall-through**; the `switch` yields a value; `yield` returns a value from a block.
- Over an enum (or sealed type) a switch **expression** must be exhaustive; the compiler checks it, and adds a default that throws `MatchException` (`IncompatibleClassChangeError` for enums before 21) if a new constant appears from a recompiled dependency.

## Text Blocks (15)

```java
String query = """
    SELECT id, total
    FROM orders
    WHERE customer_id = ?
    """;
```

- Incidental indentation is stripped relative to the closing `"""`. `\` at line end joins lines; `\s` keeps a trailing space.
- Still a string: use **parameters**, never `formatted()`/concatenation, to build SQL from user input.

---

## Records (16)

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {                                   // compact constructor: validate / normalise
        Objects.requireNonNull(currency);
        if (amount.signum() < 0) throw new IllegalArgumentException("negative");
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }
    public Money plus(Money other) { return new Money(amount.add(other.amount), currency); }
}
```

- A **transparent, shallowly immutable data carrier**: `private final` fields, canonical constructor, accessors (`amount()`, not `getAmount()`), and `equals`/`hashCode`/`toString` derived from all components.
- Implicitly `final`, extend `java.lang.Record`, can implement interfaces, have static members and extra methods; cannot declare extra instance fields.
- **Shallow** immutability: a `List` component can still be mutated. Copy defensively in the compact constructor: `items = List.copyOf(items);` ([[LLD/Foundations/02 - Immutability and Value Objects]]).
- Arrays as components get **identity** equality: two records with equal-content arrays are not `equal`.
- Good for: DTOs, value objects, map keys, multiple return values, events. Not for: JPA entities (need mutability, no-arg constructors, proxies) ([[07 - Spring Data JPA and Hibernate]]).
- Serialisation uses the canonical constructor, so validation runs on deserialise. Jackson supports records since 2.12.

## Sealed Classes and Interfaces (17)

```java
public sealed interface PaymentResult permits Approved, Declined, Pending {}
public record Approved(String authCode) implements PaymentResult {}
public record Declined(String reason)   implements PaymentResult {}
public record Pending(Instant retryAt)  implements PaymentResult {}
```

- The type lists its permitted subtypes; each must be `final`, `sealed` or `non-sealed`. Subtypes must be in the same module (or same package without modules).
- Gives the compiler a **closed set**, which makes `switch` exhaustive without `default`.
- Pick when: a domain concept has a fixed set of variants (results, commands, events, AST nodes). Use plain interfaces when third parties must add implementations.

---

## Pattern Matching

### `instanceof` (16)

```java
if (shape instanceof Circle c && c.radius() > 0) { return Math.PI * c.radius() * c.radius(); }
```

### `switch` patterns and record patterns (21)

```java
String describe(PaymentResult r) {
    return switch (r) {
        case Approved(var code)                      -> "approved " + code;
        case Declined(String reason) when reason.isBlank() -> "declined";
        case Declined(String reason)                 -> "declined: " + reason;
        case Pending p                               -> "retry at " + p.retryAt();
    };   // exhaustive: no default needed, compiler errors if a variant is added
}
```

- **Type patterns** (`case Pending p`), **record patterns** that deconstruct (`case Approved(var code)`), nested patterns, and **guards** (`when ...`).
- **Dominance**: a more general case before a more specific one is a compile error. Order specific → general.
- **`null`**: a `switch` throws `NullPointerException` on null unless there is an explicit `case null` (can be combined: `case null, default ->`).
- **Exhaustiveness** with sealed types replaces the visitor pattern for most uses: adding a variant breaks compilation everywhere it isn't handled. Don't add `default` to sealed switches, or you lose that check.
- **Primitive types in patterns** (`case int i when i > 0`) are preview from 23 onward.

### Unnamed variables and patterns (22)

```java
case Declined(_) -> "declined";
try { ... } catch (NumberFormatException _) { return 0; }
map.forEach((_, v) -> total += v);
```

---

## Java 25 Additions

| Feature | What it allows | Use |
|---|---|---|
| **Flexible constructor bodies** (JEP 513) | Statements before `super(...)`/`this(...)`, as long as they don't use `this` | Validate or compute arguments before calling the super constructor |
| **Compact source files and instance main** (JEP 512) | `void main() { IO.println("hi"); }` in a file with no class declaration | Scripts, teaching, quick tools |
| **Module import declarations** (JEP 511) | `import module java.base;` imports all exported packages of a module | Scripts and prototypes; avoid in large codebases (ambiguity) |

```java
public class PositiveAmount extends Amount {
    public PositiveAmount(long cents) {
        if (cents <= 0) throw new IllegalArgumentException("must be positive"); // before super: OK in 25
        super(cents);
    }
}
```

---

## Putting It Together: Data-Oriented Java

| Old style | Modern style |
|---|---|
| Mutable POJO + Lombok `@Data` for a value | `record` with validation in compact constructor |
| Abstract class + visitor for variants | `sealed interface` + records + exhaustive `switch` |
| `if (x instanceof A) { A a = (A) x; ... }` chains | `switch (x) { case A a -> ...; }` |
| Exceptions for expected outcomes (declined, not found) | A sealed result type returned and matched ([[LLD/Foundations/01 - Exception Handling]]) |
| String concatenation for JSON/SQL fixtures | Text blocks |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Record with mutable component** | "Immutable" value changes under you; broken map keys | `List.copyOf` / defensive copies in the compact constructor |
| **Record with array component** | `equals` compares identity | Use `List`, or override `equals`/`hashCode` |
| **Records as JPA entities** | Hibernate can't proxy or mutate them | Records for DTOs/projections; classes for entities |
| **`default` in a sealed switch** | New variants silently fall into default | Leave it out; let the compiler enforce exhaustiveness |
| **`switch` on null** | `NullPointerException` | `case null` explicitly or validate earlier |
| **`var` hiding types** | Unreadable code; `ArrayList<Object>` with diamond | Use only when the type is obvious |
| **Preview features in prod** (primitive patterns) | Need `--enable-preview`; API changes | Final features only |

---

## Interview Lines

> [!tip] Say it like this
> - "Records are transparent, shallowly immutable carriers: I validate and defensively copy in the compact constructor."
> - "Sealed interfaces plus records plus pattern-matching `switch` give Java algebraic data types, and the compiler checks exhaustiveness, so adding a variant breaks every switch that doesn't handle it."
> - "I don't put `default` on a switch over a sealed type, because it turns a compile error into a runtime bug."
> - "`var` is local inference only; the type is still static."
> - "Java 25 lets constructors validate arguments before calling `super`."

## Related Notes

- [[00 - Java Version History]] · [[07 - Java Collections Internals]] · [[08 - Java Streams Optional and Functional Style]]
- [[LLD/Foundations/02 - Immutability and Value Objects]] · [[LLD/Foundations/04 - Enums and State Machines]] · [[LLD/Foundations/05 - Generics]] · [[Languages/README]]
