---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Caching and the Edge

> [!abstract] What this is
> Problems about **serving repeated reads from somewhere faster than the database**: whether to cache, which pattern, keeping the cache correct, stampedes and hot keys, what happens when Redis itself fails, and how the CDN caches, invalidates and leaks.

---

## Whether and How

### Should this be cached at all?

| Cache it when | Don't cache when |
|---|---|
| Read:write above ~10:1 | Every request is unique |
| A small set of keys is hot (size the cache to the working set, ~20% of data) | Stale would be a bug (money, stock at checkout) |
| Seconds of staleness are acceptable | The value changes on every read |

- **Depth:** [[16 - Estimation and Decision Cheat Sheet]], [[06 - Caching]]

### Which caching pattern?

| Pattern | How it works | Costs you | Pick when |
|---|---|---|---|
| **Cache-aside** | Read cache; on miss read DB and fill with TTL | First read after a change is slow | Default |
| **Write-through** | Write cache and DB together | Slower writes | You own the write path and want it always warm |
| **Write-behind** | Write cache, flush to DB later | Data loss if the cache dies first | Almost never |
| **Refresh-ahead** | Refresh hot keys before expiry | Background work | A few very hot keys that must never miss |
| **In-process cache** (Caffeine) | Per-instance memory | Lost on restart; per-instance staleness | Tiny hot data: config, flags, top keys |

- **Depth:** [[06 - Caching]]

### Where can a cache live?

- Browser → CDN → API gateway → in-process → Redis → database buffer pool. Private data belongs only in places you can delete instantly (Redis), never in a shared CDN or browser cache you can't recall.
- **Depth:** [[06 - Caching]], [[04 - Load Balancing CDN and Gateway]]

---

## Keeping It Correct

### Stale or wrong value after a database write

