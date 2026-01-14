import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Instagram System
 * Time to complete: 45-60 minutes
 * Focus: Observer pattern for feed, like/comment features
 */

// ==================== User ====================
class InstaUser {
    private final String userId;
    private final String username;
    private final Set<String> followers;
    private final Set<String> following;

    public InstaUser(String userId, String username) {
        this.userId = userId;
        this.username = username;
        this.followers = new HashSet<>();
        this.following = new HashSet<>();
    }

    public void follow(String targetId) {
        following.add(targetId);
    }

    public void addFollower(String followerId) {
        followers.add(followerId);
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public Set<String> getFollowing() { return new HashSet<>(following); }

    @Override
    public String toString() {
        return "@" + username + " (Followers: " + followers.size() + ")";
    }
}

// ==================== Comment ====================
class InstaComment {
    private final String commentId;
    private final String userId;
    private final String text;
    private final LocalDateTime timestamp;

    public InstaComment(String commentId, String userId, String text) {
        this.commentId = commentId;
        this.userId = userId;
        this.text = text;
        this.timestamp = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "@" + userId + ": " + text;
    }
}

// ==================== Post ====================
class InstaPost {
    private final String postId;
    private final String userId;
    private final String imageURL;
    private final String caption;
    private final LocalDateTime timestamp;
    private final Set<String> likes;
    private final List<InstaComment> comments;

    public InstaPost(String postId, String userId, String imageURL, String caption) {
        this.postId = postId;
        this.userId = userId;
        this.imageURL = imageURL;
        this.caption = caption;
        this.timestamp = LocalDateTime.now();
        this.likes = new HashSet<>();
        this.comments = new ArrayList<>();
    }

    public void like(String userId) {
        likes.add(userId);
    }

    public void unlike(String userId) {
        likes.remove(userId);
    }

    public void addComment(InstaComment comment) {
        comments.add(comment);
    }

    public String getPostId() { return postId; }
    public String getUserId() { return userId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public int getLikeCount() { return likes.size(); }
    public int getCommentCount() { return comments.size(); }

    @Override
    public String toString() {
        return "@" + userId + ": " + caption + " [♥" + likes.size() + " 💬" + comments.size() + "]";
    }
}

// ==================== Instagram Service ====================
class InstagramService {
    private final Map<String, InstaUser> users;
    private final Map<String, InstaPost> posts;
    private final Map<String, List<InstaPost>> userPosts;
    private int postCounter;
    private int commentCounter;

    public InstagramService() {
        this.users = new HashMap<>();
        this.posts = new HashMap<>();
        this.userPosts = new HashMap<>();
        this.postCounter = 1;
        this.commentCounter = 1;
    }

    public void registerUser(InstaUser user) {
        users.put(user.getUserId(), user);
        userPosts.put(user.getUserId(), new ArrayList<>());
        System.out.println("✓ Registered: " + user);
    }

    public void follow(String followerId, String followeeId) {
        InstaUser follower = users.get(followerId);
        InstaUser followee = users.get(followeeId);

        if (follower != null && followee != null) {
            follower.follow(followeeId);
            followee.addFollower(followerId);
            System.out.println("✓ " + follower.getUsername() + " followed " + followee.getUsername());
        }
    }

    public InstaPost createPost(String userId, String imageURL, String caption) {
        String postId = "P" + postCounter++;
        InstaPost post = new InstaPost(postId, userId, imageURL, caption);

        posts.put(postId, post);
        userPosts.get(userId).add(post);

        System.out.println("✓ Post created: " + post);
        return post;
    }

    public void likePost(String postId, String userId) {
        InstaPost post = posts.get(postId);
        if (post != null) {
            post.like(userId);
            System.out.println("♥ " + userId + " liked post " + postId);
        }
    }

    public void commentOnPost(String postId, String userId, String text) {
        InstaPost post = posts.get(postId);
        if (post != null) {
            String commentId = "C" + commentCounter++;
            InstaComment comment = new InstaComment(commentId, userId, text);
            post.addComment(comment);
            System.out.println("💬 " + userId + " commented on " + postId);
        }
    }

    public List<InstaPost> getFeed(String userId, int limit) {
        InstaUser user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        List<InstaPost> feed = new ArrayList<>();

        // Add posts from followed users
        for (String followingId : user.getFollowing()) {
            feed.addAll(userPosts.get(followingId));
        }

        // Sort by timestamp (newest first)
        feed.sort((p1, p2) -> p2.getTimestamp().compareTo(p1.getTimestamp()));

        return feed.subList(0, Math.min(limit, feed.size()));
    }

    public void displayFeed(String userId, int limit) {
        System.out.println("\n=== Feed for @" + users.get(userId).getUsername() + " ===");
        List<InstaPost> feed = getFeed(userId, limit);

        if (feed.isEmpty()) {
            System.out.println("No posts to show");
        } else {
            for (int i = 0; i < feed.size(); i++) {
                System.out.println((i + 1) + ". " + feed.get(i));
            }
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class InstagramSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Instagram Demo ===\n");

        InstagramService instagram = new InstagramService();

        // Register users
        instagram.registerUser(new InstaUser("alice", "alice"));
        instagram.registerUser(new InstaUser("bob", "bob"));
        instagram.registerUser(new InstaUser("charlie", "charlie"));

        // Follow
        System.out.println("\n--- Following ---");
        instagram.follow("alice", "bob");
        instagram.follow("alice", "charlie");

        // Create posts
        System.out.println("\n--- Creating Posts ---");
        InstaPost p1 = instagram.createPost("bob", "photo1.jpg", "Beautiful sunset");
        InstaPost p2 = instagram.createPost("charlie", "photo2.jpg", "Coffee time");
        InstaPost p3 = instagram.createPost("bob", "photo3.jpg", "Coding session");

        // Like posts
        System.out.println("\n--- Liking Posts ---");
        instagram.likePost(p1.getPostId(), "alice");
        instagram.likePost(p1.getPostId(), "charlie");
        instagram.likePost(p2.getPostId(), "alice");

        // Comment on posts
        System.out.println("\n--- Commenting ---");
        instagram.commentOnPost(p1.getPostId(), "alice", "Amazing shot!");
        instagram.commentOnPost(p1.getPostId(), "charlie", "Love it!");

        // Display feed
        instagram.displayFeed("alice", 10);

        System.out.println("✅ Demo complete!");
    }
}
