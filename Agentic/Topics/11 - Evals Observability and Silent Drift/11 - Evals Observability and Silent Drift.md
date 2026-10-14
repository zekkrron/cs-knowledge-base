---
tags: [agentic/topics, status/draft]
created: 2026-10-11
---
# Evals, Observability and Silent Drift

> [!abstract] What this is
> Knowing whether an AI system works and noticing when it stops: **tracing** every model, tool and retrieval step with tokens and cost (OpenTelemetry), a **three-tier evaluation** strategy (deterministic checks, trajectory analysis, outcome verification), **LLM-as-a-judge** and its biases, building **eval datasets** and running them in CI, catching **silent drift** in production, the risks of **semantic caching**, and **migrating embedding models** without downtime. The decision this helps with: what to measure, how to trust the measurement, and what to alert on.

---

## Why It Exists

- AI systems fail **silently**. A broken service returns 500s; a broken agent returns 200 OK with a plausible, wrong answer, or quietly loops through retries that triple the cost.
- Outputs are non-deterministic and quality is subjective, so normal unit tests don't cover it. Without evals, every prompt change, model upgrade or index rebuild is a guess.

---

## Observability: Tracing AI Systems

- One **trace** per request or agent run; one **span** per step: each LLM call, tool call, retrieval, rerank, guardrail check and sub-agent. This is distributed tracing ([[11 - Services and Observability]]) with AI-specific attributes.

| Attribute | Why |
|---|---|
| Model name and version, provider | Attribute regressions to model changes |
| Input, output, cached and reasoning token counts | Cost and latency drivers |
| Computed cost | Per request, per feature, per tenant |
| Latency, time to first token | Where time goes |
| Prompt template id and version | Which prompt produced this |
| Tool name, arguments, result status | Debug trajectories |
| Retrieved chunk ids and scores | Debug RAG |
| Run / thread / tenant / user ids | Correlation and attribution |
| Finish reason, error types | Truncation, refusals, failures |

- OpenTelemetry has **GenAI semantic conventions** for these attributes, so traces can flow to general backends (Datadog, Grafana, Honeycomb) and LLM-specific tools (Langfuse, LangSmith, Arize Phoenix, and others).

```python
from opentelemetry import trace
tracer = trace.get_tracer("support-agent")

def traced_llm_call(messages, model, step: int):
    with tracer.start_as_current_span("chat " + model) as span:
        span.set_attribute("gen_ai.request.model", model)
        span.set_attribute("agent.step", step)
        resp = gateway.chat(model=model, messages=messages)
        span.set_attribute("gen_ai.usage.input_tokens", resp.usage.prompt_tokens)
        span.set_attribute("gen_ai.usage.output_tokens", resp.usage.completion_tokens)
        span.set_attribute("llm.cost_usd", price(model, resp.usage))
        span.set_attribute("gen_ai.response.finish_reasons", [resp.choices[0].finish_reason])
        return resp
```

- Prompts and outputs in traces are sensitive data: redact, restrict access and set retention ([[10 - AI Gateway Security and Guardrails]]).

### Production metrics to dashboard and alert on

| Metric | Catches |
|---|---|
| **Task success rate** (from outcome checks, user feedback, escalations) | Quality regressions |
| **Cost per task** and tokens per task | Retry loops, context bloat, routing mistakes |
| **Steps per task**, repeated-action rate | Looping agents ([[02 - Execution Loops and Control Topologies]]) |
| **Tool error rate** by tool | Broken upstream APIs |
| **Latency p50/p95/p99**, TTFT | Provider slowdowns, prompt growth |
| **Refusal / abstention rate** | Over-cautious changes, or retrieval failures |
| **Truncation rate** (finish reason = length) | Output caps too low |
| **Guardrail trigger rate** | Attacks, or over-blocking |
| **User signals**: thumbs, edits, retries, handoff to human | Real-world dissatisfaction |

---

## Three-Tier Evaluation

| Tier | Question | Examples | Cost | Run when |
|---|---|---|---|---|
| **1. Unit / deterministic** | Does each step behave correctly? | Output parses against the schema; router picked the right label; tool chosen and arguments correct; required fields present; regex/exact match; latency and token limits | Cheap, fast, reliable | Every commit in CI |
| **2. Trajectory** | Did the agent take a reasonable path? | Called the right tools in a sensible order; no forbidden actions; no repeated calls; within step budget; asked for approval when required | Medium; needs traces | CI on agent changes; sampled production |
| **3. Outcome** | Did the task actually succeed? | Final environment state is correct (refund recorded once, ticket closed, tests pass); answer correct against a reference; RAG answer faithful to sources | Expensive (sandbox environments, judges, humans) | Before releases; nightly; sampled production |

