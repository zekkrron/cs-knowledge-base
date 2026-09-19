# 08 — Supporting Modules

Core modules that support the tool/agent/workflow subsystems in the Catalog Service. Paths are
under `self_api_registration/src/catalog/` unless noted.

---

## 1. RBAC / access control — `catalog/rbac/` + `self_api_registration/opa/`

Authorization is enforced with a JWT + OPA (Open Policy Agent) model:
- `rbac/jwt_validator.py` — validates inbound JWTs.
- `rbac/action_mapper.py` — maps an API route/verb to a policy action.
- `rbac/namespace_resolver.py` — resolves the namespace/tenant a request targets.
- `rbac/opa_client.py` — calls OPA to authorize `(subject, action, resource, namespace)`.
- `rbac/ums_client.py` — talks to UMS (user management) for identity/role data.
- `rbac/models.py` — RBAC data models.
- `self_api_registration/opa/` — the OPA policy bundle; `auth-sidecar-opa-setup.md`,
  `RBAC.txt` at the service root document the sidecar setup and role matrix.

This provides namespace-scoped, policy-driven access control over the 600+ tool registry
(who can register/publish/link/run which resources in which namespace).

---

## 2. Guardrail registry — `catalog/services/guardrail*.py` + gateway `guardrails/`

Guardrails are registered, versioned resources (like tools), then evaluated at runtime.
- Catalog side: `services/guardrail.py` (`GuardrailRegistryService`),
  `services/guardrail_payload_builder.py` (`GuardrailPayloadBuilder`),
  `services/guardrail_evaluation_client.py` (`EvaluationServiceClient` → an external evaluation
  service at `GUARDRAIL_EVALUATION_SERVICE_URL`). Storage: `storage/guardrails.py`,
  `storage/guardrail_versions.py` (`MongoGuardrailRepository`, `MongoGuardrailVersionRepository`).
  DI wired in `api/deps.py::get_guardrail_service`.
- Gateway side: `mcp-gateway/guardrails/` runs the input/output guardrail chain
  (`chain.py`, `registry.py`, grounding + output scanners) during agent/workflow execution.
- Workflows can embed a `guardrail` node (parsed as `GuardrailNode`) that checks an input
  variable against a registered guardrail.
- Design refs at service root: `guardrail_registry_api.txt`, `guardrail_registry_design.txt`,
  `guardrail_registry_schema.txt`.

---

## 3. Data transformers — `catalog/services/data_transformer.py` + `transformer_executor.py`

A **data transformer** is a registered, versioned piece of transformation logic applied to a
tool's response before it reaches the caller/LLM (e.g. to trim, reshape, or redact a verbose
backend response).
- Catalog: `services/data_transformer.py` (`DataTransformerService`),
  `services/transformer_executor.py`, `services/tool_response_transformer.py`
  (`ToolResponseTransformerService`). Storage: `storage/data_transformers.py`,
  `storage/data_transformer_versions.py`, `storage/tool_response_transformers.py`.
- Validation: `validation/transformer_mapping.py`, `validation/code_security.py` (transformer
  code is security-scanned before registration).
- Execution client (gateway): `mcp-gateway/clients/transformer_executor.py` +
  `transformer_cache.py`; a separate executor runs the transformer code safely.
- Standalone executor service: `self_api_registration/data_transformer_executor/`.
- DI: `api/deps.py::get_data_transformer_service`, `get_tool_response_transformer_service`.

This directly supports token optimization — transformers shrink noisy responses so downstream
LLM calls stay within budget.

---

## 4. SOP subsystem — `catalog/services/sop*.py`, `sop_generator/`, `engine/sop_compiler/`

Covered in depth in `04-workflows.md` §8. Summary of the moving parts:
- **Generation** (`sop_generator/`) — build an SOP from the tool universe: domain/capability/
  topology classifiers, hierarchical retriever, tool selector/mapper, indexer.
