---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-28
---
# Concept Basis — Greedy

> [!abstract] Minimal spanning set for **making a locally justified choice and proving it does not block a better global one**. One entry per **new idea you have to learn**. Greedy is an *argument*, not a data structure — the heap, the sort, and the two pointers are how you *implement* an argument you already have. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!tip] **If you cannot say the exchange out loud, you do not have a greedy solution. You have a hope.**
>
> Every entry below is a different licence for "take this one, never look back" (or, in the ↗ heap rows, "take this one, look back with a heap"). The code is usually a sort plus a scan. The content is why that scan is allowed to be greedy rather than DP.

## Why this file is not "problems people tag greedy"

LeetCode's `greedy` tag is a residue: jump games, interval scheduling, Huffman, candy, gas station, and "sort then take" all land there, and they do not share a data structure. What they share is a **proof obligation**. The families below split on *what the argument is*, not on what you sort.

Cuts already promised by other files, honoured here rather than re-litigated:

- **[[Sorting & Custom Comparators]] #13** owns *the sort key is the deliverable*. This file owns *the exchange that names that key*. Native here, ↗ there.
- **[[Heap]] H6** owns regret — greedy that *revokes*. ↗ here.
- **[[Dynamic Programming]] #67** is what happens when the exchange dies (weights). ↗ here as the counterexample, not as a greedy.
- **[[Intervals]]** owns merge / insert / overlap-*counting* / calendars. Earliest-finish *selection* and *covering-by-farthest-reach* are greedy arguments and live here; ↗ both ways.

## Mechanism axes

| Axis | Values |
|---|---|
| **What you choose at each step** | take or skip this item · which of two sides to assign · how far to jump · whether to patch · a local swap / repair · the next symbol of a construction · which existing chain to extend |
| **What justifies the choice** | **exchange** (swap your pick into any optimal) · **stay-ahead** (your partial solution is always at least as far along) · **leftmost-feasible exists** (if any embedding works, the greedy one does) · **local safety** (a repair cannot create a new violation) · **forced** (only one legal move) |
| **Do you sort first, and by which key** | no, input order · **end / finish** · duration · density / ratio · difference of two costs · value itself · events, not elements |
| **Is the choice irrevocable** | yes · **no — a heap keeps the worst past decision cheap to drop** ([[Heap]] H6) |
| **What is being covered or allocated** | a line of time · a reachability frontier · a matching of two sorted sides · a string / digit sequence · k identical resources |
| **What greedy is competing with** | DP (the exchange fails) · binary search on the answer (the check *is* this greedy) · a heap (the candidate set is dynamic) |
| **What is returned** | a yes/no · a count · the constructed object · leftover resource |
| **What breaks it** | **weights** on intervals · a **non-canonical** coin system · a future constraint the local max cannot see · needing every solution, not one |

## Shape of this topic

```
G1  The exchange names the order          7
G2  Stretch the frontier                  4
G3  A local fix is globally safe          5
G4  Build the object                      6
                                          + 16 cross-listed ↗
```

**22 native entries, plus 16 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Entries are grouped by family, so a later-added entry keeps its high number inside the family it belongs to.

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| **Activity selection** · earliest finish · N meetings | #1 |
| **Fractional knapsack** | #2 |
| Two City Scheduling *(sort by difference)* | #3 |
| Assign Cookies · Boats to Save People | #4 |
| **Shortest processing time** · Smith's rule | #5 |
| **Job sequencing with deadlines** | #6 |
| **Movie Festival II** *(k screens)* | #22 |
| **Jump Game** | #7 |
| **Jump Game II** · video stitching · taps | #8 |
| Patch Array | #9 |
| **Gas Station** | #10 |
| Wiggle Subsequence / Wiggle Sort | #11 · #12 |
| Candy *(two-pass)* | #14 |
| **Integer to Roman** | #15 |
| Lemonade Change · canonical coin systems | #16 |
| Valid Parenthesis String *(lo / hi)* | #17 |
| Broken Calculator *(work backwards)* | #18 |
| Split Array into Consecutive Subsequences | #19 |
| Huffman · MST · Dijkstra · regret greedy | ↗ |

---

## G1 · The exchange names the order

