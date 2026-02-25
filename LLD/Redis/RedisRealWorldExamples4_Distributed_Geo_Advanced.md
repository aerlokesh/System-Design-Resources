# Redis Real-World Examples — Part 4: DISTRIBUTED, GEO, HYPERLOGLOG & ADVANCED

## 🔹 DISTRIBUTED & ATOMIC PROBLEMS (58–65)

---

### Problem 58: Distributed Lock (SET NX PX)

**Scenario**: Only one server should process a cron job at a time. Multiple app servers run the same scheduler — need mutual exclusion.

**Why Redis?**: `SET key value NX PX` is atomic. Single-threaded Redis guarantees only one caller wins. Built-in TTL prevents deadlock.

**Key Design**: `lock:{resource}`

```redis
# Server A tries to acquire lock
SET lock:daily_report "server-A:uuid-123" NX PX 30000
# NX = only if not exists
# PX = expire in 30000ms (30 seconds)
# Returns OK → lock acquired!

# Server B tries simultaneously
SET lock:daily_report "server-B:uuid-456" NX PX 30000
# Returns nil → lock NOT acquired (Server A has it)

# Server A finishes work → release lock
# IMPORTANT: Only release if you own it!
```

**Safe Release — Lua Script (Check-and-Delete)**:
```lua
-- KEYS[1] = lock:daily_report
-- ARGV[1] = owner identifier (server-A:uuid-123)
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0  -- Not your lock! Don't delete.
end
```

**Why check owner before delete?**
```
1. Server A acquires lock (TTL = 30s)
2. Server A takes 35s (longer than TTL!)
3. Lock auto-expires at 30s
4. Server B acquires lock at 31s
5. Server A finishes at 35s, calls DEL
6. WITHOUT owner check → Server A deletes Server B's lock!
7. Server C acquires → TWO servers running simultaneously
```

**Lock with Retry (Java)**:
```java
// Pseudocode — Jedis client
boolean acquireLock(Jedis jedis, String resource, String owner, long ttlMs, int retries) {
    for (int i = 0; i < retries; i++) {
        String result = jedis.set("lock:" + resource, owner,
                SetParams.setParams().nx().px(ttlMs));
        if ("OK".equals(result)) return true;
        try { Thread.sleep((long)(100 * Math.pow(2, i))); }  // Exponential backoff
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    return false;
}
```

**Time Complexity**: O(1).

**Interview Follow-up**:
- "What about Redis failover?" → Use Redlock algorithm (lock on N/2+1 instances)
- "What if the process takes longer than TTL?" → Use lock extension (watchdog pattern): background thread extends TTL while process is alive

---

### Problem 59: Distributed Semaphore (Sorted Set)

**Scenario**: API allows max 5 concurrent requests per user. Unlike a simple counter, need to track WHO holds the slot and auto-expire stale holders.

**Why Sorted Set?**: Score = acquisition timestamp. ZCARD = current count. ZRANGEBYSCORE to find expired holders.

**Key Design**: `semaphore:{resource}`

```redis
# User 123 acquires a slot
ZADD semaphore:api:user:123 1705312200 "request:uuid-aaa"
# Score = current timestamp, member = unique request ID

# Check count
ZCARD semaphore:api:user:123
# Returns 1 → 1 of 5 slots used

# More requests come in
ZADD semaphore:api:user:123 1705312201 "request:uuid-bbb"
ZADD semaphore:api:user:123 1705312202 "request:uuid-ccc"
ZADD semaphore:api:user:123 1705312203 "request:uuid-ddd"
ZADD semaphore:api:user:123 1705312204 "request:uuid-eee"

# 6th request → check if allowed
ZCARD semaphore:api:user:123
# Returns 5 → at limit, REJECT!

# Release slot when request completes
ZREM semaphore:api:user:123 "request:uuid-aaa"
# Returns 1 → slot freed

# Cleanup stale slots (requests that crashed without releasing)
ZREMRANGEBYSCORE semaphore:api:user:123 -inf 1705312170
# Remove all entries older than 30 seconds
```

**Atomic Acquire — Lua Script**:
```lua
-- KEYS[1] = semaphore:api:user:123
-- ARGV[1] = max slots (5)
-- ARGV[2] = current timestamp
-- ARGV[3] = request UUID
-- ARGV[4] = TTL (30 seconds)

-- Cleanup expired slots first
redis.call("ZREMRANGEBYSCORE", KEYS[1], "-inf", tonumber(ARGV[2]) - tonumber(ARGV[4]))

-- Check current count
local count = redis.call("ZCARD", KEYS[1])
if count >= tonumber(ARGV[1]) then
    return 0  -- REJECTED: at capacity
end

-- Acquire slot
redis.call("ZADD", KEYS[1], ARGV[2], ARGV[3])
return 1  -- ACQUIRED
```

