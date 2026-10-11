---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Search, Geo and Analytics

> [!abstract] What this is
> Problems about **specialised queries the OLTP database is bad at**: full-text and fuzzy search (and running Elasticsearch), semantic/vector search, "near me" geo queries, analytics and reporting, batch and stream processing, and approximate counting at scale.

---

## Text Search

### Full-text search over a catalogue

- **Signal:** `LIKE '%foo%'` scans and can't rank; relevance, facets, typos.

| Option | Costs you | Pick when |
|---|---|---|
| **Postgres `tsvector` + GIN / trigram** | Basic ranking; facets need heavy `GROUP BY`; hard to distribute | Under ~1M rows, low query volume |
| **Elasticsearch / OpenSearch** (inverted index, analyzers, BM25) | A separate cluster kept in sync; eventual | Large corpus, relevance tuning, facets, high QPS |

- **Default:** ES as a derived index, a couple of seconds stale, rebuildable from the database.
- **Depth:** [[09 - Storage Search and Geo]], [[22 - Elasticsearch]]

### Keeping the search index in sync with the database

| Option | Costs you | Pick when |
|---|---|---|
| **CDC** (Debezium → Kafka → indexer) | Pipeline to run | Default |
| **Outbox → Kafka → indexer** | Outbox table | The app emits domain events |
| **Mongo change streams** (resume token) / **DynamoDB Streams** (24 h) | Store-specific | Those sources |
| ~~Dual write in the request~~ | Drifts on partial failure | Never |

- **Idempotent indexer:** use the event's stable id as the ES `_id` (never let ES mint ids), and **external versioning** (`version_type=external`) so a late or replayed event can't overwrite a newer document.
- **Depth:** [[22 - Elasticsearch]], [[10 - Distributed Building Blocks]], [[20 - Data Processing and Analytics]]

### A just-saved item doesn't show up in search

| Option | Pick when |
|---|---|
| **Accept ~1 s refresh** (near real-time) | Default |
| **Read your own item from the database** | The user must see what they just saved |

- **Depth:** [[09 - Storage Search and Geo]]

### Ranking favours keyword-stuffed or long documents

- **Fix:** BM25 (term-frequency saturation + length normalisation) rather than raw TF-IDF, then mix in popularity and recency. Put hard constraints (tenant, status, price range) in `filter` (not scored, cached), and relevance in `must`.
- **Depth:** [[09 - Storage Search and Geo]], [[22 - Elasticsearch]]

### Autocomplete is too slow to rank live

- **Fix:** a prefix structure (trie or ES completion suggester) with the top-K suggestions per prefix computed offline. **Depth:** [[09 - Storage Search and Geo]]

### A field needs full-text search *and* exact match, sort and aggregation

- **Fix:** map it as `text` with a `.keyword` sub-field at index creation; aggregate and sort on `keyword`/numeric/date fields (doc values), never on `text` (fielddata eats heap).
- **Depth:** [[22 - Elasticsearch]]

### Mapping explosion from dynamic mapping

- **Fix:** `"dynamic": "strict"` in production, or route unknown fields into one catch-all field. **Depth:** [[22 - Elasticsearch]]

### Mapping, analyzer or shard count must change

- **Fix:** create a new index, `_reindex` or backfill, catch up via CDC replay, then **swap the alias atomically** (rollback = swap back). Plan primary shard counts for growth (roughly tens of GB per shard); oversharding costs per-shard overhead.
- **Depth:** [[22 - Elasticsearch]], [[09 - Storage Search and Geo]]

### Deep pagination in Elasticsearch

| Option | Costs you | Pick when |
|---|---|---|
| **`from` + `size`** | Cost grows per shard; capped at 10,000 | First few UI pages |
| **`search_after` + point-in-time** | No random page jumps | Infinite scroll, deep pages |
| **Product cap** ("top 1,000 results") | No deep pages | Most products |
| **Scroll** | Legacy | Bulk export (PIT replaces it) |

- **Depth:** [[22 - Elasticsearch]]

### Indexing too slow, or fast-changing fields churn merges

| Problem | Fix |
|---|---|
| One request per document | `_bulk` API |
| Big bulk load slowed by refreshes | Widen or disable `refresh_interval` during the load |
| View counts / stock updated every second | Keep fast-changing values out of the search document, or batch the updates |

- **Depth:** [[22 - Elasticsearch]]

### Every tenant's search hits every shard

| Option | Costs you | Pick when |
|---|---|---|
| **Custom routing by `tenant_id`** | A big tenant becomes a hot shard | Many small tenants |
| **Own index for big tenants** | More indices | A few huge tenants |

- **Depth:** [[22 - Elasticsearch]]

