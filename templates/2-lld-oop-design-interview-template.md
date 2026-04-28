# 🏗️ LLD / OOP Design Interview Template — Microsoft (Discussion-Heavy Edition)

> **Time**: 45-60 minutes | **Language**: Java
> **Goal**: Demonstrate clean OOP design, SOLID principles, design patterns, concurrency awareness
> **Microsoft Focus**: **80% discussion / 20% code** — they care more about WHY than WHAT you code
> **Key Insight**: Microsoft LLD interviews are conversations. Every code choice should open a discussion.

---

## 🚨 MICROSOFT LLD INTERVIEW MINDSET (READ THIS FIRST)

> **Microsoft LLD ≠ "write a complete system"**
> **Microsoft LLD = "discuss design trade-offs while sketching classes"**

### What Microsoft Actually Evaluates

| Weight | What They Evaluate | How to Show It |
|--------|-------------------|----------------|
| **35%** | **Design trade-off discussions** | "I chose X over Y because..." at every decision |
| **25%** | **SOLID + OOP reasoning** | Verbally justify every class/interface split |
| **20%** | **Pattern knowledge + when NOT to use** | "Strategy works here, but Singleton would be overkill because..." |
| **15%** | **Code quality** (clean, not complete) | Write 80-100 lines of core code, not 300 lines |
| **5%** | **Concurrency awareness** | Mention it, show one lock, discuss alternatives |

### The #1 Mistake Candidates Make
```
❌ WRONG: Silently coding for 25 minutes → show finished code → "any questions?"
✅ RIGHT: Code 3-4 lines → stop → explain WHY → invite discussion → continue
```

### Golden Rule: Every 5 Lines of Code = 1 Discussion Point
```
Write: enum VehicleType { BIKE, CAR, TRUCK }
Discuss: "I gave enums fields because later we need size-based pricing. 
          Should VehicleType know its size, or should a separate SizeCalculator? 
          I'll keep it in the enum for now — simpler, and we can extract if needed."
```

---

## ⏱️ Timeline (60 min) — Discussion-Optimized

| Phase | Time | What You Do | Discussion % |
|-------|------|-------------|-------------|
| **1. Requirements** | 8 min | Clarify scope, negotiate trade-offs | 100% talk |
| **2. Core Entities + Relationships** | 7 min | Identify classes, justify why separate | 80% talk, 20% sketch |
| **3. API / Interface Design** | 5 min | Define contracts, discuss alternatives | 90% talk |
| **4. Data Flow Walkthrough** | 5 min | Trace through main operations | 100% talk |
| **5. Code Core Classes** | 20 min | Write key classes WITH discussion pauses | 50% talk, 50% code |
| **6. Deep Dive Discussions** | 15 min | Trade-offs, scaling, concurrency, patterns | 100% talk |

---

## Phase 1️⃣ — Requirements & Scope Negotiation (8 min)

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

### 💬 DISCUSSION POINTS — Requirements Phase

> **These are gold mines for showing product thinking. Use 2-3 per interview.**

| Discussion Topic | What to Say | Why It Impresses |
|-----------------|------------|-----------------|
| **Ambiguity trade-off** | "Should a TRUCK be allowed to take 2 CAR spots, or only TRUCK spots? I'll assume only matching spots for simplicity, but can extend with a SpotAllocation strategy." | Shows you think about edge cases before coding |
| **MVP vs Full** | "For a 45-min interview, I'll build the core park/unpark flow. But I want to design the interfaces so payment/notifications can be plugged in without changing existing code." | Shows extensibility mindset |
| **Consistency vs Availability** | "When two cars try the last spot simultaneously — do we fail-fast (throw exception) or queue? I'll fail-fast with optimistic locking for now." | Shows distributed thinking even in OOP |
| **Push vs Pull** | "When capacity changes, should we push notifications to a dashboard, or should the dashboard poll? I'll use Observer (push) — lower latency, more complex. Pull is simpler but has delay." | Shows you weigh trade-offs |
| **Priority** | "If the lot is nearly full and a VIP arrives, do we reserve spots? This changes our data structure from a simple list to a priority queue." | Shows you think about data structure implications |

---

## Phase 2️⃣ — Core Entities & Relationships (7 min)

> **Say**: "Let me identify the core entities and their relationships."

### 🧩 Entity Identification Template

| Category | Question | Example |
|----------|----------|---------|
| **Primary Entities** | What are the main "things"? | Vehicle, ParkingSpot, Ticket |
| **Enums** | What fixed categories exist? | VehicleType, SpotType, Status |
| **Value Objects** | Immutable data we pass around? | Address, Money, TimeRange |
| **Managers/Services** | Who coordinates? | ParkingLotManager, BookingService |
| **Strategies** | Multiple algorithms for same operation? | PricingStrategy, AllocationStrategy |

### 📝 Entity Template

```
ENUMS:
 - [EnumName]: VALUE_1, VALUE_2, VALUE_3

CORE CLASSES:
 - [ClassName]: [key fields] — [single responsibility]

INTERFACES:
 - [InterfaceName]: [key methods] — [what it abstracts]

RELATIONSHIPS:
 - [ClassA] HAS-MANY [ClassB]
 - [ClassC] IMPLEMENTS [InterfaceD]
 - [ClassE] USES [ClassF] (composition)
```

### 💬 DISCUSSION POINTS — Entity Design

> **Stop after naming each entity and discuss WHY it's separate:**

| Decision | Discussion Script | Principle Demonstrated |
|----------|------------------|----------------------|
| **Why separate Vehicle from ParkingSpot?** | "Vehicle and Spot have different lifecycles — a Vehicle exists before and after parking. Coupling them would violate SRP. Vehicle knows about itself, Spot knows about its location." | **SRP, Low Coupling** |
| **Why a Ticket entity?** | "Ticket captures the relationship between Vehicle and Spot at a point in time. Without it, we'd need to store parking history on either Vehicle or Spot — bloating both. Ticket is the association class." | **Association Class, SRP** |
| **Enum vs Class hierarchy?** | "For VehicleType, I'm using an enum with fields rather than a class hierarchy (Bike extends Vehicle). Why? Because the behavior difference between types is just data (size, price multiplier), not logic. If types had fundamentally different behavior (like a SelfDrivingVehicle needing different park logic), I'd use inheritance." | **Composition over Inheritance** |
| **Interface vs Abstract Class?** | "I'll use an interface for Strategy because implementations share no state. If they shared common helper methods, I'd use an abstract class with template method pattern." | **Interface Segregation** |
| **Mutable vs Immutable?** | "Vehicle is immutable after creation (ID, type don't change). Spot is mutable (occupied status changes). Ticket is semi-immutable — created once, only status can transition ACTIVE→COMPLETED." | **Immutability, Thread Safety** |

