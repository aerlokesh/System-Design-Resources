# 🎯 Topic 12: Enums, Annotations & Reflection

> **Java Interview — Deep Dive**
> Covering enum internals, custom annotations, reflection API, and interview-ready concepts.

---

## Enums — Type-Safe Constants

### Basic Enum

```java
public enum Status {
    PENDING, ACTIVE, INACTIVE, DELETED;
}

Status s = Status.ACTIVE;
s.name();       // "ACTIVE"
s.ordinal();    // 1 (position)
Status.valueOf("ACTIVE");  // Status.ACTIVE
Status.values();           // [PENDING, ACTIVE, INACTIVE, DELETED]
```

### Enum with Fields, Constructor & Methods

```java
public enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    VENUS(4.869e+24, 6.0518e6),
    EARTH(5.976e+24, 6.37814e6);
    
    private final double mass;
    private final double radius;
    
    Planet(double mass, double radius) {  // Constructor is ALWAYS private
        this.mass = mass;
        this.radius = radius;
    }
    
    public double surfaceGravity() {
        return 6.67300E-11 * mass / (radius * radius);
    }
}
```

### Enum with Abstract Methods (Strategy per Constant)

```java
public enum Operation {
    ADD {
        @Override public double apply(double a, double b) { return a + b; }
    },
    MULTIPLY {
        @Override public double apply(double a, double b) { return a * b; }
    };
    
    public abstract double apply(double a, double b);
}

double result = Operation.ADD.apply(3, 4);  // 7.0
```

### Enum in Switch

```java
switch (status) {
    case ACTIVE -> handleActive();
    case DELETED -> handleDeleted();
    default -> handleOther();
}
```

### Key Enum Facts (Interview)

| Fact | Detail |
|------|--------|
| Extends | `java.lang.Enum` implicitly (can't extend anything else) |
| Implements | CAN implement interfaces |
| Constructor | Always **private** (compile error if public) |
| Singleton | Each constant is a singleton — best Singleton pattern |
| Serialization | Safe — no duplicate instances on deserialization |
| Comparable | Implements `Comparable` (by ordinal) |
| `==` safe | Can use `==` instead of `equals()` for comparison |
| EnumSet/EnumMap | Highly optimized collections for enums (bit-vector backed) |

---

## Annotations

### Built-in Annotations

```java
@Override           // Compile error if method doesn't actually override
@Deprecated         // Marks as deprecated
@SuppressWarnings   // Suppress compiler warnings
@FunctionalInterface // Enforces single abstract method
@SafeVarargs        // Suppress unchecked varargs warning
```

### Custom Annotations

```java
@Retention(RetentionPolicy.RUNTIME)  // Available at runtime via reflection
@Target(ElementType.METHOD)          // Can only be on methods
public @interface CacheResult {
    int ttlSeconds() default 300;
    String key() default "";
}

// Usage
@CacheResult(ttlSeconds = 60, key = "user")
public User getUser(String id) { /* ... */ }
```

### Retention Policies

| Policy | Available | Use Case |
|--------|----------|----------|
| `SOURCE` | Compile-time only (discarded) | `@Override`, `@SuppressWarnings` |
| `CLASS` | In .class file, not at runtime | Bytecode tools |
| `RUNTIME` | At runtime via reflection | Frameworks (Spring, JPA) |

---

## Reflection

### What Is Reflection?

Inspecting and modifying classes, methods, fields **at runtime**.

```java
Class<?> clazz = User.class;
// Or: Class.forName("com.example.User")
// Or: user.getClass()

// Inspect
clazz.getName();                    // "com.example.User"
clazz.getDeclaredFields();          // All fields (including private)
clazz.getDeclaredMethods();         // All methods
clazz.getConstructors();            // Public constructors

// Create instance
Object obj = clazz.getDeclaredConstructor().newInstance();

// Access private field
Field field = clazz.getDeclaredField("name");
field.setAccessible(true);          // Bypass private!
field.set(obj, "Alice");
String name = (String) field.get(obj);

// Invoke method
Method method = clazz.getDeclaredMethod("getName");
method.setAccessible(true);
String result = (String) method.invoke(obj);
```

### When Reflection Is Used

| Framework | How |
|-----------|-----|
| **Spring** | Bean creation, dependency injection, `@Autowired` |
| **JPA/Hibernate** | Entity mapping, field access |
| **JUnit** | Test method discovery, `@Test` processing |
| **Jackson** | JSON serialization/deserialization |
| **Lombok** | Annotation processing at compile time |

### Reflection Downsides

- **Performance**: Slower than direct calls (no JIT optimization)
- **Security**: Bypasses access control (`setAccessible(true)`)
- **Type safety**: No compile-time checking
- **Maintenance**: Breaks if field/method names change

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│ Enums: type-safe constants, can have fields/methods         │
│ • Constructor always private, extends Enum implicitly       │
│ • Best Singleton pattern, == safe, EnumSet/EnumMap optimized│
│                                                            │
│ Annotations: metadata on code elements                      │
│ • RUNTIME retention → accessible via reflection             │
│ • Frameworks (Spring, JPA, JUnit) rely on annotations       │
│                                                            │
│ Reflection: runtime class/method/field inspection           │
│ • Powerful but slow, breaks encapsulation                   │
│ • Used by all major frameworks for DI, ORM, testing        │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Autoboxing, Wrapper Classes & Primitives →](./13-autoboxing-wrapper-classes-and-primitives.md)
