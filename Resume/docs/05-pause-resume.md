# 05 — Pause / Resume Architecture (Durable, Resumable Execution)

Resume mapping: the *"durable, resumable execution"* clause of the Workflow Automation Platform
bullet. This is the most intricate subsystem: a workflow can suspend mid-run (waiting on a timer
or an external event), survive pod restarts for up to ~6 days, and later resume exactly where it
left off with full state and trace continuity.

---

## 1. The central architectural framing

Responsibility is split across four planes:

| Plane | Owns | Where |
|---|---|---|
| **LangGraph runtime** | `interrupt()`, `Command(resume=...)`, the `Checkpointer`, thread_id-driven restore | `mcp-gateway/workflow/` |
| **Snapshot bytes** | Serialized graph state + pending-writes blobs | **S3** (via `mcp-gateway/workflow/checkpoint/saver.py`) |
| **Checkpoint metadata** | One doc per checkpoint: pointers (`s3_key`), status, TTL, trace context | **Catalog** Mongo `workflow_checkpoints` |
| **Timed delivery** | Fires the delayed resume callback at the right time | **Event Scheduler** (Postgres + Kafka) |

Key decision: **Catalog is the durable metadata store, NOT the execution runtime.** Catalog
stores only the `s3_key`/`pending_writes_s3_key` pointers — never the snapshot bytes. The
gateway owns the LangGraph checkpointer (`WorkflowCheckpointSaver`). S3 holds the bytes. This is
verifiable: a search for `interrupt(`, `Command(`, `Checkpointer` in `self_api_registration/`
finds no runtime code, only the metadata layer.

---

## 2. What a "pause" is at the DSL level

A `pause-resume` node (`catalog/models/workflow_spec.py`, validated by
`workflow_validator._validate_pause_resume_node`, parsed by
`mcp-gateway/workflow/converter/parser.py`) has a `sub_type`:

- **`timer`** — resume after a delay. Delay source:
  - `resume_duration_type="constant"` → `resume_after_seconds` (positive int).
  - `resume_duration_type="variable"` → `resume_after_value_selector` (a state path, in
    minutes) resolved at pause time.
- **`event`** — wait for an external event; **no scheduler is involved**, the run stays paused
  until an external event resumes it. Requires `output_variables` or a `json_schema`.

At runtime the node calls LangGraph's `interrupt()`, which suspends the graph at that
super-step. `mcp-gateway/workflow/nodes/pause_resume_node.py` implements the node;
`OutputVariable` (in that module) types the event payload.

---

## 3. The durable checkpointer — `mcp-gateway/workflow/checkpoint/saver.py`

`WorkflowCheckpointSaver(BaseCheckpointSaver)` is a custom LangGraph checkpointer. Async-only
(sync `put`/`get_tuple`/`list` raise `NotImplementedError` to avoid event-loop reentrancy).
Constructed with `checkpoint_client` (Catalog HTTP client), `s3_client`, and `config`
(`CHECKPOINT_S3_BUCKET`, `RETENTION_PERIOD` in epoch µs). Serializer: `JsonPlusSerializer`.

### 3.1 Persist-only-on-interrupt (the core optimization)
LangGraph calls `aput` on **every super-step**, but we only want to persist the *pause*
checkpoint, not every intermediate step. So:

- `aput(config, checkpoint, metadata, new_versions)` — **buffers only**. It serializes the
  checkpoint (`_dumps` → `(type_tag, payload)`), merges run context (`trace_id`, `session_id`,
  `version_id`, `workflow_slug`) into the stored metadata, and stores it in an in-memory
  `_snapshot_buffer` keyed by `(thread_id, checkpoint_ns)`. One entry per thread; each
  super-step overwrites it. Returns the config with the new `checkpoint_id`.
