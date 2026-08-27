---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Sliding Window

> [!abstract] Minimal spanning set for sliding windows — **both fixed-length and variable-length**. One entry per **new idea you have to learn**. Every other two-pointer geometry lives in [[Two Pointers]]. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## Scope boundary against [[Two Pointers]]

A sliding window *is* a two-pointer technique, so the split needs stating precisely.

**This file:** both pointers move **left to right, never backwards**, and the interval between them is a meaningful object — a *window* — whose summary you maintain incrementally. Fixed `k` and variable length both qualify; the length rule is an axis inside this file, not the boundary.

**[[Two Pointers]]:** every other geometry. Converging from both ends, diverging from a centre, one pointer per sequence, two speeds on one sequence, read-and-write on one array, partitioning.

| Description | File |
|---|---|
| "longest stretch with no repeats" | here |
| "average of every `k` consecutive days" | here |
| "two numbers in a sorted array summing to `X`" | [[Two Pointers]] |
| "most water between two walls" | [[Two Pointers]] |
| "is there a loop in this chain" | [[Two Pointers]] |

## The three questions that decide everything

> [!tip] **Every sliding window problem is answered by three questions, in this order.**
>
> 1. **What is the summary, and can it be *removed* from the left as cheaply as it was added on the right?** Sum, count and frequency: yes. Max, min and median: no — and that single fact generates all of W5.
> 2. **What sets the length?** A given `k`, a predicate you grow until it breaks, or an external clock. This decides whether you write a `for` or a `while`.
> 3. **Is the predicate monotone in length?** If a longer window is always at least as invalid as a shorter one, shrinking is a valid repair and the technique works. **If not, there is no window** — and W6 is what you do instead.

> [!warning] **The `while` loop's direction is the whole thing, and it is where everything goes wrong.** For a **longest** answer you shrink *while invalid*, then record — so the window is valid every time you look at it. For a **shortest** answer you record, then shrink *while still valid* — so you are always testing whether you can afford to give something back. Writing one when you meant the other is the single most common bug in this topic, which is why #3 and #4 are separate entries rather than one.

## Mechanism axes

| Axis | Values |
|---|---|
| **What sets the length** | a given `k` · grown until a predicate breaks · shrunk until it is restored · an external clock (timestamps) · a block-sized stride |
| **What the summary is** | a running sum or product · a frequency map · a distinct-count · a count of "bad" elements · a max or min · an order statistic · a bitmask or per-bit counts |
| **Is the summary removable from the left?** | **yes, `O(1)` both ways** · yes but only with a deque · yes but only with a heap or ordered multiset · **not at all** — needs per-bit counts or a different technique |
| **Is the predicate monotone in length?** | yes — longer is never more valid · **no** — which breaks shrinking entirely |
| **Shrink discipline** | shrink while invalid, then record (longest) · record, then shrink while valid (shortest) · advance `l` at most once per step and never shrink (maximum-only) |
| **What is returned** | the longest length · the shortest length · a **count of valid windows** · one value per window · a boolean · the window itself |
| **How the answer accumulates** | max · min · **sum of `r − l + 1` contributions** · one emission per position |
| **What it slides over** | an array · a string · a **stream you cannot rewind** · a circular array · a sorted-by-you array · a 2D grid |
| **Which assumption breaks** | negatives break "the sum grows as the window grows" · a non-monotone predicate breaks shrinking · a max summary is not subtractable · "exactly `k`" is not a window predicate |

## Shape of this topic

```
W1  The engine — fixed length          2 ideas
W2  The engine — variable length       5 ideas
W3  Making a window appear             3 ideas
W4  What defines the extent            2 ideas
W5  What the window carries            1 idea  + 4 ↗
W6  When there is no window            2 ideas + 3 ↗
```

**15 native entries, plus 7 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.**

---

## W1 · The engine — fixed length

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Maximum Average Subarray I | LC **643** | **A window's summary is *updated*, not recomputed: add the element entering, subtract the one leaving.** `O(n)` instead of `O(nk)`, and the whole topic is this sentence applied to harder summaries. The one thing to internalise beyond the code is the precondition it hides — this works only because a sum has an inverse, and W5 is what happens when the summary does not. |
| 2 | Permutation in String | LC **567** | **When the summary is a vector, keep a scalar that says how close it is to the target.** Comparing two 26-length frequency maps at every position is `O(Σ)` per step and defeats the point. Instead track a single `matched` counter — bumped when a character's count reaches the required value, decremented when it leaves it — so the check is `O(1)`. New because the summary is no longer a number, and the move is *summarise the summary*. Every "does this window contain exactly / at least these characters" problem is this, including LC 76. |

