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
  Course/           Record layer. One folder per module: NN - <Title>/
                    Each folder contains ordered MD files: 01 - Problem Statement.md,
                    02 - <Topic>.md, etc. A module can span as many files as needed.
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
  Problems/         Researched LLD / machine-coding problem notes (format: "LLD Problems"
                    below). Code lives in Code/, notes link to it.
  Code/             Maven project: plain-Java solutions, two self-contained packages per
                    problem (v0, full), JUnit tests. Source of truth for all LLD code.
  Problem Catalog.md
  Pattern Selection.md
  Interview Approach.md
  README.md
```

---

## DSA Folder Structure

```
DSA/
  Topics/           One folder per topic (e.g. Arrays/, Trees/, Graphs/)
                    Each folder contains numbered MD files: 01 - <Topic>.md, 02 - <Topic>.md, etc.
                    One special folder per topic: Indexing/ — contains MD files that map
                    every question from the course to the thought process or pattern it teaches.
  Ratta Sheet/      Standard/non-trivial questions to be memorised, not derived in an interview.
                    One folder per section (e.g. Stack and Queue/, Arrays/, Graph/).
                    Each folder holds numbered MD files: <Section> 1.md, <Section> 2.md, etc.
                    Split into a new numbered file when one grows too long — no fixed count.
  README.md
```

**Ratta Sheet source of truth:** lives in a Google Doc (needed during office hours where the
vault isn't available). Content pasted into `temp.md` for conversion must be transcribed
**exactly as given** — no rewording, paraphrasing, or restructuring of the explanation text.
Only the heading structure below is imposed on top of it.

**Ratta Sheet question format:**

```
## <Exact Question Name as Pasted> (<Source Tag>)

**Problem Statement:** ... (if given)

### Brute
Intuition: ...
Complexity: ...
(explanation and code included only if explicitly given/asked for)

### Better
Intuition: ...
Complexity: ...
(explanation and code included only if explicitly given/asked for)

### Optimal
Intuition: ...
Explanation: ...
Code: ...
Complexity: ...
(explanation and code always included)
```

---

## Two Layers

- **Record** (`Course/`) — the course as taught. Written once. Owns: outline, instructor's framing, full problem walkthrough, Akash's questions.
- **Concept notes** (everything else — `Patterns/`, `Principles/`, `Foundations/`, etc.) — one canonical note per concept.

**Write-once, link-everywhere:** a concept (a pattern, a principle, an OOP mechanism) is explained **once** in its own canonical note. Every other place that touches it just **links** to that note with `[[wikilinks]]` — it does NOT re-explain, re-summarise, or merge distilled copies of it. This keeps a single source of truth without the burden of re-distilling knowledge across modules.

Do not try to "accumulate" a concept by pulling paragraphs from many modules into one merged writeup. Just create the canonical note the first time the concept appears, and link to it thereafter.

**Naming:** Each module is a **folder** `NN - <Original OneNote Page Title>/`. NN = module position. One OneNote page = one module = one folder. Inside the folder, files are numbered `01 - <Topic>.md`, `02 - <Topic>.md`, etc. Split whenever a section grows large enough to deserve its own file — there is no fixed count. The first file is always `01 - Problem Statement.md` when the module starts with a problem.

---

## Agent Extraction Procedure

The user says "convert module 12." The agent does everything below. Never ask the user where something belongs.

**Step 1** — Render the source (OneNote PDFs have no text layer, see [OneNote Conversion](#onenote-conversion)).

**Step 2** — Write the record notes inside `Course/NN - <Title>/`. Start with `01 - Problem Statement.md` if the module opens with a problem, then create additional numbered files (`02 - ...`, `03 - ...`) as sections grow. A module folder can hold as many files as needed — split on natural topic boundaries, not on length alone.

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

**Step 4** — For each hit: search first. If a canonical concept note already exists, **link to it** — do not rewrite or merge new distilled content into it. Create the canonical note only if it does not exist yet.

**Step 5** — Cross-link both directions. Module note lists what it links to under `## Extracted To`. Each concept note lists the modules that reference it under `## Sources`.

**Step 6** — Update `README.md` of every folder touched. Report what was created, updated, skipped.

