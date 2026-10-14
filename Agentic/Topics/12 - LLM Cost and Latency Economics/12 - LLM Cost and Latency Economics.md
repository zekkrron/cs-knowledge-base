---
tags: [agentic/topics, status/draft]
created: 2026-10-11
---
# LLM Cost and Latency Economics

> [!abstract] What this is
> Where the money and the milliseconds go in an LLM application, and every lever that changes them: the token cost model (input, cached, output, reasoning), cost-per-task arithmetic for agents, **prompt caching**, **model routing and cascades**, **batch APIs**, output and context discipline, latency anatomy and perceived latency, budgets and attribution, and when **self-hosting** beats API pricing. The decision this helps with: making a design affordable and fast enough, with numbers.

> [!info] Prices change constantly
> Figures here are **illustrative ratios**, not current price lists. What stays true: output costs several times more than input, cached input costs much less than fresh input, batch costs less than realtime, and small models cost an order of magnitude less than frontier ones.

---

## Why It Exists

- A prototype that costs $0.002 per request becomes $0.20 per task once it's an agent with 15 steps and a growing context, and $200,000 a month at a million tasks. Cost and latency are design constraints, not afterthoughts, and interviewers increasingly ask for the arithmetic.

---

## The Cost Model

```
cost per call = input_tokens × input_price
              + cached_input_tokens × cached_price        (often ~10–50% of input price, varies by provider)
              + output_tokens × output_price               (typically several × input price)
              + reasoning_tokens × output_price            (reasoning models; billed like output)
```

### Agent cost compounds

- Each step re-sends the system prompt, tool definitions and the whole history so far. With a fixed prefix `P`, per-step additions `a`, and `n` steps, total input tokens grow roughly **quadratically** in `n`:

```
total input ≈ n·P + a·(1 + 2 + ... + n) ≈ n·P + a·n²/2
```

- Worked example (illustrative): system prompt + tools = 4,000 tokens, each step adds 1,500 tokens (tool call + result), 12 steps, 300 output tokens per step.
    + Input ≈ 12 × 4,000 + 1,500 × 12² / 2 ≈ 48,000 + 108,000 = **~156k input tokens**.
    + Output ≈ 12 × 300 = **3.6k output tokens**.
    + Input dominates the bill here, which is why **context compaction** and **prompt caching** are the big levers for agents ([[04 - State Memory and Context Engineering]]).

```python
from dataclasses import dataclass

@dataclass
class Price:            # per 1M tokens, illustrative
    input: float
    cached: float
    output: float

def task_cost(steps: int, prefix: int, per_step: int, out_per_step: int,
              price: Price, cache_hit: float = 0.0) -> float:
    total_in = sum(prefix + per_step * i for i in range(steps))
    cached = prefix * steps * cache_hit                       # cached part of the stable prefix
    fresh = total_in - cached
    return (fresh * price.input + cached * price.cached + steps * out_per_step * price.output) / 1e6

frontier = Price(input=3.0, cached=0.3, output=15.0)
print(task_cost(12, 4000, 1500, 300, frontier))                  # no caching
print(task_cost(12, 4000, 1500, 300, frontier, cache_hit=0.9))   # stable cached prefix
```

---

## The Levers

| Lever | Saves | How | Watch out |
|---|---|---|---|
| **Prompt caching** | Input cost and TTFT | Keep a long, **stable prefix** (system prompt, tools, reference docs) at the top; append variable content at the end | Any change early in the prompt invalidates the cache; caches expire after minutes; minimum prefix lengths apply |
| **Model routing / cascades** | Most of the bill | Small model for easy or high-volume steps; large or reasoning model for hard ones ([[01 - Workflows vs Agents]]) | Router errors; evaluate both tiers |
| **Context compaction** | Input cost, latency, quality | Trim tool outputs, mask old observations, summarise ([[04 - State Memory and Context Engineering]]) | Compaction changes the prefix, so do it in chunks |
| **Shorter outputs** | Output cost and latency | Ask for concise answers and structured fields; cap `max_tokens` | Truncation if the cap is too low |
| **Batch APIs** | ~50% on asynchronous work (typical) | Submit offline jobs (evals, backfills, enrichment, contextual chunk headers) to a batch endpoint with a completion window of hours | Not for interactive requests |
| **Fewer steps** | Everything | Workflows instead of agents; intent-level tools ([[03 - Tool Engineering and Action Spaces]]) | Less flexibility |
| **Response caching** | Repeated calls | Exact-match caching for deterministic calls; semantic caching only for safe FAQ-style reads ([[11 - Evals Observability and Silent Drift]]) | Stale or wrong answers |
| **Smaller / fine-tuned models** | Per-token cost | Distil a large model's behaviour into a small one for a narrow task ([[15 - Post-Training and Model Adaptation]]) | Training and maintenance cost |
| **Reasoning-effort control** | Reasoning tokens | Lower effort/budget for routine steps | Quality on hard steps |

### Prompt caching in practice

- Providers cache the **computed prefix** (the KV cache, [[13 - GPU Serving and Disaggregated KV Cache]]) of recent prompts. A later request that starts with the **exact same tokens** reuses it: cheaper input and lower time to first token. Some providers cache automatically above a minimum length; others need explicit cache markers.

```python
messages = [
    {"role": "system", "content": SYSTEM_PROMPT},           # stable: cached
    {"role": "user", "content": REFERENCE_DOCS},             # stable per tenant: cached
    *conversation_so_far,                                    # grows: partly cached across turns
    {"role": "user", "content": new_message},                # variable: always last
]
# Anti-pattern: f"Today is {datetime.now().isoformat()}\n" + SYSTEM_PROMPT
# A timestamp at the top changes the first tokens every request and kills every cache hit.
```

