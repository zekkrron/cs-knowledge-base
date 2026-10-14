---
tags: [agentic/topics, status/draft]
created: 2026-10-11
---
# Vector DB Internals and Hybrid Search

> [!abstract] What this is
> How retrieval actually executes and scales: ANN index families (**HNSW**, **IVF / IVF-PQ**, **DiskANN**), vector compression (scalar, product, **binary** quantization, **Matryoshka** truncation), memory arithmetic, **metadata filtering** strategies and their failure modes, **hybrid search** with BM25/SPLADE and **Reciprocal Rank Fusion**, and **bi-encoder retrieval vs cross-encoder reranking**. The decision this helps with: which index, compression and filtering design fits a corpus size, latency target and budget.

> [!info] Builds on HLD
> The basics of exact kNN vs HNSW vs IVF, `ef_search`, recall and hybrid search are in [[09 - Storage Search and Geo]]. This note goes a level deeper.

---

## Why It Exists

- A brute-force search compares the query to every vector: `O(n·d)`. At 10 million 1536-dimension vectors that's ~15 billion multiply-adds per query, plus ~61 GB of raw float32 data. Approximate indexes, compression and smart filtering are what make vector search fit in memory and answer in milliseconds.
- And dense vectors alone miss exact identifiers, so production retrieval is hybrid and reranked.

---

## Memory Arithmetic First

```
raw size = vectors × dimensions × bytes per value
10M × 1536 × 4 bytes (float32) ≈ 61 GB      (before index overhead)
```

| Representation | Bytes per 1536-dim vector | 10M vectors | Relative |
|---|---|---|---|
| float32 | 6,144 | ~61 GB | 1× |
| float16 / bfloat16 | 3,072 | ~31 GB | 2× smaller |
| int8 (scalar quantization) | 1,536 | ~15 GB | 4× smaller |
| Product quantization (e.g. 96 sub-vectors × 1 byte) | 96 | ~1 GB | 64× smaller |
| Binary (1 bit per dim) | 192 | ~2 GB | 32× smaller |
| Matryoshka-truncated to 512 dims, float32 | 2,048 | ~20 GB | 3× smaller |

- Add index overhead: HNSW stores neighbour lists per node (roughly `M × 2 × 4–8 bytes` per vector at the base layer, so tens to hundreds of bytes per vector), which matters at small dims and huge counts.
- Being able to do this arithmetic out loud is a strong interview signal.

---

## ANN Index Families

### HNSW (Hierarchical Navigable Small World)

- A **multi-layer proximity graph**: upper layers are sparse with long-range links, the bottom layer contains every vector. Search starts at the top, greedily moves to the neighbour closest to the query, drops a layer, and repeats, keeping a candidate list of size `ef_search` at the bottom.

| Parameter | Effect |
|---|---|
| **M** (max neighbours per node) | Higher = better recall, more memory, slower build |
| **ef_construction** | Candidate list size while building; higher = better graph, slower build |
| **ef_search** | Candidate list size at query time; higher = better recall, slower queries (the main runtime knob) |

- **Strengths:** excellent recall/latency trade-off; supports incremental inserts.
- **Costs:** the graph and (usually) full vectors must sit in **RAM**; random memory access; **deletes** are typically tombstones that degrade the graph until it's rebuilt or compacted; build is CPU-heavy.

### IVF and IVF-PQ

- **IVF (inverted file)**: k-means clusters the vectors into `nlist` cells, each with a centroid. A query finds the `nprobe` nearest centroids and searches only those cells.
    + `nprobe` is the recall/speed knob; vectors near cell boundaries are the classic recall loss.
- **Product quantization (PQ)**: split each vector into `m` sub-vectors and replace each with the id of its nearest centroid in a small per-subspace codebook (typically 256 entries → 1 byte). A 1536-dim float vector becomes `m` bytes.
    + Distances are computed **approximately** via precomputed lookup tables (asymmetric distance), which is fast.
- **IVF-PQ** combines them: coarse cells to search fewer vectors, PQ codes to make each comparison cheap and memory-tiny. The standard design for **hundreds of millions to billions** of vectors (FAISS, Milvus).
- Because PQ distances are approximate, retrieve more candidates (e.g. 10× k) and **rescore** them with full-precision vectors.

### DiskANN

- A graph index (Vamana) designed to live mostly **on SSD**: compressed (PQ) vectors stay in RAM to guide the search, while full-precision vectors and the graph live on disk and are read in a few batched I/Os per query.
- **Pick when:** billion-scale corpora where keeping everything in RAM is too expensive, and single-digit-millisecond-ish latency on NVMe is acceptable.

### Choosing

