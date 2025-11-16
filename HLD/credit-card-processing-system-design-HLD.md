# Credit Card Processing System Design - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [Payment Processing Flow](#payment-processing-flow)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a scalable, secure, PCI-DSS compliant credit card processing system that enables:
- Payment authorization and capture
- Refunds and chargebacks
- Multiple payment methods (credit cards, debit cards, digital wallets)
- Fraud detection and prevention
- 3D Secure authentication
- Recurring billing and subscriptions
- Multi-currency support
- Settlement and reconciliation
- Compliance with PCI-DSS Level 1
- Integration with multiple payment gateways
- Real-time payment status tracking
- Dispute management

### Scale Requirements
- **50 million transactions per day**
- **Peak load: 10,000 transactions per second**
- **Average transaction value: $75**
- **Daily transaction volume: $3.75 billion**
- **99.99% availability** (< 1 hour downtime/year)
- **< 500ms authorization latency (p95)**
- **Zero data loss** (critical for financial data)
- **Support 100+ countries and 50+ currencies**
- **Handle Black Friday: 100K TPS peak**

---

## Functional Requirements

### Must Have (P0)

#### 1. **Payment Processing**
- Authorize payment (hold funds, don't charge)
- Capture payment (charge the held funds)
- Authorize + Capture in one step (for instant purchases)
- Void authorization (release hold before capture)
- Refund payment (full or partial)
- Validate card details (number, CVV, expiry)
- Support multiple card brands (Visa, Mastercard, Amex, Discover)

#### 2. **Payment Methods**
- Credit cards (Visa, Mastercard, Amex, Discover, JCB, Diners)
- Debit cards
- Digital wallets (Apple Pay, Google Pay, PayPal)
- Bank transfers (ACH in US, SEPA in EU)
- Buy Now Pay Later (Affirm, Klarna, Afterpay)

#### 3. **Security & Compliance**
- PCI-DSS Level 1 compliance
- Tokenization (never store raw card numbers)
- End-to-end encryption (TLS 1.2+)
- 3D Secure (SCA - Strong Customer Authentication)
- Fraud detection (real-time risk scoring)
- Card verification (CVV, AVS)
- Secure card storage for recurring payments

#### 4. **Transaction Management**
- Create payment
- Retrieve payment status
- List transactions (with filters)
- Search transactions
- Export transaction history
- Transaction reconciliation
- Dispute management

#### 5. **Fraud Detection**
- Real-time risk scoring (0-100 scale)
- Velocity checks (multiple payments from same card/IP)
- Geolocation verification
- Device fingerprinting
- Machine learning fraud models
- Manual review queue for suspicious transactions
- Blocklist/allowlist management

#### 6. **Recurring Billing**
- Create subscription (monthly, annual, custom)
- Charge subscription automatically
- Update payment method
- Cancel subscription
- Handle failed payments (retry logic)
- Dunning management (recover failed payments)
- Proration for plan changes

#### 7. **Multi-Currency & International**
- Support 50+ currencies
- Dynamic currency conversion
- FX rate management
- Local payment methods (Alipay, WeChat Pay, iDEAL)
- Compliance with local regulations
- Tax calculation (VAT, GST)

#### 8. **Reporting & Analytics**
- Transaction success rate
- Decline reasons
- Fraud detection accuracy
- Settlement reports
- Revenue analytics
- Gateway performance metrics
- Chargeback analytics

### Nice to Have (P1)
- Instant bank transfers
- Cryptocurrency payments
- Installment plans
- Dynamic 3D Secure (only when needed)
- Smart retry logic (retry at optimal time)
- Payment link generation (email/SMS payment)
- Virtual cards
- Card account updater (get new card details automatically)
- Split payments (multiple payment methods)
- Tips and gratuity handling

---

## Non-Functional Requirements

### Performance
- **Authorization latency**: < 500ms (p95), < 200ms (p50)
- **Capture latency**: < 1 second (p95)
- **Refund processing**: < 5 seconds
- **API response time**: < 100ms (p95)
- **Fraud check latency**: < 100ms
- **Database query time**: < 50ms

### Scalability
- Support 50M transactions/day
- Peak capacity: 10K TPS (Black Friday)
- Scale to 500M transactions/day in 3 years
- Support 10M merchants
- Handle 100M stored payment methods

### Availability
- **99.99% uptime** (< 53 minutes downtime/year)
- Multi-region active-active deployment
- No single point of failure
- Graceful degradation (if fraud check slow, proceed with payment)
- Disaster recovery: RPO < 1 minute, RTO < 15 minutes

### Reliability
- **Zero transaction loss** (durability guarantee)
- **Idempotent operations** (duplicate requests handled)
- **Exactly-once payment** (no double charging)
- **Atomic operations** (authorization + capture = all or nothing)
- **Data consistency** (strong consistency for payments)

### Security
- **PCI-DSS Level 1 compliance** (highest security standard)
- **Encryption**: TLS 1.3 for transit, AES-256 for at-rest
- **Tokenization**: Replace card numbers with tokens
- **Key management**: HSM (Hardware Security Module)
- **Access control**: MFA, role-based access (RBAC)
- **Audit trail**: Log every action (immutable logs)
- **Data isolation**: Multi-tenant with strict separation
- **Penetration testing**: Quarterly security audits

### Consistency
- **Strong consistency** for payment operations
- **Eventual consistency** for analytics and reporting
- **Idempotent** APIs (same request = same result)
- **Two-phase commit** for distributed transactions

### Compliance
- **PCI-DSS**: Never log or store full card numbers
- **GDPR**: Right to be forgotten, data export
- **SOX**: Financial reporting compliance
- **Regional**: NACHA (US), SEPA (EU), local regulations

---

## Capacity Estimation

### Traffic Estimates
```
Daily Transactions: 50M
Transactions/second (average): 50M / 86,400 ≈ 579 TPS
Transactions/second (peak - Black Friday 10x): 5,790 TPS
Absolute peak capacity needed: 10,000 TPS

Transaction types distribution:
- Authorization only: 20% → 10M/day → 116 TPS
- Authorize + Capture: 60% → 30M/day → 347 TPS
- Refunds: 15% → 7.5M/day → 87 TPS
- Voids: 5% → 2.5M/day → 29 TPS

Payment methods:
- Credit cards: 70% → 35M/day
- Debit cards: 15% → 7.5M/day
- Digital wallets: 10% → 5M/day
- Other: 5% → 2.5M/day

Success vs Decline:
- Approved: 85% → 42.5M/day
- Declined: 15% → 7.5M/day
  - Insufficient funds: 40% → 3M
  - Fraud suspected: 30% → 2.25M
  - Invalid card: 20% → 1.5M
  - Other: 10% → 0.75M
```

### Storage Estimates

**Transaction Records**:
```
Per transaction record:
{
  transaction_id: 16 bytes (UUID)
  merchant_id: 16 bytes (UUID)
  customer_id: 16 bytes (UUID)
  card_token: 32 bytes
  amount: 8 bytes (decimal)
  currency: 3 bytes
  status: 20 bytes
  gateway_ref: 32 bytes
  created_at: 8 bytes
  metadata: 500 bytes (JSON)
  risk_score: 4 bytes
  fraud_flags: 100 bytes
}
Total per transaction: ~800 bytes

Daily storage: 50M × 800 bytes = 40 GB/day
Monthly: 40 GB × 30 = 1.2 TB/month
Yearly: 14.4 TB/year
With 7-year retention (compliance): 100 TB

With replication (3x): 300 TB
With backups: 450 TB total
```

**Card Tokens**:
```
Stored payment methods: 100M users × 2 cards = 200M tokens
Per token record:
{
  token_id: 16 bytes
  user_id: 16 bytes
  card_fingerprint: 32 bytes (hash)
  last_four: 4 bytes
  brand: 10 bytes (visa, mastercard)
  exp_month: 2 bytes
  exp_year: 2 bytes
  billing_zip: 10 bytes
  is_default: 1 byte
  created_at: 8 bytes
}
Total per token: ~100 bytes

Total: 200M × 100 bytes = 20 GB
With replication: 60 GB
```

**Fraud Data**:
```
Rules: 1000 fraud rules × 5 KB = 5 MB
Blocklists: 1M entries × 100 bytes = 100 MB
Historical fraud patterns: 100 GB
ML model data: 50 GB

Total: ~150 GB (negligible compared to transactions)
```

**Audit Logs** (Immutable):
```
Every API call logged:
- 50M transactions × 5 API calls each = 250M logs/day
- Per log: 500 bytes
- Daily: 250M × 500 bytes = 125 GB/day
- With 7-year retention: 320 TB
- Compressed (10:1 ratio): 32 TB
```

**Total Storage**:
```
Transactions (7 years): 450 TB
Card tokens: 60 GB = 0.06 TB
Fraud data: 0.15 TB
Audit logs (7 years, compressed): 32 TB
─────────────────────────────────
Total: ~482 TB
```

### Bandwidth Estimates
```
Transaction payload size:
- Request: 2 KB (card details, amount, metadata)
- Response: 1 KB (transaction ID, status, gateway response)
- Total per transaction: 3 KB

Daily bandwidth:
Incoming: 50M × 2 KB = 100 GB/day
Outgoing: 50M × 1 KB = 50 GB/day
Total: 150 GB/day

Per second (average): 150 GB / 86,400 ≈ 1.7 MB/second
Peak (10x): 17 MB/second
```

### Memory Requirements (Caching)
```
Active merchant sessions: 1M merchants online
Session data per merchant: 5 KB
Total: 1M × 5 KB = 5 GB

Card token cache (hot cards): 50M tokens × 100 bytes = 5 GB

Fraud rules cache: 1000 rules × 5 KB = 5 MB

Rate limiter counters: 10M active users × 64 bytes = 640 MB

3D Secure sessions: 1M active × 2 KB = 2 GB

Total memory needed: ~13 GB
Distributed across Redis cluster: 10 nodes × 1.3 GB per node
```

### Server Estimates
```
API Servers (payment processing):
  - Handle requests: 579 TPS average, 10K peak
  - Each server: 500 TPS capacity
  - Servers needed: 10K / 500 = 20 servers
  - With redundancy (3x): 60 servers

Payment Gateway Connectors:
  - One connector per gateway (Stripe, Braintree, Adyen, etc.)
  - 10 gateways × 5 instances each = 50 servers

Fraud Detection Service:
  - ML model inference: 579 TPS
  - Each server: 200 TPS
  - Servers needed: 579 / 200 = 3 servers
  - With redundancy (3x): 10 servers

Webhook Processors:
  - Handle gateway callbacks: 1000 TPS
  - Each server: 500 TPS
  - Servers needed: 5 servers

Background Workers:
  - Settlement reconciliation: 10 servers
  - Retry failed payments: 10 servers
  - Subscription billing: 10 servers
  - Report generation: 5 servers

Total servers: 60 + 50 + 10 + 5 + 35 = 160 servers

Cost estimation (AWS):
- API servers: 60 × $150/month = $9K/month
- Gateway connectors: 50 × $100/month = $5K/month
- Fraud detection: 10 × $200/month = $2K/month
- Database (PostgreSQL + Redis): $25K/month
- Message Queue (Kafka): $8K/month
- HSM (Hardware Security Module): $10K/month
- Compliance & auditing: $5K/month
- Gateway fees: 50M × $0.10 = $5M/month
- Total infrastructure: ~$49K/month
- Total with gateway fees: ~$5.05M/month
- Cost per transaction: $0.10
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                        CLIENTS                                 │
│  Web Apps | Mobile Apps | Backend Services | Admin Dashboard  │
└─────────────────────────┬──────────────────────────────────────┘
                          │ (HTTPS/TLS 1.3)
                          ↓
┌────────────────────────────────────────────────────────────────┐
│                    WAF + DDoS Protection                       │
│  AWS WAF | Cloudflare - Block malicious traffic               │
└─────────────────────────┬──────────────────────────────────────┘
                          │
                          ↓
┌────────────────────────────────────────────────────────────────┐
│                     LOAD BALANCER                              │
│  AWS ALB - SSL termination, Health checks, Sticky sessions    │
└─────────────────────────┬──────────────────────────────────────┘
                          │
                          ↓
┌────────────────────────────────────────────────────────────────┐
│                    API GATEWAY                                 │
│  - Authentication (API keys, OAuth, JWT)                       │
│  - Rate limiting (1000 req/min per merchant)                   │
│  - Request validation (schema validation)                      │
│  - Audit logging (every request logged)                        │
│  - PCI-DSS compliant (no card data in logs)                    │
└─────────────────────────┬──────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ↓               ↓                ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Payment     │  │   Tokenization│  │   Fraud      │
│  Service     │  │   Service     │  │   Detection  │
│ (Stateless)  │  │ (PCI Scope)   │  │   Service    │
└──────┬───────┘  └──────┬────────┘  └──────┬───────┘
       │                 │                   │
       └─────────────────┼───────────────────┘
                         ↓
┌────────────────────────────────────────────────────────────────┐
│              MESSAGE QUEUE (Kafka Cluster)                     │
│  Topics:                                                       │
│  - payments.authorization (partition by merchant_id)           │
│  - payments.capture (partition by transaction_id)              │
│  - payments.refund (partition by transaction_id)               │
│  - payments.webhook (partition by gateway)                     │
│  - payments.fraud_review (partition by risk_level)             │
│  - payments.settlement (partition by date)                     │
└─────────────────────────┬──────────────────────────────────────┘
                          │
              ┌───────────┼───────────┐
              ↓           ↓            ↓
┌──────────────────┐ ┌──────────────┐ ┌──────────────────┐
│ Authorization    │ │   Capture    │ │  Refund Worker   │
│ Worker           │ │   Worker     │ │                  │
└─────────┬────────┘ └──────┬───────┘ └────────┬─────────┘
          │                 │                   │
          └─────────────────┼───────────────────┘
                            ↓
                  ┌──────────────────┐
                  │  Gateway Router  │
                  │  (Select best    │
                  │   gateway)       │
                  └─────────┬────────┘
                            │
              ┌─────────────┼─────────────┐
              ↓             ↓              ↓
    ┌─────────────┐  ┌─────────────┐  ┌──────────────┐
    │   Stripe    │  │  Braintree  │  │    Adyen     │
    │   Gateway   │  │   Gateway   │  │   Gateway    │
    │  Connector  │  │  Connector  │  │  Connector   │
    └──────┬──────┘  └──────┬──────┘  └──────┬───────┘
           │                │                 │
           └────────────────┼─────────────────┘
                            ↓
            ┌──────────────────────────────────┐
            │    PAYMENT GATEWAYS              │
            │  Stripe | Braintree | Adyen     │
            │  PayPal | Authorize.net          │
            └──────────────────────────────────┘
                            │
                            ↓ (Webhooks - payment status)
                    ┌───────────────┐
                    │ Webhook       │
                    │ Handler       │
                    │ (Status sync) │
                    └───────┬───────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────────┐
│           TOKENIZATION VAULT (PCI-Compliant)                   │
│  - Card data encrypted with HSM keys                           │
│  - Network segmentation (isolated VPC)                         │
│  - No direct internet access                                   │
│  - Access via secure API only                                  │
└────────────────────────┬───────────────────────────────────────┘
                         │
┌────────────────────────────────────────────────────────────────┐
│                CACHE LAYER (Redis Cluster)                     │
│  - Merchant config (hash, 1 hour TTL)                          │
│  - Card tokens (string, 24 hour TTL)                           │
│  - Fraud rules (hash, 1 hour TTL)                              │
│  - Rate limit counters (sorted set)                            │
│  - 3D Secure sessions (hash, 15 min TTL)                       │
│  - FX rates (string, 1 hour TTL)                               │
└────────────────────────┬───────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓               ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Transaction  │ │  Merchant    │ │   Fraud      │
│     DB       │ │     DB       │ │     DB       │
│(PostgreSQL)  │ │(PostgreSQL)  │ │(PostgreSQL)  │
│              │ │              │ │              │
│- Payments    │ │- Merchants   │ │- Fraud rules │
│- Refunds     │ │- API keys    │ │- Blocklists  │
│- Settlements │ │- Configs     │ │- ML features │
│- Disputes    │ │- Webhooks    │ │- Risk scores │
└──────────────┘ └──────────────┘ └──────────────┘

┌────────────────────────────────────────────────────────────────┐
│              ANALYTICS DB (ClickHouse)                         │
│  - Transaction events (time-series)                            │
│  - Metrics aggregations                                        │
│  - Success/decline rates                                       │
│  - Gateway performance                                         │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│           OBJECT STORAGE (S3 Encrypted)                        │
│  - Receipt PDFs                                                │
│  - Settlement reports                                          │
│  - Compliance exports                                          │
│  - Audit logs (immutable, encrypted)                           │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                 FRAUD DETECTION ML                             │
│  SageMaker - Real-time fraud scoring                           │
│  - Feature extraction from transaction                         │
│  - Model inference (< 50ms)                                    │
│  - Risk score: 0-100                                           │
│  - Updated model: Weekly                                       │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│              MONITORING & OBSERVABILITY                        │
│  Prometheus | Grafana | ELK | Jaeger | PagerDuty             │
│  - Transaction success rate                                    │
│  - Authorization latency                                       │
│  - Gateway performance                                         │
│  - Fraud detection accuracy                                    │
│  - Alert on anomalies                                          │
└────────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **PostgreSQL for Transactions**
   - ACID compliance critical for money
   - Strong consistency
   - Complex queries (refunds, settlements)
   - Battle-tested for financial data
   - Read replicas for analytics

2. **Kafka for Event Streaming**
   - Durability (transactions never lost)
   - Exactly-once semantics
   - Ordered processing per partition
   - Replay capability for auditing
   - Consumer groups for scaling

3. **Redis for Caching & Rate Limiting**
   - Sub-millisecond latency for tokens
   - Sliding window rate limiting
   - Session management (3D Secure)
   - Atomic operations for counters

4. **Separate Tokenization Service**
   - PCI-DSS scope reduction
   - Network isolation (separate VPC)
   - HSM integration
   - Only service that touches card data
   - Audit all access

5. **Multi-Gateway Support**
   - No vendor lock-in
   - Failover if gateway down
   - Route by cost/success rate
   - A/B test gateways
   - Comply with regional requirements

6. **Real-time Fraud Detection**
   - Check before authorization
   - Block fraudulent transactions
   - ML model for adaptive detection
   - Manual review queue
   - Reduce chargebacks

---

## Core Components

### 1. Payment Service (API Layer)

**Purpose**: Entry point for all payment operations

**Responsibilities**:
- Accept payment requests
- Validate inputs (card number format, amount, currency)
- Authenticate merchant
- Enforce rate limits
- Check fraud score
- Route to appropriate handler
- Return response to client

**API Endpoints**:

**Create Payment Intent** (Two-step: Auth then Capture):
```http
POST /api/v1/payments/intents

Request:
{
  "merchant_id": "merch_xyz",
  "amount": 4999,  // $49.99 in cents
  "currency": "USD",
  "payment_method": {
    "type": "card",
    "card": {
      "number": "4242424242424242",  // Test card
      "exp_month": 12,
      "exp_year": 2025,
      "cvc": "123"
    },
    "billing": {
      "name": "Alice Johnson",
      "address": "123 Main St",
      "city": "New York",
      "state": "NY",
      "zip": "10001",
      "country": "US"
    }
  },
  "customer_id": "cust_alice_123",
  "description": "Order #ORD-12345",
  "metadata": {
    "order_id": "ORD-12345",
    "product_ids": ["prod_1", "prod_2"]
  },
  "capture_method": "manual",  // authorize only
  "idempotency_key": "idem_unique_uuid_here"
}

Response:
{
  "payment_intent_id": "pi_1234567890",
  "status": "requires_capture",  // authorized but not captured
  "amount": 4999,
  "amount_authorized": 4999,
  "amount_capturable": 4999,
  "currency": "USD",
  "payment_method_token": "tok_visa_4242",
  "created_at": "2024-01-08T10:30:00Z",
  "authorization_code": "AUTH_ABC123",
  "risk_score": 15,  // Low risk
  "risk_level": "normal",
  "gateway": "stripe",
  "gateway_transaction_id": "ch_stripe_xyz"
}
```

**Capture Payment** (Charge the authorized amount):
```http
POST /api/v1/payments/pi_1234567890/capture

Request:
{
  "amount": 4999,  // Optional: can capture partial amount
  "statement_descriptor": "MYSTORE.COM ORDER-12345"
}

Response:
{
  "payment_intent_id": "pi_1234567890",
  "status": "succeeded",
  "amount_captured": 4999,
  "captured_at": "2024-01-08T11:00:00Z",
  "receipt_url": "https://receipts.example.com/pi_1234567890"
}
```

**Create Direct Payment** (Auth + Capture in one step):
```http
POST /api/v1/payments

Request:
{
  "merchant_id": "merch_xyz",
  "amount": 4999,
  "currency": "USD",
  "payment_method_token": "tok_visa_4242",  // Saved card token
  "customer_id": "cust_alice_123",
  "description": "Subscription renewal",
  "capture_method": "automatic"  // Capture immediately
}

Response:
{
  "payment_id": "pay_abc123",
  "status": "succeeded",
  "amount": 4999,
  "captured_at": "2024-01-08T10:30:01Z"
}
```

**Refund Payment**:
```http
POST /api/v1/refunds

Request:
{
  "payment_id": "pay_abc123",
  "amount": 1000,  // Partial refund: $10.00
  "reason": "customer_request",
  "metadata": {
    "return_id": "RET-98765"
  }
}

Response:
{
  "refund_id": "ref_xyz789",
  "status": "succeeded",
  "amount": 1000,
  "payment_id": "pay_abc123",
  "created_at": "2024-01-08T12:00:00Z"
}
```

**Request Validation**:
```
1. API key authentication (verify merchant_id)
2. Rate limit check (Redis): 1000 requests/minute per merchant
3. Amount validation: 
   - Min: $0.50 (50 cents)
   - Max: $999,999.99
4. Currency code validation (ISO 4217)
5. Card number validation (Luhn algorithm)
6. Expiry date validation (not expired)
7. CVV validation (3-4 digits)
8. Required fields present
9. Idempotency key unique

Validation time: < 5ms
Reject invalid requests immediately (don't process)
```

**Idempotency Handling**:
```
Purpose: Prevent duplicate charges from network retries

Implementation:
1. Client sends idempotency_key (UUID)
2. Server checks Redis:
   GET idempotency:idem_unique_uuid_here
3. If exists (< 24 hours):
   → Return cached response (duplicate prevented)
4. If not exists:
   → Process payment
   → Cache response:
     SETEX idempotency:idem_unique_uuid_here 86400 {response_json}
   → Return response

Critical for payments: Same request must NEVER charge twice
```

### 2. Tokenization Service (PCI-Compliant)

**Purpose**: Secure storage and retrieval of sensitive card data

**Architecture**:
```
Isolated Network (DMZ):
- Separate VPC with strict firewall rules
- No direct internet access
- Access only via internal API
- All traffic encrypted (mTLS)
- Hardware Security Module (HSM) for encryption keys

Data Flow:
Raw Card Number → Tokenization Service → Encrypted Storage
                         ↓
                    Token (safe to store)
                         ↓
                  Return to Payment Service
```

**Tokenization Flow**:
```
Input: Raw card number "4242424242424242"

1. Validate card number (Luhn check)
2. Generate card fingerprint (irreversible hash):
   fingerprint = SHA256(card_number + salt)
   → "a1b2c3d4e5f6..."

3. Check if card already tokenized:
   Query DB: SELECT token FROM card_vault 
             WHERE fingerprint = 'a1b2c3d4e5f6...'
             AND merchant_id = 'merch_xyz'
   
4. If exists: Return existing token
   If not exists: Create new token

5. Encrypt card number with HSM:
   encrypted = HSM.encrypt(card_number, key_id)
   
6. Generate token (safe identifier):
   token = "tok_" + random_base62(16)
   → "tok_v1_1AbC2DeF3GhI"

7. Store in vault database:
   INSERT INTO card_vault (token, fingerprint, encrypted_pan, 
                           last_four, brand, exp_month, exp_year)
   VALUES ('tok_v1_...', 'a1b2c3...', encrypted_pan, '4242', 
           'visa', 12, 2025)

8. Return token to Payment Service

Output: "tok_v1_1AbC2DeF3GhI"

Security guarantees:
✅ Original card number never leaves tokenization service
✅ Token cannot be reverse-engineered
✅ Encrypted with HSM (FIPS 140-2 Level 3)
✅ Access logged and audited
✅ PCI-DSS compliant
```

**Detokenization** (When needed for actual charge):
```
Input: Token "tok_v1_1AbC2DeF3GhI"

1. Authenticate request (only authorized services)
2. Query vault database:
   SELECT encrypted_pan, key_id FROM card_vault
   WHERE token = 'tok_v1_1AbC2DeF3GhI'
   
3. Decrypt with HSM:
   card_number = HSM.decrypt(encrypted_pan, key_id)
   → "4242424242424242"

4. Return ONLY to gateway connector (never to client)
5. Card number used immediately for charge
6. Discarded from memory after use
7. Log access for audit

Security:
- Detokenization only by authorized workers
- Card number exists in memory for < 100ms
- Never logged or stored in plaintext
- Audit trail of every detokenization
```

**HSM Key Management**:
```
Hardware Security Module (AWS CloudHSM or Thales):
- Master key stored in HSM (never leaves)
- Data encryption keys (DEKs) encrypted with master key
- Key rotation every 90 days
- Multi-party key ceremony (3 of 5 quorum)
- FIPS 140-2 Level 3 certified

Key hierarchy:
Master Key (HSM) → Data Encryption Keys → Encrypted card data

Benefits:
✅ Keys never in software
✅ Tamper-resistant hardware
✅ Compliance requirement
✅ Cryptographic operations in hardware (faster)
```

### 3. Fraud Detection Service

**Purpose**: Real-time risk assessment before authorization

**Risk Scoring Model**:
```
Features extracted from transaction:
1. Transaction amount
2. Merchant category
3. Card BIN (first 6 digits)
4. Card country
5. Transaction country
6. IP address and geolocation
7. Device fingerprint
8. Time of day
9. Velocity (transactions in last hour/day)
10. Historical patterns (user's typical behavior)

ML Model (Gradient Boosting):
- Trained on historical fraud data
- Input: Feature vector (50+ features)
- Output: Probability of fraud (0.0 - 1.0)
- Convert to risk score: 0-100

Risk Score Interpretation:
0-20: Low risk (auto-approve)
21-70: Medium risk (additional verification)
71-90: High risk (require 3D Secure)
91-100: Very high risk (block or manual review)
```

**Fraud Check Flow**:
```
1. Payment request arrives
2. Extract features:
   - IP → Geolocation (MaxMind API)
   - Device fingerprint (from client SDK)
   - Card BIN → Issuing bank, country
   - Velocity check (Redis):
     ZCOUNT transactions:card:4242 <1h_ago> <now>
   - Historical behavior (query PostgreSQL)

3. Call ML model (SageMaker endpoint):
   POST /fraud-model/predict
   Features: [amount, merchant_cat, card_country, ...]
   Response: {fraud_probability: 0.15}  // 15% = Low risk

4. Calculate final risk score:
   base_score = fraud_probability × 100 = 15
   + velocity_penalty (if 5+ txns in hour: +20)
   + geolocation_mismatch (card_country ≠ ip_country: +30)
   + unusual_amount (> user's typical: +10)
   final_score = min(base_score + penalties, 100)

5. Apply rules:
   IF final_score < 20: approve = true
   IF final_score 21-70: require_verification = true
   IF final_score 71-90: require_3ds = true
   IF final_score > 90: block = true, manual_review = true

6. Check blocklists:
   - Card number in blocklist? → Block
   - IP address in blocklist? → Block
   - Email in blocklist? → Block

7. Return fraud decision:
   {
     "risk_score": 15,
     "risk_level": "low",
     "decision": "approve",
     "checks_passed": [
       "velocity_check",
       "geolocation_check",
       "ml_model_check",
       "blocklist_check"
     ],
     "require_3ds": false
   }

Total time: < 100ms
```

**Velocity Checks** (Redis):
```
Check multiple velocities to detect patterns:

1. Card velocity:
   Key: velocity:card:{fingerprint}
   Type: Sorted Set (timestamp scored)
   
   Check: ZCOUNT velocity:card:xyz <1h_ago> <now>
   Rule: If > 5 transactions/hour → Flag

2. IP velocity:
   Key: velocity:ip:{ip_address}
   Check: > 10 transactions/hour → Flag

3. Email velocity:
   Key: velocity:email:{email}
   Check: > 3 transactions/hour → Flag

4. Amount velocity:
   Key: velocity:card:{fingerprint}:amount
   Sum: Total amount in last 24 hours
   Rule: If > $10,000 → Flag

5. Cross-merchant velocity:
   Key: velocity:card:{fingerprint}:merchants
   Type: Set (merchant_ids)
   Check: SCARD > 10 merchants/day → Flag
   (Card used at many different merchants = suspicious)

Cleanup: ZREMRANGEBYSCORE to remove old entries
```

**Blocklist Management**:
```
Types of blocklists:

1. Card blocklist:
   - Stolen cards reported
   - Cards with high chargeback rate
   - Test cards in production

2. IP blocklist:
   - Known fraud IPs
   - VPN/proxy IPs (for high-risk transactions)
   - Bot IPs

3. Email blocklist:
   - Confirmed fraudsters
   - Disposable email domains

4. BIN blocklist:
   - High-risk card issuing banks
   - Prepaid cards (if merchant doesn't accept)

Storage: Redis Set
Check: SISMEMBER blocklist:cards {card_fingerprint}
Update: Add/remove via admin API
Sync: Across regions every hour
```

**Manual Review Queue**:
```
High-risk transactions (score > 90):

1. Transaction blocked immediately
2. Added to review queue (PostgreSQL):
   Table: fraud_review_queue
   - transaction_id
   - risk_score
   - fraud_signals
   - status: pending/approved/declined
   
3. Alert fraud analyst
4. Analyst reviews:
   - Transaction details
   - Customer history
   - Device information
   - Similar fraud patterns
   
5. Decision:
   - Approve → Process payment (with note)
   - Decline → Refund and notify merchant
   - Add to blocklist if confirmed fraud

SLA: Review within 2 hours
Auto-decline if not reviewed in 24 hours
```

### 4. Gateway Router

**Purpose**: Select optimal payment gateway for each transaction

**Gateway Selection Algorithm**:
```
Factors:
1. Gateway availability (circuit breaker status)
2. Gateway success rate (last 1000 transactions)
3. Gateway latency (p95)
4. Gateway cost (fees per transaction)
5. Card brand support (Amex only on some gateways)
6. Currency support
7. Country restrictions
8. Merchant preference

Scoring:
gateway_score = (success_rate × 0.4) + 
                ((1 - normalized_latency) × 0.3) +
                ((1 - normalized_cost) × 0.2) +
                (availability × 0.1)

Select gateway with highest score
```

**Example**:
```
Transaction: $50 USD, Visa card, US merchant

Available gateways:
1. Stripe:
   - Success rate: 95%
   - Latency: 200ms (p95)
   - Cost: 2.9% + $0.30 = $1.75
   - Available: Yes
   - Score: (0.95 × 0.4) + (0.8 × 0.3) + (0.7 × 0.2) + (1.0 × 0.1)
          = 0.38 + 0.24 + 0.14 + 0.1 = 0.86

2. Braintree:
   - Success rate: 93%
   - Latency: 300ms
   - Cost: 2.59% + $0.49 = $1.79
   - Available: Yes
   - Score: 0.84

3. Adyen:
   - Success rate: 96%
   - Latency: 250ms
   - Cost: 2.5% + $0.10 = $1.35
   - Available: Circuit breaker OPEN (down)
   - Score: 0 (unavailable)

Selected: Stripe (highest score)
```

**Circuit Breaker** (Per Gateway):
```
States:
- CLOSED: Normal operation
- OPEN: Gateway failing, don't route traffic
- HALF_OPEN: Testing recovery

Implementation:
1. Track last 100 requests per gateway
2. If failure rate > 50%:
   → Open circuit breaker
   → Stop routing to this gateway
   → Route to next best gateway

3. After 5 minutes:
   → Try one request (half-open)
   → If success: Close circuit
   → If failure: Keep open, wait 10 minutes

4. Gradual recovery:
   → Send 10% traffic to recovered gateway
   → If stable: Increase to 50%, then 100%

Benefits:
- Fast failure detection
- Automatic failover
- Prevents cascade failures
- Graceful recovery
```

### 5. Authorization Worker

**Purpose**: Process authorization requests, communicate with gateways

**Authorization Flow**:
```
1. Consume message from Kafka (payments.authorization topic)

2. Parse payment request:
   {
     "payment_intent_id": "pi_123",
     "merchant_id": "merch_xyz",
     "amount": 4999,
     "currency": "USD",
     "card_token": "tok_visa_4242",
     "customer_id": "cust_alice",
     "risk_score": 15
   }

3. Check risk score:
   - If > 90: Reject immediately
   - If 71-90: Trigger 3D Secure flow
   - If < 70: Proceed with authorization

4. Detokenize card (call Tokenization Service):
   Request: {token: "tok_visa_4242"}
   Response: {pan: "4242424242424242", cvv: "123"}

5. Select gateway (Gateway Router):
   → Stripe selected

6. Build gateway request:
   {
     "amount": 4999,
     "currency": "usd",
     "source": {
       "object": "card",
       "number": "4242424242424242",
       "exp_month": 12,
       "exp_year": 2025,
       "cvc": "123"
     },
     "capture": false,  // Auth only
     "metadata": {
       "merchant_txn_id": "pi_123"
     }
   }

7. Send to Stripe API:
   POST https://api.stripe.com/v1/charges
   Headers:
     - Authorization: Bearer sk_live_xyz
     - Idempotency-Key: pi_123
   
8. Handle Stripe response:
   Success (200):
   {
     "id": "ch_stripe_abc",
     "status": "authorized",
     "amount": 4999,
     "authorization_code": "AUTH_123456"
   }
   
   Failure (402):
   {
     "error": {
       "code": "card_declined",
       "decline_code": "insufficient_funds",
       "message": "Your card has insufficient funds."
     }
   }

9. Update database (PostgreSQL):
   UPDATE payments
   SET status = 'authorized',
       gateway_transaction_id = 'ch_stripe_abc',
       authorization_code = 'AUTH_123456',
       authorized_at = NOW()
   WHERE payment_intent_id = 'pi_123';

10. Publish status event (Kafka):
    Topic: payments.status_update
    Event: {payment_intent_id, status: 'authorized'}

11. Update cache (Redis):
    SET payment:pi_123:status 'authorized' EX 3600

12. Return success

Total latency:
- Detokenize: 10ms
- Gateway call: 150ms (Stripe p50)
- Database update: 20ms
- Cache update: 5ms
- Total: ~185ms ✓ (< 500ms SLA)
```

**Retry Logic**:
```
Gateway timeout or network error:

1. Retry with exponential backoff:
   - Attempt 1: Immediate
   - Attempt 2: 1 second later
   - Attempt 3: 3 seconds later
   
2. If all attempts fail:
   - Try next gateway (if available)
   - Update status: 'gateway_error'
   - Alert engineering team
   - Return error to merchant

3. Timeout handling:
   - Gateway timeout: 30 seconds
   - If uncertain state:
     → Query gateway for transaction status
     → Reconcile with our database
     → Never double-charge
```

### 6. Capture Worker

**Purpose**: Capture previously authorized payments

**Capture Flow**:
```
1. Consume from Kafka (payments.capture topic)

2. Load payment from database:
   SELECT * FROM payments WHERE payment_intent_id = 'pi_123';
   
3. Validate state:
   - Status must be 'authorized'
   - Authorization not expired (typically 7 days)
   - Amount to capture <= authorized amount
   - Not already captured

4. Call gateway capture endpoint:
   POST https://api.stripe.com/v1/charges/ch_stripe_abc/capture
   {
     "amount": 4999,
     "statement_descriptor": "MYSTORE.COM ORD-12345"
   }

5. Handle response:
   Success:
   {
     "id": "ch_stripe_abc",
     "status": "succeeded",
     "amount_captured": 4999,
     "balance_transaction": "txn_stripe_balance_xyz"
   }

6. Update database:
   UPDATE payments
   SET status = 'succeeded',
       amount_captured = 4999,
       captured_at = NOW(),
       balance_transaction_id = 'txn_stripe_balance_xyz'
   WHERE payment_intent_id = 'pi_123';

7. Trigger settlement process:
   - Publish to payments.settlement topic
   - Will be included in next settlement batch

8. Send receipt:
   - Generate PDF (Lambda + wkhtmltopdf)
   - Upload to S3
   - Email to customer (via SES)

9. Publish status event

Total latency: ~300ms
```

**Partial Capture**:
```
Scenario: Authorized $100, but only capture $80 (item out of stock)

1. Capture $80:
   POST /capture {amount: 8000}
   
2. Gateway captures $80
3. Remaining $20 authorization:
   - Option A: Void remaining (release hold)
   - Option B: Keep authorized (customer may order more)
   
4. Merchant decides via API:
   POST /payments/pi_123/void {amount: 2000}
```

### 7. Refund Worker

**Purpose**: Process refund requests

**Refund Flow**:
```
1. Consume from Kafka (payments.refund topic)

2. Validate refund:
   - Payment must be in 'succeeded' state
   - Refund amount <= captured amount
   - Not already fully refunded
   - Within refund window (typically 180 days)

3. Calculate refund amount:
   - Full refund: amount = payment.amount_captured
   - Partial refund: amount specified in request
   - Check: previous_refunds + new_refund <= captured amount

4. Call gateway refund endpoint:
   POST https://api.stripe.com/v1/refunds
   {
     "charge": "ch_stripe_abc",
     "amount": 1000,
     "reason": "requested_by_customer",
     "metadata": {
       "internal_refund_id": "ref_xyz789"
     }
   }

5. Handle response:
   Success:
   {
     "id": "re_stripe_refund_def",
     "status": "succeeded",
     "amount": 1000
   }

6. Update database:
   INSERT INTO refunds (refund_id, payment_id, amount, status, 
                        gateway_refund_id, created_at)
   VALUES ('ref_xyz789', 'pay_abc123', 1000, 'succeeded', 
           're_stripe_refund_def', NOW());
   
   UPDATE payments
   SET amount_refunded = amount_refunded + 1000,
       updated_at = NOW()
   WHERE payment_id = 'pay_abc123';

7. Send notification:
   - Email to customer: "Refund processed"
   - Webhook to merchant: refund.succeeded
   
8. Update analytics

Refund latency: 1-3 seconds
Money returns to customer: 5-10 business days (bank-dependent)
```

**Dispute/Chargeback Handling**:
```
Chargeback = Customer disputes transaction with their bank

Flow:
1. Gateway webhook: chargeback.created
   {
     "charge_id": "ch_stripe_abc",
     "amount": 4999,
     "reason": "fraudulent",
     "evidence_due_by": "2024-02-08T23:59:59Z"
   }

2. Create dispute record:
   INSERT INTO disputes (dispute_id, payment_id, amount, reason, 
                         status, evidence_due_by)
   VALUES ('disp_123', 'pay_abc', 4999, 'fraudulent', 
           'needs_response', '2024-02-08');

3. Notify merchant (urgent):
   - Email + webhook + dashboard alert
   - Merchant has 7-21 days to respond
   
4. Merchant provides evidence:
   POST /disputes/disp_123/evidence
   {
     "proof_of_delivery": "https://s3.../shipment_tracking.pdf",
     "customer_communication": "email_thread.pdf",
     "receipt": "signed_receipt.pdf"
   }

5. Submit evidence to gateway:
   POST /gateway/disputes/disp_123/evidence
   
6. Bank reviews and makes decision:
   - Won: Merchant keeps money, dispute closed
   - Lost: Money refunded to customer, merchant charged fee
   
7. Update status based on webhook:
   - dispute.won → status = 'won'
   - dispute.lost → status = 'lost', deduct amount + fee

Chargeback fee: $15-25 per dispute
High chargeback rate (> 1%) = Account at risk
```

### 8. 3D Secure Service

**Purpose**: Additional authentication for high-risk transactions

**3D Secure Flow** (3DS2):
```
1. Payment Service determines 3DS required (risk_score > 70)

2. Create 3DS session:
   POST /3ds/sessions
   {
     "payment_intent_id": "pi_123",
     "amount": 4999,
     "card_token": "tok_visa_4242",
     "return_url": "https://merchant.com/payment/callback"
   }

3. Response:
   {
     "session_id": "3ds_session_xyz",
     "redirect_url": "https://auth.issuer.com/3ds?session=xyz",
     "status": "requires_action"
   }

4. Return to client:
   Client must redirect user to redirect_url

5. User authentication flow:
   a. Redirect to issuer website (bank)
   b. Bank challenges user:
      - SMS OTP
      - Mobile app push notification
      - Biometric (fingerprint)
   c. User completes authentication
   d. Bank redirects back to return_url

6. Callback to merchant site:
   GET https://merchant.com/payment/callback?session=3ds_session_xyz
   
7. Merchant frontend calls our API:
   POST /3ds/sessions/3ds_session_xyz/confirm
   
8. We query issuer for authentication result:
   GET https://issuer.com/3ds/result?session=xyz
   Response: {authenticated: true, trans_status: "Y"}

9. If authenticated:
   - Proceed with authorization
   - Transaction marked as SCA-compliant
   - Lower liability shift (fraud liability on issuer)

10. If not authenticated:
    - Decline transaction
    - Return error to merchant

3DS adds latency: 2-10 seconds (user interaction)
But reduces fraud: 70% fewer chargebacks
```

**Dynamic 3DS** (Optimize user experience):
```
Not all transactions need 3DS

Skip 3DS if:
- Low risk score (< 30)
- Trusted customer (> 10 successful payments)
- Low amount (< $30)
- Merchant has low fraud rate
- Customer in low-risk country

Require 3DS if:
- High risk score (> 70)
- First transaction with card
- High amount (> $500)
- Suspicious patterns detected
- Regulatory requirement (EU SCA)

Balance: User friction vs fraud prevention
```

### 9. Settlement Service

**Purpose**: Reconcile transactions and transfer funds to merchants

**Settlement Process**:
```
Daily settlement batch (runs at 2 AM UTC):

1. Query all captured transactions from previous day:
   SELECT * FROM payments
   WHERE status = 'succeeded'
     AND captured_at >= CURRENT_DATE - 1
     AND captured_at < CURRENT_DATE
     AND settled = false;

2. Group by merchant:
   Merchant A: 1000 transactions, $75,000 total
   Merchant B: 500 transactions, $37,500 total
   ...

3. Calculate merchant payout:
   For Merchant A:
   Gross: $75,000
   - Platform fee (2.5%): -$1,875
   - Gateway fees: -$500
   - Refunds processed: -$1,000
   - Chargeback fees: -$50
   ────────────────────────────
   Net payout: $71,575

4. Create payout record:
   INSERT INTO payouts (payout_id, merchant_id, amount, 
                        transactions_count, status, created_at)
   VALUES ('payout_123', 'merch_A', 71575, 1000, 
           'pending', NOW());

5. Initiate bank transfer:
   - ACH transfer (US): 2-3 business days
   - Wire transfer (International): 1-2 days
   - Instant transfer (for fee): Same day

6. Mark transactions as settled:
   UPDATE payments
   SET settled = true, payout_id = 'payout_123'
   WHERE payment_id IN (...);

7. Generate settlement report (PDF):
   - Transaction breakdown
   - Fee details
   - Net payout amount
   - Upload to S3
   - Email to merchant

8. Update payout status when bank confirms transfer:
   Webhook from bank → Update status to 'paid'
```

**Reconciliation**:
```
Purpose: Ensure our records match gateway records

Daily reconciliation job:
1. Download transaction report from each gateway
   - Stripe: CSV report via API
   - Adyen: SFTP download
   
2. Compare with our database:
   - Match by gateway_transaction_id
   - Check amounts match
   - Check statuses match
   
3. Flag discrepancies:
   - Transaction in gateway but not in our DB
   - Transaction in our DB but not in gateway
   - Amount mismatch
   - Status mismatch

4. Alert finance team for investigation
5. Generate reconciliation report

Tolerance: 0.01% mismatch allowed
Above threshold: Page on-call engineer
```

### 10. Subscription Service

**Purpose**: Handle recurring billing

**Subscription Model**:
```json
{
  "subscription_id": "sub_12345",
  "customer_id": "cust_alice",
  "plan": {
    "amount": 999,  // $9.99/month
    "currency": "USD",
    "interval": "month",
    "interval_count": 1
  },
  "payment_method_token": "tok_visa_4242",
  "status": "active",
  "current_period_start": "2024-01-01T00:00:00Z",
  "current_period_end": "2024-02-01T00:00:00Z",
  "billing_cycle_anchor": 1,  // Day of month
  "trial_end": null,
  "cancel_at_period_end": false,
  "metadata": {
    "plan_name": "Premium Monthly"
  }
}
```

**Subscription Billing Flow**:
```
Every day at 1 AM UTC:

1. Query subscriptions due for billing:
   SELECT * FROM subscriptions
   WHERE status = 'active'
     AND current_period_end <= CURRENT_DATE
     AND billing_cycle_anchor = DAY(CURRENT_DATE);

2. For each subscription:
   a. Create payment:
      POST /payments
      {
        "amount": 999,
        "payment_method_token": "tok_visa_4242",
        "customer_id": "cust_alice",
        "description": "Subscription renewal"
      }
   
   b. If payment succeeds:
      - Update subscription:
        current_period_start = current_period_end
        current_period_end = current_period_end + 1 month
      - Send receipt to customer
   
   c. If payment fails:
      - Retry logic (see dunning management)
      - Update status: 'past_due'
      - Send payment failure email

3. Process in batches (1000 subscriptions/batch)
4. Parallel processing (50 workers)
5. Complete in 1-2 hours
```

**Dunning Management** (Recover failed payments):
```
Failed subscription payment:

Day 0: Payment fails (insufficient funds)
- Status: 'past_due'
- Email: "Payment failed, please update card"
- Keep subscription active (grace period)

Day 3: Retry #1
- Attempt payment again
- If succeeds: Status → 'active'
- If fails: Continue grace period

Day 7: Retry #2
- Attempt payment
- Email: "Final reminder"
- If fails: Status → 'unpaid'

Day 10: Retry #3
- Final attempt
- If succeeds: Status → 'active'
- If fails: Status → 'canceled'
  - Revoke access
  - Archive subscription

Smart retry timing:
- Retry on payday (1st, 15th of month)
- Avoid weekends
- Consider user's timezone
- ML model predicts best time

Recovery rate with dunning: 40-60%
Without dunning: < 20%
```

---

## Database Design

### 1. Transaction Database (PostgreSQL)

**Schema**:

```sql
-- Payments (main table)
CREATE TABLE payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_intent_id VARCHAR(50) UNIQUE NOT NULL,
    merchant_id UUID NOT NULL,
    customer_id UUID,
    amount DECIMAL(19, 4) NOT NULL,  -- Store in smallest currency unit
    currency CHAR(3) NOT NULL,  -- ISO 4217 (USD, EUR, etc.)
    status VARCHAR(20) NOT NULL,  -- authorized, succeeded, failed, refunded
    payment_method_token VARCHAR(100) NOT NULL,
    payment_method_type VARCHAR(20),  -- card, bank_account, wallet
    card_last_four CHAR(4),
    card_brand VARCHAR(20),  -- visa, mastercard, amex
    card_fingerprint VARCHAR(64),
    
    -- Authorization details
    authorization_code VARCHAR(50),
    authorized_at TIMESTAMP,
    authorization_expires_at TIMESTAMP,
    
    -- Capture details
    amount_authorized DECIMAL(19, 4),
    amount_captured DECIMAL(19, 4) DEFAULT 0,
    amount_refunded DECIMAL(19, 4) DEFAULT 0,
    captured_at TIMESTAMP,
    
    -- Gateway details
    gateway VARCHAR(50) NOT NULL,  -- stripe, braintree, adyen
    gateway_transaction_id VARCHAR(100),
    gateway_response TEXT,  -- JSON
    
    -- Fraud & Risk
    risk_score INTEGER,  -- 0-100
    risk_level VARCHAR(20),  -- low, medium, high, critical
    fraud_checks TEXT,  -- JSON array of checks performed
    require_3ds BOOLEAN DEFAULT false,
    threeds_authenticated BOOLEAN,
    
    -- Metadata
    description TEXT,
    statement_descriptor VARCHAR(22),  -- Shows on card statement
    metadata JSONB,  -- Flexible key-value pairs
    
    -- Settlement
    settled BOOLEAN DEFAULT false,
    payout_id UUID,
    settled_at TIMESTAMP,
    
    -- Audit
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100),  -- API key ID or user ID
    
    -- Soft delete
    deleted_at TIMESTAMP,
    
    CONSTRAINT amount_positive CHECK (amount > 0),
    CONSTRAINT currency_valid CHECK (LENGTH(currency) = 3)
);

CREATE INDEX idx_payments_merchant ON payments(merchant_id, created_at DESC);
CREATE INDEX idx_payments_customer ON payments(customer_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments(status, created_at DESC);
CREATE INDEX idx_payments_gateway ON payments(gateway, gateway_transaction_id);
CREATE INDEX idx_payments_settlement ON payments(settled, captured_at) 
    WHERE settled = false AND status = 'succeeded';

-- Refunds
CREATE TABLE refunds (
    refund_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(payment_id),
    amount DECIMAL(19, 4) NOT NULL,
    reason VARCHAR(50),  -- duplicate, fraudulent, requested_by_customer
    status VARCHAR(20) NOT NULL,  -- pending, succeeded, failed, canceled
    gateway VARCHAR(50),
    gateway_refund_id VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP,
    
    CONSTRAINT refund_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_refunds_payment ON refunds(payment_id);
CREATE INDEX idx_refunds_status ON refunds(status, created_at DESC);

-- Disputes/Chargebacks
CREATE TABLE disputes (
    dispute_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(payment_id),
    amount DECIMAL(19, 4) NOT NULL,
    reason VARCHAR(100),  -- fraudulent, unrecognized, duplicate, etc.
    status VARCHAR(20) NOT NULL,  -- needs_response, under_review, won, lost
    evidence_due_by TIMESTAMP,
    evidence_submitted_at TIMESTAMP,
    resolution VARCHAR(20),  -- won, lost, accepted
    gateway_dispute_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    resolved_at TIMESTAMP,
    
    CONSTRAINT dispute_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_disputes_payment ON disputes(payment_id);
CREATE INDEX idx_disputes_status ON disputes(status, evidence_due_by);

-- Payouts (settlements to merchants)
CREATE TABLE payouts (
    payout_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    transaction_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,  -- pending, paid, failed, canceled
    arrival_date DATE,
    bank_account_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    paid_at TIMESTAMP
);

CREATE INDEX idx_payouts_merchant ON payouts(merchant_id, created_at DESC);
CREATE INDEX idx_payouts_status ON payouts(status, arrival_date);

-- Subscriptions
CREATE TABLE subscriptions (
    subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,  -- active, past_due, canceled, unpaid
    plan_amount DECIMAL(19, 4) NOT NULL,
    plan_currency CHAR(3) NOT NULL,
    plan_interval VARCHAR(20) NOT NULL,  -- day, week, month, year
    plan_interval_count INTEGER DEFAULT 1,
    payment_method_token VARCHAR(100) NOT NULL,
    current_period_start TIMESTAMP NOT NULL,
    current_period_end TIMESTAMP NOT NULL,
    billing_cycle_anchor INTEGER,  -- Day of month (1-31)
    trial_end TIMESTAMP,
    cancel_at_period_end BOOLEAN DEFAULT false,
    canceled_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_customer ON subscriptions(customer_id);
CREATE INDEX idx_subscriptions_billing ON subscriptions(status, current_period_end) 
    WHERE status IN ('active', 'past_due');
```

### 2. Card Vault Database (PostgreSQL - Isolated)

```sql
-- Card vault (PCI-compliant, encrypted)
CREATE TABLE card_vault (
    token VARCHAR(50) PRIMARY KEY,
    merchant_id UUID NOT NULL,
    customer_id UUID,
    card_fingerprint VARCHAR(64) UNIQUE NOT NULL,  -- SHA-256 hash
    encrypted_pan BYTEA NOT NULL,  -- Encrypted card number
    encryption_key_id VARCHAR(50) NOT NULL,  -- HSM key ID
    last_four CHAR(4) NOT NULL,
    card_brand VARCHAR(20) NOT NULL,
    exp_month SMALLINT NOT NULL,
    exp_year SMALLINT NOT NULL,
    billing_zip VARCHAR(20),
    billing_country CHAR(2),
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP,
    
    CONSTRAINT exp_month_valid CHECK (exp_month BETWEEN 1 AND 12),
    CONSTRAINT exp_year_valid CHECK (exp_year >= EXTRACT(YEAR FROM CURRENT_DATE))
);

CREATE INDEX idx_card_vault_customer ON card_vault(customer_id, is_default);
CREATE INDEX idx_card_vault_fingerprint ON card_vault(card_fingerprint, merchant_id);

-- Access audit (every detokenization logged)
CREATE TABLE vault_access_log (
    log_id BIGSERIAL PRIMARY KEY,
    token VARCHAR(50) NOT NULL,
    accessed_by VARCHAR(100) NOT NULL,  -- Service/user that accessed
    access_type VARCHAR(20),  -- tokenize, detokenize, delete
    ip_address INET,
    timestamp TIMESTAMP DEFAULT NOW(),
    request_id VARCHAR(100),
    
    -- Never modify or delete from this table (immutable)
    CONSTRAINT immutable_log CHECK (timestamp IS NOT NULL)
);

CREATE INDEX idx_vault_access ON vault_access_log(token, timestamp DESC);
CREATE INDEX idx_vault_access_time ON vault_access_log(timestamp DESC);
```

### 3. Merchant Database (PostgreSQL)

```sql
-- Merchants
CREATE TABLE merchants (
    merchant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    country CHAR(2) NOT NULL,
    business_type VARCHAR(50),
    mcc VARCHAR(4),  -- Merchant Category Code
    status VARCHAR(20) DEFAULT 'active',  -- active, suspended, closed
    risk_level VARCHAR(20) DEFAULT 'low',  -- low, medium, high
    chargeback_rate DECIMAL(5, 4),  -- Percentage (0.0150 = 1.5%)
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- API Keys
CREATE TABLE api_keys (
    key_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(merchant_id),
    key_hash VARCHAR(64) NOT NULL,  -- SHA-256 of actual key
    key_prefix VARCHAR(20) NOT NULL,  -- First 8 chars (for merchant to identify)
    name VARCHAR(100),
    permissions TEXT[],  -- ['payments:write', 'refunds:write']
    is_live BOOLEAN DEFAULT false,  -- test vs live mode
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    revoked_at TIMESTAMP
);

CREATE INDEX idx_api_keys_merchant ON api_keys(merchant_id);
CREATE INDEX idx_api_keys_hash ON api_keys(key_hash) WHERE revoked_at IS NULL;

-- Webhook endpoints
CREATE TABLE webhook_endpoints (
    endpoint_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(merchant_id),
    url VARCHAR(500) NOT NULL,
    events TEXT[],  -- ['payment.succeeded', 'refund.created']
    secret VARCHAR(64) NOT NULL,  -- For signature verification
    is_enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Gateway configurations per merchant
CREATE TABLE merchant_gateway_configs (
    config_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(merchant_id),
    gateway VARCHAR(50) NOT NULL,  -- stripe, braintree, adyen
    gateway_merchant_id VARCHAR(100),  -- Merchant's ID at gateway
    gateway_api_key_encrypted BYTEA,
    is_enabled BOOLEAN DEFAULT true,
    priority INTEGER DEFAULT 1,  -- Preferred gateway
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(merchant_id, gateway)
);
```

### 4. Fraud Database (PostgreSQL)

```sql
-- Fraud rules
CREATE TABLE fraud_rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rule_type VARCHAR(50),  -- velocity, amount, geolocation, bin
    conditions JSONB NOT NULL,  -- Rule logic
    action VARCHAR(20) NOT NULL,  -- block, review, require_3ds, allow
    is_active BOOLEAN DEFAULT true,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Blocklists
CREATE TABLE blocklists (
    entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL,  -- card, email, ip, bin
    value_hash VARCHAR(64) NOT NULL,  -- Hashed value
    reason TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100)
);

CREATE INDEX idx_blocklists_type ON blocklists(type, value_hash) 
    WHERE expires_at IS NULL OR expires_at > NOW();

-- Fraud events (transactions flagged)
CREATE TABLE fraud_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID REFERENCES payments(payment_id),
    risk_score INTEGER NOT NULL,
    fraud_signals JSONB,  -- Which checks failed
    action_taken VARCHAR(20),  -- blocked, reviewed, allowed_with_3ds
    reviewed_by VARCHAR(100),
    review_decision VARCHAR(20),  -- approve, decline
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_fraud_events_payment ON fraud_events(payment_id);
CREATE INDEX idx_fraud_events_score ON fraud_events(risk_score DESC, created_at DESC);
```

---

## Payment Processing Flow

### End-to-End Flow: E-commerce Checkout

```
Customer checking out with $49.99 purchase

┌─────────────────────────────────────────────────────────┐
│ T=0ms: Customer submits payment at checkout             │
└─────────────────────────────────────────────────────────┘

Checkout form submits:
{
  "amount": 4999,  // cents
  "currency": "USD",
  "card": {
    "number": "4242424242424242",
    "exp_month": 12,
    "exp_year": 2025,
    "cvc": "123"
  },
  "billing": {...},
  "email": "alice@example.com",
  "ip_address": "192.168.1.100",
  "device_id": "device_xyz"
}

┌─────────────────────────────────────────────────────────┐
│ T=5ms: API Gateway receives request                     │
└─────────────────────────────────────────────────────────┘

1. TLS termination
2. WAF checks (SQL injection, XSS attempts)
3. Rate limit check: INCR ratelimit:merchant_xyz:minute
   → 847 requests this minute (< 1000 limit) ✓
4. Authenticate merchant (API key validation)
5. Route to Payment Service

┌─────────────────────────────────────────────────────────┐
│ T=10ms: Payment Service processes request               │
└─────────────────────────────────────────────────────────┘

1. Generate payment_intent_id: pi_abc123

2. Validate card (client-side):
   - Luhn algorithm: ✓ Valid
   - Expiry: 12/2025 ✓ Not expired
   - CVV: 123 ✓ 3 digits

3. Extract card details:
   - BIN: 424242 (Visa test card)
   - Last four: 4242
   - Brand: Visa

4. Check idempotency:
   GET idempotency:client_uuid_abc
   → Not found (first request)

5. Create record in database:
   INSERT INTO payments (payment_intent_id, merchant_id, 
                         amount, currency, status)
   VALUES ('pi_abc123', 'merch_xyz', 4999, 'USD', 'processing');

6. Return 202 Accepted immediately:
   {
     "payment_intent_id": "pi_abc123",
     "status": "processing"
   }

7. Publish to Kafka (payments.authorization topic)

Response time: 10-20ms

┌─────────────────────────────────────────────────────────┐
│ T=20ms: Tokenization Service (PCI-compliant zone)       │
└─────────────────────────────────────────────────────────┘

1. Worker consumes Kafka message
2. Extract card number: 4242424242424242
3. Generate fingerprint: SHA256(4242... + salt) → hash_abc
4. Check if already tokenized:
   SELECT token FROM card_vault WHERE fingerprint = 'hash_abc'
   → Not found (new card)

5. Encrypt with HSM:
   encrypted_pan = HSM.encrypt("4242424242424242", key_v1)

6. Generate token: tok_visa_4242

7. Store in vault:
   INSERT INTO card_vault (token, fingerprint, encrypted_pan, 
                           last_four, brand)
   VALUES ('tok_visa_4242', 'hash_abc', encrypted_pan, 
           '4242', 'visa');

8. Return token to Payment Service
9. Discard card number from memory (PCI requirement)

Time: 10ms

┌─────────────────────────────────────────────────────────┐
│ T=30ms: Fraud Detection Service analyzes transaction   │
└─────────────────────────────────────────────────────────┘

1. Extract features:
   - Amount: $49.99 (normal range ✓)
   - IP: 192.168.1.100 → Geo: New York, US
   - Card BIN: 424242 → Issuer: US Bank
   - Device: Known device for this customer ✓
   - Time: 2 PM EST (normal shopping hours ✓)

2. Velocity checks (Redis):
   a. Card velocity:
      ZCOUNT velocity:card:hash_abc <1h_ago> <now>
      → 0 transactions (first time) ✓
   
   b. IP velocity:
      ZCOUNT velocity:ip:192.168.1.100 <1h_ago> <now>
      → 2 transactions (normal) ✓
   
   c. Email velocity:
      ZCOUNT velocity:email:alice <1h_ago> <now>
      → 0 transactions ✓

3. Check blocklists:
   SISMEMBER blocklist:cards hash_abc → false ✓
   SISMEMBER blocklist:ips 192.168.1.100 → false ✓

4. ML model prediction:
   POST /sagemaker/fraud-model
   Features: [49.99, 'visa', 'US', 'US', 14, ...]
   Response: {fraud_probability: 0.08}  // 8%

5. Calculate final risk score:
   base = 8
   + geolocation_match: 0 (US card, US IP)
   + velocity_normal: 0
   + known_device: -5 (bonus for known device)
   final_score = max(8 - 5, 0) = 3

6. Decision:
   Score 3 = Very Low Risk
   → approve: true
   → require_3ds: false

7. Update database:
   UPDATE payments SET risk_score = 3, risk_level = 'low'
   WHERE payment_intent_id = 'pi_abc123';

Time: 50ms

┌─────────────────────────────────────────────────────────┐
│ T=80ms: Authorization Worker processes payment         │
└─────────────────────────────────────────────────────────┘

1. Consume from Kafka
2. Load payment details from DB
3. Risk score: 3 (low) → Proceed without 3DS

4. Gateway selection:
   - Gateway Router evaluates options
   - Stripe selected (best score)

5. Detokenize card:
   Request to Tokenization Service:
   {token: "tok_visa_4242"}
   Response: {pan: "4242424242424242", cvv: "123"}

6. Build Stripe request:
   {
     "amount": 4999,
     "currency": "usd",
     "source": {
       "number": "4242424242424242",
       "exp_month": 12,
       "exp_year": 2025,
       "cvc": "123"
     },
     "capture": true,  // Immediate capture
     "description": "Order #ORD-12345",
     "statement_descriptor": "MYSTORE.COM",
     "metadata": {
       "payment_intent_id": "pi_abc123"
     }
   }

7. Send to Stripe:
   POST https://api.stripe.com/v1/charges
   Idempotency-Key: pi_abc123

8. Stripe processes (network call to card issuer)
   ...waiting for response...

Time: 10ms (prep) + 150ms (Stripe) = 160ms

┌─────────────────────────────────────────────────────────┐
│ T=240ms: Stripe returns success response                │
└─────────────────────────────────────────────────────────┘

Response:
{
  "id": "ch_stripe_xyz789",
  "status": "succeeded",
  "amount": 4999,
  "captured": true,
  "authorization_code": "AUTH_456789",
  "risk_score": 12,
  "outcome": {
    "type": "authorized",
    "network_status": "approved_by_network",
    "reason": null
  }
}

┌─────────────────────────────────────────────────────────┐
│ T=250ms: Update database with success                   │
└─────────────────────────────────────────────────────────┘

BEGIN TRANSACTION;

UPDATE payments
SET status = 'succeeded',
    gateway_transaction_id = 'ch_stripe_xyz789',
    authorization_code = 'AUTH_456789',
    amount_authorized = 4999,
    amount_captured = 4999,
    authorized_at = NOW(),
    captured_at = NOW(),
    gateway_response = '{"id": "ch_stripe_xyz789", ...}'
WHERE payment_intent_id = 'pi_abc123';

COMMIT;

Time: 20ms

┌─────────────────────────────────────────────────────────┐
│ T=270ms: Publish success events                         │
└─────────────────────────────────────────────────────────┘

1. Kafka: payments.status_update
   {
     "payment_intent_id": "pi_abc123",
     "status": "succeeded",
     "amount": 4999,
     "timestamp": "2024-01-08T10:30:00.270Z"
   }

2. Update Redis cache:
   SET payment:pi_abc123:status 'succeeded' EX 3600

3. Update velocity counters:
   ZADD velocity:card:hash_abc <timestamp> pi_abc123
   ZADD velocity:email:alice <timestamp> pi_abc123

Time: 10ms

┌─────────────────────────────────────────────────────────┐
│ T=280ms: Webhook to merchant                            │
└─────────────────────────────────────────────────────────┘

1. Lookup merchant webhook URL:
   GET webhook:merchant_xyz
   → https://merchant.com/webhooks/payment

2. Build webhook payload:
   {
     "event": "payment.succeeded",
     "payment_intent_id": "pi_abc123",
     "amount": 4999,
     "currency": "USD",
     "created_at": "2024-01-08T10:30:00Z"
   }

3. Sign payload (HMAC-SHA256):
   signature = HMAC(payload, merchant_webhook_secret)

4. Send webhook:
   POST https://merchant.com/webhooks/payment
   Headers:
     - X-Signature: signature
     - X-Event-Type: payment.succeeded

5. Merchant returns 200 OK
6. Mark webhook delivered

Time: 50ms

┌─────────────────────────────────────────────────────────┐
│ T=330ms: Analytics & logging                            │
└─────────────────────────────────────────────────────────┘

1. Write to ClickHouse (analytics DB):
   INSERT INTO payment_events
   (payment_id, merchant_id, amount, currency, status,
    gateway, latency_ms, risk_score, timestamp)
   VALUES ('pi_abc123', 'merch_xyz', 4999, 'USD', 'succeeded',
           'stripe', 240, 3, NOW());

2. Write audit log to S3:
   {
     "event_type": "payment.processed",
     "payment_intent_id": "pi_abc123",
     "merchant_id": "merch_xyz",
     "amount": 4999,
     "status": "succeeded",
     "timestamp": "2024-01-08T10:30:00.330Z",
     "ip_address": "REDACTED",  // PCI: No PII in logs
     "user_agent": "REDACTED"
   }

Time: 20ms

Total end-to-end latency: 330ms ✓
(< 500ms SLA met)
```

### Failure Scenario: Card Declined

```
Same flow, but Step 8 (Stripe response) differs:

┌─────────────────────────────────────────────────────────┐
│ T=240ms: Stripe returns decline response                │
└─────────────────────────────────────────────────────────┘

Response (402 Payment Required):
{
  "error": {
    "type": "card_error",
    "code": "card_declined",
    "decline_code": "insufficient_funds",
    "message": "Your card has insufficient funds.",
    "charge": "ch_stripe_failed_xyz"
  }
}

┌─────────────────────────────────────────────────────────┐
│ T=250ms: Handle decline                                 │
└─────────────────────────────────────────────────────────┘

1. Update database:
   UPDATE payments
   SET status = 'failed',
       gateway_transaction_id = 'ch_stripe_failed_xyz',
       decline_code = 'insufficient_funds',
       decline_message = 'Your card has insufficient funds',
       failed_at = NOW()
   WHERE payment_intent_id = 'pi_abc123';

2. Publish failure event (Kafka)

3. Webhook to merchant:
   Event: payment.failed
   Include decline_code for merchant to display

4. DO NOT retry (insufficient funds won't change)

5. Return to customer:
   {
     "payment_intent_id": "pi_abc123",
     "status": "failed",
     "error": {
       "code": "card_declined",
       "message": "Your card was declined. Please try another card.",
       "decline_code": "insufficient_funds"
     }
   }

Merchant displays: "Payment failed. Please use a different payment method."

Total latency: 250ms
```

---

## Deep Dives

### 1. Handling Payment Failures & Retries

**Types of Failures**:

**1. Soft Declines** (Temporary issues - should retry):
```
Examples:
- issuer_declined (bank timeout)
- processing_error (network glitch)
- try_again_later
- do_not_honor (temporary block)

Handling:
1. Mark payment as 'retryable'
2. Schedule retry:
   - Attempt 1: Immediate (different gateway)
   - Attempt 2: 1 hour later
   - Attempt 3: 24 hours later
3. Use different gateway for retry
4. Notify customer only after all retries fail

Success rate: 30-40% of soft declines succeed on retry
```

**2. Hard Declines** (Permanent issues - don't retry):
```
Examples:
- insufficient_funds
- expired_card
- incorrect_cvc
- card_not_supported
- lost_card
- stolen_card

Handling:
1. Mark payment as 'failed' (final)
2. Don't retry (won't succeed)
3. Immediately notify customer
4. Suggest alternative payment method
5. Log for analytics

These indicate:
- Customer needs to update card
- Use different payment method
- Fraud (lost/stolen)
```

**Smart Retry Logic**:
```
For subscriptions with soft decline:

Day 0: First attempt fails (issuer_declined)
- Don't email customer yet (might succeed on retry)

Day 0 + 2 hours: Retry attempt #1
- Try different gateway (Stripe → Braintree)
- Use different time (maybe end of day = more funds)
- If succeeds: Customer never knew it failed ✓

Day 1: Retry attempt #2
- Try original gateway again
- Maybe on payday (1st, 15th of month)
- If succeeds: Send "Payment processed" email

Day 3: Retry attempt #3
- Final attempt
- If succeeds: Great!
- If fails: Email customer "Payment failed, update card"

ML model predicts best retry time:
- Analyzes historical success patterns
- Considers day of month, time of day
- Personalizes retry schedule per customer
```

### 2. Preventing Double Charging

**Challenge**: Network failures may cause duplicate requests

**Prevention Layers**:

**Layer 1: Idempotency Keys** (Client-side):
```
Client generates unique UUID per payment attempt:

Request 1:
{
  "idempotency_key": "idem_abc123",
  "amount": 4999,
  ...
}

Server:
1. Check Redis: GET idempotency:idem_abc123
2. Not found → Process payment
3. Cache result: SETEX idempotency:idem_abc123 86400 {response}
4. Return response

Request 2 (retry after timeout):
{
  "idempotency_key": "idem_abc123",  // SAME key
  "amount": 4999,
  ...
}

Server:
1. Check Redis: GET idempotency:idem_abc123
2. Found! → Return cached response
3. Don't process again ✓

Result: Customer charged only once
```

**Layer 2: Database Uniqueness** (Server-side):
```
Unique constraint on payment_intent_id:

CREATE UNIQUE INDEX idx_payment_intent_unique 
ON payments(payment_intent_id);

If duplicate INSERT attempted:
→ PostgreSQL error: duplicate key value
→ Catch error, return existing payment
→ Never create duplicate payment record
```

**Layer 3: Gateway Idempotency**:
```
All gateway APIs support idempotency:

Stripe:
POST /charges
Header: Idempotency-Key: pi_abc123

If same key sent twice:
- Stripe returns same charge object
- No duplicate charge created
- Safe to retry

Braintree, Adyen: Similar mechanisms
```

**Layer 4: Payment State Machine**:
```
States:
processing → authorized → captured
                      ↘ failed

Transitions only allowed in one direction:
- Can't go from 'succeeded' to 'processing'
- Can't authorize already authorized payment
- Can't capture already captured payment

Database constraint:
- Check status before state transitions
- Atomic compare-and-swap
- Prevents duplicate operations
```

### 3. Multi-Currency Handling

**Currency Conversion**:
```
Scenario: US merchant, European customer

Customer sees: €45.00 (their local currency)
Merchant receives: $49.99 (their currency)

Exchange rate: 1 EUR = 1.11 USD

Flow:
1. Customer payment request:
   {
     "amount": 4500,  // €45.00 in cents
     "currency": "EUR",
     "present_currency": "EUR"  // Display currency
   }

2. Get FX rate (Redis cache or FX service):
   GET fx:rate:EUR:USD
   → 1.11

3. Convert to merchant currency:
   amount_usd = 4500 / 100 * 1.11 = $49.95
   → Round: $49.99

4. Process payment in USD with Stripe

5. Store both amounts:
   amount = 4999 (USD)
   amount_customer = 4500 (EUR)
   fx_rate = 1.11
   customer_currency = EUR

6. Receipt shows:
   "Charged: €45.00 (= $49.99 USD at rate 1.11)"

Benefits:
- Customer sees familiar currency
- Merchant gets their currency
- Exchange rate locked at transaction time
```

**FX Rate Management**:
```
Update rates every hour:

1. Cron job queries FX API (xe.com, fixer.io)
2. Get rates for all currency pairs
3. Store in Redis:
   SET fx:rate:EUR:USD 1.11 EX 3600
   SET fx:rate:GBP:USD 1.27 EX 3600
   ...

4. Add margin (0.5% for revenue):
   customer_rate = base_rate × 1.005

5. Fallback if cache miss:
   - Query PostgreSQL (last known rate)
   - Mark as stale
   - Alert if rate older than 4 hours

Rate volatility handling:
- Crypto: Update every minute (volatile)
- Major pairs (USD/EUR): Every hour
- Minor pairs: Every 4 hours
```

### 4. Dispute & Chargeback Management

**Chargeback Process**:
```
Lifecycle:
1. Customer disputes with bank (30-120 days after purchase)
2. Bank creates chargeback
3. Gateway notifies us (webhook)
4. Funds withdrawn from merchant account
5. Merchant responds with evidence (7-21 days)
6. Bank reviews evidence
7. Final decision (can take 60-90 days)

Our system's role:
- Receive chargeback notification
- Alert merchant immediately
- Provide evidence upload interface
- Submit evidence to gateway
- Track status updates
- Final outcome handling
```

**Evidence Collection**:
```
Types of evidence that help win disputes:

1. Proof of delivery:
   - Shipping tracking (signature required)
   - GPS coordinates of delivery
   - Photos of delivered package

2. Customer communication:
   - Email confirmations
   - Chat transcripts
   - Phone call recordings

3. Service/product evidence:
   - Login logs (proves customer used service)
   - Usage data (customer actively used product)
   - Invoices and receipts

4. Fraud prevention:
   - AVS match (address verification)
   - CVV match (card security code)
   - 3D Secure authentication
   - IP address matches card country

Merchant portal:
- Upload evidence files (drag & drop)
- Automatic submission to gateway
- Track dispute status
- Get expert advice on evidence
```

**Chargeback Prevention**:
```
Proactive measures:

1. Clear billing descriptor:
   - Bad: "XYZ123 CORP"
   - Good: "MYSTORE.COM - Order 12345"
   - Customer recognizes charge = fewer disputes

2. Customer service:
   - Easy refund process
   - Resolve issues before they dispute
   - Refund costs $0.50, chargeback costs $25

3. Confirmation emails:
   - Send immediately after purchase
   - Include merchant name, amount, description
   - Provide contact info for questions

4. Fraud prevention:
   - Strong fraud detection
   - Use 3D Secure
   - AVS and CVV checks
   - Less fraud = fewer chargebacks

5. Documentation:
   - T&C acceptance logs
   - Delivery confirmation
   - Service usage logs
   - Helps win disputes

Goal: Keep chargeback rate < 0.5%
Above 1% = High risk (gateway may terminate account)
```

### 5. PCI-DSS Compliance

**PCI-DSS Requirements**:

**Requirement 1-2: Network Security**
```
✅ Firewall between cardholder data environment and internet
✅ No default passwords
✅ Encrypted transmission of card data (TLS 1.3)
✅ Network segmentation (tokenization service in separate VPC)

Implementation:
- Tokenization service: Isolated VPC (10.0.0.0/16)
- No direct internet access
- Access only via private API gateway
- All traffic encrypted (mTLS)
- Firewall rules: Whitelist only authorized services
```

**Requirement 3-4: Cardholder Data Protection**
```
✅ Never store full PAN (Primary Account Number) in plaintext
✅ Mask PAN when displayed (show only last 4 digits)
✅ Render PAN unreadable (encryption with HSM)
✅ Cryptographic keys managed securely

Implementation:
- Tokenization: Replace PAN with token
- Encryption: AES-256 with HSM-managed keys
- Access: Only tokenization service touches raw PAN
- Logs: Never log full PAN (mask to "****4242")
- Display: Show "Visa ****4242" to users
```

**Requirement 5-6: Vulnerability Management**
```
✅ Use and regularly update anti-virus software
✅ Develop secure systems and applications
✅ Regular security scans and penetration tests
✅ Patch management (update within 30 days)

Implementation:
- Quarterly penetration tests (mandator)
- Automated vulnerability scans (weekly)
- Security code reviews (all payment code)
- Dependency updates (automated via Dependabot)
```

**Requirement 7-8: Access Control**
```
✅ Restrict access to cardholder data (need-to-know basis)
✅ Unique ID for each person with computer access
✅ Multi-factor authentication for admin access
✅ Audit trail of all access to cardholder data

Implementation:
- RBAC: Roles limit access to sensitive data
- MFA: Required for production database access
- Audit: Every vault access logged (immutable)
- Least privilege: Developers don't access production data
```

**Requirement 9-10: Physical & Monitoring**
```
✅ Restrict physical access to cardholder data
✅ Track and monitor all access to network resources
✅ Regularly test security systems and processes

Implementation:
- Cloud provider handles physical security (AWS)
- Log all API requests (CloudTrail)
- Monitor for suspicious patterns
- Alert on anomalies (PagerDuty)
- Annual PCI compliance audit (QSA)
```

**Requirement 11-12: Testing & Policy**
```
✅ Regularly test security systems and processes
✅ Maintain a policy that addresses information security

Implementation:
- Automated security tests in CI/CD
- Annual employee security training
- Incident response plan (tested quarterly)
- Documented security policies
- Compliance team oversight
```

---

## Scalability & Reliability

### Horizontal Scaling

**API Servers** (Stateless - Easy to scale):
```
Auto-scaling rules:
- Scale up: CPU > 70% OR RPS > 400 per server
- Scale down: CPU < 30% AND RPS < 200 per server
- Min instances: 20 per region
- Max instances: 200 per region

Deployment:
- Blue-green deployments (zero downtime)
- Canary releases (1% → 10% → 100%)
- Health checks every 10 seconds
- Auto-recovery on failure
```

**Worker Scaling** (Consumer groups):
```
Kafka Consumer Groups:

Authorization Workers:
- Current: 20 workers, processing 579 TPS
- Queue depth monitoring: If > 10K messages
  → Auto-scale to 40 workers
  → Kafka rebalances partitions
  → Processing capacity doubled

Capture Workers:
- Scale independently based on capture queue depth

Refund Workers:
- Lower priority, scale more conservatively
```

**Database Scaling**:

**PostgreSQL** (Transactions):
```
Vertical: Upgrade instance size
- db.r5.4xlarge → db.r5.8xlarge
- Double CPU/memory
- 5-minute downtime for upgrade

Horizontal: Read replicas
- Master: All writes
- 10 read replicas: Read-only queries
- Connection pooling (PgBouncer)
- Route reads to nearest replica

Sharding (for massive scale):
- Shard by merchant_id (mod 64)
- Each shard: Independent database
- Cross-shard queries: Federation layer
- Implementation: Citus extension
```

**Redis Cluster**:
```
Current: 10 nodes, 16GB each = 160GB total

Scale up:
1. Add nodes to cluster
2. Redis Cluster reshards automatically
3. 16,384 hash slots redistributed
4. No downtime

Result: 20 nodes, 320GB, double throughput
```

### Multi-Region Deployment

**Active-Active Configuration**:
```
3 Regions: US-East, EU-West, AP-Southeast

Each region:
- Full payment processing stack
- Local PostgreSQL (master)
- Local Redis cluster
- Local Kafka cluster
- Gateway connectors

Benefits:
- Low latency (< 100ms to nearest region)
- High availability (region failure → failover)
- Compliance (data residency)

Challenges:
- Cross-region data synchronization
- Conflict resolution
- Network partitions
```

**Data Replication**:
```
PostgreSQL:
- Logical replication (async)
- Each region has master for local writes
- Replicate to other regions (eventual consistency)
- Read from local, write to local
- Conflict resolution: Last-write-wins with timestamp

Redis:
- Independent per region (no cross-region sync)
- Rebuild cache from database if needed
- Acceptable (cache can be stale)

Kafka:
- MirrorMaker 2 for cross-region replication
- Disaster recovery (replay from other region)
```

### High Availability

**99.99% Uptime Strategy**:
```
Allowed downtime: 52.56 minutes/year = 4.38 minutes/month

Strategies:
1. No single point of failure
   - Multiple API servers behind load balancer
   - Database replicas (failover < 1 minute)
   - Redis Cluster (automatic failover)
   - Multi-region deployment

2. Health checks
   - Load balancer checks every 10 seconds
   - Remove unhealthy instances
   - Auto-replace failed instances

3. Graceful degradation
   - If fraud check slow: Proceed with payment
   - If Redis down: Fall back to database
   - If one gateway down: Use another

4. Disaster recovery
   - Automated backups (every hour)
   - Cross-region replication
   - Tested recovery procedures (monthly drills)
   - RPO: < 1 minute, RTO: < 15 minutes
```

**Failure Scenarios**:

**API Server Failure**:
```
Detection: Health check fails (3 consecutive)

Impact: That server stops receiving traffic

Mitigation:
- Load balancer removes from pool
- Traffic distributed to healthy servers
- Auto-scaling launches replacement
- No customer impact

Recovery: < 30 seconds
```

**Database Master Failure**:
```
Detection: Connection failures, replication lag spikes

Impact: Write operations fail

Mitigation:
- Automatic failover to read replica
- Promote replica to master (< 1 minute)
- Update connection endpoints
- Brief write downtime (< 2 minutes)

Recovery: 1-2 minutes
Customer impact: Minimal (retries succeed)
```

**Gateway Failure**:
```
Detection: Circuit breaker opens (50% failure rate)

Impact: Can't process payments via that gateway

Mitigation:
- Automatic routing to backup gateway
- Circuit breaker prevents flood of failures
- Alert on-call engineer
- Monitor gateway status page

Recovery: When gateway recovers (minutes to hours)
Customer impact: None (transparent failover)
```

**Entire Region Failure**:
```
Detection: All health checks fail in region

Impact: Customers in region can't process payments

Mitigation:
- DNS failover (Route 53 health checks)
- Route traffic to next nearest region
- Slightly higher latency (50-100ms)
- All data replicated (no data loss)

Recovery: 2-5 minutes (DNS propagation)
Customer impact: Brief interruption, then recovered
```

### Monitoring & Alerting

**Key Metrics**:
```
Golden Signals:
1. Latency:
   - p50: 150ms
   - p95: 350ms
   - p99: 500ms
   - p99.9: 1000ms

2. Traffic:
   - Requests/second
   - Transactions/second
   - By payment method, gateway, region

3. Errors:
   - Error rate (< 1%)
   - Decline rate (< 15%)
   - Gateway errors
   - Database errors

4. Saturation:
   - CPU usage (< 70%)
   - Memory usage (< 80%)
   - Queue depth
   - Database connections
```

**Alerts** (PagerDuty):
```
P0 (Page immediately, 24/7):
- Payment API down (> 10% error rate)
- Authorization latency > 1 second (p95)
- Database master down
- Zero successful payments in 5 minutes
- Critical security breach

P1 (Alert on-call):
- Error rate > 2%
- Latency degradation (p95 > 500ms)
- Gateway circuit breaker open
- Fraud detection service down
- Settlement reconciliation mismatch > 0.1%

P2 (Business hours):
- Chargeback rate increasing
- Slow queries detected
- Cache hit ratio < 80%
- Disk space > 80%
```

**Dashboards** (Grafana):
```
Operations Dashboard:
- Real-time TPS (transactions per second)
- Success vs decline rate
- Latency percentiles (p50, p95, p99)
- Gateway performance comparison
- Queue depths
- Database performance
- Cache hit ratios

Business Dashboard:
- Revenue (real-time and historical)
- Transaction volume by country
- Payment method distribution
- Top merchants by volume
- Chargeback trends
- Fraud detection accuracy

Fraud Dashboard:
- Risk score distribution
- Fraud rate (actual fraud / total)
- False positive rate
- Manual review queue depth
- Blocked transactions
- Velocity anomalies
```

---

## Trade-offs & Alternatives

### 1. Database: PostgreSQL vs Alternatives

**PostgreSQL** (Chosen):
```
Pros:
+ ACID compliance (critical for money)
+ Mature and battle-tested
+ Strong consistency
+ Complex queries (joins, analytics)
+ JSON support (JSONB)
+ Active community

Cons:
- Vertical scaling limits
- Sharding not native
- Write throughput limits
- More expensive than NoSQL

Use when: Financial data, need ACID, complex queries
```

**MySQL**:
```
Pros:
+ Similar to PostgreSQL
+ Slightly better write performance
+ More hosting options

Cons:
- Less feature-rich than PostgreSQL
- JSONB support limited
- Replication more complex

Would work, but PostgreSQL preferred for features
```

**MongoDB/DynamoDB**:
```
Pros:
+ Horizontal scaling (easy)
+ High write throughput
+ Flexible schema
+ Lower cost at scale

Cons:
- No ACID across documents (deal-breaker for payments)
- Eventual consistency
- Complex transactions difficult
- Not suitable for financial data

Don't use: Consistency requirements too strict
```

### 2. Message Queue: Kafka vs Alternatives

**Kafka** (Chosen):
```
Pros:
+ High throughput (millions/sec)
+ Durable (disk-backed)
+ Exactly-once semantics
+ Replay capability (auditing)
+ Ordered per partition
+ Consumer groups (scaling)

Cons:
- Operational complexity
- Higher latency (disk writes)
- Resource intensive
- Steep learning curve

Use when: High volume, need durability, exactly-once
```

**RabbitMQ**:
```
Pros:
+ Easier to operate
+ Lower latency
+ Mature, stable
+ Good routing features

Cons:
- Lower throughput
- No replay
- Memory-bound
- Not ideal for high-volume financial

Could work for smaller scale (< 1000 TPS)
```

**Amazon SQS**:
```
Pros:
+ Fully managed
+ No operations
+ Auto-scaling
+ Pay per use

Cons:
- No exactly-once for standard queues
- FIFO queues limited to 300 TPS (3K with batching)
- No replay
- Higher cost at scale

Not suitable: Throughput and exactly-once requirements
```

### 3. Gateway Strategy: Multi vs Single

**Multi-Gateway** (Chosen):
```
Pros:
+ No vendor lock-in
+ Failover capability
+ Route by cost/performance
+ Regional compliance
+ Negotiate better rates

Cons:
- More complex integration
- Multiple SDKs to maintain
- Reconciliation complexity
- Testing burden

Use when: High volume, need reliability, cost-sensitive
```

**Single Gateway** (Stripe/Adyen):
```
Pros:
+ Simpler integration
+ Single SDK
+ Unified reporting
+ Easier reconciliation
+ Faster implementation

Cons:
- Vendor lock-in
- Single point of failure
- Less negotiating power
- Regional limitations
- Higher risk

Use when: Small scale, speed to market, trust single vendor
```

### 4. 3D Secure: Always vs Dynamic

**Dynamic 3DS** (Chosen):
```
Pros:
+ Better user experience (less friction)
+ Higher conversion rate
+ Only secure when needed
+ Cost savings (per-auth fee)

Cons:
- More complex logic
- Risk if fraud model wrong
- Liability for non-3DS fraud

Use when: Optimizing conversion, good fraud model
```

**Always Require 3DS**:
```
Pros:
+ Maximum security
+ Liability shift to issuer
+ Regulatory compliance (EU)
+ Lower fraud rate

Cons:
- User friction (5-10 second delay)
- Lower conversion (5-10% drop)
- Higher cart abandonment
- Cost (per-auth fees)

Use when: High-risk merchants, regulatory requirement
```

### 5. Settlement: T+1 vs Instant

**T+1 Settlement** (Chosen):
```
Pros:
+ Standard industry practice
+ Allows for fraud review
+ Chargeback reserve period
+ Lower risk for platform

Cons:
- Merchants wait for funds
- Cash flow impact
- Competitive disadvantage

Use when: Need fraud protection, standard merchants
```

**Instant Settlement**:
```
Pros:
+ Merchants get money immediately
+ Competitive advantage
+ Better merchant satisfaction

Cons:
- Higher fraud risk
- Platform liable for chargebacks
- Need larger reserve fund
- Higher operational risk

Use when: Trusted merchants, premium service
```

---

## Conclusion

This credit card processing system handles 50 million daily transactions with:

**Key Features**:
- **Multi-gateway support**: Stripe, Braintree, Adyen with automatic failover
- **Real-time fraud detection**: ML-based risk scoring < 100ms
- **PCI-DSS Level 1 compliant**: Tokenization, HSM encryption, network isolation
- **High performance**: < 500ms authorization (p95), 10K TPS capacity
- **Strong reliability**: 99.99% uptime, zero transaction loss, exactly-once payments
- **Multi-currency**: 50+ currencies with real-time FX rates
- **3D Secure**: Dynamic SCA for optimal conversion vs security
- **Subscription billing**: Automated recurring charges with smart dunning

**Scalability**:
- Horizontal scaling: Add servers, workers, database replicas
- Multi-region: Active-active deployment in 3 regions
- Peak capacity: 10K TPS (Black Friday) with auto-scaling
- Future growth: Scale to 500M transactions/day by adding capacity

**Security**:
- Tokenization: Never store raw card numbers
- HSM encryption: FIPS 140-2 Level 3
- Network isolation: Separate VPC for card data
- Audit trail: Immutable logs of every access
- Penetration testing: Quarterly audits

**Reliability**:
- Idempotency: Prevent double charging
- Circuit breakers: Automatic gateway failover
- Retries: Exponential backoff with jitter
- Reconciliation: Daily matching with gateways
- Monitoring: Comprehensive metrics and alerting

The system is production-ready for a payment processing platform serving millions of merchants and billions in transaction volume.

---

*This design document is for educational purposes in system design interviews.*
*Last Updated: November 2025*
