---
tags: [languages/python-ai, status/draft]
created: 2026-10-14
---
# PyTorch for Inference Engineers

> [!abstract] What this is
> The PyTorch (2.1x) knowledge needed to **run** models in production rather than train them: tensors, **devices** and **dtypes**, moving data between CPU and GPU, **`torch.inference_mode`** and `eval()`, **batching** and padding, **CUDA's asynchronous execution** (and why timing code lies), **GPU memory** (weights, activations, KV cache, the caching allocator, OOMs), **mixed precision** (bf16/fp16), `torch.compile`, and threading under the GIL. GPU serving architecture and quantisation maths are in [[13 - GPU Serving and Disaggregated KV Cache]] and [[14 - Quantization Decoding and VRAM Math]].

---

## Why It Matters

- Inference code that "works" can be 10× slower or OOM in production: gradients tracked by accident, batch size 1, `.item()` syncs in loops, float32 weights on a GPU that supports bf16, CPU threads oversubscribed by many workers.

---

## Tensors, Devices, dtypes

```python
import torch

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
x = torch.tensor(batch_np, dtype=torch.float32)          # copies; torch.from_numpy shares CPU memory
x = x.to(device, non_blocking=True)                      # async copy when source is pinned memory

y = model(x)                                             # runs on GPU (asynchronously)
probs = y.softmax(dim=-1).cpu().numpy()                  # .cpu() synchronises and copies back
```

| dtype | Bytes | Use |
|---|---|---|
| `float32` | 4 | CPU inference, numerically sensitive ops |
| **`bfloat16`** | 2 | Default for modern GPU inference (A100/H100/L4+): float32 range, less precision |
| `float16` | 2 | Older GPUs; narrower range (overflow risk) |
| `int8` / `fp8` / int4 | 1 / 1 / 0.5 | Quantised weights (via libraries: bitsandbytes, torchao, GPTQ/AWQ kernels) ([[05 - Hugging Face Stack]]) |

- Tensors on different devices can't mix: `RuntimeError: Expected all tensors to be on the same device`.
- `.to(device)` for a model moves all parameters; load directly to the target device and dtype to avoid a full float32 copy in CPU RAM first.

---

## Inference Mode and eval()

```python
model.eval()                                   # dropout off, batchnorm uses running stats
with torch.inference_mode():                   # no autograd graph, no version counters: less memory, faster
    logits = model(input_ids=ids, attention_mask=mask).logits
```

- **`model.eval()`** changes layer behaviour; **`inference_mode()`** (stricter, faster than `no_grad()`) disables gradient tracking. You need both.
- Forgetting `inference_mode`/`no_grad` keeps activations alive for backward: memory grows per request until OOM.

---

## Batching and Padding

- GPUs are throughput machines: batch size 1 leaves most of the hardware idle. Batch requests (offline jobs, embedding pipelines, classifier services).
- Variable-length inputs are **padded** to the longest in the batch, with an **attention mask**; sort or bucket by length to waste less compute.
- Online services: **dynamic batching** collects requests for a few milliseconds and runs them together (Triton, TorchServe, Ray Serve, or a small asyncio batcher). LLM generation needs **continuous batching** and a KV-cache manager: use vLLM/SGLang/TGI rather than a hand-written loop ([[06 - Serving Models from Python]]).

---

## CUDA Is Asynchronous

- Kernel launches return immediately; the CPU queues work on a **CUDA stream** and continues. The CPU waits only at **synchronisation points**: `.item()`, `.cpu()`, `.numpy()`, printing a tensor, `torch.cuda.synchronize()`, some allocations.

```python
start = time.perf_counter()
y = model(x)
elapsed = time.perf_counter() - start          # WRONG: measures kernel launch, not execution

torch.cuda.synchronize(); start = time.perf_counter()
y = model(x); torch.cuda.synchronize()
elapsed = time.perf_counter() - start          # right (or CUDA events / torch.profiler)
```

- `.item()` inside a loop forces a sync each iteration and serialises CPU and GPU: keep results on the GPU and transfer once.
- **Pinned memory** (`pin_memory=True` in DataLoaders, `tensor.pin_memory()`) + `non_blocking=True` overlaps host→device copies with compute.

---

## GPU Memory

| Consumer | Size driver |
|---|---|
| **Weights** | params × bytes per param (7B × 2 bytes in bf16 ≈ 14 GB) |
| **Activations** | batch × sequence × hidden × layers (transient during the forward pass) |
| **KV cache** (LLMs) | 2 × layers × kv_heads × head_dim × seq_len × batch × bytes; often the largest at long context ([[14 - Quantization Decoding and VRAM Math]]) |
| **CUDA context + workspace** | Hundreds of MB per process per GPU |
| **Allocator cache** | Freed blocks kept for reuse |

