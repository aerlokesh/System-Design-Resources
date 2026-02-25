# Design a System to View Latest Stock Prices - Hello Interview Framework

> **Question**: Design a system that allows users to view real-time stock prices, subscribe to price updates via WebSocket, view historical price charts (OHLCV), and see trending/top movers — similar to Yahoo Finance, Robinhood, or Google Finance.
>
> **Asked at**: Google, Meta, Goldman Sachs, Robinhood, Bloomberg
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

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
1. **View Latest Price**: Users can query the current price of any stock by symbol (e.g., AAPL) — including last traded price, bid/ask, change, change%, volume, and OHLC for the day.
2. **Real-Time Price Streaming**: Users can subscribe to one or more stock symbols via WebSocket and receive push-based price updates with < 200ms latency from exchange to client.
3. **Historical Price Charts**: Users can view OHLCV (Open, High, Low, Close, Volume) data at multiple resolutions — 1min, 5min, 1hour, 1day — for periods ranging from 1 day to 10 years.
4. **Top Movers / Trending Stocks**: A dashboard showing top K gainers, top K losers, and most active stocks by volume — updated in near real-time.
5. **Watchlist**: Users can create custom watchlists of stocks and see real-time prices for all watchlist items on a single screen.

#### Nice to Have (P1)
- Price alerts (notify when AAPL > $200, or when stock drops 5% in a day).
- Portfolio tracking (holdings, P&L, allocation breakdown).
- Technical indicators (MA, RSI, MACD, Bollinger Bands).
- Market depth / order book (Level 2 data).
- Multi-exchange support with currency conversion.
- Search/autocomplete for stock symbols and company names.

#### Below the Line (Out of Scope)
- Placing trades / order execution.
- News or social sentiment integration.
- Fundamental data (P/E, earnings, analyst ratings).
- Options chains or derivatives pricing.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Price Update Latency** | < 200ms (exchange → user) | Users expect near-instant price reflection |
| **API Latency (quote)** | < 50ms | Price lookup must feel instantaneous |
| **Historical Query Latency** | < 500ms | Chart rendering needs fast data fetching |
| **WebSocket Delivery** | < 100ms (server → client) | Real-time streaming must be responsive |
| **Availability** | 99.99% during market hours | Downtime during trading = lost trust/revenue |
| **Consistency** | Strong for latest price, eventual for aggregates | Users must never see stale "current" price; historical aggregates can lag briefly |
| **Scale** | 10M concurrent users, 50K stocks, 75K updates/sec | Peak during market open across global exchanges |
| **Throughput** | 75K price writes/sec, 60K API reads/sec | High-frequency updates + heavy read traffic |

### Capacity Estimation

```
Stocks tracked: 50,000 (across NYSE, NASDAQ, LSE, TSE, HKEX, etc.)
Concurrent users: 10M during peak trading hours

Price updates:
  Average update frequency: Every 2 seconds per stock
  Sustained: 50,000 / 2 = 25,000 updates/sec
  Peak (market open, volatility): 3x = 75,000 updates/sec

WebSocket subscriptions:
  10M users × 5 stocks average = 50M active subscriptions
  Each price update fans out to subscribers of that stock

REST API:
  Price quote queries: 50K QPS
  Historical data queries: 10K QPS
  Total: 60K QPS

Storage:
  Real-time prices (Redis): 50K stocks × 1 KB = 50 MB
  Stock metadata (PostgreSQL): 50K × 5 KB = 250 MB
  1-minute OHLCV (10 years): 50K stocks × 4.9 MB/stock/year × 10 = 2.45 TB
  Daily OHLCV (10 years): 50K × 12.6 KB/year × 10 = 6.3 GB
  Search index (Elasticsearch): ~500 MB
  Total: ~3 TB (with indexes), ~9 TB with 3x replication

Network (outbound):
  WebSocket: ~5 GB/s (with fan-out optimization)
  REST API: ~120 MB/s
  Total: ~41 Gbps
```

---

## 2️⃣ Core Entities

### Entity 1: Stock
```java
public class Stock {
    private final String symbol;           // "AAPL", "GOOGL"
    private final String name;             // "Apple Inc."
    private final String exchange;         // "NASDAQ"
    private final String sector;           // "Technology"
    private final String currency;         // "USD"
    private final String country;          // "US"
    private final boolean isActive;
}
```

### Entity 2: PriceQuote (real-time snapshot)
```java
public class PriceQuote {
    private final String symbol;
    private final double lastPrice;        // Last traded price
    private final double open;             // Day's open
    private final double high;             // Day's high
    private final double low;              // Day's low
    private final double previousClose;    // Previous day's close
    private final double change;           // lastPrice - previousClose
    private final double changePercent;    // (change / previousClose) × 100
    private final long volume;             // Shares traded today
    private final double bid;              // Best bid
    private final double ask;              // Best ask
    private final long timestamp;          // Epoch millis of last update
    private final MarketStatus marketStatus; // OPEN, CLOSED, PRE_MARKET, AFTER_HOURS
}

public enum MarketStatus {
    OPEN, CLOSED, PRE_MARKET, AFTER_HOURS, HALTED
}
```

### Entity 3: OHLCV Bar (historical candle)
```java
public class OHLCVBar {
    private final String symbol;
    private final long timestamp;          // Start of the bar
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final long volume;
    private final String resolution;       // "1m", "5m", "1h", "1d"
}
```

### Entity 4: Watchlist
```java
public class Watchlist {
    private final String watchlistId;      // UUID
    private final String userId;
    private final String name;             // "Tech Stocks"
    private final List<String> symbols;    // ["AAPL", "GOOGL", "MSFT"]
    private final Instant createdAt;
    private final Instant updatedAt;
}
```

### Entity 5: TopMoverEntry
```java
public class TopMoverEntry {
    private final String symbol;
    private final String name;
    private final double lastPrice;
    private final double changePercent;
    private final long volume;
    private final int rank;
    private final MoverCategory category;  // GAINER, LOSER, MOST_ACTIVE
}

public enum MoverCategory {
    GAINER, LOSER, MOST_ACTIVE
}
```

---

## 3️⃣ API Design

### 1. Get Current Price Quote
```
GET /api/v1/quote?symbol=AAPL

Response (200 OK):
{
  "symbol": "AAPL",
  "name": "Apple Inc.",
  "exchange": "NASDAQ",
  "last_price": 192.53,
  "open": 190.00,
  "high": 193.10,
  "low": 189.50,
  "previous_close": 189.84,
  "change": +2.69,
  "change_percent": +1.42,
  "volume": 54312000,
  "bid": 192.52,
  "ask": 192.54,
  "market_status": "OPEN",
  "timestamp": 1740500400000
}
```

### 2. Batch Quote (Multiple Stocks)
```
GET /api/v1/quotes?symbols=AAPL,GOOGL,MSFT,TSLA&fields=last_price,change_percent,volume

Response (200 OK):
{
  "quotes": [
    { "symbol": "AAPL", "last_price": 192.53, "change_percent": +1.42, "volume": 54312000 },
    { "symbol": "GOOGL", "last_price": 178.20, "change_percent": -0.35, "volume": 22100000 },
    { "symbol": "MSFT", "last_price": 415.80, "change_percent": +0.87, "volume": 18500000 },
    { "symbol": "TSLA", "last_price": 195.60, "change_percent": +3.21, "volume": 98700000 }
  ]
}
```

