# Redis Real-World Examples — Part 2: LIST & SET Problems

## 🔹 LIST-BASED PROBLEMS (21–30)

---

### Problem 21: Message Queue (LPUSH / RPOP)

**Scenario**: Uber dispatches ride requests to drivers. Producer (rider app) pushes requests, consumer (matching service) pops and processes them. FIFO order.

**Why Redis List?**: LPUSH/RPOP gives O(1) FIFO queue. No broker setup like Kafka/RabbitMQ. Perfect for simple, high-throughput queues.

**Key Design**: `queue:{queue_name}`

```redis
# Producer: Rider requests a ride
LPUSH queue:ride_requests '{"rider_id":123,"pickup":"37.7749,-122.4194","time":"2024-01-15T10:30:00Z"}'
LPUSH queue:ride_requests '{"rider_id":456,"pickup":"40.7128,-74.0060","time":"2024-01-15T10:30:05Z"}'
LPUSH queue:ride_requests '{"rider_id":789,"pickup":"34.0522,-118.2437","time":"2024-01-15T10:30:10Z"}'

# Consumer: Matching service processes oldest first (FIFO)
RPOP queue:ride_requests
# Returns '{"rider_id":123,...}' → oldest request

RPOP queue:ride_requests
# Returns '{"rider_id":456,...}'

# Check queue depth (monitoring)
LLEN queue:ride_requests
# Returns 1 → one request remaining

# Peek without consuming
LRANGE queue:ride_requests -1 -1
# Returns last element (next to be RPOPed) without removing
```

**Reliable Queue Pattern (RPOPLPUSH)**:
```redis
# Problem: What if consumer crashes after RPOP but before processing?
# Solution: Move to processing queue, then remove after completion

RPOPLPUSH queue:ride_requests queue:ride_processing
# Atomically: pop from main, push to processing queue

# After successful processing:
LREM queue:ride_processing 1 '{"rider_id":123,...}'
# Remove from processing queue

# Recovery: Check processing queue for stuck items
LRANGE queue:ride_processing 0 -1
# Items here for too long → re-enqueue
```

**Time Complexity**: LPUSH O(1), RPOP O(1), LLEN O(1).

---

### Problem 22: Chat Feed (LPUSH)

**Scenario**: WhatsApp group chat — new messages pushed to the top. Each group has its own message list. Show latest messages first.

**Key Design**: `chat:{group_id}:messages`

```redis
# User sends a message in group chat
LPUSH chat:group:42:messages '{"from":"alice","text":"Hey everyone!","ts":1705312200}'
LPUSH chat:group:42:messages '{"from":"bob","text":"Hi Alice!","ts":1705312205}'
LPUSH chat:group:42:messages '{"from":"charlie","text":"Whats up?","ts":1705312210}'

# Latest messages are at the head (index 0)
# Get last 20 messages (most recent first)
LRANGE chat:group:42:messages 0 19
# Returns newest → oldest

# Keep only last 500 messages (memory management)
LTRIM chat:group:42:messages 0 499
# Trims list to 500 entries. Older messages deleted.

# Get message count
LLEN chat:group:42:messages
# Returns 500

# Pagination: page 2 (messages 20-39)
LRANGE chat:group:42:messages 20 39
```

**Chat with Delivery Receipts**:
```redis
# Track unread count per user per group
INCR unread:user:123:group:42
# When user opens chat:
SET unread:user:123:group:42 0

# Track last-read position
SET lastread:user:123:group:42 1705312200
# App shows messages after this timestamp as unread
```

**Time Complexity**: LPUSH O(1). LRANGE O(S+N) where S = start offset, N = elements returned. LTRIM O(N).

---

### Problem 23: Last N Messages Fetch (LRANGE)

**Scenario**: Slack loads the last 50 messages when user opens a channel. Support scrolling up for older messages.

**Key Design**: `channel:{channel_id}:messages`

