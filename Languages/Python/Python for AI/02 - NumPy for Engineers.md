---
tags: [languages/python-ai, status/draft]
created: 2026-10-14
---
# NumPy for Engineers

> [!abstract] What this is
> The NumPy knowledge a backend or AI engineer needs: the **ndarray** (one contiguous typed buffer + shape + strides), **dtypes** and their memory/precision trade-offs, **vectorisation** (why one NumPy call beats a Python loop by 10–100×), **broadcasting** rules, **views vs copies**, memory layout (C vs Fortran order, contiguity), reductions and `axis`, random generators, and how NumPy interacts with the **GIL** and other libraries (PyTorch, pandas/Polars, Arrow). Based on NumPy 2.x. The decision this helps with: writing array code that is correct, fast and memory-predictable.

---

## Why It Matters

- Embeddings, scores, features and model outputs are arrays. Python lists of floats cost ~4× the memory and ~100× the time ([[02 - Python Memory Management and GC]]).
- Bugs in array code are silent: broadcasting a `(n,)` against `(n, 1)` produces an `(n, n)` matrix instead of an error; a view modified in place changes the original.

---

## The ndarray

```
array a: shape (3, 4), dtype float32, C order
buffer:  [a00 a01 a02 a03 a10 a11 a12 a13 a20 a21 a22 a23]   one contiguous block, 4 bytes each
strides: (16, 4)   → bytes to step to the next row / next column
```

- **One homogeneous dtype**, one buffer (no per-element Python objects), plus metadata: `shape`, `strides`, `dtype`, flags.
- Operations loop in C over the buffer, often with SIMD; NumPy **releases the GIL** for many operations on large arrays, so threads can overlap NumPy work ([[03 - The GIL and Free-Threaded Python]]).
- `a.nbytes` = elements × itemsize: memory is predictable (1M × 768 `float32` embeddings = ~3 GB).

### dtypes

| dtype | Bytes | Use | Trap |
|---|---|---|---|
| `float64` (default for floats) | 8 | Numerics needing precision | Twice the memory of float32 |
| **`float32`** | 4 | Embeddings, ML features, GPU interop | Accumulating big sums loses precision |
| `float16` / `bfloat16` (via ml_dtypes/torch) | 2 | Model weights, compact storage | Small range/precision; compute in float32 |
| `int64` / `int32` / `int8` / `uint8` | 8/4/1/1 | IDs, counts, quantised vectors, images | **Silent overflow** wrapping in integer arithmetic |
| `bool` | 1 | Masks | — |
| `object` | 8 (pointer) | Arbitrary Python objects | Loses all speed; avoid |
| `StringDType` (NumPy 2.0) | variable | Native variable-length strings | Still slower than numeric work |

- **NumPy 2.0** (2024) changed type promotion (NEP 50): Python scalars no longer upcast arrays unexpectedly (`np.float32(3) + 3.0` stays `float32`), plus ABI changes requiring rebuilt extensions.

---

## Vectorisation

```python
import numpy as np

prices = np.asarray(prices_list, dtype=np.float64)
qty = np.asarray(qty_list, dtype=np.int32)

# Python loop: ~100× slower
totals = [p * q for p, q in zip(prices_list, qty_list)]

# Vectorised: one C loop
totals = prices * qty
mask = totals > 100
high_value = totals[mask]                       # boolean indexing (copy)
discounted = np.where(mask, totals * 0.9, totals)
```

- Replace loops with **elementwise ops, ufuncs** (`np.exp`, `np.log1p`), **reductions** (`sum`, `mean`, `max`, `argmax` with `axis=`), **boolean masks**, `np.where`, `np.clip`, sorting/partitioning (`np.argpartition` for top-k), and `np.einsum` / `@` for linear algebra.
- Avoid `np.vectorize` (a Python loop in disguise) and `apply`-style per-element Python callbacks.
- Converting Python lists to arrays costs time too: keep data in arrays end to end; read files directly into arrays (`np.load`, Arrow/Parquet readers).

---

## Broadcasting

Rules, comparing shapes **from the right**: dimensions are compatible if equal or one of them is 1; missing leading dimensions count as 1.

```python
X = np.random.rand(1000, 768).astype(np.float32)    # (n, d)
mu = X.mean(axis=0)                                  # (d,)
Xc = X - mu                                          # (n, d) - (d,) → OK, row-wise centering

norms = np.linalg.norm(X, axis=1)                    # (n,)
Xn_bad = X / norms                                   # (n, d) / (n,) → ERROR (or wrong if n == d!)
Xn = X / norms[:, None]                              # (n, d) / (n, 1) → correct row normalisation
```

