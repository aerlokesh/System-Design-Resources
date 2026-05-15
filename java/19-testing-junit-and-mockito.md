# 🎯 Topic 19: Testing in Java — JUnit & Mockito

> **Java Interview — Deep Dive**
> Covering unit testing, JUnit 5, Mockito, TDD principles, and testing best practices for interviews.

---

## JUnit 5 Basics

```java
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {
    
    private Calculator calc;
    
    @BeforeEach
    void setUp() { calc = new Calculator(); }
    
    @AfterEach
    void tearDown() { calc = null; }
    
    @Test
    @DisplayName("Should add two positive numbers")
    void testAdd() {
        assertEquals(5, calc.add(2, 3));
        assertEquals(0, calc.add(-1, 1));
    }
    
    @Test
    void testDivideByZero() {
        assertThrows(ArithmeticException.class, () -> calc.divide(10, 0));
    }
    
    @Test
    @Disabled("Bug #123 — fix pending")
    void testBrokenFeature() { /* ... */ }
    
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5, 7, 9})
    void testIsOdd(int number) {
        assertTrue(calc.isOdd(number));
    }
    
    @ParameterizedTest
    @CsvSource({"1, 1, 2", "2, 3, 5", "-1, 1, 0"})
    void testAddParameterized(int a, int b, int expected) {
        assertEquals(expected, calc.add(a, b));
    }
}
```

### Key Assertions

```java
assertEquals(expected, actual);
assertNotEquals(unexpected, actual);
assertTrue(condition);
assertFalse(condition);
assertNull(value);
assertNotNull(value);
assertThrows(ExceptionType.class, () -> code());
assertTimeout(Duration.ofSeconds(1), () -> slowMethod());
assertAll(                          // Group assertions — all run even if one fails
    () -> assertEquals("Alice", user.getName()),
    () -> assertEquals(30, user.getAge()),
    () -> assertNotNull(user.getEmail())
);
```

### Lifecycle Annotations

| Annotation | When | Scope |
|-----------|------|-------|
| `@BeforeAll` | Before all tests (static) | Class |
| `@AfterAll` | After all tests (static) | Class |
| `@BeforeEach` | Before each test | Method |
| `@AfterEach` | After each test | Method |

---

## Mockito — Mocking Dependencies

```java
import static org.mockito.Mockito.*;

class UserServiceTest {
    
    @Mock
    private UserRepository userRepo;
    
    @InjectMocks
    private UserService userService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testGetUser() {
        // Arrange — define mock behavior
        User mockUser = new User(1, "Alice");
        when(userRepo.findById(1)).thenReturn(Optional.of(mockUser));
        
        // Act
        User result = userService.getUser(1);
        
        // Assert
        assertEquals("Alice", result.getName());
        verify(userRepo, times(1)).findById(1);           // Verify interaction
        verify(userRepo, never()).delete(any());           // Never called
    }
    
    @Test
    void testGetUserNotFound() {
        when(userRepo.findById(99)).thenReturn(Optional.empty());
        
        assertThrows(UserNotFoundException.class, () -> userService.getUser(99));
    }
    
    @Test
    void testSaveUser() {
        User user = new User(0, "Bob");
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1);
            return saved;
        });
        
        User saved = userService.createUser(user);
        assertEquals(1, saved.getId());
        
        // Argument capture
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertEquals("Bob", captor.getValue().getName());
    }
}
```

### Mock vs Spy

```java
// Mock — complete fake (all methods return defaults)
UserRepository mock = mock(UserRepository.class);
// All methods return null/0/false by default

// Spy — partial mock (real object, override specific methods)
UserRepository spy = spy(new UserRepositoryImpl());
doReturn(Optional.of(user)).when(spy).findById(1);  // Override this method
// Other methods use real implementation
```

---

## Testing Best Practices

### AAA Pattern (Arrange-Act-Assert)

```java
@Test
void testTransfer() {
    // Arrange
    Account from = new Account(1000);
    Account to = new Account(500);
    
    // Act
    transferService.transfer(from, to, 200);
    
    // Assert
    assertEquals(800, from.getBalance());
    assertEquals(700, to.getBalance());
}
```

### FIRST Principles

| Principle | Meaning |
|-----------|---------|
| **F**ast | Tests run quickly (milliseconds) |
| **I**ndependent | No test depends on another |
| **R**epeatable | Same result every run (no external dependencies) |
| **S**elf-validating | Pass/fail automatically (no manual checking) |
| **T**imely | Written close to production code (ideally TDD) |

### What to Test vs Not

| Test ✅ | Don't Test ❌ |
|---------|--------------|
| Business logic | Getters/setters |
| Edge cases (null, empty, boundary) | Framework code (Spring, JPA) |
| Error handling paths | Private methods directly |
| Integration points (with mocks) | Constructors with no logic |

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│ JUnit 5: @Test, @BeforeEach, @ParameterizedTest            │
│ Assertions: assertEquals, assertThrows, assertAll           │
│                                                            │
│ Mockito: when().thenReturn(), verify(), @Mock, @InjectMocks │
│ Mock = full fake | Spy = partial mock (real + overrides)    │
│                                                            │
│ Patterns: AAA (Arrange-Act-Assert), FIRST principles        │
│ Test: business logic, edge cases, error paths               │
│ Don't test: getters, framework code, trivial constructors   │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Java Interview Quick Reference & Traps →](./20-java-interview-quick-reference.md)