## W2 · The engine — variable length

The core of the topic. Five distinct moves, and they are routinely mistaken for one.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Longest Substring Without Repeating Characters | LC **3** | **Longest: expand right always, shrink left *while invalid*, record after the window is valid again.** The amortised argument is the content — `l` never moves backwards, so both pointers together travel `2n` and the whole thing is `O(n)` despite the nested loop. Everything in this family deforms this. |
| 4 | Minimum Size Subarray Sum | LC **209** | **Shortest: record *first*, then shrink while the window is still valid.** The mirror of #3 and a genuinely separate entry, because the loop's meaning inverts — #3 shrinks to *escape* invalidity, this shrinks to *test* how much you can give back and stay valid. Kept apart deliberately: conflating the two is the defining bug of this topic, and a single "sliding window" entry would hide it. |
| 5 | Subarray Product Less Than K | LC **713** | **Counting, not measuring: a valid window ending at `r` contributes `r − l + 1` subarrays at once.** Because every suffix of a valid window is also valid, one arithmetic step counts a whole block instead of enumerating it. New because the *return type* changes from a length to a count, and that changes what you do at each step rather than just what you print — probe 4 in [[README]]. The dual form, "every start up to `l` works, so add `l + 1`", is the same idea read from the other side. |
| 6 | Subarrays with K Different Integers | LC **992** | **"Exactly `k`" is not a window predicate, so decompose it: `atMost(k) − atMost(k−1)`.** Exactly-`k` is non-monotone — growing a window can make it valid, then invalid, then valid again — so there is no shrink rule that preserves it. Two monotone problems subtract to give the non-monotone one. The generalisation is worth more than the problem: **when a predicate is an equality, write it as the difference of two inequalities.** |
| 7 | Longest Repeating Character Replacement | LC **424** | **When the answer is a maximum length, the window never has to shrink below its current best.** So `l` advances at most once per step and the `while` becomes an `if` — and, startlingly, the max-frequency count need never be decreased, because a stale-but-too-high value can only produce a window that fails to beat the record. New idea in general form: **if you only care about the largest valid window, the window is allowed to be invalid**, as long as it can never *report* a wrong answer. Feels like cheating, is correct, and the argument is what interviewers probe. |

## W3 · Making a window appear

Three modelling moves. In each the mechanism is already known — the difficulty is that the input does not look like a window until you change what you are windowing over.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Maximum Points You Can Obtain from Cards | LC **1423** | **Put the predicate on the *complement*.** "Take `k` cards from either end" is a two-sided choice with `k+1` cases — until you notice that what you *leave behind* is one contiguous block of size `n − k`, so maximising the take is minimising a fixed window. The same inversion solves "replace the shortest substring so the rest is balanced" (LC 1234) and "remove the shortest subarray to hit a target sum" (LC 1658). Reflex worth building: **if a choice is made at both ends, window the middle.** |
| 9 | Frequency of the Most Frequent Element | LC **1838** | **Sort first, because the original order is irrelevant.** Nothing in the problem is contiguous — you are choosing a multiset of elements to raise to a common value — but after sorting, the optimal group *is* contiguous, and the cost of levelling a window to its right edge is `k·max − windowSum`. New because the window is over an order **you imposed**, which is a step earlier than any other entry here. Ask it of every non-contiguous problem: *would sorting make the answer an interval?* |
| 10 | Minimum Number of Flips to Make the Binary String Alternating | LC **1888** | **A circular array becomes linear by doubling it, then windowing at length `n`.** Concatenate the string with itself and every rotation appears exactly once as a window, so "best over all rotations" collapses to "best window". Cheap, general, and the standard answer to any wraparound constraint — the alternative, modulo arithmetic inside the loop, is correct but far easier to get wrong. |

## W4 · What defines the extent

Two entries where the length rule is neither a given `k` nor a predicate.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Number of Recent Calls | LC **933** | **The window is defined by an external clock, over a stream you cannot rewind.** Elements expire because *time* passed, not because a count was exceeded or a predicate broke, so eviction is driven by a value you compare against rather than by an index offset. Two consequences: the window's element count is an output rather than a parameter, and you can never look ahead — which rules out every technique that needs a second pass. This is the pattern behind rate limiters and every "in the last N seconds" metric, and [[Stack and Queue]] correctly sends you here for it. |
| 12 | Substring with Concatenation of All Words | LC **30** | **When the window's unit is a block rather than an element, run `w` interleaved windows.** With words of length `w`, positions `0, w, 2w, …` form one window that steps a whole word at a time — and there are `w` such families, offset by each residue. Restarting the scan at every index is `O(n·w)`; running `w` windows that each advance monotonically is `O(n)`. New because the axis "what is one step" was silently 1 in every entry above, and this is the value that breaks it. |

