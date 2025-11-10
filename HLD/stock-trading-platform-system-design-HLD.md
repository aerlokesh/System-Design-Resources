# Stock Trading Platform System Design - High-Level Design (HLD)
## (Robinhood/Groww Style)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [API Design](#api-design)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Security & Compliance](#security--compliance)
12. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a stock trading platform like Robinhood or Groww that allows users to:
- Buy and sell stocks, ETFs, and mutual funds
- View real-time market data and charts
- Manage their portfolio and track P&L
- Add funds and withdraw money
- Set price alerts and notifications
- Access market research and news
- Trade options and derivatives (advanced)
- View transaction history and tax reports

### Scale Requirements
- **10 million registered users**
- **1 million daily active users (DAU)**
- **5 million trades per day**
- **Peak trading hours**: Market open/close (9:30 AM - 4:00 PM EST)
- **100,000 concurrent websocket connections**
- **Real-time data**: < 100ms latency for market data
- **Order execution**: < 500ms end-to-end

---

## Functional Requirements

### Must Have (P0)

#### 1. User Management
- User registration with KYC verification
- Email/Phone/Biometric authentication
- Profile management
- Bank account linking
- Tax information (SSN/PAN)
- Risk profile assessment

#### 2. Wallet & Payments
- Add money (UPI/Bank Transfer/Debit Card)
- Withdraw money to linked bank account
- Maintain buying power
- Instant settlements (for some trades)
- Transaction history

#### 3. Market Data
- Real-time stock quotes (price, bid/ask, volume)
- Intraday charts (1m, 5m, 15m, 1h, 1d)
- Historical price data
- Market depth (Level 2 data)
- Top gainers/losers
- Market indices (S&P 500, NASDAQ, NSE, BSE)

#### 4. Trading
- Market orders (buy/sell at current price)
- Limit orders (buy/sell at specific price)
- Stop-loss orders
- Order book management (pending, executed, cancelled)
- Fractional shares (buy 0.5 shares of AAPL)
- Order validation (sufficient funds, market hours)

#### 5. Portfolio Management
- View holdings (stocks, ETFs, mutual funds)
- Real-time portfolio value
- Day P&L and total P&L
- Cost basis and returns calculation
- Dividend tracking
- Portfolio performance charts

#### 6. Watchlist
- Add stocks to watchlist
- Price alerts (notify when price reaches threshold)
- Custom watchlists (Tech, Healthcare, etc.)

#### 7. Research & Discovery
- Company fundamentals (P/E, Market Cap, EPS)
- Analyst ratings and price targets
- News feed from financial sources
- Earnings calendar
- IPO listings

### Nice to Have (P1)
- Options and derivatives trading
- Crypto trading
- Mutual fund SIPs (Systematic Investment Plans)
- Margin trading (borrow to trade)
- After-hours trading
- Social features (see what others are buying)
- Paper trading (practice with virtual money)
- Tax-loss harvesting
- Automated investing (robo-advisor)

---

## Non-Functional Requirements

### Performance
- **Order placement**: < 500ms end-to-end
- **Market data updates**: < 100ms latency
- **Portfolio load**: < 300ms
- **Search latency**: < 200ms
- **API response time**: < 200ms for p99

### Scalability
- Handle 1M DAU
- Support 5M trades/day
- Process 10K trades/second during peak
- 100K concurrent WebSocket connections
- 1M+ price updates per second

### Availability
- **99.99% uptime** (52 minutes downtime/year)
- Zero downtime during market hours
- Graceful degradation (market data may lag, but trading must work)
- Multi-region deployment

### Consistency
- **Strong consistency** for financial transactions (ACID)
- **Eventual consistency** for market data (acceptable 100ms lag)
- **Sequential consistency** for order execution (FIFO per user)

### Reliability
- No duplicate orders
- No lost orders
- Guaranteed order execution notification
- Idempotent API calls

### Security
- **PCI DSS compliance** for payment data
- **SOC 2 Type II** certification
- **Encryption**: TLS 1.3 in transit, AES-256 at rest
- **2FA/Biometric** authentication
- **Audit logging** for all financial transactions
- **DDoS protection**

### Compliance
- **KYC/AML** (Know Your Customer, Anti-Money Laundering)
- **SEC regulations** (US) or **SEBI** (India)
- **Pattern Day Trading** rules enforcement
- **Tax reporting** (1099 forms, Capital Gains)
- **Trade surveillance** (detect market manipulation)

---

## Capacity Estimation

### Traffic Estimates
```
Daily Active Users: 1M
Trades per day: 5M
API calls per user: 50/day

Total API calls/day: 1M × 50 = 50M
API calls/second (average): 50M / 86400 ≈ 580 QPS
Peak QPS (market hours, 3x average): ~1,750 QPS

Order placement:
  5M trades/day
  Concentrated in 6.5 hours (market hours)
  Average: 5M / 23400 seconds ≈ 214 orders/sec
  Peak (first 30 min, last 30 min): ~2,000 orders/sec

WebSocket connections:
  20% of DAU keep app open: 200K concurrent connections
  Price updates: 1M events/second (100 stocks × 10K subscribers each)
```

### Storage Estimates
```
User data:
  10M users × 10 KB/user = 100 GB

Order history:
  5M orders/day × 1 KB/order = 5 GB/day
  Yearly: 5 GB × 365 = 1.8 TB/year
  10 years: 18 TB

Portfolio holdings:
  1M active users × avg 10 holdings × 500 bytes = 5 GB

Market data (historical):
  5000 stocks × 365 days × 390 minutes × 100 bytes = 7 TB/year
  10 years: 70 TB

Transaction records:
  5M transactions/day × 2 KB/transaction = 10 GB/day
  Yearly: 3.6 TB/year

Total storage (10 years): ~200 TB
```

### Bandwidth Estimates
```
Market data streaming:
  100K concurrent WebSocket connections
  Each receives updates for ~10 stocks
  Updates: 1 per second per stock
  Payload: 200 bytes per update
  
  Bandwidth: 100K × 10 × 1 × 200 bytes = 200 MB/second

Order placement:
  2000 orders/sec (peak) × 1 KB = 2 MB/second

API responses:
  1750 QPS × 5 KB avg response = 8.75 MB/second

Total outgoing bandwidth: ~210 MB/second
```

