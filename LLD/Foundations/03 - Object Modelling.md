---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Object Modelling

> [!abstract] What this is
> The core skill of an LLD round: turning requirements into classes with clear responsibilities, relationships and invariants. Covers finding the objects, composition over inheritance, interfaces vs abstract classes, encapsulation that actually protects invariants, aggregates, and where behaviour should live.

---

## From Requirements to Classes

- A repeatable approach for the first 10 minutes of an LLD round:
    + **1. Clarify use cases.** "A driver parks, gets a ticket, pays on exit." List the actors and the 3–5 core operations.
    + **2. Pick out nouns → candidate classes.** `ParkingLot`, `Floor`, `Spot`, `Vehicle`, `Ticket`, `Payment`.
    + **3. Pick out verbs → behaviour.** `park`, `issueTicket`, `calculateFee`, `pay`. Ask *which object owns the data the verb needs*: that's where the method goes.
    + **4. Find what varies.** Vehicle types, pricing rules, spot-assignment strategy. Variation points become interfaces ([[Strategy]], [[Factory Pattern]]).
    + **5. Name the invariants.** "A spot holds at most one vehicle", "a ticket is paid at most once". Decide which class enforces each.
    + **6. Draw relationships**, then write the core classes and one end-to-end flow.
- Drop nouns that are just attributes (`licensePlate` is a field of `Vehicle`, not a class) unless they carry rules (then make them value objects, [[02 - Immutability and Value Objects]]).

---

## Relationships

| Relationship | Meaning | Java shape | Example |
|---|---|---|---|
| **Association** | Uses / knows about | A field or parameter | `Ticket` refers to a `Spot` |
| **Aggregation** | Has, but parts live independently | Field holding objects created elsewhere | `Team` has `Player`s |
| **Composition** | Owns; parts die with the whole | Whole creates and fully controls parts | `Order` owns its `LineItem`s |
| **Inheritance** | Is-a | `extends` | `Car extends Vehicle` (use sparingly) |
| **Realisation** | Implements a contract | `implements` | `UpiPayment implements PaymentMethod` |
| **Dependency** | Briefly uses | Method parameter or local | `FeeCalculator.compute(Ticket)` |

---

## Composition Over Inheritance

- **Inheritance** couples a subclass to every detail of its parent, fixes the relationship at compile time, and explodes when variations combine (`ElectricCar`, `ElectricTruck`, `LargeElectricTruck`…).
- **Composition** gives an object a collaborator that provides the varying behaviour, and that collaborator can be swapped.

```java
// Inheritance: every pricing variation needs a subclass of Spot
class HourlySpot extends Spot { ... }
class FlatRateSpot extends Spot { ... }

// Composition: Spot keeps its job; pricing is a pluggable collaborator
interface PricingStrategy { Money fee(Duration parked, VehicleType type); }
class ParkingLot {
    private final PricingStrategy pricing;            // injected, swappable
    ParkingLot(PricingStrategy pricing) { this.pricing = pricing; }
}
```

- Use inheritance only for a genuine **is-a** with a **stable** hierarchy where subclasses honour the parent's contract (Liskov substitution); otherwise prefer interfaces + composition. The SOLID notes go deeper ([[LLD/Sanket Singh Course/01 - SOLID Principles/01 - Single Responsibility Principle]]).

---

## Interfaces vs Abstract Classes

| | Interface | Abstract class |
|---|---|---|
| Purpose | A **capability / contract** ("can be paid", "can be notified") | A **partial implementation** shared by closely related classes |
| State | No instance fields (constants only) | Can hold fields and constructors |
| Multiple | A class can implement many | A class extends one |
| Evolution | `default` methods add behaviour without breaking implementers | Add concrete methods freely |
| Pick when | Default for variation points and dependencies | Several subclasses share real state and logic (template method) |

- Depend on **interfaces** in fields and parameters (`PaymentMethod`, `NotificationSender`), so implementations can change and tests can substitute fakes ([[07 - Dependency Injection and Layering]]).
- An abstract class is often an implementation detail **behind** an interface.

---

## Encapsulation That Protects Invariants

- Encapsulation isn't "private fields with public getters and setters". It means **the object controls every change to its state**, so its invariants can't be broken from outside.

```java
// Anemic: anyone can break the rules
class Ticket {
    public Status status;
    public Money paidAmount;
}
ticket.status = Status.PAID;          // without paying

// Encapsulated: the only way to change state enforces the rules
class Ticket {
    private Status status = Status.ACTIVE;
    private Money paid;

    public void pay(Money amount, Money due) {
        if (status != Status.ACTIVE) throw new IllegalStateException("ticket " + id + " is " + status);
        if (amount.compareTo(due) < 0) throw new IllegalArgumentException("insufficient payment");
        this.paid = amount;
        this.status = Status.PAID;
    }
    public Status status() { return status; }
}
```

- **Tell, don't ask:** tell objects to do things (`ticket.pay(...)`) instead of asking for their data and deciding outside (`if (ticket.getStatus() == ...) ticket.setStatus(...)`).
- No setters by default; add **intention-revealing** methods (`markDelivered()`, `cancel(reason)`).
- Return unmodifiable views or copies of internal collections ([[02 - Immutability and Value Objects]]).

---

## Aggregates and Boundaries

- An **aggregate** is a cluster of objects treated as one unit for changes, with one **root** that outsiders go through. `Order` (root) owns `LineItem`s; nobody adds a line item except through `order.addItem(...)`, so the order can keep its total and limits correct.
- Rules of thumb:
    + Reference other aggregates **by id**, not by holding the whole object (a `Booking` holds `showId`, not a `Show`).
    + Keep aggregates small: big aggregates mean big locks and big transactions.
    + One transaction changes one aggregate where possible; coordination across aggregates goes through services or events.
- The aggregate root is the natural place for the **lock** in a concurrent LLD problem: lock the show, the account, the parking floor ([[LLD/Concurrency/13 - Designing Thread-Safe Classes]]).

---

## Where Behaviour Lives

| Kind of logic | Lives in | Example |
|---|---|---|
| Rules about one object's own state | The **entity / value object** | `ticket.pay()`, `money.plus()` |
| A rule that varies or is pluggable | A **strategy** behind an interface | `PricingStrategy`, `SpotAssignmentStrategy` |
| Coordinating several aggregates or external systems | A **service** | `BookingService.book()` reserves seats, charges, issues tickets |
| Creating complex objects | A **factory / builder** | `VehicleFactory`, `Order.builder()` ([[Builder]]) |
| Persistence | A **repository** interface | `TicketRepository` ([[07 - Dependency Injection and Layering]]) |

- Warning signs: a `Manager` or `Util` class doing everything (God class), entities that are just data bags while services hold all the rules (anemic model), and deep `if`/`switch` on a type field (replace with polymorphism or a strategy).

---

## Interview Lines

> [!tip] Say it like this
> - "I start from the use cases, pull out nouns and verbs, and put each behaviour on the object that owns its data."
> - "What varies becomes an interface, like the pricing strategy; I prefer composition over inheritance."
> - "Encapsulation means the object controls its state changes, so `pay()` enforces the rules instead of a public setter."
> - "Other aggregates are referenced by id, and the aggregate root is where I'd put the lock."
> - "Services coordinate; entities enforce their own invariants."

## Related Notes

- [[02 - Immutability and Value Objects]] · [[04 - Enums and State Machines]] · [[07 - Dependency Injection and Layering]]
- [[LLD/Foundations/README]]
