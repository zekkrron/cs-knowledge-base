---
tags: [hld/problems, status/draft]
created: 2026-09-27
---
# Speed Run — all twelve in one page

> [!abstract]
> - For the morning of. Read **aloud**, 20 minutes
> - Twelve problems, but only **six mechanisms**. Learn the mechanisms and no question is blank
> - Each block: what it is · **the invariant** · stores · the number · what they'll ask · close
> - Numbers and thresholds: [[16 - Estimation and Decision Cheat Sheet]] · script: [[01 - How the Round Works]]

---

## The six mechanisms (this is the actual syllabus)

| # | Mechanism | Shape | Used by |
|---|---|---|---|
| 1 | **Idempotency key** | Unique constraint on a client-supplied key; replay returns the first answer | every write in all 12 |
| 2 | **Hold + TTL state machine** | `free → held → sold`, expiry sweeper, **never** lock across a payment | 6, 9, 12 |
| 3 | **Optimistic version** | `UPDATE … WHERE id=? AND version=?` → 0 rows = 409 | 5, 10, 11 |
| 4 | **Ledger / append-only + denormalised total** | Truth is the rows; the total is a cache in the same txn | 1, 7 |
| 5 | **Outbox** | Commit to DB, then a relay publishes. Never "update PG then fire HTTP" | 5, 10, 11, 12 |
| 6 | **`SKIP LOCKED` claim loop** | Due rows → claim → commit → work **outside** the lock | 2, plus every sweeper |

> [!tip] If you blank on a question, say: *"This looks like a hold-with-TTL problem"* or *"this is idempotency plus a state machine"* and start drawing. Naming the mechanism buys you the whole first minute.

**Two more that are everywhere:** presigned direct-to-S3 for bytes (4, 8, 11), and read-model split — Postgres for lists, Elasticsearch for text (5, 11).

---

## 1 — [[01 - Splitwise]]

- **What:** groups, expenses, splits, who owes whom, settle
- **Invariant:** `SUM(splits.share) = expense.amount`, and the **pair balance** moves in the **same txn** as the expense
- **Stores:** Postgres only. Redis cache-aside on `GET balances`, delete on write
- **Number:** 1M DAU × 2 expenses = 2M/day ≈ **20 QPS write, ~600 peak read**. ~1.5 TB over 2 yrs → **one primary + replicas, no shard**
- **Trick:** one row per pair with `user_lo < user_hi` and a **signed** amount. One row to update, not two
- **They ask**
    + *Why store balances, not sum on read?* → 50 members × 10k expenses per GET. Denormalise the net, reconcile nightly
    + *Simplify debts?* → greedy max-debtor pays max-creditor, **cosmetic**; history stays SoR
    + *Double tap?* → `Idempotency-Key` unique
- **Close:** "Expenses and splits are the truth, the pair balance is denormalised in the same transaction, and money never lives in Redis."

## 2 — [[02 - Task Scheduler]] · deep dive

- **What:** cron and one-shot delayed jobs, fired once, retried with backoff
- **Invariant:** one due row is dispatched by exactly **one** poller; the target may still see two calls (at-least-once)
- **Stores:** Postgres `jobs` + `job_fires`. Kafka only if the target is our own worker
- **Number:** 1M jobs, 10% due in a minute ≈ **1.6k/s if clumped**. Row 200 B → trivial disk. PG wins
- **Trick:** `next_run_at` indexed partial `WHERE status='active'`; `FOR UPDATE SKIP LOCKED`; dispatch **outside** the lock
- **They ask**
    + *Two pollers?* → second skips the row
    + *Exactly once?* → no. `fire_id` as the idempotency key on the target
    + *Poller died after claim?* → lease/stale sweeper puts it back. May double-call
    + *Midnight herd?* → `ORDER BY next_run_at`, bounded batch, jitter on create
- **Close:** "Jobs are rows with `next_run_at`. Pollers claim due rows with `SKIP LOCKED`, dispatch outside the transaction, then write the fire and the next time."

## 3 — [[03 - Wearable Device]] · deep dive