### Memory Estimates (Caching)
```
Cache hot data:
- User sessions: 1M users × 5 KB = 5 GB
- Portfolio data: 500K active users × 10 KB = 5 GB
- Stock prices (realtime): 5000 stocks × 1 KB = 5 MB
- Order book (pending orders): 100K orders × 1 KB = 100 MB
- Market data (5-min window): 200 MB

Total cache: ~11 GB (easily fits in single Redis instance)
Distribute across 5 cache servers: ~2.5 GB each
```

---

## High-Level Architecture

```
                           [Users]
                              |
                   ┌──────────┴──────────┐
                   ↓                     ↓
              [Mobile App]          [Web App]
                   |                     |
                   └──────────┬──────────┘
                              ↓
                   [CDN - CloudFront]
                              |
                              ↓
                  [API Gateway - Kong/AWS]
                  [Authentication & Rate Limiting]
                              |
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
   [REST APIs]      [WebSocket Server]      [GraphQL API]
        |                     |                     |
        └─────────────────────┼─────────────────────┘
                              ↓
                    [Application Layer]
                              |
    ┌─────────────────────────┼──────────────────────────┐
    ↓                         ↓                          ↓
[User Service]         [Trading Service]         [Market Data Service]
    |                         |                          |
    ↓                         ↓                          ↓
[Wallet Service]      [Order Matching Engine]    [Price Aggregator]
    |                         |                          |
    ↓                         ↓                          ↓
[KYC Service]         [Portfolio Service]        [Analytics Service]
    |                         |                          |
    └─────────────────────────┼──────────────────────────┘
                              ↓
                       [Cache Layer]
                       [Redis Cluster]
                              |
    ┌─────────────────────────┼──────────────────────────┐
    ↓                         ↓                          ↓
[User DB]               [Trading DB]              [Market Data DB]
[PostgreSQL]            [PostgreSQL]              [TimescaleDB]
    |                         |                          |
[Wallet DB]            [Order Book Cache]         [Price Cache]
[PostgreSQL]           [Redis]                    [Redis]
    |                         |                          |
    └─────────────────────────┼──────────────────────────┘
                              ↓
                    [Message Queue]
                    [Kafka/RabbitMQ]
                              |
              ┌───────────────┼───────────────┐
              ↓               ↓               ↓
    [Notification     [Settlement      [Audit/Compliance
     Service]          Service]         Service]
              |               |               |
              ↓               ↓               ↓
    [Push/Email]    [Payment Gateway]   [Audit Logs]
    [FCM/SMTP]      [Stripe/Razorpay]   [S3/Warehouse]
                              |
                              ↓
                    [External Services]
                              |
              ┌───────────────┼────────────────┐
              ↓               ↓                ↓
    [Stock Exchanges]  [Market Data      [Bank APIs]
    [NYSE/NSE/BSE]      Providers]        [Plaid/Yodlee]
                        [Bloomberg/        
                         Reuters]
```

---

## Core Components

### 1. API Gateway (Kong/AWS API Gateway)

**Purpose**: Single entry point for all client requests

**Responsibilities**:
- **Authentication**: Validate JWT tokens, OAuth
- **Authorization**: Check user permissions
- **Rate limiting**: Prevent abuse (100 req/min per user)
- **Request routing**: Route to appropriate microservice
- **SSL termination**: Handle HTTPS
- **API versioning**: Support multiple API versions
- **Request/Response transformation**
- **Circuit breaking**: Fail fast on downstream failures

**Technology**: Kong, AWS API Gateway, NGINX

**Rate Limits**:
```
Per user:
- API calls: 100/minute (normal), 1000/minute (premium)
- Order placement: 10/minute
- Portfolio refresh: 20/minute
- Market data: unlimited (WebSocket)

Per IP:
- Registration: 5/hour
- Login: 20/minute
```

### 2. User Service

**Purpose**: Manage user accounts and authentication

**Features**:
- User registration/login
- Profile management
- KYC verification workflow
- Bank account linking
- Password reset, 2FA setup
- User preferences (dark mode, notifications)

**Database Schema**:
```sql
Users:
- user_id (PK, UUID)
- email (unique)
- phone (unique)
- password_hash
- kyc_status (PENDING, VERIFIED, REJECTED)
- kyc_doc_urls (JSON)
- created_at, updated_at

LinkedBankAccounts:
- account_id (PK)
- user_id (FK)
- bank_name
- account_number (encrypted)
- ifsc_code/routing_number
- is_primary
- verified_at
```

**API Endpoints**:
```
POST   /api/v1/users/register
POST   /api/v1/users/login
GET    /api/v1/users/profile
PUT    /api/v1/users/profile
POST   /api/v1/users/kyc/submit
GET    /api/v1/users/kyc/status
POST   /api/v1/users/bank-accounts
```

### 3. Wallet Service

**Purpose**: Manage user's buying power and cash balance

**Features**:
- Add money (deposit)
- Withdraw money
- Track available balance
- Transaction history
- Instant settlements for some transactions

**Database Schema**:
```sql
Wallets:
- wallet_id (PK)
- user_id (FK, unique)
- balance (DECIMAL(15,2))
- buying_power (DECIMAL(15,2)) -- includes margin if enabled
- withdrawable_cash (DECIMAL(15,2))
- updated_at

Transactions:
- transaction_id (PK, UUID)
- wallet_id (FK)
- type (DEPOSIT, WITHDRAWAL, BUY, SELL, DIVIDEND, FEE)
- amount (DECIMAL(15,2))
- status (PENDING, COMPLETED, FAILED, CANCELLED)
- reference_id (order_id or external transaction id)
- created_at
- completed_at

Deposits:
- deposit_id (PK)
- user_id (FK)
- amount
- payment_method (UPI, BANK_TRANSFER, CARD)
- payment_gateway_id
- status
- created_at
```

**Critical Operations**:
```python
# Atomic balance deduction (use database transactions)
def deduct_for_purchase(user_id, amount):
    with transaction:
        wallet = SELECT * FROM wallets WHERE user_id = ? FOR UPDATE
        if wallet.buying_power >= amount:
            UPDATE wallets SET buying_power = buying_power - amount
            INSERT INTO transactions (...)
            return SUCCESS
        return INSUFFICIENT_FUNDS
```

### 4. Trading Service

**Purpose**: Handle order placement and execution

**Features**:
- Place orders (market, limit, stop-loss)
- Validate orders (funds, market hours, quantity)
- Order routing to exchanges
- Order status tracking
- Order cancellation
- Fractional shares support

