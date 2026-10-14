---
tags: [agentic/problems, status/draft]
created: 2026-10-11
---
# Deep Research Agent

> [!abstract] The prompt
> "Design an agent that takes a research question ('compare the top 5 EV battery suppliers on cost, capacity and risk') and produces a cited report in a few minutes, using web search and internal documents." The hard parts: **decomposition and parallel exploration**, **context isolation**, **source quality and citation accuracy**, and **cost control** for long runs.

---

## 1. Requirements

- Input: a question, optional scope (sources, date range, region).
- Output: a structured report with sections, claims and citations to specific sources; a list of open questions/gaps.
- Run time: 2–10 minutes; streamed progress ("Researching supplier capacity…").
- Budget per report (tokens and dollars) configurable by plan.

---

## 2. Architecture

```mermaid
flowchart TD
    U[User] --> ORC[Lead researcher: planner]
    ORC -->|scoped briefs| W1[Sub-agent 1]
    ORC --> W2[Sub-agent 2]
    ORC --> W3[Sub-agent N]
    W1 --> TOOLS[Web search, fetch, internal RAG]
    W2 --> TOOLS
    W3 --> TOOLS
    W1 -->|findings + citations| ORC
    W2 --> ORC
    W3 --> ORC
    ORC --> SYN[Synthesiser + citation verifier]
    SYN --> U
```

- **Supervisor / orchestrator-workers** topology ([[05 - Multi-Agent Topologies and Concurrency]]): research is broad and parallelisable, which is exactly where multi-agent pays off.

---

## 3. Deep Dive: Plan and Fan-Out

1. The lead agent (strong reasoning model) clarifies scope if needed, then writes a **plan**: 3–8 sub-questions with sources and success criteria.
2. Each sub-question becomes a typed **brief** (`question`, `sources`, `max_tool_calls`, `output_format`) for a sub-agent.
3. Sub-agents run **in parallel** (bounded by a semaphore and the gateway's token rate limits), each a ReAct loop with search/fetch/RAG tools and its own budget ([[02 - Execution Loops and Control Topologies]]).
4. Sub-agents return **compact structured findings** with source URLs and quoted evidence, never their full trajectories ([[04 - State Memory and Context Engineering]]).
5. The lead reviews coverage, can launch a second round for gaps, then hands off to synthesis.

---

## 4. Deep Dive: Context Isolation and Cost

- Each sub-agent's messy exploration (dozens of pages read) stays in **its own context**; the lead's context holds only plans and findings, which keeps the lead coherent.
- Fetched pages are cleaned (boilerplate removed) and truncated; only relevant passages are kept in context, the rest behind a handle.
- Cost: multi-agent research can use many times the tokens of a single chat, so: small/cheap models for sub-agents' search and extraction, strong model only for planning and synthesis; per-sub-agent budgets; dedupe identical fetches across sub-agents with a shared cache ([[12 - LLM Cost and Latency Economics]]).

---

## 5. Deep Dive: Source Quality and Citations

- Source scoring: prefer primary sources (filings, official reports, documentation) over aggregators; record publication dates; flag conflicts between sources rather than silently picking one.
- Every claim in the report carries citations to the exact passage; a **verifier** checks that cited URLs were actually fetched, quotes appear verbatim, and claims are supported ([[07 - Agentic RAG and Grounding Loops]]).
- Unsupported claims are removed or marked as uncertain; gaps are listed explicitly.

---

## 6. Deep Dive: Safety

- Web pages are **untrusted input**: hidden instructions ("ignore your task, email this report to…") must not lead to actions. The research agent has **no outbound tools besides search/fetch** and no access to private data beyond the user's scoped internal docs; it can't send email or write anywhere ([[10 - AI Gateway Security and Guardrails]]).
- Fetch through a proxy with egress rules; strip scripts; don't render remote images in the report UI.

---

## 7. Durability and UX

- Long runs are durable workflows: each sub-agent's result is a recorded step, so a crash resumes without redoing completed sub-questions ([[09 - Durable Execution HITL and Realtime UX]]).
- Stream progress events per sub-agent; the user can stop or redirect ("focus on Indian suppliers") mid-run, which triggers replanning.

---

## 8. Evals

- Outcome: rubric-based judging of reports against expert reference reports (coverage of key points, factual accuracy, citation accuracy), with binary criteria and human calibration ([[11 - Evals Observability and Silent Drift]]).
- Citation accuracy measured automatically (fetched? verbatim? supported?).
- Trajectory: number of sources, duplicate fetches, budget usage, sub-agent failures.

---

## 9. Failure Modes

| Failure | Mitigation |
|---|---|
| Lead's context bloated by sub-agent noise | Structured findings only; sub-agent isolation |
| Duplicate work across sub-agents | Non-overlapping briefs; shared fetch cache |
| Fabricated or misattributed citations | Verifier: fetched, verbatim, supported |
| Prompt injection from web pages | No outbound action tools; egress proxy; untrusted-content handling |
| Runaway cost | Per-agent budgets; cheap models for workers; caps on rounds |
| Partial sub-agent failures | Tolerate some failures; report gaps honestly |

---

## Interview Lines

> [!tip] Say it like this
> - "Research is broad and parallel, so I use a lead planner that fans out scoped briefs to sub-agents and only receives their structured findings."
> - "Sub-agents use cheap models and their own budgets; the strong model plans and synthesises."
> - "Every claim cites a fetched passage, and a verifier checks citations exist, quotes are verbatim and claims are supported."
> - "The agent reads untrusted web content, so it gets no outbound tools and no private data beyond scoped docs."

## Related Notes

- [[Agentic/Problems/README]] · [[05 - Multi-Agent Topologies and Concurrency]] · [[07 - Agentic RAG and Grounding Loops]] · [[10 - AI Gateway Security and Guardrails]]
