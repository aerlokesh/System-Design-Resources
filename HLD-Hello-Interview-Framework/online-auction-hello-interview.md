# Online Auction System (Instagram Auctions) - Hello Interview Framework

> **Question**: Design an auction system where users can create auction posts for items, place bids, view top bidders, and receive notifications when auctions end. Think of it as a blend of Instagram and eBay: rich media posts drive discovery, and a simple auction workflow determines a single winner.
>
> **Asked at**: Meta, TikTok, Apexx Global, Adobe
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
1. **Create Auction**: Sellers can create auction posts with photos/videos, item description, starting price, and auction end time.
2. **Place Bids**: Users can place bids on active auctions. A bid must be higher than the current highest bid.
3. **View Auction & Top Bidders**: Users can view auction details including current price, top N bidders, and time remaining.
4. **Real-Time Bid Updates**: All users viewing an auction see new bids in real-time (< 1 second).
5. **End-of-Auction Workflow**: When the timer expires, the system determines the winner, notifies the seller and winner, and blocks further bids.

#### Nice to Have (P1)
- "Buy It Now" price (instant purchase, ends auction early).
- Anti-sniping protection (extend auction by 2 minutes if bid placed in last 30 seconds).
- Bid increment rules (minimum bid increment, e.g., +$1 or +5%).
- Proxy/automatic bidding (set max bid, system auto-bids on your behalf).
- Auction feed discovery (trending auctions, personalized recommendations).
- Payment processing integration.

#### Below the Line (Out of Scope)
- Payment/escrow system (assume external payment service).
- Shipping/logistics.
- Fraud detection (assume separate ML pipeline).
- Full social media features (follows, likes, comments on non-auction posts).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Throughput** | 10,000+ bids/sec sustained, 100K peak (celebrity auctions) | Hot auctions get enormous traffic |
| **Bid Latency** | < 200ms end-to-end (bid placed → confirmed) | Users expect instant feedback |
| **Real-Time Updates** | < 1 second from bid placed to all viewers notified | Live auction experience |
| **Consistency** | Strong consistency for bid ordering (linearizable) | MUST never accept a lower bid after a higher one |
| **Availability** | 99.99% for bid placement | Missing a bid = lost revenue |
| **Auction Timer Accuracy** | ±1 second for auction end | Fair for all bidders |
| **Scalability** | Support 1M concurrent auctions, 100M registered users | Instagram-scale platform |
| **Durability** | Zero bid loss; every accepted bid is permanent | Financial transaction |

### Capacity Estimation

```
Users & Auctions:
  Registered users: 100M
  Active auctions at any time: 1M
  Average auction duration: 24 hours
  New auctions created/day: ~1M

Bids:
  Average bids per auction: 50
  Total bids/day: 1M × 50 = 50M bids/day
  Sustained: ~580 bids/sec
  Peak (celebrity auction with 1M viewers): 100K bids/sec burst
  
  A single hot auction: 10,000 bids in 60 seconds = 167 bids/sec on ONE item

Real-Time Updates:
  Average viewers per auction: 100
  Hot auction viewers: 100,000-1,000,000
  When a bid is placed on a hot auction: fanout to 1M WebSocket connections
  
Storage:
  Per auction: 5 KB (metadata) + 2 MB (media) = ~2 MB
  Per bid: 200 bytes (bidder, amount, timestamp, auction_id)
  Daily: 1M × 2 MB (auctions) + 50M × 200 bytes (bids) = 2 TB + 10 GB
  
WebSocket Connections:
  Peak concurrent: 10M users watching auctions
  WebSocket servers: 10M / 50K connections per server = 200 servers
```

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│  User             │ 1───* │  Auction          │ 1───* │  Bid              │
├──────────────────┤       ├──────────────────┤       ├──────────────────┤
│ user_id (PK)     │       │ auction_id (PK)   │       │ bid_id (PK)       │
│ username         │       │ seller_id (FK)    │       │ auction_id (FK)   │
│ display_name     │       │ title             │       │ bidder_id (FK)    │
│ avatar_url       │       │ description       │       │ amount            │
│ balance          │       │ media_urls        │       │ created_at        │
└──────────────────┘       │ starting_price    │       │ status            │
                           │ current_price     │       └──────────────────┘
                           │ buy_now_price     │
                           │ status            │       ┌──────────────────┐
                           │ starts_at         │       │  Notification     │
                           │ ends_at           │       ├──────────────────┤
                           │ winner_id         │       │ notification_id   │
                           │ bid_count         │       │ user_id (FK)      │
                           │ created_at        │       │ auction_id (FK)   │
                           └──────────────────┘       │ type              │
                                                       │ message           │
                                                       │ read              │
                                                       │ created_at        │
                                                       └──────────────────┘
