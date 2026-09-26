# 04 — Workflows (Workflow Automation Platform)

Resume mapping: *"Built a drag-and-drop workflow automation tool on LangGraph that compiles
visual DAGs into deterministic multi-step pipelines with durable, resumable execution.
Migrated all 20+ business workflows onto it, serving 124,000+ requests/day, and cut Orion
indent cost to ₹1.5 per indent."*

This document covers the full workflow platform except the pause/resume mechanics, which have
their own document (`05-pause-resume.md`).

Two authoring dialects compile into an executable graph:
1. **Canvas workflows** — the drag-and-drop DAG (flat DSL of `nodes[]` + `edges[]`). Authored
   in the UI, stored/validated in Catalog, compiled to a LangGraph `StateGraph` in the Gateway.
2. **SOP workflows** — Standard Operating Procedure documents compiled (LLM-assisted) into a
   frozen graph document. Compiled in Catalog (`engine/sop_compiler`), executed in the Gateway.

Both emit graphs the Gateway runtime can run. This document details canvas first (the resume
point), then the SOP compiler and the cross-service graph contract.

---

## 1. Where the pieces live

| Concern | Location |
|---|---|
| Canvas workflow domain models | `catalog/models/workflow.py`, `catalog/models/workflow_spec.py` |
| Canvas workflow CRUD / versioning | `catalog/services/workflow_engine.py` |
| Status transitions / publish | `catalog/services/workflow.py` |
| Canvas graph validation | `catalog/services/workflow_validator.py` |
| SOP → graph compiler (Catalog) | `catalog/engine/sop_compiler/`, `catalog/services/graph_compiler.py` |
| Cross-service graph contract | `catalog/engine/graph_contract/graph_schema.py` (byte-identical to gateway copy) |
| DSL → LangGraph compilation (Gateway) | `mcp-gateway/workflow/converter/` |
| Workflow node implementations (Gateway) | `mcp-gateway/workflow/nodes/` |
| Run/execute endpoints (Gateway) | `mcp-gateway/workflow/handler.py` |
| Public run API contract | `workflow-run-api-client.md` |

---

## 2. Canvas workflow data model — `catalog/models/workflow.py` + `workflow_spec.py`

### 2.1 Parent + version documents (`models/workflow.py`)
```python
class Workflow(BaseModel):        # the parent
    id (_id), name, slug, description, team, enabled=True,
    active_version_id, cost_config, created_by, updated_by, created_at, updated_at

class WorkflowVersion(BaseModel): # one per version
    id (_id), workflow_id,
    api_version = "agent.delhivery.io/v1alpha1",
    version_number: int,
    stage: VersionStage,          # draft | published | live
    source_version_id,
    spec: GraphDefinition,        # the DAG
    execution_config: ExecutionConfig,
    created_by, updated_by, created_at, updated_at
```
Storage note (`services/workflow_engine.py` docstring): canvas workflow **parents live in
`registry_sops`** tagged `engine="canvas"` (shared with the SOP flow so both list together),
while **versions live in `workflow_versions`** because the canvas spec shape (`{nodes[],
edges[]}`) is a different dialect from the SOP flow's `sections[]`.

### 2.2 The DAG spec (`models/workflow_spec.py`)
```python
class Position(BaseModel): x: float; y: float          # canvas coordinates (drag-and-drop)

class NodeDefinition(BaseModel):                        # gateway "flat DSL" — no config wrapper
    id, type: NodeType, title, desc="", position=Position(0,0),
    error_strategy="none", timeout=60, retry_config={},
    # type-specific fields live DIRECTLY on the node:
    json_schema, subType,                               # StartNode
    cases,                                              # IfElseNode
    model, llm_gateway_virtual_key, prompt_template, context, structured_output,  # LLMNode
    variables,                                          # Output/Code
    outputs, resource_id,                               # CodeNode
    tool_resource_id, input_mapping,                    # ToolNode
    sub_type, output_variables, resume_after_seconds,   # PauseResumeNode
    resume_duration_type, resume_after_value_selector
    class Config: extra = "allow"

class EdgeDefinition(BaseModel):
    source, target, source_handle, target_handle        # handles carry if-else branch labels

class GraphDefinition(BaseModel): id, name, nodes: [NodeDefinition], edges: [EdgeDefinition]

class ExecutionConfig(BaseModel):
    max_execution_time=300, max_execution_steps=50, retry_on_failure=False
```
The **flat DSL** decision is important: every type-specific field sits at the top level of the
node (no nested `config` object). The node schema mirrors the gateway's DSL exactly so no
translation is needed between authoring and execution. `position` (x/y) is what the
drag-and-drop canvas persists; `source_handle`/`target_handle` on edges encode which branch of
an if-else a connection came from.