**Database Schema**:
```sql
Orders:
- order_id (PK, UUID)
- user_id (FK, indexed)
- stock_symbol (indexed)
- order_type (MARKET, LIMIT, STOP_LOSS)
- side (BUY, SELL)
- quantity (DECIMAL)
- price (DECIMAL) -- null for market orders
- status (PENDING, PARTIAL, FILLED, CANCELLED, REJECTED)
- filled_quantity (DECIMAL)
- average_price (DECIMAL)
- created_at (indexed)
- updated_at
- filled_at

OrderExecutions:
- execution_id (PK)
- order_id (FK)
- quantity (DECIMAL)
- price (DECIMAL)
- fee (DECIMAL)
- executed_at
- exchange_trade_id
```

**Order Flow**:
```
1. Client → API: Place order
2. Validate: Check funds, market hours, stock exists
3. Deduct buying power from wallet (hold)
4. Insert order in DB with PENDING status
5. Send to Order Matching Engine / Exchange
6. Receive execution confirmation
7. Update order status to FILLED
8. Update portfolio holdings
9. Settle funds (release hold, deduct actual)
10. Send notification to user
```

### 5. Order Matching Engine

**Purpose**: Match buy and sell orders (for internal liquidity or route to exchange)

**Two Modes**:

**A. Internal Matching** (for high liquidity stocks):
- Match buy/sell orders from our users
- Reduce exchange fees
- Faster execution

**B. Exchange Routing**:
- Route orders to external exchanges (NYSE, NASDAQ, NSE)
- Use FIX protocol (Financial Information eXchange)
- Smart order routing (find best price across exchanges)

**Matching Algorithm** (Internal):
```
Price-Time Priority:
1. Match by best price first
2. If same price, FIFO (first come first serve)
3. Partial fills allowed

Example Order Book:
Sell Orders (Asks):
  $102.50 - 100 shares
  $102.00 - 200 shares
  
Buy Orders (Bids):
  $101.50 - 150 shares
  $101.00 - 300 shares

Market Buy Order (50 shares) → Filled at $102.00
```

**Technology**: 
- In-memory data structure (Redis Sorted Sets) for speed
- Low-latency language: C++, Rust, or Go
- Target latency: < 10ms for matching

### 6. Market Data Service

**Purpose**: Aggregate and distribute real-time market data

**Data Sources**:
- Stock exchanges (NYSE, NASDAQ, NSE, BSE)
- Market data providers (Bloomberg, Reuters, IEX Cloud)
- Free APIs (Alpha Vantage, Yahoo Finance) for basic data

**Features**:
- Real-time price quotes
- Historical OHLCV data (Open, High, Low, Close, Volume)
- Market depth (Level 2 data)
- News feed
- Company fundamentals

**Architecture**:
```
[Exchange Feed] → [Price Aggregator] → [Redis Cache] → [WebSocket Server]
                                                    ↓
                                              [TimescaleDB]
                                              (Historical data)
```

**WebSocket Protocol**:
```json
// Client subscribes
{
  "action": "subscribe",
  "symbols": ["AAPL", "GOOGL", "TSLA"]
}

// Server pushes updates
{
  "symbol": "AAPL",
  "price": 178.50,
  "change": +2.30,
  "volume": 45623190,
  "timestamp": 1704723845000
}
```

**Optimization**:
- **Fan-out on write**: When price updates, push to all subscribers
- **Throttling**: Send max 1 update per symbol per second to clients
- **Batching**: Bundle multiple symbols in single WebSocket message

### 7. Portfolio Service

**Purpose**: Track user holdings and calculate P&L

**Features**:
- Real-time portfolio value
- Day P&L and total P&L
- Cost basis calculation (FIFO, LIFO, specific lot)
- Dividend tracking
- Performance metrics (returns, Sharpe ratio)

**Database Schema**:
```sql
Holdings:
- holding_id (PK)
- user_id (FK, indexed)
- stock_symbol (indexed)
- quantity (DECIMAL)
- average_cost (DECIMAL) -- cost basis per share
- total_cost (DECIMAL) -- total invested
- current_value (DECIMAL) -- quantity × current_price
- unrealized_pnl (DECIMAL) -- current_value - total_cost
- realized_pnl (DECIMAL) -- from previous sells
- last_updated

Dividends:
- dividend_id (PK)
- user_id (FK)
- stock_symbol
- amount_per_share
- total_amount
- payment_date
- record_date
```

**P&L Calculation**:
```python
def calculate_portfolio_value(user_id):
    holdings = get_holdings(user_id)
    total_value = 0
    total_invested = 0
    
    for holding in holdings:
        current_price = get_current_price(holding.symbol)
        current_value = holding.quantity * current_price
        
        total_value += current_value
        total_invested += holding.total_cost
    
    unrealized_pnl = total_value - total_invested
    day_pnl = calculate_day_change(holdings)
    
    return {
        'total_value': total_value,
        'total_invested': total_invested,
        'unrealized_pnl': unrealized_pnl,
        'day_pnl': day_pnl,
        'return_percentage': (unrealized_pnl / total_invested) * 100
    }
```

### 8. Settlement Service

**Purpose**: Handle T+2 settlement for stock trades

**Background**:
- Stock trades settle in T+2 days (2 business days after trade)
- Cash is held during settlement period
- After settlement, cash becomes withdrawable

**Process**:
```
Day 0 (Trade Day):
  - User buys stock for $1000
  - $1000 deducted from buying power
  - Stock added to holdings (not settled)
  
Day 1 (T+1):
  - Pending settlement
  
Day 2 (T+2):
  - Settlement complete
  - Stock marked as settled
  - User can now sell
```

**Database**:
```sql
Settlements:
- settlement_id (PK)
- order_id (FK)
- trade_date
- settlement_date (trade_date + 2 business days)
- status (PENDING, SETTLED)
- settled_at
```

**Cron Job**:
- Run daily at market close
- Process all trades from T-2
- Update settlement status
- Update withdrawable cash

### 9. Notification Service

**Purpose**: Send timely notifications to users

**Notification Types**:
- **Order executed**: "Your order for 10 shares of AAPL filled at $178.50"
- **Price alerts**: "TSLA reached your target price of $250"
- **Market news**: "Apple announces Q4 earnings"
- **Dividends**: "You received $15.50 dividend from MSFT"
- **Deposits/Withdrawals**: "Your deposit of $5000 is processed"

**Channels**:
- Push notifications (FCM for Android, APNs for iOS)
- Email
- SMS (for critical alerts)
- In-app notifications

