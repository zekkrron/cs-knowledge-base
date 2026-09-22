---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
---
# Interview Questions

> [!abstract]
> - Mouth version of the grill
> - Each answer is the **full senior answer**, not a one-liner
> - Depth notes: [[01 - Core Architecture and Cluster Consensus]] … [[07 - Event-Driven Architecture and System Trade-offs]]
> - Extras I added (not in your original list): partition count / adding partitions breaks the hash; auto vs manual commit; `max.in.flight` + reorder; tombstones; Kafka as a delayed job queue (don’t)

---

## System Design and Trade-offs

### When would you choose RabbitMQ or SQS over Kafka, and vice versa?

- Do **not** stop at “Kafka is for streaming”
- Frame it as **smart broker vs smart consumer**

| Want | Pick | Why |
|---|---|---|
| Replay, many groups, CDC, per-key order at scale | **Kafka** | Dumb broker, smart consumer. Immutable log. Independent groups each read the same history. |
| NACK this message, priority, fancy routing, “do once” | **Rabbit** | Smart broker. Tracks message state. Exchanges / bindings. Deletes on ack. |
| No cluster, no replay | **SQS** | Zero-maintenance. Standard = no order. FIFO = order + **TPS cap**. |

- Rabbit
  - tracks message state on the broker
  - complex routing, priority queues, per-message ack / NACK
  - deletes the message once read
- Kafka
  - retains messages on disk as an immutable log
  - consumers track their own offsets
  - ideal for event replay, strict *per-partition* order, massive fan-out
  - several independent microservices read the **exact same** event history
- SQS
  - best for serverless decoupling where you will not sell replay
  - FIFO if you need order — say the throughput cap out loud
- Depth: [[07 - Event-Driven Architecture and System Trade-offs]]

---

### How would you handle a database write followed by a message publication so both succeed or both fail?

- Name it: **distributed transaction / dual-write problem**
- Immediately bring up the **Outbox Pattern**
- Writing to a database and then publishing to Kafka in the **same API request** is an anti-pattern
  - crash between the two
  - network partition
  - broker down after the DB commit
- The fix
  - write the event to an **outbox table** in the **same DB transaction** as the primary write
  - then CDC (Debezium via Kafka Connect) tails the DB’s **WAL** and publishes to Kafka
  - or a poller with `SKIP LOCKED` if you don’t have Debezium
- What you get
  - **eventual** consistency
  - no 2PC with the broker
  - if the row committed, the event **will** be published
- Depth: [[06 - Ecosystem Resilience and Design Patterns]]

---

## Data Integrity and Delivery Guarantees

### Explain how “Exactly-Once Semantics” (EOS) works. Is it truly exactly once?

- In distributed systems, “exactly-once” means **effectively once** (idempotent processing)
- Two Kafka mechanisms, plus **your** handler

| Piece | Stops |
|---|---|
| Idempotent producer (PID + sequence) | Retry **dupes** at the broker |
| Transactions (`__transaction_state`, 2PC among Kafka partitions) | Half-written **multi-partition** produce — the Streams read-process-write loop |
| **Your** upsert on `event_id` | Handler ran twice **outside** Kafka |

- Idempotent producer
  - PID + sequence numbers
  - broker drops a retry that it already appended
- Kafka transactions
  - atomic write to multiple partitions / topics
  - used hard in Kafka Streams
  - **not** 2PC with Postgres
- Your consumer
  - still has to be idempotent
  - Kafka cannot stop you from charging the card twice in your own code
- Depth: [[04 - Producer Mechanics and Delivery Guarantees]]

---

### Relationship between `acks=all` and `min.insync.replicas`. Can you still lose data with `acks=all`?

- **Yes**
- `acks=all` = the leader waits for **all replicas currently in the ISR** to ack
  - not “RF=3 acknowledged”
- If `min.insync.replicas=1`, ISR can shrink to **{leader} only**
  - `acks=all` has silently become `acks=1`
  - you have one disk
- Safe combo
  - RF = 3
  - `min.insync.replicas` = 2
  - producer `acks` = `all`
- If two brokers crash
  - producer gets `NotEnoughReplicasException`
  - you sacrificed **availability** to keep **consistency**
  - that is the point
- Depth: [[03 - Replication Durability and Consistency]]

---

## Consumer Scalability and Failure Modes

### A consumer deserializes, crashes. Group rebalances. Next consumer crashes on the same record. How do you stop the loop?

- Name it: **poison pill**
- Kafka consumers manage their own offsets
  - fail to process → offset **never advances**
  - every new owner dies on the same record
- Fix: **DLQ topology**
  - catch the exception locally
  - log it
  - route the malformed payload to a separate DLQ topic
  - **deliberately commit** the bad offset
  - group advances to the next record
- Transient errors (503, timeout)
  - retry topic + backoff
  - not `sleep(60)` on the hot partition
