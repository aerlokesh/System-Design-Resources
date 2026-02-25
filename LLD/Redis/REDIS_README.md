# Redis Real-World Interview Questions — Comprehensive Guide

## 📚 Overview

This comprehensive collection covers **75 real-world Redis interview problems** organized by data structure and pattern. Each example is interview-ready with actual Redis commands, time complexities, Lua scripts, and system design context — exactly what FAANG interviewers expect.

## 🎯 Structure

| File | Topics | Problems |
|------|--------|----------|
| `redis-data-structures-comprehensive-guide.md` | All Redis data structures at a glance | Quick reference |
| `redis-when-to-use-what.md` | Decision guide for picking the right structure | Cheat sheet |
| `RedisRealWorldExamples1_Strings_Hashes.md` | STRING + HASH based problems | #1 – #20 |
| `RedisRealWorldExamples2_Lists_Sets.md` | LIST + SET based problems | #21 – #40 |
| `RedisRealWorldExamples3_SortedSets_PubSub_Streams.md` | ZSET + PUB/SUB + STREAMS | #41 – #57 |
| `RedisRealWorldExamples4_Distributed_Geo_Advanced.md` | Distributed locks, Geo, HyperLogLog, Advanced | #58 – #75 |

## 🔹 Problem Categories

### STRING-BASED (10 Problems)
1. API Rate Limiter (INCR + EXPIRE)
2. Session Token Storage (SETEX)
3. OTP Code Generation with Expiry (SETEX)
4. Duplicate Payment Prevention (SETNX)
5. Page View Counter (INCR)
6. Flash Sale Stock Decrement (DECR)
7. User Login Count Tracker (INCRBY)
8. Database Query Result Cache with TTL
9. Temporary Auth Token for Password Reset
10. Atomic Multi-Step Update (Lua Script)

### HASH-BASED (10 Problems)
11. User Profile Attribute Store
12. Shopping Cart per User (HSET)
13. Per-User Metrics Increment (HINCRBY)
14. Outdated Field Cleanup (HDEL)
15. Bulk User Attribute Fetch (HGETALL)
16. Leaderboard with User Metadata (Hash + ZSet)
17. Per-Product Inventory Tracking
18. Feature Flags per User
19. Multi-Tenant Configuration Store
20. Real-Time Recommendation User Preferences

### LIST-BASED (10 Problems)
21. Message Queue (LPUSH / RPOP)
22. Chat Feed (LPUSH)
23. Last N Messages Fetch (LRANGE)
24. Blocking Background Worker Queue (BLPOP)
25. Recent User Activity Log
26. Failed Job Retry Queue
27. Task Prioritization with Multiple Lists
28. Page Visit History per User
29. Event Stream Consumer (RPOP/LPOP)
30. FIFO Order Processing System

### SET-BASED (10 Problems)
31. Unique Visitor Tracker (SADD)
32. Active User Count (SCARD)
33. User Subscription Check (SISMEMBER)
34. Users Who Liked Both Products (SINTER)
35. Newsletter Subscriber Merge (SUNION)
36. Signed Up But Didn't Purchase (SDIFF)
37. Online Users List (SMEMBERS)
38. Random Winner Selection (SPOP)
39. Feature Rollout Group Tracking
40. A/B Testing Cohort Management

### SORTED SET / ZSET (10 Problems)
41. Leaderboard (ZADD)
42. Top N Scores (ZREVRANGE)
43. User Rank Lookup (ZRANK / ZREVRANK)
44. Inactive Player Removal (ZREM)
45. Multi-Leaderboard Merge (ZUNIONSTORE)
46. Leaderboard Intersection (ZINTERSTORE)
47. Score Range User Count (ZCOUNT)
48. Time-Based Event Queue
49. Trending Topics Tracker
50. Delayed Job Scheduler (score = timestamp)

