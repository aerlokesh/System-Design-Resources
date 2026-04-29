# Leaderboard for Fantasy Teams - HELLO Interview Framework

> **Companies**: Uber, Microsoft, WayFair, Dream11  
> **Difficulty**: Medium  
> **Pattern**: Observer + TreeMap for O(log N) ranked ordering  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Leaderboard?

A real-time ranked list of players/teams sorted by score. Fantasy sports platforms (Dream11, FanDuel, ESPN Fantasy) process millions of score updates during a game. The leaderboard must efficiently support: **update score**, **get top-K**, **get rank of player**, and **notify on rank changes**.

**Real-world**: Dream11 has 200M+ users. During IPL, millions of score updates per minute. The leaderboard must be responsive (< 100ms) even under heavy load.

### For L63 Microsoft

1. **TreeMap\<Integer, Set\<String\>\>** (descending): O(log N) update, O(K) top-K
2. **ReadWriteLock**: Multiple readers (viewing leaderboard), exclusive writer (score updates)
3. **Observer Pattern**: Notify on new leader, rank change milestones
4. **Tied scores**: Multiple players with same score share a rank bucket

---

## 1️⃣ Requirements

### Functional (P0)
1. **addPlayer(id, name)**: Register player with initial score 0
2. **updateScore(playerId, newScore)**: Set absolute score
3. **addPoints(playerId, points)**: Incremental score addition
4. **getTopK(k)**: Get top K players ranked by score (descending)
5. **getRank(playerId)**: Get a player's current rank position
6. **getScore(playerId)**: Get a player's current score
7. **Observer notifications**: New leader, score updates

### Non-Functional
- O(log N) score updates (TreeMap insert/remove)
- O(K) top-K retrieval (iterate sorted map head)
- Thread-safe: concurrent reads with ReadWriteLock
- Handle 100K+ players efficiently

---

## 2️⃣ Core Entities

```
┌───────────────────────────────────────────────────────────┐
│                      Leaderboard                          │
│  - players: HashMap<String, Player>  ← O(1) player lookup │
│  - scoreBoard: TreeMap<Integer↓, LinkedHashSet<String>>   │
│  - rwLock: ReentrantReadWriteLock                         │
│  - observers: List<LeaderboardObserver>                    │
│  - currentLeaderId: String                                │
│  - updateScore(playerId, newScore)                        │
│  - addPoints(playerId, points)                            │
│  - getTopK(k) → List<Player>                              │
│  - getRank(playerId) → int                                │
└───────────────────────────────────────────────────────────┘

LeaderboardPlayer — id, name, score, rank
LeaderboardObserver (interface) — onScoreUpdate, onNewLeader, onTopKChanged
ConsoleLeaderboardObserver — logs rank changes to console
```

---

## 3️⃣ API Design

```java
class Leaderboard {
    void addPlayer(String id, String name);
    void updateScore(String playerId, int newScore);   // absolute
    void addPoints(String playerId, int points);       // incremental
    List<LeaderboardPlayer> getTopK(int k);
    int getRank(String playerId);
    int getScore(String playerId);
    void addObserver(LeaderboardObserver observer);
}
```

---

## 4️⃣ Data Flow

### Scenario: updateScore("t4", 500) — Knight Riders Takes Lead

```
updateScore("t4", 500)
  │
  ├─ rwLock.writeLock().lock()
  │
  ├─ Step 1: Remove from OLD score bucket
  │   ├─ oldScore = player.getScore() → 220
  │   ├─ scoreBoard.get(220).remove("t4")
  │   └─ If bucket empty → scoreBoard.remove(220)
  │
  ├─ Step 2: Add to NEW score bucket
  │   ├─ player.setScore(500)
  │   └─ scoreBoard.computeIfAbsent(500, LinkedHashSet::new).add("t4")
  │
  ├─ Step 3: Check new leader
  │   ├─ topId = scoreBoard.firstEntry().getValue().iterator().next()
  │   ├─ topId = "t4", currentLeaderId = "t1"
  │   ├─ "t4" != "t1" → NEW LEADER!
  │   └─ Notify: onNewLeader(Knight Riders, 500 pts)
  │
  └─ rwLock.writeLock().unlock()
```

---

## 5️⃣ Design + Implementation

### Why TreeMap\<Integer, Set\<String\>\> Descending?

```java
TreeMap<Integer, LinkedHashSet<String>> scoreBoard = new TreeMap<>(Comparator.reverseOrder());
```

| Structure | updateScore | getTopK | getRank |
|-----------|------------|---------|---------|
| Sorted Array | O(N) shift | O(K) | O(log N) binary |
| **TreeMap** | **O(log N)** | **O(K)** | O(R) iterate |
| PriorityQueue | O(N) remove+add | O(K log N) | O(N) |
| Redis ZADD | O(log N) | O(K + log N) | **O(log N)** |

TreeMap wins for in-memory with **frequent score updates + top-K**. Redis wins for distributed.

### Score Update (O(log N))