```

### Entity 1: Auction
```sql
CREATE TABLE auctions (
    auction_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID NOT NULL REFERENCES users(user_id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    media_urls      TEXT[] NOT NULL,             -- Array of S3 URLs (photos/videos)
    starting_price  DECIMAL(12,2) NOT NULL,
    current_price   DECIMAL(12,2) NOT NULL,      -- Denormalized: highest bid amount
    buy_now_price   DECIMAL(12,2),               -- Optional: instant purchase price
    bid_increment   DECIMAL(12,2) DEFAULT 1.00,  -- Minimum bid increment
    status          VARCHAR(20) DEFAULT 'ACTIVE', -- SCHEDULED, ACTIVE, ENDED, CANCELLED
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    original_end    TIMESTAMPTZ NOT NULL,         -- Before any anti-snipe extensions
    winner_id       UUID,                         -- Set when auction ends
    winning_bid_id  UUID,
    bid_count       INT DEFAULT 0,
    viewer_count    INT DEFAULT 0,                -- Approximate, from WebSocket connections
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    version         BIGINT DEFAULT 0              -- Optimistic locking
);

CREATE INDEX idx_auctions_status ON auctions (status, ends_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_auctions_seller ON auctions (seller_id, created_at DESC);
CREATE INDEX idx_auctions_ending_soon ON auctions (ends_at) WHERE status = 'ACTIVE';
```

### Entity 2: Bid
```sql
CREATE TABLE bids (
    bid_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id      UUID NOT NULL REFERENCES auctions(auction_id),
    bidder_id       UUID NOT NULL REFERENCES users(user_id),
    amount          DECIMAL(12,2) NOT NULL,
    status          VARCHAR(20) DEFAULT 'ACCEPTED', -- ACCEPTED, OUTBID, WINNING, REJECTED
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT uk_auction_amount UNIQUE (auction_id, amount) -- No two bids with same amount
);

CREATE INDEX idx_bids_auction ON bids (auction_id, amount DESC);
CREATE INDEX idx_bids_bidder ON bids (bidder_id, created_at DESC);
```

### Entity 3: AuctionTimer (Durable Timer)
```sql
-- Stored in Redis or a dedicated timer service
-- Key: auction_timer:{auction_id}
-- Value: {auction_id, ends_at, callback_url}
-- TTL: set to (ends_at - now)

-- When TTL expires, the timer service triggers the end-of-auction workflow.
-- Must survive server restarts (durable timer, not in-memory setTimeout).
```

---

## 3️⃣ API Design

### Authentication
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### 1. Create Auction
```
POST /api/v1/auctions

Request:
{
  "title": "Signed Jersey - LeBron James",
  "description": "Game-worn, authenticated by PSA",
  "media_urls": ["s3://bucket/jersey-front.jpg", "s3://bucket/jersey-back.jpg"],
  "starting_price": 500.00,
  "buy_now_price": 10000.00,
  "bid_increment": 25.00,
  "duration_hours": 48,
  "starts_at": "2025-01-10T18:00:00Z"
}

Response (201 Created):
{
  "auction_id": "a1b2c3d4-...",
  "title": "Signed Jersey - LeBron James",
  "status": "SCHEDULED",
  "starting_price": 500.00,
  "current_price": 500.00,
  "ends_at": "2025-01-12T18:00:00Z",
  "created_at": "2025-01-08T10:00:00Z"
}
```

### 2. Place Bid
```
POST /api/v1/auctions/{auction_id}/bids

Request:
{
  "amount": 750.00
}

Response (201 Created — Bid Accepted):
{
  "bid_id": "b5e6f7g8-...",
  "auction_id": "a1b2c3d4-...",
  "amount": 750.00,
  "status": "ACCEPTED",
  "new_current_price": 750.00,
  "your_rank": 1,
  "created_at": "2025-01-10T19:30:00Z"
}

Response (409 Conflict — Bid Too Low):
{
  "error": "BID_TOO_LOW",
  "message": "Bid must be at least $775.00 (current: $750.00 + increment: $25.00)",
  "current_price": 750.00,
  "minimum_bid": 775.00
}

Response (410 Gone — Auction Ended):
{
  "error": "AUCTION_ENDED",
  "message": "This auction ended at 2025-01-12T18:00:00Z",
  "winner": "user_xyz"
}
```

### 3. Get Auction Details
```
GET /api/v1/auctions/{auction_id}

Response (200 OK):
{
  "auction_id": "a1b2c3d4-...",
  "title": "Signed Jersey - LeBron James",
  "seller": { "user_id": "...", "username": "lebron_fan", "avatar_url": "..." },
  "media_urls": ["..."],
  "starting_price": 500.00,
  "current_price": 750.00,
  "buy_now_price": 10000.00,
  "bid_increment": 25.00,
  "bid_count": 15,
  "viewer_count": 4523,
  "status": "ACTIVE",
  "ends_at": "2025-01-12T18:00:00Z",
  "time_remaining_seconds": 172800,
  "top_bidders": [
    { "rank": 1, "username": "collector99", "amount": 750.00, "bid_at": "..." },
    { "rank": 2, "username": "jersey_king", "amount": 700.00, "bid_at": "..." },
    { "rank": 3, "username": "sports_fan", "amount": 650.00, "bid_at": "..." }
  ]
}
```

### 4. Get Bid History
```
GET /api/v1/auctions/{auction_id}/bids?limit=50

Response (200 OK):
{
  "bids": [
    { "bid_id": "...", "bidder": "collector99", "amount": 750.00, "created_at": "..." },
    { "bid_id": "...", "bidder": "jersey_king", "amount": 700.00, "created_at": "..." }
  ],
  "total": 15,
  "pagination": { "next_cursor": "..." }
}
```

### 5. Subscribe to Auction (WebSocket)
```
WebSocket: wss://ws.auction.com/v1/auctions/{auction_id}/live

Server → Client (Bid Update):
{
  "type": "BID_UPDATE",
  "auction_id": "a1b2c3d4-...",
  "bid": {
    "bidder": "collector99",
    "amount": 750.00,
    "rank": 1,
    "bid_at": "2025-01-10T19:30:00Z"
  },
  "current_price": 750.00,
  "bid_count": 15,
  "time_remaining_seconds": 172800
}

Server → Client (Auction Ended):
{
  "type": "AUCTION_ENDED",
  "auction_id": "a1b2c3d4-...",
  "winner": { "user_id": "...", "username": "collector99" },
  "winning_bid": 750.00,
  "total_bids": 15
}

Server → Client (Anti-Snipe Extension):
{
  "type": "TIME_EXTENDED",
  "auction_id": "a1b2c3d4-...",
  "new_ends_at": "2025-01-12T18:02:00Z",
  "reason": "Bid placed in final 30 seconds"
}
```

---

## 4️⃣ Data Flow

### Flow 1: Placing a Bid (Happy Path)
```
1. User submits: POST /api/v1/auctions/{id}/bids { amount: 750 }
   ↓
2. API Gateway → Bid Service
   ↓
3. Bid Service:
   a. Acquire distributed lock: LOCK auction:{auction_id}
   b. Read current auction state from DB
   c. Validate:
      - Auction is ACTIVE
      - Auction hasn't ended (ends_at > NOW())
      - Amount >= current_price + bid_increment
      - Bidder != seller (can't bid on own auction)
      - Bidder has sufficient balance (if required)
   d. If valid:
      - INSERT bid into bids table
      - UPDATE auction: current_price = amount, bid_count++
      - Release lock
   e. If invalid: return error, release lock
   ↓
4. Publish event to Kafka: "bid-placed"
   { auction_id, bid_id, bidder_id, amount, timestamp }
   ↓
5. Bid Event Consumers:
   a. WebSocket Fanout Service → push BID_UPDATE to all connected viewers
   b. Notification Service → notify previous highest bidder ("You've been outbid!")
   c. Anti-Snipe Service → check if bid is in final 30 seconds, extend if needed
   d. Analytics Service → update bid statistics
   ↓
6. Return 201 Created to bidder
   Total latency: < 200ms
```

### Flow 2: Auction Ends (Timer Fires)
```
1. Durable Timer expires for auction_id at ends_at
   ↓
2. Auction End Service:
   a. Acquire distributed lock: LOCK auction:{auction_id}
   b. Read auction state
   c. If status is already ENDED → skip (idempotent)
   d. Determine winner: SELECT bid with MAX(amount) for this auction
   e. UPDATE auction: status = 'ENDED', winner_id = top_bidder
   f. Release lock
   ↓
3. Publish event to Kafka: "auction-ended"
   { auction_id, winner_id, winning_bid, total_bids }
   ↓
4. Event Consumers:
   a. WebSocket Fanout → push AUCTION_ENDED to all viewers
   b. Notification Service:
      - Notify winner: "Congratulations! You won the auction"
      - Notify seller: "Your auction has ended. Winner: collector99"
      - Notify all other bidders: "Auction ended. You didn't win."
   c. Payment Service → initiate payment from winner to seller
   d. Feed Service → update auction post to show "SOLD" status
   ↓
5. Reject any bids that arrive after this point (410 Gone)
```

### Flow 3: Real-Time Bid Fanout (WebSocket)
```
1. User opens auction page → WebSocket connection established
   wss://ws.auction.com/v1/auctions/{auction_id}/live
   ↓
2. WebSocket server subscribes this connection to channel: auction:{auction_id}
   ↓
3. When a new bid is placed:
   a. Bid Event → Kafka → WebSocket Fanout Service
   b. Fanout Service looks up all WebSocket servers with subscribers for this auction
   c. Push BID_UPDATE message to each server
   d. Each server pushes to all connected clients for that auction
   ↓
4. Client receives update and refreshes UI (new price, new top bidder)
   ↓
   Latency: Bid placed → client notified: < 500ms

Hot auction (1M viewers):
  - 1M WebSocket connections across ~200 servers
  - Each server handles ~5000 connections for this auction
  - Fanout via Redis Pub/Sub or Kafka → all 200 servers simultaneously
  - Total fanout time: < 500ms
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                   │
│   [Mobile App]      [Web App]      [API Consumers]                    │
│   HTTP REST + WebSocket for real-time updates                         │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │    API Gateway / LB      │
              │  • Auth, Rate Limiting   │
              │  • WebSocket upgrade     │
              └────────────┬────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                  ▼
┌─────────────────┐ ┌───────────────┐ ┌─────────────────┐
│  Auction Service │ │  Bid Service  │ │  WebSocket       │
│  • Create/Read   │ │  • Place bid  │ │  Gateway         │
│  • End auction   │ │  • Validate   │ │  • 200 servers   │
│  • Search/Feed   │ │  • Lock+Write │ │  • 10M conns     │
└───────┬─────────┘ └──────┬────────┘ └────────┬─────────┘
        │                  │                    │
        │                  ▼                    │
        │       ┌──────────────────┐            │
        │       │  Redis Cluster    │            │
        │       │  • Distributed    │            │
        │       │    locks          │            │
        │       │  • Auction cache  │            │
        │       │  • Pub/Sub for    │◄───────────┘
        │       │    WebSocket      │
        │       │    fanout         │
        │       └──────────────────┘
        │                  │
        ▼                  ▼
┌──────────────────────────────────┐
│           PostgreSQL              │
│  • Auctions table (source of     │
│    truth for auction state)      │
│  • Bids table (all bids)         │
│  • Strong consistency via        │
│    row-level locks               │
│  Master + 2 Read Replicas        │
└──────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────┐
│           Kafka Cluster           │
│  Topics:                         │
│  • bid-placed                    │
│  • auction-ended                 │
│  • auction-created               │
│  • notifications                 │
└───────────────┬──────────────────┘
                │
    ┌───────────┼───────────────┐
    ▼           ▼               ▼
┌────────┐ ┌──────────┐ ┌────────────┐
│Notif.  │ │ Timer    │ │ Analytics  │
│Service │ │ Service  │ │ Service    │
│• Push  │ │• Durable │ │• Bid stats │
│• Email │ │  timers  │ │• Trending  │
│• SMS   │ │• End     │ │  auctions  │
└────────┘ │  auction │ └────────────┘
           │  workflow│
           └──────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **API Gateway** | Auth, rate limiting, WS upgrade | Kong / Envoy | Horizontal |
| **Auction Service** | CRUD auctions, search, feed | Java/Spring Boot | Stateless, horizontal |
| **Bid Service** | Place bids with strong consistency | Java/Spring Boot | Stateless (lock externalized to Redis) |
| **WebSocket Gateway** | Real-time bid updates to viewers | Netty / Spring WebFlux | 200+ servers, 50K conns each |
| **Redis Cluster** | Distributed locks, Pub/Sub, cache | Redis 7+ Cluster | 6+ nodes, sharded |
| **PostgreSQL** | Source of truth for auctions + bids | PostgreSQL 15+ | Master + read replicas |
| **Kafka** | Event bus for async processing | Apache Kafka | Standard cluster |
| **Timer Service** | Durable timers for auction end | Custom + Redis / DB polling | HA pair |
| **Notification Service** | Push, email, SMS notifications | Java microservice | Horizontal |
| **S3** | Media storage (photos/videos) | AWS S3 + CloudFront CDN | Managed |

### Why This Architecture?

1. **Strong consistency for bids**: PostgreSQL row-level locks + Redis distributed locks ensure no two bids can create a race condition.
2. **Separated read and write paths**: Bid writes go through a locked critical path; reads are served from Redis cache + read replicas.
3. **WebSocket for real-time**: Kafka → Redis Pub/Sub → WebSocket servers → clients. Decoupled fanout that scales to millions.
4. **Durable timers**: Auction end times must survive server restarts. Not `setTimeout()` in memory!

---

## 6️⃣ Deep Dives

### Deep Dive 1: Bid Contention & Distributed Locking

**The Problem**: A celebrity auction gets 10,000 bids in 60 seconds. Multiple bids arrive simultaneously. We MUST ensure: (1) only one bid is accepted at a time, (2) the highest bid always wins, (3) no bid is lost or double-counted.

**Solution: Redis Distributed Lock + PostgreSQL Row-Level Lock (Belt and Suspenders)**

```java
public class BidService {
    private final RedissonClient redisson;
    private final AuctionRepository auctionRepo;
    private final BidRepository bidRepo;
    private final KafkaProducer<String, BidEvent> kafka;

    /**
     * Place a bid with strong consistency guarantees.
     * Uses Redis distributed lock + PostgreSQL FOR UPDATE.
     */
    @Transactional
    public BidResult placeBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        // 1. Acquire Redis distributed lock (prevents concurrent bid processing)
        RLock lock = redisson.getLock("auction:lock:" + auctionId);
        boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS); // wait 5s, hold 10s
        
        if (!acquired) {
            throw new BidException("Auction is busy, please retry");
        }
        
        try {
            // 2. Read current auction state WITH row-level lock
            Auction auction = auctionRepo.findByIdForUpdate(auctionId) // SELECT ... FOR UPDATE
                .orElseThrow(() -> new NotFoundException("Auction not found"));
            
            // 3. Validate bid
            validateBid(auction, bidderId, amount);
            
            // 4. Create bid record
            Bid bid = Bid.builder()
                .bidId(UUID.randomUUID())
                .auctionId(auctionId)
                .bidderId(bidderId)
                .amount(amount)
                .status(BidStatus.ACCEPTED)
                .createdAt(Instant.now())
                .build();
            bidRepo.save(bid);
            
            // 5. Update auction current price
            UUID previousHighBidderId = auction.getWinnerId();
            auction.setCurrentPrice(amount);
            auction.setWinnerId(bidderId);
            auction.setWinningBidId(bid.getBidId());
            auction.setBidCount(auction.getBidCount() + 1);
            auction.setVersion(auction.getVersion() + 1);
            auctionRepo.save(auction);
            
            // 6. Publish event (async, outside the lock)
            kafka.send(new ProducerRecord<>("bid-placed", auctionId.toString(),
                BidEvent.builder()
                    .auctionId(auctionId)
                    .bidId(bid.getBidId())
                    .bidderId(bidderId)
                    .amount(amount)
                    .previousHighBidderId(previousHighBidderId)
                    .timestamp(bid.getCreatedAt())
                    .build()));
            
            // 7. Update Redis cache
            redisCache.updateAuctionPrice(auctionId, amount, bidderId);
            
            return BidResult.accepted(bid);
            
        } finally {
            lock.unlock();
        }
    }

    private void validateBid(Auction auction, UUID bidderId, BigDecimal amount) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new BidException("AUCTION_NOT_ACTIVE", "Auction is " + auction.getStatus());
        }
        if (Instant.now().isAfter(auction.getEndsAt())) {
            throw new BidException("AUCTION_ENDED", "Auction has ended");
        }
        if (bidderId.equals(auction.getSellerId())) {
            throw new BidException("SELF_BID", "Cannot bid on your own auction");
        }
        BigDecimal minimumBid = auction.getCurrentPrice().add(auction.getBidIncrement());
        if (amount.compareTo(minimumBid) < 0) {
            throw new BidException("BID_TOO_LOW", 
                "Minimum bid is " + minimumBid + " (current: " + auction.getCurrentPrice() + 
                " + increment: " + auction.getBidIncrement() + ")");
        }
    }
}
```

**Why Both Redis Lock AND PostgreSQL FOR UPDATE?**
```
Redis Lock alone:
  ✓ Fast (< 1ms to acquire)
  ✗ Not durable: Redis crash → lock lost → concurrent writes
  ✗ Clock skew can cause lock expiry while still holding

PostgreSQL FOR UPDATE alone:
  ✓ Durable, ACID compliant
  ✗ Slower (DB round-trip for every bid)
  ✗ Lock contention at DB level under high load

Both together (belt and suspenders):
  Redis Lock: First line of defense, serializes 99% of concurrent bids quickly
  PostgreSQL FOR UPDATE: Safety net, guarantees correctness even if Redis fails
  Result: Fast + correct
```

---

### Deep Dive 2: Durable Timers for Auction End

**The Problem**: When an auction is created with `ends_at = 2025-01-12T18:00:00Z`, the system must fire an end-of-auction workflow at exactly that time — even if servers restart, crash, or the timer service dies.

**Solution: Database-Backed Timer with Polling**

```java
public class AuctionTimerService {
    private final AuctionRepository auctionRepo;
    private final AuctionEndService endService;
    
    /**
     * Poll for auctions that have expired but haven't been ended yet.
     * Runs every second. The DB query is efficient due to index.
     */
    @Scheduled(fixedRate = 1_000) // Every 1 second
    public void pollExpiredAuctions() {
        Instant now = Instant.now();
        
        // Find auctions past their end time but still ACTIVE
        List<Auction> expired = auctionRepo.findExpiredActive(now);
        // SQL: SELECT * FROM auctions WHERE status = 'ACTIVE' AND ends_at <= ?
        //      ORDER BY ends_at LIMIT 100
        
        for (Auction auction : expired) {
            try {
                endService.endAuction(auction.getAuctionId());
            } catch (Exception e) {
                log.error("Failed to end auction {}: {}", auction.getAuctionId(), e.getMessage());
                // Will retry on next poll cycle (1 second later)
            }
        }
    }
}
```

**Why DB Polling Instead of In-Memory Timers?**
```
In-memory timer (setTimeout / ScheduledExecutorService):
  ✗ Lost on server restart / deployment
  ✗ Can only run on one server (not HA)

DB Polling:
  ✓ Survives restarts (timers are just rows in DB)
  ✓ ±1 second accuracy (polling interval)
  ✓ Naturally idempotent
  ✓ Index on (status, ends_at) makes polling O(log N)
```

---

### Deep Dive 3: Anti-Sniping Protection

**The Problem**: "Sniping" — placing a bid in the last second so others can't respond.

```java
public class AntiSnipeService {
    private static final int SNIPE_WINDOW_SECONDS = 30;
    private static final int EXTENSION_SECONDS = 120;

    public void checkAndExtend(BidEvent event) {
        Auction auction = auctionRepo.findById(event.getAuctionId()).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) return;

        long secondsUntilEnd = Duration.between(event.getTimestamp(), auction.getEndsAt()).getSeconds();
        if (secondsUntilEnd <= SNIPE_WINDOW_SECONDS && secondsUntilEnd > 0) {
            Instant newEndTime = auction.getEndsAt().plusSeconds(EXTENSION_SECONDS);
            auction.setEndsAt(newEndTime);
            auctionRepo.save(auction);
            timerService.reschedule(auction.getAuctionId(), newEndTime);

            kafka.send(new ProducerRecord<>("auction-updates", auction.getAuctionId().toString(),
                AuctionUpdate.builder().type("TIME_EXTENDED")
                    .auctionId(auction.getAuctionId()).newEndsAt(newEndTime)
                    .reason("Bid placed in final " + SNIPE_WINDOW_SECONDS + " seconds").build()));
        }
    }
}
```

---

### Deep Dive 4: WebSocket Fanout at Scale (1M Viewers)

```java
public class WebSocketFanoutService {
    public void fanoutBidUpdate(BidEvent event) {
        String channel = "auction:" + event.getAuctionId();
        BidUpdateMessage msg = BidUpdateMessage.builder()
            .type("BID_UPDATE").auctionId(event.getAuctionId())
            .amount(event.getAmount()).bidder(event.getBidderId().toString()).build();
        redisson.getTopic(channel).publish(msg); // All WS servers receive via Pub/Sub
    }
}

public class WebSocketServer {
    private final Map<UUID, Set<WebSocketSession>> subs = new ConcurrentHashMap<>();

    public void onConnect(WebSocketSession session, UUID auctionId) {
        subs.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(session);
        redisSubscriber.subscribe("auction:" + auctionId, msg -> broadcastLocal(auctionId, msg));
    }

    private void broadcastLocal(UUID auctionId, BidUpdateMessage msg) {
        Set<WebSocketSession> sessions = subs.get(auctionId);
        if (sessions == null) return;
        String json = objectMapper.writeValueAsString(msg);
        sessions.stream().filter(WebSocketSession::isOpen)
                .forEach(s -> s.sendMessage(new TextMessage(json)));
    }
}
```

**Fanout**: Kafka → Redis Pub/Sub → 200 WS servers → 1M clients. Total < 500ms.

---

### Deep Dive 5: Read Path — Redis Cache + Sorted Set Leaderboard

```java
public class BidLeaderboard {
    public void recordBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        redis.zadd("leaderboard:" + auctionId, amount.doubleValue(), bidderId.toString());
    }

    public List<LeaderboardEntry> getTopBidders(UUID auctionId, int n) {
        Set<Tuple> topN = redis.zrevrangeWithScores("leaderboard:" + auctionId, 0, n - 1);
        int rank = 1;
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Tuple t : topN) entries.add(new LeaderboardEntry(rank++, t.getElement(), BigDecimal.valueOf(t.getScore())));
        return entries;
    }
}
```

Auction details served from Redis cache with write-through updates on every bid. 60s TTL, sub-ms reads.

---

### Deep Dive 6: Proxy/Automatic Bidding

```java
public class ProxyBidService {
    public void setMaxBid(UUID auctionId, UUID bidderId, BigDecimal maxAmount) {
        redis.set("proxy:" + auctionId + ":" + bidderId, maxAmount.toString());
        triggerProxyBids(auctionId);
    }

    public void triggerProxyBids(UUID auctionId) {
        Auction auction = auctionRepo.findById(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) return;

        BigDecimal nextMinBid = auction.getCurrentPrice().add(auction.getBidIncrement());
        Set<String> proxyKeys = redis.keys("proxy:" + auctionId + ":*");

        UUID bestBidder = null; BigDecimal bestMax = BigDecimal.ZERO;
        for (String key : proxyKeys) {
            UUID bidderId = extractBidderId(key);
            BigDecimal maxBid = new BigDecimal(redis.get(key));
            if (!bidderId.equals(auction.getWinnerId()) && maxBid.compareTo(nextMinBid) >= 0 && maxBid.compareTo(bestMax) > 0) {
                bestBidder = bidderId; bestMax = maxBid;
            }
        }
        if (bestBidder != null) bidService.placeBid(auctionId, bestBidder, nextMinBid);
    }
}
```

---

### Deep Dive 7: Hot Auction Sharding (100K Bids/Sec)

For the 0.01% of auctions with extreme traffic, use **bid queue + batch processing**:

```java
public class HotAuctionBidProcessor {
    public void enqueueBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        redis.rpush("bid_queue:" + auctionId, objectMapper.writeValueAsString(
            Map.of("bidder", bidderId, "amount", amount, "ts", Instant.now())));
    }

    @Scheduled(fixedRate = 100) // Every 100ms
    public void processBidBatches() {
        for (UUID auctionId : hotAuctions) {
            List<String> bids = new ArrayList<>(); String bid;
            while ((bid = redis.lpop("bid_queue:" + auctionId)) != null) bids.add(bid);
            if (bids.isEmpty()) continue;

            BidRequest highest = bids.stream()
                .map(b -> objectMapper.readValue(b, BidRequest.class))
                .max(Comparator.comparing(BidRequest::getAmount)).orElse(null);
            if (highest != null) bidService.placeBid(auctionId, highest.getBidder(), highest.getAmount());
        }
    }
}
```

100ms batches reduce lock contention by 100x. Only the highest bid in each batch wins.

---

### Deep Dive 8: Auction Discovery Feed & Trending

```java
public class AuctionFeedService {
    public List<AuctionSummary> getEndingSoon(int limit) {
        Set<String> ids = redis.zrangeByScore("auctions:ending_soon",
            String.valueOf(Instant.now().getEpochSecond()),
            String.valueOf(Instant.now().plusHours(1).getEpochSecond()), 0, limit);
        return ids.stream().map(id -> getAuctionSummary(UUID.fromString(id))).collect(Collectors.toList());
    }

    public List<AuctionSummary> getTrending(int limit) {
        Set<Tuple> trending = redis.zrevrangeWithScores("auctions:trending", 0, limit - 1);
        return trending.stream().map(t -> getAuctionSummary(UUID.fromString(t.getElement()))).collect(Collectors.toList());
    }

    public void onBidPlaced(UUID auctionId) { redis.zincrby("auctions:trending", 1, auctionId.toString()); }

    @Scheduled(cron = "0 0 * * * *")
    public void decayTrendingScores() {
        Set<Tuple> all = redis.zrangeWithScores("auctions:trending", 0, -1);
        for (Tuple e : all) {
            double newScore = e.getScore() * 0.5;
            if (newScore < 1) redis.zrem("auctions:trending", e.getElement());
            else redis.zadd("auctions:trending", newScore, e.getElement());
        }
    }
}
```

---

### Deep Dive 9: Notification Fanout — Outbid, Won, Lost

```java
public class AuctionNotificationService {
    public void onBidPlaced(BidEvent event) {
        if (event.getPreviousHighBidderId() != null) {
            notificationService.send(Notification.builder()
                .userId(event.getPreviousHighBidderId()).type(NotificationType.OUTBID)
                .title("You've been outbid!")
                .message(String.format("Someone bid $%.2f on '%s'.", event.getAmount(), event.getAuctionTitle()))
                .channels(Set.of(Channel.PUSH, Channel.IN_APP)).build());
        }
    }

    public void onAuctionEnded(AuctionEndedEvent event) {
        if (event.getWinnerId() != null) {
            notificationService.send(Notification.builder().userId(event.getWinnerId())
                .type(NotificationType.AUCTION_WON).title("🎉 You won!")
                .message(String.format("You won '%s' for $%.2f.", event.getAuctionTitle(), event.getWinningAmount()))
                .channels(Set.of(Channel.PUSH, Channel.EMAIL, Channel.IN_APP)).build());
        }
        notificationService.send(Notification.builder().userId(event.getSellerId())
            .type(NotificationType.AUCTION_ENDED).title("Your auction has ended")
            .message(String.format("'%s' sold for $%.2f.", event.getAuctionTitle(), event.getWinningAmount()))
            .channels(Set.of(Channel.PUSH, Channel.EMAIL, Channel.IN_APP)).build());

        bidRepo.findDistinctBiddersByAuction(event.getAuctionId()).stream()
            .filter(id -> !id.equals(event.getWinnerId()))
            .forEach(bidderId -> notificationService.send(Notification.builder()
                .userId(bidderId).type(NotificationType.AUCTION_LOST).title("Auction ended")
                .message(String.format("'%s' was won for $%.2f.", event.getAuctionTitle(), event.getWinningAmount()))
                .channels(Set.of(Channel.IN_APP)).build()));
    }
}
```

---

### Deep Dive 10: Observability & Metrics

```java
public class AuctionMetrics {
    Counter bidsPlaced     = Counter.builder("bids.placed").register(registry);
    Counter bidsRejected   = Counter.builder("bids.rejected").register(registry);
    Timer   bidLatency     = Timer.builder("bids.latency_ms").register(registry);
    Gauge   activeAuctions = Gauge.builder("auctions.active", ...).register(registry);
    Counter auctionsEnded  = Counter.builder("auctions.ended").register(registry);
    Counter snipeExtensions= Counter.builder("auctions.snipe_extensions").register(registry);
    Gauge   wsConnections  = Gauge.builder("ws.connections.total", ...).register(registry);
    Timer   wsFanoutLatency= Timer.builder("ws.fanout.latency_ms").register(registry);
    Timer   timerAccuracy  = Timer.builder("timers.accuracy_ms").register(registry);
}
```

---

## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Bid consistency** | Redis lock + PG FOR UPDATE | Fast + correct |
| **Real-time** | WebSocket + Redis Pub/Sub | Low latency, 1M fanout |
| **Auction timer** | DB polling every 1s | Durable, survives restarts |
| **Leaderboard** | Redis ZSET | O(log N), real-time |
| **Hot auctions** | Queue + 100ms batch | 100x less lock contention |
| **Anti-sniping** | 2-min extension on last-30s bids | Fair, simple |
| **Read path** | Redis cache + write-through | Sub-ms, always fresh |
| **Fanout** | Kafka → Redis Pub/Sub → WS | Decoupled, scalable |

## 🎯 Interview Talking Points

1. **"Bids require linearizable consistency"** — Redis lock + PostgreSQL FOR UPDATE = fast + correct.
2. **"Durable timers, not setTimeout"** — DB-backed polling every 1s, idempotent, survives restarts.
3. **"Anti-sniping = 2-min extension on last-30s bids"** — Simple rule, huge UX improvement.
4. **"WebSocket fanout via Redis Pub/Sub"** — Kafka → Pub/Sub → 200 servers → 1M clients in < 500ms.
5. **"Hot auctions get batched"** — 100ms batch window, only highest bid wins, 100x less contention.
6. **"Bid → Event → Async fanout"** — Sync write path < 200ms. Everything else is async via Kafka.

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Deep Dives**: 10 topics
 later)
            }
        }
    }
}

