import java.util.*;
import java.time.*;

// ==================== Enums ====================
// Why Enums? Type safety, self-documenting, prevents invalid states

enum PostType {
    PHOTO,
    VIDEO,
    CAROUSEL       // Multiple photos/videos
}

enum NotificationType {
    LIKE,
    COMMENT,
    FOLLOW,
    MENTION,
    TAG
}

enum PrivacyLevel {
    PUBLIC,        // Anyone can see
    PRIVATE,       // Only followers
    FRIENDS        // Close friends only
}

enum MessageStatus {
    SENT,
    DELIVERED,
    READ,
    DELETED
}

// ==================== Media ====================
// Represents a photo or video

class Media {
    private String mediaId;
    private String url;
    private PostType type;
    private int width;
    private int height;
    private long fileSize;
    private LocalDateTime uploadTime;

    public Media(String mediaId, String url, PostType type, int width, int height, long fileSize) {
        this.mediaId = mediaId;
        this.url = url;
        this.type = type;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
        this.uploadTime = LocalDateTime.now();
    }

    public String getMediaId() { return mediaId; }
    public String getUrl() { return url; }
    public PostType getType() { return type; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getFileSize() { return fileSize; }
    public LocalDateTime getUploadTime() { return uploadTime; }

    @Override
    public String toString() {
        return type + " (" + width + "x" + height + ")";
    }
}

// ==================== User ====================
// Represents a user account

class User {
    private String userId;
    private String username;
    private String email;
    private String fullName;
    private String bio;
    private String profilePictureUrl;
    private boolean isVerified;
    private PrivacyLevel privacyLevel;
    private LocalDateTime createdAt;
    
    // Relationships
    private Set<String> followers;        // User IDs following this user
    private Set<String> following;        // User IDs this user follows
    private Set<String> blockedUsers;     // Blocked user IDs
    
    // Content
    private List<Post> posts;
    private List<Story> stories;

    public User(String userId, String username, String email, String fullName) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.bio = "";
        this.isVerified = false;
        this.privacyLevel = PrivacyLevel.PUBLIC;
        this.createdAt = LocalDateTime.now();
        this.followers = new HashSet<>();
        this.following = new HashSet<>();
        this.blockedUsers = new HashSet<>();
        this.posts = new ArrayList<>();
        this.stories = new ArrayList<>();
    }

    // Getters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getBio() { return bio; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public boolean isVerified() { return isVerified; }
    public PrivacyLevel getPrivacyLevel() { return privacyLevel; }
    public Set<String> getFollowers() { return followers; }
    public Set<String> getFollowing() { return following; }
    public List<Post> getPosts() { return posts; }
    public List<Story> getStories() { return stories; }

    // Setters
    public void setBio(String bio) { this.bio = bio; }
    public void setProfilePictureUrl(String url) { this.profilePictureUrl = url; }
    public void setPrivacyLevel(PrivacyLevel level) { this.privacyLevel = level; }
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

    // Content operations
    public void addPost(Post post) {
        posts.add(0, post);  // Add to beginning (newest first)
    }

    public void addStory(Story story) {
        stories.add(story);
    }

    // Stats
    public int getFollowerCount() {
        return followers.size();
    }

    public int getFollowingCount() {
        return following.size();
    }

    public int getPostCount() {
        return posts.size();
    }

    @Override
    public String toString() {
        return "@" + username + " (" + fullName + ")";
    }
}

// ==================== Comment (Composite Pattern) ====================
// Why Composite Pattern? Comments can have replies (nested structure)
// Benefits: Uniform treatment of comments and replies

interface CommentComponent {
    String getCommentId();
    User getAuthor();
    String getText();
    LocalDateTime getCreatedAt();
    int getLikeCount();
    List<CommentComponent> getReplies();
}

class Comment implements CommentComponent {
    private String commentId;
    private User author;
    private String text;
    private LocalDateTime createdAt;
    private Set<String> likedBy;           // User IDs who liked
    private List<CommentComponent> replies; // Nested replies

    public Comment(String commentId, User author, String text) {
        this.commentId = commentId;
        this.author = author;
        this.text = text;
        this.createdAt = LocalDateTime.now();
        this.likedBy = new HashSet<>();
        this.replies = new ArrayList<>();
    }

    @Override
    public String getCommentId() { return commentId; }
    
    @Override
    public User getAuthor() { return author; }
    
    @Override
    public String getText() { return text; }
    
    @Override
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    @Override
    public int getLikeCount() { return likedBy.size(); }
    
    @Override
    public List<CommentComponent> getReplies() { return replies; }

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

