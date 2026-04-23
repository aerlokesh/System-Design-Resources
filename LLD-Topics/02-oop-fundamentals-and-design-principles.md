# 🎯 LLD Topic 2: OOP Fundamentals & Design Principles

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering the four pillars of OOP — Encapsulation, Abstraction, Inheritance, Polymorphism — along with DRY, KISS, YAGNI, Law of Demeter, and how to demonstrate deep OOP thinking in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 2: OOP Fundamentals \& Design Principles](#-lld-topic-2-oop-fundamentals--design-principles)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Encapsulation](#encapsulation)
    - [Why It Matters](#why-it-matters)
    - [Bad: Exposed Internals](#bad-exposed-internals)
    - [Good: Protected State with Controlled Access](#good-protected-state-with-controlled-access)
    - [Encapsulation Rules](#encapsulation-rules)
  - [Abstraction](#abstraction)
    - [Abstraction Through Interfaces](#abstraction-through-interfaces)
    - [Levels of Abstraction](#levels-of-abstraction)
  - [Inheritance](#inheritance)
    - [When Inheritance Is Appropriate](#when-inheritance-is-appropriate)
    - [When Inheritance Goes Wrong](#when-inheritance-goes-wrong)
    - [Inheritance Depth Rule](#inheritance-depth-rule)
  - [Polymorphism](#polymorphism)
    - [Compile-Time Polymorphism (Overloading)](#compile-time-polymorphism-overloading)
    - [Runtime Polymorphism (Overriding) — The Important One](#runtime-polymorphism-overriding--the-important-one)
    - [Polymorphism Eliminates Switch/If-Else Chains](#polymorphism-eliminates-switchif-else-chains)
  - [DRY — Don't Repeat Yourself](#dry--dont-repeat-yourself)
    - [DRY Doesn't Mean "No Duplication Ever"](#dry-doesnt-mean-no-duplication-ever)
  - [KISS — Keep It Simple, Stupid](#kiss--keep-it-simple-stupid)
  - [YAGNI — You Aren't Gonna Need It](#yagni--you-arent-gonna-need-it)
  - [Law of Demeter](#law-of-demeter)
    - [Why It Matters](#why-it-matters-1)
  - [Tell Don't Ask](#tell-dont-ask)
  - [Favor Composition Over Inheritance](#favor-composition-over-inheritance)
    - [When to Use Which](#when-to-use-which)
  - [Program to Interfaces, Not Implementations](#program-to-interfaces-not-implementations)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Asked "What is OOP?"](#when-asked-what-is-oop)
    - [When Starting an LLD Problem](#when-starting-an-lld-problem)
    - [When Justifying a Design Decision](#when-justifying-a-design-decision)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Object-Oriented Programming organizes code around **objects** (data + behavior) rather than functions and procedures. The four pillars work together to create systems that model real-world concepts with clear boundaries, reusable components, and manageable complexity.

```
Why OOP mastery matters in LLD interviews:
  1. Every LLD question requires class modeling
  2. Interviewers look for NATURAL use of OOP (not forced)
  3. OOP done well = code that READS like the problem description
  4. OOP done poorly = tangled inheritance, God classes, data dumps
```

---

## Encapsulation

> **Bundle data and the methods that operate on that data within a single unit, and restrict direct access to the internal state.**

### Why It Matters

Encapsulation protects the **invariants** of your objects. If external code can set fields directly, they can put the object in an **invalid state**.

### Bad: Exposed Internals

```java
// ❌ BAD: Anyone can break the invariant
class BankAccount {
    public double balance;  // Public field — no protection
    public List<Transaction> transactions;
}

// Caller can do this:
account.balance = -1000;  // Invalid state!
account.transactions = null;  // Will cause NPE later
```

### Good: Protected State with Controlled Access

```java
// ✅ GOOD: State is protected, access is controlled
class BankAccount {
    private double balance;
    private final List<Transaction> transactions = new ArrayList<>();
    
    public BankAccount(double initialBalance) {
        if (initialBalance < 0) throw new IllegalArgumentException("Cannot start negative");
        this.balance = initialBalance;
    }
    
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit must be positive");
        this.balance += amount;
        transactions.add(new Transaction(TransactionType.DEPOSIT, amount));
    }
    
    public void withdraw(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Withdrawal must be positive");
        if (amount > balance) throw new InsufficientFundsException();
        this.balance -= amount;
        transactions.add(new Transaction(TransactionType.WITHDRAWAL, amount));
    }
    
    public double getBalance() { return balance; }
    
    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);  // Defensive copy
    }
}
```

### Encapsulation Rules

```
1. Make all fields PRIVATE
2. Provide public methods that ENFORCE business rules
3. Return COPIES or UNMODIFIABLE views of collections
4. Validate ALL inputs at the boundary
5. Never expose internal data structures directly
```

---

## Abstraction

> **Hide complex implementation details and expose only the essential interface.**

### Abstraction Through Interfaces

```java
// Client code sees WHAT, not HOW
interface MessageQueue {
    void publish(String topic, Message message);
    Message consume(String topic);
}

// Implementation 1: In-memory (for testing)
class InMemoryQueue implements MessageQueue {
    private Map<String, Queue<Message>> topics = new ConcurrentHashMap<>();
    // ... implementation details hidden
}

// Implementation 2: Kafka (for production)
class KafkaQueue implements MessageQueue {
    private KafkaProducer producer;
    private KafkaConsumer consumer;
    // ... implementation details hidden
}

// Client doesn't know or care which implementation is used
class OrderService {
    private final MessageQueue queue;  // Sees only the abstraction
    
    void placeOrder(Order order) {
        queue.publish("orders", new OrderMessage(order));
    }
}
```

### Levels of Abstraction

```
Level 1 (Highest): Business Domain
  - Order, Payment, User, Notification

Level 2: Service Layer
  - OrderService, PaymentProcessor, NotificationSender

Level 3: Infrastructure
  - DatabaseRepository, HttpClient, MessagePublisher

Level 4 (Lowest): Implementation Details
  - MySQLConnection, TCP Socket, JSON Serializer

Key: Each level should ONLY talk to the level directly below it.
```

---

## Inheritance

> **A mechanism where a new class derives properties and behavior from an existing class.**

### When Inheritance Is Appropriate

```java
// ✅ GOOD: IS-A relationship, shared behavior makes sense
abstract class Vehicle {
    private String licensePlate;
    private double fuelLevel;
    
    abstract double getFuelEfficiency();  // Each vehicle type differs
    
    double calculateRange() {
        return fuelLevel * getFuelEfficiency();  // Shared logic
    }
    
    void refuel(double amount) {
        this.fuelLevel += amount;  // Shared behavior
    }
}

class Car extends Vehicle {
    double getFuelEfficiency() { return 30.0; }  // 30 miles/gallon
}

class Truck extends Vehicle {
    private double cargoWeight;
    double getFuelEfficiency() { 
        return 15.0 - (cargoWeight * 0.01);  // Decreases with load
    }
}
```

### When Inheritance Goes Wrong

```java
// ❌ BAD: Stack is NOT an ArrayList — it's using inheritance for code reuse
class Stack<T> extends ArrayList<T> {
    public void push(T item) { add(item); }
    public T pop() { return remove(size() - 1); }
    public T peek() { return get(size() - 1); }
    // But callers can also do: stack.add(0, item) — breaks stack semantics!
    // And: stack.remove(3) — stacks don't support random removal!
}

// ✅ GOOD: Stack HAS-A list (composition)
class Stack<T> {
    private final List<T> elements = new ArrayList<>();
    
    public void push(T item) { elements.add(item); }
    public T pop() { return elements.remove(elements.size() - 1); }
    public T peek() { return elements.get(elements.size() - 1); }
    // No way to break stack semantics from outside
}
```

### Inheritance Depth Rule

```
Maximum inheritance depth: 2-3 levels
  Interface → Abstract Base → Concrete Class

Beyond that:
  - Code becomes hard to understand ("where is this method defined?")
  - Changes in parent break all children (fragile base class problem)
  - Testing requires understanding the entire hierarchy
```

---

## Polymorphism

> **The ability to treat objects of different types through a common interface, where each type responds in its own way.**

### Compile-Time Polymorphism (Overloading)

```java
class Calculator {
    int add(int a, int b) { return a + b; }
    double add(double a, double b) { return a + b; }
    String add(String a, String b) { return a + b; }  // Concatenation
}
```

### Runtime Polymorphism (Overriding) — The Important One

```java
interface NotificationChannel {
    void send(User user, String message);
}

class EmailNotification implements NotificationChannel {
    public void send(User user, String message) {
        // Send via SMTP
        smtpClient.send(user.getEmail(), "Notification", message);
    }
}

class SmsNotification implements NotificationChannel {
    public void send(User user, String message) {
        // Send via Twilio
        twilioClient.sendSms(user.getPhone(), message);
    }
}

class PushNotification implements NotificationChannel {
    public void send(User user, String message) {
        // Send via Firebase
        firebaseClient.push(user.getDeviceToken(), message);
    }
}

// Polymorphic usage — same code works with ANY channel
class NotificationService {
    private final List<NotificationChannel> channels;
    
    void notifyUser(User user, String message) {
        for (NotificationChannel channel : channels) {
            channel.send(user, message);  // Each type responds differently
        }
    }
}
```

### Polymorphism Eliminates Switch/If-Else Chains

```java
// ❌ BAD: Switch on type — violates OCP
double calculateDiscount(Customer customer) {
    switch (customer.getType()) {
        case REGULAR: return 0.0;
        case PREMIUM: return 0.10;
        case VIP: return 0.20;
        default: throw new IllegalArgumentException();
    }
}

// ✅ GOOD: Polymorphism
interface DiscountPolicy {
    double getDiscount();
}

class RegularDiscount implements DiscountPolicy {
    public double getDiscount() { return 0.0; }
}

class PremiumDiscount implements DiscountPolicy {
    public double getDiscount() { return 0.10; }
}

class VipDiscount implements DiscountPolicy {
    public double getDiscount() { return 0.20; }
}
```

---

## DRY — Don't Repeat Yourself

> **Every piece of knowledge should have a single, unambiguous, authoritative representation within a system.**

```java
// ❌ BAD: Tax calculation duplicated across classes
class OrderService {
    double calculateOrderTotal(Order order) {
        double subtotal = order.getSubtotal();
        double tax = subtotal * 0.08;  // Duplicated tax logic!
        return subtotal + tax;
    }
}

class InvoiceService {
    double calculateInvoiceTotal(Invoice invoice) {
        double subtotal = invoice.getSubtotal();
        double tax = subtotal * 0.08;  // Same logic repeated!
        return subtotal + tax;
    }
}

// ✅ GOOD: Single source of truth for tax calculation
class TaxCalculator {
    private static final double TAX_RATE = 0.08;
    
    double calculateTax(double amount) {
        return amount * TAX_RATE;
    }
}
```

### DRY Doesn't Mean "No Duplication Ever"

```
DRY is about KNOWLEDGE duplication, not CODE duplication.

Two methods that happen to look similar but serve DIFFERENT business 
purposes are NOT violations of DRY:
  - calculateShippingCost() and calculateHandlingCost() might have 
    similar formulas today, but they change for DIFFERENT reasons.
  - Forcing them to share code creates COUPLING where none should exist.
```

---

## KISS — Keep It Simple, Stupid

```java
// ❌ Over-engineered: AbstractNotificationChannelFactoryBuilder
// ✅ Simple: EmailSender with a send() method

// ❌ Premature generics and patterns
class GenericRepositoryAdapterFactoryImpl<T extends BaseEntity & Serializable> { }

// ✅ Start simple, extract patterns when you see THREE instances
class UserRepository {
    void save(User user) { ... }
    Optional<User> findById(String id) { ... }
}
```

---

## YAGNI — You Aren't Gonna Need It

```
Don't build what you don't need TODAY.

❌ "Let me add a plugin system in case we need it later"
❌ "I'll make this configurable for 20 different payment providers"
❌ "Let me add event sourcing in case we need audit logs someday"

✅ Build the simplest thing that works
✅ Refactor when the ACTUAL requirement arrives
✅ "I could add X, but since it's not in the requirements, 
    I'll design this so X is POSSIBLE to add later, 
    without building it now."
```

---

## Law of Demeter

> **An object should only talk to its immediate friends, not strangers.**

```java
// ❌ BAD: Train wreck — reaching through objects
String city = order.getCustomer().getAddress().getCity();

// ✅ GOOD: Ask, don't dig
String city = order.getShippingCity();

// Inside Order class:
class Order {
    private Customer customer;
    
    String getShippingCity() {
        return customer.getShippingCity();  // Delegate, don't expose
    }
}
```

### Why It Matters

```
Each "dot" in a chain is a DEPENDENCY:
  order.getCustomer().getAddress().getCity()
  
  Your code now depends on:
  - Order having a getCustomer()
  - Customer having a getAddress()
  - Address having a getCity()
  
  If ANY of these change, YOUR code breaks.
  
With Law of Demeter:
  order.getShippingCity()
  
  Your code depends on:
  - Order having getShippingCity()
  
  Internal structure changes? Only Order class needs updating.
```

---

## Tell Don't Ask

> **Tell objects what to do; don't ask them for their data and make decisions for them.**

```java
// ❌ BAD: Ask for data, make decision externally
if (account.getBalance() >= amount) {
    account.setBalance(account.getBalance() - amount);
    // What about transaction logging? Validation? Thread safety?
}

// ✅ GOOD: Tell the object what to do
account.withdraw(amount);  // Object handles all logic internally
```

---

## Favor Composition Over Inheritance

```java
// ❌ Inheritance: rigid hierarchy
class FlyingSwimmingDuck extends FlyingAnimal { }  // Wait, what about swimming?

// ✅ Composition: flexible capabilities
class Duck {
    private FlyBehavior flyBehavior;
    private SwimBehavior swimBehavior;
    private QuackBehavior quackBehavior;
    
    void performFly() { flyBehavior.fly(); }
    void performSwim() { swimBehavior.swim(); }
    void performQuack() { quackBehavior.quack(); }
}
```

### When to Use Which

```
Use INHERITANCE when:
  - True IS-A relationship (Cat IS-A Animal)
  - You want to share code in a type hierarchy
  - The relationship is STABLE and won't change

Use COMPOSITION when:
  - HAS-A or USES-A relationship (Car HAS-A Engine)
  - Behavior varies independently
  - You want to swap behavior at runtime
  - You'd need multiple inheritance (Java doesn't support it)
```

---

## Program to Interfaces, Not Implementations

```java
// ❌ BAD: Tied to ArrayList forever
ArrayList<String> names = new ArrayList<>();

// ✅ GOOD: Can swap to LinkedList, CopyOnWriteArrayList, etc.
List<String> names = new ArrayList<>();

// ❌ BAD: Method signature ties to concrete type
void processOrders(ArrayList<Order> orders) { }

// ✅ GOOD: Accepts any List implementation
void processOrders(List<Order> orders) { }

// EVEN BETTER: Accept the most general type that works
void processOrders(Collection<Order> orders) { }
// Or if you only need iteration:
void processOrders(Iterable<Order> orders) { }
```

---

## Interview Talking Points & Scripts

### When Asked "What is OOP?"

> *"OOP models software as interacting objects, each with state and behavior. The four pillars — encapsulation, abstraction, inheritance, and polymorphism — help me create systems where data is protected from invalid states, complexity is hidden behind clean interfaces, common behavior is shared through hierarchies or composition, and different types can be used interchangeably through a common interface."*

### When Starting an LLD Problem

> *"I'll start by identifying the key entities — these will become my classes. Then I'll think about what data each entity owns (encapsulation), what behaviors they expose (abstraction), whether there are natural type hierarchies or shared behaviors (inheritance/composition), and where different types need to be treated uniformly (polymorphism)."*

### When Justifying a Design Decision

> *"I chose composition over inheritance here because the behaviors can vary independently. A notification might be sent via email OR SMS OR push — and these might change at runtime. With inheritance, I'd need a class for each combination."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Using inheritance for code reuse (not IS-A)
   Fix: "I use inheritance ONLY for genuine IS-A. For code reuse, 
   I use composition or utility classes."

❌ Mistake 2: Exposing internal collections
   Fix: Return Collections.unmodifiableList() or defensive copies.

❌ Mistake 3: Anemic domain models (data classes with no behavior)
   Fix: Put behavior WHERE THE DATA IS. If Order has items, 
   Order should calculate its own total.

❌ Mistake 4: Creating deep inheritance hierarchies
   Fix: Keep to 2-3 levels max. Beyond that, use interfaces + composition.

❌ Mistake 5: Using getters/setters for everything
   Fix: Ask "does the caller NEED this data, or should the object 
   perform the action itself?" (Tell Don't Ask)
```

---

## Summary Cheat Sheet

```
┌─────────────────────────────────────────────────────────────┐
│               OOP FUNDAMENTALS CHEAT SHEET                   │
├──────────────┬──────────────────────────────────────────────┤
│ Encapsulation│ Hide state, expose behavior, enforce rules    │
│ Abstraction  │ Hide HOW, expose WHAT (interfaces)            │
│ Inheritance  │ IS-A only, max 2-3 levels deep                │
│ Polymorphism │ One interface, many behaviors (Strategy, etc.) │
├──────────────┼──────────────────────────────────────────────┤
│ DRY          │ One source of truth for each piece of logic   │
│ KISS         │ Simplest solution that works                  │
│ YAGNI        │ Don't build what you don't need yet           │
│ Demeter      │ Only talk to immediate friends                │
│ Tell Don't   │ Tell objects to act, don't ask for their data │
│ Ask          │                                               │
├──────────────┼──────────────────────────────────────────────┤
│ Composition  │ Prefer HAS-A over IS-A for flexibility        │
│ Interfaces   │ Program to abstractions, not concretions      │
├──────────────┴──────────────────────────────────────────────┤
│ Key Interview Line:                                          │
│ "I model the problem domain as objects with clear boundaries │
│  — protected state, clean interfaces, and polymorphic        │
│  behavior — so the code reads like the problem itself."      │
└─────────────────────────────────────────────────────────────┘
```