- **Authoring services** (`services/sop.py`, `sop_v2.py`, `sop_draft.py`, `sop_upload.py`,
  `sop_v2_upload.py`, `sop_parser.py`, `sop_validator.py`, `sop_qna.py`, `sop_tools.py`,
  `sop_dsi_adapter.py`, `sop_observability.py`) — upload, draft, parse, validate, QnA.
- **Compilation** (`engine/sop_compiler/`, `services/graph_compiler.py`) — LLM-assisted
  compile of an SOP into the executable graph document (the graph contract).
- **Storage** — Postgres repos (`database/postgres/sop_repository.py`, `sop_draft_repository.py`,
  `sop_prefix_routing_repository.py`) + Mongo `sops_v2`, `sop_versions`, `graphs`.
- **Routing** — `storage/sop_prefix_routing.py` + gateway `resolution/prefix_router.py` route a
  `/chat` message to the right SOP graph by prefix.

---

## 5. Versioning & caching

- **Versioning** — `services/versioning.py` + `storage/versions.py` / `*_versions.py` implement
  the generic draft→published→(live) version machinery reused by tools, workflows, agents,
  guardrails, data transformers, and SOPs. `utils/diff.py` computes version diffs.
- **Cache invalidation** — `services/cache_invalidation.py` (`invalidate_resource_cache`,
  `refresh_upstream_cache`) keeps the Gateway's Redis keys consistent on publish/rollback.
  Catalog reads never touch Redis (control-plane authority); Redis is a write-through cache for
  the Gateway only.
- **Cache repo** — `storage/cache.py` (`ICacheRepository`), Redis client under `database/redis/`.

---

## 6. Eventing & observability

- **Kafka RAG events** — `services/kafka_producer.py` emits `tool` / `mcp_server` resource
  events on publish/mutation (payloads built by `services/rag_extractors.py`), feeding the
  downstream search index.
- **Observability** — `services/sop_observability.py`, `services/cubeapm_client.py`
  (CubeAPM), New Relic (`newrelic.ini`), and gateway Langfuse tracing
  (`mcp-gateway/observability/langfuse_client.py`). Traces stitch across pause/resume via the
  recovered `trace_id` (see `05-pause-resume.md`).

---

## 7. API app wiring — `catalog/api/`

- `api/app.py` — FastAPI app; mounts route routers (v1/v2, internal, workflow-checkpoint,
  workflow-conversation). Checkpoint/conversation routers are on the normal auth path under
  `/api/v1`.
- `api/deps.py` — dependency-injection singletons (`@lru_cache`) for every repo/service/handler
  (tools, agents, MCP servers, workflows, checkpoints, conversations, guardrails, data
  transformers, SOPs, evals, datasets, S3).
- `api/routes/`, `api/v1/`, `api/v2/`, `api/schemas/`, `api/middleware/`, `api/response.py`
  (`success_response`), `api/error_codes.yaml`, `api/responses.yaml`.
- `core/` — domain primitives: `entity.py`, `enums.py` (`NodeType`, `VersionStage`),
  `exceptions.py` (`ResourceNotFoundError`, `ValidationError`, `ConflictError`,
  `VersionNotFoundError`, `InvalidStatusTransitionError`).

---

## 8. Cross-service contracts to keep in sync

- `catalog/engine/graph_contract/graph_schema.py` ↔ `mcp-gateway/sop/graph_contract/graph_schema.py`
  — the compiled-graph wire contract (must stay byte-identical; see `04-workflows.md` §9).
- The `__sop_gen_uuid4__` marker — recognized in both the catalog compiler
  (`engine/sop_compiler/compile_sop.py`) and the gateway executor
  (`mcp-gateway/sop/nodes/run_graph.py`) to expand a required infra header to a fresh uuid4 at
  call time.
- Catalog checkpoint/conversation API shapes ↔ gateway `checkpoint/checkpoint_client.py` /
  `conversation/conversation_client.py`.
