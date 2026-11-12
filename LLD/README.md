# LLD

Low Level Design notes.

| Folder | What it covers |
|---|---|
| [[LLD/Concurrency/README\|Concurrency]] | Threads, locks, the Java memory model, concurrent collections, executors, classic concurrent problems |
| [[LLD/Foundations/README\|Foundations]] | Exception handling, value objects, object modelling, state machines, generics, collections, DI and layering, testability |
| `Patterns/` | Design patterns (creational, structural, behavioural) |
| `Sanket Singh Course/` | Course record notes (SOLID, Simple Rule Engine) |
| [[LLD/Problems/README\|Problems]] | Researched LLD / machine-coding problem notes: V0, extension ladder E1–E4, review cards |
| `Code/` | Maven project with all LLD code: two self-contained packages per problem (`v0`, `full`) and JUnit tests. Run `.\mvnw.cmd test` inside it |
| [[Problem Catalog]] | Every LLD problem by archetype, market priority (Oct 2026) and study order |

## Audio Overview Prompts

Upload one note as the NotebookLM source, then Studio → Audio Overview → customise, paste the prompt, generate. Use **Interactive mode → Join** to ask questions by voice. **Problems** use a bundle of note plus code and their own prompt: see [[LLD/Problems/README|Problems README]], "NotebookLM bundle".

**Theory notes** (`Concurrency/`, `Foundations/`, `Patterns/`, `Sanket Singh Course/`):

````text
Explain this low-level-design note to someone preparing for an SDE interview where they must write working Java, following the note's own order, section by section. Do not reorder, skip or jump ahead.

1. Start with the abstract: what problem in real code this idea solves, and what goes wrong without it.
2. Then each section in order. Explain the concept in plain words first, then how it shows up in code.
3. Walk through every code example line by line in plain words: what each line does and why it is written that way. Point out the exact line that would be a bug if written the naive way.
4. For concurrency notes: describe what each thread is doing at each step, where the race or the wait happens, and what makes the fix correct.
5. For pattern notes: the recognition signal (when to use it), how to implement it step by step, what it is confused with, and when it is overkill.
6. For every comparison table, explain each option and when to pick it.
7. End with the Interview Lines and two or three follow-up questions an interviewer would ask, with short answers from the note.

Use only the uploaded source. Conversational, but precise.
````
