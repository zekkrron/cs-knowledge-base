---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-24
---
# Concept Basis — Binary Trees

> [!abstract] Minimal spanning set for binary trees — ideas that need **exactly two children** but not an ordering. One entry per **new idea you have to learn**. Ordering-dependent ideas live in [[Binary Search Trees]]; degree-independent ideas in [[N-ary Trees]]. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## Scope boundary against the two sibling files

This file owns anything that uses the `left`/`right` distinction or the two-way branch factor. Concretely:

| Belongs here | Belongs in [[Binary Search Trees]] | Belongs in [[N-ary Trees]] |
|---|---|---|
| Inorder as a traversal | Inorder as a *sorted* sequence | — (no inorder exists) |
| Diameter via `left + right` | — | Diameter via the **top two** children |
| Reconstruction from two traversals | Reconstruction from one, since order recovers structure | Reconstruction needing an arity marker |
| Mirroring, which pairs `left↔right` | — | — (no asymmetry to exploit) |

Expect overlap. Level-order appears in all three, because someone drilling any one of them needs it.

## The organising question

> [!tip] **The first question on a binary tree problem is not "which traversal?" — it is "which way does the information need to move?"**
>
> If a node's answer depends on its children, information flows **up** and you want postorder (B2). If it depends on where the node sits relative to the root, it flows **down** as a parameter (B3). If both, you do both. Choosing wrong is why people end up needing a second traversal, or a global variable they cannot justify.
>
> Traversal order is then a *consequence*, not a choice: postorder when children must finish first, preorder when context arrives from above, inorder when the entire left subtree must be done before the node is visited.

## Mechanism axes

| Axis | Values |
|---|---|
| **Direction of information flow** | up (return values) · down (parameters) · both · sideways across a level |
| **Traversal order** | preorder · inorder · postorder · level-order · reverse preorder · Euler tour |
| **What a call returns vs records** | returns the answer · returns one quantity while recording another globally · returns a tuple · returns a sentinel encoding failure |
| **What travels down** | nothing · a copied scalar · a mutated container needing undo · an accumulating map needing undo |
| **How a node is reached** | child pointers · parent pointers added · array index (heap-style) · a root-to-node path |
| **Structural guarantee exploited** | none · complete · perfect · balanced |
| **Space discipline** | `O(h)` recursion · explicit stack · `O(1)` by threading |
| **What is produced** | an aggregate · a path · a reconstruction · a canonical identity · an in-place rewiring |
| **What breaks** | the answer path may not pass through the root · negative values break greedy path reasoning · a single-child node makes preorder+postorder ambiguous · deep skewed trees blow the recursion stack |

## Shape of this topic

```
B1  Traversal orders               3 ideas
B2  Information flowing up         4 ideas
B3  Information flowing down       3 ideas
B4  Reconstruction & identity      3 ideas
B5  Structural shortcuts           3 ideas
B6  Shape comparison & rewiring    2 ideas
                                   + 5 cross-listed ↗
```

**18 native entries, plus 5 cross-listed (↗).** See [[README]] on cross-listing.

---