    // Reply operations (Composite pattern)
    public void addReply(CommentComponent reply) {
        replies.add(reply);
    }

    public void removeReply(CommentComponent reply) {
        replies.remove(reply);
    }

    @Override
    public String toString() {
        return author.getUsername() + ": " + text + 
               " (" + likeCount() + " likes, " + replies.size() + " replies)";
    }

    private int likeCount() { return likedBy.size(); }
}

// ==================== Post ====================
// Represents a user's post

class Post {
    private String postId;
    private User author;
    private String caption;
    private List<Media> media;
    private LocalDateTime createdAt;
    private PrivacyLevel privacyLevel;
    private String location;
    
    // Engagement
    private Set<String> likedBy;
    private List<CommentComponent> comments;
    private Set<String> savedBy;
    private Set<String> taggedUsers;

    public Post(String postId, User author, String caption, List<Media> media) {
        this.postId = postId;
        this.author = author;
        this.caption = caption;
        this.media = new ArrayList<>(media);
        this.createdAt = LocalDateTime.now();
        this.privacyLevel = author.getPrivacyLevel();
        this.likedBy = new HashSet<>();
        this.comments = new ArrayList<>();
        this.savedBy = new HashSet<>();
        this.taggedUsers = new HashSet<>();
    }

    // Getters
    public String getPostId() { return postId; }
    public User getAuthor() { return author; }
    public String getCaption() { return caption; }
    public List<Media> getMedia() { return media; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public PrivacyLevel getPrivacyLevel() { return privacyLevel; }
    public String getLocation() { return location; }
    public Set<String> getLikedBy() { return likedBy; }
    public List<CommentComponent> getComments() { return comments; }
    public Set<String> getTaggedUsers() { return taggedUsers; }

    // Setters
    public void setLocation(String location) { this.location = location; }
    public void setPrivacyLevel(PrivacyLevel level) { this.privacyLevel = level; }

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

    // Comment operations
    public void addComment(CommentComponent comment) {
        comments.add(comment);
    }

    public void removeComment(CommentComponent comment) {
        comments.remove(comment);
    }

    // Save operations
    public void addSave(String userId) {
        savedBy.add(userId);
    }

    public void removeSave(String userId) {
        savedBy.remove(userId);
    }

    // Tag operations
    public void tagUser(String userId) {
        taggedUsers.add(userId);
    }

    public void untagUser(String userId) {
        taggedUsers.remove(userId);
    }

    // Stats
    public int getLikeCount() {
        return likedBy.size();
    }

    public int getCommentCount() {
        return comments.size();
    }

    public int getSaveCount() {
        return savedBy.size();
    }

    @Override
    public String toString() {
        return String.format("Post[%s by %s, %d likes, %d comments]",
            postId, author.getUsername(), getLikeCount(), getCommentCount());
    }
}

// ==================== Story ====================
// Represents a 24-hour story

class Story {
    private String storyId;
    private User author;
    private Media media;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Set<String> viewedBy;

    public Story(String storyId, User author, Media media) {
        this.storyId = storyId;
        this.author = author;
        this.media = media;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusHours(24);  // Stories expire after 24 hours
        this.viewedBy = new HashSet<>();
    }

