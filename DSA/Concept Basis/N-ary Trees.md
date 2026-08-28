---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-24
---
# Concept Basis — N-ary Trees

> [!abstract] Minimal spanning set for n-ary trees — ideas that appear or change once a node can have **any number of children**. One entry per **new idea you have to learn**. Two-child-specific ideas live in [[Binary Trees]]; ordering-dependent ideas in [[Binary Search Trees]]. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## Scope warning, read this first

This is the **smallest of the three tree files, and honestly so.** Most n-ary tree problems are either (a) a binary tree solution with `left`/`right` replaced by a loop, which teaches nothing new, or (b) a general graph problem that happens to be acyclic, which belongs to [[Graphs]].

So the file is deliberately narrow: it holds only the places where **arity itself changes the mechanics.** That turns out to be a real list — five or six ideas that genuinely do not exist in the binary case — plus the practical fact that almost every tree you meet in real software is n-ary, not binary.

> [!warning] **The defining negative fact: there is no inorder traversal.**
>
> "Visit left, then node, then right" needs exactly two children. With `k` children there is no canonical place to put the node, so inorder simply does not exist — and with it goes the entire [[Binary Search Trees]] toolkit that depended on inorder being sorted. What survives is preorder, postorder and level-order. Knowing *why* a technique vanished is worth as much as knowing the ones that remain.

## The organising question

> [!tip] **On a binary tree you ask "which traversal?" On an n-ary tree you ask "what does the branching cost me?"**
>
> Three consequences follow, and they generate most of this file. Combining children is no longer a two-argument comparison but a **fold, sometimes needing the top two or a sort**. Recursion depth is usually *small* while total width is *large*, so the expensive thing moves from stack depth to per-node work. And structure is no longer implicit in the pointer layout, so **anything you serialise or compare must state the arity explicitly.**

## Mechanism axes

| Axis | Values |
|---|---|
| **How children are stored** | fixed array of size `k` · dynamic list · map keyed by name or character · first-child + next-sibling pointers · implicit by array index |
| **Is child order meaningful?** | yes, order is part of identity · no, children are a set |
| **How children are combined** | fold with an associative operator · needs the **top two** · needs a sort · needs a merge of child containers |
| **Degree** | unbounded · bounded by `k` · exactly `k` (complete) |
| **Where the tree comes from** | given as linked nodes · built from an edge list or parent array · built from paths or strings |
| **Traversal available** | preorder · postorder · level-order — **never inorder** |
| **Dominant shape** | wide and shallow · deep and path-like · balanced |
| **What breaks** | no inorder, so no sorted view · no `left`/`right` asymmetry to mirror against · child-order ambiguity in comparison · a naive merge at each node is quadratic |

## Shape of this topic

```
N1  Traversal                       2 ideas
N2  Serialisation & identity        2 ideas
N3  Aggregation upward              3 ideas
N4  Representation                  3 ideas
N5  Domain-shaped n-ary trees       2 ideas
                                    + 4 cross-listed ↗
```

**12 native entries, plus 4 cross-listed (↗).** See [[README]] on cross-listing.

---

## N1 · Traversal

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | N-ary Tree Preorder · Postorder Traversal | LC **589** · **590** | **`left`/`right` becomes a loop, and inorder disappears.** Mechanically trivial, conceptually the entry point: preorder and postorder generalise cleanly because they only ask "before or after the children", while inorder asked "between *which* children" and has no answer. The iterative form has one wrinkle worth knowing — to emit children left-to-right you must push them **right-to-left**. |
| 2 | N-ary Tree Level Order Traversal | LC **429** | **For a wide tree, level-order is the natural traversal, not an alternative one.** Real n-ary trees — org charts, file trees, DOM — are shallow and broad, so BFS mirrors how you actually think about them, and the queue's width rather than its depth is what you must budget for. Same level-snapshot trick as [[Binary Trees]] #2, with the opposite performance profile: cheap depth, expensive frontier. |