**Time Complexity**: ZADD O(log N). ZCARD O(1). ZREMRANGEBYSCORE O(log N + M).

---

### Problem 60: Cache Stampede Prevention (Lock + TTL)

**Scenario**: 10,000 requests hit the same cache key at expiration. All 10,000 query the database simultaneously → DB overwhelmed.

**Solution**: Only ONE request refreshes the cache; others wait or get stale data.

**Key Design**: `cache:{key}`, `lock:cache:{key}`

```redis
# Normal flow: cache hit
GET cache:user:123:profile
# Returns cached data → serve it

# Cache MISS (expired):

# Step 1: Try to acquire refresh lock
SET lock:cache:user:123:profile "worker-1" NX PX 5000
# Returns OK → I'll refresh. Returns nil → someone else is refreshing.

# Step 2a (lock acquired): Query DB and update cache
# ... query DB ...
SET cache:user:123:profile '{"name":"Alice",...}' EX 300
DEL lock:cache:user:123:profile
# Other waiting requests now get the fresh cache

# Step 2b (lock NOT acquired): Wait briefly and retry cache
# sleep(50ms)
GET cache:user:123:profile
# By now, the lock holder should have refreshed the cache
```

**Complete Stampede Prevention — Lua Script**:
```lua
-- KEYS[1] = cache:user:123:profile
-- KEYS[2] = lock:cache:user:123:profile
-- ARGV[1] = lock TTL (5000ms)
-- ARGV[2] = lock owner (worker UUID)

-- Try cache first
local cached = redis.call("GET", KEYS[1])
if cached then
    return cached
end

-- Cache miss → try to acquire lock
local locked = redis.call("SET", KEYS[2], ARGV[2], "NX", "PX", tonumber(ARGV[1]))
if locked then
    return "REFRESH"  -- Caller should query DB and SET cache
else
    return "WAIT"     -- Caller should retry after short delay
end
```

**Alternative: Probabilistic Early Expiration (Java)**:
```java
// Instead of fixed TTL, add jitter
ThreadLocalRandom rand = ThreadLocalRandom.current();
int ttl = 300 + rand.nextInt(-30, 31);  // 270-330 seconds
jedis.setex(key, ttl, value);
// Keys expire at different times → no simultaneous stampede

// Or: Refresh slightly before expiration
long remainingTTL = jedis.ttl("cache:user:123:profile");
if (remainingTTL < 30) {  // Less than 30 seconds left
    // Proactively refresh in background
    executor.submit(() -> refreshCache("cache:user:123:profile"));
}
```

**Time Complexity**: O(1).

---

### Problem 61: Multi-Key Atomic Operation (Lua)

**Scenario**: Transfer loyalty points between users. Must deduct from sender AND credit to receiver atomically. No partial transfers.

**Key Design**: `points:{user_id}`

```lua
-- KEYS[1] = points:sender:123
-- KEYS[2] = points:receiver:456
-- ARGV[1] = amount to transfer (500)

local sender_balance = tonumber(redis.call("GET", KEYS[1]) or "0")
local amount = tonumber(ARGV[1])

if sender_balance < amount then
    return cjson.encode({error = "INSUFFICIENT_POINTS", balance = sender_balance})
end

-- Atomic transfer
redis.call("DECRBY", KEYS[1], amount)
redis.call("INCRBY", KEYS[2], amount)

return cjson.encode({
    status = "OK",
    sender_balance = tonumber(redis.call("GET", KEYS[1])),
    receiver_balance = tonumber(redis.call("GET", KEYS[2]))
})
```

**Why not MULTI/EXEC?**:
```redis
# MULTI/EXEC CAN'T do conditional logic:
WATCH points:sender:123
val = GET points:sender:123
if val < 500: abort  # ← Can't do this inside MULTI!
MULTI
DECRBY points:sender:123 500
INCRBY points:receiver:456 500
EXEC
# EXEC returns nil if key changed since WATCH → retry needed
# Lua is simpler and faster (single round-trip)
```

**Redis Cluster Consideration**:
```redis
# Keys must be in same hash slot!
# Use hash tags:
EVAL "..." 2 {transfer:123-456}:sender {transfer:123-456}:receiver 500
# Both keys hash to same slot → works in cluster
```

**Time Complexity**: O(1) — a few O(1) commands in Lua.

---

### Problem 62: Idempotent Payment Processing

**Scenario**: Stripe webhook fires twice (network retry). Must process payment exactly once. High concurrency.

**Key Design**: `payment:processed:{payment_id}`, `payment:result:{payment_id}`

