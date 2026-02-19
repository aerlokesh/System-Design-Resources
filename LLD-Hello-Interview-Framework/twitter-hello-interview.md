# Twitter System - HELLO Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **User Management**
   - Register users with unique username
   - User profiles with basic info
   - Follow/unfollow other users
   - Track followers and following lists

2. **Tweet Operations**
   - Post tweets (280 character limit)
   - View tweets by user
   - Like tweets
   - Retweet functionality
   - Timestamp for each tweet

3. **News Feed Generation**
   - Show tweets from users you follow
   - Show your own tweets
   - Sort by timestamp (newest first)
   - Support pagination/limits

4. **Social Graph**
   - Bidirectional follow relationships
   - Follower count tracking
   - Following count tracking

#### Nice to Have (P1)
- Tweet replies and threads
- Hashtags and mentions
- Media attachments (images, videos)
- Direct messaging
- Trending topics
- Search functionality
- User verification badges
- Tweet analytics
- Bookmarks and lists
- Multiple feed algorithms (chronological, ranked)

### Non-Functional Requirements

#### Performance
- **Tweet posting**: < 100ms
- **Feed generation**: < 200ms for 50 tweets
- **Follow operation**: < 50ms
- Support 100M+ daily active users

#### Scalability
- Handle 500M tweets per day
- Support 1B+ total users
- Handle celebrity accounts with 100M+ followers
- Feed generation for power users (following 10K+ accounts)

#### Thread Safety
- Concurrent tweet posting
- Race-free like counting
- Consistent follower graphs

#### Availability
- 99.9% uptime
- No data loss for tweets
- Eventually consistent feed generation acceptable

### Constraints and Assumptions

**Constraints**:
- 280 character limit per tweet
- Tweet content immutable after posting
- Username unique and immutable
- Single timeline per user

**Assumptions**:
- Users mostly follow < 1000 accounts
- Most tweets have < 100 likes
- Read-heavy system (read:write = 100:1)
- Celebrity accounts are rare edge cases

**Out of Scope**:
- Spam detection and moderation
- Ads and promoted content
- Video streaming infrastructure
- Advanced ML recommendation algorithms
- Multi-region data replication
- Mobile push notifications

---

## 2️⃣ Core Entities

### Class: TwitterUser
**Responsibility**: Represent a user in the system

**Key Attributes**:
- `userId: String` - Unique identifier
- `username: String` - Display name (@username)
- `followers: Set<String>` - User IDs of followers
- `following: Set<String>` - User IDs being followed

**Key Methods**:
- `follow(userId)`: Add user to following list
- `unfollow(userId)`: Remove user from following list
- `addFollower(userId)`: Add to followers list
- `removeFollower(userId)`: Remove from followers list

**Relationships**:
- Self-referencing: Many-to-Many (following)
- Creates: Tweet
- Owns: News Feed

### Class: Tweet
**Responsibility**: Represent a single tweet

**Key Attributes**:
- `tweetId: String` - Unique identifier
- `userId: String` - Author's user ID
- `content: String` - Tweet text (max 280 chars)
- `timestamp: LocalDateTime` - Creation time
- `likes: int` - Like count
- `retweets: int` - Retweet count

**Key Methods**:
- `like()`: Increment like count
- `retweet()`: Increment retweet count
- `getContent()`: Get tweet text

**Relationships**:
- Authored by: TwitterUser
- Contained in: News Feed
- Part of: Timeline

**Constraints**:
- Content immutable after creation
- Timestamp set at creation
- Metrics can only increase

### Interface: FeedGenerationStrategy
**Responsibility**: Define contract for feed generation algorithms

**Key Methods**:
- `generateFeed(userId, tweets, limit)`: Generate personalized feed
- `getName()`: Get strategy name

**Implementations**:
- `ChronologicalFeedStrategy`: Time-ordered feed
- `RankedFeedStrategy`: Engagement-based ranking
- `EngagementBoostStrategy`: Boost high-engagement tweets

**Design Pattern**: Strategy Pattern
**Benefits**:
- Easy to swap algorithms
- Test different ranking strategies
- Add new algorithms without changing core code

### Class: ChronologicalFeedStrategy
**Responsibility**: Generate time-sorted feed

**Algorithm**:
1. Collect all tweets from following + own tweets
2. Sort by timestamp (newest first)
3. Apply limit
4. Return ordered list

**Use Case**: "Latest Tweets" view
**Complexity**: O(n log n) where n = total tweets

### Class: RankedFeedStrategy
**Responsibility**: Generate engagement-ranked feed

