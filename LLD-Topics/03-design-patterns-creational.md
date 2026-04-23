# 🎯 LLD Topic 3: Design Patterns — Creational

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering all five Creational Design Patterns — Singleton, Factory Method, Abstract Factory, Builder, Prototype — with Java examples, when to use each, real-world applications, and how to articulate pattern choices in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 3: Design Patterns — Creational](#-lld-topic-3-design-patterns--creational)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Singleton Pattern](#singleton-pattern)
    - [When to Use](#when-to-use)
    - [Thread-Safe Implementation (Recommended)](#thread-safe-implementation-recommended)
    - [Double-Checked Locking (Classic)](#double-checked-locking-classic)
    - [Bill Pugh Singleton (Lazy, Thread-Safe)](#bill-pugh-singleton-lazy-thread-safe)
    - [Singleton Pitfalls](#singleton-pitfalls)
  - [Factory Method Pattern](#factory-method-pattern)
    - [When to Use](#when-to-use-1)
    - [Implementation](#implementation)
    - [Factory Method with Subclasses](#factory-method-with-subclasses)
    - [Registry-Based Factory (Extensible)](#registry-based-factory-extensible)
  - [Abstract Factory Pattern](#abstract-factory-pattern)
    - [When to Use](#when-to-use-2)
    - [Implementation](#implementation-1)
    - [Factory Method vs Abstract Factory](#factory-method-vs-abstract-factory)
  - [Builder Pattern](#builder-pattern)
    - [When to Use](#when-to-use-3)
    - [Implementation](#implementation-2)
    - [Builder with Validation](#builder-with-validation)
  - [Prototype Pattern](#prototype-pattern)
    - [When to Use](#when-to-use-4)
    - [Implementation](#implementation-3)
  - [Creational Patterns Comparison](#creational-patterns-comparison)
  - [Choosing the Right Creational Pattern](#choosing-the-right-creational-pattern)
  - [Creational Patterns in LLD Problems](#creational-patterns-in-lld-problems)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Asked "When would you use Factory vs Builder?"](#when-asked-when-would-you-use-factory-vs-builder)
    - [When Implementing Singleton](#when-implementing-singleton)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Creational patterns deal with **object creation mechanisms** — they abstract the instantiation process so that the system is independent of how its objects are created, composed, and represented.

```
Why Creational Patterns matter:
  1. Decouple the CLIENT from the CONCRETE CLASS it instantiates
  2. Control WHEN, HOW, and HOW MANY objects are created
  3. Make it easy to introduce NEW types without changing client code
  4. Manage EXPENSIVE object creation (pooling, caching, lazy init)
```

> **Interview Gold**: *"I use creational patterns when the 'new' keyword would create tight coupling. Instead of clients knowing the exact class to instantiate, I centralize creation logic so I can change what gets created without touching the calling code."*

---

## Singleton Pattern

> **Ensure a class has only one instance and provide a global point of access to it.**

### When to Use

- Configuration managers, connection pools, thread pools, caches
- Objects that coordinate system-wide actions (logging, metrics)
- Objects where multiple instances would cause conflicts (file manager, print spooler)

### Thread-Safe Implementation (Recommended)

```java
// ✅ Best: Enum Singleton (thread-safe, serialization-safe, reflection-safe)
public enum DatabaseConnection {
    INSTANCE;
    
    private Connection connection;
    
    DatabaseConnection() {
        this.connection = createConnection();
    }
    
    public Connection getConnection() { return connection; }
    
    private Connection createConnection() {
        // Create actual DB connection
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/db");
    }
}

// Usage:
DatabaseConnection.INSTANCE.getConnection();
```

### Double-Checked Locking (Classic)

```java
public class ConnectionPool {
    private static volatile ConnectionPool instance;  // volatile is CRITICAL
    private final List<Connection> pool;
    
    private ConnectionPool(int poolSize) {
        pool = new ArrayList<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.add(createConnection());
        }
    }
    
    public static ConnectionPool getInstance() {
        if (instance == null) {                    // First check (no lock)
            synchronized (ConnectionPool.class) {
                if (instance == null) {            // Second check (with lock)
                    instance = new ConnectionPool(10);
                }
            }
        }
        return instance;
    }
    
    public synchronized Connection getConnection() {
        if (pool.isEmpty()) throw new RuntimeException("Pool exhausted");
        return pool.remove(pool.size() - 1);
    }
    
    public synchronized void returnConnection(Connection conn) {
        pool.add(conn);
    }
}
```

### Bill Pugh Singleton (Lazy, Thread-Safe)

```java
public class Logger {
    private Logger() {}
    
    // Inner class is not loaded until getInstance() is called
    private static class LoggerHolder {
        private static final Logger INSTANCE = new Logger();
    }
    
    public static Logger getInstance() {
        return LoggerHolder.INSTANCE;
    }
    
    public void log(String message) {
        System.out.println("[" + Instant.now() + "] " + message);
    }
}
```

### Singleton Pitfalls

```
Problems with Singleton:
  1. TESTING: Hard to mock — use DI to inject singleton instead of static access
  2. HIDDEN DEPENDENCIES: Code secretly depends on global state
  3. THREAD SAFETY: Must handle concurrent access explicitly
  4. SERIALIZATION: Deserialization can create a second instance

When to AVOID:
  - When you just want a "convenient global variable"
  - When the class has mutable state accessed from multiple threads
  - When testability is important (prefer DI instead)
```

---

## Factory Method Pattern

> **Define an interface for creating an object, but let subclasses decide which class to instantiate.**

### When to Use

- When a class can't anticipate the type of objects it needs to create
- When a class wants its subclasses to specify the objects it creates
- When you want to localize the knowledge of which class gets instantiated

### Implementation

```java
// Product interface
interface Notification {
    void send(String recipient, String message);
}

// Concrete products
class EmailNotification implements Notification {
    public void send(String recipient, String message) {
        System.out.println("Email to " + recipient + ": " + message);
    }
}

class SmsNotification implements Notification {
    public void send(String recipient, String message) {
        System.out.println("SMS to " + recipient + ": " + message);
    }
}

class PushNotification implements Notification {
    public void send(String recipient, String message) {
        System.out.println("Push to " + recipient + ": " + message);
    }
}

// Factory
class NotificationFactory {
    public static Notification createNotification(String channel) {
        return switch (channel.toLowerCase()) {
            case "email" -> new EmailNotification();
            case "sms" -> new SmsNotification();
            case "push" -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown channel: " + channel);
        };
    }
}

// Usage — client doesn't know concrete classes
Notification notif = NotificationFactory.createNotification("email");
notif.send("user@example.com", "Your order shipped!");
```

### Factory Method with Subclasses

```java
// Creator defines the factory method
abstract class DocumentCreator {
    // Factory method — subclasses decide what to create
    abstract Document createDocument();
    
    // Template method using the factory
    void openDocument(String path) {
        Document doc = createDocument();
        doc.load(path);
        doc.render();
    }
}

class PdfDocumentCreator extends DocumentCreator {
    Document createDocument() { return new PdfDocument(); }
}

class WordDocumentCreator extends DocumentCreator {
    Document createDocument() { return new WordDocument(); }
}

class SpreadsheetCreator extends DocumentCreator {
    Document createDocument() { return new SpreadsheetDocument(); }
}
```

### Registry-Based Factory (Extensible)

```java
class NotificationFactory {
    private static final Map<String, Supplier<Notification>> registry = new HashMap<>();
    
    // Register new types without modifying factory code!
    static void register(String type, Supplier<Notification> creator) {
        registry.put(type.toLowerCase(), creator);
    }
    
    static {
        register("email", EmailNotification::new);
        register("sms", SmsNotification::new);
        register("push", PushNotification::new);
    }
    
    public static Notification create(String type) {
        Supplier<Notification> creator = registry.get(type.toLowerCase());
        if (creator == null) throw new IllegalArgumentException("Unknown: " + type);
        return creator.get();
    }
}

// Adding new types without touching factory:
NotificationFactory.register("slack", SlackNotification::new);
```

---

## Abstract Factory Pattern

> **Provide an interface for creating families of related objects without specifying their concrete classes.**

### When to Use

- When the system needs to work with multiple families of products
- When you want to ensure that products from the same family are used together
- Cross-platform UI, theme systems, database abstraction

### Implementation

```java
// Abstract products
interface Button {
    void render();
    void onClick(Runnable action);
}

interface TextBox {
    void render();
    String getText();
}

interface CheckBox {
    void render();
    boolean isChecked();
}

// Abstract Factory
interface UIFactory {
    Button createButton(String label);
    TextBox createTextBox(String placeholder);
    CheckBox createCheckBox(String label);
}

// Concrete Family 1: Dark Theme
class DarkButton implements Button {
    private String label;
    DarkButton(String label) { this.label = label; }
    public void render() { System.out.println("[DARK BTN: " + label + "]"); }
    public void onClick(Runnable action) { action.run(); }
}

class DarkTextBox implements TextBox { /* dark-themed text box */ }
class DarkCheckBox implements CheckBox { /* dark-themed checkbox */ }

class DarkUIFactory implements UIFactory {
    public Button createButton(String label) { return new DarkButton(label); }
    public TextBox createTextBox(String ph) { return new DarkTextBox(ph); }
    public CheckBox createCheckBox(String label) { return new DarkCheckBox(label); }
}

// Concrete Family 2: Light Theme
class LightUIFactory implements UIFactory {
    public Button createButton(String label) { return new LightButton(label); }
    public TextBox createTextBox(String ph) { return new LightTextBox(ph); }
    public CheckBox createCheckBox(String label) { return new LightCheckBox(label); }
}

// Client code works with ANY factory — doesn't know which theme
class LoginScreen {
    private final Button loginButton;
    private final TextBox usernameField;
    private final TextBox passwordField;
    
    LoginScreen(UIFactory factory) {
        this.loginButton = factory.createButton("Login");
        this.usernameField = factory.createTextBox("Username");
        this.passwordField = factory.createTextBox("Password");
    }
    
    void render() {
        usernameField.render();
        passwordField.render();
        loginButton.render();
    }
}

// Usage:
UIFactory factory = isDarkMode ? new DarkUIFactory() : new LightUIFactory();
LoginScreen screen = new LoginScreen(factory);
```

### Factory Method vs Abstract Factory

```
Factory Method:
  - Creates ONE product type
  - Uses inheritance (subclass decides)
  - Single method creates the object

Abstract Factory:
  - Creates FAMILIES of related products
  - Uses composition (factory object injected)
  - Multiple methods create different products
  - Ensures products from same family are compatible
```

---

## Builder Pattern

> **Separate the construction of a complex object from its representation, allowing the same construction process to create different representations.**

### When to Use

- Objects with many optional parameters (avoid telescoping constructors)
- Objects that require step-by-step construction
- Immutable objects with many fields

### Implementation

```java
class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final int timeout;
    private final boolean followRedirects;
    private final String contentType;
    
    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = Collections.unmodifiableMap(builder.headers);
        this.body = builder.body;
        this.timeout = builder.timeout;
        this.followRedirects = builder.followRedirects;
        this.contentType = builder.contentType;
    }
    
    // Getters only — immutable
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public Map<String, String> getHeaders() { return headers; }
    
    public static class Builder {
        // Required
        private final String method;
        private final String url;
        
        // Optional with defaults
        private Map<String, String> headers = new HashMap<>();
        private String body = "";
        private int timeout = 30000;
        private boolean followRedirects = true;
        private String contentType = "application/json";
        
        public Builder(String method, String url) {
            this.method = method;
            this.url = url;
        }
        
        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }
        
        public Builder body(String body) {
            this.body = body;
            return this;
        }
        
        public Builder timeout(int ms) {
            this.timeout = ms;
            return this;
        }
        
        public Builder followRedirects(boolean follow) {
            this.followRedirects = follow;
            return this;
        }
        
        public Builder contentType(String type) {
            this.contentType = type;
            return this;
        }
        
        public HttpRequest build() {
            // Validation before building
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("URL is required");
            }
            return new HttpRequest(this);
        }
    }
}

// Usage — readable and flexible
HttpRequest request = new HttpRequest.Builder("POST", "https://api.example.com/orders")
    .header("Authorization", "Bearer token123")
    .header("X-Request-Id", UUID.randomUUID().toString())
    .body("{\"item\": \"laptop\", \"qty\": 1}")
    .timeout(5000)
    .contentType("application/json")
    .build();
```

### Builder with Validation

```java
public HttpRequest build() {
    List<String> errors = new ArrayList<>();
    if (url == null) errors.add("URL is required");
    if ("POST".equals(method) && body.isEmpty()) errors.add("POST requires a body");
    if (timeout <= 0) errors.add("Timeout must be positive");
    
    if (!errors.isEmpty()) {
        throw new IllegalStateException("Invalid request: " + String.join(", ", errors));
    }
    return new HttpRequest(this);
}
```

---

## Prototype Pattern

> **Create new objects by copying an existing object (prototype) rather than building from scratch.**

### When to Use

- Object creation is expensive (deep initialization, DB calls, network calls)
- You need copies with slight variations
- Runtime configuration determines the type of objects to create

### Implementation

```java
interface GameUnit extends Cloneable {
    GameUnit clone();
    void setPosition(int x, int y);
    String getType();
}

class Soldier implements GameUnit {
    private String type;
    private int health;
    private int attack;
    private int defense;
    private int x, y;
    private List<String> equipment;
    
    public Soldier(String type, int health, int attack, int defense, List<String> equipment) {
        this.type = type;
        this.health = health;
        this.attack = attack;
        this.defense = defense;
        this.equipment = new ArrayList<>(equipment);
        // Simulate expensive initialization
        loadAnimations();
        loadSoundEffects();
    }
    
    @Override
    public GameUnit clone() {
        Soldier copy = new Soldier(type, health, attack, defense, new ArrayList<>(equipment));
        // Deep copy — each clone gets its own equipment list
        return copy;
    }
    
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public String getType() { return type; }
}

// Prototype Registry
class UnitRegistry {
    private Map<String, GameUnit> prototypes = new HashMap<>();
    
    void register(String key, GameUnit prototype) {
        prototypes.put(key, prototype);
    }
    
    GameUnit create(String key) {
        GameUnit prototype = prototypes.get(key);
        if (prototype == null) throw new IllegalArgumentException("Unknown unit: " + key);
        return prototype.clone();
    }
}

// Usage:
UnitRegistry registry = new UnitRegistry();
registry.register("infantry", new Soldier("Infantry", 100, 15, 10, List.of("Rifle", "Helmet")));
registry.register("heavy", new Soldier("Heavy", 200, 25, 20, List.of("MG", "Armor", "Helmet")));

// Create 50 infantry soldiers — cloning, not constructing from scratch
for (int i = 0; i < 50; i++) {
    GameUnit soldier = registry.create("infantry");
    soldier.setPosition(i * 10, 0);
}
```

---

## Creational Patterns Comparison

| Pattern | Purpose | Creates | Key Mechanism |
|---|---|---|---|
| **Singleton** | One instance only | Single object | Private constructor + static access |
| **Factory Method** | Delegate creation to subclass | One product | Inheritance + polymorphism |
| **Abstract Factory** | Create families of products | Product family | Composition + interface |
| **Builder** | Complex object step-by-step | Complex object | Fluent API + validation |
| **Prototype** | Clone existing objects | Copy of existing | Cloning |

---

## Choosing the Right Creational Pattern

```
Decision flow:
  1. Do you need exactly ONE instance? → Singleton
  2. Is the object complex with many optional params? → Builder
  3. Is object creation expensive and you need copies? → Prototype
  4. Do you need to create FAMILIES of related objects? → Abstract Factory
  5. Do you want subclasses to decide what to create? → Factory Method
  6. Do you just need to encapsulate "new"? → Simple Factory
```

---

## Creational Patterns in LLD Problems

| LLD Problem | Pattern | Why |
|---|---|---|
| **Parking Lot** | Factory Method | Create different vehicle types (Car, Truck, Bike) |
| **Chess** | Prototype | Clone board positions for undo/AI evaluation |
| **Logging Framework** | Singleton | One logger per application |
| **Notification System** | Abstract Factory | Families: Email+Template, SMS+Template, Push+Template |
| **HTTP Client** | Builder | Many optional params (headers, timeout, body) |
| **Game Units** | Prototype + Factory | Clone base units, factory creates by type |
| **Database Connection** | Singleton + Pool | One pool, managed connections |

---

## Interview Talking Points & Scripts

### When Asked "When would you use Factory vs Builder?"

> *"Factory is about WHAT gets created — decoupling the client from the concrete class. Builder is about HOW it gets created — simplifying the construction of complex objects with many optional parameters. I use Factory when I have a type hierarchy and the client shouldn't know which subclass to instantiate. I use Builder when I have one class but it has 5+ constructor parameters with many optional ones."*

### When Implementing Singleton

> *"I'll use the enum singleton approach since it's thread-safe, prevents reflection attacks, and handles serialization correctly. However, for testability, I'll inject this dependency rather than accessing it statically — so I can mock it in unit tests."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Using Singleton as a global variable dump
   Fix: Singleton is for ensuring ONE INSTANCE, not for convenience.

❌ Mistake 2: Not making Singleton thread-safe
   Fix: Use enum, Bill Pugh, or double-checked locking with volatile.

❌ Mistake 3: Confusing Factory Method with Simple Factory
   Fix: Factory Method uses INHERITANCE (subclass overrides). 
   Simple Factory is just a method with switch/if-else.

❌ Mistake 4: Using Builder when constructor has 2-3 params
   Fix: Builder shines with 5+ params. For 2-3, a simple constructor is fine.

❌ Mistake 5: Shallow copy in Prototype when deep copy is needed
   Fix: Always deep-copy mutable fields (lists, maps, nested objects).
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               CREATIONAL PATTERNS CHEAT SHEET                 │
├──────────────────┬───────────────────────────────────────────┤
│ Singleton        │ One instance, global access                │
│                  │ Use: Config, pool, logger                  │
│                  │ Prefer: Enum or Bill Pugh idiom            │
├──────────────────┼───────────────────────────────────────────┤
│ Factory Method   │ Subclass decides what to create            │
│                  │ Use: Type hierarchies, plugin systems       │
│                  │ Key: Returns interface, hides concrete type │
├──────────────────┼───────────────────────────────────────────┤
│ Abstract Factory │ Create families of related objects          │
│                  │ Use: Themes, cross-platform, DB abstraction │
│                  │ Key: Ensures product compatibility          │
├──────────────────┼───────────────────────────────────────────┤
│ Builder          │ Complex object, step by step               │
│                  │ Use: 5+ params, immutable objects           │
│                  │ Key: Fluent API + build() validation        │
├──────────────────┼───────────────────────────────────────────┤
│ Prototype        │ Clone existing objects                     │
│                  │ Use: Expensive init, config-driven creation │
│                  │ Key: Deep copy mutable fields!              │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "Creational patterns let me decouple WHAT is created from     │
│  WHERE it's created — so I can add new types, change          │
│  implementations, and control lifecycle without touching       │
│  the client code."                                            │
└──────────────────────────────────────────────────────────────┘
```
