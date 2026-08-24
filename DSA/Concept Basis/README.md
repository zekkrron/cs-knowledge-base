---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-23
---
# Concept Basis — Index & Method

> [!abstract] A minimal spanning set of DSA ideas. One entry per **new thing you have to learn** — no redundancy *within* a topic. Roughly 250 distinct ideas across all topics, plus cross-listings. Not a question list, a *basis*.

## Cross-listing — read this before the index

> [!tip] **A concept appears in every topic where you would meet it.** If you are drilling stacks, the deque-optimised DP problem belongs in the stack file. Being told "that one lives in DP" is precisely the wrong answer when you are sitting in the stack file trying to learn deques.
>
> Cross-listed entries are marked **↗** and name the topic that develops the idea most fully, so you know where the deeper treatment is and know not to double-count when totalling. Each file's header counts native and cross-listed entries separately.
>
> The old rule said an idea belongs to exactly one topic, the one whose machinery is new. That optimised for a tidy global count at the cost of every individual file being incomplete for study. A little repetition is cheap; a missing concept is not.

The no-redundancy rule still applies **within** a topic. Two problems in the same file that teach the same move are still one entry.

## Index

| Topic | Native | ↗ | Status |
|---|---|---|---|
| [[Stack and Queue]] | 23 | 4 | Draft · 4B passed |
| [[Graphs]] | 33 | 5 | Draft · 4B passed |
| [[Dynamic Programming]] | 68 | 4 | Draft · 4B passed |
| [[Heap]] | 24 | 5 | Draft · 4B passed |
| [[Binary Search]] | 21 | 4 | Draft · 4B passed |
| [[Segment Trees]] | 13 | 4 | Draft · 4B passed · mostly tail scope |
| [[Binary Trees]] | 18 | 5 | Draft · 4B passed |
| [[Binary Search Trees]] | 16 | 3 | Draft · 4B found 1 |
| [[N-ary Trees]] | 12 | 4 | Draft · 4B found 1 · smallest topic, honestly so |
| [[Tries]] | 18 | 4 | Draft · 4B found 2 |
| Arrays / Two Pointers / Sliding Window | — | — | Not started |
| **Sorted Containers / Order Statistics** | — | — | **Partially covered by [[Binary Search Trees]] S4 + S6 — see below** |
| Linked List | — | — | Not started |
| Backtracking | — | — | Not started |
| Greedy | — | — | Not started |
| Bit Manipulation | — | — | Not started |
| Strings (KMP / Z / hashing) | — | — | Not started |
| Intervals | — | — | Not started |
| Matrix | — | — | Not started |
| Union-Find | — | — | Not started |
| Math & Number Theory | — | — | Not started |
| Design | — | — | Not started |
| Sorting & Custom Comparators | — | — | Not started |

> [!warning] **Mostly-closed gap: incrementally maintained sorted containers.** Four files independently hit the same missing idea — a structure you insert into *and* query by rank as you sweep. [[Binary Search Trees]] now covers most of it: **S4** gives the structure (why balance is required, what a rotation restores, how subtree-size augmentation yields an order-statistic tree) and **S6** gives the usage (an ordered set over a sliding window, and `floorKey`/`ceilingKey` for neighbour queries). What is still absent is `bisect.insort` over a plain list, and the sorted-multiset-as-sliding-window family in its own right. Now a missing *section*, not a missing file.

---

## Why the first pass missed things

The Stack pilot missed **LC 862 (Shortest Subarray with Sum at Least K)** on its first attempt. The cause is worth recording because it will recur on every topic.

Generating from **recall** — "list the stack problems I know" — retrieves by *fame*, not by *concept*. Popular problems come back; conceptually distinct but less-famous ones vanish, and nothing marks their absence.

The repair is to invert the direction. Enumerate the **axes of the machinery first**, take the cross-product, and then ask of each cell "does a real problem live here?" An empty cell is visible. A forgotten problem is not.

Under that method LC 862 falls out immediately: it is the cell *stores prefix sums* × *pops front on satisfaction*.

## The failure the method cannot see by itself

> [!danger] **A missing axis is invisible from inside the classification, because its absence is exactly what makes distinct things look identical.**
>
> The cross-product guarantees you cover every cell *you enumerated*. It says nothing about axes you never thought of — and step 4's guard ("if it occupies no cell, your axes were incomplete") cannot fire, because a problem separated only by the missing axis **does** occupy a cell. It just occupies the wrong one, alongside something it is not actually like. Asking "does this fit?" returns true either way.

This has now happened three times, always with the same signature and always discovered by hitting a problem rather than by introspection:

