# DynamoDB System Design Interview Guide

## Why DynamoDB Matters in Interviews

DynamoDB is frequently asked about because it's **AWS's flagship NoSQL database** used by:
- Amazon.com (shopping cart, product catalog)
- Netflix (user profiles, viewing history)
- Lyft (ride tracking, driver location)
- Airbnb (booking system, availability)

---

## 1. Partition Key Design - Most Critical Interview Topic

### Interview Question: "How do you design a partition key for a Twitter-like system?"

**❌ BAD Design (Hot Partition Problem):**
```python
# Storing all tweets in one partition
Partition Key: "tweets"
Sort Key: timestamp

# Problem: ALL tweets go to one partition!
# Result: Throttling, slow queries, wasted capacity
```

**✅ GOOD Design (Distributed Load):**
```python
# Method 1: User-based partitioning
Partition Key: user_id
Sort Key: timestamp

# Benefits:
# - Each user's tweets in separate partition
# - Naturally distributes load
# - Fast queries: "Get user's tweets"

# Method 2: Time-based sharding for global timeline
Partition Key: "timeline#{shard_number}"  # 0-99
Sort Key: timestamp

# Pick shard: hash(tweet_id) % 100
# Benefits:
# - Load distributed across 100 partitions
# - Can scale to trillions of tweets
```

**Real Example - Lyft Ride Tracking:**
```python
# BAD: Single partition for all rides
PK: "rides", SK: timestamp  # ❌ Hot partition!

# GOOD: Partition by driver
PK: driver_id, SK: timestamp
# Each driver's rides separate
# Naturally balanced (drivers evenly distributed)
```

**Interview Tip:** Always ask: "What's my access pattern?" before designing keys.

---

## 2. Single-Table Design - Advanced Interview Topic

### Interview Question: "Design a DynamoDB schema for a multi-tenant SaaS application (like Slack)"

**Traditional Approach (Multiple Tables - ❌):**
```
Users Table: user_id → user_data
Channels Table: channel_id → channel_data
Messages Table: message_id → message_data

Problem: 
- Expensive JOIN-like operations
- Multiple table queries (slow)
- Complex transaction management
```

**Single-Table Design (✅ Netflix/Amazon Way):**
```python
PK                          SK                      Data
------------------------------------------------------------------
ORG#slack                   ORG#slack               {org_name, plan}
ORG#slack                   USER#alice              {name, email, role}
ORG#slack                   USER#bob                {name, email, role}
ORG#slack                   CHANNEL#general         {name, topic}
ORG#slack#CHANNEL#general   MSG#2024-01-15T10:00    {text, sender}
ORG#slack#CHANNEL#general   MSG#2024-01-15T10:05    {text, sender}

# Access Patterns:
# 1. Get org details: PK=ORG#slack, SK=ORG#slack
# 2. List all users: PK=ORG#slack, SK begins_with USER#
# 3. List channels: PK=ORG#slack, SK begins_with CHANNEL#
# 4. Get channel messages: PK=ORG#slack#CHANNEL#general, SK begins_with MSG#
```

**Why This Works:**
- **One Query**: Get org + users + channels in single request
- **Atomic Updates**: All related data in same partition
- **Cost-Effective**: Fewer read operations
- **Scalable**: Each org is independent partition

**Used By:**
- Netflix: User profiles + viewing history + recommendations
- Amazon: Order + items + shipping in one query
- Airbnb: Listing + reviews + bookings together

---

## 3. Global Secondary Index (GSI) - Critical for Access Patterns

### Interview Question: "How do you support multiple access patterns efficiently?"

**Scenario: E-commerce Order System**

**Table Schema:**
```python
PK: customer_id
SK: order_id
Attributes: order_date, status, total_amount
```

**Access Patterns Needed:**
1. Get customer's orders (Primary Key) ✅
2. Find all pending orders (Need GSI) ❌
3. Get orders by date range (Need GSI) ❌

