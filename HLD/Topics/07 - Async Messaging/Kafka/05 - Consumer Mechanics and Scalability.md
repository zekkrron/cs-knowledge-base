---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
---
# Consumer Mechanics and Scalability

> [!abstract]
> - Consumers **pull** (that is backpressure) — they poll, the broker does not push
> - A **group** splits partitions; progress is an **offset** in `__consumer_offsets`
> - Two groups on the same topic each see **every** record
> - Rebalance used to stop the world (**eager**); **cooperative sticky** + **static membership** don’t have to
> - Two liveness clocks — mix them up and you get a fake-death storm

---

## Pull vs Push Consumption

### Kafka pulls

- The consumer **polls**
- If you are slow, you poll less / fetch smaller
- The log waits (until retention)
- That is **consumer-managed backpressure**

### Why not push

- A push broker would pile memory on the consumer **or** the broker
- Kafka does not babysit your in-flight list on the broker

### Contrast with Rabbit

- Rabbit **pushes** / tracks unacked messages
- Rabbit can **prefetch**
- Kafka: you ask, you get a batch, you commit an offset when *you* decide you’re done

---

## Consumer Groups and Offsets

### Two groups = two independent subscribers

- `email-workers` and `analytics` on `orders`
  - each group sees **every** order
  - they do not steal records from each other

### Inside one group

- One partition → **at most one** consumer (classic assignment)
- Scale out by adding instances **up to partition count**
- Instance 11 with 10 partitions sits **idle**

```mermaid
flowchart LR
    T["Topic\n4 partitions"] --> C1[Consumer A]
    T --> C2[Consumer B]
    T --> Idle[Consumer C idle]
```

- 4 partitions, 3 consumers in the **same** group
  - two of them get work
  - one sits idle
  - you needed a 4th partition, not a 3rd consumer

### Offset

- “Next record **this group** wants on **this partition**”
- Stored in topic `__consumer_offsets`
  - **compacted** — you care about the last commit, not every commit ever
  - [[02 - Low-Level Storage and Performance IO]]

| Commit style | What happens on crash | Failure mode |
|---|---|---|
| **Auto** (interval) | Easy to commit **before** you finished | **Lose** work (at-most-once) |
| **Manual after success** | Crash before commit → **replay** | Duplicates (at-least-once). This is what you want. |

### Commit frequency

- Too often = hammer `__consumer_offsets`
- Too rare = long replay after a crash
- Manual after success is the default you design for

### Lag

- **Lag** = HW − committed offset
- Alert on it
- Fix
  - more partitions + more instances (cap = partition count)
  - faster handler
  - produce slower
- Depth of “always lagging”: [[08 - Interview Questions]]

---

## Rebalance Protocols

### What triggers a rebalance

- Member **joins**
- Member **leaves**
- Topic’s **partition count** changes
- Someone is **kicked**
  - session timeout (heartbeat)
  - poll interval (processing)

### Eager (old, stop-the-world)

- Every member **revokes all** partitions
- Whole group **stops**
- New assignment from scratch
- That’s the **latency spike** they complain about

### Cooperative sticky (what you want)

- Keep partitions you still own
- Only **revoke the ones that must move**
- Rest of the group keeps polling
- “Sticky” = prefer not to shuffle for fun

| | Eager | Cooperative sticky |
|---|---|---|
| Partitions | All revoked | Only ones that **must move** |
| Group | Stop the world | Rest keep reading |
| Feel | Latency spike | Incremental handoff |

---

## Static Group Membership

### What `group.instance.id` is

- This process is a **named** member
- Not a random UUID each start

### Rolling restart

- Instance dies and comes back **before** session timeout
- Group does **not** treat it as a new stranger
- **No rebalance** (or a much smaller one)

### Dynamic membership (the default you don’t want for stateful nodes)

- Every deploy looks like leave + join
- Revoke storm every rollout

### When to use it

- Stateful consumers
- Kafka Streams nodes that should own the **same** partitions across restarts

---

## Consumer Liveness Knobs

### Two different “are you alive?” clocks

| Knob | Watches | Thread | If you miss it |
|---|---|---|---|
| `heartbeat.interval.ms` / `session.timeout.ms` | **Network** heartbeat | Heartbeat thread | Coordinator: you’re dead → rebalance |
| `max.poll.interval.ms` | Time **between polls** | Your processing / poll loop | Kicked even if heartbeats were **fine** |

> [!danger] Classic outage
> - Fat handler + small `max.poll.interval.ms`
> - Coordinator thinks you’re dead (**fake death**)
> - Rebalance
> - Next owner hits the **same** slow record
> - Storm

### Fixes

- Raise `max.poll.interval.ms` for fat work
- **Process faster** — shrink the handler
- Smaller `max.poll.records`
  - smaller batches so you can poll again before the interval
- Thread pool **only if**
  - you can still commit offsets correctly
  - order / exactly-once get harder the moment you leave the poll thread
  - don’t do this if per-key order is the invariant

---

## Related Notes

- [[Kafka/README]]
- [[08 - Interview Questions]]
- [[04 - Producer Mechanics and Delivery Guarantees]]
- [[02 - Task Scheduler]] — don’t fake a delay by sleeping in the consumer
