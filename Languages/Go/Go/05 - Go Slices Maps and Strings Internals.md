---
tags: [languages/go, status/draft]
created: 2026-10-14
---
# Go Slices Maps and Strings Internals

> [!abstract] What this is
> How Go's built-in collections work underneath: the **slice header** (pointer, length, capacity), **`append` growth and aliasing** bugs, full slice expressions, retaining large backing arrays; **maps** (Swiss-table implementation since **1.24**, randomised iteration, not safe for concurrent writes, no shrinking, non-addressable values); and **strings vs `[]byte` vs runes** (immutable UTF-8 bytes, indexing returns bytes, `range` decodes runes, conversion costs). Also the `slices`, `maps` and `strings` helper packages and iterators. The decision this helps with: avoiding the aliasing, concurrency and Unicode bugs that show up in Go code review.

---

## Why It Matters

- `append` aliasing ("I appended to one slice and another changed") and concurrent map writes (`fatal error: concurrent map writes`, an unrecoverable crash) are among the most common Go production bugs.
- Strings are bytes, not characters: `len("héllo") == 6`, and indexing slices bytes.

---

## Slices

```
slice header (3 words)                backing array
+---------+-----+-----+               +---+---+---+---+---+---+---+---+
| ptr ----+ len | cap |  ──────────▶  | a | b | c | d |   |   |   |   |
+---------+-----+-----+               +---+---+---+---+---+---+---+---+
             4     8
```

- A slice is a **view**: pointer to a backing array, length, capacity. Passing a slice copies the header, not the data, so callees can modify elements the caller sees.
- `s[i:j]` creates a new header over the **same** array (cap extends to the end of the original array).

### append and growth

```go
s := make([]int, 0, 4)   // len 0, cap 4
s = append(s, 1, 2, 3)   // fits: same array
s = append(s, 4, 5)      // exceeds cap: allocates a new, larger array, copies, returns a header to it
```

- When capacity is exceeded, `append` allocates a bigger array: roughly **2×** for small slices, tapering towards **1.25×** for large ones (rounded to allocator size classes), and copies.
- **Always use the result**: `s = append(s, x)`. The old header may point to the old array.

### The aliasing bug

```go
base := make([]int, 3, 10)        // spare capacity
a := append(base, 1)              // writes base's array at index 3
b := append(base, 2)              // writes the SAME index 3
fmt.Println(a[3], b[3])           // 2 2 — a was overwritten

// Fix 1: full slice expression caps capacity, forcing a copy on append
a = append(base[:len(base):len(base)], 1)
// Fix 2: copy explicitly
a = append(slices.Clone(base), 1)
```

- Happens whenever two slices share a backing array with spare capacity: returning sub-slices of internal buffers, appending to a slice received as an argument, building variants from a common prefix.
- Functions that store or append to a caller's slice should **copy** (`slices.Clone`) or document ownership.

### Retaining big arrays

```go
func firstLine(data []byte) []byte {
    i := bytes.IndexByte(data, '\n')
    return data[:i]                     // keeps the WHOLE (maybe 100 MB) array alive
}
return bytes.Clone(data[:i])            // copy what you keep
```

### Nil vs empty

- `var s []int` (nil) and `[]int{}` (empty) both have `len 0` and work with `append`/`range`. They differ in `s == nil` and JSON encoding (`null` vs `[]`). Pick one convention for APIs.

---

## Maps

### Implementation

- Since **1.24**, maps use a **Swiss-table** design: entries in groups of 8 slots with a control word of hash fragments, probed with bitwise (SIMD-style) matching; tables split incrementally as they grow. Faster lookups and inserts than the old bucket-chaining design, especially for large maps.
- Hash function is seeded per map, and **iteration order is deliberately randomised**: never depend on it (sort keys: `slices.Sorted(maps.Keys(m))`).

### Rules and traps

