# Note-Writing Conventions

This file tells any AI (or human) exactly how notes in this vault should be written.
It also serves as a reference when writing notes manually.

---

## Vault Structure

```
cs-knowledge-base/
  DSA/
    Course/            Source layer — one note per course module, in course order
    Patterns/          Sliding window, two pointers, BFS/DFS, DP, greedy...
    Structures/        Arrays, trees, heaps, tries, graphs...
    Problems/          Problems I solved myself
    README.md
  LLD/
    Course/                  Record layer — one note per module: `NN - <Title>.md`
    Patterns/
      Creational/            Singleton, Factory, Abstract Factory, Builder, Prototype
      Structural/            Adapter, Bridge, Composite, Decorator, Facade, Proxy
      Behavioural/           Strategy, Observer, State, Command, Chain of Responsibility
    Principles/              SOLID (one note each), DRY, KISS, composition over inheritance
    Foundations/             OOP mechanics, UML notation, relationship types
    Concurrency/             Threading, locks, races, deadlock
    Tech/
      Java/                  Language idioms the course relies on
      Spring Boot/           API-based solutions, DI, annotations, layering
    Problems/                MY OWN solved designs (empty for now — see rule below)
    Pattern Selection.md
    Interview Approach.md
    README.md
  HLD/
    Course/            Source layer — one note per course module, in course order
    Concepts/          Caching, sharding, consistent hashing, CAP...
    <System Name>/     One subfolder per system, e.g. Netflix HLD/
    README.md
  CONVENTIONS.md
  README.md
```

Each folder has a `README.md` that acts as an index, linking to every note inside it.
Update it when adding a note.

---

## The Two-Layer Model

This vault stores knowledge in **two layers that link to each other**. Understanding
this is the single most important convention in this file.

### Layer 1 — Source (`Course/`)

Preserves each course exactly as taught. One note per module, named
`Module NN - <Topic>.md`, kept in course order. Nothing from the course is lost here.

A module note owns everything **tied to that teaching moment**:

- Outline of what was covered, in the order the course covered it
- The instructor's specific examples, analogies and framing
- Code written during the lecture
- Full walkthrough of any problem the course solved (see Problems rule below)
- Your own questions and confusions from that lecture
- Links out to every concept note this module feeds

Module notes may be short. An outline plus links is often enough.
Tag them `source/<course-name>` so course material can always be filtered out.

### Layer 2 — Concept (`Principles/`, `Patterns/`, `Concepts/`, `Structures/`)

Distilled, source-independent knowledge. One note per concept. These absorb input
from the course **and every other source** — books, blogs, interviews, videos.

A concept note owns the **generalised** knowledge: intent, recognition signal,
trade-offs, your synthesis. It cites its sources but is not bound to any of them.

### The Anti-Duplication Rule

> [!important] If you would write the same paragraph in both layers, it belongs in
> the **concept** note. The module note links to it instead.

Use **transclusion** to show content in two places without copying it:

```markdown
![[Module 07 - Observer via Notification System#Stock Ticker Example]]
```

This renders that section of the module note inside the concept note, live.
One source of truth, visible in both places.

### Linking Discipline

- In a module note: "Taught [[Observer]] via a stock-price notifier. Also introduced [[Loose Coupling]]."
- In a concept note, under `## Sources`: "Introduced in [[Module 07 - Observer via Notification System]]; refined with Head First Design Patterns."

Both questions then have an answer. *"What did module 7 cover?"* → the module note.
*"Everything I know about Observer?"* → the concept note, with backlinks showing every
module and problem that touched it.

---

## Agent Extraction Procedure

> [!important] The agent performs the extraction, not the user
> The user will say something like *"convert module 12"* or *"add this module to my notes"*
> and nothing more. The agent is responsible for finding every piece of extractable
> knowledge in that module and routing it to the correct destination.
> **Do not ask the user where things belong.** That decision is specified below.

### Record vs Accumulator

- **Record note** — `Course/NN - <Title>.md`. Written once, never revisited.
- **Accumulator note** — everything else. Returned to and grown across the entire course.

So *"where does this go?"* always means *"which accumulator does this feed?"*

### Procedure

