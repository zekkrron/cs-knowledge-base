---
tags: [agentic/topics, status/draft]
created: 2026-10-11
---
# Agentic RAG and Grounding Loops

> [!abstract] What this is
> The online half of RAG when "embed the question, take the top 5, answer" isn't enough: transforming and **decomposing queries**, **routing** to the right source, **grading** retrieval and correcting it (CRAG, Self-RAG), **GraphRAG** for multi-hop and global questions, and the grounding mechanics that keep answers honest: citations, verification and abstention. The decision this helps with: how much intelligence to put in the retrieval loop, and how to bound it.

---

## Why It Exists

- Naive RAG (one retrieval, then generate) fails when:
    + the question is **vague or conversational** ("what about the other plan?"),
    + it needs **several pieces** of information ("compare our refund policy with last year's"),
    + the answer lives in a **different kind of source** (a SQL table, an API, the web),
    + retrieval returns **irrelevant** chunks and the model answers from them anyway,
    + the information **doesn't exist** and the model invents it.
- Agentic RAG puts an LLM in charge of the retrieval loop. That fixes these cases but adds calls, latency and new failure modes (infinite rewriting), so it needs the same budgets as any agent ([[02 - Execution Loops and Control Topologies]]).

---

## The Naive Baseline

```mermaid
flowchart LR
    Q[Question] --> E[Embed] --> R[Retrieve top-k] --> G[Generate with chunks] --> A[Answer]
```

- Always build and evaluate this first. Many systems need only better ingestion ([[06 - RAG Ingestion and Indexing Pipeline]]), hybrid search and a reranker ([[08 - Vector DB Internals and Hybrid Search]]), not agentic loops.

---

## Query Understanding

| Technique | How it works | Pick when |
|---|---|---|
| **Query rewriting / condensation** | Turn a conversational follow-up into a standalone query using the chat history ("other plan" → "Gold plan refund policy") | Any multi-turn chat; cheap and high-value |
| **Multi-query** | Generate 3–5 paraphrases, retrieve for each, merge (e.g. with RRF) | Vocabulary mismatch between users and documents |
| **HyDE** (hypothetical document embeddings) | Generate a hypothetical answer and embed *that*, since answers resemble documents more than questions do | Short or abstract queries; one extra call |
| **Query decomposition** | Split a complex question into sub-questions, retrieve for each, then synthesise | Comparisons, multi-part and multi-hop questions |
| **Metadata extraction** | Pull filters out of the question ("2025", "for enterprise customers") into structured filters | Questions that name time ranges, products, regions |

```python
from pydantic import BaseModel

class QueryPlan(BaseModel):
    standalone_query: str
    sub_questions: list[str] = []
    filters: dict = {}                       # e.g. {"year": 2025, "product": "gold"}

plan = llm_struct(f"History:\n{history}\nUser: {question}\n"
                  "Rewrite as a standalone query, split into sub-questions if needed, "
                  "and extract metadata filters.", QueryPlan)
```

---

## Routing to Sources

- Not everything should be answered from a vector index. A router (LLM, classifier or rules) picks the source per question or sub-question:

