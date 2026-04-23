# 🎯 LLD Topic 1: SOLID Principles

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering all five SOLID principles — Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion — with Java examples, violation patterns, refactoring strategies, and how to articulate these concepts with depth in LLD interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Single Responsibility Principle (SRP)](#single-responsibility-principle-srp)
3. [Open/Closed Principle (OCP)](#openclosed-principle-ocp)
4. [Liskov Substitution Principle (LSP)](#liskov-substitution-principle-lsp)
5. [Interface Segregation Principle (ISP)](#interface-segregation-principle-isp)
6. [Dependency Inversion Principle (DIP)](#dependency-inversion-principle-dip)
7. [How SOLID Principles Work Together](#how-solid-principles-work-together)
8. [Real-World Refactoring Examples](#real-world-refactoring-examples)
9. [SOLID in Design Patterns](#solid-in-design-patterns)
10. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
11. [Common Interview Mistakes](#common-interview-mistakes)
12. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

SOLID is an acronym for five design principles that make object-oriented software **flexible, maintainable, and extensible**. They were introduced by Robert C. Martin (Uncle Bob) and represent the foundation of good class design.

```
Why SOLID matters in interviews:
  1. Shows you write code that's EASY TO CHANGE (not just "works")
  2. Demonstrates you think about FUTURE requirements
  3. Proves you understand WHY design patterns exist
  4. Shows you can identify BAD code and refactor it
```

> **Interview Gold**: *"SOLID principles aren't rules to blindly follow — they're guidelines that help me write code where each class has a clear purpose, new features don't require modifying existing code, and components are loosely coupled enough to test and evolve independently."*

---

## Single Responsibility Principle (SRP)

> **A class should have only one reason to change.**

### The Key Insight

SRP isn't about doing "one thing" — it's about having **one actor** or **one axis of change**. A class should be responsible to one, and only one, stakeholder.

### Violation Example

```java
// ❌ BAD: This class has THREE reasons to change
class Employee {
    String name;
    double salary;
    
    // Reason 1: Business logic changes (HR department)
    double calculatePay() {
        return salary * getOvertimeMultiplier();
    }
    
    // Reason 2: Report format changes (Management)
    String generateReport() {
        return "Employee: " + name + ", Pay: $" + calculatePay();
    }
    
    // Reason 3: Database schema changes (DBA team)
    void saveToDatabase() {
        // INSERT INTO employees...
    }
}
```

### Refactored

```java
// ✅ GOOD: Each class has ONE reason to change
class Employee {
    String name;
    double salary;
}

class PayCalculator {
    double calculatePay(Employee emp) {
        return emp.salary * getOvertimeMultiplier();
    }
}

class EmployeeReportGenerator {
    String generateReport(Employee emp, PayCalculator calc) {
        return "Employee: " + emp.name + ", Pay: $" + calc.calculatePay(emp);
    }
}

class EmployeeRepository {
    void save(Employee emp) {
        // INSERT INTO employees...
    }
}
```

### How to Identify SRP Violations

```
Ask yourself:
  1. "Who would request a change to this class?"
     - If multiple stakeholders → violation
  2. "If I change X, could it break Y within this class?"
     - If unrelated methods could break → violation
  3. "Can I describe this class without using AND/OR?"
     - "Calculates pay AND generates reports" → violation
     - "Stores employee data" → good
```

### SRP Applied to Methods

```java
// ❌ BAD: Method does too much
void processOrder(Order order) {
    validateOrder(order);
    calculateTax(order);
    applyDiscount(order);
    chargePayment(order);
    sendConfirmationEmail(order);
    updateInventory(order);
}

// ✅ GOOD: Orchestrator delegates to focused services
void processOrder(Order order) {
    validator.validate(order);
    pricingService.calculateTotal(order);
    paymentService.charge(order);
    notificationService.sendConfirmation(order);
    inventoryService.update(order);
}
```

---

## Open/Closed Principle (OCP)

> **Software entities should be open for extension, but closed for modification.**

### The Key Insight

You should be able to add new behavior **without changing existing code**. This is typically achieved through **abstraction** (interfaces/abstract classes) and **polymorphism**.

### Violation Example

```java
// ❌ BAD: Adding a new shape requires modifying this method
class AreaCalculator {
    double calculateArea(Object shape) {
        if (shape instanceof Circle) {
            Circle c = (Circle) shape;
            return Math.PI * c.radius * c.radius;
        } else if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            return r.width * r.height;
        }
        // Every new shape = modify this method!
        throw new UnsupportedOperationException();
    }
}
```

### Refactored

```java
// ✅ GOOD: New shapes extend without modifying existing code
interface Shape {
    double calculateArea();
}

class Circle implements Shape {
    double radius;
    public double calculateArea() {
        return Math.PI * radius * radius;
    }
}

class Rectangle implements Shape {
    double width, height;
    public double calculateArea() {
        return width * height;
    }
}

// Adding Triangle? Just create a new class. ZERO changes to existing code.
class Triangle implements Shape {
    double base, height;
    public double calculateArea() {
        return 0.5 * base * height;
    }
}

class AreaCalculator {
    double calculateTotalArea(List<Shape> shapes) {
        return shapes.stream().mapToDouble(Shape::calculateArea).sum();
    }
}
```

### OCP Through Strategy Pattern

```java
// Payment processing that's open for extension
interface PaymentStrategy {
    void pay(double amount);
}

class CreditCardPayment implements PaymentStrategy { ... }
class PayPalPayment implements PaymentStrategy { ... }
class CryptoPayment implements PaymentStrategy { ... }  // Added later, no changes needed

class PaymentProcessor {
    void processPayment(PaymentStrategy strategy, double amount) {
        strategy.pay(amount);  // Works with ANY payment type
    }
}
```

### When OCP Gets Over-Engineered

```
DON'T apply OCP everywhere:
  ❌ Creating abstractions for things that will NEVER change
  ❌ Having 1 interface with 1 implementation "just in case"
  ✅ Apply when you KNOW new types/behaviors will be added
  ✅ Apply at clear EXTENSION POINTS (payment types, notification channels, etc.)
```

---

## Liskov Substitution Principle (LSP)

> **Objects of a superclass should be replaceable with objects of its subclasses without altering the correctness of the program.**

### The Key Insight

If class `B` extends class `A`, then anywhere you use `A`, you should be able to use `B` without surprises. Subtypes must be **behaviorally compatible** with their base type.

### Classic Violation: Rectangle/Square

```java
// ❌ BAD: Square violates LSP when substituted for Rectangle
class Rectangle {
    protected int width, height;
    
    void setWidth(int w) { this.width = w; }
    void setHeight(int h) { this.height = h; }
    int getArea() { return width * height; }
}

class Square extends Rectangle {
    // Square MUST keep width == height
    void setWidth(int w) { this.width = w; this.height = w; }
    void setHeight(int h) { this.width = h; this.height = h; }
}

// This breaks!
void testArea(Rectangle r) {
    r.setWidth(5);
    r.setHeight(4);
    assert r.getArea() == 20; // FAILS for Square! Area is 16
}
```

### Refactored

```java
// ✅ GOOD: Use composition or separate hierarchy
interface Shape {
    int getArea();
}

class Rectangle implements Shape {
    private int width, height;
    Rectangle(int w, int h) { this.width = w; this.height = h; }
    int getArea() { return width * height; }
}

class Square implements Shape {
    private int side;
    Square(int s) { this.side = s; }
    int getArea() { return side * side; }
}
```

### LSP Rules

```
A subclass MUST:
  1. Accept ALL inputs the parent accepts (same or weaker preconditions)
  2. Return outputs the parent would return (same or stronger postconditions)
  3. NOT throw new exceptions the parent doesn't declare
  4. NOT change behavior the parent's clients depend on

Smell tests:
  - Does the subclass throw UnsupportedOperationException? → LSP violation
  - Does the subclass override a method to do nothing? → LSP violation
  - Does instanceof check appear in client code? → likely LSP issue
```

### Real-World Example

```java
// ❌ BAD: ReadOnlyList violates LSP
class ReadOnlyList<T> extends ArrayList<T> {
    @Override
    public boolean add(T element) {
        throw new UnsupportedOperationException(); // Surprise!
    }
}

// ✅ GOOD: Separate interfaces
interface ReadableList<T> {
    T get(int index);
    int size();
}

interface WritableList<T> extends ReadableList<T> {
    void add(T element);
}
```

---

## Interface Segregation Principle (ISP)

> **No client should be forced to depend on methods it does not use.**

### The Key Insight

Instead of one fat interface, create multiple small, focused interfaces. Clients should only know about methods they actually call.

### Violation Example

```java
// ❌ BAD: Fat interface forces unnecessary implementations
interface Worker {
    void work();
    void eat();
    void sleep();
    void attendMeeting();
}

class Robot implements Worker {
    public void work() { /* OK */ }
    public void eat() { /* Robots don't eat! */ throw new UnsupportedOperationException(); }
    public void sleep() { /* Robots don't sleep! */ throw new UnsupportedOperationException(); }
    public void attendMeeting() { /* Maybe? */ }
}
```

### Refactored

```java
// ✅ GOOD: Segregated interfaces — each client uses only what it needs
interface Workable {
    void work();
}

interface Feedable {
    void eat();
}

interface Restable {
    void sleep();
}

class HumanWorker implements Workable, Feedable, Restable {
    public void work() { ... }
    public void eat() { ... }
    public void sleep() { ... }
}

class Robot implements Workable {
    public void work() { ... }
    // No eat(), no sleep() — clean!
}
```

### ISP in Practice

```java
// Real-world: Notification service with segregated interfaces
interface EmailSender {
    void sendEmail(String to, String subject, String body);
}

interface SmsSender {
    void sendSms(String phoneNumber, String message);
}

interface PushNotifier {
    void sendPush(String deviceToken, String message);
}

// Order service only needs email
class OrderService {
    private final EmailSender emailSender;  // Depends ONLY on what it uses
    
    void confirmOrder(Order order) {
        emailSender.sendEmail(order.userEmail(), "Confirmed", "...");
    }
}
```

---

## Dependency Inversion Principle (DIP)

> **High-level modules should not depend on low-level modules. Both should depend on abstractions.**
> **Abstractions should not depend on details. Details should depend on abstractions.**

### The Key Insight

Instead of high-level business logic depending directly on low-level implementation details (database, file system, API), both depend on an **interface**. This makes the system flexible and testable.

### Violation Example

```java
// ❌ BAD: High-level OrderService depends on low-level MySQLDatabase
class OrderService {
    private MySQLDatabase database = new MySQLDatabase();  // TIGHT coupling
    private SmtpEmailSender emailSender = new SmtpEmailSender();
    
    void placeOrder(Order order) {
        database.save(order);           // Can't switch to PostgreSQL
        emailSender.send(order.email);  // Can't switch to SES
    }
}
```

### Refactored

```java
// ✅ GOOD: Both high-level and low-level depend on abstractions
interface OrderRepository {
    void save(Order order);
}

interface NotificationService {
    void notify(String recipient, String message);
}

class OrderService {
    private final OrderRepository repository;        // Depends on abstraction
    private final NotificationService notification;  // Depends on abstraction
    
    // Constructor injection — dependencies provided from outside
    OrderService(OrderRepository repo, NotificationService notif) {
        this.repository = repo;
        this.notification = notif;
    }
    
    void placeOrder(Order order) {
        repository.save(order);
        notification.notify(order.email(), "Order placed!");
    }
}

// Low-level modules also depend on abstractions
class MySQLOrderRepository implements OrderRepository { ... }
class PostgresOrderRepository implements OrderRepository { ... }
class EmailNotificationService implements NotificationService { ... }
class SmsNotificationService implements NotificationService { ... }
```

### DIP Enables Testing

```java
// Easy to test with mocks
class OrderServiceTest {
    @Test
    void testPlaceOrder() {
        OrderRepository mockRepo = mock(OrderRepository.class);
        NotificationService mockNotif = mock(NotificationService.class);
        OrderService service = new OrderService(mockRepo, mockNotif);
        
        service.placeOrder(new Order("item", "user@email.com"));
        
        verify(mockRepo).save(any(Order.class));
        verify(mockNotif).notify(eq("user@email.com"), anyString());
    }
}
```

---

## How SOLID Principles Work Together

```
Real-World Example: Notification System

SRP: NotificationService only sends notifications (not user management)
OCP: New channels (Slack, Teams) added without modifying existing code
LSP: All notification channels (Email, SMS, Push) are interchangeable
ISP: Services only depend on the channel interfaces they actually use
DIP: Business logic depends on NotificationChannel interface, not SmtpClient

interface NotificationChannel {          // ISP: focused interface
    void send(String recipient, String message);
}

class EmailChannel implements NotificationChannel { ... }  // LSP: substitutable
class SmsChannel implements NotificationChannel { ... }    // OCP: extend, don't modify
class SlackChannel implements NotificationChannel { ... }  // Added later, no changes

class AlertService {                     // SRP: only handles alerts
    private final NotificationChannel channel;  // DIP: depends on abstraction
    
    AlertService(NotificationChannel channel) {
        this.channel = channel;
    }
    
    void sendAlert(String message) {
        channel.send("ops-team", message);
    }
}
```

---

## Real-World Refactoring Examples

### Before: God Class

```java
// ❌ Violates SRP, OCP, DIP
class UserManager {
    private MySQLConnection db = new MySQLConnection();
    
    void registerUser(String name, String email, String password) {
        // Validation (SRP violation — mixed with persistence)
        if (email == null || !email.contains("@")) throw new RuntimeException();
        if (password.length() < 8) throw new RuntimeException();
        
        // Hashing (SRP violation — security logic mixed in)
        String hashed = BCrypt.hashpw(password);
        
        // Database (DIP violation — hard-coded MySQL)
        db.execute("INSERT INTO users VALUES('" + name + "','" + email + "','" + hashed + "')");
        
        // Notification (SRP violation — emailing mixed in)
        SmtpClient smtp = new SmtpClient();
        smtp.send(email, "Welcome!", "Thanks for joining.");
    }
}
```

### After: SOLID Refactoring

```java
// ✅ Each class has one job, depends on abstractions
class UserValidator {
    void validate(UserRegistrationRequest request) { ... }
}

class PasswordHasher {
    String hash(String raw) { return BCrypt.hashpw(raw); }
}

interface UserRepository {
    void save(User user);
}

interface WelcomeNotifier {
    void sendWelcome(String email);
}

class UserRegistrationService {
    private final UserValidator validator;
    private final PasswordHasher hasher;
    private final UserRepository repository;
    private final WelcomeNotifier notifier;
    
    UserRegistrationService(UserValidator v, PasswordHasher h, 
                           UserRepository r, WelcomeNotifier n) {
        this.validator = v; this.hasher = h;
        this.repository = r; this.notifier = n;
    }
    
    void register(UserRegistrationRequest request) {
        validator.validate(request);
        String hashed = hasher.hash(request.password());
        User user = new User(request.name(), request.email(), hashed);
        repository.save(user);
        notifier.sendWelcome(request.email());
    }
}
```

---

## SOLID in Design Patterns

| Principle | Patterns That Embody It |
|---|---|
| **SRP** | Facade, Mediator, Command |
| **OCP** | Strategy, Template Method, Decorator, Observer |
| **LSP** | Template Method, Factory Method |
| **ISP** | Adapter, Facade, Proxy |
| **DIP** | Abstract Factory, Strategy, Bridge, Dependency Injection |

---

## Interview Talking Points & Scripts

### When Asked "What are SOLID principles?"

> *"SOLID is a set of five OO design principles. SRP says each class should have one reason to change — one stakeholder owning it. OCP says I should be able to add new behavior through extension, not by modifying existing code — typically using interfaces and polymorphism. LSP ensures subtypes are fully substitutable for their parent types without breaking behavior. ISP says interfaces should be focused — clients shouldn't depend on methods they don't use. DIP says high-level business logic should depend on abstractions, not concrete implementations — which makes code testable and swappable."*

### When Designing a Class

> *"Before I write this class, let me think about SRP — what's its single responsibility? For the PaymentService, its job is to orchestrate payment flow. It shouldn't know about database details (DIP) or notification formatting (SRP). I'll define interfaces for the repository and notifier, so the concrete implementations can change independently."*

### When Reviewing Code

> *"I notice this class is handling both validation and persistence — that's an SRP violation. If the validation rules change, this class changes. If the database schema changes, this class also changes. I'd extract a Validator and a Repository."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Reciting definitions without examples
   Fix: Always give a concrete code example for each principle.

❌ Mistake 2: Over-applying SOLID (creating interfaces for everything)
   Fix: "I apply SOLID at boundaries where change is likely. For internal 
   helper methods, pragmatism wins over dogma."

❌ Mistake 3: Confusing SRP with "do one thing"
   Fix: SRP is about ONE REASON TO CHANGE (one stakeholder), not one method.

❌ Mistake 4: Not connecting SOLID to testability
   Fix: "DIP is why I can mock dependencies in unit tests. ISP is why 
   my mocks are small and focused."

❌ Mistake 5: Treating LSP as "just about inheritance"
   Fix: LSP applies to any type substitution — interfaces, generics, etc.
```

---

## Summary Cheat Sheet

```
┌─────────────────────────────────────────────────────────────┐
│                    SOLID PRINCIPLES                          │
├──────────┬──────────────────────────────────────────────────┤
│ SRP      │ One class = one reason to change                 │
│          │ Ask: "Who would request changes to this class?"  │
├──────────┼──────────────────────────────────────────────────┤
│ OCP      │ Open for extension, closed for modification      │
│          │ Use interfaces + polymorphism to add behavior     │
├──────────┼──────────────────────────────────────────────────┤
│ LSP      │ Subtypes must be substitutable for base types     │
│          │ No surprises, no UnsupportedOperationException    │
├──────────┼──────────────────────────────────────────────────┤
│ ISP      │ Many small interfaces > one fat interface         │
│          │ Clients depend only on what they use              │
├──────────┼──────────────────────────────────────────────────┤
│ DIP      │ Depend on abstractions, not concretions           │
│          │ Constructor injection + interface = testable code  │
├──────────┴──────────────────────────────────────────────────┤
│ Key Interview Line:                                          │
│ "SOLID helps me write code that's easy to change, extend,    │
│  and test — without breaking existing functionality."        │
└─────────────────────────────────────────────────────────────┘
```