### 💬 REUSABLE DISCUSSION: Composition vs Inheritance

> **This comes up in EVERY LLD interview. Memorize this script:**

```
"I prefer composition over inheritance here. Here's why:

 INHERITANCE problems:
 - Java is single-inheritance, so I'd be locked into one hierarchy
 - Fragile base class: changing Vehicle breaks all subtypes
 - Tight coupling: Bike IS-A Vehicle means Bike knows Vehicle's internals
 
 COMPOSITION benefits:
 - Vehicle HAS-A VehicleType (swap types, add new types easily)
 - Loosely coupled: VehicleType can change independently
 - Can compose behaviors: Vehicle + ParkingPermit + InsurancePolicy
 
 Rule of thumb: Use inheritance for IS-A when behavior truly differs (Shape → Circle/Square).
 Use composition for HAS-A when it's just different data/config."
```

### 💬 REUSABLE DISCUSSION: When to Use Enum vs Class Hierarchy vs Interface

```
Use ENUM when:
 - Fixed set of values known at compile time
 - Behavior differences are just data (price, size, priority)
 - Example: VehicleType.BIKE has size=1, VehicleType.TRUCK has size=3

Use CLASS HIERARCHY when:
 - Subtypes have fundamentally different behavior (different method implementations)
 - Example: Shape → Circle.area() uses πr², Rectangle.area() uses l×w

Use INTERFACE when:
 - Multiple unrelated classes need the same contract
 - You want to swap implementations at runtime
 - Example: PricingStrategy → HourlyPricing, FlatPricing, SurgePricing
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

### 💬 DISCUSSION POINTS — API Design

| Decision | Discussion Script | Why It Matters |
|----------|------------------|----------------|
| **Return Ticket vs void** | "parkVehicle returns a Ticket, not void. The caller needs a handle for future operations (unpark, check status). This is the receipt pattern — every write returns a receipt." | **API Usability** |
| **Accept ID vs Object** | "unparkVehicle takes ticketId (String), not the whole Ticket object. Why? Caller might only have the ID (from a QR code). Accepting the whole object forces caller to reconstruct it." | **Minimal Parameter Principle** |
| **Optional vs Exception** | "findById returns Optional<Entity> instead of throwing. Why? Not finding an entity is a normal case, not an error. But parkVehicle throws CapacityFullException because that IS exceptional." | **Error Handling Philosophy** |
| **Why an interface, not concrete class?** | "I'm defining an interface first so tests can mock it, and we can swap implementations. Today it's in-memory, tomorrow it could delegate to a microservice." | **Dependency Inversion, Testability** |

### 💬 REUSABLE DISCUSSION: SOLID Principles (Have Ready for ANY LLD)

| Principle | How to Apply | Say This |
|-----------|-------------|----------|
| **Single Responsibility** | Each class does ONE thing | "Vehicle only holds vehicle data, it doesn't know about parking logic" |
| **Open/Closed** | Extend via interfaces, not modifying | "New vehicle types are added by implementing Vehicle interface, not modifying existing code" |
| **Liskov Substitution** | Subtypes work anywhere parent works | "Any PricingStrategy can be swapped without breaking the system" |
| **Interface Segregation** | Small, focused interfaces | "I'll keep the Strategy interface with just one method — callers shouldn't depend on methods they don't use" |
| **Dependency Inversion** | Depend on abstractions | "ParkingLot depends on the Strategy interface, not concrete implementations" |

---

## Phase 3.5️⃣ — Data Flow Walkthrough (5 min)

> **Say**: "Let me walk through how these objects interact for the main operations."
> **This is the step most candidates skip** — but it shows you understand object collaboration, not just class structure.

### 📋 Data Flow Template

```
OPERATION: [e.g., Park a Vehicle]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SEQUENCE:
  1. Client calls ParkingLotManager.parkVehicle(vehicle)
  2. Manager validates input (null check, duplicate check)
  3. Manager calls Strategy.findSpot(vehicle.getType(), availableSpots)
  4. Strategy returns best available Spot (or throws CapacityFullException)
  5. Manager marks Spot as occupied (atomic operation under write lock)
  6. Manager creates Ticket(spot, vehicle, timestamp)
  7. Manager publishes "VEHICLE_PARKED" event to EventManager
  8. Observers (EmailNotifier, DashboardUpdater) react asynchronously
  9. Manager returns Ticket to client
```

### 💬 DISCUSSION POINTS — Data Flow

| Topic | Discussion Script |
|-------|------------------|
| **Where does validation live?** | "I validate in the Manager, not the Entity. Why? Entity validates its own invariants (non-null ID). Manager validates business rules (duplicate check, capacity). Two different concerns." |
| **Who creates the Ticket?** | "Manager creates the Ticket, not Vehicle or Spot. Neither should know about Tickets — that would couple them. Manager is the coordinator." |
| **Sync vs Async notifications** | "Events are published after the core operation completes. In production, I'd make this async so a slow email service doesn't block parking. For interview, I'll keep it sync but mention the trade-off." |
| **What if Observer throws?** | "I catch exceptions in each observer so one failing listener doesn't break others. This is the resilience pattern — isolation between observers." |

### 🔄 State Transition Diagram Template

```
"Let me document the state transitions for key entities:"

[Entity Name] States:
  ┌────────┐     create      ┌────────┐     process     ┌───────────┐
  │ INITIAL │ ──────────────→ │ ACTIVE  │ ──────────────→ │ COMPLETED │
  └────────┘                  └────┬───┘                  └───────────┘
                                   │
                                   │ cancel
                                   ▼
                              ┌───────────┐
                              │ CANCELLED  │
                              └───────────┘