public class AuctionEndService {
    
    /** End an auction. Must be idempotent (safe to call multiple times). */
    @Transactional
    public void endAuction(UUID auctionId) {
        RLock lock = redisson.getLock("auction:lock:" + auctionId);
        lock.lock(30, TimeUnit.SECONDS);
        
        try {
            Auction auction = auctionRepo.findByIdForUpdate(auctionId).orElseThrow();
            
            // Idempotency check: already ended?
            if (auction.getStatus() == AuctionStatus.ENDED) {
                log.info("Auction {} already ended, skipping", auctionId);
                return;
            }
            
            // Find winning bid
            Optional<Bid> winningBid = bidRepo.findTopByAuctionIdOrderByAmountDesc(auctionId);
            
            auction.setStatus(AuctionStatus.ENDED);
            if (winningBid.isPresent()) {
                auction.setWinnerId(winningBid.get().getBidderId());
                auction.setWinningBidId(winningBid.get().getBidId());
                winningBid.get().setStatus(BidStatus.WINNING);
                bidRepo.save(winningBid.get());
            }
            auctionRepo.save(auction);
            
            // Publish auction-ended event
            kafka.send(new ProducerRecord<>("auction-ended", auctionId.toString(),
                AuctionEndedEvent.builder()
                    .auctionId(auctionId)
                    .winnerId(auction.getWinnerId())
                    .winningAmount(auction.getCurrentPrice())
                    .totalBids(auction.getBidCount())
                    .build()));
                    
            log.info("Auction {} ended. Winner: {}, Amount: {}", 
                     auctionId, auction.getWinnerId(), auction.getCurrentPrice());
        } finally {
            lock.unlock();
        }
    }
}
```

**Why DB Polling Instead of In-Memory Timers?**
```
In-memory timer (setTimeout / ScheduledExecutorService):
  ✗ Lost on server restart
  ✗ Lost on deployment
  ✗ Can only run on one server (not HA)
  ✗ 1M concurrent timers = massive memory