| Topic | What was hidden | The axis that was missing |
|---|---|---|
| Stack & Queue | LC 862 | *why* eviction fires — expiry versus satisfaction |
| Graphs | LC 126, 1976, 1483 | is the output a **cost**, a **count**, or the **object itself** |
| Binary Search | LC 1760, 2064, 2226 | does the check **decompose per element** or carry state |

Step 4 fixed recall-dependence at the *problem* level, where external lists exist to sweep against. Nothing fixed it at the *axis* level, where no such list exists — nobody publishes "all the axes along which binary search solutions vary." **Step 4B is the repair**: query the file from outside, in problem-shaped natural language, and see whether it can be navigated. Two plain-language descriptions colliding on one entry is the signature of a missing axis, and it is the only reliable signal there is.

> [!warning] **Read the confidence percentages accordingly.** They estimate "did I miss a problem," not "did I miss an axis" — and one missing axis can hide an unbounded number of concepts while problem-level confidence sits comfortably above 90%. All six files have now passed step 4B, so the figures are honest *given the axes currently listed*, which is the only thing they ever measured. They will need re-reading whenever a new axis is discovered.

A secondary rule that fell out of the same incident: **pick the canonical problem whose most salient property IS the idea.** LC 774's obvious feature is that it is real-valued; its transferable feature is budget allocation. Naming the entry after the first buried the second. When those diverge, choose a different canonical or split the entry in two.

## What step 4B actually found

All ten files have now been through it. Results, because the pattern in them is more useful than any individual fix:

| Topic | Descriptions | Failures | What was added | Axis that had been missing |
|---|---|---|---|---|
| [[Dynamic Programming]] | 46 | **2** | #67 weighted interval scheduling, #68 value-keyed state | *what the state is keyed by* · *where the DP order comes from* |
| [[Graphs]] | 35 | 1 + 1 ↗ | #33 peel-inward, ↗ rerooting | *inward peeling* as a processing direction |
| [[Stack and Queue]] | 25 | 1 | #24 ambiguous-token interval | *is a token's effect determined or ambiguous* — the file had **no axis table at all** |
| [[Heap]] | 30 | 1 | #24 reordering buffer | *what happens to what leaves* — discarded, emitted, or transferred |
| [[Binary Search]] | 26 | 0 | — | already fixed by the probe that motivated 4B |
| [[Segment Trees]] | 21 | 0 | — | — |
| [[Binary Trees]] | 26 | 1 | nothing — a merge was flagged, not reversed | *deep tree forces an iterative traversal* — noted, left merged |
| [[Binary Search Trees]] | 30 | **1** | #6 BST serialisation needs no null markers | *the invariant is stored information* |
| [[N-ary Trees]] | 20 | **1** | #4 canonical identity under unordered children | *is child order meaningful* |
| [[Tries]] | 31 | **2** | #10 persistent trie, #12 fuzzy search by DP row | *persistent / versioned mutability* · *whether the query stays aligned with the path* |

Five observations worth carrying forward.

**Only one outright absence was found in the whole basis.** Weighted interval scheduling was simply not in the DP file. Every other failure was an idea that existed but was *unreachable* — misnamed, or collapsed into a neighbour by a missing axis. That ratio says the original method is good at coverage and bad at *findability*, which is a much better problem to have and a much easier one to fix.

**The worst result came from the family I knew best.** Both DP failures were in D1, linear DP, the most familiar material in the basis. Familiarity suppresses the question "what am I taking for granted?" — every entry in that file silently assumed the input's own index order until an axis asked where the order comes from.

**File size predicts findings, not file quality.** DP has 68 entries and produced two misses; Segment Trees has 13 and produced none. A clean pass on a small file is weak evidence, and the two clean passes here should be read as such rather than as vindication.

**Collisions are a signal to check, not a verdict.** Five descriptions landed on two entries each. One was a real missing axis; the rest were single ideas legitimately appearing on two containers, and were correctly left merged.

**A merged file hides failures from its own sweep.** The trees material was first written as one file covering n-ary, binary and BST together, and it passed 4B with zero findings. Split into three and swept separately, it produced two genuine misses — BST serialisation needing no null markers, and canonical identity under unordered children. In the merged file both descriptions resolved to a binary-tree entry and the sweep terminated satisfied, because *something* answered them. **When one file spans several structures, the most general one absorbs every probe and the specialisations never get tested.** If a topic file covers more than one structure, sweep each structure against a file that contains only it.

> [!danger] **Build order.** The sorted-container gap is now down to a missing *section* rather than a missing file, since [[Binary Search Trees]] S4 and S6 cover the structure and the main usages. **Arrays / Two Pointers / Sliding Window is now the largest hole in the basis** — it is the highest-frequency topic in interviews and has nothing written. Build it next.

---

