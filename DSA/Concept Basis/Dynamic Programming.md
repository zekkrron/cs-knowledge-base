---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-23
---
# Concept Basis — Dynamic Programming

> [!abstract] Minimal spanning set for DP. One entry per **new idea you have to learn**. Variations live in the exclusions table with the entry each collapses into.

## Why these families

Classification is by **what the state means and how transitions are shaped**, never by how many indices the array happens to have. A grid path problem and a linear subarray problem are the same idea wearing different input; `dp[i][j]` versus `dp[i]` is a property of the input, not of the concept.

Adopted from your scheme: knapsack, "ending at", multi-sequence, interval, optimisations, bitmask, tree, digit, misc.

Four adjustments:

- **Grid survives, but only as a residue.** Most grid DP genuinely is "ending at a cell" and lives in D1. D4 holds only the grid problems whose idea is something else — reversed direction, two simultaneous agents, geometric state, dimension collapse.
- **Front-partition split out from interval.** "Where does the *last piece* start" is a prefix scan in `O(n²)`. "What is the best way to combine `[i, j]`" is an interval split in `O(n³)`. Different states, different loops, different recognition signal.
- **State machine promoted to its own family.** Technically "ending at, with a phase," but augmenting state with a *mode* is distinct enough to deserve visibility.
- **Probability & expectation added.** Transitions that carry probability mass behave differently enough to separate.

## Mechanism axes

| Axis | Values |
|---|---|
| **What the index means** | ending exactly at `i` · prefix through `i` · interval `[i, j]` · position in a digit string |
| **What the state is keyed by** | an array position · **a derived value** (a difference, a remainder, a ratio) requiring a hash map · a set, as a bitmask |
| **Where the DP order comes from** | the input's own index · **a sort you impose** (by end time, by ratio, by size) · a topological order · interval length |
| **What else is in the state** | nothing · capacity/budget · count of pieces · phase/mode · bitmask of used items · accumulated run-length · agent positions |
| **Transition span** | `O(1)` look-back · scan all previous `j < i` · **a binary-searched predecessor** · split point `k` inside an interval · subset enumeration |
| **Combining operation** | min/max · sum (counting) · product · expectation · game-theoretic minimax |
| **Direction** | forward from base · backward from target · both roots (rerooting) |
| **What is returned** | the value · a count · the object itself (reconstruction) |
| **What makes the naive version too slow** | inner scan → binary search · window max → monotonic deque · range sum → prefix sums · a whole dimension → priority queue or greedy · absurd `n` → matrix power |
| **Assumption that breaks** | cycles break memoisation · negatives break monotone transitions · loop order silently swaps combinations and permutations |

## Shape of this topic

```
D1  Linear — "ending at" vs "prefix"     10
D2  Knapsack — choose or don't            8
D3  Multi-sequence                        7
D4  Grid — beyond "ending at"             5
D5  Front partition                       3
D6  Interval                              7
D7  State machine                         2
D8  Tree DP                               4
D9  Bitmask                               4
D10 Digit DP                              3
D11 Optimisations                         8
D12 Probability & expectation             3
D13 Miscellaneous                         4
```

