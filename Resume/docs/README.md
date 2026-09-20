# System Architecture Documentation

This directory contains granular, interview-oriented documentation of the Agentic Platform
built at Delhivery (Catalog Service, MCP Gateway, MCP servers, Multi-Agent Orchestration,
Workflow Automation Platform, Pause/Resume architecture, and the Ask AI hybrid tool-search engine).

> Note: `09-ask-ai-search.md` documents the Ask AI hybrid tool-search engine, which lives in the
> `self_api_registration` repo on the `ask-ai` branch (not on the default working branch).

## Index

- `01-system-overview.md` — High-level architecture, services, and how they fit together
- `02-tool-registry.md` — Catalog Service tool registry, entities, storage, resolution
- `03-agents.md` — Agent framework, agent entities, orchestration, Orion/Indent agent
- `04-workflows.md` — Workflow automation platform, DAG compilation, LangGraph execution
- `05-pause-resume.md` — Durable, resumable execution and checkpoint architecture
- `06-mcp-gateway.md` — MCP Gateway: OpenAPI → executable tools
- `07-mcp-servers.md` — MCP server collection (FastMCP), domains, and tooling
- `08-supporting-modules.md` — RBAC, guardrails, data transformers, SOP subsystem, versioning
- `09-ask-ai-search.md` — Ask AI hybrid tool-search engine (kNN + BM25 + Cross-Encoder + TAZS), branch `ask-ai`
- `10-client-integration-mcp.md` — INTEGRATION_MCP: Client IDE knowledge base (3 weeks → 5 days), branch `mcp-client-graviton`
- `11-v2-agent-orchestration.md` — v2 LangGraph nodes, rule vs prompt routing, child depth, LLM loop, cost
- `12-observability-langfuse.md` — Langfuse traces, thinking tokens, budget via metrics API