```lua
-- KEYS[1] = payment:processed:PAY-001
-- KEYS[2] = payment:result:PAY-001
-- ARGV[1] = payment amount
-- ARGV[2] = TTL (86400 = 24 hours)

-- Check if already processed
local result = redis.call("GET", KEYS[2])
if result then
    return result  -- Return cached result (idempotent!)
end

-- Try to claim this payment
local claimed = redis.call("SET", KEYS[1], "PROCESSING", "NX", "EX", tonumber(ARGV[2]))
if not claimed then
    -- Another worker is processing
    return cjson.encode({status = "IN_PROGRESS"})
end

-- We claimed it! Process payment.
-- (In real app, actual payment logic happens in application code)
-- For Redis, just mark as processing:
return cjson.encode({status = "PROCEED", payment_id = KEYS[1]})

-- After payment completes (in application code):
-- SET payment:result:PAY-001 '{"status":"SUCCESS","txn":"TXN-999"}' EX 86400
-- DEL payment:processed:PAY-001
```

**Application Flow**:
```
Request 1: EVAL lua → "PROCEED" → process payment → SET result → "SUCCESS"
Request 2: EVAL lua → returns cached "SUCCESS" (idempotent!)
Request 3: EVAL lua → returns cached "SUCCESS" (idempotent!)
```

**Time Complexity**: O(1).

---

### Problem 63: Concurrent Worker Tracking (ZSet Semaphore)

**Scenario**: Kubernetes pod auto-scaler needs to know how many workers are actively processing jobs. Workers register with heartbeat timestamps.

**Key Design**: `workers:active:{service}`

```redis
# Worker registers (score = heartbeat timestamp)
ZADD workers:active:payment-svc 1705312200 "worker:pod-abc"
ZADD workers:active:payment-svc 1705312201 "worker:pod-def"
ZADD workers:active:payment-svc 1705312202 "worker:pod-ghi"

# Count active workers
ZCARD workers:active:payment-svc
# Returns 3

# Worker sends heartbeat (update timestamp)
ZADD workers:active:payment-svc 1705312260 "worker:pod-abc"
# Updates score to current time

# Find dead workers (no heartbeat in 60 seconds)
ZRANGEBYSCORE workers:active:payment-svc -inf 1705312140
# Returns workers with heartbeat older than 60s ago

# Remove dead workers
ZREMRANGEBYSCORE workers:active:payment-svc -inf 1705312140

# Worker gracefully shuts down
ZREM workers:active:payment-svc "worker:pod-def"

# Auto-scaling decision:
ZCARD workers:active:payment-svc
# If < min_workers → scale up
# If > max_workers → scale down
```

**Time Complexity**: ZADD O(log N). ZCARD O(1). ZREMRANGEBYSCORE O(log N + M).

---

### Problem 64: Leader Election in Distributed System

**Scenario**: In a cluster of 5 app servers, exactly one should be the leader (for cron jobs, migrations, etc.). If leader dies, another takes over.

**Key Design**: `leader:{service}` with TTL renewal

```redis
# Server A tries to become leader
SET leader:scheduler "server-A" NX PX 10000
# Returns OK → Server A is leader!
# PX 10000 = 10 second lease

# Server B tries
SET leader:scheduler "server-B" NX PX 10000
# Returns nil → Server A is leader, B is follower

# Server A renews lease every 3 seconds (heartbeat)
# (Only renew if still the leader!)
```

**Leader Renewal — Lua Script**:
```lua
-- KEYS[1] = leader:scheduler
-- ARGV[1] = server identity (server-A)
-- ARGV[2] = lease TTL (10000ms)
local current_leader = redis.call("GET", KEYS[1])
if current_leader == ARGV[1] then
    -- I'm still the leader, renew lease
    redis.call("PEXPIRE", KEYS[1], tonumber(ARGV[2]))
    return "RENEWED"
elseif current_leader == false then
    -- No leader, try to claim
    local claimed = redis.call("SET", KEYS[1], ARGV[1], "NX", "PX", tonumber(ARGV[2]))
    if claimed then
        return "ELECTED"
    end
    return "LOST_ELECTION"
else
    return "FOLLOWER"  -- Someone else is leader
end
```

**Election Pattern**:
```
Every 3 seconds, each server runs:
  result = EVAL leader_lua "leader:scheduler" "server-X" 10000
  if result == "ELECTED" or result == "RENEWED":
      run_leader_tasks()  # Cron jobs, etc.
  else:
      standby()           # Do nothing, wait

If leader crashes → lease expires in 10 seconds
→ Next heartbeat from follower → "ELECTED"
→ New leader in < 13 seconds (10s lease + 3s heartbeat interval)
```

**Time Complexity**: O(1).

---

### Problem 65: Atomic Wallet Deduction (Lua Script)

**Scenario**: In-app purchase — deduct coins from user's wallet, grant item, log transaction. All-or-nothing.