## W5 · What the window carries

> [!warning] **The removability taxonomy — decide this before writing any code.**
>
> - **Invertible, `O(1)` both ways:** sum, product with a zero-count, XOR, count of a property, a frequency map, a distinct-count derived from that map. Use #1 directly.
> - **Not invertible, but the discarded candidates were provably useless:** max, min. Use a **monotonic deque** — a max cannot be un-added, but the elements you would need to recover were already dominated. ↗ below.
> - **Not invertible, and everything matters:** median, `k`-th smallest, nearest neighbour. Use **two heaps with lazy deletion** or an **ordered multiset**. ↗ below.
> - **Not invertible and not orderable:** bitwise OR and AND, GCD. No deque helps, because the leaving element's contribution is genuinely entangled. Use #13.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 13 | Shortest Subarray With OR at Least K II | LC **3097** | **Replace a non-invertible aggregate with per-bit counters, which *are* invertible.** You cannot subtract a value out of a bitwise OR — but if you keep `count[b]` = how many elements in the window have bit `b` set, then adding and removing are both `O(30)`, and the OR is "every bit whose count is positive". The move generalises: **decompose the aggregate into independent components that each support subtraction.** The same trick handles windowed AND, and it is the reason bitwise window problems are easier than they look. The *why a nested family of ORs is short at all* is [[Bit Manipulation]] #11. |
| ↗ | Bitwise ORs of Subarrays | LC **898** | Not a window you shrink — a counting argument that an OR-chain of nested subarrays has length at most 32. Listed so the two bitwise-OR ideas do not get merged: this file repairs invertibility, that file bounds the number of distinct values. [[Bit Manipulation]] #11. |
| ↗ | Sliding Window Maximum | LC **239** | Max is not subtractable, so evict from **both** ends: dominated candidates from the back, expired ones from the front. The canonical proof that a window's summary can need its own data structure. [[Stack and Queue]] #21; the max-heap-with-lazy-deletion alternative is [[Heap]] #12. |
| ↗ | Longest Continuous Subarray With Absolute Diff ≤ Limit | LC **1438** | **Two deques run in parallel**, one for max and one for min, so the window's *range* is queryable while it is being resized — a summary that needs two structures at once. [[Stack and Queue]] #22. |
| ↗ | Sliding Window Median | LC **480** | An order statistic over a window: two heaps facing each other, with **lazy deletion** because elements now leave from the middle. [[Heap]] #12. |
| ↗ | Contains Duplicate III | LC **220** | A **neighbourhood** query rather than an aggregate — "is anything within `t` of this value currently in the window" — which needs an ordered set's `ceiling`, not a deque. [[Binary Search Trees]] #15. |

## W6 · When there is no window

The most valuable family, because recognising that the technique *cannot* apply is worth more than another variation of #3.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 14 | Subarray Sum Equals K | LC **560** | **Negatives destroy monotonicity, so there is no window at all — use prefix sums and a hash map.** Sliding window silently assumes that extending right increases the sum and shrinking left decreases it; one negative number and both are false, so no shrink rule is valid. The replacement counts pairs of prefix sums instead of maintaining an interval, and it is a genuinely different technique that happens to solve the same-looking problem. **This is the diagnosis entry: if the array can go negative, stop reaching for two pointers.** The prefix-sum machinery is developed in [[Prefix Sums & Difference Arrays]] #4. |
| 15 | Longest Substring with At Least K Repeating Characters | LC **395** | **When the predicate is not monotone, fix an extra parameter until it is, and loop over the parameter.** "Every character appears at least `k` times" can break when you *grow* the window as well as when you shrink it, so no shrink discipline works. The repair: for each `t` in `1…26`, find the longest window containing exactly `t` distinct characters, all with count ≥ `k` — with `t` pinned, the predicate becomes monotone and #3 applies. Twenty-six windows instead of one. The general move — **restore monotonicity by conditioning on a small parameter** — is the sliding-window analogue of #6, and divide-and-conquer on a disqualified character is the alternative worth knowing. |
| ↗ | Shortest Subarray with Sum at Least K | LC **862** | The *repair* for #14 when you need a **shortest** window and values can be negative: a monotonic deque over **prefix sums**, where the front is evicted on **satisfaction** rather than expiry. The single best illustration of what breaks and what replaces it. [[Stack and Queue]] #23. |
| ↗ | 2D window over a grid | — | Collapse one dimension first: run the 1D window along every row, then along the columns of the result. The reduction is the idea, not the window. [[Dynamic Programming]] #28. |
| ↗ | Binary search the length, verify with a fixed window | — | When "is there a valid window of length `L`" is monotone in `L` but the window itself cannot be grown incrementally, search `L` and run #1 as the check. Turns an unusable variable window into `log n` usable fixed ones. [[Binary Search]] #7. |

