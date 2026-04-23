# 🎯 LLD Topic 11: Immutability & Defensive Programming

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering immutable objects, Java Records, defensive copying, unmodifiable collections, value objects, and how immutability makes code thread-safe and bug-free in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 11: Immutability \& Defensive Programming](#-lld-topic-11-immutability--defensive-programming)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Why Immutability Matters](#why-immutability-matters)
  - [Creating Immutable Classes](#creating-immutable-classes)
  - [Java Records](#java-records)
  - [Defensive Copying](#defensive-copying)
  - [Unmodifiable Collections](#unmodifiable-collections)
  - [Value Objects](#value-objects)
  - [Builder Pattern for Immutable Objects](#builder-pattern-for-immutable-objects)
  - [When NOT to Use Immutability](#when-not-to-use-immutability)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Designing Value Objects](#when-designing-value-objects)
    - [When Asked About Thread Safety](#when-asked-about-thread-safety)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

An **immutable object** cannot be modified after creation. Once constructed, its state never changes. This eliminates entire categories of bugs: race conditions, unexpected mutations, and stale references.

```
Immutability benefits:
  1. THREAD SAFE: No synchronization needed — can't change, can't corrupt
  2. CACHE SAFE: Can be shared freely as cache keys, map keys
  3. PREDICTABLE: No surprise mutations from distant code
  4. HASHABLE: hashCode() never changes — safe for HashMap/HashSet
  5. SIMPLE: No defensive copying needed when sharing
```

---

## Why Immutability Matters

```java
// ❌ MUTABLE: Bug waiting to happen
class Money {
    private double amount;    // Mutable!
    private String currency;  // Mutable!
    
    void setAmount(double amount) { this.amount = amount; }
}

Money price = new Money(100, "USD");
cart.setPrice(price);
price.setAmount(0);  // Oops — cart's price is now $0!

// ✅ IMMUTABLE: Safe to share
class Money {
    private final double amount;
    private final String currency;
    
    Money(double amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }
    
    // No setters! Create new object for new values
    Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException();
        return new Money(this.amount + other.amount, this.currency);
    }
    
    double getAmount() { return amount; }
    String getCurrency() { return currency; }
}
```

---

## Creating Immutable Classes

```java
// Rules for immutable classes:
// 1. Class is final (or all methods are final)
// 2. All fields are private and final
// 3. No setters
// 4. Deep copy mutable fields in constructor and getters
// 5. Don't allow subclasses to override methods

public final class Order {
    private final String id;
    private final String customerId;
    private final List<LineItem> items;        // Mutable type!
    private final LocalDateTime createdAt;
    private final Money total;
    
    public Order(String id, String customerId, List<LineItem> items, Money total) {
        this.id = id;
        this.customerId = customerId;
        this.items = List.copyOf(items);        // Defensive copy!
        this.createdAt = LocalDateTime.now();
        this.total = total;                      // Money is already immutable
    }
    
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public List<LineItem> getItems() { return items; }  // Already unmodifiable
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Money getTotal() { return total; }
    
    // "Modification" creates new object
    public Order withItems(List<LineItem> newItems) {
        return new Order(id, customerId, newItems, calculateTotal(newItems));
    }
}
```

---

## Java Records

```java
// Java 16+: Records are immutable by default
public record Money(double amount, String currency) {
    // Compact constructor for validation
    public Money {
        if (amount < 0) throw new IllegalArgumentException("Amount must be non-negative");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("Currency required");
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
        return new Money(this.amount + other.amount, this.currency);
    }
}

public record Address(String street, String city, String state, String zipCode) {}

public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("OrderId required");
    }
}

// Records auto-generate:
// - Constructor, getters (amount(), currency() — no "get" prefix)
// - equals(), hashCode(), toString()
// - Fields are private final
```

---

## Defensive Copying

```java
// ❌ BAD: Exposing mutable internals
class Team {
    private final List<Player> players;
    
    Team(List<Player> players) {
        this.players = players;  // Caller's list is stored directly!
    }
    
    List<Player> getPlayers() {
        return players;  // Caller can modify internal list!
    }
}

List<Player> myList = new ArrayList<>();
myList.add(new Player("Alice"));
Team team = new Team(myList);
myList.add(new Player("Hacker"));  // Oops — team's list is modified!
team.getPlayers().clear();          // Oops — team's list is emptied!

// ✅ GOOD: Defensive copies on input AND output
class Team {
    private final List<Player> players;
    
    Team(List<Player> players) {
        this.players = List.copyOf(players);  // Copy on input
    }
    
    List<Player> getPlayers() {
        return players;  // List.copyOf already returns unmodifiable
    }
}
```

---

## Unmodifiable Collections

```java
// Creating unmodifiable collections
List<String> list = List.of("a", "b", "c");           // Immutable
Set<String> set = Set.of("a", "b", "c");               // Immutable
Map<String, Integer> map = Map.of("a", 1, "b", 2);    // Immutable

// Wrapping existing mutable collections
List<String> mutable = new ArrayList<>(List.of("a", "b"));
List<String> unmodifiable = Collections.unmodifiableList(mutable);
// BUT: modifying 'mutable' still changes 'unmodifiable'!

// Safe approach: copy then wrap
List<String> safe = Collections.unmodifiableList(new ArrayList<>(mutable));
// Or simpler:
List<String> safest = List.copyOf(mutable);
```

---

## Value Objects

```java
// Value objects: identity is based on VALUES, not reference
// Two Money objects with same amount and currency are EQUAL

public record Money(BigDecimal amount, Currency currency) {
    // Records auto-generate equals/hashCode based on all fields
}

public record EmailAddress(String value) {
    public EmailAddress {
        if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
        value = value.toLowerCase();  // Normalize
    }
}

public record DateRange(LocalDate start, LocalDate end) {
    public DateRange {
        if (end.isBefore(start)) throw new IllegalArgumentException("End before start");
    }
    
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
    
    public boolean overlaps(DateRange other) {
        return !this.end.isBefore(other.start) && !other.end.isBefore(this.start);
    }
}
```

---

## Builder Pattern for Immutable Objects

```java
public final class HttpResponse {
    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;
    
    private HttpResponse(Builder builder) {
        this.statusCode = builder.statusCode;
        this.headers = Map.copyOf(builder.headers);  // Immutable copy
        this.body = builder.body;
    }
    
    public static class Builder {
        private int statusCode = 200;
        private final Map<String, String> headers = new HashMap<>();
        private String body = "";
        
        public Builder statusCode(int code) { this.statusCode = code; return this; }
        public Builder header(String key, String val) { headers.put(key, val); return this; }
        public Builder body(String body) { this.body = body; return this; }
        
        public HttpResponse build() { return new HttpResponse(this); }
    }
    
    // Getters only — no setters
    public int getStatusCode() { return statusCode; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
}
```

---

## When NOT to Use Immutability

```
Immutability is NOT always appropriate:
  ❌ Entities with identity that changes state (Order status, User profile)
     → Use mutable entities with controlled mutation
  ❌ Performance-critical paths with many updates
     → Creating new objects each time = GC pressure
  ❌ Large objects with single-field updates
     → Copying entire object for one field change is wasteful
  
Use immutability for:
  ✅ Value objects (Money, Address, DateRange, Email)
  ✅ Configuration objects
  ✅ DTOs / API responses
  ✅ Cache keys / Map keys
  ✅ Shared state between threads
```

---

## Interview Talking Points & Scripts

### When Designing Value Objects

> *"I'll make Money immutable — it's a value object where identity comes from its values, not a database ID. Fields are final, no setters, and the add() method returns a new Money instance. This means it's automatically thread-safe and safe to use as a HashMap key."*

### When Asked About Thread Safety

> *"My first choice for thread safety is immutability — if the object can't change, no synchronization is needed. I'll use Records or final classes for value objects, and List.copyOf() to ensure collections can't be modified after construction."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Making entity classes immutable
   Fix: Entities (Order, User) need to change state. 
   Make VALUE OBJECTS immutable, not entities.

❌ Mistake 2: Returning mutable internal collections
   Fix: Return List.copyOf() or Collections.unmodifiableList().

❌ Mistake 3: Storing mutable parameters directly
   Fix: Always copy mutable inputs in the constructor.

❌ Mistake 4: Using mutable objects as Map keys
   Fix: Map keys MUST have stable hashCode() — use immutable objects.

❌ Mistake 5: Forgetting 'final' on the class itself
   Fix: Make immutable classes final to prevent subclass mutation.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│       IMMUTABILITY & DEFENSIVE PROGRAMMING                    │
├──────────────────┬───────────────────────────────────────────┤
│ Immutable Rules  │ final class, private final fields          │
│                  │ No setters, defensive copies               │
│                  │ Return new objects for "modifications"     │
├──────────────────┼───────────────────────────────────────────┤
│ Java Records     │ Immutable by default, auto equals/hash     │
│                  │ Compact constructors for validation         │
├──────────────────┼───────────────────────────────────────────┤
│ Defensive Copy   │ Copy on input (constructor)                │
│                  │ Copy on output (getters for collections)   │
├──────────────────┼───────────────────────────────────────────┤
│ Value Objects    │ Identity = values, not reference            │
│                  │ Always immutable, great as Map keys         │
├──────────────────┼───────────────────────────────────────────┤
│ Use For          │ Money, Address, Email, Config, DTOs, keys  │
│ Don't Use For    │ Entities, large mutable state, perf-hot    │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "I make value objects immutable — they're thread-safe,         │
│  predictable, and safe to share. For entities, I control      │
│  mutation through well-defined business methods."             │
└──────────────────────────────────────────────────────────────┘
```
