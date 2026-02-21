# Design a Ticket Booking System (Ticketmaster) - Hello Interview Framework

> **Question**: Design a ticket booking system like Ticketmaster that handles high-traffic events, supports both seated and general admission tickets, and manages scenarios like flash sales with limited inventory and concurrent users.
>
> **Asked at**: Meta, Microsoft, Google, Amazon, Uber, Stripe
>
> **Difficulty**: Hard | **Level**: Senior

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Browse Events**: Users discover events by category, date, location, and artist.
2. **View Seat Map / Availability**: Show available seats (for seated events) or remaining quantity (general admission).
3. **Reserve Tickets (Temporary Hold)**: Place a short-lived hold on selected seats (5-10 minutes) to prevent double-selling while user completes checkout.
4. **Purchase Tickets**: Process payment and issue digital tickets. Release hold if payment fails.
5. **Receive Digital Tickets**: After purchase, user gets ticket with QR code (email + in-app).

#### Nice to Have (P1)
- Waiting room / virtual queue for high-demand events (Taylor Swift scenario).
- Resale marketplace (transfer / sell tickets to others).
- Presale codes (early access for fan clubs, credit card holders).
- Dynamic pricing (prices adjust based on demand).
- Refund / cancellation support.

#### Below the Line (Out of Scope)
- Event creation and management (admin console).
- Venue management (seat map design tool).
- Anti-bot / CAPTCHA systems (assume separate fraud layer).
- Social features (invite friends, share events).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Throughput** | 500K concurrent users per popular event launch | Taylor Swift / BTS flash sale scenario |
| **Hold Duration** | 5-10 minutes per reservation | Enough time for checkout, not too long to block inventory |
| **Consistency** | No double-selling (linearizable for seat assignment) | Selling the same seat twice = lawsuit + refund + reputation damage |
| **Availability** | 99.99% for browsing; 99.9% for booking | Browsing must always work; booking can degrade gracefully |
| **Latency** | < 200ms for availability check; < 2s for reservation | Real-time seat map; reservation must feel instant |
| **Fairness** | First-come-first-served (with virtual queue for flash sales) | Users expect fair access; queue prevents server crush |
| **Durability** | No confirmed tickets lost | Financial transaction; must be durable |
| **Scalability** | 100K events, 1M seats per venue, 500M total tickets/year | Ticketmaster-scale platform |

### Capacity Estimation

```
Events & Tickets:
  Active events: 100K
  Average seats per event: 10K (range: 100 to 100K)
  Total inventory: 100K × 10K = 1B seats
  Tickets sold per year: 500M
  Tickets sold per day: 1.4M
  Peak: flash sale = 500K users hitting one event simultaneously

Traffic:
  Browsing: 10M page views/day = ~120 QPS (sustained)
  Seat availability: 1M checks/day = ~12 QPS (sustained)
  Flash sale peak: 500K users × 5 requests each = 2.5M requests in 5 minutes
    = ~8,300 QPS (burst)
  
  Reservation attempts during flash: 500K users → only 50K seats
    = 10:1 oversubscription ratio

Storage:
  Per ticket record: ~500 bytes (event, seat, user, status, QR code ref, payment)
  500M tickets/year × 500 bytes = 250 GB/year (trivial)
  Seat map per event: 100K seats × 100 bytes = 10 MB per event
  Total seat maps: 100K events × 10 MB = 1 TB (fits on SSD)
```

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│    Event     │ 1─* │    Section   │ 1─* │     Seat     │
├──────────────┤     ├──────────────┤     ├──────────────┤
│ event_id     │     │ section_id   │     │ seat_id      │
│ title        │     │ event_id     │     │ section_id   │
│ venue        │     │ name         │     │ row          │
│ date_time    │     │ price_tier   │     │ number       │
│ status       │     │ capacity     │     │ status       │
└──────────────┘     └──────────────┘     │ held_by      │
                                          │ held_until   │
       ┌──────────────┐                   └──────────────┘
       │  Reservation │
       ├──────────────┤
       │ reservation_id│
       │ user_id       │
       │ event_id      │
       │ seat_ids[]    │
       │ status        │  HELD → CONFIRMED → (CANCELLED)
       │ held_until    │
       │ total_price   │
       │ payment_id    │
       └──────────────┘
```

### Entity 1: Seat
```sql
CREATE TABLE seats (
    seat_id         UUID PRIMARY KEY,
    event_id        UUID NOT NULL REFERENCES events(event_id),
    section_id      UUID NOT NULL REFERENCES sections(section_id),
    row_label       VARCHAR(5),          -- "A", "B", "AA"
    seat_number     INT,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
        -- AVAILABLE, HELD, SOLD, BLOCKED
    price_cents     INT NOT NULL,
    held_by         UUID,                -- user_id who holds it
    held_until      TIMESTAMPTZ,         -- when hold expires
    sold_to         UUID,                -- user_id who bought it
    version         INT NOT NULL DEFAULT 0  -- optimistic locking
);

CREATE INDEX idx_seats_event_status ON seats (event_id, status);
CREATE INDEX idx_seats_held_until ON seats (held_until) WHERE status = 'HELD';
```

### Entity 2: Reservation
```sql
CREATE TABLE reservations (
    reservation_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    event_id        UUID NOT NULL,
    seat_ids        UUID[] NOT NULL,     -- Array of reserved seat_ids
    status          VARCHAR(20) NOT NULL DEFAULT 'HELD',
        -- HELD, PAYMENT_PENDING, CONFIRMED, EXPIRED, CANCELLED
    total_price_cents INT NOT NULL,
    held_until      TIMESTAMPTZ NOT NULL,
    payment_id      UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ,
    version         INT NOT NULL DEFAULT 0
);
```

### Entity 3: Ticket (issued after payment)
```java
public class Ticket {
    private final UUID ticketId;
    private final UUID reservationId;
    private final UUID userId;
    private final UUID eventId;
    private final UUID seatId;
    private final String qrCode;           // Unique QR code for venue entry
    private final TicketStatus status;     // ACTIVE, USED, CANCELLED, TRANSFERRED
    private final Instant issuedAt;
}
```

---

## 3️⃣ API Design

### 1. Get Available Seats
```
GET /api/v1/events/{event_id}/seats?section=VIP&status=AVAILABLE

Response (200 OK):
{
  "event_id": "evt_123",
  "sections": [
    {
      "section_id": "sec_VIP",
      "name": "VIP Floor",
      "price_cents": 35000,
      "available_count": 42,
      "seats": [
        { "seat_id": "seat_001", "row": "A", "number": 1, "status": "AVAILABLE" },
        { "seat_id": "seat_002", "row": "A", "number": 2, "status": "AVAILABLE" },
        { "seat_id": "seat_005", "row": "A", "number": 5, "status": "HELD" }
      ]
    }
  ],
  "total_available": 4200,
  "last_updated": "2025-01-10T19:30:00Z"
}
```

### 2. Reserve Seats (Create Hold)
```
POST /api/v1/reservations

Headers:
  Authorization: Bearer <JWT>
  X-Idempotency-Key: "client-uuid-123"

Request:
{
  "event_id": "evt_123",
  "seat_ids": ["seat_001", "seat_002"],
  "hold_duration_minutes": 10
}