**Algorithm**:
1. Collect tweets from following + own tweets
2. Calculate engagement score: likes + (retweets × 2)
3. Sort by: engagement score DESC, timestamp DESC
4. Apply limit
5. Return ranked list

**Use Case**: "Top Tweets" view
**Complexity**: O(n log n)

### Class: EngagementBoostStrategy
**Responsibility**: Boost high-engagement recent tweets

**Algorithm**:
1. Collect tweets from following + own tweets
2. Calculate recency weight (newer = higher)
3. Calculate boost score: engagement × recency_weight
4. Sort by boost score
5. Apply limit

**Use Case**: "What's Happening" feed
**Benefits**: Balances freshness and popularity

### Class: TwitterService
**Responsibility**: Main service coordinator

**Key Attributes**:
- `users: Map<String, TwitterUser>` - All users
- `tweets: Map<String, Tweet>` - All tweets
- `userTweets: Map<String, List<Tweet>>` - User's tweets
- `feedStrategy: FeedGenerationStrategy` - Current strategy

**Key Methods**:
- `registerUser(user)`: Add new user
- `follow(followerId, followeeId)`: Create follow relationship
- `postTweet(userId, content)`: Create new tweet
- `likeTweet(tweetId)`: Increment like count
- `getNewsFeed(userId, limit)`: Generate personalized feed
- `setFeedStrategy(strategy)`: Change feed algorithm

**Relationships**:
- Manages: TwitterUser, Tweet
- Uses: FeedGenerationStrategy
- Coordinates: All system operations

**Design Patterns**:
- Facade: Simplifies complex operations
- Strategy: Delegates feed generation
- Singleton: Single service instance (optional)

---

## 3️⃣ API Design

### Interface: FeedGenerationStrategy

```java
/**
 * Strategy interface for feed generation algorithms
 * Allows swapping different ranking/sorting strategies
 */
public interface FeedGenerationStrategy {
    /**
     * Generate personalized feed for user
     * @param userId User requesting feed
     * @param allRelevantTweets Tweets from following + own tweets
     * @param limit Maximum tweets to return
     * @return Ordered list of tweets
     */
    List<Tweet> generateFeed(String userId, List<Tweet> allRelevantTweets, int limit);
    
    /**
     * Get strategy name for display
     * @return Strategy name
     */
    String getName();
}
```

### Class: ChronologicalFeedStrategy

```java
/**
 * Time-ordered feed generation (newest first)
 * Traditional Twitter timeline
 */
public class ChronologicalFeedStrategy implements FeedGenerationStrategy {
    @Override
    public List<Tweet> generateFeed(String userId, List<Tweet> allRelevantTweets, int limit) {
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
```

### Class: RankedFeedStrategy

```java
/**
 * Engagement-based feed ranking
 * Prioritizes tweets with high likes and retweets
 */
public class RankedFeedStrategy implements FeedGenerationStrategy {
    @Override
    public List<Tweet> generateFeed(String userId, List<Tweet> allRelevantTweets, int limit) {
        return allRelevantTweets.stream()
            .sorted((t1, t2) -> {
                // Calculate engagement score
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
```

### Class: EngagementBoostStrategy

```java
/**
 * Boost high-engagement recent tweets
 * Balances freshness and popularity
 */
public class EngagementBoostStrategy implements FeedGenerationStrategy {
    @Override
    public List<Tweet> generateFeed(String userId, List<Tweet> allRelevantTweets, int limit) {
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
    
    /**
     * Calculate recency weight using exponential decay
     * Recent tweets get higher weight
     */
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
```

### Class: TwitterService

```java
/**
 * Main Twitter service with strategy pattern for feed generation
 */
public class TwitterService {
    private final Map<String, TwitterUser> users;
    private final Map<String, Tweet> tweets;
    private final Map<String, List<Tweet>> userTweets;
    private FeedGenerationStrategy feedStrategy;
    private int tweetCounter;

    public TwitterService() {
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
     * 
     * @param strategy New feed generation strategy
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

    public Tweet postTweet(String userId, String content) {
        if (content.length() > 280) {
            System.out.println("✗ Tweet exceeds 280 character limit");
            return null;
        }

        String tweetId = "T" + tweetCounter++;
        Tweet tweet = new Tweet(tweetId, userId, content);

        tweets.put(tweetId, tweet);
        userTweets.get(userId).add(tweet);

        System.out.println("✓ Tweet posted: " + tweet);
        return tweet;
    }

    public void likeTweet(String tweetId) {
        Tweet tweet = tweets.get(tweetId);
        if (tweet != null) {
            tweet.like();
            System.out.println("♥ Liked: " + tweetId);
        }
    }

    /**
     * Generate news feed using current strategy
     * Delegates to strategy for sorting/ranking
     * 
     * @param userId User requesting feed
     * @param limit Maximum tweets to return
     * @return Personalized feed
     */
    public List<Tweet> getNewsFeed(String userId, int limit) {
        TwitterUser user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        // Collect all relevant tweets
        List<Tweet> allRelevantTweets = new ArrayList<>();

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
        List<Tweet> feed = getNewsFeed(userId, limit);

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
```