> **Note**: `fields` parameter allows clients to request only the data they need, reducing payload size.

### 3. Historical OHLCV Data
```
GET /api/v1/history/AAPL?resolution=1d&from=2025-01-01&to=2025-02-25

Response (200 OK):
{
  "symbol": "AAPL",
  "resolution": "1d",
  "bars": [
    { "timestamp": 1735689600000, "open": 185.00, "high": 187.50, "low": 184.20, "close": 186.90, "volume": 45000000 },
    { "timestamp": 1735776000000, "open": 187.00, "high": 189.00, "low": 186.00, "close": 188.50, "volume": 42000000 },
    ...
  ],
  "count": 38
}
```

> **Resolution options**: `1m`, `5m`, `15m`, `1h`, `1d`, `1w`, `1M`
> **Max bars**: 5000 per request. Pagination via `from`/`to` date range.

### 4. WebSocket — Subscribe to Real-Time Updates
```
WS wss://stream.stockservice.com/v1?token={jwt}

Client → Server (subscribe):
{
  "action": "subscribe",
  "symbols": ["AAPL", "GOOGL", "MSFT"]
}

Server → Client (acknowledgment):
{
  "type": "subscribed",
  "symbols": ["AAPL", "GOOGL", "MSFT"]
}

Server → Client (price update, pushed on every trade):
{
  "type": "price_update",
  "symbol": "AAPL",
  "last_price": 192.55,
  "change": +2.71,
  "change_percent": +1.43,
  "volume": 54320000,
  "bid": 192.54,
  "ask": 192.56,
  "timestamp": 1740500401234
}

Client → Server (unsubscribe):
{
  "action": "unsubscribe",
  "symbols": ["GOOGL"]
}
```

### 5. Top Movers
```
GET /api/v1/movers?category=gainers&k=10

Response (200 OK):
{
  "category": "gainers",
  "as_of": "2026-02-25T15:30:00Z",
  "movers": [
    { "rank": 1, "symbol": "NVDA", "name": "NVIDIA Corp", "last_price": 950.00, "change_percent": +8.50, "volume": 120000000 },
    { "rank": 2, "symbol": "TSLA", "name": "Tesla Inc", "last_price": 195.60, "change_percent": +3.21, "volume": 98700000 },
    ...
  ]
}
```

> **Categories**: `gainers`, `losers`, `most_active`

### 6. Watchlist CRUD
```
POST /api/v1/watchlists
Body: { "name": "Tech Stocks", "symbols": ["AAPL", "GOOGL", "MSFT"] }

Response (201 Created):
{ "watchlist_id": "wl_abc123", "name": "Tech Stocks", "symbols": ["AAPL", "GOOGL", "MSFT"] }

GET /api/v1/watchlists/{watchlist_id}/quotes
Response: Real-time quotes for all symbols in the watchlist (same format as batch quote)

PUT /api/v1/watchlists/{watchlist_id}
Body: { "add": ["TSLA"], "remove": ["MSFT"] }

DELETE /api/v1/watchlists/{watchlist_id}
```

---

## 4️⃣ Data Flow

### Flow 1: Price Ingestion (Exchange → Cache — Write Path)
```
1. Trade executes on NASDAQ: AAPL @ $192.55, 1000 shares
   ↓
2. Exchange publishes via data feed (FIX protocol / WebSocket)
   ↓
3. Feed Handler (deployed near exchange, US region) receives within ~20ms
   a. Parse protocol (FIX tag-value or JSON)
   b. Normalize: convert exchange format → standard PriceUpdate
   c. Validate: price in range, timestamp fresh, symbol valid
   d. Publish to Kafka topic "raw-prices" (partitioned by symbol)
   ↓
4. Price Processor (Flink/KStreams) consumes from Kafka
   a. Calculate derived fields: change = 192.55 - 189.84 = +2.71, change% = +1.43%
   b. Anomaly check: price within 20% of previous? volume reasonable?
   c. Deduplicate: skip if same timestamp already processed
   ↓
5. Fan-out to multiple sinks:
   a. Redis cache: UPDATE stock:AAPL hash with latest price fields
   b. TimescaleDB: INSERT 1-minute OHLCV bar (aggregated)
   c. Redis Pub/Sub: PUBLISH channel "price:AAPL" → WebSocket servers
   d. Alert engine: check if any user alerts triggered
   e. Top Movers updater: update sorted sets for gainers/losers/active

Total latency: ~100ms (exchange → Redis + WebSocket broadcast)
```

### Flow 2: User Requests Current Price (REST — Read Path)
```
1. Client calls GET /api/v1/quote?symbol=AAPL
   ↓
2. API Gateway → Price Service (stateless)
   ↓
3. Price Service:
   a. Lookup Redis: HGETALL stock:AAPL → returns all current price fields
   b. If cache miss (extremely rare during market hours): query TimescaleDB for last trade
   c. Enrich with stock metadata from local cache / PostgreSQL
   ↓
4. Return JSON response to client
   Latency: < 50ms (Redis HGETALL = < 1ms, network = rest)
```

### Flow 3: Real-Time Streaming (WebSocket — Push Path)
```
1. Client opens WebSocket: wss://stream.stockservice.com/v1
   ↓
2. WebSocket Server authenticates (JWT), assigns connection to server via consistent hashing
   ↓
3. Client sends: { "action": "subscribe", "symbols": ["AAPL", "GOOGL"] }
   ↓
4. WebSocket Server:
   a. Adds user to subscription registry: AAPL → [user_123, ...], GOOGL → [user_123, ...]
   b. Subscribes to Redis Pub/Sub channels: "price:AAPL", "price:GOOGL"
   ↓
5. When Price Processor publishes to "price:AAPL":
   a. All WebSocket servers subscribed to that channel receive the message
   b. Each server looks up local subscriptions: which of MY connections want AAPL?
   c. Broadcast to those connections
   ↓
6. Client receives push message within ~100ms of the price processor publishing
```