**Architecture**:
```
[Event] → [Kafka] → [Notification Service] → [FCM/APNs]
                                          → [Email Service]
                                          → [SMS Gateway]
```

**Deduplication**: Don't spam users with same event multiple times

### 10. Analytics Service

**Purpose**: Generate insights and reports

**Features**:
- Portfolio performance charts
- Tax reports (capital gains, dividends)
- Transaction history export (CSV)
- Investment patterns
- Risk analysis

**Tech Stack**:
- **Data Warehouse**: Snowflake, BigQuery, Redshift
- **ETL Pipeline**: Apache Airflow, AWS Glue
- **BI Tools**: Tableau, Looker, Metabase

**Reports Generated**:
- 1099 forms (for US taxes)
- Capital gains/loss statement
- Dividend income report
- Portfolio allocation (sector-wise, stock-wise)

---

## Database Design

### Database Selection

**User & Wallet Data → PostgreSQL**
- ACID compliance critical for financial transactions
- Complex queries (JOINs for user, wallet, bank accounts)
- Mature, battle-tested

**Trading Data → PostgreSQL + Redis**
- PostgreSQL for persistent storage
- Redis for hot data (pending orders, recent trades)

**Market Data → TimescaleDB**
- Time-series database (optimized for time-based queries)
- Efficient storage and querying of historical prices
- Automatic partitioning by time

**Cache Layer → Redis**
- Portfolio data (frequently accessed)
- Stock prices (real-time)
- User sessions
- Order book (in-memory)

### Schema Design

#### Users & Authentication
```sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    kyc_status VARCHAR(20) DEFAULT 'PENDING',
    kyc_verified_at TIMESTAMP,
    risk_profile VARCHAR(20), -- CONSERVATIVE, MODERATE, AGGRESSIVE
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_kyc_status ON users(kyc_status);
```

#### Wallets & Transactions
```sql
CREATE TABLE wallets (
    wallet_id SERIAL PRIMARY KEY,
    user_id UUID UNIQUE REFERENCES users(user_id),
    balance NUMERIC(15,2) DEFAULT 0.00,
    buying_power NUMERIC(15,2) DEFAULT 0.00,
    withdrawable_cash NUMERIC(15,2) DEFAULT 0.00,
    margin_enabled BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id INT REFERENCES wallets(wallet_id),
    type VARCHAR(20) NOT NULL, -- DEPOSIT, WITHDRAWAL, BUY, SELL, DIVIDEND, FEE
    amount NUMERIC(15,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    reference_id VARCHAR(100), -- order_id or external payment id
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_transactions_wallet ON transactions(wallet_id, created_at DESC);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(type);
```

#### Orders & Executions
```sql
CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id),
    stock_symbol VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL, -- MARKET, LIMIT, STOP_LOSS
    side VARCHAR(4) NOT NULL, -- BUY, SELL
    quantity NUMERIC(10,4) NOT NULL,
    price NUMERIC(10,2), -- NULL for market orders
    status VARCHAR(20) DEFAULT 'PENDING',
    filled_quantity NUMERIC(10,4) DEFAULT 0,
    average_price NUMERIC(10,2),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    filled_at TIMESTAMP,
    expires_at TIMESTAMP, -- for limit orders
    
    CONSTRAINT valid_quantity CHECK (quantity > 0),
    CONSTRAINT valid_side CHECK (side IN ('BUY', 'SELL'))
);

CREATE INDEX idx_orders_user ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_symbol ON orders(stock_symbol);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_pending ON orders(status) WHERE status = 'PENDING';

CREATE TABLE order_executions (
    execution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID REFERENCES orders(order_id),
    quantity NUMERIC(10,4) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    fee NUMERIC(10,2) DEFAULT 0.00,
    executed_at TIMESTAMP DEFAULT NOW(),
    exchange_trade_id VARCHAR(100)
);

CREATE INDEX idx_executions_order ON order_executions(order_id);
```

#### Holdings & Portfolio
```sql
CREATE TABLE holdings (
    holding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id),
    stock_symbol VARCHAR(10) NOT NULL,
    quantity NUMERIC(10,4) NOT NULL,
    average_cost NUMERIC(10,2) NOT NULL,
    total_cost NUMERIC(15,2) NOT NULL,
    realized_pnl NUMERIC(15,2) DEFAULT 0.00,
    last_updated TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(user_id, stock_symbol)
);

CREATE INDEX idx_holdings_user ON holdings(user_id);

CREATE TABLE dividends (
    dividend_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(user_id),
    stock_symbol VARCHAR(10) NOT NULL,
    quantity NUMERIC(10,4) NOT NULL,
    amount_per_share NUMERIC(10,2) NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL,
    payment_date DATE NOT NULL,
    record_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_dividends_user ON dividends(user_id, payment_date DESC);
```

#### Market Data (TimescaleDB)
```sql
CREATE TABLE stock_prices (
    symbol VARCHAR(10) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    open NUMERIC(10,2),
    high NUMERIC(10,2),
    low NUMERIC(10,2),
    close NUMERIC(10,2),
    volume BIGINT,
    
    PRIMARY KEY (symbol, timestamp)
);

-- Convert to hypertable (TimescaleDB specific)
SELECT create_hypertable('stock_prices', 'timestamp');

-- Retention policy: keep 1-minute data for 90 days, daily data forever
SELECT add_retention_policy('stock_prices', INTERVAL '90 days');

CREATE TABLE stock_metadata (
    symbol VARCHAR(10) PRIMARY KEY,
    company_name VARCHAR(255),
    sector VARCHAR(100),
    industry VARCHAR(100),
    market_cap NUMERIC(20,2),
    pe_ratio NUMERIC(10,2),
    dividend_yield NUMERIC(5,2),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### Sharding Strategy

**User-based Sharding**:
```
Shard = hash(user_id) % num_shards

Benefits:
- User's data co-located (wallet, orders, holdings)
- No cross-shard queries for user operations
- Easy to scale

Challenges:
- Shard rebalancing if user distribution uneven
- Cross-shard queries for admin/analytics
```

**Time-based Partitioning** (for orders, transactions):
```sql
-- Partition orders by month
CREATE TABLE orders_2025_01 PARTITION OF orders
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE orders_2025_02 PARTITION OF orders
FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Automatically create partitions using pg_partman extension
```

### Replication

**Master-Slave Setup**:
- 1 Master (writes)
- 2+ Slaves (reads)
- Asynchronous replication for slaves
- Synchronous replication for critical operations

**Read-Write Separation**:
- Write operations → Master database
- Read operations → Read replicas
- Use connection pooling (PgBouncer)

**Multi-Region**:
- Primary region: US East (for US users) or India (for Indian users)
- Secondary regions: for disaster recovery
- Cross-region replication with lag monitoring

---

## API Design

### RESTful API Endpoints

#### User APIs
```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
POST   /api/v1/auth/refresh-token
POST   /api/v1/auth/forgot-password
POST   /api/v1/auth/reset-password

