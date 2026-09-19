# 07 — MCP Server Collection

Resume mapping: *"Delivered MCP servers within a microservices architecture (Python, FastMCP),
coding 100+ tools to bridge internal systems with the AI ecosystem"* and the
token-optimized MCP server for the Client IDE knowledge base.

`mcp_collection/` holds a set of standalone FastMCP servers, each exposing a family of
Delhivery internal API endpoints as MCP tools. These are the "hand-built" domain servers
(distinct from the Gateway, which builds tools dynamically from the catalog).

---

## 1. The servers (`mcp_collection/README.md`)

A collection of FastMCP servers converted from Postman collections / legacy scripts. Each runs
as its own containerized microservice on its own port:

| Server | Tools | Port | Domain |
|---|---|---|---|
| `EXPRESS_MCP` | 73 | 8000 | Express/HQ — package tracking, dispatch, clients, pincodes, billing |
| `FMS_MCP` | 40 | 8001 | Fleet Management — vehicles, contracts (FTL/Intracity/Trailer/B2B), routes, billing |
| `HRMS_MCP` | 11 | 8002 | HR/UMS/DAS — users, vendors, attendance, partners |
| `WMS_MCP` | 5 | 8003 | Warehouse — shipments, inventory |
| `UCID_MCP` | 3 | 8004 | UCID — address, phone, details lookup |
| `FAAS_MCP` | 1 | 8005 | Facility-as-a-Service — facility details |
| `JARVIS_MCP` | 3 | 8006 | Jarvis/Narad helpdesk |

The README headline totals **133–136 tools across ~7 servers** — the concrete realization of
"100+ tools." Additional server folders present in the workspace:
`INTEGRATION_MCP`, `HYPERLOCAL_MCP`, `FM_MCP`, `epod_lm`, `API_SPEC_GENERATOR`, plus the
orchestration folders `agent-hive` and `async-agent-orchestrator`, and the dynamic `mcp-gateway`.

---

## 2. Server anatomy

Each `*_MCP/` folder is a self-contained microservice:
```
<NAME>_MCP/
  Dockerfile
  server.py          # FastMCP server: @mcp.tool()-decorated functions, one per API endpoint
  config.py          # env/config (tokens, base URLs) via load_dotenv()
  requirements.txt
  env.example        # token template (EXPRESS_TOKEN, FMS_TOKEN, ...)
  README.md
```
Tools are declared with the `@mcp.tool()` decorator in `server.py`; each accepts
`token: Optional[str] = None` and forwards it to `make_request(url, params, token=token)`.
Servers run in HTTP transport mode (e.g. `http://localhost:8000/mcp`).

---

## 3. Authentication model

Every tool supports flexible auth with a priority order:
1. **Client-supplied token** (passed as a tool parameter) — most secure, used in production.
2. **Environment variable** (`.env` / Docker `env_file`, e.g. `EXPRESS_TOKEN`) — dev/testing
   fallback.
3. Empty string → auth error.

`.env` files stay in each server folder and are never baked into images / committed
(`.gitignore` excludes them; Docker Compose reads them via `env_file`).

---

## 4. Deployment

- **Docker Compose** (`mcp_collection/docker-compose.yml`) orchestrates all servers; each maps
  to its port. `docker compose up -d` starts all; individual servers can be started selectively.
- Test scripts: `test_servers.sh` (full build+start+health), `test_local.sh`, `quick_test.sh`.
- MCP client config uses HTTP transport, e.g.:
  ```json
  {"mcpServers": {"express": {"url": "http://localhost:8000/mcp", "transport": "http"}}}
  ```

---

## 5. Integrated backend services (the "internal systems" bridged)

Express/HQ, Echo, Bird, FMS, VCUS (attendance/disputes), VCCS (billing/usage), WMS, HRMS, UMS,
DAS (delivery agents/partners), UCID, FaaS, Jarvis/Narad. Each MCP server is the AI-facing
adapter over one or more of these, so an LLM/agent can call them as typed tools.

---

## 6. Token-optimized MCP server (Client IDE knowledge base)

Resume: *"Implemented a token-optimized MCP server tailored for Client IDE knowledge bases,
slashing Delhivery One API client integration effort from 3 weeks to 5 days."*

That is **`INTEGRATION_MCP`** (port 8010, branch `mcp-client-graviton`) — a docs knowledge base
for IDE assistants, **not** another `make_request` wrapper farm, and **not** Catalog
`count_tokens`. Full writeup: `10-client-integration-mcp.md`.

---

## 7. Two ways tools reach the AI ecosystem

1. **Static FastMCP servers** (this document) — hand-built domain servers, one function per
   endpoint, deployed as microservices.
2. **Dynamic Gateway tools** (`06-mcp-gateway.md`) — the gateway builds tools on demand from
   OpenAPI specs registered in the Catalog. The catalog `McpServerService` groups registered
   tools into publishable MCP servers exposed at `{gateway}/{owner}/c/{slug}/mcp`.

Both feed the same agents/workflows; the static servers seeded the ecosystem, the dynamic
catalog+gateway path scaled it to 600+ tools.
