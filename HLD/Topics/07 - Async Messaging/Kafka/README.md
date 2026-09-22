---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
---
# Kafka

> [!abstract]
> - SDE-1 Kafka, split so you can open **one idea** at a time
> - These are **complete notes**, not a revision sheet
> - Ten-minute slice: [[07 - Async Messaging]]
> - Mouth version of the grill: [[08 - Interview Questions]]

| # | Open this when they ask… |
|---|---|
| [[01 - Core Architecture and Cluster Consensus]] | What is a broker? Who elects the leader? KRaft vs ZK? |
| [[02 - Low-Level Storage and Performance IO]] | Why is it fast if it’s Java? Page cache? Compaction? |
| [[03 - Replication Durability and Consistency]] | ISR, HW vs LEO, can `acks=all` still lose data? |
| [[04 - Producer Mechanics and Delivery Guarantees]] | Keys, acks, exactly-once, batching |
| [[05 - Consumer Mechanics and Scalability]] | Groups, rebalance, lag, heartbeats |
| [[06 - Ecosystem Resilience and Design Patterns]] | Streams, Connect, outbox, DLQ |
| [[07 - Event-Driven Architecture and System Trade-offs]] | Kafka vs Rabbit vs SQS |
| [[08 - Interview Questions]] | Say it out loud — full senior answers |

---

## Related Notes

- [[07 - Async Messaging]]
- [[HLD/Topics/07 - Async Messaging/README]]
