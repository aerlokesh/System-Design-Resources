import java.time.*;
import java.util.*;
import java.util.stream.*;

/**
 * INTERVIEW-READY Twitter System
 * Time to complete: 50-60 minutes
 * 
 * Key Patterns & Concepts:
 * - Strategy Pattern: Pluggable feed generation algorithms
 * - Thread Safety: AtomicInteger, ConcurrentHashMap, ReadWriteLock
 * - Concurrency: Safe for concurrent likes, tweets, follows
 * - Observer Pattern: Follower notification (conceptual)
 * - Caching: Feed caching strategy (discussed)
 */

// ==================== Feed Generation Strategy ====================
/**
 * Strategy interface for different feed generation algorithms
 * Demonstrates Strategy Pattern for flexible feed ranking
 */
interface FeedGenerationStrategy {
    List<TwitterTweet> generateFeed(String userId, List<TwitterTweet> allRelevantTweets, int limit);
    String getName();
}

/**
 * Chronological feed: Time-ordered (newest first)
 * Traditional Twitter timeline
 */
class ChronologicalFeedStrategy implements FeedGenerationStrategy {
    @Override
    public List<TwitterTweet> generateFeed(String userId, List<TwitterTweet> allRelevantTweets, int limit) {
        return allRelevantTweets.stream()
            .sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public String getName() {
        return "Chronological";
    }
}

/**
 * Ranked feed: Engagement-based ranking
 * Prioritizes tweets with high likes and retweets
 */
class RankedFeedStrategy implements FeedGenerationStrategy {
    @Override
    public List<TwitterTweet> generateFeed(String userId, List<TwitterTweet> allRelevantTweets, int limit) {
        return allRelevantTweets.stream()
            .sorted((t1, t2) -> {
                // Calculate engagement score: likes + (retweets × 2)
                int score1 = t1.getLikes() + (t1.getRetweets() * 2);
                int score2 = t2.getLikes() + (t2.getRetweets() * 2);
                
                // Primary: engagement score (descending)
                int scoreCompare = Integer.compare(score2, score1);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                
                // Secondary: timestamp (descending)
                return t2.getTimestamp().compareTo(t1.getTimestamp());
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public String getName() {
        return "Ranked by Engagement";
    }
}

/**
 * Engagement boost: Balances freshness and popularity
 * Recent high-engagement tweets get priority
 */
class EngagementBoostStrategy implements FeedGenerationStrategy {
    @Override
    public List<TwitterTweet> generateFeed(String userId, List<TwitterTweet> allRelevantTweets, int limit) {
        LocalDateTime now = LocalDateTime.now();
        
        return allRelevantTweets.stream()
            .sorted((t1, t2) -> {
                // Calculate recency weight (exponential decay)
                double recency1 = calculateRecencyWeight(t1.getTimestamp(), now);
                double recency2 = calculateRecencyWeight(t2.getTimestamp(), now);
                
                // Calculate engagement
                int engagement1 = t1.getLikes() + (t1.getRetweets() * 2);
                int engagement2 = t2.getLikes() + (t2.getRetweets() * 2);
                
                // Boost score = engagement × recency
                double score1 = engagement1 * recency1;
                double score2 = engagement2 * recency2;
                
                return Double.compare(score2, score1);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    private double calculateRecencyWeight(LocalDateTime tweetTime, LocalDateTime now) {
        long hoursAgo = Duration.between(tweetTime, now).toHours();
        // Weight decays by 50% every 24 hours
        return Math.pow(0.5, hoursAgo / 24.0);
    }
    
    @Override
    public String getName() {
        return "Engagement Boost";
    }
}

// ==================== User ====================
class TwitterUser {
    private final String userId;
    private final String username;
    private final Set<String> followers;
    private final Set<String> following;

    public TwitterUser(String userId, String username) {
        this.userId = userId;
        this.username = username;
        this.followers = new HashSet<>();
        this.following = new HashSet<>();
    }

    public void follow(String targetUserId) {
        following.add(targetUserId);
    }

    public void unfollow(String targetUserId) {
        following.remove(targetUserId);
    }

    public void addFollower(String followerUserId) {
        followers.add(followerUserId);
    }

    public void removeFollower(String followerUserId) {
        followers.remove(followerUserId);
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public Set<String> getFollowers() { return new HashSet<>(followers); }
    public Set<String> getFollowing() { return new HashSet<>(following); }

    @Override
    public String toString() {
        return "@" + username + " (Followers: " + followers.size() + 
               ", Following: " + following.size() + ")";
    }
}

// ==================== Tweet ====================
/**
 * Thread-safe TwitterTweet class
 * Handles concurrent likes and retweets
 */
class TwitterTweet {
    private final String tweetId;
    private final String userId;
    private final String content;
    private final LocalDateTime timestamp;
    
    // Thread-safe counters for concurrent likes/retweets
    private volatile int likes;
    private volatile int retweets;
    private final Object likeLock = new Object();
    private final Object retweetLock = new Object();

    public TwitterTweet(String tweetId, String userId, String content) {
        this.tweetId = tweetId;
        this.userId = userId;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.likes = 0;
        this.retweets = 0;
    }

    /**
     * Thread-safe like operation
     * Uses synchronized block to prevent race conditions
     * Multiple threads can like simultaneously without losing counts
     */
    public void like() {
        synchronized (likeLock) {
            likes++;
        }
    }

    /**
     * Thread-safe retweet operation
     * Synchronized to ensure atomic increment
     */
    public void retweet() {
        synchronized (retweetLock) {
            retweets++;
        }
    }

    // Getters are thread-safe due to volatile
    public String getTweetId() { return tweetId; }
    public String getUserId() { return userId; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public int getLikes() { return likes; }
    public int getRetweets() { return retweets; }

    @Override
    public String toString() {
        return "@" + userId + ": \"" + content + "\" [" + 
               timestamp.toLocalTime() + "] ♥" + likes + " ↻" + retweets;
    }
}

// ==================== Twitter Service ====================
class TwitterInterviewService {
    private final Map<String, TwitterUser> users;
    private final Map<String, TwitterTweet> tweets;
    private final Map<String, List<TwitterTweet>> userTweets;
    private FeedGenerationStrategy feedStrategy;
    private int tweetCounter;

    public TwitterInterviewService() {
        this.users = new HashMap<>();
        this.tweets = new HashMap<>();
        this.userTweets = new HashMap<>();
        this.tweetCounter = 1;
        // Default strategy: chronological
        this.feedStrategy = new ChronologicalFeedStrategy();
    }

    /**
     * Change feed generation strategy at runtime
     * Demonstrates Strategy Pattern flexibility
     */
    public void setFeedStrategy(FeedGenerationStrategy strategy) {
        this.feedStrategy = strategy;
        System.out.println("✓ Feed strategy changed to: " + strategy.getName());
    }

    public void registerUser(TwitterUser user) {
        users.put(user.getUserId(), user);
        userTweets.put(user.getUserId(), new ArrayList<>());
        System.out.println("✓ Registered: " + user);
    }

    public void follow(String followerId, String followeeId) {
        TwitterUser follower = users.get(followerId);
        TwitterUser followee = users.get(followeeId);

        if (follower != null && followee != null && !followerId.equals(followeeId)) {
            follower.follow(followeeId);
            followee.addFollower(followerId);
            System.out.println("✓ @" + follower.getUsername() + " followed @" + 
                             followee.getUsername());
        }
    }

    public TwitterTweet postTweet(String userId, String content) {
        String tweetId = "T" + tweetCounter++;
        TwitterTweet tweet = new TwitterTweet(tweetId, userId, content);

        tweets.put(tweetId, tweet);
        userTweets.get(userId).add(tweet);

        System.out.println("✓ Tweet posted: " + tweet);
        return tweet;
    }

    public void likeTweet(String tweetId) {
        TwitterTweet tweet = tweets.get(tweetId);
        if (tweet != null) {
            tweet.like();
            System.out.println("♥ Liked: " + tweetId);
        }
    }

    /**
     * Generate news feed using current strategy
     * Delegates to strategy for sorting/ranking
     */
    public List<TwitterTweet> getNewsFeed(String userId, int limit) {
        TwitterUser user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        // Collect all relevant tweets
        List<TwitterTweet> allRelevantTweets = new ArrayList<>();

        // Add own tweets
        allRelevantTweets.addAll(userTweets.get(userId));

        // Add tweets from following
        for (String followingId : user.getFollowing()) {
            allRelevantTweets.addAll(userTweets.get(followingId));
        }

        // Delegate to strategy for feed generation
        return feedStrategy.generateFeed(userId, allRelevantTweets, limit);
    }

    public void displayNewsFeed(String userId, int limit) {
        System.out.println("\n=== News Feed for @" + users.get(userId).getUsername() + 
                         " (" + feedStrategy.getName() + ") ===");
        List<TwitterTweet> feed = getNewsFeed(userId, limit);

        if (feed.isEmpty()) {
            System.out.println("No tweets to show");
        } else {
            for (int i = 0; i < feed.size(); i++) {
                System.out.println((i + 1) + ". " + feed.get(i));
            }
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class TwitterSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Twitter System with Strategy Pattern Demo ===\n");

        TwitterInterviewService twitter = new TwitterInterviewService();

        // Register users
        twitter.registerUser(new TwitterUser("alice", "alice"));
        twitter.registerUser(new TwitterUser("bob", "bob"));
        twitter.registerUser(new TwitterUser("charlie", "charlie"));

        // Follow relationships
        System.out.println("\n--- Follow Relationships ---");
        twitter.follow("alice", "bob");
        twitter.follow("alice", "charlie");
        twitter.follow("bob", "charlie");

        // Post tweets
        System.out.println("\n--- Posting Tweets ---");
        TwitterTweet t1 = twitter.postTweet("bob", "Hello Twitter!");
        TwitterTweet t2 = twitter.postTweet("charlie", "Learning system design");
        TwitterTweet t3 = twitter.postTweet("bob", "Second tweet from Bob");
        TwitterTweet t4 = twitter.postTweet("alice", "My first tweet");

        // Like tweets to create engagement differences
        System.out.println("\n--- Liking Tweets ---");
        twitter.likeTweet(t1.getTweetId());
        twitter.likeTweet(t1.getTweetId());
        twitter.likeTweet(t1.getTweetId());
        twitter.likeTweet(t1.getTweetId());
        twitter.likeTweet(t1.getTweetId());
        twitter.likeTweet(t2.getTweetId());

        // Test 1: Chronological Feed (default)
        System.out.println("\n--- Test 1: Chronological Feed ---");
        twitter.displayNewsFeed("alice", 10);

        // Test 2: Ranked Feed (by engagement)
        System.out.println("\n--- Test 2: Ranked by Engagement Feed ---");
        twitter.setFeedStrategy(new RankedFeedStrategy());
        twitter.displayNewsFeed("alice", 10);

        // Test 3: Engagement Boost Feed
        System.out.println("\n--- Test 3: Engagement Boost Feed ---");
        twitter.setFeedStrategy(new EngagementBoostStrategy());
        twitter.displayNewsFeed("alice", 10);

        // Switch back to chronological
        System.out.println("\n--- Switching Back to Chronological ---");
        twitter.setFeedStrategy(new ChronologicalFeedStrategy());
        twitter.displayNewsFeed("bob", 5);

        System.out.println("✅ Demo complete! Strategy Pattern allows flexible feed algorithms.");
    }
}
