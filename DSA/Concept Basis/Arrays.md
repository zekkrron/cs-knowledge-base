---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Arrays

> [!abstract] Minimal spanning set for ideas that exploit **what an array itself is** — contiguous storage, `O(1)` random access, and indices living in the same universe as values. One entry per **new idea you have to learn**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!warning] **Read this before deciding the file is thin.** "Arrays" is not a technique, it is a container, so most of what gets tagged `array` on LeetCode has already been filed by mechanism: scanning windows in [[Sliding Window]], converging and in-place pointers in [[Two Pointers]], folds in [[Prefix Sums & Difference Arrays]], sorted-order search in [[Binary Search]], and optimal-substructure scans in [[Dynamic Programming]]. **This file holds only what is left when those are removed**, and what is left has a genuine unifying thread — see below. The ↗ table is unusually long on purpose: this topic is mostly a hub, and pretending otherwise would either duplicate five files or leave you unable to find anything from here.

## The thread that actually unifies this file

> [!tip] **In an array, indices and values live in the same universe — and almost every native array idea is a way of cashing that in for `O(1)` extra space.**
>
> If the values are a permutation of `0…n−1`, the array *is* a hash map and you already own the storage. If they are bounded by `n`, each cell has headroom you can hide a second number in. If you are allowed to destroy the input, its sign bits are a free bitset. And when even that is unavailable, a **counting argument** can sometimes replace storage entirely.
>
> So the first question on an array problem where the interviewer says "now do it in `O(1)` space" is not "which trick?" It is: **what is the relationship between my values and my indices, and how much headroom does that give me?**

## Mechanism axes

| Axis | Values |
|---|---|
| **Extra space allowed** | `O(n)` — a hash map or a copy is fine · **`O(1)` — the input must double as scratch space** |
| **Relation between values and indices** | unrelated · values are a **permutation** of the indices · values bounded by `n` · values bounded by a small constant · values unbounded |
| **Where metadata goes when there is no room** | a **sign bit** · the high part of a cell, via a modulus · a **designated row or column** · nowhere — a counting argument replaces it |
| **What a bucket index means** | a value · a **derived count** (frequency) · a compressed rank |
| **Why bucketing is legal** | the key range is bounded · **pigeonhole** — the answer provably cannot lie *inside* a bucket |
| **Passes over the data** | one, streaming, cannot look back · one, building a hash map as you go · two, forward then backward · unbounded random access |
| **Is order meaningful** | yes, contiguity is part of the problem · **no, the array is really a multiset** |
| **What is returned** | a value · an index · the array **rearranged in place** · a grouping · a random sample |
| **Which assumption breaks** | index tricks need values in `[0, n)` · sign marking breaks on zero or negatives · packing breaks without headroom · **a naive shuffle is biased** · duplicates break "values are a permutation" |

## Shape of this topic

```
A1  The array as its own hash table          4 ideas
A2  Counting arguments that replace storage  3 ideas
A3  The hash map as one-pass memory          3 ideas
A4  Rearrangement under a global rule        1 idea
A5  Randomisation                            2 ideas
A6  The array as a container                 1 idea
                                             + 9 cross-listed ↗
```

**14 native entries, plus 9 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** #14 was added by the reverse sweep and sits inside A2.

## Named algorithms in this file

Curated lists and interviewers give you a **name**; the rest of this file is organised by *idea*. This table bridges the two, for the names this file actually owns. Anything array-shaped that is not here is owned by another file — the ↗ and exclusion tables below say which.

