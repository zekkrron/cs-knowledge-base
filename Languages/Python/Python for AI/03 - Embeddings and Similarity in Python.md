---
tags: [languages/python-ai, status/draft]
created: 2026-10-14
---
# Embeddings and Similarity in Python

> [!abstract] What this is
> The Python mechanics of working with embeddings: generating them in **batches** (API or local model), **normalisation** and why cosine similarity becomes a dot product, **batched similarity** and **top-k** with NumPy/PyTorch, the **memory math** (n × d × bytes), compression (float16, int8/binary quantisation, Matryoshka truncation), and when to stop doing it in-process and use **FAISS / a vector database**. Index internals (HNSW, IVF, PQ, hybrid search) are covered in [[08 - Vector DB Internals and Hybrid Search]]; this note is the code and numbers.

---

## Why It Matters

- Retrieval quality and cost depend on details that live in Python code: unnormalised vectors with cosine assumptions, float64 matrices that don't fit in RAM, a loop over 1M vectors per query, mixing embeddings from two model versions.

---

## Generating Embeddings

```python
async def embed_texts(client: AsyncOpenAI, texts: list[str], batch: int = 256) -> np.ndarray:
    out = []
    for i in range(0, len(texts), batch):                      # providers cap inputs per request
        resp = await client.embeddings.create(model=settings.embed_model, input=texts[i:i + batch])
        out.extend(d.embedding for d in resp.data)             # results come back in input order
    return np.asarray(out, dtype=np.float32)
```

```python
from sentence_transformers import SentenceTransformer        # local model
model = SentenceTransformer("BAAI/bge-small-en-v1.5", device="cuda")
vecs = model.encode(texts, batch_size=128, normalize_embeddings=True, convert_to_numpy=True)
```

- **Batch** requests (API) or forward passes (local); run batches concurrently within rate limits ([[01 - Async LLM Client Patterns]]).
- Local models: batch on GPU, sort texts by length to reduce padding, use `float16` inference ([[04 - PyTorch for Inference Engineers]]).
- Follow the model's conventions: some models need **prefixes** ("query: ", "passage: ") or separate query/document modes; inputs beyond the max token length are **truncated** silently, so chunk documents first ([[06 - RAG Ingestion and Indexing Pipeline]]).
- **Version** embeddings: store model name + version with each vector. Vectors from different models (or versions) are not comparable; a model change means re-embedding the corpus.

---

## Similarity

| Metric | Formula | Note |
|---|---|---|
| **Cosine** | `a·b / (‖a‖‖b‖)` | Direction only; the common default for text embeddings |
| **Dot product** | `a·b` | Equals cosine **if vectors are L2-normalised** |
| **Euclidean (L2)** | `‖a − b‖` | For normalised vectors, ranking is the same as cosine (`‖a−b‖² = 2 − 2cos`) |

- **Normalise once** at write time (and the query at query time); then use the dot product everywhere. Check which metric the model and the index expect.

```python
def l2_normalise(x: np.ndarray, eps: float = 1e-12) -> np.ndarray:
    return x / np.maximum(np.linalg.norm(x, axis=-1, keepdims=True), eps)   # keepdims: (n,1), not (n,)

def top_k(query: np.ndarray, corpus: np.ndarray, k: int = 10) -> tuple[np.ndarray, np.ndarray]:
    """query: (d,) or (q, d); corpus: (n, d); both normalised float32."""
    scores = corpus @ query.T                                   # (n,) or (n, q) — one BLAS call
    if scores.ndim == 1:
        idx = np.argpartition(-scores, k)[:k]                   # O(n) selection, not a full sort
        idx = idx[np.argsort(-scores[idx])]                     # sort only the k winners
        return idx, scores[idx]
    idx = np.argpartition(-scores, k, axis=0)[:k]               # (k, q): unordered winners per query
    top = np.take_along_axis(scores, idx, axis=0)               # (k, q)
    order = np.argsort(-top, axis=0)
    return np.take_along_axis(idx, order, axis=0), np.take_along_axis(top, order, axis=0)
```

