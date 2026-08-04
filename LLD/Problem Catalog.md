---
tags: [lld/catalog, status/draft]
created: 2026-08-04
---
# LLD Problem Catalog — Indian SDE Market

> [!abstract] Exhaustive catalog of LLD / machine-coding problems asked in Indian SDE interviews (SDE-1 and above), organised by **structural archetype** rather than domain. The archetype is what interviewers actually vary; domains are reskins. If every archetype below is covered, no problem can surprise you — even one not on this list.

## How to read this

**Priority**
- `P0` — asked constantly across many companies. Non-negotiable.
- `P1` — asked regularly, or the cheapest way to cover a unique archetype.
- `P2` — long tail. Domain-specific or a reskin of something already covered. Do if time permits.

`[LLD Course Question]` marks problems from the course. These are fixed and never dropped regardless of priority.

**Tier assignment (must-code vs architect-via-dictation) is deliberately not in this file yet** — that's the next pass.

---

## 1. State Machines & Lifecycle

> [!tip] Recognition signal: the object moves through named states, transitions are restricted, and behaviour changes per state. Interviewer will ask "what if we add a new state?"

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design a Vending Machine | State, Factory | Amazon, Flipkart, Walmart |
| P0 | Design an ATM System `[LLD Course Question]` | State, Chain of Responsibility | Widely asked |
| P0 | Design an Elevator System | State, Strategy, Command | Adobe, Amazon, Uber |
| P0 | **Order Management System** *(new)* | State, Observer | Meesho, PhonePe, Amazon |
| P1 | **Order Fulfilment / Delivery Tracking** *(new)* | State, Observer | Flipkart, Swiggy |
| P1 | **Workflow / Approval Engine (multi-level approval)** *(new)* | State, Chain of Responsibility | Rippling, Razorpay, SaaS |
| P1 | Design a Traffic Signal Control System *(new)* | State, Observer | Amazon |
| P2 | **Coffee Vending Machine** *(new)* | State | Flipkart |
| P2 | **Bug Bounty Program Management** *(new)* | State, Observer | Flipkart |

---

## 2. Pluggable Policy / Strategy Engines

