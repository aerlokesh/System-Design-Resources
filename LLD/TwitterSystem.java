import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.stream.*;

// ==================== Enums ====================
// Why Enums? Type safety, self-documenting, prevents invalid states
// Better than strings which can have typos and no compile-time validation

enum TweetType {
    ORIGINAL,       // Regular tweet
    RETWEET,        // Share someone else's tweet
    QUOTE_TWEET,    // Retweet with comment
    REPLY           // Reply to another tweet
}

enum NotificationType {
    LIKE,
    RETWEET,
    REPLY,
    MENTION,
    FOLLOW,
    DIRECT_MESSAGE
}

enum TimelineType {
    HOME,           // Tweets from followed users
    USER,           // User's own tweets
    MENTIONS        // Tweets mentioning the user
}

// ==================== User ====================
// Represents a Twitter user account

class User {
    private String userId;
    private String username;        // @username (unique)
    private String displayName;
    private String email;
    private String bio;
    private String profilePictureUrl;
    private boolean isVerified;     // Blue checkmark
    private LocalDateTime createdAt;
    
    // Social graph - Why Set? Fast O(1) lookup for "is following"
    private Set<String> followers;
    private Set<String> following;
    private Set<String> blockedUsers;
    private Set<String> mutedUsers;
    
    // Content
    private List<Tweet> tweets;
    private List<Tweet> likedTweets;

    public User(String userId, String username, String displayName, String email) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.bio = "";
        this.isVerified = false;
        this.createdAt = LocalDateTime.now();
        this.followers = new HashSet<>();
        this.following = new HashSet<>();
        this.blockedUsers = new HashSet<>();
        this.mutedUsers = new HashSet<>();
        this.tweets = new ArrayList<>();
        this.likedTweets = new ArrayList<>();
    }

    // Getters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getBio() { return bio; }
    public boolean isVerified() { return isVerified; }
    public Set<String> getFollowers() { return followers; }
    public Set<String> getFollowing() { return following; }
    public List<Tweet> getTweets() { return tweets; }
    public List<Tweet> getLikedTweets() { return likedTweets; }

    // Setters
    public void setBio(String bio) { this.bio = bio; }
    public void setProfilePictureUrl(String url) { this.profilePictureUrl = url; }
    public void setVerified(boolean verified) { this.isVerified = verified; }

    // Follow operations
    public void addFollower(String userId) {
        followers.add(userId);
    }

    public void removeFollower(String userId) {
        followers.remove(userId);
    }

    public void addFollowing(String userId) {
        following.add(userId);
    }

    public void removeFollowing(String userId) {
        following.remove(userId);
    }

    public boolean isFollowing(String userId) {
        return following.contains(userId);
    }

    // Block operations
    public void blockUser(String userId) {
        blockedUsers.add(userId);
        followers.remove(userId);
        following.remove(userId);
    }

    public void unblockUser(String userId) {
        blockedUsers.remove(userId);
    }

    public boolean hasBlocked(String userId) {
        return blockedUsers.contains(userId);
    }

    // Mute operations
    public void muteUser(String userId) {
        mutedUsers.add(userId);
    }

    public void unmuteUser(String userId) {
        mutedUsers.remove(userId);
    }

    public boolean hasMuted(String userId) {
        return mutedUsers.contains(userId);
    }

    // Content operations
    public void addTweet(Tweet tweet) {
        tweets.add(0, tweet);  // Newest first
    }

    public void likeTweet(Tweet tweet) {
        likedTweets.add(tweet);
    }

    public void unlikeTweet(Tweet tweet) {
        likedTweets.remove(tweet);
    }

    // Stats
    public int getFollowerCount() { return followers.size(); }
    public int getFollowingCount() { return following.size(); }
    public int getTweetCount() { return tweets.size(); }

    @Override
    public String toString() {
        return "@" + username + " (" + displayName + ")";
    }
}

// ==================== Tweet (Composite Pattern) ====================
// Why Composite Pattern? Tweets can have replies forming a tree structure
// Benefits: Uniform treatment of tweets and replies, easy to add nested replies

