---
tags: [agentic/drills, status/draft]
created: 2026-11-26
---
# AI Engineer Round Drills

> [!abstract] What this is
> Practice for the parts of an AI engineer loop that **aren't** system design: the **coding round** (LLM-flavoured tasks, usually in Python), the **debugging round** (a broken pipeline or an agent stuck in a loop), the **product / scoping round** (turn a vague complaint into measurable work) and the **take-home**. Each drill has the prompt, what's graded, a reference solution or method, and the follow-ups. Design questions live in [[Agentic/Problems/README|Practice Designs]]; rounds where you *use* an AI assistant are in [[AI Assisted Coding Rounds/README|AI Assisted Coding Rounds]].

---

## Why It Exists

- Reported AI engineer loops in 2026 run roughly: phone screen on retrieval and prompting → **take-home** (build a small LLM system and measure it) → **live coding or debugging** → system design → **product or customer round**, plus behavioural. Only system design is covered by the topic notes.
- Interview reports confirm the non-design rounds: an EPAM senior AI engineer loop included an **async programming problem** next to agent and HITL discussion and RAG evaluation metrics; prep guides describe coding tasks such as a chunker, top-k cosine similarity, a resilient model-API wrapper, JSON validation and repair, and **debugging an agent loop that never ends**.
- How to use this note: do each coding drill once **without** help in 25–30 minutes, with tests, then compare. Do the debugging and product drills **out loud**, two minutes per answer.

---

## Coding Round

### Drill 1: Chunker with overlap that respects paragraphs

- **Prompt:** "Split a document into chunks of at most `max_tokens`, with about `overlap` tokens of overlap, never cutting a paragraph unless one paragraph alone is too long. Write tests."
- **Graded on:** boundary handling, the oversized-paragraph case, no empty chunks, overlap correctness, tests written unprompted.

```python
def chunk(text: str, max_tokens: int, overlap: int, count=lambda s: len(s.split())) -> list[str]:
    assert 0 <= overlap < max_tokens
    paras = [p.strip() for p in text.split("\n\n") if p.strip()]
    units: list[str] = []
    for p in paras:                                   # split only paragraphs that can't fit alone
        if count(p) <= max_tokens:
            units.append(p)
        else:
            words = p.split()
            step = max_tokens - overlap
            units += [" ".join(words[i:i + max_tokens]) for i in range(0, len(words), step)]
    chunks, cur = [], []
    for u in units:
        if cur and count("\n\n".join(cur + [u])) > max_tokens:
            chunks.append("\n\n".join(cur))
            tail, n = [], 0                           # carry trailing units as overlap
            for prev in reversed(cur):
                if n + count(prev) > overlap:
                    break
                tail.insert(0, prev); n += count(prev)
            while tail and count("\n\n".join(tail + [u])) > max_tokens:
                tail.pop(0)                           # overlap must never push a chunk over the limit
            cur = tail
        cur.append(u)
    if cur:
        chunks.append("\n\n".join(cur))
    return chunks

def test_chunker():
    assert chunk("", 50, 10) == []
    assert chunk("a b c", 50, 10) == ["a b c"]
    long_para = " ".join(["w"] * 120)
    assert all(len(c.split()) <= 50 for c in chunk(long_para, 50, 10))
    assert all(len(c.split()) <= 50 for c in chunk("x " * 30 + "\n\n" + "y " * 30, 50, 10))
```

- **Follow-ups:** count real tokens with the model's tokenizer, not words; keep tables and code blocks whole; attach a heading path as metadata ("Refund policy > Exceptions") ([[06 - RAG Ingestion and Indexing Pipeline]]).

### Drill 2: Top-k cosine similarity, then make it fast

- **Prompt:** "Given a query embedding and N document embeddings, return the top k by cosine similarity. Now N is a million."
- **Graded on:** normalising once, vectorising, using a partial sort, and knowing when to stop doing it exactly.

```python
import numpy as np

def build_index(docs: np.ndarray) -> np.ndarray:
    return docs / np.linalg.norm(docs, axis=1, keepdims=True)     # normalise once, at write time

def top_k(index: np.ndarray, q: np.ndarray, k: int) -> list[tuple[int, float]]:
    q = q / np.linalg.norm(q)
    scores = index @ q                                             # cosine = dot product of unit vectors
    k = min(k, len(scores))
    idx = np.argpartition(-scores, k - 1)[:k]                      # O(N), not a full O(N log N) sort
    idx = idx[np.argsort(-scores[idx])]                            # sort only the k winners
    return [(int(i), float(scores[i])) for i in idx]
```

- **Sizes to say:** 1M vectors × 768 dims × 4 bytes (float32) ≈ 3 GB; one matrix–vector product is ~768M multiply-adds (~1.5 GFLOP), and it's bound by reading 3 GB of memory, so at tens of GB/s of memory bandwidth it takes on the order of 100 ms per query (*rough estimate*). Fine for a batch job.
- **Follow-ups:** batch many queries as one matrix multiply; float16 or int8 to cut memory; past a few million vectors or tight latency, an ANN index (HNSW, IVF-PQ) trading a little recall for speed; filters by tenant **before** ranking ([[08 - Vector DB Internals and Hybrid Search]]).

### Drill 3: A resilient wrapper around a flaky model API

- **Prompt:** "Wrap an LLM API call with timeouts, retries and a token budget."
- **Graded on:** retrying only retryable errors, exponential backoff with **jitter**, honouring `Retry-After`, a total deadline, and stopping on budget.

```python
import random, time

RETRYABLE = {408, 429, 500, 502, 503, 504}

class BudgetExceeded(Exception): ...

def call_llm(send, messages, *, max_attempts=4, base=0.5, cap=8.0, deadline_s=30.0,
             budget: dict, max_out=512):
    est = count_tokens(messages) + max_out
    if budget["remaining"] < est:
        raise BudgetExceeded(f"need {est}, have {budget['remaining']}")
    start = time.monotonic()
    for attempt in range(max_attempts):
        timeout = min(10.0, deadline_s - (time.monotonic() - start))
        if timeout <= 0:
            break
        try:
            resp = send(messages, max_tokens=max_out, timeout=timeout)
            budget["remaining"] -= resp.usage.total_tokens         # charge actual usage
            return resp
        except ApiError as e:
            if e.status not in RETRYABLE or attempt == max_attempts - 1:
                raise                                              # 400/401/403: retrying won't help
            delay = e.retry_after or random.uniform(0, min(cap, base * 2 ** attempt))  # full jitter
            time.sleep(delay)
        except TimeoutError:
            if attempt == max_attempts - 1:
                raise
    raise TimeoutError("deadline exceeded")
```

- **Follow-ups:** fall back to a second provider after N failures; a circuit breaker so a dead provider isn't hammered; why retries of a side-effecting tool need an idempotency key ([[10 - AI Gateway Security and Guardrails]], [[08 - Reliability]]).

### Drill 4: Validate a model's JSON and repair it

- **Prompt:** "The model must return JSON matching a schema. Parse it, validate it, and recover when it doesn't."
- **Graded on:** a schema as code, feeding the exact validation error back, a bounded number of repairs, and a safe final failure.

```python
from pydantic import BaseModel, ValidationError
import json, re

class Ticket(BaseModel):
    category: str
    priority: int
    summary: str

def extract_json(text: str) -> str:
    m = re.search(r"\{.*\}", text, re.S)                         # tolerate prose or code fences
    if not m:
        raise ValueError("no JSON object found")
    return m.group(0)

def parse_with_repair(llm, prompt: str, max_repairs: int = 2) -> Ticket:
    messages = [{"role": "user", "content": prompt}]
    for _ in range(max_repairs + 1):
        out = llm(messages)
        try:
            return Ticket.model_validate_json(extract_json(out))
        except (ValueError, ValidationError) as e:
            messages += [{"role": "assistant", "content": out},
                         {"role": "user", "content": f"Invalid: {e}. Return only corrected JSON."}]
    raise ValueError("model output failed validation after repairs")
```

