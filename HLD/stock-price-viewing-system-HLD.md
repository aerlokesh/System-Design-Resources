# Global Stock Price Viewing System - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [API Design](#api-design)
9. [Price Update Flow](#price-update-flow)
10. [Deep Dives](#deep-dives)
11. [Scalability & Reliability](#scalability--reliability)
12. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a real-time stock price viewing system that allows users worldwide to:
- View current stock prices from global exchanges (NYSE, NASDAQ, LSE, TSE, HKEX, etc.)
- Receive real-time price updates with minimal latency
- Access historical price data (intraday, daily, weekly, monthly)
- View charts and technical indicators
- Track portfolios and watchlists
- Set price alerts
- View market depth (order book) and trade history
- Support millions of concurrent users
- Handle high-frequency updates during market hours
- Ensure data accuracy and consistency

### Scale Requirements
- **10 million concurrent users** during peak trading hours
- **50,000 stocks** tracked across global exchanges
- **Price updates**: Every 1-5 seconds per stock during market hours
- **100 million price updates per minute** at peak
- **Historical data**: 10 years of minute-level data
- **WebSocket connections**: 10M concurrent
- **Update latency**: < 200ms from exchange to user
- **API latency**: < 50ms for price queries
- **Uptime**: 99.99% during market hours

---

## Functional Requirements

### Must Have (P0)

1. **Real-Time Price Display**
   - Current price (last traded price)
   - Bid/ask prices
   - Price change ($ and %)
   - Volume traded
   - Open, high, low, close (OHLC)
   - Market status (open, closed, pre-market, after-hours)

2. **Market Data Integration**
   - Connect to multiple exchanges via data feeds
   - NYSE, NASDAQ (US)
   - LSE (London), Euronext (Europe)
   - TSE (Tokyo), HKEX (Hong Kong)
   - Support different data feed protocols

3. **Historical Data**
   - Intraday charts (1min, 5min, 15min, 1hour)
   - Daily charts (1day, 1week, 1month)
   - OHLCV data (Open, High, Low, Close, Volume)
   - 10 years of historical data
   - Adjustments for splits and dividends

4. **Charting & Visualization**
   - Candlestick charts
   - Line charts
   - Technical indicators (MA, RSI, MACD, Bollinger Bands)
   - Volume bars
   - Multiple timeframes
   - Chart comparison (overlay multiple stocks)

5. **Portfolio & Watchlist**
   - Create custom watchlists
   - Track portfolio holdings
   - Real-time P&L (Profit & Loss)
   - Portfolio performance tracking
   - Alerts on watchlist stocks

6. **Price Alerts**
   - Price crosses threshold (above/below)
   - Percentage change alerts
   - Volume spike alerts
   - Technical indicator alerts
   - Push notifications, email, SMS

7. **Market Depth (Level 2)**
   - Order book (bids and asks)
   - Market depth chart
   - Recent trades
   - Time and sales data
   - Liquidity analysis

8. **Search & Discovery**
   - Search stocks by symbol, name, sector
   - Trending stocks
   - Top gainers/losers
   - Most active (by volume)
   - Sector performance

### Nice to Have (P1)
- News integration (stock-related news)
- Fundamental data (P/E ratio, market cap, dividends)
- Analyst ratings and price targets
- Options chain data
- Crypto currency support
- Forex rates
- Commodity prices
- Social sentiment analysis
- Paper trading (simulation)

---

## Non-Functional Requirements

### Performance
- **Price update latency**: < 200ms from exchange to user
- **API response time**: < 50ms for price queries
- **WebSocket message delivery**: < 100ms
- **Historical data query**: < 500ms
- **Chart rendering**: < 1 second

### Scalability
- Support 10M concurrent users
- Handle 50K stocks with real-time updates
- 100M price updates per minute
- 10M WebSocket connections
- Scale horizontally

### Availability
- **99.99% uptime** during market hours (15.5 hours/day)
- **99.9% uptime** outside market hours
- Multi-region deployment
- Automatic failover
- Graceful degradation

### Latency
- **Real-time updates**: < 200ms (exchange → user)
- **Price quote**: < 50ms
- **Historical query**: < 500ms
- **Search**: < 100ms

### Consistency
- **Strong consistency** for price data (no stale prices)
- **Eventual consistency** for historical aggregates (acceptable)
- **Sequential consistency** for price updates (maintain order)

### Data Accuracy
- **100% accuracy** for price data
- Validate all data from exchanges
- Detect and correct anomalies
- Audit trail for price changes

---

## Capacity Estimation

### Traffic Estimates

```
Concurrent Users: 10M (during peak trading hours)
Stocks tracked: 50,000

Active trading hours per day:
- US markets: 6.5 hours (9:30 AM - 4 PM ET)
- Asian markets: 6 hours
- European markets: 8.5 hours
- Total: ~20 hours (overlapping markets)

Price updates per stock:
- Liquid stocks: Every 1 second
- Less liquid: Every 5 seconds
- Average: Every 2 seconds

Total price updates per minute:
- 50,000 stocks / 2 sec = 25,000 updates/sec = 1.5M updates/minute

Peak (market open, high volatility):
- 3x normal = 75,000 updates/sec = 4.5M updates/minute

WebSocket messages:
- 10M concurrent users
- Each user watching 5 stocks average
- 50M active subscriptions
- Price update broadcast: 50M messages per update
- At 25K updates/sec: 1.25B messages/sec

API Queries (REST):
- Users refreshing, searching: 50K QPS
- Historical data queries: 10K QPS
- Total: 60K QPS
```

### Storage Estimates

```
Stock metadata:
- 50,000 stocks × 5 KB = 250 MB

Real-time prices (in-memory):
- 50,000 stocks × 1 KB (current state) = 50 MB

Historical data (time-series):
1-minute OHLCV data:
- Per stock per minute: 50 bytes (OHLCV = 5 floats)
- Per stock per day: 50 bytes × 390 min (6.5 hrs) = 19.5 KB
- Per stock per year: 19.5 KB × 252 days = 4.9 MB
- 50,000 stocks × 4.9 MB = 245 GB/year
- 10 years: 2.45 TB

Daily OHLCV data:
- Per stock per day: 50 bytes
- Per stock per year: 50 bytes × 252 = 12.6 KB
- 50,000 stocks × 12.6 KB = 630 MB/year
- 10 years: 6.3 GB

Total storage:
- Metadata: 0.25 GB
- Real-time: 0.05 GB
- 1-minute data (10 years): 2.45 TB
- Daily data (10 years): 6.3 GB
- Indexes: 500 GB
- Total: ~3 TB

With replication (3x): 9 TB
```

### Processing Requirements

```
Price Update Processing:
- Updates per second: 75,000 (peak)
- Processing per update: 5ms (validate, normalize, publish)
- CPU cores: 75,000 × 0.005 = 375 cores

WebSocket Broadcasting:
- Messages per second: 1.25B (if all subscriptions active)
- Message size: 200 bytes
- Bandwidth: 1.25B × 200 bytes = 250 GB/s = 2 Tbps (unrealistic!)

Optimization with fan-out:
- Group users by subscriptions
- Broadcast to groups (not individual users)
- Effective messages: 25K updates/sec × 1000 users/update = 25M msg/sec
- Bandwidth: 25M × 200 bytes = 5 GB/s = 40 Gbps
```

### Network Bandwidth

```
Inbound (from exchanges):
- 75,000 updates/sec × 500 bytes = 37.5 MB/s = 300 Mbps

Outbound (WebSocket to users):
- 25M messages/sec × 200 bytes = 5 GB/s = 40 Gbps

REST API:
- 60K QPS × 2 KB response = 120 MB/s = 960 Mbps

Total: ~41 Gbps
```

### Cost Estimation

```
Monthly Costs (approximate):

Compute:
- Data feed processors: 50 instances × $200 = $10K
- WebSocket servers: 500 instances × $200 = $100K
- API servers: 100 instances × $150 = $15K

Storage:
- Time-series DB (SSD): 5 TB × $200/TB = $1K
- Cache (Redis): 100 GB × $5/GB = $0.5K

Network:
- Data transfer: 50 TB/month × $0.05/GB = $2.5K

Data Feed Costs:
- Exchange data feeds: $50K/month (major expense!)

Total: ~$179K per month

Revenue (if trading platform):
- Commission on trades: $1-5 per trade
- Premium subscriptions: $10-100/month/user
- Market data subscriptions: $50-500/month/user
```

---

## High-Level Architecture

```
            [Global Stock Exchanges]
                      |
    ┌─────────────────┼─────────────────┐
    ↓                 ↓                 ↓
[NYSE/NASDAQ]    [LSE/Euronext]    [TSE/HKEX]
(US exchanges)   (EU exchanges)    (Asia exchanges)
    |                 |                 |
    └─────────────────┼─────────────────┘
                      ↓
            [Market Data Providers]
            (Bloomberg, Reuters, IEX)
                      |
                      ↓
            [Data Feed Aggregators]
            (FIX, WebSocket, REST)
                      |
        ┌─────────────┼─────────────┐
        ↓             ↓             ↓
  [Feed Handler] [Feed Handler] [Feed Handler]
  (US region)    (EU region)    (APAC region)
        |             |             |
        └─────────────┼─────────────┘
                      ↓
            [Message Bus - Kafka]
            (Price update stream)
                      |
        ┌─────────────┼─────────────────────┐
        ↓             ↓                     ↓
  [Price        [Historical          [Alert
   Processor]    Aggregator]          Engine]
  (Normalize)   (Store OHLCV)        (Check triggers)
        |             |                     |
        └─────────────┼─────────────────────┘
                      ↓
            [Data Storage Layer]
                      |
    ┌─────────────────┼─────────────────┐
    ↓                 ↓                 ↓
[Real-Time       [Time-Series       [Metadata DB]
 Cache]           DB]                (PostgreSQL)
(Redis)          (InfluxDB/         (Stocks, sectors,
(Current prices) TimescaleDB)        exchanges)
                 (Historical OHLCV)
    |                 |                 |
    └─────────────────┼─────────────────┘
                      ↓
            [API Gateway]
                      |
        ┌─────────────┼─────────────┐
        ↓             ↓             ↓
  [REST API]    [WebSocket      [GraphQL]
  (Price        Server]         (Flexible
   quotes)      (Real-time      queries)
                 updates)
        |             |             |
        └─────────────┼─────────────┘
                      ↓
            [Client Applications]
                      |
        ┌─────────────┼─────────────┐
        ↓             ↓             ↓
  [Web App]     [Mobile Apps]  [Desktop App]
  (React)       (iOS, Android)  (Trading terminal)
```

### Key Architectural Decisions

1. **WebSocket for Real-Time Updates**
   - Push-based updates to clients
   - Bi-directional communication
   - Low latency (< 100ms)
   - Efficient for high-frequency updates

2. **Redis for Real-Time Cache**
   - In-memory storage for current prices
   - Sub-millisecond reads
   - Pub/Sub for price distribution
   - High throughput

3. **Time-Series DB for Historical Data**
   - Optimized for OHLCV data
   - Fast range queries
   - Efficient compression
   - Built-in aggregations

4. **Kafka for Data Pipeline**
   - Reliable message delivery
   - Multiple consumers
   - Replay capability
   - Decouples ingestion from processing

5. **Multi-Region Deployment**
   - Feed handlers near exchanges
   - Serve users from nearest region
   - Reduce latency
   - High availability

---

## Core Components

### 1. Market Data Feed Integration

**Purpose**: Ingest price data from stock exchanges

**Data Feed Protocols**:

**A. FIX Protocol** (Financial Information eXchange):
```
Industry standard for financial data

Message types:
- Market Data Snapshot (full refresh)
- Market Data Incremental (updates only)
- Trade execution reports
- Order book updates

Format: Tag-value pairs
Example: 35=D|49=SENDER|56=RECEIVER|55=AAPL|31=150.25|...

Benefits:
+ Industry standard
+ Real-time
+ Comprehensive

Complexity: High
Latency: 10-50ms
```

**B. WebSocket Feeds**:
```
Modern exchanges provide WebSocket APIs

Example: IEX Cloud, Polygon.io
- Subscribe to symbols
- Receive JSON messages
- Real-time quotes and trades

Format: JSON
{
  "symbol": "AAPL",
  "price": 150.25,
  "volume": 1000,
  "timestamp": 1705363200123
}

Benefits:
+ Easy to integrate
+ Low latency
+ HTTP-based

Latency: 50-200ms
```

**C. REST API Polling**:
```
For less time-sensitive data

Poll endpoints every N seconds:
- GET /quotes?symbols=AAPL,GOOGL,MSFT
- Returns current prices

Benefits:
+ Simple
+ Wide availability
+ No persistent connection

Drawbacks:
- Higher latency (polling interval)
- Not real-time
- More API calls

Use for: Historical data, metadata
```

**Feed Handler Architecture**:
```
[Exchange Data Feed]
        ↓
[Feed Handler Service]
    ├─ Connection manager
    ├─ Protocol parser (FIX, JSON)
    ├─ Data normalizer
    ├─ Validator
    └─ Publisher
        ↓
[Kafka: raw-prices topic]

Deployed regionally:
- US handlers → NYSE, NASDAQ
- EU handlers → LSE, Euronext
- Asia handlers → TSE, HKEX

Benefits:
- Low latency (near exchange)
- Regional failover
- Load distribution
```

**Data Normalization**:
```
Exchange formats differ:
- NYSE: Price in cents (15025 = $150.25)
- LSE: Price in pence (15025 = £150.25)
- TSE: Price in yen (15025 = ¥150.25)

Normalize to standard format:
{
  "symbol": "AAPL",
  "price_usd": 150.25,
  "currency": "USD",
  "exchange": "NASDAQ",
  "timestamp": 1705363200123,
  "volume": 1000
}
```

### 2. Price Processing Pipeline

**Purpose**: Process and distribute price updates

**Architecture**:
```
[Kafka: raw-prices]
        ↓
[Price Processor (Flink)]
    ├─ Validate data
    ├─ Detect anomalies
    ├─ Calculate derived fields (change, change%)
    ├─ Enrich with metadata
    └─ Deduplicate
        ↓
[Multiple Outputs]
    ├─ Redis (real-time cache)
    ├─ Time-Series DB (historical)
    ├─ WebSocket broadcaster
    └─ Alert engine
```

**Processing Steps**:

**1. Validation**:
```
Check data quality:
- Price within reasonable range (no outliers)
- Timestamp is recent (< 5 seconds old)
- Symbol exists in database
- Volume is non-negative
- Exchange is open (market hours check)

Reject invalid data:
- Log error
- Alert ops team
- Don't propagate
```

**2. Anomaly Detection**:
```
Detect unusual price movements:
- Price change > 10% in 1 minute (potential error or halt)
- Volume spike > 100x average
- Price crosses circuit breaker

Action:
- Flag for review
- Still display (with warning)
- Alert compliance team
```

**3. Derived Calculations**:
```
Calculate additional fields:
- change_amount = current_price - previous_close
- change_percent = (change_amount / previous_close) × 100
- vwap = Σ(price × volume) / Σ(volume)  (Volume Weighted Average Price)
- spread = ask_price - bid_price

Enrich update with these fields
```

**4. Deduplication**:
```
Problem: Same price update from multiple sources

Solution:
- Track last update per stock (Redis)
- Check timestamp
- If duplicate (same timestamp): Skip
- If newer: Process

Deduplication window: 1 second
```

### 3. Real-Time Cache (Redis)

**Purpose**: Store current state of all stocks for fast access

**Data Structure**:

**Current Price**:
```
Key: stock:{symbol}:current
Value (hash):
- symbol: AAPL
- price: 150.25
- change: +2.50
- change_percent: +1.69
- volume: 50000000
- open: 148.00
- high: 151.00
- low: 147.50
- bid: 150.24
- ask: 150.26
- last_update: 1705363200123

TTL: 1 hour (auto-refresh on each update)
```

**Watchlist**:
```
Key: watchlist:{user_id}
Value (set): [AAPL, GOOGL, MSFT, TSLA, AMZN]

Operations:
- SADD: Add stock to watchlist
- SREM: Remove stock
- SMEMBERS: Get all stocks in watchlist
```

**Price Alerts**:
```
Key: alerts:{user_id}
Value (list): [
  {
    "symbol": "AAPL",
    "condition": "price > 160",
    "notification": "push"
  }
]

Check alerts on every price update
```

**Market Status**:
```
Key: market:status:{exchange}
Value (string): open | closed | pre-market | after-hours

TTL: 5 minutes (refresh periodically)

Used to: Determine if updates expected
```

### 4. WebSocket Server

**Purpose**: Push real-time price updates to connected clients

**Architecture**:
```
[WebSocket Server Cluster]
    ├─ Connection manager (10M connections)
    ├─ Subscription manager
    ├─ Message broadcaster
    └─ Load balancer (sticky sessions)
```

**Connection Management**:
```
Client connects:
1. ws://api.stocks.com/stream
2. Authenticate (JWT token)
3. Assign to WebSocket server (sticky session)
4. Keep-alive heartbeat (every 30 seconds)

Server tracks:
- Connection ID → User ID mapping
- User ID → Subscriptions mapping
- Active connections per server
```

**Subscription Model**:
```
Client subscribes:
{
  "action": "subscribe",
  "symbols": ["AAPL", "GOOGL", "MSFT"]
}

Server:
1. Validate symbols
2. Add to user's subscription list
3. Start receiving updates for these symbols
4. Broadcast updates via WebSocket

Client unsubscribes:
{
  "action": "unsubscribe",
  "symbols": ["AAPL"]
}
```

**Message Broadcasting**:
```
Price update arrives (from Kafka):
{
  "symbol": "AAPL",
  "price": 150.25,
  "change": +2.50,
  "timestamp": 1705363200123
}

Broadcast to all subscribed users:
1. Query: Who's subscribed to AAPL?
2. Find all connections (could be 1M users)
3. Send message to each connection
4. Track delivery success/failure

Optimization: Fan-out architecture
- Group servers by symbol
- Each server handles subset of users
- Parallel broadcasting
```

**Scalability**:
```
WebSocket servers:
- Each server: 20,000 connections
- 10M total connections / 20K = 500 servers

Connection distribution:
- Consistent hashing by user_id
- Sticky sessions (user always connects to same server)
- Load balancer routes based on hash
```

### 5. Time-Series Database

**Purpose**: Store historical price data

**Schema**:

**OHLCV Data** (1-minute resolution):
```
Table: stock_prices_1min
Partition: By date (daily partitions)
Order: (symbol, timestamp)

Columns:
- symbol: VARCHAR
- timestamp: TIMESTAMP
- open: DECIMAL(10, 4)
- high: DECIMAL(10, 4)
- low: DECIMAL(10, 4)
- close: DECIMAL(10, 4)
- volume: BIGINT

Compression: 10:1 typical
```

**Continuous Aggregates** (Pre-computed):
```
5-minute OHLCV:
- Aggregate from 1-minute data
- Update every 5 minutes

Hourly OHLCV:
- Aggregate from 5-minute data
- Update every hour

Daily OHLCV:
- Aggregate from hourly data
- Update at market close

Benefits:
- Fast chart rendering
- Reduce query load
- Efficient storage
```

**Query Patterns**:
```
Get last 7 days (1-minute data):
SELECT * FROM stock_prices_1min
WHERE symbol = 'AAPL'
  AND timestamp > NOW() - INTERVAL '7 days'
ORDER BY timestamp ASC;

Get last 1 year (daily data):
SELECT * FROM stock_prices_daily
WHERE symbol = 'AAPL'
  AND timestamp > NOW() - INTERVAL '1 year'
ORDER BY timestamp ASC;

Response time: < 500ms
```

**Retention Policy**:
```
1-minute data: 90 days
5-minute data: 1 year
Hourly data: 5 years
Daily data: 10+ years

Auto-delete old data:
- Saves storage
- Maintains query performance
```

### 6. Alert Engine

**Purpose**: Monitor prices and trigger user alerts

**Alert Types**:

**1. Price Alerts**:
```
Conditions:
- Price above $X
- Price below $X
- Price crosses moving average
- Percentage change > X%

Example:
{
  "user_id": "user_123",
  "symbol": "AAPL",
  "condition": "price > 160",
  "notification": ["push", "email"]
}

Check: Every price update for AAPL
```

**2. Volume Alerts**:
```
Conditions:
- Volume > X shares
- Volume spike (> 2x average)

Example:
{
  "symbol": "TSLA",
  "condition": "volume > 100000000",
  "notification": "push"
}
```

**3. Technical Indicator Alerts**:
```
Conditions:
- RSI > 70 (overbought)
- RSI < 30 (oversold)
- MACD crossover
- Bollinger Band breach

Example:
{
  "symbol": "GOOGL",
  "condition": "RSI > 70",
  "notification": "email"
}
```

**Alert Evaluation**:
```
For each price update:
1. Query active alerts for this symbol
2. Evaluate conditions
3. If triggered:
   - Fire notification
   - Mark alert as triggered
   - Apply cooldown (don't spam)
4. Track alert history

Processing time: < 50ms
```

**Alert Cooldown**:
```
After alert triggers:
- Cooldown period: 1 hour
- Don't re-trigger same alert
- Prevents alert spam

User can:
- Dismiss alert
- Reset cooldown
- Delete alert
```

### 7. Portfolio & Watchlist Service

**Purpose**: Track user portfolios and watchlists

**Watchlist**:
```
User's custom list of stocks to monitor

Storage:
{
  "user_id": "user_123",
  "watchlist_id": "watchlist_1",
  "name": "Tech Stocks",
  "symbols": ["AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"],
  "created_at": "2025-01-01",
  "updated_at": "2025-01-16"
}

Max symbols per watchlist: 100
Max watchlists per user: 10
```

**Portfolio Tracking**:
```
User's actual holdings:

{
  "user_id": "user_123",
  "holdings": [
    {
      "symbol": "AAPL",
      "quantity": 100,
      "avg_cost": 145.00,
      "current_price": 150.25,
      "total_cost": 14500.00,
      "current_value": 15025.00,
      "gain_loss": +525.00,
      "gain_loss_percent": +3.62%
    }
  ],
  "total_value": 50000.00,
  "total_cost": 48000.00,
  "total_gain": +2000.00
}

Update: Real-time (as prices update)
```

**Portfolio Performance**:
```
Calculate:
- Total return
- Daily P&L
- Annualized return
- Sharpe ratio
- Max drawdown
- Beta (vs market)

Display as charts:
- Portfolio value over time
- Allocation by sector
- Top performers/losers
```

### 8. Search & Discovery

**Purpose**: Help users find stocks

**Search Index** (Elasticsearch):
```
Index structure:
{
  "symbol": "AAPL",
  "name": "Apple Inc.",
  "exchange": "NASDAQ",
  "sector": "Technology",
  "industry": "Consumer Electronics",
  "market_cap": 2800000000000,
  "description": "Apple designs and manufactures consumer electronics..."
}

Search queries:
- Symbol: "AAPL" → Apple Inc.
- Name: "Apple" → Apple Inc., Applied Materials, ...
- Sector: "Technology" → All tech stocks
```

**Trending Stocks**:
```
Rank by:
- Price change % (top gainers/losers)
- Volume (most active)
- Search frequency (most searched)
- News mentions (most in news)

Update: Every 5 minutes

Display:
- Top 10 gainers
- Top 10 losers
- Most active by volume
```

**Sector Performance**:
```
Aggregate by sector:
- Technology: +2.5%
- Healthcare: +1.2%
- Energy: -0.8%
- Finance: +0.5%

Calculate: Market-cap weighted average
Update: Every minute
```

---

## Database Design

### 1. Stock Metadata (PostgreSQL)

**Stocks Table**:
```
Table: stocks

Columns:
- symbol: VARCHAR(10) PRIMARY KEY
- name: VARCHAR(255)
- exchange: VARCHAR(50)
- sector: VARCHAR(100)
- industry: VARCHAR(100)
- market_cap: BIGINT
- currency: VARCHAR(3)
- country: VARCHAR(50)
- ipo_date: DATE
- is_active: BOOLEAN
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

Indexes:
- exchange, sector
- market_cap DESC
- name (full-text)
```

**Exchanges Table**:
```
Table: exchanges

Columns:
- exchange_code: VARCHAR(10) PRIMARY KEY
- name: VARCHAR(255)
- country: VARCHAR(50)
- timezone: VARCHAR(50)
- trading_hours: JSONB
- currency: VARCHAR(3)
- is_open: BOOLEAN
- next_open: TIMESTAMP
- next_close: TIMESTAMP

Purpose: Track exchange trading hours, status
```

**Holidays Table**:
```
Table: market_holidays

Columns:
- holiday_id: UUID PRIMARY KEY
- exchange_code: VARCHAR(10) (FK)
- date: DATE
- name: VARCHAR(255)
- is_full_day: BOOLEAN
- early_close_time: TIME

Purpose: Track when markets closed
```

### 2. Time-Series Database (InfluxDB/TimescaleDB)

**1-Minute OHLCV**:
```
Measurement: stock_prices_1min

Tags (indexed):
- symbol
- exchange

Fields:
- open: FLOAT
- high: FLOAT
- low: FLOAT
- close: FLOAT
- volume: INT64

Timestamp: Primary sort key

Retention: 90 days
Compression: 10:1
```

**Daily OHLCV**:
```
Measurement: stock_prices_daily

Same structure as 1-minute

Retention: 10+ years
Use for: Long-term charts, backtesting
```

**Continuous Aggregates** (Materialized Views):
```
CREATE CONTINUOUS AGGREGATE stock_prices_5min
WITH (timescaledb.continuous) AS
SELECT
  symbol,
  time_bucket('5 minutes', timestamp) AS bucket,
  first(open, timestamp) AS open,
  max(high) AS high,
  min(low) AS low,
  last(close, timestamp) AS close,
  sum(volume) AS volume
FROM stock_prices_1min
GROUP BY symbol, bucket;

Auto-updates as new data arrives
Fast queries for 5-min charts
```

### 3. Real-Time Cache (Redis)

**Current Prices**:
```
Key: stock:{symbol}
Type: Hash
Fields:
- price, change, change_percent, volume
- open, high, low, close
- bid, ask, spread
- timestamp

TTL: 1 hour (refreshed on updates)

Purpose: Ultra-fast price lookups
Latency: < 1ms
```

**Symbol-to-Subscribers Mapping**:
```
Key: subscribers:{symbol}
Type: Set
Members: [user_123, user_456, user_789, ...]

Purpose: Find who's subscribed to each stock
Update: When user subscribes/unsubscribes

Used for: Broadcasting price updates
```

**Pub/Sub Channels**:
```
Channel: stock_updates:{symbol}

Publishers: Price processors
Subscribers: WebSocket servers

When price updates:
1. Publish to channel
2. All subscribed WebSocket servers receive
3. Each server broadcasts to its connected users
```

---

## API Design

### REST APIs

**Price Quote APIs**:
```
GET /api/v1/quote?symbol=AAPL
- Get current price for single stock
- Response: Current price, OHLC, volume
- Latency: < 50ms

GET /api/v1/quotes?symbols=AAPL,GOOGL,MSFT
- Batch quote for multiple stocks (up to 100)
- Response: Array of quotes
- Latency: < 100ms

GET /api/v1/market/snapshot
- Get overview of entire market
- Indices (S&P 500, NASDAQ, DOW)
- Top movers
- Latency: < 200ms
```

**Historical Data APIs**:
```
GET /api/v1/history/{symbol}?from={date}&to={date}&resolution={1m|5m|1h|1d}
- Get historical OHLCV data
- Max range: 1 year for minute data
- Response: Array of OHLCV bars
- Latency: < 500ms

GET /api/v1/chart/{symbol}?period={1d|5d|1m|3m|1y|5y}
- Pre-configured chart data
- Returns appropriate resolution
- 1d: 1-minute data
- 1y: daily data
```

**Watchlist APIs**:
```
GET /api/v1/watchlists
- Get user's watchlists
- Returns list with symbols

POST /api/v1/watchlists
- Create new watchlist
- Body: {name, symbols[]}

PUT /api/v1/watchlists/{id}/symbols
- Add/remove symbols
- Body: {add: [], remove: []}

DELETE /api/v1/watchlists/{id}
- Delete watchlist
```

**Alert APIs**:
```
GET /api/v1/alerts
- Get user's price alerts

POST /api/v1/alerts
- Create new alert
- Body: {symbol, condition, notification_type}

DELETE /api/v1/alerts/{id}
- Delete alert
```

### WebSocket API

**Connection**:
```
ws://api.stocks.com/stream?token={jwt_token}

On connect:
- Server sends: {"type": "connected", "session_id": "abc123"}
```

**Subscribe to Price Updates**:
```
Client → Server:
{
  "type": "subscribe",
  "symbols": ["AAPL", "GOOGL", "MSFT"]
}

Server → Client (acknowledgment):
{
  "type": "subscribed",
  "symbols": ["AAPL", "GOOGL", "MSFT"]
}

Server → Client (price updates):
{
  "type": "price_update",
  "symbol": "AAPL",
  "price": 150.25,
  "change": +2.50,
  "change_percent": +1.69,
  "volume": 50000000,
  "timestamp": 1705363200123
}
```

**Unsubscribe**:
```
Client → Server:
{
  "type": "unsubscribe",
  "symbols": ["AAPL"]
}
```

**Heartbeat**:
```
Server → Client (every 30 seconds):
{
  "type": "ping",
  "timestamp": 1705363200
}

Client → Server:
{
  "type": "pong",
  "timestamp": 1705363200
}

Purpose: Keep connection alive, detect disconnections
```

---

## Price Update Flow

### End-to-End Flow: AAPL Price Update

```
T=0ms: Trade executes on NASDAQ
- AAPL trades at $150.25
- 1,000 shares
        ↓
T=10ms: Exchange publishes to data feed
- FIX message or WebSocket update
        ↓
T=30ms: Feed Handler (US region) receives
- Parse message
- Extract: symbol=AAPL, price=150.25, volume=1000
        ↓
T=35ms: Normalize and validate
- Validate price (within range)
- Check market hours (NASDAQ open)
- Normalize format
        ↓
T=40ms: Publish to Kafka
Topic: raw-prices
Partition: By symbol (AAPL → partition 5)
        ↓
T=50ms: Price Processor (Flink) consumes
- Calculate change: +2.50 (+1.69%)
- Calculate VWAP
- Check for anomalies
        ↓
T=60ms: Multiple outputs
    ├─ Update Redis cache
    ├─ Write to Time-Series DB
    ├─ Publish to WebSocket servers (Pub/Sub)
    └─ Check alerts
        ↓
T=70ms: Redis cache updated
Key: stock:AAPL
Value: {price: 150.25, change: +2.50, ...}
        ↓
T=80ms: WebSocket servers receive (via Pub/Sub)
- 500 servers subscribed to AAPL updates
- Each receives price update
        ↓
T=90ms: Broadcast to connected users
- Server 1: 5,000 users subscribed to AAPL
- Server 2: 4,800 users subscribed to AAPL
- ... (all 500 servers)
- Total: 1M users receive update
        ↓
T=100ms: Client receives WebSocket message
{
  "type": "price_update",
  "symbol": "AAPL",
  "price": 150.25,
  "change": +2.50
}
        ↓
T=110ms: Client UI updates
- Display new price
- Update chart
- Update portfolio value
- Check if alert triggered

Total latency: 110ms (exchange → user screen)
```

---

## Deep Dives

### 1. Handling High-Frequency Updates

**Challenge**: 75,000 price updates per second at peak

**Problem**:
```
Naive approach: Update every connected user immediately
- 75K updates/sec
- 10M users (not all subscribed to each stock)
- Average 5 stocks per user
- Effective: 75K × (10M/50K) × (5/50K) = Too many!

Bottleneck: Broadcasting to millions of users
```

**Solution: Smart Fan-Out**:
```
Optimization 1: Subscription filtering
- Only send to users subscribed to that stock
- AAPL update: 1M subscribed users (not 10M)
- 75x reduction

Optimization 2: Batching
- Batch multiple updates in 100ms window
- Send as single message
- Reduce network overhead

Optimization 3: Compression
- Compress WebSocket messages
- Save 50-70% bandwidth
- Trade: Slight CPU for network savings

Optimization 4: Regional distribution
- 500 WebSocket servers
- Each handles 20K users
- Parallel broadcasting
- No single bottleneck
```

**Rate Limiting**:
```
Per-connection limits:
- Max 100 messages/second to single user
- If exceeded: Sample updates (send every Nth)
- Prevents overwhelming slow clients

Per-server limits:
- Track outbound bandwidth
- Throttle if approaching limit
- Ensure fair distribution
```

### 2. Data Consistency During Exchange Transitions

**Challenge**: Ensure accurate prices across time zones and exchanges

**Scenario**: Stock listed on multiple exchanges
```
AAPL listed on:
- NASDAQ (primary)
- LSE (London)
- Deutsche Börse (Frankfurt)

Prices may differ:
- NASDAQ: $150.25 (USD)
- LSE: £118.50 (GBP) = $151.00 USD
- Frankfurt: €140.20 (EUR) = $149.80 USD

Which price to show?
```

**Solution: Primary Exchange**:
```
Designate primary exchange:
- AAPL primary: NASDAQ
- Show NASDAQ price as canonical
- Other exchanges: Show with disclaimer

Currency conversion:
- Convert all to USD
- Use real-time FX rates
- Update FX rates every minute
```

**After-Hours Trading**:
```
Regular hours: 9:30 AM - 4:00 PM ET
After-hours: 4:00 PM - 8:00 PM ET
Pre-market: 4:00 AM - 9:30 AM ET

Tracking:
- Show regular hours price (close)
- Show after-hours price separately
- Label clearly: "After Hours: $150.50 (+0.25)"

User setting: Show/hide extended hours
```

### 3. WebSocket Connection Management at Scale

**Challenge**: Maintain 10M concurrent WebSocket connections

**Connection Lifecycle**:
```
Connect → Authenticate → Subscribe → Receive Updates → Disconnect

Connection duration:
- Average session: 30 minutes
- Heartbeat: Every 30 seconds
- Disconnect on 3 missed heartbeats

Connection pool:
- 10M active connections
- Churn: 5% per minute (500K reconnects/min)
```

**Memory Management**:
```
Per connection memory:
- Connection state: 1 KB
- Subscription data: 500 bytes (average 5 stocks)
- Buffers: 4 KB
Total: ~5.5 KB per connection

10M connections × 5.5 KB = 55 GB total

Distributed across 500 servers:
- 55 GB / 500 = 110 MB per server
- Manageable
```

**Sticky Sessions**:
```
Why needed:
- User subscriptions stored on specific server
- Reconnect should go to same server
- Avoid subscription re-setup

Implementation:
- Consistent hashing by user_id
- Load balancer routes based on hash
- Same user → same server (unless server down)

Failover:
- If server dies, users reconnect to different server
- Re-subscribe automatically
- Seamless experience
```

**Connection Draining**:
```
For server maintenance:
1. Mark server as draining
2. Stop accepting new connections
3. Notify connected users to reconnect
4. Wait for connections to close (or force after 5 min)
5. Shutdown server
6. Rolling update (no downtime)
```

### 4. Circuit Breakers and Trading Halts

**Challenge**: Handle market-wide events

**Circuit Breaker Levels** (NYSE/NASDAQ):
```
Level 1: Market drops 7%
- 15-minute trading halt

Level 2: Market drops 13%
- 15-minute trading halt

Level 3: Market drops 20%
- Trading halted for rest of day

Action:
- Detect halt via data feed
- Update market status: HALTED
- Display message to users
- Stop price updates
- Resume when trading resumes
```

**Individual Stock Halts**:
```
Reasons:
- Pending news
- Order imbalance
- Regulatory review

Detection:
- Exchange sends halt message
- No new trades
- Last price frozen

Display:
- Show "TRADING HALTED" banner
- Show last price before halt
- Show reason (if available)
- Notify subscribed users
```

**System Response**:
```
During halt:
- Stop processing updates for halted stock
- Keep connection alive
- Display cached data
- Alert users
- Resume when trading resumes

Latency: Display halt message within 1 second
```

### 5. Technical Indicators Calculation

**Challenge**: Calculate indicators in real-time

**Common Indicators**:

**Moving Average (MA)**:
```
Simple Moving Average (SMA):
SMA_20 = Average of last 20 closing prices

Calculation:
- Maintain sliding window of last 20 prices
- On new price: Add to window, remove oldest
- Recalculate average
- O(1) time with proper data structure

Update: Every price update
```

**Relative Strength Index (RSI)**:
```
RSI = 100 - (100 / (1 + RS))
RS = Average Gain / Average Loss (over 14 periods)

Calculation:
- Track last 14 price changes
- Separate gains and losses
- Calculate averages
- Compute RSI

Update: Every price update
Cache result for 1 minute
```

**MACD** (Moving Average Convergence Divergence):
```
MACD = EMA_12 - EMA_26
Signal = EMA_9 of MACD
Histogram = MACD - Signal

Calculation:
- Maintain EMAs (Exponential Moving Averages)
- Update incrementally on each price
- Calculate MACD line
- Calculate signal line

Update: Every price update
```

**Pre-Computation Strategy**:
```
Option A: Calculate on-demand
+ Always fresh
- High CPU on each request
- Slow for complex indicators

Option B: Pre-compute (Chosen)
+ Fast queries (< 10ms)
+ Reduce load
- Requires storage
- Background job

Implementation:
- Batch job calculates every 1 minute
- Store in time-series DB
- Serve from cache
- Acceptable 1-minute staleness
```

### 6. Market Data Feed Reliability

**Challenge**: Ensure no data loss from exchanges

**Redundancy**:
```
Multiple data providers:
- Primary: Direct exchange feed
- Secondary: Market data vendor (Bloomberg, Reuters)
- Tertiary: Alternative feed (IEX, Polygon.io)

Failover:
- Primary fails → Switch to secondary
- Cross-validate between providers
- Alert if discrepancy
```

**Feed Handler Resilience**:
```
Connection management:
- Persistent connection to exchange
- Auto-reconnect on disconnect
- Exponential backoff (1s, 2s, 4s, ...)
- Max retries: Infinite (critical service)

Message buffering:
- Local buffer (10-minute capacity)
- Replay missed messages on reconnect
- Sequence number tracking
- Ensure no gaps

Health monitoring:
- Track last message timestamp
- Alert if no messages > 30 seconds
- Auto-failover to backup feed
```

**Data Validation**:
```
Sanity checks:
- Price not 0 or negative
- Price within 20% of previous (circuit breaker exception)
- Timestamp within 10 seconds
- Volume reasonable

Cross-validation:
- Compare across multiple feeds
- If discrepancy > $0.10: Flag for review
- Use consensus price
```

---

## Scalability & Reliability

### Horizontal Scaling

**Feed Handlers**:
```
Scale by exchange:
- NYSE/NASDAQ: 10 handlers
- LSE: 5 handlers
- TSE: 5 handlers

Each handler independent:
- No coordination needed
- Processes subset of stocks
- Publishes to Kafka

Add capacity: Deploy more handlers
```

**WebSocket Servers**:
```
Current: 500 servers, 10M connections
Each: 20K connections

Scale to 20M connections:
- Deploy 500 more servers
- Load balancer distributes
- Linear scaling

Auto-scaling:
- Scale based on connection count
- Target: 15K connections per server
- Add servers when average > 15K
```

**API Servers**:
```
Stateless REST API servers

Current: 100 servers, 60K QPS
Each: 600 QPS

Scale to 120K QPS:
- Add 100 more servers
- Auto-scaling based on CPU
- Linear scaling
```

**Database Scaling**:
```
PostgreSQL:
- Vertical scaling (larger instances)
- Read replicas (5 replicas)
- Route reads to replicas
- Master for writes only

TimescaleDB:
- Horizontal scaling (sharding)
- Shard by symbol hash
- Distributed queries
- Add nodes for capacity

Redis:
- Redis Cluster
- Add nodes to cluster
- Automatic resharding
- Linear scaling
```

### High Availability

**Multi-Region Deployment**:
```
3 Regions: US-East, EU-West, AP-Southeast

Each region:
- Feed handlers (near exchanges)
- WebSocket servers
- API servers
- Cache (Redis)
- Database replicas

Global:
- Kafka (cross-region replication)
- PostgreSQL (primary in US, replicas in all regions)
- TimescaleDB (partitioned by exchange)

User routing:
- Connect to nearest region (latency)
- Failover to backup region if down
```

**Failover Scenarios**:

**Feed Handler Failure**:
```
Detection: No messages for 30 seconds

Action:
1. Switch to backup feed (secondary provider)
2. Reconnect primary in background
3. Resume from last sequence number
4. No data loss

Recovery: Automatic (< 1 minute)
```

**WebSocket Server Failure**:
```
Detection: Health check fails

Impact: 20K users disconnected

Action:
1. Load balancer removes server
2. Users auto-reconnect (client retry logic)
3. Reconnect to different server
4. Re-subscribe automatically
5. Resume updates

Recovery: < 30 seconds for users
```

**Database Failure**:
```
PostgreSQL primary fails:
- Promote replica to primary (< 30 seconds)
- Update connection strings
- Continue serving

Redis node fails:
- Redis Cluster promotes replica (< 5 seconds)
- Minimal impact
- Cache repopulated

TimescaleDB node fails:
- Query other replicas
- Automatic failover
- No data loss
```

### Performance Optimization

**Caching Strategy**:
```
L1: In-memory cache (per server)
- Current prices for active stocks
- 50 MB, 1-second TTL
- Hit ratio: 70%

L2: Redis cache (shared)
- All stock prices
- 100 GB, 1-hour TTL
- Hit ratio: 95%

L3: TimescaleDB (persistent)
- Historical data
- 3 TB
- Query when cache miss
```

**Query Optimization**:
```
Historical data queries:
- Use pre-computed aggregates (5min, 1hour, 1day)
- Partition pruning (only scan relevant dates)
- Compression (10:1 reduction)
- Indexes on symbol + timestamp

Chart rendering:
- Limit data points (max 1000 points per chart)
- Downsample if needed (1-year chart doesn't need 1-min data)
- Cache rendered data (1-minute TTL)
```

**Network Optimization**:
```
WebSocket compression:
- Enable per-message deflate
- 50-70% bandwidth savings
- Trade CPU for network

Message batching:
- Batch multiple price updates
- Send every 100ms (instead of immediately)
- Reduce message overhead
- Acceptable 100ms delay

Binary protocol:
- Use MessagePack or Protocol Buffers
- Smaller than JSON
- Faster parsing
```

---

## Trade-offs & Alternatives

### 1. WebSocket vs Server-Sent Events (SSE)

**WebSocket** (Chosen):
```
Pros:
+ Bi-directional (client can send commands)
+ Lower overhead after handshake
+ Full-duplex communication
+ Industry standard for real-time

Cons:
- More complex
- Requires special infrastructure
- Proxy/firewall challenges

Use when: Need bi-directional, high frequency
```

**Server-Sent Events (SSE)**:
```
Pros:
+ Simpler (HTTP-based)
+ Auto-reconnect
+ Works through proxies

Cons:
- Uni-directional (server to client only)
- Higher overhead
- Limited browser support

Use when: Simple push notifications, low frequency
```

### 2. Redis vs Memcached for Caching

**Redis** (Chosen):
```
Pros:
+ Rich data structures (Hash, Set, Sorted Set)
+ Pub/Sub support (needed for broadcasting)
+ Persistence (RDB, AOF)
+ Cluster mode

Cons:
- Single-threaded per shard
- More memory overhead

Use when: Need Pub/Sub, complex data structures
```

**Memcached**:
```
Pros:
+ Multi-threaded
+ Lower memory overhead
+ Simple, fast

Cons:
- Only key-value
- No Pub/Sub
- No persistence

Use when: Simple caching, multi-threaded workload
```

### 3. TimescaleDB vs InfluxDB for Time-Series

**TimescaleDB** (Chosen):
```
Pros:
+ PostgreSQL extension (SQL familiar)
+ ACID transactions
+ Joins supported
+ Continuous aggregates

Cons:
- Heavier than pure time-series DB
- Higher resource usage

Use when: Need SQL, transactions, complex queries
```

**InfluxDB**:
```
Pros:
+ Purpose-built for time-series
+ Better compression
+ Lower resource usage
+ InfluxQL (simpler)

Cons:
- No JOINs
- Eventual consistency
- Limited transaction support

Use when: Pure time-series, high throughput
```

### 4. Push vs Pull for Price Updates

**Push (WebSocket)** (Chosen for real-time):
```
Pros:
+ Instant updates (< 100ms)
+ Efficient (no polling)
+ Low latency

Cons:
- Connection management complexity
- Scalability challenges

Use when: Real-time trading, active users
```

**Pull (REST API)** (For less active users):
```
Pros:
+ Simpler
+ Stateless
+ Better for sporadic access

Cons:
- Higher latency (polling interval)
- More API calls
- Wasted polls if no updates

Use when: Dashboards, less frequent updates
```

### 5. Monolithic vs Microservices

**Microservices** (Chosen):
```
Services:
- Feed handler service
- Price processor service
- WebSocket service
- API service
- Alert service
- Portfolio service

Pros:
+ Independent scaling
+ Technology diversity
+ Fault isolation
+ Team autonomy

Cons:
- Operational complexity
- Network latency between services
- Distributed tracing needed

Use when: Large scale, multiple teams
```

---

## Conclusion

This Global Stock Price Viewing System serves **10M concurrent users** with **sub-200ms latency** for **50,000 stocks** worldwide:

**Key Features**:
- Real-time price updates via WebSocket (< 200ms latency)
- 10M concurrent connections supported
- Historical data (10 years, multiple resolutions)
- Real-time portfolio tracking
- Price alerts with multiple notification channels
- 99.99% uptime during market hours

**Core Design Principles**:
1. **Low latency**: Regional feed handlers, Redis cache, WebSocket push
2. **High throughput**: Kafka pipeline, parallel processing
3. **Scalability**: Horizontal scaling, stateless services
4. **Reliability**: Multi-region, redundant feeds, automatic failover
5. **Data accuracy**: Validation, anomaly detection, cross-verification

**Technology Stack**:
- **Feed Integration**: FIX protocol, WebSocket feeds
- **Message Queue**: Apache Kafka
- **Stream Processing**: Apache Flink
- **Real-Time Cache**: Redis (Pub/Sub + Cache)
- **Time-Series DB**: TimescaleDB / InfluxDB
- **Metadata DB**: PostgreSQL
- **WebSocket**: Custom servers / Socket.io
- **API**: REST + GraphQL
- **Search**: Elasticsearch

**Scalability**:
- Scales to 20M users by adding WebSocket servers
- Scales to 100K stocks by adding feed handlers and storage
- Linear scaling for most components

**Cost Efficiency**:
- Infrastructure: $179K/month
- Data feeds: $50K/month (major cost)
- Total: $229K/month
- Cost per concurrent user: $0.023/month

---

**Document Version**: 1.0  
**Last Updated**: November 16, 2025  
**Status**: Complete & Interview-Ready ✅
