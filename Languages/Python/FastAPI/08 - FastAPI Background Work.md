---
tags: [languages/fastapi, status/draft]
created: 2026-10-14
---
# FastAPI Background Work

> [!abstract] What this is
> Where work that shouldn't block the response goes in a FastAPI service: **`BackgroundTasks`** (in-process, after the response, no durability), **lifespan-managed background tasks** (pollers, consumers, schedulers inside the worker), and **real task queues** (**Celery**, **arq**, **Dramatiq**, Taskiq, or managed queues like SQS/Cloud Tasks) and **durable workflow engines** (Temporal). Covers the **lifespan** pattern, retries and idempotency, the outbox for reliable enqueueing, scheduling, and resource sharing between API and workers. The decision this helps with: picking the lightest mechanism that won't lose work.

---

## Why It Matters

- `BackgroundTasks` looks like a queue but isn't: if the worker restarts (deploy, OOM, autoscale-in), pending tasks are **lost silently**. Teams discover this when "welcome emails" or "webhook deliveries" quietly go missing.
- Long CPU or I/O jobs inside API workers compete with requests for the event loop, threads and memory ([[02 - FastAPI Request Lifecycle and Sync vs Async]]).

---

## Choosing

| Mechanism | Runs | Durable | Retries | Pick when |
|---|---|---|---|---|
| **`BackgroundTasks`** | Same worker, after the response is sent | **No** | No | Best-effort, quick, losable work: cache warming, metrics, non-critical notifications |
| **Lifespan task** (`asyncio.TaskGroup` in lifespan) | Same worker, for the process lifetime | No | Your code | In-process pollers, refreshing config/caches, consuming a stream with its own offsets |
| **Task queue** (Celery, arq, Dramatiq, Taskiq) | Separate worker processes | **Yes** (broker) | Built in | Emails, webhooks, report generation, image processing, anything that must happen |
| **Managed queue** (SQS, Pub/Sub, Cloud Tasks) | Your consumers / HTTP push | Yes | Built in | Cloud-native setups; less to operate |
| **Workflow engine** (Temporal, Step Functions) | Workers executing durable workflows | Yes, with state | Per step | Multi-step processes over minutes to days, sagas, human approvals ([[09 - Durable Execution HITL and Realtime UX]]) |

---

## BackgroundTasks

```python
from fastapi import BackgroundTasks

@router.post("/signup", status_code=201)
async def signup(body: SignupIn, tasks: BackgroundTasks, svc: UserSvc) -> UserOut:
    user = await svc.create(body)                    # commit BEFORE scheduling follow-up work
    tasks.add_task(send_welcome_email, user.email)   # runs after the response, in this worker
    return user
```

- Tasks run **after the response is sent**, sequentially, in the same worker: `async def` tasks on the event loop, `def` tasks in the thread pool.
- Lost on restart; no retries; failures are only logged. The client already got a success response.
- Don't use request-scoped resources (the yield-dependency DB session) inside tasks: create what the task needs inside it.
- Heavy tasks still delay **other requests** in that worker (loop time, threads, CPU).

---

## Lifespan and In-Process Background Tasks

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.http = httpx.AsyncClient(timeout=5)
    async with asyncio.TaskGroup() as tg:              # tasks live as long as the worker process
        stop = asyncio.Event()
        tg.create_task(refresh_feature_flags(app, stop))
        yield
        stop.set()                                     # ask tasks to finish; TaskGroup awaits them
    await app.state.http.aclose()
```

- One copy **per worker process**: with 4 workers × 10 pods you get 40 pollers. For "exactly one" jobs (scheduled reports, cleanups) use a scheduler outside the API or a lock (DB advisory lock, Redis lock with TTL).
- Handle cancellation at shutdown and keep references to tasks ([[05 - asyncio Internals]]).

---

## Task Queues

```python
# tasks.py (arq: asyncio-native, Redis)
async def deliver_webhook(ctx, delivery_id: str) -> None:
    delivery = await ctx["db"].get_delivery(delivery_id)
    if delivery.sent:                                  # idempotent: safe to run twice
        return
    r = await ctx["http"].post(delivery.url, json=delivery.payload, timeout=10)
    r.raise_for_status()                               # raising → arq retries per job settings
    await ctx["db"].mark_sent(delivery_id)

class WorkerSettings:
    functions = [deliver_webhook]
    max_tries = 8
    on_startup = startup                               # create db/http clients for the worker process