### Flow 4: Top Movers Dashboard (Streaming Aggregation)
```
1. Every price update event in Kafka includes: symbol, price, previous_close, volume
   ↓
2. Stream Processor (Flink):
   a. Compute change_percent for each stock: (price - prev_close) / prev_close × 100
   b. Maintain 3 Redis Sorted Sets per exchange/market:
      - movers:gainers   → ZADD with change_percent as score (positive)
      - movers:losers    → ZADD with change_percent as score (negative, sorted ascending)
      - movers:active    → ZADD with volume as score
   ↓
3. Dashboard reads: ZREVRANGE movers:gainers 0 9 WITHSCORES → top 10 gainers
   ↓
4. Freshness: < 5 seconds from trade to dashboard update
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          STOCK EXCHANGES                                     │
│                                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ NYSE/NASDAQ   │  │  LSE/Euronext │  │   TSE/HKEX   │  │   BSE/NSE    │    │
│  │  (US)         │  │  (Europe)     │  │  (Asia)       │  │  (India)     │    │
│  └──────┬────────┘  └──────┬────────┘  └──────┬────────┘  └──────┬───────┘    │
└─────────┼──────────────────┼──────────────────┼──────────────────┼────────────┘
          │                  │                  │                  │
          ▼                  ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     FEED HANDLERS (Regional)                                 │
│                     (Deployed near exchanges for low latency)                │
│                                                                               │
│  • Parse FIX / WebSocket / REST feeds                                        │
│  • Normalize to standard PriceUpdate format                                  │
│  • Validate (price range, timestamp, symbol)                                 │
│  • Publish to Kafka                                                          │
│                                                                               │
│  Redundancy: Primary + secondary data providers per exchange                 │
│  Failover: Auto-switch on feed failure (< 30s)                              │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         APACHE KAFKA                                         │
│                                                                               │
│  Topic: "raw-prices"                                                         │
│  Partitioned by: symbol hash (ensures ordering per stock)                    │
│  Partitions: 50 (handles 75K msg/sec)                                       │
│  Retention: 24 hours (replay for recovery)                                   │
│                                                                               │
│  Consumers:                                                                   │
│  ├─ Price Processor (Flink)                                                  │
│  ├─ Historical Aggregator                                                    │
│  ├─ Alert Engine                                                             │
│  └─ Top Movers Updater                                                       │
└──────┬──────────────┬──────────────┬──────────────┬─────────────────────────┘
       │              │              │              │
       ▼              ▼              ▼              ▼
┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────┐
│  PRICE     │ │ HISTORICAL │ │  ALERT     │ │  TOP MOVERS    │
│ PROCESSOR  │ │ AGGREGATOR │ │  ENGINE    │ │  UPDATER       │
│ (Flink)    │ │            │ │            │ │  (Flink)       │
│            │ │ Aggregate  │ │ Evaluate   │ │                │
│ • Validate │ │ 1min→5min  │ │ user alert │ │ Maintain Redis │
│ • Enrich   │ │ 5min→1hour │ │ conditions │ │ sorted sets:   │
│ • Dedupe   │ │ 1hour→1day │ │ on each    │ │ • gainers      │
│ • Calc Δ   │ │            │ │ price tick │ │ • losers       │
│ • Publish  │ │ Write to   │ │            │ │ • most_active  │
│            │ │ TimescaleDB│ │ Fire push  │ │                │
└─────┬──────┘ └─────┬──────┘ │ notifs     │ └───────┬────────┘
      │               │        └────────────┘         │
      │               │                               │
      ▼               ▼                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DATA STORAGE LAYER                                  │
│                                                                               │
│  ┌─────────────────┐  ┌──────────────────┐  ┌─────────────────────┐        │
│  │  REDIS CLUSTER   │  │  TIMESCALE DB    │  │  POSTGRESQL         │        │
│  │  (Real-Time)     │  │  (Time-Series)   │  │  (Metadata)         │        │
│  │                   │  │                  │  │                     │        │
│  │ • stock:{sym}     │  │ • 1min OHLCV    │  │ • stocks table      │        │
│  │   (Hash: price,   │  │ • 5min OHLCV    │  │ • exchanges table   │        │
│  │   change, vol..)  │  │ • 1hour OHLCV   │  │ • watchlists table  │        │
│  │                   │  │ • 1day OHLCV    │  │ • alerts table      │        │
│  │ • movers:gainers  │  │                  │  │ • users table       │        │
│  │   (Sorted Set)    │  │ Continuous       │  │                     │        │
│  │ • movers:losers   │  │ Aggregates       │  │ Sharded by user_id  │        │
│  │ • movers:active   │  │ (auto-rollup)    │  │                     │        │
│  │                   │  │                  │  │                     │        │
│  │ • Pub/Sub channels│  │ Retention:       │  │                     │        │
│  │   price:{symbol}  │  │ 1min=90 days     │  │                     │        │
│  │                   │  │ 5min=1 year      │  │                     │        │
│  │ • watchlist:{uid} │  │ 1hr=5 years      │  │                     │        │
│  │   (Set of syms)   │  │ 1day=10+ years   │  │                     │        │
│  └─────────────────┘  └──────────────────┘  └─────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API / SERVING LAYER                                  │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                      API GATEWAY                                 │        │
│  │              (Rate limiting, auth, routing)                      │        │
│  └─────────┬──────────────────────┬─────────────────────────────────┘        │
│            │                      │                                          │
│     ┌──────▼────────┐     ┌──────▼──────────────┐                          │
│     │  REST API      │     │  WEBSOCKET SERVERS   │                          │
│     │  (Stateless)   │     │  (Stateful - sticky) │                          │
│     │                │     │                       │                          │
│     │ • GET /quote   │     │ • 500 servers         │                          │
│     │ • GET /quotes  │     │ • 20K conn/server     │                          │
│     │ • GET /history │     │ • 10M total conns     │                          │
│     │ • GET /movers  │     │ • Subscribe/unsub     │                          │
│     │ • CRUD watchl. │     │ • Push price updates  │                          │
│     │                │     │ • Heartbeat (30s)     │                          │
│     │ Reads from:    │     │                       │                          │
│     │ Redis + TSDB   │     │ Receives from:        │                          │
│     │ 100 pods       │     │ Redis Pub/Sub         │                          │
│     └────────────────┘     └───────────────────────┘                          │
└─────────────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CLIENTS                                            │
│                                                                               │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐      │
│  │  Web App  │  │  Mobile App   │  │ Desktop App    │  │  3rd-Party   │      │
│  │  (React)  │  │ (iOS/Android) │  │ (Terminal)     │  │  API Users   │      │
│  └──────────┘  └──────────────┘  └───────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Feed Handlers** | Ingest price data from exchanges, normalize, validate | Java (FIX protocol), deployed near exchanges | 5-10 per exchange, regional |
| **Kafka** | Durable event stream for price updates | Apache Kafka | 50 partitions, partitioned by symbol |
| **Price Processor** | Enrich, validate, deduplicate, compute derived fields | Apache Flink | 10-20 task managers |
| **Historical Aggregator** | Roll up 1min → 5min → 1hr → 1day OHLCV | Flink / TimescaleDB continuous aggregates | Runs alongside price processor |
| **Alert Engine** | Evaluate user-defined price conditions, fire notifications | Java service consuming from Kafka | 10-20 pods |
| **Top Movers Updater** | Maintain sorted sets of gainers/losers/active | Flink → Redis | 3-5 nodes |
| **Redis Cluster** | Real-time price cache + Pub/Sub + sorted sets | Redis 7+ Cluster | 6 nodes (3 master + 3 replica) |
| **TimescaleDB** | Historical OHLCV time-series storage | TimescaleDB (PostgreSQL extension) | 3-node cluster, sharded by symbol hash |
| **PostgreSQL** | Stock metadata, watchlists, user data, alerts | PostgreSQL 15+ | Primary + 3 read replicas |
| **REST API** | Serve price quotes, history, movers, watchlists | Java/Node.js on K8s | 100 stateless pods |
| **WebSocket Servers** | Push real-time price updates to connected clients | Java/Go with Netty/Gorilla | 500 servers, 20K conn each |
| **API Gateway** | Auth, rate limiting, routing, TLS termination | Kong / AWS ALB | Auto-scaled |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Real-Time Price Ingestion Pipeline

**The Problem**: We ingest prices from multiple global exchanges using different protocols (FIX, WebSocket, REST). Each exchange has its own format, currency, and market hours. We need to normalize everything into a unified format with < 100ms processing latency.

```java
public class FeedHandler {

