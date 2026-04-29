# Agentic Workflow System - HELLO Interview Framework

> **Companies**: Microsoft (AutoGen/Semantic Kernel), OpenAI (Swarm), LangChain, Amazon (Bedrock Agents)  
> **Difficulty**: Hard  
> **Pattern**: Mediator (coordinator) + Command (tasks) + Observer (state changes) + Strategy (delegation) + State (agent lifecycle)  
> **Time**: 45 minutes

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

### 🎯 What is an Agentic Workflow System?

A system that orchestrates **autonomous agents** — each with its own state, capabilities, and lifecycle — to collaboratively accomplish complex goals. A central **WorkflowManager** (mediator) coordinates task delegation, while an **AgentRegistry** tracks active agents and their capabilities. Agents communicate via **messages** (sync/async), delegate sub-tasks to each other based on capability or load, and must handle geographic distribution, failures, and recovery.

**Real-world**: Microsoft AutoGen (multi-agent conversations), Semantic Kernel (AI agent orchestration), OpenAI Swarm, LangChain Agents, CrewAI, Amazon Bedrock Agents, Kubernetes controllers (each controller is an agent managing specific resources).

### For L63 Microsoft Interview

1. **Mediator Pattern**: WorkflowManager coordinates all agent-to-agent communication — agents don't know about each other directly
2. **Command Pattern**: Tasks are encapsulated as objects with execute/undo/status — can be queued, retried, delegated
3. **Observer Pattern**: AgentRegistry notifies WorkflowManager on agent join/leave/failure
4. **Strategy Pattern**: DelegationStrategy determines which agent gets a task (capability-match, least-loaded, region-aware)
5. **State Pattern**: Agent lifecycle (IDLE → BUSY → FAILED → RECOVERING)
6. **Geographic awareness**: Region-based routing, latency-aware delegation, partition handling
7. **Fault tolerance**: Heartbeat monitoring, task reassignment on failure, idempotent tasks

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What is an agent?" → An autonomous unit with capabilities (e.g., "text-analysis", "code-generation", "data-retrieval"), its own state, and ability to execute tasks
- "How do agents communicate?" → Via the Mediator (WorkflowManager) — both sync (request/response) and async (fire-and-forget messages)
- "Task delegation logic?" → Based on capability match first, then load balancing, then region proximity
- "Geographic distribution?" → Agents in different regions (us-east, eu-west, ap-south), prefer local delegation to minimize latency
- "Failure handling?" → Heartbeat monitoring, task reassignment to healthy agent, max retries before escalation
- "Dynamic agents?" → Agents can join and leave at runtime — the system adapts

### Functional Requirements (P0)
1. **Agent lifecycle**: Create, register, deregister agents dynamically at runtime
2. **Agent state**: Each agent maintains own state (IDLE, BUSY, FAILED, RECOVERING) with exposed query/update methods
3. **Task delegation**: WorkflowManager assigns tasks to agents based on capability and load
4. **Inter-agent messaging**: Sync (request/response) and async (fire-and-forget) communication
5. **Sub-task delegation**: Agent can delegate sub-tasks to other agents via WorkflowManager
6. **Central registry**: Track all active agents, capabilities, pending tasks, and health
7. **Fault tolerance**: Detect agent failures, reassign tasks, recover state

### Functional Requirements (P1)
- Workflow DAG (tasks with dependencies)
- Agent groups/pools by capability
- Priority-based task queuing
- Task progress/cancellation

### Non-Functional
- Thread-safe agent registration and task assignment
- O(1) agent lookup by ID, O(C) capability-based lookup (C = agents with capability)
- Heartbeat-based health monitoring
- Idempotent task execution (safe retries)
- Region-aware delegation (minimize cross-region latency)

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────────────────────────────────────────────────────────┐
│                       WorkflowManager (Mediator)                      │
│  - registry: AgentRegistry                                           │
│  - delegationStrategy: DelegationStrategy                            │
│  - taskQueue: BlockingQueue<Task>                                    │
│  - submitTask(task) → TaskResult                                     │
│  - sendMessage(from, to, message) → Response                        │
│  - broadcastMessage(from, message)                                   │
│  - onAgentFailed(agentId) → reassign tasks                          │
└──────────────┬───────────────────────────────────────────────────────┘
               │ coordinates
     ┌─────────┼──────────┬───────────────┬──────────────┐
     ▼         ▼          ▼               ▼              ▼
┌──────────┐ ┌───────────────┐ ┌────────────────┐ ┌──────────────────┐
│  Agent   │ │ AgentRegistry │ │     Task       │ │    Message       │
│          │ │               │ │  (Command)     │ │                  │
│ - id     │ │ - agents: Map │ ├────────────────┤ │ - id             │
│ - name   │ │ - byCapability│ │ - id           │ │ - fromAgentId    │
│ - state  │ │   : Map<C,Set>│ │ - type         │ │ - toAgentId      │
│ - region │ │ - byRegion    │ │ - payload      │ │ - type (REQ/RESP)│
│ - capab- │ │   : Map<R,Set>│ │ - status       │ │ - payload        │
│   ilities│ │ - observers   │ │ - assignedTo   │ │ - correlationId  │
│ - tasks  │ │               │ │ - retryCount   │ │ - async: boolean │
│ - execute│ │ - register()  │ │ - maxRetries   │ └──────────────────┘
│   (task) │ │ - deregister()│ │ - result       │
│ - receive│ │ - findBy...() │ │ - execute()    │ ┌──────────────────┐
│   Message│ │ - healthCheck │ │ - onComplete() │ │DelegationStrategy│
│ - heart- │ └───────────────┘ └────────────────┘ │  (interface)     │
│   beat() │                                      ├──────────────────┤
└──────────┘                                      │CapabilityMatch   │
                                                  │LeastLoaded       │
   ┌────────────────┐                             │RegionAware       │
   │  AgentState     │                             │CompositeStrategy │
   │  (interface)    │                             └──────────────────┘
   ├────────────────┤
   │ IdleState      │
   │ BusyState      │
   │ FailedState    │
   │ RecoveringState│
   └────────────────┘