Valid Transitions:
  INITIAL → ACTIVE     (on creation)
  ACTIVE → COMPLETED   (on success)
  ACTIVE → CANCELLED   (on cancel)
  
Invalid Transitions (throw IllegalStateException):
  COMPLETED → anything
  CANCELLED → anything  (terminal states)
```

### 💬 REUSABLE DISCUSSION: State Machines

```
"Any entity with a lifecycle should be modeled as a state machine. Benefits:
 1. Explicit valid transitions — prevents illegal states (no cancelling a delivered order)
 2. Each state encapsulates its own behavior (State pattern)
 3. Easy to add new states without breaking existing ones
 4. Easy to audit — log every transition with timestamp

Should I use an enum with switch statements, or the State pattern?
 - Enum + switch: Simpler, works for ≤5 states with simple transitions
 - State pattern: Better when states have complex behavior (Elevator: IDLE, MOVING_UP, MOVING_DOWN each handle requestFloor() differently)
 
I'll start with enum + validation, and refactor to State pattern if complexity grows."
```

---

## Phase 4️⃣ — Code Core Classes (20 min)

> **Key**: Write ~100-120 lines of clean, focused code. **Pause every 5-10 lines to discuss.**
> **Microsoft cares about code QUALITY, not QUANTITY.**

### 🏗️ Code Order (What to Write & When)

| Order | What | Time | Lines | Discussion Pause |
|-------|------|------|-------|-----------------|
| 1 | Enums (with fields!) | 2 min | 10-15 | "Why I gave enums fields..." |
| 2 | Core Entity class | 5 min | 25-30 | "Why Comparable, why equals/hashCode..." |
| 3 | Strategy Interface + 1 Impl | 4 min | 15-20 | "Why Strategy over if-else..." |
| 4 | Manager/Coordinator | 7 min | 40-50 | "Why ReadWriteLock, why atomic..." |
| 5 | Quick demo in main() | 2 min | 10-15 | "Let me trace through a scenario..." |

### 📝 Code Skeleton Template (With Discussion Pause Points)

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.time.*;
import java.util.stream.Collectors;

// ==================== ENUMS ====================
// 💬 PAUSE: "I'm giving enums fields because VehicleType isn't just a label — 
//    it carries data (priority, display name). This avoids switch statements everywhere."

enum EntityType {
    TYPE_A(1, "Standard"),
    TYPE_B(2, "Premium"),
    TYPE_C(3, "Enterprise");
    
    private final int priority;
    private final String displayName;
    
    EntityType(int priority, String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }
    
    public int getPriority() { return priority; }
    public String getDisplayName() { return displayName; }
    
    public boolean fitsIn(EntityType slot) {
        return this.priority <= slot.priority;
    }
}

enum Status {
    ACTIVE, INACTIVE, COMPLETED, ERROR, CANCELLED;
    
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == ERROR;
    }
}

// ==================== CUSTOM EXCEPTIONS ====================
// 💬 PAUSE: "I create domain-specific exceptions rather than generic RuntimeException.
//    This lets callers handle CapacityFull differently from NotFound — 
//    e.g., CapacityFull might trigger a 'try another lot' flow."

class SystemException extends RuntimeException {
    private final String errorCode;
    public SystemException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}

class CapacityFullException extends SystemException {
    public CapacityFullException(String resource, int capacity) {
        super(resource + " is full (max: " + capacity + ")", "CAPACITY_FULL");
    }
}

class EntityNotFoundException extends SystemException {
    public EntityNotFoundException(String entityId) {
        super("Entity not found: " + entityId, "NOT_FOUND");
    }
}

class DuplicateEntityException extends SystemException {
    public DuplicateEntityException(String entityId) {
        super("Entity already exists: " + entityId, "DUPLICATE");
    }
}

// ==================== CORE ENTITY ====================
// 💬 PAUSE: "Entity implements Comparable for PriorityQueue sorting,
//    overrides equals/hashCode based on ID — required for Set/Map correctness.
//    Status is volatile for thread visibility without locking."

class Entity implements Comparable<Entity> {
    private final String id;
    private final EntityType type;
    private volatile Status status;                      // volatile for thread visibility
    private final Instant createdAt;
    private final AtomicInteger usageCount;              // lock-free counting

    public Entity(String id, EntityType type) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("ID cannot be null/empty");
        Objects.requireNonNull(type, "Type cannot be null");
        this.id = id;
        this.type = type;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.usageCount = new AtomicInteger(0);
    }

    public void incrementUsage() { usageCount.incrementAndGet(); }
    
    public String getId()           { return id; }
    public EntityType getType()     { return type; }
    public Status getStatus()       { return status; }
    public Instant getCreatedAt()   { return createdAt; }
    public int getUsageCount()      { return usageCount.get(); }
    
    public void setStatus(Status s) { 
        if (this.status.isTerminal()) {
            throw new IllegalStateException("Cannot change terminal entity: " + id);
        }
        this.status = s; 
    }

    @Override
    public int compareTo(Entity other) {
        int typeCmp = Integer.compare(this.type.getPriority(), other.type.getPriority());
        return typeCmp != 0 ? typeCmp : this.createdAt.compareTo(other.createdAt);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity)) return false;
        return id.equals(((Entity) o).id);
    }
    
    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return type.getDisplayName() + "[" + id + ", " + status + "]";
    }
}

// ==================== STRATEGY INTERFACE ====================
// 💬 PAUSE: "Strategy pattern here because selection algorithm may change:
//    RoundRobin for even distribution, LeastUsed for fairness, Random for simplicity.
//    New algorithm = new class, zero changes to Manager."

interface ProcessingStrategy {
    Entity selectEntity(List<Entity> entities);
    String getName();
}

class RoundRobinStrategy implements ProcessingStrategy {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public Entity selectEntity(List<Entity> entities) {
        if (entities.isEmpty()) throw new IllegalStateException("No entities available");
        return entities.get(Math.abs(index.getAndIncrement() % entities.size()));
    }
    @Override
    public String getName() { return "RoundRobin"; }
}

class LeastUsedStrategy implements ProcessingStrategy {
    @Override
    public Entity selectEntity(List<Entity> entities) {
        if (entities.isEmpty()) throw new IllegalStateException("No entities available");
        return entities.stream()
            .min(Comparator.comparingInt(Entity::getUsageCount))
            .orElseThrow();
    }
    @Override
    public String getName() { return "LeastUsed"; }
}

// ==================== OBSERVER (Event System) ====================
// 💬 PAUSE: "Observer decouples the event producer from consumers.
//    Manager doesn't know WHO listens — could be email, SMS, dashboard.
//    Adding a new listener = zero changes to Manager."

interface SystemEventListener {
    void onEvent(String eventType, Object data);
}

class EventManager {
    private final Map<String, List<SystemEventListener>> listeners = new ConcurrentHashMap<>();

    public void subscribe(String event, SystemEventListener listener) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void publish(String event, Object data) {
        for (SystemEventListener l : listeners.getOrDefault(event, Collections.emptyList())) {
            try {
                l.onEvent(event, data);
            } catch (Exception e) {
                System.err.println("Observer error: " + e.getMessage());
            }
        }
    }
}

// ==================== MANAGER / COORDINATOR ====================
// 💬 PAUSE: "Manager coordinates everything. ReadWriteLock because reads (availability check)
//    are much more frequent than writes (park/unpark). Multiple readers, single writer."

class SystemManager {
    private final List<Entity> entities = new ArrayList<>();
    private final Map<String, Entity> entityMap = new ConcurrentHashMap<>();
    private volatile ProcessingStrategy strategy;
    private final EventManager events = new EventManager();
    private final int maxCapacity;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final AtomicLong totalOps = new AtomicLong(0);

    public SystemManager(ProcessingStrategy strategy, int maxCapacity) {
        this.strategy = strategy;
        this.maxCapacity = maxCapacity;
    }

    public Entity addEntity(Entity entity) {
        rwLock.writeLock().lock();
        try {
            if (entityMap.containsKey(entity.getId())) throw new DuplicateEntityException(entity.getId());
            if (entities.size() >= maxCapacity) throw new CapacityFullException("System", maxCapacity);
            entities.add(entity);
            entityMap.put(entity.getId(), entity);
            totalOps.incrementAndGet();
            events.publish("ENTITY_ADDED", entity);
            return entity;
        } finally { rwLock.writeLock().unlock(); }
    }

    public Entity processNext() {
        rwLock.readLock().lock();
        try {
            List<Entity> active = entities.stream()
                .filter(e -> e.getStatus() == Status.ACTIVE).collect(Collectors.toList());
            Entity selected = strategy.selectEntity(active);
            selected.incrementUsage();
            events.publish("ENTITY_PROCESSED", selected);
            return selected;
        } finally { rwLock.readLock().unlock(); }
    }

    public Entity removeEntity(String id) {
        rwLock.writeLock().lock();
        try {
            Entity entity = entityMap.remove(id);
            if (entity == null) throw new EntityNotFoundException(id);
            entities.remove(entity);
            entity.setStatus(Status.COMPLETED);
            events.publish("ENTITY_REMOVED", entity);
            return entity;
        } finally { rwLock.writeLock().unlock(); }
    }

    public Optional<Entity> findById(String id) {
        return Optional.ofNullable(entityMap.get(id));
    }

    public int getActiveCount() {
        rwLock.readLock().lock();
        try { return (int) entities.stream().filter(e -> e.getStatus() == Status.ACTIVE).count(); }
        finally { rwLock.readLock().unlock(); }
    }
    
    public void setStrategy(ProcessingStrategy s) { this.strategy = s; }
    public EventManager getEvents() { return events; }
}

// ==================== DEMO ====================
public class SystemDemo {
    public static void main(String[] args) {
        SystemManager manager = new SystemManager(new RoundRobinStrategy(), 100);
        
        manager.getEvents().subscribe("ENTITY_ADDED", (e, d) -> System.out.println("📧 Added: " + d));
        
        manager.addEntity(new Entity("E1", EntityType.TYPE_A));
        manager.addEntity(new Entity("E2", EntityType.TYPE_B));
        
        Entity selected = manager.processNext();
        System.out.println("Selected: " + selected);
        
        manager.setStrategy(new LeastUsedStrategy());  // runtime strategy swap
        selected = manager.processNext();
        System.out.println("LeastUsed selected: " + selected);
        
        manager.findById("E1").ifPresent(e -> System.out.println("Found: " + e));
    }
}
```

