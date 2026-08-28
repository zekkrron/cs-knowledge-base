---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-28
---
# Concept Basis — Union-Find

> [!abstract] Minimal spanning set for **merge-only connectivity** — the DSU itself, activating cells on a grid, a relation stored on the parent pointer, reversing time so deletions become unions, offline sorted unions, two DSUs with a shared-edge priority, union through a property key, and splitting a cell into parts before you union. One entry per **new idea you have to learn**. [[Graphs]] G5 already has the interview core (Provinces, weighted DSU, reverse timeline) and **keeps those natives**; they are dual-native here. Kruskal, undirected cycles, and bottleneck-MST alternatives stay ↗ into Graphs. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!tip] **DSU answers "are these two in the same component" after a sequence of unions, in nearly `O(1)` amortised.** It cannot split. If the live graph *loses* edges, you reverse the timeline (#4), sort so unions only grow (#5), or — contest side — **undo the last unions** (#9) instead of splitting. If the question is a shortest path, you wanted [[Graphs]] G6 — unless the cost is a bottleneck and you can add edges in sorted order (#5).

## The one reflex

> [!tip] **If you only ever add connections, do not DFS after every add.** Hold a DSU, union the new edge, and read `components` or `find(a) == find(b)`.
>
> The second reflex: **ordinary DSU cannot undo.** Process the operations backwards (#4), or sort them so the unions are monotone (#5). Both make the updates *only grow*. The contest escape is #9: drop path compression, keep a stack, undo.

## Mechanism axes

| Axis | Values |
|---|---|
| **What a node is** | a vertex · a grid cell (`r·n + c`) · an email / string · a **row id or column id** · a **sub-cell** (slash-split) · a sentinel ("touches this side") |
| **What find composes** | identity (plain parent) · **a ratio / offset** · a size · a min-label |
| **How you union** | by rank · by size · "attach to the smaller character" |
| **When edges appear** | all at once · **one land cell at a time** · **backwards from deletions** · **sorted by weight / value**, answering queries as the threshold grows · **added then undone** (rollback) |
| **How many forests** | one · **two** (Alice / Bob, type-1 / type-2) |
| **Find may compress** | yes (interview default) · **no — rollback needs union-by-rank only** |
| **Assumption that breaks** | you need to split · path compression without union-by-rank is not `α(n)` in the way you quoted · `==` and `!=` constraints in one pass (two-pass, or a parity payload) · path compression plus undo (the parent writes are not a stack) |

## Shape of this topic

```
U1  The forest                       2 ideas
U2  Payload on the parent            1 idea
U3  Time and order                   2 ideas
U4  How you model the nodes          3 ideas
U5  Undo  [tail]                     1 idea
                                     + 7 cross-listed ↗
```

**9 native entries, plus 7 cross-listed (↗).** See [[README]] on cross-listing. #9 is tagged **[tail]** — contest DSU, not Indian SDE.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Dual-native with [[Graphs]] #14 · #15 · #16. Those three stay native in Graphs — this file does not steal G5.

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| **DSU** · path compression · union by rank | #1 |
| **Number of Islands II** | #2 |
| **Evaluate Division** · weighted DSU | #3 |
| **Last Day Where You Can Still Cross** · reverse timeline | #4 |
| Checking Existence of Edge Length Limited Paths | #5 |
| Remove Max Edges to Keep Graph Traversable | #6 |
| Most Stones Removed · union by row/col | #7 |
| Regions Cut By Slashes | #8 |
| **Rollback DSU** · offline dynamic connectivity | #9 |

---

## U1 · The forest

> [!warning] **In C++ you write `vector<int> p(n), r(n);` with `find` compressing `p[x] = find(p[x])`, and `union` attaching the lower rank.** `iota(p.begin(), p.end(), 0)` seeds identity. Without union-by-rank (or size) a chain makes find linear; without compression it is `O(log n)` which is usually fine and easier to debug. Interview sentence: amortised almost `O(1)` by inverse Ackermann, say `α(n)`, do not claim worst-case `O(1)`.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Number of Provinces | LC **547** | **The structure: `find` with path compression, `union` by rank or size, and a component count that drops on a successful merge.** Connectivity without a traversal. Dual-native with [[Graphs]] #14 — a graph-driller meets this as G5; a DSU-driller meets it as the forest itself. Keep `sz[]` if you need component sizes (malware spread, max island as land is added). Accounts Merge (LC **721**) is this keyed by email; Satisfiability of Equality Equations (LC **990**) is this in two passes (union every `==`, then check no `!=` shares a parent). Graph Valid Tree (LC **261**) is "one component and `n − 1` successful unions." |
| 2 | Number of Islands II | LC **305** | **Activate cells over time: a cell is a node only once it becomes land, then you try to union it with already-land neighbours and decrement `components` on each real merge.** Start at 0 components, `+1` on add, `−1` per successful neighbour union (a cell can merge into several existing islands and drop by more than one). New against #1 because the graph is **born incrementally** and the index is `r·n + c`. Dual with [[Graphs]] ↗ Islands II — Graphs keeps that ↗ row and points here; this is the native. Bricks Falling When Hit (LC **803**) is the reverse: hits become adds when you walk the query list backwards, then #4. |

## U2 · Payload on the parent

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Evaluate Division | LC **399** | **Store a relation on the parent pointer and compose it during find.** If `p[x]` means "`x = w[x] · p[x]`" (or an offset, or an xor-parity), then find returns `(root, weight-to-root)` and union of `a` and `b` given `a / b = v` rewires one root with the composed weight. Dual-native with [[Graphs]] #15. New against #1 because the set now answers *how* two things relate, not only whether. Parity DSU (bipartite / "same side") is the same compose with `xor` in `{0,1}` — [[Graphs]] #13 is the traversal form; this is the merge form. |

## U3 · Time and order

| # | Problem | Source | The new idea |
|---|---|---|---|
| 4 | Last Day Where You Can Still Cross | LC **1970** | **DSU can merge but not split, so process removals backwards and they become additions.** Dual-native with [[Graphs]] #16. On a grid, two **sentinel nodes** ("touches top", "touches bottom") turn "is there a crossing" into `find(top) == find(bottom)` after each add. New against #2: #2 only ever adds land; this one is given a *destruction* timeline and has to reverse it. Same licence as "offline deletions." |
| 5 | Checking Existence of Edge Length Limited Paths | LC **1697** | **Sort edges and queries by the same threshold, add every edge with `weight < limit`, then answer `find(u) == find(v)`.** Unions are monotone in the limit, so one sweep serves all queries. New against #4: you are not reversing a destruction, you are **growing the graph up to a key** and answering as you go. Number of Good Paths (LC **2421**) is this with nodes sorted by value; Swim in Rising Water as DSU is this with cells sorted by height (↗ [[Graphs]] #22 is the Dijkstra/bottleneck reading). Rank Transform of a Matrix (LC **1632**) unions equal cells, then assigns ranks in value order — #5 plus #7. |

## U4 · How you model the nodes

| # | Problem | Source | The new idea |
|---|---|---|---|
| 6 | Remove Max Number of Edges to Keep Graph Traversable | LC **1579** | **Two forests, and the shared edges go first.** Alice-only, Bob-only, and type-3 (both). Union type-3 on *both* DSUs first (they are the ones that can save two people at once), then fill each person's remaining needs from their exclusive edges; leftover exclusive edges are the ones you can drop. New because #1 is one forest; here **the priority among edge types** is the design, and a type-3 that does not merge *either* forest is wasted. |
| 7 | Most Stones Removed with Same Row or Column | LC **947** | **Union through a property, not through an explicit edge list.** Stones that share a row (or a column) are connected, so you union stone `i` with "row `r`" and "col `c`" as auxiliary nodes — or union all stones on row `r` to a row representative. `n` stones plus `R + C` keys, not `n²` pairs. Largest Component Size by Common Factor (LC **952**) is the same with primes as keys: union `x` with each prime factor of `x`. New because the graph was never written down; **the key is a node**. |
| 8 | Regions Cut By Slashes | LC **959** | **Split each cell into parts, then union along open sides.** A `/` or `\\` cuts a square into two triangles (or four triangles if you want a uniform split); neighbouring cells' touching triangles merge, and a blank cell merges all its parts. `components` at the end is the number of regions. New because the DSU nodes are **finer than the input cells** — you invented vertices the grid did not hand you. |

## U5 · Undo  [tail]

> [!warning] **Interview DSU path-compresses and never undoes.** The moment a query stream *adds and deletes* edges and you cannot reverse the whole timeline (#4), this is the structure. Codeforces EDU "DSU with rollback"; no clean LeetCode. Know what it is; you will not write it in an SDE loop.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Rollback DSU · offline dynamic connectivity | *CF EDU / AtCoder — "edges live on a time interval, queries ask connectivity now"* **[tail]** | **Drop path compression. Union by rank (or size) only, and every write is a stack frame you can pop.** `union` pushes `(x, old_p[x], old_rank[x], old_components)` before attaching; `undo` restores. Find is `while (p[x] != x) x = p[x]` — no compression, so a parent pointer changes only at a root, and undo is `O(1)` per merged edge. `O(log n)` per find because rank bounds the height. New against #4/#5: those never need to *un*-merge; they rewrite the order so merges only grow. This one **genuinely rolls back**. The usual consumer is a **segment tree on time**: each edge is an interval `[add, remove)`, you DFS the tree adding the edge on enter and `undo` on exit, and a connectivity query at a leaf sees exactly the edges alive at that time. The tree is [[Segment Trees]]; the DSU is this entry. Persistent DSU (copy the parent array, or a persistent array) is a different cost model and stays excluded. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Redundant Connection · Graph Valid Tree | LC **684** · **261** | A failed union *is* the undirected cycle. Directed cycle logic does not transfer. [[Graphs]] #10. |
| ↗ | Min Cost to Connect All Points | LC **1584** | Kruskal: sort edges, union when disjoint. Prim is the heap twin. [[Graphs]] #25. |
| ↗ | Path With Minimum Effort · Swim in Rising Water | LC **1631** · **778** | Bottleneck path: sort edges/cells, DSU until `s` meets `t`. Dijkstra-on-max is the other reading. [[Graphs]] #22. |
| ↗ | Job Sequencing with Deadlines | *GFG* | Latest-fit: `find(slot)` is the next free time `≤ deadline`. DSU as a next-available pointer, not as a graph. [[Greedy]] #6. |
| ↗ | Small-to-large merging on a tree | LC **1519** | Union-by-size is why absorbing the smaller map is `O(n log n)`. The tree fold, not a forest of parents. [[N-ary Trees]] #7. |
| ↗ | Is Graph Bipartite | LC **785** | Traversal 2-colouring. Parity-DSU (#3's xor) is the merge form of the same question. [[Graphs]] #13. |
| ↗ | Flatten index `r·n + c` | LC **566** | Grid DSU (#2, #4, #8) needs a 1D id. [[Matrix]] #1. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Friend Circles | LC **547** | #1 | Old name of Provinces. |
| Number of Connected Components | LC **323** | #1 | Explicit edges, same forest. |
| Accounts Merge | LC **721** | #1 | Keyed by email. |
| Satisfiability of Equality Equations | LC **990** | #1 | Two passes: `==` then `!=`. |
| Smallest String With Swaps | LC **1202** | #1 | Union, then sort each component. |
| Lexicographically Smallest Equivalent String | LC **1061** | #1 | Keep the min character as parent. |
| Process Restricted Friend Requests | LC **2076** | #1 | Check restrictions before union. |
| Minimize Malware Spread | LC **924** · **928** | #1 · #2 | Size plus a "how many initials" count. |
| Largest Component Size by Common Factor | LC **952** | #7 | Primes as auxiliary nodes. |
| Number of Good Paths | LC **2421** | #5 | Grow by value, count pairs inside a component. |
| Rank Transform of a Matrix | LC **1632** | #5 · #7 | Equal cells union, then ranks in order. [[Matrix]] exclusion. |
| Bricks Falling When Hit | LC **803** | #4 · #2 | Reverse the hits, then incremental land. |
| Redundant Connection II | LC **685** | ↗ #10 | Directed casework. [[Graphs]]. |
| Couples Holding Hands | LC **765** | #1 | Cycles of misplaced pairs; greedy swap is the usual write. |
| Connecting Cities With Minimum Cost | LC **1135** | ↗ Kruskal | MST, possibly disconnected → `−1`. |
| Find Critical and Pseudo-Critical Edges | LC **1489** | ↗ Kruskal | MST with an edge forced in or out. [[Graphs]] #25. |
| Persistent DSU *(copy parent array / persistent array)* | *CP* | #9 | Different cost: you keep old versions, you do not undo the current one. Heavier than rollback; skip. |
| CSES Road Construction · Road Reparation | CSES **1676** · **1675** | #1 · ↗ Kruskal | Component count as you add; MST. |

---

## Self-audit

**Borderline calls, and which way I went**

- **Dual-native with [[Graphs]] G5 on #1, #3, #4.** The README's "file-or-not" is answered: both. Graphs keeps the three natives so a graph-driller still has a G5. This file exists because Islands II, offline sorted unions, two-DSU, property keys, and cell-splitting were squatting on a three-entry family or parked as "Union-Find basis."
- **#2 native rather than collapsing into #1.** Incremental activation plus "a merge can drop `components` by more than one" is the interview Hard. Graphs ↗ it and **keeps that ↗ row**; the native lives here.
- **#5 kept separate from #4.** Reverse-a-destruction vs sort-by-threshold. Same licence (unions only grow), different input shape. Merging them would hide "sort the queries."
- **#6 native.** Two forests plus type priority is not "run #1 twice."
- **#8 native rather than "just a grid DSU."** The nodes are invented. That is the modelling step #7 also teaches, and #7's keys already existed (rows, primes); #8's triangles did not.
- **Sentinels folded into #4**, not a ninth entry. Last Day is the problem that forces them; Islands II does not need "touches the ocean" as a node.
- **#9 tagged [tail], not hidden.** Graphs called rollback CP and left G5 at three natives — that file is untouched. This file is allowed to name the contest half. One entry, not a second file: the undo stack *is* the idea; the time-segment-tree is the consumer, pointed at [[Segment Trees]].

**Naming check.** #1 stayed "Number of Provinces" because that is the handle; the structure is in the warning above the table. #5 is the long LC title — "offline sorted unions" is the heading U3. #7 stayed "Most Stones" because "union through a key" is in the body.

**Step 4B — reverse sweep**

Eighteen descriptions.

- **"Are these two nodes connected after I add edges, no DFS"** → #1.
- **"Land cells appear one by one, how many islands after each"** → #2, not #1. Why #2 exists.
- **"`a / b = 2`, `b / c = 3`, what is `a / c`"** → #3.
- **"Cells flood in order, last day I can still walk top to bottom"** → #4 (reverse + sentinels).
- **"For each query, is `u` connected to `v` using only edges lighter than `limit`"** → #5, not Dijkstra.
- **"Alice and Bob must both stay connected, drop as many edges as you can"** → #6.
- **"Remove stones that share a row or column"** → #7, not an `n²` graph.
- **"A grid of slashes, how many regions"** → #8.
- **"Edges appear and disappear; after each query, are `u` and `v` connected"** → #9, not #4. Why the tail exists: you cannot reverse a mixed add/delete stream.

No missing axis. Collision, checked: **"connected components on a grid"** reaches #1 (static), #2 (incremental), and ↗ [[Graphs]] #1 (flood). Three answers, three files, intended.

**Step 4C — inward**

(i) [[Graphs]] G5 natives stay natives; ↗ Islands II **kept** and pointed at #2. Kruskal, Redundant Connection, Swim already named DSU; ↗ back. [[Greedy]] #6 "DSU-on-slots." [[N-ary Trees]] #7 small-to-large. [[Matrix]] Rank Transform exclusion.
(ii) Provinces, Islands II, Evaluate Division, Last Day, Accounts Merge, LC 990, Kruskal, 1579, 947, 959, 1697 — each is native, dual-native, ↗, or an exclusion.
(iii) Graphs "Union-Find rollback… still thin at three entries" — G5 stays at three; the contest half is **#9 here**, not a Graphs edit. README "file-or-not" — both files.

**What I am uncertain about**

- **Parity DSU as its own entry.** Folded into #3. A bipartite-only drill might want it split; [[Graphs]] #13 already owns the question.
- **DSU-on-slots native.** ↗ [[Greedy]] #6 instead: the *argument* is latest-fit, the pointer jump is one implementation.
- **#8 vs hex / 6-dir grids.** Same split-then-union. No extra entry.

**Completeness confidence: ~90%** on U1–U4 (interview). U5 is "know the name and the no-compression rule," not "implement the time-ST in an SDE round." Persistent arrays and Li Chao stay out.

## Related Notes

- [[README]]
- [[Graphs]]
- [[Matrix]]
- [[Greedy]]
- [[N-ary Trees]]
- [[Sorting & Custom Comparators]]
- [[Heap]]
- [[Binary Search]]
- [[Segment Trees]]
- [[Hashing]]
- [[Arrays]]
