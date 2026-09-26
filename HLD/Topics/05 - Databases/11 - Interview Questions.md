---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
---
# Interview Questions

> [!abstract]
> - Mouth version
> - Full senior answers, not one-liners
> - Depth: [[01 - Storage Engine and Disk IO]] … [[10 - Engine Choice]]

---

## Storage and durability

### A committed txn, then the box loses power. Is the data gone?

- **No**, if the **WAL was `fsync`’d**
- Commit is “log on stable storage,” not “heap page on disk”
- Dirty pages in the buffer pool are gone — **ARIES redo** puts committed work back
- Uncommitted work that **stole** a page onto disk — **ARIES undo** rolls it back
- If they hear “we lost committed data,” the log did not make it (or the disk lied)
- Depth: [[01 - Storage Engine and Disk IO]]

---

### Why WAL instead of fsync-ing every dirty page on commit?

- WAL is **sequential**
- Data pages are **random**
- One log `fsync` covers many page changes that flush later (**no-force**)
- Steal + no-force is why we need redo **and** undo

---

## Indexes

### Clustered vs secondary. Why does InnoDB hate UUID PKs?

- Clustered = the table’s **physical order is the PK**
- Secondary = key → pointer (in InnoDB, pointer is the **PK**, then a second lookup)
- UUID v4 as clustered PK = inserts **everywhere** in the B+ → split storm
- `bigserial` / snowflake / ULID append at the **right edge**
- Postgres heap is **not** clustered by default — UUID as PK is still a random unique index, less brutal than InnoDB
- Depth: [[02 - Indexing]]

---

### Why B+ and not a hash index for `created_at >`?

- Hash is equality only
- B+ leaves are **linked** — find the left edge, walk
- Range + `ORDER BY` + `LIMIT` live here

---

### Composite `(user_id, created_at)` — what does it *not* help?

- `WHERE created_at > ?` alone
- Leftmost prefix: `user_id`, or `user_id + created_at`
- Covering: if the select list is only those columns, index-only is possible

---

## Concurrency and isolation

### Name the anomalies. Which level kills which?

| Anomaly | Dead at |
|---|---|
| Dirty read | Read Committed |
| Non-repeatable read | Repeatable Read |
| Phantom | Serializable (InnoDB RR ≈ gap locks) |
| Write skew | True Serializable (or lock the invariant) |

- Postgres default = **RC**
- Don’t Serializable the whole app
- Depth: [[04 - Isolation and Anomalies]]

---

### Two doctors both go off call. Each txn looked valid.

- **Write skew** under snapshot isolation
- Invariant was on a **set**, not one row
- Fix: Serializable, or `FOR UPDATE` a single roster row
- Not a lost update (they touched different rows)
- Depth: [[04 - Isolation and Anomalies]] · [[14 - Coordination and Concurrency]]

---

### Lost update on `qty`. What do you do?

- Not `SELECT` then `UPDATE` in the app
- `UPDATE … SET qty = qty - 1 WHERE qty > 0`
- or `version` CAS
- or `FOR UPDATE` in a **tiny** txn
- Never hold that lock across payment
- Depth: [[03 - Concurrency Control]]

---

## Optimizer and access

### I added an index. The planner still seq-scans. Broken?

- **No**
- If 40% of rows match, seq scan is cheaper
- Stale stats (`ANALYZE`)
- Leftmost prefix miss
- `EXPLAIN ANALYZE` — estimate vs actual
- Depth: [[05 - Query Execution and Optimization]]

---

### Nested loop vs hash vs sort-merge — when?

- Nested loop + **index**: small outer
- Hash: equality, both sides large, build the smaller
- Sort-merge: already ordered, or huge and sort-friendly
- Nested loop + seq inner = `O(N*M)` — the failure mode
- Depth: [[05 - Query Execution and Optimization]]

---

## Connections

### 200 pods, pool size 50, Postgres dying.

- 10,000 backends
- Shrink the **app** pool
- PgBouncer **transaction** mode in front
- Postgres connections are processes — not 10k of them
- Depth: [[06 - Connections and Network IO]]

---

## Replication and shard

### After UPDATE I GET the old value.

- You read a **lagging replica**
- Read-your-writes: primary for a beat, or accept stale for *other* users
- Depth: [[08 - Replication]]

---

### When do you shard? When do you not?

- **Not:** 50k users, reads are the problem (replica + cache)
- **Yes:** writes or disk don’t fit one primary
- Key is the design. Cross-shard join/txn are gone
- Depth: [[09 - Sharding]]

---

### If I keep adding shards, will throughput always go up?

- **No**
- Same speech as Kafka partitions
- Throughput is `min(app, hottest shard, scatter-gather fan-out)`
- One hot key does not split
- A 64-way scatter-gather can be **slower**
- `hash % N` reshard moves almost everything
- Pick a key with cardinality, `N` you can operate, consistent hash if `N` will change
- Depth: [[09 - Sharding]] · [[04 - Producer Mechanics and Delivery Guarantees]]

---

## The pick

### Postgres or Cassandra?

- Don’t say “NoSQL scales”
- Postgres: txns, joins, money, ad-hoc
- Cassandra: firehose, **one** key-shaped query, accept eventual / quorum
- Money stays in Postgres
- Depth: [[10 - Engine Choice]]

---

## Extra (still asked)

| Question | Answer |
|---|---|
| N+1? | Join or `IN`. ORM default. [[05 - Query Execution and Optimization]] |
| LSM vs B+? | Append+compact vs in-place. Write-heavy vs point-read txn. [[02 - Indexing]] |
| What is a dirty page? | Buffer-pool page ≠ disk. WAL first. [[01 - Storage Engine and Disk IO]] |
| Intention locks? | Table-level “I will lock a row” so `LOCK TABLE` doesn’t scan rows. [[03 - Concurrency Control]] |
| Directory table for shards? | Flexible, extra hop/SPOF. Not first answer. [[09 - Sharding]] |

---

## Related Notes

- [[README]]
- [[05 - Databases]]
- [[01 - Storage Engine and Disk IO]]
- [[02 - Indexing]]
- [[03 - Concurrency Control]]
- [[04 - Isolation and Anomalies]]
- [[05 - Query Execution and Optimization]]
- [[06 - Connections and Network IO]]
- [[07 - Data Modelling]]
- [[08 - Replication]]
- [[09 - Sharding]]
- [[10 - Engine Choice]]
