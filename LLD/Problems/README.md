---
tags: [lld/problems, status/draft]
created: 2026-10-24
updated: 2026-10-27
---
# LLD Problems

> [!abstract] One note per LLD / machine-coding problem, plus working plain-Java code in `LLD/Code`. This README is the **complete procedure** for completing a problem: read it top to bottom before starting one, even with zero other context. `CONVENTIONS.md` points here.

| # | Problem | Catalog | Code |
|---|---|---|---|
| 1 | [[01 - Parking Lot]] | #1, P0 | `lld.parkinglot` |
| 2 | [[02 - Rate Limiter]] | #2, P0 | `lld.ratelimiter` |

Order of the rest: the ranked list in [[Problem Catalog]].

---

## 1. Ideology

### What a real round looks like

- **Machine coding** (Flipkart, PhonePe, Swiggy, Razorpay): ~90 minutes of working, demoable, in-memory code, then a 30–45 minute review where the interviewer adds requirements live and runs edge cases. Often the whole brief (5–8 features) is given upfront.
- **LLD discussion** (Amazon, Atlassian, Uber style): ~45 minutes of requirements, entities, class design, key-method code, a trace, then a burst of "what if we…" follow-ups.
- So each problem is taught as **V0 → extension deltas → full**.

### V0

- V0 is **the common core of every reported version of the question**: what all variants share, the base everything else is built on.
- It is also a **complete, runnable answer on its own**: if the interview stopped there, you'd have answered the base question.
- It is **shaped for the likely extensions** (an interface already in place where the first follow-up will plug in), so each extension adds classes rather than rewriting.
- If most reports include a feature in the *initial* prompt, it belongs in V0, not on the ladder.

### Extension ladder (E1–E4) and "also asked"

- Extensions are **the follow-ups real interviewers actually ask**, chosen by frequency in interview reports, not by what's interesting to design.
- E1–E4 = the four most frequent, in roughly the order they're asked. Each carries an **evidence line** (sources, companies, dates) and a **confidence**: `strong` (several independent reports), `medium` (one report or consistent guide material), `judgement` (no report found; included because the design needs it). Prefer `strong`; `judgement` items are allowed only when stated.
- Concurrency is a ladder step **when the reports support it** (it usually is, for SDE 3).
- Less frequent follow-ups go in an **"Also asked"** list with a one-to-three-line answer each.
- Each extension shows **classes added vs edited**, a small delta diagram (see §4), the code delta and the pattern.

### Level and market

- **SDE 3 only.** No level sections or comparisons.
- **Indian product companies and similar markets.** Use the prompt wording Indian rounds actually use.

---

## 2. Research procedure (mandatory, before writing anything)

1. **Collect the prompt as actually given**: workat.tech / practice statements, LeetCode Discuss, GeeksforGeeks, takeuforward, company guides (TechPrep, PracHub, Hello Interview). Note whether the prompt defines a command-line input format (this decides the driver, §3).
2. **Collect follow-ups from 10–20 dated interview reports.** For each report record company, date, starting prompt, every follow-up asked.
3. **Tally.** Features present in most starting prompts → V0. Most frequent follow-ups → E1–E4 with evidence and confidence. The rest → "Also asked".
4. **Record every source with its date** in the note's `## Sources`, and keep the tally table in the note (§5 *Evidence*).

---

## 3. Code rules (`LLD/Code`)

