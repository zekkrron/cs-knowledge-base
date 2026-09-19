# 09 — Ask AI: Hybrid Tool-Search Engine

Resume mapping: *"Re-architected the platform's hybrid tool-search engine utilizing
character-count routing, kNN retrieval, and Cross-Encoder reranking with adaptive
tail-distribution thresholding, reducing search noise by ~70% with zero relevant-result
dropout across all query categories."*

> Source: this feature lives in the `self_api_registration` repo on the **`ask-ai`** branch
> (tracking `origin/ask-ai`), under `self_api_registration/src/`. It is a separate deployable
> from the Catalog Service (own FastAPI app + ingestion worker). Sibling branches:
> `origin/ask-ai-dev`, `origin/askai-vertex-migration`, `origin/agentic-rag`, `origin/managed-rag`.

The engine answers: "given a natural-language query, which registered tools/agents/MCP-servers/
data-transformers are relevant?" It is what powers tool discovery for the AI ecosystem, over the
same 600+ tool registry described in `02-tool-registry.md`.

---

## 1. Two apps, one codebase

The `ask-ai` branch adds a second top-level structure under `src/` alongside `catalog/`:

| Path | Role |
|---|---|
| `src/main_api.py` | FastAPI app "BreakPoint Ask AI" — the query-time search + RAG endpoints. |
| `src/main_worker.py` | The Kafka ingestion worker entrypoint. |
| `src/entrypoint.py` | Chooses API vs worker via `RUN_MODE`. |
| `src/rag_ingest.py` | Bulk/one-off ingestion. |
| `src/ask_ai/` | Search-specific logic: reranking, cutoffs, query building, filters, extractors, config, Vertex client. |
| `src/core/` | Clean-architecture engines + interfaces (`api_engine.py`, `worker_engine.py`, `rag_engine.py`, `interfaces.py`). |
| `src/infrastructure/` | Adapters: OpenSearch store, document-RAG store, Gemini embedding model, Kafka stream. |
| `src/rag/` | The team-scoped document-RAG pipeline (chunker, parser, generator, reranker) — a sibling feature sharing the cross-encoder + embedding model. |

Clean-architecture seam: `core/interfaces.py` defines `BaseEmbeddingModel`, `BaseVectorStore`,
`BaseExtractor`, `BaseEventStream`. Engines depend on these abstractions; concrete adapters live
in `infrastructure/`. This is why the same `SearchEngine` works over OpenSearch + Gemini without
knowing either.

---

## 2. Query-time search flow — `src/core/api_engine.py` (`SearchEngine.execute_search`)

Endpoint: `GET /api/v1/search?q=&resource_type=&page=&limit=` (`main_api.py`). Filters default
to `get_active_only_filter()` (`status ∈ {live, published, draft, active}`) plus an optional
`{"term": {"resource_type": ...}}`. Pipeline:

1. **Embed the query** — `model.generate_vector(query_text, task_type="RETRIEVAL_QUERY")`
   (Gemini embedding, `EMBEDDING_MODEL = gemini-embedding-2`, 3072 dims).
2. **Hybrid retrieval (kNN + BM25, top 30)** — `db.search(query_vector, filters, limit=30,
   query_text)`. Detailed in §3.
3. **Rerank + cutoff** — routed by `RERANKER_TYPE`:
   - `"cross-encoder"` → `cross_encoder_rerank(...)` (local Cross-Encoder + TAZS, §4).
   - else (`"gemini"`, default) → `llm_score_and_cutoff(...)` (Gemini Flash-Lite scoring +
     per-resource-type cutoff, §5).
4. **Paginate** — `final_results[offset:offset+limit]`.

Timing is logged at each stage (`embedding`, `hybrid`, `llm`, `total`). On empty hybrid results
it short-circuits to `[]`.