---

## Excluded as variations

| Problem                                              | Source      | Collapses into   | Why                                                                                                                                                                                                            |
| ---------------------------------------------------- | ----------- | ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Maximum Number of Vowels in a Substring              | LC **1456** | #1               | Fixed window, summary is a count.                                                                                                                                                                              |
| Minimum Recolors to Get K Consecutive Black Blocks   | LC **2379** | #1               | Fixed window, minimise instead of maximise.                                                                                                                                                                    |
| Moving Average from Data Stream                      | LC **346**  | #1               | Fixed window behind a class; the queue is incidental.                                                                                                                                                          |
| Maximum Sum of Distinct Subarrays With Length K      | LC **2461** | #1 + #2          | Fixed window carrying both a sum and a frequency map.                                                                                                                                                          |
| Find All Anagrams in a String                        | LC **438**  | #2               | Emits every match instead of the first; identical machinery.                                                                                                                                                   |
| **Minimum Window Substring**                         | LC **76**   | #4 + #2          | Shortest-window discipline over a frequency summary — a *composition* of two entries, not a new idea. Do it anyway: it is the standard hard interview question and the place both entries are tested together. |
| Longest Substring with At Most K Distinct            | LC **340**  | #3               | Summary is a distinct-count.                                                                                                                                                                                   |
| Fruit Into Baskets                                   | LC **904**  | #3               | LC 340 with `k = 2`.                                                                                                                                                                                           |
| Max Consecutive Ones III                             | LC **1004** | #3               | Summary is a count of zeros.                                                                                                                                                                                   |
| Longest Subarray of 1's After Deleting One Element   | LC **1493** | #3               | LC 1004 with `k = 1`.                                                                                                                                                                                          |
| Longest Nice Subarray                                | LC **2401** | #3               | The bitmask *is* removable by XOR here, because all set bits are distinct by the constraint.                                                                                                                   |
| Longest Substring Of All Vowels in Order             | LC **1839** | #3               | Predicate is a state machine; the window discipline is unchanged.                                                                                                                                              |
| Number of Substrings Containing All Three Characters | LC **1358** | #5               | Contribution counted from the left end instead of the right.                                                                                                                                                   |
| Count Subarrays With Score Less Than K               | LC **2302** | #5               | Same contribution formula, different summary.                                                                                                                                                                  |
| Count Subarrays With Fixed Bounds                    | LC **2444** | #5               | Contribution counting with last-seen indices. Fiddly bookkeeping, no new move.                                                                                                                                 |
| Subarrays with sum exactly `k`, all positive         | —           | #6               | `atMost(k) − atMost(k−1)`.                                                                                                                                                                                     |
| Grumpy Bookstore Owner                               | LC **1052** | #8               | The additive form of the same reframe: a constant base plus a windowed bonus.                                                                                                                                  |
| Minimum Operations to Reduce X to Zero               | LC **1658** | #8               | Prefix plus suffix equals a complement window.                                                                                                                                                                 |
| Replace the Substring for Balanced String            | LC **1234** | #8               | Predicate on the complement, variable length.                                                                                                                                                                  |
| Minimum Size Subarray in Infinite Array              | LC **2875** | #10              | Doubling, then #4.                                                                                                                                                                                             |
| Subarray Sums Divisible by K                         | LC **974**  | #14              | Prefix sums modulo `k` in a hash map. Not a window.                                                                                                                                                            |
| Continuous Subarray Sum                              | LC **523**  | #14              | Same, with a length constraint.                                                                                                                                                                                |
| Minimum Window Subsequence                           | LC **727**  | [[Two Pointers]] | The predicate is *order-sensitive* and not removable, so no window exists — it needs a forward-then-backward walk.                                                                                             |
| Shortest Unsorted Continuous Subarray                | LC **581**  | [[Two Pointers]] | Two independent end-to-middle sweeps, not a maintained window.                                                                                                                                                 |
| Sliding Puzzle                                       | LC **773**  | —                | Not a window. BFS over states, [[Graphs]] #6.                                                                                                                                                                  |

