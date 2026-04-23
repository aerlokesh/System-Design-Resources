# 🎯 LLD Topic 15: Enum Design & State Machines

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering Java Enums with behavior, enum-based strategies, finite state machines, state transition tables, guard conditions, and how to model stateful systems cleanly in LLD interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Enums Are More Than Constants](#why-enums-are-more)
3. [Enums with Fields and Methods](#enums-with-fields-and-methods)
4. [Enum as Strategy Pattern](#enum-as-strategy)
5. [State Machines — Why They Matter](#state-machines-why)
6. [State Machine with Enum](#state-machine-with-enum)
7. [State Transition Table Pattern](#state-transition-table)
8. [Guards and Actions on Transitions](#guards-and-actions)
9. [EnumSet and EnumMap](#enumset-and-enummap)
10. [State Machines in LLD Problems](#state-machines-in-lld)
11. [Full Example: Order Lifecycle](#full-example-order)
12. [Full Example: Vending Machine](#full-example-vending)
13. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
14. [Common Interview Mistakes](#common-interview-mistakes)
15. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Enums in Java are **full-fledged classes** — they can have fields, methods, constructors, and implement interfaces. They're not just constants. Combined with state machines, they create **clean, compile-time-safe models** for order lifecycles, vending machines, game states, and any system with well-defined states and transitions.

```
Why Enums + State Machines matter in LLD:
  1. Replace SCATTERED if-else/switch with SELF-CONTAINED state logic
  2. Make INVALID TRANSITIONS impossible (compile-time or runtime check)
  3. Self-documenting — state diagram maps directly to code
  4. Easy to TEST — each state is an isolated behavior unit
```

> **Interview Gold**: *"I model lifecycles as enum state machines — each state knows its valid transitions and allowed operations. This eliminates scattered if-else checks and makes invalid state transitions impossible."*

---

## Why Enums Are More Than Constants

```java
// ❌ BAD: String/int constants — no type safety, typos cause bugs
public static final String STATUS_PENDING = "PENDING";
public static final String STATUS_ACTIVE = "ACTIVE";
public static final int ROLE_ADMIN = 1;
public static final int ROLE_USER = 2;

void setStatus(String status) { }  // Accepts ANY string — "PDNING" compiles!
void setRole(int role) { }          // Accepts ANY int — role = 999 compiles!

// ✅ GOOD: Enums — compile-time safety, IDE autocomplete, no typos
enum Status { PENDING, ACTIVE, SUSPENDED, DELETED }
enum Role { ADMIN, USER, MODERATOR }

void setStatus(Status status) { }   // Only valid statuses accepted
void setRole(Role role) { }         // Only valid roles accepted
```

---

## Enums with Fields and Methods

```java
// Enum with data and behavior — each constant is a SINGLETON instance
enum PaymentMethod {
    CREDIT_CARD(2.9, "Stripe", true),
    DEBIT_CARD(1.5, "Stripe", true),
    PAYPAL(3.5, "PayPal", false),
    BANK_TRANSFER(0.5, "Plaid", false),
    CRYPTO(1.0, "Coinbase", false),
    CASH(0.0, "In-Store", true);
    
    private final double feePercent;
    private final String processor;
    private final boolean supportsRefund;
    
    PaymentMethod(double fee, String proc, boolean refund) {
        this.feePercent = fee;
        this.processor = proc;
        this.supportsRefund = refund;
    }
    
    double calculateFee(double amount) {
        return amount * feePercent / 100.0;
    }
    
    double getNetAmount(double amount) {
        return amount - calculateFee(amount);
    }
    
    boolean supportsRefund() { return supportsRefund; }
    String getProcessor() { return processor; }
}

// Usage — clean, type-safe, self-documenting
PaymentMethod method = PaymentMethod.CREDIT_CARD;
double fee = method.calculateFee(100.0);       // $2.90
double net = method.getNetAmount(100.0);       // $97.10
boolean canRefund = method.supportsRefund();   // true
String processor = method.getProcessor();       // "Stripe"
```

### Enum with Abstract Methods — Per-Constant Behavior

```java
enum HttpStatus {
    OK(200) {
        public boolean isSuccess() { return true; }
        public String getCategory() { return "Success"; }
    },
    CREATED(201) {
        public boolean isSuccess() { return true; }
        public String getCategory() { return "Success"; }
    },
    BAD_REQUEST(400) {
        public boolean isSuccess() { return false; }
        public String getCategory() { return "Client Error"; }
    },
    UNAUTHORIZED(401) {
        public boolean isSuccess() { return false; }
        public String getCategory() { return "Client Error"; }
    },
    INTERNAL_ERROR(500) {
        public boolean isSuccess() { return false; }
        public String getCategory() { return "Server Error"; }
    };
    
    private final int code;
    HttpStatus(int code) { this.code = code; }
    
    public int getCode() { return code; }
    public abstract boolean isSuccess();
    public abstract String getCategory();
}
```

---

## Enum as Strategy Pattern

```java
// Each enum constant implements a different algorithm
enum DiscountStrategy {
    NONE {
        public double apply(double price, int quantity) { return price * quantity; }
        public String getDescription() { return "No discount"; }
    },
    PERCENTAGE_10 {
        public double apply(double price, int quantity) { return price * quantity * 0.90; }
        public String getDescription() { return "10% off"; }
    },
    PERCENTAGE_20 {
        public double apply(double price, int quantity) { return price * quantity * 0.80; }
        public String getDescription() { return "20% off"; }
    },
    BUY_TWO_GET_ONE_FREE {
        public double apply(double price, int quantity) {
            int freeItems = quantity / 3;
            return price * (quantity - freeItems);
        }
        public String getDescription() { return "Buy 2 Get 1 Free"; }
    },
    BULK_PRICING {
        public double apply(double price, int quantity) {
            if (quantity >= 100) return price * quantity * 0.60;
            if (quantity >= 50) return price * quantity * 0.70;
            if (quantity >= 10) return price * quantity * 0.85;
            return price * quantity;
        }
        public String getDescription() { return "Bulk pricing tiers"; }
    };
    
    public abstract double apply(double price, int quantity);
    public abstract String getDescription();
}

// Usage
DiscountStrategy strategy = DiscountStrategy.BUY_TWO_GET_ONE_FREE;
double total = strategy.apply(30.0, 6);  // 6 items, 2 free = $120 (not $180)
```

### Enum Strategy with Interface

```java
// Enum implementing a Strategy interface — can be used polymorphically
interface PricingStrategy {
    double calculate(double basePrice, int quantity);
}

enum StandardPricing implements PricingStrategy {
    REGULAR {
        public double calculate(double base, int qty) { return base * qty; }
    },
    PREMIUM {
        public double calculate(double base, int qty) { return base * qty * 0.9; }
    };
}

// Can inject enum constants wherever PricingStrategy is expected
class ShoppingCart {
    private PricingStrategy strategy = StandardPricing.REGULAR;
    
    void setStrategy(PricingStrategy strategy) { this.strategy = strategy; }
}
```

---

## State Machines — Why They Matter

```
Many LLD problems are STATE MACHINES:
  Order:    PENDING → CONFIRMED → SHIPPED → DELIVERED / CANCELLED
  Elevator: IDLE → MOVING_UP → STOPPED → MOVING_DOWN → IDLE
  Vending:  IDLE → COIN_INSERTED → ITEM_SELECTED → DISPENSING → IDLE
  ATM:      IDLE → CARD_INSERTED → PIN_ENTERED → TRANSACTION → IDLE
  Booking:  TENTATIVE → CONFIRMED → CHECKED_IN → COMPLETED / CANCELLED
  Account:  ACTIVE → SUSPENDED → CLOSED

Without state machine pattern:
  ❌ Scattered if-else checks: if (status == PENDING) { if (action == SHIP) ... }
  ❌ Invalid transitions possible: order.setStatus(DELIVERED) from PENDING
  ❌ Business rules lost in spaghetti code

With state machine pattern:
  ✅ Each state knows its valid transitions
  ✅ Invalid transitions throw clear exceptions
  ✅ Business logic lives WITH the state
```

---

## State Machine with Enum

```java
enum OrderStatus {
    PENDING {
        public OrderStatus next() { return CONFIRMED; }
        public boolean canCancel() { return true; }
        public boolean canModify() { return true; }
        public Set<OrderStatus> validTransitions() { 
            return EnumSet.of(CONFIRMED, CANCELLED); 
        }
    },
    CONFIRMED {
        public OrderStatus next() { return SHIPPED; }
        public boolean canCancel() { return true; }
        public boolean canModify() { return false; }
        public Set<OrderStatus> validTransitions() { 
            return EnumSet.of(SHIPPED, CANCELLED); 
        }
    },
    SHIPPED {
        public OrderStatus next() { return DELIVERED; }
        public boolean canCancel() { return false; }
        public boolean canModify() { return false; }
        public Set<OrderStatus> validTransitions() { 
            return EnumSet.of(DELIVERED); 
        }
    },
    DELIVERED {
        public OrderStatus next() { throw new IllegalStateException("Already delivered"); }
        public boolean canCancel() { return false; }
        public boolean canModify() { return false; }
        public Set<OrderStatus> validTransitions() { return EnumSet.noneOf(OrderStatus.class); }
    },
    CANCELLED {
        public OrderStatus next() { throw new IllegalStateException("Order cancelled"); }
        public boolean canCancel() { return false; }
        public boolean canModify() { return false; }
        public Set<OrderStatus> validTransitions() { return EnumSet.noneOf(OrderStatus.class); }
    };
    
    public abstract OrderStatus next();
    public abstract boolean canCancel();
    public abstract boolean canModify();
    public abstract Set<OrderStatus> validTransitions();
    
    public OrderStatus cancel() {
        if (!canCancel()) throw new IllegalStateException("Cannot cancel from " + this);
        return CANCELLED;
    }
    
    public boolean canTransitionTo(OrderStatus target) {
        return validTransitions().contains(target);
    }
    
    public OrderStatus transitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                "Invalid transition: " + this + " → " + target + 
                ". Valid: " + validTransitions());
        }
        return target;
    }
}

// Usage in Order entity
class Order {
    private OrderStatus status = OrderStatus.PENDING;
    
    void confirm() {
        this.status = status.transitionTo(OrderStatus.CONFIRMED);
    }
    
    void ship(String trackingNumber) {
        this.status = status.transitionTo(OrderStatus.SHIPPED);
        this.trackingNumber = trackingNumber;
    }
    
    void deliver() {
        this.status = status.transitionTo(OrderStatus.DELIVERED);
    }
    
    void cancel(String reason) {
        this.status = status.cancel();
        this.cancellationReason = reason;
    }
    
    void modify(List<LineItem> newItems) {
        if (!status.canModify()) {
            throw new IllegalStateException("Cannot modify order in " + status + " state");
        }
        this.items = newItems;
    }
}
```

---

## State Transition Table Pattern

```java
// For complex state machines with multiple events
enum VendingState { IDLE, COIN_INSERTED, ITEM_SELECTED, DISPENSING, OUT_OF_STOCK }
enum VendingEvent { INSERT_COIN, SELECT_ITEM, DISPENSE_COMPLETE, CANCEL, STOCK_EMPTY, RESTOCK }

class VendingMachine {
    private VendingState state = VendingState.IDLE;
    private double balance = 0;
    
    // Transition table: (currentState, event) → nextState
    private static final Map<VendingState, Map<VendingEvent, VendingState>> TRANSITIONS;
    
    static {
        TRANSITIONS = new EnumMap<>(VendingState.class);
        
        TRANSITIONS.put(VendingState.IDLE, Map.of(
            VendingEvent.INSERT_COIN, VendingState.COIN_INSERTED,
            VendingEvent.STOCK_EMPTY, VendingState.OUT_OF_STOCK
        ));
        
        TRANSITIONS.put(VendingState.COIN_INSERTED, Map.of(
            VendingEvent.INSERT_COIN, VendingState.COIN_INSERTED,  // Add more coins
            VendingEvent.SELECT_ITEM, VendingState.ITEM_SELECTED,
            VendingEvent.CANCEL, VendingState.IDLE
        ));
        
        TRANSITIONS.put(VendingState.ITEM_SELECTED, Map.of(
            VendingEvent.DISPENSE_COMPLETE, VendingState.IDLE,
            VendingEvent.CANCEL, VendingState.IDLE,
            VendingEvent.STOCK_EMPTY, VendingState.OUT_OF_STOCK
        ));
        
        TRANSITIONS.put(VendingState.OUT_OF_STOCK, Map.of(
            VendingEvent.RESTOCK, VendingState.IDLE
        ));
    }
    
    void handleEvent(VendingEvent event) {
        Map<VendingEvent, VendingState> validEvents = TRANSITIONS.get(state);
        if (validEvents == null || !validEvents.containsKey(event)) {
            throw new IllegalStateException(
                "Event " + event + " not valid in state " + state);
        }
        
        VendingState previousState = state;
        state = validEvents.get(event);
        System.out.println(previousState + " --[" + event + "]--> " + state);
        
        // Execute side effects based on transition
        executeTransitionAction(previousState, event, state);
    }
    
    private void executeTransitionAction(VendingState from, VendingEvent event, VendingState to) {
        switch (event) {
            case INSERT_COIN -> balance += 1.00;
            case CANCEL -> { returnChange(balance); balance = 0; }
            case DISPENSE_COMPLETE -> { dispenseItem(); balance = 0; }
            default -> { }
        }
    }
    
    VendingState getState() { return state; }
}
```

---

## Guards and Actions on Transitions

```java
// Transitions with conditions (guards) and side effects (actions)
record Transition(VendingState from, VendingEvent event, VendingState to,
                  Predicate<VendingMachine> guard, Consumer<VendingMachine> action) {
    
    boolean isApplicable(VendingMachine machine) {
        return machine.getState() == from && guard.test(machine);
    }
    
    void execute(VendingMachine machine) {
        action.accept(machine);
    }
}

// Define transitions with guards
List<Transition> transitions = List.of(
    new Transition(COIN_INSERTED, SELECT_ITEM, ITEM_SELECTED,
        machine -> machine.getBalance() >= machine.getSelectedItemPrice(),  // Guard
        machine -> machine.dispenseItem()),                                 // Action
    
    new Transition(COIN_INSERTED, SELECT_ITEM, COIN_INSERTED,
        machine -> machine.getBalance() < machine.getSelectedItemPrice(),   // Guard: not enough
        machine -> machine.showInsufficientFundsMessage())                  // Action
);
```

---

## EnumSet and EnumMap

```java
// EnumSet: Ultra-efficient set for enum values (backed by bit vector)
// One long (64 bits) can represent up to 64 enum constants!

// Permissions system
enum Permission { READ, WRITE, DELETE, ADMIN, EXPORT, IMPORT }

Set<Permission> adminPerms = EnumSet.allOf(Permission.class);
Set<Permission> userPerms = EnumSet.of(Permission.READ);
Set<Permission> editorPerms = EnumSet.of(Permission.READ, Permission.WRITE);

// O(1) operations — just bit manipulation!
adminPerms.contains(Permission.ADMIN);    // O(1)
adminPerms.containsAll(editorPerms);      // O(1)
EnumSet.complementOf(userPerms);          // All except READ

// Day-of-week scheduling
Set<DayOfWeek> weekdays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
Set<DayOfWeek> weekends = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

// EnumMap: Array-backed map with enum keys — fastest map possible
Map<OrderStatus, List<Order>> ordersByStatus = new EnumMap<>(OrderStatus.class);
for (OrderStatus status : OrderStatus.values()) {
    ordersByStatus.put(status, new ArrayList<>());
}

// O(1) access — just array index, no hashing
ordersByStatus.get(OrderStatus.PENDING).add(newOrder);

// Configuration by status
Map<OrderStatus, Duration> slaByStatus = new EnumMap<>(Map.of(
    OrderStatus.PENDING, Duration.ofHours(24),
    OrderStatus.CONFIRMED, Duration.ofHours(48),
    OrderStatus.SHIPPED, Duration.ofDays(7)
));
```

---

## State Machines in LLD Problems

| LLD Problem | States | Events/Transitions |
|---|---|---|
| **Order System** | PENDING → CONFIRMED → SHIPPED → DELIVERED / CANCELLED | confirm, ship, deliver, cancel |
| **Vending Machine** | IDLE → COIN_INSERTED → ITEM_SELECTED → DISPENSING | insertCoin, selectItem, dispense, cancel |
| **Elevator** | IDLE → MOVING_UP → STOPPED → MOVING_DOWN | requestFloor, arrive, idle |
| **ATM** | IDLE → CARD_INSERTED → PIN_VERIFIED → TRANSACTION → COMPLETE | insertCard, enterPin, selectTransaction |
| **Booking System** | TENTATIVE → CONFIRMED → CHECKED_IN → COMPLETED → ARCHIVED | confirm, checkIn, complete, expire |
| **User Account** | ACTIVE → SUSPENDED → CLOSED | suspend, reactivate, close |
| **Traffic Light** | GREEN → YELLOW → RED → GREEN | timer |
| **Ticket** | OPEN → IN_PROGRESS → REVIEW → DONE | assign, review, approve, reject |

---

## Full Example: Order Lifecycle

```java
class Order {
    private final String id;
    private OrderStatus status;
    private final List<LineItem> items;
    private String trackingNumber;
    private String cancellationReason;
    private final List<StatusChange> history = new ArrayList<>();
    
    Order(String id, List<LineItem> items) {
        this.id = id;
        this.items = List.copyOf(items);
        this.status = OrderStatus.PENDING;
        recordChange(null, OrderStatus.PENDING, "Order created");
    }
    
    void confirm() {
        transition(OrderStatus.CONFIRMED, "Order confirmed by payment");
    }
    
    void ship(String tracking) {
        transition(OrderStatus.SHIPPED, "Shipped with tracking: " + tracking);
        this.trackingNumber = tracking;
    }
    
    void deliver() {
        transition(OrderStatus.DELIVERED, "Delivered to customer");
    }
    
    void cancel(String reason) {
        if (!status.canCancel()) {
            throw new IllegalStateException("Cannot cancel order in " + status + " state");
        }
        transition(OrderStatus.CANCELLED, "Cancelled: " + reason);
        this.cancellationReason = reason;
    }
    
    private void transition(OrderStatus newStatus, String reason) {
        OrderStatus old = this.status;
        this.status = old.transitionTo(newStatus);
        recordChange(old, newStatus, reason);
    }
    
    private void recordChange(OrderStatus from, OrderStatus to, String reason) {
        history.add(new StatusChange(from, to, Instant.now(), reason));
    }
    
    // Query methods that delegate to state
    boolean canCancel() { return status.canCancel(); }
    boolean canModify() { return status.canModify(); }
    OrderStatus getStatus() { return status; }
    List<StatusChange> getHistory() { return List.copyOf(history); }
    
    record StatusChange(OrderStatus from, OrderStatus to, Instant timestamp, String reason) {}
}
```

---

## Full Example: Vending Machine

```java
class VendingMachineDemo {
    public static void main(String[] args) {
        VendingMachine machine = new VendingMachine();
        
        // Normal flow
        machine.handleEvent(VendingEvent.INSERT_COIN);   // IDLE → COIN_INSERTED
        machine.handleEvent(VendingEvent.INSERT_COIN);   // COIN_INSERTED → COIN_INSERTED (add more)
        machine.handleEvent(VendingEvent.SELECT_ITEM);   // COIN_INSERTED → ITEM_SELECTED
        machine.handleEvent(VendingEvent.DISPENSE_COMPLETE); // ITEM_SELECTED → IDLE
        
        // Cancel flow
        machine.handleEvent(VendingEvent.INSERT_COIN);   // IDLE → COIN_INSERTED
        machine.handleEvent(VendingEvent.CANCEL);         // COIN_INSERTED → IDLE (refund)
        
        // Invalid transition — throws exception
        try {
            machine.handleEvent(VendingEvent.SELECT_ITEM); // IDLE + SELECT = INVALID!
        } catch (IllegalStateException e) {
            System.out.println("Expected: " + e.getMessage());
        }
    }
}
```

---

## Interview Talking Points & Scripts

### When Asked About State Management

> *"For the order lifecycle, I'll model states as an enum with behavior — each OrderStatus knows its valid transitions, what actions are allowed in that state, and how to transition safely. PENDING can cancel and modify, CONFIRMED can cancel but not modify, SHIPPED can't cancel. This replaces scattered if-else checks with self-documenting, testable state logic."*

### When Choosing Between Enum and State Pattern

> *"For simple state machines with 3-5 states and clear transitions, I use enum with abstract methods — it's compact and the compiler enforces that every state implements every method. For complex state machines with guards, actions, and dynamic transitions, I use the State design pattern with separate classes per state."*

### When Designing Permissions

> *"I model permissions as an EnumSet — it's backed by a bit vector, so operations like contains(), containsAll(), and intersection are O(1) bitwise operations. An admin has EnumSet.allOf(Permission.class), a viewer has EnumSet.of(READ), and checking permission is just a single bit check."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Using String constants instead of enums
   Fix: Always use enums for fixed sets of values. Type-safe, no typos.

❌ Mistake 2: Using enums as dumb constants (no behavior)
   Fix: Add fields and methods. calculateFee(), canCancel(), next().

❌ Mistake 3: Scattered if-else for state transitions
   Fix: Put transition logic IN the enum. state.next(), state.canCancel().

❌ Mistake 4: Allowing direct status assignment
   Fix: Use transitionTo() that validates. Never expose setStatus().

❌ Mistake 5: No history tracking on state changes
   Fix: Record StatusChange(from, to, timestamp, reason) on each transition.

❌ Mistake 6: Using HashMap<String, ...> when enum keys would work
   Fix: EnumMap is faster (array-backed) and type-safe.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          ENUM DESIGN & STATE MACHINES CHEAT SHEET             │
├──────────────────┬───────────────────────────────────────────┤
│ Enum + Fields    │ PaymentMethod with fee, processor, refund  │
│ Enum + Abstract  │ Each constant overrides abstract method    │
│ Enum + Strategy  │ DiscountStrategy with different algorithms │
│ Enum + Interface │ Enum implements Strategy interface          │
├──────────────────┼───────────────────────────────────────────┤
│ State Machine    │ Each state knows valid transitions          │
│                  │ transitionTo() validates, throws if invalid│
│                  │ canCancel(), canModify() per state          │
├──────────────────┼───────────────────────────────────────────┤
│ Transition Table │ Map<State, Map<Event, State>> for complex   │
│                  │ Add guards (conditions) and actions          │
├──────────────────┼───────────────────────────────────────────┤
│ EnumSet          │ Ultra-fast set operations (bit vector)     │
│                  │ Perfect for permissions, flags              │
│ EnumMap          │ Fastest map with enum keys (array-backed)  │
│                  │ Perfect for config, SLA, pricing by type   │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "I use enums with behavior for state machines — each state    │
│  knows its valid transitions, eliminating scattered if-else   │
│  and making invalid transitions impossible at the type level."│
└──────────────────────────────────────────────────────────────┘
```
