---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Backtracking

> [!abstract] Minimal spanning set for **searching a decision tree you build as you go** — choose, recurse, undo. One entry per **new idea you have to learn**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## What separates this from DP and from graph DFS

> [!tip] All three are "recursive DFS". The difference is what you are doing with the tree.
>
> | | The tree is… | You want… | Repeated states |
> |---|---|---|---|
> | **Graph DFS** | given to you | to visit everything once | forbidden — a `visited` set kills them permanently |
> | **Backtracking** | built by your choices | **every leaf**, listed | usually irrelevant — different paths are different answers |
> | **DP** | built by your choices | a **number** | the whole point — memoise and collapse them |
>
> The line that matters in practice: **if the answer is a list of solutions, memoisation cannot help you, because the output itself is exponential.** The moment the question changes from "list them" to "count them" or "give me the best one", the same recursion becomes DP and the complexity collapses. K6 is entirely about that switch, and recognising which side of it you are on is the single highest-value skill in this file.

## Mechanism axes

| Axis | Values |
|---|---|
| **What one level decides** | include this element or not · which candidate goes in the next slot · **where to cut** · which value fills this cell · which bucket this item joins |
| **Branching across levels** | fixed 2 · all `n` candidates · all *remaining* candidates (shrinking) · candidates from a **start index** (non-decreasing) |
| **What stops duplicate output** | nothing — inputs are distinct · a start index · **sort, then skip equal siblings** · a `used[]` array · canonical forms |
| **Where the partial answer lives** | a list mutated and undone · a copy passed down · **the input itself, mutated in place** · constraint counters |
| **What is returned** | every solution · the **first** solution, then stop · a count · the best one |
| **Why a branch dies** | it violates a constraint · it **cannot beat the incumbent** · it is symmetric to one already tried · a lookup says it was seen |
| **Order of choices** | fixed · **dynamically chosen — most-constrained first** |
| **Who drives the enumeration** | the recursion, running to completion · **the caller, asking for one solution at a time** |
| **When the tree is too big** | memoise — but only if the answer is a number · prune harder · **split the input in half and join** · deepen iteratively |
| **What breaks** | forgetting the undo · deduplicating output *after* generating it · copying the board at every node · assuming memoisation applies when the output is a list |

## Shape of this topic

```
K1  The three canonical trees              3 ideas
K2  Not producing the same answer twice    2 ideas
K3  Constraints as state                   3 ideas
K4  Choosing where to cut                  2 ideas
K5  Pruning that changes the complexity    3 ideas
K6  The dynamic-programming boundary       3 ideas
K7  Escaping the tree entirely             2 ideas
K8  When the tree is still too large       2 ideas
                                           + 7 cross-listed ↗
```

**20 native entries, plus 7 cross-listed (↗).** See [[README]] on cross-listing. One entry is marked **[tail]** — real, but reach for it last.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** #18 and #19 were added by the reverse sweep and sit inside K7 and K8.

---

## K1 · The three canonical trees

> [!warning] **These three are not variations of one template, and treating them as one is why people get stuck.** The tree shapes genuinely differ — two branches per level, candidates from a start index, and all remaining candidates — and the shape is what decides both the complexity and the deduplication rule. Learn all three as separate objects.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Subsets | LC **78** | **Two branches per level: take element `i`, or do not.** `n` levels, `2ⁿ` leaves, and the recursion carries a list you append to before descending and pop from afterwards. This is where the **undo discipline** is introduced and it is the whole entry — the alternative, passing a fresh copy down, is correct but `O(n)` per node instead of `O(1)`, and the mutate-and-restore version is what every later family assumes. Worth knowing that subsets specifically need no recursion at all: loop `mask` from `0` to `2ⁿ − 1` and read its bits. **When the iterative form exists, say so, because it removes the stack.** |
| 2 | Combination Sum · Combinations | LC **39** · **77** | **A start index is what turns "which subset" into "which combination", because it forces choices to be non-decreasing.** Loop candidates from `start` and recurse with `i` if reuse is allowed or `i + 1` if not — that one character is the entire difference between LC 39 and LC 40. The reason it matters: without the start index the tree generates `[2,3]` and `[3,2]` separately, and deduplicating afterwards is exponentially wasteful. Two prunings live here too — **sort the candidates so an overshoot lets you `break` rather than `continue`**, and stop early when the remaining candidates cannot fill the required size. |
| 3 | Permutations | LC **46** | **All remaining candidates at each level, so the branch count shrinks: `n · (n−1) · … = n!`.** Two implementations that are not equivalent to hold in your head — a `used[]` boolean array, which is what generalises to constrained permutations; or **swapping the chosen element into position `i` and swapping it back**, which needs no extra array and is where the "the prefix is fixed, the suffix is a bag" invariant becomes visible. New versus #2 because there *is* no start index: order is the answer here, not an artefact to suppress. |