| Index | Memory | Latency | Recall | Updates | Pick when |
|---|---|---|---|---|---|
| **Flat (exact)** | Full vectors | Linear in n | 100% | Trivial | Up to ~100k vectors, or exact rescoring of candidates |
| **HNSW** | High (RAM) | Very low | Very high | Inserts good, deletes degrade | Default up to tens of millions with RAM budget |
| **IVF-Flat** | Full vectors | Low | Tunable via nprobe | Needs periodic re-training of centroids | Large sets, simpler than graphs |
| **IVF-PQ** | Tiny | Low | Lower; rescoring needed | Re-train on drift | Hundreds of millions to billions; memory-bound |
| **DiskANN** | Low RAM, SSD-heavy | Low (SSD) | High | Supported with care | Billion-scale on a budget |

---

## Compression Without Rebuilding the Model

### Scalar and binary quantization

- **Scalar (int8)**: map each float dimension to 8 bits using per-dimension min/max. ~4× smaller, small recall loss.
- **Binary**: keep only the **sign** of each dimension (1 bit). 32× smaller, and distance becomes **Hamming distance**, computed with XOR + popcount, which is extremely fast.
    + Recall drops noticeably on its own, so the standard pattern is **binary search for candidates → rescore the top few hundred with int8 or float vectors**.
    + Works best with high-dimensional embeddings trained or known to quantize well.

```python
import numpy as np

def to_binary(v: np.ndarray) -> np.ndarray:
    return np.packbits(v > 0, axis=-1)                       # 1536 dims -> 192 bytes

def search(query: np.ndarray, bin_index: np.ndarray, full: np.ndarray, k=10, oversample=20):
    q = to_binary(query[None])[0]
    hamming = np.unpackbits(bin_index ^ q, axis=-1).sum(axis=1)       # XOR + popcount
    candidates = np.argsort(hamming)[: k * oversample]                # cheap, approximate
    scores = full[candidates] @ query                                  # exact rescoring
    return candidates[np.argsort(-scores)[:k]]
```

### Matryoshka embeddings

- **Matryoshka Representation Learning** trains embeddings so the **first N dimensions are themselves a good embedding**. You can truncate a 3072-dim vector to 256 or 512 dims (and re-normalise) with modest quality loss.
- Uses: smaller indexes, faster search, and **funnel retrieval** (search with short vectors, rescore with full ones). Only works for models trained this way; truncating an ordinary embedding destroys it.

---

## Metadata Filtering

- Almost every real query has filters: tenant, ACL, language, date, product. How the filter interacts with ANN search is one of the most common production problems.

| Strategy | How it works | Failure mode | Pick when |
|---|---|---|---|
| **Post-filtering** | ANN top-k (say 50) globally, then drop results that fail the filter | **Starvation**: for a small tenant or a selective filter, all 50 global matches may belong to others, returning empty or very few results | Filters that match most of the data |
| **Pre-filtering** | Compute the matching id set first, then search only among them | With a graph index, restricting traversal to allowed nodes **breaks connectivity**, so recall collapses; or falls back to brute force over the allowed set (fine when small) | Highly selective filters (small allowed sets → exact search) |
| **Single-stage / filter-aware traversal** | The ANN search itself respects the filter during traversal (allowed-set bitmask checked per node, with extra edges or expanded exploration to stay connected) | Engine-specific; cost rises with very selective filters | Default in modern engines (e.g. filterable HNSW in Qdrant, Weaviate's ACORN-style filtering, others) |
| **Partitioning by filter key** | Separate index / collection / partition per tenant (or per major filter value) | Many small indexes to manage; cross-partition queries harder | Multi-tenancy with strong isolation; few large tenants |

- **Multi-tenancy rule of thumb:** large tenants get their own partition or collection; many small tenants share an index with a `tenant_id` filter enforced via filter-aware search (never post-filtering), plus payload indexes on filter fields.
- Filters are also the **security boundary** for RAG ACLs ([[06 - RAG Ingestion and Indexing Pipeline]]), so they must be applied inside the engine, not after results come back to the app.

---

## Hybrid Search

- Dense embeddings capture meaning but are weak on **exact tokens**: SKUs (`SKU-88213`), error codes (`E1042`), function names, rare proper nouns, version numbers. Lexical search nails these.

| Retriever | How it scores | Strong at |
|---|---|---|
| **BM25** (sparse, lexical) | Term frequency (saturated) × inverse document frequency, length-normalised ([[09 - Storage Search and Geo]]) | Exact terms, ids, rare words |
| **SPLADE** (learned sparse) | A model expands text into weighted vocabulary terms, stored in an inverted index | Lexical precision plus some synonym expansion |
| **Dense** (bi-encoder) | Cosine / dot product between embeddings | Paraphrases, semantics, cross-lingual |

### Reciprocal Rank Fusion (RRF)

- BM25 and cosine scores live on different scales, so adding them directly is meaningless. **RRF** combines **ranks** instead:

```
RRF(d) = Σ over retrievers r of 1 / (k + rank_r(d))        k ≈ 60 by convention
```

```python
from collections import defaultdict

def rrf(result_lists: list[list[str]], k: int = 60, top_n: int = 20) -> list[str]:
    scores: dict[str, float] = defaultdict(float)
    for results in result_lists:                     # each list is ranked best-first
        for rank, doc_id in enumerate(results, start=1):
            scores[doc_id] += 1.0 / (k + rank)
    return sorted(scores, key=scores.get, reverse=True)[:top_n]

fused = rrf([bm25_search(q, k=50), dense_search(q, k=50)])
```

- RRF needs no score normalisation or tuning and is robust, which is why it's the default. **Weighted score fusion** (normalise each retriever's scores, then `α·dense + (1−α)·lexical`) can do better when tuned on evals but is brittle.

