---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Linked List

> [!abstract] Minimal spanning set for **rewiring a structure you can only walk forwards**. One entry per **new idea you have to learn**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!warning] **A linked list is almost always the wrong data structure, and knowing that is part of the topic.** An array beats it on nearly everything that matters in practice — random access, cache locality, memory overhead per element — and "use a linked list" is rarely the right production answer. So why learn it? Two honest reasons. **One:** it is the cleanest possible test of pointer discipline, which is why interviewers keep asking. **Two:** it has exactly one irreplaceable property — **`O(1)` removal and insertion at a position you already hold** — and that single property is what makes L5's designs possible and what no array can give you.

## What the constraint actually is

> [!tip] Every idea in this file comes from one of three deprivations. Naming which one a problem imposes usually names the technique.
>
> | You do not have | So you cannot | And the answers are |
> |---|---|---|
> | **random access** | index, binary search, or jump to the middle | walk with two pointers at a chosen offset or speed (#3) |
> | **backward links** *(singly linked)* | look at the previous node, or traverse in reverse | reverse the list, use an explicit stack, or ride the recursion's unwind (#5) |
> | **a handle on the predecessor** | delete or insert before a node you are standing on | keep a `prev`, use a dummy head (#2), or impersonate your successor (#16) |

## Mechanism axes

| Axis | Values |
|---|---|
| **What you are given** | the head · **only a node, with no head** · head and tail · a head that may be `null` |
| **Links available** | forward only · forward and backward · **plus arbitrary extra pointers** (random, child, express lanes) |
| **Structure shape** | acyclic · may contain a cycle · **circular** · sorted · multilevel / nested |
| **How you avoid special-casing the head** | a **dummy / sentinel** node · a pointer-to-pointer · returning the new head and letting the caller rebind |
| **What travels with the walk** | one pointer · two at a fixed offset · two at **different speeds** · a `prev` you are building · the recursion stack |
| **May you modify the list?** | rewire freely · rewire but **restore it afterwards** · read-only |
| **Getting reverse order** | reverse the list in place · push onto an explicit stack · **recurse and act on the way back up** · two passes |
| **What is returned** | a node · the **new head** · a boolean · the list rewired · a copy |
| **Where the bug will be** | overwriting `next` before saving it · the head or tail edge case · off by one on *which* node you stop at · a cycle breaking termination · forgetting `prev` on a doubly linked list |

## Shape of this topic

```
L1  The primitives                        3 ideas
L2  You cannot walk backwards             3 ideas
L3  Rewiring in place                     5 ideas
L4  Sorting a list                        1 idea
L5  The list inside a design              3 ideas
L6  Circular lists                        1 idea
                                          + 12 cross-listed ↗
```

**16 native entries, plus 12 cross-listed (↗).** See [[README]] on cross-listing.

> [!tip] **The ↗ table is long and four of its rows point at the tree files, which is the point.** Half of what curated lists call "linked list problems" are really *a tree being rewired into a list* (LC 114, 426, 117) or *another technique running over a list* (LC 1171, 1019). Those ideas belong where their mechanism belongs; they are cross-listed here so that arriving from the linked-list side still finds them.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** #16 was added by the reverse sweep and sits inside L3.

## Named problems in this file

| The name you remember | Entry |
|---|---|
| **Reverse a Linked List** | #1 |
| **Dummy head / sentinel node** | #2 |
| Find the middle *(slow and fast)* | #3 |
| **Palindrome Linked List** · Reorder List | #4 |
| Add Two Numbers II *(reverse order)* | #5 |
| Convert Sorted List to BST | #6 |
| **Reverse Nodes in k-Group** · Reverse Between | #7 |
| Partition List · Odd Even Linked List | #8 |
| **Copy List with Random Pointer** | #9 |
| Flatten a Multilevel Doubly Linked List | #10 |
| **Merge sort on a list** *(Sort List)* | #11 |
| **LRU Cache** | #12 |
| **LFU Cache** | #13 |
| **Skip list** | #14 |
| Insert into a Sorted Circular Linked List | #15 |
| **Delete Node in a Linked List** *(no head given)* | #16 |

---

## L1 · The primitives

Everything below is built from these three. Get them wrong and nothing else works.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Reverse Linked List | LC **206** | **Three pointers, and the rule is: save `next` before you overwrite it.** `prev`, `cur`, and a temporary — because the instant you set `cur.next = prev` you have destroyed your only route to the rest of the list. That sentence is the entire discipline this topic tests, and it recurs in every rewiring entry below. Learn both forms and know why: the iterative version is `O(1)` space and is what you write; the recursive version reverses on the way *back up* (`head.next.next = head`, then `head.next = null`) and is worth holding because it is the same unwind that #5 exploits. Also note what you return — **the new head**, not the old one, which is the most common silent bug in the file. |
| 2 | Remove Linked List Elements · Add Two Numbers | LC **203** · **2** | **A dummy node in front of the head deletes the head special case entirely.** Every insert-or-delete algorithm has two versions — one that handles "this is the first node" separately, and one that does not because a sentinel guarantees every real node has a predecessor. The second is shorter, and it is shorter *for a reason worth stating*: the head is only special because it has no `prev`, so manufacturing a `prev` makes it ordinary. You return `dummy.next`. In C the same problem is solved with a pointer-to-pointer, which is the same insight one level down. **Reach for this before writing any linked-list mutation**, and half the edge cases stop existing. |
| 3 | Middle of the Linked List | LC **876** | **A `2×` pointer arrives at the end exactly when a `1×` pointer arrives at the middle, so one pass finds the midpoint without knowing the length.** New beyond ↗ [[Two Pointers]] #9's fixed-offset idea because the offset here *grows*, and the payoff is that you can split a list you have not measured. The detail that actually matters and that nobody states: **for an even-length list, the loop condition decides which middle you get.** `while (fast && fast.next)` lands on the second middle; `while (fast.next && fast.next.next)` leaves you on the node *before* it, which is what #11 needs to cut the list in two. Choosing the wrong one gives an infinite recursion in merge sort, so decide deliberately rather than by memory. |

## L2 · You cannot walk backwards

| # | Problem | Source | The new idea |
|---|---|---|---|
| 4 | Palindrome Linked List · Reorder List | LC **234** · **143** | **Split at the middle, reverse the second half, then walk both halves forward together.** The composition is the idea: #3 finds the cut, #1 flips the tail, and now two forward walks simulate one forward and one backward walk — which is how you get `O(1)` space on a problem that looks like it needs a stack or an array copy. LC 234 then *compares* the halves and LC 143 *interleaves* them, but the machinery is identical, which is why they are one entry. Two things interviewers ask about: the odd-length middle node needs no special handling if you stop when either walk ends, and **you should offer to restore the list**, since you have mutated the caller's data. |
| 5 | Add Two Numbers II | LC **445** | **There are exactly three ways to traverse a singly linked list backwards, and choosing between them is the question.** Digits stored most-significant-first must be added least-significant-first, so you either **reverse both lists** (`O(1)` space, mutates the input, then reverse the answer), push onto **two explicit stacks** (`O(n)` space, no mutation, clearest to write), or **recurse and add on the way back up** (`O(n)` stack, elegant, and it blows up on a million nodes). New because the entry is a *decision*, not a technique — and the tradeoff table is what you say out loud: mutation versus memory versus stack depth. Once you have it, "process this list in reverse" is never an obstacle again. |
| 6 | Convert Sorted List to BST | LC **109** | **Build the tree in inorder while a single cursor consumes the list, and the values land in the right place without ever being indexed.** The array version picks the middle as the root ([[Binary Search Trees]] #10); on a list you cannot index a middle cheaply, so instead you compute the *size*, recurse to build the left subtree first, then take whatever node the cursor is now on as the root, then build the right subtree. `O(n)` total, no array copy. New and genuinely surprising: **the recursion's traversal order is doing the indexing for you**, so a structure that needs random access is built by a structure that has none. The reflex to keep is broader than lists — *when you cannot index, see whether a traversal visits things in the order you need anyway.* |

## L3 · Rewiring in place

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Reverse Nodes in k-Group · Reverse Between | LC **25** · **92** | **To reverse a sublist you need handles on the node *before* it and the node *after* it, and you must capture both before you start destroying links.** So the bookkeeping is `groupPrev` and `groupNext`, then reverse the interior with #1, then stitch: `groupPrev.next` to the group's new head, and the group's new tail to `groupNext`. New because #1 assumed the sublist was the whole list, and the stitching is where every bug lives. Two details that are the actual content: **check that `k` nodes remain before reversing anything** (a partial final group must be left alone), and use a dummy (#2) so the first group's `groupPrev` exists. This is the hardest pure-pointer problem that gets asked, and it is asked a lot. |
| 8 | Partition List · Odd Even Linked List | LC **86** · **328** | **Build two independent chains with their own tail pointers, then concatenate — one pass, stable, `O(1)` space.** Rather than moving nodes around inside one list, you *unbuild* it into two and rebuild once. New because it inverts the usual approach: no node is ever inserted into the middle of anything, so there is no shifting and no lost-pointer hazard, and stability is free because each chain preserves arrival order. The step people forget is **terminating the second chain with `null`**, without which you get a cycle. Generalises straight to `k` chains, which is bucket-sorting a list, and to LC 328's odd/even split by position rather than by value. |
| 9 | Copy List with Random Pointer | LC **138** | **Weave the copies into the original list so each node can find its own copy in `O(1)`, then unweave.** The obvious solution maps old node to new node in a hash map and does a second pass to fix the random pointers, which is `O(n)` space. The trick is to interleave — `A → A' → B → B' → …` — because then `node.random.next` *is* the copy of `node.random`, so the random pointers are set with pure pointer arithmetic and no lookup table at all. Then separate the two lists in a third pass. New in general form: **when you need a mapping from old to new, storing it inside the structure can be cheaper than storing it beside the structure.** The same instinct as the in-place marking tricks in [[Arrays]] A1. The graph form — hash map as visited payload, no weave possible — is [[Graphs]] #37. |
| 10 | Flatten a Multilevel Doubly Linked List | LC **430** | **Splice a child list into the middle of its parent, which means saving the parent's continuation before you lose it and repairing `prev` on both seams.** Walk forward; on hitting a node with a child, push `node.next` (the continuation) somewhere safe, attach the child, and clear the child pointer — then when the spliced-in run ends, reattach what you saved. Either an explicit stack or a recursive flatten that returns the sublist's tail. New because it is the only entry where the list is being *restructured across levels*, and because a doubly linked list doubles the number of links you must fix — the `prev` you forget is the one nobody tests until they do. |
| 16 | Delete Node in a Linked List | LC **237** | **You cannot delete a node without its predecessor — so instead of removing this node, become the next one and remove that.** Given only a pointer to the node, copy `next`'s value into it and splice `next` out; the node object survives but the *list* is correct. New because it is the sharpest statement of what a node pointer actually gives you: **a handle on a node grants no access to the link pointing at it**, which is the deprivation the dummy head (#2) works around from the other side. Worth knowing its limits, because that is the follow-up — it fails on the tail (there is nothing to impersonate) and it is illegal if any external reference to the node's identity or value matters, which is why real APIs pass the predecessor or use a doubly linked list. |

## L4 · Sorting a list

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Sort List | LC **148** | **Merge sort is the natural sort for a linked list, and it is the one place where merge sort's usual `O(n)` space penalty disappears.** Merging two lists is pure relinking ([[Two Pointers]] ↗ LC 21), so no auxiliary array is needed — only the recursion's `O(log n)` stack, and a bottom-up version removes even that. The entry is really the comparison: **quicksort is bad here** because it needs random access to pick a pivot and to partition efficiently, while merge sort only ever walks forwards, which is exactly what a list offers. Split with #3, being careful to use the *before-the-middle* loop condition so a two-node list actually splits and the recursion terminates. This is the cleanest example in the basis of an algorithm's cost depending on the container, not the data. |

## L5 · The list inside a design

> [!tip] **This is where linked lists earn their existence.** All three entries exist because a doubly linked list is the only structure giving `O(1)` removal from the middle *while maintaining an order*. An array has the order but `O(n)` removal; a hash map has `O(1)` removal but no order. Combining the two is the whole idea, and it is the most commonly asked design question there is.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 12 | LRU Cache | LC **146** | **A hash map for lookup plus a doubly linked list for recency, where the map's *value* is the list node itself.** That last detail is the entry: because the map hands you the node directly, you can unlink it in `O(1)` without walking, which is the only reason all three operations are constant time. Recency lives in the list order — touch a key and move its node to the front, evict from the back. Use **sentinel head and tail nodes** (#2 again) so unlinking never checks for `null`. Being able to say *why not an array* (`O(n)` to move an element) and *why not a heap* (`O(log n)`, and timestamps go stale) is what separates a memorised answer from an understood one. |
| 13 | LFU Cache | LC **460** | **One doubly linked list *per frequency*, plus a pointer to the current minimum frequency.** Promoting a key means unlinking it from its bucket and appending it to the `freq + 1` bucket, both `O(1)`; eviction takes the back of the `minFreq` bucket. New beyond #12 because the ordering key is now a *count* rather than a timestamp, so a single list cannot express it — and the piece that looks impossible until you see it is maintaining `minFreq`, which only ever **increases by one on a promotion** or resets to 1 on an insertion, so it never needs to be searched for. The general shape is worth naming: **bucket by a bounded derived key and keep order inside each bucket**, which is [[Arrays]] #14's idea with an ordered payload. |
| 14 | Design Skiplist | LC **1206** | **Add express lanes above the list — each level skips roughly twice as far — and search becomes `O(log n)` on a linked structure.** A node is promoted to the next level with probability ½, so the expected height is logarithmic and the expected search cost is too; searching descends from the top level, moving right while the next value is smaller, then dropping a level. New because it is the answer to "you cannot binary search a list" that does not involve giving up and copying to an array — **you buy random access by adding redundant links rather than by changing container.** Simpler to implement than a balanced BST and with the same expected complexities, which is why Redis and LevelDB use one. Worth holding as the probabilistic counterpart to [[Binary Search Trees]] #11. |

## L6 · Circular lists

| # | Problem | Source | The new idea |
|---|---|---|---|
| 15 | Insert into a Sorted Circular Linked List | LC **708** | **A sorted circular list has exactly one place where the order "breaks", and that wrap point is where the extremes belong.** Walking with `prev` and `cur`, you insert when `prev.val ≤ x ≤ cur.val` — or, if you have reached the wrap (`prev.val > cur.val`), when `x` is larger than the maximum or smaller than the minimum. New because termination is no longer "reach `null`": you must stop after one full lap, which is the case that matters when **every value is equal** and no insertion point is ever found, and inserting anywhere is then correct. Filed as its own family because the same "detect the wrap, then handle the two tails" reasoning is what rotated-array binary search does ([[Binary Search]] #3) — the structures differ, the argument does not. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Linked List Cycle II | LC **142** | Floyd's tortoise and hare, plus the distance arithmetic that locates the cycle's entry from the meeting point. The reason `O(1)`-space cycle detection exists at all. [[Two Pointers]] #7. |
| ↗ | Remove Nth Node From End | LC **19** | A fixed gap between two pointers converts "from the end" into "from the start" in one pass — the deprivation in this file's first table, solved. [[Two Pointers]] #9. |
| ↗ | Merge Two Sorted Lists | LC **21** | Advance whichever pointer holds the smaller value; total work `m + n`. The engine of #11 and of every "combine two ordered streams" problem. [[Two Pointers]] #14. |
| ↗ | Intersection of Two Linked Lists | LC **160** | Equalise two unknown lengths by switching heads at the end, so both pointers travel `lenA + lenB` and must meet at the junction. [[Two Pointers]] #18. |
| ↗ | Merge k Sorted Lists | LC **23** | A heap of size `k` replaces the pairwise comparison, giving `O(n log k)`. The scaling of #11's merge step. [[Heap]] #7. |
| ↗ | Linked List Random Node | LC **382** | Reservoir sampling — a uniform sample from a sequence whose length you never learn, which is the natural setting for a list. [[Arrays]] #12. |
| ↗ | Add Two Numbers | LC **2** | Carry propagation over digit sequences, with the list supplying least-significant-first order for free. The big-integer machinery is [[Math & Number Theory]] #3; the reverse-order variant is #5 here. |
| ↗ | Flatten Binary Tree to Linked List | LC **114** | **#1's save-before-you-overwrite discipline, on a tree.** You are destroying pointers you still need to read, so either save them or choose a traversal order in which every read precedes its overwrite — which is why the clean solution runs in *reverse* preorder. Exactly the same hazard as reversing a list, and worth reading in both settings because seeing it twice is what makes it a reflex rather than a memorised sequence. [[Binary Trees]] #18. |
| ↗ | BST to Sorted Doubly Linked List · Populating Next Right Pointers II | LC **426** · **117** | The other two faces of the same rewiring hazard. LC 426 threads `prev`/`next` during an inorder walk; LC 117 uses the **already-threaded level above as a linked list** to build the next one, which is how you reach `O(1)` space. Both are "a tree becomes a list in place". [[Binary Search Trees]] #13 and [[Binary Trees]] #18. |
| ↗ | Remove Zero Sum Consecutive Nodes | LC **1171** | **A prefix-sum hash map, but keyed to *nodes* rather than indices.** Store `runningSum → node`; seeing a sum twice means the run between them is zero, so you splice by pointing the first node's `next` past the second. Two list-specific consequences: you need a dummy head (#2) because the zero-run may start at the head, and you must **evict the stale map entries** for the nodes you just removed, which the array version never has to think about. The prefix machinery is [[Prefix Sums & Difference Arrays]] #5. |
| ↗ | Next Greater Node in Linked List | LC **1019** | A monotonic stack over a structure you can only walk forwards — which is the one thing a list is actually good at, so the technique transfers unchanged and only the *output indexing* gets awkward. Worth doing once, to confirm that "no random access" costs nothing here. [[Stack and Queue]] S7. |
| ↗ | Clone Graph | LC **133** | The same old-to-new mapping as #9, on a graph — visited carries the copy, not a boolean. No weave possible, so the hash map *is* the answer. Native at [[Graphs]] #37. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Reverse a doubly linked list | *classic* | #1 | Swap both links per node; the discipline is unchanged. |
| Remove Duplicates from Sorted List | LC **83** | #2 | Compare with `next` and skip. |
| Remove Duplicates from Sorted List II | LC **82** | #2 · #8 | Needs a dummy and a `prev`, which is exactly why #2 exists. |
| Delete the Middle Node | LC **2095** | #3 | The before-the-middle loop condition, then unlink. |
| Swap Nodes in Pairs | LC **24** | #7 | `k = 2`, with the same stitching. |
| Rotate List | LC **61** | #7 · #8 | Find the length, close the loop, cut at `n − k % n`. |
| Split Linked List in Parts | LC **725** | #8 | Length, then repeated cuts with the remainder distributed. |
| Reverse Even Length Groups | LC **2074** | #7 | Group reversal with a length-dependent condition. |
| Merge In Between Linked Lists | LC **1669** | #7 | Splice: capture both seams, relink. |
| Insertion Sort List | LC **147** | #11 | `O(n²)`, and the reason merge sort is the answer. |
| Sort a nearly-sorted list | — | #11 | A heap of size `k`. [[Heap]] #24. |
| Flatten a nested list *(iterator)* | LC **341** | #10 | An explicit stack over nesting. [[Stack and Queue]] #19. |
| Copy List with Random Pointer *(hash map)* | LC **138** | #9 | The `O(n)`-space route, named in the entry. |
| Design Linked List | LC **707** | #2 | An API wrapper; sentinels make it short. |
| Implement a stack / queue with a list | — | #2 | Head insertion, or head and tail pointers. |
| LRU with `list` + `unordered_map` | — | #12 | There is no library LRU map in C++. Write the two structures; say the name, then implement it anyway. |
| Design Browser History | LC **1472** | #12 | A list with a cursor, or two stacks. Truncation of the forward branch is the idea — [[Design]] #9. |
| All O(1) Data Structure | LC **432** | #13 | Buckets of keys in a doubly linked list of counts. Same `minFreq` as #13; Design ↗ it with LFU. |
| Josephus / circular elimination | LC **1823** | #15 | The `O(n)` recurrence beats simulating a circular list. [[Math & Number Theory]] #20. |
| XOR linked list · unrolled linked list | *concept* | — | Memory-layout curiosities. Worth naming in a systems conversation, not an algorithmic idea. |
| Convert Sorted Array to BST | LC **108** | #6 | The indexable counterpart, where the middle is picked directly. [[Binary Search Trees]] #10. |
| Maximum Twin Sum of a Linked List | LC **2130** | #4 | Split, reverse, walk together — and sum instead of comparing. |
| Swapping Nodes in a Linked List | LC **1721** | #4 | Two fixed-offset walks, then swap values. Swapping *nodes* is #7. |
| Linked List Components | LC **817** | — | One pass with a set membership check; nothing about lists. |
| Odd Even / Reverse alternating groups | LC **1474** · **2074** | #7 · #8 | Positional grouping with the same stitching. |
| Merge Nodes in Between Zeros | LC **2181** | #8 | One pass building a single chain with a tail pointer. |

---

## Self-audit

**Borderline calls, and which way I went**

- **#3 native, despite [[Two Pointers]] #9 already mentioning the midpoint.** Cross-listing rather than deferring, because that entry gives it one clause and this file needs the part it omits — that the loop condition chooses *which* middle you land on, and that #11 breaks if you pick wrong. This is the intended use of cross-listing: same problem, different load-bearing detail.
- **#6 native, despite being an exclusion row under [[Binary Search Trees]] #10** which names "inorder simulation" in passing. Pulled in and developed here for the same reason, and because the transferable lesson — *when you cannot index, check whether a traversal already visits things in the order you need* — is a linked-list lesson wearing a tree costume. Flagged in the [[README]] pattern: an idea named in one clause and developed nowhere is effectively absent.
- **#4 merges LC 234 and LC 143**, which I went back and forth on. One compares the halves and one interleaves them, so the *output* differs; the split-reverse-walk machinery is identical and that is what you learn. Merged, with both cited, on the "variations teach you edge cases not ideas" rule.
- **#5 is a decision entry, not a technique.** Three ways to walk backwards, with a tradeoff to recite. Kept in that form because the three are genuinely interchangeable and the interview question *is* which you pick and why — a single "reverse the list" entry would have taught one third of it.
- **L5 given three entries in a file about pointers.** Defensible only because of the callout: the `O(1)`-middle-removal property is the sole thing a linked list is uniquely good for, so a file that treated LRU as a Design problem would omit the topic's only real justification. #13 in particular nearly went to Design, and stayed because maintaining `minFreq` without searching is the non-obvious part and it is pure list reasoning.
- **#14 (skip list) kept as a full entry** where [[Sorted Containers & Order Statistics]] dismisses it as "worth naming, not worth learning separately". Both are right in their own file — there it is one of several interchangeable implementations of an ordered set; here it is the *only* answer to "you cannot binary search a list" that does not abandon the container.
- **#15 is the entry I am least sure earns its place.** Circular-list insertion is mostly edge-case handling, which is normally the definition of a variation. Kept because circular lists otherwise appear nowhere in the basis, and because the wrap-point argument is genuinely shared with rotated-array binary search.

**Naming check.** Four retitles. #2 was drafted as "Remove Linked List Elements" and is now *the dummy head*, since the idiom rather than the problem is the point. #5 was "Add Two Numbers II" and is now *three ways to traverse backwards*. #9 was "Copy List with Random Pointer" and is now *store the old-to-new mapping inside the structure*, which is what transfers. **#16 was the important one:** drafted as "Delete Node in a Linked List", which makes it sound trivial — retitled to *a handle on a node grants no access to the link pointing at it*, and only in that framing is it visibly the mirror of #2.

**Step 4B — reverse sweep**

Thirty-five plain-language descriptions navigated against the family headings. **One failure:**

- **"You are handed a node, not the head, and told to delete it"** landed nowhere. Every family assumed you hold the head and can therefore reach any node's predecessor, and the axis this exposed was **what you are given** — which my draft listed as "the head" and nothing else. That is **#16**, and it is worth more than its difficulty suggests, because it is the exact complement of #2: the dummy head manufactures a predecessor where none exists, and this entry is what you do when you cannot. The pair together is what "linked list" actually means.

Four collisions, all checked and cleared. "Find the middle" reaches #3 and ↗ LC 19, correctly separated by whether the offset is fixed or growing. "Reverse part of a list" reaches #1 and #7, which is the intended progression. "Build a balanced BST from sorted input" reaches #6 and the ↗ LC 108 row, the indexable-versus-not pair the entry is about. "Cache with eviction" reaches #12 and #13, correctly, since the ordering key is what distinguishes them.

**Step 4C — inward sweep**

Added after this file exposed the gap 4B cannot see. **4B probes descriptions against *my* classification; it never asks whether the other files already hold entries that belong here.** Cross-listing had only ever been applied *outward* — noting, while writing a file, what should be developed elsewhere — so with seventeen files already written there were seventeen to sweep for list-shaped content, and I had swept none. Grepping them produced four ↗ rows this file was missing:

- **LC 114, 426, 117** were all correctly filed as tree-rewiring ([[Binary Trees]] #18, [[Binary Search Trees]] #13), and #18 is *the same save-before-overwrite hazard as #1 here*, stated in tree vocabulary. Neither file linked to the other, so someone learning the discipline on lists would meet it again on trees without recognising it.
- **LC 1171** and **LC 1019** were in **no file at all** — a prefix-sum hash map and a monotonic stack, each running over a list. Both fell between two stools: the technique files did not think of the list variant, and this file was built from the pointer-manipulation side and never looked outward.

The procedural fix, which applies retroactively to every file: **after 4B, grep the other files for the new topic's vocabulary and check what should be cross-listed inward.** Cheap, mechanical, and it is the only step that catches an idea sitting correctly in one file while being unreachable from the file a reader would start in.

**What I am uncertain about**

- **The Design boundary is closed.** #12, #13 and #14 stay because the difficulty is the *list reasoning*. [[Design]] ↗ LRU/LFU and natives Browser History (#9) and the pairing sentence; LC 432 stays an exclusion of #13. The cut held.
- **Whether L6 should exist.** One entry, and a borderline one. Intervals did not absorb circular structures. Folding into L3 is still optional, not forced.
- **Recall is thinnest on doubly linked lists.** #10, #12 and #13 use them; nothing systematically covers the `prev`-maintenance failure modes, and there is no curated list of doubly-linked problems to sweep against because most sources treat them as an implementation detail.
- **Nothing here covers memory and cache behaviour** beyond the opening warning. That is a systems topic rather than a DSA one, but it is the honest answer to "when would you actually use this", and a reader who only has this file will not be able to give it.
- **The tail-scope question does not arise**, which is itself mildly suspicious — every other file has at least one entry that is real but rarely asked. Either this topic genuinely has no tail, or I have excluded it as variation. I lean towards the former, since the topic is small and heavily asked at the shallow end.

**Completeness confidence: ~90% on the native ideas, and I no longer trust the figure for reachability.** The native side is strong for a structural reason — the topic is small, the deprivations are enumerable (three of them, in the table up top), and every technique answers one of them. But step 4C found four ↗ rows missing, two of which were ideas in *no* file, and a confidence number computed from my own families could never have seen that. **Every file's figure has the same blind spot until 4C has been run on it.**

## Related Notes

- [[README]]
- [[Two Pointers]]
- [[Heap]]
- [[Binary Search Trees]]
- [[Stack and Queue]]
- [[Arrays]]
- [[Binary Search]]
- [[Math & Number Theory]]
- [[Sorted Containers & Order Statistics]]
- [[Sorting & Custom Comparators]]
- [[Design]]
- [[Hashing]]