Response (201 Created):
{
  "reservation_id": "res_abc",
  "status": "HELD",
  "seats": [
    { "seat_id": "seat_001", "row": "A", "number": 1, "price_cents": 35000 },
    { "seat_id": "seat_002", "row": "A", "number": 2, "price_cents": 35000 }
  ],
  "total_price_cents": 70000,
  "held_until": "2025-01-10T19:40:00Z",
  "checkout_url": "/checkout/res_abc"
}

Response (409 Conflict — seats already taken):
{
  "error": "SEATS_UNAVAILABLE",
  "message": "One or more selected seats are no longer available",
  "unavailable_seats": ["seat_001"]
}
```

### 3. Confirm Purchase (Process Payment)
```
POST /api/v1/reservations/{reservation_id}/confirm

Request:
{
  "payment_method_id": "pm_stripe_123",
  "billing_address": { ... }
}

Response (200 OK):
{
  "reservation_id": "res_abc",
  "status": "CONFIRMED",
  "tickets": [
    { "ticket_id": "tkt_001", "seat": "A1", "qr_code": "QR_DATA_HERE" },
    { "ticket_id": "tkt_002", "seat": "A2", "qr_code": "QR_DATA_HERE" }
  ],
  "receipt_url": "/receipts/res_abc"
}
```

### 4. Release Reservation (Cancel Hold)
```
DELETE /api/v1/reservations/{reservation_id}

Response (200 OK):
{
  "reservation_id": "res_abc",
  "status": "CANCELLED",
  "seats_released": ["seat_001", "seat_002"]
}
```

---

## 4️⃣ Data Flow

### Flow 1: Reserve Seats (Hold)
```
1. User selects seats A1, A2 → POST /api/v1/reservations
   ↓
2. Reservation Service:
   a. Validate: event is active, seats exist, user is authenticated
   b. Acquire distributed lock on event_id (Redis SETNX, 5s TTL)
   c. Check seat status: SELECT status FROM seats WHERE seat_id IN (...) FOR UPDATE
   d. If ANY seat is not AVAILABLE → return 409 Conflict
   e. Update seats: status = HELD, held_by = user_id, held_until = NOW() + 10 min
   f. Create reservation: status = HELD
   g. Release lock
   h. Schedule hold expiration timer (10 min)
   i. Return 201 Created with reservation details
   ↓
3. Hold Expiration Service (runs every 30 seconds):
   a. SELECT * FROM seats WHERE status = 'HELD' AND held_until < NOW()
   b. For each expired hold: SET status = 'AVAILABLE', held_by = NULL
   c. Update reservation: status = EXPIRED
   ↓
4. Total latency: < 500ms (DB transaction + Redis lock)
```

### Flow 2: Confirm Purchase
```
1. User clicks "Pay" → POST /api/v1/reservations/{id}/confirm
   ↓
2. Reservation Service:
   a. Validate reservation: status = HELD, held_until > NOW()
   b. Update reservation: status = PAYMENT_PENDING
   ↓
3. Payment Service:
   a. Charge payment method via Stripe/PayPal
   b. If payment fails → release hold, return 402
   c. If payment succeeds → continue
   ↓
4. Reservation Service:
   a. Update seats: status = SOLD, sold_to = user_id
   b. Update reservation: status = CONFIRMED, payment_id = pay_123
   c. Generate tickets with unique QR codes
   d. Publish "tickets-issued" event to Kafka
   ↓
5. Notification Service (async):
   a. Email confirmation + digital tickets (PDF)
   b. Push notification to mobile app
   ↓
6. Total latency: 1-3 seconds (payment processing dominates)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                   │
│   [Web App]        [Mobile App]        [API Partners]                 │
│   Browse → Select Seats → Hold → Pay → Receive Tickets               │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │  API Gateway / LB        │
              │  • Auth, Rate Limiting   │
              │  • Virtual Queue (flash) │
              └────────────┬────────────┘
                           │
         ┌─────────────────┼────────────────────┐
         ▼                 ▼                     ▼
┌─────────────────┐ ┌────────────────┐  ┌────────────────┐
│  Event Service   │ │ Reservation    │  │  Payment       │
│  • Browse events │ │ Service        │  │  Service       │
│  • Seat map      │ │ • Hold seats   │  │  • Stripe/PP   │
│  • Availability  │ │ • Confirm buy  │  │  • Idempotent  │
│                  │ │ • Cancel hold  │  │  • Refunds     │
└───────┬─────────┘ └───────┬────────┘  └───────┬────────┘
        │                   │                    │
        ▼                   ▼                    ▼
┌──────────────────────────────────────────────────────────┐
│                    REDIS CLUSTER                          │
│  • Distributed locks (seat reservation)                  │
│  • Seat availability cache (fast reads)                  │
│  • Virtual queue state (flash sales)                     │
│  • Hold expiration tracking                              │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│               POSTGRESQL (Sharded)                        │
│  • Events, Sections, Seats (source of truth)             │
│  • Reservations, Tickets                                 │
│  • Optimistic locking (version column)                   │
│  Shard key: event_id                                     │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│                    KAFKA                                   │
│  "reservation-events" → Notification Service              │
│  "payment-events" → Analytics, Accounting                 │
│  "hold-expired" → Seat release                           │
└──────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Event Service** | Browse events, seat maps, availability | Java/Spring Boot | Stateless, CDN-cached |
| **Reservation Service** | Hold seats, confirm purchase, cancel | Java/Spring Boot | Stateless, 50+ pods |
| **Payment Service** | Process payments, refunds | Java/Spring Boot | Stateless, idempotent |
| **Redis** | Locks, availability cache, queue state | Redis Cluster | 6-10 nodes |
| **PostgreSQL** | Source of truth: events, seats, reservations | PostgreSQL 16 | Sharded by event_id |
| **Kafka** | Event stream for async processing | Apache Kafka | 50+ partitions |
| **Notification Service** | Email, push, SMS for confirmations | Java | Async, 20+ pods |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Seat Reservation — Preventing Double-Selling

**The Problem**: Two users click on seat A1 at the same time. Both read it as AVAILABLE. Both try to reserve it. Without proper concurrency control, both succeed → double-sold seat.

**Solution**: Optimistic locking with `SELECT ... FOR UPDATE` + Redis distributed lock for hot events.

