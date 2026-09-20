# 01 — System Overview

This document describes the end-to-end architecture of the Agentic Platform: the
services, how they interact, the data stores, and how each resume bullet maps to a
concrete subsystem in the code. Read this first; the other documents drill into each
component.

> Excluded by request: the "Ask AI" enterprise search feature (`self_api_registration/src/ask_ai/`
> and the search re-architecture bullet). It is not documented here.

---

## 1. The services (repositories/folders in this workspace)

The workspace is a multi-service monorepo rooted at `/home/akash/Desktop/codebase/codetool`.
The Agentic Platform is composed of these services:

| Folder | Service | Role | Language / Framework |
|---|---|---|---|
| `self_api_registration/src/catalog` | **Catalog Service** | Control plane. The registry/system-of-record for tools, agents, MCP servers, prompts, contexts, SOPs, workflows, guardrails, data transformers, versions, and durable-execution checkpoint **metadata**. FastAPI. | Python, FastAPI, MongoDB, Postgres, Redis |
| `mcp_collection/mcp-gateway` | **MCP Gateway** | Data plane / runtime. Resolves specs from Catalog on demand and builds MCP tool servers, LangGraph agent graphs, SOP graphs, and workflow graphs in memory, executes backend HTTP calls, and hosts the durable LangGraph workflow runtime (pause/resume). Starlette ASGI + FastMCP. | Python, Starlette, FastMCP, LangChain, LangGraph |
| `mcp_collection/*_MCP` | **MCP Server collection** | Standalone FastMCP servers exposing Delhivery internal APIs as MCP tools (EXPRESS, FMS, HRMS, WMS, UCID, FAAS, JARVIS, etc.). | Python, FastMCP |
| `mcp_collection/async-agent-orchestrator`, `mcp_collection/agent-hive` | **Agent orchestration** | Multi-agent orchestration building blocks. | Python, LangGraph |
| `dd-automated-NSL-update/event_scheduler` | **Event Scheduler** | Durable, time-based scheduler (Postgres + Kafka) that delivers delayed workflow *resume* callbacks. Underpins pause/resume timers. | Python, Flask, SQLAlchemy, Postgres, Kafka |
| `mcp-token-generation` | **Token service** | Mints short-lived gateway client-credential tokens; used by the scheduler to refresh auth on long-paused resumes. | Python |
| `mcp-ui` | **MCP UI** | Next.js frontend: the drag-and-drop workflow builder, agent/tool/MCP-server management console. | Next.js / TypeScript |
| `self_api_registration/src/gateway` | **Registry loader** | Loader/config bridge for registry data. | Python |

The resume line "Delivered MCP servers ... coding 100+ tools" maps to `mcp_collection/*_MCP`.
"Catalog Service API registry, expanding ecosystem to 600+ tools across 11+ business domains"
maps to the Catalog Service. "MCP Gateway (LangChain) dynamically translating OpenAPI specs
into executable tools" maps to `mcp_collection/mcp-gateway`. "Multi-Agent Orchestration
(LangGraph)" and "Workflow Automation Platform ... durable, resumable execution" map to the
Catalog workflow authoring plane + Gateway workflow runtime + Event Scheduler.

---

## 2. Control plane vs data plane (the central architectural decision)

The platform cleanly separates **authoring/registry (control plane)** from
**execution (data plane)**:

- **Catalog Service (control plane)** stores *definitions*: OpenAPI tool specs, agent
  configs, MCP-server tool bindings, SOP documents, workflow DSL graphs, and — for
  durable execution — the *checkpoint metadata* (pointers, not bytes). It never executes
  a workflow or calls a backend business API itself. It also compiles SOP documents into a
  frozen executable graph document.
- **MCP Gateway (data plane)** *resolves* those definitions at request time and *executes*
  them: it builds MCP tool servers, LangGraph agents, and LangGraph workflow `StateGraph`s
  in memory, invokes backend HTTP APIs, runs guardrails, and hosts the LangGraph
  checkpointer/runtime for pause/resume.

Why this split: it lets the registry be a durable, cache-backed system of record shared by
the UI, chat, and MCP clients, while the gateway remains a stateless-per-request executor
that scales horizontally. Snapshot *bytes* live in S3; the gateway owns the LangGraph
runtime; Catalog owns only durable metadata. This is why a repo-wide search for LangGraph
runtime primitives (`interrupt()`, `Command(resume=...)`, `Checkpointer`) finds them in
`mcp-gateway`, never in `catalog`.

---

## 3. Data stores

- **MongoDB / DocumentDB** — primary Catalog store. Collections include `registry_resources`
  (tools and other resources), `registry_versions` (`workflow_versions` alias for canvas
  workflow versions), `registry_sops` (SOP + canvas-workflow parent docs, tagged by
  `engine`), `agents`, `agent_links`, `mcp_servers`, `mcp_server_tools`, `contexts`,
  `prompts`, `graphs` (compiled SOP graphs), `guardrails`, `data_transformers`,
  `workflow_checkpoints`, `workflow_conversations`, `workflow_conversation_turns`, and eval
  collections.