    /**
     * Feed Handler Architecture:
     * 
     * Deployed regionally near exchanges for minimum network latency:
     *   US-East: NYSE, NASDAQ feeds
     *   EU-West: LSE, Euronext feeds
     *   AP-Northeast: TSE, HKEX feeds
     * 
     * Each handler:
     * 1. Maintains persistent connection to exchange data feed
     * 2. Parses protocol-specific messages (FIX, JSON, binary)
     * 3. Normalizes to standard PriceUpdate
     * 4. Validates (price range, timestamp freshness, symbol existence)
     * 5. Publishes to Kafka
     * 
     * Redundancy: 2 feed handlers per exchange (primary + standby)
     * Failover: If primary silent > 30s, standby takes over
     */

    // Normalization: exchange-specific → standard format
    public PriceUpdate normalize(RawFeedMessage raw) {
        return PriceUpdate.builder()
            .symbol(mapSymbol(raw.getExchangeSymbol(), raw.getExchange()))
            .price(convertPrice(raw.getRawPrice(), raw.getExchange()))  // Cents→dollars, pence→pounds
            .currency(raw.getCurrency())
            .volume(raw.getVolume())
            .bid(raw.getBid())
            .ask(raw.getAsk())
            .exchange(raw.getExchange())
            .timestamp(raw.getTimestamp())
            .build();
    }

    // Validation: reject bad data before it enters the pipeline
    public boolean validate(PriceUpdate update) {
        if (update.getPrice() <= 0) return false;                    // Price must be positive
        if (update.getTimestamp() < System.currentTimeMillis() - 10_000) return false;  // < 10s old
        if (!symbolRegistry.exists(update.getSymbol())) return false; // Known symbol
        
        // Anomaly: price moved > 20% from last known price (potential bad data or halt)
        Double lastPrice = redis.hget("stock:" + update.getSymbol(), "price");
        if (lastPrice != null && Math.abs(update.getPrice() - lastPrice) / lastPrice > 0.20) {
            alertOpsTeam(update, "Price anomaly: " + update.getPrice() + " vs last " + lastPrice);
            return false;  // Don't propagate until confirmed
        }
        return true;
    }

    // Publish to Kafka with symbol-based partitioning (ordering guarantee per stock)
    public void publish(PriceUpdate update) {
        int partition = Math.abs(update.getSymbol().hashCode()) % NUM_PARTITIONS;
        kafkaProducer.send(new ProducerRecord<>("raw-prices", partition, 
            update.getSymbol(), serialize(update)));
    }
}
```

**Feed reliability**:
```
Redundancy per exchange:
  Primary feed: Direct exchange connection (FIX protocol)
  Secondary feed: Market data vendor (Bloomberg/Reuters)
  Tertiary feed: Alternative provider (IEX, Polygon.io)

Failover logic:
  If primary silent > 30 seconds → switch to secondary
  Cross-validate: if primary and secondary differ by > $0.10 → alert, use consensus
  Sequence number tracking: detect gaps, request replay on reconnect

Connection management:
  Persistent TCP connection with auto-reconnect
  Exponential backoff: 1s, 2s, 4s, 8s... max 60s
  Heartbeat: exchange sends heartbeat every 5s, handler expects it
```

---

### Deep Dive 2: WebSocket Fan-Out at 10M Concurrent Connections

**The Problem**: When AAPL's price updates, we need to push that update to potentially 1M+ subscribed users across 500 WebSocket servers. Naive broadcasting would require 1M individual messages per price tick — at 25K ticks/sec that's 25 billion messages/sec. How do we make this efficient?

```java
public class WebSocketFanOutService {

    /**
     * Fan-out strategy: Redis Pub/Sub → WebSocket Servers → Connected Users
     * 
     * Key insight: We DON'T send 1 message per user per price tick.
     * Instead:
     * 1. Price Processor publishes 1 message to Redis Pub/Sub channel "price:AAPL"
     * 2. Only WebSocket servers that HAVE users subscribed to AAPL receive it
     * 3. Each server broadcasts to its local subscribed connections
     * 
     * This reduces 1M messages → ~500 messages (one per server that has AAPL subscribers)
     * Each server then does local fan-out to its ~2000 AAPL subscribers
     */

    // Server-side subscription registry (in-memory, per WebSocket server)
    // symbol → set of connection IDs on THIS server
    private final Map<String, Set<String>> symbolToConnections = new ConcurrentHashMap<>();
    
    // When user subscribes to a symbol
    public void onSubscribe(String connectionId, List<String> symbols) {
        for (String symbol : symbols) {
            symbolToConnections
                .computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet())
                .add(connectionId);
            
            // Subscribe to Redis Pub/Sub channel if this is the first subscriber on this server
            if (symbolToConnections.get(symbol).size() == 1) {
                redisPubSub.subscribe("price:" + symbol);
            }
        }
    }
    
    // When Redis Pub/Sub delivers a price update to this server
    public void onPriceMessage(String channel, String message) {
        String symbol = channel.replace("price:", "");  // "price:AAPL" → "AAPL"
        Set<String> connections = symbolToConnections.get(symbol);
        
        if (connections == null || connections.isEmpty()) return;
        
        // Broadcast to all local connections subscribed to this symbol
        // Use batching: serialize message ONCE, write to all connections
        byte[] serialized = serializeMessage(message);
        for (String connId : connections) {
            WebSocketSession session = sessionRegistry.get(connId);
            if (session != null && session.isOpen()) {
                session.sendBinary(serialized);  // Zero-copy: same byte array for all
            }
        }
    }
}
```

**Connection management at scale**:
```
500 WebSocket servers, each handling 20K connections = 10M total

Per-connection memory:
  Connection state: 1 KB
  Subscription data: 500 bytes (avg 5 stocks)
  Send/receive buffers: 4 KB
  Total: ~5.5 KB per connection

Per server: 20K × 5.5 KB = 110 MB → easily fits in memory

Sticky sessions via consistent hashing:
  hash(user_id) → server_id
  Same user always connects to same server (avoids subscription re-setup)
  If server dies: user reconnects to next server in ring, re-subscribes

Heartbeat: server sends ping every 30s, expects pong within 10s
  3 missed pongs → close connection, free resources

Connection draining (for deployments):
  1. Mark server as draining (stop accepting new connections)
  2. Send "reconnect" message to all clients
  3. Clients reconnect to different server (load balancer routes away)
  4. Wait 60s, then shutdown
```

**Bandwidth optimization**:
```
Problem: 25K price updates/sec × 200 bytes × ~2000 subscribers per server = 10 GB/s per server

Optimizations:
1. Message batching: Buffer updates for 100ms, send as batch
   → Reduces per-message overhead (headers, framing)
   → Acceptable 100ms delay for most users