- Prefer **outcome checks against real state** over judging the text: "the refund row exists exactly once with amount 499" beats "the reply sounds like it refunded".
- For RAG, evaluate retrieval and generation separately:
    + **Retrieval**: recall@k, MRR, context precision ([[06 - RAG Ingestion and Indexing Pipeline]]).
    + **Generation**: **faithfulness** (claims supported by retrieved context), **answer relevance**, **correctness** vs a reference, and correct **abstention** when the answer isn't in the corpus. Libraries like RAGAS package these metrics.

```python
def eval_case(case) -> dict:
    run = agent.run(case.input, sandbox=fresh_env(case.fixtures))
    return {
        "schema_ok": run.final.validates(),                                  # tier 1
        "tools_ok": set(case.required_tools) <= set(run.tools_called()),     # tier 2
        "no_forbidden": not (set(run.tools_called()) & set(case.forbidden_tools)),
        "steps_ok": run.steps <= case.max_steps,
        "outcome_ok": case.check_state(run.env),                             # tier 3: real state
    }
```

---

## LLM-as-a-Judge

- Using a model to grade outputs scales evaluation to open-ended answers, but judges have systematic biases:

| Bias | What happens | Mitigation |
|---|---|---|
| **Position bias** | In pairwise comparison, prefers whichever answer appears first (or second) | Evaluate both orders; count only consistent wins |
| **Verbosity bias** | Prefers longer answers | Rubrics that reward concision; length-controlled comparisons |
| **Self-preference** | Rates outputs from its own model family higher | Judge with a different model family |
| **Leniency / score inflation** | Everything gets 4/5 | Binary pass/fail criteria instead of 1–10 scales |
| **Format sensitivity** | Markdown or confident tone scores higher | Rubric focused on specific checkable criteria |

