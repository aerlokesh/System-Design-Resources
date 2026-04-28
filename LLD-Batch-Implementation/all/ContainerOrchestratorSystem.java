import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Container Orchestrator System - HELLO Interview Framework
 * 
 * Companies: Microsoft
 * Pattern: Strategy (scheduling) + Observer (lifecycle events)
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Strategy — swappable scheduling: RoundRobin, LeastLoaded, BestFit
 * 2. Observer — notify on container lifecycle (created, started, stopped, failed)
 * 3. Node pool with resource tracking (CPU, memory)
 * 4. Container lifecycle: PENDING → RUNNING → STOPPED/FAILED
 * 5. Auto-restart on failure (configurable restart policy)
 */

// ==================== Enums ====================

enum ContainerStatus { PENDING, RUNNING, STOPPED, FAILED }
enum RestartPolicy { NEVER, ON_FAILURE, ALWAYS }

// ==================== Container ====================

class Container {
    private final String id;
    private final String image;
    private final int cpuRequired;      // millicores
    private final int memoryRequired;   // MB
    private volatile ContainerStatus status;
    private String nodeId;
    private final RestartPolicy restartPolicy;
    private int restartCount;

    Container(String id, String image, int cpu, int memory, RestartPolicy policy) {
        this.id = id; this.image = image;
        this.cpuRequired = cpu; this.memoryRequired = memory;
        this.status = ContainerStatus.PENDING;
        this.restartPolicy = policy; this.restartCount = 0;
    }

    String getId() { return id; }
    String getImage() { return image; }
    int getCpuRequired() { return cpuRequired; }
    int getMemoryRequired() { return memoryRequired; }
    ContainerStatus getStatus() { return status; }
    String getNodeId() { return nodeId; }
    RestartPolicy getRestartPolicy() { return restartPolicy; }
    int getRestartCount() { return restartCount; }

    void setStatus(ContainerStatus s) { this.status = s; }
    void setNodeId(String n) { this.nodeId = n; }
    void incrementRestart() { this.restartCount++; }

    @Override
    public String toString() {
        return String.format("Container[%s] %s (cpu=%d, mem=%dMB) [%s] node=%s restarts=%d",
            id, image, cpuRequired, memoryRequired, status, nodeId, restartCount);
    }
}

// ==================== Node ====================

class WorkerNode {
    private final String id;
    private final int totalCpu;        // millicores
    private final int totalMemory;     // MB
    private int usedCpu;
    private int usedMemory;
    private final Map<String, Container> containers = new ConcurrentHashMap<>();
    private boolean healthy = true;

    WorkerNode(String id, int totalCpu, int totalMemory) {
        this.id = id; this.totalCpu = totalCpu; this.totalMemory = totalMemory;
    }

    String getId() { return id; }
    int getAvailableCpu() { return totalCpu - usedCpu; }
    int getAvailableMemory() { return totalMemory - usedMemory; }
    int getTotalCpu() { return totalCpu; }
    int getTotalMemory() { return totalMemory; }
    int getContainerCount() { return containers.size(); }
    boolean isHealthy() { return healthy; }
    void setHealthy(boolean h) { this.healthy = h; }

    boolean canFit(Container c) {
        return healthy && getAvailableCpu() >= c.getCpuRequired()
            && getAvailableMemory() >= c.getMemoryRequired();
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

    double getCpuUtilization() { return totalCpu > 0 ? (double) usedCpu / totalCpu : 0; }
    double getMemUtilization() { return totalMemory > 0 ? (double) usedMemory / totalMemory : 0; }

    @Override
    public String toString() {
        return String.format("Node[%s] CPU=%d/%d(%d%%) MEM=%d/%dMB(%d%%) containers=%d %s",
            id, usedCpu, totalCpu, (int)(getCpuUtilization() * 100),
            usedMemory, totalMemory, (int)(getMemUtilization() * 100),
            containers.size(), healthy ? "✓" : "✗UNHEALTHY");
    }
}

// ==================== Scheduling Strategy ====================

interface SchedulingStrategy {
    WorkerNode selectNode(Container container, List<WorkerNode> nodes);
    String getName();
}

class RoundRobinScheduling implements SchedulingStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);
    @Override
    public WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
        int attempts = nodes.size();
        for (int i = 0; i < attempts; i++) {
            int idx = counter.getAndIncrement() % nodes.size();
            if (nodes.get(idx).canFit(c)) return nodes.get(idx);
        }
        return null;
    }
    @Override public String getName() { return "RoundRobin"; }
}