interface TweetComponent {
    String getTweetId();
    User getAuthor();
    String getContent();
    LocalDateTime getCreatedAt();
    int getLikeCount();
    int getRetweetCount();
    int getReplyCount();
    List<TweetComponent> getReplies();
}

class Tweet implements TweetComponent {
    private String tweetId;
    private User author;
    private String content;
    private TweetType type;
    private LocalDateTime createdAt;
    
    // References
    private String replyToTweetId;      // If this is a reply
    private String retweetOfTweetId;    // If this is a retweet
    private Tweet quotedTweet;          // If this is a quote tweet
    
    // Engagement
    private Set<String> likedBy;
    private Set<String> retweetedBy;
    private List<TweetComponent> replies;  // Nested replies (Composite pattern)
    
    // Metadata
    private List<String> hashtags;
    private List<String> mentionedUsers;
    private String mediaUrl;             // Optional image/video

    public Tweet(String tweetId, User author, String content, TweetType type) {
        this.tweetId = tweetId;
        this.author = author;
        this.content = content;
        this.type = type;
        this.createdAt = LocalDateTime.now();
        this.likedBy = new HashSet<>();
        this.retweetedBy = new HashSet<>();
        this.replies = new ArrayList<>();
        this.hashtags = extractHashtags(content);
        this.mentionedUsers = extractMentions(content);
    }

    // Extract hashtags from content
    private List<String> extractHashtags(String text) {
        List<String> tags = new ArrayList<>();
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.startsWith("#") && word.length() > 1) {
                tags.add(word.substring(1).toLowerCase());
            }
        }
        return tags;
    }

    // Extract mentions from content
    private List<String> extractMentions(String text) {
        List<String> mentions = new ArrayList<>();
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.startsWith("@") && word.length() > 1) {
                mentions.add(word.substring(1));
            }
        }
        return mentions;
    }

    // Getters
    @Override
    public String getTweetId() { return tweetId; }
    
    @Override
    public User getAuthor() { return author; }
    
    @Override
    public String getContent() { return content; }
    
    @Override
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    public TweetType getType() { return type; }
    public String getReplyToTweetId() { return replyToTweetId; }
    public String getRetweetOfTweetId() { return retweetOfTweetId; }
    public Tweet getQuotedTweet() { return quotedTweet; }
    public List<String> getHashtags() { return hashtags; }
    public List<String> getMentionedUsers() { return mentionedUsers; }
    public String getMediaUrl() { return mediaUrl; }

    @Override
    public int getLikeCount() { return likedBy.size(); }
    
    @Override
    public int getRetweetCount() { return retweetedBy.size(); }
    
    @Override
    public int getReplyCount() { return replies.size(); }
    
    @Override
    public List<TweetComponent> getReplies() { return replies; }

    // Setters
    public void setReplyToTweetId(String tweetId) { this.replyToTweetId = tweetId; }
    public void setRetweetOfTweetId(String tweetId) { this.retweetOfTweetId = tweetId; }
    public void setQuotedTweet(Tweet tweet) { this.quotedTweet = tweet; }
    public void setMediaUrl(String url) { this.mediaUrl = url; }

    // Like operations
    public void addLike(String userId) {
        likedBy.add(userId);
    }

    public void removeLike(String userId) {
        likedBy.remove(userId);
    }

    public boolean isLikedBy(String userId) {
        return likedBy.contains(userId);
    }

    // Retweet operations
    public void addRetweet(String userId) {
        retweetedBy.add(userId);
    }

    public void removeRetweet(String userId) {
        retweetedBy.remove(userId);
    }

    public boolean isRetweetedBy(String userId) {
        return retweetedBy.contains(userId);
    }

    // Reply operations (Composite pattern)
    public void addReply(TweetComponent reply) {
        replies.add(reply);
    }

    public void removeReply(TweetComponent reply) {
        replies.remove(reply);
    }

    @Override
    public String toString() {
        String prefix = "";
        if (type == TweetType.RETWEET) prefix = "RT ";
        if (type == TweetType.QUOTE_TWEET) prefix = "QT ";
        if (type == TweetType.REPLY) prefix = "Reply ";
        
        return String.format("%sTweet[%s by @%s: %s (%d❤️ %d🔄 %d💬)]",
            prefix, tweetId, author.getUsername(), 
            content.substring(0, Math.min(50, content.length())),
            getLikeCount(), getRetweetCount(), getReplyCount());
    }
}

