# Robinhood Stock Trading Platform - Complete Architecture

## Full System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                    │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │   iOS App    │    │  Android App │    │  Web Client  │                 │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                 │
└─────────┼──────────────────┼──────────────────┼────────────────────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       CDN LAYER (CloudFront)                                 │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Edge Locations: 300+ globally                                       │  │
│  │  Content: Static assets, stock charts, company logos                │  │
│  │  Performance: <20ms latency                                          │  │
│  │  Handles: 30% of total traffic                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LOAD BALANCER (ALB)                           │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Multi-AZ Deployment | Health Checks (15s) | SSL Termination         │  │
│  │  Geographic routing | Auto-failover | 99.99% availability            │  │
│  │  WebSocket support (sticky sessions)                                 │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────┬────────────────────────────────┬───────────────────────────┘
                 │                                │
         ┌───────┴────────┐              ┌────────┴───────┐
         │                │              │                │
         ▼                ▼              ▼                ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│  API Gateway   │  │  API Gateway   │  │  API Gateway   │  │  API Gateway   │
│  US-EAST-1a    │  │  US-EAST-1b    │  │  US-WEST-2     │  │  EU-WEST-1     │
│                │  │                │  │                │  │                │
│  • Auth/JWT    │  │  • Auth/JWT    │  │  • Auth/JWT    │  │  • Auth/JWT    │
│  • Rate Limit  │  │  • Rate Limit  │  │  • Rate Limit  │  │  • Rate Limit  │
│  • API Routing │  │  • API Routing │  │  • API Routing │  │  • API Routing │
└────────┬───────┘  └────────┬───────┘  └────────┬───────┘  └────────┬───────┘
         │                   │                   │                   │
         └───────────────────┴───────────────────┴───────────────────┘
                                      │
┌─────────────────────────────────────┼─────────────────────────────────────┐
│                        MICROSERVICES LAYER                                 │
│                                                                            │
│     ┌────────────────────────────────┴────────────────────────────┐       │
│     │                                                              │       │
│     ▼                    ▼                    ▼                   ▼       │
│ ┌──────────┐      ┌──────────┐       ┌──────────┐       ┌──────────┐    │
│ │   USER   │      │  WALLET  │       │ TRADING  │       │  MARKET  │    │
│ │ SERVICE  │      │ SERVICE  │       │ SERVICE  │       │   DATA   │    │
│ │          │      │          │       │          │       │ SERVICE  │    │
│ │ • Auth   │      │ • Balance│       │ • Orders │       │ • Quotes │    │
│ │ • KYC    │      │ • Deposit│       │ • Exec   │       │ • Charts │    │
│ │ • Profile│      │ • Withdraw│      │ • Cancel │       │ • Feed   │    │
│ └────┬─────┘      └────┬─────┘       └────┬─────┘       └────┬─────┘    │
│      │                 │                   │                   │          │
│      ▼                 ▼                   ▼                   ▼          │
│ ┌──────────┐      ┌──────────┐       ┌──────────┐       ┌──────────┐    │
│ │PORTFOLIO │      │   KYC    │       │  ORDER   │       │  PRICE   │    │
│ │ SERVICE  │      │ SERVICE  │       │ MATCHING │       │AGGREGATOR│    │
│ │          │      │          │       │  ENGINE  │       │          │    │
│ │ • P&L    │      │ • Verify │       │ • Match  │       │ • Stream │    │
│ │ • Holds  │      │ • Review │       │ • Route  │       │ • Cache  │    │
│ │ • Returns│      │ • Docs   │       │ • Fill   │       │ • Parse  │    │
│ └────┬─────┘      └────┬─────┘       └────┬─────┘       └────┬─────┘    │
│      │                 │                   │                   │          │
│      └─────────────────┴───────────────────┴───────────────────┘          │
└─────────────────────────────────┬──────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MESSAGE QUEUE (Apache Kafka)                          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Topics & Partitions:                                                │  │
│  │  • orders.new (50 partitions) → Order placement events              │  │
│  │  • orders.filled (50 partitions) → Execution confirmations          │  │
│  │  • prices.updates (100 partitions) → Market data stream             │  │
│  │  • portfolio.updates (30 partitions) → Holdings changes             │  │
│  │  • notifications (20 partitions) → User notifications                │  │
│  │                                                                       │  │
│  │  Configuration:                                                      │  │
│  │  • 12 brokers (m5.2xlarge)                                           │  │
│  │  • Replication factor: 3                                             │  │
│  │  • Retention: 7 days                                                 │  │
│  │  • Throughput: 300K messages/sec                                     │  │
│  │  • Latency: < 10ms (p99)                                             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             │ Consumed by Workers
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       WORKER FLEET                                           │
│                                                                              │
│  ┌───────────────────┐  ┌──────────────────┐  ┌──────────────────────┐    │
│  │ SETTLEMENT        │  │  NOTIFICATION    │  │  ANALYTICS           │    │
│  │ WORKERS           │  │  WORKERS         │  │  WORKERS             │    │
│  │                   │  │                  │  │                      │    │
│  │ Count: 30         │  │  Count: 50       │  │  Count: 20           │    │
│  │ Function:         │  │  Function:       │  │  Function:           │    │
│  │ - T+2 settlement  │  │  - Push notifs   │  │  - Calculate P&L     │    │
│  │ - Fund release    │  │  - Email alerts  │  │  - Tax reports       │    │
│  │ - Batch process   │  │  - SMS           │  │  - Performance stats │    │
│  └─────────┬─────────┘  └─────────┬────────┘  └──────────┬───────────┘    │
└────────────┼──────────────────────┼──────────────────────┼─────────────────┘
             │                      │                      │
             │                      │                      │
    ┌────────┴────────┐     ┌───────┴──────┐      ┌──────┴────────┐
    │                 │     │              │      │               │
    ▼                 ▼     ▼              ▼      ▼               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STORAGE LAYER                                        │
