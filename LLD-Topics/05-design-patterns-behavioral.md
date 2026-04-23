# 🎯 LLD Topic 5: Design Patterns — Behavioral

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering the most important Behavioral Design Patterns — Strategy, Observer, Command, State, Template Method, Chain of Responsibility, Iterator, Mediator — with Java examples and LLD interview applications.

---

## Table of Contents

- [🎯 LLD Topic 5: Design Patterns — Behavioral](#-lld-topic-5-design-patterns--behavioral)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Strategy Pattern](#strategy-pattern)
    - [The Most Important Behavioral Pattern for LLD Interviews](#the-most-important-behavioral-pattern-for-lld-interviews)
    - [Strategy with Lambda (Modern Java)](#strategy-with-lambda-modern-java)
  - [Observer Pattern](#observer-pattern)
    - [Implementation](#implementation)
    - [Type-Safe Observer with Generics](#type-safe-observer-with-generics)
  - [Command Pattern](#command-pattern)
    - [Implementation with Undo](#implementation-with-undo)
  - [State Pattern](#state-pattern)
    - [Implementation](#implementation-1)
    - [State vs Strategy](#state-vs-strategy)
  - [Template Method Pattern](#template-method-pattern)
    - [Implementation](#implementation-2)
  - [Chain of Responsibility Pattern](#chain-of-responsibility-pattern)
    - [Implementation](#implementation-3)
  - [Iterator Pattern](#iterator-pattern)
  - [Mediator Pattern](#mediator-pattern)
  - [Behavioral Patterns Comparison](#behavioral-patterns-comparison)
  - [Behavioral Patterns in LLD Problems](#behavioral-patterns-in-lld-problems)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Asked "Strategy vs State?"](#when-asked-strategy-vs-state)
    - [When Asked "When would you use Observer?"](#when-asked-when-would-you-use-observer)
    - [When Asked "When would you use Command?"](#when-asked-when-would-you-use-command)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Behavioral patterns deal with **algorithms and the assignment of responsibilities between objects**. They describe how objects communicate and how work flows through the system. They focus on **what objects do** and **how they interact**.

```
Why Behavioral Patterns matter:
  1. Encapsulate ALGORITHMS so they can be swapped (Strategy)
  2. Decouple PUBLISHERS from SUBSCRIBERS (Observer)
  3. Turn REQUESTS into objects for undo/queue/log (Command)
  4. Manage OBJECT STATE transitions cleanly (State)
  5. Define ALGORITHM SKELETONS with customizable steps (Template Method)
  6. Pass REQUESTS along a chain of handlers (Chain of Responsibility)
```

---

## Strategy Pattern

> **Define a family of algorithms, encapsulate each one, and make them interchangeable. Strategy lets the algorithm vary independently from clients that use it.**

### The Most Important Behavioral Pattern for LLD Interviews

```java
// Strategy interface
interface PricingStrategy {
    double calculatePrice(double basePrice, int quantity);
}

// Concrete strategies
class RegularPricing implements PricingStrategy {
    public double calculatePrice(double basePrice, int quantity) {
        return basePrice * quantity;
    }
}

class BulkPricing implements PricingStrategy {
    public double calculatePrice(double basePrice, int quantity) {
        if (quantity >= 100) return basePrice * quantity * 0.70;  // 30% off
        if (quantity >= 50) return basePrice * quantity * 0.80;   // 20% off
        if (quantity >= 10) return basePrice * quantity * 0.90;   // 10% off
        return basePrice * quantity;
    }
}

class SeasonalPricing implements PricingStrategy {
    private final double seasonalMultiplier;
    
    SeasonalPricing(double multiplier) { this.seasonalMultiplier = multiplier; }
    
    public double calculatePrice(double basePrice, int quantity) {
        return basePrice * quantity * seasonalMultiplier;
    }
}

// Context: Uses the strategy
class ShoppingCart {
    private PricingStrategy pricingStrategy;
    private List<CartItem> items = new ArrayList<>();
    
    void setPricingStrategy(PricingStrategy strategy) {
        this.pricingStrategy = strategy;  // Can change at RUNTIME
    }
    
    double calculateTotal() {
        return items.stream()
            .mapToDouble(item -> pricingStrategy.calculatePrice(item.price(), item.quantity()))
            .sum();
    }
}

// Usage: Switch strategies dynamically
ShoppingCart cart = new ShoppingCart();
cart.setPricingStrategy(new RegularPricing());     // Normal day
cart.setPricingStrategy(new SeasonalPricing(0.5)); // Black Friday: 50% off
cart.setPricingStrategy(new BulkPricing());        // Wholesale customer
```

### Strategy with Lambda (Modern Java)

```java
// Strategy as functional interface
@FunctionalInterface
interface SortStrategy<T> {
    int compare(T a, T b);
}

class Sorter<T> {
    void sort(List<T> list, SortStrategy<T> strategy) {
        list.sort(strategy::compare);
    }
}

// Usage with lambdas — no separate classes needed for simple strategies
sorter.sort(users, (a, b) -> a.getName().compareTo(b.getName()));  // By name
sorter.sort(users, (a, b) -> Integer.compare(a.getAge(), b.getAge()));  // By age
```

---

## Observer Pattern

> **Define a one-to-many dependency between objects so that when one object changes state, all its dependents are notified and updated automatically.**

### Implementation

```java
// Observer interface
interface EventListener {
    void onEvent(String eventType, Object data);
}

// Subject (Publisher)
class EventBus {
    private final Map<String, List<EventListener>> listeners = new ConcurrentHashMap<>();
    
    void subscribe(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }
    
    void unsubscribe(String eventType, EventListener listener) {
        List<EventListener> subs = listeners.get(eventType);
        if (subs != null) subs.remove(listener);
    }
    
    void publish(String eventType, Object data) {
        List<EventListener> subs = listeners.getOrDefault(eventType, Collections.emptyList());
        for (EventListener listener : subs) {
            listener.onEvent(eventType, data);
        }
    }
}

// Concrete observers
class EmailNotifier implements EventListener {
    public void onEvent(String eventType, Object data) {
        if ("ORDER_PLACED".equals(eventType)) {
            Order order = (Order) data;
            sendEmail(order.getCustomerEmail(), "Order confirmed: " + order.getId());
        }
    }
}

class InventoryUpdater implements EventListener {
    public void onEvent(String eventType, Object data) {
        if ("ORDER_PLACED".equals(eventType)) {
            Order order = (Order) data;
            reduceStock(order.getItems());
        }
    }
}

class AnalyticsTracker implements EventListener {
    public void onEvent(String eventType, Object data) {
        trackEvent(eventType, data);  // Track ALL events
    }
}

// Usage: Decoupled — OrderService doesn't know about email, inventory, analytics
EventBus eventBus = new EventBus();
eventBus.subscribe("ORDER_PLACED", new EmailNotifier());
eventBus.subscribe("ORDER_PLACED", new InventoryUpdater());
eventBus.subscribe("ORDER_PLACED", new AnalyticsTracker());

// When order is placed:
eventBus.publish("ORDER_PLACED", order);
// All three listeners are notified automatically
```

### Type-Safe Observer with Generics

```java
interface EventHandler<T> {
    void handle(T event);
}

class TypedEventBus {
    private final Map<Class<?>, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();
    
    <T> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(handler);
    }
    
    @SuppressWarnings("unchecked")
    <T> void publish(T event) {
        List<EventHandler<?>> subs = handlers.getOrDefault(event.getClass(), List.of());
        for (EventHandler<?> handler : subs) {
            ((EventHandler<T>) handler).handle(event);
        }
    }
}

// Usage: Type-safe events
record OrderPlacedEvent(String orderId, double total) {}
record PaymentProcessedEvent(String transactionId) {}

eventBus.subscribe(OrderPlacedEvent.class, event -> {
    sendEmail(event.orderId());
});
```

---

## Command Pattern

> **Encapsulate a request as an object, thereby letting you parameterize clients with different requests, queue or log requests, and support undoable operations.**

### Implementation with Undo

```java
// Command interface
interface Command {
    void execute();
    void undo();
}

// Concrete commands
class AddTextCommand implements Command {
    private final Document document;
    private final String text;
    private final int position;
    
    AddTextCommand(Document doc, String text, int position) {
        this.document = doc;
        this.text = text;
        this.position = position;
    }
    
    public void execute() {
        document.insert(position, text);
    }
    
    public void undo() {
        document.delete(position, text.length());
    }
}

class DeleteTextCommand implements Command {
    private final Document document;
    private final int position;
    private final int length;
    private String deletedText;  // Save for undo
    
    public void execute() {
        deletedText = document.getText(position, length);
        document.delete(position, length);
    }
    
    public void undo() {
        document.insert(position, deletedText);
    }
}

class BoldTextCommand implements Command {
    private final Document document;
    private final int start, end;
    
    public void execute() { document.applyBold(start, end); }
    public void undo() { document.removeBold(start, end); }
}

// Invoker: Manages command history
class CommandHistory {
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    
    void executeCommand(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();  // New action invalidates redo history
    }
    
    void undo() {
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            redoStack.push(command);
        }
    }
    
    void redo() {
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.execute();
            undoStack.push(command);
        }
    }
}

// Usage
CommandHistory history = new CommandHistory();
history.executeCommand(new AddTextCommand(doc, "Hello", 0));
history.executeCommand(new AddTextCommand(doc, " World", 5));
history.undo();  // Removes " World"
history.redo();  // Adds " World" back
```

---

## State Pattern

> **Allow an object to alter its behavior when its internal state changes. The object will appear to change its class.**

### Implementation

```java
// State interface
interface OrderState {
    void next(OrderContext context);
    void previous(OrderContext context);
    void cancel(OrderContext context);
    String getStatus();
}

// Concrete states
class PendingState implements OrderState {
    public void next(OrderContext ctx) {
        ctx.setState(new ConfirmedState());
    }
    public void previous(OrderContext ctx) {
        System.out.println("Already at initial state");
    }
    public void cancel(OrderContext ctx) {
        ctx.setState(new CancelledState());
    }
    public String getStatus() { return "PENDING"; }
}

class ConfirmedState implements OrderState {
    public void next(OrderContext ctx) {
        ctx.setState(new ShippedState());
    }
    public void previous(OrderContext ctx) {
        ctx.setState(new PendingState());
    }
    public void cancel(OrderContext ctx) {
        ctx.setState(new CancelledState());
    }
    public String getStatus() { return "CONFIRMED"; }
}

class ShippedState implements OrderState {
    public void next(OrderContext ctx) {
        ctx.setState(new DeliveredState());
    }
    public void previous(OrderContext ctx) {
        System.out.println("Cannot un-ship an order");
    }
    public void cancel(OrderContext ctx) {
        System.out.println("Cannot cancel shipped order");
    }
    public String getStatus() { return "SHIPPED"; }
}

class DeliveredState implements OrderState {
    public void next(OrderContext ctx) {
        System.out.println("Already delivered — final state");
    }
    public void previous(OrderContext ctx) {
        System.out.println("Cannot reverse delivery");
    }
    public void cancel(OrderContext ctx) {
        System.out.println("Cannot cancel delivered order");
    }
    public String getStatus() { return "DELIVERED"; }
}

class CancelledState implements OrderState {
    public void next(OrderContext ctx) { System.out.println("Cancelled is final"); }
    public void previous(OrderContext ctx) { System.out.println("Cancelled is final"); }
    public void cancel(OrderContext ctx) { System.out.println("Already cancelled"); }
    public String getStatus() { return "CANCELLED"; }
}

// Context
class OrderContext {
    private OrderState currentState;
    
    OrderContext() { this.currentState = new PendingState(); }
    
    void setState(OrderState state) { this.currentState = state; }
    void next() { currentState.next(this); }
    void previous() { currentState.previous(this); }
    void cancel() { currentState.cancel(this); }
    String getStatus() { return currentState.getStatus(); }
}

// Usage
OrderContext order = new OrderContext();
order.getStatus();  // PENDING
order.next();       // → CONFIRMED
order.next();       // → SHIPPED
order.cancel();     // "Cannot cancel shipped order"
order.next();       // → DELIVERED
```

### State vs Strategy

```
State:
  - Object changes BEHAVIOR based on INTERNAL STATE
  - State transitions are part of the pattern
  - The object "becomes" something different
  - Example: Order (Pending → Confirmed → Shipped → Delivered)

Strategy:
  - CLIENT chooses which algorithm to use
  - No state transitions — just swapping algorithms
  - The object stays the same, behavior changes
  - Example: SortingAlgorithm (QuickSort, MergeSort, BubbleSort)
```

---

## Template Method Pattern

> **Define the skeleton of an algorithm in a superclass, letting subclasses override specific steps without changing the algorithm's structure.**

### Implementation

```java
// Template: Defines algorithm structure
abstract class DataProcessor {
    
    // Template method — defines the SKELETON (final = can't override structure)
    public final void process() {
        readData();
        parseData();
        validateData();
        transformData();
        saveData();
        generateReport();
    }
    
    abstract void readData();       // Subclass must implement
    abstract void parseData();      // Subclass must implement
    
    // Hook methods — optional override with default behavior
    void validateData() {
        System.out.println("Default validation: checking nulls");
    }
    
    abstract void transformData();  // Subclass must implement
    abstract void saveData();       // Subclass must implement
    
    void generateReport() {
        System.out.println("Processing complete");
    }
}

// Concrete implementation: CSV processing
class CsvProcessor extends DataProcessor {
    void readData() { /* Read CSV file */ }
    void parseData() { /* Split by commas */ }
    void transformData() { /* Map columns to objects */ }
    void saveData() { /* Insert into database */ }
}

// Concrete implementation: JSON processing
class JsonProcessor extends DataProcessor {
    void readData() { /* Read JSON file */ }
    void parseData() { /* Parse JSON structure */ }
    void validateData() {
        super.validateData();
        // Additional JSON schema validation
        validateJsonSchema();
    }
    void transformData() { /* Map JSON fields to objects */ }
    void saveData() { /* Insert into database */ }
}
```

---

## Chain of Responsibility Pattern

> **Pass a request along a chain of handlers. Each handler decides either to process the request or pass it to the next handler in the chain.**

### Implementation

```java
// Handler interface
abstract class SupportHandler {
    private SupportHandler nextHandler;
    
    SupportHandler setNext(SupportHandler next) {
        this.nextHandler = next;
        return next;
    }
    
    void handle(SupportTicket ticket) {
        if (canHandle(ticket)) {
            processTicket(ticket);
        } else if (nextHandler != null) {
            nextHandler.handle(ticket);
        } else {
            System.out.println("No handler found for: " + ticket.getDescription());
        }
    }
    
    abstract boolean canHandle(SupportTicket ticket);
    abstract void processTicket(SupportTicket ticket);
}

class BotHandler extends SupportHandler {
    boolean canHandle(SupportTicket ticket) {
        return ticket.getSeverity() == Severity.LOW && ticket.isCommonQuestion();
    }
    void processTicket(SupportTicket ticket) {
        System.out.println("Bot: Auto-resolved with FAQ");
    }
}

class L1SupportHandler extends SupportHandler {
    boolean canHandle(SupportTicket ticket) {
        return ticket.getSeverity() == Severity.LOW || ticket.getSeverity() == Severity.MEDIUM;
    }
    void processTicket(SupportTicket ticket) {
        System.out.println("L1 Agent handling: " + ticket.getDescription());
    }
}

class L2SupportHandler extends SupportHandler {
    boolean canHandle(SupportTicket ticket) {
        return ticket.getSeverity() == Severity.HIGH;
    }
    void processTicket(SupportTicket ticket) {
        System.out.println("L2 Engineer handling: " + ticket.getDescription());
    }
}

class ManagerHandler extends SupportHandler {
    boolean canHandle(SupportTicket ticket) { return true; }  // Handles everything
    void processTicket(SupportTicket ticket) {
        System.out.println("Manager escalation: " + ticket.getDescription());
    }
}

// Build the chain
SupportHandler chain = new BotHandler();
chain.setNext(new L1SupportHandler())
     .setNext(new L2SupportHandler())
     .setNext(new ManagerHandler());

chain.handle(new SupportTicket(Severity.LOW, true, "Reset password"));  // → Bot
chain.handle(new SupportTicket(Severity.HIGH, false, "Data breach"));   // → L2
```

---

## Iterator Pattern

> **Provide a way to access elements of a collection sequentially without exposing its underlying representation.**

```java
interface Iterator<T> {
    boolean hasNext();
    T next();
}

interface IterableCollection<T> {
    Iterator<T> createIterator();
}

class TreeNode<T> {
    T value;
    TreeNode<T> left, right;
    
    TreeNode(T val) { this.value = val; }
}

// In-order iterator for binary tree
class InOrderIterator<T> implements Iterator<T> {
    private final Deque<TreeNode<T>> stack = new ArrayDeque<>();
    
    InOrderIterator(TreeNode<T> root) {
        pushLeft(root);
    }
    
    private void pushLeft(TreeNode<T> node) {
        while (node != null) {
            stack.push(node);
            node = node.left;
        }
    }
    
    public boolean hasNext() { return !stack.isEmpty(); }
    
    public T next() {
        TreeNode<T> node = stack.pop();
        pushLeft(node.right);
        return node.value;
    }
}
```

---

## Mediator Pattern

> **Define an object that encapsulates how a set of objects interact. Promotes loose coupling by keeping objects from referring to each other explicitly.**

```java
// Mediator
interface ChatMediator {
    void sendMessage(String message, User sender);
    void addUser(User user);
}

class ChatRoom implements ChatMediator {
    private final List<User> users = new ArrayList<>();
    
    public void addUser(User user) {
        users.add(user);
    }
    
    public void sendMessage(String message, User sender) {
        for (User user : users) {
            if (user != sender) {
                user.receive(message, sender.getName());
            }
        }
    }
}

// Colleague
class User {
    private final String name;
    private final ChatMediator mediator;
    
    User(String name, ChatMediator mediator) {
        this.name = name;
        this.mediator = mediator;
        mediator.addUser(this);
    }
    
    void send(String message) {
        mediator.sendMessage(message, this);
    }
    
    void receive(String message, String from) {
        System.out.println(name + " received from " + from + ": " + message);
    }
    
    String getName() { return name; }
}
```

---

## Behavioral Patterns Comparison

| Pattern | Purpose | Key Idea |
|---|---|---|
| **Strategy** | Swap algorithms at runtime | Encapsulate what varies |
| **Observer** | Notify dependents of changes | Pub-sub, one-to-many |
| **Command** | Encapsulate request as object | Undo, queue, log operations |
| **State** | Change behavior based on state | Replace conditionals with polymorphism |
| **Template Method** | Algorithm skeleton with hooks | Inheritance-based customization |
| **Chain of Resp.** | Pass request along handler chain | Decouple sender from receiver |
| **Iterator** | Sequential access without exposing structure | Uniform traversal |
| **Mediator** | Centralize complex communication | Reduce many-to-many to one-to-many |

---

## Behavioral Patterns in LLD Problems

| LLD Problem | Pattern | Why |
|---|---|---|
| **Parking Lot** | Strategy | Different pricing strategies (hourly, daily, monthly) |
| **Notification System** | Observer | Multiple subscribers for events |
| **Text Editor / Google Docs** | Command | Undo/redo operations |
| **Order Management** | State | Order lifecycle (pending → shipped → delivered) |
| **Data Pipeline** | Template Method | Same process, different data sources |
| **Vending Machine** | State | Machine states (idle, selecting, dispensing) |
| **Support Ticket System** | Chain of Responsibility | Escalation levels |
| **Chat System** | Mediator | Centralized message routing |
| **E-commerce** | Strategy + Observer | Pricing strategies + event notifications |

---

## Interview Talking Points & Scripts

### When Asked "Strategy vs State?"

> *"Strategy is about swapping ALGORITHMS — the client picks which one to use, like choosing a sorting method. State is about OBJECT BEHAVIOR changing based on internal state — the object transitions automatically, like an order going from Pending to Shipped. Strategy is externally driven, State is internally driven."*

### When Asked "When would you use Observer?"

> *"Whenever I have a one-to-many relationship where changes in one object should trigger actions in multiple others — but I don't want tight coupling. For example, when an order is placed, the email service, inventory service, and analytics service all need to know — but the order service shouldn't depend on any of them directly."*

### When Asked "When would you use Command?"

> *"When I need to make operations first-class objects — for undo/redo in a text editor, for queuing operations to execute later, or for logging operations for audit trails. The key insight is turning a method call into an object that can be stored, passed around, and reversed."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Confusing Strategy and State
   Fix: Strategy = client chooses algorithm. State = object changes behavior.

❌ Mistake 2: Observer with synchronous blocking calls
   Fix: Consider async notification in production to prevent cascading failures.

❌ Mistake 3: Using if-else chains instead of State pattern
   Fix: If you have 5+ states with different behavior, use State pattern.

❌ Mistake 4: Command without undo capability
   Fix: If you use Command, always consider if undo is needed.

❌ Mistake 5: Over-using patterns
   Fix: A simple if-else is fine for 2-3 cases. Patterns shine at 5+.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              BEHAVIORAL PATTERNS CHEAT SHEET                  │
├──────────────────┬───────────────────────────────────────────┤
│ Strategy         │ Swap algorithms at runtime                 │
│                  │ Use: Pricing, sorting, validation rules    │
├──────────────────┼───────────────────────────────────────────┤
│ Observer         │ Pub-sub: notify dependents of changes      │
│                  │ Use: Events, notifications, data binding   │
├──────────────────┼───────────────────────────────────────────┤
│ Command          │ Request as object: undo, queue, log        │
│                  │ Use: Text editor, task queue, audit trail  │
├──────────────────┼───────────────────────────────────────────┤
│ State            │ Behavior changes with internal state       │
│                  │ Use: Order lifecycle, vending machine      │
├──────────────────┼───────────────────────────────────────────┤
│ Template Method  │ Algorithm skeleton with overridable steps  │
│                  │ Use: Data processing, report generation    │
├──────────────────┼───────────────────────────────────────────┤
│ Chain of Resp.   │ Pass request along handler chain           │
│                  │ Use: Middleware, validation, support tiers  │
├──────────────────┼───────────────────────────────────────────┤
│ Iterator         │ Sequential access, hide structure          │
│                  │ Use: Collections, tree traversal           │
├──────────────────┼───────────────────────────────────────────┤
│ Mediator         │ Centralize complex interactions            │
│                  │ Use: Chat rooms, air traffic control       │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "Behavioral patterns help me define clear communication       │
│  between objects — so algorithms can be swapped, events can   │
│  be broadcast, and state transitions are managed cleanly."    │
└──────────────────────────────────────────────────────────────┘
```
