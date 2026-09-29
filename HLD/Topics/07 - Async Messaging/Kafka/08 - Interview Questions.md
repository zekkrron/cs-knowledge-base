---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
updated: 2026-09-29
---
# Interview Questions

> [!abstract] What this is
> The Kafka questions interviewers actually ask, with the answer you'd say out loud. Each group opens with a summary table (question, short answer, where the depth lives), followed by the full answer for when they keep pushing. The depth notes run from [[01 - Core Architecture and Cluster Consensus]] to [[07 - Event-Driven Architecture and System Trade-offs]].
>
> Beyond the core list, the bank includes the follow-ups that come up often: more partitions is not always more throughput, hot partitions, auto vs manual commit, `max.in.flight` and reordering, tombstones, and why Kafka makes a poor delayed job queue.

---

## System Design and Trade-offs

These questions test whether you can pick a tool by the shape of the problem rather than by reputation. The strongest framing for both is **smart broker vs smart consumer**, and naming the **dual-write problem** before anyone else does.

| Question | Short answer | Deep link |
|---|---|---|
| When would you choose RabbitMQ or SQS over Kafka, and vice versa? | Smart broker (Rabbit) for jobs, NACK, priority, routing. Smart consumer (Kafka) for replay, many groups, CDC, per-key order at scale. SQS for zero ops and no replay. | [[07 - Event-Driven Architecture and System Trade-offs]] |
| How would you handle a database write followed by a message publication so both succeed or both fail? | That's the dual-write problem. Outbox table in the same DB transaction, then Debezium (CDC) or a `SKIP LOCKED` poller publishes. Eventual consistency, no 2PC. | [[06 - Ecosystem Resilience and Design Patterns]] |

### When would you choose RabbitMQ or SQS over Kafka, and vice versa?

Don't stop at "Kafka is for streaming". Frame it as **smart broker vs smart consumer**.

| Want | Pick | Why |
|---|---|---|
| Replay, many groups, CDC, per-key order at scale | **Kafka** | Dumb broker, smart consumer. Immutable log. Independent groups each read the same history. |
| NACK this message, priority, fancy routing, "do once" | **Rabbit** | Smart broker. Tracks message state. Exchanges / bindings. Deletes on ack. |
| No cluster, no replay | **SQS** | Zero maintenance. Standard = no order. FIFO = order + **TPS cap**. |

When they want the full comparison, walk through each system's traits.

| Trait | RabbitMQ | Kafka | SQS |
|---|---|---|---|
| Message state | Tracked on the broker (smart broker) | Consumers track their own offsets (**pull-based**) | AWS runs the counter; visibility timeout, so a crash makes the ticket **reappear** |
| Storage after consume | Deleted once acked | Retained on disk as an immutable log | Deleted on delete |
| Per-message failure | Ack / NACK / reject: retry **this** ticket, or dead-letter it | No first-class NACK; the commit is a bookmark, not "this one ticket". Poison pill: **you** write a DLQ topic, then move the bookmark. | Built-in DLQ (redrive) |
| Workers | Competing consumers; add workers, they grab the next ticket | Scale in one group = **partition count**; workers past `n` sit idle | Competing consumers; just add workers |
| Routing | Exchanges / bindings; broker sorts by key or headers | Topic + key → partition | One queue |
| Priority | Priority queues | No first-class priority | None; make a high queue and a low queue |
| Delay | Delay / TTL: "come back later", ticket dies if nobody takes it | No first-class delay or scheduled job; sleeping **blocks that lane** | Built-in delay (up to 15 min) |
| Order | Per queue | Strict **per-partition** (not global) | Standard: **no order**, at-least-once, can dupe. FIFO: order per group id + dedup; say the **throughput cap** out loud. |
| Replay | Awkward; after ack it's gone. New subscriber starts **from now** (no last week). | Reset the offset / start from a timestamp. A new team catches up if **retention** still has the pages (time decoupling). | None |
| Fan-out | **Copy** into many queues at publish time | Many independent groups, **one** copy of the bytes; several microservices read the **exact same** event history | SNS → many queues, **from now** (no last week); no many-groups-on-one-log |
| Performance | Often **lower latency** for a single small job; **throughput** is not the point (lower than Kafka) | Optimizes **throughput**, pays a bit of **latency** (batch / linger) | Fine for workers |
| Backlog | Lives **on your machine**; a fat queue can hurt the node | On disk; you pay for disk + retention + consumer-lag ops | Lives at Amazon; can get huge and you pay for the pile |
| Other patterns | Request-reply (RPC) is normal | CDC / Streams / compaction live here. At-least-once by default; EOS is **inside** Kafka, not with your DB. | Cost is **API calls** (send / receive / delete). Message cap 256 KB (big blobs → S3 + id). AWS-only. |
| Who runs it | You (or CloudAMQP / Amazon MQ) | You (or MSK) run a cluster | Zero cluster on your on-call |
| Good for | Jobs, "do this once" | Facts, the company event log. Bad as a delayed job queue. | Serverless / Lambda / "decouple this worker", when you will **not** sell replay |

