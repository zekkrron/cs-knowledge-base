---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-28
---
# Concept Basis — Hashing

> [!abstract] Minimal spanning set for **`O(1)`-average lookup as a primitive** — complement maps, canonical keys, frequency payloads, amortised set walks, splitting a search across two tables, and the C++ realities of `unordered_map`. One entry per **new idea you have to learn**. Polynomial *substring* hashes live in [[Strings]] #5; prefix-as-a-key folds in [[Prefix Sums & Difference Arrays]] P2. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!tip] **A hash map answers "have I seen this exact key, and what did I store with it?" in expected `O(1)`.** Everything in this file is a different *choice of key* or a different *payload*. If the question is "what is nearest" or "how many are smaller", you wanted [[Sorted Containers & Order Statistics]], not this. If the question is "does this window match a frequency vector", you wanted [[Sliding Window]] #2 — the map is incidental.

## The one reflex

> [!tip] **Before you nest a loop, ask whether the inner search is an exact lookup.** If it is, store what you have already seen and query the complement. That is Two Sum, and it is also every "prefix `P` such that `P − k` has occurred" problem wearing a fold.
>
> The second reflex, almost as useful: **if equality should ignore some distinction, hash a form that has already erased it.** Sorted letters, a count vector, an offset from the first character — the key is a modelling decision, not a data structure one.

## Mechanism axes

| Axis | Values |
|---|---|
| **What the key is** | the raw value · a **complement** · a **canonical form** · an equivalence class (`mod k`, a bitmask) · a pair / tuple · a **bucket id** derived from a range |
| **What the value stores** | nothing (a set) · a count · an **index** (first / last) · a list of indices · a nested map |
| **When you insert vs lookup** | lookup-then-insert (one pass) · insert-all-then-query · **count, then a second pass** |
| **How many tables** | one · **two, meeting in the middle** · two, because a bijection needs both directions |
| **What equality means** | exact · after a transform you chose · **neighbourhood** (own bucket and adjacent) |
| **Assumption that breaks** | `unordered_map` is average-case, not worst · a bad custom hash clusters · a bijection needs both directions · consecutive walks overlap without a start guard · a hash map cannot answer an inequality |

## Shape of this topic

```
H1  Complement and membership      2 ideas
H2  Canonical keys                 2 ideas
H3  Frequency as the payload       1 idea
H4  Amortised walk on a set        1 idea
H5  Split the search               1 idea
H6  Neighbourhood via buckets      1 idea
H7  The table itself               2 ideas
                                   + 8 cross-listed ↗
```

**10 native entries, plus 8 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Dual-native with [[Arrays]] A3 on #1, #3, #6 — those three were squatting here before this file existed.

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| **Two Sum** | #1 |
| Contains Duplicate II *(last-seen)* | #2 |
| **Group Anagrams** | #3 |
| **Isomorphic Strings** · Word Pattern | #4 |
| First Unique Character | #5 |
| **Longest Consecutive Sequence** | #6 |
| **4Sum II** | #7 |
| Contains Duplicate III *(bucket)* | #8 |
| **Design HashMap** · chaining · load factor | #9 |
| Hashing a `pair` / custom hash | #10 |

---

## H1 · Complement and membership

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Two Sum | LC **1** | **Store what you have seen, look up the complement, insert after the lookup.** The insert-after is the whole one-pass argument: by the time you ask for `target − a[i]`, the map holds exactly `{a[0]…a[i−1]}`, so every pair is considered once and nothing pairs with itself. Two passes (build the whole map, then query) is the same idea with a worse bug surface — a value can pair with itself. Dual-native with [[Arrays]] #7; the prefix-fold form is [[Prefix Sums & Difference Arrays]] #4. |
| 2 | Contains Duplicate II | LC **219** | **The payload is an index, and the query is "have I seen *this* value within distance `k`."** Not a complement — the key is the value itself. Keep `last[v]`, accept if `i − last[v] ≤ k`, then overwrite. New against #1 because the lookup is identity plus a *distance*, and because overwriting (latest index) is correct here where it is fatal for "longest subarray with prefix `P`" ([[Prefix Sums & Difference Arrays]] #5 never overwrites). Contains Duplicate (LC **217**) is the set with no distance — a one-line exclusion. |

