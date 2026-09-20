# 06 — MCP Gateway

Resume mapping: *"Architected MCP Gateway (LangChain) dynamically translating OpenAPI specs
into executable tools for UI and chat, handling 72,000+ daily requests."*

The gateway (`mcp_collection/mcp-gateway/`) is the data-plane runtime. Instead of hand-writing
an MCP server per backend API, it resolves tool/agent/SOP/workflow specs on demand from the
Catalog Service, builds the corresponding MCP tool servers or LangGraph graphs **in memory**,
executes the underlying HTTP calls, and returns MCP/JSON responses. Stack: Python 3.11+,
Starlette ASGI + `fastmcp`, `httpx`, Redis, `pydantic`, `langchain-core`/`langgraph`,
`google-genai`, New Relic.

---

## 1. Execution modes (side by side)

- **Tool proxying** — resolve an OpenAPI-derived tool spec and execute the backend HTTP call.
- **Agent chat (v1 & v2)** — LangGraph orchestration with hooks, routing, guardrails, cost
  tracking (see `03-agents.md`).
- **SOP graphs** — LangGraph pipelines from compiled SOP documents.
- **Workflow graphs** — the canvas/SOP workflow DSL runtime (see `04-workflows.md`,
  `05-pause-resume.md`).
- **Hibernation** — long-running agent runs persist state and resume via a scheduler webhook.

---

## 2. Module map (from `mcp-gateway/README.md` + tree)

| Module | Responsibility |
|---|---|
| `server.py` | Starlette ASGI app. Middleware chain: CORS → vanity-host rewrite → UMS auth. On startup connects Redis and constructs shared clients, LangGraph builders, and the workflow/SOP handlers used by routes. |
| `config.py` | Settings via `os.getenv` + `python-dotenv`. |
| `auth/` | UMS bearer-token validation middleware (`ums_middleware.py`), OS1 client-credentials header injection (`os1.py`), header helpers, secrets. |
| `cache/` | Redis client + typed cache for resolved specs/resources (`redis_client.py`, `redis_cache.py`). Degrades gracefully if Redis is unreachable. |
| `clients/` | HTTP clients: `catalog_client.py` (spec/agent/workflow resolve), `guardrail_client.py`, `middleware_client.py`, `session_client.py`, `api_executor.py` (backend HTTP proxy), `transformer_executor.py`/`transformer_cache.py`. |
| `core/` | Legacy spec-resolution / tool-factory path still used by some handlers (`spec_resolver.py`, `tool_factory.py`, `param_builder.py`, `os1_auth.py`, `vanity_host_middleware.py`). |
| `resolution/` | On-demand resolution + routing: `spec_resolver.py`, `agent_resolver.py`, `code_resolver.py`, `sop_resolver.py`, `openapi_converter.py`, `prefix_router.py`. |
| `schema/` | Flattens OpenAPI into LLM-function-calling / Pydantic tool schemas: `schema_simplifier.py`, `pydantic_builder.py`, `param_builder.py`, `code_tool_builder.py`. |
| `execution/` | Builds MCP tool servers from resolved specs (`mcp_builder.py`) and runs the v1 chat loop (`chat_runner.py`), executes tool calls (`tool_executor.py`). |
| `llm/` | LLM client abstraction + routing: Gemini direct/LangChain, Bedrock, Bifrost gateway (`client.py`, `agent.py`, `routing.py`, `registry.py`, `gemini_*`, `bedrock_client.py`, `bifrost_base.py`). |
| `guardrails/` | Pluggable input/output guardrail chain (`chain.py`, `registry.py`, grounding/output checks, `middleware_client.py`). |
| `v2/` | v2 LangGraph agent orchestration engine (`engine/`, `nodes/`, `handler.py`, `graph_builder.py`, `state.py`, `cost_handling.py`, `media_resolver.py`, `email_notifier.py`). |
| `sop/` | LangGraph pipeline for SOP-driven chat (`graph_builder.py`, `handler.py`, `state.py`, `nodes/`, `sop_runtime/`, `graph_contract/`). |
| `workflow/` | Canvas/SOP workflow DSL runtime + pause/resume (see `04`/`05`). |
| `hibernation/` | Suspend/resume long-running agent executions (`manager.py`, `interceptor.py`, `reconstruct.py`, `resume_handler.py`, `resume_state.py`, `scheduler_client.py`). |
| `middleware/` | Vanity-host rewriting for custom-domain requests. |
| `models/` | Pydantic models for tool specs and API responses. |
| `storage/` | S3 client for presigned upload/download + checkpoint bytes. |
| `observability/` | Langfuse tracing (`langfuse_client.py`). |
| `handlers/` | Request handlers: resources, versions, custom MCP servers, agent chat, upload, completions, analytics, workflow analytics. |