```

| Queue | Model | Notes |
|---|---|---|
| **Celery** | Sync workers (prefork/threads/gevent); Redis or RabbitMQ broker | Mature, huge ecosystem (beat scheduler, canvas workflows); async support limited; config-heavy |
| **arq** | asyncio-native, Redis | Simple, fits async codebases |
| **Dramatiq** | Sync, Redis/RabbitMQ | Simpler than Celery, good defaults |
| **Taskiq** | asyncio-native, pluggable brokers | FastAPI-friendly dependency style |
| **Managed (SQS etc.)** | Pull consumers or HTTP push | No broker to run; visibility timeouts, DLQs |

### Rules that make queues reliable

- **At-least-once** delivery is the norm: tasks can run more than once (retries, worker crash after doing the work, visibility timeout expiry). Make every task **idempotent** (dedupe keys, upserts, "already done" checks) ([[LLD/Foundations/11 - Idempotency and Retries]]).
- **Pass IDs, not objects**: send `order_id`, load fresh state in the task; payloads stay small and current.
- **Retries with backoff and a cap**, then a **dead-letter queue** and alerting. Distinguish retryable from permanent errors ([[10 - Python Errors and Exception Groups]]).
- **Timeouts** on tasks and on everything they call; acknowledge only after completion (Celery `acks_late=True` + `task_reject_on_worker_lost` for crash safety).
- **Enqueue after commit**: enqueueing inside a transaction that later rolls back sends work for data that doesn't exist; enqueueing after commit can be lost if the process dies in between. For guarantees, use the **transactional outbox**: write the job/event row in the same transaction, and a relay enqueues it ([[06 - Spring Transactions]]).
- Separate queues for slow and fast work so a backlog of reports doesn't delay password-reset emails.
- Workers have their own process lifecycle: create DB engines/clients in worker startup hooks, size their DB pools, and include them in connection budgets ([[05 - FastAPI Data Access with SQLAlchemy]]).

---

## Scheduling

| Need | Option |
|---|---|
| Periodic jobs | Celery beat, arq cron jobs, APScheduler (single instance or with a lock), Kubernetes CronJobs |
| Exactly-once-ish periodic jobs across replicas | Kubernetes CronJob (`concurrencyPolicy: Forbid`) or a scheduler with distributed locking |
| Delayed jobs | Queue delay/ETA features, SQS delay, Cloud Tasks schedule time |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Critical work in `BackgroundTasks`** | Lost on deploy/restart without trace | Durable queue |
| **Heavy work in API workers** | Request latency for everyone | Separate worker processes |
| **Non-idempotent tasks** | Duplicate emails/charges on retry | Dedupe keys, idempotent writes |
| **Enqueue before commit** | Tasks for rolled-back data | Enqueue after commit or outbox |
| **Request-scoped session in a task** | Closed-session errors | New session per task |
| **Pollers in every worker** | N× duplicate scheduled jobs | Single scheduler or distributed lock |
| **No DLQ / alerting** | Poison jobs retry forever or vanish | Max retries + DLQ + alerts |
| **One queue for everything** | Slow jobs block urgent ones | Separate queues/priorities |
| **Large payloads in messages** | Broker memory pressure, stale data | Pass IDs |

---

## Interview Lines

> [!tip] Say it like this
> - "`BackgroundTasks` runs after the response in the same worker with no durability, so it's only for work I can afford to lose."
> - "Anything that must happen goes to a real queue, Celery or arq or SQS, with retries, a dead-letter queue and idempotent tasks, because delivery is at least once."
> - "I pass IDs, not objects, and enqueue after commit, or use an outbox when losing the job isn't acceptable."
> - "Lifespan tasks run once per worker process, so with many workers and pods a scheduled job runs many times unless I use a single scheduler or a lock."
> - "Multi-step processes that span minutes or days go to a workflow engine like Temporal."

## Related Notes

- [[02 - FastAPI Request Lifecycle and Sync vs Async]] · [[03 - FastAPI Dependency Injection]] · [[09 - FastAPI Production Deployment]]
- [[05 - asyncio Internals]] · [[04 - Python Threads Processes and Executors]] · [[11 - Spring HTTP Clients Resilience and Messaging]] · [[LLD/Foundations/11 - Idempotency and Retries]] · [[Languages/README]]
