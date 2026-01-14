import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Voting/Like System
 * Time to complete: 35-45 minutes
 * Focus: Vote counting, user tracking, thread safety
 */

// ==================== Vote Type ====================
enum VoteType {
    UPVOTE, DOWNVOTE
}

// ==================== Post ====================
class Post {
    private final String postId;
    private final String content;
    private final String author;
    private final Set<String> upvotes;
    private final Set<String> downvotes;

    public Post(String postId, String content, String author) {
        this.postId = postId;
        this.content = content;
        this.author = author;
        this.upvotes = ConcurrentHashMap.newKeySet();
        this.downvotes = ConcurrentHashMap.newKeySet();
    }

    public synchronized boolean vote(String userId, VoteType type) {
        // Remove previous vote if exists
        upvotes.remove(userId);
        downvotes.remove(userId);

        // Add new vote
        if (type == VoteType.UPVOTE) {
            upvotes.add(userId);
        } else {
            downvotes.add(userId);
        }

        return true;
    }

    public synchronized boolean removeVote(String userId) {
        boolean removed = upvotes.remove(userId) || downvotes.remove(userId);
        return removed;
    }

    public int getScore() {
        return upvotes.size() - downvotes.size();
    }

    public int getUpvoteCount() {
        return upvotes.size();
    }

    public int getDownvoteCount() {
        return downvotes.size();
    }

    public String getPostId() { return postId; }
    public String getContent() { return content; }
    public String getAuthor() { return author; }

    @Override
    public String toString() {
        return "Post[" + postId + "] by @" + author + ": \"" + content + 
               "\" [↑" + upvotes.size() + " ↓" + downvotes.size() + 
               " Score:" + getScore() + "]";
    }
}

// ==================== Comment ====================
class Comment {
    private final String commentId;
    private final String postId;
    private final String content;
    private final String author;
    private final Set<String> likes;

    public Comment(String commentId, String postId, String content, String author) {
        this.commentId = commentId;
        this.postId = postId;
        this.content = content;
        this.author = author;
        this.likes = ConcurrentHashMap.newKeySet();
    }

    public synchronized void like(String userId) {
        likes.add(userId);
    }

    public synchronized void unlike(String userId) {
        likes.remove(userId);
    }

    public int getLikeCount() {
        return likes.size();
    }

    public String getCommentId() { return commentId; }
    public String getPostId() { return postId; }

    @Override
    public String toString() {
        return "Comment by @" + author + ": \"" + content + "\" [♥" + likes.size() + "]";
    }
}

// ==================== Voting Service ====================
class VotingService {
    private final Map<String, Post> posts;
    private final Map<String, Comment> comments;
    private final Map<String, List<Comment>> postComments;
    private int postCounter;
    private int commentCounter;

    public VotingService() {
        this.posts = new ConcurrentHashMap<>();
        this.comments = new ConcurrentHashMap<>();
        this.postComments = new ConcurrentHashMap<>();
        this.postCounter = 1;
        this.commentCounter = 1;
    }

    public Post createPost(String content, String author) {
        String postId = "P" + postCounter++;
        Post post = new Post(postId, content, author);
        posts.put(postId, post);
        postComments.put(postId, new ArrayList<>());
        
        System.out.println("✓ Created: " + post);
        return post;
    }

    public void votePost(String postId, String userId, VoteType type) {
        Post post = posts.get(postId);
        if (post == null) {
            System.out.println("✗ Post not found");
            return;
        }

        post.vote(userId, type);
        String voteSymbol = type == VoteType.UPVOTE ? "↑" : "↓";
        System.out.println("✓ " + voteSymbol + " @" + userId + " voted on " + postId);
    }

    public Comment addComment(String postId, String content, String author) {
        if (!posts.containsKey(postId)) {
            System.out.println("✗ Post not found");
            return null;
        }

        String commentId = "C" + commentCounter++;
        Comment comment = new Comment(commentId, postId, content, author);
        comments.put(commentId, comment);
        postComments.get(postId).add(comment);

        System.out.println("✓ Comment added: " + comment);
        return comment;
    }

    public void likeComment(String commentId, String userId) {
        Comment comment = comments.get(commentId);
        if (comment == null) {
            System.out.println("✗ Comment not found");
            return;
        }

        comment.like(userId);
        System.out.println("✓ ♥ @" + userId + " liked comment " + commentId);
    }

    public List<Post> getTopPosts(int limit) {
        List<Post> allPosts = new ArrayList<>(posts.values());
        allPosts.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        return allPosts.subList(0, Math.min(limit, allPosts.size()));
    }

    public void displayPost(String postId) {
        Post post = posts.get(postId);
        if (post == null) {
            System.out.println("✗ Post not found");
            return;
        }

        System.out.println("\n=== Post Details ===");
        System.out.println(post);
        
        List<Comment> comments = postComments.get(postId);
        if (comments != null && !comments.isEmpty()) {
            System.out.println("\nComments:");
            for (Comment comment : comments) {
                System.out.println("  " + comment);
            }
        }
        System.out.println();
    }

    public void displayTopPosts(int limit) {
        System.out.println("\n=== Top Posts ===");
        List<Post> topPosts = getTopPosts(limit);
        
        for (int i = 0; i < topPosts.size(); i++) {
            System.out.println((i + 1) + ". " + topPosts.get(i));
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class VotingLikeSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Voting/Like System Demo ===\n");

        VotingService service = new VotingService();

        // Create posts
        System.out.println("--- Creating Posts ---");
        Post p1 = service.createPost("Check out this amazing sunset!", "alice");
        Post p2 = service.createPost("Just learned system design", "bob");
        Post p3 = service.createPost("New coffee shop in town", "charlie");

        // Vote on posts
        System.out.println("\n--- Voting on Posts ---");
        service.votePost(p1.getPostId(), "user1", VoteType.UPVOTE);
        service.votePost(p1.getPostId(), "user2", VoteType.UPVOTE);
        service.votePost(p1.getPostId(), "user3", VoteType.UPVOTE);
        
        service.votePost(p2.getPostId(), "user1", VoteType.UPVOTE);
        service.votePost(p2.getPostId(), "user2", VoteType.DOWNVOTE);
        
        service.votePost(p3.getPostId(), "user1", VoteType.DOWNVOTE);

        // Add comments
        System.out.println("\n--- Adding Comments ---");
        Comment c1 = service.addComment(p1.getPostId(), "Beautiful colors!", "user1");
        Comment c2 = service.addComment(p1.getPostId(), "Where was this taken?", "user2");

        // Like comments
        System.out.println("\n--- Liking Comments ---");
        service.likeComment(c1.getCommentId(), "alice");
        service.likeComment(c1.getCommentId(), "bob");
        service.likeComment(c2.getCommentId(), "alice");

        // Display
        service.displayPost(p1.getPostId());
        service.displayTopPosts(3);

        // Change vote
        System.out.println("--- Changing Vote ---");
        service.votePost(p2.getPostId(), "user2", VoteType.UPVOTE);  // Was downvote, now upvote

        service.displayTopPosts(3);

        System.out.println("✅ Demo complete!");
    }
}
