---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Generics

> [!abstract] What this is
> Writing reusable, type-safe components (caches, repositories, event buses, pools) with Java generics: type parameters, bounded types, wildcards and the **PECS** rule, generic methods, and the limits caused by type erasure.

---

## Why Generics

- Without generics, a reusable component stores `Object` and every caller casts, with failures showing up at runtime as `ClassCastException`. Generics move that check to **compile time**.

```java
public interface Repository<T, ID> {
    Optional<T> findById(ID id);
    void save(T entity);
}

public final class InMemoryRepository<T, ID> implements Repository<T, ID> {
    private final Map<ID, T> store = new ConcurrentHashMap<>();
    private final Function<T, ID> idOf;

    public InMemoryRepository(Function<T, ID> idOf) { this.idOf = idOf; }
    public Optional<T> findById(ID id) { return Optional.ofNullable(store.get(id)); }
    public void save(T entity) { store.put(idOf.apply(entity), entity); }
}

Repository<Ticket, String> tickets = new InMemoryRepository<>(Ticket::id);
```

- Typical generic components in LLD: `Cache<K, V>`, `Repository<T, ID>`, `EventBus<E>`, `Pool<T>`, `Result<T>`.

---

## Bounded Type Parameters

- **`<T extends Bound>`** restricts `T` to `Bound` or its subtypes, and lets you call `Bound`'s methods on `T`.

```java
public static <T extends Comparable<? super T>> T max(List<T> items) {
    T best = items.get(0);
    for (T item : items) if (item.compareTo(best) > 0) best = item;
    return best;
}

class Garage<V extends Vehicle> {           // only vehicles can be parked
    private final List<V> vehicles = new ArrayList<>();
    void park(V v) { vehicles.add(v); }
}
```

- Multiple bounds: `<T extends Number & Comparable<T>>` (a class first, then interfaces).

---

## Wildcards and PECS

- Generic types are **invariant**: a `List<Car>` is **not** a `List<Vehicle>`, even though a `Car` is a `Vehicle`. If it were, you could add a `Truck` to a list of cars.
- Wildcards add flexibility:
    + **`? extends T`** ("some subtype of T"): you can **read** `T`s out, but can't add (except `null`).
    + **`? super T`** ("some supertype of T"): you can **add** `T`s in, but reads only give `Object`.
- **PECS: Producer `extends`, Consumer `super`.** If a parameter **produces** values you read, use `extends`; if it **consumes** values you write, use `super`.

```java
// src produces vehicles (we read), dst consumes them (we write)
static <T> void copy(List<? extends T> src, List<? super T> dst) {
    for (T item : src) dst.add(item);
}

void parkAll(Collection<? extends Vehicle> vehicles) { ... }       // accepts List<Car>, Set<Truck>
void collectInvoices(List<? super Invoice> sink) { sink.add(...); } // accepts List<Object>
```

- `Comparator<? super T>` is the classic consumer: a comparator of `Vehicle` can sort a list of `Car`.
- Use plain `T` when the parameter both produces and consumes; use `?` (unbounded) when the type doesn't matter (`size()`, `clear()`).

---

## Generic Methods

- A method can declare its own type parameters, independent of the class: `public static <K, V> Map<V, K> invert(Map<K, V> m)`.
- Type inference usually works it out; occasionally you write it explicitly: `Collections.<String>emptyList()`.

---

## Type Erasure and Its Limits

- Generics exist only at compile time; the compiler **erases** type parameters to their bounds (usually `Object`) and inserts casts. At runtime, a `List<String>` and a `List<Integer>` are both just `List`.
- Consequences:
    + Can't do `new T()`, `new T[]`, `instanceof List<String>`, or `T.class`.
    + Can't overload methods that differ only in type arguments (`void f(List<String>)` and `void f(List<Integer>)` clash).
    + Primitives can't be type arguments (`List<int>`), so boxing costs apply.
- Workarounds: pass a `Class<T>` token or a `Supplier<T>` factory when you need to create instances.

```java
class Registry<T> {
    private final Supplier<T> factory;
    Registry(Supplier<T> factory) { this.factory = factory; }
    T create() { return factory.get(); }
}
new Registry<>(ParkingTicket::new);
```

- **Raw types** (`List` with no type argument) switch off checking; don't use them in new code.
- **Heap pollution** with generic varargs: annotate safe generic varargs methods with `@SafeVarargs`.

---

## Interview Lines

> [!tip] Say it like this
> - "I make reusable components generic, like `Cache<K, V>` or `Repository<T, ID>`, so type errors are caught at compile time."
> - "Generics are invariant; a `List<Car>` isn't a `List<Vehicle>`, which is why wildcards exist."
> - "PECS: if I read from it, `? extends`; if I write into it, `? super`."
> - "Because of erasure I can't call `new T()`, so I pass a `Supplier<T>` or a `Class<T>`."

## Related Notes

- [[06 - Collections]] · [[07 - Dependency Injection and Layering]]
- [[LLD/Foundations/README]]