- Never retry a **400** on the same partition forever
- Depth: [[06 - Ecosystem Resilience and Design Patterns]]

---

### How does Cooperative Sticky Rebalancing fix Eager’s latency spikes?

| | Eager | Cooperative sticky |
|---|---|---|
| Partitions | All revoked | Only ones that **must move** |
| Group | Stop the world — processing **halts** | Rest keep reading |
| Redistribute | From scratch | Incremental handoff |

- Eager
  - entire group revokes **all** partitions
  - processing stops
  - new assignment from zero
  - that’s the spike
- Cooperative sticky
  - consumers **retain** current assignments
  - only revoke partitions that actually need to migrate
  - rest of the group keeps polling
- Add `group.instance.id` (static membership)
  - rolling restart ≠ leave + join
  - if you’re back before session timeout, you’re not a new stranger
- Depth: [[05 - Consumer Mechanics and Scalability]]

---

## Partitioning, Ordering, and Storage

### How do you get strict ordering without one-partition suicide?

- Kafka only guarantees order **inside one partition**
- One partition = order + **no scale**
- Correct approach: partition by a **business key**
  - `user_id` / `order_id` / `device_id`
  - all events for that entity land on the **same** partition
  - processed in order by **one** consumer thread
  - the topic still scales across dozens of partitions
- Adding partitions later **breaks** `hash % n`
  - same key can move
  - old history stays on the old partition
- Depth: [[04 - Producer Mechanics and Delivery Guarantees]]

---

### What is log compaction, and how is it different from retention?

| Retention | Compaction |
|---|---|
| Drop by **time** (e.g. 7 days) or **size** (e.g. 50 GB) | Keep the **latest value per key**, delete older updates for that key |
| Audit / clicks / “every event” | Changelog / state / `__consumer_offsets` |
| History of what happened | Current state of each key |
| Delete = wait for time | Delete = **tombstone** (`value = null`) |

- Compaction is how you keep a materialized view of a table in a topic
- `__consumer_offsets` is compacted
  - you care about the **last** commit per group-partition
  - not the entire history of commits
- Compaction is **background**, on old segments
  - the tail can still have two updates for the same key until it runs
- Depth: [[02 - Low-Level Storage and Performance IO]]

---

## Low-Level I/O and Performance

### Kafka is Java / Scala. How is it not GC-bound?

- Relies on the **OS page cache**, not the JVM heap, for in-flight data
- Writes **sequentially** to disk
- Consumer fetch uses **zero-copy (`sendfile`)**
  - bytes go page cache → socket
  - bypasses user-space
  - heap stays **small**
- Throughput is **network / sequential-disk** bound, not CPU / GC bound
- Fat `-Xmx` that starves the page cache **kills** this
- Depth: [[02 - Low-Level Storage and Performance IO]]

---

### Consumer is constantly lagging. How do you tune?

- First: is the bottleneck **network I/O** or **CPU / handler**?

| Bottleneck | Turn |
|---|---|
| Heavy processing | More **partitions + instances** (cap = partition count) |
| Network-bound / chatty | Bigger fetch — `fetch.min.bytes` / `fetch.max.wait.ms` — larger batches, less protocol overhead |
| Fake death | `max.poll.interval.ms` vs a slow poll loop |
| Order doesn’t strictly matter | Thread pool — **decouple** poll thread from processing. Order / EOS get harder. |

- Alert on **lag** (HW − committed offset)
- Scaling workers is the real fix; the log only buys time
- Depth: [[05 - Consumer Mechanics and Scalability]]

---

## Extra (still asked — not in your original list)

| Question | Full answer |
|---|---|
| How many partitions? | `peak rate / rate-per-partition`, and useful consumers in a group ≤ partition count. Don’t start at 1000 — more partitions = more files, more replication, more rebalance surface. |
| Auto vs manual commit? | Manual **after** success = at-least-once (crash → replay). Auto on a timer can commit **before** work → you **lose**. |
| `max.in.flight` without idempotence? | A retry can **land before** an earlier batch → records in that partition **reorder**. Idempotence on keeps `max.in.flight` in a safe range. |
| Tombstone? | Compacted delete: produce `key=X, value=null`. After compaction, X disappears. |
| Delayed job for 10 minutes? | Not Kafka’s job. Sleeping in a consumer blocks the partition. Use a scheduler ([[02 - Task Scheduler]]) and Kafka as dispatch if you want a log. |

---

## Related Notes

- [[Kafka/README]]
- [[07 - Async Messaging]]
- [[01 - Core Architecture and Cluster Consensus]]
- [[02 - Low-Level Storage and Performance IO]]
- [[03 - Replication Durability and Consistency]]
- [[04 - Producer Mechanics and Delivery Guarantees]]
- [[05 - Consumer Mechanics and Scalability]]
- [[06 - Ecosystem Resilience and Design Patterns]]
- [[07 - Event-Driven Architecture and System Trade-offs]]
