---
tags: [resume, status/draft]
created: 2026-09-19
---
# Resume

> [!abstract] Wording lives in `Resume/resume.tex`. Defence notes live in `Delhivery/` and `ClearTax/`. Raw writeups: `docs/` (Delhivery) and `ClearTax/docs/` (ClearTax). When `resume.tex` changes, the matching note must change the same day.

## Source of truth

| File | Role |
|---|---|
| `resume.tex` | Current resume (LaTeX, maintained in this vault). Bullet ids are `% resume:…` comments |
| `Delhivery/` | How to **answer** Delhivery bullets |
| `docs/` | Delhivery code-level writeups |
| `ClearTax/` | How to **answer** ClearTax bullets |
| `ClearTax/docs/` | ClearTax / Data-Harvester writeups (moved off vault root) |

**Sync rule:** `Resume/resume.tex` is the source of truth ↔ the `resume:*` note.

**After interviews:** apply [[After Interviews - Resume Fixes]] to `resume.tex`. Do not apply it before Temple.

## Delhivery — study order

1. [[00 - Ownership and How to Talk]] — plane split + **fill the ownership table and number sources**
2. [[01 - MCP Servers]] — `mcp-servers`, `dev-productivity`
3. [[02 - Catalog and Gateway]] — `catalog-gateway`
4. [[03 - Agents and Orion]] — `multi-agent`, `orion`
5. [[04 - Workflows]] — canvas compile / run
6. [[05 - Pause Resume]] — interrupt / S3 / scheduler (deepest hole)
7. [[05 - Ask AI Search]] — `ask-ai` (TAZS name, `RERANKER_TYPE` vs character-count)
8. [[06 - Supporting Modules]] — RBAC, transformers, SOP (follow-ups)
9. [[07 - Langfuse]] — how to talk traces (you did not build Langfuse)

## ClearTax — study order

1. [[00 - ClearTax Ownership]] — two flows + **fill ownership and number sources**
2. [[01 - Data Harvester]] — `cleartax-harvester` (the LLD-shaped bullet)
3. [[02 - Transactional Reconciliation]] — `cleartax-recon`
4. [[03 - Singapore Expansion]] — `cleartax-singapore` (no `SG/` in the snapshot)
5. [[04 - On Call]] — `cleartax-oncall`
6. [[05 - Automated Testing]] — `cleartax-testing` (90% not in this tree)

## Bullet id → note

| id | Note |
|---|---|
| `mcp-servers` | [[01 - MCP Servers]] |
| `dev-productivity` | [[01 - MCP Servers]] |
| `catalog-gateway` | [[02 - Catalog and Gateway]] |
| `multi-agent` | [[03 - Agents and Orion]] |
| `orion` | [[03 - Agents and Orion]] |
| `ask-ai` | [[05 - Ask AI Search]] |
| `workflows` | [[04 - Workflows]] · [[05 - Pause Resume]] |
| `cleartax-harvester` | [[01 - Data Harvester]] |
| `cleartax-recon` | [[02 - Transactional Reconciliation]] |
| `cleartax-singapore` | [[03 - Singapore Expansion]] |
| `cleartax-oncall` | [[04 - On Call]] |
| `cleartax-testing` | [[05 - Automated Testing]] |

## Delhivery docs index

| File | What |
|---|---|
| [[01-system-overview]] | Architecture |
| [[02-tool-registry]] | Catalog |
| [[03-agents]] | Agents, Orion |
| [[04-workflows]] | Canvas / SOP |
| [[05-pause-resume]] | Durable pause |
| [[06-mcp-gateway]] | Gateway |
| [[07-mcp-servers]] | Static FastMCP |
| [[08-supporting-modules]] | RBAC, transformers |
| [[09-ask-ai-search]] | Hybrid search |
| [[10-client-integration-mcp]] | INTEGRATION_MCP / Client IDE KB |
| [[11-v2-agent-orchestration]] | v2 graph nodes, routing, child depth, LLM loop |
| [[12-observability-langfuse]] | Langfuse traces + cost metrics |

## ClearTax docs index

| File | What |
|---|---|
| [[00-architecture-overview]] | Two flows, modules, Reactor |
| [[01-data-harvester-microservice]] | Generic / configurable |
| [[02-transactional-reconciliation]] | MY recon |
| [[03-einvoicing-expansion-templates]] | Country template machinery |
| [[04-incident-management-oncall]] | Harvester runbook |
| [[05-automated-testing]] | JaCoCo / JUnit5 |

## Related Notes

- [[HR]]
- [[HLD/Topics/README]]
- [[ClearTax/README]]
- [[Delhivery/README]]
