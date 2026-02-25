# Redis Real-World Examples — Part 1: STRING & HASH Problems

## 🔹 STRING-BASED PROBLEMS (1–10)

---

### Problem 1: API Rate Limiter (INCR + EXPIRE)

**Scenario**: Instagram API allows 100 requests per minute per user. Reject requests that exceed the limit.

**Why Redis?**: Need atomic increment + per-key TTL. In-memory, O(1), handles millions of concurrent users.

**Key Design**: `rate:{user_id}:{minute_window}`

```redis
# User 123 makes a request at 2024-01-15 10:30:xx
INCR rate:user:123:202401151030
# First call → returns 1, key auto-created

# Set expiry only on first creation (60 seconds)
EXPIRE rate:user:123:202401151030 60
# After 60s, key auto-deletes → counter resets

# Check: if count > 100 → reject
GET rate:user:123:202401151030
# Returns "47" → allow
# Returns "101" → reject with 429 Too Many Requests
```

**Race Condition Fix — Lua Script (Atomic INCR + EXPIRE)**:
```lua
-- KEYS[1] = rate:user:123:202401151030
-- ARGV[1] = 100 (limit)
-- ARGV[2] = 60  (window in seconds)
local current = redis.call("INCR", KEYS[1])
if current == 1 then
    redis.call("EXPIRE", KEYS[1], tonumber(ARGV[2]))
end
if current > tonumber(ARGV[1]) then
    return 0  -- REJECTED
end
return 1      -- ALLOWED
```

**Why Lua?**: Without Lua, there's a race between INCR and EXPIRE. If the process crashes after INCR but before EXPIRE, the key never expires → permanent block.

**Time Complexity**: O(1) per request.

**Interview Follow-up**: 
- "What about distributed rate limiting across multiple Redis instances?" → Use Redis Cluster with hash tags: `{user:123}:rate`
- "Sliding window alternative?" → Use SORTED SET (Problem 48 in Part 3)

---

### Problem 2: Session Token Storage (SETEX)

**Scenario**: After login, store a session token that expires in 1 hour. Used by Netflix, Spotify, any auth system.

**Why Redis?**: Session lookups must be < 1ms. SETEX atomically sets value + TTL. No background cleanup needed.

**Key Design**: `session:{token}`

```redis
# After successful login, generate token and store
SETEX session:abc123def456 3600 '{"user_id":123,"role":"premium","ip":"1.2.3.4"}'
# Key = session:abc123def456
# TTL = 3600 seconds (1 hour)
# Value = JSON payload

# On every API request, validate session
GET session:abc123def456
# Returns JSON → valid session
# Returns nil → expired or invalid → redirect to login

# User logs out → immediately invalidate
DEL session:abc123def456

# Check remaining time
TTL session:abc123def456
# Returns 2847 → ~47 minutes left

# Extend session on activity (sliding expiration)
EXPIRE session:abc123def456 3600
# Reset TTL to 1 hour from now
```

**Production Pattern — Session with User Index**:
```redis
# Store session
SETEX session:token123 3600 '{"user_id":123}'

# Also track which sessions belong to a user (for "logout all devices")
SADD user:123:sessions token123
EXPIRE user:123:sessions 86400

# Logout all devices:
SMEMBERS user:123:sessions
# → ["token123", "token456", "token789"]
# DEL session:token123 session:token456 session:token789
# DEL user:123:sessions
```

**Time Complexity**: O(1) for all operations.

---

### Problem 3: OTP Code Generation with Expiry (SETEX)

**Scenario**: WhatsApp/Uber sends a 6-digit OTP that expires in 5 minutes. Max 3 attempts.

**Key Design**: `otp:{phone}` for code, `otp:attempts:{phone}` for attempt counter

```redis
# Generate and store OTP
SETEX otp:+14155551234 300 "847293"
# Expires in 300 seconds (5 minutes)

# Track attempts (max 3)
SETEX otp:attempts:+14155551234 300 "0"

# User submits OTP for verification:
# Step 1: Check attempts
GET otp:attempts:+14155551234
# Returns "2" → under limit, proceed

# Step 2: Increment attempt counter
INCR otp:attempts:+14155551234

# Step 3: Verify code
GET otp:+14155551234
# Returns "847293" → compare with user input

# Step 4a: Correct → clean up
DEL otp:+14155551234 otp:attempts:+14155551234

# Step 4b: Wrong + attempts >= 3 → lock out
DEL otp:+14155551234
SETEX otp:lockout:+14155551234 900 "locked"
# 15-minute lockout
```