```redis
# Initial load: Get last 50 messages
LRANGE channel:general:messages 0 49
# Returns 50 most recent messages (index 0 = newest)

# Scroll up: Load 50 more (older messages)
LRANGE channel:general:messages 50 99
# Returns messages 51-100

# Scroll up again
LRANGE channel:general:messages 100 149

# Total messages in channel
LLEN channel:general:messages
# Returns 12847

# Search for specific message (not efficient with lists!)
# → Use a separate search index (Elasticsearch) for text search
# Lists are for ordered retrieval, not search
```

**Bounded Channel History**:
```redis
# On every new message:
LPUSH channel:general:messages '{"msg":"Hello","ts":1705312200}'
LTRIM channel:general:messages 0 9999
# Keep only last 10,000 messages
# Older messages should be archived to DB/S3
```

**Time Complexity**: LRANGE O(S+N). For `LRANGE key 0 49`, S=0, N=50 → O(50).

---

### Problem 24: Blocking Background Worker Queue (BLPOP)

**Scenario**: Email service has worker processes that wait for emails to send. Workers should block (not busy-wait) when queue is empty.

**Why BLPOP?**: Worker blocks until a job arrives. No CPU-wasting polling. No sleep/retry loops. Redis pushes the job to the worker.

**Key Design**: `queue:{job_type}`

```redis
# Producer: Application enqueues emails
LPUSH queue:emails '{"to":"alice@gmail.com","subject":"Welcome!","body":"..."}'
LPUSH queue:emails '{"to":"bob@gmail.com","subject":"Password Reset","body":"..."}'

# Consumer (Worker): Block until job available
BLPOP queue:emails 30
# Blocks for up to 30 seconds waiting for a job
# Returns ["queue:emails", '{"to":"alice@gmail.com",...}'] when job arrives
# Returns nil after 30 seconds if no job (timeout)

# Multiple queues with priority:
BLPOP queue:emails:urgent queue:emails:normal queue:emails:low 30
# Checks urgent first, then normal, then low
# Returns from first non-empty queue
```

**Multi-Worker Pattern**:
```redis
# Worker 1 (blocks):  BLPOP queue:emails 0    → Gets job A
# Worker 2 (blocks):  BLPOP queue:emails 0    → Gets job B
# Worker 3 (blocks):  BLPOP queue:emails 0    → Waiting...

# Producer pushes job C:
LPUSH queue:emails '{"to":"charlie@gmail.com",...}'
# → Worker 3 immediately receives job C

# Each job goes to exactly ONE worker (no duplicates)
```

**Dead Letter Queue Pattern**:
```redis
# Worker processes job, fails:
# 1. Check retry count
HGET job:meta:job123 retries
# Returns "2"

# 2. If retries < max (3), re-enqueue with delay
HINCRBY job:meta:job123 retries 1
LPUSH queue:emails '{"id":"job123",...}'

# 3. If retries >= max, move to dead letter queue
LPUSH queue:emails:dead '{"id":"job123","error":"SMTP timeout","retries":3}'
```

**Time Complexity**: BLPOP O(1). Blocks until data available or timeout.

---

### Problem 25: Recent User Activity Log

**Scenario**: LinkedIn shows "Recent Activity" — last 50 actions (likes, comments, shares). Newest first, bounded list.

**Key Design**: `activity:{user_id}`

```redis
# User performs actions
LPUSH activity:user:123 '{"action":"liked","target":"post:456","ts":1705312200}'
LPUSH activity:user:123 '{"action":"commented","target":"post:789","ts":1705312260}'
LPUSH activity:user:123 '{"action":"shared","target":"article:101","ts":1705312320}'

# Always trim to keep only last 50
LTRIM activity:user:123 0 49

# Get recent activity feed
LRANGE activity:user:123 0 9
# Returns last 10 actions (for initial page load)

# Get more (pagination)
LRANGE activity:user:123 10 19

# Activity count
LLEN activity:user:123
# Returns 50 (max, since we trim)
```

