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
| **Observer** | Notify multiple listeners on state change | Notification System, Event Bus, Chat |
| **Singleton** | One instance globally (use sparingly) | Logger, Configuration Manager |
| **Factory** | Create objects without specifying class | Vehicle creation, Message creation |
| **State** | Object behavior changes with state | Elevator (MOVING/STOPPED), Order (PLACED/SHIPPED) |
| **Command** | Encapsulate actions as objects | Undo/Redo, Task Queue |
| **Decorator** | Add behavior dynamically | Pizza toppings, Stream wrappers, Logging |
| **Builder** | Complex object construction | Query builder, Configuration |
| **Chain of Responsibility** | Pass request through handlers | Middleware, Approval workflow, Logging |
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

### 🔔 Observer Pattern — Interview-Ready Code

> **Use when**: Multiple objects need to be notified when one object's state changes.
> **Interview problems**: Notification System, Chat System, Event Bus, Stock Price Tracker, Auction Bidding

```java
// ===== Observer Interface =====
interface EventListener {
    void onEvent(String eventType, Object data);
}

// ===== Subject (Event Manager) =====
class EventManager {
    private final Map<String, List<EventListener>> listeners = new ConcurrentHashMap<>();

    public void subscribe(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribe(String eventType, EventListener listener) {
        List<EventListener> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    public void notify(String eventType, Object data) {
        List<EventListener> eventListeners = listeners.getOrDefault(eventType, Collections.emptyList());
        for (EventListener listener : eventListeners) {
            listener.onEvent(eventType, data);
        }
    }
}

// ===== Concrete Observers =====
class EmailNotifier implements EventListener {
    @Override
    public void onEvent(String eventType, Object data) {
        System.out.println("📧 Email: [" + eventType + "] " + data);
    }
}

class SMSNotifier implements EventListener {
    @Override
    public void onEvent(String eventType, Object data) {
        System.out.println("📱 SMS: [" + eventType + "] " + data);
    }
}

class SlackNotifier implements EventListener {
    @Override
    public void onEvent(String eventType, Object data) {
        System.out.println("💬 Slack: [" + eventType + "] " + data);
    }
}

// ===== Usage =====
EventManager events = new EventManager();
events.subscribe("ORDER_PLACED", new EmailNotifier());
events.subscribe("ORDER_PLACED", new SMSNotifier());
events.subscribe("ORDER_SHIPPED", new SlackNotifier());

events.notify("ORDER_PLACED", "Order #123");  // Both email + SMS fire
events.notify("ORDER_SHIPPED", "Order #123"); // Only slack fires
```

**Say this**: "I'm using Observer pattern here because multiple components (email, SMS, Slack) need to react to the same event independently. Adding a new notification channel means just adding a new listener — no existing code changes. Open/Closed principle."

---

### 🏭 Factory Pattern — Interview-Ready Code

> **Use when**: Object creation logic varies by type, and you want to decouple the caller from concrete classes.
> **Interview problems**: Parking Lot (Vehicle types), Messaging (Message types), Shape drawing, Payment processing

```java
// ===== Product Interface =====
interface Notification {
    void send(String to, String message);
    String getType();
}

// ===== Concrete Products =====
class EmailNotification implements Notification {
    @Override
    public void send(String to, String message) {
        System.out.println("Email to " + to + ": " + message);
    }
    @Override
    public String getType() { return "EMAIL"; }
}

class SMSNotification implements Notification {
    @Override
    public void send(String to, String message) {
        System.out.println("SMS to " + to + ": " + message);
    }
    @Override
    public String getType() { return "SMS"; }
}

class PushNotification implements Notification {
    @Override
    public void send(String to, String message) {
        System.out.println("Push to " + to + ": " + message);
    }
    @Override
    public String getType() { return "PUSH"; }
}

// ===== Factory =====
class NotificationFactory {
    public static Notification create(String type) {
        switch (type.toUpperCase()) {
            case "EMAIL": return new EmailNotification();
            case "SMS":   return new SMSNotification();
            case "PUSH":  return new PushNotification();
            default: throw new IllegalArgumentException("Unknown notification type: " + type);
        }
    }
    
    // Alternative: Registry-based factory (more extensible)
    private static final Map<String, Supplier<Notification>> registry = new HashMap<>();
    static {
        registry.put("EMAIL", EmailNotification::new);
        registry.put("SMS", SMSNotification::new);
        registry.put("PUSH", PushNotification::new);
    }
    
    public static Notification createFromRegistry(String type) {
        Supplier<Notification> supplier = registry.get(type.toUpperCase());
        if (supplier == null) throw new IllegalArgumentException("Unknown type: " + type);
        return supplier.get();
    }
}

// ===== Usage =====
Notification notif = NotificationFactory.create("EMAIL");
notif.send("user@example.com", "Your order shipped!");
```