- `corpus @ query` uses BLAS (multi-threaded, GIL released). 1M × 768 float32 against one query ≈ 3 GB read: tens of milliseconds on CPU, faster on GPU.
- Batch **queries** together (`(q, d)` matrix) to turn many matrix-vector products into one matrix-matrix product.
- Top-k: `argpartition` (O(n)) then sort k; or `torch.topk` on GPU.
- Very large corpora: process in **chunks** to cap the memory of the score matrix (`n × q` floats).

---

## Memory Math

| Corpus | d | dtype | Size |
|---|---|---|---|
| 1M vectors | 768 | float32 | **3.07 GB** |
| 1M | 768 | float16 | 1.54 GB |
| 1M | 768 | int8 (scalar quantised) | 0.77 GB |
| 1M | 768 | binary (1 bit/dim) | 96 MB |
| 1M | 256 (Matryoshka truncation) | float32 | 1.02 GB |
| 100M | 1024 | float32 | **410 GB** |

- `bytes = n × d × bytes_per_dim` (+ index overhead: HNSW graphs add roughly tens to hundreds of bytes per vector).
- **Compression options**:
  - **float16 / bfloat16** storage: half memory, minimal quality loss.
  - **int8 scalar quantisation**: 4× smaller, small recall loss; rescore top candidates with full precision.
  - **Binary quantisation**: 32× smaller, Hamming distance; use as a first pass, then rescore.
  - **Matryoshka** models: truncate to fewer dimensions (and renormalise) with graceful quality loss.
  - **PQ** inside ANN indexes ([[08 - Vector DB Internals and Hybrid Search]]).
- Measure recall@k on your own eval set after any compression ([[07 - Data Tooling for Evals]]).

---

## When to Leave In-Process Search

| Scale / need | Approach |
|---|---|
| ≤ ~1M vectors, single process, rebuilt rarely | **NumPy/PyTorch brute force**: exact, simple |
| Millions, low latency, one machine | **FAISS** (`IndexFlatIP` exact, `IndexHNSWFlat`, `IndexIVFPQ`), or hnswlib/usearch |
| Filters, updates, multi-tenant, replicas, hybrid keyword search | **Vector database** (pgvector, Qdrant, Weaviate, Milvus, OpenSearch, Elasticsearch) |
| Already on Postgres, moderate scale | **pgvector** with HNSW indexes |

- Multiple worker processes each loading a copy of a large in-memory matrix multiply RAM: memory-map a shared file, or move search to a separate service ([[08 - Python AI Performance Traps]]).
- With FAISS, normalise before `IndexFlatIP` for cosine; batch searches; `faiss.omp_set_num_threads` to avoid oversubscribing CPUs alongside web workers.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Dot product on unnormalised vectors** | Long documents score higher regardless of relevance | Normalise at write and query time |
| **Mixed embedding models/versions** | Meaningless similarities | Store model version; re-embed on change |
| **Silent truncation of long inputs** | Embeddings ignore most of the document | Chunk to the model's max length |
| **Missing query/passage prefixes** | Lower retrieval quality | Follow the model card |
| **float64 corpora** | 2× memory | float32 or smaller |
| **Python loop over vectors** | Seconds per query | Matrix ops, batched queries |
| **Full sort for top-k** | O(n log n) per query | `argpartition` / `topk` |
| **Compression without measuring recall** | Quality silently drops | Eval set + recall@k before/after |
| **Per-worker copies of large matrices** | RAM × workers | mmap, shared service |

---

## Interview Lines

> [!tip] Say it like this
> - "I L2-normalise embeddings once, at write and query time, so cosine similarity is just a dot product, and top-k over a million vectors is one matrix multiply plus `argpartition`."
> - "Memory is n times d times bytes: a million 768-dimensional float32 vectors is about 3 GB; int8 is a quarter of that and binary a thirty-second, with rescoring of the top candidates."
> - "Embeddings from different models or versions aren't comparable, so I store the model version and re-embed on upgrades."
> - "Brute force in NumPy is fine up to around a million vectors; beyond that, or with filters and updates, FAISS or a vector database."

## Related Notes

- [[02 - NumPy for Engineers]] · [[04 - PyTorch for Inference Engineers]] · [[01 - Async LLM Client Patterns]] · [[07 - Data Tooling for Evals]]
- [[08 - Vector DB Internals and Hybrid Search]] · [[06 - RAG Ingestion and Indexing Pipeline]] · [[07 - Agentic RAG and Grounding Loops]] · [[Languages/README]]