```

### Class: Agent
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique agent ID ("agent-001") |
| name | String | "TextAnalyzer", "CodeGenerator" |
| capabilities | Set\<String\> | {"text-analysis", "summarization"} |
| region | String | "us-east-1", "eu-west-1" |
| state | AgentState | Current lifecycle state (State Pattern) |
| currentTasks | List\<Task\> | Tasks currently assigned |
| maxConcurrentTasks | int | How many tasks can run simultaneously |
| lastHeartbeat | long | For health monitoring |
| metadata | Map\<String, String\> | Custom key-value pairs |

### Class: Task (Command Pattern)
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique task ID |
| type | String | "analyze-text", "generate-code" |
| requiredCapability | String | Which capability is needed |
| payload | Map\<String, Object\> | Input data for the task |
| status | TaskStatus | PENDING, ASSIGNED, RUNNING, COMPLETED, FAILED, CANCELLED |
| assignedAgentId | String | Which agent is executing this |
| parentTaskId | String | For sub-task tracking (null if root) |
| retryCount | int | How many times retried |
| maxRetries | int | Max retries before giving up |
| priority | int | Higher = more urgent |
| result | Object | Task output after completion |
| createdAt, startedAt, completedAt | long | Timestamps |

### Class: Message
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique message ID |
| fromAgentId | String | Sender agent |
| toAgentId | String | Receiver agent (null for broadcast) |
| type | MessageType | REQUEST, RESPONSE, EVENT, HEARTBEAT |
| payload | Object | Message content |
| correlationId | String | Links request to response |
| async | boolean | Fire-and-forget vs wait-for-response |

### Enum: TaskStatus
```
PENDING    → Waiting in queue for assignment
ASSIGNED   → Assigned to agent, not yet started
RUNNING    → Agent is executing the task
COMPLETED  → Successfully finished
FAILED     → Failed after max retries
CANCELLED  → Cancelled by user or system
```

### Agent States (State Pattern)
```
         register()           assignTask()
  ┌──────────┐ ──────────→ ┌──────────┐
  │   IDLE    │              │   BUSY    │
  └────┬─────┘ ←──────────  └────┬─────┘
       │      taskComplete()     │
       │                         │ failure detected
       │   deregister()          ▼
       │ ←──────────────── ┌──────────┐
       │                   │  FAILED   │
       │                   └────┬─────┘
       │                        │ recover()
       │                        ▼
       │                   ┌────────────┐
       │ ←──────────────── │ RECOVERING │
       │   recovery done   └────────────┘
```

---

## 3️⃣ API Design

```java
class WorkflowManager {
    /** Submit a task for execution. Delegates to best available agent. */
    CompletableFuture<TaskResult> submitTask(Task task);
    
    /** Submit a workflow (DAG of tasks with dependencies). */
    CompletableFuture<WorkflowResult> submitWorkflow(List<Task> tasks, Map<String, List<String>> dependencies);
    
    /** Send a sync message from one agent to another. Blocks until response. */
    Message sendMessage(String fromAgentId, String toAgentId, Object payload);
    
    /** Send an async message (fire-and-forget). */
    void sendAsyncMessage(String fromAgentId, String toAgentId, Object payload);
    
    /** Broadcast a message to all agents (or agents with a specific capability). */
    void broadcastMessage(String fromAgentId, Object payload, String capability);
    
    /** Register a new agent dynamically. */
    void registerAgent(Agent agent);
    
    /** Deregister an agent (gracefully — reassign its tasks). */
    void deregisterAgent(String agentId);
    
    /** Set task delegation strategy. */
    void setDelegationStrategy(DelegationStrategy strategy);
    
    /** Get system-wide status. */
    SystemStatus getStatus();
}

class Agent {
    /** Execute a task. Called by WorkflowManager. */
    TaskResult execute(Task task);
    
    /** Receive a message from another agent via WorkflowManager. */
    Message receiveMessage(Message message);
    
    /** Delegate a sub-task to another agent (goes through WorkflowManager). */
    CompletableFuture<TaskResult> delegateSubTask(Task subTask);
    
    /** Send heartbeat to registry. */
    void heartbeat();
    
    /** Query current state. */
    AgentState getState();
    