**Say this**: "I'm using Factory to decouple creation logic from usage. The caller doesn't need to know about concrete classes — just asks the factory for the right type. Adding a new notification type means adding one class + one registry entry — zero changes to existing code."

---

### 🔄 State Pattern — Interview-Ready Code

> **Use when**: An object's behavior changes based on its internal state (like a state machine).
> **Interview problems**: Elevator System, Vending Machine, Order Lifecycle, Circuit Breaker, Traffic Light

```java
// ===== State Interface =====
interface OrderState {
    void next(Order order);
    void prev(Order order);
    void cancel(Order order);
    String getStatus();
}

// ===== Concrete States =====
class PlacedState implements OrderState {
    @Override
    public void next(Order order) { order.setState(new PaidState()); }
    @Override
    public void prev(Order order) { System.out.println("Already at initial state"); }
    @Override
    public void cancel(Order order) { order.setState(new CancelledState()); }
    @Override
    public String getStatus() { return "PLACED"; }
}

class PaidState implements OrderState {
    @Override
    public void next(Order order) { order.setState(new ShippedState()); }
    @Override
    public void prev(Order order) { order.setState(new PlacedState()); }
    @Override
    public void cancel(Order order) { order.setState(new CancelledState()); }
    @Override
    public String getStatus() { return "PAID"; }
}

class ShippedState implements OrderState {
    @Override
    public void next(Order order) { order.setState(new DeliveredState()); }
    @Override
    public void prev(Order order) { order.setState(new PaidState()); }
    @Override
    public void cancel(Order order) { System.out.println("Cannot cancel shipped order!"); }
    @Override
    public String getStatus() { return "SHIPPED"; }
}

class DeliveredState implements OrderState {
    @Override
    public void next(Order order) { System.out.println("Already delivered"); }
    @Override
    public void prev(Order order) { System.out.println("Cannot undo delivery"); }
    @Override
    public void cancel(Order order) { System.out.println("Cannot cancel delivered order"); }
    @Override
    public String getStatus() { return "DELIVERED"; }
}

class CancelledState implements OrderState {
    @Override
    public void next(Order order) { System.out.println("Order is cancelled"); }
    @Override
    public void prev(Order order) { System.out.println("Order is cancelled"); }
    @Override
    public void cancel(Order order) { System.out.println("Already cancelled"); }
    @Override
    public String getStatus() { return "CANCELLED"; }
}

// ===== Context (Order) =====
class Order {
    private OrderState state;
    private final String orderId;
    
    public Order(String orderId) {
        this.orderId = orderId;
        this.state = new PlacedState();  // initial state
    }
    
    public void setState(OrderState state) {
        System.out.println("Order " + orderId + ": " + this.state.getStatus() + " → " + state.getStatus());
        this.state = state;
    }
    
    public void next()   { state.next(this); }
    public void prev()   { state.prev(this); }
    public void cancel() { state.cancel(this); }
    public String getStatus() { return state.getStatus(); }
}

// ===== Usage =====
Order order = new Order("ORD-001");    // PLACED
order.next();                           // PLACED → PAID
order.next();                           // PAID → SHIPPED
order.cancel();                         // "Cannot cancel shipped order!"
order.next();                           // SHIPPED → DELIVERED
```

**Say this**: "I'm using State pattern instead of if-else chains for status. Each state encapsulates its own transition rules — much cleaner than a giant switch statement. Adding a new state (like REFUNDED) means one new class, no changes to existing states. It also prevents invalid transitions — ShippedState knows it can't be cancelled."

---

### 🏗️ Singleton Pattern — Interview-Ready Code

> **Use when**: Exactly one instance needed globally. **Use sparingly** — often overused.
> **Interview problems**: Logger, Configuration Manager, Connection Pool, Cache Manager

```java
// ===== Thread-Safe Singleton (Bill Pugh — Best approach) =====
class Logger {
    // Inner class loaded lazily by JVM — thread-safe without synchronization
    private static class Holder {
        private static final Logger INSTANCE = new Logger();
    }

    private final List<String> logs = new CopyOnWriteArrayList<>();

    private Logger() { }  // private constructor

    public static Logger getInstance() {
        return Holder.INSTANCE;
    }

    public void log(String level, String message) {
        String entry = "[" + level + "] " + Instant.now() + ": " + message;
        logs.add(entry);
        System.out.println(entry);
    }

    public List<String> getLogs() {
        return Collections.unmodifiableList(logs);
    }
}

// ===== Usage (same instance everywhere) =====
Logger.getInstance().log("INFO", "System started");
Logger.getInstance().log("ERROR", "Connection failed");
```

