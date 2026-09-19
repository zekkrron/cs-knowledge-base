---
tags: [resume/delhivery, status/draft]
created: 2026-09-19
---
# Ask AI Search

> [!abstract] `resume:ask-ai`. Hybrid retrieval (kNN + BM25) then rerank. "TAZS" is **our nickname**, not a paper. Character-count routing on the resume vs **config `RERANKER_TYPE`** in the docs — reconcile before you say it. Branch `ask-ai`, not default Catalog. Doc: [[09-ask-ai-search]].

## Resume line

Hybrid tool-search: character-count routing, kNN, Cross-Encoder, adaptive tail thresholding, ~70% less noise, zero relevant-result dropout.

## Truth

Separate FastAPI (`main_api`) + Kafka worker (`main_worker`). Clean architecture: engines vs OpenSearch / Gemini adapters.

**Query:** embed (`gemini-embedding-2`, 3072-d) → hybrid top ~30 → rerank → page.

**Hybrid:** kNN 20 on `embedding` (HNSW, cosine, faiss) **plus** BM25 20 on `name^3`, `semantic_summary^2`, `semantic_text`. Merge, dedup `resource_id`.

**Rerank switch `RERANKER_TYPE`:**
- `cross-encoder` — local MiniLM (etc.), batch `predict`, then TAZS cutoff.
- else Gemini Flash-Lite scores 0–10, **per-type cutoff** (mcp_server stricter). Fail-open to hybrid on LLM error.

Docs say on this branch the switch is **config**, not "if query length then X." Resume says **character-count routing**. If you built a char-count router on another revision, say so. If not, **do not say character-count**.

**TAZS:** sort scores; tail of 10 = noise; threshold = `tail_mean + 2.5σ` (σ floored 0.5); clamp. Keep `score >= threshold`. If that set is empty → **top 20**. That last line is "zero dropout" in **code**. ~70% noise needs an eval set you can describe.

Publish path: Catalog Kafka → worker upserts `agentic-rag`.

## One-minute pitch

Keyword search on 600 tools is either empty or a dump. I embed the query, take dense + sparse hits, then a cross-encoder that *sees query and document together*. I do not use a global score cutoff — I estimate noise from the bottom of **this** query's list and keep outliers above it. If the heuristic keeps nothing, I still return top-20 so the UI never goes blank when OpenSearch had hits.

## Ugly questions

**Why hybrid?** Names/ids like BM25. Paraphrase likes kNN. Merge.

**Why not only LLM rerank?** Cost/latency; Gemini path exists and fail-opens.

**TAZS paper?** No. One-sided z-score on the tail. Say that first.

**Dropout vs empty index?** Fallback only if hybrid returned candidates. Empty hybrid → `[]`.

**70%?** Define noise (precision@k? human labels?). Before/after on the same query set. Or drop the number.

**PII in the index?** `has_pii` is a field. What is filtered at query time — fill.

**Stale after unpublish?** Worker delete-by-query on `resource_id`. Lag = Kafka + index refresh.

**3072 dims / cost?** Gemini embedding size. Tradeoff vs a smaller model.

## Related Notes

- [[00 - Ownership and How to Talk]]
- [[02 - Catalog and Gateway]]
- [[09-ask-ai-search]]
