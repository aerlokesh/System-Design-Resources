# Leaderboard for Fantasy Teams - HELLO Interview Framework

> **Companies**: Uber, Microsoft, WayFair, Dream11  
> **Difficulty**: Medium  
> **Pattern**: Observer + TreeMap for O(log N) ranked ordering  
> **Time**: 35 minutes

## Understanding the Problem

Fantasy sports leaderboard: teams have scores that change in real-time. Need efficient **top-K**, **rank query**, and **new-leader notifications**. Dream11/FanDuel process millions of score updates per game.

### For L63 Microsoft
1. **TreeMap\<Score, Set\<Id\>\>** descending for O(log N) score updates + O(K) top-K
2. **ReadWriteLock** for concurrent reads (getTopK) and exclusive writes (updateScore)
3. **Observer** for rank change notifications (new leader, score milestones)

---

## Key Design

```
Leaderboard
  - players: HashMap<String, Player>           ← O(1) player lookup
  - scoreBoard: TreeMap<Integer↓, Set<String>> ← O(log N) ranked ordering
  - rwLock: ReadWriteLock                      ← concurrent reads, exclusive writes
  - observers: List<LeaderboardObserver>
  
  updateScore(playerId, newScore):
    1. writeLock.lock()
    2. Remove from old score bucket
    3. Add to new score bucket
    4. Check for new leader → notify observers
    5. writeLock.unlock()
  
  getTopK(k): 
    1. readLock.lock()
    2. Iterate TreeMap head (descending) for K entries
    3. readLock.unlock()
    → O(K) since TreeMap is pre-sorted!
  
  getRank(playerId):
    1. readLock.lock()
    2. Iterate TreeMap until player found → rank = position
    3. readLock.unlock()
```

### Why TreeMap Over Other Structures?

| Structure | updateScore | getTopK | getRank |
|-----------|------------|---------|---------|
| Sorted Array | O(N) shift | O(K) | O(log N) |
| **TreeMap** | **O(log N)** | **O(K)** | O(R) iterate |
| PriorityQueue | O(N) remove+add | O(K log N) | O(N) |
| Redis Sorted Set | O(log N) | O(K + log N) | O(log N) |

TreeMap wins for in-memory with **frequent score updates** + top-K queries.

### ReadWriteLock vs synchronized

| Approach | Read concurrency | Write concurrency |
|----------|-----------------|------------------|
| synchronized | ❌ One at a time | ❌ One at a time |
| **ReadWriteLock** | ✅ Multiple simultaneous | ❌ Exclusive |

Leaderboard is **read-heavy** (users viewing) with occasional writes (score changes). ReadWriteLock gives **10× better throughput** than synchronized for this pattern.

### Score Update with Observer

```java
void updateScore(String playerId, int newScore) {
    rwLock.writeLock().lock();
    try {
        // Remove from old bucket, add to new bucket
        removeFromScoreBoard(playerId, oldScore);
        player.setScore(newScore);
        addToScoreBoard(playerId, newScore);
        
        // Check new leader → notify
        String topId = scoreBoard.firstEntry().getValue().iterator().next();
        if (!topId.equals(currentLeaderId)) {
            for (Observer o : observers) o.onNewLeader(players.get(topId));
            currentLeaderId = topId;
        }
    } finally { rwLock.writeLock().unlock(); }
}
```

### Tied Scores

TreeMap value = `LinkedHashSet<String>`. Multiple players with same score share a bucket. LinkedHashSet preserves insertion order for FIFO tiebreaking.

### Deep Dives
- **Production**: Redis ZADD/ZRANGE for distributed leaderboard, O(log N) all ops
- **Pagination**: getTopK(page=2, size=10) → skip first 10, return next 10
- **Time-scoped**: Weekly/monthly leaderboard with separate TreeMaps per period

See `LeaderboardSystem.java` for full implementation with 8-team fantasy league + concurrent updates demo.