**Say this**: "I'm using Bill Pugh Singleton — the inner static class is loaded lazily by the JVM on first access, which is inherently thread-safe without needing synchronized blocks or volatile. It's the cleanest approach in Java."

---

### ⌨️ Command Pattern — Interview-Ready Code

> **Use when**: Need to encapsulate actions as objects — supports undo/redo, queuing, logging.
> **Interview problems**: Text Editor (Undo/Redo), Task Queue, Smart Home Remote, Transaction System

```java
// ===== Command Interface =====
interface Command {
    void execute();
    void undo();
    String getDescription();
}

// ===== Concrete Commands =====
class AddTextCommand implements Command {
    private final StringBuilder document;
    private final String textToAdd;
    private final int position;
    
    public AddTextCommand(StringBuilder document, String text) {
        this.document = document;
        this.textToAdd = text;
        this.position = document.length();
    }
    
    @Override
    public void execute() { document.append(textToAdd); }
    @Override
    public void undo() { document.delete(position, position + textToAdd.length()); }
    @Override
    public String getDescription() { return "Add '" + textToAdd + "'"; }
}

class DeleteTextCommand implements Command {
    private final StringBuilder document;
    private final int start, end;
    private String deletedText;
    
    public DeleteTextCommand(StringBuilder document, int start, int end) {
        this.document = document;
        this.start = start;
        this.end = end;
    }
    
    @Override
    public void execute() {
        deletedText = document.substring(start, end);
        document.delete(start, end);
    }
    @Override
    public void undo() { document.insert(start, deletedText); }
    @Override
    public String getDescription() { return "Delete '" + deletedText + "'"; }
}

// ===== Invoker (with Undo/Redo stack) =====
class TextEditor {
    private final StringBuilder document = new StringBuilder();
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    
    public void executeCommand(Command cmd) {
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear();  // clear redo history on new action
        System.out.println("Executed: " + cmd.getDescription() + " → \"" + document + "\"");
    }
    
    public void undo() {
        if (undoStack.isEmpty()) { System.out.println("Nothing to undo"); return; }
        Command cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
        System.out.println("Undo: " + cmd.getDescription() + " → \"" + document + "\"");
    }
    
    public void redo() {
        if (redoStack.isEmpty()) { System.out.println("Nothing to redo"); return; }
        Command cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);
        System.out.println("Redo: " + cmd.getDescription() + " → \"" + document + "\"");
    }
}

// ===== Usage =====
TextEditor editor = new TextEditor();
editor.executeCommand(new AddTextCommand(editor.document, "Hello"));   // "Hello"
editor.executeCommand(new AddTextCommand(editor.document, " World"));  // "Hello World"
editor.undo();                                                          // "Hello"
editor.redo();                                                          // "Hello World"
```

**Say this**: "Command pattern lets me encapsulate each action as an object with execute() and undo(). This naturally gives us undo/redo with two stacks — much cleaner than trying to reverse-engineer state changes."

---

### 🎨 Decorator Pattern — Interview-Ready Code

> **Use when**: Add behavior dynamically without modifying existing classes. Wraps objects.
> **Interview problems**: Pizza/Coffee toppings, Message encryption/compression, Logging wrappers, Stream processing

```java
// ===== Component Interface =====
interface DataSource {
    void writeData(String data);
    String readData();
}

// ===== Concrete Component =====
class FileDataSource implements DataSource {
    private String data = "";
    
    @Override
    public void writeData(String data) { this.data = data; }
    @Override
    public String readData() { return data; }
}

// ===== Base Decorator =====
abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrapped;
    
    public DataSourceDecorator(DataSource source) { this.wrapped = source; }
    
    @Override
    public void writeData(String data) { wrapped.writeData(data); }
    @Override
    public String readData() { return wrapped.readData(); }
}

// ===== Concrete Decorators =====
class EncryptionDecorator extends DataSourceDecorator {
    public EncryptionDecorator(DataSource source) { super(source); }
    
    @Override
    public void writeData(String data) {
        System.out.println("🔒 Encrypting data...");
        super.writeData(encode(data));  // encrypt before writing
    }
    @Override
    public String readData() {
        return decode(super.readData()); // decrypt after reading
    }
    
    private String encode(String data) { return Base64.getEncoder().encodeToString(data.getBytes()); }
    private String decode(String data) { return new String(Base64.getDecoder().decode(data)); }
}

class CompressionDecorator extends DataSourceDecorator {
    public CompressionDecorator(DataSource source) { super(source); }
    
    @Override
    public void writeData(String data) {
        System.out.println("📦 Compressing data...");
        super.writeData("[compressed]" + data);
    }
    @Override
    public String readData() {
        return super.readData().replace("[compressed]", "");
    }
}

class LoggingDecorator extends DataSourceDecorator {
    public LoggingDecorator(DataSource source) { super(source); }
    
    @Override
    public void writeData(String data) {
        System.out.println("📝 LOG: Writing " + data.length() + " chars");
        super.writeData(data);
    }
}

// ===== Usage — Stack decorators like layers =====
DataSource source = new LoggingDecorator(
                        new EncryptionDecorator(
                            new CompressionDecorator(
                                new FileDataSource())));
source.writeData("Secret message");  // Logs → Encrypts → Compresses → Writes
String data = source.readData();     // Reads → Decompresses → Decrypts
```