- `aput_writes(config, writes, task_id, ...)` — persistence trigger. It **returns immediately
  unless one of the writes targets the `__interrupt__` channel** (`INTERRUPT_CHANNEL`). On an
  interrupt write it flushes the buffered snapshot:
  1. Build S3 keys (see §3.2).
  2. Serialize the interrupt pending writes into an entries blob (base64 payloads, keyed by
     `(task_id, idx)` for deterministic replay).
  3. **S3 first**: `put_object(snapshot.bin, payload)` + `put_object(writes.json, blob)`.
  4. Then `catalog.upsert_checkpoint(..., status="paused", expires_at=now_us+retention)`.
  5. On catalog failure: **compensating delete** of both S3 objects, then re-raise (so no
     dangling snapshot without metadata).
  6. `finally`: drop the buffer entry (bounds memory to one per active thread).

This "buffer in aput, flush in aput_writes on `__interrupt__`" pattern means intermediate
super-step checkpoints never hit S3/Catalog — only genuine pauses do.

### 3.2 S3 key layout
`CHECKPOINT_S3_PREFIX/{workflow_slug}/{date}/{run_id=thread_id}/{checkpoint_id}/` contains
`snapshot.bin` (state bytes) and `writes.json` (pending-writes blob). Each checkpoint gets its
own folder so its snapshot + writes sit together. `_build_key` sanitizes segments; the date
folder comes from `checkpoint_date` (one value per invoke, threaded in by the handlers). Reads
never rebuild the key — they follow the stored `s3_key`, so it stays stable across a pause.

### 3.3 Read / restore
- `aget_tuple(config)` — reads the checkpoint doc from Catalog
  (`get_checkpoint(thread_id, ns, checkpoint_id)`; latest when id is `None`). Returns `None`
  when the doc is absent, has no `s3_key`, is expired (`expires_at < now_us`), or its S3 object
  is missing. Otherwise loads the snapshot (`_loads(type_tag, payload)`), decodes pending
  writes (`_decode_pending_writes` — ordered by `(task_id, idx)`), and returns a
  `CheckpointTuple` with `parent_config` chained via `parent_checkpoint_id`.
- `alist(...)` — minimal; yields the latest tuple only (the pause/resume path uses
  `aget_tuple`, not history traversal).

### 3.4 Lifecycle helpers
- `mark_completed(thread_id)` — flips all of a thread's checkpoints to `completed` in Catalog
  (S3 objects retained for debugging, reclaimed by the S3 lifecycle policy) and drops any
  lingering buffer entry.
- `get_status(thread_id)` — returns the latest doc's `status` (used for duplicate-resume
  detection).
- `refresh_state_auth_token(config, auth_token)` — **long-pause token refresh**: rewrites the
  stored snapshot's `system.auth_token` and any `system.client_headers` Authorization header to
  a fresh bearer before resume. Done by rewriting the S3 object (not `Command(update=...)`,
  which would collide with the pause node echoing `{**state}`). Tools resolve auth from
  `client_headers` first, so both must be patched.

---

## 4. Catalog checkpoint metadata (control plane)

### 4.1 Service — `catalog/services/workflow_checkpoint.py` (`WorkflowCheckpointService`)
Thin layer over `IWorkflowCheckpointRepository`. Converts `expires_at` between epoch
microseconds (wire) and BSON datetime (storage, for the TTL index) via `_us_to_datetime` /
`_datetime_to_us`; `_serialize` strips `_id` and converts datetimes back to µs on read.
```python
upsert_checkpoint(*, thread_id, checkpoint_ns, checkpoint_id, parent_checkpoint_id,
                  type_tag, s3_key, pending_writes_s3_key, metadata, status, expires_at)
get_checkpoint(thread_id, checkpoint_ns, checkpoint_id)   # exact id, else get_latest
mark_completed(thread_id) -> List[str]                    # returns s3_keys of the thread
```

### 4.2 Repository — `catalog/storage/workflow_checkpoints.py` + Mongo impl
`IWorkflowCheckpointRepository`: `upsert(key, document)`, `get(key)`,
`get_latest(thread_id, checkpoint_ns)`, `mark_completed(thread_id) -> List[str]`.
`MongoWorkflowCheckpointRepository` (`COLLECTION = "workflow_checkpoints"`):
- Composite `_id` = `f"{thread_id}::{checkpoint_ns}::{checkpoint_id}"`.
- `upsert` — `update_one({_id}, {$set:{...,updated_at}, $setOnInsert:{created_at}}, upsert=True)`.
- `get_latest` — filters `{thread_id, checkpoint_ns, s3_key:{$exists:True}}`,
  `.sort("checkpoint_id", -1).limit(1)` — **stub docs with no `s3_key` are excluded** (relevant
  to the writes-first ordering case).
