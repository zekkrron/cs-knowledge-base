---
tags: [lld/patterns/behavioural, status/draft]
created: 2026-10-11
---
# State Pattern

> [!abstract] Let an object change its behaviour when its internal state changes, by moving each state's behaviour into its own class and delegating to the current one — to the client it looks as if the object changed its class at runtime.

**External references:**
- [Refactoring.guru — State](https://refactoring.guru/design-patterns/state)
- [Refactoring.guru — State in Java](https://refactoring.guru/design-patterns/state/java/example)

## Part 1 — Core Framework

### Main Purpose

To allow an object to **alter its behavior when its internal state changes**. The pattern extracts **state-specific behaviors into separate classes**, forcing the original object (the **context**) to **delegate** the work to the currently active state object. To the client, it appears as though the object has magically changed its class at runtime.

### Recognition Signal

> [!tip] The cue
> - An object's behavior depends entirely on its current **"status" or "lifecycle phase"** (e.g. an `Order` that is Pending, Paid, or Shipped).
> - You have **massive `switch` or `if/else` statements inside multiple methods** of a class, all checking the **exact same status variable** to figure out what to do.
> - You need to **enforce strict rules about transitions** (e.g. you cannot "Ship" an Order that is still in the "Pending Payment" state).

### How to Implement

1. Define a **state interface** with one method per event the context reacts to (`pay`, `ship`, `cancel`), each taking the context.
2. Write one **concrete state** class per state. Each method either does the work and **triggers the transition** (`context.setState(...)`) or rejects the event.
3. Give the **context** a `currentState` field, an initial state, a `setState()`, and public methods that simply **delegate** to `currentState`.
4. The **client** only calls the context's methods; it never sees the state classes.

### Key Code

You are building the backend for a Vending Machine or an E-commerce Order. An order can be **Unpaid**, **Paid**, or **Shipped**. If a user tries to cancel a Shipped order, it should fail. If they try to pay for an already Paid order, it should fail. Instead of a spaghetti of if-statements, we use the State pattern.

```java
// 1. The State Interface
public interface OrderState {
    void pay(OrderContext context);
    void ship(OrderContext context);
    void cancel(OrderContext context);
}

// 2. Concrete States
public class UnpaidState implements OrderState {
    public void pay(OrderContext context) {
        System.out.println("Payment accepted. Moving to PAID state.");
        context.setState(new PaidState()); // Triggering the transition
    }
    public void ship(OrderContext context) {
        System.out.println("Cannot ship. Order is not paid yet.");
    }
    public void cancel(OrderContext context) {
        System.out.println("Order cancelled successfully.");
        // transition to CancelledState...
    }
}

public class PaidState implements OrderState {
    public void pay(OrderContext context) {
        System.out.println("Order is already paid.");
    }
    public void ship(OrderContext context) {
        System.out.println("Shipping order now! Moving to SHIPPED state.");
        context.setState(new ShippedState());
    }
    public void cancel(OrderContext context) {
        System.out.println("Refunding payment and cancelling order.");
        // transition to CancelledState...
    }
}

public class ShippedState implements OrderState {
    public void pay(OrderContext context) { System.out.println("Already paid."); }
    public void ship(OrderContext context) { System.out.println("Already shipped."); }
    public void cancel(OrderContext context) { System.out.println("Too late. Already shipped."); }
}

// 3. The Context (The Core Object)
public class OrderContext {
    private OrderState currentState;

    public OrderContext() {
        this.currentState = new UnpaidState(); // Initial State
    }

    public void setState(OrderState state) {
        this.currentState = state;
    }

    // Delegation to the current state
    public void pay() { currentState.pay(this); }
    public void ship() { currentState.ship(this); }
    public void cancel() { currentState.cancel(this); }
}

// 4. The Client
public class ECommerceSystem {
    public static void main(String[] args) {
        OrderContext order = new OrderContext();

        order.ship(); // Fails: Cannot ship unpaid.
        order.pay();  // Success: Moves to Paid.
        order.pay();  // Fails: Already paid.
        order.ship(); // Success: Moves to Shipped.
    }
}
```

### Beyond the Basics

In the strict GoF pattern, **the Concrete States themselves dictate the transitions** (e.g. `UnpaidState` explicitly calls `context.setState(new PaidState())`). This perfectly encapsulates the rules of **what state can legally follow another**: each state class knows its own outgoing transitions, and the context stays free of transition logic.

### Anti-Patterns & When NOT to Use

- **State Explosion.** If your object has 15 different states and 20 different trigger methods, writing 15 concrete classes with 20 methods each becomes a maintenance nightmare. At that point, you need a **dedicated State Machine library**, not a manual GoF pattern implementation.
- **Stateless States.** If your concrete states don't actually hold any instance variables (they just execute logic and transition the context), you shouldn't be calling `new PaidState()` every time. Implement the concrete states as **[[Singleton]]s** to save memory.

```java
// Stateless states shared as singletons (enum constants are the simplest singletons)
public enum OrderStates implements OrderState {
    UNPAID { public void pay(OrderContext c) { c.setState(PAID); } /* ... */ },
    PAID   { public void ship(OrderContext c) { c.setState(SHIPPED); } /* ... */ },
    SHIPPED{ /* ... */ };
}
```

## Part 2 — Architecture Deep Dive

### Pattern Synergy

#### State vs Strategy

This is the most common interview question regarding this pattern, as their **UML diagrams are nearly identical** (a context holding a reference to an interface with swappable implementations).

| | [[Strategy]] | State |
|---|---|---|
| Mechanic | **"Pull"**: the **client** dynamically injects the strategy from the **outside** | **"Push"**: the **context transitions itself** from the **inside** |
| Lifetime | Usually stays the same for the lifespan of that operation | Changes as the object moves through its lifecycle |
| Awareness | Strategies **do not know about each other** | States are **highly aware of each other**, because they dictate the transitions to the next state |

#### State + Observer

When a context transitions to a new State, it frequently fires an event using the **[[Observer]]** pattern (e.g. "Order changed to Shipped!") so that external services can react.

### Real-World Context

While the manual implementation is great for LLD rounds, in real-world production backends, complex state logic is almost always handled by dedicated **Finite State Machine (FSM)** frameworks:

- **Spring Statemachine:** for enterprise Java, developers use this library to define states, transitions, and triggers in a **centralized configuration** rather than scattering transition logic across dozens of classes.
- **Workflow Engines (AWS Step Functions, Temporal):** at the macro-architecture level, distributed sagas and workflows are essentially massive, highly durable state machines managing the transitions of microservices.

## Field Notes

> [!note] Enum transition table vs State pattern
> When states mostly differ in *which moves are allowed* (not in behaviour), an enum with a transition table is lighter than a class per state. The trade-off and the concurrency angle (compare-and-set on the status) are in [[LLD/Foundations/04 - Enums and State Machines]].

## Principles Served

- [[02 - Open-Close Principle]] — a new state is a new class; existing states and the context's delegation don't change.
- [[01 - Single Responsibility Principle]] — each state class owns the behaviour and outgoing transitions for exactly one state.

## Sources

- [Refactoring.guru — State](https://refactoring.guru/design-patterns/state) (external, primary reference)