## K2 · Not producing the same answer twice

> [!danger] **Two different guards, and they are not interchangeable.** This is the most common source of a nearly-correct backtracking solution, and the reason is that the *reason* the guard works differs between the two tree shapes. Both require sorting first.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 4 | Subsets II · Combination Sum II | LC **90** · **40** | **Sort, then skip a candidate equal to its previous *sibling*: `if (i > start && a[i] == a[i-1]) continue`.** The guard is `i > start`, not `i > 0`, and that is the entry — at the first position of *this* loop the duplicate is legitimate (it is a deeper copy of the same value, which you are allowed to take), while later in the same loop it would generate a set you have already produced. Getting this to `i > 0` silently drops valid answers; omitting it produces duplicates. Being able to explain *which* duplicate each branch would have made is what proves you understand it. |
| 5 | Permutations II | LC **47** | **With no start index, the guard has to reference the `used[]` array instead: `if (i > 0 && a[i] == a[i-1] && !used[i-1]) continue`.** Genuinely different reasoning, which is why this is not folded into #4. The rule enforces that among equal values you always consume them **left to right** — so if the previous equal copy is unused, this copy is being taken out of turn and would duplicate a branch already explored. Same goal, different mechanism, and the fact that `!used[i-1]` rather than `used[i-1]` is correct is the part everyone flips. |

## K3 · Constraints as state

The partial solution stops being a list and becomes a set of **occupancy facts** you maintain incrementally, so checking a candidate is `O(1)` instead of a scan.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 6 | N-Queens | LC **51** | **Keep three sets — columns, and both diagonal families — so conflict checking is `O(1)` rather than a re-scan of the board.** The indexing is the content: cells on the same `↘` diagonal share `r + c`, and on the same `↙` diagonal share `r − c` (shift by `n` to keep it non-negative). Place row by row so rows never conflict by construction. New in general form: **derive an index that makes a constraint into a lookup**, which is the same instinct as canonical keys in [[Arrays]] #8. The bitmask version — three integers, `lowbit` to iterate free columns — is the standard follow-up and is worth writing once. |
| 7 | Sudoku Solver | LC **37** | **Return a boolean and let `true` unwind the entire stack, because you want one solution rather than all of them.** This is a different control flow from every entry above: `if (solve(next)) return true;` propagates success upward and *skips the undo*, since the board is now the answer. Second idea, and the one that actually decides whether your solver finishes: **choose the most-constrained cell next, not the next cell in reading order.** Picking the cell with the fewest legal candidates collapses the tree by orders of magnitude — this is the MRV heuristic from constraint satisfaction, and it is what separates a Sudoku solver that runs in milliseconds from one that hangs. |
| 8 | Word Search | LC **79** | **The input itself is the state: mark the cell visited, recurse, unmark it.** No separate `visited` grid, no copies — you write a sentinel into the board and restore it on the way out, so memory is `O(depth)` rather than `O(depth · board)`. New because the thing being mutated is *shared and external*, which makes the undo non-negotiable in a way #1's private list never was: forget it and sibling branches see a corrupted board. The general statement is the one from [[Binary Trees]] #3 — **temporarily mutate the structure instead of allocating memory to describe it.** |

## K4 · Choosing where to cut

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Palindrome Partitioning | LC **131** | **The decision at each level is a *boundary*, not an element: branch over every prefix length that is legal.** So the loop is `for end in start..n` with the validity test on `s[start..end]`, and the recursion continues from `end + 1`. New because the branching factor comes from the *geometry of the string* rather than a candidate list, and this is the shape behind every "split a sequence into valid pieces" problem — IP address restoration, word break, splitting into `k` parts. Precomputing an `isPalindrome[i][j]` table with DP first is the standard optimisation, and it is a clean example of DP feeding a search. |
| 10 | Expression Add Operators | LC **282** | **Carry enough state that a later operator can *retract* an earlier one.** Inserting `*` after `2 + 3` must undo the `+3` and re-apply it as `×3`, so the recursion carries the running total **and the last operand separately** — then `*` computes `total − prev + prev * next`. New because the partial answer is no longer append-only: a choice at depth `d` rewrites the meaning of a choice at depth `d − 1`, which is the general problem with any non-associative or precedence-bearing operator. Also where you learn to reject leading zeros and to watch overflow while multiplying, both of which are the actual test cases. |

