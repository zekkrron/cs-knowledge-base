---
tags: [lld/patterns/structural, status/draft]
created: 2026-08-04
---
# Decorator Pattern

> [!abstract] Attach extra responsibilities to an object dynamically at runtime by wrapping it in objects that share its interface — a compositional alternative to subclassing for extending behavior.

**External references:**
- [Refactoring.guru — Decorator](https://refactoring.guru/design-patterns/decorator)
- [Refactoring.guru — Decorator in Java](https://refactoring.guru/design-patterns/decorator/java/example)

## Part 1 — Core Framework

### Main Purpose

Add behaviors to an object dynamically at runtime by wrapping it. A flexible alternative to subclassing that adheres to [[02 - Open-Close Principle]] — extend behavior without modifying the original class.

### Recognition Signal

> [!tip] The cue
> - **Combinatorial subclass explosion** — a `Notifier` needing Email, SMS, Slack, plus `EmailAndSms`, `SmsAndSlack`, ... inheritance breeds dozens of classes.
> - You need to **add/remove behaviors at runtime**, not lock them into a compile-time hierarchy.
> - You want to extend a **final class or third-party class** you can't modify.

### How to Implement

1. Define the **component interface** (`DataSource`).
2. Write the **concrete component** (core behavior, e.g. `FileDataSource`).
3. Write a **base decorator** implementing the interface and holding a wrapped component (`wrappee`); by default it delegates every call to the wrappee.
4. Write **concrete decorators** that override methods, adding behavior before/after calling `super`.

### Key Code

```java
// 1. Component interface
public interface DataSource {
    void writeData(String data);
    String readData();
}

// 2. Concrete component — the core behavior
public class FileDataSource implements DataSource {
    private final String name;
    public FileDataSource(String name) { this.name = name; }
    public void writeData(String data) { System.out.println("Writing '" + data + "' to " + name); }
    public String readData() { return "RawFileContent"; }
}

// 3. Base decorator — implements the SAME interface, holds a wrappee, delegates
public abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrappee;
    public DataSourceDecorator(DataSource source) { this.wrappee = source; }
    public void writeData(String data) { wrappee.writeData(data); }
    public String readData() { return wrappee.readData(); }
}

// 4. Concrete decorators
public class EncryptionDecorator extends DataSourceDecorator {
    public EncryptionDecorator(DataSource source) { super(source); }
    public void writeData(String data) { super.writeData("Encrypted{" + data + "}"); }
}
public class CompressionDecorator extends DataSourceDecorator {
    public CompressionDecorator(DataSource source) { super(source); }
    public void writeData(String data) { super.writeData("Compressed[" + data + "]"); }
}

// 5. Client assembly — order matters
DataSource plain = new FileDataSource("salary.dat");
DataSource secured = new EncryptionDecorator(new CompressionDecorator(plain));
secured.writeData("Salary=50000");
// Writing 'Encrypted{Compressed[Salary=50000]}' to salary.dat
```

### Beyond the Basics

The elegance: a decorator implements the **exact same interface** as what it wraps. The client has no idea whether it's talking to a raw `FileDataSource` or a stack of 10 decorators — it just calls `writeData()`.

### Anti-Patterns & When NOT to Use

- **Order dependency.** If decorators break when applied in a different order (encrypt-then-compress vs compress-then-encrypt give different sizes), document heavily or enforce order with a Builder/Factory. Pure decorators should ideally be order-independent.
- **The identity problem.** Wrapping loses the original object's identity. Code relying on `if (src instanceof FileDataSource)` fails because `src` is now an `EncryptionDecorator`.

## Part 2 — Architecture Deep Dive

### Advanced Nuances — Transparent vs Semi-Transparent

> [!info] "API" here = the set of public methods on the object, **not** a web/HTTP API.

- **Transparent decorator** — adds *no* new public methods; it only intercepts methods already on the interface. The client is completely blind to the decoration. This is the ideal.
- **Semi-transparent decorator** — adds *new* public methods beyond the interface (e.g. `getEncryptionKeyVersion()`). This lets you extend the API but leaks badly.

```java
public class EncryptionDecorator extends DataSourceDecorator {
    public EncryptionDecorator(DataSource source) { super(source); }
    public void writeData(String data) { super.writeData("Encrypted{" + data + "}"); }

    // NEW public method — not on the DataSource interface (this makes it semi-transparent)
    public int getEncryptionKeyVersion() { return 3; }
}
```

**Why it's a problem in strongly-typed languages:** the reference type on the **left** of the assignment is the *interface*, but the object on the **right** is a *concrete* decorator:

```java
DataSource src = new EncryptionDecorator(new FileDataSource("x"));  // left type = DataSource

src.writeData("hi");                // OK — declared on the interface
src.getEncryptionKeyVersion();      // COMPILE ERROR — DataSource has no such method

// To reach the new method you must downcast to the concrete type:
((EncryptionDecorator) src).getEncryptionKeyVersion();   // compiles, but...
```

That downcast breaks the pattern two ways:
1. **It destroys polymorphism / couples the client to the concrete decorator** — the client now must *know* it's holding an `EncryptionDecorator`, which defeats the whole point of decorators being interchangeable and transparent.
2. **It's runtime-fragile** — if `src` is wrapped in yet another decorator (`new CompressionDecorator(new EncryptionDecorator(...))`), the cast to `EncryptionDecorator` throws `ClassCastException` at runtime, because the outermost object is now a `CompressionDecorator`.

So the compiler only exposes interface methods (left-side type), and any extra decorator method is unreachable without a brittle cast. **Strive for pure transparency** — keep decorators to the interface's methods only.

### Pattern Synergy — relations with other patterns

#### Adapter vs Decorator

Adapter *changes* the interface; Decorator *enhances without changing* it. And Decorator supports **recursive composition** (stacking), which Adapter can't — because a decorator's input type == output type, while an adapter converts one type to a different one. Full treatment in [[Adapter]].

#### Interface trio — Adapter / Proxy / Decorator

| Pattern | Interface it gives the wrapped object |
|---|---|
| Adapter | a **different** interface |
| Proxy | the **same** interface |
| Decorator | the **same but enhanced** interface |

#### Chain of Responsibility vs Decorator

Nearly identical class structure — both use recursive composition to pass execution through a series of objects. The crucial differences:
- **CoR handlers can act independently and *stop* the chain** at any point (e.g. an auth handler rejects a request and it goes no further).
- **Decorators must keep the base interface consistent and are NOT allowed to break the flow** — each decorator does its bit and always passes the call along.

```java
// CoR — a handler may STOP the flow
class AuthHandler extends Handler {
    public void handle(Request r) {
        if (!r.isAuthorized()) return;      // <-- stops here; next handler never runs
        next.handle(r);
    }
}

// Decorator — always passes through, just adds behavior around the call
class EncryptionDecorator extends DataSourceDecorator {
    public void writeData(String data) {
        super.writeData("Encrypted{" + data + "}");   // <-- always delegates onward
    }
}
```

#### Composite vs Decorator

Same recursive-composition diagram, but:
- **A Decorator is like a Composite with exactly one child** (it wraps a single component).
- **Decorator *adds* responsibilities** to that one child; **Composite just "sums up" its children's results** across many children.

They cooperate: you can use a Decorator to extend the behavior of one specific node inside a [[Composite]] tree.

```java
// A single decorated node placed inside a Composite tree
Component tree = new Box(                       // Composite (many children)
    new EncryptionDecorator(new FileNode("a")), // Decorator wrapping ONE child
    new FileNode("b"));
```

#### Prototype + Composite/Decorator

Designs heavy on Composite and Decorator build up deep nested structures. [[Prototype]] helps: **clone** the assembled structure instead of re-constructing it step-by-step from scratch.

#### Strategy — "skin vs guts"

> Decorator lets you change the **skin** of an object; Strategy lets you change its **guts**.

Decorator wraps the object from *outside*, adding features before/after the core runs (skin). [[Strategy]] swaps the *inner* algorithm the object uses (guts). Skin = layered around; guts = replaced within.

#### Proxy vs Decorator

Structurally identical, different intent. The RG distinction: **a Proxy usually manages the life cycle of its service object itself** (it may create/destroy the real object, e.g. lazy-loading), **whereas a Decorator's composition is always controlled by the client** (the client assembles the wrapper chain and hands in the wrappee). Also: Proxy = *control access*, Decorator = *add behavior*.

### Real-World Context

- **Java I/O streams** — the most famous Decorator: `new BufferedReader(new InputStreamReader(new FileInputStream(file)))` stacks three decorators to add buffering and byte→char conversion onto a raw file stream.
- **Web middleware (Express / Spring)** — a request passes through a logging wrapper, an auth wrapper, then the core controller. (Sometimes modeled as Chain of Responsibility, but often behaves like Decorator.)

## Principles Served

- [[02 - Open-Close Principle]] — add behavior by adding a decorator class, never modifying the component.
- [[01 - Single Responsibility Principle]] — each decorator owns exactly one added concern (encryption, compression, logging).

## Sources

- [Refactoring.guru — Decorator](https://refactoring.guru/design-patterns/decorator) (external, primary reference)