The `RERANKER_TYPE` switch is the "character-count / strategy routing" seam: a query can be
routed to the cheap local Cross-Encoder or the Gemini LLM judge depending on configuration/
candidate profile. On this branch the selection is config-driven; the Cross-Encoder path is the
noise-reduction workhorse (TAZS), the Gemini path is the alternative scorer.

---

## 3. Hybrid retrieval — `src/infrastructure/repositories/elasticsearch_store.py`

`ElasticsearchStore` wraps OpenSearch (managed, IAM or basic auth; SSL when `ES_PORT==443`).

### 3.1 Index mapping (`setup_index`)
Index `agentic-rag` (`ES_INDEX`). Key fields:
- `embedding` — `knn_vector`, **dimension 3072**, HNSW method, `space_type=cosinesimil`,
  `engine=faiss`.
- `chunk_id`/`resource_id`/`resource_type`/`status`/`team`/`domain`/`tags`/`has_pii` — keyword/
  scalar filters.
- `version_number`, `lifecycle_rank` — for versioned resources.
- `semantic_summary`, `semantic_text` — analyzed text for BM25.
- `endpoint`, `endpoint_description`, `example_questions`, `raw_spec` — stored but
  `index: False` (retrieved for reranking/response, not searched).
- Index setting `knn: true`.

### 3.2 `search(...)` — the hybrid query
1. **kNN branch** — `size=20`, `knn` query on `embedding` (`k=20`) with a `bool.filter` of the
   status/resource-type filters.
2. **BM25 branch** — `size=20`, `multi_match` over `name^3`, `semantic_summary^2`,
   `semantic_text` (`best_fields`, `minimum_should_match=1`) with the same filters.
3. **Merge + dedup** — concatenate `knn_hits + bm25_hits`, dedup by `resource_id` (first wins),
   return `results[:limit]` (30). This is the "hybrid" set fed to the reranker.

Other methods: `save_document` (upsert by `chunk_id` as `_id`), `handle_deletion`
(delete-by-query on `resource_id`, optionally scoped by `resource_type`),
`get_all_chunk_ids_for_resource`.

---

## 4. Cross-Encoder reranking + TAZS — `src/ask_ai/cross_encoder_reranker.py` + `reranking_core.py`

### 4.1 The model (`reranking_core.py`)
Lazy singleton `get_model()` loads a `sentence_transformers.CrossEncoder`. Supported models
(`SUPPORTED_MODELS`): `ms-marco-MiniLM-L-6-v2` (default, `CROSS_ENCODER_MODEL`),
`mxbai-rerank-xsmall-v1`, `ms-marco-TinyBERT-L-2-v2`. `warmup()` pre-loads at startup
(`main_api.startup_event`) so the first request is fast. The model is **shared** by both the
tool search (`cross_encoder_reranker.py`) and the document-RAG reranker (`rag/reranker.py`).

### 4.2 Reranking (`cross_encoder_rerank`)
1. Build a document text per candidate (`_build_document_text`): `Name | Team | Summary |
   Description[:300] | Examples`.
2. Score all `(query, doc)` pairs in one `model.predict(pairs)` batch.
3. Sort by score descending.
4. Compute the **TAZS threshold** over all scores; keep candidates with `score >= threshold`
   (cap 20). If TAZS keeps zero (degenerate), **fallback to top-20** so a query never returns
   empty when candidates exist (this is the "zero relevant-result dropout" guarantee).
5. Attach `llm_score = round(score, 4)` to each kept result.

### 4.3 TAZS — "Tail-Anchored Z-Score" thresholding (`tazs_threshold`)

> **Naming note (read this first).** "TAZS" / "Tail-Anchored Z-Score" is **not an established,
> textbook algorithm** — it is a name coined during development (by the AI coding assistant used
> at the time) for a small, custom heuristic. Do not present it as a known published method. What
> it *actually is*, in standard terms: a **per-query, one-sided outlier-detection threshold** that
> uses a **z-score (standard score)** computed from the **low-scoring tail** of the results to
> estimate a noise floor, then keeps only results that sit clearly above that floor. Everything it
> relies on (mean, standard deviation, z-score, outlier cutoff) is standard statistics; only the
> name and the specific recipe are bespoke.

