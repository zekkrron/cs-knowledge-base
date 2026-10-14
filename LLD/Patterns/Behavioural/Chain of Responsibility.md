---
tags: [lld/patterns/behavioural, status/draft]
created: 2026-10-11
---
# Chain of Responsibility Pattern

> [!abstract] Pass a request along a dynamic chain of handlers, where each handler decides to process it, process part of it and pass it on, or stop it entirely — decoupling the sender of a request from whoever ends up handling it.

**External references:**
- [Refactoring.guru — Chain of Responsibility](https://refactoring.guru/design-patterns/chain-of-responsibility)
- [Refactoring.guru — Chain of Responsibility in Java](https://refactoring.guru/design-patterns/chain-of-responsibility/java/example)

## Part 1 — Core Framework

### Main Purpose

To pass a request along a **dynamic chain** of potential handlers. Upon receiving a request, each handler decides whether to **process it fully**, **partially process it and pass it along**, or **completely halt the execution**. It decouples the sender of a request from its receiver: the client only talks to the first link and never knows which handler (or how many) did the work.

### Recognition Signal

> [!tip] The cue
> - Your application needs to process an incoming request through a **series of sequential checks or filters** (e.g. rate limiting, then authentication, then validation).
> - You want to execute multiple handlers in a **specific order**, but need the ability to **reorder, add, or remove handlers dynamically** at runtime.
> - **Multiple objects can handle a request**, but the exact handler is determined at runtime based on the request's payload.

### How to Implement

1. Define a **base handler** that holds a reference to the `next` handler, with a `setNext()` to link them and a `checkNext()` helper that forwards to `next` (or ends the chain).
2. Write **concrete handlers**, each doing one check or job. A handler either **halts** (returns / throws) or **passes on** by calling `checkNext()`.
3. The **client assembles the chain** once and sends every request to the **first** handler only.

### Key Code

You are designing the entry point for a secure API. Before the business logic is allowed to run, the request must pass through a Rate Limiter, an Authentication check, and a Role Authorization check. If any link in the chain fails, the request is immediately dropped.

```java
// 1. The Base Handler
public abstract class ApiHandler {
    private ApiHandler next;

    public ApiHandler setNext(ApiHandler next) {
        this.next = next;
        return next; // Returning next allows for method chaining!
    }

    protected boolean checkNext(String request) {
        if (next == null) {
            return true; // End of chain, successful traversal
        }
        return next.handle(request);
    }

    public abstract boolean handle(String request);
}

// 2. Concrete Handlers
public class RateLimitHandler extends ApiHandler {
    private int requestsPerMinute = 0;
    public boolean handle(String request) {
        if (requestsPerMinute > 100) {
            System.out.println("429 Too Many Requests");
            return false; // Halts the chain
        }
        requestsPerMinute++;
        System.out.println("Rate Limit Passed.");
        return checkNext(request); // Passes to next in chain
    }
}

public class AuthHandler extends ApiHandler {
    public boolean handle(String request) {
        if (!request.contains("BearerToken")) {
            System.out.println("401 Unauthorized");
            return false; // Halts the chain
        }
        System.out.println("Authentication Passed.");
        return checkNext(request);
    }
}

// 3. Client Configuration
public class ApiServer {
    public static void main(String[] args) {
        // Assembling the chain fluently
        ApiHandler pipeline = new RateLimitHandler();
        pipeline.setNext(new AuthHandler());

        // The client only talks to the first link in the chain
        System.out.println("Sending bad request...");
        pipeline.handle("GuestUserRequest");
        // Output: Rate Limit Passed. -> 401 Unauthorized

        System.out.println("\nSending good request...");
        pipeline.handle("AdminRequest with BearerToken");
        // Output: Rate Limit Passed. -> Authentication Passed.
    }
}
```

### Beyond the Basics

The design of the `setNext()` method is crucial. By having it **return the newly attached handler** (instead of `void`), you can construct the chain fluently: `handlerA.setNext(handlerB).setNext(handlerC)`. This makes the assembly highly readable and extensible.

### Anti-Patterns & When NOT to Use

- **The Unhandled Drop.** Unlike standard method calls, a request can reach the end of the chain **without ever being handled** by a concrete class. If this is not intended, it results in silent failures. Always ensure a **fallback or default handler** exists at the tail.
- **The Broken Link.** If a concrete handler forgets to call `checkNext()` after successfully doing its part, the chain is **prematurely severed**, and subsequent handlers are starved of the request.

## Part 2 — Architecture Deep Dive

### Pattern Synergy

#### Chain of Responsibility vs Decorator

Both allow you to link objects and pass requests through them. However, a **Decorator** typically **augments** the request and **always passes it down** the stack. A **Chain of Responsibility** has the power to **completely halt execution** (e.g. throwing a `401 Unauthorized` and stopping the chain). Full side-by-side code in [[Decorator]].

#### Chain of Responsibility + Composite

In UI frameworks (like the DOM), an event (like a mouse click) **bubbles up the component tree**. The [[Composite]] tree structures the objects, and the CoR dictates how the event propagates until a node handles it.

#### Chain of Responsibility + Command

Handlers in Chain of Responsibility can be implemented as **[[Command]]s**. In this case, you can execute a lot of **different operations over the same context object**, represented by a request.

However, there's another approach, where **the request itself is a Command** object. In this case, you can execute **the same operation in a series of different contexts** linked into a chain.

```java
// Approach 1 — handlers ARE commands: many different operations, one shared context (the request)
interface RequestCommand { boolean execute(HttpRequest ctx); }   // each handler = one command
List<RequestCommand> chain = List.of(new RateLimit(), new Authenticate(), new Authorize());
for (RequestCommand step : chain) {
    if (!step.execute(request)) break;          // different operations over the SAME request
}

// Approach 2 — the request IS a command: one operation, many contexts down the chain
interface Command { boolean runOn(Context ctx); }                // "approve expense ₹50,000"
abstract class Approver {
    private Approver next;
    private final Context ctx;                                   // this link's context (its limit, role)
    Approver(Context ctx) { this.ctx = ctx; }
    Approver setNext(Approver next) { this.next = next; return next; }
    void pass(Command cmd) {
        if (cmd.runOn(ctx)) return;                              // handled in this context
        if (next != null) next.pass(cmd);                        // same command, next context
    }
}
// team lead -> manager -> director: the SAME command tried in each context until one can run it
```

### Real-World Context

This is arguably the most common pattern for HTTP request handling in modern web frameworks:

- **Spring Security Filter Chain:** in Java Spring Boot, security is implemented as a massive Chain of Responsibility. A request passes through CORS filters, CSRF filters, Basic Auth filters, and JWT filters before it ever reaches your `@RestController`.
- **Express.js Middleware:** in Node.js ecosystems, the `app.use((req, res, next) => {...})` paradigm is a direct implementation of this pattern. Calling `next()` passes control to the next handler; failing to call it halts the request.

### Advanced Nuances — "Pure" CoR vs "Pipeline" (Filter Chain)

In a Low-Level Design interview, distinguishing between the original GoF definition and modern implementations is a massive differentiator.

- **The Pure GoF Chain:** in the original 1994 definition, a request travels down the chain until **exactly one handler** processes it, at which point the chain completely stops. It acts as a **router** looking for a single capable receiver.
- **The Modern Pipeline (Filter Chain):** in modern backend architecture (like Spring Filters or API Gateways), the request travels down the chain, and **every single handler does a piece of work** (logging, metrics, auth) and passes it along. It acts as an **assembly line**.

> [!tip] When discussing this in an interview, refer to the modern variant as the **"Filter Chain"** or **"Middleware Pipeline"** to show you understand how the pattern evolved to meet real-world web engineering needs.

## Principles Served

- [[02 - Open-Close Principle]] — add a new check by adding a handler and linking it in; existing handlers don't change.
- [[01 - Single Responsibility Principle]] — each handler owns exactly one concern (rate limiting, authentication, authorization).

## Sources

- [Refactoring.guru — Chain of Responsibility](https://refactoring.guru/design-patterns/chain-of-responsibility) (external, primary reference)
