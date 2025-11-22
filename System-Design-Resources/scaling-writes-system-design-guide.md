# Scaling Writes: Complete HLD Guide with Real-World Examples

## Quick Navigation
- [Introduction](#introduction)
- [The Write Scaling Challenge](#the-write-scaling-challenge)
- [Core Write Scaling Strategies](#core-write-scaling-strategies)
- [10+ Real-World Examples](#real-world-architecture-examples)
- [Design Patterns](#design-patterns)
- [Decision Framework](#decision-framework)
- [Interview Guide](#interview-guide)

---

## Introduction

**Write Scaling** is the practice of optimizing systems to handle increasing write traffic while maintaining consistency, durability, and performance. Unlike reads, writes are inherently more challenging due to:

- **Coordination overhead** (locks, transactions)
- **Consistency requirements** (ACID properties)
- **No caching benefits** (cache invalidation complexity)
- **Sequential bottlenecks** (single master)
- **Replication complexity** (sync vs async trade-offs)

This guide focuses on high-level architecture patterns with extensive real-world examples.

---

## The Write Scaling Challenge

```
┌──────────────────────────────────────────────────────────────────┐
│                WHY WRITE SCALING IS HARDER                        │
└──────────────────────────────────────────────────────────────────┘

READS vs WRITES:

Reads:
✓ Can be cached (Redis, CDN)
✓ Can be replicated infinitely
✓ Eventual consistency acceptable
✓ Parallel execution easy
✓ Idempotent (safe to retry)

Writes:
✗ Cannot cache (invalidation problem)
✗ Limited replication (coordination needed)
✗ Strong consistency often required
✗ Sequential execution common
✗ Not always idempotent

Performance Impact:

Read Path:
Client → Cache → Return (2ms)

Write Path:
Client → Validation → Lock → Write → Index Update
     → Replicate → Wait for ACK → Commit (50-200ms)
```

### The Single Master Bottleneck

```
┌──────────────────────────────────────────────────────────────────┐
│              SINGLE MASTER WRITE BOTTLENECK                       │
└──────────────────────────────────────────────────────────────────┘

Without Scaling:
                    ┌──────────────┐
    100K users ────▶│  Master DB   │
    All writes       │  (Single)    │
                     │              │
                     │Max: 10K WPS  │
                     └──────────────┘
                            │
                            ▼
                    [Bottleneck!]

Symptoms:
• 0-5K WPS: <50ms latency ✓
• 5-10K WPS: 100-300ms latency ⚠️
• >10K WPS: Timeouts, failures ❌

WPS = Writes Per Second
```

---

## Core Write Scaling Strategies

```
┌──────────────────────────────────────────────────────────────────┐
│           6 PRIMARY WRITE SCALING TECHNIQUES                      │
└──────────────────────────────────────────────────────────────────┘

1. ASYNC WRITES (Message Queues)
   What: Return immediately, process later
   Latency: 10-50ms (user-facing)
   Throughput: 10x improvement
   Consistency: Eventual
   Use: Social media, notifications, analytics

2. BATCH WRITES
   What: Group multiple writes into one
   Throughput: 10-100x improvement
   Latency: Variable (batching window)
   Use: Log ingestion, analytics, bulk imports

3. WRITE-AHEAD LOGGING (WAL)
   What: Sequential log + async apply
   Throughput: 5-10x improvement
   Durability: Guaranteed
   Use: Almost all databases use this

4. DATABASE SHARDING
   What: Partition data across multiple DBs
   Scaling: Linear (4 shards = 4x writes)
   Complexity: High
   Use: When single DB maxed (>10K WPS)

5. EVENT SOURCING
   What: Store changes as immutable events
   Write Speed: Very fast (append-only)
   Audit: Complete history
   Use: Financial, compliance, CQRS

6. MULTI-MASTER REPLICATION
   What: Accept writes on multiple nodes
   Availability: Very high
   Consistency: Eventual (conflicts possible)
   Use: Global apps, high availability needs
```

---

## Real-World Architecture Examples

### Example 1: Twitter - Tweet Creation at Scale

```
┌──────────────────────────────────────────────────────────────────┐
│                  TWITTER WRITE ARCHITECTURE                       │
└──────────────────────────────────────────────────────────────────┘

Scale: 500M+ tweets/day, 6K tweets/sec average, 20K peak

Challenge: Fast tweet creation + instant delivery to followers

Architecture:

User posts tweet
   ↓
┌────────────────────────┐
│   API Gateway          │
│   • Auth check         │
│   • Rate limit         │
│   • 100 tweets/hour    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Tweet Service         │
│   • Validate (280 char)│
│   • Spam check         │
│   • Generate ID        │
│   Time: 30ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Manhattan DB          │
│  (Write Master)        │
│   • Store tweet        │
│   • Partition: user_id │
│   Time: 40ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Kafka Queue          │
│   • Topic: tweets      │
│   • 100 partitions     │
│   • Async publish      │
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼ Return to user (80ms total)
         │
         │ Background processing
         ├─────────────────┬─────────────────┬─────────────────┐
         ▼                 ▼                 ▼                 ▼
   ┌──────────┐      ┌──────────┐    ┌──────────┐    ┌──────────┐
   │ Fan-out  │      │  Search  │    │ Trending │    │ Notify   │
   │ Workers  │      │  Index   │    │ Service  │    │ Service  │
   │          │      │          │    │          │    │          │
   │Push to   │      │Update ES │    │Extract   │    │Push to   │
   │followers │      │index     │    │hashtags  │    │followers │
   └──────────┘      └──────────┘    └──────────┘    └──────────┘

Fan-out Strategy (Write Heavy):

REGULAR USERS (<1K followers):
   Tweet created
      ↓
   Fan-out workers (200 workers)
      ↓
   Write to all followers' Redis timelines
   • Batch writes (100 followers at a time)
   • Async, eventually consistent
   • Completed in <5 seconds

POWER USERS (1K-100K followers):
   Tweet created
      ↓
   Write to active followers only (last 7 days)
   • Reduces fan-out by 80-90%
   • Others fetch on next app open

CELEBRITIES (>100K followers):
   Tweet created
      ↓
   Store in central cache (no fan-out)
   • Followers fetch on-demand
   • Merged with timeline on read
   • Write completes in 80ms

Write Optimization Techniques:

1. WRITE BUFFERING
   • Buffer 100 timeline writes
   • Flush to Redis in single pipeline
   • Reduces network overhead by 99%

2. BATCH COMMITS
   • Group database writes
   • Single transaction for multiple tweets
   • 5x throughput improvement

3. ASYNC EVERYTHING
   • Only critical path: Save tweet
   • Everything else: Background workers
   • Fan-out, indexing, notifications all async

Performance:
• Tweet creation: 80ms (user-facing)
• Fan-out completion: 1-5 seconds (async)
• Throughput: 6K tweets/sec sustained
• Peak handling: 20K tweets/sec
• Availability: 99.99%
```

---

### Example 2: Instagram - Photo Upload & Processing

```
┌──────────────────────────────────────────────────────────────────┐
│              INSTAGRAM PHOTO UPLOAD ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 100M+ photos/day, 1K uploads/second

Challenge: Fast upload + multiple processing steps

Architecture:

User uploads photo
   ↓
┌────────────────────────┐
│   Upload Service       │
│   • Validate image     │
│   • Check size/format  │
│   • Generate ID        │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   S3 (Original)        │
│   • Store full quality │
│   • Multi-part upload  │
│   • Parallel chunks    │
│   Time: 2-5 seconds    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Cassandra (Metadata)  │
│   • Photo ID           │
│   • User ID            │
│   • Upload time        │
│   • Status: processing │
│   Time: 20ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Kafka Queue          │
│   • Topic: photos      │
│   • Async processing   │
│   Time: 5ms            │
└────────┬───────────────┘
         │
         ▼ Return to user (total: 2-5 seconds)
         │
         │ Background Processing Pipeline
         ├──────────────────┬──────────────────┬──────────────────┐
         ▼                  ▼                  ▼                  ▼
   ┌──────────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐
   │ Thumbnail│      │  Filter  │      │   ML     │      │ Fan-out  │
   │Generation│      │  Apply   │      │ Tagging  │      │ Service  │
   │          │      │          │      │          │      │          │
   │Multiple  │      │Instagram │      │Object    │      │Write to  │
   │sizes     │      │filters   │      │detection │      │followers │
   └────┬─────┘      └────┬─────┘      └────┬─────┘      └────┬─────┘
        │                 │                 │                 │
        └─────────────────┴─────────────────┴─────────────────┘
                                  │
                                  ▼
                        Update Cassandra
                        Status: "complete"

Processing Pipeline (All Async):

Step 1: THUMBNAIL GENERATION (30 seconds)
   • Generate 5 sizes (150px to 1080px)
   • Upload to CDN
   • Write URLs to Cassandra

Step 2: FILTER APPLICATION (20 seconds)
   • Apply selected filter
   • Generate processed version
   • Upload to CDN

Step 3: ML PROCESSING (60 seconds)
   • Object detection
   • Face recognition
   • Inappropriate content detection
   • Auto-tagging suggestions

Step 4: FAN-OUT (Variable)
   • Write to followers' feeds
   • Similar to Twitter strategy
   • Batch writes to Redis

Write Optimizations:

1. MULTI-PART UPLOAD
   • Split large images into chunks
   • Upload chunks in parallel
   • Reduces upload time by 80%

2. ASYNC PROCESSING
   • User sees "Uploaded!" immediately
   • Processing happens in background
   • Followers see post within 5 seconds

3. BATCH METADATA WRITES
   • Buffer multiple photo metadata
   • Flush to Cassandra in batches
   • 10x throughput improvement

4. PARTITIONED CASSANDRA
   • Partition by user_id + timestamp
   • Even distribution across 50+ nodes
   • Each node: 20 writes/sec (easy)

Performance:
• Upload acknowledgment: 2-5 seconds
• Full processing: 1-2 minutes
• Throughput: 1K uploads/sec sustained
• Storage: Petabytes in S3
```

---

### Example 3: Uber - Location & Trip Data Writes

```
┌──────────────────────────────────────────────────────────────────┐
│                  UBER WRITE ARCHITECTURE                          │
└──────────────────────────────────────────────────────────────────┘

Scale: 3M drivers, 250K location updates/second, 15M trips/day

Challenge: High-frequency writes with low latency

Architecture:

Driver sends location (every 4 seconds)
   ↓
┌────────────────────────┐
│   API Gateway          │
│   • Rate limiting      │
│   • Auth check         │
│   • Geo validation     │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Location Service      │
│   • Validate coords    │
│   • Enrich data        │
│   • Generate event     │
│   Time: 20ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Kafka (Buffered)     │
│   • Topic: locations   │
│   • 50 partitions      │
│   • By geo region      │
│   • Async publish      │
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼ Return to driver (30ms)
         │
         │ Background Processing
         ├──────────────────┬──────────────────┬──────────────────┐
         ▼                  ▼                  ▼                  ▼
   ┌──────────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐
   │  Redis   │      │Cassandra │      │  Match   │      │Analytics │
   │  (Hot)   │      │(History) │      │ Service  │      │ Service  │
   │          │      │          │      │          │      │          │
   │Current   │      │24-hour   │      │Find      │      │Track     │
   │locations │      │trail     │      │nearby    │      │patterns  │
   └──────────┘      └──────────┘      │riders    │      └──────────┘
                                       └──────────┘

Multi-Database Strategy:

Database 1: REDIS (Real-time)
• Store current driver locations
• GEOADD for spatial indexing
• TTL: 30 seconds
• Fast queries (<10ms)
• Write rate: 250K/sec
• 20-node cluster

Database 2: CASSANDRA (Historical)
• Store location trail (24 hours)
• Partitioned by driver_id + hour
• Time-series optimized
• Batch writes (100 at a time)
• Write rate: 250K/sec
• 50-node cluster

Database 3: POSTGRESQL (Trips)
• Store trip records
• ACID transactions
• Sharded by user_id
• Write rate: 173/sec (15M trips/day)
• 10 shards

Write Optimizations:

1. GEOGRAPHIC SHARDING
   Partition by region:
   • US-East, US-West, EU, Asia
   • Writes go to nearest cluster
   • Reduces latency by 60%
   • Cross-region replication: async

2. BATCH CASSANDRA WRITES
   Buffer locations for 100ms
   Write 25 locations in single batch
   Reduces write amplification

3. WRITE-OPTIMIZED SCHEMA
   Cassandra:
   • Append-only (no updates)
   • Time-based partitioning
   • Automatic TTL expiration
   • No expensive deletes

4. KAFKA PARTITIONING
   Partition by geo_region
   • Ensures ordering per region
   • Parallel processing
   • 50 partitions = 50x parallelism

Performance:
• Location write: 30ms (API response)
• Location persistence: 200ms (async)
• Trip creation: 50-100ms (synchronous)
• Concurrent drivers: 3M+
• Write throughput: 250K+/sec
```

---

### Example 4: Discord - Message Delivery

```
┌──────────────────────────────────────────────────────────────────┐
│                 DISCORD MESSAGE ARCHITECTURE                      │
└──────────────────────────────────────────────────────────────────┘

Scale: 150M+ users, 5M messages/second peak

Challenge: Deliver messages instantly with full history

Architecture:

User sends message
   ↓
┌────────────────────────┐
│   Gateway Service      │
│   • Validate message   │
│   • Check permissions  │
│   • Rate limit check   │
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Message Service       │
│   • Generate ID        │
│   • Add metadata       │
│   • Prepare for write  │
│   Time: 15ms           │
└────────┬───────────────┘
         │
         ├──────────────────────┬──────────────────┐
         │                      │                  │
         ▼                      ▼                  ▼
┌─────────────────┐    ┌─────────────┐    ┌─────────────┐
│   Cassandra     │    │   Kafka     │    │   Redis     │
│   (Persist)     │    │   (Events)  │    │  (Recent)   │
│                 │    │             │    │             │
│• Append-only    │    │• Fanout     │    │• Last 50    │
│• channel_id+time│    │  topic      │    │  messages   │
│• Replication: 3 │    │• 100 parts  │    │  per channel│
│• Time: 5ms      │    │• Time: 5ms  │    │• Time: 3ms  │
└─────────────────┘    └──────┬──────┘    └─────────────┘
                                │
                                ▼
                       ┌──────────────┐
                       │  WebSocket   │
                       │  Servers     │
                       │  (Real-time) │
                       └──────────────┘

Write Flow (Parallel Execution):

Step 1: CASSANDRA WRITE (Persistent)
   Write to partition: channel_123_2025_01_23_12
   • Time-based partitioning
   • Append-only (no updates)
   • Replication factor: 3
   • Quorum write (2 of 3)

Step 2: REDIS WRITE (Recent Cache)
   LPUSH channel:123:recent message_id
   LTRIM channel:123:recent 0 49
   • Keep last 50 messages
   • Fast access for UI
   • TTL: 24 hours

Step 3: KAFKA PUBLISH (Event)
   Publish to kafka topic
   • Consumed by WebSocket servers
   • Delivered to online users
   • Exactly-once semantics

All three happen in parallel: Total 5ms (slowest)

Write Optimizations:

1. APPEND-ONLY ARCHITECTURE
   • No updates or deletes
   • Extremely fast writes
   • Natural time-series fit

2. PARTITION STRATEGY
   Cassandra partition key:
   • channel_id + date + hour
   • Even distribution
   • Old partitions archived
   • Hot partitions in memory

3. BATCH OPERATIONS
   • Multiple messages buffered (100ms)
   • Written as single batch
   • Reduces Cassandra load by 10x

4. WRITE-LOCAL, READ-GLOBAL
   • Write to nearest datacenter
   • Async replication to others
   • Reads can go anywhere
   • Eventual consistency acceptable

Performance:
• Message delivery: 20ms (API response)
• Message persistence: 5ms (Cassandra)
• Real-time delivery: 50-100ms (WebSocket)
• Throughput: 5M messages/sec peak
• Message durability: 99.9999%
```

---

### Example 5: Stripe - Payment Processing

```
┌──────────────────────────────────────────────────────────────────┐
│              STRIPE PAYMENT WRITE ARCHITECTURE                    │
└──────────────────────────────────────────────────────────────────┘

Scale: Billions of API requests/year, Millions of payments/day

Challenge: Strong consistency + high availability + audit trail

Architecture:

Payment request
   ↓
┌────────────────────────┐
│   API Gateway          │
│   • TLS termination    │
│   • Rate limiting      │
│   • Idempotency key    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Payment Service       │
│   • Fraud check        │
│   • Validation         │
│   • Idempotency check  │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   PostgreSQL           │
│   (ACID Transactions)  │
│   • Synchronous write  │
│   • Immediate commit   │
│   • No async!          │
│   Time: 100ms          │
└────────┬───────────────┘
         │
         ├──────────────────────┬──────────────────┐
         │                      │                  │
         ▼                      ▼                  ▼
   ┌──────────┐          ┌──────────┐      ┌──────────┐
   │Replica 1 │          │Replica 2 │      │Event Log │
   │(Sync)    │          │(Sync)    │      │(Audit)   │
   └──────────┘          └──────────┘      └──────────┘

Synchronous Writes (By Design):

Critical Path:
1. Validate payment details
2. Check idempotency key (prevent duplicates)
3. Begin transaction
4. Deduct from source account
5. Add to destination account
6. Log event for audit
7. Synchronous replication (wait for 2 of 3)
8. Commit transaction
9. Return success

Total: 150-300ms (Acceptable trade-off for correctness)

Write Guarantees:

1. IDEMPOTENCY
   • Client provides idempotency key
   • Duplicate requests return same result
   • Stored for 24 hours
   • Prevents double charges

2. ACID TRANSACTIONS
   • All-or-nothing
   • No partial updates
   • Strong consistency
   • Immediate visibility

3. SYNCHRONOUS REPLICATION
   • Write to 2 of 3 replicas before ACK
   • Ensures durability
   • Higher latency but safer

4. EVENT SOURCING FOR AUDIT
   • Every state change logged
   • Immutable audit trail
   • Regulatory compliance
   • Can reconstruct any state

Sharding Strategy:

Partition by customer_id:
• 100 database shards
• Even distribution via hash
• Each shard: 100-500 writes/sec
• Cross-shard transactions avoided when possible

Optimization Techniques:

1. HOT/COLD SEPARATION
   Hot data (recent 90 days):
   • Fast SSD storage
   • Aggressive indexing
   • In-memory cache
   
   Cold data (older):
   • Cheaper storage (HDD/S3)
   • Archived format
   • Retrieved on-demand

2. CONNECTION POOLING
   • 100 connections per app server
   • Reused across requests
   • Reduces overhead by 50ms

3. PREPARED STATEMENTS
   • Query plans cached
   • Faster execution
   • Reduced parsing overhead

Performance:
• Payment processing: 150-300ms
• Write throughput: 10K+/sec
• Availability: 99.999% (5 nines)
• Data durability: 99.9999999% (11 nines)
• Zero data loss tolerance
```

---

### Example 6: Airbnb - Booking & Reservation System

```
┌──────────────────────────────────────────────────────────────────┐
│              AIRBNB BOOKING WRITE ARCHITECTURE                    │
└──────────────────────────────────────────────────────────────────┘

Scale: 7M+ listings, 100M+ bookings/year, Complex transactions

Challenge: Handle concurrent bookings for same listing

Architecture:

User books listing
   ↓
┌────────────────────────┐
│   Booking API          │
│   • Check availability │
│   • Calculate price    │
│   • Validate dates     │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Distributed Lock      │
│  (Redis/Zookeeper)     │
│   • Lock: listing_id   │
│   • TTL: 10 seconds    │
│   • Prevents conflicts │
│   Time: 5ms            │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   MySQL (Sharded)      │
│   Transaction:         │
│   1. Check availability│
│   2. Reserve dates     │
│   3. Create booking    │
│   4. Update calendar   │
│   Time: 100ms          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Release Lock         │
│   • Lock released      │
│   • Other requests OK  │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Event Bus (Kafka)    │
│   • BookingCreated     │
│   • Async processing   │
└────────┬───────────────┘
         │
         ├──────────────────┬──────────────────┐
         ▼                  ▼                  ▼
   ┌──────────┐      ┌──────────┐      ┌──────────┐
   │ Email    │      │ Payment  │      │Analytics │
   │ Service  │      │ Service  │      │ Service  │
   └──────────┘      └──────────┘      └──────────┘

Concurrent Booking Prevention:

Scenario: Two users book same dates simultaneously

User A (t=100ms): Requests Feb 1-5
User B (t=102ms): Requests Feb 1-5

Solution with Distributed Lock:
1. User A acquires lock on listing_123
2. User A checks availability (available)
3. User A reserves dates
4. User A releases lock
5. User B acquires lock on listing_123
6. User B checks availability (now unavailable)
7. User B receives error: "Dates no longer available"

Write Sharding Strategy:

Shard by listing_id:
• 20 database shards
• Hash(listing_id) % 20 = shard
• Each shard: Listings from specific regions
• Bookings co-located with listings
• No cross-shard transactions

Database Optimization:

1. OPTIMISTIC LOCKING
   version column prevents lost updates
   Retry on conflict (3 attempts)
   
2. READ-MODIFY-WRITE
   SELECT FOR UPDATE
   Prevents concurrent modifications
   Held only during transaction

3. CALENDAR DENORMALIZATION
   Separate calendar table:
   • listing_id + date = available/booked
   • Fast availability checks
   • No complex queries

4. ASYNC EVENT PROCESSING
   After booking confirmed:
   • Send confirmation email
   • Process payment
   • Update search index
   • Notify host
   All via Kafka (don't block user)

Performance:
• Booking creation: 100-150ms
• Lock acquisition: 95% success first try
• Throughput: 100 bookings/sec sustained
• Peak (holidays): 500 bookings/sec
• Conflict rate: <0.1% (well-handled)
```

---

### Example 7: LinkedIn - Profile Updates

```
┌──────────────────────────────────────────────────────────────────┐
│              LINKEDIN PROFILE WRITE ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 800M members, Millions of profile updates/day

Challenge: Update multiple databases, maintain consistency

Architecture:

User updates profile
   ↓
┌────────────────────────┐
│   Profile API          │
│   • Validate changes   │
│   • Sanitize input     │
│   • Check permissions  │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Espresso (Master)    │
│   • Primary storage    │
│   • ACID writes        │
│   • Synchronous        │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Databus (CDC)        │
│   • Change Data Capture│
│   • Publishes events   │
│   Time: 5ms            │
└────────┬───────────────┘
         │
         ├──────────────────────┬──────────────────┬──────────────────┐
         ▼                      ▼                  ▼                  ▼
   ┌──────────┐          ┌──────────┐      ┌──────────┐      ┌──────────┐
   │Couchbase │          │  Galene  │      │ Voyager  │      │  Search  │
   │ (Cache)  │          │  (Graph) │      │ (Search) │      │  Index   │
   │          │          │          │      │          │      │          │
   │Invalidate│          │Update    │      │Update    │      │Reindex   │
   │profile   │          │connections│     │skills    │      │profile   │
   └──────────┘          └──────────┘      └──────────┘      └──────────┘

Multi-Database Write Pattern:

Primary Write (Synchronous):
   User updates title/company
      ↓
   Write to Espresso (master)
   • Strong consistency
   • Immediate acknowledgment
   • Visible to user immediately

Secondary Writes (Async via CDC):
   Databus captures change
      ↓
   Publishes event: "ProfileUpdated"
      ↓
   Multiple consumers update derived data:
   • Couchbase: Invalidate cache
   • Galene: Update graph if job changed
   • Voyager: Reindex skills/endorsements
   • Search: Update member search index

Write Pattern Benefits:

1. SINGLE SOURCE OF TRUTH
   Espresso is authoritative
   All other DBs are derived
   Clear consistency model

2. ASYNC PROPAGATION
   Primary write: 50ms
   Secondary updates: 100-500ms lag
   Acceptable for profile data

3. EVENTUAL CONSISTENCY
   User sees update immediately
   Others see within seconds
   Good enough for social network

Performance:
• Profile update: 50ms (user-facing)
• Full propagation: 500ms
• Write throughput: 5K updates/sec
• Update latency acceptable
```

---

### Example 8: GitHub - Code Push & Repository Writes

```
┌──────────────────────────────────────────────────────────────────┐
│                GITHUB REPOSITORY WRITE ARCHITECTURE               │
└──────────────────────────────────────────────────────────────────┘

Scale: 100M+ repositories, 1M+ commits/day

Challenge: Handle large git pushes efficiently

Architecture:

Developer pushes code
   ↓
┌────────────────────────┐
│   Git Frontend         │
│   • Auth check         │
│   • Receive objects    │
│   • Validate refs      │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Git Storage (DFS)    │
│   • Distributed FS     │
│   • Object storage     │
│   • Pack files         │
│   • Replicated (3x)    │
│   Time: Variable       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   MySQL (Metadata)     │
│   • Repository info    │
│   • Branch refs        │
│   • Commit metadata    │
│   • Sharded by repo_id │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Kafka (Events)       │
│   • push_event         │
│   • Async processing   │
└────────┬───────────────┘
         │
         ├──────────────────┬──────────────────┐
         ▼                  ▼                  ▼
   ┌──────────┐      ┌──────────┐      ┌──────────┐
   │Webhooks  │      │   CI/CD  │      │ Activity │
   │Service   │      │ Trigger  │      │   Feed   │
   └──────────┘      └──────────┘      └──────────┘

Git Push Optimization:

1. INCREMENTAL PUSHES
   Only send new objects
   Reduces data transfer by 95%
   Delta compression

2. PACK FILES
   Compressed object storage
   Deduplicated data
   60-80% space savings

3. DISTRIBUTED STORAGE
   Large repos split across servers
   Parallel retrieval
   High availability

4. ASYNC POST-RECEIVE
   Push completes immediately
   Webhooks fired async
   CI/CD triggered in background

Write Sharding:

Partition by repository_id:
• Each repo assigned to shard
• 100+ shards
• Large repos: Dedicated shards
• Small repos: Co-located

Performance:
• Small push: 1-2 seconds
• Large push: 10-60 seconds
• Throughput: 1K pushes/sec
• Storage: Petabytes of code
```

---

### Example 9: Shopify - Order Creation

```
┌──────────────────────────────────────────────────────────────────┐
│                  SHOPIFY ORDER WRITE ARCHITECTURE                 │
└──────────────────────────────────────────────────────────────────┘

Scale: 2M+ merchants, 50M+ orders/year, Black Friday spikes

Challenge: Handle traffic spikes, maintain consistency

Architecture:

Customer places order
   ↓
┌────────────────────────┐
│   Checkout API         │
│   • Validate cart      │
│   • Check inventory    │
│   • Calculate total    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Redis (Lock)         │
│   • Lock SKUs          │
│   • Prevent oversell   │
│   Time: 5ms            │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   MySQL (Orders)       │
│   • Create order       │
│   • Deduct inventory   │
│   • ACID transaction   │
│   • Sharded by shop_id │
│   Time: 80ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Kafka (Events)       │
│   • order_created      │
│   • Async processing   │
└────────┬───────────────┘
         │
         ├──────────────────┬──────────────────┬──────────────────┐
         ▼                  ▼                  ▼                  ▼
   ┌──────────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐
   │ Payment  │      │Fulfillment│     │ Email    │      │Analytics │
   │ Process  │      │  Service  │      │ Service  │      │ Service  │
   └──────────┘      └──────────┘      └──────────┘      └──────────┘

Black Friday Optimization:

Challenge: 10x normal traffic (5K orders/sec)

Solutions:

1. WRITE QUEUEING
   Orders queue in Redis
   • Accept instantly (20ms)
   • Process in order
   • Smooth out spikes

2. AUTO-SCALING
   Add database shards dynamically
   • Monitor write queue depth
   • Spin up new capacity
   • Rebalance automatically

3. INVENTORY OVERSELL PROTECTION
   Distributed locks on SKUs
   • Prevent race conditions
   • 99.99% accuracy
   • Accepts small oversell (refund later)

4. BATCH INVENTORY UPDATES
   Group deductions:
   • 100 orders → 1 inventory update
   • Eventually consistent
   • Much faster

Performance:
• Order creation: 100ms (user-facing)
• Payment processing: 2-5 seconds (async)
• Black Friday throughput: 5K orders/sec
• Regular throughput: 500 orders/sec
• Availability during spikes: 99.9%
```

---

### Example 10: Robinhood - Stock Trade Execution

```
┌──────────────────────────────────────────────────────────────────┐
│              ROBINHOOD TRADE WRITE ARCHITECTURE                   │
└──────────────────────────────────────────────────────────────────┘

Scale: 1M+ trades/day, 2K trades/sec peak (market open/close)

Challenge: Low latency + strong consistency + audit compliance

Architecture:

User places order
   ↓
┌────────────────────────┐
│   Trading API          │
│   • Validate order     │
│   • Check funds        │
│   • Market hours check │
│   Time: 30ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Wallet Service       │
│   • Lock funds         │
│   • BEGIN TRANSACTION  │
│   • Deduct balance     │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   PostgreSQL (Orders)  │
│   • Insert order       │
│   • Status: PENDING    │
│   • Synchronous write  │
│   Time: 40ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Kafka (Order Queue)  │
│   • Topic: orders.new  │
│   • Async publish      │
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼ Return to user (130ms)
         │
         │ Background Order Execution
         ▼
┌────────────────────────┐
│   Order Matching       │
│   • Match internally   │
│   • Or route to NYSE   │
│   Time: 50-500ms       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Update Order Status  │
│   • Status: FILLED     │
│   • Execution price    │
│   • Settlement info    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Update Portfolio     │
│   • Add holdings       │
│   • Calculate P&L      │
│   • Update cache       │
└────────────────────────┘

Write Consistency Requirements:

CRITICAL (Strong Consistency):
• Order placement
• Fund deduction
• Trade execution
• Portfolio updates

USE SYNCHRONOUS WRITES:
• PostgreSQL with sync replication
• 2-phase commit where needed
• Immediate visibility

NON-CRITICAL (Eventual):
• Search index updates
• Analytics
• Notifications

USE ASYNC WRITES:
• Kafka event bus
• Background workers
• Lower latency

Database Sharding:

Shard by user_id:
• 20 database shards
• User's orders + portfolio co-located
• No cross-shard queries needed
• Each shard: 100 trades/sec

Write Optimizations:

1. WRITE-AHEAD LOG
   PostgreSQL WAL:
   • Sequential writes (fast)
   • Durability guaranteed
   • Can replay on crash

2. BATCH PORTFOLIO UPDATES
   Multiple trades batched
   • Update portfolio every 5 seconds
   • Reduces write load
   • Still accurate enough

3. EVENT SOURCING FOR AUDIT
   Every state change logged
   • Regulatory requirement (SEC)
   • Complete history
   • Can reconstruct any point in time

4. CONNECTION POOLING
   • 50 connections per server
   • Reused efficiently
   • Handles burst traffic

Performance:
• Order placement: 130ms (user-facing)
• Order execution: 500ms-3sec (async)
• Throughput: 2K orders/sec peak
• Write latency (p99): 200ms
• Trade success rate: 99.99%
```

---

## Design Patterns

### Pattern 1: Write-Ahead Logging (WAL)

```
┌──────────────────────────────────────────────────────────────────┐
│                  WAL PATTERN ARCHITECTURE                         │
└──────────────────────────────────────────────────────────────────┘

Write Request
   ↓
Step 1: APPEND TO LOG (Sequential, Fast)
┌────────────────────────┐
│   Write-Ahead Log      │
│   • Sequential writes  │
│   • 100K+ ops/sec      │
│   • Fsync for durability│
│   Time: 1-5ms          │
└────────┬───────────────┘
         │
         ▼
Step 2: ACKNOWLEDGE CLIENT
   Return success immediately
   Write is durable in log
   
Step 3: APPLY TO DATABASE (Async)
┌────────────────────────┐
│   Background Process   │
│   • Read from WAL      │
│   • Apply to DB        │
│   • Can batch          │
│   • Update indexes     │
└────────────────────────┘

Benefits:
• Fast writes (sequential I/O)
• Guaranteed durability
• Crash recovery (replay WAL)
• Batch optimization possible

Used By:
• PostgreSQL (pg_wal)
• MySQL (redo log)
• Redis (AOF)
• Kafka (commit log)
```

---

### Pattern 2: Event Sourcing

```
┌──────────────────────────────────────────────────────────────────┐
│                EVENT SOURCING PATTERN                             │
└──────────────────────────────────────────────────────────────────┘

Traditional State Storage:
┌─────────────────┐
│   users table   │
│   balance: 1000 │  ← Only current state
└─────────────────┘
   Lost: How did we get here?

Event Sourcing:
┌─────────────────────────────────────────┐
│   events table (append-only)            │
│                                         │
│   1. AccountCreated: +1000              │
│   2. Deposited: +500                    │
│   3. Withdrew: -200                     │
│   4. Deposited: +300                    │
│   → Current balance: 1600               │
└─────────────────────────────────────────┘
   ✓ Complete history preserved

Benefits:
• Audit trail (compliance)
• Time travel queries
• Event replay
• Very fast writes (append-only)

Architecture:
Events → Event Store (Cassandra)
      → Event Processor
      → Update Projections (Read Models)

Use Cases:
• Banking (transaction history)
• E-commerce (order lifecycle)
• Gaming (player actions)
• Audit logging
```

---

### Pattern 3: CQRS (Command Query Responsibility Segregation)

```
┌──────────────────────────────────────────────────────────────────┐
│                    CQRS PATTERN                                   │
└──────────────────────────────────────────────────────────────────┘

Traditional (Same Model for Read/Write):
┌─────────────────┐
│   PostgreSQL    │
│   (Normalized)  │
│   • Writes OK   │
│   • Reads slow  │  ← JOIN overhead
└─────────────────┘

CQRS (Separate Models):
┌─────────────────┐                 ┌─────────────────┐
│  Write Model    │    Events       │  Read Model     │
│  (PostgreSQL)   │─────────────────▶│  (MongoDB)      │
│  • Normalized   │                 │  • Denormalized │
│  • ACID         │                 │  • Optimized    │
│  • Fast writes  │                 │  • Fast reads   │
└─────────────────┘                 └─────────────────┘

Example:
Write: Create order (3 tables, normalized)
Event: OrderCreated published
Read Model: Single document with all order info

Benefits:
• Optimized for each operation
• Independent scaling
• Multiple read models
• Better performance

Use When:
• Read/write patterns very different
• Complex read requirements
• Can accept eventual consistency
```

---

### Pattern 4: Saga Pattern (Distributed Transactions)

```
┌──────────────────────────────────────────────────────────────────┐
│                    SAGA PATTERN                                   │
└──────────────────────────────────────────────────────────────────┘

Problem: Transaction across multiple services

Order Creation Saga:

Step 1: Reserve Inventory
┌────────────────────┐
│ Inventory Service  │ ← Reserve items
└────────────────────┘
   ↓ Success
   
Step 2: Process Payment
┌────────────────────┐
│ Payment Service    │ ← Charge card
└────────────────────┘
   ↓ Success
   
Step 3: Create Order
┌────────────────────┐
│  Order Service     │ ← Finalize order
└────────────────────┘
   ↓ Success
   
All steps succeed → Order complete

If Step 2 fails:
   Compensating Transaction:
   → Release inventory (undo Step 1)

If Step 3 fails:
   Compensating Transactions:
   → Refund payment (undo Step 2)
   → Release inventory (undo Step 1)

Benefits:
• No distributed locks
• Each service autonomous
• Better availability
• Scales horizontally

Trade-offs:
• Eventual consistency
• Compensating logic needed
• More complex error handling
```

---

## Decision Framework

```
┌──────────────────────────────────────────────────────────────────┐
│            COMPLETE WRITE SCALING DECISION MATRIX                 │
└──────────────────────────────────────────────────────────────────┘

BY CONSISTENCY REQUIREMENT:

Strong Consistency Needed:
├─ Financial transactions → PostgreSQL + Sync replication
├─ Inventory management → Distributed locks + ACID
├─ Booking systems → Pessimistic locking
└─ User authentication → Immediate consistency

Eventual Consistency Acceptable:
├─ Social media posts → Async + Kafka
├─ Analytics data → Batch writes
├─ Notifications → Queue-based
└─ Feed generation → Background workers

BY WRITE VOLUME:

< 1,000 WPS:
├─ Single database with optimization
├─ Connection pooling
└─ Basic batching

1,000 - 10,000 WPS:
├─ Async processing (Kafka/RabbitMQ)
├─ Write-ahead logging
├─ Batch writes
└─ Connection pooling

10,000 - 100,000 WPS:
├─ Database sharding (10+ shards)
├─ Async processing
├─ Geographic distribution
└─ Event-driven architecture

> 100,000 WPS:
├─ Multi-region sharding
├─ Event sourcing
├─ CQRS pattern
└─ Specialized databases (Cassandra, DynamoDB)

BY LATENCY REQUIREMENT:

< 50ms:
• Async writes (queue and return)
• Write to cache, persist async
• Trade consistency for speed

50-200ms:
• Sync database write
• Async secondary operations
• Balanced approach

> 200ms:
• Synchronous everything
• Strong consistency
• Full ACID transactions
```

---

## Interview Guide

### Framework for Write Scaling Interviews

```
┌──────────────────────────────────────────────────────────────────┐
│              INTERVIEW ANSWER FRAMEWORK                           │
└──────────────────────────────────────────────────────────────────┘

STEP 1: CLARIFY (5 minutes)
Questions to ask:
• Write rate (QPS)?
• Consistency requirements?
• Acceptable latency?
• Geographic distribution?
• Read:write ratio?
• Data size per write?
• Related writes (transactions)?

STEP 2: CAPACITY (3 minutes)
Calculate:
• Writes per second
• Peak load (3-10x average)
• Storage per day/month/year
• Bandwidth requirements

STEP 3: CHOOSE STRATEGY (10 minutes)

If eventual consistency OK:
  → Async processing (Kafka)
  → Fast API response
  → Background workers

If strong consistency needed:
  → Synchronous writes
  → ACID transactions
  → Higher latency acceptable

If high volume:
  → Sharding
  → Batch writes
  → Multiple masters

STEP 4: DESIGN ARCHITECTURE (15 minutes)

Components:
1. Load balancer
2. API servers
3. Message queue (if async)
4. Database (sharded if needed)
5. Workers (if async)
6. Monitoring

STEP 5: DISCUSS TRADE-OFFS (7 minutes)

Always mention:
• Consistency vs Availability
• Latency vs Throughput
• Cost vs Complexity
• Operational overhead
```

---

## Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                  WRITE SCALING BEST PRACTICES                     │
└──────────────────────────────────────────────────────────────────┘

ALWAYS DO:
✓ Use connection pooling
✓ Batch writes when possible
✓ Implement idempotency
✓ Monitor write latency (p50, p99)
✓ Plan for failures (retries)

AT MEDIUM SCALE (1K-10K WPS):
✓ Async processing with queues
✓ Write-ahead logging
✓ Batch optimizations
✓ Cache invalidation strategy

AT HIGH SCALE (>10K WPS):
✓ Database sharding
✓ Event-driven architecture
✓ Geographic distribution
✓ CQRS if needed

AVOID:
✗ Premature sharding
✗ Over-indexing
✗ Ignoring monitoring
✗ Complex distributed transactions
✗ Synchronous cross-service calls
```

---

**Document Version**: 2.0  
**Created**: January 2025  
**Type**: High-Level Design Guide  
**Examples**: 10 real-world architectures (Twitter, Instagram, Uber, Discord, Stripe, Airbnb, LinkedIn, GitHub, Shopify, Robinhood)  
**Focus**: Architecture patterns for interviews
