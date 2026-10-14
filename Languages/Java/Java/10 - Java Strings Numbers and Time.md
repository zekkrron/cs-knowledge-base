---
tags: [languages/java, status/draft]
created: 2026-10-14
---
# Java Strings Numbers and Time

> [!abstract] What this is
> The everyday value types that cause the most production bugs: **`String`** internals (immutability, compact strings, the string pool, interning, concatenation), **numbers** (overflow, floating point, **`BigDecimal`**, boxing and the `Integer` cache), and **`java.time`** (instants vs local times, zones and DST, `Duration` vs `Period`, formatting, testable clocks). The decision this helps with: picking the right type for money and time, and spotting equality and rounding traps in review.

---

## Why It Matters

- Money stored in `double`, `==` on boxed `Integer`s, `BigDecimal.equals` failing on `2.0` vs `2.00`, timestamps stored in local time, `YYYY` in a date format: each one has caused real incidents and each is a classic interview trap.
- Strings are often the largest share of a Java heap; their internals explain memory usage and the cost of string building.

---

## Strings

### Internals

- **Immutable** and `final`: safe to share across threads, usable as map keys, `hashCode` computed once and cached.
- **Compact strings** (9): the backing `byte[]` uses **Latin-1 (1 byte per char)** when possible and UTF-16 (2 bytes) otherwise, with a `coder` flag. Roughly halves memory for mostly-ASCII data.
- `length()` counts UTF-16 **code units**, not characters: an emoji is 2. Use `codePoints()` / `codePointCount` for user-visible characters.
- **Default charset is UTF-8** since **18** (JEP 400). Before that, `new String(bytes)` and `FileReader` used the platform charset; still pass a `Charset` explicitly.

### The string pool and interning

```java
String a = "order";                 // literal: interned in the string pool
String b = "order";                 // same instance as a
String c = new String("order");     // new object on the heap
a == b;          // true  (same pooled instance)
a == c;          // false (different objects)
a.equals(c);     // true  — always use equals
c.intern() == a; // true
```

- The pool lives in the **heap** (since 7), so pooled strings are GC'd when unreachable.
- Manual `intern()` to save memory is rarely worth it now; **G1 string deduplication** (`-XX:+UseStringDeduplication`, also supported by other collectors since 18) shares identical backing arrays automatically.

### Building strings

| Approach | Use |
|---|---|
| `a + b + c` in one expression | Fine: compiled to `invokedynamic` (`StringConcatFactory`, 9), efficient |
| `+=` inside a loop | Avoid: creates a new string each iteration, O(n²) |
| `StringBuilder` | Loops and incremental building; not thread-safe (`StringBuffer` is, and is almost never needed) |
| `String.join`, `Collectors.joining` | Delimited lists |
| `"%s".formatted(x)` / `String.format` | Readability; slower; **never for SQL or shell commands** |
| Text blocks (15) | Multi-line literals ([[06 - Modern Java Language Features]]) |

- `substring` copies (since 7u6), so it no longer leaks the parent array.
- `split` takes a **regex**: `"a.b".split(".")` returns an empty array. Use `Pattern.quote` or precompile a `Pattern` for hot paths.

---

## Numbers

### Integer overflow

- `int` and `long` overflow **silently** wrap around: `Integer.MAX_VALUE + 1 == Integer.MIN_VALUE`.
- Use `Math.addExact`, `multiplyExact`, `toIntExact` where overflow must be an error (quantities, money in cents, array sizes).
- `Math.abs(Integer.MIN_VALUE)` is still negative. `(a + b) / 2` overflows for large values; use `(a + b) >>> 1` or `a + (b - a) / 2` (binary search bug).
- `hashCode() % n` can be negative: use `Math.floorMod`.

### Floating point

- `double` is binary floating point: `0.1 + 0.2 != 0.3`. Never for money or anything that must be exact.
- Compare with a tolerance; `NaN != NaN` (use `Double.isNaN`); `0.0 == -0.0` but `Double.equals` distinguishes them.

### BigDecimal

```java
new BigDecimal(0.1);         // 0.1000000000000000055511151231257827... (exact binary value)
BigDecimal.valueOf(0.1);     // 0.1  (uses Double.toString)
new BigDecimal("0.10");      // 0.10 — best: from strings or long cents

new BigDecimal("2.0").equals(new BigDecimal("2.00"));    // false: scale differs
new BigDecimal("2.0").compareTo(new BigDecimal("2.00")); // 0: numerically equal

BigDecimal total = price.multiply(qty).setScale(2, RoundingMode.HALF_EVEN);
BigDecimal share = total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_EVEN); // must give scale+mode
```

