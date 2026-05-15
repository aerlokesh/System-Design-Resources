# 🟠 DynamoDB Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how DynamoDB is used in production at companies like Amazon, Lyft, Nike, Duolingo, Capital One, and more. Every table design, access pattern, and key schema decision is explained with the **WHY** behind it.

---

## 📋 Table of Contents

1. [Amazon — Shopping Cart (The Original Use Case)](#1-amazon--shopping-cart-the-original-use-case)
2. [Amazon — Order Management & History](#2-amazon--order-management--history)
3. [Lyft — Ride Matching & Trip History](#3-lyft--ride-matching--trip-history)
4. [Duolingo — User Progress & Streaks](#4-duolingo--user-progress--streaks)
5. [Nike — SNKRS Flash Sale & Inventory](#5-nike--snkrs-flash-sale--inventory)
6. [Capital One — Account Transactions](#6-capital-one--account-transactions)
7. [Twitch — Chat Messages](#7-twitch--chat-messages)
8. [Tinder — User Swipes & Match Tracking](#8-tinder--user-swipes--match-tracking)
9. [Uber Eats — Restaurant Menu & Order Tracking](#9-uber-eats--restaurant-menu--order-tracking)
10. [Snapchat — Ephemeral Messages with TTL](#10-snapchat--ephemeral-messages-with-ttl)
11. [Netflix — User Viewing History](#11-netflix--user-viewing-history)
12. [Slack — Notification Preferences](#12-slack--notification-preferences)
13. [DoorDash — Real-Time Delivery Tracking](#13-doordash--real-time-delivery-tracking)
14. [Airbnb — Booking & Availability](#14-airbnb--booking--availability)
15. [Discord — Server & Channel Membership](#15-discord--server--channel-membership)
16. [16–25: Additional Real-World Applications (Summary)](#1625-additional-real-world-applications-summary)
17. [🏆 Cheat Sheet: DynamoDB Table Design Decision Guide](#-cheat-sheet-dynamodb-table-design-decision-guide)
18. [🎯 Interview Summary: "When Would You Use DynamoDB?"](#-interview-summary-when-would-you-use-dynamodb)

---

## 1. Amazon — Shopping Cart (The Original Use Case)

### 🏗️ Architecture Context

Amazon literally built DynamoDB for this. The Dynamo paper (2007) described the need for an always-available, highly scalable key-value store for shopping carts. Millions of concurrent users, zero tolerance for "your cart is unavailable."

### 📐 Table Design

```
Table: ShoppingCart
┌─────────────────────────────────────────────────────────────────────┐
│ PK (userId)     │ SK (productId)     │ quantity │ addedAt    │ price │
├─────────────────┼────────────────────┼──────────┼────────────┼───────┤
│ user_123        │ PROD#asin_ABC      │ 2        │ 2024-01-15 │ 29.99 │
│ user_123        │ PROD#asin_DEF      │ 1        │ 2024-01-15 │ 49.99 │
│ user_456        │ PROD#asin_ABC      │ 3        │ 2024-01-14 │ 29.99 │
└─────────────────┴────────────────────┴──────────┴────────────┴───────┘
```

### 💡 Why This Design?

- **PK = userId**: All cart items for one user in one partition → single Query retrieves entire cart
- **SK = productId**: Each item is a separate row → can update quantity without reading entire cart
- **No GSI needed**: Only access pattern is "get cart for user" and "add/remove item"
- **TTL**: Set TTL on abandoned carts (e.g., 30 days) to auto-expire

### 🔧 Operations — Step by Step

```
# Add item to cart (or update quantity atomically)
UpdateItem:
  Key: { PK: "user_123", SK: "PROD#asin_ABC" }
  UpdateExpression: "SET quantity = quantity + :qty, price = :price, addedAt = :now"
  ExpressionAttributeValues: { ":qty": 1, ":price": 29.99, ":now": "2024-01-15" }

# Get entire cart
Query:
  KeyConditionExpression: "PK = :userId"
  ExpressionAttributeValues: { ":userId": "user_123" }

# Remove item
DeleteItem:
  Key: { PK: "user_123", SK: "PROD#asin_ABC" }

# Conditional: Don't let quantity go negative
UpdateItem:
  Key: { PK: "user_123", SK: "PROD#asin_ABC" }
  UpdateExpression: "SET quantity = quantity - :one"
  ConditionExpression: "quantity > :zero"
```

### 🎯 Why DynamoDB Over Others?

| Alternative | Problem |
|-------------|---------|
| Redis | Cart lost on eviction/restart; no durability guarantee |
| PostgreSQL | Doesn't scale to millions of concurrent cart updates |
| MongoDB | Possible, but DynamoDB's key-value model is simpler and scales linearly |

---

## 2. Amazon — Order Management & History

### 🏗️ Architecture Context

Every Amazon order — from creation to delivery — lives in DynamoDB. Need to query by orderId, by userId (order history), by date range, and by status.

### 📐 Table Design (Single-Table)

```
Table: Orders
┌──────────────────┬─────────────────────────┬───────────┬────────────┬──────────┐
│ PK               │ SK                      │ status    │ total      │ GSI1PK   │
├──────────────────┼─────────────────────────┼───────────┼────────────┼──────────┤
│ USER#user_123    │ ORDER#2024-01-15#ord_A  │ DELIVERED │ 79.98      │ ord_A    │
│ USER#user_123    │ ORDER#2024-01-10#ord_B  │ SHIPPED   │ 149.99     │ ord_B    │
│ USER#user_123    │ ITEM#ord_A#asin_ABC     │ —         │ 29.99      │ —        │
│ USER#user_123    │ ITEM#ord_A#asin_DEF     │ —         │ 49.99      │ —        │
└──────────────────┴─────────────────────────┴───────────┴────────────┴──────────┘

GSI1: PK = orderId → Lookup order by orderId directly
GSI2: PK = userId, SK = status#date → Filter orders by status
```

### 💡 Why Single-Table Design?

- **One Query per access pattern** — no joins needed
- Orders AND items in same partition → get full order in one Query
- SK begins_with("ORDER#") → get order list; begins_with("ITEM#ord_A") → get order items
- Date-prefixed SK → natural time-ordered pagination

### 🔧 Access Patterns

```
# Get user's recent orders (last 30 days)
Query:
  PK = "USER#user_123"
  SK begins_with "ORDER#2024-01"
  ScanIndexForward: false  (newest first)

# Get order by orderId (via GSI1)
Query on GSI1:
  PK = "ord_A"

# Get all items for an order
Query:
  PK = "USER#user_123"
  SK begins_with "ITEM#ord_A"

# Update order status (with optimistic locking)
UpdateItem:
  Key: { PK: "USER#user_123", SK: "ORDER#2024-01-15#ord_A" }
  UpdateExpression: "SET #status = :new"
  ConditionExpression: "#status = :expected"
  ExpressionAttributeValues: { ":new": "SHIPPED", ":expected": "PROCESSING" }
```

---

## 3. Lyft — Ride Matching & Trip History

### 🏗️ Architecture Context

Lyft uses DynamoDB to store trip data, driver status, and ride matching state. Needs sub-10ms reads for real-time matching and months of history for analytics.

### 📐 Table Design

```
Table: Trips
PK: RIDER#rider_123        SK: TRIP#2024-01-15T14:30:00#trip_abc
PK: DRIVER#driver_456      SK: TRIP#2024-01-15T14:30:00#trip_abc
PK: TRIP#trip_abc           SK: META                        (trip details)
PK: TRIP#trip_abc           SK: ROUTE#1, ROUTE#2, ...       (GPS waypoints)

Table: DriverStatus
PK: driver_456
Attributes: status (AVAILABLE|ON_TRIP|OFFLINE), lastLocation, lastUpdated
TTL: lastUpdated + 5 minutes (offline if no heartbeat)
```

### 💡 Why DynamoDB?

- **Consistent single-digit ms latency** at any scale — critical for ride matching
- **TTL auto-expires** driver status if no heartbeat → driver goes offline automatically
- **DynamoDB Streams** → trigger Lambda to update rider's trip status in real-time
- Trip data is write-heavy (GPS updates every 5s) → DynamoDB handles write spikes effortlessly

### 🔧 Key Operations

```
# Driver heartbeat (update location + extend TTL)
UpdateItem:
  Key: { PK: "driver_456" }
  UpdateExpression: "SET #loc = :loc, lastUpdated = :now, #ttl = :expiry"
  
# Get rider's trip history (newest first)
Query:
  PK = "RIDER#rider_123", SK begins_with "TRIP#2024"
  ScanIndexForward: false
  Limit: 20

# Find available drivers (GSI on status)
Query on GSI:
  PK = "AVAILABLE"
  FilterExpression: "contains(serviceArea, :area)"
```

---

## 4. Duolingo — User Progress & Streaks

### 🏗️ Architecture Context

Duolingo tracks learning progress for 500M+ users. Every lesson completion, XP gain, streak count, and leaderboard position is stored in DynamoDB.

### 📐 Table Design

```
Table: UserProgress
PK: USER#user_abc          SK: PROFILE                     (streak, totalXP, level)
PK: USER#user_abc          SK: COURSE#spanish               (progress %, units done)
PK: USER#user_abc          SK: LESSON#spanish#unit3#lesson5  (score, completedAt)
PK: USER#user_abc          SK: STREAK#2024-01-15            (dailyXP for that day)

GSI: Leaderboard
PK: LEAGUE#diamond#week_2024_03    SK: weeklyXP (number)
```

### 💡 Why DynamoDB?

- **Single partition per user** → entire profile + progress in one Query
- **Atomic counters** for streak days: `SET streakDays = streakDays + :one`
- **TTL on daily entries** → auto-cleanup old daily data
- **Leaderboard via GSI** with numeric sort key for ranking

### 🔧 Streak Logic

```
# Complete a lesson — atomic XP update
UpdateItem:
  Key: { PK: "USER#user_abc", SK: "PROFILE" }
  UpdateExpression: "ADD totalXP :xp SET lastActiveDate = :today"
  
# Check and extend streak (conditional)
UpdateItem:
  Key: { PK: "USER#user_abc", SK: "PROFILE" }
  UpdateExpression: "SET streakDays = streakDays + :one, lastActiveDate = :today"
  ConditionExpression: "lastActiveDate = :yesterday"
  # If condition fails → streak broken, reset to 1
```

---

## 5. Nike — SNKRS Flash Sale & Inventory

### 🏗️ Architecture Context

Nike SNKRS drops generate millions of simultaneous requests. DynamoDB handles the inventory decrement with zero overselling using **conditional writes**.

### 📐 Table Design

```
Table: Inventory
PK: PRODUCT#shoe_abc       SK: SIZE#10
Attributes: stock (number), reservedUntil (TTL for held items)
```

### 🔧 Atomic Inventory Decrement

```
# Reserve a pair — ONLY if stock > 0 (prevents overselling)
UpdateItem:
  Key: { PK: "PRODUCT#shoe_abc", SK: "SIZE#10" }
  UpdateExpression: "SET stock = stock - :one"
  ConditionExpression: "stock > :zero"
  # If condition fails → ConditionalCheckFailedException → SOLD OUT

# Release reservation (if user doesn't complete checkout)
TTL auto-expires the reservation → stock restored via DynamoDB Streams + Lambda
```

### 💡 Why DynamoDB for Flash Sales?

- **Conditional writes** prevent overselling without distributed locks
- **Auto-scales to millions of TPS** during the drop
- **On-demand capacity** mode — pay only during the spike
- **Single-digit ms** even under extreme load

---

## 6. Capital One — Account Transactions

### 📐 Table Design

```
Table: Transactions
PK: ACCOUNT#acc_123        SK: TXN#2024-01-15T14:30:00#txn_xyz
Attributes: amount, merchant, category, balance_after

GSI: PK = ACCOUNT#acc_123, SK = CATEGORY#dining#2024-01-15
```

### 🔧 Key Operations

```
# Get recent transactions
Query: PK = "ACCOUNT#acc_123", SK begins_with "TXN#2024-01", ScanIndexForward: false

# Monthly spending by category (via GSI)
Query on GSI: PK = "ACCOUNT#acc_123", SK begins_with "CATEGORY#dining#2024-01"

# Idempotent transaction write (prevent double-posting)
PutItem:
  Item: { PK: "ACCOUNT#acc_123", SK: "TXN#...#txn_xyz", ... }
  ConditionExpression: "attribute_not_exists(PK)"
```

---

## 7. Twitch — Chat Messages

### 📐 Table Design

```
Table: ChatMessages
PK: CHANNEL#channel_abc     SK: MSG#2024-01-15T14:30:05.123#msg_id
Attributes: userId, text, badges, emotes
TTL: 30 days (auto-delete old messages)
```

### 🔧 Operations

```
# Get latest 50 messages for channel
Query: PK = "CHANNEL#channel_abc", ScanIndexForward: false, Limit: 50

# Write message (fire-and-forget, high throughput)
PutItem: { PK: "CHANNEL#channel_abc", SK: "MSG#<timestamp>#<msgId>", ... }
```

### 💡 Why?
- Millions of concurrent chat channels during big streams
- Write-heavy (thousands of messages/sec per popular channel)
- TTL auto-cleans old messages — no manual purge needed

---

## 8. Tinder — User Swipes & Match Tracking

### 📐 Table Design

```
Table: Swipes
PK: USER#user_A             SK: SWIPE#USER#user_B
Attributes: direction (LEFT|RIGHT), swipedAt

Table: Matches
PK: USER#user_A             SK: MATCH#USER#user_B
Attributes: matchedAt, lastMessage
```

### 🔧 Match Detection

```
# User A swipes RIGHT on User B
PutItem: { PK: "USER#user_A", SK: "SWIPE#USER#user_B", direction: "RIGHT" }

# Check if User B already swiped RIGHT on User A
GetItem: { PK: "USER#user_B", SK: "SWIPE#USER#user_A" }
  → If exists AND direction = "RIGHT" → IT'S A MATCH!
  → TransactWriteItems: Create match records for BOTH users
```

---

## 9. Uber Eats — Restaurant Menu & Order Tracking

### 📐 Table Design

```
Table: RestaurantData (Single-Table)
PK: REST#rest_123           SK: META                     (name, address, rating)
PK: REST#rest_123           SK: MENU#item_abc            (name, price, category)
PK: REST#rest_123           SK: HOURS#monday             (open, close)
PK: ORDER#ord_xyz           SK: META                     (status, total, estimatedTime)
PK: ORDER#ord_xyz           SK: ITEM#item_abc            (quantity, customizations)
PK: USER#user_123           SK: ORDER#2024-01-15#ord_xyz (orderRef)
```

### 🔧 Real-Time Order Status Updates

```
# Update order status → triggers DynamoDB Stream
UpdateItem:
  Key: { PK: "ORDER#ord_xyz", SK: "META" }
  UpdateExpression: "SET #status = :new, estimatedTime = :eta"

# DynamoDB Streams → Lambda → WebSocket push to customer's app
```

---

## 10. Snapchat — Ephemeral Messages with TTL

### 📐 Table Design

```
Table: Messages
PK: CONV#conv_abc           SK: MSG#<timestamp>#msg_id
Attributes: senderId, content (encrypted), mediaUrl
TTL: viewedAt + 10 seconds  (or sentAt + 24 hours if unviewed)
```

### 💡 Why TTL is Perfect for Snapchat?

- **DynamoDB TTL = free deletion** — no cron job needed
- Items auto-expire at specified Unix timestamp
- Expired items removed within ~48 hours (eventually, not instant)
- **DynamoDB Streams** captures TTL deletions → trigger cleanup of media in S3

---

## 11. Netflix — User Viewing History

### 📐 Table Design

```
PK: USER#user_123           SK: WATCH#2024-01-15T20:30:00#title_abc
Attributes: titleId, progress (%), duration, device

GSI: PK = USER#user_123, SK = TITLE#title_abc (resume playback)
```

### 🔧 Operations

```
# "Continue Watching" — get latest position
GetItem on GSI: PK = "USER#user_123", SK = "TITLE#title_abc"

# Save progress (every 30 seconds during playback)
UpdateItem:
  Key: { PK: "USER#user_123", SK: "WATCH#<now>#title_abc" }
  UpdateExpression: "SET progress = :pct, lastUpdated = :now"
```

---

## 12. Slack — Notification Preferences

### 📐 Table Design

```
PK: USER#user_123           SK: PREF#workspace_abc#channel_xyz
Attributes: muted (bool), keywords [], schedule { start, end }
```

### 💡 Why DynamoDB?
- Simple key-value lookup per user-channel combination
- Low latency for "should I send this notification?" check on every message
- Millions of preference records, accessed individually

---

## 13. DoorDash — Real-Time Delivery Tracking

### 📐 Table Design

```
Table: Deliveries
PK: DELIVERY#del_abc        SK: META         (status, dasher, customer, restaurant)
PK: DELIVERY#del_abc        SK: LOC#<ts>     (GPS coordinates)
PK: DASHER#dasher_456       SK: ACTIVE       (current delivery reference)
```

### 🔧 GPS Tracking

```
# Dasher sends location every 5 seconds
PutItem:
  Item: { PK: "DELIVERY#del_abc", SK: "LOC#2024-01-15T14:30:05", lat: 37.7749, lng: -122.4194 }
  TTL: now + 1 hour (auto-cleanup old location points)

# Customer app queries latest location
Query: PK = "DELIVERY#del_abc", SK begins_with "LOC#", ScanIndexForward: false, Limit: 1
```

---

## 14. Airbnb — Booking & Availability

### 📐 Table Design

```
PK: LISTING#list_abc        SK: AVAIL#2024-01-15    (available: bool, price)
PK: LISTING#list_abc        SK: BOOKING#book_xyz    (guestId, checkIn, checkOut, status)
```

### 🔧 Prevent Double Booking

```
# Book a date range — conditional on availability
TransactWriteItems:
  - Update { PK: "LISTING#list_abc", SK: "AVAIL#2024-01-15" }
    ConditionExpression: "available = :true"
    UpdateExpression: "SET available = :false"
  - Update { PK: "LISTING#list_abc", SK: "AVAIL#2024-01-16" }
    ConditionExpression: "available = :true"
    UpdateExpression: "SET available = :false"
  - Put { PK: "LISTING#list_abc", SK: "BOOKING#book_xyz", ... }
# ALL succeed or ALL fail — no partial booking!
```

---

## 15. Discord — Server & Channel Membership

### 📐 Table Design

```
PK: SERVER#srv_abc          SK: MEMBER#user_123     (roles, joinedAt, nickname)
PK: SERVER#srv_abc          SK: CHANNEL#ch_xyz      (name, type, permissions)
PK: USER#user_123           SK: SERVER#srv_abc      (reverse lookup — user's servers)
```

### 🔧 Operations

```
# Get all members of a server
Query: PK = "SERVER#srv_abc", SK begins_with "MEMBER#"

# Get all servers a user belongs to
Query: PK = "USER#user_123", SK begins_with "SERVER#"

# Join server — write to BOTH partitions (TransactWriteItems)
TransactWriteItems:
  - PutItem: { PK: "SERVER#srv_abc", SK: "MEMBER#user_123", ... }
  - PutItem: { PK: "USER#user_123", SK: "SERVER#srv_abc", ... }
```

---

## 16–25: Additional Real-World Applications (Summary)

### 16. IoT — Sensor Data Collection
```
PK: DEVICE#device_abc, SK: DATA#<timestamp>
TTL: 90 days. DynamoDB Streams → Kinesis → S3 for long-term storage.
```

### 17. GitHub — Feature Flags
```
PK: FLAG#dark_mode, SK: META (percentage, rules)
PK: FLAG#dark_mode, SK: USER#user_123 (override for specific user)
```

### 18. Stripe — Idempotency Keys
```
PK: IDEMPOTENCY#key_abc. ConditionExpression: attribute_not_exists(PK).
TTL: 24 hours. Prevents duplicate payment processing.
```

### 19. Zoom — Meeting Metadata
```
PK: MEETING#mtg_abc, SK: META (host, scheduled, duration)
PK: MEETING#mtg_abc, SK: PARTICIPANT#user_123 (joined, left, duration)
```

### 20. Pinterest — User Pins & Boards
```
PK: USER#user_abc, SK: BOARD#board_xyz (name, visibility)
PK: BOARD#board_xyz, SK: PIN#<timestamp>#pin_id (imageUrl, description)
```

### 21. Shopify — Product Catalog
```
PK: SHOP#shop_abc, SK: PRODUCT#prod_xyz (title, price, inventory)
GSI: PK = CATEGORY#electronics, SK = price (for category browsing)
```

### 22. Ring — Doorbell Event History
```
PK: DEVICE#doorbell_abc, SK: EVENT#<timestamp> (type: motion|ring, videoUrl)
TTL: 60 days (Basic plan). DynamoDB Streams → send push notification.
```

### 23. Robinhood — Watchlist
```
PK: USER#user_abc, SK: WATCH#AAPL (addedAt, alertPrice)
User adds/removes stocks from watchlist — simple PutItem/DeleteItem.
```

### 24. Ticketmaster — Seat Reservation
```
PK: EVENT#evt_abc, SK: SEAT#A12
ConditionExpression: "#status = :available" → prevents double-booking.
TTL on held seats → auto-release if not purchased within 10 minutes.
```

### 25. Notion — Page & Block Storage
```
PK: PAGE#page_abc, SK: BLOCK#<order>#block_id (type, content, parentBlockId)
Query with begins_with for all blocks of a page, ordered by position.
```

---

## 🏆 Cheat Sheet: DynamoDB Table Design Decision Guide

```
┌────────────────────────────────────────────────────────────────────────┐
│           DYNAMODB TABLE DESIGN DECISION GUIDE                         │
├────────────────────────────────────────────────────────────────────────┤
│ Access Pattern               → Design Choice                          │
│─────────────────────────────────────────────────────────────────────── │
│ Get one item by ID           → PK = entityId, no SK needed            │
│ Get entity + related items   → PK = entityId, SK = type#details       │
│ Time-ordered queries         → SK = timestamp#id (natural sort)       │
│ Lookup by alternate key      → GSI with that key as PK                │
│ Prevent duplicates           → ConditionExpression: attr_not_exists   │
│ Prevent race conditions      → ConditionExpression on current value   │
│ Auto-expire data             → TTL field (Unix timestamp)             │
│ Cross-item transactions      → TransactWriteItems (up to 100 items)  │
│ Real-time reactions          → DynamoDB Streams → Lambda              │
│ Bi-directional relationship  → Write to BOTH partitions (A→B, B→A)   │
│ High-cardinality partition   → Add random suffix to PK (write sharding)│
│ Category browsing            → GSI with category as PK, price as SK   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary: "When Would You Use DynamoDB?"

> *"I'd choose DynamoDB when I need **single-digit millisecond latency at any scale** with a well-defined set of access patterns. It's perfect for shopping carts, user sessions, order tracking, IoT data, and any workload where I know my queries upfront and can model them as partition key + sort key lookups. The key design principles: model around access patterns (not entities), use single-table design for related data, use conditional writes for consistency, TTL for auto-expiration, and DynamoDB Streams for event-driven reactions. I'd NOT use it when I need ad-hoc queries, complex joins, or full-text search — that's where PostgreSQL or Elasticsearch fits better."*

### DynamoDB vs Alternatives — When to Choose What

| Use Case | DynamoDB ✅ | PostgreSQL ✅ | Redis ✅ |
|----------|-----------|-------------|---------|
| Shopping cart | ✅ Durable, scalable | Possible but slower at scale | ❌ Not durable |
| Complex reporting | ❌ No joins/aggregates | ✅ SQL, JOINs, GROUP BY | ❌ |
| Session store | ✅ With TTL | Possible | ✅ Faster but volatile |
| User profiles | ✅ Simple key-value | ✅ If schema is relational | ❌ |
| Real-time leaderboard | ✅ With GSI | Possible | ✅ Sorted Sets (best) |
| Transaction history | ✅ Time-series queries | ✅ With indexes | ❌ Not designed for this |
| Flash sale inventory | ✅ Conditional writes | ✅ SELECT FOR UPDATE | ❌ |
| Ad-hoc analytics | ❌ Must know patterns | ✅ Flexible SQL | ❌ |

---

> **Related Topics**: [DynamoDB Deep Dive →](./49-dynamodb-deep-dive.md) | [DynamoDB Practical Patterns →](./49b-dynamodb-practical-patterns-and-use-cases.md) | [Redis Real-World →](./40b-redis-real-world-applications.md) | [PostgreSQL Deep Dive →](./50-postgresql-deep-dive.md)
