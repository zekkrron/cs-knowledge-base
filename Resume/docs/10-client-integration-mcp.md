# 10 — Client-Integration MCP Server (INTEGRATION_MCP)

Resume mapping: *"Implemented a token-optimized MCP server tailored for Client IDE knowledge
bases, slashing Delhivery One API client integration effort from 3 weeks to 5 days."*

> Source: `mcp_collection` repo, branch **`mcp-client-graviton`** (commit `client integration
> graviton`), directory `mcp_collection/INTEGRATION_MCP/`. It is a standalone FastMCP server
> (port 8010) whose "product" is a curated Delhivery B2C/One API **knowledge base** exposed to an
> IDE coding assistant (Kiro, Claude Desktop, etc.), so a client engineer can integrate
> Delhivery shipping APIs from inside their editor instead of reading scattered external docs.

---

## 1. The problem it solves

Integrating Delhivery's B2C transportation APIs (create shipment, tracking, warehouse, pincode
serviceability, NDR, RVP QC, e-waybill, etc.) traditionally took ~3 weeks: an engineer had to
read external docs, reconcile quirks, guess retry/error semantics, and hand-write clients. This
server collapses that to ~5 days by putting the *entire, curated, integration-grade knowledge
base* directly in the IDE assistant's context — on demand and **token-optimized** (only the docs
relevant to the current task are loaded, never the whole corpus).

Two design pillars:
1. **Docs-as-knowledge-base** — every API is documented as structured Markdown (spec, auth,
   errors, quirks, retry) under `docs/`, plus shared `common/` practices and `workflows/`
   roadmaps.
2. **Smart, tiered loading** — a "Smart Resource Loader" + a 3-tier interaction model
   (Architect → Engineer → Diagnostician) that loads *only* the documents relevant to the
   current phase, keeping token usage low while remaining exhaustive where it matters.

---

## 2. Knowledge-base layout — `INTEGRATION_MCP/docs/`

The corpus is a three-tier taxonomy (matching `mcp_implementation_plan.md`'s 3-Tier
Architecture):

- **Tier 1 — Workflows (high-level roadmaps)** — `docs/workflows/`:
  `overview.md` (terminology, package lifecycle, payment modes, the 11-step integration order),
  `forward_journey.md`, `reverse_journey.md`.
- **Tier 2 — Per-API docs** — `docs/{api_name}/`. Each API folder can contain:
  - `spec.md` — the endpoint spec (raw OpenAPI + summary); **the single source of truth**.
  - `auth.md` — API-specific auth (overrides common).
  - `errors.md` — API-specific error catalog (overrides common).
  - `quirks.md` — type/format gotchas (e.g. "must be float not int"), always appended.
  - `retry.md` — what's retryable vs terminal, appended.
  APIs present: `pincode_serviceability`, `bulk_pincode`, `bulk_waybill`, `cancel_shipment`,
  `edit_shipment`, `shipment_creation`, `tracking`, `expected_tat`, `warehouse_create`,
  `warehouse_edit`, `pickup_request`, `packing_slip`, `invoice_charges`, `document_download`,
  `ewaybill_update`, `ndr_status`, `ndr_update`, `rvp_qc`.
- **Tier 3 — Common practices** — `docs/common/`: `auth.md`, `errors.md`, `config.md`,
  `request_construction.md`, `logging.md`, `checklist.md`, `labels-and-pricing.md`.
- **Behavioral programming** — `docs/north_star.md` (see §5), `docs/getting-started.md`,
  `docs/faq-troubleshooting.md`.

This structure is what "token-optimized ... knowledge base" means literally: the docs are
pre-chunked by API and concern so the loader can pull a minimal, relevant subset per request.

---

## 3. The server — `INTEGRATION_MCP/server.py` (FastMCP, v2.0.0, the current implementation)

`mcp = FastMCP("Delhivery-Integration-Copilot")`, `DOCS_DIR = <pkg>/docs`. Runs HTTP transport
on port 8010 (`mcp.run(transport="http", host, port, stateless_http=True)`). Exposes three
surfaces: a dynamic **resource**, three **prompts**, and five **tools**.

### 3.1 Dynamic resource loader
`@mcp.resource("delhivery://{category}/{topic}")` → `get_documentation(category, topic)` maps a
URI to `docs/{category}/{topic}.md` and returns its text. Directory-traversal guard: rejects any
`..` in `category`/`topic`. Missing files return a helpful "use list_available_apis" message.
This is the "Smart Resource Loader" — one URI scheme over the whole corpus.

