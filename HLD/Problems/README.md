---
tags: [hld/problems, status/draft]
created: 2026-09-22
---
# HLD Problems

> [!abstract] Interview-shaped walks. Same script every time: **requirements → numbers → APIs/schema → boxes → one deep dive → break it**. [[01 - How the Round Works]].

> [!tip] Short on time? **[[16 - Estimation and Decision Cheat Sheet]]** is the arithmetic plus the thresholds that pick the tech; then walk one problem below out loud.

## The list

Order of work: [[Interview Question Priority]] (this sprint: [[LLD and HLD Tracker]]). Only problems written in the current format are kept here; the older-format notes were removed on 2026-11-14 and each comes back only after its research.

| # | Problem | Deep dive on the board |
|---|---|---|
| 6 | [[06 - Seat Booking]] | `free→held→sold`, no lock across PSP; waiting room for mega sales |
| 13 | [[13 - URL Shortener]] | Permuted counter ranges, cached 302s, clicks off the hot path |

> [!info] Note format
> Clarify script → requirements → estimates that drive V0 → V0 → scale ladder (V1–V4, each with its trigger number) → twist cards → break it → operations → close, with a Mermaid diagram for V0 and after every ladder step. Shared mechanics live in [[12 - Mini Designs]]. The template is in `CONVENTIONS.md` under *HLD Problems and Mini Designs*; how to research and prioritise the ladder and twists is in *Researching and Prioritising a Problem* below.

## How you walk any of them

1. Clarify, then functional / NFR (with numbers) / **out of scope**
2. Only the estimates that pick something
3. APIs, tables + FKs
4. V0: the simplest design that works, and what it can't handle
5. Climb the ladder: each step names the number that breaks the previous version and the mini design it uses
6. Expect twists off the ladder: consistency, features, failures, cost, regions, compliance, abuse
7. Break it: double-click / process death / cache lie

---

## Researching and Prioritising a Problem

Applies to every problem note in the new format. The note structure (template in `CONVENTIONS.md`) and the mini designs do not change; this procedure decides **what goes on the ladder and in the twist cards, and in what priority**.

### What research decides, and what it doesn't

- **V0** is the functional core of the prompt as interviewers actually give it. It is usually stable; research confirms it rather than rebuilds it.
- **The scale ladder (V1–Vn) is ordered by its trigger numbers**, not by how often a step is asked: you can't shard before the cache breaks. Research adds an **evidence line and a confidence** to each step and decides how much space it gets. A step nobody asks about stays if the logic needs it, kept short.
- **Twist cards are ordered by evidence.** Cards supported by reports come first; cards from recall or judgement stay, in a lower-priority group, so nothing useful is lost.
- **Mini designs are not touched** by this research. They are reusable mechanics; they only need sourced numbers.

### Procedure

1. **Collect 10–20 dated reports** of the question being asked: LeetCode Discuss, GeeksforGeeks, Blind, Glassdoor, PracHub, company prep guides; plus the major guides (Hello Interview, Grokking/DesignGurus) for what interviewers are told to probe. For each, record company, date, starting scope, the scale pushed, the component the interviewer drilled into, and every follow-up.
2. **Tally.** Each ladder step and twist card gets a count and a confidence:
    + `strong`: several independent reports.
    + `medium`: one report, or consistent guide material.
    + `judgement`: no report found; kept because the design or the Indian market needs it (regulation, Indian spikes).
3. **Annotate in place.** Add `Evidence:` with sources and confidence to each ladder step; group twist cards into **Most asked** and **Lower priority (recall / judgement)**, keeping their ids so cross-references still work.
4. **Add an `## Evidence` section** before `## Sources`: Follow-up | Reports | Count | Placed as (V0 / Vn / twist, with confidence) | Deep dive? (whether interviewers drill into it).
5. **Label numbers.** A number is either sourced (with date) or marked *assumed*. Clarify-script defaults are assumptions.
6. **Change little when the flow is right.** If the existing ladder is already a correct logical escalation and the evidence doesn't contradict it, keep its order and text; only add evidence, confidence and grouping. Rewrite a step only when the evidence shows it's wrong or missing.