**Atomic Verification — Lua Script**:
```lua
-- KEYS[1] = otp:+14155551234
-- KEYS[2] = otp:attempts:+14155551234
-- ARGV[1] = user-submitted code
-- ARGV[2] = max attempts (3)
local attempts = tonumber(redis.call("INCR", KEYS[2]))
if attempts > tonumber(ARGV[2]) then
    redis.call("DEL", KEYS[1], KEYS[2])
    return -1  -- LOCKED OUT
end
local stored = redis.call("GET", KEYS[1])
if stored == ARGV[1] then
    redis.call("DEL", KEYS[1], KEYS[2])
    return 1   -- VERIFIED
end
return 0       -- WRONG CODE, tries remaining
```

**Time Complexity**: O(1) per operation.

---

### Problem 4: Duplicate Payment Prevention (SETNX)

**Scenario**: Stripe/Razorpay must ensure a payment is processed exactly once, even if the client retries.

**Why Redis?**: SETNX is atomic — only the first caller wins. Perfect for idempotency keys.

**Key Design**: `payment:idempotency:{idempotency_key}`

```redis
# Client sends payment with idempotency key
SETNX payment:idempotency:pay_abc123 "PROCESSING"
# Returns 1 → first time, proceed with payment
# Returns 0 → duplicate! Return cached result

# After successful payment, store result with TTL
SET payment:idempotency:pay_abc123 '{"status":"SUCCESS","txn_id":"txn_789"}' EX 86400
# Keep for 24 hours for client retries

# Client retries same payment:
GET payment:idempotency:pay_abc123
# Returns '{"status":"SUCCESS","txn_id":"txn_789"}'
# → Return this cached result, don't charge again
```

**Production-Grade — Lua Script with State Machine**:
```lua
-- KEYS[1] = payment:idempotency:pay_abc123
-- ARGV[1] = TTL (86400)
local state = redis.call("GET", KEYS[1])
if state == false then
    -- First time: claim this payment
    redis.call("SET", KEYS[1], "PROCESSING", "EX", tonumber(ARGV[1]), "NX")
    return "PROCEED"
elseif state == "PROCESSING" then
    -- Another thread is processing
    return "IN_PROGRESS"
else
    -- Already completed, return cached result
    return state
end
```

**Time Complexity**: O(1).

**Interview Follow-up**:
- "What if the server crashes after SETNX but before payment?" → Use PROCESSING state + TTL. Background worker checks for stale PROCESSING entries.
- "What about distributed systems?" → Idempotency key stored in Redis, actual payment state in DB. Two-phase approach.

---

### Problem 5: Page View Counter (INCR)

**Scenario**: Medium/Dev.to tracks page views per article in real-time. Dashboard shows live counts.

**Why Redis?**: INCR is atomic and O(1). Can handle 100K+ increments/sec per key. Way faster than UPDATE count=count+1 in SQL.

**Key Design**: `views:{entity}:{id}` or `views:{entity}:{id}:{date}`

```redis
# User views article 456
INCR views:article:456
# Returns 10001 → 10,001st view

# Track daily views separately (for analytics)
INCR views:article:456:2024-01-15
EXPIRE views:article:456:2024-01-15 604800
# Keep daily counters for 7 days

# Get current view count
GET views:article:456
# Returns "10001"

# Batch fetch multiple article views
MGET views:article:456 views:article:789 views:article:101
# Returns ["10001", "5432", "876"]

# Periodically sync to database (write-behind pattern)
# Background job every 5 minutes:
# 1. GET views:article:456  → "10500"
# 2. UPDATE articles SET views = 10500 WHERE id = 456
```

**Unique View Tracking (with SET)**:
```redis
# Only count unique viewers
SADD views:article:456:unique user:789
# Returns 1 → new viewer, also INCR views:article:456
# Returns 0 → already viewed, skip increment

SCARD views:article:456:unique
# Returns 8432 → 8,432 unique viewers
```

**Time Complexity**: O(1) per increment. O(N) for MGET with N keys.

---

### Problem 6: Flash Sale Stock Decrement (DECR)

**Scenario**: Amazon Lightning Deal — 100 units, 50,000 concurrent buyers. Must never oversell.

**Why Redis?**: DECR is atomic. Single-threaded model guarantees no race conditions. Way faster than DB row locks.

**Key Design**: `stock:{product_id}:{sale_id}`