DB Polling:
  ✓ Survives restarts (timers are just rows in the DB)
  ✓ Multiple timer service instances can poll (with distributed lock)
  ✓ ±1 second accuracy (polling interval)
  ✓ Naturally idempotent (status check before processing)
  ✓ Index on (status, ends_at) makes polling O(log N)
```

---

### Deep Dive 3: Anti-Sniping Protection

**The Problem**: "Sniping" — placing a bid in the last second so other bidders can't respond. Ruins the auction experience.

```java
public class AntiSnipeService {
    private static final int SNIPE_WINDOW_SECONDS = 30;   // Last 30 seconds
    private static final int EXTENSION_SECONDS = 120;      // Extend by 2 minutes

    /**
     * Called after every bid. Checks if the bid was in the snipe window
     * and extends the auction if needed.
     */
    public void checkAndExtend(BidEvent bidEvent) {
        Auction auction = auctionRepo.findById(bidEvent.getAuctionId()).orElseReturn(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) return;

        Instant bidTime = bidEvent.getTimestamp();
        Instant auctionEnd = auction.getEndsAt();
        long secondsUntilEnd = Duration.between(bidTime, auctionEnd).getSeconds();

        if (secondsUntilEnd <= SNIPE_WINDOW_SECONDS && secondsUntilEnd > 0) {
            // Bid placed in snipe window — extend the auction
            Instant newEndTime = auctionEnd.plusSeconds(EXTENSION_SECONDS);
            
            auction.setEndsAt(newEndTime);
            auctionRepo.save(auction);

            // Update the durable timer
            timerService.reschedule(auction.getAuctionId(), newEndTime);

            // Notify all viewers
            kafka.send(new ProducerRecord<>("auction-updates", auction.getAuctionId().toString(),
                AuctionUpdate.builder()
                    .type("TIME_EXTENDED")
                    .auctionId(auction.getAuctionId())
                    .newEndsAt(newEndTime)
                    .reason("Bid placed in final " + SNIPE_WINDOW_SECONDS + " seconds")
                    .build()));

            log.info("Anti-snipe: Auction {} extended to {} (bid at {} seconds before end)",
                     auction.getAuctionId(), newEndTime, secondsUntilEnd);
            metrics.counter("auction.snipe_extensions").increment();
        }
    }
}
```

---

### Deep Dive 4: WebSocket Fanout at Scale (1M Viewers)

**The Problem**: A celebrity auction has 1 million concurrent viewers. When a bid is placed, all 1M must be notified within 1 second.

```java
public class WebSocketFanoutService {
    private final RedissonClient redisson; // Redis Pub/Sub for cross-server fanout

    /**
     * Called when a bid is placed. Fans out to all WebSocket servers
     * that have clients watching this auction.
     */
    public void fanoutBidUpdate(BidEvent event) {
        String channel = "auction:" + event.getAuctionId();
        
        BidUpdateMessage message = BidUpdateMessage.builder()
            .type("BID_UPDATE")
            .auctionId(event.getAuctionId())
            .bidder(event.getBidderId().toString())
            .amount(event.getAmount())
            .bidCount(event.getBidCount())
            .timestamp(event.getTimestamp())
            .build();
        
        // Publish to Redis Pub/Sub — all WebSocket servers subscribed to this channel receive it
        redisson.getTopic(channel).publish(message);
        
        metrics.counter("ws.fanout.messages").increment();
    }
}

public class WebSocketServer {
    // Each WebSocket server handles ~50K concurrent connections
    private final Map<UUID, Set<WebSocketSession>> auctionSubscribers = new ConcurrentHashMap<>();

    /** Client connects and subscribes to an auction */
    public void onConnect(WebSocketSession session, UUID auctionId) {
        auctionSubscribers.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
                          .add(session);
        
        // Subscribe to Redis Pub/Sub channel for this auction (if not already)
        redisSubscriber.subscribe("auction:" + auctionId, message -> {
            broadcastToLocal(auctionId, message);
        });
    }

    /** Broadcast to all local clients watching this auction */
    private void broadcastToLocal(UUID auctionId, BidUpdateMessage message) {
        Set<WebSocketSession> sessions = auctionSubscribers.get(auctionId);
        if (sessions == null) return;
        
        String json = objectMapper.writeValueAsString(message);
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
        
        metrics.counter("ws.messages.sent").increment(sessions.size());
    }
}
```

**Fanout Architecture for 1M Viewers**:
```
Bid placed → Kafka "bid-placed" topic
  ↓
WebSocket Fanout Service consumes event
  ↓
Publishes to Redis Pub/Sub channel: "auction:{id}"
  ↓
All 200 WebSocket servers receive the message simultaneously
  ↓
Each server broadcasts to its local connections (~5000 per auction)
  ↓
200 × 5000 = 1,000,000 clients notified

Total latency: < 500ms
  Redis Pub/Sub publish: ~1ms
  Server receive + broadcast: ~100ms (parallel across servers)
  Client WebSocket delivery: ~200ms
```

---

### Deep Dive 5: Read Path Optimization — Serving Auction Details Fast

**The Problem**: Auction detail pages are read 1000x more than they're updated. The DB can't handle every read.

```java
public class AuctionReadService {
    private final JedisCluster redis;
    private final AuctionRepository auctionRepo;
    
    /**
     * Get auction details — read from cache first, DB fallback.
     * Cache is updated on every bid via write-through.
     */
    public AuctionDetailResponse getAuction(UUID auctionId) {
        // 1. Try Redis cache (< 1ms)
        String cacheKey = "auction:" + auctionId;
        String cached = redis.get(cacheKey);
        
        if (cached != null) {
            metrics.counter("auction.cache.hit").increment();
            return objectMapper.readValue(cached, AuctionDetailResponse.class);
        }
        
        // 2. Cache miss — read from DB read replica
        metrics.counter("auction.cache.miss").increment();
        Auction auction = auctionRepo.findById(auctionId).orElseThrow();
        List<Bid> topBids = bidRepo.findTopBidsByAuction(auctionId, 10);
        
        AuctionDetailResponse response = buildResponse(auction, topBids);
        
        // 3. Populate cache (60 second TTL)
        redis.setex(cacheKey, 60, objectMapper.writeValueAsString(response));
        
        return response;
    }
    
    /**
     * Called by BidService after a successful bid.
     * Updates the cache immediately (write-through).
     */
    public void updateCacheOnBid(UUID auctionId, Bid newBid, Auction updatedAuction) {
        String cacheKey = "auction:" + auctionId;
        
        // Update cached auction with new price and top bidder
        AuctionDetailResponse response = buildResponse(updatedAuction, 
            bidRepo.findTopBidsByAuction(auctionId, 10));
        
        redis.setex(cacheKey, 60, objectMapper.writeValueAsString(response));
    }
}
```

**Leaderboard (Top N Bidders) Using Redis Sorted Set**:
```java
public class BidLeaderboard {
    private final JedisCluster redis;

