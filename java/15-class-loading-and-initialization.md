# 🎯 Topic 15: Class Loading & Initialization

> **Java Interview — Deep Dive**
> Covering ClassLoader hierarchy, class loading process, static initialization, and interview questions.

---

## Class Loading Process

```
.class file → Loading → Linking (Verify → Prepare → Resolve) → Initialization
```

### 1. Loading
- ClassLoader reads `.class` bytecode and creates `Class<?>` object in Method Area

### 2. Linking
- **Verify**: Bytecode is valid and safe
- **Prepare**: Allocate memory for static variables, set to default values (0, null, false)
- **Resolve**: Replace symbolic references with direct references

### 3. Initialization
- Execute static initializers and static blocks **in order of appearance**

---

## ClassLoader Hierarchy

```
Bootstrap ClassLoader (C/C++)        ← java.lang, java.util (rt.jar)
        │
Extension/Platform ClassLoader       ← javax.*, security, extensions
        │
Application/System ClassLoader       ← classpath (your code)
        │
Custom ClassLoaders                  ← frameworks (Tomcat, OSGi)
```

### Delegation Model (Parent-First)

```java
// When loading a class:
// 1. Check if already loaded
// 2. Delegate to PARENT classloader first
// 3. If parent can't find → load it yourself
// This prevents loading java.lang.String from malicious code!
```

---

## Static Initialization Order

```java
class Parent {
    static { System.out.println("1. Parent static block"); }
    { System.out.println("3. Parent instance block"); }
    Parent() { System.out.println("4. Parent constructor"); }
}

class Child extends Parent {
    static { System.out.println("2. Child static block"); }
    { System.out.println("5. Child instance block"); }
    Child() { System.out.println("6. Child constructor"); }
}

new Child();
// Output:
// 1. Parent static block      (class loading — once)
// 2. Child static block       (class loading — once)
// 3. Parent instance block    (object creation)
// 4. Parent constructor       (object creation)
// 5. Child instance block     (object creation)
// 6. Child constructor        (object creation)
```

### Order Rules (Interview Critical)

1. **Static blocks/fields** → Parent first, then Child (only once, on class load)
2. **Instance blocks** → Parent first, then Child (every `new`)
3. **Constructors** → Parent first, then Child
4. Static members initialized **in order of appearance** in the file

---

## Interview Tricky Question

```java
class Singleton {
    private static Singleton instance = new Singleton();
    private static int count1;
    private static int count2 = 0;
    
    private Singleton() {
        count1++;
        count2++;
    }
    
    public static Singleton getInstance() { return instance; }
}

// What are count1 and count2?
Singleton s = Singleton.getInstance();
System.out.println(s.count1);  // 1
System.out.println(s.count2);  // 0 ← SURPRISE!

// Why? Static init order:
// 1. instance = new Singleton() → count1=1, count2=1
// 2. count1 → no initializer, stays 1
// 3. count2 = 0 → RESETS to 0!
```

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│ Loading → Linking (Verify/Prepare/Resolve) → Initialization │
│ ClassLoaders: Bootstrap → Extension → Application (parent-first)│
│ Static init: Parent static → Child static → Parent instance → etc│
│ Static blocks run ONCE on class load, in order of appearance│
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Final, Static & Keywords Deep Dive →](./16-final-static-and-keywords.md)