2. Delta encoding: Send only changed fields
   Full message:  { symbol, price, change, change%, volume, bid, ask, timestamp } = 200 bytes
   Delta message: { symbol, price, volume, timestamp } = 80 bytes (60% reduction)

3. Binary protocol (MessagePack/Protobuf instead of JSON)
   JSON: 200 bytes → MessagePack: 120 bytes (40% reduction)

4. Per-message compression (WebSocket permessage-deflate)
   120 bytes → ~60 bytes (50% compression)

Combined: 200 bytes → 60 bytes = 70% bandwidth reduction
Effective: 10 GB/s → 3 GB/s per server cluster (manageable)
```

---

### Deep Dive 3: Time-Series Storage and Historical Query Optimization

**The Problem**: We store 10 years of OHLCV data at minute-level granularity (2.45 TB). Users request charts at different resolutions (1min to 1month). We need < 500ms query latency for any historical range.

```java
public class HistoricalPriceService {

    /**
     * Storage strategy: TimescaleDB with continuous aggregates
     * 
     * Raw data: 1-minute OHLCV bars
     * Pre-computed rollups: 5min, 1hour, 1day (via continuous aggregates)
     * 
     * When a user requests "1 year of daily AAPL data":
     *   → Query stock_prices_daily (pre-computed) → ~252 rows → < 10ms
     * 
     * When a user requests "1 day of 1-minute AAPL data":
     *   → Query stock_prices_1min → ~390 rows → < 50ms
     * 
     * When a user requests "5 years of weekly AAPL data":
     *   → Query stock_prices_daily, aggregate to weekly in SQL → ~260 rows → < 100ms
     */

    public List<OHLCVBar> getHistory(String symbol, String resolution, 
                                      Instant from, Instant to) {
        // Route to the right table based on resolution
        String table = resolveTable(resolution);
        
        // If exact resolution table exists, query directly
        if (table != null) {
            return jdbcTemplate.query(
                "SELECT * FROM " + table + 
                " WHERE symbol = ? AND timestamp >= ? AND timestamp <= ?" +
                " ORDER BY timestamp ASC LIMIT 5000",
                new Object[]{symbol, from, to},
                ohlcvRowMapper);
        }
        
        // For non-standard resolutions (15min, 1week), aggregate from nearest lower resolution
        return aggregateFromBase(symbol, resolution, from, to);
    }
    
    private String resolveTable(String resolution) {
        return switch (resolution) {
            case "1m"  -> "stock_prices_1min";
            case "5m"  -> "stock_prices_5min";
            case "1h"  -> "stock_prices_1hour";
            case "1d"  -> "stock_prices_daily";
            default    -> null;  // 15m, 1w, 1M → aggregate
        };
    }
}
```

**TimescaleDB schema and continuous aggregates**:
```sql
-- Raw 1-minute data (source of truth)
CREATE TABLE stock_prices_1min (
    symbol      TEXT NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL,
    open        DOUBLE PRECISION,
    high        DOUBLE PRECISION,
    low         DOUBLE PRECISION,
    close       DOUBLE PRECISION,
    volume      BIGINT
);

-- Convert to hypertable (TimescaleDB auto-partitions by time)
SELECT create_hypertable('stock_prices_1min', 'timestamp', 
    chunk_time_interval => INTERVAL '1 day');

-- Index for fast lookups: specific stock in time range
CREATE INDEX idx_1min_symbol_time ON stock_prices_1min (symbol, timestamp DESC);

-- Continuous aggregate: 5-minute bars (auto-updated as new data arrives)
CREATE MATERIALIZED VIEW stock_prices_5min
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('5 minutes', timestamp) AS timestamp,
    first(open, timestamp) AS open,
    max(high) AS high,
    min(low) AS low,
    last(close, timestamp) AS close,
    sum(volume) AS volume
FROM stock_prices_1min
GROUP BY symbol, time_bucket('5 minutes', timestamp);

-- Continuous aggregate: hourly bars
CREATE MATERIALIZED VIEW stock_prices_1hour
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 hour', timestamp) AS timestamp,
    first(open, timestamp) AS open,
    max(high) AS high,
    min(low) AS low,
    last(close, timestamp) AS close,
    sum(volume) AS volume
FROM stock_prices_5min
GROUP BY symbol, time_bucket('1 hour', timestamp);

-- Continuous aggregate: daily bars
CREATE MATERIALIZED VIEW stock_prices_daily
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 day', timestamp) AS timestamp,
    first(open, timestamp) AS open,
    max(high) AS high,
    min(low) AS low,
    last(close, timestamp) AS close,
    sum(volume) AS volume
FROM stock_prices_1hour
GROUP BY symbol, time_bucket('1 day', timestamp);

-- Retention policy: auto-delete old granular data
SELECT add_retention_policy('stock_prices_1min', INTERVAL '90 days');
-- 5min data kept for 1 year, 1hour for 5 years, daily forever
```

**Query performance**:
```
"AAPL daily for 1 year" (252 rows):
  → stock_prices_daily, indexed on (symbol, timestamp)
  → Single index scan → < 10ms

"AAPL 1-minute for today" (390 rows):
  → stock_prices_1min, today's chunk only (partition pruning)
  → < 50ms

"All 50K stocks, today's close" (50K rows):
  → stock_prices_daily WHERE timestamp = today
  → Sequential scan on today's partition → < 200ms

"AAPL 1-minute for 90 days" (98K rows):
  → Large result set, but with partition pruning
  → ~500ms (at the limit, consider pagination)
```

---

### Deep Dive 4: Top Movers — Real-Time Leaderboard with Redis Sorted Sets

**The Problem**: Users want to see top 10 gainers, top 10 losers, and most active stocks — updated within seconds of each trade. Querying all 50K stocks and sorting on every request would be too slow.

```java
public class TopMoversService {

    /**
     * Strategy: Maintain 3 Redis Sorted Sets, updated on every price tick
     * 
     * movers:gainers  → score = change_percent (positive values, sorted descending)
     * movers:losers   → score = change_percent (negative values, sorted ascending)
     * movers:active   → score = volume (sorted descending)
     * 
     * On every price update:
     *   ZADD movers:gainers change_percent AAPL
     *   ZADD movers:losers  change_percent AAPL  (losers have negative scores)
     *   ZADD movers:active  volume AAPL
     * 
     * To get top 10 gainers:
     *   ZREVRANGE movers:gainers 0 9 WITHSCORES → O(log N + K)
     * 
     * 50K stocks × 75K updates/sec → 75K ZADD/sec → Redis handles easily
     */

    // Called by stream processor on every price update
    public void updateMovers(String symbol, double changePercent, long volume) {
        Pipeline pipe = redis.pipelined();
        pipe.zadd("movers:gainers", changePercent, symbol);
        pipe.zadd("movers:losers", changePercent, symbol);   // Same set, losers have negative scores
        pipe.zadd("movers:active", (double) volume, symbol);
        pipe.sync();  // Single round-trip for 3 operations
    }

