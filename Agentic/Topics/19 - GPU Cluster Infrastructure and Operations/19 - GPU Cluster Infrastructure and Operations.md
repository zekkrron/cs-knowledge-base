---
tags: [agentic, status/draft]
created: 2026-11-27
---
# GPU Cluster Infrastructure and Operations

> [!abstract] What this is
> The layer under the serving engine that GPU-cloud and inference-provider interviews probe (Together AI, Crusoe, Baseten, CoreWeave, Lambda, 2026): what is inside a GPU node and between nodes, how Kubernetes and Slurm schedule GPUs (device plugins, MIG, gang scheduling, topology), how a fleet detects and drains bad GPUs, GPU virtualization for multi-tenant clouds, collective-communication failures, and a method for debugging a slow Linux host instead of guessing. [[13 - GPU Serving and Disaggregated KV Cache]] and [[14 - Quantization Decoding and VRAM Math]] cover the engine and the arithmetic; this note covers the machines.

---

## Why It Exists

- At inference providers the interview goes "one layer below the box diagram": how the scheduler learns GPU topology, how tenant isolation works in the network, what happens when a GPU falls off the bus (PracHub Together AI guide, Nov 2026).
- Reported questions: a GPU-aware pod scheduler (Together AI, hard, ~1,200 solves); schedule GPU pods and drain a node; on-demand GPU scheduling with minimal fragmentation; VFIO vs SR-IOV vs PCIe passthrough; Linux CPU, disk, network and logging-saturation diagnosis; a VM deployment system for a fleet of hosts (Crusoe); a data pipeline for messy pod alerts and bad-pod detection (Baseten).
- Application-level AI engineers rarely need this depth; inference and infra roles do ([[Syllabus and Coverage#Axis C: Who Asks]]).

---

## Inside a GPU Node and Between Nodes

| Link | Typical bandwidth | Used for |
|---|---|---|
| **HBM** on the GPU | ~3.35 TB/s (H100 SXM) | Weights, KV cache, activations |
| **NVLink / NVSwitch** inside a node | ~900 GB/s per GPU total (H100) | Tensor parallelism, all-reduce inside the node |
| **PCIe Gen5 x16** GPU ↔ CPU / NIC | ~64 GB/s each way | Loading weights from host memory, staging |
| **InfiniBand or RoCE** between nodes | 200–400 Gb/s per GPU NIC (*assumed*) | Data and pipeline parallelism, KV transfer in disaggregated serving |
| **Frontend Ethernet** | 25–200 Gb/s per node (*assumed*) | API traffic, storage, image and weight pulls |

- **GPUDirect RDMA** lets the NIC read and write GPU memory directly without copying through the CPU; it is what makes cross-node KV transfer and GPU-to-GPU weight loading fast ([[14 - Model Weight Distribution to a GPU Fleet]]).
- **Rail-optimised fabrics:** GPU *i* on every node connects to the same leaf switch ("rail"), so the common collective pattern stays one hop.
- **NUMA matters:** pin each GPU's worker process and its NIC to the CPU socket they hang off; cross-socket traffic adds latency and halves effective PCIe bandwidth.
- **Rule from [[14 - Quantization Decoding and VRAM Math#Roofline and Sharding Arithmetic]]:** keep tensor parallelism inside the NVLink domain; cross nodes with pipeline or data parallelism.

---

## Scheduling GPUs

### Kubernetes

- GPUs are an **extended resource** (`nvidia.com/gpu`) advertised by the NVIDIA **device plugin**; a pod requests whole GPUs and the kubelet assigns specific devices. The **NVIDIA GPU Operator** installs drivers, the device plugin, DCGM and the container toolkit on every GPU node.
- **Sharing a GPU:**

| Method | Isolation | Pick when |
|---|---|---|
| **Whole GPU** | Full | Large models; anything latency-sensitive |
| **MIG** (Multi-Instance GPU) | Hardware: separate memory and compute slices, up to 7 per A100/H100 | Several small models or tenants with guaranteed isolation |
| **MPS** | Shared memory space, concurrent kernels | Cooperative processes of one tenant wanting throughput |
| **Time-slicing** | None: contexts take turns | Dev and low-priority work; worst tail latency |

- **The default scheduler is per pod.** A distributed job that needs 16 GPUs across 2 nodes can get 12 and wait forever while holding them. Use **gang scheduling** (all-or-nothing admission) with Kueue, Volcano or the coscheduling plugin, or **Slurm** for training clusters.
- **Topology-aware placement:** prefer GPUs on the same NVLink island and nodes on the same leaf switch; the device plugin and scheduler extensions expose topology labels.

### Placement and fragmentation

- **Best fit** (smallest free slot that fits) keeps whole nodes free for large jobs; first fit is faster and fragments more. Track a **fragmentation metric**: free GPUs that can't serve the largest pending request.
- Data structure for the coding version (Together AI's pod scheduler): `free_gpus → set(nodes)` buckets or a sorted structure, updated atomically on every add, schedule, remove and evict; drain marks a node unschedulable, evicts its pods to pending and reschedules them.
- Reservations, backfill and preemption for mixed 1-GPU and 8-GPU work: [[15 - GPU Job Scheduler for Video Generation]] V4.

---

## Node Health: Detect, Cordon, Drain

At thousands of GPUs, something is always broken. The question is how the fleet notices and stops sending work there.

- **Signals:**
    + **DCGM** (NVIDIA Data Center GPU Manager) exports per-GPU health, temperature, power, ECC error counts, NVLink errors and utilisation to Prometheus.
    + **Xid errors** in the kernel log are the driver's error codes; a few to recognise: **Xid 79** (GPU has fallen off the bus), **Xid 48** (double-bit ECC error), **Xid 63/64** (row-remapping events and failures), **Xid 13/31/43** (usually application faults, not hardware).
    + Job-level symptoms: NCCL timeouts, throughput far below the fleet median for the same model, repeated job failures on one node.
- **Pipeline:** health checks and log parsing → a per-node health state → **cordon** (no new work) → **drain** (move or finish existing work) → automated diagnostics (DCGM diagnostic levels, burn-in) → repair ticket or return to service.
- **Guard the automation:** rate-limit cordons (never more than a few percent of a cluster per hour) and require a human above that, because a bad health check or a monitoring outage can otherwise drain the whole fleet (PracHub Together AI guide).
- **Draining inference vs training:** inference replicas drain by stopping new requests and letting streams finish ([[07 - LLM Inference Platform]] V3); training jobs can't move mid-step, so drain means "checkpoint, then restart the job without that node".
- **Bad-pod detection from messy alerts** (Baseten data-engineering question): normalise alerts per pod, deduplicate flapping, join with deployment and node metadata, and score pods by alert rate against their peers running the same model; a node is suspect when several unrelated pods on it degrade together.

---

## GPU Virtualization for Multi-Tenant Clouds

| Method | How | Isolation | Overhead | Pick when |
|---|---|---|---|---|
| **PCIe passthrough (VFIO)** | Whole GPU assigned to one VM through the IOMMU | Strong | Near zero | Default for GPU VMs; one tenant per GPU |
| **SR-IOV** | Device exposes virtual functions, each assignable to a VM | Hardware-partitioned | Low | NICs (and some GPUs) shared across VMs with line-rate performance |
| **vGPU / mediated devices** | Hypervisor-managed time or space slices of one GPU | Software-enforced | Some | VDI and small inference shared across VMs |
| **MIG inside a passed-through GPU** | Hardware slices exposed to containers in the VM | Hardware | Low | Many small tenants on big GPUs |

- **Network isolation between tenants:** VLAN (12-bit IDs, ~4,000 networks) vs **VXLAN** overlays (24-bit VNIs, ~16M networks) vs cloud VPC constructs; InfiniBand uses **partition keys** set through the subnet manager. Verify isolation before handing a node to a new tenant, and wipe GPU memory between tenants.

---

## Collective Communication and Training-Scale Failures

- **NCCL** implements all-reduce, all-gather and reduce-scatter over NVLink and InfiniBand. The ring all-reduce time is about `2 × (n−1)/n × bytes ÷ bandwidth` plus per-step latency ([[14 - Quantization Decoding and VRAM Math#Sharding a matmul across GPUs]]).
- **One slow or dead rank stalls every rank:** collectives wait for all participants. Symptoms are a hang, not an error. Set NCCL and job timeouts, and log per-rank step times so the straggler is visible.
- **Checkpoints at scale** (reported scenario: a 512-node training run): write sharded checkpoints in parallel from every rank to fast parallel storage, asynchronously so training continues; keep the last few; on failure, restart from the latest complete checkpoint without the bad node. Checkpoint interval trades lost work against write cost: with failure rate λ per hour and checkpoint cost C, a classic approximation is an interval of about `√(2C ÷ λ)` (Young's formula, 1974).

---

## Debugging a Slow Host: a Method, Not a Guess

Together AI's screens ask to diagnose CPU problems, storage I/O, slow network throughput including containers, and a server saturated by logging. Use the **USE method** (Brendan Gregg): for every resource, check **Utilisation, Saturation, Errors**, then narrow.

| Resource | Utilisation | Saturation | Errors | Tools |
|---|---|---|---|---|
| **CPU** | `%usr/%sys` per core | Run-queue length (load vs cores), throttling in containers (`cpu.stat` throttled time) | Machine-check events | `top`, `mpstat -P ALL`, `pidstat`, `perf top` |
| **Memory** | Used vs available | Swapping, page faults, OOM kills | ECC errors | `free`, `vmstat`, `dmesg` |
| **Disk** | `%util` | Queue depth `aqu-sz`, `await` | I/O errors in `dmesg` | `iostat -x 1`, `iotop`, `pidstat -d` |
| **Network** | Throughput vs link speed | Drops, retransmits, socket backlogs | Interface errors | `ss -s`, `sar -n DEV,TCP`, `ethtool -S`, `nstat` |
| **GPU** | SM utilisation, memory bandwidth | Queue time in the engine, KV usage | Xid, ECC | `nvidia-smi`, DCGM, engine metrics |

- **Containers:** CPU limits cause throttling at low average utilisation (bursty work hits the quota each period); check the cgroup's throttled time, not just `top`. Network inside containers adds a veth and often NAT or an overlay: compare throughput host-to-host vs pod-to-pod to find the layer that loses it; check MTU mismatches on overlays.
- **A server saturated by logging:** symptoms are high `%sys` or `iowait`, a slow disk, or a blocked process when synchronous log writes back up. Fixes: asynchronous, buffered logging with a bounded queue that drops (and counts) rather than blocks, sampling of high-volume debug logs, log rotation, and shipping off-host ([[11 - Services and Observability]]).
- **For each step, say what reading rules the cause out** (PracHub guide): "run queue below core count, so CPU isn't saturated; next disk".

---

## Failure Modes

| Failure | What happens | Production fix |
|---|---|---|
| Partial gang allocation | Job holds 12 of 16 GPUs and never starts | Gang scheduling (Kueue, Volcano, Slurm) |
| Fragmentation | Free GPUs nobody can use | Best fit, reservations, separate pools, fragmentation metric |
| TP across nodes | All-reduces over InfiniBand dominate | Tensor parallel inside the NVLink domain |
| GPU falls off the bus (Xid 79) | Jobs on the node fail or hang | DCGM and Xid monitoring, automatic cordon and drain |
| Health automation misfires | Whole cluster drained | Rate-limited cordons; human approval above a threshold |
| One straggler rank | Collective hang | Timeouts, per-rank step times, restart without the node |
| CPU throttling in containers | Latency spikes at low average CPU | Check cgroup throttling; raise limits or remove them for latency-critical pods |
| Tenant data left in GPU memory | Leak to the next tenant | Wipe GPU memory and reset between tenants; verify network isolation |

---

## Interview Lines

> [!tip] Say it like this
> - "GPUs are an extended resource from the device plugin; for anything multi-node I need gang scheduling, or a job sits on 12 of its 16 GPUs forever."
> - "I place with best fit and track a fragmentation metric, because free GPUs scattered across nodes can't serve an 8-GPU job."
> - "MIG gives hardware isolation for small tenants; time-slicing gives none, so I only use it for dev."
> - "Node health comes from DCGM and Xid errors; automation cordons and drains, but it's rate-limited so a bad check can't drain the cluster."
> - "For a slow host I go resource by resource with USE: utilisation, saturation, errors, and say what each reading rules out; in containers I check CPU throttling first."
> - "Passthrough with VFIO is the default for GPU VMs; SR-IOV shares NICs at line rate; tenants get VXLAN or InfiniBand partition-key isolation, and GPU memory is wiped between them."

## Sources

- PracHub, *Together AI Software Engineer Interview Guide* (Nov 2026): reported design, systems and troubleshooting questions. https://prachub.com/interview-guide/together-ai-software-engineer-interview-questions-guide-2026
- PracHub, *Design a GPU-aware pod scheduler* (Together AI, technical screen). https://prachub.com/interview-questions/design-a-gpu-aware-pod-scheduler
- PracHub, Crusoe and Baseten company question banks (2026). https://prachub.com/companies/crusoe · https://prachub.com/companies/baseten
- NVIDIA, *Xid Errors* (driver documentation), *DCGM* user guide, *MIG User Guide*, *Kubernetes device plugin* and *GPU Operator* docs.
- Kubernetes, *Schedule GPUs*; Kueue documentation (job queueing and all-or-nothing admission).
- B. Gregg, *The USE Method* (2012). https://www.brendangregg.com/usemethod.html
- J. W. Young, *A First Order Approximation to the Optimum Checkpoint Interval* (CACM, 1974).

## Related Notes

- [[13 - GPU Serving and Disaggregated KV Cache]] · [[14 - Quantization Decoding and VRAM Math]] · [[07 - LLM Inference Platform]] · [[14 - Model Weight Distribution to a GPU Fleet]] · [[15 - GPU Job Scheduler for Video Generation]] · [[19 - Deployment and Infrastructure]]
- [[Agentic/README]]