│                                                                              │
│  ┌────────────────────┐  ┌──────────────────┐  ┌──────────────────────┐   │
│  │    POSTGRESQL      │  │   TIMESCALEDB    │  │      REDIS           │   │
│  │  (User/Trading)    │  │  (Market Data)   │  │   (Cache/Realtime)   │   │
│  │                    │  │                  │  │                      │   │
│  │  Master + 3 Reads  │  │  Time-series DB  │  │  6-node cluster      │   │
│  │  Sharded by        │  │  Optimized for   │  │  Memory: 384GB       │   │
│  │  user_id (hash)    │  │  OHLCV data      │  │                      │   │
│  │                    │  │                  │  │  Use Cases:          │   │
│  │  Tables:           │  │  Partitioned by  │  │  • User sessions     │   │
│  │  • users           │  │  time (daily)    │  │  • Stock prices      │   │
│  │  • wallets         │  │                  │  │  • Order book        │   │
│  │  • orders          │  │  Retention:      │  │  • Portfolio cache   │   │
│  │  • holdings        │  │  • 1min: 90 days │  │  • WebSocket state   │   │
│  │  • transactions    │  │  • 1day: Forever │  │                      │   │
│  │                    │  │                  │  │  Latency: 1-5ms      │   │
│  │  Capacity:         │  │  Capacity:       │  │  Hit Rate: 90%       │   │
│  │  10K writes/sec    │  │  1M writes/sec   │  │                      │   │
│  │  100K reads/sec    │  │  5M reads/sec    │  │                      │   │
│  │                    │  │                  │  │                      │   │
│  │  Latency: 10-50ms  │  │  Latency: 5-20ms │  │                      │   │
│  └────────────────────┘  └──────────────────┘  └──────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL INTEGRATIONS                                 │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐     │
│  │ STOCK EXCHANGES  │  │  MARKET DATA     │  │  PAYMENT GATEWAYS    │     │
│  │                  │  │  PROVIDERS       │  │                      │     │
│  │ • NYSE           │  │                  │  │  • Stripe            │     │
│  │ • NASDAQ         │  │  • Bloomberg     │  │  • Plaid (Banking)   │     │
│  │ • CBOE           │  │  • IEX Cloud     │  │  • ACH Network       │     │
│  │                  │  │  • Polygon.io    │  │  • Wire Transfer     │     │
│  │ Protocol:        │  │  • Alpha Vantage │  │                      │     │
│  │ FIX 4.4          │  │                  │  │  PCI DSS Compliant   │     │
│  │                  │  │  WebSocket/REST  │  │                      │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Trade Execution Flow (End-to-End)

```
┌──────────────────────────────────────────────────────────────────┐
│                     SYNCHRONOUS PATH                              │
│                  (User-Facing Response)                           │
│                     Target: <500ms                                │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   1. API Gateway    │
                    │   - Validate JWT    │
                    │   - Rate limit      │
                    │   - Check KYC       │
                    │   Time: 20ms        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  2. Trading Service │
                    │  - Validate order   │
                    │  - Check market hrs │
                    │  - Verify symbol    │
                    │  Time: 30ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  3. Wallet Service  │
                    │  - Check balance    │
                    │  - Lock funds       │
                    │  - BEGIN TX         │
                    │  Time: 50ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ 4. Insert Order DB  │
                    │ - Generate order_id │
                    │ - Status: PENDING   │
                    │ - PostgreSQL write  │
                    │ Time: 40ms          │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  5. Kafka Publish   │
                    │  - Topic: orders.new│
                    │  - Async (no wait)  │
                    │  Time: 10ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  6. Return Success  │
                    │  - Order ID         │
                    │  - Status: PENDING  │
                    │  - 201 Created      │
                    │  Total: 150ms       │
                    └─────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                    ASYNCHRONOUS PATH                              │
│              (Background Processing)                              │
│                   Target: <5 seconds                              │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  Kafka Consumer     │
                    │  Topic: orders.new  │
                    │  Partition routing  │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Order Matching      │
                    │ Engine              │
                    │ - Internal match?   │
                    │ - Route to exchange │
                    │ Time: 50ms          │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────┐
     │  Internal    │  │   NYSE       │  │  NASDAQ  │
     │  Liquidity   │  │   (FIX)      │  │  (FIX)   │
     └──────┬───────┘  └──────┬───────┘  └────┬─────┘
            │                 │               │
            └─────────────────┼───────────────┘
                              │
                              ▼ Execution Confirmation
                   ┌──────────────────────┐
                   │ 8. Update Order      │
                   │ - Status: FILLED     │
                   │ - Filled qty & price │
                   │ - Average price      │
                   │ Time: 40ms           │
                   └──────────┬───────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ 9. Settle Funds      │
                   │ - Release lock       │
                   │ - Deduct actual amt  │
                   │ - Record transaction │
                   │ Time: 50ms           │
                   └──────────┬───────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ 10. Update Portfolio │
                   │ - Add to holdings    │
                   │ - Calc avg cost      │
                   │ - Update cache       │
                   │ Time: 30ms           │
                   └──────────┬───────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ 11. Publish Events   │
                   │ - orders.filled      │
                   │ - portfolio.updated  │
                   │ Time: 10ms           │
                   └──────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
     ┌──────────────┐ ┌──────────────┐ ┌──────────┐
     │Notification  │ │  Settlement  │ │ Analytics│
     │  Service     │ │  Service     │ │ Service  │
     │              │ │              │ │          │
     │Push: "Order │ │Schedule T+2  │ │Update P&L│
     │Filled!"      │ │settlement    │ │metrics   │
     └──────────────┘ └──────────────┘ └──────────┘

Total End-to-End: 150ms (sync) + 500ms (async) = 650ms
User sees "Order Placed" in 150ms
Order filled & notified within 5 seconds
```

---