- **PostgreSQL** — Catalog SOP repositories (`sop_repository`, `sop_draft_repository`,
  `sop_prefix_routing_repository`) and the Event Scheduler's `scheduled_events` table.
- **Redis** — write-through cache the Gateway reads for resolved specs / MCP server tool
  lists / active workflow versions. Catalog's own reads always hit MongoDB; Redis is written
  on mutations only (see `services/cache_invalidation.py`, `services/mcp_server.py`).
- **S3** — serialized LangGraph checkpoint snapshot bytes + pending-writes blobs (pause/resume),
  and presigned upload/download for spec/file uploads.
- **Kafka** — RAG-pipeline resource events emitted by Catalog on tool/MCP-server publish
  (`services/kafka_producer.py`), and the Event Scheduler's `dd-scheduled-events-dispatch`
  dispatch topic for delayed resume delivery.

---

## 4. End-to-end request flows

### 4.1 Tool call via an MCP server (chat/IDE client → backend API)
1. MCP client connects to `{gateway}/{owner}/c/{slug}/mcp` (endpoint format built in
   `catalog/services/mcp_server.py::_build_endpoint`).
2. Gateway looks up the MCP server's enabled tool resource IDs (Redis key `mcp_server_{slug}`,
   populated write-through by Catalog `_sync_redis_cache`; falls back to Mongo via
   `get_tools_by_slug`).
3. Gateway resolves each tool's OpenAPI spec from Catalog, flattens it into an LLM/Pydantic
   tool schema (`mcp-gateway/schema/`), and executes the backend HTTP call
   (`mcp-gateway/clients/api_executor.py`).

### 4.2 Agent chat
1. Client hits `POST /{teamname}/custom-agent/{slug}/chat` (or `/v2/chat`) on the gateway.
2. Gateway fetches the full agent config from Catalog `AgentService.get_agent_detail_by_slug`
   (system prompt, model, temperature, linked tools/MCP-servers/contexts/prompts + default
   prompts).
3. Gateway builds a LangGraph agent graph (`mcp-gateway/v2/`, `llm/agent.py`) and runs the
   orchestration loop with guardrails, routing, and child agents.

### 4.3 Workflow run (drag-and-drop DAG → deterministic pipeline)
1. Author builds a visual DAG in the UI; Catalog stores it as a flat DSL (`nodes[]`, `edges[]`)
   in a `workflow_versions` document (draft → published → live stages).
2. Client calls `POST /{team}/workflows/{workflow_id}/run` on the gateway.
3. Gateway fetches the resolved live-version spec from Catalog, compiles the DSL into a
   LangGraph `StateGraph` (`mcp-gateway/workflow/converter/`), and `ainvoke`s it.
4. If a pause node fires, the run suspends durably (checkpoint → S3 bytes + Catalog metadata),
   schedules a delayed resume via the Event Scheduler, and returns `status: "paused"`.
5. When the scheduled time arrives, the Event Scheduler → Kafka → consumer calls the gateway's
   `/resume`, which restores state and continues.

See `04-workflows.md` and `05-pause-resume.md` for the exhaustive treatment.

---

## 5. Document map

- `02-tool-registry.md` — Catalog tool registry: resource/version model, `Tool` entity,
  enricher (domain classification, fingerprinting, PII), MCP-server tool bindings, resolution,
  caching, RAG events.
- `03-agents.md` — Agent framework: agent entity, links, connection endpoints, gateway
  orchestration (v1/v2), Orion/Indent freight automation, child/sub-agents.
- `04-workflows.md` — Workflow automation platform: canvas DSL node/edge schema, authoring
  lifecycle (versions/stages), validator, DSL→LangGraph compilation, node types, routing,
  parallel/loop/iteration, SOP compiler pipeline, graph contract.
- `05-pause-resume.md` — Durable resumable execution: `WorkflowCheckpointSaver`, S3 snapshot
  layout, Catalog checkpoint metadata, Event Scheduler delivery, resume handler, conversation
  memory, and the 40 pause/resume test scenarios.
- `06-mcp-gateway.md` — Gateway internals: modules, routes, auth, caching, guardrails, LLM
  routing, OpenAPI→tool conversion, hibernation.
- `07-mcp-servers.md` — The FastMCP server collection: servers, domains, auth model, tool count.
- `08-supporting-modules.md` — RBAC/OPA, guardrail registry, data transformers, SOP subsystem,
  versioning, cache invalidation, observability.
- `11-v2-agent-orchestration.md` — v2 `StateGraph` nodes, rule vs prompt routing, child
  recursion, LLM loop, bulk, cost budgets.
- `12-observability-langfuse.md` — Langfuse SDK + callback handler, thinking tokens, cost SoR.
