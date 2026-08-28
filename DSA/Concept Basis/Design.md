---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-28
---
# Concept Basis — Design

> [!abstract] Minimal spanning set for **designing a class whose operations hit promised complexities** — which containers, glued how, with what deferred work. One entry per **new idea you have to learn**. LRU/LFU *list reasoning* lives in [[Linked List]]; Min Stack / iterators in [[Stack and Queue]]; Time Map's binary search in [[Binary Search]] #21. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!tip] **The question is never "which LeetCode Design tag is this." It is: for each operation in the API, which structure makes that operation cheap, and how do the structures point at each other?**
>
> A hash map gives `O(1)` by key and nothing else. An array gives `O(1)` by index and `getRandom`. A list gives `O(1)` unlink at a node you already hold. An ordered set gives neighbours and rank. Almost every design below is **a hash map whose values are nodes (or indices) in one of the others**. If you can say that sentence and then name the second structure, you can derive the class.

## Mechanism axes

| Axis | Values |
|---|---|
| **What the API promises** | `O(1)` get/put · `O(1)` getRandom · floor-of-time · a snapshot · encode/decode · back/forward · increment a prefix of a stack |
| **Which container owns the data** | array · hash map · linked list · ordered set / `std::map` · heap · ring buffer |
| **How the structures point at each other** | map → index · map → list node · map → ordered-set iterator · two maps in opposite directions |
| **Versioning** | none · **per-key timestamps** · **global snapshot id** · copy-on-write |
| **When work happens** | eagerly on write · **lazily on read** (pull fanout, deferred increment) · on a `snap()` / `flush` |
| **What a new write does to history** | append · **truncate the future** (browser) · invalidate a snapshot copy |
| **Assumption that breaks** | `unordered_map` is not worst-case `O(1)` · a delimiter in the payload breaks naive join · copying the whole array on `snap()` is `O(n)` |

## Shape of this topic

```
D1  Pairing containers                 3 ideas
D2  Versioned lookup                   2 ideas
D3  Encoding                           2 ideas
D4  Fanout direction                   1 idea
D5  Cursor on a history                1 idea
D6  Deferred updates                   1 idea
D7  Ring, both ends                    1 idea
                                       + 12 cross-listed ↗
```

**11 native entries, plus 12 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Dual-native with [[Arrays]] #13 on RandomizedSet. LRU/LFU stay native in [[Linked List]] — the list reasoning is the content; they are ↗ here as the cache API.

## Named designs in this file

| The name you remember | Entry |
|---|---|
| **Insert Delete GetRandom O(1)** | #1 |
| Design Underground System | #2 |
| Design a Leaderboard | #3 |
| **Time Based Key-Value Store** | #4 |
| **Snapshot Array** | #5 |
| **Encode and Decode Strings** | #6 |
| Encode and Decode TinyURL | #7 |
| **Design Twitter** | #8 |
| Design Browser History | #9 |
| Design a Stack With Increment | #10 |
| Design Circular Deque | #11 |
| LRU / LFU Cache | ↗ [[Linked List]] #12 · #13 |
| Min Stack · FreqStack · Queue with Stacks | ↗ [[Stack and Queue]] |
| Nested List Iterator | ↗ [[Stack and Queue]] #19 |

---

## D1 · Pairing containers