## Market Data Distribution Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    MARKET DATA INGESTION                          │
│                  Real-time Price Streaming                        │
└──────────────────────────────┬───────────────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────┐
     │   NYSE       │  │   NASDAQ     │  │ IEX Cloud│
     │   Feed       │  │   Feed       │  │   API    │
     │              │  │              │  │          │
     │ WebSocket    │  │ WebSocket    │  │  REST    │
     │ 5K stocks    │  │ 3K stocks    │  │ Real-time│
     └──────┬───────┘  └──────┬───────┘  └────┬─────┘
            │                 │               │
            └─────────────────┼───────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │  Price Aggregator    │
                   │  (Go/Rust Service)   │
                   │                      │
                   │  Functions:          │
                   │  • Parse feed        │
                   │  • Normalize data    │
                   │  • Deduplicate       │
                   │  • Enrich metadata   │
                   │                      │
                   │  Throughput:         │
                   │  1M events/sec       │
                   │  Latency: <5ms       │
                   └──────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
     ┌──────────────┐ ┌──────────────┐ ┌──────────┐
     │   Redis      │ │ TimescaleDB  │ │  Kafka   │
     │   Cache      │ │  Historical  │ │  Stream  │
     │              │ │              │ │          │
     │ KEY: price:  │ │ Store OHLCV  │ │ Topic:   │
     │ {symbol}     │ │ 1min/1day    │ │ prices.  │
     │              │ │ bars         │ │ updates  │
     │ VALUE:       │ │              │ │          │
     │ {            │ │ Partitioned  │ │ Fanout   │
     │   price,     │ │ by time      │ │ to all   │
     │   bid,       │ │              │ │ consumers│
     │   ask,       │ │ Retention:   │ │          │
     │   volume,    │ │ 10 years     │ │          │
     │   timestamp  │ │              │ │          │
     │ }            │ │              │ │          │
     │              │ │              │ │          │
     │ TTL: 5 sec   │ │              │ │          │
     │ 5K keys      │ │ 70TB data    │ │          │
     └──────┬───────┘ └──────────────┘ └────┬─────┘
            │                                │
            └────────────────┬───────────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │  WebSocket Server    │
                  │  (Node.js Cluster)   │
                  │                      │
                  │  • 20 instances      │
                  │  • 5K conn each      │
                  │  • Total: 100K conn  │
                  │                      │
                  │  Per Connection:     │
                  │  • Subscribe to      │
                  │    10-50 symbols     │
                  │  • Receive updates   │
                  │    ~1/sec per symbol │
                  │                      │
                  │  Optimizations:      │
                  │  • Binary protocol   │
                  │  • Compression       │
                  │  • Batching          │
                  │  • Throttling        │
                  └──────────┬───────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
     ┌──────────────┐ ┌──────────┐ ┌──────────┐
     │   Mobile     │ │   Web    │ │ Internal │
     │   Clients    │ │ Clients  │ │ Systems  │
     │              │ │          │ │          │
     │ 60K users    │ │ 30K users│ │ 10K      │
     └──────────────┘ └──────────┘ └──────────┘

Latency Breakdown:
┌──────────────────────────────────────────┐
│ Exchange → Aggregator:     20ms          │
│ Aggregator → Redis:        5ms           │
│ Redis → WebSocket Server:  10ms          │
│ WebSocket → Client:        30ms          │
│ ────────────────────────────────         │
│ Total End-to-End:         65ms           │
│ Target SLA:              <100ms          │
└──────────────────────────────────────────┘
```

---

## Database Schema Design

### Core Trading Tables

```sql
-- ============================================
-- USERS & AUTHENTICATION
-- ============================================

CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    date_of_birth DATE,
    
    -- KYC Status
    kyc_status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING, IN_REVIEW, VERIFIED, REJECTED, SUSPENDED
    kyc_verified_at TIMESTAMP,
    kyc_tier INTEGER DEFAULT 1, -- 1: Basic, 2: Standard, 3: Enhanced
    
    -- Security
    two_fa_enabled BOOLEAN DEFAULT FALSE,
    two_fa_secret VARCHAR(100),
    
    -- Profile
    risk_profile VARCHAR(20) DEFAULT 'MODERATE',
    -- CONSERVATIVE, MODERATE, AGGRESSIVE
    tax_id_last_4 VARCHAR(4), -- SSN/TIN last 4 digits
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP,
    
    -- Indexes
    CONSTRAINT check_kyc_status CHECK (
        kyc_status IN ('PENDING', 'IN_REVIEW', 'VERIFIED', 'REJECTED', 'SUSPENDED')
    ),
    CONSTRAINT check_risk_profile CHECK (
        risk_profile IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE')
    )
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_kyc_status ON users(kyc_status);
CREATE INDEX idx_users_created_at ON users(created_at);

-- ============================================
-- WALLETS & BALANCES
-- ============================================

CREATE TABLE wallets (
    wallet_id SERIAL PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES users(user_id),
    
    -- Balances (in USD cents to avoid floating point issues)
    balance_cents BIGINT DEFAULT 0, -- Total cash
    buying_power_cents BIGINT DEFAULT 0, -- Available to trade
    withdrawable_cash_cents BIGINT DEFAULT 0, -- Can withdraw
    
    -- Margin (if enabled)
    margin_enabled BOOLEAN DEFAULT FALSE,
    margin_limit_cents BIGINT DEFAULT 0,
    margin_used_cents BIGINT DEFAULT 0,
    
    -- Metadata
    currency VARCHAR(3) DEFAULT 'USD',
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT check_positive_balance CHECK (balance_cents >= 0),
    CONSTRAINT check_positive_buying_power CHECK (buying_power_cents >= 0),
    CONSTRAINT check_positive_withdrawable CHECK (withdrawable_cash_cents >= 0)
);

CREATE INDEX idx_wallets_user ON wallets(user_id);

-- ============================================
-- TRANSACTIONS (Financial Ledger)
-- ============================================

CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id INTEGER NOT NULL REFERENCES wallets(wallet_id),
    
    -- Transaction Details
    type VARCHAR(20) NOT NULL,
    -- DEPOSIT, WITHDRAWAL, BUY, SELL, DIVIDEND, FEE, INTEREST, REFUND
    amount_cents BIGINT NOT NULL,
    
    -- Status & Reference
    status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    reference_id VARCHAR(100), -- order_id, payment_id, etc.
    reference_type VARCHAR(50), -- ORDER, PAYMENT, DIVIDEND, etc.
    
    -- Payment Details (for deposits/withdrawals)
    payment_method VARCHAR(50),
    -- UPI, ACH, WIRE, DEBIT_CARD, BANK_TRANSFER
    payment_gateway_id VARCHAR(100),
    
    -- Description
    description TEXT,
    metadata JSONB, -- Additional flexible data
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP,
    
    CONSTRAINT check_transaction_type CHECK (
        type IN ('DEPOSIT', 'WITHDRAWAL', 'BUY', 'SELL', 'DIVIDEND', 
                'FEE', 'INTEREST', 'REFUND')
    ),
    CONSTRAINT check_transaction_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX idx_transactions_wallet ON transactions(wallet_id, created_at DESC);
CREATE INDEX idx_transactions_status ON transactions(status) WHERE status = 'PENDING';
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_reference ON transactions(reference_id);

-- ============================================
-- ORDERS
-- ============================================

CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id),
    
    -- Order Details
    stock_symbol VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    -- MARKET, LIMIT, STOP_LOSS, STOP_LIMIT
    side VARCHAR(4) NOT NULL, -- BUY, SELL
    
    -- Quantity & Price (stored as rational numbers to avoid float issues)
    quantity_whole INTEGER NOT NULL,
    quantity_fractional INTEGER DEFAULT 0, -- For fractional shares
    price_cents BIGINT, -- NULL for market orders
    stop_price_cents BIGINT, -- For stop orders
    
    -- Execution Status
    status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING, PARTIAL, FILLED, CANCELLED, REJECTED, EXPIRED
    filled_quantity_whole INTEGER DEFAULT 0,
    filled_quantity_fractional INTEGER DEFAULT 0,
    average_price_cents BIGINT,
    
    -- Fees & Costs
    commission_cents BIGINT DEFAULT 0,
    sec_fee_cents BIGINT DEFAULT 0, -- SEC regulatory fee
    total_cost_cents BIGINT, -- Total amount deducted
    
    -- Time in Force
    time_in_force VARCHAR(10) DEFAULT 'DAY',
    -- DAY, GTC (Good Till Cancel), IOC (Immediate or Cancel), FOK (Fill or Kill)
    expires_at TIMESTAMP,
    
    -- External References
    exchange_order_id VARCHAR(100), -- Exchange's order ID
    execution_venue VARCHAR(50), -- NYSE, NASDAQ, INTERNAL, etc.
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    filled_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    
    CONSTRAINT check_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT check_status CHECK (
        status IN ('PENDING', 'PARTIAL', 'FILLED', 'CANCELLED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT check_positive_quantity CHECK (quantity_whole > 0)
);

-- Partitioning by month for orders table
CREATE TABLE orders_2025_01 PARTITION OF orders
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_symbol ON orders(stock_symbol);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_pending ON orders(user_id, status) WHERE status IN ('PENDING', 'PARTIAL');

-- ============================================
-- HOLDINGS (Portfolio)
-- ============================================

CREATE TABLE holdings (
    holding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id),
    stock_symbol VARCHAR(10) NOT NULL,
    
    -- Quantity
    quantity_whole INTEGER NOT NULL,
    quantity_fractional INTEGER DEFAULT 0,
    
    -- Cost Basis
    average_cost_cents BIGINT NOT NULL,
    total_cost_cents BIGINT NOT NULL,
    
    -- P&L
    realized_pnl_cents BIGINT DEFAULT 0, -- From previous sells
    
    -- Timestamps
    first_acquired_at TIMESTAMP,
    last_updated TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(user_id, stock_symbol),
    CONSTRAINT check_positive_quantity CHECK (
        quantity_whole > 0 OR 
        (quantity_whole = 0 AND quantity_fractional > 0)
    )
);

CREATE INDEX idx_holdings_user ON holdings(user_id);
CREATE INDEX idx_holdings_symbol ON holdings(stock_symbol);

-- ============================================
-- MARKET DATA (TimescaleDB)
-- ============================================

CREATE TABLE stock_prices (
    symbol VARCHAR(10) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    
    -- OHLCV Data
    open_cents BIGINT NOT NULL,
    high_cents BIGINT NOT NULL,
    low_cents BIGINT NOT NULL,
    close_cents BIGINT NOT NULL,
    volume BIGINT NOT NULL,
    
    -- Additional Market Data
    bid_cents BIGINT,
    ask_cents BIGINT,
    bid_size INTEGER,
    ask_size INTEGER,
    
    PRIMARY KEY (symbol, timestamp)
);

-- Convert to TimescaleDB hypertable
SELECT create_hypertable('stock_prices', 'timestamp');

-- Create continuous aggregates for different timeframes
CREATE MATERIALIZED VIEW stock_prices_1hour
WITH (timescaledb.continuous) AS
SELECT 
    symbol,
    time_bucket('1 hour', timestamp) AS hour,
    first(open_cents, timestamp) AS open_cents,
    max(high_cents) AS high_cents,
    min(low_cents) AS low_cents,
    last(close_cents, timestamp) AS close_cents,
    sum(volume) AS volume
FROM stock_prices
GROUP BY symbol, hour;

CREATE MATERIALIZED VIEW stock_prices_1day
WITH (timescaledb.continuous) AS
SELECT 
    symbol,
    time_bucket('1 day', timestamp) AS day,
    first(open_cents, timestamp) AS open_cents,
    max(high_cents) AS high_cents,
    min(low_cents) AS low_cents,
    last(close_cents, timestamp) AS close_cents,
    sum(volume) AS volume
FROM stock_prices
GROUP BY symbol, day;

-- Retention policy: keep 1-minute data for 90 days
SELECT add_retention_policy('stock_prices', INTERVAL '90 days');
```

---

## API Design

### Authentication Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION FLOW                            │
└──────────────────────────────────────────────────────────────────┘

Registration:
1. POST /api/v1/auth/register
   {
     "email": "user@example.com",
     "password": "SecureP@ss123",
     "phone": "+1234567890",
     "full_name": "John Doe"
   }
   
2. Verify Email → Send 6-digit OTP
3. POST /api/v1/auth/verify-email
   { "otp": "123456" }
   
4. Response: 
   {
     "user_id": "usr_abc123",
     "kyc_status": "PENDING"
   }

Login:
1. POST /api/v1/auth/login
   {
     "email": "user@example.com",
     "password": "SecureP@ss123"
   }
   
2. If 2FA enabled → Request OTP
   POST /api/v1/auth/verify-2fa
   { "otp": "123456" }
   
3. Response:
   {
     "access_token": "eyJhbGc...",  // 1 hour expiry
     "refresh_token": "abc123...",  // 7 days expiry
     "user": {
       "user_id": "usr_abc123",
       "email": "user@example.com",
       "kyc_verified": true
     }
   }

Token Refresh:
POST /api/v1/auth/refresh
Authorization: Bearer <refresh_token>
→ Returns new access_token
```

### Core API Endpoints