**Atomic Push + Trim — Lua Script**:
```lua
-- KEYS[1] = activity:user:123
-- ARGV[1] = JSON payload
-- ARGV[2] = max length (50)
redis.call("LPUSH", KEYS[1], ARGV[1])
redis.call("LTRIM", KEYS[1], 0, tonumber(ARGV[2]) - 1)
return redis.call("LLEN", KEYS[1])
```

**Time Complexity**: LPUSH O(1). LTRIM O(N) where N = elements removed. LRANGE O(S+N).

---

### Problem 26: Failed Job Retry Queue

**Scenario**: Payment processing service — when a charge fails (network timeout), retry with exponential backoff. Track retry count.

**Key Design**: `queue:payments`, `queue:payments:retry`, `job:meta:{job_id}`

```redis
# Job fails → push to retry queue with metadata
LPUSH queue:payments:retry '{"job_id":"pay_001","amount":99.99,"retry":1}'

# Worker picks up retry job
RPOP queue:payments:retry
# Returns the failed job

# Track retry metadata
HINCRBY job:meta:pay_001 retry_count 1
HSET job:meta:pay_001 last_error "GATEWAY_TIMEOUT"
HSET job:meta:pay_001 next_retry_at "2024-01-15T10:35:00Z"

# Check if should retry or give up
HGET job:meta:pay_001 retry_count
# Returns "3" → if >= max_retries, move to dead letter queue

# Dead letter queue (permanently failed)
LPUSH queue:payments:dead '{"job_id":"pay_001","error":"MAX_RETRIES_EXCEEDED"}'

# Monitor retry queue depth
LLEN queue:payments:retry
# Returns 5 → 5 jobs waiting for retry
```

**Delayed Retry with Sorted Set (Better Approach)**:
```redis
# Score = timestamp when retry should happen
ZADD queue:payments:delayed 1705312500 '{"job_id":"pay_001","retry":1}'
# Retry at timestamp 1705312500 (in 5 minutes)

# Worker polls for ready-to-retry jobs:
ZRANGEBYSCORE queue:payments:delayed -inf 1705312200
# Returns jobs with score <= now → ready to process

# Remove from delayed queue and process
ZPOPMIN queue:payments:delayed
```

**Time Complexity**: LPUSH/RPOP O(1). ZADD O(log N).

---

### Problem 27: Task Prioritization with Multiple Lists

**Scenario**: Support ticket system — critical, high, medium, low priority. Workers process highest priority first.

**Key Design**: `queue:tickets:{priority}`

```redis
# Enqueue tickets by priority
LPUSH queue:tickets:critical '{"ticket":"T001","issue":"SITE_DOWN"}'
LPUSH queue:tickets:high '{"ticket":"T002","issue":"PAYMENT_FAILED"}'
LPUSH queue:tickets:medium '{"ticket":"T003","issue":"SLOW_LOAD"}'
LPUSH queue:tickets:low '{"ticket":"T004","issue":"UI_BUG"}'

# Worker: Process highest priority first (blocking)
BRPOP queue:tickets:critical queue:tickets:high queue:tickets:medium queue:tickets:low 30
# BRPOP checks queues LEFT to RIGHT
# Returns from first non-empty queue
# If critical has items → returns critical ticket
# If critical empty, checks high, etc.

# Monitor queue depths per priority
LLEN queue:tickets:critical   # → 0
LLEN queue:tickets:high       # → 3
LLEN queue:tickets:medium     # → 12
LLEN queue:tickets:low        # → 47
```

**Weighted Priority (Lua Script)**:
```lua
-- Process critical 60% of time, high 25%, medium 10%, low 5%
-- KEYS[1-4] = critical, high, medium, low queues
local rand = math.random(100)
if rand <= 60 then
    local job = redis.call("RPOP", KEYS[1])
    if job then return job end
end
if rand <= 85 then
    local job = redis.call("RPOP", KEYS[2])
    if job then return job end
end
if rand <= 95 then
    local job = redis.call("RPOP", KEYS[3])
    if job then return job end
end
return redis.call("RPOP", KEYS[4])
```

**Time Complexity**: BRPOP O(1) per queue checked. LPUSH O(1).

---

### Problem 28: Page Visit History per User

