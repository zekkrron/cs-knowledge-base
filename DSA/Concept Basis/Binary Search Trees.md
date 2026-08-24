---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-24
---
# Concept Basis — Binary Search Trees

> [!abstract] Minimal spanning set for BSTs — ideas that depend on the **ordering invariant**. One entry per **new idea you have to learn**. Structure-only binary tree ideas live in [[Binary Trees]]. Cross-listed entries (↗) are developed further elsewhere but belong here too.

## The one property, and the four things you do with it

A BST is a binary tree plus one sentence: *everything in the left subtree is smaller, everything in the right subtree is larger*. Every entry below is a consequence, and they sort into four verbs.

| Verb | What it means | Family |
|---|---|---|
| **Check** it | is this actually a BST? | S1 |
| **Exploit** it | prune, descend, or skip work because of the ordering | S2 |
| **Maintain** it | insert, delete, and keep the shape usable | S3, S4 |
| **Repair** it | something violated the invariant; find and fix | S3 |

> [!tip] **The master fact, and the only one you must never forget: an inorder traversal of a BST is sorted.**
>
> That single sentence converts every order-statistic, successor, predecessor, closest-value, range, mode and k-th question into a walk over a sorted sequence. Reverse inorder gives descending order for free. If you learn nothing else from this file, learn that a BST *is* a sorted array that happens to support `O(log n)` insertion — the tree shape is a choice about how to index it, nothing more.

## Mechanism axes

| Axis | Values |
|---|---|
| **What you do with the invariant** | check it · exploit it to prune · maintain it under mutation · repair a violation |
| **What the ordering buys** | inorder = sorted · `O(h)` descent · range pruning · successor / predecessor · structural information you no longer need to store |
| **Traversal direction** | forward inorder (ascending) · reverse inorder (descending) · descent by comparison, visiting one child only |
| **Node augmentation** | none · subtree size (rank / select) · subtree sum · duplicate count |
| **Balance** | assumed · actively maintained by rotation · ignored, and therefore degenerate |
| **Who owns the tree** | the problem hands it to you · **you build it yourself as a tool** |
| **Duplicate policy** | forbidden · sent left · sent right · counted in the node |
| **What breaks** | a per-node local check misses distant violations · sorted insertion order degenerates to a linked list · a plain BST cannot answer rank without augmentation |

## Shape of this topic

```
S1  The invariant, and how easily it is misread   2 ideas
S2  Exploiting the order                          4 ideas
S3  Maintaining and repairing it                  3 ideas
S4  Shape, balance, augmentation                  3 ideas
S5  Order-dependent traversal                     2 ideas
S6  The BST as a live container                   2 ideas
                                                  + 3 cross-listed ↗
```

**16 native entries, plus 3 cross-listed (↗).** See [[README]] on cross-listing.

---

