---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# Distributed Building Blocks

> [!abstract] A bag of named tools. You will not implement Raft. You **will** be asked: unique IDs, consistent hashing, quorum, and "how do two services agree." Match the tool to the sentence, don't dump the bag.

## Consistent hashing

`hash(key) % N` dumps **almost all keys** onto new machines when N changes.

**Ring:** hash keys *and* nodes onto a circle. Key belongs to the next node clockwise. Add a node → only the arc before it moves.

**Virtual nodes:** each physical box sits at many points so load is even.

Where it shows up: Redis cluster, Cassandra, some LBs, CDNs.

> [!tip] Interview line: "We'll place cache keys with consistent hashing so adding a replica doesn't invalidate the whole cache."

## Unique IDs

DB autoincrement **does not work** across shards (collides, or you need a single sequence SPOF).

| Scheme | Pros | Cons |
|---|---|---|
| UUID v4 | no coord | big, not sortable, random B-tree inserts |
| UUID v7 / ULID | sortable-ish | still 128 bit |
| **Snowflake** | 64-bit, time-ordered, per-machine | need machine id, clock caution |
| Redis INCR | simple | Redis is now in the write path |
| Range allocator | DB gives you 1000 ids | batch coordination |

**Snowflake layout (memorise):** 1 sign bit + timestamp + worker id + sequence.

This is the URL-shortener / tweet-id deep dive.

## Bloom filter

Probabilistic set: **"definitely not there"** or **"maybe there."** False positives, **no false negatives.** Tiny memory.

Use: "have we crawled this URL," "is this user in the 10B-key cache" before a disk hit. Not a DB.

Count-Min Sketch / HyperLogLog: frequency / cardinality. Tail. Name if they say "unique counts at huge scale."

## Quorum

`N` replicas, write `W`, read `R`. **R + W > N** → a read sees at least one latest write (under the usual assumptions).

Classic: N=3, W=2, R=2. Fast-write: W=1, R=N — stale reads.

This is Dynamo/Cassandra. Postgres replication is **not** usually discussed as quorum; don't mix the models.

## Leader election

Need one primary: partition leader, scheduler, lock holder.

**Raft/Paxos** — majority votes a leader, replicated log. One-sentence: "consensus so we don't have two primaries." You will not draw Raft.

ZooKeeper / etcd — "someone else runs Raft for us."

## 2PC vs saga (cross-service "transaction")

**Two-phase commit:** prepare all, then commit all. Blocking, painful across microservices. Don't propose 2PC as your architecture.

**Saga:** each service commits locally, **compensating action** if a later step fails (reserve stock → charge card → on fail, release stock). Orchestrator (one conductor) or choreography (events).

> [!warning] Sagas are **not** ACID. You can observe "reserved but not paid." Design for that (timeouts, status machine).

## Vector clocks / conflict (tail)

Multi-writer NoSQL: two replicas write without seeing each other. Vector clocks tell you **concurrent vs causal**. App must merge. SDE-1: "avoid multi-leader; if we must, last-write-wins or store both and resolve."

## Merkle trees (tail)

Hash tree to **diff replicas** without sending all data (Dynamo anti-entropy). Know the name.

## CDC

Change Data Capture: DB WAL → Kafka → search/cache/warehouse. How you keep ES in sync without dual writes. Pairs with [[07 - Async Messaging|outbox]].

## Related Notes

- [[05 - Databases]]
- [[02 - Fundamentals]]
- [[12 - Mini Designs]] — unique IDs, URL shortener
