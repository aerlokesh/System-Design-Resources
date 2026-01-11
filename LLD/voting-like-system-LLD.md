# Voting / Like System - Low-Level Design

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [API Design](#api-design)
5. [Concurrency Patterns](#concurrency-patterns)
6. [Implementation Approaches](#implementation-approaches)
7. [Idempotency & Data Consistency](#idempotency--data-consistency)
8. [Edge Cases](#edge-cases)
9. [Extensibility](#extensibility)

---

## Understanding the Problem

### What is a Voting/Like System?

A voting/like system allows users to express approval or disapproval for content (posts, comments, videos, etc.). The system must handle:
- **Double Voting Prevention**: Each user can vote only once per item
- **Thread Safety**: Multiple users voting simultaneously without data corruption
- **High Concurrency**: Thousands of votes per second
- **Consistency**: Vote counts must always be accurate

### Real-World Examples
- **Reddit**: Upvote/Downvote system with karma
- **YouTube**: Like/Dislike counts on videos
- **Stack Overflow**: Upvote/Downvote on questions and answers
- **Twitter/X**: Likes and retweets
- **Facebook**: Reactions (Like, Love, Haha, etc.)

### Core Challenge

The main challenge is handling **concurrent vote modifications** safely:

```
Thread 1: User A likes Post X (count: 100 → 101)
Thread 2: User B likes Post X (count: 100 → 101) ❌ Lost update!
Thread 3: User C unlikes Post X (count: 100 → 99)
```

Without proper concurrency control, we get:
- **Lost Updates**: Final count is 101 instead of 101
- **Race Conditions**: Two threads read same count, both increment, one overwrites
- **Double Voting**: Same user votes twice

---

## Requirements

### Functional Requirements

1. **Vote Operation**
   - User can like (upvote) an item
   - User can unlike (remove vote)
   - User can change vote type (upvote ↔ downvote)

2. **Prevent Double Voting**
   - Each user can vote only once per item
   - Attempting to vote twice should be idempotent (no-op or update)
   - System tracks who voted for what

3. **Vote Counting**
   - Get current vote count for an item
   - Get user's vote status for an item
   - Get list of users who voted

4. **Vote Types**
   - Support multiple vote types: UPVOTE, DOWNVOTE, NEUTRAL
   - Some systems may have reactions: LIKE, LOVE, HAHA, WOW, SAD, ANGRY

### Non-Functional Requirements

1. **Thread Safety**: Concurrent votes must not corrupt data
2. **High Throughput**: Handle 10,000+ votes per second
3. **Low Latency**: Vote operation completes in < 10ms
4. **Consistency**: Vote counts must always be accurate
5. **Idempotency**: Same vote request multiple times = same result
6. **Atomicity**: Vote and count update happen atomically

### Out of Scope

- Vote history/audit trail
- Vote aggregation analytics
- Fraud detection/bot prevention
- Real-time vote updates (websockets)
- Distributed system (multi-server coordination)

---

## Core Entities and Relationships

### 1. **VoteManager** (Main Orchestrator)
Coordinates voting operations and maintains consistency.

**Responsibilities:**
- Process vote requests
- Prevent double voting
- Update vote counts atomically
- Handle concurrent requests

### 2. **VoteRecord**
Tracks individual user votes.

**Attributes:**
- `userId`: Who voted
- `itemId`: What was voted on
- `voteType`: UPVOTE, DOWNVOTE, or NEUTRAL
- `timestamp`: When vote occurred

### 3. **VoteCount**
Aggregated vote statistics per item.

**Attributes:**
- `itemId`: Item identifier
- `upvotes`: Number of upvotes
- `downvotes`: Number of downvotes
- `totalVotes`: Total votes (upvotes + downvotes)
- `score`: Net score (upvotes - downvotes)

### 4. **VoteType** (Enum)
Vote classification.

**Values:**
- `UPVOTE`: Positive vote (+1)
- `DOWNVOTE`: Negative vote (-1)
- `NEUTRAL`: No vote (removed)

---

## API Design

### Core Interface

```java
public interface VotingSystem {
    /**
     * Cast a vote (idempotent)
     * @param userId user voting
     * @param itemId item being voted on
     * @param voteType type of vote
     * @return result indicating success/change
     */
    VoteResult vote(String userId, String itemId, VoteType voteType);
    
    /**
     * Remove vote
     * @param userId user removing vote
     * @param itemId item vote is for
     * @return true if vote was removed
     */
    boolean removeVote(String userId, String itemId);
    
    /**
     * Get vote count for item
     * @param itemId item to get count for
     * @return vote statistics
     */
    VoteCount getVoteCount(String itemId);
    
    /**
     * Get user's vote for item
     * @param userId user to check
     * @param itemId item to check
     * @return user's current vote or null
     */
    VoteType getUserVote(String userId, String itemId);
}
```

### Usage Examples

```java
VotingSystem voting = new ConcurrentVotingSystem();

// User A upvotes Post 1
VoteResult result = voting.vote("userA", "post1", VoteType.UPVOTE);
// result.isNewVote() = true, result.previousVote() = null

// User A tries to upvote again (idempotent)
result = voting.vote("userA", "post1", VoteType.UPVOTE);
// result.isNewVote() = false, result.previousVote() = UPVOTE

// User A changes to downvote
result = voting.vote("userA", "post1", VoteType.DOWNVOTE);
// result.isNewVote() = false, result.previousVote() = UPVOTE

// Get vote count
VoteCount count = voting.getVoteCount("post1");
// count.getUpvotes() = 0, count.getDownvotes() = 1, count.getScore() = -1
```

---

## Concurrency Patterns

### Challenge: Race Conditions

**Problem Scenario:**
```java
// Thread 1 and Thread 2 both try to increment count
int count = voteCounts.get("post1");  // Both read 100
voteCounts.put("post1", count + 1);   // Both write 101 ❌
// Expected: 102, Actual: 101 (lost update!)
```

### Solution 1: ConcurrentHashMap + AtomicInteger

**Approach:** Use thread-safe collections with atomic operations

```java
ConcurrentHashMap<String, AtomicInteger> upvotes = new ConcurrentHashMap<>();

// Atomic increment - no race condition!
upvotes.computeIfAbsent("post1", k -> new AtomicInteger(0))
       .incrementAndGet();
```

**Pros:**
- ✅ Simple and efficient
- ✅ No locks needed
- ✅ O(1) operations

**Cons:**
- ❌ Requires separate tracking for user votes
- ❌ Multiple data structures to keep in sync

### Solution 2: Compare-And-Swap (CAS)

**Approach:** Optimistic locking with retry

```java
public boolean incrementVoteCount(String itemId) {
    AtomicInteger counter = voteCounts.get(itemId);
    int current, updated;
    
    do {
        current = counter.get();           // Read current value
        updated = current + 1;             // Calculate new value
    } while (!counter.compareAndSet(current, updated)); // Retry if changed
    
    return true;
}
```

**Pros:**
- ✅ Lock-free
- ✅ High performance under low contention
- ✅ No deadlocks

**Cons:**
- ❌ May retry multiple times under high contention
- ❌ More complex logic

### Solution 3: Synchronized Block (Simple but Slower)

**Approach:** Use mutex for exclusive access

```java
private final Object lock = new Object();
private Map<String, Integer> voteCounts = new HashMap<>();

public void incrementVote(String itemId) {
    synchronized(lock) {
        voteCounts.put(itemId, voteCounts.getOrDefault(itemId, 0) + 1);
    }
}
```

**Pros:**
- ✅ Simple to implement
- ✅ Guarantees consistency

**Cons:**
- ❌ Poor scalability
- ❌ Blocks all threads
- ❌ Single point of contention

### Solution 4: StampedLock (Optimized Read-Write)

**Approach:** Use StampedLock for optimistic reads

```java
private StampedLock lock = new StampedLock();
private Map<String, VoteCount> voteCounts = new HashMap<>();

public VoteCount getVoteCount(String itemId) {
    long stamp = lock.tryOptimisticRead();  // Try optimistic read
    VoteCount count = voteCounts.get(itemId);
    
    if (!lock.validate(stamp)) {            // Check if data changed
        stamp = lock.readLock();            // Fall back to read lock
        try {
            count = voteCounts.get(itemId);
        } finally {
            lock.unlockRead(stamp);
        }
    }
    return count;
}
```

**Pros:**
- ✅ Better read performance than ReentrantReadWriteLock
- ✅ Optimistic reads don't block

**Cons:**
- ❌ More complex
- ❌ Can lead to starvation

---

## Implementation Approaches

### Approach 1: ConcurrentHashMap (Recommended)

**Data Structure:**
```java
// Track user votes: (userId, itemId) → voteType
ConcurrentHashMap<String, VoteType> userVotes;

// Track vote counts per item
ConcurrentHashMap<String, VoteCount> itemVotes;
```

**Key Operations:**
```java
// Vote with double-vote prevention
String key = userId + ":" + itemId;
VoteType previous = userVotes.put(key, voteType);

// Update counts atomically
itemVotes.compute(itemId, (k, count) -> {
    if (count == null) count = new VoteCount();
    count.update(previous, voteType);
    return count;
});
```

**Pros:**
- ✅ Built-in thread safety
- ✅ No explicit locking
- ✅ High performance
- ✅ Simple to understand

### Approach 2: Two-Phase Locking

**Data Structure:**
```java
// Use separate locks per item
ConcurrentHashMap<String, ReentrantLock> itemLocks;
HashMap<String, VoteType> userVotes;
HashMap<String, VoteCount> itemVotes;
```

**Pros:**
- ✅ Fine-grained locking
- ✅ Better than single global lock

**Cons:**
- ❌ More complex
- ❌ Potential deadlocks
- ❌ Lock overhead

### Approach 3: Event Sourcing

**Approach:** Store all vote events, compute counts on demand

**Data Structure:**
```java
// Immutable vote events
List<VoteEvent> events = new CopyOnWriteArrayList<>();

// Compute counts from events
public VoteCount getVoteCount(String itemId) {
    return events.stream()
        .filter(e -> e.itemId.equals(itemId))
        .collect(VoteCount.collector());
}
```

**Pros:**
- ✅ Complete audit trail
- ✅ Can replay/recompute
- ✅ No lost updates

**Cons:**
- ❌ Slower reads (must compute)
- ❌ Memory intensive
- ❌ Complexity

---

## Idempotency & Data Consistency

### Idempotency Requirements

**Definition:** Same operation multiple times = same result

**Example:**
```java
vote("userA", "post1", UPVOTE);  // count = 1
vote("userA", "post1", UPVOTE);  // count = 1 (idempotent!)
vote("userA", "post1", UPVOTE);  // count = 1 (idempotent!)
```

### Implementation Strategy

```java
public VoteResult vote(String userId, String itemId, VoteType newVote) {
    String key = userId + ":" + itemId;
    
    // Atomically check and update
    VoteType previousVote = userVotes.put(key, newVote);
    
    // Idempotent: same vote = no-op
    if (previousVote == newVote) {
        return VoteResult.noChange(previousVote);
    }
    
    // Update counts atomically
    updateVoteCounts(itemId, previousVote, newVote);
    
    return VoteResult.updated(previousVote, newVote);
}
```

### Data Consistency

**Invariants to Maintain:**
1. User can have at most one vote per item
2. Vote count = sum of individual votes
3. Score = upvotes - downvotes
4. No negative vote counts

**Consistency Checks:**
```java
// Verify consistency
public boolean isConsistent(String itemId) {
    VoteCount count = getVoteCount(itemId);
    
    // Count actual votes
    int actualUpvotes = (int) userVotes.entrySet().stream()
        .filter(e -> e.getKey().endsWith(":" + itemId))
        .filter(e -> e.getValue() == VoteType.UPVOTE)
        .count();
    
    return count.getUpvotes() == actualUpvotes;
}
```

---

## Edge Cases

### 1. Concurrent Same-User Votes
**Scenario:** User clicks upvote button rapidly multiple times

**Solution:** Last write wins with idempotency
```java
// All requests process, but only one takes effect
userVotes.put(key, UPVOTE);  // Thread-safe put
```

### 2. Vote Flip Race Condition
**Scenario:** User rapidly toggles between upvote/downvote

**Solution:** Process all events, maintain consistency
```java
// Each flip updates counts correctly
if (prev == UPVOTE && new == DOWNVOTE) {
    upvotes.decrementAndGet();
    downvotes.incrementAndGet();
}
```

### 3. Item Doesn't Exist
**Scenario:** Voting on non-existent item

**Solution:** Auto-create vote count on first vote
```java
itemVotes.computeIfAbsent(itemId, k -> new VoteCount());
```

### 4. Remove Non-Existent Vote
**Scenario:** User removes vote they never cast

**Solution:** Idempotent removal (no error)
```java
VoteType prev = userVotes.remove(key);
if (prev == null) {
    return false;  // Already removed
}
```

### 5. Integer Overflow
**Scenario:** Vote count exceeds Integer.MAX_VALUE

**Solution:** Use AtomicLong instead of AtomicInteger
```java
ConcurrentHashMap<String, AtomicLong> upvotes;
```

### 6. Memory Leak from Dead Items
**Scenario:** Millions of old items still tracked

**Solution:** 
- Implement cleanup policy
- Use WeakReference for old items
- Archive to persistent storage

---

## Extensibility

### Future Extensions

#### 1. **Multiple Reaction Types**
Extend beyond upvote/downvote to reactions like Facebook:
```java
enum ReactionType {
    LIKE, LOVE, HAHA, WOW, SAD, ANGRY
}

ConcurrentHashMap<String, Map<ReactionType, Integer>> reactions;
```

#### 2. **Vote Weight/Power**
Different users have different vote weights:
```java
class WeightedVote {
    VoteType type;
    double weight;  // VIP users = 2.0, normal = 1.0
}
```

#### 3. **Undo/Redo Stack**
Allow users to undo recent votes:
```java
class VoteHistory {
    Deque<VoteEvent> userHistory;
    
    void undo(String userId) {
        VoteEvent last = userHistory.pollLast();
        revertVote(last);
    }
}
```

#### 4. **Vote Decay**
Older votes count less over time:
```java
double getDecayedScore(String itemId) {
    return votes.stream()
        .map(v -> v.weight * decayFunction(v.age))
        .sum();
}
```

#### 5. **Bulk Operations**
Vote on multiple items at once:
```java
Map<String, VoteResult> bulkVote(
    String userId, 
    Map<String, VoteType> votes
);
```

#### 6. **Distributed System**
Scale across multiple servers:
- Use Redis for shared state
- Implement eventual consistency
- Use distributed locks (Redlock)

---

## Design Patterns Used

1. **Strategy Pattern**: Different vote counting strategies
2. **Command Pattern**: Vote operations as commands
3. **Observer Pattern**: Notify listeners of vote changes
4. **Factory Pattern**: Create vote managers for different use cases
5. **Immutable Object**: VoteRecord, VoteCount (thread-safe)

---

## Performance Characteristics

| Metric | ConcurrentHashMap | Synchronized | StampedLock |
|--------|------------------|--------------|-------------|
| Vote Operation | O(1) | O(1) | O(1) |
| Get Count | O(1) | O(1) | O(1) |
| Throughput | Very High | Low | High |
| Contention | Low | High | Medium |
| Complexity | Low | Low | Medium |

**Recommended:** ConcurrentHashMap for best balance

---

## Interview Tips

1. **Start with Requirements**: Clarify vote types, scale, and consistency needs
2. **Discuss Concurrency**: Explain race conditions and prevention strategies
3. **Show CAS Knowledge**: Demonstrate understanding of atomic operations
4. **Explain Idempotency**: Why same vote twice should be no-op
5. **Consider Scale**: How system handles millions of concurrent votes
6. **Edge Cases**: Double voting, rapid toggles, non-existent items
7. **Trade-offs**: Consistency vs. performance, memory vs. computation

### Common Interview Questions

Q: How do you prevent double voting?
A: Track user votes in ConcurrentHashMap with userId+itemId as key

Q: How do you handle race conditions?
A: Use ConcurrentHashMap with atomic operations (compute, computeIfAbsent)

Q: What if same user votes from multiple devices simultaneously?
A: ConcurrentHashMap handles this - last write wins, counts stay consistent

Q: How do you ensure vote count accuracy?
A: Use atomic updates in compute() method - read and write are atomic

Q: What's the difference between synchronized and ConcurrentHashMap?
A: Synchronized blocks all threads, ConcurrentHashMap allows concurrent reads/writes

---

## References

- Reddit's Voting System: https://www.reddit.com/r/changelog/comments/7spgg0/
- YouTube Like System Architecture
- Stack Overflow Voting Implementation
- Java ConcurrentHashMap Documentation
- Compare-And-Swap (CAS) Algorithm
- Optimistic Concurrency Control
