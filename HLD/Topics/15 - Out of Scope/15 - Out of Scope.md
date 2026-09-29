---
tags: [hld/topics, status/draft]
created: 2026-09-18
updated: 2026-09-29
---
# Out of Scope

> [!abstract] What this is
> A backlog, not a scope rule. These topics exist and matter; they just have no note in this folder yet. When one gets written, move it to its home folder and delete it here.
>
> If an interviewer names one before then, say the one-line idea and return to the diagram.

---

## Still Without a Note

These are either proof-level theory or internals of a specific tool. Each has a one-liner you can say if it comes up.

| Topic | One-liner if asked | Would live in |
|---|---|---|
| Raft / Paxos proofs, membership changes | "Majorities overlap, so a new leader always has every committed entry. Membership changes go through the log too." | [[17 - Consistency Time and Consensus]] |
| ZooKeeper / etcd lock internals (zxid, ephemeral nodes) | "An ephemeral node vanishes when the session dies, so the lock releases itself." | [[14 - Coordination and Concurrency]] |
| The Redlock debate | "Multi-Redis majority lock. Without fencing tokens it's unsafe under pauses, so I won't use it for money." | [[14 - Coordination and Concurrency]] |
| Serializable Snapshot Isolation proofs | "Postgres tracks read/write dependencies and aborts one transaction if they form a cycle." | [[05 - Databases]] |
| Postgres advisory locks as a design | "An app-defined lock inside Postgres, useful when you already hold a DB connection." | [[14 - Coordination and Concurrency]] |
| XA / 3PC | "Distributed commit protocols that block or are fragile. Across services I use sagas." | [[10 - Distributed Building Blocks]] |
| Chain replication | "Writes go head to tail, reads come from the tail, so reads are consistent." | [[17 - Consistency Time and Consensus]] |
| Dynamo paper end to end | "Consistent hashing, sloppy quorums, hinted handoff, vector clocks, Merkle anti-entropy — each piece is in 10 and 17." | [[10 - Distributed Building Blocks]] |
| Poison-message frameworks beyond a DLQ | "Retry with backoff, then park in a DLQ with the error, and alert on DLQ depth." | [[07 - Async Messaging]] |
| Kubernetes internals (controllers, scheduler, operators) | "Controllers loop until actual state matches desired state." | [[19 - Deployment and Infrastructure]] |
| ML recommenders and ranking models | "Candidate generation, then ranking, served from precomputed features." | A future ML note |
| Netflix / YouTube end to end | Stretch reading: [[Netflix HLD]] | [[HLD/Problems/README]] |

LLD and machine-coding rounds are a different round entirely: [[LLD/README]].

---

## Moved Into a Home Note

Everything below used to be on this page and now has a proper section. The table exists so an old habit of looking here still lands somewhere.

| Topic | Now covered in |
|---|---|
| Consistency models, CAP and PACELC in depth | [[17 - Consistency Time and Consensus]] |
| Lamport, vector and hybrid logical clocks, TrueTime | [[17 - Consistency Time and Consensus]] |
| Raft at concept level, quorum vs consensus | [[17 - Consistency Time and Consensus]] |
| Split-brain, fencing tokens, STONITH | [[17 - Consistency Time and Consensus]] |
| Multi-region active-active and conflict resolution | [[17 - Consistency Time and Consensus]] · [[08 - Reliability]] |
| CRDTs, gossip, phi accrual failure detection | [[10 - Distributed Building Blocks]] |
| Hinted handoff, read repair, Merkle anti-entropy, rendezvous hashing | [[10 - Distributed Building Blocks]] |
| Vacuum and bloat | [[01 - Storage Engine and Disk IO]] |
| Online DDL, expand–contract, backfills, PITR and backups | [[12 - Schema Changes and Backups]] · [[19 - Deployment and Infrastructure]] |
| Kafka exactly-once transactions, idempotent producer | [[04 - Producer Mechanics and Delivery Guarantees]] |
| Log compaction | [[02 - Low-Level Storage and Performance IO]] |
| Schema registry and schema evolution | [[06 - Ecosystem Resilience and Design Patterns]] |
| RabbitMQ and SQS | [[RabbitMQ and SQS]] |
| Flink / Spark windowing, stream processing | [[20 - Data Processing and Analytics]] |
| Service mesh and mTLS | [[04 - Load Balancing CDN and Gateway]] · [[11 - Services and Observability]] |
| Blue-green and canary rollouts, capacity planning | [[19 - Deployment and Infrastructure]] |
| Queueing and Little's Law | [[02 - Fundamentals]] · [[16 - Estimation and Decision Cheat Sheet]] |
| Hedged requests, tail latency, chaos engineering | [[08 - Reliability]] · [[02 - Fundamentals]] |
| OAuth2 / OIDC, CSRF / CORS | [[18 - Security]] |
| PCI, KMS and envelope encryption, GDPR deletion | [[18 - Security]] |

## Related Notes

- [[README]]
- [[17 - Consistency Time and Consensus]]
- [[10 - Distributed Building Blocks]]
