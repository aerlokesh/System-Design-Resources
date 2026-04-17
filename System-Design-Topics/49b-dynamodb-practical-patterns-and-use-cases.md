# 🎯 Topic 49b: DynamoDB Practical Patterns & Use Cases

> **DynamoDB Practitioner's Guide**
> Every practical use case, access pattern, condition expression, batch operation, error handling strategy, and operational gotcha you need to know — from someone who works with DynamoDB every day at Amazon.

---

## Table of Contents

- [🎯 Topic 49b: DynamoDB Practical Patterns \& Use Cases](#-topic-49b-dynamodb-practical-patterns--use-cases)
  - [Table of Contents](#table-of-contents)
  - [Practical Use Case Catalog](#practical-use-case-catalog)
    - [Use Case 1: User Profiles](#use-case-1-user-profiles)
    - [Use Case 2: Shopping Cart](#use-case-2-shopping-cart)
    - [Use Case 3: Order Management](#use-case-3-order-management)
    - [Use Case 4: Session Store](#use-case-4-session-store)
    - [Use Case 5: Chat Messages](#use-case-5-chat-messages)
    - [Use Case 6: Inventory Management](#use-case-6-inventory-management)
    - [Use Case 7: Rate Limiting](#use-case-7-rate-limiting)
    - [Use Case 8: Feature Flags](#use-case-8-feature-flags)
    - [Use Case 9: Leaderboard (Pre-Computed)](#use-case-9-leaderboard-pre-computed)
    - [Use Case 10: Notifications / Activity Feed](#use-case-10-notifications--activity-feed)
    - [Use Case 11: URL Shortener](#use-case-11-url-shortener)
    - [Use Case 12: IoT Sensor Data](#use-case-12-iot-sensor-data)
    - [Use Case 13: Distributed Lock](#use-case-13-distributed-lock)
    - [Use Case 14: Config Store (Service Configuration)](#use-case-14-config-store-service-configuration)
    - [Use Case 15: Multi-Tenant SaaS](#use-case-15-multi-tenant-saas)
    - [Use Case 16: Audit Log (Append-Only)](#use-case-16-audit-log-append-only)
    - [Use Case 17: Workflow / State Machine](#use-case-17-workflow--state-machine)
    - [Use Case 18: Graph-Like Relationships (Follows)](#use-case-18-graph-like-relationships-follows)
    - [Use Case 19: Event Scheduling (Calendar)](#use-case-19-event-scheduling-calendar)
    - [Use Case 20: API Key Management](#use-case-20-api-key-management)
  - [Condition Expressions — The Safety Net](#condition-expressions--the-safety-net)
    - [The Most Important DynamoDB Feature](#the-most-important-dynamodb-feature)
    - [Common Condition Patterns](#common-condition-patterns)
  - [Batch Operations and Their Gotchas](#batch-operations-and-their-gotchas)
    - [BatchWriteItem](#batchwriteitem)
    - [BatchGetItem](#batchgetitem)
    - [TransactWriteItems vs BatchWriteItem](#transactwriteitems-vs-batchwriteitem)
  - [Error Handling and Retries](#error-handling-and-retries)
    - [Common DynamoDB Errors](#common-dynamodb-errors)
    - [Retry Strategy](#retry-strategy)
  - [FilterExpression vs KeyConditionExpression](#filterexpression-vs-keyconditionexpression)
    - [The Critical Difference](#the-critical-difference)
  - [TTL Practical Applications](#ttl-practical-applications)
  - [Update Expressions — Atomic Operations](#update-expressions--atomic-operations)
  - [DynamoDB with Lambda Patterns](#dynamodb-with-lambda-patterns)
    - [DynamoDB Streams → Lambda](#dynamodb-streams--lambda)
    - [Lambda + DynamoDB Best Practices](#lambda--dynamodb-best-practices)
  - [Operational Gotchas and War Stories](#operational-gotchas-and-war-stories)
    - [Gotcha 1: Scan is Always Expensive](#gotcha-1-scan-is-always-expensive)
    - [Gotcha 2: Hot Partition During Events](#gotcha-2-hot-partition-during-events)
    - [Gotcha 3: GSI Lag](#gotcha-3-gsi-lag)
    - [Gotcha 4: Item Size Limit (400 KB)](#gotcha-4-item-size-limit-400-kb)
    - [Gotcha 5: Empty Strings Not Allowed (Until Recently)](#gotcha-5-empty-strings-not-allowed-until-recently)
  - [Cost Optimization Playbook](#cost-optimization-playbook)
  - [Quick Reference Card](#quick-reference-card)
  - [Related Topics](#related-topics)

---

## Practical Use Case Catalog

### Use Case 1: User Profiles

```
PK: USER#{user_id}
SK: PROFILE

{
  PK: "USER#user_123",
  SK: "PROFILE",
  name: "Alice",
  email: "alice@example.com",
  avatar_url: "s3://avatars/user_123.jpg",
  created_at: "2025-01-15T10:00:00Z",
  last_login: "2025-03-15T14:30:00Z",
  preferences: { theme: "dark", notifications: true }
}

Operations:
  GetItem(PK="USER#user_123", SK="PROFILE") → user profile
  UpdateItem: SET last_login = :now
  Condition: attribute_exists(PK) (don't create if user doesn't exist)
```

### Use Case 2: Shopping Cart

```
PK: CART#{user_id}
SK: ITEM#{product_id}

  { PK: "CART#user_123", SK: "ITEM#prod_456", quantity: 2, price: 29.99, added_at: "..." }
  { PK: "CART#user_123", SK: "ITEM#prod_789", quantity: 1, price: 49.99, added_at: "..." }

Operations:
  Add to cart: PutItem(PK, SK, quantity, price)
  Update quantity: UpdateItem SET quantity = :new_qty
  Remove item: DeleteItem(PK, SK)
  Get entire cart: Query(PK = "CART#user_123") → all items
  Clear cart (after checkout): BatchWriteItem delete all items for PK

Cart size: Typically < 50 items → single partition, fast queries.
TTL: Set expires_at = now + 30 days → abandoned carts auto-deleted.
```

### Use Case 3: Order Management

```
PK: USER#{user_id}
SK: ORDER#{order_date}#{order_id}

  { PK: "USER#u123", SK: "ORDER#2025-03-15#ord_789", 
    total: 99.99, status: "shipped", items: [...], 
    shipping_address: {...}, payment_method: "card_ending_4242" }

Queries:
  User's all orders: Query PK = "USER#u123", SK begins_with "ORDER#"
  User's orders this month: Query PK = "USER#u123", SK between "ORDER#2025-03-01" and "ORDER#2025-03-31"
  
GSI for order lookup by order_id:
  GSI1_PK: order_id
  GSI1_SK: (none needed)
  GetItem(GSI1_PK = "ord_789") → order details regardless of user
  
GSI for orders by status (admin view):
  GSI2_PK: order_status (e.g., "pending", "shipped")  
  GSI2_SK: order_date
  WARNING: Low cardinality PK → hot partition! Add shard suffix.
  GSI2_PK: "pending#3" (status + shard 0-9)
  Query all 10 shards → merge results
```

### Use Case 4: Session Store

```
PK: SESSION#{session_id}
TTL: expires_at (epoch seconds)

  { PK: "SESSION#sess_abc123", 
    user_id: "user_456", 
    created_at: "2025-03-15T14:00:00Z",
    last_activity: "2025-03-15T14:30:00Z",
    ip_address: "192.168.1.1",
    user_agent: "Chrome/120",
    expires_at: 1742234400  // TTL field
  }

Operations:
  Create: PutItem with TTL = now + 30 minutes
  Read (every request): GetItem(PK) → if not found, redirect to login
  Extend: UpdateItem SET last_activity = :now, expires_at = :new_expiry
  Invalidate: DeleteItem(PK) (logout)
  
  TTL auto-deletes expired sessions → no cleanup job needed!
  Free: TTL deletions don't consume WCU.
  
  With DAX: GetItem latency drops from 5ms to 0.5ms (microsecond reads).
```

### Use Case 5: Chat Messages

```
PK: CONVERSATION#{conversation_id}
SK: MSG#{timestamp}#{message_id}

  { PK: "CONVERSATION#conv_123", SK: "MSG#2025-03-15T14:30:05#msg_abc",
    sender_id: "user_456", body: "Hello!", type: "text" }

Queries:
  Latest 50 messages: Query PK, ScanIndexForward=false, Limit=50
  Messages after cursor: Query PK, SK > "MSG#{last_seen_timestamp}"
  
  Pagination: Use LastEvaluatedKey for cursor-based pagination
  
Scaling concern: Very active group chats → large partition (>10GB)
  Solution: Partition by time: PK = "CONVERSATION#conv_123#2025-03"
  Each month gets its own partition.
  Cross-month query: Query both partitions, merge client-side.
```

### Use Case 6: Inventory Management

```
PK: PRODUCT#{product_id}
SK: INVENTORY

  { PK: "PRODUCT#prod_456", SK: "INVENTORY",
    quantity: 150, reserved: 23, available: 127,
    warehouse: "WH-East", last_updated: "...", version: 42 }

Atomic decrement at checkout:
  UpdateItem(
    PK = "PRODUCT#prod_456", SK = "INVENTORY",
    UpdateExpression = "SET available = available - :qty, reserved = reserved + :qty, version = version + 1",
    ConditionExpression = "available >= :qty AND version = :expected_version"
  )
  
  If available < qty → ConditionCheckFailedException → "Out of stock!"
  If version mismatch → ConditionCheckFailedException → Optimistic lock conflict → retry.
  
  CRITICAL: This is the pattern for preventing overselling.
  The condition expression makes this atomic and safe.
```

### Use Case 7: Rate Limiting

```
PK: RATE#{api_key}#{window}
SK: (none - simple key)
TTL: expires_at

  { PK: "RATE#api_key_abc#2025-03-15T14:30",
    count: 47,
    expires_at: 1742234460  // TTL: 60 seconds after window start
  }

Check and increment:
  UpdateItem(
    PK = "RATE#api_key_abc#2025-03-15T14:30",
    UpdateExpression = "ADD #count :one",
    ConditionExpression = "attribute_not_exists(#count) OR #count < :limit",
    ExpressionAttributeNames = {"#count": "count"},
    ExpressionAttributeValues = {":one": 1, ":limit": 100}
  )
  
  If count >= 100 → ConditionCheckFailedException → 429 Too Many Requests
  New window (key doesn't exist) → ADD creates with value 1
  TTL auto-deletes old windows → no cleanup needed.
```

### Use Case 8: Feature Flags

```
PK: FLAG#{flag_name}
SK: (none)

  { PK: "FLAG#dark_mode", enabled: true, percentage: 50, 
    whitelist_users: ["user_123", "user_456"],
    created_by: "engineer_789", updated_at: "..." }

Read all flags (on service startup):
  Scan table → cache in memory → refresh every 30 seconds
  Or: Query with GSI PK = "FLAG" (if using single-table design)

Update flag:
  UpdateItem SET enabled = :val, percentage = :pct
  DynamoDB Streams → Lambda → invalidate all service caches
  
  Cost: Tiny table (< 1000 items) → minimal RCU/WCU needed.
```

### Use Case 9: Leaderboard (Pre-Computed)

```
PK: LEADERBOARD#{game_id}#{period}
SK: RANK#{zero_padded_rank}

  { PK: "LEADERBOARD#game_1#2025-03-15", SK: "RANK#000001",
    user_id: "user_789", score: 99500, username: "ProGamer99" }
  { PK: "LEADERBOARD#game_1#2025-03-15", SK: "RANK#000002",
    user_id: "user_456", score: 98200, username: "SpeedRunner" }

Top 100: Query PK, Limit=100 → instant (items are pre-sorted by rank)
User's neighborhood: Use GSI with user_id as PK to find their rank, then Query ±10.

Pre-computed by:
  Batch job (Spark/Lambda) computes rankings → BatchWriteItem to DDB
  Updated every 5-15 minutes (not real-time, but cheap and fast reads)
  
For real-time leaderboard: Use Redis sorted sets (ZADD/ZREVRANGE)
  DDB for persistence, Redis for real-time ranking.
```

### Use Case 10: Notifications / Activity Feed

```
PK: USER#{user_id}
SK: NOTIF#{timestamp}#{notification_id}

  { PK: "USER#u123", SK: "NOTIF#2025-03-15T14:30#notif_abc",
    type: "order_shipped", title: "Your order is on the way!",
    body: "Order #789 shipped via UPS", is_read: false,
    action_url: "/orders/789", TTL: 1745000000 }

Latest notifications: Query PK, SK begins_with "NOTIF#", ScanIndexForward=false, Limit=20
Unread count: Query + FilterExpression is_read = false
  (WARNING: FilterExpression still reads all items → use a separate counter)
  Better: Maintain unread_count as attribute on USER#PROFILE item
    UpdateItem ADD unread_count :one (on new notification)
    UpdateItem SET unread_count = unread_count - :one (on mark read)

TTL: 30 days → old notifications auto-deleted.
```

### Use Case 11: URL Shortener

```
PK: SHORT#{short_code}
SK: (none)

  { PK: "SHORT#abc123", long_url: "https://example.com/very/long/path",
    created_by: "user_789", created_at: "...", click_count: 0,
    expires_at: 1745000000 }

Redirect: GetItem(PK = "SHORT#abc123") → 301 redirect to long_url
  + UpdateItem ADD click_count :one (async, don't block redirect)

Create: PutItem with ConditionExpression = attribute_not_exists(PK)
  (Ensures short code is unique — if collision, generate new code)

With DAX: GetItem in microseconds → sub-ms redirect latency.
```

### Use Case 12: IoT Sensor Data

```
PK: DEVICE#{device_id}
SK: DATA#{timestamp}

  { PK: "DEVICE#sensor_42", SK: "DATA#2025-03-15T14:30:05",
    temperature: 23.5, humidity: 65, battery: 87 }

Recent readings: Query PK, SK > "DATA#2025-03-15T14:00", ScanIndexForward=false
Last reading: Query PK, ScanIndexForward=false, Limit=1

Partition per device: Perfect distribution (millions of devices)
TTL: Keep 30 days of data → older data archived to S3 via Streams.

High-volume ingestion:
  Batch writes: BatchWriteItem (25 items per batch)
  Or: Kinesis Data Streams → Lambda → BatchWriteItem (buffer + batch)
```

### Use Case 13: Distributed Lock

```
PK: LOCK#{resource_id}
SK: (none)
TTL: expires_at

Acquire:
  PutItem(
    PK = "LOCK#order_789",
    owner = "worker_5",
    acquired_at = "2025-03-15T14:30:00Z",
    expires_at = now + 30,  // TTL
    ConditionExpression = "attribute_not_exists(PK) OR expires_at < :now"
  )
  
  If succeeds → lock acquired ✅
  If ConditionCheckFailedException → lock held by someone else ❌

Release:
  DeleteItem(
    PK = "LOCK#order_789",
    ConditionExpression = "owner = :my_id"  // Only release MY lock
  )

TTL safety net: If worker crashes → lock auto-expires after 30 seconds.
NOTE: Not as strong as ZooKeeper locks (see Topic 32), but sufficient
  for non-critical coordination.
```

### Use Case 14: Config Store (Service Configuration)

```
PK: CONFIG#{service_name}
SK: KEY#{config_key}

  { PK: "CONFIG#payment-service", SK: "KEY#max_retry_count", value: "3" }
  { PK: "CONFIG#payment-service", SK: "KEY#timeout_ms", value: "5000" }
  { PK: "CONFIG#payment-service", SK: "KEY#feature_new_checkout", value: "true" }

Load all config: Query PK = "CONFIG#payment-service" → all config keys
  Cache in-memory, refresh every 60 seconds.

Update config:
  UpdateItem SET value = :new_value
  DynamoDB Streams → Lambda → push invalidation to all service instances
```

### Use Case 15: Multi-Tenant SaaS

```
PK: TENANT#{tenant_id}
SK: entity-specific (USER#, ORDER#, SETTING#, etc.)

  { PK: "TENANT#acme", SK: "USER#user_1", name: "Alice", role: "admin" }
  { PK: "TENANT#acme", SK: "USER#user_2", name: "Bob", role: "member" }
  { PK: "TENANT#acme", SK: "ORDER#2025-03-15#ord_1", total: 500.00 }
  { PK: "TENANT#acme", SK: "SETTING#billing", plan: "enterprise" }

Tenant isolation: All data for one tenant under one PK
  Query PK = "TENANT#acme" → all tenant data
  Query PK = "TENANT#acme", SK begins_with "USER#" → all users
  Query PK = "TENANT#acme", SK begins_with "ORDER#" → all orders

Security: IAM policy restricts each tenant to their own PK.
  Condition: {"dynamodb:LeadingKeys": ["TENANT#${tenant_id}"]}
```

### Use Case 16: Audit Log (Append-Only)

```
PK: AUDIT#{entity_type}#{entity_id}
SK: EVENT#{timestamp}#{event_id}

  { PK: "AUDIT#ORDER#ord_789", SK: "EVENT#2025-03-15T14:30#evt_1",
    action: "created", actor: "user_123", changes: {...} }
  { PK: "AUDIT#ORDER#ord_789", SK: "EVENT#2025-03-15T15:00#evt_2",
    action: "status_changed", actor: "system", 
    old_value: "pending", new_value: "shipped" }

Query audit trail: Query PK = "AUDIT#ORDER#ord_789" → full history, time-sorted
Never update or delete: Append-only (PutItem only, no UpdateItem/DeleteItem)
TTL: Keep 90 days in DDB → archive to S3 via Streams for long-term retention.
```

### Use Case 17: Workflow / State Machine

```
PK: WORKFLOW#{workflow_id}
SK: STATE

  { PK: "WORKFLOW#wf_123", SK: "STATE",
    current_state: "payment_pending",
    order_id: "ord_789",
    transitions: [
      { from: "created", to: "payment_pending", at: "T1", by: "system" },
      { from: "payment_pending", to: "paid", at: "T2", by: "payment_service" }
    ],
    version: 3 }

State transition (atomic):
  UpdateItem(
    SET current_state = :new_state, 
        version = version + 1,
        transitions = list_append(transitions, :new_transition)
    CONDITION current_state = :expected_state AND version = :expected_version
  )
  
  If current_state != expected → transition rejected (stale state)
  Optimistic locking prevents concurrent state corruption.
```

### Use Case 18: Graph-Like Relationships (Follows)

```
PK: USER#{user_id}
SK: FOLLOWS#{followed_user_id}

  { PK: "USER#alice", SK: "FOLLOWS#bob" }
  { PK: "USER#alice", SK: "FOLLOWS#charlie" }

GSI (inverted):
  GSI_PK: USER#{followed_user_id}  (who follows me?)
  GSI_SK: FOLLOWS#{follower_user_id}

Queries:
  "Who does Alice follow?" → Query PK = "USER#alice", SK begins_with "FOLLOWS#"
  "Who follows Bob?" → Query GSI PK = "USER#bob", SK begins_with "FOLLOWS#"
  "Does Alice follow Bob?" → GetItem(PK = "USER#alice", SK = "FOLLOWS#bob")
  "Follow count" → Store as counter on USER#{id}#PROFILE item
```

### Use Case 19: Event Scheduling (Calendar)

```
PK: CALENDAR#{user_id}
SK: EVENT#{start_time}#{event_id}

  { PK: "CALENDAR#u123", SK: "EVENT#2025-03-15T14:00#evt_abc",
    title: "Team Standup", end_time: "2025-03-15T14:30",
    location: "Zoom", attendees: ["u456", "u789"] }

Today's events: Query PK, SK between "EVENT#2025-03-15T00:00" and "EVENT#2025-03-15T23:59"
This week's events: Query PK, SK between "EVENT#2025-03-10" and "EVENT#2025-03-16"
Next event: Query PK, SK > "EVENT#{now}", ScanIndexForward=true, Limit=1
```

### Use Case 20: API Key Management

```
PK: APIKEY#{api_key_hash}  (hash the key for security!)
SK: (none)

  { PK: "APIKEY#sha256_abc123", 
    owner_id: "user_789",
    permissions: ["read", "write"],
    rate_limit: 1000,
    created_at: "...",
    last_used: "...",
    is_active: true }

Validate API key: GetItem(PK = "APIKEY#" + sha256(request.api_key))
  If found AND is_active → authorized
  If not found → 401 Unauthorized

With DAX: Sub-ms key validation (every API request checks this)
```

---

## Condition Expressions — The Safety Net

### The Most Important DynamoDB Feature

```
Condition expressions prevent race conditions without locks:

Create only if not exists (prevent duplicates):
  PutItem(ConditionExpression = "attribute_not_exists(PK)")

Update only if version matches (optimistic locking):
  UpdateItem(ConditionExpression = "version = :expected")

Decrement only if sufficient (prevent negative values):
  UpdateItem(
    SET quantity = quantity - :amount
    CONDITION quantity >= :amount
  )

Delete only if owner matches (prevent deleting others' data):
  DeleteItem(ConditionExpression = "owner_id = :my_id")

Update only if status allows transition:
  UpdateItem(
    SET status = :new_status
    CONDITION status IN (:allowed_statuses)
  )
  
  e.g., Can only cancel if status is "pending" or "confirmed", not "shipped"

ALL of these are ATOMIC — checked and applied in one operation.
No need for distributed locks. No race conditions. This is DDB's superpower.
```

### Common Condition Patterns

```
// Prevent overwriting existing item
"attribute_not_exists(PK)"

// Optimistic locking
"version = :expected_version"

// Atomic counter with ceiling
"#count < :max_limit"

// Only update if item was created by this user
"created_by = :user_id"

// Allow transition only from specific states
"#status IN (:s1, :s2, :s3)"

// Ensure item exists before updating
"attribute_exists(PK)"

// Composite condition
"attribute_exists(PK) AND version = :v AND #status <> :deleted"
```

---

## Batch Operations and Their Gotchas

### BatchWriteItem

```
Write up to 25 items in one request:

  response = dynamodb.batch_write_item(
    RequestItems={
      'Orders': [
        {'PutRequest': {'Item': item1}},
        {'PutRequest': {'Item': item2}},
        {'DeleteRequest': {'Key': {'PK': 'x', 'SK': 'y'}}},
      ]
    }
  )

GOTCHAS:
  1. NO condition expressions! Can't do conditional batch writes.
     If you need conditions → use TransactWriteItems instead.
  
  2. Partial failures: Some items may fail → check UnprocessedItems!
     unprocessed = response.get('UnprocessedItems', {})
     if unprocessed:
         # Retry with exponential backoff
         dynamodb.batch_write_item(RequestItems=unprocessed)
  
  3. Max 25 items per batch, max 16 MB total.
  
  4. Items in same batch CAN'T have the same key (no duplicate operations).
  
  5. All items count toward table's WCU (25 items × 1 WCU each = 25 WCU consumed).
```

### BatchGetItem

```
Read up to 100 items in one request:

  response = dynamodb.batch_get_item(
    RequestItems={
      'Users': {
        'Keys': [
          {'PK': 'USER#u1', 'SK': 'PROFILE'},
          {'PK': 'USER#u2', 'SK': 'PROFILE'},
          ...
        ],
        'ProjectionExpression': 'PK, #name, email',
        'ConsistentRead': False  // Eventually consistent (cheaper)
      }
    }
  )

GOTCHAS:
  1. Max 100 items per batch, max 16 MB total response.
  2. Partial failures: Check UnprocessedKeys and retry.
  3. No ordering guarantee: Items may return in any order.
  4. Each item consumes RCU independently (100 items = 100 × 0.5 RCU eventual).
```

### TransactWriteItems vs BatchWriteItem

```
BatchWriteItem:
  ✅ Faster (no coordination overhead)
  ✅ Up to 25 items
  ❌ No conditions
  ❌ Partial failures possible
  Use: Bulk data loading, non-critical writes

TransactWriteItems:
  ✅ All-or-nothing (atomic)
  ✅ Condition expressions on each item
  ❌ Slower (~2x cost)
  ❌ Up to 100 items (but 2x WCU each)
  Use: Business logic requiring atomicity (order + inventory + user stats)
```

---

## Error Handling and Retries

### Common DynamoDB Errors

```
ProvisionedThroughputExceededException:
  Cause: Exceeded provisioned RCU/WCU (or partition limit)
  Fix: Increase capacity, use on-demand, or add retry with backoff
  SDK: Auto-retries with exponential backoff (configure max retries)

ConditionalCheckFailedException:
  Cause: Condition expression evaluated to false
  Fix: NOT a retry scenario — the condition was legitimately false
  Application: Handle in business logic (e.g., "item already exists", "out of stock")

ValidationException:
  Cause: Invalid request (bad expression, wrong types)
  Fix: Fix the code — this is a bug, not a transient error. Don't retry.

ItemCollectionSizeLimitExceededException:
  Cause: Item collection (same PK) exceeds 10 GB (only with LSI)
  Fix: Redesign key to distribute data better

ResourceNotFoundException:
  Cause: Table doesn't exist
  Fix: Check table name, region, account. Usually a config error.

ThrottlingException:
  Cause: Control plane operation rate limit (CreateTable, UpdateTable)
  Fix: Rare in normal operation. Retry with backoff.
```

### Retry Strategy

```python
import boto3
from botocore.config import Config

# Configure SDK retries
config = Config(
    retries={
        'max_attempts': 5,          # Up to 5 retries
        'mode': 'adaptive'          # Adaptive retry mode (recommended)
    },
    read_timeout=5,
    connect_timeout=2
)

dynamodb = boto3.resource('dynamodb', config=config)

# Application-level retry for conditional failures
def atomic_increment(table, pk, sk, field, amount, max_retries=3):
    for attempt in range(max_retries):
        item = table.get_item(Key={'PK': pk, 'SK': sk})['Item']
        try:
            table.update_item(
                Key={'PK': pk, 'SK': sk},
                UpdateExpression=f'SET {field} = :new_val, version = :new_ver',
                ConditionExpression='version = :expected_ver',
                ExpressionAttributeValues={
                    ':new_val': item[field] + amount,
                    ':new_ver': item['version'] + 1,
                    ':expected_ver': item['version']
                }
            )
            return  # Success
        except ClientError as e:
            if e.response['Error']['Code'] == 'ConditionalCheckFailedException':
                continue  # Retry — someone else updated
            raise  # Other error — don't retry
    raise MaxRetriesExceeded()
```

---

## FilterExpression vs KeyConditionExpression

### The Critical Difference

```
KeyConditionExpression: Filters BEFORE reading from disk. Efficient. Uses index.
FilterExpression: Filters AFTER reading from disk. Expensive. Reads everything.

Example: "Get all orders for user_123 with status = 'shipped'"

GOOD (KeyCondition + Filter):
  Query(
    KeyConditionExpression = "PK = :pk AND SK begins_with :prefix",
    FilterExpression = "order_status = :status"
  )
  
  DynamoDB reads ALL orders for user → filters for "shipped" → returns matches.
  If user has 100 orders, 5 shipped: DDB reads 100, returns 5.
  YOU PAY FOR 100 reads, not 5!

BETTER (design SK to include status):
  SK = "ORDER#shipped#2025-03-15#ord_789"
  Query(KeyConditionExpression = "PK = :pk AND SK begins_with :prefix")
  where prefix = "ORDER#shipped#"
  
  DDB reads ONLY shipped orders → returns all.
  100 orders, 5 shipped: DDB reads 5, returns 5. 20x cheaper!

EVEN BETTER (GSI):
  GSI PK = user_id, GSI SK = status#date
  Query(KeyConditionExpression = "user_id = :uid AND begins_with(status_date, :status)")
  
  Efficient: Only reads matching items.

RULE: If you're using FilterExpression extensively, your key design is wrong.
  Redesign keys or add a GSI to make the filter a KeyCondition.
```

---

## TTL Practical Applications

```
TTL = Time To Live: DynamoDB automatically deletes items after expiry.

Setting TTL:
  Table setting: Enable TTL on a specific attribute (e.g., "expires_at")
  Item: Set expires_at = Unix epoch timestamp when item should expire
  
  Example: { PK: "SESSION#abc", expires_at: 1742234400 }
  At 1742234400 (Unix time) → DynamoDB deletes this item

IMPORTANT GOTCHAS:
  1. Deletion is NOT instant — may take up to 48 hours after expiry!
     Items may still appear in queries for hours after TTL.
     Application MUST check: if item.expires_at < now() → treat as deleted.
     
  2. TTL deletes are FREE — no WCU consumed.
  
  3. TTL deletes DO appear in DynamoDB Streams → can trigger Lambda.
     Filter: eventName = "REMOVE" AND userIdentity.type = "Service" (TTL deletion)
     vs user-initiated delete: userIdentity.type != "Service"
     
  4. TTL attribute MUST be a Number (epoch seconds, not ISO string).

Practical uses:
  Sessions: 30-minute TTL (refreshed on activity)
  Cache entries: 5-minute to 24-hour TTL
  Rate limit windows: 60-second TTL
  Temporary tokens: 24-hour TTL (OTP, reset tokens)
  Old notifications: 30-day TTL
  Audit logs: 90-day TTL (archived to S3 via Streams before deletion)
```

---

## Update Expressions — Atomic Operations

```
SET: Set attribute values
  SET #name = :new_name, email = :new_email
  SET price = if_not_exists(price, :default) + :increase  // Set with default

ADD: Atomic increment (numbers) or add to set
  ADD view_count :one  // Atomic increment (+1)
  ADD tags :new_tags   // Add elements to a set

REMOVE: Delete attributes
  REMOVE temporary_field, debug_data

DELETE: Remove elements from a set
  DELETE tags :tags_to_remove

LIST_APPEND: Add to list
  SET comments = list_append(comments, :new_comment)   // Append
  SET comments = list_append(:new_comment, comments)   // Prepend

Combining multiple operations in one UpdateItem:
  UpdateItem(
    UpdateExpression = "SET #name = :name, updated_at = :now 
                        ADD login_count :one 
                        REMOVE temp_flag",
    ConditionExpression = "attribute_exists(PK)"
  )
  
  One atomic operation: Updates name, sets timestamp, increments counter, removes flag.
  ALL happen atomically. No partial updates.
```

---

## DynamoDB with Lambda Patterns

### DynamoDB Streams → Lambda

```
The most common serverless pattern at Amazon:

Table change → Stream record → Lambda function invoked

Use cases:
  1. Search indexing: Item updated → Lambda → Elasticsearch PUT
  2. Cache invalidation: Item updated → Lambda → Redis DEL
  3. Notifications: Order status changed → Lambda → SNS → Push notification
  4. Aggregation: Order created → Lambda → increment daily_order_count
  5. Replication: Item changed → Lambda → write to another DDB table
  6. Archival: Item TTL-deleted → Lambda → write to S3 for long-term storage

Lambda configuration:
  Event source: DynamoDB Stream
  Batch size: 100 (process up to 100 stream records per invocation)
  Starting position: LATEST (or TRIM_HORIZON for full replay)
  Parallelization: 1-10 per shard
  Error handling: On failure → retry entire batch → DLQ after N retries

GOTCHA: Stream records are processed in ORDER within a shard (partition key).
  Records for different PKs may be processed out of order (different shards).
  For cross-PK ordering: Single-shard processing (limits parallelism).
```

### Lambda + DynamoDB Best Practices

```
1. Minimize cold starts: Keep Lambda warm with provisioned concurrency
   DDB read in cold Lambda: ~100ms first call, ~5ms subsequent

2. Use DynamoDB DocumentClient (not low-level client):
   Simpler API, automatic type marshaling

3. Connection reuse: Declare DDB client OUTSIDE handler
   const ddb = new DynamoDB.DocumentClient();  // Reused across invocations
   exports.handler = async (event) => { ... }

4. Batch operations for bulk processing:
   Stream batch of 100 records → BatchWriteItem instead of 100 PutItems
   
5. Idempotency: Streams may deliver records more than once
   Use eventID as idempotency key → deduplicate in processing
```

---

## Operational Gotchas and War Stories

### Gotcha 1: Scan is Always Expensive

```
Scan reads EVERY item in the table (or index), then filters.
  10M items × 1KB = 10 GB → ~2.5M RCU consumed → takes minutes, costs $$

NEVER use Scan in production request path.
Only acceptable for: One-time scripts, admin tools, data migration.
Always use Query with proper key design instead.
```

### Gotcha 2: Hot Partition During Events

```
Black Friday: Product "deal_of_the_day" gets 100K reads/sec
  All reads to PK = "PRODUCT#deal_123" → ONE partition → 3K RCU max → THROTTLED!

Fix: DAX (absorbs repeated reads), or key sharding, or cache in application.
```

### Gotcha 3: GSI Lag

```
Base table write → GSI update is ASYNC (typically < 1 second lag)
  Write item to base table → immediately query GSI → item NOT THERE yet!
  
  If consistency matters: Read from base table, not GSI.
  GSI is eventually consistent ONLY. No strong consistency option.
```

### Gotcha 4: Item Size Limit (400 KB)

```
Item > 400 KB → ValidationException!

  User profile with 10,000 follower IDs embedded → easily > 400 KB
  
  Fix: Store large data in S3, reference URL in DDB item.
  Or: Split into multiple items (FOLLOWERS#chunk_1, FOLLOWERS#chunk_2)
```

### Gotcha 5: Empty Strings Not Allowed (Until Recently)

```
Old behavior: Empty string "" → error!
  { name: "" } → ValidationException
  
New behavior (2020+): Empty strings are now supported.
  But legacy code may still have workarounds (storing " " or null).
  Be aware when working with older tables.
```

---

## Cost Optimization Playbook

```
1. Eventually consistent reads everywhere possible: 50% savings
2. On-demand → Provisioned after traffic stabilizes: 60-75% savings  
3. Use ProjectionExpression (read only needed attributes): 30-50% savings
4. TTL instead of DeleteItem for expiring data: Free deletes
5. Minimize GSIs (each GSI = 1 extra WCU per write): 25-50% savings per GSI removed
6. Compress large attributes (gzip JSON before storing): 50-80% less storage
7. Use KEYS_ONLY projection on GSIs: 70% less GSI storage
8. BatchWriteItem instead of individual PutItems: 50% fewer API calls
9. DAX for read-heavy hot data: Reduce RCU by 90%+
10. Reserved capacity for baseline: 25-50% discount (1-3 year commitment)

Real example:
  Before: On-demand, 3 GSIs, ALL projection, no DAX = $5,000/month
  After: Provisioned + auto-scale, 1 GSI, KEYS_ONLY, DAX, TTL = $1,200/month
  Savings: 76%
```

---

## Quick Reference Card

```
┌──────────────────────────────────────────────────────────────┐
│            DYNAMODB PRACTICAL QUICK REFERENCE                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  LIMITS:                                                     │
│    Item size: 400 KB max                                     │
│    Partition: 10 GB storage, 3K RCU, 1K WCU                 │
│    BatchWrite: 25 items max, 16 MB max                       │
│    BatchGet: 100 items max, 16 MB response                   │
│    Transaction: 100 items max, 4 MB max                      │
│    GSIs: 20 per table max                                    │
│    LSIs: 5 per table max (create at table creation only!)    │
│    Query/Scan result: 1 MB max per call (paginate)           │
│                                                              │
│  COSTS (us-east-1, on-demand):                               │
│    Write: $1.25 per million WRU                              │
│    Read (eventual): $0.25 per million RRU                    │
│    Read (strong): $0.50 per million RRU (2x eventual)        │
│    Storage: $0.25 per GB per month                           │
│    Transaction: 2x normal read/write cost                    │
│    Streams: $0.02 per 100K read requests                     │
│    TTL deletes: FREE                                         │
│    GSI writes: Same as base table writes (per GSI)           │
│                                                              │
│  CONDITION EXPRESSIONS (prevent race conditions):            │
│    attribute_not_exists(PK) → create only if new             │
│    version = :expected → optimistic locking                  │
│    quantity >= :amount → prevent negative values              │
│    #status IN (:s1, :s2) → allowed state transitions         │
│                                                              │
│  COMMON PATTERNS:                                            │
│    Single-table: All entities in one table, SK prefix types  │
│    Sparse GSI: Only items WITH the attribute are indexed     │
│    Write sharding: PK + random suffix for hot items          │
│    TTL: Auto-delete sessions, rate limits, temp data         │
│    Streams + Lambda: CDC for search, cache, notifications    │
│    Optimistic locking: version attribute + condition expr    │
│                                                              │
│  ANTI-PATTERNS:                                              │
│    ❌ Scan in production (reads entire table)                │
│    ❌ FilterExpression instead of proper key design           │
│    ❌ Low-cardinality PK (status, boolean, date)             │
│    ❌ Large items (>100KB — use S3 for blobs)                │
│    ❌ Too many GSIs (each adds write cost)                   │
│    ❌ Missing TTL on ephemeral data (memory leak)            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 49: DynamoDB Deep Dive** — Architecture, replication, Global Tables, DAX
- **Topic 40: Redis Deep Dive** — Redis + DynamoDB caching patterns
- **Topic 12: Sharding & Partitioning** — DynamoDB partition management
- **Topic 31: Hot Partitions** — Hot key handling in DynamoDB
- **Topic 44: Data Migration** — CDC-based DynamoDB migrations

---

*This document is part of the System Design Interview Deep Dive series.*