---

## 3. OpenAPI → executable tool (the core resume claim)

The "dynamically translating OpenAPI specs into executable tools" pipeline:
1. **Resolve** — `resolution/spec_resolver.py` fetches a tool's OpenAPI spec from Catalog
   (`clients/catalog_client.py`), cached in Redis (`cache/`) with `CACHE_TTL_RESOURCE` /
   `CACHE_TTL_VERSION` TTLs.
2. **Convert / flatten** — `resolution/openapi_converter.py` + `schema/schema_simplifier.py` +
   `schema/pydantic_builder.py` flatten the (possibly deeply nested, `$ref`-laden) OpenAPI
   schema into a flat LLM-function-calling / Pydantic parameter schema a model can call.
3. **Build tool** — `core/tool_factory.py` / `execution/mcp_builder.py` construct the callable
   MCP tool (name, description, params) and register it on an in-memory FastMCP server for the
   requested MCP-server slug.
4. **Execute** — on invocation, `core/param_builder.py` builds the request from the mapped
   params, auth is attached (OS1 client-credentials via `auth/os1.py` for outbound, or the
   caller's forwarded headers), and `clients/api_executor.py` performs the backend HTTP call.
5. **Transform** — optional response transformation via `clients/transformer_executor.py`
   (backed by the catalog's data-transformer registry; see `08-supporting-modules.md`).

This is what serves **72,000+ daily requests** across UI and chat surfaces — every tool call,
agent turn, and workflow tool node ultimately flows through this resolve→convert→execute path.

---

## 4. Auth, routing, and safety

- **UMS auth** (`auth/ums_middleware.py`) — validates the inbound bearer token against
  `UMS_VALIDATE_URL`. `AUTH_DISABLED` / `AUTH_BYPASS_MCP_SLUGS` allow selective bypass.
- **OS1 client-credentials** (`auth/os1.py`, `core/os1_auth.py`) — mints/attaches outbound
  auth for backend calls (`OS1_AUTH_URL`, `OS1_CLIENT_ID/SECRET`, `OS1_TENANT_URL`).
- **Vanity host** (`middleware/vanity_host.py`, `core/vanity_host_middleware.py`) — rewrites
  custom-domain requests to the right team/slug using `VANITY_HOST_MAP`.
- **Guardrails** (`guardrails/`) — a pluggable input/output chain (grounding checks, secret/URL
  scanning) gated by `GUARDRAILS_ENABLED` / `GROUNDING_ENABLED` / `MIDDLEWARE_GUARDRAILS_URL`.
- **Prefix routing** (`resolution/prefix_router.py`) — `POST /chat` dispatches to an SOP graph
  when the message prefix matches a registered SOP prefix route, else falls back to the chat
  middleware.

---

## 5. Key routes

- `POST /{teamname}/custom-agent/{slug}/chat` and `.../v2/chat` — v1/v2 agent chat.
- `POST /{teamname}/workflows/{workflow_slug}/run` and `.../versions/{version_id}/run` —
  workflow run (live / specific version).
- `POST /{teamname}/workflows/{workflow_slug}/resume` — workflow resume callback.
- `POST /chat` — smart router (SOP graph vs chat middleware).
- `POST /v2/resume` — hibernation resume webhook.
- `{owner}/c/{slug}/mcp` — custom MCP server endpoint (streamable-http).
- Resource / version / MCP-server / upload / completions / analytics routes under `handlers/`.

---

## 6. LLM routing

`llm/routing.py` + `llm/registry.py` abstract the model provider. Supported: Gemini (direct
via `google-genai` and LangChain), Bedrock (`bedrock_client.py`), and the **Bifrost LLM gateway**
(`USE_BIFROST`, `BIFROST_BASE_URL`, `BIFROST_API_KEY`) for centralized routing. Agents/LLM nodes
carry an optional `llm_gateway_virtual_key` used for routing/attribution.

---

## 7. Related docs (in-repo)

- `11-v2-agent-orchestration.md` (this vault) / `mcp-gateway/docs/v2-orchestration-flow.md` — v2 agent pipeline.
- `12-observability-langfuse.md` (this vault) — Langfuse tracing + cost metrics.
- `mcp-gateway/docs/mongodb-indexes-agent-v2.md` — required Mongo indexes for v2 agents.
- `mcp-gateway/docs/hook-sandbox-security.md` — sandboxing for user-supplied hook code.
- `mcp-gateway/docs/staging-config.md` — full env var reference.
- `mcp-gateway/workflow/docs/loop-node-architecture.md` — loop lowering.
- `mcp-gateway/.kiro/specs/` — design/requirements/task specs.