---

## 3. Authoring lifecycle — `catalog/services/workflow_engine.py` (`WorkflowEngineService`)

Every canvas workflow moves through a version + stage lifecycle. Stages:
`VALID_TRANSITIONS = {"draft": ["published"], "published": ["live"]}`.

### 3.1 Methods
- `create_workflow(name, description, team, user)` — conflict-checks the name, generates a
  slug (`generate_slug` = kebab(name)+`secrets.token_hex(5)`), creates the `registry_sops`
  parent with `engine="canvas"`, and an initial **draft** version #1 with an empty spec
  (`{"nodes": [], "edges": []}`) and default `execution_config`.
- `create_version(workflow_slug, source_version_id, user, replace_existing_draft=False)` —
  creates a new draft. Can copy the spec/execution_config from a published/live source version
  (rollback/branch). Guards: rejects a second draft unless `replace_existing_draft`; if
  replacing, deletes the old draft **only after** the source version validates (so a bad
  request never destroys the existing draft); computes the next version number **after**
  deletion to avoid sequence gaps.
- `update_draft(workflow_slug, version_id, updates, user)` — patches only `spec` /
  `execution_config`, only on a `draft` stage version. This is what the UI calls as the author
  edits the canvas.
- `transition_stage(workflow_slug, version_id, target_stage, user)` — validates the transition,
  **runs the full graph validator** (`is_live_promotion = (target=="live")`), and on `live`
  promotion demotes the current live version (`demote_live_to_published`) and points the parent
  `active_version_id` at the new version.
- `resolve_version` / `resolve_live` — return the full spec + execution_config (+
  `conversation_history_config`) for the gateway to compile. `resolve_live` is what the public
  run endpoint uses.
- `validate_version` / `validate_live` — run the validator without transitioning.
- `_require_canvas_workflow(id_or_slug)` — fetches the `registry_sops` parent by ObjectId or
  `endpoint_slug`, **scoped to `engine=="canvas"`** (an SOP-engine parent is treated as not
  found — this API only manages canvas workflows).
- `_build_run_url(team, slug)` → `{MCP_GATEWAY_ORIGIN}/{team}/workflows/{slug}/run`.

### 3.2 Publish semantics — `catalog/services/workflow.py`
`WorkflowService.update_status` handles the `draft → published` transition on the *latest
draft* (not the active version), sets the version active, updates the parent resource pointers
(`active_version_id`, `latest_version_id`), and calls `refresh_upstream_cache` to push the new
active version + spec into the Gateway's Redis keys. `_republish_version` re-activates an older
version (rollback). Tool-kind resources additionally emit a RAG event on publish/republish.

---

## 4. Graph validation — `catalog/services/workflow_validator.py` (`WorkflowValidator`)

`validate(spec, is_live_promotion=False) -> ValidationResult` runs authoring-time validation so
a malformed DAG never reaches the gateway. Graph-level checks:

- **Unique node IDs**; every edge `source`/`target` must reference an existing node.
- **Exactly one `start` node**; **at least one `output` node**.
- **Acyclicity** — DFS cycle detection (`_dfs_cycle` with visited + recursion-stack sets).
  A cycle is an error. (This is what makes it a DAG.)
- **Reachability** — BFS from the start node; every node must be reachable, and at least one
  output node must be reachable from start.