### 🔑 What This Code Demonstrates (Use in Discussion)

| Feature | What | Why — Discussion Script |
|---------|------|------------------------|
| **Enums with fields** | `priority`, `fitsIn()`, `isTerminal()` | "Enums are first-class objects in Java. Putting data on enums avoids switch statements scattered across code." |
| **Custom exceptions** | `CapacityFull`, `Duplicate`, `NotFound` | "Generic RuntimeException tells caller nothing. Domain exceptions let callers react differently — retry vs redirect vs error page." |
| **ReadWriteLock** | Not `synchronized` | "synchronized is too coarse — blocks ALL threads. RWLock lets 100 readers check availability simultaneously, only blocking for writes." |
| **AtomicInteger** | `usageCount`, `totalOps` | "For simple counters, AtomicInteger is lock-free (CAS-based). No need to acquire a lock just to increment." |
| **volatile** | `strategy` field | "volatile ensures all threads see the latest strategy reference when we swap at runtime. Without it, a thread might cache a stale reference." |
| **Optional** | `findById` returns Optional | "Returning null is a billion-dollar mistake. Optional forces callers to handle absence explicitly." |
| **Comparable** | `compareTo` on Entity | "If we ever need a PriorityQueue (VIP parking, job scheduling), entities are already sortable. Preparation without over-engineering." |
| **equals/hashCode** | Based on ID only | "If I put entities in a HashSet or as HashMap keys, I need consistent equals/hashCode. ID-based because two entities with same ID ARE the same entity." |
| **Observer** | EventManager pub/sub | "Decouples notification logic. Manager doesn't know or care about email/SMS — it just publishes events. Adding Slack notifications = one new class." |

