---
tags: [agentic/topics, status/draft]
created: 2026-10-11
---
# Quantization, Decoding and VRAM Math

> [!abstract] What this is
> The numeric and decoding tricks that make models cheaper and faster to serve: precision formats (**BF16, FP8, INT8, INT4**), **weight-only vs weight-and-activation** quantization (GPTQ, AWQ), **KV-cache compression**, **constrained decoding** (FSMs, grammars, logit masking), **speculative decoding** (draft models, EAGLE), and the **VRAM and cost arithmetic** to size a deployment. The decision this helps with: which precision and decoding setup fits a quality bar, a GPU budget and a latency target.

---

## Why It Exists

- Memory and memory bandwidth limit LLM serving ([[13 - GPU Serving and Disaggregated KV Cache]]). Halving the bytes per weight roughly halves the memory needed and speeds up memory-bound decode, but can cost quality.
- Structured output and speculative decoding change *how* tokens are chosen, affecting reliability and latency without changing the model.
- Being able to say "a 70B model in FP8 needs about 70 GB for weights, so on two 80 GB GPUs I have ~75 GB left for KV cache, roughly 240k tokens of context across all requests" is an SDE-3 signal.

---

## Precision Formats

| Format | Bits | Notes | Typical use |
|---|---|---|---|
| **FP32** | 32 | Full precision | Training master weights; rarely for inference |
| **BF16** | 16 | Same exponent range as FP32, less precision; numerically robust | Default for training and high-quality inference |
| **FP16** | 16 | More precision, smaller range (overflow risk) | Older inference setups |
| **FP8** (E4M3 / E5M2) | 8 | Native on recent accelerators (e.g. NVIDIA Hopper and later); usually small quality loss | High-throughput inference of weights and activations |
| **INT8** | 8 | Integer with scale factors | Weight or weight+activation quantization |
| **INT4** (and FP4 variants) | 4 | 4× smaller than BF16; quality loss more noticeable | Memory-constrained deployment, local models |

- Quantized values need **scales** (and sometimes zero points) per tensor, per channel, or per **group** of weights (e.g. groups of 128). Finer groups = better accuracy, slightly more memory.

---

## Weight-Only vs Weight-and-Activation Quantization

| | Weight-only (e.g. W4A16, W8A16) | Weights and activations (e.g. W8A8, FP8) |
|---|---|---|
| What's quantized | Stored weights; dequantized on the fly to BF16 for compute | Weights **and** the activations flowing through matmuls |
| Speeds up | **Memory-bound decode** (fewer bytes read per step) and fits bigger models | Also **compute-bound prefill**, using low-precision tensor cores |
| Quality risk | Lower at 8 bits; noticeable at 4 bits | Activation outliers make it harder; FP8 handles it well on supported hardware |
| Pick when | Fit a model on fewer/smaller GPUs; latency-bound single-user serving | High-throughput serving on hardware with FP8/INT8 support |

### Methods to name

- **GPTQ**: post-training quantization layer by layer, using second-order (Hessian-based) information to adjust remaining weights to compensate for rounding error. Needs a small calibration set.
- **AWQ** (activation-aware weight quantization): a small fraction of weights matter much more because they multiply large activations; AWQ scales those channels to protect them before quantizing. Robust at 4 bits.
- **SmoothQuant**: shifts quantization difficulty from activations to weights for W8A8.
- **GGUF / llama.cpp** formats for CPU and local inference.

### Quality caveat

- Quantization quality loss is **uneven**: short-answer benchmarks may look unchanged while **multi-step reasoning, long-context retrieval, code and tool use** degrade more. Aggressive INT4 on a planning or orchestrator model can produce subtly wrong plans.
- Rule: evaluate the quantized model **on your own tasks and trajectories** ([[11 - Evals Observability and Silent Drift]]), not on headline benchmarks. A common split: FP8 or BF16 for the planner/reasoner, INT4 for high-volume simple steps.

---

## KV-Cache Compression

