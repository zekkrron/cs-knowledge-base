---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-28
---
# Concept Basis — Strings

> [!abstract] Minimal spanning set for **linear string algorithms** — failure functions, rolling hashes, palindrome radii, suffix structures. One entry per **new idea you have to learn**. Prefix *trees* live in [[Tries]]; string DP (LCS, edit distance, palindrome table) in [[Dynamic Programming]]; expand-around-centre in [[Two Pointers]]. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!warning] **Scope note.** The interview-relevant core is **#1–#7** — KMP (search, borders, the concat trick), Z, rolling hash, binary-search-plus-hash, and Manacher. Suffix array + LCP (#8, #9) are the CSES / harder-round half. Suffix automaton (#10) and KMP-as-a-DP-coordinate (#11) are tagged **[tail]** and worth naming, not grinding.

## The one reflex this topic is trying to build

> [!tip] **A string problem is almost never "loop over characters." It is "what did I already know about this prefix, and how far can that knowledge jump?"**
>
> Naive matching re-reads characters it has already compared. Every entry below is a way of *not doing that*: a failure function remembers the border, a Z-box remembers a window already matched against the prefix, a hash turns a substring into a number, a suffix array puts every tail in order so LCP answers "how much do these two tails share."
>
> The corollary, and the reason this file is small: **if the question is about a *set* of patterns or a prefix query, you wanted a trie.** If it is about a subsequence or an edit, you wanted DP. If it is a window of characters, you wanted [[Sliding Window]]. Reach for this file when the object is one (or two) strings and the primitive is *substring structure*.

## Mechanism axes