## K5 · Pruning that changes the complexity class

> [!tip] The distinction worth carrying: **#11 prunes because a branch is *invalid*, #12 because it is *redundant*, #13 because it *cannot win*.** Three different licences, and only the third needs an incumbent answer to compare against.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Generate Parentheses | LC **22** | **Build only valid strings, so every leaf is an answer and there is no filtering pass.** Track opens used and closes used; you may open while `open < n` and close only while `close < open`. The naive approach generates all `2^{2n}` strings and validates each; this generates exactly the `Catalan(n)` valid ones. New idea in general form: **push the constraint into the branch condition rather than into a check at the leaf** — the tree stops being a superset of the answer and becomes the answer. Once you see it, most "generate all valid X" problems are this, and the question becomes *what invariant can I maintain incrementally?* |
| 12 | Partition to K Equal Sum Subsets | LC **698** | **Symmetry breaking: if a bucket is empty and you already tried an empty bucket at this level, skip it — those branches are identical.** Assigning item `i` to empty bucket 3 versus empty bucket 4 produces the same partition with different labels, and without this guard the tree is `k^n` instead of something tractable. Two companions that matter as much: **sort descending** so failures surface at shallow depth, and return immediately if any item exceeds the target. New because the branch is killed by an **isomorphism argument**, not by a constraint — nothing about it is illegal, it is just the same work twice. The bitmask-DP route to the same problem is [[Dynamic Programming]] #45, and knowing both is the point. |
| 13 | Find Minimum Time to Finish All Jobs | LC **1723** | **Branch and bound: keep the best complete answer found so far and abandon any partial state whose cost already reaches it.** The search stops being enumeration and becomes optimisation — you are not listing solutions, you are racing a bound downward, and each improvement prunes harder for everything after it. New because pruning now depends on **information discovered elsewhere in the tree**, which none of the earlier families do, and because the ordering of exploration suddenly matters: find a good answer early and the rest of the tree collapses. Combine with #12 here — this problem needs both, and the pair is what makes an exponential-looking search finish. |

## K6 · The dynamic-programming boundary