## Scope is bounded. Source is not.

> [!danger] These are two different things and conflating them silently deletes concepts.
>
> **Scope — bounded.** Interview-style problems only. Techniques that exist purely for competitive programming stay out: convex hull trick, Knuth optimisation, Li Chao trees, bitset tricks, heavy CP number theory.
>
> **Source — unbounded.** The canonical problem for a concept is whichever one teaches it most cleanly, wherever it lives. LeetCode's catalogue reflects what is *popular*, not what is *conceptually complete* — it is thin on digit DP, has no clean negative-weight graph problem, and no good prefix-sum-over-DP problem. A concept is never dropped because LeetCode lacks a representative.

Preferred sources, in order, when several teach the same concept equally well:

1. **LeetCode** — most likely to be the actual interview question
2. **CSES** — clean, canonical, excellent where LeetCode is thin (digit DP, SCC, Bellman-Ford, matrix exponentiation)
3. **AtCoder Educational DP Contest** — the best teaching set for DP transitions specifically
4. **Codeforces** — for concepts the others miss; prefer problems tagged `*1600–2100`
5. **GeeksforGeeks / InterviewBit** — last resort, for classics with no clean online judge version

Every entry records its source explicitly, so a LeetCode-only run is still possible by filtering.

## The five probes

Apply these to any two problems that use the same data structure. If any answer differs, they are separate entries.

1. **What is stored?** Raw values · indices · prefix sums · `(value, count)` tuples · partial results · suspended frames
2. **What triggers the state change, and why?** Expiry · domination · satisfaction · collision rule · match. *This is the probe that caught LC 862* — the deque was identical to LC 239, but front eviction fired for a different reason.
3. **Where is the answer read from?** The popped element · what remains at the end · a counter accumulated during pops · the size
4. **What is being returned — a cost, a count, or the object itself?** Added after the Graphs re-sweep, where its absence cost three concepts. "Shortest distance," "how many shortest paths," and "list every shortest path" run the same traversal and are three different problems, because reconstruction needs information a distance-only sweep throws away.
5. **Which standard assumption breaks?** The highest-yield probe. Ask what the naive approach silently relies on and which problem violates it. Negatives break sliding window. Duplicates break binary-search boundaries. Cycles break DAG DP. Negative weights break Dijkstra. Each break is its own entry.

---

## The prompt

Paste this per topic. It is written to force enumeration over recall, and to make gaps visible.

