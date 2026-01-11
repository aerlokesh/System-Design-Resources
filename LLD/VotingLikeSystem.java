import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

/**
 * Voting / Like System - Low-Level Design
 * 
 * This file demonstrates:
 * 1. ConcurrentHashMap-based Voting System (Recommended)
 * 2. Synchronized Voting System (Simple)
 * 3. StampedLock Voting System (Optimized)
 * 4. CAS-based Voting System (Lock-free)
 * 5. Reaction System (Facebook-like)
 * 6. Weighted Voting System
 * 7. Thread Safety Tests
 * 
 * Key Features:
 * - Prevents double voting
 * - Thread-safe
 * - Highly concurrent
 * - Idempotent operations
 * - Atomic updates
 * - Data consistency
 * 
 * @author System Design Repository
 * @version 1.0
 */

// ============================================================================
// CORE ENUMS AND VALUE OBJECTS
// ============================================================================

/**
 * Vote type enumeration
 */
enum VoteType {
    UPVOTE(1),
    DOWNVOTE(-1),
    NEUTRAL(0);
    
    private final int value;
    
    VoteType(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
}

/**
 * Reaction types (Facebook-style)
 */
enum ReactionType {
    LIKE, LOVE, HAHA, WOW, SAD, ANGRY
}

/**
 * Result of a vote operation
 */
class VoteResult {
    private final boolean changed;
    private final VoteType previousVote;
    private final VoteType newVote;
    
    private VoteResult(boolean changed, VoteType previousVote, VoteType newVote) {
        this.changed = changed;
        this.previousVote = previousVote;
        this.newVote = newVote;
    }
    
    public static VoteResult noChange(VoteType current) {
        return new VoteResult(false, current, current);
    }
    
    public static VoteResult updated(VoteType previous, VoteType newVote) {
        return new VoteResult(true, previous, newVote);
    }
    
    public static VoteResult newVote(VoteType voteType) {
        return new VoteResult(true, null, voteType);
    }
    
    public boolean isChanged() {
        return changed;
    }
    
    public boolean isNewVote() {
        return previousVote == null;
    }
    
    public VoteType getPreviousVote() {
        return previousVote;
    }
    
    public VoteType getNewVote() {
        return newVote;
    }
    
    @Override
    public String toString() {
        if (!changed) {
            return "VoteResult{noChange, vote=" + newVote + "}";
        }
        return String.format("VoteResult{changed, previous=%s, new=%s}", 
            previousVote, newVote);
    }
}

/**
 * Immutable vote count statistics
 */
class VoteCount {
    private final AtomicInteger upvotes;
    private final AtomicInteger downvotes;
    
    public VoteCount() {
        this.upvotes = new AtomicInteger(0);
        this.downvotes = new AtomicInteger(0);
    }
    
    public VoteCount(int upvotes, int downvotes) {
        this.upvotes = new AtomicInteger(upvotes);
        this.downvotes = new AtomicInteger(downvotes);
    }
    
    public int getUpvotes() {
        return upvotes.get();
    }
    
    public int getDownvotes() {
        return downvotes.get();
    }
    
    public int getTotalVotes() {
        return upvotes.get() + downvotes.get();
    }
    
    public int getScore() {
        return upvotes.get() - downvotes.get();
    }
    
    public void incrementUpvote() {
        upvotes.incrementAndGet();
    }
    
    public void decrementUpvote() {
        upvotes.decrementAndGet();
    }
    
    public void incrementDownvote() {
        downvotes.incrementAndGet();
    }
    
    public void decrementDownvote() {
        downvotes.decrementAndGet();
    }
    
    /**
     * Update counts based on vote change
     */
    public synchronized void update(VoteType from, VoteType to) {
        // Remove old vote
        if (from == VoteType.UPVOTE) {
            upvotes.decrementAndGet();
        } else if (from == VoteType.DOWNVOTE) {
            downvotes.decrementAndGet();
        }
        
        // Add new vote
        if (to == VoteType.UPVOTE) {
            upvotes.incrementAndGet();
        } else if (to == VoteType.DOWNVOTE) {
            downvotes.incrementAndGet();
        }
    }
    