> [!tip] **Find the argument first. It will name the key.** Sort by end, by duration, by ratio, by difference, by profit — all feel natural, and at most one is legal. [[Sorting & Custom Comparators]] #13 is the *sorting* half of this sentence. The rows below are the *arguments*, and they are not the same argument.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Non-overlapping Intervals · N meetings in one room | LC **435** · *CSES **1629** Movie Festival* | **Earliest finish: among all items compatible so far, taking the one that ends first never rules out a set the alternatives would have allowed.** Sort by end, scan, take every start that clears the last kept end. The exchange is the entry — swap your pick into any optimal schedule; it finishes no later, so the rest still fits. Sort the same data by start and the greedy is simply wrong. Minimum Arrows (LC **452**) and Maximum Length of Pair Chain (LC **646**) are this with the story changed. **Weights break it** — then the past is a DP prefix, [[Dynamic Programming]] #67, and knowing *why* the exchange needed equal (or absent) weights is the interview follow-up. The sort-key half is [[Sorting & Custom Comparators]] #13. |
| 2 | Fractional knapsack | *GFG / CSES-adjacent; practise the 0/1 failure on LC **416*** | **Density: take the item with the best value per unit of the scarce resource, and you may take a fraction of the last one.** The exchange: if an optimal omits some of this density in favour of a worse one, swap. New against #1 because the key is a *ratio*, not an endpoint, and because **the moment you cannot take a fraction, the same key is illegal** and you are in 0/1 knapsack ([[Dynamic Programming]] #9). The pair — greedy on ratios iff fractional, DP iff 0/1 — is the thing to own, not either problem alone. |
| 3 | Two City Scheduling | LC **1029** | **The key is a *difference between two options*, not a property of one option.** Sending a person to A costs `costA` against the alternative `costB`; the right order is `costA − costB`, then send the `n` smallest differences to A. New because #1 and #2 read a field of the item, and here the comparator is "how much do I *save* by choosing A over B" — [[Sorting & Custom Comparators]] #7's "the comparator encodes the objective" wearing greedy clothes. Boats and cookies (#4) match two *sides*; this assigns one pool onto two labelled bins of fixed size. |
| 4 | Assign Cookies · Boats to Save People | LC **455** · **881** | **Sort both sides, then greedily match the weakest remaining demand to the cheapest remaining supply (or the lightest with the heaviest that still fits).** The exchange: wasting a large cookie on a small child, when a smaller cookie would have done, can only hurt. New because there are *two* sorted sequences and the greedy step is a pairing, not a take-or-skip on one list. The pointer machinery is [[Two Pointers]] #1; the argument is here. Advantage Shuffle (LC **870**) is Tian Ji's horse racing — same pairing, skip a loser you cannot beat. |
| 5 | Tasks and Deadlines | CSES **1630** | **Shortest processing time first: sort by duration, not by deadline, to minimise the sum of completion times.** Each task's completion is "everything before me, plus me"; a long task sitting early is added into every later completion, so the exchange pushes long work right. New against #1 (earliest *finish* of an interval) and against [[Heap]] #17 (earliest *deadline* of an event you attend at most once). Deadline is a red herring on this problem, which is exactly why it earns a row. Smith's rule in scheduling textbooks. |
| 6 | Job Sequencing with Deadlines | *GFG / InterviewBit* | **Unit-time jobs with profits and deadlines: sort by profit descending, and slot each job into the *latest* still-free time at or before its deadline.** Latest-fit is the content — an earlier slot is a strictly more precious resource (more future jobs could have used it), so burning it on a job that could have gone later is dominated. New against #1 (max *count*, not profit; one machine, intervals of different lengths) and against #5 (no profits, no slots). A boolean array of slots is enough at small time; a DSU-on-slots is the upgrade ([[Union-Find]] ↗ Job Sequencing). |
| 22 | Movie Festival II | CSES **1632** | **Activity selection on `k` identical machines: when a new movie starts, assign it to the occupied screen that frees *latest* among those already free, or open a new screen if `k` allows.** The one-machine greedy (#1) does not extend by running #1 `k` times independently — you need a *set of current end times* and a policy for which machine to reuse. New because the resource count is now part of the state, and the structure that holds free times is [[Sorted Containers & Order Statistics]] (a multiset of ends). `k = 1` is #1; `k` unknown and you want the minimum is Meeting Rooms II (↗ [[Heap]] #14), a different question. |

## G2 · Stretch the frontier

Stay-ahead, not exchange: at every moment your greedy coverage reaches at least as far as any other strategy that has used the same number of moves. No sort of the *items* is required — the array *is* the line you are covering.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Jump Game | LC **55** | **Boolean reachability: track the farthest index you can still land on, and fail the moment the scan passes it.** `reach = max(reach, i + a[i])`; if `i > reach` you are stuck. New as the *frontier* itself — you are not choosing among intervals with an exchange, you are asking whether the reachable prefix ever covers `n − 1`. Greedy because extending `reach` as far as possible at each `i` stay-aheads every alternative; there is no reason to take a shorter jump. |
| 8 | Jump Game II | LC **45** | **Minimum number of jumps: the current jump owns a window, and the next jump starts at the far edge of the farthest you saw *inside* that window.** When `i` hits the end of the current jump, increment the answer and close a new window at `farthest`. Stay-ahead: any optimal uses at least as many jumps to reach this far, so closing the window here is never late. New against #7 — yes/no versus a count, and the *window of the current jump* is extra state. Video Stitching (LC **1024**) and Minimum Number of Taps (LC **1326**) are this on explicit intervals: from the current coverage, pick the clip that extends farthest. Also ↗ [[Intervals]] — the objects are intervals, the argument stays here. |
| 9 | Patch Array | LC **330** | **You may *insert* a number, and the greedy insert is the smallest value the current reachable prefix cannot make.** Maintain `miss` = smallest integer you cannot form from `[1, miss)`. If the next given number is `≤ miss`, it extends the prefix to `miss + nums[i]`; otherwise you patch `miss` itself, doubling the reachable range, and you do not consume the given number yet. New because #7/#8 only use what is already in the array; here the move is to *add* the thing that extends the frontier farthest (which, for a complete prefix of integers, is `miss` itself). |
| 10 | Gas Station | LC **134** | **A circular tour with a tank: if `Σ gas ≥ Σ cost` a start exists, and it is unique — it is the station *after* the most negative prefix of `gas − cost`.** You never try every start. The tank going negative is "the frontier died"; resetting the candidate start to `i + 1` and zeroing the tank is the same stay-ahead as #7, on a circle, with a global-sum feasibility check that #7 does not need. New because of the circle and because the *unique* start is a prefix-sum fact ([[Prefix Sums & Difference Arrays]] #9's running-extreme family) as much as a greedy one. |

## G3 · A local fix is globally safe

No global sort-key. You look at a neighbourhood, repair it, and an argument says the repair cannot create a new violation that a different repair would have avoided.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Wiggle Subsequence | LC **376** | **Peaks and valleys are all you need: a longer wiggle never wants a point strictly inside a run in the same direction.** Delete the interior of a monotone run; the greedy subsequence is the sequence of turning points (plus the ends). `O(n)` instead of the `O(n²)` LIS-shaped DP. New because the "safe local deletion" is the argument, and because the DP exists and is the thing this beats. |
| 12 | Wiggle Sort | LC **280** | **Rearrange in place with adjacent swaps: if `a[i]` and `a[i+1]` violate the required up/down, swap them — a local fix is always safe.** Distinct from #11: there you *select* a subsequence, here you *permute* the whole array. The "a local fix cannot ruin a later pair" argument is greedy; the three-step *lexicographic successor* that looks similar is [[Arrays]] #10 and is not this. Wiggle Sort II (LC **324**) needs a median partition and is not a local swap. |
| 13 | Non-decreasing Array | LC **665** | **At most one repair, decided by a local comparison, and the choice of *which* of the two elements to lower/raise is forced by the neighbour further left.** If you have already used your one change, a second dip fails. New because the budget is *one*, so the greedy is "repair the first violation, carefully, and hope" — the carefulness (prefer modifying `a[i]` vs `a[i+1]` depending on `a[i−1]`) *is* the entry. |
| 14 | Candy | LC **135** | **Local rating constraints in both directions cannot be satisfied in one greedy pass.** Walk left to right giving more candy than the left neighbour when the rating is higher; walk right to left the same against the right neighbour; take the max. New because a single direction leaves the other neighbour's constraint broken, and because the two-pass is the same shape as [[Prefix Sums & Difference Arrays]] #10 (combine a forward fold with a backward fold) with `max` as the combine. The *argument* that locally more-than-neighbour is necessary and, after both passes, sufficient, is greedy. |
| 21 | Increasing Triplet Subsequence | LC **334** | **Two thresholds, updated greedily: keep the smallest value seen so far, and the smallest value that has a smaller to its left; a third above that is the answer.** You are not building an LIS (`O(n log n)` tails, [[Dynamic Programming]] #52); you are asking existence of length 3, and the pair `(first, second)` is a sufficient summary. New as a *compressed* greedy state — two numbers, not a tails array — and as the thing people overkill with LIS. |

## G4 · Build the object

The answer is a string, a digit sequence, a partition, or a set of chains. Greedy still needs an argument; the difference is you are constructing, not counting.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 15 | Integer to Roman | LC **12** | **A fixed greedy table: always peel the largest denomination that fits, including the subtractive pairs `IV, IX, XL, …` as first-class denominations.** Works because the Roman system is *canonical* — the greedy choice property holds by construction of the numeral system, the same reason canonical coin systems (US coins, #16) admit greedy change. New as the *table* being the algorithm, and as the dual of Roman-to-integer ([[Math & Number Theory]]'s exclusion, which is a left-to-right decode, not a greedy). |
| 16 | Lemonade Change | LC **860** | **Change-making with a *limited* inventory of already-received coins, preferring the larger denomination.** Greedy is legal here because the denominations `{5, 10, 20}` with the 10-before-two-5s preference never paint you into a corner that a different giving would have avoided. New against #15 (unlimited table) and against [[Dynamic Programming]] #11 (unbounded knapsack *min coins*): **the same "always take the largest" rule is a bug the moment the system is not canonical** — `{1, 3, 4}` and amount 6 is the one-line counterexample. Own the pair. |
| 17 | Valid Parenthesis String | LC **678** | **A `*` is a wildcard; track a *range* `[lo, hi]` of possible open-counts instead of a single balance.** `(` increments both; `)` decrements both (floor `lo` at 0); `*` can do `−1, 0, +1`, so `lo−1` and `hi+1`. Fail if `hi < 0`; succeed if `lo == 0` at the end. New because greedy on a single balance cannot commit what `*` is, and the *interval of possible balances* is a sufficient summary — if any assignment of stars works, the greedy range contains 0. The stack solution that stores indices of `(` and `*` is the object-producing twin; this is the `O(1)`-space count. |
| 18 | Broken Calculator | LC **991** | **Work backwards from the target: if it is even, divide; if odd, increment; stop at `startValue`.** Forward (`×2` or `−1`) branches; backward the even case is *forced* and the odd case has one repair. New because **the greedy direction is the reverse of the operations as stated**, which is the move whenever an operation is invertible and the forward tree is bushy. Integer Replacement (LC **397**) is the same instinct with a messier odd case (sometimes `+1` beats `−1`); treat that as a search, this as the clean teaching problem. |
| 19 | Split Array into Consecutive Subsequences | LC **659** | **Prefer extending an existing chain over opening a new one.** Walk left to right; a value `x` first tries to append to a chain ending at `x−1`; only if none exists does it start a new chain of length 3 (`x, x+1, x+2`), which it may do only if those frequencies remain. New because the greedy is *which pile to join*, not which item to take, and because opening a new chain is the expensive move you postpone. Hand of Straights / Divide Array in Sets of K Consecutive (LC **846** · **1296**) is the same policy starting from the current minimum. |
| 20 | Monotone Increasing Digits | LC **738** | **Find the leftmost descent, decrement that digit, and set everything after it to `9`.** Any other repair either isn't smaller or isn't monotone. New as a *digit-greedy construction* that does not need a stack; the stack form of "make the number as large as possible after `k` deletions" is [[Stack and Queue]] #15, which is the same lexicographic instinct with a budget. |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them while studying greedy and they belong in this file. See [[README]] on cross-listing.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Non-overlapping Intervals *(the sort)* | LC **435** | The *key* is the deliverable; the exchange is #1. [[Sorting & Custom Comparators]] #13. |
| ↗ | Largest Number · Queue Reconstruction | LC **179** · **406** | Comparator-as-objective, and "sort so later inserts cannot invalidate earlier ones." Greedy-flavoured, sorting-shaped. [[Sorting & Custom Comparators]] #7 and #19. |
| ↗ | Meeting Rooms II · events by deadline | LC **253** · **1353** | Max concurrent is a *count*, not a selection — typed-event sweep is [[Intervals]] #6; the heap's *size* is the same count ([[Heap]] #14). Deadline-heap attendance is [[Heap]] #17. |
| ↗ | Course Schedule III · Refueling Stops | LC **630** · **871** | **Regret greedy** — commit, then drop the worst past decision. A plain greedy cannot revoke; the heap is what makes revocation `O(log n)`. [[Heap]] #18; the DP-dimension-killed form is [[Dynamic Programming]] #54. |
| ↗ | Huffman · connect sticks | LC **1167** | Repeatedly merge the two smallest. [[Heap]] #20. |
| ↗ | Task Scheduler · Reorganize String | LC **621** · **767** | Always place the currently most frequent remaining, with a cooldown or a gap. [[Heap]] #15. |
| ↗ | Weighted interval scheduling | LC **1235** | **The exchange in #1 dies the moment intervals have values.** Sort by end still creates a *DP order*; the decision at each job is take-or-skip with a binary-searched predecessor. [[Dynamic Programming]] #67. |
| ↗ | Coin Change | LC **322** | Unbounded knapsack. Greedy by largest denomination is #16's failure mode. [[Dynamic Programming]] #11. |
| ↗ | Maximum Subarray | LC **53** | Restart-or-extend. Kadane is greedy in spirit and DP in the file that owns "ending at." [[Dynamic Programming]] #4. |
| ↗ | Split Array Largest Sum *(the check)* | LC **410** | Binary search on the answer; the feasibility check *is* #8's cousin — a sequential greedy that starts a new piece the moment it would overflow. [[Binary Search]] #8. |
| ↗ | MST · Dijkstra | LC **1584** · **743** | Grow a set by always taking the cheapest outgoing edge / the closest unsettled node. The proof is a greedy-choice property on a matroid / on non-negative weights. [[Graphs]] #25 and #17. |
| ↗ | Remove K Digits | LC **402** | Monotonic stack under a deletion budget — lexicographically greedy. [[Stack and Queue]] #15. |
| ↗ | Is Subsequence | LC **392** | Leftmost-greedy matching: if any embedding exists, the earliest one does. [[Two Pointers]] #15. |
| ↗ | Partition Labels | LC **763** | Last-occurrence table, then close a part when `i == reach` — #8's covering argument fed by a positional precompute. [[Prefix Sums & Difference Arrays]] #15. |
| ↗ | Greedy from the MSB · XOR trie descent | LC **2429** · **421** | Filter a candidate set bit by bit, or prefer the opposite bit in a trie. [[Bit Manipulation]] #22, [[Tries]] #7. |
| ↗ | Branch and bound incumbent | LC **51** follow-ups | A greedy first pass gives #13's "cannot win" prune something to beat. [[Backtracking]] #13. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Minimum Number of Arrows to Burst Balloons | LC **452** | #1 | Activity selection; sort by end. Named in #1. |
| Maximum Length of Pair Chain | LC **646** | #1 | Intervals of pairs, earliest finish. |
| Merge Intervals · Insert Interval | LC **56** · **57** | — | Overlap algebra, not an exchange. [[Intervals]] #1 · #2. |
| Minimum Number of Platforms | *GFG* | ↗ LC 253 | Meeting Rooms II. [[Intervals]] #6. |
| Boats to Save People | LC **881** | #4 | Named as the second canonical. |
| Advantage Shuffle | LC **870** | #4 | Tian Ji; skip a horse you cannot beat. |
| Array Partition | LC **561** | #1 · #4 | Sort, pair neighbours; one-line exchange. |
| Maximum Units on a Truck | LC **1710** | #2 | Density, and you take whole boxes of a type — still the ratio key. |
| Jump Game III / IV | LC **1306** · **1345** | — | BFS on indices / value-teleports. [[Graphs]]. |
| Video Stitching · Minimum Number of Taps | LC **1024** · **1326** | #8 | Farthest-reach covering on explicit intervals. |
| Stamping the Sequence | LC **936** | #8 | Covering, with a different legal move set. Intervals / greedy cover. |
| Can Place Flowers | LC **605** | #12 · #13 | Plant if both neighbours are empty; local safety. |
| Wiggle Sort II | LC **324** | — | Median partition, then interleave. [[Two Pointers]] #11, not a local swap. |
| Next Permutation | LC **31** | — | Three forced local steps, but the goal is the lexicographic successor, not a greedy optimum. [[Arrays]] #10. |
| Minimum Increment to Make Array Unique | LC **945** | #5 | Sort, then greedy bump; a linear scan after a sort. |
| Maximum Ice Cream Bars · Destroying Asteroids | LC **1833** · **2126** | #2 · #4 | Sort by cost, take while the budget holds. |
| Reduce Array Size to The Half | LC **1338** | #2 | Sort by frequency descending, greedy take. |
| Minimum Rounds to Complete All Tasks | LC **2244** | #16 | Counts of 2 and 3; a 1 is impossible. Canonical grouping. |
| Hand of Straights · Divide Array in Sets of K Consecutive | LC **846** · **1296** | #19 | Start from the minimum, same extend-or-open. |
| Dota2 Senate | LC **649** | ↗ LC 621 | Two queues, greedy ban the next opponent. Simulation with a most-urgent rule. |
| Bag of Tokens | LC **948** | #4 | Two pointers after sort; face down the cheapest, face up the most valuable. |
| Merge Triplets to Form Target | LC **1899** | #13 | A triplet is safe iff no coordinate exceeds the target; OR the rest. Local filter. |
| Minimum Add / Remove to Make Parentheses Valid | LC **921** · **1249** | #17 | Single balance, not a wildcard range. |
| Minimum Deletion Cost to Avoid Repeating | LC **1578** | #14 | In a run, keep the max, delete the rest. Local. |
| Break a Palindrome · Maximum 69 Number | LC **1328** · **1945** | #15 | First eligible digit rewrite. Table-greedy on one pass. |
| Integer Replacement | LC **397** | #18 | Work backwards in spirit; the odd case is a search. [[Bit Manipulation]] exclusion. |
| Remove Duplicate Letters / Smallest Subsequence | LC **316** · **1081** | ↗ LC 402 | Stack greedy with a last-occurrence constraint. [[Stack and Queue]] #15. |
| Create Maximum Number | LC **321** | ↗ LC 402 | Two #15-style stacks plus a merge. Composition, Hard. |
| Reorganize String | LC **767** | ↗ LC 621 | Most frequent, with a gap of 1. |
| Maximum Performance of a Team · Hire K Workers | LC **1383** · **857** | ↗ LC 630 | Sort one key, heap the other. [[Heap]] H6 / H5. |
| Furthest Building You Can Reach | LC **1642** | ↗ Heap #25 | Regret that *downgrades* a resource rather than dropping it. |
| IPO | LC **502** | ↗ | Unlock as capital grows. [[Heap]] #13. |
| Best Time to Buy and Sell Stock | LC **121** | — | Running minimum. [[Prefix Sums & Difference Arrays]] #9. |
| Queue Reconstruction by Height | LC **406** | ↗ | Named in the ↗ table. |
| Pancake Sorting | LC **969** | — | Restricted operations. [[Sorting & Custom Comparators]] exclusion. |
| Matroid theory as a chapter | *textbook* | ↗ MST | Kruskal is the interview fragment. Do not learn matroids for SDE. |

---

## Self-audit

**Borderline calls, and which way I went**

- **#1 native here despite [[Sorting & Custom Comparators]] #13 already owning the sort-by-end.** Sorting owns *which key*. This file owns *the exchange*. Cross-listed both ways, which is what Sorting's audit asked for when Greedy did not exist. Merging them would hide the argument from anyone drilling greedy, and hide the key-choice from anyone drilling sorting.
- **#1 and #8 kept separate.** Both can be told as "intervals on a line." Split because the arguments differ: #1 is exchange on *which interval to keep*; #8 is stay-ahead on *how far the current jump reaches*. Video stitching looks like #1 and is #8.
- **#7 and #8 kept separate.** Yes/no versus min-count, and the current-jump window is extra state. Jump Game III/IV are graphs and were not allowed to merge in.
- **#2 and #16 kept separate.** Both are "take the best density / largest denomination." Split because fractional knapsack is about a *ratio on items you sort*, and lemonade is about a *canonical denomination system with inventory*. The shared moral — greedy dies when you cannot take a fraction, or when the system is not canonical — is stated in both, which is the pair to remember.
- **#11 and #12 kept separate.** Subsequence versus in-place permute. Arrays had parked Wiggle Sort as an exclusion of Next Permutation; that was a hedge waiting on this file.
- **#5 (SPT) included, despite no LeetCode.** CSES 1630 is the clean teaching problem, and without it "sort by deadline" ([[Heap]] #17) and "sort by end" (#1) silently absorb a third key. Source-unbounded.
- **#22 (k screens) included.** `k = 1` is #1; min-`k` is Meeting Rooms II. Max-profit with a given `k` is a different question and a different structure (multiset of ends). The empty cell would have been "activity selection with a resource count."
- **#21 (increasing triplet) included rather than folded into LIS.** Existence of length 3 is a two-variable greedy; LIS tails are a different idea. The overkill is the reason it earns a row.
- **Regret greedy, Huffman, MST, Dijkstra all ↗, none native.** The *argument* is greedy; the *new machinery* is the heap, the DSU, or the distance array. A reader drilling greedy still meets them here. Native would duplicate [[Heap]] H6/H7 and [[Graphs]] #25.
- **Partition Labels ↗ rather than native.** The covering argument is #8; the new idea in that problem is the last-occurrence *table*, which [[Prefix Sums & Difference Arrays]] #15 already owns.
- **Matroid theory excluded.** Kruskal is the fragment. A chapter on matroids would be Sorting-T1-energy spent on the wrong audience.

**Naming check.** Five retitles. #1 was drafted as "Non-overlapping Intervals", an LC name that reads as Intervals; it is now *earliest finish*. #3 was drafted as "Two City Scheduling"; it is now *the key is a difference between two options*, which is what LC 1029 shares with every "savings vs the alternative" greedy. #8 was drafted as "Jump Game II"; it is now *the current jump owns a window*. #9 was kept close to "Patch Array" because the insert-into-a-reachable-prefix move has no better common name. #17 was drafted as "Valid Parenthesis String" and the title now has to carry *lo / hi*, because "wildcard parentheses" would still look like a stack problem.

**Step 4 — recognition sweep**

Walked LeetCode `greedy` by frequency, Striver A2Z Greedy, NeetCode 250 Greedy, CSES Sorting and Searching (Movie Festival I/II, Tasks and Deadlines, Apartments, Ferris Wheel, Room Allocation), and the GFG job-sequencing / fractional-knapsack / min-platforms classics.

Forced natives rather than variations: CSES **1630** (#5, a third sort key that LC does not teach), CSES **1632** (#22, `k` resources), LC **330** (#9, insert to extend a prefix), LC **678** (#17, a *range* of balances), LC **659** (#19, extend-existing-chain). Platforms, Huffman, MST, task scheduler, regret, merge/insert interval all landed as ↗ or Intervals, not as new families.

**Step 4B — reverse sweep**

Twenty-two plain-language descriptions, interviewer register, no mechanism words. **Two naming fixes, no missing axis, no absent idea that stayed absent.**

- **"I have k movie screens, maximise the number of non-overlapping films"** landed on #1 and was wrong for `k > 1`. That is **#22**. The axis *what is being allocated* already had "k identical resources"; the cell was empty until this probe. Not a new axis — an unfilled cell, which is what step 2 is for.
- **"Sort by deadline to minimise total completion time"** landed on #1 and on ↗ Heap #17, both wrong. Deadline is a distractor; duration is the key. That is **#5**. Without #5 the probe would have been a silent collision between two existing rows.

Collisions checked and cleared. "Sort then take" reaches #1, #2, #3, #4, #5, #6 — the intended fan-out, and the reason G1 is seven rows rather than one. "Jump as far as you can" reaches #7 and #8, correctly (boolean vs count). "Greedy but I can undo" reaches only the ↗ Heap rows, which is the point of H6.

Descriptions that resolved cleanly: "take the interval that finishes first"; "value per kilo, and I may break the last sack"; "two cities, n people each, the cost is a pair"; "smallest cookie that satisfies the greediest remaining child"; "unit-time jobs, slot into the latest free hour"; "farthest index I can still reach"; "min jumps, the current jump is a window"; "I may insert a number to fill holes in 1..n"; "circular gas, unique start"; "candy, both neighbours"; "stars as parens, can I balance"; "always extend a chain before opening one"; "work backwards, doubling is forced."

**Step 4C — inward sweep**

- **4C-iii (hedges) first.** [[Sorting & Custom Comparators]] named this file: expect Greedy to want #13, LC 1029, LC 881, LC 455, keep them cross-listed. Honoured: #1, #3, #4 native here, Sorting keeps #13 native and the ↗ cousins. [[Arrays]] parked Wiggle Sort and LC 665 as "Greedy basis" — now #12 and #13. [[Math & Number Theory]] parked Roman — now #15. [[Prefix Sums & Difference Arrays]] parked taps/stamping as Intervals; the covering *argument* is #8, merge/insert are [[Intervals]] #1 · #2. [[Backtracking]] wanted the greedy incumbent for branch-and-bound — ↗ row, not a native (the new idea there is the prune, not the bound). [[Heap]]'s fuzzy greedy boundary: regret / Huffman / deadline / rooms all ↗, none duplicated (the Prim false-positive rule).
- **4C-i (reciprocity).** Inbound "Greedy basis" pointers from Sorting, Arrays, Math, Prefix (taps), Backtracking now have a destination. Confirmed against those files before adding anything on *their* side — done in the follow-up edits.
- **4C-ii (orphans).** Jump I/II, gas, candy, fractional knapsack, job sequencing, lemonade, valid paren string, patch array, movie festival, tasks-and-deadlines were absent as *developed greedy arguments* (several were named as exclusions or ↗ waiting on this file). Native here.

**What I am uncertain about**

- **The Intervals boundary is now closed.** #1 and #8 stay native here; [[Intervals]] ↗ them. Merge / insert / overlap-count live there (#1, #2, #6) and must not be pulled in — they are not exchanges.
- **Whether #6 (job sequencing) is too GFG-shaped.** It is a standing favourite in Indian interviews and the latest-fit slot is a real idea. If it never comes up for you, skip it; #1 and #5 already teach two other keys.
- **Whether #22 should have been ↗ Sorted Containers only.** The multiset is the structure; the policy "reuse the machine that frees latest" is the greedy. Kept native because a reader drilling greedy will meet "k screens" and should not be sent to a containers file first.
- **Dijkstra as greedy.** ↗ Graphs. Some courses teach Dijkstra *as* the greedy chapter. The non-negative-weights proof is a greedy-choice property; the new machinery is the heap. I would not make it native.
- **Kadane as greedy.** ↗ DP #4. Same tension. Ending-at is the DP idea; restart-or-extend is the greedy reading. Left where the file that owns "ending at" already put it.

**Completeness confidence: ~90%.** G1–G4 I am confident about: the axes (justification, key, irrevocable vs regret, what is allocated) are the right ones, and 4B filled two empty cells rather than discovering a missing axis. Remaining porosity is the Heap/Graphs ↗ boundary, which is the same one Sorting already recorded. Taps/stamping/merge re-cut as expected when [[Intervals]] was written; they did not add a fifth family.

## Related Notes

- [[README]]
- [[Sorting & Custom Comparators]]
- [[Heap]]
- [[Dynamic Programming]]
- [[Two Pointers]]
- [[Binary Search]]
- [[Graphs]]
- [[Stack and Queue]]
- [[Prefix Sums & Difference Arrays]]
- [[Arrays]]
- [[Sorted Containers & Order Statistics]]
- [[Backtracking]]
- [[Math & Number Theory]]
- [[Bit Manipulation]]
- [[Tries]]
- [[Intervals]]
- [[Union-Find]]
