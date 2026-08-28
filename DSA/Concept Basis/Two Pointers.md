---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Two Pointers

> [!abstract] Minimal spanning set for two-pointer techniques **other than the sliding window** — converging, diverging, two speeds, one pointer per sequence, read-and-write, partitioning. One entry per **new idea you have to learn**. Same-direction windows live in [[Sliding Window]]. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## Scope boundary against [[Sliding Window]]

Both pointers moving left to right with a maintained interval between them is a **window**, and that file owns it — fixed length and variable length alike. Everything else is here: any geometry where the pointers converge, diverge, move at different speeds, live in different sequences, or write as well as read.

## What actually makes a two-pointer solution correct

> [!tip] **The code is four lines. The content is the argument for why moving one pointer lets you discard a whole set of candidates forever.**
>
> There are `n²` pairs and you examine `n` of them, so `n² − n` candidates must be provably safe to skip. Each family below is really a *different justification* for that skip, and the justification is what you are being asked for in an interview:
>
> - **Sortedness** (#1) — the sum is monotone in each pointer, so a too-small total can only be fixed by moving the left pointer up.
> - **The binding constraint** (#3) — the shorter wall limits the area no matter what the other side does, so moving the taller one cannot help.
> - **Speed arithmetic** (#7) — after `k` steps the gap has changed by exactly `k`, which forces a meeting.
> - **A finished prefix** (#10) — everything behind the slow pointer is already correct and will never be revisited.
> - **A mismatch budget** (#20) — the comparison no longer names the move; you fork, once.
>
> If you can state which of these applies, you can derive the code. If you memorise the code, you will move the wrong pointer under pressure.

## Mechanism axes

| Axis | Values |
|---|---|
| **Pointer geometry** | converging from both ends · **diverging from a centre** · same direction at different speeds · one pointer per sequence · read and write on one array · a fixed outer plus a sweeping inner |
| **What justifies the discard** | sortedness · the binding-constraint argument · speed arithmetic · pigeonhole · a greedy exchange argument · **a budget of mismatches** · **pairwise elimination of a special vertex** · nothing (the scan is genuinely `O(n²)`) |
| **Is the input sorted, and by whom** | given sorted · **you sort it first** · order is meaningful and cannot be touched |
| **How many pointers** | two · three (one pinned, two converging) · `k`, one per sequence |
| **What is written** | nothing · overwrite at the slow pointer · swap · write from the far end |
| **Can a pointer move backwards** | no — monotone, so `O(n)` total · yes, resets on failure · **two passes in opposite directions** |
| **What is returned** | a pair or tuple · a count · a length · a node · the array modified in place · a boolean |
| **Which assumption breaks** | duplicates break the distinct-tuple contract · unsorted input breaks the discard argument · a swap from the far end invalidates the scan position · writing forward destroys data you have not read |

## Shape of this topic

```
P1  Converging & diverging on one sequence   8 ideas
P2  Two speeds on one sequence               3 ideas
P3  Read and write on one array              5 ideas
P4  One pointer per sequence                 3 ideas
P5  Partitioning                             1 idea
P6  Pointer-arithmetic identities            1 idea
                                             + 5 cross-listed ↗
```

**21 native entries, plus 5 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.**

---

## P1 · Converging & diverging on one sequence

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Two Sum II — Input Array Is Sorted | LC **167** | **On sorted data, the comparison tells you which pointer to move, and the discarded candidate is gone for good.** If the sum is too small, no partner further left can help the current left element, so `l` advances and an entire row of the pair matrix is eliminated. The `O(n)` argument *is* the entry — the loop is trivial. Comparing two characters while converging (LC 125, LC 344) is the same geometry with no discard reasoning at all, which is why it is not a separate entry. One allowed mismatch is #20. |
| 2 | 3Sum | LC **15** | **Pin one element and the problem drops to `(k−1)`-Sum; deduplication is a separate concern from correctness.** The reduction is what generalises — 4Sum is a second pinned loop, `k`-Sum is recursion down to #1 — and the part everyone gets wrong is orthogonal to it: skipping equal values at all three positions to avoid repeated triples. Keeping those two ideas mentally separate is the point, because a correct search with broken dedup looks like a wrong algorithm. |
| 3 | Container With Most Water | LC **11** | **The binding-constraint argument, on data that is not sorted at all.** The area is limited by the shorter wall, so moving the taller one can never improve on the current pair — whatever it meets, the short wall still caps it. This is the most important "why" in the file, because it proves the technique does not require sortedness, only a *monotone objective in the constraint you relax*. Recognising that shape is what lets you invent a two-pointer solution rather than recall one. |
| 4 | Trapping Rain Water | LC **42** | **Commit a final answer at each step instead of discarding a candidate.** Carry running maxima from both ends and always advance the smaller one — because for that side, `min(maxLeft, maxRight)` is already known and cannot change, so the water above that index can be settled now. Distinct from #3: same geometry, opposite use — #3 *eliminates*, this *finalises*. `O(1)` space where the monotonic-stack solution needs `O(n)` ([[Stack and Queue]] #14). |
| 5 | Valid Triangle Number | LC **611** | **Counting: when a pair qualifies, an entire block of partners qualifies with it.** Sort, pin the largest side, converge — and when `a[l] + a[r] > c`, every index between `l` and `r` also works, so add `r − l` and move `r` rather than testing each one. New because the return type is a count, so a single arithmetic step replaces an inner loop. Same reasoning powers LC 259 and LC 16's "how close can I get". |
| 6 | Longest Palindromic Substring | LC **5** | **Pointers that *diverge* from a centre, and there are `2n − 1` centres, not `n`.** Expand outward while the characters match; the even-length case is why the centre count is not `n`, and forgetting it is the classic bug. New geometry — every entry above converges, and this is the only place the interval *grows* from the inside. The DP table (`dp[i][j]` = ends match and inside is a palindrome) is [[Dynamic Programming]] #75; Manacher's linear-time version is [[Strings]] #7. |
| 20 | Valid Palindrome II | LC **680** | **Converging with a budget of one mismatch.** A mismatch is not "move the pointer the comparison names" — both skips might still work, and you are allowed to drop exactly one character. Try skipping left, try skipping right; if either remaining scan is a palindrome, accept. New against #1 (no retries) and against #16 (a *planned* second pass after a successful forward match, not a fork at the first failure). The discard argument has to survive the skip: everything already matched is still settled; the budget is the only extra state. This is the axis cell *resets on failure*. `k` mismatches is no longer two pointers — Valid Palindrome III (LC **1216**) is edit-distance DP. |
| 21 | Find the Celebrity | LC **277** | **Pairwise elimination of a special vertex: one comparison discards one of the pair forever.** A celebrity knows nobody; everyone knows the celebrity. Ask `knows(i, j)`: if true, `i` is out (would have out-degree); if false, `j` is out (someone doesn't know them). Two pointers on `0…n−1` burn `n − 1` people; one candidate remains. Then a linear *verify* — the candidate knows nobody, everyone knows them — because elimination only rejects, it does not prove. New discard argument against #1 (not sortedness) and #3 (not a numeric binding constraint): the comparison names *who cannot be the answer*. Building the graph and reading degrees is `O(n²)` and is the thing this exists to avoid. The characterisation — unique vertex with in-degree `n−1`, out-degree `0` — is ↗ [[Graphs]]. |

## P2 · Two speeds on one sequence

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Linked List Cycle II | LC **142** | **Floyd's tortoise and hare, and the phase-two arithmetic that locates the entry.** Detection is easy to believe — a `2×` pointer gains one step per tick, so inside a cycle it must catch up. The content is the second half: from the meeting point, a pointer restarted at the head and the meeting pointer both advance at speed 1 and collide exactly at the cycle's start. Being able to *derive* that from the distance equation, rather than recall it, is what the question tests. `O(1)` space is the reason it exists. |
| 8 | Find the Duplicate Number | LC **287** | **Read the array as a function `i → nums[i]` and the duplicate becomes a cycle entry.** With values in `1…n`, the array *is* a functional graph, two indices point at the same successor, and #7 finds it in `O(1)` space without modifying the input. New because the reframe is the whole solution — the machinery is unchanged. The binary-search-on-value-range solution is the same problem seen through pigeonhole ([[Binary Search]] #13), and knowing both is the point. |
| 9 | Remove Nth Node From End of List | LC **19** | **A fixed *gap* between two pointers converts "from the end" into "from the start" in one pass.** Advance one pointer `n` steps, then move both; when the leader falls off, the follower is exactly where you need it. Distinct from #7 — no cycle, equal speeds, and the invariant is a constant offset rather than a closing gap. The same idea at speed `2×` finds the middle (LC 876), which is the entry point to merge sort on a list. |

## P3 · Read and write on one array

In-place rearrangement. **The recurring hazard is that writing destroys data you have not read yet**, and each entry is a different way of dodging it.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 10 | Remove Duplicates from Sorted Array | LC **26** | **A slow pointer marks the boundary of the finished prefix; a fast pointer scans ahead.** The invariant — everything before `slow` is final and will never be revisited — is what makes the single pass correct, and it is the same invariant behind partitioning, compaction and stable filtering. Safe here because you only ever write *behind* where you read. |
| 11 | Sort Colors | LC **75** | **Three regions, two moving boundaries — and after a swap from the far end you must not advance the scanner.** Dutch national flag: `low` bounds the settled front, `high` the settled back, `mid` scans. The subtlety is the whole entry: a value swapped in from `high` has never been examined, so `mid` must stay put and re-inspect it, whereas a swap with `low` brings something already known. This is the classic off-by-one that turns a correct idea into an infinite loop, and it is also quicksort's three-way partition. |
| 12 | Merge Sorted Array | LC **88** | **Write from the back, because the free space is at the back.** Filling forward would overwrite unread elements of the first array; filling from the largest end means every write lands on a slot already consumed or empty. New idea in general form: **when an in-place operation has an overwrite hazard, the direction of the write is forced, not chosen** — the same reasoning as flattening a tree in reverse preorder ([[Binary Trees]] #18). |
| 19 | Merge two arrays in place with **no** spare buffer — the gap method | *Striver A2Z; the sized-buffer form is LC **88*** | **When there is no free space at either end, compare across a *shrinking gap* instead of with adjacent pointers.** Treat the two arrays as one virtual array of length `n + m`; start with `gap = ⌈(n+m)/2⌉`, sweep every pair `(i, i + gap)` and swap if out of order, then halve the gap and repeat until it reaches zero. `O((n+m)·log(n+m))` time, genuinely `O(1)` space. This is **not** #12 — #12 works only because LC 88 hands you `m` empty trailing slots, and the moment that buffer is removed the backward write has nowhere to go. New idea: a **Shellsort pass**, and the reason it is correct is Shellsort's, that a gap-`g` sorted array stays gap-`g` sorted while you fix a smaller gap. Kept native despite being borderline CP because it is a standing favourite in Indian interviews, where "now do it without the extra space" is the follow-up to LC 88. |
| 13 | Rotate Array | LC **189** | **Rotation is three reversals: `reverse(A)`, `reverse(B)`, then reverse the whole thing — because `reverse(reverse(A) · reverse(B)) = B · A`.** An identity worth memorising outright, since the alternatives are an `O(n)` buffer or a cyclic-replacement walk that needs a GCD argument to know how many cycles to start. New because the primitive being composed is *reversal itself* rather than a per-element move, and once you see it, rotating words in a sentence and rotating a matrix fall out the same way. |

## P4 · One pointer per sequence

| # | Problem | Source | The new idea |
|---|---|---|---|
| 14 | Merge Two Sorted Lists | LC **21** | **Advance whichever pointer produced the smaller element; total work is `m + n`.** The merge step, which is worth an entry on its own because it is the engine of merge sort, of external sorting, and of every "combine two ordered streams" problem — and because it is the two-sequence geometry's base case. Scaling to `k` sequences is where a heap replaces the comparison (↗ below). |
| 15 | Is Subsequence | LC **392** | **Greedy earliest matching: advance the pattern pointer only on a match, the text pointer always.** The exchange argument is the content — if any embedding exists, the leftmost-greedy one exists, so never backtracking is safe. New because the two pointers advance at *data-dependent* rates rather than by a rule. The follow-up matters: for many patterns against one text, you precompute per-character index lists and binary search them instead ([[Binary Search]] #21). |
| 16 | Minimum Window Subsequence | LC **727** | **Match forward to find *an* answer, then walk backwards to tighten it.** Because "contains `t` as a subsequence" is order-sensitive, it is not removable from the left, so no sliding window exists — but once a forward greedy match has landed, reversing the walk from the end position finds the latest possible start. New geometry: **two passes over the same region in opposite directions, where the first proves existence and the second proves minimality.** The general lesson is that an unshrinkable predicate can still be tightened, just not incrementally. |

## P5 · Partitioning

| # | Problem | Source | The new idea |
|---|---|---|---|
| 17 | Kth Largest Element in an Array (quickselect) | LC **215** | **Partition around a pivot with converging pointers, then recurse into one side only.** Because you discard a whole partition rather than sorting it, the expected cost is `O(n)` rather than `O(n log n)` — the geometric series `n + n/2 + n/4 + …` is the argument, and the worst case is `O(n²)` unless the pivot is randomised. New because the pointers are producing a *structural guarantee* about the array (everything left of the pivot is smaller) rather than searching for a target. This is Hoare's partition, quicksort's engine, and the `O(n)` alternative that [[Heap]] #3 names but does not develop. The general principle it is an instance of — **when you only need part of the output, do not order the part you are discarding** — is [[Sorting & Custom Comparators]] ↗ LC 215, along with `nth_element`, `partial_sort` and median-of-medians. |

## P6 · Pointer-arithmetic identities

| # | Problem | Source | The new idea |
|---|---|---|---|
| 18 | Intersection of Two Linked Lists | LC **160** | **Equalise two unequal path lengths by switching heads at the end.** When a pointer runs off list `A` it restarts at the head of `B`, and vice versa; both then have travelled `lenA + lenB` and must meet at the junction if one exists. Distinct from #9, which equalises with a *known* offset — here neither length is known and the concatenation makes the arithmetic work itself out. Filed as its own family because the move is a construction on the traversal path rather than a rule about when to advance, and the same trick reappears whenever two cursors must be brought into phase. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Longest Substring Without Repeating Characters | LC **3** | The same-direction case: both pointers advance monotonically and the interval between them is a maintained window. The largest sub-family of two pointers by problem count, and it has its own file — [[Sliding Window]] #3. |
| ↗ | Merge k Sorted Lists | LC **23** | #14 with `k` pointers, where finding the smallest head becomes a heap query rather than a comparison. [[Heap]] #7. |
| ↗ | Median of Two Sorted Arrays | LC **4** | Not a linear walk at all — binary search *the partition point* of one array, which fixes the other's by arithmetic. The problem that looks like #14 and is not. [[Binary Search]] #15. |
| ↗ | Interval List Intersections | LC **986** | #14's merge walk over intervals: advance whichever ends first, emit the overlap. [[Intervals]] #3. |
| ↗ | Trapping Rain Water II | LC **407** | #4 in two dimensions, where "process the smaller side" becomes "process the lowest boundary cell", which needs a heap. [[Heap]] #22. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Valid Palindrome | LC **125** | #1 | Converging comparison with no discard argument. One skip is #20. |
| Valid Palindrome III | LC **1216** | — | `k` mismatches is edit distance, not a budget you fork on. [[Dynamic Programming]] #18. |
| Palindromic Substrings | LC **647** | #6 | Same expand, counting centres instead of tracking the longest. The `dp[i][j]` table is [[Dynamic Programming]] #75. |
| Reverse String · Reverse Vowels | LC **344** · **345** | #1 · #13 | Converging swap; reversal is the primitive #13 composes. |
| Squares of a Sorted Array | LC **977** | #1 | Converge, write outward-in — the write direction is #12's hazard. |
| Two Sum Less Than K | LC **1099** | #1 | Same sweep, tracking a best instead of an exact hit. |
| 3Sum Closest · 3Sum Smaller | LC **16** · **259** | #2 · #5 | Pinned element plus converging pair; #5 supplies the block-counting step. |
| 4Sum | LC **18** | #2 | A second pinned loop on the same reduction. |
| Boats to Save People | LC **881** | #1 | Sort, then a converging greedy pairing. |
| Palindrome Linked List | LC **234** | #9 + #13 | Find the middle by speed, reverse half, compare. Composition. |
| Happy Number | LC **202** | #7 | Floyd on a digit-sum function — the reframe is #8's. |
| Middle of the Linked List | LC **876** | #9 | Speed `2×` instead of a fixed gap. |
| Move Zeroes | LC **283** | #10 | Compaction, writing behind the read head. |
| Remove Duplicates from Sorted Array II | LC **80** | #10 | The finished-prefix invariant with a count of 2. |
| String Compression | LC **443** | #10 | Write-behind compaction with run counting. |
| Sort Array By Parity | LC **905** | #11 | Two-way partition; Dutch flag with one boundary removed. |
| Partition Labels | LC **763** | — | Last-occurrence sweep. [[Prefix Sums & Difference Arrays]] #15; the covering reading is [[Greedy]] #8. |
| Backspace String Compare | LC **844** | #14 | One pointer per string, walking backwards with skips. |
| Intersection of Two Arrays II | LC **350** | #14 | The merge walk, emitting matches. |
| Shortest Unsorted Continuous Subarray | LC **581** | — | Two independent end-to-middle max/min sweeps that never interact. Two scans, not two pointers. |
| Rotate Image | LC **48** | #13 | Transpose then reverse rows — the same compose-reversals identity in 2D. Native at [[Matrix]] #4. |
| Reverse Words in a String | LC **151** | #13 | Reverse all, then reverse each word. |
| Enumerate a deleted middle block by fixing `l` and sweeping `r` | *classic — the [[README]] criterion example* | [[Sliding Window]] #8 | When the cost is monotone this is a complement window; when it is not, the `O(n²)` enumeration is the same fixed-outer, sweeping-inner geometry with no discard argument to exploit. |
| Longest Word in Dictionary through Deleting | LC **524** | #15 | Greedy subsequence check run per candidate. |
| Number of Matching Subsequences | LC **792** | #15 | The many-patterns follow-up named in #15. |

---

## Self-audit

**Borderline calls, and which way I went**

- **#20 native in P1, not a new family.** It fills the existing axis cell *resets on failure* / *a budget of mismatches*. Folding it into #1 would hide the fork; a seventh family for one retry would be the opposite overfit.
- **#3 and #4 kept separate.** Same geometry, same "advance the smaller side" rule, and I nearly merged them. Split because the *use* of the step inverts: #3 discards a candidate it will never need, #4 finalises an answer it can never revise. Under probe 2 — what triggers the move and why — they differ, and #4 is where "I can settle this cell now" is learned.
- **#1 and #5 kept separate.** Both converge on sorted data; #5 returns a count and replaces an inner loop with arithmetic. Same call as [[Sliding Window]] #5, and made for the same reason.
- **#6 (diverging from a centre) native rather than cross-listed to Strings.** The palindrome *machinery* belongs to Strings and DP, but "pointers can move outward, and there are `2n − 1` centres" is a geometry claim, and this file is organised by geometry. The DP table is [[Dynamic Programming]] #75; Manacher is [[Strings]] #7.
- **#13 (rotate by three reversals) kept as an entry.** It looks like a trick rather than an idea, and it is asked constantly, generalises to matrices and sentences, and the alternative cyclic-replacement solution needs a GCD argument most people cannot produce. An identity you should simply own.
- **#17 (quickselect) native here.** [[Heap]] #3 names it and moves on; nothing else develops it. It is a partition built by converging pointers, so this is its home.
- **#18 given its own family for one entry.** Slightly awkward. The alternative was burying it in P2, but it is not a speed argument — it is a path construction — and burying it under the wrong justification is exactly the naming failure step 7b exists to catch.

**Naming check.** #4 was drafted as "trapping rain water, two-pointer version", which names the problem rather than the move; retitled around *committing a final answer per step*. #16 was drafted as "minimum window subsequence", which reads like a [[Sliding Window]] entry and would have been looked for there; retitled around *forward match, backward tighten*. #12 was drafted as "merge from the back" and generalised to *the overwrite hazard forces the write direction*, since that is the half that transfers.

**Step 4B — reverse sweep**

Twenty-three plain-language descriptions navigated against the family headings. **One failure:**

- **"Find the shortest piece of the long string that contains the short one in order, not necessarily together"** landed nowhere. It reads like a window problem, so the sweep first tried [[Sliding Window]] and failed there too — the predicate is order-sensitive and cannot be removed from the left, so no window exists. Under #15 it looked like a variation, which it is not: testing *whether* a subsequence exists is greedy and one-pass, while finding the *shortest* containing window needs a second pass in the opposite direction. That is **#16**, and the axis it exposed — *can a pointer move backwards, including a deliberate second pass in the opposite direction* — was missing. My draft axis table had only "monotone" versus "resets on failure", which are both forward notions.

Two collisions, both checked and cleared. "Move everything matching a rule to one side" reaches #10 and #11 — compaction and two-way partition are the same finished-prefix invariant, correctly merged with LC 283 and LC 905 in the exclusions. "Reverse this in place" reaches #1's geometry and #13's primitive; cleared, since #13 explicitly names reversal as the thing being composed.

**What I am uncertain about**

- **The Linked List boundary is now closed.** Four entries (#7, #9, #14, #18) are list problems; they stay native here and ↗ [[Linked List]].
- **Whether #5 and [[Sliding Window]] #5 should be one idea in two files.** They are the same contribution-counting insight on two geometries. Currently native in both, which the cross-listing rule permits, but a reader might reasonably see one concept counted twice.
- **Cyclic-replacement rotation** (the GCD-cycles algorithm) is excluded in favour of #13's reversals. It is a genuinely different algorithm, not a variation — kept out on scope grounds, since nobody wants it in an interview. Moderate confidence.
- **Three-pointer problems beyond the pinned-plus-converging shape.** I could not name one that is not a variation of #2 or #11. Possibly an empty cell, possibly thin recall.
- **Recall is likely thinnest on in-place array manipulation (P3)**, where the problem space is large, the ideas are few, and the difference between the two is exactly what an exclusions table can get wrong.

- **#21 (Celebrity) added on a later gap pass.** It fills a discard argument that was missing: the comparison names who *cannot* be the answer, not which pointer the monotone sum moves. Native here, ↗ [[Graphs]] for the degree characterisation.

**Completeness confidence: ~90%.** The justifications in P1 and P2 I would call complete — those are the arguments the topic exists to teach, and there are not many. #20 filled the mismatch-budget cell rather than adding a family. #21 filled the special-vertex elimination cell. Remaining risk is P3's collapse.

## Related Notes

- [[README]]
- [[Sliding Window]]
- [[Stack and Queue]]
- [[Heap]]
- [[Binary Search]]
- [[Binary Trees]]
- [[Prefix Sums & Difference Arrays]]
- [[Arrays]]
- [[Dynamic Programming]]
- [[Math & Number Theory]]
- [[Linked List]]
- [[Sorting & Custom Comparators]]
- [[Greedy]]
- [[Intervals]]
- [[Graphs]]
- [[Strings]]
- [[Hashing]]
- [[Matrix]]