- `mark_completed` — collects `s3_key`s then `update_many({thread_id}, {$set:{status:"completed",
  updated_at}})`.
- **TTL**: `expires_at` stored as BSON datetime; a TTL index (created out-of-band) auto-deletes
  expired checkpoints — the ~6-day retention horizon.

### 4.3 API — `catalog/api/routes/internal_workflow_checkpoint.py` (prefix `/api/v1`, normal auth)
- `PUT /workflow-checkpoints/{thread_id}/{checkpoint_ns}/{checkpoint_id}` — upsert metadata
  (body `UpsertCheckpointRequest`: `parent_checkpoint_id?`, `type_tag`, `s3_key`,
  `pending_writes_s3_key`, `metadata={}`, `status="paused"`, `expires_at`).
- `GET /workflow-checkpoints/{thread_id}/{checkpoint_ns}?checkpoint_id=` — specific or latest.
- `POST /workflow-checkpoints/{thread_id}/complete` — mark all completed, returns `{s3_keys}`.
An empty `checkpoint_ns` is transported as `_root_` (`NS_ROOT`/`_decode_ns`) to avoid a `//`
path. Handler `api/v1/handlers/workflow_checkpoint.py` wraps the service; DI singletons in
`api/deps.py` (`get_workflow_checkpoint_repo/service/handler`).

---

## 5. Scheduling the resume (timer pauses) — `mcp-gateway/workflow/scheduler_client.py`

When a timer pause fires, the gateway's run handler calls `schedule_workflow_resume(...)`:
- Computes `scheduled_at_us = now + bounded_delay`. `_bounded_delay_seconds` defaults to
  `WORKFLOW_DEFAULT_RESUME_DELAY_SECONDS` (3600), floors at 0, and clamps to
  `MAX_PAUSE_DURATION` (retention ceiling).
- POSTs to `{EVENT_SCHEDULER_URL}/api/schedule-event` (Bearer auth) with a body carrying:
  `scheduled_at`, `callback = {base_url, path=/{team}/workflows/{slug}/resume, method=POST}`,
  `config.headers` (the Authorization to replay), `payload = {thread_id}`, and
  `metadata = {origin: "gateway-workflow-pause-resume", workflow_slug}`.
- Retries up to `MAX_RETRIES=3` with exponential backoff (1s,2s,4s). HTTP 401 is terminal
  (`reason:"auth_rejected"`). Never raises — returns a success dict (`event_id`) or a failure
  dict so the caller can mark the run failed. Supports a `mock` mode.

The `metadata.origin` marker and `workflow_slug` are what let the scheduler later mint a fresh
gateway token for the resume.

---

## 6. The Event Scheduler — `dd-automated-NSL-update/event_scheduler/`

A standalone, durable, time-based scheduler (design in `event_scheduler/APPROACH.md`). Chosen
over APScheduler/Celery/Kafka-delayed-topics because it gives ACID guarantees + crash recovery +
safe concurrent pollers using only Postgres + Kafka (already operated).

### 6.1 Model — `ScheduledEvent` (in central `models.py`, Postgres `scheduled_events`)
Columns include `event_id` (UUID, unique, the idempotency key), `status`
(`pending→processing→success|failed`), `scheduled_at` (epoch µs), `callback_url`, `payload`
(JSON), `meta_data` (JSON), `attempts`, `max_attempts`, `locked_at`, timestamps. Partial
indexes on due-pending and processing rows.

### 6.2 API — `POST /api/schedule-event` (in `app.py`)
Validates required fields + future `scheduled_at`, inserts a `pending` row, returns `event_id`.
Also `GET /api/scheduled-events`, `GET/DELETE /api/scheduled-events/{event_id}`.