**Solution: Add GSI**
```python
# GSI 1: Orders by Status
GSI_PK: status (e.g., "PENDING", "SHIPPED")
GSI_SK: order_date

# Query: Get all pending orders sorted by date
response = table.query(
    IndexName='StatusDateIndex',
    KeyConditionExpression='status = :status',
    ExpressionAttributeValues={':status': 'PENDING'}
)

# GSI 2: Orders by Date
GSI_PK: "ORDER"  # Fixed value
GSI_SK: order_date

# Query: Get recent orders across all customers
response = table.query(
    IndexName='DateIndex',
    KeyConditionExpression='GSI_PK = :type AND order_date > :date',
    ExpressionAttributeValues={
        ':type': 'ORDER',
        ':date': '2024-01-01'
    }
)
```

**Real Example - Airbnb Search:**
```python
# Main Table:
PK: listing_id
SK: "LISTING"

# GSI: Search by location + price
GSI_PK: city (e.g., "SF")
GSI_SK: price_per_night

# Query: Find SF listings under $200
response = table.query(
    IndexName='LocationPriceIndex',
    KeyConditionExpression='city = :city AND price < :max',
    ExpressionAttributeValues={
        ':city': 'SF',
        ':max': 200
    }
)
```

**GSI vs LSI - Interview Comparison:**

| Feature | GSI | LSI |
|---------|-----|-----|
| Partition Key | Different from table | Same as table |
| Sort Key | Can be different | Must be different |
| Creation | Anytime | Only at table creation |
| Consistency | Eventually consistent | Strongly consistent |
| Use Case | New access patterns | Alternative sorting |

---

## 4. Hot Partition Problem - Must-Know for Interviews

### Interview Question: "Your celebrity user has 10M followers. How do you avoid hot partitions?"

**Problem:**
```python
# Celebrity with 10M followers
PK: user_id (celeb)
SK: follower_id

# Result: 10M items in ONE partition
# DynamoDB limit: 10GB per partition
# Read/Write capacity: Throttled!
```

**Solution 1: Write Sharding**
```python
# Shard the partition key
PK: f"user#{user_id}#shard#{random(0, 10)}"
SK: follower_id

# Writes distributed across 10 partitions
# For reads: Query all 10 shards in parallel
```

**Solution 2: Inverted Index (Better)**
```python
# Instead of storing followers under celebrity
# Store following list under each user

# For user "alice" following "celebrity":
PK: alice
SK: FOLLOWING#celebrity

# Benefits:
# - Load naturally distributed (millions of users)
# - Each user has reasonable data (<1000 following)
# - No celebrity hot spot!
```

**Real Example - Instagram Likes:**
```python
# BAD: Store all likes under photo
PK: photo_id
SK: user_id
# Viral photo = 100M likes = Hot partition!

# GOOD: Store likes under user
PK: user_id
SK: LIKED#photo_id
# Distributed: Each user has ~1000 likes
# To get like count: Use separate counter

# Like counter in separate table:
PK: photo_id
SK: "STATS"
Attributes: like_count (use UpdateItem with ADD)
```

---

## 5. Transactions - ACID in NoSQL

### Interview Question: "How do you transfer money between accounts in DynamoDB?"

**Without Transactions (❌ Race Condition):**
```python
# Step 1: Read balances
account_a = get_item(PK='ACCT#123')  # $1000
account_b = get_item(PK='ACCT#456')  # $500

# Step 2: Update balances
put_item(PK='ACCT#123', balance=900)   # -$100
put_item(PK='ACCT#456', balance=600)   # +$100

# Problem: What if second put_item fails?
# Result: Money vanishes! (Lost $100)
```

**With Transactions (✅ ACID):**
```python
import boto3
dynamodb = boto3.client('dynamodb')

response = dynamodb.transact_write_items(
    TransactItems=[
        {
            'Update': {
                'TableName': 'Accounts',
                'Key': {'account_id': {'S': 'ACCT#123'}},
                'UpdateExpression': 'SET balance = balance - :amount',
                'ConditionExpression': 'balance >= :amount',
                'ExpressionAttributeValues': {':amount': {'N': '100'}}
            }
        },
        {
            'Update': {
                'TableName': 'Accounts',
                'Key': {'account_id': {'S': 'ACCT#456'}},
                'UpdateExpression': 'SET balance = balance + :amount',
                'ExpressionAttributeValues': {':amount': {'N': '100'}}
            }
        }
    ]
)

# Benefits:
# - All or nothing (ACID)
# - ConditionExpression prevents overdraft
# - Up to 100 items per transaction
```

