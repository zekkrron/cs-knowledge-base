---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-24
---
# Concept Basis — Tries

> [!abstract] Minimal spanning set for tries and prefix structures. One entry per **new idea you have to learn**. Variations live in the exclusions table with the entry each collapses into. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!warning] **Scope note.** The interview-relevant core is **#1–#7, #11, #13, #14 and #17** — ten entries, and #1, #3, #7 and #11 alone cover most of what gets asked in Indian SDE loops. Entries tagged **[tail]** are CP, systems, or Google-tier: worth reading once so you can name the technique, not worth grinding.

## The one thing a trie does

> [!tip] **A hash set answers "is this exact key present". A trie answers every question about *prefixes* of the key — and it does so in `O(length)`, independent of how many keys you stored.**
>
> That is the entire value proposition, and the mechanism behind it is worth stating once: **the path from the root spells the key, so the node itself stores nothing about the key.** Shared prefixes are literally the same path, which is why prefix work is free.
>
> The corollary is the question to ask yourself before reaching for one: *does my query care about a prefix?* If the answer is no, a hash map is smaller, faster and shorter to write. Reaching for a trie on an exact-membership problem is the most common way to over-engineer an interview answer.

## Mechanism axes

| Axis | Values |
|---|---|
| **What indexes a child** | a character from a fixed alphabet · a character in a map (sparse or Unicode) · **a single bit** · a whole token · a compressed multi-character edge label |
| **What the key is** | a string · a **reversed** string · a derived or concatenated key · the bit-string of an integer · every **suffix** of one string · a growing stream |
| **What a node stores** | a terminal flag · a count of keys passing through · a count of keys ending here · a value or aggregate · the top-`k` completions · a subtree extremum for pruning |
| **What the query asks** | exact membership · prefix existence · count by prefix · **longest prefix of the query present in the set** · all completions · an extremal partner · a count over a range of partners · a fuzzy match |
| **How the query descends** | one deterministic path · **branching** into several children · **greedy by a maximisation rule** · driven by an external structure · carrying a DP state |
| **Mutability** | build once then query · interleaved insert and query · delete (needs counts, not flags) · **offline activation window** · persistent and versioned |
| **Space discipline** | array of `Σ` pointers per node · hash map per node · path compression · suffix sharing (DAWG) |
| **What breaks** | `Σ ×` node count blows memory · a terminal flag cannot support deletion · keys sharing no prefix degenerate to `n` disjoint chains · inserting all suffixes is `O(n²)` |

## Shape of this topic

```
T1  The structure and its operations     3 ideas
T2  What the node carries                3 ideas
T3  Binary tries over bit-strings        4 ideas
T4  The trie as a search driver          2 ideas
T5  Changing what the key is             3 ideas
T6  Space and representation             2 ideas
T7  Automaton with failure links         1 idea
                                         + 4 cross-listed ↗
```

**18 native entries, plus 4 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Entries are grouped by family, so a later-added entry keeps its high number inside the family it belongs to.

---

## T1 · The structure and its operations

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Implement Trie (Prefix Tree) | LC **208** | **The path is the key.** A node is `children[Σ]` plus `isEnd`, and insert, `search` and `startsWith` are the same descent with different terminal conditions — which is the fact worth holding, because it means `startsWith` costs nothing extra over `search`. Write this once from memory; it is the base every entry below deforms, and it is asked directly. |
| 2 | Implement Trie II | LC **1804** | **A terminal flag cannot support deletion — you need counts.** Store `endCount` (keys ending here) and `passCount` (keys passing through), and every operation becomes arithmetic on a path: `countWordsStartingWith` reads one node's `passCount`, and `erase` decrements along the path, freeing a node exactly when its `passCount` hits zero. New because it changes the node's contract from *boolean* to *counter*, which is what makes the trie a **multiset** rather than a set. Also the entry that quietly answers "shortest unique prefix" — descend until `passCount == 1`. |
| 3 | Design Add and Search Words Data Structure | LC **211** | **A wildcard turns one descent into a branching search.** With `.` matching any character, the query is no longer a path but a DFS over the trie, recursing into every child at each wildcard position. This is the first entry where the *query* has structure rather than just the data, and it is the gateway to T4 — once you can fork a descent, the trie stops being a lookup table and becomes a pruning device. |