    // Serve dashboard request
    public List<TopMoverEntry> getTopMovers(MoverCategory category, int k) {
        String key = switch (category) {
            case GAINER     -> "movers:gainers";
            case LOSER      -> "movers:losers";
            case MOST_ACTIVE -> "movers:active";
        };
        
        Set<ZSetOperations.TypedTuple<String>> results;
        if (category == MoverCategory.LOSER) {
            // Losers: lowest change_percent first
            results = redis.zrangeWithScores(key, 0, k - 1);
        } else {
            // Gainers and active: highest score first
            results = redis.zrevrangeWithScores(key, 0, k - 1);
        }
        
        int rank = 1;
        List<TopMoverEntry> movers = new ArrayList<>();
        for (var entry : results) {
            String symbol = entry.getValue();
            // Hydrate with full price data from Redis hash
            Map<String, String> priceData = redis.hgetAll("stock:" + symbol);
            movers.add(new TopMoverEntry(symbol, priceData.get("name"),
                Double.parseDouble(priceData.get("price")),
                entry.getScore(), Long.parseLong(priceData.getOrDefault("volume", "0")),
                rank++, category));
        }
        return movers;
    }

    // Reset at market close (clear today's data, prepare for tomorrow)
    @Scheduled(cron = "0 0 16 * * MON-FRI", timezone = "America/New_York")
    public void resetDailyMovers() {
        redis.del("movers:gainers", "movers:losers", "movers:active");
    }
}
```

**Performance**:
```
Redis ZADD: O(log N) per update, N = 50K stocks → log(50000) ≈ 16 comparisons → < 0.1ms
Redis ZREVRANGE: O(K + log N) for top K → < 0.1ms for K=10

At 75K updates/sec: 75K × 3 ZADD = 225K Redis operations/sec
Redis throughput: 500K ops/sec per shard → single shard handles this easily

Memory: 50K stocks × 100 bytes per entry × 3 sets = ~15 MB → trivial
```

---

### Deep Dive 5: Redis Pub/Sub vs Kafka for WebSocket Broadcasting

**The Problem**: We need to deliver price updates from the processing pipeline to 500 WebSocket servers. Should we use Redis Pub/Sub or have each WebSocket server consume directly from Kafka?

```java
public class BroadcastArchitectureComparison {

    /**
     * Option A: Redis Pub/Sub (CHOSEN)
     * 
     * Price Processor → PUBLISH "price:AAPL" message → 
     *   All WebSocket servers subscribed to "price:AAPL" receive it
     * 
     * Pros:
     * + Simple: one PUBLISH, all subscribers get it
     * + Low latency: < 1ms within Redis
     * + No consumer group management
     * + WebSocket servers subscribe only to channels they need
     *   (if no user on server X wants AAPL, server X doesn't subscribe)
     * 
     * Cons:
     * - Fire-and-forget: if WebSocket server is down, message lost
     * - No replay: can't re-read missed messages
     * - Scale: single Redis node handles ~500K msg/sec (sufficient for us)
     * 
     * 
     * Option B: Kafka Direct Consumption
     * 
     * Each WebSocket server runs a Kafka consumer on "processed-prices" topic
     * 
     * Pros:
     * + Durable: messages retained, can replay
     * + Consumer groups: automatic load balancing
     * 
     * Cons:
     * - Each of 500 servers would consume ALL 75K updates/sec (375M msg/sec total reads!)
     * - Need filtering: each server must discard updates for stocks no one on that server watches
     * - Higher latency: Kafka consumer poll interval (~100ms)
     * - 50 partitions / 500 servers = can't parallelize (more servers than partitions)
     * 
     * 
     * Option C: Kafka → Intermediate Fanout Service → WebSocket (Alternative for huge scale)
     * 
     * Kafka → Fanout Service (maintains subscription → server mapping) → push to specific WS servers
     * 
     * This is overengineering for our scale. Redis Pub/Sub handles 75K msg/sec trivially.
     */
    
    // WHY REDIS PUB/SUB WINS HERE:
    // 1. We already have Redis for caching — no new infrastructure
    // 2. 75K publishes/sec is well within Redis capacity (500K ops/sec)
    // 3. Fire-and-forget is fine: if a WS server misses an update, the NEXT
    //    update (1-2 seconds later) will have the latest price anyway
    // 4. Server subscribes to ~5K channels (symbols with active subscribers)
    //    — much less than processing all 50K symbols from Kafka
}
```

**Hybrid approach for resilience**:
```
Normal path: Redis Pub/Sub (low latency, < 1ms)
Recovery path: On WebSocket server restart, fetch latest prices from Redis Hash
  → HGETALL stock:AAPL for each subscribed symbol
  → Immediately up-to-date, no need to replay Kafka history

This gives us:
  Latency: < 1ms (Pub/Sub)
  Recovery: Instant (Redis cache has latest state)
  Durability: Kafka retains events for 24h (for other consumers, not WS)
```

---

### Deep Dive 6: Handling Market Events — Halts, Circuit Breakers, After-Hours

**The Problem**: Stock exchanges have complex states — pre-market, regular hours, after-hours, halts, circuit breakers. Our system must accurately reflect these states and not show stale or misleading prices.

```java
public class MarketStateManager {

    /**
     * Market states per exchange:
     * 
     * PRE_MARKET:    4:00 AM - 9:30 AM ET (limited trading, lower volume)
     * OPEN:          9:30 AM - 4:00 PM ET (regular trading)
     * AFTER_HOURS:   4:00 PM - 8:00 PM ET (extended trading)
     * CLOSED:        8:00 PM - 4:00 AM ET (no trading)
     * HALTED:        Any time (regulatory halt on specific stock)
     * 
     * Circuit breakers (market-wide):
     * Level 1: S&P 500 drops 7%  → 15-minute halt
     * Level 2: S&P 500 drops 13% → 15-minute halt
     * Level 3: S&P 500 drops 20% → trading suspended for day
     */

    // Track market status per exchange
    private final Map<String, MarketStatus> exchangeStatus = new ConcurrentHashMap<>();
    
    // Track individual stock halts
    private final Set<String> haltedSymbols = ConcurrentHashMap.newKeySet();

    // Called when exchange sends halt notification
    public void onStockHalt(String symbol, String reason) {
        haltedSymbols.add(symbol);
        
        // Update Redis: mark stock as halted
        redis.hset("stock:" + symbol, "market_status", "HALTED");
        redis.hset("stock:" + symbol, "halt_reason", reason);
        
        // Notify all subscribers via WebSocket
        redisPubSub.publish("price:" + symbol, 
            "{\"type\":\"halt\",\"symbol\":\"" + symbol + "\",\"reason\":\"" + reason + "\"}");
    }
    
    // Price processor checks before processing an update
    public boolean shouldProcess(PriceUpdate update) {
        MarketStatus status = exchangeStatus.get(update.getExchange());
        
        // During CLOSED: only process if it's after-hours/pre-market eligible
        if (status == MarketStatus.CLOSED) {
            return false;  // Reject stale data
        }
        
        // During HALTED: reject updates for halted stocks
        if (haltedSymbols.contains(update.getSymbol())) {
            return false;  // Stock is halted, don't update price
        }
        
        return true;
    }

