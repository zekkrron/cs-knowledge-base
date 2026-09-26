---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
---
# Producer Mechanics and Delivery Guarantees

> [!abstract]
> - Where a record lands (**partition**), when the producer believes it landed (`acks`), how retries don’t create ghosts (**idempotence / transactions**)
> - One record → **exactly one partition**
> - Order is **per partition only** — global order = one partition = no scale
> - Default you design for: **at-least-once + dedupe on `event_id`**
> - Batching is how you stop murdering the NIC
> - More partitions do **not** always mean more throughput — [[08 - Interview Questions]]

---

## Message Routing and Partitioning

### Where the record goes

- One record → **exactly one partition**

| Strategy | Rule | Order? | Use |
|---|---|---|---|
| **Key** | `murmur2(key) % n` | Same key → same partition → **ordered for that key** | `order_id` / `user_id` / `device_id` |
| **Sticky / default (no key)** | Fill a batch on one partition, then rotate | Throughput. **No** per-entity order | Firehose, no entity invariant |
| **Custom** | You write the partitioner | Whatever you coded | Rare — say why |

### Order vs scale

- Order is **per partition only**
- Global order = one partition = **no horizontal scale**
- Correct move: partition by a **business key**
    + that entity is ordered
    + the topic is still parallel across dozens of partitions

> [!warning] Adding partitions later
> - `hash % n` **changes** when `n` changes
> - Same `user_id` can land on a **new** partition
> - Old history stays on the **old** partition
> - “We’ll add partitions next year” is not free if you keyed by user

### How many partitions?

- First cut: `peak rate / rate-per-partition`
- **Useful consumers in a group ≤ partition count**
    + instance 11 with 10 partitions sits idle
- Pick a count that **divides** the consumer counts you will actually run
    + 12 partitions works for 2 / 3 / 4 / 6 instances
    + 10 partitions + 3 consumers → 4 / 3 / 3 — one box does more work
- Don’t open with 1000
    + more partitions = more files, more replication, more rebalance surface
    + and the producer gets slower before the cluster does — see below

---

## More partitions ≠ always more throughput

> [!tip] The one-liner they want
> - Throughput is `min(producer, broker, consumer)`
> - Parallelism in a group is `min(partitions, consumers)`
> - Adding partitions only helps while those are the limiter **and** keys are even
> - After that it plateaus, then it **hurts**

### Why people think “always”

- A partition is one **ordered** log with one **leader**
- More partitions → more leaders you can write in parallel → more consumers that can poll in parallel
- That is true **until** something else is the bottleneck

### What actually caps you

| Cap | What happens when you add partitions anyway |
|---|---|
| Consumers in the group `< n` | Each instance owns **more** partitions — more files, more fetch loops, more memory. Parallelism did **not** go up. |
| Consumers in the group `> n` | Extra instances sit **idle**. You needed partitions, not another pod. [[05 - Consumer Mechanics and Scalability]] |
| One **hot key** | That key still lands on **one** partition. `n = 1000` does not split `user_id=celebrity`. |
| Producer batching | Same QPS spread thinner → **smaller batches**, more requests, more RAM (`batch.size × n`) |
| Broker / controller | More open segment files, more replication, heavier metadata, **longer rebalances** |

### Producer-side cost of a huge `n`

- The producer keeps a **buffer per partition**
    + RAM ≈ `batch.size × partition count` (plus compression buffers)
    + 1000 partitions × 16 KB is already tens of MB **before** you talk payload
- Same produce rate, more partitions
    + each partition fills slower
    + `linger.ms` expires before `batch.size`
    + worse compression (compression is **per batch**)
    + more `ProduceRequest`s on the wire
- More leaders to discover and talk to
    + metadata refreshes get fatter
    + `acks=all` is waiting on **more** ISR sets
- This is why “just set it to 1000” makes **produce p99 worse** before it makes consume faster

### Hot partitions (key skew)

- `murmur2(key) % n` is even over **keys**, not over **traffic**
- Celebrity / bursty key
    + one partition is 80% of QPS
    + one consumer in the group is drowning
    + the other 99 partitions / consumers look “fine”
- More partitions **do not fix this**
    + that key still hashes to exactly one partition
    + splitting the key (e.g. `user_id + shard`) **breaks per-key order**
- Fixes you actually say
    + pick a key with real cardinality (`order_id`, not `country=IN`)
    + isolate the hot entity on its **own topic** if it is a known whale
    + accept that one key is one lane — that is the order contract
- Sticky / no-key partitioner
    + spreads load
    + **no** per-entity order
    + still can hot-spot if one producer process is the firehose

