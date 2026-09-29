---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
updated: 2026-09-29
---
# Kafka

> [!abstract] What this is
> Kafka in depth, split so you can open **one idea** at a time. These are complete notes, not a revision sheet. The short version is [[07 - Async Messaging]]; the spoken version of the grill is [[08 - Interview Questions]].

| File | What it covers | Read when |
|---|---|---|
| [[01 - Core Architecture and Cluster Consensus]] | Brokers, controller, KRaft vs ZooKeeper, leader / follower, bootstrap and client connections | They ask what a broker is, who elects the leader, or KRaft vs ZK |
| [[02 - Low-Level Storage and Performance IO]] | Append-only segments, sparse indexes, page cache, `sendfile`, retention vs compaction | They ask why it's fast if it's Java, page cache, or compaction |
| [[03 - Replication Durability and Consistency]] | Replica set, ISR, `min.insync.replicas`, HW vs LEO, unclean election | They ask about ISR, HW vs LEO, or whether `acks=all` can still lose data |
| [[04 - Producer Mechanics and Delivery Guarantees]] | Keys and partitioning, `acks`, idempotence, transactions, exactly-once end to end, batching | Keys, acks, exactly-once, batching, or **more partitions ≠ more throughput** |
| [[05 - Consumer Mechanics and Scalability]] | Consumer groups, `n` vs `c`, idle consumers, hot partitions, rebalance | Groups, scaling consumers, rebalances |
| [[06 - Ecosystem Resilience and Design Patterns]] | Kafka Streams, Connect, outbox, DLQ | Streams, Connect, outbox, DLQ |
| [[07 - Event-Driven Architecture and System Trade-offs]] | Event-driven design, Kafka vs RabbitMQ vs SQS | Choosing a broker or an event-driven shape |
| [[08 - Interview Questions]] | Full spoken answers | Rehearsing out loud |

## Reading Order

Read 01 → 04 in order: architecture, then storage, then replication, then the producer, since each builds on the last. Then 05 for consumers and 06 for patterns. Read 07 alongside [[RabbitMQ and SQS]] when comparing brokers, and finish with 08 to practise saying it.

## Related Notes

- [[07 - Async Messaging]]
- [[HLD/Topics/07 - Async Messaging/README]]
- [[RabbitMQ and SQS]]
