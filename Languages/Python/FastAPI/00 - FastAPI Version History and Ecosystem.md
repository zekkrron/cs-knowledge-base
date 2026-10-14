---
tags: [languages/fastapi, status/draft]
created: 2026-10-14
---
# FastAPI Version History and Ecosystem

> [!abstract] What this is
> Where FastAPI comes from and how it evolves: the stack it's built on (**Starlette** for ASGI/web, **Pydantic** for validation, **Uvicorn** as the server), its **0.x versioning policy** (minor bumps can break, so pin), and the releases that changed how apps are written, up to **0.143** (Oct 2026): Pydantic v2 (0.100), the yield-dependency changes, dropping Pydantic v1 (0.126–0.128) and Python 3.9 (0.129), Rust-based JSON serialisation (0.130), streaming and SSE (0.134+), and built-in OpenTelemetry (0.142). The decision this helps with: pinning and upgrading FastAPI safely and knowing which features a given version has.

---

## Why It Matters

- FastAPI is still **0.x**: a minor version (0.131 → 0.132) can contain breaking changes. Unpinned installs have broken production services (e.g. strict JSON content-type checks in 0.132, OpenTelemetry auto-export in 0.142).
- Many FastAPI answers online target old versions (Pydantic v1, `@app.on_event`, `orm_mode`); knowing the timeline separates current practice from outdated advice.

---

## The Stack

| Layer | Library | Role |
|---|---|---|
| ASGI toolkit | **Starlette** (1.x since 2026) | Routing primitives, `Request`/`Response`, middleware, WebSockets, background tasks, test client |
| Validation and serialisation | **Pydantic v2** (Rust core) | Parsing request data, response models, JSON Schema for OpenAPI ([[04 - Pydantic v2 Deep Dive]]) |
| Async compatibility | **anyio** | Thread pool for sync endpoints, task groups; runs on asyncio |
| Server | **Uvicorn** (or Hypercorn, Granian) | Runs the ASGI app ([[06 - WSGI vs ASGI and Python Servers]]) |
| CLI | **FastAPI CLI** (`fastapi dev`, `fastapi run`) | Dev server with reload; production server via Uvicorn |
| Related | **SQLModel** (SQLAlchemy + Pydantic models), **Typer** (CLIs), `pydantic-settings` | Same author/ecosystem |

- `pip install "fastapi[standard]"` brings Uvicorn, the CLI, `httpx`, `python-multipart`, `email-validator`, `pydantic-settings` and similar extras; plain `fastapi` is the minimal core. `fastapi-slim` is discontinued.

---

## Versioning Policy

- Releases are frequent (often weekly). In 0.x, **patch** releases (0.143.0 → 0.143.1) are fixes; **minor** releases (0.142 → 0.143) may break things, called out under "Breaking Changes" in the release notes.
- Pin the minor version in libraries' ranges and lock exact versions in apps: `fastapi>=0.143,<0.144` plus a lockfile ([[11 - Python Packaging and Environments]]). Upgrade deliberately, reading the release notes, with tests.
- Pin **Starlette** and **Pydantic** via the lockfile too: FastAPI declares ranges for both.

---

## Releases That Changed Things

