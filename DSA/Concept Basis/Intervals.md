---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-28
---
# Concept Basis — Intervals

> [!abstract] Minimal spanning set for **closed or half-open ranges on a line**: merge them, count them, query them, store them while they change. One entry per **new idea you have to learn**. The default implementation is a **sweep of typed events**. That is not the whole topic — sorted-list algebra and a live interval set are different ideas that happen to live on the same objects. Cross-listed entries (↗) are developed more fully elsewhere but belong here too.

> [!tip] **The sweep you will actually write.** Each interval becomes two events, `(time, type, …payload)`. Queries, if any, become events too. Sort by time, and **at equal times sort by `type`**. The integers you pick for `type` — `0 / 1 / 2` — *are* the problem's closed/open convention:
>
> - Touching should *not* conflict (`[1, 2]` and `[2, 3]` are fine) → process **ends before starts** (and queries where you want them to sit).
> - A point query at `t` should see intervals that *end* at `t` → **start, then query, then end**.
> - A point query at `t` should *not* see intervals that end at `t` → **start, then end, then query**.
>
> The running state is usually an `active` count. Sometimes it is a heap of the live intervals, a set of colours, or a pool of room IDs. The event list is the same; what you store while you walk it is the entry.

Interval DP (`dp[i][j]` on array indices) is **not this file**. That is [[Dynamic Programming]] D6. Same English word, different object.

## Cuts already promised

- **[[Sorting & Custom Comparators]] #14** owns *the sortable unit is not the input element*. This file owns *what the sweep does with those events* — the running state, the type codes, queries-as-events. Native here, ↗ there.
- **[[Heap]] #14** is the same Meeting Rooms II with a min-heap of end times instead of an event list. ↗ both ways; the argument is identical, the bookkeeping is not.
- **[[Prefix Sums & Difference Arrays]] #18** is the same `+1 / −1` when the axis is too large to index. This file starts from the interval, that file from the difference array.
- **[[Greedy]] #1 / #8 / #22** own earliest-finish *selection*, farthest-reach *covering*, and `k`-screen selection. They are arguments about *which* intervals to keep. This file is about *what the overlaps are*.
- **[[Sorted Containers & Order Statistics]] #5** and **[[Binary Search Trees]] #16** own `lower_bound` / `--it`. This file owns the booking/overlap *question* those queries answer.

## Mechanism axes

| Axis | Values |
|---|---|
| **What an event is** | an interval's start · an interval's end · **a query** · a colour / payload change |
| **Tie-break (`type`)** | end before start (touching is free) · start before end (a point is occupied) · **query slotted between them** — the `0 / 1 / 2` you assign |
| **What is live during the sweep** | a count · a **sum of payloads** · a heap / set of the open intervals · a pool of labels (room IDs) |
| **When the intervals arrive** | all at once, sort and sweep · **one by one, a live tree** |
| **What the line is** | small integers (difference array) · large / sparse (sort the events) · 2D (sweep a line across rectangles) |
| **What is asked** | the union as a list · an intersection as a list · a yes/no overlap · a max / running count · an answer **per query** · a room assignment · a stored set you can mutate |
| **Closed or half-open** | `[L, R]` vs `[L, R)` — this *is* the type-code decision, not a separate algorithm |
| **What breaks** | processing start before end when touching must be legal · treating a query as "just another interval" with no type · assuming the lists are disjoint when they are not |

## Shape of this topic

```
I1  Algebra on sorted lists               5
I2  Sweep of typed events                 5
I3  A live interval set                   3
                                          + 12 cross-listed ↗
```

**13 native entries, plus 12 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.**

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| **Merge Intervals** | #1 |
| **Insert Interval** | #2 |
| Interval List Intersections | #3 |
| Employee Free Time · punch a hole | #4 |
| Remove Covered Intervals | #5 |
| **Sweep line** · typed events · Meeting Rooms II | #6 |
| **Queries as events** · Flowers in Full Bloom | #7 |
| Min covering interval per query | #8 |
| **Room Allocation** *(assign labels)* | #9 |
| Describe the Painting *(emit when mix changes)* | #10 |
| **My Calendar** I / III | #11 · #13 |
| **Range Module** | #12 |
| Activity selection · video stitching · Skyline | ↗ |