### PUB/SUB & STREAMS (7 Problems)
51. Real-Time Notification Broadcast
52. Chat Messaging for Multiple Subscribers
53. News Feed Real-Time Updates
54. Event-Driven Architecture with Streams
55. Multi-Consumer Event Queue with Streams
56. Reliable Delivery with Consumer Groups
57. Undo Logs for Collaborative Editing

### DISTRIBUTED & ATOMIC (8 Problems)
58. Distributed Lock (SET NX PX)
59. Distributed Semaphore (Sorted Set)
60. Cache Stampede Prevention (Lock + TTL)
61. Multi-Key Atomic Operation (Lua)
62. Idempotent Payment Processing
63. Concurrent Worker Tracking (ZSet Semaphore)
64. Leader Election in Distributed System
65. Atomic Wallet Deduction (Lua Script)

### GEO & HYPERLOGLOG (5 Problems)
66. Driver Location Tracking (GEOADD)
67. Nearby Driver Search (GEORADIUS)
68. Distance Calculation (GEODIST)
69. Unique Visitor Counting (HyperLogLog)
70. Cross-Server Visitor Merge (PFMERGE)

### ADVANCED / SYSTEM DESIGN (5 Problems)
71. Flash Sale System (Atomic Stock Decrement)
72. Social Feed Ranking (ZSET + HASH)
73. Real-Time Analytics Dashboard (Hash + INCRBY)
74. Reliable Delayed Task Scheduler (ZSET + Lua)
75. Real-Time Geospatial Matchmaking (GEO + ZSet)

## 🚀 How to Use

### For Interview Prep
1. Read through each problem's **scenario** and **why Redis** section
2. Understand the **Redis commands** and their **time complexities**
3. Practice writing the **Lua scripts** for atomic operations
4. Draw the **architecture diagrams** mentally for system design rounds

### For System Design Interviews
- Know when to use Redis vs. a traditional DB
- Understand Redis persistence (RDB vs AOF)
- Be ready to discuss Redis Cluster for horizontal scaling
- Know the trade-offs: memory cost, single-threaded model, eviction policies

### For Coding Interviews
- Be able to write Redis commands from memory
- Know Lua scripting basics for atomic operations
- Understand pub/sub patterns and their limitations
- Know time complexity of every command you use

## 🎓 Key Redis Concepts for Interviews

### Data Structure Time Complexities
| Structure | Add | Get | Delete | Count |
|-----------|-----|-----|--------|-------|
| String | O(1) | O(1) | O(1) | — |
| Hash | O(1) | O(1) | O(1) | O(1) |
| List | O(1) push | O(N) index | O(N) | O(1) |
| Set | O(1) | O(1) member | O(1) | O(1) |
| Sorted Set | O(log N) | O(log N) | O(log N) | O(log N) |
| HyperLogLog | O(1) | — | — | O(1) |
| Geo | O(log N) | O(N+log N) | O(log N) | — |

### Redis Single-Threaded Model
- Redis processes commands sequentially on a single thread
- This means **individual commands are always atomic**
- For **multi-step atomicity**, use Lua scripts or MULTI/EXEC
- This is why INCR, SETNX, etc. are inherently safe

### Persistence Options
| Mode | Description | Trade-off |
|------|-------------|-----------|
| RDB | Point-in-time snapshots | Fast recovery, some data loss |
| AOF | Append every write | Durable, slower recovery |
| RDB+AOF | Both together | Best durability, more disk |

## 💡 Interview Tips

1. **Always state the time complexity** of your Redis commands
2. **Explain why Redis** over alternatives (DB, in-memory cache, etc.)
3. **Discuss failure scenarios**: What if Redis goes down? (Persistence, replication)
4. **Mention scaling strategy**: Redis Cluster, read replicas, sharding
5. **Know the limits**: Max key size (512MB), max members, memory constraints
6. **Lua scripts are your secret weapon** for atomic multi-step operations

---

**Good luck with your interviews! 🚀**

Redis mastery separates good engineers from great ones in system design interviews.