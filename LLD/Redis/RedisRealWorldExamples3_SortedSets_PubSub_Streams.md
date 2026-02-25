# Redis Real-World Examples — Part 3: SORTED SET, PUB/SUB & STREAMS

## 🔹 SORTED SET (ZSET) PROBLEMS (41–50)

---

### Problem 41: Leaderboard (ZADD)

**Scenario**: Fortnite global leaderboard — millions of players, need top-N, rank lookup, and score updates in real-time.

**Why Sorted Set?**: ZADD O(log N), ZREVRANGE O(log N + M). Built for ranking. No manual sorting needed.

**Key Design**: `leaderboard:{game}:{season}`

```redis
# Players score points
ZADD leaderboard:fortnite:s5 1500 "player:alice"
ZADD leaderboard:fortnite:s5 2200 "player:bob"
ZADD leaderboard:fortnite:s5 1800 "player:charlie"
ZADD leaderboard:fortnite:s5 3100 "player:dave"
ZADD leaderboard:fortnite:s5 2700 "player:eve"

# Update score (player wins a match)
ZINCRBY leaderboard:fortnite:s5 150 "player:alice"
# Returns 1650 → Alice's new score

# Top 3 players (highest scores first)
ZREVRANGE leaderboard:fortnite:s5 0 2 WITHSCORES
# Returns: ["player:dave", "3100", "player:eve", "2700", "player:bob", "2200"]

# Alice's rank (0-based, highest = rank 0)
ZREVRANK leaderboard:fortnite:s5 "player:alice"
# Returns 3 → 4th place

# Alice's score
ZSCORE leaderboard:fortnite:s5 "player:alice"
# Returns "1650"

# Total players on leaderboard
ZCARD leaderboard:fortnite:s5
# Returns 5

# Players ranked 10th-20th (for pagination)
ZREVRANGE leaderboard:fortnite:s5 9 19 WITHSCORES
```

**Time Complexity**: ZADD O(log N). ZREVRANGE O(log N + M). ZREVRANK O(log N). ZSCORE O(1).

---

### Problem 42: Top N Scores (ZREVRANGE)

**Scenario**: Spotify — show top 10 most-played songs this week. Updated in real-time as songs are played.

**Key Design**: `top:songs:{week}`

```redis
# Increment play count as songs are played
ZINCRBY top:songs:2024-w03 1 "song:bohemian_rhapsody"
ZINCRBY top:songs:2024-w03 1 "song:hotel_california"
ZINCRBY top:songs:2024-w03 1 "song:bohemian_rhapsody"
# Bohemian Rhapsody now has score 2

# Top 10 most played songs this week
ZREVRANGE top:songs:2024-w03 0 9 WITHSCORES
# Returns songs sorted by play count, highest first

# Top 3 with scores
ZREVRANGE top:songs:2024-w03 0 2 WITHSCORES
# Returns: ["song:bohemian_rhapsody", "2", "song:hotel_california", "1"]

# Auto-expire weekly chart
EXPIRE top:songs:2024-w03 604800

# Bottom 3 (least played)
ZRANGE top:songs:2024-w03 0 2 WITHSCORES
```

**Time Complexity**: ZREVRANGE O(log N + M) where M = number returned.

---

### Problem 43: User Rank Lookup (ZRANK / ZREVRANK)

**Scenario**: Duolingo shows "You are #1,247 out of 50,000 learners this month". Instant rank lookup.

**Key Design**: `rank:learners:{month}`

