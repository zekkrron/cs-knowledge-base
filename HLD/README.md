# HLD

High Level Design notes.

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
| [[12 - Mini Designs]] | 20-min skeletons |
| [[13 - Question Bank]] | Grill + prompts |
| [[14 - Coordination and Concurrency]] | Locks, leases, fencing, SKIP LOCKED |
| [[15 - Out of Scope]] | Named, not taught this week |

## Problems (interview walks)

[[HLD/Problems/README]] — twelve designs, full script.

| # | Note |
|---|---|
| 1 | [[01 - Splitwise]] |
| 2 | [[02 - Task Scheduler]] |
| 3 | [[03 - Wearable Device]] |
| 4 | [[04 - Multipart Upload]] |
| 5 | [[05 - Jira]] |
| 6 | [[06 - Seat Booking]] |
| 7 | [[07 - Wallet and Ledger]] |
| 8 | [[08 - Resource Sharing ACL]] |
| 9 | [[09 - Inventory and Flash Sale]] |
| 10 | [[10 - Order Management]] |
| 11 | [[11 - Collaborative Workspace]] |
| 12 | [[12 - E-commerce Checkout]] |

## Other

| Note | Purpose |
|---|---|
| [[Netflix HLD]] | Full streaming case study — stretch, not SDE-1 default |
