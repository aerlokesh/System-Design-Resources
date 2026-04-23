# 🎯 LLD Topic 20: LLD Interview Approach & Framework

> **Low-Level Design Interview — Deep Dive**
> A step-by-step framework for approaching any LLD interview question — from requirements gathering to class design to implementation, with time management, common mistakes, and scoring criteria.

---

## The 5-Step LLD Framework

```
Step 1: CLARIFY REQUIREMENTS (3-5 min)
  → What entities? What operations? What constraints?
  
Step 2: IDENTIFY CORE OBJECTS & RELATIONSHIPS (5-7 min)
  → Nouns become classes, verbs become methods
  
Step 3: DEFINE CLASS DIAGRAM (5 min)
  → UML sketch: classes, interfaces, relationships
  
Step 4: IMPLEMENT CORE LOGIC (20-25 min)
  → Write the code with proper OOP, patterns, thread safety
  
Step 5: DISCUSS EXTENSIONS & TRADEOFFS (5 min)
  → Scalability, testing, what you'd add with more time
```

---

## Step 1: Clarify Requirements

```
Ask these questions for EVERY LLD problem:

FUNCTIONAL:
  "What are the main operations the system needs to support?"
  "Are there different user types or roles?"
  "What are the edge cases?" (empty, full, duplicate, concurrent)

NON-FUNCTIONAL:
  "Is this single-threaded or multi-threaded?"
  "How many concurrent users/operations?"
  "Do we need persistence, or is in-memory OK?"
  
SCOPE:
  "Should I focus on the core flow or cover all features?"
  "Do you want me to implement the full system or key classes?"

Example for Parking Lot:
  "How many floors? How many spot types? (Compact, Regular, Large)"
  "Is there a pricing model? Per-hour? Per-day?"
  "Do we need real-time availability tracking?"
  "Single entrance or multiple?"
```

---

## Step 2: Identify Core Objects

```
Technique: Noun-Verb Analysis

Problem: "Design a Movie Ticket Booking System"

NOUNS (→ Classes):
  Movie, Show, Theater, Screen, Seat, Booking, User, Payment, Ticket

VERBS (→ Methods):
  searchMovies, selectShow, selectSeats, makeBooking, processPayment, 
  generateTicket, cancelBooking

RELATIONSHIPS:
  Theater HAS-MANY Screens (composition)
  Screen HAS-MANY Seats (composition)
  Movie HAS-MANY Shows (association)
  Show belongs-to Screen (association)
  Booking HAS-MANY Seats (association)
  User HAS-MANY Bookings (association)
```

---

## Step 3: Class Diagram (Quick Sketch)

```
Show KEY classes, not all classes:

┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Theater    │◆──>│    Screen    │◆──>│     Seat     │
├──────────────┤    ├──────────────┤    ├──────────────┤
│ - name       │    │ - screenNum  │    │ - row, col   │
│ - screens    │    │ - seats      │    │ - type       │
│ - address    │    │ - shows      │    │ - status     │
├──────────────┤    ├──────────────┤    ├──────────────┤
│ + getShows() │    │ + available()│    │ + reserve()  │
└──────────────┘    └──────────────┘    │ + release()  │
                                         └──────────────┘
                    ┌──────────────┐    ┌──────────────┐
                    │   Booking    │───>│    Show      │
                    ├──────────────┤    ├──────────────┤
                    │ - bookingId  │    │ - movie      │
                    │ - user       │    │ - startTime  │
                    │ - seats      │    │ - screen     │
                    │ - status     │    │ - price      │
                    ├──────────────┤    └──────────────┘
                    │ + confirm()  │
                    │ + cancel()   │
                    └──────────────┘

Interfaces:
  <<interface>> PaymentStrategy
  <<interface>> SeatAssigner
  <<interface>> NotificationChannel
```

---

## Step 4: Implementation Priorities

```
Implement in this order:

1. MODELS / ENTITIES (2-3 min)
   - Core domain objects with fields and constructors
   - Enums for types and statuses

2. INTERFACES (2-3 min)
   - Repository interfaces
   - Strategy interfaces for varying behavior

3. CORE SERVICE LOGIC (10-15 min)
   - The main use case (book ticket, park car, etc.)
   - Validation, state transitions, business rules

4. PATTERN APPLICATION (5 min)
   - Strategy for pricing, Factory for creation
   - Observer for notifications, State for lifecycle

5. THREAD SAFETY (if applicable) (3-5 min)
   - synchronized/Lock for shared state
   - ConcurrentHashMap for concurrent access
```

---

## Step 5: Extensions Discussion

```
After implementing, mention:

"With more time, I would add:"
  1. TESTING: Unit tests with mocks for each service
  2. PERSISTENCE: Swap InMemoryRepository for database-backed
  3. CONCURRENCY: Handle race conditions on seat booking
  4. CACHING: LRU cache for frequently accessed shows
  5. LOGGING: Audit trail for bookings and cancellations
  6. METRICS: Track booking rates, popular movies
  7. EXTENSIBILITY: Plugin system for new payment methods
```

