# 🎯 Topic 5: Generics & Type Erasure

> **Java Interview — Deep Dive**
> A comprehensive guide covering Java Generics, wildcards, bounded types, type erasure, and how to articulate these with depth in a Java interview.

---

## Table of Contents

1. [Why Generics?](#why-generics)
2. [Generic Classes & Interfaces](#generic-classes--interfaces)
3. [Generic Methods](#generic-methods)
4. [Bounded Type Parameters](#bounded-type-parameters)
5. [Wildcards — ?, extends, super](#wildcards----extends-super)
6. [PECS — Producer Extends, Consumer Super](#pecs--producer-extends-consumer-super)
7. [Type Erasure — How Generics Work at Runtime](#type-erasure--how-generics-work-at-runtime)
8. [Generics Restrictions & Gotchas](#generics-restrictions--gotchas)
9. [Generic Patterns in Real Code](#generic-patterns-in-real-code)
10. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
11. [Common Interview Mistakes](#common-interview-mistakes)
12. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Why Generics?

### Before Generics (Java < 5)

```java
// ❌ No type safety — runtime ClassCastException
List list = new ArrayList();
list.add("hello");
list.add(42);                              // No compile error!
String s = (String) list.get(1);           // 💥 ClassCastException at runtime!
```

### With Generics (Java 5+)

```java
// ✅ Type-safe at compile time
List<String> list = new ArrayList<>();
list.add("hello");
list.add(42);                              // ❌ COMPILE ERROR!
String s = list.get(0);                    // No cast needed
```

### Benefits

| Benefit | Explanation |
|---------|-------------|
| **Type safety** | Errors caught at compile time, not runtime |
| **No casting** | Compiler inserts casts automatically |
| **Code reuse** | One class/method works with any type |
| **Readability** | `List<String>` is self-documenting |

---

## Generic Classes & Interfaces

```java
// Generic class
public class Box<T> {
    private T item;
    
    public void set(T item) { this.item = item; }
    public T get() { return item; }
}

// Usage
Box<String> stringBox = new Box<>();
stringBox.set("hello");
String s = stringBox.get();  // No cast

Box<Integer> intBox = new Box<>();
intBox.set(42);
Integer i = intBox.get();

// Multiple type parameters
public class Pair<K, V> {
    private K key;
    private V value;
    
    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }
}
Pair<String, Integer> pair = new Pair<>("age", 30);
```

### Convention for Type Parameter Names

| Letter | Convention |
|--------|-----------|
| `T` | Type |
| `E` | Element (collections) |
| `K` | Key |
| `V` | Value |
| `N` | Number |
| `S, U` | Second, third types |

### Generic Interfaces

```java
public interface Repository<T, ID> {
    T findById(ID id);
    List<T> findAll();
    void save(T entity);
    void delete(ID id);
}

public class UserRepository implements Repository<User, Long> {
    @Override
    public User findById(Long id) { /* ... */ }
    @Override
    public List<User> findAll() { /* ... */ }
    // ...
}
```

---

## Generic Methods

```java
// Generic method — type parameter BEFORE return type
public static <T> T getFirst(List<T> list) {
    return list.isEmpty() ? null : list.get(0);
}

// Usage — type inferred
String first = getFirst(List.of("a", "b", "c"));  // T inferred as String
Integer num = getFirst(List.of(1, 2, 3));          // T inferred as Integer

// Explicit type specification (rarely needed)
String first = MyClass.<String>getFirst(someList);

// Multiple type parameters
public static <K, V> Map<K, V> singletonMap(K key, V value) {
    Map<K, V> map = new HashMap<>();
    map.put(key, value);
    return map;
}
```

---

## Bounded Type Parameters

### Upper Bound (`extends`)

```java
// T must be Number or a subclass of Number
public static <T extends Number> double sum(List<T> list) {
    double total = 0;
    for (T num : list) {
        total += num.doubleValue();  // Can call Number methods!
    }
    return total;
}

sum(List.of(1, 2, 3));         // ✅ Integer extends Number
sum(List.of(1.5, 2.5));        // ✅ Double extends Number
sum(List.of("a", "b"));        // ❌ COMPILE ERROR — String doesn't extend Number
```

### Multiple Bounds

```java
// T must extend Comparable AND Serializable
public static <T extends Comparable<T> & Serializable> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}

// Rules:
// - Class bound must come FIRST
// - Only ONE class bound allowed (single inheritance)
// - Multiple interface bounds allowed
<T extends Animal & Comparable<T> & Serializable>  // ✅
<T extends Comparable<T> & Animal>                  // ❌ Class must be first
<T extends Animal & Dog>                            // ❌ Can't have two class bounds
```

---

## Wildcards — ?, extends, super

### Unbounded Wildcard `<?>`

```java
// Accepts any List, regardless of type
public static void printList(List<?> list) {
    for (Object item : list) {
        System.out.println(item);
    }
}

printList(List.of(1, 2, 3));        // ✅
printList(List.of("a", "b", "c"));  // ✅
```

### Upper Bounded Wildcard `<? extends T>`

```java
// Accepts List<Number> or List<any-subclass-of-Number>
public static double sum(List<? extends Number> list) {
    double total = 0;
    for (Number n : list) {
        total += n.doubleValue();
    }
    return total;
}

sum(List.of(1, 2, 3));      // ✅ List<Integer> — Integer extends Number
sum(List.of(1.5, 2.5));     // ✅ List<Double> — Double extends Number

// But you CAN'T add to it (except null)!
List<? extends Number> nums = new ArrayList<Integer>();
nums.add(42);      // ❌ COMPILE ERROR — don't know the actual type
nums.add(null);    // ✅ null is always valid
Number n = nums.get(0);  // ✅ Can read as Number
```

### Lower Bounded Wildcard `<? super T>`

```java
// Accepts List<Integer> or List<any-superclass-of-Integer>
public static void addInts(List<? super Integer> list) {
    list.add(1);   // ✅ Can add Integer
    list.add(2);   // ✅ Can add Integer
}

addInts(new ArrayList<Integer>());  // ✅
addInts(new ArrayList<Number>());   // ✅ Number is super of Integer
addInts(new ArrayList<Object>());   // ✅ Object is super of Integer
addInts(new ArrayList<Double>());   // ❌ Double is NOT super of Integer

// But reading is limited to Object
List<? super Integer> list = new ArrayList<Number>();
Object obj = list.get(0);  // ✅ Can only read as Object
Integer i = list.get(0);   // ❌ Don't know actual type
```

---

## PECS — Producer Extends, Consumer Super

> **PECS** = **P**roducer **E**xtends, **C**onsumer **S**uper — Joshua Bloch, Effective Java

### The Rule

- If you **READ** from the collection (produce data) → use `extends`
- If you **WRITE** to the collection (consume data) → use `super`
- If you **READ AND WRITE** → use exact type (no wildcard)

```java
// PRODUCER — you read FROM it → extends
public static <T> void copy(
    List<? extends T> source,    // Producer: READ from source
    List<? super T> destination  // Consumer: WRITE to destination
) {
    for (T item : source) {
        destination.add(item);
    }
}

// Real-world: Collections.copy()
List<Integer> source = List.of(1, 2, 3);
List<Number> dest = new ArrayList<>(List.of(0, 0, 0));
Collections.copy(dest, source);  // Works! Integer extends Number
```

### Visual Summary

```
┌──────────────────────────────────────────────┐
│                   PECS                        │
├──────────────────────────────────────────────┤
│ <? extends T>  → READ only (produces T)       │
│                → Can get T out                 │
│                → Can't put T in               │
│                                              │
│ <? super T>    → WRITE only (consumes T)      │
│                → Can put T in                  │
│                → Can only get Object out       │
│                                              │
│ <T>            → READ and WRITE               │
│                → Full access                   │
└──────────────────────────────────────────────┘
```

---

## Type Erasure — How Generics Work at Runtime

### What Is Type Erasure?

Java generics are **compile-time only**. The compiler:
1. Checks type safety at compile time
2. Inserts necessary casts
3. **Erases** all type parameters to their bounds (or `Object`)

```java
// What you write:
public class Box<T> {
    private T item;
    public T get() { return item; }
}

// What the compiler generates (after erasure):
public class Box {
    private Object item;
    public Object get() { return item; }
}

// What you write:
Box<String> box = new Box<>();
String s = box.get();

// What the compiler generates:
Box box = new Box();
String s = (String) box.get();  // Cast inserted by compiler
```

### Bounded Type Erasure

```java
// What you write:
public class NumberBox<T extends Number> {
    private T value;
    public T get() { return value; }
}

// After erasure — T replaced with bound (Number, not Object):
public class NumberBox {
    private Number value;
    public Number get() { return value; }
}
```

### Bridge Methods

```java
// You write:
public class StringBox extends Box<String> {
    @Override
    public String get() { return "hello"; }
}

// After erasure, Box.get() returns Object, but StringBox.get() returns String
// Compiler generates a BRIDGE METHOD:
public class StringBox extends Box {
    public String get() { return "hello"; }
    
    // Bridge method (synthetic)
    public Object get() { return get(); }  // Calls the String version
}
```

---

## Generics Restrictions & Gotchas

### Cannot Do with Generics

```java
// 1. Cannot instantiate type parameter
T obj = new T();                    // ❌ — T is erased to Object at runtime

// 2. Cannot create generic arrays
T[] array = new T[10];             // ❌
List<String>[] arr = new List<String>[5];  // ❌

// 3. Cannot use primitives
List<int> list = new ArrayList<>(); // ❌ — Use List<Integer>

// 4. Cannot use instanceof with parameterized type
obj instanceof List<String>        // ❌ — Type info erased
obj instanceof List<?>             // ✅ — Unbounded OK

// 5. Cannot create static fields of type parameter
class Box<T> {
    static T item;                  // ❌ — T is instance-level
}

// 6. Cannot catch/throw type parameter
class Box<T extends Exception> {
    void method() throws T { }     // ❌
}
```

### The Generic Array Workaround

```java
// Workaround for generic arrays
@SuppressWarnings("unchecked")
T[] array = (T[]) new Object[10];

// Better: use List<T>
List<T> list = new ArrayList<>();
```

### Invariance Problem

```java
// Arrays are COVARIANT (dangerous!)
Object[] arr = new Integer[10];
arr[0] = "hello";  // ✅ Compiles, but 💥 ArrayStoreException at runtime!

// Generics are INVARIANT (safe!)
List<Object> list = new ArrayList<Integer>();  // ❌ COMPILE ERROR
// Use wildcards for flexibility:
List<? extends Object> list = new ArrayList<Integer>();  // ✅
```

---

## Generic Patterns in Real Code

### Type-Safe Heterogeneous Container

```java
public class TypeSafeMap {
    private Map<Class<?>, Object> map = new HashMap<>();
    
    public <T> void put(Class<T> type, T instance) {
        map.put(type, instance);
    }
    
    public <T> T get(Class<T> type) {
        return type.cast(map.get(type));
    }
}

TypeSafeMap container = new TypeSafeMap();
container.put(String.class, "hello");
container.put(Integer.class, 42);
String s = container.get(String.class);  // Type-safe, no cast
```

### Generic Builder Pattern

```java
public abstract class Builder<T, B extends Builder<T, B>> {
    protected abstract B self();
    public abstract T build();
}

public class UserBuilder extends Builder<User, UserBuilder> {
    private String name;
    
    public UserBuilder name(String name) { this.name = name; return self(); }
    
    @Override protected UserBuilder self() { return this; }
    @Override public User build() { return new User(name); }
}
```

---

## Interview Talking Points & Scripts

### "What is type erasure and why does Java do it?"

> *"Type erasure means generic type information is removed by the compiler after type checking. At runtime, List<String> and List<Integer> are just List. Java does this for backward compatibility — generics were added in Java 5, and existing non-generic code needed to keep working. The downside is you can't do things like `new T()` or `instanceof List<String>` at runtime because the type info doesn't exist."*

### "Explain PECS with an example"

> *"PECS stands for Producer Extends, Consumer Super. If a method reads from a generic collection — producing values for us — use `? extends T` so it accepts any subtype. If a method writes to a collection — consuming our values — use `? super T` so it accepts any supertype. For example, Collections.copy() takes `List<? super T> dest, List<? extends T> src` — it reads from source (producer/extends) and writes to destination (consumer/super)."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "Generics exist at runtime" | Type erasure removes them — `List<String>` becomes `List` |
| "You can create `new T()`" | Not possible due to type erasure |
| "`List<Object>` accepts `List<String>`" | Generics are invariant — use `List<? extends Object>` |
| "Arrays and generics work the same" | Arrays are covariant + reified; generics are invariant + erased |
| "Wildcards and type params are the same" | `?` can't be used to declare fields/returns; `T` can |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│              GENERICS CHEAT SHEET                           │
├────────────────────────────────────────────────────────────┤
│ <T>               → Type parameter (read + write)           │
│ <? extends T>     → Upper bound (read only — PRODUCER)      │
│ <? super T>       → Lower bound (write only — CONSUMER)     │
│ <?>               → Unbounded (read as Object)              │
│                                                            │
│ PECS: Producer Extends, Consumer Super                      │
│                                                            │
│ Type Erasure:                                               │
│ • Compile-time only — erased to bounds at runtime           │
│ • <T> → Object, <T extends Number> → Number                │
│ • Bridge methods generated for overrides                    │
│                                                            │
│ CAN'T DO:                                                  │
│ • new T(), T[], instanceof List<String>                    │
│ • Primitives as type args (use wrappers)                   │
│ • Static fields of type parameter T                        │
│                                                            │
│ Arrays vs Generics:                                         │
│ • Arrays: covariant, reified (runtime type)                │
│ • Generics: invariant, erased (compile-time only)          │
│ • → Prefer List<T> over T[]                                │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Multithreading & Concurrency →](./06-multithreading-and-concurrency.md)