// ==================== Timeline Strategy (Strategy Pattern) ====================
// Why Strategy Pattern? Different timeline algorithms
// Benefits: Interchangeable, testable, easy to switch

interface TimelineStrategy {
    List<Tweet> generateTimeline(User user, List<Tweet> allTweets, int limit);
}

// Strategy 1: Chronological timeline (simple time-based)
class ChronologicalTimelineStrategy implements TimelineStrategy {
    @Override
    public List<Tweet> generateTimeline(User user, List<Tweet> allTweets, int limit) {
        List<Tweet> timeline = new ArrayList<>();
        
        // Get tweets from users we follow + our own tweets
        for (Tweet tweet : allTweets) {
            String authorId = tweet.getAuthor().getUserId();
            if (user.isFollowing(authorId) || 
                authorId.equals(user.getUserId())) {
                // Skip muted users
                if (!user.hasMuted(authorId)) {
                    timeline.add(tweet);
                }
            }
        }
        
        // Sort by time (newest first)
        timeline.sort((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()));
        
        // Limit results
        return timeline.stream().limit(limit).collect(Collectors.toList());
    }
}

// Strategy 2: Engagement-based timeline (algorithmic)
class AlgorithmicTimelineStrategy implements TimelineStrategy {
    @Override
    public List<Tweet> generateTimeline(User user, List<Tweet> allTweets, int limit) {
        List<Tweet> timeline = new ArrayList<>();
        
        // Get tweets from users we follow
        for (Tweet tweet : allTweets) {
            String authorId = tweet.getAuthor().getUserId();
            if (user.isFollowing(authorId) || 
                authorId.equals(user.getUserId())) {
                if (!user.hasMuted(authorId)) {
                    timeline.add(tweet);
                }
            }
        }
        
        // Sort by engagement score
        timeline.sort((t1, t2) -> {
            // Score = likes + (retweets × 2) + (replies × 3)
            int score1 = t1.getLikeCount() + (t1.getRetweetCount() * 2) + (t1.getReplyCount() * 3);
            int score2 = t2.getLikeCount() + (t2.getRetweetCount() * 2) + (t2.getReplyCount() * 3);
            return Integer.compare(score2, score1);  // Higher engagement first
        });
        
        return timeline.stream().limit(limit).collect(Collectors.toList());
    }
}

// ==================== Hashtag ====================
// Tracks hashtags and trending topics

class Hashtag {
    private String tag;
    private int tweetCount;
    private LocalDateTime lastUsed;
    private List<String> tweetIds;  // Recent tweets with this hashtag

    public Hashtag(String tag) {
        this.tag = tag.toLowerCase();
        this.tweetCount = 0;
        this.lastUsed = LocalDateTime.now();
        this.tweetIds = new ArrayList<>();
    }

    public String getTag() { return tag; }
    public int getTweetCount() { return tweetCount; }
    public LocalDateTime getLastUsed() { return lastUsed; }
    public List<String> getTweetIds() { return tweetIds; }

    public void incrementCount() {
        tweetCount++;
        lastUsed = LocalDateTime.now();
    }

    public void addTweet(String tweetId) {
        tweetIds.add(0, tweetId);  // Newest first
        // Keep only last 100 tweets
        if (tweetIds.size() > 100) {
            tweetIds = tweetIds.subList(0, 100);
        }
    }

    @Override
    public String toString() {
        return "#" + tag + " (" + tweetCount + " tweets)";
    }
}

// ==================== Direct Message ====================
// Represents a private message between users

class DirectMessage {
    private String messageId;
    private User sender;
    private User receiver;
    private String content;
    private LocalDateTime sentTime;
    private boolean isRead;

