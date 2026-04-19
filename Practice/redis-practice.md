# 🔴 Redis Hands-On Practice Guide — Basics to Interview Mastery

> **How to use this file:**
> 1. Start Redis locally: `redis-server` (in one terminal)
> 2. Open Redis CLI: `redis-cli` (in another terminal)
> 3. Copy-paste commands section by section
> 4. Read the comments (lines starting with `#`) to understand what's happening
> 5. Each section builds on the previous — go in order!

---

## 📋 Table of Contents

- [🔴 Redis Hands-On Practice Guide — Basics to Interview Mastery](#-redis-hands-on-practice-guide--basics-to-interview-mastery)
  - [📋 Table of Contents](#-table-of-contents)
  - [1. Connection \& Server Basics](#1-connection--server-basics)
  - [2. Strings — The Foundation](#2-strings--the-foundation)
  - [3. String Advanced — Counters, Bits, Ranges](#3-string-advanced--counters-bits-ranges)
  - [4. Keys — Expiry, TTL, Patterns](#4-keys--expiry-ttl-patterns)
  - [5. Lists — Queues \& Stacks](#5-lists--queues--stacks)
  - [6. Sets — Unique Collections](#6-sets--unique-collections)
  - [7. Sorted Sets (ZSETs) — Leaderboards \& Rankings](#7-sorted-sets-zsets--leaderboards--rankings)
  - [8. Hashes — Object Storage](#8-hashes--object-storage)
  - [9. HyperLogLog — Cardinality Estimation](#9-hyperloglog--cardinality-estimation)
  - [10. Bitmaps — Compact Boolean Storage](#10-bitmaps--compact-boolean-storage)
  - [11. Geospatial — Location-Based Features](#11-geospatial--location-based-features)
  - [12. Streams — Event Sourcing \& Message Queues](#12-streams--event-sourcing--message-queues)
  - [13. Pub/Sub — Real-Time Messaging](#13-pubsub--real-time-messaging)
  - [14. Transactions — MULTI/EXEC](#14-transactions--multiexec)
  - [15. Lua Scripting — Atomic Operations](#15-lua-scripting--atomic-operations)
  - [16. Pipelining — Batch Operations](#16-pipelining--batch-operations)
  - [17. Persistence — RDB \& AOF](#17-persistence--rdb--aof)
  - [18. Memory Management \& Eviction](#18-memory-management--eviction)
  - [19. Replication \& High Availability](#19-replication--high-availability)
  - [20. Redis Cluster](#20-redis-cluster)
  - [21. Interview Pattern: Rate Limiter](#21-interview-pattern-rate-limiter)
  - [22. Interview Pattern: Distributed Lock (Redlock)](#22-interview-pattern-distributed-lock-redlock)
  - [23. Interview Pattern: Session Store](#23-interview-pattern-session-store)
  - [24. Interview Pattern: Leaderboard System](#24-interview-pattern-leaderboard-system)
  - [25. Interview Pattern: Feed / Timeline](#25-interview-pattern-feed--timeline)
  - [26. Interview Pattern: Unique Visitors / Count Distinct](#26-interview-pattern-unique-visitors--count-distinct)
  - [27. Interview Pattern: Autocomplete / Typeahead](#27-interview-pattern-autocomplete--typeahead)
  - [28. Interview Pattern: Job Queue](#28-interview-pattern-job-queue)
  - [29. Interview Pattern: Sliding Window Counter](#29-interview-pattern-sliding-window-counter)
  - [30. Interview Pattern: Pub/Sub Chat System](#30-interview-pattern-pubsub-chat-system)
  - [31. Interview Pattern: Cache-Aside (Lazy Loading)](#31-interview-pattern-cache-aside-lazy-loading)
  - [32. Interview Pattern: Write-Through / Write-Behind Cache](#32-interview-pattern-write-through--write-behind-cache)
  - [33. Interview Pattern: Bloom Filter Simulation](#33-interview-pattern-bloom-filter-simulation)
  - [34. Interview Pattern: Idempotency Keys](#34-interview-pattern-idempotency-keys)
  - [35. Interview Pattern: Presence / Online Status](#35-interview-pattern-presence--online-status)
  - [36. Interview Pattern: Trending / Top-K](#36-interview-pattern-trending--top-k)
  - [37. Interview Pattern: Distributed Counter](#37-interview-pattern-distributed-counter)
  - [38. Interview Pattern: Notification System](#38-interview-pattern-notification-system)
  - [39. Interview Pattern: URL Shortener Cache](#39-interview-pattern-url-shortener-cache)
  - [40. Interview Pattern: Stock Ticker / Time-Series](#40-interview-pattern-stock-ticker--time-series)
  - [41. Performance \& Debugging Commands](#41-performance--debugging-commands)
  - [42. Anti-Patterns \& Common Mistakes](#42-anti-patterns--common-mistakes)
  - [43. Interview Quick-Fire Q\&A Commands](#43-interview-quick-fire-qa-commands)

---

## 1. Connection & Server Basics

```redis
# =============================================
# SECTION 1: CONNECTION & SERVER BASICS
# =============================================

# Check if Redis is running
PING
# Expected: PONG

# Get server info (huge output — shows memory, replication, stats, etc.)
INFO

# Get specific section of INFO
INFO server
INFO memory
INFO replication
INFO stats
INFO keyspace

# Check how many keys exist in the current database
DBSIZE

# Select a different database (Redis has 16 databases: 0-15)
SELECT 0
SELECT 1
SELECT 0

# Flush current database (⚠️ deletes all keys in current DB)
# FLUSHDB

# Flush ALL databases (⚠️ deletes everything)
# FLUSHALL

# Get server time
TIME

# Check last save time
LASTSAVE

# Get config value
CONFIG GET maxmemory
CONFIG GET save
CONFIG GET appendonly

# Check connected clients
CLIENT LIST
CLIENT GETNAME
CLIENT SETNAME "practice-session"
CLIENT GETNAME

# Check slow queries
SLOWLOG GET 10
SLOWLOG LEN
SLOWLOG RESET

# Monitor all commands in real time (⚠️ use in dev only, press Ctrl+C to stop)
# MONITOR
```

---

## 2. Strings — The Foundation

```redis
# =============================================
# SECTION 2: STRINGS — THE FOUNDATION
# =============================================

# SET a simple key-value pair
SET name "Redis"
GET name
# Expected: "Redis"

# SET with options
SET greeting "Hello World"
GET greeting

# SET only if key does NOT exist (NX = Not eXists)
SET username "alice" NX
SET username "bob" NX
GET username
# Expected: "alice" (second SET was ignored because key already existed)

# SET only if key ALREADY exists (XX)
SET password "secret123" XX
# Expected: (nil) — password key doesn't exist yet
SET password "initial"
SET password "updated" XX
GET password
# Expected: "updated"

# SET with expiry in seconds (EX)
SET otp "482910" EX 30
GET otp
TTL otp
# Expected: some positive number (seconds remaining)

# SET with expiry in milliseconds (PX)
SET flash_sale "active" PX 5000
GET flash_sale
PTTL flash_sale

# GETSET — atomically set new value and return old value (deprecated, use GETDEL/GETEX)
SET counter "10"
GETSET counter "20"
# Expected: "10" (old value returned)
GET counter
# Expected: "20"

# GETDEL — get value and delete key atomically
SET temp "delete_me"
GETDEL temp
# Expected: "delete_me"
GET temp
# Expected: (nil)

# GETEX — get value and set expiry atomically
SET session "abc123"
GETEX session EX 60
TTL session
# Expected: 60 (or close to it)

# MSET — set multiple keys at once
MSET city "Barcelona" country "Spain" continent "Europe"
MGET city country continent
# Expected: 1) "Barcelona" 2) "Spain" 3) "Europe"

# MSETNX — set multiple keys only if NONE exist
MSETNX key1 "val1" key2 "val2" key3 "val3"
MSETNX key1 "new1" key4 "val4"
# Expected: 0 (failed because key1 already exists — ALL or NOTHING)
GET key4
# Expected: (nil)

# APPEND — append to existing string
SET bio "Redis is"
APPEND bio " awesome!"
GET bio
# Expected: "Redis is awesome!"

# STRLEN — get length of string
STRLEN bio
# Expected: 18

# SETRANGE — overwrite part of string
SET msg "Hello World"
SETRANGE msg 6 "Redis"
GET msg
# Expected: "Hello Redis"

# GETRANGE — get substring (0-indexed, inclusive on both ends)
SET message "Hello, Redis World!"
GETRANGE message 0 4
# Expected: "Hello"
GETRANGE message 7 11
# Expected: "Redis"
GETRANGE message -6 -1
# Expected: "orld!" ... actually let me check
GETRANGE message -6 -1
# Expected: "World!"

# LCS — Longest Common Substring (Redis 7.0+)
SET key1 "ohmytext"
SET key2 "mynewtext"
LCS key1 key2
# Expected: "mytext"

# Cleanup
DEL name greeting username password otp flash_sale counter temp session city country continent key1 key2 key3 bio msg message
```

---

## 3. String Advanced — Counters, Bits, Ranges

```redis
# =============================================
# SECTION 3: STRING ADVANCED — COUNTERS
# =============================================

# INCR — atomic increment by 1 (key created if not exists, starts at 0)
SET page_views "100"
INCR page_views
INCR page_views
INCR page_views
GET page_views
# Expected: "103"

# INCR on non-existent key
DEL new_counter
INCR new_counter
GET new_counter
# Expected: "1"

# DECR — atomic decrement by 1
SET stock "50"
DECR stock
DECR stock
GET stock
# Expected: "48"

# INCRBY — increment by specific amount
SET balance "1000"
INCRBY balance 500
GET balance
# Expected: "1500"

# DECRBY — decrement by specific amount
DECRBY balance 200
GET balance
# Expected: "1300"

# INCRBYFLOAT — increment by float
SET temperature "36.5"
INCRBYFLOAT temperature 0.3
GET temperature
# Expected: "36.8"

INCRBYFLOAT temperature -1.2
GET temperature
# Expected: "35.6"

# ⚠️ INCR on non-numeric string will ERROR
SET name "alice"
# INCR name   <-- This would give: (error) ERR value is not an integer or out of range

# 🎯 INTERVIEW: Why is INCR atomic?
# Redis is single-threaded, so INCR is a single operation
# No race conditions — perfect for counters, rate limiters, ID generators

# Cleanup
DEL page_views new_counter stock balance temperature name
```

---

## 4. Keys — Expiry, TTL, Patterns

```redis
# =============================================
# SECTION 4: KEYS — EXPIRY, TTL, PATTERNS
# =============================================

# Check if key exists
SET mykey "hello"
EXISTS mykey
# Expected: 1
EXISTS nonexistent
# Expected: 0

# Check multiple keys at once
SET k1 "a"
SET k2 "b"
EXISTS k1 k2 k3
# Expected: 2 (k1 and k2 exist, k3 doesn't)

# TYPE — check data type of a key
SET str_key "hello"
LPUSH list_key "a" "b"
SADD set_key "x" "y"
ZADD zset_key 1 "alpha"
HSET hash_key field1 "val1"

TYPE str_key
# Expected: string
TYPE list_key
# Expected: list
TYPE set_key
# Expected: set
TYPE zset_key
# Expected: zset
TYPE hash_key
# Expected: hash

# EXPIRE — set expiry in seconds
SET temp_key "i will expire"
EXPIRE temp_key 10
TTL temp_key
# Expected: 10 (or close)

# Wait a few seconds...
TTL temp_key

# PEXPIRE — set expiry in milliseconds
SET temp_key2 "precise expiry"
PEXPIRE temp_key2 5000
PTTL temp_key2

# EXPIREAT — set expiry as Unix timestamp
SET event "conference"
EXPIREAT event 1893456000
TTL event

# PERSIST — remove expiry (make key permanent again)
SET volatile_key "i might expire"
EXPIRE volatile_key 60
TTL volatile_key
PERSIST volatile_key
TTL volatile_key
# Expected: -1 (no expiry)

# TTL return values:
# -1 = key exists but has no expiry
# -2 = key does not exist
# positive number = seconds until expiry

TTL nonexistent_key
# Expected: -2

# RENAME — rename a key
SET old_name "value"
RENAME old_name new_name
GET new_name
# Expected: "value"
GET old_name
# Expected: (nil)

# RENAMENX — rename only if new name doesn't exist
SET a "1"
SET b "2"
RENAMENX a b
# Expected: 0 (b already exists)
GET a
# Expected: "1" (unchanged)

# KEYS — find keys matching pattern (⚠️ NEVER use in production — blocks Redis!)
MSET user:1:name "Alice" user:2:name "Bob" user:3:name "Charlie"
KEYS user:*
# Expected: user:1:name, user:2:name, user:3:name
KEYS user:?:name
# Expected: same (? matches single char)
KEYS *name*

# SCAN — safe alternative to KEYS (cursor-based, non-blocking)
SCAN 0 MATCH user:* COUNT 10
# Returns cursor + results. Keep calling with returned cursor until cursor = 0

# RANDOMKEY — get a random key
RANDOMKEY

# OBJECT — inspect key internals
SET myobj "hello"
OBJECT ENCODING myobj
# Expected: "embstr"
SET myobj "this is a much longer string that exceeds 44 bytes to test encoding change"
OBJECT ENCODING myobj
# Expected: "raw"

SET num "12345"
OBJECT ENCODING num
# Expected: "int"

OBJECT IDLETIME myobj
OBJECT REFCOUNT myobj
OBJECT HELP

# DUMP and RESTORE — serialize/deserialize a key
SET backup_test "important_data"
DUMP backup_test
# Returns binary serialization

# UNLINK vs DEL
# DEL — synchronous deletion (blocks Redis for big keys)
# UNLINK — async deletion (non-blocking, preferred for large keys)
SET big_key "some data"
UNLINK big_key
GET big_key
# Expected: (nil)

# OBJECT FREQ — for LFU eviction policy
# OBJECT ENCODING — shows internal representation
SET small_list "val"
OBJECT ENCODING small_list
# Expected: "embstr"

# COPY (Redis 6.2+)
SET source "original"
COPY source destination
GET destination
# Expected: "original"
GET source
# Expected: "original" (still there)

COPY source destination REPLACE
# REPLACE overwrites destination if it exists

# WAIT — wait for replication (useful in replicated setup)
# WAIT 1 1000
# Waits for 1 replica to acknowledge within 1000ms

# Cleanup
DEL mykey k1 k2 str_key list_key set_key zset_key hash_key temp_key temp_key2 event volatile_key new_name a b user:1:name user:2:name user:3:name myobj num backup_test small_list source destination
```

---

## 5. Lists — Queues & Stacks

```redis
# =============================================
# SECTION 5: LISTS — QUEUES & STACKS
# =============================================

# Redis Lists are linked lists (doubly-linked under the hood — actually quicklists)
# O(1) push/pop at both ends, O(n) access by index

# LPUSH — push to the LEFT (head) of the list
LPUSH tasks "task3" "task2" "task1"
# List is now: task1, task2, task3 (last pushed is at head)

# RPUSH — push to the RIGHT (tail)
RPUSH tasks "task4" "task5"
# List is now: task1, task2, task3, task4, task5

# LRANGE — get range of elements (0 = first, -1 = last)
LRANGE tasks 0 -1
# Expected: task1, task2, task3, task4, task5

LRANGE tasks 0 2
# Expected: task1, task2, task3

LRANGE tasks -3 -1
# Expected: task3, task4, task5

# LLEN — get list length
LLEN tasks
# Expected: 5

# LPOP — pop from LEFT (head)
LPOP tasks
# Expected: "task1"

# RPOP — pop from RIGHT (tail)
RPOP tasks
# Expected: "task5"

LRANGE tasks 0 -1
# Expected: task2, task3, task4

# LPOP/RPOP with count (Redis 6.2+)
RPUSH numbers "1" "2" "3" "4" "5"
LPOP numbers 3
# Expected: 1, 2, 3

# LINDEX — get element by index (0-based)
RPUSH colors "red" "green" "blue" "yellow"
LINDEX colors 0
# Expected: "red"
LINDEX colors 2
# Expected: "blue"
LINDEX colors -1
# Expected: "yellow"

# LSET — set element at index
LSET colors 1 "purple"
LRANGE colors 0 -1
# Expected: red, purple, blue, yellow

# LINSERT — insert before/after a pivot element
LINSERT colors BEFORE "blue" "orange"
LRANGE colors 0 -1
# Expected: red, purple, orange, blue, yellow

LINSERT colors AFTER "blue" "pink"
LRANGE colors 0 -1
# Expected: red, purple, orange, blue, pink, yellow

# LREM — remove elements
# LREM key count element
# count > 0: remove from head
# count < 0: remove from tail
# count = 0: remove ALL occurrences
RPUSH dupes "a" "b" "a" "c" "a" "d" "a"
LREM dupes 2 "a"
LRANGE dupes 0 -1
# Expected: b, c, a, d, a (removed first 2 "a"s from head)

RPUSH dupes2 "a" "b" "a" "c" "a" "d" "a"
LREM dupes2 -2 "a"
LRANGE dupes2 0 -1
# Expected: a, b, a, c, d (removed last 2 "a"s from tail)

RPUSH dupes3 "a" "b" "a" "c" "a"
LREM dupes3 0 "a"
LRANGE dupes3 0 -1
# Expected: b, c (removed ALL "a"s)

# LTRIM — trim list to specified range (keeps only elements in range)
RPUSH trim_test "a" "b" "c" "d" "e" "f" "g"
LTRIM trim_test 1 4
LRANGE trim_test 0 -1
# Expected: b, c, d, e

# 🎯 INTERVIEW: Use LTRIM to keep only the latest N items
# Example: Keep only last 100 notifications
RPUSH notifications "notif1"
RPUSH notifications "notif2"
RPUSH notifications "notif3"
# After each push:
LTRIM notifications -100 -1
# This keeps only the last 100 items — perfect for bounded lists

# LPOS — find position of element (Redis 6.0.6+)
RPUSH search_list "a" "b" "c" "b" "d" "b"
LPOS search_list "b"
# Expected: 1 (first occurrence)
LPOS search_list "b" RANK 2
# Expected: 3 (second occurrence)
LPOS search_list "b" COUNT 0
# Expected: 1, 3, 5 (all occurrences)

# LMPOP — pop from multiple lists (Redis 7.0+)
RPUSH list1 "a" "b" "c"
RPUSH list2 "x" "y" "z"
LMPOP 2 list1 list2 LEFT COUNT 2
# Pops 2 elements from the left of the first non-empty list

# RPOPLPUSH — pop from tail of one list, push to head of another (DEPRECATED → use LMOVE)
RPUSH source_list "1" "2" "3"
RPUSH dest_list "a" "b"
RPOPLPUSH source_list dest_list
LRANGE source_list 0 -1
# Expected: 1, 2
LRANGE dest_list 0 -1
# Expected: 3, a, b

# LMOVE — more flexible version (Redis 6.2+)
RPUSH src "x" "y" "z"
RPUSH dst "1" "2"
LMOVE src dst LEFT RIGHT
LRANGE src 0 -1
# Expected: y, z
LRANGE dst 0 -1
# Expected: 1, 2, x

# =============================================
# BLOCKING OPERATIONS — Key for Job Queues!
# =============================================

# BLPOP — blocking left pop (waits for element with timeout)
# Open TWO redis-cli terminals:
# Terminal 1: BLPOP job_queue 30    (blocks for 30 seconds waiting)
# Terminal 2: RPUSH job_queue "new_job"
# Terminal 1 will immediately receive "new_job"

# BRPOP — blocking right pop
# Same as BLPOP but pops from right

# BLMOVE — blocking version of LMOVE
# BLMOVE source destination LEFT RIGHT timeout

# 🎯 INTERVIEW: Implementing a Queue vs Stack with Lists
# Queue (FIFO): RPUSH to add, LPOP to consume (or LPUSH + RPOP)
# Stack (LIFO): LPUSH to add, LPOP to consume (or RPUSH + RPOP)

# 🎯 INTERVIEW: Reliable Queue Pattern
# Problem: Worker crashes after LPOP but before processing
# Solution: RPOPLPUSH (or LMOVE) to a "processing" list
# If worker succeeds → LREM from processing list
# If worker crashes → move back from processing to queue
RPUSH work_queue "job1" "job2" "job3"
LMOVE work_queue processing LEFT RIGHT
# processing now has "job1", work_queue has "job2", "job3"
# After successful processing:
LREM processing 1 "job1"

# Cleanup
DEL tasks numbers colors dupes dupes2 dupes3 trim_test notifications search_list list1 list2 source_list dest_list src dst job_queue work_queue processing
```

---

## 6. Sets — Unique Collections

```redis
# =============================================
# SECTION 6: SETS — UNIQUE COLLECTIONS
# =============================================

# Sets: unordered collection of unique strings
# O(1) add, remove, membership check

# SADD — add members to set
SADD fruits "apple" "banana" "cherry" "apple"
# Expected: 3 (apple counted only once)

# SMEMBERS — get all members
SMEMBERS fruits
# Expected: apple, banana, cherry (order not guaranteed)

# SCARD — get set cardinality (size)
SCARD fruits
# Expected: 3

# SISMEMBER — check if member exists
SISMEMBER fruits "apple"
# Expected: 1 (true)
SISMEMBER fruits "grape"
# Expected: 0 (false)

# SMISMEMBER — check multiple members at once (Redis 6.2+)
SMISMEMBER fruits "apple" "grape" "banana"
# Expected: 1, 0, 1

# SRANDMEMBER — get random member(s) WITHOUT removing
SRANDMEMBER fruits
SRANDMEMBER fruits 2
# Gets 2 random unique members
SRANDMEMBER fruits -2
# Negative count: may return duplicates (with replacement)

# SPOP — remove and return random member(s)
SADD lottery "Alice" "Bob" "Charlie" "Diana" "Eve"
SPOP lottery
# Removes and returns 1 random member
SPOP lottery 2
# Removes and returns 2 random members
SMEMBERS lottery

# SREM — remove specific members
SADD colors "red" "blue" "green" "yellow"
SREM colors "blue" "yellow"
SMEMBERS colors
# Expected: red, green

# SMOVE — move member from one set to another
SADD set_a "1" "2" "3"
SADD set_b "4" "5"
SMOVE set_a set_b "2"
SMEMBERS set_a
# Expected: 1, 3
SMEMBERS set_b
# Expected: 2, 4, 5

# =============================================
# SET OPERATIONS — Very Important for Interviews!
# =============================================

SADD team_a "Alice" "Bob" "Charlie" "Diana"
SADD team_b "Charlie" "Diana" "Eve" "Frank"

# SUNION — all members in either set (OR)
SUNION team_a team_b
# Expected: Alice, Bob, Charlie, Diana, Eve, Frank

# SINTER — members in BOTH sets (AND)
SINTER team_a team_b
# Expected: Charlie, Diana

# SDIFF — members in first set but NOT in second (A - B)
SDIFF team_a team_b
# Expected: Alice, Bob

SDIFF team_b team_a
# Expected: Eve, Frank

# SUNIONSTORE / SINTERSTORE / SDIFFSTORE — store result in new key
SINTERSTORE common_members team_a team_b
SMEMBERS common_members
# Expected: Charlie, Diana

SDIFFSTORE only_in_a team_a team_b
SMEMBERS only_in_a
# Expected: Alice, Bob

# SSCAN — iterate set elements (cursor-based, non-blocking)
SADD big_set "a" "b" "c" "d" "e" "f" "g" "h" "i" "j"
SSCAN big_set 0 COUNT 5
# Returns cursor + batch of results

SSCAN big_set 0 MATCH "a*"
# Returns members matching pattern

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: Mutual Friends
SADD user:1:friends "Alice" "Bob" "Charlie" "Diana"
SADD user:2:friends "Bob" "Diana" "Eve" "Frank"
SINTER user:1:friends user:2:friends
# Expected: Bob, Diana

# Use Case 2: Friend Recommendations (friends of friends not already friends)
SDIFF user:2:friends user:1:friends
# Expected: Eve, Frank (recommend these to user 1)

# Use Case 3: Unique IP tracking
SADD ip:2024-01-15 "192.168.1.1" "10.0.0.1" "192.168.1.1"
SCARD ip:2024-01-15
# Expected: 2 (unique IPs)

# Use Case 4: Tags system
SADD article:100:tags "redis" "database" "nosql"
SADD article:200:tags "redis" "caching" "performance"
SADD article:300:tags "database" "sql" "nosql"

# Articles tagged with both "redis" AND "database"
SADD tag:redis "100" "200"
SADD tag:database "100" "300"
SINTER tag:redis tag:database
# Expected: 100

# Use Case 5: Online users
SADD online_users "user1" "user2" "user3"
SISMEMBER online_users "user2"
# Expected: 1 (online)
SREM online_users "user2"
SISMEMBER online_users "user2"
# Expected: 0 (offline)

# Cleanup
DEL fruits lottery colors set_a set_b team_a team_b common_members only_in_a big_set user:1:friends user:2:friends ip:2024-01-15 article:100:tags article:200:tags article:300:tags tag:redis tag:database online_users
```

---

## 7. Sorted Sets (ZSETs) — Leaderboards & Rankings

```redis
# =============================================
# SECTION 7: SORTED SETS (ZSETS) — LEADERBOARDS & RANKINGS
# =============================================

# Sorted Sets: like sets but each member has a score
# Ordered by score (ascending by default)
# O(log N) add/remove, O(log N + M) range queries

# ZADD — add members with scores
ZADD leaderboard 100 "Alice"
ZADD leaderboard 250 "Bob"
ZADD leaderboard 180 "Charlie"
ZADD leaderboard 320 "Diana"
ZADD leaderboard 90 "Eve"

# ZADD with options
ZADD leaderboard NX 200 "Frank"     # Only add if member doesn't exist
ZADD leaderboard XX 300 "Alice"     # Only update if member exists
ZADD leaderboard GT 50 "Bob"        # Update only if new score > current score (stays 250)
ZADD leaderboard LT 400 "Diana"     # Update only if new score < current score (stays 320)
ZADD leaderboard CH 500 "Alice"     # CH: return count of changed (not just added)

# ZSCORE — get score of member
ZSCORE leaderboard "Alice"
# Expected: "500" (we updated it)

# ZMSCORE — get scores of multiple members (Redis 6.2+)
ZMSCORE leaderboard "Alice" "Bob" "NonExistent"
# Expected: "500", "250", (nil)

# ZRANK — get rank (0-based, lowest score = rank 0)
ZRANK leaderboard "Eve"
# Expected: 0 (lowest score)
ZRANK leaderboard "Alice"
# Expected: 5 (highest score)

# ZREVRANK — get reverse rank (highest score = rank 0)
ZREVRANK leaderboard "Alice"
# Expected: 0 (top of leaderboard!)
ZREVRANK leaderboard "Eve"
# Expected: 5

# ZRANGE — get members by rank range (Redis 6.2+ supports extended syntax)
ZRANGE leaderboard 0 -1
# Expected: Eve, Charlie, Frank, Bob, Diana, Alice (ascending by score)

ZRANGE leaderboard 0 -1 WITHSCORES
# Shows member-score pairs

# Top 3 players (highest scores first)
ZRANGE leaderboard 0 2 REV WITHSCORES
# Expected: Alice(500), Diana(320), Bob(250)

# ZRANGEBYSCORE — get members by score range (older syntax)
ZRANGEBYSCORE leaderboard 100 300
# Expected: Charlie, Frank, Bob

ZRANGEBYSCORE leaderboard 100 300 WITHSCORES

# With LIMIT (pagination)
ZRANGEBYSCORE leaderboard 0 +inf WITHSCORES LIMIT 0 3
# First 3 results

ZRANGEBYSCORE leaderboard -inf +inf
# All members

# Exclusive ranges with (
ZRANGEBYSCORE leaderboard (100 300
# Score > 100 and <= 300

ZRANGEBYSCORE leaderboard (100 (300
# Score > 100 and < 300

# ZREVRANGEBYSCORE — reverse order
ZREVRANGEBYSCORE leaderboard 300 100 WITHSCORES

# ZRANGE with BYSCORE (modern syntax, Redis 6.2+)
ZRANGE leaderboard 100 300 BYSCORE WITHSCORES
ZRANGE leaderboard 300 100 BYSCORE REV WITHSCORES LIMIT 0 2

# ZRANGEBYLEX — lexicographic range (when all scores are same)
ZADD myset 0 "apple" 0 "banana" 0 "cherry" 0 "date" 0 "elderberry"
ZRANGEBYLEX myset "[b" "[d"
# Expected: banana, cherry (inclusive)
ZRANGEBYLEX myset "(a" "[c"
# Expected: banana, cherry (a exclusive, c inclusive)
ZRANGEBYLEX myset "-" "+"
# All members
ZRANGEBYLEX myset "[b" "+"
# Members >= "b"

# ZCARD — get number of members
ZCARD leaderboard
# Expected: 6

# ZCOUNT — count members in score range
ZCOUNT leaderboard 100 300
# Expected: 4 (Charlie, Frank, Bob, Diana... depends on current scores)

ZCOUNT leaderboard -inf +inf
# Expected: 6 (all members)

# ZLEXCOUNT — count members in lex range
ZLEXCOUNT myset "[b" "[d"

# ZINCRBY — increment score
ZINCRBY leaderboard 50 "Eve"
# Eve's score goes from 90 to 140
ZSCORE leaderboard "Eve"
# Expected: "140"

# ZREM — remove members
ZREM leaderboard "Frank"
ZRANGE leaderboard 0 -1 WITHSCORES

# ZREMRANGEBYRANK — remove by rank range
ZADD temp_scores 1 "a" 2 "b" 3 "c" 4 "d" 5 "e"
ZREMRANGEBYRANK temp_scores 0 1
# Removes lowest 2 ranks
ZRANGE temp_scores 0 -1
# Expected: c, d, e

# ZREMRANGEBYSCORE — remove by score range
ZADD temp_scores2 10 "x" 20 "y" 30 "z" 40 "w"
ZREMRANGEBYSCORE temp_scores2 15 35
ZRANGE temp_scores2 0 -1
# Expected: x, w

# ZPOPMIN / ZPOPMAX — pop lowest/highest scoring members
ZADD scores 10 "a" 20 "b" 30 "c" 40 "d"
ZPOPMIN scores 2
# Expected: a(10), b(20)
ZPOPMAX scores
# Expected: d(40)

# BZPOPMIN / BZPOPMAX — blocking versions
# BZPOPMIN key1 key2 timeout
# Waits for element or timeout

# ZRANDMEMBER — random member(s) (Redis 6.2+)
ZADD random_test 1 "a" 2 "b" 3 "c" 4 "d" 5 "e"
ZRANDMEMBER random_test
ZRANDMEMBER random_test 3
ZRANDMEMBER random_test 3 WITHSCORES
ZRANDMEMBER random_test -5
# Negative count: with replacement (may have duplicates)

# =============================================
# SET OPERATIONS ON SORTED SETS
# =============================================

ZADD math_students 85 "Alice" 92 "Bob" 78 "Charlie"
ZADD science_students 90 "Bob" 88 "Diana" 75 "Charlie"

# ZUNIONSTORE — union with score aggregation
ZUNIONSTORE all_students 2 math_students science_students
ZRANGE all_students 0 -1 WITHSCORES
# Alice:85, Charlie:153(78+75), Bob:182(92+90), Diana:88

# With WEIGHTS
ZUNIONSTORE weighted 2 math_students science_students WEIGHTS 1 2
ZRANGE weighted 0 -1 WITHSCORES
# Doubles science scores

# With AGGREGATE (SUM is default)
ZUNIONSTORE max_scores 2 math_students science_students AGGREGATE MAX
ZRANGE max_scores 0 -1 WITHSCORES
# Takes max score for each member

ZUNIONSTORE min_scores 2 math_students science_students AGGREGATE MIN
ZRANGE min_scores 0 -1 WITHSCORES
# Takes min score for each member

# ZINTERSTORE — intersection with score aggregation
ZINTERSTORE both_subjects 2 math_students science_students
ZRANGE both_subjects 0 -1 WITHSCORES
# Only Bob and Charlie (in both sets)

# ZDIFF (Redis 6.2+)
ZDIFF 2 math_students science_students WITHSCORES
# Alice:85 (only in math)

# ZDIFFSTORE
ZDIFFSTORE only_math 2 math_students science_students
ZRANGE only_math 0 -1 WITHSCORES

# ZUNION / ZINTER (Redis 6.2+ — return results without storing)
ZUNION 2 math_students science_students WITHSCORES
ZINTER 2 math_students science_students WITHSCORES

# ZRANGESTORE (Redis 6.2+) — store range results
ZADD full_board 100 "p1" 200 "p2" 300 "p3" 400 "p4" 500 "p5"
ZRANGESTORE top3 full_board 0 2 REV
ZRANGE top3 0 -1 WITHSCORES
# Top 3 stored in "top3" key

# ZSCAN — iterate sorted set (cursor-based)
ZSCAN leaderboard 0 COUNT 10
ZSCAN leaderboard 0 MATCH "A*"

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: Real-time Leaderboard
ZADD game:leaderboard 1500 "player1" 1200 "player2" 1800 "player3"
# Get top 10:
ZRANGE game:leaderboard 0 9 REV WITHSCORES
# Get player rank:
ZREVRANK game:leaderboard "player2"
# Update score:
ZINCRBY game:leaderboard 100 "player2"

# Use Case 2: Priority Queue
ZADD priority_queue 1 "critical_task" 5 "low_task" 3 "medium_task"
# Process highest priority first (lowest score):
ZPOPMIN priority_queue

# Use Case 3: Time-based events (score = timestamp)
ZADD events 1705363200 "event_a" 1705449600 "event_b" 1705536000 "event_c"
# Get events in time range:
ZRANGEBYSCORE events 1705363200 1705449600

# Use Case 4: Rate limiting with sorted sets
# Store request timestamps as score, user_request_id as member
# Count requests in sliding window

# Cleanup
DEL leaderboard myset temp_scores temp_scores2 scores random_test math_students science_students all_students weighted max_scores min_scores both_subjects only_math full_board top3 game:leaderboard priority_queue events
```

---

## 8. Hashes — Object Storage

```redis
# =============================================
# SECTION 8: HASHES — OBJECT STORAGE
# =============================================

# Hashes: field-value pairs under a single key (like a mini object/map)
# Perfect for representing objects: users, products, sessions

# HSET — set field(s) in hash
HSET user:1 name "Alice" age "30" email "alice@example.com" city "Barcelona"
# Expected: 4 (number of fields added)

# HGET — get a single field
HGET user:1 name
# Expected: "Alice"
HGET user:1 email
# Expected: "alice@example.com"

# HMGET — get multiple fields
HMGET user:1 name age city
# Expected: Alice, 30, Barcelona

# HGETALL — get ALL fields and values
HGETALL user:1
# Expected: name Alice age 30 email alice@example.com city Barcelona

# HKEYS — get all field names
HKEYS user:1
# Expected: name, age, email, city

# HVALS — get all values
HVALS user:1
# Expected: Alice, 30, alice@example.com, Barcelona

# HLEN — get number of fields
HLEN user:1
# Expected: 4

# HEXISTS — check if field exists
HEXISTS user:1 email
# Expected: 1
HEXISTS user:1 phone
# Expected: 0

# HSETNX — set field only if it doesn't exist
HSETNX user:1 name "Bob"
# Expected: 0 (name already exists)
HGET user:1 name
# Expected: "Alice" (unchanged)

HSETNX user:1 phone "+34123456789"
# Expected: 1 (new field added)

# HDEL — delete field(s)
HDEL user:1 phone
HEXISTS user:1 phone
# Expected: 0

# HINCRBY — increment integer field
HSET user:1 login_count "0"
HINCRBY user:1 login_count 1
HINCRBY user:1 login_count 1
HINCRBY user:1 login_count 1
HGET user:1 login_count
# Expected: "3"

# HINCRBYFLOAT — increment float field
HSET product:1 price "19.99"
HINCRBYFLOAT product:1 price 0.50
HGET product:1 price
# Expected: "20.49"

HINCRBYFLOAT product:1 price -2.00
HGET product:1 price
# Expected: "18.49"

# HRANDFIELD — get random field(s) (Redis 6.2+)
HSET colors:map red "#FF0000" blue "#0000FF" green "#00FF00" yellow "#FFFF00"
HRANDFIELD colors:map
HRANDFIELD colors:map 2
HRANDFIELD colors:map 2 WITHVALUES
HRANDFIELD colors:map -4
# Negative: with replacement

# HSTRLEN — get length of field value
HSTRLEN user:1 name
# Expected: 5 (length of "Alice")

# HSCAN — iterate hash fields (cursor-based)
HSCAN user:1 0 COUNT 10
HSCAN user:1 0 MATCH "n*"

# =============================================
# 🎯 INTERVIEW: Hash vs String for Objects
# =============================================

# Approach 1: Separate string keys
SET user:1:name "Alice"
SET user:1:age "30"
SET user:1:email "alice@example.com"
# Problems: More memory, more operations, no atomic multi-field update

# Approach 2: Serialized JSON in string
SET user:1:json "{\"name\":\"Alice\",\"age\":30,\"email\":\"alice@example.com\"}"
# Problems: Must read/write entire object, no partial update, more CPU for ser/deser

# Approach 3: Hash (RECOMMENDED)
HSET user:1:hash name "Alice" age "30" email "alice@example.com"
# Benefits: Partial reads/writes, atomic field updates, memory efficient (ziplist encoding for small hashes)

# 🎯 INTERVIEW: Memory Optimization
# Small hashes (< hash-max-ziplist-entries fields, each < hash-max-ziplist-value bytes)
# are stored as ziplist — VERY memory efficient!
OBJECT ENCODING user:1
# Expected: "listpack" (or "ziplist" in older versions)

# Check current limits:
CONFIG GET hash-max-ziplist-entries
CONFIG GET hash-max-ziplist-value
# Default: 128 entries, 64 bytes per value

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: User Profile
HSET user:profile:1001 username "alice" display_name "Alice Smith" bio "Redis enthusiast" followers "1523" following "342" created_at "2024-01-15"

# Get specific fields (efficient partial read)
HMGET user:profile:1001 username followers

# Increment followers atomically
HINCRBY user:profile:1001 followers 1
HGET user:profile:1001 followers

# Use Case 2: Shopping Cart
HSET cart:user:1001 "product:100" "2" "product:200" "1" "product:300" "3"
# field = product ID, value = quantity

# Add item
HSET cart:user:1001 "product:400" "1"

# Update quantity
HINCRBY cart:user:1001 "product:100" 1

# Remove item
HDEL cart:user:1001 "product:200"

# Get entire cart
HGETALL cart:user:1001

# Get cart size
HLEN cart:user:1001

# Use Case 3: Configuration Store
HSET config:app max_connections "100" timeout_ms "5000" feature_dark_mode "true" version "2.1.0"
HGET config:app feature_dark_mode
HSET config:app feature_dark_mode "false"

# Use Case 4: Session Store
HSET session:abc123 user_id "1001" username "alice" role "admin" ip "192.168.1.1" login_time "1705363200"
EXPIRE session:abc123 3600

# Cleanup
DEL user:1 product:1 colors:map user:1:name user:1:age user:1:email user:1:json user:1:hash user:profile:1001 cart:user:1001 config:app session:abc123
```

---

## 9. HyperLogLog — Cardinality Estimation

```redis
# =============================================
# SECTION 9: HYPERLOGLOG — CARDINALITY ESTIMATION
# =============================================

# HyperLogLog: probabilistic data structure for counting unique elements
# Uses only ~12KB regardless of cardinality!
# Standard error: 0.81%
# 🎯 INTERVIEW FAVORITE: "How do you count unique visitors with millions of users?"

# PFADD — add elements
PFADD visitors:2024-01-15 "user1" "user2" "user3" "user1"
# Expected: 1 (at least one new element was added — user1 is duplicate)

PFADD visitors:2024-01-15 "user1" "user2"
# Expected: 0 (no new elements)

# PFCOUNT — get approximate cardinality
PFCOUNT visitors:2024-01-15
# Expected: 3

# Add more users
PFADD visitors:2024-01-15 "user4" "user5" "user6"
PFCOUNT visitors:2024-01-15
# Expected: 6

# PFMERGE — merge multiple HyperLogLogs
PFADD visitors:2024-01-15 "user1" "user2" "user3"
PFADD visitors:2024-01-16 "user2" "user3" "user4" "user5"
PFADD visitors:2024-01-17 "user5" "user6" "user7"

# Get unique visitors across all 3 days
PFMERGE visitors:week visitors:2024-01-15 visitors:2024-01-16 visitors:2024-01-17
PFCOUNT visitors:week
# Expected: ~7 (user1 through user7)

# PFCOUNT on multiple keys (auto-merges temporarily)
PFCOUNT visitors:2024-01-15 visitors:2024-01-16
# Unique visitors across Jan 15 + Jan 16

# 🎯 INTERVIEW: HyperLogLog vs SET
# SET: Exact count, but O(N) memory
# HLL: ~12KB always, but ~0.81% error
# For 100M unique users:
#   SET: ~1.6GB memory
#   HLL: ~12KB memory!

# Simulating large cardinality (try this to see the approximation)
# In a script you'd do: for i in range(100000): PFADD test_hll user_{i}
# PFCOUNT test_hll → ~99,700-100,300 (within 0.81% error)

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: Unique Page Views
PFADD page:/home "ip1" "ip2" "ip3" "ip1"
PFCOUNT page:/home
# ~3 unique visitors to homepage

# Use Case 2: Unique Search Queries
PFADD search:daily:2024-01-15 "redis tutorial" "best database" "redis tutorial"
PFCOUNT search:daily:2024-01-15
# ~2 unique queries

# Use Case 3: A/B Test Reach
PFADD experiment:variant_a "u1" "u2" "u3"
PFADD experiment:variant_b "u4" "u5" "u6" "u7"
PFCOUNT experiment:variant_a
PFCOUNT experiment:variant_b
# Compare reach of each variant

# Use Case 4: Monthly active users from daily logs
PFMERGE mau:january visitors:2024-01-15 visitors:2024-01-16 visitors:2024-01-17
PFCOUNT mau:january

# Cleanup
DEL visitors:2024-01-15 visitors:2024-01-16 visitors:2024-01-17 visitors:week page:/home search:daily:2024-01-15 experiment:variant_a experiment:variant_b mau:january
```

---

## 10. Bitmaps — Compact Boolean Storage

```redis
# =============================================
# SECTION 10: BITMAPS — COMPACT BOOLEAN STORAGE
# =============================================

# Bitmaps: string of bits, each bit = 0 or 1
# Incredibly memory efficient for boolean flags
# 🎯 INTERVIEW: Track daily active users, feature flags, attendance

# SETBIT — set bit at offset
SETBIT daily_active:2024-01-15 1001 1   # user 1001 was active
SETBIT daily_active:2024-01-15 1002 1   # user 1002 was active
SETBIT daily_active:2024-01-15 1003 1   # user 1003 was active
SETBIT daily_active:2024-01-15 2000 1   # user 2000 was active

# GETBIT — get bit at offset
GETBIT daily_active:2024-01-15 1001
# Expected: 1 (active)
GETBIT daily_active:2024-01-15 1004
# Expected: 0 (not active)

# BITCOUNT — count set bits (1s) in range
BITCOUNT daily_active:2024-01-15
# Expected: 4 (four active users)

# BITCOUNT with byte range
# BITCOUNT key start end [BYTE|BIT]
SETBIT mybits 0 1
SETBIT mybits 1 1
SETBIT mybits 2 1
SETBIT mybits 7 1
BITCOUNT mybits 0 0
# Counts bits in first byte (offsets 0-7)

# BITPOS — find first bit set to 0 or 1
SETBIT pos_test 5 1
SETBIT pos_test 10 1
SETBIT pos_test 15 1
BITPOS pos_test 1
# Expected: 5 (first bit set to 1)
BITPOS pos_test 0
# Expected: 0 (first bit set to 0)

# =============================================
# BITOP — Bitwise operations between keys
# =============================================

SETBIT day1 1 1    # user 1 active day 1
SETBIT day1 2 1    # user 2 active day 1
SETBIT day1 3 1    # user 3 active day 1

SETBIT day2 2 1    # user 2 active day 2
SETBIT day2 3 1    # user 3 active day 2
SETBIT day2 4 1    # user 4 active day 2

SETBIT day3 1 1    # user 1 active day 3
SETBIT day3 3 1    # user 3 active day 3
SETBIT day3 5 1    # user 5 active day 3

# AND — users active on ALL days
BITOP AND active_all_days day1 day2 day3
GETBIT active_all_days 1
# Expected: 0 (user 1 not active day 2)
GETBIT active_all_days 3
# Expected: 1 (user 3 active all days!)
BITCOUNT active_all_days
# Expected: 1 (only user 3)

# OR — users active on ANY day
BITOP OR active_any_day day1 day2 day3
BITCOUNT active_any_day
# Expected: 5 (users 1,2,3,4,5)

# XOR — users active on odd number of days
BITOP XOR active_xor day1 day2 day3

# NOT — users NOT active on day1
BITOP NOT inactive_day1 day1

# BITFIELD — treat string as array of integers
BITFIELD myfield SET u8 0 200     # Set unsigned 8-bit int at offset 0 = 200
BITFIELD myfield SET u8 8 150     # Set unsigned 8-bit int at offset 8 = 150
BITFIELD myfield GET u8 0         # Get value at offset 0
# Expected: 200
BITFIELD myfield GET u8 8
# Expected: 150

BITFIELD myfield INCRBY u8 0 10   # Increment by 10
BITFIELD myfield GET u8 0
# Expected: 210

# Overflow handling
BITFIELD myfield SET u8 0 250
BITFIELD myfield OVERFLOW WRAP INCRBY u8 0 10
# Expected: 4 (wraps around: 250+10=260, 260%256=4)

BITFIELD myfield SET u8 0 250
BITFIELD myfield OVERFLOW SAT INCRBY u8 0 10
# Expected: 255 (saturates at max)

BITFIELD myfield SET u8 0 250
BITFIELD myfield OVERFLOW FAIL INCRBY u8 0 10
# Expected: (nil) (fails, no change)

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: Daily Active Users (DAU)
# Bit offset = user ID, 1 = active, 0 = inactive
# 10 million users = only ~1.2MB per day!

# Use Case 2: Feature Flag per user
SETBIT feature:dark_mode 1001 1
SETBIT feature:dark_mode 1002 0
SETBIT feature:dark_mode 1003 1
GETBIT feature:dark_mode 1001
# Expected: 1 (dark mode enabled for user 1001)

# Use Case 3: Attendance tracking
# 365 days × 10M users = ~456MB for entire year!

# Use Case 4: User retention
# Users active on BOTH day 1 AND day 7:
# BITOP AND retention day1 day7
# BITCOUNT retention → retained users

# 🎯 INTERVIEW: Bitmap vs SET for DAU
# 10M users, ~50% active daily:
#   SET: 5M entries × ~40 bytes = ~200MB
#   Bitmap: 10M bits = ~1.2MB
# Bitmap wins by 160x!

# But if only 100 users out of 10M are active:
#   SET: 100 entries × ~40 bytes = ~4KB
#   Bitmap: 10M bits = ~1.2MB
# SET wins! Bitmaps best when high density.

# Cleanup
DEL daily_active:2024-01-15 mybits pos_test day1 day2 day3 active_all_days active_any_day active_xor inactive_day1 myfield feature:dark_mode
```

---

## 11. Geospatial — Location-Based Features

```redis
# =============================================
# SECTION 11: GEOSPATIAL — LOCATION-BASED FEATURES
# =============================================

# Geospatial: store coordinates and find nearby points
# Uses Sorted Sets internally (score = geohash)
# 🎯 INTERVIEW: Uber drivers nearby, restaurant finder, delivery tracking

# GEOADD — add locations (longitude, latitude, member)
GEOADD restaurants -73.9857 40.7484 "Shake Shack"
GEOADD restaurants -73.9712 40.7831 "Le Bernardin"
GEOADD restaurants -73.9654 40.7829 "Peter Luger"
GEOADD restaurants -74.0060 40.7128 "Joe's Pizza"
GEOADD restaurants -73.9855 40.7580 "Eataly"

# Add Barcelona locations
GEOADD places 2.1734 41.3851 "Sagrada Familia"
GEOADD places 2.1744 41.3818 "Casa Batllo"
GEOADD places 2.1769 41.4036 "Park Guell"
GEOADD places 2.1686 41.3794 "La Rambla"
GEOADD places 2.1894 41.3892 "Barceloneta Beach"

# GEOPOS — get coordinates
GEOPOS places "Sagrada Familia"
# Expected: longitude, latitude

GEOPOS places "Sagrada Familia" "Park Guell"

# GEODIST — distance between two members
GEODIST places "Sagrada Familia" "Park Guell"
# Default unit: meters

GEODIST places "Sagrada Familia" "Park Guell" km
# In kilometers

GEODIST places "Sagrada Familia" "Park Guell" mi
# In miles

GEODIST places "La Rambla" "Barceloneta Beach" m
# In meters

# GEOHASH — get geohash of member
GEOHASH places "Sagrada Familia"
# Returns 11-char geohash string

# GEOSEARCH — find members within radius or box (Redis 6.2+)
# Find places within 2km of Sagrada Familia
GEOSEARCH places FROMMEMBER "Sagrada Familia" BYRADIUS 2 km ASC
# Returns nearby places sorted by distance

GEOSEARCH places FROMMEMBER "Sagrada Familia" BYRADIUS 2 km ASC WITHCOORD WITHDIST COUNT 3
# With coordinates and distances, limit to 3

# Search from arbitrary coordinates
GEOSEARCH places FROMLONLAT 2.17 41.39 BYRADIUS 1 km ASC WITHCOORD WITHDIST

# Search within a box
GEOSEARCH places FROMMEMBER "Sagrada Familia" BYBOX 5 5 km ASC WITHCOORD WITHDIST

# GEOSEARCHSTORE — store search results (Redis 6.2+)
GEOSEARCHSTORE nearby_places places FROMMEMBER "Sagrada Familia" BYRADIUS 2 km ASC COUNT 5
ZRANGE nearby_places 0 -1 WITHSCORES

# GEOSEARCHSTORE with STOREDIST — store distances as scores
GEOSEARCHSTORE nearby_distances places FROMMEMBER "Sagrada Familia" BYRADIUS 2 km ASC COUNT 5 STOREDIST
ZRANGE nearby_distances 0 -1 WITHSCORES

# Old commands (still work but prefer GEOSEARCH):
# GEORADIUS places 2.17 41.39 2 km ASC WITHCOORD WITHDIST COUNT 5
# GEORADIUSBYMEMBER places "Sagrada Familia" 2 km ASC WITHCOORD WITHDIST

# Since Geo uses Sorted Sets internally, you can:
ZRANGE places 0 -1 WITHSCORES
# Shows all members with their geohash scores

ZREM places "Park Guell"
# Remove a location

ZCARD places
# Count locations

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: Uber — Find nearby drivers
GEOADD drivers 2.170 41.385 "driver:1"
GEOADD drivers 2.172 41.386 "driver:2"
GEOADD drivers 2.180 41.390 "driver:3"
GEOADD drivers 2.150 41.370 "driver:4"

# Rider at location (2.171, 41.385) wants drivers within 1km
GEOSEARCH drivers FROMLONLAT 2.171 41.385 BYRADIUS 1 km ASC WITHDIST COUNT 5

# Use Case 2: Restaurant finder
# Already set up above — just GEOSEARCH with user's current location

# Use Case 3: Delivery tracking
GEOADD delivery:order:1001 2.170 41.385 "current_position"
# Update position:
GEOADD delivery:order:1001 2.175 41.390 "current_position"
GEOPOS delivery:order:1001 "current_position"

# Distance to destination:
GEOADD delivery:order:1001 2.180 41.400 "destination"
GEODIST delivery:order:1001 "current_position" "destination" km

# Cleanup
DEL restaurants places nearby_places nearby_distances drivers delivery:order:1001
```

---

## 12. Streams — Event Sourcing & Message Queues

```redis
# =============================================
# SECTION 12: STREAMS — EVENT SOURCING & MESSAGE QUEUES
# =============================================

# Streams: append-only log data structure
# Like Kafka topics but in Redis!
# 🎯 INTERVIEW: Event sourcing, activity feeds, message queues

# XADD — add entry to stream
# * = auto-generate ID (timestamp-based)
XADD events * user "alice" action "login" ip "192.168.1.1"
XADD events * user "bob" action "purchase" item "laptop" price "999"
XADD events * user "alice" action "page_view" page "/products"
XADD events * user "charlie" action "login" ip "10.0.0.1"
XADD events * user "bob" action "logout"

# XADD with custom ID
XADD custom_stream 1-0 key "first"
XADD custom_stream 2-0 key "second"
XADD custom_stream 3-0 key "third"

# XADD with MAXLEN (cap stream size)
XADD capped_stream MAXLEN 1000 * data "entry"
# Keeps at most 1000 entries

# XADD with MAXLEN ~ (approximate trimming — more efficient)
XADD capped_stream MAXLEN ~ 1000 * data "entry"

# XADD with MINID (trim entries older than given ID)
XADD trimmed_stream MINID ~ 1234567890000-0 * data "entry"

# XLEN — get stream length
XLEN events
# Expected: 5

# XRANGE — read entries in order (oldest to newest)
XRANGE events - +
# - = minimum ID, + = maximum ID (all entries)

XRANGE events - + COUNT 2
# First 2 entries

# XRANGE with specific ID range
# Use IDs from XRANGE output above
# XRANGE events 1705363200000-0 1705363210000-0

# XREVRANGE — read entries in reverse order (newest to oldest)
XREVRANGE events + -
XREVRANGE events + - COUNT 2
# Latest 2 entries

# XREAD — read entries (can block!)
XREAD COUNT 2 STREAMS events 0
# Read from beginning

# XREAD BLOCK — blocking read (waits for new entries)
# Terminal 1: XREAD BLOCK 5000 STREAMS events $
# ($ means only new entries, blocks for 5 seconds)
# Terminal 2: XADD events * user "diana" action "signup"
# Terminal 1 will receive the new entry!

# XINFO — stream information
XINFO STREAM events
XINFO STREAM events FULL

# XTRIM — trim stream
XADD trim_test * a "1"
XADD trim_test * b "2"
XADD trim_test * c "3"
XADD trim_test * d "4"
XADD trim_test * e "5"
XTRIM trim_test MAXLEN 3
XRANGE trim_test - +
# Only last 3 entries remain

# XDEL — delete specific entry
XADD del_test * msg "keep"
XADD del_test * msg "delete_me"
XADD del_test * msg "keep_too"
XRANGE del_test - +
# Note the ID of "delete_me" and use it:
# XDEL del_test <id>

# =============================================
# CONSUMER GROUPS — The Power Feature!
# =============================================

# Create a stream with some data
XADD orders * order_id "1001" product "laptop" qty "1"
XADD orders * order_id "1002" product "phone" qty "2"
XADD orders * order_id "1003" product "tablet" qty "1"
XADD orders * order_id "1004" product "headphones" qty "3"
XADD orders * order_id "1005" product "charger" qty "5"

# XGROUP CREATE — create consumer group
XGROUP CREATE orders order_processors 0
# 0 = start from beginning, $ = only new entries

# XREADGROUP — read as consumer in a group
# Each message delivered to only ONE consumer in the group!
XREADGROUP GROUP order_processors worker1 COUNT 2 STREAMS orders >
# worker1 gets first 2 messages

XREADGROUP GROUP order_processors worker2 COUNT 2 STREAMS orders >
# worker2 gets next 2 messages

XREADGROUP GROUP order_processors worker1 COUNT 2 STREAMS orders >
# worker1 gets next available (message 5)

# XACK — acknowledge processed message
# Use IDs from XREADGROUP output:
# XACK orders order_processors <id1> <id2>

# XPENDING — check pending (unacknowledged) messages
XPENDING orders order_processors - + 10
# Shows unacknowledged messages

XPENDING orders order_processors
# Summary: total pending, min/max ID, consumers with pending

# XCLAIM — claim pending messages (e.g., if a worker crashed)
# XCLAIM orders order_processors worker3 60000 <id>
# Claims message for worker3 if it's been pending > 60 seconds

# XAUTOCLAIM — auto-claim idle messages (Redis 6.2+)
# XAUTOCLAIM orders order_processors worker3 60000 0 COUNT 10
# Auto-claims messages idle > 60 seconds

# XINFO GROUPS — info about consumer groups
XINFO GROUPS orders

# XINFO CONSUMERS — info about consumers in a group
XINFO CONSUMERS orders order_processors

# XGROUP DELCONSUMER — remove consumer from group
# XGROUP DELCONSUMER orders order_processors worker3

# XGROUP DESTROY — delete entire consumer group
# XGROUP DESTROY orders order_processors

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: Activity/Audit Log
XADD audit_log * user "admin" action "config_change" detail "max_connections=200"
XADD audit_log * user "alice" action "data_export" detail "users_table"
# Immutable, time-ordered, queryable

# Use Case 2: Event Sourcing
XADD account:1001:events * type "DEPOSIT" amount "1000"
XADD account:1001:events * type "WITHDRAW" amount "200"
XADD account:1001:events * type "DEPOSIT" amount "500"
# Replay events to reconstruct state

# Use Case 3: Real-time Analytics Pipeline
# Producers: XADD clickstream * page "/home" user_id "1001"
# Consumer group processes clicks for analytics

# Use Case 4: Task Distribution
# Multiple workers read from same stream via consumer group
# If worker crashes, XPENDING + XCLAIM redistributes work

# Cleanup
DEL events custom_stream capped_stream trimmed_stream trim_test del_test orders audit_log account:1001:events
```

---

## 13. Pub/Sub — Real-Time Messaging

```redis
# =============================================
# SECTION 13: PUB/SUB — REAL-TIME MESSAGING
# =============================================

# Pub/Sub: fire-and-forget messaging
# ⚠️ No persistence — messages lost if no subscriber is listening!
# 🎯 INTERVIEW: Real-time notifications, chat, live updates

# You need TWO terminals for this section!

# =============================================
# Terminal 1 (Subscriber):
# =============================================

# SUBSCRIBE — subscribe to channel(s)
SUBSCRIBE news
# (Blocks and waits for messages)

# SUBSCRIBE to multiple channels
# SUBSCRIBE news sports weather

# PSUBSCRIBE — subscribe with pattern
# PSUBSCRIBE news.*
# Matches: news.tech, news.sports, news.weather

# PSUBSCRIBE user:*:notifications
# Matches: user:1001:notifications, user:1002:notifications

# =============================================
# Terminal 2 (Publisher):
# =============================================

# PUBLISH — send message to channel
PUBLISH news "Breaking: Redis 8.0 released!"
# Expected: (integer) 1 (1 subscriber received it)

PUBLISH news "Redis now supports vector search!"
# Expected: (integer) 1

PUBLISH sports "Barcelona wins Champions League!"
# Expected: (integer) 0 (no one subscribed to "sports")

# PUBSUB — introspect pub/sub system
PUBSUB CHANNELS
# Lists channels with active subscribers

PUBSUB CHANNELS news*
# Channels matching pattern

PUBSUB NUMSUB news sports
# Number of subscribers per channel

PUBSUB NUMPAT
# Number of pattern subscriptions

# PUBSUB SHARDCHANNELS (Redis 7.0+ cluster mode)
# PUBSUB SHARDNUMSUB

# =============================================
# Terminal 1: Press Ctrl+C to stop subscribing
# =============================================

# UNSUBSCRIBE — unsubscribe from channel(s) (usually handled by client library)
# UNSUBSCRIBE news
# PUNSUBSCRIBE news.*

# =============================================
# 🎯 INTERVIEW: Pub/Sub vs Streams
# =============================================
# Pub/Sub:
#   - Fire and forget
#   - No persistence
#   - No consumer groups
#   - No replay
#   - Simpler, lower latency
#   - Good for: real-time notifications, cache invalidation

# Streams:
#   - Persistent
#   - Consumer groups
#   - Replay from any point
#   - Acknowledgment (XACK)
#   - Good for: reliable message processing, event sourcing

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# Use Case 1: Chat Room
# Subscribe: SUBSCRIBE chat:room:general
# Publish: PUBLISH chat:room:general "Alice: Hello everyone!"

# Use Case 2: Cache Invalidation
# Subscribe: PSUBSCRIBE cache:invalidate:*
# Publish: PUBLISH cache:invalidate:user:1001 "updated"
# All app servers subscribed will invalidate their local cache

# Use Case 3: Live Score Updates
# Subscribe: SUBSCRIBE scores:football scores:basketball
# Publish: PUBLISH scores:football "Barcelona 2-1 Real Madrid (45')"

# Use Case 4: System Events
# Subscribe: PSUBSCRIBE system:*
# Publish: PUBLISH system:deploy "v2.1.0 deployed to production"
# Publish: PUBLISH system:alert "High CPU usage on server-5"

# Keyspace notifications (built-in pub/sub)
# Enable: CONFIG SET notify-keyspace-events KEA
# Subscribe: SUBSCRIBE __keyevent@0__:expired
# Now when any key expires, you get notified!
```

---

## 14. Transactions — MULTI/EXEC

```redis
# =============================================
# SECTION 14: TRANSACTIONS — MULTI/EXEC
# =============================================

# Redis transactions: batch commands atomically
# All commands between MULTI and EXEC run without interruption
# ⚠️ NOT like SQL transactions — no ROLLBACK!
# 🎯 INTERVIEW: Atomicity without locks

# Basic transaction
MULTI
SET account:alice:balance "1000"
SET account:bob:balance "500"
EXEC
# Both SET commands executed atomically

# Transaction with reads (reads are queued, not immediate!)
SET counter "10"
MULTI
INCR counter
INCR counter
INCR counter
EXEC
# Expected: 11, 12, 13
GET counter
# Expected: "13"

# DISCARD — cancel transaction
MULTI
SET x "1"
SET y "2"
DISCARD
# Transaction cancelled, nothing executed
GET x
# Expected: (nil) or previous value

# =============================================
# WATCH — Optimistic Locking (CAS)
# =============================================

# WATCH key before MULTI — if key changes before EXEC, transaction fails
SET watched_key "original"

WATCH watched_key
# Now open another terminal and: SET watched_key "changed"
MULTI
SET watched_key "my_update"
EXEC
# If another client changed watched_key → returns (nil) — transaction aborted!
# If no one changed it → returns OK

# 🎯 INTERVIEW: Transfer money atomically
# Scenario: Transfer $200 from Alice to Bob

SET account:alice "1000"
SET account:bob "500"

# Step 1: Watch both accounts
WATCH account:alice account:bob

# Step 2: Read current balances (outside MULTI)
GET account:alice
# "1000"
GET account:bob
# "500"

# Step 3: Start transaction
MULTI
DECRBY account:alice 200
INCRBY account:bob 200
EXEC
# If no one modified accounts → succeeds
# If someone did → EXEC returns nil, retry!

GET account:alice
# Expected: "800"
GET account:bob
# Expected: "700"

# UNWATCH — cancel all watches
WATCH account:alice
UNWATCH
# Watches cleared

# =============================================
# Transaction Error Handling
# =============================================

# Error during queuing (syntax error) → entire transaction rejected
MULTI
SET a "1"
WRONGCOMMAND  
SET b "2"
EXEC
# Returns error, NOTHING executed

# Error during execution (wrong type) → other commands still execute!
SET str_val "hello"
MULTI
INCR str_val
SET good_key "works"
EXEC
# INCR fails (str_val not numeric), but SET good_key succeeds!
GET good_key
# Expected: "works"

# ⚠️ This is why Redis transactions are NOT like SQL transactions
# No rollback on runtime errors!

# =============================================
# 🎯 INTERVIEW: MULTI/EXEC vs Lua Scripts
# =============================================
# MULTI/EXEC:
#   - Can WATCH for optimistic locking
#   - Commands queued, executed sequentially
#   - No conditional logic between commands
#   - Can fail partially on runtime errors
#
# Lua Scripts:
#   - Atomic execution (all or nothing in practice)
#   - CAN have conditional logic, loops
#   - No WATCH needed
#   - Better for complex atomic operations
#   - EVAL command

# Cleanup
DEL counter watched_key account:alice account:bob str_val good_key a b x y
```

---

## 15. Lua Scripting — Atomic Operations

```redis
# =============================================
# SECTION 15: LUA SCRIPTING — ATOMIC OPERATIONS
# =============================================

# Lua scripts execute atomically in Redis (blocks other commands)
# 🎯 INTERVIEW: Complex atomic operations, rate limiters, distributed locks

# EVAL — execute Lua script
# EVAL script numkeys key1 key2 ... arg1 arg2 ...

# Hello World
EVAL "return 'Hello from Lua!'" 0
# Expected: "Hello from Lua!"

# Simple math
EVAL "return 1 + 2" 0
# Expected: 3

# Access keys with KEYS table (1-indexed in Lua!)
SET mykey "hello"
EVAL "return redis.call('GET', KEYS[1])" 1 mykey
# Expected: "hello"

# Access arguments with ARGV table
EVAL "return ARGV[1] .. ' ' .. ARGV[2]" 0 "Hello" "World"
# Expected: "Hello World"

# SET and GET in one script
EVAL "redis.call('SET', KEYS[1], ARGV[1]); return redis.call('GET', KEYS[1])" 1 script_key "script_value"
# Expected: "script_value"

# Conditional logic (not possible with MULTI/EXEC!)
SET balance "100"
EVAL "local bal = tonumber(redis.call('GET', KEYS[1])); if bal >= tonumber(ARGV[1]) then redis.call('DECRBY', KEYS[1], ARGV[1]); return 1 else return 0 end" 1 balance "50"
# Expected: 1 (success, balance was >= 50)
GET balance
# Expected: "50"

EVAL "local bal = tonumber(redis.call('GET', KEYS[1])); if bal >= tonumber(ARGV[1]) then redis.call('DECRBY', KEYS[1], ARGV[1]); return 1 else return 0 end" 1 balance "80"
# Expected: 0 (failed, balance only 50)
GET balance
# Expected: "50" (unchanged)

# =============================================
# 🎯 INTERVIEW: Rate Limiter in Lua
# =============================================

# Fixed Window Rate Limiter
EVAL "\
local current = redis.call('INCR', KEYS[1])\
if current == 1 then\
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))\
end\
if current > tonumber(ARGV[1]) then\
    return 0\
else\
    return 1\
end" 1 "ratelimit:user:1001" "10" "60"
# KEYS[1] = rate limit key
# ARGV[1] = max requests (10)
# ARGV[2] = window in seconds (60)
# Returns 1 = allowed, 0 = rate limited

# =============================================
# 🎯 INTERVIEW: Sliding Window Rate Limiter in Lua
# =============================================

EVAL "\
local key = KEYS[1]\
local now = tonumber(ARGV[1])\
local window = tonumber(ARGV[2])\
local limit = tonumber(ARGV[3])\
local clearBefore = now - window\
redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)\
local count = redis.call('ZCARD', key)\
if count < limit then\
    redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))\
    redis.call('EXPIRE', key, window)\
    return 1\
else\
    return 0\
end" 1 "sliding:user:1001" "1705363200" "60" "10"
# Uses sorted set with timestamps as scores
# Removes old entries outside window, checks count

# =============================================
# 🎯 INTERVIEW: Compare-And-Swap (CAS) in Lua
# =============================================

SET cas_key "old_value"
EVAL "\
local current = redis.call('GET', KEYS[1])\
if current == ARGV[1] then\
    redis.call('SET', KEYS[1], ARGV[2])\
    return 1\
else\
    return 0\
end" 1 cas_key "old_value" "new_value"
# Expected: 1 (swapped successfully)
GET cas_key
# Expected: "new_value"

# =============================================
# 🎯 INTERVIEW: Atomic Transfer in Lua
# =============================================

SET account:A "1000"
SET account:B "500"

EVAL "\
local from_bal = tonumber(redis.call('GET', KEYS[1]))\
local amount = tonumber(ARGV[1])\
if from_bal >= amount then\
    redis.call('DECRBY', KEYS[1], amount)\
    redis.call('INCRBY', KEYS[2], amount)\
    return 1\
else\
    return 0\
end" 2 account:A account:B "300"
# Expected: 1
GET account:A
# Expected: "700"
GET account:B
# Expected: "800"

# =============================================
# EVALSHA — Use cached script hash
# =============================================

# Load script (returns SHA1 hash)
SCRIPT LOAD "return redis.call('GET', KEYS[1])"
# Returns something like: "a42059b356c875f0717db19a51f6aaa9161571a2"

# Execute by SHA
# EVALSHA <sha> 1 mykey

# Check if script exists
# SCRIPT EXISTS <sha1> <sha2>

# Flush script cache
# SCRIPT FLUSH

# =============================================
# EVAL vs EVALRO (Redis 7.0+)
# =============================================

# EVALRO — read-only eval, can run on replicas
# EVALRO "return redis.call('GET', KEYS[1])" 1 mykey

# =============================================
# redis.call vs redis.pcall
# =============================================

# redis.call — raises error on failure (stops script)
# redis.pcall — returns error as table (script continues)

EVAL "\
local ok, err = pcall(redis.call, 'INCR', 'not_a_number_key')\
if not ok then\
    return 'Error handled: ' .. tostring(err)\
else\
    return 'Success'\
end" 0

# =============================================
# 🎯 INTERVIEW: Functions (Redis 7.0+)
# =============================================

# Redis Functions are the modern replacement for EVAL scripts
# They are persistent (survive restart) and named

# Register a function library:
# FUNCTION LOAD "#!lua name=mylib\nredis.register_function('myfunc', function(keys, args) return redis.call('GET', keys[1]) end)"

# Call it:
# FCALL myfunc 1 mykey

# List functions:
# FUNCTION LIST

# Dump/restore functions:
# FUNCTION DUMP
# FUNCTION RESTORE <serialized>

# Cleanup
DEL mykey script_key balance cas_key account:A account:B ratelimit:user:1001 sliding:user:1001
```

---

## 16. Pipelining — Batch Operations

```redis
# =============================================
# SECTION 16: PIPELINING — BATCH OPERATIONS
# =============================================

# Pipelining: send multiple commands without waiting for each response
# Reduces round-trip time (RTT) — huge performance gain!
# 🎯 INTERVIEW: "How do you optimize Redis performance?"

# In redis-cli, you can use --pipe flag
# In client libraries, use pipeline objects

# redis-cli doesn't have native pipeline support, but you can simulate:
# From bash:
# echo -e "SET key1 val1\nSET key2 val2\nSET key3 val3\nGET key1\nGET key2\nGET key3" | redis-cli --pipe

# Or use --pipe with a file:
# cat commands.txt | redis-cli --pipe

# In redis-cli, you can run multiple commands quickly:
SET p1 "val1"
SET p2 "val2"
SET p3 "val3"
SET p4 "val4"
SET p5 "val5"
MGET p1 p2 p3 p4 p5

# 🎯 INTERVIEW: Pipeline vs MULTI/EXEC
# Pipeline: Batch sending for performance (NOT atomic)
# MULTI/EXEC: Atomic execution (NOT about performance)
# You CAN combine them: pipeline a MULTI/EXEC block

# 🎯 INTERVIEW: Pipeline Performance
# Without pipeline (100 commands):
#   100 × RTT ≈ 100 × 0.5ms = 50ms
# With pipeline:
#   1 × RTT + processing ≈ 0.5ms + 1ms = 1.5ms
# ~33x faster!

# ⚠️ Don't pipeline too many commands at once
# Memory for buffering responses can grow
# Typical batch size: 100-1000 commands

# Cleanup
DEL p1 p2 p3 p4 p5
```

---

## 17. Persistence — RDB & AOF

```redis
# =============================================
# SECTION 17: PERSISTENCE — RDB & AOF
# =============================================

# 🎯 INTERVIEW FAVORITE: "How does Redis persist data?"

# Two persistence mechanisms:
# 1. RDB (Redis Database) — point-in-time snapshots
# 2. AOF (Append Only File) — log every write operation

# =============================================
# RDB — Snapshots
# =============================================

# Check current RDB config
CONFIG GET save
# Default: "3600 1 300 100 60 10000"
# Meaning: save if 1 key changed in 3600s, 100 in 300s, 10000 in 60s

# Trigger manual save (BLOCKS Redis!)
# SAVE
# ⚠️ Never use in production — use BGSAVE instead

# Trigger background save (non-blocking)
BGSAVE
# Forks a child process to write RDB file

# Check last save time
LASTSAVE

# Check background save status
INFO persistence

# Where is the RDB file?
CONFIG GET dir
CONFIG GET dbfilename
# Default: dump.rdb in Redis working directory

# =============================================
# AOF — Append Only File
# =============================================

# Check AOF status
CONFIG GET appendonly
# Default: "no"

# Enable AOF (runtime, non-persistent config change)
# CONFIG SET appendonly yes

# AOF sync policy
CONFIG GET appendfsync
# Options:
# "always"  — fsync after every write (safest, slowest)
# "everysec" — fsync every second (good balance) ← DEFAULT
# "no"      — let OS decide when to fsync (fastest, least safe)

# AOF rewrite (compacts the AOF file)
# BGREWRITEAOF

# =============================================
# RDB + AOF (Redis 7.0+ default)
# =============================================

# Redis 7.0+ uses Multi Part AOF (MP-AOF)
# Combines RDB base + AOF incremental
# CONFIG SET aof-use-rdb-preamble yes

# =============================================
# 🎯 INTERVIEW: RDB vs AOF Comparison
# =============================================

# RDB Pros:
# - Compact single file (good for backups)
# - Fast recovery for large datasets
# - Minimal performance impact (fork + copy-on-write)
#
# RDB Cons:
# - Data loss between snapshots
# - Fork can be slow on large datasets

# AOF Pros:
# - More durable (at most 1 second of data loss with everysec)
# - Append-only (no corruption risk from partial writes)
# - Human-readable format
# - Can be used for point-in-time recovery
#
# AOF Cons:
# - Larger files than RDB
# - Slower recovery (must replay all commands)
# - Can be slower for writes (with always fsync)

# 🎯 INTERVIEW: Best Practice
# Use BOTH: AOF for durability + periodic RDB for backups
# Or use Redis 7.0+ Multi Part AOF (RDB base + AOF incremental)

# Check persistence info
INFO persistence
```

---

## 18. Memory Management & Eviction

```redis
# =============================================
# SECTION 18: MEMORY MANAGEMENT & EVICTION
# =============================================

# 🎯 INTERVIEW FAVORITE: "What happens when Redis runs out of memory?"

# Check memory usage
INFO memory

# Memory usage of specific key
SET mydata "hello world this is some data"
MEMORY USAGE mydata
# Returns bytes used by key + value

# Detailed memory report
MEMORY DOCTOR

# Memory stats
MEMORY STATS

# =============================================
# Max Memory Configuration
# =============================================

CONFIG GET maxmemory
# 0 = no limit (default for standalone, NOT recommended)

# Set max memory
# CONFIG SET maxmemory 100mb

CONFIG GET maxmemory-policy

# =============================================
# Eviction Policies
# =============================================

# noeviction — return error when memory limit reached (DEFAULT)
# allkeys-lru — evict least recently used key (MOST COMMON for cache)
# volatile-lru — evict LRU among keys with expiry
# allkeys-lfu — evict least frequently used key (Redis 4.0+)
# volatile-lfu — evict LFU among keys with expiry
# allkeys-random — evict random key
# volatile-random — evict random key with expiry
# volatile-ttl — evict key with shortest TTL

# 🎯 INTERVIEW: Which eviction policy to use?
# Cache use case → allkeys-lru or allkeys-lfu
# Mixed use (cache + persistent) → volatile-lru
# Session store → volatile-ttl
# Critical data → noeviction (handle errors in app)

# Set eviction policy
# CONFIG SET maxmemory-policy allkeys-lru

# =============================================
# Memory Optimization Tips
# =============================================

# 1. Use appropriate data structures
# Small hashes use ziplist encoding (very compact)
CONFIG GET hash-max-ziplist-entries
CONFIG GET hash-max-ziplist-value

# 2. Use short key names in production
# "user:1001:session" vs "u:1001:s"

# 3. Set TTL on cache keys
SET cache:result "data" EX 3600

# 4. Use UNLINK instead of DEL for large keys
# UNLINK is async (non-blocking)

# 5. Monitor big keys
# redis-cli --bigkeys

# 6. Use OBJECT ENCODING to check encoding
SET small_hash_val "tiny"
OBJECT ENCODING small_hash_val

HSET small_hash f1 "v1" f2 "v2"
OBJECT ENCODING small_hash
# Expected: "listpack" or "ziplist" (compact!)

# =============================================
# Lazy Free (Background Deletion)
# =============================================

CONFIG GET lazyfree-lazy-eviction
CONFIG GET lazyfree-lazy-expire
CONFIG GET lazyfree-lazy-server-del
CONFIG GET lazyfree-lazy-user-del

# Recommended: enable all for production
# CONFIG SET lazyfree-lazy-eviction yes
# CONFIG SET lazyfree-lazy-expire yes
# CONFIG SET lazyfree-lazy-server-del yes
# CONFIG SET lazyfree-lazy-user-del yes

# Cleanup
DEL mydata cache:result small_hash_val small_hash
```

---

## 19. Replication & High Availability

```redis
# =============================================
# SECTION 19: REPLICATION & HIGH AVAILABILITY
# =============================================

# 🎯 INTERVIEW: "How does Redis handle failover?"

# =============================================
# Replication Basics
# =============================================

# Check replication info
INFO replication

# To set up replication (on the REPLICA):
# REPLICAOF <master-host> <master-port>
# Example: REPLICAOF 127.0.0.1 6379

# To stop replication (promote to master):
# REPLICAOF NO ONE

# Check if current instance is master or replica
INFO replication
# role:master or role:slave

# =============================================
# Replication Architecture
# =============================================

# Master → Replica (async by default)
# 1. Replica sends REPLICAOF command
# 2. Master starts BGSAVE (creates RDB)
# 3. Master sends RDB to replica
# 4. Replica loads RDB
# 5. Master sends buffered writes to replica
# 6. Ongoing: master streams writes to replica

# =============================================
# WAIT — Synchronous Replication
# =============================================

# WAIT numreplicas timeout
# WAIT 1 5000
# Blocks until 1 replica has acknowledged, or 5 seconds

# =============================================
# Redis Sentinel — Automatic Failover
# =============================================

# Sentinel monitors master-replica sets
# Automatic failover if master goes down
# Provides service discovery (clients ask Sentinel for current master)

# Key Sentinel concepts:
# - Monitors: watches master/replicas
# - Notification: alerts admin
# - Failover: promotes replica to master
# - Configuration provider: clients query Sentinel for master address

# Sentinel commands (run on Sentinel instance):
# SENTINEL MASTERS
# SENTINEL MASTER mymaster
# SENTINEL REPLICAS mymaster
# SENTINEL SENTINELS mymaster
# SENTINEL GET-MASTER-ADDR-BY-NAME mymaster
# SENTINEL FAILOVER mymaster

# =============================================
# 🎯 INTERVIEW: Replication Trade-offs
# =============================================

# Async replication:
# + Low latency writes
# - Possible data loss on failover (master writes not yet replicated)

# WAIT for sync replication:
# + Stronger consistency
# - Higher latency

# 🎯 INTERVIEW: "Is Redis CP or AP?"
# Default: AP (available + partition tolerant, eventual consistency)
# With WAIT: Can be CP (but at cost of availability)
# With Sentinel: AP with automatic failover
```

---

## 20. Redis Cluster

```redis
# =============================================
# SECTION 20: REDIS CLUSTER
# =============================================

# 🎯 INTERVIEW: "How does Redis scale horizontally?"

# Redis Cluster: automatic sharding across multiple nodes
# 16,384 hash slots distributed across masters
# Each key → CRC16(key) mod 16384 → assigned to a slot → mapped to a node

# =============================================
# Key Concepts
# =============================================

# Hash Slots: 16,384 total, divided among masters
# Example: 3 masters
# Master A: slots 0-5460
# Master B: slots 5461-10922
# Master C: slots 10923-16383

# Check which slot a key maps to:
CLUSTER KEYSLOT "mykey"
CLUSTER KEYSLOT "user:1001"
CLUSTER KEYSLOT "user:1002"

# Hash Tags: Force keys to same slot using {tag}
CLUSTER KEYSLOT "{user:1001}.profile"
CLUSTER KEYSLOT "{user:1001}.settings"
CLUSTER KEYSLOT "{user:1001}.cart"
# All three go to same slot because of {user:1001} tag!

# Without hash tags:
CLUSTER KEYSLOT "user:1001:profile"
CLUSTER KEYSLOT "user:1001:settings"
# Different slots! Can't do multi-key operations across slots!

# =============================================
# Cluster Info Commands (work on cluster nodes)
# =============================================

# CLUSTER INFO — cluster status
# CLUSTER NODES — list all nodes
# CLUSTER SLOTS — slot-to-node mapping
# CLUSTER MYID — current node ID

# =============================================
# MOVED and ASK Redirections
# =============================================

# If you send a command to wrong node:
# -MOVED 3999 127.0.0.1:6381
# Permanent redirect: key belongs to another node

# During resharding:
# -ASK 3999 127.0.0.1:6381
# Temporary redirect: slot is being migrated

# =============================================
# 🎯 INTERVIEW: Cluster Limitations
# =============================================

# 1. Multi-key operations require all keys in same slot
#    → Use hash tags: {user:1001}.profile, {user:1001}.cart
#
# 2. Lua scripts: all keys must be in same slot
#
# 3. MULTI/EXEC: all keys must be in same slot
#
# 4. No SELECT (only database 0)
#
# 5. Pub/Sub: messages are broadcast to all nodes (bandwidth cost)
#
# 6. Slightly higher latency than standalone

# =============================================
# 🎯 INTERVIEW: Cluster vs Sentinel
# =============================================

# Sentinel:
# - Single master, multiple replicas
# - Automatic failover only
# - No sharding
# - Good for: HA with dataset fits in single node memory

# Cluster:
# - Multiple masters, each with replicas
# - Automatic sharding + automatic failover
# - Horizontal scaling
# - Good for: datasets too large for single node

# =============================================
# 🎯 INTERVIEW: "How would you design Redis for 100GB dataset?"
# =============================================

# Option 1: Redis Cluster
# 10 masters × 10GB each + replicas for HA
# Automatic resharding to add nodes

# Option 2: Client-side sharding
# Application decides which Redis instance to use
# Consistent hashing to minimize rebalancing
# Manual failover handling

# Option 3: Proxy-based sharding
# Twemproxy, Redis Cluster Proxy
# Proxy routes commands to correct shard
```

---

## 21. Interview Pattern: Rate Limiter

```redis
# =============================================
# SECTION 21: 🎯 INTERVIEW — RATE LIMITER
# =============================================

# Scenario: Limit API to 10 requests per minute per user

# =============================================
# Approach 1: Fixed Window Counter
# =============================================

# Key: ratelimit:{user_id}:{window}
# Window = current_minute

# Simulate: user 1001, current minute = 202401151030
SET ratelimit:1001:202401151030 "0" EX 60 NX
INCR ratelimit:1001:202401151030
INCR ratelimit:1001:202401151030
INCR ratelimit:1001:202401151030
GET ratelimit:1001:202401151030
# Check if > 10, if so → rate limited

# Pros: Simple, O(1)
# Cons: Boundary problem (burst at window edges)
# Example: 10 requests at 10:30:59, 10 more at 10:31:01 = 20 in 2 seconds!

# =============================================
# Approach 2: Sliding Window Log
# =============================================

# Use sorted set: score = timestamp, member = unique request ID

# Request at timestamp 1705363200:
ZADD sliding:1001 1705363200 "req:1705363200:1"
ZADD sliding:1001 1705363205 "req:1705363205:2"
ZADD sliding:1001 1705363210 "req:1705363210:3"
ZADD sliding:1001 1705363215 "req:1705363215:4"

# Check rate limit (window = 60 seconds):
# Remove entries older than (now - 60):
ZREMRANGEBYSCORE sliding:1001 0 1705363140

# Count entries in window:
ZCARD sliding:1001

# If count >= 10 → rate limited!
# Expire key for cleanup:
EXPIRE sliding:1001 60

# Pros: Exact sliding window, no boundary issues
# Cons: Memory = O(N) where N = number of requests

# =============================================
# Approach 3: Sliding Window Counter (Hybrid)
# =============================================

# Weighted average of current and previous window
# Estimated count = prev_count × overlap_ratio + curr_count

# If window = 60s, current position = 15s into window:
# overlap_ratio = (60 - 15) / 60 = 0.75
# estimated = prev_count × 0.75 + curr_count

SET ratelimit:1001:prev "8"
SET ratelimit:1001:curr "3"
# If 15s into current window:
# estimated = 8 × 0.75 + 3 = 9 → allowed (< 10)

# Pros: Memory efficient (just 2 counters), close to accurate
# Cons: Approximate

# =============================================
# Approach 4: Token Bucket (Lua Script)
# =============================================

EVAL "\
local tokens_key = KEYS[1]\
local ts_key = KEYS[2]\
local rate = tonumber(ARGV[1])\
local capacity = tonumber(ARGV[2])\
local now = tonumber(ARGV[3])\
local requested = tonumber(ARGV[4])\
\
local last_tokens = tonumber(redis.call('GET', tokens_key) or capacity)\
local last_refreshed = tonumber(redis.call('GET', ts_key) or now)\
\
local delta = math.max(0, now - last_refreshed)\
local filled_tokens = math.min(capacity, last_tokens + (delta * rate))\
\
local allowed = filled_tokens >= requested\
local new_tokens = filled_tokens\
if allowed then\
    new_tokens = filled_tokens - requested\
end\
\
redis.call('SETEX', tokens_key, capacity / rate * 2, new_tokens)\
redis.call('SETEX', ts_key, capacity / rate * 2, now)\
\
return allowed and 1 or 0" 2 "bucket:tokens:1001" "bucket:ts:1001" "1" "10" "1705363200" "1"
# ARGV: rate=1 token/sec, capacity=10, now=timestamp, requested=1
# Returns 1=allowed, 0=denied

# Cleanup
DEL ratelimit:1001:202401151030 sliding:1001 ratelimit:1001:prev ratelimit:1001:curr bucket:tokens:1001 bucket:ts:1001
```

---

## 22. Interview Pattern: Distributed Lock (Redlock)

```redis
# =============================================
# SECTION 22: 🎯 INTERVIEW — DISTRIBUTED LOCK
# =============================================

# Scenario: Ensure only one process handles a critical section

# =============================================
# Simple Lock
# =============================================

# Acquire lock (NX = only if not exists, EX = auto-expire)
SET lock:resource:1001 "worker:abc123" NX EX 30
# Returns OK if acquired, nil if already held

# Check who holds the lock
GET lock:resource:1001
# Expected: "worker:abc123"

# Try to acquire again (fails)
SET lock:resource:1001 "worker:xyz789" NX EX 30
# Expected: (nil) — already locked!

# Release lock (ONLY if we hold it — Lua script!)
EVAL "\
if redis.call('GET', KEYS[1]) == ARGV[1] then\
    return redis.call('DEL', KEYS[1])\
else\
    return 0\
end" 1 lock:resource:1001 "worker:abc123"
# Returns 1 if we released it, 0 if someone else holds it

# ⚠️ Why Lua? Because GET + DEL must be atomic!
# Without Lua: GET returns our ID, but between GET and DEL,
# the lock could expire and be acquired by someone else!

# =============================================
# Lock with Renewal (Watchdog Pattern)
# =============================================

# Acquire with 30s TTL
SET lock:resource:2001 "worker:abc" NX EX 30

# Worker periodically extends TTL while processing:
EVAL "\
if redis.call('GET', KEYS[1]) == ARGV[1] then\
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))\
    return 1\
else\
    return 0\
end" 1 lock:resource:2001 "worker:abc" "30"
# Only extends if we still hold it

# =============================================
# 🎯 INTERVIEW: Redlock Algorithm
# =============================================

# Problem: Single Redis instance = single point of failure
# Solution: Acquire lock on MAJORITY of independent Redis instances

# Steps:
# 1. Get current time T1
# 2. Try to acquire lock on N instances (e.g., 5)
#    SET lock:resource "client_id" NX PX 30000
# 3. If acquired on majority (>= 3 of 5):
#    - Valid time = TTL - (T2 - T1)  [time elapsed]
#    - If valid time > 0 → lock acquired!
# 4. If not majority → release all acquired locks

# On each of 5 instances:
SET lock:payment:1001 "client:xyz:1705363200" NX PX 30000
# Do this on all 5, count successes
# Need >= 3 successes + valid remaining TTL

# 🎯 INTERVIEW: Redlock Controversy
# Martin Kleppmann's critique:
# - GC pauses can cause expired lock to be used
# - Clock drift between nodes
# - Network delays
# Solution: Use fencing tokens (monotonic counter)
# Each lock acquisition gets increasing token number
# Storage layer rejects writes with old tokens

# =============================================
# Lock with Fencing Token
# =============================================

# Acquire lock + get fencing token
EVAL "\
if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then\
    return redis.call('INCR', KEYS[2])\
else\
    return nil\
end" 2 lock:resource:3001 lock:fence:counter "worker:abc" "30"
# Returns fencing token number if lock acquired

# Use fencing token in storage operations:
# INSERT INTO orders WHERE fencing_token > last_token

# Cleanup
DEL lock:resource:1001 lock:resource:2001 lock:payment:1001 lock:resource:3001 lock:fence:counter
```

---

## 23. Interview Pattern: Session Store

```redis
# =============================================
# SECTION 23: 🎯 INTERVIEW — SESSION STORE
# =============================================

# Scenario: Web application session management with Redis

# =============================================
# Create Session
# =============================================

# Session ID generated by app (e.g., UUID)
HSET session:abc-def-123 user_id "1001" username "alice" role "admin" ip "192.168.1.1" user_agent "Mozilla/5.0" login_time "1705363200" csrf_token "xyz789"

# Set session expiry (30 minutes)
EXPIRE session:abc-def-123 1800

# =============================================
# Read Session
# =============================================

# Get all session data
HGETALL session:abc-def-123

# Get specific fields
HMGET session:abc-def-123 user_id role

# Check if session exists
EXISTS session:abc-def-123

# =============================================
# Update Session (sliding expiry)
# =============================================

# Every request: update last_access and reset TTL
HSET session:abc-def-123 last_access "1705364000"
EXPIRE session:abc-def-123 1800

# Or atomically with Lua:
EVAL "\
if redis.call('EXISTS', KEYS[1]) == 1 then\
    redis.call('HSET', KEYS[1], 'last_access', ARGV[1])\
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))\
    return redis.call('HGETALL', KEYS[1])\
else\
    return nil\
end" 1 session:abc-def-123 "1705364100" "1800"

# =============================================
# Destroy Session (Logout)
# =============================================

DEL session:abc-def-123

# =============================================
# Session Indexing — Find all sessions for a user
# =============================================

# Maintain a set of session IDs per user
SADD user:1001:sessions "session:abc-def-123"
SADD user:1001:sessions "session:ghi-jkl-456"

# Logout from all devices:
SMEMBERS user:1001:sessions
# For each: DEL session:...
# Then: DEL user:1001:sessions

# =============================================
# 🎯 INTERVIEW: Session Store Trade-offs
# =============================================

# Why Redis for sessions?
# - Fast (sub-millisecond reads)
# - Automatic expiry via TTL
# - Shared across multiple app servers
# - Survives app server restarts

# Alternative: JWT tokens
# - Stateless (no Redis needed)
# - Can't invalidate easily
# - Larger payload
# - Redis session = easy invalidation + smaller cookies

# Cleanup
DEL session:abc-def-123 session:ghi-jkl-456 user:1001:sessions
```

---

## 24. Interview Pattern: Leaderboard System

```redis
# =============================================
# SECTION 24: 🎯 INTERVIEW — LEADERBOARD SYSTEM
# =============================================

# Scenario: Real-time gaming leaderboard with millions of players

# =============================================
# Setup: Add Players with Scores
# =============================================

ZADD leaderboard 2500 "player:ninja42"
ZADD leaderboard 1800 "player:dragon99"
ZADD leaderboard 3200 "player:speedster"
ZADD leaderboard 2900 "player:shadow_x"
ZADD leaderboard 1500 "player:noob123"
ZADD leaderboard 4100 "player:pro_gamer"
ZADD leaderboard 3800 "player:elite_one"
ZADD leaderboard 2100 "player:casual_bob"
ZADD leaderboard 3500 "player:tryhard_99"
ZADD leaderboard 2700 "player:lucky_shot"

# =============================================
# Core Operations
# =============================================

# Top 5 Players (highest scores first)
ZRANGE leaderboard 0 4 REV WITHSCORES
# Expected: pro_gamer(4100), elite_one(3800), tryhard_99(3500), ...

# Top 10
ZRANGE leaderboard 0 9 REV WITHSCORES

# Get player's rank (0-based, highest = 0)
ZREVRANK leaderboard "player:ninja42"
# Expected: rank among all players

# Get player's score
ZSCORE leaderboard "player:ninja42"
# Expected: "2500"

# Update score (player wins a game)
ZINCRBY leaderboard 150 "player:noob123"
ZSCORE leaderboard "player:noob123"
# Expected: "1650"

# Get players ranked 5th to 10th
ZRANGE leaderboard 4 9 REV WITHSCORES

# Get players with scores between 2000 and 3000
ZRANGE leaderboard 2000 3000 BYSCORE WITHSCORES

# =============================================
# Player Neighborhood (players around you)
# =============================================

# Get player's rank
ZREVRANK leaderboard "player:ninja42"
# Say rank is 5

# Get players ranked 3-7 (2 above and 2 below)
ZRANGE leaderboard 3 7 REV WITHSCORES

# =============================================
# Paginated Leaderboard
# =============================================

# Page 1 (ranks 0-9)
ZRANGE leaderboard 0 9 REV WITHSCORES
# Page 2 (ranks 10-19)
ZRANGE leaderboard 10 19 REV WITHSCORES

# =============================================
# Daily/Weekly/Monthly Leaderboards
# =============================================

# Daily leaderboard (reset daily)
ZADD leaderboard:daily:2024-01-15 100 "player:ninja42"
ZADD leaderboard:daily:2024-01-15 200 "player:dragon99"
EXPIRE leaderboard:daily:2024-01-15 172800

# Weekly leaderboard (merge daily boards)
ZUNIONSTORE leaderboard:weekly:2024-w03 7 leaderboard:daily:2024-01-15 leaderboard:daily:2024-01-16 leaderboard:daily:2024-01-17 leaderboard:daily:2024-01-18 leaderboard:daily:2024-01-19 leaderboard:daily:2024-01-20 leaderboard:daily:2024-01-21

# =============================================
# Multi-Dimension Leaderboard (composite score)
# =============================================

# Score = wins × 10000 + (10000 - losses)
# This way: more wins → higher score, ties broken by fewer losses
# Player A: 100 wins, 20 losses → 100*10000 + (10000-20) = 1009980
# Player B: 100 wins, 30 losses → 100*10000 + (10000-30) = 1009970
ZADD composite_board 1009980 "player_a"
ZADD composite_board 1009970 "player_b"
ZRANGE composite_board 0 -1 REV WITHSCORES
# player_a ranks higher (fewer losses)

# =============================================
# 🎯 INTERVIEW: Leaderboard at Scale
# =============================================

# Challenge: 100M players, need top-K and arbitrary rank queries
# Sorted Set in Redis: O(log N) for all operations
# 100M members: ~8-10GB memory (reasonable for single node)
# If too big: shard by region/tier

# Hot path optimization:
# Cache top-100 separately with short TTL
SET leaderboard:top100:cache "[...]" EX 5

# Cleanup
DEL leaderboard leaderboard:daily:2024-01-15 leaderboard:daily:2024-01-16 leaderboard:daily:2024-01-17 leaderboard:daily:2024-01-18 leaderboard:daily:2024-01-19 leaderboard:daily:2024-01-20 leaderboard:daily:2024-01-21 leaderboard:weekly:2024-w03 composite_board leaderboard:top100:cache
```

---

## 25. Interview Pattern: Feed / Timeline

```redis
# =============================================
# SECTION 25: 🎯 INTERVIEW — FEED / TIMELINE
# =============================================

# Scenario: Twitter-like social media feed

# =============================================
# Fan-Out on Write (Push Model)
# =============================================

# User alice (user:1) posts a tweet
# Alice's followers: bob (user:2), charlie (user:3)

# Store the post
HSET post:1001 author "user:1" content "Hello from Alice!" timestamp "1705363200" likes "0"

# Fan-out: push post ID to each follower's timeline
LPUSH timeline:user:2 "post:1001"
LPUSH timeline:user:3 "post:1001"

# Also add to alice's own timeline
LPUSH timeline:user:1 "post:1001"

# Keep timelines bounded (last 1000 posts)
LTRIM timeline:user:2 0 999
LTRIM timeline:user:3 0 999

# Another post from bob (user:2)
HSET post:1002 author "user:2" content "Bob here!" timestamp "1705363300" likes "0"
# Bob's followers: alice (user:1)
LPUSH timeline:user:1 "post:1002"

# =============================================
# Read Timeline
# =============================================

# Get latest 10 posts from bob's timeline
LRANGE timeline:user:2 0 9
# Returns: post:1001 (and others)

# For each post ID, get full post data:
HGETALL post:1001

# Pagination
LRANGE timeline:user:2 0 19    # Page 1
LRANGE timeline:user:2 20 39   # Page 2

# =============================================
# Fan-Out on Read (Pull Model) — Alternative
# =============================================

# Instead of pushing to timelines, maintain following lists
SADD user:2:following "user:1" "user:3"

# When bob loads feed:
# 1. Get all users bob follows
SMEMBERS user:2:following
# 2. For each followed user, get their recent posts
LRANGE posts:user:1 0 9
LRANGE posts:user:3 0 9
# 3. Merge and sort by timestamp in application

# =============================================
# Hybrid Approach (Twitter-style)
# =============================================

# For normal users (< 5000 followers): Fan-out on write
# For celebrities (millions of followers): Fan-out on read
# Timeline = merge(pre-computed_timeline, celebrity_posts)

# Store follower count
HSET user:1:meta followers "5200000"  # celebrity!
HGET user:1:meta followers
# If > threshold → skip fan-out, use pull model

# =============================================
# Like/Unlike a Post
# =============================================

# Like
SADD post:1001:likes "user:2"
HINCRBY post:1001 likes 1

# Unlike
SREM post:1001:likes "user:2"
HINCRBY post:1001 likes -1

# Check if user liked
SISMEMBER post:1001:likes "user:2"

# Get like count
HGET post:1001 likes

# Cleanup
DEL post:1001 post:1002 timeline:user:1 timeline:user:2 timeline:user:3 user:2:following posts:user:1 posts:user:3 user:1:meta post:1001:likes
```

---

## 26. Interview Pattern: Unique Visitors / Count Distinct

```redis
# =============================================
# SECTION 26: 🎯 INTERVIEW — UNIQUE VISITORS / COUNT DISTINCT
# =============================================

# Scenario: Count unique visitors to a website

# =============================================
# Approach 1: SET (Exact, High Memory)
# =============================================

SADD visitors:2024-01-15 "user:1001" "user:1002" "user:1003" "user:1001"
SCARD visitors:2024-01-15
# Expected: 3 (exact count)

# Memory: ~40 bytes per entry × 10M users = ~400MB per day! 😰

# =============================================
# Approach 2: HyperLogLog (Approximate, ~12KB!)
# =============================================

PFADD uv:2024-01-15 "user:1001" "user:1002" "user:1003" "user:1001"
PFCOUNT uv:2024-01-15
# Expected: 3 (approximately)

# Weekly unique visitors (merge daily HLLs)
PFADD uv:2024-01-15 "user:1001" "user:1002"
PFADD uv:2024-01-16 "user:1002" "user:1003"
PFADD uv:2024-01-17 "user:1003" "user:1004"

PFMERGE uv:week uv:2024-01-15 uv:2024-01-16 uv:2024-01-17
PFCOUNT uv:week
# Expected: ~4

# Memory: ~12KB per HLL, regardless of cardinality!
# 365 days × 12KB = ~4.3MB for entire year! 🎉

# =============================================
# Approach 3: Bitmap (Exact, Compact for Dense Data)
# =============================================

# If user IDs are numeric and dense (0 to N):
SETBIT dau:2024-01-15 1001 1
SETBIT dau:2024-01-15 1002 1
SETBIT dau:2024-01-15 1003 1

BITCOUNT dau:2024-01-15
# Expected: 3 (exact)

# Memory: 10M users = ~1.2MB per day

# Weekly active users (visited any day)
BITOP OR wau:week dau:2024-01-15 dau:2024-01-16 dau:2024-01-17
BITCOUNT wau:week

# =============================================
# 🎯 INTERVIEW: Comparison Table
# =============================================

# | Approach    | Memory (10M users) | Accuracy | Speed |
# |-------------|-------------------|----------|-------|
# | SET         | ~400MB            | Exact    | O(1)  |
# | HyperLogLog | ~12KB             | ~0.81%   | O(1)  |
# | Bitmap      | ~1.2MB            | Exact    | O(1)  |

# When to use what:
# - Small cardinality → SET
# - Large cardinality, exact count → Bitmap (if IDs are numeric)
# - Large cardinality, approximation OK → HyperLogLog

# Cleanup
DEL visitors:2024-01-15 uv:2024-01-15 uv:2024-01-16 uv:2024-01-17 uv:week dau:2024-01-15 dau:2024-01-16 dau:2024-01-17 wau:week
```

---

## 27. Interview Pattern: Autocomplete / Typeahead

```redis
# =============================================
# SECTION 27: 🎯 INTERVIEW — AUTOCOMPLETE / TYPEAHEAD
# =============================================

# Scenario: Search suggestions as user types

# =============================================
# Approach 1: Sorted Set with Prefix Search
# =============================================

# Store search terms with frequency as score
ZADD autocomplete 1000 "redis"
ZADD autocomplete 800 "react"
ZADD autocomplete 500 "react native"
ZADD autocomplete 300 "redis cluster"
ZADD autocomplete 200 "redis sentinel"
ZADD autocomplete 100 "rest api"
ZADD autocomplete 900 "responsive design"
ZADD autocomplete 600 "redux"
ZADD autocomplete 400 "redis streams"
ZADD autocomplete 150 "redis pub sub"

# User types "red" — find all completions
# Use ZRANGEBYLEX with same-score trick:
ZADD ac:terms 0 "redis"
ZADD ac:terms 0 "react"
ZADD ac:terms 0 "react native"
ZADD ac:terms 0 "redis cluster"
ZADD ac:terms 0 "redis sentinel"
ZADD ac:terms 0 "rest api"
ZADD ac:terms 0 "responsive design"
ZADD ac:terms 0 "redux"
ZADD ac:terms 0 "redis streams"
ZADD ac:terms 0 "redis pub sub"

# Search for "red"
ZRANGEBYLEX ac:terms "[red" "[red\xff" LIMIT 0 5
# Expected: redis, redis cluster, redis pub sub, redis sentinel, redis streams, redux

# Search for "reac"
ZRANGEBYLEX ac:terms "[reac" "[reac\xff" LIMIT 0 5
# Expected: react, react native

# Search for "redis s"
ZRANGEBYLEX ac:terms "[redis s" "[redis s\xff" LIMIT 0 5
# Expected: redis sentinel, redis streams

# =============================================
# Approach 2: Sorted Set with Weighted Suggestions
# =============================================

# Store per-prefix sorted sets with popularity scores
ZADD ac:r 1000 "redis"
ZADD ac:r 900 "responsive design"
ZADD ac:r 800 "react"
ZADD ac:r 600 "redux"

ZADD ac:re 1000 "redis"
ZADD ac:re 900 "responsive design"
ZADD ac:re 800 "react"
ZADD ac:re 600 "redux"

ZADD ac:red 1000 "redis"
ZADD ac:red 600 "redux"
ZADD ac:red 300 "redis cluster"

# User types "red" → get top 5 by score
ZRANGE ac:red 0 4 REV WITHSCORES
# Returns suggestions sorted by popularity!

# Increment popularity when user selects a suggestion
ZINCRBY ac:red 1 "redis cluster"

# =============================================
# Approach 3: Trie-like Structure with Hashes
# =============================================

# Store completions per prefix
SADD prefix:r "redis" "react" "redux" "rest"
SADD prefix:re "redis" "react" "redux" "rest"
SADD prefix:red "redis" "redux"
SADD prefix:redi "redis"
SADD prefix:redis "redis"

# Lookup
SRANDMEMBER prefix:red 5
# Gets up to 5 random suggestions for "red"

# Or with scoring:
HSET prefix:red:scores "redis" "1000" "redux" "600"

# =============================================
# 🎯 INTERVIEW: Scaling Autocomplete
# =============================================

# Challenge: Millions of search terms, low latency
# 1. Pre-compute top-K per prefix (ZADD with scores)
# 2. Limit prefix depth (2-3 characters minimum)
# 3. TTL on prefix keys (rebuild periodically from analytics)
# 4. Shard by first character or hash

# Cleanup
DEL autocomplete ac:terms ac:r ac:re ac:red ac:red prefix:r prefix:re prefix:red prefix:redi prefix:redis prefix:red:scores
```

---

## 28. Interview Pattern: Job Queue

```redis
# =============================================
# SECTION 28: 🎯 INTERVIEW — JOB QUEUE
# =============================================

# Scenario: Distributed job processing with reliability

# =============================================
# Simple Queue (RPUSH + BLPOP)
# =============================================

# Producer: add jobs
RPUSH job_queue "{\"id\":1,\"type\":\"email\",\"to\":\"alice@example.com\"}"
RPUSH job_queue "{\"id\":2,\"type\":\"resize\",\"image\":\"photo.jpg\"}"
RPUSH job_queue "{\"id\":3,\"type\":\"report\",\"format\":\"pdf\"}"

# Consumer: process jobs (blocking)
# BLPOP job_queue 0
# Blocks until job available (0 = wait forever)

LPOP job_queue
# Non-blocking: returns nil if empty

# =============================================
# Reliable Queue (with processing list)
# =============================================

# Producer
RPUSH reliable_queue "job:1001"
RPUSH reliable_queue "job:1002"
RPUSH reliable_queue "job:1003"

# Consumer: atomically move from queue to processing
LMOVE reliable_queue processing LEFT RIGHT

# If worker succeeds:
LREM processing 1 "job:1001"

# If worker crashes: check processing list for stale jobs
LRANGE processing 0 -1
# Move stale jobs back to queue

# =============================================
# Priority Queue
# =============================================

# Use sorted set with priority as score (lower = higher priority)
ZADD priority_queue 1 "critical:fix_production_bug"
ZADD priority_queue 5 "low:update_docs"
ZADD priority_queue 3 "medium:add_feature"
ZADD priority_queue 1 "critical:data_corruption"
ZADD priority_queue 2 "high:security_patch"

# Process highest priority (lowest score)
ZPOPMIN priority_queue
# Expected: one of the critical tasks

ZPOPMIN priority_queue
ZPOPMIN priority_queue

# Blocking version:
# BZPOPMIN priority_queue 30

# =============================================
# Delayed Queue
# =============================================

# Use sorted set with execution timestamp as score
ZADD delayed_queue 1705363800 "job:send_reminder"     # run at specific time
ZADD delayed_queue 1705364400 "job:generate_report"    # run later
ZADD delayed_queue 1705363200 "job:expire_coupon"      # run now (past time)

# Worker polls for ready jobs:
# Get jobs with score <= current_time
ZRANGEBYSCORE delayed_queue 0 1705363500
# Returns jobs ready to execute

# Process and remove:
# ZPOPMIN delayed_queue  (or use Lua to atomically check and pop)

EVAL "\
local jobs = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, 1)\
if #jobs > 0 then\
    redis.call('ZREM', KEYS[1], jobs[1])\
    return jobs[1]\
else\
    return nil\
end" 1 delayed_queue "1705363500"

# =============================================
# Dead Letter Queue
# =============================================

# If a job fails too many times, move to DLQ
HSET job:1001:meta attempts "0"

# On failure:
HINCRBY job:1001:meta attempts 1
HGET job:1001:meta attempts
# If attempts > 3:
RPUSH dead_letter_queue "job:1001"
# Remove from retry queue

# =============================================
# Using Streams (Best for Production)
# =============================================

# Producer
XADD jobs * type "email" to "alice@example.com" priority "high"
XADD jobs * type "resize" image "photo.jpg" priority "low"

# Create consumer group
XGROUP CREATE jobs workers 0

# Consumer 1 reads
XREADGROUP GROUP workers worker1 COUNT 1 STREAMS jobs >

# Consumer 2 reads (gets different message!)
XREADGROUP GROUP workers worker2 COUNT 1 STREAMS jobs >

# Acknowledge after processing
# XACK jobs workers <id>

# Check pending (failed/slow jobs)
XPENDING jobs workers - + 10

# Cleanup
DEL job_queue reliable_queue processing priority_queue delayed_queue dead_letter_queue job:1001:meta jobs
```

---

## 29. Interview Pattern: Sliding Window Counter

```redis
# =============================================
# SECTION 29: 🎯 INTERVIEW — SLIDING WINDOW COUNTER
# =============================================

# Scenario: Count events in a rolling time window
# Example: "How many requests in the last 60 seconds?"

# =============================================
# Approach 1: Sorted Set Based (Most Common)
# =============================================

# Each request: add with timestamp as score
# Unique member to avoid deduplication
ZADD window:api:user1001 1705363200.001 "1705363200.001:req1"
ZADD window:api:user1001 1705363210.500 "1705363210.500:req2"
ZADD window:api:user1001 1705363220.123 "1705363220.123:req3"
ZADD window:api:user1001 1705363230.789 "1705363230.789:req4"
ZADD window:api:user1001 1705363250.456 "1705363250.456:req5"

# Count events in last 60 seconds (assume now = 1705363260)
# 1. Remove old entries
ZREMRANGEBYSCORE window:api:user1001 0 1705363200
# 2. Count remaining
ZCARD window:api:user1001
# 3. Set TTL for cleanup
EXPIRE window:api:user1001 60

# Atomic version with Lua:
EVAL "\
local key = KEYS[1]\
local now = tonumber(ARGV[1])\
local window = tonumber(ARGV[2])\
local member = ARGV[3]\
\
redis.call('ZADD', key, now, member)\
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\
redis.call('EXPIRE', key, math.ceil(window))\
local count = redis.call('ZCARD', key)\
return count" 1 "sw:user:1001" "1705363260" "60" "1705363260:req6"
# Returns current count in window

# =============================================
# Approach 2: Multiple Fixed Windows (Approximate)
# =============================================

# Divide time into small buckets (e.g., 1-second buckets)
# Key: counter:{user}:{second_timestamp}

INCR counter:user1001:1705363200
INCR counter:user1001:1705363201
INCR counter:user1001:1705363201
INCR counter:user1001:1705363205
EXPIRE counter:user1001:1705363200 120
EXPIRE counter:user1001:1705363201 120
EXPIRE counter:user1001:1705363205 120

# To count last 60 seconds: sum all bucket counters
# Application generates the 60 keys and uses MGET
# MGET counter:user1001:1705363200 counter:user1001:1705363201 ... counter:user1001:1705363259

# =============================================
# 🎯 INTERVIEW: Which Approach?
# =============================================

# Sorted Set:
# + Exact sliding window
# + Single key per entity
# - Memory grows with request count
# - Need periodic cleanup

# Multiple Windows:
# + Constant memory per bucket
# + Fast INCR/GET
# - Approximate (can miss boundary)
# - Many keys (but auto-expire)
# - Need MGET for count (60 keys for 60s window)

# Cleanup
DEL window:api:user1001 sw:user:1001 counter:user1001:1705363200 counter:user1001:1705363201 counter:user1001:1705363205
```

---

## 30. Interview Pattern: Pub/Sub Chat System

```redis
# =============================================
# SECTION 30: 🎯 INTERVIEW — PUB/SUB CHAT SYSTEM
# =============================================

# Scenario: Real-time group chat using Redis

# =============================================
# Chat Room Setup
# =============================================

# Room metadata
HSET room:general name "General Chat" created_by "admin" created_at "1705363200" type "public"
HSET room:engineering name "Engineering" created_by "alice" created_at "1705363200" type "private"

# Room members
SADD room:general:members "alice" "bob" "charlie" "diana"
SADD room:engineering:members "alice" "bob"

# User's rooms
SADD user:alice:rooms "room:general" "room:engineering"
SADD user:bob:rooms "room:general" "room:engineering"

# =============================================
# Send Message (using Streams for persistence)
# =============================================

# Store message in stream
XADD chat:room:general * sender "alice" message "Hey everyone!" type "text"
XADD chat:room:general * sender "bob" message "Hi Alice!" type "text"
XADD chat:room:general * sender "charlie" message "What's up?" type "text"

# Real-time delivery via Pub/Sub
# PUBLISH chat:room:general "{\"sender\":\"alice\",\"message\":\"Hey everyone!\"}"

# =============================================
# Read Chat History
# =============================================

# Latest messages
XREVRANGE chat:room:general + - COUNT 20

# Older messages (pagination using message ID)
# XREVRANGE chat:room:general <last_id> - COUNT 20

# Messages after a specific time
XRANGE chat:room:general 1705363200000-0 +

# =============================================
# Unread Count
# =============================================

# Track last read position per user per room
HSET user:alice:last_read "room:general" "1705363200000-0"
HSET user:bob:last_read "room:general" "1705363100000-0"

# Count unread for bob in general:
# XLEN chat:room:general after bob's last read position
EVAL "\
local last_read = redis.call('HGET', KEYS[2], ARGV[1])\
if not last_read then last_read = '0-0' end\
local messages = redis.call('XRANGE', KEYS[1], '(' .. last_read, '+')\
return #messages" 2 chat:room:general user:bob:last_read "room:general"
# Returns unread count

# Mark as read
HSET user:bob:last_read "room:general" "1705363300000-0"

# =============================================
# Typing Indicator
# =============================================

# User starts typing
SET typing:room:general:alice "1" EX 3
# Auto-expires in 3 seconds (if user stops typing, indicator goes away)

# Check who's typing
KEYS typing:room:general:*
# ⚠️ Use SCAN in production, not KEYS!

# Or better: use Pub/Sub
# PUBLISH typing:room:general "alice:start"
# PUBLISH typing:room:general "alice:stop"

# =============================================
# Online Presence (for chat)
# =============================================

SET presence:alice "online" EX 60
SET presence:bob "online" EX 60

# Heartbeat: re-SET with EX every 30 seconds
# If user disconnects → key expires → offline

# Check presence
GET presence:alice
# "online" or nil

# Get all online members of a room
SMEMBERS room:general:members
# For each: GET presence:{member}

# Cleanup
DEL room:general room:engineering room:general:members room:engineering:members user:alice:rooms user:bob:rooms chat:room:general user:alice:last_read user:bob:last_read typing:room:general:alice presence:alice presence:bob
```

---

## 31. Interview Pattern: Cache-Aside (Lazy Loading)

```redis
# =============================================
# SECTION 31: 🎯 INTERVIEW — CACHE-ASIDE (LAZY LOADING)
# =============================================

# Most common caching pattern!
# App checks cache first, falls back to DB, then populates cache

# =============================================
# The Pattern
# =============================================

# Step 1: Try to get from cache
GET cache:user:1001
# Returns nil (cache miss)

# Step 2: Read from database (simulate)
# SELECT * FROM users WHERE id = 1001
# Returns: {name: "Alice", email: "alice@example.com"}

# Step 3: Write to cache with TTL
SET cache:user:1001 "{\"name\":\"Alice\",\"email\":\"alice@example.com\"}" EX 3600

# Next request: cache hit!
GET cache:user:1001
# Returns the cached JSON

# =============================================
# Cache Invalidation
# =============================================

# On update: delete cache key
DEL cache:user:1001
# Next read will be a cache miss → reads fresh data from DB

# Or: Update cache and DB together
SET cache:user:1001 "{\"name\":\"Alice Updated\",\"email\":\"alice@new.com\"}" EX 3600

# =============================================
# Stampede Prevention
# =============================================

# Problem: Popular key expires → 1000 concurrent requests all hit DB!

# Solution 1: Lock-based rebuild
# Only one request rebuilds cache, others wait

# Try to acquire rebuild lock
SET lock:rebuild:user:1001 "worker:1" NX EX 10
# If acquired → rebuild cache
# If not → wait/retry or serve stale data

# Solution 2: Early expiration
# Set actual TTL longer, check a stored expiry time
HSET cache:user:1001:meta data "{...}" expires_at "1705366800" actual_ttl "7200"
# If current_time > expires_at:
#   One thread rebuilds (with lock)
#   Others serve slightly stale data (actual_ttl hasn't expired)

# Solution 3: Probabilistic early recomputation
# Each request has probability of triggering rebuild before actual expiry
# Probability increases as expiry approaches

# =============================================
# 🎯 INTERVIEW: Pros & Cons of Cache-Aside
# =============================================

# Pros:
# - Simple to implement
# - Only caches data that is actually requested
# - Cache failure doesn't break the system (just slower)
# - Resilient to cache crashes

# Cons:
# - Cache miss penalty (extra DB round trip)
# - Stale data possible (until TTL expires or invalidation)
# - Stampede risk on popular key expiry
# - Application manages both cache and DB

# Cleanup
DEL cache:user:1001 lock:rebuild:user:1001 cache:user:1001:meta
```

---

## 32. Interview Pattern: Write-Through / Write-Behind Cache

```redis
# =============================================
# SECTION 32: 🎯 INTERVIEW — WRITE-THROUGH / WRITE-BEHIND
# =============================================

# =============================================
# Write-Through: Write to cache AND DB synchronously
# =============================================

# Step 1: Write to Redis
SET cache:product:100 "{\"name\":\"Laptop\",\"price\":999}" EX 3600

# Step 2: Write to DB (synchronous)
# UPDATE products SET name='Laptop', price=999 WHERE id=100

# Both happen before returning success to client

# Pros:
# - Cache always consistent with DB
# - Read from cache is always fresh
# Cons:
# - Higher write latency (must write to both)
# - Every write goes to cache (even if not read frequently)

# =============================================
# Write-Behind (Write-Back): Write to cache, async write to DB
# =============================================

# Step 1: Write to Redis immediately (fast!)
SET cache:product:200 "{\"name\":\"Phone\",\"price\":599}" EX 3600

# Step 2: Queue DB write for async processing
RPUSH db_write_queue "{\"table\":\"products\",\"id\":200,\"data\":{\"name\":\"Phone\",\"price\":599}}"

# Background worker processes the queue:
# LPOP db_write_queue → write to DB

# Batch writes for efficiency:
RPUSH db_write_queue "{\"op\":\"update\",\"id\":200}"
RPUSH db_write_queue "{\"op\":\"update\",\"id\":201}"
RPUSH db_write_queue "{\"op\":\"update\",\"id\":202}"
# Worker batches these into single DB transaction

# Pros:
# - Very low write latency
# - Can batch/coalesce writes
# Cons:
# - Risk of data loss if Redis crashes before DB write
# - Complexity in maintaining consistency

# =============================================
# Read-Through: Cache handles DB reads automatically
# =============================================

# Application only talks to cache layer
# Cache layer handles DB read/write
# Usually implemented by cache libraries (e.g., Redis + application framework)

# Read:  App → Cache (hit? return) → DB → Cache → App
# Write: App → Cache → DB (in same operation)

# =============================================
# 🎯 INTERVIEW: Pattern Comparison
# =============================================

# | Pattern        | Read Latency | Write Latency | Consistency | Complexity |
# |----------------|-------------|---------------|-------------|------------|
# | Cache-Aside    | Miss: High  | N/A           | Eventual    | Low        |
# | Write-Through  | Low (cached)| High (2 writes)| Strong     | Medium     |
# | Write-Behind   | Low (cached)| Low (async)   | Eventual    | High       |
# | Read-Through   | Miss: High  | Varies        | Varies      | Medium     |

# Cleanup
DEL cache:product:100 cache:product:200 db_write_queue
```

---

## 33. Interview Pattern: Bloom Filter Simulation

```redis
# =============================================
# SECTION 33: 🎯 INTERVIEW — BLOOM FILTER
# =============================================

# Bloom Filter: probabilistic data structure
# "Definitely NOT in set" or "PROBABLY in set"
# False positives possible, false negatives NEVER
# 🎯 INTERVIEW: Prevent unnecessary DB lookups

# =============================================
# Redis has native Bloom Filter via RedisBloom module
# But we can simulate with bitmaps!
# =============================================

# Simulated Bloom Filter using bitmap
# We'll use multiple "hash functions" (simplified)

# "Hash function 1": sum of ASCII values mod 1000
# "Hash function 2": product of ASCII values mod 1000
# "Hash function 3": XOR of ASCII values mod 1000

# For simplicity, let's manually set bits:

# Add "alice" to bloom filter
SETBIT bloom:users 42 1    # hash1("alice") = 42
SETBIT bloom:users 317 1   # hash2("alice") = 317
SETBIT bloom:users 891 1   # hash3("alice") = 891

# Add "bob"
SETBIT bloom:users 55 1    # hash1("bob") = 55
SETBIT bloom:users 223 1   # hash2("bob") = 223
SETBIT bloom:users 667 1   # hash3("bob") = 667

# Check "alice" — ALL bits must be 1
GETBIT bloom:users 42
GETBIT bloom:users 317
GETBIT bloom:users 891
# All 1 → "probably in set" ✓

# Check "charlie" (not added)
GETBIT bloom:users 78     # hash1("charlie")
# 0 → "definitely NOT in set" ✗ (saved a DB query!)

# =============================================
# Redis Bloom Module Commands (if available)
# =============================================

# BF.ADD bloom:users "alice"
# BF.ADD bloom:users "bob"
# BF.EXISTS bloom:users "alice"   → 1
# BF.EXISTS bloom:users "charlie" → 0 (probably)
# BF.MADD bloom:users "charlie" "diana"
# BF.MEXISTS bloom:users "alice" "charlie" "eve"

# BF.RESERVE bloom:emails 0.01 1000000
# Creates bloom filter for 1M elements with 1% false positive rate

# =============================================
# 🎯 INTERVIEW USE CASES
# =============================================

# 1. Username availability check
# Before querying DB: check bloom filter
# If bloom says "no" → definitely available (skip DB)
# If bloom says "yes" → might be taken, check DB to confirm

# 2. Prevent duplicate processing
# Before processing a URL/event:
# Check bloom → already processed? skip!

# 3. Cache warming
# Check if data is likely in cache before requesting

# 4. Malicious URL detection
# Google Chrome: uses bloom filter to check URLs against known bad URLs

# =============================================
# Cuckoo Filter (Redis Bloom module)
# =============================================

# Like Bloom but supports DELETE!
# CF.ADD filter "item"
# CF.EXISTS filter "item"
# CF.DEL filter "item"

# Cleanup
DEL bloom:users
```

---

## 34. Interview Pattern: Idempotency Keys

```redis
# =============================================
# SECTION 34: 🎯 INTERVIEW — IDEMPOTENCY KEYS
# =============================================

# Scenario: Prevent duplicate operations (e.g., double payment)
# Client sends idempotency key with request

# =============================================
# Basic Pattern
# =============================================

# Client sends: POST /payments { amount: 100, idempotency_key: "pay-abc-123" }

# Check if already processed
GET idempotency:pay-abc-123
# nil → first time, process it

# Start processing: mark as "in progress"
SET idempotency:pay-abc-123 "processing" NX EX 86400
# NX ensures only one request proceeds
# EX 86400 = 24 hour expiry

# After successful processing: store result
SET idempotency:pay-abc-123 "{\"status\":\"success\",\"payment_id\":\"pay_1234\",\"amount\":100}" EX 86400

# Duplicate request comes in:
GET idempotency:pay-abc-123
# Returns stored result → return same response to client
# Payment NOT processed twice! ✓

# =============================================
# Atomic Check-and-Set with Lua
# =============================================

EVAL "\
local key = KEYS[1]\
local existing = redis.call('GET', key)\
if existing then\
    return existing\
end\
redis.call('SET', key, ARGV[1], 'EX', tonumber(ARGV[2]))\
return nil" 1 "idempotency:pay-xyz-456" "processing" "86400"
# Returns nil if first request (proceed with processing)
# Returns stored value if duplicate (return cached response)

# =============================================
# Full Flow
# =============================================

# 1. Request arrives with idempotency key
# 2. SET key "processing" NX EX 86400
#    - If OK → first request, proceed
#    - If nil → check GET key
#      - "processing" → concurrent duplicate, return 409 or wait
#      - result JSON → completed duplicate, return cached result

# 3. Process the operation
# 4. SET key <result_json> EX 86400 (overwrite "processing")

# =============================================
# 🎯 INTERVIEW: Edge Cases
# =============================================

# Q: What if processing crashes after SET NX but before completion?
# A: The "processing" value with TTL handles this.
#    After TTL expires, client can retry.
#    Or: use a shorter TTL for "processing" state and check timestamps.

# Q: How to handle concurrent duplicate requests?
# A: NX ensures only one proceeds. Others see "processing" and can:
#    - Return 409 Conflict
#    - Retry after short delay
#    - Use Pub/Sub to wait for completion

# Cleanup
DEL idempotency:pay-abc-123 idempotency:pay-xyz-456
```

---

## 35. Interview Pattern: Presence / Online Status

```redis
# =============================================
# SECTION 35: 🎯 INTERVIEW — PRESENCE / ONLINE STATUS
# =============================================

# Scenario: WhatsApp/Slack-like online/offline status

# =============================================
# Approach 1: Key with TTL (Simple)
# =============================================

# User comes online → set key with TTL
SET presence:user:1001 "online" EX 60
SET presence:user:1002 "online" EX 60
SET presence:user:1003 "online" EX 60

# Heartbeat every 30 seconds → refresh TTL
SET presence:user:1001 "online" EX 60

# Check if user is online
GET presence:user:1001
# "online" → online
# nil → offline (key expired)

# =============================================
# Approach 2: Sorted Set with Timestamps
# =============================================

# Score = last heartbeat timestamp
ZADD online_users 1705363200 "user:1001"
ZADD online_users 1705363200 "user:1002"
ZADD online_users 1705363190 "user:1003"
ZADD online_users 1705363100 "user:1004"  # Stale!

# Heartbeat update
ZADD online_users 1705363260 "user:1001"

# Get online users (heartbeat within last 60 seconds)
# Assume now = 1705363260
ZRANGEBYSCORE online_users 1705363200 +inf
# Users with heartbeat in last 60 seconds

# Remove stale users
ZREMRANGEBYSCORE online_users 0 1705363140
# Removes users with heartbeat > 2 minutes ago

# Count online users
ZCOUNT online_users 1705363200 +inf

# =============================================
# Approach 3: Bitmap (for large scale)
# =============================================

# Bit offset = user ID
SETBIT online:bitmap 1001 1
SETBIT online:bitmap 1002 1
SETBIT online:bitmap 1003 1

# Check online
GETBIT online:bitmap 1001
# 1 → online

# Count online users
BITCOUNT online:bitmap

# User goes offline
SETBIT online:bitmap 1001 0

# 🎯 Challenge: How to handle TTL with bitmap?
# Option 1: Periodic cleanup job clears stale bits
# Option 2: Use bitmap per time window, BITOP AND recent windows

# =============================================
# Real-time Status Broadcast
# =============================================

# When user status changes, notify friends
# PUBLISH presence:updates "{\"user\":\"user:1001\",\"status\":\"online\"}"
# PUBLISH presence:updates "{\"user\":\"user:1001\",\"status\":\"offline\"}"

# Friends subscribe to their interests:
# SUBSCRIBE presence:user:1001 presence:user:1002

# =============================================
# 🎯 INTERVIEW: "Last Seen" Feature
# =============================================

# When user goes offline, record timestamp
HSET user:1001:status state "offline" last_seen "1705363200"

# Display "Last seen: 5 minutes ago"

# When user comes online:
HSET user:1001:status state "online" last_seen "0"

# =============================================
# 🎯 INTERVIEW: Scaling Presence
# =============================================

# Challenge: 100M connected users, real-time presence
# 
# 1. Use sorted set per region/shard
# 2. Heartbeat interval: 30s (balance accuracy vs load)
# 3. Batch heartbeat updates (pipeline)
# 4. Presence fanout: only notify users who are currently viewing contacts
# 5. Lazy presence: check on demand, don't push to everyone
#
# WhatsApp approach: "last seen" is lazy (checked on profile view)
# Slack approach: active polling with intelligent batching

# Cleanup
DEL presence:user:1001 presence:user:1002 presence:user:1003 online_users online:bitmap user:1001:status
```

---

## 36. Interview Pattern: Trending / Top-K

```redis
# =============================================
# SECTION 36: 🎯 INTERVIEW — TRENDING / TOP-K
# =============================================

# Scenario: Twitter trending hashtags, trending products, hot topics

# =============================================
# Approach 1: Sorted Set with Time Windows
# =============================================

# Track hashtag counts per hour
ZINCRBY trending:2024-01-15:10 1 "#redis"
ZINCRBY trending:2024-01-15:10 1 "#redis"
ZINCRBY trending:2024-01-15:10 1 "#mongodb"
ZINCRBY trending:2024-01-15:10 1 "#redis"
ZINCRBY trending:2024-01-15:10 1 "#python"
ZINCRBY trending:2024-01-15:10 1 "#python"
ZINCRBY trending:2024-01-15:10 1 "#javascript"
ZINCRBY trending:2024-01-15:10 1 "#redis"
ZINCRBY trending:2024-01-15:10 1 "#golang"

EXPIRE trending:2024-01-15:10 7200

# Get top 5 trending
ZRANGE trending:2024-01-15:10 0 4 REV WITHSCORES
# Expected: #redis(4), #python(2), #mongodb(1), #javascript(1), #golang(1)

# =============================================
# Multi-hour Trending (aggregate windows)
# =============================================

ZADD trending:2024-01-15:09 5 "#redis" 8 "#python" 3 "#golang"
ZADD trending:2024-01-15:10 4 "#redis" 2 "#python" 1 "#javascript"
ZADD trending:2024-01-15:11 7 "#redis" 3 "#docker" 6 "#python"

# Weighted union (more recent hours weighted higher)
ZUNIONSTORE trending:recent 3 trending:2024-01-15:09 trending:2024-01-15:10 trending:2024-01-15:11 WEIGHTS 1 2 3
ZRANGE trending:recent 0 4 REV WITHSCORES

# =============================================
# Approach 2: Count-Min Sketch (Redis Bloom module)
# =============================================

# Memory-efficient approximate counting
# CMS.INITBYDIM trending_cms 2000 5
# CMS.INCRBY trending_cms "#redis" 1 "#python" 1 "#golang" 1
# CMS.QUERY trending_cms "#redis" "#python"

# =============================================
# Approach 3: Top-K Data Structure (Redis Bloom module)
# =============================================

# Keeps only top K items
# TOPK.RESERVE top_hashtags 10 50 7 0.925
# TOPK.ADD top_hashtags "#redis" "#python" "#redis" "#golang"
# TOPK.LIST top_hashtags
# TOPK.COUNT top_hashtags "#redis"

# =============================================
# Approach 4: Exponential Decay (Recency Weighted)
# =============================================

# Score decays over time, recent events have more weight
# decay_factor = e^(-lambda * age)
# New score = current_score * decay_factor + 1

EVAL "\
local key = KEYS[1]\
local member = ARGV[1]\
local now = tonumber(ARGV[2])\
local lambda = 0.001\
\
local current_score = tonumber(redis.call('ZSCORE', key, member) or '0')\
local last_update = tonumber(redis.call('HGET', key .. ':ts', member) or now)\
local age = now - last_update\
local decayed = current_score * math.exp(-lambda * age) + 1\
\
redis.call('ZADD', key, decayed, member)\
redis.call('HSET', key .. ':ts', member, now)\
return tostring(decayed)" 1 "trending:decay" "#redis" "1705363200"

EVAL "\
local key = KEYS[1]\
local member = ARGV[1]\
local now = tonumber(ARGV[2])\
local lambda = 0.001\
\
local current_score = tonumber(redis.call('ZSCORE', key, member) or '0')\
local last_update = tonumber(redis.call('HGET', key .. ':ts', member) or now)\
local age = now - last_update\
local decayed = current_score * math.exp(-lambda * age) + 1\
\
redis.call('ZADD', key, decayed, member)\
redis.call('HSET', key .. ':ts', member, now)\
return tostring(decayed)" 1 "trending:decay" "#python" "1705363210"

ZRANGE trending:decay 0 -1 REV WITHSCORES

# =============================================
# 🎯 INTERVIEW: Trending at Scale
# =============================================

# Challenge: Billions of events, real-time top-K

# 1. Two-level counting:
#    - Level 1: Local count per app server (in-memory)
#    - Level 2: Periodic flush to Redis (ZINCRBY in pipeline)
#    - Reduces Redis write load by 10-100x

# 2. Time-windowed sorted sets with ZUNIONSTORE for aggregation
# 3. Count-Min Sketch for space-efficient approximate counting
# 4. Separate "trending candidates" from "confirmed trending"
#    - Only items above threshold enter sorted set

# Cleanup
DEL trending:2024-01-15:09 trending:2024-01-15:10 trending:2024-01-15:11 trending:recent trending:decay trending:decay:ts
```

---

## 37. Interview Pattern: Distributed Counter

```redis
# =============================================
# SECTION 37: 🎯 INTERVIEW — DISTRIBUTED COUNTER
# =============================================

# Scenario: High-throughput counters (page views, likes, inventory)

# =============================================
# Simple Counter
# =============================================

INCR page_views:/home
INCR page_views:/home
INCR page_views:/home
GET page_views:/home
# Expected: "3"

# =============================================
# Sharded Counter (for hot keys)
# =============================================

# Problem: Single key = single Redis thread = bottleneck under extreme load
# Solution: Shard the counter across multiple keys

# Increment a random shard (client picks shard 0-9)
INCR counter:likes:post:1001:shard:0
INCR counter:likes:post:1001:shard:3
INCR counter:likes:post:1001:shard:7
INCR counter:likes:post:1001:shard:3
INCR counter:likes:post:1001:shard:1

# Read: sum all shards
MGET counter:likes:post:1001:shard:0 counter:likes:post:1001:shard:1 counter:likes:post:1001:shard:2 counter:likes:post:1001:shard:3 counter:likes:post:1001:shard:4 counter:likes:post:1001:shard:5 counter:likes:post:1001:shard:6 counter:likes:post:1001:shard:7 counter:likes:post:1001:shard:8 counter:likes:post:1001:shard:9
# Sum non-nil values in application

# Or use Lua:
EVAL "\
local total = 0\
for i = 0, 9 do\
    local val = redis.call('GET', KEYS[1] .. ':shard:' .. i)\
    if val then total = total + tonumber(val) end\
end\
return total" 1 "counter:likes:post:1001"

# =============================================
# Exact Counter with Periodic Flush
# =============================================

# Pattern: Accumulate in Redis, periodically flush to DB
INCRBY counter:product:100:views 1
INCRBY counter:product:100:views 1
INCRBY counter:product:100:views 1

# Every 5 minutes: read and reset
GETSET counter:product:100:views "0"
# Returns accumulated count, resets to 0
# Write the returned count to database

# Or atomic with GETDEL:
GETDEL counter:product:100:views
# Returns count and removes key

# =============================================
# 🎯 INTERVIEW: Counter at Scale
# =============================================

# Challenge: 1M increments/second for a viral post

# 1. Client-side batching:
#    Accumulate locally for 100ms, then INCRBY <batch_count>
#    Reduces Redis ops by 100x

# 2. Sharded counters (as above)
#    10 shards → 10x throughput

# 3. Approximate counter (probabilistic increment)
#    Only increment with probability 1/N
#    Multiply count by N for estimate
#    Reduces Redis ops by Nx

# 4. Two-level counting:
#    Level 1: In-memory per app server
#    Level 2: Periodic sync to Redis

# Cleanup
DEL page_views:/home counter:likes:post:1001:shard:0 counter:likes:post:1001:shard:1 counter:likes:post:1001:shard:2 counter:likes:post:1001:shard:3 counter:likes:post:1001:shard:4 counter:likes:post:1001:shard:5 counter:likes:post:1001:shard:6 counter:likes:post:1001:shard:7 counter:likes:post:1001:shard:8 counter:likes:post:1001:shard:9 counter:product:100:views
```

---

## 38. Interview Pattern: Notification System

```redis
# =============================================
# SECTION 38: 🎯 INTERVIEW — NOTIFICATION SYSTEM
# =============================================

# Scenario: In-app notification system (like Facebook/Instagram)

# =============================================
# Store Notifications (per user, using List)
# =============================================

# Push notification to user's list
LPUSH notifications:user:1001 "{\"id\":\"n1\",\"type\":\"like\",\"from\":\"bob\",\"post\":\"p100\",\"time\":1705363200,\"read\":false}"
LPUSH notifications:user:1001 "{\"id\":\"n2\",\"type\":\"comment\",\"from\":\"charlie\",\"post\":\"p100\",\"time\":1705363300,\"read\":false}"
LPUSH notifications:user:1001 "{\"id\":\"n3\",\"type\":\"follow\",\"from\":\"diana\",\"time\":1705363400,\"read\":false}"

# Keep only last 100 notifications
LTRIM notifications:user:1001 0 99

# Get latest 10 notifications
LRANGE notifications:user:1001 0 9

# =============================================
# Unread Count
# =============================================

# Increment unread counter
INCR notifications:user:1001:unread
INCR notifications:user:1001:unread
INCR notifications:user:1001:unread
GET notifications:user:1001:unread
# Expected: "3"

# Mark all as read (reset counter)
SET notifications:user:1001:unread "0"

# =============================================
# Using Streams (Better approach)
# =============================================

XADD notifications:stream:user:1001 * type "like" from "bob" post "p100"
XADD notifications:stream:user:1001 * type "comment" from "charlie" post "p100" text "Great post!"
XADD notifications:stream:user:1001 * type "follow" from "diana"

# Read notifications
XREVRANGE notifications:stream:user:1001 + - COUNT 10

# Track last read position
SET notifications:user:1001:last_read "0-0"

# Count unread (entries after last read)
EVAL "\
local last = redis.call('GET', KEYS[2]) or '0-0'\
local msgs = redis.call('XRANGE', KEYS[1], '(' .. last, '+')\
return #msgs" 2 notifications:stream:user:1001 notifications:user:1001:last_read

# =============================================
# Notification Preferences
# =============================================

HSET user:1001:notification_prefs email_likes "true" email_comments "true" email_follows "false" push_likes "true" push_comments "true" push_follows "true"

# Check before sending
HGET user:1001:notification_prefs email_follows
# "false" → don't send email for follows

# =============================================
# Batch Notifications (Aggregation)
# =============================================

# Instead of "Alice liked", "Bob liked", "Charlie liked"
# → "Alice, Bob, and 1 other liked your post"

# Aggregate by type + target within a window
SADD notif:agg:post:100:like "alice" "bob" "charlie"
SCARD notif:agg:post:100:like
# 3 → "Alice, Bob, and 1 other liked your post"

# Cleanup
DEL notifications:user:1001 notifications:user:1001:unread notifications:stream:user:1001 notifications:user:1001:last_read user:1001:notification_prefs notif:agg:post:100:like
```

---

## 39. Interview Pattern: URL Shortener Cache

```redis
# =============================================
# SECTION 39: 🎯 INTERVIEW — URL SHORTENER CACHE
# =============================================

# Scenario: TinyURL/bit.ly style URL shortener with Redis cache

# =============================================
# Store URL Mapping
# =============================================

# Short code → Long URL
SET url:abc123 "https://www.example.com/very/long/path/to/some/resource?param=value" EX 2592000
SET url:xyz789 "https://docs.redis.io/commands/set" EX 2592000

# Reverse mapping (for deduplication)
SET url:reverse:https://www.example.com/very/long/path/to/some/resource?param=value "abc123" EX 2592000

# =============================================
# Redirect Flow
# =============================================

# User visits https://short.url/abc123
GET url:abc123
# Returns long URL → redirect (301/302)

# Cache miss? → Check database → Populate cache
# SET url:abc123 <long_url> EX 2592000

# =============================================
# Click Analytics
# =============================================

# Increment click counter
INCR url:abc123:clicks

# Track unique visitors (HyperLogLog)
PFADD url:abc123:visitors "ip:1.2.3.4"
PFADD url:abc123:visitors "ip:5.6.7.8"
PFADD url:abc123:visitors "ip:1.2.3.4"
PFCOUNT url:abc123:visitors
# Expected: 2

# Track clicks per day
INCR url:abc123:clicks:2024-01-15
EXPIRE url:abc123:clicks:2024-01-15 2592000

# Click metadata (using stream)
XADD url:abc123:clickstream * ip "1.2.3.4" country "US" referer "twitter.com" ua "Chrome"

# =============================================
# URL Creation with Dedup
# =============================================

# Check if long URL already shortened
GET url:reverse:https://www.example.com/some/page
# If exists → return existing short code
# If nil → generate new short code, store both mappings

# Atomic URL creation with Lua
EVAL "\
local reverse_key = 'url:reverse:' .. ARGV[1]\
local existing = redis.call('GET', reverse_key)\
if existing then\
    return existing\
end\
local short_code = ARGV[2]\
redis.call('SET', 'url:' .. short_code, ARGV[1], 'EX', 2592000)\
redis.call('SET', reverse_key, short_code, 'EX', 2592000)\
return short_code" 0 "https://new-url.com/page" "def456"

GET url:def456
# Expected: "https://new-url.com/page"

# =============================================
# 🎯 INTERVIEW: URL Shortener Design Points
# =============================================

# 1. ID Generation: Base62(auto_increment) or Random
# 2. Cache: Redis for hot URLs (80/20 rule — 20% URLs get 80% traffic)
# 3. Storage: DB for all URLs (Cassandra/DynamoDB for scale)
# 4. Analytics: Redis HyperLogLog for unique visitors, counters for clicks
# 5. Expiration: TTL on Redis keys + DB cleanup job

# Cleanup
DEL url:abc123 url:xyz789 url:reverse:https://www.example.com/very/long/path/to/some/resource?param=value url:abc123:clicks url:abc123:visitors url:abc123:clicks:2024-01-15 url:abc123:clickstream url:def456 url:reverse:https://new-url.com/page
```

---

## 40. Interview Pattern: Stock Ticker / Time-Series

```redis
# =============================================
# SECTION 40: 🎯 INTERVIEW — STOCK TICKER / TIME-SERIES
# =============================================

# Scenario: Real-time stock price tracking and aggregation

# =============================================
# Store Price Ticks (using Streams)
# =============================================

XADD stock:AAPL * price "185.50" volume "1000"
XADD stock:AAPL * price "185.75" volume "500"
XADD stock:AAPL * price "186.00" volume "2000"
XADD stock:AAPL * price "185.80" volume "800"
XADD stock:AAPL * price "186.25" volume "1500"

# Read latest ticks
XREVRANGE stock:AAPL + - COUNT 5

# Read ticks in time range
XRANGE stock:AAPL - +

# =============================================
# Store Price Ticks (using Sorted Set)
# =============================================

# Score = timestamp, member = price:volume
ZADD stock:GOOG:ticks 1705363200 "185.50:1000"
ZADD stock:GOOG:ticks 1705363260 "185.75:500"
ZADD stock:GOOG:ticks 1705363320 "186.00:2000"
ZADD stock:GOOG:ticks 1705363380 "185.80:800"

# Get ticks in time range
ZRANGEBYSCORE stock:GOOG:ticks 1705363200 1705363380 WITHSCORES

# =============================================
# OHLC Aggregation (Open, High, Low, Close)
# =============================================

# Store OHLC per minute
HSET stock:AAPL:ohlc:2024-01-15:10:30 open "185.50" high "186.25" low "185.50" close "186.25" volume "5800"
HSET stock:AAPL:ohlc:2024-01-15:10:31 open "186.25" high "187.00" low "186.00" close "186.50" volume "4200"

# Get OHLC for a specific minute
HGETALL stock:AAPL:ohlc:2024-01-15:10:30

# =============================================
# Current Price (always latest)
# =============================================

SET stock:AAPL:current "186.25"
SET stock:GOOG:current "142.50"
SET stock:MSFT:current "390.00"

# Get multiple stock prices at once
MGET stock:AAPL:current stock:GOOG:current stock:MSFT:current

# Publish price updates for subscribers
# PUBLISH stock:AAPL:updates "186.25"

# =============================================
# Portfolio Watchlist
# =============================================

SADD watchlist:user:1001 "AAPL" "GOOG" "MSFT" "AMZN"

# Get user's watchlist
SMEMBERS watchlist:user:1001

# =============================================
# Redis TimeSeries Module (if available)
# =============================================

# TS.CREATE stock:AAPL:price RETENTION 86400000 LABELS symbol AAPL type price
# TS.ADD stock:AAPL:price * 185.50
# TS.ADD stock:AAPL:price * 185.75
# TS.ADD stock:AAPL:price * 186.00
# TS.RANGE stock:AAPL:price - + COUNT 100
# TS.MRANGE - + FILTER symbol=AAPL
# TS.RANGE stock:AAPL:price - + AGGREGATION avg 60000

# =============================================
# 🎯 INTERVIEW: Time-Series at Scale
# =============================================

# Challenge: Millions of ticks per second across thousands of stocks

# 1. Partition by symbol: stock:AAPL, stock:GOOG (natural sharding)
# 2. Use Streams for raw ticks (auto-trim with MAXLEN)
# 3. Pre-aggregate OHLC in Lua or Streams consumer
# 4. Use TTL for data retention (raw: 1 day, 1min OHLC: 30 days, daily: forever)
# 5. Hot stocks (top 100) cached with shorter TTL
# 6. Pub/Sub for real-time price distribution to websockets

# Cleanup
DEL stock:AAPL stock:GOOG:ticks stock:AAPL:ohlc:2024-01-15:10:30 stock:AAPL:ohlc:2024-01-15:10:31 stock:AAPL:current stock:GOOG:current stock:MSFT:current watchlist:user:1001
```

---

## 41. Performance & Debugging Commands

```redis
# =============================================
# SECTION 41: PERFORMANCE & DEBUGGING
# =============================================

# =============================================
# Memory Analysis
# =============================================

INFO memory
# used_memory, used_memory_peak, fragmentation_ratio, etc.

# Memory usage of a specific key
SET test_key "hello world"
MEMORY USAGE test_key
# Returns bytes

MEMORY DOCTOR
# Automated memory issue diagnosis

# Find biggest keys (run from command line):
# redis-cli --bigkeys
# Scans entire keyspace, reports biggest key per type

# redis-cli --memkeys
# Shows memory usage per key (slow!)

# =============================================
# Slow Log
# =============================================

CONFIG GET slowlog-log-slower-than
# Default: 10000 (microseconds = 10ms)

CONFIG GET slowlog-max-len
# Default: 128 entries

SLOWLOG GET 10
# Last 10 slow queries

SLOWLOG LEN
# Total slow queries recorded

SLOWLOG RESET
# Clear slow log

# =============================================
# Latency Monitoring
# =============================================

CONFIG SET latency-monitor-threshold 100
# Track operations taking > 100ms

# LATENCY LATEST
# LATENCY HISTORY event-name
# LATENCY RESET

# =============================================
# Client Management
# =============================================

CLIENT LIST
# All connected clients

CLIENT INFO
# Current client info

CLIENT GETNAME
CLIENT SETNAME "debug-session"

# Kill a specific client
# CLIENT KILL ID <client-id>

# =============================================
# Debug Commands
# =============================================

# Object introspection
SET num_val "12345"
OBJECT ENCODING num_val
# "int"

SET str_val "a short string"
OBJECT ENCODING str_val
# "embstr"

SET long_str "this is a very long string that exceeds forty four bytes for sure to test raw encoding"
OBJECT ENCODING long_str
# "raw"

LPUSH my_list "a" "b" "c"
OBJECT ENCODING my_list
# "listpack" or "quicklist"

HSET my_hash f1 "v1"
OBJECT ENCODING my_hash
# "listpack" or "ziplist"

SADD my_set "1" "2" "3"
OBJECT ENCODING my_set
# "listpack" or "hashtable"

ZADD my_zset 1 "a" 2 "b"
OBJECT ENCODING my_zset
# "listpack" or "skiplist"

# Idle time (seconds since last access)
OBJECT IDLETIME my_hash

# =============================================
# Key Statistics
# =============================================

DBSIZE
# Total number of keys

INFO keyspace
# Keys per database + expiring keys

# Key distribution analysis (from CLI):
# redis-cli --bigkeys
# redis-cli --hotkeys (requires LFU policy)

# =============================================
# CONFIG Best Practices Check
# =============================================

CONFIG GET maxmemory
CONFIG GET maxmemory-policy
CONFIG GET appendonly
CONFIG GET save
CONFIG GET tcp-backlog
CONFIG GET timeout
CONFIG GET hz

# Cleanup
DEL test_key num_val str_val long_str my_list my_hash my_set my_zset
```

---

## 42. Anti-Patterns & Common Mistakes

```redis
# =============================================
# SECTION 42: ⚠️ ANTI-PATTERNS & COMMON MISTAKES
# =============================================

# =============================================
# ❌ Anti-Pattern 1: Using KEYS in production
# =============================================

# BAD: KEYS user:*   → Blocks Redis, O(N) scan of all keys!
# GOOD: Use SCAN with cursor
MSET user:1 "a" user:2 "b" user:3 "c"
SCAN 0 MATCH user:* COUNT 10

# =============================================
# ❌ Anti-Pattern 2: Storing huge values
# =============================================

# BAD: SET big_key <10MB JSON blob>
# Redis is single-threaded — large values block other operations!
# GOOD: Break into smaller pieces, use Hashes, or store in S3/blob storage

# =============================================
# ❌ Anti-Pattern 3: No TTL on cache keys
# =============================================

# BAD:
SET cache:data "value"
# Key lives forever → memory leak!

# GOOD:
SET cache:data "value" EX 3600

# =============================================
# ❌ Anti-Pattern 4: Using Redis as primary database
# =============================================

# Redis is great for caching, sessions, real-time features
# But WITHOUT proper persistence config, data can be lost!
# Always have a primary database (PostgreSQL, DynamoDB, etc.)

# =============================================
# ❌ Anti-Pattern 5: Too many small keys
# =============================================

# BAD: user:1001:name, user:1001:email, user:1001:age (3 keys per user)
# Each key has ~50 bytes overhead!

# GOOD: Use Hash
HSET user:1001 name "Alice" email "alice@example.com" age "30"
# Single key, less overhead, ziplist encoding for small hashes

# =============================================
# ❌ Anti-Pattern 6: Not using Pipeline
# =============================================

# BAD: 100 separate SET commands (100 round trips)
# GOOD: Pipeline them (1 round trip)
# In code: pipeline = redis.pipeline(); for i in range(100): pipeline.set(f"k{i}", f"v{i}"); pipeline.execute()

# =============================================
# ❌ Anti-Pattern 7: DEL on large collections
# =============================================

# BAD: DEL huge_sorted_set  → Blocks Redis for seconds!
# GOOD: UNLINK huge_sorted_set  → Async deletion

# Or gradual deletion:
# While ZCARD huge_sorted_set > 0:
#   ZPOPMIN huge_sorted_set 100  (delete 100 at a time)

# =============================================
# ❌ Anti-Pattern 8: Using SELECT for multi-tenancy
# =============================================

# BAD: SELECT 0 for tenant A, SELECT 1 for tenant B
# No per-database memory limits!
# GOOD: Use key prefixes: tenant:A:key, tenant:B:key
# Or: separate Redis instances per tenant

# =============================================
# ❌ Anti-Pattern 9: Hot Key Problem
# =============================================

# BAD: One key accessed millions of times per second
# Even Redis single-thread can bottleneck!

# GOOD:
# 1. Local cache (app-level) in front of Redis
# 2. Replicas for read scaling
# 3. Sharded keys: hot_key:shard:{0-9}

# =============================================
# ❌ Anti-Pattern 10: Lua scripts that are too long
# =============================================

# BAD: Lua script running for 5 seconds → blocks ALL Redis operations!
# GOOD: Keep Lua scripts short and fast
# Use SCRIPT KILL to kill running script if needed
# CONFIG SET lua-time-limit 5000 (default: 5 seconds)

# Cleanup
DEL user:1 user:2 user:3 cache:data user:1001
```

---

## 43. Interview Quick-Fire Q&A Commands

```redis
# =============================================
# SECTION 43: 🎯 INTERVIEW QUICK-FIRE Q&A
# =============================================

# Run these to demonstrate answers to common interview questions!

# =============================================
# Q: "Is Redis single-threaded?"
# =============================================
# A: Redis command processing is single-threaded (event loop).
#    But Redis 6.0+ uses I/O threads for network reading/writing.
#    Background threads for: RDB/AOF, lazy deletion, etc.

INFO server
# Check redis_version and process_id

# =============================================
# Q: "What's the max size of a Redis key?"
# =============================================
# A: 512 MB (for both key name and value)

# =============================================
# Q: "How does Redis handle concurrent clients?"
# =============================================
# A: Event-driven I/O multiplexing (epoll/kqueue)
#    All commands serialized by single thread → no locks needed!

CLIENT LIST
# See all concurrent clients

# =============================================
# Q: "How to check Redis performance?"
# =============================================
# From command line:
# redis-benchmark -q -n 100000
# redis-benchmark -q -n 100000 -t set,get

# =============================================
# Q: "What happens when Redis runs out of memory?"
# =============================================
CONFIG GET maxmemory
CONFIG GET maxmemory-policy
# noeviction → returns error
# allkeys-lru → evicts LRU key
# (see Section 18 for full list)

# =============================================
# Q: "How would you implement a cache with Redis?"
# =============================================

# 1. Set with TTL:
SET cache:user:1 "data" EX 300

# 2. Check cache before DB:
GET cache:user:1
# nil → cache miss → read DB → SET cache

# 3. Invalidate on update:
DEL cache:user:1

# =============================================
# Q: "Difference between DEL and UNLINK?"
# =============================================
SET deltest "value"
DEL deltest        # Synchronous: blocks Redis if key is large
SET deltest "value"
UNLINK deltest     # Asynchronous: non-blocking, reclaims memory in background

# =============================================
# Q: "How would you implement a distributed lock?"
# =============================================
SET lock:resource "owner123" NX EX 30
# NX: only if not exists (acquire)
# EX: auto-expire (prevent deadlock)
# Release: DEL (with Lua to check ownership)

# =============================================
# Q: "How does Redis persist data?"
# =============================================
CONFIG GET save          # RDB snapshot intervals
CONFIG GET appendonly    # AOF enabled?
CONFIG GET appendfsync   # AOF sync policy

# =============================================
# Q: "What data structures does Redis support?"
# =============================================
SET str "string"                         # String
LPUSH lst "list"                         # List
SADD st "set"                            # Set
ZADD zst 1 "sorted_set"                 # Sorted Set
HSET hsh field "hash"                   # Hash
PFADD hll "hyperloglog"                 # HyperLogLog
SETBIT bmp 0 1                          # Bitmap
GEOADD geo 0 0 "geospatial"            # Geo
XADD strm * key "stream"               # Stream

TYPE str
TYPE lst
TYPE st
TYPE zst
TYPE hsh

DEL str lst st zst hsh hll bmp geo strm lock:resource deltest cache:user:1

# =============================================
# Q: "How would you handle the thundering herd problem?"
# =============================================

# Approach 1: Mutex/lock-based cache rebuild
SET lock:rebuild:popular_key "worker1" NX EX 10
# Only winner rebuilds, others wait or serve stale

# Approach 2: Early revalidation
# Refresh cache before it actually expires

# Approach 3: Never expire (background refresh)
# Background job refreshes cache periodically

DEL lock:rebuild:popular_key

# =============================================
# Q: "Redis vs Memcached?"
# =============================================
# Redis:
# + Rich data structures (lists, sets, sorted sets, etc.)
# + Persistence (RDB, AOF)
# + Pub/Sub, Streams
# + Lua scripting
# + Cluster mode (sharding)
# + Replication
# + Atomic operations
#
# Memcached:
# + Multi-threaded (better for pure cache workload)
# + Simpler, lightweight
# + Slab allocator (less memory fragmentation)
# - Only key-value (strings)
# - No persistence
# - No pub/sub
# - Limited data types

# =============================================
# 🏆 CONGRATULATIONS! 
# You've completed the Redis Practice Guide!
# =============================================
# 
# Summary of what you practiced:
# ✅ All 9+ data structures (String, List, Set, Sorted Set, Hash, HyperLogLog, Bitmap, Geo, Stream)
# ✅ Persistence (RDB, AOF)
# ✅ Transactions (MULTI/EXEC/WATCH)
# ✅ Lua Scripting
# ✅ Pub/Sub & Streams
# ✅ Memory Management & Eviction
# ✅ Replication & Cluster
# ✅ 20+ Interview Patterns:
#    - Rate Limiter (4 approaches!)
#    - Distributed Lock (Redlock)
#    - Session Store
#    - Leaderboard
#    - Feed/Timeline (fan-out on read/write)
#    - Unique Visitors (SET vs HLL vs Bitmap)
#    - Autocomplete
#    - Job Queue
#    - Sliding Window Counter
#    - Chat System
#    - Cache Patterns (Cache-Aside, Write-Through, Write-Behind)
#    - Bloom Filter
#    - Idempotency Keys
#    - Presence/Online Status
#    - Trending/Top-K
#    - Distributed Counter
#    - Notification System
#    - URL Shortener
#    - Stock Ticker/Time-Series
# ✅ Performance Debugging
# ✅ Anti-Patterns
# ✅ Interview Quick-Fire Q&A
# 
# You're ready to ace any Redis interview! 🚀
```
