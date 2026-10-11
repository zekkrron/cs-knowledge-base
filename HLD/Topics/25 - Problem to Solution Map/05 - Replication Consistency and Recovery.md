---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Replication, Consistency and Recovery

> [!abstract] What this is
> Problems that come from **having more than one copy**: choosing consistency per path, stale reads from replicas, losing writes on failover, split brain, quorum tuning, conflicting writes, ordering events across machines, consensus, multi-region writes, and recovering from disasters and bad writes.

---

## Choosing Consistency

### Which consistency does each path need?

- **Signal:** "strong or eventual?"; or one model forced on everything.

| Model | Costs you | Pick for |
|---|---|---|
| **Linearizable / strong** | Majority coordination; minority side stops | Money, stock, unique seats, locks, unique usernames, leader election |
| **Sequential** | Global order but reads may lag | Config and coordination data (ZooKeeper) |
| **Causal** | Track dependencies | Comment replies, chat threads |
| **Session guarantees** (read-your-writes, monotonic reads) | Sticky routing or version tokens | User-facing CRUD on async replicas |
| **Eventual** | Stale reads; name the lag | Feeds, counts, search indexes, analytics |

- **Default:** choose **per path**: "the seat row is CP, the view counter is AP."
- **Depth:** [[17 - Consistency Time and Consensus]], [[02 - Fundamentals]]

### Network partition: refuse or serve stale?

| Option | Costs you | Pick when |
|---|---|---|
| **CP**: refuse on the minority side | Availability | "Rather reject than double-book" |
| **AP**: keep answering | Stale or conflicting answers | Feeds, counters |
| **PACELC, healthy network**: wait for coordination vs read nearest copy | Latency vs staleness | Per query (Cassandra `QUORUM`, DynamoDB strong reads, primary vs replica) |

- **Depth:** [[17 - Consistency Time and Consensus]], [[02 - Fundamentals]]

---

## Replica Reads

### User saves, refreshes, and sees old data (read-your-writes)

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Sticky to primary briefly** | Session flag routes this user to the primary for a few seconds after a write | Primary load | Simple default |
| **Read-after-write from primary** for known flows | Profile, settings always read the primary | Per-flow routing | A few known flows |
| **Causal token** | Client carries the write's WAL position/timestamp; read only from a caught-up replica | Complexity | Heavy read offload |
| **Mongo causally consistent session** + `w:majority` + majority reads | Secondary waits until caught up | Latency | MongoDB |
| **Accept lag** | Nothing | Staleness | Other people's data |

- **Depth:** [[08 - Replication]], [[17 - Consistency Time and Consensus]], [[23 - MongoDB]]

### Values go backwards between refreshes / writes land out of order

| Problem | Fix |
|---|---|
| **Monotonic reads** violated | Pin the session to one replica |
| **Monotonic writes** violated | Route a session's writes through one leader, or sequence them |
| **Reply visible before its comment** | Causal consistency: attach the version you read to the write |

- **Depth:** [[17 - Consistency Time and Consensus]]

### Replica lag reaches seconds under load

- **Fixes:** monitor lag; route read-your-writes paths to the primary; throttle backfills on lag; drop lagging replicas from the read endpoint. A **sync** replica doesn't guarantee fresh reads either (`synchronous_commit = on` waits for flush, not apply; only `remote_apply` does).
- **Depth:** [[08 - Replication]]

### Routing reads to replicas and writes to the primary

- **Write endpoint:** a name that follows the primary (DNS updated on failover, HAProxy + Patroni health check, `target_session_attrs=read-write`). **Read endpoint:** round-robin / least-connections across replicas with health and lag checks. Routing is **per connection**, so read-your-writes stays the app's job.
- **Depth:** [[08 - Replication]], [[03 - Networking and APIs]]

---

## Failover

### Async failover loses the last committed writes

- **Signal:** "we cannot lose the last payment"; RPO ≈ replication lag.

| Option | Costs you | Pick when |
|---|---|---|
| **Sync replica** (one, for durability) | Commit waits for the replica; writes block if it dies | Zero-loss data |
| **Two sync candidates / quorum commit** | More hardware | Durability *and* availability |
| **Degrade to async when the sync replica dies** | Brief loss window | Availability over durability |
| **Accept RPO ≈ lag** | Some loss | Most data |
| **Mongo `w:"majority"`** | Higher write latency | Writes must survive failover (`w:1` can be rolled back) |
| **Kafka `acks=all` + `min.insync.replicas=2` + RF 3** | `NotEnoughReplicas` when two brokers are down | Anything that matters |
| **Redis `WAIT` / `min-replicas-to-write`** | Narrows the window, isn't consensus | Redis only; keep truth in a DB |

- **Depth:** [[08 - Replication]], [[23 - MongoDB]], [[03 - Replication Durability and Consistency]], [[21 - Redis]]

### Split brain: two primaries accept writes

- **Signal:** the old primary comes back after a partition or GC pause and keeps writing.