GET    /api/v1/users/profile
PUT    /api/v1/users/profile
POST   /api/v1/users/kyc/submit
GET    /api/v1/users/kyc/status
POST   /api/v1/users/bank-accounts
GET    /api/v1/users/bank-accounts
DELETE /api/v1/users/bank-accounts/{account_id}
```

#### Wallet APIs
```
GET    /api/v1/wallet/balance
POST   /api/v1/wallet/deposit
POST   /api/v1/wallet/withdraw
GET    /api/v1/wallet/transactions?page=1&limit=50
GET    /api/v1/wallet/transactions/{transaction_id}
```

#### Trading APIs
```
POST   /api/v1/orders
GET    /api/v1/orders/{order_id}
DELETE /api/v1/orders/{order_id}  # Cancel order
GET    /api/v1/orders?status=PENDING&page=1&limit=50
GET    /api/v1/orders/history?from_date=2025-01-01&to_date=2025-01-31
```

#### Market Data APIs
```
GET    /api/v1/stocks/search?q=apple
GET    /api/v1/stocks/{symbol}
GET    /api/v1/stocks/{symbol}/quote
GET    /api/v1/stocks/{symbol}/chart?interval=1d&range=1y
GET    /api/v1/stocks/{symbol}/fundamentals
GET    /api/v1/stocks/{symbol}/news
GET    /api/v1/market/indices
GET    /api/v1/market/top-gainers
GET    /api/v1/market/top-losers
```

#### Portfolio APIs
```
GET    /api/v1/portfolio
GET    /api/v1/portfolio/holdings
GET    /api/v1/portfolio/performance?period=1Y
GET    /api/v1/portfolio/dividends
```

#### Watchlist APIs
```
GET    /api/v1/watchlist
POST   /api/v1/watchlist
DELETE /api/v1/watchlist/{symbol}
POST   /api/v1/watchlist/{symbol}/alert
GET    /api/v1/watchlist/{symbol}/alerts
```

### API Request/Response Examples

#### Place Order Request
```json
POST /api/v1/orders
Authorization: Bearer <jwt_token>

{
  "stock_symbol": "AAPL",
  "side": "BUY",
  "order_type": "LIMIT",
  "quantity": 10,
  "price": 175.50,
  "time_in_force": "DAY"  // DAY, GTC (Good Till Cancel)
}
```

#### Place Order Response
```json
{
  "success": true,
  "data": {
    "order_id": "ord_abc123xyz",
    "stock_symbol": "AAPL",
    "side": "BUY",
    "order_type": "LIMIT",
    "quantity": 10,
    "price": 175.50,
    "status": "PENDING",
    "filled_quantity": 0,
    "created_at": "2025-01-10T14:30:00Z",
    "estimated_total": 1755.00
  }
}
```

#### Get Portfolio Response
```json
{
  "success": true,
  "data": {
    "total_value": 125430.50,
    "total_invested": 100000.00,
    "cash_balance": 5430.50,
    "buying_power": 5430.50,
    "day_pnl": 2150.30,
    "day_pnl_percentage": 1.74,
    "total_pnl": 25430.50,
    "total_pnl_percentage": 25.43,
    "holdings": [
      {
        "symbol": "AAPL",
        "quantity": 100,
        "average_cost": 150.00,
        "current_price": 178.50,
        "current_value": 17850.00,
        "total_cost": 15000.00,
        "unrealized_pnl": 2850.00,
        "unrealized_pnl_percentage": 19.00,
        "day_change": 350.00,
        "day_change_percentage": 2.00
      },
      {
        "symbol": "GOOGL",
        "quantity": 50,
        "average_cost": 140.00,
        "current_price": 142.50,
        "current_value": 7125.00,
        "total_cost": 7000.00,
        "unrealized_pnl": 125.00,
        "unrealized_pnl_percentage": 1.79,
        "day_change": -75.00,
        "day_change_percentage": -1.04
      }
    ]
  }
}
```

### WebSocket API

**Connection**:
```javascript
const ws = new WebSocket('wss://api.trading.com/v1/ws');

ws.onopen = () => {
  ws.send(JSON.stringify({
    action: 'auth',
    token: 'jwt_token_here'
  }));
};
```

**Subscribe to Real-time Quotes**:
```javascript
ws.send(JSON.stringify({
  action: 'subscribe',
  channels: ['quotes'],
  symbols: ['AAPL', 'GOOGL', 'TSLA']
}));
```

**Receive Updates**:
```json
{
  "channel": "quotes",
  "data": {
    "symbol": "AAPL",
    "price": 178.50,
    "change": 2.30,
    "change_percentage": 1.31,
    "volume": 45623190,
    "bid": 178.48,
    "ask": 178.52,
    "timestamp": 1704723845000
  }
}
```

**Subscribe to Order Updates**:
```javascript
ws.send(JSON.stringify({
  action: 'subscribe',
  channels: ['orders']
}));
```

**Order Update Event**:
```json
{
  "channel": "orders",
  "data": {
    "order_id": "ord_abc123xyz",
    "status": "FILLED",
    "filled_quantity": 10,
    "average_price": 175.45,
    "filled_at": "2025-01-10T14:32:15Z"
  }
}
```

---

## Deep Dives

### 1. Order Execution Flow (End-to-End)

```
┌─────────────┐
│   Client    │
│  (Mobile)   │
└──────┬──────┘
       │
       │ 1. POST /api/v1/orders
       ↓
┌─────────────────┐
│   API Gateway   │ 2. Authenticate, Rate Limit
│   (Kong/AWS)    │
└─────────┬───────┘
          │
          │ 3. Route to Trading Service
          ↓
┌─────────────────┐
│ Trading Service │ 4. Validate Order
│                 │    - Check market hours
│                 │    - Validate symbol
│                 │    - Check quantity > 0
└─────────┬───────┘
          │
          │ 5. Check Buying Power
          ↓
