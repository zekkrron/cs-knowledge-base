---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Types Interfaces and Embedding

> [!abstract] What this is
> Go's type system for people coming from Java or Python: named types and underlying types, **implicitly satisfied interfaces** and how they're represented (**itab + data pointer**), the **nil interface trap** (a typed nil pointer in an interface is not `nil`), type assertions and switches, **method sets** and **value vs pointer receivers**, **struct embedding** (composition and method promotion, not inheritance), zero values, and design guidance ("accept interfaces, return structs", small interfaces defined by the consumer). The decision this helps with: designing Go APIs idiomatically and avoiding the classic interface bugs.

---

## Why It Matters

- The nil-interface trap (`err != nil` for a function that "returned nil") is one of the most famous Go bugs and a standard interview question.
- Receiver choice decides which types satisfy an interface and whether mutations are visible; embedding decides which methods a type exposes.

---

## Types Basics

```go
type UserID int64              // new named type: distinct from int64, no implicit conversion
type Celsius float64
func (c Celsius) F() float64 { return float64(c)*9/5 + 32 }

type Alias = int64             // alias: the SAME type, just another name
```

- Defined types get their own method sets and require explicit conversion (`UserID(42)`), which prevents mixing IDs and counts.
- **Zero values** are meaningful: `0`, `""`, `nil` slices/maps/pointers/interfaces, structs of zero fields. Design types so the zero value is usable (`var mu sync.Mutex`, `var buf bytes.Buffer`).
- A `nil` map can be read but not written (panic); a `nil` slice can be appended to.

---

## Interfaces

```go
type Store interface {
    Get(ctx context.Context, id string) (Order, error)
}

type PostgresStore struct{ db *sql.DB }
func (s *PostgresStore) Get(ctx context.Context, id string) (Order, error) { ... }

var _ Store = (*PostgresStore)(nil)   // compile-time check that *PostgresStore implements Store
```

- **Implicit satisfaction**: a type implements an interface by having the methods. No `implements` keyword. Interfaces can be declared **where they're used** (the consumer), after the fact, even for third-party types: structural typing, like Python's `Protocol` ([[09 - Python Typing]]).
- **Small interfaces**: `io.Reader`, `io.Writer`, `fmt.Stringer`, `error`. "The bigger the interface, the weaker the abstraction."
- **Accept interfaces, return concrete types**: functions take the minimal interface they need; constructors return structs so callers get the full API and you can add methods without breaking anyone.
- Don't create an interface for every struct "for mocking"; define it in the consumer package when a second implementation or a test fake actually needs it.

### Representation

```
interface value (two words)
+------------------+------------------+
| itab / type ptr  | data pointer     |
+------------------+------------------+
  dynamic type +       pointer to the value
  method table         (a copy for non-pointer values, often heap-allocated)
```

- Non-empty interfaces store an **itab** (dynamic type + method pointers); `any` (empty interface) stores just the type pointer.
- Calls through interfaces are **dynamic dispatch** (indirect call, usually not inlined); PGO can devirtualise hot call sites.
- Storing a non-pointer value in an interface copies it, frequently onto the heap ([[02 - Go Memory and Escape Analysis]]).

### The nil interface trap

```go
type MyErr struct{}
func (*MyErr) Error() string { return "boom" }

func find() error {
    var e *MyErr = nil
    if somethingFailed {
        e = &MyErr{}
    }
    return e              // BUG: returns a non-nil interface holding (type *MyErr, value nil)
}

if err := find(); err != nil {   // always true
    log.Println("failed:", err)
}
```

- An interface is `nil` only if **both** words are nil. A nil pointer of a concrete type stored in an interface has a non-nil type word, so `!= nil` is true.
- Fix: return the interface type directly (`return nil` on success); never declare `var e *MyErr` and return it as `error`.
- Same trap with any interface: a nil `*bytes.Buffer` passed as `io.Writer`.

### Type assertions and switches

```go
if s, ok := v.(fmt.Stringer); ok { ... }      // comma-ok form: no panic
s := v.(fmt.Stringer)                          // panics if v doesn't implement it

switch x := v.(type) {
case nil:
case string:  use(x)
case error:   log(x)
default:
}
```

- For errors, prefer `errors.As` / `errors.AsType` (1.26) over type assertions, because errors are often wrapped ([[06 - Go Errors and Panics]]).