- Making judges trustworthy:
    + **Binary, specific rubrics**: "Does the answer state the refund window as 7 days? yes/no" instead of "rate helpfulness 1–10". Break quality into several yes/no checks.
    + **Give the judge the reference** answer or the source context when available.
    + **Calibrate against humans**: label 100–200 cases yourself, measure judge–human agreement (accuracy, Cohen's kappa), and iterate on the rubric until agreement is high; re-check periodically.
    + Ask for a short **reason before the verdict**, and log both.

```python
JUDGE_RUBRIC = """You are grading a support answer against the policy excerpt.
Answer each with yes or no, then give one-sentence reasons.
1. Every factual claim is supported by the policy excerpt.
2. The refund window stated (if any) matches the policy.
3. The answer does not promise anything the policy doesn't allow.
Return JSON: {"supported": "...", "window_correct": "...", "no_overpromise": "...", "reasons": "..."}"""
```

---

## Eval Datasets and CI

- **Where cases come from:**
    + Real production traffic (sampled, anonymised), especially failures, escalations and thumbs-down.
    + Every **incident** becomes a regression case.
    + **Adversarial** cases: prompt injection, out-of-scope questions, unanswerable questions, edge-case inputs.
    + Coverage across intents, languages, tenants and difficulty.
- **Hygiene:**
    + Version the dataset with the code; record expected outcomes or rubrics per case.
    + Keep a **held-out** set you don't tune against, or you'll overfit prompts to the eval.
    + Run multiple samples per case for non-deterministic components and report pass rates with variance.
- **In CI:** run tier-1 checks on every change; run trajectory and outcome suites on prompt, model, tool or retrieval changes; **block merges** on regressions beyond a threshold; show per-case diffs, not just one score.
- **Online evaluation:** score a sample of production traffic continuously with the same judges and checks, so quality is a live metric.

---

## Silent Drift

| Source of drift | Symptom | Mitigation |
|---|---|---|
| **Provider updates a model** behind the same alias | Quality, format or refusal behaviour changes overnight | Pin dated model versions; re-run evals before switching |
| **Upstream API changes** | Tool calls start failing; agent retries quietly while returning 200 | Tool error-rate alerts; contract tests on tools; circuit breakers |
| **Data drift** | New product names, policies or user language the prompts and index don't cover | Monitor retrieval scores and abstentions; refresh eval sets from recent traffic |
| **Index staleness** | Answers cite outdated docs | Freshness monitoring on ingestion ([[06 - RAG Ingestion and Indexing Pipeline]]) |
| **Prompt edits** | A "small wording change" breaks a case | Prompts versioned and gated by evals |

- **Semantic circuit breakers**: trip on error rate, cost per task, steps per task or quality-score drops (not just HTTP errors), and switch to a fallback (simpler workflow, older prompt, human handoff) ([[08 - Reliability]]).

---

## Semantic Caching Risks

- A **semantic cache** returns a stored answer when a new query's embedding is close enough to a cached one. It can cut cost and latency for repeated FAQ-style questions, but similarity is not equivalence:
    + "Send ₹500 to Ravi" and "Send ₹500 to Rahul" have very high cosine similarity and completely different meaning.
    + "Is the store open today?" asked on different days needs different answers.
    + Personalised answers leak across users if the cache key ignores the user.

| Rule | Why |
|---|---|
| **Never semantically cache actions or transactional intents** | A near-match executes the wrong thing |
| **Exact-match caching** (normalised text + parameters) for deterministic calls | Safe and still useful |
| **Include tenant, user scope, locale and data version in the key** | No cross-user leakage; invalidation on data change |
| **High similarity thresholds plus entity checks** (same ids, names, numbers) | Catch "Ravi vs Rahul" |
| **TTLs and invalidation on source changes** | No stale answers |
| **Measure false-hit rate** on an eval set | Know the risk you're taking |

- Provider **prompt caching** (reusing computed prefixes) is a different thing and is always safe: it changes cost and latency, not answers ([[12 - LLM Cost and Latency Economics]]).

---

## Zero-Downtime Embedding Model Migration

- Vectors from different embedding models can't be mixed, so changing models is a full data migration ([[06 - RAG Ingestion and Indexing Pipeline]]). The expand–migrate–contract pattern from schema changes applies ([[12 - Schema Changes and Backups]]):
    1. **Create a new index** for the new model; keep the old one serving.
    2. **Dual-write**: ingestion embeds new and changed chunks with both models.
    3. **Backfill** the new index with the existing corpus (batched, throttled, resumable).
    4. **Shadow read**: run queries against both; compare retrieval metrics on the eval set and on sampled traffic.
    5. **Switch reads** (alias swap or config flag), with the old index kept warm for rollback.
    6. **Stop dual-writing** and delete the old index after a safety window.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **200 OK but wrong** | Silent quality failures, cost-inflating retry loops | Outcome checks, cost/steps-per-task alerts, semantic circuit breakers |
| **Untrusted judge** | Eval scores move but don't reflect quality | Binary rubrics; reference answers; human calibration; different model family |
| **Overfitting the eval** | Scores improve, production doesn't | Held-out set; fresh cases from production |
| **Model alias drift** | Behaviour changes without a deploy | Pin versions; eval before upgrading |
| **Semantic cache false hit** | Wrong recipient, stale or another user's answer | No semantic caching for actions; scoped keys; entity checks; thresholds |
| **Mixed-model index during migration** | Retrieval quality collapses | Separate indexes; dual-write, backfill, shadow, switch |
| **Unredacted traces** | Sensitive data exposure | Redaction and retention on telemetry |

---

## Interview Lines

> [!tip] Say it like this
> - "AI systems fail with 200 OK, so I alert on task success, cost per task and steps per task, not just error rates."
> - "Evals run in three tiers: deterministic checks on every commit, trajectory checks on agent changes, and outcome checks against real environment state."
> - "LLM judges get binary rubrics, both answer orders, a different model family, and calibration against human labels."
> - "Every incident becomes an eval case, and prompt or model changes are gated in CI like code."
> - "I never semantically cache transactional intents; 'send to Ravi' and 'send to Rahul' are nearly identical vectors."
> - "Embedding migrations are dual-write, backfill, shadow compare, then switch."

## Related Notes

- [[02 - Execution Loops and Control Topologies]] · [[06 - RAG Ingestion and Indexing Pipeline]] · [[10 - AI Gateway Security and Guardrails]] · [[11 - Services and Observability]]
- [[Agentic/README]]
