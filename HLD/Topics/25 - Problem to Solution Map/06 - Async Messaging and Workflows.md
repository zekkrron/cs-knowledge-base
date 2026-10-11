---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Async Messaging and Workflows

> [!abstract] What this is
> Problems about **work that happens later or elsewhere**: whether to add a queue and which one, publishing events without losing writes, duplicates and exactly-once, ordering, consumers that can't keep up, failing messages, Kafka durability and operations, schema evolution, and business transactions that span services.

---

## Whether and Which

### Slow work sits on the user's request path

- **Signal:** the HTTP response waits on email, image resizing, fan-out or calls to other teams' services.

| Option | Costs you | Pick when |
|---|---|---|
| **Enqueue it; return first** | Eventual; more moving parts | The user doesn't need the result to continue |
| **Keep it synchronous with a timeout; queue only the side effects** | Slower request | The user is waiting for the answer (charging the card at checkout) |
| **202 Accepted + job id, then poll or webhook** | Client handles async completion | Work takes > ~500 ms or "30 seconds" |

- **Watch out:** "add a queue for scale" at 200 QPS is wrong. Say "this is for decoupling, not throughput".
- **Depth:** [[07 - Async Messaging]], [[16 - Estimation and Decision Cheat Sheet]]

### Queue, log or pub/sub?

| Model | How it works | Costs you | Pick for |
|---|---|---|---|
| **Queue** | One competing worker takes each message; deleted on ack | Replay is awkward | Jobs, emails, "do this once" |
| **Log / stream** | Kept for a retention period; each consumer group tracks its own offset | Disk, retention, lag ops | Events, audit, many readers, reprocessing |
| **Pub/sub** | A copy to every subscriber | Replay only if log-backed | One event, many independent reactions |

- **Depth:** [[07 - Async Messaging]], [[07 - Event-Driven Architecture and System Trade-offs]]

### Kafka, RabbitMQ or SQS?

| Option | Strengths | Costs you | Pick when |
|---|---|---|---|
| **Kafka** | Replay, many consumer groups, per-key order at scale, CDC, Streams | High ops (less with MSK/Confluent); parallelism capped by partitions | Several systems need the event, you might reprocess, high volume |
| **RabbitMQ** | Smart broker: per-message ack/NACK, routing, priority, delayed retry | No replay; moderate throughput | Routed work queues, retrying *this* message |
| **SQS** | Zero ops, visibility timeout, DLQ | No replay; 14-day max retention; FIFO TPS cap | AWS job queue without history |
| **Redis Streams** | Small, already there | One node, must trim | Small durable queue on existing Redis |

- **Watch out:** Kafka as a job queue (needs NACK, priority, delays; `sleep` in a consumer blocks the partition) → use Rabbit/SQS or a scheduler.
- **Depth:** [[RabbitMQ and SQS]], [[07 - Event-Driven Architecture and System Trade-offs]]

### A new consumer needs last month's events

| Option | Pick when |
|---|---|
| **Kafka with retention; reset the offset** (to 0 or a timestamp) | Replay matters |
| **Keep your own copy** | Stuck on a queue that deletes on ack |

- **Depth:** [[07 - Event-Driven Architecture and System Trade-offs]]

### One event must reach many downstreams (fan-out)

| Option | Costs you | Pick when |
|---|---|---|
| **Topic + one consumer group per downstream** | Retention ops | Default |
| **SNS → one SQS queue per subscriber** | AWS only | On AWS; each subscriber gets its own retry and DLQ |
| **RabbitMQ fanout exchange** | No replay | Broadcast on Rabbit |
| ~~One worker calls every downstream~~ | Tight coupling; one slow channel blocks the rest | Rarely |

- **Depth:** [[07 - Async Messaging]], [[RabbitMQ and SQS]]

### Routing messages by attributes (billing vs shipping, EU vs US)

- **RabbitMQ exchanges:** direct (one key per job type), topic (`*`/`#` patterns over several attributes), fanout (broadcast), headers (rare). On Kafka, use separate topics or filter in consumers.
- **Depth:** [[RabbitMQ and SQS]]

---

## Publishing Reliably

### Write the database and publish an event atomically (dual write)

- **Signal:** the order is saved but the event is lost, or an event goes out for a rolled-back transaction.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Transactional outbox + poller** | Business row and outbox row in one transaction; a relay reads with `FOR UPDATE SKIP LOCKED`, publishes, marks sent | Extra queries; relay can publish twice | Default without Kafka Connect |
| **Outbox + Debezium CDC** | Relay tails the WAL for the outbox table | Kafka Connect to run; outbox needs a cleanup job | Connect already runs |
| **CDC on the business tables** | Publish every row change | Events mirror table shape | Feeding search, cache, warehouse |
| ~~Kafka transactions~~ | | Can't include Postgres | Not for this |

