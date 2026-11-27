# HLD

High Level Design notes.

> [!tip] **Interview tomorrow, few hours?** Read [[16 - Estimation and Decision Cheat Sheet]], then walk [[06 - Seat Booking]] or [[13 - URL Shortener]] out loud.

> [!todo] Later: Problem to Solution Map cleanup (`Topics/25 - Problem to Solution Map/`)
> The map files become the core of revision and the entry point into the topic notes. In this order, each step additive (existing content stays as it is):
> 1. Extract anything useful from the question banks (`13 - Question Bank`, `05 - Databases/11 - Interview Questions`, Kafka `08 - Interview Questions`) into the map files in their format, then retire the old question bank.
> 2. Point every **Depth** link at a section (`[[02 - Indexing#Composite indexes]]`), not a whole file.
> 3. Research what's actually asked, add the missing problems, label evidence and confidence, and reorder by ROI.
> 4. Last, for recall practice: reword headings that give the answer away, and fold answers into collapsed callouts.
> 5. Add a "Map entries this problem uses" line to each HLD problem note.

## Topics (building blocks)

[[Topics/README|HLD Topics]] — one folder per topic. Kafka lives under [[07 - Async Messaging]].

| Note | Purpose |
|---|---|
| [[Topics/README]] | 7-day plan and map |
| [[01 - How the Round Works]] | Round script |
| [[02 - Fundamentals]] | CAP, nines, estimates |
| [[03 - Networking and APIs]] | HTTP, realtime, auth |
| [[04 - Load Balancing CDN and Gateway]] | LB, CDN, gateway |
| [[05 - Databases]] | SQL/NoSQL HLD slice. Depth: [[05 - Databases/README]] |
| [[06 - Caching]] | Redis patterns |
| [[07 - Async Messaging]] | Kafka, idempotency |
| [[08 - Reliability]] | Timeouts, rate limit |
| [[09 - Storage Search and Geo]] | S3, ES, geohash |
| [[10 - Distributed Building Blocks]] | Hash ring, IDs, quorum |
| [[11 - Services and Observability]] | Monolith vs MS, telemetry |
| [[12 - Mini Designs]] | Reusable sub-systems (MD-01 … MD-22) |
| [[13 - Question Bank]] | Grill + prompts |
| [[14 - Coordination and Concurrency]] | Locks, leases, fencing, SKIP LOCKED |
| [[15 - Out of Scope]] | Named, not taught this week |
| [[16 - Estimation and Decision Cheat Sheet]] | **Maths + the thresholds that pick the tech** |

## Problems (interview walks)

[[HLD/Problems/README]] — the procedure and the list. Order of work: [[Interview Question Priority]]; this sprint's schedule: [[LLD and HLD Tracker]].

| # | Note |
|---|---|
| 6 | [[06 - Seat Booking]] |
| 13 | [[13 - URL Shortener]] |

New problems are added only after the research in [[HLD/Problems/README]] → *Researching and Prioritising a Problem*.

## Audio Overview Prompts

Upload one note as the NotebookLM source, then Studio → Audio Overview → customise, paste the matching prompt, generate. Use **Interactive mode → Join** to ask questions by voice.

**Topic notes and mini designs** (`Topics/`, `12 - Mini Designs/`):

````text
Explain this system-design note to someone preparing for an SDE-3 interview at an Indian product company, following the note's own order, section by section. Do not reorder, skip or jump ahead.

1. Start with the abstract and "why it exists": the problem at scale that forces this building block into a design.
2. Then each section in order. For each mechanism, describe the request or data flow step by step (client, services, stores, queues), then why it works that way.
3. For every comparison or decision table, explain each option, the trade-off and when to pick it.
4. Keep every number the note gives (throughput per node, latency, storage) and say what decision each number drives.
5. If it is a mini design: walk its evolution ladder in order, for each step the trigger number, what breaks and the change.
6. Cover the Failure Modes: what fails, how you detect it and the fix.
7. End with the Interview Lines and two or three follow-up questions an interviewer would push, with short answers from the note.

Use only the uploaded source. Conversational, but precise.
````

**Problem notes** (`Problems/`): these use a **bundle** (the problem note plus the mini designs it links), built on request, and their own prompt. See [[HLD/Problems/README]] → *NotebookLM bundle*.
