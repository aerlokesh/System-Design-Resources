# 🎯 Topic 49: DynamoDB Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering DynamoDB's architecture, partition keys and sort keys, single-table design, GSIs and LSIs, capacity modes (provisioned vs on-demand), DynamoDB Streams, Global Tables, transactions, DAX caching, hot partition handling, and how to articulate DynamoDB design decisions with depth and precision — especially for Amazon system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [DynamoDB Architecture — How It Works Under the Hood](#dynamodb-architecture--how-it-works-under-the-hood)
3. [Primary Key Design — The Most Critical Decision](#primary-key-design--the-most-critical-decision)
4. [Single-Table Design](#single-table-design)
5. [Secondary Indexes (GSI and LSI)](#secondary-indexes-gsi-and-lsi)
6. [Capacity Modes — Provisioned vs On-Demand](#capacity-modes--provisioned-vs-on-demand)
7. [DynamoDB Streams (CDC)](#dynamodb-streams-cdc)
8. [Global Tables (Multi-Region)](#global-tables-multi-region)
9. [Transactions](#transactions)
10. [DAX (DynamoDB Accelerator)](#dax-dynamodb-accelerator)
11. [Hot Partition Handling](#hot-partition-handling)
12. [Query Patterns and Access Design](#query-patterns-and-access-design)
13. [Cost Optimization](#cost-optimization)
14. [DynamoDB vs Other Databases](#dynamodb-vs-other-databases)
15. [Amazon Internal Patterns](#amazon-internal-patterns)
16. [Real-World Use Cases at Amazon Scale](#real-world-use-cases-at-amazon-scale)
17. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
18. [Common Interview Mistakes](#common-interview-mistakes)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**DynamoDB** is a fully managed, serverless, key-value and document database that delivers single-digit millisecond performance at any scale. It's Amazon's flagship NoSQL database, used internally across virtually every Amazon service, and a go-to choice in system design interviews — especially at Amazon.

```
Why DynamoDB is Amazon's default database:

  Fully managed:    No servers to provision, patch, or manage
  Serverless:       Auto-scales capacity (on-demand mode)
  Single-digit ms:  Consistent performance regardless of table size
  Multi-region:     Global Tables for active-active replication
  Durable:          Data replicated across 3 AZs automatically
  Integrated:       Native integration with Lambda, Streams, S3, Kinesis

  Amazon's philosophy: "Build on DynamoDB unless you have a specific 
  reason to choose something else."
  
  Used by: Amazon.com (shopping cart, orders), Alexa, Prime Video, 
           Twitch, Ring, AWS IAM, Route 53, and hundreds more.
```

---

## DynamoDB Architecture — How It Works Under the Hood

### Partition-Based Storage

```
DynamoDB splits tables into partitions:

  Table "orders"
  ┌──────────────────────────────────────────────────┐
  │  Partition 1          Partition 2          Partition 3 │
  │  ┌──────────┐       ┌──────────┐       ┌──────────┐ │
  │  │ Items    │       │ Items    │       │ Items    │ │
  │  │ A-G      │       │ H-P      │       │ Q-Z      │ │
  │  │          │       │          │       │          │ │
  │  │ 3K RCU   │       │ 3K RCU   │       │ 3K RCU   │ │
  │  │ 1K WCU   │       │ 1K WCU   │       │ 1K WCU   │ │
  │  │ 10 GB    │       │ 10 GB    │       │ 10 GB    │ │
  │  └──────────┘       └──────────┘       └──────────┘ │
  └──────────────────────────────────────────────────┘

Each partition:
  Storage: Up to 10 GB of data
  Throughput: Up to 3,000 RCU + 1,000 WCU
  Replication: 3 copies across 3 AZs (automatic)
  
Partition assignment:
  hash(partition_key) → determines which partition stores the item
  All items with same partition key → same partition
  
Auto-splitting:
  If partition exceeds 10 GB → DynamoDB splits it into 2 partitions
  If partition exceeds throughput → DynamoDB splits and redistributes
  Transparent to the application (no downtime, no schema change)
```

### Request Router

```
Every DynamoDB request goes through a request router:

  Client → Request Router → Target Partition

  Request Router:
    1. Authenticate (IAM)
    2. Determine target partition: hash(partition_key) → partition_id
    3. Route to the partition's leader node
    4. Return response

  Strong consistency read: Read from leader node (latest committed value)
  Eventually consistent read: Read from any of 3 replicas (may be slightly stale)
    → 50% cheaper, higher availability, slight staleness (usually < 1 second)
```

### Replication Within a Region

```
Each partition is replicated across 3 AZs:

  AZ-a: Leader (accepts writes)
  AZ-b: Replica (synchronous replication)
  AZ-c: Replica (synchronous replication)

Write path:
  Client → Leader → replicate to 2 replicas → committed (2 of 3 acknowledge)
  → Return success to client
  
  Writes are durable once 2 of 3 nodes acknowledge.
  If AZ-a fails: One of the replicas is promoted to leader.
  
Strong consistency:
  Read from Leader → guaranteed latest value
  Cost: 1 RCU per 4 KB (strong)
  
Eventually consistent:
  Read from any replica → may be slightly stale
  Cost: 0.5 RCU per 4 KB (half price!)
  Staleness: Usually < 100ms, worst case ~1 second
```

---

## Primary Key Design — The Most Critical Decision

### Two Types of Primary Keys

```
1. Partition Key only (Simple Primary Key):
   Item identified by partition key alone.
   Each partition key must be unique.
   
   Example: user_id = "user_123"
   → One item per user

2. Partition Key + Sort Key (Composite Primary Key):
   Multiple items can share the same partition key.
   Combination of (partition_key, sort_key) must be unique.
   
   Example: user_id = "user_123", order_id = "order_456"
   → Multiple orders per user, each identified by order_id
   
   CRITICAL: Items with same partition key are CO-LOCATED on the same partition.
   → Range queries within a partition key are extremely fast (sequential reads).
```

### Choosing the Partition Key

```
GOOD partition keys (high cardinality, uniform distribution):
  ✅ user_id: Millions of unique users → even distribution
  ✅ order_id: Random UUIDs → perfect distribution
  ✅ device_id: Millions of IoT devices → even distribution
  ✅ session_id: Random per session → perfect distribution

BAD partition keys (low cardinality, hot partitions):
  ❌ status: Only 3 values ("active", "inactive", "pending") → 3 partitions, very uneven
  ❌ date: One partition per day → today's partition gets all traffic
  ❌ country: ~200 values, US gets 60% of traffic → US partition is hot
  ❌ boolean: true/false → only 2 partitions

The test: "Will any single partition key value get >3,000 RCU or >1,000 WCU?"
  If yes → bad partition key. Need to redesign.
```

### Sort Key Design Patterns

```
Sort key enables RANGE QUERIES within a partition:

Pattern 1: Timestamp ordering
  PK: user_id, SK: timestamp
  Query: "All orders for user_123 in the last 7 days"
  → user_id = "user_123" AND SK BETWEEN "2025-03-08" AND "2025-03-15"
  → Sequential scan within one partition → very fast

Pattern 2: Hierarchical sorting
  PK: tenant_id, SK: "department#team#employee"
  Query: "All employees in engineering department"
  → tenant_id = "acme" AND SK BEGINS_WITH "engineering#"

Pattern 3: Version tracking
  PK: document_id, SK: version_number
  Query: "Latest version of document_123"
  → document_id = "doc_123" AND SK = (max)
  → ScanIndexForward=false, Limit=1 (latest first)

Pattern 4: Type-prefixed sort key (Single-Table Design)
  PK: user_id, SK: "PROFILE", "ORDER#2025-03-15#order_456", "ADDR#home"
  Query: "User's profile" → SK = "PROFILE"
  Query: "User's orders" → SK BEGINS_WITH "ORDER#"
  Query: "User's addresses" → SK BEGINS_WITH "ADDR#"
```

---

## Single-Table Design

### The Philosophy

```
Traditional SQL: One table per entity (users, orders, products, addresses)
  → JOINs combine data at query time

DynamoDB Single-Table: ALL entities in ONE table
  → No JOINs (DynamoDB doesn't support them)
  → Data pre-joined by co-locating related items in the same partition
  → One query fetches everything needed (user + orders + addresses)
```

### Example: E-Commerce User Data

```
Table: UserData

PK (user_id)    | SK              | Attributes
────────────────|─────────────────|──────────────────────────
user_123        | PROFILE         | name: "Alice", email: "alice@..."
user_123        | ADDR#home       | street: "123 Main St", city: "NYC"
user_123        | ADDR#work       | street: "456 Corp Ave", city: "NYC"
user_123        | ORDER#2025-03-15#ord_789 | total: 99.00, status: "shipped"
user_123        | ORDER#2025-03-10#ord_456 | total: 49.00, status: "delivered"
user_123        | PAYMENT#card_1  | last4: "4242", exp: "12/26"

Queries:
  "Get user profile":
    PK = "user_123", SK = "PROFILE" → 1 read, 1 item ✅

  "Get all user orders":
    PK = "user_123", SK begins_with "ORDER#" → 1 query, N items ✅

  "Get user profile + recent orders + addresses" (one API call):
    PK = "user_123" (no SK filter) → 1 query, all items for this user ✅
    Filter on SK prefix in application code to separate entity types.

  "Get orders from last 7 days":
    PK = "user_123", SK between "ORDER#2025-03-08" and "ORDER#2025-03-15" ✅

Why single-table?
  One query instead of 3 separate queries (user + orders + addresses)
  All data for one user is co-located on the same partition → fast
  Reduces round-trips → lower latency → better user experience
```

### When NOT to Use Single-Table Design

```
❌ Avoid when:
  - Team is unfamiliar with DynamoDB (learning curve is steep)
  - Data model changes frequently (schema flexibility is limited)
  - Access patterns are not well-defined upfront
  - You need complex ad-hoc queries (SQL is better)
  - Different entities have very different access patterns/scaling needs

✅ Best when:
  - Access patterns are well-known and stable
  - Low-latency, single-query access is critical
  - Data is naturally hierarchical (user → orders → items)
  - Team is experienced with DynamoDB
  - Amazon internal services (this is the standard pattern)
```

---

## Secondary Indexes (GSI and LSI)

### Global Secondary Index (GSI)

```
A GSI is a completely separate table (behind the scenes) with a different primary key:

Base table: PK = user_id, SK = order_date
GSI: PK = order_status, SK = order_date

Now you can query:
  Base: "All orders for user_123" → PK = "user_123"
  GSI:  "All pending orders across all users" → PK = "pending", SK = recent

GSI properties:
  ✅ Different partition key from base table
  ✅ Eventually consistent only (no strong consistency reads)
  ✅ Has its own provisioned throughput (separate RCU/WCU)
  ✅ Can be created/deleted anytime (no downtime)
  ✅ Max 20 GSIs per table
  
  ❌ Writes to base table → automatic async replication to GSI
     → GSI may be slightly behind (usually < 1 second)
  ❌ GSI storage = copy of projected attributes (additional storage cost)
  ❌ GSI write cost: Each base table write → 1 additional WCU per GSI

Cost consideration:
  3 GSIs → every write costs 4x (1 base + 3 GSIs)
  Don't create GSIs "just in case" — create for known access patterns
```

### Local Secondary Index (LSI)

```
An LSI shares the SAME partition key but has a different sort key:

Base table: PK = user_id, SK = order_date
LSI: PK = user_id, SK = order_total (same PK, different SK)

Now you can query:
  Base: "User's orders sorted by date" → PK = "user_123", SK = order_date
  LSI:  "User's orders sorted by amount" → PK = "user_123", SK = order_total

LSI properties:
  ✅ Supports strong consistency reads (unlike GSI)
  ✅ Shares partition throughput with base table
  ✅ Max 5 LSIs per table
  
  ❌ Must be created at table creation time (cannot add later!)
  ❌ Shares the 10 GB per partition limit with base table
  ❌ Same partition key as base table (can't query across partitions)

Rule: If you MIGHT need an LSI, create it at table creation.
  You can't add it later without recreating the table.
```

### GSI Overloading

```
Single GSI can serve multiple query patterns:

Table: UserData
  PK: user_id
  SK: entity_type#timestamp
  GSI1_PK: entity_type (e.g., "ORDER", "PROFILE", "PAYMENT")
  GSI1_SK: user_id#timestamp

GSI1 queries:
  "All orders across all users (paginated)":
    GSI1_PK = "ORDER", GSI1_SK begins_with "" → all orders, sorted by user+time
    
  "All profiles (admin view)":
    GSI1_PK = "PROFILE" → all user profiles

One GSI, multiple access patterns!
Key: Use the GSI PK as a "type" discriminator, GSI SK as the query dimension.
```

---

## Capacity Modes — Provisioned vs On-Demand

### Provisioned Capacity

```
You specify: "I need X reads/sec and Y writes/sec"

RCU (Read Capacity Unit):
  1 RCU = 1 strongly consistent read/sec for item ≤ 4 KB
  1 RCU = 2 eventually consistent reads/sec for item ≤ 4 KB
  Large items: 8 KB item = 2 RCU (strong) or 1 RCU (eventual)

WCU (Write Capacity Unit):
  1 WCU = 1 write/sec for item ≤ 1 KB
  Large items: 3 KB item = 3 WCU

Cost: ~$0.00065/RCU/hour, ~$0.00065/WCU/hour
  1,000 RCU + 500 WCU ≈ $700/month

Auto-scaling:
  Set target utilization (70%)
  DynamoDB adjusts capacity within min/max bounds
  Scale-up: 1-2 minutes (reactive, not instant)
  Scale-down: Up to 4 decreases per day per table

Best for: Predictable, steady traffic
  Cost savings: 60-75% cheaper than on-demand for steady workloads
```

### On-Demand Capacity

```
You specify: Nothing. DynamoDB auto-scales instantly.

Pay per request:
  $0.25 per million read request units
  $1.25 per million write request units

  1,000 reads/sec × 86,400 sec/day × $0.25/million = $21.60/day = $648/month
  500 writes/sec × 86,400 sec/day × $1.25/million = $54/day = $1,620/month
  Total: ~$2,268/month (vs ~$700 provisioned)

  On-demand is ~3x more expensive for steady traffic!

But: No capacity planning, no throttling, instant scaling.
  Handles 0 to 100K requests/sec without any configuration.

Best for:
  ✅ Unpredictable/spiky traffic (Black Friday, flash sales)
  ✅ New tables (don't know traffic pattern yet)
  ✅ Development/testing (don't waste money on idle capacity)
  ✅ Lambda-based architectures (bursty by nature)

Strategy:
  Start on-demand (no capacity planning needed)
  Once traffic pattern is stable (2-4 weeks) → switch to provisioned (save 60-75%)
  Use auto-scaling for provisioned to handle moderate spikes
```

### Burst Capacity

```
DynamoDB stores unused capacity for bursts:

  Provisioned: 1,000 RCU
  Quiet period: Only using 200 RCU for 5 minutes
  Unused: 800 RCU × 300 seconds = 240,000 RCU saved as "burst credits"
  
  Spike: Suddenly need 3,000 RCU → uses burst credits
  Duration: Burst credits last ~5 minutes of sustained over-provisioning
  
  After burst credits exhausted → throttled (ProvisionedThroughputExceededException)
  
  Important: Burst capacity is NOT guaranteed. Don't design around it.
  It's a safety net, not a feature to rely on.
```

---

## DynamoDB Streams (CDC)

### How Streams Work

```
DynamoDB Streams captures every write (INSERT, UPDATE, DELETE) as a stream record:

  Table write: PUT item {user_id: "123", name: "Alice"}
  Stream record:
    {
      "eventName": "INSERT",
      "dynamodb": {
        "Keys": {"user_id": {"S": "123"}},
        "NewImage": {"user_id": {"S": "123"}, "name": {"S": "Alice"}},
        "OldImage": null  // (for INSERT, no old image)
      }
    }

Stream views:
  KEYS_ONLY: Only the key attributes of the modified item
  NEW_IMAGE: The entire item as it appears after modification
  OLD_IMAGE: The entire item as it appeared before modification
  NEW_AND_OLD_IMAGES: Both new and old images (most useful, most expensive)

Retention: 24 hours (stream records available for 24 hours)
Ordering: Stream records are ordered by time within each partition key
```

### Common Stream Patterns

```
Pattern 1: Trigger Lambda on change
  DynamoDB → Stream → Lambda function
  Use: Send notification when order status changes
       Invalidate cache when user profile updates
       Sync data to Elasticsearch for search

Pattern 2: Cross-region replication (Global Tables uses this internally)
  DynamoDB → Stream → replication to another region

Pattern 3: Materialized views
  DynamoDB → Stream → Lambda → write to another DynamoDB table
  Use: Maintain a "view" table with different key structure
       Aggregate data (total orders per day)

Pattern 4: Event sourcing
  DynamoDB → Stream → Kinesis Data Firehose → S3
  Use: Archive all changes for audit trail
       Feed analytics pipeline with change events

Pattern 5: Elasticsearch sync
  DynamoDB → Stream → Lambda → Elasticsearch
  Use: Full-text search on DynamoDB data
       DynamoDB for OLTP, Elasticsearch for search queries
```

---

## Global Tables (Multi-Region)

### Active-Active Multi-Region

```
DynamoDB Global Tables: Multi-region, multi-active replication

  us-east-1 (Active) ←→ eu-west-1 (Active) ←→ ap-northeast-1 (Active)

Each region:
  ✅ Accepts reads AND writes
  ✅ Full replica of the table
  ✅ Low-latency local access
  
Replication:
  Asynchronous (typically < 1 second lag between regions)
  Conflict resolution: Last Writer Wins (LWW) by default
  Uses DynamoDB Streams internally for replication

Setup: One-click in console (or CloudFormation/CDK)
  No application changes needed!
  DynamoDB handles all replication automatically.

Consistency:
  Within region: Strong consistency available (read from local leader)
  Cross-region: Eventually consistent (async replication)
  
  Strong consistent read in us-east-1 sees writes from us-east-1 immediately
  But may not see writes from eu-west-1 until replication catches up (~1 second)
```

### Conflict Resolution

```
Two users update the same item in different regions simultaneously:

  us-east-1: UPDATE item SET price = 99 WHERE id = "prod_123" (at T=1000.001)
  eu-west-1: UPDATE item SET price = 89 WHERE id = "prod_123" (at T=1000.003)

Both succeed locally. When replication happens:
  LWW: eu-west-1 wins (T=1000.003 > T=1000.001)
  Final value in BOTH regions: price = 89

Implications:
  For most data: LWW is fine (last update wins is intuitive)
  For counters: LWW loses increments! Use conditional writes or application-level CRDTs
  For inventory: Use one-region-writes pattern (all checkout → us-east-1)
```

---

## Transactions

### DynamoDB Transactions

```
TransactWriteItems: Up to 100 actions, all-or-nothing

  transactWriteItems([
    Put(table="orders", item={order_id: "ord_789", user_id: "user_123", total: 99.00}),
    Update(table="inventory", key={product_id: "prod_456"}, 
           update="SET quantity = quantity - 1",
           condition="quantity > 0"),     // Fails if out of stock!
    Update(table="users", key={user_id: "user_123"},
           update="SET order_count = order_count + 1")
  ])

If ANY condition fails → ALL operations rolled back. Atomic.

Cost: 2x the cost of regular writes (transactions use 2-phase commit internally)
  Regular write: 1 WCU per 1 KB
  Transactional write: 2 WCU per 1 KB

Limitations:
  Max 100 items per transaction (across all tables)
  Max 4 MB total payload
  Items must be in the same region
  No cross-table transactions in Global Tables

Use cases:
  ✅ Place order: Create order + decrement inventory + update user stats
  ✅ Transfer funds: Debit account A + credit account B
  ✅ Conditional writes: "Update only if version matches" (optimistic locking)
```

### Optimistic Locking with Version Numbers

```
Pattern: Check-and-set using condition expressions

  Item: {id: "doc_123", content: "Hello", version: 5}

  Read: version = 5
  Modify content locally
  Write with condition:
    Update item 
    SET content = "Hello World", version = 6
    CONDITION version = 5

  If someone else updated between read and write:
    version is now 6 → condition fails → ConditionalCheckFailedException
    → Application retries: read again, modify, write with new version

This is the standard pattern for preventing lost updates in DynamoDB.
Much better than pessimistic locking (no lock held between read and write).
```

---

## DAX (DynamoDB Accelerator)

### In-Memory Caching for DynamoDB

```
DAX = Fully managed, in-memory cache IN FRONT of DynamoDB

  Application → DAX Cluster → DynamoDB

  Cache hit: Response in microseconds (~1-10μs)
  Cache miss: Falls through to DynamoDB (~1-5ms), caches result

DAX is API-compatible:
  Change DynamoDB client endpoint to DAX endpoint
  NO CODE CHANGES needed (same API, same SDK)
  
  ddb = boto3.client('dynamodb', endpoint_url='dax://my-cluster.xxx.dax-clusters.us-east-1.amazonaws.com')

Cache behavior:
  Item cache: Caches GetItem/PutItem results by primary key
  Query cache: Caches full Query results by query parameters
  TTL: Configurable (default 5 minutes)

When to use DAX:
  ✅ Read-heavy workloads (10:1+ read:write ratio)
  ✅ Need microsecond read latency
  ✅ Hot partition reads (DAX absorbs repeated reads for same key)
  ✅ Cost optimization (fewer RCUs needed from DynamoDB)

When NOT to use DAX:
  ❌ Write-heavy workloads (DAX doesn't cache writes meaningfully)
  ❌ Strong consistency required (DAX serves eventually consistent only)
  ❌ Infrequent reads (cache misses dominate → overhead without benefit)

Cost:
  DAX node: ~$200-$800/month per node (depends on instance type)
  Minimum: 3 nodes for HA (multi-AZ)
  Break-even: When DAX saves more in RCU costs than it costs to run
```

---

## Hot Partition Handling

### DynamoDB Adaptive Capacity

```
DynamoDB automatically handles hot partitions:

  Table: 10,000 RCU provisioned across 5 partitions = 2,000 RCU each
  
  Partition 3 suddenly gets 5,000 RCU (viral product)
  
  Adaptive capacity:
    DynamoDB "borrows" RCU from cold partitions
    Partition 3: Gets up to 5,000 RCU (from unused capacity elsewhere)
    Partition 1: Temporarily has 1,000 RCU (less demand anyway)
  
  Limit: Can borrow up to table-level provisioned capacity per partition
  Beyond that: Need to increase table capacity or redesign key

Instant Adaptive Capacity (newer):
  Works within seconds of detecting hot partition
  Can boost individual partition to full table capacity
  Even on-demand mode benefits from adaptive capacity
```

### Write Sharding for Hot Keys

```
Problem: Product "prime_day_deal_1" gets 50K writes/sec
  One partition key → one partition → 1,000 WCU max → throttled!

Solution: Shard the partition key
  Instead of: PK = "prime_day_deal_1"
  Use: PK = "prime_day_deal_1#" + random(0, 9)
  
  10 partition keys: "prime_day_deal_1#0" through "prime_day_deal_1#9"
  Each gets ~5K writes/sec → 10 partitions × 1K WCU each = well within limits

Read aggregation:
  To get total count: Query all 10 shards and SUM
  Or: Use DynamoDB Streams + Lambda to maintain aggregated count in a separate item
```

---

## Query Patterns and Access Design

### DynamoDB Design Process

```
Step 1: List ALL access patterns upfront
  Access Pattern 1: Get user profile by user_id
  Access Pattern 2: Get all orders for a user, sorted by date
  Access Pattern 3: Get order details by order_id
  Access Pattern 4: Get all orders with status "pending" (admin view)
  Access Pattern 5: Get top spenders (leaderboard)

Step 2: Design primary key to support patterns 1-3
  PK: user_id
  SK: "PROFILE" | "ORDER#<date>#<order_id>"
  
  Pattern 1: PK = user_id, SK = "PROFILE" ✅
  Pattern 2: PK = user_id, SK begins_with "ORDER#" ✅
  Pattern 3: Need GSI on order_id ✅

Step 3: Design GSIs for remaining patterns
  GSI1: PK = order_status, SK = order_date → Pattern 4 ✅
  GSI2: PK = "LEADERBOARD", SK = total_spend → Pattern 5 ✅

Step 4: Verify no hot partitions
  Pattern 4: PK = "pending" → only 3 status values → HOT!
  Fix: PK = "pending#<shard_0-9>", SK = order_date
  Scatter reads: Query all 10 shards, merge results
```

### Pagination

```
DynamoDB returns max 1 MB per query. For large result sets:

  response = table.query(
    KeyConditionExpression="PK = :pk AND SK begins_with :prefix",
    Limit=50  # Items per page
  )
  
  if "LastEvaluatedKey" in response:
    # More results available
    next_page = table.query(
      ExclusiveStartKey=response["LastEvaluatedKey"],
      Limit=50
    )

Cursor-based pagination:
  Client receives LastEvaluatedKey as an opaque cursor
  Client sends cursor in next request → DynamoDB resumes from that position
  
  Much better than offset-based pagination (no "skip 10,000 rows")
```

### Sparse Indexes

```
A GSI only contains items that have the GSI key attributes:

  Base table:
    {user_id: "u1", name: "Alice", is_admin: true}    ← has is_admin
    {user_id: "u2", name: "Bob"}                        ← no is_admin
    {user_id: "u3", name: "Charlie", is_admin: true}   ← has is_admin

  GSI on is_admin:
    Only u1 and u3 are in the GSI (u2 is excluded!)
    
  Query "all admin users": Scan GSI → only 2 items → fast!
  vs scanning base table → 3 items (and millions of non-admins)

Use for: Flags, optional attributes, "find items with X property"
  "All items on sale" → sparse GSI on sale_price attribute
  "All overdue orders" → sparse GSI on overdue_flag attribute
```

---

## Cost Optimization

### Cost Breakdown

```
DynamoDB costs:
  1. Read/Write capacity (RCU/WCU or on-demand requests)
  2. Storage ($0.25/GB/month for first 25 GB, then standard pricing)
  3. GSI capacity (separate RCU/WCU per GSI)
  4. DynamoDB Streams ($0.02 per 100K read requests)
  5. Data transfer (standard AWS data transfer pricing)
  6. Global Tables (additional WCU for cross-region replication)
  7. DAX (instance hours)

Biggest cost driver: Usually RCU/WCU (capacity charges)
```

### Optimization Strategies

```
1. Eventually consistent reads (50% cheaper):
   Use everywhere except where strong consistency is required
   Profile reads → eventually consistent ✅ (stale by < 1 second is fine)
   Inventory at checkout → strong consistency ✅ (must be exact)

2. Provisioned + auto-scaling (vs on-demand):
   Switch from on-demand to provisioned once traffic is predictable
   Savings: 60-75%

3. Reserved capacity:
   Commit to 1-year or 3-year → significant discount
   Best for: Baseline capacity that never changes

4. Minimize GSIs:
   Each GSI = additional write cost (every base write → GSI write)
   3 GSIs → 4x write cost
   Only create GSIs for proven access patterns

5. Use projection (KEYS_ONLY, INCLUDE) on GSIs:
   Don't project ALL attributes to GSI if you only need a few
   Reduces GSI storage and write throughput

6. TTL for auto-expiry:
   Set TTL attribute → DynamoDB automatically deletes expired items
   No write cost for TTL deletions!
   Use: Session data, temporary records, time-limited promotions

7. Smaller items:
   Use shorter attribute names (save bytes per item)
   Compress large values (gzip before storing)
   Move large blobs to S3, store S3 URL in DynamoDB
```

---

## DynamoDB vs Other Databases

| Aspect | DynamoDB | PostgreSQL | Cassandra | MongoDB |
|---|---|---|---|---|
| **Model** | Key-value + document | Relational (SQL) | Wide-column | Document |
| **Managed** | Fully (serverless) | RDS (managed), self-hosted | Self-managed (mostly) | Atlas (managed) |
| **Latency** | Single-digit ms | 1-10ms | 2-10ms | 1-10ms |
| **Scale** | Virtually unlimited | ~50K writes/sec (single master) | Linear (add nodes) | Sharded |
| **Consistency** | Eventual + strong (per read) | Strong (ACID) | Tunable | Tunable |
| **JOINs** | No | Yes (powerful) | No | Limited ($lookup) |
| **Transactions** | Yes (limited: 100 items) | Full ACID | Limited | Yes (4.0+) |
| **Schema** | Flexible (per item) | Fixed (enforced) | Fixed per column family | Flexible |
| **Cost model** | Per request or provisioned | Instance hours | Instance hours × nodes | Instance hours |
| **Best for** | Key-value, serverless, Amazon | Complex queries, ACID | High write throughput | Flexible schema |

```
Choose DynamoDB when:
  ✅ Key-value or simple document access patterns
  ✅ Need serverless / fully managed
  ✅ Single-digit ms latency at any scale
  ✅ Building on AWS (native integration with Lambda, Streams, etc.)
  ✅ Amazon internal (standard choice, team expertise)

Choose PostgreSQL when:
  ✅ Complex queries with JOINs
  ✅ Full ACID transactions across many tables
  ✅ Ad-hoc queries (SQL flexibility)
  ✅ Schema enforcement is valuable
```

---

## Amazon Internal Patterns

### The Amazon Way with DynamoDB

```
At Amazon, DynamoDB is the default choice unless you have a specific reason not to use it:

1. "Start with DynamoDB"
   New service? Default to DynamoDB.
   Need SQL? Must justify why (complex JOINs, full ACID, etc.)
   
2. Single-table design is standard
   All related data in one table
   Access patterns defined upfront
   GSIs for secondary access patterns

3. On-demand for new services
   Start on-demand (no capacity planning)
   Switch to provisioned after traffic stabilizes (cost optimization)

4. DynamoDB Streams for event-driven architectures
   Every table change → event → Lambda → downstream processing
   Replaces traditional polling and cron jobs

5. Global Tables for international services
   Amazon.com, Prime Video, Alexa → multi-region by default
   Global Tables provide < 1 second replication

6. DAX for read-heavy internal tools
   Admin dashboards, reporting tools → DAX for microsecond reads
   Product catalog reads → DAX absorbs hot item traffic
```

### Amazon Services on DynamoDB

```
Amazon.com:
  Shopping cart: DynamoDB (classic use case from the Dynamo paper)
  Order history: DynamoDB with time-based sort keys
  Product catalog: DynamoDB + Elasticsearch (search)
  
Prime Video:
  User viewing history: DynamoDB
  Content metadata: DynamoDB
  Recommendations: DynamoDB for serving pre-computed recommendations
  
Alexa:
  User profiles and preferences: DynamoDB
  Skill state persistence: DynamoDB
  Device registry: DynamoDB
  
AWS IAM:
  Policy storage: DynamoDB
  Role/permission lookups: DynamoDB (single-digit ms critical for auth)
  
Twitch:
  Chat messages: DynamoDB
  Channel metadata: DynamoDB
  User preferences: DynamoDB
```

---

## Real-World Use Cases at Amazon Scale

### Shopping Cart (The Classic)

```
PK: user_id
SK: "CART#<product_id>"

Add to cart:
  PUT item {PK: "u123", SK: "CART#prod_456", quantity: 2, added_at: "..."}

Get cart:
  Query PK = "u123", SK begins_with "CART#"
  → All items in user's cart in one query

Update quantity:
  Update PK = "u123", SK = "CART#prod_456" SET quantity = 3

Remove item:
  Delete PK = "u123", SK = "CART#prod_456"

Cart is per-user: Perfect partition key distribution.
Each user's cart is co-located: One query gets everything.
```

### Session Store

```
PK: session_id (UUID)
TTL: expires_at (epoch timestamp)

Create session:
  PUT {session_id: "sess_abc", user_id: "u123", created_at: "...", expires_at: {TTL}}

Read session (every request):
  GetItem PK = "sess_abc" → 0.5ms (or <0.01ms with DAX)
  
Session expiry:
  DynamoDB TTL automatically deletes expired sessions
  No cron job needed. No cost for TTL deletions.

Scale: Millions of sessions. Each is a separate item with unique PK.
  Perfect distribution. No hot partitions.
```

### Leaderboard

```
PK: "LEADERBOARD#global"
SK: score (zero-padded: "00005000#user_123")

Problem: DynamoDB doesn't have ZRANK like Redis.
  Can't efficiently compute "What's my rank out of 10M players?"

Solution 1: Pre-computed leaderboard in DynamoDB
  Batch job computes top-1000 → stores in DynamoDB
  Display: Query PK = "LEADERBOARD#global", Limit = 100
  Update: Nightly Spark job recomputes

Solution 2: Redis sorted set for leaderboard, DynamoDB for data
  Redis ZADD/ZREVRANGE for ranking operations
  DynamoDB for player profiles and detailed stats
  
  Better: Use Redis for what it's good at (ranking) and DynamoDB for storage.

Solution 3: Write sharding + scatter-gather
  Partition by score range: PK = "LEADERBOARD#0-999", "LEADERBOARD#1000-1999", etc.
  Scatter-gather: Query all partitions, merge results, top-K
  Expensive but works for moderate-scale leaderboards.
```

---

## Interview Talking Points & Scripts

### Why DynamoDB

> *"I'd use DynamoDB here because our access pattern is simple key-value lookups — get user by ID, get orders by user ID. We don't need JOINs or complex SQL queries. DynamoDB gives us single-digit millisecond latency at any scale, fully managed (no database administration), and native integration with Lambda for event-driven processing. At our scale of 100K reads/sec, DynamoDB on-demand handles it without any capacity planning."*

### Key Design

> *"I'd use a composite primary key: partition key = user_id, sort key = entity_type#timestamp. All of a user's data — profile, orders, addresses — is co-located in the same partition. 'Get user profile' is PK = user_id, SK = 'PROFILE'. 'Get recent orders' is PK = user_id, SK begins_with 'ORDER#'. One query returns everything I need, no JOINs."*

### Consistency Choice

> *"For the product catalog browse page, I'd use eventually consistent reads — half the cost and the data can be up to 1 second stale, which is fine for display. For the checkout flow where I check real-time inventory, I'd use strongly consistent reads to ensure the item is actually in stock at the moment of purchase."*

### Global Tables

> *"For our global user base, I'd enable DynamoDB Global Tables across us-east-1, eu-west-1, and ap-northeast-1. Each region accepts reads and writes with single-digit ms latency. Replication between regions is asynchronous with typically < 1 second lag. For profile updates, Last Writer Wins conflict resolution is fine — if a user updates their name from two devices, either value is acceptable."*

### Cost Optimization

> *"I'd start with on-demand capacity for the new service — no capacity planning, instant scaling. Once we have 4 weeks of traffic data and see a stable pattern, I'd switch to provisioned with auto-scaling — saving about 65% on our steady-state cost. I'd also use eventually consistent reads wherever possible — that's another 50% savings on reads."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Designing DynamoDB like a relational database
**Why it's wrong**: Creating multiple tables and trying to JOIN them. DynamoDB has no JOINs.
**Better**: Single-table design with composite keys. Pre-join data by co-location.

### ❌ Mistake 2: Using low-cardinality partition keys
**Why it's wrong**: PK = "status" (3 values) creates 3 hot partitions.
**Better**: High-cardinality keys (user_id, order_id). If you must use status, add a shard suffix.

### ❌ Mistake 3: Not mentioning consistency mode
**Why it's wrong**: Defaulting to strong consistency everywhere wastes 50% of your read budget.
**Better**: Eventually consistent for display/browse, strong consistent for transactions/checkout.

### ❌ Mistake 4: Creating too many GSIs "just in case"
**Why it's wrong**: Each GSI multiplies write costs. 3 GSIs = 4x write cost.
**Better**: Only create GSIs for proven, frequently-used access patterns.

### ❌ Mistake 5: Not mentioning DynamoDB Streams
**Why it's wrong**: Missing the event-driven architecture capability that DynamoDB provides natively.
**Better**: "DynamoDB Streams triggers a Lambda on every write — we use this to sync to Elasticsearch for search, update the cache, and send notifications."

### ❌ Mistake 6: Ignoring item size limits
**Why it's wrong**: DynamoDB items are limited to 400 KB. Large documents won't fit.
**Better**: Store metadata in DynamoDB, large payloads in S3. DynamoDB item contains S3 URL.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                 DYNAMODB DEEP DIVE CHEAT SHEET                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ARCHITECTURE: Partitioned, 3-AZ replication, serverless     │
│  LATENCY: Single-digit ms (reads AND writes)                 │
│  ITEM LIMIT: 400 KB max per item                             │
│                                                              │
│  PRIMARY KEY:                                                │
│    Simple: Partition key only (unique per item)              │
│    Composite: Partition key + sort key (range queries!)      │
│    PK design: High cardinality, uniform distribution         │
│                                                              │
│  SINGLE-TABLE DESIGN:                                        │
│    All entities in one table                                 │
│    SK prefix: "PROFILE", "ORDER#date", "ADDR#type"          │
│    One query fetches user + orders + addresses               │
│                                                              │
│  INDEXES:                                                    │
│    GSI: Different PK (max 20, eventually consistent only)    │
│    LSI: Same PK, different SK (max 5, must create at start)  │
│    Cost: Each GSI adds 1 WCU per base table write            │
│                                                              │
│  CAPACITY:                                                   │
│    On-demand: No planning, instant scale, 3x more expensive  │
│    Provisioned: Cheaper, needs capacity planning + auto-scale│
│    Strategy: Start on-demand → switch to provisioned          │
│                                                              │
│  CONSISTENCY:                                                │
│    Eventually consistent: 0.5 RCU/4KB (default, cheaper)    │
│    Strongly consistent: 1 RCU/4KB (latest value guaranteed) │
│    Use eventual for browsing, strong for checkout            │
│                                                              │
│  STREAMS: CDC for every write → Lambda/Kinesis/S3            │
│  GLOBAL TABLES: Multi-region active-active, LWW conflicts   │
│  TRANSACTIONS: Up to 100 items, 2x cost, all-or-nothing     │
│  DAX: In-memory cache, microsecond reads, API-compatible     │
│  TTL: Auto-delete expired items (free, no WCU cost)          │
│                                                              │
│  HOT PARTITIONS:                                             │
│    Adaptive capacity (automatic, borrows from cold)          │
│    Write sharding: PK + random(0-9) suffix                  │
│    DAX: Absorbs repeated reads for hot items                 │
│                                                              │
│  AMAZON PATTERN: "Default to DynamoDB unless you need SQL"   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 2: Database Selection & Data Modeling** — When to choose DynamoDB vs PostgreSQL
- **Topic 3: SQL vs NoSQL** — DynamoDB as the exemplar NoSQL database
- **Topic 12: Sharding & Partitioning** — DynamoDB's partition-based architecture
- **Topic 31: Hot Partitions & Hot Keys** — DynamoDB adaptive capacity and write sharding
- **Topic 34: Multi-Region & Geo-Distribution** — DynamoDB Global Tables
- **Topic 40: Redis Deep Dive** — DAX vs Redis for caching DynamoDB

---

*This document is part of the System Design Interview Deep Dive series.*