1. **Read this file first.**
2. **Render the source.** OneNote PDFs have no text layer — see
   [Converting Handwritten Notes](#converting-handwritten-notes-onenote--obsidian).
3. **Write the record note** at `Course/NN - <Original OneNote Page Title>.md`.
   `NN` is the module's position in the course. One OneNote page = one module = one note.
4. **Run the extraction checklist** below across the whole module.
5. **For every hit, search the vault for an existing accumulator note first.**
   - Exists → **merge** the new understanding into it. Never overwrite.
   - Does not exist → create it using that folder's prescribed structure.
6. **Cross-link in both directions.** The module note links out to each accumulator it
   fed; each accumulator gains an entry under `## Sources` naming the module.
7. **Update the `README.md` index** of every folder touched.
8. **Report** what was created, what was updated, and anything deliberately skipped.

### The Extraction Checklist

Scan every module for all seven. A single module normally hits several.

| # | Look for | Destination |
|---|---|---|
| 1 | A design pattern taught or applied | `Patterns/<Category>/<Name>.md` |
| 2 | A principle invoked or violated | `Principles/<Name>.md` |
| 3 | An OOP mechanism, relationship type, or UML notation | `Foundations/<Name>.md` |
| 4 | A naive solution and why it failed | *Motivation* section of the pattern note |
| 5 | A concurrency concern | `Concurrency/<Name>.md` |
| 6 | A Java or Spring Boot idiom | `Tech/Java/` or `Tech/Spring Boot/` |
| 7 | An interview-relevant remark or technique | `Interview Approach.md` |

Anything matching no row **stays in the module note**: the narrative, the instructor's
specific framing, the full problem walkthrough, the user's own questions and confusions.
That residue is expected and correct — it is what the record layer is for.

### Merge Rules

> [!danger] Accumulators are append-and-refine, never replace
> A pattern note may already hold knowledge from five earlier modules. Destroying it
> to write the current module's version is the worst failure mode in this vault.

- Never delete existing content from an accumulator to make room for new content.
- If new material contradicts what is there, keep both and flag it with a `[!question]` callout.
- If the idea is already present, improve the existing wording instead of appending a duplicate.
- Append to `## Sources`; never replace it.

### Code Policy

The course has a public repo, linked in the header of each OneNote page
(e.g. `github.com/singhsanket143/Design-Patterns/tree/master/src/...`).

- Link the relevant repo folder from the module note.
- Inline **only** excerpts that carry the teaching point — typically 5–20 lines.
- Do **not** transcribe DTOs, enums, or boilerplate from photographs of a screen.

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

## LLD Folder Roles

### `Course/`

Source layer. See [The Two-Layer Model](#the-two-layer-model).
One note per module. Course problem walkthroughs live here in full.

### `Principles/`

The rules used to **judge** a design: SOLID, coupling and cohesion, composition over
inheritance, DRY, KISS, YAGNI, Law of Demeter, separation of concerns.

Principles are distinct from patterns because they serve a different purpose.
Patterns are what you **build with**; principles are what you **justify with**.
In an interview: *"I'm splitting this class because of SRP"* is a principle.
*"I'll use Strategy here"* is a pattern.

Most patterns are a principle made concrete — Strategy is essentially the
Open/Closed Principle in executable form. So:

- A principle note links **down** to the patterns that embody it
- A pattern note cites **up** to the principles it serves

### `Patterns/`

One note per design pattern, filed under `Creational/`, `Structural/` or `Behavioural/`.

Subfolders organise the folder tree only. Links are unaffected — `[[Strategy]]` resolves
from anywhere in the vault regardless of which subfolder the note sits in.

Structure of a pattern note:

1. **Intent** — one sentence, what it solves
2. **Motivation** — the naive approach and the specific problems it caused
3. **Recognition signal** — the cue in a problem that says "use this"
4. **Structure** — Mermaid `classDiagram`
5. **Code** — the teaching excerpt only; link the repo for the full implementation
6. **Trade-offs** — table of pros/cons
7. **Confused with** — the pattern it is most often mistaken for, and how to tell them apart
8. **Principles served** — `[[wikilinks]]` upward
9. **Sources** — every module that contributed, appended over time

> [!tip] Why Motivation matters
> Courses teach patterns as naive solution → problems → refactor, and interviewers ask
> *"why did you choose this?"*. A pattern note without its motivation cannot answer that.

### `Problems/`

> [!warning] Reserved for problems solved independently
> This folder holds **only** designs worked out from scratch with full knowledge.
> Course problem walkthroughs do **not** go here — they stay in their `Course/Module NN` note.
> The course's treatment is not authoritative enough to occupy this space.

Unsolved course assignments **do** belong here: create the note with the question and
`status/todo` in frontmatter. Filtering that tag gives the work queue. Fill it in on solving.

Even when a problem stays in the course layer, any **pattern** it teaches still flows
up into `Patterns/`. Only the problem design itself stays in the module note.

Structure of a solved problem note:

1. **Problem statement** and clarifying questions to ask
2. **Requirements** — functional and non-functional
3. **Entities / Classes** — Mermaid `classDiagram`
4. **Key methods** — responsibility per class
5. **Patterns used** — `[[wikilinks]]`, one line each on *why*
6. **Edge cases** — what breaks the naive implementation
7. **Extensions** — follow-ups an interviewer would add

### `Foundations/`

The **mechanisms** design is built from, as opposed to the guidelines in `Principles/`.
Abstraction, encapsulation, inheritance, polymorphism, interface vs abstract class,
static vs instance — plus the relationship types (association, aggregation, composition,
dependency) and UML class diagram notation.

The distinction: polymorphism is a *mechanism* (Foundations); "program to an interface"
is a *guideline* (Principles). Without this folder, mechanism knowledge has no home and
gets stranded inside module notes.

### `Tech/`

Language- and framework-specific knowledge, split into `Java/` and `Spring Boot/`.

Named `Tech/` rather than `Stack/` to avoid colliding with the Stack data structure
in `DSA/`. Extensible — a new language or framework becomes a new subfolder.

This exists so implementation detail does not contaminate pattern notes. *"Use an enum
for a fixed set of states"* is a Java idiom, not a design pattern, and belongs in
`Tech/Java/`. Spring Boot material from the API-based modules goes in `Tech/Spring Boot/`
— dependency injection, annotations, service layering.

> [!tip] Spring Boot cross-links heavily
> Dependency injection is [[Dependency Inversion Principle]] made concrete, beans are
> managed [[Singleton]]s, and AOP is [[Proxy]]. Always link Spring Boot notes back to the
> principle or pattern they implement.

### `Concurrency/`

Threading, locks, synchronisation primitives, race conditions, deadlock.
Concept layer — same rules as `Principles/`.

### `Pattern Selection.md`

A **decision table**, not knowledge storage. Optimised for one moment: a problem
statement is in front of you and you need the right pattern.

Maps *signal in the problem* → *pattern*:

| Signal | Pattern |
|---|---|
| Interchangeable algorithms swapped at runtime | [[Strategy]] |
| One-to-many notification on state change | [[Observer]] |
| Object creation logic varies by input | [[Factory]] |
| Behaviour changes with internal state | [[State]] |
| Add responsibilities without subclassing | [[Decorator]] |

Also holds **commonly confused pairs**: Strategy vs State, Factory vs Abstract Factory,
Decorator vs Proxy, Adapter vs Facade.

It deliberately duplicates the pattern notes. Its value is being one scannable page.
Write it last, grow it as patterns are learned, read it before an interview.

### `Interview Approach.md`

The meta-skill, accumulated from asides scattered across the course: how to open an LLD
interview, which clarifying questions to ask before designing, how to sequence the
answer, what interviewers actually score, and common ways candidates lose points.

The agent extracts these remarks — the user will not flag them.

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

The source notes are entirely freehand ink plus photographs of the lecture screen.

> [!danger] PDF text extraction returns nothing
> OneNote rasterises ink, so a PDF export has no text layer. Reading the PDF as text
> yields only the page title, date and repo link. **The pages must be rendered to
> images and read visually.**

### Tooling

Scripts live in `C:\Users\akash\onenote-tools\` — deliberately outside the vault so they
do not sync. Requires `pymupdf`.

```powershell
# Whole page, low DPI, to locate content on the canvas
python onenote_to_png.py "<file>.pdf" "overview" 35

# Fractional region at readable DPI: x0 y0 x1 y1 (0-1, origin top-left)
python crop_region.py "<file>.pdf" 1 0.60 0.0 0.75 0.26 170 "crops/region.png"
```

### Why cropping is required

A OneNote page is a canvas of roughly 76 × 48 inches, mostly whitespace, laid out in
vertical columns. Rendered whole at readable DPI it exceeds 15000 px wide. Render an
overview at ~35 DPI first to locate the columns, then crop each region at ~170 DPI.
Budget roughly 16–20 crops per page.

### Conversion rules

- Handwritten text → restructured markdown
- Drawn diagrams → recreated as Mermaid (`stateDiagram-v2` for state machines)
- Photographed slides → read and converted to prose, tables or callouts
- Photographed code → link the repo; inline only the teaching excerpt
- **One OneNote page = one module = one `Course/NN - <Title>.md`**

### Source PDFs are not tracked

A single handwritten page exports to ~85 MB. `*.pdf` is gitignored. Keep originals in
OneDrive and commit only the converted markdown.

> [!warning] The photographs contain personal contact details
> The meeting overlay in many screen photos shows an email address and phone number.
> The repo is currently private. Do not make it public, and do not share raw page
> images, without checking this first.
