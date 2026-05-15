# 🎯 Topic 17: Equals, HashCode & Object Contract

> **Java Interview — Deep Dive**
> Covering the equals/hashCode contract, proper implementation, Object class methods, and interview traps.

---

## The Object Class — Root of All Java Classes

Every Java class inherits from `Object`. Key methods:

| Method | Purpose | Default Behavior |
|--------|---------|-----------------|
| `equals(Object)` | Value equality | Reference equality (`==`) |
| `hashCode()` | Hash for collections | Memory address based |
| `toString()` | String representation | `ClassName@hexHashCode` |
| `clone()` | Copy object | Shallow copy (protected) |
| `getClass()` | Runtime class info | Returns `Class<?>` |
| `finalize()` | Cleanup before GC | **Deprecated** (Java 9+) |
| `wait()`/`notify()` | Thread communication | Must be in synchronized block |

---

## equals() Contract

The `equals()` method must satisfy:

```java
// 1. Reflexive: x.equals(x) → true
// 2. Symmetric: x.equals(y) ↔ y.equals(x)
// 3. Transitive: x.equals(y) && y.equals(z) → x.equals(z)
// 4. Consistent: Multiple calls return same result (if objects unchanged)
// 5. Null: x.equals(null) → false (never throw NPE)
```

### Proper Implementation

```java
public class Employee {
    private int id;
    private String name;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                           // 1. Same reference
        if (o == null || getClass() != o.getClass()) return false;  // 2. Null + type check
        Employee that = (Employee) o;                         // 3. Cast
        return id == that.id && Objects.equals(name, that.name);    // 4. Field comparison
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name);  // MUST use same fields as equals()
    }
}
```

### `getClass()` vs `instanceof`

```java
// getClass() — strict type check (recommended)
if (o == null || getClass() != o.getClass()) return false;
// Employee.equals(Manager) → false (different classes)

// instanceof — allows subclass matching (can break symmetry!)
if (!(o instanceof Employee)) return false;
// Employee.equals(Manager) → true, but Manager.equals(Employee) → false!
// Violates symmetry if Manager adds fields to equals()!
```

> **Rule**: Use `getClass()` for equals() unless you have a specific reason for `instanceof` (like abstract base classes).

---

## hashCode() Contract

```java
// 1. If a.equals(b) → a.hashCode() == b.hashCode()     (MANDATORY)
// 2. If !a.equals(b) → hashCodes MAY differ             (desirable for performance)
// 3. hashCode() must be consistent across calls          (if object unchanged)

// ❌ BROKEN — Override equals() without hashCode()
// Two "equal" objects → different hash codes → different HashMap buckets → HashMap BROKEN
```

### Recipe (Effective Java)

```java
@Override
public int hashCode() {
    // Use same fields as equals()
    return Objects.hash(id, name);  // Simplest
    
    // Or manual (better performance):
    int result = Integer.hashCode(id);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
}
```

**Why 31?** — It's an odd prime. `31 * i` can be optimized by JVM to `(i << 5) - i` (bit shift).

---

## toString()

```java
// Default: com.example.Employee@1a2b3c
// Override for meaningful output:
@Override
public String toString() {
    return "Employee{id=" + id + ", name='" + name + "'}";
}

// Java 16+ Records auto-generate toString(), equals(), hashCode()
record Employee(int id, String name) {}
```

---

## Interview Tricky Questions

```java
// Q1: What happens if you override equals() but NOT hashCode()?
Employee e1 = new Employee(1, "Alice");
Employee e2 = new Employee(1, "Alice");
e1.equals(e2);  // true ← equals works

Set<Employee> set = new HashSet<>();
set.add(e1);
set.contains(e2);  // false! ← Different hashCode → different bucket → NOT FOUND

// Q2: Can two unequal objects have the same hashCode?
// YES — this is called a hash collision. It's allowed and normal.

// Q3: Is hashCode unique per object?
// NO — hash codes are int (2^32 values) but infinite possible objects.
```

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│ equals() Contract: reflexive, symmetric, transitive, consistent, null-safe│
│ hashCode() Contract: equal objects → equal hash codes       │
│ RULE: Override equals() → MUST override hashCode()          │
│ Use same fields in both. Use getClass() not instanceof.    │
│ Records (Java 16+) auto-generate both + toString()          │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Java Memory Management & Performance →](./18-java-memory-management-and-performance.md)
