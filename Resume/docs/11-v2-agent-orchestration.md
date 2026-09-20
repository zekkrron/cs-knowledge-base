# 11 — v2 Agent Orchestration Engine

Resume mapping: *"Spearheaded a hierarchical AI Agent framework (LangGraph) leveraging
event-driven multi-step orchestration and child agents to automate 20+ business workflows."*

This is the deep-dive companion to `03-agents.md`. It documents **how the v2 agent runtime
actually works** — the LangGraph `StateGraph`, its nodes, the shared state, routing (rule vs
prompt), hierarchical child agents, guardrails, hooks, bulk fan-out, cost control, and
hibernation.

> Source: `mcp_collection` repo (git root), branch **`loop-node`**, directory
> `mcp_collection/mcp-gateway/v2/`. Endpoint: `POST /{teamname}/custom-agent/{slug}/v2/chat`.

---

## 1. Big picture

A v2 agent is not a single LLM call — it is a **compiled LangGraph pipeline** built per request
from the agent's catalog config. The pipeline resolves the agent, runs pre-hooks and input
guardrails, decides how to route (deterministic rule → a specific child agent, or prompt → LLM
with child agents/tools registered), runs the LLM tool-calling loop (or the child), then runs
output guardrails, post-hooks, and formats the response. Child agents are invoked **recursively
as sub-graphs**, which is what makes the framework *hierarchical*.

Key files:

| File | Role |
|---|---|
| `v2/handler.py` | HTTP endpoint; parses request, resolves agent, routes SINGLE vs BULK, builds + invokes the graph, session + Langfuse tracing, error→HTTP mapping. |
| `v2/graph_builder.py` | `GraphBuilder` — constructs/compiles the LangGraph `StateGraph` (`build` and slim `build_slim`). |
| `v2/state.py` | `AgentGraphState` TypedDict — the shared state passed between nodes. |
| `v2/nodes/*` | The node implementations (resolve, hooks, guardrails, routing, llm loop, child, external, format). |
| `v2/engine/*` | Engines: `rule_engine`, `hook_executor`, `model_factory`, `bulk_executor`, `agent_resolver`. |
| `v2/cost_handling.py` | Budget enforcement + per-trace cost calc (uses Langfuse metrics). |
| `v2/media_resolver.py`, `v2/email_notifier.py` | Media resolution; cost-alert emails. |

---

## 2. The graph — `v2/graph_builder.py` (`GraphBuilder.build`)

`GraphBuilder` is constructed once at startup with shared clients (`catalog_client`,
`spec_resolver`, `api_executor`, `session_client`, `hook_executor`, `model_factory`,
`guardrail_client`, `hibernation_manager`, `transformer_cache`). `build(slug, auth_token,
request_id, depth, config)` creates node instances and wires a `StateGraph(AgentGraphState)`:

**Nodes registered:** `resolve_agent`, `external_agent`, `run_pre_node`, `run_pre_guardrail`,
`evaluate_routing`, `call_deterministic_child`, `llm_agent_loop`, `run_post_guardrail`,
`run_post_node`, `format_response`.

**Edges (the pipeline):**
```
START → resolve_agent
resolve_agent → (conditional: route_after_resolve)
     ├─ error            → format_response
     ├─ origin=external  → external_agent → format_response → END
     └─ internal         → run_pre_node
run_pre_node → run_pre_guardrail → evaluate_routing
evaluate_routing → (conditional: route_after_evaluation)
     ├─ error            → format_response
     ├─ mode == "rule"   → call_deterministic_child
     └─ else             → llm_agent_loop
call_deterministic_child ┐
llm_agent_loop           ┴→ run_post_guardrail → run_post_node → format_response → END
```
The graph is compiled with `graph.compile().with_config(config=config)` — `config` carries the
Langfuse callback + trace metadata + `run_name` so every node/LLM/tool is traced.

