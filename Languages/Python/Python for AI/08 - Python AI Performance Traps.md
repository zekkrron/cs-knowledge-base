---
tags: [languages/python-ai, status/draft]
created: 2026-10-14
---
# Python AI Performance Traps

> [!abstract] What this is
> The recurring performance and reliability traps in Python AI services on the standard GIL build, in one place: **CPU-heavy pre/post-processing** (tokenisation, chunking, parsing PDFs, JSON) holding the GIL and stalling event loops, **process pools for tokenisation** and their pickling costs, **forked workers and model memory** (N copies of weights, CUDA and fork), **thread oversubscription** from BLAS/PyTorch/tokenizers inside multiple workers, blocking SDKs in async code, and startup/import costs of heavy ML libraries. Each trap comes with the symptom and the fix. Free-threaded Python is noted as an extra where it changes the picture.

---

## Why It Matters

- AI services mix **I/O-bound** work (LLM APIs, vector DBs) with **CPU-bound** work (tokenising, chunking, embedding locally, parsing documents) and **GPU** work. The GIL punishes mixing CPU-bound Python into the I/O path; memory punishes copying models into every worker.

---

## Trap 1: CPU Work on the Event Loop

**Symptom**: an async FastAPI/agent service has low CPU-per-request but p99 latency climbs with concurrency; liveness probes time out during document uploads.

```python
@app.post("/ingest")
async def ingest(file: UploadFile):
    text = extract_pdf_text(await file.read())          # CPU-heavy, pure Python + C: blocks the loop for seconds
    chunks = chunk(text)                                 # more CPU
    tokens = [count_tokens(c) for c in chunks]           # more CPU
    ...
```

**Fix**:

```python
process_pool = ProcessPoolExecutor(max_workers=cpu_limit, max_tasks_per_child=200)   # created in the lifespan

@app.post("/ingest")
async def ingest(file: UploadFile, queue: IngestQueue):
    blob_key = await storage.put(file)                   # stream to object storage
    await queue.enqueue("ingest_document", blob_key)     # heavy work in background workers
    return {"status": "accepted"}

# in the worker process (or for latency-critical inline work):
loop = asyncio.get_running_loop()
chunks = await loop.run_in_executor(process_pool, extract_and_chunk, path)
```