- **Default:** "write the DB first, publish via an outbox; consumers are idempotent."
- **Depth:** [[07 - Async Messaging]], [[06 - Ecosystem Resilience and Design Patterns]]

### Money or a critical write lives only on a fire-and-forget topic

- **Fix:** the system of record is the database (or outbox in the same transaction); topics carry only the follow-on work, consumed idempotently.
- **Depth:** [[07 - Event-Driven Architecture and System Trade-offs]]

### Producer retries create duplicates or reorder records

| Problem | Fix |
|---|---|
| **Same batch appended twice after a timeout** | `enable.idempotence=true` (PID + per-partition sequence) |
| **Retries reorder a partition** | Keep idempotence on; don't force `max.in.flight` above 5 or disable idempotence with in-flight > 1 |
| **Zombie producer after restart** | Stable `transactional.id`; `initTransactions()` fences the old epoch |

- **Depth:** [[04 - Producer Mechanics and Delivery Guarantees]]

### High produce throughput without saturating the network

| Option | Costs you | Pick when |
|---|---|---|
| **Batching** (`batch.size`, `linger.ms` 5–20 ms) | Latency | Pipelines |
| **Compression** (LZ4, Zstd, Snappy) | CPU | Network- or disk-bound |
| **`linger.ms=0`** | Many tiny requests | Low-volume, latency-sensitive |

- **Depth:** [[04 - Producer Mechanics and Delivery Guarantees]]

### Producer is faster than the brokers

- **Built in:** `send()` blocks up to `max.block.ms` when `buffer.memory` fills, pushing pressure into the producing service. **Multi-tenant clusters:** broker quotas delay an over-quota client's responses instead of rejecting.
- **Depth:** [[07 - Async Messaging]]

---

## Duplicates and Exactly-Once

### "Process each event exactly once"

| Option | Covers | Pick when |
|---|---|---|
| **At-least-once + idempotent consumer** ("effectively once") | Everything, if every consumer dedupes | Production default; say this |
| **Outbox + idempotent producer + idempotent consumer** | DB → Kafka → external system | End-to-end flows |
| **Kafka EOS** (idempotence + transactions + `read_committed`) | Kafka-to-Kafka only | Streams-style read-process-write |
| **SQS FIFO dedup id** | 5-minute window | On SQS when order and dedupe matter |

- **Watch out:** EOS stops at Kafka's edge: an email or card charge outside Kafka still needs its own idempotency.
- **Depth:** [[04 - Producer Mechanics and Delivery Guarantees]], [[07 - Async Messaging]]

### Duplicate processing after a crash before ack/commit

- **Fixes:** dedupe on `event_id` (`ON CONFLICT DO NOTHING`), an **inbox table** written in the side effect's transaction, upsert by natural key, or a client `Idempotency-Key` on payment intents. Also see [[04 - Concurrency and Correctness]].
- **Depth:** [[07 - Async Messaging]], [[06 - Ecosystem Resilience and Design Patterns]]

### SQS processes a message twice because the job outlives the visibility timeout

| Option | Pick when |
|---|---|
| **Timeout > worst-case processing time** | Always |
| **`ChangeMessageVisibility` heartbeat** | Unpredictable durations |
| **Idempotent consumer** | Always |

- **Depth:** [[RabbitMQ and SQS]]

### Messages lost because the offset was committed before the work

| Option | Costs you | Pick when |
|---|---|---|
| **Manual commit after success** | Duplicates on crash, so pair with idempotency | Default |
| **Auto-commit** | Can lose work | Loss is acceptable (sampled metrics) |

- **Depth:** [[05 - Consumer Mechanics and Scalability]]

---

## Ordering

### Events for one entity must stay in order

| Option | Costs you | Pick when |
|---|---|---|
| **Kafka partition key** = entity id | Hot-key risk; no cross-key order | Default |
| **SQS FIFO message group id** | FIFO throughput cap; a hot group is one lane | On SQS |
| **RabbitMQ single consumer per queue** | No parallelism; requeues break order | One worker suffices |

- **"Global order"**: only with one partition (no scale); push back to per-entity order.
- **No order needed:** sticky/no-key partitioner for maximum throughput.
- **Depth:** [[04 - Producer Mechanics and Delivery Guarantees]]

