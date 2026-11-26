---
tags: [agentic/drills, status/draft]
created: 2026-11-26
---
# AI Engineer Round Drills

> [!abstract] What this is
> Practice for the parts of an AI engineer loop that **aren't** system design: the **coding round** (LLM-flavoured tasks, usually in Python), the **debugging round** (a broken pipeline or an agent stuck in a loop), the **product / scoping round** (turn a vague complaint into measurable work) and the **take-home**. Each drill has the prompt, what's graded, a reference solution or method, and the follow-ups. Design questions live in [[Agentic/Problems/README|Practice Designs]]; rounds where you *use* an AI assistant are in [[AI Assisted Coding Rounds/README|AI Assisted Coding Rounds]].

---

## Why It Exists

- Reported AI engineer loops in 2026 run roughly: phone screen on retrieval and prompting → **take-home** (build a small LLM system and measure it) → **live coding or debugging** → system design → **product or customer round**, plus behavioural. Only system design is covered by the topic notes.
- Interview reports confirm the non-design rounds: an EPAM senior AI engineer loop included an **async programming problem** next to agent and HITL discussion and RAG evaluation metrics; prep guides describe coding tasks such as a chunker, top-k cosine similarity, a resilient model-API wrapper, JSON validation and repair, and **debugging an agent loop that never ends**.
- How to use this note: do each coding drill once **without** help in 25–30 minutes, with tests, then compare. Do the debugging and product drills **out loud**, two minutes per answer.

---

## Coding Round

### Drill 1: Chunker with overlap that respects paragraphs

- **Prompt:** "Split a document into chunks of at most `max_tokens`, with about `overlap` tokens of overlap, never cutting a paragraph unless one paragraph alone is too long. Write tests."
- **Graded on:** boundary handling, the oversized-paragraph case, no empty chunks, overlap correctness, tests written unprompted.

```python
def chunk(text: str, max_tokens: int, overlap: int, count=lambda s: len(s.split())) -> list[str]:
    assert 0 <= overlap < max_tokens
    paras = [p.strip() for p in text.split("\n\n") if p.strip()]
    units: list[str] = []
    for p in paras:                                   # split only paragraphs that can't fit alone
        if count(p) <= max_tokens:
            units.append(p)
        else:
            words = p.split()
            step = max_tokens - overlap
            units += [" ".join(words[i:i + max_tokens]) for i in range(0, len(words), step)]
    chunks, cur = [], []
    for u in units:
        if cur and count("\n\n".join(cur + [u])) > max_tokens:
            chunks.append("\n\n".join(cur))
            tail, n = [], 0                           # carry trailing units as overlap
            for prev in reversed(cur):
                if n + count(prev) > overlap:
                    break
                tail.insert(0, prev); n += count(prev)
            while tail and count("\n\n".join(tail + [u])) > max_tokens:
                tail.pop(0)                           # overlap must never push a chunk over the limit
            cur = tail
        cur.append(u)
    if cur:
        chunks.append("\n\n".join(cur))
    return chunks

def test_chunker():
    assert chunk("", 50, 10) == []
    assert chunk("a b c", 50, 10) == ["a b c"]
    long_para = " ".join(["w"] * 120)
    assert all(len(c.split()) <= 50 for c in chunk(long_para, 50, 10))
    assert all(len(c.split()) <= 50 for c in chunk("x " * 30 + "\n\n" + "y " * 30, 50, 10))
```

- **Follow-ups:** count real tokens with the model's tokenizer, not words; keep tables and code blocks whole; attach a heading path as metadata ("Refund policy > Exceptions") ([[06 - RAG Ingestion and Indexing Pipeline]]).

### Drill 2: Top-k cosine similarity, then make it fast

- **Prompt:** "Given a query embedding and N document embeddings, return the top k by cosine similarity. Now N is a million."
- **Graded on:** normalising once, vectorising, using a partial sort, and knowing when to stop doing it exactly.

```python
import numpy as np

def build_index(docs: np.ndarray) -> np.ndarray:
    return docs / np.linalg.norm(docs, axis=1, keepdims=True)     # normalise once, at write time

def top_k(index: np.ndarray, q: np.ndarray, k: int) -> list[tuple[int, float]]:
    q = q / np.linalg.norm(q)
    scores = index @ q                                             # cosine = dot product of unit vectors
    k = min(k, len(scores))
    idx = np.argpartition(-scores, k - 1)[:k]                      # O(N), not a full O(N log N) sort
    idx = idx[np.argsort(-scores[idx])]                            # sort only the k winners
    return [(int(i), float(scores[i])) for i in idx]
```