- **What:** watch → phone → API. Samples, sessions, latest value, charts, alerts
- **Invariant:** a replayed batch never double-inserts — **monotonic `seq` per device**
- **Stores:** Postgres (devices, sessions, `last_seq`), time-series store for samples, Redis for `now`, S3 for dumps
- **Number:** 100k devices × 1 Hz × 8h = **2.9e9 samples/day ≈ 90 GB/day**. Phone batches 60 s → **~1.7k req/s**. The 90 GB is what forbids an OLTP `samples` table
- **Trick:** phone is the buffer (tunnels, offline). Charts read **1-minute rollups**, not raw
- **They ask**
    + *Phone retries the same batch?* → `seq <= last_seq` → 200, no insert
    + *Gap in seq?* → accept + mark hole, or 409. Say which and why
    + *Device clock is wrong?* → store **device_ts and server_received_at**
    + *Alerts?* → worker off the ingest path, with hysteresis + cooldown
- **Close:** "Idempotent batches keyed on a device sequence. Metadata in Postgres, samples in a time-series path, rollups for charts, and I never claim Postgres holds 1 Hz forever."

## 4 — [[04 - Multipart Upload]] · deep dive

- **What:** big file upload with resume
- **Invariant:** **bytes never touch the app server**, and `ready` only when every part has an ETag
- **Stores:** Postgres `uploads` + `upload_parts`, S3 holds bytes and does the assembly
- **Number:** 100k uploads/day × 25 parts → initiate **~1 QPS**, acks **~25 QPS**. Trivial. The interesting number is **bandwidth you avoided**
- **Trick:** initiate returns **presigned PUT URLs**; client acks each part's ETag; CompleteMPU needs those ETags
- **They ask**
    + *Why ack each part?* → resume, and CompleteMPU requires the ETag list
    + *Presigned URL expired overnight?* → GET the upload, mint fresh URLs for pending parts, same MPU
    + *Complete called twice?* → CAS to `ready`, return the same file id
    + *Who concatenates?* → S3. Not a worker running `cat`
    + *Zombie uploads?* → TTL cron aborts the MPU; S3 **bills** incomplete parts
- **Close:** "Initiate writes a session and hands back presigned part URLs. The client talks to S3 and acks ETags. Complete asks S3 to assemble. The video never goes through my API."

## 5 — [[05 - Jira]]

- **What:** projects, issues, legal transitions, comments, list + search
- **Invariant:** status only moves along a **declared edge**, and never loses a concurrent update
- **Stores:** Postgres (issues) + Elasticsearch (search) + Kafka (notify, index)
- **Number:** 10M issues × 2 KB ≈ **20 GB**, list **2k QPS**, writes 200. No shard
- **Trick:** `UPDATE … WHERE id=? AND version=? AND status=?` → 0 rows = **409**. Edges in a tiny `workflow_edges` table
- **They ask**
    + *List vs search?* → relational filters on a composite index vs inverted index. **Never** `LIKE '%bug%'` at 10M rows
    + *ES stale?* → say "a couple of seconds," and list still works if ES is down
    + *Custom fields?* → `JSONB` + GIN, not 200 columns
    + *Switch to LLD?* → `Issue`, `Workflow.canTransition`, `Comment`. The workflow **is** the LLD
- **Close:** "Issues are Postgres rows with a versioned status and a tiny edge table. Lists hit indexes, search is an async index, notifications go through a queue."

## 6 — [[06 - Seat Booking]]

- **What:** one seat, two buyers
- **Invariant:** **never double-sell.** One atomic `free → held`, and `held → sold` only if we still own the hold
- **Stores:** Postgres `seats` (SoR), Redis for the browse map only
- **Number:** 100k DAU ≈ 2–6 QPS, but a premiere is **~500 QPS on those seat rows**. Still PG, because we lock **seats, not the show**
- **Trick:** one `UPDATE … WHERE status='free'` for all requested ids, then **compare rowcount** — that is all-or-nothing without a long lock
- **They ask**
    + *Why not `SELECT FOR UPDATE` for 10 minutes?* → you don't hold a DB lock across a human paying. Hold is **state + clock**
    + *Redis lock for the hold?* → process death and TTL vs a late PSP = double-sell. Don't
    + *Payment lands after expiry?* → webhook updates 0 rows → refund, no sell
    + *Deadlock on multi-seat?* → lock ids in sorted order
