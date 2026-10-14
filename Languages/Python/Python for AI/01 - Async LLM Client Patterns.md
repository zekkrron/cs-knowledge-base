---
tags: [languages/python-ai, status/draft]
created: 2026-10-14
---
# Async LLM Client Patterns

> [!abstract] What this is
> How Python services call LLM APIs (OpenAI, Anthropic, Gemini, and OpenAI-compatible servers like vLLM) safely and efficiently: **async clients** shared for the app lifetime, **bounded concurrency** and rate-limit handling, **timeouts and retries** with backoff, **streaming** tokens and parsing incremental output, **structured outputs** validated with Pydantic, tool calls, cancellation, and cost/latency telemetry. Written for the standard GIL build: LLM calls are I/O-bound, so asyncio handles thousands of concurrent calls in one process. Architecture-level LLM topics live in [[Agentic/README]]; this note is the Python mechanics.

---

## Why It Matters

- LLM calls are slow (seconds), expensive and rate-limited. A naive loop of sync calls takes hours; an unbounded `gather` gets 429s and blows the budget; a missing timeout hangs workers; a disconnected user keeps generating billable tokens.

---

## Clients

| Choice | Notes |
|---|---|
| **Official async SDK** (`openai.AsyncOpenAI`, `anthropic.AsyncAnthropic`, `google-genai`) | Built on `httpx`; typed responses; streaming helpers; built-in retries |
| **OpenAI-compatible endpoints** | Same `AsyncOpenAI` with `base_url=` for vLLM, SGLang, Ollama, gateways ([[06 - Serving Models from Python]]) |
| **Multi-provider layers** (LiteLLM, PydanticAI, LangChain) | Provider switching, fallbacks; more abstraction to debug |
| **Raw `httpx.AsyncClient`** | Full control; you own retries, parsing, streaming |

```python
from openai import AsyncOpenAI
import httpx

@asynccontextmanager
async def lifespan(app):
    app.state.llm = AsyncOpenAI(
        timeout=httpx.Timeout(60.0, connect=5.0),     # total generation can be long; connect must be short
        max_retries=2,                                # SDK retries 408/409/429/5xx with backoff
        http_client=httpx.AsyncClient(limits=httpx.Limits(max_connections=200, max_keepalive_connections=50)),
    )
    yield
    await app.state.llm.close()
```

- **One client per process**, created in the lifespan and reused: it owns the connection pool and TLS sessions. Creating a client per request leaks sockets and adds handshake latency ([[01 - FastAPI Architecture]]).
- Never use the **sync** client inside `async def` code: it blocks the event loop for the whole generation ([[02 - FastAPI Request Lifecycle and Sync vs Async]]).

---

## Bounded Concurrency and Rate Limits

```python
sem = asyncio.Semaphore(32)                         # in-flight requests per process

async def classify(client: AsyncOpenAI, text: str) -> Label:
    async with sem:
        resp = await client.responses.parse(
            model=settings.model,
            input=[{"role": "system", "content": PROMPT}, {"role": "user", "content": text}],
            text_format=Label,                       # Pydantic model → JSON schema → parsed object
        )
        return resp.output_parsed

async def classify_all(client, texts: list[str]) -> list[Label | Exception]:
    async with asyncio.TaskGroup() as tg:
        tasks = [tg.create_task(_safe(classify(client, t))) for t in texts]
    return [t.result() for t in tasks]
```

- Providers limit **requests per minute and tokens per minute** (per key/org/model). Exceeding them returns **429** with retry hints (`retry-after`, rate-limit headers).
- Bound concurrency with a **semaphore** (or a fixed pool of worker tasks over an `asyncio.Queue`); for token-based limits, a **token bucket** that charges estimated tokens per request works better than a request count.
- Limits are **per process**: 8 workers × 32 = 256 in flight. For fleet-wide limits use a shared limiter (Redis) or an LLM gateway.
- Large offline jobs: use providers' **batch APIs** (cheaper, higher limits, results in hours) instead of hammering the online endpoint.
- Wrapping each task (`_safe`) so one failure doesn't cancel the batch is a deliberate choice; for request handling, `TaskGroup`'s cancel-on-failure is usually right ([[05 - asyncio Internals]]).

---

## Timeouts and Retries

| Error | Retry? | How |
|---|---|---|
| Connection errors, timeouts | Yes | Exponential backoff + jitter, 2–4 attempts |
| 429 rate limit | Yes | Honour `retry-after`; reduce concurrency (adaptive) |
| 500/502/503/529 overloaded | Yes | Backoff; consider fallback model/provider |
| 400 invalid request, context too long | **No** | Fix input (truncate, summarise) |
| 401/403 | **No** | Alert |
| Content-policy refusal | No | Handle as a result |