    public DirectMessage(String messageId, User sender, User receiver, String content) {
        this.messageId = messageId;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.sentTime = LocalDateTime.now();
        this.isRead = false;
    }

    public String getMessageId() { return messageId; }
    public User getSender() { return sender; }
    public User getReceiver() { return receiver; }
    public String getContent() { return content; }
    public LocalDateTime getSentTime() { return sentTime; }
    public boolean isRead() { return isRead; }

    public void markAsRead() {
        this.isRead = true;
    }

    @Override
    public String toString() {
        return String.format("DM[%s → %s: %s]",
            sender.getUsername(), receiver.getUsername(),
            content.substring(0, Math.min(30, content.length())));
    }
}

// ==================== Notification (Observer Pattern) ====================
// Why Observer Pattern? Notify users of various events
// Benefits: Loose coupling, easy to add new notification types

interface NotificationObserver {
    void update(Notification notification);
}

class Notification {
    private String notificationId;
    private NotificationType type;
    private User actor;          // User who performed the action
    private User recipient;      // User receiving notification
    private String entityId;     // Tweet/Message ID
    private LocalDateTime createdAt;
    private boolean isRead;

    public Notification(String notificationId, NotificationType type,
                       User actor, User recipient, String entityId) {
        this.notificationId = notificationId;
        this.type = type;
        this.actor = actor;
        this.recipient = recipient;
        this.entityId = entityId;
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
    }

    public String getNotificationId() { return notificationId; }
    public NotificationType getType() { return type; }
    public User getActor() { return actor; }
    public User getRecipient() { return recipient; }
    public String getEntityId() { return entityId; }
    public boolean isRead() { return isRead; }

    public void markAsRead() {
        this.isRead = true;
    }

    @Override
    public String toString() {
        return String.format("🔔 %s: @%s %s",
            type, actor.getUsername(), getActionText());
    }

    private String getActionText() {
        switch (type) {
            case LIKE: return "liked your tweet";
            case RETWEET: return "retweeted your tweet";
            case REPLY: return "replied to your tweet";
            case MENTION: return "mentioned you";
            case FOLLOW: return "followed you";
            case DIRECT_MESSAGE: return "sent you a message";
            default: return "";
        }
    }
}

// Concrete observer implementations
class InAppNotificationObserver implements NotificationObserver {
    @Override
    public void update(Notification notification) {
        System.out.println(notification);
    }
}

class PushNotificationObserver implements NotificationObserver {
    @Override
    public void update(Notification notification) {
        System.out.println("📱 Push: " + notification);
    }
}

// ==================== Trending Topics ====================
// Tracks and ranks trending hashtags

class TrendingTopics {
    // Why PriorityQueue? Efficiently maintain top N trending topics
    private PriorityQueue<Hashtag> trendingQueue;
    private Map<String, Hashtag> hashtags;
    private int maxTrending;

    public TrendingTopics(int maxTrending) {
        this.maxTrending = maxTrending;
        this.hashtags = new ConcurrentHashMap<>();  // Thread-safe
        // Max heap by tweet count
        this.trendingQueue = new PriorityQueue<>(
            (h1, h2) -> Integer.compare(h2.getTweetCount(), h1.getTweetCount())
        );
    }

    // Update trending topics
    public void updateTrending(List<String> newHashtags) {
        for (String tag : newHashtags) {
            Hashtag hashtag = hashtags.computeIfAbsent(tag, k -> new Hashtag(tag));
            hashtag.incrementCount();
        }
        
        // Rebuild trending queue
        rebuildTrendingQueue();
    }

    private void rebuildTrendingQueue() {
        trendingQueue.clear();
        trendingQueue.addAll(hashtags.values());
    }

    // Get top N trending topics
    public List<Hashtag> getTopTrending(int n) {
        List<Hashtag> trending = new ArrayList<>();
        PriorityQueue<Hashtag> temp = new PriorityQueue<>(trendingQueue);
        
        for (int i = 0; i < Math.min(n, temp.size()); i++) {
            Hashtag h = temp.poll();
            if (h != null) {
                trending.add(h);
            }
        }
        
        return trending;
    }