- **Close:** "Seats are rows. Hold is an atomic status flip plus a clock. The webhook only sells if we still hold it. The browse cache may lie; the UPDATE cannot."

## 7 — [[07 - Wallet and Ledger]]

- **What:** balance, topup via PSP, P2P transfer, history
- **Invariant:** **append-only ledger is SoR**; `balance` moves in the same txn; `SUM(ledger) = balance`
- **Stores:** Postgres only. Paise as `BIGINT`, never floats
- **Number:** 2M DAU × 3 ops = 6M/day ≈ **70 QPS avg, 200 peak**; ~200 GB/yr. One PG
- **Trick:** credit on the **PSP webhook**, never on the browser redirect. `psp_events(event_id UNIQUE)` kills duplicates
- **They ask**
    + *Webhook twice?* → short-circuit if already `posted`
    + *PSP timed out, state unknown?* → stay `pending`, **reconcile job** pulls the PSP. Never guess
    + *Refund?* → new opposite ledger row. No `DELETE`, no `UPDATE` of history
    + *Transfer deadlock?* → lock both wallets in id order
    + *Redis INCR for balance?* → Redis is not the bank
- **Close:** "Append-only ledger with a denormalised balance in the same transaction. Topups land on the webhook. Retries don't print money."

## 8 — [[08 - Resource Sharing ACL]]

- **What:** Drive-style share of a file or folder tree
- **Invariant:** every read is authorised, and **denied looks like 404** so nothing leaks
- **Stores:** Postgres `nodes` + `grants`, S3 for bytes, optional Redis 30 s ACL cache
- **Number:** 10M nodes ≈ 10 GB, list **2k QPS**, grants 50/s. One PG
- **Trick:** owner on the row; everyone else in `grants`. Read walks **node → parent → root**, **closest grant wins**
- **They ask**
    + *Walk vs materialise?* → walk is 1 insert to share and ~depth reads. Materialising is 1 read but rewrites the subtree on every move/revoke. Materialise only when the share root is huge
    + *"Shared with me"?* → query `grants WHERE grantee_id=?`. Don't scan the tree
    + *403 or 404?* → **404**
    + *Move a folder into itself?* → reject, cycle check
- **Close:** "A tree of nodes, owner on the row, everyone else in grants. Reads walk up until a grant hits. I don't copy the ACL onto a million children until forced."

## 9 — [[09 - Inventory and Flash Sale]]

- **What:** stock per `(store, sku)`; 10k people, 200 units
- **Invariant:** **available = `on_hand - reserved` never goes negative** — enforced in the predicate, not an `if`
- **Stores:** Postgres `inventory`, Redis for the product page, Redis counter **only** for a named flash
- **Number:** 1M DAU ≈ 150 QPS browse; flash = **20k QPS on one page + 5k reserve/s on one row**. A single PG row does ~1–3k TPS then hurts — **that** number drives the flash answer
- **Trick:** `UPDATE … SET reserved = reserved + n WHERE on_hand - reserved >= n` → 0 rows = sold out
- **They ask**
    + *This vs seat booking?* → **counter** vs **unique row**. Same isolation family, different schema
    + *Hot row melts?* → pick one and defend it: queue + serialise per SKU / Redis `DECR` + Lua with PG reconcile / **shard the counter** into buckets
    + *Redis says 5, PG says 0?* → PG confirms. Redis is a **gate**, repair from PG, fail closed
    + *Page says "3 left"?* → cached and wrong. Reserve is truth
    + *Kafka in front of reserve?* → only if the product is literally "join the queue"
- **Close:** "Inventory is a row with `on_hand` and `reserved`. Reserve is one conditional update plus a TTL hold. Flash is the same update behind a rate limiter, and if one row melts I queue or shard the counter."

## 10 — [[10 - Order Management]]

