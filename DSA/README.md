# DSA

Data Structures and Algorithms notes.

## Structure

```
DSA/
  Topics/           One folder per topic. Each contains numbered MD files + Indexing/ folder
  Concept Basis/    Concept notes per data structure / technique
  Ratta Sheet/      Standard questions to memorise. One folder per section
                    (Array/, Graph/, ...), files <Section> 1.md, <Section> 2.md, ...
  README.md
```

---

## Ratta Sheet Answer Format

This is the single source of truth for how a Ratta Sheet answer is written. The workflow:

1. Paste the prompt below into Gemini, followed by the question.
2. Iterate with Gemini until the answer is correct.
3. Paste the final answer into `temp.md` and ask the agent to add it. The agent transcribes it **as is** (see *Vault Formatting Rules* below).

### Prompt for Gemini (copy everything inside the box)

````text
Answer the DSA question at the end in EXACTLY this Markdown format. Use C++ for all code.

## [Exact Question Name as pasted] ([Source, e.g. Striver A2Z, Array, Medium])

**Problem Statement:** [the problem in 1–4 sentences, with the input/output names used in the code]

### Brute ([approach name])

**Intuition:** [why this approach works, in plain words, 2–5 sentences]

**Explanation:** [ONLY if needed for this approach: numbered steps of the algorithm]

**Complexity:** **Time:** O(...) — [one-line reason] | **Space:** O(...) — [one-line reason]

**Code:**
```cpp
<complete LeetCode-style C++ solution: includes, using namespace std, class Solution>
```

### Better (<approach name>)

<same four parts as Brute. Explanation only if needed.>

### Optimal (<approach name>)

**Intuition:** <the key insight that makes this optimal>

**Explanation:** <ALWAYS present here: numbered steps, plus a short dry run on a small example>

**Complexity:** **Time:** O(...) — <reason> | **Space:** O(...) — <reason>

**Code:**
```cpp
<complete C++ solution with short comments>
```

Rules:
- Keep the headings, their order and the bold labels exactly as above.
- If there is no meaningful Better approach, write "### Better" followed by "Same as Optimal / not applicable" and one sentence why.
- Explanation is supposed to not just explain the approach but also keep the important points that user discussed and during the discussion thought were important.
- Explanation is mandatory for Optimal; for Brute and Better include it only when asked.
- Use `backticks` for variable names and complexities inside sentences.
- No text before the first heading or after the last code block.

Question:
````

### Vault Formatting Rules (for the agent, not for Gemini)

When adding a pasted answer to a Ratta Sheet file:

- **Do not reword** the explanation text. The words stay exactly as given.
- **Do impose the structure and formatting** if the paste is missing it:
    + `##` for the question, `###` for Brute / Better / Optimal.
    + Bold labels on their own paragraph: `**Problem Statement:**`, `**Intuition:**`, `**Explanation:**`, `**Complexity:**`, `**Code:**`.
    + Complexity on one line as `**Time:** … | **Space:** …`.
    + Code in a ` ```cpp ` block.
    + Numbered lists for algorithm steps that were written as run-on sentences or plain line breaks.
- Missing parts stay missing (don't invent an Intuition Gemini didn't write); note the gap at the end of the question as `> [!question] Missing: Explanation for Optimal`.
- File placement: the section's latest `<Section> N.md`; start `<Section> N+1.md` when the file gets long.

> [!warning] Existing answers need a formatting pass
> Many answers already in `Ratta Sheet/` have the right headings but no bold `Intuition` / `Explanation` / `Complexity` labels, so the parts run together. They should be reformatted to the rules above, without changing any wording. Not done yet; ask for it per section (e.g. "reformat Ratta Sheet Array").