**Scenario**: Amazon shows "Recently Viewed Products" — last 20 items a user viewed. No duplicates, newest first.

**Key Design**: `viewed:{user_id}`

```redis
# User views products (push to head)
LPUSH viewed:user:123 "product:LAPTOP-001"
LPUSH viewed:user:123 "product:MOUSE-042"
LPUSH viewed:user:123 "product:LAPTOP-001"  # Viewed again!

# Problem: duplicates! LAPTOP-001 appears twice
# Solution: Remove existing before pushing
LREM viewed:user:123 0 "product:LAPTOP-001"  # Remove all occurrences
LPUSH viewed:user:123 "product:LAPTOP-001"   # Add to top
LTRIM viewed:user:123 0 19                   # Keep last 20
```

**Atomic No-Duplicate Push — Lua Script**:
```lua
-- KEYS[1] = viewed:user:123
-- ARGV[1] = product:LAPTOP-001
-- ARGV[2] = 20 (max history length)
redis.call("LREM", KEYS[1], 0, ARGV[1])          -- Remove if exists
redis.call("LPUSH", KEYS[1], ARGV[1])             -- Push to head
redis.call("LTRIM", KEYS[1], 0, tonumber(ARGV[2]) - 1)  -- Trim
return redis.call("LLEN", KEYS[1])
```

```redis
# Get recently viewed
LRANGE viewed:user:123 0 19
# Returns: ["product:LAPTOP-001", "product:MOUSE-042", ...]
# Most recently viewed first, no duplicates, max 20 items
```

**Time Complexity**: LREM O(N). LPUSH O(1). LTRIM O(K) where K = removed elements.

---

### Problem 29: Event Stream Consumer (RPOP/LPOP)

**Scenario**: Analytics pipeline — web events (clicks, pageviews) are pushed by frontend, consumed by analytics workers.

**Key Design**: `events:{event_type}`

```redis
# Frontend pushes events
LPUSH events:clicks '{"user":123,"page":"/home","ts":1705312200}'
LPUSH events:clicks '{"user":456,"page":"/product/42","ts":1705312201}'
LPUSH events:pageviews '{"user":789,"page":"/checkout","ts":1705312202}'

# Analytics worker consumes click events
RPOP events:clicks
# Returns oldest click event

# Batch consume (pop multiple at once, Redis 6.2+)
LMPOP 2 events:clicks events:pageviews LEFT COUNT 10
# Pop up to 10 events from first non-empty queue

# For Redis < 6.2, use Lua for batch pop:
```

**Batch Consumer — Lua Script**:
```lua
-- KEYS[1] = events:clicks
-- ARGV[1] = batch size (10)
local batch = {}
local count = math.min(tonumber(ARGV[1]), redis.call("LLEN", KEYS[1]))
for i = 1, count do
    local event = redis.call("RPOP", KEYS[1])
    if event then
        table.insert(batch, event)
    end
end
return batch
```

**Time Complexity**: RPOP O(1). Batch of N → O(N).

**Interview Follow-up**: "For production event streaming, why not just use Redis Streams?" → Streams provide consumer groups, acknowledgment, and replay. Lists are simpler but don't guarantee processing. Use Streams for reliability (see Part 3).

---

### Problem 30: FIFO Order Processing System

**Scenario**: Domino's Pizza — orders must be prepared in the order they were received. Multiple kitchen stations consume from the same queue.

**Key Design**: `orders:{store_id}`

```redis
# Customer places orders
LPUSH orders:store:NYC-001 '{"order_id":"ORD-101","items":["pizza","coke"],"ts":1705312200}'
LPUSH orders:store:NYC-001 '{"order_id":"ORD-102","items":["burger","fries"],"ts":1705312210}'
LPUSH orders:store:NYC-001 '{"order_id":"ORD-103","items":["salad"],"ts":1705312220}'

# Kitchen station 1 takes oldest order
RPOP orders:store:NYC-001
# Returns ORD-101 (FIFO — first in, first out)

# Kitchen station 2 takes next
RPOP orders:store:NYC-001
# Returns ORD-102

# Blocking wait (station waits for next order)
BRPOP orders:store:NYC-001 0
# Blocks indefinitely until next order arrives

# Order tracking (separate hash)
HSET order:ORD-101 status "PREPARING" station "STATION-1" started_at 1705312230
HSET order:ORD-101 status "READY" completed_at 1705312530

# Queue depth monitoring (dashboard)
LLEN orders:store:NYC-001
# Returns 1 → 1 order waiting
# If > 10 → alert manager, open more stations
```

