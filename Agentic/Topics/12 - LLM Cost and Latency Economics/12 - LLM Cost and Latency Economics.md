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

### Unit economics and pricing

- The question in a product or FDE round: "does this feature make money, and what happens when usage doubles?" Answer with cost per unit of value, the pricing model, and the margin.

| Pricing model | Revenue moves with | Who carries the cost risk | Breaks when |
|---|---|---|---|
| **Per seat** (flat monthly) | Number of users | The vendor | Power users run far more tasks than the average the price assumed |
| **Per token or per call** (pass-through plus markup) | Usage | The customer | Customers can't predict the bill; agents with long contexts look expensive |
| **Per outcome** (per resolution, per saved cancellation; Sierra prices this way, DataInterview, Oct 2026) | Value delivered | The vendor, on failed attempts | The outcome definition is disputed, or many runs fail before one succeeds |
| **Hybrid** (seat plus usage tiers or credits) | Both | Shared | Credit rules get complicated |

- Worked example (all inputs *assumed*, prices from the cost model above): a support agent, 12 steps per conversation.
    + LLM cost per conversation ≈ **$0.38** with a 90% cached prefix (`task_cost(..., cache_hit=0.9)`), $0.50 without.
    + Add retrieval, tools, logging and hosting at +30%: ≈ **$0.50** per conversation.
    + Only 60% of conversations end resolved, so cost per resolution ≈ 0.50 / 0.6 ≈ **$0.83**.
    + At a price of $1.50 per resolution, gross margin ≈ (1.50 − 0.83) / 1.50 ≈ **45%**. Every point of resolution rate and every cache hit moves the margin; that is why outcome pricing makes evals a finance concern.
- Usage doubles:
    + Per-token pricing: revenue and cost both double; margin holds.
    + Per-outcome pricing: margin holds only if resolution rate holds on the new traffic, which is usually harder.
    + Per-seat pricing: revenue is flat and cost doubles.
- Power users on flat pricing: a $50 seat whose user runs 400 tasks a month at $0.50 costs $200. Fixes: fair-use caps, a cheaper model past a quota, credits, or a usage tier. Look at the distribution of cost per user, not the mean, because a few heavy users usually carry most of the cost.
- Levers in order of effort: caching and compaction, routing routine steps to a cheaper model ([[#Cascades]]), fewer steps through better tools, then self-hosting at high steady volume ([[#API vs Self-Hosting]]).

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

### Choosing a model and reading benchmarks

"Choose fast or cheap models" is a Google screen question (PracHub, Jan 2026); the method matters more than the answer.

1. **Write the task's bar first:** quality metric and threshold on your own eval set, latency targets (TTFT and total), cost per task, context length, modalities, data rules (residency, retention), and whether you need fine-tuning or self-hosting.
2. **Shortlist from public signals, then ignore them:** leaderboards and benchmarks narrow the field, but they are contaminated (test items leak into training data), saturated at the top, and measure tasks that aren't yours; vendor-reported numbers are run by the vendor. Arena-style human-preference rankings reward style as well as substance.
3. **Run your eval set on 3–5 candidates**, several samples each with error bars ([[11 - Evals Observability and Silent Drift#Eval Statistics: Error Bars on Every Score]]), and record quality, latency and cost per task together.
4. **Pick per step, not per product:** the cheapest model that clears the bar for each step, a stronger one for the hard steps, and a fallback in the same tier ([[#Cascades]]).
5. **Re-run on every new model release**; model choice is a recurring decision, and a newer small model often replaces an older large one.

- Build vs buy (Huyen, *AI Engineering*, ch. 4): API models for frontier quality and fast iteration; open weights when you need control, fine-tuning, residency or steady high volume; the table above and the next section cover the economics.

### On-prem, air-gapped and sovereign deployment

Banks, government, defence and healthcare often can't send data to a public API. The question is "what changes when the model has to run inside our walls?"

| Option | What it means | Pick when |
|---|---|---|
| **Provider API with residency controls** | Regional endpoints, zero data retention, no training on your data | Data may leave your network but must stay in a region |
| **Provider models in your cloud account** | Frontier models through your cloud (Bedrock, Vertex AI, Azure) over private endpoints, in your VPC and region | You need a frontier model and your cloud is already approved |
| **Self-hosted open weights in your cloud** | vLLM or SGLang on your GPUs ([[13 - GPU Serving and Disaggregated KV Cache]]) | Strict control, fine-tuning, or steady volume |
| **On-prem, connected** | Your data centre; vendor-managed stacks exist (Google Distributed Cloud connected with Gemini, preview 2025) | Data can't leave the building, but the site can reach the vendor |
| **Air-gapped** | No connection to the vendor or the internet; Gemini on GDC air-gapped is generally available (Google, Aug 2025), as are open-weight models | Classified or regulated environments |

- **What changes when air-gapped:**
    + **Model updates arrive as signed artifacts on approved media** and go through your own eval suite before promotion; nothing auto-updates ([[14 - Model Weight Distribution to a GPU Fleet]]).
    + **Everything around the model must be local too:** embedding model, reranker, guardrail classifiers, vector store, package mirrors, tracing and eval tooling. A design that calls a hosted moderation API or a SaaS tracer fails here.
    + **Capacity is fixed:** you can't burst to a provider, so admission control and queues matter more, and quantization buys capacity ([[14 - Quantization Decoding and VRAM Math]]).
    + **Model choice narrows** to what the vendor licenses for on-prem or open weights; evaluate quality gaps on your tasks before promising parity with the API.
- **India:** sector rules (RBI's payment-data localisation) and government buyers push toward in-country hosting ([[10 - AI Gateway Security and Guardrails#Regulation to name in a design round]]). The IndiaAI Mission's subsidised GPU capacity (38,000+ GPUs onboarded through the AI compute portal, Lok Sabha answers, 2026) and Indian model builders such as Sarvam are the "sovereign" options interviewers in India may expect you to name.

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

## Sources

- DataInterview, *Sierra Forward Deployed Engineer interview guide* (28 Oct 2026): Sierra prices on outcomes (resolutions, saved cancellations, revenue, CSAT).
- Google Cloud, *Gemini is now available anywhere* (GA on Google Distributed Cloud air-gapped, Aug 2025) and *Run Gemini and AI on-prem with Google Distributed Cloud* (Apr 2025). https://cloud.google.com/blog/topics/hybrid-cloud/gemini-is-now-available-anywhere
- Lok Sabha unstarred question 6044 (answered 1 April 2026): IndiaAI national compute capacity on subsidised cloud GPUs. https://sansad.in/getFile/lsapps/loksabhaquestions/annex/187/AU6044_lZOsLn.pdf

## Related Notes

- [[00 - LLM Fundamentals for Engineers]] · [[04 - State Memory and Context Engineering]] · [[10 - AI Gateway Security and Guardrails]] · [[13 - GPU Serving and Disaggregated KV Cache]]
- [[Agentic/README]]