| The name you remember | Entry |
|---|---|
| Boyer–Moore voting · Moore's voting | #5 |
| Build Array from Permutation | #4 |
| Counting · bucket · radix sort | ↗ Sorting · #6 · #14 |
| **Cyclic sort** | #1 |
| Find All Duplicates in an Array | #1 · #2 |
| Find missing and repeating number | #1 · #3 |
| First Missing Positive | #1 |
| **Fisher–Yates shuffle** | #11 |
| Game of Life | #4 |
| Group Anagrams | #8 |
| Insert Delete GetRandom O(1) | #13 |
| Longest Consecutive Sequence | #9 |
| Majority Element *(n/2 and n/3)* | #5 |
| Maximum Gap | #6 |
| Missing Number | #3 |
| **Next Permutation** | #10 |
| Pigeonhole bucketing | #6 |
| Rearrange array by sign | #10 · #4 — a variation |
| **Reservoir sampling** | #12 |
| Set Matrix Zeroes | #2 |
| Single Number | #3 |
| Sign-bit marking | #2 |
| Top K Frequent Elements | #14 |
| **Two Sum** | #7 |

---

## A1 · The array as its own hash table

Four ways to store information you were told you had no room for.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | First Missing Positive | LC **41** | **When the values are (nearly) a permutation of the indices, put value `v` at index `v − 1` and the array becomes a lookup table.** Cyclic placement: repeatedly swap the current value to its home slot until the slot already holds it, then move on. Each swap places one element permanently, so despite the inner loop the whole thing is `O(n)` — that amortised argument is the content. After the pass, the first index whose value is wrong *is* the answer. This is cyclic sort, and it is the highest-yield `O(1)`-space idea for arrays. |
| 2 | Find All Numbers Disappeared in an Array | LC **448** | **Repurpose the input as scratch space.** You need a "have I seen `v`" bitset and have no memory, so negate `a[|v| − 1]` — the sign bit of each cell is a free flag, and the magnitudes stay readable. The same idea with a different hiding place solves Set Matrix Zeroes (LC **73**): use the **first row and column** as the marker storage, handling their own overlap as the one special case. New in general form: **if you may destroy the input, look for unused bits or an expendable region inside it.** Breaks on zeros and negatives, which is the check to state out loud. |
| 3 | Missing Number | LC **268** | **Compare an aggregate of the indices against an aggregate of the values.** Expected sum minus actual sum, or `XOR` of both ranges, gives the discrepancy in one pass and `O(1)` space with no mutation at all. New because nothing is stored *anywhere* — the arithmetic identity does the work — and XOR extends it to "everything appears twice except one" (LC **136**, [[Bit Manipulation]] #3). Watch overflow on the sum version, which is why XOR is usually the better answer. |
| 4 | Build Array from Permutation | LC **1920** | **Pack two numbers into one cell using the value range's headroom.** With all values `< n` you can store `old + n·new` in a single slot, complete the whole transform in one pass reading `a[i] % n` for old values, then divide out — `O(1)` space for an operation that appears to need a copy. Game of Life (LC **289**) is the same trick with two bits instead of two digits. The general statement is that **a bounded value range is unused capacity**, and it is the most reusable of the four because it does not need a permutation, only a bound. |

## A2 · Counting arguments that replace storage

| # | Problem | Source | The new idea |
|---|---|---|---|
| 5 | Majority Element | LC **169** | **Cancellation: pair off unlike elements and whatever survives is the majority.** Boyer–Moore keeps one candidate and one counter, and the proof is the entry — if an element occupies more than half the array, it cannot be fully cancelled by everything else combined. `O(1)` space with no hash map. The generalisation is what makes it a real idea rather than a trick: **`k − 1` counters find every element appearing more than `n/k` times**, which is LC **229** at `k = 3`, and it needs a verification pass because the counters can survive without qualifying. |
| 6 | Maximum Gap | LC **164** | **Pigeonhole: choose the bucket width so the answer cannot possibly lie inside a bucket.** With `n` values spanning `max − min`, some adjacent gap must be at least `(max − min)/(n − 1)`; bucket at exactly that width and any within-bucket gap is strictly smaller, so you only ever compare the maximum of one bucket against the minimum of the next. Linear time without sorting. Distinct from #14 below: there the bucket index is legal because the key range is *bounded*, here it is legal because of a **proof about where the answer can be**. |
| 14 | Top K Frequent Elements | LC **347** | **Bucket by a bounded *derived* quantity, not by the value.** A frequency can never exceed `n`, so `buckets[f]` is an array you are allowed to allocate even when the values themselves are unbounded — then walk it downward and stop after `k`. `O(n)`, beating the `O(n log k)` heap ([[Heap]] #3) that everyone reaches for first. New because the indexing axis is a *statistic of the data* rather than the data, and the same move sorts by frequency, finds the mode, and answers "which value occurs exactly `m` times". |

## A3 · The hash map as one-pass memory

> [!info] There is no Hashing file in the basis and probably should be. These three live here because an array is what you are usually sweeping, and because leaving Two Sum out of the entire basis would be absurd. See the audit.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Two Sum | LC **1** | **Store what you have seen as you go, and look up the complement rather than searching for it.** The move that turns `O(n²)` into `O(n)`, and the reason it is one pass rather than two is worth stating: by the time you ask for `target − a[i]`, the map holds exactly the elements before `i`, so every pair is considered once and no element pairs with itself. This is the base case of the whole "prefix as a lookup key" family ([[Prefix Sums & Difference Arrays]] #4) — same shape, with the running fold replaced by the raw element. |
| 8 | Group Anagrams | LC **49** | **A canonical form turns equivalence into dictionary lookup.** Two words are anagrams iff their sorted letters — or their 26-length count vector — agree, so mapping each item to that canonical key groups them in one pass. New because the key is *derived to erase exactly the distinction you want to ignore*, which is a modelling decision rather than a data-structure one, and the same idea identifies duplicate subtrees ([[Binary Trees]] #13) and duplicate folder structures ([[N-ary Trees]] #4). |
| 9 | Longest Consecutive Sequence | LC **128** | **Only start counting from an element that has no predecessor, and the total stays linear.** Dump everything in a hash set, then for each `v` check whether `v − 1` is absent — if so, walk upward counting. Without that guard the walks overlap and the bound collapses; with it, every element is visited by exactly one walk. New because the linearity comes from an **amortisation argument about which starts you allow**, not from the data structure, and the sorting solution at `O(n log n)` is the thing this beats. |

## A4 · Rearrangement under a global rule

| # | Problem | Source | The new idea |
|---|---|---|---|
| 10 | Next Permutation | LC **31** | **The lexicographic successor is fully determined by three local steps.** Scan from the right for the first index where `a[i] < a[i+1]` — everything after it is non-increasing and therefore already maximal, so the change must happen at `i`. Swap `a[i]` with the smallest value to its right that still exceeds it, then **reverse the suffix** to make it minimal. `O(n)`, `O(1)` space, and the reason it earns an entry is that each step answers a *why* — where the change must occur, which value must replace it, and why reversing is enough. Owning this gives you previous-permutation, permutation ranking, and the iterative way to enumerate all permutations without recursion. |

## A5 · Randomisation

Two classics with no other home, both about arrays and both about `O(1)` space.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Shuffle an Array | LC **384** | **Fisher–Yates: walk backwards and swap each position with a uniformly chosen index at or before it.** The entry is really the negative result — swapping each position with *any* random index produces `n^n` equally likely execution paths over `n!` outcomes, so the distribution cannot be uniform, and it is subtly biased in a way testing rarely catches. Knowing that the range must shrink, and being able to say why, is the whole question. `O(n)` time, in place. |
| 12 | Random Pick Index | LC **398** | **Reservoir sampling: keep the `k`-th qualifying element with probability `1/k` and you end up uniform over a stream whose length you never learned.** `O(1)` memory, one pass, no second look — which is the only option when the data does not fit or arrives live. The induction proof is short and is what is being tested. Generalises to reservoirs of size `k`, and it is the sampling counterpart to #5: both extract a global property from a stream you cannot store. |

## A6 · The array as a container

| # | Problem | Source | The new idea |
|---|---|---|---|
| 13 | Insert Delete GetRandom O(1) | LC **380** | **Swap the victim with the last element, then pop — deletion becomes `O(1)` without breaking contiguity.** An array gives `O(1)` random access, which is what `getRandom` needs and no hash map or tree can provide; its weakness is `O(n)` deletion from the middle, and the swap-with-last trick removes it at the cost of destroying order. Pair it with a hash map from value to index and all three operations are `O(1)`. New because it is the one entry about the array's own **performance profile** rather than about a puzzle, and "which container gives me random access *and* fast deletion" is a design question you will be asked directly. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Maximum Subarray | LC **53** | The best contiguous run, either as Kadane's recurrence or as "current prefix minus the smallest earlier prefix" — the same algorithm from two directions. [[Dynamic Programming]] #1 and [[Prefix Sums & Difference Arrays]] #9. |
| ↗ | Maximum Product Subarray | LC **152** | Carry **both** the running max and min, because one negative swaps them. The clean demonstration that a scan's state sometimes needs two components. [[Dynamic Programming]] basis. |
| ↗ | Remove Duplicates · Sort Colors · Merge Sorted Array | LC **26** · **75** · **88** | In-place rearrangement with read and write pointers: the finished-prefix invariant, three-way partition, and the write-from-the-back hazard. [[Two Pointers]] #10, #11, #12. |
| ↗ | Rotate Array | LC **189** | Rotation is three reversals, because `reverse(reverse(A)·reverse(B)) = B·A`. [[Two Pointers]] #13. |
| ↗ | Subarray Sum Equals K | LC **560** | Once the question is about *contiguous* sums with negatives allowed, it is a prefix-sum lookup, not an array trick. [[Prefix Sums & Difference Arrays]] #4. |
| ↗ | Longest Substring Without Repeating Characters | LC **3** | The maintained-interval sweep, which owns most of what gets tagged "array + hash map". [[Sliding Window]] #3. |
| ↗ | Find the Duplicate Number | LC **287** | Two `O(1)`-space answers on a read-only array: Floyd on the functional graph, or pigeonhole with binary search. The constraint "do not modify the input" is what rules out #1. [[Two Pointers]] #8, [[Binary Search]] #13. |
| ↗ | Counting · bucket · radix sort | *classic* | Sorting in `O(n)` when the domain is constrained — the general form of #6 and #14, including the prefix step that turns counts into positions ([[Prefix Sums & Difference Arrays]] #13). [[Sorting & Custom Comparators]] #3 and #4. |
| ↗ | Coordinate compression | LC **732** | When values reach `10⁹` but only `10⁵` of them occur, replace each by its rank and every bucketing idea above becomes available again. [[Segment Trees]] #11. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Find All Duplicates in an Array | LC **442** | #1 · #2 | Cyclic placement, or sign marking; both already here. |
| Set Mismatch | LC **645** | #1 | Cyclic placement, reporting the two mismatches. |
| Missing Number (sorting solution) | LC **268** | #3 | The aggregate identity is the point of the entry. |
| Single Number | LC **136** | #3 | XOR cancellation. [[Bit Manipulation]] #3 develops the family (including the two-survivor and mod-3 cases). |
| Game of Life | LC **289** | #4 | Two bits per cell instead of two digits. |
| Set Matrix Zeroes | LC **73** | #2 | Designated-region marking, named in the entry. |
| Majority Element II | LC **229** | #5 | Two counters, plus the verification pass. |
| Contains Duplicate | LC **217** | #7 | A hash set, one line. |
| Isomorphic Strings · Word Pattern | LC **205** · **290** | #8 | Canonical key, built as a bijection check. |
| Find All Anagrams in a String | LC **438** | #8 | Canonical key inside a fixed window — [[Sliding Window]] #2. |
| Sort Characters By Frequency | LC **451** | #14 | Bucket by count, read downward. |
| Kth Largest Element | LC **215** | #14 | Bucketing works when values are small; otherwise quickselect, [[Two Pointers]] #17. |
| Wiggle Sort | LC **280** | #10 | One pass of local swaps; the "a local fix is always safe" argument is a greedy idea. Greedy basis. |
| Non-decreasing Array | LC **665** | — | At most one repair, decided by a local comparison. Greedy basis. |
| Previous Permutation | LC **31** | #10 | The mirror of the same three steps. |
| Permutations (enumerate all) | LC **46** | — | Backtracking, not an array rearrangement. [[Backtracking]] #3; the recursion-free route via #10 is [[Backtracking]] #18. |
| Shuffle the Array | LC **1470** | #4 | Index interleaving; the `O(1)`-space version is the packing trick. |
| Linked List Random Node | LC **382** | #12 | Reservoir sampling on a list instead of an array. |
| Random Pick with Weight | LC **528** | — | Prefix sums plus binary search. [[Binary Search]] #20. |
| Design Underground System · Twitter | — | #13 | Container-choice design questions. Design basis. |
| Product of Array Except Self | LC **238** | — | Prefix and suffix folds. [[Prefix Sums & Difference Arrays]] #10. |
| Merge Intervals | LC **56** | — | Sort by start, then merge. Intervals basis. |
| Spiral Matrix · Rotate Image | LC **54** · **48** | — | Boundary shrinking and transpose-then-reverse. Matrix basis. |
| Flatten a 2D index to 1D (`r·cols + c`) | — | — | Arithmetic, not an idea. Used in [[Binary Search]] #16. |
| Best Time to Buy and Sell Stock | LC **121** | — | Running extreme against the current value. [[Prefix Sums & Difference Arrays]] #9. |

---

## Self-audit

**Borderline calls, and which way I went**

- **The file's scope, which is the only real decision here.** I could have written a 60-entry Arrays file by pulling back everything tagged `array`, and it would have duplicated five better-organised files. Instead the inclusion test was: *does this idea depend on the array being an array* — contiguous, randomly accessible, indices interchangeable with values? That test is what leaves 14 entries and 9 cross-lists, and it is why A1 is the largest family rather than something about scanning.
- **#2 absorbed Set Matrix Zeroes rather than splitting it out.** Sign-bit marking and first-row-and-column marking are one idea — hide metadata inside the input — with two hiding places. Splitting would have implied a distinction that is not there.
- **#6 and #14 kept separate**, and this is the split I am least sure of. Both bucket, both avoid a sort. Kept apart because the *licence* differs: #6 needs a proof that the answer cannot be inside a bucket, #14 only needs the key range to be bounded. When the reverse sweep produced both from different descriptions and they landed in the same family with different justifications, that confirmed the axis rather than the merge.
- **A3 exists under protest.** Two Sum, Group Anagrams and Longest Consecutive Sequence are hash-map ideas that happen to sweep an array, and a Hashing file would want all three. Included because omitting them entirely was worse than filing them imperfectly, and flagged below.
- **#11 and #12 kept despite being "trivia".** Both are asked, both are about `O(1)` space over a sequence, and both have a *proof* as their content — the bias argument and the induction — which is exactly what an interview is probing. Neither has another home.
- **#13 is a Design entry as much as an Arrays one.** Kept here because it is the only place the basis says out loud what an array is *good at*, which is the fact underlying every "which container" decision.
- **Quickselect not native here.** It partitions an array in place and has an equal claim, but it is built from converging pointers, so [[Two Pointers]] #17 owns it and this file cross-references it from #14's exclusion row.

**Naming check.** Three retitles. #1 was drafted as "First Missing Positive", which names a puzzle; it is now the index-value placement idea, with the amortisation argument foregrounded. #4 was drafted as "in-place permutation" and is now *pack two numbers into one cell using the value range's headroom*, since the headroom — not the permutation — is what transfers. #14 was drafted as "Top K Frequent" and is now *bucket by a bounded derived quantity*, because the derived-key insight is invisible under the problem's name. #10 was checked and kept: "next permutation" is a standard name that a differently-dressed version would still match.

**Step 4B — reverse sweep**

Thirty plain-language descriptions navigated against the family headings. **One failure:**

- **"Give me the `k` most common values, faster than sorting"** landed nowhere. A2's two entries were about cancellation and pigeonhole, neither of which fits; #1's index trick needs values bounded by `n`, and frequencies are bounded by `n` while the *values* need not be. That is **#14**, and it exposed two missing axes: *what a bucket index means* — a value or a **derived count** — and *why bucketing is legal* — a bounded key range or a pigeonhole proof. Both are now in the axis table, and adding them is what justified keeping #6 and #14 apart instead of merging them on sight.

Four collisions, all checked and cleared. "Numbers from 1 to n, one is missing, no extra space" reaches #1, #2 and #3, which is correct and instructive — three genuinely different `O(1)`-space techniques answer it, and the choice depends on whether you may mutate the input. "Set rows and columns to zero in place" reaches #2 as intended. "Element appearing more than half the time" reaches #5 and #14, correctly, since bucketing also works and Boyer–Moore is what wins on space. "Sort in linear time" reaches #6, #14 and the ↗ Sorting row, all correct.

**What I am uncertain about**

- **A Hashing basis is missing, and this file is currently absorbing it.** Complement lookup, canonical keys, frequency maps, seen-sets and the `O(1)`-average caveat are a real topic. Three entries here and several in [[Prefix Sums & Difference Arrays]] are squatting on it. **This is the clearest structural gap the file exposes.**
- **The boundary with Sorting.** Counting, bucket and radix sort are cross-listed, but #6 and #14 are bucketing ideas kept native. The cut is *bucketing to answer a question* versus *bucketing to produce sorted output*. **Reviewed when [[Sorting & Custom Comparators]] was written and upheld** — that file states the same boundary from its side in the same words, so the two descriptions cannot drift apart.
- **The boundary with Matrix.** #2 pulls Set Matrix Zeroes in and #4 pulls Game of Life in, both because the *space trick* is the lesson. A Matrix file will want both, which cross-listing handles, but the entries would need splitting if Matrix claims the traversal side.
- ~~**In-place merge of two sorted halves** in `O(1)` space — the gap or Shell method. Genuinely a different algorithm, excluded on scope: it is real but nobody asks for it.~~ **Wrong on the facts** — it is a standing Indian-interview follow-up to LC 88, and it is now [[Two Pointers]] #19.
- **The named-algorithm table covers only this file's own entries.** It started as a whole-array-chapter index spanning seven files and was cut back, because a cross-file index sitting inside one topic is a maintenance trap — every renumber elsewhere silently rots it, and the ↗ and exclusion tables below already carry those pointers with the reasoning attached. Building the wide version first still earned its keep: sweeping curated lists by *name* is what surfaced the gap method ([[Two Pointers]] #19), showed "leaders in an array" had no matchable row anywhere, and identified Intervals, Math and Matrix as the only array-chapter destinations with no file at all.
- **Recall is thinnest on A1.** The `O(1)`-space index-trick literature is scattered across blog posts rather than curated lists, so there is no good external enumeration to sweep against — the same problem [[Prefix Sums & Difference Arrays]] has on its difference-array side.

**Completeness confidence: ~87%**, the lowest in the basis, and for a structural reason rather than a sloppy one. Every other file is defined by a *mechanism*, so its boundary is self-evident; this one is defined by *what the other twelve files left behind*, so its boundary moves whenever a new file is written. Expect to re-cut it when Hashing, Sorting and Matrix exist.

## Related Notes

- [[README]]
- [[Two Pointers]]
- [[Sliding Window]]
- [[Prefix Sums & Difference Arrays]]
- [[Binary Search]]
- [[Heap]]
- [[Sorted Containers & Order Statistics]]
- [[Dynamic Programming]]
- [[Backtracking]]
- [[Math & Number Theory]]
- [[Linked List]]
- [[Sorting & Custom Comparators]]
- [[Bit Manipulation]]
