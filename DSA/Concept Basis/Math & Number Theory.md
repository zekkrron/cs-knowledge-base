---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-27
---
# Concept Basis — Math & Number Theory

> [!abstract] Minimal spanning set for problems where **the structure of numbers replaces a search**. One entry per **new idea you have to learn**. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!tip] **The single reflex this topic is trying to build.** Almost every entry here is the same move: *the naive loop is `O(n)` or `O(answer)`, and a fact about arithmetic collapses it to `O(log n)`, `O(√n)` or `O(1)`.* So when a problem's input bound is `10⁹` or `10¹⁸` with no array in sight, the question is never "how do I iterate faster" — it is **"what property of these numbers am I not using?"** That is why the axes below are mostly about *what you are allowed to assume*, not about data structures.

## Mechanism axes

| Axis | Values |
|---|---|
| **What the object is** | one integer · a pair, so **gcd** enters · a range `1…n` · the **digits** of a number · a residue class · an exact fraction · a lattice point |
| **Size of the input** | fits in 32 bits · fits in 64 bits · **exceeds 64 bits, so arithmetic becomes string work** · unbounded, so the answer is modular |
| **What is asked** | a value · a **count** · a yes/no · every divisor or factor · a construction · a probability |
| **What replaces brute force** | a closed form · **doubling** (`O(log n)`) · the `√n` bound · a sieve (`O(n log log n)`) · precompute then answer `O(1)` · an **invariant** · a theorem |
| **Where the overflow is** | an intermediate product · a factorial · an exponentiation · the `lcm` of two large numbers |
| **Under a modulus** | `+` and `×` are free · **`÷` needs an inverse** · a negative result needs normalising · the modulus is prime, or it is not |
| **Direction of counting** | iterate the objects and sum their parts · **iterate the parts and count the objects each appears in** |
| **What breaks** | `int` overflow before you notice · `double` losing precision above `2⁵³` · `%` returning negative in C++ · a sieve you cannot allocate · assuming the modulus is prime |

## Shape of this topic

```
M1  Doing the arithmetic yourself         4 ideas
M2  Euclid, and what follows from it      3 ideas
M3  Primes, factors, divisors             3 ideas
M4  Counting                              6 ideas
M5  Digits and bases                      3 ideas
M6  Invariants instead of search          4 ideas
M7  Integers in geometry                  1 idea
M8  Randomness                            1 idea
M9  Modular arithmetic in practice        1 idea
                                          + 7 cross-listed ↗
```

**26 native entries, plus 7 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** #25 and #26 were added by later sweeps and sit inside M4.

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| **Binary exponentiation** · fast power | #1 |
| Matrix exponentiation *(Fibonacci in `O(log n)`)* | #1 |
| **Euclid's algorithm** *(gcd)* | #5 |
| Extended Euclid · **Bézout's identity** | #6 |
| **Fermat's little theorem** · modular inverse | #7 |
| **Sieve of Eratosthenes** | #9 |
| Smallest-prime-factor sieve | #9 |
| **nCr mod p** *(factorial + inverse tables)* | #11 |
| **Stars and bars** · combinations with repetition | #26 |
| **Pascal's Triangle** | #12 |
| **Catalan numbers** | #13 |
| **Inclusion–exclusion** | #14 |
| Contribution technique · **linearity of expectation** | #25 |
| Bijective base-26 *(Excel columns)* | #15 |
| **Digital root** | #16 |
| **Legendre's formula** *(trailing zeros of `n!`)* | #17 |
| **Josephus problem** | #20 |
| **Lagrange's four-square theorem** | #21 |
| **Rejection sampling** | #23 |

---

## M1 · Doing the arithmetic yourself

