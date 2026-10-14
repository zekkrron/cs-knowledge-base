---
tags: [agentic/topics, status/draft]
created: 2026-10-11
---
# Execution Loops and Control Topologies

> [!abstract] What this is
> How an agent actually runs: the main loop designs (**ReAct**, **Plan-and-Execute**, **ReWOO**), self-correction (**Reflexion** and deterministic correction with real feedback), representing control flow as a **DAG** or a **cyclic state machine** (LangGraph), and the guards that stop runaway loops. The decision this helps with: which loop shape fits the task, and how to keep it bounded.

---

## Why It Exists

- The basic tool loop ([[00 - LLM Fundamentals for Engineers]]) works for 2–3 steps. On longer tasks it drifts, repeats itself, burns tokens, or never stops.
- Loop topology decides **how often the model re-decides**, **how much context each decision sees**, and **where you can insert checks**, which together decide reliability, latency and cost.

---

## ReAct: Reason + Act, One Step at a Time

- The model interleaves **Thought → Action (tool call) → Observation (tool result)**, deciding each next step after seeing the last result. Today's native tool-calling loop is ReAct with the "thought" often implicit or in a reasoning model's hidden thinking.

```
Thought: I need the order status first.
Action: get_order_status(order_id="ORD-1234")
Observation: {"status": "DELIVERED", "delivered_at": "2026-10-09"}
Thought: Delivered 2 days ago, within the 7-day window. Check refund eligibility.
Action: check_refund_policy(order_id="ORD-1234")
...
```

- **Strengths:** adapts to every observation; simple; good for short, exploratory tasks.
- **Weaknesses:**
    + **Greedy, local decisions**: each step optimises the next move, not the overall goal. On long tasks it loses the thread (a heuristic often quoted is degradation after ~6+ steps; it depends heavily on the model).
    + **Cost**: every step re-sends the full growing history.
    + **Repetition**: it can call the same tool with the same arguments again and again.

---

## Plan-and-Execute

- Split **planning** from **doing**:
    1. A **planner** call produces an explicit multi-step plan.
    2. An **executor** (often a smaller model or a ReAct sub-loop) carries out one step at a time.
    3. A **replanner** looks at results so far and keeps, revises or finishes the plan.

```python
from pydantic import BaseModel

class Plan(BaseModel):
    steps: list[str]

class Replan(BaseModel):
    done: bool
    answer: str | None = None
    remaining_steps: list[str] = []

def plan_and_execute(goal: str, max_rounds: int = 8) -> str:
    plan = llm_struct(f"Make a short step-by-step plan for: {goal}", Plan)
    results: list[tuple[str, str]] = []
    for _ in range(max_rounds):
        if not plan.steps:
            break
        step, *rest = plan.steps
        results.append((step, execute_step(step, results)))     # small model / ReAct sub-loop
        decision = llm_struct(
            f"Goal: {goal}\nDone so far: {results}\nRemaining plan: {rest}\n"
            "Finish with an answer, or give the updated remaining steps.", Replan)
        if decision.done:
            return decision.answer
        plan = Plan(steps=decision.remaining_steps)
    return summarise_partial(goal, results)
```

- **Strengths:** keeps the **global goal** visible in an explicit plan; the expensive "thinking" model runs only at plan/replan points; steps can be checked or shown to a human.
- **Weaknesses:** a bad plan wastes the whole run; replanning adds calls; rigid if replanning is skipped.

---

## ReWOO: Reasoning Without Observation

- The planner writes the **entire plan up front, including tool calls with placeholders** that refer to earlier results. A worker then runs the tools **without calling the LLM between steps**, and a final **solver** call combines the evidence.

```
Plan: Find the order's delivery date.        #E1 = get_order(order_id="ORD-1234")
Plan: Check the refund policy for that date. #E2 = refund_policy(delivered_at=#E1.delivered_at)
Solver: answer using #E1 and #E2
```

- **Strengths:** very few LLM calls (plan + solve), so **lower cost and latency**; independent steps can run **in parallel**.
- **Weaknesses:** **can't adapt** mid-run: if `#E1` returns something unexpected, the plan doesn't change. Fails on exploratory tasks.

