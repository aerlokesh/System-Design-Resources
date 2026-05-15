# 🎯 Topic 3: Strings, Immutability & String Pool

> **Java Interview — Deep Dive**
> A comprehensive guide covering String internals, immutability, the String Pool, StringBuilder vs StringBuffer, and how to articulate these with depth in a Java interview.

---

## Table of Contents

1. [String Immutability — Why & How](#string-immutability--why--how)
2. [String Pool (Intern Pool)](#string-pool-intern-pool)
3. [String Creation — Heap vs Pool](#string-creation--heap-vs-pool)
4. [String Comparison — == vs equals()](#string-comparison---vs-equals)
5. [StringBuilder vs StringBuffer](#stringbuilder-vs-stringbuffer)
6. [String Concatenation Internals](#string-concatenation-internals)
7. [Important String Methods](#important-string-methods)
8. [String in Switch, Records & Text Blocks](#string-in-switch-records--text-blocks)
9. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
10. [Common Interview Mistakes](#common-interview-mistakes)
11. [Tricky Output Questions](#tricky-output-questions)
12. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## String Immutability — Why & How

### What Makes String Immutable?

```java
public final class String {           // final → can't be subclassed
    private final byte[] value;       // final → reference can't change
    private final byte coder;         // final → encoding can't change
    private int hash;                 // Cached hashCode (computed once)
}
```

**Three layers of immutability**:
1. `final class` — cannot be extended
2. `private final byte[] value` — array reference can't be reassigned
3. **No mutator methods** — no method modifies the internal array

### Why Is String Immutable?

| Reason | Explanation |
|--------|-------------|
| **String Pool** | Immutability allows safe sharing via the pool |
| **Thread Safety** | Immutable objects are inherently thread-safe |
| **Security** | Class loading, network connections use Strings — mutation would be a vulnerability |
| **Caching hashCode** | Computed once, cached forever — crucial for HashMap performance |
| **ClassLoader Safety** | Class names are Strings; mutability would break class loading |

### The `hash` Caching Trick

```java
// String.hashCode() — computed once, cached
public int hashCode() {
    int h = hash;  // Read cached value
    if (h == 0 && !hashIsZero) {
        h = isLatin1() ? StringLatin1.hashCode(value) : StringUTF16.hashCode(value);
        if (h == 0) {
            hashIsZero = true;
        } else {
            hash = h;  // Cache for future calls
        }
    }
    return h;
}
```

> **Interview Insight**: This is why String is an excellent HashMap key — hashCode() is O(1) after first call.

---

## String Pool (Intern Pool)

### How It Works

The **String Pool** (also called the String Intern Pool) is a special area in the **heap** (moved from PermGen to Heap in Java 7) that stores unique string literals.

```
┌─────────────── HEAP ──────────────────────────┐
│                                                │
│  ┌──────── String Pool ────────┐               │
│  │ "hello"  "world"  "java"    │               │
│  │ (shared references)         │               │
│  └─────────────────────────────┘               │
│                                                │
│  Regular Objects:                               │
│  new String("hello")  ← separate object        │
│  new String("hello")  ← another separate obj   │
│                                                │
└────────────────────────────────────────────────┘
```

### `intern()` Method

```java
String s1 = new String("hello");  // Creates object in heap (NOT pool)
String s2 = s1.intern();          // Returns reference from pool
String s3 = "hello";              // Literal → from pool

System.out.println(s2 == s3);     // true — both point to pool
System.out.println(s1 == s3);     // false — s1 is in heap, s3 in pool
```

---

## String Creation — Heap vs Pool

### Scenario Analysis (Interview Favorite)

```java
// Scenario 1: String literal
String s1 = "hello";
// Creates: 1 object in String Pool (if not already there)
// Total: 1 object

// Scenario 2: new String()
String s2 = new String("hello");
// Creates: 1 object in String Pool ("hello" literal) + 1 object in Heap (new String)
// Total: 2 objects (if "hello" not already in pool)

// Scenario 3: Concatenation of literals (compile-time constant)
String s3 = "hel" + "lo";
// Compiler optimizes to "hello" → 1 object in pool
// s3 == s1 → true!

// Scenario 4: Concatenation with variable (runtime)
String part = "hel";
String s4 = part + "lo";
// Runtime concatenation → new object in Heap
// s4 == s1 → false!

// Scenario 5: final variable concatenation
final String part2 = "hel";
String s5 = part2 + "lo";
// Compile-time constant (final) → optimized to "hello"
// s5 == s1 → true!
```

### How Many Objects Created?

```java
String s = new String("abc");
// Answer: UP TO 2 objects
// 1. "abc" in String Pool (if not already there)
// 2. new String object in Heap

String s = "abc";
// Answer: UP TO 1 object
// 1. "abc" in String Pool (if not already there)
// 0 if already in pool
```

---

## String Comparison — == vs equals()

```java
String s1 = "hello";
String s2 = "hello";
String s3 = new String("hello");
String s4 = new String("hello");

s1 == s2        // true  — both from pool (same reference)
s1 == s3        // false — pool vs heap (different references)
s3 == s4        // false — two different heap objects
s1.equals(s2)   // true  — same content
s1.equals(s3)   // true  — same content
s3.equals(s4)   // true  — same content

// After intern()
s3.intern() == s1  // true — intern() returns pool reference
```

### The Rule

| Operator | Checks | Use For |
|----------|--------|---------|
| `==` | **Reference equality** (same memory address) | Primitives, enums, null checks |
| `equals()` | **Value equality** (content comparison) | **Always use for Strings** |

---

## StringBuilder vs StringBuffer

```java
// StringBuilder — NOT thread-safe, FASTER
StringBuilder sb = new StringBuilder();
sb.append("Hello").append(" ").append("World");

// StringBuffer — Thread-safe (synchronized), SLOWER
StringBuffer sbuf = new StringBuffer();
sbuf.append("Hello").append(" ").append("World");
```

### Complete Comparison

| Feature | String | StringBuilder | StringBuffer |
|---------|--------|---------------|--------------|
| Mutable | ❌ | ✅ | ✅ |
| Thread-safe | ✅ (immutable) | ❌ | ✅ (synchronized) |
| Performance | Slow (new object each time) | **Fastest** | Slower than SB |
| Use case | Few modifications | **Single-threaded concat** | Multi-threaded concat |
| Initial capacity | N/A | **16 chars** | 16 chars |

### When to Use What

```java
// ✅ String — When value won't change
String name = "John";

// ✅ StringBuilder — Loop concatenation, single-threaded
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    sb.append(i).append(", ");
}

// ✅ StringBuffer — Multi-threaded scenarios (rare)
// Almost never needed — prefer StringBuilder + external sync
```

### Internal Resizing

```java
// Default capacity: 16
StringBuilder sb = new StringBuilder();    // capacity = 16
StringBuilder sb = new StringBuilder(100); // capacity = 100

// Resizing: newCapacity = (oldCapacity << 1) + 2 → 2x + 2
// 16 → 34 → 70 → 142 → ...
```

---

## String Concatenation Internals

### Before Java 9

```java
String result = "Hello" + name + "!";
// Compiled to:
String result = new StringBuilder().append("Hello").append(name).append("!").toString();
```

### Java 9+ (Indify String Concatenation)

```java
String result = "Hello" + name + "!";
// Uses invokedynamic → StringConcatFactory
// JVM chooses optimal strategy at runtime
// Strategies: BC_SB, BC_SB_SIZED, MH_SB_SIZED, MH_INLINE_SIZED_EXACT
```

### Loop Concatenation Anti-Pattern

```java
// ❌ BAD — Creates new String/StringBuilder each iteration
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i;  // O(n²) total — new String object each time!
}

// ✅ GOOD — Single StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);  // O(n) total
}
String result = sb.toString();

// ✅ GOOD — Java 8+ Streams
String result = IntStream.range(0, 10000)
    .mapToObj(String::valueOf)
    .collect(Collectors.joining());
```

---

## Important String Methods

```java
String s = "Hello, World!";

// Length & Characters
s.length()                    // 13
s.charAt(0)                   // 'H'
s.toCharArray()               // char[]

// Searching
s.indexOf("World")            // 7
s.lastIndexOf('l')            // 10
s.contains("World")           // true
s.startsWith("Hello")         // true
s.endsWith("!")               // true

// Extraction
s.substring(7)                // "World!"
s.substring(7, 12)            // "World"

// Modification (returns NEW string)
s.toLowerCase()               // "hello, world!"
s.toUpperCase()               // "HELLO, WORLD!"
s.trim()                      // Removes leading/trailing spaces
s.strip()                     // Java 11+ — Unicode-aware trim
s.replace("World", "Java")   // "Hello, Java!"
s.replaceAll("[aeiou]", "*")  // Regex replacement

// Splitting & Joining
"a,b,c".split(",")            // ["a", "b", "c"]
String.join("-", "a", "b")    // "a-b"

// Comparison
s.equals("Hello, World!")     // true
s.equalsIgnoreCase("hello, world!")  // true
s.compareTo("Hello")          // positive (longer)

// Java 11+ Methods
"  hello  ".strip()           // "hello"
"  hello  ".stripLeading()    // "hello  "
"  hello  ".stripTrailing()   // "  hello"
"hello".repeat(3)             // "hellohellohello"
"".isBlank()                  // true
" ".isBlank()                 // true (vs isEmpty() → false)

// Java 12+ Methods
"hello world".indent(4)       // "    hello world\n"

// Java 15+ Text Blocks
String json = """
        {
            "name": "John",
            "age": 30
        }
        """;
```

---

## String in Switch, Records & Text Blocks

### String in Switch (Java 7+)

```java
String day = "MONDAY";
switch (day) {
    case "MONDAY": case "TUESDAY":
        System.out.println("Work day");
        break;
    case "SATURDAY": case "SUNDAY":
        System.out.println("Weekend");
        break;
}

// Java 14+ Enhanced Switch
String result = switch (day) {
    case "MONDAY", "TUESDAY" -> "Work day";
    case "SATURDAY", "SUNDAY" -> "Weekend";
    default -> "Unknown";
};
```

### Text Blocks (Java 15+)

```java
// Old way
String html = "<html>\n" +
              "    <body>\n" +
              "        <p>Hello</p>\n" +
              "    </body>\n" +
              "</html>";

// Text Block
String html = """
        <html>
            <body>
                <p>Hello</p>
            </body>
        </html>
        """;
```

---

## Interview Talking Points & Scripts

### "Why is String immutable in Java?"

> *"String is immutable for five critical reasons: First, it enables the String Pool — since Strings can't change, multiple references can safely share the same object, saving memory. Second, it makes Strings inherently thread-safe with no synchronization needed. Third, hashCode can be cached permanently, making Strings excellent HashMap keys with O(1) hash after first call. Fourth, security — Strings are used in class loading, database URLs, and network connections; if they were mutable, these could be maliciously changed. Fifth, the final class prevents subclassing that could break these guarantees."*

### "How many objects does `new String("abc")` create?"

> *"Up to two objects. First, the literal 'abc' may create an object in the String Pool if it's not already there — this happens at class loading time. Second, the `new` keyword always creates a separate object on the heap. So if 'abc' was never seen before, it's 2 objects. If it was already in the pool, just 1 new heap object."*

### "When should you use StringBuilder vs String?"

> *"Use String for values that don't change — it's thread-safe and interned. Use StringBuilder whenever you're building a string in a loop or through multiple concatenation steps, because String concatenation in a loop creates O(n²) garbage objects. StringBuilder is O(n). StringBuffer is the thread-safe version but I rarely need it — I'd typically use StringBuilder with external synchronization if needed."*

---

## Tricky Output Questions

```java
// Q1: What's the output?
String s1 = "Hello";
String s2 = "Hel" + "lo";
System.out.println(s1 == s2);  // true — compile-time constant

// Q2: What's the output?
String s1 = "Hello";
String s3 = "Hel";
String s4 = s3 + "lo";
System.out.println(s1 == s4);  // false — runtime concatenation

// Q3: What's the output?
final String s3 = "Hel";
String s5 = s3 + "lo";
System.out.println("Hello" == s5);  // true — final makes it compile-time constant

// Q4: What's the output?
String s1 = new String("abc");
String s2 = new String("abc");
System.out.println(s1 == s2);       // false
System.out.println(s1.equals(s2));   // true
System.out.println(s1.intern() == s2.intern());  // true
```

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "String Pool is in PermGen" | Moved to **Heap** in Java 7 |
| "== compares content" | `==` compares **references**; use `equals()` for content |
| "String is mutable because you can reassign" | Reassignment changes the **reference**, not the object |
| "StringBuilder is thread-safe" | StringBuilder is NOT thread-safe; StringBuffer is |
| "concat() is same as +" | `+` uses StringBuilder internally; `concat()` creates new String directly |
| "String literal always creates new object" | Literals are **interned** — they reuse pool objects |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│              STRINGS CHEAT SHEET                            │
├────────────────────────────────────────────────────────────┤
│ String = immutable (final class + final byte[] + no mutators)│
│                                                            │
│ String Pool:                                               │
│ • Literals → pool (shared)                                 │
│ • new String() → heap (separate)                           │
│ • intern() → returns pool reference                        │
│ • Pool is in Heap (since Java 7)                           │
│                                                            │
│ Comparison:                                                │
│ • == → reference equality                                  │
│ • equals() → content equality (ALWAYS USE THIS)            │
│                                                            │
│ Concatenation:                                             │
│ • "a" + "b" → compile-time constant → pool                │
│ • var + "b" → runtime → heap (StringBuilder)               │
│ • final var + "b" → compile-time constant → pool           │
│ • Loop concat → USE StringBuilder                          │
│                                                            │
│ StringBuilder vs StringBuffer:                             │
│ • StringBuilder → fast, NOT thread-safe                    │
│ • StringBuffer → slower, thread-safe (synchronized)        │
│ • Both: initial capacity 16, grow 2x + 2                   │
│                                                            │
│ Key Methods:                                               │
│ • strip() > trim() (Java 11+, Unicode-aware)               │
│ • isBlank() vs isEmpty() (" ".isBlank()=true)              │
│ • repeat(), indent(), Text Blocks (Java 15+)               │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Exception Handling Deep Dive →](./04-exception-handling-deep-dive.md)
