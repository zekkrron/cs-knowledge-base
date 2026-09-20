# 12 — Observability & Langfuse Tracing

How the platform traces LLM/agent execution end-to-end with **Langfuse**: per-request traces,
nested generation/tool observations, token + thinking-token capture, trace-level attribution,
and the cost linkage that powers budgets.

> Source: `mcp_collection` repo, branch **`o11y-main`** (commits "FT added logger", "updated:
> trace capture", "FT added thinking token logger"). On this branch the Langfuse integration is
> implemented **directly with the Langfuse SDK v3** inside `server.py`, `execution/chat_runner.py`,
> and `llm/gemini_direct.py` (+ `config.py`) — there is no separate `observability/` module here.
> The LangGraph-callback variant (`OptimizedLangfuseCallbackHandler` in an `observability/`
> package) is the counterpart used by the v2-agent and workflow LangGraph runtimes on other
> branches (see §7 and `11-v2-agent-orchestration.md`).

---

## 1. Configuration — `mcp-gateway/config.py`

```python
# ── Langfuse Observability ──
LANGFUSE_SECRET_KEY = os.getenv("LANGFUSE_SECRET_KEY", "")
LANGFUSE_PUBLIC_KEY = os.getenv("LANGFUSE_PUBLIC_KEY", "")
LANGFUSE_BASE_URL   = os.getenv("LANGFUSE_BASE_URL", "https://cloud.langfuse.com")
LANGFUSE_ENABLED    = bool(LANGFUSE_SECRET_KEY and LANGFUSE_PUBLIC_KEY)

AGENT_THINKING_BUDGET = int(os.getenv("AGENT_THINKING_BUDGET", "0"))
```
- Langfuse is **enabled only when both keys are present** — otherwise the whole integration
  degrades to no-ops (see §2), so the gateway runs fine without observability configured.
- `AGENT_THINKING_BUDGET` controls Gemini's reasoning ("thinking") token budget (§5).
- `LANGFUSE_BASE_URL` is also the host the cost module hits for the metrics API (§6).

---

## 2. Client init + graceful fallback

Two entry points construct the singleton client:
- `server.py`:
  ```python
  from langfuse import observe, get_client as _get_langfuse, propagate_attributes
  _langfuse = _get_langfuse()
  ```
- `execution/chat_runner.py` guards the import so a missing/misconfigured Langfuse never breaks
  execution:
  ```python
  try:
      from langfuse import observe, get_client as _get_langfuse
      _langfuse = _get_langfuse()
  except Exception:
      _langfuse = None
      def observe(func=None, *, name=None, as_type=None, **kw):  # no-op decorator
          ... # returns the wrapped coroutine unchanged
  ```
  Every tracing call site is additionally guarded by `if _langfuse:`. **Observability is
  strictly best-effort** — it can never change the functional result or raise.

The SDK used is **Langfuse v3**, which models tracing as **nested observations** created via
context managers and decorators, rather than manual span bookkeeping.

---

## 3. The three tracing primitives used

1. **`@observe(as_type="agent")`** — decorates a handler (e.g. `server.py::handle_agent_chat`) so
   the whole request becomes the **root trace/observation**; every nested observation created
   during the call is automatically parented to it.
2. **`_langfuse.start_as_current_observation(as_type="generation"|..., name=, input=, model=)`** —
   a context manager wrapping a single LLM generation (or other unit). On exit the code calls
   `gen_obs.update(output=, usage_details={input, output}, metadata={latency_ms, iteration, ...})`.
3. **`propagate_attributes(...)`** — sets trace-level attributes (`user_id`, `session_id`, `tags`,
   `trace_name`, and custom `metadata` such as `agent_id`) that child observations inherit. Used
   both to stamp `agent_id` on everything and to make the trace searchable/attributable.

Additionally `_langfuse.update_current_generation(name=, input=)` renames/enriches the current
generation (e.g. set the trace name to a truncated user question).

---

## 4. Request-level tracing — `server.py::handle_agent_chat`

```python
@observe(as_type="agent")
async def handle_agent_chat(request):
    ...
    with propagate_attributes(metadata={"agent_id": slug}):   # stamps agent_id on all children
        ...
        _langfuse.update_current_generation(
            name=question_truncate,                            # trace name = truncated question
            input={"agent-slug": slug, "teamname": teamname, "body": body},
        )
        logger.info("[TRACE] agent_chat | user=%s | agent=%s", username, slug)
        ...
```
- The `@observe(as_type="agent")` decorator opens the root trace for the whole agent chat.
- `propagate_attributes(metadata={"agent_id": slug})` guarantees `agent_id` is attached to every
  child observation — this is exactly the key the **cost metrics query filters on** (§6).
- `[TRACE] ...` structured log lines (`tool_access`, `mcp_server_access`, `tool_chat`,
  `mcp_server_chat`, `agent_chat`) mirror access events for log-based audit alongside the traces.

---

## 5. Generation-level tracing — `execution/chat_runner.py::run_chat`

`run_chat` is the unified single/multi-tool/agent chat loop. Langfuse wraps **every LLM call** as
a `generation` observation. There are guarded (`if _langfuse:`) and unguarded branches for each
path so behavior is identical when Langfuse is off:

- **Zero-tool path** — `start_as_current_observation(as_type="generation", name="llm-zero-tool",
  input=question, model=GEMINI_MODEL)`, then `gen_obs.update(output=..., usage_details={input,
  output}, metadata={latency_ms})`.
- **Agentic loop (per iteration)** — `name=f"llm-call-iter-{i+1}"`, `input` is a representative
  payload (system prompts + history + question + bound tool names on iter 0; the full replayed
  message list on later iters), and `update(...)` records output (text or the tool-call names),
  `usage_details`, and `metadata={latency_ms, iteration}`.
- **Malformed-function-call retries** — each retry is its own generation observation
  (`name=f"llm-retry-iter-{i+1}-attempt-{n}"`, `metadata={"retry": True}`), so retries are
  visible in the trace rather than hidden.

Token usage is accumulated across all calls via `_extract_usage(ai_msg)` (reads
`ai_msg.usage_metadata`: `input_tokens`, `output_tokens`, `total_tokens`) and reported both in
the response `meta` and per-observation `usage_details`. Tool executions in the loop are likewise
wrapped in observations so the trace shows the interleaving of LLM reasoning and tool calls.

---

## 6. Thinking-token capture — `llm/gemini_direct.py`

`GeminiDirectClient` is the native Gemini SDK client. It captures Gemini's **reasoning/thinking**
usage that ordinary token counts miss:
```python
usage_metadata = {
    "input_tokens":  response.usage_metadata.prompt_token_count or 0,
    "output_tokens": response.usage_metadata.candidates_token_count or 0,
    "total_tokens":  response.usage_metadata.total_token_count or 0,
    "cache_read":    getattr(response.usage_metadata, "cached_content_token_count", 0) or 0,
    "thinking_tokens": getattr(response.usage_metadata, "thoughts_token_count", 0) or 0,
}
```
- **`thinking_tokens`** (`thoughts_token_count`) and **`cache_read`** (`cached_content_token_count`)
  are surfaced alongside the normal counts and logged in `_log_response`
  (`... tokens — in / out / total / cache_read / thinking`). This is the "thinking token logger."
- The thinking budget is applied via `_thinking_config()` →
  `genai_types.ThinkingConfig(thinking_budget=config.AGENT_THINKING_BUDGET)`, passed into every
  `GenerateContentConfig` (main calls and fallbacks).
- Capturing cached vs thinking vs regular tokens is what makes the **cost calculation** accurate
  (cached input is discounted; see `11-v2-agent-orchestration.md` §9 `calculate_trace_cost`).

---

## 7. The LangGraph callback variant — `OptimizedLangfuseCallbackHandler`

The v2-agent and workflow runtimes are LangGraph graphs, so they trace via a **callback handler**
rather than manual context managers. Both the v2 handler (`v2/handler.py`) and the workflow
handlers (`workflow/handler.py`, `workflow/resume_handler.py`) do:
```python
from observability import langfuse_client as _langfuse, OptimizedLangfuseCallbackHandler, propagate_attributes, observe
trace_id = Langfuse.create_trace_id()
langfuse_handler = OptimizedLangfuseCallbackHandler(trace_context={"trace_id": trace_id})
graph.ainvoke(state, config={"callbacks": [langfuse_handler], "metadata": trace_metadata, "run_name": f"Agent:{slug}"})
```
- Passing the handler in `config["callbacks"]` makes LangGraph automatically emit an observation
  for **every node, LLM generation, and tool call** — no per-call instrumentation needed.
- **"Optimized"**: the handler strips bloat that inflates the observation store — Gemini thought
  signatures (~6 KB base64 per generation), duplicate `function_call` objects, full tool schemas
  in `invocation_params`, and large/sensitive LangGraph state keys — for roughly a **60% per-trace
  storage reduction** (~5 MB → ~1.5 MB typical).
- `trace_id` is created up front and threaded into the run so **trace continuity survives
  pause/resume and hibernation**: a resumed workflow rebuilds the handler from the saved
  `trace_id` (`workflow/resume_handler.py`) so the resume stitches onto the original trace
  (see `05-pause-resume.md`, and pause/resume observability test cases TC-39/TC-40).
- `trace_metadata` carries `langfuse_user_id`, `langfuse_session_id`, `langfuse_trace_name`,
  `agent_id`, `teamname`, `request_id`, `version` — the same attribution keys as §4.

This handler lives in the `observability/` package on the LangGraph-integrated branches; on
`o11y-main` the equivalent tracing for the (non-LangGraph) chat_runner path is the manual
context-manager approach of §5.

---

## 8. Cost linkage (traces → budgets)

Because every trace is tagged with `metadata.agent_id`, the cost subsystem
(`v2/cost_handling.py`, `11-v2-agent-orchestration.md` §9) queries the **Langfuse metrics API**
(`{LANGFUSE_BASE_URL}/api/public/metrics`, Basic auth from public:secret keys) for
`sum(totalCost)` filtered by `metadata.agent_id == slug` over the current period. That figure
drives per-agent daily/weekly/monthly budget enforcement and threshold alert emails. So the
observability layer is not just for debugging — it is the **source of truth for cost governance**.

---

## 9. What gets traced (summary)

| Layer | Mechanism | What appears in Langfuse |
|---|---|---|
| Agent chat request | `@observe(as_type="agent")` on the handler | Root trace named by the user question, tagged `agent_id`/user/session |
| Each LLM call (chat_runner) | `start_as_current_observation(as_type="generation")` + `update()` | Generation with input, output, `usage_details`, latency, iteration/retry |
| Retries | separate generation observations | MALFORMED_FUNCTION_CALL retries visible, `metadata.retry=True` |
| Thinking/cache tokens | `gemini_direct` usage capture + logger | `thinking_tokens`, `cache_read` in usage + logs |
| LangGraph nodes/tools (v2/workflow) | `OptimizedLangfuseCallbackHandler` callback | Per-node/LLM/tool observations, bloat-stripped, trace-continuous across resume |
| Access events | `[TRACE] ...` structured logs | Log-based audit of tool/server/agent access |
| Cost | Langfuse metrics API query by `agent_id` | Per-agent period cost → budget + alerts |

---

## 10. Exact locations (branch `o11y-main`, repo `mcp_collection`)

| Concern | File |
|---|---|
| Langfuse config flags + thinking budget | `mcp-gateway/config.py` |
| Client init + `@observe` + `propagate_attributes` | `mcp-gateway/server.py` |
| Per-generation tracing in the chat loop | `mcp-gateway/execution/chat_runner.py` |
| Thinking/cache token capture + logging + ThinkingConfig | `mcp-gateway/llm/gemini_direct.py` |
| LangGraph callback handler (v2/workflow branches) | `mcp-gateway/observability/langfuse_client.py` (`OptimizedLangfuseCallbackHandler`) |
| Cost via Langfuse metrics | `mcp-gateway/v2/cost_handling.py` |