**Key Design**: `wallet:{user_id}`, `inventory:{user_id}`, `txlog:{user_id}`

```lua
-- KEYS[1] = wallet:user:123
-- KEYS[2] = inventory:user:123
-- KEYS[3] = txlog:user:123
-- ARGV[1] = item cost (500)
-- ARGV[2] = item ID (sword_of_fire)
-- ARGV[3] = transaction ID (tx-uuid-abc)
-- ARGV[4] = timestamp

-- Idempotency check
local existing = redis.call("HGET", KEYS[2], ARGV[2])
-- (Optional: check if already purchased)

-- Check balance
local balance = tonumber(redis.call("GET", KEYS[1]) or "0")
local cost = tonumber(ARGV[1])

if balance < cost then
    return cjson.encode({error = "INSUFFICIENT_FUNDS", balance = balance})
end

-- Atomic: deduct + grant + log
redis.call("DECRBY", KEYS[1], cost)
redis.call("HSET", KEYS[2], ARGV[2], "1")  -- Grant item
redis.call("LPUSH", KEYS[3], cjson.encode({
    tx_id = ARGV[3],
    item = ARGV[2],
    cost = cost,
    ts = ARGV[4],
    balance_after = tonumber(redis.call("GET", KEYS[1]))
}))
redis.call("LTRIM", KEYS[3], 0, 99)  -- Keep last 100 transactions

return cjson.encode({
    status = "SUCCESS",
    balance = tonumber(redis.call("GET", KEYS[1])),
    item = ARGV[2]
})
```

**Time Complexity**: O(1) — several O(1) commands in Lua.

---

## 🔹 GEO & HYPERLOGLOG PROBLEMS (66–70)

---

### Problem 66: Driver Location Tracking (GEOADD)

**Scenario**: Uber tracks real-time location of all drivers. Update positions as drivers move.

**Why GEO?**: Built-in geospatial indexing using sorted sets. GEOADD O(log N). GEORADIUS for proximity search.

**Key Design**: `drivers:{city}`

```redis
# Drivers report their location
GEOADD drivers:NYC -73.9857 40.7484 "driver:100"   # Times Square
GEOADD drivers:NYC -73.9712 40.7831 "driver:200"   # Central Park
GEOADD drivers:NYC -74.0060 40.7128 "driver:300"   # Wall Street
GEOADD drivers:NYC -73.9857 40.7580 "driver:400"   # Midtown

# Update driver position (same command, overwrites)
GEOADD drivers:NYC -73.9900 40.7500 "driver:100"
# Driver 100 moved

# Get driver's position
GEOPOS drivers:NYC "driver:100"
# Returns [[-73.9900, 40.7500]]

# Remove driver (went offline)
ZREM drivers:NYC "driver:300"
# GEO uses ZSET internally, so ZREM works!

# Count active drivers in city
ZCARD drivers:NYC
# Returns 3
```

**Time Complexity**: GEOADD O(log N). GEOPOS O(1).

---

### Problem 67: Nearby Driver Search (GEORADIUS)

**Scenario**: Rider requests a ride from Times Square. Find nearest available drivers within 2km.

```redis
# Find drivers within 2km of rider's location
GEORADIUS drivers:NYC -73.9857 40.7484 2 km WITHCOORD WITHDIST COUNT 5 ASC
# Returns up to 5 nearest drivers, sorted by distance:
# [
#   ["driver:100", "0.18", [-73.9900, 40.7500]],   # 0.18 km away
#   ["driver:400", "1.07", [-73.9857, 40.7580]],   # 1.07 km away
# ]

# ASC = nearest first
# COUNT 5 = return max 5 results
# WITHCOORD = include coordinates
# WITHDIST = include distance

# Find drivers near another driver (for carpooling)
GEORADIUSBYMEMBER drivers:NYC "driver:100" 1 km WITHCOORD WITHDIST
# Find all drivers within 1km of driver:100

# Redis 6.2+: GEOSEARCH (newer, more flexible)
GEOSEARCH drivers:NYC FROMLONLAT -73.9857 40.7484 BYRADIUS 2 km ASC COUNT 5
# Same as GEORADIUS but with cleaner syntax

# Search within a box (rectangle) instead of circle
GEOSEARCH drivers:NYC FROMLONLAT -73.9857 40.7484 BYBOX 4 4 km ASC
```

**Time Complexity**: GEORADIUS O(N+log N) where N = elements in radius.

---

### Problem 68: Distance Calculation (GEODIST)

**Scenario**: Show rider the distance between their location and the assigned driver. ETA estimation.