### 6.3 Poller — `event_scheduler/poller.py` (`EventSchedulerPoller`), standalone process
Main loop each cycle:
1. `_recover_stale_events()` — rows stuck in `processing` past `STALE_THRESHOLD`: reset to
   `pending` if `attempts < MAX_ATTEMPTS`, else **dead-letter to `failed`**.
2. `_fetch_due_batch()` — the crux: `SELECT id ... WHERE status='pending' AND scheduled_at<=now
   ORDER BY scheduled_at LIMIT BATCH_SIZE FOR UPDATE SKIP LOCKED`, then atomically flips them to
   `processing` (increments `attempts`, sets `locked_at`). `SKIP LOCKED` guarantees no
   double-dispatch across overlapping cycles or multiple poller replicas.
3. `_dispatch_batch(events, producer)` — for each event builds a Kafka message
   (`event_id`, `data`, `config={headers, host, api, method}`). **Gateway-origin token
   refresh**: if `meta_data.origin == "gateway-workflow-pause-resume"`, it resolves the
   workflow's client_id from `GATEWAY_CLIENT_ID_MAP[workflow_slug]`, fetches a fresh token
   (`token_service._fetch_gateway_token`, bounded timeout), and overwrites the Authorization
   header (`_replace_auth_header`). No mapping or failed token → leave in `processing` for
   stale-recovery retry (never dispatch a stale token). Successful produce → mark `success`.
4. Elapsed-time-aware sleep (`max(0, POLL_INTERVAL - elapsed)`), so effective cadence stays
   ~`POLL_INTERVAL` even under load.

### 6.4 Producer — `event_scheduler/producer.py`
`EventSchedulerProducer.dispatch_event` produces to Kafka topic `dd-scheduled-events-dispatch`
with `key=event_id` (partition affinity + downstream dedup), `flush(timeout=10)`. An external
consumer reads the topic and calls the callback URL — i.e. the gateway `/resume` endpoint.

---

## 7. Resuming a run — `mcp-gateway/workflow/resume_handler.py`

Endpoint: `POST /{teamname}/workflows/{workflow_slug}/resume` (`handle_workflow_resume`), called
by the Kafka consumer after the scheduled delay. `RESUME_CONSTANT = "__resume__"` — resume is a
pure trigger; no external input is accepted for timer pauses. Injected deps mirror the run
handler (`init_resume_handler`: checkpoint_saver, catalog_client, model_factory, spec_resolver,
code_resolver, graph_builder).

Flow (auth-first ordering):
1. **Authenticate** — require a Bearer token before any snapshot access; 401 otherwise.
2. Parse body; require a non-empty `thread_id`.
3. **Duplicate-resume guard** — `get_status(thread_id)`; if `completed`, return
   `{status:"already_resumed"}` (no duplicate run). Handles Kafka redelivery idempotency.
4. **Load snapshot** — `aget_tuple({configurable:{thread_id, auth_token}})`. `None` +
   no status → 404 (not found); `None` + status present → 410 (expired). Read/deserialize
   failure → 500 (snapshot left unchanged).
5. **Recover run context** from snapshot metadata: `version_id`, `session_id`, `trace_id`.
   Missing `version_id` → 422 (cannot recompile). Rebuild the Langfuse handler from `trace_id`
   so resume telemetry stitches onto the same trace.
6. **Recompile** — fetch the exact workflow version (`/api/v1/workflows/{slug}/versions/
   {version_id}/resolve`), `convert_dsl(spec, node_config, langgraph_config,
   checkpointer=_checkpoint_saver)` — identical `node_config` to the run handler, so the graph
   recompiles bit-for-bit.
7. **Refresh auth in snapshot** — `refresh_state_auth_token(...)` so tool calls use a fresh
   token after a long pause.
8. **Resume** — `await compiled_graph.ainvoke(Command(resume=RESUME_CONSTANT), config=invoke_config)`
   with the **same thread_id** (LangGraph reads the persisted checkpoint) and re-threaded run
   context (session_id, trace_id, version_id, workflow_slug, fresh checkpoint_date) so a
   re-pause keeps continuity.
