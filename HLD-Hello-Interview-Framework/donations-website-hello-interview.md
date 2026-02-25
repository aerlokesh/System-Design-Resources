# Design a Donations Website — Hello Interview Framework

> **Question**: Design a short-term (24-48 hour) donations website where users can contribute to pre-selected charities, with focus on payment processing, failure handling, and backup payment methods.
>
> **Source**: [Hello Interview Community](https://www.hellointerview.com/community/questions/donations-website-charities/cmbdisew101kuad08fuuiky5v)
>
> **Asked at**: DoorDash, Lyft
>
> **Difficulty**: Medium-Hard | **Level**: Senior

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## Context

Think of a **weekend telethon** but online. A charity event runs for 24-48 hours. Users visit the site, pick a charity, donate money, and watch a live counter showing how much has been raised in real time. The event ends, totals are finalized, and money is disbursed.

**Why interviewers love this question:** It tests your ability to design a reliable payments-facing system under a strict time window. They're probing for:
- **Idempotency** across retries and webhooks
- **Failure handling** when a payment processor degrades
- **Hot-counter contention** for live totals (thousands of concurrent donations)
- **Clean reconciliation** at the end (every dollar accounted for)
- Balancing **correctness, simplicity, and operational readiness** for a short, high-visibility event

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Browse Charities**: Display a fixed catalog of pre-selected charities (10-100) with name, description, logo, and current donation total.
2. **Make a Donation**: User selects a charity, enters an amount, provides payment info, and submits. System processes payment and records the donation.
3. **Idempotent Payment Processing**: A donation must be processed **exactly once** even with retries, network failures, webhook duplicates, or browser refreshes.
4. **Live Donation Totals**: Show real-time running totals per charity and a global total. Updates visible within 5 seconds of payment confirmation.
5. **Payment Failure Handling**: Gracefully handle declined cards, processor timeouts, and partial failures. Provide clear feedback to users.
6. **Backup Payment Processor**: If the primary payment processor (e.g., Stripe) goes down, automatically failover to a backup processor (e.g., Braintree/PayPal).
7. **Event Lifecycle**: Support event start/end times. Reject donations outside the event window. Finalize totals after event ends.

#### Nice to Have (P1)
- Donation receipt emails with tax deduction info
- Donor leaderboard (top donors, recent donors)
- Social sharing ("I donated $50 to Red Cross!")
- Recurring donation option
- Employer matching integration
- Admin dashboard for event organizers

#### Below the Line (Out of Scope)
- Charity onboarding and verification
- Tax filing / 501(c)(3) compliance
- Multi-currency support (assume USD only)
- User account management (guest donations OK)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Availability** | 99.99% during event window | 24-48 hour event; downtime = lost donations |
| **Payment correctness** | Exactly-once processing, zero double-charges | Financial system; double-charge = legal/trust issue |
| **Live counter latency** | < 5 seconds from payment to counter update | Real-time excitement drives more donations |
| **Throughput** | 1,000 donations/minute at peak | TV/social media surge during live event |
| **Payment processing** | < 10 seconds end-to-end (user click → confirmation) | Users abandon if payment is slow |
| **Reconciliation** | 100% of payments accounted for post-event | Financial audit requirement |
| **Durability** | Zero lost donations (even if server crashes mid-payment) | Every donation is someone's money |

### Capacity Estimation

```
Event duration: 24 hours
Expected donations: 500,000 total
  Peak rate: 1,000 donations/minute (during TV segments)
  Average rate: ~350/minute

Per donation:
  Average amount: $50
  Payment payload: ~2 KB
  Total data: 500K × 2 KB = 1 GB (trivial)

Total raised: ~$25M

Charities: 50
Concurrent users on site: 50,000 peak
Page views: 5M total

Live counter updates:
  500K donations → 500K counter increments
  Peak: 1000 increments/minute → ~17/second per charity (if evenly distributed)
  Hot charity (viral): could get 500 donations/minute → 8/second on ONE counter

Payment processor calls:
  500K payments × 2 API calls each (create + confirm) = 1M API calls
  Peak: 2000 calls/minute to Stripe/Braintree

Webhook events:
  500K payment confirmation webhooks
  + retries on failure: ~5% → 25K extra = 525K total webhook events
```

---

## 2️⃣ Core Entities

### Entity 1: Charity
```java
public class Charity {
    private final String charityId;            // "red_cross"
    private final String name;                 // "American Red Cross"
    private final String description;
    private final String logoUrl;
    private final String category;             // "disaster_relief", "education", "health"
    private final boolean active;              // Is this charity accepting donations?
}
```

### Entity 2: Donation
```java
public class Donation {
    private final String donationId;           // UUID (generated client-side for idempotency)
    private final String charityId;
    private final String eventId;
    private final long amountCents;            // $50.00 = 5000 cents (avoid floating point!)
    private final String currency;             // "USD"
    private final String donorName;            // Optional (can be anonymous)
    private final String donorEmail;           // For receipt
    private final DonationStatus status;       // PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED
    private final String idempotencyKey;       // Client-generated UUID (THE key to exactly-once)
    private final String paymentIntentId;      // Stripe/Braintree payment ID
    private final String paymentProcessor;     // "stripe" or "braintree"
    private final Instant createdAt;
    private final Instant completedAt;
    private final String failureReason;        // If FAILED: "card_declined", "processor_timeout"
}

public enum DonationStatus {
    PENDING,       // Created, not yet sent to payment processor
    PROCESSING,    // Sent to Stripe/Braintree, waiting for confirmation
    COMPLETED,     // Payment confirmed, donation counted
    FAILED,        // Payment failed (declined, timeout, error)
    REFUNDED       // Donation was refunded post-event
}
```

### Entity 3: Event
```java
public class Event {
    private final String eventId;              // "telethon_2025"
    private final String name;                 // "Annual Charity Telethon 2025"
    private final Instant startsAt;
    private final Instant endsAt;
    private final EventStatus status;          // UPCOMING, ACTIVE, ENDED, RECONCILED
    private final List<String> charityIds;     // Participating charities
    private final long goalCents;              // Fundraising goal: $10M = 1_000_000_000 cents
}
```

### Entity 4: Donation Counter (for live totals)
```java
public class DonationCounter {
    private final String charityId;
    private final String eventId;
    private final long totalCents;             // Running total (in Redis)
    private final long donationCount;          // Number of donations
    private final Instant lastUpdatedAt;
}
```

---

## 3️⃣ API Design

### 1. Get Charities (with live totals)
```
GET /api/v1/events/{event_id}/charities

Response:
{
  "event": {
    "event_id": "telethon_2025",
    "name": "Annual Charity Telethon 2025",
    "status": "ACTIVE",
    "ends_at": "2025-06-15T00:00:00Z",
    "goal_cents": 1000000000,
    "total_raised_cents": 12500000000,
    "total_donations": 250000
  },
  "charities": [
    {
      "charity_id": "red_cross",
      "name": "American Red Cross",
      "logo_url": "https://cdn.example.com/red_cross.png",
      "total_raised_cents": 3200000000,
      "donation_count": 64000
    },
    ...
  ]
}
```

### 2. Create Donation (Initiate Payment)
```
POST /api/v1/donations

Headers:
  Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000  ← CLIENT-GENERATED UUID

Request:
{
  "event_id": "telethon_2025",
  "charity_id": "red_cross",
  "amount_cents": 5000,
  "currency": "USD",
  "donor_name": "Jane Doe",
  "donor_email": "jane@example.com",
  "payment_method": {
    "type": "card",
    "token": "tok_visa_4242"
  }
}

Response (202 Accepted):
{
  "donation_id": "don_abc123",
  "status": "PROCESSING",
  "idempotency_key": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Payment is being processed. You'll receive a confirmation shortly."
}

// If same Idempotency-Key is sent again (retry):
Response (200 OK):  ← Returns the SAME response, does NOT charge again
{
  "donation_id": "don_abc123",
  "status": "COMPLETED",
  "idempotency_key": "550e8400-e29b-41d4-a716-446655440000",
  "message": "This donation was already processed successfully."
}
```

### 3. Get Donation Status
```
GET /api/v1/donations/{donation_id}

Response:
{
  "donation_id": "don_abc123",
  "status": "COMPLETED",
  "charity": "American Red Cross",
  "amount_cents": 5000,
  "completed_at": "2025-06-14T15:32:10Z",
  "receipt_url": "https://receipts.example.com/don_abc123.pdf"
}
```

### 4. Live Counter Stream (SSE/WebSocket)
```
GET /api/v1/events/{event_id}/live (Server-Sent Events)

data: {"type":"counter_update","charity_id":"red_cross","total_cents":3200050000,"count":64001}
data: {"type":"counter_update","charity_id":"unicef","total_cents":2800000000,"count":56000}
data: {"type":"global_update","total_cents":12500050000,"total_count":250001}

// Client receives updates every 1-5 seconds
```

---

## 4️⃣ Data Flow

### Flow 1: Happy Path — Donation Succeeds
```
1. User selects charity, enters $50, clicks "Donate"
   Client generates idempotency_key = UUID
   ↓
2. POST /api/v1/donations {idempotency_key, charity_id, amount, payment_token}
   ↓
3. Donation Service:
   a. Check idempotency_key in Redis → NOT FOUND (new donation)
   b. Create Donation record in DB: status = PENDING
   c. Store idempotency_key → donation_id in Redis (TTL 48h)
   ↓
4. Call Payment Processor (Stripe):
   a. Create PaymentIntent with idempotency_key
   b. Stripe returns: payment_intent_id, status = "requires_confirmation"
   c. Confirm payment
   d. Stripe returns: status = "succeeded"
   ↓
5. Update Donation: status = COMPLETED, payment_intent_id = pi_xxx
   ↓
6. Publish event: "donation.completed" → Kafka
   ↓
7. Counter Service consumes event:
   a. INCRBY redis "counter:{event_id}:{charity_id}" 5000
   b. INCRBY redis "counter:{event_id}:global" 5000
   c. INCR redis "count:{event_id}:{charity_id}"
   ↓
8. Live counter pushed to connected clients via SSE
   ↓
9. Receipt Service: send confirmation email with receipt
   ↓
10. User sees: "Thank you! You donated $50 to Red Cross."
    Total: $12,500,050 raised (updated in real-time)
```

### Flow 2: Idempotent Retry — User Double-Clicks or Network Retry
```
1. User clicks "Donate" → request sent with idempotency_key = "abc123"
   ↓
2. Network timeout → user clicks again → SAME idempotency_key = "abc123"
   ↓
3. Donation Service:
   a. Check idempotency_key "abc123" in Redis → FOUND → donation_id = "don_xyz"
   b. Look up Donation "don_xyz" in DB
   c. If status = COMPLETED → return 200 OK (already done, no new charge)
   d. If status = PROCESSING → return 202 Accepted (still working on it)
   e. If status = FAILED → allow retry? (depends on policy)
   ↓
4. User is NEVER double-charged. Same idempotency_key = same donation.
```

### Flow 3: Payment Processor Failover
```
1. User submits donation → Donation Service calls Stripe
   ↓
2. Stripe returns: HTTP 503 Service Unavailable (or timeout after 10s)
   ↓
3. Donation Service retries Stripe (up to 2 retries with exponential backoff)
   ↓
4. Still failing → Circuit breaker opens for Stripe
   ↓
5. Failover to Braintree:
   a. Create payment with Braintree API (using SAME idempotency_key)
   b. Braintree returns: status = "succeeded"
   c. Record: payment_processor = "braintree"
   ↓
6. Donation marked COMPLETED, counter updated as normal
   ↓
7. User sees success. They don't know which processor was used.

IMPORTANT: Both Stripe AND Braintree use the same idempotency_key.
If Stripe DID process it (but we timed out before getting the response),
Braintree will also process it → we'd have a DOUBLE CHARGE.

SOLUTION: Check Stripe first ("did you process idempotency_key abc123?")
before falling back to Braintree. If Stripe says "yes, succeeded" → done.
Only failover if Stripe confirms it did NOT process the payment.
```

### Flow 4: Post-Event Reconciliation
```
1. Event ends at midnight → status = ENDED
   ↓
2. Reject any new donations (API returns 410 Gone)
   ↓
3. Wait for all PROCESSING donations to settle (up to 30 min)
   ↓
4. Reconciliation job:
   a. Query all donations for event_id WHERE status = COMPLETED
   b. Sum amounts per charity → DB totals
   c. Compare with Redis counter totals
   d. Compare with payment processor records:
      - Stripe: list all charges with our event metadata
      - Braintree: same query
   e. Three-way reconciliation:
      DB donations ↔ Redis counters ↔ Payment processor records
   f. If all three match: ✅ RECONCILED
   g. If mismatch: flag for manual review
   ↓
5. Generate final report:
   {
     "event": "telethon_2025",
     "total_raised": "$25,000,000",
     "total_donations": 500000,
     "per_charity": { "red_cross": "$6.4M", ... },
     "reconciliation": "PASSED",
     "discrepancies": 0
   }
   ↓
6. Initiate disbursement to charities (separate treasury system)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                      │
│  Browser / Mobile App                                                │
│  • Browse charities + live totals                                    │
│  • Submit donation (with client-generated idempotency_key)           │
│  • SSE connection for live counter updates                           │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY / LOAD BALANCER                        │
│  • Rate limiting (100 req/sec per IP)                                │
│  • SSL termination                                                   │
│  • Route: /donations → Donation Service                              │
│  • Route: /live → SSE Service                                        │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ DONATION       │ │ CHARITY        │ │ SSE / LIVE     │
│ SERVICE        │ │ SERVICE        │ │ COUNTER SERVICE│
│                │ │                │ │                │
│ • Idempotency  │ │ • List charities│ │ • Push counter │
│   check (Redis)│ │ • Get details  │ │   updates to   │
│ • Create       │ │                │ │   connected    │
│   donation     │ │                │ │   clients      │
│ • Call payment │ │                │ │ • Subscribe to │
│   processor    │ │                │ │   Redis Pub/Sub│
│ • Handle       │ │                │ │                │
│   failure/     │ │                │ │                │
│   failover     │ │                │ │                │
│ • Publish      │ │                │ │                │
│   event        │ │                │ │                │
└───────┬────────┘ └────────────────┘ └───────┬────────┘
        │                                     │
        ▼                                     │
┌────────────────┐                            │
│ PAYMENT        │                            │
│ PROCESSOR      │                            │
│ ADAPTER        │                            │
│                │                            │
│ ┌────────────┐ │                            │
│ │ Stripe     │ │ ← Primary                  │
│ │ (active)   │ │                            │
│ ├────────────┤ │                            │
│ │ Braintree  │ │ ← Backup (circuit breaker) │
│ │ (standby)  │ │                            │
│ └────────────┘ │                            │
└───────┬────────┘                            │
        │                                     │
        ▼                                     │
┌─────────────────────────────────────────────┴───────────────────────┐
│                         KAFKA                                        │
│  Topic: donation.completed                                           │
│  Topic: donation.failed                                              │
│  Topic: counter.updates (for SSE fan-out)                            │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ COUNTER        │ │ RECEIPT        │ │ RECONCILIATION │
│ SERVICE        │ │ SERVICE        │ │ SERVICE        │
│                │ │                │ │                │
│ • INCRBY Redis │ │ • Send email   │ │ • Post-event   │
│   per charity  │ │ • Generate PDF │ │   three-way    │
│ • Publish to   │ │   receipt      │ │   reconciliation│
│   Redis Pub/Sub│ │                │ │ • DB ↔ Redis ↔ │
│   for SSE      │ │                │ │   Payment proc │
└───────┬────────┘ └────────────────┘ └────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         DATA STORES                                  │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ PostgreSQL   │  │ Redis        │  │ S3                        │  │
│  │              │  │              │  │                           │  │
│  │ • Donations  │  │ • Idempotency│  │ • Receipts (PDF)         │  │
│  │   (source of │  │   keys (48h) │  │ • Reconciliation reports │  │
│  │   truth)     │  │ • Live       │  │                           │  │
│  │ • Charities  │  │   counters   │  │                           │  │
│  │ • Events     │  │ • Pub/Sub    │  │                           │  │
│  │              │  │   (SSE fan-  │  │                           │  │
│  │              │  │   out)       │  │                           │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology |
|-----------|---------|-----------|
| **Donation Service** | Accept donations, idempotency check, call payment processor | Java/Spring Boot, 10 pods |
| **Payment Processor Adapter** | Abstract Stripe/Braintree, circuit breaker, failover | Resilience4j circuit breaker |
| **Counter Service** | Increment Redis counters on donation completion | Kafka consumer, 3 pods |
| **SSE/Live Service** | Push live counter updates to connected browsers | Node.js/Go, Redis Pub/Sub |
| **Receipt Service** | Generate and email donation receipts | Kafka consumer, 3 pods |
| **Reconciliation Service** | Post-event three-way reconciliation | Batch job (cron) |
| **PostgreSQL** | Source of truth for donations, charities, events | Primary + read replica |
| **Redis** | Idempotency keys (48h TTL), live counters, Pub/Sub | Redis Cluster |
| **Kafka** | Decouple donation processing from counter/receipt/recon | 3 brokers |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Idempotent Payment Processing — Never Double-Charge

**The Problem**: Network failures, browser refreshes, and webhook retries can cause the same donation to be processed multiple times. Charging someone twice for a $500 donation is catastrophic for trust.

```java
public class DonationService {

    /**
     * IDEMPOTENCY STRATEGY:
     * 1. Client generates a UUID (idempotency_key) BEFORE sending the request
     * 2. Server checks Redis: has this key been seen before?
     *    - YES → return the existing donation (no new charge)
     *    - NO  → process new donation, store key → donation_id in Redis
     * 3. Payment processor (Stripe) also receives the idempotency_key
     *    → Stripe won't charge twice for the same key (their guarantee)
     * 4. Belt AND suspenders: both OUR dedup + Stripe's dedup
     */

    public DonationResponse createDonation(DonationRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // STEP 1: Check our idempotency cache (Redis)
        String existingDonationId = redis.get("idempotency:" + idempotencyKey);
        if (existingDonationId != null) {
            // Already processed — return existing result (no new charge)
            Donation existing = donationRepo.findById(existingDonationId);
            return DonationResponse.fromExisting(existing); // 200 OK
        }

        // STEP 2: Check event is active
        Event event = eventService.getEvent(request.getEventId());
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new EventNotActiveException("Event has ended");
        }

        // STEP 3: Create donation record (status = PENDING)
        Donation donation = Donation.builder()
            .donationId(UUID.randomUUID().toString())
            .charityId(request.getCharityId())
            .eventId(request.getEventId())
            .amountCents(request.getAmountCents())
            .donorName(request.getDonorName())
            .donorEmail(request.getDonorEmail())
            .idempotencyKey(idempotencyKey)
            .status(DonationStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        donationRepo.save(donation);

        // STEP 4: Store idempotency key → donation_id (with TTL)
        redis.setex("idempotency:" + idempotencyKey,
                     Duration.ofHours(48), donation.getDonationId());

        // STEP 5: Process payment (with failover)
        try {
            PaymentResult result = paymentAdapter.processPayment(
                request.getAmountCents(),
                request.getPaymentMethod(),
                idempotencyKey  // Pass to Stripe/Braintree too!
            );

            // STEP 6: Update donation with payment result
            donation.setStatus(DonationStatus.COMPLETED);
            donation.setPaymentIntentId(result.getPaymentId());
            donation.setPaymentProcessor(result.getProcessor());
            donation.setCompletedAt(Instant.now());
            donationRepo.save(donation);

            // STEP 7: Publish completion event
            kafka.send("donation.completed", donation);

            return DonationResponse.success(donation); // 202 Accepted

        } catch (PaymentDeclinedException e) {
            donation.setStatus(DonationStatus.FAILED);
            donation.setFailureReason(e.getDeclineCode());
            donationRepo.save(donation);
            // Remove idempotency key so user can retry with new payment method
            redis.del("idempotency:" + idempotencyKey);
            return DonationResponse.failed(donation, e.getUserMessage());

        } catch (PaymentProcessorException e) {
            // Processor error (not a decline) — may have charged or may not
            // DO NOT remove idempotency key — must investigate
            donation.setStatus(DonationStatus.PROCESSING);
            donation.setFailureReason("processor_error: " + e.getMessage());
            donationRepo.save(donation);
            // Async job will check payment processor for final status
            kafka.send("donation.needs_resolution", donation);
            return DonationResponse.pending(donation);
        }
    }
}
```

**Why idempotency_key must come from the CLIENT**:
```
If server generates the ID:
  1. Client sends request → server creates donation_id = "abc"
  2. Response lost in network
  3. Client retries → server creates NEW donation_id = "def"
  4. User is charged TWICE ❌

If client generates the ID:
  1. Client generates idempotency_key = "xyz" BEFORE sending
  2. Client sends request → server processes, stores key "xyz"
  3. Response lost in network
  4. Client retries with SAME key "xyz" → server finds existing → returns 200
  5. User charged exactly ONCE ✅
```

---

### Deep Dive 2: Payment Processor Failover — Circuit Breaker Pattern

**The Problem**: Stripe goes down during peak hour. 1000 donations/minute are failing. We need to seamlessly switch to Braintree without double-charging anyone.

```java
public class PaymentProcessorAdapter {

    private final StripeClient stripe;
    private final BraintreeClient braintree;
    private final CircuitBreaker stripeCircuitBreaker;

    /**
     * Circuit breaker states:
     * CLOSED:    Normal operation, all calls go to Stripe
     * OPEN:      Stripe is down, all calls go to Braintree
     * HALF_OPEN: Testing if Stripe is back (send 1 request)
     * 
     * Transitions:
     * CLOSED → OPEN:      After 5 failures in 60 seconds
     * OPEN → HALF_OPEN:   After 30 seconds cooldown
     * HALF_OPEN → CLOSED:  If test request succeeds
     * HALF_OPEN → OPEN:    If test request fails
     */

    public PaymentProcessorAdapter() {
        this.stripeCircuitBreaker = CircuitBreaker.ofDefaults("stripe")
            .failureRateThreshold(50)       // Open if 50% of calls fail
            .slidingWindowSize(10)           // Over last 10 calls
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3);
    }

    public PaymentResult processPayment(long amountCents, PaymentMethod method,
                                         String idempotencyKey) {
        // Try primary processor (Stripe) with circuit breaker
        if (stripeCircuitBreaker.getState() != CircuitBreaker.State.OPEN) {
            try {
                PaymentResult result = stripeCircuitBreaker.executeSupplier(() ->
                    stripe.charge(amountCents, method.getToken(), idempotencyKey));
                return result.withProcessor("stripe");
            } catch (Exception e) {
                log.warn("Stripe failed: {}", e.getMessage());
                // Fall through to backup
            }
        }

        // CRITICAL: Before calling Braintree, verify Stripe didn't actually charge
        // (we might have timed out but Stripe succeeded)
        try {
            StripePaymentStatus stripeStatus = stripe.checkPaymentStatus(idempotencyKey);
            if (stripeStatus == StripePaymentStatus.SUCCEEDED) {
                log.info("Stripe actually succeeded for {} — skipping Braintree", idempotencyKey);
                return PaymentResult.success(stripeStatus.getPaymentId(), "stripe");
            }
        } catch (Exception e) {
            log.warn("Can't verify Stripe status, proceeding to Braintree: {}", e.getMessage());
        }

        // Backup: Braintree (with SAME idempotency key)
        log.info("Failing over to Braintree for idempotency_key={}", idempotencyKey);
        PaymentResult result = braintree.charge(amountCents, method.getToken(), idempotencyKey);
        metrics.counter("payment.failover.braintree").increment();
        return result.withProcessor("braintree");
    }
}
```

**Failover safety — avoiding double charge**:
```
DANGEROUS scenario without safety check:
  1. We call Stripe → timeout after 10s (Stripe is slow, not down)
  2. Stripe DOES process the payment (we just didn't get the response)
  3. We failover to Braintree → Braintree ALSO charges
  4. User charged TWICE ❌

SAFE scenario with verification:
  1. We call Stripe → timeout after 10s
  2. Before calling Braintree: check Stripe "did you process key abc123?"
  3a. Stripe says "yes, succeeded" → use Stripe result, skip Braintree ✅
  3b. Stripe says "no / not found" → safe to call Braintree ✅
  3c. Stripe unreachable → risky, but Braintree's own idempotency helps

The idempotency_key on BOTH processors is the last safety net.
```

---

### Deep Dive 3: Hot Counter — Live Donation Totals Under Contention

**The Problem**: A viral charity could get 500 donations/minute. All 500 increment the SAME Redis counter. Redis handles this fine (~100K ops/sec), but what about the database?

```java
public class CounterService {

    /**
     * STRATEGY: Redis for real-time display, DB for reconciliation.
     * 
     * Redis INCRBY is atomic and fast (< 1ms). 
     * 500 INCRBY/sec on one key is trivial for Redis.
     * 
     * DB counter is NOT updated per-donation (too slow).
     * Instead: periodically flush Redis counters to DB (every 60s).
     * 
     * Three counter sources:
     * 1. Redis (live, real-time, might lose data on Redis crash)
     * 2. DB aggregate (SELECT SUM(amount) — always correct but slow)
     * 3. DB counter table (materialized, updated every 60s)
     */

    @KafkaListener(topics = "donation.completed")
    public void onDonationCompleted(DonationCompletedEvent event) {
        String charityKey = "counter:" + event.getEventId() + ":" + event.getCharityId();
        String globalKey = "counter:" + event.getEventId() + ":global";
        String countKey = "count:" + event.getEventId() + ":" + event.getCharityId();

        // Atomic Redis increments (< 1ms each)
        redis.incrBy(charityKey, event.getAmountCents());
        redis.incrBy(globalKey, event.getAmountCents());
        redis.incr(countKey);

        // Publish for SSE fan-out
        redis.publish("live:" + event.getEventId(), Json.encode(
            new CounterUpdate(event.getCharityId(),
                redis.get(charityKey),
                redis.get(countKey))));
    }

    /**
     * Periodic flush: Redis → DB (for durability and reconciliation)
     * Even if Redis crashes, DB has data from last flush.
     * Max data loss: 60 seconds of counter increments.
     */
    @Scheduled(fixedRate = 60_000)
    public void flushCountersToDB() {
        for (String charityId : activeCharities) {
            String key = "counter:" + eventId + ":" + charityId;
            long redisCents = Long.parseLong(redis.get(key));
            
            // Upsert into DB counter table
            jdbc.update("""
                INSERT INTO donation_counters (event_id, charity_id, total_cents, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (event_id, charity_id)
                DO UPDATE SET total_cents = ?, updated_at = NOW()
                """, eventId, charityId, redisCents, redisCents);
        }
    }

    /**
     * SSE endpoint: push live updates to browsers
     */
    public Flux<ServerSentEvent<String>> streamLiveCounters(String eventId) {
        return Flux.create(sink -> {
            // Subscribe to Redis Pub/Sub channel
            redis.subscribe("live:" + eventId, message -> {
                sink.next(ServerSentEvent.builder(message).build());
            });
        });
    }
}
```

**Why Redis INCRBY is safe here**:
```
Redis INCRBY is:
  ✅ Atomic (single-threaded Redis — no race conditions)
  ✅ Fast (< 1ms per operation)
  ✅ Handles 100K+ ops/sec (our peak is 500/sec — 200x headroom)
  ✅ No lock contention (unlike DB UPDATE SET total = total + 5000)

The DB would struggle with 500 concurrent UPDATEs/sec on the same row:
  ❌ Row-level lock contention
  ❌ Transaction overhead
  ❌ WAL write amplification
  
Solution: Redis for live counter, DB for periodic materialization.
```

---

### Deep Dive 4: Post-Event Reconciliation — Every Dollar Accounted For

**The Problem**: After the event, we must prove that every dollar charged to a credit card is accounted for in our system. The three sources of truth are: our donation DB, Redis counters, and the payment processor's records.

```java
public class ReconciliationService {

    /**
     * Three-way reconciliation:
     * 1. Our DB: SUM(amount_cents) WHERE event_id = ? AND status = 'COMPLETED'
     * 2. Redis counters: GET counter:{event_id}:global
     * 3. Payment processor: list all charges with event metadata
     * 
     * All three MUST match. If they don't → investigate.
     */
    public ReconciliationReport reconcile(String eventId) {
        // Source 1: Our database (source of truth)
        long dbTotalCents = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM donations " +
            "WHERE event_id = ? AND status = 'COMPLETED'", Long.class, eventId);
        long dbCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM donations WHERE event_id = ? AND status = 'COMPLETED'",
            Long.class, eventId);

        // Source 2: Redis counters (real-time display)
        long redisTotalCents = Long.parseLong(
            redis.get("counter:" + eventId + ":global"));

        // Source 3: Payment processors
        long stripeTotalCents = stripe.listCharges(eventId).stream()
            .mapToLong(c -> c.getAmountCents()).sum();
        long braintreeTotalCents = braintree.listTransactions(eventId).stream()
            .mapToLong(t -> t.getAmountCents()).sum();
        long processorTotalCents = stripeTotalCents + braintreeTotalCents;

        // Compare
        boolean dbMatchesProcessor = dbTotalCents == processorTotalCents;
        boolean dbMatchesRedis = dbTotalCents == redisTotalCents;

        ReconciliationStatus status;
        if (dbMatchesProcessor && dbMatchesRedis) {
            status = ReconciliationStatus.PASSED;
        } else if (dbMatchesProcessor && !dbMatchesRedis) {
            status = ReconciliationStatus.REDIS_DRIFT;
            // Redis lost some increments (crash?) — DB is correct
            // Fix: update Redis from DB
        } else {
            status = ReconciliationStatus.NEEDS_INVESTIGATION;
            // DB and processor disagree — manual review required
        }

        return ReconciliationReport.builder()
            .eventId(eventId)
            .dbTotalCents(dbTotalCents)
            .dbCount(dbCount)
            .redisTotalCents(redisTotalCents)
            .stripeTotalCents(stripeTotalCents)
            .braintreeTotalCents(braintreeTotalCents)
            .status(status)
            .build();
    }
}
```

**Common reconciliation discrepancies and fixes**:
```
1. Redis > DB (Redis counted more than DB recorded)
   Cause: Kafka consumer incremented Redis but DB write failed
   Fix: Redis counter is wrong. Reset from DB. Rare.