```redis
# Track XP earned per user
ZADD rank:learners:2024-01 5420 "user:alice"
ZADD rank:learners:2024-01 8900 "user:bob"
ZADD rank:learners:2024-01 3200 "user:charlie"
ZADD rank:learners:2024-01 12000 "user:dave"

# User earns more XP
ZINCRBY rank:learners:2024-01 200 "user:alice"

# Alice's rank (highest score = rank 0)
ZREVRANK rank:learners:2024-01 "user:alice"
# Returns 2 → 3rd place (0-based)
# Display as "#3 out of 4 learners"

# Alice's score
ZSCORE rank:learners:2024-01 "user:alice"
# Returns "5620"

# Total learners
ZCARD rank:learners:2024-01
# Returns 4

# Users around Alice (for "nearby" leaderboard)
# Alice is rank 2, show rank 1-3
ZREVRANGE rank:learners:2024-01 1 3 WITHSCORES
```

**Time Complexity**: ZREVRANK O(log N). ZSCORE O(1).

---

### Problem 44: Inactive Player Removal (ZREM)

**Scenario**: Game server removes players who haven't been active for 30 days. Also remove banned players immediately.

**Key Design**: `leaderboard:active` with score = last_active_timestamp

```redis
# Track player activity (score = last active timestamp)
ZADD leaderboard:active 1705312200 "player:alice"
ZADD leaderboard:active 1705000000 "player:bob"    # 3 days ago
ZADD leaderboard:active 1702720000 "player:charlie" # 30 days ago

# Remove specific banned player
ZREM leaderboard:active "player:bob"
# Returns 1 → removed

# Remove ALL players inactive for 30+ days
ZREMRANGEBYSCORE leaderboard:active -inf 1702720000
# Removes all players with last_active <= 30 days ago

# Alternatively: Remove bottom N players by rank
ZREMRANGEBYRANK leaderboard:active 0 9
# Removes 10 lowest-scoring players

# Count remaining active players
ZCARD leaderboard:active
```

**Periodic Cleanup — Lua Script**:
```lua
-- KEYS[1] = leaderboard:active
-- ARGV[1] = cutoff timestamp (now - 30 days)
local removed = redis.call("ZREMRANGEBYSCORE", KEYS[1], "-inf", ARGV[1])
return removed  -- number of players removed
```

**Time Complexity**: ZREM O(log N). ZREMRANGEBYSCORE O(log N + M).

---

### Problem 45: Multi-Leaderboard Merge (ZUNIONSTORE)

**Scenario**: Call of Duty — combine leaderboards from Team Deathmatch, Free-for-All, and Battle Royale into an overall ranking.

**Key Design**: `leaderboard:{mode}`, `leaderboard:overall`

```redis
# Mode-specific leaderboards
ZADD leaderboard:tdm "1500" "player:alice" "2000" "player:bob" "1200" "player:charlie"
ZADD leaderboard:ffa "800" "player:alice" "1500" "player:dave" "900" "player:bob"
ZADD leaderboard:br "2000" "player:eve" "1000" "player:alice" "500" "player:bob"

# Merge ALL modes into overall leaderboard (SUM scores)
ZUNIONSTORE leaderboard:overall 3 leaderboard:tdm leaderboard:ffa leaderboard:br
# Alice: 1500 + 800 + 1000 = 3300
# Bob: 2000 + 900 + 500 = 3400

# Overall top 5
ZREVRANGE leaderboard:overall 0 4 WITHSCORES

# Weighted merge (TDM counts 2x, BR counts 1.5x)
ZUNIONSTORE leaderboard:overall 3 leaderboard:tdm leaderboard:ffa leaderboard:br WEIGHTS 2 1 1.5
# Alice: 1500*2 + 800*1 + 1000*1.5 = 4300

# Use MAX instead of SUM (best score across modes)
ZUNIONSTORE leaderboard:best 3 leaderboard:tdm leaderboard:ffa leaderboard:br AGGREGATE MAX
# Alice: max(1500, 800, 1000) = 1500
```

**Time Complexity**: ZUNIONSTORE O(N*log N) where N = total elements.

---

### Problem 46: Leaderboard Intersection (ZINTERSTORE)

**Scenario**: Find players who are top performers in BOTH PvP AND PvE modes. Intersection of two leaderboards.

**Key Design**: `leaderboard:{mode}`