```redis
# Add rider and driver locations
GEOADD locations -73.9857 40.7484 "rider:123"     # Times Square
GEOADD locations -73.9712 40.7831 "driver:456"    # Central Park

# Calculate distance
GEODIST locations "rider:123" "driver:456" km
# Returns "3.84" (kilometers)

GEODIST locations "rider:123" "driver:456" mi
# Returns "2.39" (miles)

GEODIST locations "rider:123" "driver:456" m
# Returns "3840" (meters)

# ETA estimation (pseudocode):
# distance_km = 3.84
# avg_speed_kmh = 30  (city traffic)
# eta_minutes = (distance_km / avg_speed_kmh) * 60
# eta = 7.68 minutes → "Arriving in ~8 min"
```

**Multi-Stop Distance — Lua Script**:
```lua
-- Calculate total route distance: A → B → C → D
-- KEYS[1] = locations key
-- ARGV = list of stop member names
local total = 0
for i = 1, #ARGV - 1 do
    local dist = redis.call("GEODIST", KEYS[1], ARGV[i], ARGV[i+1], "km")
    if dist then
        total = total + tonumber(dist)
    end
end
return tostring(total)
```

**Time Complexity**: GEODIST O(1).

---

### Problem 69: Unique Visitor Counting (HyperLogLog)

**Scenario**: Google Analytics counts unique visitors per page per day. With 100M+ visitors, SETs would use GBs of memory.

**Why HyperLogLog?**: Fixed 12KB memory regardless of cardinality. ~0.81% error rate. Perfect for approximate unique counting at scale.

**Key Design**: `hll:visitors:{page}:{date}`

```redis
# Track visitors (looks like SADD but uses ~0% of the memory)
PFADD hll:visitors:/home:2024-01-15 "user:123"
# Returns 1 → new unique

PFADD hll:visitors:/home:2024-01-15 "user:456"
# Returns 1 → new unique

PFADD hll:visitors:/home:2024-01-15 "user:123"
# Returns 0 → already counted (probabilistically)

# Approximate unique count
PFCOUNT hll:visitors:/home:2024-01-15
# Returns 2 (exact for small counts, ~0.81% error at scale)

# After 100M visitors:
PFCOUNT hll:visitors:/home:2024-01-15
# Returns ~99,187,432 (within 0.81% of actual)
# Memory used: 12KB (vs. ~1.6GB for SET)
```

**Multi-Period Merge**:
```redis
# Weekly unique visitors (merge daily HLLs)
PFMERGE hll:visitors:/home:week1 
    hll:visitors:/home:2024-01-15 
    hll:visitors:/home:2024-01-16 
    hll:visitors:/home:2024-01-17
    hll:visitors:/home:2024-01-18
    hll:visitors:/home:2024-01-19

PFCOUNT hll:visitors:/home:week1
# Returns approximate unique visitors across entire week
# Users who visited multiple days are deduplicated!
```

**SET vs HyperLogLog**:
```
100M unique visitors:
  SET:          ~1.6 GB memory, exact count, can list members
  HyperLogLog:  12 KB memory, ~0.81% error, cannot list members

For "how many unique?":    → HyperLogLog
For "was user X a visitor?": → SET (HLL can't check membership)
```

**Time Complexity**: PFADD O(1). PFCOUNT O(1). PFMERGE O(N).

---

### Problem 70: Cross-Server Visitor Merge (PFMERGE)

**Scenario**: CDN has 50 edge servers. Each tracks visitors locally with HyperLogLog. Need global unique visitor count.

**Key Design**: `hll:edge:{server}:{date}`, `hll:global:{date}`

```redis
# Each edge server tracks visitors locally
# Edge Server 1 (NYC):
PFADD hll:edge:nyc:2024-01-15 "user:100" "user:200" "user:300"

# Edge Server 2 (LAX):
PFADD hll:edge:lax:2024-01-15 "user:200" "user:400" "user:500"
# user:200 visited both NYC and LAX servers

# Edge Server 3 (LDN):
PFADD hll:edge:ldn:2024-01-15 "user:300" "user:600"

# Merge all edges into global count
PFMERGE hll:global:2024-01-15 
    hll:edge:nyc:2024-01-15 
    hll:edge:lax:2024-01-15 
    hll:edge:ldn:2024-01-15

# Global unique visitors
PFCOUNT hll:global:2024-01-15
# Returns ~5 (user:200 and user:300 deduplicated across servers)
# Memory: 12KB total regardless of visitor count

# Per-server counts
PFCOUNT hll:edge:nyc:2024-01-15  # → 3
PFCOUNT hll:edge:lax:2024-01-15  # → 3
PFCOUNT hll:edge:ldn:2024-01-15  # → 2
# Sum = 8, but global = 5 (3 duplicates removed!)
```

**Time Complexity**: PFMERGE O(N) where N = number of HLLs merged.

---

## 🔹 ADVANCED / SYSTEM DESIGN PROBLEMS (71–75)

---

### Problem 71: Flash Sale System (Atomic Stock Decrement)

