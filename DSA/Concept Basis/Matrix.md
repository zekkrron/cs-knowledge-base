---
tags: [dsa/concept-basis, status/draft]
created: 2026-08-28
---
# Concept Basis — Matrix

> [!abstract] Minimal spanning set for **a 2D grid as a coordinate system** — flattening, diagonals, layer walks, in-place transforms, local counting that skips a flood, and the extra dimension on a rolling hash. One entry per **new idea you have to learn**. A grid *as a graph* lives in [[Graphs]]; a grid *as a DP table* in [[Dynamic Programming]]; first-row marking and two-bit packing in [[Arrays]]. Cross-listed entries (↗) are developed further elsewhere but belong here too.

> [!tip] **The question is almost never "loop `i`, loop `j`." It is "what is invariant on this slice, and can I walk the slice without a copy?"**
>
> Rows share `r`. Columns share `c`. One diagonal family shares `r − c`, the other shares `r + c`. A layer is four bounds that shrink. Once you name the slice, the walk is usually four pointers or a flatten index. Reach for [[Graphs]] when the *neighbours* are the story; reach for this file when the *coordinates* are.

## The one reflex

> [!tip] **Before you allocate a second matrix, ask whether the answer is a permutation of the cells you already hold** — a rotate, a reshape, a spiral readout, a diagonal rewrite. If it is, the work is an index map, and extra space is a confession that you have not found the map.
>
> The second reflex: **a 2D problem is often `n` copies of a 1D one**, stacked across rows (histogram → maximal rectangle) or hashed across rows then columns (2D matching). Name the 1D primitive first.

## Mechanism axes

| Axis | Values |
|---|---|
| **What is constant on the slice** | a row · a column · **`r − c`** · **`r + c`** · a layer (four bounds) · a flatten index `r·n + c` |
| **What the walk does** | read in order · write a transform in place · mark, then a second sweep · simulate a particle |
| **How a 90° turn is built** | **transpose then reverse rows** · a 4-cycle of cells · `k` times 90° |
| **What you may overwrite** | nothing · a designated row/column as marks · unused bits in the cell · the whole matrix (reshape) |
| **Neighbourhood** | four-dir · eight-dir · "the cell itself encodes the next step" · none (pure indexing) |
| **Assumption that breaks** | a second matrix is required · flood fill is required to *count* objects · 74-flattening works on 240 · a 1D hash is enough for a 2D pattern |

## Shape of this topic

```
M1  Index arithmetic                 2 ideas
M2  Layer and rotation               2 ideas
M3  In-place mark / simultaneous     2 ideas
M4  Local count, no flood            1 idea
M5  The cell encodes the next step   1 idea
M6  The extra dimension on a hash    1 idea
                                     + 11 cross-listed ↗
```

**9 native entries, plus 11 cross-listed (↗).** See [[README]] on cross-listing.

> [!info] **Numbers are stable IDs assigned in order of addition, not reading order.** Dual-native with [[Arrays]] #2 / #4 on Set Matrix Zeroes and Game of Life — those two were filed there as *space tricks*; they live here as *2D walks*.

## Named algorithms in this file

| The name you remember | Entry |
|---|---|
| Reshape · flatten `r·n + c` | #1 |
| **Diagonal Traverse** · Toeplitz | #2 |
| **Spiral Matrix** | #3 |
| **Rotate Image** · transpose-then-reverse | #4 |
| **Set Matrix Zeroes** | #5 |
| **Game of Life** | #6 |
| Battleships in a Board | #7 |
| Where Will the Ball Fall | #8 |
| **2D Rabin–Karp** | #9 |

---

## M1 · Index arithmetic