The operators are taken away, or the numbers outgrow them.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Pow(x, n) | LC **50** | **Doubling: `aᵏ` needs `O(log k)` multiplications, because squaring halves the exponent.** Read `k` in binary and multiply in the base whenever the bit is set. The reason this is the file's first entry is that the same doubling reappears everywhere — `aᵏ mod m` is the workhorse of #7 and #11, and replacing the number by a **matrix** turns any linear recurrence into `O(log n)`, which is how you get the `n`-th Fibonacci number for `n = 10¹⁸`. Edge cases that are the actual test: negative `n` (invert), and `n = INT_MIN` (negating it overflows). |
| 2 | Divide Two Integers | LC **29** | **Division without `/`: subtract the largest shifted copy of the divisor that still fits, repeatedly.** Doubling again, read from the other end — you are building the quotient's binary representation one bit at a time, so it is `O(log n)` rather than the `O(quotient)` of repeated subtraction. Kept as its own entry because the *overflow discipline* is the content: work in `long`, or negate both operands to be negative rather than positive, since `−2³¹` has a magnitude that `int` cannot hold and `INT_MIN / −1` is the one case that must be special-cased. A favourite precisely because it is impossible to pass without thinking about representation limits. |
| 3 | Multiply Strings · Add Strings | LC **43** · **415** | **When the number will not fit in any primitive, arithmetic becomes digit-array work with explicit carries.** Addition is a single reversed pass; multiplication needs the index identity that is the real lesson — the product of digits `i` and `j` lands in positions `i + j` and `i + j + 1`, so you accumulate into an `m + n` buffer and normalise carries once at the end rather than shifting and adding `n` times. New because you are implementing the representation, not using it, and because the leading-zero strip at the end is where the wrong answers live. |
| 4 | Reverse Integer | LC **7** | **Check for overflow *before* you cause it, because after the fact the evidence is gone.** In C++ signed overflow is **undefined behaviour**, so the test has to be `res > INT_MAX / 10` (or equal, with a digit check) *before* the multiply. New in general form: **detect a boundary by inverting the operation rather than by performing it**, which is also why you compare `a > limit / b` instead of `a * b > limit`. Small, universally applicable, and the thing an interviewer is actually watching for on any integer problem. |

## M2 · Euclid, and what follows from it

| # | Problem | Source | The new idea |
|---|---|---|---|
| 5 | Greatest Common Divisor of Strings | LC **1071** | **`gcd(a, b) = gcd(b, a mod b)`, and the reason it works is that the *set of common divisors is unchanged* by subtracting one from the other.** Which is why the identical algorithm finds the shortest repeating unit of two strings — the structure is the same, only the operation differs. That transfer is why this problem rather than a numeric one leads the family: holding gcd as **"repeated structure"** instead of "biggest common factor" is what lets you recognise it in disguise. Also the home of the `lcm` overflow rule: write `a / g * b`, never `a * b / g`, because the product overflows and the divided form does not. |
| 6 | Water and Jug Problem | LC **365** | **Bézout: `ax + by = c` has an integer solution exactly when `gcd(a, b)` divides `c`.** So the entire jug puzzle — which looks like a BFS over states — collapses to one gcd check. Extended Euclid is what produces the actual coefficients `x` and `y` alongside the gcd, by unwinding the recursion. New because it is the file's cleanest example of the opening reflex: **a search you were about to write is answered by a divisibility condition.** The coefficients matter in their own right, since they are one of the two ways to build #7. |
| 7 | Modular inverse | *classic — the prerequisite for #11; practise on any "answer mod 1e9+7" problem* | **Division does not exist under a modulus; you multiply by an inverse instead, and `a⁻¹ = a^{p−2} mod p` when `p` is prime.** That is Fermat's little theorem plus #1, so it costs `O(log p)`. When the modulus is *not* prime, Fermat does not apply and you need extended Euclid (#6), which works whenever `gcd(a, m) = 1`. New because it is the operation people assume they have and do not — the symptom is a formula with a `/` in it that silently produces garbage under `%`. **The check to run every time: is my modulus prime?** `10⁹ + 7` is, which is exactly why every problem statement picks it. |

