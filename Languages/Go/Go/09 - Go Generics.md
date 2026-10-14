---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Generics

> [!abstract] What this is
> Go's type parameters (since **1.18**): generic functions and types, **constraints** (interfaces as type sets, `any`, `comparable`, `cmp.Ordered`, `~T` approximation), type inference, how generics are **implemented** (GC-shape stenciling with dictionaries) and what that means for performance, the later additions (generic type aliases 1.24, self-referential constraints 1.26, **generic methods 1.27**), iterators as a generics use case, and **when not to use generics**. The decision this helps with: using generics where they remove duplication without making Go code harder to read.

---

## Why It Matters

- Before 1.18, Go code chose between duplicating functions per type and using `interface{}` with type assertions. Generics fixed that for containers and algorithms, and the standard library now relies on them (`slices`, `maps`, `cmp`, `sync/atomic.Pointer[T]`, `iter`).
- Interviewers probe the limits: no generic methods until 1.27, interface methods still can't be generic, and generics aren't always faster than interfaces.

---

## Basics

```go
// Generic function with a constraint
func Map[T, U any](xs []T, f func(T) U) []U {
    out := make([]U, 0, len(xs))
    for _, x := range xs {
        out = append(out, f(x))
    }
    return out
}

// Generic type
type Set[T comparable] struct{ m map[T]struct{} }

func NewSet[T comparable](items ...T) *Set[T] {
    s := &Set[T]{m: make(map[T]struct{}, len(items))}
    for _, it := range items {
        s.m[it] = struct{}{}
    }
    return s
}
func (s *Set[T]) Has(v T) bool { _, ok := s.m[v]; return ok }

names := Map(users, func(u User) string { return u.Name })   // T, U inferred
ids := NewSet[int64]()                                        // explicit when nothing to infer from
```

- **Type inference** works from function arguments (and, since 1.21, more contexts; 1.27 generalises inference when assigning generic functions to function-typed variables). Explicit instantiation `F[int]` when it can't infer.

---

## Constraints

```go
type Number interface {
    ~int | ~int32 | ~int64 | ~float32 | ~float64     // type set: these types or types whose underlying type is one
}

func Sum[T Number](xs []T) T {
    var total T
    for _, x := range xs {
        total += x                                    // + is allowed because every type in the set supports it
    }
    return total
}

type Celsius float64
Sum([]Celsius{20.5, 21})                              // OK thanks to ~float64
```

| Constraint | Allows |
|---|---|
| `any` | Anything; only operations valid for all types (assignment, passing, `==` not allowed) |
| `comparable` | `==` / `!=`; usable as map keys |
| `cmp.Ordered` (1.21) | `<`, `>`, etc. on integers, floats, strings |
| Interfaces with methods | Calling those methods: `[T fmt.Stringer]` |
| Union type sets (`~int \| ~string`) | Operators common to all members |

- Interfaces with type sets (unions, `~T`) can **only** be used as constraints, not as ordinary variable types.
- **Self-referential constraints** (1.26): `type Adder[A Adder[A]] interface{ Add(A) A }` lets a constraint require a method that takes and returns the type itself.

---

## Generic Methods (1.27)

```go
type Rand struct{ ... }

// 1.27: a method can declare its own type parameters
func (r *Rand) N[Int intType](n Int) Int { ... }

type Cache[K comparable, V any] struct{ ... }
func (c *Cache[K, V]) GetAs[T any](k K) (T, bool) { ... }   // method-level T in addition to K, V
```

