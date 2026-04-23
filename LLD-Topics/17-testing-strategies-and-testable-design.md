# 🎯 LLD Topic 17: Testing Strategies & Testable Design

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering unit testing, test doubles (mocks, stubs, fakes), testable design principles, TDD approach, and how to demonstrate testing awareness in LLD interviews.

---

## Core Concept

Testable design and good OO design are **the same thing**. If your code is hard to test, it has a design problem. DI, interfaces, SRP, and low coupling naturally make code testable.

---

## Unit Testing Fundamentals

```java
// Arrange-Act-Assert pattern
class OrderServiceTest {
    
    @Test
    void shouldCalculateTotalWithDiscount() {
        // ARRANGE: Set up test data and dependencies
        Order order = new Order(List.of(
            new LineItem("Laptop", 1000, 1),
            new LineItem("Mouse", 50, 2)
        ));
        PricingStrategy strategy = new PercentageDiscount(10);
        OrderService service = new OrderService(strategy);
        
        // ACT: Execute the method under test
        double total = service.calculateTotal(order);
        
        // ASSERT: Verify the result
        assertEquals(990.0, total, 0.01);  // 1100 - 10% = 990
    }
}
```

---

## Test Doubles

```java
// 1. MOCK: Verify interactions (did method X get called?)
@Test
void shouldSendNotificationOnOrderPlacement() {
    NotificationService mockNotif = mock(NotificationService.class);
    OrderRepository mockRepo = mock(OrderRepository.class);
    OrderService service = new OrderService(mockRepo, mockNotif);
    
    service.placeOrder(new Order("laptop", "user@email.com"));
    
    verify(mockNotif).notify("user@email.com", "Order placed!");
    verify(mockRepo).save(any(Order.class));
}

// 2. STUB: Return predetermined values
@Test
void shouldHandleOutOfStock() {
    InventoryService stubInventory = mock(InventoryService.class);
    when(stubInventory.checkStock("laptop")).thenReturn(false);
    
    OrderService service = new OrderService(stubInventory);
    
    assertThrows(OutOfStockException.class, 
        () -> service.placeOrder(new Order("laptop")));
}

// 3. FAKE: Working implementation with shortcuts (in-memory DB)
class FakeUserRepository implements UserRepository {
    private final Map<String, User> store = new HashMap<>();
    
    public void save(User user) { store.put(user.getId(), user); }
    public Optional<User> findById(String id) { 
        return Optional.ofNullable(store.get(id)); 
    }
}

@Test
void shouldRegisterAndFindUser() {
    UserRepository fakeRepo = new FakeUserRepository();
    UserService service = new UserService(fakeRepo);
    
    service.register("Alice", "alice@test.com");
    
    Optional<User> found = fakeRepo.findById("alice-id");
    assertTrue(found.isPresent());
    assertEquals("Alice", found.get().getName());
}
```

---

## Design for Testability

```java
// ❌ UNTESTABLE: Hard-coded dependencies
class NotificationService {
    void sendReminder(String userId) {
        User user = new UserDatabase().findById(userId);  // Can't mock!
        if (LocalDateTime.now().getHour() < 9) return;    // Can't control time!
        new SmtpClient().send(user.getEmail(), "Reminder"); // Can't mock!
    }
}

// ✅ TESTABLE: Inject everything
class NotificationService {
    private final UserRepository userRepo;
    private final EmailSender emailSender;
    private final Clock clock;  // Inject time for testing!
    
    NotificationService(UserRepository repo, EmailSender sender, Clock clock) {
        this.userRepo = repo;
        this.emailSender = sender;
        this.clock = clock;
    }
    
    void sendReminder(String userId) {
        User user = userRepo.findById(userId).orElseThrow();
        if (LocalDateTime.now(clock).getHour() < 9) return;
        emailSender.send(user.getEmail(), "Reminder");
    }
}

// Test with controlled time
@Test
void shouldNotSendReminderBefore9AM() {
    Clock fixedClock = Clock.fixed(
        LocalDate.now().atTime(8, 0).toInstant(ZoneOffset.UTC),
        ZoneOffset.UTC);
    
    EmailSender mockSender = mock(EmailSender.class);
    NotificationService service = new NotificationService(fakeRepo, mockSender, fixedClock);
    
    service.sendReminder("user123");
    
    verify(mockSender, never()).send(anyString(), anyString());
}
```

---

## What Makes Code Testable

```
1. DEPENDENCY INJECTION: Inject collaborators, don't create them
2. INTERFACES: Depend on abstractions — easy to mock
3. PURE FUNCTIONS: Same input → same output, no side effects
4. SINGLE RESPONSIBILITY: Smaller classes = simpler tests
5. NO STATIC STATE: Static methods/fields can't be mocked
6. INJECTABLE TIME: Use Clock, not System.currentTimeMillis()
7. INJECTABLE RANDOMNESS: Pass Random/Supplier, don't use Math.random()
```

---

## Testing Patterns for LLD Problems

| LLD Problem | What to Test | Test Technique |
|---|---|---|
| **Rate Limiter** | Allow/deny requests | Inject Clock, simulate time progression |
| **LRU Cache** | Eviction on capacity | Unit test with small capacity |
| **Parking Lot** | Park/unpark, capacity | Fake repository, mock payment |
| **Notification** | Correct channel dispatched | Mock channels, verify calls |
| **State Machine** | Valid/invalid transitions | Assert state after each event |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          TESTING & TESTABLE DESIGN                            │
├──────────────────┬───────────────────────────────────────────┤
│ Unit Test        │ Arrange-Act-Assert. Test ONE behavior.     │
│ Mock             │ Verify interactions (was method called?)   │
│ Stub             │ Return predetermined values                │
│ Fake             │ Working shortcut (in-memory repository)    │
├──────────────────┼───────────────────────────────────────────┤
│ Testable Design  │ Inject deps, use interfaces, inject time   │
│ Untestable Signs │ new inside methods, static state, no DI    │
├──────────────────┴───────────────────────────────────────────┤
│ "Testable code and well-designed code are the same thing —    │
│  DI, interfaces, and SRP naturally make code easy to test     │
│  in isolation with mocks and fakes."                          │
└──────────────────────────────────────────────────────────────┘
```
