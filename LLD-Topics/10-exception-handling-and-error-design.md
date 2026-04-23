# 🎯 LLD Topic 10: Exception Handling & Error Design

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering checked vs unchecked exceptions, custom exception hierarchies, error handling strategies, Result/Either patterns, and how to design clean error flows in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 10: Exception Handling \& Error Design](#-lld-topic-10-exception-handling--error-design)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Checked vs Unchecked Exceptions](#checked-vs-unchecked-exceptions)
  - [Custom Exception Hierarchy](#custom-exception-hierarchy)
  - [Exception Handling Best Practices](#exception-handling-best-practices)
    - [DO: Catch Specific Exceptions](#do-catch-specific-exceptions)
    - [DO: Include Context in Exceptions](#do-include-context-in-exceptions)
    - [DO: Clean Up Resources with Try-With-Resources](#do-clean-up-resources-with-try-with-resources)
    - [DON'T: Swallow Exceptions](#dont-swallow-exceptions)
  - [Fail-Fast Principle](#fail-fast-principle)
    - [Guard Clauses](#guard-clauses)
  - [Result Pattern](#result-pattern)
    - [When to Use Exceptions vs Result](#when-to-use-exceptions-vs-result)
  - [Exception Translation](#exception-translation)
  - [Error Handling in LLD Problems](#error-handling-in-lld-problems)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Designing Error Handling](#when-designing-error-handling)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Exception handling is how your system communicates and recovers from errors. Good error design means: errors are **specific** (not generic), **actionable** (caller knows what to do), and **clean** (no stack traces leaking to users).

```
Good Error Design:
  1. Fail FAST: Validate inputs immediately
  2. Fail CLEARLY: Use specific exception types with messages
  3. Fail SAFELY: Don't leave system in inconsistent state
  4. Fail INFORMATIVELY: Include context (what was attempted, what went wrong)
```

---

## Checked vs Unchecked Exceptions

```
Checked (extends Exception):
  - Compiler forces you to handle them
  - Use for RECOVERABLE conditions
  - Example: FileNotFoundException, InsufficientFundsException
  
Unchecked (extends RuntimeException):
  - Compiler doesn't force handling
  - Use for PROGRAMMING ERRORS
  - Example: NullPointerException, IllegalArgumentException

Modern Preference:
  Most Java teams prefer UNCHECKED exceptions because:
  - Checked exceptions clutter method signatures
  - Most callers just wrap and rethrow anyway
  - Spring, Hibernate, etc. all use unchecked
```

---

## Custom Exception Hierarchy

```java
// Base exception for your domain
abstract class OrderException extends RuntimeException {
    private final String orderId;
    private final ErrorCode errorCode;
    
    OrderException(String message, String orderId, ErrorCode errorCode) {
        super(message);
        this.orderId = orderId;
        this.errorCode = errorCode;
    }
    
    public String getOrderId() { return orderId; }
    public ErrorCode getErrorCode() { return errorCode; }
}

// Specific exceptions
class OrderNotFoundException extends OrderException {
    OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId, orderId, ErrorCode.NOT_FOUND);
    }
}

class InsufficientStockException extends OrderException {
    private final String productId;
    private final int requested;
    private final int available;
    
    InsufficientStockException(String orderId, String productId, int requested, int available) {
        super(String.format("Insufficient stock for %s: requested %d, available %d",
            productId, requested, available), orderId, ErrorCode.INSUFFICIENT_STOCK);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }
}

class PaymentDeclinedException extends OrderException {
    private final String reason;
    
    PaymentDeclinedException(String orderId, String reason) {
        super("Payment declined for order " + orderId + ": " + reason, 
              orderId, ErrorCode.PAYMENT_DECLINED);
        this.reason = reason;
    }
}

enum ErrorCode {
    NOT_FOUND, INSUFFICIENT_STOCK, PAYMENT_DECLINED, 
    INVALID_INPUT, DUPLICATE_ORDER, INTERNAL_ERROR
}
```

---

## Exception Handling Best Practices

### DO: Catch Specific Exceptions

```java
// ❌ BAD: Catching generic Exception
try {
    orderService.placeOrder(order);
} catch (Exception e) {
    log.error("Something went wrong", e);  // No idea what happened
}

// ✅ GOOD: Catch specific, handle appropriately
try {
    orderService.placeOrder(order);
} catch (InsufficientStockException e) {
    notifyUser("Item out of stock: " + e.getProductId());
    suggestAlternatives(e.getProductId());
} catch (PaymentDeclinedException e) {
    notifyUser("Payment failed: " + e.getReason());
    offerRetry();
} catch (OrderException e) {
    log.error("Order error: {}", e.getMessage(), e);
    notifyUser("Unable to process order. Please try again.");
}
```

### DO: Include Context in Exceptions

```java
// ❌ BAD: No context
throw new RuntimeException("Not found");

// ✅ GOOD: Rich context
throw new OrderNotFoundException(orderId);
// Message: "Order not found: ORD-12345"
// Has: orderId, errorCode for programmatic handling
```

### DO: Clean Up Resources with Try-With-Resources

```java
// ✅ Resources auto-closed even on exception
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setString(1, orderId);
    ResultSet rs = stmt.executeQuery();
    // ...
}  // conn and stmt automatically closed
```

### DON'T: Swallow Exceptions

```java
// ❌ TERRIBLE: Silent failure
try {
    repository.save(order);
} catch (Exception e) {
    // Empty catch = bugs that are impossible to debug
}

// ✅ At minimum, log it
try {
    repository.save(order);
} catch (DatabaseException e) {
    log.error("Failed to save order {}: {}", order.getId(), e.getMessage(), e);
    throw new OrderPersistenceException(order.getId(), e);
}
```

---

## Fail-Fast Principle

```java
// Validate at the BOUNDARY — fail before doing any work
class OrderService {
    void placeOrder(CreateOrderRequest request) {
        // Fail fast: validate all inputs upfront
        Objects.requireNonNull(request, "Request cannot be null");
        if (request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        for (OrderItem item : request.getItems()) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive: " + item);
            }
        }
        
        // All validations passed — now do the actual work
        processOrder(request);
    }
}
```

### Guard Clauses

```java
// ❌ BAD: Nested validation
void processPayment(Payment payment) {
    if (payment != null) {
        if (payment.getAmount() > 0) {
            if (payment.getCurrency() != null) {
                // actual logic buried deep
            }
        }
    }
}

// ✅ GOOD: Guard clauses — early returns
void processPayment(Payment payment) {
    if (payment == null) throw new IllegalArgumentException("Payment required");
    if (payment.getAmount() <= 0) throw new IllegalArgumentException("Amount must be positive");
    if (payment.getCurrency() == null) throw new IllegalArgumentException("Currency required");
    
    // Actual logic at top level — clean and readable
    chargePayment(payment);
}
```

---

## Result Pattern

```java
// Instead of throwing exceptions, return a Result object
class Result<T> {
    private final T value;
    private final String error;
    private final boolean success;
    
    private Result(T value, String error, boolean success) {
        this.value = value;
        this.error = error;
        this.success = success;
    }
    
    static <T> Result<T> success(T value) {
        return new Result<>(value, null, true);
    }
    
    static <T> Result<T> failure(String error) {
        return new Result<>(null, error, false);
    }
    
    boolean isSuccess() { return success; }
    T getValue() { return value; }
    String getError() { return error; }
    
    <R> Result<R> map(Function<T, R> fn) {
        return success ? Result.success(fn.apply(value)) : Result.failure(error);
    }
}

// Usage: No exceptions for expected business outcomes
class PaymentService {
    Result<PaymentReceipt> processPayment(PaymentRequest request) {
        if (!validateCard(request.getCard())) {
            return Result.failure("Invalid card number");
        }
        if (!hasSufficientFunds(request)) {
            return Result.failure("Insufficient funds");
        }
        PaymentReceipt receipt = charge(request);
        return Result.success(receipt);
    }
}

// Caller handles both cases explicitly
Result<PaymentReceipt> result = paymentService.processPayment(request);
if (result.isSuccess()) {
    sendConfirmation(result.getValue());
} else {
    showError(result.getError());
}
```

### When to Use Exceptions vs Result

```
Use EXCEPTIONS for:
  - Unexpected errors (database down, network failure)
  - Programming errors (null arguments, invalid state)
  - Situations that SHOULD NOT normally happen

Use RESULT for:
  - Expected business outcomes (payment declined, out of stock)
  - Validation results
  - Situations where BOTH success and failure are normal
```

---

## Exception Translation

```java
// Translate low-level exceptions to domain exceptions
class OrderRepository {
    void save(Order order) {
        try {
            jdbcTemplate.update(INSERT_SQL, order.getId(), order.getTotal());
        } catch (DuplicateKeyException e) {
            throw new DuplicateOrderException(order.getId(), e);
        } catch (DataAccessException e) {
            throw new OrderPersistenceException(order.getId(), e);
        }
        // Don't let JDBC exceptions leak to business layer!
    }
}
```

---

## Error Handling in LLD Problems

| LLD Problem | Key Error Scenarios |
|---|---|
| **Parking Lot** | Lot full, invalid vehicle type, spot already taken |
| **Payment System** | Card declined, insufficient funds, timeout |
| **Booking System** | Already booked, past date, invalid seats |
| **Rate Limiter** | Rate exceeded (return false, not exception) |
| **LRU Cache** | Key not found (return Optional, not exception) |
| **Elevator** | Invalid floor, overweight, maintenance mode |

---

## Interview Talking Points & Scripts

### When Designing Error Handling

> *"I'll create a domain-specific exception hierarchy — OrderNotFoundException, InsufficientStockException, PaymentDeclinedException — each extending a base OrderException with an error code. This lets callers handle specific errors differently while having a catch-all for unexpected failures. For expected business outcomes like 'out of stock', I might use a Result pattern instead of exceptions."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Using generic RuntimeException everywhere
   Fix: Create specific domain exceptions with context.

❌ Mistake 2: Empty catch blocks
   Fix: At minimum log. Preferably, translate and rethrow.

❌ Mistake 3: Throwing exceptions for expected outcomes
   Fix: Use Optional for "not found", Result for business outcomes.

❌ Mistake 4: Leaking implementation exceptions
   Fix: Translate JDBC exceptions to domain exceptions.

❌ Mistake 5: Not validating inputs (late failure)
   Fix: Fail fast with guard clauses at method entry.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          EXCEPTION HANDLING CHEAT SHEET                        │
├──────────────────┬───────────────────────────────────────────┤
│ Fail Fast        │ Validate inputs at boundaries immediately  │
│ Specific Errors  │ Custom hierarchy with context and codes    │
│ Guard Clauses    │ Early returns instead of nested ifs        │
│ Result Pattern   │ For expected business outcomes (not thrown) │
│ Exception Trans. │ Translate low-level to domain exceptions   │
│ Try-Resources    │ Auto-close resources safely                │
├──────────────────┼───────────────────────────────────────────┤
│ NOT FOUND        │ Return Optional.empty(), not exception     │
│ VALIDATION       │ Throw IllegalArgumentException early       │
│ BUSINESS RULE    │ Domain exception or Result                 │
│ INFRASTRUCTURE   │ Catch, translate, rethrow as domain        │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "I fail fast with guard clauses, use specific domain          │
│  exceptions with context, and translate infrastructure        │
│  errors so they don't leak through layer boundaries."         │
└──────────────────────────────────────────────────────────────┘
```