### How would you handle a database write followed by a message publication so both succeed or both fail?

Name it first: this is the **distributed transaction / dual-write problem**, and the answer is the **Outbox Pattern**.

Writing to the database and then publishing to Kafka in the **same API request** is an anti-pattern. The process can crash between the two, the network can partition, or the broker can be down right after the DB commit.

The fix is to write the event to an **outbox table** in the **same DB transaction** as the primary write. Then CDC (Debezium via Kafka Connect) tails the database's **WAL** and publishes to Kafka, or a poller using `SKIP LOCKED` does the same job if you don't have Debezium.

What you get is **eventual** consistency with no 2PC against the broker, and a simple guarantee: if the row committed, the event **will** be published.

---

## Data Integrity and Delivery Guarantees

These questions check whether you know what Kafka's guarantees actually cover. The theme of both answers is that the guarantee has edges: exactly-once stops at Kafka's boundary, and `acks=all` is only as strong as `min.insync.replicas`.

| Question | Short answer | Deep link |
|---|---|---|
| Explain how Exactly-Once Semantics (EOS) works. Is it truly exactly once? | It's "effectively once". Idempotent producer stops retry dupes, transactions make multi-partition writes atomic, and **your** handler must still be idempotent. | [[04 - Producer Mechanics and Delivery Guarantees]] |
| What's the relationship between `acks=all` and `min.insync.replicas`? Can you still lose data with `acks=all`? | Yes. `acks=all` waits for the current ISR, which can shrink to just the leader. Use RF=3, `min.insync.replicas=2`, `acks=all`. | [[03 - Replication Durability and Consistency]] |

### Explain how Exactly-Once Semantics (EOS) works. Is it truly exactly once?

In distributed systems, "exactly-once" means **effectively once**: processing is idempotent, so running twice has the same effect as running once. Kafka provides two mechanisms, and **your** handler provides the third.

| Piece | How it works | Stops |
|---|---|---|
| Idempotent producer | PID + sequence numbers; the broker drops a retry it already appended | Retry **dupes** at the broker |
| Transactions | `__transaction_state`, 2PC among Kafka partitions; atomic write to multiple partitions / topics, used heavily in Kafka Streams | Half-written **multi-partition** produce in the Streams read-process-write loop |
| **Your** upsert on `event_id` | Consumer is idempotent | Handler ran twice **outside** Kafka |

Kafka transactions are **not** 2PC with Postgres. Your consumer still has to be idempotent, because Kafka cannot stop you from charging the card twice in your own code.

### Relationship between `acks=all` and `min.insync.replicas`. Can you still lose data with `acks=all`?

**Yes.** `acks=all` means the leader waits for **all replicas currently in the ISR** to acknowledge, not "all RF=3 replicas acknowledged". If `min.insync.replicas=1`, the ISR can shrink to **just the leader**. At that point `acks=all` has silently become `acks=1`, and your data lives on one disk.

| Setting | Safe value |
|---|---|
| Replication factor | 3 |
| `min.insync.replicas` | 2 |
| Producer `acks` | `all` |

With that combination, if two brokers crash the producer gets `NotEnoughReplicasException`. You've sacrificed **availability** to keep **consistency**, and that is the point.

---

## Consumer Scalability and Failure Modes

These questions are about what happens when consumers misbehave or the group reshuffles. Interviewers want the name of the failure (poison pill, stop-the-world rebalance) and the standard fix.

| Question | Short answer | Deep link |
|---|---|---|
| A consumer deserializes, crashes. Group rebalances. Next consumer crashes on the same record. How do you stop the loop? | Poison pill. Catch, log, send to a DLQ topic, **commit the bad offset**, move on. Transient errors go to a retry topic with backoff. | [[06 - Ecosystem Resilience and Design Patterns]] |
| How does Cooperative Sticky Rebalancing fix Eager's latency spikes? | Eager revokes everything and stops the group. Cooperative sticky only revokes partitions that must move. Add static membership for rolling restarts. | [[05 - Consumer Mechanics and Scalability]] |

### A consumer deserializes, crashes. Group rebalances. Next consumer crashes on the same record. How do you stop the loop?

Name it: a **poison pill**. Kafka consumers manage their own offsets, so if processing fails the offset **never advances**, and every new owner of the partition dies on the same record.

