---
tags: [agentic/topics, status/draft]
created: 2026-10-11
---
# Workflows vs Agents

> [!abstract] What this is
> The first design decision in any AI system: does this need an **agent** that decides its own steps, or a **workflow** where code fixes the steps and the LLM fills them in? Covers the definitions, the five standard workflow patterns, when autonomy is worth its cost, and the complexity ladder to climb only as far as needed.

---

## Why It Exists

- "Build an agent" is the default answer in interviews and in demos, and often the wrong one. Autonomy costs **predictability, latency, money and debuggability**, and errors compound across steps.
- Senior candidates are graded on whether they reach for the **simplest structure that works** and can say exactly what would justify more autonomy.

---

## Definitions

- **Augmented LLM**: one model call enhanced with retrieval, tools and memory. The building block of everything below.
- **Workflow**: LLM calls and tools orchestrated through **predefined code paths**. You decide the steps; the model does each step.
- **Agent**: the LLM **dynamically directs its own process**: it decides which tool to call next, when to loop, and when it's done, based on what it observes.

| | Workflow | Agent |
|---|---|---|
| Control flow | Code | Model |
| Predictability | High; same path every time | Variable; path depends on observations |
| Cost and latency | Bounded, easy to estimate | Variable; grows with steps |
| Debugging | Inspect the failing step | Inspect a trajectory |
| Best for | Well-defined tasks you can decompose up front | Open-ended tasks where the steps can't be known in advance |

- These terms (and the five patterns below) follow Anthropic's widely cited "Building effective agents" taxonomy; most frameworks name the same ideas differently.

---

## The Complexity Ladder

- Climb only as far as the task forces you:
    1. **Single call** with a good prompt.
    2. **Augmented single call**: plus retrieval or a couple of tools.
    3. **Workflow**: a fixed composition of calls (patterns below).
    4. **Agent**: a model-driven loop with tools.
    5. **Multi-agent**: several agents coordinating ([[05 - Multi-Agent Topologies and Concurrency]]).
- At each rung, measure with evals ([[11 - Evals Observability and Silent Drift]]) before moving up. "Agentic" is a means, not a requirement.

---

## The Five Workflow Patterns

- In the code below, `llm(prompt) -> str` is a thin wrapper around a model call ([[00 - LLM Fundamentals for Engineers]]).

### 1. Prompt chaining

- Split a task into **fixed sequential steps**, each call consuming the previous output, with programmatic **gates** in between to stop early when a step fails a check.
- Trades latency for accuracy: each call gets an easier sub-task.

```python
def write_release_note(diff: str) -> str:
    summary = llm(f"Summarise the user-visible changes in this diff:\n<diff>{diff}</diff>")
    if "no user-visible changes" in summary.lower():       # gate: cheap check in code
        return ""
    draft = llm(f"Write a release note for customers from:\n{summary}")
    return llm(f"Edit for tone and under 80 words:\n{draft}")
```

- **Pick when:** the task decomposes cleanly into known steps (outline → draft → polish; extract → validate → format).

### 2. Routing

- **Classify** the input, then send it to a specialised prompt, tool set or model. Separation of concerns: each branch can be optimised without hurting the others.

```python
ROUTES = {
    "refund":    handle_refund,       # strict prompt, refund tools, careful model
    "tracking":  handle_tracking,     # order-status tool, cheap model
    "technical": handle_technical,    # docs RAG
}

def handle(ticket: str) -> str:
    label = llm(f"Classify as one of {list(ROUTES)}. Answer with the label only.\n{ticket}").strip()
    return ROUTES.get(label, handle_general)(ticket)
```

- Routing is also the main **cost** lever: easy queries go to a small model, hard ones to a large one ([[12 - LLM Cost and Latency Economics]]). The router can be an LLM, a small classifier, or embeddings plus rules.
- **Pick when:** inputs fall into distinct categories that are handled better separately.

### 3. Parallelisation

- Run calls **concurrently** and aggregate in code. Two variants:
    + **Sectioning**: independent sub-tasks in parallel (check policy compliance, check tone and extract entities at once).
    + **Voting**: the same task several times (or with different prompts) and combine (majority, any-flag), for higher confidence.

```python
import asyncio

async def review(code: str) -> dict:
    security, perf, style = await asyncio.gather(
        allm(f"List security issues only:\n{code}"),
        allm(f"List performance issues only:\n{code}"),
        allm(f"List style issues only:\n{code}"),
    )
    return {"security": security, "performance": perf, "style": style}

async def is_unsafe(text: str, votes: int = 3) -> bool:
    answers = await asyncio.gather(*[allm(f"Is this unsafe? yes/no:\n{text}") for _ in range(votes)])
    return sum(a.strip().lower().startswith("yes") for a in answers) >= 2   # majority vote
```

