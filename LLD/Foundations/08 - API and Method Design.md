---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# API and Method Design

> [!abstract] What this is
> The habits that make class and method signatures readable and hard to misuse: intention-revealing names, small focused methods, avoiding boolean flags and long parameter lists, static factories and builders, and handling `null` deliberately with `Optional` and fail-fast checks.

---

## Naming and Size

- **Names reveal intent.** `reserveSeats(showId, seatIds)` over `process(data)`; `isExpired()` over `check()`; `calculateFee` over `calc`.
- **Methods do one thing at one level of abstraction.** A public method reads like a short summary that calls well-named private steps.

```java
public Ticket park(Vehicle vehicle) {
    Spot spot = findFreeSpot(vehicle.type());
    spot.occupy(vehicle);
    return issueTicket(vehicle, spot);
}
```

- **Command-query separation:** a method either **changes state** (command, often `void`) or **returns information** (query, no side effects). `getBalance()` shouldn't deduct a fee.

---

## Parameters

### No boolean flags

```java
bookSeats(seats, true, false);                  // what do true and false mean?

bookSeats(seats, HoldPolicy.HOLD_FOR_PAYMENT);  // an enum says it
holdSeats(seats); confirmSeats(seats);          // or split into two methods
```

### Long parameter lists

- Four or more parameters, especially several of the same type, invite mixed-up arguments.
    + Group related values into a **value object** (`DateRange`, `Address`) ([[02 - Immutability and Value Objects]]).
    + Use a **builder** for objects with many optional fields ([[Builder]]).
    + Use a **parameter object / request record** for service methods: `book(BookingRequest request)`.

### Accept general types, return specific ones

- Parameters: the most general useful interface (`Collection<Seat>`, `Iterable`), so callers aren't forced into one implementation.
- Return types: something useful and unmodifiable (`List<Seat>`).

---

## Creating Objects

- **Static factory methods** have names, can return cached instances or subtypes, and can validate:

```java
Money.of(500, INR);
Money.zero(INR);
Duration.ofMinutes(15);
VehicleFactory.forType(VehicleType.CAR, plate);
```

- **Constructors** should leave the object fully valid: validate inputs, set every field, don't do I/O or start threads ([[LLD/Concurrency/13 - Designing Thread-Safe Classes]]).
- **Builders** for many optional parameters; validate in `build()`.

---

## Handling `null`

- `null` is the most common source of sloppy LLD code. Be deliberate about where it can appear.

| Situation | Do |
|---|---|
| A method may legitimately find nothing | Return **`Optional<T>`** (`findById`) |
| A method returns a collection | Return an **empty collection**, never `null` |
| A parameter must not be null | **`Objects.requireNonNull(param, "name")`** at the boundary (constructor, public method) |
| A field may be absent | A nullable field with a clear comment, or a dedicated value (`NoDiscount`, the **Null Object** pattern) |

```java
public Optional<Ticket> findActiveTicket(String plate) {
    return Optional.ofNullable(activeByPlate.get(plate));
}

ticketService.findActiveTicket(plate)
        .map(Ticket::fee)
        .orElseThrow(() -> new TicketNotFoundException(plate));
```

- `Optional` is for **return types**. Don't use it for fields, parameters, or collections (`Optional<List<T>>`: return an empty list).
- Don't call `optional.get()` without checking; use `orElse`, `orElseThrow`, `map`, `ifPresent`.

---

## Return Values and Errors

- Expected outcomes are part of the signature (`Optional`, boolean, or a sealed result type); unexpected failures are exceptions ([[01 - Exception Handling]]).
- Don't return special values like `-1` or `""` to mean "missing".
- Document preconditions and what the method throws when they're violated.

---

## Interview Lines

> [!tip] Say it like this
> - "Names say what the method does, and each public method reads like a summary of private steps."
> - "No boolean flags; I use an enum or split the method."
> - "Lookups return `Optional`, collections return empty rather than null, and I check required arguments with `requireNonNull` at the boundary."
> - "Many parameters become a request object or a builder."

## Related Notes

- [[01 - Exception Handling]] · [[02 - Immutability and Value Objects]] · [[03 - Object Modelling]]
- [[LLD/Foundations/README]]