| Axis | Values |
|---|---|
| **What is precomputed** | prefix function `π` · Z-array · polynomial prefix hashes · palindrome radii · suffix array + LCP · suffix automaton · nothing (scan) |
| **What the query asks** | find a pattern · all occurrences · period / border of one string · substring equality · longest palindrome · order of suffixes · count of distinct substrings |
| **How many patterns** | one · many (↗ [[Tries]] #18 Aho–Corasick) |
| **How a mismatch is repaired** | restart from 0 · **jump to `π[q]`** · slide a hash window · expand a palindrome radius using a mirrored one |
| **What equality means** | exact characters · **hash, with collision risk** · after a cyclic shift |
| **Random access to the text** | yes · **no — stream / online** (KMP is; a hash window needs the last `m` characters) |
| **Assumption that breaks** | naive matching is `O(nm)` · a single hash collides · a suffix *trie* is `O(n²)` nodes |

## Shape of this topic

```
S1  Failure functions              4 ideas
S2  Rolling hash                   2 ideas
S3  Linear palindromes             1 idea
S4  Suffix structures              3 ideas   ← #10 tail
S5  Automaton as state             1 idea    ← tail
                                   + 10 cross-listed ↗
```

**11 native entries, plus 10 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.**

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| **KMP** · Knuth–Morris–Pratt · LPS · `π` / prefix function | #1 |
| **Border / period theorem** | #2 |
| Concatenate-with-separator *(KMP on `s + # + t`)* | #3 |
| **Z-algorithm** · Z-function | #4 |
| **Rabin–Karp** · polynomial rolling hash | #5 |
| Binary search on length + hash | #6 |
| **Manacher** | #7 |
| **Suffix array** · prefix doubling | #8 |
| **Kasai LCP** | #9 |
| **Suffix automaton** · SAM | #10 |
| KMP automaton as a DP coordinate | #11 |

---

## S1 · Failure functions

The shared fact: **a proper prefix that is also a suffix (a *border*) is reusable work.** KMP stores it as `π`; Z reports, at every index, how much of the whole string matches there.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Find the Index of the First Occurrence in a String | LC **28** | **On mismatch, jump to the longest border of the pattern prefix you have already matched — never slide by one and re-read.** Compute `π[i]` = longest proper prefix of `pat[0..i]` that is also a suffix; while matching, a fail at pattern index `q` resumes at `π[q−1]`. `O(n + m)`, deterministic, and **online** — you can process the text as a stream. The same table searched against `pat + '#' + text` reports every occurrence as a `π` value equal to `m`. Write the `π` loop from memory once; every entry below is a different *question* asked of the same table. Many patterns at once is Aho–Corasick (↗ [[Tries]] #18), which is this failure function on a trie. |
| 2 | Repeated Substring Pattern | LC **459** | **The border of the *whole* string is the period.** If `π[n−1] > 0` and `n` is divisible by `n − π[n−1]`, the string is `k ≥ 2` copies of a block of that length. New because you are no longer searching a text — you are reading a *property of one string* off the last `π` value. CSES Finding Borders is the unadorned form (every `π` jump is a border); Finding Periods is the Z-form of the same fact (#4). Longest Happy Prefix (LC **1392**) is literally `s[0..π[n−1])`. |
| 3 | Shortest Palindrome | LC **214** | **Build a string whose border *is* the answer to a different question.** `s + '#' + reverse(s)`, then `n − π[last]` is the suffix you must prepend to make a palindrome. New against #2 because the table is run on a *constructed* string, not on the input — the separator is load-bearing (without it, borders leak across the join). The same shape answers "longest palindromic prefix", "shortest cyclic shift" via `s+s`, and any problem of the form *reduce a construction to a border query*. |
| 4 | Z-algorithm | *classic — CSES String Functions; practise matching on LC **28*** | **`Z[i]` is the LCP of `s` with `s[i..]`, computed in linear time by maintaining the rightmost window `[L, R]` already matched against the prefix.** If `i` is inside that window, `Z[i − L]` is a lower bound and you only extend when it would fall off `R`. Dual of KMP: `π` answers "border of every prefix"; `Z` answers "how much of the whole string matches here." Same power, different view — matching is `Z` of `pat + '#' + text`, periods are indices `i` with `i + Z[i] = n`. New because the *maintained interval* is a two-pointer window on the string itself (↗ [[Two Pointers]] geometry), not a stack of failure jumps. Know both; interviewers ask you to pick. |

## S2 · Rolling hash

> [!tip] **Reach for a hash when substring *equality* is a primitive you will call many times. Reach for KMP when you need a deterministic linear scan or the failure function itself.** A hash is `O(1)` per substring after `O(n)` precompute; it can lie. KMP cannot lie and cannot compare two arbitrary ranges.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 5 | Rabin–Karp / polynomial rolling hash | *classic — LC **28** is the search form; palindrome queries hash `s` and `reverse(s)`* | **A substring is a number: precompute prefix hashes and `pow[i] = b^i`, then `hash(l..r) = (H[r+1] − H[l] · b^{len}) mod M` is `O(1)`.** Pick a base `b` coprime to `M` (131, 911, `1e9+7` or a 64-bit overflow-as-mod). **A collision is a wrong answer**, so either double-hash (two mods) or verify with a character compare on hit. New because equality of ranges becomes arithmetic — the same algebra as a prefix sum ([[Prefix Sums & Difference Arrays]] #1), with collision as the failure mode that prefix sums do not have. Sliding a fixed window is a multiply-add-subtract; packing a tiny alphabet into an `int` instead of a polynomial is [[Bit Manipulation]] #17. |
| 6 | Longest Duplicate Substring | LC **1044** | **Binary search the length; the predicate is "does any substring of this length repeat", answered by hashing every window into a set.** Search is [[Binary Search]]'s skeleton; the string idea is the predicate — one hash per window of length `mid`, and a collision-aware set (store start indices, compare on hash hit). Same template for longest repeating subarray (LC **718**), longest common substring of two strings via hash, and "largest `L` such that a length-`L` string appears ≥ `k` times." New against #5 because the hash is no longer the answer; it is the *check* inside a search on a monotone length. |

## S3 · Linear palindromes

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Longest Palindromic Substring *(Manacher)* | LC **5** · CSES **1111** | **Palindrome radii in `O(n)`: odd and even centres sit on a transformed string (`#a#b#c#`), and a radius inside a larger palindrome is copied from its mirror, then maybe extended.** Expand-around-centre is `O(n²)` and is the geometry you learn first ([[Two Pointers]] #6); Manacher is what you write when `n` is `10⁵` or CSES asks for the longest palindrome. New because the *reuse* is a mirror inside an already-known palindrome — the same "I have already matched this, jump" instinct as KMP, on a different symmetry. The `dp[i][j]` palindrome *table* is still [[Dynamic Programming]] #75; it classifies every range, which Manacher does not. |

## S4 · Suffix structures

> [!warning] **A suffix *trie* (insert every suffix) is `O(n²)` nodes and is [[Tries]] #15.** Everything here exists because that is hopeless at `n ≥ 10⁴`. Interview bar: know what a suffix array *is*, build it by prefix doubling, and use LCP. Do not implement Ukkonen or SA-IS at a whiteboard.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Suffix array — prefix doubling | *classic — CSES Distinct Substrings / Suffix Array; no clean LC* | **Sort the suffixes, but compare them as pairs of already-ranked halves so each round doubles the known prefix.** After round `k` you know order by the first `2^k` characters; `O(n log² n)` with `std::sort`, `O(n log n)` with radix. The array `sa[i]` = starting index of the `i`-th suffix in lexicographic order; `rank[j]` is the inverse. New because **the suffixes of one string are not `n` independent strings** — they nest, and doubling exploits that. Smallest cyclic rotation is the smallest suffix of `s+s` of length `n` (or the concat trick #3). `O(n)` constructions (SA-IS, DC3) exist; name them, do not write them. |
| 9 | LCP array (Kasai) | *classic — CSES Distinct Substrings; longest common substring of two strings* | **Adjacent suffixes in suffix-array order have an LCP that is easy, and walking `i, i+1, i+2, …` in *text* order reuses the previous LCP minus one.** Kasai is `O(n)` given `sa` and `rank`. Distinct substrings = `n(n+1)/2 − Σ lcp[i]`. Longest common substring of `s` and `t`: concatenate with a separator, take the max LCP whose two suffixes come from different sides. LCP of *arbitrary* suffixes `i, j` is an RMQ on the LCP array between their ranks ([[Segment Trees]] #9). New against #8 because the array of suffixes does not, by itself, count or compare *content* — the height array does. |
| 10 | Suffix automaton | *classic — every substring is a unique path; `|states| ≤ 2n − 1`* **[tail]** | **Endpos-equivalence: two substrings that occur in exactly the same set of ending positions are the same state; a clone-on-split keeps the automaton linear.** One path per distinct substring, so counting, longest common substring, and "smallest cyclic shift" become graph walks. New against #8/#9 because the object is a DAG, not a permutation of indices — more powerful, and you will not be asked to write the construction in an Indian SDE loop. Know it exists; the interview substitute is #8 + #9. |

## S5 · Automaton as state

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Required Substring | CSES **1112** | **The KMP automaton is a legal DP coordinate: state = "longest prefix of the pattern matched so far."** Transitions are `δ(q, c)`; absorbing at `q = m` means the pattern has occurred. Counting strings of length `n` that contain (or avoid) a pattern is then `dp[i][q]`, `O(n · m · Σ)`. New against #1 because you are no longer *searching* a given text — you are *generating* texts while the failure function tracks a constraint. The same embedding puts Aho–Corasick into a DP (many forbidden words). String-avoidance DP lives here for the automaton and in [[Dynamic Programming]] for the table. **[tail]** for interview frequency; not for the idea. |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them while studying strings and they belong in this file.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Longest Palindromic Substring *(expand)* | LC **5** | `2n − 1` centres, pointers diverge. The geometry; #7 is the linear upgrade. [[Two Pointers]] #6. |
| ↗ | Palindromic Substrings *(table)* | LC **647** | `dp[i][j]` iff ends match and inside is a palindrome. Classifies every range; Manacher does not. [[Dynamic Programming]] #75. |
| ↗ | Edit Distance · LCS · LPS | LC **72** · **1143** · **516** | Subsequence / alignment DP. A string *algorithm* this is not. [[Dynamic Programming]] #18 · #17 · #37. |
| ↗ | Word Break | LC **139** | DP on prefixes, dictionary as a set or a trie. [[Dynamic Programming]]; the trie acceleration is [[Tries]] ↗ LC 140. |
| ↗ | Aho–Corasick | *classic* | KMP's failure function on a trie: many patterns, one pass. Native at [[Tries]] #18. |
| ↗ | Number of Distinct Substrings *(suffix trie)* | LC **1698** | Insert every suffix, `O(n²)` nodes. The naive form; #8/#9 are the scalable one. [[Tries]] #15. |
| ↗ | Packed sliding window | LC **187** | Tiny alphabet packed into an `int`, shifted in and masked out. Rolling hash's bit-level twin. [[Bit Manipulation]] #17. |
| ↗ | Reverse Words in a String | LC **151** | Reverse all, then reverse each word. [[Two Pointers]] #13. |
| ↗ | Greatest Common Divisor of Strings | LC **1071** | Euclid on strings: repeated structure, not characters. [[Math & Number Theory]] #5. |
| ↗ | Group Anagrams · permutation-in-string | LC **49** · **567** | Canonical keys and windowed frequency. [[Hashing]] #3 · [[Arrays]] #8 and [[Sliding Window]] #2 — no substring *structure*. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Implement `strStr` *(naive)* | LC **28** | #1 | The `O(nm)` scan is the thing #1 exists to beat. Fine at small `m`. |
| Longest Happy Prefix | LC **1392** | #2 | `s[0..π[n−1])` — the border as a string. |
| Finding Borders · Finding Periods | CSES **1732** · **1733** | #2 · #4 | Unadorned `π` jumps / `i + Z[i] = n`. |
| Repeated String Match | LC **686** | #1 | KMP of `b` inside enough copies of `a`. |
| Rotate String | LC **796** | #1 · #3 | `b` occurs in `a+a`. |
| Shortest Palindrome *(hash)* | LC **214** | #3 · #5 | Hash `s` and `reverse(s)` for the longest palindromic prefix; KMP is deterministic. |
| Distinct Echo Substrings | LC **1316** | #5 | Hash every even-length split. |
| Maximum Repeating Substring | LC **1668** | #1 · #6 | KMP, or search on repeat count. |
| Longest Repeating Substring · Longest Common Subarray | LC **1062** · **718** | #6 | Same search-on-length + hash (or #9 on concatenated SA). |
| Palindrome Queries | *classic* | #5 | Hash of `s[l..r]` equals hash of the corresponding reverse range. |
| Smallest cyclic shift · Booth | *classic* | #3 · #8 | Concat or suffix array; Booth is the linear specialised form. |
| Count and Say | LC **38** | — | Simulation; run-length as output, not as a structure. |
| String Compression | LC **443** | — | Write-behind compaction. [[Two Pointers]] #10. |
| Zigzag Conversion | LC **6** | — | Index arithmetic, not a string algorithm. |
| Integer to Roman · atoi · multiply strings | LC **12** · **8** · **43** | — | [[Greedy]] #15, [[Math & Number Theory]] #4 · #3. |
| Decode String | LC **394** | — | Suspend/restore. [[Stack and Queue]] #5. |
| Minimum Window Substring | LC **76** | — | [[Sliding Window]] #2. |
| Longest Substring Without Repeating Characters | LC **3** | — | [[Sliding Window]] #3. |
| Valid Palindrome II | LC **680** | — | [[Two Pointers]] #20. |
| Wildcard / Regular Expression Matching | LC **44** · **10** | — | DP on two pointers into pattern and text. [[Dynamic Programming]]. |
| Boyer–Moore · Horspool · Sunday | *classic* | — | Bad-character / good-suffix skip tables. Faster in practice on large alphabets; not asked. KMP is the interview algorithm. |
| Ukkonen suffix tree · SA-IS · DC3 | *classic* | #8 | `O(n)` constructions. Name them; do not write them. |
| Palindromic tree *(eertree)* | *classic* | #7 | All palindromic substrings as a graph. CP. |
| Lyndon factorisation · Duval | *classic* | — | CP. Smallest rotation is #3/#8. |
| Burrows–Wheeler · FM-index | *systems* | #8 | Compressed suffix array. System design, not DSA. |
| 2D Rabin–Karp / matching | *classic* | #5 | Rolling hash on rows then columns. Native at [[Matrix]] #9. |
| Shift-and / bitset matching | *CP* | — | Out of scope per [[README]]. |

---

## Self-audit

**Borderline calls, and which way I went**

- **This file is the algorithm layer, not "problems that happen to use strings."** Reverse words, anagrams, decode, windows, and edit distance all *look* like string chapter problems and are all ↗. The inclusion test was: *does a precomputed string structure replace an `O(nm)` scan or an `O(n²)` compare?* That keeps KMP, Z, hashes, Manacher, SA/LCP, and throws out everything other files already own.
- **#2 kept separate from #1.** Same `π` table. Split because search and "read a period off `π[n−1]`" are different *questions*, and LC 459 is asked without ever mentioning matching.
- **#3 kept separate from #2.** The concat-with-separator construction is the move interviewers actually want after strStr, and merging it into "borders" would hide that you *build a string* so that a border becomes the answer.
- **#4 (Z) native rather than folded into KMP.** Dual information, different computation, and "which would you write" is a real follow-up. The `[L, R]` window is a two-pointer argument, which is why the entry points at [[Two Pointers]] without moving there.
- **#6 native rather than "just #5 plus binary search."** Composition, and I nearly excluded it. Kept because the *predicate* — hash every window of length `mid` — is the string half of a template that shows up as LC 1044 / 718 / 1923, and a Strings file that only taught "here is a hash" would leave you unable to answer the Hard.
- **#10 and #11 tagged [tail].** SAM you will not write; KMP-as-DP-state you might meet on CSES and almost never on LC Easy/Medium. Both are real ideas. Leaving them unnumbered would reproduce the "native nowhere" failure.
- **Manacher native, expand ↗, table ↗ DP.** Three faces of palindromes, three files. Deliberate: the geometry, the DP classification, and the linear radii are different ideas, and each file's reader needs its own.

**Naming check.** #1 was going to be "KMP" and is now *jump to the border on mismatch*, because that is what transfers to Aho and to #11. #3 was "shortest palindrome" and is now *concatenate so a border answers a different question*. #6 was "longest duplicate substring" and is now the search-on-length + hash *template*.

**Step 4B — reverse sweep**

Eighteen plain-language descriptions navigated against the family headings.

- **"Add as little as possible in front to make this a palindrome"** lands on #3, not #7. Correct: Manacher finds the longest palindrome *inside*; this constructs one by a border of `s+#rev`.
- **"How many different substrings does this have"** lands on #9 (and ↗ the suffix trie). Correct split by `n`.
- **"Count binary strings of length n that contain 1011"** lands on #11, not #1. That is why #11 exists; without it the description dies at "KMP searches a text."
- Collision, checked and cleared: **"smallest cyclic shift"** reaches #3 and #8 — two genuine solutions, named in both.
- Collision, checked and cleared: **"does this string have period p"** reaches #2 and #4. Dual, intended.

No missing axis. The axes were written from the algorithms, which is the failure mode this step usually catches — here the topic *is* a short list of named algorithms, so recall and recognition mostly agree. Remaining miss risk is a named algorithm I did not put on the list (Boyer–Moore was considered and excluded).

**Step 4C — inward**

(i) Reciprocity: [[Tries]] ↗ KMP/Z/hashing and suffix-array exclusion; [[Prefix Sums & Difference Arrays]] ↗ prefix hashing; [[Two Pointers]] #6 Manacher hedge; [[Bit Manipulation]] #17 rolling-encoding hedge; [[Dynamic Programming]] #75 "pulled native from the Strings ↗". All now point at IDs in this file.
(ii) Orphans: CSES String Algorithms (matching, borders, periods, longest palindrome, distinct substrings, string functions) all sit on #1–#4, #7, #9. LC 28 / 214 / 459 / 1392 / 1044 / 5 accounted for.
(iii) Hedges acted on: Tries "boundary with Strings", Bit "when Strings is written #17 should ↗", Two Pointers "Manacher waits on Strings", Prefix "Strings basis", README "not started".

**What I am uncertain about**

- **S2 vs [[Hashing]] is closed.** Polynomial rolling hash stays here because the primitive is a substring as a number, not a key in a table. Maps, canonical keys, and frequency payloads are [[Hashing]]. ↗ both ways.
- **2D matching is [[Matrix]] #9.** Same hash, extra dimension. #5 stays the 1D polynomial.
- **Whether #8's doubling belongs in [[Sorting & Custom Comparators]]** as a custom key. No — the *rank pairing* is the string idea.
- **Boyer–Moore excluded.** Faster in practice; never asked in the loops this vault is for. Moderate confidence.
- **SAM as tail rather than native-untagged.** A contest-heavy reader would want it in the core. This vault's scope is interview; the call matches [[Tries]] parking SAM here in the first place.

**Completeness confidence: ~90%** on S1–S3 (named algorithms, dense external lists). S4 is "know what it is" complete, not "can implement every construction" complete, which is the intended bar.

## Related Notes

- [[README]]
- [[Tries]]
- [[Two Pointers]]
- [[Dynamic Programming]]
- [[Prefix Sums & Difference Arrays]]
- [[Bit Manipulation]]
- [[Binary Search]]
- [[Sliding Window]]
- [[Arrays]]
- [[Math & Number Theory]]
- [[Stack and Queue]]
- [[Segment Trees]]
- [[Graphs]]
- [[Hashing]]
- [[Matrix]]