    /** Get current load (tasks / maxConcurrent). */
    double getLoad();
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Task Submission + Delegation

```
User submits: Task("analyze-report", capability="text-analysis", payload={report: "..."})

WorkflowManager.submitTask(task):
  │
  ├─ Step 1: Find suitable agent
  │   delegationStrategy.selectAgent(task, registry):
  │     ├─ CapabilityMatch: registry.findByCapability("text-analysis")
  │     │   → [Agent-A (us-east, load=0.2), Agent-B (eu-west, load=0.8)]
  │     ├─ LeastLoaded: pick Agent-A (load=0.2 < 0.8)
  │     └─ Return: Agent-A
  │
  ├─ Step 2: Assign task to Agent-A
  │   ├─ task.status = ASSIGNED
  │   ├─ task.assignedAgentId = "Agent-A"
  │   ├─ Agent-A.state: IDLE → BUSY
  │   └─ Agent-A.currentTasks.add(task)
  │
  ├─ Step 3: Agent-A executes task
  │   ├─ task.status = RUNNING
  │   ├─ Agent-A.execute(task):
  │   │   ├─ Analyze report text
  │   │   ├─ Generate summary
  │   │   └─ Return: TaskResult(success, {summary: "Report shows..."})
  │   │
  │   ├─ task.status = COMPLETED
  │   ├─ Agent-A.currentTasks.remove(task)
  │   └─ Agent-A.state: BUSY → IDLE (if no more tasks)
  │
  └─ Return: TaskResult(success, summary)
```

### Scenario 2: Sub-Task Delegation (Agent-to-Agent)

```
Agent-A receives task: "Generate comprehensive report"
Agent-A can analyze text but needs data from a database.

Agent-A.execute(task):
  │
  ├─ Step 1: Agent-A creates sub-task
  │   subTask = Task("fetch-data", capability="data-retrieval", payload={query: "..."})
  │   subTask.parentTaskId = task.id
  │
  ├─ Step 2: Agent-A delegates via WorkflowManager
  │   Agent-A.delegateSubTask(subTask)
  │     → WorkflowManager.submitTask(subTask)
  │       → DelegationStrategy finds Agent-C (has "data-retrieval" capability)
  │       → Agent-C.execute(subTask) → data result
  │
  ├─ Step 3: Agent-A receives sub-task result
  │   data = subTask.result
  │
  ├─ Step 4: Agent-A completes original task using data
  │   analysis = analyzeText(task.payload.report, data)
  │   return TaskResult(success, analysis)
  │
  └─ Parent task completed with combined result

  ✅ Agent-A doesn't know about Agent-C — Mediator handles routing
  ✅ Sub-task tracked via parentTaskId for full lineage
```

### Scenario 3: Sync vs Async Messaging

```
=== SYNCHRONOUS (Request/Response) ===
Agent-A needs data from Agent-B to continue processing:

  Agent-A → WorkflowManager.sendMessage("A", "B", {query: "user_count"})
    │
    ├─ WorkflowManager routes to Agent-B
    ├─ Agent-B.receiveMessage(msg) → processes → returns Response(42)
    ├─ WorkflowManager returns Response to Agent-A
    └─ Agent-A continues with value 42

  ⏱️ Agent-A BLOCKS until Agent-B responds (or timeout)

=== ASYNCHRONOUS (Fire-and-Forget) ===
Agent-A notifies all agents about a data update:

  Agent-A → WorkflowManager.broadcastMessage("A", {event: "data-updated"}, null)
    │
    ├─ WorkflowManager puts message in each agent's inbox (BlockingQueue)
    ├─ Agent-B processes asynchronously whenever ready
    ├─ Agent-C processes asynchronously whenever ready
    └─ Agent-A continues immediately (non-blocking)

  🚀 Agent-A doesn't wait — messages queued for eventual delivery
```

### Scenario 4: Agent Failure + Task Reassignment

```
Agent-B is executing Task-5, then crashes (no heartbeat for 30s):

Health Monitor:
  │
  ├─ Detect: Agent-B missed 3 heartbeats (10s interval × 3 = 30s)
  ├─ Mark: Agent-B.state → FAILED
  ├─ Notify WorkflowManager: onAgentFailed("Agent-B")
  │
  ├─ WorkflowManager.onAgentFailed("Agent-B"):
  │   ├─ Get Agent-B's assigned tasks: [Task-5]
  │   │
  │   ├─ For each task:
  │   │   ├─ Task-5.retryCount++ (now 1, max 3)
  │   │   ├─ Task-5.status = PENDING
  │   │   ├─ Task-5.assignedAgentId = null
  │   │   │
  │   │   ├─ Re-delegate: delegationStrategy.selectAgent(Task-5, registry)
  │   │   │   ├─ Agent-B excluded (FAILED)
  │   │   │   ├─ Agent-A has "text-analysis" capability → selected
  │   │   │   └─ Task-5 assigned to Agent-A
  │   │   │
  │   │   └─ Agent-A.execute(Task-5) → continues from checkpoint (if idempotent)
  │   │
  │   └─ Agent-B removed from active rotation (stays in registry as FAILED)
  │
  └─ Later: Agent-B recovers → state: FAILED → RECOVERING → IDLE

  ✅ Task-5 reassigned within seconds of failure detection
  ✅ Agent-B can rejoin when healthy (re-register or recover)
```

### Scenario 5: Region-Aware Delegation

```
Agents:
  Agent-A: us-east-1, capability: "text-analysis", load: 0.3
  Agent-B: eu-west-1, capability: "text-analysis", load: 0.2
  Agent-C: us-east-1, capability: "text-analysis", load: 0.5

Task submitted from us-east-1 region:

RegionAwareDelegation.selectAgent(task, registry):
  │
  ├─ Step 1: Filter by capability → [A, B, C]
  ├─ Step 2: Prefer same region (us-east-1) → [A (0.3), C (0.5)]
  ├─ Step 3: Among same-region, pick least loaded → Agent-A (0.3)
  ├─ Step 4: If no same-region agent available → fallback to cross-region (Agent-B)
  └─ Return: Agent-A

  ✅ Minimizes cross-region latency
  ✅ Falls back to cross-region only when necessary
  ✅ Load balancing within same region
```

### Scenario 6: Dynamic Agent Registration

```
System running with Agent-A, Agent-B.
New Agent-D joins (region: ap-south-1, capability: "translation"):

WorkflowManager.registerAgent(Agent-D):
  │
  ├─ AgentRegistry.register(Agent-D):
  │   ├─ agents["Agent-D"] = Agent-D
  │   ├─ byCapability["translation"].add("Agent-D")
  │   ├─ byRegion["ap-south-1"].add("Agent-D")
  │   └─ Agent-D.state = IDLE
  │
  ├─ Notify observers: onAgentRegistered(Agent-D)
  │   └─ WorkflowManager checks pending tasks:
  │       ├─ Any PENDING tasks needing "translation"?
  │       ├─ Task-7 needs "translation" — was PENDING (no agent available!)
  │       └─ Assign Task-7 to Agent-D immediately
  │
  └─ Agent-D starts processing Task-7

  ✅ New capability instantly available
  ✅ Backlogged tasks assigned immediately on new agent registration
```

---

## 5️⃣ Design + Implementation

### Agent (Autonomous Unit)

```java
// ==================== AGENT ====================
// 💬 "Agent is the autonomous unit. It has state (State Pattern), capabilities,
//    and can execute tasks. It doesn't know about other agents — communicates
//    via WorkflowManager (Mediator Pattern)."

class Agent {
    private final String id;
    private final String name;
    private final Set<String> capabilities;
    private final String region;
    private final int maxConcurrentTasks;
    private AgentState state;
    private final ConcurrentLinkedQueue<Task> currentTasks;
    private final BlockingQueue<Message> inbox;  // async messages
    private volatile long lastHeartbeat;
    private WorkflowManager workflowManager;  // mediator reference
    
    public Agent(String id, String name, Set<String> capabilities, String region, int maxConcurrent) {
        this.id = id;
        this.name = name;
        this.capabilities = new HashSet<>(capabilities);
        this.region = region;
        this.maxConcurrentTasks = maxConcurrent;
        this.state = new IdleState();
        this.currentTasks = new ConcurrentLinkedQueue<>();
        this.inbox = new LinkedBlockingQueue<>();
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    /** Execute a task. Called by WorkflowManager after delegation. */
    public TaskResult execute(Task task) {
        state.onTaskAssigned(this, task);
        currentTasks.add(task);
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(System.currentTimeMillis());
        
        try {
            // Simulate task execution — in production, this is the agent's core logic
            Object result = processTask(task);
            task.setResult(result);
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(System.currentTimeMillis());
            
            System.out.println("    ✅ " + name + " completed task: " + task.getId());
            return new TaskResult(task.getId(), true, result);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            System.out.println("    ❌ " + name + " failed task: " + task.getId() + " — " + e.getMessage());
            return new TaskResult(task.getId(), false, e.getMessage());
        } finally {
            currentTasks.remove(task);
            if (currentTasks.isEmpty()) {
                state = new IdleState();
            }
        }
    }
    
    /** Process the task — override in concrete agent implementations. */
    protected Object processTask(Task task) {
        // Default: simulate work
        return "Processed by " + name + ": " + task.getType();
    }
    
    /** Receive a sync message — return response immediately. */
    public Message receiveMessage(Message message) {
        System.out.println("    📨 " + name + " received: " + message.getPayload());
        Object response = handleMessage(message);
        return Message.response(message, id, response);
    }
    
    /** Handle message — override for custom logic. */
    protected Object handleMessage(Message message) {
        return "ACK from " + name;
    }
    
    /** Delegate a sub-task to another agent via the mediator. */
    public CompletableFuture<TaskResult> delegateSubTask(Task subTask) {
        subTask.setParentTaskId(currentTasks.peek() != null ? currentTasks.peek().getId() : null);
        return workflowManager.submitTask(subTask);
    }
    
    /** Heartbeat — update timestamp. */
    public void heartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    /** Check if agent has a specific capability. */
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }
    
    /** Current load: currentTasks / maxConcurrent (0.0 to 1.0). */
    public double getLoad() {
        return (double) currentTasks.size() / maxConcurrentTasks;
    }
    
    /** Can accept more tasks? */
    public boolean canAcceptTask() {
        return currentTasks.size() < maxConcurrentTasks && state.canAcceptTask();
    }
    
    // Getters
    public String getId()                  { return id; }
    public String getName()                { return name; }
    public Set<String> getCapabilities()   { return Collections.unmodifiableSet(capabilities); }
    public String getRegion()              { return region; }
    public AgentState getState()           { return state; }
    public void setState(AgentState s)     { this.state = s; }
    public long getLastHeartbeat()         { return lastHeartbeat; }
    public int getTaskCount()              { return currentTasks.size(); }
    void setWorkflowManager(WorkflowManager wm) { this.workflowManager = wm; }
    
    @Override
    public String toString() {
        return String.format("Agent[%s, %s, %s, load=%.0f%%, %s]",
            id, name, region, getLoad() * 100, state.getName());
    }
}
```

### Agent State (State Pattern)

```java
// ==================== AGENT STATE ====================
// 💬 "State Pattern because agent behavior changes per state:
//    - IDLE can accept tasks, BUSY might queue them, FAILED rejects them.
//    - State transitions are enforced — no illegal jumps."

interface AgentState {
    void onTaskAssigned(Agent agent, Task task);
    void onTaskCompleted(Agent agent, Task task);
    void onFailure(Agent agent, Exception e);
    void onRecover(Agent agent);
    boolean canAcceptTask();
    String getName();
}

class IdleState implements AgentState {
    @Override
    public void onTaskAssigned(Agent agent, Task task) {
        agent.setState(new BusyState());
        System.out.println("    🔄 " + agent.getName() + ": IDLE → BUSY");
    }
    
    @Override
    public void onTaskCompleted(Agent agent, Task task) { /* already idle */ }
    
    @Override
    public void onFailure(Agent agent, Exception e) {
        agent.setState(new FailedState(e.getMessage()));
    }
    
    @Override
    public void onRecover(Agent agent) { /* already idle */ }
    
    @Override
    public boolean canAcceptTask() { return true; }
    
    @Override
    public String getName() { return "IDLE"; }
}

class BusyState implements AgentState {
    @Override
    public void onTaskAssigned(Agent agent, Task task) {
        // Can accept more if under maxConcurrent — stay BUSY
        if (!agent.canAcceptTask()) {
            throw new IllegalStateException(agent.getName() + " at max capacity");
        }
    }
    
    @Override
    public void onTaskCompleted(Agent agent, Task task) {
        if (agent.getTaskCount() == 0) {
            agent.setState(new IdleState());
            System.out.println("    🔄 " + agent.getName() + ": BUSY → IDLE");
        }
    }
    
    @Override
    public void onFailure(Agent agent, Exception e) {
        agent.setState(new FailedState(e.getMessage()));
        System.out.println("    🔄 " + agent.getName() + ": BUSY → FAILED");
    }
    
    @Override
    public void onRecover(Agent agent) { /* not applicable in BUSY */ }
    
    @Override
    public boolean canAcceptTask() { return true; }  // up to maxConcurrent
    
    @Override
    public String getName() { return "BUSY"; }
}

class FailedState implements AgentState {
    private final String reason;
    
    public FailedState(String reason) { this.reason = reason; }
    
    @Override
    public void onTaskAssigned(Agent agent, Task task) {
        throw new IllegalStateException("Cannot assign task to FAILED agent: " + agent.getName());
    }
    
    @Override
    public void onTaskCompleted(Agent agent, Task task) { /* n/a */ }
    
    @Override
    public void onFailure(Agent agent, Exception e) { /* already failed */ }
    
    @Override
    public void onRecover(Agent agent) {
        agent.setState(new RecoveringState());
        System.out.println("    🔄 " + agent.getName() + ": FAILED → RECOVERING");
    }
    
    @Override
    public boolean canAcceptTask() { return false; }
    
    @Override
    public String getName() { return "FAILED(" + reason + ")"; }
}

class RecoveringState implements AgentState {
    @Override
    public void onTaskAssigned(Agent agent, Task task) {
        throw new IllegalStateException("Agent recovering, cannot accept tasks yet");
    }
    
    @Override
    public void onTaskCompleted(Agent agent, Task task) {
        // Recovery complete — go IDLE
        agent.setState(new IdleState());
        System.out.println("    🔄 " + agent.getName() + ": RECOVERING → IDLE");
    }
    
    @Override
    public void onFailure(Agent agent, Exception e) {
        agent.setState(new FailedState("Recovery failed: " + e.getMessage()));
    }
    
    @Override
    public void onRecover(Agent agent) { /* already recovering */ }
    
    @Override
    public boolean canAcceptTask() { return false; }
    
    @Override
    public String getName() { return "RECOVERING"; }
}
```

### Task (Command Pattern)

```java
// ==================== TASK (COMMAND PATTERN) ====================
// 💬 "Command Pattern: Task is a first-class object that can be queued, retried,
//    delegated, tracked, and cancelled. Status transitions are explicit."

class Task {
    private final String id;
    private final String type;
    private final String requiredCapability;
    private final Map<String, Object> payload;
    private TaskStatus status;
    private String assignedAgentId;
    private String parentTaskId;     // null if root task
    private int retryCount;
    private final int maxRetries;
    private final int priority;      // higher = more urgent
    private Object result;
    private long createdAt, startedAt, completedAt;
    
    public Task(String type, String requiredCapability, Map<String, Object> payload) {
        this(type, requiredCapability, payload, 3, 0);
    }
    
    public Task(String type, String requiredCapability, Map<String, Object> payload,
                int maxRetries, int priority) {
        this.id = "TASK-" + UUID.randomUUID().toString().substring(0, 8);
        this.type = type;
        this.requiredCapability = requiredCapability;
        this.payload = new HashMap<>(payload);
        this.status = TaskStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = maxRetries;
        this.priority = priority;
        this.createdAt = System.currentTimeMillis();
    }
    
    public boolean canRetry()  { return retryCount < maxRetries; }
    public void incrementRetry() { retryCount++; }
    
    // Getters + Setters...
    public String getId()                 { return id; }
    public String getType()               { return type; }
    public String getRequiredCapability() { return requiredCapability; }
    public TaskStatus getStatus()         { return status; }
    public void setStatus(TaskStatus s)   { this.status = s; }
    public String getAssignedAgentId()    { return assignedAgentId; }
    public void setAssignedAgentId(String a) { this.assignedAgentId = a; }
    public String getParentTaskId()       { return parentTaskId; }
    public void setParentTaskId(String p) { this.parentTaskId = p; }
    public Object getResult()             { return result; }
    public void setResult(Object r)       { this.result = r; }
    public int getPriority()              { return priority; }
    public void setStartedAt(long t)      { this.startedAt = t; }
    public void setCompletedAt(long t)    { this.completedAt = t; }
}

enum TaskStatus {
    PENDING, ASSIGNED, RUNNING, COMPLETED, FAILED, CANCELLED
}
```

### Message

```java
class Message {
    private final String id;
    private final String fromAgentId;
    private final String toAgentId;
    private final MessageType type;
    private final Object payload;
    private final String correlationId;
    private final boolean async;
    private final long timestamp;
    
    public Message(String from, String to, MessageType type, Object payload, boolean async) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.fromAgentId = from;
        this.toAgentId = to;
        this.type = type;
        this.payload = payload;
        this.correlationId = id;
        this.async = async;
        this.timestamp = System.currentTimeMillis();
    }
    
    public static Message request(String from, String to, Object payload) {
        return new Message(from, to, MessageType.REQUEST, payload, false);
    }
    
    public static Message asyncEvent(String from, Object payload) {
        return new Message(from, null, MessageType.EVENT, payload, true);
    }
    
    public static Message response(Message request, String from, Object payload) {
        Message resp = new Message(from, request.fromAgentId, MessageType.RESPONSE, payload, false);
        return resp;
    }
    
    // Getters...
    public String getFromAgentId() { return fromAgentId; }
    public String getToAgentId()   { return toAgentId; }
    public Object getPayload()     { return payload; }
    public MessageType getType()   { return type; }
    public boolean isAsync()       { return async; }
}

enum MessageType { REQUEST, RESPONSE, EVENT, HEARTBEAT }
```

### AgentRegistry (Observer Pattern)

```java
// ==================== AGENT REGISTRY ====================
// 💬 "Registry tracks agents with multi-index for fast lookup.
//    Observer Pattern: notifies listeners on agent join/leave/fail."

class AgentRegistry {
    private final ConcurrentHashMap<String, Agent> agents;              // id → Agent
    private final ConcurrentHashMap<String, Set<String>> byCapability;  // capability → agent IDs
    private final ConcurrentHashMap<String, Set<String>> byRegion;      // region → agent IDs
    private final List<RegistryObserver> observers;
    
    public AgentRegistry() {
        this.agents = new ConcurrentHashMap<>();
        this.byCapability = new ConcurrentHashMap<>();
        this.byRegion = new ConcurrentHashMap<>();
        this.observers = new CopyOnWriteArrayList<>();
    }
    
    public void register(Agent agent) {
        agents.put(agent.getId(), agent);
        
        // Index by capability
        for (String cap : agent.getCapabilities()) {
            byCapability.computeIfAbsent(cap, k -> ConcurrentHashMap.newKeySet()).add(agent.getId());
        }
        
        // Index by region
        byRegion.computeIfAbsent(agent.getRegion(), k -> ConcurrentHashMap.newKeySet()).add(agent.getId());
        
        // Notify observers
        observers.forEach(o -> o.onAgentRegistered(agent));
        System.out.println("  📥 Registered: " + agent);
    }
    
    public void deregister(String agentId) {
        Agent agent = agents.remove(agentId);
        if (agent == null) return;
        
        // Remove from indexes
        for (String cap : agent.getCapabilities()) {
            Set<String> set = byCapability.get(cap);
            if (set != null) set.remove(agentId);
        }
        Set<String> regionSet = byRegion.get(agent.getRegion());
        if (regionSet != null) regionSet.remove(agentId);
        
        observers.forEach(o -> o.onAgentDeregistered(agent));
        System.out.println("  📤 Deregistered: " + agentId);
    }
    
    /** Find all agents with a given capability that can accept tasks. */
    public List<Agent> findByCapability(String capability) {
        Set<String> ids = byCapability.getOrDefault(capability, Set.of());
        return ids.stream()
            .map(agents::get)
            .filter(Objects::nonNull)
            .filter(Agent::canAcceptTask)
            .collect(Collectors.toList());
    }
    
    /** Find agents in a specific region with a capability. */
    public List<Agent> findByCapabilityAndRegion(String capability, String region) {
        return findByCapability(capability).stream()
            .filter(a -> a.getRegion().equals(region))
            .collect(Collectors.toList());
    }
    
    public Agent getAgent(String id)         { return agents.get(id); }
    public int getActiveAgentCount()         { return agents.size(); }
    public void addObserver(RegistryObserver o) { observers.add(o); }
    
    /** Health check — detect agents with stale heartbeats. */
    public List<Agent> getUnhealthyAgents(long heartbeatTimeoutMs) {
        long cutoff = System.currentTimeMillis() - heartbeatTimeoutMs;
        return agents.values().stream()
            .filter(a -> a.getLastHeartbeat() < cutoff)
            .filter(a -> a.getState().canAcceptTask())  // don't re-flag already failed
            .collect(Collectors.toList());
    }
}

interface RegistryObserver {
    void onAgentRegistered(Agent agent);
    void onAgentDeregistered(Agent agent);
    void onAgentFailed(Agent agent);
}
```

### Delegation Strategy (Strategy Pattern)

```java
// ==================== DELEGATION STRATEGY ====================
// 💬 "Strategy Pattern: How to pick the best agent for a task.
//    Swap strategies without changing WorkflowManager."

interface DelegationStrategy {
    Agent selectAgent(Task task, AgentRegistry registry);
    String getName();
}

// ==================== CAPABILITY MATCH (filter only) ====================
class CapabilityMatchStrategy implements DelegationStrategy {
    @Override
    public Agent selectAgent(Task task, AgentRegistry registry) {
        List<Agent> candidates = registry.findByCapability(task.getRequiredCapability());
        return candidates.isEmpty() ? null : candidates.get(0);
    }
    @Override
    public String getName() { return "CapabilityMatch"; }
}

// ==================== LEAST LOADED ====================
class LeastLoadedStrategy implements DelegationStrategy {
    @Override
    public Agent selectAgent(Task task, AgentRegistry registry) {
        return registry.findByCapability(task.getRequiredCapability()).stream()
            .min(Comparator.comparingDouble(Agent::getLoad))
            .orElse(null);
    }
    @Override
    public String getName() { return "LeastLoaded"; }
}

// ==================== REGION-AWARE ====================
class RegionAwareStrategy implements DelegationStrategy {
    private final String preferredRegion;
    
    public RegionAwareStrategy(String preferredRegion) {
        this.preferredRegion = preferredRegion;
    }
    
    @Override
    public Agent selectAgent(Task task, AgentRegistry registry) {
        // Prefer same region
        List<Agent> sameRegion = registry.findByCapabilityAndRegion(
            task.getRequiredCapability(), preferredRegion);
        if (!sameRegion.isEmpty()) {
            return sameRegion.stream()
                .min(Comparator.comparingDouble(Agent::getLoad))
                .orElse(null);
        }
        // Fallback: any region
        return registry.findByCapability(task.getRequiredCapability()).stream()
            .min(Comparator.comparingDouble(Agent::getLoad))
            .orElse(null);
    }
    @Override
    public String getName() { return "RegionAware(" + preferredRegion + ")"; }
}

// ==================== COMPOSITE (chain multiple strategies) ====================
class CompositeStrategy implements DelegationStrategy {
    private final List<DelegationStrategy> strategies;
    
    public CompositeStrategy(DelegationStrategy... strategies) {
        this.strategies = List.of(strategies);
    }
    
    @Override
    public Agent selectAgent(Task task, AgentRegistry registry) {
        for (DelegationStrategy strategy : strategies) {
            Agent agent = strategy.selectAgent(task, registry);
            if (agent != null) return agent;
        }
        return null;
    }
    @Override
    public String getName() { return "Composite"; }
}
```

### WorkflowManager (Mediator)

```java
// ==================== WORKFLOW MANAGER (MEDIATOR) ====================
// 💬 "Mediator Pattern: All agent communication goes through WorkflowManager.
//    Agents don't know about each other — reduces coupling from N×N to N×1."

class WorkflowManager implements RegistryObserver {
    private final AgentRegistry registry;
    private DelegationStrategy delegationStrategy;
    private final BlockingQueue<Task> pendingTasks;
    private final ScheduledExecutorService healthChecker;
    private final long heartbeatTimeoutMs;
    
    public WorkflowManager(DelegationStrategy strategy) {
        this.registry = new AgentRegistry();
        this.delegationStrategy = strategy;
        this.pendingTasks = new PriorityBlockingQueue<>(100,
            Comparator.comparingInt(Task::getPriority).reversed());  // highest priority first
        this.heartbeatTimeoutMs = 30_000;  // 30 seconds
        
        // Register as observer for agent events
        registry.addObserver(this);
        
        // Start health checker
        this.healthChecker = Executors.newSingleThreadScheduledExecutor();
        healthChecker.scheduleAtFixedRate(this::checkHealth, 10, 10, TimeUnit.SECONDS);
    }
    
    // ===== TASK SUBMISSION =====
    
    public CompletableFuture<TaskResult> submitTask(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            Agent agent = delegationStrategy.selectAgent(task, registry);
            
            if (agent == null) {
                // No agent available — queue for later
                task.setStatus(TaskStatus.PENDING);
                pendingTasks.add(task);
                System.out.println("  ⏳ No agent for '" + task.getRequiredCapability() 
                    + "' — queued: " + task.getId());
                return new TaskResult(task.getId(), false, "No agent available — queued");
            }
            
            task.setAssignedAgentId(agent.getId());
            task.setStatus(TaskStatus.ASSIGNED);
            System.out.println("  📌 Assigned " + task.getId() + " → " + agent.getName());
            
            TaskResult result = agent.execute(task);
            
            if (!result.isSuccess() && task.canRetry()) {
                task.incrementRetry();
                System.out.println("  🔄 Retrying " + task.getId() + " (attempt " + task.getRetryCount() + ")");
                return submitTask(task).join();  // recursive retry
            }
            
            return result;
        });
    }
    
    // ===== MESSAGING =====
    
    public Message sendMessage(String fromId, String toId, Object payload) {
        Agent target = registry.getAgent(toId);
        if (target == null) throw new IllegalArgumentException("Agent not found: " + toId);
        
        Message request = Message.request(fromId, toId, payload);
        return target.receiveMessage(request);
    }
    
    public void sendAsyncMessage(String fromId, String toId, Object payload) {
        CompletableFuture.runAsync(() -> {
            Agent target = registry.getAgent(toId);
            if (target != null) {
                target.receiveMessage(Message.request(fromId, toId, payload));
            }
        });
    }
    
    public void broadcastMessage(String fromId, Object payload, String capability) {
        List<Agent> targets = capability != null
            ? registry.findByCapability(capability)
            : new ArrayList<>(registry.getAllAgents());
        
        targets.forEach(agent ->
            CompletableFuture.runAsync(() -> 
                agent.receiveMessage(Message.asyncEvent(fromId, payload))));
    }
    
    // ===== AGENT MANAGEMENT =====
    
    public void registerAgent(Agent agent) {
        agent.setWorkflowManager(this);
        registry.register(agent);
    }
    
    public void deregisterAgent(String agentId) {
        Agent agent = registry.getAgent(agentId);
        if (agent != null) {
            // Reassign current tasks before removal
            reassignTasks(agent);
            registry.deregister(agentId);
        }
    }
    
    // ===== FAULT TOLERANCE =====
    
    private void checkHealth() {
        List<Agent> unhealthy = registry.getUnhealthyAgents(heartbeatTimeoutMs);
        for (Agent agent : unhealthy) {
            System.out.println("  💀 Agent " + agent.getName() + " unhealthy — reassigning tasks");
            agent.getState().onFailure(agent, new RuntimeException("Heartbeat timeout"));
            onAgentFailed(agent);
        }
    }
    
    private void reassignTasks(Agent failedAgent) {
        // Get all tasks assigned to failed agent and re-queue them
        // (In production: query task store by assignedAgentId)
        System.out.println("  🔄 Reassigning tasks from " + failedAgent.getName());
    }
    
    // ===== OBSERVER CALLBACKS =====
    
    @Override
    public void onAgentRegistered(Agent agent) {
        // Check if any pending tasks can now be assigned
        Task pending = pendingTasks.peek();
        if (pending != null && agent.hasCapability(pending.getRequiredCapability())) {
            pendingTasks.poll();
            submitTask(pending);
        }
    }
    
    @Override
    public void onAgentDeregistered(Agent agent) {
        reassignTasks(agent);
    }
    
    @Override
    public void onAgentFailed(Agent agent) {
        reassignTasks(agent);
    }
    
    public void setDelegationStrategy(DelegationStrategy s) { this.delegationStrategy = s; }
    public AgentRegistry getRegistry() { return registry; }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Design Patterns Applied

| Pattern | Where | Why |
|---------|-------|-----|
| **Mediator** | WorkflowManager | Decouples agents — N agents have N×1 connections (to mediator) instead of N×N (to each other) |
| **Command** | Task class | Tasks are first-class objects: queueable, retryable, delegatable, trackable, cancellable |
| **Observer** | RegistryObserver, WorkflowManager observes AgentRegistry | Decouple agent lifecycle events from reactions (task reassignment, pending task processing) |
| **Strategy** | DelegationStrategy (4 implementations) | Swappable delegation logic without modifying WorkflowManager |
| **State** | AgentState (4 states) | Agent behavior changes per state — IDLE accepts tasks, FAILED rejects them |
| **Composite** | CompositeStrategy chains strategies | Try region-aware first, fallback to least-loaded |
| **Facade** | WorkflowManager public API | Simple interface hiding registry, delegation, messaging, health monitoring complexity |

### Deep Dive 2: Geographic Distribution

```
CHALLENGE: Agents in different regions have latency between them.

SOLUTION: Multi-level delegation strategy:
  1. SAME REGION: Prefer agents in same data center (1-5ms latency)
  2. SAME CONTINENT: Fallback to same continent (20-50ms)
  3. CROSS-CONTINENT: Last resort (100-300ms)

IMPLEMENTATION:
  RegionAwareStrategy with region hierarchy:
    "us-east-1" → same region → "us-west-2" → same continent → "eu-west-1"

NETWORK PARTITION HANDLING:
  - Each region has its own WorkflowManager (regional coordinator)
  - Global WorkflowManager coordinates cross-region tasks
  - If partition detected: regional managers operate independently
  - When partition heals: reconcile state (conflict resolution)

TASK ROUTING:
  - Task metadata includes "originRegion"
  - Strategy checks originRegion first
  - Data-locality tasks (e.g., "process EU customer data") must stay in region (compliance)
```

### Deep Dive 3: Fault Tolerance Design

| Mechanism | Implementation | Benefit |
|-----------|---------------|---------|
| **Heartbeat** | Agents send heartbeat every 10s, manager checks every 10s | Detect failures within 30s |
| **Task retry** | maxRetries per task, exponential backoff | Transient failures recover automatically |
| **Task reassignment** | On agent failure → re-queue tasks with PENDING status | No task is lost |
| **Idempotent tasks** | Task ID used for dedup — reprocessing same task = same result | Safe retries |
| **Agent recovery** | FAILED → RECOVERING → IDLE lifecycle | Agent can rejoin after fix |
| **Pending queue** | Tasks waiting for capability → auto-assigned when agent registers | No backlog lost |
| **Circuit breaker** | If agent fails N tasks in a row → mark FAILED, stop sending tasks | Prevent cascade failures |

### Deep Dive 4: Thread Safety

```java
// ==================== THREAD SAFETY ANALYSIS ====================

// AgentRegistry: ConcurrentHashMap for all indexes
//   - Multiple threads can register/deregister simultaneously
//   - findByCapability() reads don't block writes

// Task Queue: PriorityBlockingQueue
//   - Thread-safe: multiple producers (submit) + single consumer (assign)
//   - Priority ordering: highest priority dequeued first

// Agent.currentTasks: ConcurrentLinkedQueue
//   - Lock-free add/remove for task tracking
//   - Multiple tasks can execute concurrently (up to maxConcurrent)

// Agent.state: volatile reference to AgentState
//   - State transitions visible to all threads immediately

// Observer list: CopyOnWriteArrayList
//   - Safe iteration during notification while allowing dynamic add/remove

// Messaging: CompletableFuture for async operations
//   - Non-blocking async message delivery
//   - Sync messages use join() with timeout
```

### Deep Dive 5: Sync vs Async Communication Trade-offs

| Aspect | Synchronous | Asynchronous |
|--------|------------|--------------|
| **Blocking** | Caller waits for response | Caller continues immediately |
| **Latency** | Adds to total task time | No added latency to caller |
| **Error handling** | Immediate exception/timeout | Must check later (callback, polling) |
| **Use case** | "Get me data I need right now" | "Notify others about an event" |
| **Cross-region** | High latency (100ms+) — avoid | Better for cross-region (latency hidden) |
| **Implementation** | `sendMessage()` → `Message.response()` | `CompletableFuture.runAsync()` + inbox queue |

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| No agent has required capability | Task queued as PENDING, assigned when matching agent registers |
| All agents in region are BUSY | Fallback to cross-region via CompositeStrategy |
| Task fails after max retries | Status = FAILED, escalate to human/alert system |
| Agent crashes mid-task | Heartbeat timeout → reassign task (idempotent) |
| Network partition between regions | Regional managers operate independently, reconcile on heal |
| Agent registered with overlapping capabilities | Appears in multiple byCapability indexes — correct |
| Circular sub-task delegation (A → B → A) | Detect via parentTaskId chain, break cycle with error |
| WorkflowManager itself fails | In production: replicate state, use leader election |
| Task with no requiredCapability | Any agent can execute — select least loaded |
| Agent deregisters while executing task | Complete current task first (graceful), then deregister |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space | Note |
|-----------|------|-------|------|
| registerAgent | O(C) | O(1) | C = capabilities to index |
| deregisterAgent | O(C) | O(1) | Remove from C indexes |
| submitTask | O(A) | O(1) | A = agents with capability (scan for best) |
| sendMessage (sync) | O(1) | O(1) | Direct agent lookup + invoke |
| broadcastMessage | O(A) | O(A) | A = target agents |
| healthCheck | O(N) | O(1) | N = total agents |
| findByCapability | O(A) | O(A) | A = agents with capability |
| **Total space** | — | O(N + T) | N = agents, T = pending tasks |

### Deep Dive 8: Production Enhancements

| Enhancement | Approach | Benefit |
|-------------|----------|---------|
| **Workflow DAG** | Task dependencies as directed acyclic graph, topological sort for execution order | Complex multi-step workflows |
| **Agent pools** | Group agents by capability, scale pools independently | Elastic scaling |
| **Task checkpointing** | Save intermediate state to durable store | Resume after failure without restart |
| **Distributed tracing** | Correlate task + sub-task IDs across agents | Debug complex workflows |
| **Rate limiting** | Token bucket per agent | Prevent overloading slow agents |
| **Agent priority** | Some agents are faster/more reliable → weighted selection | Better performance |
| **Event sourcing** | All state changes as events → reconstruct state from log | Audit trail + replay |
| **Hot standby** | Passive replica agent, ready to take over on failure | Zero-downtime failover |

---

## 📋 Interview Checklist (L63)

- [ ] **Mediator Pattern**: WorkflowManager — agents communicate via mediator, not directly (N×1 not N×N)
- [ ] **Command Pattern**: Task as first-class object — queueable, retryable, delegatable, cancellable
- [ ] **Observer Pattern**: AgentRegistry notifies WorkflowManager on join/leave/fail → auto-reassignment
- [ ] **Strategy Pattern**: DelegationStrategy (CapabilityMatch, LeastLoaded, RegionAware, Composite)
- [ ] **State Pattern**: Agent lifecycle (IDLE → BUSY → FAILED → RECOVERING) with enforced transitions
- [ ] **Geographic awareness**: Region-aware strategy prefers local agents, falls back to cross-region
- [ ] **Fault tolerance**: Heartbeat monitoring, task retry with max attempts, automatic reassignment
- [ ] **Thread safety**: ConcurrentHashMap, PriorityBlockingQueue, CopyOnWriteArrayList, CompletableFuture
- [ ] **Sync vs Async**: sendMessage (blocking) vs sendAsyncMessage (fire-and-forget) with trade-offs
- [ ] **Dynamic agents**: Register/deregister at runtime, pending tasks auto-assigned to new agents
- [ ] **Sub-task delegation**: Agent delegates via mediator, tracked via parentTaskId

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 5 min |
| Core Entities (Agent, Task, Message, Registry, WorkflowManager) | 7 min |
| Agent State Pattern (4 states) | 5 min |
| Delegation Strategy (4 implementations) | 5 min |
| WorkflowManager + Messaging | 10 min |
| Fault tolerance + Health monitoring | 5 min |
| Deep Dives (geo, thread safety, edge cases) | 8 min |
| **Total** | **~45 min** |

### Class Relationship Summary

```
WorkflowManager (Mediator)
  ├── uses → AgentRegistry (Observer subject)
  │            ├── indexes → Agent by ID, capability, region
  │            └── notifies → RegistryObserver on join/leave/fail
  ├── uses → DelegationStrategy (Strategy)
  │            ├── CapabilityMatchStrategy
  │            ├── LeastLoadedStrategy
  │            ├── RegionAwareStrategy
  │            └── CompositeStrategy
  ├── submits → Task (Command)
  │            ├── status: TaskStatus enum
  │            └── parentTaskId for sub-task tracking
  ├── routes → Message
  │            ├── REQUEST (sync)
  │            ├── RESPONSE (sync reply)
  │            ├── EVENT (async broadcast)
  │            └── HEARTBEAT (health)
  └── monitors → Agent health (ScheduledExecutorService)

Agent
  ├── has → AgentState (State Pattern)
  │          ├── IdleState
  │          ├── BusyState
  │          ├── FailedState
  │          └── RecoveringState
  ├── executes → Task
  ├── delegates → sub-Tasks via WorkflowManager
  └── receives → Messages via WorkflowManager
```

See `AgenticWorkflowSystem.java` for full implementation with task delegation, agent failure recovery, messaging, and region-aware routing demos.