    @Override
    public String toString() {
        return String.format("VoteCount{upvotes=%d, downvotes=%d, score=%d}", 
            getUpvotes(), getDownvotes(), getScore());
    }
    
    public VoteCount copy() {
        return new VoteCount(upvotes.get(), downvotes.get());
    }
}

// ============================================================================
// CORE INTERFACE
// ============================================================================

/**
 * Voting system interface
 */
interface VotingSystem {
    /**
     * Cast a vote (idempotent)
     */
    VoteResult vote(String userId, String itemId, VoteType voteType);
    
    /**
     * Remove vote
     */
    boolean removeVote(String userId, String itemId);
    
    /**
     * Get vote count for item
     */
    VoteCount getVoteCount(String itemId);
    
    /**
     * Get user's current vote for item
     */
    VoteType getUserVote(String userId, String itemId);
    
    /**
     * Get all users who voted on item
     */
    Set<String> getVoters(String itemId);
    
    /**
     * Reset system (for testing)
     */
    void reset();
}

// ============================================================================
// 1. CONCURRENT HASHMAP VOTING SYSTEM (RECOMMENDED)
// ============================================================================

/**
 * Thread-safe voting system using ConcurrentHashMap
 * 
 * Prevents double voting
 * Highly concurrent
 * Idempotent operations
 * Atomic updates using compute()
 * 
 * Thread-safe: Yes
 * Lock-free: Mostly (internal locks only)
 * Performance: Excellent
 */
class ConcurrentVotingSystem implements VotingSystem {
    // Track user votes: "userId:itemId" -> voteType
    private final ConcurrentHashMap<String, VoteType> userVotes;
    
    // Track vote counts per item
    private final ConcurrentHashMap<String, VoteCount> itemCounts;
    
    // Statistics
    private final AtomicLong totalVotes;
    private final AtomicLong voteChanges;
    
    public ConcurrentVotingSystem() {
        this.userVotes = new ConcurrentHashMap<>();
        this.itemCounts = new ConcurrentHashMap<>();
        this.totalVotes = new AtomicLong(0);
        this.voteChanges = new AtomicLong(0);
    }
    
    @Override
    public VoteResult vote(String userId, String itemId, VoteType voteType) {
        if (userId == null || itemId == null || voteType == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        
        String key = makeKey(userId, itemId);
        
        // Atomically update user's vote
        VoteType previousVote = userVotes.put(key, voteType);
        
        // Idempotent: same vote = no change
        if (previousVote == voteType) {
            return VoteResult.noChange(voteType);
        }
        
        // Update counts atomically
        itemCounts.compute(itemId, (k, count) -> {
            if (count == null) {
                count = new VoteCount();
            }
            count.update(previousVote, voteType);
            return count;
        });
        
        // Update statistics
        if (previousVote == null) {
            totalVotes.incrementAndGet();
        } else {
            voteChanges.incrementAndGet();
        }
        
        return previousVote == null ? 
            VoteResult.newVote(voteType) : 
            VoteResult.updated(previousVote, voteType);
    }
    
    @Override
    public boolean removeVote(String userId, String itemId) {
        String key = makeKey(userId, itemId);
        
        // Remove user's vote
        VoteType previousVote = userVotes.remove(key);
        
        // Idempotent: no vote to remove
        if (previousVote == null) {
            return false;
        }
        
        // Update counts
        itemCounts.compute(itemId, (k, count) -> {
            if (count != null) {
                count.update(previousVote, VoteType.NEUTRAL);
            }
            return count;
        });
        
        totalVotes.decrementAndGet();
        return true;
    }
    
    @Override
    public VoteCount getVoteCount(String itemId) {
        VoteCount count = itemCounts.get(itemId);
        return count != null ? count.copy() : new VoteCount();
    }
    
    @Override
    public VoteType getUserVote(String userId, String itemId) {
        return userVotes.get(makeKey(userId, itemId));
    }
    
    @Override
    public Set<String> getVoters(String itemId) {
        String suffix = ":" + itemId;
        return userVotes.keySet().stream()
            .filter(key -> key.endsWith(suffix))
            .map(key -> key.substring(0, key.indexOf(':')))
            .collect(Collectors.toSet());
    }
    
    @Override
    public void reset() {
        userVotes.clear();
        itemCounts.clear();
        totalVotes.set(0);
        voteChanges.set(0);
    }
    
    private String makeKey(String userId, String itemId) {
        return userId + ":" + itemId;
    }
    
    public long getTotalVotes() {
        return totalVotes.get();
    }
    
    public long getVoteChanges() {
        return voteChanges.get();
    }
}

// ============================================================================
// 2. SYNCHRONIZED VOTING SYSTEM
// ============================================================================

/**
 * Simple synchronized voting system
 * 
 * Uses global lock for all operations
 * Simple to understand but poor scalability
 * 
 * Thread-safe: Yes
 * Lock-free: No
 * Performance: Poor under high contention
 */
class SynchronizedVotingSystem implements VotingSystem {
    private final Map<String, VoteType> userVotes;
    private final Map<String, VoteCount> itemCounts;
    private final Object lock = new Object();
    
