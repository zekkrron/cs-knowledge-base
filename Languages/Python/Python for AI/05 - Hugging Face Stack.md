---
tags: [languages/python-ai, status/draft]
created: 2026-10-14
---
# Hugging Face Stack

> [!abstract] What this is
> The Hugging Face libraries an engineer uses to load and run open models: the **Hub** (model repos, revisions, safetensors, caching, gated models), **`transformers` v5** (PyTorch-only since v5; `AutoModel`/`AutoTokenizer`, `pipeline`, `generate`, chat templates, attention implementations, device placement), **tokenizers** (fast Rust tokenizers, special tokens, padding sides), **`datasets`** (Arrow-backed, memory-mapped, streaming), **PEFT/LoRA** adapters, and **loading quantised models** (bitsandbytes, GPTQ/AWQ, torchao). The decision this helps with: loading models reproducibly and efficiently, and knowing when to hand serving to vLLM instead of `generate()`.

---

## Why It Matters

- Most open-model work starts with `from_pretrained`: the defaults decide download size, dtype, device, memory and security (pickle vs safetensors, remote code).
- `transformers` v5 removed TensorFlow/JAX and changed loading and tokenizer internals; v4-era snippets often need updates.

---

## The Hub

- Models, datasets and Spaces are **git repos** (LFS/Xet-backed). Files: `config.json`, weights (`*.safetensors`, sharded with an index), tokenizer files, `generation_config.json`, chat template.
- **Pin a revision** (commit hash) for reproducibility: `from_pretrained("org/model", revision="<sha>")`. `main` moves.
- Cache: `~/.cache/huggingface/hub` (`HF_HOME`/`HF_HUB_CACHE`); in containers, **pre-download into the image or a volume** (`hf download org/model --revision <sha>`) so pods don't download GBs at startup; set `HF_HUB_OFFLINE=1` in production.
- **Gated/private models** need a token (`HF_TOKEN`); treat it as a secret.
- **safetensors** is the default weight format: zero-copy, memory-mappable, no code execution. **Never load untrusted pickle (`.bin`) weights**: unpickling can run arbitrary code. Avoid `trust_remote_code=True` unless you've reviewed and pinned the code.

---

## transformers v5

```python
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

model_id = "Qwen/Qwen3-8B"
tok = AutoTokenizer.from_pretrained(model_id, revision=REV)
model = AutoModelForCausalLM.from_pretrained(
    model_id, revision=REV,
    dtype=torch.bfloat16,                 # don't load fp32 by accident
    device_map="auto",                    # place layers across available GPUs (accelerate)
    attn_implementation="sdpa",           # or "flash_attention_2" if installed
)
model.eval()

messages = [{"role": "user", "content": "Summarise this incident report: ..."}]
inputs = tok.apply_chat_template(messages, add_generation_prompt=True, return_tensors="pt",
                                 return_dict=True).to(model.device)
with torch.inference_mode():
    out = model.generate(**inputs, max_new_tokens=256, do_sample=False)
print(tok.decode(out[0, inputs["input_ids"].shape[1]:], skip_special_tokens=True))
```

- **Auto classes** pick the architecture from `config.json`: `AutoModel` (encoder outputs, embeddings), `AutoModelForCausalLM` (generation), `AutoModelForSequenceClassification` (classifiers, rerankers).
- **v5 changes** to know: PyTorch-only (TF/Flax classes removed), reworked weight loading, tokenizer internals rewritten around the fast (Rust) backend, some pipelines and argument names changed (`dtype=` instead of the older `torch_dtype=`). Read the v5 migration guide when porting.
- `device_map="auto"` shards big models across GPUs (and CPU/disk offload, which is very slow); for one GPU, `device_map="cuda"`.
- **Chat templates**: always format conversations with `apply_chat_template`; hand-built prompts with the wrong special tokens degrade quality silently.
- `generate()` is fine for scripts, evals and low traffic; it lacks continuous batching and paged KV cache. For serving LLMs use vLLM/SGLang/TGI ([[06 - Serving Models from Python]]).
- `pipeline(...)` gives quick task APIs (classification, NER, ASR); for services, call the model directly with explicit batching.

---

## Tokenizers