**Conditional routers (module functions):**
- `route_after_resolve(state)` — `error` → `format_response`; `agent_config.origin == "external"`
  → `external_agent`; else → `run_pre_node`.
- `route_after_evaluation(state)` — `error` → `format_response`; `routing_result.mode == "rule"`
  → `call_deterministic_child`; else → `llm_agent_loop`.

**`build_slim(...)`** — a reduced graph that starts at `run_pre_guardrail` (skips
`resolve_agent` + pre-hooks). Used by the **bulk executor**, where agent resolution and the
pre-hook splitter already ran at the handler level, so each fanned-out item shouldn't repeat them.

---

## 3. Shared state — `v2/state.py` (`AgentGraphState`)

A `TypedDict` threaded through every node. Grouped fields:
- **Request context**: `slug`, `version_number`, `request` (raw body), `auth_token`,
  `request_id`, `session_id`, `client_headers` (forwarded headers for os1/hyperlocal).
- **Resolved config** (populated by `resolve_agent`): `agent_config`, `model_config`,
  `resolved_tools` (openspec dicts), `resolved_agents` (child agent configs), `pre_hooks`,
  `post_hooks`, `execution_config` (`max_iteration`, `timeout`, `max_depth`),
  `observability_config` (`info_log_enabled`), `system_prompt`, `prompt_instructions`.
- **Execution state**: `pre_hook_output`, `routing_result` (`RoutingResult`), `llm_output`,
  `post_hook_output`, `token_usage` (`TokenUsage`), `current_depth`, `max_depth`.
- **Guardrail/timeout**: `start_time` (monotonic), `timeout`, `error`.
- **Events**: `events` (tool calls, LLM responses — for session storage).
- **Hibernation**: `resume_messages`, `hibernation` signal, `trace_id` (for trace continuity).
- **Response**: `response` (`{session_id, contents, meta}`, set by `format_response`).

Every node returns a partial dict merged into this state. Nodes **short-circuit** when
`state["error"]` is already set, and check `check_timeout(state)` (wall-clock via `start_time`
+ `timeout`) — that's the "event-driven multi-step" control: any step can set `error` and the
conditional edges funnel straight to `format_response`.

---

## 4. The nodes — `v2/nodes/`

- **`resolve_agent.py` (`ResolveAgentNode`)** — fetches the agent config from catalog
  (`get_agent_detail_by_slug` via `catalog_client`), resolves linked tools' openspecs
  (`spec_resolver`), and populates `agent_config`, `model_config`, `resolved_tools`,
  `resolved_agents` (child agents), `pre_hooks`/`post_hooks`, `execution_config`,
  `system_prompt`. Errors set `Agent '{slug}' not found` / `has no active version`, or a cost
  block. Runs the **budget cost check** (`cost_handling.run_cost_check`) — blocks with a
  cost-limit error when the agent exceeded its daily/weekly/monthly budget.
- **`external_agent.py` (`ExternalAgentNode`)** — fast path for imported/external agents
  (`origin == "external"`): forwards to the external agent endpoint and jumps straight to
  `format_response`.
- **`run_hooks.py` (`PreNodeExecutor` / `PostNodeExecutor`)** — execute the agent's pre/post
  hooks via `HookExecutor` (`v2/engine/hook_executor.py`). Hooks are user-supplied code run in a
  sandbox (see `mcp-gateway/docs/hook-sandbox-security.md`); pre-hooks enrich context
  (`pre_hook_output`), post-hooks transform output (`post_hook_output`). In BULK mode the first
  pre-hook doubles as the **splitter**.
- **`run_guardrails.py` (`RunPreGuardrailNode` / `RunPostGuardrailNode`)** — run the guardrail
  chain (`guardrail_client`) on input and output. A block sets `error =
  "GUARDRAIL_BLOCKED:..."` which the handler maps to HTTP 200 with `meta.status = "blocked"`.