## T2 · What the node carries

Three different things to hang on a node, and three different query shapes that result.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 4 | Map Sum Pairs | LC **677** | **Store an aggregate at every node, updated along the insertion path.** Each node holds the sum of values of all keys passing through it, so a prefix-sum query is a single descent and one read. Exactly the augmentation logic that makes a segment tree work ([[Segment Trees]] #1), applied to a prefix tree — and the general statement is worth keeping: *any associative aggregate over "all keys with this prefix" can be maintained in `O(length)` per insert.* Note the trap: overwriting an existing key needs a delta, not an add. |
| 5 | Replace Words | LC **648** | **Longest (or shortest) prefix match — the query is a string, and you want the best key that prefixes it.** Descend the query and remember the last terminal node you passed. Structurally different from #1: membership asks "is the query in the set", this asks "which member is a prefix of the query", and that inversion is what IP routing tables, URL routers, dictionary stemming and tokenisers all do. On a binary trie it is longest-prefix-match on an address, which is #16's actual industrial use. |
| 6 | Search Suggestions System | LC **1268** | **The answer is a whole subtree, not a node.** Autocomplete descends to the prefix node and then *enumerates* below it — either by DFS at query time, or by precomputing the top-`k` completions at every node, which trades `O(k)` memory per node for `O(k)` query time. New query shape: #4 read a scalar off one node, this one collects a list from a region. The precompute-vs-traverse tradeoff is the interview content, and it is also the honest answer to "how does a search bar actually work". |

## T3 · Binary tries over bit-strings

The most under-appreciated part of the topic. **Drop the alphabet to `{0, 1}` and insert integers as fixed-length bit strings, most significant bit first.** The trie becomes a structure for questions about *XOR and bitwise order*, which nothing else answers well.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Maximum XOR of Two Numbers in an Array | LC **421** | **Greedy descent by a maximisation rule.** Insert every number as 32 bits, then for each number walk down preferring the *opposite* bit at each level, since setting a higher bit always beats anything below it. New on two counts: the key is not a string, and the descent is not a match but an optimisation — you take the best available branch and fall back only when it is absent. If you learn one thing from this file beyond #1, learn this; "maximise XOR" is otherwise a hopeless `O(n²)`. |
| 8 | Maximum XOR With an Element From Array | LC **1707** | **Restrict which keys are eligible, without rebuilding the trie.** The partner must satisfy `nums[j] ≤ m`, and there are two clean answers, both generally useful. **Offline:** sort the queries by `m` and the array by value, then insert incrementally so the trie only ever contains eligible keys — an *activation window*. **Online:** store `min` at each node and refuse to descend into a subtree whose minimum exceeds `m`. The second is the reusable one: **a subtree extremum stored at each node turns a constraint into a pruning rule**, which works for any monotone filter, not just XOR. |
| 9 | Count Pairs With XOR in a Range | LC **1803** | **Count an entire subtree instead of descending into it.** To count partners with `x ⊕ y < k`, follow `k`'s bits: at each level one child's whole subtree is *certainly* below the bound, so you add its `passCount` and stop, and you continue into the other. `O(32)` per query rather than `O(n)`. This is the same decomposition a segment tree uses — express the query as `O(log)` complete subtrees — and it converts the trie from an optimiser (#7) into a counter. |
| 10 | Persistent binary trie — max XOR over a subarray | *classic — CF/CSES flavour; the range-restricted form of #7* **[tail]** | **Version the trie so a query can run against a range of insertions.** Keep a root per prefix of the array, each sharing all untouched nodes with its predecessor, and a query on `[l, r]` descends `root[r]` and `root[l-1]` together, using the difference of their `passCount`s to decide whether a branch exists *within the window*. New because the set you query is no longer "everything inserted" but an arbitrary contiguous slice of insertion history. Exactly the persistence idea from [[Segment Trees]] #13, and the reason it works here is the same: a trie is a tree of shared paths, so copying one path is `O(32)`. |

## T4 · The trie as a search driver

Here the trie stops being the thing you query and becomes the thing that **prunes someone else's search**. The control inversion is the idea.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Word Search II | LC **212** | **Walk the external structure and the trie in lockstep, and let the trie kill branches.** Instead of searching the board once per word, you search the board *once* carrying the whole dictionary: at each step the current cell must correspond to a child of the current trie node, and if it does not, that entire branch of the board DFS dies immediately. This is the general pattern of **matching many patterns at once**, and it is a genuine algorithmic win, not a constant factor — the same reframing scaled to a linear text gives you #18. Also the most commonly asked hard trie problem there is. |
| 12 | Fuzzy search within `k` edits (spell checker) | *classic — trie + the [[Dynamic Programming]] edit-distance row; no clean judge problem* **[tail]** | **Carry a DP row down the descent and prune on its minimum.** Edit-distance search is not #3: a wildcard consumes exactly one character, while an edit can insert or delete, so the query and the path can drift out of alignment. The fix is to compute one row of the Levenshtein table per trie node as you descend, and abandon a subtree the moment the row's minimum exceeds `k` — because the row can only grow downward. New idea in general form: **descend a trie carrying accumulated state, and prune on a bound that is monotone in depth.** This is what spell checkers and fuzzy autocomplete actually run, and the pruning argument is the part worth being able to state. |

## T5 · Changing what the key is

Three entries where the trie is untouched and the **key** is transformed. The cheapest kind of cleverness there is, and the family most people never think of.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 13 | Prefix and Suffix Search | LC **745** | **Index one string under many derived keys, so a two-sided query becomes a one-sided lookup.** Insert every `suffix + '#' + word` for each word; then the query `(prefix, suffix)` becomes a single lookup of `suffix + '#' + prefix`. Nothing about the trie changed — the *key* absorbed the second constraint. Generalises far beyond this problem: whenever a structure supports one kind of query and you need a conjunction, ask whether the extra condition can be folded into the key. |
| 14 | Stream of Characters | LC **1032** | **Insert the words reversed, because a stream can only grow at the end.** Characters arrive one at a time and you must answer "does any dictionary word end here", which is a *suffix* query on what you have seen. A trie cannot do suffixes — so reverse the dictionary on the way in, and walk the received characters backwards on the way out, turning it back into a prefix query. The transferable move: **when the query is on the wrong end, reverse the data.** |
| 15 | Number of Distinct Substrings in a String | LC **1698** | **Insert every suffix and the trie contains every substring — one node per distinct one.** The bridge from strings to substrings, and the reason the suffix-structure family exists at all. It also comes with the boundary you must know: this is `O(n²)` nodes, fine for `n ≈ 1000` and hopeless beyond, at which point the real tools are a **suffix automaton, suffix array or suffix tree** (Strings basis). Knowing that a suffix trie is the naive form of a named family is worth more than the problem. |

## T6 · Space and representation

The trie's real cost is memory, and both entries are about paying less.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 16 | Radix tree / PATRICIA — path compression | *concept — IP routing tables, `git` object store, Ethereum's Merkle-Patricia trie* **[tail]** | **Collapse every single-child chain into one edge with a multi-character label.** In a trie over long, sparse keys most nodes have exactly one child and exist only to hold a character, so merging them makes the node count `O(number of keys)` instead of `O(total length)`. Two things follow: the structure survives keys that share almost no prefix, which is the degenerate case that ruins a plain trie, and edge comparison becomes a string compare rather than a character step. The industrial trie is almost always this one, doing #5's longest-prefix-match. |
| 17 | Choosing the child representation — array, map, or DAWG | *concept — the follow-up to LC 208 that actually gets asked* | **`Σ` pointers per node is the trie's dominant cost, and there are three answers.** A fixed array of 26 is fastest and wastes most of itself; a hash map per node is compact for sparse or Unicode alphabets and costs you a hash per step; and a **DAWG** (minimised automaton) additionally merges identical *suffixes*, so shared tails are stored once as well as shared heads — turning the tree into a DAG and shrinking a real dictionary by an order of magnitude. Worth an entry because "how much memory does your trie use, and what would you change" is a standard follow-up, and because the DAWG idea — shared suffixes, not just shared prefixes — is the one thing a trie does *not* do by default. |

## T7 · Automaton with failure links

| # | Problem | Source | The new idea |
|---|---|---|---|
| 18 | Aho–Corasick | *classic — the scaled form of #11; usable on LC 1032 and LC 616* **[tail]** | **Add a failure link to every trie node and the trie becomes a finite automaton over the text.** When the next character has no child, follow the failure link — the longest proper suffix of the current match that is also a prefix in the trie — instead of restarting. One pass finds *all* occurrences of *all* patterns in `O(text + total pattern length + matches)`. This is KMP's failure function generalised from one pattern to a set, which is exactly the right way to hold it: **KMP is Aho–Corasick on a trie with one branch.** #11 pruned a branching search; this eliminates backtracking entirely on a linear one. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Word Break II | LC **140** | The trie replaces the "is `s[i..j]` a word" check inside a DP, so transitions are discovered by *descending from `i`* rather than tested one by one — dropping a factor of the word length. The clean example of a trie accelerating someone else's recurrence. [[Dynamic Programming]] basis. |
| ↗ | Concatenated Words | LC **472** | Sort by length, then for each word run a DP over the trie of the shorter words already inserted. Composes ↗ LC 140 with an insertion order that guarantees the pieces exist. [[Dynamic Programming]] basis. |
| ↗ | Design In-Memory File System | LC **588** | The same map-of-children structure with path components as the alphabet — a trie over tokens rather than characters. [[N-ary Trees]] #11. |
| ↗ | KMP · Z-function · string hashing | *classic* | The **non-trie** route to substring and single-pattern problems, and usually the better one. Reach for a trie when you have a *set* of patterns or a prefix query; reach for these when you have one pattern or need substring equality. Strings basis. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Longest Common Prefix | LC **14** | #1 | Descend while exactly one child and not terminal. Vertical scanning is simpler and the trie is over-engineering — worth doing once to feel the difference. |
| Shortest Unique Prefix | *classic* | #2 | Descend until `passCount == 1`. |
| Index Pairs of a String | LC **1065** | #1 | Descend from every start index. |
| Longest Word in Dictionary | LC **720** | #1 | Build the trie, then DFS only through terminal nodes. |
| Camelcase Matching · Word Abbreviation | LC **1023** · **527** | #3 | Branching descent with a different acceptance rule. |
| Word Squares | LC **425** | #6 | Backtracking, using prefix-completion lookup at each step. |
| Palindrome Pairs | LC **336** | #14 | Reversed insertion plus a two-case split on which word is longer. The case analysis is the difficulty; the trie idea is #14's. |
| Maximum XOR After Operations · XOR queries | — | #7 | Greedy descent with a different objective. |
| Count Pairs With XOR equal to `k` | — | #9 | A hash map is strictly better; no trie needed. |
| Maximum Genetic Difference Query | LC **1938** | #8 · #10 | Offline DFS with insert-and-undo on the trie — an activation window along a tree path rather than along a sorted order. |
| Design Search Autocomplete System | LC **642** | #6 | Top-`k` at each node, keyed by frequency. |
| Replace Words with the longest root | — | #5 | The other terminal condition on the same descent. |
| Prefix-based rate limiter · URL router | *systems* | #5 | Longest-prefix-match on tokens. |
| Bitwise ORs of Subarrays | LC **898** | — | Not a trie. Set-of-reachable-values, which is a [[Dynamic Programming]] idea. |
| Group Anagrams · Isomorphic Strings | LC **49** · **205** | — | Canonical-key hashing. No prefix question, so no trie. |
| Suffix array construction | *classic* | #15 | The scalable replacement, and it belongs to the Strings basis. |
| Compressed trie for a spell-check dictionary | — | #16 · #17 | The two space entries applied together. |

---

## Self-audit

**Borderline calls, and which way I went**

- **T3 given four entries, which is more than most sources give the whole topic.** Deliberate. Binary tries are the part of this material that transfers least obviously from the string case and gets asked at the harder end, and #7, #9 and #10 are genuinely three different things — optimise, count, and restrict-the-set. If any family here is over-split it is this one, but I would defend it.
- **#5 (longest-prefix-match) separated from #1 (membership).** They are the same descent with a different bookkeeping line, and I nearly merged them. Kept apart because the *question is inverted* — is the query in the set, versus which set member prefixes the query — and that inversion is what routers and tokenisers do. Merging would have hidden the entire industrial use of tries.
- **#2 (counts) kept as its own entry rather than folded into #1.** The code is nearly the same; the contract is not. A flag gives you a set, a count gives you a multiset with deletion, and "how would you delete from your trie" is a standard follow-up that a flag-based implementation simply cannot answer.
- **Four concept entries with no clean judge problem (#10, #12, #16, #17), all tagged [tail] except #17.** Consistent with the `MISSING representative` convention used in [[Binary Search Trees]] and [[Segment Trees]]. #17 is untagged because the memory follow-up genuinely does get asked right after LC 208.
- **Aho–Corasick kept native, KMP cross-listed.** Aho–Corasick *is* a trie with extra pointers, so it belongs here; KMP is a string algorithm that happens to be its degenerate case, so it belongs to Strings. Stating the relationship in both directions is the point.
- **Suffix automata and suffix arrays cross-listed to Strings, not native.** #15 holds the boundary — insert all suffixes, `O(n²)`, and here is where you stop — which I think is the right amount for a trie file. A reader whose target includes contest work would want them here.

**Step 4B — reverse sweep**

Thirty-one plain-language descriptions navigated against the family headings. **Two failures, both genuine absences:**

- **"I need the maximum XOR, but only against elements inside a subarray"** landed nowhere. #8 restricts the *set* by a value predicate and #7 uses everything, but nothing handled a query against a contiguous slice of insertion history. That is **#10, persistent trie**, and the axis it exposed — *mutability: persistent and versioned* — was missing from the axis table entirely, even though [[Segment Trees]] has carried a persistence entry since it was written. A missing axis that was already visible one file away.
- **"Find dictionary words within one or two typos"** resolved to #3, incorrectly. Wildcards and edits look interchangeable and are not: a wildcard consumes exactly one character, an edit can insert or delete, so the query and the path desynchronise and a pure branching descent cannot express it. That is **#12**. Worth noting *how* this failed — the sweep did not come back empty, it came back with a wrong answer that looked right, which is the harder failure mode and the reason each description has to be checked against the entry it reaches rather than merely reaching one.

Two collisions, both checked and cleared. "Which stored word is a prefix of my query" reaches #5 and #16 (the operation and its compressed implementation — correctly separated). "Find all patterns in a text" reaches #11 and #18 (branching search on a 2D structure versus a linear automaton — different mechanics, and #11 names #18 as its scaled form).

**What I am uncertain about**

- **Where the boundary with the Strings basis sits.** This is the largest risk in the file. Suffix arrays, suffix automata, KMP and Z all touch trie territory, and I have drawn the line at *does the structure branch on a shared prefix*. Defensible, but a different line would move three or four entries.
- **Whether #12 is in scope.** Fuzzy search is systems-flavoured and has no judge representative, so it may be tail-of-the-tail. Included because the sweep found it and because "descend carrying state, prune on a monotone bound" is a shape that recurs.
- **Ternary search tries** — excluded. A space-time compromise between #17's array and map, genuinely used, but almost never asked. Moderate confidence.
- **Bitwise tries for `AND`/`OR` extremal queries** rather than XOR — excluded, since the greedy argument mostly collapses and other techniques win. Mild uncertainty.
- **Merkle tries** (Patricia-Merkle, `git`, blockchain state) — named in #16 but not developed. That is system-design scope, not DSA.
- **The exclusions table is short relative to the entries**, which is unusual and slightly suspicious. Tries have fewer near-duplicate judge problems than DP or heaps do, so I think it is real rather than a sign of under-collection — but it is the opposite of the [[Binary Search]] pattern and worth a second look if something feels missing.

**Completeness confidence: ~90%.** The core is solid and #1–#7 plus #11 I would call complete. The uncertainty is almost entirely at the Strings boundary and in how much of the automaton and suffix-structure world should have been pulled inside.

## Related Notes

- [[README]]
- [[N-ary Trees]]
- [[Binary Trees]]
- [[Segment Trees]]
- [[Dynamic Programming]]
- [[Sorted Containers & Order Statistics]]
- [[Backtracking]]
- [[Bit Manipulation]]