    // Display logic: what price to show when market is closed?
    public PriceQuote enrichWithMarketState(PriceQuote quote) {
        MarketStatus status = exchangeStatus.get(quote.getExchange());
        
        if (status == MarketStatus.AFTER_HOURS || status == MarketStatus.PRE_MARKET) {
            // Show extended hours price separately from regular close
            quote.setMarketStatus(status);
            quote.setExtendedHoursPrice(getExtendedPrice(quote.getSymbol()));
        } else if (status == MarketStatus.CLOSED) {
            // Show last close price, clearly labeled
            quote.setMarketStatus(MarketStatus.CLOSED);
        }
        
        return quote;
    }
}
```

**Holiday and schedule management**:
```sql
-- Market holidays table (loaded annually)
CREATE TABLE market_holidays (
    exchange_code VARCHAR(10),
    date          DATE,
    name          VARCHAR(100),
    is_early_close BOOLEAN DEFAULT FALSE,
    early_close_time TIME,  -- e.g., 1:00 PM for day-before-holiday
    PRIMARY KEY (exchange_code, date)
);

-- Check at startup and cache in memory
-- Used by Feed Handler to know when to expect data
-- Used by UI to display "Market Closed — Holiday" message
```

---

### Deep Dive 7: Data Consistency — Ensuring Price Accuracy

**The Problem**: Prices flow through multiple systems (Feed Handler → Kafka → Flink → Redis / TimescaleDB / WebSocket). How do we ensure the price a user sees is accurate and not stale?

```java
public class PriceConsistencyManager {

    /**
     * Consistency model:
     * 
     * STRONG consistency for "current price" (Redis):
     *   - Redis is updated synchronously by Price Processor
     *   - REST API reads from Redis → always latest
     *   - Single writer per symbol (Kafka partition = single consumer)
     *   - No stale reads from Redis (no replication lag — using master for reads too)
     * 
     * EVENTUAL consistency for "historical data" (TimescaleDB):
     *   - 1-minute bars aggregated from ticks → may lag by up to 60 seconds
     *   - Continuous aggregates (5min, 1hr, 1day) → lag by aggregate window
     *   - Acceptable: user viewing "1-year daily chart" doesn't need sub-second freshness
     * 
     * EVENTUAL consistency for "top movers" (Redis Sorted Sets):
     *   - Updated on every tick → effectively real-time
     *   - Worst case: 2-3 seconds behind if Flink has processing delay
     *   - Acceptable for dashboard display
     */

    // Ordering guarantee: Kafka partitions by symbol
    // → All AAPL updates go to same partition
    // → Single consumer processes them IN ORDER
    // → No out-of-order price updates for any stock
    
    // Deduplication: Price Processor tracks last timestamp per symbol
    private final Map<String, Long> lastProcessedTimestamp = new ConcurrentHashMap<>();
    
    public boolean isDuplicate(PriceUpdate update) {
        Long last = lastProcessedTimestamp.get(update.getSymbol());
        if (last != null && update.getTimestamp() <= last) {
            return true;  // Already processed this or newer update
        }
        lastProcessedTimestamp.put(update.getSymbol(), update.getTimestamp());
        return false;
    }

    // Cross-validation: compare prices from multiple feed sources
    public void crossValidate(PriceUpdate primary, PriceUpdate secondary) {
        double diff = Math.abs(primary.getPrice() - secondary.getPrice());
        double threshold = primary.getPrice() * 0.001;  // 0.1% tolerance
        
        if (diff > threshold) {
            // Log discrepancy, alert ops, use primary (trusted source)
            metrics.increment("price.discrepancy", "symbol", primary.getSymbol());
            log.warn("Price discrepancy for {}: primary={}, secondary={}", 
                primary.getSymbol(), primary.getPrice(), secondary.getPrice());
        }
    }

    // Periodic reconciliation: verify Redis matches TimescaleDB
    @Scheduled(fixedRate = 300_000)  // Every 5 minutes
    public void reconcileRedisWithDB() {
        for (String symbol : getActiveSymbols()) {
            String redisPrice = redis.hget("stock:" + symbol, "price");
            Double dbPrice = getLatestPriceFromDB(symbol);
            
            if (redisPrice != null && dbPrice != null) {
                double diff = Math.abs(Double.parseDouble(redisPrice) - dbPrice);
                if (diff > 0.01) {
                    // Redis drifted — correct it
                    redis.hset("stock:" + symbol, "price", String.valueOf(dbPrice));
                    metrics.increment("price.reconciliation.corrected");
                }
            }
        }
    }
}
```

**Why single-writer-per-symbol is critical**:
```
Kafka partition key = symbol hash
→ All AAPL events go to partition 5 (example)
→ Partition 5 has exactly 1 consumer in the Flink consumer group
→ That single consumer processes AAPL events in strict order

Without this:
  T=100ms: Trade at $192.55 arrives
  T=101ms: Trade at $192.60 arrives  
  If processed out of order → user sees $192.55 AFTER $192.60 → confusing

With partition ordering:
  Always process $192.55 first, then $192.60 → monotonically increasing timestamps
```

---

### Deep Dive 8: Scaling WebSocket Servers — Consistent Hashing and Graceful Failover

**The Problem**: With 500 WebSocket servers and 10M connections, how do we route users, handle server failures, and perform zero-downtime deployments?

```java
public class WebSocketRoutingService {

    /**
     * Routing strategy: Consistent hashing by user_id
     * 
     * Why consistent hashing:
     * - Same user always routes to same server (sticky session)
     * - Subscriptions stored in server memory (no external lookup needed)
     * - If server dies, only 1/N users need to re-route (not all users)
     * 
     * Hash ring: 500 servers, each with 150 virtual nodes = 75,000 points on ring
     * User routes to: nearest server clockwise on the ring
     */

    private final ConsistentHashRing<String> hashRing;
    
    public WebSocketRoutingService(List<String> serverIds) {
        this.hashRing = new ConsistentHashRing<>(serverIds, 150);  // 150 vnodes each
    }

    // Load balancer calls this to determine which server a user connects to
    public String getServerForUser(String userId) {
        return hashRing.getNode(userId);
    }

    // When a server fails: remove from ring, affected users reconnect to next server
    public void onServerFailure(String serverId) {
        hashRing.removeNode(serverId);
        // ~20K users (1/500 of total) need to reconnect
        // Client auto-retry logic handles this: exponential backoff with jitter
    }

    // Zero-downtime deployment (rolling restart):
    public void rollingDeploy(String serverId) {
        // 1. Mark server as draining
        loadBalancer.markDraining(serverId);
        
        // 2. Server sends "reconnect" message to all 20K connected users
        webSocketServer.sendToAll(serverId, 
            "{\"type\":\"reconnect\",\"reason\":\"server_maintenance\"}");
        
        // 3. Clients reconnect to next server on hash ring (load balancer routes away)
        // 4. Wait 60s for connections to drain
        // 5. Shutdown old server, start new version
        // 6. Add new server back to ring
        
        // Impact: 20K users experience ~2s reconnection delay
        // With 500 servers, do one at a time → 500 × 60s = ~8 hours for full deploy
        // Parallelize 10 at a time → ~50 minutes
    }
}
```

**Client reconnection logic**:
```java
// Client-side reconnection (Java WebSocket client — e.g., OkHttp, Tyrus, or Android)
public class StockWebSocketClient {

    private final String wsUrl;
    private final List<String> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private WebSocket webSocket;