- **What:** life of the order **after** "yes" — paid, picking, shipped, cancelled
- **Invariant:** only **legal** transitions, and line prices are **frozen snapshots**
- **Stores:** Postgres `orders` / `order_lines` / `order_events`, Kafka via outbox to notify + WMS
- **Number:** 200k orders/day ≈ **2–5 QPS create**, ~150 GB over 2 yrs. One PG. "This is not a scale problem, it is a correctness problem"
- **Trick:** `order_events` written in the **same txn** as the status change = audit **and** outbox
- **They ask**
    + *Checkout vs OMS vs Inventory?* → cart+freeze vs **status/lines/who can cancel** vs `on_hand`/`reserved`
    + *Why snapshot prices?* → the catalog changes tomorrow; the order does not
    + *Paid but stock commit failed?* → prefer one DB and one txn; otherwise `paid` + `needs_manual`. Never ship air, never hide it
    + *WMS marks shipped twice?* → transition idempotent, `awb` unique
    + *Kafka down?* → outbox rows sit; the order is still `paid`
- **Close:** "An order is a versioned state machine plus frozen line items. Checkout mints it; payment and the warehouse only move legal edges. My-orders is a Postgres index, not search."

## 11 — [[11 - Collaborative Workspace]]

- **What:** Notion-lite — page tree, body, comments, versions, presence
- **Invariant:** no silent overwrite — save carries `base_version`, mismatch is a **409**
- **Stores:** Postgres `pages` + `page_versions`, S3 for old snapshots, **Redis for presence**, ES for titles
- **Number:** 200k DAU → saves **~40 QPS**; presence heartbeats **~2k/s** → that number is exactly why presence is Redis and not Postgres
- **Trick:** save is an **HTTP PUT**, not a keystroke stream. WebSocket carries presence only
- **They ask**
    + *Two people typing?* → SDE-1 answer is optimistic version + 409, then block-level versions. **Name** OT/CRDT and park it — "if that's the product, it's a sync service, I won't derive Jupiter OT on the board"
    + *WS connection dies?* → presence TTL expires, document is safe at the last PUT
    + *Redis flushed?* → everyone looks offline for one heartbeat. Docs unaffected
    + *Sharing?* → same ancestor walk as [[08 - Resource Sharing ACL]]
- **Close:** "Pages are versioned rows saved with an optimistic PUT. Presence is Redis over WebSocket. Sharing reuses the grants tree."

## 12 — [[12 - E-commerce Checkout]]

- **What:** cart → freeze price → reserve stock → PSP → **one** order
- **Invariant:** what you agreed to pay is **immutable** once the session exists; one session produces **one** order
- **Stores:** Postgres `carts` / `checkout_sessions` / `checkout_lines`, Redis optional for cart
- **Number:** 500k DAU → cart 10–30 QPS, checkout **1–3 QPS**. Tiny. Again: correctness, not scale
- **Trick:** three systems of record — `cart_items` (intent), `checkout_lines` (agreement), `orders` (purchase)
- **They ask**
    + *Price changed between cart and pay?* → session create re-reads and either 409s or snapshots. Pick one out loud
    + *Webhook fires twice?* → `Idempotency-Key = session_id`, `orders.checkout_id UNIQUE` → one order
    + *Paid but the reservation expired?* → refund, no order. Don't oversell to be nice
    + *Guest cart?* → cookie cart id, merge on login. Mention, don't build
- **Close:** "Cart is a scratchpad. Checkout freezes prices and reserves stock. The webhook commits stock and mints exactly one order keyed by the session. After that I stop talking checkout and talk OMS."

---

## Last-30-seconds reminders

- Requirements and **out of scope** first. Never open with tech
- Do the arithmetic aloud, then say **"so I pick X, and I'd revisit at Y"**
- Read path and write path **separately**
- One deep dive, not six shallow ones. Offer them the choice
- Correctness lives in a **DB constraint**, not an `if`
- Never hold a DB lock across payment, HTTP, or a human
- Say the stale window before they find it
- At-least-once + idempotent consumer. Never "exactly once"
- Not knowing is fine; going silent is not

---

## Related Notes

- [[HLD/Problems/README]]
- [[16 - Estimation and Decision Cheat Sheet]]
- [[01 - How the Round Works]]
- [[14 - Coordination and Concurrency]]
- [[13 - Question Bank]]