| Option | How it works | Pick when |
|---|---|---|
| **Majority-only elections** (Raft terms, Patroni via etcd) | Only the side with a majority can elect | Always |
| **Fencing tokens / epochs** checked by storage | Storage rejects writes from an older term | A stale leader may still act |
| **STONITH** | Power off or isolate the old node before promoting | Storage can't check tokens |
| **Refuse writes when isolated** (Redis `min-replicas-to-write`, Kafka unclean election off) | Old primary stops on its own | Bound the damage |

- **Depth:** [[17 - Consistency Time and Consensus]], [[08 - Replication]]

### Who promotes the new primary?

- **Postgres:** nothing built in. Patroni + etcd (leader key with TTL), or the managed service's control plane. **Mongo:** Raft-like elections inside the replica set (odd voters; prefer data-bearing members over arbiters). **Kafka:** the controller (KRaft quorum) promotes an ISR member. **Redis:** Sentinel (3+ on separate machines, quorum).
- **Depth:** [[08 - Replication]], [[23 - MongoDB]], [[01 - Core Architecture and Cluster Consensus]], [[21 - Redis]]

### Kafka: every in-sync replica is dead

| Option | Costs you | Pick when |
|---|---|---|
| **`unclean.leader.election.enable=false`** | Partition stays offline | Orders, payments |
| **`true`** | Stale replica leads; recent writes silently lost | Metrics/logs where gaps beat outages |

- **Depth:** [[03 - Replication Durability and Consistency]]

### A replica falls too far behind to catch up

| Store | Problem | Fix |
|---|---|---|
| **Postgres** | Primary recycled WAL the replica needs | Replication slot (but monitor it; cap with `max_slot_wal_keep_size`) |
| **Postgres** | Stale slot fills the primary's disk | Alert on slot lag; drop slots for dead consumers |
| **Mongo** | Secondary is outside the oplog window | Size the oplog for the longest outage; alert on window and lag |
| **Kafka** | Follower dropped out of the ISR | Alert on ISR shrink; fix that broker |

- **Depth:** [[08 - Replication]], [[23 - MongoDB]], [[03 - Replication Durability and Consistency]]

---

## Leaderless Replication and Conflicts

### Quorum tuning in a leaderless store

| Setting | Behaviour | Pick when |
|---|---|---|
| **N=3, W=2, R=2** (R + W > N) | Reads overlap the latest write; tolerates one node down | Default |
| **W=3, R=1** | Writes fail if any node is down | Read-heavy |
| **W=1, R=3** | Reads fail if any node is down | Write-heavy ingestion |
| **W=1, R=1** | Stale reads are normal | Freshness doesn't matter |
| **Cassandra `LOCAL_QUORUM`** | Quorum within the local DC | Multi-region without cross-region latency |

- **Watch out:** quorums aren't linearizable: sloppy quorums, LWW on concurrent writes, clock skew and partial failures can all return old values. For "definitely latest", use a leader-based or consensus store.
- **Depth:** [[10 - Distributed Building Blocks]], [[17 - Consistency Time and Consensus]], [[24 - Cassandra and DynamoDB]]

### Replicas drift apart

| Mechanism | Fixes | Limits |
|---|---|---|
| **Hinted handoff** | Writes during short outages | Hints kept a few hours; sloppy quorum breaks R + W > N until handed off |
| **Read repair** | Rows that get read | Cold rows never repaired |
| **Anti-entropy repair** (Merkle trees) | Everything else | Scheduled, heavy; must run within `gc_grace_seconds` or deleted data comes back |

- **Depth:** [[10 - Distributed Building Blocks]], [[24 - Cassandra and DynamoDB]]

### Two replicas or regions write the same key concurrently

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Avoid it: single writer per key** | One region or leader owns each key | Cross-region write latency | Default; money and inventory |
| **Last-write-wins** | Newest timestamp wins | Silently drops a real write; skew picks the winner | Losing an update is acceptable |
| **Compare-and-set / version checks** | Reject stale writes | Retries | Needs a leader (Dynamo conditions, Cassandra LWT for rare cases) |
| **Keep siblings, app merges** | Version vectors detect concurrency; app merges on read and writes back | Merge code; deleted items resurrect unless deletes are recorded | Carts, documents (Riak, CouchDB) |
| **CRDTs** | G-counter, PN-counter, OR-set merge automatically | Only mergeable shapes; can't enforce "never below zero" | Counters, sets, presence, collaborative state |

- **Depth:** [[08 - Replication]], [[10 - Distributed Building Blocks]], [[17 - Consistency Time and Consensus]]

---

## Time and Agreement

### Ordering events across machines

| Option | Costs you | Pick when |
|---|---|---|
| **Version numbers per entity** | Need one writer per entity | Interview default |
| **Wall clock (NTP)** | Skew, backward jumps | Logs, TTLs, "roughly" |
| **Lamport clock** | Total order, can't detect concurrency | One agreed order, don't care who wins |
| **Vector clock** | Grows with writers | Must detect conflicts (Dynamo, Riak) |
| **Hybrid logical clock** | Relies on bounded skew | Time-based MVCC (CockroachDB, Yugabyte) |
| **TrueTime + commit-wait** | Special hardware, wait per commit | Spanner-style external consistency |

