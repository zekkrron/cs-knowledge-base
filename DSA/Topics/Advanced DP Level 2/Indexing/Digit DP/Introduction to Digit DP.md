---
tags: [dsa/indexing, status/draft]
created: 2026-08-01
---
# Introduction to Digit DP

> [!abstract] Quick-lookup index — digit DP problems mapped to their core learnings.

## 1. Digit Sum Divisibility

Count integers from 1 to K whose digit sum is divisible by D. K can have up to 10,000 digits. Answer mod 10^9+7. Constraints: D ≤ 100.

- **K is a giant string → Digit DP.** Whenever the upper bound is too large for iteration and the condition is about digit properties, build the number digit-by-digit from left to right using recursion + memoisation.
- **State: `dp[idx][rem][tight]`.** `idx` = current digit position, `rem` = digit sum so far mod D, `tight` = whether the prefix built so far exactly matches K's prefix (restricts the next digit choice).
- **The tight flag mechanic.** If tight = 1, the next digit can go from 0 to `K[idx]`. If tight = 0, you're free to pick 0–9. Pass `tight & (d == limit)` to the next call — once you place a digit smaller than K's digit, you're free for all remaining positions.
- **Subtract 1 for the zero case.** Digit DP naturally counts 0 as valid (digit sum 0 is divisible by anything). Problem asks for 1 to K, so subtract 1. Use `(ans - 1 + MOD) % MOD` to avoid negative remainders.