```java
public class ReservationService {

    /**
     * Reserve seats for a user. Must be atomic and prevent double-selling.
     * 
     * Strategy:
     *   1. Redis distributed lock (event-level, prevents thundering herd)
     *   2. PostgreSQL SELECT FOR UPDATE (row-level, prevents double-sell)
     *   3. Optimistic version check (catches any edge cases)
     * 
     * Belt and suspenders: Redis lock for performance, DB lock for correctness.
     */
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResult reserveSeats(UUID userId, UUID eventId, 
                                           List<UUID> seatIds, Duration holdDuration) {
        // Step 1: Redis lock (coarse-grained, prevents thundering herd on hot events)
        String lockKey = "event_lock:" + eventId;
        RLock lock = redisson.getLock(lockKey);
        
        if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
            throw new ServiceUnavailableException("Event is experiencing high demand. Please retry.");
        }
        
        try {
            // Step 2: DB-level pessimistic lock on specific seats
            List<Seat> seats = jdbc.query("""
                SELECT seat_id, status, version FROM seats 
                WHERE seat_id = ANY(?) AND event_id = ?
                FOR UPDATE NOWAIT
                """, seatIds.toArray(UUID[]::new), eventId);
            
            // Step 3: Verify ALL seats are available
            for (Seat seat : seats) {
                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    throw new SeatUnavailableException(
                        "Seat " + seat.getRow() + seat.getNumber() + " is no longer available");
                }
            }
            
            if (seats.size() != seatIds.size()) {
                throw new SeatNotFoundException("One or more seats not found");
            }
            
            // Step 4: Create hold
            Instant holdUntil = Instant.now().plus(holdDuration);
            
            // Update all seats atomically
            jdbc.batchUpdate("""
                UPDATE seats SET status = 'HELD', held_by = ?, held_until = ?, 
                                 version = version + 1
                WHERE seat_id = ? AND version = ?
                """,
                seats.stream().map(s -> new Object[]{
                    userId, holdUntil, s.getSeatId(), s.getVersion()
                }).toList());
            
            // Create reservation record
            int totalPrice = seats.stream().mapToInt(Seat::getPriceCents).sum();
            
            UUID reservationId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO reservations (reservation_id, user_id, event_id, seat_ids,
                    status, total_price_cents, held_until)
                VALUES (?, ?, ?, ?, 'HELD', ?, ?)
                """, reservationId, userId, eventId, 
                seatIds.toArray(UUID[]::new), totalPrice, holdUntil);
            
            // Step 5: Schedule hold expiration
            holdExpirationScheduler.scheduleExpiration(reservationId, holdUntil);
            
            // Step 6: Invalidate availability cache
            redis.del("availability:" + eventId);
            
            return ReservationResult.success(reservationId, seats, holdUntil, totalPrice);
            
        } finally {
            lock.unlock();
        }
    }
}
```

**Why Both Redis Lock AND DB Lock?**
```
Redis distributed lock (coarse):
  ✓ Prevents 500K users from all hitting the DB simultaneously
  ✓ Only one reservation transaction per event at a time
  ✗ If Redis fails, we still need correctness → DB lock is backup

PostgreSQL FOR UPDATE (fine):
  ✓ Row-level locking → only locks the specific seats
  ✓ Serialized access → no double-sell even without Redis
  ✗ Under extreme load (500K QPS), DB becomes bottleneck → Redis helps

Together: Redis absorbs the thundering herd; DB guarantees correctness.
```

---

### Deep Dive 2: Hold Expiration — Releasing Unpurchased Seats

**The Problem**: User holds seats A1, A2 but never completes payment. Those seats are locked for 10 minutes while other users can't buy them. We need reliable, automatic expiration.

```java
public class HoldExpirationService {

    /**
     * Two-tier hold expiration:
     * 
     * Tier 1: Redis key expiration (fast, approximate)
     *   - Set Redis key with TTL = hold duration
     *   - On expiry: Keyspace notification triggers release
     * 
     * Tier 2: DB polling (reliable, catches missed expirations)
     *   - Every 30 seconds: scan for expired holds
     *   - Releases any holds where held_until < NOW()
     */
    
    /** Tier 1: Redis-based expiration (reactive) */
    public void scheduleExpiration(UUID reservationId, Instant holdUntil) {
        long ttlSeconds = Duration.between(Instant.now(), holdUntil).getSeconds();
        
        // Set a Redis key that expires when the hold should
        String key = "hold:" + reservationId;
        redis.setex(key, (int) ttlSeconds, reservationId.toString());
        
        // Redis keyspace notification will call onHoldExpired when key expires
    }
    
    /** Called by Redis keyspace notification listener */
    @RedisKeyspaceListener(pattern = "__keyevent@0__:expired")
    public void onHoldExpired(String expiredKey) {
        if (!expiredKey.startsWith("hold:")) return;
        
        UUID reservationId = UUID.fromString(expiredKey.substring("hold:".length()));
        releaseHold(reservationId, "REDIS_EXPIRY");
    }
    
    /** Tier 2: DB polling (defensive — catches any missed expirations) */
    @Scheduled(fixedRate = 30_000) // Every 30 seconds
    public void pollExpiredHolds() {
        List<UUID> expiredReservations = jdbc.queryForList("""
            SELECT reservation_id FROM reservations
            WHERE status = 'HELD' AND held_until < NOW()
            LIMIT 1000
            """, UUID.class);
        
        for (UUID reservationId : expiredReservations) {
            releaseHold(reservationId, "DB_POLL");
        }
        
        if (!expiredReservations.isEmpty()) {
            log.info("Released {} expired holds via DB polling", expiredReservations.size());
        }
    }
    
    /** Release a held reservation and make seats available again */
    @Transactional
    public void releaseHold(UUID reservationId, String trigger) {
        // Atomic: only release if still HELD (idempotent)
        int updated = jdbc.update("""
            UPDATE reservations SET status = 'EXPIRED'
            WHERE reservation_id = ? AND status = 'HELD'
            """, reservationId);
        
        if (updated == 0) return; // Already released or confirmed
        
        // Get seat IDs from reservation
        UUID[] seatIds = jdbc.queryForObject("""
            SELECT seat_ids FROM reservations WHERE reservation_id = ?
            """, UUID[].class, reservationId);
        
        // Release seats
        jdbc.update("""
            UPDATE seats SET status = 'AVAILABLE', held_by = NULL, held_until = NULL
            WHERE seat_id = ANY(?) AND status = 'HELD'
            """, (Object) seatIds);
        
        // Invalidate cache
        UUID eventId = getEventIdForReservation(reservationId);
        redis.del("availability:" + eventId);
        
        // Publish event (notify waiting users that seats became available)
        kafka.send(new ProducerRecord<>("hold-expired", eventId.toString(),
            HoldExpiredEvent.builder()
                .reservationId(reservationId)
                .eventId(eventId)
                .seatIds(Arrays.asList(seatIds))
                .trigger(trigger)
                .build()));
        
        metrics.counter("hold.expired", "trigger", trigger).increment();
    }
}
```

**Why Two Tiers?**
```
Redis expiration (Tier 1):
  ✓ Fires within 1-2 seconds of hold_until
  ✗ Not guaranteed (Redis crash, memory pressure → eviction)
  ✗ Keyspace notifications are best-effort

DB polling (Tier 2):
  ✓ Guaranteed to catch ALL expired holds (every 30 seconds)
  ✗ Up to 30-second delay
  ✗ Adds DB load (mitigated by LIMIT 1000 + index on held_until)

Together: Redis handles 99% of expirations instantly.
DB polling catches the 1% that Redis missed.
```

---

### Deep Dive 3: Virtual Queue — Flash Sale Fairness

**The Problem**: Taylor Swift tickets go on sale. 500K users hit "Buy" at 10:00:00 AM. Without a queue, the servers crash. With a queue, users wait their turn fairly.

