---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# Out of Scope

> [!abstract] Things the crash course **knows exist** and is **not teaching this week**. Not "unimportant." Wrong altitude for SDE-1 HLD in seven days. If a follow-up names one, say the one-liner from here and move on.

The in-scope slice of locking/coordination is [[14 - Coordination and Concurrency]]. Full designs of Netflix/YouTube are [[Netflix HLD]] as stretch, still not this week's job.

---

## Consensus and clocks

- Raft / Paxos **proofs**, log replication details, membership changes
- ZooKeeper / etcd **lock internals** (zxid, ephemeral-node protocol)
- Redlock **paper** and the distributed-lock controversy beyond "I won't use it for money"
- Fencing-token **implementation** (we name the idea in #14; we do not build one)
- Split-brain / STONITH
- TrueTime, Hybrid Logical Clocks, NTP / leap-second handling
- Vector-clock **merge rules** (the name is in [[10 - Distributed Building Blocks]])

## Database internals

- MVCC internals, WAL, checkpoints, crash recovery
- Vacuum / bloat
- Serializable Snapshot Isolation proofs
- Full write-skew / isolation-anomaly catalogue (one example is in #14)
- Two-phase locking algorithms, deadlock-detection graphs
- Postgres advisory locks as a design
- PgBouncer / connection-pooler architecture
- Online DDL, expand-contract migrations, backfills
- PITR, backup/restore, replica-lag runbooks
- XA / 3PC

## Distributed-data machinery

- CRDTs
- Gossip protocol
- Hinted handoff, read repair, anti-entropy as a design
- Merkle trees beyond the name
- Chain replication, Rendezvous hashing
- Quorum vs consensus as a theory lecture
- Phi accrual failure detectors
- Dynamo paper end-to-end

## Messaging internals

- Kafka exactly-once **transactions**, idempotent producer internals
- Log compaction as a system
- Schema registry
- Flink / Spark windowing
- Poison-message frameworks beyond DLQ

## Infra / platform

- Kubernetes internals (pods, controllers, operators)
- Service mesh (Istio / mTLS as architecture)
- Multi-region **active-active**, conflict resolution across regions
- Blue-green / canary as a full rollout design
- Capacity-planning spreadsheets, queueing theory / Little's law
- Hedged requests, tail-latency engineering
- Chaos engineering

## Product / adjacent rounds

- Designing Netflix / YouTube **end-to-end** (stretch: [[Netflix HLD]])
- ML recommenders, ranking models
- Full OAuth/OIDC spec, CSRF/CORS as HLD
- PCI / storing PANs, KMS / envelope encryption as a design
- GDPR deletion pipelines
- LLD / machine coding (that is [[LLD/Interview Approach]], a different week)

---

> [!tip] If an interviewer goes here anyway: name the idea in one sentence ("that's Raft — majority picks a leader so we don't have two primaries"), then return to the diagram. Do not open a lecture.

## Related Notes

- [[README]]
- [[14 - Coordination and Concurrency]]
- [[10 - Distributed Building Blocks]]
- [[Netflix HLD]]