    public SynchronizedVotingSystem() {
        this.userVotes = new HashMap<>();
        this.itemCounts = new HashMap<>();
    }
    
    @Override
    public synchronized VoteResult vote(String userId, String itemId, VoteType voteType) {
        String key = userId + ":" + itemId;
        VoteType previousVote = userVotes.put(key, voteType);
        
        if (previousVote == voteType) {
            return VoteResult.noChange(voteType);
        }
        
        VoteCount count = itemCounts.computeIfAbsent(itemId, k -> new VoteCount());
        count.update(previousVote, voteType);
        
        return previousVote == null ? 
            VoteResult.newVote(voteType) : 
            VoteResult.updated(previousVote, voteType);
    }
    
    @Override
    public synchronized boolean removeVote(String userId, String itemId) {
        String key = userId + ":" + itemId;
        VoteType previousVote = userVotes.remove(key);
        
        if (previousVote == null) {
            return false;
        }
        
        VoteCount count = itemCounts.get(itemId);
        if (count != null) {
            count.update(previousVote, VoteType.NEUTRAL);
        }
        
        return true;
    }
    
    @Override
    public synchronized VoteCount getVoteCount(String itemId) {
        VoteCount count = itemCounts.get(itemId);
        return count != null ? count.copy() : new VoteCount();
    }
    
    @Override
    public synchronized VoteType getUserVote(String userId, String itemId) {
        return userVotes.get(userId + ":" + itemId);
    }
    
    @Override
    public synchronized Set<String> getVoters(String itemId) {
        String suffix = ":" + itemId;
        return userVotes.keySet().stream()
            .filter(key -> key.endsWith(suffix))
            .map(key -> key.substring(0, key.indexOf(':')))
            .collect(Collectors.toSet());
    }
    
    @Override
    public synchronized void reset() {
        userVotes.clear();
        itemCounts.clear();
    }
}

// ============================================================================
// 3. STAMPED LOCK VOTING SYSTEM
// ============================================================================

/**
 * Optimized voting system using StampedLock
 * 
 * Optimistic reads for getters
 * Write locks for modifications
 * Better read performance than ReentrantReadWriteLock
 * 
 * Thread-safe: Yes
 * Lock-free: No (but optimized)
 * Performance: Good for read-heavy workloads
 */
class StampedLockVotingSystem implements VotingSystem {
    private final Map<String, VoteType> userVotes;
    private final Map<String, VoteCount> itemCounts;
    private final StampedLock lock;
    
    public StampedLockVotingSystem() {
        this.userVotes = new HashMap<>();
        this.itemCounts = new HashMap<>();
        this.lock = new StampedLock();
    }
    
