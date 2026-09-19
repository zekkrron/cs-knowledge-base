---
tags: [resume/delhivery, status/draft]
created: 2026-09-19
---
# Supporting Modules

> [!abstract] Not their own resume bullets. They **will** follow up here if the 40-minute hole is Catalog/Gateway. Doc: [[08-supporting-modules]].

## RBAC

JWT → action mapper → namespace → **OPA** `(subject, action, resource, namespace)`. UMS for identity. This is how "600 tools" is not a flat free-for-all.

**Ugly:** what if OPA is down? Fill fail-closed vs fail-open. Sidecar vs library.

## Guardrails

Versioned like tools. Gateway input/output chain (secrets, URLs, grounding). Workflow can have a `guardrail` node. External eval service URL.

## Data transformers

Registered code that **shrinks/redacts** a tool response before the LLM. Security scan on register. This is the other half of "token-optimised" besides the IDE MCP.

**Ugly:** transformer infinite loop / exfil. `code_security` + executor sandbox. What you actually enforce.

## SOP compiler

Second authoring dialect: prose SOP → LLM compile → same graph contract as canvas. Prefix router on `POST /chat` picks an SOP. Compile failure is **non-fatal** (docs): SOP still runnable another way. Say that.

## Versioning / cache

Same draft→published→live machine for tools, workflows, guardrails, transformers. `refresh_upstream_cache` on publish. Catalog never reads Redis.

## Observability

New Relic, CubeAPM, Langfuse. Pause/resume **reuses `trace_id`** from snapshot metadata so the trace is one story.

## Related Notes

- [[02 - Catalog and Gateway]]
- [[04 - Workflows and Pause Resume]]
- [[08-supporting-modules]]
