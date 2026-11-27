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
| **5. Specialised agents and modalities** | 16–17 | Full: computer-use and browser agents; multimodal (vision, documents, audio, video) |
| **6. Knowledge representation** | 18 | Full: knowledge graphs and GraphRAG, as an extension of layer 2 |
| **7. Infrastructure and training systems** | 19–20 | Architect depth, for inference-provider, GPU-cloud and MLE loops: GPU clusters, training parallelism, RL systems |
| **8. ML foundations** | 21 | Quick reference: one answer per classic question, for big-tech MLE and applied-scientist screens |

- **By role track** (from [[Syllabus and Coverage#Axis C: Who Asks]]): applied AI engineers need layers 1–3 and the product rounds; inference and infra engineers need layer 4 at full depth plus the estimation and infra-design problems (07, 13–15); forward-deployed engineers need layers 1–3, the build round and the deep dive.

---

## Topics

| # | Topic | What it covers |
|---|---|---|
| 00 | [[00 - LLM Fundamentals for Engineers]] | Tokens and the Indian-language token tax, context windows and long context vs RAG, transformer internals (attention, GQA, RoPE, norms, MoE), sampling, the stateless chat API, tool-calling mechanics, structured outputs, prompting (CoT, self-consistency), reasoning models inside tool loops (interleaved thinking, passing reasoning back), latency anatomy |
| 01 | [[01 - Workflows vs Agents]] | When not to build an agent; prompt chaining, routing, parallelisation, orchestrator-workers, evaluator-optimizer |
| 02 | [[02 - Execution Loops and Control Topologies]] | ReAct, Plan-and-Execute, ReWOO, LLMCompiler, Tree of Thoughts, Reflexion, DAGs vs cyclic state machines, loop termination, the harness as a whole (seven layers mapped to the notes) |
| 03 | [[03 - Tool Engineering and Action Spaces]] | Schemas, tool granularity, errors as observations, idempotency, delegated identity, code mode and skills, workload identity (SPIFFE + token exchange), MCP (2026-07-28 spec and OAuth), A2A v1.0, agent commerce and payment protocols, tool permissions |
| 04 | [[04 - State Memory and Context Engineering]] | Transcripts vs structured state, compaction, memory tiers and products, long-running multi-session harnesses, async memory writes |
| 05 | [[05 - Multi-Agent Topologies and Concurrency]] | Single vs multi-agent, router / supervisor / swarm, scoped handoffs, fan-out and reducers |
| 06 | [[06 - RAG Ingestion and Indexing Pipeline]] | Parsing, chunking strategies, embedding choice, metadata, freshness and re-indexing |
| 07 | [[07 - Agentic RAG and Grounding Loops]] | Query decomposition, routing, CRAG, Self-RAG, GraphRAG, citation verification |
| 08 | [[08 - Vector DB Internals and Hybrid Search]] | HNSW, IVF-PQ, DiskANN, filtering, hybrid search and RRF, reranking, fine-tuning the embedder and reranker, Matryoshka, quantization |
| 09 | [[09 - Durable Execution HITL and Realtime UX]] | Temporal-style replay, checkpointing, human-in-the-loop, always-on agents (inbox, timers, memory, deferred permissions, outbox), streaming, AI UX (progress, uncertainty, citations, actions, feedback design, generative UI), voice |
| 10 | [[10 - AI Gateway Security and Guardrails]] | Gateway control plane, token rate limits, prompt injection, guardrail tools, OWASP Agentic Top 10, red-teaming pipelines, sandboxing, RBAC, PII, DPDP and the EU AI Act timeline |
| 11 | [[11 - Evals Observability and Silent Drift]] | Tracing, three-tier evals, RAG metrics and how they're computed, simulated users and pass^k, designing trustworthy agent evals, shipping prompt and model changes, product metrics and the launch decision (metric stack, A/B tests, over-refusal), LLM-as-judge bias, eval datasets in CI, eval statistics (SEs, clustering, paired tests, power), hallucination detection without references, quality SLOs and AI incident response, semantic caching risks, embedding migrations |
| 12 | [[12 - LLM Cost and Latency Economics]] | Token pricing, prompt caching, model routing, batching, budgets, unit economics and pricing models (seat, usage, outcome), perceived latency, API vs self-hosting, on-prem, air-gapped and sovereign deployment |
| 13 | [[13 - GPU Serving and Disaggregated KV Cache]] | Prefill vs decode, continuous batching, PagedAttention, RadixAttention, disaggregated serving, KV networks, prefix-aware routing, model registry, cold starts and admission control |
| 14 | [[14 - Quantization Decoding and VRAM Math]] | BF16 to INT4, KV-cache compression, constrained decoding, speculative decoding, VRAM and cost arithmetic, roofline and sharded-matmul arithmetic, tensor-parallel communication |
| 15 | [[15 - Post-Training and Model Adaptation]] | Prompt vs RAG vs fine-tune, automatic prompt optimisation (DSPy, GEPA), SFT, LoRA and multi-LoRA serving, distillation, RLVR / GRPO, RL environments and reward design for agents, test-time compute, the data flywheel |
| 16 | [[16 - Computer Use and Browser Agents]] | When a UI agent is right, observation and action, verify-then-retry, isolated browsers and MFA hand-off, page injection, evaluation |
| 17 | [[17 - Multimodal Systems]] | Image and video token costs, OCR vs vision model vs ColPali page-image retrieval, region citations, speech pipelines and WER, video sampling, per-modality evals, injection and PII in images |
| 18 | [[18 - Knowledge Graphs and GraphRAG]] | Property graphs vs RDF, LLM extraction with provenance, entity resolution, local / global / DRIFT search, text-to-Cypher safety, LazyGraphRAG, LightRAG, HippoRAG 2, RAPTOR, upkeep and deletes, ACLs on edges, evals against vector baselines |
| 19 | [[19 - GPU Cluster Infrastructure and Operations]] | Node and fabric anatomy (NVLink, InfiniBand, GPUDirect), Kubernetes GPU scheduling (device plugin, MIG, gang scheduling), fragmentation, node health (DCGM, Xid, cordon and drain), GPU virtualization, NCCL failures and checkpoints, the USE method for slow hosts |
| 20 | [[20 - Training and RL Systems at Architect Depth]] | Training memory and FLOPs arithmetic, DP / FSDP / TP / PP / EP / context parallelism, pipeline bubbles, FlashAttention and long sequences, vocab-parallel logits, MoE pretraining, asynchronous RL post-training systems |
| 21 | [[21 - ML Foundations Quick Reference]] | Bias and variance, classic models (logistic regression, Naive Bayes, forests vs boosting), deep-learning families, metrics and calibration, probability and statistics, causal inference basics |

## Practice and Mapping

| Note | What it is |
|---|---|
| [[Agentic/Problems/README\|Practice Designs]] | Sixteen interview-style walkthroughs: support agent with refunds, multi-tenant document RAG, coding agent, voice booking agent, deep research agent, text-to-SQL assistant, LLM inference platform, ChatGPT-scale chat, enterprise search over SaaS connectors, batch document extraction, content moderation, multi-tenant agent platform, prompt playground, model weight distribution to a GPU fleet, GPU job scheduler for video generation, AI gateway |
| [[Syllabus and Coverage]] | The checklist: every leaf topic an AI-engineering interview can ask, by domain, format and company type, with where it's covered and what's missing |
| [[Agentic Problem to Solution Map]] | Every recurring AI-system problem mapped to its standard solutions, trade-offs and when to pick each |
| [[AI Engineer Round Drills]] | The non-design rounds: 17 LLM-flavoured coding drills with tested code (including BPE, Merkle repo diff, streaming fence parser, span merge, decoding, conversation compaction, VAD, gradient descent), debugging drills, the product/scoping round, the take-home, the live build round, the project deep dive, values rounds, the design-review round, forward-deployed engineering rounds (discovery, scoping, product vs capability gaps, stakeholder scenarios, outcome scoring) |
| [[AI Assisted Coding Rounds/README\|AI Assisted Coding Rounds]] (separate folder) | Coding interviews where you use an AI assistant: formats, company map, grading, method, practice plan |

---

## How the Notes Are Written

- Same shape as the HLD topic notes ([[CONVENTIONS]]): abstract → why it exists → concept sections with "pick when" tables → **Failure Modes** (failure → production fix) → **Interview Lines** → related notes.
- Code is **Python**, kept to the part that teaches the mechanism (an agent loop, a reducer, RRF, a budget guard).
- Numbers from practice ("tool selection degrades past ~15 tools") are marked as **heuristics**, not laws.
- Anything already explained in HLD or LLD (idempotency, rate limiting, sagas, tracing, vector search basics, concurrency) is **linked, not re-explained**.

## Reading Order

00 → 01 → 02–05 → 06–08 → 18 → 09–12 → 13–15 → 16–17 → 19–20 (infra and MLE roles) → 21 (as needed) → Practice Designs → Round Drills → Problem to Solution Map. Each topic leans on the ones before it; the map is for revision.

## Audio Overview Prompt

Upload one note as the NotebookLM source, then Studio → Audio Overview → customise, paste this, generate. Use **Interactive mode → Join** to ask questions by voice.

````text
Explain this AI-engineering note to someone preparing for an SDE-3 interview, following the note's own order, section by section. Do not reorder, skip or jump ahead.

1. Start with the abstract: what this topic is and the problem in real AI systems that makes it necessary.
2. Then each section in the order it appears. For each mechanism, explain what happens step by step at run time (what the model, the orchestrator, the tools or the GPU actually do), then why it is designed that way.
3. For every comparison table, explain each option, when to pick it and what it costs, using the note's "pick when" guidance.
4. Walk through any code in plain words, line by line, and say which mechanism it demonstrates.
5. Keep the numbers the note gives (latency, cost, token counts, VRAM). Say which are measured and which the note marks as heuristics.
6. Cover the Failure Modes: what breaks in production and the fix.
7. End with the Interview Lines, then two or three follow-up questions an interviewer would ask about this topic, with short answers from the note.

Use only the uploaded source. Conversational, but precise.
````

## Related Notes

- [[HLD/Topics/README]] · [[LLD/README]]