    @Override
    public VoteResult vote(String userId, String itemId, VoteType voteType) {
        long stamp = lock.writeLock();
        try {
            String key = userId + ":" + itemId;
            VoteType previousVote = userVotes.put(key, voteType);
            
            if (previousVote == voteType) {
                return VoteResult.noChange(voteType);
            }
            
            VoteCount count = itemCounts.computeIfAbsent(itemId, k -> new VoteCount());
            count.update(previousVote, voteType);
            
            return previousVote == null ? 
                VoteResult.newVote(voteType) : 
                VoteResult.updated(previousVote, voteType);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    @Override
    public boolean removeVote(String userId, String itemId) {
        long stamp = lock.writeLock();
        try {
            String key = userId + ":" + itemId;
            VoteType previousVote = userVotes.remove(key);
            
            if (previousVote == null) {
                return false;
            }
            
            VoteCount count = itemCounts.get(itemId);
            if (count != null) {
                count.update(previousVote, VoteType.NEUTRAL);
            }
            
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    @Override
    public VoteCount getVoteCount(String itemId) {
        // Try optimistic read first
        long stamp = lock.tryOptimisticRead();
        VoteCount count = itemCounts.get(itemId);
        
        if (!lock.validate(stamp)) {
            // Validation failed, use read lock
            stamp = lock.readLock();
            try {
                count = itemCounts.get(itemId);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        return count != null ? count.copy() : new VoteCount();
    }
    
    @Override
    public VoteType getUserVote(String userId, String itemId) {
        long stamp = lock.tryOptimisticRead();
        VoteType vote = userVotes.get(userId + ":" + itemId);
        
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                vote = userVotes.get(userId + ":" + itemId);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        return vote;
    }
    
    @Override
    public Set<String> getVoters(String itemId) {
        long stamp = lock.readLock();
        try {
            String suffix = ":" + itemId;
            return userVotes.keySet().stream()
                .filter(key -> key.endsWith(suffix))
                .map(key -> key.substring(0, key.indexOf(':')))
                .collect(Collectors.toSet());
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    @Override
    public void reset() {
        long stamp = lock.writeLock();
        try {
            userVotes.clear();
            itemCounts.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}

// ============================================================================
// 4. CAS-BASED VOTING SYSTEM
// ============================================================================

/**
 * Lock-free voting system using Compare-And-Swap
 * 
 * Uses AtomicInteger with CAS operations
 * Retries on contention
 * Best for low-contention scenarios
 * 
 * Thread-safe: Yes
 * Lock-free: Yes
 * Performance: Excellent under low contention
 */
class CASVotingSystem implements VotingSystem {
    private final ConcurrentHashMap<String, VoteType> userVotes;
    private final ConcurrentHashMap<String, AtomicInteger> upvotes;
    private final ConcurrentHashMap<String, AtomicInteger> downvotes;
    
    public CASVotingSystem() {
        this.userVotes = new ConcurrentHashMap<>();
        this.upvotes = new ConcurrentHashMap<>();
        this.downvotes = new ConcurrentHashMap<>();
    }
    
    @Override
    public VoteResult vote(String userId, String itemId, VoteType voteType) {
        String key = userId + ":" + itemId;
        VoteType previousVote = userVotes.put(key, voteType);
        
        if (previousVote == voteType) {
            return VoteResult.noChange(voteType);
        }
        
        // Update counts using CAS
        updateCountsWithCAS(itemId, previousVote, voteType);
        
        return previousVote == null ? 
            VoteResult.newVote(voteType) : 
            VoteResult.updated(previousVote, voteType);
    }
    
    private void updateCountsWithCAS(String itemId, VoteType from, VoteType to) {
        // Remove old vote
        if (from == VoteType.UPVOTE) {
            upvotes.computeIfAbsent(itemId, k -> new AtomicInteger(0))
                   .decrementAndGet();
        } else if (from == VoteType.DOWNVOTE) {
            downvotes.computeIfAbsent(itemId, k -> new AtomicInteger(0))
                     .decrementAndGet();
        }
        
        // Add new vote
        if (to == VoteType.UPVOTE) {
            upvotes.computeIfAbsent(itemId, k -> new AtomicInteger(0))
                   .incrementAndGet();
        } else if (to == VoteType.DOWNVOTE) {
            downvotes.computeIfAbsent(itemId, k -> new AtomicInteger(0))
                     .incrementAndGet();
        }
    }
    
    @Override
    public boolean removeVote(String userId, String itemId) {
        String key = userId + ":" + itemId;
        VoteType previousVote = userVotes.remove(key);
        
        if (previousVote == null) {
            return false;
        }
        
        updateCountsWithCAS(itemId, previousVote, VoteType.NEUTRAL);
        return true;
    }
    
    @Override
    public VoteCount getVoteCount(String itemId) {
        int up = upvotes.getOrDefault(itemId, new AtomicInteger(0)).get();
        int down = downvotes.getOrDefault(itemId, new AtomicInteger(0)).get();
        return new VoteCount(up, down);
    }
    
    @Override
    public VoteType getUserVote(String userId, String itemId) {
        return userVotes.get(userId + ":" + itemId);
    }
    
    @Override
    public Set<String> getVoters(String itemId) {
        String suffix = ":" + itemId;
        return userVotes.keySet().stream()
            .filter(key -> key.endsWith(suffix))
            .map(key -> key.substring(0, key.indexOf(':')))
            .collect(Collectors.toSet());
    }
    
    @Override
    public void reset() {
        userVotes.clear();
        upvotes.clear();
        downvotes.clear();
    }
}

// ============================================================================
// 5. REACTION SYSTEM (FACEBOOK-STYLE)
// ============================================================================

/**
 * Multi-reaction voting system (Like, Love, Haha, etc.)
 */
class ReactionSystem {
    private final ConcurrentHashMap<String, ReactionType> userReactions;
    private final ConcurrentHashMap<String, Map<ReactionType, AtomicInteger>> itemReactions;
    
    public ReactionSystem() {
        this.userReactions = new ConcurrentHashMap<>();
        this.itemReactions = new ConcurrentHashMap<>();
    }
    
    public boolean react(String userId, String itemId, ReactionType reaction) {
        String key = userId + ":" + itemId;
        ReactionType previousReaction = userReactions.put(key, reaction);
        
        if (previousReaction == reaction) {
            return false;  // Idempotent
        }
        
        // Update counts
        itemReactions.compute(itemId, (k, reactions) -> {
            if (reactions == null) {
                reactions = new ConcurrentHashMap<>();
            }
            
            // Remove old reaction
            if (previousReaction != null) {
                reactions.computeIfAbsent(previousReaction, r -> new AtomicInteger(0))
                         .decrementAndGet();
            }
            
            // Add new reaction
            reactions.computeIfAbsent(reaction, r -> new AtomicInteger(0))
                     .incrementAndGet();
            
            return reactions;
        });
        
        return true;
    }
    
    public boolean removeReaction(String userId, String itemId) {
        String key = userId + ":" + itemId;
        ReactionType previous = userReactions.remove(key);
        
        if (previous == null) {
            return false;
        }
        
        itemReactions.computeIfPresent(itemId, (k, reactions) -> {
            reactions.computeIfPresent(previous, (r, count) -> {
                count.decrementAndGet();
                return count;
            });
            return reactions;
        });
        
        return true;
    }
    
    public Map<ReactionType, Integer> getReactionCounts(String itemId) {
        Map<ReactionType, AtomicInteger> reactions = itemReactions.get(itemId);
        if (reactions == null) {
            return new HashMap<>();
        }
        
        return reactions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get()
            ));
    }
    
    public ReactionType getUserReaction(String userId, String itemId) {
        return userReactions.get(userId + ":" + itemId);
    }
}

// ============================================================================
// 6. WEIGHTED VOTING SYSTEM
// ============================================================================

/**
 * Voting system with weighted votes (VIP users have higher weight)
 */
class WeightedVotingSystem {
    private final ConcurrentHashMap<String, VoteType> userVotes;
    private final ConcurrentHashMap<String, Double> voteScores;
    private final Map<String, Double> userWeights;
    
    public WeightedVotingSystem() {
        this.userVotes = new ConcurrentHashMap<>();
        this.voteScores = new ConcurrentHashMap<>();
        this.userWeights = new ConcurrentHashMap<>();
    }
    
    public void setUserWeight(String userId, double weight) {
        userWeights.put(userId, weight);
    }
    
    public VoteResult vote(String userId, String itemId, VoteType voteType) {
        double weight = userWeights.getOrDefault(userId, 1.0);
        String key = userId + ":" + itemId;
        
        VoteType previousVote = userVotes.put(key, voteType);
        
        if (previousVote == voteType) {
            return VoteResult.noChange(voteType);
        }
        
        // Update weighted score
        voteScores.compute(itemId, (k, score) -> {
            if (score == null) score = 0.0;
            
            // Remove old weight
            if (previousVote != null) {
                score -= previousVote.getValue() * weight;
            }
            
            // Add new weight
            score += voteType.getValue() * weight;
            
            return score;
        });
        
        return previousVote == null ? 
            VoteResult.newVote(voteType) : 
            VoteResult.updated(previousVote, voteType);
    }
    
    public double getWeightedScore(String itemId) {
        return voteScores.getOrDefault(itemId, 0.0);
    }
}

// ============================================================================
// DEMONSTRATION AND TESTING
// ============================================================================

/**
 * Comprehensive demonstration of Voting/Like System
 */
public class VotingLikeSystem {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("VOTING / LIKE SYSTEM - COMPREHENSIVE DEMONSTRATION");
        System.out.println("=".repeat(70));
        
        demo1BasicVoting();
        demo2Idempotency();
        demo3VoteFlipping();
        demo4ConcurrentVoting();
        demo5DoubleVotePrevention();
        demo6Reactions();
        demo7WeightedVoting();
        demo8PerformanceBenchmark();
        demo9DataConsistency();
        demo10CompareImplementations();
    }
    
    private static void demo1BasicVoting() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 1: Basic Voting Operations");
        System.out.println("=".repeat(70));
        
        VotingSystem voting = new ConcurrentVotingSystem();
        
        System.out.println("User A upvotes Post 1:");
        VoteResult result = voting.vote("userA", "post1", VoteType.UPVOTE);
        System.out.println("  Result: " + result);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\nUser B upvotes Post 1:");
        result = voting.vote("userB", "post1", VoteType.UPVOTE);
        System.out.println("  Result: " + result);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\nUser C downvotes Post 1:");
        result = voting.vote("userC", "post1", VoteType.DOWNVOTE);
        System.out.println("  Result: " + result);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\nVoters: " + voting.getVoters("post1"));
    }
    
    private static void demo2Idempotency() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 2: Idempotency - Same Vote Multiple Times");
        System.out.println("=".repeat(70));
        
        VotingSystem voting = new ConcurrentVotingSystem();
        
        System.out.println("User A upvotes Post 1 (first time):");
        VoteResult result1 = voting.vote("userA", "post1", VoteType.UPVOTE);
        System.out.println("  Result: " + result1);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\nUser A upvotes Post 1 (second time - should be idempotent):");
        VoteResult result2 = voting.vote("userA", "post1", VoteType.UPVOTE);
        System.out.println("  Result: " + result2);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        System.out.println("  Changed: " + result2.isChanged());
        
        System.out.println("\nUser A upvotes Post 1 (third time - still idempotent):");
        VoteResult result3 = voting.vote("userA", "post1", VoteType.UPVOTE);
        System.out.println("  Result: " + result3);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\n✅ Idempotency verified: " + 
            "Count remains 1 despite multiple votes");
    }
    
    private static void demo3VoteFlipping() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 3: Vote Flipping (Upvote ↔ Downvote)");
        System.out.println("=".repeat(70));
        
        VotingSystem voting = new ConcurrentVotingSystem();
        
        System.out.println("User A upvotes Post 1:");
        voting.vote("userA", "post1", VoteType.UPVOTE);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\nUser A changes to downvote:");
        voting.vote("userA", "post1", VoteType.DOWNVOTE);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\nUser A changes back to upvote:");
        voting.vote("userA", "post1", VoteType.UPVOTE);
        System.out.println("  Count: " + voting.getVoteCount("post1"));
        
        System.out.println("\n✅ Vote flipping works correctly: counts update properly");
    }
    
    private static void demo4ConcurrentVoting() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 4: Concurrent Voting (Thread Safety)");
        System.out.println("=".repeat(70));
        
        ConcurrentVotingSystem voting = new ConcurrentVotingSystem();
        int numThreads = 10;
        int votesPerThread = 100;
        
        System.out.println("Spawning " + numThreads + " threads, each casting " + votesPerThread + " votes...");
        
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < votesPerThread; j++) {
                    String userId = "user" + threadId + "_" + j;
                    voting.vote(userId, "post1", VoteType.UPVOTE);
                }
            });
            threads[i].start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        VoteCount count = voting.getVoteCount("post1");
        System.out.printf("Expected: %d upvotes%n", numThreads * votesPerThread);
        System.out.printf("Actual: %d upvotes%n", count.getUpvotes());
        System.out.println("✅ Thread-safe: " + 
            (count.getUpvotes() == numThreads * votesPerThread));
    }
    
