---
tags: [ai-coding-rounds, status/draft]
created: 2026-11-26
---
# Formats and Company Map

> [!abstract] What this is
> The four shapes an AI-assisted coding round takes, which companies are reported to run which, what the environment looks like, and where the Indian market stands. Everything here is dated November 2026; policies change by quarter, so confirm with the recruiter.

> [!warning] How solid is this?
> Company statements (Canva, Anthropic, Datadog) are firm. Press reports (Meta, Google, Amazon) describe internal documents at one point in time. Candidate reports (most of the format detail) are individual experiences. Each line says which kind it is.

---

## The Four Formats

Grouped from candidate reports on LeetCode, Reddit, Blind and Glassdoor by an engineer who helped run Canva's AI-assisted interviews (October 2026). The companies listed are where candidates **reported** each format, not official programmes.

| Format | What you get | Where the signal is | Reported at |
|---|---|---|---|
| **1. DSA with AI turned on** | A LeetCode-style problem plus an assistant; you still propose the approach, the AI may write the code | The **follow-ups**: the interviewer adds requirements or changes the problem after each iteration | TikTok, Snap, LinkedIn |
| **2. Build a feature or small system** | An ambiguous product problem with several iterations | Scoping, design decisions, code quality, production-readiness | Shopify, Netflix, Stripe |
| **3. Staged multi-file task** | A small existing project with tests; stages: fix failing tests → implement a feature → optimise | Reading unfamiliar code, verifying, recognising the right optimisation | Meta, Snap, Google |
| **4. Debug until tests pass** | A service or web app with several bugs and failing tests | Diagnosis, narrow fixes, not breaking other things | Atlassian, Amazon |

- Example of format 1: evaluate an arithmetic expression with `+ - * /` and parentheses; follow-ups add unary minus, integer division with negatives, then variables from a map. Start simple, state complexity, leave time for the follow-ups, where most of the signal is.

---

## Company Map (November 2026)

| Company | Policy | Source type |
|---|---|---|
| **Meta** | One **AI-enabled coding round** in the onsite (replacing one of two coding rounds); the other coding round has no AI. Some 2026 loops reported two AI-enabled rounds and no classic one | Press report (July 2025) + candidate reports since October 2025 |
| **Canva** | AI **expected** in technical interviews for backend, frontend and ML; replaces the CS fundamentals screen for backend and frontend | Company statement (June 2025) and interviewer guide (October 2025) |
| **Google** | Mostly no AI; a **pilot "code comprehension" round with Gemini** for early and mid-career roles on selected US teams from H2 2026; also at least one in-person round | Press reports (2025, May 2026) |
| **Amazon** | Live interviews: no generative AI **unless explicitly permitted**; some technical **assessments** include an interactive AI assistant | Press report (March 2025) + Amazon article (January 2026) |
| **Stripe** | An AI coding round on the platform's assistant, reported with a **rule-engine** task | Candidate reports (PracHub) |
| **Atlassian** | Mixed: of 12 reports in one bank, one described an AI-assisted bug-fix round and one a screen that **banned** AI | Candidate reports (PracHub, November 2026) |
| **Anthropic** | No AI in live interviews or take-homes unless told otherwise | Company statement (July 2025) |
| **Datadog** | No AI in live coding or assessments unless told; some roles have an AI coding interview announced in advance | Company statement (May 2026) |
| **Cognition** | Not allowed | Press report (May 2026) |

- Lists online that assign a policy to dozens more companies usually give no source. For anything not above, the recruiter is the only reliable source.

---

## Meta's Round in Detail (the best-documented one)

From 35 candidate reports in PracHub's bank (counted 2 November 2026) plus CoderPad's documentation.

- **Environment:** CoderPad, a **multi-file project you didn't write**, runnable tests, and an AI Assist panel. CoderPad has an **Ask** mode (chat only, no write access) and an **Edit** mode (can change files); reports describe both kinds of use.
- **Models:** you can switch during the round; reports name GPT-5, Claude Sonnet and Opus, ChatGPT and Gemini. Two candidates had a model stop answering mid-round. **If it stalls, switch instead of waiting.**
- **Shape:** one extended problem in parts that build on each other (most maze reports count four):

| Part | Task | AI use |
|---|---|---|
| 1 | **Fix a bug** in the existing code (a BFS with no visited set, a printer that overwrites symbols, a test inserting into a map while iterating) | Sometimes **switched off** (3 candidates) |
| 2 | **Core implementation**: the main algorithm (BFS through a maze, backtracking over a word list) | Expected; you choose the approach, the AI writes it |
| 3+ | **Extend or optimise**: a new rule (one-way gates, keys and doors) or larger inputs that break part 2 (thousands of words) | Useful for implementing and **benchmarking** two approaches |