> [!tip] **For each operation, name the structure that makes it `O(1)` (or `O(log n)`), then name how you find that structure's node from a key.** If you cannot, the design is not finished.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Insert Delete GetRandom O(1) | LC **380** | **An array for `getRandom` (uniform index), a hash map `value → index` so delete can swap-with-last and pop.** The array cannot delete from the middle in `O(1)`; the map cannot pick a uniform element. Together they can. Dual-native with [[Arrays]] #13 — that file owns *why the array*, this file owns *the pairing as a design move*. Duplicates (LC **381**) replace the index with a *set* of indices. |
| 2 | Design Underground System | LC **1396** | **Two maps with different keys, because the two queries are about different entities.** `id → (station, check-in time)` for the in-flight passenger; `(start, end) → (total, count)` for the route stats. New against #1 because you are not making one structure fast — you are **splitting the state along the query grain**. The moment a customer checks out, you drop them from the first map and add one sample to the second. Underground, food-ratings, and "id → record, group → aggregate" are all this. |
| 3 | Design a Leaderboard | LC **1244** | **Hash the entity, order the scores.** `id → score` for changeScore / reset; an ordered multiset (or `map<score, count>`) for top(K). New because the second structure is *sorted*, so every update must repair both — bump a score means erase the old score from the ordered side, then insert the new. The same pairing is Food Rating (LC **2353**) and Stock Price (↗ [[Sorted Containers & Order Statistics]] #13). Forgetting to erase the old score is the bug. |

## D2 · Versioned lookup

| # | Problem | Source | The new idea |
|---|---|---|---|
| 4 | Time Based Key-Value Store | LC **981** | **Each key owns a timeline: a vector of `(timestamp, value)`, strictly increasing, and `get` is `upper_bound` then step back.** The hash map is `key → vector`. New because the query is a *floor*, not a match — which is why `unordered_map` alone is not enough and why this was sitting as an exclusion of [[Binary Search]] #21 (the search half). Native here for the *per-key history* model. |
| 5 | Snapshot Array | LC **1146** | **One global version clock; store only the indices that changed.** `snap()` bumps `snap_id`. Each index holds a vector of `(snap_id, value)` — `set` appends if the last snap differs, else overwrites. `get(i, snap_id)` is floor-search on that one index's history. New against #4: versions are **global**, not per-key, and the win is not copying the array (`O(n)` per snap). Copy-on-write of the whole array is the thing this exists to beat. |

## D3 · Encoding

| # | Problem | Source | The new idea |
|---|---|---|---|
| 6 | Encode and Decode Strings | LC **271** | **Length-prefix, because any delimiter you pick can appear in the data.** `encode(["a#b","c"])` cannot be `"a#b#c"`. Write `len` then the bytes (or `len#payload`) so decode is a cursor: read a number, consume that many characters, repeat. New as a *protocol* idea, and it is the one that transfers to any "serialise a list of arbitrary strings" question. Tree serialisation ([[Binary Trees]] #12) is the same problem with nulls as the extra alphabet. |
| 7 | Encode and Decode TinyURL | LC **535** | **A bijection in both directions: `long → short` and `short → long`.** Counter, random, or hash the long URL; collision means retry or the counter. New against #6: you are not packing a list, you are injecting a **short name** and you must invert it. Two maps, or one map plus a reversible encoding. The hashing half is [[Hashing]] #4's bijection wearing a URL. |

## D4 · Fanout direction

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Design Twitter | LC **355** | **Pull on read, not push on write.** Each tweet is stored on the author's list. `getNewsFeed` merges the followees' lists lazily — a heap of list heads, [[Heap]] #7 — instead of fanning every tweet out to every follower at post time. New because the *direction of work* is the design: write-fanout is cheap reads and expensive follows/unfollows (and storage linear in followers); read-fanout is cheap writes and a `k`-way merge on read. Interviews want you to name the tradeoff, then write the pull version. |

## D5 · Cursor on a history

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Design Browser History | LC **1472** | **A cursor on a linear history; `visit` truncates everything forward of the cursor.** Two stacks (back / forward) or one list plus an index: `back`/`forward` move the cursor; `visit` pushes and **clears the forward stack**, because the future of a different branch is gone. New against the nested-list iterator (↗ [[Stack and Queue]] #19): that one *expands* lazily and never throws work away; this one *discards* the unused branch. The truncation is the idea. |

## D6 · Deferred updates

| # | Problem | Source | The new idea |
|---|---|---|---|
| 10 | Design a Stack With Increment Operation | LC **1381** | **Do not walk the bottom `k` on increment — leave a lazy add on the `k`-th slot, and flush it on pop.** An `inc[i]` means "add this to every element at or below `i` when it leaves." `pop` applies `inc[top]`, then pushes that lazy add one slot down. New because the API's expensive-looking operation is **O(1) by lying until the value is observed**. Same instinct as lazy deletion on a heap ([[Heap]] #12) and as difference arrays ([[Prefix Sums & Difference Arrays]] #17) — deposit the effect at a boundary, materialise later. |

## D7 · Ring, both ends

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Design Circular Deque | LC **641** | **A ring buffer with *both* operations on *both* ends.** Circular queue ([[Stack and Queue]] #20) is insert-at-tail / remove-at-head, and the full-vs-empty coincidence is already the idea there. Here `insertFront` / `deleteLast` are legal too, so you maintain `front` and `rear` and wrap both. New because the two-ended API is what forces the extra pointer arithmetic; the wrap and the wasted-slot (or size counter) are #20's. Write the queue first, then add the other two operations. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | LRU Cache | LC **146** | Hash map to a list node; recency is list order; unlink is `O(1)` *because* the map hands you the node. [[Linked List]] #12. |
| ↗ | LFU Cache · All O(1) | LC **460** · **432** | One list per frequency, `minFreq` never searched. [[Linked List]] #13. |
| ↗ | Min Stack · FreqStack · Queue from Stacks | LC **155** · **895** · **232** | Auxiliary invariant per frame; stack per frequency; amortised transfer. [[Stack and Queue]] #10 · #25 · #11. |
| ↗ | Flatten Nested List Iterator | LC **341** | A stack that survives between `next()` calls. [[Stack and Queue]] #19. BST iterator is [[Binary Search Trees]] #14; generated search is [[Backtracking]] #18. |
| ↗ | Design Circular Queue | LC **622** | Ring buffer, full vs empty. The one-ended cousin of #11. [[Stack and Queue]] #20. |
| ↗ | Design HashMap | LC **706** | Chaining, load factor, rehash. [[Hashing]] #9. |
| ↗ | Implement Trie · Add-and-Search Words | LC **208** · **211** | The path is the key; wildcard forks the descent. [[Tries]] #1 · #3. |
| ↗ | My Calendar · Range Module | LC **729** · **715** | Neighbour queries on a live interval set. [[Intervals]] I3. |
| ↗ | Find Median from Data Stream | LC **295** | Two heaps, or a `multiset` cursor. [[Heap]] #11, [[Sorted Containers & Order Statistics]] #7. |
| ↗ | Kth Largest in a Stream · Seat Manager | LC **703** · **1845** | Size-`k` heap / min-heap of free ids. [[Heap]] #5. |
| ↗ | External merge sort | *concept* | I/O is the cost; sequential merge, not quicksort. [[Sorting & Custom Comparators]] #18. |
| ↗ | Number of Recent Calls · Hit Counter | LC **933** · **362** | Time-expiring queue. [[Sliding Window]] #11. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Insert Delete GetRandom *(duplicates)* | LC **381** | #1 | Map to a *set* of indices. Named in the entry. |
| Design a Food Rating System | LC **2353** | #3 | Entity hash + ordered scores, cuisine as a second key. |
| Stock Price Fluctuation | LC **2034** | #3 | Latest plus ordered prices. [[Sorted Containers & Order Statistics]] #13. |
| Design a Number Container System | LC **2349** | #3 | `index → number` and `number → ordered indices`. |
| Max Stack | LC **716** | ↗ Min Stack | A second stack of maxima, or a tree. [[Stack and Queue]] #10. |
| Min Queue | *classic* | ↗ Min Stack | Two min-stacks, or a monotonic deque. [[Stack and Queue]] #11 + #21. |
| Design Parking System | LC **1603** | — | Three counters. Not a design. |
| Design an Ordered Stream | LC **1656** | — | A cursor plus a buffer of holes. |
| Design a Stack *(arrays)* | — | — | The container tutorial. |
| Peeking Iterator | LC **284** | ↗ LC 341 | One buffered `next`. |
| Combination Iterator | LC **1286** | ↗ LC 341 | Resumable generation. [[Backtracking]] #18. |
| Design Linked List | LC **707** | — | Sentinels. [[Linked List]] #2. |
| Design Skiplist | LC **1206** | — | [[Linked List]] #14. |
| Design Search Autocomplete | LC **642** | — | [[Tries]] #6. |
| Design In-Memory File System | LC **588** | — | Trie over tokens. [[N-ary Trees]] #11. |
| Design Add and Search Words | LC **211** | ↗ Trie | Already ↗ [[Tries]] #3. |
| Snapshot Array *(copy whole array)* | LC **1146** | #5 | The `O(n)` snap this exists to beat. |
| Encode TinyURL *(just hash, no invert)* | LC **535** | #7 | You still need `short → long`. |
| Design Twitter *(push fanout)* | LC **355** | #8 | The other side of the tradeoff, named in the entry. |
| Implement Queue using Stacks | LC **232** | ↗ | [[Stack and Queue]] #11. |
| Implement Stack using Queues | LC **225** | ↗ | Mirror of the same amortisation. |

---

## Self-audit

**Borderline calls, and which way I went**

- **LRU/LFU ↗ rather than native.** [[Linked List]] already develops the map-to-node and `minFreq` arguments, and that file's justification for existing *is* those designs. Native here would be a second copy of the same two entries. The pairing *sentence* in this file's opening tip is the Design half; the implementations stay there.
- **#1 dual-native with [[Arrays]] #13.** Array performance profile vs container pairing. Both readers need it.
- **#4 and #5 kept split.** Per-key timestamps vs a global snap clock. Binary Search collapsed both into #21; that was correct *for the search*, wrong *for the versioning model*. Promoted on the Design side.
- **#6 and #7 kept split.** Length-prefix of a list vs injective short names. Both are "encode/decode" and both are asked; the failure modes (delimiter in payload vs collision / invertibility) differ.
- **#11 native despite [[Stack and Queue]] #20.** Queue is one pair of ends. Deque is both. Write #20 first.
- **External sort ↗, not native.** [[Sorting & Custom Comparators]] #18 already has the I/O cost model. This file points at it so a design-driller finds it.
- **Hit Counter ↗ Sliding Window.** The class wrapper is not an idea.

**Naming check.** #8 stayed "Design Twitter" because pull-vs-push is in the body and the problem is the handle. #10 is the lazy-increment move. #5 stayed Snapshot Array — the COW-vs-history split is in the body.

**Step 4B — reverse sweep**

Eighteen descriptions.

- **"Value of this key at time t"** → #4. **"Index i as of snapshot 7"** → #5. Split held.
- **"Serialise a list of strings that may contain hashes"** → #6, not TinyURL.
- **"News feed of people I follow"** → #8, and the ↗ heap merge. Intended pair.
- **"Back, forward, then visit a new URL"** → #9. Truncation is what #19's iterator does not do.
- **"Add 5 to the bottom 3 of the stack in O(1)"** → #10.
- **"O(1) getRandom and O(1) delete"** → #1.

No missing axis. Design tags on LeetCode are a junk drawer; the axes above are what stop this file becoming one.

**Step 4C — inward**

(i) [[Linked List]] Design-boundary hedge, [[Sorted Containers]] #4/2034/2349, [[Stack and Queue]] #19 "Design basis", [[Heap]] Twitter, [[Binary Search]] Time Map / Snapshot exclusions, [[Sorting]] #18 "move to Design", [[Arrays]] Underground/Twitter exclusion. All now have a native or a ↗.
(ii) LRU, LFU, Min Stack, Trie, Calendar, Median, Time Map, Snapshot, Twitter, RandomizedSet, TinyURL, Browser, Circular deque/queue — each is native here or ↗ a file that develops it.
(iii) Those hedges closed.

**What I am uncertain about**

- **Whether #2 (Underground) is too thin.** Two maps keyed differently. Kept because "split state along the query grain" is the move people miss when they stuff everything into one map.
- **Rate limiter / token bucket / leaky bucket.** System-design round, not this DSA Design tag. Named so they are not silently dropped; out of this file's scope.
- **Concurrency (thread-safe LRU, lock striping).** LLD / [[Concurrency]], not here.

**Completeness confidence: ~88%.** D1–D4 I would call complete for Indian SDE design-tag rounds. Remaining miss risk is a LeetCode Design problem whose *idea* is a composition I collapsed (Food Rating → #3 is the template). The ↗ table is doing a lot of work, which is correct: most "design X" questions are a class wrapper around an idea another file already owns.

## Related Notes

- [[README]]
- [[Hashing]]
- [[Arrays]]
- [[Linked List]]
- [[Stack and Queue]]
- [[Heap]]
- [[Binary Search]]
- [[Sorted Containers & Order Statistics]]
- [[Intervals]]
- [[Tries]]
- [[Sliding Window]]
- [[Sorting & Custom Comparators]]
- [[Binary Search Trees]]
- [[Backtracking]]
- [[Prefix Sums & Difference Arrays]]
- [[N-ary Trees]]
