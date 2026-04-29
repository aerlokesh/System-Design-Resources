# Container Orchestrator System - HELLO Interview Framework

> **Companies**: Microsoft (Azure/AKS)  
> **Difficulty**: Medium  
> **Pattern**: Strategy (scheduling) + Observer (lifecycle) + Restart Policy  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Container Orchestrator?

A system that deploys containerized applications across a cluster of worker nodes, managing resource allocation (CPU/memory), handling failures with auto-restart, and rescheduling when nodes die. Think: simplified **Kubernetes**, Docker Swarm, or **Azure Kubernetes Service (AKS)**.

**Real-world**: Kubernetes manages containers for Google (15B containers/week), Netflix, Spotify. AKS is Microsoft's managed Kubernetes — directly relevant for L63 Azure interviews.

### For L63 Microsoft Interview

This is a **premium Microsoft question** — directly relevant to Azure:
1. **Strategy Pattern**: 3 scheduling algorithms (RoundRobin, LeastLoaded, BestFit) swappable at runtime
2. **Observer Pattern**: Lifecycle event notifications (started, stopped, failed, scheduling failed)
3. **Restart Policy**: NEVER, ON_FAILURE, ALWAYS — mirrors Kubernetes RestartPolicy exactly
4. **Resource management**: CPU millicores + memory MB tracking per node
5. **Node failure**: Mark unhealthy, reschedule all affected containers to healthy nodes
6. **Resource exhaustion**: Graceful handling when no node can fit a container

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "How many nodes in cluster?" → Configurable (3-100+)
- "What resources to track?" → CPU (millicores) + Memory (MB)
- "Restart on failure?" → Configurable per container (NEVER, ON_FAILURE, ALWAYS)
- "What scheduling strategies?" → Multiple, swappable at runtime
- "What happens when no node can fit?" → Report error, container stays FAILED

### Functional Requirements (P0)
1. **Deploy container**: Specify image, CPU, memory, restart policy → schedule to best node
2. **Scheduling strategies**: RoundRobin, LeastLoaded, BestFit — swappable
3. **Stop container**: Deallocate resources, update status
4. **Container failure**: Auto-restart based on restart policy
5. **Node failure**: Mark unhealthy, reschedule all containers to surviving nodes
6. **Resource tracking**: Per-node CPU/memory utilization
7. **Observer notifications**: Started, stopped, failed, scheduling failed

### Non-Functional
- O(N) scheduling where N = nodes (scan for best fit)
- Resource accounting accurate (no over-allocation)
- Graceful handling of resource exhaustion

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌─────────────────────────────────────────────────────────┐
│              ContainerOrchestrator                       │
│  - nodes: List<WorkerNode>                              │
│  - allContainers: Map<String, Container>                │
│  - strategy: SchedulingStrategy (swappable!)             │
│  - observers: List<OrchestratorObserver>                │
│  - deploy(image, cpu, mem, restartPolicy) → Container   │
│  - stop(containerId)                                    │
│  - simulateFailure(containerId) → auto-restart          │
│  - simulateNodeFailure(nodeId) → reschedule all         │
└─────────────┬───────────────────────────────────────────┘
              │ manages
    ┌─────────┼─────────┐
    ▼         ▼         ▼
┌─────────┐ ┌──────────────────────────────────────────┐
│Container│ │            WorkerNode                    │
│- id     │ │  - id, totalCpu, totalMemory             │
│- image  │ │  - usedCpu, usedMemory                   │
│- cpu    │ │  - containers: Map<String, Container>    │
│- memory │ │  - healthy: boolean                      │
│- status │ │  - canFit(container): boolean            │
│- nodeId │ │  - allocate(container) / deallocate()    │
│- restart│ │  - getCpuUtilization(): double           │
│  Policy │ └──────────────────────────────────────────┘
│- restart│
│  Count  │ ┌──────────────────────────────────────────┐
└─────────┘ │      SchedulingStrategy (interface)       │
            │  + selectNode(container, nodes): Node     │
            ├──────────────────────────────────────────┤
            │  RoundRobinScheduling — cycle through     │
            │  LeastLoadedScheduling — lowest CPU util  │
            │  BestFitScheduling — tightest resource fit│
            └──────────────────────────────────────────┘
