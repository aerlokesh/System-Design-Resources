# Container Orchestrator System - HELLO Interview Framework

> **Companies**: Microsoft (Azure/AKS)  
> **Difficulty**: Medium  
> **Pattern**: Strategy (scheduling) + Observer (lifecycle) + Restart Policy  
> **Time**: 35 minutes

## Understanding the Problem

A simplified Kubernetes/Docker Swarm: deploy containers to worker nodes based on resource availability (CPU/memory), handle failures with auto-restart, reschedule when nodes die. **Premium Microsoft question** — directly relevant to Azure Kubernetes Service (AKS).

### For L63 Microsoft
1. **Strategy**: 3 scheduling algorithms (RoundRobin, LeastLoaded, BestFit) swappable at runtime
2. **Observer**: Lifecycle event notifications (started, stopped, failed, scheduling failed)
3. **Restart Policy**: NEVER, ON_FAILURE, ALWAYS — mirrors Kubernetes exactly
4. **Resource tracking**: CPU millicores + memory MB per node, `canFit()` check
5. **Node failure**: Reschedule all containers to healthy nodes

---

## Key Design

```
Container — id, image, cpuRequired, memRequired, status, nodeId, restartPolicy, restartCount
WorkerNode — id, totalCpu, totalMemory, usedCpu, usedMemory, healthy
  - canFit(container): healthy && availableCpu >= required && availableMem >= required
  - allocate(container): usedCpu += required, add to node
  - deallocate(container): usedCpu -= required, remove from node

SchedulingStrategy (interface)
  ├── RoundRobinScheduling  — cycle through nodes (AtomicInteger counter)
  ├── LeastLoadedScheduling — min CPU utilization (stream + filter + min)
  └── BestFitScheduling     — tightest resource fit (minimize waste)

OrchestratorObserver → LoggingObserver (onStarted, onStopped, onFailed, onSchedulingFailed)

ContainerOrchestrator
  - deploy(image, cpu, mem, restartPolicy) → schedule to best node
  - stop(containerId) → deallocate + stopped status
  - simulateFailure(containerId) → deallocate + auto-restart per policy
  - simulateNodeFailure(nodeId) → mark unhealthy + reschedule all containers
```

### Scheduling Strategies

```java
// LeastLoaded: spread evenly across cluster
WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
    return nodes.stream()
        .filter(n -> n.canFit(c))           // only nodes with enough resources
        .min(comparingDouble(WorkerNode::getCpuUtilization))  // lowest load
        .orElse(null);
}

// BestFit: pack tightly (minimize wasted resources)
WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
    return nodes.stream()
        .filter(n -> n.canFit(c))
        .min(comparingInt(n -> n.getAvailableCpu() - c.getCpuRequired()))
        .orElse(null);  // smallest remaining CPU = tightest fit
}
```

### Restart Policy (mirrors K8s)
```
NEVER      → Container stays FAILED (batch jobs)
ON_FAILURE → Auto-restart on crash (most services)
ALWAYS     → Restart on any termination including manual stop (critical services)
```

### Node Failure → Reschedule
```java
void simulateNodeFailure(String nodeId) {
    node.setHealthy(false);  // canFit() now returns false for this node
    for (Container c : node.getContainers()) {
        node.deallocate(c);
        c.setStatus(PENDING);
        scheduleContainer(c);  // reschedule to HEALTHY nodes only
    }
}
```

### Deep Dives
- **Production K8s**: etcd for state, kube-scheduler for placement, kubelet on each node
- **Affinity/Anti-affinity**: Prefer/avoid co-locating certain containers
- **Resource requests vs limits**: Request = minimum guaranteed, Limit = max allowed
- **Horizontal Pod Autoscaler**: Scale replicas based on CPU/memory utilization

### Tested Scenarios
1. Deploy 5 containers (LeastLoaded spreads evenly across 3 nodes)
2. Container failure → ON_FAILURE auto-restart
3. Resource exhaustion → scheduling fails gracefully
4. Runtime strategy swap to BestFit
5. Node failure → all containers rescheduled to surviving nodes

See `ContainerOrchestratorSystem.java` for full implementation.