Per-node validation (dispatched by `type`), reading fields directly off the flat node:
- `start` (`_validate_start_node`) — requires a `json_schema` of `type: "object"` with ≥1
  property (the workflow's declared inputs).
- `llm` (`_validate_llm_node`) — requires `model` (+ `model.name`), validates
  `temperature ∈ [0,2]` and `max_token ∈ [1,50000]`; requires a `prompt_template` with a
  system message (text or prompt id); context `variable_selector` must reference an upstream
  node or a system variable.
- `if-else` (`_validate_if_else_node`) — ≥1 case, and ≥1 case with non-empty conditions (an
  empty-conditions case is the else/default branch).
- `tool` (`_validate_tool_node`) — requires `tool_resource_id` that resolves to a real catalog
  resource; every `input_mapping[*].value_selector` must reference an upstream node or a system
  variable.
- `code` (`_validate_code_node`) — requires `outputs`; variable selectors must be upstream.
- `output` (`_validate_output_node`) — ≥1 variable; non-constant selectors must be upstream.
- `pause-resume` (`_validate_pause_resume_node`) — see `05-pause-resume.md`. Validates
  `sub_type ∈ {timer,event}`; timer requires `resume_after_seconds` (constant) or a
  `resume_after_value_selector` list (variable duration); event requires `output_variables`
  or a `json_schema`.
- `loop` (`_validate_loop_node`) — validates `start_node_id`, `logical_operator ∈ {and,or}`,
  `loop_count ≥ 1` int, `on_max_reached ∈ {output,error}`, `break_conditions` list,
  `loop_variables` list; enforces loop-in-loop rejection server-side (defense in depth).

**System variables**: selectors may reference `["system", <prop>]` where prop ∈
`{request_id, workflow_slug, teamname, auth_token}` (`SYSTEM_VARIABLE_PROPERTIES`) without an
upstream producer. `_get_upstream_nodes` computes the upstream set via reverse-BFS over edges.

`ALLOWED_OUTPUT_VARIABLE_TYPES = {string, number, boolean, object, array}`.

---

## 5. DSL → LangGraph compilation (Gateway) — `mcp-gateway/workflow/converter/`

The gateway turns the stored DAG JSON into a compiled, executable LangGraph `StateGraph`. The
public entrypoint is `convert_dsl(...)` (package `workflow/converter/__init__.py`), which wires
`DSLParser` → `GraphCompiler`.

### 5.1 Parsing — `converter/parser.py` (`DSLParser`)
`parse(json_str) -> ParsedWorkflow`:
1. `_decode_json` — JSON decode (raises `DSLParseError` on malformed JSON).
2. `_validate_structure` — exactly 1 start, ≥1 output, every node `type` ∈
   `ALLOWED_NODE_TYPES`, every edge references an existing node. Container (iteration/loop)
   nodes carry their children **inline**, not at top level.
3. `_instantiate_node` — maps each JSON node to a typed dataclass: `StartNode`, `CodeNode`,
   `IfElseNode`, `ToolNode`, `LLMNode`, `OutputNode`, `AgentNode`, `GuardrailNode`,
   `IterationNode`(+`IterationStartNode`/`IterationEndNode`), `LoopNode`(+`LoopStartNode`/
   `LoopEndNode`), `PauseResumeNode`.
4. `_instantiate_edge` — `BaseEdge(source, target, source_handle="source", target_handle="target")`.

`ALLOWED_NODE_TYPES = {start, code, if-else, tool, llm, output, agent, guardrail, iteration,
iteration-start, iteration-end, loop, loop-start, loop-end, pause-resume}`.

Container parsing details:
- **Iteration** (`_instantiate_iteration_node`) — requires `iterator_selector` (list[str]),
  `start_node_id` (must exist among children), a mandatory `iteration-end` child;
  `error_handle_mode ∈ {terminated, continue_on_error, remove_abnormal_output}`;
  `parallel_nums` (default 10). Children parsed recursively with `iteration_id` stamped on each.
- **Loop** (`_instantiate_loop_node`) — parses `break_conditions` (same shape as if-else
  conditions, via `_parse_conditions`), `logical_operator`, `loop_count` (int ≥1),
  `on_max_reached`, `loop_variables`; requires a mandatory `loop-end` child; injects the
  loop-variable update spec onto each `LoopEndNode`. `_reject_nested_loop` enforces
  **loop-in-loop rejection transitively** (a loop body may contain iteration, but never
  another loop container or loop sentinels nested inside another container).

### 5.2 Compilation — `converter/compiler.py` (`GraphCompiler.compile`)
Pipeline (from the module docstring, exact steps):
1. **State schema** — `state_schema.generate_state_schema(nodes)` builds a `TypedDict`
   (`WorkflowState`) with one `Dict[str, Any]` channel per node id, plus framework channels
   `request`, `system`, `final_output`, `metadata`. Each node writes into its own namespace;
   loop-body nodes get parent channels (they're inlined); iteration children are excluded
   (they live in a child sub-graph schema).
2. **Build** `StateGraph(state_schema)`.
3. **Register nodes** — each node is wrapped by `_create_node_function`, which returns
   `_node_with_resolution(state)` = `resolve_variables(node, state)` then
   `await node.run(resolved_state, node_config)`. Timeout/retry/error-strategy is handled
   inside `BaseNode.run()`. `node_config` must carry `model_factory`, `spec_resolver`,
   `catalog_client` (+ `code_resolver`, `graph_builder`, `langgraph_config`).
4. **Detect parallel branches** (`parallel.detect_parallel_branches`) before wiring edges.
5. **Direct edges** — sequential edges wired with `graph.add_edge`, skipping edges owned by
   conditional routing, parallel wiring, or loop-container outgoing edges.
6. **Conditional edges** — for each if-else node, `build_conditional_router(node, edge_map)` is
   attached via `graph.add_conditional_edges(source, path=router, path_map=...)`. The router
   reads `state[node_id]["__branch"]` (set by the if-else node at runtime) and maps the branch
   label (edge `source_handle`) to the target node.
7. **Parallel groups** — `wire_parallel_group` wires fan-out/fan-in.
8. **Loops as native cycles** (`_wire_loop_cycle`) — a loop is lowered into a parent-graph
   cycle: the loop container id becomes a **seed** node (`build_loop_seed`), an entry guard
   (`build_loop_entry_guard`) routes body-start vs exit (0 iterations), the loop-end router
   (`build_loop_end_router`) either back-edges to loop-start or exits, and a compiler-injected
   `{loop_id}__loop_exit` assembler node fans out to the loop's successors. Body edges are
   inlined onto the parent graph (`_wire_loop_body_edges`).
9. **Pause reachability** (`_validate_pause_node_reachability`) — BFS from start; an
   unreachable pause node is a `DSLValidationError` (it could never suspend the run).
10. **Entry/finish** — `set_entry_point(start_node_id)`, `set_finish_point(output_id)` for each
    output node.
11. **Compile** — if any node (top-level **or inlined loop body**, via `_iter_all_nodes`) is a
    `pause-resume`, the graph is compiled **with the checkpointer**
    (`graph.compile(checkpointer=...)`); otherwise `graph.compile()` with no checkpointer.
    A pause node with no checkpointer raises `DSLValidationError`.
12. **Recursion limit** — loop-containing graphs raise LangGraph's `recursion_limit`
    (`_required_recursion_limit` = `25 + Σ max(1,loop_count)*(body_size+1) + 10`), applied via
    `compiled.with_config(...)`. Non-loop graphs are unchanged.

**Determinism guarantee**: the DAG is acyclic (validated in Catalog), each node writes only its
own state channel, edges/branches are wired from the static spec, and the same version_id
recompiles to the identical graph on resume (see `05-pause-resume.md`). This is what "compiles
visual DAGs into deterministic multi-step pipelines" means concretely.

---

## 6. Node types (Gateway runtime) — `mcp-gateway/workflow/nodes/`

Each node subclasses `BaseNode` (`nodes/base.py`, `NodeTypeEnum`) and implements
`run(state, node_config)`. `BaseNode.run()` centralizes timeout/retry/error-strategy. Exact fields, UI bugs, unused `execution_config`: `14-node-timeout-retry-errors.md`.

| Node | File | Behavior |
|---|---|---|
| Start | `start_node.py` | Validates inputs against `json_schema`; promotes system variables (`auth_token`, `request_id`, `workflow_slug`, `teamname`, `client_headers`, `conversation_history`, `memory_session_id`) into `state["system"]`. |
| Tool | `nodes/http_action/` + `tool_node.py` | Resolves the tool by `tool_resource_id`, maps `input_mapping` value_selectors from state, executes the backend HTTP call, writes the response into its channel. Reads auth from `system.client_headers` then `system.auth_token`. |
| LLM | `llm_node.py` | Renders `prompt_template` (Jinja over state), calls the model via the model factory, supports `structured_output`. |
| If-Else | `if_else_node.py` (+ `condition_eval.py`) | Evaluates `cases` conditions and writes `__branch` into its channel; the conditional router reads it. |
| Code | `code_node.py` | `code_resolver` → `data-transformer-executor` Lambda (not in-process). See `13-code-node-lambda-sandbox.md`. |
| Agent | `agent_node.py` | Invokes a child agent by `agent_slug` (+ optional `agent_version`) with a `content_mapping` (Jinja). This is the workflow-side child-agent hook. |
| Guardrail | (parsed as `GuardrailNode`) | Runs a guardrail check on an input variable. |
| Output | `output_node.py` | Assembles the workflow's declared output variables into `final_output`. |
| Pause-Resume | `pause_resume_node.py` | Suspends the run (LangGraph `interrupt()`); see `05-pause-resume.md`. |
| Iteration | `iteration_node.py`, `iteration_start_node.py`, `iteration_end_node.py` | Map-over-array: runs a child sub-graph per item (compiled separately, `parallel_nums` concurrency). |
| Loop | `loop_node.py`, `loop_start_node.py`, `loop_end_node.py` | Condition-based loop lowered into a native parent-graph cycle (see §5.2 step 8); `loop-node-architecture.md` documents it. |

Supporting converter modules: `variable_resolver.py` (`resolve_variables`,
`collect_declared_dependencies` — resolves `value_selector` paths from state),
`routing.py` (router builders), `parallel.py` (fan-out/fan-in), `node_wrapper.py`,
`dynamic_functions.py` (+ `DYNAMIC_FUNCTIONS_README.md`), `pretty_printer.py`, `exceptions.py`
(`DSLParseError`, `DSLValidationError`, `UnresolvedDependencyError`).

---

## 7. Run execution flow (Gateway) — `mcp-gateway/workflow/handler.py`

Endpoints:
- `POST /{teamname}/workflows/{workflow_slug}/run` (`handle_workflow_run`) — runs the **live**
  version.
- `POST /{teamname}/workflows/{workflow_slug}/versions/{version_id}/run`
  (`handle_workflow_version_run`) — runs a **specific** version (test/sandbox).

`_execute_workflow(...)` steps:
1. Extract graph spec from `workflow_data` (Catalog resolve response).
2. `_validate_workflow_via_catalog` (server-side validation) + `_validate_inputs` (local check
   of `input_variables` against the start node's `json_schema`).
3. Set up Langfuse tracing — create `trace_id`, build `OptimizedLangfuseCallbackHandler`.
4. `convert_dsl(graph_spec_str, node_config=..., langgraph_config=..., checkpointer=_checkpoint_saver)`
   — the checkpointer is passed unconditionally; the compiler only wires it in for pause
   workflows. Also sets per-handler trace node deps (`collect_trace_node_deps`).
5. Build `initial_state`:
   ```python
   {"request": {"payload": input_variables,
                "system_variables": {auth_token, request_id, workflow_slug, teamname,
                                     client_headers, conversation_history, memory_session_id}},
    "metadata": {"mapping": {node_id: title, ...}}}
   ```
   `conversation_history` is fetched (when enabled) from Catalog via the conversation client.
6. If the graph is checkpointer-backed (or the spec has a `pause-resume` node), inject
   `configurable` = `{thread_id=run_id, session_id, trace_id, version_id, auth_token,
   workflow_slug, checkpoint_date}` — these are persisted into the snapshot metadata by the
   saver so a resume can recover context.
7. `final_state = await compiled_graph.ainvoke(initial_state, config=invoke_config)`.
8. Branch on outcome:
   - **paused** (`"__interrupt__" in final_state`) → record pause, schedule delayed resume
     (timer sub_type) or skip scheduler (event sub_type), return `status:"paused"`. Full detail
     in `05-pause-resume.md`.
   - **succeeded** → `_extract_outputs(spec_dict, final_state)` from the output nodes,
     `_sum_tokens`, `sanitize_state` (redact JWTs/tokens), record the conversation turn as a
     Starlette `BackgroundTask` (off the response path), return `status:"succeeded"`.
   - **failed** (exception) → return `status:"failed"` with the error; all HTTP 200.

### 7.1 Public run API contract (`workflow-run-api-client.md`)
`POST https://gateway.delhivery.com/{team}/workflows/{workflow_id}/run`, Bearer auth. Body:
`input_variables` (dict), optional `session_id`, optional `memory_session_id` (UUID4 for
cross-run memory), optional `system_variable` (`request_id`, `user_id`). Response is always
HTTP 200 with `status ∈ {succeeded, failed, paused}`, plus `run_id`, `memory_session_id`,
`version`, `outputs`, `error`, `metadata`. Errors (400/404) cover invalid JSON, non-object
`input_variables`, invalid UUID4 `memory_session_id`, validation failure, workflow not found.

This platform serves **124,000+ requests/day** and hosts the migrated 20+ business workflows,
including Orion freight automation (indent cost cut to ₹1.5/indent).

---

## 8. SOP workflows — the compiler pipeline (`catalog/engine/sop_compiler/`)

SOP workflows are the second authoring dialect: a Standard Operating Procedure document
(`sections[]` with prose + tools) is compiled — with LLM assistance — into the same executable
graph document the gateway runs. This is orchestrated by `catalog/services/graph_compiler.py`
(async wrapper) around the sync `engine/sop_compiler/compile_sop.py` pipeline.

### 8.1 Orchestration — `catalog/services/graph_compiler.py` (`GraphCompilerService`)
- `compile_version(sop_id, version_id)` — sets `graph_status="compiling"`, loads the version
  spec, builds a `resource_id → version_id` map (`_build_resource_to_version`), then runs the
  sync compiler in a thread (`asyncio.to_thread(self._compile_sync, ...)`) to keep the event
  loop free (compilation is CPU-bound + makes LLM calls). Stores the compiled graph via
  `graph_repo.upsert(sop_id, version_id, graph)` and sets `graph_status="ready"`.
- **Never raises**: a compile failure is non-fatal — the SOP still works through the gateway's
  LLM-loop path. `graph_status` is a compile-owned field independent of the SSE
  `generation_status`, guarded so a failed compile never clobbers a concurrent `ready`.
- `_compile_sync` wires the compiler's dependencies: `DomainIndex.from_yaml_file(domains.yaml)`,
  a sync `RegistryGateway` (Mongo + `SampleStore` of real tool responses for binding
  validation), and `GeminiLLM`. Progress labels are marshalled back to the event loop via
  `run_coroutine_threadsafe` and persisted as `generation_message`.
- `get_graph(sop_id)` / `get_graph_for_version(sop_id, version_id)` — fetch the compiled graph
  (active version, with fallback to latest by recency).

### 8.2 The compile pipeline — `engine/sop_compiler/compile_sop.py::compile_sop`
Per section: **retrieve → enrich → draft → validate bindings → derive inputs**; then merge all
sections, dedup, wire, assemble + validate. Stages (each a module):
- `retrieve.py` (`retrieve_tools`) — pick candidate tools for the section from the
  `DomainIndex` (hierarchical retrieval).
- `_enrich_candidates` — attach real OpenAPI `input_params`/`response_fields` from the
  `RegistryGateway`; candidates whose operation drifted out of the spec are **dropped** (with a
  user-facing warning) rather than crashing.
- `draft.py` (`draft_graph`) — LLM drafts nodes/edges for the section using enriched tools +
  real samples + a key legend. Node ids are namespaced per section (`_namespace_draft`) to
  avoid collisions on merge.
- `binding_validator.py` (`validate_bindings`) — proves each cross-node data edge against real
  schema + sample responses; unproven bindings become `unresolved`/`breakpoints`.
- `input_extract.py` (`extract_tool_inputs`) — recovers SOP-specified per-tool inputs from the
  original markdown (the upstream DSI summarization drops them); schema-gated LLM call.
- `_apply_validated_edges_to_inputs` — **critical determinism step**: the runtime executes off
  each node's `inputs[].from`, so cross-node inputs are derived from the *validated* edges, never
  the LLM's raw draft strings.
- `_drop_invalid_enum_literals` — deterministic, no-LLM safety net dropping baked literals not
  in a param's declared enum.
- `_scrub_unedged_cross_node_inputs` — drops any cross-node input without a validated-edge
  provenance (removes hallucinations).
- `_bake_required_header_defaults` — fills a required infra header the SOP never mentions (e.g.
  a tracing `request-id`) with the `__sop_gen_uuid4__` marker the gateway expands to a fresh
  uuid4 at call time.
- Higher-order stages: `transform_extract`/`transform_emit` (compute steps),
  `coalesce_extract`/`coalesce_emit` (fan-in resolution), `classifier_extract`/`classifier_emit`,
  `decision_extract`/`decision_validator` (branching), `cross_section_binding`, `dedup`,
  `acyclicity.break_cycles`, `assemble.assemble_graph`, `_prune_stale_data_edges`.
Supporting: `sop_loader.py` (`parse_sop_spec` → `ParsedSOP`/`ParsedSection`), `domain_index.py`,
`registry_gateway.py`, `sample_store.py`, `legend.py`, `llm_client.py` (`GeminiLLM`),
`code_runner.py` (smoke-tests transform code).

### 8.3 SOP generation & routing
`catalog/sop_generator/` builds SOPs from a tool universe (domain/capability/topology
classifiers, hierarchical retriever, tool selector/mapper). SOP services (`services/sop*.py`)
handle upload, drafts, versioning, QnA, validation, and DSI adaptation. `storage/
sop_prefix_routing.py` + gateway `resolution/prefix_router.py` route a `/chat` message to the
right SOP graph by prefix.

---

## 9. The cross-service graph contract — `engine/graph_contract/graph_schema.py`

This file is the **wire contract** between Catalog (writer) and Gateway (reader). Its header
states it MUST stay byte-identical to `mcp-gateway/sop/graph_contract/graph_schema.py`. Catalog's
compiler writes this shape to Mongo `graphs`; the gateway runtime reads it.

Dataclasses:
```python
@dataclass Node:  id, type, inputs: list[dict], config: dict          # from_dict/to_dict
@dataclass Edge:  from_id, to_id, binding: dict|None, condition: str|None, validated: bool
                  # (de)serialized with wire keys "from"/"to" since from/to are py keywords
@dataclass Section: id, title, trigger, target_nodes, output_rule, output_example,
                    member_nodes, primary_output
@dataclass GraphDocument:
    schema_version, sop_id, sop_version, status, topology, sections, nodes, edges
    # helpers: node(id), section(id), incoming_edges(node_id), outgoing_edges(node_id)
```
`graph_contract/graph_validation.py` validates a compiled `GraphDocument`. This contract is why
the two dialects (canvas + SOP) converge: both ultimately produce graphs the gateway executes,
and any change to the contract must land in both repos together.

---

## 10. Determinism, durability, and scale — the resume claim decoded

- **Compiles visual DAGs** → §2 (canvas DSL: nodes/edges/positions) + §5 (`convert_dsl`).
- **Into deterministic multi-step pipelines** → §4 acyclicity/reachability validation + §5
  isolated per-node state channels + statically wired edges/branches + identical recompile on
  resume.
- **Durable, resumable execution** → §7 pause branch + the entire `05-pause-resume.md`
  (LangGraph checkpointer, S3 snapshots, Catalog metadata, Event Scheduler resume delivery).
- **Migrated all 20+ business workflows** → the same automations previously built as LangGraph
  agents (`03-agents.md`) re-expressed as canvas/SOP workflows.
- **124,000+ requests/day** → aggregate run volume across the migrated workflows on the gateway
  run endpoints (§7).
- **Cut Orion indent cost to ₹1.5/indent** → the Indent Creation workflow (freight procurement)
  running on this platform; cost analytics via `mcp-gateway/handlers/workflow_analytics_handler.py`
  and the parent `cost_config` field.
