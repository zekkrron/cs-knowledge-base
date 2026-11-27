---
tags: [agentic/problems, status/draft]
created: 2026-10-11
updated: 2026-11-27
---
# Agentic Practice Designs

> [!abstract] What this is
> Interview-style AI system designs, one note per question, in the same format as the HLD problem notes: a clarify script, a simple working **V0**, a **scale ladder** where each step names the number that breaks the previous version, **twist cards** for follow-ups off the ladder (ordered by how often interview reports show them), and a Mermaid diagram for V0 and after every ladder step. Mechanisms live in the [[Agentic/README|Agentic topic notes]] (and HLD mini designs); problem notes link to them instead of re-explaining.

| # | Design | Format | Exercises most |
|---|---|---|---|
| 01 | [[01 - Customer Support Agent with Refunds]] | **new** | Workflow vs agent, safe money-moving tools, idempotency, HITL and handoff ownership, grounded answers, cost at scale |
| 02 | [[02 - Multi-Tenant Document RAG]] | **new** | Ingestion, permissions in retrieval, tenant isolation, freshness, hybrid search |
| 03 | [[03 - Coding Agent]] | **new** | Execution loops with ground truth, context for large repos, sandboxing, stopping |
| 04 | [[04 - Voice Booking Agent]] | **new** | Realtime latency budget, VAD and barge-in, slot holds, handoff |
| 05 | [[05 - Deep Research Agent]] | **new** | Multi-agent fan-out, context isolation, citation verification, injection from web content |
| 06 | [[06 - Text-to-SQL Analytics Assistant]] | **new** | Evaluator-optimizer with real validation, semantic layer, row-level security, result-set evals |
| 07 | [[07 - LLM Inference Platform]] | **new** | GPU serving, KV cache, prefix-aware routing, disaggregation, multi-LoRA, capacity planning |
| 08 | [[08 - ChatGPT-Scale Consumer Chat]] | **new** | GPU capacity and quotas, streaming at scale, context assembly, model routing, degradation, multi-region |
| 09 | [[09 - Enterprise Search over SaaS Connectors]] | **new** | Connector framework, identity and permission graph, indexed vs federated search, workplace ranking, delegated actions |
| 10 | [[10 - Batch Document Extraction]] | **new** | Schema-constrained extraction with evidence, deterministic validation, confidence routing, human review, batch economics |
| 11 | [[11 - LLM Content Moderation Pipeline]] | **new** | Cost-aware cascade, policy-as-prompt, fail-open vs fail-closed, adversarial evasion, precision/recall per category |
| 12 | [[12 - Multi-Tenant Agent Platform]] | **new** | Control vs data plane, tenant isolation of data/secrets/compute, tool connectivity, durable runtime, evals as a feature |
| 13 | [[13 - Prompt Playground]] | **new** | Authoring vs execution planes, freeze-before-run snapshots, CAS saves and two-tab conflicts, large prompts and tiny edits, revocable shares through caches, idempotent and cancellable runs |
| 14 | [[14 - Model Weight Distribution to a GPU Fleet]] | **new** | The distribution lower bound, P2P swarm, data plane vs control plane, generation-numbered assignments, safe activation, quality-gated rollouts |
| 15 | [[15 - GPU Job Scheduler for Video Generation]] | **new** | Async jobs with conditional state transitions, fair queueing by GPU time, leases and fencing, checkpoints, bursts far beyond capacity, cancellation, gang scheduling for multi-GPU jobs |
| 16 | [[16 - AI Gateway]] | **new** | Unified API and adapters, reserve-then-settle token quotas, health-aware routing and fallback, retries only before the first token, privacy-preserving logging, residency, caching and routing for cost |

- **To add later** (named in 2026 AI system design question lists, not yet written): fine-tuning platform (light evidence), LLM evaluation platform, guardrails service, multimodal assistant. The prompt-management platform is covered by 13 and the multi-model gateway by 16.

---

## How You Walk Any of Them

1. **Clarify**: channels, users, which actions the AI may take alone, scale, latency, what "success" means.
2. **Requirements** with numbers, and what's out of scope.
3. **Only the estimates that pick something**: tokens per turn, cost per task, latency budget per stage.
4. **Entities, tools and state**: the tool contracts, what's stored where.
5. **V0**: the simplest design that works, and what it can't handle.
6. **Climb the ladder**: each step names the number or event that breaks the previous version and the topic or mini design it uses.
7. **Expect twists**: injection, timeouts, evals, rollout, cost, outages, compliance, abuse.
8. **Break it**: what fails, what the user sees, how you'd know.

- Timebox 45 minutes: 5 clarify and requirements, 5 estimates, 10 V0, 15 ladder and twists, 10 break it and evals.
- Say the numbers out loud: tokens per turn, cost per conversation, latency per stage, VRAM per replica.

---

## Note Template (new format)

Follows the HLD problem template in `CONVENTIONS.md` (*HLD Problems and Mini Designs*), with AI-specific sections. Research rules are the same: 10–20 dated reports or guide entries, tallied into evidence and confidence (`strong` / `medium` / `judgement`), every number sourced or marked *assumed* ([[HLD/Problems/README]] → *Researching and Prioritising a Problem*).

```
---
tags: [agentic/problems, status/draft]
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# <Problem>

> [!abstract] Where it's asked (companies, dates). Topics: 01 (role) → 03 (role) → ... The hard part, in one sentence.

## 1. Clarify Script
| Question | Default (assumed unless sourced) | If different |

## 2. Requirements
Functional, non-functional (quantified, including quality targets), out of scope.

## 3. Estimates That Drive V0
Tokens per turn, cost per task, latency budget; only numbers that pick something.

## 4. Entities, Tools and State
Tool contracts (what the model can call, what code enforces), state and stores.

## 5. V0 — Simplest Design That Works
Mermaid diagram, core flows, the most drilled component callout, what V0 can't handle.

## 6. Scale Ladder
### V1 — <name>
Evidence + confidence → trigger (number or event) → what breaks → the change (topic / MD links) → trade-off → Mermaid diagram of the whole system after this step.
(V2, V3, V4...)

## 7. Twist Cards
### Most asked
**Tn — "what the interviewer says"** · Evidence · Hits · Answer · Attaches to
### Lower priority (recall / judgement)

## 8. Break It
| Failure | What the user sees | What you say |

## 9. Evals, Operations and Cost
Eval tiers for this problem, production metrics, alerts, cost drivers.

## 10. One-Minute Close

## Evidence
| Follow-up | Reports | Count | Placed as | Deep dive? |

## Sources
## Related Notes
```

## Related Notes

- [[Agentic/README]] · [[Agentic Problem to Solution Map]] · [[HLD/Problems/README]]
