---
tags: [languages/python-ai, status/draft]
created: 2026-10-14
---
# Data Tooling for Evals

> [!abstract] What this is
> The Python data tooling behind LLM and retrieval evaluation: **pandas vs Polars** (eager vs lazy, memory, speed, Arrow), **DuckDB** for SQL over files, file formats (**JSONL** for eval sets, **Parquet** for results), structuring **eval datasets** (versioned, stratified, with metadata), running evals concurrently, and **analysing results** (per-slice metrics, confidence intervals, paired comparisons, regressions). Eval methodology (what to measure, LLM-as-judge, drift) is in [[11 - Evals Observability and Silent Drift]]; this note is the code and data handling.

---

## Why It Matters

- Prompt and model changes are only safe if you can compare runs: same inputs, recorded outputs, scores per example, slices that show where things got worse. That's data engineering in miniature.
- Eval tables grow quickly (examples × variants × runs × judge scores); slow, memory-hungry tooling makes people skip evals.

---

## pandas vs Polars vs DuckDB

| | pandas (2.x/3.x) | **Polars** | DuckDB |
|---|---|---|---|
| Model | Eager DataFrame, index-based | Eager **and lazy** (query optimiser), no index | In-process SQL engine |
| Engine | Mostly single-threaded NumPy/Arrow backends | Multi-threaded Rust, Arrow memory | Multi-threaded C++, vectorised |
| Memory | Often several × data size | Lean; streaming for larger-than-RAM | Out-of-core |
| API | Huge ecosystem, mutable, many ways to do things | Expression API, consistent, immutable | SQL |
| Strings | Arrow-backed string dtype is the default in pandas 3 | Arrow strings | Native |
| Pick when | Notebooks, libraries that expect pandas, small data | **Analysis pipelines, larger tables, speed** | SQL over Parquet/JSONL files, joins, ad-hoc analysis |

- All three interoperate through **Arrow** cheaply (`pl.from_pandas`, `df.to_pandas()`, `duckdb.sql("... FROM df")` on DataFrames in scope).
- pandas traps: chained assignment (`df[df.x > 0]["y"] = 1` modifies a copy; pandas 3 makes copy-on-write the default, so it never modifies the original), `object` dtype columns, `apply` with Python functions (slow), silent dtype changes when NaNs appear in int columns.

```python
import polars as pl

results = (
    pl.scan_parquet("runs/*.parquet")                         # lazy: nothing read yet
      .filter(pl.col("suite") == "support-v3")
      .group_by(["variant", "category"])
      .agg(pl.col("correct").mean().alias("accuracy"),
           pl.col("latency_ms").quantile(0.95).alias("p95_ms"),
           pl.len().alias("n"))
      .sort(["category", "variant"])
      .collect()                                              # optimiser pushes filters/projections into the scan
)
```

---

## Formats

| Format | Use | Notes |
|---|---|---|
| **JSONL** | Eval inputs, golden answers, conversations | One example per line; diff-friendly; nested fields fine |
| **Parquet** | Run outputs, scores, large tables | Columnar, compressed, typed; fast scans of selected columns |
| CSV | Hand-offs to spreadsheets | Lossy types, quoting issues |
| Arrow / Feather | In-memory interchange, caching | Zero-copy between libraries |

- Store **inputs** (versioned) separately from **runs** (append-only, one row per example × variant × run).

---

## Structuring an Eval Dataset

```json
{"id": "refund-0142", "input": {"messages": [...]}, "expected": {"action": "refund", "amount": 42.0},
 "tags": ["refund", "partial", "de"], "source": "prod-sample-2026-09", "difficulty": "hard"}
```

- **Stable IDs** per example so runs can be joined and compared over time.
- **Version** the dataset (git/DVC/LFS, or a Hub dataset with revisions): a score is meaningless without the dataset version.
- **Tags/slices** (intent, language, customer tier, difficulty, source) so aggregate scores can be broken down: averages hide regressions in small but important slices.
- Mix sources: curated edge cases, sampled (and anonymised) production traffic, adversarial cases; keep a **held-out** set that prompts aren't tuned against.
- No secrets or raw personal data in eval files; anonymise production samples ([[10 - AI Gateway Security and Guardrails]]).