```
Build the CONCEPT BASIS for the topic: <TOPIC>.

GOAL
A minimal spanning set of ideas — one entry per genuinely NEW thing I must
learn. Zero conceptual redundancy. This is not a question list.

INCLUSION CRITERION
An entry earns its place only if solving it requires a mental move I did not
already have from an earlier entry. NOT a new entry: a different cost
function, different wording, harder constraints, different input format, or
extra edge cases.

Worked example of the criterion: "delete a subarray and the ends squeeze
together" is ONE entry. What is learned is — fix the left end, sweep the
right end, so l..mid is always the deleted block and all subarrays get
considered. A variant where deletion cost depends on length teaches nothing
new; it is arithmetic on the same move. Excluded.

DO THESE STEPS IN ORDER. Do not skip to step 3.

STEP 1 — MECHANISM AXES
Before naming a single problem, enumerate the axes along which solutions in
this topic actually vary. For each axis list its possible values. Typical
axes: what is stored; what the maintained invariant is; what triggers a
push/pop/expand/shrink and WHY; where the answer is read from; whether the
output is a cost, a count, or the object itself; what shape the input takes;
which standard assumption is violated.

STEP 2 — CELL TABLE
Take the meaningful cross-product of those axes. For each cell state:
OCCUPIED (a real problem lives here) or EMPTY (no known problem). Do not
skip cells because you cannot immediately recall a problem — mark them EMPTY
and move on. Empty cells are a finding, not a failure.

STEP 3 — ASSIGN
Give exactly one canonical problem per OCCUPIED cell: the one that teaches
the idea most CLEANLY, from any source. Give name + number/id + source.

SCOPE vs SOURCE — do not confuse these:
  - SCOPE is bounded to interview-style problems. Exclude techniques that
    exist only for competitive programming (convex hull trick, Knuth
    optimisation, Li Chao, bitset tricks).
  - SOURCE is unbounded. NEVER drop an in-scope concept because LeetCode
    lacks a good representative. LeetCode reflects popularity, not
    conceptual completeness. Use CSES, AtCoder Educational DP Contest,
    Codeforces (prefer *1600-2100), or GfG as needed.
If a cell is in scope but you cannot find a good problem anywhere, still
create the entry and mark the representative as MISSING. An entry with no
problem is far better than a silently deleted concept.

STEP 4 — RECOGNITION SWEEP  (do not skip; this is the anti-recall step)
Walk external enumerations rather than your memory, and force every item into
exactly one bucket: matches entry #N / NEW entry (add it) / variation of #N /
cross-listed (add it here too, marked ↗, naming where it goes deeper).
  - LeetCode tags for this topic, top ~60 by frequency
  - Striver A2Z, the sections covering this topic
  - NeetCode 250, this topic's section
  - CSES, the sections covering this topic
  - AtCoder Educational DP Contest, all 26 problems  (DP topics only)
  - Codeforces EDU / topic tags, in-scope difficulty only
The last three exist specifically to catch concepts LeetCode under-represents.
A sweep that only walks LeetCode-derived lists will reproduce LeetCode's gaps.
Report anything that forced a NEW entry, and say which cell of step 2 it
occupies. If it occupies no cell, your axes in step 1 were incomplete — go
back and add the axis.

STEP 4B — REVERSE SWEEP  (the axis check; step 4 cannot do this)
Step 4 pushes problems INTO cells, which only ever validates the axes you
already have. A missing axis is invisible to it: two genuinely different
problems land in the same cell and look like variations, and "does this fit
a cell" returns true. So run the sweep backwards as well.

Write 15-20 short PROBLEM-SHAPED descriptions in plain domain language --
the way an interviewer would say it out loud, with no mechanism vocabulary.
Examples of the register: "some items are already placed and I add k more to
minimise the worst gap"; "I need every shortest route, not just its length";
"the thing I want to count changes as I sweep left to right".
For each, try to navigate the file to the right entry using ONLY the family
headings and entry titles.
  - Cannot find it, but the idea is present under another name
        -> NAMING failure. Rename the entry after its idea.
  - Cannot find it, and two descriptions land on the same entry
        -> MISSING AXIS. Name the axis that separates them, add it to
           step 1, re-split that cell, and re-run steps 3 and 4.
  - Cannot find it, and it is genuinely absent -> new entry.
Report the axes this step forced you to add. If it added none, say so
explicitly rather than staying silent -- silence here usually means the
descriptions were written in mechanism vocabulary and the step was circular.

STEP 5 — EXCLUSIONS
Table of every problem considered and rejected: problem + number, the entry
it collapses into, and one line on why. This table is the deliverable that
proves deduplication actually happened.

STEP 6 — CROSS-LISTING
Problems that appear in this topic but whose new machinery is developed more
fully elsewhere are STILL ENTRIES HERE. Do not exclude them. Mark each with ↗
and name the topic that develops it further, so I know where to read more.
Never answer "that belongs to another topic" — this file has to be complete
on its own for someone drilling only this topic.
Count native and cross-listed entries separately in the header.

STEP 7 — SELF-AUDIT  (mandatory, do not soften)
  a) List every borderline call you made — pairs you nearly merged or nearly
     split — and say which way you went and why.
  b) NAMING CHECK. For each entry ask: is this named after its IDEA, or after
     an incidental property of the canonical problem I picked? An entry filed
     under a surface feature is invisible even though it exists, so nothing in
     a completeness count will catch it. Rename any entry whose title would
     not let me recognise a differently-dressed version of the same problem.
  c) List what you are UNCERTAIN about: cells you suspect are occupied but
     could not name a problem for, and areas where your recall is likely thin.
  d) State a confidence level for the completeness of this topic's basis.

OUTPUT
Group entries under sub-headings by mechanism family (e.g. for stacks:
monotonic / collision / expression / cleaning / design). Numbered table per
group with columns: # | Problem | Source + number | The new idea.
Then the exclusions, ownership and self-audit tables.
```

---

## What this still will not catch

Be realistic about the ceiling. The prompt moves completeness from roughly 85% to the mid-90s; it does not reach 100%.

- **Recall of obscure problems stays imperfect.** Step 4 is the mitigation — recognition against an enumerated list is far more reliable than free recall — but it is only as good as the lists walked.
- **The tail is genuinely fractal.** Where you stop subdividing is a judgment call, which is why step 7a exists: borderline calls become visible and reversible instead of silent.
- **New ideas do appear.** Contests occasionally produce a genuinely novel recurrence.

The practical consequence: treat each topic file as **closed under review, not closed forever**. Step 7b is the part to read carefully — it converts unknown-unknowns into known-unknowns, which is the whole game.

## Related Notes

- [[Stack and Queue]]
- [[Graphs]]
- [[Dynamic Programming]]
- [[Heap]]
- [[Binary Search]]
- [[Segment Trees]]
- [[Binary Trees]]
- [[Binary Search Trees]]
- [[N-ary Trees]]
- [[Tries]]