---

## Retrieval vs Reranking

| | Bi-encoder (retrieval) | Cross-encoder (reranking) |
|---|---|---|
| How | Query and document embedded **separately**; compare vectors | Query and document fed **together** through one model that outputs a relevance score |
| Precompute | Document vectors indexed ahead of time | Nothing; must run per (query, doc) pair at query time |
| Quality | Good | Substantially better: sees exact interactions between query and document words |
| Cost | Milliseconds over millions of docs | Tens to hundreds of ms for ~50–100 pairs (GPU helps) |
| Role | Recall: get candidates | Precision: order the final few |

- The standard funnel: **hybrid retrieve top 50–100 → cross-encoder rerank → top 5–10 to the LLM**. A reranker is often the single biggest quality win after fixing ingestion.
- **Late-interaction models** (ColBERT-style) sit in between: per-token vectors for documents, matched with a cheap "MaxSim" at query time; better than bi-encoders, cheaper than cross-encoders, at a higher storage cost.
- LLMs can also rerank (listwise prompts), at much higher latency and cost; reserve for small candidate sets or offline evaluation.

---

## Picking a Store

| Option | Pick when |
|---|---|
| **pgvector** (Postgres) | Up to a few million vectors; you want transactions, joins, existing ACL/tenant tables, one less system; HNSW/IVF supported |
| **Elasticsearch / OpenSearch** | You already run it; need strong BM25, hybrid and filters in one engine ([[22 - Elasticsearch]]) |
| **Dedicated vector DBs** (Qdrant, Milvus, Weaviate, Pinecone) | Tens of millions+ vectors, heavy filtered search, quantization and multi-tenancy features, managed scaling |
| **Vespa** | Large-scale hybrid ranking with complex ranking expressions |
| **FAISS / in-process** | Offline jobs, embedded use, research; you build persistence and updates yourself |

- Whatever the store: keep the **source of truth elsewhere** (chunk store + registry), make indexing idempotent, and plan for re-embedding migrations ([[06 - RAG Ingestion and Indexing Pipeline]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Post-filter starvation** | Low-volume tenants get empty results | Filter-aware single-stage search; per-tenant partitions for large tenants |
| **Pre-filter connectivity loss** | Recall collapses on selective filters with graph indexes | Engine-level filtered HNSW; brute force when the allowed set is small |
| **Dense misses exact identifiers** | SKUs, error codes, function names not found | Hybrid BM25/SPLADE + dense with RRF |
| **RAM blow-up** | Index doesn't fit; swapping kills latency | Quantization (int8, binary with rescoring), Matryoshka truncation, IVF-PQ or DiskANN |
| **Recall drift after deletes** | HNSW tombstones degrade the graph | Periodic compaction / rebuild; monitor recall on a fixed eval set |
| **IVF centroid drift** | New data distribution doesn't match trained cells | Periodic re-training of centroids |
| **Mixing score scales** | Weighted fusion of raw BM25 and cosine scores behaves erratically | RRF, or normalise and tune on evals |
| **No reranker** | Right chunk retrieved but ranked 14th, never shown | Cross-encoder rerank of top 50–100 |

---

## Interview Lines

> [!tip] Say it like this
> - "Ten million 1536-dim float32 vectors is about 61 GB raw, so I'd look at int8 or binary quantization with rescoring, or Matryoshka truncation."
> - "HNSW is the default up to tens of millions in RAM; IVF-PQ or DiskANN when memory, not latency, is the constraint."
> - "Post-filtering starves small tenants; I use filter-aware search, and give the largest tenants their own partitions."
> - "Dense vectors miss SKUs and error codes, so I run BM25 alongside and fuse with Reciprocal Rank Fusion, which needs no score normalisation."
> - "Retrieval is for recall, a cross-encoder reranker is for precision: retrieve 50–100, rerank, send the top 5–10."

## Related Notes

- [[06 - RAG Ingestion and Indexing Pipeline]] · [[07 - Agentic RAG and Grounding Loops]] · [[09 - Storage Search and Geo]] · [[22 - Elasticsearch]]
- [[Agentic/README]]
