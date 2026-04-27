# 🏗️ LLD / OOP Design Interview Template — Microsoft

> **Time**: 45-60 minutes | **Language**: Java
> **Goal**: Demonstrate clean OOP design, SOLID principles, design patterns, concurrency awareness
> **Framework**: Based on Hello Interview (Requirements → Entities → API → Data Flow → Design → Deep Dives)

---

## ⏱️ Timeline (60 min)

| Phase | Time | What You Do |
|-------|------|-------------|
| **1. Requirements** | 7 min | Clarify scope, list functional + non-functional |
| **2. Core Entities** | 5 min | Identify classes, enums, relationships |
| **3. API / Interface Design** | 5 min | Define public methods / contracts |
| **4. Class Diagram** | 5 min | Draw relationships (whiteboard) |
| **5. Code Core Classes** | 25 min | Write the key classes with patterns |
| **6. Demo + Discussion** | 8 min | Run through scenario, discuss trade-offs |
| **7. Deep Dives** | 5 min | Concurrency, scaling, patterns |

---

## Phase 1️⃣ — Requirements (7 min)

> **Say**: "Before I start designing, let me clarify the requirements and scope."

### 🔑 Clarifying Questions Checklist

#### Functional Scope
- [ ] What are the **core operations**? (park/unpark, send/receive, create/delete?)
- [ ] What **entity types** exist? (vehicle types, user roles, message types?)
- [ ] What are the **business rules**? (pricing, capacity, priority?)
- [ ] Do we need **search / query** functionality?
- [ ] Do we need **history / audit trail**?

#### Non-Functional
- [ ] **Concurrency**: Multiple users simultaneously? (multi-threaded?)
- [ ] **Scale**: How many entities? (100s vs millions?)
- [ ] **Persistence**: In-memory only or need database?
- [ ] **Real-time**: Any real-time notifications or updates?

#### Scope Boundaries
- [ ] What's **in scope** vs **out of scope**?
- [ ] **Payment** processing needed?
- [ ] **Authentication/authorization** needed?
- [ ] **UI/API layer** or just the core domain?

### 📝 Template Output

```
"Let me summarize the requirements:

 FUNCTIONAL (P0 - Must Have):
 1. [Core operation 1] — e.g., Park a vehicle in appropriate spot
 2. [Core operation 2] — e.g., Unpark and calculate fee
 3. [Core operation 3] — e.g., Check availability by type
 4. [Core operation 4] — e.g., Support multiple floors

 FUNCTIONAL (P1 - Nice to Have):
 - [Feature] — mention but defer
 - [Feature] — mention but defer

 NON-FUNCTIONAL:
 - Thread-safe for concurrent operations
 - O(1) or O(log N) for core operations
 - In-memory (no persistence layer for now)

 OUT OF SCOPE:
 - Payment processing
 - UI / REST API layer
 - Database persistence

 Does this scope look right?"
```

---

## Phase 2️⃣ — Core Entities (5 min)

> **Say**: "Let me identify the core entities and their relationships."

### 🧩 Entity Identification Template

Think through these categories:

| Category | Question | Example |
|----------|----------|---------|
| **Primary Entities** | What are the main "things" in the system? | Vehicle, ParkingSpot, Ticket |
| **Enums** | What fixed categories exist? | VehicleType, SpotType, Status |
| **Value Objects** | What immutable data do we pass around? | Address, Money, TimeRange |
| **Managers / Services** | Who coordinates the operations? | ParkingLotManager, BookingService |
| **Strategies** | Are there multiple algorithms for same operation? | PricingStrategy, AllocationStrategy |

### 📝 Entity Template

```
ENUMS:
 - [EnumName]: VALUE_1, VALUE_2, VALUE_3

CORE CLASSES:
 - [ClassName]: [key fields] — [single responsibility]
 - [ClassName]: [key fields] — [single responsibility]

INTERFACES:
 - [InterfaceName]: [key methods] — [what it abstracts]

RELATIONSHIPS:
 - [ClassA] HAS-MANY [ClassB]
 - [ClassC] IMPLEMENTS [InterfaceD]
 - [ClassE] USES [ClassF] (composition)
```

---

## Phase 3️⃣ — API / Interface Design (5 min)

> **Say**: "Let me define the public interfaces — the contract of the system."

### 🔌 Interface Design Template

```java
/**
 * Main entry point for the system.
 * This is what external callers interact with.
 */
public interface [SystemName]Service {
    
    // ===== Core Operations =====
    Result operation1(Input input);          // e.g., Ticket parkVehicle(Vehicle v)
    Result operation2(Input input);          // e.g., Receipt unparkVehicle(String ticketId)
    
    // ===== Query Operations =====
    List<Entity> query1(Filter filter);      // e.g., List<Spot> getAvailableSpots(SpotType type)
    int getCount();                          // e.g., int getAvailableCount()
    
    // ===== Configuration =====
    void configure(Config config);           // e.g., void setStrategy(Strategy s)
}
```