9. **Outcome**:
   - Node failure on resume is **terminal** → 200 `status:"failed"` + `mark_completed` (so the
     consumer commits instead of hammering `/resume` on a 5xx).
   - Recompile/fetch failure *before* invoke → 500 (transient; consumer retries within the
     scheduler horizon).
   - `"__interrupt__" in final_state` → **re-paused**. If the active pause is `event` sub_type,
     return `paused` without scheduling. If `timer`, resolve the (re-)paused node's own delay
     (`_resume_delay_for_active_pause`; unresolvable variable delay → terminal fail +
     mark_completed) and `schedule_workflow_resume` again.
   - Otherwise **completed** → `mark_completed`, `_extract_outputs`, return `status:"succeeded"`.

Event-driven resumes (external event, not a timer) are handled by
`mcp-gateway/workflow/event_resume_handler.py`.

---

## 8. End-to-end pause→resume timeline

1. `POST /{team}/workflows/{slug}/run` → gateway compiles the graph with the checkpointer,
   injects `thread_id = run_id` into `configurable`, `ainvoke`s.
2. Graph hits a `pause-resume` node → LangGraph `interrupt()` → `aput` buffered the snapshot,
   `aput_writes` sees the `__interrupt__` write → S3 (snapshot.bin + writes.json) then Catalog
   `PUT /workflow-checkpoints/...` with `status="paused"`, `expires_at = now + retention`.
3. Gateway run handler detects `"__interrupt__"`, and for a timer pause calls
   `schedule_workflow_resume` → Event Scheduler `POST /api/schedule-event` (pending row).
   Returns `status:"paused"` to the caller.
4. Time passes (up to hours). Poller `_fetch_due_batch` (SKIP LOCKED) claims the due event,
   refreshes the gateway token, produces to Kafka. Consumer calls
   `POST /{team}/workflows/{slug}/resume` with `{thread_id}`.
5. `handle_workflow_resume` authenticates, loads the snapshot (`aget_tuple` → Catalog metadata →
   S3 bytes), recompiles for `version_id`, refreshes auth, `ainvoke(Command(resume=...))`.
6. Graph continues from the pause node. On completion → `mark_completed` (all thread
   checkpoints → `completed`), outputs returned. On re-pause → a fresh checkpoint doc + another
   schedule.
7. Cross-run memory: the completed turn is recorded via the conversation layer (§9), keyed by
   `memory_session_id`.

---

## 9. Conversation memory (cross-run context tied to resumption)

`memory_session_id` (== `conversation_id`, a UUID4) threads context across runs and resumes.
Catalog side:
- `catalog/services/workflow_conversation.py` (`WorkflowConversationService`) —
  `record_turn(*, conversation_id, workflow_id, workflow_slug, version_id, user_id, team, input,
  output, status, started_at, completed_at)`: validates `conversation_id` is UUID4
  (`_is_valid_uuid4`), parses ISO-8601 timestamps, writes one turn doc, bumps the conversation
  summary. `get_turns(conversation_id, mode, since, limit)` supports `mode="time"` (since a
  timestamp) or `mode="turns"` (last N; default 1 when mode omitted).
- Repos: `MongoWorkflowConversationTurnRepository` (`workflow_conversation_turns`, one doc per
  completed run, index `(conversation_id, started_at)`; `list_since`, `list_last_n`),
  `MongoWorkflowConversationRepository` (`workflow_conversations` summary, unique
  `conversation_id`, `(user_id, updated_at desc)`, `$inc turn_count`).
- API — `catalog/api/routes/internal_workflow_conversation.py` (prefix `/api/v1`):
  `POST /workflow-conversations/{conversation_id}/turns` (201),
  `GET /workflow-conversations/{conversation_id}/turns?mode&since&limit&user_id`,
  `GET /workflow-conversations?user_id&skip&limit`. Handler renames the wire field
  `conversation_id → memory_session_id` to match the gateway's caller-facing name.

Gateway side: `mcp-gateway/workflow/conversation/` (`conversation_client.py`, `history.py`)
fetches recent turns to inject as `conversation_history` into the initial state (run handler
§7 of `04-workflows.md`) and records the completed turn as a background task.