---

## Phase 5️⃣ — Design Patterns Reference + Discussion Scripts

### 🎯 Pattern Declaration Template (Say This at Start of Coding)

```
"For this problem, I'll use:
 1. Strategy Pattern — for [algorithm selection] because [multiple algorithms, same interface]
 2. Observer Pattern — for [notifications] because [multiple listeners, decoupled]
 3. Factory Pattern — for [object creation] because [creation logic varies by type]
 
 This gives us:
 - Open/Closed: Add new strategies/observers without modifying existing code
 - Single Responsibility: Each class has one reason to change
 - Testability: Can mock strategies/observers in unit tests"
```

### 💬 REUSABLE PATTERN DISCUSSIONS (Memorize These)

#### Strategy Pattern Discussion

```
WHEN: Multiple algorithms for same operation (load balancing, pricing, sorting, allocation)

DISCUSS: "I could use if-else/switch, but Strategy pattern is better because:
 1. Open/Closed: New algorithm = new class, no touching Manager
 2. Runtime swap: Can change algorithm without restart
 3. Testable: Test each strategy in isolation
 4. Combinable: Can compose strategies (PrimaryStrategy → FallbackStrategy)
 
 Trade-off: More classes. For 2 algorithms, if-else might be fine. 
 For 3+, Strategy pays for itself."
```

#### Observer Pattern Discussion

```
WHEN: Multiple objects react to state changes (notifications, UI updates, logging, metrics)

DISCUSS: "Why Observer over direct method calls?
 1. Decoupling: Publisher doesn't know about subscribers
 2. Open/Closed: Add new listener without changing publisher
 3. Dynamic: Subscribe/unsubscribe at runtime
 
 Trade-off: Harder to debug (who's listening?), potential memory leaks 
 if observers aren't unsubscribed. In production, I'd use weak references 
 or explicit lifecycle management.
 
 Sync vs Async: Here I'm doing sync notify. In production with slow observers 
 (email), I'd use an async executor or message queue to not block the caller."
```

#### Factory Pattern Discussion

```
WHEN: Object creation varies by type, and caller shouldn't know concrete classes

DISCUSS: "Factory vs Constructor — when to use which?
 - Simple objects with few params → Constructor directly
 - Creation logic varies by type → Factory
 - Complex objects with many params → Builder
 
 I'm using a registry-based factory (Map<String, Supplier<T>>) rather than 
 switch-case because new types can self-register without modifying the factory."
```

#### State Pattern Discussion

```
WHEN: Object behavior changes based on internal state (elevator, order, circuit breaker)

DISCUSS: "State pattern vs enum + switch:
 - Enum + switch: Simpler for ≤4 states with simple transitions
 - State pattern: When each state has complex behavior
 
 Example: Elevator IDLE handles requestFloor() by starting to move.
 Elevator MOVING_UP handles requestFloor() by adding to queue.
 Completely different behavior per state → State pattern.
 
 Order status PLACED/PAID/SHIPPED is just transition validation → enum is fine."
```

#### Singleton Discussion (When They Ask)

```
DISCUSS: "I generally avoid Singleton. Problems:
 1. Global state → hidden dependencies → hard to test
 2. Violates SRP (manages its own lifecycle AND business logic)
 3. Tight coupling everywhere
 
 When I'd use it: Logger, Configuration Manager, Connection Pool — 
 truly global, stateless or read-heavy resources.
 
 Better alternative: Dependency Injection — create one instance and inject it.
 Same effect, but explicit and testable."
```

#### Command Pattern Discussion

```
WHEN: Encapsulate actions as objects — undo/redo, queuing, logging

DISCUSS: "Command is perfect for undo/redo because each action is an object with 
 execute() and undo(). Two stacks (undo + redo) give us full history.
 
 The key insight: redo stack clears on new action. If I undo 3 times then 
 type something new, those 3 undone actions are gone forever. This matches 
 user expectations from every text editor."
```

#### Decorator Discussion

```
WHEN: Add behavior dynamically without modifying existing classes (layering)

DISCUSS: "Decorator vs Inheritance for adding features:
 - Inheritance: Compile-time, one combination
 - Decorator: Runtime, any combination of layers
 
 Example: DataSource → EncryptionDecorator → CompressionDecorator → LoggingDecorator
 I can stack any combination without 2^N subclasses.
 
 Java uses this: InputStream → BufferedInputStream → GZIPInputStream
 Real world: Middleware in web frameworks (auth → rate limit → logging → handler)"
```

#### Chain of Responsibility Discussion

```
WHEN: Multiple handlers process a request, each deciding to handle or pass

DISCUSS: "Chain of Responsibility vs if-else-if:
 - Chain: Each handler is independent, reorderable, testable separately
 - if-else: All logic in one place, hard to extend
 
 Key design choice: Should handlers always pass to next, or can they short-circuit?
 Auth failure → stop (short-circuit). Logging → always pass.
 
 Real world: Servlet filters, Spring interceptors, Express middleware."
```

### 🧩 Pattern Combination Guide

| Problem Type | Patterns | Why |
|-------------|----------|-----|
| **Parking Lot** | Strategy + Factory + Observer | Strategy: spot allocation, Factory: vehicle creation, Observer: capacity alerts |
| **Elevator** | State + Strategy + Observer | State: elevator mode, Strategy: scheduling, Observer: floor display |
| **Chat** | Observer + Command + Mediator | Observer: message broadcast, Command: message history, Mediator: routing |
| **File System** | Composite + Decorator | Composite: folder/file tree, Decorator: permissions/encryption |
| **Rate Limiter** | Strategy + Chain | Strategy: algorithm (token bucket/sliding window), Chain: middleware pipeline |
| **Text Editor** | Command + Observer | Command: undo/redo, Observer: UI updates |
| **Order Mgmt** | State + Observer + Factory | State: lifecycle, Observer: notifications, Factory: order creation |
| **Logging** | Singleton + Chain + Decorator | Singleton: logger, Chain: level filtering, Decorator: formatting |