```java
public class VirtualQueueService {

    /**
     * Virtual queue for high-demand events.
     * 
     * Architecture:
     *   1. When a flash sale event is about to start, enable queue mode
     *   2. All users are placed in a FIFO queue (Redis sorted set, score = join time)
     *   3. Users are admitted in batches (e.g., 1000 every 10 seconds)
     *   4. Admitted users get a time-limited "queue token" to access the booking page
     *   5. Token expires after 5 minutes (if they don't complete booking)
     */
    
    private static final int BATCH_SIZE = 1000;
    private static final Duration BATCH_INTERVAL = Duration.ofSeconds(10);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    
    /** User joins the queue */
    public QueuePosition joinQueue(UUID userId, UUID eventId) {
        String queueKey = "queue:" + eventId;
        
        // Add to sorted set (score = current time in millis)
        double score = Instant.now().toEpochMilli();
        redis.zadd(queueKey, score, userId.toString());
        
        // Get position in queue
        Long rank = redis.zrank(queueKey, userId.toString());
        long totalInQueue = redis.zcard(queueKey);
        
        // Estimate wait time
        long batchesAhead = rank / BATCH_SIZE;
        Duration estimatedWait = BATCH_INTERVAL.multipliedBy(batchesAhead);
        
        return QueuePosition.builder()
            .userId(userId).eventId(eventId)
            .position(rank + 1)
            .totalInQueue(totalInQueue)
            .estimatedWaitMinutes(estimatedWait.toMinutes())
            .build();
    }
    
    /** Admit next batch of users (runs on timer) */
    @Scheduled(fixedRate = 10_000) // Every 10 seconds
    public void admitNextBatch() {
        for (String queueKey : getActiveQueues()) {
            UUID eventId = extractEventId(queueKey);
            
            // Pop first BATCH_SIZE users from queue
            Set<String> admitted = redis.zpopmin(queueKey, BATCH_SIZE).stream()
                .map(Tuple::getElement)
                .collect(Collectors.toSet());
            
            if (admitted.isEmpty()) continue;
            
            // Issue queue tokens for admitted users
            for (String userIdStr : admitted) {
                String tokenKey = "queue_token:" + eventId + ":" + userIdStr;
                String token = UUID.randomUUID().toString();
                redis.setex(tokenKey, (int) TOKEN_TTL.getSeconds(), token);
                
                // Notify user via WebSocket
                webSocketService.sendToUser(UUID.fromString(userIdStr),
                    QueueAdmittedEvent.builder()
                        .eventId(eventId)
                        .token(token)
                        .expiresAt(Instant.now().plus(TOKEN_TTL))
                        .message("It's your turn! You have 5 minutes to select seats.")
                        .build());
            }
            
            log.info("Admitted {} users for event {}", admitted.size(), eventId);
            metrics.counter("queue.admitted").increment(admitted.size());
        }
    }
    
    /** Validate queue token before allowing seat selection */
    public boolean validateQueueToken(UUID userId, UUID eventId, String token) {
        String tokenKey = "queue_token:" + eventId + ":" + userId;
        String storedToken = redis.get(tokenKey);
        return token.equals(storedToken);
    }
    
    /** Get user's current position (for polling) */
    public QueuePosition getPosition(UUID userId, UUID eventId) {
        String queueKey = "queue:" + eventId;
        Long rank = redis.zrank(queueKey, userId.toString());
        
        if (rank == null) {
            // Already admitted or not in queue
            String tokenKey = "queue_token:" + eventId + ":" + userId;
            if (redis.exists(tokenKey)) {
                return QueuePosition.admitted();
            }
            return QueuePosition.notInQueue();
        }
        
        long totalInQueue = redis.zcard(queueKey);
        long batchesAhead = rank / BATCH_SIZE;
        Duration estimatedWait =BATCH_INTERVAL.multipliedBy(batchesAhead);
        
        return QueuePosition.builder()
            .position(rank + 1).totalInQueue(totalInQueue)
            .estimatedWaitMinutes(estimatedWait.toMinutes()).build();
    }
}
```

**Virtual Queue UX**:
```
User visits event page at 9:59 AM (sale starts at 10:00)
  → "Join queue" button appears
  → User clicks → placed in queue at position #234,567

10:00:00 → First batch of 1,000 admitted
10:00:10 → Next 1,000 admitted
...
10:03:50 → User at position 234,567 → batch #235 → admitted at ~10:39

User sees:
  "You are #234,567 in queue. Estimated wait: ~39 minutes."
  "It's your turn! Select your seats within 5 minutes."

Fairness guarantee:
  - FIFO ordering (Redis sorted set by timestamp)
  - No advantage from refreshing page (queue position is fixed)
  - Token expires → seats released → next users admitted
```

---

### Deep Dive 4: Payment Processing & Idempotency

**The Problem**: Payment is the most critical step. Network timeouts, double-clicks, and retries can cause double-charges. We need idempotent payment processing.

```java
public class PaymentOrchestrator {

    /**
     * Orchestrate the payment flow with idempotency guarantees.
     * Uses the Stripe/PayPal idempotency key pattern.
     */
    
    @Transactional
    public PaymentResult processPayment(UUID reservationId, String paymentMethodId, 
                                         String idempotencyKey) {
        // Step 1: Check idempotency (have we already processed this exact request?)
        String idemKey = "payment_idem:" + idempotencyKey;
        String existingResult = redis.get(idemKey);
        if (existingResult != null) {
            return objectMapper.readValue(existingResult, PaymentResult.class);
        }
        
        // Step 2: Validate reservation is still held
        Reservation reservation = reservationRepo.findById(reservationId);
        if (reservation.getStatus() != ReservationStatus.HELD) {
            throw new InvalidStateException("Reservation is not in HELD state");
        }
        if (reservation.getHeldUntil().isBefore(Instant.now())) {
            throw new HoldExpiredException("Reservation hold has expired");
        }
        
        // Step 3: Transition to PAYMENT_PENDING (prevents concurrent payment attempts)
        int updated = jdbc.update("""
            UPDATE reservations SET status = 'PAYMENT_PENDING', version = version + 1
            WHERE reservation_id = ? AND status = 'HELD' AND version = ?
            """, reservationId, reservation.getVersion());
        
        if (updated == 0) {
            throw new ConcurrentModificationException("Reservation was modified concurrently");
        }
        
        // Step 4: Charge payment (external call to Stripe)
        try {
            StripeCharge charge = stripe.charges().create(
                ChargeCreateParams.builder()
                    .setAmount(reservation.getTotalPriceCents())
                    .setCurrency("usd")
                    .setPaymentMethod(paymentMethodId)
                    .setIdempotencyKey(idempotencyKey) // Stripe-level idempotency
                    .build());
            
            // Step 5: Confirm reservation
            jdbc.update("""
                UPDATE reservations SET status = 'CONFIRMED', payment_id = ?, confirmed_at = NOW()
                WHERE reservation_id = ?
                """, charge.getId(), reservationId);
            
            // Update seats to SOLD
            jdbc.update("""
                UPDATE seats SET status = 'SOLD', sold_to = ?
                WHERE seat_id = ANY(?) AND status = 'HELD'
                """, reservation.getUserId(), reservation.getSeatIds());
            
            // Generate tickets
            List<Ticket> tickets = generateTickets(reservation);
            
            PaymentResult result = PaymentResult.success(reservationId, charge.getId(), tickets);
            
            // Cache idempotency result (1 hour TTL)
            redis.setex(idemKey, 3600, objectMapper.writeValueAsString(result));
            
            // Publish events
            kafka.send(new ProducerRecord<>("tickets-issued", reservationId.toString(),
                TicketsIssuedEvent.of(reservation, tickets)));
            
            return result;
            
        } catch (StripeException e) {
            // Payment failed → release hold
            releaseReservation(reservationId);
            
            PaymentResult failure = PaymentResult.failure(reservationId, e.getMessage());
            redis.setex(idemKey, 3600, objectMapper.writeValueAsString(failure));
            
            return failure;
        }
    }
}
```

**Payment State Machine**:
```
   HELD ──→ PAYMENT_PENDING ──→ CONFIRMED
     │            │                  │
     │            ▼                  ▼
     └──→ EXPIRED  FAILED ──→ CANCELLED
                    │
                    ▼
              Seats released
              Hold removed
```

---