| Rule | Consequence |
|---|---|
| **Not safe for concurrent writes** | Concurrent write + read/write → `fatal error: concurrent map writes` (crashes the process; `recover` can't catch it). Use `sync.Mutex`/`RWMutex` around the map, or `sync.Map` for specific patterns ([[07 - Go Concurrency Primitives]]) |
| **Writing to a nil map panics** | `var m map[string]int; m["a"] = 1` panics; `make` it first |
| **Values aren't addressable** | `m[k].Field = 1` doesn't compile; read-modify-write or store pointers |
| **Missing key returns the zero value** | Use `v, ok := m[k]` to distinguish |
| **Maps never shrink** | Deleting all entries keeps the memory; recreate the map for big transient maps (`clear(m)` empties but keeps capacity) |
| **Keys must be comparable** | No slices/maps/funcs as keys; structs of comparable fields work; floats with NaN are never found |
| **Deleting during `range` is allowed** | Entries added during iteration may or may not be visited |

- Pre-size when the count is known: `make(map[K]V, n)`.
- `map[string]struct{}` for sets (zero-size values).

---

## Strings, Bytes and Runes

| | `string` | `[]byte` | `rune` |
|---|---|---|---|
| What | **Immutable** sequence of bytes (usually UTF-8) | Mutable bytes | An `int32` Unicode code point |
| `len` | Bytes | Bytes | — |
| Index `s[i]` | A **byte** | A byte | — |
| Conversion | `[]byte(s)` / `string(b)` **copy** (except when the compiler can prove it's safe, e.g. `m[string(b)]` lookups, comparisons) | | `[]rune(s)` allocates 4 bytes per rune |

```go
s := "héllo, 世界"
len(s)                          // 14 bytes
utf8.RuneCountInString(s)       // 9 runes
s[1]                            // 0xC3: first byte of 'é', not 'é'
for i, r := range s {           // range decodes UTF-8: i = byte offset, r = rune
    fmt.Println(i, string(r))
}
s[:2]                           // may cut a multi-byte character in half → invalid UTF-8
```

- User-visible "characters" can be several runes (combining marks, emoji sequences): grapheme clusters need a library (`golang.org/x/text`, `rivo/uniseg`).
- Building strings: `strings.Builder` (no copy on `String()`), `strconv.Append*`, `fmt.Appendf`; avoid `+=` in loops ([[02 - Go Memory and Escape Analysis]]).
- Substrings share the original string's memory, like slices: keep a copy (`strings.Clone`, 1.20) when holding a small part of a big string long-term.
- `strings.EqualFold` for case-insensitive comparison; `bytes`/`strings` functions mirror each other (`Cut`, `CutPrefix`, `Fields`, and `CutLast` in 1.27).

---

## Helpers and Iterators

| Package | Highlights |
|---|---|
| **`slices`** (1.21) | `Sort`, `SortFunc`, `BinarySearch`, `Contains`, `Index`, `Clone`, `Compact`, `Insert`, `Delete` (zeroes freed tail since 1.22), `Reverse`, `Max`, `Concat`, iterator funcs `All`, `Values`, `Collect`, `Sorted` (1.23) |
| **`maps`** | `Keys`, `Values`, `All` (iterators, 1.23), `Clone`, `Copy`, `DeleteFunc` |
| **`strings` / `bytes`** | `Lines`, `SplitSeq`, `FieldsSeq` iterators (1.24) |
| Built-ins | `min`, `max`, `clear` (1.21) |

```go
for k, v := range maps.All(m) { ... }                 // iter.Seq2
keys := slices.Sorted(maps.Keys(m))                   // deterministic order
active := slices.DeleteFunc(slices.Clone(users), func(u User) bool { return !u.Active })
```

- **Range-over-func iterators** (1.23): functions of type `iter.Seq[V]` / `iter.Seq2[K, V]` work directly with `for range`, enabling lazy pipelines without channels ([[09 - Go Generics]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Ignoring `append`'s result** | Lost elements | Always `s = append(s, ...)` |
| **Shared backing array** | One slice overwrites another | `slices.Clone`, full slice expressions `s[:n:n]` |
| **Sub-slice of a huge buffer kept** | Memory retained | `bytes.Clone` / `strings.Clone` |
| **Concurrent map writes** | Unrecoverable crash | Mutex-guarded maps, `sync.Map`, sharding; `-race` in tests |
| **Writing to a nil map** | Panic | `make` before use |
| **Depending on map order** | Flaky tests/output | Sort keys |
| **Huge maps after deletes** | Memory never returned | Recreate the map |
| **Indexing strings as characters** | Broken non-ASCII text | `range`, `utf8` package, grapheme libraries |
| **Byte/string conversions in hot loops** | Allocations | Keep one representation |

---

## Interview Lines

> [!tip] Say it like this
> - "A slice is a header of pointer, length and capacity over a backing array; `append` reuses spare capacity, so two slices from the same base can overwrite each other unless I copy or cap the capacity with a full slice expression."
> - "Since 1.24 maps are Swiss tables; iteration order is randomised, maps never shrink, and concurrent writes crash the process, so shared maps need a mutex."
> - "Strings are immutable UTF-8 bytes: `len` counts bytes, indexing gives bytes, and `range` decodes runes."
> - "A small sub-slice or substring keeps the whole original buffer alive, so I clone what I keep."
> - "Since 1.23, `slices`, `maps` and `strings` expose iterators that work with `for range`."

## Related Notes

- [[02 - Go Memory and Escape Analysis]] · [[07 - Go Concurrency Primitives]] · [[09 - Go Generics]] · [[04 - Go Types Interfaces and Embedding]]
- [[07 - Java Collections Internals]] · [[Languages/README]]
