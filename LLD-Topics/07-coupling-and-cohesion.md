# 🎯 LLD Topic 7: Coupling & Cohesion

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering coupling (tight vs loose), cohesion (high vs low), how to measure them, refactoring strategies, and how to articulate these concepts to demonstrate design maturity in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 7: Coupling \& Cohesion](#-lld-topic-7-coupling--cohesion)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Cohesion — The Good Force](#cohesion--the-good-force)
    - [Low Cohesion (Bad)](#low-cohesion-bad)
    - [High Cohesion (Good)](#high-cohesion-good)
  - [Types of Cohesion](#types-of-cohesion)
  - [Coupling — The Force to Minimize](#coupling--the-force-to-minimize)
    - [Tight Coupling (Bad)](#tight-coupling-bad)
    - [Loose Coupling (Good)](#loose-coupling-good)
  - [Types of Coupling](#types-of-coupling)
  - [Measuring Coupling and Cohesion](#measuring-coupling-and-cohesion)
    - [Quick Heuristics](#quick-heuristics)
  - [Refactoring for Better Cohesion](#refactoring-for-better-cohesion)
    - [Extract Class](#extract-class)
  - [Refactoring for Looser Coupling](#refactoring-for-looser-coupling)
    - [Introduce Interface](#introduce-interface)
    - [Event-Based Decoupling](#event-based-decoupling)
  - [Coupling \& Cohesion in Design Patterns](#coupling--cohesion-in-design-patterns)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Asked "What makes good class design?"](#when-asked-what-makes-good-class-design)
    - [When Justifying Your Design](#when-justifying-your-design)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Cohesion** and **coupling** are the two most important metrics of good class design:

- **Cohesion**: How strongly related the elements WITHIN a module are. **HIGH cohesion = GOOD.**
- **Coupling**: How dependent one module is on OTHER modules. **LOW coupling = GOOD.**

```
The Golden Rule:
  HIGH COHESION + LOW COUPLING = maintainable, testable, extensible code

  Think of it like teams in a company:
  - High Cohesion: Each team has a clear, focused mission
  - Low Coupling: Teams can work independently without blocking each other
```

---

## Cohesion — The Good Force

> **High cohesion means every element in a class serves a single, clear purpose.**

### Low Cohesion (Bad)

```java
// ❌ BAD: This class does EVERYTHING related to users
class UserManager {
    void registerUser(String name, String email) { /* ... */ }
    void authenticateUser(String email, String password) { /* ... */ }
    void sendEmail(String to, String subject) { /* ... */ }
    void generateReport(List<User> users) { /* ... */ }
    void exportToCsv(List<User> users) { /* ... */ }
    void calculateSubscriptionFee(User user) { /* ... */ }
    void updateUserAddress(User user, Address addr) { /* ... */ }
}
```

### High Cohesion (Good)

```java
// ✅ GOOD: Each class is focused on ONE thing
class UserRegistrationService {
    void register(String name, String email) { /* ... */ }
    void verifyEmail(String token) { /* ... */ }
}

class AuthenticationService {
    AuthResult authenticate(String email, String password) { /* ... */ }
    void logout(String sessionId) { /* ... */ }
}

class UserReportService {
    Report generate(List<User> users) { /* ... */ }
    void exportCsv(Report report) { /* ... */ }
}

class SubscriptionService {
    double calculateFee(User user) { /* ... */ }
    void renewSubscription(User user) { /* ... */ }
}
```

---

## Types of Cohesion

```
From WORST to BEST:

1. COINCIDENTAL (worst)
   - Elements are grouped randomly, no real relationship
   - Example: UtilityClass with unrelated static methods

2. LOGICAL
   - Elements do similar TYPES of things but on different data
   - Example: InputHandler that handles keyboard, mouse, and joystick

3. TEMPORAL
   - Elements are grouped because they happen at the same TIME
   - Example: InitializationService that sets up DB, cache, and logging

4. PROCEDURAL
   - Elements are grouped because they follow a specific SEQUENCE
   - Example: CheckoutProcessor that validates, charges, and ships

5. COMMUNICATIONAL
   - Elements operate on the same DATA
   - Example: CustomerReport class that reads and formats customer data

6. SEQUENTIAL
   - Output of one element is input to the next
   - Example: DataPipeline where parse → validate → transform → store

7. FUNCTIONAL (best)
   - Every element contributes to a SINGLE well-defined task
   - Example: PasswordHasher that only deals with hashing passwords
```

---

## Coupling — The Force to Minimize

> **Low coupling means changing one module doesn't force changes in others.**

### Tight Coupling (Bad)

```java
// ❌ BAD: OrderService is TIGHTLY coupled to specific implementations
class OrderService {
    private MySQLDatabase db = new MySQLDatabase();           // Concrete class
    private StripePaymentGateway stripe = new StripePaymentGateway(); // Concrete class
    private SmtpEmailSender email = new SmtpEmailSender();    // Concrete class
    
    void placeOrder(Order order) {
        db.insertOrder(order);              // Can't switch database
        stripe.charge(order.getTotal());    // Can't switch payment provider
        email.send(order.getUserEmail());   // Can't switch email provider
    }
}
// Changing ANY implementation requires modifying OrderService
```

### Loose Coupling (Good)

```java
// ✅ GOOD: OrderService depends on ABSTRACTIONS
class OrderService {
    private final OrderRepository repository;        // Interface
    private final PaymentGateway payment;            // Interface
    private final NotificationService notification;  // Interface
    
    OrderService(OrderRepository repo, PaymentGateway pay, NotificationService notif) {
        this.repository = repo;
        this.payment = pay;
        this.notification = notif;
    }
    
    void placeOrder(Order order) {
        repository.save(order);
        payment.charge(order.getTotal());
        notification.notify(order.getUserEmail(), "Order placed!");
    }
}
// Can switch MySQL → PostgreSQL, Stripe → PayPal, SMTP → SES without touching this class
```

---

## Types of Coupling

```
From WORST to BEST:

1. CONTENT COUPLING (worst)
   - One class directly modifies internal state of another
   - Example: classA.otherClass.privateField = value;

2. COMMON COUPLING
   - Multiple classes depend on GLOBAL/SHARED mutable state
   - Example: Static mutable variables accessed by multiple classes

3. CONTROL COUPLING
   - One class controls the FLOW of another by passing a flag
   - Example: process(data, isAdmin) where behavior changes based on flag

4. STAMP COUPLING
   - Classes share a DATA STRUCTURE but each uses only part of it
   - Example: Passing entire User object when only email is needed

5. DATA COUPLING (best for coupled classes)
   - Classes communicate through SIMPLE parameters
   - Example: calculateTax(double amount, String state)

6. MESSAGE COUPLING (ideal)
   - Classes communicate only through METHOD CALLS on interfaces
   - Example: repository.save(order) — no knowledge of internals
```

---

## Measuring Coupling and Cohesion

### Quick Heuristics

```
HIGH COUPLING symptoms:
  ☐ Changing class A forces changes in class B, C, D
  ☐ Class uses 'new' to create dependencies internally
  ☐ Class references concrete implementations, not interfaces
  ☐ Too many import statements at the top
  ☐ Circular dependencies between packages

HIGH COHESION symptoms (GOOD):
  ☑ Class name accurately describes everything it does
  ☑ All methods use most of the class fields
  ☑ You can't split the class without creating artificial coupling
  ☑ Class is easy to name (no "Manager", "Handler", "Utils")

LOW COHESION symptoms (BAD):
  ☐ Class is hard to name (UserManagerHelperUtils)
  ☐ Methods operate on completely different data
  ☐ Class has 500+ lines
  ☐ Methods don't share any fields
```

---

## Refactoring for Better Cohesion

### Extract Class

```java
// BEFORE: Low cohesion — Order handles formatting and persistence
class Order {
    private List<LineItem> items;
    private Customer customer;
    
    double calculateTotal() { /* pricing logic */ }
    String toJson() { /* serialization logic */ }
    String toPdf() { /* PDF generation logic */ }
    void saveToDb() { /* persistence logic */ }
}

// AFTER: High cohesion — each class has one purpose
class Order {
    private List<LineItem> items;
    private Customer customer;
    double calculateTotal() { /* only pricing */ }
}

class OrderSerializer {
    String toJson(Order order) { /* only serialization */ }
    String toPdf(Order order) { /* only PDF generation */ }
}

class OrderRepository {
    void save(Order order) { /* only persistence */ }
}
```

---

## Refactoring for Looser Coupling

### Introduce Interface

```java
// BEFORE: Tight coupling
class ReportService {
    private PostgresDB db = new PostgresDB();  // Concrete!
    
    Report generate() {
        List<Row> data = db.query("SELECT * FROM sales");
        return new Report(data);
    }
}

// AFTER: Loose coupling via interface
interface DataSource {
    List<Row> query(String sql);
}

class ReportService {
    private final DataSource dataSource;  // Interface!
    
    ReportService(DataSource ds) { this.dataSource = ds; }
    
    Report generate() {
        List<Row> data = dataSource.query("SELECT * FROM sales");
        return new Report(data);
    }
}
```

### Event-Based Decoupling

```java
// BEFORE: OrderService directly calls 5 other services
class OrderService {
    private InventoryService inventory;
    private PaymentService payment;
    private EmailService email;
    private AnalyticsService analytics;
    private LoyaltyService loyalty;
    
    void placeOrder(Order order) {
        inventory.reserve(order);
        payment.charge(order);
        email.sendConfirmation(order);
        analytics.track(order);
        loyalty.addPoints(order);
    }
}

// AFTER: Event-driven decoupling
class OrderService {
    private final EventBus eventBus;
    
    void placeOrder(Order order) {
        // Core operation
        processOrder(order);
        // Publish event — don't know or care who listens
        eventBus.publish(new OrderPlacedEvent(order));
    }
}
// Each service subscribes independently — OrderService has ZERO coupling to them
```

---

## Coupling & Cohesion in Design Patterns

| Pattern | Impact on Coupling | Impact on Cohesion |
|---|---|---|
| **Strategy** | Reduces coupling (algorithm swappable) | Increases cohesion (each strategy is focused) |
| **Observer** | Reduces coupling (pub-sub decoupling) | Increases cohesion (each observer has one job) |
| **Facade** | Reduces coupling (clients see simple API) | May reduce cohesion (facade does too much) |
| **Mediator** | Reduces coupling between peers | Mediator itself may have low cohesion |
| **DI/IoC** | Reduces coupling (dependencies injected) | Neutral |

---

## Interview Talking Points & Scripts

### When Asked "What makes good class design?"

> *"Two things: high cohesion and low coupling. High cohesion means each class has a single, clear purpose — all its methods and fields serve that one purpose. Low coupling means classes depend on abstractions, not concrete implementations — so I can change one class without rippling changes through the system. Together, they give me code that's easy to understand, test, and extend."*

### When Justifying Your Design

> *"I separated OrderService from NotificationService because they have different reasons to change — that gives each class high cohesion. I connected them through an EventBus interface rather than direct calls — that keeps coupling low. If we add a new notification channel tomorrow, OrderService doesn't change at all."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Creating "Manager" or "Utils" classes
   Fix: These are signs of low cohesion. Split into focused classes.

❌ Mistake 2: Using concrete classes in constructor
   Fix: Depend on interfaces. Inject dependencies.

❌ Mistake 3: Circular dependencies
   Fix: Introduce an interface or event bus to break the cycle.

❌ Mistake 4: God classes with 20+ methods
   Fix: Extract classes by responsibility.

❌ Mistake 5: Data classes with no behavior
   Fix: Put behavior WHERE THE DATA IS (high cohesion).
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│            COUPLING & COHESION CHEAT SHEET                    │
├──────────────┬───────────────────────────────────────────────┤
│ COHESION     │ HIGH = GOOD. Each class does ONE thing well.  │
│ (within)     │ Test: Can you name it without "Manager/Utils"?│
├──────────────┼───────────────────────────────────────────────┤
│ COUPLING     │ LOW = GOOD. Classes depend on interfaces.     │
│ (between)    │ Test: Can you change one without changing all? │
├──────────────┼───────────────────────────────────────────────┤
│ Reduce       │ 1. Depend on interfaces, not concretions      │
│ Coupling     │ 2. Use dependency injection                   │
│              │ 3. Use events/observer for notifications       │
│              │ 4. Apply Facade for subsystem simplification   │
├──────────────┼───────────────────────────────────────────────┤
│ Increase     │ 1. Extract classes by responsibility           │
│ Cohesion     │ 2. Move behavior to where the data is         │
│              │ 3. Eliminate "and" in class descriptions       │
│              │ 4. Every method should use class fields        │
├──────────────┴───────────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "High cohesion + low coupling = code that's easy to change,   │
│  test independently, and extend without breaking things."     │
└──────────────────────────────────────────────────────────────┘
```
