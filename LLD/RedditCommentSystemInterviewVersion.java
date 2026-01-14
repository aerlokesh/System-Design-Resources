import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Reddit Comment System
 * Time to complete: 45-60 minutes
 * Focus: Tree structure for nested comments, voting
 */

// ==================== Comment ====================
class RedditComment {
    private final String commentId;
    private final String content;
    private final String author;
    private final Set<String> upvotes;
    private final Set<String> downvotes;
    private final List<RedditComment> replies;
    private final RedditComment parent;

    public RedditComment(String commentId, String content, String author, RedditComment parent) {
        this.commentId = commentId;
        this.content = content;
        this.author = author;
        this.parent = parent;
        this.upvotes = ConcurrentHashMap.newKeySet();
        this.downvotes = ConcurrentHashMap.newKeySet();
        this.replies = new ArrayList<>();
    }

    public synchronized void vote(String userId, boolean isUpvote) {
        upvotes.remove(userId);
        downvotes.remove(userId);

        if (isUpvote) {
            upvotes.add(userId);
        } else {
            downvotes.add(userId);
        }
    }

    public void addReply(RedditComment reply) {
        replies.add(reply);
        replies.sort((c1, c2) -> Integer.compare(c2.getScore(), c1.getScore()));
    }

    public int getScore() {
        return upvotes.size() - downvotes.size();
    }

    public String getCommentId() { return commentId; }
    public List<RedditComment> getReplies() { return new ArrayList<>(replies); }

    @Override
    public String toString() {
        return "@" + author + ": " + content + " [↑" + upvotes.size() + 
               " ↓" + downvotes.size() + " Score:" + getScore() + "]";
    }
}

// ==================== Post ====================
class RedditPost {
    private final String postId;
    private final String title;
    private final String content;
    private final String author;
    private final List<RedditComment> rootComments;
    private final Set<String> upvotes;
    private final Set<String> downvotes;

    public RedditPost(String postId, String title, String content, String author) {
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.author = author;
        this.rootComments = new ArrayList<>();
        this.upvotes = ConcurrentHashMap.newKeySet();
        this.downvotes = ConcurrentHashMap.newKeySet();
    }

    public synchronized void vote(String userId, boolean isUpvote) {
        upvotes.remove(userId);
        downvotes.remove(userId);

        if (isUpvote) {
            upvotes.add(userId);
        } else {
            downvotes.add(userId);
        }
    }

    public void addRootComment(RedditComment comment) {
        rootComments.add(comment);
        rootComments.sort((c1, c2) -> Integer.compare(c2.getScore(), c1.getScore()));
    }

    public int getScore() {
        return upvotes.size() - downvotes.size();
    }

    public String getPostId() { return postId; }
    public List<RedditComment> getRootComments() { return new ArrayList<>(rootComments); }

    @Override
    public String toString() {
        return title + " by @" + author + " [↑" + upvotes.size() + 
               " ↓" + downvotes.size() + " 💬" + rootComments.size() + "]";
    }
}

// ==================== Reddit Service ====================
class RedditService {
    private final Map<String, RedditPost> posts;
    private final Map<String, RedditComment> comments;
    private int postCounter;
    private int commentCounter;

    public RedditService() {
        this.posts = new ConcurrentHashMap<>();
        this.comments = new ConcurrentHashMap<>();
        this.postCounter = 1;
        this.commentCounter = 1;
    }

    public RedditPost createPost(String title, String content, String author) {
        String postId = "P" + postCounter++;
        RedditPost post = new RedditPost(postId, title, content, author);
        posts.put(postId, post);
        
        System.out.println("✓ Created post: " + title);
        return post;
    }

    public RedditComment addComment(String postId, String content, String author, 
                             String parentCommentId) {
        RedditPost post = posts.get(postId);
        if (post == null) {
            System.out.println("✗ Post not found");
            return null;
        }

        RedditComment parent = parentCommentId != null ? comments.get(parentCommentId) : null;
        
        String commentId = "C" + commentCounter++;
        RedditComment comment = new RedditComment(commentId, content, author, parent);
        comments.put(commentId, comment);

        if (parent == null) {
            post.addRootComment(comment);
            System.out.println("✓ Added root comment by @" + author);
        } else {
            parent.addReply(comment);
            System.out.println("✓ Added reply by @" + author + " to " + parentCommentId);
        }

        return comment;
    }

    public void votePost(String postId, String userId, boolean isUpvote) {
        RedditPost post = posts.get(postId);
        if (post != null) {
            post.vote(userId, isUpvote);
            System.out.println((isUpvote ? "↑" : "↓") + " Vote on post " + postId);
        }
    }

    public void voteComment(String commentId, String userId, boolean isUpvote) {
        RedditComment comment = comments.get(commentId);
        if (comment != null) {
            comment.vote(userId, isUpvote);
            System.out.println((isUpvote ? "↑" : "↓") + " Vote on comment " + commentId);
        }
    }

    public void displayPost(String postId) {
        RedditPost post = posts.get(postId);
        if (post == null) {
            System.out.println("✗ Post not found");
            return;
        }

        System.out.println("\n=== Post ===");
        System.out.println(post);
        System.out.println("\nComments:");
        
        for (RedditComment comment : post.getRootComments()) {
            displayCommentTree(comment, 0);
        }
        System.out.println();
    }

    private void displayCommentTree(RedditComment comment, int level) {
        String indent = "  ".repeat(level);
        System.out.println(indent + "- " + comment);
        
        for (RedditComment reply : comment.getReplies()) {
            displayCommentTree(reply, level + 1);
        }
    }
}

// ==================== Demo ====================
public class RedditCommentSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Reddit Comment System Demo ===\n");

        RedditService reddit = new RedditService();

        // Create post
        System.out.println("--- Creating Post ---");
        RedditPost post = reddit.createPost(
            "What's the best way to learn system design?",
            "I'm preparing for interviews",
            "alice"
        );

        // Vote on post
        System.out.println("\n--- Voting on Post ---");
        reddit.votePost(post.getPostId(), "user1", true);
        reddit.votePost(post.getPostId(), "user2", true);

        // Add root comments
        System.out.println("\n--- Adding Comments ---");
        RedditComment c1 = reddit.addComment(post.getPostId(), 
            "Practice with real examples!", "bob", null);
        
        RedditComment c2 = reddit.addComment(post.getPostId(),
            "Read DDIA book", "charlie", null);

        // Add nested replies
        System.out.println("\n--- Adding Nested Replies ---");
        RedditComment c3 = reddit.addComment(post.getPostId(),
            "Which examples?", "alice", c1.getCommentId());
        
        reddit.addComment(post.getPostId(),
            "LeetCode has good ones", "bob", c3.getCommentId());

        reddit.addComment(post.getPostId(),
            "Also check System Design Interview book", 
            "diana", c2.getCommentId());

        // Vote on comments
        System.out.println("\n--- Voting on Comments ---");
        reddit.voteComment(c1.getCommentId(), "user1", true);
        reddit.voteComment(c2.getCommentId(), "user1", true);

        // Display full thread
        reddit.displayPost(post.getPostId());

        System.out.println("✅ Demo complete!");
    }
}
