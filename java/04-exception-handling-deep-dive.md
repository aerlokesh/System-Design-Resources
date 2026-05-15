# 🎯 Topic 4: Exception Handling Deep Dive

> **Java Interview — Deep Dive**
> A comprehensive guide covering Java exception hierarchy, checked vs unchecked, try-with-resources, custom exceptions, and best practices for interviews.

---

## Table of Contents

1. [Exception Hierarchy](#exception-hierarchy)
2. [Checked vs Unchecked Exceptions](#checked-vs-unchecked-exceptions)
3. [Error vs Exception](#error-vs-exception)
4. [Try-Catch-Finally Mechanics](#try-catch-finally-mechanics)
5. [Try-With-Resources (Java 7+)](#try-with-resources-java-7)
6. [Multi-Catch & Re-throwing](#multi-catch--re-throwing)
7. [Custom Exceptions](#custom-exceptions)
8. [Exception Handling Best Practices](#exception-handling-best-practices)
9. [Interview Tricky Questions](#interview-tricky-questions)
10. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
11. [Common Interview Mistakes](#common-interview-mistakes)
12. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Exception Hierarchy

```
                      Object
                        │
                    Throwable
                    /        \
               Error         Exception
              /    \         /        \
    OutOfMemory  StackOverflow  RuntimeException  IOException
    VirtualMachineError         /    |    \        FileNotFoundException
                        NullPointer  ClassCast  SQLException
                        ArrayIndexOOB  IllegalArgument
                        ArithmeticException
                        ConcurrentModificationException
```

### The Critical Insight

> `Throwable` has two children: `Error` (don't catch) and `Exception` (handle). `RuntimeException` and its subclasses are **unchecked**. All other exceptions are **checked**.

---

## Checked vs Unchecked Exceptions

### Checked Exceptions (Compile-Time)

- **Must** be declared (`throws`) or caught (`try-catch`)
- Compiler enforces handling
- Represent **recoverable** conditions

```java
// Checked — compiler forces you to handle it
public void readFile(String path) throws IOException {  // Declared
    FileInputStream fis = new FileInputStream(path);    // May throw FileNotFoundException
}

// Or catch it
public void readFile(String path) {
    try {
        FileInputStream fis = new FileInputStream(path);
    } catch (FileNotFoundException e) {
        System.err.println("File not found: " + path);
    }
}
```

**Common Checked Exceptions**: `IOException`, `SQLException`, `ClassNotFoundException`, `FileNotFoundException`, `InterruptedException`

### Unchecked Exceptions (Runtime)

- NOT required to be declared or caught
- Extend `RuntimeException`
- Represent **programming errors** (bugs)

```java
// Unchecked — no declaration needed
public int divide(int a, int b) {
    return a / b;  // ArithmeticException if b == 0 — NOT required to catch
}
```

**Common Unchecked Exceptions**: `NullPointerException`, `ArrayIndexOutOfBoundsException`, `ClassCastException`, `IllegalArgumentException`, `IllegalStateException`, `NumberFormatException`, `ConcurrentModificationException`

### Comparison Table

| Feature | Checked | Unchecked |
|---------|---------|-----------|
| Extends | `Exception` (not RuntimeException) | `RuntimeException` |
| Compiler check | ✅ Must handle | ❌ No requirement |
| Declaration | Must use `throws` | Optional |
| Represents | Recoverable conditions | Programming errors |
| Examples | IOException, SQLException | NullPointerException, ClassCastException |

---

## Error vs Exception

| Feature | Error | Exception |
|---------|-------|-----------|
| Recoverable | **No** — JVM/system failure | **Yes** (usually) |
| Should catch? | **No** — nothing you can do | Yes |
| Caused by | JVM, hardware, OS | Application logic |
| Examples | `OutOfMemoryError`, `StackOverflowError` | `IOException`, `NullPointerException` |

```java
// ❌ BAD — Never catch Error (you can't recover)
try {
    recursiveMethod();
} catch (StackOverflowError e) {
    // JVM state is corrupted — can't meaningfully recover
}

// ❌ VERY BAD — Never catch Throwable
try {
    doSomething();
} catch (Throwable t) {
    // Catches EVERYTHING including OutOfMemoryError!
}
```

---

## Try-Catch-Finally Mechanics

### Execution Order

```java
try {
    // 1. Executed first
    riskyOperation();
} catch (IOException e) {
    // 2. Executed ONLY if exception matches
    handleError(e);
} finally {
    // 3. ALWAYS executed (with exceptions — see below)
    cleanup();
}
```

### When `finally` Does NOT Execute

```java
// 1. System.exit() called in try or catch
try {
    System.exit(0);  // JVM terminates — finally SKIPPED
} finally {
    System.out.println("Never printed!");
}

// 2. JVM crash (OutOfMemoryError, infinite loop)
// 3. Thread killed (Thread.stop() — deprecated)
// 4. Power failure / OS kill
```

### Return Value Trap (Interview Favorite!)

```java
public static int tricky() {
    try {
        return 1;
    } catch (Exception e) {
        return 2;
    } finally {
        return 3;  // ⚠️ This OVERRIDES the try/catch return!
    }
}
// Returns: 3 (finally's return wins!)

// ⚠️ NEVER return from finally — it swallows exceptions too!
public static int dangerous() {
    try {
        throw new RuntimeException("Error!");
    } finally {
        return 42;  // Exception is SILENTLY SWALLOWED — returns 42
    }
}
```

---

## Try-With-Resources (Java 7+)

### The Problem Without It

```java
// ❌ BAD — Verbose, error-prone (resource leak if close() throws)
FileInputStream fis = null;
try {
    fis = new FileInputStream("file.txt");
    // use fis
} catch (IOException e) {
    // handle
} finally {
    if (fis != null) {
        try {
            fis.close();  // close() can also throw!
        } catch (IOException e) {
            // swallowed
        }
    }
}
```

### The Solution

```java
// ✅ GOOD — Auto-closes, no leak, clean code
try (FileInputStream fis = new FileInputStream("file.txt");
     BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
    String line = br.readLine();
} catch (IOException e) {
    // handle
}
// Resources automatically closed in REVERSE order (br first, then fis)
```

### How It Works

- Resource must implement `AutoCloseable` (or `Closeable`)
- `close()` called automatically at end of try block
- Closed in **reverse declaration order**
- If both try body and close() throw → try body exception is primary, close() exception is **suppressed**

```java
// Accessing suppressed exceptions
try {
    // ...
} catch (IOException e) {
    Throwable[] suppressed = e.getSuppressed();  // Exceptions from close()
}
```

### Java 9+ Enhancement

```java
// Java 9+ — Effectively final variables in try-with-resources
FileInputStream fis = new FileInputStream("file.txt");
BufferedReader br = new BufferedReader(new InputStreamReader(fis));

try (fis; br) {  // ✅ No need to redeclare — just reference existing vars
    String line = br.readLine();
}
```

---

## Multi-Catch & Re-throwing

### Multi-Catch (Java 7+)

```java
// ❌ Before Java 7 — Repetitive
try {
    riskyOperation();
} catch (IOException e) {
    log(e);
    throw new ServiceException(e);
} catch (SQLException e) {
    log(e);
    throw new ServiceException(e);
}

// ✅ Multi-catch — Clean
try {
    riskyOperation();
} catch (IOException | SQLException e) {
    log(e);
    throw new ServiceException(e);
}
```

> **Rule**: Multi-catch exceptions cannot be in the same inheritance hierarchy (e.g., `IOException | FileNotFoundException` won't compile — FileNotFoundException IS-A IOException).

### Exception Chaining

```java
try {
    connectToDatabase();
} catch (SQLException e) {
    throw new ServiceException("Failed to connect", e);  // Wraps original
}
// Original exception preserved via getCause()
```

---

## Custom Exceptions

```java
// Checked custom exception
public class InsufficientFundsException extends Exception {
    private final double amount;
    
    public InsufficientFundsException(String message, double amount) {
        super(message);
        this.amount = amount;
    }
    
    public double getAmount() { return amount; }
}

// Unchecked custom exception
public class UserNotFoundException extends RuntimeException {
    private final String userId;
    
    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
        this.userId = userId;
    }
    
    public String getUserId() { return userId; }
}
```

### When to Use Custom Exceptions

| Use Case | Example |
|----------|---------|
| Domain-specific errors | `InsufficientFundsException`, `OrderAlreadyShippedException` |
| API boundary | `ServiceException`, `ApiException` |
| Carrying additional context | Error codes, request IDs, affected entities |

---

## Exception Handling Best Practices

### Do's ✅

```java
// 1. Catch specific exceptions
try { } catch (FileNotFoundException e) { }  // ✅ Specific

// 2. Use try-with-resources for all AutoCloseable
try (var conn = dataSource.getConnection()) { }

// 3. Log the exception properly
catch (IOException e) {
    logger.error("Failed to read config: {}", path, e);  // Include stack trace
}

// 4. Throw early, catch late
public void process(String input) {
    if (input == null) throw new IllegalArgumentException("Input cannot be null");  // Throw EARLY
}

// 5. Use exception chaining
throw new ServiceException("Processing failed", originalException);

// 6. Document exceptions with @throws Javadoc
/**
 * @throws IllegalArgumentException if amount is negative
 * @throws InsufficientFundsException if balance is insufficient
 */
```

### Don'ts ❌

```java
// 1. Never catch generic Exception/Throwable
catch (Exception e) { }      // ❌ Too broad
catch (Throwable t) { }      // ❌ Catches Errors too!

// 2. Never swallow exceptions
catch (IOException e) { }     // ❌ Empty catch — silent failure

// 3. Never use exceptions for flow control
try {
    int i = 0;
    while (true) array[i++]++;  // ❌ Using AIOOBE to end loop
} catch (ArrayIndexOutOfBoundsException e) { }

// 4. Never return from finally
finally { return value; }      // ❌ Swallows exceptions

// 5. Don't log and throw
catch (IOException e) {
    logger.error("Error", e);
    throw e;                   // ❌ Exception logged TWICE up the chain
}

// 6. Don't use exceptions for null checks
try { obj.method(); }
catch (NullPointerException e) { }  // ❌ Use if (obj != null) instead
```

---

## Interview Tricky Questions

### Q1: What happens here?

```java
public static void main(String[] args) {
    try {
        System.out.println("try");
        throw new RuntimeException();
    } catch (Exception e) {
        System.out.println("catch");
        throw new RuntimeException();
    } finally {
        System.out.println("finally");
    }
}
// Output: try, catch, finally → then RuntimeException propagates
```

### Q2: Can you catch multiple exceptions?

```java
// Yes — Multi-catch (Java 7+)
catch (IOException | SQLException e) { }

// But NOT if one is parent of other:
catch (Exception | IOException e) { }  // ❌ COMPILE ERROR — IOException IS-A Exception
```

### Q3: What's the output?

```java
public static String test() {
    try {
        throw new Exception("try");
    } catch (Exception e) {
        throw new RuntimeException("catch");
    } finally {
        return "finally";  // Swallows the RuntimeException!
    }
}
// Returns: "finally" — NO exception thrown
```

### Q4: Checked or Unchecked?

```java
NullPointerException      // Unchecked (extends RuntimeException)
IOException               // Checked (extends Exception)
ClassCastException        // Unchecked (extends RuntimeException)
FileNotFoundException     // Checked (extends IOException)
StackOverflowError        // Error (don't catch)
NumberFormatException     // Unchecked (extends IllegalArgumentException → RuntimeException)
InterruptedException      // Checked (extends Exception)
```

---

## Interview Talking Points & Scripts

### "Explain checked vs unchecked exceptions"

> *"Checked exceptions extend Exception but not RuntimeException — the compiler forces you to handle them with try-catch or declare them with throws. They represent recoverable conditions like IOException or SQLException. Unchecked exceptions extend RuntimeException — no compile-time checking needed. They represent programming errors like NullPointerException or ArrayIndexOutOfBounds. The debate is ongoing: some prefer unchecked for cleaner code, while checked exceptions force explicit error handling."*

### "How does try-with-resources work?"

> *"Try-with-resources automatically closes any resource that implements AutoCloseable at the end of the try block. Resources are closed in reverse declaration order. If both the try body and close() method throw exceptions, the try body exception is the primary one, and the close() exception becomes a 'suppressed exception' accessible via getSuppressed(). This eliminates the need for verbose finally blocks and prevents resource leaks."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "finally always executes" | Not with `System.exit()`, JVM crash, or thread kill |
| "Checked exceptions are better" | Both have trade-offs; modern Java prefers unchecked in many cases |
| "catch(Exception e) is fine" | Too broad — catches things you didn't intend to handle |
| "Error and Exception are the same" | Error = JVM failure (don't catch); Exception = application failure |
| "Return in finally is OK" | It swallows exceptions silently — **never do this** |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│            EXCEPTION HANDLING CHEAT SHEET                    │
├────────────────────────────────────────────────────────────┤
│ Hierarchy: Throwable → Error (don't catch)                  │
│                       → Exception → RuntimeException (unchecked)│
│                                   → Others (checked)         │
│                                                            │
│ Checked:   Must handle (try-catch) or declare (throws)      │
│ Unchecked: No requirement (RuntimeException subclasses)      │
│ Error:     JVM failure — never catch                        │
│                                                            │
│ Try-With-Resources:                                         │
│ • AutoCloseable resources auto-closed                       │
│ • Closed in REVERSE order                                   │
│ • Suppressed exceptions via getSuppressed()                 │
│ • Java 9+: effectively final vars allowed                   │
│                                                            │
│ Best Practices:                                             │
│ • Catch specific, not generic                               │
│ • Throw early, catch late                                   │
│ • Never swallow exceptions (empty catch)                    │
│ • Never return from finally                                 │
│ • Never log AND throw (double logging)                      │
│ • Use exception chaining (wrap with cause)                  │
│                                                            │
│ Traps:                                                      │
│ • finally return OVERRIDES try/catch return                  │
│ • finally return SWALLOWS exceptions                        │
│ • Multi-catch: exceptions can't be in same hierarchy        │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Generics & Type Erasure →](./05-generics-and-type-erasure.md)