```
┌──────────────────────────────────────────────────────────────────┐
│                        TRADING APIs                               │
└──────────────────────────────────────────────────────────────────┘

Place Order:
POST /api/v1/orders
Authorization: Bearer <token>
{
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": "10.5",  // Fractional shares supported
  "price": "175.50",
  "time_in_force": "DAY"
}

Response (201 Created):
{
  "order_id": "ord_abc123",
  "status": "PENDING",
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": "10.5",
  "price": "175.50",
  "estimated_cost": "$1842.75",
  "created_at": "2025-01-10T14:30:00Z"
}

Get Order Status:
GET /api/v1/orders/{order_id}
→ Returns current order status with execution details

Cancel Order:
DELETE /api/v1/orders/{order_id}
→ Attempts to cancel if not yet filled

List Orders:
GET /api/v1/orders?status=PENDING&limit=50&page=1
→ Paginated list of user's orders

┌──────────────────────────────────────────────────────────────────┐
│                        PORTFOLIO APIs                             │
└──────────────────────────────────────────────────────────────────┘

Get Portfolio:
GET /api/v1/portfolio

Response:
{
  "total_value": "$125,430.50",
  "cash_balance": "$5,430.50",
  "buying_power": "$5,430.50",
  "day_pnl": "$2,150.30",
  "day_pnl_percentage": "1.74%",
  "total_pnl": "$25,430.50",
  "total_pnl_percentage": "25.43%",
  "holdings": [
    {
      "symbol": "AAPL",
      "quantity": "100.5",
      "average_cost": "$150.00",
      "current_price": "$178.50",
      "current_value": "$17,939.25",
      "unrealized_pnl": "$2,864.25",
      "unrealized_pnl_percentage": "19.03%",
      "day_change": "$358.75"
    }
  ]
}

Get Holdings:
GET /api/v1/portfolio/holdings
→ Detailed list of all stock positions

Get Performance:
GET /api/v1/portfolio/performance?period=1Y
→ Historical performance metrics

┌──────────────────────────────────────────────────────────────────┐
│                        WALLET APIs                                │
└──────────────────────────────────────────────────────────────────┘

Get Balance:
GET /api/v1/wallet/balance

Response:
{
  "balance": "$10,500.00",
  "buying_power": "$10,500.00",
  "withdrawable_cash": "$8,300.00",  // Excludes unsettled funds
  "margin_enabled": false
}

Deposit Money:
POST /api/v1/wallet/deposit
{
  "amount": "5000.00",
  "payment_method": "ACH",
  "bank_account_id": "bank_abc123"
}

Response:
{
  "transaction_id": "txn_xyz789",
  "status": "PENDING",
  "amount": "$5,000.00",
  "estimated_arrival": "2025-01-12T00:00:00Z",  // 2 business days
  "instant_deposit": false
}

Withdraw Money:
POST /api/v1/wallet/withdraw
{
  "amount": "1000.00",
  "bank_account_id": "bank_abc123"
}

Get Transactions:
GET /api/v1/wallet/transactions?type=DEPOSIT&limit=50
→ Paginated transaction history

┌──────────────────────────────────────────────────────────────────┐
│                        MARKET DATA APIs                           │
└──────────────────────────────────────────────────────────────────┘

Get Stock Quote:
GET /api/v1/stocks/AAPL/quote

Response:
{
  "symbol": "AAPL",
  "price": "$178.50",
  "change": "$2.30",
  "change_percentage": "1.31%",
  "volume": "45,623,190",
  "bid": "$178.48",
  "ask": "$178.52",
  "day_high": "$179.10",
  "day_low": "$175.80",
  "market_cap": "$2.8T",
  "pe_ratio": "29.5",
  "timestamp": "2025-01-10T14:35:00Z"
}

Get Price History:
GET /api/v1/stocks/AAPL/chart?interval=1d&range=1y
→ OHLCV data for charting

Search Stocks:
GET /api/v1/stocks/search?q=apple
→ Search by company name or symbol

Get Top Movers:
GET /api/v1/market/top-gainers?limit=10
GET /api/v1/market/top-losers?limit=10
```

### WebSocket API

```javascript
// Connect to WebSocket
const ws = new WebSocket('wss://api.robinhood.com/v1/ws');

// Authenticate
ws.send(JSON.stringify({
  type: 'auth',
  token: 'eyJhbGc...'
}));

// Subscribe to real-time quotes
ws.send(JSON.stringify({
  type: 'subscribe',
  channel: 'quotes',
  symbols: ['AAPL', 'GOOGL', 'TSLA']
}));

// Subscribe to order updates
ws.send(JSON.stringify({
  type: 'subscribe',
  channel: 'orders'
}));

// Receive price updates
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  if (message.channel === 'quotes') {
    // {
    //   channel: 'quotes',
    //   symbol: 'AAPL',
    //   price: 178.50,
    //   change: 2.30,
    //   timestamp: 1704723845000
    // }
    updateStockPrice(message);
  }
  
  if (message.channel === 'orders') {
    // {
    //   channel: 'orders',
    //   order_id: 'ord_abc123',
    //   status: 'FILLED',
    //   filled_quantity: 10,
    //   average_price: 175.45
    // }
    updateOrderStatus(message);
  }
};
```

---

## Security & Compliance

### Multi-Layer Security Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    SECURITY LAYERS                               │
└─────────────────────────────────────────────────────────────────┘

Layer 1: Network Security
├─ DDoS Protection (AWS Shield, Cloudflare)
├─ WAF Rules (Block SQL injection, XSS)
├─ Rate Limiting (API Gateway)
└─ Geo-blocking (Restrict by country)

Layer 2: Authentication & Authorization
├─ JWT Tokens (1-hour expiry)
├─ Refresh Tokens (7-day expiry)
├─ 2FA (TOTP/SMS)
├─ Biometric (Face ID/Fingerprint)
├─ Device Fingerprinting
└─ Session Management

Layer 3: Data Encryption
├─ TLS 1.3 (In Transit)
├─ AES-256 (At Rest)
├─ Field-Level Encryption (PII)
├─ Key Management (AWS KMS)
└─ Certificate Pinning (Mobile)

Layer 4: Application Security
├─ Input Validation
├─ SQL Injection Prevention (Parameterized Queries)
├─ CSRF Protection
├─ CORS Policy
└─ Content Security Policy (CSP)

Layer 5: Compliance & Audit
├─ SOC 2 Type II
├─ PCI DSS (Payment Card Industry)
├─ SEC Regulations
├─ KYC/AML Compliance
├─ Audit Logging (CloudTrail)
└─ Data Retention Policies
```

### KYC Verification Process

```
┌──────────────────────────────────────────────────────────────────┐
│                    KYC VERIFICATION FLOW                          │
└──────────────────────────────────────────────────────────────────┘

Tier 1 (Basic - $1K/day limit):
1. Email + Phone Verification
2. Basic Personal Info (Name, DOB, Address)
3. SSN/TIN
4. Auto-approved if no red flags