The fix is a **DLQ topology**: catch the exception locally, log it, route the malformed payload to a separate DLQ topic, and **deliberately commit** the bad offset so the group advances to the next record.

Transient errors (503, timeout) are different. Send them to a retry topic with backoff, not `sleep(60)` on the hot partition. And never retry a **400** on the same partition forever.

### How does Cooperative Sticky Rebalancing fix Eager's latency spikes?

Under **eager** rebalancing, the entire group revokes **all** partitions, processing stops, and the new assignment is built from zero. That pause is the spike. Under **cooperative sticky**, consumers **retain** their current assignments and only revoke partitions that actually need to migrate, so the rest of the group keeps polling.

| | Eager | Cooperative sticky |
|---|---|---|
| Partitions | All revoked | Only the ones that **must move** |
| Group | Stops the world; processing **halts** | Rest keep reading |
| Redistribute | From scratch | Incremental handoff |

Add `group.instance.id` (static membership) on top. Then a rolling restart isn't a leave + join: if the instance is back before the session timeout, it isn't a new stranger and no rebalance happens.

---

## Partitioning, Ordering, and Storage

These questions probe whether you understand what a partition really buys you. The recurring idea is that partitions give parallelism **across keys**, never within one key, and that each extra partition has a cost.

| Question | Short answer | Deep link |
|---|---|---|
| If I keep increasing partitions, will throughput always increase? | No. Throughput is `min(producer, broker, consumer)`, and group parallelism is `min(n, c)`. It plateaus, then hurts. | [[04 - Producer Mechanics and Delivery Guarantees]] · [[05 - Consumer Mechanics and Scalability]] |
| How do you get strict ordering without one-partition suicide? | Partition by a business key (`user_id`, `order_id`, `device_id`). Per-key order, topic still scales. | [[04 - Producer Mechanics and Delivery Guarantees]] |
| What is log compaction, and how is it different from retention? | Retention drops by time or size. Compaction keeps the latest value per key; deletes are tombstones. | [[02 - Low-Level Storage and Performance IO]] |

### If I keep increasing partitions, will throughput always increase?

**No.** Don't stop at "partitions are parallelism". End-to-end throughput is `min(producer, broker, consumer)`, and parallelism **within one group** is `min(n partitions, c consumers)`.

Adding partitions only helps while three things are true: you have consumers ready to use them (`c` was already at `n`, so a new partition gives an extra consumer work), keys are even (no whale on one partition), and the producer can still fill batches. After that throughput **plateaus**, and then it **hurts**.

| They follow up with… | You say |
|---|---|
| Partition vs consumer balancing | `c < n` → one box owns more partitions (10/3 → 4/3/3). `c = n` → clean. `c > n` → extra pods **idle**. Adding consumers past `n` does nothing. |
| Idle consumers | Instance 11 of 10 partitions never gets a partition. You needed an 11th **partition**, not another pod. |
| Hot partitions | The hash is even over **keys**, not traffic. A celebrity `user_id` is one partition and one consumer. `n = 1000` does not split that key. Splitting the key breaks per-key **order**. |
| Producer-side bottlenecks | One buffer per partition (`batch.size × n` RAM). Same QPS across more partitions → tiny batches, worse compression, more ProduceRequests, fatter metadata, `acks=all` on more ISR sets. Produce p99 gets worse. |
| Ordering vs scale | Order is **one partition**. More partitions = more scale **across keys**; one key is still one lane. Adding partitions later also **breaks** `hash % n`. |

The broker and ops costs they also want to hear: more open segment files, more replication traffic, and longer rebalances (more things to assign).

What you pick: `peak rate / rate-per-partition`, with an `n` that **divides** the `c` values you'll run (12, 24, 48). Leave headroom, but not 900 empty partitions "for later".

### How do you get strict ordering without one-partition suicide?

Kafka only guarantees order **inside one partition**, and a single partition means order with **no scale**. The correct approach is to partition by a **business key** such as `user_id`, `order_id` or `device_id`. All events for that entity land on the **same** partition and are processed in order by **one** consumer thread, while the topic as a whole still scales across dozens of partitions.

The catch: adding partitions later **breaks** `hash % n`. The same key can move to a new partition while its old history stays on the old one.

### What is log compaction, and how is it different from retention?

Retention answers "how long do I keep history?" Compaction answers "what is the current value of each key?"

| | Retention | Compaction |
|---|---|---|
| Rule | Drop by **time** (e.g. 7 days) or **size** (e.g. 50 GB) | Keep the **latest value per key**, delete older updates for that key |
| Used for | Audit, clicks, "every event" | Changelogs, state, `__consumer_offsets` |
| Topic represents | History of what happened | Current state of each key |
| Deleting | Wait for time | **Tombstone** (`value = null`) |