class LeastLoadedScheduling implements SchedulingStrategy {
    @Override
    public WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
        return nodes.stream()
            .filter(n -> n.canFit(c))
            .min(Comparator.comparingDouble(WorkerNode::getCpuUtilization))
            .orElse(null);
    }
    @Override public String getName() { return "LeastLoaded"; }
}

class BestFitScheduling implements SchedulingStrategy {
    @Override
    public WorkerNode selectNode(Container c, List<WorkerNode> nodes) {
        // Find node with least remaining resources after allocation (tightest fit)
        return nodes.stream()
            .filter(n -> n.canFit(c))
            .min(Comparator.comparingInt(n -> n.getAvailableCpu() - c.getCpuRequired()))
            .orElse(null);
    }
    @Override public String getName() { return "BestFit"; }
}

// ==================== Observer ====================

interface OrchestratorObserver {
    void onContainerStarted(Container c, WorkerNode node);
    void onContainerStopped(Container c);
    void onContainerFailed(Container c);
    void onSchedulingFailed(Container c, String reason);
}

class LoggingObserver implements OrchestratorObserver {
    @Override
    public void onContainerStarted(Container c, WorkerNode node) {
        System.out.printf("    🟢 Started %s on %s%n", c.getId(), node.getId());
    }
    @Override
    public void onContainerStopped(Container c) {
        System.out.printf("    🔴 Stopped %s%n", c.getId());
    }
    @Override
    public void onContainerFailed(Container c) {
        System.out.printf("    ⚠️ Failed %s (restarts=%d, policy=%s)%n",
            c.getId(), c.getRestartCount(), c.getRestartPolicy());
    }
    @Override
    public void onSchedulingFailed(Container c, String reason) {
        System.out.printf("    ❌ Cannot schedule %s: %s%n", c.getId(), reason);
    }
}

// ==================== Orchestrator ====================

class ContainerOrchestrator {
    private final List<WorkerNode> nodes = new ArrayList<>();
    private final Map<String, Container> allContainers = new ConcurrentHashMap<>();
    private SchedulingStrategy strategy;
    private final List<OrchestratorObserver> observers = new CopyOnWriteArrayList<>();
    private final AtomicInteger containerCounter = new AtomicInteger(0);

    ContainerOrchestrator(SchedulingStrategy strategy) { this.strategy = strategy; }

    void setStrategy(SchedulingStrategy s) { this.strategy = s; }
    void addNode(WorkerNode node) { nodes.add(node); }
    void addObserver(OrchestratorObserver o) { observers.add(o); }

    /** Deploy a container */
    Container deploy(String image, int cpu, int memory, RestartPolicy policy) {
        String id = "c-" + containerCounter.incrementAndGet();
        Container container = new Container(id, image, cpu, memory, policy);
        allContainers.put(id, container);

        return scheduleContainer(container);
    }

    private Container scheduleContainer(Container container) {
        WorkerNode node = strategy.selectNode(container, nodes);
        if (node == null) {
            container.setStatus(ContainerStatus.FAILED);
            for (OrchestratorObserver o : observers) o.onSchedulingFailed(container, "No node with sufficient resources");
            return container;
        }

        node.allocate(container);
        for (OrchestratorObserver o : observers) o.onContainerStarted(container, node);
        return container;
    }

    /** Stop a running container */
    boolean stop(String containerId) {
        Container c = allContainers.get(containerId);
        if (c == null || c.getStatus() != ContainerStatus.RUNNING) return false;

        WorkerNode node = findNode(c.getNodeId());
        if (node != null) node.deallocate(c);
        c.setStatus(ContainerStatus.STOPPED);
        for (OrchestratorObserver o : observers) o.onContainerStopped(c);
        return true;
    }

    /** Simulate container failure + auto-restart */
    void simulateFailure(String containerId) {
        Container c = allContainers.get(containerId);
        if (c == null || c.getStatus() != ContainerStatus.RUNNING) return;

        WorkerNode node = findNode(c.getNodeId());
        if (node != null) node.deallocate(c);
        c.setStatus(ContainerStatus.FAILED);
        c.incrementRestart();
        for (OrchestratorObserver o : observers) o.onContainerFailed(c);

        // Auto-restart based on policy
        if (c.getRestartPolicy() == RestartPolicy.ON_FAILURE
            || c.getRestartPolicy() == RestartPolicy.ALWAYS) {
            System.out.println("    🔄 Auto-restarting " + c.getId() + "...");
            c.setStatus(ContainerStatus.PENDING);
            scheduleContainer(c);
        }
    }

