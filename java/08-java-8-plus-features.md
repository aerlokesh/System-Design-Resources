# 🎯 Topic 8: Java 8+ Features — Streams, Lambdas, Optional

> **Java Interview — Deep Dive**
> A comprehensive guide covering functional programming in Java — lambdas, functional interfaces, streams, Optional, and modern Java features for interviews.

---

## Table of Contents

1. [Lambda Expressions](#lambda-expressions)
2. [Functional Interfaces](#functional-interfaces)
3. [Method References](#method-references)
4. [Stream API Deep Dive](#stream-api-deep-dive)
5. [Stream Operations — Intermediate & Terminal](#stream-operations--intermediate--terminal)
6. [Collectors & Grouping](#collectors--grouping)
7. [Optional — Handling Nulls](#optional--handling-nulls)
8. [Default & Static Methods in Interfaces](#default--static-methods-in-interfaces)
9. [Java 9-21 Key Features](#java-9-21-key-features)
10. [Interview Talking Points](#interview-talking-points)
11. [Common Interview Mistakes](#common-interview-mistakes)
12. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Lambda Expressions

### Syntax

```java
// Full syntax
(parameters) -> { body; return value; }

// Simplified
(a, b) -> a + b              // Two params, expression body
x -> x * 2                   // Single param (no parens needed)
() -> System.out.println("Hi") // No params
(String s) -> s.length()     // Explicit type

// Examples
Comparator<String> comp = (a, b) -> a.compareTo(b);
Runnable task = () -> System.out.println("Running");
Function<String, Integer> len = s -> s.length();
```

### Lambda vs Anonymous Class

```java
// Anonymous class (pre-Java 8)
Runnable r1 = new Runnable() {
    @Override
    public void run() {
        System.out.println("Anonymous");
    }
};

// Lambda (Java 8+)
Runnable r2 = () -> System.out.println("Lambda");
```

| Feature | Anonymous Class | Lambda |
|---------|---------------|--------|
| `this` reference | Own instance | Enclosing class |
| Can implement multiple methods | ✅ | ❌ (single abstract method) |
| State (fields) | ✅ | ❌ |
| Bytecode | Separate `.class` file | `invokedynamic` (no extra class) |
| Performance | Slower (class loading) | Faster |

### Effectively Final

```java
int x = 10;
// x = 20;  // ❌ If uncommented, lambda below won't compile
Runnable r = () -> System.out.println(x);  // x must be effectively final
```

---

## Functional Interfaces

A functional interface has **exactly one abstract method** (can have default/static methods).

### Built-in Functional Interfaces

| Interface | Method | Signature | Use Case |
|-----------|--------|-----------|----------|
| `Function<T,R>` | `apply(T)` | `T → R` | Transform |
| `Predicate<T>` | `test(T)` | `T → boolean` | Filter |
| `Consumer<T>` | `accept(T)` | `T → void` | Side effects |
| `Supplier<T>` | `get()` | `() → T` | Factory |
| `UnaryOperator<T>` | `apply(T)` | `T → T` | Same-type transform |
| `BinaryOperator<T>` | `apply(T,T)` | `(T,T) → T` | Reduction |
| `BiFunction<T,U,R>` | `apply(T,U)` | `(T,U) → R` | Two-arg transform |
| `BiPredicate<T,U>` | `test(T,U)` | `(T,U) → boolean` | Two-arg filter |

### Composing Functions

```java
Function<String, String> toUpper = String::toUpperCase;
Function<String, String> trim = String::trim;
Function<String, String> trimThenUpper = trim.andThen(toUpper);

Predicate<String> notEmpty = s -> !s.isEmpty();
Predicate<String> startsWithA = s -> s.startsWith("A");
Predicate<String> combined = notEmpty.and(startsWithA);
```

### Custom Functional Interface

```java
@FunctionalInterface
public interface Transformer<T> {
    T transform(T input);
    
    // Default methods allowed
    default Transformer<T> andThen(Transformer<T> after) {
        return input -> after.transform(this.transform(input));
    }
}
```

---

## Method References

```java
// 1. Static method reference
Function<String, Integer> parse = Integer::parseInt;     // s -> Integer.parseInt(s)

// 2. Instance method of particular object
String str = "hello";
Supplier<Integer> len = str::length;                     // () -> str.length()

// 3. Instance method of arbitrary object
Function<String, String> upper = String::toUpperCase;    // s -> s.toUpperCase()

// 4. Constructor reference
Supplier<ArrayList<String>> factory = ArrayList::new;    // () -> new ArrayList<>()
Function<Integer, int[]> arrFactory = int[]::new;        // n -> new int[n]
```

---

## Stream API Deep Dive

### What Is a Stream?

A **pipeline** of operations on a data source (collection, array, I/O) that supports functional-style processing.

```java
List<String> names = List.of("Alice", "Bob", "Charlie", "Dave");

List<String> result = names.stream()          // 1. Source
    .filter(n -> n.length() > 3)              // 2. Intermediate (lazy)
    .map(String::toUpperCase)                 // 3. Intermediate (lazy)
    .sorted()                                 // 4. Intermediate (lazy)
    .collect(Collectors.toList());            // 5. Terminal (triggers execution)
// Result: [ALICE, CHARLIE, DAVE]
```

### Stream Properties

| Property | Detail |
|----------|--------|
| **Lazy** | Intermediate ops don't execute until terminal op |
| **Single-use** | Can't reuse a stream after terminal op |
| **Non-mutating** | Doesn't modify the source |
| **Short-circuiting** | `findFirst()`, `anyMatch()`, `limit()` can stop early |
| **Parallel** | `.parallelStream()` for multi-threaded |

### Creating Streams

```java
// From collection
list.stream();
list.parallelStream();

// From values
Stream.of("a", "b", "c");
Stream.empty();

// From array
Arrays.stream(new int[]{1, 2, 3});

// Infinite streams
Stream.iterate(0, n -> n + 1);           // 0, 1, 2, 3, ...
Stream.iterate(0, n -> n < 100, n -> n + 1);  // Java 9+ with predicate
Stream.generate(Math::random);            // Random numbers forever

// From range
IntStream.range(1, 10);       // 1 to 9
IntStream.rangeClosed(1, 10); // 1 to 10
```

---

## Stream Operations — Intermediate & Terminal

### Intermediate Operations (Lazy, Return Stream)

```java
.filter(Predicate)          // Keep elements matching condition
.map(Function)              // Transform each element
.flatMap(Function)          // Flatten nested structures
.distinct()                 // Remove duplicates (uses equals())
.sorted()                   // Natural order
.sorted(Comparator)         // Custom order
.peek(Consumer)             // Debug — side effect without changing stream
.limit(n)                   // Take first n elements
.skip(n)                    // Skip first n elements
.takeWhile(Predicate)       // Java 9+ — take while condition true
.dropWhile(Predicate)       // Java 9+ — skip while condition true
```

### Terminal Operations (Trigger Execution)

```java
.collect(Collector)         // Collect to collection
.toList()                   // Java 16+ — immutable list
.forEach(Consumer)          // Perform action on each
.count()                    // Count elements
.reduce(BinaryOperator)     // Reduce to single value
.min(Comparator)            // Smallest element
.max(Comparator)            // Largest element
.findFirst()                // First element (Optional)
.findAny()                  // Any element (for parallel)
.anyMatch(Predicate)        // Any element matches?
.allMatch(Predicate)        // All elements match?
.noneMatch(Predicate)       // No element matches?
.toArray()                  // Convert to array
```

### flatMap — Flattening Nested Structures

```java
List<List<String>> nested = List.of(
    List.of("a", "b"),
    List.of("c", "d"),
    List.of("e")
);

// ❌ map gives Stream<List<String>>
nested.stream().map(list -> list.stream());  // Stream<Stream<String>>

// ✅ flatMap flattens to Stream<String>
List<String> flat = nested.stream()
    .flatMap(Collection::stream)
    .collect(Collectors.toList());  // [a, b, c, d, e]
```

### reduce — Aggregation

```java
// Sum
int sum = List.of(1, 2, 3, 4).stream()
    .reduce(0, Integer::sum);  // 10

// Max
Optional<Integer> max = List.of(1, 2, 3, 4).stream()
    .reduce(Integer::max);  // Optional[4]

// String concatenation
String joined = List.of("a", "b", "c").stream()
    .reduce("", (a, b) -> a + b);  // "abc"
```

---

## Collectors & Grouping

```java
List<Employee> employees = /* ... */;

// Collect to list/set
List<String> names = employees.stream().map(Employee::getName).collect(Collectors.toList());
Set<String> depts = employees.stream().map(Employee::getDept).collect(Collectors.toSet());

// Joining strings
String csv = employees.stream().map(Employee::getName).collect(Collectors.joining(", "));

// Counting
long count = employees.stream().collect(Collectors.counting());

// Averaging
double avgSalary = employees.stream().collect(Collectors.averagingDouble(Employee::getSalary));

// Grouping
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept));

// Grouping with counting
Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept, Collectors.counting()));

// Partitioning (boolean split)
Map<Boolean, List<Employee>> highEarners = employees.stream()
    .collect(Collectors.partitioningBy(e -> e.getSalary() > 100000));

// toMap
Map<Integer, String> idToName = employees.stream()
    .collect(Collectors.toMap(Employee::getId, Employee::getName));

// toMap with merge (handle duplicates)
Map<String, Integer> deptMaxSalary = employees.stream()
    .collect(Collectors.toMap(Employee::getDept, Employee::getSalary, Integer::max));

// Downstream collectors
Map<String, Optional<Employee>> topEarnerByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))
    ));
```

---

## Optional — Handling Nulls

### Why Optional?

```java
// ❌ BAD — NullPointerException risk
String city = user.getAddress().getCity().toUpperCase();

// ✅ GOOD — Safe with Optional
String city = Optional.ofNullable(user)
    .map(User::getAddress)
    .map(Address::getCity)
    .map(String::toUpperCase)
    .orElse("UNKNOWN");
```

### Creating Optional

```java
Optional<String> opt1 = Optional.of("hello");        // Non-null (throws NPE if null)
Optional<String> opt2 = Optional.ofNullable(null);    // May be null
Optional<String> opt3 = Optional.empty();             // Empty
```

### Using Optional

```java
Optional<String> opt = findUser(id);

// Get value
opt.get();                          // ⚠️ Throws NoSuchElementException if empty
opt.orElse("default");              // Value or default
opt.orElseGet(() -> computeDefault());  // Value or lazy default
opt.orElseThrow();                  // Value or NoSuchElementException (Java 10+)
opt.orElseThrow(() -> new UserNotFoundException(id));  // Value or custom exception

// Transform
opt.map(String::toUpperCase);       // Optional<String>
opt.flatMap(this::findAddress);     // Avoids Optional<Optional<>>
opt.filter(s -> s.length() > 3);    // Empty if predicate fails

// Check
opt.isPresent();                    // true if value exists
opt.isEmpty();                      // Java 11+ — true if no value
opt.ifPresent(System.out::println); // Execute if present
opt.ifPresentOrElse(                // Java 9+
    System.out::println,
    () -> System.out.println("empty")
);

// Stream conversion (Java 9+)
opt.stream();                       // Stream of 0 or 1 element
```

### Optional Anti-Patterns

```java
// ❌ Don't use for fields
class User {
    private Optional<String> name;  // ❌ — Optional is for return types
}

// ❌ Don't use in parameters
void process(Optional<String> input) { }  // ❌ — Use @Nullable or overloading

// ❌ Don't use isPresent() + get()
if (opt.isPresent()) {
    return opt.get();  // ❌ — Use orElse() or map()
}

// ❌ Don't use for collections
Optional<List<String>> items;  // ❌ — Use empty list instead
```

---

## Java 9-21 Key Features

### Java 9
- **Modules** (Project Jigsaw): `module-info.java`
- **JShell**: Interactive REPL
- **Collection factory methods**: `List.of()`, `Set.of()`, `Map.of()`
- **Stream**: `takeWhile()`, `dropWhile()`, `ofNullable()`
- **Optional**: `ifPresentOrElse()`, `or()`, `stream()`
- **Private interface methods**

### Java 10
- **`var`** (local variable type inference): `var list = new ArrayList<String>();`
- `List.copyOf()`, `Set.copyOf()`, `Map.copyOf()`

### Java 11 (LTS)
- **String**: `isBlank()`, `strip()`, `repeat()`, `lines()`
- **Files**: `readString()`, `writeString()`
- **`var` in lambdas**: `(var x, var y) -> x + y`
- **HTTP Client API** (standard)

### Java 14
- **Switch Expressions**: `var result = switch(x) { case 1 -> "one"; };`
- **Records** (preview): `record Point(int x, int y) {}`
- **Helpful NullPointerExceptions**: Better messages

### Java 16
- **Records** (final): Immutable data carriers
- **Pattern Matching** for `instanceof`: `if (obj instanceof String s)`
- **Stream.toList()**: Immutable list collection

### Java 17 (LTS)
- **Sealed Classes**: `sealed class Shape permits Circle, Rectangle {}`
- **Pattern Matching for switch** (preview)
- Text Blocks (final)

### Java 21 (LTS)
- **Virtual Threads** (Project Loom): Lightweight threads
- **Pattern Matching for switch** (final)
- **Record Patterns**: Destructuring in pattern matching
- **Sequenced Collections**: `SequencedCollection`, `SequencedMap`

### Records Deep Dive

```java
// Compact, immutable data class
public record User(String name, int age) {
    // Auto-generates: constructor, getters (name(), age()), equals(), hashCode(), toString()
    
    // Compact constructor for validation
    public User {
        if (age < 0) throw new IllegalArgumentException("Age cannot be negative");
        name = name.trim();  // Can modify parameters
    }
}

User user = new User("Alice", 30);
user.name();   // "Alice" (not getName())
user.age();    // 30
```

### Sealed Classes

```java
public sealed class Shape permits Circle, Rectangle, Triangle {
    // Only Circle, Rectangle, Triangle can extend Shape
}

public final class Circle extends Shape { }
public final class Rectangle extends Shape { }
public non-sealed class Triangle extends Shape { }  // Open for further extension
```

### Virtual Threads (Java 21)

```java
// Platform thread (traditional) — 1 thread = 1 OS thread (~1MB)
Thread.ofPlatform().start(() -> doWork());

// Virtual thread — millions possible, scheduled on carrier threads
Thread.ofVirtual().start(() -> doWork());

// With executor
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 1_000_000; i++) {
        executor.submit(() -> {
            Thread.sleep(Duration.ofSeconds(1));
            return "done";
        });
    }
}
```

---

## Interview Talking Points

### "Explain Streams vs Loops"

> *"Streams provide a declarative, functional approach — you describe WHAT to do, not HOW. They support lazy evaluation, easy parallelism with parallelStream(), and composable operations via method chaining. Traditional loops are imperative and can be more readable for simple operations. I use streams for data transformation pipelines and loops when I need index access, early termination with complex logic, or mutation."*

### "What is Optional and when to use it?"

> *"Optional is a container that may or may not hold a non-null value. It's designed as a return type to explicitly signal that a method might not return a value, replacing null returns. You should use map/flatMap/orElse for safe chaining instead of isPresent+get. Don't use it for fields, parameters, or collections — those have better alternatives like default values and empty collections."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "Streams are always faster" | Streams have overhead; loops can be faster for simple operations |
| "parallelStream is always better" | Thread overhead can make it slower for small collections |
| "Optional.get() is fine" | Throws exception if empty — use `orElse()` or `map()` |
| "`var` means JavaScript-style dynamic typing" | `var` is compile-time type inference — still strongly typed |
| "Records are just DTOs" | Records are immutable, have value semantics with equals/hashCode |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│           JAVA 8+ FEATURES CHEAT SHEET                      │
├────────────────────────────────────────────────────────────┤
│ Lambda: (params) -> expression | { statements }             │
│ Functional Interfaces: Function, Predicate, Consumer, Supplier│
│ Method Ref: Class::method, obj::method, Class::new          │
│                                                            │
│ Streams: source → intermediate (lazy) → terminal (triggers) │
│ • filter, map, flatMap, sorted, distinct (intermediate)     │
│ • collect, forEach, reduce, count, findFirst (terminal)     │
│ • Collectors: toList, groupingBy, partitioningBy, joining   │
│                                                            │
│ Optional: of, ofNullable, empty                             │
│ • map, flatMap, filter, orElse, orElseGet, orElseThrow     │
│ • Return types ONLY — not fields/params/collections        │
│                                                            │
│ Modern Java:                                                │
│ • var (Java 10), Records (Java 16), Sealed (Java 17)       │
│ • Virtual Threads (Java 21) — millions of lightweight threads│
│ • Pattern Matching switch (Java 21)                         │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Design Patterns in Java →](./09-design-patterns-in-java.md)
