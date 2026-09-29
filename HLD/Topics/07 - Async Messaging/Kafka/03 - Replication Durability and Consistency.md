---
tags: [hld/topics/kafka, status/draft]
created: 2026-09-22
updated: 2026-09-29
---
# Replication, Durability and Consistency

> [!abstract] What this is
> How Kafka keeps copies of each partition and what "the write is safe" actually means. Replicas are copies of a partition; the **ISR** is the set of copies that are **caught up** with the leader. Consumers read only up to the **high watermark**, the point that is safely on every ISR member. `acks=all` is only as strong as **how small you let the ISR get**, and unclean leader election is a **CAP switch**, not a Kafka bug.

---

## Why It Exists

A single broker's disk is one failure away from losing data. Replication fixes that, but it raises three questions every design has to answer: which copies count as "safe," how many of them must have a write before you say yes, and what to do when the safe copies are all gone. Each section below answers one of those.

---

## Replica Sets and In-Sync Replicas (ISR)

The **replica set** is all copies of a partition. Its size is `replication.factor`, usually ==3==: one **leader** and the rest **followers**.

The **ISR** is the **subset** of the replica set that has caught up with the leader. "Caught up" means within `replica.lag.time.max.ms`. A slow follower **drops out** of the ISR and rejoins once it catches up.

When a leader crashes, the controller should pick the new leader from the **ISR**, not from a replica that is 10 minutes behind. Promoting a lagging replica would **lose** those 10 minutes of writes the old leader had.

| Term | Meaning |
|---|---|
| **Replica set** | All copies. Size = `replication.factor` (usually ==3==). One leader. |
| **ISR** | Subset that is **caught up** (within `replica.lag.time.max.ms`). Slow follower **falls out**. |

---

## `min.insync.replicas`

`min.insync.replicas` is a floor on ISR size for a produce with `acks=all` to succeed. The leader waits until **at least this many** ISR members have the record, typically counting itself.

If the ISR shrinks below the floor, the producer gets **`NotEnoughReplicas`**. The cluster has chosen **don't take the write** over **take it and maybe lose it**.

> [!danger] The trap
> With `acks=all` and `min.insync.replicas=1`, the ISR can shrink to **{leader} only**. You *said* "all replicas," but you have **one disk**. `acks=all` has silently become `acks=1`.

### The combination to say

| Setting | Value | Why |
|---|---|---|
| Replication factor | 3 | Two followers exist |
| `min.insync.replicas` | 2 | Write must land on **at least two** disks |
| Producer `acks` | `all` | Wait for the **current ISR**, which cannot be smaller than 2 |

With this setup, if two brokers die the producer gets `NotEnoughReplicas`. You chose **consistency** over **availability**. That's the point, not a failure.

---

## High Watermark (HW) vs Log End Offset (LEO)

Every replica has its own log tip, but consumers need one safe line they can read up to. These two offsets are those two ideas.

| | What it is | Who cares |
|---|---|---|
| **LEO** | Next offset on **this** broker's log ("I appended locally") | Leader / that follower. Each replica has its own LEO |
| **HW** | Highest offset that is on **every ISR member** (safely replicated) | **Consumers**. They may read only `< HW` |

```mermaid
flowchart LR
    A["Leader appends<br/>LEO moves"] --> B["Followers fetch"]
    B --> C["ISR has it<br/>HW moves"]
    C --> D["Consumer may read"]
```

The sequence is: a produce hits the leader and its **LEO** moves. Followers fetch and append locally, so their LEOs catch up. When the record is on **every ISR member**, the **HW** moves. Consumers are only allowed to read **below HW**.

### Why consumers wait for HW

Suppose a message exists only on the leader, HW hasn't moved yet, and the leader dies. That message is **gone**. If a consumer had already read it, it saw a ghost: a record that, as far as the cluster is concerned, never happened. Waiting for HW prevents that.

Under load, HW sits a few offsets **behind** LEO. That gap is the records that are appended on the leader but not yet on every ISR member.

> [!tip] If they ask you to draw a log
> LEO is the tip. HW is a few offsets behind. Consumers live to the left of HW.

---

## Unclean Leader Election

The hard case: all ISR members are dead, and only an **out-of-sync** (lagging) replica is up. Kafka has to choose between staying down and coming back with missing data.

| `unclean.leader.election.enable` | What happens | What you chose | Pick when |
|---|---|---|---|
| **false** (what you want in prod) | Partition stays **offline**. No silent loss. | Consistency | Orders, payments, anything where losing writes is worse than downtime |
| **true** | Stale replica becomes leader. Bytes it never saw are **lost**. | Availability / uptime over the last N writes | Metrics or logs where a gap is fine but an outage isn't |

> [!tip] CAP in one sentence
> Unclean election is **availability**; refusing it is **consistency**. It's not a Kafka bug, it's a switch you flip on purpose. Theory in [[17 - Consistency Time and Consensus]].

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| `acks=all` with `min.insync.replicas=1` | ISR shrinks to the leader; one disk holds the write; leader dies and it's lost | RF=3, `min.insync.replicas=2` |
| Two of three brokers down | Producer gets `NotEnoughReplicas`; writes stop | Expected. It's the consistency choice. Restore brokers |
| Slow follower | Drops out of the ISR; durability margin shrinks | Alert on ISR shrink; fix disk / network on that broker |
| All ISR dead, unclean election on | Stale replica leads; recent writes lost silently | Keep `unclean.leader.election.enable=false` for important topics |
| Consumer reads past HW (hypothetical) | Would see ghost records after leader failover | Kafka prevents it: reads stop at HW |

---

## Interview Lines

> [!tip] Say it like this
> - "The ISR is the set of replicas caught up with the leader. New leaders come from the ISR."
> - "RF=3, `min.insync.replicas=2`, `acks=all`. Every acknowledged write is on at least two disks."
> - "`acks=all` means the current ISR, not all replicas. With `min.insync.replicas=1` it's really `acks=1`."
> - "Consumers only read below the high watermark, so they never see a record that could vanish on failover."
> - "Unclean leader election trades the last writes for uptime. I keep it off for anything that matters."

## Related Notes

- [[Kafka/README]]
- [[04 - Producer Mechanics and Delivery Guarantees]] (`acks=all` is only as strong as this note)
- [[01 - Core Architecture and Cluster Consensus]] (who promotes the new leader)
- [[17 - Consistency Time and Consensus]]