| Option | Costs you | Pick when |
|---|---|---|
| **Delete the key after commit** (don't update it) | Next read is a miss | Default; avoids the race where two writers leave the older value cached |
| **TTL only** | Stale for up to the TTL | Profiles, product pages |
| **Delete on write + TTL safety net** | Name the stale window | The standard answer; required when stale means wrong |
| **CDC-driven invalidation** | Pipeline | Many writers you don't control |

- **Depth:** [[06 - Caching]], [[20 - Data Processing and Analytics]] (CDC fan-out to caches)

### Cache memory fills up

| Policy | Pick when |
|---|---|
| **LRU** (`allkeys-lru`) | Default for a pure cache |
| **LFU** | Some keys are always hot |
| **`volatile-*`** (evict only TTL'd keys) | Redis also holds locks or counters without TTLs |
| **`noeviction`** | Redis is the primary store (queue, leaderboard); alert well before the cap |

- **Depth:** [[06 - Caching]], [[21 - Redis]]

---

## Load Spikes on the Cache

### Stampede: a hot key expires and thousands of misses hit the database

| Option | How it works | Costs you | Pick when |
|---|---|---|---|
| **Single-flight / request coalescing** | One request refills (in-process, or a short Redis `SET NX` lock across machines); others wait and re-check | Waiters' latency | Hot keys |
| **Jittered TTLs** | Spread expiries | Nothing | Many keys filled at the same time |
| **Serve stale while revalidating** | Return the old value while one request refreshes | Brief staleness | Staleness acceptable |
| **Refresh-ahead** | Refresh before expiry | Background work | Keys that must never miss |

- **Default:** single-flight + jitter.
- **Depth:** [[06 - Caching]], [[14 - Coordination and Concurrency]]

### Hot key saturates one cache node

- **Signal:** a celebrity profile; a single key over ~1k QPS; one Redis node maxed inside a cluster.

| Option | Costs you | Pick when |
|---|---|---|
| **In-process cache in front** (short TTL, jitter) | Per-instance staleness | Read-hot keys |
| **Replicate the key** (copies on several nodes) | More writes | Read-heavy |
| **Split into several keys** (`key#0..#N`) | Readers merge | Write-hot keys |
| **Rebalance slots / change the key scheme** | Migration | Several busy keys share a node |

- **Depth:** [[06 - Caching]], [[21 - Redis]]

### Flushing the cache on deploy floods the database

- **Fix:** never `FLUSHALL` in production; version keys, warm the cache, or let keys expire naturally. **Depth:** [[06 - Caching]]

---

## When Redis Itself Fails

### Redis down: all traffic falls through to the database

| Option | Pick when |
|---|---|
| **Short timeouts** (~50 ms) on cache calls | Always |
| **Circuit breaker** to a fallback | Cache is a dependency |
| **Serve stale in-process data** | Some staleness acceptable |
| **Shed non-critical traffic** | DB can't take the full load |

- **Depth:** [[06 - Caching]], [[08 - Overload and Failure Handling]]

### Recent writes lost on Redis failover

- **Accept it** for a cache; keep the database as the source of truth (never money only in Redis). `WAIT` narrows the window but isn't consensus. Choose persistence by what a restart may lose: none (pure cache), RDB (minutes), AOF every second (~1 s), or both.
- **Depth:** [[21 - Redis]]

### One command freezes every Redis client

| Culprit | Fix |
|---|---|
| `KEYS *` | `SCAN` |
| `DEL` of a huge key | `UNLINK` (frees in the background) |
| `HGETALL`/`SMEMBERS` on a huge collection | `HSCAN`/`SSCAN`, or split the key into buckets |
| Long Lua scripts | Keep them short |
| `FLUSHALL` | `ASYNC`, and disable/rename dangerous commands in prod |
| Fork for RDB/AOF on a big write-heavy instance | RAM headroom; persistence only on a replica |

- **Depth:** [[21 - Redis]]

---

## CDN

### Far-away users pay full round trips for static content

- **Fix:** a CDN (CloudFront, Cloudflare) in front of S3, with freshness via `Cache-Control`/TTL. Always for static assets; sometimes public GETs with a TTL; usually not per-request HTML.
- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### A known spike will miss every edge at once

| Option | Pick when |
|---|---|
| **Pull CDN** (fill on first miss) | Default |
| **Push / pre-warm edges** | A known file is about to get huge traffic (launch, live event) |

- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### Users see an old file after a deploy

| Option | Costs you | Pick when |
|---|---|---|
| **Versioned filenames + long TTL + `immutable`** | Build step | Default for build assets |
| **Purge by path** | Slow to reach every edge, rate-limited or billed; browsers keep their copy | A fixed URL had to change |
| **Purge by tag** | Same purge costs | Many URLs depend on one entity |

- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### CDN hit rate collapses and floods the origin

| Cause | Fix |
|---|---|
| Tracking params (`utm_*`) in the cache key | Cache key = path only, or path + the params that matter |
| `Vary: Cookie` / `Vary: User-Agent` | `Vary: Accept-Encoding` only; mark personal responses `Cache-Control: private` |

- **Depth:** [[04 - Load Balancing CDN and Gateway]]

### One user's private data served to another

- **Fix:** cache only public content at the edge; personal responses get `Cache-Control: private` (or `no-store`) and are cached, if at all, in Redis where you can delete them.
- **Depth:** [[04 - Load Balancing CDN and Gateway]], [[06 - Caching]]

### Private files served through a CDN (paid video, course content)

- **Option:** CDN signed URLs or signed cookies, checked at the edge, so cached content stays cacheable. An S3 presigned URL is different (checked by S3, unique per request, defeats caching).
- **Depth:** not yet in a topic note; presigned URLs themselves are in [[09 - Storage Search and Geo]].

## Related Notes

- [[25 - Problem to Solution Map]]
- [[06 - Caching]] · [[21 - Redis]] · [[04 - Load Balancing CDN and Gateway]]
