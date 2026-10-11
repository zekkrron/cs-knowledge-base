---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
updated: 2026-09-29
---
# Data Modelling

> [!abstract] What this is
> Data modelling decides what tables (or collections) exist and what their keys are. Start **normalised**: update once, join on read. Denormalise only a **measured** hot read, and name the pain it brings. In NoSQL, design the **primary key for the #1 query**; a second access path means a second table or index.
>
> This is the HLD modelling conversation. Engine internals are in [[01 - Storage Engine and Disk IO]] and [[02 - Indexing]].

---

## Why It Exists

The data model fixes which queries are cheap and which are expensive for the life of the system. Indexes and caches can patch a bad model for a while, but the shape of the tables and keys decides whether a query is one lookup or a scan across everything.

Broad slogans like "SQL for relationships, NoSQL for scale" are a yellow flag in an interview. Justify the model with **this query** and **this consistency** requirement.

---

## Start Normalised

In a normalised model each fact is stored **once**. Users, orders, order_lines and products each get their own table. Updating a user's name touches **one** row, and reads **join** the tables back together.

This is the default on Postgres, because it gives you foreign keys, transactions and ad-hoc queries for free. The honest position is "I'll denormalise later when a query hurts."

---

## Denormalise a Hot Read

Denormalising means copying data so a read doesn't need a join. It makes one read faster and every related write more complicated.

### When

Only when you have **measured** a join on the hot path, for example a feed card that needs `username` 10k times a second. You aren't guessing in week one.

### How

Copy `username` onto `comments`. The feed then reads one table, or one query without the user join.

### The pain you must name

Now the name lives in two places. When a user renames, you have two options:

| Option | Behaviour | Pick when |
|---|---|---|
| **Fanout UPDATE** | Rewrite the name on all of their comments | Correctness matters; slow but correct |
| **Accept stale** | Old comments keep the old name until edited | The copy is cosmetic |

Two writers can also drift apart, which makes this a cache-shaped problem. It's the same family as invalidation in [[06 - Caching]].

> [!warning] Don't denormalise "because NoSQL"
> You paid write amplification and drift. If the read was fine with a join and an index, you bought nothing.

---

## NoSQL: The Key Is the Design

**1. Distributed NoSQL and the primary key.**

- In distributed NoSQL databases like Cassandra or DynamoDB there's **no query optimizer** to figure out or rescue a poorly planned query.
- The primary key is strictly enforced because its **partition key** determines exactly which server in the cluster holds the data.
- Design the partition and primary key specifically to answer your **#1 most frequent query** directly, without a scan.
- Querying by a non-key attribute means scanning data across every server. Cassandra rejects it outright unless you add `ALLOW FILTERING`, and DynamoDB only allows it as a `Scan`, which reads (and bills for) the whole table. Either way it's a serious performance and cost failure.

**2. MongoDB as the middle ground.**

- MongoDB sits between rigid NoSQL systems and flexible relational databases: it supports traditional **B-tree secondary indexes** and has a **query optimizer**.
- A query without a usable index doesn't fail; MongoDB does a full **collection scan**, which returns correct data but becomes a major bottleneck as the dataset grows.
- When MongoDB is sharded across servers, it routes by a **shard key**. A query without the shard key forces **scatter-gather**: the request is broadcast to every shard and the router waits for all of them to reply, which hurts performance at distributed scale. See [[23 - MongoDB]].

**3. Global secondary indexes (GSIs).**

- A **GSI** is a secondary table that the database maintains automatically.
- When you create one, the database copies the relevant attributes from the base table and reorganizes them entirely around a **new key** (with its own partitioning).
- That lets a new type of query route directly to the right partition, giving fast lookups without scanning the original table. GSIs in DynamoDB are updated **asynchronously**, so reads from them are eventually consistent. See [[24 - Cassandra and DynamoDB]].

**4. Second access paths and data duplication.**

- A **second access path** is when you need to query the same data by a completely different attribute than the original primary key.
- Because NoSQL databases can't efficiently scan or filter the original table to find unindexed data, the answer is never "we'll scan the first table."
- To support a second access path, you **duplicate the data** into a new table or a GSI whose key is designed for that path.

In Cassandra or DynamoDB there's no optimizer to rescue a bad query. Pick the **#1 query** first, then choose a partition and primary key that answers it **without a scan**.

| Query | Key that works | What now fails |
|---|---|---|
| All posts by this user | `PK = user_id`, clustering `created_at` | All posts with hashtag `#x` |
| All messages in this chat | `PK = chat_id` | "All chats I am in" (that's another table) |
| Session by token | `PK = token` | "All sessions for user" (secondary index or second table) |

A second access path is **another table** (or a GSI) whose key **is** that path. It is not "we'll scan the first table." This is why Cassandra and Dynamo interviews are really **key-design** interviews.

---

## Store Types

Saying "NoSQL" names nothing. Name the type and what it's for.

| Type | Looks like | Pick when | Example |
|---|---|---|---|
| Key-value | `GET k → blob` | Sessions, flags | Redis, DynamoDB KV |
| Document | JSON documents | Evolving object; one-document aggregate | MongoDB. Mechanics: [[23 - MongoDB]] |
| Wide-column | Row key + columns | Write-heavy, huge tables | Cassandra. Mechanics: [[24 - Cassandra and DynamoDB]] |
| Graph | Nodes and edges | "Friends of friends" **is** the product | Neo4j (rarely the first pick) |

Redis as a **cache** is covered in [[06 - Caching]]. Redis as a **primary** store is only for data you can lose or rebuild, such as sessions and leaderboards.

### Document vs relational

If you always load the **whole aggregate** (one order plus its lines as one JSON document) and never ask "all lines of SKU X," a document store is an honest fit. The moment you need that cross-aggregate query, you're back to a table or a second collection.

---

## Failure Modes

Many "the DB is slow" complaints are really modelling mistakes.

| Failure | What happens | Mitigation |
|---|---|---|
| N+1 | One query per row from the app | Join or `IN`. See [[05 - Query Execution and Optimization]] |
| Missing composite on `(user_id, created_at)` | "Latest posts for user" scans or sorts | Add the composite. See [[02 - Indexing]] |
| Shard key = `created_at` day | Today's shard is on fire | Key with cardinality. See [[09 - Sharding]] |
| UUID PK on a clustered InnoDB table | Random page splits on every insert | Sequential ids. See [[02 - Indexing]] |
| Denormalised copy drifts | Stale or conflicting values | Fanout update in the same transaction, or accept staleness explicitly |

---

## Interview Lines

> [!tip] Say it like this
> - "I start normalised and denormalise one measured hot read, then say how I keep the copy in sync."
> - "In Cassandra the partition key is the design. I pick it from the #1 query."
> - "A second access path is a second table keyed by that path, not a scan."
> - "A document store fits if I always load the whole aggregate. Cross-aggregate queries push me back to tables."

## Related Notes

- [[README]]
- [[05 - Databases]]
- [[10 - Engine Choice]]
- [[09 - Sharding]]
- [[01 - Splitwise]] — pair-balance denormalise in the same txn
- [[11 - Interview Questions]]