### Deleting old logs and events is expensive

- **Fix:** time-based indices behind an alias or data stream, rolled over by age/size, with ILM (hot → warm → cold → delete) dropping whole indices instead of delete-by-query.
- **Depth:** [[22 - Elasticsearch]]

### Cluster yellow or red; hot node; disk full

| Symptom | Meaning / fix |
|---|---|
| **Yellow** | A replica is unallocated; traffic works, one failure from data loss. Replace the node |
| **Red** | A primary is unallocated; queries on it fail. Always run ≥ 1 replica |
| **Hot node** | Uneven allocation; allocation awareness or manual rebalance |
| **Disk watermark** | Writes blocked; alert early, add storage, drop old indices |
| **Master unresponsive** | Dedicated master-eligible nodes (3) at production scale |

- **Depth:** [[22 - Elasticsearch]]

---

## Semantic Search

### Keyword search misses synonyms and paraphrases

| Option | Costs you | Pick when |
|---|---|---|
| **Exact kNN on embeddings** | `O(n·d)` per query | Up to tens of thousands of vectors, or offline |
| **HNSW** (layered graph) | Memory-heavy | Default for online vector search |
| **IVF** (cluster, scan nearest buckets) | Recall depends on tuning | Very large sets where memory is the limit |

- **Depth:** [[09 - Storage Search and Geo]]

### Vector search misses relevant results (low recall)

| Option | Costs you | Pick when |
|---|---|---|
| **Raise `ef_search`** | Slower queries | Recall too low |
| **Hybrid search**: BM25 + vector, merge, rerank the top N | Two retrievals | Exact terms (codes, names) and paraphrases both matter |

- **Recall** = the share of the true nearest neighbours the approximate search actually returns.
- **Depth:** [[09 - Storage Search and Geo]]

---

## Geo

### Find nearby drivers or restaurants

| Option | Costs you | Pick when |
|---|---|---|
| **Geohash prefix on an index** | Cell-edge misses; fixed cell sizes | Interview default |
| **Redis GEO** | In memory | Moving entities with high write rates (drivers pinging every few seconds) |
| **PostGIS** (GiST / R-tree) | Struggles at huge write QPS | Stable places already in Postgres |
| **Quadtree** | Lives in a service's memory | Very uneven density |
| **S2** | Library complexity | Global coverage, polygons |
| **H3** hexagons | Library | Delivery zones, surge pricing, heatmaps |

- **Default:** moving things in Redis GEO, fixed places in PostGIS or an indexed geohash column.
- **Depth:** [[09 - Storage Search and Geo]]

### Nearby points across a cell edge are missed

- **Fix:** always query the cell **plus its neighbours**. **Depth:** [[09 - Storage Search and Geo]]

### A dense city cell overloads one shard

- **Fix:** smaller cells in dense areas, or a density-adaptive index (quadtree). **Depth:** [[09 - Storage Search and Geo]]

---

## Analytics

### Analytics queries slow down the OLTP primary

| Option | Costs you | Pick when |
|---|---|---|
| **Read replica** | Still row-oriented; heavy queries lag it | Light reporting, first step |
| **Columnar store fed by CDC** (ClickHouse, BigQuery, Redshift, Snowflake) | A pipeline | Default for analytics |

- **Depth:** [[20 - Data Processing and Analytics]]

### Row or columnar storage?

| Option | Pick when |
|---|---|
| **Row store** (Postgres, MySQL) | Serving the product: point reads, whole-row updates |
| **Column store** | Scans of a few columns over many rows; dashboards, ad hoc analysis |

- **Depth:** [[20 - Data Processing and Analytics]]

### Warehouse, lake or lakehouse?

| Option | Costs you | Pick when |
|---|---|---|
| **Warehouse** (schema-on-write) | Higher cost per TB | BI and SQL on modelled data |
| **Lake** (Parquet/JSON in S3, schema-on-read) | No transactions; quality is the reader's problem | Raw archive, ML data |
| **Lakehouse** (Parquet + Iceberg/Delta/Hudi) | Table-format tooling | Lake cost with atomic commits, schema evolution, time travel |

- **Depth:** [[20 - Data Processing and Analytics]]

### ETL or ELT?

| Option | Pick when |
|---|---|
| **ELT** (load raw, transform in-warehouse SQL) | Default; raw data kept for re-transforming |
| **ETL** (transform before load) | Data must be cleaned or masked first (PII) |

- **Depth:** [[20 - Data Processing and Analytics]]

### Keeping a warehouse in sync with OLTP

