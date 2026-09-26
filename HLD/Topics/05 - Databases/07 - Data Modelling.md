---
tags: [hld/topics/databases, status/draft]
created: 2026-09-23
---
# Data Modelling

> [!abstract]
> - Start **normalised**. Update once. Join on read
> - Denormalise a **measured** hot read, and name the pain (rename user → fanout or stale)
> - NoSQL: design the **primary key for the #1 query**. A second access path is a second table or index
> - This is the HLD modelling conversation. Engine internals: [[01 - Storage Engine and Disk IO]] · [[02 - Indexing]]

---

## Start normalised

- Users, orders, order_lines, products — each fact **once**
- Update a user’s name in **one** row
- Reads **join**
- This is the default on Postgres
    + FKs, transactions, ad-hoc query
    + “I’ll denormalise later when a query hurts”

- Broad slogans (“SQL for relationships, NoSQL for scale”) are a yellow flag
- Justify with **this query** and **this consistency**

---

## Denormalise a hot read

### When

- You have **measured** a join on the hot path
    + feed card needs `username` 10k times a second
    + you are not guessing in week one

### How

- Copy `username` onto `comments`
- Feed becomes one table (or one query without the user join)

### Pain you must name

- User renames
    + **fanout UPDATE** all comments (slow, correct)
    + or **accept stale** names until they edit
- Two writers can drift — this is now a cache-shaped problem
    + same family as [[06 - Caching]] invalidation

> [!warning] Don’t denormalise “because NoSQL”
> - You paid write amplification and drift
> - If the read was fine with a join + index, you bought nothing

---

## NoSQL: the key *is* the design

- Pick the **#1 query** first
- The partition / primary key must answer it **without a scan**

| Query | Key that works | What now fails |
|---|---|---|
| All posts by this user | `PK = user_id`, clustering `created_at` | All posts with hashtag `#x` |
| All messages in this chat | `PK = chat_id` | “All chats I am in” — that’s another table |
| Session by token | `PK = token` | “All sessions for user” — secondary or second table |

- Second access path
    + **another table** (or GSI) whose key **is** that path
    + not “we’ll scan the first table”
- This is why Cassandra / Dynamo interviews are **key-design** interviews

---

## Types (so you don’t say “NoSQL”)

| Type | Looks like | Use | Example |
|---|---|---|---|
| Key-value | `GET k → blob` | sessions, flags | Redis, DynamoDB KV |
| Document | JSON documents | evolving object, one-document aggregate | MongoDB |
| Wide-column | row key + columns | write-heavy, huge tables | Cassandra |
| Graph | nodes / edges | “friends of friends” **is** the product | Neo4j — rarely first pick |

- Redis as **cache**: [[06 - Caching]]
- Redis as **primary**: only data you can lose or rebuild (sessions, leaderboards)

- Document vs relational
    + if you always load the **whole aggregate** (one order + lines as one JSON) and never query “all lines of SKU X,” a document is honest
    + the moment you need the cross-aggregate query, you are back to a table or a second collection

---

## Modelling mistakes that show up as “DB is slow”

- N+1 — [[05 - Query Execution and Optimization]]
- Missing composite on `(user_id, created_at)` — [[02 - Indexing]]
- Shard key = `created_at` day — today’s shard is on fire — [[09 - Sharding]]
- UUID PK on a clustered InnoDB table — random splits — [[02 - Indexing]]

---

## Related Notes

- [[README]]
- [[05 - Databases]]
- [[10 - Engine Choice]]
- [[09 - Sharding]]
- [[01 - Splitwise]] — pair-balance denormalise in the same txn
- [[11 - Interview Questions]]