- Before 1.27, methods could only use the **receiver type's** parameters; anything else needed a package-level function (`slices.Map`-style helpers). Now generic functionality can live in a type's namespace (`math/rand/v2`'s `(*Rand).N`).
- Limits: **interface methods can't have type parameters**, and **generic methods don't implement interface methods**. Dynamic dispatch over generic methods isn't supported.

---

## How It's Implemented

- Go uses **GC-shape stenciling with dictionaries**: one compiled copy of a generic function per **GC shape** (types with the same underlying memory/pointer layout share code; all pointer types share one shape), plus a hidden **dictionary** argument carrying type information and method pointers.
- Consequences:
  - Fast compile times and modest binary growth compared with full monomorphisation (C++/Rust).
  - Calls to **methods of type parameters** go through the dictionary, an indirect call similar to an interface call, often not inlined. Generic code over pointer types (`[T Stringer]` with `*MyType`) may not be faster than plain interfaces.
  - Generics over **primitive types** (`int`, `float64`) usually get specialised code paths and are as fast as hand-written versions.
- Measure performance-critical generic code with benchmarks rather than assuming ([[11 - Go Testing and Benchmarks]]).

---

## Iterators: Generics in Practice

```go
// iter.Seq[V] is func(yield func(V) bool)
func Filter[V any](seq iter.Seq[V], keep func(V) bool) iter.Seq[V] {
    return func(yield func(V) bool) {
        for v := range seq {
            if keep(v) && !yield(v) {
                return                              // consumer stopped (break): stop producing
            }
        }
    }
}

for u := range Filter(slices.Values(users), func(u User) bool { return u.Active }) {
    fmt.Println(u.Name)
}
```

- Range-over-func (1.23) + generics give lazy, composable sequences (`slices.Values`, `maps.Keys`, `strings.SplitSeq`) without channels or goroutines ([[05 - Go Slices Maps and Strings Internals]]).
- Always honour `yield` returning `false`; ignoring it panics at runtime.

---

## When to Use Generics

| Use generics for | Prefer something else when |
|---|---|
| Containers and data structures (sets, caches, heaps, ring buffers, typed pools) | Behaviour varies per type: **interfaces** express it more clearly |
| Algorithms over slices/maps/channels (`Map`, `Filter`, `Chunk`, `Merge`) | Only one or two concrete types are used: write them directly |
| Type-safe wrappers (`atomic.Pointer[T]`, typed `sync.Pool` wrappers, result types) | The generic version is harder to read than the duplication it removes |
| Removing `interface{}` + type assertions from APIs | Reflection-driven code (encoding) where generics can't express the logic |

- Go style: "write code, don't design types". Start concrete; extract a generic function when the duplication is real.
- Avoid deep generic abstractions (generic repositories over generic services over generic handlers): they fight Go's explicitness and make errors unreadable.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Generics where an interface fits** | Harder code, no performance gain | Interfaces for behaviour, generics for containers/algorithms |
| **Expecting generic methods on interfaces** | Compile errors even on 1.27 | Package-level generic functions or concrete methods |
| **Assuming generics are always faster** | Dictionary-based calls on pointer shapes | Benchmark hot paths |
| **Over-constrained or `any` everywhere** | Either unusable or unsafe | Tight but sufficient constraints (`comparable`, `cmp.Ordered`) |
| **Ignoring `yield`'s return in iterators** | Runtime panic on `break` | Return when `yield` returns false |
| **Type-set interfaces used as variable types** | Compile error | They're constraints only |

---

## Interview Lines

> [!tip] Say it like this
> - "Constraints are interfaces describing type sets: `comparable` for `==`, `cmp.Ordered` for ordering, unions with `~` to include types with that underlying type."
> - "Go compiles one copy per GC shape and passes a dictionary, so compile times stay fast, but calling methods on a type parameter can cost about what an interface call does."
> - "1.27 added generic methods: methods can declare their own type parameters, but interface methods still can't, and generic methods don't satisfy interfaces."
> - "I use generics for containers and slice/map algorithms, interfaces for behaviour, and I start concrete and generalise only when duplication is real."
> - "Iterators from 1.23 are where generics show up most in everyday code: `iter.Seq` with `slices.Values`, `maps.Keys` and custom filters."

## Related Notes

- [[04 - Go Types Interfaces and Embedding]] · [[05 - Go Slices Maps and Strings Internals]] · [[00 - Go Version History]] · [[11 - Go Testing and Benchmarks]]
- [[LLD/Foundations/05 - Generics]] · [[09 - Python Typing]] · [[Languages/README]]