---

## NotebookLM Bundle (made only on request)

A problem note leans on mini designs in separate files, so for NotebookLM it is uploaded as **one Markdown bundle**: the problem note plus the mini designs it uses. The narration stays on the problem; the mini designs are context. Bundles live in `HLD/bundles/` (gitignored) and are rebuilt whenever asked.

**Recipe** (the agent follows this when Akash says "make the NotebookLM bundle for <problem>"):

1. File: `HLD/bundles/<NN> - <Problem>.md`, overwriting any old one.
2. Header: problem name, date built, and one line: "Main source: the problem note. The mini designs after it are reference material for the problem's [[MD-..]] links."
3. The problem note, `HLD/Problems/<NN> - <Problem>.md`, copied whole.
4. Each mini design the problem links, from `HLD/Topics/12 - Mini Designs/`, in the order the problem first uses them (the abstract's MD chain gives this order). Each under a heading `## Reference: MD-NN <Name> (used in <where the problem first uses it, e.g. V1 cache>)`, then the MD copied whole.
5. Not included: HLD topic notes (Caching, Databases...). Add one only if the problem leans on it heavily and Akash asks.
6. Check the size: one NotebookLM source holds up to 500,000 words; a problem plus its mini designs is a few tens of thousands.

**Using it:** new notebook → Add source → upload the bundle. Studio → Audio Overview → customise, paste the prompt below, generate. Then **Interactive mode → Join** to ask questions by voice.

**Audio Overview prompt** (copy everything inside the box):

````text
You are teaching one system-design interview problem to an engineer preparing for an SDE-3 round.

HOW THE SOURCE IS BUILT
- Part 1, before the first "## Reference:" heading, is the PROBLEM NOTE. It is the only thing you narrate.
- Part 2, every "## Reference: MD-NN ..." section, is background on a reusable mini design. It is a lookup, not content to cover.

RULE 1: STAY ON THE PROBLEM NOTE, IN ITS OWN ORDER
Narrate the problem note from its first section to its last, in the order the sections appear. Do not skip, merge or reorder sections, and cover every section the note has, including any you would not expect. For each section, explain what it says and why it matters for the design, keeping the reasoning the note gives (the number that triggers a change, the trade-off, how often interviewers ask for it). Never describe a later design before the note reaches it.

RULE 2: MINI DESIGNS ARE SHORT ASIDES
When the problem note mentions a mini design (written MD-NN or by name), pause for six to seven sentences, using its reference section:
- One or two sentences: what problem that mini design solves, and why this problem needs it at this exact point.
- Three to four sentences: how it works inside, as an overview the listener can picture. Name the main components and data structures, and walk through the core flow step by step (what is stored where, what happens on a request, what keeps it correct or fast).
- One sentence: the setting this problem picks, only if the problem note states it (for example a TTL or a block size).
Then say "back to the problem" and continue exactly where you stopped.
Stay at overview level: do not cover the mini design's evolution ladder, its alternatives, its failure modes or its twist cards. If the same mini design comes up again later, refer back to it in one sentence; do not re-explain it.

RULE 3: WHAT TO LEAVE OUT
Skip the "Evidence" table and the "Sources" list at the end of the problem note; mention evidence only through each ladder step's Evidence line. Skip all Related Notes lists.

STYLE
Conversational and precise. Keep every number exactly as written. Use only the uploaded source. Close with: "That's the full walk-through; ask me to go deeper on any step."
````

## Related Notes

- [[HLD]]
- [[16 - Estimation and Decision Cheat Sheet]]
- [[Topics/README]]
- [[05 - Databases]]
- [[14 - Coordination and Concurrency]]