**Order with Priority — Two Queues**:
```redis
# VIP orders (priority)
LPUSH orders:store:NYC-001:vip '{"order_id":"ORD-104","vip":true}'

# Regular orders
LPUSH orders:store:NYC-001:regular '{"order_id":"ORD-105"}'

# Kitchen processes VIP first
BRPOP orders:store:NYC-001:vip orders:store:NYC-001:regular 30
```

**Time Complexity**: LPUSH O(1). RPOP O(1). BRPOP O(1).

---

## 🔹 SET-BASED PROBLEMS (31–40)

---

### Problem 31: Unique Visitor Tracker (SADD)

**Scenario**: Google Analytics tracks unique visitors per page per day. Same user visiting 10 times counts as 1.

**Why Set?**: SADD automatically deduplicates. SCARD gives exact unique count in O(1). Perfect for uniqueness.

**Key Design**: `visitors:{page}:{date}`

```redis
# User visits homepage
SADD visitors:/home:2024-01-15 user:123
# Returns 1 → new visitor

SADD visitors:/home:2024-01-15 user:456
# Returns 1 → new visitor

SADD visitors:/home:2024-01-15 user:123
# Returns 0 → already visited today (deduped!)

# Count unique visitors
SCARD visitors:/home:2024-01-15
# Returns 2

# Set TTL (auto-cleanup after 30 days)
EXPIRE visitors:/home:2024-01-15 2592000

# Check if specific user visited
SISMEMBER visitors:/home:2024-01-15 user:123
# Returns 1 (yes)

# List all visitors (for small sets only)
SMEMBERS visitors:/home:2024-01-15
# Returns ["user:123", "user:456"]
```

**Multi-Day Unique Visitors**:
```redis
# Unique visitors across Mon-Fri:
SUNIONSTORE visitors:/home:week1 
    visitors:/home:2024-01-15 
    visitors:/home:2024-01-16 
    visitors:/home:2024-01-17
SCARD visitors:/home:week1
# Returns unique visitors for the week (deduplicated across days)
```

**Time Complexity**: SADD O(1). SCARD O(1). SUNIONSTORE O(N).

**Interview Follow-up**: "What if you have 100M unique visitors?" → Use HyperLogLog (12KB vs ~1.6GB for SET). See Problem 69.

---

### Problem 32: Active User Count (SCARD)

**Scenario**: Discord shows "X users online" in a server. Need real-time count of active users.

**Key Design**: `online:{server_id}`

```redis
# User comes online
SADD online:server:42 user:123
SADD online:server:42 user:456
SADD online:server:42 user:789

# Show online count
SCARD online:server:42
# Returns 3

# User goes offline
SREM online:server:42 user:456

# Updated count
SCARD online:server:42
# Returns 2

# Check if specific user is online
SISMEMBER online:server:42 user:789
# Returns 1 → online

# Handle stale connections (heartbeat pattern)
# Each user sends heartbeat → SADD with EXPIRE on per-user key
SET heartbeat:user:123 1 EX 60
# If heartbeat expires → user offline → SREM from online set
```

**Heartbeat Cleanup — Background Job**:
```redis
# Every 30 seconds, check heartbeats for all online users
SMEMBERS online:server:42
# For each member:
EXISTS heartbeat:user:123
# If 0 → heartbeat expired → SREM online:server:42 user:123
```

**Time Complexity**: SADD O(1). SREM O(1). SCARD O(1).

---

### Problem 33: User Subscription Check (SISMEMBER)