**Link, don't merge:** the canonical concept note is the single source of truth. Other notes link to it rather than duplicating or re-distilling its content. Only edit a concept note when the concept *itself* needs correcting or extending — not to fold in every module that happens to use it.

**Code:** link the course repo (`github.com/singhsanket143/Design-Patterns`). Inline only the 5–20 line teaching excerpt. Never transcribe boilerplate off screen photographs.

---

## Note Templates

### Pattern note

Two-part progressive structure: **Part 1** gets you *using* the pattern; **Part 2** gives the *architectural judgment*. The two vault-specific tails (Pattern-Specific Applications, Field Notes) and the wiring (Principles Served, Sources) sit after Part 2.

```
---
tags: [lld/patterns/behavioural, status/draft]
created: YYYY-MM-DD
---
# <Name> Pattern

> [!abstract] One sentence: the problem it exists to solve.

**External references:** links (e.g. refactoring.guru concept + language example)

## Part 1 — Core Framework

### Main Purpose
What it does + which principle(s) it promotes.

### Recognition Signal
> [!tip] The cue that says "use this pattern here" — in Akash's own words.
(= "signs you should apply this")

### How to Implement
The ordered steps.

### Key Code
Teaching excerpt only. When the pattern has runtime/environment nuance, split into
the two cases: Plain Java (no singletons) vs Spring Boot (singleton beans). Link repo
for full implementation.

### Beyond the Basics
The one core insight that unlocks the pattern (e.g. runtime polymorphism eliminating
the switch).

### Anti-Patterns & When NOT to Use
Common misuses + the case where the pattern is overkill.

## Part 2 — Architecture Deep Dive

### Pattern Synergy
Which patterns it pairs with and why (e.g. Strategy + Factory, "conservation of complexity").

### Confused With
Table comparing this pattern to the one it is most often mistaken for.

### Real-World Context
Concrete library/framework usages worth recognising.

### Advanced Nuances
Senior-level judgment: boundary decisions, concurrency/thread-safety, trade-offs.

## Pattern-Specific Applications
The vault's own worked cases (e.g. chess cross-combination). Optional.

## Field Notes
Orphan-but-important pointers that don't fit a clean section — kept as standalone
`[!note]` callouts.

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

Each file inside a module folder follows this template. The `# Title` should reflect
the file topic, not just repeat the folder name (e.g., `# Problem Statement`, `# Naive Solution`, `# The Refactor`).

```
---
tags: [source/lld-course, status/done]
created: YYYY-MM-DD
---
# <File Topic>

> [!abstract] What this file covers.

**Course repo:** <link>

## Content
(narrative, walkthrough, observations — whatever belongs here)

## Extracted To
- [[Pattern Name]] — why
- [[Principle Name]] — why
```

Cross-links from accumulators back to a module should point to the specific file:
`[[03 - Simple Rule Engine/01 - Problem Statement]]`.

### HLD topic note

Gold standard: [[HLD/Topics/06 - Caching/06 - Caching]] and [[Netflix HLD]]. Written to **teach on
a cold read**, not to cram: full sentences, one idea per section, `---` between major sections.
Terse crib material goes in the *Interview Lines* section at the end, not in the body.

```
---
tags: [hld/topics, status/draft]
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# <Topic>

> [!abstract] What this is
> Two or three sentences: what the topic is, and the single decision it helps you make.

---

## Why It Exists
The problem that forces this topic into a design. Plain prose.

---

## <Concept sections, in the order you'd explain them>
Each section: a short paragraph first, then a table / list / Mermaid only if it adds something.
Mechanisms get a diagram. Choices get a comparison table with a "pick when" column.

---

## Failure Modes
| Failure | What happens | Mitigation |

---

## Interview Lines
> [!tip] Say it like this
> The 3–6 sentences worth memorising, each one standalone.

## Related Notes
```

Rules:
- Every section opens with prose. No section is only bullets.
- Tables need a column that tells you **when** to pick each option.
- Callouts: `[!tip]` insight · `[!warning]` gotcha · `[!danger]` failure · `[!example]` numbers · `[!info]` context. At most one per section.
- Don't write "SDE-1 slice." Depth is organised by section, not by level.
- Keep every fact from the old note unless it's wrong. Rewriting is for shape, not deletion.

