---
tags: [agentic, status/draft]
created: 2026-10-11
---
# Agentic and AI Systems

> [!abstract] What this is
> Production AI engineering, from how an LLM call works up to GPU serving and post-training, written for one goal: walking into an SDE-3 interview and answering **any** question about agents, RAG, evals, cost, latency or serving infrastructure calmly and with depth.

---

## The Goal

- Be the engineer who can **design, build, run and debug** production AI systems end to end, and talk to infra and ML teams as a peer.
- Concretely, be confident on:
    + **Concepts:** how LLMs, tool calling, agents, memory, RAG and embeddings actually work.
    + **Architecture:** agent topologies, durable execution, gateways, multi-agent systems, retrieval pipelines, and how they map onto classic HLD.
    + **Production reality:** evals, observability, guardrails and security, failure modes and their fixes.
    + **Latency and cost:** where time and money go per request, and every lever that changes them.
    + **Serving internals:** prefill vs decode, KV cache, disaggregated serving, batching, quantization, speculative decoding, VRAM arithmetic.
    + **Model adaptation:** when to prompt vs RAG vs fine-tune, LoRA, distillation, RL with verifiable rewards.
- Market is tough, so the bar is "nothing in the room surprises me", not "enough to pass".

## Depth Policy

| Layer | Topics | Depth |
|---|---|---|
| **Foundations** | 00–01 | Full: everything else is built on these |
| **1. Orchestration and execution** | 02–05 | Full: mechanisms, code, trade-offs, failure modes, design fluency |
| **2. Retrieval and data** | 06–08 | Full |
| **3. Production systems** | 09–12 | Full |
| **4. Serving, hardware, post-training** | 13–15 | Architect depth: explain *why* and *how it works*, do the arithmetic, choose between options. Not CUDA kernels or training-math derivations |

---

## Topics

| # | Topic | What it covers |
|---|---|---|
| 00 | [[00 - LLM Fundamentals for Engineers]] | Tokens, context windows, sampling, the stateless chat API, tool-calling mechanics, structured outputs, prompting, latency anatomy |
| 01 | [[01 - Workflows vs Agents]] | When not to build an agent; prompt chaining, routing, parallelisation, orchestrator-workers, evaluator-optimizer |
| 02 | [[02 - Execution Loops and Control Topologies]] | ReAct, Plan-and-Execute, ReWOO, Reflexion, DAGs vs cyclic state machines, loop termination |
| 03 | [[03 - Tool Engineering and Action Spaces]] | Schemas, tool granularity, errors as observations, idempotency, MCP, A2A, tool permissions |
| 04 | [[04 - State Memory and Context Engineering]] | Transcripts vs structured state, compaction, memory tiers, async memory writes |
| 05 | [[05 - Multi-Agent Topologies and Concurrency]] | Single vs multi-agent, router / supervisor / swarm, scoped handoffs, fan-out and reducers |
| 06 | [[06 - RAG Ingestion and Indexing Pipeline]] | Parsing, chunking strategies, embedding choice, metadata, freshness and re-indexing |
| 07 | [[07 - Agentic RAG and Grounding Loops]] | Query decomposition, routing, CRAG, Self-RAG, GraphRAG, citation verification |
| 08 | [[08 - Vector DB Internals and Hybrid Search]] | HNSW, IVF-PQ, DiskANN, filtering, hybrid search and RRF, reranking, Matryoshka, quantization |
| 09 | [[09 - Durable Execution HITL and Realtime UX]] | Temporal-style replay, checkpointing, human-in-the-loop, streaming, voice |
| 10 | [[10 - AI Gateway Security and Guardrails]] | Gateway control plane, token rate limits, prompt injection, sandboxing, RBAC, PII |
| 11 | [[11 - Evals Observability and Silent Drift]] | Tracing, three-tier evals, LLM-as-judge bias, eval datasets in CI, semantic caching risks, embedding migrations |
| 12 | [[12 - LLM Cost and Latency Economics]] | Token pricing, prompt caching, model routing, batching, budgets, perceived latency |
| 13 | [[13 - GPU Serving and Disaggregated KV Cache]] | Prefill vs decode, continuous batching, PagedAttention, RadixAttention, disaggregated serving, KV networks, prefix-aware routing |
| 14 | [[14 - Quantization Decoding and VRAM Math]] | BF16 to INT4, KV-cache compression, constrained decoding, speculative decoding, VRAM and cost arithmetic |
| 15 | [[15 - Post-Training and Model Adaptation]] | Prompt vs RAG vs fine-tune, SFT, LoRA and multi-LoRA serving, distillation, RLVR / GRPO, test-time compute |

## Practice and Mapping

| Note | What it is |
|---|---|
| [[Agentic/Problems/README\|Practice Designs]] | Twelve interview-style walkthroughs: support agent with refunds, multi-tenant document RAG, coding agent, voice booking agent, deep research agent, text-to-SQL assistant, LLM inference platform, ChatGPT-scale chat, enterprise search over SaaS connectors, batch document extraction, content moderation, multi-tenant agent platform |
| [[Agentic Problem to Solution Map]] | Every recurring AI-system problem mapped to its standard solutions, trade-offs and when to pick each |

---

## How the Notes Are Written

- Same shape as the HLD topic notes ([[CONVENTIONS]]): abstract → why it exists → concept sections with "pick when" tables → **Failure Modes** (failure → production fix) → **Interview Lines** → related notes.
- Code is **Python**, kept to the part that teaches the mechanism (an agent loop, a reducer, RRF, a budget guard).
- Numbers from practice ("tool selection degrades past ~15 tools") are marked as **heuristics**, not laws.
- Anything already explained in HLD or LLD (idempotency, rate limiting, sagas, tracing, vector search basics, concurrency) is **linked, not re-explained**.

## Reading Order

00 → 01 → 02–05 → 06–08 → 09–12 → 13–15 → Practice Designs → Problem to Solution Map. Each topic leans on the ones before it; the map is for revision.

## Related Notes

- [[HLD/Topics/README]] · [[LLD/README]]
