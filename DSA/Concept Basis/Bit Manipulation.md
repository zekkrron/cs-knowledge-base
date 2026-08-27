---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Bit Manipulation

> [!abstract] Minimal spanning set for treating an integer as **thirty-two flags that are independent until an operation couples them**. One entry per **new idea you have to learn**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!tip] **The operators, said in one sentence each.** `AND` tests and clears. `OR` accumulates and never forgets. `XOR` cancels a value against itself and is its own inverse. A shift is multiply or divide by two, except when the sign bit makes it not. Almost every entry below is a consequence of one of those four facts, plus the question the axes exist to force: **are the bits independent, or is something tying them together?**

## Mechanism axes

| Axis | Values |
|---|---|
| **What the integer is standing in for** | the number itself · **a set of flags** (occupancy, an alphabet, used items) · **packed data** (a window of characters, several small fields) · a permutation of bits |
| **Are the bits independent?** | **yes** — the answer is 32 scalar answers added up · **no, coupled by carry** · **no, coupled by a contiguous range** · **no, an aggregate that only grows** (OR) |
| **The algebra** | XOR (self-inverse, cancellation) · AND (test, intersection) · OR (union, monotone) · `+` (XOR plus a carry that *moves*) |
| **How you iterate** | all 32 positions · **only the set bits** (`lowbit`, Brian Kernighan) · all masks `0…2ⁿ−1` · **all submasks of a given mask** |
| **How many times each value appears** | twice (XOR cancels) · **three / `k` (need mod-`k` per bit, or a state machine)** · mixed |
| **Granularity of the operation** | one bit · **all bits below a cutoff** (prefix flip, Gray) · **a window of `k` consecutive bits** |
| **What is returned** | a property of `n` · a constructed integer · a count · the unique / missing value · a set of masks |
| **What breaks** | **signed right-shift filling 1s** · `1 << 31` overflowing a signed int · assuming AND/OR are invertible · treating a partial order of bits as independent when a range couples them · iterating 32 times when only `popcount` bits are set |

## Shape of this topic

```
K1  The toolkit                           2 ideas
K2  XOR as cancellation                   4 ideas
K3  Bits as independent coordinates       2 ideas
K4  When bits are coupled                 5 ideas
K5  An integer as a compact set           3 ideas
K6  Representation                        2 ideas
                                          + 10 cross-listed ↗
```

**18 native entries, plus 10 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** #18 was added by the reverse sweep and sits inside K4.

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| **`n & (n − 1)`** · clear lowest set bit · power of two | #1 |
| **`n & −n`** · **lowbit** | #1 · #16 |
| **Brian Kernighan** *(popcount by clearing)* | #2 |
| **Single Number** | #3 |
| **Single Number III** *(split by a distinguishing bit)* | #4 |
| **Single Number II** *(mod-3 / two-mask FSM)* | #5 |
| Hamming distance | #2 · #3 |
| **Contribution per bit** · Total Hamming Distance | #7 |
| **Counting Bits** *(DP on `i >> 1`)* | #8 |
| Adder from XOR and carry | #9 |
| Range AND · common prefix | #10 |
| **Gray code** · inverse Gray | #12 |
| Power set via bits | #13 |
| **Submask enumeration** · `(s − 1) & m` | #14 |
| Two's complement | #16 |
| Bit packing | #17 |

---

## K1 · The toolkit

