---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-23
---
# Concept Basis — Segment Trees & Range Query Structures

> [!abstract] Minimal spanning set for range-query structures: segment tree, Fenwick/BIT, sparse table, sqrt decomposition. One entry per **new idea you have to learn**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!warning] **Scope warning — read before investing time.** This is the most CP-adjacent topic in the basis. In Indian SDE loops, roughly **five entries are genuinely interview-relevant** (#1, #2, #3, #4, #11); the rest appear at Google-tier, in quant rounds, and in contest-flavoured OAs. Each entry is tagged **[core]** or **[tail]**. If you are time-boxed, do the five core entries and stop — an unfinished segment tree topic costs you nothing, while an unfinished DP topic costs you everything.

## The organising question

> [!tip] Every structure here answers one question: **"aggregate over a range, while the data changes underneath you."**
>
> Pick your structure by answering two things. **Does the data change?** If not, use prefix sums (`O(1)`, no structure) or a sparse table. **Is the operation invertible?** If yes — sum, xor, count — a Fenwick tree does it in a quarter of the memory and a fifth of the code. If not — min, max, gcd — you need a real segment tree, because `pre(r) - pre(l-1)` has no meaning when there is nothing to subtract.
>
> Getting that decision right in an interview matters more than writing any of them from memory.

## Mechanism axes

| Axis | Values |
|---|---|
| **Node payload** | a scalar · a struct of several scalars · a sorted container · a whole child tree (2D) |
| **Merge algebra** | associative + commutative + invertible (sum, xor) · associative only (min, gcd) · associative + idempotent (min, max — enables sparse table) |
| **Mutability** | static · point update · range update with lazy tags · versioned (persistent) |
| **What the tree is indexed by** | array position · a **value** after compression · a coordinate on a sweep line · time |
| **Query answered** | range aggregate · count below a threshold · `k`-th in a range · first index where a prefix condition flips |
| **Online or offline** | answer as they arrive · reorder queries first (Mo's, offline BIT) |
| **What breaks** | non-invertible merge breaks BIT · non-idempotent merge breaks sparse table · no associativity at all breaks every tree, leaving sqrt decomposition |

## Shape of this topic

```
R1  Core structures              3 ideas   [core]
R2  Lazy propagation             2 ideas   [1 core, 1 tail]
R3  Richer node payloads         3 ideas   [tail]
R4  Static & offline alternatives 2 ideas  [tail]
R5  Domain handling              2 ideas   [1 core, 1 tail]
R6  Persistence                  1 idea    [tail]
                                 + 4 cross-listed ↗
```

**13 native entries, plus 4 cross-listed (↗).** See [[README]] on cross-listing.

---

## R1 · Core structures

| # | Problem | Source | Tier | The new idea |
|---|---|---|---|---|
| 1 | Range Sum Query - Mutable | LC **307** · CSES **1648** | **[core]** | **The segment tree itself.** Recursive build over halves; a point update walks one root-to-leaf path and pulls values back up; a range query decomposes `[l, r]` into `O(log n)` **canonical nodes** — the maximal aligned blocks that tile it. Everything problem-specific lives in one `merge` function, and once you see that, min-trees and gcd-trees are the same code with one line changed. |
| 2 | Range Sum Query - Mutable (BIT form) | LC **307** | **[core]** | **The Fenwick tree as the lighter alternative.** `i & -i` isolates the lowest set bit ([[Bit Manipulation]] #1), which is exactly the length of the block each index is responsible for. Prefix-only, so range queries need `pre(r) - pre(l-1)` and therefore an **invertible** operation. Roughly a quarter of the memory and a fifth of the lines. Writing both for the same problem is the fastest way to feel why one exists. |
| 3 | Count of Smaller Numbers After Self | LC **315** | **[core]** | **Index the tree by *value*, not by position.** Compress values to `[0, n)`, sweep the array right to left, and at each step query the prefix "how many smaller values have I already seen" then insert the current one. The tree's index axis has nothing to do with the array's. This one reframing solves counting inversions, "how many in `[l,r]` are `< x`", and most rank-as-you-sweep problems — it is the highest-yield idea in the file and the one most likely to actually appear in an interview. |

## R2 · Lazy propagation

| # | Problem | Source | Tier | The new idea |
|---|---|---|---|---|
| 4 | Range Updates and Sums | CSES **1651** | **[core]** | **Lazy propagation.** To add a value across a range you stop at the canonical nodes, record a pending tag, and push it down only when a later query forces you to descend. Two disciplines to get right: *push-down* before descending, *pull-up* after returning. Without this, range updates are `O(n log n)` per operation and the structure is pointless. |
| 5 | Range assign composed with range add | CSES **1651** | **[tail]** | **The hard part of lazy is the algebra of tags, not the pushing.** "Add" tags compose by summing. "Assign" tags *annihilate* any pending add beneath them, so applying them out of order silently corrupts the tree. Whenever a node can hold two kinds of pending work you must define how they compose — and that composition, not the tree, is where the bugs live. |

## R3 · Richer node payloads

The tree stops storing a number.

| # | Problem | Source | Tier | The new idea |
|---|---|---|---|---|
| 6 | Maximum subarray sum with updates | SPOJ **GSS1** · CSES **1190** | **[tail]** | **Store a struct so the merge becomes associative.** Max-subarray is not associative on its own, but `(total, bestPrefix, bestSuffix, bestInside)` is: the best subarray of a merged node either lives entirely in one child or straddles the join, and the straddling case is `leftSuffix + rightPrefix`. The general principle — **augment the node with whatever extra state makes the merge associative** — is what lets segment trees answer questions that look non-decomposable. |
| 7 | Merge sort tree | *classic — "count values ≤ x in `[l, r]`"* | **[tail]** | **The payload is a whole sorted container.** Each node stores its range's elements in sorted order (`O(n log n)` memory), so a query binary-searches each of the `O(log n)` canonical nodes for `O(log² n)` total. The move: when you cannot aggregate, *keep the elements* and search them. Static only — updates destroy it. |
| 8 | Descend the tree | *classic — "first prefix whose sum exceeds `x`"* | **[tail]** | **Binary search inside the structure.** The naive answer is a binary search whose predicate is a range query, at `O(log² n)`. Instead, walk down from the root: at each node, if the left child's aggregate already suffices, go left, otherwise subtract it and go right. One descent, `O(log n)`. Generalises the whole of [[Binary Search]] B1 onto a tree. |

## R4 · Static & offline alternatives

| # | Problem | Source | Tier | The new idea |
|---|---|---|---|---|
| 9 | Static Range Minimum Queries | CSES **1647** | **[tail]** | **Sparse table — idempotence buys you `O(1)`.** Precompute the answer for every power-of-two-length block in `O(n log n)`, then cover any range with **two overlapping blocks**. The overlap is harmless only because `min(x, x) = x` — which is why this works for min, max and gcd but never for sum. The doubling table is the same object as binary lifting in [[Graphs]] #29. |
| 10 | Mo's algorithm / sqrt decomposition | CSES **1734** | **[tail]** | **When the merge is not associative at all, reorder the questions.** Sort queries by `(block of l, r)` so that moving the window between consecutive queries is cheap, giving `O((n + q)√n)` with only an incremental add/remove. The reframe: if the *structure* cannot be made to work, change the *order the queries arrive in*. Offline only. |

## R5 · Domain handling

| # | Problem | Source | Tier | The new idea |
|---|---|---|---|---|
| 11 | My Calendar III · Falling Squares · The Skyline Problem | LC **732** · **699** · **218** | **[core]** | **Coordinate compression plus a sweep.** Coordinates up to `10⁹` cannot index anything, so collect every endpoint that appears, sort and deduplicate them, and index by rank instead. The prerequisite for using any of these structures on real-world data — timestamps, prices, positions — and the reason interval problems and segment trees keep meeting. A heap handles the Skyline more simply ([[Heap]] #23); the segment tree generalises to more query types. |
| 12 | Range Sum Query 2D - Mutable | LC **308** · CSES **1652** | **[tail]** | **A tree of trees.** Each node of the outer tree over rows owns an inner tree over columns, giving `O(log² n)` per operation. Straightforward as an idea and unpleasant to write; the 2D BIT is much shorter and covers the sum case, which is the only case anyone asks about. |

## R6 · Persistence

| # | Problem | Source | Tier | The new idea |
|---|---|---|---|---|
| 13 | K-th smallest number in a range | SPOJ **MKTHNUM** | **[tail]** | **Version the tree by sharing nodes.** An update touches one root-to-leaf path, so create `O(log n)` new nodes and point everything else at the old tree — you now have both versions in `O(log n)` extra memory. Subtracting version `l-1` from version `r` gives a tree over exactly that range, which you then descend (#8) to find the `k`-th. Two ideas composed, and the strongest demonstration that a segment tree is a *value*, not a mutable object. |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them while studying this one. See [[README]] on cross-listing.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Range Sum Query - Immutable | LC **303** | **The baseline you must rule out first.** No updates means prefix sums answer in `O(1)` with four lines and no structure. Reaching for a segment tree when the data is static is the most common way to lose points on this topic. [[Prefix Sums & Difference Arrays]] #1. |
| ↗ | Sliding Window Maximum | LC **239** | A segment tree answers this in `O(n log n)` and a monotonic deque answers it in `O(n)`. Listed as the canonical **when not to use a segment tree** — a moving window is not a general range query. [[Stack and Queue]] #21. |
| ↗ | Longest Increasing Subsequence | LC **300** | LIS as a segment tree over the *value* axis: for each element, query `max` over all smaller values and update at its own. Same `O(n log n)` as patience, but it survives generalisations patience cannot handle, like weighted LIS. The clearest payoff of #3's value-indexing idea. [[Dynamic Programming]] #52. |
| ↗ | Kth Ancestor of a Tree Node | LC **1483** | Binary lifting is a sparse table on a tree — literally the same doubling precomputation as #9, applied to ancestors instead of ranges. [[Graphs]] #29. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Range Sum Query 2D - Immutable | LC **304** | ↗ LC 303 | 2D prefix sums with inclusion–exclusion. Static, so no structure needed. |
| Dynamic Range Minimum Queries | CSES **1649** | #1 | The same tree with `min` as the merge. That it is a one-line diff is the point of #1. |
| Range Xor Queries | CSES **1650** | #2 | Xor is its own inverse, so the BIT applies unchanged. |
| Number of Longest Increasing Subsequence | LC **673** | ↗ LC 300 | Value-indexed tree storing `(length, count)` pairs. A richer payload on the same idea. |
| Reverse Pairs | LC **493** | #3 | Counting inversions with a scaled comparison. |
| Count of Range Sum | LC **327** | #3 | Value-indexed BIT over prefix sums instead of over elements. |
| Create Sorted Array through Instructions | LC **1649** | #3 | Two rank queries per insertion. |
| Queries on a Permutation With Key | LC **1409** | #3 | BIT over positions with a moving front. |
| My Calendar I / II | LC **729** · **731** | #11 | Interval overlap with a sorted map; the compression idea is #11's, and a `TreeMap` is the better answer at this size. |
| Rectangle Area II | LC **850** | #11 | Sweep line with a segment tree over compressed y-coordinates. The classic application, no new machinery. |
| Number of Flowers in Full Bloom | LC **2251** | #11 | Difference array on compressed coordinates, then binary search. |
| Corporate Flight Bookings | LC **1109** | #4 | A difference array is enough when all updates precede all queries. Worth doing precisely to see that lazy propagation was unnecessary. |
| Range Frequency Queries | LC **2080** | #7 | Positions bucketed by value, then binary search — a merge sort tree turned inside out. |
| Longest Substring of One Repeating Character | LC **2213** | #6 | Struct nodes carrying prefix and suffix run lengths. Same augmentation principle. |
| Sliding Window Median | LC **480** | [[Heap]] #12 | Two heaps with lazy deletion, or an order-statistic tree. A segment tree over values also works and is overkill. |
| Kth Smallest Element in a Sorted Matrix | LC **378** | [[Binary Search]] #12 | Binary search on the value range. No structure needed. |

---

## Self-audit

**Borderline calls, and which way I went**

- **Segment tree (#1) and BIT (#2) kept as separate entries on the same LeetCode problem.** They are different data structures with different applicability, and the *choice between them* is the interview content. Merging them would hide the invertibility constraint, which is the whole reason both exist.
- **Sparse table (#9) filed here rather than in a Preprocessing topic.** It answers range queries, so it belongs beside the others that do. Its doubling table is also cross-listed against binary lifting, which is the same object.
- **Mo's algorithm (#10) included at all.** Furthest out of scope of anything in this file, and marked tail. Kept because "reorder the queries when the structure cannot be fixed" is a genuine mental move with no other home.
- **Coordinate compression (#11) as an entry rather than a technique note.** It is the single thing that most often blocks people from applying these structures to real inputs, and it is the only [core] entry in the second half of the file.
- **Persistence (#13) kept despite being clearly CP.** It composes #8 and #3 and is the payoff for both. Drop it without guilt if you are time-boxed.
- **Sqrt decomposition merged into #10.** Mo's is sqrt decomposition applied to queries rather than to the array; the plain array version teaches nothing extra once you have real trees.

**Step 4B — reverse sweep**

Twenty-one plain-language descriptions were navigated against the family headings. **No failures.** I am reporting that with less satisfaction than it sounds, because a clean pass on a small file is weak evidence: thirteen entries across six families is easy to navigate, and the topic's vocabulary is unusually standardised, so plain-language descriptions map almost word-for-word onto entry titles. The reverse sweep is at its most powerful on large files with overloaded families — which is exactly where it found things ([[Dynamic Programming]], 68 entries, two misses) and where it found nothing here.

The one description that resolved to nothing was **"I keep inserting and need the rank so far"** — the sorted-container gap, already flagged, now confirmed for the third time.

**What I am uncertain about**

- **Order-statistic trees / policy-based data structures** (`__gnu_pbds::tree`, or a balanced BST with subtree counts). They subsume #3 and #7 in a few lines. This was the **third** file to flag the missing sorted-container topic, after [[Heap]] and [[Binary Search]], which is what tipped it from a note into a confirmed gap — and it is now **closed** by [[Sorted Containers & Order Statistics]], where #1 and #3 here are the implementations it selects between.
- **Segment tree beats** (range chmin/chmax with amortised complexity) — excluded, high confidence. Pure competitive programming.
- **Li Chao tree, divide-and-conquer over queries, offline dynamic connectivity** — excluded as CP, consistent with the same call in [[Dynamic Programming]].
- **Iterative bottom-up segment trees** — a shorter and faster implementation of #1, excluded as an implementation detail. Mild doubt: if you ever need to write one under time pressure it is the better one to know.
- **How much of this belongs at all.** I have marked five entries [core] on the belief that value-indexed BIT counting and coordinate compression do surface in Indian SDE loops while lazy propagation mostly does not. That judgement is the biggest single risk in this file, and it is a scoping judgement rather than a technical one — worth checking against your actual target companies.

**Completeness confidence: ~90%**, the lowest in the basis so far. Not because the concept space is unclear — it is unusually well-defined — but because the *scope boundary* is genuinely arbitrary here in a way it is not for DP or Graphs. The confirmed sorted-container gap accounts for most of the remaining ten percent.

## Related Notes

- [[README]]
- [[Binary Search]]
- [[Heap]]
- [[Dynamic Programming]]
- [[Graphs]]
- [[Binary Trees]]
- [[Binary Search Trees]]
- [[Tries]]
- [[Prefix Sums & Difference Arrays]]
- [[Sorted Containers & Order Statistics]]
- [[Bit Manipulation]]