---

## 10. Verified behavior — the 40 test scenarios (`pause_resume_test_cases.csv`)

The pause/resume behavior is pinned by 40 documented scenarios. Highlights by category:
- **Happy path (TC-01–03)** — single pause → vendor responds → success branch; resume fires at
  the node's configured delay (not the 1h default); non-pause workflows regress cleanly.
- **Retry pattern (TC-04–09)** — pause1→check→pause2→pause3 escalation; success on retry 0/1/2;
  exhaust-all → give-up branch; per-pause independent durations (2h/6h/7h); max real-world pause
  7h still under the 6-day retention.
- **Multi-pause (TC-10–12)** — two sequential pauses complete; state survives across pauses;
  `trace_id/session_id/version_id` preserved across re-pauses (no 422 on the 2nd resume).
- **If-else + fan-in (TC-13–17)** — single-branch execution, merge runs once, pause inside a
  branch, branch decision uses post-resume state.
- **Persistence (TC-18–21)** — no dropped pending writes; keep-all checkpoints (no pruning);
  crash recovery mid-pause (restore from persisted checkpoint after pod restart); writes-first
  ordering creates the doc, stubs excluded from `get_latest`.
- **Scheduler (TC-22–25)** — poller dispatches due events; stale in-flight recovery; 6-day
  give-up horizon dead-letters with alert; unmapped `workflow_slug` re-queued (not dropped).
- **Token resilience (TC-26–30)** — fresh token minted per dispatch; token service down →
  re-queue (not dead-letter) on short outage; hanging service short-circuits per cycle (bounded
  cycle time); recovery drains the backlog; high RPM + token down stays bounded.
- **Load (TC-31–33)** — steady ~10k/day throughput; synchronized burst eventually drains; two
  poller replicas — `SKIP LOCKED` prevents double-dispatch.
- **Idempotency (TC-34)** — duplicate Kafka delivery advances the workflow once
  (idempotent on `event_id`/`thread_id`; the `get_status=="completed"` guard in §7).
- **Negative (TC-35–38)** — checkpoint store down / S3 unavailable / auth failure / unknown
  thread_id all fail gracefully with no silent state loss or phantom runs.
- **Observability (TC-39–40)** — trace shows pause + resume stitched onto one trace; start node
  present on every run.

---

## 11. Exact locations

| Concern | File |
|---|---|
| Pause node (DSL model / validation / parse) | `catalog/models/workflow_spec.py`, `catalog/services/workflow_validator.py::_validate_pause_resume_node`, `mcp-gateway/workflow/converter/parser.py` |
| Pause node runtime | `mcp-gateway/workflow/nodes/pause_resume_node.py` |
| Durable checkpointer | `mcp-gateway/workflow/checkpoint/saver.py`, `checkpoint/checkpoint_client.py` |
| Checkpoint metadata (service/repo/api) | `catalog/services/workflow_checkpoint.py`, `catalog/storage/workflow_checkpoints.py`, `catalog/database/mongodb/workflow_checkpoints.py`, `catalog/api/routes/internal_workflow_checkpoint.py`, `catalog/api/schemas/workflow_checkpoint.py`, `catalog/api/v1/handlers/workflow_checkpoint.py` |
| Resume scheduling client | `mcp-gateway/workflow/scheduler_client.py` |
| Event Scheduler | `dd-automated-NSL-update/event_scheduler/poller.py`, `producer.py`, `token_service.py`, `APPROACH.md`; `app.py` (API); `models.py` (`ScheduledEvent`) |
| Resume handlers | `mcp-gateway/workflow/resume_handler.py`, `event_resume_handler.py` |
| Conversation memory | `catalog/services/workflow_conversation.py`, `catalog/storage/workflow_conversation*.py`, `catalog/api/routes/internal_workflow_conversation.py`, `mcp-gateway/workflow/conversation/` |
| Public run/paused contract | `workflow-run-api-client.md` |
| Verified scenarios | `pause_resume_test_cases.csv` |