---

## Scoring Criteria (What Interviewers Look For)

```
┌─────────────────────────┬──────────────────────────────────┐
│ Criteria                │ What They Want                    │
├─────────────────────────┼──────────────────────────────────┤
│ Requirements Gathering  │ Asked good questions, didn't jump │
│                         │ straight to coding                │
├─────────────────────────┼──────────────────────────────────┤
│ Object Modeling         │ Right classes, right boundaries   │
│                         │ Clear IS-A vs HAS-A decisions     │
├─────────────────────────┼──────────────────────────────────┤
│ Design Patterns         │ Applied naturally (not forced)    │
│                         │ Explained WHY, not just WHAT      │
├─────────────────────────┼──────────────────────────────────┤
│ SOLID Principles        │ SRP in class design, DIP in deps  │
│                         │ OCP in extension points           │
├─────────────────────────┼──────────────────────────────────┤
│ Code Quality            │ Clean naming, encapsulation       │
│                         │ Error handling, immutability       │
├─────────────────────────┼──────────────────────────────────┤
│ Extensibility           │ Easy to add new features           │
│                         │ Open/Closed at the right points   │
├─────────────────────────┼──────────────────────────────────┤
│ Concurrency             │ Thread safety where needed         │
│                         │ Correct synchronization            │
├─────────────────────────┼──────────────────────────────────┤
│ Communication           │ Explained thinking process         │
│                         │ Discussed tradeoffs                │
└─────────────────────────┴──────────────────────────────────┘
```

---

## Common LLD Problems & Key Patterns

| Problem | Key Patterns | Key Data Structures |
|---|---|---|
| **Parking Lot** | Strategy (pricing), Factory (vehicle) | HashMap, Enum |
| **Movie Booking** | State (booking), Strategy (pricing) | ConcurrentHashMap, EnumSet |
| **Elevator System** | State (elevator), Strategy (scheduling) | PriorityQueue |
| **LRU Cache** | — (data structure design) | LinkedHashMap or HashMap+DLL |
| **Rate Limiter** | Strategy (algorithm), — | ConcurrentHashMap, AtomicLong |
| **Chess** | Strategy (move validation), Observer | 2D array, Enum |
| **Splitwise** | Observer, — | HashMap, graph for debts |
| **File System** | Composite (files/dirs) | TreeMap, HashMap |
| **Notification** | Observer, Strategy, Factory | ConcurrentHashMap, BlockingQueue |
| **Logging Framework** | Singleton, Decorator, Strategy | ConcurrentLinkedQueue |
| **ATM System** | State, Chain of Responsibility | Enum, Map |
| **Vending Machine** | State (machine states) | Enum, EnumMap |

---

## Time Management

```
45-minute LLD interview:

0-5 min:   Clarify requirements (ASK, don't assume)
5-10 min:  Identify objects, sketch class diagram
10-35 min: Implement core logic (prioritize main flow)
35-40 min: Add patterns, thread safety if time permits
40-45 min: Discuss extensions, testing, tradeoffs

COMMON MISTAKE: Spending 15 min on requirements or 20 min on 
perfect UML diagram, then running out of time to code.

RULE: Start coding by minute 10.
```

---

## Anti-Patterns to Avoid

```
❌ Jumping to code without understanding requirements
❌ Over-engineering with every design pattern you know
❌ Creating perfect UML instead of working code
❌ Ignoring thread safety when the system is clearly concurrent
❌ Using getters/setters for everything (anemic domain model)
❌ One giant class doing everything (God class)
❌ Hardcoding what should be configurable (pricing, limits)
❌ Not discussing tradeoffs when asked
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          LLD INTERVIEW FRAMEWORK                              │
├──────────────────────────────────────────────────────────────┤
│ 1. CLARIFY: Ask about entities, operations, constraints       │
│ 2. MODEL: Nouns → classes, Verbs → methods                   │
│ 3. DIAGRAM: Quick UML with key relationships                  │
│ 4. CODE: Models → Interfaces → Services → Patterns            │
│ 5. EXTEND: Testing, persistence, concurrency, metrics         │
├──────────────────────────────────────────────────────────────┤
│ Patterns to have ready:                                       │
│   Strategy (pricing, scheduling, validation)                  │
│   Observer (events, notifications)                            │
│   State (order lifecycle, machine states)                     │
│   Factory (object creation based on type)                     │
│   Repository (data access abstraction)                        │
│   Builder (complex object construction)                       │
├──────────────────────────────────────────────────────────────┤
│ "Let me start by clarifying the requirements, then I'll       │
│  identify the key entities and their relationships, sketch    │
│  a class diagram, and implement the core flow with proper     │
│  OOP principles and patterns."                                │
└──────────────────────────────────────────────────────────────┘
```
