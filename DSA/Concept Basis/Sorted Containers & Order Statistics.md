---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Sorted Containers & Order Statistics

> [!abstract] Minimal spanning set for **maintaining order while the data changes** — insert and query by rank, neighbour, or position as you sweep. One entry per **new idea you have to learn**. The *structures* are in [[Binary Search Trees]] S4 and [[Segment Trees]]; this file is about the **usage patterns**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## Why this file exists separately

Four files independently hit the same missing idea before this one was written: a structure you **insert into** *and* **query by order** in the same sweep. [[Binary Search Trees]] answers *what the structure is* — why balance matters, what subtree-size augmentation buys. [[Segment Trees]] #3 answers *how to build the portable version*. Neither answers **when you reach for one and which one**, which is what actually decides problems.

## Pick the weakest structure that answers your query

> [!tip] **This lattice is the single most useful thing in the file. Read your query off the left column and stop at the first row that covers it.**
>
> | I only ever need… | Use | Cost |
> |---|---|---|
> | the minimum (or maximum) | a **heap** | `O(log n)`, tiny constant |
> | the minimum, plus arbitrary deletion | a heap with **lazy deletion** ([[Heap]] #12) | `O(log n)` amortised |
> | the **neighbours** of a value — floor, ceiling | an **ordered set** (`std::set` / `std::map`) | `O(log n)` |
> | the **rank** of a value, or the **k-th** value | a **value-indexed BIT** or `__gnu_pbds::tree` ([[Binary Search Trees]] #12) | `O(log n)` |
> | an arbitrary **aggregate** over a value range | a **segment tree** | `O(log n)` |
>
> Reaching past your actual need is the standard failure here — a segment tree where a heap would do is thirty lines you did not have to write and a bug surface you did not have to own. The reverse failure is worse and quieter: **a heap cannot answer "how many are smaller", and a hash map cannot answer "what is nearest"**, and discovering that halfway through is what loses the round.

## Mechanism axes

| Axis | Values |
|---|---|
| **What the query asks** | membership · min or max only · **floor / ceiling** (nearest neighbour) · **rank** (how many are smaller) · **select** (the k-th) · a count over a value range · an aggregate over a value range |
| **How the container changes** | insert-only · insert and delete · **insert and expire** (a sliding window) · **insert on entry, remove on exit** (along a DFS path) · versioned |
| **Set or multiset** | duplicates forbidden · duplicates allowed, so counts are required |
| **What the sweep is over** | array positions · an order **you imposed** by sorting · time · queries reordered offline |
| **Is the structure monotone?** | it only ever grows (offline sorting, activation windows) · it grows and shrinks |
| **Where the order comes from** | the values themselves · a derived key · a compressed rank |
| **Implementation** | balanced BST · **`__gnu_pbds` order-statistic tree** · value-indexed BIT · segment tree · two heaps facing each other · (Python: `bisect.insort` / `SortedList`) |
| **What breaks** | a heap cannot delete from the middle · a plain BST cannot answer rank without augmentation · **`std::multiset::erase(v)` wipes every copy** · **`std::set` cannot answer rank** · large or sparse values cannot index an array · `O(n)` insert is fine at `10⁵` and fatal at `10⁷` |

## Shape of this topic

```
S1  Rank as you sweep                4 ideas
S2  Neighbour queries                2 ideas
S3  The windowed multiset            4 ideas
S4  Making the structure monotone    2 ideas
S5  Implementation realities         2 ideas
                                     + 7 cross-listed ↗
```

**14 native entries, plus 7 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** #14 was added by the reverse sweep and sits inside S4.

---

## S1 · Rank as you sweep

The pattern the other four files kept needing: **each element asks a question about the elements already inserted, then joins them.**

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Count of Smaller Numbers After Self | LC **315** | **Insert as you sweep, and query the rank of the thing you are about to insert.** Walk right to left; at each element ask "how many smaller values have I already seen", then add it to the structure. The shape is what generalises, not the problem: *for each `i`, a question about `{j : j > i}`* becomes *for each `i`, a question about what is already in the container*. Once you see that, the direction of the sweep becomes a choice you make to put the right elements inside. Implementation is a value-indexed BIT over compressed values ([[Segment Trees]] #3) or an order-statistic tree ([[Binary Search Trees]] #12). |
| 2 | Counting inversions | *classic — CSES **1160**; LC **493** is the scaled variant* | **Merge sort counts inversions for free, with no container at all.** During the merge, when you take an element from the right half, every element still unmerged on the left is greater than it — so you add the left half's remaining size in one step. New because it removes the container entirely: divide-and-conquer supplies the ordering that a BIT was being used to maintain. Worth holding alongside #1 because the choice between them is real — merge sort is `O(n log n)` with no compression step, but it is offline and destroys the original order, while #1 survives interleaved queries. |
| 3 | Select the k-th by descending a BIT | *classic — the `O(log n)` companion to #1* | **A Fenwick tree can answer `select(k)` in `O(log n)`, not `O(log² n)`, by walking its implicit tree from the top.** Start at the highest power of two and greedily take the largest jump whose accumulated count stays below `k`; where you stop is the answer's index. The naive approach — binary search the value, each step a prefix query — costs `O(log² n)` and is what most people write. New because it exploits the fact that a BIT's index structure *is* a balanced tree, so you can descend it directly. This is what makes "k-th smallest with insertions and deletions" cheap, which is the operation an ordered set cannot give you without augmentation. |
| 4 | Sequentially Ordinal Rank Tracker | LC **2102** | **When only *one* rank is ever asked and it moves predictably, you do not need a sorted container — two heaps suffice.** Queries always advance to the next-best location, so a max-heap of candidates and a min-heap of already-returned ones exchange a single element per query. Listed because it is the entry that tells you when to *leave* this file: a fully ordered structure is overkill when the queried rank only ever moves by one, and recognising that is the same instinct as the lattice above. |

## S2 · Neighbour queries

| # | Problem | Source | The new idea |
|---|---|---|---|
| 5 | My Calendar I | LC **729** | **`lower_bound` and `--it` give you the two neighbours of an insertion, which is all an overlap check needs.** Ceiling is `m.lower_bound(start)` (first key `≥ start`); floor is `--it` if that iterator is not `begin()`. To know whether a new interval collides with anything, you only ever have to look at the interval starting just before it and the one just after — not at a scan. `O(log n)` per booking. The reflex to build: **when you need a *neighbour* rather than a *match*, `std::map` is the answer and `unordered_map` is not.** ↗ developed in [[Binary Search Trees]] #16. The overlap *question* is [[Intervals]] #11. |
| 6 | Exam Room | LC **855** | **Maintain a set of occupied positions and derive the answer from the *gaps between neighbours*.** Seating the next student means finding the largest gap, which is a property of consecutive pairs rather than of any element — so inserting or removing a seat invalidates exactly the one or two gaps touching it. New because the queried quantity **lives on the edges between container elements, not on the elements**, so every mutation must repair its local gaps. Same shape as merging intervals on a stream and as "largest gap after removing a point"; a heap of gaps with lazy deletion is the usual companion. |

## S3 · The windowed multiset

Insert *and* expire. The family that made this file necessary, because a deque handles extremes and nothing else.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Sliding Window Median | LC **480** | **Keep a multiset and hold a cursor at the k-th position, advancing it as the window shifts.** Contrast the two-heaps-with-lazy-deletion solution ([[Heap]] #12): the multiset version is shorter, needs no rebalancing invariant, and generalises immediately from the median to *any* fixed rank — which the two-heap split cannot do without redesigning it. That generality is the entry. In C++ the cursor is a real iterator; elsewhere it is an index into a `SortedList` or a BIT rank query. |
| 8 | Longest Continuous Subarray With Absolute Diff ≤ Limit | LC **1438** | **One multiset answers min *and* max together, where monotonic deques need two structures.** The deque solution is faster and is the right answer ([[Stack and Queue]] #22) — this entry is about knowing the trade: a multiset costs a `log` factor and buys you a *queryable* window, so the moment the predicate needs anything beyond the two extremes (a median, a count in a range, a nearest value) the deques cannot be patched and the multiset already works. **Learn the deque, but recognise the ceiling it has.** |
| 9 | Contains Duplicate III | LC **220** | **A neighbourhood query over an expiring window.** "Is anything within `t` of this value among the last `k` elements" needs `s.lower_bound(x − t)` on a set you are also evicting from as you advance — #5's query under #7's discipline, which is why it lives here rather than in either. New in combination: the container must support neighbour lookup *and* removal by identity, which rules out every heap. ↗ [[Binary Search Trees]] #15. The bucket form is [[Hashing]] #8. |
| 10 | Finding MK Average | LC **1825** | **Partition the container into three ordered regions and maintain the boundaries.** The average of the middle after discarding the smallest `k` and largest `k` needs a low set, a middle set, a high set, and running sums — and every insertion or expiry may cascade one element across each boundary. New because it is the general form of the two-heaps idea ([[Heap]] #11): **any fixed number of ordered regions can be maintained, as long as each boundary is repaired after every mutation.** Fiddly, and the honest reason to know it is that it teaches boundary repair as a discipline rather than as a special case for medians. |

## S4 · Making the structure monotone

Both entries buy simplicity by arranging for the container to change in only one direction.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Offline query sorting — the activation window | *classic — practise on LC **1707** or CSES **1144*** | **If the queries may be answered in any order, sort them so the container only ever grows.** With each query carrying a threshold, sorting queries by that threshold and the data by value lets you insert-and-never-delete, which removes the hardest operation from the problem entirely. New because the technique is not about the structure at all — it is about the **order the questions arrive in**, which you are allowed to change whenever the output is a per-query array rather than a stream. The same reframing is [[Segment Trees]] #10's and [[Tries]] #8's; this is its general statement. |
| 14 | Container along a DFS path | *classic — "for each node, how many ancestors are smaller"; LC **1938** is the trie form* | **Insert on the way down, remove on the way back up, and the container always holds exactly the current root-to-node path.** Every node's query is then answered against its own ancestors with no extra structure — and correctness rests entirely on the removal, because a sibling subtree must never see what the other branch inserted. New because the "window" is a **path in a tree** rather than an interval in an array, so expiry is driven by recursion depth instead of by an index. Composes with everything above: rank among ancestors, nearest ancestor value, count of ancestors in a range. The array analogue is [[Binary Trees]] #10's prefix-map-with-undo. |

## S5 · Implementation realities

> [!warning] **This family is here because the interview answer and the code you can actually write differ by language, and being unable to produce a working ordered multiset in your language is a real failure mode.**

| # | Problem | Source | The new idea |
|---|---|---|---|
| 12 | `std::set` has no rank — BIT vs `__gnu_pbds` | *concept — C++ order statistics* | **`std::set` / `std::multiset` give you neighbours and min/max. They do not give you "how many are smaller" or "what is the k-th."** That is the fork this entry exists for. Portable answer: a value-indexed BIT over compressed values (#3, [[Segment Trees]] #3) — works everywhere, including contests that ban GNU extensions. C++-only shortcut: `__gnu_pbds::tree` with `tree_order_statistics_node_update` — `order_of_key(x)` is rank, `find_by_order(k)` is select, and the rest of the set interface still works. **Know which of the two you would write, and say so out loud.** Do not reach for pbds in an interview unless you can also write the BIT; interviewers who know the extension will ask. Python's `bisect.insort` / `SortedList` is the same fork in a language with no ordered set at all — same idea, different library, not a third concept. |
| 13 | Multiset versus set — duplicates need counts | *concept — `std::multiset`; `map<T,int>` as a counted set* | **The moment duplicates are possible, a set silently loses data and every count-based answer is wrong.** C++ gives `std::multiset` directly, with the trap that **`erase(value)` removes *all* copies** — you must `erase(find(value))` (or `erase(it)`) to remove one. The other idiom is `map<T,int>` with manual increment, decrement, and **erasing the key when its count hits zero** — forget that last step and `begin()` starts returning phantoms. Small, and it is the most common source of a wrong answer in this whole topic. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Find Median from Data Stream | LC **295** | Two heaps facing each other across the median, with a size invariant repaired on every insert. The special case of #10 that everyone learns first. [[Heap]] #11. |
| ↗ | Kth Largest Element in a Stream | LC **703** | A size-`k` heap, inverted so the element you can evict cheaply is the one you no longer want. The cheapest structure that answers a *fixed* rank online — the lattice's first row. [[Heap]] #5. |
| ↗ | IPO | LC **502** | Two structures with a one-way flow: items migrate from a list sorted by one key into a heap ordered by another as a budget grows. #11's monotonicity, applied online. [[Heap]] #13. |
| ↗ | Longest Increasing Subsequence | LC **300** | The `O(n log n)` solution *is* a maintained ordered structure — the tails array, binary searched and overwritten. Worth knowing that the tails array is not itself an LIS, only the right length. [[Binary Search]] ↗ and [[Dynamic Programming]] #52; the value-indexed segment tree form (and weighted LIS) is [[Segment Trees]] ↗ LC 300 / [[Dynamic Programming]] #71. |
| ↗ | Count of Range Sum | LC **327** | Range-counting over prefix values — the case where a hash map is not enough because the lookup is an inequality. The clearest demonstration of why this file exists. [[Prefix Sums & Difference Arrays]] #23. |
| ↗ | Coordinate compression | LC **732** | Values up to `10⁹` cannot index an array; replace each by its rank and every counting structure above becomes available. The prerequisite for #1 and #3 on real data. [[Segment Trees]] #11. |
| ↗ | Design a Leaderboard | LC **1244** | Hash the entity, order the scores; every update repairs both. The pairing is the Design idea; the ordered-side erase-then-insert is this file's #13. Native at [[Design]] #3. |

---

## Excluded as variations

| Problem                                  | Source               | Collapses into | Why                                                                                                                                         |
| ---------------------------------------- | -------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Reverse Pairs                            | LC **493**           | #1 · #2        | Inversion counting with a scaled comparison.                                                                                                |
| Count Good Triplets in an Array          | LC **2179**          | #1             | Two rank sweeps, one from each side.                                                                                                        |
| Number of Pairs Satisfying Inequality    | LC **2426**          | #1             | Rank query on a derived key.                                                                                                                |
| Create Sorted Array through Instructions | LC **1649**          | #1             | Rank on both sides at each insertion.                                                                                                       |
| Queries on a Permutation With Key        | LC **1409**          | #1             | BIT over positions, simulating removal.                                                                                                     |
| Kth Smallest Element in a Sorted Matrix  | LC **378**           | —              | Heap or binary-search-the-value; the container never changes. [[Heap]] #8, [[Binary Search]] #12.                                           |
| Kth Largest Element in an Array          | LC **215**           | —              | Static selection, so quickselect wins. [[Two Pointers]] #17.                                                                                |
| My Calendar II · III                     | LC **731** · **732** | #5             | Overlap counting on the same neighbour queries; a compressed sweep scales better.                                                           |
| Data Stream as Disjoint Intervals        | LC **352**           | #5 · #6        | Neighbour lookup, then merge both sides.                                                                                                    |
| Maximum Gap after removing a point       | *classic*            | #6             | Gaps live on the edges; repair the two that changed.                                                                                        |
| Sliding Window Maximum                   | LC **239**           | #8             | A multiset works and a monotonic deque is strictly better. [[Stack and Queue]] #21.                                                         |
| Sliding Window Minimum / range           | —                    | #8             | Same, with the other extreme.                                                                                                               |
| Design a Number Container System         | LC **2349**          | #5             | A hash map from key to an ordered set — composition, not a new idea. Also [[Design]] #3's pairing.                                                                        |
| Stock Price Fluctuation                  | LC **2034**          | #13            | `map<price, int>` plus a latest-timestamp map. The counted-set idiom. Also [[Design]] #3.                                                              |
| Top K Frequent Elements                  | LC **347**           | —              | Bucket by a bounded derived key; no ordering needed. [[Arrays]] #14.                                                                        |
| Merge k Sorted Lists                     | LC **23**            | —              | A heap of size `k`; order is maintained across sources, not within a container. [[Heap]] #7.                                                |
| Skip lists                               | *concept*            | lattice · #5   | Ordered-set interface, not rank. Worth naming (Redis uses one), not worth learning separately here. It earns a full entry in [[Linked List]] #14 for a different reason: there it is the only way to get `O(log n)` search *without abandoning the container*. |
| Merge sort tree · wavelet tree           | *concept*            | —              | "K-th smallest in a *position* range" rather than in the whole container. Out of scope, and genuinely CP.                                   |
| Persistent order-statistic tree          | *concept*            | —              | Versioned rank queries. [[Segment Trees]] #13.                                                                                              |

---

## Self-audit

**Borderline calls, and which way I went**

- **The whole file versus folding it into [[Binary Search Trees]].** The README had it as a "missing section, not a missing file", and I nearly honoured that. Split it out because the BST file is organised around *the ordering invariant of a tree* while this one is organised around *what query you need and when the container changes* — and S3, S4 and S5 have no natural home under the first framing. The lattice callout alone justifies the separation.
- **#2 (merge-sort inversions) native despite involving no container.** Kept because the entry's content is the *choice*: divide-and-conquer supplies the ordering for free when you are offline, and a container is what you pay for when you are not. A file about ordered containers that never mentions the container-free alternative teaches the wrong reflex.
- **#4 (LC 2102) kept as an entry, which is unusual** — it exists to tell you when to leave the topic. Same justification as [[Sliding Window]] #14 and [[Prefix Sums & Difference Arrays]] P8: the negative results are worth as much as the techniques, and they are the ones nobody writes down.
- **#7, #8 and #9 all overlap with existing ↗ entries** in [[Heap]], [[Stack and Queue]] and [[Binary Search Trees]]. That is cross-listing working as intended, and each is written from *this* file's angle — the multiset's generality, the deque's ceiling, and the combination of neighbour-plus-expiry — rather than duplicating the other treatment.
- **S5 exists at all.** Two entries about language libraries in a concept basis is a category violation, and I kept it anyway: #12 is the C++ fork (`std::set` has no rank — BIT vs `__gnu_pbds`); #13 is the `erase(value)` / count-to-zero bug, which *is* the most frequent wrong answer in the topic. Rewritten from a Python-first draft on a later gap pass.
- **#3 (BIT descent) is the most technical entry** and I considered making it tail scope. Left untagged because it is the operation that distinguishes this topic from [[Heap]], and because the `O(log² n)` version most people write is a visible tell.

**Naming check.** Two retitles. #1 was drafted as "Count of Smaller Numbers After Self", which names a puzzle; it is now the sweep-and-rank *shape*, with the observation that the sweep direction is a choice. #11 was drafted as "offline processing", too vague to navigate to; it is now *sort the queries so the container only grows*, which is the actual move. #6 was checked and retitled from "Exam Room" to foreground that **the queried quantity lives on the gaps between elements**, since that is what a differently-dressed version would share.

**Step 4B — reverse sweep**

Twenty-three plain-language descriptions navigated against the family headings. **One failure:**

- **"For each node, how many of its ancestors hold a smaller value"** landed nowhere. #11's offline sorting is the closest and is wrong — nothing is being reordered, and the container shrinks as well as grows. The axis it exposed was *how the container changes*: my draft listed insert-only, insert-and-delete, and insert-and-expire, all of which are **array-shaped**. Expiry driven by **recursion depth** was missing, and with it the whole insert-on-entry-remove-on-exit family. That is **#14**. Notably [[Tries]] #8 already had this idea in trie clothing and [[Binary Trees]] #10 had it with a hash map, so this is the third file to need it — which is a reasonable argument that it should have been visible earlier.

Three collisions, all checked and cleared. "Median of a stream" reaches ↗ LC 295 and #10 (special case and general form, correctly linked). "Biggest value in each window" reaches #8 and ↗ LC 239, which is the trade the entry is *about*. "How many smaller ones come after" reaches #1 and #2, again the intended comparison.

**What I am uncertain about**

- **The Design boundary is closed.** #4 stays because the queried rank only ever moves by one (leave this file, two heaps). LC 2034 and LC 2349 are composition, now also named as exclusions of [[Design]] #3. The cut held: ordering stays here, the API glue is Design.
- **Whether #10 (LC 1825) is in scope.** It is a hard problem, rarely asked, and its lesson — boundary repair across `k` ordered regions — is arguably just #7 plus arithmetic. Kept because it is the general form of the two-heap idea and nothing else states that generalisation.
- **Interval containers are now claimed.** [[Intervals]] #11–#13 own Calendar I, Range Module, and online k-booking. #5 here stays the neighbour *query*; #6 stays gaps-as-the-queried-quantity.
- **Recall is thinnest on S1's exclusion list.** LeetCode has a long tail of "count pairs satisfying an inequality" problems that all reduce to a rank sweep, and I collapsed them aggressively on the strength of the shape rather than by checking each.
- **Balanced-BST rotations** are deliberately absent — [[Binary Search Trees]] #11 owns them, and this file assumes you will use the library.

**Completeness confidence: ~90%.** The lattice and S1 I am confident about; they are the parts other files kept reaching for and the parts with clear external representation. The Design and Intervals hedges are closed.

## Related Notes

- [[README]]
- [[Binary Search Trees]]
- [[Segment Trees]]
- [[Heap]]
- [[Prefix Sums & Difference Arrays]]
- [[Sliding Window]]
- [[Binary Search]]
- [[Backtracking]]
- [[Linked List]]
- [[Greedy]]
- [[Intervals]]
- [[Design]]
- [[Hashing]]