### Deep Dive 5: Seat Availability Caching — Read Path Optimization

**The Problem**: 500K users refreshing the seat map every 2 seconds = 250K QPS just for availability. We can't hit the DB for every request.

```java
public class SeatAvailabilityCache {

    /**
     * Multi-layer caching for seat availability.
     * 
     * Layer 1: CDN (10-second TTL) — handles 90% of reads
     * Layer 2: Redis (2-second TTL) — real-time availability
     * Layer 3: PostgreSQL — source of truth
     * 
     * Invalidation: on every reservation/release, delete Redis key.
     * CDN invalidation: not needed (10s stale is acceptable for browsing).
     */
    
    public EventAvailability getAvailability(UUID eventId) {
        // Layer 1: Redis cache
        String cacheKey = "availability:" + eventId;
        String cached = redis.get(cacheKey);
        if (cached != null) {
            return objectMapper.readValue(cached, EventAvailability.class);
        }
        
        // Layer 2: DB query (aggregated counts by section)
        List<SectionAvailability> sections = jdbc.query("""
            SELECT s.section_id, sec.name, sec.price_tier,
                   COUNT(*) FILTER (WHERE s.status = 'AVAILABLE') AS available,
                   COUNT(*) FILTER (WHERE s.status = 'HELD') AS held,
                   COUNT(*) FILTER (WHERE s.status = 'SOLD') AS sold,
                   COUNT(*) AS total
            FROM seats s JOIN sections sec ON s.section_id = sec.section_id
            WHERE s.event_id = ?
            GROUP BY s.section_id, sec.name, sec.price_tier
            """, eventId);
        
        EventAvailability availability = EventAvailability.builder()
            .eventId(eventId)
            .sections(sections)
            .totalAvailable(sections.stream().mapToInt(SectionAvailability::getAvailable).sum())
            .lastUpdated(Instant.now())
            .build();
        
        // Cache for 2 seconds
        redis.setex(cacheKey, 2, objectMapper.writeValueAsString(availability));
        
        return availability;
    }
    
    /** Invalidate cache when seats change status */
    public void invalidate(UUID eventId) {
        redis.del("availability:" + eventId);
    }
}
```

---

### Deep Dive 6: General Admission Tickets — Counter-Based Inventory

**The Problem**: General admission (GA) doesn't have specific seats. It's a counter: "500 GA tickets available." Multiple users decrement this counter concurrently.

```java
public class GeneralAdmissionService {

    /**
     * General admission: counter-based inventory instead of seat-based.
     * Uses Redis DECR for atomic counter decrements.
     */
    
    public ReservationResult reserveGA(UUID userId, UUID eventId, 
                                        UUID sectionId, int quantity) {
        String counterKey = "ga_inventory:" + eventId + ":" + sectionId;
        
        // Atomic decrement with check (Lua script for atomicity)
        String luaScript = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local requested = tonumber(ARGV[1])
            if current >= requested then
                redis.call('DECRBY', KEYS[1], requested)
                return current - requested
            else
                return -1
            end
            """;
        
        Long remaining = (Long) redis.eval(luaScript, 
            List.of(counterKey), List.of(String.valueOf(quantity)));
        
        if (remaining < 0) {
            throw new InsufficientInventoryException(
                "Only " + redis.get(counterKey) + " GA tickets remaining");
        }
        
        // Create reservation with hold
        UUID reservationId = UUID.randomUUID();
        Instant holdUntil = Instant.now().plus(Duration.ofMinutes(10));
        
        jdbc.update("""
            INSERT INTO reservations (reservation_id, user_id, event_id, 
                ga_section_id, ga_quantity, status, total_price_cents, held_until)
            VALUES (?, ?, ?, ?, ?, 'HELD', ?, ?)
            """, reservationId, userId, eventId, sectionId, quantity,
            quantity * getPriceCents(sectionId), holdUntil);
        
        // Schedule hold expiration (will re-increment counter)
        holdExpirationScheduler.scheduleGAExpiration(reservationId, counterKey, quantity, holdUntil);
        
        return ReservationResult.success(reservationId, quantity, holdUntil);
    }
    
    /** On hold expiration: re-add tickets to inventory */
    public void releaseGAHold(UUID reservationId, String counterKey, int quantity) {
        int updated = jdbc.update("""
            UPDATE reservations SET status = 'EXPIRED' 
            WHERE reservation_id = ? AND status = 'HELD'
            """, reservationId);
        
        if (updated > 0) {
            redis.incrBy(counterKey, quantity); // Return tickets to pool
            metrics.counter("ga.hold.expired").increment();
        }
    }
    
    /** Initialize GA inventory for a new event */
    public void initializeInventory(UUID eventId, UUID sectionId, int totalTickets) {
        String counterKey = "ga_inventory:" + eventId + ":" + sectionId;
        redis.set(counterKey, String.valueOf(totalTickets));
    }
}
```

---

### Deep Dive 7: Payment Failure Recovery — Saga Pattern

**The Problem**: Payment processing involves multiple steps (reserve → charge → confirm → issue tickets). Any step can fail. We need to handle partial failures gracefully.

```java
public class BookingSaga {

    /**
     * Saga pattern for the booking workflow.
     * Each step has a compensating action that undoes it on failure.
     * 
     * Steps:
     *   1. Reserve seats → compensate: release seats
     *   2. Charge payment → compensate: refund payment
     *   3. Confirm booking → compensate: cancel booking
     *   4. Issue tickets → compensate: void tickets
     */
    
    public BookingResult executeBookingSaga(BookingRequest request) {
        SagaContext ctx = new SagaContext();
        
        try {
            // Step 1: Reserve seats
            ReservationResult reservation = reservationService.reserveSeats(
                request.getUserId(), request.getEventId(), request.getSeatIds(),
                Duration.ofMinutes(10));
            ctx.setReservationId(reservation.getReservationId());
            ctx.addCompensation(() -> reservationService.releaseHold(reservation.getReservationId(), "SAGA_ROLLBACK"));
            
            // Step 2: Process payment
            PaymentResult payment = paymentService.charge(
                reservation.getReservationId(), reservation.getTotalPriceCents(),
                request.getPaymentMethodId(), request.getIdempotencyKey());
            ctx.setPaymentId(payment.getChargeId());
            ctx.addCompensation(() -> paymentService.refund(payment.getChargeId()));
            
            // Step 3: Confirm booking
            confirmationService.confirm(reservation.getReservationId(), payment.getChargeId());
            ctx.addCompensation(() -> confirmationService.cancel(reservation.getReservationId()));
            
            // Step 4: Issue tickets
            List<Ticket> tickets = ticketService.issueTickets(reservation);
            
            return BookingResult.success(reservation, payment, tickets);
            
        } catch (Exception e) {
            log.error("Booking saga failed at step: {}", ctx.getCurrentStep(), e);
            
            // Execute compensating actions in reverse order
            ctx.compensate();
            
            return BookingResult.failure(e.getMessage());
        }
    }
}

public class SagaContext {
    private final Deque<Runnable> compensations = new ArrayDeque<>();
    
    public void addCompensation(Runnable compensation) {
        compensations.push(compensation); // LIFO order
    }
    
    public void compensate() {
        while (!compensations.isEmpty()) {
            try {
                compensations.pop().run();
            } catch (Exception e) {
                log.error("Compensation failed", e);
                // Log for manual resolution
            }
        }
    }
}
```

---

### Deep Dive 8: QR Code Ticket Generation & Validation

