# 🎯 Topic 9: Design Patterns in Java

> **Java Interview — Deep Dive**
> A comprehensive guide covering Creational, Structural, and Behavioral design patterns with Java implementations and interview-ready explanations.

---

## Table of Contents

1. [Creational Patterns](#creational-patterns)
2. [Structural Patterns](#structural-patterns)
3. [Behavioral Patterns](#behavioral-patterns)
4. [Interview Talking Points](#interview-talking-points)
5. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Creational Patterns

### 1. Singleton — One Instance Only

```java
// Thread-safe with Bill Pugh (BEST approach)
public class Singleton {
    private Singleton() {}
    
    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
    }
    
    public static Singleton getInstance() {
        return Holder.INSTANCE;  // Loaded only when accessed (lazy + thread-safe)
    }
}

// Enum Singleton (Effective Java recommended — serialization-safe)
public enum DatabaseConnection {
    INSTANCE;
    
    public void connect() { /* ... */ }
}
```

**When to use**: Logger, Configuration, Connection Pool, Thread Pool
**Interview Key**: Bill Pugh (inner class) or Enum approach. Avoid double-checked locking complexity.

### 2. Factory Method — Let Subclasses Decide

```java
public interface Notification { void send(String message); }
public class EmailNotification implements Notification { /* ... */ }
public class SMSNotification implements Notification { /* ... */ }
public class PushNotification implements Notification { /* ... */ }

public class NotificationFactory {
    public static Notification create(String type) {
        return switch (type) {
            case "email" -> new EmailNotification();
            case "sms"   -> new SMSNotification();
            case "push"  -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown: " + type);
        };
    }
}

Notification n = NotificationFactory.create("email");
```

**When to use**: Object creation logic is complex, type determined at runtime
**Real-world**: `Calendar.getInstance()`, `NumberFormat.getInstance()`

### 3. Abstract Factory — Factory of Factories

```java
public interface UIFactory {
    Button createButton();
    TextField createTextField();
}

public class DarkThemeFactory implements UIFactory {
    public Button createButton() { return new DarkButton(); }
    public TextField createTextField() { return new DarkTextField(); }
}

public class LightThemeFactory implements UIFactory {
    public Button createButton() { return new LightButton(); }
    public TextField createTextField() { return new LightTextField(); }
}
```

### 4. Builder — Complex Object Construction

```java
public class User {
    private final String name;      // Required
    private final String email;     // Required
    private final int age;          // Optional
    private final String phone;     // Optional
    
    private User(Builder builder) {
        this.name = builder.name;
        this.email = builder.email;
        this.age = builder.age;
        this.phone = builder.phone;
    }
    
    public static class Builder {
        private final String name;    // Required
        private final String email;   // Required
        private int age;
        private String phone;
        
        public Builder(String name, String email) {
            this.name = name;
            this.email = email;
        }
        
        public Builder age(int age) { this.age = age; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        
        public User build() {
            validate();
            return new User(this);
        }
        
        private void validate() {
            if (name == null || name.isEmpty()) throw new IllegalStateException("Name required");
        }
    }
}

User user = new User.Builder("Alice", "alice@email.com")
    .age(30)
    .phone("123-456")
    .build();
```

**When to use**: Many optional parameters, immutable objects, telescoping constructor problem
**Real-world**: `StringBuilder`, `Stream.Builder`, Lombok `@Builder`

### 5. Prototype — Clone Existing Objects

```java
public class GameConfig implements Cloneable {
    private Map<String, String> settings;
    
    @Override
    public GameConfig clone() {
        GameConfig copy = new GameConfig();
        copy.settings = new HashMap<>(this.settings);  // Deep copy
        return copy;
    }
}
```

---

## Structural Patterns

### 6. Adapter — Make Incompatible Interfaces Work Together

```java
// Target interface
public interface MediaPlayer { void play(String filename); }

// Adaptee (third-party, can't modify)
public class VlcPlayer { void playVlc(String file) { /* ... */ } }

// Adapter
public class VlcAdapter implements MediaPlayer {
    private final VlcPlayer vlcPlayer = new VlcPlayer();
    
    @Override
    public void play(String filename) {
        vlcPlayer.playVlc(filename);  // Delegates to incompatible interface
    }
}
```

**Real-world**: `Arrays.asList()`, `InputStreamReader` (InputStream → Reader)

### 7. Decorator — Add Behavior Dynamically

```java
public interface Coffee { double cost(); String description(); }

public class SimpleCoffee implements Coffee {
    public double cost() { return 1.0; }
    public String description() { return "Coffee"; }
}

public abstract class CoffeeDecorator implements Coffee {
    protected final Coffee coffee;
    public CoffeeDecorator(Coffee coffee) { this.coffee = coffee; }
}

public class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee coffee) { super(coffee); }
    public double cost() { return coffee.cost() + 0.5; }
    public String description() { return coffee.description() + " + Milk"; }
}

public class SugarDecorator extends CoffeeDecorator {
    public SugarDecorator(Coffee coffee) { super(coffee); }
    public double cost() { return coffee.cost() + 0.3; }
    public String description() { return coffee.description() + " + Sugar"; }
}

// Usage — stack decorators
Coffee order = new SugarDecorator(new MilkDecorator(new SimpleCoffee()));
// cost: 1.8, description: "Coffee + Milk + Sugar"
```

**Real-world**: `BufferedInputStream(new FileInputStream())`, Java I/O streams

### 8. Proxy — Controlled Access

```java
public interface Image { void display(); }

public class RealImage implements Image {
    private String filename;
    public RealImage(String f) { this.filename = f; loadFromDisk(); }
    private void loadFromDisk() { /* expensive */ }
    public void display() { System.out.println("Displaying: " + filename); }
}

public class ProxyImage implements Image {
    private RealImage realImage;
    private String filename;
    
    public ProxyImage(String f) { this.filename = f; }
    
    public void display() {
        if (realImage == null) {
            realImage = new RealImage(filename);  // Lazy loading
        }
        realImage.display();
    }
}
```

**Types**: Virtual (lazy loading), Protection (access control), Remote (network), Caching

### 9. Facade — Simplified Interface to Complex System

```java
public class OrderFacade {
    private final InventoryService inventory;
    private final PaymentService payment;
    private final ShippingService shipping;
    
    public OrderResult placeOrder(Order order) {
        if (!inventory.check(order)) throw new OutOfStockException();
        PaymentResult pay = payment.process(order);
        ShipmentResult ship = shipping.schedule(order);
        return new OrderResult(pay, ship);
    }
}
```

---

## Behavioral Patterns

### 10. Strategy — Swap Algorithms at Runtime

```java
@FunctionalInterface
public interface SortStrategy { void sort(int[] array); }

public class Sorter {
    private SortStrategy strategy;
    
    public Sorter(SortStrategy strategy) { this.strategy = strategy; }
    public void setStrategy(SortStrategy s) { this.strategy = s; }
    public void sort(int[] data) { strategy.sort(data); }
}

// With lambdas (Java 8+)
Sorter sorter = new Sorter(Arrays::sort);          // Quick sort
sorter.setStrategy(arr -> { /* bubble sort */ });   // Swap at runtime
```

**Real-world**: `Comparator`, `Collections.sort(list, comparator)`

### 11. Observer — Pub/Sub Event System

```java
public interface EventListener { void update(String event, Object data); }

public class EventManager {
    private final Map<String, List<EventListener>> listeners = new HashMap<>();
    
    public void subscribe(String event, EventListener listener) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(listener);
    }
    
    public void notify(String event, Object data) {
        listeners.getOrDefault(event, List.of())
            .forEach(l -> l.update(event, data));
    }
}
```

**Real-world**: Java `PropertyChangeListener`, Spring Events, GUI listeners

### 12. Template Method — Define Algorithm Skeleton

```java
public abstract class DataProcessor {
    // Template method — defines the algorithm
    public final void process() {
        readData();
        processData();
        writeData();
    }
    
    protected abstract void readData();
    protected abstract void processData();
    
    protected void writeData() {  // Default implementation
        System.out.println("Writing to output...");
    }
}

public class CSVProcessor extends DataProcessor {
    protected void readData() { /* CSV specific */ }
    protected void processData() { /* CSV specific */ }
}
```

### 13. Chain of Responsibility

```java
public abstract class Handler {
    private Handler next;
    
    public Handler setNext(Handler next) { this.next = next; return next; }
    
    public void handle(Request request) {
        if (canHandle(request)) {
            process(request);
        } else if (next != null) {
            next.handle(request);
        }
    }
    
    protected abstract boolean canHandle(Request request);
    protected abstract void process(Request request);
}

// Chain: Auth → RateLimit → Validation → Handler
authHandler.setNext(rateLimitHandler).setNext(validationHandler).setNext(businessHandler);
```

### 14. Iterator

```java
// Already built into Java Collections!
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String item = it.next();
    if (shouldRemove(item)) it.remove();
}

// Enhanced for-loop uses Iterable/Iterator under the hood
for (String item : list) { /* ... */ }
```

---

## Interview Talking Points

### "Which design patterns have you used?"

> *"I regularly use Builder for constructing complex immutable objects — it solves the telescoping constructor problem. Strategy pattern with Java 8 lambdas for pluggable algorithms like sorting or validation rules. Observer for event-driven architectures. Factory Method when object creation depends on runtime configuration. And Decorator — it's fundamental to Java I/O streams where you wrap BufferedInputStream around FileInputStream."*

### "Explain Singleton and its problems"

> *"Singleton ensures one instance globally. The best Java implementation is the Bill Pugh idiom using a static inner class — it's lazy, thread-safe, and simple. Or an enum singleton for serialization safety. The downsides: it's essentially a global variable, makes testing hard because you can't easily mock it, and hides dependencies. In modern apps, I'd use dependency injection with a singleton scope instead."*

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│           DESIGN PATTERNS CHEAT SHEET                       │
├────────────────────────────────────────────────────────────┤
│ CREATIONAL:                                                 │
│ • Singleton → Bill Pugh / Enum (one instance)              │
│ • Factory → Create based on type/config                    │
│ • Builder → Complex objects with many optional params       │
│ • Prototype → Clone existing objects                        │
│                                                            │
│ STRUCTURAL:                                                 │
│ • Adapter → Convert incompatible interfaces                │
│ • Decorator → Add behavior dynamically (Java I/O)          │
│ • Proxy → Controlled access (lazy, cache, security)        │
│ • Facade → Simplified API for complex subsystem            │
│                                                            │
│ BEHAVIORAL:                                                 │
│ • Strategy → Swap algorithms (Comparator, lambdas)         │
│ • Observer → Event/notification system                      │
│ • Template Method → Algorithm skeleton with hooks           │
│ • Chain of Responsibility → Handler pipeline               │
│ • Iterator → Sequential access (built into Collections)    │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Serialization & Cloning →](./10-serialization-and-cloning.md)
