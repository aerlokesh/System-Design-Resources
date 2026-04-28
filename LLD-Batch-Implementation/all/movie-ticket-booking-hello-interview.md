# Movie Ticket Booking System (BookMyShow) - HELLO Interview Framework

> **Companies**: Salesforce, Microsoft, Uber, Intuit, Walmart +5 more  
> **Difficulty**: Medium  
> **Primary Pattern**: Observer  
> **Time**: 35 minutes  
> **Reference**: [HelloInterview LLD Delivery](https://www.hellointerview.com/learn/low-level-design/in-a-hurry/delivery)

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions to Ask
- "How many theaters/screens?" → Multiple theaters, each with multiple screens
- "Do we need seat selection?" → Yes, users pick specific seats
- "Should we handle concurrent bookings for same seat?" → Yes, critical requirement
- "Do we need payment processing?" → Out of scope, assume payment always succeeds
- "Should we notify users about booking status?" → Yes (Observer pattern trigger)

### Functional Requirements

#### Must Have (P0)
1. **Movie & Show Management**
   - List movies currently showing
   - Each movie has shows at specific times on specific screens
   - Each show has a seating arrangement (rows × columns)

2. **Seat Selection & Booking**
   - View available seats for a show
   - Select one or more seats
   - Book selected seats atomically (all or nothing)
   - Seats locked temporarily during booking flow

3. **Concurrency Control**
   - Two users cannot book the same seat
   - Temporary seat holds expire after timeout
   - Thread-safe seat status transitions

4. **Booking Lifecycle**
   - AVAILABLE → HELD → BOOKED (happy path)
   - HELD → AVAILABLE (timeout/cancel)
   - BOOKED → CANCELLED (user cancels)

5. **Notifications (Observer)**
   - Notify on successful booking
   - Notify on cancellation
   - Notify when held seats released (waitlist)

#### Nice to Have (P1)
- Multiple pricing tiers (Premium, Standard, Economy)
- Discount/coupon support
- Waitlist when show is full
- Reviews and ratings
- Food/beverage add-ons

### Non-Functional Requirements
- **Thread Safety**: Handle 1000+ concurrent booking attempts
- **Consistency**: No double-booking (CRITICAL)
- **Latency**: Seat availability check < 50ms, booking < 200ms
- **Seat Hold Timeout**: 5 minutes default

### Constraints & Assumptions
- Seats arranged in grid (row × col)
- Seat IDs are unique per show (e.g., "A1", "B5")
- One booking = one user, one show, 1-10 seats
- No partial bookings (atomic)

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│    Movie     │──1:N─│    Show      │──1:1─│   Screen     │
│  - id       │      │  - id        │      │  - id        │
│  - title    │      │  - movie     │      │  - name      │
│  - duration │      │  - screen    │      │  - totalSeats│
│  - genre    │      │  - showTime  │      │  - rows,cols │
└──────────────┘      │  - seats[][] │      └──────────────┘
                      └──────┬───────┘
                             │ 1:N
                      ┌──────▼───────┐
                      │    Seat      │
                      │  - id (A1)   │
                      │  - row, col  │
                      │  - status    │──── AVAILABLE|HELD|BOOKED|CANCELLED
                      │  - price    │
                      │  - heldBy   │
                      │  - heldAt   │
                      └──────────────┘
                             │ N:1
                      ┌──────▼───────┐      ┌──────────────────┐
                      │   Booking    │──────│ BookingObserver   │
                      │  - id        │      │ (interface)       │
                      │  - user      │      │  - onBooked()     │
                      │  - show      │      │  - onCancelled()  │
                      │  - seats[]   │      │  - onHoldExpired()│
                      │  - status    │      └──────┬───────────┘
                      │  - totalPrice│             │ implements
                      │  - bookedAt  │      ┌──────▼───────────┐
                      └──────────────┘      │EmailNotifier     │
                                            │SMSNotifier       │
                                            │WaitlistNotifier  │
                                            └──────────────────┘
```

### Enum: SeatStatus
```
AVAILABLE → Seat is free to select
HELD      → Temporarily locked by a user (expires after timeout)
BOOKED    → Confirmed booking
CANCELLED → Previously booked, now cancelled
```

### Enum: BookingStatus
```
PENDING   → Seats held, awaiting confirmation
CONFIRMED → Booking complete
CANCELLED → Booking cancelled by user
EXPIRED   → Hold timed out
```

### Class: Movie
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique movie ID |
| title | String | Movie title |
| durationMins | int | Duration in minutes |
| genre | String | Genre |

### Class: Screen
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Screen identifier |
| name | String | Display name ("Screen 1") |
| rows | int | Number of seat rows |
| cols | int | Seats per row |

### Class: Seat
| Attribute | Type | Description |
|-----------|------|-------------|
| seatId | String | e.g., "A1", "B5" |
| row | int | Row index |
| col | int | Column index |
| status | SeatStatus | Current status |
| price | double | Ticket price |
| heldBy | String | User who holds it |
| heldAt | long | Timestamp of hold |

### Class: Show
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique show ID |
| movie | Movie | Which movie |
| screen | Screen | Which screen |
| showTime | LocalDateTime | When |
| seats | Seat[][] | Grid of seats |
| lock | ReentrantLock | Concurrency control |

### Class: Booking
| Attribute | Type | Description |
|-----------|------|-------------|
| bookingId | String | Unique booking ID |
| userId | String | Who booked |
| show | Show | Which show |
| seats | List<Seat> | Booked seats |
| status | BookingStatus | Current status |
| totalPrice | double | Sum of seat prices |
| createdAt | LocalDateTime | When booked |

### Interface: BookingObserver
| Method | Description |
|--------|-------------|
| onBookingConfirmed(Booking) | Called when booking succeeds |
| onBookingCancelled(Booking) | Called when booking cancelled |
| onSeatsReleased(Show, List<Seat>) | Called when held seats timeout |

### Class: BookingService (Singleton)
**Responsibility**: Orchestrates the entire booking flow, notifies observers

---

## 3️⃣ API Design

### BookingService (Public API)

```java
public class BookingService {
    
    /** Add a movie to the system */
    void addMovie(Movie movie);
    
    /** Add a show for a movie on a screen at a time */
    Show addShow(String movieId, Screen screen, LocalDateTime showTime, double basePrice);
    
    /** Get available seats for a show */
    List<Seat> getAvailableSeats(String showId);
    
    /**
     * Hold seats temporarily for a user (5-min timeout)
     * @param showId  Show identifier
     * @param seatIds List of seat IDs to hold (e.g., ["A1", "A2"])
     * @param userId  User requesting hold
     * @return true if all seats successfully held
     * @throws SeatNotAvailableException if any seat is not available
     */
    boolean holdSeats(String showId, List<String> seatIds, String userId);
    
    /**
     * Confirm booking for held seats
     * @return Booking object with confirmation details
     * @throws BookingException if seats not held by this user
     */
    Booking confirmBooking(String showId, String userId);
    
    /**
     * Cancel an existing booking — releases seats back
     */
    boolean cancelBooking(String bookingId);
    
    /** Register observer for booking events */
    void addObserver(BookingObserver observer);
    
    /** Remove observer */
    void removeObserver(BookingObserver observer);
}
```

### Show (Seat Management)

```java
public class Show {
    /** Get all available seats */
    List<Seat> getAvailableSeats();
    
    /** Hold specific seats for a user (atomic — all or nothing) */
    boolean holdSeats(List<String> seatIds, String userId);
    
    /** Confirm held seats → BOOKED */
    List<Seat> confirmSeats(String userId);
    
    /** Release seats (cancel or timeout) */
    void releaseSeats(List<Seat> seats);
    
    /** Release expired holds */
    void releaseExpiredHolds(long timeoutMs);
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Happy Path — Book 2 Seats

```
User → BookingService.getAvailableSeats("show-1")
  └─ Returns: [A1(AVAILABLE), A2(AVAILABLE), A3(AVAILABLE), ...]

User → BookingService.holdSeats("show-1", ["A1","A2"], "user-123")
  ├─ show.lock() ← ReentrantLock for thread safety
  ├─ Check: A1 == AVAILABLE? ✓
  ├─ Check: A2 == AVAILABLE? ✓
  ├─ A1.status = HELD, A1.heldBy = "user-123"
  ├─ A2.status = HELD, A2.heldBy = "user-123"
  ├─ show.unlock()
  └─ Returns: true (seats held for 5 minutes)

User → BookingService.confirmBooking("show-1", "user-123")
  ├─ show.lock()
  ├─ Find seats held by "user-123": [A1, A2]
  ├─ A1.status = BOOKED, A2.status = BOOKED
  ├─ Create Booking(id, user-123, show-1, [A1,A2], CONFIRMED)
  ├─ show.unlock()
  ├─ notifyObservers(onBookingConfirmed, booking)  ← Observer Pattern
  │     ├─ EmailNotifier → sends confirmation email
  │     └─ SMSNotifier → sends SMS
  └─ Returns: Booking object
```

### Scenario 2: Concurrent Booking Conflict

```
Thread A: holdSeats("show-1", ["A1"], "user-1")
Thread B: holdSeats("show-1", ["A1"], "user-2")  ← same seat!

  Thread A acquires lock first:
    A1.status = HELD, A1.heldBy = "user-1"   ✓
    lock released
  
  Thread B acquires lock:
    A1.status == HELD (not AVAILABLE)
    → throws SeatNotAvailableException        ✗

Result: Only user-1 gets the seat. No double-booking.
```

### Scenario 3: Hold Expiry

```
Timer (every 30s) → BookingService.cleanupExpiredHolds()
  ├─ For each show:
  │   ├─ show.lock()
  │   ├─ Find seats where status==HELD && heldAt + 5min < now
  │   ├─ For each expired seat: status = AVAILABLE, heldBy = null
  │   ├─ show.unlock()
  │   └─ notifyObservers(onSeatsReleased, show, expiredSeats)
  └─ WaitlistNotifier notifies interested users
```

---

## 5️⃣ Design

### Design Pattern 1: Observer Pattern (Primary)

**Why Observer?**
- Multiple independent systems need to react to booking events
- Email, SMS, Waitlist, Analytics — all need to know about bookings
- Loose coupling: BookingService doesn't know about notification details

```
BookingService (Subject)
  │
  ├── List<BookingObserver> observers
  │
  ├── onBookingConfirmed(booking)
  │     ├── EmailNotifier.onBookingConfirmed(booking)
  │     ├── SMSNotifier.onBookingConfirmed(booking)
  │     └── AnalyticsTracker.onBookingConfirmed(booking)
  │
  └── onBookingCancelled(booking)
        ├── EmailNotifier.onBookingCancelled(booking)
        └── WaitlistNotifier.onBookingCancelled(booking)  ← notify waitlisted users
```

### Design Pattern 2: Singleton (BookingService)

Single entry point for all booking operations, manages global state.

### Design Pattern 3: Two-Phase Booking (Hold → Confirm)

**Why not direct booking?**
- User needs time to enter payment details
- Prevents seat being taken while user is paying
- Hold timeout prevents indefinite seat locking
- Industry standard (theaters, airlines, events)

### Concurrency Strategy: ReentrantLock per Show

```java
class Show {
    private final ReentrantLock lock = new ReentrantLock();
    
    public boolean holdSeats(List<String> seatIds, String userId) {
        lock.lock();
        try {
            // Check ALL seats available first (atomic check)
            for (String seatId : seatIds) {
                if (getSeat(seatId).getStatus() != SeatStatus.AVAILABLE)
                    throw new SeatNotAvailableException(seatId);
            }
            // All available — hold them
            for (String seatId : seatIds) {
                Seat seat = getSeat(seatId);
                seat.setStatus(SeatStatus.HELD);
                seat.setHeldBy(userId);
                seat.setHeldAt(System.currentTimeMillis());
            }
            return true;
        } finally {
            lock.unlock();
        }
    }
}
```

**Why ReentrantLock over synchronized?**
- `tryLock(timeout)` prevents deadlocks
- `lockInterruptibly()` allows thread interruption
- Fair locking option: `new ReentrantLock(true)`
- Per-show locking (not global) → shows are independent

### Data Structure Choices

| Structure | Purpose | Why |
|-----------|---------|-----|
| `Seat[][]` | Seat grid per show | O(1) access by row/col, matches theater layout |
| `Map<String, Seat>` | Seat lookup by ID | O(1) lookup for holdSeats() |
| `Map<String, Booking>` | Active bookings | O(1) cancel/lookup by bookingId |
| `List<BookingObserver>` | Observer list | Iterate all observers on events |
| `ConcurrentHashMap` | Thread-safe maps | No global lock needed for lookups |

---

### Complete Implementation

```java
// See MovieTicketBookingSystem.java for full runnable code
```

The complete implementation includes:
- All entity classes (Movie, Screen, Show, Seat, Booking)
- Observer interface + Email/SMS/Waitlist notifiers
- BookingService with thread-safe hold/confirm/cancel
- Timer-based hold expiry cleanup
- Demo with concurrent booking scenarios

---

## 6️⃣ Deep Dives

### Deep Dive 1: Why Observer over Direct Notification?

| Approach | Pros | Cons |
|----------|------|------|
| **Direct calls** | Simple, explicit | Tight coupling, hard to add new notifiers |
| **Observer** | Loose coupling, easy to extend | Slight indirection |
| **Event Bus** | Fully decoupled, async | Over-engineered for single-process |

Observer is the sweet spot: BookingService doesn't import/know about EmailNotifier. Adding PushNotifier requires zero changes to BookingService.

### Deep Dive 2: Preventing Double Booking

**Three layers of protection:**

1. **Status Check** — `seat.status == AVAILABLE` before hold
2. **Lock** — `ReentrantLock` per show ensures atomicity
3. **Atomic All-or-Nothing** — Check ALL seats first, then hold ALL

```
Without atomic check:
  Hold A1 ✓, Hold A2 ✗ → A1 held but A2 not = inconsistent!

With atomic check:
  Check A1 ✓, Check A2 ✗ → throw exception, nothing held ✓
```

### Deep Dive 3: Hold Timeout Mechanism

**Approach 1: Lazy cleanup** (simpler)
- Check timestamp when someone queries seat status
- If `now - heldAt > timeout` → treat as AVAILABLE

**Approach 2: Active cleanup** (our approach)
- Background `ScheduledExecutorService` runs every 30s
- Scans all shows, releases expired holds
- Notifies observers (waitlist users)

**Trade-off**: Active is better for user experience (waitlisted users get notified promptly).

### Deep Dive 4: Scaling Considerations

For production BookMyShow-scale:
- **Database**: Seat status in DB with optimistic locking (`version` column)
- **Distributed Lock**: Redis SETNX for cross-server seat holds
- **Event-Driven**: Kafka for booking events instead of in-process observers
- **CQRS**: Separate read model for seat availability (fast queries)

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| User holds seats but never confirms | Timer releases after 5 min |
| User tries to hold already-held seat | SeatNotAvailableException |
| User cancels non-existent booking | BookingNotFoundException |
| Two users confirm same held seats | Only holder's userId matches |
| Show has 0 available seats | getAvailableSeats returns empty list |
| Seat ID doesn't exist | SeatNotFoundException |
| Concurrent hold + cancel on same seat | Lock ensures serial execution |

### Deep Dive 6: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| Get available seats | O(R×C) | O(R×C) |
| Hold seats (k seats) | O(k) | O(1) |
| Confirm booking | O(k) | O(1) per booking |
| Cancel booking | O(k) | O(1) |
| Cleanup expired holds | O(S×R×C) | O(1) |
| Notify observers | O(O) where O = observers | O(1) |

Where R=rows, C=cols, S=shows, k=seats in booking

---

## 📋 Interview Checklist

- [ ] **Requirements**: Scoped to seat selection + booking, asked about concurrency
- [ ] **Observer Pattern**: Explained why — decoupled notifications
- [ ] **Two-Phase Booking**: Hold → Confirm with timeout
- [ ] **Thread Safety**: ReentrantLock per show, atomic all-or-nothing
- [ ] **Data Structures**: Seat grid O(1), ConcurrentHashMap for lookups
- [ ] **Edge Cases**: Double booking, hold expiry, concurrent conflicts
- [ ] **Extensibility**: Add notifier without changing BookingService
