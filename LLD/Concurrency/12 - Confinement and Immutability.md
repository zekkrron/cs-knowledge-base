---
tags: [lld/concurrency, status/draft]
created: 2026-10-11
---
# Confinement and Immutability

> [!abstract] What this is
> The two cheapest ways to be thread-safe: **don't share** the state (thread confinement, `ThreadLocal`) or **don't change** it (immutable objects, copy-on-write snapshots). No locks means nothing to get wrong. Always consider these before reaching for `synchronized`.

---

## Thread Confinement

- If only **one thread** can ever reach an object, it needs no synchronization, however mutable it is.

| Kind | How | Example |
|---|---|---|
| **Stack confinement** | Local variables; objects created and used inside one method call and never stored in a field | Building a `StringBuilder` or a `List` inside a method and returning an immutable result |
| **Owner-thread confinement** | All access to some state is funnelled through one designated thread | A single-threaded event loop, a `newSingleThreadExecutor` that owns a cache, the UI thread |
| **`ThreadLocal`** | Each thread gets its own copy of a variable | Per-thread `SimpleDateFormat`, per-request context |

- The "actor" style is owner-thread confinement: instead of locking shared state, **send messages** to the one thread that owns it (via a `BlockingQueue`). Correctness reasoning becomes single-threaded ([[16 - Advanced Topics]]).
- Confinement is a **discipline**, not enforced by Java: one `this.cache = localList` in a field and it's shared.

---

## `ThreadLocal`

```java
// SimpleDateFormat is not thread-safe; give each thread its own instance
private static final ThreadLocal<SimpleDateFormat> FORMAT =
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

String format(Date d) { return FORMAT.get().format(d); }
```

- Each thread reading `FORMAT.get()` sees **its own** object, created on first use.
- (Modern code would use the immutable, thread-safe `DateTimeFormatter` instead, which removes the need.)

### Per-request context

- Frameworks store the current user, tenant or trace id in a `ThreadLocal` so deep code can read it without passing it through every method (Spring's `SecurityContextHolder`, MDC logging context).

### The thread-pool leak

> [!danger] Always `remove()` in a pool
> Pool threads **live forever** and are reused for unrelated tasks. A value set by one request is still there for the next request on that thread: wrong user, wrong tenant, or a slow memory leak (in app servers, it can even pin a whole classloader).
> ```java
> try {
>     CONTEXT.set(requestContext);
>     handle(request);
> } finally {
>     CONTEXT.remove();          // always clean up
> }
> ```

- `ThreadLocal` values **don't flow** into tasks you hand to other threads (executors, `CompletableFuture`). Pass context explicitly, or use a wrapper that copies it.
- With **virtual threads** (millions of them), large `ThreadLocal`s multiply memory; Java 21+ previews **`ScopedValue`** as the immutable, bounded replacement ([[15 - Virtual Threads]]).

---

## Immutability

- An **immutable** object's state can't change after construction. Any number of threads can read it with no locks, and it can be published even through a data race when its fields are `final` ([[03 - Java Memory Model]]).

### Rules for an immutable class

1. All fields **`private final`**.
2. **No setters**, no methods that change state.
3. The class can't be subclassed (`final` class, or private constructor + factory), so a subclass can't add mutability.
4. **Defensive copies** of mutable inputs and outputs: copy collections/arrays in the constructor and never hand out internal mutable objects.
5. Don't let **`this` escape** during construction.

```java
public final class Order {
    private final String id;
    private final List<LineItem> items;          // LineItem must be immutable too
    private final Instant createdAt;

    public Order(String id, List<LineItem> items, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.items = List.copyOf(items);          // defensive, unmodifiable copy
        this.createdAt = createdAt;               // Instant is already immutable
    }

    public List<LineItem> items() { return items; }   // safe: already unmodifiable

    public Order withItem(LineItem item) {            // "change" = return a new object
        List<LineItem> next = new ArrayList<>(items);
        next.add(item);
        return new Order(id, next, createdAt);
    }
}
```

- **Records** (`record Money(long amount, String currency) {}`) give final fields, a canonical constructor, `equals`/`hashCode`/`toString`. They're **shallowly** immutable: a `record Cart(List<Item> items)` still holds whatever list you pass, so copy it in a compact constructor (`items = List.copyOf(items);`).
- `Collections.unmodifiableList(list)` is a **read-only view**: the original list can still change underneath. `List.copyOf` / `List.of` make a true unmodifiable copy.

### Changing shared immutable state

- "Mutation" becomes **replace the reference**: build a new object and swap it in.
    + Single writer: store it in a `volatile` field.
    + Many writers: `AtomicReference.updateAndGet(old -> old.with(...))` ([[06 - Atomics and CAS]]).
- Readers always see a complete, consistent snapshot. Config reloads, routing tables, feature flags and price lists are classic uses.
- `CopyOnWriteArrayList` is this idea packaged as a collection ([[07 - Concurrent Collections]]).

### Costs

- Allocation for every change: fine for most objects, not for something updated millions of times a second (use a lock or an atomic counter there).
- Large structures need persistent/structural-sharing collections to make copies cheap; outside interviews, libraries provide them.

---

## Effectively Immutable and Stateless

- **Effectively immutable**: technically mutable, but never changed after being safely published (e.g. a config object built at startup, then only read). Safe, provided publication is safe.
- **Stateless** objects (no fields, or only final immutable ones) are always thread-safe: most services, strategies and validators should look like this. That's why stateless strategies can be shared as singletons ([[Singleton]], [[Strategy]]).

---

## Interview Lines

> [!tip] Say it like this
> - "My first choice is not to share state, my second is to make it immutable; locks come third."
> - "An immutable class has private final fields, no setters, defensive copies, and can't be subclassed."
> - "To change shared immutable state I build a new snapshot and swap it in through a `volatile` field or an `AtomicReference`."
> - "`ThreadLocal` in a thread pool must be removed in `finally`, or the next request sees the previous user's data."
> - "Stateless services are thread-safe by construction, so I keep request data in parameters, not fields."

## Related Notes

- [[02 - Race Conditions and Shared State]] · [[03 - Java Memory Model]] · [[13 - Designing Thread-Safe Classes]] · [[LLD/Foundations/02 - Immutability and Value Objects]]
- [[LLD/Concurrency/README]]
