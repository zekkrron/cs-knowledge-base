---
tags: [dsa/indexing, status/draft]
created: 2026-08-01
---
# Bitmask DP Applications 2

> [!abstract] Quick-lookup index — bitmask DP problems mapped to their core learnings (continued).

## 1. Minimum Edge Deletion to Make DAG

Given a directed graph with N (≤ 15) vertices as an adjacency matrix, find the minimum number of edges to delete so the graph becomes acyclic (a DAG).

- **DAG = find an optimal topological ordering.** A DAG is just a graph whose vertices can be lined up left-to-right with all edges pointing forward. Any edge pointing backward in your chosen ordering must be deleted. So "min deletions to make DAG" becomes "find the permutation of vertices that minimises backward edges."
- **N ≤ 15 → bitmask over permutations.** Trying all N! orderings is too slow. But the cost of placing the next vertex only depends on *which* vertices are already placed, not their internal order. Compress the permutation search into `dp[vis]` (2^15 = 32768 states) where `vis` is the set of vertices already in the ordering.
- **Penalty calculation.** When placing vertex i next, count edges from i to any vertex already in `vis` — those are backward edges. `penalty = sum of isEdge[i][j] for all j in vis`. Each edge is evaluated exactly once (when its source is placed), so no overcounting.

## 2. Tiling (Domino Tiling)

Tile an N×M grid (N, M ≤ 12) completely with 1×2 dominoes. Count the number of distinct valid tilings.

- **Profile DP (bitmask on grids).** Small grid dimensions → process row by row. The bitmask represents the state of the boundary between the current row and the next.
- **State: `dp[row][mask]`.** Number of valid tilings of all rows up to `row`, given that `mask` describes which columns in the next row are already occupied from above (1 = filled by vertical domino from above, 0 = empty).
- **Row-by-row forward push with DFS transitions.** The approach used here processes one full row at a time and pushes counts forward to the next row. A DFS sweeps columns left to right: if a cell is filled from above, skip; if empty, branch into vertical (sets bit in `nvis` for next row) or horizontal (fills current + right neighbour, no bit set). When DFS reaches column M, push `dp[row][vis]` into `dp[row+1][nvis]`.
- **Difference from Broken Profile (cell-by-cell) DP.** In class, the technique taught is Broken Profile DP where the mask is a sliding window of M bits that advances one cell at a time — no inner DFS needed, transitions are per-cell. The solution here simplifies the DP array to `[row][full_row_mask]` and uses DFS as a temporary engine to generate all valid next-row masks in one sweep. Same result, different granularity: cell-by-cell is more general, row-by-row is simpler for rectangular tiling.
- **Final answer is `dp[N][0]`.** After the last row, the mask must be 0 — no dominoes can protrude past the grid's bottom edge.

## 3. Maximum Happiness Team Formation

N (≤ 15) students must be split into teams of any size. Pair (X, Y) on the same team contributes Happy[X][Y] to total score. Happiness can be negative. Maximise total happiness across all teams.

- **Submask DP for arbitrary partitioning.** When you need to partition items into an unknown number of variable-sized groups and N ≤ 15, use DP over submasks. State: `dp[mask]` = max happiness achievable by optimally teaming up the students in `mask`. Complexity: O(3^N).
- **The submask iteration idiom.** `for (int sub = mask; sub; sub = (sub - 1) & mask)` enumerates all non-empty subsets of `mask` without touching non-subsets. This is what keeps it O(3^N) instead of O(4^N).
- **Precompute group happiness in O(N · 2^N).** Never calculate a group's internal score inside the 3^N loop. Build `happiness_of_mask[mask]` for all 2^N masks upfront.
- **O(N) per mask precomputation trick.** To avoid O(N²) pair enumeration per mask: pick the lowest set bit (call it `temp`), sum `Happy[temp][i]` for all other set bits, then add `happiness_of_mask[mask ^ (1 << temp)]` (already computed for the smaller subset). Each pair is counted exactly once.