**Scenario**: YouTube Premium — on every video play, check if user has an active subscription (to show/hide ads). Must be < 1ms.

**Key Design**: `subscribers:premium`

```redis
# Add subscribers
SADD subscribers:premium user:123 user:456 user:789

# On video play, check subscription
SISMEMBER subscribers:premium user:123
# Returns 1 → premium subscriber → no ads
# Returns 0 → free user → show ads

# User subscribes
SADD subscribers:premium user:999
# Returns 1 → added

# User cancels
SREM subscribers:premium user:456
# Returns 1 → removed

# Total premium subscribers
SCARD subscribers:premium
# Returns 3
```

**Multi-Tier Subscription Check**:
```redis
SADD subscribers:basic user:100 user:200
SADD subscribers:premium user:123 user:456
SADD subscribers:enterprise user:500

# Check user's tier (check highest first)
SISMEMBER subscribers:enterprise user:123  # → 0
SISMEMBER subscribers:premium user:123     # → 1 → Premium!
```

**Time Complexity**: SISMEMBER O(1). Perfect for hot-path checks.

---

### Problem 34: Users Who Liked Both Products (SINTER)

**Scenario**: Amazon — "Customers who bought Product A also bought Product B". Find intersection of buyer sets.

**Key Design**: `buyers:{product_id}`

```redis
# Track who bought each product
SADD buyers:product:LAPTOP-001 user:123 user:456 user:789 user:101
SADD buyers:product:MOUSE-042 user:456 user:789 user:202 user:303

# Find users who bought BOTH
SINTER buyers:product:LAPTOP-001 buyers:product:MOUSE-042
# Returns ["user:456", "user:789"]

# Count of users who bought both
# (Store result first, then count)
SINTERSTORE temp:both buyers:product:LAPTOP-001 buyers:product:MOUSE-042
SCARD temp:both
# Returns 2
DEL temp:both

# "Customers who bought this also bought" recommendation:
# For each product bought by user:123, find other buyers' products
SMEMBERS buyers:product:LAPTOP-001
# → Get all users who bought LAPTOP-001
# → For each user, find what else they bought
# → Rank by frequency = recommendation
```

**Time Complexity**: SINTER O(N*M) where N = size of smallest set, M = number of sets.

---

### Problem 35: Newsletter Subscriber Merge (SUNION)

**Scenario**: Marketing team has separate subscriber lists for different campaigns. Need to send to ALL unique subscribers across lists.

**Key Design**: `newsletter:{campaign}`

```redis
# Different campaign subscriber lists
SADD newsletter:product_launch alice@mail.com bob@mail.com charlie@mail.com
SADD newsletter:weekly_digest bob@mail.com dave@mail.com eve@mail.com
SADD newsletter:special_offers charlie@mail.com eve@mail.com frank@mail.com

# Get ALL unique subscribers across all campaigns
SUNION newsletter:product_launch newsletter:weekly_digest newsletter:special_offers
# Returns: ["alice@mail.com", "bob@mail.com", "charlie@mail.com", 
#           "dave@mail.com", "eve@mail.com", "frank@mail.com"]
# Deduplicated! bob and charlie appear once despite being in multiple lists

# Store merged list for batch sending
SUNIONSTORE newsletter:all_subscribers 
    newsletter:product_launch 
    newsletter:weekly_digest 
    newsletter:special_offers

SCARD newsletter:all_subscribers
# Returns 6 (unique subscribers)

# Set TTL on merged list
EXPIRE newsletter:all_subscribers 3600
```

**Time Complexity**: SUNION O(N) where N = total elements across all sets.

---

### Problem 36: Signed Up But Didn't Purchase (SDIFF)

**Scenario**: E-commerce marketing — find users who created an account but never made a purchase. Target them with discount emails.

**Key Design**: `users:registered`, `users:purchased`