- **`evaluate_routing.py` (`EvaluateRoutingNode`)** — delegates to `RuleEngine.evaluate(...)`
  (see §5) and writes `routing_result`.
- **`call_child.py` (`CallDeterministicChildNode`)** — the **hierarchical child-agent** step
  (see §6).
- **`llm_loop.py` (`LLMAgentLoopNode`)** — the core LLM tool-calling loop (see §7).
- **`format_response.py` (`FormatResponseNode`)** — assembles the final
  `{session_id, contents, meta}` response from `llm_output`/`post_hook_output` + `token_usage`.

---

## 5. Routing — rule vs prompt — `v2/engine/rule_engine.py` (`RuleEngine`)

For each child agent, routing is either **deterministic (rule)** or **LLM-driven (prompt)**:

`RuleEngine.evaluate(agents, context, contents) -> RoutingResult`:
- For each child agent, read `routing.mode`:
  - `"rule"` → evaluate `routing.condition` against `{context, contents}`. **First rule match
    wins (short-circuit)** → `RoutingResult(mode="rule", matched_agent_slug=<slug>)`.
  - `"prompt"` (or missing) → collect into `prompt_agents`.
- If no rule matched but there are prompt agents → `mode="prompt"` (those children are
  registered as LLM tools). Else `mode="none"`.