    public Hashtag getHashtag(String tag) {
        return hashtags.get(tag.toLowerCase());
    }
}

// ==================== Twitter Service (Singleton) ====================
// Why Singleton? Central service coordinator for entire platform
// Manages users, tweets, timelines, notifications

class TwitterService {
    private static TwitterService instance;
    private Map<String, User> users;              // username -> User
    private Map<String, Tweet> tweets;            // tweetId -> Tweet
    private List<DirectMessage> messages;
    private List<Notification> notifications;
    private List<NotificationObserver> observers;
    private TrendingTopics trendingTopics;
    private TimelineStrategy timelineStrategy;
    private int tweetCounter;
    private int messageCounter;
    private int notificationCounter;

    // Private constructor - Singleton pattern
    private TwitterService() {
        this.users = new ConcurrentHashMap<>();  // Thread-safe
        this.tweets = new ConcurrentHashMap<>();
        this.messages = new ArrayList<>();
        this.notifications = new ArrayList<>();
        this.observers = new ArrayList<>();
        this.trendingTopics = new TrendingTopics(10);
        this.timelineStrategy = new ChronologicalTimelineStrategy();  // Default
        this.tweetCounter = 0;
        this.messageCounter = 0;
        this.notificationCounter = 0;
    }

    // Thread-safe singleton
    public static synchronized TwitterService getInstance() {
        if (instance == null) {
            instance = new TwitterService();
        }
        return instance;
    }

    // Add notification observer
    public void addObserver(NotificationObserver observer) {
        observers.add(observer);
    }

    // Notify all observers
    private void notifyObservers(Notification notification) {
        notifications.add(notification);
        for (NotificationObserver observer : observers) {
            observer.update(notification);
        }
    }

    // Set timeline strategy
    public void setTimelineStrategy(TimelineStrategy strategy) {
        this.timelineStrategy = strategy;
    }

    // Create user
    public User createUser(String username, String displayName, String email) {
        if (users.containsKey(username)) {
            System.out.println("Username already exists!");
            return null;
        }

        String userId = "USER-" + System.currentTimeMillis();
        User user = new User(userId, username, displayName, email);
        users.put(username, user);
        System.out.println("✅ User created: @" + username);
        return user;
    }

    // Get user by username
    public User getUser(String username) {
        return users.get(username);
    }

    // Follow user
    public void followUser(User follower, User followee) {
        if (follower.hasBlocked(followee.getUserId()) ||
            followee.hasBlocked(follower.getUserId())) {
            System.out.println("Cannot follow blocked user!");
            return;
        }

        follower.addFollowing(followee.getUserId());
        followee.addFollower(follower.getUserId());

        // Notify
        String notifId = "NOTIF-" + (++notificationCounter);
        Notification notification = new Notification(
            notifId, NotificationType.FOLLOW, follower, followee, null
        );
        notifyObservers(notification);

        System.out.println("@" + follower.getUsername() + " followed @" + followee.getUsername());
    }

    // Unfollow user
    public void unfollowUser(User follower, User followee) {
        follower.removeFollowing(followee.getUserId());
        followee.removeFollower(follower.getUserId());
        System.out.println("@" + follower.getUsername() + " unfollowed @" + followee.getUsername());
    }

    // Post tweet
    public Tweet postTweet(User author, String content) {
        if (content.length() > 280) {
            System.out.println("Tweet exceeds 280 character limit!");
            return null;
        }

        String tweetId = "TWEET-" + (++tweetCounter);
        Tweet tweet = new Tweet(tweetId, author, content, TweetType.ORIGINAL);
        tweets.put(tweetId, tweet);
        author.addTweet(tweet);

        // Update trending topics
        if (!tweet.getHashtags().isEmpty()) {
            trendingTopics.updateTrending(tweet.getHashtags());
        }

        // Notify mentioned users
        for (String mentionedUsername : tweet.getMentionedUsers()) {
            User mentioned = users.get(mentionedUsername);
            if (mentioned != null && !mentioned.getUserId().equals(author.getUserId())) {
                String notifId = "NOTIF-" + (++notificationCounter);
                Notification notification = new Notification(
                    notifId, NotificationType.MENTION, author, mentioned, tweetId
                );
                notifyObservers(notification);
            }
        }

        System.out.println("✅ Tweet posted by @" + author.getUsername());
        return tweet;
    }