Compaction is how you keep a materialized view of a table in a topic. `__consumer_offsets` is compacted because you only care about the **last** commit per group and partition, not the whole history of commits.

Compaction runs in the **background** on old segments, so the active tail can still hold two updates for the same key until it runs.

---

## Low-Level I/O and Performance

These questions check whether you know where Kafka's speed comes from and how to diagnose a slow consumer. Always identify the bottleneck before turning a knob.

| Question | Short answer | Deep link |
|---|---|---|
| Kafka is Java / Scala. How is it not GC-bound? | Data sits in the OS page cache, not the heap. Sequential writes, zero-copy `sendfile` reads. Network and disk bound, not GC bound. | [[02 - Low-Level Storage and Performance IO]] |
| Consumer is constantly lagging. How do you tune? | Find the bottleneck first: handler CPU, network chatter, fake death, or ordering constraints. Scale partitions + instances; alert on lag. | [[05 - Consumer Mechanics and Scalability]] |

### Kafka is Java / Scala. How is it not GC-bound?

Kafka relies on the **OS page cache**, not the JVM heap, for in-flight data. It writes **sequentially** to disk. Consumer fetches use **zero-copy (`sendfile`)**, so bytes go from the page cache straight to the socket, bypassing user space. The heap stays **small**, and throughput is bound by **network and sequential disk**, not CPU or GC.

The way to break this is a fat `-Xmx` that starves the page cache.

### Consumer is constantly lagging. How do you tune?

First ask whether the bottleneck is **network I/O** or **CPU / the handler**.

| Bottleneck | What to turn |
|---|---|
| Heavy processing | More **partitions + instances** (capped at the partition count) |
| Network-bound / chatty | Bigger fetches with `fetch.min.bytes` / `fetch.max.wait.ms`: larger batches, less protocol overhead |
| Fake death | `max.poll.interval.ms` vs a slow poll loop |
| Order doesn't strictly matter | Thread pool that **decouples** the poll thread from processing. Order and EOS get harder. |

Alert on **lag** (HW − committed offset). Scaling workers is the real fix; the log only buys you time.

---

## Extra Questions

These come up often as follow-ups even though they're rarely the opening question. Each has a short, confident answer.

| Question | Short answer | Deep link |
|---|---|---|
| How many partitions? | See "If I keep increasing partitions…" above. If they only want a number: `peak / per-partition`, `n` divides planned `c`, don't start at 1000. | [[04 - Producer Mechanics and Delivery Guarantees]] |
| Auto vs manual commit? | Manual **after** success = at-least-once (crash → replay). Auto on a timer can commit **before** the work → you **lose** it. | [[05 - Consumer Mechanics and Scalability]] |
| `max.in.flight` without idempotence? | A retry can **land before** an earlier batch, so records in that partition **reorder**. Idempotence keeps `max.in.flight` in a safe range. | [[04 - Producer Mechanics and Delivery Guarantees]] |
| Tombstone? | A compacted delete: produce `key=X, value=null`. After compaction, X disappears. | [[02 - Low-Level Storage and Performance IO]] |
| Delayed job for 10 minutes? | Not Kafka's job. Sleeping in a consumer blocks the partition. Use a scheduler, with Kafka as dispatch if you want a log. | [[02 - Task Scheduler]] |

---

## Interview Lines

> [!tip] Say it like this
> - "Kafka is a smart-consumer log; Rabbit is a smart-broker queue. I choose by the shape of the problem."
> - "I never dual-write. Outbox in the same transaction, CDC to Kafka, idempotent consumers."
> - "Exactly-once is effectively-once, and it stops at Kafka's boundary. My handler dedupes on `event_id`."
> - "`acks=all` only means something with `min.insync.replicas=2` on RF=3."
> - "Poison pills go to a DLQ and I commit past them."
> - "More partitions help until consumers, keys or producer batching become the limit. Then they hurt."

## Related Notes

- [[Kafka/README]]
- [[07 - Async Messaging]]
- [[RabbitMQ and SQS]]
- [[01 - Core Architecture and Cluster Consensus]]
- [[02 - Low-Level Storage and Performance IO]]
- [[03 - Replication Durability and Consistency]]
- [[04 - Producer Mechanics and Delivery Guarantees]]
- [[05 - Consumer Mechanics and Scalability]]
- [[06 - Ecosystem Resilience and Design Patterns]]
- [[07 - Event-Driven Architecture and System Trade-offs]]
- [[02 - Task Scheduler]]