| Version | Date | Change |
|---|---|---|
| 0.1 → 0.6x | 2018–2021 | Type-hint-driven API framework on Starlette + Pydantic v1; automatic OpenAPI docs; DI with `Depends` |
| **0.95** | 2023 | **`Annotated`** recommended for parameters and dependencies (`Annotated[str, Query(max_length=50)]`) |
| **0.100** | Jul 2023 | **Pydantic v2** support (Rust core, much faster validation) |
| 0.106 | Dec 2023 | Exit code of yield dependencies ran **before** the response was sent (broke sessions used by background tasks/streaming); later releases added `Depends(scope=...)` with **`"request"` (after the response) as the default** and `"function"` for early exit ([[03 - FastAPI Dependency Injection]]) |
| ~0.111 | 2024 | **FastAPI CLI** (`fastapi dev` / `fastapi run`) |
| 0.115 | 2024 | Pydantic models for **query, header and cookie** parameter groups |
| 0.118.3 / 0.119 | Oct 2025 | Python 3.14 support; mixed Pydantic v1/v2 models to ease migration |
| **0.126 / 0.128** | Dec 2025 | **Pydantic v1 dropped** (min `pydantic>=2.7`), then `pydantic.v1` compatibility removed |
| 0.125 / **0.129** | Dec 2025 / Feb 2026 | Python 3.8, then **3.9 dropped**: **Python 3.10+** |
| **0.130** | Feb 2026 | JSON responses **serialised by Pydantic in Rust** when there's a return type or `response_model` (≈2× faster) |
| 0.131 | Feb 2026 | `ORJSONResponse` / `UJSONResponse` **deprecated** (no longer needed for speed) |
| **0.132** | Feb 2026 | **Strict `Content-Type` checking** for JSON bodies by default (`strict_content_type=False` to relax) |
| 0.133 | Feb 2026 | Starlette **1.0** support |
| **0.134–0.135** | Feb–Mar 2026 | Streaming **JSON Lines** and binary data with `yield` in path operations; built-in **Server-Sent Events** support ([[06 - FastAPI Streaming SSE and WebSockets]]) |
| 0.137 | Jun 2026 | Routers preserved as a tree instead of copied routes (`router.routes` is now internal); groundwork for per-router dependencies/middleware |
| **0.142** | Oct 2026 | **Built-in OpenTelemetry** traces, metrics and logs for HTTP requests ([[09 - FastAPI Production Deployment]]) |
| **0.143** | Oct 2026 | Automatic OTel **exporter** setup from `OTEL_*` env vars made **opt-in** (`FASTAPI_OTEL_AUTO_CONFIGURE=true` or `FastAPI(telemetry={"auto_configure": True})`) |

### Deprecated patterns to recognise

| Old | Current |
|---|---|
| `@app.on_event("startup")` | `lifespan` async context manager ([[08 - FastAPI Background Work]]) |
| `class Config: orm_mode = True` | `model_config = ConfigDict(from_attributes=True)` |
| `.dict()`, `.json()`, `parse_obj` | `.model_dump()`, `.model_dump_json()`, `model_validate()` |
| `param: str = Query(...)` defaults | `param: Annotated[str, Query()]` |
| `ORJSONResponse` for speed | Return type / `response_model` (Rust serialisation) |
| `BaseSettings` from `pydantic` | `pydantic_settings.BaseSettings` |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Unpinned FastAPI** | A minor release changes behaviour in production (content-type checks, telemetry) | Lockfile; pin `<0.(N+1)`; read release notes |
| **Upgrading FastAPI without Starlette/Pydantic** | Incompatible combinations | Upgrade together via the lock; run the full test suite |
| **Following Pydantic v1 tutorials** | Code doesn't run on current FastAPI | Use v2 APIs ([[04 - Pydantic v2 Deep Dive]]) |
| **Clients without `Content-Type: application/json`** after 0.132 | Requests rejected | Fix clients or set `strict_content_type=False` deliberately |

---

## Interview Lines

> [!tip] Say it like this
> - "FastAPI is Starlette for the ASGI web layer plus Pydantic for validation and serialisation, with OpenAPI generated from type hints, served by Uvicorn."
> - "It's still 0.x, and minor releases can break, so I pin the minor version and lock Starlette and Pydantic."
> - "Current FastAPI is Pydantic v2 only and Python 3.10+; JSON responses are serialised by Pydantic's Rust core when there's a response model."
> - "Recent releases added streaming with `yield`, SSE, and built-in OpenTelemetry, with exporter auto-configuration opt-in since 0.143."

## Related Notes

- [[01 - FastAPI Architecture]] · [[02 - FastAPI Request Lifecycle and Sync vs Async]] · [[04 - Pydantic v2 Deep Dive]]
- [[00 - Python Version History]] · [[00 - Spring Boot Version History]] · [[Languages/README]]
