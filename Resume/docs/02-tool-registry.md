# 02 — Tool Registries

Resume mapping: *"Shipped Catalog Service API registry, expanding ecosystem to 600+ tools
across 11+ business domains."* This document covers every registry in the Catalog Service:
the generic resource/version registry, the `Tool` entity registry, the MCP-server tool
binding registry, and how tools are resolved and cached.

All paths are under `self_api_registration/src/catalog/` unless noted.

---

## 1. Two layers of "registry"

There are two distinct registry concepts in the codebase:

1. **Entity-type registry** (`entities/registry.py`) — an in-process registry mapping an
   entity `kind` (e.g. `"Tool"`) to its `IValidator` and enricher. This is the extensibility
   seam: new entity kinds register a validator + enricher pair.
2. **Resource registry** (MongoDB `registry_resources` + `registry_versions`) — the durable
   catalog of actual tools/resources and their versions. This is the "600+ tools" registry.

### 1.1 Entity-type registry — `entities/registry.py`
```python
class EntityRegistry:
    def __init__(self): self._validators = {}; self._enrichers = {}; self._register_defaults()
    def _register_defaults(self): self.register("Tool", ToolValidator(), ToolEnricher())
    def register(self, kind, validator, enricher): ...
    def get_validator(self, kind) -> Optional[IValidator]: ...
    def get_enricher(self, kind): ...
entity_registry = EntityRegistry()   # module-level singleton
```
Currently registers `Tool` → (`ToolValidator`, `ToolEnricher`). Entity packages exist for
`agent`, `context`, `prompt`, `sop`, `tool` under `entities/` (each an `__init__.py`; `tool`
is the fully fleshed-out one with `model.py`, `validator.py`, `enricher.py`).

---

## 2. The `Tool` entity

### 2.1 Model — `entities/tool/model.py`
```python
class ToolMetadata(BaseModel):
    title, description, version, backend_url: Optional[str]
    endpoints: List[str]   # OpenAPI paths
    methods:   List[str]   # HTTP methods

class Tool(BaseModel):
    kind: str = "Tool"
    name: str
    namespace: str
    metadata: ToolMetadata
```
A tool is fundamentally an **OpenAPI operation** registered into the catalog. `namespace`
scopes it (e.g. `prod-logistics`), `backend_url` comes from `servers[0].url`.

### 2.2 Enricher — `entities/tool/enricher.py` (`ToolEnricher`)
`enrich(spec)` extracts metadata from a raw OpenAPI spec. This is where a lot of the
registry's intelligence lives:

- **Info extraction**: `title`, `description`, `version`, `summary` (operation summary,
  falling back to `info.title`). Handles both standard OpenAPI (`info`) and a custom
  wrapped shape (`spec.api_info`).
- **`backend_url`** = `servers[0].url`.
- **`endpoints`** = `list(paths.keys())`; **`methods`** = uppercased set of HTTP methods
  across all path items.
- **Domain classification (`_extract_domain_keyword`)** — keyword-based, no LLM. Maps
  description+title text to exactly one of the `VALID_DOMAINS`. This is the "11+ business
  domains" set:
  ```
  Express, Freight, Fulfillment, Sorting, Serviceability, Tracking, Billing,
  Customer Support, Employee, Address, Client, Hyperlocal, Fleet Management, Platform
  ```
  Each domain has a keyword list (e.g. Express: `last mile, delivery, tat, ndr, pod, rto,
  edd, attempt`; Customer Support: `ticket, escalation, complaint, csat, support, helpdesk,
  jarvis, narad`). Falls back to `Platform`. Stored as an **array** (`metadata["domain"]`)
  so MongoDB `$in` queries work natively.
- **`tags`** — union of spec-level and operation-level tags, plus `api` and `openapi`.
- **`has_pii`** — `True` if any parameter or component-schema property carries `x-pii: true`
  (checked across standard `paths→operations→parameters`, custom `spec.parameters`, and
  `components.schemas.*.properties`). This flag drives downstream PII handling.
- **`spec_fingerprint`** (`_compute_fingerprint`) — SHA-256 over the sorted set of
  `(base_url, METHOD, path)` tuples. Two specs with the same server + endpoints + methods
  produce the same fingerprint → **duplicate detection**.
- **`token_count`** — `catalog/utils/tokens.py::count_tokens(spec)`; used for token-budget
  reasoning across the registry.