```redis
ZADD leaderboard:pvp 2000 "alice" 1800 "bob" 1500 "charlie" 900 "dave"
ZADD leaderboard:pve 1700 "alice" 2100 "charlie" 1300 "eve" 800 "bob"

# Players active in BOTH modes (intersection)
ZINTERSTORE leaderboard:both_modes 2 leaderboard:pvp leaderboard:pve
# Only alice, bob, charlie (in both sets)
# alice: 2000 + 1700 = 3700
# bob: 1800 + 800 = 2600
# charlie: 1500 + 2100 = 3600

ZREVRANGE leaderboard:both_modes 0 -1 WITHSCORES
# Returns: ["alice", "3700", "charlie", "3600", "bob", "2600"]

# Use MIN aggregate (worst mode determines rank)
ZINTERSTORE leaderboard:weakest 2 leaderboard:pvp leaderboard:pve AGGREGATE MIN
# alice: min(2000, 1700) = 1700 → well-rounded
# charlie: min(1500, 2100) = 1500
```

**Time Complexity**: ZINTERSTORE O(N*M*log M) where N = smallest set.

---

### Problem 47: Score Range User Count (ZCOUNT)

**Scenario**: Uber driver ratings — count how many drivers have ratings between 4.5 and 5.0 (eligible for "Gold" tier).

**Key Design**: `ratings:drivers:{city}`

```redis
ZADD ratings:drivers:NYC 4.8 "driver:100" 4.2 "driver:200" 4.9 "driver:300"
ZADD ratings:drivers:NYC 3.5 "driver:400" 4.6 "driver:500" 5.0 "driver:600"

# Count Gold tier drivers (rating >= 4.5)
ZCOUNT ratings:drivers:NYC 4.5 5.0
# Returns 4 (drivers 100, 300, 500, 600)

# Count all drivers below 4.0 (at risk)
ZCOUNT ratings:drivers:NYC -inf 3.99
# Returns 1 (driver 400)

# Get the Gold tier drivers
ZRANGEBYSCORE ratings:drivers:NYC 4.5 5.0 WITHSCORES
# Returns: ["driver:500", "4.6", "driver:100", "4.8", "driver:300", "4.9", "driver:600", "5.0"]

# Get at-risk drivers with LIMIT (pagination)
ZRANGEBYSCORE ratings:drivers:NYC -inf 3.99 LIMIT 0 10
```

**Time Complexity**: ZCOUNT O(log N). ZRANGEBYSCORE O(log N + M).

---

### Problem 48: Time-Based Event Queue

**Scenario**: Scheduled notifications — "Send push notification to user X at 3:00 PM". Score = scheduled timestamp.

**Why Sorted Set?**: Score = unix timestamp. ZRANGEBYSCORE fetches all events due now. Natural time ordering.

**Key Design**: `scheduled:notifications`

```redis
# Schedule notifications (score = unix timestamp)
ZADD scheduled:notifications 1705320000 '{"user":123,"msg":"Your flight is in 2 hours"}'
ZADD scheduled:notifications 1705323600 '{"user":456,"msg":"Meeting in 30 min"}'
ZADD scheduled:notifications 1705316400 '{"user":789,"msg":"Good morning!"}'

# Worker: Get all notifications due NOW (score <= current time)
ZRANGEBYSCORE scheduled:notifications -inf 1705320000
# Returns all notifications with timestamp <= now

# Process and remove (atomic pop)
ZPOPMIN scheduled:notifications
# Returns the earliest scheduled notification AND removes it
```

**Atomic Fetch-and-Remove — Lua Script**:
```lua
-- KEYS[1] = scheduled:notifications
-- ARGV[1] = current timestamp
local due = redis.call("ZRANGEBYSCORE", KEYS[1], "-inf", ARGV[1])
if #due > 0 then
    redis.call("ZREMRANGEBYSCORE", KEYS[1], "-inf", ARGV[1])
end
return due
```

