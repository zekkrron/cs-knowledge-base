---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-23
---
# Concept Basis — Graphs

> [!abstract] Minimal spanning set for graphs. One entry per **new idea you have to learn**. Variations live in the exclusions table at the bottom, with the entry each one collapses into.

## Mechanism axes

The axes graph solutions actually vary along, enumerated before any problem was named:

| Axis | Values |
|---|---|
| **Frontier discipline** | LIFO (DFS) · FIFO (BFS) · priority queue · deque (0-1) · relaxation rounds · none (DSU) |
| **Where the graph comes from** | explicit adjacency · grid with implicit edges · state space generated on the fly · derived from constraints |
| **Edge weights** | none · uniform · non-negative · `{0,1}` only · negative · multiplicative · capacities |
| **How path cost combines** | sum · max edge (bottleneck) · product |
| **State augmentation** | plain node · node + budget · node + parity · node + bitmask |
| **Processing direction** | forward from a source · multi-source · backward / reversed edges · meet in the middle · **inward, peeling the boundary away** |
| **What is computed** | reachability · distance · cycle · ordering · component structure · spanning tree · specialised walk |
| **What is returned** | a cost · a count · the object itself (reconstruction) |
| **Assumption that breaks** | cycles break DAG memoisation · non-uniform weights break BFS · negatives break Dijkstra · sum-cost breaks for bottleneck · directed cycle logic breaks on undirected |

## Shape of this topic

```
G1  Traversal & connectivity      5 ideas
G2  Implicit / constructed graphs 3 ideas
G3  Cycles & ordering             5 ideas
G4  Coloring & partition          1 idea
G5  Union-Find                    3 ideas
G6  Weighted shortest path        8 ideas
G7  Spanning & structure          6 ideas
G8  Specialised walks             1 idea
G9  Flow & matching               1 idea   ← tail
```