    public String getStoryId() { return storyId; }
    public User getAuthor() { return author; }
    public Media getMedia() { return media; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Set<String> getViewedBy() { return viewedBy; }

    public void addView(String userId) {
        viewedBy.add(userId);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public int getViewCount() {
        return viewedBy.size();
    }

    @Override
    public String toString() {
        return String.format("Story[%s by %s, %d views, expires at %s]",
            storyId, author.getUsername(), getViewCount(), expiresAt);
    }
}

// ==================== Direct Message ====================
// Represents a message between users

class DirectMessage {
    private String messageId;
    private User sender;
    private User receiver;
    private String content;
    private LocalDateTime sentTime;
    private MessageStatus status;
    private LocalDateTime deliveredTime;
    private LocalDateTime readTime;

    public DirectMessage(String messageId, User sender, User receiver, String content) {
        this.messageId = messageId;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.sentTime = LocalDateTime.now();
        this.status = MessageStatus.SENT;
    }

    public String getMessageId() { return messageId; }
    public User getSender() { return sender; }
    public User getReceiver() { return receiver; }
    public String getContent() { return content; }
    public LocalDateTime getSentTime() { return sentTime; }
    public MessageStatus getStatus() { return status; }

    public void markDelivered() {
        this.status = MessageStatus.DELIVERED;
        this.deliveredTime = LocalDateTime.now();
    }

    public void markRead() {
        this.status = MessageStatus.READ;
        this.readTime = LocalDateTime.now();
    }

    public void delete() {
        this.status = MessageStatus.DELETED;
    }

    @Override
    public String toString() {
        return String.format("Message[%s -> %s: %s (%s)]",
            sender.getUsername(), receiver.getUsername(), 
            content.substring(0, Math.min(20, content.length())), status);
    }
}

// ==================== Notification (Observer Pattern) ====================
// Why Observer Pattern? Users need to be notified of various events
// Benefits: Loose coupling, easy to add new notification types

interface NotificationObserver {
    void update(Notification notification);
}

class Notification {
    private String notificationId;
    private NotificationType type;
    private User actor;              // User who performed the action
    private User recipient;           // User receiving notification
    private String entityId;          // ID of post/comment/etc
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return isRead; }

    public void markAsRead() {
        this.isRead = true;
    }

    @Override
    public String toString() {
        return String.format("%s: %s %s", 
            type, actor.getUsername(), getActionText());
    }

    private String getActionText() {
        switch (type) {
            case LIKE: return "liked your post";
            case COMMENT: return "commented on your post";
            case FOLLOW: return "started following you";
            case MENTION: return "mentioned you";
            case TAG: return "tagged you in a post";
            default: return "";
        }
    }
}

// Concrete observer - In-App notification
class InAppNotificationObserver implements NotificationObserver {
    @Override
    public void update(Notification notification) {
        System.out.println("🔔 " + notification);
    }
}

// Concrete observer - Push notification
class PushNotificationObserver implements NotificationObserver {
    @Override
    public void update(Notification notification) {
        System.out.println("📱 Push: " + notification);
    }
}

// ==================== Feed Strategy (Strategy Pattern) ====================
// Why Strategy Pattern? Different feed algorithms (chronological vs algorithmic)
// Benefits: Interchangeable algorithms, easy to A/B test

interface FeedStrategy {
    List<Post> generateFeed(User user, List<Post> allPosts);
}

// Strategy 1: Chronological feed (simple time-based)
class ChronologicalFeedStrategy implements FeedStrategy {
    @Override
    public List<Post> generateFeed(User user, List<Post> allPosts) {
        List<Post> feed = new ArrayList<>();
        
        // Get posts from users we follow
        for (Post post : allPosts) {
            if (user.getFollowing().contains(post.getAuthor().getUserId()) ||
                post.getAuthor().getUserId().equals(user.getUserId())) {
                feed.add(post);
            }
        }
        
        // Sort by time (newest first)
        feed.sort((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()));
        
        return feed;
    }
}

// Strategy 2: Engagement-based feed (algorithmic)
class AlgorithmicFeedStrategy implements FeedStrategy {
    @Override
    public List<Post> generateFeed(User user, List<Post> allPosts) {
        List<Post> feed = new ArrayList<>();
        
        // Get posts from users we follow
        for (Post post : allPosts) {
            if (user.getFollowing().contains(post.getAuthor().getUserId()) ||
                post.getAuthor().getUserId().equals(user.getUserId())) {
                feed.add(post);
            }
        }
        
        // Sort by engagement score (likes + comments)
        feed.sort((p1, p2) -> {
            int score1 = p1.getLikeCount() + p1.getCommentCount() * 2;
            int score2 = p2.getLikeCount() + p2.getCommentCount() * 2;
            return Integer.compare(score2, score1);  // Higher engagement first
        });
        
        return feed;
    }
}

// ==================== Instagram Service (Singleton) ====================
// Why Singleton? Central service coordinator for entire platform
// Manages users, posts, relationships, notifications

class InstagramService {
    private static InstagramService instance;
    private Map<String, User> users;              // username -> User
    private Map<String, Post> posts;              // postId -> Post
    private Map<String, Story> stories;           // storyId -> Story
    private List<DirectMessage> messages;
    private List<Notification> notifications;
    private List<NotificationObserver> observers;
    private FeedStrategy feedStrategy;
    private int postCounter;
    private int commentCounter;
    private int storyCounter;
    private int messageCounter;
    private int notificationCounter;

    // Private constructor - Singleton
    private InstagramService() {
        this.users = new HashMap<>();
        this.posts = new HashMap<>();
        this.stories = new HashMap<>();
        this.messages = new ArrayList<>();
        this.notifications = new ArrayList<>();
        this.observers = new ArrayList<>();
        this.feedStrategy = new ChronologicalFeedStrategy();  // Default
        this.postCounter = 0;
        this.commentCounter = 0;
        this.storyCounter = 0;
        this.messageCounter = 0;
        this.notificationCounter = 0;
    }