**Time Complexity**: ZADD O(log N). ZRANGEBYSCORE O(log N + M). ZPOPMIN O(log N).

---

### Problem 49: Trending Topics Tracker

**Scenario**: Twitter trending — track hashtag usage, show top 10 trending. Scores decay over time.

**Key Design**: `trending:{window}`

```redis
# User tweets with hashtags
ZINCRBY trending:hourly 1 "#redis"
ZINCRBY trending:hourly 1 "#systemdesign"
ZINCRBY trending:hourly 1 "#redis"
ZINCRBY trending:hourly 1 "#interview"
ZINCRBY trending:hourly 1 "#redis"

# Top 10 trending hashtags
ZREVRANGE trending:hourly 0 9 WITHSCORES
# Returns: ["#redis", "3", "#systemdesign", "1", "#interview", "1"]

# Auto-expire hourly window
EXPIRE trending:hourly 3600

# Combine hourly windows for daily trending (weighted)
ZUNIONSTORE trending:daily 24 
    trending:hour:00 trending:hour:01 ... trending:hour:23
    WEIGHTS 1 1 1 1 ... 1
```

**Time-Decayed Trending — Lua Script**:
```lua
-- Decay all scores by 0.95 every hour
-- KEYS[1] = trending:hourly
local members = redis.call("ZRANGEBYSCORE", KEYS[1], "-inf", "+inf", "WITHSCORES")
for i = 1, #members, 2 do
    local member = members[i]
    local score = tonumber(members[i+1]) * 0.95
    if score < 0.1 then
        redis.call("ZREM", KEYS[1], member)
    else
        redis.call("ZADD", KEYS[1], score, member)
    end
end
return "OK"
```

**Time Complexity**: ZINCRBY O(log N). ZREVRANGE O(log N + M).

---

### Problem 50: Delayed Job Scheduler (score = timestamp)

**Scenario**: Send a follow-up email 24 hours after user signs up. Schedule jobs to run at specific future times.

**Why Sorted Set?**: Score = execution timestamp. Poll for jobs where score <= now. Natural priority by time.

**Key Design**: `scheduler:jobs`

```redis
# Schedule delayed jobs
ZADD scheduler:jobs 1705406400 '{"type":"followup_email","user_id":123}'
# Execute at timestamp 1705406400 (tomorrow)

ZADD scheduler:jobs 1705320000 '{"type":"trial_reminder","user_id":456}'
# Execute at timestamp 1705320000 (today 3 PM)

ZADD scheduler:jobs 1705492800 '{"type":"subscription_expiry","user_id":789}'
# Execute in 2 days

# Worker loop: Poll every second for due jobs
ZRANGEBYSCORE scheduler:jobs -inf <current_timestamp> LIMIT 0 10
# Returns up to 10 jobs ready to execute
```

**Atomic Job Claim — Lua Script**:
```lua
-- KEYS[1] = scheduler:jobs
-- KEYS[2] = scheduler:processing
-- ARGV[1] = current timestamp
-- ARGV[2] = batch size (10)
local jobs = redis.call("ZRANGEBYSCORE", KEYS[1], "-inf", ARGV[1], "LIMIT", 0, tonumber(ARGV[2]))
if #jobs == 0 then
    return {}
end
for _, job in ipairs(jobs) do
    -- Move from scheduled → processing
    local score = redis.call("ZSCORE", KEYS[1], job)
    redis.call("ZREM", KEYS[1], job)
    redis.call("ZADD", KEYS[2], score, job)
end
return jobs
```

**Worker Architecture**:
```
┌──────────────────┐     ┌──────────────────────┐
│  App Server       │     │  Redis                │
│  scheduleJob()   │────→│  ZADD scheduler:jobs  │
└──────────────────┘     │  score=future_ts      │
                          └──────────┬───────────┘
                                     │
┌──────────────────┐     ┌──────────▼───────────┐
│  Worker (poll)    │←────│  ZRANGEBYSCORE        │
│  every 1s         │     │  -inf <now> LIMIT 10 │
│  process + ZREM   │     └──────────────────────┘
└──────────────────┘
```