### Design Principles to Mention

| Principle | How to Apply | Say This |
|-----------|-------------|----------|
| **Single Responsibility** | Each class does ONE thing | "Vehicle only holds vehicle data, it doesn't know about parking logic" |
| **Open/Closed** | Extend via interfaces, not modifying | "New vehicle types are added by implementing Vehicle interface, not modifying existing code" |
| **Liskov Substitution** | Subtypes work anywhere parent works | "Any PricingStrategy can be swapped without breaking the system" |
| **Interface Segregation** | Small, focused interfaces | "I'll keep the Strategy interface with just one method" |
| **Dependency Inversion** | Depend on abstractions | "ParkingLot depends on the Strategy interface, not concrete implementations" |

---

## Phase 4️⃣ — Class Diagram (5 min)

> **Say**: "Let me draw the class relationships."

### 📊 Diagram Template (Whiteboard / Text)

```
┌─────────────────────────────────────────────────────┐
│                    <<interface>>                      │
│                  [StrategyInterface]                  │
│              + execute(Input): Result                 │
├─────────────────────────────────────────────────────┤
         ▲                          ▲
         │ implements               │ implements
┌────────┴────────┐     ┌─────────┴──────────┐
│  ConcreteStratA  │     │  ConcreteStratB     │
│ + execute(...)   │     │ + execute(...)      │
└─────────────────┘     └────────────────────┘

┌──────────────────────────────────────────┐
│            [Coordinator/Manager]          │
│  - entityList: List<Entity>              │
│  - strategy: StrategyInterface           │
│  + coreOperation1(Input): Result         │
│  + coreOperation2(Input): Result         │
│  + setStrategy(StrategyInterface): void  │
└──────────────────┬───────────────────────┘
                   │ uses
         ┌─────────┴─────────┐
         │      [Entity]      │
         │  - id: String      │
         │  - status: Enum    │
         │  + getters/setters │
         └───────────────────┘
```

### Key Relationships to Show
- **Inheritance** (▲): IS-A relationships
- **Composition** (◆→): HAS-A (strong ownership, lifecycle tied)
- **Aggregation** (◇→): HAS-A (weak, independent lifecycle)
- **Interface Implementation** (▲ dashed): IMPLEMENTS
- **Dependency** (→ dashed): USES

---

## Phase 5️⃣ — Code Core Classes (25 min)

> **Key**: Write ~150-200 lines of clean, focused code. Quality over quantity.

### 🏗️ Code Order (What to Write & When)

| Order | What | Time | Lines |
|-------|------|------|-------|
| 1 | Enums | 2 min | 10-15 |
| 2 | Core Entity class(es) | 5 min | 30-40 |
| 3 | Strategy Interface | 1 min | 5 |
| 4 | Strategy Implementation 1 | 4 min | 20-25 |
| 5 | Strategy Implementation 2 | 4 min | 20-25 |
| 6 | Manager / Coordinator class | 7 min | 50-60 |
| 7 | Demo in main() | 2 min | 20-25 |

### 📝 Full Code Skeleton Template

