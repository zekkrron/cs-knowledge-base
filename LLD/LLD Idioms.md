---
tags: [lld, lld/idioms, status/draft]
created: 2026-11-27
updated: 2026-11-27
---
# LLD Idioms

> [!abstract] What this is
> The small code shapes that keep coming back across LLD problems: a few lines each, below the level of a design pattern. Each entry gives the **signal** that calls for it, the **shape** to type, the **problems** that use it, the trap, and a **Depth** link to the pattern, foundations or concurrency note that explains why it works. Same idea as the [[Agentic Problem to Solution Map]]: the notes explain; this is the index for your hands.
>
> **Kept current by rule:** every new or changed LLD problem adds itself to the entries it uses and adds new entries for shapes it introduces ([[LLD/Problems/README|Problems README]] §2 step 5 and template §14).

> [!tip] Revision drill
> Cover the shape, read only the signal, type the shape from memory, then check. Each takes under two minutes.

## Index

| # | Idiom | Used in |
|---|---|---|
| 1 | [[#1. CAS claim; the loser tries the next candidate]] | Parking Lot E2 |
| 2 | [[#2. CAS loop over an immutable copy]] | Parking Lot E3 |
| 3 | [[#3. One atomic step per key with ConcurrentHashMap.compute]] | Rate Limiter V0, E3 |
| 4 | [[#4. Evict with computeIfPresent returning null]] | Rate Limiter E3 |
| 5 | [[#5. Exactly one winner from a concurrent remove or add]] | Parking Lot E2, E4 |
| 6 | [[#6. Guarded state machine with compare-and-set on the status]] | none yet |
| 7 | [[#7. Snapshot under the lock, publish outside it]] | none yet |
| 8 | [[#8. Injected Clock, read once per request]] | Parking Lot V0, Rate Limiter V0 |
| 9 | [[#9. Lazy expiry and refill from now, no timer thread]] | Rate Limiter V0, E1 |
| 10 | [[#10. Strategy picked by key from a map]] | Parking Lot E1, Rate Limiter E1 |
| 11 | [[#11. One-implementation interface where the first follow-up plugs in]] | Parking Lot V0, Rate Limiter V0 |
| 12 | [[#12. Rules table instead of subclasses]] | Parking Lot E3 |
| 13 | [[#13. State remembers its config; rebuild on mismatch]] | Rate Limiter E3 |
| 14 | [[#14. Secondary index kept by the service]] | Parking Lot E4 |
| 15 | [[#15. Idempotency-key map]] | none yet |
| 16 | [[#16. One-line guards and a three-class exception hierarchy]] | Parking Lot V0, Rate Limiter V0 |
| 17 | [[#17. Expected rejection is a result, not an exception]] | Rate Limiter V0, E4 |
| 18 | [[#18. Money as long paise; ceiling division for billable units]] | Parking Lot V0, E1 |
| 19 | [[#19. The service never prints; drivers format]] | Parking Lot V0, Rate Limiter V0 |

"None yet" entries come from the theory notes and are listed because the next problems on the [[Problem Catalog]] (booking, order and payment systems) are likely to need them.

---

## Concurrency

### 1. CAS claim; the loser tries the next candidate

- **Signal:** many threads compete for one of N interchangeable resources (slots, seats, workers), and each resource is held by at most one claimant.
- **Shape:**

```java
private final AtomicReference<Vehicle> holder = new AtomicReference<>();
boolean tryClaim(Vehicle v) { return holder.compareAndSet(null, v); }
boolean release(Vehicle v)  { return holder.compareAndSet(v, null); }

for (Slot s : strategy.candidates(...)) {      // ordered list, not one choice
    if (s.tryClaim(v)) return issue(s, v);     // one thread sees null
}                                              // lost this one: try the next
throw new ConflictException("Full");
```

- **Used in:** Parking Lot E2 (two gates, one slot).
- **Watch out:** the strategy must return a **list** of candidates; returning one slot means the loser fails instead of moving on. Undo any earlier reservation (the plate set) on the "full" path.
- **Depth:** [[06 - Atomics and CAS]] · [[02 - Race Conditions and Shared State]] (check-then-act)

### 2. CAS loop over an immutable copy

- **Signal:** the shared value is a small collection or record that changes as a whole (occupants of a shared slot, a set of holders) and must stay consistent with a capacity rule.
- **Shape:**

```java
private final AtomicReference<List<Vehicle>> occupants = new AtomicReference<>(List.of());

boolean tryOccupy(Vehicle v, int capacity) {
    while (true) {
        List<Vehicle> cur = occupants.get();
        if (cur.size() >= capacity || !compatible(cur, v)) return false;
        List<Vehicle> next = append(cur, v);              // copy, never mutate cur
        if (occupants.compareAndSet(cur, next)) return true;
    }                                                     // someone changed it: re-read
}
```

- **Used in:** Parking Lot E3 (up to 4 bikes in one car slot).
- **Watch out:** keep the list small and immutable (`List.copyOf`); for large or hot collections a lock is simpler and faster.
- **Depth:** [[06 - Atomics and CAS]] · [[12 - Confinement and Immutability]]

### 3. One atomic step per key with ConcurrentHashMap.compute

- **Signal:** per-key state (per client, per user, per account) with a read-modify-write that must not interleave for the same key, while different keys run in parallel.
- **Shape:**

```java
boolean[] out = new boolean[1];
map.compute(key, (k, state) -> {
    State s = state != null ? state : new State(configFor(k));   // create and use in one step
    out[0] = s.tryAcquire(now);
    return s;
});
return out[0];
```

- **Used in:** Rate Limiter V0 (create and count per client), E3 (policy swap inside the same step).
- **Watch out:** the remapping function must be short and touch **only its own key**; no I/O, no other map. The state object itself needs no locks, and its Javadoc should say "only touched inside `compute`".
- **Depth:** [[07 - Concurrent Collections]] · [[13 - Designing Thread-Safe Classes]]

### 4. Evict with computeIfPresent returning null

- **Signal:** a per-key map grows without bound (idle clients, expired sessions) and entries may be in use when the cleaner runs.
- **Shape:**

```java
long cutoff = now - idle.toMillis();
for (String id : map.keySet()) {
    map.computeIfPresent(id, (k, s) -> s.lastSeen <= cutoff ? null : s);   // null removes
}
```

- **Used in:** Rate Limiter E3 (idle eviction on a `ScheduledExecutorService`).
- **Watch out:** never `remove` after a separate `get` check; a request could touch the entry in between. Choose the idle time at least as long as the longest window.
- **Depth:** [[07 - Concurrent Collections]] · [[08 - Executors and Thread Pools]]

### 5. Exactly one winner from a concurrent remove or add

- **Signal:** "two exits present the same ticket", "the same plate at two gates", "double-click on pay": an operation must succeed once.
- **Shape:**

```java
Ticket t = activeTickets.remove(id);              // ConcurrentHashMap: one caller gets it
if (t == null) throw new NotFoundException("Invalid Ticket");

if (!inside.add(plate))                           // ConcurrentHashMap.newKeySet()
    throw new ConflictException("Vehicle already parked");
```

- **Used in:** Parking Lot E2 (one unpark per ticket, one entry per plate), E4 (plate index).
- **Watch out:** the atomic call **is** the check; don't call `containsKey` first. Remove the guard entry last on cleanup so a re-entry can't race it.
- **Depth:** [[07 - Concurrent Collections]] · [[02 - Race Conditions and Shared State]]

### 6. Guarded state machine with compare-and-set on the status

- **Signal:** an entity moves through states (order, booking, payment) and two events can arrive at once (cancel and ship, two cancels).
- **Shape:**

```java
enum Status { CREATED, PAID, SHIPPED, CANCELLED;
    boolean canGo(Status next) { return ALLOWED.get(this).contains(next); } }

private final AtomicReference<Status> status = new AtomicReference<>(Status.CREATED);

void transition(Status from, Status to) {
    if (!from.canGo(to) || !status.compareAndSet(from, to))
        throw new ConflictException(status.get() + " -> " + to + " not allowed");
}
```

- **Used in:** none yet. Expected in booking, order and payment problems.
- **Watch out:** the allowed-transition table is data (an `EnumMap`), not scattered `if`s; in a database the same check is `UPDATE … WHERE status = :from`.
- **Depth:** [[04 - Enums and State Machines]] · [[State]] · [[06 - Atomics and CAS]]

### 7. Snapshot under the lock, publish outside it

- **Signal:** a change must notify listeners, write a log or call another service, and the state is guarded by a lock.
- **Shape:**

```java
List<Listener> toNotify;
Event e;
synchronized (this) {
    e = applyChange();                 // only the state change in here
    toNotify = List.copyOf(listeners);
}
toNotify.forEach(l -> l.on(e));        // outside: no deadlock, no slow callee holding the lock
```

- **Used in:** none yet. Expected wherever Observer appears (notifications, pub-sub, order events).
- **Watch out:** listeners see events slightly after the change and possibly out of order across threads; say so, or deliver through a queue.
- **Depth:** [[13 - Designing Thread-Safe Classes]] · [[Observer]] · [[09 - Deadlock Livelock and Starvation]]

---

## Time

### 8. Injected Clock, read once per request

- **Signal:** anything time-based: fees, windows, expiry, holds, SLAs.
- **Shape:**

```java
public Service(..., Clock clock) { this.clock = clock; }   // prod: Clock.systemUTC()

Instant now = clock.instant();                             // once, at the top
limiter.tryAcquire(now);                                   // pass it down; inner code never calls the clock

final class ManualClock extends Clock {                    // tests and Demo
    private Instant now; void advance(Duration d) { now = now.plus(d); } ... }
```

- **Used in:** Parking Lot V0 (entry and exit times), Rate Limiter V0 (windows).
- **Watch out:** reading the clock twice in one request lets the two reads disagree across a boundary.
- **Depth:** [[09 - Testability]]

### 9. Lazy expiry and refill from now, no timer thread

- **Signal:** state that "resets every window", "refills over time" or "expires after T": per client or per item, possibly millions of them.
- **Shape:**

```java
long start = now - Math.floorMod(now, window);          // aligned window
if (start != windowStart) { windowStart = start; count = 0; }

tokens = Math.min(capacity, tokens + (now - lastRefill) * ratePerMilli);   // token bucket
lastRefill = now;
```

- **Used in:** Rate Limiter V0 (window reset), E1 (token bucket, sliding counter).
- **Watch out:** a timer per item doesn't scale; compute the state when it's next touched. Use a scheduler only for memory cleanup ([[#4. Evict with computeIfPresent returning null]]).
- **Depth:** [[19 - Classic Problems - Components]]

---

## Structure

### 10. Strategy picked by key from a map

- **Signal:** behaviour varies by a type or policy ("price per vehicle type", "algorithm per client"), and the follow-up will add more variants.
- **Shape:**

```java
private final Map<VehicleType, PricingStrategy> pricing;     // EnumMap when keyed by an enum

PricingStrategy p = pricing.get(vehicle.type());
if (p == null) throw new ValidationException("No price for " + vehicle.type());   // before any side effect
```

```java
static ClientLimiter create(LimitPolicy p, long now) {       // or: enum + factory switch
    return switch (p.algorithm()) {
        case FIXED_WINDOW -> new FixedWindowLimiter(p, now);
        case TOKEN_BUCKET -> new TokenBucketLimiter(p, now);
        ...
    };
}
```

- **Used in:** Parking Lot E1 (pricing per vehicle type), Rate Limiter E1 (algorithm per policy).
- **Watch out:** look the strategy up **before** claiming a slot or counting a request, so a missing entry never leaves half-done state. A new variant should be a class plus one map entry or one `case`.
- **Depth:** [[Strategy]] · [[Factory Pattern]] · [[04 - Enums and State Machines]] (`EnumMap`)

### 11. One-implementation interface where the first follow-up plugs in

- **Signal:** the research tally shows which follow-up comes first (pricing scheme, algorithm, allocation order).
- **Shape:** V0 ships `interface PricingStrategy` with only `HourlyPricing`, `interface ClientLimiter` with only `FixedWindowLimiter`. The service depends on the interface from day one.
- **Used in:** Parking Lot V0 (allocation, pricing), Rate Limiter V0 (limiter).
- **Watch out:** only for the follow-ups the evidence predicts; an interface for everything is noise. Say why it's there in one sentence.
- **Depth:** [[Strategy]] · [[07 - Dependency Injection and Layering]]

### 12. Rules table instead of subclasses

- **Signal:** compatibility or eligibility rules between two type families ("a bike may use a car slot", "4 bikes share a car slot when busy") that change by configuration.
- **Shape:**

```java
CompatibilityRules.defaults()
    .allow(VehicleType.BIKE, SlotType.BIKE, SlotType.CAR, SlotType.TRUCK)   // preference order
    .allow(VehicleType.CAR,  SlotType.CAR,  SlotType.TRUCK)
    .allow(VehicleType.TRUCK, SlotType.TRUCK);

int capacity(SlotType s, VehicleType v) { return sharing && s == CAR && v == BIKE ? 4 : 1; }
```

- **Used in:** Parking Lot E3.
- **Watch out:** split the two families into separate enums (`SlotType` next to `VehicleType`) before adding rules; a slot whose size *is* the vehicle type can't express fallback.
- **Depth:** [[03 - Object Modelling]] · [[Criteria]]

### 13. State remembers its config; rebuild on mismatch

- **Signal:** "change a client's limit while it has traffic", "update a plan live": config changes must apply without a lock across all clients.
- **Shape:**

```java
map.compute(id, (k, s) -> {
    LimitPolicy p = policies.getOrDefault(k, defaultPolicy);
    State cur = s != null && s.policy.equals(p) ? s : new State(p, factory.create(p, now));
    ...
});
```

- **Used in:** Rate Limiter E3.
- **Watch out:** a rebuild resets counters; say whether that errs strict or loose. A request that read the old policy just before the change counts on it once.
- **Depth:** [[02 - Immutability and Value Objects]] · [[#3. One atomic step per key with ConcurrentHashMap.compute]]

### 14. Secondary index kept by the service

- **Signal:** "find a car by registration", "list by colour", "lookup by email": a query by a field other than the primary id.
- **Shape:**

```java
private final Map<String, Ticket> byId = new ConcurrentHashMap<>();
private final Map<String, Ticket> byPlate = new ConcurrentHashMap<>();

// on add: primary first, then indexes.  on remove: indexes first, guard set last.
byId.put(t.id(), t);  byPlate.put(t.plate(), t);
...
byPlate.remove(t.plate());  inside.remove(t.plate());
```

- **Used in:** Parking Lot E4.
- **Watch out:** the orchestrator owns all indexes so they change together; filtered scans (by colour) are fine without an index at interview sizes, say the complexity.
- **Depth:** [[06 - Collections]] · [[13 - Designing Thread-Safe Classes]] (composing thread-safe pieces)

### 15. Idempotency-key map

- **Signal:** a retried request must not repeat a side effect: payments, bookings, transfers, "the client timed out and sent it again".
- **Shape:**

```java
private final ConcurrentHashMap<String, Result> done = new ConcurrentHashMap<>();

Result pay(String idempotencyKey, Request r) {
    return done.computeIfAbsent(idempotencyKey, k -> execute(r));   // second call returns the first result
}
```

- **Used in:** none yet. Expected in payment, wallet, booking and order problems.
- **Watch out:** the key comes from the caller (or the orchestrator), never generated per attempt; store the result, not just "seen"; a failed first attempt must be recorded so a retry doesn't run it twice in parallel.
- **Depth:** [[11 - Idempotency and Retries]] · [[07 - Concurrent Collections]]

---

## Errors, Values and Drivers

### 16. One-line guards and a three-class exception hierarchy

- **Signal:** every problem: bad input, missing ids, conflicts.
- **Shape:**

```java
public class DomainException extends RuntimeException { ... }
public class ValidationException extends DomainException {
    static void require(boolean ok, String msg) { if (!ok) throw new ValidationException(msg); } }
public class NotFoundException extends DomainException { ... }
public class ConflictException extends DomainException { ... }

ValidationException.require(floors > 0, "floors must be positive");
```

- **Used in:** Parking Lot V0, Rate Limiter V0 (validation only).
- **Watch out:** create only the subtypes the problem uses; in a 50-minute round, one `DomainException` and a sentence.
- **Depth:** [[01 - Exception Handling]]

### 17. Expected rejection is a result, not an exception

- **Signal:** the "no" answer is part of normal traffic: rate limited, out of stock, slot unavailable in a search.
- **Shape:**

```java
record Decision(boolean allowed, long remaining, Duration retryAfter) {
    static Decision allow(long remaining) { return new Decision(true, remaining, Duration.ZERO); } }
```

- **Used in:** Rate Limiter V0 (`boolean`), E4 (`Decision` with retry-after).
- **Watch out:** a request that can never succeed (cost above the whole limit) is invalid input, so it stays a `ValidationException`.
- **Depth:** [[08 - API and Method Design]] · [[01 - Exception Handling]]

### 18. Money as long paise; ceiling division for billable units

- **Signal:** fees, prices, balances, "per started hour".
- **Shape:**

```java
long feePaise = hours * 30_00;                                  // Rs 30.00
static long billableHours(Instant in, Instant out) {            // 0 min -> 1 h, 61 min -> 2 h
    long minutes = Math.max(0, Duration.between(in, out).toMinutes());
    return Math.max(1, (minutes + 59) / 60);
}
String shown = "Rs %d.%02d".formatted(p / 100, p % 100);         // only in the receipt or driver
```

- **Used in:** Parking Lot V0, E1.
- **Watch out:** never `double`; format only at the edge.
- **Depth:** [[02 - Immutability and Value Objects]]

### 19. The service never prints; drivers format

- **Signal:** every problem, and especially command-line prompts (workat.tech, Swiggy).
- **Shape:** the service takes typed arguments and returns what the output needs (`unpark` returns the `Receipt` with the vehicle). `Demo` (a `main`) or an optional `cli/` driver parses commands, calls the service and prints the prompt's strings; it catches `DomainException` and prints its message.
- **Used in:** Parking Lot V0 (Demo plus optional CLI), Rate Limiter V0 (Demo).
- **Watch out:** if a test needs the console to check behaviour, the boundary is wrong.
- **Depth:** [[07 - Dependency Injection and Layering]] · [[LLD/Problems/README|Problems README]] §3 driver rule

---

## Related Notes

- [[LLD/Problems/README|LLD Problems]] · [[Problem Catalog]] · [[LLD/Concurrency/README|Concurrency]] · [[LLD/Foundations/README|Foundations]] · [[Agentic Problem to Solution Map]]