---

## 4️⃣ Data Flow

### Flow 1: User Follows Another User

**Sequence**:
1. Alice wants to follow Bob
2. System calls `follow("alice", "bob")`
3. Look up Alice's user object
4. Look up Bob's user object
5. Validate: users exist and not same user
6. Alice adds Bob to following set
7. Bob adds Alice to followers set
8. Print confirmation
9. Return success

**State Changes**:
```
Before:
Alice.following = {}
Bob.followers = {}

After:
Alice.following = {"bob"}
Bob.followers = {"alice"}
```

**Time Complexity**: O(1)

### Flow 2: Post Tweet

**Sequence**:
1. Bob wants to post "Hello Twitter!"
2. System calls `postTweet("bob", "Hello Twitter!")`
3. Validate content length ≤ 280 chars
4. Generate unique tweet ID: "T1"
5. Create Tweet object with:
   - ID: "T1"
   - User: "bob"
   - Content: "Hello Twitter!"
   - Timestamp: now()
   - Likes: 0, Retweets: 0
6. Store in tweets map: {"T1" -> Tweet}
7. Store in userTweets: {"bob" -> [Tweet]}
8. Print confirmation
9. Return tweet object

**Data Structure Updates**:
```
tweets: {"T1" -> Tweet("T1", "bob", "Hello Twitter!", ...)}
userTweets: {"bob" -> [Tweet1]}
```

### Flow 3: Generate Chronological Feed

**Scenario**: Alice follows Bob and Charlie, both posted tweets

**Sequence**:
1. Alice requests feed: `getNewsFeed("alice", 10)`
2. Look up Alice's user object
3. Create empty feed list
4. Add Alice's own tweets to feed
5. For each user in Alice.following:
   - Get Bob's tweets → add to feed
   - Get Charlie's tweets → add to feed
6. Call ChronologicalFeedStrategy.generateFeed():
   - Sort all tweets by timestamp (newest first)
   - Limit to 10 tweets
7. Return sorted feed
8. Display to Alice

**Visual**:
```
Alice's tweets: [T4(12:30)]
Bob's tweets: [T1(10:00), T3(12:00)]
Charlie's tweets: [T2(11:00)]

Combined: [T4, T3, T2, T1]
After sort by time: [T4(12:30), T3(12:00), T2(11:00), T1(10:00)]
```

**Time Complexity**: O(n log n) where n = total tweets

### Flow 4: Generate Ranked Feed

**Scenario**: Same tweets, but use RankedFeedStrategy

**Tweet Engagement**:
```
T1: 2 likes, 0 retweets → score = 2
T2: 1 like, 0 retweets → score = 1
T3: 0 likes, 0 retweets → score = 0
T4: 0 likes, 0 retweets → score = 0
```

**Ranking Algorithm**:
1. Calculate engagement score: likes + (retweets × 2)
2. Sort by score DESC, then timestamp DESC
3. Result: [T1, T2, T4, T3]

**Strategy Pattern in Action**:
```java
// Switch from chronological to ranked
service.setFeedStrategy(new RankedFeedStrategy());
// Same getNewsFeed() call, different result
List<Tweet> feed = service.getNewsFeed("alice", 10);
```

### Flow 5: Like Tweet

**Sequence**:
1. User clicks like on tweet T1
2. System calls `likeTweet("T1")`
3. Look up tweet in tweets map
4. Tweet found
5. Call tweet.like()
6. Increment likes counter: 0 → 1
7. Print confirmation
8. Tweet object updated in map (reference)

**Concurrency Note**:
```java
// For production, likes should be atomic
private final AtomicInteger likes = new AtomicInteger(0);

public void like() {
    likes.incrementAndGet();
}
```

### Flow 6: Switch Feed Strategy

**Demonstration of Strategy Pattern Flexibility**:

```java
// Initial: Chronological feed
service.displayNewsFeed("alice", 5);
// Output: [T4, T3, T2, T1] (by time)

// Switch strategy at runtime
service.setFeedStrategy(new RankedFeedStrategy());

// Same API call, different results
service.displayNewsFeed("alice", 5);
// Output: [T1, T2, T4, T3] (by engagement)

// Switch again
service.setFeedStrategy(new EngagementBoostStrategy());
service.displayNewsFeed("alice", 5);
// Output: [T1, T4, T3, T2] (by boost score)
```