- **Depth:** [[17 - Consistency Time and Consensus]]

### Exactly one leader / one owner must exist

| Option | Costs you | Pick when |
|---|---|---|
| **Rent consensus**: etcd, ZooKeeper, KRaft | Operate or depend on a cluster | Leader election, small critical metadata |
| **Consensus-backed database** (CockroachDB, Spanner) | Write latency | Linearizable SQL |
| **Lease + fencing** | Renewal logic | Workers, schedulers |

- **Rule:** keep only small metadata in consensus ("who leads shard 7"), never hot-path data.
- **Depth:** [[17 - Consistency Time and Consensus]], [[10 - Distributed Building Blocks]]

### Sizing a consensus cluster

| Size | Tolerates | Pick when |
|---|---|---|
| **3** | 1 failure | Default |
| **5** | 2 failures | Survive planned + unplanned outages |
| **Even numbers** | Same as one fewer; 2–2 split has no majority | Never |

- **Also:** spread members across AZs; a lost majority stalls everything that depends on it.
- **Depth:** [[17 - Consistency Time and Consensus]]

### Detecting dead nodes (slow and dead look the same)

| Option | Costs you | Pick when |
|---|---|---|
| **Fixed heartbeat timeout** | Short = false failovers, long = slow detection | Simple, stable networks |
| **Phi accrual** | More complex | Variable latency; different actions need different confidence |
| **SWIM-style gossip** | Eventual views | Large clusters |
| **Fencing on writes** | Storage must check | Because any detector can be wrong |

- **Depth:** [[10 - Distributed Building Blocks]]

### Every node must know cluster membership

- **Option:** gossip: each node swaps state with a few random peers per round, reaching everyone in ~O(log N) rounds, with briefly inconsistent views (Cassandra, Consul).
- **Depth:** [[10 - Distributed Building Blocks]]

---

## Multi-Region and Disaster Recovery

### Writes from several regions

| Option | Costs you | Pick when |
|---|---|---|
| **Active-passive** (one write region, async replicas elsewhere) | Far users pay write latency; failover loses unreplicated writes | Default, including most payment systems |
| **Active-active with a home region per key** | Routing must know each key's home | Users mostly touch their own data |
| **Active-active, any region writes any key** | Conflicts guaranteed | Availability beats a single answer; data is mergeable |
| **Multi-region consensus** (Spanner, CockroachDB) | Majority-of-regions RTT per write | Global linearizable data |
| **DynamoDB global tables / Cassandra multi-DC + `LOCAL_QUORUM`** | LWW across regions by default | Low-latency multi-region writes on those stores |

- **Depth:** [[17 - Consistency Time and Consensus]], [[11 - Services and Observability]], [[24 - Cassandra and DynamoDB]]

### A whole region goes down

| Strategy | What runs in region B | RPO / RTO | Cost | Pick when |
|---|---|---|---|---|
| **Backup and restore** | Only backups | Hours / hours–day | Lowest | Internal, non-critical |
| **Pilot light** | Live DB replica; compute off | Minutes / tens of minutes–hours | Low | Important, can tolerate downtime |
| **Warm standby** | Small full copy, scaled up on failover | Seconds–minutes / minutes | Medium | Customer-facing, tight RTO |
| **Active-active** | Full capacity serving | Near zero / near zero | Highest (2× + conflicts) | Payments, core flows |

- **Routing:** DNS/GSLB failover (short TTLs) or anycast. **Depth:** [[08 - Reliability]]

### Defining how much loss and downtime is acceptable

- **RPO** = how much recent data you can lose; **RTO** = how long you can be down. Continuous WAL archiving cuts RPO; more frequent base backups and warm standbys cut RTO.
- **Depth:** [[08 - Reliability]], [[12 - Schema Changes and Backups]]

### A bad `DELETE` or migration replicated everywhere in milliseconds

| Option | Costs you | Pick when |
|---|---|---|
| **Point-in-time recovery** (base backup + archived WAL replayed to just before the mistake) | Storage; restore time | Default for important OLTP |
| **Delayed replica** | Extra replica | An extra safety margin |
| **Backups in another account/region** | Cost | Always |

- **Rule:** replicas are not backups.
- **Depth:** [[12 - Schema Changes and Backups]], [[08 - Reliability]]

### Choosing a backup kind

| Kind | Costs you | Pick when |
|---|---|---|
| **Logical** (`pg_dump`) | Slow restore, no PITR | Small DBs, version migrations, one table |
| **Physical base backup + WAL** | Same major version; whole cluster | The main production backup |
| **Disk snapshots** | Crash-consistent; provider-tied | Fast recovery path alongside physical |

- **Depth:** [[12 - Schema Changes and Backups]]

### Backups that silently don't work

- **Fixes:** alert on WAL-archiving failures; scheduled, timed **restore drills** into a separate environment (that time is your real RTO); game days for regional failover.
- **Depth:** [[12 - Schema Changes and Backups]], [[08 - Reliability]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[08 - Replication]] · [[17 - Consistency Time and Consensus]] · [[10 - Distributed Building Blocks]] · [[08 - Reliability]] · [[12 - Schema Changes and Backups]]