---

## Phase 6️⃣ — Deep Dive Discussions (15 min)

> **This is where Microsoft interviews are won or lost. The interviewer will probe you here.**
> **Have 3-4 of these ready. Proactively bring them up even if not asked.**

---

### 🧵 Deep Dive 1: Thread Safety & Concurrency

> **Applies to**: EVERY LLD problem

#### 💬 Discussion Script: Choosing the Right Lock

```
"For concurrency, I need to think about read/write patterns:

 Read-heavy (90%+ reads like availability check):
  → ReadWriteLock — multiple concurrent readers, exclusive writer
  → ConcurrentHashMap for O(1) lookups
  
 Write-heavy (counters, metrics):
  → AtomicInteger/AtomicLong — lock-free CAS operations
  → LongAdder for high-contention counters (better than AtomicLong)

 Single shared resource:
  → synchronized or ReentrantLock
  → tryLock(timeout) for deadlock prevention

 Producer-consumer:
  → BlockingQueue — built-in wait/notify semantics

 I chose ReadWriteLock here because checking availability (read) happens 
 100x more often than parking (write). synchronized would serialize ALL operations."
```

#### 💬 Discussion Script: Race Conditions

```
"The classic race condition in this problem:

 Thread A: finds spot S1 available → about to assign
 Thread B: finds spot S1 available → assigns S1 ← DOUBLE BOOKING!
 Thread A: assigns S1 ← BOTH think they got it

 Fix: The find + assign must be ATOMIC (inside a single write lock).
 This is the 'check-then-act' pattern — always a race condition unless atomic.

 In distributed systems, I'd use:
  - Optimistic locking (version number): Try to update, fail if version changed
  - Pessimistic locking (SELECT FOR UPDATE): Lock the row before reading
  - Redis SETNX: Distributed lock with TTL for auto-release"
```

#### 💬 Discussion Script: Deadlock Prevention

```
"To prevent deadlocks:
 1. Lock ordering: Always acquire locks in same global order (e.g., by entity ID)
 2. Timeout: Use tryLock(5, SECONDS) — detect deadlock, back off and retry
 3. Minimize lock scope: Hold lock for shortest possible time
 4. Avoid nested locks: If unavoidable, document the order
 
 In production, I'd also add:
 - Lock contention metrics (how long threads wait)
 - Deadlock detection thread (JMX ThreadMXBean.findDeadlockedThreads())
 - Circuit breaker: If lock wait > threshold, fail fast"
```

---

### 🚨 Deep Dive 2: Exception Handling & Error Design

> **Applies to**: EVERY LLD problem

#### 💬 Discussion Script: Exception Philosophy

```
"My exception handling philosophy:

 1. FAIL FAST: Validate input at entry point. Don't let bad data travel deep.
    - Objects.requireNonNull() for null checks
    - IllegalArgumentException for bad params
    
 2. DOMAIN EXCEPTIONS: Each error type gets its own exception class:
    - CapacityFullException → caller can redirect to another lot
    - DuplicateEntityException → caller can return idempotent response
    - EntityNotFoundException → caller can return 404
    
 3. NEVER return null: Use Optional<T> for 'might not exist' operations.
    Returning null pushes the burden to every caller (who will forget).
    
 4. EXCEPTION vs RETURN CODE:
    - Exception for truly exceptional cases (capacity full, not found)
    - Return Optional/Result for expected cases (search returns empty)
    
 5. OBSERVER ISOLATION: One failing observer shouldn't crash the system.
    Catch per-observer, log, continue to next."
```

#### 💬 Discussion: Checked vs Unchecked Exceptions

```
"I'm using unchecked (RuntimeException) because:
 - These are programming errors or exceptional business conditions
 - Checked exceptions pollute every method signature
 - Modern Java style (Spring, Guava) prefers unchecked
 
 When I'd use checked:
 - I/O operations where recovery is expected (FileNotFoundException → try another path)
 - Operations where the caller MUST handle the failure (payment → must rollback)"
```

---

### 📈 Deep Dive 3: Scalability & Data Structure Justification

> **Applies to**: EVERY LLD problem — always justify your data structure choices

#### 💬 Discussion Script: Data Structure Choices

| Need | I Chose | Why — Discussion Script |
|------|---------|------------------------|
| Fast lookup by ID | `HashMap` / `ConcurrentHashMap` | "O(1) average lookup. Trade-off: O(N) space, no ordering. Worth it for constant-time access." |
| Sorted + range queries | `TreeMap` | "For calendar slots, I need 'find next available after 3pm' — TreeMap.ceilingKey() is O(log N)." |
| FIFO processing | `ArrayDeque` | "ArrayDeque over LinkedList — cache-friendly (contiguous memory), no node allocation overhead." |
| Priority processing | `PriorityQueue` | "Min-heap for 'process lowest priority first'. O(log N) insert/remove." |
| Uniqueness check | `HashSet` | "O(1) duplicate detection. Essential for idempotency." |
| LRU eviction | `LinkedHashMap` | "Built-in access-order tracking. Override removeEldestEntry() for auto-eviction. O(1) for all ops." |
| Thread-safe map | `ConcurrentHashMap` | "Segment-level locking, not table-level. Better concurrency than Collections.synchronizedMap()." |
| Concurrent counter | `AtomicLong` / `LongAdder` | "LongAdder for high-contention (splits into cells, sums on read). AtomicLong for low-contention." |

#### 💬 Discussion Script: Scaling Beyond In-Memory

```
"Right now this is in-memory. To scale to millions of entities:

 STEP 1 — Repository Pattern (abstract storage):
  Interface EntityRepository { save(), findById(), findByStatus() }
  Swap InMemoryRepository → DatabaseRepository without changing Manager

 STEP 2 — Caching:
  Redis cache-aside: check cache → miss → read DB → populate cache
  TTL-based invalidation for simplicity, pub/sub for real-time

 STEP 3 — Horizontal Scaling:
  Stateless services behind load balancer
  Shard by entity ID (consistent hashing)
  Distributed locks (Redis SETNX) instead of in-process ReadWriteLock

 STEP 4 — Event-Driven:
  Replace sync Observer with Kafka/SQS
  Eventual consistency for notifications
  Dead letter queue for failed events

 STEP 5 — CQRS (if read/write patterns diverge):
  Separate read model (denormalized, fast queries)
  Write model (normalized, strong consistency)
  Sync via events"
```