**The idea behind it.** A cross-encoder gives every candidate a raw relevance score, but those
scores are not calibrated — a "good" score for one query can be a "bad" score for another. A
fixed cutoff (e.g. "keep score > 0.5") therefore fails: it's too strict on hard queries (drops
real matches) and too loose on easy ones (lets noise through). So instead of a fixed number, we
derive the cutoff **from the query's own score distribution**:

1. Sort scores high→low. The **bottom `tail_size` (10)** results are, by assumption, the
   irrelevant ones — they form the "noise band."
2. Compute that tail's **mean** and **standard deviation (σ)**. That's the center and spread of
   the noise.
3. Set the threshold `tail_mean + z_multiplier(2.5)·σ` — i.e. "clearly above the noise band."
   In z-score terms, anything more than 2.5 standard deviations above the noise mean is treated
   as signal, not noise.
4. Keep every result at or above that threshold; drop the rest.

This adapts automatically: a query with a few strong matches gets a threshold that lets them
through, while a vague query where everything scores similarly ends up with almost nothing above
the noise band (correctly returning little). It's conceptually a cousin of **statistical outlier
detection** (z-score method) and **knee/elbow detection** — "find where the scores stop looking
like background noise."

**Worked example.** Say 30 candidates score, sorted: `[7.9, 6.2, 5.8, ... , -3.1, -3.4, -3.0,
-2.9, ...]` and the bottom 10 (the tail) have `mean = -3.0`, `σ = 0.4` (floored to
`min_sigma=0.5`). Threshold = `-3.0 + 2.5·0.5 = -1.75`. The three results at `7.9/6.2/5.8` are
far above `-1.75` → kept; the tail near `-3` → dropped. On a different, vaguer query where the
top score is only `-1.9`, the threshold `-1.75` correctly keeps almost nothing.

**Safety rails in the code:**
- `min_sigma=0.5` — floors σ so a freak, near-flat tail can't collapse the band to a razor edge.
- `safety_cap=0.0` — the threshold is never allowed *above* 0 (never so strict it rejects
  genuinely positive scores).
- `absolute_floor=-8.5` — a hard lower bound; also returned directly when there are fewer than
  `tail_size` results (not enough data to estimate a noise band).
- Paired with the **top-20 fallback** in the caller (§4.2): if the threshold happens to keep
  zero results, return the top 20 anyway — this is the "zero relevant-result dropout" guarantee.

The exact recipe:
```python
tazs_threshold(scores, tail_size=10, z_multiplier=2.5, min_sigma=0.5,
               safety_cap=0.0, absolute_floor=-8.5):
    if len(scores) < tail_size: return absolute_floor
    tail = scores[-tail_size:]                     # the lowest-scoring tail
    tail_mean = mean(tail)
    tail_sigma = max(std(tail), min_sigma)
    threshold = tail_mean + z_multiplier * tail_sigma
    return clamp(threshold, low=absolute_floor, high=safety_cap)
```
One-line summary for an interview: *"It's a per-query adaptive cutoff — I estimate the noise
floor from the mean and standard deviation of the lowest-scoring results, keep only what sits
~2.5σ above that floor, and fall back to top-20 if nothing clears it. The name TAZS is just a
label I used; the mechanism is a one-sided z-score outlier cutoff."*

---

## 5. Gemini LLM scoring + per-type cutoff — `src/ask_ai/llm_cutoff.py`

The alternate reranker (`RERANKER_TYPE="gemini"`, default) uses Gemini Flash-Lite as a relevance
judge (Vertex AI via service account, `LLM_RERANK_MODEL = gemini-2.5-flash-lite`).

