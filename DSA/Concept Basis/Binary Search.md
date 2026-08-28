---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-23
---
# Concept Basis — Binary Search

> [!abstract] Minimal spanning set for binary search. One entry per **new idea you have to learn**. Variations live in the exclusions table with the entry each collapses into. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## The reframe everything depends on

> [!danger] **Binary search is not "find a value in a sorted array." It is "find the boundary of a monotone predicate."**
>
> Write your predicate as a function `p(x) → bool` that is `false, false, …, false, true, true, …, true` over the search space. Binary search finds where it flips. Everything else in this file is a question of *what the search space is* and *how you compute `p`*.
>
> Once you hold it this way, the classic version is just the special case where the space is array indices and `p(i)` is `a[i] >= target`. And the reason "the array must be sorted" is a lie becomes obvious — sortedness is only ever a way of making `p` monotone, and there are others (#5, #6).

This file is unusually exclusion-heavy. That is correct: binary search generates more near-identical problems than any other topic, because *any* problem becomes a binary search problem once someone bolts "minimise the maximum" onto it.

## Mechanism axes

| Axis | Values |
|---|---|
| **What the search space is** | array indices · the answer's value range · the input's value range · a partition point · a window's start · an unbounded ray · a real interval |
| **How `p` is computed** | read one element · compare two adjacent elements · a counting sweep · a two-pointer pass · a whole traversal or DP |
| **Whether `p` decomposes** | **per-element, order-free** (sum a `ceil` over independent parts) · **sequential**, carrying state left to right · **clamped**, where each contribution is capped by the candidate itself |
| **Why `p` is monotone** | the array is sorted · feasibility only improves with more budget · counts only grow with a larger threshold · a structural invariant (parity, missing-count) |
| **What breaks monotonicity, and the repair** | rotation → case-split on which half is sorted · duplicates → no strict decision, degrade to `O(n)` · unimodal → ternary search |
| **What is returned** | the index · the boundary itself · the count of a range · the element at the boundary |
| **Termination** | exact on integers · fixed iteration count on reals · until the bracket is one element |

## Shape of this topic

```
B1  Mechanics — the boundary template   2 ideas
B2  Searching a broken or derived order 4 ideas
B3  Binary search on the answer         5 ideas
B4  Binary search on the value range    3 ideas
B5  Searching a structure               3 ideas
B6  Unbounded and continuous domains    2 ideas
B7  Binary search as a subroutine       2 ideas
                                        + 4 cross-listed ↗
```

**21 native entries, plus 4 cross-listed (↗).** See [[README]] on cross-listing.

---

## B1 · Mechanics — the boundary template

> [!warning] Off-by-one errors here cost more interview points than any algorithmic gap in this file. **Learn exactly one template and never write another.** The `while (l < r)` form with `l = mid + 1` and `r = mid` finds the first `true`, terminates with `l == r`, and needs no post-loop adjustment. Use `mid = l + (r - l) / 2` so large indices cannot overflow, and be aware that biasing `mid` upward is required if a branch ever does `l = mid`.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | First Bad Version | LC **278** | **The predicate is the whole thing.** There is no array, no sortedness and no target — only a monotone boolean and a boundary to find. Starting here rather than at LC 704 is deliberate: the general case is *easier* to hold than the special case, and every other entry is this with a different `p`. |
| 2 | Find First and Last Position of Element in Sorted Array | LC **34** | **Lower bound and upper bound as a pair.** `lower_bound` is "first `≥ x`", `upper_bound` is "first `> x`", and every derived query is one of them: the count of `x` is their gap, the insert position is the first, successor and predecessor are one step off either, and **the closest value to `x` is `lower_bound` compared against the element just before it** — always check both sides, since the boundary can land on either. Two calls to #1's template with different predicates — no new loop, ever. |

## B2 · Searching a broken or derived order

The array is not sorted, or not sorted in the thing you care about. Each entry is a different way of manufacturing a monotone predicate anyway.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Search in Rotated Sorted Array | LC **33** | **One half is always sorted.** Compare `a[l]` with `a[mid]` to find out which, then check whether the target lies inside that sorted half. Monotonicity is broken globally but survives locally, and the case split is how you exploit that. |
| 4 | Search in Rotated Sorted Array II | LC **81** | **Duplicates destroy the decision.** When `a[l] == a[mid] == a[r]` you cannot tell which half is sorted, so you shrink by one and the worst case degrades to `O(n)`. The real lesson is general: binary search needs a **strictly** decidable comparison, and knowing when you have lost that is what stops you writing a subtly wrong solution. |
| 5 | Find Peak Element | LC **162** | **Binary search with no sortedness whatsoever.** Compare `a[mid]` with `a[mid+1]`: if you are ascending, a peak exists to the right; if descending, to the left. The invariant "a peak exists in `[l, r]`" is maintained by a purely local comparison. This is the entry that kills "binary search requires a sorted array" for good. |
| 6 | Single Element in a Sorted Array | LC **540** | **The monotone quantity is structural, not stored.** Before the singleton, pairs sit at `(even, odd)` indices; after it, they sit at `(odd, even)`. So `p(i) = "the pairing has already broken by index i"` is monotone even though nothing in the array is. Whenever the array itself refuses to be monotone, ask what *derived* quantity is. |

## B3 · Binary search on the answer

> [!tip] Recognition signal: **"minimise the maximum," "maximise the minimum," or "the smallest `x` such that…"** — plus a way to *check* an answer that is much easier than *finding* one. The family is defined by the search space being the answer, not by how `p` is computed.
>
> **Classify by the shape of the check, because that is what actually varies:**
> - **Decomposable** — each element contributes `f(element, x)` independently, and you sum. Order-free, usually a `ceil` division. Entries #7 and #10.
> - **Sequential greedy** — you sweep left to right carrying state, and the answer depends on order. Entries #8 and #9.
> - **Clamped or capped** — decomposable, but each contribution is bounded by `x` itself, as in `sum(min(bᵢ, t)) ≥ n·t`. A wrinkle on decomposable, not a family.
> - **A whole algorithm** — a BFS, a DP, or a backtracking search. See the ↗ entries.
>
> Setting the initial bracket is its own small skill and is where most wrong answers come from: `lo` is usually the largest single element (or `0`), `hi` is the total (or the widest span). Too tight and you exclude the answer; too loose only costs a few iterations, so err wide.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Koko Eating Bananas | LC **875** | **Search the answer space itself.** Guess a speed, check feasibility in `O(n)`, and the fact that feasibility only improves as speed rises is the entire licence for the search. Arguably the most important single idea in this file, because it converts optimisation problems into decision problems, and decision problems are usually easy. |
| 8 | Split Array Largest Sum | LC **410** | **Minimise the maximum, with a sequential greedy check.** "Can I do it in `k` pieces with no piece exceeding `x`?" is answered by one left-to-right sweep that starts a new piece the moment it would overflow. Unlike #7, the check carries state and cannot be decomposed per element. Also the canonical problem with *both* a DP and a binary-search solution — see ↗ below. |
| 9 | Magnetic Force Between Two Balls | LC **1552** | **Maximise the minimum, which flips everything.** The predicate is now "is `x` still achievable," true for small `x` and false for large, so you keep the *right* half and your template's roles reverse. Kept separate from #8 deliberately: nobody gets the boundary right on their first maximise-the-minimum problem, however solid they are on minimise-the-maximum. |
| 10 | Minimum Limit of Balls in a Bag | LC **1760** | **Spend a budget of `k` operations across independent segments.** Some items are already fixed, and you may subdivide them `k` more times in total — minimise the largest resulting piece. For a candidate `x`, each segment independently needs `ceil(size / x) - 1` cuts, so you sum the costs and compare to `k`. Two things are new: **the segments do not interact**, so the check is pure arithmetic with no sweep, and **the answer is a budget allocation** rather than a partition. This is the shape behind "`n` gas stations are already placed, add `k` more, minimise the largest gap" (LC 774) and every "distribute to minimise the worst share" problem. |
| 11 | Minimize Max Distance to Gas Station | LC **774** | **Real-valued search.** #10's problem, moved onto the reals: stations may go anywhere, so there is no next integer to converge onto. Termination changes completely — run a fixed ~100 iterations, or loop while `r - l > eps`, and never compare floats for equality. Pick `eps` from the required precision rather than by feel, and watch that the `ceil` in #10's check becomes a floating-point division that can round the wrong way. Listed separately from #10 on purpose: the modelling is identical and the code is not, which is exactly the pairing that catches people out. |

## B4 · Binary search on the value range

Formally a special case of B3, kept separate because the recognition signal is completely different: **"find the `k`-th smallest of a collection too large to build."** You search the range of possible values and count how many fall below the midpoint.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 12 | Kth Smallest Element in a Sorted Matrix | LC **378** | **Binary search the value range, count with a predicate.** `p(v) = "at least k elements are ≤ v"` is monotone, and the boundary is guaranteed to be an actual element of the matrix — which is the part that feels like cheating and is worth convincing yourself of. The `n × n` collection is never materialised. |
| 13 | Find the Duplicate Number | LC **287** | **Counting plus pigeonhole on the value range.** Count how many values are `≤ mid`; if that exceeds `mid`, the duplicate is in the lower half. Read-only, `O(1)` space, array untouched. Floyd's cycle detection is the faster alternative and you should know both, since the constraint "do not modify the array" is what the interviewer is really testing. |
| 14 | Find K-th Smallest Pair Distance | LC **719** | **The counting predicate is itself a linear scan.** Counting pairs with distance `≤ v` needs a two-pointer sweep over the sorted array, giving `O(n log n + n log W)`. The composition is the lesson: your `p` may be an entire algorithm, and its cost multiplies into the total. |

## B5 · Searching a structure

The search space stops being a line.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 15 | Median of Two Sorted Arrays | LC **4** | **Search a partition, not a value.** Choose how many elements to take from `A`; the cut in `B` is then forced by the size requirement. Check the four elements straddling the two cuts and shift. You never search for the answer at all — you search for the *split that makes the answer readable*, which appears nowhere else in this file. Binary search over the shorter array for `O(log min(m, n))`. |
| 16 | Search a 2D Matrix · Search a 2D Matrix II | LC **74** · **240** | **Two matrices that look identical and are not.** In 74 the rows chain, so row-major flattening gives a genuine 1D sorted array and one binary search works. In 240 rows and columns are sorted independently and no flattening exists — instead you walk from the **top-right corner**, eliminating one row or one column per step in `O(m + n)`. Recognising which structure you have is the entire problem. ↗ from [[Matrix]]; the flatten is [[Matrix]] #1. |
| 17 | Find K Closest Elements | LC **658** | **Binary search over window starts.** The predicate compares two elements a fixed distance apart — `a[mid]` against `a[mid + k]` — rather than one element against a target. Once you see the answer as "which window," the search space is `n - k` positions and the comparison is what makes it monotone. |

## B6 · Unbounded and continuous domains

| # | Problem | Source | The new idea |
|---|---|---|---|
| 18 | Search in an unbounded / infinite sorted array | *classic — the `ArrayReader` interview variant* | **Exponential (galloping) search.** With no known length, double the index until you overshoot the target, then binary search the last bracket `[i/2, i]`. Costs `O(log p)` in the answer's *position* rather than the array's size. The same doubling is how you search a paginated API or a stream, and how `std::vector` amortises growth. |
| 19 | Best Position for a Service Centre | LC **1515** | **Ternary search — binary search's sibling for unimodal functions.** When `p` is not monotone but the *function* has a single peak or valley, compare two interior points and discard the outer third on the losing side. Binary search needs monotone; ternary needs unimodal, and knowing the distinction stops you applying the wrong one. *Tail scope — rare outside contests and quant rounds.* |

## B7 · Binary search as a subroutine

Here the binary search is trivial and the *setup* is the idea.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 20 | Random Pick with Weight | LC **528** | **Build a monotone array so that a search answers a non-search question.** Prefix sums turn weighted sampling into `lower_bound(random(0, total))`, because the gap each index occupies on the number line is exactly its weight. The general move — *manufacture* monotonicity where none was given — is what makes binary search apply to problems that are not about searching. |
| 21 | Successful Pairs of Spells and Potions | LC **2300** | **Sort once, then binary search per query.** `O((n + m) log n)` instead of `O(nm)`. Trivial in isolation, listed because the offline "sort the thing you will query repeatedly" reflex is worth having explicitly, and because it is the shape behind Time Based Key-Value Store, Snapshot Array and most autocomplete questions. |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them while studying binary search and they belong here. See [[README]] on cross-listing.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Longest Increasing Subsequence | LC **300** · **354** | **Patience / tails array.** Binary search replaces the `O(n)` inner scan of the LIS DP, giving `O(n log n)`. The subtlety worth carrying back here is that the tails array is *not* an LIS — it is only the right length — which is a good reminder that a binary search can be correct for the answer without the structure meaning what you assume. [[Dynamic Programming]] #52. |
| ↗ | Split Array Largest Sum (DP form) | LC **410** | The same problem as #8 solved as a prefix-partition DP in `O(n²k)`. Doing both back to back is the cleanest demonstration of when a monotone answer makes an entire DP unnecessary. [[Dynamic Programming]] #31. |
| ↗ | Path With Minimum Effort | LC **1631** | Binary search on the answer where the **check is a BFS** — "is the target reachable using only edges ≤ `x`?" A genuine alternative to bottleneck Dijkstra, and the clearest case of `p` being a whole graph traversal. [[Graphs]] #22. |
| ↗ | Kth Ancestor of a Tree Node | LC **1483** | **Binary lifting** — decomposing `k` into powers of two and jumping. Not binary search, but the same halving intuition on a precomputed doubling table, and it is where that intuition pays off outside a sorted array. [[Graphs]] #29. |

---

## Excluded as variations

> [!warning] This is the longest exclusion table in the basis, and deliberately so. B3 alone could absorb fifty LeetCode problems: pick any quantity, ask for its minimum maximum, and you have "a new problem" that teaches nothing. Do two or three from this table for fluency, not twenty.

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Binary Search | LC **704** | #1 | The special case. Do it once to check your template compiles. |
| Guess Number Higher or Lower | LC **374** | #1 | LC 278 with the predicate inverted. |
| Search Insert Position | LC **35** | #2 | `lower_bound`, named differently. |
| Find Smallest Letter Greater Than Target | LC **744** | #2 | `upper_bound` with a wraparound at the end. |
| Count Negative Numbers in a Sorted Matrix | LC **1351** | #2 | One boundary search per row. |
| Find Minimum in Rotated Sorted Array | LC **153** | #3 | Same case split, looking for the pivot instead of a target. |
| Find Minimum in Rotated Sorted Array II | LC **154** | #4 | The duplicate degradation again. |
| Peak Index in a Mountain Array | LC **852** | #5 | LC 162 with a guarantee of exactly one peak. |
| Find in Mountain Array | LC **1095** | #5 | Peak-find, then two searches, under a call-count budget. |
| Find a Peak Element II | LC **1901** | #5 | Binary search columns, scanning each for its maximum. Genuinely harder, but the invariant argument is #5's. |
| Missing Element in Sorted Array | LC **1060** | #6 | `missing(i) = a[i] - a[0] - i` is the derived monotone quantity. Same move as #6, different derivation. |
| Kth Missing Positive Number | LC **1539** | #6 | LC 1060 anchored at 1. |
| H-Index II | LC **275** | #6 | The predicate compares an element to its own index — again a derived quantity. |
| Sqrt(x) · Valid Perfect Square | LC **69** · **367** | #7 | The smallest binary-search-on-answer there is. |
| Arranging Coins | LC **441** | #7 | Answer search with a closed-form check. |
| Capacity To Ship Packages Within D Days | LC **1011** | #7 | Koko with boxes. Literally the same code. |
| Find the Smallest Divisor Given a Threshold | LC **1283** | #7 | Koko with division. |
| Minimum Number of Days to Make m Bouquets | LC **1482** | #7 | Answer search with a run-length check. |
| Minimum Time to Complete Trips | LC **2187** | #7 | Answer search with a summation check. |
| Nth Magical Number | LC **878** | #7 | Answer search where the count uses inclusion–exclusion and an LCM. |
| Preimage Size of Factorial Zeroes Function | LC **793** | #7 | Answer search over a monotone counting function. |
| Maximum Value at a Given Index in a Bounded Array | LC **1802** | #7 | Answer search where `p` is `O(1)` arithmetic rather than a scan. |
| Maximum Number of Removable Characters | LC **1898** | #7 | Answer search where `p` is a subsequence check. |
| Minimize the Maximum Difference of Pairs | LC **2616** | #8 | Minimise-the-maximum with a greedy pairing check. |
| Aggressive Cows · Allocate Books · Painter's Partition | SPOJ / classic | #8, #9 | The interview-classic phrasings of #8 and #9. Worth knowing the names; they are not extra ideas. |
| Divide Chocolate | LC **1231** | #9 | Maximise-the-minimum with a greedy scan. |
| Find Minimum Time to Finish All Jobs | LC **1723** | #9 | Maximise-the-minimum where `p` is a backtracking search. Notable only for how expensive `p` is allowed to be. |
| Minimized Maximum of Products Distributed to Any Store | LC **2064** | #10 | Budget allocation across independent segments, `sum(ceil(qty / x)) ≤ n`. Identical to #10. |
| Maximum Candies Allocated to K Children | LC **2226** | #10 | The maximise-the-minimum face of #10; the check is `sum(pile / x) ≥ k`. |
| Maximum Running Time of N Computers | LC **2141** | #10 | Decomposable check with a **clamp** — `sum(min(bᵢ, t)) ≥ n·t`, because no battery can contribute more than the runtime itself. The clamp is a genuine wrinkle worth meeting once; it is not a separate idea. |
| Divide Array Into Arrays / Cutting Ribbons | classic | #10 | Same `ceil`-and-sum allocation check. |
| K-th Smallest Prime Fraction | LC **786** | #12 | Value-range search over fractions, counting with two pointers. |
| Time Based Key-Value Store | LC **981** | #21 | `upper_bound` inside a hash bucket. The versioning model is [[Design]] #4. |
| Snapshot Array | LC **1146** | #21 | Same search, indexed by version. The COW-vs-history model is [[Design]] #5. |
| Search Suggestions System | LC **1268** | #21 | Sort, then `lower_bound` per prefix. A trie is the better answer — [[Tries]] #6. |
| Kth Smallest Element in a BST | LC **230** | [[Binary Search Trees]] #2 | In-order traversal with a counter. Not a binary search despite the tree. |
| Count of Smaller Numbers After Self | LC **315** | [[Segment Trees]] | Needs a BIT or merge sort, not a binary search. |
| Closest Subsequence Sum | LC **1755** | #21 | Binary search as the **join step** in meet in the middle: enumerate both halves, sort one, then look up each half-sum's best partner. The search is a subroutine, not the algorithm. [[Backtracking]] #19. |
| "First prefix whose sum exceeds `x`", with updates | *classic* | — | **The trap worth knowing.** If your predicate is itself a range query, a binary search over it costs `O(log² n)` — descend the segment tree instead and pay `O(log n)`. So the reflex "the predicate is monotone, therefore binary search" is right about *what* and wrong about *how*. [[Segment Trees]] #8. |

---

## Self-audit

**Borderline calls, and which way I went**

- **B4 split out from B3.** Both search a value range with a monotone predicate, so under a strict reading they merge. Kept apart because the recognition signals share nothing: B3 says "minimise the maximum," B4 says "find the `k`-th smallest thing I cannot build." Merging them would produce a family you cannot recognise your way into. ==Fold them if that distinction stops feeling real.==
- **Minimise-the-maximum (#8) and maximise-the-minimum (#9) kept separate.** The code is nearly identical and the boundary handling is not. This is the same reasoning that keeps directed and undirected cycle detection split in [[Graphs]] — a failed transfer earns an entry.
- **Single Element in a Sorted Array (#6) chosen over Missing Element (LC 1060).** Both teach "derive the monotone quantity." Picked 540 because the parity invariant is more surprising and therefore stickier; 1060 is one line in the exclusions away if you prefer it.
- **Find the Duplicate Number (#13) kept as a binary search entry** even though Floyd's cycle detection is the intended solution. The counting-on-the-value-range framing is what generalises, and the cycle solution belongs to Linked List.
- **Ternary search (#19) included at all.** Right on the scope boundary and marked tail. Included because the monotone-versus-unimodal distinction is worth holding even if you never code it.
- **Search a 2D Matrix II (#16) is arguably not binary search.** The staircase walk is `O(m + n)` and halves nothing. Kept in the pair because the interview value is entirely in telling 74 and 240 apart.
- **#10 and #11 are the same problem twice**, once on integers and once on the reals. Knowingly allowed, and the second time in this basis that a deliberate near-duplicate has earned its place (the other is Coin Change II versus Combination Sum IV in [[Dynamic Programming]]). The modelling transfers perfectly and the code does not, which is precisely when a pair teaches more than either half.

**Found by probing this file after it was written**

> [!question] The probe was: *"is the 'x items are already placed, insert k more, minimise the largest gap' family in here?"*
>
> The answer was **yes but framed wrongly**, which is a more interesting failure than a miss. LC 774 was present as a single entry called "real-valued search" — filed by its *domain* rather than by its *idea*. The budget-allocation modelling, which is the transferable part and which appears on integers in LC 1760, LC 2064 and LC 2226, was invisible: nothing in the file would have let you recognise those three as the same problem.
>
> Two fixes followed. The allocation idea became #10 in its own right with an integer canonical, and real-valued termination stayed as #11 where it belongs. Separately, the B3 preamble now classifies the family by **the shape of the check** — decomposable, sequential greedy, clamped, or a whole algorithm — since that is the axis that actually varies and it was missing from the axis table.
>
> **The generalisable lesson: filing an entry under an incidental property of its canonical problem hides the idea.** A near-miss like this will not show up in a completeness count, because the entry exists. Worth re-reading the other files with the question "is this entry named after its idea or after its problem's surface?"

**Step 4B — reverse sweep**

Twenty-six plain-language descriptions were navigated against the family headings. **No failures.** This is the only file besides [[Segment Trees]] to pass clean, and the reason is simply that it had already been through the procedure informally — the gas-station probe *was* a step 4B run, conducted by hand before the step existed. The two fixes it produced (#10, #11) are what a clean pass now reflects.

One near-miss worth recording: **"find the value closest to `x`"** resolves only if you already know it is `lower_bound` plus a comparison against the neighbour. #2's text now needs to say so; it is a naming thinness rather than a missing idea.

**What I am uncertain about**

- **Maintaining a sorted container incrementally** — `bisect.insort`, C++ `multiset`, order-statistic trees — was flagged here as a homeless gap, and being flagged twice (also in [[Heap]]) is what argued for promoting it. **Now closed:** it is [[Sorted Containers & Order Statistics]], and the lattice for choosing between a heap, an ordered set, a BIT and a segment tree is that file's opening section. Left in the audit as evidence that *the same footnote appearing in two files is a topic asking to be written.*
- **Parallel binary search** (answering many queries by binary searching all of them simultaneously) — excluded as competitive-programming scope. Moderate confidence; it does occasionally surface at Google.
- **Fractional cascading** — excluded, high confidence.
- **Binary search descending a segment tree** — deliberately deferred to [[Segment Trees]], where the structure is defined.
- **The B3 exclusion table is where a wrong merge would hide.** Eighteen problems now collapse into #7, #8, #9 and #10. I am confident that is right, but it is eighteen chances to be wrong — and the probe above found that the risk in this family is mis-*naming* rather than wrong merging.

**Completeness confidence: ~93%**, unchanged. The probe found a framing error rather than a missing concept, so the count moves but the confidence does not. The concept space here is small and well-explored; the risk in this topic is not missing ideas but *over-collecting problems*, which is why the exclusions run longer than the entries.

## Related Notes

- [[README]]
- [[Segment Trees]]
- [[Dynamic Programming]]
- [[Graphs]]
- [[Heap]]
- [[Binary Search Trees]]
- [[Sliding Window]]
- [[Two Pointers]]
- [[Math & Number Theory]]
- [[Linked List]]
- [[Greedy]]
- [[Strings]]
- [[Design]]
- [[Matrix]]