```java
void updateScore(String playerId, int newScore) {
    rwLock.writeLock().lock();
    try {
        LeaderboardPlayer player = players.get(playerId);
        int oldScore = player.getScore();
        if (oldScore == newScore) return;
        
        // Remove from old bucket
        LinkedHashSet<String> oldBucket = scoreBoard.get(oldScore);
        if (oldBucket != null) {
            oldBucket.remove(playerId);
            if (oldBucket.isEmpty()) scoreBoard.remove(oldScore);
        }
        
        // Add to new bucket
        player.setScore(newScore);
        scoreBoard.computeIfAbsent(newScore, k -> new LinkedHashSet<>()).add(playerId);
        
        // Check new leader
        String topId = scoreBoard.firstEntry().getValue().iterator().next();
        if (!topId.equals(currentLeaderId)) {
            currentLeaderId = topId;
            for (LeaderboardObserver o : observers) o.onNewLeader(players.get(topId));
        }
    } finally { rwLock.writeLock().unlock(); }
}
```

### Top-K (O(K) — TreeMap is pre-sorted!)

```java
List<LeaderboardPlayer> getTopK(int k) {
    rwLock.readLock().lock();
    try {
        List<LeaderboardPlayer> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Integer, LinkedHashSet<String>> entry : scoreBoard.entrySet()) {
            for (String pid : entry.getValue()) {
                LeaderboardPlayer p = players.get(pid);
                p.setRank(rank);
                result.add(p);
                if (result.size() >= k) return result;
                rank++;
            }
        }
        return result;
    } finally { rwLock.readLock().unlock(); }
}
```

### ReadWriteLock: Why Not synchronized?

```java
private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
```

| Approach | Read concurrency | Write concurrency |
|----------|-----------------|------------------|
| `synchronized` | ❌ One at a time | ❌ One at a time |
| **ReadWriteLock** | ✅ Multiple simultaneous | ❌ Exclusive |

Leaderboard is **read-heavy** (millions viewing) with occasional writes (score changes). ReadWriteLock gives **10× throughput** for this access pattern.

### Tied Scores

```
Score 230: Mumbai Indians, Royal Challengers
// Both in same LinkedHashSet at key 230
// LinkedHashSet preserves insertion order → first-added ranks higher

getTopK(3):
  Rank 1: Knight Riders (500)
  Rank 2: Mumbai Indians (230)     ← both at 230
  Rank 3: Royal Challengers (230)  ← FIFO tiebreak
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Why LinkedHashSet (Not HashSet)?

**HashSet**: No ordering guarantee for ties — rank order changes randomly between calls.
**LinkedHashSet**: Insertion order preserved — first player to achieve score ranks higher. Consistent, predictable tiebreaking.

### Deep Dive 2: Production — Redis Sorted Set

```
ZADD leaderboard 500 "Knight Riders"    → O(log N)
ZADD leaderboard 230 "Mumbai Indians"   → O(log N)
ZREVRANGE leaderboard 0 9              → top 10, O(K + log N)
ZREVRANK leaderboard "Mumbai Indians"   → rank, O(log N)
ZSCORE leaderboard "Knight Riders"      → score, O(1)
```

**Why Redis for production?** Distributed, persistent, O(log N) rank query (our TreeMap getRank is O(R)).

### Deep Dive 3: Pagination

```java
// Page 2, size 10: skip 10, return 10
List<LeaderboardPlayer> getPage(int page, int size) {
    int skip = (page - 1) * size;
    return getTopK(skip + size).subList(skip, skip + size);
}
```

### Deep Dive 4: Time-Scoped Leaderboards

```java
Map<String, Leaderboard> scopedBoards = new HashMap<>();
scopedBoards.put("weekly", new Leaderboard());   // resets every Monday
scopedBoards.put("monthly", new Leaderboard());  // resets 1st of month
scopedBoards.put("allTime", new Leaderboard());  // never resets

// Update ALL scoped leaderboards on every score change
for (Leaderboard board : scopedBoards.values()) {
    board.updateScore(playerId, newScore);
}
```

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Update to same score | No-op (early return if oldScore == newScore) |
| Player not found | Return -1 for rank/score |
| Two players tied | LinkedHashSet: insertion order preserved |
| Top-K where K > total players | Return all players |
| Negative score | Allowed (TreeMap handles negative integers) |
| Concurrent read + write | ReadWriteLock: reads wait during write |

### Deep Dive 6: Complexity

| Operation | Time | Space |
|-----------|------|-------|
| updateScore | O(log N) | O(1) |
| addPoints | O(log N) | O(1) |
| getTopK | O(K) | O(K) |
| getRank | O(R) where R = rank | O(1) |
| getScore | O(1) HashMap | O(1) |
| Total space | — | O(N players + S score buckets) |

---

## 📋 Interview Checklist (L63)

- [ ] **TreeMap descending**: O(log N) updates, O(K) top-K
- [ ] **Why not PriorityQueue**: Can't do efficient top-K or random access
- [ ] **ReadWriteLock**: Multiple concurrent readers, exclusive writer
- [ ] **Score bucket pattern**: Remove from old bucket, add to new
- [ ] **Tied scores**: LinkedHashSet for FIFO tiebreaking
- [ ] **Observer**: New leader notifications
- [ ] **Production**: Redis ZADD/ZREVRANGE for distributed
- [ ] **Pagination**: Skip + limit on top-K

See `LeaderboardSystem.java` for full implementation with 8-team fantasy league + concurrent updates demo.