- `equals`/`hashCode` include **scale**: `BigDecimal` keys in a `HashMap`/`HashSet` behave surprisingly. Normalise with `stripTrailingZeros()` or compare with `compareTo`.
- `divide` without a scale and rounding mode throws `ArithmeticException` for non-terminating results (1/3).
- **`RoundingMode.HALF_EVEN`** (banker's rounding) is the usual choice for money; follow the domain's rule.
- Alternatives for money: `long` minor units (cents), or a `Money` value object wrapping `BigDecimal` + `Currency` ([[LLD/Foundations/02 - Immutability and Value Objects]]). Store as `NUMERIC(p,s)` in the database.

### Boxing traps

| Trap | Example | Why |
|---|---|---|
| `==` on boxed values | `Integer a = 127, b = 127; a == b` is `true`, but with `128` it's `false` | `Integer.valueOf` caches **−128..127**; outside that, distinct objects |
| Unboxing `null` | `int n = map.get(key);` → `NullPointerException` | Auto-unboxing calls `intValue()` on null |
| Ternary unboxing | `Integer x = flag ? count : null;` can NPE if `count` is `int` | Mixed types unbox the `null` |
| `List<Integer>.remove(1)` | Removes index 1 | Overload chooses `remove(int)` |
| Boxing in hot loops | `Long sum = 0L; for (...) sum += x;` | Allocates a `Long` per iteration |

---

## Time: java.time (8)

### Choose the right type

| Type | Represents | Pick when |
|---|---|---|
| **`Instant`** | A point on the UTC timeline (nanoseconds since epoch) | **Timestamps**: events, created/updated at, logs, storage |
| **`OffsetDateTime`** | Date-time with a fixed UTC offset | API payloads (ISO-8601 with offset), DB `timestamptz` mapping |
| **`ZonedDateTime`** | Date-time in a region zone (`Europe/Berlin`) with DST rules | Scheduling in a user's zone, "every day at 09:00 local" |
| **`LocalDateTime`** | Date-time **without** zone | Rarely: wall-clock values whose zone is stored separately |
| **`LocalDate`** / `LocalTime` | Calendar date / time of day | Birthdays, business dates, opening hours |
| **`Duration`** | Exact seconds/nanos | Timeouts, elapsed time |
| **`Period`** | Years/months/days | Calendar arithmetic ("one month later") |

- Rule: **store and compute in `Instant` (UTC)**, convert to a zone only at the edges (display, user input, schedules).
- `java.time` types are **immutable and thread-safe**; `DateTimeFormatter` too. The old `SimpleDateFormat` is **not thread-safe**; a shared static instance corrupts dates under load.

### DST and calendar traps

```java
ZoneId berlin = ZoneId.of("Europe/Berlin");
ZonedDateTime before = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, berlin);

before.plus(Duration.ofDays(1));  // 2026-03-29T13:00 — exactly 24h later (DST skipped an hour)
before.plus(Period.ofDays(1));    // 2026-03-29T12:00 — same wall-clock time next day
```

- Some local times **don't exist** (spring forward) and some happen **twice** (fall back). `ZonedDateTime` resolves gaps by shifting forward and overlaps to the earlier offset by default; `withLaterOffsetAtOverlap()` picks the other.
- Jobs scheduled "at 02:30 local" may skip or run twice on DST days; schedule in UTC or handle explicitly.
- `plusMonths(1)` on Jan 31 gives Feb 28/29 (clamped).
- Zone rules change (governments change DST); the JDK ships a tz database updated in JDK updates. Keep JDKs patched.
- Pattern trap: **`YYYY`** is week-based year; `yyyy` (or `uuuu`) is the calendar year. `YYYY-MM-dd` prints the wrong year around New Year. `hh` is 12-hour, `HH` is 24-hour.

### Testable time

```java
class TokenService {
    private final Clock clock;
    TokenService(Clock clock) { this.clock = clock; }
    boolean expired(Token t) { return Instant.now(clock).isAfter(t.expiresAt()); }
}
// test: new TokenService(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
```

- Inject a `Clock` instead of calling `Instant.now()` directly ([[LLD/Foundations/09 - Testability]]).
- Measure elapsed time with `System.nanoTime()` (monotonic), not `currentTimeMillis()` (wall clock jumps with NTP).

### Persistence

- Map timestamps to `timestamptz` (Postgres) as `Instant`/`OffsetDateTime`. A `timestamp without time zone` + `LocalDateTime` + servers in different zones = shifted data.
- Set the JVM and DB session zone explicitly (`-Duser.timezone=UTC`, `hibernate.jdbc.time_zone=UTC`) so defaults don't vary by host.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **`==` on strings or boxed numbers** | Works in tests (pooled/cached), fails in prod | `equals`; primitives for arithmetic |
| **Money in `double`** | Rounding errors in totals | `BigDecimal` from strings, or `long` cents |
| **`new BigDecimal(double)`** | Long binary fractions | `BigDecimal.valueOf` or string constructor |
| **`BigDecimal.equals` with different scales** | Duplicate keys, failed comparisons | `compareTo`, or normalise scale |
| **Division without rounding** | `ArithmeticException` | Always pass scale and `RoundingMode` |
| **Silent int overflow** | Negative totals, wrong sizes | `Math.*Exact`, `long` |
| **Local time stored as timestamp** | Shifted or ambiguous data across zones and DST | `Instant` + `timestamptz`; zone only at the edges |
| **Shared `SimpleDateFormat`** | Corrupted dates under concurrency | `DateTimeFormatter` |
| **`YYYY` in patterns** | Wrong year in late December | `yyyy` / `uuuu` |
| **`Instant.now()` hard-coded** | Untestable time logic | Inject `Clock` |
| **Platform default charset** | Mojibake on some hosts (pre-18) | Explicit `UTF_8` everywhere |

---

## Interview Lines

> [!tip] Say it like this
> - "Strings are immutable with a cached hash; since Java 9 they store Latin-1 text in one byte per char, and literals are pooled, which is why `==` sometimes works and why you must use `equals`."
> - "`Integer.valueOf` caches −128 to 127, so `==` on boxed integers passes small tests and fails in production."
> - "For money I use `BigDecimal` built from strings or `long` cents, always divide with a scale and rounding mode, and compare with `compareTo` because `equals` includes scale."
> - "I store `Instant`s in UTC and convert to a `ZoneId` only for display and scheduling; `Duration` is exact time, `Period` is calendar time, and they differ across DST."
> - "I inject a `Clock` so time-dependent code is testable."

## Related Notes

- [[06 - Modern Java Language Features]] · [[07 - Java Collections Internals]] · [[03 - JVM Memory Layout]]
- [[LLD/Foundations/02 - Immutability and Value Objects]] · [[LLD/Foundations/09 - Testability]] · [[Languages/README]]