---

## HLD Problems and Mini Designs

This section governs `HLD/Problems/` and `HLD/Topics/12 - Mini Designs/`. It overrides the HLD topic note template for those two folders.

### Ideology

- A real HLD round is not one question with one answer.
    + The interviewer moves the problem while you solve it: scales it 10x and 100x, swaps a requirement, kills a dependency, asks what it costs, or drags you into one component and never lets go.
    + So a note that holds one "final" architecture is useless. A note must hold a **simple working V0**, a **ladder** of upgrades with the number that triggers each one, and a **menu of twist cards** for follow-ups that are not on the ladder.
- **Level: SDE 3 only.** Every note assumes the hardest version of the round.
    + No "SDE 1 / SDE 2 / SDE 3" sections, tags, or comparisons anywhere.
    + Failure, cost and operations are written in, not left for the interviewer to ask.
- **Market: Indian product companies and similar markets.** Spikes are Indian spikes (IPL, Tatkal, Big Billion Day, a Coldplay sale, UPI month-end), and regulation is Indian regulation (RBI, NPCI, TRAI, DPDP) where it changes the design.
- **Two layers, write-once:**
    + **Mini Designs (MDs)** are reusable sub-systems that many problems are assembled from (rate limiter and waiting room, reservation with TTL, saga, hot-key shield, ID generator, baseline scaling, sharding, multi-region...). The generic mechanics, alternatives, numbers and failures live **only** in the MD.
    + **Problem notes** are one per interview question, even when two problems share most MDs. They carry the problem's own requirements, numbers, schema, V0, ladder and twists, and link to MDs instead of re-explaining them.
    + MDs link down to topic notes (`05 - Databases`, `21 - Redis`, `07 - Async Messaging`...) for theory. An MD explains how blocks are wired and tuned, not what Redis is.
- **Estimates exist to force a decision.** Write only the numbers that pick or break something. Each ladder step states the number that breaks the previous version.

> [!danger] Research is mandatory
> Before writing **any** new problem note or MD, do thorough internet research aimed at **SDE 3-level interviews in the Indian market**:
> - Recent interview reports (LeetCode Discuss, GeeksforGeeks, takeuforward, Glassdoor, company prep guides) for how the question is actually asked and which follow-ups come up.
> - Indian engineering blogs and incident write-ups (JioHotstar, Razorpay, PhonePe, Flipkart, Swiggy, Zomato, Zepto, Dream11, IRCTC, BookMyShow, CRED) for real scale numbers and real failure stories.
> - Regulators and official docs (RBI, NPCI, TRAI, DPDP Act, vendor docs) for any rule or number the design depends on.
> - Record every source with its date in the note's `## Sources` section. Never invent a number. If a number is an estimate, say so.

### Files

- MDs: `HLD/Topics/12 - Mini Designs/MD-NN - <Name>.md`. `12 - Mini Designs.md` in the same folder is the index (catalog plus a problem → MD map).
- Problems: `HLD/Problems/NN - <Name>.md`, numbered in creation order. Priority lives in [[Interview Question Priority]], not in the file name.
- Tags: `hld/mini-designs` for MDs, `hld/problems` for problems, plus a `status/*` tag.

### Mini Design template

```
---
tags: [hld/mini-designs, status/draft]
created: YYYY-MM-DD
---
# MD-NN — <Name>

> [!abstract] What this is
> What the block solves, in two or three sentences, and the one decision that defines it.

## 1. What It Solves and Where It Appears
Prose on the problem class, then a table: Problem | How it uses this block (the parameters it sets).

## 2. Knobs
The settings that change between problems (TTL, key shape, consistency level, threshold...), with what each trades.

## 3. Evolution of the Block
The block's own V0 → Vn ladder. Each step: trigger number, what breaks, the change.

## 4. Core Mechanics
Data structures, algorithms, schema, Lua / SQL / pseudocode where it helps. Mermaid for flows.

## 5. Decision Tables
Alternatives with trade-offs and a "pick when" column.

## 6. Numbers to Know
Throughput per node, latencies, memory and storage sizing. Sourced.

## 7. Failure Modes
| Failure | What happens | Detect | Fix |

## 8. Twist Cards
Follow-ups that hit this block in any problem: what the interviewer says → the answer.

## 9. Operations and Cost
Metrics, alerts, the main cost driver.

## 10. Say It in One Minute
The explanation as you'd say it in the round.

## Sources
## Related Notes
```