| Question type | Best source |
|---|---|
| Policy, how-to, explanations | Vector + keyword search over documents |
| Counts, sums, filters over records ("how many orders failed yesterday?") | **Text-to-SQL** tool over a read-only replica with a restricted schema |
| Live state ("where is my order?") | An **API tool** |
| Recent public information | **Web search** tool |
| Relationship / multi-hop questions ("which suppliers of X also supply Y?") | **Graph** query ([[#GraphRAG vs Vector RAG]]) |

- Text-to-SQL safety: read-only credentials, an allow-listed schema, row limits, query timeouts, and tenant filters enforced by the database (row-level security), never by the generated SQL ([[10 - AI Gateway Security and Guardrails]]).

---

## Corrective and Self-Reflective RAG

### Corrective RAG (CRAG)

- After retrieval, a **grader** judges whether the chunks are relevant. Depending on the verdict:
    + **Relevant** → generate from them (optionally after stripping irrelevant sentences).
    + **Ambiguous / partly relevant** → refine: rewrite the query and retrieve again, or supplement with another source.
    + **Irrelevant** → don't generate from them: fall back to another source (web search, a different index) or abstain.
- The grader can be a small, cheap model with a strict yes/no output; it doesn't need the large model.

### Self-RAG

- The original Self-RAG trains a model to emit special reflection tokens: decide **whether to retrieve at all**, judge **relevance** of each passage, and check whether its answer is **supported** by the passages. In practice, teams implement the same ideas with prompts and small graders:
    + **Adaptive retrieval**: skip retrieval for questions the model can answer directly (greetings, general knowledge) to save latency and avoid distraction.
    + **Support check** after generation: is every claim backed by a retrieved passage?

### A bounded corrective loop

```python
from typing import Literal

class Grade(BaseModel):
    relevant: Literal["yes", "no", "partial"]

MAX_REWRITES = 2                                   # hard cap: never loop on absent information

def answer(question: str, user: User) -> AnswerWithCitations:
    query = question
    for attempt in range(MAX_REWRITES + 1):
        chunks = hybrid_search(query, filters=acl_filters(user), k=20)
        chunks = rerank(query, chunks)[:6]
        graded = [c for c in chunks
                  if grade_llm(question, c.text, Grade).relevant != "no"]   # small model
        if len(graded) >= 2:
            return generate_with_citations(question, graded)
        query = llm(f"The search '{query}' found nothing useful for '{question}'. "
                    "Write one better search query.")
    return abstain(question)          # deterministic fallback: "I couldn't find this in our docs"
```

- **The infinite-rewrite trap**: when the information simply isn't in the corpus, an uncapped agent keeps rewriting queries forever. Cap rewrites (1–2 is typical), then take a **deterministic fallback**: abstain, offer escalation, or search another source once.

---

## GraphRAG vs Vector RAG

- **Vector RAG** finds chunks *similar* to the question. It struggles with:
    + **multi-hop** questions that require connecting facts across documents,
    + **global** questions about a whole corpus ("what are the main themes in these 5,000 support tickets?"), where no single chunk is similar to the question.
- **GraphRAG** builds a **knowledge graph** at ingestion: an LLM extracts entities and relationships from chunks; the graph is clustered into communities, and each community gets an LLM-written summary (the approach popularised by Microsoft's GraphRAG). At query time:
    + **Local search**: find the entities in the question, walk their neighbourhood, and gather related chunks and relations.
    + **Global search**: map-reduce over community summaries to answer corpus-wide questions.

| | Vector RAG | GraphRAG |
|---|---|---|
| Good at | Specific facts, passages, "what does the policy say about X" | Multi-hop relationships, corpus-wide themes |
| Ingestion cost | Embedding only | Many LLM calls for extraction and summaries (expensive at scale) |
| Freshness | Easy incremental updates | Graph and summaries must be updated as documents change |
| Failure mode | Misses connections across documents | Extraction errors become wrong "facts" in the graph |
| Pick when | Default | Questions are relational or global, and the corpus is stable enough to justify the indexing cost |

- Hybrid designs are common: vector search for passages plus a lightweight entity graph (or a real graph DB for domains that are already graphs, like supply chains or org charts).

---

## Grounding, Citations and Hallucination Prevention

### Generation-side techniques

- **Instruct grounding explicitly**: "Answer only from the provided sources. If they don't contain the answer, say so."
- **Label every chunk with an id** and require citations per claim (`[3]`), so answers are checkable.
- **Quote-then-answer**: for high-stakes answers, first extract the exact relevant quotes, then answer from the quotes.
- **Put retrieved content in delimited blocks** marked as data, not instructions (injected instructions inside documents are a real attack, [[10 - AI Gateway Security and Guardrails]]).
- **Order matters**: the most relevant chunks near the start or end of the context, not buried in the middle.

### Citation verification

```python
def verify_citations(answer: AnswerWithCitations, chunks: dict[str, str]) -> list[str]:
    problems = []
    for claim in answer.claims:
        for cid in claim.citation_ids:
            if cid not in chunks:
                problems.append(f"cited unknown source {cid}")        # fabricated citation
        if claim.quote and not any(claim.quote in chunks[c] for c in claim.citation_ids if c in chunks):
            problems.append(f"quote not found in cited source: {claim.quote[:60]}")
        if not supported_by(claim.text, [chunks[c] for c in claim.citation_ids if c in chunks]):
            problems.append(f"unsupported claim: {claim.text[:60]}")  # NLI model or small LLM judge
    return problems
```

- Checks from cheapest to most thorough: **citation ids exist** → **quoted text appears verbatim** in the source → **entailment / support check** with an NLI model or a small judge model.
- On failure: regenerate once with the problems listed, drop unsupported claims, or fall back to abstaining.

### Abstention

- "I don't know" is a feature. Make it explicit in the output schema (`answerable: bool`), reward it in evals, and route unanswerable questions to humans or search. A system that never abstains will hallucinate on every gap in the corpus.

---

## Latency and Cost Budget

- Each agentic step adds a model call: rewrite (~0.3–1 s), grading (small model, can run in parallel per chunk), reranking (~50–200 ms), generation, verification. A full agentic pipeline can easily be 3–6× the latency of naive RAG.
- Levers: do rewriting and filter extraction in **one** structured call; grade chunks **in parallel** with a small model; skip retrieval adaptively; stream the final answer while verification runs on completed sentences; cache frequent query results ([[12 - LLM Cost and Latency Economics]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Infinite query rewriting** | Information is absent; agent loops and burns budget | Cap rewrites at 1–2; deterministic fallback (abstain or escalate) |
| **Answering from irrelevant chunks** | Confident, wrong answer | Relevance grading (CRAG); explicit grounding instructions; abstention |
| **Conversational follow-ups retrieve nothing** | "What about the other one?" matches nothing | Query condensation with history |
| **Wrong source** | Aggregate questions answered from documents | Router: SQL tool for counts and sums, APIs for live state |
| **Fabricated citations** | Cited ids or quotes don't exist | Verify ids, verbatim quotes, and support |
| **Unsafe text-to-SQL** | Data leak or heavy query on the primary | Read-only replica, allow-listed schema, RLS, timeouts |
| **GraphRAG extraction errors** | Wrong relations treated as facts | Provenance on edges; spot-check; combine with passage retrieval |
| **Latency blow-up** | Every step adds a call | Combine steps; parallel small-model grading; adaptive retrieval; streaming |

---

## Interview Lines

> [!tip] Say it like this
> - "I start with naive RAG plus hybrid search and a reranker, and add agentic steps only where evals show failures."
> - "Conversational queries get condensed into standalone queries; complex ones get decomposed, and a router sends counts to SQL and live state to APIs."
> - "A small grader checks retrieved chunks; if they're irrelevant I rewrite at most twice, then abstain instead of looping."
> - "GraphRAG pays off for multi-hop and corpus-wide questions, at a much higher indexing cost; vector RAG stays the default."
> - "Every claim cites a chunk id, and I verify that cited ids exist, quotes are verbatim, and claims are supported."

## Related Notes

- [[06 - RAG Ingestion and Indexing Pipeline]] · [[08 - Vector DB Internals and Hybrid Search]] · [[02 - Execution Loops and Control Topologies]] · [[11 - Evals Observability and Silent Drift]]
- [[Agentic/README]]