## M3 · Primes, factors, divisors

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Trial division to `√n` | *classic — primality and full factorisation* | **Divisors come in pairs `(d, n/d)`, so one of every pair is `≤ √n` and checking up to `√n` is complete.** That single argument gives primality in `O(√n)`, the full factorisation in `O(√n)` (divide out each factor as you find it, and whatever remains above 1 is itself prime), and divisor enumeration in `O(√n)` — remembering not to emit the square root twice for a perfect square. New because the bound is a *proof*, not an optimisation, and because knowing the pairing is what lets you find "the two factors of `n` closest together" without any search. |
| 9 | Count Primes | LC **204** | **A sieve inverts the question: instead of testing each number, mark the multiples of each prime.** `O(n log log n)` for all primes below `n`, with two implementation facts that matter — start marking at `p·p` because smaller multiples already have a smaller prime factor, and iterate `p` only to `√n`. The upgrade most people never learn is the real entry: **store the *smallest prime factor* of every number instead of a boolean**, and you can then factorise any number `≤ n` in `O(log n)` by walking down repeatedly. That turns "factorise a million queries" from hopeless into linear, and it is the same precompute-then-answer-`O(1)` shape as [[Prefix Sums & Difference Arrays]] #1. |
| 10 | Divisor counts, two ways | *classic — `Σ 1/d` enumeration; LC **1390** is a small instance* | **Either factorise and multiply the exponents, or sieve over multiples for all `n` at once.** From `n = Π pᵉ`, the number of divisors is `Π (e + 1)` and their sum is `Π (p^{e+1} − 1)/(p − 1)` — closed forms, so no enumeration at all. But when you need the answer for *every* `n ≤ N`, the better move is the **harmonic enumeration**: for each `d`, walk its multiples and credit them. Total work is `N/1 + N/2 + N/3 + … = O(N log N)`, and that bound is the entry — it is the same trick as a sieve applied to any per-divisor accumulation, and it is why "for every `i`, do something with its multiples" is affordable when it looks quadratic. |

## M4 · Counting