### Problem note template

```
---
tags: [hld/problems, status/draft]
created: YYYY-MM-DD
---
# <Problem>

> [!abstract] Priority #N. Mini designs: MD-a (role) → MD-b (role) → ... The hard part, in one sentence.

## 1. Clarify Script
Questions to ask, the default assumption for each, and the twist card that applies if the answer differs.

## 2. Requirements
Functional (top 3–5), non-functional (quantified), out of scope.

## 3. Estimates That Drive V0
Only the numbers that pick something.

## 4. Entities, API, Schema

## 5. V0 — Simplest Design That Works
Diagram, core flows, and what V0 can't handle.

## 6. Scale Ladder
### V1 — <name>
Trigger (the number and the maths) → what breaks → the change → the diagram delta → the trade-off → MD link.
(V2, V3, V4...)

## 7. Twist Cards
Problem-specific follow-ups across consistency, features, failures, cost, multi-region, compliance, abuse and Indian-scale spikes.
Each card: what the interviewer says → what it hits → the answer → which version it attaches to.

## 8. Break It
| Failure | What you say |

## 9. Operations and Cost

## 10. One-Minute Close

## Sources
## Related Notes
```

### Writing rules

- Follow the HLD topic note rules above (prose opens every section, "pick when" columns, Mermaid for mechanisms).
- A problem note never re-explains MD mechanics. It states the problem-specific choice and the numbers, then links the MD.
- An MD never carries one problem's numbers as if they were universal. Problem-specific numbers belong in the problem note; the MD gives ranges.
- In a 45-minute round you reach V0 plus one or two ladder steps or twist cards. The rest of the note exists so that whichever way the interviewer turns, the answer is already written.

---

## LLD Problems

This section governs `LLD/Problems/` and `LLD/Code/`. It applies the same ideology as the HLD section above to low-level design and machine coding.

### Ideology