Two identities, and the loop that uses them. Everything else in the file is an application.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Power of Two, and the two identities | LC **231** | **`n & (n − 1)` clears the lowest set bit; `n & −n` isolates it.** Those are the only two things you have to memorise, and power-of-two is the one-line corollary: `n > 0 && (n & (n − 1)) == 0`, because a power of two has exactly one bit set and clearing it yields zero. Get / set / clear / toggle the `i`-th bit are then just `n & (1 << i)`, `n \| (1 << i)`, `n & ~(1 << i)`, `n ^ (1 << i)`. New because this is the *language* the rest of the file is written in — without these, every later entry is a magic constant. The one trap: **`1 << 31` is undefined or overflows a signed 32-bit int**; write `1 << i` in `unsigned` / `long`, or use `1L << i`. Power of four (LC **342**) is this plus "the single set bit sits on an odd position", usually checked against `0x55555555`. |
| 2 | Number of 1 Bits | LC **191** | **Walk only the bits that are set: `while (n) { n &= n − 1; cnt++; }`.** That is Brian Kernighan, and it is `O(popcount)` rather than `O(32)` — which is the same instinct as iterating a graph's adjacency list instead of a dense 32-column scan. New because **the loop bound is the data, not the word size**, and because Hamming distance is then one extra XOR (LC **461**): the positions two numbers differ are exactly the set bits of `a ^ b`. Once you have this, "minimum bit flips to convert `a` to `b`" (LC **2220**) is the same line. |

## K2 · XOR as cancellation