```redis
# Admin sets up flash sale: 100 units
SET stock:product:999:sale:55 100

# Buyer attempts purchase:
DECR stock:product:999:sale:55
# Returns 99 → success (positive = stock available)
# Returns 0  → last unit, success
# Returns -1 → oversold! Must reject and rollback

# Better approach — check before decrement:
GET stock:product:999:sale:55
# Returns "0" → sold out, reject immediately (save a DECR)
```

**Production-Grade — Lua Script (No Overselling)**:
```lua
-- KEYS[1] = stock:product:999:sale:55
-- ARGV[1] = quantity requested (usually 1)
local stock = tonumber(redis.call("GET", KEYS[1]))
if stock == nil then
    return -1  -- sale not found
end
if stock < tonumber(ARGV[1]) then
    return 0   -- insufficient stock
end
local remaining = redis.call("DECRBY", KEYS[1], tonumber(ARGV[1]))
return remaining  -- positive = success
```

**Why Lua over plain DECR?**: Plain DECR can go negative (race between GET and DECR). Lua script is atomic — read + check + decrement happens as one operation.

**Time Complexity**: O(1).

**Interview Follow-up**:
- "What about inventory held in cart but not purchased?" → Use TTL on reservation: `SET reserved:user:123:product:999 1 EX 600` (10-min hold)
- "Scale beyond single Redis?" → Shard stock across Redis Cluster nodes with hash tags

---

### Problem 7: User Login Count Tracker (INCRBY)

**Scenario**: LinkedIn tracks how many times each user has logged in (for security alerts, engagement metrics).

**Key Design**: `metrics:logins:{user_id}` or use HASH for multiple metrics

```redis
# User logs in
INCRBY metrics:logins:user:123 1
# Returns 47 → 47th login

# Could also track per-day
INCRBY metrics:logins:user:123:2024-01-15 1
EXPIRE metrics:logins:user:123:2024-01-15 2592000
# Keep for 30 days

# Security: detect suspicious login frequency
GET metrics:logins:user:123:2024-01-15
# Returns "15" → 15 logins today? Flag for review!

# Better approach: HASH for multiple metrics per user
HINCRBY user:123:metrics login_count 1
HINCRBY user:123:metrics failed_login_count 0
HGET user:123:metrics login_count
# Returns "47"
```

**Time Complexity**: O(1).

---

### Problem 8: Database Query Result Cache with TTL

**Scenario**: Twitter caches user timeline query results. DB query takes 200ms, cache lookup takes 1ms.

**Why Redis?**: Eliminates repeated expensive DB queries. SET + EX for auto-expiry. No manual cache cleanup.

**Key Design**: `cache:{query_hash}` or `cache:{entity}:{id}:{view}`

```redis
# Cache-aside pattern:

# Step 1: Check cache
GET cache:timeline:user:123
# Returns nil → cache miss

# Step 2: Query database (expensive)
# SELECT * FROM tweets WHERE user_id IN (friends_of_123) ORDER BY created_at DESC LIMIT 50

# Step 3: Store result in cache with 5-minute TTL
SET cache:timeline:user:123 '[{"tweet_id":1,"text":"Hello"}, ...]' EX 300

# Next request:
GET cache:timeline:user:123
# Returns cached JSON → 1ms instead of 200ms

# Cache invalidation on new tweet:
DEL cache:timeline:user:123
# Next read will refresh from DB

# Batch cache population (prewarming)
MSET cache:timeline:user:123 '[...]' cache:timeline:user:456 '[...]'
# Set TTL separately
EXPIRE cache:timeline:user:123 300
EXPIRE cache:timeline:user:456 300
```

**Cache-aside with Lua (Atomic Read-or-Populate)**:
```lua
-- KEYS[1] = cache:timeline:user:123
-- ARGV[1] = TTL (300)
local cached = redis.call("GET", KEYS[1])
if cached then
    return cached
end
-- Return nil, let application query DB and SET
return nil
```

**Time Complexity**: O(1) for GET/SET. O(N) for MSET.

**Interview Follow-up**:
- "How to prevent cache stampede?" → Distributed lock (see Problem 60)
- "How to keep cache consistent with DB?" → Write-through or event-driven invalidation

---

### Problem 9: Temporary Auth Token for Password Reset

**Scenario**: Gmail sends a password reset link with a token that expires in 15 minutes. Single use only.

**Key Design**: `reset:{token}` → user_id