---

## I1 · Algebra on sorted lists

The intervals are already (or cheaply made) a sorted list of *disjoint* ranges. You walk them with one or two pointers. No event list is required — and forcing a sweep here is ceremony. When the lists are *not* disjoint, #1 is the repair.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Merge Intervals | LC **56** | **Sort by start, then extend a single `curEnd` while the next interval overlaps it; emit and restart when it does not.** Overlap is `next.start ≤ curEnd` (closed) or `<` (half-open) — that comparison *is* the same decision #6 encodes as a type code, just without manufacturing `2n` events. New as the *union, produced as a list*. The sweep view is identical: `active` going `0 → 1` opens a merged interval, `1 → 0` closes it. Write whichever is shorter; know that they are the same picture. Teemo Attacking (LC **495**) is this with duration instead of explicit ends. |
| 2 | Insert Interval | LC **57** | **One new interval into an already-disjoint sorted list: copy everything that ends before it, merge everything that overlaps it, copy the rest.** Three linear passes, no full re-sort. New against #1 because the input invariant (disjoint, sorted) lets you *splice* rather than rebuild, and because the overlap window can swallow several existing intervals at once. The binary-search version (`upper_bound` on starts, `lower_bound` on ends) is the same splice with `O(log n)` to find the window and `O(n)` to rewrite. |
| 3 | Interval List Intersections | LC **986** | **Two disjoint sorted lists: advance whichever interval *ends* first, and whenever they overlap emit `[max(starts), min(ends)]`.** The two-pointer merge of [[Two Pointers]] #14, with overlap as the emit condition. New because the objects being merged are ranges, not numbers, and because "advance the one that ends first" is forced — the other one might still overlap the next opponent. Do not flatten both lists into one event sweep unless you also need a *count*; the pairwise walk is `O(n + m)` with no type codes. |
| 4 | Employee Free Time · Remove Interval | LC **759** · **1272** | **The complement of a union on a line.** Free time: merge everyone (#1), then read the *gaps* between consecutive merged pieces. Punch a hole: each stored interval is split, trimmed, or deleted by one subtracting range. New because #1 produces the occupied set and this asks for its complement (or for occupied-minus-one-range). Employee Free Time is also a `k`-way merge of already-sorted calendars ([[Heap]] #7) followed by the same gap read. |
| 5 | Remove Covered Intervals | LC **1288** | **Containment, not overlap.** Sort by start ascending, end descending, then a scan: if this end `≤` the max end seen, it is covered. New because `[1, 4]` *covers* `[2, 3]` without the two being the same overlap test as #1 — a contained interval is swallowed, two partial overlaps are not. Nested Ranges Check (CSES **2168**) is the same sort plus a sweep for "does any container exist"; Nested Ranges *Count* needs a BIT of ends ([[Sorted Containers & Order Statistics]] #1) and is ↗ there. |

## I2 · Sweep of typed events

This is the family the tip at the top is about. One event list, one sort, a running state. Everything below is a different *state*, not a different sweep.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 6 | Meeting Rooms II | LC **253** · CSES **1619** | **The skeleton: `(time, type)` events, `active += Δ`, answer = max `active` (or "did `active` ever exceed 1").** Start is `+1`, end is `−1`. **`type` is the whole problem:** if a meeting ending at `t` frees the room for one starting at `t`, ends must sort first (`type_end < type_start`); if they conflict, starts first. That is the `0 / 1` (or `0 / 1 / 2` once queries exist). New as *the* interval sweep, and as the place the three implementations meet: event list (this), min-heap of end times ([[Heap]] #14), difference array on a small axis ([[Prefix Sums & Difference Arrays]] #17 / #18). Meeting Rooms I (LC **252**) is `max active ≤ 1`. Car Pooling (LC **1094**) is the same sweep with `Δ = passengers` and a capacity cap instead of a recorded maximum. Divide Intervals Into Groups (LC **2406**) is this problem under another name. |
| 7 | Number of Flowers in Full Bloom | LC **2251** | **Queries are events.** People asking "how many flowers at time `t`?" go into the same list as starts and ends, with a `type` that sits *between* them according to whether a flower blooming or dying at `t` should count. Sweep once; when you hit a query, the current `active` *is* the answer — write it into `ans[id]`. New against #6 because the output is **per query, not a single aggregate**, and because getting `type` wrong silently shifts every answer by one at the boundaries. The alternative (sort starts, sort ends, binary search `how many started − how many ended`) is the same counts without a fused event list; the fused list is what generalises to #8. |
| 8 | Minimum Interval to Include Each Query | LC **1851** | **The live state is not a count — it is the set of currently open intervals, and the query reads an extreme of that set.** Sort queries as events (or sort queries and intervals separately and two-pointer them). A min-heap of `(length, end)` among intervals that have started and not yet ended answers "smallest covering interval" at each query. New because #6/#7's `active` integer cannot answer "which" or "best"; the moment the query needs a property of the *members*, the sweep grows a heap (or a tree) of the live set. Offline is forced: you choose the order the queries arrive. |
| 9 | Room Allocation | CSES **1164** | **The sweep must *label*, not just count.** Max-concurrent (#6) tells you how many rooms you need; assigning each interval a room ID needs a pool of free labels (a min-heap of room numbers). On an end, push that room back; on a start, pop a free room (or mint a new one). New because the running state is a **matching** — interval → resource — and because the greedy "reuse any free room" is legal here (any free room is as good as any other), which is the difference from [[Greedy]] #22, where *which* machine you reuse is constrained by end times. |
| 10 | Describe the Painting | LC **1943** | **Emit a geometry whenever the live *mix* changes, not whenever the count does.** Sweep, keep a map of colour → count (or a running XOR / sum of colour ids); when `active`'s signature changes, close the previous segment and open a new one. New against #1 (union as a list, no colour) and #6 (a scalar). The sweep produces the *arrangement* of the line. Same skeleton, richer payload. |

## I3 · A live interval set

The intervals arrive over time, or you must add, remove, and query. You cannot sort once. The structure *is* the interval set.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | My Calendar I | LC **729** | **To test overlap against a live set you only need the two neighbours of the insertion.** `lower_bound(start)` and `--it` — if the previous interval ends after `start` or the next one starts before `end`, it collides. `O(log n)` per booking, no scan. New because #6 assumed a batch; here the set mutates. The query is a *neighbour*, so a hash map is the wrong structure — [[Binary Search Trees]] #16 and [[Sorted Containers & Order Statistics]] #5. |
| 12 | Range Module | LC **715** | **The ADT: `addRange`, `removeRange`, `queryRange`, and the stored form is always a disjoint sorted union.** Add is #2's splice into a tree; remove is #4's punch-a-hole into a tree; query is "does this range sit inside one stored piece." New because I1 was a one-shot list rewrite and this is the same algebra **as an invariant you maintain**. The implementation is a `map<start, end>` (or a segment tree of coverage); the idea is that a canonical disjoint form makes every operation local. |
| 13 | My Calendar III | LC **732** | **Online k-booking: the event list of #6, stored as a `map` of deltas, updated one interval at a time.** `map[start] += 1; map[end] -= 1;`, then a scan (or a lazy max on a segment tree) for the current peak. New against #6 (batch vs streaming) and against #11 (you now *count* overlaps, so two neighbours are not enough — Calendar II is "reject if this would make any point triple-booked", which is the same map with a trial insert). Coordinate compression plus a segment tree is [[Segment Trees]] #11 when the queries get richer than a running max. |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them while studying intervals and they belong in this file. See [[README]] on cross-listing.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Meeting Rooms II *(events as the sortable unit)* | LC **253** | Manufacturing `2n` timestamps from `n` objects. The type-code / `active` half is #6. [[Sorting & Custom Comparators]] #14. |
| ↗ | Meeting Rooms II *(heap of ends)* | LC **253** | Sort by start, pop finished ends; `heap.size()` is the answer. Same argument as #6. [[Heap]] #14. |
| ↗ | Meeting Rooms II *(difference array)* | LC **253** · **1094** | `+1 / −1` on an axis you can index, or a sorted event list when you cannot. [[Prefix Sums & Difference Arrays]] #17 and #18. |
| ↗ | Non-overlapping Intervals · Arrows | LC **435** · **452** | Earliest-finish *selection* — which intervals to keep, not what the overlaps are. [[Greedy]] #1; the sort key is [[Sorting & Custom Comparators]] #13. |
| ↗ | Video Stitching · Minimum Number of Taps | LC **1024** · **1326** | From current coverage, pick the clip that extends farthest. [[Greedy]] #8. |
| ↗ | Movie Festival II | CSES **1632** | Activity selection on `k` screens; which machine to reuse is constrained. Distinct from #10 (any free room is fine). [[Greedy]] #22. |
| ↗ | Weighted interval scheduling | LC **1235** | Values kill the #1-style greedy; sort by end creates a DP order. [[Dynamic Programming]] #67. |
| ↗ | The Skyline Problem | LC **218** | Sweep building edges; the live state is a max-heap of heights, lazily purged. [[Heap]] #23. |
| ↗ | Coordinate compression · Falling Squares · Calendar III | LC **699** · **732** | Rank the endpoints, then a segment tree over the compressed line. [[Segment Trees]] #11. |
| ↗ | My Calendar I *(`lower_bound` / `--it`)* | LC **729** | The neighbour query, as a structure lesson. #11 is the interval question. [[Binary Search Trees]] #16, [[Sorted Containers & Order Statistics]] #5. |
| ↗ | Interval List Intersections *(the merge walk)* | LC **986** | Advance the list that ends first. #3 is the interval statement. [[Two Pointers]] #14. |
| ↗ | Nested Ranges Count | CSES **2169** | Containment *counts*, not a yes/no — a BIT of ends as you sweep. [[Sorted Containers & Order Statistics]] #1. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Meeting Rooms I | LC **252** | #6 | `max active ≤ 1`. Same sweep, boolean read. |
| Car Pooling | LC **1094** | #6 | `Δ = passengers`, fail if `active > seats`. Named in #6. |
| Divide Intervals Into Minimum Number of Groups | LC **2406** | #6 | Meeting Rooms II. |
| Teemo Attacking | LC **495** | #1 | Merge with `end = start + duration`. |
| Summary Ranges | LC **228** | #1 | Consecutive integers as degenerate intervals. |
| Maximum Population Year | LC **1854** | #6 · ↗ P7 | Difference array on a tiny year axis. |
| Count Days Without Meetings | LC **3169** | #1 · #4 | Merge, then subtract occupied from `[1, days]`. |
| Points That Intersect With Cars | LC **2848** | #6 | Coverage count on a small line. |
| My Calendar II | LC **731** | #13 | Trial-insert into the delta map; reject if peak would exceed 2. |
| Data Stream as Disjoint Intervals | LC **352** | #11 · #12 | Neighbour lookup, then merge both sides like #2. |
| Range Addition | LC **370** | ↗ P7 | Bare difference array; no intervals in the input. |
| Corporate Flight Bookings | LC **1109** | ↗ P7 | Difference array, bounded axis. |
| Amount of Time for Binary Tree to Be Infected | LC **2385** | — | Graph distance, not an interval. Prefix's "Amount of Time" pairing with LC 1943 was the painting, not this. |
| Insert Delete GetRandom | LC **380** | — | Hash set. Not intervals. |
| Non-overlapping Intervals | LC **435** | ↗ | Named in the ↗ table. |
| Minimum Number of Arrows | LC **452** | ↗ | Named in the ↗ table. |
| Maximum Length of Pair Chain | LC **646** | ↗ [[Greedy]] #1 | Earliest finish on pairs. |
| Jump Game II | LC **45** | ↗ [[Greedy]] #8 | Covering without explicit interval objects. |
| Stamping the Sequence | LC **936** | ↗ [[Greedy]] #8 | Covering with a different move set. |
| Burst Balloons · MCM | LC **312** · **1039** | — | Interval *DP*. [[Dynamic Programming]] D6. |
| Maximum Profit in Job Scheduling | LC **1235** | ↗ | Weighted. [[Dynamic Programming]] #67. |
| Rectangle Area II | LC **850** | ↗ [[Segment Trees]] #11 | 2D sweep, coverage on the perpendicular axis. |
| The Skyline Problem | LC **218** | ↗ | Named in the ↗ table. |

---

## Self-audit

**Borderline calls, and which way I went**

- **#6 native here despite three other files already owning Meeting Rooms II.** Sorting owns manufacturing events, Heap owns the end-time heap, Prefix owns `+1/−1` on an axis. This file owns the *interval sweep as a state machine* — `type` codes, `active`, the closed/open convention. Merging them into Sorting #14 would hide the type-code decision from anyone drilling intervals, which is the actual bug in this topic.
- **#1 kept separate from #6.** Union-as-a-list (sort by start, extend `curEnd`) and max-concurrent (event sweep) are the same picture and different code. Forcing every merge through `2n` events is the restriction the user asked me not to make. Both rows name the other view.
- **#3 native despite [[Two Pointers]] ↗.** The merge walk is two pointers; the *objects* are intervals and the advance rule is "whoever ends first." Native here, ↗ there.
- **#7 and #8 kept separate.** Both are "queries as events." Split because #7 reads a count and #8 reads an extreme of the live *set*. Collapsing them produces "queries are events", which is an axis value, not an idea — the same failure Sorting would have made by merging T3 into "sorting helps."
- **#9 (Room Allocation) kept against [[Greedy]] #22.** `k` screens where *which* machine you reuse matters is greedy; assigning any free room ID is a pool. Different arguments, both during a sweep.
- **#11 native despite Sorted Containers / BST already holding Calendar I.** Those files own the neighbour *query*. This file owns the overlap *question*. Sorted Containers' hedge said Intervals would claim this; claimed, cross-listed, not stolen.
- **#13 (online deltas) kept against #6 (batch sweep).** Streaming vs offline is the same cut Heap makes between a sorted list and a live heap. Calendar II folded into #13 as a trial insert.
- **Interval DP excluded entirely.** D6 is subarray indices. A reader searching "interval" in the graph will still hit DP via Related Notes.

**Naming check.** Four retitles. #6 was drafted as "Meeting Rooms II", which would be looked for under Heap; it is now *typed events, running active*. #7 was drafted as "Number of Flowers", a puzzle name; it is now *queries as events*. #8 kept "Minimum Interval to Include Each Query" because the heap-of-live-members move has no shorter common name, with the idea in the body. #12 was drafted as "Range Module" and that *is* the idea (the ADT), so the name stayed. #4 was drafted as "Employee Free Time" and retitled around *complement of a union*, which is what LC 1272 shares.

**Step 4 — recognition sweep**

Walked LeetCode `interval` / merge-intervals / sweep-line by frequency, NeetCode Intervals, Striver A2Z (merge, insert, non-overlapping, platforms), CSES Restaurant Customers / Room Allocation / Nested Ranges / Movie Festival, LintCode airplanes-in-the-sky.

Forced natives: CSES **1164** (#9, label not count), LC **1851** (#8, live set not count), LC **2251** (#7, queries as events — the user's second sentence), LC **715** (#12, the ADT), LC **1943** (#10, emit on mix change). Platforms, arrows, video stitching, skyline, weighted scheduling all ↗.

**Step 4B — reverse sweep**

Twenty plain-language descriptions. **One naming fix, no missing axis.**

- **"People ask how many flowers are open at time t; the flowers are intervals"** landed on #6 and was *almost* right — same sweep, wrong output. That is **#7**. The axis *what is asked* already had "an answer per query"; the cell was empty until the probe. Same shape as Greedy's `k`-screens finding: unfilled cell, not a new axis.
- **"At equal times, ends before starts because touching is allowed"** is #6's type code, and it resolved once #6 was named after the sweep rather than after Meeting Rooms. A naming failure on the first draft title, caught here.

Collisions cleared. "Merge these ranges" reaches #1 and #12 (batch list vs live ADT), intended. "Do two intervals conflict" reaches #6 (batch) and #11 (live neighbours), intended. "Queries on a timeline" reaches #7 and #8 (count vs best member), intended.

Descriptions that resolved cleanly: "max number of meetings at once"; "touching should not count, so ends first"; "a query at t should still see bookings that end at t"; "smallest interval that covers this query"; "assign each meeting a room number"; "paint the line by colour mix"; "book if it does not overlap anything already in the tree"; "add, remove, and ask whether `[L, R]` is fully covered."

**Step 4C — inward sweep**

- **4C-iii (hedges) first.** [[Sorting]] *"Intervals owns what you do with overlapping ranges"* — honoured; Sorting #14 stays the sortable-unit half. [[Greedy]] merge/insert/taps waiting — merge/insert native here, taps stay [[Greedy]] #8 with a ↗. [[Sorted Containers]] *"interval containers sit between this file and Intervals"* — Calendar I native here (#11), floor/ceiling stays there. [[Prefix]] flowers / painting / car pooling / Calendar — flowers and painting native (#7, #10); car pooling named in #6; Calendar I retargeted. [[Two Pointers]] 986 — native #3, ↗ updated. [[Arrays]] merge — native #1. [[Heap]] employee free time / car pooling — #4 and #6. [[BST]] Calendar II/III — #13. [[Segment Trees]] compression still ↗, correctly: the structure is theirs, the interval question is #13.
- **4C-i.** Inbound "Intervals basis" pointers from Greedy, Arrays, Two Pointers, Sorting, Prefix, Heap, Sorted Containers now have destinations. Confirmed against those tables before editing them.
- **4C-ii.** Merge, insert, meetings I/II, intersections, calendar, range module, flowers, room allocation, covered-intervals, free time were absent as *developed interval ideas* (present as exclusions or ↗ waiting). Native here.

**What I am uncertain about**

- **Whether #10 (painting) is too close to #1.** Union-as-list versus arrangement-by-payload. If it never comes up, skip it; #6 plus a map is enough to invent it.
- **2D sweep (Rectangle Area II).** ↗ Segment Trees, and ↗ [[Matrix]]. A fifth family for "the sweep line is in 2D" would be one entry and a lot of geometry. Left out of the native table on purpose.
- **Half-open vs closed as its own row.** Folded into #6's type codes. If that decision keeps being the whole interview, split it out — I do not think it is a second idea.
- **[[Greedy]] #8 covering** still lives there. Intervals readers will look for video stitching here and find a ↗, which is correct: the *argument* is stay-ahead, not overlap algebra.

**Completeness confidence: ~90%.** I1–I3 I am confident about: algebra, typed sweep (including queries-as-events), live set. 4B filled the per-query cell rather than a missing axis. Remaining miss risk is 2D sweep and the covering problems that Greedy already owns. The type-code tip at the top is the thing this file exists to make unmissable.

## Related Notes

- [[README]]
- [[Sorting & Custom Comparators]]
- [[Greedy]]
- [[Heap]]
- [[Prefix Sums & Difference Arrays]]
- [[Two Pointers]]
- [[Sorted Containers & Order Statistics]]
- [[Binary Search Trees]]
- [[Segment Trees]]
- [[Dynamic Programming]]
- [[Arrays]]
- [[Design]]
- [[Matrix]]
