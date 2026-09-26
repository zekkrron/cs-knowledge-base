---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
---
# Replication, Durability and Consistency

> [!abstract]
> - Replicas are copies of a partition
> - **ISR** = the copies that are **caught up** with the leader
> - Consumers only read up to the **high watermark** (safely on every ISR member)
> - `acks=all` is only as strong as **how small you allow the ISR to get**
> - Unclean leader election is a **CAP switch**, not a Kafka bug

---

## Replica Sets and In-Sync Replicas (ISR)

### Replica set

- All copies of a partition
- Size = `replication.factor`
    + usually ==3==
- One **leader**, rest **followers**

### ISR

- The **subset** of the replica set that has caught up with the leader
- “Caught up” = within `replica.lag.time.max.ms`
- A slow follower **drops out** of the ISR
- New leader on crash: controller should pick from the **ISR**
    + not a replica that is 10 minutes behind
    + promoting a lagging replica would **lose** those 10 minutes of writes the old leader had

| Term | Meaning |
|---|---|
| **Replica set** | All copies. Size = `replication.factor` (usually ==3==). One leader. |
| **ISR** | Subset that is **caught up** (within `replica.lag.time.max.ms`). Slow follower **falls out**. |

---

## `min.insync.replicas`

### What it is

- Floor on ISR size for a produce with `acks=all` to succeed
- Leader waits until **at least this many** ISR members have the record
    + including itself, typically

### What happens when ISR shrinks below the floor

- Producer gets **`NotEnoughReplicas`**
- Cluster chose **don’t take the write** over **take it and maybe lose it**

> [!danger] The trap
> - `acks=all` + `min.insync.replicas=1`
> - ISR can shrink to **{leader} only**
> - You *said* “all replicas”
> - You have **one disk**
> - `acks=all` has silently become `acks=1`

### Combo they want

| Setting               | Value | Why                                                          |
| --------------------- | ----- | ------------------------------------------------------------ |
| Replication factor    | 3     | Two followers exist                                          |
| `min.insync.replicas` | 2     | Write must land on **at least two** disks                    |
| Producer `acks`       | `all` | Wait for the **current ISR**, which cannot be smaller than 2 |

- Two brokers dead → producer gets `NotEnoughReplicas`
    + you chose **consistency** over **availability**
    + that is the point, not a failure

---

## High Watermark (HW) vs Log End Offset (LEO)

| | What it is | Who cares |
|---|---|---|
| **LEO** | Next offset on **this** broker’s log (“I appended locally”) | Leader / that follower — each replica has its own LEO |
| **HW** | Highest offset that is on **every ISR member** (safely replicated) | **Consumers** — they may read only `< HW` |

```mermaid
flowchart LR
    A["Leader appends\nLEO moves"] --> B["Followers fetch"]
    B --> C["ISR has it\nHW moves"]
    C --> D["Consumer may read"]
```

### Sequence

- Produce hits the leader → **LEO** moves
- Followers fetch and append locally → their LEO catches up
- When the record is on **every ISR member** → **HW** moves
- Consumer is allowed to read only **below HW**

### Why consumers wait for HW

- Message only on the leader, HW not moved, leader dies
    + that message is **gone**
    + if a consumer had already seen it, they saw a ghost
- Under load, HW sits a few offsets **behind** LEO
    + that gap is “replicated but not yet acknowledged by all ISR”

> [!tip] If they draw a log
> - LEO = the tip
> - HW = a few offsets behind
> - Consumers live to the left of HW

---

## Unclean Leader Election

### The situation

- All ISR members are dead
- Only an **out-of-sync** (lagging) replica is up

| `unclean.leader.election.enable` | What happens | What you chose |
|---|---|---|
| **false** (what you want in prod) | Partition stays **offline**. No silent loss. | Consistency |
| **true** | Stale replica becomes leader. Bytes it never saw are **lost**. | Availability / uptime over the last N writes |

> [!tip] CAP in one sentence
> - Unclean election = **availability**
> - Refusing it = **consistency**
> - Not a Kafka bug — a switch you flip on purpose

---

## Related Notes

- [[Kafka/README]]
- [[04 - Producer Mechanics and Delivery Guarantees]] — `acks=all` is only as strong as this note
- [[01 - Core Architecture and Cluster Consensus]] — who promotes the new leader