## B1 · Traversal orders

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Binary Tree Inorder Traversal | LC **94** | **The three DFS orders are one recursion with the visit statement moved.** Hold that as a single fact rather than three algorithms, and the choice between them stops being arbitrary — it follows from the direction of information flow. The iterative form replaces the call stack by hand, descending left while pushing, then popping and turning right (↗ [[Stack and Queue]] #18), and it is the version you need when the tree is deep enough to overflow the stack. |
| 2 | Binary Tree Level Order Traversal | LC **102** | **BFS with level boundaries.** Snapshot the queue's size at the top of each iteration and exactly that many pops form one level. The only traversal that hands you depth for free, so it is the right tool whenever the answer is *per level* or is the shallowest anything — and the reason "minimum depth" is easier than "maximum depth". |
| 3 | Morris Inorder Traversal | *classic — the `O(1)` space follow-up to LC 94* | **Thread the tree to encode your own return path.** Before descending left, point the rightmost node of the left subtree at the current node; arriving back there proves the left subtree is finished, so you emit and unthread. `O(1)` space, no stack, no recursion. Startling and worth holding for its general form: **temporarily mutate the structure to store what you would otherwise need memory for.** The same trick underlies in-place linked-list reversal. |

## B2 · Information flowing up

Postorder aggregation. Children return, the parent combines. Where most binary tree problems actually live.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 4 | Maximum Depth of Binary Tree | LC **104** | The base case of the family: `1 + max(left, right)`, empty tree zero. Trivial as a problem, worth an entry as the shape every entry below deforms. |
| 5 | Diameter of Binary Tree | LC **543** | **Return one quantity, record another.** You return the best *downward* reach, because that is all a parent can use — but you update a global with the path *through* you, which is `left + right`. The two are different quantities and conflating them is the most common binary tree bug there is. Learn this once and the entire "the answer path need not pass through the root" family collapses into a single move. |
| 6 | Balanced Binary Tree | LC **110** | **Encode failure into the return value.** Returning `-1` for "something below is already unbalanced" lets one postorder pass answer both "how tall are you" and "is anything broken", instead of calling a height function at every node for `O(n²)`. The transferable idea is a sentinel in the return type carrying a second, cheaper signal upward. |
| 7 | Lowest Common Ancestor of a Binary Tree | LC **236** | **Postorder consensus.** If a target appears in both subtrees you are the answer; if in one, pass that one up; if neither, pass up nothing. No parent pointers, no depth comparison, no second pass — and the correctness argument is genuinely non-obvious the first time you meet it. For many queries on a static tree, binary lifting is the scalable replacement (↗ below). |

## B3 · Information flowing down

The mirror of B2. The parent hands context to the child as a parameter. **What separates these three entries is the *kind* of state travelling down**, and that distinction is easy to miss.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Count Good Nodes in Binary Tree | LC **1448** | **A copied scalar travels down.** The running maximum along the root-to-node path cannot be computed from below, so it is inherited as an argument. Recognising that a quantity is *inherited* rather than *aggregated* is the modelling decision this family exists to teach — and it is what people miss when they reach for a global variable instead. |
| 9 | Path Sum II | LC **113** | **A mutated container travels down, and must be undone.** Append on entry, recurse, pop on exit. Different from #8 because the state is edited in place rather than copied, so correctness now depends on the undo. Backtracking discipline, met in its simplest possible setting. |
| 10 | Path Sum III | LC **437** | **An accumulating map travels down, and must be undone.** Count downward paths summing to `k` with a hash map of prefix-sum frequencies for the *current root-to-node path only* — decrement on the way out so sibling branches never see each other's prefixes. Composes the array prefix-sum-counting trick with #9's undo, and is the clearest case in this file of a technique from another topic being made to work on a tree. |

## B4 · Reconstruction & identity

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Construct Binary Tree from Preorder and Inorder Traversal | LC **105** | **Preorder names the root; inorder locates the split.** Neither sequence alone determines a tree; the pair does. That is the content, not the index arithmetic. The follow-up is the better question: **preorder + postorder is insufficient**, because a node with one child produces identical sequences whether that child is left or right. Knowing *why* a reconstruction is ambiguous beats memorising the code. |
| 12 | Serialize and Deserialize Binary Tree | LC **297** | **Make one traversal invertible by writing the nulls down.** A bare preorder is ambiguous; preorder with explicit null markers is not, so one sequence suffices where #11 needed two. Generalises to any serialisation: record enough to recover the **structure**, not just the values. |
| 13 | Find Duplicate Subtrees | LC **652** | **A canonical serialisation is an identity.** Serialise each subtree bottom-up and use the string, or a hash of it, as its key — turning structural comparison into dictionary lookup. Subtree matching, deduplication and Merkle-style change detection are all this one idea, and it is the bridge from "traverse the tree" to "index the tree". |

## B5 · Structural shortcuts

Each entry either exploits a guarantee to beat `O(n)`, or changes what the tree *is*.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 14 | Count Complete Tree Nodes | LC **222** | **A structural guarantee lets you skip whole subtrees.** Walk the leftmost and rightmost spines; equal depths prove this subtree is perfect, so it holds `2^h − 1` nodes with no traversal at all. Otherwise recurse both ways — and only one child can ever be imperfect, which is what yields `O(log² n)`. General lesson: when a problem volunteers completeness or balance, the intended solution almost certainly beats `O(n)`. |
| 15 | Euler tour flattening | *classic — subtree aggregate with updates* | **Flatten the tree so every subtree is a contiguous range.** Record entry and exit indices in one DFS; the subtree of `v` is exactly the slice `[tin[v], tout[v]]`. Subtree aggregates and subtree updates become array range operations, so a BIT or segment tree finishes the job ([[Segment Trees]] #1). The most powerful reframing in this file, and the reason tree problems and range-query structures keep meeting. |
| 16 | All Nodes Distance K in Binary Tree | LC **863** | **Add parent pointers and it is just an undirected graph.** A tree's downward-only bias is an artefact of the pointer layout, not of the structure; once each node knows its parent, BFS radiates in all three directions and distance-`k` falls straight out ([[Graphs]] #2). Also the right move whenever the input arrives as a parent array or an edge list rather than as linked nodes. |

## B6 · Shape comparison & rewiring

| # | Problem | Source | The new idea |
|---|---|---|---|
| 17 | Symmetric Tree | LC **101** | **Recurse on two nodes at once.** The function takes a *pair* and descends both in lockstep — and for mirroring you deliberately pair `left` against `right`, which is the one place the binary asymmetry is the whole point. Same Tree, Merge Two Trees and Subtree-of-Another are all this shape. |
| 18 | Flatten Binary Tree to Linked List | LC **114** | **In-place rewiring, where assignment order is the entire problem.** You are destroying pointers you still need to read, so you must either save them first or pick a traversal order in which every read precedes its overwrite — which is why the clean solution runs in *reverse* preorder. Inverting a tree, populating next-right pointers and BST-to-doubly-linked-list are all this same hazard. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Binary Tree Maximum Path Sum | LC **124** | #5 with weights, where a negative subtree must be **clamped to zero** rather than merely compared. The clamp is what makes it a DP instead of a traversal, and it is the follow-up to #5 in most interviews. [[Dynamic Programming]] #42. |
| ↗ | House Robber III | LC **337** | **Return a tuple** — `(best if I take this node, best if I don't)` — so the parent chooses without re-descending. The natural step after #5 and the entry point to tree DP proper. [[Dynamic Programming]] #41. |
| ↗ | Sum of Distances in Tree | LC **834** | **Rerooting.** Solve for one root, then derive every other root in `O(n)` by adjusting along each edge — the answer to "compute this for every node as the root", which otherwise costs `n` traversals. [[Dynamic Programming]] #43. |
| ↗ | Kth Ancestor of a Tree Node | LC **1483** | **Binary lifting.** Precompute `2^j`-th ancestors so any jump is `O(log k)`; the same table answers LCA in `O(log n)`, which is what replaces #7 when queries are many and the tree is static. [[Graphs]] #29. |
| ↗ | Minimum Height Trees | LC **310** | **Peel degree-1 nodes inward** to find the centre — Kahn's algorithm on an undirected tree, and the answer to "which root minimises the height". [[Graphs]] #33. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Preorder · Postorder Traversal | LC **144** · **145** | #1 | The same recursion with the visit relocated — the point of #1. |
| Binary Tree Right Side View | LC **199** | #2 | Last node of each level. |
| Average · Largest · Max Sum per Level | LC **637** · **515** · **1161** | #2 | One aggregate per level. |
| Binary Tree Zigzag Level Order | LC **103** | #2 | Reverse alternate levels. |
| Maximum Width of Binary Tree | LC **662** | #2 | Level order carrying heap-style indices. |
| Vertical Order Traversal | LC **314** · **987** | #2 | Traverse assigning `(row, col)`, then bucket and sort. |
| Cousins in Binary Tree | LC **993** | #2 | Depth plus parent identity. |
| Minimum Depth of Binary Tree | LC **111** | #2, #4 | Guard against null children, or level-order with an early exit. |
| Find Leaves of Binary Tree | LC **366** | #4 | The height returned upward *is* the bucket index. |
| Longest Univalue Path | LC **687** | #5 | Return-one-record-another with an equality guard. |
| Longest ZigZag Path | LC **1372** | #5 | Return a pair, one per direction. |
| Longest Consecutive Sequence in Binary Tree | LC **298** · **549** | #5 | Return increasing and decreasing lengths. |
| Maximum Difference Between Node and Ancestor | LC **1026** | #8 | Carry min and max downward. |
| Sum Root to Leaf Numbers | LC **129** | #8 | Accumulate a value downward. |
| Binary Tree Paths | LC **257** | #9 | Path with undo, emitted at leaves. |
| Smallest String Starting From Leaf | LC **988** | #9 | Path with undo, compared lexicographically. |
| Path Sum | LC **112** | #9 | The boolean version. |
| Construct from Inorder and Postorder | LC **106** | #11 | Postorder names the root from the back. |
| Construct from Preorder and Postorder | LC **889** | #11 | Solvable *only* because the problem forbids single children — the exact ambiguity #11 describes. |
| Subtree of Another Tree | LC **572** | #13 or #17 | Canonical-form matching, or lockstep comparison at every node. |
| Same Tree · Merge Two Binary Trees | LC **100** · **617** | #17 | Lockstep on a pair. |
| Flip Equivalent Binary Trees | LC **951** | #17 | Lockstep, trying both pairings. |
| Linked List in Binary Tree | LC **1367** | #17 | Lockstep against a list instead of a tree. |
| Invert Binary Tree | LC **226** | #18 | Swap children during any traversal. |
| Populating Next Right Pointers II | LC **117** | #18 | Use the already-threaded level above as a linked list — in-place rewiring at `O(1)` space. |
| Print Binary Tree | LC **655** | #2 | Level order into a padded grid. |
| Boundary of Binary Tree | LC **545** | #1 | Three traversals concatenated. Fiddly, no new idea. |
| Binary Tree Tilt · Sum of Left Leaves | LC **563** · **404** | #4 | Postorder aggregation with a different accumulator. |

---

## Self-audit

**Borderline calls, and which way I went**

- **Diameter (#5) native, Maximum Path Sum (LC 124) cross-listed.** The same idea; the unweighted version teaches it more cleanly and the weighted one adds the clamp step that makes it DP. Splitting a pair across two files like this is exactly what cross-listing is for.
- **B3 split into three entries where most sources have one.** #8, #9 and #10 all "pass information down", and I nearly merged them. Kept apart because the *kind* of descending state differs — copied scalar, mutated container, accumulating map — and only the last two require an undo. That axis was missing from my first draft of this material and its absence would have hidden #10 entirely.
- **Morris (#3) included.** Tail-flavoured, but it is a named follow-up to a top-50 problem and "mutate the structure to store your return path" has nowhere else to live.
- **Euler tour (#15) here rather than in [[Segment Trees]].** The flattening is a tree idea; what you do with the resulting array is a range-query idea. Filed at the origin, linked forward.
- **Level-order kept native despite also appearing in [[N-ary Trees]] and being cross-listed in [[Stack and Queue]].** Correct under the cross-listing rule, and the alternative — a binary tree file without level-order — is absurd.
- **Tree DP is mostly cross-listed rather than native**, which leaves B2 looking thinner than the topic is: four entries and three pointers where the material is genuinely deep. I think that is right, since the machinery belongs to DP, but a reader drilling only this file could reasonably want #124 and #337 promoted.

**Step 4B — reverse sweep**

Twenty-six plain-language descriptions navigated against the family headings. One failure, one collision:

- **"Walk the tree without recursion because it is too deep"** resolved only through #1's body text, not through any heading. The iterative-stack traversal is genuinely a *different* thing from the recursive one — it is what you write when `h` is large — and it is currently a clause inside #1 plus a cross-list. Left merged, but flagged: if the stack-overflow framing matters to you, this deserves promotion.
- **"Find the path with the largest sum"** landed on both #5 and ↗ LC 124. Checked and correct: the second genuinely is the first plus clamping, and the cross-list says so.

**What I am uncertain about**

- **Recursion depth as a first-class concern.** A skewed tree is `O(n)` deep, and "convert this to iterative" is a real interview request. It appears in #1 and #3 but has no entry of its own. The most likely thing I have under-weighted.
- **Threaded binary trees** beyond Morris — excluded, high confidence.
- **Array-indexed binary trees** (`2i+1`, `2i+2`) appear in #14's argument and in [[Heap]] #1, but the indexing scheme itself is not an entry here. Probably right, mildly uncertain.
- **The exclusions table runs to 29 rows**, seven of them collapsing into level-order alone. That is where a wrong merge would hide.

**Completeness confidence: ~92%.** The concept space is well-explored; risk sits in the exclusions and in the tree-DP boundary rather than in absences.

## Related Notes

- [[README]]
- [[Binary Search Trees]]
- [[N-ary Trees]]
- [[Graphs]]
- [[Dynamic Programming]]
- [[Segment Trees]]
- [[Stack and Queue]]