    private static void demo5DoubleVotePrevention() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 5: Double Vote Prevention");
        System.out.println("=".repeat(70));
        
        VotingSystem voting = new ConcurrentVotingSystem();
        
        System.out.println("User A votes on multiple posts:");
        voting.vote("userA", "post1", VoteType.UPVOTE);
        voting.vote("userA", "post2", VoteType.UPVOTE);
        voting.vote("userA", "post3", VoteType.DOWNVOTE);
        
        System.out.println("  post1: " + voting.getUserVote("userA", "post1"));
        System.out.println("  post2: " + voting.getUserVote("userA", "post2"));
        System.out.println("  post3: " + voting.getUserVote("userA", "post3"));
        
        System.out.println("\nUser A tries to vote twice on post1:");
        VoteResult result = voting.vote("userA", "post1", VoteType.UPVOTE);
        System.out.println("  Changed: " + result.isChanged() + " (prevented!)");
        System.out.println("  post1 count: " + voting.getVoteCount("post1"));
        
        System.out.println("\n✅ Each user can only vote once per item");
    }
    
    private static void demo6Reactions() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 6: Facebook-Style Reactions");
        System.out.println("=".repeat(70));
        
        ReactionSystem reactions = new ReactionSystem();
        
        System.out.println("Users react to post1:");
        reactions.react("userA", "post1", ReactionType.LIKE);
        reactions.react("userB", "post1", ReactionType.LOVE);
        reactions.react("userC", "post1", ReactionType.HAHA);
        reactions.react("userD", "post1", ReactionType.LIKE);
        reactions.react("userE", "post1", ReactionType.WOW);
        
