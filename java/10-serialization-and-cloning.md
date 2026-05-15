# 🎯 Topic 10: Serialization, Cloning & Immutability

> **Java Interview — Deep Dive**
> Covering serialization mechanics, deep vs shallow copy, creating immutable classes, and interview traps.

---

## Serialization

### What Is Serialization?

Converting an object to a **byte stream** for storage/transmission, and back (deserialization).

```java
// Mark class as serializable
public class User implements Serializable {
    private static final long serialVersionUID = 1L;  // Version control
    private String name;
    private transient String password;  // NOT serialized
    private static int count;           // NOT serialized (static = class-level)
}

// Serialize
try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("user.dat"))) {
    oos.writeObject(user);
}

// Deserialize
try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("user.dat"))) {
    User user = (User) ois.readObject();
}
```

### Key Points (Interview Critical)

| Feature | Detail |
|---------|--------|
| `serialVersionUID` | Version control — mismatch causes `InvalidClassException` |
| `transient` | Field excluded from serialization |
| `static` fields | NOT serialized (belong to class, not object) |
| Constructor | **NOT called** during deserialization |
| `Externalizable` | Full control via `writeExternal()`/`readExternal()` |
| Singleton break | Deserialization creates NEW instance — fix with `readResolve()` |

### Protecting Singleton

```java
public class Singleton implements Serializable {
    private static final Singleton INSTANCE = new Singleton();
    
    // Prevents deserialization from creating new instance
    private Object readResolve() {
        return INSTANCE;
    }
}
```

---

## Cloning — Shallow vs Deep Copy

### Shallow Copy (Default)

```java
class Team implements Cloneable {
    String name;
    List<String> members;  // Reference copied, not content!
    
    @Override
    public Team clone() throws CloneNotSupportedException {
        return (Team) super.clone();  // Shallow — members list is SHARED
    }
}

Team t1 = new Team();
Team t2 = t1.clone();
t2.members.add("New");  // ⚠️ Modifies BOTH t1 and t2!
```

### Deep Copy

```java
@Override
public Team clone() throws CloneNotSupportedException {
    Team copy = (Team) super.clone();
    copy.members = new ArrayList<>(this.members);  // Deep copy
    return copy;
}
```

### Copy Constructor (Preferred over Cloneable)

```java
public class Team {
    private String name;
    private List<String> members;
    
    // Copy constructor — clean, explicit, no CloneNotSupportedException
    public Team(Team other) {
        this.name = other.name;
        this.members = new ArrayList<>(other.members);
    }
}
```

> **Interview Key**: Prefer copy constructors over `Cloneable`. The `clone()` contract is broken and fragile (Effective Java Item 13).

---

## Creating Immutable Classes

### Rules for Immutability

```java
public final class Money {                      // 1. final class
    private final BigDecimal amount;             // 2. final fields
    private final Currency currency;
    private final List<String> tags;
    
    public Money(BigDecimal amount, Currency currency, List<String> tags) {
        this.amount = amount;                    // 3. Set in constructor
        this.currency = currency;
        this.tags = List.copyOf(tags);           // 4. Defensive copy
    }
    
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public List<String> getTags() { return tags; }  // Already immutable (List.copyOf)
    
    // 5. No setters!
}
```

### Immutability Checklist

1. ✅ Declare class `final` (prevent subclassing)
2. ✅ All fields `private final`
3. ✅ No setter methods
4. ✅ Defensive copy mutable objects in constructor
5. ✅ Return immutable copies from getters
6. ✅ Don't expose mutable internals

### Why Immutability Matters

- **Thread-safe** without synchronization
- **Safe as HashMap keys** (hashCode never changes)
- **No defensive copies** needed when sharing
- **Simple** — no invalid states possible

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│ Serialization: Serializable + serialVersionUID              │
│ • transient = not serialized, static = not serialized       │
│ • Constructor NOT called on deserialization                  │
│ • Singleton: use readResolve() to prevent new instances     │
│                                                            │
│ Cloning: Shallow (shared refs) vs Deep (independent copy)   │
│ • Prefer copy constructors over Cloneable                   │
│                                                            │
│ Immutability: final class + final fields + no setters       │
│ • Defensive copy mutable objects in/out                     │
│ • Thread-safe, safe HashMap keys, simple to reason about    │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Java I/O & NIO →](./11-java-io-and-nio.md)