- The classic bug: shapes `(n,)` and `(n, 1)` broadcast to `(n, n)`, silently creating a huge matrix. Keep track of shapes; assert them (`assert X.shape == (n, d)`); use `keepdims=True` in reductions.
- Broadcasting doesn't copy data (stride tricks), but the **result** is full-size: `(1e5, 1) - (1, 1e5)` allocates 10¹⁰ elements.

---

## Views vs Copies

| Operation | Result |
|---|---|
| Basic slicing `a[2:10]`, `a[:, 0]`, `a.T`, `reshape` (when possible), `a.view(dtype)` | **View**: shares memory |
| Fancy indexing `a[[1, 5, 7]]`, boolean masks `a[a > 0]` | **Copy** |
| Arithmetic `a + 1` | New array |
| In-place ops `a += 1`, `np.add(a, 1, out=a)` | Modify the buffer (and all views of it) |

```python
batch = embeddings[0:100]       # view
batch /= 2                      # modifies embeddings too!
batch = embeddings[0:100].copy()
```

- Check with `np.shares_memory(a, b)` or `b.base is a`.
- A small view keeps the **whole** original buffer alive: slicing 10 rows out of a 3 GB array and keeping the slice keeps 3 GB. `.copy()` to release it.
- In-place operations and `out=` avoid temporaries for big arrays (`np.multiply(a, b, out=a)`).

---

## Memory Layout

- **C order** (row-major, default): rows contiguous; iterating along the last axis is fastest. **Fortran order**: columns contiguous.
- Transposes and some slices are **non-contiguous**: some operations and libraries (BLAS, PyTorch `from_numpy` for certain uses, C extensions) need `np.ascontiguousarray`.
- `np.memmap` / `np.load(mmap_mode="r")` map files larger than RAM, loading pages on access; useful for big embedding matrices shared across processes (page cache) ([[04 - Python Threads Processes and Executors]]).

---

## Randomness and Reproducibility

```python
rng = np.random.default_rng(seed=42)        # Generator API (PCG64); avoid the legacy global np.random.seed
sample = rng.choice(n, size=1000, replace=False)
noise = rng.standard_normal((n, d), dtype=np.float32)
```

- Pass `Generator`s explicitly; in multiprocessing, spawn independent streams (`rng.spawn(k)` / `SeedSequence.spawn`) so workers don't produce identical random numbers.

---

## Interop

| With | How |
|---|---|
| **PyTorch** | `torch.from_numpy(a)` shares memory (CPU); `t.numpy()` back; GPU tensors must move to CPU first ([[04 - PyTorch for Inference Engineers]]) |
| **pandas / Polars** | `.to_numpy()`; Polars and pandas (Arrow-backed) may copy for non-numeric or null-containing columns ([[07 - Data Tooling for Evals]]) |
| **Arrow / Parquet** | Zero-copy for primitive columns without nulls |
| **Array API standard** | NumPy 2 implements it; code using `array_api_compat` runs on NumPy, CuPy, PyTorch, JAX |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Python loops over arrays** | 10–100× slower | Vectorise; ufuncs; reductions |
| **Accidental `(n,)` vs `(n,1)` broadcast** | Huge matrices, wrong results, OOM | Assert shapes; `keepdims=True`; `[:, None]` explicitly |
| **Modifying a view** | Original data silently changed | `.copy()` before in-place changes |
| **Small view of a big array kept** | Whole buffer stays in memory | `.copy()` slices you keep |
| **`float64` everywhere** | Double memory for embeddings/features | `float32` where precision allows |
| **Integer overflow** | Wrong counts/sums silently | Wider dtypes for accumulation |
| **`object` dtype arrays** | Python-speed operations | Proper numeric/StringDType |
| **Global random state in workers** | Identical "random" samples | `default_rng` + spawned seeds |

---

## Interview Lines

> [!tip] Say it like this
> - "An ndarray is one typed contiguous buffer plus shape and strides, so operations run as C loops and often release the GIL; that's why vectorised code is 10 to 100 times faster than Python loops."
> - "Broadcasting compares shapes from the right; the classic bug is `(n,)` against `(n,1)` silently producing an n-by-n matrix, so I assert shapes and use `keepdims`."
> - "Basic slices are views and fancy indexing copies; in-place changes to a view change the original, and a small view keeps the whole buffer alive."
> - "I use float32 for embeddings and features, which halves memory against the float64 default."
> - "NumPy 2 changed type promotion so Python scalars don't upcast arrays."

## Related Notes

- [[03 - Embeddings and Similarity in Python]] · [[04 - PyTorch for Inference Engineers]] · [[07 - Data Tooling for Evals]] · [[08 - Python AI Performance Traps]]
- [[13 - Python Performance and Profiling]] · [[02 - Python Memory Management and GC]] · [[Languages/README]]
