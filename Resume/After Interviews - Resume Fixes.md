---
tags: [resume, status/draft]
created: 2026-09-19
---
# After Interviews — Resume Fixes

> [!abstract] Do **not** edit `resume.tex` until Temple (and whatever else is already out) is done. This list is PDF surgery for later. Defence notes still talk to the **current** PDF.

Temple PDF is the lock. When interviews are over, apply these in `resume.tex` and the matching `% resume:…` notes the same day.

## Open

### 1. Move 72k off Catalog+Gateway → Multi-Agent

**Yes — that is the correct home.** 72,000+ daily requests is production **`POST /{team}/custom-agent/{slug}/v2/trigger`**, not Catalog Mongo, not playground `v2/chat`. Catalog+Gateway existed so that agent could load tools; the QPS is live agent runs.

| Now (`resume:catalog-gateway`) | After |
|---|---|
| `...handling 72,000+ daily requests.` | Drop the 72k clause from this bullet |
| `resume:multi-agent` has no volume | Add 72k (`/v2/trigger`) here |

Leave 124k on `resume:workflows`. Different counter, different era.

### 2. Catalog+Gateway: LangChain → LangGraph (in place)

The Gateway line says **LangChain**. Wrong name. Fix **on the same bullet** — do not move the word to multi-agent. Multi-agent already says LangGraph.

`Architected MCP Gateway (LangChain)` → `Architected MCP Gateway (LangGraph)` (or `FastMCP` + LangGraph if you want the tool path named honestly).

## Related Notes

- [[resume.tex]] — do not touch until this list is executed
- [[02 - Catalog and Gateway]]
- [[03 - Agents and Orion]]
- [[Resume/README]]