- A real LLD round never stops at the first working version.
    + **Machine coding** (Flipkart, PhonePe, Swiggy, Razorpay): ~90 minutes of working, demoable, in-memory code, then a 30–45 minute review where the interviewer adds requirements live and runs edge cases ("run it with slot 9", "what if two requests arrive together").
    + **LLD discussion** (Amazon, Atlassian, Uber-style): ~45 minutes of requirements, entities, class design, key-method code, a trace, then a burst of "what if we…" extensions.
    + So a note holds a **V0** (what you'd submit at minute 90), an **extension ladder E1–E4** (the change requests interviewers actually make, each showing which classes are added vs edited), and **review cards** for off-ladder questions.
- **Level: SDE 3 only.** No level sections or comparisons. Thread safety is always one of the ladder steps.
- **Market: Indian product companies and similar.** Prefer the prompt wording and CLI formats Indian rounds actually use.
- **Plain Java only.** Machine coding rounds forbid databases and frameworks and expect in-memory, CLI-driven code. Solution code imports only `java.*`. Maven exists only to compile and run JUnit in the vault.
- **Every snapshot is self-contained.** There is no shared library: each `v0` and `full` package contains everything it needs, including the boilerplate you'd type in the round (exception classes, the CLI loop, a demo clock). The code must look like what one candidate writes from scratch, not like something leaning on helpers the interviewer will never see.
- **Problem notes** link to `Patterns/`, `Concurrency/` and `Foundations/` notes for theory instead of re-explaining it, and list the boilerplate files each V0 needs so you know what to type in the first ten minutes.

> [!danger] Research is mandatory
> Before writing **any** new LLD problem, do thorough internet research aimed at **SDE 3-level LLD / machine-coding rounds in the Indian market**: the prompt as actually given (workat.tech, LeetCode Discuss, GeeksforGeeks, takeuforward), the follow-ups reported in reviews, and company guides. Record every source with its date in `## Sources`.

### Code rules

- **Location:** `LLD/Code/src/main/java/lld/<problem>/v0` and `lld/<problem>/full`; tests mirror it under `src/test/java`. The code folder is the source of truth; notes carry only short excerpts and link to files.
- **Two snapshots per problem:**
    + `v0` = exactly what you'd submit at the 90-minute mark: correct, clean, with a CLI demo.
    + `full` = V0 + every ladder extension E1–E4. Each extension's Javadoc says which step introduced it.
- **Every problem ships, inside each package:** a `*App` class with a `CommandLineDriver` demo and `main`; its own `DomainException` hierarchy (only the subtypes it uses: `ValidationException`, `NotFoundException`, `ConflictException`); an injected `java.time.Clock` wherever time matters, with a package-local `ManualClock` for the demo and tests; JUnit 5 tests per extension plus at least one multi-threaded test.
- **No cross-package imports.** A `full` package never imports from its `v0`, and no problem imports from another. Duplication of the small boilerplate is intended.
- **Prefer what you'd type:** a plain `EnumMap<Key, Strategy>` over a custom registry class; a `ScheduledExecutorService` over a wrapper.
- **Java 21** (`maven.compiler.release`), no preview features, `-Xlint:all` clean.
- **Done means:** `.\mvnw.cmd test` passes. Run it before reporting a problem finished.
- **Package root:** `lld`. Money in paise (`long`), never `double`.

### LLD problem note template

```
---
tags: [lld/problems, status/draft]
created: YYYY-MM-DD
---
# <Problem>

> [!abstract] Catalog #N · archetype · patterns. The hard part, in one sentence.

## 1. Clarify Script
Questions, default assumption, which extension fires if the answer differs.
## 2. Requirements
Functional, constraints (in-memory, CLI, thread-safe?), out of scope.
## 3. Entities and Responsibilities
Who owns which rule (data rules in the owning entity, workflow in the orchestrator).
## 4. Class Design
Mermaid classDiagram + short state/behaviour list.
## 5. V0 — the 90-Minute Build
Package layout, key code excerpts, exceptions, CLI demo. Link the v0 package.
## 6. Trace
One scenario, operation by operation.
## 7. Extension Ladder
### E1 — <request>
Interviewer says → classes added / edited → code delta → pattern. (E2, E3, E4; one is concurrency.)
## 8. Review Cards
Off-ladder questions with answers (SOLID, complexity, validations, testing, races, "how would this scale" → HLD link).
## 9. Edge Cases and Tests
| Case | Expected | Test |
## 10. Self-Review Rubric
Naming · SRP · OCP on extensions · exceptions · thread safety · demo runs.
## 11. One-Minute Pitch
## Sources
## Related Notes
```

---

## Formatting

**Frontmatter:** every note gets `tags` and `created`.
Tags: `lld/patterns/behavioural`, `lld/principles`, `source/lld-course`, `status/draft`, `status/mastered`.

**Callouts:** `[!abstract]` open · `[!tip]` key insight · `[!warning]` gotcha · `[!danger]` failure mode · `[!info]` context · `[!example]` worked example · `[!question]` unresolved

**Diagrams:** Mermaid first. `classDiagram` for LLD, `stateDiagram-v2` for state machines, `flowchart TD` default. Node labels 2–4 words. Excalidraw only when structure genuinely cannot be text.

**Prose — complete notes, not revision sheets:**
- Every sentence is a **bullet**. Nested bullets are the hierarchy. Long bullets are fine.
- **Never** a heading followed by a paragraph. Never shrink a note to 20% and call it “structured.”
- Tables, callouts, and diagrams sit *on top of* that outline — they do not replace it.
- These are notes you can learn from the first time, not a cheat-sheet for someone who already knows the topic.

**Nested bullet markers (so the eye can see depth):**
- Level 1: `-` at the left
- Level 2: four spaces, then `+` (deeper indent — Obsidian still draws a disc, but the nest sits further right)
- Level 3: eight spaces, then `-`
- There is no other CommonMark bullet that renders further right. Extra spaces are the only lever.

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