**Benefits**:
- No code changes in TwitterService
- Easy A/B testing of algorithms
- Runtime algorithm swapping
- Clean separation of concerns

---

## 5️⃣ Design

### Class Diagram

```
┌─────────────────────────────────────────┐
│  <<interface>> FeedGenerationStrategy   │
│  + generateFeed(userId, tweets, limit)  │
│  + getName()                            │
└──────────────▲──────────────────────────┘
               │ implements
     ┌─────────┼─────────┬─────────────────┐
     │         │         │                 │
┌────▼────┐┌──▼──────┐┌─▼────────────┐   │
│Chronolo ││Ranked   ││Engagement    │   │
│gical    ││Feed     ││Boost         │   │
└─────────┘└─────────┘└──────────────┘   │
                                          │
┌─────────────────────────────────────────▼──┐
│  TwitterService                             │
│  - users: Map<String, TwitterUser>         │
│  - tweets: Map<String, Tweet>              │
│  - userTweets: Map<String, List<Tweet>>    │
│  - feedStrategy: FeedGenerationStrategy    │
│  + setFeedStrategy(strategy)               │
│  + registerUser(user)                      │
│  + follow(followerId, followeeId)          │
│  + postTweet(userId, content)              │
│  + likeTweet(tweetId)                      │
│  + getNewsFeed(userId, limit)              │
└────────────┬───────────┬───────────────────┘
             │           │
             │ manages   │ manages
             │           │
      ┌──────▼──┐    ┌───▼────┐
      │Twitter  │    │  Tweet │
      │User     │    │        │
      └─────────┘    └────────┘
```

### Strategy Pattern Structure

```
┌─────────────────────────────┐
│  Context: TwitterService    │
│  - strategy: FeedGeneration │
│  + setStrategy()            │
│  + getNewsFeed() ────────┐  │
└─────────────────────────────┘
                             │
                             │ delegates
                             ▼
┌──────────────────────────────────────┐
│  <<interface>> FeedGenerationStrategy│
│  + generateFeed()                    │
└────────────▲─────────────────────────┘
             │ implements
    ┌────────┼────────┬─────────────┐
    │        │        │             │
┌───▼──┐ ┌──▼───┐ ┌──▼────┐    ┌───▼────┐
│Chrono│ │Ranked│ │Engage │... │Future  │
│logical│ │      │ │Boost  │    │Strategy│
└──────┘ └──────┘ └───────┘    └────────┘
```

### Follow Relationship Graph

```
User A ──follows──► User B
       ◄──follower──

Bidirectional tracking:
A.following.add(B)
B.followers.add(A)

Example Network:
    Alice
     │ follows
     ▼
    Bob ◄──┐
     │     │ follows
     │     │
     ▼     │
  Charlie ─┘
  
Alice.following = {Bob}
Bob.following = {Charlie}
Bob.followers = {Alice, Charlie}
Charlie.following = {Bob}
Charlie.followers = {Bob}
```

### Feed Generation Pipeline

```
Input: userId = "alice"

Step 1: Collect Tweets
┌──────────────────┐
│ Own Tweets       │──► [T4]
│ Following Tweets │──► [T1, T2, T3]
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ Combined List    │──► [T1, T2, T3, T4]
└──────────────────┘
         │
         ▼
Step 2: Apply Strategy
┌──────────────────────────────┐
│ FeedGenerationStrategy       │
│ (Chronological/Ranked/Boost) │
└──────────────────────────────┘
         │
         ▼
┌──────────────────┐
│ Sorted Feed      │──► [T4, T3, T2, T1]
│ Limited to N     │
└──────────────────┘
         │
         ▼
     Display
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Why Strategy Pattern for Feed Generation?

**Problem**: Different users want different feed experiences

**Without Strategy Pattern** (❌ Bad):
```java
public List<Tweet> getNewsFeed(String userId, String type) {
    // Collect tweets...
    
    if (type.equals("chronological")) {
        return tweets.stream()
            .sorted(byTimestamp)
            .limit(limit)
            .collect(toList());
    } else if (type.equals("ranked")) {
        return tweets.stream()
            .sorted(byEngagement)
            .limit(limit)
            .collect(toList());
    } else if (type.equals("boost")) {
        // More complex logic...
    }
    // Violates Open/Closed Principle
}
```

**Problems**:
1. ❌ God method - too many responsibilities
2. ❌ Hard to test each algorithm
3. ❌ Adding new algorithm requires modifying core code
4. ❌ Cannot change strategy at runtime easily
5. ❌ Tight coupling

**With Strategy Pattern** (✅ Good):
```java
public List<Tweet> getNewsFeed(String userId, int limit) {
    List<Tweet> tweets = collectRelevantTweets(userId);
    return feedStrategy.generateFeed(userId, tweets, limit);
}