- Rules: put **dates, user names, request ids** after the stable prefix (or round dates to the day); keep **tool definitions in a fixed order**; avoid reordering or editing earlier messages.

### Cascades

```python
def answer(query: str) -> str:
    draft = small_model(query, return_confidence=True)
    if draft.confidence >= 0.8 and passes_checks(draft):      # cheap path for most traffic
        return draft.text
    return large_model(query)                                  # escalate only when needed
```

- A cascade pays the small-model cost on every request and the large-model cost only on escalations, so it wins when most traffic is easy and escalation is accurate. Confidence can come from logprobs, a verifier, schema validity or a classifier.

---

## Latency Economics

- Per call: `latency ≈ queueing + TTFT + output_tokens × per-token time` ([[00 - LLM Fundamentals for Engineers]]).
- Per agent run: **sum of sequential steps** plus tool latencies. Ten steps at 1.5 s each is 15 s before tools.

| Lever | Effect |
|---|---|
| **Fewer sequential steps** | Biggest effect for agents; parallelise independent calls ([[05 - Multi-Agent Topologies and Concurrency]]) |
| **Prompt caching** | Lower TTFT on long prompts |
| **Smaller / faster models** for intermediate steps | Lower per-token time |
| **Shorter outputs** | Directly proportional reduction |
| **Streaming + progress events** | Perceived latency ≈ first visible token ([[09 - Durable Execution HITL and Realtime UX]]) |
| **Speculative work** | Start likely-needed retrieval or tool calls before the model asks; discard if unused |
| **Regional endpoints / priority tiers** | Lower network latency and queueing, at a price |
| **Hedged requests** | Cut tail latency on idempotent calls ([[10 - AI Gateway Security and Guardrails]]) |

- Report **p95/p99**, not averages: provider latency is long-tailed, and agents multiply tails across steps ([[02 - Fundamentals]]).

---

## Budgets and Attribution

- **Attribute every token** to a tenant, user, feature and run id through tracing ([[11 - Evals Observability and Silent Drift]]); otherwise you can't price plans or find the expensive feature.
- **Enforce budgets in the runtime and gateway**, not in prompts:
    + Per run: max tokens, max steps, max dollars ([[02 - Execution Loops and Control Topologies]]).
    + Per tenant / plan: monthly token quotas, with soft warnings and hard stops ([[10 - AI Gateway Security and Guardrails]]).
    + Per feature: cost per task targets tracked on dashboards.
- **Unit economics**: cost per resolved ticket, per generated report, per active user per month, compared with what the feature earns or saves.

---

## API vs Self-Hosting

- Self-hosting open-weight models on your own GPUs trades per-token pricing for **fixed hourly GPU cost** plus engineering and operations.

```
self-hosted cost per 1M output tokens ≈ GPU $/hour ÷ (tokens/second × 3600 ÷ 1e6) ÷ utilisation
```

- The key variable is **sustained utilisation**: GPUs bill by the hour whether busy or idle. High, steady volume with batching can beat API prices substantially; spiky or low volume almost never does.

| Choose | When |
|---|---|
| **API** | Variable or modest volume; need frontier quality; small team; fast iteration |
| **Self-hosted** | High, steady volume on a model you can run well; strict data residency or privacy; need custom or fine-tuned models; latency control |
| **Hybrid** | Frontier API for hard steps; self-hosted small or fine-tuned models for high-volume routine steps |

- Throughput per GPU depends on batching, KV-cache memory, quantization and model size, which is the subject of [[13 - GPU Serving and Disaggregated KV Cache]] and [[14 - Quantization Decoding and VRAM Math]].

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Quadratic agent context** | Cost per task explodes with steps | Compaction, observation masking, fewer steps |
| **Cache-busting prompts** | Timestamps or reordered tools at the top kill cache hits | Stable prefix first; variable content last |
| **One model for everything** | Paying frontier prices for trivial steps | Routing and cascades, evaluated per tier |
| **Uncapped outputs and reasoning** | Long responses and reasoning tokens dominate latency and cost | `max_tokens`, concise formats, effort settings |
| **No attribution** | Can't find the expensive feature or tenant | Token and cost attributes on every span |
| **Budgets in prompts** | Model ignores "be brief" or "stop after 5 steps" | Runtime and gateway enforcement |
| **Idle self-hosted GPUs** | Self-hosting costs more than the API | Size by sustained load; hybrid; autoscale; batch offline work |

---

## Interview Lines

> [!tip] Say it like this
> - "Agent input tokens grow roughly quadratically with steps because each step re-sends the history, so compaction and prompt caching are my biggest cost levers."
> - "I keep a stable prefix of system prompt, tools and reference docs at the top so provider caching hits, and put timestamps and user data at the end."
> - "Easy, high-volume steps go to a small model and only hard ones escalate; offline work goes to the batch API."
> - "Latency is the sum of sequential steps, so I parallelise independent calls, shorten outputs and stream progress."
> - "Self-hosting only wins at high, sustained utilisation; GPUs cost the same per hour whether they're busy or not."

## Related Notes

- [[00 - LLM Fundamentals for Engineers]] · [[04 - State Memory and Context Engineering]] · [[10 - AI Gateway Security and Guardrails]] · [[13 - GPU Serving and Disaggregated KV Cache]]
- [[Agentic/README]]