| # | Problem | Source | The new idea |
|---|---|---|---|
| 11 | Unique Paths | LC **62** | **`nCr` under a modulus needs precomputed factorials *and* their inverses.** The grid path count is `C(m + n − 2, n − 1)` outright, which is the point — a DP over the grid is `O(mn)` and the binomial is `O(1)` after an `O(N)` precompute. The toolkit is the thing to own: `fact[]` by a forward pass, `invFact[]` by computing `invFact[N]` once with #7 and then walking *downward* with `invFact[i-1] = invFact[i] · i`, which costs one modular inverse instead of `N` of them. New because the whole family of "count arrangements mod `p`" problems is unreachable without it. |
| 12 | Pascal's Triangle · Pascal's Triangle II | LC **118** · **119** | **Two ways to get a binomial without factorials, and they solve different problems.** A whole row from the previous one is `C(n, k) = C(n−1, k−1) + C(n−1, k)`, which needs no division and no modulus — and it can be done **in place if you iterate the row right to left**, the same direction argument as the 1D knapsack ([[Dynamic Programming]] #55). A *single* entry is better computed multiplicatively as `C(n, k) = Π (n − k + i)/i`, multiplying then dividing at each step so the running value stays an exact integer and never reaches `n!`. New because it is the overflow-free route when there is no modulus to hide behind, which is exactly when #11's toolkit is unavailable. |
| 13 | Unique Binary Search Trees | LC **96** | **Catalan numbers, and the recurrence's *shape* is the recognisable part: `Cₙ = Σ Cᵢ · C_{n−1−i}`.** Split on the root, multiply the independent left and right counts, sum over every split. Worth an entry because the same numbers count valid parenthesis strings, triangulations of a polygon, monotone lattice paths under the diagonal, and stack-sortable permutations — so **recognising the recurrence tells you the answer before you have solved the problem.** The closed form `C(2n, n)/(n+1)` is the follow-up, and it needs #11 to evaluate under a modulus. |
| 14 | Ugly Number III · Nth Magical Number | LC **1201** · **878** | **Inclusion–exclusion: count what each condition allows, subtract the double-counted overlaps, and the overlap of "divisible by `a`" and "divisible by `b`" is "divisible by `lcm(a, b)`".** So the count of numbers `≤ x` divisible by any of `a, b, c` is a signed sum over the `2³ − 1` non-empty subsets, with `lcm` as the intersection operator. New because the counting function is what a binary search on the answer then consumes (↗ below) — **the pair "monotone counting function + binary search" is the standard way to answer "the `n`-th number with property P" without generating anything.** Watch `lcm` overflow, per #5. |
| 25 | Count Unique Characters of All Substrings | LC **828** | **Swap the order of summation: instead of iterating the objects and summing their parts, iterate the parts and count how many objects each one appears in.** Summing over all `O(n²)` substrings is hopeless; asking instead "for each character occurrence, how many substrings does it contribute to?" gives a per-element formula from its neighbouring occurrences, and the total falls out in `O(n)`. The cleanest instance to hold is `Σ over all subarrays of their sums = Σ a[i] · (i+1) · (n−i)`, where the factor counts the subarrays containing index `i`. New and unreasonably broad — it is what makes "sum of subarray minimums" tractable (↗ below, with a monotonic stack supplying the neighbour boundaries), and **its probabilistic form is linearity of expectation**: `E[Σ Xᵢ] = Σ E[Xᵢ]`, so you price each element's contribution independently and never reason about the joint distribution at all. Same move, two vocabularies. The bitwise form — Total Hamming Distance, summing over 32 independent bit positions — is [[Bit Manipulation]] #7. |
| 26 | Count Sorted Vowel Strings | LC **1641** | **Stars and bars: indistinguishable items into bins is a binomial, not a search.** Non-negative solutions of `x₁ + … + xₖ = n` number `C(n + k − 1, k − 1)`. Two transformations you must own: **`xᵢ ≥ 1`** is `yᵢ = xᵢ − 1`, so `C(n − 1, k − 1)`; **upper bounds** `xᵢ ≤ c` is inclusion–exclusion (#14) on top of this, not a third identity. LC 1641 is combinations-with-repetition: a sorted length-`n` word over 5 letters is a non-decreasing sequence, which is exactly `C(n + 4, 4)`. New against #11 because #11 is *how to evaluate* `nCr`; this is *which `nCr` you are looking at* when the objects are identical. Derangements stay out — they are #14 on permutations, asked rarely. |

## M5 · Digits and bases

| # | Problem | Source | The new idea |
|---|---|---|---|
| 15 | Excel Sheet Column Title | LC **168** | **A numbering system with no zero digit is *bijective* base-26, and it is off by one from ordinary base conversion.** `A` is 1, not 0, so `Z` → `AA` rather than `Z` → `BA`; the fix is to decrement before taking the remainder each step. New because it is the clean demonstration that "convert to base `b`" has a hidden assumption — that the digit set includes zero — and that violating it shifts every step. The general skill underneath is plain digit extraction by repeated `% b` and `/ b`, plus its inverse, which also covers negative bases (LC 1017) and base-7 style conversions. |
| 16 | Add Digits | LC **258** | **Repeated digit-summing has a closed form: the digital root is `1 + (n − 1) mod 9`, because `10 ≡ 1 (mod 9)` makes a number congruent to its digit sum.** So the loop you were about to write is one expression. Kept because it is the file's purest example of the opening reflex, and because the mod-9 invariant has independent uses — it is the "casting out nines" check, it decides divisibility by 3 and 9, and the same reasoning with `10 ≡ −1 (mod 11)` gives the alternating-sum test for 11. **The transferable question is: does this repeated operation have a fixed point I can name?** |
| 17 | Factorial Trailing Zeroes | LC **172** | **Legendre's formula: the exponent of a prime `p` in `n!` is `⌊n/p⌋ + ⌊n/p²⌋ + ⌊n/p³⌋ + …`.** Trailing zeros are the count of 5s (2s are always more plentiful), so the answer is that sum in `O(log n)` and `n!` is never computed. New because the counting argument is genuinely non-obvious — each term counts the multiples contributing *at least one more* factor of `p` — and because the same formula is what tells you whether `nCr` is divisible by `p`, which is the follow-up when the modulus is small and not coprime to the factorials. |

## M6 · Invariants instead of search

> [!tip] The family's shared move: **you were going to explore a state space, and a property of the numbers tells you the answer directly.** Worth pausing on any "both play optimally" or "repeat until stable" problem to look for one.

| # | Problem | Source | The new idea |
|---|---|---|---|
| 18 | Fraction to Recurring Decimal | LC **166** | **Bounded state forces a cycle, and remembering *where* each state first appeared locates it.** Long division has only `denominator` possible remainders, so a repeat must occur within that many steps — keep a map from remainder to output position and the repeating block is delimited the moment a remainder recurs. New in general form and this is the framing to keep: **if a process has finitely many states, it is eventually periodic, so find the cycle and index into it rather than simulating.** That is how you evaluate a sequence at position `10¹⁸`, and the same argument in a different costume is Floyd's cycle detection (↗ below). |
| 19 | Nim Game | LC **292** | **Find the invariant instead of searching the game tree: you lose exactly when `n mod 4 == 0`.** The argument is short — from a multiple of 4 every move hands the opponent a non-multiple, and from a non-multiple you can always hand back a multiple — and it replaces a DP over `n` states with one modulo. New because the *technique* is the reasoning pattern, not the formula: compute the outcome for small `n` by hand, spot the period, then prove it with a strategy-stealing or parity argument. **Almost every "both players play optimally" interview problem is either this or a short DP, and checking for the invariant first is free.** |
| 20 | Find the Winner of the Circular Game | LC **1823** | **Josephus: solve it in the *survivor's* coordinates, and the recurrence is `f(n) = (f(n−1) + k) mod n`.** Simulating with a list is `O(nk)`; the recurrence is `O(n)` and comes from a reindexing observation — after the first elimination you have the same problem on `n − 1` people, but the numbering has rotated by `k`. New because the idea is **re-expressing a problem in coordinates relative to the answer**, which is a genuinely different move from any other entry here, and because the off-by-one between 0-indexed and 1-indexed answers is the whole implementation risk. |
| 21 | Perfect Squares | LC **279** | **A theorem can collapse a DP: Lagrange says every natural number is the sum of at most four squares, and Legendre's three-square theorem says which need exactly four.** So the answer is 1, 2, 3 or 4, decided by `O(√n)` checks, where the coin-change DP is `O(n√n)`. New because it is the file's strongest statement of the opening reflex — you are not optimising the DP, you are deleting it — and because the honest lesson is about **knowing that a closed-form result exists**, since no amount of algorithmic skill derives Lagrange at the whiteboard. The DP route is the answer you should still be able to write (↗ below); this is the answer that ends the conversation. |

## M7 · Integers in geometry

| # | Problem | Source | The new idea |
|---|---|---|---|
| 22 | Max Points on a Line | LC **149** | **Never represent a slope as a floating-point number; represent it as a gcd-reduced integer pair with a canonical sign.** `(dy, dx)` divided by `gcd(dy, dx)`, with the sign pushed onto a fixed component, is an exact hashable key — where `dy/dx` as a `double` collides for distinct slopes and separates identical ones, and vertical lines have no value at all. New because the lesson is representational and transfers to every integer-geometry question: **collinearity, parallelism and orientation are all cross-product comparisons, which stay in integers and therefore stay exact.** The same instinct compares two fractions by cross-multiplying rather than dividing. |

## M8 · Randomness

| # | Problem | Source | The new idea |
|---|---|---|---|
| 23 | Implement Rand10() Using Rand7() | LC **470** | **Rejection sampling: build a uniform space larger than you need, and *discard* the leftover instead of folding it in.** Two `rand7()` calls give 49 equally likely outcomes; keep the first 40, map them ten ways, and retry on the other 9 — because folding the remainder back in (say by `% 10`) destroys uniformity, which is the entry's real content. The expected number of retries is a geometric series, so it terminates in `O(1)` expected time despite being unbounded in the worst case, and being able to say that is the follow-up. The same technique samples a point uniformly inside a circle from a bounding square (LC 478), and it pairs with the two sampling ideas in [[Arrays]] #11 and #12. |

## M9 · Modular arithmetic in practice

| # | Problem | Source | The new idea |
|---|---|---|---|
| 24 | The four things that go wrong under a modulus | *concept* | **`%` is not the mathematical mod, and `(a·b) % m` overflows long before `m` does.** Four facts, each of which has cost people a submission. **One:** in C++, `−3 % 5` is `−3`, so any subtraction under a modulus needs `((x % m) + m) % m` — Python is the exception and this is the most common cross-language bug in the basis. **Two:** with `m ≈ 10⁹`, the product of two reduced values reaches `10¹⁸`, which fits in `int64` but *not* in `int32`, so the accumulator type is load-bearing; at `m ≈ 10¹⁸` even `int64` fails and you need `__int128` or a `mulmod`. **Three:** reduce after every operation, not at the end. **Four:** `%` is a division instruction and roughly twenty times the cost of an add, so in a tight loop `if (x >= m) x -= m` after an addition is a real speedup. Not an algorithm, and the most reliably useful entry in the file. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Sqrt(x) · Valid Perfect Square | LC **69** · **367** | Integer square root by binary search on a monotone predicate — and the reason to do it that way rather than casting `sqrt()` to an int is that `double` loses precision above `2⁵³`, so the floating answer can be off by one on large inputs. [[Binary Search]] #1. |
| ↗ | Nth Magical Number | LC **878** | Binary search the answer, with #14's inclusion–exclusion count as the predicate. The canonical composition: **a counting function that is monotone in `x` turns "the n-th such number" into a search.** [[Binary Search]] #7. |
| ↗ | Happy Number | LC **202** | A numeric function iterated until it cycles, detected with Floyd's tortoise and hare in `O(1)` space. #18's bounded-state argument, with cycle detection instead of a map. [[Two Pointers]] #7. |
| ↗ | Single Number | LC **136** | XOR cancellation — the algebra lives in [[Bit Manipulation]] #3; the aggregate-identity-instead-of-storage framing is [[Arrays]] #3. |
| ↗ | Shuffle an Array · Random Pick Index | LC **384** · **398** | Fisher–Yates and reservoir sampling: the other two sampling techniques, both about uniformity proofs rather than about generation from a weaker source. [[Arrays]] #11 · #12. |
| ↗ | Sum of Subarray Minimums | LC **907** | #25's contribution technique at full strength: a monotonic stack supplies each element's dominance boundaries, and the per-element count multiplies out. The most-asked instance of the summation swap. [[Stack and Queue]] S7. |
| ↗ | Count total set bits from 1 to n | *GFG / Striver* | #25 over a complete integer range: one closed form per bit position, `O(log n)`. The reason you can price bits independently is [[Bit Manipulation]] #21. |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Super Pow | LC **372** | #1 | Binary exponentiation with the exponent as a digit array. |
| Fibonacci Number · Climbing Stairs | LC **509** · **70** | #1 | The `O(log n)` route is matrix power; the `O(n)` route is [[Dynamic Programming]] D1. |
| Power of Two · Three · Four | LC **231** · **326** · **342** | — | Power of two/four is [[Bit Manipulation]] #1; power of three is a divisibility check against the largest power in range (#8). |
| Multiply / Add / Subtract without operators | *classic* | — | Shifting and carrying. Addition from XOR-plus-carry is [[Bit Manipulation]] #9; the rest is #2 · #3 here. |
| Palindrome Number | LC **9** | #4 · #15 | Reverse half the digits to dodge overflow entirely. |
| String to Integer (atoi) | LC **8** | #4 | Parsing plus the same pre-multiply overflow guard. |
| Simplified Fractions · Fraction Addition | LC **1447** · **592** | #5 | Reduce by gcd; the arithmetic is unchanged. |
| Minimum Moves to Equal Array Elements II | LC **462** | — | The median minimises absolute deviation. A one-line fact, reached by sorting — [[Sorting & Custom Comparators]] #11. |
| Ugly Number II | LC **264** | — | Merge three multiplied streams; a heap or three pointers, not number theory. [[Heap]]. |
| Count Primes in a range · segmented sieve | *classic* | #9 | The same sieve over a shifted window, using primes up to `√R`. |
| Prime Factorisation of every query | — | #9 | The smallest-prime-factor sieve, named in the entry. |
| Four Divisors | LC **1390** | #10 | Divisor enumeration with a count check. |
| Perfect Number | LC **507** | #10 | Divisor sum by `√n` enumeration. |
| Unique Paths II *(obstacles)* | LC **63** | — | The closed form dies the moment the grid is not free; it is DP again. [[Dynamic Programming]] D4. |
| Combinations · Permutations count | — | #11 | The formula; *listing* them is [[Backtracking]] K1. Stars-and-bars *shape* is #26. |
| Distribute Candies Among Children II | LC **2929** | #26 · #14 | Stars and bars, then inclusion–exclusion for the per-child cap. |
| Generate Parentheses *(count only)* | LC **22** | #13 | Catalan; enumerating them is [[Backtracking]] #11. |
| Number of Ways to Reorder Array to Get Same BST | LC **1569** | #11 · #13 | Multinomial coefficients over subtree sizes. |
| Count Numbers with Unique Digits | LC **357** | #11 | A permutation product per digit length. |
| Divisor Game | LC **1025** | #19 | Parity invariant — the winner depends only on `n` being even. |
| Stone Game *(I)* | LC **877** | #19 | First player always wins; the DP is unnecessary. |
| Nim with XOR *(Sprague–Grundy)* | *classic* | #19 | The general theory. Real, and out of interview scope. |
| Elimination Game | LC **390** | #20 | Josephus-flavoured reindexing on a shrinking range. |
| Excel Sheet Column Number | LC **171** | #15 | The inverse conversion. |
| Roman to Integer · Integer to Roman | LC **13** · **12** | — | A greedy lookup table, not arithmetic. [[Greedy]] #15; the decode direction is a left-to-right scan, not a greedy. |
| Add Two Numbers *(as linked lists)* | LC **2** | #3 | Carry propagation on a list. [[Linked List]] ↗; the reverse-order variant is [[Linked List]] #5. |
| Chinese Remainder Theorem | *classic* | — | Real, genuinely CP. Excluded on scope per [[README]]. |
| Möbius function · Euler's totient sieve | *classic* | #9 · #10 | Multiplicative functions by sieve. Beyond interview scope, same machinery. |
| Miller–Rabin · Pollard's rho | *classic* | #8 | Primality and factorisation past `10¹⁸`. CP only. |
| Convex hull · closest pair | *classic* | #22 | Real computational geometry, and not asked in SDE loops. |
| Probability / expected-value DP | LC **837** · **808** | #25 | Linearity of expectation where it applies; a DP over states where it does not. [[Dynamic Programming]] D12. |

---

## Self-audit

**Borderline calls, and which way I went**

- **The scope of "Math" was the whole decision.** This topic can absorb anything, so the inclusion test was: *does a fact about numbers replace an algorithm here?* That keeps binary exponentiation, sieves, Bézout and Lagrange, and it throws out Roman numerals (a lookup table), median-minimises-deviation (a sorting fact), and probability DP (a DP). It also means M9 exists, which is not mathematics at all — see below.
- **#24 is not an algorithm and I kept it anyway.** Four facts about `%` and overflow, none of them an idea. Justified on the same grounds as [[Sorted Containers & Order Statistics]] S5: a correct derivation that produces a negative index or a silently overflowed product is a failed submission, and this is where the basis says so out loud. If any entry in this file gets used most, it is this one.
- **#12 kept separate from #11**, which I went back and forth on. Both compute binomials. Separated because the *constraint* differs — #11 needs a prime modulus to exist at all, #12 is what you use when there is none and `n!` would overflow — and choosing wrongly gives either a crash or garbage rather than a slow answer.
- **#25 is enormous and is filed as one entry.** The contribution technique and linearity of expectation are the same move in two vocabularies, and I considered splitting them so the probabilistic form was findable on its own. Left merged because splitting would obscure precisely the fact worth learning, which is that they *are* the same move. Flagged in the sweep notes because a reader hunting for "expected value" may not look here.
- **#21 (Lagrange) risks teaching trivia.** Kept because the meta-lesson is real — some problems are closed by a theorem you either know or do not — and because the entry says plainly that you should still be able to write the DP. A file that only taught derivable things would misrepresent this topic.
- **Geometry is one entry.** #22 covers exact integer representation, which is the part that shows up. Convex hull, sweep-line intersection and rotating calipers are excluded on scope; I am fairly confident about that for SDE loops and would revisit it for a graphics or maps team.
- **Pascal's Triangle finally has a home.** It was one of three named questions the [[Arrays]] name sweep found with no destination, which is a small vindication of doing that sweep at all.

**Naming check.** Five retitles. #1 was drafted as "Pow(x, n)" and is now *doubling*, since that is what transfers to matrices and to #2. #10 was "divisor count" and is now *two ways*, because the per-`n` and all-`n` routes are different techniques and the harmonic bound is the transferable one. #17 was "trailing zeros" and is now Legendre's counting argument. #20 was "Josephus" and is now *solve it in the survivor's coordinates*, which is the move rather than the name. **#18 was the useful one:** drafted as "recurring decimals", which buries it — retitled to *bounded state forces a cycle*, and only after that did it become obvious that it answers "evaluate this sequence at position `10¹⁸`", which is how the idea usually arrives.

**Step 4B — reverse sweep**

Thirty-one plain-language descriptions navigated against the family headings. **One failure:**

- **"Sum some quantity over all `O(n²)` subarrays of an array"** landed nowhere. M4 held `nCr`, Catalan and inclusion–exclusion, all of which count *objects*; nothing covered summing a quantity *across* objects by pricing each element's contribution. The axis it exposed was **direction of counting** — iterate the objects and sum their parts, or iterate the parts and count the objects each appears in — which was absent from the table entirely. That is **#25**, and it is the broadest thing in the file: it subsumes "sum of subarray minimums", "sum of all subarray sums", "total Hamming distance", and, in its probabilistic form, every linearity-of-expectation argument.

Three collisions, all checked and cleared. "The `n`-th number divisible by `a` or `b`" reaches #14 and ↗ LC 878, which is the intended pairing of a counting function with a search. "Detect that this process repeats" reaches #18 and ↗ LC 202, correctly separated by whether you need the cycle's *position* or only its existence. "Fewest squares summing to `n`" reaches #21 and the ↗ DP row, which is the trade the entry is about.

**What I am uncertain about**

- **Bit Manipulation overlap, now closed.** The hedge named XOR, `lowbit`, subset enumeration and popcount, and expected M5/M6 to be re-cut. Those ideas are now [[Bit Manipulation]] K1–K5; Power of Two / add-without-operators / Single Number are retargeted above. **M5 and M6 did not need a re-cut** — digital root and Josephus are not bit ideas, and the hedge overstated the overlap. Left visible because a hedge that names the wrong families is as misleading as a stale gap flag.
- **Where probability really belongs.** #25 covers linearity of expectation; conditional-probability DP is excluded to [[Dynamic Programming]] D12, which already exists as a family. That split is defensible but nobody looking for "expected value problems" will guess it lands in two places.
- **Whether #10's harmonic bound is one idea or two.** The closed forms from a factorisation, and the `O(N log N)` sieve over multiples, are arguably unrelated. They are merged because the question "how many divisors" is what sends you to either, and the choice between them is the lesson.
- **Combinatorics recall.** Stars and bars is now #26. Derangements, multinomials and Burnside stay folded into #11 / #14 or out of scope — unlike the algorithmic families there is no curated interview list of identities. If something is still missing from this file, it is a counting identity.
- **#8 has no clean canonical problem.** It is cited as "classic" because LeetCode's primality questions are either trivial or subsumed by the sieve, which is a small symptom of the sourcing gap [[README]] warns about.

**Completeness confidence: ~85%**, still among the lowest in the basis after [[Arrays]]. M1, M2 and M3 are tight and have dense external representation. The uncertainty is concentrated in M4 for the recall reason above. The Bit Manipulation boundary is closed; what remains fuzzy is that this topic is defined by *what mathematics happens to be useful*, which is a fuzzier edge than a mechanism gives you.

## Related Notes

- [[README]]
- [[Binary Search]]
- [[Dynamic Programming]]
- [[Arrays]]
- [[Prefix Sums & Difference Arrays]]
- [[Two Pointers]]
- [[Stack and Queue]]
- [[Backtracking]]
- [[Linked List]]
- [[Sorting & Custom Comparators]]
- [[Bit Manipulation]]
- [[Greedy]]
- [[Strings]]