**Real Example - E-commerce Order:**
```python
# Atomic order placement:
dynamodb.transact_write_items(
    TransactItems=[
        # 1. Create order
        {'Put': {...}},
        # 2. Decrement inventory
        {'Update': {...}},
        # 3. Record in user's order history
        {'Put': {...}},
        # 4. Update analytics counter
        {'Update': {...}}
    ]
)

# If inventory insufficient: Entire transaction fails
# No partial orders!
```

---

## 6. Time Series Data - Common Interview Pattern

### Interview Question: "Design a system to store IoT sensor data (1M devices, 1 reading/second)"

**Naive Approach (❌):**
```python
PK: device_id
SK: timestamp

# Problem: 86,400 writes per device per day
# With 1M devices = 86 billion items per day!
# Cost: Expensive, slow queries
```

**Optimized Approach (✅ Time-Bucketing):**
```python
# Group readings into hourly buckets
PK: device_id
SK: hour_bucket  # e.g., "2024-01-15-14"
Attributes: {
    'readings': [
        {'timestamp': '14:00:01', 'temp': 72.5},
        {'timestamp': '14:00:02', 'temp': 72.6},
        # ... 3600 readings
    ]
}

# Benefits:
# - 24 items per device per day (vs 86,400)
# - 96% fewer items!
# - Query entire hour in one request
# - Much cheaper ($$$)
```

**Real Example - AWS CloudWatch Metrics:**
```python
# CloudWatch stores metrics using similar pattern
PK: metric_name
SK: time_bucket  # 1-minute buckets
Attributes: {
    'statistics': {
        'count': 60,
        'sum': 1523.4,
        'min': 20.1,
        'max': 35.7,
        'samples': [...]  # Pre-aggregated
    }
}

# Query: Get last hour of data
# Result: 60 items (not 3600!)
```

**Time Series TTL:**
```python
# Auto-delete old data
item = {
    'PK': 'device#123',
    'SK': '2024-01-15-14',
    'readings': [...],
    'TTL': 1735689600  # Expires in 90 days
}

# DynamoDB automatically deletes expired items
# No manual cleanup needed!
# Perfect for: Logs, metrics, events
```

---

## 7. Conditional Writes - Prevent Race Conditions

### Interview Question: "How do you implement a like button that prevents double-likes?"

**Without Condition (❌):**
```python
# User clicks "like"
put_item(
    TableName='Likes',
    Item={
        'photo_id': 'photo123',
        'user_id': 'alice',
        'timestamp': '2024-01-15T10:00:00'
    }
)

# Problem: User double-clicks
# Result: Duplicate likes counted!
```

**With Condition (✅):**
```python
try:
    table.put_item(
        Item={
            'photo_id': 'photo123',
            'user_id': 'alice',
            'timestamp': '2024-01-15T10:00:00'
        },
        ConditionExpression='attribute_not_exists(user_id)'
    )
    # Also increment like count
    table.update_item(
        Key={'photo_id': 'photo123'},
        UpdateExpression='ADD like_count :inc',
        ExpressionAttributeValues={':inc': 1}
    )
except ClientError as e:
    if e.response['Error']['Code'] == 'ConditionalCheckFailedException':
        print("Already liked!")
        
# Benefits:
# - Prevents duplicates
# - Atomic operation
# - No race conditions
```

**Real Example - Seat Booking:**
```python
# Book concert seat (prevent double-booking)
try:
    table.update_item(
        Key={'seat_id': 'A15'},
        UpdateExpression='SET status = :booked, user_id = :user',
        ConditionExpression='status = :available',
        ExpressionAttributeValues={
            ':booked': 'BOOKED',
            ':available': 'AVAILABLE',
            ':user': 'alice'
        }
    )
    return "Seat booked!"
except ClientError:
    return "Seat already taken!"

# Handles race conditions:
# - 100 users try to book same seat simultaneously
# - Only ONE succeeds (atomic)
# - Others get error immediately
```

---

## 8. Cost Optimization - Critical for Real Systems

### Interview Question: "Your DynamoDB bill is $10,000/month. How do you reduce it?"

**Cost Breakdown:**
```
Storage: $0.25 per GB-month
On-Demand Reads: $0.25 per million reads
On-Demand Writes: $1.25 per million writes
Provisioned: ~50-75% cheaper at scale
```