┌─────────────────┐
│ Wallet Service  │ 6. Lock Funds
│                 │    BEGIN TRANSACTION
│                 │    IF buying_power >= amount
│                 │      buying_power -= amount
│                 │    COMMIT
└─────────┬───────┘
          │
          │ 7. Funds Locked ✓
          ↓
┌─────────────────┐
│ Trading Service │ 8. Insert Order (PENDING)
│                 │    order_id = uuid()
└─────────┬───────┘
          │
          │ 9. Publish to Kafka
          ↓
┌─────────────────┐
│     Kafka       │ Topic: orders.new
│                 │
└─────────┬───────┘
          │
          │ 10. Consume Event
          ↓
┌─────────────────────┐
│ Order Matching      │ 11. Match Order
│ Engine              │     - Internal match? or
│                     │     - Route to exchange
└──────────┬──────────┘
           │
           │ 12. Execution Confirmation
           │     {price, quantity, fee}
           ↓
┌─────────────────┐
│ Trading Service │ 13. Update Order (FILLED)
│                 │     - filled_quantity
│                 │     - average_price
│                 │     - status = FILLED
└─────────┬───────┘
          │
          │ 14. Settle Funds
          ↓
┌─────────────────┐
│ Wallet Service  │ 15. Release Lock
│                 │     - Deduct actual amount
│                 │     - Add back difference
└─────────┬───────┘
          │
          │ 16. Update Portfolio
          ↓
┌──────────────────┐
│Portfolio Service │ 17. Update Holdings
│                  │     - quantity += 10
│                  │     - average_cost = recalculate
└─────────┬────────┘
          │
          │ 18. Publish Events
          ↓
┌─────────────────┐
│     Kafka       │ Topics:
│                 │ - orders.filled
│                 │ - portfolio.updated
└─────────┬───────┘
          │
          ├─────────────────────┐
          │                     │
          ↓                     ↓
┌──────────────────┐   ┌────────────────┐
│ Notification     │   │  Settlement    │
│ Service          │   │  Service       │
└─────────┬────────┘   └────────┬───────┘
          │                     │
          │ 19. Send Push       │ 20. Schedule T+2
          ↓                     │     Settlement
┌─────────────────┐            │
│   Client        │            │
│ "Order Filled!" │            │
└─────────────────┘            │
                               │
                               ↓
                      (T+2 days later)
                      Mark as settled
```

**Latency Breakdown**:
```
Step 1-3 (API Gateway): 20ms
Step 4-6 (Validation + Lock): 50ms
Step 7-8 (DB Insert): 30ms
Step 9-10 (Kafka): 10ms
Step 11-12 (Matching): 50ms
Step 13-17 (Updates): 100ms
Step 18-20 (Events): 50ms

Total: ~310ms (well within 500ms SLA)
```

### 2. Real-time Market Data Distribution

**Challenge**: Stream price updates to 100K+ concurrent users efficiently

**Architecture**:
```
┌──────────────┐
│   Exchange   │ (NYSE, NASDAQ, NSE)
│   Feed       │
└──────┬───────┘
       │ WebSocket/FIX
       │ 1M events/sec
       ↓
┌──────────────────┐
│ Price Aggregator │ (Go/C++)
│                  │ - Parse feed
│                  │ - Normalize format
└──────┬───────────┘
       │
       │ Publish
       ↓
┌──────────────────┐
│   Redis Pub/Sub  │ Channel: prices.{symbol}
│                  │
└──────┬───────────┘
       │
       │ Subscribe
       ↓
┌──────────────────────┐
│  WebSocket Server    │ (Node.js cluster)
│  (10 instances)      │ - 10K connections each
└──────┬───────────────┘
       │
       │ Push via WebSocket
       ↓
┌──────────────────┐
│     Clients      │ (Mobile/Web)
│   (100K users)   │
└──────────────────┘
```

**Optimizations**:

1. **Throttling**: Don't send every tick
   ```javascript
   // Send max 1 update per second per symbol
   const throttle = (symbol, data) => {
     const key = `throttle:${symbol}`;
     if (!cache.get(key)) {
       cache.set(key, true, 1000); // 1 sec TTL
       ws.send(data);
     }
   };
   ```

2. **Batching**: Combine multiple symbols
   ```json
   {
     "channel": "quotes",
     "data": [
       {"symbol": "AAPL", "price": 178.50},
       {"symbol": "GOOGL", "price": 142.30},
       {"symbol": "TSLA", "price": 248.90}
     ]
   }
   ```

3. **Binary Protocol**: Use Protocol Buffers instead of JSON
   ```
   JSON: 200 bytes per update
   Protobuf: 50 bytes per update
   Savings: 75%
   ```

4. **Connection Pooling**: Reuse WebSocket connections
   ```
   Single connection per user (not per symbol)
   Subscribe to multiple symbols on same connection
   ```

### 3. Fraud Detection & Risk Management

**Use Cases**:
- Detect pump-and-dump schemes
- Identify wash trading
- Prevent pattern day trading violations
- Flag suspicious account activity

**Real-time Rules Engine**:
```python
class RiskEngine:
    def check_order(self, user_id, order):
        violations = []
        
        # Rule 1: Pattern Day Trading (PDT)
        if self.is_day_trade(user_id, order):
            day_trades = self.count_day_trades_this_week(user_id)
            account_value = self.get_account_value(user_id)
            
            if day_trades >= 3 and account_value < 25000:
                violations.append("PDT_VIOLATION")
        
        # Rule 2: Unusual volume
        avg_order_size = self.get_avg_order_size(user_id)
        if order.quantity > avg_order_size * 10:
            violations.append("UNUSUAL_VOLUME")
        
        # Rule 3: Rapid trading
        recent_orders = self.get_orders_last_5min(user_id)
        if len(recent_orders) > 50:
            violations.append("RAPID_TRADING")
        
        # Rule 4: Penny stock pump
        if order.price < 5 and order.quantity > 10000:
            violations.append("PENNY_STOCK_ALERT")
        
        if violations:
            self.flag_for_review(user_id, order, violations)
            return False  # Block order
        
        return True  # Allow order
```

**ML-based Fraud Detection**:
```
Features:
- Order size, frequency, timing
- Account age, balance history
- Device fingerprint, IP address
- Geographic location, unusual hours
- Social graph (linked accounts)