- **Sizes to say:** 1M vectors × 768 dims × 4 bytes (float32) ≈ 3 GB; one matrix–vector product is ~768M multiply-adds (~1.5 GFLOP), and it's bound by reading 3 GB of memory, so at tens of GB/s of memory bandwidth it takes on the order of 100 ms per query (*rough estimate*). Fine for a batch job.
- **Follow-ups:** batch many queries as one matrix multiply; float16 or int8 to cut memory; past a few million vectors or tight latency, an ANN index (HNSW, IVF-PQ) trading a little recall for speed; filters by tenant **before** ranking ([[08 - Vector DB Internals and Hybrid Search]]).

### Drill 3: A resilient wrapper around a flaky model API

- **Prompt:** "Wrap an LLM API call with timeouts, retries and a token budget."
- **Graded on:** retrying only retryable errors, exponential backoff with **jitter**, honouring `Retry-After`, a total deadline, and stopping on budget.

```python
import random, time

RETRYABLE = {408, 429, 500, 502, 503, 504}

class BudgetExceeded(Exception): ...

def call_llm(send, messages, *, max_attempts=4, base=0.5, cap=8.0, deadline_s=30.0,
             budget: dict, max_out=512):
    est = count_tokens(messages) + max_out
    if budget["remaining"] < est:
        raise BudgetExceeded(f"need {est}, have {budget['remaining']}")
    start = time.monotonic()
    for attempt in range(max_attempts):
        timeout = min(10.0, deadline_s - (time.monotonic() - start))
        if timeout <= 0:
            break
        try:
            resp = send(messages, max_tokens=max_out, timeout=timeout)
            budget["remaining"] -= resp.usage.total_tokens         # charge actual usage
            return resp
        except ApiError as e:
            if e.status not in RETRYABLE or attempt == max_attempts - 1:
                raise                                              # 400/401/403: retrying won't help
            delay = e.retry_after or random.uniform(0, min(cap, base * 2 ** attempt))  # full jitter
            time.sleep(delay)
        except TimeoutError:
            if attempt == max_attempts - 1:
                raise
    raise TimeoutError("deadline exceeded")
```

- **Follow-ups:** fall back to a second provider after N failures; a circuit breaker so a dead provider isn't hammered; why retries of a side-effecting tool need an idempotency key ([[10 - AI Gateway Security and Guardrails]], [[08 - Reliability]]).

### Drill 4: Validate a model's JSON and repair it

- **Prompt:** "The model must return JSON matching a schema. Parse it, validate it, and recover when it doesn't."
- **Graded on:** a schema as code, feeding the exact validation error back, a bounded number of repairs, and a safe final failure.

```python
from pydantic import BaseModel, ValidationError
import json, re

class Ticket(BaseModel):
    category: str
    priority: int
    summary: str

def extract_json(text: str) -> str:
    m = re.search(r"\{.*\}", text, re.S)                         # tolerate prose or code fences
    if not m:
        raise ValueError("no JSON object found")
    return m.group(0)

def parse_with_repair(llm, prompt: str, max_repairs: int = 2) -> Ticket:
    messages = [{"role": "user", "content": prompt}]
    for _ in range(max_repairs + 1):
        out = llm(messages)
        try:
            return Ticket.model_validate_json(extract_json(out))
        except (ValueError, ValidationError) as e:
            messages += [{"role": "assistant", "content": out},
                         {"role": "user", "content": f"Invalid: {e}. Return only corrected JSON."}]
    raise ValueError("model output failed validation after repairs")
```