> [!tip] Recognition signal: a finite family of interchangeable algorithms solving the same problem, swapped at runtime, without the caller knowing. The single most common LLD archetype.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design a Parking Lot `[LLD Course Question]` | Strategy, Factory, Singleton | Uber, Flipkart, Salesforce, Swiggy |
| P0 | **Billing & Discount / Promotions Engine** *(new)* | Strategy, Decorator | **Flipkart (#1), Amazon, Meesho** |
| P0 | Simple Rule Engine (Specification/Interpreter) `[LLD Course Question]` | Specification, Interpreter | Rippling |
| P0 | Design an In-Memory Cache with Multiple Eviction Policies `[LLD Course Question]` | Strategy, Observer | PhonePe, Kutumb |
| P1 | **Customer Loyalty / Rewards Program** *(new)* | Strategy, Observer | Flipkart |
| P1 | **Ride Surge Pricing Engine** *(new)* | Strategy, Observer | Uber, Ola |
| P1 | **Tax Calculation Engine** *(new)* | Strategy, Composite | Fintech |
| P1 | **Feature Flag / A-B Testing Framework** *(new)* | Strategy, Singleton | Wingify, SaaS |
| P2 | **Recommendation / Ranking Policy** *(new)* | Strategy | Meesho, ShareChat |

---

## 3. Rule & Expression Evaluation

> [!tip] Recognition signal: user-supplied logic must be parsed, composed, and evaluated. Nested AND/OR. This is where Interpreter and Composite meet.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Simple Rule Engine `[LLD Course Question]` | *see above* | Rippling |
| P1 | **Expression Evaluator / Calculator** *(new)* | Interpreter, Composite | Amazon, Microsoft |
| P1 | **Spreadsheet / Excel Formula Engine** *(new)* | Interpreter, Observer, DAG | Atlassian, Microsoft |
| P1 | **Log Query Engine** *(new)* | Interpreter, Strategy | **Flipkart (recent)** |
| P2 | **Regex / Pattern Matcher** *(new)* | Interpreter, State | Google, Microsoft |
| P2 | **JSON Parser / Serialisation Library** *(new)* | Interpreter, Builder | BrowserStack |
| P2 | **Search Autocomplete / Typeahead** *(new)* | Trie, Strategy | Amazon, Swiggy |

---

## 4. Matching, Ordering & Allocation

> [!tip] Recognition signal: two sides of a market, or scarce resources, must be paired under a priority rule. Heaps and comparators everywhere. Concurrency almost always in scope.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Stock Exchange Order Matching Engine `[LLD Course Question]` | Strategy, Observer | Upstox, Groww, Zerodha |
| P0 | Design Uber / Ride-Sharing System | Strategy, Factory, Observer | Uber, Ola, Flipkart |
| P0 | Online Auction System `[LLD Course Question]` | Strategy, Observer, State | Flipkart |
| P1 | Design a Task Scheduler (Cron Job) | Strategy, Command | Uber |
| P1 | Design a Real-Time Leaderboard System | Strategy, Observer | Dream11, Gaming |
| P1 | **Peer-to-Peer Parcel Delivery** *(new)* | Strategy, Observer | Flipkart |
| P1 | **Load Balancer** *(new)* | Strategy | Uber, Infra roles |
| P2 | **Airline Seat Allocation** *(new)* | Strategy, State | Travel |
| P2 | **Warehouse / Inventory Allocation** *(new)* | Strategy | Flipkart, Meesho |

---

## 5. Hierarchy & Composite Structures

> [!tip] Recognition signal: a tree where leaf and container must be treated uniformly. "Add a folder inside a folder."

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design a File System (Unix) | Composite, Strategy, Visitor | Amazon, Microsoft |
| P1 | **File Search System (filters, nested)** *(new)* | Composite, Strategy | Amazon, Microsoft |
| P1 | **Trello / Jira Task Management** *(new)* | Composite, Observer, State | **Atlassian**, Flipkart |
| P1 | Design a Library Management System | Composite, State | TCS, Infosys, Amazon, Flipkart |
| P1 | Cap Table Generator `[LLD Course Question]` | Composite, Visitor | Fintech |
| P2 | **Organisation / Employee Hierarchy (HRMS)** *(new)* | Composite | Rippling, Darwinbox |
| P2 | **Survey / Form Builder** *(new)* | Composite, Builder | Google, SaaS |
| P2 | **Menu / Category Tree (nested catalog)** *(new)* | Composite | Swiggy, Zomato |

---

## 6. Event Fan-Out & Observers

> [!tip] Recognition signal: one thing changes and many dependents must be told, with the publisher not knowing who is listening.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design a Pub/Sub & In-Memory Messaging Queue `[LLD Course Question]` | Observer, Strategy | **Uber**, Flipkart |
| P0 | Design a Notification System | Observer, Strategy, Factory | Flipkart, Swiggy |
| P0 | Data Stream Consumer `[LLD Course Question]` | Observer, Strategy | Course |
| P1 | Movie Content Management System (Netflix CMS) `[LLD Course Question]` | Observer, State | Course |
| P1 | **Audit Log / Activity Feed** *(new)* | Observer, Command | SaaS, Fintech |
| P1 | **Chat Application (WhatsApp / real-time)** *(new)* | Observer, Mediator | Zepto, Meesho |
| P1 | **Simplified Twitter / Social Feed** *(new)* | Observer, Factory | AngelOne, ShareChat |
| P2 | **Smart Home / IoT Device Control** *(new)* | Observer, Command | Samsung, IoT |
| P2 | Design StackOverflow | Observer, State | Widely asked |
| P2 | **Social Network / Friend Graph (LinkedIn)** *(new)* | Observer, Graph | LinkedIn, Meesho |

---

## 7. Concurrency Primitives & Thread-Safe Resources

> [!danger] This is the most under-prepared category and the one that separates SDE-2 from SDE-1. Fintech and trading companies (PhonePe, Razorpay, Groww, Upstox) lean on it heavily. Your original list had only the Rate Limiter here.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design a Rate Limiter (Multi-Threaded) | Strategy, Singleton | **Uber, Flipkart** |
| P0 | **Thread Pool / Executor Service** *(new)* | Object Pool, Command | Uber, PhonePe |
| P0 | **Producer–Consumer Bounded Buffer** *(new)* | Monitor, Observer | Fintech, Trading |
| P1 | **Connection Pool** *(new)* | Object Pool, Singleton | Razorpay, Backend roles |
| P1 | **Circuit Breaker (Hystrix-style)** *(new)* | State, Proxy | Razorpay, PhonePe |
| P1 | **Retry with Exponential Backoff** *(new)* | Strategy, Decorator | Infra, Fintech |
| P1 | **Read-Write Lock / Concurrent Map** *(new)* | Monitor | Trading, Arcesium |
| P2 | **Distributed ID Generator (Snowflake)** *(new)* | Singleton, Strategy | Infra |
| P2 | **Distributed Lock (single-node LLD)** *(new)* | Singleton, State | Fintech |

---

## 8. Caching & Storage Internals

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | In-Memory Cache with Multiple Eviction Policies `[LLD Course Question]` | Strategy, Observer | **PhonePe** |
| P1 | **Multi-Level Cache (pluggable eviction per level)** *(new)* | Strategy, Chain of Responsibility | **PhonePe** |
| P1 | Design a Key-Value Store | Strategy, Singleton | Infra, Databases |
| P1 | Design a URL Shortener (Bitly) | Strategy, Singleton | Widely asked |
| P2 | **Browser History Simulator** *(new)* | Memento, Iterator | Easy warm-up |
| P2 | **In-Memory Database with Transactions** *(new)* | Memento, Command | Arcesium, Databases |

---

## 9. Booking & Reservation Systems

> [!warning] Heavily redundant category. These are all the same archetype — inventory, hold, confirm, concurrency on double-booking. Do two or three properly; the rest are reskins you can architect in minutes.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design BookMyShow (Movie Booking) `[LLD Course Question]` | State, Strategy, Observer | Swiggy, Paytm, DoorDash |
| P0 | Design Swiggy / Food Delivery System | State, Strategy, Observer | Swiggy, Zomato, Flipkart |
| P1 | Design Hotel Booking / Airbnb | Strategy, State | Widely asked |
| P1 | Design a Meeting Scheduler (Google Calendar) | Strategy, Observer | Flipkart, Razorpay, Groww |
| P1 | Design a Car Rental System (ZoomCar) | Strategy, State | Widely asked |
| P1 | **Doctor Appointment Booking (Practo)** *(new)* | Strategy, State | **Flipkart** |
| P1 | **Gym / Fitness Slot Booking** *(new)* | Strategy, State | **Flipkart** |
| P2 | **Restaurant Table Reservation** *(new)* | Strategy, State | Zomato, Dineout |
| P2 | **IRCTC Train Reservation** *(new)* | Strategy, State | Indian-specific |
| P2 | **Course Registration System** *(new)* | Strategy, State | EdTech |
| P2 | **Amazon Locker System** *(new)* | Strategy, Factory, State | **Amazon** |
| P2 | **Conference Room Management** *(new)* | Strategy | Flipkart |

---

## 10. Money Movement, Ledgers & Fintech

> [!info] India-heavy. PhonePe, Razorpay, Paytm, Cred, Groww, Upstox, Navi all draw from here, and they weight thread safety and transaction correctness above extensibility.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design a Payment Gateway System `[LLD Course Question]` | Strategy, State, Adapter | Razorpay, PhonePe |
| P0 | **Digital Wallet (Paytm / PhonePe / Flipkart Wallet)** *(new)* | State, Command | **Flipkart, PhonePe, Paytm** |
| P0 | Scalable Banking System (Ops & Top Spender Ranking) `[LLD Course Question]` | Strategy, Observer | Widely asked |
| P0 | Design Splitwise `[LLD Course Question]` | Strategy, Factory, Observer | ShareChat, Razorpay, Flipkart, Paytm, Swiggy |
| P1 | Stock Broker System (Zerodha / Groww) `[LLD Course Question]` | Strategy, Observer, State | Groww, Zerodha, Upstox |
| P1 | **Double-Entry Ledger / Accounting Core** *(new)* | Command, Memento | Razorpay, Cred |
| P1 | **Buy Now Pay Later (BNPL)** *(new)* | State, Strategy | **Flipkart**, Cred |
| P1 | **Refund & Settlement System** *(new)* | State, Command | Razorpay, PhonePe |
| P2 | **Loan / EMI Scheduling System** *(new)* | Strategy, Builder | Navi, Cred |
| P2 | **Insurance Policy Management** *(new)* | State, Strategy | Insurtech |
| P2 | **UPI Payment Flow** *(new)* | State, Chain of Responsibility | Indian-specific |

---

## 11. Access Control & Identity

> [!danger] Entirely absent from your original list, and it is asked far more than its reputation suggests — especially at B2B SaaS and anywhere multi-tenancy exists. Rippling, Atlassian, Freshworks.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | **RBAC / Permission System** *(new)* | Composite, Strategy, Proxy | Atlassian, Rippling, SaaS |
| P1 | **Authentication / Session & Token Management** *(new)* | Strategy, State | Widely asked |
| P2 | **Multi-Tenant Resource Isolation** *(new)* | Proxy, Strategy | B2B SaaS |
| P2 | **API Key & Quota Management** *(new)* | Strategy, Proxy | Infra, SaaS |

---

## 12. Command, Undo & Interception Pipelines

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Text Editor (Undo/Redo Engine) `[LLD Course Question]` | Command, Memento | Widely asked |
| P0 | Middleware Framework `[LLD Course Question]` | Chain of Responsibility, Decorator | Widely asked |
| P1 | Design an API Gateway / Router | Chain of Responsibility, Proxy | Infra |
| P1 | **Food Order Management using Commands** *(new)* | Command, State | **Flipkart** |
| P2 | **Terminal / Shell Command Parser** *(new)* | Command, Interpreter | Amazon |
| P2 | **CI/CD Pipeline Engine** *(new)* | Command, Chain of Responsibility | DevOps roles |

---

## 13. Object Construction & Copying

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Builder Pattern: Itinerary & HTTP Request `[LLD Course Question]` | Builder | Course |
| P1 | Object Cloning Utility `[LLD Course Question]` | Prototype | Course |
| P1 | Design Amazon / E-Commerce System `[LLD Course Question]` | Builder, Strategy, Composite | Amazon, Flipkart |
| P2 | **Dependency Injection / IoC Container** *(new)* | Factory, Singleton, Reflection | Atlassian, advanced |
| P2 | **ORM / Query Builder** *(new)* | Builder, Interpreter | Databases, Backend |

---

## 14. Games & Simulations

> [!tip] Games are how interviewers test pure OOP modelling with zero domain knowledge required. Board games cluster tightly — do Chess properly and most others collapse.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design Tic-Tac-Toe `[LLD Course Question]` | Strategy, State | Warm-up standard |
| P0 | Design Snake & Ladder | Factory, Strategy | **PhonePe**, Amazon, Adobe, Swiggy |
| P0 | Design a Chess Game `[LLD Course Question]` | Strategy, Factory, Command | Zepto, widely asked |
| P1 | Design Battleship Game `[LLD Course Question]` | Strategy, State | Course |
| P1 | **Snake Game (single-player, grid)** *(new)* | State, Strategy | **Atlassian** |
| P1 | **Deck of Cards / Card Game Engine** *(new)* | Factory, Strategy | Amazon, Adobe |
| P2 | **Minesweeper** *(new)* | Strategy, Flyweight | Amazon, Microsoft |
| P2 | **Sudoku Validator / Solver** *(new)* | Strategy | Amazon |
| P2 | **Ten-Pin Bowling Scorer** *(new)* | State, Strategy | Amazon, ThoughtWorks |
| P2 | **Connect 4** *(new)* | Strategy, State | Adobe |
| P2 | **CricInfo / Cricket Scoreboard** *(new)* | Observer, State | Indian-specific, Dream11 |

---

## 15. Framework & Library Design

> [!tip] Recognition signal: the deliverable is something *other developers* consume. The interviewer is testing API design taste and extension points, not domain modelling.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Modular Logging Framework (log4j style) `[LLD Course Question]` | Chain of Responsibility, Strategy, Singleton | **Amazon**, widely asked |
| P1 | **Unit Test Framework (JUnit-style)** *(new)* | Template Method, Observer | BrowserStack, Atlassian |
| P1 | **Validation Framework** *(new)* | Specification, Chain of Responsibility | SaaS, Fintech |
| P2 | **Config Management Library** *(new)* | Singleton, Strategy, Builder | Infra |
| P2 | **Object Mapper / DTO Mapper** *(new)* | Strategy, Reflection | Backend roles |
| P2 | **Web Crawler (LLD flavour)** *(new)* | Strategy, Observer | Amazon |

---

## 16. Misc Domain Problems (long tail)

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P1 | **Customer Issue Resolution / Helpdesk Ticketing** *(new)* | Strategy, Observer, State | **PhonePe, Flipkart**, Freshworks |
| P1 | **Inventory Management System** *(new)* | State, Observer | **Flipkart**, Meesho |
| P2 | **Online Coding Platform** *(new)* | Strategy, State | Flipkart |
| P2 | **Music Streaming / Playlist (Spotify)** *(new)* | Iterator, Strategy, Composite | Widely asked |
| P2 | **Leave / Attendance Management** *(new)* | State, Strategy | Rippling, Darwinbox |
| P2 | **Voting / Polling System** *(new)* | Strategy, Observer | Misc |
| P2 | **Quiz / Online Exam Platform** *(new)* | State, Strategy | EdTech |
| P2 | **Dating App (matching + swipe)** *(new)* | Strategy, Observer | Flipkart |
| P2 | **Vehicle Service Centre / Workshop** *(new)* | State, Strategy | Misc |

---

## Coverage Audit

Use this to check exhaustiveness by **pattern**, not by problem count. If a pattern has no `P0`/`P1` problem next to it, that's a real gap.

| Pattern | Covered by |
|---|---|
| Strategy | Parking Lot, Discount Engine, Cache, Rate Limiter |
| State | Vending Machine, Order Management, ATM, Wallet |
| Factory / Abstract Factory | Parking Lot, Snake & Ladder, Notification |
| Builder | Itinerary & HTTP Request, Amazon |
| Prototype | Object Cloning Utility |
| Singleton | Rate Limiter, Logging Framework, Connection Pool |
| Object Pool | Thread Pool, Connection Pool |
| Observer | Pub/Sub, Notification, Leaderboard, Auction |
| Command | Text Editor, Food Order, Thread Pool |
| Memento | Text Editor, Ledger, In-Memory DB |
| Chain of Responsibility | Middleware, Logging Framework, API Gateway |
| Composite | File System, RBAC, Cap Table, Trello |
| Decorator | Discount Engine, Retry with Backoff |
| Proxy | Circuit Breaker, RBAC, API Gateway |
| Adapter | Payment Gateway |
| Interpreter / Specification | Rule Engine, Expression Evaluator, Log Query |
| Iterator | Music Playlist, Browser History |
| Template Method | Unit Test Framework |
| Visitor | File System, Cap Table |
| Flyweight | Minesweeper, Chess pieces |
| Mediator | Chat Application |
| Monitor / concurrency primitives | Producer–Consumer, Read-Write Lock |

---

## Sources

Compiled 2026-08-04 from recent candidate reports and curated question banks:
- Flipkart recent machine-coding rounds (Prashant Priyadarshi, Feb 2026)
- `github.com/ashishps1/awesome-low-level-design`
- `github.com/jkaus324/machine-coding-interview-questions` (105 problems, company-mapped)
- Low Level Design Mastery — machine coding company/format table
- algomaster.io — LLD interview format taxonomy
- Candidate write-ups: Flipkart SDE-2, Zepto SDE-1, Meesho SDE-1, Blinkit SDE-1

## Related Notes

- [[Pattern Selection]]
- [[Interview Approach]]

---
---

# EXECUTION PLAN — Absolute Priority Order

> [!important] This is the section to follow verbatim. No archetype grouping, no domain grouping — just two flat ordered lists. Work top to bottom. If you run out of time, you stop at the bottom of what you finished and nothing important was left behind.

## The Budget

**Sept 15 → Dec 31 2026 ≈ 15 weeks. At 3 questions/week = 45 slots.**

The plan below is **43 questions**: 24 must-code, 19 architect. Two weeks of slack for sickness, travel, and the weeks a must-code problem beats you.

Cadence leans toward code, since must-code outnumbers architect:
- **Most weeks — 2 must-code + 1 architect**
- Flip to 1 must-code + 2 architect whenever a must-code problem beats you

Must-code problems take a weekend day each; architect problems take an evening. Don't try to do two must-code problems on the same day — the second one will be sloppy and you'll learn nothing from it.

> [!danger] All 24 `[LLD Course Question]` items are in the plan, but they now consume **24 of the 43 slots**. That leaves only 19 slots for everything the market actually asks. This is the binding constraint — it is why several P0 problems below sit in Tier B that would otherwise be coded, and why 17 problems from the 21-week version were cut outright.

> [!warning] Fifteen weeks at 3/week is materially harder than the original 42-over-21-weeks (2/week). The 43 below is already the ruthless cut. Do not add anything back without removing something.

---

## TIER A — MUST CODE (24)

Full runnable Java. Edge cases, state management, thread safety where it applies. You type every line yourself.

Ordering logic: cheap pattern first-touches up front (they're the vocabulary everything later assumes), the concurrency block unbroken in the middle, hardest last.

| # | Problem | Why it must be coded |
|---|---|---|
| 1 | Design a Parking Lot `[LLD Course Question]` | The canonical entry point. Strategy + Factory + Singleton in one problem. |
| 2 | Design a Vending Machine | Cheapest possible State pattern. Do it early so State is in your fingers. |
| 3 | Design Tic-Tac-Toe `[LLD Course Question]` | Warm-up. Two hours max. |
| 4 | Builder Pattern: Itinerary & HTTP Request `[LLD Course Question]` | Small and mechanical. Builder never sticks until you've written a fluent chain. |
| 5 | Object Cloning Utility (Prototype) `[LLD Course Question]` | Deep vs shallow copy is a *coding* lesson, not a diagram lesson. |
| 6 | In-Memory Cache with Multiple Eviction Policies `[LLD Course Question]` | HashMap + doubly linked list internals. Cannot be dictated. |
| 7 | Simple Rule Engine `[LLD Course Question]` | Already in progress. Specification composition. |
| 8 | Text Editor (Undo/Redo Engine) `[LLD Course Question]` | Command + Memento. The undo stack only makes sense once you've built it. |
| 9 | Middleware Framework `[LLD Course Question]` | Chain of Responsibility. The `next()` handoff is the whole lesson. |
| 10 | Modular Logging Framework (log4j style) `[LLD Course Question]` | P0 at Amazon. Appenders + levels + formatters, three extension axes at once. |
| 11 | Design Snake & Ladder | High frequency (PhonePe, Amazon, Adobe, Swiggy). Extensible obstacle types. |
| 12 | Design an Elevator System | State + Strategy + scheduling. Classic and genuinely hard to get clean. |
| 13 | Data Stream Consumer `[LLD Course Question]` | Observer foundations, immediately before the concurrency block. |
| 14 | Design a Rate Limiter (Multi-Threaded) | **Start of the concurrency block.** Uber and Flipkart P0. |
| 15 | Thread Pool / Executor Service | Object Pool + Command + worker lifecycle. Nobody learns this from a diagram. |
| 16 | Producer–Consumer Bounded Buffer | `wait`/`notify` correctness. The single highest-value concurrency exercise. |
| 17 | Design a Task Scheduler (Cron Job) | Uber P0. Priority queue + timer thread + cancellation. |
| 18 | Design a Pub/Sub & In-Memory Messaging Queue `[LLD Course Question]` | Uber P0. Observer under concurrency — combines 13 and 16. |
| 19 | Circuit Breaker (Hystrix-style) | **End of concurrency block.** State machine + atomic counters. Fintech favourite. |
| 20 | Design Splitwise `[LLD Course Question]` | ShareChat, Razorpay, Flipkart, Paytm, Swiggy. Settlement graph is real work. |
| 21 | Billing & Discount Engine | **#1 on Flipkart's recent list.** Strategy + Decorator stacking. |
| 22 | Digital Wallet (Paytm / PhonePe style) | Top-10 on every Indian list. Balance under concurrency, idempotency. |
| 23 | Design a File System (Unix Composite) | The P0 Composite problem. Recursion is a coding lesson. |
| 24 | Stock Exchange Order Matching Engine `[LLD Course Question]` | Hardest problem here, deliberately last. Two heaps, partial fills, thread safety. |

---

## TIER B — ARCHITECT VIA DICTATION (19)

UML class diagram, entity interfaces, relationships and pattern choices — all yours. The agent types with zero intellectual input.

> [!warning] Six of these (B3, B4, B6, B7, B8, B10) would be coded in a longer timeline. They are here because 24 course slots plus 15 weeks leaves no room, not because they're easy. When you architect them, be stricter than usual about pinning down concurrency in the diagram, since you won't be discovering it in code.

| # | Problem | Why dictation is enough (or has to be) |
|---|---|---|
| 1 | Design Uber / Ride-Sharing System | Too large to finish coding in 90 min. Value is entirely in the entity model. |
| 2 | Design Swiggy / Food Delivery System | Booking + state archetype, both coded (A2, A23). Reskin. |
| 3 | Design BookMyShow `[LLD Course Question]` | ==Would be coded with more time.== Seat-lock concurrency is real; pin it in the diagram. |
| 4 | Design an ATM System `[LLD Course Question]` | ==Would be coded with more time.== State already coded at A2. |
| 5 | Design a Library Management System | Very widely asked but structurally simple. Fluency beats typing. |
| 6 | Order Management System | ==Would be coded with more time.== Reference state machine with guarded transitions. |
| 7 | RBAC / Permission System | ==Would be coded with more time.== Hierarchy walk + deny-overrides-grant is diagram-friendly. |
| 8 | Design a Payment Gateway System `[LLD Course Question]` | ==Would be coded with more time.== Adapter-per-provider is mostly structural. |
| 9 | Design a Notification System | Observer already coded at A13/A18. Value is channel/template modelling. |
| 10 | Scalable Banking System `[LLD Course Question]` | ==Would be coded with more time.== Top-spender ranking is a DS choice you can state. |
| 11 | Online Auction System `[LLD Course Question]` | Observer + priority queue, both covered by A18 and A24. |
| 12 | Stock Broker System (Zerodha / Groww) `[LLD Course Question]` | Sits directly on top of the matching engine you code at A24. |
| 13 | Design Amazon / E-Commerce System `[LLD Course Question]` | Whiteboard OOD problem. Nobody codes this end-to-end. |
| 14 | Design a Meeting Scheduler (Google Calendar) | Interval logic is DSA you already train. The design is the interesting part. |
| 15 | Trello / Jira Task Management | Atlassian favourite. Composite coded at A23; RBAC modelled at B7. |
| 16 | Design a Chess Game `[LLD Course Question]` | Eight hours to code properly — 1.5 weekends for one problem. Not affordable. |
| 17 | Movie Content Management System (Netflix CMS) `[LLD Course Question]` | Content modelling exercise. Genuinely diagram-shaped. |
| 18 | Cap Table Generator `[LLD Course Question]` | Composite + Visitor. Niche domain, clean model. |
| 19 | Design Battleship Game `[LLD Course Question]` | Course completeness. Rarely asked externally. |

---

## CUT — Do Not Do (and why)

> [!info] These stay documented above in the catalog so you can glance at one if an interviewer names it. But do not spend a slot on them.

**Reskins of something already in the plan** — the archetype is covered, the domain is a costume:
Coffee Vending Machine · Amazon Locker (= Parking Lot) · Conference Room Management (= Meeting Scheduler) · Restaurant Table Reservation · IRCTC Train Reservation · Course Registration · Airline Seat Allocation · Warehouse Allocation · Menu/Category Tree · Org/Employee Hierarchy · Smart Home IoT · Traffic Signal Control · UPI Payment Flow · Ride Surge Pricing · Peer-to-Peer Parcel Delivery · Music Streaming/Playlist · Minesweeper · Connect 4 · Deck of Cards · Snake Game · Order Fulfilment/Delivery Tracking

**HLD wearing an LLD costume** — you'd be practising the wrong round:
Load Balancer · Distributed ID Generator · Distributed Lock · Social Network / LinkedIn · Web Crawler · StackOverflow · Simplified Twitter · Recommendation/Ranking Policy

**DSA in disguise** — you already train this separately:
Regex / Pattern Matcher · Search Autocomplete / Typeahead · Sudoku Validator · Ten-Pin Bowling Scorer · Browser History Simulator

**Single-company one-offs** — no transfer value:
Bug Bounty Program · Dating App · Online Coding Platform · Gym Slot Booking *(cut reluctantly — it's a pure Practo reskin at B12)* · Vehicle Service Centre · Voting System · Quiz Platform · CricInfo

**Subsumed by something stronger in the plan:**
Validation Framework (= Rule Engine, A7) · Audit Log (= Observer, A13) · Retry with Backoff (folded into Circuit Breaker, A19) · Read-Write Lock (= Producer–Consumer, A16) · API Key & Quota (= Rate Limiter + RBAC) · Multi-Tenant Isolation (= RBAC, A31) · File Search System (= File System, A30) · Connection Pool (= Thread Pool, A15) · Feature Flag / A-B Testing · Tax Calculation Engine

**Niche or advanced, genuinely low yield:**
JSON Parser · Terminal/Shell Parser · CI/CD Pipeline Engine · DI / IoC Container · ORM / Query Builder · Object Mapper · Config Management Library · Unit Test Framework · In-Memory DB with Transactions · Loan/EMI Scheduling · Insurance Policy Management · BNPL · Refund & Settlement · Leave / Attendance Management · Survey / Form Builder · Customer Loyalty Program

> [!info] Seventeen further problems were in the 21-week version and lost their slot when the timeline shortened. Those are **not** dead — they live in [Tier C](#tier-c--backlog-todo-after-dec-end) at the bottom of this file.

---

## Checkpoints

Week 1 starts **Sept 15**. Week 15 ends **Dec 28**, leaving the last few days of December as buffer.

| By | Should be done | Signal if behind |
|---|---|---|
| Sept 28 (wk 2) | A1–A5, B1 | These five are the cheap ones. If they took two full weeks, your 3/week assumption is wrong — recut now, not in November |
| Oct 12 (wk 4) | Through A10, B1–B3 | Pattern foundations complete. Every major pattern has been typed once |
| Nov 2 (wk 7) | Through A19, B1–B7 | **Concurrency block done.** If A14–A19 slipped, stop Tier B entirely and finish them |
| Nov 30 (wk 11) | Through A22, B1–B13 | On track |
| Dec 28 (wk 15) | All 43 | — |

> [!danger] If you fall behind, cut from the **bottom of Tier B**, never from Tier A. B14–B19 are the designated sacrifice — Meeting Scheduler, Trello, Chess, Netflix CMS, Cap Table, Battleship. Losing all six costs you far less than skipping the concurrency block or the last three Tier A problems.

> [!question] The 21-week version fit 60 problems. This fits 43. If you can find even three extra weeks in January, the first few items in [Tier C](#tier-c--backlog-todo-after-dec-end) are worth more than anything currently sitting in B14–B19.

---

## TIER C — BACKLOG (TODO after Dec end)

> [!abstract] **TODO — after Dec 31, if there is time.** These seventeen were in the 21-week plan and lost their slot when the timeline shortened to 15 weeks. Nothing here is low-quality; they are ordered by what buys the most coverage per hour, so work top-down and stop whenever you stop. Everything in the *CUT — Do Not Do* section above stays cut permanently — this list is separate from that.

| # | Problem | Code or architect | Why it's worth coming back to |
|---|---|---|---|
| 1 | Multi-Level Cache (pluggable eviction per level) | Code | PhonePe asks this directly. Direct extension of A6, so it's cheap and high-yield. |
| 2 | Customer Issue Resolution / Helpdesk Ticketing | Code | PhonePe and Flipkart both ask it. Assignment strategy + SLA escalation. |
| 3 | Inventory Management System | Architect | Flipkart. Reservation and oversell prevention. |
| 4 | Log Query Engine | Code | Appeared in a recent Flipkart round in the extend-existing-code format. Filter composition. |
| 5 | Spreadsheet / Formula Engine | Architect | Atlassian. The dependency DAG and cycle detection are a genuinely distinct insight. |
| 6 | Doctor Appointment Booking (Practo) | Architect | Flipkart. Slot booking with practitioner availability. |
| 7 | Chat Application (WhatsApp / real-time) | Architect | Mediator + Observer at scale. Asked at Zepto and Meesho. |
| 8 | Design a Key-Value Store | Code | Storage internals with TTL and persistence hooks. |
| 9 | Design an API Gateway / Router | Architect | Route matching + filter chain on top of A9. |
| 10 | Workflow / Approval Engine (multi-level) | Architect | Rippling and Razorpay. Configurable approval chains. |
| 11 | Double-Entry Ledger / Accounting Core | Code | Razorpay, Cred. Invariant enforcement is worth typing once. |
| 12 | Authentication / Session & Token Management | Architect | Refresh-token rotation and revocation. Pairs with RBAC at B7. |
| 13 | Expression Evaluator / Calculator | Code | Cheap Interpreter exercise. Two hours, good ROI. |
| 14 | Design a Real-Time Leaderboard System | Architect | Dream11. Heap and tie-breaking choices. |
| 15 | Design Hotel Booking / Airbnb | Architect | Booking reskin. Do only for domain fluency. |
| 16 | Design a Car Rental System (ZoomCar) | Architect | Booking reskin with fleet inventory. |
| 17 | Design a URL Shortener (Bitly) | Architect | Thin on LLD. Lowest priority in this file for a reason. |

> [!tip] If you reach Tier C at all, prefer the six marked **Code** over the architect ones. By January your architecting will be well-practised from 19 Tier B problems, but coded reps are what stay in your fingers.