    /** Add a bid to the leaderboard */
    public void recordBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        String key = "leaderboard:" + auctionId;
        redis.zadd(key, amount.doubleValue(), bidderId.toString());
        redis.expire(key, 86400 * 3); // 3-day TTL
    }

    /** Get top N bidders */
    public List<LeaderboardEntry> getTopBidders(UUID auctionId, int n) {
        String key = "leaderboard:" + auctionId;
        // ZREVRANGE with scores — O(log N + M)
        Set<Tuple> topN = redis.zrevrangeWithScores(key, 0, n - 1);
        
        int rank = 1;
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Tuple tuple : topN) {
            entries.add(new LeaderboardEntry(rank++, tuple.getElement(), 
                                            BigDecimal.valueOf(tuple.getScore())));
        }
        return entries;
    }

    /** Get a specific bidder's rank */
    public Long getBidderRank(UUID auctionId, UUID bidderId) {
        String key = "leaderboard:" + auctionId;
        Long reverseRank = redis.zrevrank(key, bidderId.toString());
        return reverseRank != null ? reverseRank + 1 : null; // 1-indexed
    }
}
```

---

### Deep Dive 6: Proxy/Automatic Bidding

**The Problem**: Users want to set a maximum bid and have the system automatically bid on their behalf, just enough to stay ahead.

```java
public class ProxyBidService {
    
    /**
     * Set a maximum bid for a user. The system will auto-bid up to this amount.
     */
    public void setMaxBid(UUID auctionId, UUID bidderId, BigDecimal maxAmount) {
        // Store in Redis: "proxy:{auction_id}:{bidder_id}" → maxAmount
        String key = "proxy:" + auctionId + ":" + bidderId;
        redis.set(key, maxAmount.toString());
        
        // Immediately trigger a proxy bid if needed
        triggerProxyBids(auctionId);
    }

    /**
     * Called after every regular bid. Checks if any proxy bidders should auto-bid.
     */
    public void triggerProxyBids(UUID auctionId) {
        Auction auction = auctionRepo.findById(auctionId).orElseReturn(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) return;

        BigDecimal currentPrice = auction.getCurrentPrice();
        BigDecimal increment = auction.getBidIncrement();
        BigDecimal nextMinBid = currentPrice.add(increment);

        // Find all proxy bidders for this auction
        Set<String> proxyKeys = redis.keys("proxy:" + auctionId + ":*");
        
        // Find the proxy bidder with the highest max that can still bid
        UUID bestProxyBidder = null;
        BigDecimal bestProxyMax = BigDecimal.ZERO;
        
        for (String key : proxyKeys) {
            UUID bidderId = extractBidderId(key);
            BigDecimal maxBid = new BigDecimal(redis.get(key));
            
            // Skip the current highest bidder (don't bid against yourself)
            if (bidderId.equals(auction.getWinnerId())) continue;
            
            // Can this proxy bidder afford the next minimum bid?
            if (maxBid.compareTo(nextMinBid) >= 0 && maxBid.compareTo(bestProxyMax) > 0) {
                bestProxyBidder = bidderId;
                bestProxyMax = maxBid;
            }
        }
        
        if (bestProxyBidder != null) {
            // Place a bid at the minimum required amount (not the max)
            BigDecimal bidAmount = nextMinBid;
            bidService.placeBid(auctionId, bestProxyBidder, bidAmount);
            
            log.info("Proxy bid: {} bid {} on auction {} (max: {})", 
                     bestProxyBidder, bidAmount, auctionId, bestProxyMax);
        }
    }
}
```

---

### Deep Dive 7: Hot Auction Sharding — Handling 100K Bids/Sec on One Item

**The Problem**: Most auctions get modest traffic. But one celebrity auction might get 100K bids/sec — a single PostgreSQL row becomes a bottleneck.

**Solution: Bid Queue + Batch Processing**

```java
public class HotAuctionBidProcessor {
    /**
     * For "hot" auctions (detected by bid rate), instead of locking per bid,
     * we batch bids and process them in order.
     */
    
    // Incoming bids go into a per-auction Redis queue
    public void enqueueBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        String queueKey = "bid_queue:" + auctionId;
        String bidJson = objectMapper.writeValueAsString(
            Map.of("bidder", bidderId, "amount", amount, "ts", Instant.now()));
        redis.rpush(queueKey, bidJson);
    }

    /**
     * Process queued bids in batches every 100ms.
     * Only the HIGHEST bid in each batch actually wins.
     */
    @Scheduled(fixedRate = 100) // Every 100ms
    public void processBidBatches() {
        for (UUID auctionId : hotAuctions) {
            String queueKey = "bid_queue:" + auctionId;
            
            // Drain all pending bids atomically
            List<String> bids = new ArrayList<>();
            String bid;
            while ((bid = redis.lpop(queueKey)) != null) {
                bids.add(bid);
            }
            
            if (bids.isEmpty()) continue;
            
            // Find the highest bid in the batch
            BidRequest highest = bids.stream()
                .map(b -> objectMapper.readValue(b, BidRequest.class))
                .max(Comparator.comparing(BidRequest::getAmount))
                .orElse(null);
            
            if (highest != null) {
                // Only process the winning bid
                bidService.placeBid(auctionId, highest.getBidder(), highest.getAmount());
                
                // Reject all others (they were outbid before even being processed)
                // Notify them via WebSocket: "A higher bid was placed simultaneously"
            }
            
            metrics.counter("hot_auction.batch_size").record(bids.size());
        }
    }
}
```

---

### Deep Dive 8: Auction Discovery Feed & Trending

**The Problem**: Users need to discover interesting auctions. We need a feed sorted by relevance, trending, or ending soon.

```java
public class AuctionFeedService {
    
    /** Get auctions ending soon (for FOMO-driven engagement) */
    public List<AuctionSummary> getEndingSoon(int limit) {
        // Redis sorted set: score = ends_at epoch seconds
        Set<String> auctionIds = redis.zrangeByScore(
            "auctions:ending_soon", 
            String.valueOf(Instant.now().getEpochSecond()),
            String.valueOf(Instant.now().plusHours(1).getEpochSecond()),
            0, limit);
        
        return auctionIds.stream()
            .map(id -> getAuctionSummary(UUID.fromString(id)))
            .collect(Collectors.toList());
    }
    
    /** Get trending auctions (most bids in last hour) */
    public List<AuctionSummary> getTrending(int limit) {
        // Redis sorted set: score = bid count in last hour
        Set<Tuple> trending = redis.zrevrangeWithScores("auctions:trending", 0, limit - 1);
        
        return trending.stream()
            .map(t -> getAuctionSummary(UUID.fromString(t.getElement())))
            .collect(Collectors.toList());
    }
    
    /** Update trending score when a bid is placed */
    public void onBidPlaced(UUID auctionId) {
        // Increment bid count for this auction in the trending sorted set
        redis.zincrby("auctions:trending", 1, auctionId.toString());
        
        // Decay scores every hour (scheduled job)
    }
    
    /** Hourly: decay trending scores to favor recency */
    @Scheduled(cron = "0 0 * * * *")
    public void decayTrendingScores() {
        Set<Tuple> all = redis.zrangeWithScores("auctions:trending", 0, -1);
        for (Tuple entry : all) {
            double newScore = entry.getScore() * 0.5; // 50% decay per hour
            if (newScore < 1) {
                redis.zrem("auctions:trending", entry.getElement());
            } else {
                redis.zadd("auctions:trending", newScore, entry.getElement());
            }
        }
    }
}
```

---

### Deep Dive 9: Notification Fanout — Outbid, Won, Lost

**The Problem**: When a bid is placed, the previous highest bidder must be notified ("You've been outbid!"). When an auction ends, the winner, seller, and all bidders must be notified.

```java
public class AuctionNotificationService {
    
    /** Called when a new bid is placed */
    public void onBidPlaced(BidEvent event) {
        // Notify previous highest bidder: "You've been outbid!"
        if (event.getPreviousHighBidderId() != null) {
            notificationService.send(Notification.builder()
                .userId(event.getPreviousHighBidderId())
                .type(NotificationType.OUTBID)
                .title("You've been outbid!")
                .message(String.format("Someone bid $%.2f on '%s'. Place a higher bid to win!",
                    event.getAmount(), event.getAuctionTitle()))
                .auctionId(event.getAuctionId())
                .channels(Set.of(Channel.PUSH, Channel.IN_APP))
                .build());
        }
    }

    /** Called when an auction ends */
    public void onAuctionEnded(AuctionEndedEvent event) {
        // 1. Notify winner
        if (event.getWinnerId() != null) {
            notificationService.send(Notification.builder()
                .userId(event.getWinnerId())
                .type(NotificationType.AUCTION_WON)
                .title("🎉 You won!")
                .message(String.format("You won '%s' for $%.2f. Complete payment to claim.",
                    event.getAuctionTitle(), event.getWinningAmount()))
                .channels(Set.of(Channel.PUSH, Channel.EMAIL, Channel.IN_APP))
                .build());
        }

        // 2. Notify seller
        notificationService.send(Notification.builder()
            .userId(event.getSellerId())
            .type(NotificationType.AUCTION_ENDED)
            .title("Your auction has ended")
            .message(String.format("'%s' sold for $%.2f to %s.",
                event.getAuctionTitle(), event.getWinningAmount(), event.getWinnerUsername()))
            .channels(Set.of(Channel.PUSH, Channel.EMAIL, Channel.IN_APP))
            .build());

        // 3. Notify all other bidders (batch)
        List<UUID> losingBidders = bidRepo.findDistinctBiddersByAuction(event.getAuctionId())
            .stream()
            .filter(id -> !id.equals(event.getWinnerId()))
            .collect(Collectors.toList());
        
        for (UUID bidderId : losingBidders) {
            notificationService.send(Notification.builder()
                .userId(bidderId)
                .type(NotificationType.AUCTION_LOST)
                .title("Auction ended")
                .message(String.format("'%s' was won by someone else for $%.2f.",
                    event.getAuctionTitle(), event.getWinningAmount()))
                .channels(Set.of(Channel.IN_APP))
                .build());
        }
    }
}
```

---

### Deep Dive 10: Observability & Metrics

```java
public class AuctionMetrics {
    // Bid metrics
    Counter bidsPlaced       = Counter.builder("bids.placed").register(registry);
    Counter bidsRejected     = Counter.builder("bids.rejected").tag("reason", "too_low").register(registry);
    Timer   bidLatency       = Timer.builder("bids.latency_ms").register(registry);
    Gauge   activeBidLocks   = Gauge.builder("bids.active_locks", lockTracker, LockTracker::count).register(registry);
    
    // Auction metrics
    Gauge   activeAuctions   = Gauge.builder("auctions.active", ...).register(registry);
    Counter auctionsEnded    = Counter.builder("auctions.ended").register(registry);
    Counter snipeExtensions  = Counter.builder("auctions.snipe_extensions").register(registry);
    
    // WebSocket metrics
    Gauge   wsConnections    = Gauge.builder("ws.connections.total", ...).register(registry);
    Counter wsFanoutMessages = Counter.builder("ws.fanout.messages").register(registry);
    Timer   wsFanoutLatency  = Timer.builder("ws.fanout.latency_ms").register(registry);
    
    // Timer metrics
    Gauge   pendingTimers    = Gauge.builder("timers.pending", ...).register(registry);
    Timer   timerAccuracy    = Timer.builder("timers.accuracy_ms").register(registry); // actual - scheduled
}
```

**Key Alerts**:
```yaml
alerts:
  - name: BidLatencyHigh
    condition: p99(bids.latency_ms) > 500
    severity: CRITICAL
    
  - name: AuctionEndDelayed
    condition: timers.accuracy_ms > 5000
    severity: CRITICAL
    message: "Auctions ending >5s late. Timer service may be overwhelmed."
    
  - name: WebSocketFanoutSlow
    condition: p99(ws.fanout.latency_ms) > 2000
    severity: WARNING
    
  - name: BidLockContention
    condition: rate(bids.rejected{reason="lock_timeout"}, 1m) > 100
    severity: WARNING
    message: "High lock contention. Consider hot auction sharding."
