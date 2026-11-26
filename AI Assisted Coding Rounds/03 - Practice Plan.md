---
tags: [ai-coding-rounds, status/draft]
created: 2026-11-26
---
# Practice Plan

> [!abstract] What this is
> How to get good at AI-assisted rounds without losing the classic no-AI skill most interviews still test: a one-time setup, weekly drills by format, a full mock, and a review checklist. About an hour a day; weight it toward AI-assisted practice only if companies that run the format are on your list.

---

## One-Time Setup

- **Pick your language** (the one you'd use in the interview) and **one tool** (Cursor, Copilot or Claude Code if you bring your own; CoderPad's AI Assist if the company uses it).
- **A template project** with a test runner and one passing test, so setup takes seconds on the day.
- **Agent config reviewed**: turn off auto-run of the whole test suite and anything that edits many files unasked.
- **A fallback** tool or model if yours stalls.
- If the company uses a platform, open its practice pad and learn the layout: file tree, chat panel, test runner, Ask vs Edit mode.

---

## Weekly Rhythm

| Session | Format | What you practise |
|---|---|---|
| 3–4× a week | **Classic, no AI** | Timed LeetCode-style problems, ~20 minutes each including tests and complexity; no autocomplete; trace by hand before running ([[DSA/README|DSA]]) |
| 1–2× a week | **AI-assisted** (rotate the four formats below) | One hour on a fixed timer, recorded, narrating throughout |
| 1× a fortnight | **Mock with a person** | Someone interrupting with "why that structure?" and "walk me through what it wrote" |

- If no company on your list runs AI-assisted rounds, one assisted session a fortnight keeps you current. If Meta, Canva, Stripe or a similar loop is on the list, do two a week.

---

## Drills by Format

### Format 1: DSA with AI turned on

- Take a medium problem with natural follow-ups (expression evaluator, LRU cache, interval merging, rate limiter).
- Rule: you state the approach and complexity **before** prompting; the AI may write the code.
- After the base solution, add two follow-ups yourself (a new operator, a capacity change, concurrency) and handle them in the same way.

### Format 2: Build a feature or small system

- Prompts in the shape companies use (illustrative, not real questions):
    + an airport takeoff and landing scheduler with a simple API and tests;
    + a booking service that prevents double-booking and handles cancellations ([[06 - Seat Booking]]);
    + a rate limiter with configurable policies, then how it runs across servers ([[02 - Rate Limiter]]);
    + a small design-editor model: shapes, layers, undo and redo.
- Spend the first 10 minutes on requirements and a sketch; end with 5 minutes on what production would need. Your LLD notes give you the designs; the drill is directing an assistant to build one.

### Format 3: Staged multi-file task

- Generate or pick a small multi-file project with a **planted bug** and failing tests (ask a model to create one for you, then don't read its answer key).
- Run it as: 10 minutes reading (entry point, data models, one traced test) → fix the bug → add a feature → make it handle 100× the input.
- Practise the Meta pool's shapes once each: BFS through a grid with extra state (keys and doors), backtracking over a word list with a bitmask, a simulation you have to measure over many runs and improve, recommendations on a large graph.

### Format 4: Debug until tests pass

- A service with three to five bugs and a failing suite. Fix one test at a time; after each fix, re-run everything to make sure nothing else broke.
- Use the assistant for narrow questions ("why does this test expect 3 here?"), not "fix everything".

### Reading unfamiliar code (all formats)

- On a small open-source repository, give yourself **10 minutes** to find the entry point, summarise the data models, trace one test through the call path, and find the function that owns a given behaviour. This is the skill that loses most candidates the first part.

---

## Full Mock (do one before any real loop)

- An existing codebase with tests, a bug fix, a feature, an optimisation, an assistant, and you talking the whole time, on a 60-minute timer.
- Afterwards, write down where the minutes went and which part you'd cut.

---

## Review Checklist (after every assisted session)

- [ ] Did I read the code and clarify before the first prompt?
- [ ] Did I state the plan out loud before delegating?
- [ ] Were my prompts for one piece each, with types and constraints?
- [ ] **Lines I accepted without understanding: ____** (target: zero)
- [ ] Did I run a focused test after each piece and the full suite only at the end?
- [ ] Did I explain each part to the interviewer, not just the chat panel?
- [ ] Could I answer "what algorithm did the model implement, and its complexity?" for every part?
- [ ] After a classic session: where was I slow?

---

## Sources

- techinterview.coach, *AI in Coding Interviews: Company Policies and How to Prepare* (November 2026): the combined weekly plan. https://techinterview.coach/blog/ai-coding-interviews
- PracHub, *Meta's AI-Enabled Coding Interview* (November 2026): practice steps and the recurring problems. https://prachub.com/resources/meta-ai-coding-interview
- Thita.ai, *Canva AI-Assisted Coding Interview (2026)*: the 7-day plan and illustrative prompts. https://thita.ai/blog/interview/canva-ai-assisted-coding-interview

## Related Notes

- [[01 - Formats and Company Map]] · [[02 - Grading and the Working Method]] · [[AI Engineer Round Drills]] · [[DSA/README|DSA]]