- **Follow-ups:** with **strict structured outputs** the shape is guaranteed by constrained decoding, so repair loops are only for **semantic** checks (a valid priority that's still wrong) ([[14 - Quantization Decoding and VRAM Math]]); log every repair as a quality signal.

### Drill 5: An agent loop that always terminates

- **Prompt:** "Here's a tool-calling loop that sometimes never ends. Fix it."
- **Graded on:** a step limit, a token or cost budget, detecting repeated identical calls, and a useful final answer when stopped.

```python
def run_agent(llm, tools, task, max_steps=12, max_tokens=50_000):
    messages, seen, used = [{"role": "user", "content": task}], {}, 0
    for step in range(max_steps):
        resp = llm(messages, tools=tools.schemas())
        used += resp.usage.total_tokens
        if not resp.tool_calls:
            return {"status": "done", "answer": resp.text, "steps": step + 1}
        messages.append(resp.message)
        for call in resp.tool_calls:
            key = (call.name, call.arguments)                    # same tool, same args
            seen[key] = seen.get(key, 0) + 1
            if seen[key] > 2:
                result = {"error": "repeated_call",
                          "hint": "You already tried this. Change approach or answer with what you have."}
            else:
                result = tools.execute(call)                     # errors come back as observations
            messages.append({"role": "tool", "tool_call_id": call.id, "content": json.dumps(result)})
        if used > max_tokens:
            break
    final = llm(messages + [{"role": "user", "content": "Stop. Summarise what you found and what's left."}])
    return {"status": "stopped", "answer": final.text, "steps": max_steps}
```

- **Root causes to name** when debugging the original: the tool error was raised instead of returned, so the model never learned why it failed; a tool returned an empty result the model kept re-requesting; the stop condition depended on the model saying "DONE" ([[02 - Execution Loops and Control Topologies]]).

### Drill 6: Async fan-out with a concurrency limit

- **Prompt:** "Call the model for 500 documents in parallel without exceeding 20 in flight, with a per-call timeout, and don't let one failure cancel the rest."
- **Graded on:** `asyncio.Semaphore`, `wait_for` timeouts, collecting failures instead of crashing, and preserving order.

```python
import asyncio

async def map_bounded(items, fn, limit=20, timeout_s=30.0):
    sem = asyncio.Semaphore(limit)
    async def one(i, item):
        async with sem:
            try:
                return i, await asyncio.wait_for(fn(item), timeout_s), None
            except Exception as e:                               # one failure doesn't stop the batch
                return i, None, repr(e)
    results = await asyncio.gather(*(one(i, x) for i, x in enumerate(items)))
    ok = [r for _, r, err in sorted(results) if err is None]
    failed = [(i, err) for i, _, err in sorted(results) if err is not None]
    return ok, failed
```

- **Follow-ups:** limit by **tokens per minute**, not just in-flight count ([[10 - AI Gateway Security and Guardrails]]); retry the failed list once with backoff; for thousands of offline documents, the provider's **batch API** is cheaper ([[12 - LLM Cost and Latency Economics]]).

### Drill 7: Merge keyword and vector results

- **Prompt:** "You have a BM25 ranking and a vector ranking. Merge them."
- **Answer:** Reciprocal Rank Fusion, `score(d) = Σ 1 / (k + rank_i(d))` with k ≈ 60, then rerank the top candidates. Code and trade-offs in [[08 - Vector DB Internals and Hybrid Search]].

### Drill 8: Causal self-attention and a KV cache from scratch

- **Prompt (LLM-serving and ML-leaning loops):** "Implement multi-head causal self-attention. Then add a KV cache so generating the next token doesn't recompute earlier ones."
- **Graded on:** correct tensor shapes, the `1/√d_k` scale, the causal mask, a numerically stable softmax, and saying the shapes out loud before writing code. Theory in [[00 - LLM Fundamentals for Engineers]] → Inside the transformer.

```python
import numpy as np

def softmax(x, axis=-1):
    x = x - x.max(axis=axis, keepdims=True)                 # stability: subtract the row max
    e = np.exp(x)
    return e / e.sum(axis=axis, keepdims=True)

class CausalSelfAttention:
    def __init__(self, d_model: int, n_heads: int, rng=np.random.default_rng(0)):
        assert d_model % n_heads == 0
        self.h, self.dk = n_heads, d_model // n_heads
        s = 1 / np.sqrt(d_model)
        self.Wq, self.Wk, self.Wv, self.Wo = (rng.normal(0, s, (d_model, d_model)) for _ in range(4))
        self.cache_k = self.cache_v = None                   # (h, t_past, dk)

    def _split(self, x):                                     # (t, d_model) -> (h, t, dk)
        return x.reshape(x.shape[0], self.h, self.dk).transpose(1, 0, 2)

    def forward(self, x, use_cache=False):
        """x: (t_new, d_model). Prefill: t_new = prompt length. Decode: t_new = 1."""
        q, k, v = self._split(x @ self.Wq), self._split(x @ self.Wk), self._split(x @ self.Wv)
        if use_cache and self.cache_k is not None:
            k = np.concatenate([self.cache_k, k], axis=1)    # reuse past keys and values
            v = np.concatenate([self.cache_v, v], axis=1)
        if use_cache:
            self.cache_k, self.cache_v = k, v
        t_new, t_all = q.shape[1], k.shape[1]
        scores = q @ k.transpose(0, 2, 1) / np.sqrt(self.dk)        # (h, t_new, t_all)
        offset = t_all - t_new                                       # new tokens sit at the end
        mask = np.triu(np.ones((t_new, t_all), dtype=bool), k=offset + 1)
        scores = np.where(mask, -np.inf, scores)                     # hide future positions
        out = softmax(scores) @ v                                    # (h, t_new, dk)
        out = out.transpose(1, 0, 2).reshape(t_new, self.h * self.dk)
        return out @ self.Wo

def test_cache_matches_full_recompute():
    rng = np.random.default_rng(1)
    attn = CausalSelfAttention(d_model=32, n_heads=4)
    x = rng.normal(size=(6, 32))
    full = attn.forward(x)                                   # all 6 tokens at once, no cache
    attn.cache_k = attn.cache_v = None
    step = [attn.forward(x[:5], use_cache=True)[-1]]         # prefill 5 tokens
    step.append(attn.forward(x[5:6], use_cache=True)[0])     # decode the 6th with the cache
    assert np.allclose(full[4], step[0]) and np.allclose(full[5], step[1])
```

- **Shapes to narrate:** Q, K, V are `(heads, tokens, d_k)`; scores are `(heads, t_new, t_all)`, which is the `n × n` term that makes long prefill expensive; with the cache, one decode step computes scores of `(heads, 1, t_all)` only.
- **Follow-ups:** convert to **GQA** (fewer K/V heads, each shared by a group of query heads; the cache shrinks by the group factor); cache memory = `2 × layers × kv_heads × d_k × tokens × bytes` ([[13 - GPU Serving and Disaggregated KV Cache]]); why a batched version pads and masks; why FlashAttention gives the same result faster.

### Drill 9: Train and apply a BPE tokenizer

- **Asked:** Glean (implement BPE); Anthropic (longest-match tokenizer in three parts, and a tokenize/detokenize bug fix; community compilation, Jul 2026).
- **Task:** learn merges from a corpus by repeatedly merging the most frequent adjacent pair; encode a new word by applying merges in the order they were learned.

```python
import math
from collections import Counter

def train_bpe(text, num_merges):
    words = Counter(text.split())
    vocab = {tuple(w) + ("</w>",): c for w, c in words.items()}   # end-of-word marker
    merges = []
    for _ in range(num_merges):
        pairs = Counter()
        for sym, c in vocab.items():
            for a, b in zip(sym, sym[1:]):
                pairs[(a, b)] += c                                  # weighted by word count
        if not pairs:
            break
        best = min(pairs, key=lambda p: (-pairs[p], p))            # most frequent; ties → smallest pair
        merges.append(best)
        new_vocab = {}
        for sym, c in vocab.items():
            out, i = [], 0
            while i < len(sym):
                if i + 1 < len(sym) and (sym[i], sym[i + 1]) == best:
                    out.append(sym[i] + sym[i + 1]); i += 2
                else:
                    out.append(sym[i]); i += 1
            new_vocab[tuple(out)] = c
        vocab = new_vocab
    return merges

def encode_bpe(word, merges):
    rank = {m: i for i, m in enumerate(merges)}
    sym = list(word) + ["</w>"]
    while True:                                                     # apply the earliest-learned merge first
        pairs = [(rank.get((a, b), math.inf), i) for i, (a, b) in enumerate(zip(sym, sym[1:]))]
        if not pairs:
            break
        r, i = min(pairs)
        if r == math.inf:
            break
        sym[i:i + 2] = [sym[i] + sym[i + 1]]
    return sym

merges = train_bpe("low " * 5 + "lower " * 2 + "newest " * 6 + "widest " * 3, 10)
assert merges[0] == ("e", "s")
assert encode_bpe("newest", merges) == ["newest</w>"]
assert encode_bpe("lowest", merges) == ["low", "est</w>"]                  # unseen word, known pieces
assert "".join(encode_bpe("unseen", merges)).replace("</w>", "") == "unseen"   # lossless
```

- **Say while coding:** encoding must apply merges **by rank**, not greedily left to right, or training and encoding disagree; ties need a deterministic rule.
- **Follow-ups:** byte-level BPE (start from 256 bytes so nothing is ever unknown, as GPT-style tokenizers do); pre-tokenisation by regex before merging; why Indian-language text costs more tokens ([[00 - LLM Fundamentals for Engineers#Tokens]]); speed (only re-count pairs in words that contained the merged pair); the longest-match variant (greedy longest vocabulary prefix, with unknown runs merged into one `<unk>`).

### Drill 10: Merkle tree over a repository, and a diff

- **Asked:** Cursor, in six wordings; the technical screen is mostly this problem, 60 minutes, real files on disk, you write the tests (PracHub Cursor guide, Nov 2026). Also why coding agents index repos incrementally ([[03 - Coding Agent]]).
- **Task:** hash a directory tree deterministically; diff two snapshots and return added, removed and modified files, never descending into folders whose hashes match.

```python
import hashlib, os
from pathlib import Path

def hash_file(path):
    h = hashlib.sha256(b"F\0")                                  # type tag: file
    with open(path, "rb") as f:                                 # binary, chunked
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def build(path):
    """Node = (hash, children); children is {name: node} for a folder, None for a file."""
    p = Path(path)
    if p.is_symlink():                                          # never follow: cycles
        return (hashlib.sha256(b"L\0" + os.readlink(p).encode()).hexdigest(), None)
    if p.is_file():
        return (hash_file(p), None)
    children = {}
    for entry in sorted(os.scandir(p), key=lambda e: e.name):   # code-point order, not FS order
        if entry.name == ".git":
            continue
        children[entry.name] = build(entry.path)
    h = hashlib.sha256(b"D\0")                                  # type tag: folder
    for name, (child_hash, _) in children.items():
        nb = name.encode()
        h.update(len(nb).to_bytes(4, "big") + nb + bytes.fromhex(child_hash))   # length-prefixed name
    return (h.hexdigest(), children)

def leaves(node, prefix):
    _, ch = node
    if ch is None:
        return [prefix]
    return [leaf for n, c in ch.items() for leaf in leaves(c, f"{prefix}/{n}" if prefix else n)]

def diff(a, b, prefix="", stats=None):
    added, removed, modified = [], [], []
    if a[0] == b[0]:
        return added, removed, modified                         # identical below here: stop
    if stats is not None:
        stats["dirs"] += 1
    if a[1] is None or b[1] is None:
        if a[1] is None and b[1] is None:
            modified.append(prefix)
        else:                                                   # file in one, folder in the other
            removed += leaves(a, prefix); added += leaves(b, prefix)
        return added, removed, modified
    for name in sorted(set(a[1]) | set(b[1])):                  # match children by name
        path = f"{prefix}/{name}" if prefix else name
        if name not in b[1]:
            removed += leaves(a[1][name], path)
        elif name not in a[1]:
            added += leaves(b[1][name], path)
        else:
            ad, rm, md = diff(a[1][name], b[1][name], path, stats)
            added += ad; removed += rm; modified += md
    return added, removed, modified
```

- **Tests to write first** (on a `tempfile.TemporaryDirectory()` fixture with nested folders, an empty folder, an empty file, a binary file, 20 untouched folders): same tree hashes the same twice; one edit → exactly one modified path; add, remove, and file-becomes-folder cases; **the descent count stays flat** as untouched folders are added (the "never descend into equal hashes" rule).
- **Decisions to say out loud:** content-only leaf hashes (dedupe and move detection) vs content-plus-path (simpler lookup); whether empty folders count; symlinks hashed by target, never followed.
- **Follow-ups:** avoid rereading an unchanged repo (cache leaf hashes by path, size and mtime, as git does); move detection by pairing removed and added files with the same hash; incremental update recomputes only the path to the root.

### Drill 11: Streaming Markdown code-fence parser

- **Asked:** Cursor (rated hard): render a model's output while it is still arriving, keeping fence state across chunk boundaries.
- **Task:** feed chunks of any size; emit `text`, `open(lang)`, `code` and `close` events; follow the CommonMark fence rules (3+ backticks or tildes, up to 3 spaces of indent, closer uses the same character, at least as long, nothing after it).

```python
import re

FENCE = re.compile(r"^( {0,3})(`{3,}|~{3,})(.*)$")
MAYBE_FENCE = re.compile(r"^ {0,3}([`~].*)?$")    # an unfinished line that could still become a fence

class FenceParser:
    def __init__(self):
        self.buf, self.open, self.out = "", None, []
        self.mid_line = False      # part of this line already emitted → it can't be a fence

    def _line(self, line, nl):
        m = None if self.mid_line else FENCE.match(line)
        self.mid_line = False
        if self.open is None:
            if m and not (m.group(2)[0] == "`" and "`" in m.group(3)):   # backtick info can't hold backticks
                self.open = m.group(2)
                self.out.append(("open", m.group(3).strip()))
            else:
                self.out.append(("text", line + nl))
        else:
            ch, n = self.open[0], len(self.open)
            if m and m.group(2)[0] == ch and len(m.group(2)) >= n and m.group(3).strip() == "":
                self.open = None
                self.out.append(("close",))
            else:
                self.out.append(("code", line + nl))

    def feed(self, chunk):
        self.buf += chunk
        while "\n" in self.buf:
            line, self.buf = self.buf.split("\n", 1)
            self._line(line, "\n")
        # Hold only the ambiguous tail; pass everything else through with no delay.
        if self.buf and (self.mid_line or not MAYBE_FENCE.match(self.buf)):
            self.out.append(("text" if self.open is None else "code", self.buf))
            self.buf, self.mid_line = "", True
        res, self.out = self.out, []
        return res

    def close(self):
        if self.buf:
            self._line(self.buf, "")
            self.buf = ""
        res, self.out = self.out, []
        if self.open is not None:                  # input ended inside a fence: close it, and say so
            res.append(("close",))
            self.open = None
        return res
```

- **The test that catches most bugs:** parse the document whole, then split it at **every pair of offsets** into three chunks and check the events (adjacent text or code merged) are identical. The version above passes this for a document with a language tag, a fake closer with trailing text, a longer closer, and a tilde fence containing `~~~` mid-line.
- **Follow-ups:** latency (how much is held back: at most one ambiguous line); nested lists and indented fences; streaming syntax highlighting per `code` event; the same pattern for partial JSON in tool-call streams ([[09 - Durable Execution HITL and Realtime UX#Streaming]]).

### Drill 12: Merge overlapping spans and highlight whole words

- **Asked:** Harvey's coding rounds are "text under structure": highlight whole-word matches, merge overlapping spans, tag and cite phrases (PracHub, 2026). The same code builds citation highlights in a RAG answer.

```python
import re

def merge_spans(spans):
    out = []
    for s, e in sorted(spans):
        if out and s <= out[-1][1]:                    # overlapping or touching
            out[-1][1] = max(out[-1][1], e)
        else:
            out.append([s, e])
    return [tuple(x) for x in out]

def highlight(text, terms, open_tag="<b>", close_tag="</b>"):
    spans = []
    for t in terms:
        pattern = r"(?<!\w)" + re.escape(t) + r"(?!\w)"   # whole word, works for multi-word terms
        spans += [(m.start(), m.end()) for m in re.finditer(pattern, text, re.IGNORECASE)]
    out, last = [], 0
    for s, e in merge_spans(spans):
        out += [text[last:s], open_tag, text[s:e], close_tag]
        last = e
    return "".join(out + [text[last:]])

assert merge_spans([(1, 3), (2, 6), (8, 10), (10, 12), (15, 18)]) == [(1, 6), (8, 12), (15, 18)]
assert highlight("New York is not new-york or newer", ["new york", "york"]) == \
       "<b>New York</b> is not new-<b>york</b> or newer"
assert highlight("cat catalog cat.", ["cat"]) == "<b>cat</b> catalog <b>cat</b>."
```

- **Say while coding:** sort then sweep is O(n log n); decide whether touching spans merge (here yes); `\b` fails on terms that start or end with punctuation, so use lookarounds; escape the term.
- **Follow-ups:** many terms over a long document (Aho-Corasick instead of one regex per term); spans with labels that must not merge across labels; mapping character spans back to token or page offsets for citations ([[07 - Agentic RAG and Grounding Loops#Grounding, Citations and Hallucination Prevention]]).

### Drill 13: Decoding strategies from logits

- **Asked:** "implement top-k / top-p sampling" and "why can beam search beat greedy" appear in LLM interview question banks (TensorTonic and llmgenai lists, 2026); no single company report, so treat the evidence as **light**.

```python
import math, random

def softmax(logits, temperature=1.0):
    m = max(logits)                                            # subtract max for stability
    exps = [math.exp((x - m) / temperature) for x in logits]
    s = sum(exps)
    return [e / s for e in exps]

def sample(logits, temperature=1.0, top_k=None, top_p=None, rng=random):
    if temperature == 0:
        return max(range(len(logits)), key=lambda i: logits[i])       # greedy
    probs = softmax(logits, temperature)
    idx = sorted(range(len(probs)), key=lambda i: -probs[i])
    if top_k is not None:
        idx = idx[:top_k]
    if top_p is not None:                                       # smallest set whose mass ≥ top_p
        kept, cum = [], 0.0
        for i in idx:
            kept.append(i); cum += probs[i]
            if cum >= top_p:
                break
        idx = kept
    total = sum(probs[i] for i in idx)                          # renormalise over the kept set
    r, acc = rng.random() * total, 0.0
    for i in idx:
        acc += probs[i]
        if r <= acc:
            return i
    return idx[-1]

def beam_search(step_logprobs, start, beam_width, max_len, eos):
    """step_logprobs(seq) -> {token: logprob}. Best sequence by length-normalised log-probability."""
    beams, done = [([start], 0.0)], []
    for _ in range(max_len):
        cand = [(seq + [t], score + lp) for seq, score in beams for t, lp in step_logprobs(seq).items()]
        cand.sort(key=lambda x: -x[1])
        beams = []
        for seq, score in cand:
            (done if seq[-1] == eos else beams).append((seq, score))
            if len(beams) == beam_width:
                break
        if not beams:
            break
    done += beams
    return max(done, key=lambda x: x[1] / (len(x[0]) - 1))
```

- **Tests that pass:** temperature 0 always returns the arg-max; `top_k=1` is greedy; with logits `[2, 1, 0.5, -1]` (probabilities ≈ 0.61, 0.22, 0.14, 0.03), `top_p=0.8` only ever samples tokens 0 and 1; lower temperature sharpens the distribution. Beam search with width 2 finds `b → c` (0.4 × 0.9 = 0.36) where greedy takes `a` first and gets at most 0.6 × 0.4 = 0.24.
- **Say while coding:** sequence probability is the product of token probabilities, so sum log-probabilities; without length normalisation beam search prefers short sequences; beam search suits translation-like tasks with one right answer and makes open-ended text bland and repetitive, which is why chat models sample ([[00 - LLM Fundamentals for Engineers#Sampling parameters]]).
- **Follow-ups:** repetition and frequency penalties (subtract from logits of seen tokens); min-p; why temperature 0 still isn't deterministic on a serving fleet (batching changes floating-point order); speculative decoding's accept/reject rule keeps the target distribution exact ([[14 - Quantization Decoding and VRAM Math#Speculative Decoding]]).

### Drill 14: Paginated conversation history, then token-budget compaction

- **Asked:** Sierra's agent coding screen, in four wordings (Oct–Nov 2026, the most-solved ~550): load a conversation from a cursor-paginated API, then keep it under a token limit by dropping the oldest messages, cheaply enough to run after every new message. Further parts weren't reported.

```python
from collections import deque
from dataclasses import dataclass

@dataclass(frozen=True)
class Message:
    id: str
    role: str
    text: str
    tokens: int
    group: str | None = None          # a tool call and its result share a group id

def load_history(fetch_page, max_pages=10_000, retries=3):
    """Pages are oldest-first. Stop on cursor None; dedupe overlapping pages; refuse repeated cursors."""
    out, seen_ids, seen_cursors, cursor = [], set(), set(), None
    for _ in range(max_pages):
        for attempt in range(retries):
            try:
                page, nxt = fetch_page(cursor)
                break
            except TimeoutError:
                if attempt == retries - 1:
                    raise
        for m in page:
            if m.id not in seen_ids:
                seen_ids.add(m.id); out.append(m)
        if nxt is None:
            return out
        if nxt in seen_cursors:
            raise RuntimeError(f"source repeated cursor {nxt!r}")
        seen_cursors.add(nxt); cursor = nxt
    raise RuntimeError("too many pages")

class History:
    """Running total + deque: O(1) amortised per message. Pinned messages never drop,
    grouped messages drop together, the newest message is never dropped."""
    def __init__(self, limit):
        self.limit, self.pinned, self.q, self.total = limit, [], deque(), 0

    def add(self, m, pinned=False):
        (self.pinned.append if pinned else self.q.append)(m)
        self.total += m.tokens
        self._compact()

    def _compact(self):
        while self.total > self.limit and len(self.q) > 1:
            first = self.q.popleft(); self.total -= first.tokens
            while first.group and self.q and self.q[0].group == first.group and len(self.q) > 1:
                nxt = self.q.popleft(); self.total -= nxt.tokens

    def messages(self):
        return self.pinned + list(self.q)
```

- **Tests that pass:** overlapping pages produce no duplicates; a timeout is retried; a source that repeats a cursor raises instead of looping forever; with a pinned 4-token system message and five 4-token turns under a 20-token limit, the oldest turn drops and the system message stays; a tool call and its result drop together; a single message bigger than the limit is kept (never silently lose the newest message; flag it instead).
- **Say while coding:** ask page order first (oldest- or newest-first changes the assembly); running total instead of re-summing; `deque.popleft` is O(1) where `list.pop(0)` is O(n); compaction runs before each model call, not on a timer.
- **Follow-ups (reported):** summarise instead of drop, and budget the summary's own tokens (reserve space, summarise the dropped block into one message); keep tool call and result together (the `group` above); new messages arriving while paginating (fetch by id range or snapshot cursor, dedupe by id); test pagination with a fake `fetch_page` ([[04 - State Memory and Context Engineering#Keeping Context Small]]).
- **Related Sierra coding question:** split Markdown into header-aware chunks (rated hard, ~700 solves), the same skill as Drill 1 with headings as boundaries and the header path copied into each chunk.

### Drill 15: Voice activity detection from scratch

- **Asked:** Sarvam's applied and ML build round: build a VAD from scratch in about 2.5 hours, proctored, any tools except external APIs, judged on detection accuracy, code quality and proposed improvements (Blind candidate report, 2026). Endpointing is what a voice agent's latency depends on ([[09 - Durable Execution HITL and Realtime UX#Realtime Voice Agents]]).

```python
import numpy as np

def frames(x, sr, frame_ms=25, hop_ms=10):
    n, hop = int(sr * frame_ms / 1000), int(sr * hop_ms / 1000)
    idx = np.arange(0, len(x) - n + 1, hop)
    return np.stack([x[i:i + n] for i in idx]), hop

def vad(x, sr, margin_db=10.0, hang_frames=8, min_speech_frames=5, floor_alpha=0.95):
    """Energy VAD: adaptive noise floor, hangover, minimum speech duration. Returns (start_s, end_s) segments."""
    f, hop = frames(x, sr)
    energy_db = 10 * np.log10((f ** 2).mean(axis=1) + 1e-12)
    floor = np.percentile(energy_db[: max(10, len(energy_db) // 10)], 20)   # start from quiet frames
    loud = np.zeros(len(energy_db), dtype=bool)       # frames truly above the threshold
    active = np.zeros(len(energy_db), dtype=bool)     # loud frames plus hangover
    hang = 0
    for i, e in enumerate(energy_db):
        if e > floor + margin_db:
            loud[i] = active[i] = True; hang = hang_frames
        else:
            if hang > 0:
                active[i], hang = True, hang - 1                          # keep word endings
            floor = floor_alpha * floor + (1 - floor_alpha) * e            # adapt only on non-speech
    segs, start = [], None
    for i, a in enumerate(np.append(active, False)):
        if a and start is None:
            start = i
        elif not a and start is not None:
            if loud[start:i].sum() >= min_speech_frames:                   # drop clicks
                segs.append((start * hop / sr, i * hop / sr))
            start = None
    return segs
```

- **Test that passes:** 1 s of noise, a 0.5 s tone, noise, a 5 ms click, noise, a 0.3 s tone: two segments starting within 50 ms of the true onsets; the click is rejected because fewer than 5 frames were actually loud (hangover frames don't count toward the minimum).
- **Say while coding:** a fixed dB threshold fails between a quiet room and an 8 kHz phone line, so the floor adapts, but only on non-speech frames; hangover keeps soft word endings (trailing vowels in Indian languages); the trailing-silence timeout is a product decision (form filling vs open conversation).
- **Improvements to propose:** spectral features (zero-crossing rate, band energy in 300–3,400 Hz) to reject stationary hum; a small learned VAD (Silero-style) trained on labelled telephony audio; frame-level F1 and onset/offset error on a labelled set; barge-in detection while TTS is playing.

### Drill 16: Logistic regression with gradient descent, in NumPy

- **Asked:** Sarvam ML rounds ("coding gradient descent from scratch", Blind, 2026); the basic ML-depth coding question elsewhere ([[21 - ML Foundations Quick Reference]]).

```python
import numpy as np

def logistic_gd(X, y, lr=0.1, epochs=500, l2=0.0):
    """Batch gradient descent on log loss. X: (n, d), y in {0, 1}. Returns weights with bias last."""
    Xb = np.hstack([X, np.ones((len(X), 1))])
    w = np.zeros(Xb.shape[1])
    for _ in range(epochs):
        p = 1 / (1 + np.exp(-Xb @ w))                          # sigmoid
        grad = Xb.T @ (p - y) / len(y) + l2 * np.r_[w[:-1], 0]  # don't regularise the bias
        w -= lr * grad
    return w
```

- **Test that passes:** 400 points labelled by `x₁ + 2x₂ > 0` reach over 97% training accuracy (99.5% in the run), and the learned weights keep the 1:2 ratio direction.
- **Say while coding:** the gradient of log loss is `Xᵀ(p − y)/n`, the same shape as linear regression's; standardise features first or the learning rate fits one feature and not another; clip or use a stable sigmoid for large logits.
- **Follow-ups:** mini-batch SGD and why it generalises; momentum and Adam; softmax regression for multi-class; checking the gradient numerically with finite differences.

---

## Debugging Round

Answer each out loud: say what you'd **measure first**, then the likely causes in order of likelihood, then the fix.

### "Retrieval looks fine, but answers are still wrong."

1. **Check that "fine" is true for these cases:** is the right chunk actually in what reached the prompt, after reranking and truncation? Log the final prompt for failing cases.
2. **Context ordering and size:** the right chunk is there but buried among 20 others (lost in the middle); reduce k after reranking.
3. **Generation faithfulness:** the model answers from its own knowledge instead of the context; measure faithfulness per claim; tighten the instruction to answer only from sources and to abstain.
4. **Chunk content:** the chunk has the fact but missing context (a table row without its header, a "this policy" without the policy name); fix chunking or add heading context.
5. **Stale index:** the source document changed after indexing ([[06 - RAG Ingestion and Indexing Pipeline]]).
- Separate the two failure types with two metrics: retrieval (recall@k) and generation (faithfulness), measured on the same failing set ([[11 - Evals Observability and Silent Drift]]).

### "The agent keeps calling the same tool."

- Read the trace for one stuck run: the arguments, the tool result, and the model's next message.
- Usual causes: the tool **raises** instead of returning an error the model can read; it returns an empty or ambiguous result; the description doesn't say when *not* to call it; the goal is impossible with these tools; or there is no stop condition.
- Fixes: errors as observations with a hint, a repeated-call guard, a step budget, and a final "summarise and stop" turn (Drill 5).

### "Latency doubled after yesterday's release."

- Break the trace into stages: queueing, time to first token, output tokens, tool time, number of steps ([[12 - LLM Cost and Latency Economics]]).
- Common culprits: more **steps** per task (a prompt change made the agent less decisive); a longer prompt that broke **prompt caching** (something dynamic moved into the prefix); longer outputs; a slower fallback model kicking in; a tool that got slower.

### "Cost per task tripled."

- Plot tokens per task by component: input vs output vs reasoning tokens, and steps per task.
- Usual causes: context growing each step without compaction; cache misses after a prefix change; retries or hedging during an incident; a router sending more traffic to the expensive model.

### "Eval scores dropped after the model upgrade."

- Look at the **per-case diff**, not the average: which cases flipped, and do they share a cause (format, tool-calling style, refusals, verbosity)?
- Check the **judge** didn't change with it (self-preference, a different rubric interpretation).
- Pin the old model for the affected route, fix prompts for the new one, and add the flipped cases to the regression set ([[11 - Evals Observability and Silent Drift]]).

### "In a disaggregated setup, prefill latency spikes when load goes from 23 to 24 QPS."

- **Evidence:** Crusoe inference team onsite (Nov 2026), alongside "prefill vs decode" and "how does an engine handle a request".
- **First, why a sharp knee:** queueing. If the prefill pool's capacity is about 24 requests per second at the current prompt lengths, utilisation goes from ~95% to ~100%, and waiting time grows like `1 ÷ (1 − utilisation)`: small extra load, huge extra wait. Confirm by splitting TTFT into **queue time** vs **compute time** per request: if compute is flat and queue explodes, it's capacity.
- **Measure:** prefill tokens per second against the measured maximum; prompt-length distribution (did long prompts arrive?); prefix-cache hit rate (an eviction storm makes every request a full prefill); batch token budget and chunked-prefill size; **KV transfer** time and bandwidth to decode nodes; decode-side KV usage (if decode is full, prefill nodes hold finished KV and stall: backpressure); CPU tokenisation and scheduler time on the prefill host.
- **Likely causes, in order:** prefill pool at capacity; KV transfer link saturated; decode pool full and pushing back; cache hit rate dropped; a CPU-side bottleneck (tokeniser, Python scheduler loop) that GPU metrics don't show.
- **Fix:** add prefill capacity and autoscale on queued prompt tokens, not GPU utilisation; raise cache hits with prefix-aware routing; check the RDMA path; rebalance the prefill-to-decode ratio; admission control so overload returns 429 instead of unbounded queues ([[13 - GPU Serving and Disaggregated KV Cache#Disaggregated Serving]], [[07 - LLM Inference Platform]]).

### "A GPU host is slow and nobody knows why."

- Go resource by resource with **utilisation, saturation, errors**: CPU run queue and container throttling, memory and swapping, disk queue depth and await, network drops and retransmits, then GPU Xid and ECC errors; say what each reading rules out ([[19 - GPU Cluster Infrastructure and Operations#Debugging a Slow Host: a Method, Not a Guess]]).

---

## Product and Scoping Round

### "Users say the bot gives wrong answers."

A method to say out loud, in this order:

1. **Get examples.** Sample 50–100 recent conversations flagged as bad (thumbs-down, escalations, repeated questions) plus a random sample.
2. **Label the failure types** by hand: retrieval missed it, wrong source, hallucinated beyond sources, outdated content, misunderstood the question, out of scope, correct but unhelpful. Count them.
3. **Pick the biggest bucket** and define one metric for it (e.g. "answer supported by a cited source" rate).
4. **Baseline and target**: current rate on a fixed eval set built from these cases, and a target the team agrees on.
5. **One change at a time**, measured on that set, then shipped behind a flag and watched in production.
6. **Say what you won't do yet**: switching models or fine-tuning before knowing which bucket dominates.

### "A stakeholder wants a chatbot."

- Ask what decision or task the user is trying to complete, how they do it today, and what "better" means in numbers (time saved, deflected tickets, conversion).
- Often the answer is **not** a chatbot: better search, a form, an FAQ page, or a workflow with one LLM step ([[01 - Workflows vs Agents]]). Saying so, with the reason, is a strong answer.
- If it is a chatbot: the smallest version, the eval set, the escalation path to a human, and the cost per conversation.

### "Should we ship this prompt change? One metric went up, another went down."

- Look at which **cases** moved and how much each matters: a safety or correctness regression outweighs a helpfulness gain.
- Check the size of the change against run-to-run noise (run several samples; report pass rates with variance).
- If it's a trade-off, ship behind a flag to a slice of traffic and watch the online metrics.

### "Pick the first AI agent use case for this company." (Sierra, 2026)

- **Score candidate use cases** on four things: volume (how often it happens), value per case (cost of a human doing it, revenue at stake), **error cost and detectability** (a wrong refund vs a wrong FAQ answer), and **tool readiness** (are the APIs there, is the data clean). The Anthropic "should I build an agent?" checklist asks the same: complex enough, valuable enough, all capabilities doable, errors cheap or caught ([[01 - Workflows vs Agents]]).
- **Pick high volume, low error cost, tools ready**: order status, plan changes, password resets, before refunds or cancellations with money attached.
- **Define success before building:** resolution without a human, wrong-action rate (target zero for money), CSAT, cost per resolved case, and the escalation path; ship to a slice, then widen autonomy as evals and audits allow ([[01 - Customer Support Agent with Refunds]]).

### "The agent has to run inside the client's business systems." (Sierra PM and FDE rounds, 2026)

- Ask what systems hold the truth (CRM, order system, billing), which APIs exist and their rate limits, who owns them, and which actions need approval.
- Integrate through **narrow tools** with the client's own permissions model, never a shared admin key; read-only first, writes behind approvals; a sandbox copy for evals ([[03 - Tool Engineering and Action Spaces]]).
- Agree on data rules (what may leave their network, retention), the rollout plan, and who is on call when their API changes ([[12 - Multi-Tenant Agent Platform]]).

### "Design a feature that catches AI-written reviews." (Swiggy PM, Oct 2026)

- Detection alone is weak: classifiers for AI text have high false positives on non-native and formulaic writing. Combine signals: account history and verified orders, posting patterns (bursts, many restaurants, copy-paste across accounts), text-similarity clusters, and a classifier as one input.
- Act proportionately: down-rank or hold for review rather than delete; label instead of hide when unsure; measure precision on audited samples and appeals ([[11 - LLM Content Moderation Pipeline]]).

---

## Take-Home

- **Typical prompt:** "Build a question-answering service over these documents, with an evaluation set, and report metrics."
- **What strong submissions include:**
    + a working service with a small, clear structure, tests, and a one-command setup;
    + an **eval set** of 30–50 questions with expected answers or supporting chunks, including unanswerable ones;
    + **retrieval and answer metrics reported separately** (recall@k, faithfulness, abstention accuracy);
    + **one or two experiments** with numbers (chunk size A vs B, with and without a reranker) and what you concluded;
    + a short write-up: design choices, what fails and why, what you'd do with more time, cost per query.
- Disclose any AI tool use if the instructions allow it; be ready to defend every line live ([[AI Assisted Coding Rounds/README|AI Assisted Coding Rounds]]).

---

## Live Build Round

Sierra replaced its coding rounds with this in April 2026: **Plan** (a working session where you propose a product in your own domain and the interviewers push on it), **Build** (2 hours alone, any AI tools and frameworks), **Review** (demo, debate product flows, review the code's data model and abstractions, path to production, and how you used AI). A debugging round on a medium codebase with a colleague's draft PR is being piloted (Sierra blog, Apr 2026). Sarvam runs a 2.5-hour build of a voice-activity-detection component (candidate report, 2026). The signal is **scoping and judgement under time**, not volume of code.

### The playbook

1. **Plan (15–20 min):** one user, one painful job, one flow end to end. Say what you will **not** build (auth, CRUD, settings) and why; Sierra says skipping boilerplate is fine. Name the one AI-specific risk you'll handle (wrong answers, injection, cost) and how you'll show it.
2. **First 30 minutes of Build:** a walking skeleton that runs end to end with the model stubbed or minimal. Commit.
3. **Next 60 minutes:** make the core flow good. Add the smallest eval you can run (10–20 cases with expected outcomes) so the demo has a number, not an anecdote ([[11 - Evals Observability and Silent Drift]]).
4. **Last 30 minutes:** stop adding features. Fix the demo path, write a short README (what it does, what's missing, what you'd do next), commit.
5. **Pivot rule:** if something has blocked you for 15 minutes, cut it and say so in Review. Sierra names "do they pivot when they get stuck" as a signal.
6. **AI use:** let the agent write boilerplate and tests; read every line it writes on the core path; keep a short log of where it was wrong. Review asks how you used AI.

### Review: what they probe

- **Product:** why this flow, what the user sees when the model is wrong, what you'd measure in production.
- **Code:** the data model, where the model's output is validated, what's an abstraction vs a shortcut.
- **Path to production:** evals in CI, rate limits and cost, observability, rollout ([[01 - Customer Support Agent with Refunds]] for a full example).

---

## Project Deep Dive

OpenAI's onsite includes a 45–60 minute deep dive that works like a **reverse system design**: you present a past project and the interviewer probes it (Exponent OpenAI guide, 2026). Anthropic's loop has one where "half the time presenting, half answering questions" (Jul 2026 report), and one interviewer only asked **who decided the business metric**. Cursor asks you to connect the project to your career decisions (PracHub Cursor guide, Nov 2026).

### Template (10 minutes of talking, then questions)

1. **Problem and stakes (1 min):** who was hurting, how you knew, one number.
2. **Your role (30 s):** what you owned end to end, what others did. Say "I" for your decisions.
3. **Architecture (3 min):** one diagram you can draw from memory; the request path and the data that matters.
4. **Two decisions you could have made the other way (3 min):** the options, what you chose, the evidence, what it cost. This is what interviewers dig into.
5. **What broke (1.5 min):** an incident or a wrong assumption, how you found it, what changed.
6. **Result and what you'd do now (1 min):** the measured outcome; what you'd change with today's tools.

- **Pick a project** with a number, two real trade-offs, and a component you can take three levels deep. For AI roles, prefer the agent platform work: MCP servers and gateway, workflows with pause and resume, observability with Langfuse ([[Resume/Delhivery/README|Delhivery project notes]]).
- **Prepare the drill-downs:** for each box in the diagram, the next question ("why Postgres for checkpoints?", "what happens if the tool call times out after the side effect?", "how did you evaluate it?").
- **Know who chose the metric** and why it was the right one; if it wasn't yours, say who and how you influenced it.

---

## Values and Behavioural Rounds for AI Roles

### Anthropic culture round

Reported questions (community compilation, Jul 2026; Jul and Oct 2026 onsites):

- Why does AI safety matter to you personally? Where could Anthropic do better on safety?
- Would you give up personal benefit to help keep people safe? How do you feel about possibly lower pay?
- A time you held a strong view that turned out wrong; feedback that hurt; a conflict where you later realised you were wrong.
- "Name one Anthropic value you agree with, something Anthropic has done that reflects it, and how you've lived it."

- **What they seem to want** (candidate impression, Oct 2026): critical thinking, honesty, values you hold but update when shown evidence. A surface-level safety answer is reported to fail even with strong technical rounds.
- **Prepare:** read Anthropic's core views and the constitution; have one specific thing you agree with and one you'd question. Use stories from your own engineering where you chose safety or correctness over speed: a guardrail you added, an action you put behind human approval, a launch you delayed.
- **Vocabulary to use correctly:** sycophancy (agreeing with the user against the evidence), reward hacking (optimising the measured proxy instead of the goal; in agents, passing tests by editing the tests), scalable oversight (supervising systems on tasks humans can't easily check), honesty vs helpfulness conflicts, responsible scaling (capability thresholds that trigger stronger safeguards) ([[15 - Post-Training and Model Adaptation]], [[10 - AI Gateway Security and Guardrails]]).

### Meta: AI-driven impact

- Meta made "AI-driven impact" a core performance expectation from 2026 (employee memo reported Nov 2025), and changed its engineering interview signal areas to account for AI workflows (reported 2026).
- **Expect:** "Tell me about a time you used AI to change how your team worked"; "how did you verify what the AI produced?"; "what did you stop doing?".
- **Answer shape:** the workflow before, what you changed, a measured result (time saved, defects caught), how you checked AI output, and one place you chose not to use AI and why.

### Questions any AI company asks

- "How do you verify AI output?" (Harvey PM round; everywhere): tests, evals, reading the diff, checking sources; name what you don't trust it for.
- "Tell me about an AI feature that didn't work": low adoption, wrong answers, cost; what the data said and what you changed.

---

## Design Review Round

Anthropic runs this as its own system design format: you get a teammate's design doc and critique it ("Review an inference API design for scale", hard, onsite; "Review and Scale an Inference Service Design"; Sep 2025 to 2026). The Growth loop has a code version: four TypeScript PRs in 55 minutes with AI allowed, decide which to block, then fix one (Nov 2026). The skill is different from greenfield design: the interviewer is grading **judgement about what matters**, so the order of your comments is part of the answer.

### The method

1. **Read for intent first (3 minutes).** Say back what the doc is trying to achieve, for whom, and what it explicitly leaves out. Ask the scoping questions the doc skipped: traffic shape (prompt and output length distributions, not means), SLO tiers, endpoints in scope, isolation and compliance, hardware, rollback speed.
2. **Find the few issues that change the architecture.** Lead with two or three, never a list of twenty. Typical ones: the wrong planning unit, the wrong scaling signal, a missing control plane, no overload story.
3. **For each issue:** what the doc says → why it breaks (with a number or a mechanism) → the concrete change → what it costs. "Critique → improvement", never critique alone.
4. **Then the second tier**, grouped: reliability (retries, idempotency, timeouts, cancellation), rollout and rollback, isolation and quotas, observability, safety, privacy and cost.
5. **Say what's right.** Name the parts you'd keep; it shows you read it and keeps the review honest.
6. **Close with the trade-offs you'd ask the author to decide**, each with your recommended position.

- **Severity labels** keep the order explicit: **blocker** (wrong or unsafe at launch), **major** (breaks at the stated scale or in a common failure), **minor** (worth fixing, not now).
- **Numbers as method, not constants:** state formulas and say they're calibrated by load tests ("treat per-GPU token rates as quantities you'd calibrate", PracHub prompt).

### The usual flaws in an AI-infra design doc

| What the doc does | Why it's wrong | The fix |
|---|---|---|
| One blended p99 latency SLO | Streaming users feel two numbers | TTFT and inter-token latency SLOs per model tier; stream-completion rate as an SLI ([[13 - GPU Serving and Disaggregated KV Cache]]) |
| Availability counts every non-200 | Deliberate 429s look like outages | Success rate excluding intentional 4xx, with an error budget (99.9% ≈ 43 min a month) tied to rollout gates |
| Capacity = QPS × per-request latency | Ignores batching and the prefill/decode split | Size on tokens per second: prefill RPS = prefill tokens/s ÷ prompt length, decode RPS = decode tokens/s ÷ output length; take the binding one; add headroom and N−1 zones |
| Concurrency from GPU count | KV-cache memory is the real ceiling | KV bytes per token × tokens in flight vs free HBM; paged KV ([[14 - Quantization Decoding and VRAM Math#VRAM Math]]) |
| Autoscale on GPU utilisation | Lagging and saturates early | Queue wait, KV usage and tokens/s against measured capacity; warm pool; fast out, slow in |
| Static batches | Head-of-line blocking by long outputs | Continuous batching with a token budget; chunked prefill |
| Quotas in requests | One request can be 100 or 100k tokens | Token quotas plus concurrency caps, enforced at the edge and again at admission |
| Retries everywhere | Double charges, retry storms | Idempotency keys; retry only by failure timing (before first token is safe, mid-stream is not); backoff with jitter; deadline propagation |
| Cancel closes the socket | GPU keeps generating, KV slot held | Cancel reaches the engine and frees the KV slot |
| Version = weights file | Tokenizer, chat template or sampling defaults change output too | Immutable manifest pinning weights, tokenizer, template, defaults and safety policy; previous version warm ([[14 - Model Weight Distribution to a GPU Fleet]]) |
| Response cache on all requests | Wrong for temperature > 0 and across tenants | Prefix/KV cache is the valuable one; response cache only for temperature 0, short TTL, per tenant |
| Safety filter after generation | Can't stop a stream that's already sent | Input pre-filter plus streaming post-filter with early stop ([[10 - AI Gateway Security and Guardrails]]) |
| Cost not mentioned | A canary can be correct and 2× the price | $/1k tokens and tokens/s/GPU as SLIs and rollout gates |

### The PR-review variant

- **Triage before depth.** For each PR: what is it trying to change, is anything worth blocking, how risky is the blast radius. Then pick one and fix it.
- **Verify what the AI flags.** When the assistant reports a bug, walk the call chain yourself and say when it would actually trigger; a plausible but unreachable bug is a false positive (Nov 2026 report).
- **Comment like a reviewer:** the line, the failure, a reproduction or test, the suggested change, and whether it blocks.

### Practice prompts

- Review: "Our inference API autoscales on GPU utilisation, uses static batches of 32 and retries every failed request up to 3 times."
- Review: "Model rollout: upload weights to S3, restart all pods, roll back by uploading the old file."
- Review: "The RAG service caches answers by question text for 24 hours across all tenants."

---

## Forward-Deployed Engineering Rounds

Forward-deployed engineers (FDEs) build the customer's first working system inside the customer's environment, then turn what worked into something repeatable. OpenAI describes the job as closing the **deployment gap**: the distance between what a model seems able to do and what runs reliably in a customer's workflow (Jarvis, Oct 2026). Sierra hires for it as "Software Engineer, Agent", with a loop of recruiter screen, system design screen, the Plan, Build, Review live build, a debugging round and a hiring-manager round (DataInterview, Oct 2026). The rounds test the same skills as the product round above, applied to a named customer under a deadline.

### The method: outcome first

- OpenAI's line: "Start with the outcome, do what it takes to succeed, then follow with scale." In practice:
    1. **Name the business outcome and its number** (handle time, save rate, cost per return), and who on the customer side owns it.
    2. **Build the eval with the customer's experts** before building the system. In OpenAI's semiconductor engagement, FDEs wrote evals with the customer's engineers; the work reached production in 3 to 6 months (vendor-reported: ~48% time saved, 22% kernel improvement).
    3. **Ship the smallest version that moves the number**, measured on that eval.
    4. **Make it repeatable**: move from custom code to a common harness with skills, plugins and MCP connectors ([[03 - Tool Engineering and Action Spaces#Agent Skills (progressive disclosure as files)]]). In that engagement this removed ~90% of the custom code, and a second team onboarded in 1 day after the first took 2 months (vendor-reported).

### Discovery questions

- **Outcome:** what number changes if this works, how is it measured today, and what is the baseline?
- **Workflow:** who does this job now, step by step; where do they spend the time; which cases are hard?
- **Systems:** which systems hold the truth, which APIs exist, who owns them, rate limits, sandbox access (the integration case in [[#Product and Scoping Round]]).
- **Rules:** what must the agent never do; what wording is regulated; what needs a human's approval?
- **Data:** what may leave their network, retention, PII ([[12 - LLM Cost and Latency Economics#On-prem, air-gapped and sovereign deployment]]).
- **Decision:** who signs off on go-live, and on what evidence?

### Scoping the first build (Sierra cancellation example)

- Prompt: a telecom wants an agent that saves customers who call to cancel (DataInterview, Oct 2026).
    + Ask the cancellation reasons and their volumes, and which retention offers are allowed for whom.
    + **Define a "save"** with the customer: still subscribed after 30 days, not just "accepted an offer in the chat". The definition decides the bill under outcome pricing ([[12 - LLM Cost and Latency Economics#Unit economics and pricing]]).
    + **Cut scope to the top two reasons** (say price and moving house).
    + **Offers come from a deterministic rules service**, never from the model, so the agent can't invent a discount. Exact regulated wording can bypass the LLM entirely; Sierra's Agent SDK supports this.
    + **Never block a cancellation**: after a set number of asks the agent cancels or hands off. Blocking creates complaints and regulatory risk.
    + Measure save rate, CSAT and complaints together, so a rising save rate bought with pushiness shows up.

### Product gap or capability gap?

- When the eval fails, sort each failure into one of two buckets before choosing the fix.

| Gap | Signs | Fix |
|---|---|---|
| **Product gap** | The model would succeed with the right context, tool or rule; failures cluster on missing data, bad tool output or unclear policy | Better tools, retrieval, prompts, deterministic rules, workflow changes |
| **Capability gap** | The model fails with perfect context; failures are reasoning, domain knowledge or speech quality | Escalate to the model provider, fine-tune, or narrow scope to what works today |

- OpenAI's telecom voice example is the capability path: evals went from ~60% to 80% to over 90% across 6 months, working with OpenAI Research (vendor-reported). Most FDE work is product gaps; say so, and say how you'd tell them apart (rerun failures with a human-written ideal context).

### Stakeholder scenarios

- **"They want it live next week, but the eval is at 70%."** Show the failures by bucket and their business cost. Offer a narrower launch that is safe now: the two intents that pass, a shadow mode, or human approval on every action. Put the remaining gap and its date in writing.
- **"Their API team won't give write access."** Ship read-only plus drafted actions a human confirms; measure time saved; use that number to ask again.
- **"The customer's expert disagrees with the eval labels."** Their label wins on domain facts; record why, and re-label similar cases. Disagreement between experts is itself a finding (low inter-rater agreement means the task needs a clearer rule) ([[11 - Evals Observability and Silent Drift]]).
- **"Can you build this one custom feature for us?"** Ask what outcome it moves. If other customers need it too, build it in the shared harness; if not, build it as a customer-specific skill or plugin so the core stays clean.
- **"Results look great in the pilot, now scale to every region."** Re-run the eval on each region's traffic and language before widening; the pilot slice is rarely representative.

### Drill 17: Score conversation outcomes from event logs

- **Asked:** a practice coding problem for Sierra's FDE loop (DataInterview, Oct 2026): from raw event logs, label each conversation saved, churned, escalated, resolved or open, then compute save rate. The interview signal is that you **state the definition** and handle dirty logs.

```python
from collections import defaultdict
from dataclasses import dataclass

TERMINAL = {"cancelled": "churned", "offer_accepted": "saved",
            "escalated": "escalated", "resolved": "resolved"}

@dataclass(frozen=True)
class Event:
    event_id: str
    conv: str
    ts: float
    type: str          # cancel_intent | offer_made | offer_accepted | cancelled | escalated | resolved | message

def score(events, max_asks=2):
    """Outcome per conversation from a raw, possibly duplicated, out-of-order log.
    The last terminal event wins (accepting an offer then cancelling is churn).
    'pushy' flags conversations where the user asked to cancel more than max_asks
    times and the agent neither cancelled nor handed off."""
    seen, by_conv = set(), defaultdict(list)
    for e in events:
        if e.event_id in seen:
            continue                                   # retries from the source
        seen.add(e.event_id)
        by_conv[e.conv].append(e)
    out = {}
    for conv, evs in by_conv.items():
        evs.sort(key=lambda e: (e.ts, e.event_id))     # stable order on equal timestamps
        outcome, asks, intent = "open", 0, False
        for e in evs:
            if e.type == "cancel_intent":
                asks += 1; intent = True
            if e.type in TERMINAL:
                outcome = TERMINAL[e.type]
        pushy = asks > max_asks and outcome not in ("churned", "escalated")
        out[conv] = {"outcome": outcome, "cancel_intent": intent, "pushy": pushy}
    return out

def save_rate(scored):
    """Agreed definition: saved / closed conversations that showed cancel intent.
    Escalations count as not saved; open conversations are excluded."""
    closed = [s for s in scored.values()
              if s["cancel_intent"] and s["outcome"] in ("saved", "churned", "escalated")]
    return sum(s["outcome"] == "saved" for s in closed) / len(closed) if closed else 0.0
```

- Tested on a log with a duplicate event, out-of-order timestamps, an accepted offer followed by a cancel, an escalation, a non-cancel conversation and a user asking three times: save rate 0.25, one pushy flag.
- **Follow-ups:**
    + Should escalations count against the save rate? It is a business choice; state it and report both versions.
    + A save that cancels on day 20: join with billing data and use a 30-day window.
    + Millions of events: group by conversation in SQL or a stream job keyed by conversation id, with a watermark before calling a conversation closed.
    + Report save rate with a confidence interval per cancellation reason, not one global number ([[11 - Evals Observability and Silent Drift]]).

---

## Sources

- techinterview.coach, *AI Engineer Interview: Rounds, Questions and How to Prepare* (November 2026): the loop shape, coding-task examples, the thirty practice questions. https://techinterview.coach/blog/ai-engineer-interview
- LeetCode Discuss, *EPAM Systems – Senior AI Engineer Interview Experience*: agentic HITL scenario, an async programming problem, retrieval and generation metrics. https://leetcode.com/discuss/post/7876633/epam-systems-senior-ai-engineer-intervie-4l0h/
- PracHub, *Trace and Debug Agent Tool-Call Failures* (practice question). https://prachub.com/interview-questions/trace-and-debug-agent-tool-call-failures
- Sierra, *The AI-native interview* (Apr 2026): Plan, Build, Review, and the piloted debugging round. https://sierra.ai/blog/the-ai-native-interview
- Exponent, *OpenAI System Design Interview (2026 Guide)*: the technical deep dive as reverse system design. https://www.tryexponent.com/blog/openai-system-design-interview
- PracHub, *Cursor Software Engineer Interview Guide* (Nov 2026): Merkle screen, streaming fence parser, career deep dive. https://prachub.com/interview-guide/cursor-software-engineer-interview-guide
- PracHub, *Anthropic SWE: A Community-Compiled Guide to the OA, Technical, and Values Rounds* (Jul 2026): tokenizer questions and values questions. https://prachub.com/interview-questions/design-a-prompt-playground-4
- Business Insider via WinBuzzer, *Meta to grade employees on AI-driven impact starting 2026* (Feb 2026). https://winbuzzer.com/2026/02/04/meta-ties-employee-performance-reviews-ai-usage-2026-xcxwbn/
- R. Sennrich, B. Haddow, A. Birch, *Neural Machine Translation of Rare Words with Subword Units* (ACL 2016): BPE for tokenisation (Drill 9).
- PracHub, *Review an inference API design for scale* (Anthropic, SWE onsite, Sep 2025; updated Jun 2026): the design-review format, parts and strong-answer list. https://prachub.com/interview-questions/review-an-inference-api-design-for-scale
- PracHub, *Anthropic Software Engineer Interview Experience: Growth SWE Loop with a Take-home and a Slop Review* (Nov 2026): four PRs in 55 minutes with AI. https://prachub.com/interview-experiences/anthropic-software-engineer-interview-experience-growth-swe-loop-with-a-take-home-and-a-slop-review
- Colin Jarvis, OpenAI, *What FDE means at OpenAI* (22 Oct 2026): the deployment gap, the outcome-first motto, the semiconductor and telecom voice engagements (customer numbers vendor-reported).
- DataInterview, *Sierra Forward Deployed Engineer interview guide* (28 Oct 2026): role title and loop, outcome pricing, the cancellation scoping example, the outcome-scoring practice problem (Drill 17).

## Related Notes

- [[Agentic/README]] · [[Agentic/Problems/README]] · [[Agentic Problem to Solution Map]] · [[AI Assisted Coding Rounds/README]]
