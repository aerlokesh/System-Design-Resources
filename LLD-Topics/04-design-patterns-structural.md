# 🎯 LLD Topic 4: Design Patterns — Structural

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering all seven Structural Design Patterns — Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy — with Java examples, real-world applications, and how to articulate structural pattern choices in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 4: Design Patterns — Structural](#-lld-topic-4-design-patterns--structural)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Adapter Pattern](#adapter-pattern)
    - [When to Use](#when-to-use)
    - [Implementation](#implementation)
    - [Two-Way Adapter](#two-way-adapter)
  - [Bridge Pattern](#bridge-pattern)
    - [When to Use](#when-to-use-1)
    - [Problem: Class Explosion Without Bridge](#problem-class-explosion-without-bridge)
    - [Implementation](#implementation-1)
  - [Composite Pattern](#composite-pattern)
    - [When to Use](#when-to-use-2)
    - [Implementation](#implementation-2)
  - [Decorator Pattern](#decorator-pattern)
    - [When to Use](#when-to-use-3)
    - [Implementation](#implementation-3)
    - [Java I/O: Classic Decorator Example](#java-io-classic-decorator-example)
  - [Facade Pattern](#facade-pattern)
    - [When to Use](#when-to-use-4)
    - [Implementation](#implementation-4)
  - [Flyweight Pattern](#flyweight-pattern)
    - [When to Use](#when-to-use-5)
    - [Implementation](#implementation-5)
  - [Proxy Pattern](#proxy-pattern)
    - [Types and Implementation](#types-and-implementation)
  - [Structural Patterns Comparison](#structural-patterns-comparison)
  - [Structural Patterns in LLD Problems](#structural-patterns-in-lld-problems)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Asked "Decorator vs Proxy?"](#when-asked-decorator-vs-proxy)
    - [When Asked "Adapter vs Bridge?"](#when-asked-adapter-vs-bridge)
    - [When Asked "When would you use Facade?"](#when-asked-when-would-you-use-facade)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Structural patterns deal with **how classes and objects are composed** to form larger structures. They help ensure that when one part of a system changes, the entire structure doesn't need to change. They focus on **simplifying relationships** between entities.

```
Why Structural Patterns matter:
  1. Make INCOMPATIBLE interfaces work together (Adapter)
  2. SEPARATE abstraction from implementation (Bridge)
  3. Treat INDIVIDUAL objects and COMPOSITIONS uniformly (Composite)
  4. Add BEHAVIOR dynamically without subclassing (Decorator)
  5. Provide SIMPLE interfaces to complex subsystems (Facade)
  6. SHARE objects efficiently to save memory (Flyweight)
  7. CONTROL access to objects (Proxy)
```

---

## Adapter Pattern

> **Convert the interface of a class into another interface clients expect. Lets classes work together that couldn't otherwise because of incompatible interfaces.**

### When to Use

- Integrating legacy systems with new code
- Using third-party libraries that don't match your interface
- Wrapping external APIs to match your domain model

### Implementation

```java
// Your system's interface (Target)
interface PaymentProcessor {
    PaymentResult processPayment(String orderId, double amount, String currency);
}

// Third-party Stripe SDK (Adaptee) — interface you CAN'T change
class StripeSDK {
    StripeCharge createCharge(int amountInCents, String currencyCode, Map<String, String> metadata) {
        // Stripe's actual API
        return new StripeCharge(/* ... */);
    }
}

// Adapter: Makes Stripe look like your PaymentProcessor
class StripePaymentAdapter implements PaymentProcessor {
    private final StripeSDK stripe;
    
    StripePaymentAdapter(StripeSDK stripe) {
        this.stripe = stripe;
    }
    
    @Override
    public PaymentResult processPayment(String orderId, double amount, String currency) {
        // Convert YOUR interface to STRIPE's interface
        int cents = (int) (amount * 100);
        Map<String, String> metadata = Map.of("orderId", orderId);
        
        StripeCharge charge = stripe.createCharge(cents, currency, metadata);
        
        // Convert STRIPE's response to YOUR domain model
        return new PaymentResult(
            charge.getId(),
            charge.getStatus().equals("succeeded"),
            charge.getAmount() / 100.0
        );
    }
}

// Client code works with YOUR interface — doesn't know about Stripe
class OrderService {
    private final PaymentProcessor paymentProcessor;  // Could be Stripe, PayPal, etc.
    
    void checkout(Order order) {
        PaymentResult result = paymentProcessor.processPayment(
            order.getId(), order.getTotal(), "USD"
        );
    }
}
```

### Two-Way Adapter

```java
// Adapter that converts BOTH directions (for legacy migration)
class LegacyDatabaseAdapter implements ModernRepository {
    private final LegacyDAO legacyDao;
    
    // Modern → Legacy
    public void save(User user) {
        LegacyUserRecord record = convertToLegacy(user);
        legacyDao.insertRecord(record);
    }
    
    // Legacy → Modern
    public Optional<User> findById(String id) {
        LegacyUserRecord record = legacyDao.fetchById(id);
        return record != null ? Optional.of(convertToModern(record)) : Optional.empty();
    }
}
```

---

## Bridge Pattern

> **Decouple an abstraction from its implementation so that the two can vary independently.**

### When to Use

- When you have multiple dimensions of variation (e.g., shape × color, platform × renderer)
- When you want to avoid a combinatorial explosion of classes
- When both abstraction and implementation should be extensible

### Problem: Class Explosion Without Bridge

```
Without Bridge (inheritance):
  Shape → Circle, Square, Triangle
  Color → Red, Blue, Green
  
  Classes needed: RedCircle, BlueCircle, GreenCircle,
                  RedSquare, BlueSquare, GreenSquare,
                  RedTriangle, BlueTriangle, GreenTriangle
  = 9 classes (3 shapes × 3 colors)
  
  Add one color → 3 more classes. Add one shape → 3 more classes.
```

### Implementation

```java
// Implementation interface (one dimension)
interface MessageSender {
    void sendMessage(String recipient, String content);
}

class EmailSender implements MessageSender {
    public void sendMessage(String recipient, String content) {
        System.out.println("Email to " + recipient + ": " + content);
    }
}

class SmsSender implements MessageSender {
    public void sendMessage(String recipient, String content) {
        System.out.println("SMS to " + recipient + ": " + content);
    }
}

class SlackSender implements MessageSender {
    public void sendMessage(String recipient, String content) {
        System.out.println("Slack to " + recipient + ": " + content);
    }
}

// Abstraction (other dimension)
abstract class Notification {
    protected MessageSender sender;  // Bridge to implementation
    
    Notification(MessageSender sender) {
        this.sender = sender;
    }
    
    abstract void notify(String recipient, String event);
}

class UrgentNotification extends Notification {
    UrgentNotification(MessageSender sender) { super(sender); }
    
    void notify(String recipient, String event) {
        sender.sendMessage(recipient, "🚨 URGENT: " + event);
        sender.sendMessage(recipient, "Please respond immediately!");
    }
}

class RegularNotification extends Notification {
    RegularNotification(MessageSender sender) { super(sender); }
    
    void notify(String recipient, String event) {
        sender.sendMessage(recipient, "Info: " + event);
    }
}

// Usage: Any notification type × Any sender — no class explosion
Notification urgentEmail = new UrgentNotification(new EmailSender());
Notification regularSlack = new RegularNotification(new SlackSender());
```

---

## Composite Pattern

> **Compose objects into tree structures to represent part-whole hierarchies. Let clients treat individual objects and compositions uniformly.**

### When to Use

- File system (files and directories)
- UI component trees
- Organization hierarchies
- Menu systems with submenus

### Implementation

```java
// Component — common interface for leaf and composite
interface FileSystemItem {
    String getName();
    long getSize();
    void display(String indent);
}

// Leaf — individual file
class File implements FileSystemItem {
    private String name;
    private long size;
    
    File(String name, long size) {
        this.name = name;
        this.size = size;
    }
    
    public String getName() { return name; }
    public long getSize() { return size; }
    public void display(String indent) {
        System.out.println(indent + "📄 " + name + " (" + size + " bytes)");
    }
}

// Composite — directory containing files and other directories
class Directory implements FileSystemItem {
    private String name;
    private List<FileSystemItem> children = new ArrayList<>();
    
    Directory(String name) { this.name = name; }
    
    void add(FileSystemItem item) { children.add(item); }
    void remove(FileSystemItem item) { children.remove(item); }
    
    public String getName() { return name; }
    
    public long getSize() {
        return children.stream().mapToLong(FileSystemItem::getSize).sum();
    }
    
    public void display(String indent) {
        System.out.println(indent + "📁 " + name + " (" + getSize() + " bytes)");
        for (FileSystemItem child : children) {
            child.display(indent + "  ");
        }
    }
}

// Usage — treat files and directories uniformly
Directory root = new Directory("project");
Directory src = new Directory("src");
src.add(new File("Main.java", 2048));
src.add(new File("Utils.java", 1024));
root.add(src);
root.add(new File("README.md", 512));
root.display("");  // Recursively displays entire tree
// root.getSize() → 3584 (aggregates all children)
```

---

## Decorator Pattern

> **Attach additional responsibilities to an object dynamically. Provides a flexible alternative to subclassing for extending functionality.**

### When to Use

- Adding features to objects without modifying their class
- When you need combinations of features (avoiding class explosion)
- Streams (Java I/O), middleware chains, logging wrappers

### Implementation

```java
// Component interface
interface DataSource {
    void writeData(String data);
    String readData();
}

// Concrete component
class FileDataSource implements DataSource {
    private String filename;
    private String content = "";
    
    FileDataSource(String filename) { this.filename = filename; }
    
    public void writeData(String data) { this.content = data; }
    public String readData() { return content; }
}

// Base decorator
abstract class DataSourceDecorator implements DataSource {
    protected DataSource wrappee;
    
    DataSourceDecorator(DataSource source) { this.wrappee = source; }
    
    public void writeData(String data) { wrappee.writeData(data); }
    public String readData() { return wrappee.readData(); }
}

// Concrete decorator 1: Encryption
class EncryptionDecorator extends DataSourceDecorator {
    EncryptionDecorator(DataSource source) { super(source); }
    
    public void writeData(String data) {
        String encrypted = encrypt(data);
        super.writeData(encrypted);
    }
    
    public String readData() {
        return decrypt(super.readData());
    }
    
    private String encrypt(String data) { return Base64.encode(data); }
    private String decrypt(String data) { return Base64.decode(data); }
}

// Concrete decorator 2: Compression
class CompressionDecorator extends DataSourceDecorator {
    CompressionDecorator(DataSource source) { super(source); }
    
    public void writeData(String data) {
        String compressed = compress(data);
        super.writeData(compressed);
    }
    
    public String readData() {
        return decompress(super.readData());
    }
}

// Concrete decorator 3: Logging
class LoggingDecorator extends DataSourceDecorator {
    LoggingDecorator(DataSource source) { super(source); }
    
    public void writeData(String data) {
        System.out.println("Writing " + data.length() + " bytes");
        super.writeData(data);
    }
}

// Usage — stack decorators in any combination
DataSource source = new FileDataSource("data.txt");

// Encrypt, then compress, then log
source = new LoggingDecorator(
    new CompressionDecorator(
        new EncryptionDecorator(source)
    )
);

source.writeData("sensitive data");  // Logs → Compresses → Encrypts → Writes
```

### Java I/O: Classic Decorator Example

```java
// Java's InputStream is a textbook Decorator pattern
InputStream input = new BufferedInputStream(      // Decorator: buffering
    new GZIPInputStream(                          // Decorator: decompression
        new FileInputStream("data.gz")            // Concrete component
    )
);
```

---

## Facade Pattern

> **Provide a unified interface to a set of interfaces in a subsystem. Defines a higher-level interface that makes the subsystem easier to use.**

### When to Use

- Simplifying complex subsystems
- Providing a clean API layer over messy internals
- Reducing dependencies — clients depend on facade, not subsystem classes

### Implementation

```java
// Complex subsystems
class InventoryService {
    boolean checkStock(String productId, int quantity) { /* ... */ return true; }
    void reserveStock(String productId, int quantity) { /* ... */ }
    void releaseStock(String productId, int quantity) { /* ... */ }
}

class PaymentService {
    PaymentResult charge(String customerId, double amount) { /* ... */ return new PaymentResult(); }
    void refund(String transactionId) { /* ... */ }
}

class ShippingService {
    String createShipment(String orderId, Address address) { /* ... */ return "TRACK123"; }
    double calculateCost(Address from, Address to, double weight) { /* ... */ return 5.99; }
}

class NotificationService {
    void sendOrderConfirmation(String email, String orderId) { /* ... */ }
    void sendShippingNotification(String email, String trackingId) { /* ... */ }
}

// FACADE: Simple interface over complex subsystems
class OrderFacade {
    private final InventoryService inventory;
    private final PaymentService payment;
    private final ShippingService shipping;
    private final NotificationService notification;
    
    OrderFacade(InventoryService inv, PaymentService pay, 
                ShippingService ship, NotificationService notif) {
        this.inventory = inv;
        this.payment = pay;
        this.shipping = ship;
        this.notification = notif;
    }
    
    // ONE method hides 5+ subsystem calls
    OrderResult placeOrder(String customerId, String productId, 
                           int quantity, Address address) {
        // 1. Check inventory
        if (!inventory.checkStock(productId, quantity)) {
            return OrderResult.outOfStock();
        }
        
        // 2. Reserve stock
        inventory.reserveStock(productId, quantity);
        
        // 3. Calculate total
        double shippingCost = shipping.calculateCost(WAREHOUSE, address, quantity * 0.5);
        double total = calculateItemCost(productId, quantity) + shippingCost;
        
        // 4. Charge payment
        PaymentResult payResult = payment.charge(customerId, total);
        if (!payResult.isSuccess()) {
            inventory.releaseStock(productId, quantity);
            return OrderResult.paymentFailed();
        }
        
        // 5. Create shipment
        String trackingId = shipping.createShipment(orderId, address);
        
        // 6. Notify customer
        notification.sendOrderConfirmation(customerId, orderId);
        
        return OrderResult.success(orderId, trackingId);
    }
}

// Client: Simple!
OrderResult result = orderFacade.placeOrder("cust123", "prod456", 2, address);
```

---

## Flyweight Pattern

> **Use sharing to support large numbers of fine-grained objects efficiently.**

### When to Use

- Large number of similar objects consuming too much memory
- Objects have shared intrinsic state and variable extrinsic state
- Character rendering, game tiles, icon systems

### Implementation

```java
// Flyweight: Shared intrinsic state
class CharacterStyle {
    private final String fontFamily;    // Shared
    private final int fontSize;         // Shared
    private final String color;         // Shared
    private final boolean bold;         // Shared
    
    CharacterStyle(String font, int size, String color, boolean bold) {
        this.fontFamily = font;
        this.fontSize = size;
        this.color = color;
        this.bold = bold;
    }
    
    void render(char character, int x, int y) {
        // Render character with this style at position (x, y)
        System.out.printf("Render '%c' at (%d,%d) in %s %dpx %s%n",
            character, x, y, fontFamily, fontSize, bold ? "bold" : "normal");
    }
}

// Flyweight Factory: Ensures sharing
class StyleFactory {
    private static final Map<String, CharacterStyle> cache = new HashMap<>();
    
    static CharacterStyle getStyle(String font, int size, String color, boolean bold) {
        String key = font + "-" + size + "-" + color + "-" + bold;
        return cache.computeIfAbsent(key, 
            k -> new CharacterStyle(font, size, color, bold));
    }
}

// Context: Extrinsic state (varies per instance)
class DocumentCharacter {
    private final char character;           // Unique per character
    private final int x, y;                 // Unique position
    private final CharacterStyle style;     // SHARED flyweight
    
    DocumentCharacter(char c, int x, int y, CharacterStyle style) {
        this.character = c; this.x = x; this.y = y; this.style = style;
    }
    
    void render() {
        style.render(character, x, y);
    }
}

// Usage: 100,000 characters sharing ~20 unique styles
// Without Flyweight: 100,000 style objects = ~4MB
// With Flyweight: 20 style objects = ~800 bytes
```

---

## Proxy Pattern

> **Provide a surrogate or placeholder for another object to control access to it.**

### Types and Implementation

```java
// Real subject interface
interface DatabaseService {
    QueryResult query(String sql);
    void execute(String sql);
}

class RealDatabaseService implements DatabaseService {
    public QueryResult query(String sql) { /* actual DB query */ }
    public void execute(String sql) { /* actual DB execution */ }
}

// 1. PROTECTION PROXY: Access control
class AuthorizationProxy implements DatabaseService {
    private final DatabaseService realService;
    private final AuthService auth;
    
    public QueryResult query(String sql) {
        if (!auth.hasPermission(currentUser(), "READ")) {
            throw new UnauthorizedException("No read access");
        }
        return realService.query(sql);
    }
    
    public void execute(String sql) {
        if (!auth.hasPermission(currentUser(), "WRITE")) {
            throw new UnauthorizedException("No write access");
        }
        return realService.execute(sql);
    }
}

// 2. CACHING PROXY: Performance optimization
class CachingProxy implements DatabaseService {
    private final DatabaseService realService;
    private final Map<String, QueryResult> cache = new ConcurrentHashMap<>();
    
    public QueryResult query(String sql) {
        return cache.computeIfAbsent(sql, realService::query);
    }
    
    public void execute(String sql) {
        cache.clear();  // Invalidate cache on writes
        realService.execute(sql);
    }
}

// 3. LOGGING PROXY: Audit trail
class LoggingProxy implements DatabaseService {
    private final DatabaseService realService;
    
    public QueryResult query(String sql) {
        long start = System.currentTimeMillis();
        QueryResult result = realService.query(sql);
        long duration = System.currentTimeMillis() - start;
        log.info("Query: {} | Duration: {}ms | Rows: {}", sql, duration, result.rowCount());
        return result;
    }
}

// 4. VIRTUAL PROXY: Lazy initialization
class LazyDatabaseProxy implements DatabaseService {
    private DatabaseService realService;  // null until first use
    
    private DatabaseService getRealService() {
        if (realService == null) {
            realService = new RealDatabaseService();  // Expensive creation deferred
        }
        return realService;
    }
    
    public QueryResult query(String sql) {
        return getRealService().query(sql);
    }
}

// Stack proxies like decorators
DatabaseService service = new LoggingProxy(
    new CachingProxy(
        new AuthorizationProxy(
            new RealDatabaseService()
        )
    )
);
```

---

## Structural Patterns Comparison

| Pattern | Purpose | Key Idea | Example |
|---|---|---|---|
| **Adapter** | Make incompatible interfaces work together | Wraps one interface to match another | Stripe SDK → PaymentProcessor |
| **Bridge** | Separate abstraction from implementation | Two independent hierarchies connected | Notification × Channel |
| **Composite** | Part-whole hierarchies | Tree structure, uniform treatment | File system |
| **Decorator** | Add behavior dynamically | Wraps and enhances same interface | Java I/O streams |
| **Facade** | Simplify complex subsystems | One entry point, hides complexity | OrderFacade |
| **Flyweight** | Share objects to save memory | Shared intrinsic + unique extrinsic | Text formatting |
| **Proxy** | Control access to object | Same interface, added control | Auth, caching, lazy init |

---

## Structural Patterns in LLD Problems

| LLD Problem | Pattern | Why |
|---|---|---|
| **Payment System** | Adapter | Wrap Stripe/PayPal/Square into common interface |
| **File System** | Composite | Files and directories in tree structure |
| **Notification System** | Bridge | Notification type × delivery channel |
| **Logging Framework** | Decorator | Add formatting, filtering, destinations |
| **E-commerce Checkout** | Facade | Simplify inventory + payment + shipping |
| **Text Editor** | Flyweight | Character styles shared across document |
| **Image Viewer** | Proxy (Virtual) | Lazy-load high-res images |
| **Database Access** | Proxy (Caching) | Cache frequent queries |

---

## Interview Talking Points & Scripts

### When Asked "Decorator vs Proxy?"

> *"Both wrap an object, but the intent differs. Decorator ADDS new behavior — like adding encryption or compression to a data stream. Proxy CONTROLS ACCESS — like adding authentication, caching, or lazy loading. Decorator is about enhancement, Proxy is about management."*

### When Asked "Adapter vs Bridge?"

> *"Adapter is a RETROFIT pattern — you use it when interfaces already exist and don't match. Bridge is a DESIGN-TIME pattern — you use it upfront to prevent class explosion when you have two dimensions of variation."*

### When Asked "When would you use Facade?"

> *"When I have a subsystem with many classes and the client just needs a simple operation. Instead of the client knowing about 5 services and calling them in the right order, I create a Facade that exposes one method and orchestrates the subsystem calls internally. It reduces coupling and makes the API easier to use."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Confusing Decorator and Proxy
   Fix: Decorator ENHANCES behavior. Proxy CONTROLS access.

❌ Mistake 2: Using inheritance where Decorator should be used
   Fix: If you need combinations of features (A+B, A+C, B+C, A+B+C),
   use Decorator. Inheritance leads to class explosion.

❌ Mistake 3: Making Facade too smart (putting business logic in it)
   Fix: Facade should DELEGATE to subsystems, not implement logic itself.

❌ Mistake 4: Forgetting deep copy in Flyweight shared state
   Fix: Flyweight shared state must be IMMUTABLE. If it can change,
   it's not a proper flyweight.

❌ Mistake 5: Using Adapter when you should redesign
   Fix: Adapter is for INTEGRATION. If you control both sides, 
   just make the interfaces match.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              STRUCTURAL PATTERNS CHEAT SHEET                  │
├──────────────┬───────────────────────────────────────────────┤
│ Adapter      │ Convert interface A to interface B             │
│              │ Use: Third-party integration, legacy systems   │
├──────────────┼───────────────────────────────────────────────┤
│ Bridge       │ Separate abstraction from implementation       │
│              │ Use: Multiple dimensions of variation          │
├──────────────┼───────────────────────────────────────────────┤
│ Composite    │ Tree structure, treat leaf/branch uniformly    │
│              │ Use: File system, menus, org charts            │
├──────────────┼───────────────────────────────────────────────┤
│ Decorator    │ Add behavior dynamically by wrapping           │
│              │ Use: I/O streams, middleware, feature combos   │
├──────────────┼───────────────────────────────────────────────┤
│ Facade       │ Simple interface over complex subsystem        │
│              │ Use: Checkout flow, API gateway, SDK wrapper   │
├──────────────┼───────────────────────────────────────────────┤
│ Flyweight    │ Share objects to reduce memory                 │
│              │ Use: Text rendering, game tiles, icons         │
├──────────────┼───────────────────────────────────────────────┤
│ Proxy        │ Control access (auth, cache, lazy, log)        │
│              │ Use: DB access, remote services, expensive ops │
├──────────────┴───────────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "Structural patterns help me compose objects into larger       │
│  structures while keeping the system flexible — so I can       │
│  add, wrap, or simplify components without rewriting           │
│  existing code."                                              │
└──────────────────────────────────────────────────────────────┘
```