```

---

## 📊 Summary: Key Trade-offs & Decision Matrix

| Decision | Options Considered | Chosen | Why |
|----------|-------------------|--------|-----|
| **Bid consistency** | Optimistic locking / Pessimistic / Redis lock | Redis lock + PG FOR UPDATE | Belt-and-suspenders: fast + correct |
| **Real-time updates** | Polling / SSE / WebSocket | WebSocket + Redis Pub/Sub | Bidirectional, low latency, scales with Pub/Sub |
| **Auction timer** | In-memory / Redis TTL / DB polling | DB polling (every 1s) | Durable, survives restarts, naturally idempotent |
| **Leaderboard** | DB query / Redis sorted set | Redis ZSET | O(log N) operations, real-time top-N |
| **Hot auction handling** | Per-bid lock / Bid queue + batch | Queue + batch for hot auctions | 100ms batches reduce lock contention 100x |
| **Anti-sniping** | None / Fixed extension / Dynamic | 2-min extension on bids in last 30s | Fair, prevents sniping, caps total extensions |
| **Read path** | DB only / Redis cache / CDN | Redis cache + write-through | Sub-ms reads, always fresh after a bid |
| **Fanout** | Direct push / Redis Pub/Sub / Kafka | Kafka → Redis Pub/Sub → WS servers | Decoupled, scalable to 1M viewers per auction |

## 🏗️ Technology Stack

```
┌─────────────────────────────────────────────────────────┐
│  SERVICES             │  DATA                           │
│  ────────             │  ────                            │
│  Java (Spring Boot)   │  PostgreSQL (Auctions + Bids)   │
│  Netty (WebSocket)    │  Redis Cluster (Locks, Cache,   │
│                       │    Pub/Sub, Leaderboards)        │
│  MESSAGING            │  S3 + CloudFront (Media)        │
│  ──────────           │                                  │
│  Apache Kafka         │  OBSERVABILITY                   │
│  Redis Pub/Sub        │  Prometheus + Grafana            │
│                       │  PagerDuty                       │
│  INFRA                │  Jaeger (Tracing)                │
│  Kubernetes           │                                  │
└─────────────────────────────────────────────────────────┘
```

## 🎯 Interview Talking Points

1. **"Bids require linearizable consistency"** — This is a financial transaction. We can never accept a $700 bid after a $750 bid was already recorded. Redis distributed lock + PostgreSQL FOR UPDATE gives us both speed and correctness.

2. **"Durable timers, not setTimeout"** — Auction end times must survive server restarts. DB-backed polling every 1 second gives ±1s accuracy and is naturally idempotent.

3. **"Anti-sniping makes it a fair auction"** — Extending by 2 minutes on last-30-second bids prevents sniping. Simple rule, huge UX improvement.

4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on a celebrity auction must reach 1M viewers in < 1 second. Kafka → Redis Pub/Sub → 200 WS servers → clients. Each server handles its local connections.

5. **"Hot auctions get special treatment"** — For the 0.01% of auctions with extreme bid rates, we switch to a queued-batch model. Batch bids every 100ms, only the highest wins. Reduces lock contention by 100x.

6. **"Bid → Event → Async fanout"** — The bid write path is synchronous and locked. Everything else (notifications, WebSocket, analytics) is async via Kafka events. This keeps bid latency under 200ms.

---

**References**:
- eBay's Auction Architecture (Scalability lessons)
- Redis Distributed Locks (Redlock algorithm, Martin Kleppmann critique)
- WebSocket at Scale (Discord's approach to millions of connections)
- Anti-Sniping Patterns (eBay, Catawiki, Heritage Auctions)

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 10 topics (choose 2-3 based on interviewer interest)
 later)
            }
        }
    }
}

public class AuctionEndService {
    
    /** End an auction. Must be idempotent (safe to call multiple times). */
    @Transactional
    public void endAuction(UUID auctionId) {
        RLock lock = redisson.getLock("auction:lock:" + auctionId);
        lock.lock(30, TimeUnit.SECONDS);
        
        try {
            Auction auction = auctionRepo.findByIdForUpdate(auctionId).orElseThrow();
            
            // Idempotency check: already ended?
            if (auction.getStatus() == AuctionStatus.ENDED) {
                log.info("Auction {} already ended, skipping", auctionId);
                return;
            }
            
            // Find winning bid
            Optional<Bid> winningBid = bidRepo.findTopByAuctionIdOrderByAmountDesc(auctionId);
            
            auction.setStatus(AuctionStatus.ENDED);
            if (winningBid.isPresent()) {
                auction.setWinnerId(winningBid.get().getBidderId());
                auction.setWinningBidId(winningBid.get().getBidId());
                winningBid.get().setStatus(BidStatus.WINNING);
                bidRepo.save(winningBid.get());
            }
            auctionRepo.save(auction);
            
            // Publish auction-ended event
            kafka.send(new ProducerRecord<>("auction-ended", auctionId.toString(),
                AuctionEndedEvent.builder()
                    .auctionId(auctionId)
                    .winnerId(auction.getWinnerId())
                    .winningAmount(auction.getCurrentPrice())
                    .totalBids(auction.getBidCount())
                    .build()));
                    
            log.info("Auction {} ended. Winner: {}, Amount: {}", 
                     auctionId, auction.getWinnerId(), auction.getCurrentPrice());
        } finally {
            lock.unlock();
        }
    }
}
```

**Why DB Polling Instead of In-Memory Timers?**
```
In-memory timer (setTimeout / ScheduledExecutorService):
  ✗ Lost on server restart
  ✗ Lost on deployment
  ✗ Can only run on one server (not HA)
  ✗ 1M concurrent timers = massive memory

DB Polling:
  ✓ Survives restarts (timers are just rows in the DB)
  ✓ Multiple timer service instances can poll (with distributed lock)
  ✓ ±1 second accuracy (polling interval)
  ✓ Naturally idempotent (status check before processing)
  ✓ Index on (status, ends_at) makes polling O(log N)
```

---

### Deep Dive 3: Anti-Sniping Protection

**The Problem**: "Sniping" — placing a bid in the last second so other bidders can't respond. Ruins the auction experience.

```java
public class AntiSnipeService {
    private static final int SNIPE_WINDOW_SECONDS = 30;   // Last 30 seconds
    private static final int EXTENSION_SECONDS = 120;      // Extend by 2 minutes

    /**
     * Called after every bid. Checks if the bid was in the snipe window
     * and extends the auction if needed.
     */
    public void checkAndExtend(BidEvent bidEvent) {
        Auction auction = auctionRepo.findById(bidEvent.getAuctionId()).orElseReturn(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) return;

        Instant bidTime = bidEvent.getTimestamp();
        Instant auctionEnd = auction.getEndsAt();
        long secondsUntilEnd = Duration.between(bidTime, auctionEnd).getSeconds();

        if (secondsUntilEnd <= SNIPE_WINDOW_SECONDS && secondsUntilEnd > 0) {
            // Bid placed in snipe window — extend the auction
            Instant newEndTime = auctionEnd.plusSeconds(EXTENSION_SECONDS);
            
            auction.setEndsAt(newEndTime);
            auctionRepo.save(auction);

            // Update the durable timer
            timerService.reschedule(auction.getAuctionId(), newEndTime);

            // Notify all viewers
            kafka.send(new ProducerRecord<>("auction-updates", auction.getAuctionId().toString(),
                AuctionUpdate.builder()
                    .type("TIME_EXTENDED")
                    .auctionId(auction.getAuctionId())
                    .newEndsAt(newEndTime)
                    .reason("Bid placed in final " + SNIPE_WINDOW_SECONDS + " seconds")
                    .build()));

            log.info("Anti-snipe: Auction {} extended to {} (bid at {} seconds before end)",
                     auction.getAuctionId(), newEndTime, secondsUntilEnd);
            metrics.counter("auction.snipe_extensions").increment();
        }
    }
}
```

---

### Deep Dive 4: WebSocket Fanout at Scale (1M Viewers)

**The Problem**: A celebrity auction has 1 million concurrent viewers. When a bid is placed, all 1M must be notified within 1 second.

```java
public class WebSocketFanoutService {
    private final RedissonClient redisson; // Redis Pub/Sub for cross-server fanout

    /**
     * Called when a bid is placed. Fans out to all WebSocket servers
     * that have clients watching this auction.
     */
    public void fanoutBidUpdate(BidEvent event) {
        String channel = "auction:" + event.getAuctionId();
        
        BidUpdateMessage message = BidUpdateMessage.builder()
            .type("BID_UPDATE")
            .auctionId(event.getAuctionId())
            .bidder(event.getBidderId().toString())
            .amount(event.getAmount())
            .bidCount(event.getBidCount())
            .timestamp(event.getTimestamp())
            .build();
        
        // Publish to Redis Pub/Sub — all WebSocket servers subscribed to this channel receive it
        redisson.getTopic(channel).publish(message);
        
        metrics.counter("ws.fanout.messages").increment();
    }
}

public class WebSocketServer {
    // Each WebSocket server handles ~50K concurrent connections
    private final Map<UUID, Set<WebSocketSession>> auctionSubscribers = new ConcurrentHashMap<>();

    /** Client connects and subscribes to an auction */
    public void onConnect(WebSocketSession session, UUID auctionId) {
        auctionSubscribers.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
                          .add(session);
        
        // Subscribe to Redis Pub/Sub channel for this auction (if not already)
        redisSubscriber.subscribe("auction:" + auctionId, message -> {
            broadcastToLocal(auctionId, message);
        });
    }

    /** Broadcast to all local clients watching this auction */
    private void broadcastToLocal(UUID auctionId, BidUpdateMessage message) {
        Set<WebSocketSession> sessions = auctionSubscribers.get(auctionId);
        if (sessions == null) return;
        
        String json = objectMapper.writeValueAsString(message);
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
        
        metrics.counter("ws.messages.sent").increment(sessions.size());
    }
}
```

**Fanout Architecture for 1M Viewers**:
```
Bid placed → Kafka "bid-placed" topic
  ↓
WebSocket Fanout Service consumes event
  ↓
Publishes to Redis Pub/Sub channel: "auction:{id}"
  ↓
All 200 WebSocket servers receive the message simultaneously
  ↓
Each server broadcasts to its local connections (~5000 per auction)
  ↓
200 × 5000 = 1,000,000 clients notified

Total latency: < 500ms
  Redis Pub/Sub publish: ~1ms
  Server receive + broadcast: ~100ms (parallel across servers)
  Client WebSocket delivery: ~200ms
```

---

### Deep Dive 5: Read Path Optimization — Serving Auction Details Fast

**The Problem**: Auction detail pages are read 1000x more than they're updated. The DB can't handle every read.