**Time Complexity**: ZADD O(log N). ZRANGEBYSCORE O(log N + M). ZREM O(log N).

---

## 🔹 PUB/SUB PROBLEMS (51–53)

---

### Problem 51: Real-Time Notification Broadcast

**Scenario**: Stock trading platform — broadcast price changes to all connected clients in real-time.

**Why PUB/SUB?**: Fire-and-forget fan-out. All subscribers get every message. No persistence needed (prices change constantly).

```redis
# Publisher: Market data service
PUBLISH prices:AAPL '{"symbol":"AAPL","price":185.42,"ts":1705312200}'
PUBLISH prices:GOOGL '{"symbol":"GOOGL","price":141.80,"ts":1705312200}'
PUBLISH prices:AAPL '{"symbol":"AAPL","price":185.50,"ts":1705312201}'

# Subscriber 1 (Client App): Subscribe to Apple prices
SUBSCRIBE prices:AAPL
# Receives: '{"symbol":"AAPL","price":185.42,...}'
# Receives: '{"symbol":"AAPL","price":185.50,...}'

# Subscriber 2: Subscribe to ALL stock prices (pattern)
PSUBSCRIBE prices:*
# Receives ALL price updates for every symbol

# Subscriber 3: Subscribe to multiple specific channels
SUBSCRIBE prices:AAPL prices:GOOGL prices:MSFT
```

**⚠️ PUB/SUB Limitations**:
- Messages are NOT persisted — if subscriber is disconnected, messages are lost
- No message history or replay
- No acknowledgment — publisher doesn't know if anyone received
- For reliability, use Redis Streams instead

**Time Complexity**: PUBLISH O(N+M) where N = subscribers, M = pattern subscribers.

---

### Problem 52: Chat Messaging for Multiple Subscribers

**Scenario**: Slack-like chat — multiple users in a channel receive messages in real-time. New messages broadcast instantly.

```redis
# User sends message to #general channel
PUBLISH chat:general '{"from":"alice","text":"Hey team!","ts":1705312200}'
PUBLISH chat:general '{"from":"bob","text":"Morning!","ts":1705312205}'

# All users in #general subscribe:
# User A: SUBSCRIBE chat:general
# User B: SUBSCRIBE chat:general
# User C: SUBSCRIBE chat:general
# All three receive both messages simultaneously

# Subscribe to multiple channels
SUBSCRIBE chat:general chat:random chat:engineering

# Pattern subscribe (all channels for a workspace)
PSUBSCRIBE chat:acme:*
# Receives messages from chat:acme:general, chat:acme:random, etc.

# Unsubscribe
UNSUBSCRIBE chat:general
```

**Hybrid Approach (PUB/SUB + LIST for history)**:
```redis
# On message send:
# 1. Persist to list (for history)
LPUSH chat:general:history '{"from":"alice","text":"Hey team!","ts":1705312200}'
LTRIM chat:general:history 0 999

# 2. Broadcast to online users (for real-time)
PUBLISH chat:general '{"from":"alice","text":"Hey team!","ts":1705312200}'

# When user connects: load history from list, then subscribe for real-time
```

**Time Complexity**: PUBLISH O(N+M). SUBSCRIBE O(1).

---

### Problem 53: News Feed Real-Time Updates

**Scenario**: Reddit — when a post gets a new comment, all users viewing that post see the comment appear instantly.