---

### 🧪 Deep Dive 4: Testing Strategy

> **Applies to**: EVERY LLD problem — shows quality mindset

#### 💬 Discussion Script

```
"My testing approach for this design:

 UNIT TESTS (70%):
  - Each Strategy independently: RoundRobin cycles correctly, LeastUsed picks minimum
  - Entity validation: null ID → IllegalArgumentException, terminal status → IllegalStateException
  - State transitions: valid (ACTIVE → COMPLETED) and invalid (COMPLETED → anything)
  - Factory creates correct types for each input
  - Observer receives events with correct data
  
 INTEGRATION TESTS (20%):
  - Full workflow: add → process → remove → verify state
  - Manager + Strategy + Observer together
  - Error paths: capacity full, duplicate, not found
  
 CONCURRENCY TESTS (10%):
  - 100 threads adding/removing simultaneously → no lost entities
  - CountDownLatch to synchronize start, verify final state
  - No deadlocks (test with timeout)
  
 PROPERTY-BASED TESTING:
  - Invariant: activeCount + completedCount = totalAdded (always)
  - Invariant: no two entities share same ID
  - Invariant: capacity never exceeded"
```

---

### 📊 Deep Dive 5: Monitoring & Production Readiness

> **Applies to**: EVERY LLD — shows you think beyond the interview

#### 💬 Discussion Script

```
"In production, I'd instrument this with:

 METRICS:
  - Operation counts: add/remove/process calls (Counter)
  - Active entity count (Gauge)  
  - Operation latency p50/p99 (Histogram)
  - Error rate by exception type (Counter)
  - Lock contention time (Histogram)

 HEALTH CHECK:
  - /health returns UP/DOWN
  - Capacity > 80% → WARN, 100% → DEGRADED

 LOGGING:
  - Structured JSON with correlation IDs
  - Every state transition logged (audit trail)
  - WARN at 80% capacity, ERROR at full
  
 ALERTING:
  - Error rate > 1% → page on-call
  - Latency p99 > 500ms → investigate
  - Approaching capacity → auto-scale or alert"
```

---

### 💾 Deep Dive 6: Data Integrity & Consistency

> **Applies to**: Booking, Inventory, Parking, any limited-resource system

#### 💬 Discussion Script

```
"For data integrity:

 1. ATOMIC OPERATIONS:
    Find available + assign + create ticket — all inside one write lock.
    If any step fails, nothing changes (no partial state).

 2. IDEMPOTENCY:
    Same request → same result. If vehicle already parked, return existing ticket.
    In distributed: use idempotency key (request UUID) to deduplicate.

 3. NO DOUBLE BOOKING:
    Lock before checking + assigning. Check-then-act under single lock.
    Distributed: Redis SETNX (SET if Not eXists) with TTL.

 4. EVENTUAL CONSISTENCY:
    Notifications can be eventually consistent (async). But the booking 
    itself must be strongly consistent (atomic).

 5. COMPENSATING TRANSACTIONS:
    If notification fails after booking succeeds, don't rollback the booking.
    Retry the notification. Use a dead letter queue for failed retries."
```

---

### 🔄 Deep Dive 7: Extensibility & Future-Proofing

> **Proactively bring this up to show design maturity**

#### 💬 Discussion Script

```
"My design has these extension points:

 1. NEW ALGORITHMS → Add new Strategy class (Open/Closed)
 2. NEW ENTITY TYPES → Add enum value + optional subclass
 3. NEW NOTIFICATIONS → Add new Observer (zero change to publisher)
 4. NEW STATES → Add new State class with its own transitions
 5. NEW STORAGE → Implement Repository interface (InMemory → PostgreSQL)
 6. NEW MIDDLEWARE → Insert handler in Chain of Responsibility

 Key: Every extension is ADDITIVE — we add new classes, never modify existing ones.
 This is the Open/Closed Principle in action."
```

---

## 🔥 UNIVERSAL DISCUSSION TOPICS — Use in ANY LLD Interview

> **These are your secret weapons. Each is a 2-3 minute discussion you can insert into any LLD problem.**

### 💬 Discussion Bank: 20 Reusable Topics

#### Architecture & Design Decisions

| # | Topic | When to Use | 30-Second Script |
|---|-------|-------------|-----------------|
| 1 | **Composition vs Inheritance** | When deciding entity hierarchy | "I prefer composition — Vehicle HAS-A Type rather than Bike EXTENDS Vehicle. More flexible, avoids fragile base class." |
| 2 | **Interface vs Abstract Class** | When creating abstractions | "Interface when no shared state (Strategy). Abstract class when sharing helper methods (Template Method)." |
| 3 | **Enum vs Class hierarchy** | When modeling types | "Enum when types differ by data only. Class hierarchy when types differ by behavior." |
| 4 | **Mutable vs Immutable** | When designing entities | "Immutable objects are inherently thread-safe. I make everything immutable by default, add mutability only when needed." |
| 5 | **Pull vs Push model** | When notifications come up | "Push (Observer): lower latency, more complex. Pull (polling): simpler, higher latency. Push for real-time, pull for batch." |

#### Concurrency & Safety

| # | Topic | When to Use | 30-Second Script |
|---|-------|-------------|-----------------|
| 6 | **synchronized vs ReadWriteLock** | Always | "synchronized is mutual exclusion. RWLock allows concurrent reads. If reads > writes, RWLock is strictly better." |
| 7 | **AtomicInteger vs lock** | For counters/flags | "AtomicInteger is lock-free (CAS loop). No thread suspension, no context switch. Better for simple operations." |
| 8 | **volatile vs synchronized** | For single variable visibility | "volatile guarantees visibility (all threads see latest value). But NOT atomicity. i++ is still not safe with volatile." |
| 9 | **Check-then-act race condition** | Always | "if (available) then assign is a race condition. The check + act MUST be atomic — either inside a lock or using CAS." |
| 10 | **Optimistic vs Pessimistic locking** | For resource contention | "Optimistic: read, modify, check version, retry on conflict. Pessimistic: lock first, modify, unlock. Optimistic wins when conflicts are rare." |