```redis
# Track registrations and purchases
SADD users:registered user:100 user:200 user:300 user:400 user:500
SADD users:purchased user:200 user:400

# Find users who registered but NEVER purchased
SDIFF users:registered users:purchased
# Returns ["user:100", "user:300", "user:500"]
# These users need a nudge email!

# Store result for batch email campaign
SDIFFSTORE users:never_purchased users:registered users:purchased
SCARD users:never_purchased
# Returns 3

# More complex: Registered > 7 days ago AND never purchased
# Use sorted set for registration time, then intersect
ZADD users:registered:time 1705000000 user:100 1705100000 user:200 ...
ZRANGEBYSCORE users:registered:time -inf 1704707200
# Returns users registered before 7 days ago
# Intersect with SDIFF result for final target list
```

**Time Complexity**: SDIFF O(N) where N = size of first set.

---

### Problem 37: Online Users List (SMEMBERS)

**Scenario**: Slack shows all online team members in sidebar. Need to list all currently online users.

**Key Design**: `online:{workspace_id}`

```redis
# Users come online
SADD online:workspace:acme alice bob charlie dave eve

# List all online users (for sidebar display)
SMEMBERS online:workspace:acme
# Returns ["alice", "bob", "charlie", "dave", "eve"]

# User goes offline
SREM online:workspace:acme charlie

# Updated list
SMEMBERS online:workspace:acme
# Returns ["alice", "bob", "dave", "eve"]

# Check specific user
SISMEMBER online:workspace:acme bob
# Returns 1 → online

# Count online
SCARD online:workspace:acme
# Returns 4
```

**⚠️ SMEMBERS Warning**: O(N) — fine for small sets (< 1000 members). For large sets, use SSCAN:
```redis
# Safe iteration for large sets
SSCAN online:workspace:acme 0 COUNT 100
# Returns cursor + batch of members
# Call again with returned cursor until cursor = 0
```

**Time Complexity**: SMEMBERS O(N). SSCAN O(1) per call.

---

### Problem 38: Random Winner Selection (SPOP)

**Scenario**: Twitter giveaway — randomly select 3 winners from all participants. SPOP removes and returns random members.

**Key Design**: `contest:{contest_id}:entries`

```redis
# Users enter the contest
SADD contest:2024-jan:entries user:100 user:200 user:300 user:400 user:500
SADD contest:2024-jan:entries user:600 user:700 user:800 user:900 user:1000

# Total entries
SCARD contest:2024-jan:entries
# Returns 10

# Pick 3 random winners (removes them from set!)
SPOP contest:2024-jan:entries 3
# Returns e.g., ["user:300", "user:700", "user:100"]
# These are REMOVED from the set → can't win again

# Verify they're gone
SCARD contest:2024-jan:entries
# Returns 7

# Alternative: Random selection WITHOUT removing (for preview)
SRANDMEMBER contest:2024-jan:entries 3
# Returns 3 random members but keeps them in the set
# May return duplicates if count is negative: SRANDMEMBER key -3
```

**Fair Drawing — Lua Script (Atomic)**:
```lua
-- KEYS[1] = contest:2024-jan:entries
-- KEYS[2] = contest:2024-jan:winners
-- ARGV[1] = number of winners (3)
local count = tonumber(ARGV[1])
local winners = redis.call("SPOP", KEYS[1], count)
for _, winner in ipairs(winners) do
    redis.call("SADD", KEYS[2], winner)
end
return winners
```

**Time Complexity**: SPOP O(N) where N = count. SRANDMEMBER O(N).

---

### Problem 39: Feature Rollout Group Tracking

**Scenario**: Instagram rolling out "Reels" feature to 10% of users. Track which users are in the rollout group. Gradually increase.

**Key Design**: `rollout:{feature}:{percentage}`

```redis
# Initial 10% rollout
SADD rollout:reels user:123 user:456 user:789
# (In practice, selected by hash(user_id) % 100 < 10)

# Check if user is in rollout
SISMEMBER rollout:reels user:123
# Returns 1 → show Reels feature

SISMEMBER rollout:reels user:999
# Returns 0 → old experience

# Expand rollout: add more users (20%)
SADD rollout:reels user:111 user:222 user:333

# Track rollout size
SCARD rollout:reels
# Returns 6

# Rollback: remove specific users experiencing bugs
SREM rollout:reels user:456

# Full rollout (100%): Delete the set, check defaults to "enabled"
DEL rollout:reels
# App logic: if set doesn't exist → feature is GA (generally available)
```

