---
tags: [hld/crash-course, status/draft]
created: 2026-09-18
---
# Async Messaging

> [!abstract] A queue is for **work you should not do on the user request**: email, fanout, image resize, "tell the other service." Kafka is a **durable log** many consumers can replay. Delivery is **at-least-once** until *you* make the consumer idempotent. There is no magic exactly-once button.

## When a queue exists

- The HTTP request should return before the slow work finishes.
- Spikes: 5k QPS burst, workers do 500/s — buffer, don't drop.
- Decouple: order service shouldn't know about email, analytics, search indexer.
- Multiple independent consumers of the same event.

> [!warning] If the user is staring at a spinner waiting for this result, **do not queue it**. Queues add latency. Checkout confirmation can enqueue *email*; charging the card stays on the request (or a sync call with a timeout).

## Queue vs stream (the distinction they want)

| | Queue (RabbitMQ, SQS) | Stream / log (Kafka) |
|---|---|---|
| Model | message disappears when consumed | stays for a retention period |
| Consumers | competing consumers (one wins) | **consumer groups**; several groups each read everything |
| Replay | awkward | first-class (offset) |
| Use | jobs, emails, "do this once" | events, audit, many subscribers, replay |

SQS is "I don't want to operate Kafka." Kafka is "several systems need this event and we might reprocess."

## Kafka — the SDE-1 slice (not internals)

- **Topic** → split into **partitions**. A partition is an ordered log.
- **Partition key** (e.g. `order_id`) — same key → same partition → **order inside that key**. No global order across partitions.
- **Producer** appends. **Consumer group** — each partition assigned to one consumer in the group (so you scale consumers ≤ partitions).
- **Another group** can read the same topic independently (notifications *and* analytics).
- **Offset** — how far this group has read. Commit after processing (at-least-once) or you skip on crash.
- **Lag** — produce rate > consume rate. Alert on it. Fix: more consumers/partitions, faster workers, or backpressure on produce.

Replication: brokers, leader per partition. If they ask "why Kafka is fast": sequential disk, batching, zero-copy — one sentence, don't lecture.

## Delivery semantics (memorise)

| Name | Meaning | Reality |
|---|---|---|
| At most once | send and forget | can lose |
| At least once | retry until ack | **duplicates**. This is the default. |
| Exactly once | once | **end-to-end is a lie unless** idempotent producer + transactional outbox + idempotent consumer. Say "effectively once via idempotency keys." |

> [!tip] The interview answer: "At-least-once + idempotent consumer (dedupe on `event_id` / upsert)." That *is* how grown-ups do exactly-once.

## Idempotency

Worker crashes after doing the work but before ack → Kafka redelivers.

Fixes:

- Unique `event_id` stored in DB (`INSERT … ON CONFLICT DO NOTHING`).
- Upsert by natural key.
- Payments: **Idempotency-Key** from the client, stored on the intent row.

## Transactional outbox (say this for "write DB and publish")

Don't `INSERT order` then `kafka.send` — one can succeed, the other fail.

1. In the **same DB transaction**: write the order **and** an `outbox` row.
2. A poller / Debezium CDC publishes outbox → Kafka.
3. Mark published.

This is the grown-up "dual write" fix. Name it.

## Retry, delay, DLQ

- Retry with **exponential backoff + jitter** (else retry storm).
- After N fails → **dead letter queue**. Humans/metrics, not silent drop.
- Don't retry 400s (bad payload). Do retry 503s / timeouts.

## Backpressure

A queue that grows forever is a delayed outage. Full queue → **reject produce** (429 / 503) or slow the producer. Scaling workers is the real fix; the queue only buys time.

## Fanout

One event, many channels: `OrderPlaced` → search index, email, analytics, push.

- **Topic + many consumer groups**, or
- One worker that fans out (worse coupling).

Prefer the log + groups.

## Related Notes

- [[08 - Reliability]]
- [[11 - Services and Observability]]
- [[12 - Mini Designs]] — notifications, news feed