2. DB > Redis (DB has more than Redis shows)
   Cause: Redis crashed and restarted mid-event, losing in-memory counters
   Fix: Re-initialize Redis counters from DB aggregate query.

3. Processor > DB (Stripe charged more than we recorded)
   Cause: Our webhook handler missed a payment confirmation
   Fix: Import missing payments from Stripe into our DB. CRITICAL.

4. DB > Processor (We recorded more than Stripe charged)
   Cause: We marked a donation COMPLETED before Stripe confirmed
   Fix: Re-check each donation's payment status with Stripe API.
   These are phantom donations — status should be reverted to FAILED.
```

---

### Deep Dive 5: Handling the "Thundering Herd" at Event Start

**The Problem**: When the TV host says "Donate now!", 50,000 users hit the site simultaneously. How do we handle the spike?

```
SPIKE MITIGATION STRATEGIES:

1. CDN for static content
   - Charity catalog, images, CSS/JS served from CloudFront/Akamai
   - Only donation API calls hit our servers
   - Reduces server load by 90%

2. Connection queuing at API Gateway
   - Rate limit: 100 requests/sec per IP (prevent single-user DOS)
   - Global rate limit: 5000 donation requests/sec (circuit breaker)
   - Queue excess requests with 429 Too Many Requests + Retry-After

