---
tags: [lld/patterns/behavioural, status/draft]
created: 2026-10-11
---
# Observer Pattern

> [!abstract] Define a one-to-many dependency so that when one object (the subject) changes, all its dependents (the observers) are notified automatically — a loosely coupled publish/subscribe mechanism where the publisher never knows who is listening.

**External references:**
- [Refactoring.guru — Observer](https://refactoring.guru/design-patterns/observer)
- [Refactoring.guru — Observer in Java](https://refactoring.guru/design-patterns/observer/java/example)

## Part 1 — Core Framework

### Main Purpose

To define a **one-to-many dependency** between objects so that when one object (the **Subject**) changes state, all its dependents (the **Observers**) are **notified and updated automatically**. It establishes a **loosely coupled publish/subscribe** mechanism.

### Recognition Signal

> [!tip] The cue
> - A change to one object requires changing an **unknown or dynamic number of other objects**.
> - You want to **avoid tightly coupling the core class to its side-effects** (e.g. an Order Service shouldn't have hardcoded references to Email, SMS, and Logging services).
> - You are building an **event-driven system** where components need to react to triggers asynchronously or synchronously.

### How to Implement

1. Define an **observer interface** with an `update(...)` method carrying the event data.
2. Write **concrete observers**, one per reaction (email, inventory, invoice).
3. Give the **subject** a list of observers with `attach()` / `detach()` and a `notifyObservers()` that loops over them.
4. In the subject's business method, **change state, then notify**.
5. The **client** wires observers to the subject at runtime.

### Key Code

You are building an E-commerce Order Fulfillment system. When an order's status changes to "SHIPPED", the system needs to send an email to the user, update the inventory counts, and generate an invoice. Instead of the `OrderTracker` calling all three directly, it simply **broadcasts an event**.

```java
// 1. The Observer Interface (The Subscribers)
public interface OrderObserver {
    void update(String orderId, String status);
}

// 2. Concrete Observers
public class EmailNotifier implements OrderObserver {
    public void update(String orderId, String status) {
        System.out.println("Email Sent: Order " + orderId + " is now " + status);
    }
}

public class InventoryUpdater implements OrderObserver {
    public void update(String orderId, String status) {
        System.out.println("Inventory Updated for Order: " + orderId);
    }
}

// 3. The Subject Interface (The Publisher)
public interface Subject {
    void attach(OrderObserver observer);
    void detach(OrderObserver observer);
    void notifyObservers();
}

// 4. The Concrete Subject
public class OrderTracker implements Subject {
    private List<OrderObserver> observers = new ArrayList<>();
    private String currentOrderId;
    private String currentStatus;

    public void attach(OrderObserver observer) { observers.add(observer); }
    public void detach(OrderObserver observer) { observers.remove(observer); }

    public void notifyObservers() {
        for (OrderObserver obs : observers) {
            obs.update(currentOrderId, currentStatus);
        }
    }

    // Core Business Logic
    public void updateOrderStatus(String orderId, String status) {
        this.currentOrderId = orderId;
        this.currentStatus = status;
        notifyObservers(); // Broadcast the change!
    }
}

// 5. Client Assembly
public class Application {
    public static void main(String[] args) {
        OrderTracker tracker = new OrderTracker();

        // Dynamic subscription at runtime
        tracker.attach(new EmailNotifier());
        tracker.attach(new InventoryUpdater());

        // The tracker doesn't know WHO is listening, it just broadcasts.
        tracker.updateOrderStatus("ORD-999", "SHIPPED");
    }
}
```

### Beyond the Basics

The greatest architectural win here is the **[[02 - Open-Close Principle]]**. If the marketing team asks you to add an "SMS Notification" feature next month, you **do not touch the `OrderTracker` code at all**. You just write a new `SmsNotifier implements OrderObserver` and attach it.

### Anti-Patterns & When NOT to Use

- **The Lapsed Listener Problem (Memory Leak).** If an observer has a **shorter lifecycle than the subject**, but forgets to call `detach()` before it dies, the subject will hold a reference to it forever. In Java, this prevents the Garbage Collector from cleaning it up, causing massive memory leaks in long-running backends.
- **Cascading Updates (Infinite Loops).** If Observer A reacts to a change by updating Subject B, and Observer B reacts by updating Subject A, you can trigger a catastrophic **infinite loop** that crashes the application.

## Part 2 — Architecture Deep Dive

### Advanced Nuances — Push vs Pull & Synchronous vs Asynchronous

In system design interviews, be prepared to discuss the two distinct mechanics of this pattern:

- **Push vs Pull.** In the **"Push"** model (used in our code example), the Subject sends the **exact data** (order ID, status) directly to the Observer. In the **"Pull"** model, the Subject just says **"I changed!"** and passes a **reference to itself**. The Observer must then query the Subject (`subject.getState()`) to pull the specific data it needs.
- **Sync vs Async.** The classic GoF Observer is **completely synchronous**. The Subject's thread **freezes** while it calls `update()` on every single listener in order. If the `EmailNotifier` takes 5 seconds to send an email, the `InventoryUpdater` is blocked. In modern backend design, we almost always execute the `update()` notifications **asynchronously** (e.g. throwing them onto a background thread pool or message queue) to keep the Subject's primary transaction extremely fast.

### Advanced Nuances — Extracting Subscriptions into an Event Manager

Refactoring.guru's Java example takes the subscription bookkeeping **out of the publisher entirely**. Instead of every publisher implementing `attach` / `detach` / `notify` itself (or inheriting them from a base `Subject` class), a separate **event manager** class owns a **map from event type to the list of subscribers** for that type. A publisher simply **holds an event manager as a field** (aggregation / composition) and calls `notify(eventType, data)` when something happens.

- **No publisher interface or base class needed:** any class becomes a publisher by holding an `EventManager`, so publishers can keep extending whatever they already extend.
- **Subscribe per event type:** a listener can sign up for `"SHIPPED"` only, rather than receiving every change and filtering.
- **Reusable across publishers:** the same `EventManager` class (or even a single shared instance) can serve many different publishers.

```java
public interface EventListener {
    void update(String eventType, Object data);
}

// The subscription mechanism, extracted into its own class
public class EventManager {
    private final Map<String, List<EventListener>> listeners = new HashMap<>();

    public EventManager(String... eventTypes) {
        for (String type : eventTypes) listeners.put(type, new ArrayList<>());
    }

    public void subscribe(String eventType, EventListener listener) {
        listeners.get(eventType).add(listener);
    }

    public void unsubscribe(String eventType, EventListener listener) {
        listeners.get(eventType).remove(listener);
    }

    public void notify(String eventType, Object data) {
        for (EventListener l : listeners.get(eventType)) l.update(eventType, data);
    }
}

// A publisher that implements no Subject interface: it just HAS an event manager
public class OrderTracker {
    public final EventManager events = new EventManager("SHIPPED", "CANCELLED");

    public void ship(String orderId) {
        // ... business logic ...
        events.notify("SHIPPED", orderId);
    }
}

// Client
OrderTracker tracker = new OrderTracker();
tracker.events.subscribe("SHIPPED", new EmailNotifier());
tracker.events.subscribe("SHIPPED", new InventoryUpdater());
tracker.events.subscribe("CANCELLED", new RefundProcessor());
tracker.ship("ORD-999");
```

- Taken one step further, a **single shared** event manager that many publishers post to is an **event bus**, which is where Observer meets Mediator (below).

### Pattern Synergy

#### Observer + Mediator

While Observer is great for **1-to-Many broadcasting**, it gets chaotic if **everyone is observing everyone**: every component holds references to many others, and the web of subscriptions becomes impossible to follow. A **[[Mediator]]** is often used to **centralize complex communication networks**, acting as a **single Subject that everyone subscribes to** (an **Event Bus**).

Refactoring.guru draws the line between the two like this:

- **Different goals.** Mediator's goal is to **eliminate mutual dependencies** among a set of components: they all depend on **one** mediator instead of on each other. Observer's goal is to set up **dynamic, one-way connections** where some objects act as subordinates of others.
- **A popular Mediator implementation relies on Observer.** The **mediator plays the publisher**, and the **components act as subscribers** that subscribe to and unsubscribe from the mediator's events (and post their own events to it). Built this way, Mediator can look **very similar** to Observer.
- **But Mediator doesn't have to use Observer.** You can also **permanently link** every component to the same mediator object, with the mediator calling components directly. That's still Mediator, with no subscription mechanism at all.
- **The reverse picture:** a program where **every component becomes a publisher**, allowing dynamic connections between each other, has **no centralised mediator**, only a **distributed set of observers**.

```java
// Mediator as the single publisher everyone talks through (an event bus)
public class EventBus {
    private final Map<String, List<Consumer<Object>>> handlers = new HashMap<>();

    public void subscribe(String topic, Consumer<Object> handler) {
        handlers.computeIfAbsent(topic, t -> new ArrayList<>()).add(handler);
    }
    public void publish(String topic, Object payload) {
        handlers.getOrDefault(topic, List.of()).forEach(h -> h.accept(payload));
    }
}

// Components know only the bus, never each other
bus.subscribe("ORDER_SHIPPED", id -> emailService.sendShippedMail((String) id));
bus.subscribe("ORDER_SHIPPED", id -> inventory.release((String) id));
bus.subscribe("PAYMENT_FAILED", id -> orderService.cancel((String) id));
orderService.onShip(id -> bus.publish("ORDER_SHIPPED", id));
```

| | Many-to-many Observer | Mediator / Event Bus |
|---|---|---|
| Who knows whom | Each component subscribes directly to many others | Every component knows **only the mediator** |
| Adding a component | Wire it to every relevant publisher | Subscribe it to the mediator's topics |
| Tracing the flow | Scattered across all subscriptions | Centralised in one place |
| Risk | Spaghetti dependencies, cascading loops | The mediator becomes a "god object" if it absorbs business logic |

#### Observer + State

When an object's internal **[[State]]** changes, it often uses the Observer pattern to **notify the rest of the application** about its new phase (e.g. "Order changed to Shipped!").

### Real-World Context

This pattern is the foundational concept for all **Event-Driven Architecture (EDA)**:

- **Distributed Pub/Sub (Kafka, RabbitMQ, AWS SNS):** these are essentially the Observer pattern **scaled out across physical servers**. A microservice publishes an event to a Kafka topic (the Subject), and multiple independent consumer microservices (the Observers) read and react to it.
- **Spring Application Events:** in Spring Boot, you rarely write the `attach()`/`detach()` logic. You use an `ApplicationEventPublisher` to broadcast events, and annotate listener methods with `@EventListener`. Spring acts as the **invisible Subject** managing the subscriptions (effectively the event-manager / event-bus idea above).

## Field Notes

> [!note] Thread safety of the observer list
> If observers can attach or detach while another thread is notifying, a plain `ArrayList` throws `ConcurrentModificationException` or misses updates. `CopyOnWriteArrayList` is the standard fix (cheap iteration, rare writes), and observers should be called **outside** any lock the subject holds. See [[LLD/Concurrency/07 - Concurrent Collections]] and [[LLD/Concurrency/13 - Designing Thread-Safe Classes]].

## Principles Served

- [[02 - Open-Close Principle]] — new reactions are new observer classes; the subject never changes.
- [[01 - Single Responsibility Principle]] — the subject owns its business state; each observer owns one side-effect.

## Sources

- [Refactoring.guru — Observer](https://refactoring.guru/design-patterns/observer) (external, primary reference)
