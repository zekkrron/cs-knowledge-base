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
10. [[08 - Voice Agent]] — `voice-agent` (Dograh pipeline, post-call workflow, cost model)

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
| `narad` | no note yet (Golang ticket listing API, RBAC permissions API) |
| `mcp-servers` | [[01 - MCP Servers]] |
| `catalog-gateway` | [[02 - Catalog and Gateway]] |
| `multi-agent` | [[03 - Agents and Orion]] |
| `ask-ai` | [[05 - Ask AI Search]] |
| `workflows` | [[04 - Workflows]] · [[05 - Pause Resume]] |
| `voice-agent` | [[08 - Voice Agent]] |
| *(removed Oct 2026)* `dev-productivity`, `orion` | [[01 - MCP Servers]], [[03 - Agents and Orion]] (kept as interview material) |
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
| [[13-code-node-lambda-sandbox]] | Code node Lambda sandbox |
| [[14-node-timeout-retry-errors]] | Node timeout, retry, error strategy |

## ClearTax docs index

| File | What |
|---|---|
| [[00-architecture-overview]] | Two flows, modules, Reactor |
| [[01-data-harvester-microservice]] | Generic / configurable |
| [[02-transactional-reconciliation]] | MY recon |
| [[03-einvoicing-expansion-templates]] | Country template machinery |
| [[04-incident-management-oncall]] | Harvester runbook |
| [[05-automated-testing]] | JaCoCo / JUnit5 |

## Audio Overview Prompt

Upload one defence note (`Delhivery/`, `ClearTax/`) or code-level doc (`docs/`) as the NotebookLM source, then Studio → Audio Overview → customise, paste this, generate. Use **Interactive mode → Join** to get grilled by voice.

````text
Help me defend this resume bullet in an interview, following the note's own order. Do not reorder or skip.

1. Start with the resume line exactly as written, and what an interviewer will assume from it.
2. Explain what the system is and what I actually owned, in plain words.
3. Walk through the architecture and the main flows step by step, in the order the note gives them: which component calls which, and why.
4. For every number on the bullet, say where it comes from and how to defend it if pushed. Point out any number or word the note flags as risky.
5. Go through the follow-up questions the note lists, each as the interviewer would ask it, then the honest answer from the note.
6. End by having the interviewer push on the two weakest points, and give the best answer the note supports.

Use only the uploaded source. Never invent details that are not in it.
````

## Related Notes

- [[HR]]
- [[HLD/Topics/README]]
- [[ClearTax/README]]
- [[Delhivery/README]]
