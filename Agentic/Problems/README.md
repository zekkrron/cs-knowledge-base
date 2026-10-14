---
tags: [agentic/problems, status/draft]
created: 2026-10-11
---
# Agentic Practice Designs

> [!abstract] What this is
> Full interview-style walkthroughs of AI system designs. Each one runs requirements → estimation → architecture → deep dives → evals → failure modes, and links back to the topic notes for every mechanism it uses.

| # | Design | Exercises most |
|---|---|---|
| 01 | [[01 - Customer Support Agent with Refunds]] | Workflow vs agent, safe side effects, idempotency, HITL, grounded answers, cost at scale |
| 02 | [[02 - Multi-Tenant Document RAG]] | Ingestion, permissions in retrieval, tenant isolation, freshness, hybrid search |
| 03 | [[03 - Coding Agent]] | Execution loops with ground truth, context for large repos, sandboxing, stopping |
| 04 | [[04 - Voice Booking Agent]] | Realtime latency budget, VAD and barge-in, slot holds, handoff |
| 05 | [[05 - Deep Research Agent]] | Multi-agent fan-out, context isolation, citation verification, injection from web content |
| 06 | [[06 - Text-to-SQL Analytics Assistant]] | Evaluator-optimizer with real validation, semantic layer, row-level security, result-set evals |
| 07 | [[07 - LLM Inference Platform]] | GPU serving, KV cache, prefix-aware routing, disaggregation, multi-LoRA, capacity planning |
| 08 | [[08 - ChatGPT-Scale Consumer Chat]] | GPU capacity and quotas, streaming at scale, context assembly, model routing, degradation, multi-region |
| 09 | [[09 - Enterprise Search over SaaS Connectors]] | Connector framework, identity and permission graph, indexed vs federated search, workplace ranking, delegated actions |
| 10 | [[10 - Batch Document Extraction]] | Schema-constrained extraction with evidence, deterministic validation, confidence routing, human review, batch economics |
| 11 | [[11 - LLM Content Moderation Pipeline]] | Cost-aware cascade, policy-as-prompt, fail-open vs fail-closed, adversarial evasion, precision/recall per category |
| 12 | [[12 - Multi-Tenant Agent Platform]] | Control vs data plane, tenant isolation of data/secrets/compute, tool connectivity, durable runtime, evals as a feature |

## How to Practise

- Timebox 40 minutes: 5 requirements, 5 estimation, 10 architecture, 15 deep dives, 5 failure modes and evals.
- Say the numbers out loud: tokens per request, cost per task, latency budget per stage, VRAM per replica.
- For every component, answer "what happens when it fails?" and "how would you know it's wrong?"

## Related Notes

- [[Agentic/README]] · [[Agentic Problem to Solution Map]]