```java
public class AuctionReadService {
    private final JedisCluster redis;
    private final AuctionRepository auctionRepo;
    
    /**
     * Get auction details — read from cache first, DB fallback.
     * Cache is updated on every bid via write-through.
     */
    public AuctionDetailResponse getAuction(UUID auctionId) {
        // 1. Try Redis cache (< 1ms)
        String cacheKey = "auction:" + auctionId;
        String cached = redis.get(cacheKey);
        
        if (cached != null) {
            metrics.counter("auction.cache.hit").increment();
            return objectMapper.readValue(cached, AuctionDetailResponse.class);
        }
        
        // 2. Cache miss — read from DB read replica
        metrics.counter("auction.cache.miss").increment();
        Auction auction = auctionRepo.findById(auctionId).orElseThrow();
        List<Bid> topBids = bidRepo.findTopBidsByAuction(auctionId, 10);
        
        AuctionDetailResponse response = buildResponse(auction, topBids);
        
        // 3. Populate cache (60 second TTL)
        redis.setex(cacheKey, 60, objectMapper.writeValueAsString(response));
        
        return response;
    }
    
    /**
     * Called by BidService after a successful bid.
     * Updates the cache immediately (write-through).
     */
    public void updateCacheOnBid(UUID auctionId, Bid newBid, Auction updatedAuction) {
        String cacheKey = "auction:" + auctionId;
        
        // Update cached auction with new price and top bidder
        AuctionDetailResponse response = buildResponse(updatedAuction, 
            bidRepo.findTopBidsByAuction(auctionId, 10));
        
        redis.setex(cacheKey, 60, objectMapper.writeValueAsString(response));
    }
}
```

**Leaderboard (Top N Bidders) Using Redis Sorted Set**:
```java
public class BidLeaderboard {
    private final JedisCluster redis;

    /** Add a bid to the leaderboard */
    public void recordBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        String key = "leaderboard:" + auctionId;
        redis.zadd(key, amount.doubleValue(), bidderId.toString());
        redis.expire(key, 86400 * 3); // 3-day TTL
    }

    /** Get top N bidders */
    public List<LeaderboardEntry> getTopBidders(UUID auctionId, int n) {
        String key = "leaderboard:" + auctionId;
        // ZREVRANGE with scores — O(log N + M)
        Set<Tuple> topN = redis.zrevrangeWithScores(key, 0, n - 1);
        
        int rank = 1;
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Tuple tuple : topN) {
            entries.add(new LeaderboardEntry(rank++, tuple.getElement(), 
                                            BigDecimal.valueOf(tuple.getScore())));
        }
        return entries;
    }

    /** Get a specific bidder's rank */
    public Long getBidderRank(UUID auctionId, UUID bidderId) {
        String key = "leaderboard:" + auctionId;
        Long reverseRank = redis.zrevrank(key, bidderId.toString());
        return reverseRank != null ? reverseRank + 1 : null; // 1-indexed
    }
}
```

---

### Deep Dive 6: Proxy/Automatic Bidding

**The Problem**: Users want to set a maximum bid and have the system automatically bid on their behalf, just enough to stay ahead.

```java
public class ProxyBidService {
    
    /**
     * Set a maximum bid for a user. The system will auto-bid up to this amount.
     */
    public void setMaxBid(UUID auctionId, UUID bidderId, BigDecimal maxAmount) {
        // Store in Redis: "proxy:{auction_id}:{bidder_id}" → maxAmount
        String key = "proxy:" + auctionId + ":" + bidderId;
        redis.set(key, maxAmount.toString());
        
        // Immediately trigger a proxy bid if needed
        triggerProxyBids(auctionId);
    }

    /**
     * Called after every regular bid. Checks if any proxy bidders should auto-bid.
     */
    public void triggerProxyBids(UUID auctionId) {
        Auction auction = auctionRepo.findById(auctionId).orElseReturn(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) return;

        BigDecimal currentPrice = auction.getCurrentPrice();
        BigDecimal increment = auction.getBidIncrement();
        BigDecimal nextMinBid = currentPrice.add(increment);

        // Find all proxy bidders for this auction
        Set<String> proxyKeys = redis.keys("proxy:" + auctionId + ":*");
        
        // Find the proxy bidder with the highest max that can still bid
        UUID bestProxyBidder = null;
        BigDecimal bestProxyMax = BigDecimal.ZERO;
        
        for (String key : proxyKeys) {
            UUID bidderId = extractBidderId(key);
            BigDecimal maxBid = new BigDecimal(redis.get(key));
            
            // Skip the current highest bidder (don't bid against yourself)
            if (bidderId.equals(auction.getWinnerId())) continue;
            
            // Can this proxy bidder afford the next minimum bid?
            if (maxBid.compareTo(nextMinBid) >= 0 && maxBid.compareTo(bestProxyMax) > 0) {
                bestProxyBidder = bidderId;
                bestProxyMax = maxBid;
            }
        }
        
        if (bestProxyBidder != null) {
            // Place a bid at the minimum required amount (not the max)
            BigDecimal bidAmount = nextMinBid;
            bidService.placeBid(auctionId, bestProxyBidder, bidAmount);
            
            log.info("Proxy bid: {} bid {} on auction {} (max: {})", 
                     bestProxyBidder, bidAmount, auctionId, bestProxyMax);
        }
    }
}
```

---

### Deep Dive 7: Hot Auction Sharding — Handling 100K Bids/Sec on One Item

**The Problem**: Most auctions get modest traffic. But one celebrity auction might get 100K bids/sec — a single PostgreSQL row becomes a bottleneck.

**Solution: Bid Queue + Batch Processing**

```java
public class HotAuctionBidProcessor {
    /**
     * For "hot" auctions (detected by bid rate), instead of locking per bid,
     * we batch bids and process them in order.
     */
    
    // Incoming bids go into a per-auction Redis queue
    public void enqueueBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        String queueKey = "bid_queue:" + auctionId;
        String bidJson = objectMapper.writeValueAsString(
            Map.of("bidder", bidderId, "amount", amount, "ts", Instant.now()));
        redis.rpush(queueKey, bidJson);
    }

    /**
     * Process queued bids in batches every 100ms.
     * Only the HIGHEST bid in each batch actually wins.
     */
    @Scheduled(fixedRate = 100) // Every 100ms
    public void processBidBatches() {
        for (UUID auctionId : hotAuctions) {
            String queueKey = "bid_queue:" + auctionId;
            
            // Drain all pending bids atomically
            List<String> bids = new ArrayList<>();
            String bid;
            while ((bid = redis.lpop(queueKey)) != null) {
                bids.add(bid);
            }
            
            if (bids.isEmpty()) continue;
            
            // Find the highest bid in the batch
            BidRequest highest = bids.stream()
                .map(b -> objectMapper.readValue(b, BidRequest.class))
                .max(Comparator.comparing(BidRequest::getAmount))
                .orElse(null);
            
            if (highest != null) {
                // Only process the winning bid
                bidService.placeBid(auctionId, highest.getBidder(), highest.getAmount());
                
                // Reject all others (they were outbid before even being processed)
                // Notify them via WebSocket: "A higher bid was placed simultaneously"
            }
            
            metrics.counter("hot_auction.batch_size").record(bids.size());
        }
    }
}
```

---

### Deep Dive 8: Auction Discovery Feed & Trending

**The Problem**: Users need to discover interesting auctions. We need a feed sorted by relevance, trending, or ending soon.

```java
public class AuctionFeedService {
    
    /** Get auctions ending soon (for FOMO-driven engagement) */
    public List<AuctionSummary> getEndingSoon(int limit) {
        // Redis sorted set: score = ends_at epoch seconds
        Set<String> auctionIds = redis.zrangeByScore(
            "auctions:ending_soon", 
            String.valueOf(Instant.now().getEpochSecond()),
            String.valueOf(Instant.now().plusHours(1).getEpochSecond()),
            0, limit);
        
        return auctionIds.stream()
            .map(id -> getAuctionSummary(UUID.fromString(id)))
            .collect(Collectors.toList());
    }
    
    /** Get trending auctions (most bids in last hour) */
    public List<AuctionSummary> getTrending(int limit) {
        // Redis sorted set: score = bid count in last hour
        Set<Tuple> trending = redis.zrevrangeWithScores("auctions:trending", 0, limit - 1);
        
        return trending.stream()
            .map(t -> getAuctionSummary(UUID.fromString(t.getElement())))
            .collect(Collectors.toList());
    }
    
    /** Update trending score when a bid is placed */
    public void onBidPlaced(UUID auctionId) {
        // Increment bid count for this auction in the trending sorted set
        redis.zincrby("auctions:trending", 1, auctionId.toString());
        
        // Decay scores every hour (scheduled job)
    }
    
    /** Hourly: decay trending scores to favor recency */
    @Scheduled(cron = "0 0 * * * *")
    public void decayTrendingScores() {
        Set<Tuple> all = redis.zrangeWithScores("auctions:trending", 0, -1);
        for (Tuple entry : all) {
            double newScore = entry.getScore() * 0.5; // 50% decay per hour
            if (newScore < 1) {
                redis.zrem("auctions:trending", entry.getElement());
            } else {
                redis.zadd("auctions:trending", newScore, entry.getElement());
            }
        }
    }
}
```

---

### Deep Dive 9: Notification Fanout — Outbid, Won, Lost

**The Problem**: When a bid is placed, the previous highest bidder must be notified ("You've been outbid!"). When an auction ends, the winner, seller, and all bidders must be notified.

```java
public class AuctionNotificationService {
    
    /** Called when a new bid is placed */
    public void onBidPlaced(BidEvent event) {
        // Notify previous highest bidder: "You've been outbid!"
        if (event.getPreviousHighBidderId() != null) {
            notificationService.send(Notification.builder()
                .userId(event.getPreviousHighBidderId())
                .type(NotificationType.OUTBID)
                .title("You've been outbid!")
                .message(String.format("Someone bid $%.2f on '%s'. Place a higher bid to win!",
                    event.getAmount(), event.getAuctionTitle()))
                .auctionId(event.getAuctionId())
                .channels(Set.of(Channel.PUSH, Channel.IN_APP))
                .build());
        }
    }

    /** Called when an auction ends */
    public void onAuctionEnded(AuctionEndedEvent event) {
        // 1. Notify winner
        if (event.getWinnerId() != null) {
            notificationService.send(Notification.builder()
                .userId(event.getWinnerId())
                .type(NotificationType.AUCTION_WON)
                .title("🎉 You won!")
                .message(String.format("You won '%s' for $%.2f. Complete payment to claim.",
                    event.getAuctionTitle(), event.getWinningAmount()))
                .channels(Set.of(Channel.PUSH, Channel.EMAIL, Channel.IN_APP))
                .build());
        }

        // 2. Notify seller
        notificationService.send(Notification.builder()
            .userId(event.getSellerId())
            .type(NotificationType.AUCTION_ENDED)
            .title("Your auction has ended")
            .message(String.format("'%s' sold for $%.2f to %s.",
                event.getAuctionTitle(), event.getWinningAmount(), event.getWinnerUsername()))
            .channels(Set.of(Channel.PUSH, Channel.EMAIL, Channel.IN_APP))
            .build());

        // 3. Notify all other bidders (batch)
        List<UUID> losingBidders = bidRepo.findDistinctBiddersByAuction(event.getAuctionId())
            .stream()
            .filter(id -> !id.equals(event.getWinnerId()))
            .collect(Collectors.toList());
        
        for (UUID bidderId : losingBidders) {
            notificationService.send(Notification.builder()
                .userId(bidderId)
                .type(NotificationType.AUCTION_LOST)
                .title("Auction ended")
                .message(String.format("'%s' was won by someone else for $%.2f.",
                    event.getAuctionTitle(), event.getWinningAmount()))
                .channels(Set.of(Channel.IN_APP))
                .build());
        }
    }
}
```