- **Pick when:** sub-tasks are independent (latency win), or a decision needs several perspectives (accuracy win). Guardrail checks often run in parallel with the main answer.

### 4. Orchestrator-workers

- A central LLM **breaks the task down at runtime** into sub-tasks it couldn't know in advance, delegates them to worker calls, and synthesises the results.
- Differs from parallelisation because the sub-tasks are **decided by the model**, not fixed in code.

```python
from pydantic import BaseModel

class Plan(BaseModel):
    subtasks: list[str]

async def research(question: str) -> str:
    plan = Plan.model_validate_json(llm_json(
        f"Break this into 2-5 independent research sub-questions:\n{question}", Plan))
    findings = await asyncio.gather(*[worker(q) for q in plan.subtasks])   # each worker may use tools
    return llm(f"Answer '{question}' using these findings:\n" + "\n---\n".join(findings))
```

- **Pick when:** the number and nature of sub-tasks depend on the input (multi-file code changes, research across several sources).

### 5. Evaluator-optimizer

- One call **generates**, another **evaluates against criteria** and returns feedback, loop until it passes or a cap is hit.
- Works when there are **clear evaluation criteria** and iteration measurably helps. Works best when the evaluator has **external ground truth** (tests, a compiler, a schema) rather than pure model opinion ([[02 - Execution Loops and Control Topologies]]).

```python
def generate_sql(question: str, schema: str, max_rounds: int = 3) -> str:
    feedback = ""
    for _ in range(max_rounds):
        sql = llm(f"Schema:\n{schema}\nWrite SQL for: {question}\n{feedback}")
        ok, error = dry_run(sql)                      # external check: EXPLAIN against the DB
        if ok:
            return sql
        feedback = f"Previous attempt failed with: {error}. Fix it."
    raise RuntimeError("could not produce valid SQL")
```

- **Pick when:** quality can be checked and feedback is actionable (code that must compile, translations against a glossary, outputs that must satisfy a validator).

---

## When an Agent Is Worth It

- Choose an agent when **all** of these hold:
    + The steps **can't be predicted** up front (debug a failing build, research an open question, operate a UI).
    + The environment gives **feedback** at each step (tool results, test output, errors) the model can act on.
    + You can **bound** it: step and cost budgets, safe or sandboxed tools, human approval for risky actions.
    + You can **evaluate** whole trajectories, not just final answers.
- Prefer a workflow when the process is known (even if long), when latency or cost must be predictable, or when errors are expensive and hard to reverse.
- Common middle ground: a **workflow with agentic steps**. The overall flow is fixed (classify → retrieve → act → confirm) and only one step (say, "resolve the technical issue") is an agent loop with a tight budget.

### Why autonomy is expensive

- **Error compounding**: if each step succeeds 95% of the time, a 10-step trajectory succeeds ~60% of the time (0.95¹⁰ ≈ 0.60). Workflows keep the number of model-decided steps small.
- **Cost and latency multiply** by the number of steps, and each step re-sends the growing context.
- **Evaluation is harder**: two correct trajectories can look completely different.

---

## Frameworks: Use, but Know What's Underneath

- Frameworks (LangGraph, OpenAI Agents SDK, LlamaIndex, CrewAI, and others) speed things up but add abstraction layers that hide prompts and control flow.
- Interview stance: "I'd start with plain API calls or a thin graph framework so I can see every prompt and transition; I'd adopt a framework for durability, checkpointing and tracing, not to avoid understanding the loop."

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Agent for a known process** | Unpredictable paths, cost spikes, hard debugging for something a workflow handles | Start on the lowest ladder rung that passes evals |
| **Error compounding** | Long autonomous trajectories fail often | Fewer model-decided steps; checkpoints and validation between steps |
| **Ungated chains** | A bad early output poisons every later step | Programmatic gates between steps |
| **Router misclassification** | Requests go to the wrong handler silently | Fallback route, confidence threshold, evals on the router itself |
| **Evaluator without ground truth** | The loop polishes confident nonsense | External checks (tests, validators) in the evaluator |
| **Framework opacity** | Can't see the prompt actually sent | Trace every call; keep prompts inspectable |

---

## Interview Lines

> [!tip] Say it like this
> - "Workflows put control flow in code; agents put it in the model. I start with the simplest rung that passes evals."
> - "The five building patterns are prompt chaining, routing, parallelisation, orchestrator-workers and evaluator-optimizer."
> - "I'd only use an agent when the steps can't be known up front, the environment gives feedback, and I can bound and evaluate it."
> - "Errors compound: ten steps at 95% each is about 60% end to end, so I minimise model-decided steps."
> - "A common production shape is a fixed workflow with one tightly budgeted agentic step."

## Related Notes

- [[00 - LLM Fundamentals for Engineers]] · [[02 - Execution Loops and Control Topologies]] · [[05 - Multi-Agent Topologies and Concurrency]]
- [[Agentic/README]]