| # | Problem | Source | The new idea |
|---|---|---|---|
| 14 | Combination Sum IV, against Combination Sum | LC **377** vs **39** | **Same recursion, and the question's *return type* decides whether memoisation is legal.** LC 39 asks for the combinations, so the output is exponential and no cache can help — you must walk the tree. LC 377 asks how *many*, so `f(remaining)` is a number, states collapse, and it is a one-line DP. **This is the diagnosis entry for the whole file:** before writing anything, ask whether the answer is a list of solutions or a scalar summary of them, because that single fact decides which technique you are even allowed to use. It also explains why LC 377 counts *orderings* while LC 39 counts *sets* — the loop structure that makes memoisation work is the one that treats order as significant. |
| 15 | Beautiful Arrangement | LC **526** | **When the only thing that matters about a path is *which* elements it used, the state is a bitmask and the tree has repeated states after all.** `n!` paths collapse into `2ⁿ` states, because two different orders that consumed the same set face an identical remaining problem. New because it contradicts K1's premise — permutation trees usually have no repeated states, and the test for whether they do is whether the recursion's future depends on the *order* of past choices or only on the *set*. This is the bridge into bitmask DP ([[Dynamic Programming]] #45–48), and the reason `n ≤ 20` in a problem statement is a hint rather than a detail. |
| 16 | Word Break II | LC **140** | **Memoise a *collection*, not a number: cache the list of all completions of each suffix.** The output is still exponential, so #14's rule seems to forbid this — the escape is that many prefixes share the same suffix, and a suffix's answer set is reusable *verbatim*, so you pay for it once and splice it in. New because the cached value is itself a set of solutions, which is a genuinely different kind of memoisation and the reason this problem is tractable at all. The condition to check before reaching for it: **the sub-answers must compose independently of how you arrived**, which holds for suffixes of a string and fails the moment a global constraint links the pieces. |

## K7 · Escaping the tree entirely

Both entries exploit the fact that the solution space is **ordered**, so you can navigate it directly instead of walking a search tree.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 17 | Permutation Sequence | LC **60** | **Index into the solution space arithmetically instead of enumerating up to it.** There are `(n−1)!` permutations starting with each first element, so `k / (n−1)!` names the first element outright; take it out and recurse on the remainder. `O(n²)` where enumerating the `k`-th would be `O(k · n)`. New because you never build a tree at all — the counting structure of the space replaces the search. The inverse direction is worth owning too: **ranking** a given permutation by summing how many unused smaller elements precede each position, which is the same identity read backwards and needs a BIT to be fast ([[Sorted Containers & Order Statistics]] #1). |
| 18 | Iterator for Combination | LC **1286** | **A successor function: given one solution, produce the next in lexicographic order in place, so you hold `O(n)` memory instead of the whole output.** For combinations, find the rightmost index that can be incremented, bump it, and reset everything after it to the minimum — which is exactly `next_permutation`'s structure and exactly [[Arrays]] #10's three-step reasoning. New because **the caller drives the enumeration**, so the search must be resumable and therefore stateless between calls. The alternative route is to keep the recursion's stack as object state ([[Stack and Queue]] #19); the successor function is better here because there is no stack to keep. This is the honest answer whenever the output does not fit in memory, or when the consumer might stop early. |

## K8 · When the tree is still too large

| # | Problem | Source | The new idea |
|---|---|---|---|
| 19 | Closest Subsequence Sum | LC **1755** | **Meet in the middle: split the input in half, enumerate `2^{n/2}` subsets of each, then *join* the two halves with sorting and a two-pointer or binary search.** At `n = 40`, `2⁴⁰` is impossible and `2 · 2²⁰` is a million. New because it is neither pruning nor memoisation — it is a **reduction of the input**, and it applies exactly when the state cannot be summarised (so DP is unavailable) but the two halves interact only through one combinable quantity, like a sum or an XOR. The join is where the real work is, and it is [[Two Pointers]] #1 or [[Binary Search]] #1 on the sorted half-sums. The signature to recognise: `n ≤ 40` with no small numeric bound. |
| 20 | Iterative deepening · IDA* | *classic — sliding-puzzle and Rubik-style searches* **[tail]** | **Run a depth-limited DFS repeatedly with an increasing limit, and you get BFS's shortest-path guarantee at DFS's memory.** BFS on a branching factor of 10 dies on memory long before it dies on time; iterative deepening re-explores the shallow levels, which costs a constant factor because the last level dominates the total anyway. Adding a heuristic lower bound to the cutoff gives IDA*, which is how 15-puzzles are actually solved. **[tail]** because interviews rarely go here, but the memory argument is worth being able to state — "why not just BFS" has a real answer. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Word Search II | LC **212** | Walk the board and a trie in lockstep so the dictionary kills board branches immediately — the strongest pruning idea in the file, and it prunes with an **external structure** rather than with a local constraint. [[Tries]] #11. |
| ↗ | Path Sum II | LC **113** | Append on the way down, pop on the way back up. #1's undo discipline in its simplest possible setting, on a tree that is handed to you. [[Binary Trees]] #9. |
| ↗ | Unique Binary Search Trees II | LC **95** | Enumerate every *shape* rather than count them, with the recursion returning lists of subtrees to be combined pairwise — #16's compose-the-sub-answers idea on a structure instead of a string. [[Binary Search Trees]] ↗. |
| ↗ | All Paths From Source to Target | LC **797** | Path enumeration on a DAG: no `visited` set is needed because acyclicity already forbids revisits, which is exactly the distinction in this file's opening table. [[Graphs]]. |
| ↗ | Find the Shortest Superstring | LC **943** | Bitmask DP with a "current item" — #15 taken all the way, where the mask plus a position becomes the TSP state and the answer is reconstructed from parent pointers. [[Dynamic Programming]] #46. |
| ↗ | Flatten Nested List Iterator | LC **341** | Keep the traversal's stack as object state so `next()` resumes mid-flight. The other route to #18's resumability, and the right one when the structure is given rather than generated. [[Stack and Queue]] #19. |
| ↗ | Next Permutation | LC **31** | The successor function for permutations, in place and `O(n)` — #18's machinery, and the reason enumerating all permutations needs no recursion. [[Arrays]] #10. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Subsets with a size constraint | LC **77** | #2 | The start index with a length check. |
| Combination Sum III | LC **216** | #2 · #4 | Bounded candidates, fixed size. |
| Letter Combinations of a Phone Number | LC **17** | #2 | A Cartesian product — no undo needed, no pruning possible, nothing to learn. |
| Letter Case Permutation | LC **784** | #1 | Include/exclude, renamed. |
| Generalised Abbreviation | LC **320** | #1 | Include/exclude per character. |
| Restore IP Addresses | LC **93** | #9 | Cut positions with a range check. |
| Split a String Into the Max Number of Unique Substrings | LC **1593** | #9 | Cut positions plus a seen-set. |
| Word Break | LC **139** | #16 | The boolean version, so it is plain DP. |
| Concatenated Words | LC **472** | #16 | Word Break per word, memoised. |
| Sudoku validity check | LC **36** | #6 | Occupancy sets with no search on top. |
| N-Queens II *(count)* | LC **52** | #6 | Increment instead of collecting; the tree is identical. |
| Unique Paths III *(visit every cell)* | LC **980** | #8 | Mark-and-unmark with a completion count. |
| Hamiltonian path | *classic* | #8 | Mark-and-unmark on a graph rather than a grid. |
| Knight's tour | *classic* | #8 · #7 | Same, and the Warnsdorff heuristic is #7's MRV. |
| Matchsticks to Square | LC **473** | #12 | LC 698 at `k = 4`. |
| Fair Distribution of Cookies | LC **2305** | #12 | Bucket assignment with the same symmetry guard. |
| Minimum Number of Work Sessions | LC **1986** | #12 · #15 | Bucket packing; the bitmask DP is the intended route. |
| Optimal Account Balancing | LC **465** | #13 | Branch and bound over settlements. |
| Tiling a Rectangle with the Fewest Squares | LC **1240** | #13 | Bound pruning against the incumbent. |
| Remove Invalid Parentheses | LC **301** | #11 | Compute the removal count first, then generate only strings of that length — constructive pruning. |
| Palindrome Permutation II | LC **267** | #5 | Build half, mirror it, with the duplicate guard. |
| Beautiful Arrangement II | LC **667** | — | A construction, not a search. |
| Permutations via `next_permutation` in a loop | — | #18 | The successor function, iterated. [[Arrays]] #10. |
| Combination iterator via a saved recursion stack | LC **1286** | #18 | The other implementation; [[Stack and Queue]] #19 owns that machinery. |
| Sudoku with bitmask rows / columns / boxes | LC **37** | #6 · #7 | The same occupancy sets, stored in integers. |
| Meet in the middle for subset XOR / 4-Sum on `n = 40` | *classic* | #19 | The join key changes, the reduction does not. |
| Knapsack by enumeration | — | — | Memoisable, so it is DP. [[Dynamic Programming]] D2. |
| Generate all balanced BSTs / all expressions | LC **95** · **241** | #16 | Compose sub-answers from independent sides. |

---

## Self-audit

**Borderline calls, and which way I went**

- **K1 as three entries rather than one template.** The strongest temptation in the file was a single "backtracking template" entry with subsets, combinations and permutations as instances. Rejected because the deduplication rules in K2 are *not* transferable between them — #4's guard is wrong for permutations and #5's is wrong for subsets — so collapsing K1 would make K2 look arbitrary. The tree shape is load-bearing, not cosmetic.
- **#4 and #5 kept apart**, which is the split I care most about defending. Both are "sort and skip duplicates". They are separate because the *argument* differs: #4 reasons about siblings within one loop, #5 reasons about consumption order across the whole array. Anyone who has only learned one of them writes the other's guard and gets a silently wrong answer, which is exactly the test for a real distinction.
- **#7 carries two ideas** — first-success unwinding and MRV ordering — and I nearly split it. Kept together because both are what turns a Sudoku *solver* from a toy into a working one, and separating them would have implied you might sensibly use one without the other.
- **#12's symmetry breaking is the entry I most nearly filed as a variation** of #2's pruning. It is not: every other prune in this file kills a branch because it is illegal or losing, and this one kills a branch that is perfectly legal and merely *isomorphic* to one already explored. That is a third licence and it deserved its own row, which K5's opening callout now states explicitly.
- **#14 is a diagnosis entry rather than a technique**, comparing two problems instead of teaching one. Kept, and placed first in K6, because "should this be DP" is the question that actually gets asked at the whiteboard and because the return-type test answers it in one sentence.
- **#19 (meet in the middle) is arguably CP.** Kept because LC 1755 exists, is asked, and `n ≤ 40` is a signature you should be able to read. The scope rule in [[README]] excludes techniques that exist *only* for competitive programming, and this one does not.
- **#20 marked [tail].** Iterative deepening is real and almost never asked. The reason to keep it at all is that "why not BFS" has a memory answer, and being unable to give it is worse than not knowing the technique's name.

**Naming check.** Four retitles. #2 was drafted as "Combination Sum", which names a problem; it is now *the start index forces non-decreasing choices*, since that is the transferable fact. #10 was "Expression Add Operators" and is now *carry enough state that a later operator can retract an earlier one*, because the precedence problem is invisible under the problem's name. #12 was "Partition to K Equal Sum Subsets" and is now *symmetry breaking*, which is the only part that generalises. #17 was "the k-th permutation" and is now *index into the solution space arithmetically*, which is what a differently-dressed version would share. #11 was checked and kept close to its name, since "generate all valid X" is how the shape presents itself.

**Step 4B — reverse sweep**

Thirty plain-language descriptions navigated against the family headings. **Two failures:**

- **"Give me solutions one at a time — I cannot hold them all, and I may stop early"** landed nowhere. Every family silently assumed the recursion runs to completion and appends to a list, and the axis that exposed it was *who drives the enumeration* — the recursion, or the caller. That is **#18**. Worth flagging that this is the **third file** to need resumable enumeration, after [[Stack and Queue]] #19 and [[Binary Search Trees]] #14, which is precisely the pattern the [[README]] now warns about: an idea cross-listed in several files and native in none is invisible, because every file's sweep terminates satisfied.
- **"`n` is 40, `2⁴⁰` is too many, and there is no small numeric bound to key a DP on"** also landed nowhere. K5 offered pruning and K6 offered memoisation, and the honest answer is neither — you halve the input. The missing axis was *when the tree is too big*, whose values were only "memoise" and "prune harder"; **split the input and join the halves** was absent. That is **#19**.

Four collisions, all checked and cleared. "All subsets with duplicates" reaches #1 and #4, correctly — the shape and the guard are separate lessons. "Assign items to `k` groups" reaches #12 and ↗ LC 943, which is the pruning-versus-bitmask choice the entry is about. "Place things on a board without conflict" reaches #6 and #8, correctly distinguished by whether the constraint is derived or read from the board. "Enumerate all permutations without recursion" reaches #18 and ↗ LC 31, the same idea by design.

**What I am uncertain about**

- **The Greedy boundary.** #13's branch and bound needs a good incumbent fast, which in practice means a greedy first pass, and nothing here develops that. A Greedy basis will want to claim the bound-construction half.
- **The Graphs boundary is thinner than the opening table implies.** #8 mark-and-unmark on a grid and a Hamiltonian-path search on a graph are the same code; I filed both here because the *undo* is the lesson, but Graphs has an equal claim and a reader looking under Graphs for "enumerate all paths" finds only the ↗ row.
- **Constraint satisfaction as a topic.** #6, #7 and #12 are CSP ideas — occupancy sets, variable ordering, symmetry breaking — and a real CSP treatment would add constraint propagation and arc consistency. Out of scope for interviews as far as I can tell, but I am less sure of that than of the other scope calls.
- **Whether #16 is one idea or two.** Memoising a collection, and the independence condition that licenses it, could reasonably be separate entries. Left merged because the condition is useless without the technique.
- **Recall is thinnest on K5.** Pruning is where the interesting variety lives, and it is badly served by curated lists — most sources teach the three templates and stop, so there is no good external enumeration of pruning ideas to sweep against. If something is missing from this file, it is a pruning argument.

**Completeness confidence: ~90%.** K1 through K4 and K6 I am confident about; they have dense external representation and the axes came out clean. The uncertainty sits in K5, for the recall reason above, and at the Greedy and Graphs boundaries.

## Related Notes

- [[README]]
- [[Dynamic Programming]]
- [[Graphs]]
- [[Tries]]
- [[Binary Trees]]
- [[Stack and Queue]]
- [[Arrays]]
- [[Sorted Containers & Order Statistics]]
- [[Math & Number Theory]]
- [[Bit Manipulation]]
