---
tags: [ai-coding-rounds, status/draft]
created: 2026-11-26
---
# Grading and the Working Method

> [!abstract] What this is
> What interviewers score in an AI-assisted coding round, and a method to follow in one: how to split the hour, how to prompt, how to verify, how to keep the interviewer in the conversation, and the failure patterns companies name. The method is assembled from Canva's interviewer guide, Meta candidate reports and cross-company prep guides (November 2026).

---

## What Is Scored

- The published criteria agree across companies. **None of them rewards the model's output; they reward what you do around it.**

| Company | What it says it grades |
|---|---|
| **Canva** | Engineering problem-solving (breaking down ambiguity, scope, edge cases); technical depth and **ownership** (understand and explain your code, production standards); AI collaboration effectiveness (right tasks for AI, staying in control, critically refining its output) |
| **Meta** (from candidate reports) | Problem solving, code quality (including understanding what the AI generated), **verification** (run often, test edge cases), communication |
| **Google pilot** (press report) | "AI fluency": prompting, output validation, debugging |

- Put together, five things:
    1. **Decomposition**: you turn a vague task into pieces small enough to delegate or write, and say the plan before prompting.
    2. **Scoped prompts**: one function with its constraints, not "solve the problem".
    3. **Context**: you give the model the right files and the failing test, not a vague complaint.
    4. **Verification**: you read, run and test what comes back, and catch what it missed.
    5. **Ownership and communication**: you can explain every line, and you narrate decisions as you go.

### What a strong vs weak prompt looks like

| | Strong | Weak |
|---|---|---|
| **Question** | "In `poll()`, what's returned when the queue is empty? Point to the exact line." | "Is anything wrong with this code?" |
| **Task** | "Implement only the visited-state helper using the existing `Node` type. Don't change the public interface." | "Solve part 2." |
| **Context** | Attaches `queue.go` and the failing test output | "Something failed, fix it." |

---

## The Method for a 60-Minute Round

| Minutes | Phase | What you do |
|---|---|---|
| 0–8 | **Read and clarify** | Read the problem and the existing code **before** prompting anything: entry point, data models, one test traced through the call path. Restate the task, ask about constraints and input sizes |
| 8–12 | **Plan out loud** | The approach and its complexity, the pieces, which ones the AI will write. Get a nod |
| 12–45 | **Build in narrow loops** | Per piece: prompt → **read the diff** → fit it in → run a **focused** test → say what it does. Repeat |
| 45–52 | **Harden** | Edge cases, the larger inputs, a quick benchmark if performance matters |
| 52–60 | **Discuss** | What you built and why, what you'd improve, how it scales, what production would need |

- In a staged task (Meta-style), budget per part and move on; explaining the approach for a part you can't finish still earns credit.
- **Look at the test data before optimising.** The large datasets are what break a first solution, and they tell you which optimisation is wanted (bitmask, memoisation, a trie, a different algorithm).

### The narrow loop, in detail

1. **Decide the piece yourself** ("a BFS whose state is `(position, keys held)`").
2. **Prompt for that piece only**, naming the types and the constraint.
3. **Read every line** before accepting. If it's too clever to explain, ask for a simpler version or write it yourself.
4. **Run a targeted test**, small sample first; the full stress suite **last**, because sandboxes are small and agents sometimes start it themselves.
5. **Say what changed and why** to the interviewer, not just to the chat panel.

### Verification habits that score

- Run the code often; don't stack three unverified AI changes.
- Write or ask for **one edge-case test** per piece (empty input, a single element, the boundary).
- When the model says it changed a file, **check the file**; tools sometimes report a timeout when the edit went through, or success when it didn't.
- Re-read the code after any multi-file edit for side effects on things you didn't ask it to touch.

---

## Keeping the Interviewer in the Loop

- The commonest silent failure is **talking only to the AI**: reading the model's answer without saying anything. Narrate: "I'm asking it for the helper only; I'll check it handles the empty grid."
- When the interviewer asks you a question, **answer it yourself first**, then use the AI if needed.
- If the assistant stalls or gives poor answers, say so, switch models (or write it yourself), and keep going. That is a judgement signal, not a failure.

---

## Failure Patterns

| Pattern | What it looks like | Instead |
|---|---|---|
| **Skipping the read** | Prompting before understanding the code; 30 minutes lost on part 1 | Ten minutes reading: entry point, models, one traced test |
| **Hands-off** | Pasting the whole problem; becoming a proxy between interviewer and model | You choose the approach; the AI writes pieces you've specified |
| **Blind acceptance** | Passing tests, but you can't explain the code | Read every line; rewrite what you can't explain |
| **Feature marathon** | Racing through features, accepting output quickly | Fewer features, reviewed and tested |
| **AI showcase** | Demonstrating prompting tricks instead of solving the task | The engineering task is the point; the tool is incidental |
| **Too manual** | Ignoring the assistant and typing everything | Delegate boilerplate, data structures you've chosen, tests and benchmarks |
| **Running the big suite early** | The sandbox hangs on stress tests | Small sample first, full suite last |
| **Chasing optimal first** | No time left for follow-ups | Simple first, state its complexity, then improve |

---

## Sources

- Canva Engineering, *AI Interview Success: An Interviewer's Inside Guide* (October 2025), as summarised by Thita.ai. https://www.canva.dev/blog/engineering/ai-interview-success/ · https://thita.ai/blog/interview/canva-ai-assisted-coding-interview
- PracHub, *Meta's AI-Enabled Coding Interview* (November 2026). https://prachub.com/resources/meta-ai-coding-interview
- Timur Tibeyev, *The 4 Types of AI-Assisted Coding Interviews* (October 2026): AI-fluency signals and prompt examples. https://bigtechdream.substack.com/p/the-4-types-of-ai-assisted-coding
- techinterview.coach, *AI in Coding Interviews* (November 2026): grading across companies, Google pilot. https://techinterview.coach/blog/ai-coding-interviews

## Related Notes

- [[01 - Formats and Company Map]] · [[03 - Practice Plan]] · [[AI Assisted Coding Rounds/README]]