## H2 · Canonical keys

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Group Anagrams | LC **49** | **Hash a form that has already erased the distinction you want to ignore.** Sorted letters, or a 26-length count tuple: two words are anagrams iff the keys agree, so one map from key to list groups them. New because the key is a *modelling* decision. Group Shifted Strings (LC **249**) is the same with "offset from the first character". Dual-native with [[Arrays]] #8; duplicate-subtree identity is [[Binary Trees]] #13. |
| 4 | Isomorphic Strings · Word Pattern | LC **205** · **290** | **A bijection needs both directions.** `a → b` is not enough — two sources mapping to the same target is a collision, so you keep `s→t` *and* a set (or map) of already-used targets. New against #3: anagrams *discard* order; isomorphism *preserves* a 1-1 correspondence. Forgetting the reverse check is the standard wrong answer that still passes half the cases. |

## H3 · Frequency as the payload

| # | Problem | Source | The new idea |
|---|---|---|---|
| 5 | First Unique Character in a String | LC **387** | **Count first, then a second pass *queries* the counts — the map is not the one-pass complement of #1.** First unique, ransom note, valid anagram, "unique number of occurrences": build `freq`, then scan for the predicate. New because the two-pass split is load-bearing — you cannot know a character is unique until the count is finished. A 26-slot array is the same idea when the alphabet is tiny; `unordered_map` when it is not. Top-K-frequent then buckets the counts, which is [[Arrays]] #14, not a second hash idea. |

## H4 · Amortised walk on a set

| # | Problem | Source | The new idea |
|---|---|---|---|
| 6 | Longest Consecutive Sequence | LC **128** | **Only start a walk from a value that has no predecessor, and the total stays `O(n)`.** Dump into a hash set; for each `v`, skip if `v − 1` is present, otherwise walk `v, v+1, …`. Every element is visited by exactly one walk. Dual-native with [[Arrays]] #9. The sorting solution is `O(n log n)` and is the thing this exists to beat. |

## H5 · Split the search

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | 4Sum II | LC **454** | **Hash every pair from the first half, look up the complement pair from the second.** `n = 200` makes `n⁴` hopeless and `n²` fine: store counts of `A[i]+B[j]`, then for each `C[k]+D[l]` add `freq[-(C+D)]`. New against #1 because you *manufacture* the keys as pairwise sums — meet-in-the-middle with a map instead of sorted arrays ([[Backtracking]] #19 is the search form). The same split turns "count tuples with `a+b+c+d = 0`" into two `n²` passes. |

## H6 · Neighbourhood via buckets

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Contains Duplicate III | LC **220** | **Bucket values by width `t+1` so a neighbour can only live in this bucket or an adjacent one.** `id = nums[i] / (t+1)` (floor, and negatives need a push toward `−∞`); on insert, check buckets `id−1, id, id+1` for a value within `t`, and drop indices that have fallen out of the last-`k` window. New because a hash map is answering a *range* question — which the axes say it cannot — by choosing buckets so the range spans a constant number of keys. The ordered-set solution (`lower_bound` in a window) is [[Binary Search Trees]] #15 and is what you write when `t` is large and you do not want the floor/negative-id bug. Know both; the bucket one is the hashing idea. |

## H7 · The table itself

