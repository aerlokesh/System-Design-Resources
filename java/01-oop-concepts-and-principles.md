# 🎯 Topic 1: OOP Concepts & Principles in Java

> **Java Interview — Deep Dive**
> A comprehensive guide covering everything you need to know about Object-Oriented Programming concepts, SOLID principles, and how to articulate these with depth and precision in a Java interview.

---

## Table of Contents

1. [Four Pillars of OOP](#four-pillars-of-oop)
2. [Encapsulation — Data Hiding](#encapsulation--data-hiding)
3. [Inheritance — Code Reuse & IS-A](#inheritance--code-reuse--is-a)
4. [Polymorphism — One Interface, Many Implementations](#polymorphism--one-interface-many-implementations)
5. [Abstraction — Hiding Complexity](#abstraction--hiding-complexity)
6. [Composition vs Inheritance](#composition-vs-inheritance)
7. [Association, Aggregation, Composition](#association-aggregation-composition)
8. [SOLID Principles](#solid-principles)
9. [Abstract Classes vs Interfaces](#abstract-classes-vs-interfaces)
10. [Coupling & Cohesion](#coupling--cohesion)
11. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
12. [Common Interview Mistakes](#common-interview-mistakes)
13. [Code Examples & Traps](#code-examples--traps)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Four Pillars of OOP

Object-Oriented Programming rests on **four foundational pillars** that work together to create modular, maintainable, and extensible code:

| Pillar | Purpose | Java Mechanism |
|--------|---------|---------------|
| **Encapsulation** | Protect internal state, expose controlled API | `private` fields + `public` getters/setters |
| **Inheritance** | Code reuse, IS-A relationships | `extends`, `implements` |
| **Polymorphism** | One interface, many forms | Method overriding, method overloading |
| **Abstraction** | Hide complexity, expose essentials | `abstract` classes, `interfaces` |

### The Critical Insight

> These aren't independent concepts — they're **synergistic**. Encapsulation enables safe inheritance, inheritance enables polymorphism, and abstraction ties them together. In interviews, don't just define them — show how they interact.

---

## Encapsulation — Data Hiding

### What It Is

Encapsulation bundles **data (fields)** and **methods that operate on that data** into a single unit (class), restricting direct access to some of the object's components.

### Why It Matters

- **Data Integrity**: Prevents invalid states (e.g., negative age, null email)
- **Flexibility**: Internal representation can change without breaking clients
- **Controlled Access**: Business rules enforced at the setter level

### Deep Dive: Beyond Getters/Setters

```java
// ❌ BAD — Exposes internal state
public class BankAccount {
    public double balance;  // Anyone can set this to -1000
}

// ✅ GOOD — Encapsulated with business rules
public class BankAccount {
    private double balance;
    
    public BankAccount(double initialBalance) {
        if (initialBalance < 0) throw new IllegalArgumentException("Balance cannot be negative");
        this.balance = initialBalance;
    }
    
    public double getBalance() {
        return balance;
    }
    
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit must be positive");
        this.balance += amount;
    }
    
    public void withdraw(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Withdrawal must be positive");
        if (amount > balance) throw new InsufficientFundsException("Insufficient funds");
        this.balance -= amount;
    }
}
```

### The Mutable Collection Trap (Interview Favorite)

```java
public class Team {
    private List<String> members;
    
    // ❌ BAD — Returns reference to internal list
    public List<String> getMembers() {
        return members;  // Caller can do getMembers().clear()!
    }
    
    // ✅ GOOD — Returns defensive copy
    public List<String> getMembers() {
        return Collections.unmodifiableList(members);
    }
    
    // ✅ BEST — Return immutable copy (Java 10+)
    public List<String> getMembers() {
        return List.copyOf(members);
    }
}
```

### Access Modifiers Summary

| Modifier | Class | Package | Subclass | World |
|----------|-------|---------|----------|-------|
| `private` | ✅ | ❌ | ❌ | ❌ |
| default (package-private) | ✅ | ✅ | ❌ | ❌ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| `public` | ✅ | ✅ | ✅ | ✅ |

> **Interview Tip**: `protected` gives access to subclasses **even in different packages**. Default (no modifier) does NOT.

---

## Inheritance — Code Reuse & IS-A

### Single vs Multiple Inheritance

Java supports **single class inheritance** but **multiple interface implementation**.

```java
// Single inheritance
class Animal { }
class Dog extends Animal { }  // ✅ OK

// Multiple inheritance NOT allowed for classes
class Dog extends Animal, Pet { }  // ❌ COMPILE ERROR

// But multiple interfaces ARE allowed
class Dog extends Animal implements Pet, Trainable { }  // ✅ OK
```

### Why Java Doesn't Allow Multiple Class Inheritance — Diamond Problem

```
      Animal
      /    \
   Dog      Cat
      \    /
     DogCat  ← Which eat() method?
```

If both `Dog` and `Cat` override `eat()`, `DogCat` doesn't know which to use. Java avoids this entirely.

> **But Wait**: Java 8+ default methods in interfaces CAN cause this. You must resolve it explicitly:

```java
interface Flyable {
    default void move() { System.out.println("Flying"); }
}
interface Swimmable {
    default void move() { System.out.println("Swimming"); }
}

class Duck implements Flyable, Swimmable {
    @Override
    public void move() {
        Flyable.super.move();  // Explicit resolution required
    }
}
```

### Constructor Chaining

```java
class Animal {
    String name;
    Animal(String name) { this.name = name; }
}

class Dog extends Animal {
    String breed;
    Dog(String name, String breed) {
        super(name);          // MUST be first line
        this.breed = breed;
    }
}
```

> **Interview Trap**: If parent has NO no-arg constructor and child doesn't call `super(args)`, **compile error**.

### The `instanceof` and Type Checking

```java
Animal a = new Dog("Rex");
System.out.println(a instanceof Dog);     // true
System.out.println(a instanceof Animal);  // true
System.out.println(a instanceof Object);  // true

// Java 16+ Pattern Matching
if (a instanceof Dog d) {
    System.out.println(d.getBreed());  // No cast needed!
}
```

---

## Polymorphism — One Interface, Many Implementations

### Compile-Time Polymorphism (Method Overloading)

**Resolved at compile time** based on method signature (name + parameter types).

```java
class Calculator {
    int add(int a, int b) { return a + b; }
    double add(double a, double b) { return a + b; }
    int add(int a, int b, int c) { return a + b + c; }
}
```

**Overloading Rules**:
- ✅ Different parameter types
- ✅ Different number of parameters
- ✅ Different parameter order
- ❌ Return type alone is NOT enough
- ❌ Access modifier alone is NOT enough

### Runtime Polymorphism (Method Overriding)

**Resolved at runtime** based on the actual object type (dynamic dispatch).

```java
class Shape {
    double area() { return 0; }
}

class Circle extends Shape {
    double radius;
    Circle(double r) { this.radius = r; }
    
    @Override
    double area() { return Math.PI * radius * radius; }
}

class Rectangle extends Shape {
    double width, height;
    Rectangle(double w, double h) { this.width = w; this.height = h; }
    
    @Override
    double area() { return width * height; }
}

// Polymorphic usage
Shape s = new Circle(5);
System.out.println(s.area());  // Calls Circle.area() → 78.54
```

### Overriding Rules (Interview Critical)

| Rule | Details |
|------|---------|
| Method signature | Must match exactly |
| Return type | Same or covariant (subtype) |
| Access modifier | Same or less restrictive |
| Exceptions | Same or narrower checked exceptions |
| `static` methods | Cannot be overridden (hidden instead) |
| `final` methods | Cannot be overridden |
| `private` methods | Cannot be overridden (not inherited) |
| `@Override` | Optional but strongly recommended |

### Covariant Return Types

```java
class Animal {
    Animal create() { return new Animal(); }
}

class Dog extends Animal {
    @Override
    Dog create() { return new Dog(); }  // ✅ Covariant return
}
```

---

## Abstraction — Hiding Complexity

### Abstract Classes

```java
abstract class DatabaseConnection {
    // Concrete method — shared logic
    public void connect(String url) {
        validateUrl(url);
        openConnection(url);
        logConnection(url);
    }
    
    // Abstract method — subclass must implement
    protected abstract void openConnection(String url);
    
    // Template Method Pattern!
    private void validateUrl(String url) {
        if (url == null || url.isEmpty()) throw new IllegalArgumentException();
    }
    
    private void logConnection(String url) {
        System.out.println("Connected to: " + url);
    }
}

class MySQLConnection extends DatabaseConnection {
    @Override
    protected void openConnection(String url) {
        // MySQL-specific connection logic
    }
}
```

### Interfaces (Java 8+)

```java
public interface PaymentProcessor {
    // Abstract method (must implement)
    PaymentResult process(Payment payment);
    
    // Default method (optional override)
    default boolean validate(Payment payment) {
        return payment != null && payment.getAmount() > 0;
    }
    
    // Static method (utility)
    static PaymentProcessor create(String type) {
        return switch (type) {
            case "stripe" -> new StripeProcessor();
            case "paypal" -> new PayPalProcessor();
            default -> throw new IllegalArgumentException();
        };
    }
    
    // Private method (Java 9+ — helper for default methods)
    private void logTransaction(Payment p) {
        System.out.println("Processing: " + p.getId());
    }
}
```

---

## Abstract Classes vs Interfaces

| Feature | Abstract Class | Interface |
|---------|---------------|-----------|
| Inheritance | Single (`extends`) | Multiple (`implements`) |
| Constructor | ✅ Yes | ❌ No |
| Instance fields | ✅ Yes | ❌ Only `static final` |
| Access modifiers | Any | `public` (default for methods) |
| Method types | Abstract + concrete | Abstract + default + static + private |
| Use case | IS-A with shared state | CAN-DO / capability contract |

### When to Use Which

- **Abstract Class**: When subclasses share state and behavior (e.g., `Animal` with `name`, `age`)
- **Interface**: When defining a capability contract (e.g., `Serializable`, `Comparable`, `Runnable`)
- **Modern Preference**: Prefer interfaces with default methods; use abstract classes only when you need instance state

---

## Composition vs Inheritance

### The Golden Rule

> **"Favor composition over inheritance"** — Effective Java, Item 18

### Why Composition Wins

```java
// ❌ Inheritance — Tight coupling, fragile
class InstrumentedHashSet<E> extends HashSet<E> {
    private int addCount = 0;
    
    @Override
    public boolean add(E e) {
        addCount++;
        return super.add(e);  // What if HashSet.addAll() calls add()?
    }
    
    @Override
    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return super.addAll(c);  // DOUBLE COUNTING! addAll() internally calls add()
    }
}

// ✅ Composition — Loose coupling, robust
class InstrumentedSet<E> {
    private final Set<E> delegate;  // Composition
    private int addCount = 0;
    
    public InstrumentedSet(Set<E> delegate) {
        this.delegate = delegate;
    }
    
    public boolean add(E e) {
        addCount++;
        return delegate.add(e);  // No risk of internal callback issues
    }
    
    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return delegate.addAll(c);  // Delegated — no double counting
    }
}
```

---

## SOLID Principles

### S — Single Responsibility Principle

> A class should have **one, and only one, reason to change**.

```java
// ❌ BAD — Multiple responsibilities
class UserService {
    void createUser(User u) { /* DB logic */ }
    void sendEmail(User u) { /* Email logic */ }
    String generateReport(User u) { /* Report logic */ }
}

// ✅ GOOD — Separated concerns
class UserRepository { void save(User u) { } }
class EmailService { void sendWelcome(User u) { } }
class UserReportGenerator { String generate(User u) { } }
```

### O — Open/Closed Principle

> Open for **extension**, closed for **modification**.

```java
// ✅ GOOD — New shapes don't require modifying existing code
interface Shape { double area(); }
class Circle implements Shape { /* ... */ }
class Rectangle implements Shape { /* ... */ }
// Adding Triangle doesn't touch Circle or Rectangle
class Triangle implements Shape { /* ... */ }
```

### L — Liskov Substitution Principle

> Subtypes must be **substitutable** for their base types without altering correctness.

```java
// ❌ BAD — Square is NOT a proper substitute for Rectangle
class Rectangle {
    protected int width, height;
    void setWidth(int w) { width = w; }
    void setHeight(int h) { height = h; }
    int area() { return width * height; }
}

class Square extends Rectangle {
    @Override void setWidth(int w) { width = w; height = w; }   // Side effect!
    @Override void setHeight(int h) { width = h; height = h; }  // Side effect!
}

// rect.setWidth(5); rect.setHeight(3); → area should be 15
// But if rect is actually Square → area is 9!
```

### I — Interface Segregation Principle

> Clients should not be forced to depend on methods they don't use.

```java
// ❌ BAD — Fat interface
interface Worker {
    void work();
    void eat();
    void sleep();
}
class Robot implements Worker {
    void work() { /* OK */ }
    void eat() { /* Robots don't eat! */ }   // Forced to implement
    void sleep() { /* Robots don't sleep! */ }
}

// ✅ GOOD — Segregated interfaces
interface Workable { void work(); }
interface Feedable { void eat(); }
interface Restable { void sleep(); }
class Robot implements Workable { void work() { } }
class Human implements Workable, Feedable, Restable { /* ... */ }
```

### D — Dependency Inversion Principle

> Depend on **abstractions**, not concretions.

```java
// ❌ BAD — High-level depends on low-level
class OrderService {
    private MySQLDatabase db = new MySQLDatabase();  // Concrete dependency
}

// ✅ GOOD — Depends on abstraction
class OrderService {
    private final Database db;  // Interface
    OrderService(Database db) { this.db = db; }  // Injected
}
```

---

## Coupling & Cohesion

| Property | Good | Bad |
|----------|------|-----|
| **Coupling** | Loose — classes know little about each other | Tight — changes cascade |
| **Cohesion** | High — class does one thing well | Low — class is a "god object" |

---

## Interview Talking Points & Scripts

### "Explain OOP in 60 seconds"

> *"OOP is a paradigm that models software as interacting objects. The four pillars are: **Encapsulation** — bundling data with methods and restricting access; **Inheritance** — creating hierarchies for code reuse; **Polymorphism** — same interface, different behaviors at runtime; and **Abstraction** — exposing only what's necessary while hiding complexity. In practice, I favor **composition over inheritance** and follow **SOLID principles** to keep code maintainable and testable."*

### "Why composition over inheritance?"

> *"Inheritance creates tight coupling between parent and child. If the parent's implementation changes, subclasses can break silently — like the InstrumentedHashSet problem where addAll() internally calls add(), causing double-counting. Composition gives you the same code reuse through delegation, but with loose coupling, easier testing via dependency injection, and more flexibility — you can swap the delegate at runtime."*

### "Explain Liskov Substitution with an example"

> *"LSP says if S extends T, you should be able to use S anywhere T is expected without breaking behavior. The classic violation is Square extending Rectangle — setting width on a Square silently changes height, which violates Rectangle's contract. The fix is to not use inheritance here; instead, both can implement a Shape interface with an area() method."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "OOP is about classes" | OOP is about **objects and their interactions** |
| "Encapsulation = getters/setters" | Encapsulation is about **controlling access**, not just adding methods |
| "Polymorphism = overloading" | Overloading is compile-time; **runtime polymorphism (overriding)** is the key concept |
| "Always use inheritance for code reuse" | Composition is often better; inheritance is for IS-A relationships |
| "Abstract class and interface are the same" | They have different capabilities and use cases |
| "SOLID means every class needs interfaces" | SOLID is about **principled design**, not blindly adding abstractions |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                    OOP PRINCIPLES CHEAT SHEET                 │
├──────────────────────────────────────────────────────────────┤
│ Encapsulation  → private fields + public API                 │
│                → Defensive copies for mutable collections     │
│                                                              │
│ Inheritance    → Single class, multiple interface             │
│                → Diamond problem → resolved by explicit call  │
│                → super() must be first line in constructor    │
│                                                              │
│ Polymorphism   → Compile-time: overloading (method signature) │
│                → Runtime: overriding (dynamic dispatch)       │
│                → Covariant return types allowed               │
│                                                              │
│ Abstraction    → Abstract class: shared state + behavior      │
│                → Interface: capability contract               │
│                → Java 8+: default, static, private methods    │
│                                                              │
│ SOLID          → S: One reason to change                      │
│                → O: Open for extension, closed for mod        │
│                → L: Subtypes substitutable for parents        │
│                → I: No fat interfaces                         │
│                → D: Depend on abstractions                    │
│                                                              │
│ Key Rule       → Favor COMPOSITION over INHERITANCE           │
└──────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Collections Framework Deep Dive →](./02-collections-framework-deep-dive.md)