| # | Problem | Source | The new idea |
|---|---|---|---|
| 1 | Reshape the Matrix | LC **566** | **A matrix is a 1D array wearing a stride.** `id = r·n + c`, `r = id / n`, `c = id % n`. Reshape is "read in old stride, write in new stride" — `O(1)` extra if you are allowed to allocate the result, and the *idea* is the map, not the copy. Convert 1D to 2D (LC **2022**) is the inverse. New because every later entry is a *different* map on the same formula, and [[Binary Search]] #16's flattening of LC **74** is this stride with the extra fact that the 1D order is sorted. [[Arrays]] called the formula arithmetic; from this file it is the coordinate system. |
| 2 | Diagonal Traverse | LC **498** | **One family of diagonals is constant `r + c`, the other is constant `r − c`.** Group by that key, then walk each group (and reverse every other group for the zig-zag). Toeplitz (LC **766**) is the same key as a *predicate*: every `r − c` bucket must be a single value. Sort Matrix Diagonally (LC **1329**) sorts within a `r − c` group and writes back — the sort is incidental, the key is this. New because the slice is neither a row nor a column, and `r − c` needs a shift of `n` if you want a non-negative bucket index. Backtracking already uses both families as N-Queens occupancy ([[Backtracking]] #6); here they are the *walk*. |

## M2 · Layer and rotation

| # | Problem | Source | The new idea |
|---|---|---|---|
| 3 | Spiral Matrix | LC **54** | **Four bounds, shrink after each side.** `top, bottom, left, right`; walk right along `top` then `top++`, down along `right` then `right--`, and so on, stopping when a bound crosses. Spiral II (LC **59**) fills instead of reading; Spiral III (LC **885**) generates the walk in the *plane* without a bounding matrix — same turning discipline, no `bottom` to bump into. New because the object being walked is a **layer**, not a row, and the off-by-one is always "did this side empty the remaining interval." Linked-list spiral (LC **2326**) is the same bounds with a different cursor. |
| 4 | Rotate Image | LC **48** | **90° clockwise is transpose, then reverse each row** — or a 4-cycle `(r,c) → (c, n−1−r) → …` on each layer. Both are the same permutation; the transpose-reverse form is the one you can say in a sentence and is [[Two Pointers]] #13's compose-reversals identity in 2D. 180° is reverse rows then reverse each row (or two 90s); 270° is the other transpose. New because the *algebra* is the entry: you do not simulate a rotation, you **factor it into two involutions you already know**. Determine Whether Matrix Can Be Obtained By Rotation (LC **1886**) is "try `k = 0..3`." |

## M3 · In-place mark / simultaneous

| # | Problem | Source | The new idea |
|---|---|---|---|
| 5 | Set Matrix Zeroes | LC **73** | **Record which rows and columns must die, then a second sweep kills them** — and the recording lives in the first row and first column so the extra space is `O(1)`, with one extra flag because `matrix[0][0]` is on both. Dual-native with [[Arrays]] #2: that file owns *hiding a bit in the input*; this file owns the **2D two-pass** (mark, then zero) and the overlap at `(0,0)`. A first pass that allocates `m + n` bools is the same walk without the hiding trick, and is what you write if mutation of the first row is illegal. |
| 6 | Game of Life | LC **289** | **Every cell's next state depends on the *current* neighbourhood, so you cannot overwrite as you go.** Pack both states into the cell (live/dead × will-live/will-die), count neighbours from the *old* bit, write the *new* bit, then a final pass strips to 0/1. Dual-native with [[Arrays]] #4: that file owns *headroom in the value range*; this file owns **simultaneous update on a grid** — eight-neighbour count, in-place, two bits. Image Smoother (LC **661**) is the same neighbourhood without the packing, because the output is a new matrix. |

## M4 · Local count, no flood

| # | Problem | Source | The new idea |
|---|---|---|---|
| 7 | Battleships in a Board | LC **419** | **Count objects by counting only a canonical cell of each one.** A ship is a contiguous run of `X`s; increment only when this `X` has no `X` above and no `X` to the left — that cell is the unique head. Island Perimeter (LC **463**) is the dual: `4·land − 2·adjacent pairs`, because each shared edge kills two sides. New because [[Graphs]] #1 would flood each ship, and you do not need a traversal to *count* when the objects cannot touch. The reflex: **if the shapes are disjoint by the problem statement, a local predicate on the head (or on the edges) replaces DFS.** |

## M5 · The cell encodes the next step

| # | Problem | Source | The new idea |
|---|---|---|---|
| 8 | Where Will the Ball Fall | LC **1706** | **The value in the cell is the next offset, not a colour to flood.** `\` sends you right-and-down, `/` left-and-down; a V-shape (`/\` or `\/`) is a trap. Walk from each top cell until you exit or jam. New against Game of Life: there the neighbourhood is *geometry*; here the neighbourhood is *whatever the cell points at*. Rotating the Box (LC **1861**) is gravity (stones fall in the current down) then #4; Candy Crush (LC **723**) is crush-then-gravity until stable. Both are this family: the grid is a simulator, not a picture. |

## M6 · The extra dimension on a hash

| # | Problem | Source | The new idea |
|---|---|---|---|
| 9 | 2D pattern matching / 2D Rabin–Karp | *classic — no clean LC Easy; the 1D primitive is [[Strings]] #5* | **Hash every window of width `w` on each row, then treat those hashes as an alphabet and roll a hash *down* the columns of height `h`.** A 2D match is now an `O(1)` compare of one number (plus collision risk that compounds). New because [[Strings]] #5 is a substring; this is a **submatrix**, and the reduction is "do the 1D primitive twice, once per axis." Parked here from the Strings file on purpose — the extra dimension is the matrix idea, the polynomial is not. Double-hash if a wrong match is a wrong answer. |

---

## Cross-listed

| ↗ | Problem | Source | The idea, and where it goes deeper |
|---|---|---|---|
| ↗ | Search a 2D Matrix · II | LC **74** · **240** | 74 flattens (#1) because rows chain. 240 is a staircase from the top-right, eliminating a row or a column per step. Telling them apart is the interview. [[Binary Search]] #16. |
| ↗ | Kth Smallest in a Sorted Matrix | LC **378** | Value-range binary search, or a heap on the virtual merge. The matrix is already sorted; you never walk it as a matrix. [[Binary Search]] #12, [[Heap]] #8. |
| ↗ | Range Sum Query 2D | LC **304** | Inclusion-exclusion, four corners. [[Prefix Sums & Difference Arrays]] #3. Submatrix sum equals K is that plus a hash map per row-pair (LC **1074**). |
| ↗ | Maximal Rectangle | LC **85** | Each row is a histogram of consecutive 1s above it; then largest rectangle in histogram. The "2D = `n` copies of 1D" sentence. [[Stack and Queue]] #17. |
| ↗ | Unique Paths · Min Path Sum · Maximal Square | LC **62** · **64** · **221** | Ending-at-`(i,j)`, or geometric state for a square. [[Dynamic Programming]] D1 / #26. Falling path, dungeon, cherry pickup — same file. |
| ↗ | Number of Islands · 01 Matrix · Pacific Atlantic · LIP | LC **200** · **542** · **417** · **329** | The grid is a graph. Flood, multi-source BFS, two border fills, DAG memo. [[Graphs]] #1 · #3 · #12. |
| ↗ | Word Search | LC **79** | DFS with undo on the board. [[Backtracking]] #8. Word Search II is a trie on the same walk ([[Tries]]). |
| ↗ | Valid Sudoku | LC **36** | Nine row sets, nine column, nine boxes. [[Hashing]] exclusion of #2; occupancy masks are [[Bit Manipulation]] #15. |
| ↗ | Sort Matrix Diagonally | LC **1329** | Group by `r − c` (#2), sort each group. The sort is [[Sorting & Custom Comparators]]; the key is here. |
| ↗ | Matrix exponentiation | *classic* | Linear recurrence as a `k × k` multiply, then `O(k³ log n)`. [[Dynamic Programming]] #59, [[Math & Number Theory]] #1. Not a grid walk. |
| ↗ | Rectangle Area II | LC **850** | 2D sweep, segment tree on y. [[Segment Trees]], [[Intervals]] (hedge closed there as ↗). |

---

## Excluded as variations

| Problem | Source | Collapses into | Why |
|---|---|---|---|
| Spiral Matrix II · IV | LC **59** · **2326** | #3 | Fill, or a linked-list cursor. Same bounds. |
| Spiral Matrix III | LC **885** | #3 | The walk in the plane; no inner hole. |
| Transpose Matrix | LC **867** | #4 | Half of the 90° factorisation. |
| Rotate 180 / 270 · Image from rotation | LC **1886** | #4 | Compose #4. |
| Convert 1D Array Into 2D | LC **2022** | #1 | Inverse reshape. |
| Toeplitz Matrix | LC **766** | #2 | `r − c` is monochrome. |
| Diagonal Traverse II | LC **1424** | #2 | Ragged lists, same `r + c` groups. |
| Island Perimeter | LC **463** | #7 | Edge counting instead of heads. |
| Image Smoother | LC **661** | #6 | Neighbourhood average, new matrix. |
| Rotating the Box | LC **1861** | #8 · #4 | Gravity, then rotate. |
| Candy Crush | LC **723** | #8 | Crush and fall until stable. |
| Flood Fill · Max Area of Island | LC **733** · **695** | ↗ Graphs #1 | Traversal. |
| Surrounded Regions | LC **130** | ↗ Graphs #1 | Fill from the border. |
| Lucky Numbers · Special Positions | LC **1380** · **1582** | — | Min of row / unique 1. Scan. |
| Shift 2D Grid | LC **1260** | #1 | Flatten, rotate 1D, unflatten. |
| Matrix Cells in Distance Order | LC **1030** | — | BFS or sort by `|r|+|c|`. [[Graphs]] #2. |
| Sparse Matrix Multiplication | LC **311** | — | Skip zeros. Systems, not a new walk. |
| Rank Transform of a Matrix | LC **1632** | — | DSU on equal cells, then increasing ranks. [[Union-Find]] #5 · #7. |
| Build Matrix With Conditions | LC **2392** | — | Two topological orders, then place. [[Graphs]] #11. |
| Game of Life *(allocate a copy)* | LC **289** | #6 | The thing #6 exists to beat. |

---

## Self-audit

**Borderline calls, and which way I went**

- **Dual-native with [[Arrays]] #2 / #4.** Set Matrix Zeroes and Game of Life stay there as hiding tricks and live here as 2D two-pass / simultaneous neighbourhood. Same rule as Two Sum in Arrays and Hashing.
- **#1 native despite [[Arrays]] calling flatten "arithmetic."** From a 1D-array file it is arithmetic. From a matrix file it is the coordinate system every other entry sits on. Native here; Arrays exclusion now points at this ID.
- **#4 native, [[Two Pointers]] #13 keeps the 1D identity.** Rotate Image stays an exclusion there (compose-reversals in 2D) and a native here (the 2D permutation). Reciprocal, not a move.
- **#7 native rather than ↗ Graphs.** The whole point is you *do not* flood. Filing it in Graphs would hide that.
- **#9 native rather than leaving it parked forever in Strings.** Strings #5 stays the polynomial; this is "once per axis." Tagged as classic because LC will not hand you a clean Easy.
- **Staircase walk (LC 240) ↗ Binary Search #16, not native.** That file's entry *is* "74 and 240 look identical and are not." Native here would duplicate the interview.

**Naming check.** #3 stayed "Spiral Matrix" because the bounds *are* the idea. #4 stayed "Rotate Image" — the factorisation is in the body. #8 is the pinball problem; "cell encodes next step" is the family name in the heading.

**Step 4B — reverse sweep**

Eighteen descriptions.

- **"Read this rectangle clockwise from the outside in"** → #3. **"Fill 1..n² that way"** → still #3. Held.
- **"90° in place"** → #4, not a layer-by-layer copy. Transpose-reverse is the sentence.
- **"Zero every row and column that contains a zero, O(1) extra"** → #5 and [[Arrays]] #2. Dual, intended.
- **"Count ships without DFS"** → #7. Why #7 exists.
- **"Ball falls through `\` and `/`"** → #8, not flood fill.
- **"Find a submatrix matching this pattern in sub-quadratic"** → #9, not Strings #5 alone.
- **"Search a matrix that is sorted in both directions"** → ↗ LC 240, not #1's flatten. Collision with ↗ LC 74 checked: that is the pair [[Binary Search]] #16 exists for.

No missing axis. The topic is small and named; the risk is pulling graph/DP grid problems native, which the ↗ table is there to stop.

**Step 4C — inward**

(i) [[Arrays]] Spiral/Rotate exclusion, flatten exclusion, Set Matrix / Game of Life, Matrix-boundary hedge. [[Strings]] 2D matching parked. [[Stack and Queue]] #17 "Matrix basis." [[Two Pointers]] Rotate Image exclusion. [[Prefix Sums & Difference Arrays]] #3. [[Binary Search]] #16. All now have a native or a ↗.
(ii) Spiral, Rotate, Set Zeroes, Game of Life, Diagonal Traverse, Reshape, Battleships, 74/240, Maximal Rectangle, Unique Paths — each sits on a native or a ↗.
(iii) Arrays "remaining movement is Matrix" — this file. Strings "2D matching parked" — #9. Stack "developed further in the Matrix basis" — ↗ #17 stays native there.

**What I am uncertain about**

- **Spiral III as its own entry.** It drops the inner hole. Collapsed into #3; a follow-up that cannot use `bottom` might want the split.
- **Rank Transform of a Matrix** is a real Hard and is Union-Find plus sorting, not a walk. Left as an exclusion pointing at [[Union-Find]].
- **Geometry proper** (rotating calipers, closest pair). Out of scope per [[README]].

**Completeness confidence: ~90%.** M1–M3 are the interview core and have dense lists. M4–M6 are thinner externally; #9 especially is "know the reduction exists."

## Related Notes

- [[README]]
- [[Arrays]]
- [[Two Pointers]]
- [[Graphs]]
- [[Dynamic Programming]]
- [[Prefix Sums & Difference Arrays]]
- [[Stack and Queue]]
- [[Binary Search]]
- [[Strings]]
- [[Backtracking]]
- [[Hashing]]
- [[Sorting & Custom Comparators]]
- [[Union-Find]]
- [[Bit Manipulation]]
- [[Heap]]
- [[Intervals]]
- [[Segment Trees]]
- [[Math & Number Theory]]
- [[Tries]]