---

## Method Sets and Receivers

| | Value receiver `func (t T) M()` | Pointer receiver `func (t *T) M()` |
|---|---|---|
| Gets | A **copy** of the value | A pointer: can modify the original |
| Method set of `T` includes | Yes | **No** |
| Method set of `*T` includes | Yes | Yes |
| Callable on addressable `t T` | Yes | Yes (Go takes `&t` automatically) |
| `T` value satisfies an interface needing `M` | Yes | **No**: only `*T` does |

```go
type Counter struct{ n int }
func (c *Counter) Inc() { c.n++ }

type Incer interface{ Inc() }
var _ Incer = &Counter{}   // OK
var _ Incer = Counter{}    // compile error: Inc has a pointer receiver
```

- Use **pointer receivers** when the method mutates, the struct is large, or the type contains a mutex or other non-copyable state (`go vet` flags copied locks). Use **value receivers** for small immutable value types (`time.Time`, `Celsius`).
- Be **consistent** per type: mixing confuses method sets.
- Values stored in maps aren't addressable: `m[k].Inc()` doesn't compile with a pointer receiver; store pointers or reassign.

---

## Struct Embedding

```go
type Logger struct{ prefix string }
func (l *Logger) Logf(format string, args ...any) { ... }

type Service struct {
    *Logger                 // embedded: Logf is promoted to Service
    store Store
}

s := &Service{Logger: &Logger{prefix: "svc"}}
s.Logf("started")           // calls s.Logger.Logf — the receiver is the Logger, not the Service
```

- **Composition, not inheritance**: promoted methods run with the **embedded value** as receiver; there's no virtual dispatch back to the outer type ("overriding" a method in the outer type doesn't change calls made inside the embedded type).
- The outer type satisfies interfaces through promoted methods, which is how decorators are written: embed an interface and override one method.

```go
type countingStore struct {
    Store                   // embed the interface: all methods delegate
    gets atomic.Int64
}
func (c *countingStore) Get(ctx context.Context, id string) (Order, error) {
    c.gets.Add(1)
    return c.Store.Get(ctx, id)
}
```

- Traps: embedding exposes **all** promoted methods and fields as part of your API; embedding a `sync.Mutex` makes `Lock`/`Unlock` public methods of your type; name collisions at the same depth are ambiguous (compile error on use).
- Interfaces can embed interfaces (`io.ReadWriter`).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Typed nil in an interface** | `err != nil` true for "nil" errors | Return `nil` literally; return interface types from functions |
| **Pointer-receiver methods, value stored in interface** | "does not implement" compile errors | Store `&T{}`; consistent receivers |
| **Copying structs with mutexes** | Copied lock state, races | Pointer receivers; `go vet` copylocks |
| **Huge provider-side interfaces** | Hard to fake, tight coupling | Small consumer-defined interfaces |
| **Embedding to "inherit"** | Surprising receivers, leaked API | Explicit fields and delegation; embed deliberately |
| **Unchecked type assertion** | Panics on unexpected types | Comma-ok form or type switch |
| **Interfaces on hot paths** | Allocations and dynamic dispatch | Concrete types or generics where it matters ([[09 - Go Generics]]) |

---

## Interview Lines

> [!tip] Say it like this
> - "Interfaces are satisfied implicitly, so I define small interfaces in the package that consumes them and return concrete structs from constructors."
> - "An interface value is a type word plus a data pointer; it's nil only when both are nil, so returning a nil `*MyErr` as `error` gives a non-nil error."
> - "Pointer-receiver methods belong to `*T`'s method set only, so a `T` value doesn't satisfy an interface that needs them."
> - "Embedding promotes methods but the receiver stays the embedded value; it's composition, not inheritance, and it's handy for decorators that override one method."
> - "I make zero values useful, like `sync.Mutex` and `bytes.Buffer`, so types work without constructors."

## Related Notes

- [[06 - Go Errors and Panics]] · [[09 - Go Generics]] · [[02 - Go Memory and Escape Analysis]] · [[05 - Go Slices Maps and Strings Internals]]
- [[09 - Python Typing]] · [[LLD/Foundations/03 - Object Modelling]] · [[LLD/Patterns/Structural/Decorator]] · [[Languages/README]]