    /** Simulate node failure — reschedule all its containers */
    void simulateNodeFailure(String nodeId) {
        WorkerNode node = findNode(nodeId);
        if (node == null) return;

        System.out.println("    💥 Node " + nodeId + " FAILED! Rescheduling containers...");
        node.setHealthy(false);

        List<Container> toReschedule = new ArrayList<>();
        for (Container c : allContainers.values()) {
            if (nodeId.equals(c.getNodeId()) && c.getStatus() == ContainerStatus.RUNNING) {
                node.deallocate(c);
                c.setStatus(ContainerStatus.PENDING);
                c.incrementRestart();
                toReschedule.add(c);
            }
        }

        for (Container c : toReschedule) {
            scheduleContainer(c);
        }
    }

    private WorkerNode findNode(String nodeId) {
        return nodes.stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null);
    }

    String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("  ═══ Orchestrator (").append(strategy.getName()).append(") ═══\n");
        for (WorkerNode n : nodes) sb.append("  ").append(n).append("\n");
        long running = allContainers.values().stream().filter(c -> c.getStatus() == ContainerStatus.RUNNING).count();
        long pending = allContainers.values().stream().filter(c -> c.getStatus() == ContainerStatus.PENDING).count();
        long failed = allContainers.values().stream().filter(c -> c.getStatus() == ContainerStatus.FAILED).count();
        sb.append(String.format("  Containers: %d total | %d running | %d pending | %d failed%n",
            allContainers.size(), running, pending, failed));
        return sb.toString();
    }
}

// ==================== Main Demo ====================

public class ContainerOrchestratorSystem {
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  Container Orchestrator - Strategy + Observer + Restart  ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");

        ContainerOrchestrator orch = new ContainerOrchestrator(new LeastLoadedScheduling());
        orch.addObserver(new LoggingObserver());

        // Add worker nodes
        orch.addNode(new WorkerNode("node-1", 4000, 8192));   // 4 cores, 8GB
        orch.addNode(new WorkerNode("node-2", 4000, 8192));
        orch.addNode(new WorkerNode("node-3", 2000, 4096));   // 2 cores, 4GB

        System.out.println(orch.getStatus());

        // ── Scenario 1: Deploy containers ──
        System.out.println("━━━ Scenario 1: Deploy containers ━━━");
        Container c1 = orch.deploy("nginx:latest", 500, 512, RestartPolicy.ALWAYS);
        Container c2 = orch.deploy("redis:7", 1000, 1024, RestartPolicy.ON_FAILURE);
        Container c3 = orch.deploy("postgres:15", 1000, 2048, RestartPolicy.ON_FAILURE);
        Container c4 = orch.deploy("app-server:v2", 1500, 2048, RestartPolicy.ALWAYS);
        Container c5 = orch.deploy("worker:latest", 500, 512, RestartPolicy.NEVER);
        System.out.println(orch.getStatus());

        // ── Scenario 2: Container failure + auto-restart ──
        System.out.println("━━━ Scenario 2: Container failure + auto-restart ━━━");
        orch.simulateFailure(c2.getId()); // Redis fails → auto-restarts (ON_FAILURE)
        System.out.println(orch.getStatus());

        // ── Scenario 3: Stop a container ──
        System.out.println("━━━ Scenario 3: Stop container ━━━");
        orch.stop(c5.getId()); // worker stopped (NEVER restart)
        System.out.println(orch.getStatus());

        // ── Scenario 4: Resource exhaustion ──
        System.out.println("━━━ Scenario 4: Resource exhaustion ━━━");
        // Try to deploy a huge container
        orch.deploy("ml-training:gpu", 8000, 16384, RestartPolicy.NEVER); // too big!
        System.out.println();

        // ── Scenario 5: Switch scheduling strategy ──
        System.out.println("━━━ Scenario 5: Switch to BestFit scheduling ━━━");
        orch.setStrategy(new BestFitScheduling());
        Container c6 = orch.deploy("cache:latest", 500, 256, RestartPolicy.ALWAYS);
        System.out.println(orch.getStatus());

        // ── Scenario 6: Node failure → reschedule ──
        System.out.println("━━━ Scenario 6: Node failure → reschedule ━━━");
        orch.simulateNodeFailure("node-1");
        System.out.println(orch.getStatus());

        System.out.println("✅ All Container Orchestrator scenarios complete.");
    }
}
