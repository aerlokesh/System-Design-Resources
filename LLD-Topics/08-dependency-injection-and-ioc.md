# 🎯 LLD Topic 8: Dependency Injection & Inversion of Control

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering Dependency Injection (DI) types — constructor, setter, interface injection — IoC containers, Service Locator anti-pattern, testability benefits, and how to demonstrate DI mastery in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 8: Dependency Injection \& Inversion of Control](#-lld-topic-8-dependency-injection--inversion-of-control)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Why DI Matters](#why-di-matters)
  - [Constructor Injection](#constructor-injection)
    - [Why Constructor Injection Is Best](#why-constructor-injection-is-best)
  - [Setter Injection](#setter-injection)
    - [When to Use Setter Injection](#when-to-use-setter-injection)
  - [Interface Injection](#interface-injection)
  - [DI Without Frameworks](#di-without-frameworks)
  - [IoC Containers](#ioc-containers)
  - [Service Locator Anti-Pattern](#service-locator-anti-pattern)
  - [DI and Testability](#di-and-testability)
  - [DI in LLD Interview Problems](#di-in-lld-interview-problems)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Designing a Class](#when-designing-a-class)
    - [When Asked About Testing](#when-asked-about-testing)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Dependency Injection** is a technique where an object's dependencies are provided FROM OUTSIDE rather than created INSIDE. Instead of a class creating its own collaborators, they are "injected" by a caller.

**Inversion of Control (IoC)** is the broader principle: instead of your code calling a framework, the framework calls your code. DI is one form of IoC.

```
Without DI:                          With DI:
  class OrderService {                 class OrderService {
    db = new MySQL();  ← creates         OrderService(Database db) {
    email = new SMTP(); ← creates          this.db = db;    ← receives
  }                                      }
                                       }
  
  Problems:                            Benefits:
  - Can't swap MySQL for Postgres      - Swap DB without changing class
  - Can't mock for testing             - Easy to mock for testing
  - Hidden dependencies                - Dependencies are EXPLICIT
```

---

## Why DI Matters

```
1. TESTABILITY: Inject mocks instead of real databases/APIs
2. FLEXIBILITY: Swap implementations without changing code
3. EXPLICIT DEPENDENCIES: Constructor shows what a class needs
4. SINGLE RESPONSIBILITY: Class doesn't create its dependencies
5. OPEN/CLOSED: Add new implementations without modifying existing code
```

---

## Constructor Injection

> **The preferred approach. All required dependencies are provided through the constructor.**

```java
// ✅ BEST PRACTICE: Constructor injection
class OrderService {
    private final OrderRepository repository;      // final = must be set
    private final PaymentGateway paymentGateway;
    private final NotificationService notifier;
    
    // Constructor declares ALL dependencies
    OrderService(OrderRepository repo, PaymentGateway gateway, NotificationService notifier) {
        this.repository = Objects.requireNonNull(repo, "repository required");
        this.paymentGateway = Objects.requireNonNull(gateway, "gateway required");
        this.notifier = Objects.requireNonNull(notifier, "notifier required");
    }
    
    void placeOrder(Order order) {
        repository.save(order);
        paymentGateway.charge(order.getTotal());
        notifier.notify(order.getUserEmail(), "Order placed!");
    }
}
```

### Why Constructor Injection Is Best

```
1. IMMUTABILITY: Fields can be final → thread-safe, no accidental reassignment
2. COMPLETENESS: Object is fully initialized after construction
3. FAIL-FAST: Missing dependency = compile error or immediate NPE
4. DOCUMENTATION: Constructor is a CONTRACT of what the class needs
5. TESTABILITY: Easy to see what to mock
```

---

## Setter Injection

> **Dependencies set via setter methods after construction. Use for optional dependencies only.**

```java
class ReportService {
    private DataSource dataSource;          // Required
    private ReportFormatter formatter;      // Optional — has default
    
    ReportService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.formatter = new DefaultFormatter();  // Default
    }
    
    // Setter for optional override
    void setFormatter(ReportFormatter formatter) {
        this.formatter = formatter;
    }
    
    String generate() {
        List<Row> data = dataSource.query("SELECT *");
        return formatter.format(data);
    }
}
```

### When to Use Setter Injection

```
✅ Optional dependencies with sensible defaults
✅ Framework requirements (e.g., JavaBeans spec)
❌ Required dependencies (use constructor instead)
❌ When immutability matters (setters allow re-assignment)
```

---

## Interface Injection

> **The dependency provides an injector method that clients must implement.**

```java
interface DatabaseAware {
    void setDatabase(Database db);
}

class UserService implements DatabaseAware {
    private Database db;
    
    @Override
    public void setDatabase(Database db) {
        this.db = db;
    }
}
```

This approach is rarely used in modern Java. **Prefer constructor injection.**

---

## DI Without Frameworks

> **In LLD interviews, you should demonstrate manual DI — no Spring, no Guice.**

```java
// Interfaces
interface UserRepository {
    void save(User user);
    Optional<User> findById(String id);
}

interface PasswordHasher {
    String hash(String raw);
    boolean verify(String raw, String hashed);
}

interface EmailService {
    void send(String to, String subject, String body);
}

// Implementations
class InMemoryUserRepository implements UserRepository {
    private Map<String, User> store = new ConcurrentHashMap<>();
    public void save(User user) { store.put(user.getId(), user); }
    public Optional<User> findById(String id) { return Optional.ofNullable(store.get(id)); }
}

class BCryptHasher implements PasswordHasher {
    public String hash(String raw) { return BCrypt.hashpw(raw); }
    public boolean verify(String raw, String hashed) { return BCrypt.checkpw(raw, hashed); }
}

class SmtpEmailService implements EmailService {
    public void send(String to, String subject, String body) { /* SMTP logic */ }
}

// Service with DI
class UserRegistrationService {
    private final UserRepository repository;
    private final PasswordHasher hasher;
    private final EmailService emailService;
    
    UserRegistrationService(UserRepository repo, PasswordHasher hasher, EmailService email) {
        this.repository = repo;
        this.hasher = hasher;
        this.emailService = email;
    }
    
    void register(String name, String email, String password) {
        String hashed = hasher.hash(password);
        User user = new User(UUID.randomUUID().toString(), name, email, hashed);
        repository.save(user);
        emailService.send(email, "Welcome!", "Thanks for joining, " + name);
    }
}

// COMPOSITION ROOT: Wire everything together in main()
public class Application {
    public static void main(String[] args) {
        // Create implementations
        UserRepository repo = new InMemoryUserRepository();
        PasswordHasher hasher = new BCryptHasher();
        EmailService email = new SmtpEmailService();
        
        // Inject into service
        UserRegistrationService service = new UserRegistrationService(repo, hasher, email);
        
        // Use
        service.register("Alice", "alice@example.com", "password123");
    }
}
```

---

## IoC Containers

```
An IoC Container automates what we did in main():
  1. REGISTER: Tell container which interface maps to which implementation
  2. RESOLVE: Container creates objects with correct dependencies injected
  3. LIFECYCLE: Container manages object lifecycle (singleton, prototype, etc.)

Example concepts (not framework-specific):
  container.register(UserRepository.class, InMemoryUserRepository.class);
  container.register(PasswordHasher.class, BCryptHasher.class);
  container.register(EmailService.class, SmtpEmailService.class);
  
  UserRegistrationService service = container.resolve(UserRegistrationService.class);
  // Container creates all dependencies automatically!
```

---

## Service Locator Anti-Pattern

```java
// ❌ BAD: Service Locator — HIDES dependencies
class OrderService {
    void placeOrder(Order order) {
        // Dependencies are HIDDEN inside method body
        OrderRepository repo = ServiceLocator.get(OrderRepository.class);
        PaymentGateway payment = ServiceLocator.get(PaymentGateway.class);
        repo.save(order);
        payment.charge(order.getTotal());
    }
}
// Problems:
// 1. Can't see dependencies from outside
// 2. Hard to test — must configure ServiceLocator
// 3. Runtime failures instead of compile-time errors

// ✅ GOOD: Explicit constructor injection
class OrderService {
    private final OrderRepository repo;
    private final PaymentGateway payment;
    
    OrderService(OrderRepository repo, PaymentGateway payment) {
        this.repo = repo;          // Dependencies are VISIBLE
        this.payment = payment;
    }
}
```

---

## DI and Testability

```java
// With DI, testing is trivial — just inject mocks
class OrderServiceTest {
    
    @Test
    void shouldSaveOrderAndCharge() {
        // Arrange: Create mocks
        OrderRepository mockRepo = mock(OrderRepository.class);
        PaymentGateway mockPayment = mock(PaymentGateway.class);
        NotificationService mockNotif = mock(NotificationService.class);
        
        // Inject mocks
        OrderService service = new OrderService(mockRepo, mockPayment, mockNotif);
        Order order = new Order("item1", 100.0, "user@email.com");
        
        // Act
        service.placeOrder(order);
        
        // Assert
        verify(mockRepo).save(order);
        verify(mockPayment).charge(100.0);
        verify(mockNotif).notify("user@email.com", "Order placed!");
    }
    
    @Test
    void shouldHandlePaymentFailure() {
        OrderRepository mockRepo = mock(OrderRepository.class);
        PaymentGateway mockPayment = mock(PaymentGateway.class);
        when(mockPayment.charge(anyDouble())).thenThrow(new PaymentException("Declined"));
        
        OrderService service = new OrderService(mockRepo, mockPayment, mock(NotificationService.class));
        
        assertThrows(PaymentException.class, () -> service.placeOrder(order));
        verify(mockRepo, never()).save(any());  // Order NOT saved if payment fails
    }
}
```

---

## DI in LLD Interview Problems

| LLD Problem | Dependencies to Inject |
|---|---|
| **Parking Lot** | `PricingStrategy`, `SpotAssigner`, `PaymentProcessor` |
| **Notification System** | `NotificationChannel[]`, `TemplateEngine`, `UserRepository` |
| **Rate Limiter** | `RateLimitStore` (Redis/InMemory), `TimeProvider` |
| **Logging Framework** | `LogSink[]` (Console/File/Network), `Formatter` |
| **LRU Cache** | `EvictionPolicy`, `StorageBackend` |
| **Job Scheduler** | `JobStore`, `ExecutorService`, `TimeProvider` |

---

## Interview Talking Points & Scripts

### When Designing a Class

> *"I'll use constructor injection here — OrderService needs a repository, payment gateway, and notifier. Making them constructor parameters means the dependencies are explicit, the fields can be final for thread safety, and I can inject mocks for testing."*

### When Asked About Testing

> *"DI is what makes my code testable. Instead of OrderService creating a real MySQL connection, I inject a mock repository. I can test the business logic in isolation — verifying that the right methods are called with the right arguments — without needing a real database."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Creating dependencies with 'new' inside the class
   Fix: Inject via constructor. Only create LEAF objects (DTOs, value objects).

❌ Mistake 2: Too many constructor parameters (5+)
   Fix: Group related dependencies into a higher-level service.

❌ Mistake 3: Using Service Locator instead of DI
   Fix: Make dependencies explicit in the constructor.

❌ Mistake 4: Mentioning Spring/Guice in LLD interview
   Fix: Show manual DI — create interfaces, inject in main().

❌ Mistake 5: Making injected fields non-final
   Fix: Always make injected dependencies final.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│         DEPENDENCY INJECTION CHEAT SHEET                      │
├──────────────────┬───────────────────────────────────────────┤
│ Constructor DI   │ PREFERRED. All required deps in constructor│
│                  │ Fields are final. Object fully initialized.│
├──────────────────┼───────────────────────────────────────────┤
│ Setter DI        │ OPTIONAL deps only. Has sensible defaults. │
├──────────────────┼───────────────────────────────────────────┤
│ Manual DI        │ Wire in main(). No framework needed.       │
├──────────────────┼───────────────────────────────────────────┤
│ Service Locator  │ ANTI-PATTERN. Hides dependencies.          │
├──────────────────┼───────────────────────────────────────────┤
│ Testing          │ Inject mocks → test in isolation.          │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "I inject dependencies through the constructor so they're     │
│  explicit, final, and mockable — making the class testable,   │
│  flexible, and honest about what it needs."                   │
└──────────────────────────────────────────────────────────────┘
```