**Optimization 1: Reduce Item Size**
```python
# BAD: Store full JSON (5 KB per item)
item = {
    'user_id': 'user123',
    'user_name': 'Alice Johnson',
    'user_email': 'alice.johnson@example.com',
    'user_phone': '+1-555-123-4567',
    # ... 50 more fields
}

# GOOD: Store only needed fields (0.5 KB per item)
item = {
    'uid': 'u123',  # Shortened
    'name': 'Alice',
    'email': 'a@ex.com'
}

# Savings: 10x smaller = 10x cheaper!
```

**Optimization 2: Use Projections in GSI**
```python
# BAD: Project all attributes
GSI = {
    'ProjectionType': 'ALL'  # Duplicates entire table!
}

# GOOD: Project only needed attributes
GSI = {
    'ProjectionType': 'INCLUDE',
    'NonKeyAttributes': ['name', 'status']  # Only what's queried
}

# Savings: 80% less storage in GSI
```

**Optimization 3: Switch to Provisioned**
```python
# Scenario: 1 billion requests/month
# On-Demand: $250 (reads) + $1,250 (writes) = $1,500/month
# Provisioned: ~$750/month with auto-scaling

# When to use:
# On-Demand: Unpredictable, spiky traffic
# Provisioned: Steady, predictable load
```

**Optimization 4: Batch Operations**
```python
# BAD: Individual writes (100 items)
for item in items:
    table.put_item(Item=item)  # 100 write requests

# GOOD: Batch write
with table.batch_writer() as batch:
    for item in items:
        batch.put_item(Item=item)  # ~4 write requests

# Savings: 96% fewer requests!
```

**Real Example - Netflix:**
```
"We reduced DynamoDB costs by 80% using:
1. Compress large attributes (videos, images)
2. Use S3 for large objects (>400 KB)
3. TTL for temporary data (sessions, caches)
4. Careful GSI design (project minimal fields)"
```

---

## 9. DynamoDB vs Other Databases - Interview Comparison

### When to Use DynamoDB

**✅ Perfect For:**
- **Key-value lookups**: User sessions, caching
- **Known access patterns**: Twitter timeline, user feeds
- **High-scale writes**: IoT sensors, clickstream
- **Global distribution**: Multi-region apps (DynamoDB Global Tables)
- **Serverless apps**: Lambda + DynamoDB = perfect match

**❌ Not Good For:**
- **Ad-hoc queries**: "Find all users where..."
- **Complex joins**: Multi-table relationships
- **Analytics**: OLAP, data warehousing
- **Unpredictable access patterns**: Frequent schema changes

### DynamoDB vs MongoDB

| Feature | DynamoDB | MongoDB |
|---------|----------|---------|
| Scaling | Automatic, infinite | Manual sharding |
| Performance | <10ms guaranteed | Varies with load |
| Management | Fully managed | Self-managed or Atlas |
| Query Flexibility | Limited (Query/Scan) | Full SQL-like queries |
| Cost | Pay per request | Pay for instance |
| Best For | AWS-native, predictable | Flexible schema, complex queries |

**Real Comparison - Uber:**
```
Started with MongoDB:
- Flexible for rapid development
- Easy complex queries

Migrated to DynamoDB:
- Better scalability (10M+ rides/day)
- Predictable latency (<10ms)
- Lower operational overhead
- But: Required schema redesign
```

### DynamoDB vs RDS (PostgreSQL/MySQL)

| Use Case | Choose |
|----------|--------|
| User sessions | DynamoDB |
| Financial transactions | RDS (ACID, complex queries) |
| Shopping cart | DynamoDB |
| Order processing | RDS (joins, reporting) |
| IoT sensor data | DynamoDB |
| Product catalog | RDS (searchable, filterable) |
| Leaderboards | DynamoDB |
| Analytics | RDS + Redshift |

---

## Common Interview Questions & Answers

### Q1: "How does DynamoDB achieve single-digit millisecond latency?"

**Answer:**
1. **In-Memory Caching**: Hot data cached in RAM
2. **SSD Storage**: All data on SSDs (vs HDDs)
3. **Partition-Based**: Parallel queries across partitions
4. **Optimized for Key-Value**: No complex query planning
5. **Local Indexes**: Co-located with data

**Performance Numbers to Mention:**
- GetItem: 1-3ms average
- Query: 5-10ms average
- Scan: Avoid in production (expensive)
- BatchGetItem: Same latency, 100x items

