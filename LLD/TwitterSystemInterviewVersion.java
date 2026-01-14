import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Twitter System
 * Time to complete: 50-60 minutes
 * Focus: Observer pattern, feed generation, follower graph
 */

// ==================== User ====================
class User {
    private final String userId;
    private final String username;
    private final Set<String> followers;
    private final Set<String> following;

    public User(String userId, String username) {
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
class Tweet {
    private final String tweetId;
    private final String userId;
    private final String content;
    private final LocalDateTime timestamp;
    private int likes;
    private int retweets;

    public Tweet(String tweetId, String userId, String content) {
        this.tweetId = tweetId;
        this.userId = userId;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.likes = 0;
        this.retweets = 0;
    }

    public void like() {
        likes++;
    }

    public void retweet() {
        retweets++;
    }

    public String getTweetId() { return tweetId; }
    public String getUserId() { return userId; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "@" + userId + ": \"" + content + "\" [" + 
               timestamp.toLocalTime() + "] ♥" + likes + " ↻" + retweets;
    }
}

// ==================== Twitter Service ====================
class TwitterService {
    private final Map<String, User> users;
    private final Map<String, Tweet> tweets;
    private final Map<String, List<Tweet>> userTweets;
    private int tweetCounter;

    public TwitterService() {
        this.users = new HashMap<>();
        this.tweets = new HashMap<>();
        this.userTweets = new HashMap<>();
        this.tweetCounter = 1;
    }

    public void registerUser(User user) {
        users.put(user.getUserId(), user);
        userTweets.put(user.getUserId(), new ArrayList<>());
        System.out.println("✓ Registered: " + user);
    }

    public void follow(String followerId, String followeeId) {
        User follower = users.get(followerId);
        User followee = users.get(followeeId);

        if (follower != null && followee != null) {
            follower.follow(followeeId);
            followee.addFollower(followerId);
            System.out.println("✓ @" + follower.getUsername() + " followed @" + 
                             followee.getUsername());
        }
    }

    public Tweet postTweet(String userId, String content) {
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

    public List<Tweet> getNewsFeed(String userId, int limit) {
        User user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        // Get tweets from users they follow + their own tweets
        List<Tweet> feed = new ArrayList<>();

        // Add own tweets
        feed.addAll(userTweets.get(userId));

        // Add tweets from following
        for (String followingId : user.getFollowing()) {
            feed.addAll(userTweets.get(followingId));
        }

        // Sort by timestamp (newest first) and limit
        feed.sort((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()));

        return feed.subList(0, Math.min(limit, feed.size()));
    }

    public void displayNewsFeed(String userId, int limit) {
        System.out.println("\n=== News Feed for @" + users.get(userId).getUsername() + " ===");
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

// ==================== Demo ====================
public class TwitterSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Twitter System Demo ===\n");

        TwitterService twitter = new TwitterService();

        // Register users
        twitter.registerUser(new User("alice", "alice"));
        twitter.registerUser(new User("bob", "bob"));
        twitter.registerUser(new User("charlie", "charlie"));

        // Follow relationships
        System.out.println("\n--- Follow Relationships ---");
        twitter.follow("alice", "bob");
        twitter.follow("alice", "charlie");
        twitter.follow("bob", "charlie");

        // Post tweets
        System.out.println("\n--- Posting Tweets ---");
        Tweet t1 = twitter.postTweet("bob", "Hello Twitter!");
        Tweet t2 = twitter.postTweet("charlie", "Learning system design");
        Tweet t3 = twitter.postTweet("bob", "Second tweet from Bob");
        Tweet t4 = twitter.postTweet("alice", "My first tweet");

        // Like tweets
        System.out.println("\n--- Liking Tweets ---");
        twitter.likeTweet(t1.getTweetId());
        twitter.likeTweet(t1.getTweetId());
        twitter.likeTweet(t2.getTweetId());

        // Display news feeds
        twitter.displayNewsFeed("alice", 10);  // Should see tweets from bob, charlie, and herself
        twitter.displayNewsFeed("bob", 10);    // Should see tweets from charlie and himself

        System.out.println("✅ Demo complete!");
    }
}