## N2 · Serialisation & identity

Both entries exist because **arity is no longer implicit**, so anything that has to reconstruct or compare structure must say it out loud.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Serialize and Deserialize N-ary Tree | LC **428** | **You must write the arity down — either a child count or a close-sentinel.** A binary tree can serialise with null markers, because two slots per node are known in advance and a null names an absent one ([[Binary Trees]] #12). With unbounded children there are no slots, so you emit either `value degree child...` or `value child... ]`. This is the cleanest illustration in the file that **binary trees smuggle structural information into their pointer layout**, and n-ary trees have to pay for it explicitly. |
| 4 | Delete Duplicate Folders in System | LC **1948** | **When children are unordered, canonical form requires sorting them.** Two subtrees are identical if they hold the same children regardless of insertion order, so a serialisation is only a valid identity after each node's child signatures are sorted before concatenating. Binary trees never face this — `left` and `right` are fixed roles, so serialisation is canonical for free. Structural comparison also stops being a lockstep pair recursion ([[Binary Trees]] #17), because there is no fixed pairing to walk: **you must canonicalise and then compare strings or hashes.** The general lesson: identity under an unordered structure means picking a canonical order first. |

## N3 · Aggregation upward

| # | Problem | Source | The new idea |
|---|---|---|---|
| 5 | Maximum Depth of N-ary Tree | LC **559** | **`max(left, right)` becomes a fold over children.** The base case of the family, and worth stating once: any *associative* combine — sum, max, count, min — generalises to arbitrary arity with no thought at all. Every entry below is a case where the combine is **not** simply associative, which is where the real content is. |
| 6 | Diameter of N-ary Tree | LC **1522** | **You need the top *two* child depths, not `left` and `right`.** This is the genuine generalisation of return-one-record-another ([[Binary Trees]] #5), and the difference is structural rather than cosmetic: binary got its two candidates by having exactly two children, so the "pick the best two" step was invisible. With `k` children it becomes an explicit partial selection — track the two largest in one pass, or sort and take the front, `O(k log k)`. Once you see it, **"the answer combines the best `m` children"** is a reusable shape, and choosing between a linear scan and a sort is a real decision. |
| 7 | Number of Nodes in the Sub-Tree With the Same Label | LC **1519** | **Merge child containers small-into-large, or the aggregation is quadratic.** When each node returns a *set* or *frequency map* rather than a number, the naive fold copies the same elements repeatedly and degrades to `O(n²)`. Always absorbing the smaller container into the larger caps the total at `O(n log n)` — each element is moved only when its container at least doubles. Small-to-large merging (DSU on tree) is the technique, and it exists *because* arity makes the per-node merge cost visible. ↗ from [[Union-Find]]; native here because the fold is on a tree, not a forest of parents. |

## N4 · Representation

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Encode N-ary Tree to Binary Tree | LC **431** | **Every n-ary tree *is* a binary tree in disguise: `left` = first child, `right` = next sibling.** A bijection, and the single most useful fact in this file. It means arity is never a fundamental obstacle — you can reuse binary machinery, binary bounds and binary intuition on any n-ary tree by relabelling the pointers. The catch worth internalising: **depth is not preserved.** A node with a thousand children becomes a thousand-deep right spine, so anything depth-sensitive must be re-derived rather than transferred. |
| 9 | Building the tree from an edge list or parent array | *classic — LC **1466**, and the input format of most tree problems* | **N-ary trees usually *arrive* as edges, not as nodes with children.** Binary tree problems hand you a root; n-ary problems hand you `n` and a list of pairs, so constructing the adjacency, choosing a root and orienting the edges away from it is a real step that binary problems skip entirely. This is also the seam where trees and graphs meet: the same input read as an undirected graph gives you a tree with parent pointers for free ([[Graphs]] #1). Get the reflex — *build adjacency, root it, then recurse* — and half the "tree" problems on any judge become mechanical. |
| 10 | Implicit `k`-ary trees by array index | *concept — `k`-ary heap · segment tree layout* | **When the tree is complete and the degree is fixed, pointers are unnecessary: the children of `i` are `k·i+1 … k·i+k`.** The binary case is the standard heap layout ([[Heap]] #1); generalising to `k` trades a shallower tree, `log_k n` depth, against `k` comparisons per sift-down — which is the actual reason 4-ary heaps sometimes win on real hardware. The transferable idea is that **a structural guarantee can eliminate the data structure**, leaving only arithmetic. |

## N5 · Domain-shaped n-ary trees

Where n-ary trees actually live. Included because the *shape of the input* changes the solution, not just the traversal.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Design In-Memory File System · Remove Sub-Folders | LC **588** · **1233** | **Children keyed by name, not by position — so lookup is by path, not by index.** A `map<string, Node>` per node is the n-ary form you meet in practice: file systems, the DOM, JSON, config trees. Two consequences follow. Navigation becomes *split the path and descend key by key*, and sorting the paths lexicographically makes any parent appear immediately before its descendants, which collapses LC 1233 to a single scan without building a tree at all. Knowing when **not** to build the tree is the point. |
| 12 | Time Needed to Inform All Employees | LC **1376** | **Wide and shallow moves the cost from depth to breadth.** The canonical org-chart problem: recursion is safe because depth is tiny, but per-node work must be cheap because the branching is enormous — the exact inverse of the binary tree hazard, where depth is the risk. Also the standard demonstration of #9, since the input is a manager array rather than a tree, and of top-down accumulation on an n-ary tree ([[Binary Trees]] #8). |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Implement Trie | LC **208** | **The archetypal practical n-ary tree: children keyed by character.** Degree bounded by the alphabet, and the path from the root *is* the data — the node itself often stores nothing but a terminal flag. Everything about #11's map-of-children applies, specialised to a fixed alphabet. [[Tries]] #1. |
| ↗ | Minimum Height Trees | LC **310** | **Peel degree-1 nodes inward to find the centre** — the answer to "which root minimises the height", and a direction of processing that neither traversal in N1 covers. [[Graphs]] #33. |
| ↗ | Sum of Distances in Tree | LC **834** | **Rerooting** — solve for one root, then derive every other root in `O(n)` by adjusting along each edge. Nearly always posed on a general n-ary tree given as an edge list, so it composes directly with #9. [[Dynamic Programming]] #43. |
| ↗ | Longest Path With Different Adjacent Characters | LC **2246** | #6's top-two merge under a constraint: only children whose label differs from yours are eligible candidates. The step from n-ary diameter to n-ary tree DP. [[Dynamic Programming]] #41. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| N-ary Tree Level Order — per-level aggregate | — | #2 | One accumulator per level. |
| Clone N-ary Tree | LC **1490** | #1 | Preorder that allocates as it goes. |
| Find Root of N-ary Tree | LC **1506** | #9 | Rooting an edge list; the XOR trick is arithmetic, not a new idea. |
| Diameter via two DFS passes | *classic* | #6 | Farthest-from-farthest. Elegant, same quantity, and it needs a correctness proof #6 does not. |
| Maximum Depth via BFS | — | #2 · #5 | Level count instead of a fold. |
| Count nodes · sum values · count leaves | — | #5 | Associative folds with a different operator. |
| Serialize N-ary with a level-order format | LC **428** | #3 | Still an explicit degree marker, laid out breadth-first. |
| Employee Importance | LC **690** | #12 | Subtree sum over a map-of-children. |
| Longest Absolute File Path | LC **388** | #11 | Depth from indentation, plus a stack of prefix lengths ([[Stack and Queue]] #6). |
| Lowest Common Ancestor of an N-ary Tree | LC **1650** *and variants* | — | Identical to the binary case: postorder consensus ([[Binary Trees]] #7) or binary lifting ([[Graphs]] #29). Arity changes nothing. |
| N-ary Tree Symmetry / equality with ordered children | — | #4 | Lockstep pairwise on the child lists; only the *unordered* case is a new idea. |
| Nested List Weight Sum | LC **339** · **364** | #1 · #2 | An n-ary tree wearing JSON clothing; depth-weighted fold. |
| Directory tree printing | — | #11 | Preorder with indentation. |
| K-ary heap operations | — | #10 | The indexing scheme is the content; the sift logic is [[Heap]] #1. |
| Tree isomorphism by AHU hashing | *classic* | #4 | The named algorithm for #4's canonicalisation. |

---

## Self-audit

**Borderline calls, and which way I went**

- **The whole file's existence.** Judged individually, four or five entries here would not survive on a binary tree file — #1 and #5 are near-trivial. Kept because you asked for these treated separately, and because a file that skipped the trivial generalisations would not let you *see* which techniques survived arity and which died. The negative facts are the value.
- **#4 (unordered identity) is the entry I am most confident is genuinely n-ary-specific**, and it only surfaced during the sweep. It is also the hardest representative in the file, which is a sign it is real content rather than an artefact of splitting.
- **#9 (build from an edge list) sits on the [[Graphs]] boundary** and could reasonably be called a graph idea. Kept here because it is the *default input format* for n-ary tree problems and a reader drilling this topic will hit it before anything else.
- **#10 is a concept entry with no coding representative** — the `MISSING` convention, same as [[Binary Search Trees]] #11 and #12.
- **#11 and #12 are "domain" entries**, which is a category the other tree files do not have. Justified because the map-of-children representation and the wide-shallow shape genuinely change the solution, and because this is where n-ary trees actually appear in interviews — file systems and org charts, not `Node` with a `children` list.
- **Trie cross-listed, not native.** It is the most important n-ary tree in practice, but the entire technique belongs to its own basis. #11 carries the representational idea.

**Step 4B — reverse sweep**

Twenty plain-language descriptions navigated against the family headings. **One failure:**

- **"Are these two subtrees the same, when the children could be in any order?"** landed nowhere. Binary Trees' lockstep-pair entry does not apply — there is no fixed pairing to walk — and no serialisation entry mentioned canonicalisation. That is #4, now in N2, and it exposed a missing axis: **"is child order meaningful?"**, now in the axis table. It also retro-justifies the split, since in the earlier combined file this description resolved to the binary lockstep entry and the sweep terminated satisfied.

One near-miss worth recording: **"the tree is one long path, so recursion overflows"** reaches only #12, which discusses the *opposite* shape. A degenerate path-shaped n-ary tree is a real hazard — and it is exactly what #8's encoding manufactures. Noted in #8 rather than promoted, but this is the thinnest coverage in the file.

**What I am uncertain about**

- **Recursion depth on path-shaped n-ary trees**, per above. The most likely under-weighting.
- **Whether #1 and #5 should exist**, given how little they teach. They are load-bearing as the base cases that #6 and #7 deform, so probably yes.
- **Forest handling** — multiple roots, disconnected input. Common in real code, rare in interviews, no entry. Moderate confidence in excluding it.
- **Heavy-light decomposition and centroid decomposition** — excluded as competitive-programming scope, and both are really [[Graphs]] plus [[Segment Trees]] material.
- **B-trees** as bounded-degree n-ary search trees — excluded to systems and database scope, consistent with [[Binary Search Trees]].

**Completeness confidence: ~88%.** The lowest of the three tree files, and the reason is a genuine property of the topic rather than a gap in the sweep: the boundary between "n-ary tree" and "acyclic graph" is a judgement call, and I have drawn it at *whether arity changes the mechanics*. A reader who draws it differently would want several [[Graphs]] entries pulled in here.

## Related Notes

- [[README]]
- [[Binary Trees]]
- [[Binary Search Trees]]
- [[Graphs]]
- [[Heap]]
- [[Dynamic Programming]]
- [[Tries]]
- [[Design]]
- [[Union-Find]]
