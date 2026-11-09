---
tags: [resume/delhivery, status/draft]
created: 2026-10-27
---
# Voice Agent (Dograh)

> [!abstract] Defence note for the `voice-agent` bullet: how the self-hosted driver-calling agent works end to end, how it was scaled, and where each resume number comes from (`₹2.5/call`, `~47%` vs Hunar, 8 workers, ~1 s latency cut).

## What it is

An in-house, self-hosted voice AI agent that calls truck drivers on non-GPS trips, asks where the vehicle is, why it is delayed and when it will arrive, and then writes the delay reason (NSL) and the new ETA back into the Movement Tracking System (MTS) automatically. It runs alongside the existing BEAT → Hunar calling path, and traffic moves to it gradually, by origin center.

## End-to-end flow

1. **Trip selection (scheduler cron, every 30 min)**
   - Pulls all no-trace (non-GPS) trips from MTS.
   - Keeps trips that are *Departed* and whose ETA is within the next 2 hours (or missing).
   - Fetches the driver's details for each trip.

2. **Routing**
   - Config-driven split between the two voice channels:
     - trips from configured **origin centers** go to Dograh;
     - trips on configured **(origin, destination) routes** go to Dograh;
     - everything else stays on BEAT/Hunar.
   - One switch can send all traffic to Dograh.
   - Changing which centers or routes go to Dograh is a config change, not a code change.

3. **Call placement (Dograh)**
   - The scheduler calls Dograh's agent API with the driver's number and the trip context: vehicle, route, trip ID and center codes.
   - Dograh dials through **Vobiz** telephony.

4. **The conversation (real-time voice pipeline)**
   - Speech-to-text: **Sarvam saaras v3** (Hindi).
   - LLM: **Gemini 3.5 Flash on Vertex AI**, pinned to `asia-south1` for low latency.
   - Text-to-speech: **Sarvam bulbul v3** (Hindi voice "Neha").
   - The agent is a node graph: Start → Main Agenda (location, moving/stopped and reason, ETA) → End. Each node has its own prompt, and the agent moves between nodes with tool calls.
   - Turn-taking, interruptions, silence handling and call disposition are handled by the pipeline.

5. **Post-call automation (Titan / MCP Gateway workflow)**
   - When the call ends, Dograh sends a webhook (with the transcript URL and trip context) to a LangGraph workflow on the MCP Gateway.
   - The workflow runs these steps:
     1. **Pre-check (code node):** decides whether the call is usable (connected, driver spoke, has timestamps).
     2. **MTS fetch:** gets the trip's planned route and center coordinates.
     3. **One LLM step:** picks the NSL code from the transcript and checks that the location the driver gave fits the route.
     4. **Apply:** writes the NSL and updates the ETA in MTS (and onward to TRMS).
   - Calls that are unanswered, unclear or don't fit the route are skipped, so no wrong update is made.

6. **Security / auth**
   - Dograh is triggered with an API key.
   - The workflow is triggered with a long-lived service token.
   - Catalog lets internal service-to-service calls (workflow resolve/validate, code-node fetch) through only when they come from inside the cluster. Public callers still go through normal authentication.

## Infrastructure and scaling

- **Hosting:** Dograh runs on one AWS EC2 instance (m8g.2xlarge, Graviton, 8 vCPU / 32 GB) in Docker. The stack is the API, UI, Postgres, Redis, MinIO (recordings) and coturn.
- **Workers:** 8 independent uvicorn workers, one per vCPU, behind nginx `least_conn`. New calls go to the least-loaded worker, and a long call can't block the others.
- **Limits and timeouts:**
  - The org concurrency limit was raised from 10 to 40.
  - The webhook timeout was raised to 120 s, so a slow workflow run doesn't trigger a retry that would apply a duplicate NSL.
- **Reliability fixes:**
  - Log rotation was added and verbose TURN logging was turned off. These fixed a full root disk caused by a 13 GB coturn log.
  - Unused Docker volumes were cleaned up.
- **Latency:** moving Gemini from the `global` endpoint to `asia-south1` removed about 1 s from every reply.

## Observability and reporting

- **Per-call report (Excel), generated from the Dograh API:**
  - connected or not, duration, outcome, furthest stage reached, goal achieved;
  - issues found automatically (hang-ups, silence, TTS and LLM errors);
  - a per-trip view of repeat calls.
- **Per-call cost model:** built from Dograh's own usage records (LLM tokens incl. cached, TTS characters, call seconds) and rate cards for Gemini, Sarvam, Vobiz and the workflow.

## Cost (per call, excluding fixed infra)

| | Cost |
|---|---|
| Per call (all calls placed) | **~₹2.5** |
| Per answered call | ~₹5.0 |
| Per completed conversation | ~₹6.0 |

Breakdown for a full conversation: LLM ~₹1.7, TTS ~₹1.9, Vobiz ~₹1.2, STT ~₹0.6, workflow ₹0.6. Infra is USD 200/month and is tracked separately.

**Compared with Hunar:** ~₹2.5 per call against ~₹4.7 per call on the earlier Hunar voice agent, a **~47% cost reduction**.

## Tech stack

Python, Dograh (Pipecat), Gemini 3.5 Flash (Vertex AI), Sarvam STT/TTS, Vobiz SIP telephony, LangGraph workflow on MCP Gateway, FastAPI/uvicorn, nginx, Docker, PostgreSQL, Redis, MinIO, AWS EC2, Kubernetes CronJob (Devtron).

## Resume pointer (drop-in for resume.tex, same format as the other Delhivery bullets)

```latex
\resumeItem{\textbf{Self-Hosted Voice AI Agent:} Built an in-house driver-calling voice agent (Dograh/Pipecat, Gemini 3.5 Flash, Sarvam STT/TTS, Vobiz SIP) that captures live location, delay reason and ETA for non-GPS trips and auto-applies NSL and ETA in MTS through a LangGraph post-call workflow. Scaled it to \textbf{8 load-balanced workers}, cut reply latency by \textbf{$\sim$1s}, and brought cost down to \textbf{₹2.5 per call}, \textbf{$\sim$47\% lower} than the earlier Hunar voice agent.}
```

It goes in the Delhivery block of `resume.tex`, after the **Workflow Automation Platform** bullet (line 114), since the post-call workflow runs on that platform.

Shorter variant (if space is tight):

```latex
\resumeItem{\textbf{Voice AI Agent:} Shipped a self-hosted driver-calling agent (Pipecat, Gemini, Sarvam, Vobiz) with an automated LangGraph NSL/ETA workflow, now serving live routes at \textbf{₹2.5/call} (\textbf{$\sim$47\% lower} than Hunar).}
```
