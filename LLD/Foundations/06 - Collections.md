---
tags: [lld/foundations, status/draft]
created: 2026-10-11
---
# Collections

> [!abstract] What this is
> Choosing collections deliberately: what each Java collection is good at, its complexity, ordering guarantees, the structures that solve common LLD needs (LRU, priority scheduling, sorted lookups), and the iteration and mutability traps. Picking the right structure is part of the design, and interviewers notice.

---

## Picking a Map

| Map | Order | `get`/`put` | Pick when |
|---|---|---|---|
| **`HashMap`** | None | `O(1)` average | Default lookup by key |
| **`LinkedHashMap`** | Insertion order, or **access order** | `O(1)` | Predictable iteration; **LRU cache** (access order + `removeEldestEntry`) |
| **`TreeMap`** | Sorted by key | `O(log n)` | Range queries, floor/ceiling (next free slot, nearest price), sorted output |
| **`EnumMap`** | Enum declaration order | `O(1)`, array-backed | Keys are an enum ([[04 - Enums and State Machines]]) |
| **`ConcurrentHashMap`** | None | `O(1)` | Shared across threads ([[LLD/Concurrency/07 - Concurrent Collections]]) |

- `TreeMap` / `NavigableMap` methods solve many LLD sub-problems directly: `floorKey`, `ceilingKey`, `headMap`, `tailMap`, `firstEntry`, `pollFirstEntry`.

```java
// Interval booking: does [start, end) overlap an existing booking?
NavigableMap<Integer, Integer> bookings = new TreeMap<>();   // start -> end
boolean canBook(int start, int end) {
    Map.Entry<Integer, Integer> prev = bookings.floorEntry(start);
    Map.Entry<Integer, Integer> next = bookings.ceilingEntry(start);
    return (prev == null || prev.getValue() <= start) && (next == null || next.getKey() >= end);
}
```

---

## Picking a List, Queue or Set

| Collection | Strengths | Watch out | Pick when |
|---|---|---|---|
| **`ArrayList`** | `O(1)` index access, cache-friendly | `O(n)` insert/remove in the middle | Default list |
| **`LinkedList`** | `O(1)` add/remove at the ends | Slow index access, poor cache use | Rarely; prefer `ArrayDeque` |
| **`ArrayDeque`** | `O(1)` push/pop at both ends | No nulls | Stack, queue, deque (replaces `Stack` and `LinkedList`) |
| **`PriorityQueue`** | `O(log n)` offer/poll of the min (or max) | Iteration isn't sorted; `remove(Object)` is `O(n)` | Scheduling by priority or time, top-K, Dijkstra |
| **`HashSet`** | `O(1)` membership | No order | Dedupe, "seen" checks |
| **`LinkedHashSet`** | Membership + insertion order | Slightly more memory | Ordered dedupe |
| **`TreeSet`** | Sorted, `O(log n)`, floor/ceiling | Uses `compareTo`; 0 means duplicate | Sorted unique items, nearest-value lookups |

```java
// Nearest available spot by distance, then id
PriorityQueue<Spot> free = new PriorityQueue<>(
        Comparator.comparingInt(Spot::distanceFromGate).thenComparing(Spot::id));
Spot best = free.poll();
```

- **Priority queue gotcha:** changing an element's priority while it's inside the queue breaks the heap. Remove, update, re-insert (or use lazy deletion: push the new version and skip stale ones on poll).

---

## Iteration Traps

### `ConcurrentModificationException`

- Modifying a collection (other than through the iterator) while iterating it throws `ConcurrentModificationException`, even in a single thread.

```java
for (Ticket t : tickets) if (t.isExpired()) tickets.remove(t);   // throws

tickets.removeIf(Ticket::isExpired);                               // correct
Iterator<Ticket> it = tickets.iterator();                          // also correct
while (it.hasNext()) if (it.next().isExpired()) it.remove();
```

- The check is a "fail-fast" best effort, **not** a thread-safety mechanism: concurrent modification from another thread may or may not throw. Use concurrent collections for shared state.

### Unmodifiable vs immutable

| Call | What you get |
|---|---|
| `List.of(...)`, `List.copyOf(list)` | A truly **unmodifiable copy**; throws on mutation; no nulls |
| `Collections.unmodifiableList(list)` | A read-only **view**; the underlying list can still change |
| `Arrays.asList(array)` | Fixed-size, **writes through** to the array; `add` throws |
| `stream().toList()` | Unmodifiable |
| `Collectors.toList()` | No guarantee (currently a mutable `ArrayList`) |

- Return unmodifiable copies from getters so callers can't mutate internal state ([[02 - Immutability and Value Objects]]).

---

## Keys and Capacity

- Hash-based collections need correct, stable `equals`/`hashCode` on keys; mutating a key after insertion loses the entry ([[02 - Immutability and Value Objects]]).
- Tree-based collections need a consistent `compareTo` or `Comparator`.
- Pre-size big hash collections when you know the size (`new HashMap<>(expected * 4 / 3 + 1)`) to avoid repeated resizing; `HashMap.newHashMap(n)` (Java 19+) does the math.

---

## Interview Lines

> [!tip] Say it like this
> - "`HashMap` for lookup, `LinkedHashMap` in access order for LRU, `TreeMap` when I need floor/ceiling or ranges."
> - "`ArrayDeque` for stacks and queues, `PriorityQueue` for scheduling by time or priority."
> - "Removing while iterating goes through `removeIf` or the iterator."
> - "Getters return `List.copyOf`, not the internal list."

## Related Notes

- [[02 - Immutability and Value Objects]] · [[05 - Generics]] · [[LLD/Concurrency/07 - Concurrent Collections]]
- [[LLD/Foundations/README]]