```redis
# User requests password reset
# Generate cryptographically random token
SET reset:a7f3b9c2e1d4 '{"user_id":123,"email":"alice@gmail.com"}' EX 900 NX
# EX 900 = 15 minutes
# NX = only set if not exists (prevent token collision)
# Returns OK → token stored
# Returns nil → token collision (astronomically unlikely)

# User clicks reset link: GET token
GET reset:a7f3b9c2e1d4
# Returns '{"user_id":123,"email":"alice@gmail.com"}' → valid, show reset form
# Returns nil → expired or already used → "Link expired"

# User submits new password:
# Step 1: Atomic get-and-delete (single use!)
GETDEL reset:a7f3b9c2e1d4
# Returns value AND deletes key → can't be reused
# (Redis 6.2+; for older versions, use Lua)

# Fallback for Redis < 6.2:
```

**Atomic Single-Use Token — Lua Script**:
```lua
-- KEYS[1] = reset:a7f3b9c2e1d4
local token_data = redis.call("GET", KEYS[1])
if token_data then
    redis.call("DEL", KEYS[1])
    return token_data  -- valid, consumed
end
return nil  -- expired or already used
```

**Time Complexity**: O(1).

---

### Problem 10: Atomic Multi-Step Update (Lua Script)

**Scenario**: Uber ride completion: deduct rider balance, credit driver balance, update ride status — all atomically.

**Why Lua?**: Three separate Redis commands aren't atomic. Another request could see partial state (money deducted but not credited). Lua runs atomically on Redis's single thread.

**Key Design**: `wallet:{user_id}`, `ride:{ride_id}`

```lua
-- KEYS[1] = wallet:rider:123     (rider balance)
-- KEYS[2] = wallet:driver:456    (driver balance)
-- KEYS[3] = ride:ride:789        (ride status)
-- ARGV[1] = 25.50                (fare amount)
-- ARGV[2] = "COMPLETED"          (new status)

-- Step 1: Check rider has sufficient balance
local rider_balance = tonumber(redis.call("GET", KEYS[1]) or "0")
local fare = tonumber(ARGV[1])

if rider_balance < fare then
    return cjson.encode({error = "INSUFFICIENT_BALANCE", balance = rider_balance})
end

-- Step 2: Atomic transfer (all-or-nothing)
redis.call("DECRBY", KEYS[1], fare * 100)       -- deduct rider (store in cents)
redis.call("INCRBY", KEYS[2], fare * 100)       -- credit driver
redis.call("HSET", KEYS[3], "status", ARGV[2])  -- update ride status
redis.call("HSET", KEYS[3], "completed_at", redis.call("TIME")[1])

return cjson.encode({
    status = "SUCCESS",
    rider_balance = tonumber(redis.call("GET", KEYS[1])),
    driver_balance = tonumber(redis.call("GET", KEYS[2]))
})
```

**Calling from Application**:
```redis
EVAL "<lua script above>" 3 wallet:rider:123 wallet:driver:456 ride:ride:789 25.50 COMPLETED
```

**Why not MULTI/EXEC?**: MULTI/EXEC can't read a value and make decisions based on it. The `if rider_balance < fare` check requires Lua.

**Time Complexity**: O(1) — just a few O(1) commands executed atomically.

**Interview Follow-up**:
- "What about Redis Cluster?" → All keys must be in same hash slot. Use hash tags: `{ride:789}:rider_wallet`, `{ride:789}:driver_wallet`, `{ride:789}:status`
- "What if we need DB persistence too?" → Redis for real-time state, async write to DB for durability.

---

## 🔹 HASH-BASED PROBLEMS (11–20)

---

### Problem 11: User Profile Attribute Store

**Scenario**: Instagram stores user profiles — name, bio, follower count, post count, account type. Need fast reads and individual field updates.

**Why Hash over JSON String?**: Can read/update individual fields without deserializing the entire object. HINCRBY for counters. ~10x more memory-efficient for small objects.

**Key Design**: `user:{user_id}`

```redis
# Create user profile
HSET user:123 
    username "alice_dev" 
    display_name "Alice Johnson" 
    bio "Software Engineer @Google" 
    followers 15420 
    following 892 
    posts 347 
    account_type "verified" 
    created_at "2020-03-15"

# Read single field
HGET user:123 username
# Returns "alice_dev"    → O(1)

# Read multiple specific fields
HMGET user:123 username followers posts
# Returns ["alice_dev", "15420", "347"]    → O(N) where N = fields requested

# Read entire profile
HGETALL user:123
# Returns all field-value pairs    → O(N) where N = total fields

# Update single field
HSET user:123 bio "Staff Engineer @Google"
# Only updates bio, other fields untouched    → O(1)

# New follower → atomic increment
HINCRBY user:123 followers 1
# Returns 15421    → O(1), no read-modify-write needed!

# New post
HINCRBY user:123 posts 1

# Unfollow → atomic decrement
HINCRBY user:123 followers -1
# Returns 15420

# Check if field exists
HEXISTS user:123 verified_email
# Returns 0 (not set) or 1 (exists)
```