// Easy to add new strategies
public void setFeedStrategy(FeedGenerationStrategy strategy) {
    this.feedStrategy = strategy;
}
```

**Benefits**:
1. ✅ Single Responsibility: TwitterService doesn't know sorting logic
2. ✅ Open/Closed: Add strategies without modifying service
3. ✅ Easy testing: Test each strategy independently
4. ✅ Runtime flexibility: Change algorithm on the fly
5. ✅ Clean code: Each strategy in separate class

### Deep Dive 2: Feed Generation Algorithm Comparison

**Test Data**:
```
T1: "Hello" by Bob, posted 10:00, 5 likes, 2 retweets
T2: "World" by Charlie, posted 11:00, 1 like, 0 retweets
T3: "Test" by Bob, posted 12:00, 0 likes, 0 retweets
T4: "Latest" by Alice, posted 12:30, 0 likes, 0 retweets
```

**Chronological Strategy**:
```
Sort key: timestamp DESC
Result: [T4(12:30), T3(12:00), T2(11:00), T1(10:00)]
Use case: "I want to see what just happened"
```

**Ranked Strategy**:
```
Score calculation: likes + (retweets × 2)
T1 score: 5 + (2 × 2) = 9
T2 score: 1 + (0 × 2) = 1
T3 score: 0
T4 score: 0

Sort: score DESC, then timestamp DESC
Result: [T1(9), T2(1), T4(0/newer), T3(0/older)]
Use case: "Show me popular tweets I missed"
```

**Engagement Boost Strategy**:
```
Recency weight (exponential decay):
T1: 2.5 hours ago → weight = 0.93
T2: 1.5 hours ago → weight = 0.96
T3: 0.5 hours ago → weight = 0.99
T4: just now → weight = 1.0

Boost score = engagement × recency:
T1: 9 × 0.93 = 8.37
T2: 1 × 0.96 = 0.96
T3: 0 × 0.99 = 0
T4: 0 × 1.0 = 0

Result: [T1(8.37), T2(0.96), T4(0/newer), T3(0/older)]
Use case: "Show me what's hot right now"
```

### Deep Dive 3: Scalability Considerations

**Problem 1: Celebrity User with 100M Followers**

**Naive Approach** (❌ Doesn't Scale):
```java
// When celebrity tweets, update 100M feeds
public Tweet postTweet(String userId, String content) {
    Tweet tweet = createTweet(userId, content);
    
    // DISASTER: O(100M) writes per tweet
    for (String followerId : getFollowers(userId)) {
        addToFeed(followerId, tweet);
    }
    
    return tweet;
}
```

**Better Approach** (✅ Fan-out on Read):
```java
// Celebrity tweets: just store tweet
// Fans request feed: compute on demand

public List<Tweet> getNewsFeed(String userId, int limit) {
    // Only fetch tweets when user opens app
    // O(following_count × avg_tweets)
    List<Tweet> tweets = new ArrayList<>();
    
    for (String followingId : user.getFollowing()) {
        tweets.addAll(getUserTweets(followingId, recent=100));
    }
    
    return feedStrategy.generateFeed(userId, tweets, limit);
}
```

**Hybrid Approach** (✅ Production-Ready):
```
Regular users (< 10K followers): Fan-out on write
  - Pre-compute and cache feeds
  - Fast read access

Celebrity users (> 10K followers): Fan-out on read
  - Compute feed on demand
  - Cache aggressively
  - Use CDN for static content
```

**Problem 2: Power User Following 10K Accounts**

**Solution: Time-windowing**
```java
// Only fetch recent tweets (last 24 hours)
public List<Tweet> getNewsFeed(String userId, int limit) {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
    
    List<Tweet> recentTweets = user.getFollowing().stream()
        .flatMap(id -> getRecentTweets(id, cutoff).stream())
        .collect(toList());
    
    return feedStrategy.generateFeed(userId, recentTweets, limit);
}
```

### Deep Dive 4: Thread Safety

**Problem**: Concurrent likes on same tweet

**Race Condition**:
```java
// Thread 1 and Thread 2 both call like() simultaneously
private int likes = 0;

public void like() {
    likes++;  // NOT ATOMIC!
}

//