Model: Random Forest / XGBoost
Training: Historical fraud cases
Prediction: Real-time score (0-1)
Action: Score > 0.8 → Block & Review
```

### 4. Tax Calculation & Reporting

**Challenge**: Calculate capital gains/losses accurately

**Cost Basis Methods**:

1. **FIFO** (First In, First Out) - Default
   ```
   Buys:
   - 100 AAPL @ $150 (Jan 1)
   - 50 AAPL @ $160 (Feb 1)
   
   Sell: 120 AAPL @ $170 (Mar 1)
   
   Calculation:
   - 100 shares @ $150 = $15,000 cost
   - 20 shares @ $160 = $3,200 cost
   - Total cost: $18,200
   - Sale value: 120 × $170 = $20,400
   - Capital gain: $20,400 - $18,200 = $2,200
   ```

2. **LIFO** (Last In, First Out)
   ```
   Sell: 120 AAPL @ $170
   
   Calculation:
   - 50 shares @ $160 = $8,000 cost
   - 70 shares @ $150 = $10,500 cost
   - Total cost: $18,500
   - Sale value: $20,400
   - Capital gain: $20,400 - $18,500 = $1,900
   ```

3. **Specific Lot** (User chooses which shares to sell)

**Tax Forms Generation**:
```sql
-- Generate 1099 form data
SELECT 
  user_id,
  SUM(CASE WHEN holding_period <= 365 
      THEN gain ELSE 0 END) as short_term_gain,
  SUM(CASE WHEN holding_period > 365 
      THEN gain ELSE 0 END) as long_term_gain,
  SUM(dividend_amount) as total_dividends
FROM (
  SELECT 
    o1.user_id,
    (o2.executed_at - o1.executed_at) as holding_period,
    (o2.price - o1.price) * o2.quantity as gain
  FROM orders o1
  JOIN orders o2 ON o1.stock_symbol = o2.stock_symbol
  WHERE o1.side = 'BUY' 
    AND o2.side = 'SELL'
    AND YEAR(o2.executed_at) = 2024
) gains
JOIN dividends d ON d.user_id = gains.user_id
  AND YEAR(d.payment_date) = 2024
GROUP BY user_id;
```

### 5. Disaster Recovery & Business Continuity

**Backup Strategy**:
```
Database Backups:
- Full backup: Daily at 2 AM
- Incremental backup: Every 4 hours
- Transaction logs: Continuous archival
- Retention: 90 days online, 7 years archived

S3 Backups:
- Cross-region replication
- Versioning enabled
- Lifecycle policy to Glacier

Recovery Scenarios:
1. Database failure → Promote read replica (30 sec)
2. Region failure → Failover to secondary region (5 min)
3. Data corruption → Restore from backup (1 hour)
```

**Multi-Region Failover**:
```
Primary Region (US-East):
- 80% traffic
- Master database
- Active-active for reads

Secondary Region (US-West):
- 20% traffic
- Read replica
- Standby for failover

Failover Process:
1. Health check fails in US-East
2. DNS switches to US-West (Route53)
3. Promote read replica to master
4. Resume trading operations
5. Downtime: < 5 minutes
```

---

## Scalability & Reliability

### Horizontal Scaling

**Application Tier**:
- Stateless microservices
- Auto-scaling groups (AWS ASG)
- Scale based on:
  - CPU utilization > 70%
  - Request queue depth > 100
  - Time of day (pre-market, market hours)

**Database Tier**:
- Read replicas for read scaling
- Sharding for write scaling
- Connection pooling (PgBouncer)

**Cache Tier**:
- Redis Cluster (6+ nodes)
- Consistent hashing for distribution
- Automatic failover

### Load Balancing

**Strategy**:
```
Layer 4 (Network): 
- Distribute TCP connections
- Used for WebSocket

Layer 7 (Application):
- Route based on URL path
- Used for REST APIs
- Sticky sessions for WebSocket

Geographic:
- Route US users → US region
- Route India users → India region
- Use CloudFront edge locations
```

### Caching Strategy

**Cache Layers**:

1. **CDN (CloudFront)**: Static assets, images
2. **Application Cache (Redis)**: Hot data
3. **Database Query Cache**: Repeated queries

**What to Cache**:
```
Data Type            TTL      Strategy
User sessions        30 min   Write-through
Stock prices         1 sec    Write-through
Portfolio data       5 min    Cache-aside
Order book           Real-time Write-through
User profile         1 hour   Cache-aside
Market news          15 min   Cache-aside
```

**Cache Invalidation**:
```python
# On order execution
def on_order_filled(order_id):
    order = get_order(order_id)
    
    # Invalidate portfolio cache
    cache.delete(f"portfolio:{order.user_id}")
    
    # Invalidate holdings cache
    cache.delete(f"holdings:{order.user_id}")
    
    # Invalidate order cache
    cache.delete(f"orders:{order.user_id}")
```

### Database Optimization

**Query Optimization**:
```sql
-- Bad: N+1 query
SELECT * FROM holdings WHERE user_id = ?;
-- Then for each holding:
SELECT * FROM stock_prices WHERE symbol = ?;

-- Good: JOIN with aggregation
SELECT 
  h.*,
  sp.current_price,
  (h.quantity * sp.current_price) as current_value,
  ((h.quantity * sp.current_price) - h.total_cost) as unrealized_pnl
FROM holdings h
JOIN (
  SELECT DISTINCT ON (symbol) 
    symbol, close as current_price
  FROM stock_prices
  WHERE symbol IN (SELECT stock_symbol FROM holdings WHERE user_id = ?)
  ORDER BY symbol, timestamp DESC
) sp ON h.stock_symbol = sp.symbol
WHERE h.user_id = ?;
```

**Connection Pooling**:
```
Application servers: 50
Connections per server: 10
Total connections: 500

Database max_connections: 1000
Reserve for admin: 100
Available for app: 900

Use PgBouncer for connection pooling:
- Transaction pooling mode
- Pool size: 20 per database
```

**Indexing Strategy**:
```sql
-- Critical indexes for fast queries
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_symbol_status ON orders(stock_symbol, status);
CREATE INDEX idx_holdings_user ON holdings(user_id);
CREATE INDEX idx_transactions_wallet_date ON transactions(wallet_id, created_at DESC);
CREATE INDEX idx_stock_prices_symbol_time ON stock_prices(symbol, timestamp DESC);
```

### Circuit Breaker Pattern

**Purpose**: Prevent cascading failures

**Implementation**:
```python
class CircuitBreaker:
    def __init__(self, failure_threshold=5, timeout=60):
        self.failure_count = 0
        self.failure_threshold = failure_threshold
        self.timeout = timeout
        self.state = "CLOSED"  # CLOSED, OPEN, HALF_OPEN
        self.last_failure_time = None
    
    def call(self, func, *args):
        if self.state == "OPEN":
            if time.time() - self.last_failure_time > self.timeout:
                self.state = "HALF_OPEN"
            else:
                raise CircuitBreakerOpenError()
        
        try:
            result = func(*args)
            if self.state == "HALF_OPEN":
                self.state = "CLOSED"
                self.failure_count = 0
            return result
        except Exception as e:
            self.failure_count += 1
            self.last_failure_time = time.time()
            
            if self.failure_count >= self.failure_threshold:
                self.state = "OPEN"
            raise e