    // Like tweet
    public void likeTweet(User user, Tweet tweet) {
        if (tweet.isLikedBy(user.getUserId())) {
            System.out.println("Already liked this tweet!");
            return;
        }

        tweet.addLike(user.getUserId());
        user.likeTweet(tweet);

        // Notify tweet author
        if (!tweet.getAuthor().getUserId().equals(user.getUserId())) {
            String notifId = "NOTIF-" + (++notificationCounter);
            Notification notification = new Notification(
                notifId, NotificationType.LIKE, user, tweet.getAuthor(), tweet.getTweetId()
            );
            notifyObservers(notification);
        }

        System.out.println("@" + user.getUsername() + " liked tweet " + tweet.getTweetId());
    }

    // Unlike tweet
    public void unlikeTweet(User user, Tweet tweet) {
        tweet.removeLike(user.getUserId());
        user.unlikeTweet(tweet);
        System.out.println("@" + user.getUsername() + " unliked tweet");
    }

    // Retweet
    public Tweet retweet(User user, Tweet originalTweet) {
        if (originalTweet.isRetweetedBy(user.getUserId())) {
            System.out.println("Already retweeted!");
            return null;
        }

        String tweetId = "TWEET-" + (++tweetCounter);
        Tweet retweet = new Tweet(tweetId, user, originalTweet.getContent(), TweetType.RETWEET);
        retweet.setRetweetOfTweetId(originalTweet.getTweetId());
        
        tweets.put(tweetId, retweet);
        user.addTweet(retweet);
        originalTweet.addRetweet(user.getUserId());

        // Notify original author
        if (!originalTweet.getAuthor().getUserId().equals(user.getUserId())) {
            String notifId = "NOTIF-" + (++notificationCounter);
            Notification notification = new Notification(
                notifId, NotificationType.RETWEET, user, 
                originalTweet.getAuthor(), originalTweet.getTweetId()
            );
            notifyObservers(notification);
        }

        System.out.println("@" + user.getUsername() + " retweeted");
        return retweet;
    }

    // Quote tweet (retweet with comment)
    public Tweet quoteTweet(User user, Tweet originalTweet, String comment) {
        String tweetId = "TWEET-" + (++tweetCounter);
        Tweet quoteTweet = new Tweet(tweetId, user, comment, TweetType.QUOTE_TWEET);
        quoteTweet.setQuotedTweet(originalTweet);
        
        tweets.put(tweetId, quoteTweet);
        user.addTweet(quoteTweet);
        originalTweet.addRetweet(user.getUserId());

        System.out.println("@" + user.getUsername() + " quote tweeted");
        return quoteTweet;
    }

    // Reply to tweet (Composite pattern)
    public Tweet replyToTweet(User user, Tweet originalTweet, String content) {
        String tweetId = "TWEET-" + (++tweetCounter);
        Tweet reply = new Tweet(tweetId, user, content, TweetType.REPLY);
        reply.setReplyToTweetId(originalTweet.getTweetId());
        
        tweets.put(tweetId, reply);
        user.addTweet(reply);
        originalTweet.addReply(reply);  // Composite pattern - add to parent

        // Notify original author
        if (!originalTweet.getAuthor().getUserId().equals(user.getUserId())) {
            String notifId = "NOTIF-" + (++notificationCounter);
            Notification notification = new Notification(
                notifId, NotificationType.REPLY, user,
                originalTweet.getAuthor(), originalTweet.getTweetId()
            );
            notifyObservers(notification);
        }

        System.out.println("@" + user.getUsername() + " replied to tweet");
        return reply;
    }

    // Get timeline
    public List<Tweet> getTimeline(User user, int limit) {
        return timelineStrategy.generateTimeline(user, 
            new ArrayList<>(tweets.values()), limit);
    }