```java
public class TicketQRService {

    /**
     * Generate secure QR codes for digital tickets.
     * QR contains a signed token that venue scanners can verify offline.
     */
    
    private final SecretKey signingKey; // HMAC-SHA256 key
    
    public Ticket generateTicket(UUID reservationId, UUID eventId, UUID seatId, UUID userId) {
        UUID ticketId = UUID.randomUUID();
        
        // QR payload: ticketId + eventId + seatId + userId + timestamp
        String payload = String.join("|", ticketId.toString(), eventId.toString(),
            seatId.toString(), userId.toString(), Instant.now().toString());
        
        // Sign payload with HMAC
        String signature = generateHMAC(payload);
        String qrData = payload + "|" + signature;
        
        // Encode as Base64 for QR code
        String qrCode = Base64.getUrlEncoder().encodeToString(qrData.getBytes());
        
        Ticket ticket = Ticket.builder()
            .ticketId(ticketId).reservationId(reservationId)
            .eventId(eventId).seatId(seatId).userId(userId)
            .qrCode(qrCode).status(TicketStatus.ACTIVE)
            .issuedAt(Instant.now()).build();
        
        ticketRepo.save(ticket);
        return ticket;
    }
    
    /** Venue scanner validates QR code */
    public TicketValidation validateTicket(String qrCode) {
        String qrData = new String(Base64.getUrlDecoder().decode(qrCode));
        String[] parts = qrData.split("\\|");
        
        String payload = String.join("|", Arrays.copyOfRange(parts, 0, parts.length - 1));
        String signature = parts[parts.length - 1];
        
        // Verify signature
        if (!generateHMAC(payload).equals(signature)) {
            return TicketValidation.invalid("Invalid signature — possible forgery");
        }
        
        UUID ticketId = UUID.fromString(parts[0]);
        Ticket ticket = ticketRepo.findById(ticketId);
        
        if (ticket == null) return TicketValidation.invalid("Ticket not found");
        if (ticket.getStatus() == TicketStatus.USED) return TicketValidation.invalid("Already scanned");
        if (ticket.getStatus() == TicketStatus.CANCELLED) return TicketValidation.invalid("Cancelled");
        
        // Mark as used (idempotent)
        ticket.setStatus(TicketStatus.USED);
        ticketRepo.save(ticket);
        
        return TicketValidation.valid(ticket);
    }
}
```

---

### Deep Dive 9: Observability & Metrics

```java
public class BookingMetrics {
    Counter reservationAttempts  = Counter.builder("booking.reservation.attempts").register(registry);
    Counter reservationSuccess   = Counter.builder("booking.reservation.success").register(registry);
    Counter reservationConflict  = Counter.builder("booking.reservation.conflict").register(registry);
    Timer   reservationLatency   = Timer.builder("booking.reservation.latency_ms").register(registry);
    Counter holdsExpired         = Counter.builder("booking.holds.expired").register(registry);
    Counter paymentSuccess       = Counter.builder("booking.payment.success").register(registry);
    Counter paymentFailed        = Counter.builder("booking.payment.failed").register(registry);
    Timer   paymentLatency       = Timer.builder("booking.payment.latency_ms").register(registry);
    Gauge   activeHolds          = Gauge.builder("booking.holds.active", ...).register(registry);
    Gauge   queueLength          = Gauge.builder("booking.queue.length", ...).register(registry);
    Counter ticketsIssued        = Counter.builder("booking.tickets.issued").register(registry);
    Gauge   availableSeats       = Gauge.builder("booking.seats.available", ...).register(registry);
}
```

---

### Deep Dive 10: Database Sharding by Event

```java
public class EventShardRouter {
    /**
     * Shard by event_id: all seats, reservations, tickets for one event on same shard.
     * This ensures reservation transactions don't span shards.
     * 
     * 16 shards: handles 100K events × 10K seats = 1B rows
     * Each shard: ~62.5 GB (manageable per-instance)
     */
    private static final int SHARD_COUNT = 16;
    
    public int getShardId(UUID eventId) {
        return Math.abs(eventId.hashCode()) % SHARD_COUNT;
    }
    
    public DataSource getDataSource(UUID eventId) {
        return shardDataSources.get(getShardId(eventId));
    }
}
```

---

### Deep Dive 11: Seat Map Real-Time Updates via WebSocket

```java
public class SeatMapWebSocketService {

    /**
     * Push real-time seat status changes to all users viewing an event's seat map.
     * When a seat is held/sold/released, all connected clients see it immediately.
     */
    
    public void onSeatStatusChanged(UUID eventId, UUID seatId, SeatStatus newStatus) {
        String channel = "seatmap:" + eventId;
        
        SeatUpdateEvent event = SeatUpdateEvent.builder()
            .seatId(seatId).newStatus(newStatus)
            .timestamp(Instant.now()).build();
        
        // Publish to Redis Pub/Sub → all WebSocket servers
        redis.publish(channel, objectMapper.writeValueAsString(event));
    }
    
    @OnMessage
    public void handleSubscription(WebSocketSession session, String eventId) {
        // Subscribe this client to seat map updates for the event
        redisPubSub.subscribe("seatmap:" + eventId, message -> {
            session.sendMessage(new TextMessage(message));
        });
    }
}
```

---

### Deep Dive 12: Presale & Access Control

```java
public class PresaleService {

    /**
     * Presale: early access windows for specific user groups.
     * 
     * Types:
     *   - Fan club presale (Verified Fan program)
     *   - Credit card presale (Amex, Citi)
     *   - Artist presale (presale code required)
     *   - General on-sale (no restrictions)
     * 
     * Each window has: start_time, end_time, access_code (optional), max_tickets_per_user
     */
    
    public boolean canAccess(UUID userId, UUID eventId, String presaleCode) {
        List<PresaleWindow> windows = presaleRepo.findActiveWindows(eventId, Instant.now());
        
        for (PresaleWindow window : windows) {
            if (window.getType() == PresaleType.GENERAL_ONSALE) {
                return true; // General sale is open
            }
            
            if (window.getType() == PresaleType.FAN_CLUB) {
                if (fanClubService.isMember(userId, window.getFanClubId())) return true;
            }
            
            if (window.getType() == PresaleType.PRESALE_CODE) {
                if (presaleCode != null && presaleCode.equals(window.getAccessCode())) return true;
            }
            
            if (window.getType() == PresaleType.CREDIT_CARD) {
                if (paymentService.hasCardBrand(userId, window.getCardBrand())) return true;
            }
        }
        
        return false; // No active window matches
    }
}
```

---

---

### Deep Dive 13: Dynamic Pricing — Demand-Based Ticket Pricing

**The Problem**: Fixed pricing leaves money on the table for hot events and results in unsold seats for cold events. Dynamic pricing adjusts prices based on real-time demand signals.

