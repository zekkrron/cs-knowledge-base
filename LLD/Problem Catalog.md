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

P values were re-checked against the market in October 2026 (see the ranked list below). Problems not in the ranked list keep their August 2026 value.

**Tier assignment (must-code vs architect-via-dictation) is deliberately not in this file yet** — that's the next pass.

---

## Market Priority — October 2026 (SDE 1–3, India and similar markets)

> [!abstract] Flat ranked list
> The archetype tables below say *what exists*. This list says *what to expect first*, re-checked against interview reports and company guides published up to October 2026. Ranking = how often a problem (or its direct reskin) is reported at Indian product companies (Flipkart, PhonePe, Razorpay, Swiggy, Zepto, Meesho, Uber India, Amazon India, Atlassian India), weighted by recency. It's a synthesis, not an official count.

### What the 2026 reports say about the round

- **Format**: a 60–120 min machine coding round with working, compilable code, then a 30–45 min review where the interviewer adds a requirement live (Flipkart, Swiggy, Razorpay). PhonePe runs it as 90 min live **or a 24-hour take-home**, followed by a code review. Uber runs object-oriented design plus implementation on CodeSignal, testing concurrency. Amazon SDE-2 has a dedicated LLD round graded on SOLID, extensibility and exception handling, with a behavioural question mixed in. Atlassian runs a 60 min LLD in your own IDE.
- **What is graded**: extensibility under a live change request, SOLID, naming, exception handling, and **thread safety** (PhonePe explicitly rejects correct but non-thread-safe solutions). A simple design that fully works beats an ambitious half-finished one.
- **Growing style**: "API + state machine" LLD, where the prompt asks for the public API, in-memory structures with complexities, a persisted schema and a status state machine (e.g. Amazon's delayed job scheduler). Class diagrams alone fail these.

### Ranked list

| # | Problem | P | Recent evidence (2025–26) | Archetype |
|---|---|---|---|---|
| 1 | Parking Lot | P0 | Most frequent in PracHub's LLD bank; Uber, Flipkart, Swiggy | §2 Strategy |
| 2 | Rate Limiter (thread-safe, per-user limits) | P0 | Atlassian, Uber, Swiggy LLD rounds; Razorpay | §7 Concurrency |
| 3 | Elevator System | P0 | Google, Salesforce, Remitly, Amazon prompts with scheduling follow-ups | §1 State |
| 4 | Splitwise / Expense Sharing | P0 | Named in Swiggy's 2026 machine coding guide; Flipkart, Razorpay, Paytm | §10 Fintech |
| 5 | In-Memory Cache with Eviction → Multi-Level Cache | P0 | PhonePe (multi-level cache, multiple reports), CRED (LRU, KV) | §8 Caching |
| 6 | Pub/Sub / In-Memory Message Broker | P0 | Uber, Swiggy, Razorpay ("pub-sub workflows"), Flipkart practice list | §6 Observer |
| 7 | Job / Task Scheduler (delayed jobs, producer–worker queue) | P0 | Razorpay SDE-2 (Jun 2026: producers submit jobs, workers pull), Amazon delayed job scheduler LLD, Uber | §4 / §7 |
| 8 | Digital Wallet / Thread-safe Money Transfer | P0 | Swiggy, PhonePe, Paytm; thread-safe transfer is a common concurrency follow-up | §10 Fintech |
| 9 | Vending Machine | P0 | Amazon, Flipkart; standard State-pattern prompt | §1 State |
| 10 | Billing / Discount / Cart Pricing Engine | P0 | Flipkart (e-commerce billing engine in machine coding), cart-pricing and delivery-cost prompts | §2 Strategy |
| 11 | Booking with Holds (BookMyShow, conference room, flight, doctor slots) | P0 | Flipkart (conference room booking, airline booking), BookMyShow-style at Paytm/Swiggy | §9 Booking |
| 12 | Inventory Management (reserve, release, no oversell) | P0 | Flipkart machine coding practice set; Zepto/Meesho; pairs with HLD flash sale | §16 → treat as §1/§9 |
| 13 | Board Games: Snake & Ladder, Tic-Tac-Toe, Chess | P0 | PhonePe, Swiggy, Zepto; quick OOP-modelling screens | §14 Games |
| 14 | Order Management / Food Delivery state machine | P0 | Swiggy, Zomato, Meesho, Amazon | §1 State / §9 |
| 15 | Logging Framework | P0 | Amazon; extension-axis favourite | §15 Framework |
| 16 | Ride Sharing (Uber/Ola) | P1 | Uber, Ola, Flipkart | §4 Matching |
| 17 | Vehicle / Car Rental | P1 | PhonePe machine coding (2026 guide example) | §9 Booking |
| 18 | DAG / Workflow Execution Engine | P1 | PhonePe machine coding (2026 guide example) | §12 Command / §4 |
| 19 | Customer Issue Resolution / Helpdesk | P1 | Flipkart LLD (recent), PhonePe | §16 Misc |
| 20 | Stock Exchange Order Matching | P1 | Zerodha, Groww, Upstox | §4 Matching |
| 21 | Thread Pool / Producer–Consumer / Connection Pool | P1 | Uber and fintech concurrency follow-ups; lazy connection pool prompts | §7 Concurrency |
| 22 | Load Balancer (strategy-based, LLD) | P1 | Razorpay machine coding reports | §4 Allocation |
| 23 | BNPL / Lending Ledger | P1 | Flipkart SDE-2 machine coding (Jan 2026) | §10 Fintech |
| 24 | URL Shortener (LLD) | P1 | Flipkart machine coding examples | §8 Storage |
| 25 | In-Memory Database / KV / Document Store with search | P1 | Razorpay senior machine coding (insert/delete/search documents), in-memory DB API prompts | §8 Storage |
| 26 | Circuit Breaker / Retry | P1 | Razorpay, PhonePe resilience follow-ups | §7 Concurrency |
| 27 | Text Editor (Undo/Redo) | P1 | Widely asked command/memento prompt | §12 Command |
| 28 | File System (Composite) | P1 | Amazon, Microsoft | §5 Composite |
| 29 | Meeting Scheduler / Calendar | P1 | Flipkart, Razorpay, Groww | §9 Booking |
| 30 | Task Management (Jira/Trello), Snake Game | P1 | Atlassian (both reported in LLD rounds) | §5 / §14 |
| 31 | RBAC / Permissions | P1 | Atlassian, Rippling, SaaS | §11 Access |
| 32 | Payment Gateway (provider adapters, retries, idempotent status) | P1 | Razorpay, PhonePe; fintech LLD staple | §10 Fintech |
| 33 | Notification System (channels, templates, retries) | P1 | Flipkart, Swiggy; often a follow-up to Order Management | §6 Observer |
| 34 | Online Auction | P1 | Amazon India SDE-2 (asked as a design round), Flipkart | §4 Matching |
| 35 | Library Management | P2 | Mostly service companies and campus rounds; rarer at product companies | §5 Composite |

> [!info] Level guide
> - **SDE 1**: #1–15. Code each in 90 minutes, runnable, with one live extension.
> - **SDE 2**: #1–34. Add thread safety, clear exception handling, and the extension the reviewer asks for without a rewrite.
> - **SDE 3 / senior**: everything, plus the API + state-machine style (persisted schema, leases, idempotency) and concurrency trade-offs stated unprompted.

> [!warning] Changes vs the August 2026 version of this file
> Promoted on recent evidence: **Task/Job Scheduler** (P1 → P0), **Inventory Management** (P1 → P0), **In-Memory DB / document store** (P2 → P1), **Airline / Flight booking** (P2 → P1), **Vehicle Rental**, **Load Balancer**, **BNPL** and **Snake Game** now carry 2026 company evidence, and **DAG / Workflow Execution Engine** is new. **Multi-Level Cache** goes P1 → P0 (it is how PhonePe actually phrases the cache question). Demoted P0 → P1 because they show up less often as the *primary* prompt in 2026 reports: Stock Exchange, Ride Sharing, File System, Text Editor, Thread Pool, Producer–Consumer, RBAC, Online Auction, Payment Gateway, Notification System. Library Management is demoted to P2 for product-company prep. The archetype tables below use the same P values. See the *Market check* callout in the execution plan for what this means for your schedule.

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
| P0 | Design a Task Scheduler (Cron / Delayed Jobs / Producer–Worker Queue) | Strategy, Command | Uber, **Razorpay SDE-2 (Jun 2026)**, **Amazon (delayed job scheduler)** |
| P1 | Stock Exchange Order Matching Engine `[LLD Course Question]` | Strategy, Observer | Upstox, Groww, Zerodha |
| P1 | Design Uber / Ride-Sharing System | Strategy, Factory, Observer | Uber, Ola, Flipkart |
| P1 | Online Auction System `[LLD Course Question]` | Strategy, Observer, State | Flipkart, Amazon India SDE-2 |
| P1 | Design a Real-Time Leaderboard System | Strategy, Observer | Dream11, Gaming |
| P1 | **Peer-to-Peer Parcel Delivery** *(new)* | Strategy, Observer | Flipkart |
| P1 | **Load Balancer** *(new)* | Strategy | Uber, **Razorpay (machine coding)** |
| P1 | **Airline Seat Allocation / Flight Booking** *(new)* | Strategy, State | **Flipkart (LLD, recent)**, Travel |
| P2 | **Warehouse / Inventory Allocation** *(new)* | Strategy | Flipkart, Meesho |

---

## 5. Hierarchy & Composite Structures

> [!tip] Recognition signal: a tree where leaf and container must be treated uniformly. "Add a folder inside a folder."

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P1 | Design a File System (Unix) | Composite, Strategy, Visitor | Amazon, Microsoft |
| P1 | **File Search System (filters, nested)** *(new)* | Composite, Strategy | Amazon, Microsoft |
| P1 | **Trello / Jira Task Management** *(new)* | Composite, Observer, State | **Atlassian**, Flipkart |
| P2 | Design a Library Management System | Composite, State | TCS, Infosys, campus rounds; rare at product companies |
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
| P1 | Design a Notification System | Observer, Strategy, Factory | Flipkart, Swiggy |
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
| P1 | **Thread Pool / Executor Service** *(new)* | Object Pool, Command | Uber, PhonePe |
| P1 | **Producer–Consumer Bounded Buffer** *(new)* | Monitor, Observer | Fintech, Trading |
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
| P0 | **Multi-Level Cache (pluggable eviction per level)** *(new)* | Strategy, Chain of Responsibility | **PhonePe (multiple reports)** |
| P1 | Design a Key-Value Store | Strategy, Singleton | CRED, Infra, Databases |
| P1 | Design a URL Shortener (Bitly) | Strategy, Singleton | **Flipkart (machine coding)**, widely asked |
| P1 | **In-Memory Database / Document Store (transactions, insert/delete/search)** *(new)* | Memento, Command | **Razorpay (senior machine coding)**, Arcesium |
| P2 | **Browser History Simulator** *(new)* | Memento, Iterator | Easy warm-up |

---

## 9. Booking & Reservation Systems

> [!warning] Heavily redundant category. These are all the same archetype — inventory, hold, confirm, concurrency on double-booking. Do two or three properly; the rest are reskins you can architect in minutes.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P0 | Design BookMyShow (Movie Booking) `[LLD Course Question]` | State, Strategy, Observer | Swiggy, Paytm, DoorDash |
| P0 | Design Swiggy / Food Delivery System | State, Strategy, Observer | Swiggy, Zomato, Flipkart |
| P1 | Design Hotel Booking / Airbnb | Strategy, State | Widely asked |
| P1 | Design a Meeting Scheduler (Google Calendar) | Strategy, Observer | Flipkart, Razorpay, Groww |
| P1 | Design a Car Rental System (ZoomCar) | Strategy, State | **PhonePe (machine coding, 2026)**, widely asked |
| P1 | **Doctor Appointment Booking (Practo)** *(new)* | Strategy, State | **Flipkart** |
| P1 | **Gym / Fitness Slot Booking** *(new)* | Strategy, State | **Flipkart** |
| P2 | **Restaurant Table Reservation** *(new)* | Strategy, State | Zomato, Dineout |
| P2 | **IRCTC Train Reservation** *(new)* | Strategy, State | Indian-specific |
| P2 | **Course Registration System** *(new)* | Strategy, State | EdTech |
| P2 | **Amazon Locker System** *(new)* | Strategy, Factory, State | **Amazon** |
| P1 | **Conference Room Management** *(new)* | Strategy | **Flipkart (machine coding examples)** |

---

## 10. Money Movement, Ledgers & Fintech

> [!info] India-heavy. PhonePe, Razorpay, Paytm, Cred, Groww, Upstox, Navi all draw from here, and they weight thread safety and transaction correctness above extensibility.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P1 | Design a Payment Gateway System `[LLD Course Question]` | Strategy, State, Adapter | Razorpay, PhonePe |
| P0 | **Digital Wallet (Paytm / PhonePe / Flipkart Wallet)** *(new)* | State, Command | **Flipkart, PhonePe, Paytm** |
| P0 | Scalable Banking System (Ops & Top Spender Ranking) `[LLD Course Question]` | Strategy, Observer | Widely asked |
| P0 | Design Splitwise `[LLD Course Question]` | Strategy, Factory, Observer | ShareChat, Razorpay, Flipkart, Paytm, Swiggy |
| P1 | Stock Broker System (Zerodha / Groww) `[LLD Course Question]` | Strategy, Observer, State | Groww, Zerodha, Upstox |
| P1 | **Double-Entry Ledger / Accounting Core** *(new)* | Command, Memento | Razorpay, Cred |
| P1 | **Buy Now Pay Later (BNPL)** *(new)* | State, Strategy | **Flipkart SDE-2 (Jan 2026)**, Cred |
| P1 | **Refund & Settlement System** *(new)* | State, Command | Razorpay, PhonePe |
| P2 | **Loan / EMI Scheduling System** *(new)* | Strategy, Builder | Navi, Cred |
| P2 | **Insurance Policy Management** *(new)* | State, Strategy | Insurtech |
| P2 | **UPI Payment Flow** *(new)* | State, Chain of Responsibility | Indian-specific |

---

## 11. Access Control & Identity

> [!danger] Entirely absent from your original list, and it is asked far more than its reputation suggests — especially at B2B SaaS and anywhere multi-tenancy exists. Rippling, Atlassian, Freshworks.

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P1 | **RBAC / Permission System** *(new)* | Composite, Strategy, Proxy | Atlassian, Rippling, SaaS |
| P1 | **Authentication / Session & Token Management** *(new)* | Strategy, State | Widely asked |
| P2 | **Multi-Tenant Resource Isolation** *(new)* | Proxy, Strategy | B2B SaaS |
| P2 | **API Key & Quota Management** *(new)* | Strategy, Proxy | Infra, SaaS |

---

## 12. Command, Undo & Interception Pipelines

| P | Problem | Patterns | Asked At |
|---|---|---|---|
| P1 | Text Editor (Undo/Redo Engine) `[LLD Course Question]` | Command, Memento | Widely asked |
| P0 | Middleware Framework `[LLD Course Question]` | Chain of Responsibility, Decorator | Widely asked |
| P1 | **DAG / Workflow Execution Engine (dependencies, parallel steps, retries)** *(new)* | Command, State, Observer | **PhonePe (machine coding, 2026)** |
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
| P1 | **Snake Game (single-player, grid)** *(new)* | State, Strategy | **Atlassian (LLD round, own IDE)** |
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
| P0 | **Inventory Management System (reserve, release, no oversell)** *(new)* | State, Observer | **Flipkart (machine coding practice set)**, Meesho, Zepto |
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

Market re-check on 2026-10-17 (ranked list, P values, execution plan market check):
- PracHub, LLD interview guide and question bank (Jun 2026): Parking Lot and Elevator most frequent; Amazon delayed job scheduler; Uber concurrency on CodeSignal
- TechPrep, Flipkart interview guide (2026): machine coding examples (URL shortener, e-commerce billing engine, conference room booking, in-memory message broker, inventory management), LLD examples (airline booking, customer issue resolution)
- TechPrep, PhonePe interview guide (2026): DAG execution engine, vehicle rental, 90 min live or 24 h take-home, thread safety mandatory; multi-level cache reports
- Swiggy machine coding guides (2026): parking lot, expense sharing, wallet, pub-sub, rate limiter
- Razorpay candidate reports: SDE-2 job queue with producers and workers (Jun 2026), load balancer, pub-sub, senior document store with insert/delete/search
- Flipkart SDE-2 BNPL machine coding (Jan 2026); Amazon SDE-2 LLD round (SOLID + LP, auction); Atlassian LLD (rate limiter, snake game, Jira-style task management); CRED (LRU, KV store)

Original compile 2026-08-04 from recent candidate reports and curated question banks:
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

> [!check] Market check (Oct 2026)
> Re-checked against the ranked list at the top of this file. Tier A already codes 12 of the 15 P0s. The other three are architected: Booking with Holds (BookMyShow, B3), Order Management (B6) and Inventory Management (B5 after this swap). So the plan mostly stands. Changes:
> - **B5 swap**: Library Management (now P2) is out, **Inventory Management** (now P0) is in. Same slot, same architect format. Library moves to the bottom of Tier C.
> - **A17 Task Scheduler** stays coded but widen the scope: delayed jobs, producers submitting to a queue, workers pulling with leases, a status state machine (`PENDING → RUNNING → DONE/FAILED/RETRY`). That one problem now covers the Razorpay and Amazon prompts.
> - **A6 In-Memory Cache**: finish with a second level (L1/L2 with per-level eviction). PhonePe asks the multi-level version directly.
> - **Tier C re-ordered**: DAG Execution Engine, In-Memory DB / Document Store, Car Rental, Load Balancer, BNPL, URL Shortener and Snake Game now carry 2026 evidence and sit near the top. Load Balancer, BNPL, Snake Game and In-Memory DB came out of CUT.
> - **If Flipkart or PhonePe is the target**: pull Tier C #1–5 forward ahead of B14–B19. Every `[LLD Course Question]` stays in the plan either way.

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
| 5 | Inventory Management System *(Oct 2026: replaces Library Management)* | P0 at Flipkart, Zepto, Meesho. Reserve/release/commit states and the oversell guard; pin the lock granularity (per SKU) in the diagram. |
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
Coffee Vending Machine · Amazon Locker (= Parking Lot) · Conference Room Management (= Meeting Scheduler) · Restaurant Table Reservation · IRCTC Train Reservation · Course Registration · Airline Seat Allocation *(P1 now, but it is BookMyShow with flights: 20 minutes of reskinning B3 if Flipkart is the target)* · Warehouse Allocation (= Inventory, B5) · Menu/Category Tree · Org/Employee Hierarchy · Smart Home IoT · Traffic Signal Control · UPI Payment Flow · Ride Surge Pricing · Peer-to-Peer Parcel Delivery · Music Streaming/Playlist · Minesweeper · Connect 4 · Deck of Cards · Order Fulfilment/Delivery Tracking

**HLD wearing an LLD costume** — you'd be practising the wrong round:
Distributed ID Generator · Distributed Lock · Social Network / LinkedIn · Web Crawler · StackOverflow · Simplified Twitter · Recommendation/Ranking Policy

**DSA in disguise** — you already train this separately:
Regex / Pattern Matcher · Search Autocomplete / Typeahead · Sudoku Validator · Ten-Pin Bowling Scorer · Browser History Simulator

**Single-company one-offs** — no transfer value:
Bug Bounty Program · Dating App · Online Coding Platform · Gym Slot Booking *(cut reluctantly — it's a pure Practo reskin at B12)* · Vehicle Service Centre · Voting System · Quiz Platform · CricInfo

**Subsumed by something stronger in the plan:**
Validation Framework (= Rule Engine, A7) · Audit Log (= Observer, A13) · Retry with Backoff (folded into Circuit Breaker, A19) · Read-Write Lock (= Producer–Consumer, A16) · API Key & Quota (= Rate Limiter + RBAC) · Multi-Tenant Isolation (= RBAC, B7) · File Search System (= File System, A23) · Connection Pool (= Thread Pool, A15) · Feature Flag / A-B Testing · Tax Calculation Engine

**Niche or advanced, genuinely low yield:**
JSON Parser · Terminal/Shell Parser · CI/CD Pipeline Engine · DI / IoC Container · ORM / Query Builder · Object Mapper · Config Management Library · Unit Test Framework · Loan/EMI Scheduling · Insurance Policy Management · Refund & Settlement · Leave / Attendance Management · Survey / Form Builder · Customer Loyalty Program

> [!info] Twenty-one further problems (the ones that lost their slot when the timeline shortened, plus the Oct 2026 market additions) are **not** dead — they live in [Tier C](#tier-c--backlog-todo-after-dec-end) at the bottom of this file.

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

> [!abstract] **TODO — after Dec 31, if there is time.** These are the problems that lost their slot when the timeline shortened to 15 weeks, re-ordered in October 2026 against current interview reports, plus the 2026 market additions. Ordered by interview likelihood first, coverage per hour second, so work top-down and stop whenever you stop. Everything still in the *CUT — Do Not Do* section above stays cut permanently.

| # | Problem | Code or architect | Why it's worth coming back to |
|---|---|---|---|
| 1 | Multi-Level Cache (pluggable eviction per level) | Code | P0. PhonePe asks this directly. Direct extension of A6, so it's cheap. |
| 2 | DAG / Workflow Execution Engine | Code | PhonePe machine coding (2026). Topological order, parallel ready steps on a thread pool (reuses A15), per-step retry and failure propagation. |
| 3 | Customer Issue Resolution / Helpdesk Ticketing | Code | PhonePe and Flipkart. Assignment strategy + SLA escalation. |
| 4 | In-Memory Database / Document Store (KV, transactions, search) | Code | Razorpay senior machine coding. Insert/delete/search with secondary indexes, `BEGIN/COMMIT/ROLLBACK` via a change log. Replaces the old standalone Key-Value Store item. |
| 5 | Design a Car Rental System (ZoomCar) | Code | PhonePe machine coding (2026). Booking archetype with fleet inventory and overlapping-interval checks; fits 90 min. |
| 6 | Load Balancer (strategy-based, LLD) | Code | Razorpay machine coding. Round robin / weighted / least-connections behind one interface, plus health checks. Two hours. |
| 7 | Buy Now Pay Later (BNPL) | Architect | Flipkart SDE-2 machine coding (Jan 2026). Credit limit, dues, repayment state machine. Reuses A22 wallet thinking. |
| 8 | Design a URL Shortener (Bitly) | Code | Flipkart lists it as a machine coding example. Thin, but cheap to code with custom alias and expiry. |
| 9 | Snake Game (single-player, grid) | Code | Atlassian LLD round. Small, and a good test of keeping game state clean under live extensions. |
| 10 | Log Query Engine | Code | Recent Flipkart round in the extend-existing-code format. Filter composition. |
| 11 | Spreadsheet / Formula Engine | Architect | Atlassian. Dependency DAG and cycle detection; overlaps with #2. |
| 12 | Doctor Appointment Booking (Practo) | Architect | Flipkart. Slot booking with practitioner availability. |
| 13 | Workflow / Approval Engine (multi-level) | Architect | Rippling and Razorpay. Configurable approval chains. |
| 14 | Chat Application (WhatsApp / real-time) | Architect | Mediator + Observer. Asked at Zepto and Meesho. |
| 15 | Double-Entry Ledger / Accounting Core | Code | Razorpay, Cred. Invariant enforcement is worth typing once. |
| 16 | Design an API Gateway / Router | Architect | Route matching + filter chain on top of A9. |
| 17 | Authentication / Session & Token Management | Architect | Refresh-token rotation and revocation. Pairs with RBAC at B7. |
| 18 | Expression Evaluator / Calculator | Code | Cheap Interpreter exercise. Two hours, good ROI. |
| 19 | Design a Real-Time Leaderboard System | Architect | Dream11. Heap and tie-breaking choices. |
| 20 | Design Hotel Booking / Airbnb | Architect | Booking reskin. Do only for domain fluency. |
| 21 | Design a Library Management System | Architect | Moved out of B5 in Oct 2026. P2 for product companies; keep for service-company or campus rounds. |

> [!tip] If you reach Tier C at all, prefer the items marked **Code** over the architect ones. By January your architecting will be well-practised from 19 Tier B problems, but coded reps are what stay in your fingers.
