# 03 — Agents

Resume mapping: *"Spearheaded a hierarchical AI Agent framework (LangGraph) leveraging
event-driven multi-step orchestration and child agents to automate 20+ business workflows"*
and *"Orion & Freight Automation: Automated freight procurement via an Indent Creation Agent
... Integrated a Subagent for enrichment ..."*.

Agents have two halves:
1. **Authoring/registry (Catalog Service)** — the agent definition, its linked resources, and
   its published endpoint. Files under `self_api_registration/src/catalog/`.
2. **Runtime (MCP Gateway)** — the LangGraph orchestration engine that runs an agent's
   chat/turn, calls tools, routes to child agents, applies guardrails, and tracks cost. Files
   under `mcp_collection/mcp-gateway/`.

---

## 1. Agent definition & registry (Catalog) — `services/agent.py` (`AgentService`)

### 1.1 Agent document
`create_agent` / `import_agent` create an `agents` document:
```python
{
  "name", "slug",                 # slug = kebab(name)+10 random chars
  "type": "internal" | "external",# internal = built here; external = imported (ext_agent_link)
  "description", "owner",
  "status": "inactive",           # activated after configuration
  "system_prompt": None,          # set via update_agent
  "model": None, "temperature": None, "max_tokens": None,
  "ext_agent_link": None,         # for imported/external agents
  "llm_gateway_virtual_key": ...  # optional LLM routing key
  "updated_at", "updated_by",
}
```
- **Endpoint** (`_build_agent_link`): `{MCP_GATEWAY_ORIGIN}/{owner}/custom-agent/{slug}`;
  chat URL = `endpoint + "/chat"`. Returned by `get_connection_config`.
- **Defaults**: `DEFAULT_TEMPERATURE = 0.7`, `DEFAULT_MAX_TOKENS = 4096`.
- **Validation** on `update_agent`: name uniqueness, `0.0 ≤ temperature ≤ 2.0`,
  `max_tokens ≥ 1`.

### 1.2 Agent ↔ resource links (`agent_links`, `IAgentLinkRepository`)
An agent is composed by *linking* four resource types to it. This is the heart of the
"composed agent" model:
- `tool` — a registry resource (validated via `resource_repo`)
- `mcp_server` — an MCP server bundle (validated via `mcp_server_repo`)
- `context` — a context document (validated via `context_repo`)
- `prompt` — a prompt document (validated via `prompt_repo`)

`_validate_resource(resource_type, resource_id)` dispatches to the correct repo and raises if
the resource does not exist. Link management:
- `add_link` — dedupe check (`get_by_agent_and_resource`), then create `{agent_id,
  resource_type, resource_id, linked_at, linked_by}`.
- `replace_links_by_type` — diff-based bulk replace (computes added/removed/unchanged,
  deletes removed, recreates the rest) returning counts.
- `remove_link`, `delete_agent` (cascades `link_repo.delete_by_agent`).
- `list_agents` includes per-agent grouped link counts via
  `link_repo.count_by_agent_grouped` (`prompts_count`, `contexts_count`, `tools_count`,
  `mcp_servers_count`).

### 1.3 Read for UI vs read for runtime
- `get_agent(agent_id)` / `get_agent_by_slug(slug)` — returns the agent plus its links grouped
  by type, with each link enriched (resource name/domain/type). For the management console.
- `get_agent_detail_by_slug(slug)` — **the gateway-facing resolve**. Returns the full runtime
  config with *enriched content*: each linked prompt/context includes its `content` and
  `tokens`; tools/mcp_servers include ids+names. It also **appends default prompts** (by name,
  from `DEFAULT_AGENT_PROMPTS` in `catalog/config.py`) that aren't already linked, and carries
  `llm_gateway_virtual_key`. This single call gives the gateway everything to build the agent.