```java
public class DynamicPricingService {

    /**
     * Adjust ticket prices based on demand signals.
     * 
     * Signals:
     *   1. Sell-through rate: % of seats sold for this section
     *   2. Velocity: seats sold per minute (acceleration)
     *   3. Time to event: prices increase as event date approaches
     *   4. Queue depth: virtual queue length (demand indicator)
     * 
     * Constraints:
     *   - Price can only change between booking sessions (not mid-checkout)
     *   - Minimum/maximum price bounds per section
     *   - Price changes capped at ±20% per adjustment period
     */
    
    private static final double MAX_ADJUSTMENT_PERCENT = 0.20; // ±20% per period
    private static final Duration ADJUSTMENT_PERIOD = Duration.ofMinutes(15);
    
    @Scheduled(fixedRate = 900_000) // Every 15 minutes
    public void adjustPrices() {
        for (Event event : eventRepo.findActiveEvents()) {
            for (Section section : event.getSections()) {
                double demandScore = computeDemandScore(event.getEventId(), section);
                int currentPrice = section.getPriceCents();
                
                // Compute adjustment factor
                double adjustmentFactor;
                if (demandScore > 0.8) {
                    adjustmentFactor = 1.0 + (demandScore - 0.8) * MAX_ADJUSTMENT_PERCENT / 0.2;
                } else if (demandScore < 0.3) {
                    adjustmentFactor = 1.0 - (0.3 - demandScore) * MAX_ADJUSTMENT_PERCENT / 0.3;
                } else {
                    adjustmentFactor = 1.0; // Neutral zone, no change
                }
                
                // Clamp to ±20%
                adjustmentFactor = Math.max(1.0 - MAX_ADJUSTMENT_PERCENT, 
                    Math.min(1.0 + MAX_ADJUSTMENT_PERCENT, adjustmentFactor));
                
                int newPrice = (int) (currentPrice * adjustmentFactor);
                newPrice = Math.max(section.getMinPriceCents(), 
                    Math.min(section.getMaxPriceCents(), newPrice));
                
                if (newPrice != currentPrice) {
                    // Update price for AVAILABLE seats only (held/sold keep their price)
                    jdbc.update("""
                        UPDATE seats SET price_cents = ? 
                        WHERE section_id = ? AND status = 'AVAILABLE'
                        """, newPrice, section.getSectionId());
                    
                    section.setPriceCents(newPrice);
                    sectionRepo.save(section);
                    
                    // Invalidate availability cache (prices changed)
                    redis.del("availability:" + event.getEventId());
                    
                    log.info("Price adjusted for {}/{}: {} → {} (demand={:.2f})",
                        event.getTitle(), section.getName(), currentPrice, newPrice, demandScore);
                }
            }
        }
    }
    
    /**
     * Compute demand score (0.0 = no demand, 1.0 = extreme demand).
     */
    private double computeDemandScore(UUID eventId, Section section) {
        // Factor 1: Sell-through rate (0-1)
        long available = jdbc.queryForObject("""
            SELECT COUNT(*) FROM seats WHERE section_id = ? AND status = 'AVAILABLE'
            """, Long.class, section.getSectionId());
        double sellThrough = 1.0 - ((double) available / section.getCapacity());
        
        // Factor 2: Velocity (seats sold in last 15 min / capacity)
        long recentSold = jdbc.queryForObject("""
            SELECT COUNT(*) FROM reservations r
            JOIN LATERAL unnest(r.seat_ids) AS sid ON true
            JOIN seats s ON s.seat_id = sid
            WHERE s.section_id = ? AND r.status = 'CONFIRMED'
              AND r.confirmed_at > NOW() - INTERVAL '15 minutes'
            """, Long.class, section.getSectionId());
        double velocity = Math.min(1.0, (double) recentSold / Math.max(section.getCapacity() * 0.05, 1));
        
        // Factor 3: Queue depth (if virtual queue is active)
        long queueDepth = redis.zcard("queue:" + eventId);
        double queueFactor = Math.min(1.0, queueDepth / 100_000.0);
        
        // Factor 4: Time to event (closer = higher demand multiplier)
        Event event = eventRepo.findById(eventId);
        long daysToEvent = Duration.between(Instant.now(), event.getDateTime()).toDays();
        double timeFactor = daysToEvent < 1 ? 1.0 : daysToEvent < 7 ? 0.7 : 0.4;
        
        // Weighted combination
        return sellThrough * 0.3 + velocity * 0.3 + queueFactor * 0.2 + timeFactor * 0.2;
    }
}
```

**Pricing Examples**:
```
Taylor Swift concert (Section: Floor):
  Base price: $350
  Demand score: 0.95 (95% sold, long queue, 2 days away)
  Adjustment: +20% → $420 (at price cap)

Local band concert (Section: Balcony):
  Base price: $50
  Demand score: 0.15 (only 20% sold, no queue, 3 weeks away)
  Adjustment: -10% → $45 (reduce to drive sales)

Rules:
  - Price locked when user creates hold (won't change during checkout)
  - Max ±20% per 15-minute adjustment cycle
  - Floor and ceiling prices set by event organizer
```

---

### Deep Dive 14: Ticket Transfer & Resale Marketplace

**The Problem**: Users who can't attend want to sell/transfer tickets. We need secure transfer that prevents fraud (duplicate tickets, price gouging) while enabling a liquid secondary market.

```java
public class TicketTransferService {

    /**
     * Ticket transfer flow:
     *   1. Seller lists ticket for transfer (with or without price)
     *   2. Buyer claims the listing
     *   3. Original ticket QR code is invalidated
     *   4. New ticket QR code is generated for the buyer
     *   5. Payment settled between parties (if resale)
     */
    
    @Transactional
    public TransferResult transferTicket(UUID ticketId, UUID sellerId, 
                                          UUID buyerId, Integer resalePriceCents) {
        Ticket ticket = ticketRepo.findById(ticketId);
        
        // Validate
        if (ticket.getUserId() != sellerId) {
            throw new UnauthorizedException("Only the ticket owner can transfer");
        }
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new InvalidStateException("Ticket is not active");
        }
        
        // Check event's transfer policy
        Event event = eventRepo.findById(ticket.getEventId());
        if (!event.isTransferable()) {
            throw new PolicyViolationException("This event does not allow ticket transfers");
        }
        if (resalePriceCents != null && event.getMaxResalePercent() > 0) {
            int maxPrice = (int) (ticket.getOriginalPriceCents() * (1 + event.getMaxResalePercent() / 100.0));
            if (resalePriceCents > maxPrice) {
                throw new PolicyViolationException(
                    "Resale price exceeds maximum allowed (" + maxPrice + " cents)");
            }
        }
        
        // Step 1: Void original ticket (QR code no longer valid)
        ticket.setStatus(TicketStatus.TRANSFERRED);
        ticket.setTransferredAt(Instant.now());
        ticket.setTransferredTo(buyerId);
        ticketRepo.save(ticket);
        
        // Step 2: Generate new ticket for buyer with new QR code
        Ticket newTicket = ticketQRService.generateTicket(
            ticket.getReservationId(), ticket.getEventId(), 
            ticket.getSeatId(), buyerId);
        newTicket.setOriginalPriceCents(ticket.getOriginalPriceCents());
        newTicket.setTransferredFrom(sellerId);
        ticketRepo.save(newTicket);
        
        // Step 3: Handle payment (if resale)
        if (resalePriceCents != null && resalePriceCents > 0) {
            PaymentResult payment = paymentService.transferPayment(
                buyerId, sellerId, resalePriceCents,
                event.getResaleFeePercent()); // Platform takes % fee
            
            return TransferResult.resale(newTicket, payment);
        }
        
        return TransferResult.freeTransfer(newTicket);
    }
    
    /** List a ticket for resale */
    public ResaleListing listForResale(UUID ticketId, UUID sellerId, int askingPriceCents) {
        Ticket ticket = ticketRepo.findById(ticketId);
        
        ResaleListing listing = ResaleListing.builder()
            .listingId(UUID.randomUUID())
            .ticketId(ticketId)
            .sellerId(sellerId)
            .eventId(ticket.getEventId())
            .seatId(ticket.getSeatId())
            .askingPriceCents(askingPriceCents)
            .originalPriceCents(ticket.getOriginalPriceCents())
            .status(ListingStatus.ACTIVE)
            .listedAt(Instant.now())
            .build();
        
        resaleRepo.save(listing);
        
        // Update search index for this event's resale listings
        redis.zadd("resale:" + ticket.getEventId(), askingPriceCents, listing.getListingId().toString());
        
        return listing;
    }
}
```