> [!warning] Adding partitions later still doesn’t fix a hot key
> - `hash % n` **changes** for *everyone*
> - The celebrity still maps to **one** (possibly new) partition
> - Old history stays behind
> - You paid the rebalance + hash-break tax and the whale is still a whale

### What you pick in the interview

- Start from **expected peak / per-partition rate** and **planned consumer count**
- Prefer a number that divides cleanly (12, 24, 48 — not 10 if you’ll run 3 instances)
- Leave **headroom**, don’t leave 900 empty partitions “for later”
- If they push “we’ll add partitions at 10×”
    + say the hash break
    + say the producer buffer cost
    + say a hot key still won’t split

---

## Acknowledgments (`acks`)

### What “success” means to the producer

| `acks` | Wait for | You can lose the write if… |
|---|---|---|
| `0` | Nobody | Leader never even saw it |
| `1` | Leader append | Leader dies before followers catch up |
| `all` / `-1` | Leader + **current ISR** | ISR shrank to 1 and `min.insync.replicas` allowed it |

### What `acks=all` is **not**

- Not “RF=3 acknowledged”
- Not “three disks have it”
- It is “whoever is **currently in the ISR** acknowledged”
    + if ISR is `{leader}` and `min.insync.replicas=1`, you have one disk
    + depth: [[03 - Replication Durability and Consistency]]

---

## Delivery Guarantees

| Name | How you get it | Honest meaning |
|---|---|---|
| At-most-once | Fire and forget / commit offset **before** work / `acks=0` | Can **lose** |
| At-least-once | Retry until ack; commit offset **after** work | **Duplicates**. This is the default. |
| Exactly-once | Idempotent produce + txns **inside Kafka** + idempotent **consumer** outside | **Effectively once** — not magic |

> [!tip] What you actually ship
> - At-least-once + **idempotent consumer** (`event_id` upsert / `INSERT … ON CONFLICT DO NOTHING`)
> - That *is* how grown-ups do exactly-once
> - Kafka EOS (below) stops *producer-retry* dupes and *multi-partition* half-writes
> - It does **not** stop a sloppy handler that ran twice outside Kafka

---

## Idempotent Producers

### The problem

- Network timeout → producer does not know if the broker got it
- Producer **sends again**
- Without help: **two copies** in the log

### What the broker remembers

- `enable.idempotence=true`
- Broker stores **PID + sequence number** per partition
- Duplicate retry → **dropped**

### What it stops vs what it does not

| Also true | Also false |
|---|---|
| Stops **network-retry** dupes at the broker | Stops “my consumer ran the handler twice” |
| Needs sane `max.in.flight` (Kafka sets this when idempotence is on) | Unlimited in-flight + no idempotence → **reorder** on retry |

### Reorder trap

- Force `max.in.flight > 5` or turn idempotence **off** with in-flight > 1
- A retry can **land before** an earlier batch
- Records in that partition are now **out of order**
- Idempotence on = Kafka keeps `max.in.flight` in a safe range so this doesn’t happen

---

## Transactional Coordinator

### What transactions are

- Atomic write to **several partitions / topics**
    + all visible, or none
- Broker-side:
    + **transactional coordinator** on a broker
    + internal topic `__transaction_state`
- This is **2PC among Kafka partitions**
    + **not** 2PC with your Postgres

### Where they actually live

- Home: **Kafka Streams** read-process-write
    + consume → process → produce
    + commit offsets in the **same** transaction
- Not home: API request that writes Postgres **and** Kafka
    + that is **outbox**
    + [[06 - Ecosystem Resilience and Design Patterns]]

### Consumers that must skip aborted txns

- Set `isolation.level=read_committed`
- Default can let you see records from a txn that later **aborted**
- Say this if they poke

---

## Batching and Compression

### Don’t send one record per syscall

| Knob | Effect |
|---|---|
| `batch.size` | Max bytes sitting in a batch **per partition** |
| `linger.ms` | Wait a few ms to fill the batch (latency ↑, throughput ↑) |
| Compression | Snappy / LZ4 / Zstd on the **batch**. CPU for less network / disk |

### Linger intuition

- `linger.ms=0` → snappy UX, more tiny requests
- `5–20 ms` → normal for pipelines
- Don’t linger **500 ms** on a user-facing “I clicked pay” event
    + and that produce usually **shouldn’t** be on the HTTP path anyway
    + HTTP path writes the DB; a poller / CDC publishes — [[06 - Ecosystem Resilience and Design Patterns]]

---

## Related Notes

- [[Kafka/README]]
- [[03 - Replication Durability and Consistency]]
- [[05 - Consumer Mechanics and Scalability]] — `n` vs `c`, idle consumers
- [[06 - Ecosystem Resilience and Design Patterns]]
- [[08 - Interview Questions]]