**Time Complexity**: O(1) per field operation. O(N) for HGETALL.

**Interview Follow-up**: "When would you NOT use a Hash?"
- If you need TTL on individual fields (Redis doesn't support per-field TTL)
- If the object has > 128 fields or fields > 64 bytes (loses ziplist optimization)
- If you always read/write the entire object → just use STRING with JSON

---

### Problem 12: Shopping Cart per User (HSET)

**Scenario**: Amazon shopping cart — add items, update quantities, remove items, get total items. Cart persists across sessions.

**Why Hash?**: Each user's cart is one key. Fields = product SKUs, values = quantities. Atomic quantity updates with HINCRBY.

**Key Design**: `cart:{user_id}`

```redis
# Add item to cart
HSET cart:user:123 sku:LAPTOP-001 1
HSET cart:user:123 sku:MOUSE-042 2
HSET cart:user:123 sku:KEYBOARD-007 1

# Increase quantity
HINCRBY cart:user:123 sku:MOUSE-042 1
# Returns 3 → now 3 mice in cart

# Decrease quantity
HINCRBY cart:user:123 sku:MOUSE-042 -1
# Returns 2

# Remove item entirely
HDEL cart:user:123 sku:KEYBOARD-007
# Returns 1 (removed)

# Get all items in cart
HGETALL cart:user:123
# Returns {"sku:LAPTOP-001": "1", "sku:MOUSE-042": "2"}

# Count distinct items
HLEN cart:user:123
# Returns 2

# Check if specific item is in cart
HEXISTS cart:user:123 sku:LAPTOP-001
# Returns 1 (yes)

# Get quantity of specific item
HGET cart:user:123 sku:LAPTOP-001
# Returns "1"

# Set cart expiry (abandon after 7 days of inactivity)
EXPIRE cart:user:123 604800
```

**Cart Merge (guest → logged-in) — Lua Script**:
```lua
-- KEYS[1] = cart:guest:session123 (guest cart)
-- KEYS[2] = cart:user:123 (user cart)
local guest_items = redis.call("HGETALL", KEYS[1])
for i = 1, #guest_items, 2 do
    local sku = guest_items[i]
    local qty = tonumber(guest_items[i+1])
    redis.call("HINCRBY", KEYS[2], sku, qty)
end
redis.call("DEL", KEYS[1])
return redis.call("HLEN", KEYS[2])
```

**Time Complexity**: O(1) per item operation. O(N) for HGETALL.

---

### Problem 13: Per-User Metrics Increment (HINCRBY)

**Scenario**: Spotify tracks per-user metrics: songs played, playlists created, hours listened, skips. Need atomic increments and fast reads for dashboard.

**Key Design**: `metrics:user:{user_id}`

```redis
# User plays a song
HINCRBY metrics:user:123 songs_played 1
HINCRBY metrics:user:123 minutes_listened 3
# Both atomic, no race conditions

# User skips a song
HINCRBY metrics:user:123 skips 1

# User creates playlist
HINCRBY metrics:user:123 playlists_created 1

# Fetch dashboard metrics
HMGET metrics:user:123 songs_played minutes_listened skips playlists_created
# Returns ["1547", "4822", "234", "12"]

# Reset monthly metrics (new billing cycle)
HDEL metrics:user:123 songs_played minutes_listened skips
# Or delete and recreate:
DEL metrics:user:123

# Float increment for hours (use string key instead)
# Redis HASH only supports integer increment
# Workaround: store minutes as integer, convert in app
HINCRBY metrics:user:123 seconds_listened 180
# App converts: 180 seconds = 3 minutes
```

**Batch Update — Pipeline**:
```redis
# Pipeline (not atomic, but reduces round trips)
HINCRBY metrics:user:123 songs_played 1
HINCRBY metrics:user:123 minutes_listened 4
HINCRBY metrics:user:123 genre:rock 1
HINCRBY metrics:user:123 artist:queen 1
# 4 commands, 1 round trip with pipelining
```

**Time Complexity**: O(1) per HINCRBY. O(N) for HMGET.

---

### Problem 14: Outdated Field Cleanup (HDEL)

**Scenario**: User profile has deprecated fields (old_email, legacy_plan, temp_flag) that need cleanup during migration. Remove specific fields without touching the rest.

**Key Design**: `user:{user_id}`

```redis
# Before cleanup
HGETALL user:123
# Returns: username, email, old_email, plan, legacy_plan, temp_migration_flag, ...

# Remove single deprecated field
HDEL user:123 old_email
# Returns 1 (removed) or 0 (didn't exist)

# Remove multiple deprecated fields at once
HDEL user:123 legacy_plan temp_migration_flag deprecated_avatar_url
# Returns 3 (number of fields actually removed)

# Verify cleanup
HEXISTS user:123 old_email
# Returns 0 → confirmed removed

# Batch cleanup across many users — Lua Script
```

**Batch Migration Cleanup — Lua Script**:
```lua
-- KEYS[1..N] = user keys to clean
-- ARGV[1..M] = fields to remove
local cleaned = 0
for i = 1, #KEYS do
    for j = 1, #ARGV do
        cleaned = cleaned + redis.call("HDEL", KEYS[i], ARGV[j])
    end
end
return cleaned
```

**Production Approach (SCAN-based)**:
```redis
# Don't use KEYS user:* in production!
# Use SCAN to iterate safely:
SCAN 0 MATCH user:* COUNT 100
# For each key returned:
HDEL <key> old_email legacy_plan temp_migration_flag
```

**Time Complexity**: O(1) per HDEL per field. O(N) for SCAN iteration.

---

### Problem 15: Bulk User Attribute Fetch (HGETALL)

**Scenario**: Admin dashboard shows user details. Need to load complete profile in one call. Also need to display a list of users with specific fields only.

**Key Design**: `user:{user_id}`

```redis
# Full profile (admin dashboard)
HGETALL user:123
# Returns ALL fields and values:
# ["username", "alice", "email", "alice@gmail.com", "plan", "premium", 
#  "followers", "15420", "posts", "347", "created_at", "2020-03-15"]

# Selective fields (user card in search results)
HMGET user:123 username display_name avatar_url followers
# Returns ["alice_dev", "Alice Johnson", "https://...", "15420"]
# Only fetches what you need → less bandwidth

# Multiple users' specific fields (user list page)
# Pipeline approach:
HMGET user:123 username avatar_url followers
HMGET user:456 username avatar_url followers
HMGET user:789 username avatar_url followers
# 3 commands, 1 round trip with pipelining

# Count total fields in profile
HLEN user:123
# Returns 8
```

**When NOT to use HGETALL**:
- Hash has 1000+ fields → use HSCAN instead
- You only need 2 out of 50 fields → use HMGET
- HGETALL is O(N) — fine for small hashes, expensive for large ones

```redis
# Safe iteration for large hashes
HSCAN user:123 0 MATCH email* COUNT 10
# Incrementally scan fields matching pattern
```

**Time Complexity**: O(N) for HGETALL. O(N) for HMGET where N = requested fields.

---

### Problem 16: Leaderboard with User Metadata (Hash + Sorted Set)

**Scenario**: Fortnite leaderboard shows rank, score, username, avatar, and country. ZSET stores rank/score, HASH stores metadata.

**Why two structures?**: ZSET is perfect for ranking but only stores member+score. HASH stores the rich user data. Combined, they give you a complete leaderboard.

**Key Design**: 
- `leaderboard:season:5` → ZSET (score → user_id)
- `user:{user_id}` → HASH (metadata)

```redis
# Player scores a victory
ZADD leaderboard:season:5 2450 user:123
ZINCRBY leaderboard:season:5 50 user:123
# Score is now 2500

# Get top 10 with scores
ZREVRANGE leaderboard:season:5 0 9 WITHSCORES
# Returns: ["user:789", "3200", "user:456", "2900", "user:123", "2500", ...]

# For each user in top 10, fetch display info
HMGET user:789 username avatar_url country
HMGET user:456 username avatar_url country
HMGET user:123 username avatar_url country
# Pipeline all HMGET calls for efficiency

# Get specific player's rank
ZREVRANK leaderboard:season:5 user:123
# Returns 2 (0-based, so 3rd place)

# Get specific player's score
ZSCORE leaderboard:season:5 user:123
# Returns "2500"
```

**Complete Leaderboard Fetch — Lua Script**:
```lua
-- KEYS[1] = leaderboard:season:5
-- ARGV[1] = start (0)
-- ARGV[2] = stop (9)  -- top 10
local results = {}
local members = redis.call("ZREVRANGE", KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[2]), "WITHSCORES")
for i = 1, #members, 2 do
    local user_key = members[i]
    local score = members[i+1]
    local rank = (tonumber(ARGV[1]) + (i-1)/2)
    local name = redis.call("HGET", user_key, "username") or "Unknown"
    local avatar = redis.call("HGET", user_key, "avatar_url") or ""
    table.insert(results, cjson.encode({
        rank = rank + 1,
        user = user_key,
        score = tonumber(score),
        name = name,
        avatar = avatar
    }))
end
return results
```

**Time Complexity**: ZREVRANGE O(log N + M). Each HMGET O(K) where K = fields.

---

### Problem 17: Per-Product Inventory Tracking

**Scenario**: Walmart tracks inventory per warehouse. Each warehouse has hundreds of SKUs. Need fast stock checks and atomic decrements during checkout.

**Why Hash?**: One key per warehouse, fields = SKUs. HINCRBY for atomic stock updates. HGETALL for full inventory snapshot.

**Key Design**: `inventory:{warehouse_id}`

```redis
# Set initial inventory for warehouse
HSET inventory:warehouse:NYC-1 
    sku:LAPTOP-001 150 
    sku:MOUSE-042 500 
    sku:KEYBOARD-007 300 
    sku:MONITOR-019 75

# Check stock for specific item
HGET inventory:warehouse:NYC-1 sku:LAPTOP-001
# Returns "150"

# Decrement on purchase (atomic!)
HINCRBY inventory:warehouse:NYC-1 sku:LAPTOP-001 -1
# Returns 149

# Restock from supplier
HINCRBY inventory:warehouse:NYC-1 sku:LAPTOP-001 50
# Returns 199

# Check which items are in stock
HGETALL inventory:warehouse:NYC-1
# Returns all SKU → quantity pairs

# Find total unique products in warehouse
HLEN inventory:warehouse:NYC-1
# Returns 4

# Cross-warehouse stock check (pipeline)
HGET inventory:warehouse:NYC-1 sku:LAPTOP-001
HGET inventory:warehouse:LAX-2 sku:LAPTOP-001
HGET inventory:warehouse:CHI-3 sku:LAPTOP-001
# Pipeline → total stock across warehouses
```

**Safe Checkout — Lua Script (No Overselling)**:
```lua
-- KEYS[1] = inventory:warehouse:NYC-1
-- ARGV[1] = sku:LAPTOP-001
-- ARGV[2] = 1 (quantity)
local stock = tonumber(redis.call("HGET", KEYS[1], ARGV[1]) or "0")
if stock < tonumber(ARGV[2]) then
    return 0  -- out of stock
end
return redis.call("HINCRBY", KEYS[1], ARGV[1], -tonumber(ARGV[2]))
```

**Time Complexity**: O(1) per item. O(N) for HGETALL.

---

### Problem 18: Feature Flags per User

**Scenario**: Netflix rolls out features gradually. Each user has flags: dark_mode, new_player, beta_search. Toggle instantly without deploy.

**Why Hash?**: One key per user, one field per feature. HSET to toggle. HGET to check. No app restart needed.

**Key Design**: `flags:{user_id}`

```redis
# Set feature flags for user
HSET flags:user:123 dark_mode 1 new_player 0 beta_search 1 ai_recommendations 1

# Check single flag
HGET flags:user:123 dark_mode
# Returns "1" → enabled

# Toggle a feature
HSET flags:user:123 new_player 1
# Instantly enabled for this user

# Disable a feature
HSET flags:user:123 beta_search 0

# Get all flags for user (on app load)
HGETALL flags:user:123
# Returns {"dark_mode":"1", "new_player":"1", "beta_search":"0", "ai_recommendations":"1"}

# Remove a feature flag entirely (feature graduated)
HDEL flags:user:123 beta_search

# Check if flag exists
HEXISTS flags:user:123 deprecated_feature
# Returns 0 → flag not set, use default
```

**Bulk Feature Rollout — Lua Script**:
```lua
-- Enable "new_player" for users 100-200
-- KEYS = user flag keys
-- ARGV[1] = feature name
-- ARGV[2] = value (1 or 0)
local updated = 0
for i = 1, #KEYS do
    redis.call("HSET", KEYS[i], ARGV[1], ARGV[2])
    updated = updated + 1
end
return updated
```

**Global Flag (applies to all users)**:
```redis
# Global flags checked first, then per-user override
HSET flags:global dark_mode 0 new_player 0 beta_search 0

# Per-user override
HSET flags:user:123 dark_mode 1
# User 123 gets dark_mode even though global is off

# Check logic (in app):
# 1. HGET flags:user:123 dark_mode → "1" → return true
# 2. If nil → HGET flags:global dark_mode → "0" → return false
```

**Time Complexity**: O(1) per flag. O(N) for HGETALL.

---

### Problem 19: Multi-Tenant Configuration Store

**Scenario**: SaaS platform (like Slack, Notion) stores per-tenant settings: max_users, storage_limit, plan_type, custom_domain. Fast lookups on every API request.

**Why Hash?**: One key per tenant. Fields = config parameters. Atomic updates. No full object serialization.

**Key Design**: `config:tenant:{tenant_id}`

```redis
# Set tenant configuration
HSET config:tenant:acme-corp 
    plan "enterprise" 
    max_users 500 
    storage_gb 1000 
    custom_domain "app.acme.com" 
    sso_enabled 1 
    mfa_required 1 
    api_rate_limit 10000 
    data_retention_days 365

# Check rate limit on every API request
HGET config:tenant:acme-corp api_rate_limit
# Returns "10000" → O(1), sub-millisecond

# Upgrade tenant plan
HSET config:tenant:acme-corp plan "enterprise-plus" max_users 1000 storage_gb 5000

# Check multiple configs at once
HMGET config:tenant:acme-corp plan max_users sso_enabled
# Returns ["enterprise-plus", "1000", "1"]

# Track usage alongside config
HSET usage:tenant:acme-corp current_users 342 storage_used_gb 456

# Check if tenant is over limit
# App: HGET config → max_users = 1000, HGET usage → current_users = 342 → OK
```

**Tenant Provisioning — Lua Script**:
```lua
-- Create tenant with default config
-- KEYS[1] = config:tenant:new-tenant-id
-- ARGV = plan defaults
local exists = redis.call("EXISTS", KEYS[1])
if exists == 1 then
    return "TENANT_EXISTS"
end
redis.call("HSET", KEYS[1], 
    "plan", "free",
    "max_users", "5",
    "storage_gb", "1",
    "api_rate_limit", "100",
    "sso_enabled", "0",
    "created_at", ARGV[1])
return "CREATED"
```

**Time Complexity**: O(1) per field. O(N) for full config load.

---

### Problem 20: Real-Time Recommendation User Preferences

**Scenario**: YouTube/Spotify stores user preferences for real-time recommendations: liked genres, disliked topics, language, watch history signals. Recommendation engine reads these on every request.

**Why Hash?**: Sub-millisecond reads on every recommendation request. Atomic updates as user interacts. Single key per user keeps it organized.

**Key Design**: `prefs:user:{user_id}`

```redis
# Store user preferences
HSET prefs:user:123 
    preferred_language "en" 
    genre_rock 85 
    genre_pop 60 
    genre_classical 30 
    genre_jazz 45 
    topic_tech 90 
    topic_sports 70 
    topic_cooking 20 
    min_duration 60 
    max_duration 600 
    explicit_content 0

# User likes a rock song → boost rock preference
HINCRBY prefs:user:123 genre_rock 5
# Returns 90

# User skips classical → decrease preference
HINCRBY prefs:user:123 genre_classical -10
# Returns 20

# Recommendation engine reads preferences
HGETALL prefs:user:123
# Returns all preferences in ~0.1ms
# Engine uses these weights to score candidates

# Read specific preferences for filtering
HMGET prefs:user:123 preferred_language explicit_content min_duration max_duration
# Returns ["en", "0", "60", "600"]
# Filter: only English, no explicit, 1-10 min duration

# Decay preferences over time (batch job)
# Multiply all genre scores by 0.95 weekly
```

**Preference Decay — Lua Script**:
```lua
-- KEYS[1] = prefs:user:123
-- ARGV[1] = decay factor (0.95)
local fields = redis.call("HGETALL", KEYS[1])
local decay = tonumber(ARGV[1])
for i = 1, #fields, 2 do
    local field = fields[i]
    local value = tonumber(fields[i+1])
    -- Only decay genre/topic scores, not settings
    if string.find(field, "genre_") or string.find(field, "topic_") then
        local new_value = math.floor(value * decay)
        redis.call("HSET", KEYS[1], field, new_value)
    end
end
return "OK"
```

**Hybrid Approach (Redis + ML Model)**:
```
Request → Redis HGETALL prefs:user:123 (0.1ms)
       → Feed preferences into ML model
       → Model scores 1000 candidate items
       → Return top 20 recommendations
Total: ~10ms end-to-end
```

**Time Complexity**: O(N) for HGETALL. O(1) per HINCRBY.

**Interview Follow-up**:
- "How to handle cold start (new users)?" → Use global popularity-based defaults from `prefs:global:defaults`
- "How to handle millions of preference keys?" → Redis Cluster, shard by user_id hash
