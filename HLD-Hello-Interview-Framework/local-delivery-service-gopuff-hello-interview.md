# Design a Local Delivery Service (GoPuff) - Hello Interview Framework

> **Question**: Design a local delivery service like Gopuff that delivers convenience store items within 1 hour using 500+ micro distribution centers.
>
> **Difficulty**: Easy | **Level**: Mid-Senior

## Table of Contents
- [Understanding the Problem](#understanding-the-problem)
- [Set Up](#set-up)
- [High-Level Design](#high-level-design)
- [Deep Dives](#deep-dives)
- [What is Expected at Each Level?](#what-is-expected-at-each-level)

---

## Understanding the Problem

**💨 What is [Gopuff](https://www.gopuff.com)?**
Gopuff delivers goods typically found in a convenience store via rapid delivery and 500+ micro distribution centers (DCs).

### Functional Requirements

We'''ll start by asking our interviewer about the extent of the functionality we need to build. Our goal for this section is to produce a concise set of functionalities that the system needs to support. In this case, we need to be able to query availability of items in our Distribution Centers (DCs) and place orders.

**Core Requirements**

1. Customers should be able to query availability of items, deliverable in 1 hour, by location (i.e. the effective availability is the union of all inventory nearby DCs).
2. Customers should be able to order multiple items at the same time.

**Below the line (out of scope)**

- Handling payments/purchases.
- Handling driver routing and deliveries.
- Search functionality and catalog APIs. (The system is strictly concerned with availability and ordering).
- Cancellations and returns.

> **💡 Tip**: For this problem, the emphasis is on aggregating availability of items across local distribution centers and allowing users to place orders without double booking. In other problems you may be more concerned with the product catalog, search functionality, etc.

### Non-Functional Requirements

**Core Requirements**

1. Availability requests should be fast (<100ms) to support use-cases like search.
2. Ordering should be strongly consistent: two customers should not be able to purchase the same physical product.
3. System should be able to support 10k DCs and 100k items in the catalog across DCs.
4. Order volume will be O(10m orders/day)

**Below the line (out of scope)**

- Privacy and security.
- Disaster recovery.

---

## Set Up

### Planning the Approach

Before we go forward with designing the system, we'''ll think briefly through the approach we'''ll take.

For this problem, our requirements are fairly straightforward, so we'''ll take our default approach. We'''ll start by designing a system which simply supports our functional requirements without much concern for scale or our non-functional requirements. Then, in our deep-dives, we'''ll bring back those concerns one by one.

To do this we'''ll start by enumerating the "nouns" of our system, build out an API, and then start drawing our boxes.

### Defining the Core Entities

Core entities are the high level "nouns" for the system. We'''re not (yet) designing a full data model - for that, we'''ll need to have a better idea of how the system fits together overall. But by figuring out the entities we'''ll have the right blocks to build the rest of the system.

**Core Entities:**

- **Inventory**: A physical instance of an item, located at a DC. We'''ll sum up Inventory to determine the quantity available to a specific user for a specific `Item`.
- **Item**: A type of item, e.g. Cheetos. These are what our customers will actually care about.
- **DistributionCenter**: A physical location where items are stored. We'''ll use these to determine which items are available to a user. `Inventory` are stored in DCs.
- **Order**: A collection of `Inventory` which have been ordered by a user (and shipping/billing information).

> **💡 Tip**: One important thing to note for this problem is the distinction between `Item` and `Inventory`. You might think of this like the difference between a Class and an Instance in object-oriented programming. While our API consumers are strictly concerned with items that they might see in a catalog (e.g. Cheetos), we need to keep track of where the **physical** items are actually located.

### Defining the API

Next, we can start with the APIs we need to satisfy, which should track closely to our functional requirements. To meet our requirements we only need two APIs:

**API 1: Get Availability**
```
GET /api/v1/items/availability?lat={latitude}&lng={longitude}&keyword={keyword}&page={page}&limit={limit}

Response:
{
  "items": [
    {
      "item_id": "item_123",
      "name": "Cheetos",
      "available_quantity": 45,
      "price": 3.99
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 1500
  }
}
```

**API 2: Place Order**
```
POST /api/v1/orders

Body: {
  "user_id": "user_456",
  "items": [
    {"item_id": "item_123", "quantity": 2},
    {"item_id": "item_789", "quantity": 1}
  ],
  "delivery_location": {"lat": 37.7749, "lng": -122.4194}
}

Response:
{
  "order_id": "order_999",
  "status": "confirmed",
  "estimated_delivery": "2026-03-14T13:45:00Z"
}
```

> **ℹ️ Note**: We'''re passing location to both APIs - before the order can be processed, we need to confirm inventory is available close enough to deliver within 1 hour.

---

## High-Level Design

### 1) Customers should be able to query availability of items

To query availability, we have two steps:

1. **Find nearby DCs**: Find DCs close enough to deliver in 1 hour
2. **Check inventory**: Query inventory for those DCs and return the union

**Architecture Components:**

- **Availability Service**: Handles requests from users for availability given a specific location
- **Nearby Service**: Syncs with the database of nearby DCs and uses an external "Travel Time Service" to calculate travel times from DCs (potentially including traffic)
- **Inventory Table**: A replicated SQL database table which returns the inventory available for each item and DC

**Data Flow:**

1. User makes request to **Availability Service** with location X and Y
2. Availability service fires request to **Nearby Service**
3. Nearby service returns list of serviceable DCs
4. Availability service queries database with those DC IDs
5. Sum up results and return to client

> **💡 Tip**: In many e-commerce systems the "Catalog" is stored separately from the inventory because of different consumers and workloads. We'''ll store them in the same database here to make our job easier and adhere to requirements.

### 2) Customers should be able to order items

For orders, we require **strong consistency** to ensure two users don'''t order the same item. We need to check inventory, record the order, and update inventory atomically.

This "double booking" problem is common in system design. To ensure we don'''t allow two users to order the same inventory, we need some form of locking.

**Solution Options:**

❌ **Good (but complex)**: Two data stores with distributed lock
- Separate databases for orders and inventory
- Lock inventory records, create order, decrement inventory, release lock
- **Challenges**: Failure modes (crash after order creation), potential deadlocks

✅ **Great**: Singular Postgres Transaction
- Put both orders and inventory in same database
- Leverage ACID properties with `SERIALIZABLE` isolation level
- Entire transaction is atomic
- If two users try to order same item simultaneously, one is rejected

**Order Process:**

1. User makes request to **Orders Service** to place order
2. Orders Service creates a singular transaction:
   - Checks inventory for all items > 0
   - If any out of stock, transaction fails
   - If all in stock, records order and updates inventory status to "ordered"
   - Creates rows in Orders and OrderItems tables
   - Commits transaction
3. If successful, return order to user

> **💡 Tip**: When atomicity of transactions is a requirement, it'''s helpful to have your data colocated in an ACID data store. While it'''s possible to manage transactions across multiple data stores, the additional complexity isn'''t what we want to focus on here.

### Putting it all Together

**Final Architecture:**

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Client    │────────▶│ Availability │────────▶│   Nearby    │
│             │         │   Service    │         │   Service   │
└─────────────┘         └──────┬───────┘         └──────┬──────┘
                               │                        │
                               │                        ▼
                               │                 ┌──────────────┐
                               │                 │ Travel Time  │
                               │                 │   Service    │
                               │                 │  (External)  │
                               │                 └──────────────┘
                               │
                               ▼
┌─────────────┐         ┌──────────────────────────────────────┐
│   Client    │────────▶│        Orders Service                │
│             │         └──────┬───────────────────────────────┘
└─────────────┘                │
                               ▼
                    ┌──────────────────────────┐
                    │  Postgres (Partitioned)  │
                    │  ┌──────────┬──────────┐ │
                    │  │  Leader  │ Replica  │ │
                    │  └──────────┴──────────┘ │
                    │                          │
                    │  Tables:                 │
                    │  - Items                 │
                    │  - Inventory             │
                    │  - Orders                │
                    │  - OrderItems            │
                    │  - DistributionCenters   │
                    └──────────────────────────┘
```

**Key Points:**
- **Three services**: Availability Service, Orders Service, and shared Nearby Service
- **Postgres database**: Singular database for inventory and orders, partitioned by region
- **Read replicas**: Availability Service reads via replicas (eventual consistency OK)
- **Leader writes**: Orders Service writes to leader using atomic transactions (strong consistency)

---

## Deep Dives

### 1) Make availability lookups incorporate traffic and drive time

**Problem**: Our system currently only determines nearby DCs based on simple distance calculation. But if our DC is over a river or border, it may be close in miles but not in drive time. Traffic also influences travel times.

**Solution Options:**

❌ **Bad**: Simple SQL distance (Euclidean or Haversine formula)
- Doesn'''t account for traffic, road conditions, borders
- ```sql
  SELECT * FROM distribution_centers 
  WHERE SQRT(POW(lat - ?, 2) + POW(lng - ?, 2)) < threshold
  ```

❌ **Bad**: Use Travel Time Estimation Service against ALL DCs
- Too many queries to external service (10k DCs = 10k API calls per request)
- Most DCs aren'''t close enough anyway
- Slow and expensive

✅ **Great**: Use Travel Time Estimation Service against NEARBY DCs
- **Step 1**: Sync DC locations to memory every 5 minutes (they rarely change - they'''re buildings!)
- **Step 2**: Filter candidates within fixed radius (e.g., 60 miles maximum)
- **Step 3**: Only pass ~5-10 candidates to external travel time service
- **Result**: 99%+ reduction in external API calls

**Implementation:**
```java
public class NearbyService {
    private List<DC> dcCache;
    
    public List<DC> findNearbyDCs(double lat, double lng) {
        // Step 1: Filter by distance (60 mile radius)
        List<DC> candidates = dcCache.stream()
            .filter(dc -> haversineDistance(lat, lng, dc.lat, dc.lng) < 60)
            .collect(Collectors.toList());
        
        // Step 2: Get actual drive times from external service
        Map<DC, Duration> driveTimes = travelTimeService
            .getBatchTravelTimes(lat, lng, candidates);
        
        // Step 3: Return only DCs within 1 hour
        return driveTimes.entrySet().stream()
            .filter(e -> e.getValue().toMinutes() <= 60)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}
```

### 2) Make availability lookups fast and scalable

**Problem**: Availability lookups create heavy load on the database.

**Capacity Estimation:**

```
Given: 10M orders/day
Assume: 10 page views per purchase, 5% conversion rate

Queries: 10M orders/day / (100k seconds/day) * 10 / 0.05 = 20k queries/second
```

This is a sizeable number! We need to scale reads.

**Pattern: Scaling Reads**

Local delivery services like Gopuff demonstrate classic **scaling reads** patterns where inventory queries vastly outnumber actual purchases. With 20k queries/second for availability checks but only occasional inventory updates, aggressive caching with short TTLs becomes critical.

**Solution Options:**

✅ **Great**: Query Inventory Through Cache
- Add **Redis** instance for caching
- Cache hit: return immediately (~1ms)
- Cache miss: query database, write to cache
- Low TTL (e.g., 1 minute) ensures freshness
- **Orders Service expires affected cache entries on writes**

```java
public class AvailabilityService {
    
    public List<Item> getAvailability(double lat, double lng) {
        // Get nearby DCs
        List<DC> nearbyDCs = nearbyService.findNearbyDCs(lat, lng);
        
        String cacheKey = buildCacheKey(nearbyDCs);
        
        // Try cache first
        List<Item> cached = redis.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Cache miss - query database
        List<Item> items = database.query(
            "SELECT i.*, SUM(inv.quantity) as total FROM items i " +
            "JOIN inventory inv ON i.id = inv.item_id " +
            "WHERE inv.dc_id IN (?) AND inv.status = '''available''' " +
            "GROUP BY i.id",
            nearbyDCs.stream().map(DC::getId).collect(Collectors.toList())
        );
        
        // Write to cache with 1-minute TTL
        redis.setex(cacheKey, 60, items);
        
        return items;
    }
}
```

✅ **Great**: Postgres Read Replicas and Partitioning
- **Partition** inventory by region ID (first 3 digits of zipcode)
- Queries go to 1-2 partitions instead of entire dataset
- **Read replicas** for availability queries (tolerate slight inconsistency)
- **Leader** for order writes (strong consistency required)

```sql
-- Partitioned table
CREATE TABLE inventory (
    id BIGSERIAL,
    item_id BIGINT,
    dc_id BIGINT,
    quantity INT,
    status VARCHAR(20),
    region_id INT  -- First 3 digits of DC zipcode
) PARTITION BY HASH (region_id);

-- Create partitions
CREATE TABLE inventory_p0 PARTITION OF inventory FOR VALUES WITH (MODULUS 10, REMAINDER 0);
CREATE TABLE inventory_p1 PARTITION OF inventory FOR VALUES WITH (MODULUS 10, REMAINDER 1);
-- ... up to p9

-- Queries automatically route to correct partition(s)
SELECT * FROM inventory WHERE region_id IN (941, 942);  -- Only hits 2 partitions
```

**Final Optimized Architecture:**

```
                    ┌─────────────────┐
                    │  Redis Cache    │
                    │  (1-min TTL)    │
                    └────────┬────────┘
                             │
┌────────────┐      ┌────────▼────────┐      ┌──────────────┐
│  Client    │─────▶│  Availability   │─────▶│    Nearby    │
└────────────┘      │    Service      │      │   Service    │
                    └────────┬────────┘      └──────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │    Postgres Cluster          │
              │                              │
              │  ┌────────┐  ┌────────────┐ │
              │  │ Leader │  │ Replica 1  │ │
              │  └───┬────┘  │ Replica 2  │ │
              │      │       │ Replica 3  │ │
              │      │       └────────────┘ │
              │      │       (Availability   │
              │      │        queries)       │
              │      │                       │
              │      │ (Order writes)        │
              └──────┼───────────────────────┘
                     │
              ┌──────▼────────┐
              │ Orders Service│
              └───────────────┘
```

---

## What is Expected at Each Level?

### Mid-Level

**Breadth vs. Depth**: 80% breadth, 20% depth. Craft a high-level design that meets functional requirements.

**Probing the Basics**: Expect questions about each component. For example, if you use DynamoDB, expect questions about indexes.

**Driving**: Drive early stages, but interviewer may take over later to probe your design.

**The Bar for GoPuff**: Clearly define API endpoints and data model, create both routes (availability and orders). Good discussion around trade-offs. When using "Bad" solutions, show good discussion but don'''t need to immediately jump to "Great" solutions.

### Senior

**Depth of Expertise**: 60% breadth, 40% depth. Go into technical details in areas of hands-on experience.

**Advanced System Design**: Read volume and trivial partitioning should jump out. Have reasonable solutions ready.

**Architectural Decisions**: Clearly articulate pros/cons of choices, especially impact on scalability, performance, maintainability.

**Problem-Solving**: Demonstrate strong problem-solving skills. Anticipate challenges, suggest improvements, identify bottlenecks.

**The Bar for GoPuff**: Speed through initial design to discuss optimization in detail. Have optimized solutions for both atomic transactions and scaling availability service.

### Staff+

**Emphasis on Depth**: 40% breadth, 60% depth. Demonstrate "been there, done that" expertise.

**Proactivity**: Exceptional degree of proactivity. Identify and solve issues independently. Anticipate problems and implement preemptive solutions.

**Practical Application**: Well-versed in practical application of technologies. Draw from past experiences.

**Problem-Solving**: Top-notch skills. Make informed decisions considering scalability, performance, reliability, maintenance.

**Advanced System Design**: Focus on scalability and reliability under high load. Deep understanding of distributed systems, load balancing, caching strategies.

**The Bar for GoPuff**: Dive deep into 2-3 key areas. Show unique insights for follow-up questions. Interviewer should gain new understanding from your discussion.

---

## Summary: Key Design Decisions

| Decision | Chosen | Why |
|----------|--------|-----|
| **Data Store** | Single Postgres database | Leverage ACID properties for atomic transactions |
| **Consistency** | Strong consistency for orders | Prevent double-booking of inventory |
| **Nearby DCs** | Travel Time Service + distance filter | Accurate 1-hour delivery estimation with traffic |
| **Read Scaling** | Redis cache + Read replicas | Handle 20k queries/second with <100ms latency |
| **Partitioning** | By region (zipcode prefix) | Queries hit 1-2 partitions instead of full dataset |
| **Cache Strategy** | 1-minute TTL, invalidate on writes | Balance freshness with performance |

## Key Interview Talking Points

1. **"Item vs Inventory"** — Item is catalog concept, Inventory is physical instance at DC
2. **"ACID transactions"** — Single Postgres database enables atomic order + inventory update
3. **"Distance pre-filter"** — 60-mile radius reduces travel time API calls by 90%+
4. **"Read-heavy workload"** — 20k availability queries/sec vs occasional inventory updates
5. **"Redis + replicas"** — Cache + read replicas handle read scale, leader handles writes
6. **"Region partitioning"** — Zipcode-based partitioning localizes queries
7. **"Cache invalidation"** — Orders Service expires cache entries on inventory updates
8. **"Strong vs eventual"** — Strong consistency for orders, eventual OK for availability

---

**Created**: March 2026  
**Framework**: Hello Interview (Simplified)  
**Source**: https://www.hellointerview.com/learn/system-design/problem-breakdowns/gopuff  
**Estimated Interview Time**: 30-45 minutes