### Adding partitions later breaks per-key order

- **Why:** the key's partition changes while its history stays on the old one; partitions can be added but never removed. **Fix:** size partitions with headroom at creation, choosing counts that divide likely consumer counts (12, 24, 48).
- **Depth:** [[04 - Producer Mechanics and Delivery Guarantees]], [[07 - Async Messaging]]

### Hot partition

- **Fixes:** higher-cardinality key; isolate the whale on its own topic; accept one key = one lane; split the key (loses that key's order). More partitions or consumers don't fix it. Same family as hot shards in [[03 - Scaling Reads Writes and Data]].
- **Depth:** [[04 - Producer Mechanics and Delivery Guarantees]]

---

## Keeping Up

### Consumer lag keeps growing

| Option | Costs you | Pick when |
|---|---|---|
| **Alert on lag and its growth; autoscale on lag** (KEDA) | Capped at the partition count | Always |
| **More partitions + more consumers** | One-way door; key mapping changes | Partitions are the limit |
| **Make the handler faster** (batch inserts, better queries, more CPU) | Effort | Locked at partitions = consumers |
| **Consumer-side fan-out** (thread pool or secondary queue) | Loses per-key order and safe commits unless keyed to threads and committing only finished offsets | Partitions can't grow |
| **Backpressure upstream** | Producers slow or see 429/503 | The downstream is the ceiling |

- **Watch out:** extra pods beyond the partition count sit idle.
- **Depth:** [[07 - Async Messaging]], [[05 - Consumer Mechanics and Scalability]]

### Scaled-up consumers overwhelm their own downstream

- **Fixes:** cap the worker pool; rate-limit calls to the downstream; on rising latency or 429/503, `pause()` the partitions and `resume()` later so lag grows on the broker instead of errors downstream.
- **Depth:** [[07 - Async Messaging]]

### The queue grows without bound

- **Fixes:** reject or slow producers when full (429/503); scale workers (the real fix); with pull-based consumers the backlog is just lag on the broker, bounded by retention. A queue only buys time. SQS: alert on message age before retention deletes it.
- **Depth:** [[07 - Async Messaging]], [[RabbitMQ and SQS]]

### Push broker floods a slow consumer

| Option | Pick when |
|---|---|
| **Prefetch 1** (`basic.qos`) | Slow, uneven jobs |
| **Prefetch tens–low hundreds** | Fast, uniform handlers |
| ~~Unlimited prefetch~~ | Never |

- **Depth:** [[RabbitMQ and SQS]]

### Healthy consumer kicked out (poll interval exceeded) or one record takes minutes

| Option | Costs you | Pick when |
|---|---|---|
| **Process faster / smaller `max.poll.records`** | More polls | Batches too big |
| **Raise `max.poll.interval.ms`** | Slower failure detection | Work slightly too slow |
| **Hand off to a DB work list** (idempotent insert, workers use `SKIP LOCKED`) | Order lost | Independent long jobs (transcodes, reports) |
| **Pause the partition while a worker runs, keep polling** | State in the consumer | Order matters, jobs occasional |

- **Depth:** [[05 - Consumer Mechanics and Scalability]]

### Rebalances stall the whole group

| Option | Pick when |
|---|---|
| **Cooperative sticky assignor** (revoke only what moves) | Default for new consumers |
| **Static membership** (`group.instance.id`) | Rolling deploys of stateful consumers / Streams |

- **Depth:** [[05 - Consumer Mechanics and Scalability]]

### Idle SQS consumers burn money

- **Fix:** long polling (`WaitTimeSeconds` up to 20). **Depth:** [[RabbitMQ and SQS]]

---

## Failing Messages

### Transient errors and retry storms

| Option | Costs you | Pick when |
|---|---|---|
| **Exponential backoff + jitter, capped retries** | Slower recovery per message | Always |
| **Retry topic with backoff** | Another topic; order relative to the main topic changes | Kafka, so the main partition keeps moving |
| **RabbitMQ delayed retry** (TTL queue whose DLX points back at the work queue) | Rabbit only | Delayed retries on Rabbit |

- **Depth:** [[06 - Ecosystem Resilience and Design Patterns]], [[RabbitMQ and SQS]]

### Poison message blocks progress or crashes every consumer in turn

| Option | Pick when |
|---|---|
| **Don't retry 4xx / validation errors; send straight to the DLQ** | Permanent errors |
| **Kafka DLQ**: produce to `topic.dlq`, commit the bad offset, continue | Always on Kafka |
| **SQS redrive** (`maxReceiveCount`; FIFO DLQ must be FIFO) | On SQS |
| **RabbitMQ reject without requeue → DLX**; quorum-queue delivery limit | On Rabbit |
| **Alert on the DLQ; never drop silently** | Always |

- **Depth:** [[07 - Async Messaging]], [[06 - Ecosystem Resilience and Design Patterns]]

### A schema change breaks consumers

| Option | Pick when |
|---|---|
| **Schema Registry with compatibility checks** | Always; it's the contract |
| **Backward compatibility** (upgrade consumers first) | Default; consumers replay old history |
| **Forward / full / transitive** | Producers change often / many teams / long retention |
| **Additive rule**: optional fields with defaults; never reuse a name or number or change a type | Always |

- **Format:** Avro (Kafka-heavy JVM, CDC), Protobuf (polyglot, gRPC), JSON Schema (readability).
- **Depth:** [[06 - Ecosystem Resilience and Design Patterns]]

---

## Kafka Durability and Operations

### Acknowledged writes lost on broker failure

| Setting | Costs you | Pick when |
|---|---|---|
| **RF 3, `min.insync.replicas=2`, `acks=all`** | `NotEnoughReplicas` with two brokers down | Anything that matters |
| **`acks=1`** | Lost if the leader dies before followers copy | Low latency, some loss OK |
| **`acks=0`** | Can lose anything | Droppable metrics |

- **Trap:** `acks=all` with `min.insync.replicas=1` quietly behaves like `acks=1`.
- **Depth:** [[03 - Replication Durability and Consistency]]

### Controller or quorum trouble; clients hit the wrong broker

- **Quorum:** 3 or 5 KRaft voters across AZs; combined mode for small clusters, dedicated controllers for large. **Routing:** a load balancer is only for bootstrap discovery; clients follow metadata and advertised listeners.
- **Depth:** [[01 - Core Architecture and Cluster Consensus]]

### Broker throughput collapses or GC pauses grow

- **Fix:** small JVM heap, leave RAM to the OS page cache sized for recent segments. Cross-AZ cost or hot leaders: fetch from follower (`client.rack`).
- **Depth:** [[02 - Low-Level Storage and Performance IO]], [[01 - Core Architecture and Cluster Consensus]]

### Moving data between Kafka and databases or search

- **Option:** Kafka Connect source/sink connectors (Debezium for CDC). At-least-once, so sinks must be idempotent; monitor connector offsets. Use Kafka Streams only when the product *is* the pipeline; a plain consumer group handles "send an email on order".
- **Depth:** [[06 - Ecosystem Resilience and Design Patterns]]

---

## Workflows Across Services

### One business action spans several services' databases

- **Signal:** reserve stock + charge card + create order, each in its own service.

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **2PC** | Coordinator: prepare (participants lock and vote), then commit/abort | Blocks with locks held if the coordinator dies; every participant must support prepare | Inside one database system or tightly coupled resources only |
| **Orchestrated saga** | Orchestrator sends commands step by step, runs compensations in reverse on failure | Central component; compensations to design | Many steps, branching, timeouts, money (Temporal, Step Functions) |
| **Choreographed saga** | Services react to each other's events | Flow written down nowhere; hard to trace; cycles | 2–3 steps, loosely coupled teams |
| **Design it away** | Put the rows one transaction touches in one service/shard | Boundary constraints | Whenever possible |

- **Default:** "a saga with compensations and idempotency keys, not 2PC."
- **Depth:** [[10 - Distributed Building Blocks]], [[07 - Event-Driven Architecture and System Trade-offs]]

### Making a saga safe

| Rule | Why |
|---|---|
| **Idempotent steps and compensations** | Messages arrive at least once |
| **Publish via outbox** | Otherwise the saga silently stalls |
| **Explicit states** (`PENDING` / `CONFIRMED` / `CANCELLED`) + timeouts | Other readers can see in-between states; sagas aren't ACID |
| **Irreversible steps last** (emails) | They can't be compensated |
| **Retry compensations until they succeed** | A refund that gives up leaves the system inconsistent |
| **Correlation ids and tracing** | "Where is order 42?" in choreography |
| **Durable orchestrator** | Resumes from saved state after a crash |

- **Depth:** [[10 - Distributed Building Blocks]], [[07 - Event-Driven Architecture and System Trade-offs]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[07 - Async Messaging]] · [[RabbitMQ and SQS]] · [[HLD/Topics/07 - Async Messaging/Kafka/README]] · [[10 - Distributed Building Blocks]]