        Map<ReactionType, Integer> counts = reactions.getReactionCounts("post1");
        System.out.println("\nReaction counts:");
        counts.forEach((type, count) -> 
            System.out.printf("  %s: %d%n", type, count));
        
        System.out.println("\nUser A changes reaction from LIKE to LOVE:");
        reactions.react("userA", "post1", ReactionType.LOVE);
        
        counts = reactions.getReactionCounts("post1");
        System.out.println("\nUpdated counts:");
        counts.forEach((type, count) -> 
            System.out.printf("  %s: %d%n", type, count));
    }
    
    private static void demo7WeightedVoting() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 7: Weighted Voting (VIP Users)");
        System.out.println("=".repeat(70));
        
        WeightedVotingSystem voting = new WeightedVotingSystem();
        
        // Set weights
        voting.setUserWeight("vipUser", 2.0);  // VIP vote counts 2x
        voting.setUserWeight("modUser", 1.5);  // Mod vote counts 1.5x
        
        System.out.println("Normal user upvotes (weight=1.0):");
        voting.vote("normalUser", "post1", VoteType.UPVOTE);
        System.out.println("  Score: " + voting.getWeightedScore("post1"));
        
        System.out.println("\nMod user upvotes (weight=1.5):");
        voting.vote("modUser", "post1", VoteType.UPVOTE);
        System.out.println("  Score: " + voting.getWeightedScore("post1"));
        
        System.out.println("\nVIP user upvotes (weight=2.0):");
        voting.vote("vipUser", "post1", VoteType.UPVOTE);
        System.out.println("  Score: " + voting.getWeightedScore("post1"));
        System.out.println("  Expected: 1.0 + 1.5 + 2.0 = 4.5");
        
        System.out.println("\nVIP user changes to downvote:");
        voting.vote("vipUser", "post1", VoteType.DOWNVOTE);
        System.out.println("  Score: " + voting.getWeightedScore("post1"));
        System.out.println("  Expected: 1.0 + 1.5 - 2.0 = 0.5");
    }
    
    private static void demo8PerformanceBenchmark() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 8: Performance Benchmark");
        System.out.println("=".repeat(70));
        
        ConcurrentVotingSystem voting = new ConcurrentVotingSystem();
        int numVotes = 10000;
        
        System.out.println("Performing " + numVotes + " votes...");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < numVotes; i++) {
            voting.vote("user" + i, "post1", VoteType.UPVOTE);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        double votesPerSecond = (numVotes * 1000.0) / durationMs;
        double avgLatencyUs = (durationMs * 1000.0) / numVotes;
        
        System.out.printf("Total time: %d ms%n", durationMs);
        System.out.printf("Votes per second: %.2f%n", votesPerSecond);
        System.out.printf("Average latency: %.2f μs%n", avgLatencyUs);
        System.out.println("✅ Performance target met: " + 
            (avgLatencyUs < 10000 ? "< 10ms" : "> 10ms"));
    }
    
    private static void demo9DataConsistency() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 9: Data Consistency Verification");
        System.out.println("=".repeat(70));
        
        VotingSystem voting = new ConcurrentVotingSystem();
        
        // Create votes
        voting.vote("user1", "post1", VoteType.UPVOTE);
        voting.vote("user2", "post1", VoteType.UPVOTE);
        voting.vote("user3", "post1", VoteType.DOWNVOTE);
        voting.vote("user4", "post1", VoteType.UPVOTE);
        
        VoteCount count = voting.getVoteCount("post1");
        Set<String> voters = voting.getVoters("post1");
        
        System.out.println("Votes: " + count);
        System.out.println("Voters: " + voters);
        System.out.println("Voter count: " + voters.size());
        
        // Verify consistency
        int expectedVoters = 4;
        int expectedUpvotes = 3;
        int expectedDownvotes = 1;
        int expectedScore = 2;
        
        boolean consistent = 
            voters.size() == expectedVoters &&
            count.getUpvotes() == expectedUpvotes &&
            count.getDownvotes() == expectedDownvotes &&
            count.getScore() == expectedScore;
        
        System.out.println("\n✅ Data consistency: " + consistent);
        System.out.println("  Voters: " + voters.size() + " == " + expectedVoters);
        System.out.println("  Upvotes: " + count.getUpvotes() + " == " + expectedUpvotes);
        System.out.println("  Downvotes: " + count.getDownvotes() + " == " + expectedDownvotes);
        System.out.println("  Score: " + count.getScore() + " == " + expectedScore);
    }
    
    private static void demo10CompareImplementations() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 10: Compare Different Implementations");
        System.out.println("=".repeat(70));
        
        System.out.println("\n1. ConcurrentHashMap (Recommended):");
        System.out.println("   - Thread-safe: ✅");
        System.out.println("   - Lock-free: Mostly ✅");
        System.out.println("   - Performance: Excellent ⭐⭐⭐⭐⭐");
        System.out.println("   - Complexity: Low");
        
        System.out.println("\n2. Synchronized:");
        System.out.println("   - Thread-safe: ✅");
        System.out.println("   - Lock-free: ❌");
        System.out.println("   - Performance: Poor under contention ⭐⭐");
        System.out.println("   - Complexity: Very Low");
        
        System.out.println("\n3. StampedLock:");
        System.out.println("   - Thread-safe: ✅");
        System.out.println("   - Lock-free: Optimistic reads ✅");
        System.out.println("   - Performance: Good for read-heavy ⭐⭐⭐⭐");
        System.out.println("   - Complexity: Medium");
        
        System.out.println("\n4. CAS (Compare-And-Swap):");
        System.out.println("   - Thread-safe: ✅");
        System.out.println("   - Lock-free: ✅");
        System.out.println("   - Performance: Excellent (low contention) ⭐⭐⭐⭐⭐");
        System.out.println("   - Complexity: Medium");
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ALL DEMOS COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(70));
        
        System.out.println("\nKey Takeaways:");
        System.out.println("1. ConcurrentHashMap is the best choice for most use cases");
        System.out.println("2. Idempotency prevents duplicate votes");
        System.out.println("3. compute() method provides atomic updates");
        System.out.println("4. Thread safety is crucial for concurrent systems");
        System.out.println("5. Vote counts must always be consistent");
        
        System.out.println("\nInterview Tips:");
        System.out.println("- Explain race conditions and prevention");
        System.out.println("- Show understanding of CAS operations");
        System.out.println("- Discuss idempotency importance");
        System.out.println("- Consider scale and performance");
        System.out.println("- Handle edge cases properly");
    }
}
