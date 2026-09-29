---
tags: [hld/topics, status/draft]
created: 2026-09-22
updated: 2026-09-29
---
# Async Messaging

> [!abstract] What this is
> The index for async messaging. Start with the core note for when to queue, delivery semantics, outbox and backpressure. Then go deep on Kafka, or read the RabbitMQ and SQS note when a classic queue fits better.

| File | What it covers | Read when |
|---|---|---|
| [[07 - Async Messaging]] | When to queue, queue vs log vs pub/sub, delivery semantics, idempotency, outbox, retries, backpressure, fanout | First. Always |
| [[Kafka/README]] | Kafka in depth: architecture, storage, replication, producer, consumer, patterns, EDA, interview Qs | The design uses Kafka or they ask about its internals |
| [[RabbitMQ and SQS]] | RabbitMQ exchanges and acks, SQS visibility timeout and FIFO, Kafka vs RabbitMQ vs SQS | You need a work queue, routing, or a managed queue instead of a log |

## Reading Order

Read [[07 - Async Messaging]] first. Then either the Kafka folder (01 → 08) or [[RabbitMQ and SQS]], depending on which broker the design needs. The broker comparison lives in [[RabbitMQ and SQS]] and [[07 - Event-Driven Architecture and System Trade-offs]].

## Related Notes

- [[HLD/Topics/README]]
- [[08 - Reliability]]
- [[14 - Coordination and Concurrency]]