| Option | Costs you | Pick when |
|---|---|---|
| **Full dump** | Hours of staleness, heavy load | Small tables, initial load |
| **Incremental by `updated_at`** | Misses deletes and changes that don't touch `updated_at` | Reliable `updated_at`, no deletes |
| **CDC** | Pipeline | Default for anything serious |

- **Depth:** [[20 - Data Processing and Analytics]]

### High-volume telemetry / time series

- **Default:** metadata in Postgres; samples in a TSDB, Cassandra or ClickHouse; 1-minute rollups for charts; raw data dropped after 7–30 days.
- **Depth:** [[16 - Estimation and Decision Cheat Sheet]], [[09 - Storage Search and Geo]]

---

## Batch and Stream Processing

### Processing a large bounded dataset ("all of yesterday's events")

- **Options:** Spark (DAG, in-memory, partitions; the shuffle is the expensive step), scheduled by Airflow with retries and re-runs for past dates. MapReduce is the underlying pattern.
- **Depth:** [[20 - Data Processing and Analytics]]

### A slow job with one straggler (skew, hot key in a shuffle)

- **Fixes:** shuffle less (filter early, pre-aggregate before joins, broadcast small tables); salt the hot key and aggregate in two stages; filter nulls before joining.
- **Depth:** [[20 - Data Processing and Analytics]]

### Re-runs double-count output

- **Fix:** idempotent jobs: atomically overwrite the date partition, or `MERGE` by key. **Depth:** [[20 - Data Processing and Analytics]]

### Real-time aggregation over an unbounded stream

| Window | Pick when |
|---|---|
| **Tumbling** | Regular reports and counters |
| **Sliding** | Moving averages, rolling alerts |
| **Session** | User sessions, engagement |

- **Rule:** group by **event time**, not processing time.
- **Depth:** [[20 - Data Processing and Analytics]]

### Late events make window counts too low

| Option | Pick when |
|---|---|
| **Watermarks + allowed lateness**, emitting updated results | Default; longer wait = more complete, higher latency |
| **Side output / correction path** | The late tail is small or fixed later in batch |

- **Depth:** [[20 - Data Processing and Analytics]]

### Stream job crashes and loses state

- **Fix:** periodic checkpoints of state plus input offsets to durable storage, and input retained long enough to replay. Kafka Streams rebuilds local state from its changelog topic.
- **Depth:** [[20 - Data Processing and Analytics]], [[06 - Ecosystem Resilience and Design Patterns]]

### Duplicate events inflate analytics counts

- **Fix:** a stable event id at the source; dedupe in stream state over a window, or `MERGE`/upsert in the warehouse. Exactly-once in a pipeline = atomic state + offset checkpoints and transactional or idempotent sinks.
- **Depth:** [[20 - Data Processing and Analytics]]

### Which stream engine?

| Engine | Costs you | Pick when |
|---|---|---|
| **Flink** | A separate cluster | Low-latency, stateful, event-time-correct |
| **Kafka Streams** | Kafka in and out only | No extra cluster |
| **Spark Structured Streaming** | Seconds of latency (micro-batch) | Already on Spark |

- **Depth:** [[20 - Data Processing and Analytics]]

### Need both fresh and correct numbers

| Option | Costs you | Pick when |
|---|---|---|
| **Lambda** (batch + speed layer) | Two code paths that must agree | Heavy historical recompute |
| **Kappa** (one stream job; replay the log for a new version) | Long retention, fast replay | The stream can do the full logic |
| **Hybrid**: stream for fresh numbers, nightly batch overwrites as truth | Two jobs | Common in practice |

- **Depth:** [[20 - Data Processing and Analytics]]

### Pipeline silently produces wrong numbers

- **Fixes:** explicit-schema formats (Avro, Protobuf, Parquet) with a registry and additive evolution; data contracts (schema, meaning, freshness, owner); automated quality checks (row counts, null keys, reconciled totals) that block downstream jobs.
- **Depth:** [[20 - Data Processing and Analytics]]

---

## Approximate Counting

### Distinct counts, heavy hitters, membership at huge scale

| Need | Structure | Costs you |
|---|---|---|
| **Unique visitors** | HyperLogLog (Redis `PFADD`/`PFCOUNT`, mergeable) | Small known error |
| **Top-K / heavy hitters** | Count-Min Sketch | Over-counts, never under |
| **"Have we seen this?"** before an expensive lookup | Bloom filter | False positives (confirm with the real store); no deletes |
| **Exploratory dashboards** | Sampling | Approximate |

- **Rule:** never approximate money, billing, or anything a user reconciles.
- **Depth:** [[10 - Distributed Building Blocks]], [[20 - Data Processing and Analytics]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[09 - Storage Search and Geo]] · [[22 - Elasticsearch]] · [[20 - Data Processing and Analytics]]