### Choosing a loop

| Loop | LLM calls | Adapts to results | Keeps global goal | Pick when |
|---|---|---|---|---|
| **ReAct** | One per step | Every step | Weakly | Short, exploratory tasks; unknown environment |
| **Plan-and-Execute** | Plan + per-step + replans | At replan points | Strongly (explicit plan) | Longer multi-step tasks; want inspectable plans |
| **ReWOO** | Plan + solve | No | Yes (fixed plan) | Predictable tool chains where cost/latency matter |

- In practice these mix: a plan-and-execute outer loop whose steps are short ReAct loops, or ReWOO with a fallback to ReAct when a placeholder resolves to an error.

---

## Self-Correction

### Reflexion

- After a failed attempt, the agent writes a **verbal reflection** ("I searched by customer name, but orders are keyed by email; next time look up the email first"), stores it in memory, and retries with that reflection in context. Across trials it improves without changing model weights.
- Reflexion works because it is driven by a **failure signal** (a test failing, a task-success check), not by the model's own opinion.

### Deterministic self-correction beats pure self-critique

- Asking a model to "check your answer" with no new information often **doesn't help and can make things worse**: it may change correct answers to wrong ones, or confidently approve errors. Research on intrinsic self-correction broadly supports this.
- Correction works when the loop includes **external ground truth**:

| Domain | Ground-truth signal |
|---|---|
| Code | Compiler errors, test results, linter, type checker |
| SQL | `EXPLAIN` / dry run, row-count sanity checks |
| Structured output | Schema and business-rule validation |
| Retrieval answers | Citation check: does the quoted text exist in the source? |
| Actions | The system's actual response (API error, state after the action) |

```python
def fix_until_green(task: str, max_attempts: int = 4) -> str:
    feedback = ""
    for attempt in range(max_attempts):
        patch = llm(f"{task}\n{feedback}")
        result = run_tests(apply(patch))                 # ground truth, not opinion
        if result.passed:
            return patch
        feedback = f"Attempt {attempt + 1} failed tests:\n{result.failures[:2000]}"
    raise RuntimeError("tests still failing")
```

- Rule: **every correction loop needs an external check and a cap.**

---

## Control Topologies: DAGs vs Cyclic State Machines

- Once a system has several steps, represent its control flow explicitly instead of nesting `if`s around model calls.

| Topology | Shape | Strengths | Pick when |
|---|---|---|---|
| **DAG** (pipeline) | Steps with dependencies, no cycles | Predictable, parallelisable, easy to reason about and retry | Workflows ([[01 - Workflows vs Agents]]), ingestion pipelines, ReWOO-style plans |
| **Cyclic state machine / graph** | Nodes with conditional edges, loops allowed | Represents agents: "call model → maybe tools → back to model" | Agents, correction loops, human-approval pauses |

### LangGraph as the reference model

- **LangGraph** models an agent as a **graph over shared state**: nodes are functions that read state and return updates; edges (including **conditional edges**) pick the next node; **reducers** decide how updates merge into state; a **checkpointer** saves state after each step so runs can pause, resume and be inspected ([[09 - Durable Execution HITL and Realtime UX]]).

```python
from typing import Annotated, TypedDict
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langgraph.checkpoint.memory import MemorySaver

class State(TypedDict):
    messages: Annotated[list, add_messages]     # reducer: append, don't overwrite
    steps: int

def agent(state: State) -> dict:
    reply = model_with_tools.invoke(state["messages"])
    return {"messages": [reply], "steps": state["steps"] + 1}

def tools(state: State) -> dict:
    return {"messages": run_tool_calls(state["messages"][-1])}

def route(state: State) -> str:
    last = state["messages"][-1]
    if state["steps"] >= 10:                    # hard step budget
        return END
    return "tools" if getattr(last, "tool_calls", None) else END

graph = StateGraph(State)
graph.add_node("agent", agent)
graph.add_node("tools", tools)
graph.add_edge(START, "agent")
graph.add_conditional_edges("agent", route, {"tools": "tools", END: END})
graph.add_edge("tools", "agent")                # the cycle
app = graph.compile(checkpointer=MemorySaver())

app.invoke({"messages": [("user", "Where is ORD-1234?")], "steps": 0},
           config={"configurable": {"thread_id": "conv-42"}})
```