- **Resource-type-aware prompts** — separate 0–10 scoring prompts for `tool`, `agent`,
  `mcp_server`, `data_transformer` (`SCORING_PROMPT`, `AGENT_SCORING_PROMPT`,
  `MCP_SCORING_PROMPT`, `DATA_TRANSFORMER_SCORING_PROMPT`). The tool prompt scores against
  name/team/summary/description/example_questions; the block is JSON-formatted
  (`_format_tools_block`, etc.).
- **Per-type cutoff** — `LLM_CUTOFF_SCORE_TOOL=2`, `_AGENT=2`, `_MCP=5`,
  `_DATA_TRANSFORMER=2` (from `config.py`, env-overridable). The resource type is read from the
  first result (`results[0]["resource_type"]`).
- **Flow** — one `generate_content` call (temperature 0, max 256 tokens) returns a
  comma-separated score list; `_parse_scores` parses/pads/truncates to the candidate count; sort
  by score desc, keep `score >= cutoff`, attach `llm_score`. On any exception it **fails open**
  (returns the unfiltered hybrid results) so search never hard-fails.

---

## 6. Ingestion pipeline (how the index gets populated)

Search quality depends on what's indexed. The ingestion side is event-driven off the Catalog's
Kafka RAG events (`02-tool-registry.md` §3.3 / §4.4 — Catalog emits `tool`/`mcp_server` events;
this consumer indexes them).

### 6.1 Worker — `src/core/worker_engine.py` (`IngestionEngine.run`)
Consumes envelopes from a `BaseEventStream` (Kafka topic `catalog.resources.events`,
`KAFKA_GROUP_ID=rag-ingestion-worker-v1`). Per message:
1. **Soft delete** — if `is_deleted`, `db.handle_deletion(resource_id, resource_type)`.
2. **Route to extractor** by `resource_type` (`_get_extractor`); unsupported types are ignored.
3. **Extract chunks** (`extractor.extract_chunks(payload)`).
4. **Blind upsert** — embed each chunk's text (`model.generate_vector`, `RETRIEVAL_DOCUMENT`)
   and `db.save_document(chunk_id, vector, metadata)`; per-chunk errors are logged and skipped.
5. **Ghost-chunk reconciliation** — for **non-versioned** resources, delete chunks in the index
   no longer produced by this payload. **Skipped for versioned** resources (tools/agents) where
   multiple versions coexist by design.

### 6.2 Extractors — `src/ask_ai/extractors.py`
Deterministic, **no-LLM** chunk construction (embedding text is built by rule, not generated):
- `BreakPointToolExtractor` — versioned (`chunk_id = tool_{rid}_v{n}_chunk_0`). Builds embedding
  text via `_build_tool_embedding_text` (name, team, summary, then per-endpoint
  `METHOD /path + description + x-example-questions`). Extracts `endpoint`,
  `endpoint_description`, `example_questions` for the reranker prompt; carries `domain`, `tags`,
  `has_pii`, `lifecycle_rank`.
- `BreakPointAgentExtractor` — versioned; embeds name/team/description.
- `BreakPointMcpExtractor` — **not** versioned (`chunk_id = mcp_server_{rid}_chunk_0`, overwrites
  on update).
- `BreakPointDataTransformerExtractor` — not versioned.
- `LIFECYCLE_RANKS = {live:3, published:2, draft:1, active:1}` — `lifecycle_rank` lets the index
  prefer the most-promoted version.

---

## 7. Document RAG (`/api/v1/rag/ask`) — sibling feature — `src/core/rag_engine.py` + `src/rag/`

A team-scoped knowledge-base RAG that shares the embedding model and Cross-Encoder but uses a
**separate index** (`RAG_ES_INDEX = document-rag`, adapter `DocumentRagStore`). Endpoint:
`GET /api/v1/rag/ask?query=&team_id=&doc_id=&mode=`.

