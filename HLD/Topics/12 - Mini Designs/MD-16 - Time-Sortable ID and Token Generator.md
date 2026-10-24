---
tags: [hld/mini-designs, status/draft]
created: 2026-10-24
---
# MD-16 — Time-Sortable ID and Monotonic Token Generator

> [!abstract] What this is
> How to hand out unique identifiers across many machines without a single database sequence: 64-bit Snowflake-style IDs, 128-bit UUIDv7/ULID, pre-allocated counter ranges for short codes (Base62), and the clock problems that come with all of them.
>
> The defining decision is **what the ID must be**: only unique, or also time-sortable, short, unguessable, or strictly ordered within a scope. Each property costs something, and the cheapest generator that meets the real requirement wins.

---

## 1. What It Solves and Where It Appears

- A single auto-increment column stops working once writes are spread over many shards or regions: two shards hand out the same number, or one sequence becomes the bottleneck.
- Interviewers use this block both as a standalone question (#25 Unique ID Generator) and as a sub-question inside others.

| Problem | ID need | Typical choice |
|---|---|---|
| URL Shortener | Short (6–8 chars), unique, ideally not guessable | Counter ranges per node → Base62, permuted |
| Chat | Unique, sortable by time, ordered within a conversation | Snowflake for message IDs; per-conversation sequence for strict order |
| Orders, payments | Unique, sortable for B-tree locality, safe to expose | Snowflake or UUIDv7, often with a type prefix |
| Seat Booking | Booking IDs and idempotency keys | UUIDv7 / Snowflake; idempotency keys come from the client |
| Distributed logs, events | Sortable, high rate | Snowflake / ULID |

---

## 2. Knobs

| Knob | Options | What it trades |
|---|---|---|
| Width | 64-bit int, 128-bit, short string | Index size and URL length vs collision space and coordination |
| Time precision | ms, s | Ordering resolution vs bits left for nodes and sequence |
| Node ID source | static config, lease from etcd/ZooKeeper, derived from pod ordinal | Operational risk of duplicate node IDs vs simplicity |
| Ordering scope | none, global-roughly (k-sorted), strict per entity | Cost rises sharply for strict ordering |
| Guessability | sequential, permuted, random | Enumeration risk vs locality and density |
| Custom epoch | e.g. 2024-01-01 | Extends the life of the timestamp bits |

---

## 3. Evolution of the Block

### Step 0 — Database sequence

- One Postgres `BIGSERIAL` or sequence. Fine on one primary, and fine up to tens of thousands of IDs per second with sequence caching.
- **Breaks when:** you shard (each shard needs its own range) or the primary is no longer the only writer.

### Step 1 — Per-shard ranges

- Each shard uses `id = local_seq * N + shard_no`, or an offset range. Unique, but not time-sortable across shards, and adding shards is awkward.

### Step 2 — Snowflake-style 64-bit IDs

- **Trigger:** many writers, need for roughly time-ordered IDs (index locality, "newest first" queries).
- **Change:** each generator composes `timestamp | node_id | sequence` locally, with no network call per ID.

### Step 3 — Range allocation for short codes

- **Trigger:** codes must be short (a URL shortener), so 64-bit numbers rendered in Base62 (11 chars) are too long.
- **Change:** a coordinator (a row in Postgres, or etcd) hands each app node a block of, say, 100k counter values. The node issues codes from memory and fetches a new block when it runs out. Encode in Base62, optionally through a bijective permutation so neighbours don't look like neighbours.

### Step 4 — Hybrid logical clocks and strict ordering

- **Trigger:** you need causal order across machines (events written in different regions) or strict order inside one entity (chat messages in one conversation).
- **Change:** HLC timestamps for cross-node causality, or a per-entity sequence assigned by the entity's single owner (the shard leader or a per-conversation sequencer).

---

## 4. Core Mechanics

### Snowflake layout

```
 1 bit  | 41 bits timestamp (ms since custom epoch) | 10 bits node | 12 bits sequence
 sign=0 | ~69.7 years of milliseconds               | 1,024 nodes  | 4,096 IDs per ms per node
```

```
next_id():
    now = current_ms()
    if now < last_ms:                      # clock went backwards
        if last_ms - now <= 5: sleep until last_ms     # small skew: wait it out
        else: raise ClockMovedBackwards              # big skew: refuse, alert
    if now == last_ms:
        seq = (seq + 1) & 0xFFF
        if seq == 0: now = wait_next_ms(last_ms)     # 4,096 used this ms
    else:
        seq = 0
    last_ms = now
    return (now - EPOCH) << 22 | node_id << 12 | seq
```

- Per node: 4,096 IDs/ms ≈ 4M IDs/s, far beyond any single app process.
- **Node IDs must be unique.** Static config breaks when pods are rescheduled. Better: lease a node ID from etcd or ZooKeeper with a TTL, renew it, and stop issuing IDs if the lease is lost.
- **Clock safety:** run NTP in slew mode (it speeds up or slows down the clock instead of jumping), persist `last_ms` across restarts or wait out the maximum skew on boot.

### Instagram variant (inside Postgres)

- 41 bits time, **13 bits logical shard ID**, 10 bits per-shard sequence, generated by a PL/pgSQL function in each shard. The shard is baked into the ID, so the ID alone tells you where the row lives.

### UUIDv7 and ULID

- **UUIDv7** (RFC 9562, 2024): 48-bit Unix ms timestamp + random bits in a standard 128-bit UUID. Sortable by creation time, no coordination, no node IDs.
- **ULID:** the same idea, 26-char Crockford Base32 string.
- Both are bigger than 64 bits (index and FK cost) but remove node-ID management completely.

### Base62 short codes

```
alphabet = 0-9 A-Z a-z                 # 62 symbols
length 6 → 62^6 ≈ 5.7 × 10^10
length 7 → 62^7 ≈ 3.5 × 10^12
length 8 → 62^8 ≈ 2.2 × 10^14
```

- **Counter + range allocation:** the coordinator does `UPDATE ranges SET next = next + 100000 RETURNING next` in one transaction; the node owns `[next-100000, next)`. If a node dies, its unused range is lost, which is fine because the space is enormous.
- **Unguessable codes:** sequential counters make codes enumerable (`abc123` → `abc124`). Fix by applying a bijection on the counter before encoding (multiply by a large constant coprime to 62^7, mod 62^7; or a small Feistel network). Unique stays guaranteed because a bijection never maps two inputs to one output.
- **Random codes:** generate random 7–8 chars and insert with a unique constraint; retry on conflict. Collisions stay rare while the space is mostly empty, but the retry rate grows as it fills.

---

## 5. Decision Tables

| Generator | Size | Sorted | Coordination | Pick when |
|---|---|---|---|---|
| DB sequence | 64-bit | Yes (one DB) | The DB | One primary; simplest |
| Snowflake | 64-bit | Roughly by time | Node-ID lease only | Many writers, want compact sortable keys |
| UUIDv7 | 128-bit | Roughly by time | None | You don't want to manage node IDs; size is fine |
| UUIDv4 | 128-bit | No | None | Opaque tokens; never as a clustered PK (random inserts fragment B-trees) |
| Range-allocated counter + Base62 | 6–8 chars | Within a node | Range fetch every N IDs | Short public codes |
| Random Base62 + unique constraint | 7–8 chars | No | DB unique check | Short codes, unguessable, moderate write rate |
| Per-entity sequence | small int | Strict in scope | Entity owner | Message order in a conversation, ledger entry numbers per account |

### Short code: counter vs hash vs random

| Approach | Collision | Same long URL → same code | Guessable | Pick when |
|---|---|---|---|---|
| Counter (permuted) → Base62 | Never | No | No, after permutation | Default |
| Hash(long URL) prefix | Possible, needs retry | Yes | No | Dedup of identical URLs is a requirement |
| Random | Rare, needs retry | No | No | Simplicity, low write rate |

---

## 6. Numbers to Know

| Thing | Number |
|---|---|
| Snowflake timestamp life (41 bits of ms) | ~69 years from the custom epoch |
| Snowflake per-node rate | 4,096 per ms ≈ 4M/s |
| 62^7 | ≈ 3.5 trillion codes |
| 62^7 used at 100M new codes/day | ~95 years |
| UUID storage | 16 bytes vs 8 for a bigint; every index and FK pays it |
| NTP typical skew on cloud VMs | sub-millisecond to a few ms when healthy |

---

## 7. Failure Modes

| Failure | What happens | Detect | Fix |
|---|---|---|---|
| Duplicate node ID (two pods with the same config) | Duplicate IDs, unique-constraint violations | Insert conflicts on PK | Lease node IDs with TTL; stop issuing on lease loss |
| Clock jumps backwards | Duplicate or out-of-order IDs | Generator logs `now < last_ms` | Wait small skews out, refuse big ones, NTP slew mode |
| Sequence exhausted in one ms | Generator must wait 1 ms | Wait counter | Fine; or more node bits if it's common |
| Range coordinator down | Nodes can't get new blocks | Range fetch errors | Large blocks (hours of supply), prefetch the next block at 50% use |
| Enumerable codes | Scrapers walk every short link | Sequential 404/200 patterns | Permute counters; rate limit by IP ([[MD-02 - Traffic Shaper Rate Limiter and Waiting Room]]) |
| Timestamp bits leak information | Competitors infer order volume from IDs | — | Don't expose raw Snowflakes where volume is sensitive; expose an opaque public ID |

---

## 8. Twist Cards

- **"Messages in a chat must appear in exactly the order they were sent."**
    + Snowflake gives only rough global order (clocks differ between nodes). Assign a per-conversation sequence number at the single owner of that conversation (its shard leader), and sort by it.
- **"IDs must be unguessable but short."**
    + Counter ranges through a bijective permutation, or random codes with a unique constraint. Both keep 7 characters.
- **"We're going multi-region."**
    + Put a region ID in the node bits (or partition the node-ID space by region). UUIDv7 needs nothing.
- **"Can't we just use UUIDv4 everywhere?"**
    + For opaque tokens, yes. As a primary key, random inserts land all over the B-tree and hurt write and cache performance; UUIDv7 fixes that while staying coordination-free.
- **"What if the ID service is down?"**
    + Don't run a network service per ID. Generate locally (Snowflake/UUIDv7) or from a pre-fetched range, so the only dependency is occasional.

---

## 9. Operations and Cost

- **Watch:** generator clock-skew events, node-ID lease renewals, range consumption rate vs remaining space, insert conflicts on ID columns.
- **Cost:** essentially free when generated in-process. The real cost is the ID's size in every index and foreign key, which is why 64-bit beats 128-bit for hot tables.

---

## 10. Say It in One Minute

> "I don't want a network call per ID. For internal keys I use Snowflake: 41 bits of milliseconds, 10 bits of node ID leased from etcd, 12 bits of sequence, so it's 64-bit, roughly time-sorted and generated in-process. If the clock goes backwards I wait out small skews and refuse big ones. For public short codes I hand each node a block of counter values, permute them so they aren't guessable, and Base62-encode to 7 characters, which is 3.5 trillion codes. Strict ordering inside a conversation comes from a per-conversation sequence at its owner, not from timestamps."

---

## Sources

- Twitter Snowflake layout and its clock-backwards handling, as described in *Snowflake IDs: how Twitter and Discord pack a sortable ID into 64 bits* (iotools, 2026) and *Generating Unique IDs in a Distributed System* (CalliCoder, 2026). https://www.callicoder.com/distributed-unique-id-sequence-number-generator/
- *Distributed ID Generators — Avoid the Clock Rollback Trap* (bugfree.ai, 2026). https://blog.bugfree.ai/distributed-id-generators-clock-rollback-trap
- Instagram Engineering, *Sharding & IDs at Instagram* (2012): 41-bit time, 13-bit logical shard, 10-bit sequence in PL/pgSQL.
- RFC 9562, *Universally Unique IDentifiers (UUIDs)* (May 2024): UUIDv7 definition.
- Existing vault coverage of Snowflake and clocks: [[10 - Distributed Building Blocks]], [[17 - Consistency Time and Consensus]].

## Related Notes

- [[12 - Mini Designs]]
- [[10 - Distributed Building Blocks]]
- [[17 - Consistency Time and Consensus]]
- [[MD-20 - Partitioning Shard Keys and Resharding]]
- [[MD-02 - Traffic Shaper Rate Limiter and Waiting Room]]