3. Pre-warm infrastructure
   - Scale donation service to 20 pods BEFORE event starts
   - Pre-warm DB connection pools
   - Pre-cache charity data in Redis

4. Async payment processing
   - Accept donation request (201 Accepted) in < 100ms
   - Process payment asynchronously
   - Notify user via WebSocket/polling when payment completes
   - This smooths the spike: acceptance is fast, processing is queued

5. Client-side throttling
   - Disable "Donate" button for 5 seconds after click
   - Show "Processing..." spinner
   - Prevent accidental double-submissions
```

---

### Deep Dive 6: Money Handling — Cents Not Dollars

**The Problem**: Floating-point arithmetic causes rounding errors. $19.99 + $0.01 ≠ $20.00 in floating point.

```java
/**
 * RULE: ALWAYS store money as integer cents (or the smallest currency unit).
 * 
 * BAD:  double amount = 19.99;  // 19.989999999999998 in IEEE 754 ❌
 * GOOD: long amountCents = 1999; // Exact integer arithmetic ✅
 * 
 * Display: amountCents / 100.0 → "$19.99" (only at the UI layer)
 * 
 * Why: Databases, Redis INCRBY, and integer math are all EXACT.
 * Floating point addition accumulates rounding errors over 500K donations.
 */

// Database schema
// amount_cents BIGINT NOT NULL  ← integer, not DECIMAL
// 
// Redis counter
// INCRBY counter:red_cross 1999  ← integer increment, exact
//
// Reconciliation sum
// SELECT SUM(amount_cents) FROM donations  ← exact integer sum
// Result: 2500000000 cents = $25,000,000.00 exactly
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Alternative | Why |
|----------|--------|-------------|-----|
| **Idempotency** | Client-generated key + Redis cache + Stripe idempotency | Server-generated ID only | Client key survives lost responses; triple-layer dedup |
| **Live counter** | Redis INCRBY + periodic DB flush | Direct DB UPDATE per donation | Redis handles 100K ops/sec; DB row-lock contention at 500/sec |
| **Payment failover** | Circuit breaker (Stripe → Braintree) with status check | Single processor only | 24-hour event can't tolerate processor downtime |
| **Counter delivery** | SSE (Server-Sent Events) | WebSocket / Polling | SSE is simpler for one-way server→client push; sufficient for counter |
| **Money format** | Integer cents (long) | Floating-point dollars (double) | Exact arithmetic; no rounding errors over 500K transactions |
| **Reconciliation** | Three-way (DB ↔ Redis ↔ Processor) | DB only | Catches phantom donations, Redis drift, and missed webhooks |
| **Payment model** | Synchronous charge (user waits) | Async + webhook | Simpler UX; user gets immediate confirmation; 24-hour event = KISS |