```

### Enum: ContainerStatus
```
PENDING → Waiting to be scheduled
RUNNING → Executing on a node
STOPPED → Manually stopped
FAILED  → Crashed or node died
```

### Enum: RestartPolicy (mirrors Kubernetes)
```
NEVER      → Stay FAILED after crash (batch jobs)
ON_FAILURE → Auto-restart only on crash (most services)
ALWAYS     → Restart on any termination including manual stop (critical services)
```

### Class: Container
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | "c-1", "c-2" |
| image | String | "nginx:latest", "redis:7" |
| cpuRequired | int | Millicores (1000 = 1 core) |
| memoryRequired | int | Megabytes |
| status | ContainerStatus | PENDING/RUNNING/STOPPED/FAILED |
| nodeId | String | Which node it's running on |
| restartPolicy | RestartPolicy | NEVER/ON_FAILURE/ALWAYS |
| restartCount | int | Times restarted |

### Class: WorkerNode
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | "node-1" |
| totalCpu/totalMemory | int | Total capacity |
| usedCpu/usedMemory | int | Currently allocated |
| healthy | boolean | Can accept new containers? |

---

## 3️⃣ API Design

```java
class ContainerOrchestrator {
    /** Deploy a container — scheduler picks best node */
    Container deploy(String image, int cpu, int memory, RestartPolicy policy);
    
    /** Stop a running container — deallocate resources */
    boolean stop(String containerId);
    
    /** Simulate container crash — auto-restart per policy */
    void simulateFailure(String containerId);
    
    /** Simulate node failure — reschedule all its containers */
    void simulateNodeFailure(String nodeId);
    
    /** Swap scheduling algorithm at runtime */
    void setStrategy(SchedulingStrategy strategy);
    
    /** Add worker node to cluster */
    void addNode(WorkerNode node);
    