```java
import java.util.*;
import java.util.concurrent.*;
import java.time.*;

/**
 * INTERVIEW-READY [System Name]
 * Time: 45-60 minutes
 * Patterns: [Strategy, Observer, Singleton, etc.]
 * Focus: [Key design focus]
 */

// ==================== Enums ====================

enum EntityType {
    TYPE_A, TYPE_B, TYPE_C
}

enum Status {
    ACTIVE, INACTIVE, COMPLETED, ERROR
}

// ==================== Core Entity ====================

class Entity {
    private final String id;
    private final EntityType type;
    private Status status;
    private final Instant createdAt;

    public Entity(String id, EntityType type) {
        // ===== Input Validation =====
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
        this.id = id;
        this.type = type;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    // Getters (keep it concise in interview)
    public String getId()        { return id; }
    public EntityType getType()  { return type; }
    public Status getStatus()    { return status; }
    public void setStatus(Status s) { this.status = s; }

    @Override
    public String toString() {
        return "Entity[id=" + id + ", type=" + type + ", status=" + status + "]";
    }
}

// ==================== Strategy Interface ====================

/**
 * Strategy Pattern: Allows swapping algorithms at runtime.
 * Open/Closed Principle: New strategies = new class, no modification.
 */
interface ProcessingStrategy {
    Entity selectEntity(List<Entity> entities);
    String getName();
}

// ==================== Strategy Implementation 1 ====================

class SimpleStrategy implements ProcessingStrategy {
    private int index = 0;

    @Override
    public Entity selectEntity(List<Entity> entities) {
        if (entities.isEmpty()) {
            throw new IllegalStateException("No entities available");
        }
        Entity selected = entities.get(index % entities.size());
        index++;
        return selected;
    }

    @Override
    public String getName() { return "SimpleRoundRobin"; }
}

// ==================== Strategy Implementation 2 ====================

class PriorityStrategy implements ProcessingStrategy {

    @Override
    public Entity selectEntity(List<Entity> entities) {
        if (entities.isEmpty()) {
            throw new IllegalStateException("No entities available");
        }
        // Select based on some priority logic
        return entities.stream()
            .filter(e -> e.getStatus() == Status.ACTIVE)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No active entities"));
    }

    @Override
    public String getName() { return "PriorityBased"; }
}

// ==================== Manager / Coordinator ====================

/**
 * Central coordinator. Uses Strategy pattern for algorithm selection.
 * Thread-safe for concurrent operations.
 */
class SystemManager {
    private final List<Entity> entities;
    private ProcessingStrategy strategy;
    private final Map<String, Entity> entityMap;  // O(1) lookup by ID
    
    // Thread safety
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public SystemManager(ProcessingStrategy strategy) {
        this.entities = new ArrayList<>();
        this.entityMap = new ConcurrentHashMap<>();
        this.strategy = strategy;
    }

    // ===== Core Operation 1: Add =====
    public void addEntity(Entity entity) {
        rwLock.writeLock().lock();
        try {
            if (entityMap.containsKey(entity.getId())) {
                throw new IllegalArgumentException("Duplicate entity: " + entity.getId());
            }
            entities.add(entity);
            entityMap.put(entity.getId(), entity);
            System.out.println("Added: " + entity);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ===== Core Operation 2: Process =====
    public Entity processNext() {
        rwLock.readLock().lock();
        try {
            List<Entity> active = entities.stream()
                .filter(e -> e.getStatus() == Status.ACTIVE)
                .collect(java.util.stream.Collectors.toList());
            return strategy.selectEntity(active);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ===== Core Operation 3: Remove =====
    public Entity removeEntity(String id) {
        rwLock.writeLock().lock();
        try {
            Entity entity = entityMap.remove(id);
            if (entity == null) {
                throw new NoSuchElementException("Entity not found: " + id);
            }
            entities.remove(entity);
            entity.setStatus(Status.COMPLETED);
            System.out.println("Removed: " + entity);
            return entity;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ===== Query =====
    public int getActiveCount() {
        rwLock.readLock().lock();
        try {
            return (int) entities.stream()
                .filter(e -> e.getStatus() == Status.ACTIVE)
                .count();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ===== Strategy Swap =====
    public void setStrategy(ProcessingStrategy newStrategy) {
        this.strategy = newStrategy;
        System.out.println("Strategy changed to: " + newStrategy.getName());
    }
}

// ==================== Demo ====================

public class SystemDemo {
    public static void main(String[] args) {
        // Setup
        SystemManager manager = new SystemManager(new SimpleStrategy());

        // Add entities
        manager.addEntity(new Entity("E1", EntityType.TYPE_A));
        manager.addEntity(new Entity("E2", EntityType.TYPE_B));
        manager.addEntity(new Entity("E3", EntityType.TYPE_A));

        // Process
        System.out.println("Active: " + manager.getActiveCount());
        Entity selected = manager.processNext();
        System.out.println("Selected: " + selected);

        // Switch strategy
        manager.setStrategy(new PriorityStrategy());
        selected = manager.processNext();
        System.out.println("Selected with new strategy: " + selected);

        // Remove
        manager.removeEntity("E1");
        System.out.println("Active after removal: " + manager.getActiveCount());
    }
}
```

---

## Phase 6️⃣ — Design Patterns Reference (Know Which to Use)

### 🎯 Common LLD Patterns for Microsoft Interviews

| Pattern | When to Use | Example Problem |
|---------|------------|-----------------|
| **Strategy** | Multiple algorithms for same operation | Load Balancer, Pricing, Sorting |
| **Observer** | Notify multiple listeners on state change | Notification System, Event Bus |
| **Singleton** | One instance globally (use sparingly) | Logger, Configuration Manager |
| **Factory** | Create objects without specifying class | Vehicle creation, Message creation |
| **State** | Object behavior changes with state | Elevator (MOVING/STOPPED), Order (PLACED/SHIPPED) |
| **Command** | Encapsulate actions as objects | Undo/Redo, Task Queue |
| **Decorator** | Add behavior dynamically | Pizza toppings, Stream wrappers |
| **Builder** | Complex object construction | Query builder, Configuration |
| **Chain of Responsibility** | Pass request through handlers | Middleware, Approval workflow |
| **Template Method** | Algorithm skeleton with customizable steps | Game turn, ETL pipeline |

