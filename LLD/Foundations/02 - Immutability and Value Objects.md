---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Immutability and Value Objects

> [!abstract] What this is
> How to model the "small things" in a domain (money, coordinates, date ranges, seat numbers) as **immutable value objects**, and the contracts that make objects behave correctly in collections: `equals`/`hashCode`, `Comparable` and `Comparator`, and records. Immutability removes a whole class of bugs, including concurrency bugs.

---

## Entities vs Value Objects

- An **entity** has an **identity** that persists while its attributes change: a `User`, an `Order`, a `ParkingSpot`. Two orders with the same contents are still different orders.
- A **value object** is defined **only by its values**, with no identity: `Money(100, INR)`, `Coordinate(12.9, 77.6)`, `DateRange(...)`. Two `Money(100, INR)` are interchangeable.

| | Entity | Value object |
|---|---|---|
| Equality | By id | By all fields |
| Mutability | State changes over its life | Immutable; "change" means a new object |
| Examples | `Order`, `Account`, `Vehicle` | `Money`, `Address`, `TimeSlot`, `SeatNumber` |

- Using value objects instead of raw primitives ("**primitive obsession**") puts validation and behaviour in one place: a `Money` can't have a negative amount or mix currencies; a raw `long` can.

```java
public record Money(long amountMinor, Currency currency) {
    public Money {                                         // compact constructor: validate
        if (amountMinor < 0) throw new IllegalArgumentException("negative amount");
        Objects.requireNonNull(currency);
    }
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amountMinor + other.amountMinor, currency);   // new object
    }
    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
    }
}
```

- Store money in **minor units** (paise, cents) as `long`, or as `BigDecimal`; never as `double` (rounding errors).

---

## Making a Class Immutable

1. All fields **`private final`**.
2. **No setters** or other mutating methods; "modifications" return a new instance (`withX(...)`, `plus(...)`).
3. Make the class **`final`** (or use a private constructor + static factory) so subclasses can't add mutable state.
4. **Defensive copies** of mutable inputs and outputs (`List.copyOf`, copying arrays and `Date`s).
5. Don't let `this` escape from the constructor.

- Full detail and the thread-safety angle: [[LLD/Concurrency/12 - Confinement and Immutability]].

### Why bother

- **Thread-safe** for free: share across threads with no locks.
- **Safe as map keys and set members**: the hash can't change after insertion.
- **No defensive copying** needed when passing them around.
- **Easier reasoning**: an object you received can't change behind your back.

---

## Records

- `record Coordinate(double lat, double lng) {}` generates private final fields, a canonical constructor, accessors (`lat()`), `equals`, `hashCode` and `toString`. It's the default way to write value objects in modern Java.
- **Compact constructors** validate and normalise.
- Records are **shallowly** immutable: a `List` component still points to whatever list was passed in. Copy it: `items = List.copyOf(items);`.
- Records can implement interfaces and have static factories and extra methods, but can't extend classes or declare extra instance fields.

---

## The `equals` / `hashCode` Contract

- `HashMap`, `HashSet` and friends rely on two rules:
    + If `a.equals(b)`, then `a.hashCode() == b.hashCode()`.
    + `equals` is **reflexive**, **symmetric**, **transitive**, **consistent**, and `x.equals(null)` is false.
- Override **both** or **neither**. Overriding only `equals` means equal objects land in different hash buckets, so `set.contains(copy)` returns false.

```java
@Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SeatNumber other)) return false;
    return row == other.row && number == other.number;
}
@Override public int hashCode() { return Objects.hash(row, number); }
```

> [!danger] Mutable fields in `hashCode`
> If a field used in `hashCode` changes **after** the object is in a `HashSet` or used as a `HashMap` key, the object sits in the wrong bucket: `contains` returns false and `remove` silently fails. Use immutable keys.

- **Entities**: base equality on the **id** only (and decide what an unsaved entity with no id means). **Value objects**: all fields.
- `instanceof` vs `getClass()` in `equals`: `instanceof` allows subclasses to be equal to the parent but risks breaking symmetry if subclasses add fields; `getClass()` is stricter. Making value classes `final` (or records) sidesteps the debate.

---

## Ordering: `Comparable` and `Comparator`

- **`Comparable<T>`** defines the **natural order** of a type (`compareTo`): one per class.
- **`Comparator<T>`** defines an **external** order you can pass around: any number of them.

```java
public record Version(int major, int minor) implements Comparable<Version> {
    @Override public int compareTo(Version o) {
        return Comparator.comparingInt(Version::major)
                         .thenComparingInt(Version::minor)
                         .compare(this, o);
    }
}

List<Order> sorted = orders.stream()
        .sorted(Comparator.comparing(Order::priority).reversed()
                          .thenComparing(Order::createdAt))
        .toList();
```

- Keep `compareTo` **consistent with `equals`** (returns 0 exactly when `equals` is true), or sorted collections behave oddly: a `TreeSet` treats `compareTo == 0` as a duplicate and drops the element.
- Never compare with subtraction (`a - b`): it overflows. Use `Integer.compare`, `Comparator.comparingInt`, etc.

---

## Interview Lines

> [!tip] Say it like this
> - "Entities are equal by id; value objects like `Money` are equal by value and immutable."
> - "I wrap primitives in value objects so validation and behaviour live in one place."
> - "Money is a `long` in minor units or a `BigDecimal`, never a double."
> - "Override `equals` and `hashCode` together, and never use a mutable field in `hashCode`."
> - "Records are my default value objects, with a compact constructor for validation and defensive copies for collections."

## Related Notes

- [[03 - Object Modelling]] · [[06 - Collections]] · [[LLD/Concurrency/12 - Confinement and Immutability]]
- [[LLD/Foundations/README]]