> [!tip] XOR is addition without carry, and that one fact is why it is the most useful operator in this file. `a ^ a = 0`, `a ^ 0 = a`, commutative and associative, **its own inverse**. A value XOR'd in twice vanishes; a value XOR'd in once survives. That is a counting argument that uses no storage.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Single Number · Missing Number | LC **136** · **268** | **XOR every element (and, for missing, every index); the survivor is the one that appeared an odd number of times.** Two appearances cancel, one remains. This is the same aggregate-identity idea as [[Arrays]] #3, developed here because the *algebra* is the content: you can fold the cancellation into a running XOR, you can XOR a stream you cannot store, and you can undo an earlier XOR by XOR'ing the same value again. Find the Difference (LC **389**) is the same move on characters. The thing to say out loud: **XOR is a set-symmetric-difference that happens to fit in a register.** |
| 4 | Single Number III | LC **260** | **When two values survive, XOR of the whole array is `a ^ b`, which is not zero — and any set bit of that XOR is a bit where `a` and `b` *differ*.** Isolate one such bit with `lowbit` (#1), then walk the array a second time partitioning on whether that bit is set: each unique falls into a different bucket, and the duplicates still cancel inside their bucket. New because XOR of everything is no longer the answer — it is a **handle that lets you split the problem into two copies of #3**. The transferable move: *when an aggregate is a mix of two unknowns, find a coordinate they disagree on and project.* |
| 5 | Single Number II | LC **137** | **XOR is addition mod 2, so it dies the moment the duplicates come in threes; count each bit mod 3 instead, or keep a two-bit state machine.** Per-bit: for each of the 32 positions, `count % 3` is the unique number's bit. The linear-time `O(1)`-space version maintains two masks `ones` and `twos` so that a value seen once lives in `ones`, seen twice in `twos`, and seen three times is cleared from both — a tiny FSM on the pair `(ones, twos)` that you update with ANDs and XORs. New because **the modulus of the cancellation has to match the multiplicity**, and "appears `k` times except one" is the same construction with `k` states (LC **137** follow-up). If you only know #3, this is the problem that shows you why. |
| 6 | XOR of numbers in a range | *classic — Striver; practise on LC **1486** / "XOR from `L` to `R`"* | **`xor(1…n)` is periodic with period 4: `n`, `1`, `n+1`, `0` according to `n % 4`, so a range is `xor(1…R) ^ xor(1…L−1)`.** You never loop. New because a running XOR looks like it needs a prefix array (#3 applied `n` times) and a closed form deletes the array — the same reflex as [[Math & Number Theory]]'s opening sentence, applied to bits. The range identity is also why [[Prefix Sums & Difference Arrays]] #2 can answer XOR queries in `O(1)` after one precompute, and why Decode XORed Array (LC **1720**, **2433**) is just "XOR is its own inverse, so peel the prefix off." |

## K3 · Bits as independent coordinates

The moment the operator does not mix bits, a pairwise or global question becomes thirty-two independent scalar questions. This is [[Math & Number Theory]] #25 wearing a bit costume, and it is the reason "looks `O(n²)`" bitwise problems are not.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Total Hamming Distance | LC **477** | **Hamming distance adds over bits, so sum over pairs equals sum over bits of (count of 1s) × (count of 0s).** Each bit position contributes independently; a pair differs there iff one has 0 and one has 1. `O(32n)` instead of `O(n²)`. New as a *recognition* rather than a formula: **whenever the quantity is a sum of per-bit predicates, you never have to look at two numbers at once.** Same move: Largest Combination With Bitwise AND Greater Than Zero (LC **2275**) is just "max frequency of any single bit"; Sum of All Subset XOR Totals (LC **1863**) counts, per bit, how many subsets have it set. If a bitwise pair problem feels quadratic, this is the first thing to try. |
| 8 | Counting Bits | LC **338** | **The answer for `i` is a function of a strictly smaller `i` you have already computed: `dp[i] = dp[i >> 1] + (i & 1)`, or `dp[i] = dp[i & (i − 1)] + 1`.** You fill `0…n` in linear time without ever popcounting from scratch. New because it is the DP-shaped form of #2: **a bit-property of `i` is a bit-property of `i` with one bit removed**, so the table is free. The two recurrences are worth both knowing — shift-and-add follows the binary representation, Kernighan-step follows the set-bit structure, and they disagree on nothing except which smaller index they jump to. |

## K4 · When bits are coupled

Independence is the default and it is a trap. These four are the ways bits stop being independent.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | Sum of Two Integers | LC **371** | **XOR is the sum *without* carry; `(a & b) << 1` is the carry; iterate until the carry vanishes.** Bits that looked independent under XOR get coupled the moment you add, because a carry walks into the next position. New because it is the one entry that *builds* `+` out of the other operators, and because the same loop with different combining rules is how you do subtraction, and how [[Math & Number Theory]] #2 builds a quotient one bit at a time. Watch the signed-int trap: in Java and C++ a left-shift of the sign bit is undefined or wraps, so the textbook solution is cleaner on `unsigned`. |
| 10 | Bitwise AND of Numbers Range | LC **201** | **AND of every integer in `[L, R]` is the *common prefix* of `L` and `R`; everything below the first differing bit is zero, because some number in the range flipped it.** The implementation that makes this obvious: while `L < R`, clear `R`'s lowest set bit (`R &= R − 1`); when they meet, that value *is* the AND. New because this is the counterexample to K3 — **a contiguous range couples bits that a set of arbitrary numbers would not**, so you cannot AND each bit position independently against "does every number have it." The same common-prefix reasoning is how you find the highest bit at which two numbers differ, and it is the reason "AND of a subarray" is monotone in a way XOR of a subarray is not. |
| 11 | Bitwise ORs of Subarrays | LC **898** | **OR only ever turns bits on, so the chain of distinct prefix-ORs ending at a fixed `r` has length at most 32.** As you extend a subarray leftward (or rightward), each new OR is a superset of the previous; a strictly-growing chain of bitsets on a 32-bit universe cannot be longer than 32. So "how many different ORs of subarrays" is `O(32n)`, not `O(n²)`. New and the most transferable coupling result in the file: **a monotone bitwise aggregate takes a logarithmic number of distinct values on a nested family of intervals.** Same fact drives Smallest Subarrays With Maximum Bitwise OR (LC **2411**), Shortest Subarray With OR at Least K (LC **3097**, ↗ [[Sliding Window]] #13), and the "OR-closest-to-target" family. AND-growing-toward-zero is the dual. |
| 12 | Gray Code | LC **89** | **`i ^ (i >> 1)` produces a sequence where consecutive codes differ by exactly one bit — a Gray code.** The encoding is the idea: you *want* bits coupled, in the specific sense that walking the integers in order should walk the hypercube by single edges. Inverse Gray (binary from Gray) is a prefix-XOR of the Gray bits, which is also **Minimum One Bit Operations to Convert Number** (LC **1611**): converting `n` to 0 by flipping prefixes of bits *is* the inverse Gray of `n`. New because it is a designed coupling rather than an accidental one, and because the inverse showing up as an interview problem is the giveaway that Gray is not a curiosity. |
| 18 | Minimum Number of K Consecutive Bit Flips | LC **995** | **A flip of `k` consecutive bits is a range-XOR of 1s, so the decision at `i` is forced: if `a[i]` is still wrong after earlier flips, you *must* flip at `i`, or you can never fix it.** Track the running flip-parity with a difference array (or a sliding window of flip events) so each position is `O(1)`. New because **the operation's granularity is a window**, not a bit and not a prefix — #12 flips everything below a cutoff, this flips a sliding block — and because greedily flipping at the leftmost defect is correct for the same reason a difference array is: later positions see the flip, earlier ones cannot be affected. If you cannot place this, you will reach for backtracking. The difference-array half is [[Prefix Sums & Difference Arrays]] #17. |

## K5 · An integer as a compact set

An `int` is a set of at most 32 (or 64) elements with union, intersection and membership in `O(1)`. That is the whole family.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 13 | Subsets | LC **78** | **The integers `0 … 2ⁿ − 1` *are* the power set: bit `i` of mask `m` means "element `i` is in."** Iterate the masks, and for each mask iterate its set bits (#2) to build the subset. New as a *replacement for recursion*: the backtracking tree of [[Backtracking]] #1 is this loop, unrolled, and it is the right tool when `n ≤ 20` and you need every subset rather than a search with pruning. Letter Case Permutation (LC **784**) is the same masks over the positions that can flip case. The bound to remember: `n = 20` is a million, `n = 30` is a billion and this loop is no longer an answer — that is when [[Backtracking]] #19 (meet in the middle) or [[Dynamic Programming]] D9 takes over. |
| 14 | Iterate all submasks of a mask | *classic — the inner loop of every bitmask DP; practise on LC **698** / **1434*** | **`for (int s = m; s; s = (s − 1) & m)` visits every non-empty submask of `m` in `O(2^{popcount(m)})`, not `O(2ⁿ)`.** Subtracting one flips all bits below the lowest set bit; AND with `m` throws away anything that was not in `m` to begin with. New, and distinct from #13: #13 enumerates *every* subset of a universe, this enumerates **the subsets of a given subset**, which is the transition of "I have used set `m`, what did I use in the last group?" The empty submask needs a separate `s = 0` if you want it. This loop is what makes [[Dynamic Programming]] #45–48 run in `O(3ⁿ)` rather than `O(4ⁿ)`, and writing it from scratch in an interview is a standard filter. |
| 15 | Maximum Product of Word Lengths | LC **318** | **Map each word to the bitset of letters it uses, then `masks[i] & masks[j] == 0` is "no letter in common" in `O(1)`.** The alphabet fits in 26 bits, so the set *is* an int. New because the mask is no longer "which items did I pick" but **which properties does this object have**, and intersection-empty is the predicate you actually wanted. The same occupancy-mask appears as the N-Queens follow-up (three ints for columns and both diagonals, `lowbit` to iterate free positions — [[Backtracking]] #6), as Sudoku's row/col/box occupancy, and as "state of used resources" in a BFS ([[Graphs]] #8). Once you see a small discrete universe, packing it into a register should be a reflex, not a trick. |

## K6 · Representation

The bits are not always a number or a set. Sometimes they are the *encoding*, and the bugs are about what the encoding promised.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 16 | Two's complement, and why `n & −n` is lowbit | *concept — the question behind every signed-shift bug* | **`−n` is `~n + 1`, so the bits of `−n` are "flip everything, then add one" — which means `n` and `−n` share exactly the lowest set bit and disagree everywhere above it.** That is why `n & −n` isolates lowbit, and it is why #1's second identity is not a coincidence. The practical half is the bugs: **arithmetic right-shift (`>>` on a signed int in Java/C++) fills 1s from the left**, so `−1 >> 1` is still `−1`, not `INT_MAX`; Java's `>>>` is the logical shift you actually wanted for bit patterns; negating `INT_MIN` overflows; and a signed `1 << 31` is undefined in C++. New because the *representation* is load-bearing — the identities in K1 are facts about two's complement, not about "bits" in the abstract — and because mixing "I wanted a bit pattern" with "the language thinks this is a negative integer" is the most common silent failure in this topic. |
| 17 | Repeated DNA Sequences | LC **187** | **Pack a small alphabet into a sliding integer instead of hashing a string.** DNA is 4 symbols, so two bits per character; a 10-character window is 20 bits, which fits in an `int`. Shift in a new character (`x = ((x << 2) \| code[s[i]]) & ((1 << 20) − 1)`) and shift out the one that fell off by the mask, and the window's identity is the integer. New because the mask is now **data, not a set of flags** — you are using the word as a rolling encoding, which is the bit-level form of a rolling hash ([[README]] Strings, unwritten) and the reason a fixed-length window over a tiny alphabet should never allocate a substring. UTF-8 validation (LC **393**) is the sibling: the *leading ones* of a byte encode how many continuation bytes follow, so the representation *is* the protocol. |

---

## Cross-listed

Developed more fully in the named topic, but you will meet them while studying bits and they belong in this file. See [[README]] on cross-listing.

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Maximum XOR of Two Numbers in an Array | LC **421** | Greedy descent on a binary trie, preferring the opposite bit at each level. XOR-maximisation is hopeless as `O(n²)` and is a trie problem the moment you see it. [[Tries]] #7; the range-restricted and persistent forms are #8 and #10 there. |
| ↗ | XOR Queries of a Subarray | LC **1310** | Prefix XOR, because XOR is invertible: `xor(l, r) = P[r+1] ^ P[l]`. The range form of #6's identity. [[Prefix Sums & Difference Arrays]] #2. |
| ↗ | Bit-level prefix counts · windowed OR/AND | LC **3097** | AND and OR are *not* invertible, so #3 does not give you a range query — but per-bit *counts* are, and "OR is the bits whose count is positive." Decompose an uninvertible aggregate into 32 invertible ones. [[Sliding Window]] #13; [[Prefix Sums & Difference Arrays]] ↗ bit-level counts. |
| ↗ | Vowels in even counts as a parity mask | LC **1371** | Several parities compressed into one integer, then used as a prefix-map key. A mask as a *state*, not as a set of items. [[Prefix Sums & Difference Arrays]] #8. |
| ↗ | Partition to K Equal Sum Subsets · TSP-shape | LC **698** · **943** | The mask *is* the DP state; #14 is the transition. [[Dynamic Programming]] #45–48. |
| ↗ | Shortest Path Visiting All Nodes | LC **847** | BFS on `(node, mask of visited)`, the first time "visited" is not a boolean. [[Graphs]] #8. |
| ↗ | N-Queens, bitmask follow-up | LC **51** | Three ints for columns and both diagonals; `lowbit` iterates free positions. #15's occupancy mask in its most famous costume. [[Backtracking]] #6. |
| ↗ | Fenwick tree | LC **307** | `i += i & −i` walks the implicit tree whose block sizes *are* the lowbits. #1's second identity as a data-structure engine. [[Segment Trees]] #2. |
| ↗ | Sign-bit marking in an array | LC **448** | The input's sign bits as a free bitset, because you are allowed to destroy it. A mask that lives *inside* the data. [[Arrays]] #2. |
| ↗ | Pow(x, n) · Divide Two Integers | LC **50** · **29** | Binary exponentiation *reads* the bits of the exponent; restoring division *writes* the bits of the quotient. The arithmetic uses this file's language and lives in [[Math & Number Theory]] #1 and #2. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Hamming Distance | LC **461** | #2 · #3 | `popcount(a ^ b)`. Named in #2. |
| Minimum Bit Flips to Convert Number | LC **2220** | #2 | Same Hamming. |
| Complementary / Number Complement | LC **476** · **1009** | #1 | Flip bits *up to the MSB*, not all 32 — a mask of `n`'s bit-width, then XOR. |
| Power of Four | LC **342** | #1 | Power of two plus "the bit sits on an odd position." |
| Swap two numbers without a temp | *classic* | #3 | `a ^= b; b ^= a; a ^= b`. XOR cancellation, and a good way to lose a value if `a` and `b` alias. |
| Find the Difference | LC **389** | #3 | XOR of characters. |
| Missing Number | LC **268** | #3 | Named as the second canonical of #3. |
| Single Number (the easy one) | LC **136** | #3 | The entry. |
| XOR Operation in an Array | LC **1486** | #6 | Closed form, or a short loop. |
| Decode XORed Array · Find Original Array of Prefix XOR | LC **1720** · **2433** | #6 | Peel a prefix XOR. |
| Decode XORed Permutation | LC **1734** | #6 | Recover `a[0]` from `xor(1…n)` and the known prefix XORs of the encoded array. |
| Maximum XOR for Each Query | LC **1829** | #3 · #6 | Running XOR from the back, then XOR with an all-ones mask of the bit-width. |
| XOR of All Pairings | LC **1835** · **2425** | #3 · #7 | If the other array's length is even, each element cancels; if odd, the XOR of this array survives. Contribution, one line. |
| Sum of All Subset XOR Totals | LC **1863** | #7 | Per bit, half the subsets have it set. |
| Largest Combination With Bitwise AND > 0 | LC **2275** | #7 | Max count of any single bit. |
| Reverse Bits · Reverse Bytes | LC **190** | #16 · #17 | Swap halves repeatedly, or a 32-step loop. Representation, not a new algebra; the divide-and-conquer swap is the same as an endian conversion. |
| Binary Watch | LC **401** | #2 · #13 | Popcount of a 10-bit hours\|minutes mask. |
| Letter Case Permutation | LC **784** | #13 | Masks over the alpha positions. |
| Binary Number with Alternating Bits | LC **693** | #1 | `n ^ (n >> 1)` is a run of 1s, then a power-of-two check. |
| Prime Number of Set Bits | LC **762** | #2 | Popcount, then a tiny is-prime. |
| Binary Gap | LC **868** | #2 | Walk set bits, track gaps. |
| Number of Steps to Reduce a Number to Zero | LC **1342** | #1 · #2 | Right-shift on even, subtract 1 on odd; the count is popcount plus the bit-width. |
| Sort Integers by Number of 1 Bits | LC **1356** | — | Popcount as a sort key. [[Sorting & Custom Comparators]] #9. |
| Subsets II | LC **90** | #13 | Dedup, which is [[Backtracking]] #4, not a bit idea. |
| Next number with the same popcount *(snoob)* | *classic* | #1 · #13 | Next permutation of the bit pattern. Real, almost never asked; named so #13 is not mistaken for it. |
| UTF-8 Validation | LC **393** | #17 | Leading ones encode length; named in the entry. |
| Integer Replacement | LC **397** | — | Recursion / greedy on even-odd; bits help, the idea is a search. |
| Concatenation of Consecutive Binary Numbers | LC **1680** | #16 | Track the current bit-width as you cross powers of two. |
| Minimize XOR | LC **2429** | #1 | Greedy bit placement under a popcount constraint. |
| Chalkboard XOR Game | LC **810** | #3 | If the total XOR is 0 you already lost; otherwise first player wins for `n` even. Game theory on #3. |
| Josephus when `k = 2` | *classic* | — | Rotate the binary representation left by one. [[Math & Number Theory]] #20 is the general recurrence; this is a cute special case, not a second idea. |
| SOS DP · bitset convolution · bitset Floyd | *CP* | — | Out of scope per [[README]]. #14 is the interview-facing fragment of SOS. |
| Hardware popcount / SWAR tricks | — | #2 | Know `__builtin_popcount` exists; do not write the SWAR version in an interview. |

---

## Self-audit

**Borderline calls, and which way I went**

- **#3 native here despite [[Arrays]] #3 already owning Missing Number.** Arrays owns the *aggregate-identity-instead-of-storage* move (sum or XOR). This file owns the *algebra of XOR* that that move depends on. Cross-linked both ways rather than merged, because a reader drilling bits would not start in Arrays, and a reader drilling Arrays should not have to learn Single Number III to understand Missing Number.
- **#4 and #5 kept separate.** Both are "XOR is not enough." Split because the repairs are different ideas: #4 *projects onto a distinguishing bit* and reuses #3 twice; #5 *changes the modulus* and needs a state machine. Merging them produces "harder Single Number", which is a difficulty, not an idea.
- **#7 native despite being the contribution technique.** [[Math & Number Theory]] #25 is the general summation-swap. Kept here because the *reason the swap is legal* is bit-independence, which is this file's central axis, and because Total Hamming is the problem people will search for under bits, not under number theory.
- **#11 and [[Sliding Window]] #13 kept as a native / ↗ pair.** #11 is "a monotone bitwise chain is short"; #13 is "replace a non-invertible window aggregate with per-bit counts." Related, not the same. The first is a counting argument on nested intervals; the second is a data-structure repair.
- **#12 absorbing LC 1611 rather than splitting.** Inverse Gray *is* the encoding read backwards. A second entry would be a named-problem duplicate.
- **#16 and #1 kept separate.** I nearly folded two's complement into the toolkit. Split because #1 is the identities you use and #16 is *why they are true, and the language-level ways they lie to you*. The signed-shift bugs do not belong in a Power-of-Two entry.
- **#17 (packing) included, despite being "just a rolling hash in bits."** The Strings file is unwritten. Until it exists, this is the home for "the word *is* the window." Flagged to become a ↗ when Strings is written.
- **#18 (windowed flips) included by 4B, not by the first pass.** See below.
- **Snoob, UTF-8, Josephus-k=2, reverse-bits all excluded.** Reverse bits was the closest call — a named LeetCode easy — and I put the divide-and-conquer swap in the exclusion rather than as an entry because it is an implementation of #16, not a new algebra.

**Naming check.** Four retitles. #1 was drafted as "Power of Two", which hides the two identities; it is now *the two identities, and power-of-two is the corollary*. #4 was drafted as "Single Number III", a sequel title; it is now *when two values survive, split on a bit they disagree on*. #10 was drafted as "Bitwise AND of Numbers Range", a problem name that reads like an implementation; it is now *AND of a range is the common prefix*. #11 was drafted as "Bitwise ORs of Subarrays"; it is now *a monotone OR-chain is at most 32 long*, which is what a differently-dressed version would share. #14 was kept as *iterate all submasks*, which is already the idea.

**Step 4B — reverse sweep**

Twenty plain-language descriptions navigated against the family headings, in interviewer register and with no mechanism vocabulary. **One failure, which added an axis:**

- **"I can flip any `k` consecutive bits, make the whole array zeros, minimise the number of flips"** landed on #12 and was wrong. #12 flips a *prefix* (everything below a cutoff). This flips a *sliding window*, and the correctness argument is a forced greedy at the leftmost defect plus a difference array, not an encoding. The axis my draft table was missing is *granularity of the operation*: I had "one bit" and, inside #12, prefix flips, with no value for **a window of `k` consecutive bits**. That is **#18**.

Two collisions, both checked and cleared. "Count the 1-bits" reaches #2 and #8, correctly — one number versus every number in `0…n`. "Use an int as a set" reaches #13, #14 and #15, which is the intended fan-out (universe / submasks of one mask / properties of an object).

Descriptions that resolved cleanly, worth recording as the ones this file is for: "every value twice except one"; "every value twice except two"; "every value three times except one"; "AND of every integer between `L` and `R`"; "as I extend a subarray the OR only grows, how many distinct values"; "list every subset of this subset, not of the whole universe"; "why is `n & −n` the lowest set bit"; "two words share no letters."

**Step 4C — inward sweep**

- **4C-iii (hedges) first.** [[Math & Number Theory]]'s audit named this file explicitly: *"a Bit Manipulation basis will want XOR properties, `lowbit`, subset enumeration and popcount tricks. Expect this file's M5 and M6 to be re-cut then."* Honoured: Power of Two / Four is now #1 here (Math's exclusion is retargeted); "add without operators" is #9 (Math had collapsed it into M1); Single Number's ↗ now points here rather than only at Arrays. M5 and M6 did **not** need a re-cut — digital root and Josephus are not bit ideas, and the hedge overstated the overlap. Recorded because a hedge that names the wrong families is as misleading as a stale gap flag.
- **4C-i (reciprocity).** Inbound references from [[Arrays]] #3, [[Tries]] #7, [[Prefix Sums & Difference Arrays]] #2 and ↗ bit-level counts, [[Sliding Window]] #13, [[Dynamic Programming]] D9, [[Graphs]] #8, [[Backtracking]] #6, [[Segment Trees]] #2 all now have ↗ rows. Confirmed against those files' tables before adding — the Heap/Prim false-positive rule.
- **4C-ii (orphans).** Single Number I/II/III, Range AND, Gray code, Counting Bits, Total Hamming, power-set-via-bits, and submask enumeration were absent from the basis as *developed* ideas (several were named in passing). Native here, as expected when the topic itself was the gap. LC 995 was the only well-known problem that was in no file and also missing from the first draft — 4B caught it rather than 4C-ii, which is the recall-dependence 4C-ii still has.

**What I am uncertain about**

- **The Strings boundary.** #17 is a rolling encoding. When a Strings (KMP / Z / rolling hash) file appears, this should probably become a ↗, keeping only "the alphabet packed into an int" as the bit-specific half.
- **Whether #8 belongs in [[Dynamic Programming]] instead.** It is a DP recurrence. Kept because the *state* is "the bits of `i`" and the jump is a bit operation; a reader drilling bits will meet Counting Bits long before they meet DP recurrences. Flagged.
- **SOS DP.** Excluded on scope. #14 is the fragment interviews actually want. If a quant / HFT loop starts asking SOS, this file's tail needs an entry, not a row in exclusions.
- **Recall is thinnest on signed-shift behaviour across languages.** #16 is drawn from Java and C++. Python has no width and no arithmetic shift, which is its own gotcha (unbounded ints, `n & −n` still works because of the language's definition of `−`). Worth a sentence if this file is ever used from Python exclusively.
- **LC 1521 / "OR closest to target"** may deserve to be native next to #11 rather than an exclusion of it. The extra idea is a two-pointer or stack of the ≤32 distinct prefix ORs plus a closest-value query. Borderline; currently treated as #11 plus a search.

**Completeness confidence: ~88%.** K1–K5 I am confident about: the axes (independence, algebra, how you iterate, multiplicity) are the right ones, and 4B only had to add granularity of the operation. The uncertainty is the porous boundary with Math (which had been absorbing this topic), with DP (bitmask and Counting Bits), and with the unwritten Strings file. K6 is the family most likely to be re-cut.

## Related Notes

- [[README]]
- [[Math & Number Theory]]
- [[Arrays]]
- [[Prefix Sums & Difference Arrays]]
- [[Sliding Window]]
- [[Tries]]
- [[Dynamic Programming]]
- [[Graphs]]
- [[Backtracking]]
- [[Segment Trees]]
- [[Sorting & Custom Comparators]]