Tier 2 (Standard - $50K/day limit):
1. Government ID Upload (Driver's License/Passport)
2. Selfie Verification (Liveness check)
3. Address Proof (Utility Bill)
4. Manual Review (if needed)
5. Typically approved in 24-48 hours

Tier 3 (Enhanced - Unlimited):
1. Income Verification (Tax Returns, Pay Stubs)
2. Source of Funds Declaration
3. Enhanced Due Diligence
4. Manual Review Required
5. Typically approved in 3-5 business days

Ongoing Monitoring:
- Transaction Monitoring (AML)
- Suspicious Activity Detection
- Periodic Re-verification
- Watch List Screening (OFAC)
```

### Data Privacy & Protection

```sql
-- Encrypt Sensitive Fields
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Store encrypted SSN
INSERT INTO users (ssn_encrypted) 
VALUES (pgp_sym_encrypt('123-45-6789', 'encryption_key'));

-- Retrieve and decrypt
SELECT pgp_sym_decrypt(ssn_encrypted, 'encryption_key') 
FROM users WHERE user_id = ?;

-- Mask PII when displaying
CREATE FUNCTION mask_ssn(ssn TEXT) RETURNS TEXT AS $$
BEGIN
    RETURN '***-**-' || RIGHT(ssn, 4);
END;
$$ LANGUAGE plpgsql;

-- Usage: SELECT mask_ssn('123-45-6789') → '***-**-6789'
```

---

## Scalability Strategies

### Horizontal Scaling

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUTO-SCALING CONFIGURATION                    │
└─────────────────────────────────────────────────────────────────┘

Microservices:
├─ API Gateway: 4-20 instances
│  ├─ Scale on: CPU > 70%, Request Queue > 100
│  └─ Target: < 100ms response time
│
├─ Trading Service: 10-50 instances
│  ├─ Scale on: Order rate, Latency
│  └─ Target: < 500ms order placement
│
├─ Market Data Service: 20-100 instances
│  ├─ Scale on: WebSocket connections
│  └─ Target: 5K connections per instance
│
└─ Portfolio Service: 5-20 instances
   ├─ Scale on: Portfolio calculation requests
   └─ Target: < 300ms calculation time

Database:
├─ Read Replicas: 3-10 (auto-scale on read load)
├─ Connection Pooling: PgBouncer (transaction mode)
└─ Query Optimization: Materialized views, indexes

Cache:
├─ Redis Cluster: 6-20 nodes
├─ Memory: 64GB-1TB total
└─ Eviction: LRU policy

Message Queue:
├─ Kafka Brokers: 12-30
├─ Partitions: Auto-rebalance
└─ Consumer Groups: Dynamic scaling
```

### Caching Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                        CACHING LAYERS                            │
└─────────────────────────────────────────────────────────────────┘

Layer 1: CDN (CloudFront)
├─ Static Assets (JS, CSS, Images)
├─ TTL: 7 days
└─ Invalidation: On deployment

Layer 2: API Gateway Cache
├─ GET Responses
├─ TTL: 1-5 minutes
└─ Cache-Control headers

Layer 3: Application Cache (Redis)
├─ User Sessions: 30 min TTL
├─ Stock Prices: 5 sec TTL
├─ Portfolio Data: 5 min TTL
├─ Order Book: Real-time (no TTL)
└─ User Preferences: 1 hour TTL

Layer 4: Database Query Cache
├─ PostgreSQL: shared_buffers (128GB)
├─ TimescaleDB: Continuous aggregates
└─ Query Result Cache: 5 min TTL

Cache Invalidation Strategy:
├─ Write-Through: Update DB + Cache simultaneously
├─ Write-Behind: Update cache first, async to DB
├─ Cache-Aside: Load from DB on cache miss
└─ TTL-Based: Automatic expiration
```

---

## Monitoring & Observability

### Monitoring Dashboard

```
┌─────────────────────────────────────────────────────────────────┐
│                    GRAFANA DASHBOARD                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │  Order Latency   │  │  Order Volume    │                    │
│  │  p50: 120ms      │  │  Current: 850/s  │                    │
│  │  p99: 450ms ✓    │  │  Peak: 2.1K/s    │                    │
│  │  [Line Graph]    │  │  [Area Chart]    │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │  Cache Hit Rate  │  │  Error Rate      │                    │
│  │  Portfolio: 92%  │  │  API: 0.02% ✓    │                    │
│  │  Prices: 88%     │  │  Orders: 0.01%   │                    │
│  │  [Gauge]         │  │  [Line Graph]    │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │  WebSocket Conn  │  │  Database Perf   │                    │
│  │  Active: 95K/100K│  │  Postgres: 25ms  │                    │
│  │  [Bar Chart]     │  │  TimescaleDB: 12ms│                   │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │  Kafka Lag       │  │  System Health   │                    │
│  │  orders.new: 120 │  │  All: ✓ Healthy  │                    │
│  │  [Gauge]         │  │  [Status Grid]   │                    │
│  └──────────────────┘  └──────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

### Alerting Rules

```yaml
alerts:
  critical:
    - name: HighOrderLatency
      condition: p99_latency > 1000ms for 5 minutes
      action: page_oncall
      
    - name: OrderFailureRate
      condition: error_rate > 1% for 2 minutes
      action: page_oncall + auto_rollback
      
    - name: DatabaseDown
      condition: health_check_failed
      action: page_oncall + failover
      
    - name: LowCashBalance
      condition: hot_wallet < $1M
      action: alert_finance_team

  warning:
    - name: HighDatabaseCPU
      condition: cpu > 80% for 10 minutes
      action: notify_slack + auto_scale
      
    - name: CacheHitRateLow
      condition: hit_rate < 70%
      action: notify_slack + investigate
      
    - name: KafkaLagHigh
      condition: lag > 1000 for 5 minutes
      action: notify_slack + scale_consumers
```

---

## Cost Estimation

```
┌─────────────────────────────────────────────────────────────────┐
│                    MONTHLY COST BREAKDOWN                        │
│                     (1M DAU, 5M trades/day)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Compute (EC2/ECS):                                              │
│  ├─ API Gateway: 20 × m5.xlarge ............. $2,880            │
│  ├─ Trading Service: 30 × m5.2xlarge ........ $8,640            │
│  ├─ Market Data: 50 × c5.xlarge ............. $6,120            │
│  ├─ Workers: 100 × t3.large ................. $7,440            │
│  └─ WebSocket: 20 × c5.2xlarge .............. $6,120            │
│                                            Subtotal: $31,200     │
│                                                                  │
│  Database:                                                       │
│  ├─ PostgreSQL RDS: db.r5.4xlarge (Master) .. $5,500           │
│  ├─ Read Replicas: 3 × db.r5.2xlarge ........ $8,250           │
│  ├─ TimescaleDB: 2 × db.r5.4xlarge .......... $11,000          │
│  └─ Backup & Snapshots ....................... $2,000           │
│                                            Subtotal: $26,750     │
│                                                                  │
│  Cache (Redis):                                                  │
│  ├─ ElastiCache: 6 × cache.r5.2xlarge ....... $4,320           │
│  └─ Data Transfer ............................ $500             │
│                                            Subtotal: $4,820      │
│                                                                  │
│  Message Queue (Kafka):                                          │
│  ├─ MSK Cluster: 12 brokers ................. $8,640           │
│  └─ Storage (10TB) ........................... $2,000           │
│                                            Subtotal: $10,640     │
│                                                                  │
│  CDN & Networking:                                               │
│  ├─ CloudFront ............................... $3,000           │
│  ├─ Load Balancers ........................... $500             │
│  └─ Data Transfer (Outbound) ................ $5,000           │
│                                            Subtotal: $8,500      │
│                                                                  │
│  Storage:                                                        │
│  ├─ S3 (Documents, Backups) .................. $1,500           │
│  ├─ EBS Volumes .............................. $3,000           │
│  └─ Glacier (Long-term Archive) .............. $500             │
│                                            Subtotal: $5,000      │
│                                                                  │
│  External Services:                                              │
│  ├─ Market Data Feeds ........................ $15,000          │
│  ├─ Payment Gateway (Stripe/Plaid) .......... $5,000           │
│  ├─ KYC/AML Service .......................... $3,000           │
│  └─ SMS/Email (Notifications) ................ $1,000           │
│                                            Subtotal: $24,000     │
│                                                                  │
│  Monitoring & Security:                                          │
│  ├─ DataDog/New Relic ........................ $2,000           │
│  ├─ AWS CloudWatch ........................... $1,000           │
│  ├─ Security Tools (GuardDuty, etc.) ........ $1,500           │
│  └─ WAF & DDoS Protection .................... $2,000           │
│                                            Subtotal: $6,500      │
│                                                                  │
│  ════════════════════════════════════════════════════            │
│  TOTAL MONTHLY COST:                         $117,410           │
│  ════════════════════════════════════════════════════            │
│                                                                  │
│  Per User (1M DAU): .......................... $0.117/day       │
│  Per Trade (5M/day): ......................... $0.023/trade     │
│  Annual Cost: ................................ $1.4M/year        │
│                                                                  │
│  Revenue Model to Break Even:                                    │
│  ├─ Commission: $0.02/trade × 5M = $100K/day                    │
│  ├─ Premium Subscription: 10K users × $5 = $50K/month           │
│  ├─ Interest on Cash: 100M × 0.5% APY = $500K/year             │
│  └─ Payment for Order Flow (PFOF): $15K/day                     │
│                                                                  │
│  Monthly Revenue Target: .................... $3.2M             │
│  Break-even Point: .......................... ~400K DAU          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions & Trade-offs

```
┌─────────────────────────────────────────────────────────────────┐
│                    DESIGN DECISIONS                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Decision 1: Eventual Consistency for Market Data               │
│  ├─ Trade-off: Strong consistency ↔ Low latency                 │
│  ├─ Choice: Eventual (65ms latency acceptable)                  │
│  └─ Reason: Real-time pricing more important than perfect sync  │
│                                                                  │
│  Decision 2: Hybrid Order Execution (Internal + Exchange)       │
│  ├─ Trade-off: Simplicity ↔ Cost savings                        │
│  ├─ Choice: Hybrid approach                                     │
│  └─ Reason: Reduce exchange fees, improve execution speed       │
│                                                                  │
│  Decision 3: Async Order Processing with Kafka                  │
│  ├─ Trade-off: Immediate consistency ↔ Scalability              │
│  ├─ Choice: Async (user sees "Order Placed" in 150ms)           │
│  └─ Reason: Handle peak load (10K orders/sec) without blocking  │
│                                                                  │
│  Decision 4: Fractional Shares Support                          │
│  ├─ Trade-off: Complexity ↔ User accessibility                  │
│  ├─ Choice: Support fractional shares                           │
│  └─ Reason: Lower barrier to entry, compete with Robinhood      │
│                                                                  │
│  Decision 5: Multi-Database Strategy                            │
│  ├─ Trade-off: Operational complexity ↔ Performance             │
│  ├─ Choice: PostgreSQL + TimescaleDB + Redis                    │
│  └─ Reason: Right tool for each data type                       │
│                                                                  │
│  Decision 6: T+2 Settlement (Not Instant)                       │
│  ├─ Trade-off: User experience ↔ Regulatory compliance          │
│  ├─ Choice: Follow standard T+2 settlement                      │
│  └─ Reason: Legal requirement, manage risk                      │
│                                                                  │
│  Decision 7: KYC Tiered System                                  │
│  ├─ Trade-off: Friction ↔ Compliance                            │
│  ├─ Choice: 3-tier system with progressive limits               │
│  └─ Reason: Balance user onboarding with AML requirements       │
│                                                                  │
│  Decision 8: WebSocket for Real-time Data                       │
│  ├─ Trade-off: Server resources ↔ User experience               │
│  ├─ Choice: WebSocket (vs polling)                              │
│  └─ Reason: Efficient real-time updates, lower latency          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Disaster Recovery & Business Continuity

### Backup Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                        BACKUP & RECOVERY                         │
└─────────────────────────────────────────────────────────────────┘

Database Backups:
├─ PostgreSQL
│  ├─ Full Backup: Daily at 2 AM ET
│  ├─ Incremental: Every 4 hours
│  ├─ WAL (Write-Ahead Log): Continuous archival
│  ├─ Retention: 90 days online, 7 years S3 Glacier
│  └─ Recovery Time: 1-2 hours
│
├─ TimescaleDB
│  ├─ Continuous Aggregate Refresh: Hourly
│  ├─ Full Backup: Daily
│  ├─ Retention: 1 year for 1-min data, forever for daily
│  └─ Recovery Time: 30 minutes
│
└─ Redis
   ├─ RDB Snapshots: Every 1 hour
   ├─ AOF (Append-Only File): Enabled
   └─ Recovery Time: 10 minutes

Multi-Region Failover:
┌────────────────────────────────────────────────┐
│ Primary Region: US-EAST-1                      │
│ ├─ Handles: 70% traffic                        │
│ ├─ Master DB: Active                           │
│ └─ Status: ✓ Healthy                           │
│                                                 │
│ Secondary Region: US-WEST-2                    │
│ ├─ Handles: 30% traffic                        │
│ ├─ Read Replica: Standby                       │
│ └─ Status: ✓ Healthy                           │
│                                                 │
│ Failover Process:                               │
│ 1. Health check fails (30 sec timeout)         │
│ 2. DNS switches via Route53 (60 sec)           │
│ 3. Promote read replica to master (90 sec)     │
│ 4. Resume trading operations                   │
│ Total Downtime: ~3 minutes                     │
└────────────────────────────────────────────────┘

Recovery Point Objective (RPO): 5 minutes
Recovery Time Objective (RTO): 30 minutes
```

---

## Performance Benchmarks

```
┌─────────────────────────────────────────────────────────────────┐
│                    PERFORMANCE METRICS                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Latency (p50/p95/p99):                                         │
│  ├─ Order Placement:    120ms / 350ms / 450ms                   │
│  ├─ Portfolio Load:     80ms / 180ms / 250ms                    │
│  ├─ Market Data Feed:   30ms / 60ms / 85ms                      │
│  ├─ Order Cancellation: 90ms / 200ms / 300ms                    │
│  └─ Account Balance:    25ms / 50ms / 75ms                      │
│                                                                  │
│  Throughput:                                                     │
│  ├─ Orders: 10,000/sec (peak 20,000/sec)                        │
│  ├─ Price Updates: 1M events/sec                                │
│  ├─ API Requests: 50K/sec sustained                             │
│  └─ WebSocket Messages: 500K/sec                                │
│                                                                  │
│  Availability:                                                   │
│  ├─ System Uptime: 99.99% (52 min downtime/year)                │
│  ├─ Trading Hours: 99.999% (5 min downtime/year)                │
│  └─ Market Data: 99.95% (acceptable 100ms lag)                  │
│                                                                  │
│  Cache Performance:                                              │
│  ├─ Portfolio Cache Hit: 92%                                    │
│  ├─ Stock Price Cache Hit: 88%                                  │
│  ├─ User Session Hit: 95%                                       │
│  └─ Cache Latency: 1-5ms                                        │
│                                                                  │
│  Database Performance:                                           │
│  ├─ PostgreSQL Write: 10K TPS                                   │
│  ├─ PostgreSQL Read: 100K QPS                                   │
│  ├─ TimescaleDB Write: 1M points/sec                            │
│  └─ Query Latency: 10-50ms (p95)                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## Summary & Final Architecture Diagram

### System Capacity

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTEM CAPACITY SUMMARY                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Users:                                                          │
│  ├─ Registered Users: 10M                                       │
│  ├─ Daily Active Users: 1M                                      │
│  ├─ Peak Concurrent Users: 200K                                 │
│  └─ WebSocket Connections: 100K                                 │
│                                                                  │
│  Trading:                                                        │
│  ├─ Daily Trades: 5M                                            │
│  ├─ Peak Orders/Second: 10K                                     │
│  ├─ Average Order Latency: 120ms                                │
│  └─ Order Fill Rate: 99.9%                                      │
│                                                                  │
│  Market Data:                                                    │
│  ├─ Tracked Stocks: 8,000                                       │
│  ├─ Price Updates/Sec: 1M                                       │
│  ├─ Historical Data: 70TB                                       │
│  └─ Real-time Latency: 65ms                                     │
│                                                                  │
│  Financial:                                                      │
│  ├─ Assets Under Management: $50B                               │
│  ├─ Daily Trading Volume: $2B                                   │
│  ├─ Average Portfolio: $50K                                     │
│  └─ Monthly Revenue: $3.2M                                      │
└─────────────────────────────────────────────────────────────────┘
```

### Technology Stack Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                    TECHNOLOGY STACK                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Frontend:                                                       │
│  ├─ iOS: Swift, SwiftUI                                         │
│  ├─ Android: Kotlin, Jetpack Compose                            │
│  └─ Web: React, TypeScript, WebSocket                           │
│                                                                  │
│  Backend:                                                        │
│  ├─ API Gateway: Node.js, Kong                                  │
│  ├─ Microservices: Go, Java Spring Boot                         │
│  ├─ Order Matching: C++, Rust (low latency)                     │
│  └─ Workers: Python, Java                                       │
│                                                                  │
│  Databases:                                                      │
│  ├─ OLTP: PostgreSQL 15                                         │
│  ├─ Time-Series: TimescaleDB                                    │
│  ├─ Cache: Redis 7.x (Cluster mode)                             │
│  └─ Search: Elasticsearch 8.x                                   │
│                                                                  │
│  Message Queue:                                                  │
│  └─ Apache Kafka 3.x (12 brokers)                               │
│                                                                  │
│  Infrastructure:                                                 │
│  ├─ Cloud: AWS (multi-region)                                   │
│  ├─ Containers: Docker, ECS                                     │
│  ├─ Orchestration: ECS, Auto Scaling Groups                     │
│  └─ Load Balancing: ALB, NLB                                    │
│                                                                  │
│  External APIs:                                                  │
│  ├─ Market Data: IEX Cloud, Polygon.io                          │
│  ├─ Payments: Stripe, Plaid                                     │
│  ├─ KYC/AML: Jumio, Onfido                                      │
│  └─ Exchange: FIX 4.4 protocol                                  │
│                                                                  │
│  Monitoring:                                                     │
│  ├─ Metrics: DataDog, CloudWatch                                │
│  ├─ Logs: ELK Stack                                             │
│  ├─ Tracing: Jaeger                                             │
│  └─ Alerting: PagerDuty                                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Conclusion

This Robinhood-style trading platform architecture is designed to handle:

✅ **1M daily active users** with 99.99% uptime  
✅ **5M trades per day** with <500ms order placement  
✅ **Real-time market data** streaming to 100K concurrent users  
✅ **Fractional shares** for democratized investing  
✅ **T+2 settlement** with proper fund management  
✅ **Multi-tier KYC** for compliance and user onboarding  
✅ **Strong security** with encryption, 2FA, and audit logging  
✅ **Horizontal scalability** with microservices and Kafka  
✅ **Cost-effective** at ~$117K/month for 1M DAU  

### Key Highlights

1. **Low Latency Trading**: 150ms order placement (user-facing), sub-second execution
2. **Real-time Data**: 65ms end-to-end latency for price updates
3. **Hybrid Execution**: Internal matching + exchange routing for best execution
4. **Financial Compliance**: SEC/FINRA regulations, KYC/AML, SOC 2 certified
5. **Disaster Recovery**: Multi-region with 3-minute failover
6. **Observability**: Comprehensive monitoring with alerting and auto-scaling

### Future Enhancements

- Options and derivatives trading
- Cryptocurrency support
- Margin trading with risk management
- Social features (copy trading, leaderboards)
- Robo-advisor for automated investing
- International expansion (EU, APAC markets)
- Machine learning for fraud detection and personalization

---

**Document Version**: 1.0  
**Created**: January 2025  
**System**: Robinhood Stock Trading Platform - Complete Architecture  
**Scale**: 1M DAU, 5M trades/day, 100K WebSocket connections  
**Availability**: 99.99% uptime, <500ms order latency