**Transfer Security**:
```
Original ticket: QR code = HMAC(ticket_001 | event | seat | user_A | timestamp_1)
  → Scanned at venue → VALID

After transfer:
  Old QR:  HMAC(ticket_001 | event | seat | user_A | timestamp_1) → INVALID (status=TRANSFERRED)
  New QR:  HMAC(ticket_002 | event | seat | user_B | timestamp_2) → VALID

Why new QR code?
  - Prevents seller from keeping a copy and using it at the venue
  - New HMAC includes new user_id → scanner verifies identity match
  - Old ticket marked TRANSFERRED in DB → scanner rejects

Anti-gouging:
  - Event organizer sets max resale price (e.g., 120% of face value)
  - Platform takes 10-15% fee on resale transactions
  - Some events disallow all transfers (e.g., artist preference)
```

---

## 🏗️ Infrastructure & Cost Estimation

```
┌─────────────────────────┬──────────────────┬──────────────────┐
│ Component               │ Configuration    │ Monthly Cost     │
├─────────────────────────┼──────────────────┼──────────────────┤
│ API Gateway             │ 50K+ RPS, WAF    │ $5,000           │
│ Reservation Service     │ 50 pods (K8s)    │ $15,000          │
│ Event Service           │ 20 pods + CDN    │ $8,000           │
│ Payment Service         │ 20 pods          │ $6,000           │
│ Redis Cluster           │ 10 nodes, 320 GB │ $12,000          │
│ PostgreSQL (sharded)    │ 16 shards × 3    │ $25,000          │
│ Kafka                   │ 10 brokers       │ $10,000          │
│ Notification Service    │ 20 pods + SES    │ $5,000           │
│ Monitoring (DD/CW)      │ Full stack       │ $4,000           │
├─────────────────────────┼──────────────────┼──────────────────┤
│ TOTAL                   │                  │ ~$90,000/month   │
└─────────────────────────┴──────────────────┴──────────────────┘

Revenue: 500M tickets/year × avg $80 × 5% platform fee = $2B/year
Infrastructure cost: ~$1M/year = 0.05% of revenue
```

---

## 🔄 Reservation State Machine (Complete)

```
┌───────────┐     reserve()     ┌──────────┐
│ AVAILABLE │ ─────────────────→│   HELD   │
│  (seats)  │                   │ (5-10 min)│
└───────────┘                   └──────┬───┘
      ▲                                │
      │                    ┌───────────┼────────────┐
      │                    ▼           ▼            ▼
      │              ┌──────────┐ ┌────────┐  ┌─────────┐
      │              │ PAYMENT  │ │EXPIRED │  │CANCELLED│
      │              │ PENDING  │ │(timeout)│ │ (user)  │
      │              └────┬─────┘ └────┬───┘  └────┬────┘
      │                   │            │           │
      │              ┌────▼─────┐      │           │
      │              │CONFIRMED │      │           │
      │              │(tickets  │      └───────────┘
      │              │ issued)  │           │
      │              └──────────┘    seats released
      │                              back to AVAILABLE
      └──────────────────────────────────────┘

Hold duration: 5-10 minutes (configurable per event)
Payment timeout: 60 seconds (Stripe charge timeout)
Saga rollback: any failure → compensating actions → seats released
```

---

## 📊 Flash Sale Timeline Example

```
Taylor Swift Eras Tour — 50,000 seats

T-1 hour:  Queue opens. 500K users join virtual queue.
T-0:       Sale starts. First 1,000 users admitted.
T+10s:     1,000 more admitted. First batch selecting seats.
T+30s:     First reservations created (3,000 seats held).
T+1min:    10,000 users admitted. 8,000 seats held.
T+5min:    30,000 users admitted. 25,000 seats held.
           System metrics: 2,000 reservations/min, 500ms avg latency.
T+10min:   First holds start expiring (users who didn't pay).
           2,000 seats released back to inventory.
T+15min:   45,000 seats held or sold. 5,000 still available.
T+20min:   All 50,000 seats sold. Queue notifies remaining users.
           "Sorry, all tickets have been sold."

Key numbers:
  - 500K users served fairly (FIFO queue)
  - Peak: 8,300 QPS (queue check + admission)
  - Reservation peak: 200 TPS (Redis lock + DB write)
  - Zero double-sells (Redis + DB FOR UPDATE)
  - 4% of holds expired → seats recycled → sold to next users
## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Seat locking** | Redis lock + DB FOR UPDATE | Redis for throughput; DB for correctness |
| **Hold expiration** | Redis TTL + DB polling (two-tier) | Redis fast (99%); DB reliable (catches rest) |
| **Virtual queue** | Redis sorted set + batch admission | Fair FIFO; controlled admission rate |
| **Payment** | Saga pattern + Stripe idempotency | Handles partial failures; no double-charge |
| **GA inventory** | Redis atomic counter (Lua script) | O(1) decrement; handles concurrency |
| **Availability cache** | Redis 2s TTL + CDN 10s TTL | 250K QPS reduced to ~100 DB queries/sec |
| **Sharding** | Event-based (16 shards) | Reservation transaction stays on one shard |
| **Seat map updates** | WebSocket + Redis Pub/Sub | Real-time; users see holds/sales instantly |
| **Tickets** | HMAC-signed QR codes | Offline verifiable; tamper-proof |
| **Presale** | Time-windowed access with codes | Flexible; supports multiple presale types |

## 🎯 Interview Talking Points

1. **"No double-selling = Redis lock + DB FOR UPDATE"** — Redis absorbs the thundering herd; DB provides linearizable correctness.
2. **"Two-tier hold expiration"** — Redis keyspace notification (fast, 99%) + DB polling (reliable, catches rest).
3. **"Virtual queue for fairness"** — Redis sorted set (FIFO). Admit 1K users every 10s. Token expires in 5 min.
4. **"Saga pattern for payment"** — Reserve → Charge → Confirm → Issue. Each step has compensating rollback.
5. **"GA uses Redis atomic counter"** — Lua script: `if inventory >= requested then DECRBY`. No race conditions.
6. **"Availability cached at 3 layers"** — CDN (10s) → Redis (2s) → DB. 250K QPS served from cache.
7. **"Shard by event_id"** — All seats + reservations for one event on same shard. No cross-shard transactions.
8. **"WebSocket seat map updates"** — Redis Pub/Sub → all WS servers → clients see holds/sales in real-time.
9. **"HMAC-signed QR tickets"** — Venue scanner verifies signature offline. Mark as USED in DB (idempotent).
10. **"Idempotency at every level"** — Client idempotency key → Redis dedup → Stripe idempotency → DB version check.
11. **"10:1 oversubscription"** — 500K users, 50K seats. Queue ensures servers aren't overwhelmed.
12. **"Presale windows"** — Time-based access control: fan club → credit card → general. Fair, configurable.

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 14 topics (choose 2-3 based on interviewer interest)