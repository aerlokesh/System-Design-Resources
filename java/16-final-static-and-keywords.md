# 🎯 Topic 16: Final, Static & Important Keywords

> **Java Interview — Deep Dive**
> Covering final, static, this, super, volatile, transient, synchronized, and their interview nuances.

---

## `final` Keyword

| Context | Meaning |
|---------|---------|
| `final` variable | Value cannot be changed (constant) |
| `final` method | Cannot be overridden by subclasses |
| `final` class | Cannot be extended (e.g., `String`, `Integer`) |
| `final` parameter | Cannot be reassigned inside method |

```java
final int MAX = 100;        // Constant — can't reassign
final List<String> list = new ArrayList<>();
list.add("OK");             // ✅ Can modify CONTENTS
// list = new ArrayList<>(); // ❌ Can't reassign REFERENCE

// Blank final — assigned once in constructor
class Config {
    final String env;
    Config(String env) { this.env = env; }  // Must assign in every constructor
}
```

> **Interview Key**: `final` on a reference means the reference can't change, but the object it points to CAN be modified (unless the object itself is immutable).

---

## `static` Keyword

| Context | Meaning |
|---------|---------|
| `static` field | Shared across ALL instances (class-level) |
| `static` method | Called on class, not instance. Can't access `this` or instance members |
| `static` block | Runs once when class is loaded |
| `static` import | Import static members directly |
| `static` nested class | No reference to enclosing instance |

```java
class Counter {
    static int count = 0;            // Shared by all instances
    
    static { count = 10; }          // Static initializer
    
    static void reset() { count = 0; }  // Static method
}

// Static method CANNOT:
// - Use 'this' or 'super'
// - Access instance variables/methods directly
// - Be overridden (they are HIDDEN, not overridden)
```

### Static Method Hiding (NOT Overriding)

```java
class Parent {
    static void greet() { System.out.println("Parent"); }
}
class Child extends Parent {
    static void greet() { System.out.println("Child"); }  // HIDING, not overriding
}

Parent p = new Child();
p.greet();  // "Parent" — resolved by REFERENCE type, not object type!
```

---

## `this` vs `super`

```java
class Parent {
    int x = 10;
    Parent(int x) { this.x = x; }
    void show() { System.out.println("Parent"); }
}

class Child extends Parent {
    int x = 20;
    
    Child() {
        super(100);              // Call parent constructor (must be FIRST line)
        // this(42);             // ❌ Can't use both this() and super()
    }
    
    void display() {
        System.out.println(this.x);   // 20 — Child's x
        System.out.println(super.x);  // 100 — Parent's x
        super.show();                 // Call Parent's method
    }
}
```

---

## Other Important Keywords

### `volatile`
- Guarantees visibility across threads (reads from main memory)
- Does NOT guarantee atomicity
- Use for: flags, status variables

### `transient`
- Field excluded from serialization
- Value becomes default (null/0) after deserialization

### `synchronized`
- Method or block level mutual exclusion
- Instance method → locks `this`; static method → locks `Class` object

### `strictfp`
- Ensures floating-point operations give same results on all platforms
- Rarely used (default since Java 17)

### `native`
- Method implemented in C/C++ via JNI
- No body in Java: `native void method();`

### `instanceof`
```java
if (obj instanceof String s) {  // Java 16+ pattern matching
    System.out.println(s.length());  // 's' already cast
}
```

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│ final: variable (constant) | method (no override) | class (no extend)│
│   → final reference ≠ immutable object                     │
│                                                            │
│ static: class-level, no 'this', loaded once                │
│   → Static methods are HIDDEN not overridden               │
│                                                            │
│ this: current instance | super: parent reference            │
│   → this() and super() must be first line (can't use both) │
│                                                            │
│ volatile: visibility | transient: skip serialization        │
│ synchronized: mutual exclusion | native: JNI               │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Equals, HashCode & Object Contract →](./17-equals-hashcode-and-object-contract.md)