**68 native entries, plus 4 cross-listed (↗).** A ↗ entry is developed more fully in the named topic, but it lives here too — see [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Entries are grouped by family, so a later-added entry keeps its high number inside the family it belongs to. This keeps cross-file references from rotting every time the basis grows.

---

## D1 · Linear — "ending at" vs "prefix"

> [!tip] The first modelling decision in every DP. **"Ending at `i`"** forces index `i` to be used, so the answer is a max over all `i`. **"Prefix through `i`"** leaves `i` optional, so the answer is `dp[n]`. Choosing wrong makes the recurrence impossible to write.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Climbing Stairs | LC **70** | The base act: name a state, write a recurrence, fix a base case. Everything else is this with more structure. |
| 2 | House Robber | LC **198** | **Prefix state with take/skip under a gap constraint.** Taking `i` forbids `i-1`, so the recurrence reaches back two. |
| 3 | House Robber II | LC **213** | **Break a circular constraint by running the DP twice** — once excluding the first element, once the last. The general repair whenever the array wraps. |
| 4 | Maximum Subarray | LC **53** | **The "ending at" framing itself**, plus restart-or-extend. `dp[i]` must use `i`, and the answer is the max over all `i` rather than `dp[n]`. |
| 5 | Maximum Product Subarray | LC **152** | **Carry several extremes when the transition is not monotone.** A negative flips min into max, so tracking only the best is insufficient. |
| 6 | Decode Ways | LC **91** | **Variable-length look-back with validity conditions.** Transitions reach back one or two characters depending on whether the substring is legal. |
| 7 | Word Break | LC **139** | **Scan every previous split point.** `dp[i]` looks at all `j < i` instead of a fixed offset — the `O(n²)` prefix shape. |
| 8 | Longest Increasing Subsequence | LC **300** | **"Ending at" combined with scan-all-previous**, filtered by a comparison. The most reused shape in this file. |
| 67 | Maximum Profit in Job Scheduling | LC **1235** | **Weighted interval scheduling — sort by end time so the past becomes a prefix.** Choosing non-overlapping intervals to maximise value looks like it needs a set, not an index, until you sort by end: now `dp[i] = max(dp[i-1], value[i] + dp[last job ending ≤ start[i]])`, and that predecessor is found by binary search rather than a scan. **The sort is what creates the DP order** — this is the canonical case of an input having no natural index until you impose one. Unweighted, it degenerates to the greedy "take the earliest-ending" ([[Heap]] #17), and knowing why weights break that greedy is the interview question. |
| 68 | Longest Arithmetic Subsequence | LC **1027** | **Index the state by a *value*, not by a position.** The state is `(index, common difference)`, and the difference is unbounded, so the second dimension is a hash map per index rather than an array. Turns a `O(n³)` scan into `O(n²)`. The move — *let the state be keyed by a derived quantity you cannot array-index* — is the same reframing that powers value-indexed BITs in [[Segment Trees]] #3, and it recurs whenever the thing that determines the future is a relationship rather than a location. |

## D2 · Knapsack — choose or don't choose

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Partition Equal Subset Sum | LC **416** | **0/1 knapsack.** State is `(index, remaining capacity)`; each item is taken at most once. Also the reduction from "equal halves" to "reach sum/2". |
| 10 | Target Sum | LC **494** | **Reduce an unfamiliar problem to knapsack by algebra.** Assigning ± signs becomes subset-sum once you solve for the positive subset. |
| 11 | Coin Change | LC **322** | **Unbounded knapsack.** An item is reusable, which changes the loop bound and nothing else — the diff against #9 is the lesson. |
| 12 | Number of Ways to Earn Points | LC **2585** | **Bounded knapsack** — each item has a finite count, sitting between #9 and #11. When counts are large, **binary splitting** decomposes a count into powers of two so bounded reduces to 0/1. The third member of a family you would otherwise think has two. |
| 13 | Coin Change II | LC **518** | **Loop order decides combinations versus permutations.** Items outer counts combinations. The single most common silent DP bug. |
| 14 | Combination Sum IV | LC **377** | The permutation counterpart of #13 — identical DP with the loops swapped. Kept because seeing the pair side by side is what makes the rule stick. |
| 15 | Last Stone Weight II | LC **1049** | **Minimising a difference is subset-sum nearest to half.** Recognising an optimisation problem as a reachability problem. |
| 16 | Ones and Zeroes | LC **474** | **Knapsack with two independent capacities.** Capacity is a vector, not a scalar. |

## D3 · Multi-sequence

| # | Problem | Source | The new idea |
|---|---|---|---|
| 17 | Longest Common Subsequence | LC **1143** | **Two indices advancing independently.** Match consumes both; mismatch branches on which to skip. |
| 18 | Edit Distance | LC **72** | **Three edit operations are three cell moves.** Insert, delete and replace map onto the three neighbouring states. |
| 19 | Distinct Subsequences | LC **115** | **Counting embeddings with an asymmetric transition** — on a match you both take and skip, on a mismatch only one pointer moves. |
| 20 | Wildcard Matching | LC **44** | **`*` consumes many characters**, so the branch is "use it again" versus "move past it". |
| 21 | Regular Expression Matching | LC **10** | **`*` binds to the preceding character**, giving different branching from #20. The pair teaches that pattern semantics reshape the recurrence. |
| 22 | Interleaving String | LC **97** | **Two sources feeding one target.** State is `(i, j)` and the third index is derived, not stored. |
| 23 | Shortest Common Supersequence | LC **1092** | **Reconstruct the answer by walking the table backwards.** Producing the object rather than its length is a separate skill from filling the table. |

## D4 · Grid — beyond "ending at"

> [!warning] Unique Paths and Minimum Path Sum are **not** here. They are "ending at cell `(i, j)`" and belong to D1's idea. This family holds only grid problems whose idea is something else.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 24 | Dungeon Game | LC **174** | **Backward induction.** The constraint binds on the minimum *along the way*, so forward DP cannot decide anything locally. You must compute from the destination. |
| 25 | Cherry Pickup II | LC **1463** | **Two agents advancing simultaneously.** State is `(step, position₁, position₂)` — synchronise on time rather than iterating one agent then the other. |
| 26 | Maximal Square | LC **221** | **Geometric state.** `dp[i][j]` is the largest square whose *corner* sits here; the min of three neighbours plus one encodes a shape constraint. |
| 27 | Cherry Pickup | LC **741** | **A round trip is two simultaneous outbound trips.** Reversing the return leg so #25's machinery applies is the insight. |
| 28 | Max Sum of Rectangle No Larger Than K | LC **363** | **Collapse a dimension by fixing its bounds.** Fix a top and bottom row, sum the columns between them, and a 2D problem becomes the 1D problem you already solved. The general move for reducing dimensionality — it also turns 2D sliding-window maximum into two passes of a 1D deque. |

## D5 · Front partition

> [!tip] Recognition signal: "cut the array into pieces." You iterate over where the **last piece begins**. Contrast D6, where you iterate over a split point *inside* an interval.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 29 | Palindrome Partitioning II | LC **132** | **`dp[i]` = best partition of prefix `i`; try every start of the final piece.** The prefix-partition shape. |
| 30 | Partition Array for Maximum Sum | LC **1043** | **Bounded piece length, with each piece's value depending on the whole piece** rather than summing its elements. |
| 31 | Split Array Largest Sum | LC **410** | **`dp[i][k]` — prefix plus a piece count.** Also the first place you see that a monotone answer permits binary search instead of DP entirely (see #56). |

## D6 · Interval

| # | Problem | Source | The new idea |
|---|---|---|---|
| 32 | Minimum Score Triangulation of Polygon | LC **1039** | **Split `[i, j]` at `k` and combine two halves.** The Matrix Chain shape, which every other entry here modifies. |
| 33 | Burst Balloons | LC **312** | **Reason about which element is *last*, not first.** Choosing the last burst keeps both neighbours intact, which is what makes the subproblems independent. A genuine reversal of intuition. |
| 34 | Minimum Cost to Merge Stones | LC **1000** | **A dimension for partial progress** — `dp[i][j][m]` = interval reduced to `m` piles, because you cannot always collapse to one. |
| 35 | Remove Boxes | LC **546** | **Carry accumulated identical elements into the state.** `dp[i][j][k]` where `k` counts equal boxes glued on the left. This is delete-and-squeeze: fix the left end, sweep the right. |
| 36 | Strange Printer | LC **664** | **Layered covering.** Operations stack, so equal characters far apart can be produced by one stroke — the transition merges non-adjacent positions. |
| 37 | Longest Palindromic Subsequence | LC **516** | **Expand an interval from both ends.** Match shrinks both, mismatch branches on one. |
| 38 | Predict the Winner | LC **486** | **Two-player minimax on intervals.** Store the score *difference* so one table serves both players. |

## D7 · State machine

| # | Problem | Source | The new idea |
|---|---|---|---|
| 39 | Best Time to Buy and Sell Stock with Cooldown | LC **309** | **Augment the index with an explicit phase.** Holding, sold, resting — draw the machine first and the recurrence writes itself. |
| 40 | Best Time to Buy and Sell Stock IV | LC **188** | **Add a resource count to the machine.** `dp[i][k][holding]`, where `k` transactions remaining is a third axis. |

## D8 · Tree DP

| # | Problem | Source | The new idea |
|---|---|---|---|
| 41 | House Robber III | LC **337** | **Return a tuple up the recursion** — `(best if I take this node, best if I don't)` — so the parent can choose without re-descending. |
| 42 | Binary Tree Maximum Path Sum | LC **124** | **Return one quantity, record another.** You return the best *downward* path but update the global answer with the *through-node* path. The two differ, and conflating them is the classic bug. |
| 43 | Sum of Distances in Tree | LC **834** | **Rerooting.** Solve for one root, then transform to every other root in `O(n)` by adjusting along each edge, instead of `n` separate traversals. |
| 44 | Karen and Supermarket | CF **815C** | **Tree knapsack — merging child DP tables.** Each subtree returns an array indexed by items taken, and a parent merges children pairwise. The surprise is the complexity: bounding merges by subtree size makes it `O(n²)`, not exponential. Where #41 returns two numbers, this returns a whole table. |

## D9 · Bitmask

| # | Problem | Source | The new idea |
|---|---|---|---|
| 45 | Partition to K Equal Sum Subsets | LC **698** | **A subset as an integer.** The mask *is* the state; iterating submasks is the new primitive ([[Bit Manipulation]] #14). |
| 46 | Find the Shortest Superstring | LC **943** | **Bitmask plus a "current item" for ordering** — the TSP shape — and reconstructing the permutation from parent pointers. |
| 47 | Maximum Students Taking Exam | LC **1349** | **Profile DP.** The mask describes an entire row's configuration and transitions run between adjacent rows. |
| 48 | Number of Ways to Wear Different Hats | LC **1434** | **Iterate the smaller axis.** Masking over 40 hats is impossible; masking over 10 people is trivial. Choosing which dimension becomes the mask is the whole problem. |

## D10 · Digit DP

| # | Problem | Source | The new idea |
|---|---|---|---|
| 49 | Numbers At Most N Given Digit Set | LC **902** | **Position-by-position with a tight flag.** The flag tracks whether the prefix still hugs `N`'s bound, which is what makes counting-below-a-limit tractable. |
| 50 | Non-negative Integers without Consecutive Ones | LC **600** | **Carry a constraint between adjacent positions.** The state remembers the previous digit, so digit DP composes with a local rule. |
| 51 | Counting Numbers | CSES **2220** | **The `started` / leading-zero flag.** Complete digit DP needs a second boolean beside `tight`, because leading zeros must not count as digits. LeetCode's digit problems dodge this, which is why #49 and #50 alone leave you unable to write a general digit DP. |

## D11 · Optimisations

> [!tip] Every entry here is the same move: identify what the inner loop recomputes, then replace the loop with a structure that maintains it.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 52 | Russian Doll Envelopes | LC **354** | **Binary search replaces the inner scan** — LIS in `O(n log n)` via patience. Also the sort-then-LIS reduction, with the descending tiebreak that prevents equal widths from chaining. |
| 53 | Jump Game VI | LC **1696** | **Monotonic deque prunes dominated states** across a sliding window, turning `O(nk)` into `O(n)`. |
| 54 | Minimum Number of Refueling Stops | LC **871** | **A priority queue replaces a whole DP dimension.** Defer the decision — take fuel retroactively from the largest tank passed — instead of enumerating stop counts. |
| 55 | Partition Equal Subset Sum (1D) | LC **416** | **Space optimisation by rolling array**, iterating capacity *descending* so each item is used once. The direction of that loop is the entire correctness argument. |
| 56 | Split Array Largest Sum (binary search) | LC **410** | **Replace DP with binary search on the answer** when feasibility is monotone. Recognising monotonicity is what unlocks it. |
| 57 | Candies | AtCoder **EDPC-M** | **Prefix sums over DP states.** When a transition sums a contiguous *range* of previous states, precompute the row's prefix sums and each transition becomes `O(1)`. Turns `O(n·k²)` into `O(n·k)`. The canonical teaching problem, and LeetCode has nothing equivalent. |
| 58 | Closest Subsequence Sum | LC **1755** | **Meet in the middle.** When `n ≈ 40`, `2ⁿ` is impossible but `2^(n/2)` is fine — split, enumerate both halves, then match across with sorting or binary search. |
| 59 | Throwing Dice | CSES **1722** | **Matrix exponentiation for linear recurrences.** When `n` reaches `10¹⁸`, express the transition as a matrix and exponentiate by squaring for `O(k³ log n)`. The signal is a fixed-width recurrence with an absurd `n`. |

## D12 · Probability & expectation

| # | Problem | Source | The new idea |
|---|---|---|---|
| 60 | Knight Probability in Chessboard | LC **688** | **Transitions carry probability mass** and split evenly rather than optimising. Sum, do not max. |
| 61 | New 21 Game | LC **837** | **A sliding-window sum over previous DP states**, plus modelling an absorbing stop condition. #57's optimisation meets probability. |
| 62 | Expected steps with restart | *classic — no clean judge version* | **Self-referential expectation.** When a state can transition back to itself, `E` appears on both sides and you solve algebraically rather than recursing: `E = 1 + pE + …` rearranges to `E = (1 + …)/(1 − p)`. Recursion never terminates without this step. |

## D13 · Miscellaneous

| # | Problem | Source | The new idea |
|---|---|---|---|
| 63 | Longest Valid Parentheses | LC **32** | **A transition that jumps by a computed offset** — `dp[i - dp[i-1] - 2]`. Uses the table to index itself, which appears nowhere else. |
| 64 | Unique Binary Search Trees | LC **96** | **Count structures by fixing a root and multiplying subtree counts.** The Catalan shape — combinatorial DP where the transition is a product over a split. |
| 65 | Super Egg Drop | LC **887** | **Invert the state and the answer.** Instead of "minimum moves for `k` eggs and `n` floors," define "maximum floors coverable with `k` eggs and `m` moves." The strongest single move in this file. |
| 66 | Grundy's Game | CSES **1730** | **Grundy numbers.** Impartial games decompose into independent piles; each position gets a Grundy value via `mex` of its moves, and the XOR of pile values decides the winner. Generalises the ad-hoc parity reasoning in #38. *Tail — mostly quant and trading interviews.* |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them here and they are part of studying DP. See [[README]] on cross-listing.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Longest Increasing Path in a Matrix | LC **329** | **Memoisation is only legal once you have proved acyclicity.** Strictly increasing values are what make the grid a DAG. The most important reminder in this file about *when DP is allowed at all*. [[Graphs]] #12. |
| ↗ | Shortest Path Visiting All Nodes | LC **847** | Bitmask state driven by a BFS frontier rather than a recurrence — the same `(node, mask)` state as #46, reached by a different machine. Worth doing right after D9. [[Graphs]] #8. |
| ↗ | Palindromic Substrings | LC **647** | The `dp[i][j]` "is this range a palindrome" table, which several interviewers ask for by name even though expand-around-centre is better. Strings basis. |
| ↗ | Word Break II | LC **140** | Where DP stops and search begins: counting or testing is DP (#7), but *enumerating every solution* is backtracking with memoised feasibility as a pruning aid. [[Backtracking]] #16, where the memoised value is the whole set of completions rather than a boolean; the general test is [[Backtracking]] #14. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Min Cost Climbing Stairs | LC **746** | #1 | Same recurrence with a cost attached. |
| Fibonacci / Tribonacci | LC **509**, **1137** | #1 | Wider look-back, same shape. |
| Delete and Earn | LC **740** | #2 | House Robber after bucketing by value. |
| Maximum Sum Circular Subarray | LC **918** | #3 + #4 | Circular break composed with Kadane. |
| Best Sightseeing Pair | LC **1014** | #4 | "Ending at" with the term algebraically split. |
| Number of Longest Increasing Subsequence | LC **673** | #8 | LIS carrying a count alongside the length. |
| Largest Divisible Subset | LC **368** | #8 | LIS with divisibility as the comparison. |
| Best Team With No Conflicts | LC **1626** | #8 | Sort, then LIS on score. |
| Perfect Squares | LC **279** | #11 | Unbounded knapsack over square-number coins. |
| Coin Change II vs Combination Sum IV | — | #13, #14 | Deliberately both kept; they are the contrast pair, not duplicates. |
| Combination Sum (I/II) | LC **39**, **40** | Backtracking | Enumeration, not counting. |
| Maximum Length of Repeated Subarray | LC **718** | #17 | LCS constrained to contiguity. |
| Uncrossed Lines | LC **1035** | #17 | LCS with the story changed. |
| Delete Operation for Two Strings | LC **583** | #17 | `len₁ + len₂ − 2·LCS`. |
| Minimum ASCII Delete Sum | LC **712** | #17 | Weighted version of the above. |
| Unique Paths / with Obstacles | LC **62**, **63** | D1 | "Ending at cell `(i, j)`" — the 2D-ness is not an idea. |
| Minimum Path Sum | LC **64** | D1 | As above. |
| Triangle / Minimum Falling Path Sum | LC **120**, **931** | D1 | Same, with a different neighbour set. |
| Minimum Insertion Steps to Make a String Palindrome | LC **1312** | #37 | `n − LPS`. |
| Stone Game | LC **877** | #38 | Minimax interval with a parity shortcut. |
| Best Time to Buy and Sell Stock with Fee | LC **714** | #39 | A phase machine with a constant subtracted. |
| Count Vowels Permutation | LC **1220** | #39 | A five-state machine. |
| Knight Dialer | LC **935** | #39 | A ten-state machine on a keypad. |
| Best Time to Buy and Sell Stock III | LC **123** | #40 | `k = 2` hardcoded. |
| Diameter of Binary Tree | LC **543** | #42 | Return-one-record-another with counts instead of sums. |
| Longest Univalue Path | LC **687** | #42 | As above, with an equality guard. |
| Constrained Subsequence Sum | LC **1425** | #53 | The same deque-prunes-dominated-states move as Jump Game VI. Do one, not both. |

---

## Self-audit

**Borderline calls**

- **Combination Sum IV (#14) kept despite being #13 with swapped loops.** Kept deliberately — the loop-order rule does not stick from one problem, and the pair *is* the lesson. This is the one place I knowingly allowed near-redundancy.
- **Cherry Pickup (#27) and Cherry Pickup II (#25) both kept.** II teaches synchronised agents; I teaches that a round trip *is* two outbound trips. Defensible to merge if the reversal feels obvious to you.
- **State machine (D7) as its own family.** Under your scheme it would sit in "ending at." Split for visibility, not because the state is fundamentally different. ==Fold it back if the separation feels artificial.==
- **Split Array Largest Sum listed twice** (#31 and #56), once as partition DP and once as binary-search-on-answer. Intentional — recognising that a monotone answer replaces the DP is a separate idea from the DP itself.
- **Palindromic substring DP excluded** to Strings. Arguable: some interviewers want the `dp[i][j]` table specifically.

**What the re-sweep changed**

Nine entries were added after the source constraint was lifted: #12, #28, #44, #51, #57, #58, #59, #62 and #66. Four of them were previously listed here as open uncertainties and turned out to be **caused by the LeetCode-only constraint rather than by genuine absence** — prefix-sum transitions (#57), matrix exponentiation (#59), digit DP depth (#51) and bounded knapsack (#12). Meet in the middle (#58) and dimension collapse (#28) were plain oversights, since both have good LeetCode representatives.

> [!warning] The lesson generalises to every remaining topic. The first pass silently equated "LeetCode has no good problem for this" with "this is not a concept." Any topic file written before this fix must be re-swept against CSES and AtCoder. [[Graphs]] has been; [[Stack and Queue]] has been and barely moved.

**Step 4B — reverse sweep**

Forty-six plain-language descriptions were navigated against the family headings. This file had the worst result of the six, and both failures were in D1 — the family I had assumed was the safest because it is the one I know best.

- **"Pick non-overlapping intervals to maximise total value"** landed nowhere at all. **Weighted interval scheduling was genuinely absent** — not misnamed, not merged into something, simply not present, despite being a standard interview question and the classic pairing of sort, DP and binary search. It is now #67. This is the only outright *absence* the reverse sweep found across all six files; everything else was a naming or framing fault.
- **"The thing that determines the future is a difference, not a position"** landed nowhere. That is #68.

Both were hidden by the same gap: the axis table described what the index *means* but never asked **what the state is keyed by**, nor **where the DP order comes from**. Every entry in this file until now took the input's own index order for granted. Once "a sort you impose" exists as an axis value, weighted interval scheduling is unmissable — and its absence for as long as it lasted is a good illustration of why the cross-product method cannot audit itself.

Three axis rows were added: *what the state is keyed by*, *where the DP order comes from*, and a *binary-searched predecessor* value on the transition-span axis.

**Borderline calls still open**

- **Divide-and-conquer optimisation, Knuth optimisation, convex hull trick** remain excluded — genuinely competitive-programming machinery, out of scope rather than out of source.
- **Grundy numbers (#66)** sits right on the scope boundary. Included because game theory does surface at trading firms, but skip it if you are not targeting those.
- **SOS DP (sum over subsets)** excluded as CP-only. Lower confidence on this one than on the optimisations above.
- **D13 is a real family, not a dumping ground** — but it is the section most likely to grow.

**Completeness confidence: ~93%.** Unchanged in number but not in meaning. It rose from 88% after the source re-sweep, then should honestly have *fallen* when 4B found an outright absence in the family I was most confident about — and rises back now that the two missing axes are named. Treat the figure as "confidence given the axes currently listed," which is the only thing any of these numbers ever measured.

## Related Notes

- [[README]]
- [[Stack and Queue]]
- [[Graphs]]
- [[Heap]]
- [[Binary Search]]
- [[Segment Trees]]
- [[Binary Trees]]
- [[N-ary Trees]]
- [[Tries]]
- [[Backtracking]]
- [[Math & Number Theory]]
- [[Bit Manipulation]]