    // Get user's tweets
    public List<Tweet> getUserTweets(User user, int limit) {
        return user.getTweets().stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    // Search tweets by hashtag
    public List<Tweet> searchByHashtag(String tag, int limit) {
        List<Tweet> results = new ArrayList<>();
        String normalizedTag = tag.toLowerCase().replace("#", "");
        
        for (Tweet tweet : tweets.values()) {
            if (tweet.getHashtags().contains(normalizedTag)) {
                results.add(tweet);
            }
        }
        
        // Sort by recency
        results.sort((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()));
        
        return results.stream().limit(limit).collect(Collectors.toList());
    }

    // Search users
    public List<User> searchUsers(String query) {
        List<User> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (User user : users.values()) {
            if (user.getUsername().toLowerCase().contains(lowerQuery) ||
                user.getDisplayName().toLowerCase().contains(lowerQuery)) {
                results.add(user);
            }
        }
        
        return results;
    }

    // Get trending topics
    public List<Hashtag> getTrendingTopics(int limit) {
        return trendingTopics.getTopTrending(limit);
    }

    // Send direct message
    public DirectMessage sendMessage(User sender, User receiver, String content) {
        if (sender.hasBlocked(receiver.getUserId()) ||
            receiver.hasBlocked(sender.getUserId())) {
            System.out.println("Cannot send message to blocked user!");
            return null;
        }

        String messageId = "MSG-" + (++messageCounter);
        DirectMessage message = new DirectMessage(messageId, sender, receiver, content);
        messages.add(message);

        // Notify receiver
        String notifId = "NOTIF-" + (++notificationCounter);
        Notification notification = new Notification(
            notifId, NotificationType.DIRECT_MESSAGE, sender, receiver, messageId
        );
        notifyObservers(notification);

        System.out.println("💬 Message sent from @" + sender.getUsername() +
                         " to @" + receiver.getUsername());
        return message;
    }

    // Get user's unread notifications
    public List<Notification> getUnreadNotifications(User user) {
        List<Notification> unread = new ArrayList<>();
        for (Notification notif : notifications) {
            if (notif.getRecipient().getUserId().equals(user.getUserId()) &&
                !notif.isRead()) {
                unread.add(notif);
            }
        }
        return unread;
    }

    // Get tweet thread (all replies recursively)
    public void printTweetThread(Tweet tweet, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + tweet);
        
        for (TweetComponent reply : tweet.getReplies()) {
            if (reply instanceof Tweet) {
                printTweetThread((Tweet) reply, depth + 1);
            }
        }
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates complete Twitter functionality

public class TwitterSystem {
    public static void main(String[] args) {
        // Initialize Twitter Service (Singleton)
        TwitterService twitter = TwitterService.getInstance();

        // Add notification observers
        twitter.addObserver(new InAppNotificationObserver());
        twitter.addObserver(new PushNotificationObserver());

        System.out.println("========== Twitter System Demo ==========\n");

        // ===== Create Users =====
        System.out.println("--- Creating Users ---");
        User alice = twitter.createUser("alice", "Alice Smith", "alice@example.com");
        User bob = twitter.createUser("bob", "Bob Johnson", "bob@example.com");
        User charlie = twitter.createUser("charlie", "Charlie Brown", "charlie@example.com");

        // ===== Follow Operations =====
        System.out.println("\n--- Follow Operations ---");
        twitter.followUser(bob, alice);
        twitter.followUser(charlie, alice);
        twitter.followUser(alice, bob);

        System.out.println("\nFollower counts:");
        System.out.println("@alice: " + alice.getFollowerCount() + " followers");
        System.out.println("@bob: " + bob.getFollowerCount() + " followers");

        // ===== Post Tweets =====
        System.out.println("\n--- Posting Tweets ---");
        Tweet tweet1 = twitter.postTweet(alice, 
            "Loving the new #Java features! Great for #SystemDesign interviews. @bob check this out!");
        
        Tweet tweet2 = twitter.postTweet(bob,
            "Just finished my #SystemDesign interview prep! Feeling confident 💪");

        Tweet tweet3 = twitter.postTweet(charlie,
            "Beautiful day for coding! ☀️ #programming");

        // ===== Likes and Retweets =====
        System.out.println("\n--- Likes and Retweets ---");
        twitter.likeTweet(bob, tweet1);
        twitter.likeTweet(charlie, tweet1);
        
        Tweet retweet1 = twitter.retweet(bob, tweet3);

        System.out.println("\nTweet stats:");
        System.out.println(tweet1);

        // ===== Quote Tweet =====
        System.out.println("\n--- Quote Tweet ---");
        Tweet quoteTweet = twitter.quoteTweet(alice, tweet2,
            "This is so helpful! Everyone should read this! 🎯");

        // ===== Reply to Tweet (Composite Pattern) =====
        System.out.println("\n--- Replies (Nested Thread) ---");
        Tweet reply1 = twitter.replyToTweet(bob, tweet1, "Thanks for sharing!");
        Tweet reply2 = twitter.replyToTweet(charlie, tweet1, "Very useful!");
        Tweet nestedReply = twitter.replyToTweet(alice, reply1, "Glad you found it helpful!");

        // Print tweet thread
        System.out.println("\nTweet Thread:");
        twitter.printTweetThread(tweet1, 0);

        // ===== Generate Timeline =====
        System.out.println("\n--- Generating Timeline ---");
        List<Tweet> bobsTimeline = twitter.getTimeline(bob, 5);
        System.out.println("@bob's Home Timeline (" + bobsTimeline.size() + " tweets):");
        for (Tweet tweet : bobsTimeline) {
            System.out.println("  " + tweet);
        }

        // ===== Trending Topics =====
        System.out.println("\n--- Trending Topics ---");
        List<Hashtag> trending = twitter.getTrendingTopics(5);
        System.out.println("Top Trending Hashtags:");
        for (int i = 0; i < trending.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + trending.get(i));
        }

        // ===== Search by Hashtag =====
        System.out.println("\n--- Search by Hashtag ---");
        List<Tweet> javaTweets = twitter.searchByHashtag("Java", 10);
        System.out.println("Tweets with #Java: " + javaTweets.size());
        for (Tweet tweet : javaTweets) {
            System.out.println("  " + tweet);
        }

        // ===== Direct Messaging =====
        System.out.println("\n--- Direct Messaging ---");
        DirectMessage dm = twitter.sendMessage(bob, alice,
            "Hey! Love your tweets about system design!");
        if (dm != null) {
            dm.markAsRead();
        }

        // ===== Check Notifications =====
        System.out.println("\n--- Checking Notifications ---");
        List<Notification> aliceNotifs = twitter.getUnreadNotifications(alice);
        System.out.println("@alice has " + aliceNotifs.size() + " unread notifications");

        // ===== User Stats =====
        System.out.println("\n--- User Stats ---");
        System.out.println(alice + ":");
        System.out.println("  Followers: " + alice.getFollowerCount());
        System.out.println("  Following: " + alice.getFollowingCount());
        System.out.println("  Tweets: " + alice.getTweetCount());

        // ===== Switch to Algorithmic Timeline =====
        System.out.println("\n--- Switching to Algorithmic Timeline ---");
        twitter.setTimelineStrategy(new AlgorithmicTimelineStrategy());
        List<Tweet> algorithmicTimeline = twitter.getTimeline(bob, 5);
        System.out.println("@bob's Algorithmic Timeline:");
        for (Tweet tweet : algorithmicTimeline) {
            System.out.println("  " + tweet);
        }

        // ===== Mute User =====
        System.out.println("\n--- Mute User Demo ---");
        bob.muteUser(charlie.getUserId());
        System.out.println("@bob muted @charlie");
        
        List<Tweet> mutedTimeline = twitter.getTimeline(bob, 5);
        System.out.println("@bob's timeline (charlie muted): " + mutedTimeline.size() + " tweets");

        System.out.println("\n========== Demo Complete ==========");
    }
}