**Condition evaluation** (`_evaluate_condition`): a `combinator` (`and`/`or`) over `rules`; each
rule is `{field, operator, value}`.
- **Field resolution** (`_resolve_field`) supports dot-notation and content-type-aware
  shortcuts: `context.{key}`, `contents.text` (first text content), `contents.json.{key}`
  (first json content's data), `contents.{type}` (exists-check for files/images/etc.),
  `contents.0.text` (index access).
- **Operators** (`_eval_operator`): `exists`, `equals`, `not_equals`, `contains`, `in`,
  `gt`, `lt`.

This is the "event-driven" routing: the incoming request's shape/content deterministically
selects a specialized child agent when a rule matches, otherwise the LLM decides.

---

## 6. Hierarchical child agents — `v2/nodes/call_child.py` (`CallDeterministicChildNode`)

When routing is `mode="rule"`, the matched child agent is executed **as a recursive sub-graph**:
1. Short-circuit on `error`; check timeout.
2. **Depth guard** — if `current_depth >= max_depth` → `"Maximum child agent nesting depth
   exceeded"`. This bounds recursion.
3. Build the child graph via `graph_builder.build(child_slug, depth=current_depth+1)` — the
   *same* GraphBuilder, so a child is a full agent pipeline of its own (it can itself route to
   further children → true hierarchy).
4. Invoke the child with the full request payload; the child gets its **own `session_id`
   (uuid4)** but shares `request_id` and `auth_token`.
5. **Accumulate token usage** — child input/output/total tokens are added into the parent's
   `token_usage`, so the top-level response reflects the whole tree's cost.
6. Return `llm_output` (child's output) + accumulated `token_usage`.

The LLM-driven counterpart: in `prompt` mode, `prompt_agents` are registered as callable tools
inside the LLM loop (§7), so the model can invoke a child agent mid-reasoning. Both paths realize
"child agents".

---

## 7. The LLM tool-calling loop — `v2/nodes/llm_loop.py` (`LLMAgentLoopNode`)

The multi-step reasoning core. Constructed with `model_factory`, `api_executor`, the
`graph_builder` (to invoke child agents), `session_client`, `spec_resolver`,
`hibernation_manager`, `transformer_cache`. It runs the classic agent loop:
1. Build the model client from `model_config` via `ModelClientFactory`
   (`v2/engine/model_factory.py`) — model, temperature, max_tokens, timeout, retry, and
   **fallback models**.
2. Register tools: the agent's `resolved_tools` (OpenAPI-derived, executed through
   `api_executor`) plus `routing_result.prompt_agents` (child agents exposed as tools) plus
   system tools (e.g. the **hibernate** tool).
3. Iterate up to `execution_config.max_iteration`: call the LLM → if it returns tool calls,
   execute them (HTTP tools via `api_executor`, optionally transforming responses via
   `transformer_cache`; child-agent tools via the recursive sub-graph) → feed results back →
   repeat until a final text answer or the iteration cap. Hitting the cap sets
   `"Max iterations (N) reached"` (mapped to HTTP 200, degraded).
4. Accumulate `token_usage` and append `events`.
5. **Hibernation**: if the model calls the hibernate tool, the loop sets `state["hibernation"]`
   and the run suspends (persisted by `hibernation_manager`) to resume later via the hibernation
   webhook — the agent-side analogue of workflow pause/resume (see `05-pause-resume.md`).

---

## 8. Request handling — `v2/handler.py` (`handle_agent_v2_chat`)

Endpoint `POST /{teamname}/custom-agent/{slug}/v2/chat`. Flow:
1. Parse body (`session_id`, `context`, `contents`, `metadata`). Supports a **bulk array** body
   (list of full request objects, capped at `BATCH_MAX_ITEMS`).
2. Validate `contents` non-empty; extract/generate `request_id` (into a contextvar for
   request-scoped logging) and `session_id`.
3. Create a **Langfuse `OptimizedLangfuseCallbackHandler`** with a fresh `trace_id` and
   trace metadata (`langfuse_user_id`, `langfuse_session_id`, trace name, `agent_id`, teamname,
   request_id, version). (Details in `12-observability-langfuse.md`.)
4. `initiate_chat_session(...)` (status: processing) via `session_client`.
5. **Resolve at handler level** (`agent_resolver.resolve_agent_config`) to pick execution mode:
   - **BULK** — run the pre-hook **splitter** (`_run_splitter`) or split `contents`/the body
     array into items, then `execute_bulk(...)` (`v2/engine/bulk_executor.py`) fans them out
     through `build_slim` graphs concurrently (`BATCH_MAX_CONCURRENT`, per-item timeout);
     concatenates text + sums tokens; `all_failed` → HTTP 500, else partial/full success.
   - **SINGLE** — `graph_builder.build(...)`, construct `initial_state`, and
     `asyncio.wait_for(graph.ainvoke(initial_state, config={callbacks, metadata, run_name}),
     timeout)` wrapped in `propagate_attributes(...)` for trace attribution.
6. **Hibernation response** — if `final_state["hibernation"]`, return 200 with
   `meta.status = "hibernating"` + `wait_seconds`.
7. Extract `response` from `format_response`; on `error`, map via `_classify_graph_error` to the
   right HTTP status:
   - 200: `GUARDRAIL_BLOCKED:`, `Max iterations (...)`, hook failures (degraded).
   - 404: agent not found / no active version.
   - 429: cost/budget limit.
   - 504: `Execution timeout exceeded`.
   - 502: catalog errors.
   - 422: depth exceeded / no matched child slug.
   - 500: everything else.
8. `_complete_v2_session(...)` (status success/error) and return `{session_id, contents, meta}`.
   `meta.execution_mode` = `single` | `bulk`.

Exception mapping: `SpecNotFound`→404, `AgentInactive`→403, `CatalogApiError`→502,
`TimeoutError`→504, else 500.

---

## 9. Cost control — `v2/cost_handling.py`

Two responsibilities:
1. **Budget enforcement** (`run_cost_check`) — reads the agent's `cost_config.budget`
   (`interval` daily/weekly/monthly, `max_cost`, `alert_at` %). `fetch_agent_cost` queries the
   **Langfuse metrics API** (`/api/public/metrics`, `sum(totalCost)` filtered by
   `metadata.agent_id == slug`) for the current period (`get_period_bounds`, IST). If usage
   crosses `alert_at` %, it sends a one-per-period warning email (`email_notifier.send_cost_
   warning_email`, deduped via a Redis key with period TTL). If usage ≥ `max_cost`, it returns a
   cost-limit error → the run is blocked (HTTP 429). This is invoked from `resolve_agent`.
2. **Per-trace cost calc** (`calculate_trace_cost`) — converts token usage to USD using a pricing
   map (`COST_PER_MILLION_TOKENS_JSON` env, else defaults for gemini-2.5-flash/pro/2.0-flash),
   with cached-input tokens discounted (cache_read rate). Used for observability/attribution.

This budget + cost machinery is what allows per-workflow/agent cost figures (e.g. the Orion
indent-cost reductions in `03-agents.md`).

---

## 10. Supporting engines — `v2/engine/`

- `model_factory.py` (`ModelClientFactory`) — builds the LLM client for the agent's configured
  model with temperature/max_tokens/timeout/retry and **fallback models**.
- `hook_executor.py` (`HookExecutor`) — runs pre/post hooks (sandboxed user code);
  `execute_single` used by the bulk splitter.
- `bulk_executor.py` (`execute_bulk`) — concurrent fan-out of items through `build_slim` graphs
  with a per-item timeout; aggregates results/tokens; reports `all_failed`/partial.
- `agent_resolver.py` (`resolve_agent_config`) — handler-level resolve used to pick SINGLE vs
  BULK before graph construction (and shared by the slim path).
- `rule_engine.py` — §5.

Media inputs (files/images) are resolved by `v2/media_resolver.py`; cost-alert emails by
`v2/email_notifier.py`.

---

## 11. How the resume claim decodes

- **Hierarchical AI Agent framework (LangGraph)** → §2 compiled `StateGraph` + §6 recursive
  child-agent sub-graphs with a depth guard.
- **Event-driven multi-step orchestration** → §3 short-circuit-on-error/timeout state machine +
  §5 rule-based routing on request content/context + §7 iterative LLM tool-calling loop.
- **Child agents** → §6 (deterministic, rule-matched) and §7 (LLM-invoked prompt agents),
  token usage accumulated up the tree.
- **Automate 20+ business workflows** → these agents (later migrated to the workflow platform,
  see `04-workflows.md`) run the business automations; bulk mode (§8) fans a single request into
  many parallel agent runs.

---

## 12. Exact locations (branch `loop-node`, repo `mcp_collection`)

| Concern | File |
|---|---|
| Graph construction (full + slim) | `mcp-gateway/v2/graph_builder.py` |
| Shared state | `mcp-gateway/v2/state.py` |
| Endpoint / SINGLE vs BULK / error mapping | `mcp-gateway/v2/handler.py` |
| Resolve agent (+ cost check) | `mcp-gateway/v2/nodes/resolve_agent.py` |
| Pre/Post hooks | `mcp-gateway/v2/nodes/run_hooks.py`, `mcp-gateway/v2/engine/hook_executor.py` |
| Pre/Post guardrails | `mcp-gateway/v2/nodes/run_guardrails.py` |
| Routing (rule vs prompt) | `mcp-gateway/v2/nodes/evaluate_routing.py`, `mcp-gateway/v2/engine/rule_engine.py` |
| Child agent (recursive) | `mcp-gateway/v2/nodes/call_child.py` |
| LLM tool loop + hibernation | `mcp-gateway/v2/nodes/llm_loop.py` |
| External agent | `mcp-gateway/v2/nodes/external_agent.py` |
| Format response | `mcp-gateway/v2/nodes/format_response.py` |
| Bulk fan-out | `mcp-gateway/v2/engine/bulk_executor.py` |
| Model factory | `mcp-gateway/v2/engine/model_factory.py` |
| Cost / budget | `mcp-gateway/v2/cost_handling.py`, `mcp-gateway/v2/email_notifier.py` |
| v2 flow / indexes docs | `mcp-gateway/docs/v2-orchestration-flow.md`, `mcp-gateway/docs/mongodb-indexes-agent-v2.md` |