---

## Self-audit

**Borderline calls, and which way I went**

- **#3 and #4 kept separate.** Almost every source treats "sliding window" as one technique with a parameter. Split because the `while` loop's *semantics* invert — shrink-to-escape versus shrink-to-test — and because that inversion is the topic's signature bug. If any split in this file is over-fine it is this one, and I would still defend it.
- **#7 (the non-shrinking window) kept as an entry rather than a footnote on #3.** It looks like an optimisation and is really a correctness argument: the window is permitted to be invalid. That is a different mental model, and LC 424 is famous precisely because people cannot reconstruct the argument.
- **#8 and #9 nearly merged** into one "reframe until a window appears" entry. Kept apart because the reframes are unrelated — one changes *which interval* you window, the other changes *what order* the elements are in — and a merged entry would have been named after neither. LC 1052 *was* merged into #8, since a constant base plus a windowed bonus is the same inversion.
- **LC 76 excluded.** Uncomfortable, because it is the single most-asked problem in this topic. But it is #4's discipline over #2's summary with nothing added, and the exclusions table exists to say exactly that. Flagged in the table as worth solving anyway.
- **W5 has one native entry and four cross-listed ones.** That is the honest shape: the *diagnosis* (which summaries are removable) is a sliding-window idea, while every repair is a container developed in another file. The taxonomy callout carries the weight that would otherwise need four native entries.
- **#14 (LC 560) native despite not being a sliding window.** Deliberate. A file that never tells you when the technique fails will have you applying it to negative-valued arrays forever, and the diagnosis is the most valuable single line here.

**Naming check.** Two entries were renamed during this step. #5 was drafted as "product less than k", which names the canonical problem's arithmetic rather than the idea; it is now about the contribution formula. #9 was drafted as "maximise frequency", now "sort first, because order is irrelevant" — the transferable half. #6 was checked and kept: "exactly-`k` by difference of two at-most windows" is already the idea, not the dressing.

**Step 4B — reverse sweep**

Twenty-three plain-language descriptions navigated against the family headings. **One failure:**

- **"Longest stretch where every letter shows up at least three times"** landed nowhere, and worse, felt like it should land on #3. It cannot: that predicate breaks when the window *grows* as well as when it shrinks, so no shrink discipline is valid. That is **#15**, and the axis it exposed — *is the predicate monotone in length* — was in my draft axis table with only a "what breaks" note attached, and **no entry carrying the repair**. #14 covered the negatives break; nothing covered the non-monotone break. A named axis with no entry is a gap the cross-product cannot see, because the cell looks occupied from the axis table's side.

Two collisions, both checked and cleared. "Longest window with at most `k` zeros" reaches #3 and #7 — one idea (a counted bad element) legitimately visible under two disciplines, correctly left merged. "Smallest window containing all of these characters" reaches #2 and #4, which is the composition LC 76 is, and the exclusions table says so.

**What I am uncertain about**

- **Prefix sums.** #14 points at them and stops, which was the largest gap this file exposed. Now closed by [[Prefix Sums & Difference Arrays]], written immediately after this one.
- **Whether #12 (LC 30) is in scope.** Block-stride windows appear in essentially one problem. Kept because "what is one step" was an unexamined assumption everywhere else, and a single sharp representative is worth more than the axis staying invisible.
- **Monotone-deque DP optimisation** (LC 1696) is cross-listed in [[Stack and Queue]] but not here, because the window there is over *DP states* rather than input elements. Defensible, mildly uncertain — someone drilling windows might well want it.
- **Windows over two sequences simultaneously.** I could not name a problem where two windows advance in genuine lockstep. Probably an empty cell; possibly thin recall.
- **Recall is likely thin on the counting family (#5).** LeetCode has dozens of "count subarrays where …" problems and I collapsed them aggressively. If an exclusion is wrong, it is there.

**Completeness confidence: ~91%.** The engine (W1, W2) I would call complete — those seven entries are the topic. The uncertainty sits in W3, where "modelling moves that make a window appear" is an open-ended category with no external list to sweep against, and in the missing prefix-sum boundary.

## Related Notes

- [[README]]
- [[Two Pointers]]
- [[Stack and Queue]]
- [[Heap]]
- [[Binary Search]]
- [[Binary Search Trees]]
- [[Dynamic Programming]]
- [[Prefix Sums & Difference Arrays]]
- [[Arrays]]
- [[Sorted Containers & Order Statistics]]
- [[Bit Manipulation]]