- **Follow-ups:** with **strict structured outputs** the shape is guaranteed by constrained decoding, so repair loops are only for **semantic** checks (a valid priority that's still wrong) ([[14 - Quantization Decoding and VRAM Math]]); log every repair as a quality signal.

### Drill 5: An agent loop that always terminates

- **Prompt:** "Here's a tool-calling loop that sometimes never ends. Fix it."
- **Graded on:** a step limit, a token or cost budget, detecting repeated identical calls, and a useful final answer when stopped.

```python
def run_agent(llm, tools, task, max_steps=12, max_tokens=50_000):
    messages, seen, used = [{"role": "user", "content": task}], {}, 0
    for step in range(max_steps):
        resp = llm(messages, tools=tools.schemas())
        used += resp.usage.total_tokens
        if not resp.tool_calls:
            return {"status": "done", "answer": resp.text, "steps": step + 1}
        messages.append(resp.message)
        for call in resp.tool_calls:
            key = (call.name, call.arguments)                    # same tool, same args
            seen[key] = seen.get(key, 0) + 1
            if seen[key] > 2:
                result = {"error": "repeated_call",
                          "hint": "You already tried this. Change approach or answer with what you have."}
            else:
                result = tools.execute(call)                     # errors come back as observations
            messages.append({"role": "tool", "tool_call_id": call.id, "content": json.dumps(result)})
        if used > max_tokens:
            break
    final = llm(messages + [{"role": "user", "content": "Stop. Summarise what you found and what's left."}])
    return {"status": "stopped", "answer": final.text, "steps": max_steps}
```

- **Root causes to name** when debugging the original: the tool error was raised instead of returned, so the model never learned why it failed; a tool returned an empty result the model kept re-requesting; the stop condition depended on the model saying "DONE" ([[02 - Execution Loops and Control Topologies]]).

### Drill 6: Async fan-out with a concurrency limit

- **Prompt:** "Call the model for 500 documents in parallel without exceeding 20 in flight, with a per-call timeout, and don't let one failure cancel the rest."
- **Graded on:** `asyncio.Semaphore`, `wait_for` timeouts, collecting failures instead of crashing, and preserving order.

```python
import asyncio

async def map_bounded(items, fn, limit=20, timeout_s=30.0):
    sem = asyncio.Semaphore(limit)
    async def one(i, item):
        async with sem:
            try:
                return i, await asyncio.wait_for(fn(item), timeout_s), None
            except Exception as e:                               # one failure doesn't stop the batch
                return i, None, repr(e)
    results = await asyncio.gather(*(one(i, x) for i, x in enumerate(items)))
    ok = [r for _, r, err in sorted(results) if err is None]
    failed = [(i, err) for i, _, err in sorted(results) if err is not None]
    return ok, failed
```

- **Follow-ups:** limit by **tokens per minute**, not just in-flight count ([[10 - AI Gateway Security and Guardrails]]); retry the failed list once with backoff; for thousands of offline documents, the provider's **batch API** is cheaper ([[12 - LLM Cost and Latency Economics]]).

### Drill 7: Merge keyword and vector results

- **Prompt:** "You have a BM25 ranking and a vector ranking. Merge them."
- **Answer:** Reciprocal Rank Fusion, `score(d) = Σ 1 / (k + rank_i(d))` with k ≈ 60, then rerank the top candidates. Code and trade-offs in [[08 - Vector DB Internals and Hybrid Search]].

---

## Debugging Round

Answer each out loud: say what you'd **measure first**, then the likely causes in order of likelihood, then the fix.

### "Retrieval looks fine, but answers are still wrong."

1. **Check that "fine" is true for these cases:** is the right chunk actually in what reached the prompt, after reranking and truncation? Log the final prompt for failing cases.
2. **Context ordering and size:** the right chunk is there but buried among 20 others (lost in the middle); reduce k after reranking.
3. **Generation faithfulness:** the model answers from its own knowledge instead of the context; measure faithfulness per claim; tighten the instruction to answer only from sources and to abstain.
4. **Chunk content:** the chunk has the fact but missing context (a table row without its header, a "this policy" without the policy name); fix chunking or add heading context.
5. **Stale index:** the source document changed after indexing ([[06 - RAG Ingestion and Indexing Pipeline]]).
- Separate the two failure types with two metrics: retrieval (recall@k) and generation (faithfulness), measured on the same failing set ([[11 - Evals Observability and Silent Drift]]).

### "The agent keeps calling the same tool."

- Read the trace for one stuck run: the arguments, the tool result, and the model's next message.
- Usual causes: the tool **raises** instead of returning an error the model can read; it returns an empty or ambiguous result; the description doesn't say when *not* to call it; the goal is impossible with these tools; or there is no stop condition.
- Fixes: errors as observations with a hint, a repeated-call guard, a step budget, and a final "summarise and stop" turn (Drill 5).

### "Latency doubled after yesterday's release."

- Break the trace into stages: queueing, time to first token, output tokens, tool time, number of steps ([[12 - LLM Cost and Latency Economics]]).
- Common culprits: more **steps** per task (a prompt change made the agent less decisive); a longer prompt that broke **prompt caching** (something dynamic moved into the prefix); longer outputs; a slower fallback model kicking in; a tool that got slower.

### "Cost per task tripled."

- Plot tokens per task by component: input vs output vs reasoning tokens, and steps per task.
- Usual causes: context growing each step without compaction; cache misses after a prefix change; retries or hedging during an incident; a router sending more traffic to the expensive model.

### "Eval scores dropped after the model upgrade."

- Look at the **per-case diff**, not the average: which cases flipped, and do they share a cause (format, tool-calling style, refusals, verbosity)?
- Check the **judge** didn't change with it (self-preference, a different rubric interpretation).
- Pin the old model for the affected route, fix prompts for the new one, and add the flipped cases to the regression set ([[11 - Evals Observability and Silent Drift]]).

---

## Product and Scoping Round

### "Users say the bot gives wrong answers."

A method to say out loud, in this order:

1. **Get examples.** Sample 50–100 recent conversations flagged as bad (thumbs-down, escalations, repeated questions) plus a random sample.
2. **Label the failure types** by hand: retrieval missed it, wrong source, hallucinated beyond sources, outdated content, misunderstood the question, out of scope, correct but unhelpful. Count them.
3. **Pick the biggest bucket** and define one metric for it (e.g. "answer supported by a cited source" rate).
4. **Baseline and target**: current rate on a fixed eval set built from these cases, and a target the team agrees on.
5. **One change at a time**, measured on that set, then shipped behind a flag and watched in production.
6. **Say what you won't do yet**: switching models or fine-tuning before knowing which bucket dominates.

### "A stakeholder wants a chatbot."

- Ask what decision or task the user is trying to complete, how they do it today, and what "better" means in numbers (time saved, deflected tickets, conversion).
- Often the answer is **not** a chatbot: better search, a form, an FAQ page, or a workflow with one LLM step ([[01 - Workflows vs Agents]]). Saying so, with the reason, is a strong answer.
- If it is a chatbot: the smallest version, the eval set, the escalation path to a human, and the cost per conversation.

### "Should we ship this prompt change? One metric went up, another went down."

- Look at which **cases** moved and how much each matters: a safety or correctness regression outweighs a helpfulness gain.
- Check the size of the change against run-to-run noise (run several samples; report pass rates with variance).
- If it's a trade-off, ship behind a flag to a slice of traffic and watch the online metrics.

---

## Take-Home

- **Typical prompt:** "Build a question-answering service over these documents, with an evaluation set, and report metrics."
- **What strong submissions include:**
    + a working service with a small, clear structure, tests, and a one-command setup;
    + an **eval set** of 30–50 questions with expected answers or supporting chunks, including unanswerable ones;
    + **retrieval and answer metrics reported separately** (recall@k, faithfulness, abstention accuracy);
    + **one or two experiments** with numbers (chunk size A vs B, with and without a reranker) and what you concluded;
    + a short write-up: design choices, what fails and why, what you'd do with more time, cost per query.
- Disclose any AI tool use if the instructions allow it; be ready to defend every line live ([[AI Assisted Coding Rounds/README|AI Assisted Coding Rounds]]).

---

## Sources

- techinterview.coach, *AI Engineer Interview: Rounds, Questions and How to Prepare* (November 2026): the loop shape, coding-task examples, the thirty practice questions. https://techinterview.coach/blog/ai-engineer-interview
- LeetCode Discuss, *EPAM Systems – Senior AI Engineer Interview Experience*: agentic HITL scenario, an async programming problem, retrieval and generation metrics. https://leetcode.com/discuss/post/7876633/epam-systems-senior-ai-engineer-intervie-4l0h/
- PracHub, *Trace and Debug Agent Tool-Call Failures* (practice question). https://prachub.com/interview-questions/trace-and-debug-agent-tool-call-failures

## Related Notes

- [[Agentic/README]] · [[Agentic/Problems/README]] · [[Agentic Problem to Solution Map]] · [[AI Assisted Coding Rounds/README]]
