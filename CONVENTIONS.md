# Note-Writing Conventions

This file tells any AI (or human) exactly how notes in this vault should be written.
It also serves as a reference when writing notes manually.

---

## Vault Structure

```
cs-knowledge-base/
  DSA/          One note per data structure or algorithm topic
  LLD/          One note per design problem (e.g. Parking Lot, Snake Game)
  HLD/          One subfolder per system (e.g. Netflix HLD/)
  CONVENTIONS.md
  README.md
```

Each folder has a `README.md` that acts as an index, linking to all notes inside it.
Update it when adding a new note.

---

## Note Structure — Standard Order

Every note should follow this order:

1. **`# Title`** — topic name only, no fluff
2. **`[!abstract]` callout** — one paragraph: what problem this solves and why it matters
3. **Core concepts** — the meat; use `##` and `###` for hierarchy
4. **Diagrams** — Mermaid where structure can be expressed as text; Excalidraw only as last resort
5. **Complexity / Trade-offs table** — mandatory for DSA; recommended for HLD/LLD
6. **Common patterns / Interview notes** — what interviewers actually test
7. **Related Notes** — `[[wikilinks]]` to connected topics at the bottom

---

## Callout Types and When to Use Them

Obsidian renders these as coloured boxes. Use consistently:

| Callout | Colour | Use for |
|---|---|---|
| `[!abstract]` | purple | Opening summary of what the note covers |
| `[!info]` | blue | Interesting context, background, "did you know" |
| `[!tip]` | green | Key insight, the thing that makes it click |
| `[!note]` | blue | Supplementary detail worth flagging |
| `[!example]` | green | Worked example, sample input/output |
| `[!warning]` | yellow | Common mistake, gotcha, edge case |
| `[!danger]` | red | Failure mode, worst-case scenario, what breaks |
| `[!question]` | pink | Open question, something to revisit |

Syntax:
```
> [!tip] Optional title here
> Body text. Can span multiple lines.
> Continues here.
```

---

## Inline Emphasis

- `==highlight==` — the single most important thing on a line; use sparingly
- **bold** — terms being defined, key names
- *italic* — introduced vocabulary, titles, foreign terms
- `code` — any identifier, command, variable name, complexity like `O(log n)`
- ~~strikethrough~~ — something that seems obvious but is wrong

---

## Mermaid Diagrams

Obsidian renders Mermaid natively. Always prefer Mermaid over describing structure in prose.

**Use `flowchart TD`** (top-down) as the default — it fits the page width better than LR.
Switch to `LR` only when the diagram is a chain with no branching (e.g. a pipeline).

Common diagram types:

```
flowchart TD      — architecture, data flow, process steps
sequenceDiagram   — request/response flows, API calls
classDiagram      — OOP relationships for LLD
stateDiagram-v2   — state machines
erDiagram         — database schema
```

Keep node labels short (2–4 words). Avoid long sentences inside nodes.
Use `\n` for a line break inside a node label.

---

## Tables

Use tables for any comparison that would otherwise be a long paragraph:

- Time/Space complexity across approaches
- Database choice rationale (what, why)
- Trade-offs between design options
- API endpoints
- Failure modes and mitigations

---

## DSA Note Specifics

Every DSA note must include:

1. **Problem pattern** — which family does this belong to (sliding window, two pointers, BFS/DFS, divide and conquer, DP, greedy, backtracking)
2. **When to use** — the signal in a problem that tells you this is the right approach
3. **Core idea** in plain English before any code
4. **Code block** with language tag — clean implementation, not interview-compressed
5. **Complexity table** — always Time and Space, best/average/worst where they differ
6. **Key variants** — common twists interviewers add
7. **Classic problems** — 3–5 problems that exemplify this pattern, with links if possible

Example complexity table:
```markdown
| Operation | Time | Space | Notes |
|---|---|---|---|
| Insert | `O(log n)` | `O(1)` | Sifts up |
| Delete min | `O(log n)` | `O(1)` | Sifts down |
| Peek | `O(1)` | `O(1)` | |
| Build heap | `O(n)` | `O(1)` | Not O(n log n) — common mistake |
```

---

## HLD Note Specifics

Every HLD note lives in its own subfolder: `HLD/<System Name>/<System Name>.md`

Mandatory sections:

1. **Requirements** — Functional and Non-Functional, separated
2. **Scale Estimates** — table with DAU, QPS, storage
3. **High Level Architecture** — one Mermaid `flowchart TD` showing all major components
4. **Component Deep Dives** — one `###` section per component
5. **Database Choices** — table with (service, DB choice, reason)
6. **Failure Modes** — table with (failure, mitigation)
7. **Key Design Decisions** — bullet list of the non-obvious choices and why

---

## LLD Note Specifics

Every LLD note follows this structure:

1. **Requirements** — what the system must do
2. **Entities / Classes** — the nouns; use a `classDiagram` Mermaid block
3. **Key Methods** — what each class is responsible for
4. **Design Patterns used** — Observer, Strategy, Factory, etc. with a [[wikilink]] to the pattern
5. **Edge Cases** — what breaks the naive implementation

---

## Frontmatter (YAML)

Add this to the top of every note, above the `# Title`:

```yaml
---
tags: [dsa/trees, status/draft]
created: YYYY-MM-DD
---
```

**Tag taxonomy:**

- Topic: `dsa/arrays`, `dsa/graphs`, `dsa/dp`, `hld/streaming`, `lld/patterns`, etc.
- Status: `status/draft`, `status/reviewed`, `status/mastered`

Status tags let you filter notes by how well you know them — useful for revision.

---

## Linking

- Every note should link to at least 2–3 related notes with `[[Note Name]]`
- When you define a term that has its own note, link it inline: "uses [[Consistent Hashing]] to distribute load"
- Each folder's `README.md` should list and link every note in that folder

---

## Spaced Repetition (Optional)

The **Spaced Repetition** community plugin supports flashcard syntax inside notes.
Add a `#flashcard` heading above a question, answer below a `?` separator:

```markdown
What is the time complexity of building a heap? #flashcard
?
O(n) — not O(n log n). Each element sifts down at most log(height) levels,
and most elements are near the bottom.
```

Use this for facts that must be memorised cold (complexity, theorem names, port numbers).

---

## What Is NOT in This Vault

- `.obsidian/plugins/` — plugin code; install per device
- `.obsidian/workspace.json` — per-device UI state
- Temporary scratch notes — use a separate throwaway vault or paper

---

## Converting Handwritten Notes (OneNote → Obsidian)

Export OneNote section as PDF (even handwritten pages export as image-inside-PDF).
Send the PDF to Cursor chat. Ask:

> "Convert this to an Obsidian note following CONVENTIONS.md"

For each page:
- Handwritten text → restructured markdown
- Drawn diagrams → recreated as Mermaid where possible
- Pasted screenshots/images → described in text; embed original image if needed
- One OneNote section (4–5 pages on one topic) → one `.md` file