# Usage
exchange_api = CircuitBreaker(failure_threshold=5, timeout=60)
exchange_api.call(place_order_on_exchange, order)
```

### Rate Limiting

**Token Bucket Algorithm**:
```python
class TokenBucket:
    def __init__(self, capacity, refill_rate):
        self.capacity = capacity
        self.tokens = capacity
        self.refill_rate = refill_rate  # tokens per second
        self.last_refill = time.time()
    
    def consume(self, tokens=1):
        self._refill()
        
        if self.tokens >= tokens:
            self.tokens -= tokens
            return True
        return False
    
    def _refill(self):
        now = time.time()
        elapsed = now - self.last_refill
        tokens_to_add = elapsed * self.refill_rate
        self.tokens = min(self.capacity, self.tokens + tokens_to_add)
        self.last_refill = now

# Usage (Redis-based)
def is_rate_limited(user_id, action):
    key = f"rate_limit:{user_id}:{action}"
    
    # Using Redis for distributed rate limiting
    current = redis.get(key)
    if current is None:
        redis.setex(key, 60, 1)  # 1 request, 60 sec window
        return False
    
    if int(current) >= RATE_LIMITS[action]:
        return True
    
    redis.incr(key)
    return False
```

### Monitoring & Alerting

**Key Metrics**:
```
Business Metrics:
- Orders per second
- Order fill rate (%)
- Average order latency
- Revenue per day
- Active users

Technical Metrics:
- API response time (p50, p95, p99)
- Error rate (%)
- Database query time
- Cache hit rate (%)
- WebSocket connections

Infrastructure Metrics:
- CPU utilization (%)
- Memory usage (%)
- Disk I/O
- Network bandwidth
```

**Alerting Rules**:
```yaml
alerts:
  - name: HighOrderLatency
    condition: p99_latency > 1000ms
    severity: critical
    action: page_oncall

  - name: DatabaseHighCPU
    condition: cpu > 80% for 5 minutes
    severity: warning
    action: auto_scale

  - name: LowCacheHitRate
    condition: cache_hit_rate < 70%
    severity: warning
    action: notify_slack

  - name: OrderFailureRate
    condition: error_rate > 1%
    severity: critical
    action: page_oncall
```

**Logging**:
```
Structured Logging (JSON):
{
  "timestamp": "2025-01-10T14:30:45Z",
  "level": "INFO",
  "service": "trading-service",
  "user_id": "usr_123",
  "order_id": "ord_abc",
  "action": "ORDER_PLACED",
  "latency_ms": 245,
  "status": "SUCCESS"
}

ELK Stack:
- Elasticsearch: Store logs
- Logstash: Parse and ingest
- Kibana: Visualize and search
```

---

## Security & Compliance

### Authentication & Authorization

**Multi-Factor Authentication**:
```
1. Primary: Password (bcrypt hashed)
2. Secondary: SMS OTP / Authenticator App (TOTP)
3. Biometric: Face ID / Fingerprint (on mobile)

Login Flow:
1. User enters email + password
2. Backend validates credentials
3. If valid, send OTP to phone
4. User enters OTP
5. Backend validates OTP
6. Issue JWT token (1 hour expiry)
7. Issue refresh token (7 days expiry)
```

**JWT Token Structure**:
```json
{
  "sub": "usr_12345",
  "email": "user@example.com",
  "kyc_verified": true,
  "roles": ["trader"],
  "iat": 1704723845,
  "exp": 1704727445
}
```

**Authorization**:
```python
@require_auth
@require_kyc_verified
def place_order(user_id, order_data):
    # Only KYC verified users can trade
    pass

@require_auth
@require_role("admin")
def view_all_orders():
    # Only admins can view all orders
    pass
```

### Data Encryption

**In Transit**:
- TLS 1.3 for all API calls
- Certificate pinning for mobile apps
- WebSocket over TLS (wss://)

**At Rest**:
```
Database:
- Encrypt sensitive fields (SSN, bank account)
- Use AES-256 encryption
- Key management via AWS KMS

S3:
- Server-side encryption (SSE-S3)
- Bucket policies to restrict access

Backups:
- Encrypted before uploading
- Separate encryption keys for backups
```

**PII Handling**:
```python
# Encrypt sensitive data before storing
def store_bank_account(user_id, account_number, routing_number):
    encrypted_account = encrypt(account_number, kms_key_id)
    encrypted_routing = encrypt(routing_number, kms_key_id)
    
    db.execute("""
        INSERT INTO linked_bank_accounts 
        (user_id, account_number_encrypted, routing_number_encrypted)
        VALUES (?, ?, ?)
    """, user_id, encrypted_account, encrypted_routing)

# Mask when displaying
def display_account_number(encrypted_account):
    account = decrypt(encrypted_account, kms_key_id)
    return f"****{account[-4:]}"  # Show last 4 digits only
```

### Compliance

**KYC/AML Requirements**:
```
Tier 1 (Basic):
- Name, DOB, Address
- Email, Phone verification
- Trade limit: $1,000/day

Tier 2 (Standard):
- Government ID (Driver's License, Passport)
- Selfie verification
- Address proof
- Trade limit: $50,000/day

Tier 3 (Enhanced):
- Income verification
- Source of funds
- Tax documents (W2, 1099)
- Trade limit: Unlimited
```

**AML Monitoring**:
```python
def monitor_suspicious_activity(user_id):
    # Red flags
    alerts = []
    
    # Large cash deposits
    deposits_7d = sum_deposits_last_7_days(user_id)
    if deposits_7d > 50000:
        alerts.append("LARGE_DEPOSIT_7D")
    
    # Rapid in-out
    if rapid_deposit_withdraw_pattern(user_id):
        alerts.append("RAPID_IN_OUT")
    
    # Structuring (avoiding $10K reporting threshold)
    if multiple_deposits_just_under_10k(user_id):
        alerts