**33 native entries, plus 5 cross-listed (↗).** A ↗ entry is developed more fully in the named topic, but it lives here too — see [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Entries are grouped by family, so a later-added entry keeps its high number inside the family it belongs to. This keeps cross-file references from rotting every time the basis grows.

---

## G1 · Traversal & Connectivity

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Number of Islands | LC **200** | **Flood fill / connected components.** Mark visited, recurse, count how many times you had to launch a traversal. Also where you learn that a grid *is* a graph with implicit edges. |
| 2 | Shortest Path in Binary Matrix | LC **1091** | **BFS layer = distance.** The first time you reach a node is the shortest way to reach it — true only because every edge costs the same. Everything in G6 exists because this assumption fails. |
| 3 | Rotting Oranges | LC **994** | **Multi-source BFS.** Seed the queue with *all* sources at distance 0 and you get nearest-source distance for every node in one sweep, instead of running #2 once per source. |
| 4 | Word Ladder | LC **127** | **Bidirectional BFS.** Search from both ends and stop when the frontiers meet. Turns `b^d` into roughly `2·b^(d/2)` — the reason it works is that you always expand the *smaller* frontier. |
| 5 | Word Ladder II | LC **126** | **Reconstruct the paths, not just the distance — and get *all* of them.** BFS to fix every node's layer while recording parent sets, then DFS backwards. A distance-only sweep throws away exactly what this needs, which is why the two-phase split is forced rather than stylistic. |

## G2 · Implicit & Constructed Graphs

> [!tip] The hardest step in these is realising there is a graph at all. Nobody hands you an adjacency list.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 6 | Open the Lock | LC **752** | **The state space is the graph.** Nodes are configurations, edges are legal moves, and neighbours are *generated on demand*. No adjacency structure is ever built. |
| 7 | Alien Dictionary | LC **269** | **Derive the edges from the input.** The graph is not given and not obvious — you extract ordering constraints from adjacent word pairs, then run a topological sort. The construction is the lesson, not the sort. |
| 8 | Shortest Path Visiting All Nodes | LC **847** | **Augment the node with a bitmask.** State is `(node, set of visited nodes)`, so the same node is legitimately visited many times in different states. The first idea that breaks "visited means done." |

## G3 · Cycles & Ordering

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Course Schedule | LC **207** | **Directed cycle detection.** Three-colour marking, or a recursion stack — an edge back to a node *currently on the stack* is a cycle. A plain visited set is not enough. |
| 10 | Redundant Connection | LC **684** | **Undirected cycle detection is a different problem.** The directed logic fires on the trivial edge back to your parent, so you must skip the parent explicitly (or use DSU). Worth its own entry precisely because the obvious transfer fails. |
| 11 | Course Schedule II | LC **210** | **Topological order.** Repeatedly emit an indegree-0 node (Kahn), or reverse a DFS postorder. Cycle detection falls out for free when the emitted count falls short. |
| 12 | Longest Increasing Path in a Matrix | LC **329** | **Memoised DFS is only safe on a DAG.** The insight is proving acyclicity first — strictly increasing values guarantee it — after which the graph becomes a DP. Recognising "this is secretly a DAG" is the transferable move. |
| 33 | Minimum Height Trees | LC **310** | **Peel inward from the boundary instead of expanding outward from a source.** Strip all degree-1 nodes, then all newly-exposed degree-1 nodes, and repeat; whatever survives the last round is the centre, and there are always exactly one or two. Mechanically this is Kahn's algorithm (#11) on an undirected graph with "degree 1" replacing "indegree 0", but the *direction* is the opposite of everything else in G1 — you are removing the frontier rather than growing it. The same peeling answers tree diameter and eccentricity questions. |

## G4 · Coloring & Partition

| # | Problem | Source | The new idea |
|---|---|---|---|
| 13 | Is Graph Bipartite? | LC **785** | **2-colouring.** Assign alternating colours along edges; a conflict proves an odd cycle. Converts a structural question into a traversal. |

## G5 · Union-Find

| # | Problem | Source | The new idea |
|---|---|---|---|
| 14 | Number of Provinces | LC **547** | **DSU itself** — path compression plus union by rank, and the near-constant amortised cost. Connectivity without traversal. |
| 15 | Evaluate Division | LC **399** | **DSU carrying relational data.** Store a ratio (or offset) along each parent pointer and compose it during find. The set structure now answers *how* two things relate, not merely whether. |
| 16 | Last Day Where You Can Still Cross | LC **1970** | **Reverse the timeline.** DSU can merge but not split, so process removals backwards and they become additions. The general trick for "connectivity as things disappear." |

## G6 · Weighted Shortest Path

| # | Problem | Source | The new idea |
|---|---|---|---|
| 17 | Network Delay Time | LC **743** | **Dijkstra.** A priority-queue frontier, relaxation, and the reason non-negative weights are required — once popped, a node is final. |
| 18 | Cheapest Flights Within K Stops | LC **787** | **Augment the node with a budget.** State is `(node, stops used)`; the same node is re-expanded at different budgets, and a cheaper path with more stops does not dominate. Second break of "visited means done." |
| 19 | Minimum Obstacle Removal to Reach Corner | LC **2290** | **0-1 BFS.** When weights are only 0 or 1, a deque replaces the heap — push-front for 0, push-back for 1 — giving `O(V+E)` instead of `O(E log V)`. The frontier stays sorted without a heap. |
| 20 | High Score | CSES **1673** | **Bellman-Ford.** `V-1` relaxation rounds instead of a frontier, which tolerates negative edges and *detects negative cycles* by relaxing once more. LeetCode has no clean negative-weight problem, which is why the canonical one is CSES. |
| 21 | Find the City With the Smallest Number of Neighbors at a Threshold Distance | LC **1334** | **Floyd-Warshall.** All-pairs by letting `k` be an intermediate vertex. The loop order is the whole algorithm and getting it wrong silently gives wrong answers. |
| 22 | Path With Minimum Effort | LC **1631** | **Dijkstra when cost is not a sum.** Path cost here is the *maximum* edge, not the total. Dijkstra generalises to any combining operation that never improves as the path grows — which also covers max-product (LC 1514). Alternatives: binary search on the answer plus BFS, or incremental DSU. |
| 23 | Number of Ways to Arrive at Destination | LC **1976** | **Carry an aggregate alongside the distance.** Maintain a path count during relaxation, and know when to overwrite it (strictly shorter path found) versus add to it (equal-length path found). Generalises to counting edges, minimum hops, or any statistic riding along with Dijkstra. |
| 24 | Flight Routes | CSES **1196** | **K-th shortest path.** Keep the best `k` distances per node instead of a single best, and stop expanding a node after it has been popped `k` times. Deliberately relaxes the "finalise once" invariant that makes #17 correct. |

## G7 · Spanning & Structure

| # | Problem | Source | The new idea |
|---|---|---|---|
| 25 | Min Cost to Connect All Points | LC **1584** | **MST.** Kruskal (sort edges, union when disjoint) or Prim (grow from one node via a heap). One idea, two implementations — learn Kruskal first since it reuses DSU. |
| 26 | Critical Connections in a Network | LC **1192** | **Tarjan low-link.** Track discovery time and the earliest reachable ancestor; an edge is a bridge when `low[v] > disc[u]`. Articulation points are the same machinery with a different comparison. |
| 27 | Planets and Kingdoms | CSES **1683** | **Strongly connected components.** Kosaraju's two passes on the graph and its reverse, or Tarjan's stack. Also where **reversing the edges** becomes a deliberate tool. No clean LeetCode equivalent. |
| 28 | Coin Collector | CSES **1686** | **Condense the SCCs, then DP on the resulting DAG.** Finding the components (#27) is only half the job; collapsing each into a single node produces an acyclic graph on which longest-path DP becomes legal. This is what SCC is usually *for*. |
| 29 | Kth Ancestor of a Tree Node | LC **1483** · CSES **1750** | **Binary lifting.** Precompute the `2^j`-th successor of every node, then answer any `k`-th-successor query in `O(log k)` by decomposing `k` into bits. The same table answers LCA — [[Binary Trees]] references this rather than rebuilding it. |
| 30 | Array Nesting | LC **565** · CSES **1751** | **Functional graphs.** When every node has exactly one outgoing edge, the structure is forced into a "rho" shape: a tail feeding into a cycle. That guarantee is what enables tortoise-and-hare and clean tail/cycle decomposition, neither of which is available on a general graph. |

## G8 · Specialised Walks

| # | Problem | Source | The new idea |
|---|---|---|---|
| 31 | Reconstruct Itinerary | LC **332** | **Eulerian path via Hierholzer.** Consume edges rather than visit nodes, and append to the answer on the way *out* of the recursion. The reversed-postorder output is deeply counter-intuitive and appears nowhere else. |

## G9 · Flow & Matching

> [!warning] Tail scope. Rare in Indian SDE loops; shows up at Google-tier and in some quant interviews. Learn it last, and only if the rest is solid.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 32 | Download Speed · School Dance | CSES **1694** · **1696** | **Max flow, min cut, and bipartite matching.** Ford–Fulkerson with BFS augmenting paths (Edmonds–Karp), the max-flow-min-cut duality, and the modelling step where a matching problem is rewritten as a flow network. The modelling is the transferable part. |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them here and they are part of studying graphs. See [[README]] on cross-listing.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Word Search | LC **79** | **DFS with undo.** Mark a cell used, recurse, then *unmark it on the way out* — the first time a traversal's visited set is temporary rather than permanent. Every entry in G1 relies on visited being monotone; this one deliberately breaks that. [[Backtracking]] #8. |
| ↗ | Lowest Common Ancestor of a Binary Tree | LC **236** | The recursive "found in both subtrees means I am the answer" argument, and the binary-lifting alternative that reuses #29's table. [[Binary Trees]] #7. |
| ↗ | Maximum Students Taking Exam | LC **1349** | Profile DP over a grid, which reads as a graph problem and is not one. Useful here precisely as a negative example — recognising when *not* to reach for a traversal. [[Dynamic Programming]] #47. |
| ↗ | Number of Islands II | LC **305** | #14 run incrementally as land is added, decrementing the component count on each successful union. Union-Find basis. |
| ↗ | Sum of Distances in Tree | LC **834** | **Rerooting.** Solve for one root, then derive the answer for every other root in `O(n)` by adjusting along each edge as you move the root one step. The alternative is `n` separate traversals, so this is the graph-shaped answer to "compute something for *every* starting node." [[Dynamic Programming]] #43. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Max Area of Island | LC **695** | #1 | Flood fill returning a size instead of a count. |
| Surrounded Regions | LC **130** | #1 | Flood fill seeded from the border. |
| Pacific Atlantic Water Flow | LC **417** | #1 + #3 | Multi-source fill from two border sets, then intersect. |
| Number of Connected Components | LC **323** | #1 | Same count, explicit adjacency instead of a grid. |
| Find if Path Exists in Graph | LC **1971** | #1 | Reachability with an early exit. |
| Clone Graph | LC **133** | #1 | Visited set whose payload is the copied node. |
| 01 Matrix | LC **542** | #3 | Multi-source BFS from every zero. |
| Walls and Gates | LC **286** | #3 | Multi-source BFS from every gate. |
| Message Route | CSES **1667** | #5 | Single-path reconstruction — the easy half of #5. |
| Bus Routes | LC **815** | #6 | Implicit graph; the twist is choosing *routes* as nodes rather than stops. |
| Minimum Genetic Mutation | LC **433** | #6 | Word Ladder with a four-letter alphabet. |
| Find Eventual Safe States | LC **802** | #9 | Colour-marking cycle detection, reported differently. |
| Graph Valid Tree | LC **261** | #10 | Undirected cycle check plus an edge count. |
| Redundant Connection II | LC **685** | #10 | Directed variant; extra casework, no new machinery. |
| Parallel Courses | LC **1136** | #11 | Topological sort counting levels. |
| Find All Possible Recipes | LC **2115** | #11 | Topological sort with a supplies set. |
| Minimum Number of Vertices to Reach All Nodes | LC **1557** | #11 | Indegree-zero scan. |
| Longest Flight Route | CSES **1680** | #12 | DAG longest path — topological order instead of memoised DFS. |
| Game Routes | CSES **1681** | #12 | DAG path *counting*: same recurrence, sum instead of max. |
| Possible Bipartition | LC **886** | #13 | Bipartite check on a "dislikes" graph. |
| Accounts Merge | LC **721** | #14 | DSU keyed by email. |
| Satisfiability of Equality Equations | LC **990** | #14 | DSU over two passes. |
| Cheapest Flights (Bellman-Ford solution) | LC **787** | #18 / #20 | Same problem already used for augmented state; BF is an alternative implementation. |
| Minimum Cost to Make at Least One Valid Path | LC **1368** | #19 | 0-1 BFS where turning costs 1. |
| Cycle Finding | CSES **1197** | #20 | Bellman-Ford negative-cycle detection with the cycle printed out. |
| Path with Maximum Probability | LC **1514** | #22 | Dijkstra with max-product instead of min-sum — the same generalisation. |
| Swim in Rising Water | LC **778** | #22 | Bottleneck path on a grid. |
| Path With Maximum Minimum Value | LC **1102** | #22 | Bottleneck, maximising the minimum. |
| Investigation | CSES **1202** | #23 | Four aggregates carried through Dijkstra instead of one. |
| Find Critical and Pseudo-Critical Edges in MST | LC **1489** | #25 | MST run repeatedly with an edge forced in or out. |
| Planets Cycles | CSES **1751** | #30 | Functional graph with per-node cycle lengths reported. |
| De Bruijn Sequence · Teleporters Path | CSES **1002**, **1693** | #31 | Eulerian circuit on a different construction. |
| Police Chase · Distinct Routes | CSES **1695**, **1711** | #32 | Min cut and path extraction from the same flow. |

---

## Self-audit

**Borderline calls, and which way I went**

- **Kruskal vs Prim (#25).** Merged. Different machinery — DSU versus heap — but the idea being learned is "grow a minimum spanning structure greedily," and the second one teaches implementation rather than insight. ==Split it if you find Prim genuinely surprising when you get there.==
- **Bridges vs articulation points (#26).** Merged. Identical low-link computation, different comparison.
- **Bus Routes (LC 815).** Excluded into #6, but reluctantly. "Choosing the non-obvious thing to be a node" may deserve standalone status — it is the same skill that makes #8 hard.
- **Reversing edges.** Not given its own entry. It appears inside #27 and is load-bearing in #28, but on its own it is a step rather than an idea.
- **Clone Graph (LC 133).** Excluded. "Visited set carrying a payload" recurs in Copy List with Random Pointer, so it may belong in a cross-cutting basis rather than here.
- **Directed vs undirected cycle detection (#9, #10).** Kept split, deliberately. The obvious transfer fails, and a failed transfer is exactly what earns an entry under this criterion.
- **Binary lifting (#29) filed under Graphs, not Trees.** The technique is identical in both places and [[Binary Trees]] points here. Filed where the *functional graph* framing lives, since that is the more general statement.

**What the re-sweep changed**

Seven entries were added after the source constraint was lifted — #5, #23, #24, #28, #29, #30 and #32. Only four of those were genuine sourcing failures. The other three (#5, #23, #29) have perfectly good LeetCode representatives and were simply missed.

> [!warning] Missing axis, now fixed. "**What is returned — a cost, a count, or the object itself?**" was absent from the axis table on the first pass, and its absence is what hid #5 and #23. It distinguishes #5 from #2 and #23 from #17. It is now in the table above and in the shared probe list.

**Step 4B — reverse sweep**

Thirty-five plain-language descriptions were navigated against the family headings. Two failures:

- **"Find the node that minimises the maximum distance to everything else"** landed nowhere. That is #33, now in G3. The axis it exposed is a missing *value*, not a missing axis: "processing direction" listed forward, multi-source, reversed and meet-in-the-middle, but not **inward, peeling the boundary away**. Added.
- **"Compute an answer for every node as the root"** landed nowhere, because rerooting had been filed only in [[Dynamic Programming]] #43. Now cross-listed. This is residue from the old ownership rule rather than a new miss — worth noting that removing that rule did not automatically surface everything it had previously hidden.

One collision, checked and cleared: "count the paths between two nodes" reaches both #23 (counting *shortest* paths under relaxation) and the DAG path-counting excluded into #12. Different machinery, genuinely different problems, correctly separated already.

**Still open**

- **A\* and heuristic search** — omitted as interview-irrelevant. Low confidence, flagging it.
- **Graph colouring with `k > 2`** — greedy works when degree is bounded, and it is NP-hard in general. Excluded as a backtracking problem rather than a graph one. Moderate confidence.
- **2-SAT** (implication graph plus SCC) — excluded as competitive-programming scope, though closer to the boundary than I would like.
- **Union-Find rollback and offline queries** — still thin at three entries. Most of the remaining machinery is genuinely CP.
- **Grid problems are heavily represented** in the exclusions. Still the place a wrong merge would hide.
- **Six entries cite CSES** (#20, #24, #27, #28, #32, and #29 jointly). A LeetCode-only run loses five of them outright.

**Completeness confidence: ~94%**, up from 90% before the re-sweep.

## Related Notes

- [[README]]
- [[Stack and Queue]]
- [[Dynamic Programming]]
- [[Heap]]
- [[Binary Search]]
- [[Binary Trees]]
- [[N-ary Trees]]
- [[Backtracking]]
- [[Bit Manipulation]]