- SDK retries cover transient errors; add an outer `asyncio.timeout()` for the whole operation (including retries) so a request can't exceed its latency budget.
- Streaming has two timeouts: **time to first token** and **inter-token idle** time; a stream that stalls mid-way needs its own watchdog.
- Retries multiply cost and load: retry at **one** layer, and make side-effecting tool calls idempotent ([[LLD/Foundations/11 - Idempotency and Retries]]).
- **Fallbacks**: a cheaper/secondary model or provider behind a circuit breaker for overload; record which model actually answered.

---

## Streaming

```python
async def stream_answer(client: AsyncOpenAI, messages) -> AsyncIterator[str]:
    async with client.responses.stream(model=settings.model, input=messages) as stream:
        async for event in stream:
            if event.type == "response.output_text.delta":
                yield event.delta
        final = await stream.get_final_response()          # usage, finish reason
        record_usage(final.usage)
```

- `async with` around the stream: on client disconnect or cancellation the HTTP stream is **closed**, so the provider stops generating ([[06 - FastAPI Streaming SSE and WebSockets]]).
- **Incremental parsing**: deltas split words, JSON and tool-call arguments arbitrarily. Accumulate; parse complete units (sentences, JSON objects with partial-JSON parsers) before acting on them.
- Measure **TTFT** (time to first token) and **tokens/second** separately from total latency.

---

## Structured Outputs and Tools

- **Structured outputs**: pass a JSON Schema (generated from a Pydantic model) so the provider constrains decoding; SDK helpers (`responses.parse(text_format=Model)`, `chat.completions.parse(response_format=Model)`) return parsed objects ([[04 - Pydantic v2 Deep Dive]]).
- Still **validate**: schema-constrained output can be semantically wrong, truncated (`max_output_tokens` reached), or a refusal. Check finish reasons and handle `ValidationError` with a bounded repair retry.
- Keep schemas simple: required fields, enums via `Literal`, no deep optional nesting; descriptions in `Field(description=...)` act as instructions.
- **Tool calling**: the model returns tool calls with JSON arguments; validate arguments with Pydantic before executing, enforce authorisation server-side, cap the number of tool iterations, and run independent tool calls concurrently ([[03 - Tool Engineering and Action Spaces]]).

---

## Telemetry and Cost

- Record per call: model, prompt/completion/cached tokens, latency, TTFT, retries, finish reason, cost estimate; attach to traces (OpenTelemetry GenAI semantic conventions) ([[11 - Evals Observability and Silent Drift]]).
- **Prompt caching**: keep stable prefixes (system prompt, tools, documents) first so provider caches hit; it cuts cost and TTFT ([[12 - LLM Cost and Latency Economics]]).
- Never log full prompts with user data or secrets by default; redact or sample.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Sync client in async code** | Event loop blocked for seconds per call | Async SDK clients |
| **Client per request** | Socket exhaustion, slow handshakes | One client per process (lifespan) |
| **Unbounded `gather`** | 429 storms, cost spikes | Semaphores / worker pools / token buckets |
| **No overall timeout** | Requests hang through retries | `asyncio.timeout` around the operation; stream watchdogs |
| **Retrying 400s** | Wasted cost; same failure | Retry only transient errors |
| **Stream not closed on disconnect** | Tokens billed for nobody | `async with` streams; propagate cancellation |
| **Trusting structured output blindly** | Truncated or wrong data downstream | Validate, check finish reason, bounded repair |
| **Executing tool calls without validation/auth** | Injection-driven actions | Validate args; server-side authorisation |
| **Per-process limits only** | Fleet exceeds provider limits | Shared limiter or gateway |

---

## Interview Lines

> [!tip] Say it like this
> - "LLM calls are I/O-bound, so one async client per process, created in the lifespan, handles high concurrency under the GIL; the sync client in async code would block the loop."
> - "I bound concurrency with a semaphore or token bucket sized to the provider's rate limits, and use batch APIs for offline jobs."
> - "Retries only for timeouts, 429s and 5xx, with backoff and jitter, inside an overall deadline."
> - "Streams sit in `async with` so a client disconnect closes the upstream request and stops billing."
> - "Structured outputs come from Pydantic schemas, but I still validate, check the finish reason and cap repair attempts."

## Related Notes

- [[05 - asyncio Internals]] · [[02 - FastAPI Request Lifecycle and Sync vs Async]] · [[06 - FastAPI Streaming SSE and WebSockets]] · [[04 - Pydantic v2 Deep Dive]]
- [[06 - Serving Models from Python]] · [[08 - Python AI Performance Traps]] · [[12 - LLM Cost and Latency Economics]] · [[Languages/README]]