## Interview Talking Points

1. **"Client-generated idempotency key is the foundation"** — The client generates a UUID before sending. Same key = same donation. Server checks Redis first, Stripe also deduplicates on their end. Triple-layer protection against double-charges.

2. **"Circuit breaker for payment processor failover"** — Resilience4j circuit breaker: CLOSED (Stripe works) → OPEN (Stripe down, route to Braintree) → HALF_OPEN (test if Stripe is back). Critical: verify Stripe didn't actually charge before falling back.

3. **"Redis INCRBY for live counters, not DB UPDATE"** — 500 concurrent increments/sec on one counter. Redis INCRBY is atomic and < 1ms. DB UPDATE would cause row-lock contention. Periodic flush to DB for durability.

4. **"Three-way reconciliation post-event"** — Our DB, Redis counters, and Stripe/Braintree records must all agree. Catches: missed webhooks, phantom donations, Redis drift. Every dollar accounted for.

5. **"Store money as integer cents, never floating-point"** — `long amountCents = 1999` not `double amount = 19.99`. Exact arithmetic across 500K transactions. Floating-point accumulates rounding errors.

6. **"SSE for live counter push, not WebSocket"** — One-way server→client is all we need. SSE is simpler than WebSocket. Backed by Redis Pub/Sub for fan-out across multiple SSE server pods.

7. **"24-hour event = optimize for simplicity"** — This isn't a permanent system. KISS: synchronous payment (not async saga), simple PostgreSQL (not distributed DB), Redis for counters (not complex CQRS). Build for reliability, not infinite scale.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Payment Processing (Stripe)** | Same idempotency and payment handling patterns | Stripe is a platform; donations website is a single-event consumer |
| **Flash Sale / Ticket Booking** | Same thundering herd + inventory contention | Flash sale has limited inventory; donations are unlimited |
| **Leaderboard** | Same real-time counter pattern (Redis sorted sets) | Leaderboard ranks users; donation counter sums amounts |
| **Crowdfunding (GoFundMe)** | Same donation model | GoFundMe is permanent; this is a 24-hour time-bounded event |
| **Ad Click Aggregator** | Same real-time counter aggregation | Ad clicks are high-volume but low-value; donations are payment-critical |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Source**: [Hello Interview Community](https://www.hellointerview.com/community/questions/donations-website-charities/cmbdisew101kuad08fuuiky5v)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (choose 2-3 based on interviewer interest)
