# 🎯 Topic 53: Payment & Financial Systems — Complete Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering payment processing, idempotency, double-entry bookkeeping, PCI compliance, fraud detection, currency handling, reconciliation, Stripe/payment provider integration, and real-world financial system architectures at scale.

---

## Table of Contents

1. [Core Concept — Why Payments Are Hard](#core-concept--why-payments-are-hard)
2. [Payment Processing Flow](#payment-processing-flow)
3. [Idempotency — The #1 Rule of Payments](#idempotency--the-1-rule-of-payments)
4. [Double-Entry Bookkeeping](#double-entry-bookkeeping)
5. [Money Representation — Never Use Floats](#money-representation--never-use-floats)
6. [Payment States & State Machine](#payment-states--state-machine)
7. [PCI Compliance & Tokenization](#pci-compliance--tokenization)
8. [Stripe Integration Patterns](#stripe-integration-patterns)
9. [Retry & Error Handling](#retry--error-handling)
10. [Fraud Detection](#fraud-detection)
11. [Currency & International Payments](#currency--international-payments)
12. [Refunds & Chargebacks](#refunds--chargebacks)
13. [Reconciliation — Balancing the Books](#reconciliation--balancing-the-books)
14. [Wallet & Balance Systems](#wallet--balance-systems)
15. [Subscription & Recurring Billing](#subscription--recurring-billing)
16. [Real-World: Stripe Architecture](#real-world-stripe-architecture)
17. [Real-World: E-Commerce Checkout (Amazon-like)](#real-world-e-commerce-checkout)
18. [Real-World: Peer-to-Peer (Venmo/PayPal)](#real-world-peer-to-peer)
19. [Real-World: Marketplace (Uber/Airbnb)](#real-world-marketplace)
20. [AWS Architecture for Payments](#aws-architecture-for-payments)
21. [Interview Talking Points](#interview-talking-points)
22. [Common Interview Mistakes](#common-interview-mistakes)
23. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept — Why Payments Are Hard

```
Normal system bug: User sees wrong name → fix it, deploy, done
Payment bug:       User charged twice → legal liability, reputation damage, regulatory fine

Why financial systems are different:
1. MONEY CANNOT DISAPPEAR: Every cent must be accounted for
2. MONEY CANNOT DUPLICATE: Charging twice = fraud (you're liable!)
3. CONSISTENCY > AVAILABILITY: Better to reject a payment than process it twice
4. AUDITABILITY: Every transaction must be traceable (for years)
5. COMPLIANCE: PCI DSS, SOX, AML, KYC — regulatory requirements
6. RECONCILIATION: Your records MUST match bank records (to the cent)

Key principle: In payments, EXACTLY-ONCE semantics matter more than anywhere else.
In practice: AT-LEAST-ONCE delivery + IDEMPOTENCY = EXACTLY-ONCE processing
```

---

## Payment Processing Flow

### Simple Card Payment Flow

```
Customer                  Merchant              Payment Gateway         Card Network        Issuing Bank
   │                        │                       │                     │                    │
   │ "Buy for $49.99"       │                       │                     │                    │
   │───────────────────────→│                       │                     │                    │
   │                        │ Charge(card_token,     │                     │                    │
   │                        │  $49.99, idem_key)     │                     │                    │
   │                        │──────────────────────→│                     │                    │
   │                        │                       │ Authorization Request│                    │
   │                        │                       │────────────────────→│                    │
   │                        │                       │                     │ "Does cardholder   │
   │                        │                       │                     │  have $49.99?"     │
   │                        │                       │                     │───────────────────→│
   │                        │                       │                     │                    │
   │                        │                       │                     │ "Yes, funds held"  │
   │                        │                       │                     │←───────────────────│
   │                        │                       │ Authorization Approved│                   │
   │                        │                       │←────────────────────│                    │
   │                        │  {status: "succeeded", │                     │                    │
   │                        │   charge_id: "ch_abc"} │                     │                    │
   │                        │←──────────────────────│                     │                    │
   │ "Payment successful!"  │                       │                     │                    │
   │←───────────────────────│                       │                     │                    │
```

### Two-Phase: Authorize → Capture

```
# Phase 1: AUTHORIZE (hold funds, don't charge yet)
# "Does the card have $49.99?" → "Yes, I've held it for 7 days"
authorize(card_token, $49.99) → auth_id

# Phase 2: CAPTURE (actually move the money)
# "OK, charge the held amount" → "Done, money transferred"
capture(auth_id, $49.99) → charge_id

# WHY two phases?
# Hotels: Authorize at check-in, capture at checkout (final amount may differ)
# E-commerce: Authorize at order, capture at shipment
# Rideshare: Authorize estimated fare, capture actual fare after ride

# If you don't capture within 7 days → authorization expires → funds released
# You can capture LESS than authorized (e.g., auth $100, capture $85 if item out of stock)
```

---

## Idempotency — The #1 Rule of Payments

### The Problem

```
Scenario 1: Network timeout
  Client → "Charge $49.99" → Server → Stripe → SUCCESS
  Client ← ??? (network timeout, never got response)
  Client → "Charge $49.99" → Server → Stripe → CHARGED AGAIN! 💀

Scenario 2: Client retry
  Client → "Charge $49.99" → 500 error (temporary)
  Client automatically retries → "Charge $49.99" → SUCCESS
  But first request also succeeded (just returned error to client)
  → Customer charged TWICE! 💀

Scenario 3: User double-click
  User clicks "Pay" → request 1 sent
  User clicks "Pay" again → request 2 sent
  Both succeed → DOUBLE CHARGE! 💀
```

### The Solution: Idempotency Keys

```
# Every payment request includes a UNIQUE idempotency key
# If same key sent twice → return the SAME result (don't re-process)

# Client generates key BEFORE sending:
POST /api/charges
Idempotency-Key: idem_ord123_pay_20240115_v1
{
  "amount_cents": 4999,
  "currency": "usd",
  "card_token": "tok_abc"
}

# Server implementation:
def process_payment(request):
    idem_key = request.headers["Idempotency-Key"]
    
    # Check if we've seen this key before
    existing = db.get(idempotency_key=idem_key)
    if existing:
        return existing.response  # Return SAME response as first time
    
    # First time: Process the payment
    result = stripe.charge(amount=4999, token="tok_abc", idempotency_key=idem_key)
    
    # Store the result (so future retries return this)
    db.save(idempotency_key=idem_key, response=result, expires_at=now()+24h)
    
    return result
```

### Database Implementation

```sql
-- Idempotency key table
CREATE TABLE idempotency_keys (
    key             VARCHAR(255) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,  -- SHA256 of request body (detect misuse)
    response_code   INTEGER,
    response_body   JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    expires_at      TIMESTAMPTZ DEFAULT NOW() + INTERVAL '24 hours'
);

-- Payment with idempotency (PostgreSQL)
INSERT INTO payments (id, amount_cents, idempotency_key, status)
VALUES (gen_random_uuid(), 4999, 'idem_ord123_pay', 'processing')
ON CONFLICT (idempotency_key) DO NOTHING  -- If key exists → no duplicate!
RETURNING *;
-- If RETURNING is empty → it was a duplicate → fetch existing result

-- DynamoDB: ConditionExpression: "attribute_not_exists(idempotency_key)"
```

### Idempotency Key Best Practices

```
✅ Key format: {entity_id}_{action}_{timestamp_or_version}
   "order_123_payment_v1"
   "refund_456_20240115"
   "subscription_789_renewal_202401"

✅ Client generates the key (not server)
   → Client can safely retry with same key

✅ Key expires after 24-48 hours
   → Prevents table from growing forever
   → After expiry: Same key treated as new request

✅ Validate request body matches
   → If same key but different amount → reject (misuse!)
   → SHA256(request_body) stored alongside key

❌ Don't use random UUIDs as idempotency keys
   → Defeats the purpose (every retry gets new key)
   
❌ Don't generate key on server
   → Client can't retry safely (doesn't know the key)
```

---

## Double-Entry Bookkeeping

### The Principle

```
EVERY financial transaction creates TWO entries:
  One DEBIT (money leaves an account)
  One CREDIT (money enters an account)

The sum of ALL debits and credits in the entire system = ZERO
If it's not zero → something is WRONG (money appeared or disappeared)

Example: Customer pays $49.99 for an order
  DEBIT:  Customer Account      -$49.99  (money leaves customer)
  CREDIT: Merchant Revenue       +$49.99  (money enters merchant)
  
  SUM: -49.99 + 49.99 = $0.00 ✅

Example: Merchant refunds $10.00
  DEBIT:  Merchant Revenue       -$10.00  (money leaves merchant)
  CREDIT: Customer Account       +$10.00  (money returns to customer)
  
  SUM of all entries: (-49.99 + 49.99) + (-10.00 + 10.00) = $0.00 ✅
```

### Database Schema

```sql
CREATE TABLE ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID NOT NULL,          -- Groups related entries
    account_id      UUID NOT NULL,
    entry_type      VARCHAR(10) NOT NULL CHECK (entry_type IN ('debit', 'credit')),
    amount_cents    BIGINT NOT NULL CHECK (amount_cents > 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    description     VARCHAR(255),
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- INVARIANT: For every transaction_id, sum of credits = sum of debits
-- Verified by: 
SELECT transaction_id,
       SUM(CASE WHEN entry_type = 'credit' THEN amount_cents ELSE 0 END) as credits,
       SUM(CASE WHEN entry_type = 'debit' THEN amount_cents ELSE 0 END) as debits
FROM ledger_entries
GROUP BY transaction_id
HAVING SUM(CASE WHEN entry_type = 'credit' THEN amount_cents ELSE 0 END) 
    != SUM(CASE WHEN entry_type = 'debit' THEN amount_cents ELSE 0 END);
-- This query should ALWAYS return ZERO rows

-- Account balance (derived from ledger):
SELECT SUM(CASE WHEN entry_type = 'credit' THEN amount_cents 
                WHEN entry_type = 'debit' THEN -amount_cents END) as balance_cents
FROM ledger_entries
WHERE account_id = 'merchant_123';
```

### Why Double-Entry?

```
1. AUDITABILITY: Every dollar has a source and destination
2. ERROR DETECTION: If sum ≠ 0, you know something is wrong
3. DEBUGGING: "Where did this $49.99 come from?" → follow the entries
4. RECONCILIATION: Compare your ledger against bank statements
5. REGULATORY: Required for financial reporting (SOX compliance)
6. IMMUTABILITY: Entries are NEVER deleted or modified → append-only
   To "undo": Create new REVERSE entries (not delete originals)
```

---

## Money Representation — Never Use Floats

### The Problem with Floats

```python
# ❌ NEVER DO THIS:
>>> 0.1 + 0.2
0.30000000000000004

>>> 19.99 * 100
1998.9999999999998

# Customer should pay $19.99 but you charge $19.98 → you lose money
# Or $20.00 → you overcharge → lawsuit!
```

### The Solution: Integer Cents

```
# ✅ ALWAYS represent money as INTEGER CENTS:
$19.99 → 1999 (integer)
$0.01  → 1 (integer)
$1,000.00 → 100000 (integer)

# Database: BIGINT (not DECIMAL, not FLOAT)
amount_cents BIGINT NOT NULL CHECK (amount_cents >= 0)

# Display: Divide by 100 only for UI
display_price = f"${amount_cents / 100:.2f}"

# Arithmetic is EXACT:
1999 + 1 = 2000  ✅ (not 1999.9999998)
1999 * 3 = 5997  ✅ (not 5996.999999)
```

### Multi-Currency

```
# Different currencies have different decimal places:
USD: 2 decimals → multiply by 100 (cents)
JPY: 0 decimals → multiply by 1 (yen is smallest unit)
BHD: 3 decimals → multiply by 1000 (fils)
BTC: 8 decimals → multiply by 100,000,000 (satoshis)

# Store:
{
  "amount": 1999,        // Smallest unit (cents for USD)
  "currency": "usd",     // ISO 4217 code
  "exponent": 2           // Divide by 10^2 to get display amount
}

# Or use a currency library that handles this:
# Java: java.util.Currency + BigDecimal
# Python: decimal.Decimal or money library
# JavaScript: dinero.js or currency.js
```

---

## Payment States & State Machine

```
┌──────────────────────────────────────────────────────────────┐
│                 PAYMENT STATE MACHINE                         │
│                                                              │
│  ┌──────────┐    ┌────────────┐    ┌───────────┐            │
│  │ CREATED  │───→│ PROCESSING │───→│ SUCCEEDED │            │
│  └──────────┘    └─────┬──────┘    └─────┬─────┘            │
│                        │                  │                   │
│                        ▼                  ▼                   │
│                 ┌──────────┐      ┌────────────┐            │
│                 │  FAILED  │      │ REFUNDING  │            │
│                 └──────────┘      └──────┬─────┘            │
│                        │                  │                   │
│                        ▼                  ▼                   │
│                 ┌──────────┐      ┌────────────┐            │
│                 │ RETRYING │      │  REFUNDED  │            │
│                 └──────────┘      └────────────┘            │
│                                                              │
│  Valid transitions:                                          │
│  CREATED → PROCESSING (submitted to provider)                │
│  PROCESSING → SUCCEEDED (provider confirmed)                 │
│  PROCESSING → FAILED (provider declined/error)               │
│  FAILED → RETRYING (eligible for retry)                      │
│  RETRYING → PROCESSING (resubmitted)                         │
│  SUCCEEDED → REFUNDING (refund initiated)                    │
│  REFUNDING → REFUNDED (refund confirmed)                     │
│                                                              │
│  INVALID transitions (must be prevented!):                    │
│  SUCCEEDED → PROCESSING (can't reprocess success!)           │
│  REFUNDED → SUCCEEDED (can't un-refund!)                     │
│  FAILED → SUCCEEDED (only via RETRYING)                       │
└──────────────────────────────────────────────────────────────┘
```

### State Machine in Database

```sql
-- PostgreSQL: Enforce valid transitions with CHECK + trigger
UPDATE payments 
SET status = 'processing'
WHERE id = 'pay_123' 
AND status = 'created'  -- Only transition from CREATED → PROCESSING
RETURNING *;

-- If 0 rows updated → invalid transition → reject!
-- This is an ATOMIC state transition — no race conditions

-- DynamoDB: Conditional update
UpdateItem:
  Key: { PK: "PAY#pay_123" }
  UpdateExpression: "SET #status = :new"
  ConditionExpression: "#status = :expected"
  ExpressionAttributeValues: { ":new": "processing", ":expected": "created" }
-- ConditionalCheckFailedException → invalid transition → reject!
```

---

## PCI Compliance & Tokenization

### Never Touch Card Numbers

```
# PCI DSS has 315 requirements for handling card data
# Solution: LET STRIPE/BRAINTREE HANDLE IT

# ❌ BAD: Card number goes to YOUR server
Browser → Your API → {"card": "4111111111111111"} → Store in DB
→ You're PCI DSS Level 1 (315 requirements, annual audit, $$$)

# ✅ GOOD: Card number goes DIRECTLY to Stripe
Browser → Stripe.js SDK → Stripe's servers → returns token "tok_abc123"
Browser → Your API → {"card_token": "tok_abc123"} → Charge via Stripe API
→ You're PCI DSS SAQ A (22 requirements, self-assessment questionnaire)

# Tokenization flow:
1. Browser loads Stripe Elements (Stripe's own iframe for card input)
2. User types card number → data goes directly to Stripe (NEVER your server)
3. Stripe returns a one-time token: tok_abc123
4. Your frontend sends token to your backend
5. Your backend calls Stripe API: stripe.charges.create(token="tok_abc123", amount=4999)
6. Stripe charges the card, returns charge_id
7. You store charge_id (NOT card number) in your database

# Your servers NEVER see the card number = minimal PCI scope
```

---

## Stripe Integration Patterns

### Payment Intent Flow (Modern)

```python
# Step 1: Create PaymentIntent (server-side)
intent = stripe.PaymentIntent.create(
    amount=4999,                    # $49.99 in cents
    currency="usd",
    idempotency_key="order_123_payment_v1",
    metadata={"order_id": "123", "user_id": "456"},
    # payment_method_types=["card"],  # Auto-detected
)
# Returns: { id: "pi_abc", client_secret: "pi_abc_secret_xyz", status: "requires_payment_method" }

# Step 2: Confirm payment (client-side with Stripe.js)
# stripe.confirmCardPayment(clientSecret, { payment_method: { card: cardElement } })
# → Stripe handles 3D Secure (SCA), redirects, authentication

# Step 3: Webhook confirmation (server-side)
# Stripe sends webhook: payment_intent.succeeded
# CRITICAL: Don't fulfill order from client callback — use webhook!
# Client can be manipulated; webhook is server-to-server (trusted)

@app.post("/webhooks/stripe")
def handle_webhook(payload, signature):
    event = stripe.Webhook.construct_event(payload, signature, webhook_secret)
    
    if event.type == "payment_intent.succeeded":
        order_id = event.data.object.metadata["order_id"]
        fulfill_order(order_id)
    
    elif event.type == "payment_intent.payment_failed":
        order_id = event.data.object.metadata["order_id"]
        notify_customer_payment_failed(order_id)
    
    return {"status": "ok"}
```

### Webhook Best Practices

```
1. VERIFY SIGNATURE: stripe.Webhook.construct_event(payload, sig, secret)
   → Prevents attackers from sending fake webhooks

2. IDEMPOTENT HANDLER: Same event may be delivered multiple times
   → Check: if order already fulfilled → skip (don't double-ship!)

3. RESPOND QUICKLY: Return 200 within 5 seconds
   → If slow → Stripe retries → duplicate processing risk
   → Process async: Save event to SQS → respond 200 → process from queue

4. RETRY HANDLING: Stripe retries failed webhooks for 72 hours
   → Your handler must be idempotent!

5. EVENT ORDERING: Events may arrive out of order
   → Check payment status before acting (not just event type)
```

---

## Retry & Error Handling

### Payment Error Categories

```
# Category 1: TRANSIENT (retry)
# Network timeout, 500 from provider, rate limited
# → Retry with exponential backoff: 1s, 2s, 4s, 8s
# → Max 3 retries

# Category 2: SOFT DECLINE (retry with changes)
# Insufficient funds → retry tomorrow (maybe payday)
# Card expired → ask for new card
# 3DS authentication required → redirect user to authenticate

# Category 3: HARD DECLINE (don't retry)
# Stolen card → block immediately
# Invalid card number → ask user to re-enter
# Fraud detected → flag account

# Category 4: UNKNOWN (investigate)
# Timeout after submission → DID IT GO THROUGH?
# → Check with Stripe: GET /v1/payment_intents/{id}
# → If status=succeeded → don't retry → fulfill order
# → If status=requires_payment_method → retry is safe
# → NEVER assume failure on timeout — always check!
```

### Timeout Handling — The Scariest Scenario

```
Your Server → Stripe API → ... timeout ...

DID STRIPE CHARGE THE CARD?
  Option A: Yes, but response was lost → Customer charged, you don't know
  Option B: No, request didn't reach Stripe → Nothing happened

HOW TO HANDLE:
1. Use idempotency key (Stripe supports it natively)
2. On timeout → query Stripe for the PaymentIntent status
3. If succeeded → fulfill order (don't charge again!)
4. If not → retry with SAME idempotency key (safe — Stripe deduplicates)

# Stripe's idempotency guarantee:
# Same idempotency key within 24 hours → returns cached response
# → Safe to retry ANY number of times
```

---

## Fraud Detection

### Rule-Based Fraud Detection

```
# Quick wins — block obvious fraud:
Rule 1: Same card → 5+ charges in 10 minutes → BLOCK
Rule 2: Order amount > $5,000 and new account (< 1 day old) → REVIEW
Rule 3: Shipping address different country than billing → FLAG
Rule 4: 3+ failed payment attempts then success → FLAG
Rule 5: Known fraudulent IP ranges / email patterns → BLOCK
Rule 6: Velocity: Same email → 10+ orders in 24h → BLOCK

# Stripe Radar:
# ML-based fraud detection built into Stripe
# Analyzes: device fingerprint, behavioral signals, card history
# Returns risk score (0-100) with each charge
# Rules: if risk_score > 75 → require 3D Secure authentication
```

### Fraud Prevention Architecture

```
Order Request → Rate Limiter → Fraud Rules Engine → Payment Processor
                                     │
                              ┌──────┴──────┐
                              │   ML Model   │  (trained on historical fraud data)
                              │  Risk Score  │
                              └──────┬──────┘
                                     │
                              Score < 30 → Auto-approve
                              Score 30-75 → 3D Secure (extra authentication)
                              Score > 75 → Block + alert fraud team
```

---

## Refunds & Chargebacks

### Refund Flow

```sql
-- Refund is a NEW transaction, not a deletion of the original!
BEGIN;
  -- Create refund record
  INSERT INTO refunds (payment_id, amount_cents, reason, status)
  VALUES ('pay_123', 4999, 'customer_request', 'processing');
  
  -- Double-entry: Reverse the original charge
  INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount_cents)
  VALUES ('refund_456', 'merchant_revenue', 'debit', 4999);   -- Money leaves merchant
  INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount_cents)
  VALUES ('refund_456', 'customer_account', 'credit', 4999);  -- Money returns to customer
  
  -- Call Stripe
  -- stripe.Refund.create(charge="ch_abc", amount=4999)
COMMIT;

-- PARTIAL refund: Refund $10 of $49.99 order
-- Same flow but amount_cents = 1000 (not full amount)
```

### Chargebacks (Disputes)

```
# Chargeback = Customer tells their BANK "I didn't make this purchase"
# Bank reverses the charge → merchant loses money + $15-25 fee

# Timeline:
# Day 0: Customer disputes charge with bank
# Day 1-3: Stripe notifies merchant (webhook: charge.dispute.created)
# Day 3-21: Merchant submits evidence (receipt, shipping proof, etc.)
# Day 21-75: Bank reviews and decides
# → Merchant wins: Money returned
# → Merchant loses: Money gone + dispute fee ($15-25)

# Prevention:
# 1. Clear billing descriptor: "MYSTORE*ORDER123" (not cryptic codes)
# 2. Prompt customer service: Resolve complaints before chargeback
# 3. Delivery confirmation: Track shipments, require signature
# 4. 3D Secure: Shifts liability to card issuer (merchant protected!)
# 5. Clear refund policy: Easy refunds = fewer chargebacks
```

---

## Reconciliation — Balancing the Books

```
# DAILY: Compare your records against payment provider's records

Your Database:
  Payment pay_123: $49.99, status=succeeded, created=2024-01-15
  Payment pay_456: $29.99, status=succeeded, created=2024-01-15
  Total: $79.98

Stripe Dashboard:
  Charge ch_abc: $49.99, status=succeeded
  Charge ch_def: $29.99, status=succeeded
  Total: $79.98

Match? ✅ → All good

Mismatch scenarios:
  Your DB says succeeded but Stripe says failed → BUG: Order fulfilled but not paid!
  Stripe says succeeded but your DB says failed → BUG: Customer charged but order not fulfilled!
  
# Reconciliation job (run daily):
1. Fetch all Stripe charges for yesterday
2. Compare against your payment records
3. Flag mismatches for manual review
4. Generate report: matched, unmatched, discrepancies

# Bank reconciliation (monthly):
# Compare Stripe payouts against bank deposits
# Stripe settles: gross charges - fees - refunds - disputes = net payout
# Your bank should receive exactly that amount
```

---

## Wallet & Balance Systems

### Wallet Schema

```sql
CREATE TABLE wallets (
    id          UUID PRIMARY KEY,
    user_id     UUID UNIQUE NOT NULL REFERENCES users(id),
    balance_cents BIGINT NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
    currency    VARCHAR(3) DEFAULT 'USD',
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE wallet_transactions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id   UUID NOT NULL REFERENCES wallets(id),
    type        VARCHAR(20) NOT NULL CHECK (type IN ('credit', 'debit', 'hold', 'release')),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    reference_id VARCHAR(255),  -- order_id, refund_id, transfer_id
    description VARCHAR(255),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Atomic debit (prevent negative balance):
UPDATE wallets 
SET balance_cents = balance_cents - 4999, updated_at = NOW()
WHERE id = 'wallet_abc' AND balance_cents >= 4999
RETURNING balance_cents;
-- If 0 rows affected → insufficient balance → reject transaction!

-- Balance = SUM of all transactions (for verification):
SELECT SUM(CASE WHEN type = 'credit' THEN amount_cents 
                WHEN type = 'debit' THEN -amount_cents 
                ELSE 0 END) as calculated_balance
FROM wallet_transactions
WHERE wallet_id = 'wallet_abc';
-- Should match wallets.balance_cents — if not, investigate!
```

---

## Subscription & Recurring Billing

```
# Subscription lifecycle:
TRIAL → ACTIVE → PAST_DUE → CANCELLED / ACTIVE (if payment recovered)

# Billing:
# 1. Subscription renewal date arrives
# 2. System attempts charge: stripe.Invoice.create(customer, amount)
# 3. SUCCESS → Extend subscription, send receipt
# 4. FAIL → Enter "grace period" (3-7 days)
#    → Retry: Day 1, Day 3, Day 5 (smart retry — different times)
#    → Send dunning emails: "Update your payment method"
# 5. All retries fail → Cancel subscription

# Proration:
# User upgrades mid-cycle: Basic ($10/mo) → Pro ($30/mo)
# Day 15 of 30-day cycle → 50% of month remaining
# Prorate: Credit $5 (unused Basic) + Charge $15 (half Pro) = Net $10

# Stripe handles all this natively:
stripe.Subscription.modify(subscription_id, 
    items=[{"price": "price_pro"}],
    proration_behavior="create_prorations")
```

---

## Real-World: Stripe Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    STRIPE ARCHITECTURE                        │
│                                                              │
│  API Layer:                                                  │
│  Load Balancer → API Servers (Ruby/Java)                    │
│  → Authentication (API key / OAuth)                          │
│  → Rate limiting (per-key)                                   │
│  → Request validation                                        │
│  → Idempotency check                                         │
│                                                              │
│  Processing Layer:                                           │
│  → Payment routing (choose optimal card network)             │
│  → Fraud detection (Stripe Radar, ML model)                 │
│  → 3D Secure orchestration                                   │
│  → Card network communication (Visa, Mastercard, AMEX)      │
│                                                              │
│  Data Layer:                                                 │
│  → PostgreSQL (primary data store — ACID transactions!)     │
│  → Double-entry ledger (every cent accounted for)           │
│  → Event sourcing (complete audit trail)                    │
│                                                              │
│  Async Layer:                                                │
│  → Webhook delivery (guaranteed, with retries)              │
│  → Reconciliation jobs (daily)                              │
│  → Reporting & analytics                                     │
│                                                              │
│  Key decisions:                                              │
│  • PostgreSQL (not NoSQL) — ACID matters for money          │
│  • Idempotency at every layer                                │
│  • Event sourcing — never delete, always append              │
│  • Multi-region — active-passive with synchronous replication│
└─────────────────────────────────────────────────────────────┘
```

---

## Real-World: E-Commerce Checkout

```
┌─────────────────────────────────────────────────────────────┐
│              E-COMMERCE CHECKOUT FLOW                         │
│                                                              │
│  1. Cart → API: Create Order (status=PENDING, idem_key)     │
│     → DynamoDB: Save order with items, prices, user         │
│     → Reserve inventory (DDB conditional write)              │
│                                                              │
│  2. Payment → Stripe PaymentIntent                          │
│     → Client: Stripe.js confirms payment (handles 3DS)      │
│     → Webhook: payment_intent.succeeded                     │
│                                                              │
│  3. Fulfillment (triggered by webhook):                     │
│     → SQS: publish OrderPaid event                          │
│     → Lambda: Update order status to PAID                   │
│     → Lambda: Send confirmation email (SES)                 │
│     → Lambda: Notify warehouse (SQS)                        │
│     → Lambda: Create ledger entries (double-entry)          │
│                                                              │
│  4. Shipping:                                                │
│     → Warehouse ships → tracking number                     │
│     → Update order status to SHIPPED                        │
│     → Capture payment (if using auth+capture)               │
│                                                              │
│  5. Delivery:                                                │
│     → Carrier confirms delivery                              │
│     → Update order status to DELIVERED                      │
│     → Release inventory hold                                 │
│                                                              │
│  Error paths:                                                │
│  • Payment fails → Release inventory reservation            │
│  • Webhook timeout → SQS DLQ → manual investigation         │
│  • Double webhook → Idempotent handler (check order status) │
└─────────────────────────────────────────────────────────────┘
```

---

## Real-World: Peer-to-Peer (Venmo/PayPal)

```
Alice sends $50 to Bob:

1. Alice → POST /transfer { from: "alice", to: "bob", amount: 5000, idem_key }
2. Validate: Alice balance >= $50? → YES

3. ATOMIC TRANSACTION:
   BEGIN;
     UPDATE wallets SET balance = balance - 5000 WHERE user_id = 'alice' AND balance >= 5000;
     UPDATE wallets SET balance = balance + 5000 WHERE user_id = 'bob';
     INSERT INTO ledger (txn, account, type, amount) VALUES ('txn_1', 'alice', 'debit', 5000);
     INSERT INTO ledger (txn, account, type, amount) VALUES ('txn_1', 'bob', 'credit', 5000);
   COMMIT;

4. If Alice has insufficient balance → ROLLBACK → return error
5. If DB failure mid-transaction → ROLLBACK → money safe (ACID!)

# WHY PostgreSQL (not DynamoDB)?
# DynamoDB: Can't do cross-item transactions (Alice and Bob = different items)
# PostgreSQL: ACID transaction across multiple rows → atomically safe
# DynamoDB TransactWriteItems: Up to 100 items, but more complex + limited
```

---

## Real-World: Marketplace (Uber/Airbnb)

```
# Marketplace payment: Customer → Platform → Provider (driver/host)

# Stripe Connect: Designed for marketplaces

Customer pays $100 for Airbnb stay:
  1. Customer → Stripe: Pay $100 to Airbnb's platform account
  2. Airbnb takes 15% fee: $15 → Airbnb's account
  3. Host gets 85%: $85 → Host's Stripe Connect account
  4. Stripe's processing fee: ~$3.20 → Stripe

# Stripe Connect account types:
# Standard: Host has full Stripe account (Stripe dashboard, bank link)
# Express: Simplified onboarding (Airbnb-branded flow)
# Custom: Full control (you build the UI, handle compliance)

# Split payment example:
stripe.PaymentIntent.create(
    amount=10000,  # $100
    currency="usd",
    transfer_data={"destination": "acct_host_123"},  # Host's connected account
    application_fee_amount=1500,  # $15 platform fee
)
```

---

## AWS Architecture for Payments

```
┌─────────────────────────────────────────────────────────────┐
│         AWS PAYMENT ARCHITECTURE                              │
│                                                              │
│  CloudFront + WAF ──→ API Gateway (Cognito auth)            │
│                              │                               │
│                    Lambda: CreatePaymentIntent                │
│                    ├── Secrets Manager (Stripe API key)       │
│                    ├── DynamoDB (orders, idempotency keys)   │
│                    └── Stripe API (create payment intent)    │
│                                                              │
│  Stripe Webhook ──→ API Gateway ──→ Lambda: HandleWebhook   │
│                              │                               │
│                    ├── Verify webhook signature               │
│                    ├── SQS: order-fulfillment queue          │
│                    └── DynamoDB: update order status          │
│                                                              │
│  SQS ──→ Lambda: FulfillOrder                               │
│           ├── DynamoDB: update order PAID                    │
│           ├── SES: send confirmation email                   │
│           ├── Aurora PostgreSQL: ledger entries (double-entry)│
│           └── SNS: notify downstream services               │
│                                                              │
│  Aurora PostgreSQL: Financial ledger (ACID required!)        │
│  ├── ledger_entries table (append-only, immutable)          │
│  ├── Daily reconciliation job (EventBridge → Lambda)        │
│  └── KMS encryption on all financial data                   │
│                                                              │
│  WHY Aurora (not DynamoDB) for ledger?                      │
│  → ACID transactions for double-entry bookkeeping           │
│  → Complex aggregation queries for reconciliation           │
│  → JOINs for financial reporting                             │
│  → DynamoDB for orders (key-value, high scale)              │
│  → Aurora for money (ACID, SQL, reporting)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Interview Talking Points

### "Design a payment system"

> *"I'd design for exactly-once processing using idempotency keys on every payment request. The payment flow: client creates a PaymentIntent via our API → Stripe handles card authentication (3DS) → webhook confirms payment → we fulfill the order. Card numbers never touch our servers — Stripe tokenization reduces PCI scope to SAQ-A. All financial data uses double-entry bookkeeping in Aurora PostgreSQL for ACID guarantees — every charge creates a debit and credit entry that must sum to zero. Money is stored as integer cents (BIGINT, never float) with ISO 4217 currency codes. For error handling: transient errors get retried with exponential backoff; on timeout, we query the PaymentIntent status before retrying (never assume failure). Daily reconciliation compares our ledger against Stripe's records to catch any discrepancies."*

### "How do you prevent double-charging?"

> *"Three layers of protection. First, client generates an idempotency key before sending the request — 'order_123_payment_v1'. Our API checks: has this key been processed? If yes, return the cached response. If no, proceed. Second, Stripe also supports idempotency keys — even if our retry logic has a bug, Stripe won't charge twice for the same key within 24 hours. Third, our database uses a UNIQUE constraint on idempotency_key — a duplicate INSERT fails at the database level. All three layers must fail for a double charge to happen."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong | Better |
|---------|---------------|--------|
| Using FLOAT for money | `0.1 + 0.2 = 0.30000000000000004` | Integer cents (BIGINT) |
| No idempotency | Double charges on retry | Idempotency key on every payment |
| Storing card numbers | PCI DSS Level 1 = 315 requirements | Stripe tokenization (SAQ-A = 22 req) |
| Single-entry bookkeeping | Can't detect missing money | Double-entry (debit + credit = 0) |
| Deleting failed payments | Lose audit trail | Append-only ledger, status transitions |
| Fulfilling from client callback | Client can be spoofed | Fulfill from webhook (server-to-server) |
| No reconciliation | Discrepancies go undetected for months | Daily automated reconciliation |
| Using DynamoDB for ledger | No ACID for multi-row transactions | PostgreSQL/Aurora for financial data |
| Not handling timeouts | Unknown state → potential double charge | Query status before retrying |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│         PAYMENT & FINANCIAL SYSTEMS CHEAT SHEET               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  MONEY: Integer cents (BIGINT), NEVER float                  │
│  IDEMPOTENCY: Every request has unique key (client-generated)│
│  DOUBLE-ENTRY: Every txn = debit + credit, sum = 0          │
│  LEDGER: Append-only, immutable, PostgreSQL (ACID)           │
│                                                              │
│  PAYMENT FLOW:                                               │
│    PaymentIntent (Stripe) → Client confirms → Webhook → Fulfill│
│    Never fulfill from client callback!                        │
│    Card numbers → Stripe tokenization (never your server)    │
│                                                              │
│  STATE MACHINE:                                              │
│    CREATED → PROCESSING → SUCCEEDED → REFUNDING → REFUNDED  │
│                        → FAILED → RETRYING → PROCESSING      │
│    Atomic transitions (WHERE status = :expected)              │
│                                                              │
│  ERROR HANDLING:                                             │
│    Transient (timeout, 500) → Retry with idempotency key    │
│    Soft decline (insufficient funds) → Retry later           │
│    Hard decline (stolen card) → Don't retry, block           │
│    Unknown (timeout after submission) → CHECK STATUS first!   │
│                                                              │
│  PCI: Stripe tokenization → SAQ-A (22 requirements)         │
│  FRAUD: Stripe Radar + custom rules + velocity checks        │
│  RECONCILIATION: Daily comparison (your ledger vs Stripe)    │
│                                                              │
│  AWS ARCHITECTURE:                                           │
│    DynamoDB: Orders, idempotency keys (scale + speed)        │
│    Aurora PostgreSQL: Ledger entries (ACID + SQL + reporting) │
│    Secrets Manager: Stripe API keys (auto-rotation)          │
│    SQS: Async fulfillment (webhook → queue → process)        │
│    KMS: Encrypt all financial data at rest                   │
│                                                              │
│  MARKETPLACE (Uber/Airbnb):                                  │
│    Stripe Connect: Split payments (platform fee + provider)  │
│                                                              │
│  SUBSCRIPTIONS:                                              │
│    Trial → Active → Past Due (retry 3x) → Cancelled         │
│    Proration on plan changes                                 │
│    Dunning emails for failed payments                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **[Security & Encryption](./27-security-and-encryption.md)** — PCI compliance, encryption at rest/transit
- **[AWS Security Real-World](./27b-aws-security-and-encryption-real-world.md)** — KMS, Secrets Manager, IAM
- **[Database Schema Design](./02b-database-schema-design-real-world.md)** — Payment system schemas
- **[PostgreSQL Real-World](./50b-postgresql-real-world-applications.md)** — Stripe ledger design
- **[DynamoDB Real-World](./49c-dynamodb-real-world-applications.md)** — Order tracking patterns
- **[SQS/SNS/Kinesis](./10b-sqs-sns-kinesis-real-world-applications.md)** — Async payment processing

---

*This document is part of the System Design Interview Deep Dive series.*
