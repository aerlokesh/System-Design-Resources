# 🎯 LLD Topic 12: Generics & Type Safety

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering Java Generics — type parameters, bounded types, wildcards, type erasure, generic design patterns, and how to write type-safe reusable code in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 12: Generics \& Type Safety](#-lld-topic-12-generics--type-safety)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Why Generics Matter](#why-generics-matter)
    - [The Cost of No Generics](#the-cost-of-no-generics)
  - [Generic Classes](#generic-classes)
    - [Generic Repository — The Most Important LLD Generic](#generic-repository--the-most-important-lld-generic)
  - [Generic Interfaces](#generic-interfaces)
  - [Generic Methods](#generic-methods)
  - [Bounded Type Parameters](#bounded-type-parameters)
  - [Wildcards](#wildcards)
    - [Upper Bounded Wildcard: `? extends T` (Read-Only)](#upper-bounded-wildcard--extends-t-read-only)
    - [Lower Bounded Wildcard: `? super T` (Write-Only)](#lower-bounded-wildcard--super-t-write-only)
    - [Unbounded Wildcard: `?`](#unbounded-wildcard-)
  - [PECS](#pecs)
  - [Type Erasure](#type-erasure)
  - [Generic Design Patterns for LLD](#generic-design-patterns-for-lld)
    - [Generic Event Bus](#generic-event-bus)
    - [Generic Cache with TTL](#generic-cache-with-ttl)
    - [Generic Result Type](#generic-result-type)
    - [Generic Builder](#generic-builder)
  - [Generics in Real LLD Problems](#generics-in-real-lld-problems)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Designing a Generic Component](#when-designing-a-generic-component)
    - [When Asked "Why Generics?"](#when-asked-why-generics)
    - [When Using Wildcards](#when-using-wildcards)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Generics enable **type-safe, reusable code** — write once, use with any type, catch errors at compile time instead of runtime. They're the foundation of good LLD: every Repository, Cache, EventBus, and Result type should be generic.

```
Without generics:  List list = new ArrayList();
                   list.add("hello");
                   list.add(42);  // No error — mixed types!
                   String s = (String) list.get(1);  // ClassCastException at RUNTIME!

With generics:     List<String> list = new ArrayList<>();
                   list.add("hello");
                   list.add(42);  // COMPILE ERROR — caught immediately!
                   String s = list.get(0);  // No cast needed!
```

> **Interview Gold**: *"I use generics everywhere — Repository<T, ID>, Cache<K, V>, Result<T>, EventHandler<E>. This gives me compile-time type safety, eliminates casting, and follows DRY because one generic class replaces N type-specific classes."*

---

## Why Generics Matter

```
1. TYPE SAFETY: Errors caught at COMPILE time, not runtime
2. CODE REUSE: One Repository<T> works for User, Order, Product
3. NO CASTING: Compiler knows the type — no (String) casts
4. SELF-DOCUMENTING: Cache<UserId, UserProfile> reads its own docs
5. IDE SUPPORT: Autocomplete, refactoring, navigation all work
```

### The Cost of No Generics

```java
// ❌ BAD: Raw types — no type safety
class UserCache {
    private Map store = new HashMap();
    
    void put(String key, Object value) { store.put(key, value); }
    
    Object get(String key) { return store.get(key); }
}

// Caller must cast — error-prone
User user = (User) cache.get("user123");  // ClassCastException if wrong type!

// ✅ GOOD: Generics — compile-time safety
class Cache<K, V> {
    private final Map<K, V> store = new HashMap<>();
    
    void put(K key, V value) { store.put(key, value); }
    
    Optional<V> get(K key) { return Optional.ofNullable(store.get(key)); }
}

// No casting needed — compiler enforces types
Cache<String, User> userCache = new Cache<>();
Optional<User> user = userCache.get("user123");  // Returns Optional<User>
```

---

## Generic Classes

```java
// Generic Pair — holds two values of any types
class Pair<A, B> {
    private final A first;
    private final B second;
    
    Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }
    
    A getFirst() { return first; }
    B getSecond() { return second; }
    
    // Factory method — infers types
    static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }
}

// Usage
Pair<String, Integer> nameAge = Pair.of("Alice", 30);
Pair<User, List<Order>> userOrders = Pair.of(user, orders);

// Generic Triple
class Triple<A, B, C> {
    private final A first;
    private final B second;
    private final C third;
    // ...
}
```

### Generic Repository — The Most Important LLD Generic

```java
// Generic repository interface — works with ANY entity type
interface Repository<T, ID> {
    void save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    void deleteById(ID id);
    boolean existsById(ID id);
    long count();
}

class InMemoryRepository<T, ID> implements Repository<T, ID> {
    private final Map<ID, T> store = new ConcurrentHashMap<>();
    private final Function<T, ID> idExtractor;
    
    InMemoryRepository(Function<T, ID> idExtractor) {
        this.idExtractor = idExtractor;
    }
    
    public void save(T entity) {
        store.put(idExtractor.apply(entity), entity);
    }
    
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(store.get(id));
    }
    
    public List<T> findAll() { return new ArrayList<>(store.values()); }
    
    public void deleteById(ID id) { store.remove(id); }
    
    public boolean existsById(ID id) { return store.containsKey(id); }
    
    public long count() { return store.size(); }
}

// Usage: Type-safe for different entities — ONE implementation, MANY uses
Repository<User, String> userRepo = new InMemoryRepository<>(User::getId);
Repository<Order, Long> orderRepo = new InMemoryRepository<>(Order::getId);
Repository<Product, UUID> productRepo = new InMemoryRepository<>(Product::getId);

userRepo.save(new User("123", "Alice"));
// userRepo.save(new Order(...));  // COMPILE ERROR! Type safety!
```

---

## Generic Interfaces

```java
// Generic event handler
interface EventHandler<E> {
    void handle(E event);
}

class OrderCreatedHandler implements EventHandler<OrderCreatedEvent> {
    public void handle(OrderCreatedEvent event) {
        sendConfirmationEmail(event.getOrderId());
    }
}

class PaymentProcessedHandler implements EventHandler<PaymentProcessedEvent> {
    public void handle(PaymentProcessedEvent event) {
        updateOrderStatus(event.getOrderId(), "PAID");
    }
}

// Generic comparator-like interface
interface Matcher<T> {
    boolean matches(T item);
}

class AgeRangeMatcher implements Matcher<User> {
    private final int minAge, maxAge;
    
    AgeRangeMatcher(int min, int max) { this.minAge = min; this.maxAge = max; }
    
    public boolean matches(User user) {
        return user.getAge() >= minAge && user.getAge() <= maxAge;
    }
}

// Generic filtering
class FilterEngine<T> {
    List<T> filter(List<T> items, Matcher<T> matcher) {
        return items.stream()
            .filter(matcher::matches)
            .collect(Collectors.toList());
    }
    
    List<T> filter(List<T> items, List<Matcher<T>> matchers) {
        return items.stream()
            .filter(item -> matchers.stream().allMatch(m -> m.matches(item)))
            .collect(Collectors.toList());
    }
}
```

---

## Generic Methods

```java
class CollectionUtils {
    
    // Generic method — type parameter declared before return type
    static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        return list.stream().filter(predicate).collect(Collectors.toList());
    }
    
    // Find max with Comparable
    static <T extends Comparable<T>> T findMax(List<T> list) {
        if (list.isEmpty()) throw new IllegalArgumentException("Empty list");
        return list.stream().max(Comparable::compareTo).orElseThrow();
    }
    
    // Merge two maps
    static <K, V> Map<K, V> merge(Map<K, V> map1, Map<K, V> map2) {
        Map<K, V> result = new HashMap<>(map1);
        result.putAll(map2);
        return result;
    }
    
    // Frequency count
    static <T> Map<T, Long> frequencyCount(List<T> items) {
        return items.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
    
    // Safe cast
    static <T> Optional<T> safeCast(Object obj, Class<T> type) {
        if (type.isInstance(obj)) return Optional.of(type.cast(obj));
        return Optional.empty();
    }
}

// Usage — type inference
List<String> names = CollectionUtils.filter(allNames, n -> n.startsWith("A"));
String longest = CollectionUtils.findMax(names);
```

---

## Bounded Type Parameters

```java
// Upper bound: T must extend Comparable
class SortedList<T extends Comparable<T>> {
    private final List<T> items = new ArrayList<>();
    
    void add(T item) {
        int index = Collections.binarySearch(items, item);
        if (index < 0) index = -(index + 1);
        items.add(index, item);
    }
    
    T getMin() { return items.isEmpty() ? null : items.get(0); }
    T getMax() { return items.isEmpty() ? null : items.get(items.size() - 1); }
    
    List<T> getRange(T from, T to) {
        return items.stream()
            .filter(i -> i.compareTo(from) >= 0 && i.compareTo(to) <= 0)
            .collect(Collectors.toList());
    }
}

// Usage
SortedList<Integer> scores = new SortedList<>();
scores.add(85); scores.add(42); scores.add(97);
scores.getMin();  // 42
scores.getMax();  // 97
scores.getRange(40, 90);  // [42, 85]

// SortedList<Object> list = ...;  // COMPILE ERROR — Object isn't Comparable

// Multiple bounds
class Serializer<T extends Serializable & Comparable<T>> {
    byte[] serialize(T obj) { /* ... */ }
}

// Bounded type in method
static <T extends Number> double sum(List<T> numbers) {
    return numbers.stream().mapToDouble(Number::doubleValue).sum();
}

sum(List.of(1, 2, 3));       // Works — Integer extends Number
sum(List.of(1.5, 2.5));      // Works — Double extends Number
// sum(List.of("a", "b"));   // COMPILE ERROR — String doesn't extend Number
```

---

## Wildcards

### Upper Bounded Wildcard: `? extends T` (Read-Only)

```java
// Can READ as T, but can NOT write (except null)
void printShapes(List<? extends Shape> shapes) {
    for (Shape s : shapes) {
        System.out.println(s.getArea());  // Safe — every element IS a Shape
    }
    // shapes.add(new Circle());  // COMPILE ERROR!
    // Why? shapes could be List<Square>, and adding Circle would break it
}

// Works with any subtype of Shape
printShapes(List.of(new Circle(5)));
printShapes(List.of(new Rectangle(3, 4)));
printShapes(List.of(new Circle(5), new Rectangle(3, 4)));  // Mixed subtypes
```

### Lower Bounded Wildcard: `? super T` (Write-Only)

```java
// Can WRITE T (and subtypes), but can only READ as Object
void addCircles(List<? super Circle> list, int count) {
    for (int i = 0; i < count; i++) {
        list.add(new Circle(i));  // Safe — list accepts Circle or its supertypes
    }
    // Circle c = list.get(0);   // COMPILE ERROR — can only get Object
    Object obj = list.get(0);    // OK — everything is at least Object
}

// Works with List<Circle>, List<Shape>, List<Object>
List<Shape> shapes = new ArrayList<>();
addCircles(shapes, 5);  // OK — Shape is a supertype of Circle
```

### Unbounded Wildcard: `?`

```java
// When you don't care about the type at all
void printList(List<?> list) {
    for (Object item : list) {
        System.out.println(item);
    }
}

boolean isNullOrEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
}
```

---

## PECS

> **Producer Extends, Consumer Super**

```java
// PECS Rule:
// If you READ from it → ? extends T  (it PRODUCES items for you)
// If you WRITE to it → ? super T    (it CONSUMES items from you)

// Example: Copy elements from source to destination
static <T> void copy(List<? extends T> source,    // Producer: we READ from source
                     List<? super T> destination) { // Consumer: we WRITE to dest
    for (T item : source) {
        destination.add(item);
    }
}

// Real-world example: Collections.sort
static <T> void sort(List<T> list, Comparator<? super T> comparator) {
    // Comparator<? super T> means a Comparator<Object> can sort List<String>
    // because Object is a supertype of String
}

// Comparator for Employee works for Manager too (Manager extends Employee)
Comparator<Employee> byName = Comparator.comparing(Employee::getName);
List<Manager> managers = getManagers();
managers.sort(byName);  // Works because Comparator<? super Manager> accepts Comparator<Employee>
```

---

## Type Erasure

```java
// At RUNTIME, generics are erased — the JVM doesn't know about type parameters

// This DOESN'T work:
class TypedFactory<T> {
    T create() {
        // return new T();  // COMPILE ERROR — can't instantiate type parameter!
    }
}

// Workaround: Pass Class<T>
class TypedFactory<T> {
    private final Class<T> type;
    
    TypedFactory(Class<T> type) { this.type = type; }
    
    T create() throws Exception {
        return type.getDeclaredConstructor().newInstance();
    }
    
    boolean isInstance(Object obj) {
        return type.isInstance(obj);
    }
}

// Type erasure means these are the SAME class at runtime:
// List<String> and List<Integer> → both become List
// Cannot do: if (list instanceof List<String>)  // COMPILE ERROR

// Workaround for type-safe containers
class TypeSafeMap {
    private final Map<Class<?>, Object> store = new HashMap<>();
    
    <T> void put(Class<T> type, T value) {
        store.put(type, value);
    }
    
    <T> T get(Class<T> type) {
        return type.cast(store.get(type));  // Safe cast using Class<T>
    }
}

TypeSafeMap config = new TypeSafeMap();
config.put(String.class, "hello");
config.put(Integer.class, 42);
String s = config.get(String.class);  // No cast needed, type-safe
```

---

## Generic Design Patterns for LLD

### Generic Event Bus

```java
class EventBus {
    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();
    
    <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(handler);
    }
    
    <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> list = handlers.get(eventType);
        if (list != null) list.remove(handler);
    }
    
    @SuppressWarnings("unchecked")
    <T> void publish(T event) {
        List<Consumer<?>> subs = handlers.getOrDefault(event.getClass(), List.of());
        for (Consumer<?> handler : subs) {
            ((Consumer<T>) handler).accept(event);
        }
    }
}

// Type-safe usage
record OrderPlacedEvent(String orderId, double total) {}
record UserRegisteredEvent(String userId, String email) {}

EventBus bus = new EventBus();
bus.subscribe(OrderPlacedEvent.class, e -> System.out.println("Order: " + e.orderId()));
bus.subscribe(UserRegisteredEvent.class, e -> sendWelcome(e.email()));
bus.publish(new OrderPlacedEvent("ORD-123", 99.99));
```

### Generic Cache with TTL

```java
class Cache<K, V> {
    private final Map<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    private final Duration defaultTtl;
    
    record CacheEntry<V>(V value, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }
    
    Cache(Duration defaultTtl) { this.defaultTtl = defaultTtl; }
    
    void put(K key, V value) {
        put(key, value, defaultTtl);
    }
    
    void put(K key, V value, Duration ttl) {
        store.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
    }
    
    Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }
    
    V getOrLoad(K key, Function<K, V> loader) {
        return get(key).orElseGet(() -> {
            V value = loader.apply(key);
            put(key, value);
            return value;
        });
    }
    
    void evictExpired() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
    }
    
    long size() { return store.size(); }
}

// Type-safe usage
Cache<String, UserProfile> profileCache = new Cache<>(Duration.ofMinutes(5));
Cache<ProductId, PricingInfo> priceCache = new Cache<>(Duration.ofSeconds(30));

UserProfile profile = profileCache.getOrLoad("user123", id -> userService.fetchProfile(id));
```

### Generic Result Type

```java
class Result<T> {
    private final T value;
    private final String error;
    private final boolean success;
    
    private Result(T value, String error, boolean success) {
        this.value = value;
        this.error = error;
        this.success = success;
    }
    
    static <T> Result<T> success(T value) {
        return new Result<>(Objects.requireNonNull(value), null, true);
    }
    
    static <T> Result<T> failure(String error) {
        return new Result<>(null, Objects.requireNonNull(error), false);
    }
    
    boolean isSuccess() { return success; }
    boolean isFailure() { return !success; }
    
    T getValue() {
        if (!success) throw new IllegalStateException("Cannot get value from failure");
        return value;
    }
    
    String getError() {
        if (success) throw new IllegalStateException("Cannot get error from success");
        return error;
    }
    
    T getOrElse(T defaultValue) { return success ? value : defaultValue; }
    
    T getOrElseGet(Supplier<T> supplier) { return success ? value : supplier.get(); }
    
    <R> Result<R> map(Function<T, R> fn) {
        if (!success) return Result.failure(error);
        return Result.success(fn.apply(value));
    }
    
    <R> Result<R> flatMap(Function<T, Result<R>> fn) {
        if (!success) return Result.failure(error);
        return fn.apply(value);
    }
    
    void ifSuccess(Consumer<T> action) { if (success) action.accept(value); }
    void ifFailure(Consumer<String> action) { if (!success) action.accept(error); }
}

// Usage: Composable error handling without exceptions
Result<User> userResult = userService.findByEmail("alice@example.com");
Result<Order> orderResult = userResult
    .flatMap(user -> orderService.getLatestOrder(user.getId()))
    .map(order -> enrichWithShipping(order));

orderResult.ifSuccess(order -> renderOrder(order));
orderResult.ifFailure(error -> showError(error));
```

### Generic Builder

```java
// Builder that returns the correct type for chaining
abstract class AbstractBuilder<T, B extends AbstractBuilder<T, B>> {
    @SuppressWarnings("unchecked")
    protected B self() { return (B) this; }
    
    abstract T build();
}

class UserBuilder extends AbstractBuilder<User, UserBuilder> {
    private String name;
    private String email;
    
    UserBuilder name(String name) { this.name = name; return self(); }
    UserBuilder email(String email) { this.email = email; return self(); }
    
    User build() { return new User(name, email); }
}
```

---

## Generics in Real LLD Problems

| LLD Problem | Generic Usage |
|---|---|
| **Repository** | `Repository<T, ID>` — one interface for all entities |
| **Cache** | `Cache<K, V>` — type-safe key-value with TTL |
| **Event Bus** | `EventHandler<E>`, `subscribe(Class<E>)` — type-safe events |
| **Result** | `Result<T>` — composable success/failure |
| **Comparator** | `Comparator<? super T>` — sortable by supertype |
| **Filter** | `Predicate<T>`, `Matcher<T>` — type-safe filtering |
| **Object Pool** | `Pool<T>` — reusable connections, threads |
| **Tree** | `TreeNode<T>` — generic tree data structure |
| **Graph** | `Graph<V, E>` — vertices of type V, edges of type E |
| **Priority Queue** | `PriorityQueue<T extends Comparable<T>>` |

---

## Interview Talking Points & Scripts

### When Designing a Generic Component

> *"I'll make this Cache generic — Cache<K, V> — so it works with any key-value pair. The key type K lets us use String, UserId, or any custom type. The value type V means the caller gets type-safe results without casting. This one implementation replaces separate UserCache, OrderCache, ProductCache classes."*

### When Asked "Why Generics?"

> *"Generics give me three things: compile-time type safety so bugs are caught before runtime, code reuse so one Repository<T> works for all entities, and self-documenting code — when I see Cache<UserId, UserProfile>, I know exactly what it stores without reading the implementation."*

### When Using Wildcards

> *"I'm using `? extends Shape` here because this method only READS from the list — it produces shapes for me to process. If I needed to WRITE to it, I'd use `? super Shape`. This follows the PECS principle: Producer Extends, Consumer Super."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Using raw types (List instead of List<String>)
   Fix: ALWAYS specify type parameters. Raw types lose all type safety.

❌ Mistake 2: Creating separate UserRepo, OrderRepo, ProductRepo
   Fix: One generic Repository<T, ID> serves all entities.

❌ Mistake 3: Casting results from generic methods
   Fix: If you're casting, your generics are wrong. Fix the type params.

❌ Mistake 4: Using Object when you mean T
   Fix: Introduce a type parameter. Map<String, Object> → Map<K, V>.

❌ Mistake 5: Trying to create new T() (type erasure)
   Fix: Pass Class<T> and use reflection, or pass a Supplier<T>.

❌ Mistake 6: Overcomplicating wildcards
   Fix: Start simple. Only use wildcards when you need flexibility.
   Most of the time, plain <T> is enough.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              GENERICS & TYPE SAFETY CHEAT SHEET               │
├──────────────────┬───────────────────────────────────────────┤
│ Generic Class    │ class Cache<K, V> — type-safe containers   │
│ Generic Method   │ static <T> List<T> filter(List<T>, Pred)   │
│ Generic Interface│ interface Repository<T, ID>                │
├──────────────────┼───────────────────────────────────────────┤
│ Bounded Upper    │ <T extends Comparable<T>> — constrained    │
│ Bounded Multiple │ <T extends Serializable & Comparable<T>>   │
├──────────────────┼───────────────────────────────────────────┤
│ ? extends T      │ READ-ONLY (Producer). Can read as T.       │
│ ? super T        │ WRITE-ONLY (Consumer). Can add T.          │
│ PECS Rule        │ Producer Extends, Consumer Super            │
├──────────────────┼───────────────────────────────────────────┤
│ Type Erasure     │ Generics removed at runtime — use Class<T>  │
│                  │ Can't do new T() or instanceof List<String> │
├──────────────────┼───────────────────────────────────────────┤
│ LLD Patterns     │ Repository<T,ID>, Cache<K,V>, Result<T>,   │
│                  │ EventBus<E>, Pool<T>, Matcher<T>            │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "Generics give me compile-time type safety and code reuse     │
│  — one Repository<T> replaces N separate repository classes,  │
│  and the compiler catches type errors before runtime."         │
└──────────────────────────────────────────────────────────────┘
```