#### Error Handling & Robustness

| # | Topic | When to Use | 30-Second Script |
|---|-------|-------------|-----------------|
| 11 | **Fail-fast vs Fail-safe** | Always | "Fail-fast: validate input immediately, throw on bad data. Prevents bad state from propagating. Always my default." |
| 12 | **Optional vs null** | For lookups | "Return Optional, never null. Forces caller to handle absence. Null is the billion-dollar mistake." |
| 13 | **Custom vs generic exceptions** | When handling errors | "CapacityFull vs RuntimeException — domain exceptions let callers react differently. It's part of the API contract." |
| 14 | **Retry with backoff** | For transient failures | "Exponential backoff (100ms, 200ms, 400ms) with jitter. Prevents thundering herd on recovery." |
| 15 | **Graceful degradation** | For fallback strategies | "Primary strategy fails → fall back to simpler strategy. Better to serve degraded than to error." |

#### Scalability & Production

| # | Topic | When to Use | 30-Second Script |
|---|-------|-------------|-----------------|
| 16 | **Repository pattern** | When storage comes up | "Abstract storage behind an interface. Swap InMemory for DB without changing business logic. Dependency inversion." |
| 17 | **Sync vs Async notifications** | When Observer/events come up | "Sync is simpler but blocks caller. Async (ExecutorService, Kafka) decouples latency. Start sync, extract to async." |
| 18 | **Idempotency** | For create/update operations | "Same request → same result. Use idempotency key to detect duplicates. Essential for retry safety." |
| 19 | **Event Sourcing vs State** | For audit/history needs | "State: store current state only. Event Sourcing: store all events, derive state. Event Sourcing when you need full history." |
| 20 | **Cache-aside vs Write-through** | When caching comes up | "Cache-aside: app manages cache (check cache → miss → read DB → populate). Write-through: cache auto-syncs with DB." |

---

## 📋 Quick Self-Check Before Finishing

- [ ] Did I state **requirements** clearly (functional + non-functional)?
- [ ] Did I identify the right **design pattern(s)** and explain WHY?
- [ ] Did I follow **SOLID** principles and name them explicitly?
- [ ] Is my code **thread-safe** and can I explain my locking choice?
- [ ] Did I throw **meaningful exceptions** (not return null)?
- [ ] Did I discuss at least **3 trade-offs** proactively?
- [ ] Can new features be added **without modifying** existing code?
- [ ] Did I walk through a **data flow** for the main operation?
- [ ] Can I discuss **scaling** and **production concerns**?
- [ ] Did I **invite discussion** at each major decision point?

---

## 🗣️ Key Phrases Cheat Sheet

| Moment | Say This |
|--------|----------|
| **Starting** | "Let me first clarify the requirements and scope..." |
| **Every design decision** | "I chose X over Y because... the trade-off is..." |
| **Entity design** | "Vehicle and Spot are separate because they have different lifecycles — SRP." |
| **Interface creation** | "I'll use an interface so we can swap implementations — Open/Closed Principle." |
| **Pattern choice** | "This is Strategy because we have multiple algorithms for the same operation." |
| **Thread safety** | "ReadWriteLock here because reads are 10x more frequent than writes." |
| **Error handling** | "I throw CapacityFullException, not RuntimeException, so callers can react differently." |
| **Data structure** | "HashMap for O(1) lookup. TreeMap if we need range queries." |
| **Extensibility** | "Adding a new type means one new class — zero changes to existing code." |
| **Production** | "In production I'd add metrics: operation count, latency p99, error rate." |
| **Inviting discussion** | "Would you like me to dig deeper into the concurrency model, or move on to...?" |
| **When stuck** | "Let me think about the trade-offs here... I see two approaches..." |

---

## 📚 Common Microsoft LLD Problems (Practice List)

| Problem | Key Patterns | Key Discussion Points |
|---------|-------------|----------------------|
| Parking Lot | Strategy, Factory | Double-booking prevention, multi-floor scaling, pricing strategies |
| Elevator System | State, Strategy | State machine transitions, scheduling algorithms (SCAN vs LOOK) |
| LRU Cache | - | LinkedHashMap internals, O(1) design, thread-safety, eviction policies |
| Load Balancer | Strategy | Round Robin vs Weighted vs Least Connections, health checking |
| Rate Limiter | Strategy | Token Bucket vs Sliding Window vs Fixed Window, distributed rate limiting |
| File System | Composite | Composite pattern, recursive operations, permissions model |
| Logger | Singleton, Chain | Why Singleton (sometimes), log level filtering, async logging |
| Task Scheduler | Strategy, Observer | Priority queue, thread pool sizing, missed schedule handling |
| Chat System | Observer, Mediator | Message ordering, read receipts, online/offline presence |
| Hotel Booking | Strategy | Overbooking policy, cancellation, calendar range queries (TreeMap) |
| Vending Machine | State | State machine design, payment handling, inventory management |
| Online Chess | State, Strategy | Move validation per piece type, check/checkmate detection |
| Notification Service | Observer, Strategy | Multi-channel delivery, retry policies, user preferences |
| Circuit Breaker | State | CLOSED→OPEN→HALF_OPEN transitions, threshold configuration |
| Text Editor | Command | Undo/redo stacks, cursor management, collaborative editing |

---

## 🎯 30-Second Pre-Interview Warmup

> Read this right before your interview:

```
1. DISCUSS more than you CODE — every decision gets a "because..."
2. Use SOLID names explicitly: "This is Single Responsibility"
3. Show TRADE-OFFS: "I chose X over Y because..."
4. Mention CONCURRENCY even if not asked
5. Write CLEAN code, not COMPLETE code — 100 lines > 300 lines
6. END with: "Here's how this would scale in production..."
7. INVITE discussion: "Would you like me to explore this further?"
```

**Good luck! 🚀**
