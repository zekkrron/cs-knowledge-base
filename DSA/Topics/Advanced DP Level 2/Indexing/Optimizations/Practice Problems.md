---
tags: [dsa/indexing, status/draft]
created: 2026-07-28
---
# Practice Problems

> [!abstract] Quick-lookup index — each question mapped to its core learnings.

## 1. Bugs in Code

N people write exactly M lines of code total. Person i introduces A[i] bugs per line. Count the number of distinct plans (X1, X2, ..., XN) where Xi ≥ 0, sum = M, and total bugs ≤ B. Answer mod 10^9+7. Constraints: N, M ≤ 500.

- It's an **unbounded knapsack** in disguise. Programmers are items, M lines is capacity, B bugs is the weight limit. "Unbounded" because one programmer can write 0 to M lines.
- The naive 3D DP (`dp[i][lines][bugs]`) at 500×500×500 blows the 256 MB memory limit. Use a **rolling array** (`dp[2][501][501]`) — you only ever need the current and previous programmer's row.
- The unbounded mechanic: the inner loop reads from the **current row** (`dp[p][j-1][k - A_i]`), not the previous row. Because `j` iterates forward, each state builds on itself — letting the same programmer write one more line on top of what they already wrote. That's what gives the "infinite supply" behaviour.

## 2. Sky

N stars on a grid (X, Y ≤ 100) each with brightness S (0 ≤ S ≤ C ≤ 10). Brightness increments by 1 each second, resets to 0 after C. Given Q queries, each asks total brightness inside a rectangle at time M. Constraints: N, Q ≤ 10^5, M ≤ 10^9.

- **Detect the trap first.** The naive instinct is to track each star individually per query — that's O(Q·N) = 10^10, instant TLE. But the constraints scream at you: grid is only 100×100, brightness maxes at 10. Huge entity count + tiny state space = stop tracking entities, start grouping by state. That's the **inverted thinking** trigger.
- **3D prefix sum.** Build a separate 2D prefix sum grid for each possible initial brightness (0 to C). `dp[x][y][s]` = count of stars in rectangle (1,1) to (x,y) that started at brightness s. Query any rectangle in O(1) via inclusion-exclusion. You precompute once, then answer all queries without touching individual stars.
- **Modulo time-shift.** Since brightness cycles (increments by 1 each second, resets to 0 after C), a star starting at brightness s has brightness `(s + T) % (C + 1)` at time T. Per query, loop through 11 brightness levels, multiply count-in-rectangle by time-shifted brightness, sum. O(C) per query instead of O(N).

## 3. Distinct Subsequences III

Given a string s of length n (≤ 100), find the minimum total cost to collect k (≤ 10^12) distinct subsequences into a set S. Cost of picking a subsequence of length L is n − L.

- **Greedy on length — longest first.** To minimise total cost, iterate length from `n` down to `0`. At each length `l`, count how many distinct subsequences of that length exist, take as many as you still need (up to remaining `k`), then move shorter. The counting is where the real work lives.
- **Avoiding duplicates — always pick the rightmost occurrence.** The problem is counting distinct subsequences without overcounting. If you want a subsequence ending in character `c`, always anchor to the rightmost `c` in the current prefix. Walk from right to left, stop at the first `c` you hit, claim it as the ending, then recurse left for the rest. This forces one canonical way to build each unique subsequence — no duplicates.
- **The counting function: `rec(i, l, c)`.** Reads as "how many distinct subsequences of length `l`, ending with character `c`, can be formed using the first `i` characters?" You loop `c` over all 26 characters, and for each, find the rightmost occurrence in `s[0..i]`, anchor there, then call `rec(anchor - 1, l - 1, *)` summing over all characters for the prefix. This recursion naturally enforces the anchoring rule at every step.
- **3D DP state: `dp[i][l][c]`.** Memoises `rec(i, l, c)`. Adding the final-character dimension is what gives you control over the anchoring rule and prevents the blind overcounting that a 2D DP would suffer.
- **Cap values at k to avoid overflow.** Subsequence counts can exceed 2^100 — way past 64-bit limits. But since you only need k (≤ 10^12) of them, cap every DP value at k immediately after addition. You only care whether the count is ≥ k, not the exact astronomical number. Skip this and you overflow into garbage.