**Say this**: "Decorator lets me stack behaviors like layers — logging, encryption, compression — without modifying the base class. Each decorator wraps the previous one. I can add/remove behaviors at runtime by composing different decorators. Java's own InputStream/BufferedInputStream uses this exact pattern."

---

### 🔗 Chain of Responsibility — Interview-Ready Code

> **Use when**: Multiple handlers process a request, each deciding to handle it or pass to the next.
> **Interview problems**: Middleware pipeline, Approval workflow, Request validation, Logging levels

```java
// ===== Handler Interface =====
abstract class RequestHandler {
    protected RequestHandler next;

    public RequestHandler setNext(RequestHandler next) {
        this.next = next;
        return next;  // enables chaining: a.setNext(b).setNext(c)
    }

    public void handle(Request request) {
        if (next != null) next.handle(request);
    }
}

// ===== Concrete Handlers =====
class AuthenticationHandler extends RequestHandler {
    @Override
    public void handle(Request request) {
        if (request.getToken() == null) {
            throw new SecurityException("No auth token!");
        }
        System.out.println("✅ Auth: Token valid");
        super.handle(request);  // pass to next
    }
}

class RateLimitHandler extends RequestHandler {
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 100;

    @Override
    public void handle(Request request) {
        int count = requestCounts.merge(request.getUserId(), 1, Integer::sum);
        if (count > MAX_REQUESTS) {
            throw new RuntimeException("Rate limit exceeded for: " + request.getUserId());
        }
        System.out.println("✅ Rate Limit: " + count + "/" + MAX_REQUESTS);
        super.handle(request);
    }
}

class ValidationHandler extends RequestHandler {
    @Override
    public void handle(Request request) {
        if (request.getBody() == null || request.getBody().isEmpty()) {
            throw new IllegalArgumentException("Request body cannot be empty");
        }
        System.out.println("✅ Validation: Body is valid");
        super.handle(request);
    }
}

class LoggingHandler extends RequestHandler {
    @Override
    public void handle(Request request) {
        System.out.println("📝 Logging: " + request.getMethod() + " " + request.getPath());
        super.handle(request);
    }
}

// ===== Build the chain =====
RequestHandler chain = new LoggingHandler();
chain.setNext(new AuthenticationHandler())
     .setNext(new RateLimitHandler())
     .setNext(new ValidationHandler());

chain.handle(request);  // Logging → Auth → RateLimit → Validation
```

**Say this**: "Chain of Responsibility lets me build a pipeline of handlers. Each handler does one thing (SRP) and decides to pass or stop. Adding a new middleware step means adding one class and inserting it in the chain — zero changes to existing handlers. This is exactly how web frameworks like Spring's filter chain work."

---

### 🧩 Pattern Combination Guide — Which Patterns Go Together

> In real interviews, you almost always combine 2-3 patterns. Here's what pairs well:

| Problem Type | Combine These Patterns | Why |
|-------------|----------------------|-----|
| **Notification System** | Observer + Strategy + Factory | Observer for event dispatch, Strategy for channel routing, Factory for notification creation |
| **Parking Lot** | Strategy + Factory + Observer | Strategy for spot allocation, Factory for vehicle creation, Observer for capacity alerts |
| **Elevator System** | State + Strategy + Observer | State for elevator mode, Strategy for scheduling algo, Observer for floor display updates |
| **Chat System** | Observer + Command + Singleton | Observer for message broadcasting, Command for message history/undo, Singleton for chat server |
| **File System** | Composite + Decorator + Observer | Composite for folder/file tree, Decorator for permissions/encryption, Observer for file watchers |
| **Rate Limiter** | Strategy + Chain of Responsibility | Strategy for rate-limit algorithm, Chain for middleware pipeline |
| **Text Editor** | Command + Observer + Singleton | Command for undo/redo, Observer for UI updates, Singleton for editor instance |
| **Order Management** | State + Observer + Factory | State for order lifecycle, Observer for status notifications, Factory for order creation |
| **Logging Framework** | Singleton + Chain of Responsibility + Decorator | Singleton for logger, Chain for log-level filtering, Decorator for formatting |
| **Vending Machine** | State + Strategy | State for machine modes (idle/dispensing/out-of-stock), Strategy for payment methods |

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