**Scenario**: Amazon Prime Day — 1000 PlayStation 5 consoles, 500K concurrent buyers hitting "Buy Now" within seconds. Zero overselling.

**Architecture**:
```
Buyer → API Gateway → Rate Limiter (Redis) → Stock Check (Redis Lua) → Order Service → DB
```

**Complete Flash Sale — Lua Script**:
```lua
-- KEYS[1] = stock:ps5:prime_day        (remaining stock)
-- KEYS[2] = orders:ps5:prime_day       (set of successful buyers)
-- KEYS[3] = rate:user:{user_id}        (per-user rate limit)
-- ARGV[1] = user_id
-- ARGV[2] = quantity (1)
-- ARGV[3] = max_per_user (2)

-- Rate limit: max 5 attempts per minute
local attempts = redis.call("INCR", KEYS[3])
if attempts == 1 then
    redis.call("EXPIRE", KEYS[3], 60)
end
if attempts > 5 then
    return cjson.encode({error = "RATE_LIMITED"})
end

-- Check if user already bought max quantity
local user_orders = redis.call("HGET", KEYS[2], ARGV[1])
if user_orders and tonumber(user_orders) >= tonumber(ARGV[3]) then
    return cjson.encode({error = "MAX_PER_USER_REACHED"})
end

-- Check and decrement stock
local stock = tonumber(redis.call("GET", KEYS[1]) or "0")
local qty = tonumber(ARGV[2])

if stock < qty then
    return cjson.encode({error = "SOLD_OUT", stock = 0})
end

-- Success! Decrement stock and record order
redis.call("DECRBY", KEYS[1], qty)
redis.call("HINCRBY", KEYS[2], ARGV[1], qty)

return cjson.encode({
    status = "SUCCESS",
    remaining_stock = tonumber(redis.call("GET", KEYS[1])),
    user_total_purchased = tonumber(redis.call("HGET", KEYS[2], ARGV[1]))
})
```

**Setup**:
```redis
SET stock:ps5:prime_day 1000
# 1000 units available
```

**Time Complexity**: O(1) — entire purchase flow is atomic and constant time.

---

### Problem 72: Social Feed Ranking (ZSET + HASH)

**Scenario**: Instagram "Explore" feed — rank posts by engagement score. Score combines likes, comments, shares, recency.

**Key Design**: 
- `feed:explore` → ZSET (score = engagement, member = post_id)
- `post:{post_id}` → HASH (metadata)

```redis
# Calculate engagement score: likes*1 + comments*3 + shares*5 + recency_bonus
# Post gets a like:
ZINCRBY feed:explore 1 "post:42"

# Post gets a comment (worth 3x):
ZINCRBY feed:explore 3 "post:42"

# Post gets a share (worth 5x):
ZINCRBY feed:explore 5 "post:42"

# Post metadata
HSET post:42 author "alice" image_url "https://..." caption "Sunset!" 
    likes 1500 comments 230 shares 89 created_at 1705312200

# Get explore feed (top 20 posts)
ZREVRANGE feed:explore 0 19 WITHSCORES
# Returns: ["post:42", "842", "post:99", "710", ...]

# For each post, fetch metadata
HMGET post:42 author image_url caption likes
HMGET post:99 author image_url caption likes
# Pipeline all HMGET calls
```

**Time-Decayed Scoring — Lua Script**:
```lua
-- Score = engagement / (hours_since_post ^ 1.5)
-- Gravity algorithm (Hacker News style)
-- KEYS[1] = feed:explore
-- ARGV[1] = post_id
-- ARGV[2] = engagement_delta (1 for like, 3 for comment, etc.)
-- ARGV[3] = post_created_timestamp
-- ARGV[4] = current_timestamp

local engagement = redis.call("ZSCORE", KEYS[1], ARGV[1]) or 0
engagement = tonumber(engagement) + tonumber(ARGV[2])

local hours_old = (tonumber(ARGV[4]) - tonumber(ARGV[3])) / 3600
local gravity = 1.5
local score = engagement / math.pow(math.max(hours_old, 1), gravity)

redis.call("ZADD", KEYS[1], score, ARGV[1])
return tostring(score)
```

**Time Complexity**: ZINCRBY O(log N). ZREVRANGE O(log N + M).

---

### Problem 73: Real-Time Analytics Dashboard (Hash + INCRBY)

**Scenario**: Stripe dashboard shows real-time metrics: total revenue, transaction count, average amount, per-currency breakdown. Updates on every payment.

**Key Design**: `analytics:{metric}:{window}`