### 📝 Pattern Declaration Template

```
"For this problem, I'll use:
 1. Strategy Pattern — for [swapping algorithms] because [reason]
 2. Observer Pattern — for [notifications] because [multiple listeners need updates]
 3. Factory Pattern — for [entity creation] because [creation logic varies by type]
 
 This gives us:
 - Open/Closed: Add new strategies/observers without modifying existing code
 - Single Responsibility: Each class has one reason to change
 - Testability: Can mock strategies/observers in unit tests"
```

---

## Phase 7️⃣ — Deep Dives & Discussion (5 min)

### 🧵 Concurrency Discussion

```
"For thread safety, I've used:
 - ReentrantReadWriteLock: Allows concurrent reads, exclusive writes
 - ConcurrentHashMap: For O(1) thread-safe lookups
 
 In production, I'd also consider:
 - Lock striping (partition locks by entity ID range)
 - Optimistic locking with version numbers for database
 - Actor model (each entity processes messages sequentially)"
```

### 📈 Scaling Discussion

```
"To scale this system:
 - Horizontal: Shard entities by ID/type across instances
 - Caching: Add LRU cache for frequently accessed entities
 - Event-driven: Use message queue for async operations
 - Database: Move from in-memory to distributed DB (Redis for cache, Postgres for persistence)"
```

### 🧪 Testing Discussion

```
"For testing, I'd write:
 - Unit tests: Each strategy independently, edge cases
 - Integration tests: Manager + Strategy together
 - Concurrency tests: Multiple threads add/remove/process simultaneously
 - Load tests: Performance under 10K entities"
```

---

## 🎯 Microsoft LLD-Specific Tips

### What Microsoft Interviewers Look For in LLD
1. **SOLID Principles**: Can you design with clean OOP?
2. **Design Patterns**: Do you know and apply them correctly?
3. **Thread Safety**: Can you handle concurrent access?
4. **Clean Code**: Readable, well-named, well-structured
5. **Extensibility**: Can new features be added without rewriting?
6. **Error Handling**: Meaningful exceptions, not silent failures

### 🗣️ Key Phrases to Use

| Moment | Say This |
|--------|----------|
| Starting | "Let me first clarify the requirements and scope..." |
| Entity design | "I'll keep Vehicle as a pure data class — Single Responsibility." |
| Interface design | "I'll use an interface here so we can swap implementations — Open/Closed Principle." |
| Pattern choice | "This is a classic Strategy pattern — multiple algorithms, same interface." |
| Thread safety | "Since multiple threads may call this, I'll use a ReadWriteLock for better concurrency." |
| Error handling | "I'll throw IllegalArgumentException for invalid input — fail fast." |
| Extensibility | "To add a new vehicle type, we just add an enum value and implement the interface — no existing code changes." |
| Trade-offs | "I chose HashMap for O(1) lookup at the cost of O(N) extra space. For this scale, that's the right trade-off." |

---

## 📋 Quick Self-Check Before Finishing

- [ ] Did I state **requirements** clearly (functional + non-functional)?
- [ ] Did I identify the right **design pattern(s)**?
- [ ] Did I follow **SOLID** principles?
- [ ] Is my code **thread-safe** for concurrent access?
- [ ] Did I throw **meaningful exceptions** (not return null)?
- [ ] Did I use **proper encapsulation** (private fields, public methods)?
- [ ] Can new features be added **without modifying** existing code?
- [ ] Did I demonstrate the system works with a **demo/main method**?
- [ ] Can I discuss **scaling** and **production concerns** verbally?

---

## 📚 Common Microsoft LLD Problems (Practice List)

| Problem | Key Patterns | Key Data Structures |
|---------|-------------|-------------------|
| Parking Lot | Strategy, Factory | HashMap, PriorityQueue |
| Elevator System | State, Strategy | PriorityQueue (min/max heap) |
| LRU Cache | - | HashMap + Doubly Linked List |
| Load Balancer | Strategy | List, AtomicInteger |
| Rate Limiter | Strategy | HashMap, Queue (sliding window) |
| File System | Composite | Tree (N-ary) |
| Logger / Logging Framework | Singleton, Chain of Responsibility | Queue, HashMap |
| Task Scheduler | Strategy, Observer | PriorityQueue, ScheduledExecutor |
| Chat System | Observer, Mediator | HashMap, List |
| Hotel Booking | Strategy | HashMap, TreeMap |
| Library Management | Factory | HashMap, List |
| Vending Machine | State | HashMap, enum |
| Online Chess | State, Strategy | 2D Array, enum |
| Notification Service | Observer, Strategy | Queue, HashMap |
| Circuit Breaker | State | AtomicInteger, Timer |