---

## Running Evals

```python
async def run_suite(examples: list[Example], variant: Variant, run_id: str) -> pl.DataFrame:
    sem = asyncio.Semaphore(16)

    async def one(ex: Example) -> dict:
        async with sem:
            start = time.perf_counter()
            try:
                out = await variant.call(ex.input)
                score = grade(ex, out)                          # exact match, schema checks, or judge model
                err = None
            except Exception as e:
                out, score, err = None, 0.0, type(e).__name__
            return {"run_id": run_id, "variant": variant.name, "id": ex.id, "tags": ex.tags,
                    "output": out, "score": score, "error": err,
                    "latency_ms": (time.perf_counter() - start) * 1000}

    async with asyncio.TaskGroup() as tg:
        tasks = [tg.create_task(one(ex)) for ex in examples]
    return pl.DataFrame([t.result() for t in tasks])
```

- Bounded concurrency and retries as for any LLM workload ([[01 - Async LLM Client Patterns]]); record **errors as rows**, not crashes.
- Record everything needed to reproduce: model, model version/date, prompt version (hash), parameters (temperature, seed), dataset version, code commit.
- Non-determinism: run important comparisons several times or at temperature 0; cache model outputs keyed by (input, prompt hash, model, params) to rescore cheaply.
- **LLM-as-judge**: fixed judge model and prompt version, structured judge output (Pydantic), and periodic agreement checks against human labels.

---

## Analysing Results

| Question | Technique |
|---|---|
| Is variant B better than A? | **Paired** comparison on the same examples (per-example score differences), not two independent averages |
| Is the difference real? | **Bootstrap confidence intervals** over examples; with few examples, differences of a few points are noise |
| Where did it get worse? | Per-slice metrics (group by tags); list examples that flipped from pass to fail |
| Did latency/cost change? | p50/p95 latency and tokens per example alongside quality |
| Regression gate in CI | Thresholds per critical slice, not just the overall mean |

```python
def bootstrap_diff(a: np.ndarray, b: np.ndarray, n: int = 10_000, seed: int = 0) -> tuple[float, float]:
    rng = np.random.default_rng(seed)
    d = b - a                                                  # paired per-example differences
    idx = rng.integers(0, len(d), size=(n, len(d)))
    means = d[idx].mean(axis=1)
    return tuple(np.percentile(means, [2.5, 97.5]))            # 95% CI for the improvement
```

- Look at **flipped examples** by hand: aggregate metrics tell you *whether*; examples tell you *why*.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Unversioned datasets/prompts** | Scores can't be compared or reproduced | Version data, prompts, models, code |
| **Only overall averages** | Slice regressions hidden | Per-tag metrics; slice thresholds |
| **Unpaired comparisons** | Noise mistaken for improvement | Paired differences + bootstrap CIs |
| **Tuning on the test set** | Overfit prompts, optimistic scores | Held-out set |
| **pandas `apply` and object columns at scale** | Slow, memory-hungry analysis | Polars/DuckDB expressions |
| **Chained assignment in pandas** | Changes silently not applied | `.loc`, copy-on-write semantics |
| **Errors crash the run** | Partial, biased results | Errors recorded as rows |
| **Personal data in eval files** | Compliance and leak risk | Anonymise; access control |

---

## Interview Lines

> [!tip] Say it like this
> - "Eval inputs live in versioned JSONL with stable IDs and tags; every run appends a Parquet table with outputs, scores, latency and full provenance."
> - "I analyse with Polars lazy scans or DuckDB over Parquet: multi-threaded and memory-lean compared with pandas."
> - "To compare two variants I use paired per-example differences with bootstrap confidence intervals, and I check per-slice results, because averages hide regressions."
> - "Runs record model version, prompt hash, parameters and dataset version, so any score can be reproduced."

## Related Notes

- [[11 - Evals Observability and Silent Drift]] · [[01 - Async LLM Client Patterns]] · [[02 - NumPy for Engineers]] · [[05 - Hugging Face Stack]]
- [[13 - Python Performance and Profiling]] · [[12 - Python Testing with pytest]] · [[Languages/README]]
