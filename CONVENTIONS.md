# Conventions

Authoritative spec for this vault. Agent reads this before writing anything.

---

## Voice

> [!important]
> Write every note in Akash's natural language — the way he would explain it to himself,
> not a textbook definition. The test: would the sentence connect in his head immediately?
>
> Bad: "Interchangeable algorithms swapped at runtime"
> Good: "You have a finite family of algorithms that all solve the same problem and you
> need to swap between them at runtime based on conditions — without the caller knowing"
>
> If something clicks better as an analogy, use the analogy. Precision is less important
> than immediate recognition.

---

## LLD Folder Structure

```
LLD/
  Course/           Record layer. One note per module: NN - <Title>.md
  Patterns/
    Creational/
    Structural/
    Behavioural/
  Principles/       One note per principle (e.g. Single Responsibility.md)
  Foundations/      OOP mechanics, UML notation, relationship types
  Concurrency/      Threads, locks, races, deadlock
  Tech/
    Java/
    Spring Boot/
  Problems/         ONLY Akash's own solved designs. Empty for now.
  Pattern Selection.md
  Interview Approach.md
  README.md
```

---

## Two Layers

- **Record** (`Course/`) — the course as taught. Written once. Owns: outline, instructor's framing, full problem walkthrough, Akash's questions. Never revisited after extraction.
- **Accumulators** (everything else) — distilled knowledge that grows across the entire course and beyond.

**Anti-duplication:** if the same paragraph would go in both, it belongs in the accumulator. The module note links to it instead.

**Naming:** `NN - <Original OneNote Page Title>.md`. NN = module position. One OneNote page = one module = one note.

---

## Agent Extraction Procedure

The user says "convert module 12." The agent does everything below. Never ask the user where something belongs.

**Step 1** — Render the source (OneNote PDFs have no text layer, see [OneNote Conversion](#onenote-conversion)).

**Step 2** — Write the record note at `Course/NN - <Title>.md`.

**Step 3** — Run the extraction checklist across the whole module:

| Look for | Destination |
|---|---|
| Design pattern taught or applied | `Patterns/{Creational,Structural,Behavioural}/<Name>.md` |
| Principle invoked or violated | `Principles/<Name>.md` |
| OOP mechanism, relationship type, UML | `Foundations/<Name>.md` |
| Naive solution + why it failed | *Motivation* section of the pattern note |
| Concurrency concern | `Concurrency/<Name>.md` |
| Java or Spring Boot idiom | `Tech/Java/` or `Tech/Spring Boot/` |
| Interview-relevant remark | `Interview Approach.md` |

Anything that matches nothing stays in the module note. That residue is correct.

**Step 4** — For each hit: search first. Merge into the existing note if it exists. Create only if it does not.

**Step 5** — Cross-link both directions. Module note lists what it fed. Each accumulator appends the module under `## Sources`.

**Step 6** — Update `README.md` of every folder touched. Report what was created, updated, skipped.

**Merge rule:** accumulators are never overwritten. Append and refine only. Contradictions stay side by side under `[!question]`.

**Code:** link the course repo (`github.com/singhsanket143/Design-Patterns`). Inline only the 5–20 line teaching excerpt. Never transcribe boilerplate off screen photographs.

---

## Note Templates

### Pattern note

```
---
tags: [lld/patterns/behavioural, status/draft]
created: YYYY-MM-DD
---
# <Name> Pattern

> [!abstract] One sentence: the problem it exists to solve.

## Motivation
The naive approach and the specific problems it caused. This is what justifies the pattern.

## Recognition Signal
> [!tip] The cue that says "use this pattern here" — in Akash's own words.

## Confused With
Table comparing this pattern to the one it is most often mistaken for.

## Structure
classDiagram Mermaid block.

## Key Code
Teaching excerpt only. Link repo for full implementation.

## Trade-offs
| Pro | Con |

## Principles Served
[[wikilinks]] to Principles/ notes.

## Sources
- [[NN - Module Title]] (introduced)
```

### Principle note

```
---
tags: [lld/principles, status/draft]
created: YYYY-MM-DD
---
# <Principle Name>

> [!abstract] What rule this enforces and why it exists.

## The Problem It Solves
What goes wrong without this principle.

## The Rule
One clear statement in plain language.

## Patterns That Embody This
[[wikilinks]] down to Patterns/ notes.

## Sources
```

### Module (record) note

```
---
tags: [source/lld-course, status/done]
created: YYYY-MM-DD
---
# NN - <Title>

> [!abstract] What this lecture covered.

**Course repo:** <link>

## What Was Covered
Outline in course order.

## Naive Solution and Its Problems
What failed and why. (Feeds Motivation in the pattern note.)

## The Refactor
How the design evolved. Key excerpts only.

## Key Observations
Instructor's analogies, Akash's questions, things that didn't click.

## Extracted To
- [[Pattern Name]] — why
- [[Principle Name]] — why
```

---

## Formatting

**Frontmatter:** every note gets `tags` and `created`.
Tags: `lld/patterns/behavioural`, `lld/principles`, `source/lld-course`, `status/draft`, `status/mastered`.

**Callouts:** `[!abstract]` open · `[!tip]` key insight · `[!warning]` gotcha · `[!danger]` failure mode · `[!info]` context · `[!example]` worked example · `[!question]` unresolved

**Diagrams:** Mermaid first. `classDiagram` for LLD, `stateDiagram-v2` for state machines, `flowchart TD` default. Node labels 2–4 words. Excalidraw only when structure genuinely cannot be text.

**Inline:** `==highlight==` sparingly · **bold** for terms · `code` for identifiers and complexity · link concepts inline with `[[Name]]` · end with `## Related Notes`.

---

## OneNote Conversion

Source notes are entirely freehand ink and screen photographs — no text layer in the PDF.

**Tooling** (`C:\Users\akash\onenote-tools\`, not in vault):
```powershell
python onenote_to_png.py "<file>.pdf" overview 35        # full page, low DPI, find content
python crop_region.py "<file>.pdf" 1 x0 y0 x1 y1 170 out.png  # region at readable DPI
```
Render overview at ~35 DPI, then crop ~16–20 regions per page at ~170 DPI.

**Rules:** handwriting → markdown · drawn diagrams → Mermaid · photographed slides → prose/tables · photographed code → link repo, inline excerpt only.

`*.pdf` is gitignored (~85 MB per page). Keep originals in OneDrive.

> [!warning] Screen photographs contain Akash's email and phone number in the meeting overlay. Repo is private. Do not make it public without checking first.
