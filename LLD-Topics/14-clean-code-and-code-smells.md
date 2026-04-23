# 🎯 LLD Topic 14: Clean Code & Code Smells

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering clean code principles, common code smells, naming conventions, method design, refactoring techniques, and how to write production-quality code that demonstrates design maturity in LLD interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Naming Conventions](#naming-conventions)
3. [Method Design Rules](#method-design-rules)
4. [Class Design Rules](#class-design-rules)
5. [Code Smells — Detection and Fixes](#code-smells)
6. [Refactoring Techniques](#refactoring-techniques)
7. [Comments — When and How](#comments)
8. [Clean Code in LLD Problems](#clean-code-in-lld)
9. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
10. [Common Interview Mistakes](#common-interview-mistakes)
11. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Clean code is code that is **easy to read, understand, and change**. In LLD interviews, clean code shows you can produce production-quality designs — not just "it works" solutions. A well-named class with clear methods is worth more than clever code that's hard to follow.

```
Clean Code Principles:
  1. Code should read like PROSE — tell a story
  2. Each function should do ONE thing
  3. Names should reveal INTENT
  4. The READER shouldn't have to think hard
  5. DRY but don't sacrifice CLARITY
```

> **Interview Gold**: *"I treat code as communication — first for humans, then for the compiler. If another developer can't understand my class in 30 seconds by reading its name and public methods, I've failed."*

---

## Naming Conventions

### Classes and Interfaces

```java
// ❌ BAD: Vague, abbreviated, or misleading names
class Mgr { }              // Manager of what?
class Data { }             // All code deals with data
class Info { }             // Not informative at all
class Processor { }        // Processes what?
class Helper { }           // Helps with what?
class Utils { }            // Utility for what?

// ✅ GOOD: Specific, descriptive names
class UserRegistrationService { }
class OrderPricingCalculator { }
class PasswordHasher { }
class PaymentGateway { }
class EmailNotificationChannel { }
class InMemoryOrderRepository { }

// Interfaces: Describe CAPABILITY, not implementation
interface Sortable { }            // CAN be sorted
interface PaymentProcessor { }    // CAN process payments
interface NotificationChannel { } // IS a notification channel
```

### Methods

```java
// ❌ BAD: Vague verbs, unclear actions
void do(Order o);
void handle(Request r);
void process(Data d);
int get();

// ✅ GOOD: Specific verbs that tell WHAT they do
void placeOrder(Order order);
void validatePaymentRequest(PaymentRequest request);
double calculateShippingCost(Address from, Address to, double weight);
List<Order> findOrdersByCustomerId(String customerId);
boolean isEligibleForDiscount(Customer customer);
void sendOrderConfirmation(Order order, String email);

// Boolean methods: is/has/can/should prefix
boolean isActive();
boolean hasPermission(Permission perm);
boolean canWithdraw(double amount);
boolean shouldRetry(Exception e);

// Factory methods: create/of/from/build
User createUser(String name, String email);
Money of(double amount, String currency);
Order fromRequest(CreateOrderRequest request);
```

### Variables

```java
// ❌ BAD: Single letters, abbreviations, unclear purpose
int d;                    // days? distance? discount?
List<String> list1;       // list of what?
Map<String, Object> m;    // what does this map?
boolean flag;             // what flag?
String s;                 // what string?

// ✅ GOOD: Purpose-revealing names
int elapsedTimeInDays;
List<String> activeUserEmails;
Map<String, UserSession> sessionsByUserId;
boolean isAccountLocked;
String formattedPhoneNumber;

// Loop variables: meaningful even in loops
// ❌
for (int i = 0; i < orders.size(); i++) {
    process(orders.get(i));
}

// ✅
for (Order order : pendingOrders) {
    processPayment(order);
}

// Or with streams
pendingOrders.stream()
    .filter(order -> order.isPayable())
    .forEach(this::processPayment);
```

### Constants

```java
// ❌ BAD: Magic numbers / inline strings
if (retryCount > 3) { }
if (status.equals("ACTIVE")) { }
Thread.sleep(5000);
if (age >= 18) { }

// ✅ GOOD: Named constants explain WHY
private static final int MAX_RETRY_ATTEMPTS = 3;
private static final String ACTIVE_STATUS = "ACTIVE";
private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
private static final int MINIMUM_VOTING_AGE = 18;

if (retryCount > MAX_RETRY_ATTEMPTS) { }
if (status.equals(ACTIVE_STATUS)) { }
Thread.sleep(RETRY_DELAY.toMillis());
if (age >= MINIMUM_VOTING_AGE) { }
```

---

## Method Design Rules

### Rule 1: Do ONE Thing

```java
// ❌ BAD: Method does too many things
void processOrder(Order order) {
    // Validate (15 lines)
    if (order.getItems() == null || order.getItems().isEmpty()) {
        throw new IllegalArgumentException("Order must have items");
    }
    for (OrderItem item : order.getItems()) {
        if (item.getQuantity() <= 0) throw new IllegalArgumentException("...");
        if (item.getPrice() <= 0) throw new IllegalArgumentException("...");
    }
    
    // Calculate total (10 lines)
    double subtotal = 0;
    for (OrderItem item : order.getItems()) {
        subtotal += item.getPrice() * item.getQuantity();
    }
    double tax = subtotal * 0.08;
    double shipping = calculateShipping(order);
    double total = subtotal + tax + shipping;
    
    // Charge payment (10 lines)
    PaymentResult result = paymentGateway.charge(order.getCustomerId(), total);
    if (!result.isSuccess()) throw new PaymentException(result.getError());
    
    // Send email (5 lines)
    emailService.send(order.getCustomerEmail(), "Order Confirmed", 
        "Your total: $" + total);
    
    // Update inventory (5 lines)
    for (OrderItem item : order.getItems()) {
        inventoryService.reduceStock(item.getProductId(), item.getQuantity());
    }
}

// ✅ GOOD: Each method does ONE thing, reads like a story
void processOrder(Order order) {
    validateOrder(order);
    Money total = calculateTotal(order);
    chargePayment(order.getCustomerId(), total);
    sendConfirmation(order, total);
    updateInventory(order.getItems());
}

private void validateOrder(Order order) {
    Objects.requireNonNull(order, "Order cannot be null");
    if (order.getItems().isEmpty()) throw new IllegalArgumentException("Order must have items");
    order.getItems().forEach(this::validateItem);
}

private Money calculateTotal(Order order) {
    Money subtotal = pricingService.calculateSubtotal(order.getItems());
    Money tax = taxService.calculateTax(subtotal, order.getShippingAddress());
    Money shipping = shippingService.calculateCost(order);
    return subtotal.add(tax).add(shipping);
}
```

### Rule 2: Keep Parameters ≤ 3

```java
// ❌ BAD: Too many parameters — hard to remember order
void createUser(String firstName, String lastName, String email, 
                String phone, String address, String city, 
                String state, String zipCode, boolean isAdmin) { }

// ✅ GOOD: Parameter object
void createUser(CreateUserRequest request) { }

record CreateUserRequest(
    String firstName,
    String lastName,
    String email,
    String phone,
    Address address,
    boolean isAdmin
) { }

// ✅ GOOD: Builder for many optional params
User user = new User.Builder("Alice", "alice@test.com")
    .phone("+1234567890")
    .address(new Address("123 Main St", "NYC", "NY", "10001"))
    .admin(false)
    .build();
```

### Rule 3: No Boolean Parameters

```java
// ❌ BAD: Boolean parameter — what does true/false mean?
void sendNotification(String message, boolean isUrgent) { }
// Caller: sendNotification(msg, true)  // What does true mean?

// ✅ GOOD: Separate methods with clear names
void sendNotification(String message) { }
void sendUrgentNotification(String message) { }

// ❌ BAD
List<User> getUsers(boolean includeInactive) { }

// ✅ GOOD
List<User> getActiveUsers() { }
List<User> getAllUsersIncludingInactive() { }
```

### Rule 4: Guard Clauses (Early Returns)

```java
// ❌ BAD: Deeply nested conditionals
void processPayment(Payment payment) {
    if (payment != null) {
        if (payment.getAmount() > 0) {
            if (payment.getCurrency() != null) {
                if (payment.getCard() != null) {
                    // Actual logic buried 4 levels deep
                    chargeCard(payment);
                }
            }
        }
    }
}

// ✅ GOOD: Guard clauses — fail fast, keep happy path left-aligned
void processPayment(Payment payment) {
    if (payment == null) throw new IllegalArgumentException("Payment required");
    if (payment.getAmount() <= 0) throw new IllegalArgumentException("Amount must be positive");
    if (payment.getCurrency() == null) throw new IllegalArgumentException("Currency required");
    if (payment.getCard() == null) throw new IllegalArgumentException("Card required");
    
    // Happy path — clear and readable at the top level
    chargeCard(payment);
}
```

### Rule 5: Keep Methods SHORT

```java
// Target: 5-20 lines per method
// If a method exceeds 20 lines, extract sub-methods
// Each method should fit on ONE screen without scrolling

// Smell test: If you need comments to separate sections within 
// a method, each section should be its own method
```

---

## Class Design Rules

### Rule 1: Single Responsibility (Name Test)

```java
// ❌ BAD: Can't name it without "and" or "or"
class UserManagerAndEmailSenderAndReportGenerator { }

// ✅ GOOD: One clear responsibility per class
class UserRegistrationService { }  // Registers users
class UserAuthenticator { }        // Authenticates users
class UserReportGenerator { }      // Generates user reports
```

### Rule 2: Small Class Size

```java
// ❌ BAD: 500+ line class with 30+ methods → God class
// ✅ GOOD: 50-200 lines, 5-10 public methods

// If a class has too many fields, it probably has too many responsibilities
// Rule of thumb: ≤ 5-7 fields per class (not counting injected dependencies)
```

### Rule 3: Cohesion — All Methods Use All Fields

```java
// ❌ BAD: Low cohesion — methods operate on different subsets of fields
class Order {
    private String id;
    private Customer customer;
    private List<LineItem> items;
    private PaymentInfo payment;
    private ShippingInfo shipping;
    private String trackingNumber;
    
    // These methods only touch items
    double calculateSubtotal() { /* uses items */ }
    int getItemCount() { /* uses items */ }
    
    // These methods only touch payment
    void processPayment() { /* uses payment */ }
    boolean isPaymentComplete() { /* uses payment */ }
    
    // These methods only touch shipping
    void ship() { /* uses shipping, trackingNumber */ }
    String getTrackingUrl() { /* uses trackingNumber */ }
}

// ✅ GOOD: High cohesion — extract classes by responsibility
class Order {
    private String id;
    private Customer customer;
    private OrderItems items;      // Extracted
    private PaymentInfo payment;   // Extracted  
    private ShippingInfo shipping; // Extracted
}

class OrderItems {
    private List<LineItem> items;
    double calculateSubtotal() { }
    int getCount() { }
}
```

---

## Code Smells — Detection and Fixes

### Smell 1: Long Method (>20 lines)

```
Detection: You need to scroll to read the method.
Fix: Extract Method — pull out coherent blocks into named methods.
```

### Smell 2: God Class (>300 lines, >15 methods)

```
Detection: Class name includes "Manager", "Handler", "Utils", "Helper".
Fix: Extract Class — split by responsibility into focused classes.
```

### Smell 3: Primitive Obsession

```java
// Detection: Using String/int for domain concepts
void createUser(String email, String phone, int age, String zipCode) { }

// Fix: Value Objects
void createUser(EmailAddress email, PhoneNumber phone, Age age, ZipCode zipCode) { }

record EmailAddress(String value) {
    public EmailAddress {
        if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))
            throw new IllegalArgumentException("Invalid email: " + value);
        value = value.toLowerCase();  // Normalize
    }
}

record PhoneNumber(String value) {
    public PhoneNumber {
        value = value.replaceAll("[^0-9+]", "");  // Normalize
        if (value.length() < 10) throw new IllegalArgumentException("Invalid phone");
    }
}
```

### Smell 4: Feature Envy

```java
// Detection: Method uses another class's data more than its own
class OrderPrinter {
    void print(Order order) {
        double total = 0;
        for (LineItem item : order.getItems()) {
            total += item.getPrice() * item.getQuantity();
            if (item.getDiscount() > 0) {
                total -= item.getDiscount();
            }
        }
        System.out.println("Total: $" + total);
    }
}

// Fix: Move logic to the class that OWNS the data
class Order {
    Money calculateTotal() {
        return items.stream()
            .map(LineItem::getNetPrice)
            .reduce(Money.ZERO, Money::add);
    }
}

class LineItem {
    Money getNetPrice() {
        return price.multiply(quantity).subtract(discount);
    }
}
```

### Smell 5: Shotgun Surgery

```java
// Detection: One change requires modifications in many classes
// Example: Adding a new user field requires changes in:
//   - User class
//   - UserService
//   - UserValidator
//   - UserMapper
//   - UserDTO
//   - UserController

// Fix: Ensure related changes are localized
// Group related data and behavior together
// Use encapsulation so internal changes don't ripple
```

### Smell 6: Data Class (No Behavior)

```java
// Detection: Class is just getters/setters, no methods
// ❌ 
class Order {
    private String id;
    private double total;
    // Only getters and setters — no behavior!
}

// The actual behavior is scattered elsewhere:
class OrderCalculator {
    double calculateTotal(Order order) { /* reaches into order */ }
}

// ✅ Fix: Put behavior WHERE THE DATA IS
class Order {
    private String id;
    private List<LineItem> items;
    
    Money calculateTotal() {  // Behavior lives WITH the data
        return items.stream()
            .map(LineItem::getNetPrice)
            .reduce(Money.ZERO, Money::add);
    }
    
    void addItem(LineItem item) {
        items.add(item);
    }
    
    boolean isEmpty() { return items.isEmpty(); }
}
```

### Smell 7: Switch on Type

```java
// Detection: switch/if-else on type or enum to choose behavior
// ❌
double calculateDiscount(Customer customer) {
    switch (customer.getType()) {
        case REGULAR: return 0.0;
        case PREMIUM: return 0.10;
        case VIP: return 0.20;
        default: return 0.0;
    }
}

// Fix: Polymorphism — each type knows its own behavior
interface DiscountPolicy {
    double getDiscount();
}

class RegularDiscount implements DiscountPolicy {
    public double getDiscount() { return 0.0; }
}
class PremiumDiscount implements DiscountPolicy {
    public double getDiscount() { return 0.10; }
}
```

---

## Refactoring Techniques

```
Extract Method:     Pull code block → named method
Extract Class:      Split God class → focused classes
Extract Interface:  Create interface from concrete class
Introduce Parameter Object: Many params → single object
Replace Conditional with Polymorphism: switch → Strategy pattern
Move Method:        Move to class with the data it uses
Rename:             Always rename to better communicate intent
Replace Magic Number with Constant: 3600 → SECONDS_PER_HOUR
```

---

## Comments — When and How

```java
// ❌ BAD: Comments that explain WHAT (code should be self-explanatory)
// Check if user is active
if (user.getStatus() == Status.ACTIVE) { }

// ❌ BAD: Comments that are outdated/wrong
// Returns the user's age
int getDaysUntilExpiry() { }  // This returns days, not age!

// ✅ GOOD: Comments that explain WHY (business context)
// We cap at 100 items per order per SEC-2024-001 compliance requirement
if (order.getItems().size() > MAX_ITEMS_PER_ORDER) { }

// ✅ GOOD: Comments for non-obvious algorithms
// Using Fisher-Yates shuffle to ensure uniform distribution
// See: https://en.wikipedia.org/wiki/Fisher-Yates_shuffle

// ✅ GOOD: TODOs with context
// TODO(alice): Replace with event-based notification after Q3 migration
emailService.sendDirect(user.getEmail(), message);

// BEST: Code that doesn't NEED comments
// Instead of:  // Check if user can withdraw
//              if (balance >= amount && !isLocked && !isFrozen)
// Write:
boolean canWithdraw(double amount) {
    return hasSufficientFunds(amount) && isAccountAccessible();
}
```

---

## Clean Code in LLD Problems

| LLD Problem | Clean Code Application |
|---|---|
| **Parking Lot** | VehicleType enum, ParkingSpot with behavior, named strategies |
| **Payment System** | Money value object, PaymentResult instead of boolean |
| **Order System** | Order.calculateTotal() not external Calculator |
| **Rate Limiter** | TokenBucket class with clear acquire/refill methods |
| **Notification** | NotificationChannel interface, separate Email/SMS/Push classes |
| **Cache** | Cache<K,V> generic, clear get/put/evict methods |

---

## Interview Talking Points & Scripts

### When Writing Code in Interview

> *"I name things by their purpose — OrderService not Manager, calculateTotal() not proc(). I keep methods under 15 lines and extract sub-methods when they get longer. I use value objects for domain concepts like Money and EmailAddress instead of raw primitives. These practices make the code self-documenting."*

### When Asked About Code Quality

> *"I follow three rules: each class has one clear responsibility that I can describe in one sentence, each method does one thing and reads like a step in a story, and names reveal intent so comments are rarely needed. When I see a switch statement on types, I replace it with polymorphism."*

### When Refactoring

> *"This class has low cohesion — the payment methods don't use the shipping fields and vice versa. I'll extract a PaymentInfo and ShippingInfo class, each with its own behavior. This makes each class smaller, more focused, and easier to test independently."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Single-letter variables (i, j, k, s, m)
   Fix: Use descriptive names. "order" not "o", "index" not "i" 
   (except in trivial loops).

❌ Mistake 2: Methods that do 5 things
   Fix: Extract until each method does ONE thing. 
   The main method should read like a table of contents.

❌ Mistake 3: No value objects — passing raw strings everywhere
   Fix: EmailAddress, Money, OrderId → domain types with validation.

❌ Mistake 4: God class with 30+ methods
   Fix: Split by responsibility. If you can't name it in one word, split it.

❌ Mistake 5: Commented-out code
   Fix: Delete it. Version control remembers.

❌ Mistake 6: Deeply nested if-else (arrow anti-pattern)
   Fix: Guard clauses — fail fast, return early.

❌ Mistake 7: Boolean parameters
   Fix: Split into two methods with clear names.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              CLEAN CODE CHEAT SHEET                           │
├──────────────────┬───────────────────────────────────────────┤
│ Naming           │ Intent-revealing. Nouns for classes,       │
│                  │ verbs for methods. is/has for booleans.    │
│                  │ UPPER_SNAKE for constants. No abbreviations│
├──────────────────┼───────────────────────────────────────────┤
│ Methods          │ ONE thing. ≤3 params. ≤20 lines.          │
│                  │ No boolean params. Guard clauses first.    │
│                  │ Return early. Read like prose.             │
├──────────────────┼───────────────────────────────────────────┤
│ Classes          │ One responsibility (name test).            │
│                  │ ≤200 lines, ≤10 public methods.           │
│                  │ High cohesion — all methods use all fields.│
├──────────────────┼───────────────────────────────────────────┤
│ Code Smells      │ Long Method → extract                     │
│                  │ God Class → split by responsibility        │
│                  │ Primitive Obsession → value objects         │
│                  │ Feature Envy → move to data owner          │
│                  │ Magic Numbers → named constants             │
│                  │ Data Class → add behavior to data          │
│                  │ Switch on Type → polymorphism               │
├──────────────────┼───────────────────────────────────────────┤
│ Comments         │ WHY not WHAT. Code should be self-describing│
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "Clean code reads like well-written prose — each class has    │
│  a clear name and purpose, methods tell a story, and the      │
│  reader never has to guess what a name means."                │
└──────────────────────────────────────────────────────────────┘
```