## S1 · The invariant, and how easily it is misread

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Validate Binary Search Tree | LC **98** | **The BST property is global, not local.** Checking `left.val < node.val < right.val` at each node is the classic wrong answer — it accepts trees where a deep left descendant exceeds a distant ancestor. The fix is to carry an open interval `(low, high)` downward, narrowing it at each step, which makes this an information-flows-**down** problem wearing BST clothing ([[Binary Trees]] #8). Worth doing precisely *because* the intuitive solution fails; the alternative solution — inorder must be strictly increasing — is #2 in disguise. |
| 2 | Kth Smallest Element in a BST | LC **230** | **Inorder is sorted, and that is the master fact.** Every order question reduces to a position in that sequence: the `k`-th smallest is a counted walk, the **successor** is one step forward, the **predecessor** one step back, the **mode** is the longest run of equal values in `O(1)` space, and two-sum becomes two pointers over the inorder array. Stopping early gives `O(k)`; augmenting nodes with subtree sizes gives `O(h)` and is #12. |

## S2 · Exploiting the order

Four different ways the invariant lets you avoid work.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Search in a BST · LCA of a BST | LC **700** · **235** | **Descend by comparison — one subtree is eliminated at every step.** This is the reason the structure exists: `O(h)` instead of `O(n)`, visiting one child rather than both. LCA becomes almost trivial: walk down while both targets sit on the same side, and the first node that splits them is the answer. Compare that to the postorder consensus argument the general binary tree needs ([[Binary Trees]] #7) — the gap between those two solutions is exactly what the ordering buys you. |
| 4 | Range Sum of BST | LC **938** | **Prune on bounds, not on a single target.** If `node.val < low`, the entire left subtree is irrelevant and is never entered; symmetrically on the right. #3 eliminates a subtree because it cannot contain *the* value; this eliminates a subtree because it cannot contain *any* value in the range — a two-sided condition, and the shape of every "all keys in `[low, high]`" query. |
| 5 | Closest Binary Search Tree Value | LC **270** | **The search path contains the answer even when the search fails.** Descend as if looking for the target; the nodes you pass are exactly the candidates, so track the best as you go. The insight generalises past BSTs: **a failed lookup is not a wasted one** — the path it took is informative, which is the same reason `lower_bound` returns an insertion point rather than "not found". |
| 6 | Serialize and Deserialize BST | LC **449** | **The invariant replaces the structural markers you would otherwise have to store.** A general binary tree needs explicit nulls in its serialisation to be invertible ([[Binary Trees]] #12); a BST needs none, because a bare preorder plus the ordering rule determines the shape uniquely — each value's position is forced by the values before it. The transferable idea is that **a maintained invariant is stored information**, so anything it implies is redundant to write down. |

## S3 · Maintaining and repairing it

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Insert into a BST | LC **701** | **Insertion is forced, always lands at a leaf — and its *order* determines the shape.** There is exactly one legal position for a new key, found by the same descent as #3. The consequence matters far more than the code: **inserting sorted data builds a linked list**, which is why unbalanced trees happen in practice and why #11 exists. This is also the mechanism behind building a BST as a tool to answer counting questions (↗ LC 315). Duplicates need an explicit policy — forbid, send right, or keep a count in the node — and interviewers do ask which you chose. |
| 8 | Delete Node in a BST | LC **450** | **The three-case surgery.** A leaf detaches; a one-child node is bypassed; a two-child node is overwritten by its **inorder successor**, which is then deleted from the right subtree. The only mutation with real content, and the reason is worth naming: deletion is the sole operation that must *choose* a replacement, and the successor is the unique key that preserves the invariant on both sides. |
| 9 | Recover Binary Search Tree | LC **99** | **Repair by finding the inversions in the inorder sequence.** Two swapped nodes produce either one adjacent inversion or two separated ones, so a single inorder pass with a `prev` pointer identifies both offenders and you swap their values. New because it is neither a query nor an insertion — it is *diagnosis*, and it works by comparing the tree against the sorted sequence it is supposed to be. |

## S4 · Shape, balance, augmentation

| # | Problem | Source | The new idea |
|---|---|---|---|
| 10 | Convert Sorted Array to Binary Search Tree | LC **108** | **The middle element is the root.** Recursing on halves produces a height-balanced tree, and this is the *constructive* reason `O(log n)` is achievable at all. Read alongside #2 it is an isomorphism: sorted sequence and balanced BST are two views of one object, and #10 and #2 are the two directions of the conversion. |
| 11 | Rotations, AVL and red-black trees | *concept — `TreeMap`, `std::map`, `std::set`* | **Why balance must be maintained, and why you never implement it.** An unbalanced BST degrades to `O(n)`, so real implementations rotate on insertion to hold height at `O(log n)`. You should be able to draw a single rotation and say which invariant it restores; you should not write one in an interview. What matters practically is knowing that the library structure is a balanced BST, so every `O(log n)` guarantee you rely on comes from here. *No coding representative — this is a concept entry, per the method's `MISSING` convention.* |
| 12 | Order-statistic trees | *concept — subtree-size augmentation · `__gnu_pbds::tree`* | **Augment each node with its subtree size and the tree answers rank questions.** `select(k)` becomes a descent comparing `k` against the left subtree's size; `rank(x)` accumulates sizes as it descends. Both `O(log n)`, where #2's counted walk was `O(k)`. The general principle is the one that also drives segment trees: **store an aggregate at each node and queries that were linear become logarithmic.** A value-indexed BIT is the more robust answer to the same questions ([[Segment Trees]] #3). |

## S5 · Order-dependent traversal

| # | Problem | Source | The new idea |
|---|---|---|---|
| 13 | Convert BST to Greater Sum Tree | LC **538** | **Reverse inorder gives descending order for free, so suffix aggregates fall out.** Visit right, then node, then left, carrying a running total. Nothing about the tree changes — only the direction you read it — and any "sum of everything larger than me" question becomes a one-pass accumulation. The lesson is small and reusable: *the traversal direction is a free parameter*. |
| 14 | Binary Search Tree Iterator | LC **173** | **A resumable inorder.** Keep the descent's stack as object state so `next()` continues where the last call stopped: `O(h)` memory, amortised `O(1)` per call, and nothing materialised. This is what makes "the tree is too large to flatten" tractable, and it is the same laziness as a generator ([[Stack and Queue]] #19). Also the honest answer to #2 when `k` is unknown in advance. |

## S6 · The BST as a live container

Here the BST is not the problem — it is the **tool**. You build it yourself because you need ordered queries over data that keeps arriving.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 15 | Contains Duplicate III | LC **220** | **A balanced BST over a sliding window answers "is anything near this value".** Maintain a `TreeSet` of the last `k` elements and ask `ceiling(x - t)`, checking whether what comes back is `≤ x + t`. Two things are new: the set is **windowed**, so you evict as you advance, and the query is a **neighbourhood** rather than an exact match — which is precisely what a hash set cannot do and an ordered set can. |
| 16 | My Calendar I | LC **729** | **`floorKey` and `ceilingKey` find an insertion's neighbours in `O(log n)`.** To check whether a new interval overlaps anything, you only ever need the interval starting just before it and the one just after — not a scan. Interval merging on a stream, "insert into a sorted structure and check both sides", and most calendar or booking problems are this one query pair. The reflex worth building: **whenever you need a neighbour rather than a match, reach for an ordered map.** |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Count of Smaller Numbers After Self | LC **315** | Insert right-to-left into a size-augmented BST (#7 plus #12) and each insertion reports how many smaller keys preceded it. The canonical demonstration that a BST can be *built as a counting tool*; a value-indexed BIT is the sturdier implementation. [[Segment Trees]] #3. |
| ↗ | Unique Binary Search Trees | LC **96** | Counting distinct BST *shapes* on `n` keys is Catalan DP — fix a root, multiply the subtree counts. Not a tree traversal at all, which is why it feels out of place until you notice it never touches a tree. [[Dynamic Programming]] #64. |
| ↗ | Generate All BSTs | LC **95** | Enumerating every shape rather than counting them is search, not DP, and the recursion returns *lists of subtrees* to be combined pairwise. Backtracking basis. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Minimum Absolute Difference in BST | LC **530** | #2 | Adjacent pairs in the inorder sequence. |
| Find Mode in Binary Search Tree | LC **501** | #2 | The longest run of equal values in inorder, in `O(1)` space. |
| Two Sum IV — Input is a BST | LC **653** | #2 | Inorder to an array, then two pointers. |
| Inorder Successor in BST | LC **285** | #2 or #3 | One inorder step, or a downward walk tracking the last left turn. |
| Increasing Order Search Tree | LC **897** | #2 | Inorder, rewiring as you go. |
| Range Sum Query — BST variants | — | #4 | Bounds pruning with a different accumulator. |
| Trim a Binary Search Tree | LC **669** | #4 | Bounds pruning that returns the rebuilt subtree. |
| All Elements in Two BSTs | LC **1305** | #2 | Two inorder walks merged — the merge is [[Heap]] #7's idea. |
| Closest BST Value II | LC **272** | #5 | The search path plus a bidirectional expansion. |
| Search in a BST (iterative) | LC **700** | #3 | The loop form of the same descent. |
| Insert · Delete in a BST (recursive vs iterative) | LC **701** · **450** | #7 · #8 | Implementation shape, not idea. |
| Balance a Binary Search Tree | LC **1382** | #10 | Inorder to an array, then #10. |
| Convert Sorted List to BST | LC **109** | #10 | Same, with the middle found by two pointers, or by inorder simulation. |
| Minimum Distance Between BST Nodes | LC **783** | #2 | A duplicate of LC 530. |
| BST to Sorted Doubly Linked List | LC **426** | #13 | In-place rewiring during inorder — the rewiring hazard is [[Binary Trees]] #18. |
| Kth Largest Element in a BST | — | #13 | Reverse inorder, counted. |
| My Calendar II · III | LC **731** · **732** | #16 | Overlap counting on top of the same neighbour queries; a sweep with coordinate compression scales better ([[Segment Trees]] #11). |
| Data Stream as Disjoint Intervals | LC **352** | #16 | `floorKey`/`ceilingKey` then merge both sides. |
| Contains Duplicate II | LC **219** | #15 | Index-only window — a hash map suffices, since no neighbourhood query is needed. |

---

## Self-audit

**Borderline calls, and which way I went**

- **Search and LCA merged into one entry (#3).** Both are "descend by comparison, discard a subtree." Merging them is the point: LCA looks like a harder problem and is not, once you see it as the same descent with a different stopping rule.
- **Insert (#7) kept despite being three lines.** Not for the code — for the consequence. "Inserting sorted data builds a linked list" is the fact that motivates #11 and licenses ↗ LC 315, and burying it inside #8 would have hidden it.
- **Two concept entries with no coding problem (#11, #12).** Uncomfortable, and correct. Rotations and size-augmentation are things you must be able to *discuss* and should never implement under time pressure. The method's `MISSING representative` convention exists for exactly this, and the alternative — dropping them — would leave the file unable to explain where `O(log n)` comes from.
- **S6 exists at all.** The BST-as-tool framing overlaps heavily with the still-missing sorted-container topic. Included because a BST file that never shows you *using* a `TreeMap` has taught you the data structure and not the skill.
- **Validate (#1) is arguably a [[Binary Trees]] problem.** It is information-flowing-down with a `(low, high)` parameter, and the machinery is not BST-specific. Kept here because the *lesson* — the invariant is global — is about the invariant, and that is this file's subject.

**Step 4B — reverse sweep**

Thirty plain-language descriptions navigated against the family headings. **One failure, and it was a real absence:**

- **"Save a BST to a string and read it back"** landed nowhere. That is #6, now in S2. The idea it carries is the good one: **a maintained invariant is stored information, so a BST serialisation needs no null markers where a general binary tree does.** Notably, this concept was **missing from the earlier combined trees file** — it only surfaced once BSTs were swept in isolation, because in a merged file "serialisation" resolved to the binary-tree entry and the sweep stopped there. That is a direct argument for the split you asked for.
- Two collisions, both checked and cleared. "Successor of a node" reaches #2 and #3 (two genuine solutions, correctly noted in both). "Find all keys in a range" reaches #4 and #16 (query on a static tree versus a maintained container — different families, correctly separated).

**What I am uncertain about**

- **The sorted-container gap is now mostly closed by S6 plus #12**, but not entirely. `bisect.insort` over a plain list, and the "sorted window as a multiset" pattern, still have no proper home. Downgrading it from a missing topic to a missing section.
- **Duplicate handling** is a sentence inside #7 rather than an entry. It is asked ("what if there are duplicates?") often enough that this may be under-weighted.
- **Treaps, splay trees, scapegoat trees** — excluded as competitive-programming or theory scope. High confidence.
- **B-trees and B+ trees** — excluded as a database and systems topic, not DSA. Moderate confidence; they do come up in system design rounds, where they belong.
- **Threaded BSTs** — excluded; Morris covers the useful half ([[Binary Trees]] #3).
- **Whether #1 belongs here or in [[Binary Trees]]** is the one placement I would most readily reverse.

**Completeness confidence: ~94%.** Higher than either sibling file, because the topic is generated by a single property and the consequences of one property are genuinely enumerable — which is the rare case where the cross-product method is close to exhaustive rather than merely thorough.

## Related Notes

- [[README]]
- [[Binary Trees]]
- [[N-ary Trees]]
- [[Segment Trees]]
- [[Heap]]
- [[Binary Search]]