### 2.3 Validator — `entities/tool/validator.py` (`ToolValidator`)
Structural/security validation of the incoming spec before registration (paired with the
enricher in `entity_registry`). Validation base classes live in `validation/` (`base.py`,
`result.py`, `code_security.py`, `transformer_mapping.py`).

---

## 3. The resource/version registry (durable, MongoDB)

The catalog stores every tool (and other resources) as a **resource** document with one or
more **versions**. This generic resource+version machinery backs tools, and the same
version machinery is reused for workflows, agents, guardrails, etc.

### 3.1 Storage repositories — `storage/`
- `storage/resources.py` (`IResourceRepository`) — CRUD + `list`/`count` over
  `registry_resources`. Reads by id, name; updates `status`, `active`, `active_version_id`,
  `latest_version_id`.
- `storage/versions.py` (`IVersionRepository`) — versions: `get_latest_draft`,
  `get_by_number`, `set_active`, `update_version`, `list_by_resource`, `get_active`.
- `storage/mcp_servers.py`, `storage/mcp_server_tools.py` — MCP-server registry + the
  server↔tool bindings.
- Other resource-family repos: `agents.py`, `agent_links.py`, `agent_versions.py`,
  `contexts.py`, `context_links.py`, `prompts.py`, `guardrails.py`, `guardrail_versions.py`,
  `data_transformers.py`, `sops.py`/`sops_v2.py`, `graphs.py`, `workflow_versions.py`.

### 3.2 Read path — `services/catalog.py` (`CatalogService`)
Deliberately simple and DB-authoritative:
```python
class CatalogService:                       # "Direct MongoDB reads. Cache invalidation on writes."
    get_resource(resource_id, kind)         # resource_repo.get_by_id
    list_resources(filters, skip, limit)    # count + list
    get_version_list(resource_id)           # version_repo.list_by_resource
    get_active_version(resource_id)         # version_repo.get_active
    invalidate_resource(resource_id, kind)  # invalidate upstream Redis keys
```
Key decision: **Catalog's own reads never read Redis.** Redis is exclusively a write-through
cache for the Gateway. This avoids stale-read bugs in the control plane.

### 3.3 Publish/lifecycle & RAG events — `services/workflow.py` (`WorkflowService`, status transitions)
`WorkflowService.update_status` (used for status transitions on resources, including tools)
implements the `draft → published` transition, sets the active version, refreshes upstream
Redis keys (`refresh_upstream_cache`), and — for tool resources — emits a RAG-pipeline event:
```python
async def _emit_tool_rag_event(resource, is_deleted=False):
    payload = await extract_tool_payload(resource, version_repo)     # services/rag_extractors.py
    await kafka_producer.emit_event(resource_type="tool", payload=payload,
                                    is_deleted=is_deleted, key=str(resource["_id"]))
```
`_republish_version` supports rollback (make an older version active again). Both publish and
republish re-emit the RAG event so the downstream search index stays in sync.

---

## 4. MCP-server tool binding registry — `services/mcp_server.py`

An **MCP server** is a named, publishable bundle of tools. This is how the 600+ registered
tools get grouped and exposed to clients.

### 4.1 Model & endpoint
- Server doc: `{name, slug, description, owner, status, updated_at, updated_by}`; slug is
  `generate_slug(name)` = kebab(name) + 10 random chars.
- Endpoint format (`_build_endpoint`): `{MCP_GATEWAY_ORIGIN}/{owner}/c/{slug}/mcp`.
- Connection config (`get_connection_config`) returns a ready-to-paste `mcp.json`:
  ```json
  {"mcpServers": {"<slug>": {"transport": "streamable-http",
    "url": "<endpoint>", "disabled": false, "headers": {"Authorization": "Bearer <TOKEN>"}}}}
  ```

### 4.2 Server↔tool bindings (`McpServerService` + `IMcpServerToolRepository`)
- `create_server(name, ..., tool_ids)` — validates every `tool_id` resolves to a real
  resource (`_validate_resource_ids`), then creates `{server_id, resource_id, enabled, ...}`
  link rows.
- `replace_tools`, `toggle_tool` (enable/disable a single binding), `remove_tool` — full
  binding management with add/remove/unchanged diffing.
- `get_server` enriches each binding with the resource's name/summary/domain/version/status.