- Small CPU tasks (< a few ms): inline is fine. Medium: process pool. Large or batchable: background queue ([[08 - FastAPI Background Work]]).
- `asyncio.to_thread` only helps if the work **releases the GIL** (NumPy, PyTorch kernels, fast tokenizers' batch encode, `hashlib`, compression); pure-Python parsing in a thread still competes for the GIL ([[03 - The GIL and Free-Threaded Python]]).

---

## Trap 2: Process Pools That Cost More Than They Save

**Symptom**: moving tokenisation to a process pool made it slower.

- Every task pickles its arguments and results: sending 100 KB strings to tokenise and receiving token lists back can cost more than tokenising in place.
- Each pool process re-imports heavy libraries and reloads the tokenizer/model (seconds of startup, hundreds of MB each).

**Fixes**:
- **Batch** (`chunksize`, or tasks of 1,000 texts), and return compact results (counts, NumPy arrays) instead of large Python lists.
- Load the tokenizer once per worker via an **initializer** (`ProcessPoolExecutor(initializer=load_tokenizer)`).
- Prefer **fast tokenizers' batch APIs** in threads: HF `tokenizers` and `tiktoken` run in Rust and release the GIL for batch encoding, giving parallelism without pickling.
- Pass file paths/object keys, not file contents ([[04 - Python Threads Processes and Executors]]).

---

## Trap 3: Models Copied Into Every Worker

**Symptom**: OOM kills when scaling Uvicorn/Gunicorn workers; GPU OOM with `--workers 4`; 4 × model memory.

| Setup | Memory |
|---|---|
| 4 workers each loading a 2 GB embedding model on CPU | 8 GB + 4× Python overhead |
| 4 workers each loading a model on one GPU | 4 CUDA contexts + 4 weight copies → OOM |
| 1 inference process + 4 API workers calling it | 1 copy, batched across all callers |

**Fixes**:
- Serve models from a **dedicated inference process/service** (vLLM, TEI, Triton, Ray Serve, or a single batching worker) and call it over HTTP/gRPC ([[06 - Serving Models from Python]]).
- On CPU, if per-worker models are unavoidable: preload in the Gunicorn master (`preload_app`) for copy-on-write sharing, `gc.freeze()` before forking, and keep weights in NumPy/safetensors **memory-mapped** buffers (no per-object refcount writes). Refcount updates still dirty pages for Python objects ([[02 - Python Memory Management and GC]]).
- **Never fork after CUDA initialisation**: CUDA contexts don't survive fork. Load GPU models after the worker starts, or use `spawn`/`forkserver` ([[04 - PyTorch for Inference Engineers]]).

---

## Trap 4: Thread Oversubscription

**Symptom**: on a 4-CPU pod with 4 workers, throughput is worse than with 1 worker; CPU throttling metrics spike.

- NumPy/BLAS (OpenBLAS/MKL), PyTorch (OpenMP), ONNX Runtime, FAISS and HF tokenizers each start **thread pools sized to all visible cores**, often the **host's** cores, not the container limit. 4 workers × 16 threads × several libraries = hundreds of runnable threads on 4 CPUs.
- **Fix**: set thread counts explicitly per process: `OMP_NUM_THREADS`, `MKL_NUM_THREADS`, `OPENBLAS_NUM_THREADS`, `torch.set_num_threads(n)`, `faiss.omp_set_num_threads(n)`, `TOKENIZERS_PARALLELISM=false` in forked workers (also silences the HF fork warning). Target workers × threads ≈ CPU limit.

---

## Trap 5: Blocking SDKs and Clients in Async Code

**Symptom**: one slow vector DB or LLM call freezes all requests in a worker.

- Sync SDKs (some vector DB clients, boto3, sync OpenAI/Anthropic clients, `requests`) inside `async def` block the loop ([[02 - FastAPI Request Lifecycle and Sync vs Async]]).
- **Fix**: async clients (`AsyncOpenAI`, `AsyncQdrantClient`, `aioboto3`/async variants), or `asyncio.to_thread` for the rare sync call, with timeouts everywhere ([[01 - Async LLM Client Patterns]]).

---

## Trap 6: Import and Startup Costs

**Symptom**: workers take 20–60 s to start; autoscaling and deploys are slow; CLI tools feel sluggish.

- `import torch`, `transformers`, `pandas`, cloud SDKs each cost seconds and hundreds of MB; model loading costs more.
- **Fixes**: measure with `python -X importtime`; import heavy libraries only in the processes that need them (API workers shouldn't import torch if inference is remote); **lazy imports** on 3.15 (`lazy import torch`) ([[01 - CPython Execution Model]]); bake model weights into images; readiness only after warm-up.

---

## Trap 7: Python-Level Loops Over Model Outputs

**Symptom**: GPU is fast but end-to-end latency is dominated by post-processing.

- Looping over logits/embeddings in Python, `.item()` per element, building Python lists of floats.
- **Fix**: keep results as tensors/arrays, vectorise post-processing (`softmax`, `topk`, masks), transfer once per batch ([[02 - NumPy for Engineers]], [[04 - PyTorch for Inference Engineers]]).

---

## Quick Diagnosis

| Symptom | Likely trap | Check |
|---|---|---|
| Latency grows with concurrency, CPU low | Blocking call on the loop | Loop-lag metric; py-spy/Tachyon on the loop thread |
| Latency grows with concurrency, CPU high | CPU work in the request path | CPU profile; move to pools/queues |
| More workers = less throughput | Thread oversubscription | Thread counts per process; CPU throttling |
| OOM when adding workers | Model per worker | RSS per worker; GPU memory per process |
| Slow scale-out | Imports/model loading | `-X importtime`; image-baked weights |
| Process pool slower than inline | Pickling and reloading | Batch, initializers, Rust tokenizers in threads |

> [!info] Free-threaded build (extra)
> On `python3.14t`, CPU-bound pure-Python pre/post-processing can run in parallel threads without pickling or per-process model copies, which addresses traps 1–3 within one process. It requires every native dependency (tokenizers, NumPy, PyTorch, drivers) to support free-threading, and the event-loop rule still holds: CPU work in a coroutine still blocks that loop. Treat it as an option to benchmark, not the default.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **CPU-heavy parsing in `async def`** | Whole worker stalls | Process pool / background queue |
| **Tiny tasks to a process pool** | Pickling overhead dominates | Batch; return compact results |
| **Model per web worker** | RAM/VRAM × workers, OOM | Dedicated inference service |
| **CUDA before fork** | Crashes/hangs in children | Load after fork; spawn/forkserver |
| **Default library thread pools in many workers** | Oversubscription, throttling | Explicit thread env vars per process |
| **Sync SDKs in async code** | Loop blocked on I/O | Async clients, `to_thread` |
| **Heavy imports in API workers** | Slow starts, high memory | Import only where needed; lazy imports |
| **Python loops over outputs** | Post-processing dominates latency | Vectorise; transfer once |

---

## Interview Lines

> [!tip] Say it like this
> - "AI services mix I/O-bound calls with CPU-bound tokenising and parsing; under the GIL the CPU work must leave the event loop, to a process pool or a background queue."
> - "Process pools pay for pickling and re-loading tokenizers, so I batch work, use initializers, or use Rust tokenizers in threads because they release the GIL."
> - "Models live in one inference process per GPU; loading them in every web worker multiplies memory and breaks batching, and CUDA must never be initialised before fork."
> - "BLAS, PyTorch and tokenizers each spawn thread pools sized to the host, so I set thread counts per worker to match the container's CPU limit."
> - "Slow scale-out usually comes from heavy imports and model downloads, so I bake weights into the image and keep torch out of the API workers."

## Related Notes

- [[03 - The GIL and Free-Threaded Python]] · [[04 - Python Threads Processes and Executors]] · [[02 - Python Memory Management and GC]] · [[13 - Python Performance and Profiling]]
- [[02 - FastAPI Request Lifecycle and Sync vs Async]] · [[04 - PyTorch for Inference Engineers]] · [[06 - Serving Models from Python]] · [[Languages/README]]