### 3.2 Why both prompts AND tools
The comment in the file states the key compatibility decision: **most MCP clients only discover/
invoke `@mcp.tool()` functions — they do not auto-browse `@mcp.resource()` or invoke
`@mcp.prompt()`**. So the same tiered logic is exposed twice: as MCP prompts (for clients that
support them) and as tools (for clients like Kiro that only call tools). The tools are the
primary interface.

---

## 4. The 3-tier interaction model (Architect → Engineer → Diagnostician)

Each tier exists as a prompt and a mirrored tool. All of them share the **Smart Loading logic**
for cross-cutting concerns (auth/errors/retry/quirks), which is the token-optimization core.

### 4.1 Tier 1 — Architect (broad planning)
- Prompt `plan-integration(flow_type="forward_journey")` / Tool
  `get_integration_guide(flow_type)`.
- Loads: `north_star.md` + `workflows/overview.md` + `workflows/{flow_type}.md`.
- Purpose: broad "how do I integrate Delhivery?" questions — present the API suite, recommended
  order, and dependencies, then ask the user which API to build first.

### 4.2 Tier 2 — Engineer (implement a specific API)
- Prompt `integrate-api(api_name)` / Tool `get_api_documentation(api_name)`.
- **Smart Loading resolution** (the heart of the design):
  - `north_star.md` — always first (behavioral guide).
  - **Auth**: OVERRIDE — load `{api}/auth.md` **if it exists**, else `common/auth.md`.
  - **Errors**: OVERRIDE — load `{api}/errors.md` if it exists, else `common/errors.md`.
  - **Quirks**: HYBRID — append `{api}/quirks.md` if it exists.
  - **Retry**: HYBRID — append `{api}/retry.md` if it exists.
  - **Common practices**: append `common/{config,request_construction,logging,checklist}.md`.
  - **Spec**: always append `{api}/spec.md` (source of truth).
  - **Optional extras**: append `{api}/{examples,serviceability,response_samples}.md` if present.
  - Then a user-instruction message: inspect the codebase, ask clarifying questions, present a
    plan, respect existing patterns, deliver complete runnable files, then STOP.
- This override/hybrid resolution means the assistant never loads redundant generic docs when a
  more specific one exists — minimizing tokens while guaranteeing the most precise guidance wins.

### 4.3 Tier 3 — Diagnostician (troubleshoot an error)
- Prompt `diagnose-issue(api_name, error_description="")` / Tool `get_diagnostic_info(api_name)`.
- Loads: `{api}/spec.md` + auth (specific or common) + `common/errors.md` + `{api}/errors.md`
  (if present) + `{api}/quirks.md` + `{api}/retry.md` (if present), then a direct-fix
  instruction (no design discussion).

### 4.4 Discovery + escape hatch
- `list_available_apis()` — **"CALL THIS FIRST."** Enumerates API folders (excluding
  `common`/`workflows`), workflows, and common docs, and tells the assistant which tools to call.
- `get_doc(category, topic)` — load any single doc file directly (traversal-guarded).

### 4.5 Helpers
- `_safe_read(relative_path)` — safe file read with graceful "not found"/error strings.
- `_embed_doc(relative_path, uri)` — wraps a doc as an `EmbeddedResource` `PromptMessage` (used
  by the prompt tier).
- Health: `/health`, `/ping` → `{status: healthy, service: INTEGRATION_MCP, version: 2.0.0}`;
  `/.well-known/oauth-authorization-server` returns `{}` to silence client auth probes.

---

## 5. Behavioral programming — `docs/north_star.md`

`north_star.md` is loaded first in every tier; it programs the assistant's behavior rather than
describing an API. Key rules an interviewer might probe:

- **§0 Do NOT search the internet** — the assistant must treat this MCP server as the *only*
  source of truth for Delhivery docs; never fetch external URLs. Every tool/prompt reinforces
  this ("Everything you need is here").
- **§1 Identity** — acts as a professional integration consultant, not a generic coding bot.
- **§2 Two operating modes** — Consultation Mode (broad → consult before code) vs Diagnostic
  Mode (specific error → direct fix).
- **§2.1 Consultation-before-code** — inspect the user's codebase, match their language/
  framework/patterns (sync vs async, folder conventions, HTTP client), ask one grouped round of
  clarifying questions, present a structured plan, get approval, then deliver **complete runnable
  files** (never snippets/pseudocode). Do NOT auto-execute.
- **§4.4 Environment** — never hardcode base URLs; read from config/env (prod
  `https://track.delhivery.com`, staging alternative). Don't ask "staging or prod?" — code reads
  config so it works for both.