### 1.4 Agent versions & agent-as-tool
- `storage/agent_versions.py`, `storage/agents_v2.py` — versioned agent configs (v2).
- `services/agent_v2.py` — v2 agent service.
- `services/agent_tool_generator.py` — generates a tool interface for an agent so **an agent
  can be exposed as a callable tool** to other agents/workflows. This is a key enabler of the
  hierarchical/child-agent design: a parent agent (or a workflow's Agent node) can invoke a
  child agent as if it were a tool.

---

## 2. Agent runtime (MCP Gateway)

The gateway builds and runs agents with LangGraph. Relevant modules
(`mcp_collection/mcp-gateway/`):

| Module | Role |
|---|---|
| `handlers/agent_handler.py` | HTTP entry for agent chat (`POST /{team}/custom-agent/{slug}/chat`). |
| `resolution/agent_resolver.py` | Resolves the agent config from Catalog (`get_agent_detail_by_slug`). |
| `llm/agent.py`, `llm/client.py`, `llm/routing.py`, `llm/registry.py` | LLM client abstraction and routing (Gemini direct/LangChain, Bedrock, Bifrost gateway). |
| `v2/` (`engine/`, `nodes/`, `handler.py`, `graph_builder.py`, `state.py`, `cost_handling.py`, `email_notifier.py`, `media_resolver.py`) | **v2 LangGraph agent orchestration engine**: hooks, routing, child agents, cost tracking. |
| `execution/chat_runner.py`, `execution/mcp_builder.py`, `execution/tool_executor.py` | v1 chat loop + builds MCP tool servers from resolved specs + executes tool calls. |
| `guardrails/` | Input/output guardrail chain (grounding, secret/URL scanning). |
| `hibernation/` | Suspend/resume long-running agent executions (separate from workflow pause/resume). |
| `observability/langfuse_client.py` | LLM tracing. |

### 2.1 v2 orchestration flow
The canonical description in this vault is `11-v2-agent-orchestration.md` (from
`mcp-gateway/docs/v2-orchestration-flow.md` + `v2/` on branch `loop-node`). Required
Mongo indexes in `mcp-gateway/docs/mongodb-indexes-agent-v2.md`. The v2 engine is a LangGraph
graph with:
- **Hooks** — pre/post extension points (user-supplied hook code is sandboxed; see
  `mcp-gateway/docs/hook-sandbox-security.md`).
- **Routing** — decides between tools, child agents, and direct responses ("multi-step
  orchestration").
- **Child agents** — an agent can dispatch to another agent (hierarchical). Backed by
  `agent_tool_generator.py` on the catalog side (agent-as-tool) and the v2 engine's routing on
  the runtime side.
- **Cost tracking** (`v2/cost_handling.py`) — per-run token/cost accounting; enables the
  per-indent cost figures below.

### 2.2 Standalone orchestration building blocks
`mcp_collection/async-agent-orchestrator/` and `mcp_collection/agent-hive/` contain the
multi-agent orchestration primitives (async orchestration + an agent "hive"). These are the
reusable substrate for the hierarchical framework.

---

## 3. Orion / Freight Automation — the Indent Creation Agent

Resume: *"Automated freight procurement via an Indent Creation Agent, processing 1,000+ daily
indents and driving down costs from ₹28 to ₹4 per indent. Integrated a Subagent for enrichment
to resolve missing data, ensuring clean records for physical truck assignments."* Later cut to
**₹1.5/indent** once migrated onto the workflow platform (see `04-workflows.md`).

Architecture pattern (as realized by the agent + workflow platform):
1. **Indent Creation Agent** — an internal agent (Catalog agent doc, `owner = orion` team)
   linked to the freight/FMS tools (Fleet Management domain; see FMS tools in
   `07-mcp-servers.md`). It drives the freight-procurement workflow: create indents for
   trucking demand.
2. **Enrichment Subagent (child agent)** — invoked when an indent has missing/dirty data. It
   resolves the gaps (looks up vendor/route/contract data via tools) so the final record is
   clean enough for a *physical truck assignment*. This is the "child agent / subagent" pattern
   from §2.1 — a parent agent delegating a bounded sub-task to a specialized child.
3. **Cost reduction** — automating what was a manual/expensive process drove per-indent cost
   from ₹28 → ₹4 (agent) → ₹1.5 (workflow platform). The cost accounting is visible via
   `v2/cost_handling.py` and workflow `cost_config` (`models/workflow.py` carries a
   `cost_config` field; `v2/cost_handling.py` and `handlers/workflow_analytics_handler.py`
   handle cost analytics).

The Orion run endpoint format is exactly the workflow run URL: e.g.
`https://gateway.delhivery.com/orion/workflows/track-shipment-xxxx/run`
(see `workflow-run-api-client.md`) — Orion is a `team`, its automations run as agents and, after
migration, as workflows.

---

## 4. "20+ business workflows" and event-driven orchestration

- The agent framework automates 20+ business workflows; these were later **migrated onto the
  workflow automation platform** (resume: "Migrated all 20+ business workflows onto it"). So an
  "agent workflow" and a "platform workflow" describe the same business automations at two
  points in the evolution — first as LangGraph agents, then as compiled DAG workflows.
- **Event-driven**: automations are triggered by events and can suspend waiting for external
  events. On the workflow side this is the `pause-resume` node with `sub_type: "event"` (waits
  for an external event, no timer) — see `05-pause-resume.md`. On the agent side, hibernation
  (`mcp-gateway/hibernation/`) suspends a long-running run and resumes it on a scheduler
  webhook.

---

## 5. Exact locations

| Concern | File |
|---|---|
| Agent CRUD, links, endpoints | `catalog/services/agent.py` |
| Agent runtime-resolve (enriched config for gateway) | `catalog/services/agent.py::get_agent_detail_by_slug` |
| Agent v2 service | `catalog/services/agent_v2.py` |
| Agent-as-tool generation (child agents) | `catalog/services/agent_tool_generator.py` |
| Agent storage | `catalog/storage/agents.py`, `agents_v2.py`, `agent_versions.py`, `agent_links.py` |
| Gateway agent chat entry | `mcp-gateway/handlers/agent_handler.py` |
| Gateway agent resolve | `mcp-gateway/resolution/agent_resolver.py` |
| v2 orchestration engine | `mcp-gateway/v2/` |
| v1 chat loop / tool exec | `mcp-gateway/execution/` |
| LLM abstraction & routing | `mcp-gateway/llm/` |
| Hibernation (agent suspend/resume) | `mcp-gateway/hibernation/` |
| Orchestration primitives | `mcp_collection/async-agent-orchestrator/`, `mcp_collection/agent-hive/` |
| v2 flow / indexes / hook security docs | `mcp-gateway/docs/v2-orchestration-flow.md`, `mongodb-indexes-agent-v2.md`, `hook-sandbox-security.md` |