---

### Deep Dive 10: Observability & Metrics

```java
public class AuctionMetrics {
    // Bid metrics
    Counter bidsPlaced       = Counter.builder("bids.placed").register(registry);
    Counter bidsRejected     = Counter.builder("bids.rejected").tag("reason", "too_low").register(registry);
    Timer   bidLatency       = Timer.builder("bids.latency_ms").register(registry);
    Gauge   activeBidLocks   = Gauge.builder("bids.active_locks", lockTracker, LockTracker::count).register(registry);
    
    // Auction metrics
    Gauge   activeAuctions   = Gauge.builder("auctions.active", ...).register(registry);
    Counter auctionsEnded    = Counter.builder("auctions.ended").register(registry);
    Counter snipeExtensions  = Counter.builder("auctions.snipe_extensions").register(registry);
    
    // WebSocket metrics
    Gauge   wsConnections    = Gauge.builder("ws.connections.total", ...).register(registry);
    Counter wsFanoutMessages = Counter.builder("ws.fanout.messages").register(registry);
    Timer   wsFanoutLatency  = Timer.builder("ws.fanout.latency_ms").register(registry);
    
    // Timer metrics
    Gauge   pendingTimers    = Gauge.builder("timers.pending", ...).register(registry);
    Timer   timerAccuracy    = Timer.builder("timers.accuracy_ms").register(registry); // actual - scheduled
}
```

**Key Alerts**:
```yaml
alerts:
  - name: BidLatencyHigh
    condition: p99(bids.latency_ms) > 500
    severity: CRITICAL
    
  - name: AuctionEndDelayed
    condition: timers.accuracy_ms > 5000
    severity: CRITICAL
    message: "Auctions ending >5s late. Timer service may be overwhelmed."
    
  - name: WebSocketFanoutSlow
    condition: p99(ws.fanout.latency_ms) > 2000
    severity: WARNING
    
  - name: BidLockContention
    condition: rate(bids.rejected{reason="lock_timeout"}, 1m) > 100
    severity: WARNING
    message: "High lock contention. Consider hot auction sharding."
```

---

## 📊 Summary: Key Trade-offs & Decision Matrix

| Decision | Options Considered | Chosen | Why |
|----------|-------------------|--------|-----|
| **Bid consistency** | Optimistic locking / Pessimistic / Redis lock | Redis lock + PG FOR UPDATE | Belt-and-suspenders: fast + correct |
| **Real-time updates** | Polling / SSE / WebSocket | WebSocket + Redis Pub/Sub | Bidirectional, low latency, scales with Pub/Sub |
| **Auction timer** | In-memory / Redis TTL / DB polling | DB polling (every 1s) | Durable, survives restarts, naturally idempotent |
| **Leaderboard** | DB query / Redis sorted set | Redis ZSET | O(log N) operations, real-time top-N |
| **Hot auction handling** | Per-bid lock / Bid queue + batch | Queue + batch for hot auctions | 100ms batches reduce lock contention 100x |
| **Anti-sniping** | None / Fixed extension / Dynamic | 2-min extension on bids in last 30s | Fair, prevents sniping, caps total extensions |
| **Read path** | DB only / Redis cache / CDN | Redis cache + write-through | Sub-ms reads, always fresh after a bid |
| **Fanout** | Direct push / Redis Pub/Sub / Kafka | Kafka → Redis Pub/Sub → WS servers | Decoupled, scalable to 1M viewers per auction |

## 🏗️ Technology Stack

```
┌─────────────────────────────────────────────────────────┐
│  SERVICES             │  DATA                           │
│  ────────             │  ────                            │
│  Java (Spring Boot)   │  PostgreSQL (Auctions + Bids)   │
│  Netty (WebSocket)    │  Redis Cluster (Locks, Cache,   │
│                       │    Pub/Sub, Leaderboards)        │
│  MESSAGING            │  S3 + CloudFront (Media)        │
│  ──────────           │                                  │
│  Apache Kafka         │  OBSERVABILITY                   │
│  Redis Pub/Sub        │  Prometheus + Grafana            │
│                       │  PagerDuty                       │
│  INFRA                │  Jaeger (Tracing)                │
│  Kubernetes           │                                  │
└─────────────────────────────────────────────────────────┘
```

## 🎯 Interview Talking Points

1. **"Bids require linearizable consistency"** — This is a financial transaction. We can never accept a $700 bid after a $750 bid was already recorded. Redis distributed lock + PostgreSQL FOR UPDATE gives us both speed and correctness.

2. **"Durable timers, not setTimeout"** — Auction end times must survive server restarts. DB-backed polling every 1 second gives ±1s accuracy and is naturally idempotent.

3. **"Anti-sniping makes it a fair auction"** — Extending by 2 minutes on last-30-second bids prevents sniping. Simple rule, huge UX improvement.

4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on a celebrity auction must reach 1M viewers in < 1 second. Kafka → Redis Pub/Sub → 200 WS servers → clients. Each server handles its local connections.

5. **"Hot auctions get special treatment"** — For the 0.01% of auctions with extreme bid rates, we switch to a queued-batch model. Batch bids every 100ms, only the highest wins. Reduces lock contention by 100x.

6. **"Bid → Event → Async fanout"** — The bid write path is synchronous and locked. Everything else (notifications, WebSocket, analytics) is async via Kafka events. This keeps bid latency under 200ms.

---

**References**:
- eBay's Auction Architecture (Scalability lessons)
- Redis Distributed Locks (Redlock algorithm, Martin Kleppmann critique)
- WebSocket at Scale (Discord's approach to millions of connections)
- Anti-Sniping Patterns (eBay, Catawiki, Heritage Auctions)

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 10 topics (choose 2-3 based on interviewer interest)
**Deep Dives**: 10 topics (choose 2-3 based on intervi
**Deep Dives**: 10 topics (choose 2
**Deep Dives**: 10 topics
**Deep
**Estimated
**Framework**: Hello Interview (
**Framework
6. **"Bid → Event → Async fanout"** — The bid write path is synchronous and locked
6. **"Bid → Event → Async fanout"** — The bid write path
6. **"Bid → Event → Async fanout"** — The
6. **"Bid → Event
6. **"
5. **"Hot auctions get special treatment"** — For the 0.01% of auctions with extreme bid rates, we switch to a que
5. **"Hot auctions get special treatment"** — For the 0.01% of auctions with
5. **"Hot auctions get special treatment"** — For the
5. **"Hot auctions get special
4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on a celebrity auction must reach 1M viewers in < 1 second. Kafka → Redis Pub/Sub → 200 WS servers → clients
4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on a celebrity auction must reach 1M viewers in < 1 second. Kafka → Redis Pub/Sub → 200
4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on a celebrity auction must reach 1M viewers in
4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on a celebrity auction must reach
4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on a celebrity auction must
4. **"WebSocket fanout via Redis Pub/Sub"** — A bid on
4. **"WebSocket fanout via Redis
4. **"
3. **"Anti-sniping makes it a fair auction"** — Extending by 2 minutes on last-
3. **"Anti-sniping makes it a fair auction"** — Extending by
3. **"Anti-sniping makes it a
3. **"Anti
2. **"Durable timers, not setTimeout"** — Auction end times must survive server restarts. DB-backed polling every
2. **"Durable timers, not setTimeout"** — Auction end times must survive server restarts. DB
2. **"Durable timers, not setTimeout"** — Auction end
2. **"Durable timers,
1. **"Bids require linearizable consistency"** — This is a financial transaction. We can never accept a $700 bid after a $750 bid was already
1. **"Bids require linearizable consistency"** — This is a financial transaction. We can never accept a $700
1. **"Bids require linearizable consistency"** — This is a financial transaction. We can never
1. **"Bids require linearizable consistency"** — This is a financial transaction
1. **"Bids require linearizable consistency"** — This
1. **"Bids require linear
│  Apache Kafka         │
│
│  MESSAGING
│                       │
│  Netty (WebSocket)    │  Redis Cluster (Locks
│  Netty (
│
│  Java (
│  SERVICES
│
| **Fanout** | Direct push / Redis Pub/Sub / Kafka |
| **Fanout** | Direct push / Redis Pub/Sub /
| **Fanout** | Direct push / Redis
| **Fan
| **Read path** | DB only / Redis cache / CDN | Redis cache + write-through | Sub-ms reads, always
| **Read path** | DB only / Redis cache / CDN | Redis cache + write-through | Sub
| **Read path** | DB only / Redis cache /
| **Read path** | DB only
| **Read
| **Anti-sniping** | None / Fixed extension / Dynamic | 2-min extension on bids in last 30s | Fair, prevents s
| **Anti-sniping** | None / Fixed extension / Dynamic | 2-min extension on bids in last 30
| **Anti-sniping** | None / Fixed extension / Dynamic | 2-min extension on
| **Anti-sniping** | None / Fixed extension / Dynamic | 2-
| **Anti-sniping** | None / Fixed extension / Dynamic |
| **Anti-sniping** | None / Fixed extension /
| **Anti-sniping** | None /
| **Hot auction handling** | Per-bid lock / Bid queue + batch | Queue + batch for hot auctions | 100ms batches reduce
| **Hot auction handling** | Per-bid lock / Bid queue + batch | Queue + batch for hot auctions |
| **Hot auction handling** | Per-bid lock / Bid queue + batch | Queue
| **Hot auction handling** | Per-bid lock
| **Hot auction handling
| **Leaderboard** | DB query / Redis sorted set | Redis ZSET |
| **Leaderboard** | DB query
| **Auction timer** | In-memory / Redis TTL / DB polling | DB polling (every 1s) | D
| **Auction timer** | In-memory / Redis TTL / DB polling | DB polling (
| **Auction timer** | In-memory / Redis TTL / DB polling
| **Auction timer** | In-memory / Redis TT
| **Auction timer** | In
| **Real-time updates** | Polling / SSE / WebSocket | WebSocket + Redis Pub/Sub | Bidirectional, low latency,
| **Real-time updates** | Polling / SSE / WebSocket | WebSocket + Redis Pub/Sub | Bidirect
| **Real-time updates** | Polling / SSE / WebSocket | WebSocket + Redis
| **Real-time updates** | Polling / SS
| **Real
| **Bid consistency** | Optimistic locking / Pessimistic / Redis lock | Redis lock + PG FOR UPDATE | Belt
| **Bid consistency** | Optimistic locking / Pessimistic / Redis lock | Redis lock
| **Bid consistency** | Optimistic locking / Pessimistic /
| **Bid consistency** | Optim
| **Bid
## 📊 Summary
##
    Gauge   activeBidLocks
    Gauge
    Timer   bid
    Counter bidsRejected     = Counter.builder("bids.rejected").tag("reason
    Counter bidsR
    Counter bidsPlaced       = Counter
    Counter b