`RagEngine.ask` flow:
1. Embed the query (`RETRIEVAL_QUERY`).
2. Hybrid search scoped by `team_id` (+ optional `doc_id`), top 30.
3. `rag/reranker.rerank` — same Cross-Encoder + **TAZS** (writes `rerank_score`).
4. `mode="search"` → return reranked chunks; `mode="generate"` (default) → take top
   `RAG_MAX_CONTEXT_CHUNKS=8` and `rag/generator.generate_answer` (Gemini
   `RAG_GENERATION_MODEL = gemini-2.5-flash`) returns `(answer, sources)`.
5. Fail-soft: generation failure degrades to returning search results.

Supporting `rag/`: `chunker.py` (page-aware chunking, `CHUNK_TARGET_CHARS=3500`, never splits a
page), `parser.py`, `generator.py`, `schemas.py`.

---

## 8. Configuration — `src/ask_ai/config.py`

Key env vars: `ES_HOST/ES_PORT/ES_INDEX/ES_AUTH_TYPE`, `KAFKA_BROKERS/KAFKA_TOPIC/
KAFKA_GROUP_ID`, `GOOGLE_CREDENTIALS_JSON`, `EMBEDDING_MODEL=gemini-embedding-2`,
`LLM_RERANK_MODEL=gemini-2.5-flash-lite`, `LLM_CUTOFF_SCORE_{TOOL,AGENT,MCP,DATA_TRANSFORMER}`,
`CROSS_ENCODER_MODEL=ms-marco-MiniLM-L-6-v2`, `RERANKER_TYPE={gemini|cross-encoder}`,
`AWS_REGION=ap-south-1`, `RUN_MODE={api|worker}`, `RAG_ES_INDEX`, `RAG_GENERATION_MODEL`,
`RAG_MAX_CONTEXT_CHUNKS=8`. Logger name: `breakpoint_rag`.

---

## 9. How the resume claim decodes

- **Hybrid tool-search engine** → §2–§3: kNN (Gemini embeddings on OpenSearch/FAISS HNSW) +
  BM25 multi_match, merged and deduped by `resource_id`.
- **kNN retrieval** → §3.2 kNN branch (`k=20`, cosine, 3072-dim).
- **Cross-Encoder reranking** → §4: local `sentence-transformers` CrossEncoder scoring the
  hybrid candidates.
- **Adaptive tail-distribution thresholding** → §4.3 TAZS: per-query threshold anchored to the
  score-distribution tail (noise floor) + z·σ.
- **Character-count / strategy routing** → §2 `RERANKER_TYPE` selection between the local
  Cross-Encoder path and the Gemini judge, plus per-resource-type cutoffs (§5). (Query-length /
  candidate-budget routing refinements also exist on sibling branches `ask-ai-dev` /
  `askai-vertex-migration`.)
- **~70% noise reduction, zero relevant-result dropout** → TAZS removes the low-scoring tail
  while the top-20 fallback (§4.2 step 4, and `rag/reranker`) guarantees a non-empty result set
  when any candidate exists.

---

## 10. Exact locations (branch `ask-ai`)

| Concern | File |
|---|---|
| Search API + RAG API | `src/main_api.py` |
| Search orchestration (routing, timing) | `src/core/api_engine.py` |
| Hybrid kNN+BM25 store + index mapping | `src/infrastructure/repositories/elasticsearch_store.py` |
| Cross-Encoder rerank | `src/ask_ai/cross_encoder_reranker.py` |
| Model singleton + TAZS threshold | `src/ask_ai/reranking_core.py` |
| Gemini scoring + per-type cutoff | `src/ask_ai/llm_cutoff.py` |
| Status pre-filter | `src/ask_ai/query_builder.py`, `src/ask_ai/filters.py` |
| Ingestion worker | `src/core/worker_engine.py`, `src/main_worker.py` |
| Extractors (tool/agent/mcp/transformer) | `src/ask_ai/extractors.py` |
| Clean-arch interfaces | `src/core/interfaces.py` |
| Document RAG | `src/core/rag_engine.py`, `src/rag/` |
| Config | `src/ask_ai/config.py` |
| Vertex client | `src/ask_ai/vertex_client.py` |
