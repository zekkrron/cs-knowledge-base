---
tags: [dsa/indexing, status/draft]
created: 2026-08-01
---
# Bitmask DP Applications 1

> [!abstract] Quick-lookup index — bitmask DP problems mapped to their core learnings.

## 1. Shortest Hamiltonian Walk (Traveling Salesperson)

Given a weighted graph with N (≤ 20) nodes, find the path visiting every node exactly once with minimum total edge weight. If a cycle is required, return to the start.

- **State: `dp[node][mask]`.** "Standing at `node`, having visited exactly the nodes in `mask`." This is the universal Hamiltonian state — N ≤ 20 guarantees bitmask DP at O(N² · 2^N) instead of O(N!) brute force.
- **Transition: minimise cost.** For each unvisited neighbour v: `ans = min(ans, weight(node, v) + dfs(v, mask | (1 << v)))`.
- **Base case.** When `mask == (1 << N) - 1` (all visited): return 0 if just a path, or return `weight(node, start)` if a cycle (∞ if no edge back).
- **1-based indexing shift.** If nodes are 1-indexed, shift bits by `v-1`. Full mask is `(1 << N) - 1`.

## 2. Number of Hamiltonian Paths

Same graph setup (N ≤ 20). Count the number of valid paths visiting every node exactly once.

- **Fixed start → fixed end.** Base case: when mask is full, return 1 only if `node == target`. Otherwise 0.
- **Any start → any end.** Base case: return 1 whenever mask is full regardless of current node. Wrap the DFS in a loop trying every node as start.
- **Transition: sum paths.** For each unvisited neighbour v: `ans += dfs(v, mask | (1 << v))`.

## 3. Number of Hamiltonian Cycles

Count cycles that visit every node exactly once and return to start. N ≤ 20.

- **Anchor the start to avoid rotational duplicates.** A cycle 1→2→3→1 is the same as 2→3→1→2. Fix start at node 0, initial mask = 1. This counts each cycle exactly once from one starting point.
- **Base case.** When mask is full, return 1 only if there's an edge from current node back to node 0. Otherwise 0.
- **Undirected graphs: divide by 2.** Cycle A→B→C→A and A→C→B→A are the same physical loop traversed in opposite directions. The DP counts both — halve the final answer.

## 4. Number of Simple Paths

Count all paths with no repeated vertices in a graph (N ≤ 20). Any length is valid — doesn't have to visit all nodes.

- **No full-mask base case.** Unlike Hamiltonian variants, a path doesn't need to touch every node. Remove the `mask == (1 << N) - 1` check entirely.
- **Accumulator starts at 1.** Every state you enter is itself a valid simple path (the path ending here). Initialise `ans = 1`, then add results from all valid DFS transitions. This counts every sub-path of every length organically.

## 5. Cycles in a Graph

Given an undirected graph with N (≤ 20) nodes and up to 190 edges, count the number of unique simple cycles (no repeated vertices or edges).

- **Same bitmask DP template.** State: `dp[pos][vis]` — at node `pos`, visited set encoded in `vis`. Counts paths from a fixed start that successfully return to it.
- **Anchor to the lowest-indexed node.** A cycle 0→3→5→0 could be discovered starting from 0, 3, or 5. Force the DFS to only visit neighbours with index ≥ `start`. When the outer loop is at start = 3, node 0 is unreachable — each cycle is found exactly once from its minimum vertex.
- **Reject length-2 "cycles".** In an undirected graph, A→B→A looks like a cycle to DFS but is just bouncing on one edge. Only count a cycle if path length > 2.
- **Divide by 2 for direction.** Undirected cycle A→B→C→A and A→C→B→A are the same loop traversed both ways. The DP counts both — halve the final answer.

## 6. Chess And GCD

2N (≤ 20) chess players with ratings A[i]. Pair them into N pairs on boards 1 to N. Pairing player i and j on board K scores K × |A[i] − A[j]| × gcd(A[i], A[j]). Maximise total score.

- **1D bitmask DP — `dp[mask]`.** Mask encodes which players are already paired. Since each board takes exactly 2 players, the current board number is `popcount(mask) / 2 + 1` — no extra dimension needed.
- **Implicit board number from popcount.** Don't store board K separately. It's derivable from how many bits are set. Keeps the DP 1D (2^20 ≈ 10^6 states) instead of 2D.
- **Precompute inner-loop work.** With 10^6 states and multiple pair choices per state, the inner loop is the bottleneck. Precompute `pre_bits[mask]` (list of available player indices per mask) and `pre_gcd[i][j]` (GCD of all pairs). Turns scanning and math into O(1) lookups.
