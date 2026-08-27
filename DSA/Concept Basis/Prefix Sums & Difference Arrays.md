---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Prefix Sums & Difference Arrays

> [!abstract] Minimal spanning set for **precompute-in-one-pass-then-consume** techniques: prefix and suffix folds, prefix-as-a-hash-key, positional tables like last-seen and next-occurrence, and the difference-array dual. One entry per **new idea you have to learn**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## The two halves, and why they are the same idea

> [!tip] **Prefix and difference are inverse operations, and each one buys you a different asymmetry.**
>
> - **Prefix** — fold left to right so `P[i]` summarises everything before `i`. You now get **cheap range reads** (`O(1)`) and expensive writes (any update invalidates the whole tail).
> - **Difference** — deposit deltas at boundaries and fold *once at the end*. You now get **cheap range writes** (`O(1)`) and no reads at all until the fold happens.
>
> So the question is never "should I use prefix sums". It is: **am I reading ranges many times, or writing ranges many times?** Both cheap simultaneously is exactly what you cannot have here, and needing both is the signal to reach for a BIT ([[Segment Trees]] #2).

> [!info] **The generalisation this file is really about.** Nothing above mentions addition. The move is *fold something across the array in one sweep, store it, and let a later query read it in `O(1)`* — and what you fold can be a sum, an XOR, a bitmask of parities, a running extreme, or **the index of the last time you saw something**. Prefix sums are the famous special case; P5 and P6 are where the idea earns its keep.

## Mechanism axes

| Axis | Values |
|---|---|
| **What the fold accumulates** | a sum · a product · an XOR · a count of a property · a **running extreme** · a frequency vector · a **bitmask of parities** · a hash · **an index (last seen, next occurrence)** |
| **Direction of the pass** | left to right · right to left · **both, combined per index** · both, combined at a scanned **split point** |
| **Is the operator invertible?** | yes (sum, XOR, count) → arbitrary ranges by subtraction · **no** (min, max, gcd, OR) → **prefix-only reads still work**, arbitrary ranges do not |
| **What the index is keyed by** | position · **a value** (counting/bucket) · a residue class · a compressed coordinate · a timestamp · a `(row, col)` pair · a tree node's depth or Euler position · **the answer space** |
| **How the prefix is consumed** | subtract two known prefixes · **look up a needed earlier prefix in a hash map** · compare against the extreme earlier prefix · **count earlier prefixes in a range** · feed a DP |
| **What the lookup asks for** | **equality** — a hash map suffices · **inequality or a range** — needs an ordered / order-statistic structure |
| **What the map stores** | a **frequency** (to count ranges) · the **earliest index** (to maximise length) · the latest index (to minimise) |
| **Read/write discipline** | many reads, no writes · many writes, one read · **interleaved** → not this topic |
| **Dimensionality** | 1D · 2D (inclusion–exclusion) · on a tree (root-to-node, Euler tour) |
| **Which assumption breaks** | subtraction needs an inverse · **binary searching a prefix array needs non-negative values** · one interleaved update kills the whole approach · a difference array answers nothing before its final fold · floating-point prefixes drift · a rolling hash collides |

## Shape of this topic

```
P1  The fold and the range query           3 ideas
P2  The prefix as a lookup key             6 ideas
P3  Against the extreme earlier prefix     1 idea
P4  Two directions combined                3 ideas
P5  What the index is keyed by             2 ideas + 1 ↗
P6  Precomputed positional tables          2 ideas + 2 ↗
P7  Difference arrays — the dual           6 ideas
P8  When neither works                     0 ideas + 3 ↗
```

**23 native entries, plus 6 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** #23 was added by the reverse sweep and sits inside P2, where it belongs.

---

## P1 · The fold and the range query

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Range Sum Query — Immutable | LC **303** | **Fold once, then every range costs a subtraction.** `P[i+1] = P[i] + a[i]`, and `sum(l, r) = P[r+1] − P[l]`. Two things matter more than the code: keep the **sentinel `P[0] = 0`** so `l = 0` needs no special case, and notice the hidden precondition — subtraction only recovers a range because addition has an **inverse**. Every entry in this file is that sentence pushed somewhere new. |
| 2 | XOR Queries of a Subarray | LC **1310** | **Any invertible operator works, not just addition.** XOR is the cleanest case because it is its own inverse, so `xor(l, r) = P[r+1] ^ P[l]` with no sign to track. The same slot takes multiplication (with a zero-count kept separately, since zero has no inverse) and — most usefully — **a count of any property, which is just a prefix sum of a 0/1 array**: how many vowels, how many evens, how many days below freezing. Recognising "count of X in a range" as a prefix sum is worth more than the XOR itself. |
| 3 | Range Sum Query 2D — Immutable | LC **304** | **Inclusion–exclusion in two dimensions: four terms, not two.** `S[r][c]` holds the sum of the rectangle from the origin, and a query subtracts the strip above and the strip left, then **adds back** the corner counted twice. The correction term is the whole idea, and it generalises — `d` dimensions need `2^d` terms, which is why nobody does this past 2D. Composing it with #4 gives "count submatrices summing to a target" (LC 1074), the standard hard version. |

## P2 · The prefix as a lookup key

The largest family, and the conceptual jump. Above, you knew both endpoints and subtracted. Here **you do not know where the range starts**, so instead of computing a prefix you *search* for the one you need.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 4 | Subarray Sum Equals K | LC **560** | **Store every prefix sum in a hash map and ask whether `P[r] − k` has occurred.** Each earlier occurrence is one valid subarray ending here, so the map's **value is a frequency** and the answer accumulates a count. This is the single most important entry in the file, because it is what you use when a sliding window is illegal — negatives break monotonicity, so there is no window to shrink ([[Sliding Window]] #14). Seed the map with `{0: 1}` so subarrays starting at index 0 are counted. |
| 5 | Maximum Size Subarray Sum Equals k | LC **325** | **Store the *earliest index* instead of a frequency, and you get the longest range instead of a count.** Same lookup, different bookkeeping — and one rule that decides correctness: **never overwrite an existing key**, because a later occurrence of the same prefix can only produce a shorter subarray. Split from #4 deliberately: the probe "what is stored, and where is the answer read from" gives different answers, and choosing the wrong one is a silent wrong answer rather than a crash. |
| 6 | Subarray Sums Divisible by K | LC **974** | **Key the map by an equivalence class, not by the value.** Two prefixes with the same remainder mod `k` bound a subarray divisible by `k`, so the key is `P mod k` and you never store the sums at all. New because it collapses infinitely many prefix values into `k` buckets, which is what makes the technique work when only a *relation* between endpoints matters. Watch the language: `-3 % 5` is negative in C++, Java and Go, so normalise with `((x % k) + k) % k`. |
| 7 | Contiguous Array | LC **525** | **Re-encode the values so the property you want becomes a sum.** "Equal numbers of 0s and 1s" is not a sum until you map `0 → −1`; then it is exactly "the prefix sum repeats", and #5 finishes it. The encoding step is the idea and it is highly transferable — equal counts of two characters, "as many opens as closes", balanced +1/−1 votes. **Ask of any counting property: can I make it arithmetic?** |
| 8 | Find the Longest Substring Containing Vowels in Even Counts | LC **1371** | **When you need several parities at once, fold them into a bitmask.** Five vowels means five parity bits, so the prefix state is a 5-bit integer and two positions with the same mask bound a substring where every vowel appears an even number of times. New because the accumulated object is a **vector compressed to a scalar** — 32 states rather than 32 counters — and the same trick handles "each of these `k` things appears an even number of times" for any small `k`. |
| 23 | Count of Range Sum | LC **327** | **When the lookup is an inequality rather than an equality, a hash map is not enough.** Counting subarrays with sum in `[lo, hi]` means asking "how many earlier prefixes lie in `[P[r] − hi, P[r] − lo]`" — a **range count over prefix values**, which a hash map cannot answer. You need an order-statistic structure: a value-indexed BIT over compressed prefix sums ([[Segment Trees]] #3), a merge-sort pass, or a balanced multiset. **The diagnosis is the entry**: equality → hash map, inequality → ordered structure. The same split decides "count subarrays with sum ≥ k" and inversion counting. |

## P3 · Against the extreme earlier prefix

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Maximum Subarray | LC **53** | **Compare the current prefix against the *best* earlier prefix, not a specific one.** `maxSubarray = max over r of (P[r] − min over l < r of P[l])`, so one scan carrying a running minimum solves it — and the same sentence with prices instead of sums *is* Best Time to Buy and Sell Stock (LC 121). Distinct from P2: there you searched for an exact partner in a map, here you only ever need the extreme one, so the map collapses to a **scalar** and the space drops to `O(1)`. This reframing is also the honest explanation of why Kadane's recurrence works ([[Dynamic Programming]] #1) — and it makes the circular variant fall out as `total − minSubarray`. |

## P4 · Two directions combined

| # | Problem | Source | The new idea |
|---|---|---|---|
| 10 | Product of Array Except Self | LC **238** | **Fold from the left, fold from the right, combine at each index.** The answer at `i` depends on everything on both sides, and two passes supply both without division — which matters because division dies on zeros, so the two-pass version is the *only* general solution, not merely a tidier one. The canonical shape for any "answer for every index, given everything else". |
| 11 | Trapping Rain Water | LC **42** | **Prefix-only reads do not need an invertible operator.** `prefixMax` and `suffixMax` arrays give `min(maxLeft[i], maxRight[i]) − h[i]` per index — and max has no inverse, which would have made arbitrary range queries impossible. It does not matter, because you never subtract two maxima; you read a prefix value directly. **Knowing exactly where invertibility is required, and where it is not, is what this entry is for** — it is the qualifier on #1 that most people carry incorrectly. |
| 12 | Best Time to Buy and Sell Stock III | LC **123** | **Scan the split point: best answer on the prefix plus best answer on the suffix.** Build `left[i]` = best single transaction in `[0, i]` and `right[i]` = best in `[i, n)`, then maximise their sum over every cut. New because the combined object is a **partition of the array** rather than a per-index value, so the scanned quantity is the boundary itself. The shape behind "two non-overlapping subarrays", "delete one element then take the best run", and LC 1653-style balancing problems. |

## P5 · What the index is keyed by

Two entries where the array you fold over is not the input array.

| #   | Problem                                              | Source                                                                   | The new idea                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| --- | ---------------------------------------------------- | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 13  | How Many Numbers Are Smaller Than the Current Number | LC **1365**                                                              | **Fold over the *value* axis instead of the position axis.** Tally each value into a bucket, prefix-sum the buckets, and `count[v]` is now "how many elements are `< v`" for every `v` at once — `O(n + V)` with no sorting and no comparisons. This is counting sort's second step standing alone, and it is the static, `O(1)`-query answer to rank questions that [[Segment Trees]] #3 solves dynamically. When the values are large or sparse, compress them first (↗ below).                                                                                             |
| 14  | Path sums on a tree                                  | *classic — the tree form of #1; practise on LC **1123** / CSES **1138*** | **Root-to-node prefix sums plus an LCA give any path sum by subtraction: `d[u] + d[v] − 2·d[lca]`.** The tree is not linear, so "prefix" means "accumulated from the root", and the double subtraction is because the shared segment above the LCA is removed from both. Two companions worth holding alongside it: an **Euler tour** turns every subtree into a contiguous range so subtree sums become #1 ([[Binary Trees]] #15), and prefix-sum *counting* on a downward path needs the map undone on the way out ([[Binary Trees]] #10). LCA machinery is [[Graphs]] #29. |
| ↗   | Coordinate compression                               | LC **732** · **699**                                                     | When the axis is timestamps or coordinates up to `10⁹`, you cannot allocate it — collect the endpoints that actually occur, sort, dedupe, and index by rank. The prerequisite for using any of this on real data, and the bridge to sweep-line problems. [[Segment Trees]] #11.                                                                                                                                                                                                                                                                                               |

## P6 · Precomputed positional tables

> [!tip] **The fold does not have to produce a number — it can produce an *index*.** "Where did I last see this", "where is the next occurrence of that", "where is the nearest smaller element" are all built by one pass and consumed in `O(1)` later, and they are the same technique as a prefix sum wearing different clothes. This is the family people never file under prefix sums and then reinvent badly.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 15 | Partition Labels | LC **763** | **Precompute the last occurrence of each symbol, then sweep, extending a boundary.** One backward pass (or one dictionary pass) gives `last[c]`; then scanning forward and keeping `reach = max(reach, last[s[i]])` closes a partition exactly when `i == reach`. New because the precomputed table holds **positions**, so the later query is "how far right am I forced to extend" rather than "what is the total here". The general shape behind interval-merging-by-scan and greedy segmentation. |
| 16 | Number of Matching Subsequences | LC **792** | **A two-dimensional positional table built by a backward fold: `nxt[i][c]` = the first position `≥ i` holding character `c`.** Built in `O(nΣ)` with `nxt[i][c] = (s[i] == c) ? i : nxt[i+1][c]`. Then testing whether a pattern is a subsequence costs `O(|pattern|)` instead of `O(|s|)`, which is the difference between one query and fifty thousand. New because the table is indexed by `(position, symbol)` — a genuine second dimension — and because it is the **subsequence automaton**, the same object that makes LC 727 and "lexicographically smallest subsequence" tractable. |
| ↗ | Previous / next smaller element | LC **739** · **84** | The nearest-smaller-on-each-side table, built by a monotonic stack in one pass and then read in `O(1)` — the positional precompute behind histogram areas and contribution counting. [[Stack and Queue]] #12 and #13. |
| ↗ | `lastSeen` to jump a window's left edge | LC **3** | Keeping the last index of each character lets the left pointer leap instead of crawl. The same table as #15, consumed by a window rather than a boundary sweep. [[Sliding Window]] #3. |

## P7 · Difference arrays — the dual

| # | Problem | Source | The new idea |
|---|---|---|---|
| 17 | Corporate Flight Bookings | LC **1109** | **To add `v` across `[l, r]` in `O(1)`, deposit `+v` at `l` and `−v` at `r+1`; one prefix pass at the end materialises everything.** The exact inverse of #1, and the whole point is the asymmetry: `q` range updates cost `O(q + n)` total instead of `O(qn)`. The `r+1` boundary is where every bug lives — the delta marks *where the effect stops*, so allocate `n+1` slots and stop asking whether it should be `r` or `r+1`. |
| 18 | Meeting Rooms II | LC **253** | **When the axis is too large or not integral, the difference array becomes a sorted event list.** You cannot allocate an array indexed by timestamps, so emit `(start, +1)` and `(end, −1)`, sort, and sweep — the running total is the concurrent count and its maximum is the answer. New because the *domain* changed rather than the idea: this is #17 with the array replaced by a sort, which is the sweep-line technique in its simplest form. The choice between them is purely "can I index the axis directly". |
| 19 | Increment Submatrices by One | LC **2536** | **The 2D dual: four corner deposits, then a 2D prefix pass.** `+1` at the top-left, `−1` at the two positions just past the far edges, `+1` at the far corner — the same inclusion–exclusion as #3 running backwards. Worth its own entry because the sign pattern is not guessable from the 1D case, and because "apply many rectangle updates then read the grid once" is a common shape in grid simulation problems. |
| 20 | Minimum Number of K Consecutive Bit Flips | LC **995** | **Use a difference array *online*, inside a greedy sweep, as a counter of effects that are still in force.** Rather than doing all updates and then one read, you interleave: at each index you first retire the flips that have expired, then decide greedily, then record a delta at `i + k` so the effect switches off later by itself. New because it inverts the family's premise — the fold is happening *as you go*, and the difference array is acting as a queue of scheduled expiries. This is the pattern behind most "apply an operation with a range of effect, greedily, left to right" problems. |
| 21 | Minimum Moves to Make Array Complementary | LC **1674** | **Index the difference array by the *answer space*, not by the input.** Each pair of elements votes: for some candidate target sums it costs 0, for others 1, for the rest 2 — and each of those is a contiguous range of targets. So you accumulate votes across all pairs with range updates, then read off the best target in one pass. New and genuinely large: **when every input contributes a known cost to a contiguous band of candidate answers, sum the contributions with a difference array instead of evaluating each candidate.** Turns `O(n · answers)` into `O(n + answers)`. |
| 22 | Second-order difference — range-add an arithmetic progression | *classic — CSES/CF flavour; no clean LeetCode version* **[tail]** | **Apply the difference trick twice and you can range-add `1, 2, 3, …` in `O(1)`.** Adding a linear ramp to a range is a constant in the *second* difference, so two prefix passes at the end reconstruct it — and the pattern extends to polynomials of degree `d` with `d + 1` passes. Listed because it proves the operation composes, which is the structural fact behind the family, and because it makes the "cheap writes, one read" trade look less like a trick and more like a transform you can iterate. |

## P8 · When neither works

> [!warning] **The diagnosis, in one line each.** *Reads and writes interleaved* — prefix sums die at the first update, use a BIT or segment tree. *Non-invertible operator over arbitrary ranges* — min, max, gcd cannot be subtracted, use a sparse table. *Queries can be reordered* — offline is cheaper than online.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Range Sum Query — Mutable | LC **307** | The moment one update lands between two queries, a prefix array must be rebuilt in `O(n)`. A Fenwick tree keeps both at `O(log n)` and is prefix-based, so it still needs an **invertible** operator — the constraint from #1 survives the upgrade. [[Segment Trees]] #1 and #2. |
| ↗ | Prefix sums over a DP table | *classic — "sum of a range of previous states"* | **The fold applied to a table you are still filling.** When a transition reads `sum of dp[i−1][a..b]`, keeping a prefix row makes each state `O(1)` instead of `O(range)`, dropping a whole factor from the complexity. Exactly #1, except the array is a DP layer rather than the input — which is why someone drilling prefix sums recognises it instantly and someone drilling DP files it as an optimisation. [[Dynamic Programming]] #57. |
| ↗ | Bit-level prefix counts | *classic — "how many elements in `[l, r]` have bit `b` set"* | **One prefix array per bit, so any range's bitwise composition is `O(30)`.** The trick that rescues AND/OR/XOR range questions: those operators are not invertible, so #1 does not apply — but *per-bit counts are*, so you decompose one uninvertible aggregate into thirty invertible ones. #2 run thirty times, and worth its own row because the decomposition is the transferable half. [[Sliding Window]] #13. |
| ↗ | Static Range Minimum Queries | CSES **1647** | Min has no inverse, so #1 cannot answer arbitrary ranges — but min is **idempotent**, so two overlapping power-of-two blocks cover any range and `O(1)` is recoverable a different way. The precise complement of #11. [[Segment Trees]] #9. |
| ↗ | Prefix hashing (rolling hash) | *classic* | The fold applied to strings: precompute polynomial prefix hashes and any substring's hash is a subtraction plus a power multiply, so substring equality becomes `O(1)`. Same algebra as #1, with collision risk as the new failure mode. Strings basis. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Running Sum of 1d Array | LC **1480** | #1 | The prefix array *is* the output. |
| Remove Zero Sum Consecutive Nodes | LC **1171** | #5 | The same prefix-sum map, keyed to nodes instead of indices, with a splice instead of an index difference. [[Linked List]] ↗. |
| Leaders in an array | *Striver A2Z* | #11 | A suffix-max scan, read right to left — the same prefix-only read, with the comparison as the output. Listed by name because curated lists ask for it under this name and nothing else here would match it. |
| Left and Right Sum Differences | LC **2574** | #1 | Prefix and suffix sums, printed. |
| Find Pivot Index · Find the Middle Index | LC **724** · **1991** | #1 | `total − prefix − self`, which needs no second array. |
| Number of Vowels / Consonants in a range | LC **2559** | #2 | A 0/1 prefix sum. |
| Count Items Matching a Rule in ranges | — | #2 | Same, different predicate. |
| Range Sum of Sorted Subarray Sums | LC **1508** | #1 | Prefix sums of a generated list. |
| Number of Submatrices That Sum to Target | LC **1074** | #3 + #4 | Collapse one dimension, then run the hash-map lookup per column pair. Composition, and worth solving as the place both are tested. |
| Continuous Subarray Sum | LC **523** | #6 | Residue keying with a length constraint. |
| Make Sum Divisible by P | LC **1590** | #6 | Residue keying, minimising a removed block. |
| Binary Subarrays With Sum | LC **930** | #4 | Non-negative values make a window possible too, but the map solution is identical. |
| Count Number of Nice Subarrays | LC **1248** | #4 | Prefix count of odd numbers. |
| Number of Wonderful Substrings | LC **1915** | #8 | Ten parity bits instead of five, plus one flipped bit per candidate. |
| Maximum Sum Circular Subarray | LC **918** | #9 | `max(bestSubarray, total − minSubarray)`, plus the all-negative special case. |
| Best Time to Buy and Sell Stock | LC **121** | #9 | The running-extreme scan, named in the entry. |
| Best Time to Buy and Sell Stock IV | LC **188** | — | `k` transactions is a genuine DP, not a split scan. [[Dynamic Programming]] basis. |
| Minimum Deletions to Make String Balanced | LC **1653** | #12 | Count `b` in the prefix plus `a` in the suffix, minimise over the cut. |
| Maximum Sum of Two Non-Overlapping Subarrays | LC **1031** | #12 | Split scan with a fixed-length best on each side. |
| Candy | LC **135** | #10 | Two passes, left then right, taking the max — the combine step of #10 with a different operator. |
| Shortest Unsorted Continuous Subarray | LC **581** | #10 | Prefix-max and suffix-min compared per index. |
| Sum of Absolute Differences in a Sorted Array | LC **1685** | #1 | Prefix sums on sorted input, split at `i`. |
| Range Addition | LC **370** | #17 | The bare difference array. |
| Car Pooling | LC **1094** | #17 | Difference array over a bounded coordinate. |
| My Calendar I | LC **729** | #18 | Neighbour queries on a stream — [[Binary Search Trees]] #16 is the better tool. |
| Describe the Painting · Amount of Time... | LC **1943** | #18 | Event sweep with an extra accumulator. |
| Number of Flowers in Full Bloom | LC **2251** | #18 | Sweep, or two sorted arrays plus binary search. |
| Zero Array Transformation | LC **3355** | #20 | Online difference array inside a feasibility check. |
| Maximum Population Year | LC **1854** | #17 | Difference array over a tiny fixed axis. |
| Stamping the Sequence · Minimum Number of Taps | LC **936** · **1326** | — | Greedy interval covering. Intervals basis. |
| Prefix sums of doubles for a moving average | — | #1 | Correct, but the drift is why you keep a rolling sum instead. |

---

## Self-audit

**Borderline calls, and which way I went**

- **#4 and #5 split.** The lookup is identical; what the map *stores* differs — a frequency to count ranges, an earliest index to maximise length — and so does where the answer is read from. Two of the five probes disagree, so they are two entries. This is also the pair people most often merge and then get wrong in the direction that still compiles.
- **#6, #7 and #8 kept as three entries** rather than one "transform the key" entry. Kept apart because the transformations are unrelated: #6 quotients the value space, #7 re-encodes the *input alphabet*, #8 compresses a *vector* of parities into a scalar. A merged entry would have to be named so abstractly that none of the three would be findable from it.
- **#9 nearly went into P2.** It uses the prefix-versus-earlier-prefix shape, but against an extreme rather than an exact match, which is what lets the hash map collapse to a scalar. That collapse is the idea, so it got its own family — a one-entry family, which is slightly awkward and still the right call.
- **#11 (LC 42) native here as well as in [[Two Pointers]] #4 and [[Stack and Queue]] #14.** Three appearances of one problem, which sounds excessive until you notice each teaches something different: here it is that prefix-only reads survive a non-invertible operator, there it is the binding-constraint argument, and in the stack file it is horizontal layering. Cross-listing exists for exactly this.
- **P6 exists because you asked for it, and it earned the space.** Last-occurrence tables and the subsequence automaton are structurally prefix-fold problems that nobody files under prefix sums, which is precisely why they get reinvented. Two native entries plus two cross-lists is honest — the monotonic-stack tables genuinely belong to [[Stack and Queue]].
- **#22 (second-order difference) tail-tagged and kept.** No clean interview representative. Kept because it demonstrates the transform composes, which is the structural claim the whole file rests on, and dropping it would leave "why is this a transform rather than a trick" unanswered.
- **#14 (tree path sums) native despite depending on LCA.** The formula and the root-to-node reframing are prefix ideas; the LCA machinery is cross-linked. A reader drilling prefix sums should not have to already know [[Graphs]] #29 to find it.

**Naming check.** Three entries were retitled. #11 was drafted as "Trapping Rain Water with prefix arrays", which names the problem and hides the transferable half; it is now about *where invertibility is actually required*. #21 was drafted as "Minimum Moves to Make Array Complementary", entirely opaque; it is now *index the difference array by the answer space*. #13 was drafted as "counting sort's prefix step", which names the algorithm it came from rather than the move; it is now *fold over the value axis*. #16 was checked and kept, since "subsequence automaton" is a real name that a differently-dressed version would still match.

**Step 4B — reverse sweep**

Thirty-one plain-language descriptions navigated against the family headings. **One failure:**

- **"How many chunks have a sum between 100 and 200"** landed on #4 and stopped there, incorrectly. A hash map answers "has exactly this prefix occurred"; it cannot answer "how many earlier prefixes fall in this band". That is **#23**, and the axis it exposed — *what the lookup asks for: equality, or an inequality/range* — was missing entirely. The signature was the diagnostic one described in [[README]]: the description **did** find a cell, and the cell was wrong, so nothing in step 4 could have flagged it. Inversion counting and "sum at least `k` with negatives" were hidden by the same gap.

Three collisions, all checked and cleared. "Total of a range, many times" reaches #1 and #2 (the operator axis, correctly one entry apart). "Answer for every index using everything else" reaches #10 and #12 (per-index combine versus scanned split point — different, correctly separated). "Where did I last see this" reaches #15 and the ↗ `lastSeen` cross-list, which is one table consumed by two different sweeps and correctly marked.

**What I am uncertain about**

- **The boundary with Intervals.** #18 is a sweep line, and a full Intervals basis would want it plus half a dozen exclusions listed here. I have drawn the line at *is the running total the answer* (here) versus *are the intervals themselves the output* (Intervals). Defensible, and the most likely place a future file forces a re-cut.
- **Whether #12's split-point scan is really a prefix idea or a DP idea.** LC 123 has a clean DP formulation, and [[Dynamic Programming]] would have a fair claim. Kept here because the two-array construction is the thing being learned.
- **2D difference arrays (#19) may be over-scoped** for Indian SDE loops. Kept untagged because grid simulation questions do appear, but it is the entry I would cut first.
- **Prefix sums over a DP table** and **bit-level prefix counts** were both named in this audit as probable omissions, then cross-listed in during step 4C ([[README]]). Recording the sequence, because it is the pattern to watch: **a hedge in an audit is a finding you have written down and declined to act on.** If the sentence "someone drilling this topic would recognise it as the same move" is true, the row belongs in the table.
- **Recall is thinnest on the difference-array side.** P7 has six entries against P2's six, but the difference-array literature is far smaller and less systematically catalogued, so there is no good external list to sweep against. #21's idea especially — voting over an answer space — I suspect has more instances than I can name.
- **Bit-level prefix counts** were argued here as "#2 applied 30 times" and left out. Reversed in 4C: the *decomposition of an uninvertible aggregate into invertible per-bit ones* is the idea, and "applied 30 times" describes the implementation, not the insight. Now ↗ above.

**Completeness confidence: ~89%.** P1 through P4 I would call complete — that material is well-catalogued and heavily represented. The uncertainty is concentrated in P6, where the "positional table" framing is mine rather than standard so there is nothing to sweep against, and in P7, for the same reason on the difference-array side.

## Related Notes

- [[README]]
- [[Sliding Window]]
- [[Two Pointers]]
- [[Arrays]]
- [[Sorted Containers & Order Statistics]]
- [[Segment Trees]]
- [[Stack and Queue]]
- [[Binary Trees]]
- [[Dynamic Programming]]
- [[Math & Number Theory]]
- [[Linked List]]
- [[Bit Manipulation]]
