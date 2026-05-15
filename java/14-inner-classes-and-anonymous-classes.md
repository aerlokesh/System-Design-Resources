# 🎯 Topic 14: Inner Classes & Anonymous Classes

> **Java Interview — Deep Dive**
> Covering all types of inner classes, their use cases, memory implications, and interview gotchas.

---

## Types of Inner Classes

### 1. Member Inner Class (Non-Static)

```java
class Outer {
    private int x = 10;
    
    class Inner {
        void display() {
            System.out.println(x);  // ✅ Can access Outer's private members
            System.out.println(Outer.this.x);  // Explicit outer reference
        }
    }
}

Outer outer = new Outer();
Outer.Inner inner = outer.new Inner();  // Needs outer instance
```

**Key**: Holds implicit reference to outer instance → potential **memory leak**.

### 2. Static Nested Class

```java
class Outer {
    private static int y = 20;
    
    static class Nested {
        void display() {
            System.out.println(y);  // ✅ Can access static members only
            // System.out.println(x);  // ❌ Cannot access non-static
        }
    }
}

Outer.Nested nested = new Outer.Nested();  // No outer instance needed!
```

**Key**: No reference to outer — preferred for helper classes. Used in Builder pattern, Map.Entry.

### 3. Local Inner Class (Inside Method)

```java
void process() {
    final int localVar = 42;  // Must be effectively final
    
    class LocalHelper {
        void help() {
            System.out.println(localVar);  // ✅ Accesses effectively final local
        }
    }
    new LocalHelper().help();
}
```

### 4. Anonymous Inner Class

```java
// Before Java 8 (replaced by lambdas for single-method interfaces)
Runnable r = new Runnable() {
    @Override
    public void run() {
        System.out.println("Anonymous");
    }
};

// Java 8+ — Lambda (preferred for functional interfaces)
Runnable r = () -> System.out.println("Lambda");

// Still needed for: abstract classes, multi-method interfaces
```

---

## Comparison Table

| Type | Outer Reference | Static Access | Non-Static Access | Use Case |
|------|----------------|---------------|-------------------|----------|
| Member Inner | ✅ Yes (implicit) | ✅ | ✅ | Closely coupled helper |
| Static Nested | ❌ No | ✅ | ❌ | Builder, Entry, independent helper |
| Local | N/A | Effectively final locals | N/A | Encapsulated one-off logic |
| Anonymous | ✅ (if non-static context) | Effectively final | N/A | Quick implementations (pre-lambda) |

---

## Memory Leak Warning

```java
// ❌ DANGER — Inner class keeps Outer alive
class Activity {
    byte[] data = new byte[10_000_000];  // 10MB
    
    class Listener {  // Holds reference to Activity → 10MB can't be GC'd
        void onEvent() { }
    }
}

// ✅ FIX — Use static nested class
class Activity {
    byte[] data = new byte[10_000_000];
    
    static class Listener {  // No reference to Activity → safe
        void onEvent() { }
    }
}
```

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│ Member Inner: has outer reference → memory leak risk        │
│ Static Nested: no outer reference → preferred (Builder)     │
│ Local: inside method, effectively final vars only           │
│ Anonymous: quick impl → replaced by lambdas for FI          │
│                                                            │
│ Rule: Prefer static nested classes to avoid memory leaks    │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Class Loading & Initialization →](./15-class-loading-and-initialization.md)