```redis
# On every payment ($99.99 USD):
HINCRBY analytics:2024-01-15 total_revenue_cents 9999
HINCRBY analytics:2024-01-15 transaction_count 1
HINCRBY analytics:2024-01-15 revenue_usd_cents 9999

# Per-currency breakdown
HINCRBY analytics:2024-01-15:currency usd_cents 9999
HINCRBY analytics:2024-01-15:currency eur_cents 0

# Per-hour granularity
HINCRBY analytics:2024-01-15:hour:14 revenue_cents 9999
HINCRBY analytics:2024-01-15:hour:14 count 1
EXPIRE analytics:2024-01-15:hour:14 172800  # Keep 2 days

# Read dashboard metrics
HGETALL analytics:2024-01-15
# Returns: {"total_revenue_cents":"5432100", "transaction_count":"543"}
# App converts: $54,321.00 from 543 transactions
# Average: $54321 / 543 = $100.04

# Per-hour chart data (for graph)
HMGET analytics:2024-01-15:hour:09 revenue_cents count
HMGET analytics:2024-01-15:hour:10 revenue_cents count
HMGET analytics:2024-01-15:hour:11 revenue_cents count
# Pipeline → build hourly revenue chart
```

**Real-Time Counter with Sliding Window — Lua Script**:
```lua
-- KEYS[1] = analytics:realtime (hash)
-- ARGV[1] = field prefix (revenue, count, etc.)
-- ARGV[2] = amount
-- ARGV[3] = current hour key
redis.call("HINCRBY", KEYS[1], ARGV[1] .. ":total", tonumber(ARGV[2]))
redis.call("HINCRBY", KEYS[1], ARGV[1] .. ":hour:" .. ARGV[3], tonumber(ARGV[2]))
return redis.call("HGET", KEYS[1], ARGV[1] .. ":total")
```

**Time Complexity**: O(1) per HINCRBY. O(N) for HGETALL.

---

### Problem 74: Reliable Delayed Task Scheduler (ZSET + Lua)

**Scenario**: Cron-like system — schedule recurring and one-shot tasks. Handle failures with retry. Prevent duplicate execution in multi-server setup.

**Architecture**:
```
┌──────────────┐      ZADD           ┌─────────────────────────┐
│ App Server    │ ──────────────────→ │ scheduler:pending       │
│ schedule()    │                     │ (ZSET, score=exec_time) │
└──────────────┘                     └───────────┬─────────────┘
                                                  │
┌──────────────┐   Lua: atomic poll  ┌───────────▼─────────────┐
│ Worker 1      │ ←─────────────────  │ ZRANGEBYSCORE -inf <now>│
│ Worker 2      │   (only ONE wins)   │ ZREM (claim job)        │
│ Worker 3      │                     │ ZADD processing         │
└──────┬───────┘                     └─────────────────────────┘
       │
       │ success → ZREM processing
       │ failure → ZADD pending (retry with backoff)
```

**Complete Scheduler — Lua Scripts**:

**1. Schedule a Job**:
```redis
ZADD scheduler:pending 1705406400 '{"id":"job-001","type":"send_email","payload":{"to":"alice@mail.com"},"retries":0,"max_retries":3}'
# Score = execution timestamp
```

**2. Atomic Claim (Worker polls every second)**:
```lua
-- KEYS[1] = scheduler:pending
-- KEYS[2] = scheduler:processing
-- ARGV[1] = current timestamp
-- ARGV[2] = worker ID
-- ARGV[3] = processing TTL (300 seconds)

-- Get due jobs
local jobs = redis.call("ZRANGEBYSCORE", KEYS[1], "-inf", ARGV[1], "LIMIT", 0, 1)
if #jobs == 0 then
    return nil
end

local job = jobs[1]
-- Atomic: remove from pending, add to processing
redis.call("ZREM", KEYS[1], job)
redis.call("ZADD", KEYS[2], tonumber(ARGV[1]) + tonumber(ARGV[3]), job)
return job
```

**3. Complete or Retry**:
```lua
-- On success:
-- KEYS[1] = scheduler:processing
-- ARGV[1] = job JSON
redis.call("ZREM", KEYS[1], ARGV[1])
return "COMPLETED"

-- On failure (in application code):
-- Parse job JSON, increment retries
-- If retries < max_retries:
--   Calculate next_run = now + (2^retries * 60)  -- exponential backoff
--   ZADD scheduler:pending <next_run> <updated_job_json>
--   ZREM scheduler:processing <old_job_json>
-- Else:
--   LPUSH scheduler:dead_letter <job_json>
--   ZREM scheduler:processing <old_job_json>
```

**4. Stuck Job Recovery (runs periodically)**:
```lua
-- KEYS[1] = scheduler:processing
-- KEYS[2] = scheduler:pending
-- ARGV[1] = current timestamp
-- Find jobs in processing longer than their TTL
local stuck = redis.call("ZRANGEBYSCORE", KEYS[1], "-inf", ARGV[1])
for _, job in ipairs(stuck) do
    redis.call("ZREM", KEYS[1], job)
    redis.call("ZADD", KEYS[2], ARGV[1], job)  -- Re-enqueue immediately
end
return #stuck
```