    /** Register lifecycle observer */
    void addObserver(OrchestratorObserver observer);
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Deploy Container (LeastLoaded)

```
deploy("nginx:latest", 500cpu, 512MB, ALWAYS)
  │
  ├─ Create Container("c-1", "nginx:latest", 500, 512, ALWAYS)
  ├─ strategy.selectNode(container, nodes):
  │   ├─ LeastLoadedScheduling:
  │   │   ├─ node-1: canFit? cpu 500≤4000 ✓, mem 512≤8192 ✓, healthy ✓ → util=0%
  │   │   ├─ node-2: canFit? ✓ → util=0%
  │   │   ├─ node-3: canFit? ✓ → util=0%
  │   │   └─ Min utilization → node-1 (first with 0%)
  │   └─ Return: node-1
  │
  ├─ node-1.allocate(container):
  │   ├─ usedCpu += 500 → 500/4000 = 12.5%
  │   ├─ usedMemory += 512 → 512/8192 = 6.25%
  │   ├─ container.nodeId = "node-1"
  │   └─ container.status = RUNNING
  │
  ├─ Notify observers: onContainerStarted(c-1, node-1)
  │   └─ LoggingObserver: "🟢 Started c-1 on node-1"
  │
  └─ Return: Container c-1
```

### Scenario 2: Container Failure + Auto-Restart

```
simulateFailure("c-2")     ← Redis container crashes
  │
  ├─ container c-2: status=RUNNING, restartPolicy=ON_FAILURE
  ├─ node-2.deallocate(c-2) → usedCpu -= 1000, usedMemory -= 1024
  ├─ c-2.status = FAILED
  ├─ c-2.restartCount++
  │
  ├─ Notify: onContainerFailed(c-2)
  │   └─ "⚠️ Failed c-2 (restarts=1, policy=ON_FAILURE)"
  │
  ├─ Policy check: ON_FAILURE → auto-restart!
  │   ├─ c-2.status = PENDING
  │   └─ scheduleContainer(c-2):
  │       ├─ strategy.selectNode → node-2 (still has capacity)
  │       ├─ node-2.allocate(c-2) → RUNNING again
  │       └─ Notify: onContainerStarted(c-2, node-2)
  │
  └─ "🔄 Auto-restarting c-2..."
```

### Scenario 3: Node Failure → Reschedule All

```
simulateNodeFailure("node-1")
  │
  ├─ node-1.setHealthy(false)    ← canFit() now always returns false
  │
  ├─ Find all containers on node-1: [c-1 (nginx), c-4 (app-server)]
  │
  ├─ For each container:
  │   ├─ node-1.deallocate(c-1) → free resources
  │   ├─ c-1.status = PENDING
  │   ├─ c-1.restartCount++
  │   └─ scheduleContainer(c-1):
  │       ├─ strategy.selectNode → node-3 (node-1 excluded: unhealthy)
  │       └─ node-3.allocate(c-1) → RUNNING on node-3
  │
  └─ "💥 Node node-1 FAILED! Rescheduling 2 containers..."
```

### Scenario 4: Resource Exhaustion

```
deploy("ml-training:gpu", 8000cpu, 16384MB, NEVER)
  │
  ├─ strategy.selectNode(container, nodes):
  │   ├─ node-1: canFit? cpu 8000≤4000? ❌ (not enough CPU)
  │   ├─ node-2: canFit? cpu 8000≤4000? ❌
  │   ├─ node-3: canFit? cpu 8000≤2000? ❌
  │   └─ Return: null (no node can fit!)
  │
  ├─ container.status = FAILED
  ├─ Notify: onSchedulingFailed(c-6, "No node with sufficient resources")
  │   └─ "❌ Cannot schedule c-6: No node with sufficient resources"
  └─ Return: Container (status=FAILED)
```

---

## 5️⃣ Design + Implementation

### Strategy Pattern: 3 Scheduling Algorithms

```java
interface SchedulingStrategy {
    WorkerNode selectNode(Container container, List<WorkerNode> nodes);
    String getName();
}

// 1. RoundRobin: cycle through nodes
class RoundRobinScheduling implements SchedulingStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            int idx = counter.getAndIncrement() % nodes.size();
            if (nodes.get(idx).canFit(c)) return nodes.get(idx);
        }
        return null;  // no node can fit
    }
}

// 2. LeastLoaded: lowest CPU utilization
class LeastLoadedScheduling implements SchedulingStrategy {
    @Override
    public WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
        return nodes.stream()
            .filter(n -> n.canFit(c))
            .min(Comparator.comparingDouble(WorkerNode::getCpuUtilization))
            .orElse(null);
    }
}

// 3. BestFit: tightest resource fit (minimize waste)
class BestFitScheduling implements SchedulingStrategy {
    @Override
    public WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
        return nodes.stream()
            .filter(n -> n.canFit(c))
            .min(Comparator.comparingInt(n -> n.getAvailableCpu() - c.getCpuRequired()))
            .orElse(null);
    }
}
```

**Why Strategy?** Different scheduling policies for different situations:
- **RoundRobin**: Simple, fair distribution
- **LeastLoaded**: Spread load evenly (default for most clusters)
- **BestFit**: Pack tightly to save resources (cost optimization)

Swap at runtime: `orchestrator.setStrategy(new BestFitScheduling())`

### Resource Tracking: canFit()

```java
class WorkerNode {
    boolean canFit(Container c) {
        return healthy                                    // not failed
            && getAvailableCpu() >= c.getCpuRequired()   // enough CPU
            && getAvailableMemory() >= c.getMemoryRequired(); // enough memory
    }
    
    void allocate(Container c) {
        usedCpu += c.getCpuRequired();
        usedMemory += c.getMemoryRequired();
        containers.put(c.getId(), c);
        c.setNodeId(id);
        c.setStatus(ContainerStatus.RUNNING);
    }
    
    void deallocate(Container c) {
        usedCpu -= c.getCpuRequired();
        usedMemory -= c.getMemoryRequired();
        containers.remove(c.getId());
    }
}
```

### Restart Policy Logic

```java
void simulateFailure(String containerId) {
    Container c = allContainers.get(containerId);
    WorkerNode node = findNode(c.getNodeId());
    node.deallocate(c);
    c.setStatus(FAILED);
    c.incrementRestart();
    
    // Auto-restart based on policy
    if (c.getRestartPolicy() == RestartPolicy.ON_FAILURE
        || c.getRestartPolicy() == RestartPolicy.ALWAYS) {
        c.setStatus(PENDING);
        scheduleContainer(c);  // reschedule to any healthy node
    }
    // NEVER → stays FAILED
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Scheduling Strategy Comparison

| Strategy | Algorithm | Best For | Weakness |
|----------|-----------|----------|----------|
| **RoundRobin** | Cycle through nodes | Even distribution, simple | Ignores capacity differences |
| **LeastLoaded** | Min CPU utilization | Load balancing | May not pack efficiently |
| **BestFit** | Tightest fit | Resource efficiency, cost | Uneven load, fragmentation |

**Production K8s** uses a scoring system: filter (can node fit?) → score (multiple factors: spread, affinity, resource balance) → pick highest score.

### Deep Dive 2: Kubernetes Concepts Mapped to Our Design

| K8s Concept | Our Design | Description |
|-------------|-----------|-------------|
| Pod | Container | Deployable unit |
| Node | WorkerNode | Machine in cluster |
| kube-scheduler | SchedulingStrategy | Placement decisions |
| kubelet | allocate/deallocate | Node-level container management |
| RestartPolicy | RestartPolicy enum | NEVER, OnFailure, Always |
| etcd | allContainers Map | Cluster state storage |
| Controller Manager | simulateFailure | Reconciliation loop |

### Deep Dive 3: Node Failure Recovery

```
Node failure detection (production):
  1. kubelet sends heartbeat every 10s
  2. Controller Manager checks: no heartbeat for 40s → mark NotReady
  3. After 5 minutes: evict all pods, reschedule to healthy nodes

Our design simplifies this:
  simulateNodeFailure(nodeId) → immediate reschedule
```

### Deep Dive 4: Resource Requests vs Limits

| Concept | Meaning | Our Design |
|---------|---------|-----------|
| **Request** | Guaranteed minimum | cpuRequired (used for scheduling) |
| **Limit** | Maximum allowed | Not implemented (P1) |
| **Overcommit** | Request < Limit | Allows running more containers |

In K8s: Pod can burst above request up to limit. We implement only requests (scheduling guarantee).

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| No node can fit container | Container status = FAILED, observer notified |
| Container on unhealthy node | canFit() returns false, excluded from scheduling |
| NEVER restart policy + failure | Container stays FAILED permanently |
| Reschedule to same node | Won't happen if node is marked unhealthy |
| All nodes unhealthy | No container can be scheduled, all FAILED |
| Deploy after setStrategy | New strategy used immediately for next deploy |

### Deep Dive 6: Complexity

| Operation | Time | Space |
|-----------|------|-------|
| deploy | O(N) scan nodes | O(1) per container |
| stop | O(1) deallocate | O(1) |
| simulateFailure | O(N) reschedule | O(1) |
| simulateNodeFailure | O(C × N) reschedule C containers | O(C) |
| Total | — | O(N nodes + C containers) |

---

## 📋 Interview Checklist (L63 Microsoft)

- [ ] **Strategy Pattern**: 3 scheduling algorithms, swappable at runtime
- [ ] **Resource tracking**: canFit() checks CPU + memory + healthy
- [ ] **Restart Policy**: NEVER/ON_FAILURE/ALWAYS — mirrors Kubernetes
- [ ] **Node failure**: Mark unhealthy + reschedule all containers
- [ ] **Observer**: Lifecycle notifications (started, stopped, failed)
- [ ] **Graceful exhaustion**: No node fits → FAILED status, not crash
- [ ] **K8s mapping**: Explained how design maps to real Kubernetes concepts
- [ ] **Azure relevance**: Mentioned AKS, production scoring vs our simplified strategy

See `ContainerOrchestratorSystem.java` for full implementation with 6 demo scenarios.