- Why graphs over a hand-written loop: explicit, inspectable transitions; per-node retries and timeouts; checkpoint/resume and human-in-the-loop pauses; easy to add a deterministic node (validator, policy check) between model steps.
- The same ideas exist without LangGraph: a state machine ([[LLD/Foundations/04 - Enums and State Machines]]) plus persisted state is all a graph runtime is.

---

## Loop Termination and Runaway Detection

- Agents must be **guaranteed** to stop. Layer several guards; each catches a different failure.

| Guard | Catches | Typical setting |
|---|---|---|
| **Max steps** | Endless tool loops | 10–25 for most tasks |
| **Token / cost budget** per trace | Expensive context growth | e.g. 200k tokens or $0.50 per task, enforced by the runtime |
| **Wall-clock timeout** | Slow tools, hung calls | Per tool and per run |
| **Repeated-action detection** | Same tool + same arguments again | Hash `(tool, normalised args)`; stop or inject a warning on repeat |
| **No-progress detection** | Different calls that don't change state | No new information for N steps → escalate |
| **Explicit finish action** | Model rambling instead of ending | A `final_answer` tool / structured "done" |
| **Escalation path** | Hitting any limit | Hand off to a human or return a partial result honestly |

```python
import hashlib, json

class Budget:
    def __init__(self, max_steps=15, max_tokens=200_000):
        self.max_steps, self.max_tokens = max_steps, max_tokens
        self.steps = self.tokens = 0
        self.seen: dict[str, int] = {}

    def charge(self, usage_tokens: int) -> None:
        self.steps += 1
        self.tokens += usage_tokens
        if self.steps > self.max_steps or self.tokens > self.max_tokens:
            raise BudgetExceeded(f"steps={self.steps} tokens={self.tokens}")

    def check_repeat(self, tool: str, args: dict) -> bool:
        key = hashlib.sha256(f"{tool}:{json.dumps(args, sort_keys=True)}".encode()).hexdigest()
        self.seen[key] = self.seen.get(key, 0) + 1
        return self.seen[key] > 1          # caller: refuse, or tell the model it already has this result
```

- On a repeated call, a good response is to **not execute** and return an observation like "You already called this with these arguments; the result was X. Choose a different action or finish." This often breaks the loop without killing the run.
- Budgets must be enforced **by the runtime**, never by asking the model to stop.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Goal drift in long ReAct runs** | Greedy local steps lose the overall task | Plan-and-execute; restate the goal and plan each step; shorter sub-loops |
| **Infinite / repeated tool calls** | Same call over and over; cost explodes | Repeated-action hashing; step and token budgets |
| **Self-critique without ground truth** | Accuracy drops over iterations | Correction only with external checks (tests, compiler, validators) |
| **Rigid plan** | ReWOO or a stale plan proceeds on bad intermediate results | Replan points; fall back to ReAct on unexpected observations |
| **Unbounded context growth** | Each step slower and costlier | Observation masking and compaction ([[04 - State Memory and Context Engineering]]) |
| **Silent stop at a limit** | User gets nothing or a confident partial answer | Explicit escalation; honest partial results |

---

## Interview Lines

> [!tip] Say it like this
> - "ReAct re-decides every step, which adapts well but drifts on long tasks; plan-and-execute keeps an explicit plan; ReWOO plans tool calls up front for fewer LLM calls but can't adapt."
> - "Self-correction only works with external ground truth like tests or a compiler; pure self-critique often makes answers worse."
> - "I model the agent as a state graph with conditional edges and a checkpointer, so I can add deterministic nodes, pause for humans and resume after crashes."
> - "Termination is enforced by the runtime: step caps, token budgets, timeouts and repeated-action hashing, with an escalation path."

## Related Notes

- [[00 - LLM Fundamentals for Engineers]] · [[01 - Workflows vs Agents]] · [[04 - State Memory and Context Engineering]] · [[09 - Durable Execution HITL and Realtime UX]]
- [[Agentic/README]]