**Time Complexity**: O(log N) per job operation.

---

### Problem 75: Real-Time Geospatial Matchmaking (GEO + ZSet)

**Scenario**: Uber-like ride matching — rider requests a ride, system finds nearest available drivers, assigns the closest one who accepts.

**Architecture**:
```
Rider Request → Find Nearby Drivers (GEO) → Filter by Availability (SET) → 
    Rank by Rating (ZSET) → Send Offer → First Accept Wins (Lock)
```

**Key Design**:
- `drivers:location:{city}` → GEO (real-time positions)
- `drivers:available:{city}` → SET (currently available)
- `drivers:rating:{city}` → ZSET (rating scores)
- `ride:offers:{ride_id}` → Lock for acceptance

**Step 1: Rider requests ride**:
```redis
# Store rider's pickup location
GEOADD riders:pending -73.9857 40.7484 "ride:R001"
```

**Step 2: Find nearby available drivers**:
```lua
-- KEYS[1] = drivers:location:NYC (GEO)
-- KEYS[2] = drivers:available:NYC (SET)
-- KEYS[3] = drivers:rating:NYC (ZSET)
-- ARGV[1] = rider longitude (-73.9857)
-- ARGV[2] = rider latitude (40.7484)
-- ARGV[3] = radius (3 km)
-- ARGV[4] = max results (10)

-- Step 1: Find drivers within radius
local nearby = redis.call("GEORADIUS", KEYS[1], 
    ARGV[1], ARGV[2], ARGV[3], "km", 
    "WITHCOORD", "WITHDIST", "ASC", "COUNT", tonumber(ARGV[4]))

-- Step 2: Filter by availability
local candidates = {}
for _, driver_info in ipairs(nearby) do
    local driver_id = driver_info[1]
    local distance = driver_info[2]
    if redis.call("SISMEMBER", KEYS[2], driver_id) == 1 then
        -- Step 3: Get driver rating
        local rating = redis.call("ZSCORE", KEYS[3], driver_id) or "4.0"
        table.insert(candidates, {
            driver = driver_id,
            distance = distance,
            rating = tonumber(rating)
        })
    end
end

-- Step 4: Sort by composite score (distance * 0.6 + rating * 0.4)
-- Return top candidates
return cjson.encode(candidates)
```

**Step 3: Send offer and handle acceptance**:
```redis
# Mark driver as unavailable (prevent double-assignment)
SREM drivers:available:NYC "driver:100"

# Create ride offer with TTL (driver has 15 seconds to accept)
SET ride:offer:R001:driver:100 "PENDING" EX 15 NX

# Driver accepts:
SET ride:offer:R001:accepted "driver:100" NX EX 3600
# NX ensures only first accept wins (if sent to multiple drivers)

# If driver doesn't accept in 15 seconds:
# ride:offer:R001:driver:100 expires → try next driver
# Use keyspace notifications to detect expiration:
# SUBSCRIBE __keyevent@0__:expired
```

**Step 4: Ride lifecycle tracking**:
```redis
# Create ride record
HSET ride:R001 
    rider "rider:123"
    driver "driver:100"
    status "MATCHED"
    pickup_lat "40.7484"
    pickup_lon "-73.9857"
    matched_at "1705312200"

# Driver starts ride
HSET ride:R001 status "IN_PROGRESS" started_at "1705312500"

# Driver completes ride
HSET ride:R001 status "COMPLETED" completed_at "1705313100"
    dropoff_lat "40.7831" dropoff_lon "-73.9712"

# Return driver to available pool
SADD drivers:available:NYC "driver:100"

# Calculate fare (using GEO distance)
GEODIST locations "ride:R001:pickup" "ride:R001:dropoff" km
# Returns distance → calculate fare
```

**Complete Matchmaking Flow**:
```
1. Rider opens app → GEOADD rider location
2. Rider taps "Request Ride"
3. GEORADIUS → 10 nearest drivers within 3km
4. SISMEMBER → filter to available drivers (5 of 10)
5. ZSCORE → get ratings, sort by distance+rating score
6. SET NX → send offer to #1 driver (15s timeout)
7. Driver accepts → SET NX ride:accepted (first-wins)
8. SREM → mark driver unavailable
9. HSET → create ride record
10. Real-time tracking → GEOADD updates every 5 seconds
11. Ride complete → SADD driver back to available
```

**Time Complexity**: 
- GEORADIUS O(N+log N)
- SISMEMBER O(1) per check
- ZSCORE O(1) per lookup
- SET NX O(1) for locking
- Total matchmaking: O(N+log N) where N = nearby drivers