### 4.3 Write-through Redis cache (`_sync_redis_cache`)
On every mutation, the enabled tool resource IDs are pushed to Redis key `mcp_server_{slug}`
(only when the server is `active`; deleted otherwise). The Gateway reads this key to know
which tools to expose.
```python
async def _sync_redis_cache(server):
    key = f"mcp_server_{server['slug']}"
    if server["status"] == "active":
        enabled_ids = await tool_repo.get_enabled_resource_ids(server["_id"])
        await cache.set(key, enabled_ids) if enabled_ids else await cache.delete(key)
    else:
        await cache.delete(key)
```
`get_tools_by_slug(slug)` is the Gateway-facing read: Redis first (`source: "cache"`), Mongo
fallback that re-populates the cache (`source: "db"`), 404 if missing, validation error if the
server is not active.

### 4.4 RAG events
Every server mutation (create/update/replace tools/toggle/remove/delete) emits an
`mcp_server` RAG event via `_emit_rag_event` → `extract_mcp_server_payload` →
`kafka_producer.emit_event`, so the search index reflects server membership changes.

---

## 5. Tool resolution against the domain catalog — `services/tool_resolver.py`

This module verifies that tools referenced (e.g. by an SOP or workflow author) actually exist
in the canonical tool universe defined in `sop_generator/domains.yaml`. It is pure and
environment-independent (parses YAML directly, never imports env-gated config) and **fail-open**
(any error returns the input unchanged).

- `build_tool_index(domains_path)` → `{operationId: resource_id}` built from each capability's
  `mcp_tools` entries (`"resource_id:operation_id"`). An `operationId` that maps to two
  different `resource_id`s is stored as the sentinel `__AMBIGUOUS__` and treated as
  unregistered (never guessed).
- `resolve_section_tools(spec)` → `(cleaned_spec, dropped_tools)`. For each section tool it
  verifies the operationId is in the index, canonicalizes it to `{resource_id, operation}`,
  keeps matches, and reports drops. Fail-open on malformed input.
- `render_capability_catalog()` — renders a compact domain/capability catalog (names +
  one-line descriptions, no ids) for LLM prompt injection.

This is the deterministic guardrail that prevents hallucinated/renamed tools from entering a
compiled graph.

---

## 6. Domain taxonomy ("11+ business domains")

The domain set is defined in two coordinated places:
- `entities/tool/enricher.py::VALID_DOMAINS` — the classification target set (14 domains).
- `sop_generator/domains.yaml` + `sop_generator/domains_config.py` /
  `sop_generator/domain_classifier.py` — the canonical domain→capability→tool universe used
  by the SOP compiler and `tool_resolver`.

Each domain groups capabilities; each capability lists concrete `mcp_tools`
(`resource_id:operation_id`). This taxonomy is what lets the platform reason about tools by
business area rather than raw endpoint.

---

## 7. Auditing the registry — `audit/`

The `audit/` package (`run_audit.py`, `evaluator.py`, `fetcher.py`, `llm_judge.py`,
`report.py`, `mailer.py`, `checks/`) periodically evaluates registered tools/specs for quality
(including LLM-as-judge checks) and mails a report. This keeps a large (600+) tool registry
healthy over time.

---

## 8. Summary of exact locations

| Concern | File |
|---|---|
| Entity-type registry | `entities/registry.py` |
| Tool model | `entities/tool/model.py` |
| Tool enrichment (domain, fingerprint, PII, tokens) | `entities/tool/enricher.py` |
| Tool validation | `entities/tool/validator.py`, `validation/` |
| Resource CRUD | `storage/resources.py` |
| Version CRUD / active-version | `storage/versions.py` |
| Read service (DB-authoritative) | `services/catalog.py` |
| Publish / rollback / RAG emit | `services/workflow.py` |
| MCP-server registry + bindings + cache | `services/mcp_server.py`, `storage/mcp_servers.py`, `storage/mcp_server_tools.py` |
| Tool resolution vs domain universe | `services/tool_resolver.py` |
| Domain taxonomy | `entities/tool/enricher.py::VALID_DOMAINS`, `sop_generator/domains.yaml` |
| RAG payload extraction | `services/rag_extractors.py` |
| Kafka event emit | `services/kafka_producer.py` |
| Registry audit | `audit/` |
