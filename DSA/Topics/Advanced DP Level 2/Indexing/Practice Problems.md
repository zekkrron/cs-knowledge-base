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