- **Plain Java only.** Machine-coding rounds forbid databases and frameworks and expect in-memory code. Solution code imports only `java.*`. Maven exists only to compile and run JUnit in the vault.
- **Java 21** (`maven.compiler.release`), no preview features, `-Xlint:all` clean. Money in rupees as `double`, formatted to two decimals for output; in review, say that production code would use `BigDecimal` or paise in a `long` to avoid rounding errors.
- **Two snapshots per problem:** `lld.<problem>.v0` (exactly what you'd submit at minute 90) and `lld.<problem>.full` (V0 + every ladder extension). Javadoc on each class or method says which extension introduced it.
- **Self-contained snapshots.** No shared library, no imports between `v0` and `full`, none between problems. The small boilerplate (exceptions, clock, driver) is duplicated on purpose, because in the round you type it yourself.
- **Folder layout inside each snapshot** (same names everywhere; omit folders a problem doesn't need):

```
lld/<problem>/<v0|full>/
  model/       entities, value objects, enums
  <feature>/   one folder per pluggable concern, named for it (allocation/, pricing/, algorithm/, rules/ ...)
  service/     the orchestrator(s)
  exception/   DomainException + only the subtypes used (Validation, NotFound, Conflict)
  demo/        Demo (plain driver with main) and ManualClock if time matters
  cli/         optional: CommandLineDriver + <Problem>Cli, only when the real prompt uses a command format
```

- **Driver rule:**
    + Default is a **plain `Demo`** in `demo/`: a `main` that builds the service and calls its methods for a short scenario, printing results.
    + Add a **CLI** in `cli/` only when the reported prompt defines input commands and exact output strings (workat.tech style), or says the reviewer will run their own inputs.
    + The domain and service **never read input or print**. They take typed arguments, return results (everything the output needs, e.g. `unpark` returns the `Vehicle`) or throw domain exceptions, and take a `java.time.Clock` when time matters. That makes any solution convertible to a CLI by adding a driver only.
- **Tests:** JUnit 5 under `src/test/java` mirroring the packages. Tests call the service directly (never the driver), one group per extension, at least one multi-threaded test where concurrency is on the ladder, plus a test of the CLI output if a CLI exists.
- **Done means** `.\mvnw.cmd test` passes in `LLD/Code`. Run it before reporting a problem finished.

---

## 4. Diagrams

- One **V0 class diagram**.
- A **delta diagram per extension that changes structure**: only the classes involved, with added classes highlighted (`cssClass "A,B" added` + `classDef added fill:#d4f7d4,stroke:#2e7d32`) and a line listing what was edited rather than added. Skip it for extensions that only add one implementation of an interface already drawn.
- A **sequence diagram** instead of a class diagram for concurrency or multi-step behaviour (races, refunds, retries).
- One **full-version class diagram** after the ladder.

---

## 5. Problem note template

```
---
tags: [lld/problems, status/draft]
created: YYYY-MM-DD
---
# <Problem>

> [!abstract] Catalog #N · archetype · patterns · driver (Demo / CLI, and why). The hard part, in one sentence.

Code links (v0, full, tests). Snapshots are self-contained.

## 1. Clarify Script
Questions, default assumption, which extension fires if the answer differs. Include "command-line input or demo main?" when unclear.
## 2. Requirements
Functional (always stated as service operations, never as CLI commands), Non-functional (concurrency, time source, in-memory, error handling), Out of scope.
If the reported prompt is a command-line version: an "Optional CLI driver" table, command → service call → output.
## 3. Entities and Responsibilities
Who owns which rule (data rules in the owning entity, workflow in the orchestrator).
## 4. Class Design
V0 Mermaid classDiagram.
## 5. V0 — the 90-Minute Build
Folder layout, key excerpts, exceptions, driver; list the boilerplate files to type first.
## 6. Trace
One scenario, operation by operation.
## 7. Evidence
Tally table: Follow-up | Reports (company, date) | Count | Placed as (V0 / E1–E4 / also asked).
## 8. Extension Ladder
### E1 — "<request as interviewers phrase it>"
Evidence + confidence · added / edited · delta diagram · code delta · pattern · tested.
(E2–E4) then the full-version diagram.
## 9. Also Asked
Less frequent follow-ups, one to three lines each.
## 10. Review Cards
Off-ladder questions (SOLID, complexity, validation, testing, "how would this scale" → HLD link).
## 11. Edge Cases and Tests
| Case | Expected | Test |
## 12. Self-Review Rubric
## 13. One-Minute Pitch
## Sources
## Related Notes
```

---

## NotebookLM bundle (made only on request)

NotebookLM can't read `.java` files, so a problem is uploaded as **one Markdown file** holding the note and the code. Bundles are build output, not notes: they live in `LLD/bundles/` (gitignored) and are rebuilt from the sources whenever asked.

**Recipe** (the agent follows this when Akash says "make the NotebookLM bundle for <problem>"):

1. File: `LLD/bundles/<NN> - <Problem>.md`, overwriting any old one.
2. Header: problem name, date built, and one line: "Note first, then V0 code, then full code, then tests. Each file starts with its path."
3. The problem note, `LLD/Problems/<NN> - <Problem>.md`, copied whole.
4. Every file under `LLD/Code/src/main/java/lld/<problem>/v0/`, then `.../full/`, in folder order (`model/`, feature folders, `service/`, `exception/`, `demo/`, `cli/`). Each one as a `### <path from LLD/Code/>` heading followed by its code in a ```java block.
5. The tests under `LLD/Code/src/test/java/lld/<problem>/`, same format.
6. Check the size: one NotebookLM source holds up to 500,000 words, far more than one problem needs.

**Using it:** new notebook → Add source → upload the bundle. Studio → Audio Overview → customise, paste the prompt below, generate. Then choose **Interactive mode → Join** to interrupt and ask questions by voice.

**Audio Overview prompt** (copy everything inside the box):

````text
Explain this low-level design problem step by step, in this exact order, as if teaching someone preparing for an SDE interview:

1. The problem: what we are designing and what makes it hard.
2. The functional requirements, then the non-functional requirements, then what is out of scope.
3. The V0 design: the entities, which class owns which rule, and the class diagram, in plain words.
4. The V0 code: go through the files in order (model, then the strategy and pricing folders, then the service, then exceptions, then the demo). For each important class say what it does, then walk through its key methods line by line in plain words. Finish with a trace of one scenario through the code.
5. The extensions, one at a time (E1, E2, E3, E4). For each: the follow-up as an interviewer would ask it, why interviewers ask it, which classes are added and which are edited, and the code that changes.
6. Only after all extensions: the final full code, how all the pieces fit together, and how it differs from V0.
7. End with the most likely interview follow-up questions and short answers.

Use only the uploaded source. Keep it conversational, but do not skip steps or jump ahead to the final code early.
````

## 6. How to study a problem

1. Read §1–2 of the note only; sketch your own entities and methods for 10 minutes.
2. Read §3–5 and compare. Read the V0 code (model → service → feature folders → demo), then walk §6 against it.
3. For each extension: read only the request line, decide what you'd add vs edit, then read the section and its diagram, then open only the classes it names in `full`.
4. Answer the review cards out loud before reading the answers; finish with the one-minute pitch from memory.

## Related Notes

- [[Problem Catalog]]
- [[LLD/README|LLD]]
