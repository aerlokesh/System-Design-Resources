# 🎯 Topic 13: Autoboxing, Wrapper Classes & Primitives

> **Java Interview — Deep Dive**
> Covering primitives vs wrappers, autoboxing/unboxing traps, caching, and interview gotchas.

---

## Primitives vs Wrapper Classes

| Primitive | Wrapper | Size | Default |
|-----------|---------|------|---------|
| `byte` | `Byte` | 1 byte | 0 |
| `short` | `Short` | 2 bytes | 0 |
| `int` | `Integer` | 4 bytes | 0 |
| `long` | `Long` | 8 bytes | 0L |
| `float` | `Float` | 4 bytes | 0.0f |
| `double` | `Double` | 8 bytes | 0.0d |
| `char` | `Character` | 2 bytes | '\u0000' |
| `boolean` | `Boolean` | ~1 byte | false |

### Key Differences

| Feature | Primitive | Wrapper |
|---------|-----------|---------|
| Null | ❌ Cannot be null | ✅ Can be null |
| Generics | ❌ `List<int>` impossible | ✅ `List<Integer>` |
| Performance | **Faster** (stack) | Slower (heap object) |
| Default value | 0, false, etc. | `null` |
| Collections | ❌ Not allowed | ✅ Required |
| `==` behavior | Value comparison | **Reference comparison** ⚠️ |

---

## Autoboxing & Unboxing

```java
// Autoboxing — primitive → wrapper (compiler does: Integer.valueOf(5))
Integer a = 5;

// Unboxing — wrapper → primitive (compiler does: a.intValue())
int b = a;

// NullPointerException trap!
Integer x = null;
int y = x;  // 💥 NullPointerException on unboxing!
```

---

## Integer Cache — The Famous Trap

```java
Integer a = 127;
Integer b = 127;
System.out.println(a == b);   // true ✅ — cached range [-128, 127]

Integer c = 128;
Integer d = 128;
System.out.println(c == d);   // false ❌ — outside cache, different objects!

System.out.println(c.equals(d));  // true ✅ — ALWAYS use equals() for wrappers
```

### Cache Ranges

| Type | Cached Range |
|------|-------------|
| `Integer` | **-128 to 127** (extendable with `-XX:AutoBoxCacheMax`) |
| `Byte` | -128 to 127 (all values) |
| `Short` | -128 to 127 |
| `Long` | -128 to 127 |
| `Character` | 0 to 127 |
| `Boolean` | `TRUE` and `FALSE` (both cached) |

> **Rule**: ALWAYS use `.equals()` for wrapper comparisons. Never use `==`.

---

## Interview Tricky Questions

```java
// Q1: What's the output?
Integer a = new Integer(127);
Integer b = new Integer(127);
System.out.println(a == b);      // false — new always creates new object
System.out.println(a.equals(b)); // true

// Q2: Performance trap
Long sum = 0L;
for (long i = 0; i < 1_000_000; i++) {
    sum += i;  // Creates ~1M Long objects! Use primitive long instead
}

// Q3: Ternary + autoboxing trap
Integer a = null;
int b = (a != null) ? a : 0;     // ✅ Safe
int c = (true) ? a : 0;          // 💥 NPE! Ternary unboxes 'a' because other branch is int
```

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│ Primitives: fast, stack, can't be null, no generics         │
│ Wrappers: objects, heap, nullable, required for collections │
│                                                            │
│ Autoboxing: int → Integer (valueOf), Integer → int (intValue)│
│ Cache: Integer -128 to 127 → == works; outside → USE equals()│
│ Trap: null unboxing → NullPointerException                  │
│ Trap: Ternary with mixed types forces unboxing              │
│ Performance: Avoid wrappers in loops — use primitives       │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Inner Classes & Anonymous Classes →](./14-inner-classes-and-anonymous-classes.md)