    // Thread-safe singleton
    public static synchronized InstagramService getInstance() {
        if (instance == null) {
            instance = new InstagramService();
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

    // Set feed strategy
    public void setFeedStrategy(FeedStrategy strategy) {
        this.feedStrategy = strategy;
    }

    // Create user
    public User createUser(String username, String email, String fullName) {
        if (users.containsKey(username)) {
            System.out.println("Username already exists!");
            return null;
        }

        String userId = "USER-" + System.currentTimeMillis();
        User user = new User(userId, username, email, fullName);
        users.put(username, user);
        System.out.println("✅ User created: @" + username);
        return user;
    }

    // Get user
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

        System.out.println(follower.getUsername() + " followed " + followee.getUsername());
    }

    // Unfollow user
    public void unfollowUser(User follower, User followee) {
        follower.removeFollowing(followee.getUserId());
        followee.removeFollower(follower.getUserId());
        System.out.println(follower.getUsername() + " unfollowed " + followee.getUsername());
    }

    // Create post
    public Post createPost(User author, String caption, List<Media> media) {
        String postId = "POST-" + (++postCounter);
        Post post = new Post(postId, author, caption, media);
        posts.put(postId, post);
        author.addPost(post);
        System.out.println("✅ Post created by @" + author.getUsername());
        return post;
    }

    // Like post
    public void likePost(User user, Post post) {
        if (post.isLikedBy(user.getUserId())) {
            System.out.println("Already liked this post!");
            return;
        }

        post.addLike(user.getUserId());

        // Notify post author
        if (!post.getAuthor().getUserId().equals(user.getUserId())) {
            String notifId = "NOTIF-" + (++notificationCounter);
            Notification notification = new Notification(
                notifId, NotificationType.LIKE, user, post.getAuthor(), post.getPostId()
            );
            notifyObservers(notification);
        }

        System.out.println(user.getUsername() + " liked post " + post.getPostId());
    }

    // Unlike post
    public void unlikePost(User user, Post post) {
        post.removeLike(user.getUserId());
        System.out.println(user.getUsername() + " unliked post " + post.getPostId());
    }

    // Comment on post
    public Comment commentOnPost(User user, Post post, String text) {
        String commentId = "COMMENT-" + (++commentCounter);
        Comment comment = new Comment(commentId, user, text);
        post.addComment(comment);

        // Notify post author
        if (!post.getAuthor().getUserId().equals(user.getUserId())) {
            String notifId = "NOTIF-" + (++notificationCounter);
            Notification notification = new Notification(
                notifId, NotificationType.COMMENT, user, post.getAuthor(), post.getPostId()
            );
            notifyObservers(notification);
        }

        System.out.println(user.getUsername() + " commented on post " + post.getPostId());
        return comment;
    }

    // Reply to comment
    public Comment replyToComment(User user, CommentComponent parentComment, String text) {
        String commentId = "COMMENT-" + (++commentCounter);
        Comment reply = new Comment(commentId, user, text);
        
        if (parentComment instanceof Comment) {
            ((Comment) parentComment).addReply(reply);
        }

        System.out.println(user.getUsername() + " replied to comment");
        return reply;
    }

    // Create story
    public Story createStory(User author, Media media) {
        String storyId = "STORY-" + (++storyCounter);
        Story story = new Story(storyId, author, media);
        stories.put(storyId, story);
        author.addStory(story);
        System.out.println("✅ Story created by @" + author.getUsername());
        return story;
    }

    // View story
    public void viewStory(User viewer, Story story) {
        story.addView(viewer.getUserId());
        System.out.println(viewer.getUsername() + " viewed story " + story.getStoryId());
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
        message.markDelivered();
        System.out.println("💬 Message sent from " + sender.getUsername() + 
                         " to " + receiver.getUsername());
        return message;
    }

    // Generate feed
    public List<Post> getFeed(User user) {
        return feedStrategy.generateFeed(user, new ArrayList<>(posts.values()));
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

    // Clean up expired stories
    public void cleanupExpiredStories() {
        List<String> expiredIds = new ArrayList<>();
        for (Story story : stories.values()) {
            if (story.isExpired()) {
                expiredIds.add(story.getStoryId());
            }
        }
        
        for (String id : expiredIds) {
            stories.remove(id);
        }
        
        if (!expiredIds.isEmpty()) {
            System.out.println("🧹 Cleaned up " + expiredIds.size() + " expired stories");
        }
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates complete Instagram functionality

public class InstagramSystem {
    public static void main(String[] args) {
        // Initialize Instagram Service (Singleton)
        InstagramService instagram = InstagramService.getInstance();

        // Add notification observers
        instagram.addObserver(new InAppNotificationObserver());
        instagram.addObserver(new PushNotificationObserver());

        System.out.println("========== Instagram System Demo ==========\n");

        // ===== Create Users =====
        System.out.println("--- Creating Users ---");
        User alice = instagram.createUser("alice", "alice@example.com", "Alice Smith");
        User bob = instagram.createUser("bob", "bob@example.com", "Bob Johnson");
        User charlie = instagram.createUser("charlie", "charlie@example.com", "Charlie Brown");

        // ===== Follow Operations =====
        System.out.println("\n--- Follow Operations ---");
        instagram.followUser(bob, alice);
        instagram.followUser(charlie, alice);
        instagram.followUser(charlie, bob);

        System.out.println("\nFollower counts:");
        System.out.println("Alice: " + alice.getFollowerCount() + " followers");
        System.out.println("Bob: " + bob.getFollowerCount() + " followers");

        // ===== Create Posts =====
        System.out.println("\n--- Creating Posts ---");
        Media photo1 = new Media("MEDIA-1", "https://example.com/photo1.jpg", 
                                PostType.PHOTO, 1080, 1080, 1024 * 500);
        
        Post alicePost = instagram.createPost(
            alice,
            "Beautiful sunset! 🌅 #nature",
            Arrays.asList(photo1)
        );

        Media photo2 = new Media("MEDIA-2", "https://example.com/photo2.jpg",
                                PostType.PHOTO, 1080, 1350, 1024 * 600);
        
        Post bobPost = instagram.createPost(
            bob,
            "Great day at the beach! ☀️",
            Arrays.asList(photo2)
        );

        // ===== Likes and Comments =====
        System.out.println("\n--- Likes and Comments ---");
        instagram.likePost(bob, alicePost);
        instagram.likePost(charlie, alicePost);

        Comment comment1 = instagram.commentOnPost(bob, alicePost, "Amazing photo!");
        Comment comment2 = instagram.commentOnPost(charlie, alicePost, "Love it!");
        
        // Reply to comment (Composite pattern)
        instagram.replyToComment(alice, comment1, "Thanks Bob!");

        System.out.println("\nPost stats:");
        System.out.println(alicePost.getPostId() + ": " + alicePost.getLikeCount() + 
                         " likes, " + alicePost.getCommentCount() + " comments");

        // ===== Create Stories =====
        System.out.println("\n--- Creating Stories ---");
        Media storyMedia = new Media("MEDIA-3", "https://example.com/story1.jpg",
                                    PostType.PHOTO, 1080, 1920, 1024 * 400);
        
        Story aliceStory = instagram.createStory(alice, storyMedia);
        instagram.viewStory(bob, aliceStory);
        instagram.viewStory(charlie, aliceStory);

        System.out.println("Story views: " + aliceStory.getViewCount());

        // ===== Direct Messaging =====
        System.out.println("\n--- Direct Messaging ---");
        DirectMessage dm1 = instagram.sendMessage(bob, alice, "Hey! Great post!");
        if (dm1 != null) {
            dm1.markRead();
            System.out.println("Message status: " + dm1.getStatus());
        }

        // ===== Generate Feed =====
        System.out.println("\n--- Generating Feed ---");
        List<Post> bobsFeed = instagram.getFeed(bob);
        System.out.println("Bob's feed has " + bobsFeed.size() + " posts:");
        for (Post post : bobsFeed) {
            System.out.println("  - " + post);
        }

        // ===== Check Notifications =====
        System.out.println("\n--- Checking Notifications ---");
        List<Notification> aliceNotifs = instagram.getUnreadNotifications(alice);
        System.out.println("Alice has " + aliceNotifs.size() + " unread notifications");

        // ===== User Stats =====
        System.out.println("\n--- User Stats ---");
        System.out.println(alice + ":");
        System.out.println("  Followers: " + alice.getFollowerCount());
        System.out.println("  Following: " + alice.getFollowingCount());
        System.out.println("  Posts: " + alice.getPostCount());

        // ===== Switch to Algorithmic Feed =====
        System.out.println("\n--- Switching to Algorithmic Feed ---");
        instagram.setFeedStrategy(new AlgorithmicFeedStrategy());
        List<Post> algorithmicFeed = instagram.getFeed(bob);
        System.out.println("Bob's algorithmic feed: " + algorithmicFeed.size() + " posts");

        System.out.println("\n========== Demo Complete ==========");
    }
}