- **The pool is small:** of 32 reports naming the problem, 30 drew one of four: a maze solver (15), maximum unique characters from a word list (10), a sum-to-15 card game (7), friend recommendations (2). New ones appear, so learn the format, not the answers.
- **After each part, expect "what algorithm did the model actually implement?"** One candidate pasted ~150 lines of AI-written dynamic programming that passed, then stumbled explaining it.
- **Not finishing every part is survivable** (an offer after reaching part 3 of 4), but don't prepare only the easy version.
- **Sandbox limits:** one candidate who sat several companies' AI rounds reported sandboxes of about 1.5 CPU cores and 2 GB, and that newer assistants **run the test suite on their own**, which can hang the machine if the full stress suite runs before you've optimised.
- A classic coding round (two LeetCode-style problems, no AI, ~20 minutes each) often sits in the same loop.

## Canva's Round in Detail

- **Tools:** your own AI-integrated IDE (Canva names Copilot, Cursor and Claude). Set up a project skeleton in your language beforehand.
- **Problems:** realistic, ambiguous, not solvable in one prompt. Canva's published example: "Build a control system for managing aircraft takeoffs and landings at a busy airport" (it replaced Conway's Game of Life).
- **Phases:** planning (requirements, assumptions, sketch) → development (AI for skeletons, unfamiliar APIs, routine code; pause on complex suggestions) → discussion (decisions, trade-offs, what production needs).
- **Graded:** engineering problem-solving, technical depth and ownership, AI collaboration effectiveness.
- **Named mistakes:** "AI showcase", "feature marathon", "hands-off" ([[02 - Grading and the Working Method]]).

---

## Environments and Tools

| Setup | What it means | Prepare by |
|---|---|---|
| **Built into the platform** (CoderPad, HackerRank) | Only the models the platform offers; nothing to install | Learning the layout: file tree, chat panel, test runner, Ask vs Edit |
| **Bring your own tools** (screen share) | Your own IDE and agent | An empty project with a test runner, agent config reviewed, distracting features off, and a **fallback** tool if yours stalls |

- Subagents, plugins and skills are generally **not** part of the rubric yet; a few companies do look at coding-agent effectiveness.

---

## The Indian Market

- Most Indian product companies and IT services firms **restrict** AI in interviews and are tightening checks. Intuit India encourages AI tools on the job (teams report delivering code about 40% faster) but restricts them in interviews (Financial Express, 2026). Employers in India including Deloitte and Deutsche Bank have added monitoring and scenario-based assessments to counter AI misuse (Economic Times, 2026).
- At the same time, leaders screen for AI fluency in conversation: Razorpay's CEO was reported asking every interviewee about their use of AI tools, finding only about a third familiar enough (The Ken, 2025).
- Where AI-assisted rounds appear for India-based roles, it's mostly through **global companies' standard loops** (Atlassian, Stripe, Canva, Meta and similar).
- Practical upshot for you: prepare **classic no-AI rounds as the default**, practise the AI-assisted format for the companies on your list that run it, and have a crisp answer to "how do you use AI tools in your work?" for everyone else.

---

## Sources

- techinterview.coach, *AI in Coding Interviews: Company Policies and How to Prepare* (November 2026), including the Karat survey (January 2026). https://techinterview.coach/blog/ai-coding-interviews
- PracHub, *Meta's AI-Enabled Coding Interview: What to Expect in 2026* (35 candidate reports, counted November 2026). https://prachub.com/resources/meta-ai-coding-interview
- Thita.ai, *Canva AI-Assisted Coding Interview (2026)*, summarising Canva's posts. https://thita.ai/blog/interview/canva-ai-assisted-coding-interview · Canva Engineering, *Yes, You Can Use AI in Our Interviews* (June 2025). https://canva.dev/blog/engineering/yes-you-can-use-ai-in-our-interviews · *AI Interview Success: An Interviewer's Inside Guide* (October 2025). https://www.canva.dev/blog/engineering/ai-interview-success/
- Timur Tibeyev, *The 4 Types of AI-Assisted Coding Interviews* (October 2026). https://bigtechdream.substack.com/p/the-4-types-of-ai-assisted-coding
- PracHub, *Atlassian AI-Assisted Coding Interview Guide 2026*. https://prachub.com/resources/atlassian-ai-assisted-coding-interview-guide-2026-repository-tasks-agent-use-and-values · *Stripe AI Coding Interview*. https://prachub.com/resources/stripe-ai-coding-interview-how-the-round-works-and-how-to-prepare
- CoderPad, *AI Assist* interview documentation. https://coderpad.io/resources/docs/interview/pads/interview-ai-assist/
- Financial Express, *Tech hiring split over use of AI tools in coding interviews* (2026). https://www.financialexpress.com/artificial-intelligence/tech-hiring-split-over-use-of-ai-tools-in-coding-interviews/4131548/
- Economic Times, *Corporate India closing interview doors to prying AIs* (2026). https://economictimes.indiatimes.com/jobs/hr-policies-trends/corporate-india-closing-interview-doors-to-prying-ais/articleshow/131296422.cms
- The Ken, *What happens when Indian unicorns go shopping for AI tools?* (May 2025). https://the-ken.com/story/what-happens-when-indian-unicorns-go-shopping-for-ai-tools/

## Related Notes

- [[AI Assisted Coding Rounds/README]] · [[02 - Grading and the Working Method]] · [[03 - Practice Plan]]