> [!warning] **`std::unordered_map` is not `O(1)`. It is expected `O(1)`, worst-case `O(n)`, and a bad hash or an adversarial set of keys makes the worst case real.** `reserve` when you know `n`. For contests that ban GNU extensions and care about worst case, a `std::map` is the honest fallback.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Design HashMap | LC **706** | **Chaining: an array of buckets, each a list (or a tree once a bucket grows), `hash(key) % B` picks the bucket, and load factor `n/B` is what you keep bounded by rehashing.** Open addressing (linear/quadratic probe, Robin Hood) is the other family — no pointers, clustering is the failure mode. New because everything above *used* a hash table; this is *what it is*. In C++ you write `unordered_map`; in an interview you say chaining, load factor `≈ 0.75`, rehash to `2B`, and why `B` is a prime or a power of two. Design HashSet (LC **705**) is the same with a boolean payload. |
| 10 | Hashing a pair / custom hash | *concept — C++ `std::pair` has no `std::hash`* | **`unordered_map<pair<int,int>, V>` does not compile, and `hash(a) ^ hash(b)` clusters.** You need a combiner — `hash(a) ^ (hash(b) + 0x9e3779b9 + (hash(a) << 6) + (hash(a) >> 2))` (Boost's), or pack two 32-bit ints into a `uint64_t` and hash that. New as a language reality, same justification as [[Sorted Containers & Order Statistics]] #12: being unable to put a tuple in a hash map is a failed design, not a missed algorithm. Custom `Hash` + `Eq` for a struct is the same entry. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Subarray Sum Equals K | LC **560** | The key is a *prefix fold*, not a raw value. Frequency vs earliest-index vs `mod k` vs bitmask of parities are [[Prefix Sums & Difference Arrays]] #4 · #5 · #6 · #8. Inequality instead of equality is #23 there — a hash map is the wrong tool. |
| ↗ | Permutation in String · Min Window | LC **567** · **76** | A frequency vector as a *window summary*, plus a `matched` scalar. [[Sliding Window]] #2. |
| ↗ | Longest Substring Without Repeating | LC **3** | Last-seen index jumping the left edge. [[Sliding Window]] #3; the table is [[Prefix Sums & Difference Arrays]] #15's kind. |
| ↗ | Insert Delete GetRandom O(1) | LC **380** | The map is `value → index` so an array can delete in `O(1)`. The array is the point. [[Arrays]] #13; the class is [[Design]] #1. |
| ↗ | Rolling hash | *classic* | A substring as a number. Collision is the failure mode. [[Strings]] #5. Packed tiny alphabet is [[Bit Manipulation]] #17. |
| ↗ | Copy List / Clone Graph | LC **138** · **133** | Old-to-new as the visited payload. [[Linked List]] #9, [[Graphs]] #37. |
| ↗ | Top K Frequent | LC **347** | Count with a map, then *bucket by the count*. The second half is [[Arrays]] #14. |
| ↗ | `map<T,int>` as a counted set | *concept* | When you also need order, the hash map is the wrong counted set. [[Sorted Containers & Order Statistics]] #13. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Contains Duplicate | LC **217** | #1 · #2 | A set, no distance. |
| Two Sum III · Unique Word Abbreviation | LC **170** · **288** | #1 | The map behind an API. |
| 4Sum *(hash)* | LC **18** | #1 · #7 | Nested Two Sum, or the split of #7 on two arrays. Two pointers after sorting is [[Two Pointers]] #2. |
| Count Nice Pairs · Number of Good Pairs | LC **1814** · **1512** | #1 · #5 | Two Sum on a derived key, or `nC2` of frequencies. |
| Group Shifted Strings | LC **249** | #3 | Canonical key = offsets from the first char. |
| Find and Replace Pattern | LC **890** | #4 | Bijection, per word. |
| Valid Anagram · Ransom Note | LC **242** · **383** | #5 | Count, then consume. |
| Unique Number of Occurrences | LC **1207** | #5 | Frequencies themselves must be unique — a set of the counts. |
| Sort Characters By Frequency | LC **451** | ↗ LC 347 | Count, then order. |
| Valid Sudoku | LC **36** | #2 | Nine row sets, nine column, nine boxes. ↗ from [[Matrix]]. |
| Happy Number *(seen set)* | LC **202** | #2 | Floyd is better. [[Two Pointers]] #7. |
| Jewels and Stones · Intersection of Two Arrays | LC **771** · **349** | #2 | Membership. Intersection II keeps counts (#5). |
| Find Duplicate File in System | LC **609** | #3 | Group by content string. |
| Encode and Decode TinyURL | LC **535** | — | Two-way map. [[Design]] #7. |
| Design HashSet | LC **705** | #9 | Boolean payload. |
| Longest Consecutive *(sort)* | LC **128** | #6 | The `O(n log n)` baseline. |
| Contains Duplicate III *(ordered set)* | LC **220** | #8 | [[Binary Search Trees]] #15. |
| Subarray Sums Divisible by K | LC **974** | ↗ P2 | The key is `P mod k`. [[Prefix Sums & Difference Arrays]] #6. |

---

## Self-audit

**Borderline calls, and which way I went**

- **Dual-native with [[Arrays]] A3 on #1, #3, #6.** Those three were written there because this file did not exist. They stay there (the sweep is an array) and live here (the move is a hash map). Same rule as Meeting Rooms and LC 632.
- **#2 kept separate from #1.** Complement vs identity-plus-distance. Merging would hide last-seen, which is half of sliding-window hashing.
- **#4 kept separate from #3.** Canonical key *erases* a distinction; bijection *enforces* one. The reverse-map check is the idea.
- **#8 native despite [[Binary Search Trees]] #15.** Two solutions, two files. The bucket argument is how a hash map answers a range; the ordered set is how you do it without the floor/negative bug. Dual on purpose.
- **#9 and #10 in a concept basis.** Same defence as [[Sorted Containers & Order Statistics]] S5: a correct algorithm you cannot type in C++ is a failed interview. #10 is the one that actually bites.
- **Prefix P2 is ↗, not native.** The *fold* is Prefix; the map is a consumer. Native here would duplicate five entries. One ↗ row names the family.

**Naming check.** #1 stayed "Two Sum" — the name *is* the idea. #2 was going to be "last seen" and the LC title is what people search. #7 stayed "4Sum II" because "meet in the middle with a map" is in the body and the number is the handle. #8 is named after the bucket move in the body, problem in the title.

**Step 4B — reverse sweep**

Eighteen descriptions.

- **"Are these two words the same letters in different order"** → #3. **"Does this mapping stay 1-1"** → #4. Split held.
- **"Count quadruples summing to zero, `n = 200`"** → #7, not nested #1. Why #7 exists.
- **"Any two values within `t`, only last `k` elements"** → #8 and ↗ BST #15. Dual, intended.
- **"Put a `pair<int,int>` in an unordered_map"** → #10. Why S5-style entries exist.
- No missing axis. The topic is short and named; recall and recognition mostly agree.

**Step 4C — inward**

(i) [[Arrays]] A3 was squatting; now dual. [[Prefix Sums & Difference Arrays]] P2 already described the map; ↗ back. [[Strings]] flagged a recut of rolling hash — left there, ↗ from here.
(ii) Two Sum, anagrams, consecutive, 4Sum II, isomorphic, Duplicate II/III, Design HashMap all sit on a native or a ↗.
(iii) Arrays "Hashing is missing" — this file. Strings "Hashing will recut S2" — closed without moving #5.

**What I am uncertain about**

- **[[Design]] will want #9.** Kept here because the content is how a hash table *works*, not which API you wrap it in. Design ↗ this.
- **Worst-case hashing (universal, cuckoo)** — out of scope. `reserve` and "use a map if they adversarial-test" is the interview answer.
- **Rolling hash as a Hashing native.** No — the primitive is a substring as a polynomial, which is [[Strings]] #5. This file's hashes are *keys in a table*.

**Completeness confidence: ~90%.** H1–H6 are the interview core and have dense external lists. H7 is C++-specific and is the part a textbook hashing chapter would pad with open addressing variants I collapsed into #9.

## Related Notes

- [[README]]
- [[Arrays]]
- [[Prefix Sums & Difference Arrays]]
- [[Sliding Window]]
- [[Strings]]
- [[Design]]
- [[Sorted Containers & Order Statistics]]
- [[Binary Search Trees]]
- [[Linked List]]
- [[Graphs]]
- [[Two Pointers]]
- [[Bit Manipulation]]
- [[Backtracking]]
- [[Matrix]]
- [[Union-Find]]