- **§4.8 Spec fidelity — every key matters** — account for **every field** in the OpenAPI spec
  (request and response); never cherry-pick; when the summary and raw YAML conflict, **trust the
  raw YAML**. (This is why `spec.md` embeds the raw spec, and why the resume's client-integration
  work emphasizes correctness.)
- **§5 Knowledge loading strategy** — load north_star → workflow overview → common → API spec,
  and never dump all docs at once (the token-optimization rule, encoded as behavior).

---

## 6. `server.py` vs `server2.py`

The branch contains two implementations:
- **`server.py` (v2.0.0, current)** — the docs-driven **Smart Resource Loader**. All knowledge
  lives in `docs/*.md`; the server is thin (dynamic resource + tiered prompts/tools that compose
  files at request time). Adding/updating an API = adding/editing Markdown, no code change. This
  is the token-optimized, maintainable design.
- **`server2.py` (~5,566 lines, prior/alternative)** — a monolithic implementation where each
  API's documentation is hardcoded inside a Python function returning a dict
  (`get_pincode_serviceability_info`, `get_edit_shipment_info`, `get_bulk_waybill_generation_info`,
  `get_cancel_shipment_info`, `get_package_tracking_info`, `get_invoice_charges_info`,
  `get_packing_slip_info`, `get_expected_tat_info`, `get_bulk_client_pincode_serviceability_info`,
  ...), plus a `make_request(...)` helper for live API calls. It documents the evolution: from
  code-embedded docs (server2) to externalized Markdown knowledge base (server) — the latter is
  what makes it scalable and token-efficient.

---

## 7. Configuration & deployment

`config.py`: `INTEGRATION_BASE_URL` (default `https://track.delhivery.com`), `TOKEN`
(`INTEGRATION_TOKEN`, `Authorization: Token <token>`), `REQUEST_TIMEOUT` (30s), and
`get_headers(token)`. Server env: `INTEGRATION_HOST` (0.0.0.0), `INTEGRATION_PORT` (8010).
Runs via `python server.py`, Docker (`INTEGRATION_MCP/Dockerfile`), or `docker-compose up
integration-mcp`. `run_server.sh` is the local launcher. Auth token can be per-request or from
env (same flexible model as the other MCP servers in `07-mcp-servers.md`).

Design/authoring aids on the branch: `mcp_collection/mcp_implementation_plan.md` (the 3-Tier
Architecture + Smart Loader spec), `tool_update_prompt.md`, and
`INTEGRATION_MCP/prompt_extract_common_practices.md` (the prompt used to distill `common/*.md`
from the raw specs).

---

## 8. How the resume claim decodes

- **Token-optimized MCP server** → §2 pre-chunked corpus + §4 Smart Loading (override/hybrid) +
  north_star §5 "never dump all docs" → only the minimal relevant subset enters context.
- **Tailored for Client IDE knowledge bases** → §3 FastMCP tools (primary IDE-client interface) +
  the "do not search the internet, this is your knowledge base" behavioral contract (§5).
- **Delhivery One API client integration 3 weeks → 5 days** → the assistant delivers complete,
  spec-faithful, pattern-matching client code across all 18 B2C APIs (create/track/warehouse/
  serviceability/NDR/RVP/e-waybill/...) directly in the engineer's editor, with quirks/retry/auth
  pre-resolved — collapsing weeks of manual doc-reading and trial-and-error.

---

## 9. Exact locations (branch `mcp-client-graviton`, repo `mcp_collection`)

| Concern | File |
|---|---|
| FastMCP server (current, docs-driven) | `INTEGRATION_MCP/server.py` |
| Monolithic prior implementation | `INTEGRATION_MCP/server2.py` |
| Config / auth headers | `INTEGRATION_MCP/config.py` |
| Behavioral guide (loaded first) | `INTEGRATION_MCP/docs/north_star.md` |
| Getting started / lifecycle / terminology | `INTEGRATION_MCP/docs/getting-started.md` |
| Workflow roadmaps | `INTEGRATION_MCP/docs/workflows/{overview,forward_journey,reverse_journey}.md` |
| Shared practices | `INTEGRATION_MCP/docs/common/*.md` |
| Per-API docs | `INTEGRATION_MCP/docs/{api}/{spec,auth,errors,quirks,retry}.md` |
| FAQ / troubleshooting | `INTEGRATION_MCP/docs/faq-troubleshooting.md` |
| README | `INTEGRATION_MCP/README.md` |
| 3-Tier + Smart Loader design | `mcp_collection/mcp_implementation_plan.md` |