- Fast tokenizers (Rust `tokenizers` library) handle batch encoding, truncation, padding, offsets (for span highlighting) at high speed, releasing the GIL.
- **Padding side**: decoder-only models generate after the last token, so batched generation needs **left padding** (`tok.padding_side = "left"`) and a pad token (often set to EOS if missing).
- Count tokens with the **model's own tokenizer** for context-length and cost decisions; token counts differ across model families.
- Special tokens (`bos`, `eos`, chat role markers) must match the template; mismatches cause run-on or truncated generations.
- Tokenisation of large corpora is CPU work: batch it, use `datasets.map(batched=True, num_proc=N)`, keep it out of event loops ([[08 - Python AI Performance Traps]]).

---

## datasets

```python
from datasets import load_dataset
ds = load_dataset("json", data_files="evals/*.jsonl", split="train")       # Arrow, memory-mapped on disk
ds = ds.map(lambda b: tok(b["text"], truncation=True, max_length=512), batched=True, num_proc=8)
stream = load_dataset("org/huge-corpus", split="train", streaming=True)    # iterate without downloading all
```

- Backed by **Apache Arrow** files, memory-mapped: datasets larger than RAM work, and `map` results are cached on disk (fingerprinted).
- `to_pandas()`, `to_polars()`, `with_format("torch"/"numpy")` for interop ([[07 - Data Tooling for Evals]]).

---

## PEFT and LoRA

- **LoRA** trains small low-rank adapter matrices on top of frozen weights: megabytes instead of the full model, cheap to train and swap ([[15 - Post-Training and Model Adaptation]]).
- `peft`: `PeftModel.from_pretrained(base, adapter_id)` to apply; `merge_and_unload()` to bake the adapter into weights for faster single-adapter inference.
- Serving many adapters on one base model: vLLM/TGI multi-LoRA support loads adapters per request without copying the base.
- **QLoRA**: LoRA on a 4-bit quantised base (bitsandbytes NF4) to fine-tune large models on one GPU.

---

## Loading Quantised Models

| Method | Bits | Use |
|---|---|---|
| **bitsandbytes** (`BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4")`) | 8 / 4 | Quick on-the-fly quantisation for experiments, QLoRA; slower kernels than dedicated formats |
| **GPTQ / AWQ** (pre-quantised checkpoints) | 4 (mostly) | Inference with fast kernels; widely supported by vLLM |
| **FP8** checkpoints | 8 | Hopper/Blackwell GPUs; near-bf16 quality, half the memory |
| **torchao** | int8/int4/fp8 | PyTorch-native quantisation, `torch.compile` friendly |
| **GGUF** (llama.cpp) | 2–8 | CPU/Apple/edge inference |

- Weight memory ≈ params × bits / 8 (7B at 4-bit ≈ 3.5–4 GB) plus KV cache and overhead ([[14 - Quantization Decoding and VRAM Math]]). Validate quality on your eval set after quantising.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Unpinned revision** | Model changes under you | Pin commit hashes |
| **Downloading at pod startup** | Slow, flaky starts; Hub rate limits | Bake into image/volume; offline mode |
| **Loading pickle weights / `trust_remote_code`** | Arbitrary code execution | safetensors only; reviewed, pinned code |
| **Default fp32 load** | 2× memory, OOM | `dtype=torch.bfloat16` |
| **Hand-built chat prompts** | Degraded or run-on outputs | `apply_chat_template` |
| **Right padding for batched generation** | Garbage continuations | Left padding + pad token |
| **`generate()` as a production LLM server** | Low throughput, high latency | vLLM/SGLang/TGI |
| **v4 snippets on v5** | Missing classes/args | Migration guide; PyTorch-only APIs |
| **Quantisation without evals** | Silent quality loss | Measure on task evals |

---

## Interview Lines

> [!tip] Say it like this
> - "I load models from pinned Hub revisions, in safetensors, in bf16, with `device_map` placement, pre-downloaded into the image so pods start offline."
> - "transformers v5 is PyTorch-only and reworked loading and tokenizers, so older snippets need porting."
> - "Conversations always go through the model's chat template, and batched generation uses left padding."
> - "`generate()` is fine for evals; production LLM serving goes to vLLM or SGLang for continuous batching and paged KV cache."
> - "LoRA adapters are megabytes on a frozen base, so one server can host many; quantised checkpoints like AWQ or FP8 cut weight memory, verified against evals."

## Related Notes

- [[04 - PyTorch for Inference Engineers]] · [[06 - Serving Models from Python]] · [[03 - Embeddings and Similarity in Python]] · [[07 - Data Tooling for Evals]]
- [[14 - Quantization Decoding and VRAM Math]] · [[15 - Post-Training and Model Adaptation]] · [[Languages/README]]