---

### Q2: "What's the maximum item size in DynamoDB?"

**Answer:** 400 KB per item

**Workarounds for Larger Data:**
```python
# Store large objects in S3
item = {
    'user_id': 'user123',
    'profile_photo': 's3://bucket/photos/user123.jpg',  # URL, not data
    'video': 's3://bucket/videos/user123.mp4'
}

# Benefits:
# - DynamoDB: Fast metadata queries
# - S3: Cheap large object storage
# - Common pattern: Netflix, Instagram
```

---

### Q3: "How do you handle pagination in DynamoDB?"

**Answer:**
```python
# First page
response = table.query(
    KeyConditionExpression='user_id = :uid',
    ExpressionAttributeValues={':uid': 'user123'},
    Limit=20  # Page size
)
items = response['Items']
last_key = response.get('LastEvaluatedKey')

# Next page
if last_key:
    response = table.query(
        KeyConditionExpression='user_id = :uid',
        ExpressionAttributeValues={':uid': 'user123'},
        Limit=20,
        ExclusiveStartKey=last_key  # Continue from here
    )
```

**Interview Tip:** DynamoDB pagination is **cursor-based**, not offset-based.
- No "skip 1000" - must iterate through pages
- Efficient for infinite scroll (social media feeds)

---

## Key Interview Takeaways

### Design Principles (Memorize These!)

1. **Design for Access Patterns**
   - Don't design schema first
   - List ALL access patterns
   - Then design keys/indexes

2. **Avoid Hot Partitions**
   - Distribute load evenly
   - Use sharding for celebrity users
   - Monitor partition metrics

3. **Minimize Scans**
   - Scans are expensive (read entire table)
   - Use Query with indexes instead
   - Scans OK for: Analytics, backups

4. **Use Composite Keys**
   - Enables range queries
   - Natural sorting (timestamps)
   - Hierarchical data

5. **Embrace Denormalization**
   - Store data multiple times if needed
   - Storage is cheap, queries are expensive
   - Example: Store user name in posts table

### Performance Metrics to Mention

- **Latency**: <10ms (p99)
- **Throughput**: 10+ trillion requests/day
- **Availability**: 99.99% SLA
- **Scalability**: Unlimited storage/throughput
- **Consistency**: Eventually consistent reads (default), strongly consistent available

### Real-World Examples to Reference

**Amazon.com Shopping Cart:**
```python
PK: user_id
SK: item_id
# Handles millions of concurrent users
# <10ms latency for add/remove items
```

**Netflix Viewing History:**
```python
PK: user_id
SK: timestamp
# Single-table design: user + profile + history
# Global tables for worldwide access
```

**Lyft Driver Location:**
```python
PK: driver_id
SK: timestamp
# 1 update per second per driver
# Millions of writes/second globally
```

---

## Practice Interview Question

**"Design DynamoDB schema for Instagram"**

**Requirements:**
1. Users can post photos
2. Users can follow other users
3. Users see feed of following's posts
4. Users can like/comment on posts

**Solution:**

**Table 1: Users & Posts (Single Table)**
```python
PK                  SK                  Data
------------------------------------------------------------------
USER#alice          PROFILE             {name, bio, follower_count}
USER#alice          POST#2024-01-15     {photo_url, caption, likes}
USER#alice          POST#2024-01-14     {photo_url, caption, likes}
USER#alice          FOLLOWING#bob       {followed_at}
USER#bob            PROFILE             {name, bio}
USER#bob            POST#2024-01-15     {photo_url, caption, likes}
```

**GSI: Posts by Time (Global Feed)**
```python
GSI_PK: "POST"
GSI_SK: timestamp
# Query recent posts globally
```

**Table 2: Likes**
```python
PK: post_id
SK: user_id
# Check if user liked post: O(1)
# Count likes: SCAN (cached separately)
```

**Table 3: Comments**
```python
PK: post_id
SK: timestamp
# Get comments sorted by time
```

**Feed Generation:**
```
1. Query USER#alice, SK begins_with FOLLOWING#
2. For each followed user, query their recent posts
3. Merge and sort by timestamp
4. Cache feed in Redis (TTL: 5 minutes)
```

---

Good luck with your DynamoDB interview! 🚀
