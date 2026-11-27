---
tags: [agentic, status/draft]
created: 2026-11-27
---
# Syllabus and Coverage

> [!abstract] What this is
> The checklist for "nothing in an AI-engineering interview surprises me". Every topic a company can ask about is a **leaf** in one of eleven domains (Axis A). Each leaf points at the section of the vault that covers it and carries a coverage mark. Axis B lists the interview formats and Axis C the company types and role tracks, so a gap can be a missing topic, a missing format or a missing company's style. Update this note after every writing batch.

> [!info] How to read the marks
> - **Coverage:** **full** (interview depth, sourced), **partial** (mentioned or shallow), **none**.
> - **Evidence:** how often the leaf shows up in reported interviews. **strong** (several companies or many solves), **light** (one or two reports), **?** (not yet researched).
> - **Action:** what closes the gap. Blank means nothing to do.

---

## Where the Structure Comes From

| Source | What it contributed |
|---|---|
| Andrew Ng, *AI Engineering Skills Map* (DeepLearning.AI, Sep 2026) | Four pillars from 10,000+ job postings and hiring-manager interviews; the reason D1 includes ML basics and D11 exists |
| Chip Huyen, *AI Engineering* (O'Reilly, 2025), table of contents | Model selection, dataset engineering, user-feedback design, the application architecture |
| CMU 11-768 *AI Agents* (Fall 2026) | Agent foundations, computer use, SFT and RL for agents, deep research, safety |
| Harvard CS2680 *Modern AI Systems* (Fall 2026) | One system from the agent loop down to the serving engine |
| Stanford CS336 *Language Modeling from Scratch* (2026) | Model-internals depth: tokenizer, architecture, GPUs, parallelism, scaling laws |
| *Agent Harness Engineering: A Survey* (OpenReview, 2026) | Seven harness layers: execution, tools, context, lifecycle, observability, verification, governance |
| *Agentic Engineering Handbook* (GitHub, Nov 2026) | 231 vendor sources in eight phases; always-on agents, skills, MCP events |
| roadmap.sh *AI Engineer* (2026) | Broad topic checklist |
| PracHub question bank, guides and *Designing Generative AI Systems* course (2026) | How interviews group questions; generation systems (image, speech, video) as their own design family |

---

## Axis A: Knowledge Domains

### D1. Model foundations

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 1.1 | Tokens, tokenizers, BPE | [[00 - LLM Fundamentals for Engineers#Tokens]], [[AI Engineer Round Drills#Drill 9: Train and apply a BPE tokenizer]] | full | light (Glean BPE; Anthropic longest-match tokenizer) | |
| 1.2 | Transformer internals: attention, GQA, RoPE, MoE | [[00 - LLM Fundamentals for Engineers#Inside the transformer (engineer depth)]], [[AI Engineer Round Drills#Drill 8: Causal self-attention and a KV cache from scratch]] | full | strong | |
| 1.3 | Sampling and decoding: greedy, temperature, top-k, top-p, beam | [[00 - LLM Fundamentals for Engineers#Sampling parameters]], [[AI Engineer Round Drills#Drill 13: Decoding strategies from logits]] | full | light | |
| 1.4 | Reasoning models, test-time compute, reasoning inside tool loops | [[00 - LLM Fundamentals for Engineers#Reasoning models]], [[00 - LLM Fundamentals for Engineers#Reasoning models inside a tool loop]], [[15 - Post-Training and Model Adaptation#Test-Time Compute Scaling]] | full | ? | |
| 1.5 | Tool-call mechanics and structured output | [[00 - LLM Fundamentals for Engineers#Tool Calling: What Actually Happens]], [[00 - LLM Fundamentals for Engineers#Structured Outputs]] | full | strong | |
| 1.6 | Context window; long context vs RAG | [[00 - LLM Fundamentals for Engineers#The Context Window]] | full | strong | |
| 1.7 | Model selection, public benchmarks, contamination | [[12 - LLM Cost and Latency Economics#Choosing a model and reading benchmarks]] | full | light (Google "choose fast or cheap models") | |
| 1.8 | Scaling laws and pre-training, concept level | [[20 - Training and RL Systems at Architect Depth]] (FLOPs ≈ 6ND, MoE pretraining) | partial | light (Meta MoE pretraining pipeline) | |
| 1.9 | ML basics: bias and variance, classification metrics, calibration, error analysis | [[21 - ML Foundations Quick Reference]] | full | strong for MLE and applied-scientist roles (Google, Amazon, Microsoft) | |
| 1.10 | Embeddings: how they are trained, similarity | [[00 - LLM Fundamentals for Engineers#Embeddings in One Section]], [[08 - Vector DB Internals and Hybrid Search#Fine-Tuning the Embedder and Reranker]] | full | strong | |

### D2. Prompt and context engineering

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 2.1 | Prompt design: system prompts, few-shot, CoT | [[00 - LLM Fundamentals for Engineers#Prompting Basics That Matter]] | full | strong | |
| 2.2 | Prompt versioning, registry, playground | [[13 - Prompt Playground]] | full | strong (Anthropic 6 variants, OpenAI) | |
| 2.3 | Context assembly and compaction | [[04 - State Memory and Context Engineering#Keeping Context Small]] | full | strong | |
| 2.4 | Work across many context windows | [[04 - State Memory and Context Engineering#Long-Running Agents: Working Across Many Context Windows]] | full | strong | |
| 2.5 | Skills, AGENTS.md, progressive disclosure | [[03 - Tool Engineering and Action Spaces#Agent Skills (progressive disclosure as files)]] | partial | none found in interview banks | |
| 2.6 | Prompt caching | [[12 - LLM Cost and Latency Economics#Prompt caching in practice]] | full | strong | |
| 2.7 | Automatic prompt optimisation | [[15 - Post-Training and Model Adaptation#Automatic Prompt Optimisation: DSPy and GEPA]] | full | light | |

### D3. Grounding and knowledge

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 3.1 | Parsing documents | [[06 - RAG Ingestion and Indexing Pipeline#Parsing]], [[17 - Multimodal Systems#Documents: Three Ways to Read a Page]] | full | strong | |
| 3.2 | Chunking | [[06 - RAG Ingestion and Indexing Pipeline#Chunking]] | full | strong | |
| 3.3 | Choosing an embedding model | [[06 - RAG Ingestion and Indexing Pipeline#Choosing an Embedding Model]] | full | strong | |
| 3.4 | ANN indexes and memory arithmetic | [[08 - Vector DB Internals and Hybrid Search#ANN Index Families]] | full | strong | |
| 3.5 | Hybrid search, RRF, reranking | [[08 - Vector DB Internals and Hybrid Search#Hybrid Search]], [[08 - Vector DB Internals and Hybrid Search#Retrieval vs Reranking]] | full | strong | |
| 3.6 | Query understanding and source routing | [[07 - Agentic RAG and Grounding Loops#Query Understanding]], [[07 - Agentic RAG and Grounding Loops#Routing to Sources]] | full | strong | |
| 3.7 | Corrective retrieval loops (CRAG, Self-RAG) | [[07 - Agentic RAG and Grounding Loops#Corrective and Self-Reflective RAG]] | full | light | |
| 3.8 | Citations, verification, abstention | [[07 - Agentic RAG and Grounding Loops#Grounding, Citations and Hallucination Prevention]] | full | strong | |
| 3.9 | Knowledge graphs and GraphRAG: data models, extraction, entity resolution, cheaper variants, upkeep, evals | [[18 - Knowledge Graphs and GraphRAG]] | full | light (PracHub guide, one reported question) | |
| 3.10 | Structured data: text-to-SQL, semantic layer | [[06 - Text-to-SQL Analytics Assistant]] | full | strong | |
| 3.11 | Freshness, deletes, ACLs in retrieval | [[06 - RAG Ingestion and Indexing Pipeline#Freshness: Updates and Deletes]], [[06 - RAG Ingestion and Indexing Pipeline#Metadata and Access Control]] | full | strong | |
| 3.12 | One question over a whole corpus (map-reduce under rate limits) | [[10 - Batch Document Extraction]] (V4) | full | light (Harvey, Oct 2026) | |

### D4. Agent architecture and harness

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 4.1 | Workflows vs agents | [[01 - Workflows vs Agents]] | full | strong | |
| 4.2 | Loops and planning (ReAct, plan-and-execute, ReWOO, LLMCompiler, Tree of Thoughts, Reflexion) | [[02 - Execution Loops and Control Topologies]] | full | strong | |
| 4.3 | Tool design and errors as observations | [[03 - Tool Engineering and Action Spaces#Schema Engineering]], [[03 - Tool Engineering and Action Spaces#Errors as Observations]] | full | strong | |
| 4.4 | MCP, A2A, payment protocols | [[03 - Tool Engineering and Action Spaces#Model Context Protocol (MCP)]] | full | strong | |
| 4.5 | Delegated and workload identity | [[03 - Tool Engineering and Action Spaces#Permissions and Identity]] | full | strong | |
| 4.6 | Long-term memory | [[04 - State Memory and Context Engineering#Long-Term Memory]] | full | strong | |
| 4.7 | Multi-agent topologies and fan-out | [[05 - Multi-Agent Topologies and Concurrency]] | full | strong | |
| 4.8 | Durable execution and human-in-the-loop | [[09 - Durable Execution HITL and Realtime UX]] | full | strong | |
| 4.9 | Always-on, event-driven agents: triggers, timers, inbox, quiet runs | [[09 - Durable Execution HITL and Realtime UX#Always-On Agents]] | full | none found in interview banks (vendor guidance only) | |
| 4.10 | The harness as a whole (seven layers) | [[02 - Execution Loops and Control Topologies#The Harness as a Whole]] | full | light (no direct interview question found) | |
| 4.11 | Coding agents, repo indexing, Merkle diff, file-event batching, streaming output parsing | [[03 - Coding Agent]] (T11), [[AI Engineer Round Drills#Drill 10: Merkle tree over a repository, and a diff]], [[AI Engineer Round Drills#Drill 11: Streaming Markdown code-fence parser]] | full | strong (Cursor screen) | |
| 4.12 | Computer-use and browser agents | [[16 - Computer Use and Browser Agents]] | full | light | |
| 4.13 | Voice agents | [[09 - Durable Execution HITL and Realtime UX#Realtime Voice Agents]], [[04 - Voice Booking Agent]] | full | strong | |
| 4.14 | Deep research agents | [[05 - Deep Research Agent]] | full | strong | |
| 4.15 | Rule-compliance and review agents (contract against many rules) | [[10 - Batch Document Extraction]] (V5) | full | light (Harvey, Sep–Oct 2026) | |

### D5. Evaluation and quality

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 5.1 | Tracing AI systems | [[11 - Evals Observability and Silent Drift#Observability: Tracing AI Systems]] | full | strong | |
| 5.2 | Three-tier evals and RAG metrics | [[11 - Evals Observability and Silent Drift#Three-Tier Evaluation]] | full | strong | |
| 5.3 | Multi-turn agent evals: simulated users, pass^k | [[11 - Evals Observability and Silent Drift#Evaluating Multi-Turn Agents: Simulated Users, State Grading and pass^k]] | full | strong | |
| 5.4 | LLM-as-a-judge | [[11 - Evals Observability and Silent Drift#LLM-as-a-Judge]] | full | strong | |
| 5.5 | Designing agent evals: tasks, graders, maintenance | [[11 - Evals Observability and Silent Drift#Designing Agent Evals You Can Trust]] | full | strong | |
| 5.6 | Eval statistics: standard errors, clustering, paired tests, intervals, sample size | [[11 - Evals Observability and Silent Drift#Eval Statistics: Error Bars on Every Score]] | full | strong (Anthropic paper; interview courses) | |
| 5.7 | Hallucination detection without references | [[11 - Evals Observability and Silent Drift#Hallucination Detection Without a Reference]] | full | light | |
| 5.8 | Online metrics, A/B tests, the launch decision | [[11 - Evals Observability and Silent Drift#Product Metrics and the Launch Decision]] | full | strong | |
| 5.9 | Error analysis and the data flywheel | [[15 - Post-Training and Model Adaptation#The Data Flywheel: How the System Gets Better Over Time]], [[21 - ML Foundations Quick Reference#Generalisation]] | full | light (Microsoft eval-and-improvement loop) | |
| 5.10 | Benchmarks, contamination, eval awareness | [[12 - LLM Cost and Latency Economics#Choosing a model and reading benchmarks]], [[15 - Post-Training and Model Adaptation#Dataset Engineering and Synthetic Data]] | full | light | |

### D6. Safety, security and governance

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 6.1 | Prompt injection and exfiltration | [[10 - AI Gateway Security and Guardrails#Prompt Injection]] | full | strong | |
| 6.2 | Sandboxing model-generated code | [[10 - AI Gateway Security and Guardrails#Sandboxing Model-Generated Code]] | full | strong | |
| 6.3 | AI gateway and token rate limits | [[10 - AI Gateway Security and Guardrails#The AI Gateway]], [[10 - AI Gateway Security and Guardrails#Token-Aware Rate Limiting]] | full | strong | |
| 6.4 | Red-teaming and safety eval pipelines | [[10 - AI Gateway Security and Guardrails#Red-Teaming and Safety Eval Pipelines]] | full | light | |
| 6.5 | OWASP Top 10 for agentic apps | [[10 - AI Gateway Security and Guardrails#OWASP Top 10 for Agentic Applications]] | full | light | |
| 6.6 | PII, data governance, regulation (DPDP, EU AI Act) | [[10 - AI Gateway Security and Guardrails#PII and Data Governance]], [[10 - AI Gateway Security and Guardrails#Regulation to name in a design round]] | full | light | |
| 6.7 | Alignment vocabulary: sycophancy, reward hacking, oversight, constitutions | [[AI Engineer Round Drills#Values and Behavioural Rounds for AI Roles]], [[15 - Post-Training and Model Adaptation#RL environments and rewards for agents]] | full | light (Anthropic values round) | |

### D7. Production operations

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 7.1 | Production metrics and alerts | [[11 - Evals Observability and Silent Drift#Production metrics to dashboard and alert on]] | full | strong | |
| 7.2 | Shipping prompt and model changes | [[11 - Evals Observability and Silent Drift#Shipping Prompt and Model Changes]] | full | strong | |
| 7.3 | Silent drift | [[11 - Evals Observability and Silent Drift#Silent Drift]] | full | strong | |
| 7.4 | Incident response, quality SLOs, error budgets, runbooks | [[11 - Evals Observability and Silent Drift#Quality SLOs and Incident Response]] | full | light | |
| 7.5 | Cost model and levers | [[12 - LLM Cost and Latency Economics#The Levers]] | full | strong | |
| 7.6 | Latency economics | [[12 - LLM Cost and Latency Economics#Latency Economics]] | full | strong | |
| 7.7 | API vs self-hosting | [[12 - LLM Cost and Latency Economics#API vs Self-Hosting]] | full | strong | |
| 7.8 | On-prem, air-gapped, sovereign deployment | [[12 - LLM Cost and Latency Economics#On-prem, air-gapped and sovereign deployment]] | full | ? | |
| 7.9 | Multi-tenancy | [[12 - Multi-Tenant Agent Platform]], [[02 - Multi-Tenant Document RAG]] | full | strong | |
| 7.10 | Semantic caching | [[11 - Evals Observability and Silent Drift#Semantic Caching Risks]] | full | light | |

### D8. Serving and infrastructure

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 8.1 | KV cache and its size | [[13 - GPU Serving and Disaggregated KV Cache#The KV Cache]] | full | strong | |
| 8.2 | Prefill vs decode, continuous batching, chunked prefill | [[13 - GPU Serving and Disaggregated KV Cache#Prefill vs Decode]], [[13 - GPU Serving and Disaggregated KV Cache#Batching and Scheduling]] | full | strong | |
| 8.3 | PagedAttention and prefix caching | [[13 - GPU Serving and Disaggregated KV Cache#PagedAttention and RadixAttention]] | full | strong | |
| 8.4 | Disaggregated serving and tiered KV | [[13 - GPU Serving and Disaggregated KV Cache#Disaggregated Serving]] | full | strong | |
| 8.5 | Prefix-aware routing | [[13 - GPU Serving and Disaggregated KV Cache#Prefix-Aware ("Sticky") Routing]] | full | strong | |
| 8.6 | Model parallelism and its communication cost | [[13 - GPU Serving and Disaggregated KV Cache#Model Parallelism]], [[14 - Quantization Decoding and VRAM Math#Sharding a matmul across GPUs]] | full | strong (Anthropic sharded matmul) | |
| 8.7 | Roofline, FLOPs, arithmetic intensity | [[14 - Quantization Decoding and VRAM Math#The roofline model]] | full | strong (Anthropic) | |
| 8.8 | Quantization | [[14 - Quantization Decoding and VRAM Math#Precision Formats]] | full | strong | |
| 8.9 | Speculative and constrained decoding | [[14 - Quantization Decoding and VRAM Math#Speculative Decoding]], [[14 - Quantization Decoding and VRAM Math#Constrained Decoding]] | full | strong | |
| 8.10 | VRAM and cost arithmetic | [[14 - Quantization Decoding and VRAM Math#VRAM Math]] | full | strong | |
| 8.11 | Fleet operations: registry, rollouts, cold starts | [[13 - GPU Serving and Disaggregated KV Cache#Running a Model Fleet: Registry, Rollouts and Cold Starts]] | full | strong | |
| 8.12 | Distributing model weights to a GPU fleet | [[14 - Model Weight Distribution to a GPU Fleet]] | full | strong (Anthropic 5 variants) | |
| 8.13 | GPU job scheduling: gang scheduling, bin-packing, fair share, preemption | [[15 - GPU Job Scheduler for Video Generation]] | full | strong (OpenAI, Anthropic) | |
| 8.14 | GPU hardware: HBM, NVLink, interconnect | [[19 - GPU Cluster Infrastructure and Operations#Inside a GPU Node and Between Nodes]] | full | strong (Together AI, Crusoe) | |
| 8.15 | GPU cluster scheduling, node health, virtualization | [[19 - GPU Cluster Infrastructure and Operations]] | full | strong (Together AI pod scheduler ~1,200 solves; Crusoe VM fleet; Baseten bad-pod pipeline) | |
| 8.16 | Debugging a slow host and an inference latency knee | [[19 - GPU Cluster Infrastructure and Operations#Debugging a Slow Host: a Method, Not a Guess]], [[AI Engineer Round Drills#Debugging Round]] | full | strong (Together AI four troubleshooting questions; Crusoe prefill spike) | |
| 8.17 | Training parallelism, FlashAttention, vocab-parallel logits | [[20 - Training and RL Systems at Architect Depth]] | full | strong for MLE (Meta, Google, Amazon, Microsoft) | |

### D9. Model adaptation and training

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 9.1 | Prompt vs RAG vs fine-tune | [[15 - Post-Training and Model Adaptation#Prompting vs RAG vs Fine-Tuning]] | full | strong | |
| 9.2 | SFT, LoRA, QLoRA | [[15 - Post-Training and Model Adaptation#Parameter-Efficient Fine-Tuning: LoRA and QLoRA]] | full | strong | |
| 9.3 | Preference tuning (RLHF, DPO) | [[15 - Post-Training and Model Adaptation#Preference Tuning: RLHF and DPO]] | full | strong | |
| 9.4 | RL with verifiable rewards, GRPO | [[15 - Post-Training and Model Adaptation#Reinforcement Learning with Verifiable Rewards (RLVR)]] | full | light | |
| 9.5 | RL environments and reward design for agents | [[15 - Post-Training and Model Adaptation#RL environments and rewards for agents]] | full | light (Harvey MLE, Meta) | |
| 9.6 | Distilling agent trajectories | [[15 - Post-Training and Model Adaptation#Distilling Agent Trajectories]] | full | light | |
| 9.7 | Multi-LoRA serving | [[15 - Post-Training and Model Adaptation#Serving Many Adapters: Multi-LoRA]] | full | light | |
| 9.8 | Dataset engineering and synthetic data | [[15 - Post-Training and Model Adaptation#Dataset Engineering and Synthetic Data]] | full | light (Microsoft, Amazon, Meta training-data prompts) | |
| 9.10 | Asynchronous RL post-training systems | [[20 - Training and RL Systems at Architect Depth#Asynchronous RL Post-Training Systems]] | full | light (Meta hard; Google GRPO pipeline) | |
| 9.11 | Decision models: calibrated scoring in one pass | [[15 - Post-Training and Model Adaptation#Decision Models: Calibrated Scores in One Pass]] | full | light (Sarvam Prism; vendor docs) | |
| 9.9 | A fine-tuning platform as a system | none | none | light (OpenAI, Dec 2025) | Problem 16 (optional) |

### D10. Modalities and generation systems

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 10.1 | Vision and documents | [[17 - Multimodal Systems]] | full | strong | |
| 10.2 | Speech in and out | [[17 - Multimodal Systems#Audio]], [[04 - Voice Booking Agent]] | full | strong | |
| 10.3 | Video understanding | [[17 - Multimodal Systems#Video]] | full | light | |
| 10.4 | Generation systems: text-to-image, text-to-video, TTS serving | [[17 - Multimodal Systems#Generation Systems: Images, Video and Speech Out]], [[15 - GPU Job Scheduler for Video Generation]] | full | medium (OpenAI Sora orchestration; PracHub course sections; Microsoft video VLM) | |
| 10.5 | Cross-lingual and code-mixed retrieval (Indian languages) | [[06 - RAG Ingestion and Indexing Pipeline#Multilingual and Code-Mixed Corpora]] | full | light (Sarvam representative set) | |
| 10.6 | Voice activity detection and endpointing | [[AI Engineer Round Drills#Drill 15: Voice activity detection from scratch]], [[04 - Voice Booking Agent]] | full | light (Sarvam build round) | |

### D11. Product and applied judgement

| # | Leaf | Where | Coverage | Evidence | Action |
|---|---|---|---|---|---|
| 11.1 | Use-case selection and scoping | [[AI Engineer Round Drills#Product and Scoping Round]] | full | medium (Sierra PM: first agent use case) | |
| 11.2 | AI UX: streaming, progress, citations, feedback design, generative UI | [[09 - Durable Execution HITL and Realtime UX#AI UX: What the User Sees]], [[09 - Durable Execution HITL and Realtime UX#Streaming]], [[13 - Prompt Playground]] | full | light (no dedicated interview question found) | |
| 11.3 | Unit economics and pricing | [[12 - LLM Cost and Latency Economics#Unit economics and pricing]], [[12 - LLM Cost and Latency Economics#The Cost Model]], [[16 - AI Gateway]] | full | light | |
| 11.4 | Build vs buy, vendor choice | [[12 - LLM Cost and Latency Economics#API vs Self-Hosting]], [[12 - LLM Cost and Latency Economics#Choosing a model and reading benchmarks]] | full | light | |
| 11.5 | Customer discovery and delivery (forward-deployed roles) | [[AI Engineer Round Drills#Forward-Deployed Engineering Rounds]], [[AI Engineer Round Drills#Product and Scoping Round]] (integration case), [[AI Engineer Round Drills#Live Build Round]] | full | medium (Sierra PM and FDE; Sarvam FDE) | |
| 11.6 | Launch metrics | [[11 - Evals Observability and Silent Drift#Product Metrics and the Launch Decision]] | full | strong | |

---

## Axis B: Interview Formats

| # | Format | Example | Where | Coverage | Action |
|---|---|---|---|---|---|
| F1 | Concept and depth Q&A | "Explain the KV cache" | Interview Lines in every topic | full | |
| F2 | Product system design | Support agent with refunds | [[Agentic/Problems/README\|Problems]] 01–06, 08–12 | full | |
| F3 | Infra system design | Weight distribution, GPU scheduler, playground | [[07 - LLM Inference Platform]], [[13 - Prompt Playground]], [[14 - Model Weight Distribution to a GPU Fleet]], [[15 - GPU Job Scheduler for Video Generation]] | full | |
| F4 | LLM-flavoured coding | BPE, top-p, Merkle diff, streaming parser | [[AI Engineer Round Drills#Coding Round]] (Drills 1–13) | full | |
| F5 | Debugging and diagnosis | "Cost per task tripled"; prefill spike at 23→24 QPS; slow Linux host | [[AI Engineer Round Drills#Debugging Round]], [[19 - GPU Cluster Infrastructure and Operations#Debugging a Slow Host: a Method, Not a Guess]] | full | |
| F6 | Design review of a flawed doc | Anthropic: "review this design" questions; Growth loop PR review | [[AI Engineer Round Drills#Design Review Round]] | full | |
| F7 | Estimation | VRAM, FLOPs, cost per task | [[14 - Quantization Decoding and VRAM Math#VRAM Math]], [[14 - Quantization Decoding and VRAM Math#Roofline and Sharding Arithmetic]], Estimates in each Problem | full | |
| F8 | Live build, take-home, AI-assisted coding | Sierra 2-hour build; Sarvam VAD build | [[AI Engineer Round Drills#Take-Home]], [[AI Engineer Round Drills#Live Build Round]], [[AI Assisted Coding Rounds/README\|AI Assisted Coding Rounds]] | full | |
| F9 | Product, scoping or case round | "A stakeholder wants a chatbot"; first agent use case; AI-written reviews | [[AI Engineer Round Drills#Product and Scoping Round]] | full | |
| F10 | Behavioural, project deep dive, values | Meta "AI-driven impact"; Anthropic safety culture; Cursor career deep dive | [[AI Engineer Round Drills#Project Deep Dive]], [[AI Engineer Round Drills#Values and Behavioural Rounds for AI Roles]] | full | |

---

## Axis C: Who Asks

| Company type | Companies to research | Typical emphasis | Researched |
|---|---|---|---|
| Frontier labs | Anthropic, OpenAI, Google DeepMind | Infra design (F3), estimation (F7), design review (F6), values (F10) | Anthropic, OpenAI (Nov 2026) |
| AI-native product companies | Harvey, Sierra, Cursor, Perplexity, Glean, Decagon | Product design (F2), coding on real code (F4), live builds (F8) | All six (Nov 2026) |
| Big tech AI teams | Meta, Google, Amazon, Microsoft | ML system design, AI-enabled coding, behavioural | All four ML system design banks (Nov 2026) |
| Inference providers | Crusoe, Together, Fireworks, Baseten | Serving (D8), debugging (F5), GPU clusters | All four (Nov 2026) |
| Indian companies | Sarvam, Flipkart, Swiggy, PhonePe, Razorpay | Indian languages, cost, voice, product design | All five (Nov 2026) |

| Role track | Domains that need full depth | Formats to drill |
|---|---|---|
| Applied or AI engineer | D2–D7, D11 | F2, F4, F5, F8, F9 |
| SWE on an AI product | D2–D5, D7 | F2, F4, F8 |
| Inference or infra engineer | D1, D7, D8 | F3, F5, F7 |
| Forward-deployed engineer | D2–D5, D11 | F2, F8, F9, F10 |

---

## Research Method

1. **Leaves first.** The leaf list above is the union of the sources in [[#Where the Structure Comes From]]. Add a leaf only when a source or an interview report names it.
2. **Evidence by company type.** For each company in Axis C, read the same four sources: PracHub company page, its interview guide, its interview experiences, and the company's engineering blog. Tag each question with domain, format and role.
3. **Score each leaf** on evidence and coverage. Coverage is checked by reading the section, not by grep.
4. **Write as you go.** When a domain's research is done, write its gaps in the same session, then update the marks here.

## Research Findings by Company Type (Nov 2026)

| Company type | What their banks and reports actually ask | What it changed in the notes |
|---|---|---|
| **Frontier labs** (Anthropic, OpenAI) | Infra design: prompt playground (6 variants), weight distribution (5), batched inference, GPU schedulers, text-to-video orchestration; design-doc review; roofline and sharding arithmetic; tokenizer and LRU coding; values round | Problems 13–15, Design Review Round, roofline section, values round |
| **AI-native product** (Harvey, Sierra, Cursor, Perplexity, Glean, Decagon) | Practical coding tied to the product: Merkle repo diff, streaming fence parser, conversation compaction to a token budget, header-aware Markdown chunking, BPE, span merging; AI gateway (Decagon ×2); rule-compliance agents (Harvey); live build rounds (Sierra); project deep dives | Drills 9–14, Problem 16, Problem 10 V4–V5, Live Build Round |
| **Big tech AI teams** (Meta, Google, Amazon, Microsoft) | Agents with HITL and refunds, safe agentic systems, coding assistants and code-review agents; RL post-training and MoE pretraining systems; training parallelism; long-sequence attention; vocab-sharded LM heads; classic ML and statistics breadth for MLE and applied-scientist roles; plenty of classic recommender and fraud design | Topics 20 and 21; classic recommender design left out of scope |
| **Inference providers** (Crusoe, Together AI, Fireworks, Baseten) | Prefill vs decode basics; prefill latency knee in a disaggregated setup; autoscaling inference; GPU-aware pod scheduling and node draining; Linux CPU, disk, network and logging diagnosis; GPU virtualization; chunked parallel downloads with integrity checks | Topic 19, two debugging cases |
| **Indian companies** (Sarvam, Flipkart, Swiggy, PhonePe, Razorpay) | Product companies' SDE loops are classic DSA, HLD and LLD with almost no AI questions in PracHub's banks; AI shows up in PM rounds (Swiggy: catching AI-written reviews). Sarvam runs proof-of-work builds (VAD in 2.5 hours), from-scratch maths (gradient descent, attention), Indic tokenization, cross-lingual RAG, voice pipelines, on-prem delivery | Drills 15–16, multilingual retrieval section, product cases |

- **Out of scope, deliberately:** classic recommender, ads, fraud and spam ML system design (heavy at Meta, Google, Amazon); classic HLD and DSA (covered elsewhere in the vault).
- **Leaves still partial:** 2.5 skills (kept as is; covered in [[03 - Tool Engineering and Action Spaces#Agent Skills (progressive disclosure as files)]]). 4.9, 4.10, 11.2 and 11.3 are now written from vendor guidance and books, since no interview bank asked them directly. 11.5 is written from OpenAI and Sierra FDE material.

## Change Log

| Date | Change |
|---|---|
| 2026-11-27 | Created; first coverage pass from the headings and a term search of every Agentic note |
| 2026-11-27 | Topic 11: eval statistics (5.6), hallucination detection (5.7), quality SLOs and incident response (7.4) |
| 2026-11-27 | Topic 14: roofline (8.7) and sharded-matmul and tensor-parallel communication arithmetic (8.6) |
| 2026-11-27 | Problem 13 Prompt Playground (2.2, F3); Problem 14 Model Weight Distribution (8.12, F3) |
| 2026-11-27 | Drills: Design Review Round (F6), with the AI-infra flaw table and the PR-review variant |
| 2026-11-27 | Structured research pass across all five company types; new: Topic 19 GPU Cluster Infrastructure (8.14–8.16), Topic 20 Training and RL Systems (8.17, 9.10, 1.8), Topic 21 ML Foundations (1.9), Problem 16 AI Gateway, Drills 14–16, debugging cases (F5), product cases (11.1, 11.5, F9), Topic 06 multilingual retrieval (10.5), Topic 12 model selection (1.7, 5.10), Topic 15 dataset engineering (9.8) and decision models (9.11), Topic 17 generation systems (10.4) |
| 2026-11-27 | Problem 15 GPU Job Scheduler (8.13); Drills 9–13 tested (1.1, 1.3, 4.11); Live Build, Project Deep Dive, Values rounds (F8, F10, 6.7); Topic 18 Knowledge Graphs (3.9); Topic 00 reasoning in tool loops (1.4); Topic 02 LLMCompiler and ToT (4.2); Topic 10 DPDP phasing (6.6); Topic 12 on-prem and sovereign (7.8); Topic 15 RL environments (9.5); Problem 10 V4 and V5 (3.12, 4.15); Problem 03 T11 |
| 2026-11-27 | Partial leaves closed: Topic 02 the harness as a whole (4.10); Topic 09 always-on agents (4.9) and AI UX with feedback design (11.2); Topic 12 unit economics and pricing (11.3); Drills: Forward-Deployed Engineering Rounds with Drill 17 outcome scoring, tested (11.5) |

## Related Notes

- [[Agentic/README|Agentic README]] · [[Agentic Problem to Solution Map]] · [[Agentic/Problems/README|Practice Designs]] · [[AI Engineer Round Drills]]