## 4. Mountain Arrays

Given N (≤ 1000) distinct integers and a max adjacent gap K, count the number of ways to reorder them into a valid mountain sequence (strictly increasing then strictly decreasing, peak cannot be at endpoints) where consecutive elements differ by at most K. Answer mod 10^9+7.

- **Sort descending, build top-down.** Don't try to guess the peak. Sort the array largest-first — the biggest element is always the peak. Every subsequent element extends either the left or right slope downward. The problem becomes: for each element, choose left or right.
- **State compression to 2D.** Naively tracking both slope bottoms gives O(N³). But since you process in descending order, the last element you placed is always the bottom of the "current" slope. You only need to remember the bottom of the *other* slope. State: `dp[i][j]` — processing element i, opposite slope ends at element j.
- **Two transitions per element.** For element A[i]: (1) place on same side as previous — valid if `A[i-1] - A[i] ≤ K`, transition to `dp[i+1][j]`. (2) swap sides — valid if `A[j] - A[i] ≤ K`, transition to `dp[i+1][i-1]` (previous becomes the new opposite).
- **Force asymmetry, then multiply by 2.** A mountain has mirror symmetry. Force A[1] (second largest) onto the left slope, start DP from A[2] with `f(2, 0)`. Multiply final answer by 2 to account for the mirror.
- **Base case: reject single-slope paths.** If at i == N and j == 0, everything went on one side — that's a ramp, not a mountain. Return 0. Otherwise return 1.

## 5. The Witcher

Given N (≤ 10^5) rooms with A[i] monsters each (A[i] ≤ 10^7), find the longest subsequence of rooms you can clear such that any two consecutively cleared rooms share a prime factor (gcd > 1).

- **DP on prime factors, not indices.** Naive LIS-style `dp[i]` checks all previous indices — O(N²) TLE. Since the link condition is a shared prime, flip the state: `dp[p]` = longest chain ending with any multiple of prime p. This makes transitions O(number of primes of A[i]) instead of O(N).
- **SPF sieve for fast factorisation.** Trial division per element is O(√A[i]) ≈ 3×10³ — borderline. Precompute a Smallest Prime Factor array up to 10^7 using a sieve. Then factorise any number in O(log A[i]) by repeatedly dividing by its SPF.
- **Update logic.** For current element A[i]: extract its distinct primes, query `max(dp[p])` across those primes to get the best chain length T, then set `dp[p] = T + 1` for all its primes. This extends the longest reachable chain through any shared factor.
- **Deduplicate primes per element.** A number like 12 = 2×2×3 would query/update dp[2] twice if you're not careful, inflating the chain. Extract primes into a set (or skip duplicates during SPF division) so each prime is processed exactly once per element.

## 6. Students Happiness

Assign up to N (≤ 10) ranks to M (≤ 50) students to maximise total happiness. Each rank goes to at most one student. Students without a rank get 0 happiness.

- **Bitmask the smaller set, iterate on the larger.** Two sets to match, one is tiny (N ≤ 10). That's the bitmask trigger. Encode which ranks are taken as a binary integer (10 bits = 1024 states). Iterate over students (the bigger set, up to 50) one by one — for each student you decide: skip, or assign an available rank.
- **State: `dp[pos][mask]`.** `pos` = current student index, `mask` = which ranks are already claimed. For each student, either skip (`dp[pos+1][mask]`) or try every rank where bit is 0: `dp[pos+1][mask | (1 << i)]` + happiness[pos][i]. Take the max.
- **Bitwise checks.** Rank i available? `(mask & (1 << i)) == 0`. Claim it? `mask | (1 << i)`. Simple and O(1) per rank.
- **Complexity is tiny.** O(M · 2^N · N) = 50 × 1024 × 10 ≈ 5×10^5. Passes comfortably even with T = 200.