    public void connect() {
        webSocket = httpClient.newWebSocket(new Request.Builder().url(wsUrl).build(),
            new WebSocketListener() {
                @Override
                public void onOpen(WebSocket ws, Response response) {
                    retryCount.set(0);
                    // Re-subscribe to previously watched symbols on reconnect
                    if (!subscriptions.isEmpty()) {
                        ws.send("{\"action\":\"subscribe\",\"symbols\":" + 
                            toJsonArray(subscriptions) + "}");
                    }
                }

                @Override
                public void onMessage(WebSocket ws, String text) {
                    Map<String, Object> msg = parseJson(text);
                    if ("reconnect".equals(msg.get("type"))) {
                        ws.close(1000, "server requested reconnect");  // triggers onClosed → auto-reconnect
                    } else if ("price_update".equals(msg.get("type"))) {
                        onPriceUpdate(msg);
                    }
                }

                @Override
                public void onClosed(WebSocket ws, int code, String reason) {
                    scheduleReconnect();
                }

                @Override
                public void onFailure(WebSocket ws, Throwable t, Response response) {
                    scheduleReconnect();
                }
            });
    }

    private void scheduleReconnect() {
        // Exponential backoff with jitter: 1s, 2s, 4s, 8s... max 30s
        long delay = Math.min(1000L * (1L << retryCount.getAndIncrement()), 30_000);
        long jitter = (long) (delay * 0.5 * Math.random());
        scheduler.schedule(this::connect, delay + jitter, TimeUnit.MILLISECONDS);
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Real-time delivery** | WebSocket (push) over REST polling | < 200ms latency; efficient for high-frequency; bi-directional |
| **Price cache** | Redis Hash per stock | Sub-ms reads; rich data structures (Hash, Sorted Set, Pub/Sub); single source for current price |
| **WS broadcast** | Redis Pub/Sub → WebSocket servers | Simple; < 1ms latency; servers subscribe only to needed symbols; fire-and-forget OK (next tick corrects) |
| **Time-series DB** | TimescaleDB (PostgreSQL extension) | SQL familiarity; continuous aggregates auto-rollup 1min→5min→1hr→1day; retention policies; ACID |
| **Event stream** | Kafka (partitioned by symbol) | Durable; ordered per stock; multiple consumers; 24h replay for recovery |
| **Top movers** | Redis Sorted Sets (ZADD + ZREVRANGE) | O(log N) update, O(K) query; real-time; trivial memory (15 MB for 50K stocks) |
| **WS routing** | Consistent hashing by user_id | Sticky sessions; minimal disruption on server failure (only 1/N users affected) |
| **Feed reliability** | Primary + secondary + tertiary data providers | Cross-validation; auto-failover; sequence number tracking for gap detection |
| **Consistency** | Strong for current price (Redis master), eventual for history/aggregates | Users see latest price instantly; chart data can lag by aggregate window |
| **Historical retention** | Tiered: 1min=90d, 5min=1yr, 1hr=5yr, 1day=forever | Balances storage cost with query needs; older data at coarser granularity |

## Interview Talking Points

1. **"Two-tier fan-out: Redis Pub/Sub → WebSocket servers → local connections"** — Price Processor publishes 1 message to Redis channel. Only servers with subscribers receive it. Each server does local fan-out to its connections. Reduces 1M messages to ~500.
2. **"Kafka partitioned by symbol for strict ordering"** — All AAPL events → same partition → single consumer → no out-of-order prices. Critical for financial data accuracy.
3. **"TimescaleDB continuous aggregates for multi-resolution charts"** — Raw 1-min data auto-rolls up to 5min, 1hr, 1day. Query the right table for the right resolution. "1 year daily" = 252 rows from pre-computed view, < 10ms.
4. **"Redis Sorted Sets for top movers"** — ZADD on every tick (75K/sec), ZREVRANGE for top-K in O(K). Same Redis cluster used for price cache + Pub/Sub + sorted sets — no new infra.
5. **"Feed Handler near exchange, validate before Kafka"** — Deployed regionally for < 20ms network. Validates price range, timestamp freshness, anomaly detection (> 20% change). Bad data never enters pipeline.
6. **"Consistent hashing for WebSocket sticky sessions"** — hash(user_id) → server. If server dies, only 1/500 users reconnect. Rolling deploys: drain → reconnect signal → shutdown. Client auto-reconnects with exponential backoff + jitter.
7. **"Strong consistency for current price, eventual for aggregates"** — Single writer per symbol (Kafka partition). Redis master for reads (no replica lag). Historical aggregates can lag by window size — acceptable for charts.
8. **"Bandwidth optimization: delta encoding + binary protocol + compression"** — 200 bytes JSON → 60 bytes compressed binary = 70% reduction. Message batching (100ms window) reduces per-message overhead.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Real-Time Sports Scores** | Same push architecture (WebSocket + Pub/Sub) | Lower update frequency; fewer "stocks" (games) but more diverse data |
| **Cryptocurrency Exchange** | Same price streaming + OHLCV | 24/7 markets (no market hours logic); higher volatility; crypto-specific protocols |
| **Leaderboard System** | Same Top-K pattern (Redis Sorted Sets) | Leaderboards update on user actions; our movers update on market trades |
| **Chat/Messaging (WhatsApp)** | Same WebSocket connection management | Chat has bidirectional messages; stock prices are primarily server→client push |
| **Metrics/Monitoring (Datadog)** | Same time-series storage pattern | Metrics have more dimensions; stock data is simpler (symbol + OHLCV) |
| **News Feed (Twitter)** | Same fan-out challenge | Twitter has social graph fan-out; we have subscription-based fan-out |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Real-time cache** | Redis | Memcached | If no Pub/Sub or Sorted Sets needed; multi-threaded for pure KV cache |
| **Time-series DB** | TimescaleDB | InfluxDB | If no JOINs needed; better compression; pure time-series workload |
| **Event stream** | Kafka | Amazon Kinesis / Pulsar | Kinesis for AWS-native; Pulsar for built-in geo-replication |
| **Stream processing** | Apache Flink | Kafka Streams | KStreams if already using Kafka; simpler; no separate cluster |
| **WebSocket server** | Custom (Netty/Go) | Socket.io / AWS AppSync | Socket.io for rapid prototyping; AppSync for managed WebSocket |
| **WS broadcast** | Redis Pub/Sub | NATS / RabbitMQ | NATS for extreme low latency (< 0.5ms); RabbitMQ for routing flexibility |
| **Top movers** | Redis Sorted Set | Flink windowed aggregation | Flink for complex trending (time-decay, weighted scores) |
| **Metadata DB** | PostgreSQL | DynamoDB | DynamoDB for serverless; if no complex queries needed |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- FIX Protocol (Financial Information eXchange) for Market Data
- TimescaleDB Continuous Aggregates Documentation
- Redis Pub/Sub and Sorted Sets for Real-Time Systems
- Consistent Hashing for Distributed Connection Management
- WebSocket Protocol (RFC 6455) and Scaling Patterns
- Apache Kafka Partitioning for Ordered Event Processing
- Circuit Breaker Rules (NYSE/NASDAQ Rule 80B)
