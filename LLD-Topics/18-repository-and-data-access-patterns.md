# 🎯 LLD Topic 18: Repository & Data Access Patterns

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering the Repository Pattern, DAO pattern, Unit of Work, in-memory implementations for interviews, and how to separate persistence from business logic in LLD designs.

---

## Core Concept

The **Repository Pattern** abstracts data storage behind an interface so business logic doesn't know or care whether data lives in MySQL, MongoDB, Redis, or an in-memory HashMap. This is the #1 pattern for data access in LLD interviews.

---

## Repository Pattern

```java
// Generic repository interface
interface Repository<T, ID> {
    void save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    void deleteById(ID id);
    boolean existsById(ID id);
}

// Domain-specific repository with custom queries
interface OrderRepository extends Repository<Order, String> {
    List<Order> findByCustomerId(String customerId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByDateRange(LocalDate start, LocalDate end);
}

// In-memory implementation (USE THIS IN INTERVIEWS)
class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> store = new ConcurrentHashMap<>();
    
    public void save(Order order) {
        store.put(order.getId(), order);
    }
    
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
    
    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }
    
    public void deleteById(String id) {
        store.remove(id);
    }
    
    public boolean existsById(String id) {
        return store.containsKey(id);
    }
    
    public List<Order> findByCustomerId(String customerId) {
        return store.values().stream()
            .filter(o -> o.getCustomerId().equals(customerId))
            .collect(Collectors.toList());
    }
    
    public List<Order> findByStatus(OrderStatus status) {
        return store.values().stream()
            .filter(o -> o.getStatus() == status)
            .collect(Collectors.toList());
    }
    
    public List<Order> findByDateRange(LocalDate start, LocalDate end) {
        return store.values().stream()
            .filter(o -> !o.getCreatedDate().isBefore(start) && !o.getCreatedDate().isAfter(end))
            .collect(Collectors.toList());
    }
}
```

---

## Service Layer Uses Repository

```java
class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentGateway paymentGateway;
    
    OrderService(OrderRepository repo, InventoryService inv, PaymentGateway pay) {
        this.orderRepository = repo;
        this.inventoryService = inv;
        this.paymentGateway = pay;
    }
    
    Order placeOrder(CreateOrderRequest request) {
        // Business logic — doesn't know about database!
        inventoryService.reserveStock(request.getItems());
        paymentGateway.charge(request.getCustomerId(), request.getTotal());
        
        Order order = new Order(generateId(), request.getCustomerId(), 
                                request.getItems(), OrderStatus.CONFIRMED);
        orderRepository.save(order);  // Persistence is abstracted
        return order;
    }
    
    Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

---

## Repository with Indexes (Interview Optimization)

```java
// When you need O(1) lookups by multiple fields
class IndexedUserRepository implements UserRepository {
    private final Map<String, User> byId = new ConcurrentHashMap<>();
    private final Map<String, User> byEmail = new ConcurrentHashMap<>();
    private final Map<String, List<User>> byCity = new ConcurrentHashMap<>();
    
    public void save(User user) {
        byId.put(user.getId(), user);
        byEmail.put(user.getEmail(), user);
        byCity.computeIfAbsent(user.getCity(), k -> new ArrayList<>()).add(user);
    }
    
    public Optional<User> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }
    
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(byEmail.get(email));  // O(1) instead of O(n)
    }
    
    public List<User> findByCity(String city) {
        return byCity.getOrDefault(city, List.of());
    }
}
```

---

## DAO vs Repository

```
DAO (Data Access Object):
  - Closer to the DATABASE (SQL queries, table structure)
  - One DAO per TABLE
  - Methods: insert(), update(), delete(), selectById()

Repository:
  - Closer to the DOMAIN (business objects)
  - One Repository per AGGREGATE
  - Methods: save(), findByCustomer(), findActive()

In LLD interviews: Use REPOSITORY — it's more domain-focused
```

---

## Interview Talking Points

> *"I always separate persistence from business logic using the Repository pattern. The OrderService depends on an OrderRepository interface — not a database. In the interview, I implement it with ConcurrentHashMap for simplicity, but in production it could be backed by DynamoDB, PostgreSQL, or any storage. This makes my code testable and the storage swappable."*

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          REPOSITORY & DATA ACCESS PATTERNS                    │
├──────────────────┬───────────────────────────────────────────┤
│ Repository       │ Interface: save, findById, findBy...       │
│                  │ Hides storage details from business logic  │
├──────────────────┼───────────────────────────────────────────┤
│ In-Memory Impl   │ ConcurrentHashMap — USE IN INTERVIEWS     │
│                  │ Add secondary indexes for O(1) lookups     │
├──────────────────┼───────────────────────────────────────────┤
│ Generic Repo     │ Repository<T, ID> for DRY across entities  │
│ Domain Repo      │ OrderRepository with findByCustomer()      │
├──────────────────┼───────────────────────────────────────────┤
│ Service Layer    │ Business logic → Repository → Storage      │
│                  │ Service doesn't know about database         │
├──────────────────┴───────────────────────────────────────────┤
│ "Repository abstracts persistence so business logic is pure,  │
│  testable, and storage-agnostic. In interviews, I use         │
│  ConcurrentHashMap; in production, it's DynamoDB or SQL."     │
└──────────────────────────────────────────────────────────────┘
```