```redis
# User comments on post
# 1. Store comment
LPUSH comments:post:42 '{"user":"alice","text":"Great post!","ts":1705312200}'

# 2. Broadcast to viewers
PUBLISH post:42:comments '{"user":"alice","text":"Great post!","ts":1705312200}'

# All users viewing post 42:
SUBSCRIBE post:42:comments
# Instantly receive new comments

# Pattern: Subscribe to comments on all posts user is watching
PSUBSCRIBE post:*:comments

# Keyspace notifications (Redis built-in):
# Notify when any key is modified
CONFIG SET notify-keyspace-events Kx
SUBSCRIBE __keyevent@0__:expired
# Triggered when ANY key expires — useful for session timeouts
```

**Time Complexity**: PUBLISH O(N+M).

---

## 🔹 STREAMS PROBLEMS (54–57)

---

### Problem 54: Event-Driven Architecture with Streams

**Scenario**: E-commerce — order events (created, paid, shipped, delivered) flow through a stream. Multiple services consume different events.

**Why Streams over PUB/SUB?**: Persistence, consumer groups, acknowledgment, replay. Production-grade event processing.

**Key Design**: `stream:orders`

```redis
# Produce events
XADD stream:orders * event "ORDER_CREATED" order_id "ORD-101" user_id "123" total "99.99"
# Returns "1705312200000-0" (auto-generated ID: timestamp-sequence)

XADD stream:orders * event "PAYMENT_SUCCESS" order_id "ORD-101" payment_id "PAY-555"
XADD stream:orders * event "ORDER_SHIPPED" order_id "ORD-101" tracking "TRK-999"

# Read all events from beginning
XRANGE stream:orders - +
# Returns all entries from start (-) to end (+)

# Read last 5 events
XREVRANGE stream:orders + - COUNT 5

# Read events after a specific ID
XREAD COUNT 10 STREAMS stream:orders 1705312200000-0
# Returns events after the given ID

# Stream length
XLEN stream:orders
# Returns 3

# Trim stream to last 10000 entries (memory management)
XTRIM stream:orders MAXLEN ~ 10000
# ~ means approximate (more efficient)
```

**Time Complexity**: XADD O(1). XRANGE O(N). XLEN O(1).

---

### Problem 55: Multi-Consumer Event Queue with Streams

**Scenario**: Payment events need to be processed by billing service, analytics service, and notification service. Each service processes independently.

**Key Design**: `stream:payments` with consumer groups

```redis
# Create consumer groups (each service gets its own group)
XGROUP CREATE stream:payments billing-service 0
XGROUP CREATE stream:payments analytics-service 0
XGROUP CREATE stream:payments notification-service 0

# Producer: Add payment event
XADD stream:payments * event "PAYMENT" user "123" amount "99.99" currency "USD"

# Billing service consumer reads:
XREADGROUP GROUP billing-service worker-1 COUNT 1 STREAMS stream:payments >
# Returns the event — ">" means only new (undelivered) messages

# Analytics service consumer reads THE SAME event:
XREADGROUP GROUP analytics-service worker-1 COUNT 1 STREAMS stream:payments >
# Each group gets its own copy! Independent processing.

# Notification service:
XREADGROUP GROUP notification-service worker-1 COUNT 1 STREAMS stream:payments >

# Acknowledge after processing (billing confirms)
XACK stream:payments billing-service 1705312200000-0
```

**Multiple Workers per Group**:
```redis
# 3 billing workers share the load:
XREADGROUP GROUP billing-service worker-1 COUNT 5 STREAMS stream:payments >
XREADGROUP GROUP billing-service worker-2 COUNT 5 STREAMS stream:payments >
XREADGROUP GROUP billing-service worker-3 COUNT 5 STREAMS stream:payments >
# Each message goes to exactly ONE worker within the group
# Different groups get ALL messages (fan-out per group)
```

**Time Complexity**: XREADGROUP O(1) for new messages.

---

### Problem 56: Reliable Delivery with Consumer Groups

**Scenario**: Payment processing must be reliable — if a worker crashes mid-processing, another worker must pick up the message.