- KV cache often uses more memory than weights at high concurrency or long contexts ([[13 - GPU Serving and Disaggregated KV Cache]]).
    + **FP8 / INT8 KV cache**: halves KV memory vs FP16 with usually small quality loss → roughly 2× more concurrent tokens.
    + **Architecture-level**: grouped-query / multi-query attention, multi-head latent attention (compressed latent KV), sliding-window layers.
    + **Eviction / sparsification** (keep only "important" tokens' KV): research-stage, riskier for retrieval-style tasks.
- Weight quantization and KV quantization are **independent** choices: W4 weights with FP16 KV, FP8 weights with FP8 KV, and so on.

---

## Constrained Decoding

- Structured outputs and strict tool schemas ([[00 - LLM Fundamentals for Engineers]], [[03 - Tool Engineering and Action Spaces]]) are enforced by **masking logits**: at each step, tokens that would make the output invalid get probability zero.

```mermaid
flowchart LR
    S[JSON Schema / regex / grammar] --> C[Compile to FSM or pushdown automaton]
    C --> M[Per step: allowed-token mask]
    L[Model logits] --> X[Apply mask]
    M --> X
    X --> T[Sample next token]
    T --> C
```

| Approach | How | Notes |
|---|---|---|
| **FSM from regex / schema** (e.g. Outlines) | Compile the schema to a finite-state machine over tokens; each state has an allowed-token set | Fast per step; compiling complex schemas is expensive |
| **Grammar engines** (XGrammar, llguidance) | Context-free grammars (pushdown automata) with precomputed token masks; handle nested JSON efficiently | Near-zero per-token overhead once compiled; used in vLLM, SGLang, TensorRT-LLM |
| **Logit masking hooks** | Custom per-step masks (allow only certain labels or ids) | Classification, enum outputs |

- **Compile cost** is the hidden latency: a new, complex schema sent per request must be compiled before the first token, inflating TTFT. Fix: **pre-compile and cache** grammars for static Pydantic schemas; keep schemas small and reused; avoid generating a new schema per request.
- Constrained decoding guarantees **syntax**, not semantics. It can also slightly hurt quality if the schema forces an unnatural order (e.g. a `final_answer` field before a `reasoning` field); put reasoning fields first when you need them.

---

## Speculative Decoding

- Decode is memory-bound: verifying several tokens in one forward pass costs about the same as generating one. Speculative decoding exploits that:
    1. A cheap **draft** proposes the next `k` tokens.
    2. The large **target** model scores all `k` positions in **one** forward pass.
    3. Accept the longest prefix the target agrees with (plus one corrected token); discard the rest.
- With proper rejection sampling, the output distribution is **identical** to the target model's: it's a lossless speed-up. The speed-up depends on the **acceptance rate** (how often the draft is right).

| Draft source | How | Notes |
|---|---|---|
| **Small draft model** | A smaller model of the same family | Needs the same tokenizer; extra memory |
| **Medusa heads** | Extra prediction heads on the target predict several future tokens | Needs fine-tuning heads |
| **EAGLE** (and successors) | A lightweight head drafts at the **feature** level using the target's hidden states | High acceptance rates; widely supported in engines |
| **N-gram / prompt lookup** | Copy likely continuations from the prompt itself | Free; great for edits, code and RAG answers that quote context |

- Best when: low-to-moderate batch sizes and latency-sensitive serving. At very high batch sizes the GPU is already busy, so the extra verification work helps less (or hurts throughput).
- Also effective for **structured outputs**: predictable JSON keys and boilerplate are easy for drafts, raising acceptance.

---

## VRAM Math

```
total VRAM ≈ weights + KV cache + activations/workspace + framework overhead
weights    = parameters × bytes per parameter
KV cache   = KV bytes per token × total tokens in flight (sum over concurrent requests)
overhead   ≈ 10–20% (CUDA graphs, buffers, fragmentation) — heuristic
```

| Model size | BF16 | FP8 / INT8 | INT4 |
|---|---|---|---|
| 8B | ~16 GB | ~8 GB | ~4–5 GB |
| 70B | ~140 GB | ~70 GB | ~35–40 GB |
| 405B | ~810 GB | ~405 GB | ~200+ GB |

### Worked example

- Serve a 70B model in **FP8** on **2 × 80 GB** GPUs (160 GB total), tensor parallel:
    + Weights ≈ 70 GB.
    + Overhead ≈ 15 GB.
    + Left for KV ≈ 75 GB.
    + KV per token at FP16 ≈ 0.31 MB ([[13 - GPU Serving and Disaggregated KV Cache]]) → **~240k tokens in flight**, e.g. ~30 concurrent requests at 8k tokens each.
    + With an **FP8 KV cache** (~0.16 MB/token) → ~480k tokens, roughly doubling concurrency.
- In BF16 the same model needs ~140 GB of weights, leaving almost nothing for KV on 160 GB: you'd need more GPUs.

### Decode speed and cost

- Single-stream decode is bandwidth-bound, so an upper bound is:

```
tokens/sec per sequence ≲ memory bandwidth ÷ bytes read per step (≈ weight bytes, per GPU shard)
```

- E.g. ~3.35 TB/s bandwidth and ~35 GB of weights per GPU (70 GB FP8 split over 2) → at most ~95 steps/s per sequence, before KV reads and overheads. Real numbers are lower.
- Batching multiplies **aggregate** throughput because the same weight reads serve many sequences.
- Cost per million output tokens (illustrative):

```
$/1M tokens ≈ (GPU $/hour × number of GPUs) ÷ (aggregate tokens/sec × 3600 ÷ 1,000,000)
```

```python
def cost_per_million(gpu_hourly: float, gpus: int, agg_tokens_per_s: float, utilisation: float) -> float:
    effective = agg_tokens_per_s * utilisation               # idle time still costs money
    return gpu_hourly * gpus / (effective * 3600 / 1e6)

print(cost_per_million(gpu_hourly=3.0, gpus=2, agg_tokens_per_s=2500, utilisation=0.6))
```

- The levers in this note show up directly: quantization raises tokens/s and frees memory for bigger batches; KV compression raises concurrency; speculative decoding raises per-sequence speed; utilisation decides whether self-hosting beats an API ([[12 - LLM Cost and Latency Economics]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **INT4 on a planner** | Multi-step reasoning and plans degrade subtly | FP8/BF16 for reasoning steps; evaluate quantized models on your trajectories |
| **Calibration mismatch** | GPTQ/AWQ calibrated on generic text underperforms on your domain | Calibrate on representative data |
| **Per-request schema compilation** | Constrained decoding inflates TTFT | Pre-compile and cache static schemas; keep schemas small |
| **Schema forces answer-before-reasoning** | Lower answer quality | Put reasoning fields before answer fields |
| **Speculative decoding at high batch** | Little gain or lower throughput | Use for latency-sensitive, lower-batch serving; measure acceptance |
| **Forgetting KV in VRAM plans** | Model "fits" but concurrency collapses | Budget weights + KV × tokens in flight + overhead |
| **Mixing tokenizers in drafting** | Draft model unusable | Draft from the same family / tokenizer, or use EAGLE / n-gram drafts |

---

## Interview Lines

> [!tip] Say it like this
> - "Weights are parameters times bytes: a 70B model is about 140 GB in BF16, 70 GB in FP8, 35–40 GB in INT4, and whatever's left goes to KV cache."
> - "Weight-only INT4 speeds up memory-bound decode; FP8 weights and activations also speed up prefill on hardware that supports it."
> - "I'd keep the planner in FP8 or BF16 because aggressive quantization hurts multi-step reasoning more than benchmarks show."
> - "Constrained decoding masks invalid tokens with a compiled grammar; I pre-compile static schemas so compilation doesn't inflate time to first token."
> - "Speculative decoding drafts several tokens and verifies them in one pass; it's lossless and helps most at low batch sizes."

## Related Notes

- [[13 - GPU Serving and Disaggregated KV Cache]] · [[12 - LLM Cost and Latency Economics]] · [[15 - Post-Training and Model Adaptation]] · [[03 - Tool Engineering and Action Spaces]]
- [[Agentic/README]]