**Percentage-Based Rollout (No Set Needed)**:
```redis
# Alternative: Don't store individual users
# App logic: if hash(user_id) % 100 < rollout_percentage → enabled
SET rollout:reels:percentage 10
# GET on every request → if user hash < 10 → show feature
# To expand: SET rollout:reels:percentage 50
# No per-user storage needed!
```

**Time Complexity**: SISMEMBER O(1). SADD O(1). SCARD O(1).

---

### Problem 40: A/B Testing Cohort Management

**Scenario**: Netflix runs A/B tests — users are assigned to cohorts (control, variant_A, variant_B). Need to check cohort on every page load and compute results.

**Why Sets?**: O(1) membership check. Set operations for analysis (intersection of cohort + converters).

**Key Design**: `ab:{experiment}:{variant}`

```redis
# Assign users to cohorts
SADD ab:new_checkout:control user:100 user:200 user:300 user:400 user:500
SADD ab:new_checkout:variant_a user:600 user:700 user:800 user:900 user:1000
SADD ab:new_checkout:variant_b user:1100 user:1200 user:1300 user:1400 user:1500

# On page load: check which variant to show
SISMEMBER ab:new_checkout:variant_a user:700
# Returns 1 → show variant A

SISMEMBER ab:new_checkout:control user:700
# Returns 0 → not in control

# Track conversions (purchases)
SADD ab:new_checkout:conversions user:700 user:800 user:300

# Analyze results: Which variant had more conversions?

# Control group conversions:
SINTERSTORE temp:control_conv ab:new_checkout:control ab:new_checkout:conversions
SCARD temp:control_conv
# Returns 1 (user:300) → 1/5 = 20% conversion rate

# Variant A conversions:
SINTERSTORE temp:variant_a_conv ab:new_checkout:variant_a ab:new_checkout:conversions
SCARD temp:variant_a_conv
# Returns 2 (user:700, user:800) → 2/5 = 40% conversion rate

# Variant B conversions:
SINTERSTORE temp:variant_b_conv ab:new_checkout:variant_b ab:new_checkout:conversions
SCARD temp:variant_b_conv
# Returns 0 → 0% conversion rate

# Cleanup temp keys
DEL temp:control_conv temp:variant_a_conv temp:variant_b_conv

# Cohort sizes
SCARD ab:new_checkout:control     # → 5
SCARD ab:new_checkout:variant_a   # → 5
SCARD ab:new_checkout:variant_b   # → 5
```

**Experiment Assignment — Lua Script**:
```lua
-- KEYS[1] = ab:new_checkout:control
-- KEYS[2] = ab:new_checkout:variant_a
-- KEYS[3] = ab:new_checkout:variant_b
-- ARGV[1] = user_id
-- Assign user to cohort based on hash
local user = ARGV[1]
-- Check if already assigned
if redis.call("SISMEMBER", KEYS[1], user) == 1 then return "control" end
if redis.call("SISMEMBER", KEYS[2], user) == 1 then return "variant_a" end
if redis.call("SISMEMBER", KEYS[3], user) == 1 then return "variant_b" end
-- New user: assign randomly (1/3 each)
local rand = math.random(3)
if rand == 1 then
    redis.call("SADD", KEYS[1], user)
    return "control"
elseif rand == 2 then
    redis.call("SADD", KEYS[2], user)
    return "variant_a"
else
    redis.call("SADD", KEYS[3], user)
    return "variant_b"
end
```

**Time Complexity**: SISMEMBER O(1). SINTER O(N*M). SCARD O(1).

**Interview Follow-up**:
- "How to handle millions of users?" → Use percentage-based hashing instead of storing every user in a set
- "How to ensure statistical significance?" → Track sample size per cohort with SCARD, run chi-squared test on conversion counts