```redis
# Create group starting from beginning
XGROUP CREATE stream:payments processors 0

# Worker 1 reads a message
XREADGROUP GROUP processors worker-1 COUNT 1 STREAMS stream:payments >
# Returns message ID: 1705312200000-0

# Worker 1 CRASHES before acknowledging!

# Check pending (unacknowledged) messages
XPENDING stream:payments processors
# Returns: 1 pending message, min-id, max-id, consumer info
# Shows worker-1 has 1 unacked message

# Detailed pending info
XPENDING stream:payments processors - + 10
# Returns: [["1705312200000-0", "worker-1", 30000, 1]]
# Message 1705312200000-0, owned by worker-1, idle for 30000ms, delivered 1 time

# Worker 2 claims the stuck message (idle > 30 seconds)
XCLAIM stream:payments processors worker-2 30000 1705312200000-0
# Now worker-2 owns this message

# Worker 2 processes and acknowledges
XACK stream:payments processors 1705312200000-0
# Message is now confirmed processed

# Auto-claim (Redis 6.2+): Claim idle messages automatically
XAUTOCLAIM stream:payments processors worker-2 30000 0
# Claims all messages idle for > 30 seconds
```

**Reliable Consumer Pattern**:
```
┌─────────────┐     XADD      ┌─────────────┐
│ Producer     │ ────────────→ │ Stream      │
└─────────────┘                └──────┬──────┘
                                      │
                    XREADGROUP        │
              ┌───────────────────────┤
              │                       │
        ┌─────▼─────┐          ┌─────▼─────┐
        │ Worker-1   │          │ Worker-2   │
        │ process    │          │ process    │
        │ XACK ✓     │          │ XCLAIM     │
        └────────────┘          │ (if W1     │
                                │  crashes)  │
                                │ XACK ✓     │
                                └────────────┘
```

**Time Complexity**: XPENDING O(N). XCLAIM O(1). XACK O(1).

---

### Problem 57: Undo Logs for Collaborative Editing

**Scenario**: Google Docs — track all edits in real-time. Support undo/redo. Multiple users editing simultaneously.

**Why Streams?**: Append-only log with ordering. XRANGE for history. Consumer groups for real-time sync.

**Key Design**: `edits:{document_id}`

```redis
# User A types
XADD edits:doc:42 * user "alice" action "insert" position "0:5" content "Hello" version "1"

# User B types
XADD edits:doc:42 * user "bob" action "insert" position "0:10" content " World" version "2"

# User A deletes
XADD edits:doc:42 * user "alice" action "delete" position "0:0" length "5" version "3"

# Get all edits (for replaying document state)
XRANGE edits:doc:42 - +
# Returns chronological edit log

# Get edits since last sync (for late-joining collaborator)
XRANGE edits:doc:42 1705312200000-0 +
# Returns edits after the given ID

# Undo: Read last edit by user
XREVRANGE edits:doc:42 + - COUNT 1
# Returns most recent edit → generate inverse operation

# Real-time sync: Each collaborator subscribes via consumer group
XGROUP CREATE edits:doc:42 sync-alice $ 
# $ = only new messages from now
XREADGROUP GROUP sync-alice alice-browser COUNT 10 BLOCK 5000 STREAMS edits:doc:42 >
# Blocks until new edits arrive → push to browser via WebSocket

# Trim old edits (keep last 10000)
XTRIM edits:doc:42 MAXLEN ~ 10000
```

**Document Snapshot + Stream Pattern**:
```redis
# Store periodic snapshots (every 100 edits)
SET doc:42:snapshot:v100 '{"content":"Hello World","version":100}'
SET doc:42:snapshot:latest "100"

# To reconstruct current state:
# 1. Load latest snapshot: GET doc:42:snapshot:v100
# 2. Replay edits since v100: XRANGE edits:doc:42 <snapshot-id> +
# 3. Apply edits to snapshot → current document
```

**Time Complexity**: XADD O(1). XRANGE O(N). XREADGROUP O(1) for new.