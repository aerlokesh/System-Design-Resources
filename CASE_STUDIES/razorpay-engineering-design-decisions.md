# Razorpay Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Razorpay's engineering blog, conference talks, and tech articles. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Razorpay Engineering Blog (engineering.razorpay.com), Rootconf/GopherCon India talks, tech articles
>
> **Context**: Razorpay is India's leading payment gateway processing $80B+ annually. Their biggest challenges: **zero tolerance for data loss** (it's real money), **extreme consistency requirements** (double-charge = lawsuit), and **handling India's payment diversity** (UPI, cards, netbanking, wallets — each with different failure modes).

---

## Table of Contents

1. [Payment Processing — Exactly-Once at Scale](#1-payment-processing)
2. [Database Strategy — MySQL + Tidb + Trident](#2-database-strategy)
3. [Event-Driven Architecture with Kafka](#3-kafka-events)
4. [Idempotency — Preventing Double Charges](#4-idempotency)
5. [Reconciliation Engine — Matching Every Rupee](#5-reconciliation)
6. [API Gateway — Traffic Management](#6-api-gateway)
7. [Golang Migration — From PHP to Go](#7-golang-migration)
8. [Distributed Locking — Preventing Race Conditions](#8-distributed-locking)
9. [Webhook Delivery — Reliable Notifications](#9-webhook-delivery)
10. [UPI Infrastructure — Real-Time Payments](#10-upi-infrastructure)
11. [Fraud Detection — ML at Payment Speed](#11-fraud-detection)
12. [Monitoring & Observability](#12-monitoring)

---

## 1. Payment Processing — Exactly-Once at Scale

**Source**: "Building Reliable Payment Systems" + Razorpay engineering talks

### The Problem
Every payment transaction MUST be processed exactly once. A duplicate payment means a double-charge (legal liability, customer trust destroyed). A lost payment means lost revenue for the merchant. At $80B+ annually, even 0.001% error rate is millions of dollars.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Consistency** | Strong consistency (ACID) for all payment state changes | Eventual consistency | Financial data must be exactly correct — no "eventually consistent" for money |
| **State machine** | Strict payment state machine (created → authorized → captured → settled) | Freeform status updates | State machine prevents invalid transitions (can't settle an unauthorized payment) |
| **Idempotency** | Idempotency key on every API call | No protection | Network retries, client bugs, and webhook retries must NOT cause duplicate charges |
| **Ledger** | Double-entry bookkeeping | Simple credit/debit log | Every rupee movement has a debit and credit entry — always balanced |
| **Amounts** | Stored as integers (paise — smallest unit) | Floating-point | Avoid rounding errors: ₹99.99 stored as 9999 paise |
| **Timeout** | Explicit timeout on every payment gateway call (30s) | Wait indefinitely | If a payment gateway doesn't respond in 30s, mark as "pending" and reconcile later |

### Payment State Machine
```
    CREATED ──→ AUTHORIZED ──→ CAPTURED ──→ SETTLED
       │            │              │
       ↓            ↓              ↓
     FAILED     FAILED/REFUNDED  REFUNDED
       
Rules:
  - Can only move forward (no backward transitions except refund)
  - Every transition is a database transaction
  - Every transition publishes a Kafka event
  - State + version is checked before transition (optimistic locking)
```

### Tradeoff
- ✅ Exactly-once: idempotency keys prevent double-charges
- ✅ State machine prevents invalid transitions (correctness by design)
- ✅ Double-entry bookkeeping ensures every rupee is accounted for
- ❌ Strong consistency limits throughput compared to eventual consistency
- ❌ State machine makes it harder to add new payment flows (rigid)
- ❌ Double-entry adds overhead to every transaction

### Interview Use
> "For a payment system, I'd use a strict state machine (created → authorized → captured → settled) with every transition as an ACID transaction. Idempotency keys on every API call prevent double-charges on retry. Double-entry bookkeeping ensures every rupee has a debit and credit. Razorpay processes $80B+ annually with this approach — zero tolerance for financial errors."

---

## 2. Database Strategy — MySQL + TiDB

**Source**: "Scaling Razorpay's Database" + Razorpay engineering blog

### Problem
Razorpay's payment data grows rapidly. MySQL worked well initially, but as transaction volume grew to millions per day, single MySQL instances hit write throughput limits. Need horizontal scaling without losing ACID guarantees.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Primary database** | MySQL → migrating to TiDB (MySQL-compatible, distributed) | Cassandra, DynamoDB, CockroachDB | TiDB: MySQL-compatible (no code changes), distributed transactions, horizontal scaling |
| **Shard key** | merchant_id for merchant data, payment_id for transaction data | Random | All data for one merchant on the same shard — no cross-shard queries for merchant dashboard |
| **Read scaling** | Read replicas for dashboard queries | Application-level caching only | Dashboard queries (transaction history, analytics) go to replicas; write path goes to master |
| **Financial data** | Strong consistency (no eventual consistency for money) | Eventual consistency with reconciliation | Real-time balance must be accurate; reconciliation is a backup, not the primary mechanism |
| **Backup** | Continuous backup to S3 + point-in-time recovery | Nightly backup only | Financial data: must recover to the exact second before failure |
| **Migration** | TiDB for new tables; MySQL for legacy; gradual migration | Big-bang migration | Zero downtime; migrate table by table; dual-write during transition |

### Why TiDB over CockroachDB?
- **MySQL compatibility**: Razorpay's entire codebase uses MySQL. TiDB speaks MySQL protocol — most queries work without changes.
- **Distributed transactions**: TiDB supports distributed ACID transactions across shards.
- **Hot standby**: TiDB's Raft-based replication provides automatic failover.
- **Ecosystem**: TiDB has strong adoption in Asia (PingCAP is Asia-based).

### Tradeoff
- ✅ TiDB: MySQL-compatible distributed database — scale without code changes
- ✅ Distributed transactions across shards (critical for financial data)
- ✅ Automatic failover via Raft consensus
- ❌ TiDB is newer than MySQL — smaller community, fewer DBA experts
- ❌ Distributed transactions have higher latency than single-node MySQL
- ❌ Migration from MySQL to TiDB requires careful testing

### Interview Use
> "For a financial system outgrowing single MySQL, I'd consider TiDB — a MySQL-compatible distributed database with ACID transactions. Razorpay is migrating from MySQL to TiDB for horizontal scaling without losing transactions. The key: MySQL compatibility means minimal code changes. The alternative is manual sharding (expensive to maintain) or CockroachDB (PostgreSQL-compatible, not MySQL)."

---

## 3. Event-Driven Architecture with Kafka

**Source**: "Event-Driven Architecture at Razorpay" + engineering blog

### Problem
When a payment succeeds, many things must happen: notify the merchant (webhook), update the dashboard, trigger settlement, update analytics, send receipt email, update fraud models. Can't do all of this synchronously in the payment API call.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Event bus** | Kafka | SQS, RabbitMQ | High throughput, replay capability, multiple consumer groups, ordered per partition |
| **Key events** | payment.authorized, payment.captured, payment.failed, refund.created, settlement.processed | Single "all events" topic | Separate topics per event type; consumers subscribe to what they need |
| **Partition key** | merchant_id | payment_id | All events for one merchant on one partition → ordered processing per merchant |
| **Delivery** | At-least-once + idempotent consumers | Exactly-once | At-least-once is simpler; consumers use payment_id as idempotency key |
| **Schema** | Protobuf with schema registry | JSON | Compact, type-safe, backward/forward compatible; critical for financial events |
| **Retention** | 30 days (for replay and debugging) | 7 days | Financial events may need replay for reconciliation or debugging |

### Event Flow for a Successful Payment
```
Payment captured:
  1. Payment Service → Kafka: "payment.captured" event
  
  Consumer Group "webhooks":
    → Deliver webhook to merchant's URL (with retry)
    
  Consumer Group "settlement":
    → Queue payment for merchant settlement (T+2 days)
    
  Consumer Group "analytics":
    → Update merchant dashboard metrics (real-time)
    
  Consumer Group "receipt":
    → Send payment receipt email to customer
    
  Consumer Group "fraud":
    → Feed payment data to fraud detection model
    
  Consumer Group "ledger":
    → Update double-entry ledger entries
```

### Tradeoff
- ✅ Decoupled: payment API returns in < 500ms; all side effects happen async
- ✅ Multiple independent consumers process the same event
- ✅ 30-day retention enables replay for debugging and reconciliation
- ❌ Eventual consistency for non-payment data (analytics lag by seconds)
- ❌ Kafka operational complexity (managing clusters, monitoring lag)
- ❌ At-least-once means all consumers must be idempotent

### Interview Use
> "For a payment system, I'd use Kafka as the event bus — the payment API completes in < 500ms (just the state change), then publishes an event. Webhooks, settlement, analytics, receipts, and fraud detection all consume independently. Kafka gives ordering per merchant (partition by merchant_id) and 30-day retention for replay. Razorpay uses this pattern to decouple the critical payment path from all downstream processing."

---

## 4. Idempotency — Preventing Double Charges

**Source**: "Idempotency at Razorpay" + engineering blog

### The Problem
The most critical requirement in payments: a retried request must NOT cause a duplicate charge. Sources of retries: client network timeout → retry, webhook delivery retry, load balancer retry, user double-clicks.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Key** | Client-provided idempotency_key header (UUID) | Server-generated | Client controls the key — retries with same key are safe |
| **Storage** | Redis (SETNX with TTL) + MySQL (permanent record) | Redis only | Redis for fast hot-path check; MySQL for permanent record (audit) |
| **TTL** | 24-hour TTL in Redis; permanent in MySQL | Short TTL only | 24h covers most retry scenarios; MySQL is the permanent audit trail |
| **Response** | Return the original response on duplicate request | Return error on duplicate | Returning the original response is transparent to the client — it doesn't know it was a retry |
| **Scope** | Per-API-endpoint per merchant | Global | Same idempotency key for /payments and /refunds are different operations |

### How It Works
```
Request arrives with idempotency_key: "abc123"

1. [Redis] SETNX idem:{merchant}:{endpoint}:abc123 → value = request_hash
   → If SET succeeded (new request): process normally
   → If SET failed (duplicate): go to step 2

2. [Redis/MySQL] Lookup original response for this idempotency key
   → Return original response (200 OK with same body)

3. After processing: store response in MySQL permanently
   → {idempotency_key, merchant_id, endpoint, request_hash, response_body, created_at}

4. Verify request body hash matches (prevent different requests with same key)
   → If hashes don't match → return 409 Conflict (misuse of idempotency key)
```

### Tradeoff
- ✅ Double-charge prevention: retried requests return the original response
- ✅ Transparent to client: client doesn't know if it was a retry or original
- ✅ Request hash verification catches misuse (different request with same key)
- ❌ Redis is the hot-path dependency — if Redis fails, idempotency check fails
- ❌ 24-hour TTL means very late retries (> 24h) won't be caught by Redis (caught by MySQL)
- ❌ Storage overhead for every request's response (MySQL)

### Interview Use
> "For payment idempotency, I'd use a client-provided idempotency key, checked via Redis SETNX (fast, atomic) with MySQL as permanent audit. On duplicate request, return the original response — transparent to the client. Verify request body hash to prevent misuse (same key, different request → 409 Conflict). Razorpay uses this on every API endpoint to prevent double-charges."

---

## 5. Reconciliation — Matching Every Rupee

**Source**: "Building Razorpay's Reconciliation Engine" + engineering blog

### The Problem
In Indian payments, money flows through multiple intermediaries: Customer → Issuing Bank → Payment Gateway (Razorpay) → Acquiring Bank → Merchant. Each intermediary has its own ledger. Reconciliation ensures all ledgers agree — every rupee is accounted for.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Frequency** | Daily batch + continuous stream reconciliation | Daily batch only | Streaming catches discrepancies within hours, not next day |
| **Matching** | Multi-dimensional: amount + payment_id + merchant_id + gateway_ref | Amount-only matching | Multiple fields reduce false matches; gateway_ref links to partner's system |
| **Discrepancy handling** | Auto-resolve common patterns; escalate edge cases to ops | Manual review of everything | 95% of discrepancies are common patterns (timing, rounding); only 5% need humans |
| **Source of truth** | Razorpay's ledger (MySQL) | Bank's ledger | Our ledger is real-time; bank statements arrive T+1; we reconcile against banks |
| **Data sources** | Bank settlement files + gateway APIs + partner reports | Single source | Each payment method (UPI, cards, netbanking) has different reconciliation sources |

### Reconciliation Types

| Type | What | When | How |
|------|------|------|-----|
| **Payment-Gateway** | Razorpay's records vs payment gateway partner records | Continuous (streaming) | Match payment_id + amount + status in both systems |
| **Payment-Bank** | Razorpay's records vs bank settlement file | Daily (T+1 batch) | Bank sends settlement file; match against our records |
| **Internal** | Ledger debit entries vs credit entries | Continuous | Double-entry bookkeeping: total debits must equal total credits |
| **Merchant** | Settlement amount vs sum of captured payments | Per settlement | What we settled to merchant = sum of their captured payments minus fees |

### Common Discrepancy Patterns

| Pattern | Frequency | Auto-Resolution |
|---------|-----------|-----------------|
| **Timing mismatch** (captured today, settled tomorrow) | 60% | Wait for next settlement; auto-match on arrival |
| **Status mismatch** (we say success, gateway says pending) | 20% | Query gateway API; update if actually successful |
| **Amount mismatch** (rounding in different currencies) | 10% | Allow ±₹1 tolerance; flag larger differences |
| **Missing in gateway** (we have record, gateway doesn't) | 5% | Escalate to ops; potential gateway issue |
| **Missing in our system** (gateway has, we don't) | 5% | Create pending record; investigate source |

### Tradeoff
- ✅ Streaming reconciliation catches discrepancies within hours (not next day)
- ✅ Auto-resolution handles 95% of discrepancies without human intervention
- ✅ Multi-source reconciliation covers all payment methods
- ❌ Complex system (multiple data sources, formats, timing)
- ❌ Each payment method has different reconciliation logic
- ❌ Bank settlement files arrive in different formats (CSV, XML, custom)

### Interview Use
> "For a payment system, reconciliation is critical — match every transaction across Razorpay's ledger, the gateway's records, and the bank's settlement files. I'd use streaming reconciliation (catch discrepancies within hours) + daily batch (full reconciliation against bank files). Auto-resolve common patterns (timing, rounding) — only escalate the 5% that need human review. Razorpay reconciles billions of transactions across dozens of payment methods daily."

---

## 6. API Gateway — Traffic Management

**Source**: Razorpay engineering blog on API architecture

### Problem
Razorpay's API serves thousands of merchants, each with different traffic patterns. A flash sale on one merchant's site can spike their API calls 100×. Must protect other merchants from the noisy neighbor and prevent system overload.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Rate limiting** | Per-merchant + per-endpoint (not just global) | Global rate limit only | Merchant A's flash sale shouldn't affect Merchant B's normal traffic |
| **Algorithm** | Token bucket per merchant | Fixed window | Token bucket allows legitimate bursts (flash sale start) while enforcing average rate |
| **Throttling** | Gradual degradation (429 for exceeded, not 503) | Hard rejection | 429 with Retry-After header tells client exactly when to retry |
| **Authentication** | API key + secret (HMAC signature on requests) | API key only | HMAC prevents replay attacks; secret never transmitted over network |
| **Versioning** | URL path versioning (/v1/payments) | Header versioning | Clear, explicit; easy for merchants to understand and migrate |
| **Timeout** | 30 seconds max per API call | No timeout | Financial transactions: if no response in 30s, something is wrong; mark as pending |

### Interview Use
> "For a multi-tenant API (like a payment gateway), per-tenant rate limiting is critical — one merchant's flash sale shouldn't affect others. I'd use token bucket per merchant (allows bursts) with 429 Retry-After responses. HMAC signature for authentication (prevents replay attacks). Razorpay's API serves thousands of merchants with isolated rate limits per merchant."

---

## 7. PHP to Go Migration

**Source**: "Razorpay's Journey from PHP to Go" + GopherCon India talks

### Problem
Razorpay started on PHP (Laravel). As transaction volume grew, PHP's performance became a bottleneck: high memory per process, slow serialization, no native concurrency, and long GC pauses during peak.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Go | Java, Rust, stay on PHP | Go: simple syntax (easy for PHP devs), fast compilation, goroutines, low memory, excellent stdlib |
| **Migration** | Strangler fig (rewrite service by service) | Big-bang rewrite | Rewrite critical services first (payment processing, webhook delivery); keep PHP for low-traffic admin |
| **First service** | Payment processing (highest traffic, most latency-sensitive) | Random | Maximum impact — the bottleneck service migrated first |
| **HTTP framework** | Standard library (net/http) + custom middleware | Gin, Echo, Fiber | Stdlib is fast enough; fewer dependencies = fewer security risks in financial software |
| **Concurrency** | Goroutines for parallel gateway calls | Sequential processing | Payment often calls 2-3 gateways simultaneously (primary + fallback); goroutines parallelize this |

### Results
- **Latency**: P99 reduced from 800ms to 200ms
- **Memory**: 70% reduction per instance
- **Throughput**: 3× more requests per instance
- **Cost**: Significant AWS cost savings (fewer instances for same throughput)

### Tradeoff
- ✅ 4× latency improvement, 70% memory reduction
- ✅ Goroutines enable parallel payment gateway calls
- ✅ Go's simplicity: PHP developers ramped up quickly (2-3 weeks)
- ❌ Migration cost (6+ months for critical services)
- ❌ Go's error handling is verbose (no exceptions)
- ❌ Two codebases during migration (PHP + Go)

### Interview Use
> "Razorpay migrated from PHP to Go — P99 latency dropped from 800ms to 200ms, memory usage dropped 70%. Go's goroutines enabled parallel calls to multiple payment gateways (primary + fallback). The lesson: for financial systems where every millisecond matters, compiled languages with lightweight concurrency (Go, Rust) significantly outperform interpreted languages (PHP, Node.js)."

---

## 8. Distributed Locking — Preventing Race Conditions

**Source**: Razorpay engineering blog on concurrency

### Problem
Multiple API requests for the same payment can arrive simultaneously: user double-clicks, network retries, webhook + API response race. Without locking, two concurrent requests could both authorize the same payment → double charge.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Lock mechanism** | Redis distributed lock (SETNX with TTL) | MySQL SELECT FOR UPDATE, ZooKeeper | Redis: fast (< 1ms), simple, sufficient for Razorpay's needs |
| **Lock key** | payment_id | merchant_id | Payment-level lock: only blocks concurrent operations on the SAME payment |
| **TTL** | 30 seconds (auto-release) | No TTL (manual release) | If the service crashes, the lock auto-releases after 30 seconds; no stuck locks |
| **Retry** | 3 retries with 100ms backoff | No retry | Brief contention is common (double-click); resolve with retry |
| **Scope** | Lock only during state transition | Lock entire payment lifecycle | Only lock during the critical section (authorized → captured); reads don't need lock |

### How It Works
```
Request: Capture payment_id: "pay_123"

1. Acquire lock: SET lock:payment:pay_123 {request_id} NX EX 30
   → Success: proceed
   → Failure: wait 100ms, retry (up to 3 times)
   → Still locked: return 409 "Payment is being processed"

2. Check current state: is payment in "authorized" state?
   → Yes: proceed to capture
   → No: return 400 "Invalid state transition"

3. Call payment gateway: capture the payment

4. Update state: authorized → captured (MySQL transaction)

5. Release lock: DEL lock:payment:pay_123 (only if request_id matches)

6. Publish event: payment.captured → Kafka
```

### Tradeoff
- ✅ Prevents double-charges from concurrent requests
- ✅ Payment-level lock: doesn't block other payments
- ✅ 30s auto-release prevents stuck locks on crash
- ❌ Redis lock is not 100% safe (Redlock debate); acceptable for payment locking (MySQL is backup)
- ❌ Lock contention during peak can add 100-300ms latency
- ❌ Redis must be available for lock operations (critical dependency)

### Interview Use
> "To prevent double-charges from concurrent requests, I'd use Redis distributed locks per payment_id — SETNX with 30-second TTL. Only lock during the state transition (authorized → captured), not the entire lifecycle. The lock auto-releases on crash (TTL). Razorpay uses this to prevent race conditions when user double-clicks 'Pay' or network retries arrive simultaneously."

---

## 9. Webhook Delivery — Reliable Notifications

**Source**: "Reliable Webhook Delivery at Razorpay" + engineering blog

### Problem
When a payment succeeds, Razorpay must notify the merchant via webhook (HTTP POST to their server). Merchant servers may be down, slow, or return errors. Must guarantee delivery — merchants depend on webhooks for order fulfillment.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Delivery guarantee** | At-least-once with retry | At-most-once (fire and forget) | Merchants must receive every payment notification; missed webhook = unfulfilled order |
| **Retry strategy** | Exponential backoff: 5min, 30min, 2h, 12h, 24h | Fixed interval | Gives merchant server time to recover; doesn't overwhelm a struggling server |
| **Max retries** | 24 hours (5 retries) | Infinite retry | After 24 hours, the payment is stale; merchant can poll API instead |
| **Signature** | HMAC-SHA256 signature on webhook body | No signature | Merchant verifies webhook is from Razorpay (not spoofed); prevents fraudulent notifications |
| **Timeout** | 5 second timeout per delivery attempt | No timeout | Slow merchant servers must not block Razorpay's webhook workers |
| **Success criteria** | HTTP 2xx response = delivered; anything else = retry | Any response = success | Only 2xx confirms the merchant received and processed the webhook |
| **Queue** | Kafka → webhook workers (dedicated consumer group) | In-process retry | Kafka decouples webhook delivery from payment processing; independent scaling |

### Webhook Delivery Flow
```
Payment captured:
  1. Kafka event: payment.captured
  2. Webhook Worker picks up event
  3. Construct webhook payload (JSON)
  4. Sign with HMAC-SHA256 (merchant's webhook secret)
  5. POST to merchant's webhook URL
  6. If 2xx → mark as delivered
  7. If timeout/error → schedule retry (exponential backoff)
  8. After 5 retries (24h) → mark as failed, alert merchant via email

Retry schedule:
  Attempt 1: Immediate
  Attempt 2: +5 minutes
  Attempt 3: +30 minutes
  Attempt 4: +2 hours
  Attempt 5: +12 hours
  (total: ~14.5 hours of retries before giving up)
```

### Tradeoff
- ✅ At-least-once guarantees merchants receive payment notifications
- ✅ HMAC signature prevents spoofed webhooks
- ✅ Exponential backoff doesn't overwhelm recovering servers
- ❌ Merchants must handle duplicate webhooks (at-least-once → may receive twice)
- ❌ Up to 5-minute delay on first retry (merchant may wonder why order isn't confirmed)
- ❌ Webhook workers must scale for peak (all merchants' webhooks during sale events)

### Interview Use
> "For reliable webhook delivery, I'd use at-least-once with exponential backoff (5min → 30min → 2h → 12h → give up after 24h). HMAC-SHA256 signature so merchants can verify the source. 5-second timeout per attempt. Razorpay delivers millions of webhooks daily with this pattern — guaranteed delivery as long as the merchant's server is reachable within 24 hours."

---

## 10. UPI Infrastructure — Real-Time Payments

**Source**: Razorpay engineering blog on UPI processing

### Problem
UPI (Unified Payments Interface) is India's most popular payment method — 10B+ transactions/month nationwide. UPI is real-time (settles in seconds, not days). Razorpay must process UPI requests with < 2 second end-to-end latency while handling extreme spikes (festive sales, bill payment deadlines).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Dedicated UPI microservice (separate from card payments) | Single payment service | UPI has fundamentally different flow (collect request → user approves on their app → callback); separate service avoids polluting card flow |
| **Callback handling** | Async callback from NPCI/bank → Kafka → process | Synchronous wait | UPI responses arrive via callback (30s+ after user approves on their phone); can't block |
| **Timeout** | 5-minute timeout for user approval | No timeout | UPI spec: user has up to 5 minutes to approve on their app |
| **Status tracking** | Poll NPCI + listen for callback (belt and suspenders) | Callback only | Callbacks can be lost; periodic polling ensures Razorpay catches completed payments |
| **Concurrency** | Thousands of concurrent pending UPI transactions | Sequential processing | User approval time is unpredictable; must handle thousands of concurrent pending transactions |

### UPI Payment Flow
```
1. Merchant creates payment: POST /v1/payments (with UPI VPA)
2. Razorpay sends collect request to NPCI → NPCI routes to user's bank app
3. Return payment_id to merchant (status: "created")
4. User sees notification on their phone → opens bank app → enters PIN → approves
5. NPCI sends callback to Razorpay: "payment approved" (or "declined")
6. Razorpay updates payment status: created → authorized → captured (auto)
7. Kafka event: payment.captured → webhook to merchant
8. User sees "Payment successful" on merchant's page

Total time: 10-30 seconds (mostly user approval time)
```

### Tradeoff
- ✅ Dedicated UPI service optimized for callback-based flow
- ✅ Belt-and-suspenders: callback + polling catches all completions
- ✅ Handles thousands of concurrent pending transactions
- ❌ UPI's callback model is fundamentally different from card payments (separate code path)
- ❌ 5-minute timeout means resources held for pending transactions
- ❌ NPCI infrastructure sometimes slow/unavailable (requires graceful degradation)

### Interview Use
> "UPI payments are callback-based: create a collect request, then wait for the user to approve on their phone (up to 5 minutes). I'd use an async callback handler (NPCI sends callback to our URL) + periodic polling (belt and suspenders — callbacks can be lost). Dedicate a separate microservice for UPI because its flow is fundamentally different from card payments. Razorpay handles millions of concurrent UPI transactions with this pattern."

---

## 11. Fraud Detection — ML at Payment Speed

**Source**: Razorpay engineering blog on fraud prevention

### Problem
Detect fraudulent payments in real-time (< 200ms) without blocking legitimate payments. False positives (blocking good payments) lose merchants money. False negatives (missing fraud) cause chargebacks.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Approach** | ML model (gradient boosted trees) + rule engine | Rules only | ML catches patterns rules miss; rules handle known fraud patterns instantly |
| **Latency** | < 200ms per payment (inline with payment flow) | Async (post-payment analysis) | Must block fraud before money moves; async detection is too late |
| **Features** | Real-time: IP, device fingerprint, velocity + Historical: merchant fraud rate, user history | Real-time only | Historical features catch repeat offenders; real-time catches new patterns |
| **Threshold** | Risk score 0-100; merchant configures their threshold | Fixed threshold | Different merchants have different risk tolerance (high-value goods vs digital subscriptions) |
| **False positive handling** | 3D Secure challenge (OTP) for medium-risk, block for high-risk | Block all suspicious | Challenge (3DS) verifies the customer without blocking; only block clearly fraudulent |
| **Model updates** | Weekly batch retraining + daily feature updates | Monthly retraining | Fraud patterns evolve quickly; weekly retraining stays current |

### Fraud Decision Flow
```
Payment arrives:
  1. Extract features: IP geolocation, device fingerprint, payment velocity, card BIN
  2. ML model: predict fraud probability (< 50ms)
  3. Rule engine: check against known patterns (< 10ms)
  4. Risk score = max(ML score, rule score)
  
  Score < 30: APPROVE (low risk)
  Score 30-70: CHALLENGE (redirect to 3D Secure / OTP)
  Score > 70: BLOCK (high risk, reject payment)
  
Total latency: < 200ms (runs in parallel with payment preprocessing)
```

### Tradeoff
- ✅ ML catches fraud patterns that rules miss
- ✅ < 200ms latency — inline with payment flow
- ✅ Merchant-configurable thresholds (flexibility)
- ❌ ML model is a black box (harder to explain why a payment was blocked)
- ❌ False positives frustrate legitimate customers
- ❌ Weekly retraining requires ML infrastructure (compute, pipeline, monitoring)

### Interview Use
> "For real-time fraud detection, I'd run an ML model + rule engine in parallel with payment preprocessing — total < 200ms. Low risk → approve, medium risk → 3D Secure challenge (OTP), high risk → block. Merchant-configurable thresholds because risk tolerance varies. Razorpay runs fraud scoring inline with every payment — detecting fraud BEFORE money moves, not after."

---

## 12. Monitoring & Observability

**Source**: Razorpay engineering blog on reliability

### Problem
Payment systems have zero tolerance for undetected failures. A payment gateway going down for 5 minutes without detection means thousands of failed payments and merchant revenue loss.

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Metrics** | Prometheus + Grafana (real-time dashboards) | Industry standard, rich alerting |
| **Key metric** | Payment success rate per gateway per payment method | Single number tells if payments are working |
| **Distributed tracing** | Jaeger (trace every payment through all services) | Pinpoint which service caused a payment failure |
| **Alerting** | Anomaly detection (drop in success rate) + static thresholds | Catches issues before customers notice |
| **Log aggregation** | ELK stack (Elasticsearch + Logstash + Kibana) | Searchable logs for debugging payment failures |
| **SLA monitoring** | Per-gateway, per-payment-method success rate tracked | Different gateways have different SLAs |

### Key Alerts

| Alert | Threshold | Action |
|-------|-----------|--------|
| Payment success rate drop | < 95% for 5 minutes | Page on-call; investigate gateway issues |
| Gateway latency spike | P99 > 5 seconds | Switch traffic to backup gateway |
| Error rate spike | > 5% 5xx errors | Circuit breaker triggers; traffic shifted |
| Kafka consumer lag | > 10K messages | Scale consumers; investigate bottleneck |
| Redis memory | > 80% used | Add nodes or increase eviction |

### Interview Use
> "For a payment system, the #1 metric is payment success rate — broken down by gateway and payment method. A 1% drop in success rate means thousands of lost transactions. I'd alert on anomaly detection (success rate drop) rather than just static thresholds — catches subtle degradation. Distributed tracing (Jaeger) through every payment pinpoints which service failed. Razorpay monitors success rate per-gateway per-method in real-time."

---

## 🎯 Quick Reference: Razorpay's Key Decisions

### Payment Processing
| Decision | Choice | Why |
|----------|--------|-----|
| Consistency | Strong ACID for all payment state changes | Financial data: no eventual consistency for money |
| State management | Strict state machine (created → authorized → captured → settled) | Prevents invalid transitions by design |
| Idempotency | Client key → Redis SETNX → MySQL permanent | Prevents double-charges; returns original response on retry |
| Ledger | Double-entry bookkeeping (integers in paise) | Every rupee has debit + credit; no floating-point errors |
| Reconciliation | Streaming + daily batch; auto-resolve 95% | Catches discrepancies within hours |

### Data & Infrastructure
| System | Choice | Why |
|--------|--------|-----|
| Database | MySQL → TiDB (MySQL-compatible distributed) | ACID transactions at scale; minimal code changes |
| Event bus | Kafka (partition by merchant_id) | Decouple payment from webhooks, analytics, settlement |
| Locking | Redis SETNX per payment_id (30s TTL) | Prevent concurrent state transitions; auto-release |
| Language | PHP → Go | 4× latency reduction, 70% memory savings |
| Fraud | ML + rules inline (< 200ms) | Block fraud before money moves |

### Indian Payment Specifics
| Payment Method | Architecture Decision | Why |
|---------------|----------------------|-----|
| UPI | Separate microservice, callback + polling | Fundamentally different flow (async user approval) |
| Cards | Synchronous gateway call with 30s timeout | Standard authorization flow |
| Netbanking | Redirect flow + callback | Bank-specific redirect pages |
| Wallets | API integration per wallet provider | Each wallet has its own API |
| Reconciliation | Multi-source: bank files + gateway APIs + NPCI | Each method has different settlement processes |

---

## 🗣️ How to Use Razorpay Examples in Interviews

### Example Sentences
- "For payment processing, I'd use a strict state machine like Razorpay — created → authorized → captured → settled. Every transition is an ACID transaction with optimistic locking."
- "Idempotency is critical: client-provided key, Redis SETNX for fast check, MySQL for permanent audit. Return the original response on duplicate — transparent to the client."
- "Razorpay reconciles every rupee: streaming recon catches discrepancies in hours, daily batch against bank files for completeness. Auto-resolve 95% of common patterns."
- "For webhook delivery, at-least-once with exponential backoff (5min → 30min → 2h → 12h). HMAC signature for verification. Razorpay retries for up to 24 hours."
- "UPI payments need a separate service — callback-based flow is fundamentally different from synchronous card payments. Belt-and-suspenders: callback + polling."
- "Razorpay migrated from PHP to Go — 4× latency improvement. Goroutines enable parallel calls to primary + fallback payment gateways."
- "Per-merchant rate limiting (not just global) — one merchant's flash sale shouldn't affect others. Token bucket allows legitimate bursts."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 12 design decisions focused on payment processing, financial consistency, and Indian payment infrastructure  
**Status**: Complete & Interview-Ready ✅
