# 🎯 LLD Topic 16: Null Safety & Optional Patterns

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering null handling strategies — Optional, Null Object Pattern, defensive null checks, and how to eliminate NullPointerExceptions in LLD interviews.

---

## Core Concept

`NullPointerException` is the #1 runtime error in Java. Good LLD eliminates nulls at boundaries using **Optional for return types**, **Null Object Pattern for defaults**, and **Objects.requireNonNull for constructor validation**.

---

## The Problem with Null

```java
// ❌ BAD: Null is ambiguous — does it mean "not found", "not set", or "error"?
User user = userRepository.findById("123");
String email = user.getEmail();  // NPE if user is null!
String city = user.getAddress().getCity();  // NPE if address is null!
```

---

## Optional — The Right Way

```java
// ✅ GOOD: Optional makes "absence" explicit in the type system
class UserRepository {
    Optional<User> findById(String id) {
        User user = store.get(id);
        return Optional.ofNullable(user);  // Wraps possible null
    }
}

// Caller is FORCED to handle absence
Optional<User> maybeUser = userRepository.findById("123");

// Pattern 1: orElse — provide default
User user = maybeUser.orElse(User.GUEST);

// Pattern 2: orElseThrow — fail explicitly
User user = maybeUser.orElseThrow(() -> 
    new UserNotFoundException("123"));

// Pattern 3: ifPresent — act only if present
maybeUser.ifPresent(u -> sendWelcomeEmail(u.getEmail()));

// Pattern 4: map/flatMap — transform safely
String email = maybeUser
    .map(User::getEmail)
    .orElse("unknown@example.com");

String city = maybeUser
    .map(User::getAddress)      // Optional<Address>
    .map(Address::getCity)       // Optional<String>
    .orElse("Unknown");          // Safe — no NPE possible
```

---

## Optional Rules

```
DO:
  ✅ Use Optional as METHOD RETURN TYPE for "may not exist"
  ✅ Use Optional.empty() instead of returning null
  ✅ Use map/flatMap for chaining transformations
  ✅ Use orElseThrow for required values

DON'T:
  ❌ Use Optional as FIELD type (use null with encapsulation)
  ❌ Use Optional as METHOD PARAMETER (just overload the method)
  ❌ Call .get() without .isPresent() check (defeats the purpose)
  ❌ Use Optional for collections (return empty List instead)
```

---

## Null Object Pattern

```java
// Instead of returning null, return a "do nothing" implementation
interface NotificationChannel {
    void send(String message);
}

class EmailChannel implements NotificationChannel {
    public void send(String message) { /* send email */ }
}

class NullChannel implements NotificationChannel {
    public void send(String message) { /* do nothing */ }
}

class NotificationService {
    private final NotificationChannel channel;
    
    NotificationService(NotificationChannel channel) {
        this.channel = channel != null ? channel : new NullChannel();
    }
    
    void notify(String message) {
        channel.send(message);  // No null check needed — NullChannel is safe
    }
}
```

---

## Defensive Null Checks at Boundaries

```java
class OrderService {
    private final OrderRepository repository;
    private final PaymentGateway payment;
    
    OrderService(OrderRepository repository, PaymentGateway payment) {
        // Fail fast: null dependencies are programming errors
        this.repository = Objects.requireNonNull(repository, "OrderRepository required");
        this.payment = Objects.requireNonNull(payment, "PaymentGateway required");
    }
    
    void placeOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must have items");
        }
        // Safe to proceed — nulls eliminated at boundary
    }
}
```

---

## Collections: Empty, Not Null

```java
// ❌ BAD: Returning null collections
List<Order> findByCustomer(String id) {
    if (noOrders) return null;  // Caller must null-check!
}

// ✅ GOOD: Return empty collections
List<Order> findByCustomer(String id) {
    if (noOrders) return List.of();  // Empty list — safe to iterate
}

// Caller code is clean
List<Order> orders = findByCustomer("123");
for (Order o : orders) { }  // Works even if empty — no null check
orders.isEmpty();  // True — no NPE
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              NULL SAFETY CHEAT SHEET                           │
├──────────────────┬───────────────────────────────────────────┤
│ Return type      │ Use Optional<T> for "may not exist"        │
│ Constructor      │ Objects.requireNonNull() for dependencies  │
│ Collections      │ Return empty List/Set/Map, NEVER null      │
│ Default behavior │ Null Object Pattern (do-nothing impl)      │
│ Chaining         │ Optional.map().map().orElse()              │
├──────────────────┼───────────────────────────────────────────┤
│ Field types      │ DON'T use Optional — use encapsulation     │
│ Parameters       │ DON'T use Optional — overload methods      │
│ .get() raw       │ DON'T — use orElse/orElseThrow instead     │
├──────────────────┴───────────────────────────────────────────┤
│ "I eliminate nulls at boundaries — Optional for return types,  │
│  requireNonNull in constructors, empty collections instead     │
│  of null, and Null Object Pattern for default behaviors."      │
└──────────────────────────────────────────────────────────────┘
```