- PyTorch's **caching allocator** keeps freed memory reserved: `nvidia-smi` shows reserved, not used. Inspect with `torch.cuda.memory_allocated()`, `memory_reserved()`, `memory_summary()`, and memory snapshots.
- **Fragmentation** from varying shapes can cause OOMs with free memory available: `PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True` helps.
- `torch.cuda.empty_cache()` returns cached blocks to the driver (for other processes); it doesn't free live tensors and slows later allocations.
- **One CUDA context per process**: N web workers each loading the model on one GPU = N copies of the weights + N contexts. Put the model in **one** inference process and call it from API workers ([[08 - Python AI Performance Traps]]).
- Never initialise CUDA before `fork`: use `spawn`/`forkserver` for multiprocessing ([[04 - Python Threads Processes and Executors]]).

---

## Speed-Ups

| Technique | Effect |
|---|---|
| **bf16/fp16 weights and compute** | ~2× faster, half memory vs fp32 on tensor-core GPUs |
| `torch.autocast(device_type="cuda", dtype=torch.bfloat16)` | Mixed precision for models kept in fp32 |
| **SDPA / FlashAttention** (`attn_implementation="sdpa"` or FlashAttention kernels) | Fused attention, less memory, faster at long sequence lengths |
| **`torch.compile(model)`** | Graph capture + fused kernels (Inductor); warm-up compile time; recompiles on new shapes (use `dynamic=True` or fixed buckets) |
| CUDA graphs (`mode="reduce-overhead"`) | Cut kernel-launch overhead for small batches |
| Quantisation | Fit bigger models, faster memory-bound decoding ([[05 - Hugging Face Stack]]) |
| ONNX Runtime / TensorRT | Export for optimised runtimes; extra build step |

---

## CPU Inference and Threads

- PyTorch uses intra-op thread pools (OpenMP/MKL); ops release the GIL. Default threads = cores; **K worker processes × cores threads oversubscribes** the CPU and kills throughput.
- Set `torch.set_num_threads(n)` (and `OMP_NUM_THREADS`) per process so workers × threads ≈ cores in the container's CPU limit.
- Small models on CPU (classifiers, rerankers, embedders): int8 dynamic quantisation, ONNX Runtime, batching.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **No `inference_mode` / `eval()`** | Memory growth, wrong outputs (dropout on) | Both, always |
| **Batch size 1 on GPU** | Low throughput, high cost per request | Batching / dynamic batching |
| **Timing without synchronising** | Misleading benchmarks | `torch.cuda.synchronize()` or CUDA events |
| **`.item()`/`.cpu()` in loops** | CPU-GPU serialisation | Transfer once per batch |
| **Model loaded per web worker** | N× VRAM, OOM | One inference process/service |
| **CUDA initialised before fork** | Child crashes / hangs | `spawn`/`forkserver`; load after fork |
| **fp32 on modern GPUs** | 2× memory, slower | bf16 |
| **`torch.compile` recompiling per shape** | Latency spikes | Bucketed shapes, `dynamic=True`, warm-up |
| **CPU thread oversubscription** | Throughput collapses | `torch.set_num_threads`, `OMP_NUM_THREADS` per worker |
| **Fragmentation OOMs** | OOM with free memory | `expandable_segments`, consistent shapes |

---

## Interview Lines

> [!tip] Say it like this
> - "For inference I call `model.eval()` and run under `torch.inference_mode()`, so no autograd graph is built and memory stays flat."
> - "CUDA is asynchronous: the CPU only waits at sync points like `.item()` or `.cpu()`, so I synchronise before timing and avoid syncs in loops."
> - "GPU memory is weights plus activations plus, for LLMs, the KV cache; PyTorch's caching allocator keeps freed blocks reserved, which is why `nvidia-smi` looks full."
> - "Each process creates its own CUDA context and model copy, so web workers call one inference process instead of loading the model themselves."
> - "On modern GPUs I run bf16 with SDPA or FlashAttention, batch requests, and use `torch.compile` with bucketed shapes."

## Related Notes

- [[05 - Hugging Face Stack]] · [[06 - Serving Models from Python]] · [[08 - Python AI Performance Traps]] · [[02 - NumPy for Engineers]]
- [[13 - GPU Serving and Disaggregated KV Cache]] · [[14 - Quantization Decoding and VRAM Math]] · [[Languages/README]]
