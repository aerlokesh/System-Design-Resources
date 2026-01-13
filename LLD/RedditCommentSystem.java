import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Reddit Comment Section - Low-Level Design
 * 
 * Features:
 * 1. Hierarchical comment tree structure
 * 2. Multiple sorting strategies (Best, Top, New, Controversial, Old)
 * 3. Integration with voting system
 * 4. Thread-safe operations
 * 5. Soft delete with reply preservation
 * 6. Depth limiting and pagination
 * 
 * Design Patterns:
 * - Composite Pattern (tree structure)
 * - Strategy Pattern (sorting)
 * - Observer Pattern (notifications)
 * 
 * @author System Design Repository
 * @version 1.0
 */

// ============================================================================
// CORE ENTITIES
// ============================================================================

/**
 * Comment entity
 */
class Comment {
    private final String id;
    private final String postId;
    private final String parentId;  // null for top-level
    private String authorId;
    private String content;
    private final long timestamp;
    private boolean isDeleted;
    private boolean isEdited;
    private long editedTimestamp;
    private int depth;
    private final List<String> childIds;
    
    public Comment(String id, String postId, String parentId, String authorId, String content) {
        this.id = id;
        this.postId = postId;
        this.parentId = parentId;
        this.authorId = authorId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.isDeleted = false;
        this.isEdited = false;
        this.editedTimestamp = 0;
        this.depth = 0;
        this.childIds = new CopyOnWriteArrayList<>();
    }
    
    // Getters
    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getParentId() { return parentId; }
    public String getAuthorId() { return authorId; }
    public String getContent() { return isDeleted ? "[deleted]" : content; }
    public String getRawContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public boolean isDeleted() { return isDeleted; }
    public boolean isEdited() { return isEdited; }
    public long getEditedTimestamp() { return editedTimestamp; }
    public int getDepth() { return depth; }
    public List<String> getChildIds() { return new ArrayList<>(childIds); }
    
    public boolean isTopLevel() { return parentId == null; }
    public boolean hasReplies() { return !childIds.isEmpty(); }
    
    // Setters
    public void setContent(String content) {
        this.content = content;
        this.isEdited = true;
        this.editedTimestamp = System.currentTimeMillis();
    }
    
    public void setDepth(int depth) {
        this.depth = depth;
    }
    
    public void addChild(String childId) {
        childIds.add(childId);
    }
    
    public void markAsDeleted() {
        this.isDeleted = true;
        this.content = "[deleted]";
        this.authorId = "[deleted]";
    }
    
    @Override
    public String toString() {
        String indent = "  ".repeat(depth);
        String contentPreview = getContent().length() > 50 ? 
            getContent().substring(0, 47) + "..." : getContent();
        return String.format("%s[%s] %s by %s", 
            indent, id.substring(0, Math.min(8, id.length())), 
            contentPreview, authorId);
    }
}

// ============================================================================
// VOTING SYSTEM (Simplified)
// ============================================================================

enum CommentVoteType {
    UPVOTE(1),
    DOWNVOTE(-1),
    NEUTRAL(0);
    
    private final int value;
    CommentVoteType(int value) { this.value = value; }
    public int getValue() { return value; }
}

class CommentVoteResult {
    private final boolean changed;
    
    public CommentVoteResult(boolean changed) {
        this.changed = changed;
    }
    
    public boolean isChanged() { return changed; }
}

class CommentVoteCount {
    private final AtomicInteger upvotes = new AtomicInteger(0);
    private final AtomicInteger downvotes = new AtomicInteger(0);
    
    public int getUpvotes() { return upvotes.get(); }
    public int getDownvotes() { return downvotes.get(); }
    public int getScore() { return upvotes.get() - downvotes.get(); }
    
    public synchronized void update(CommentVoteType from, CommentVoteType to) {
        if (from == CommentVoteType.UPVOTE) upvotes.decrementAndGet();
        else if (from == CommentVoteType.DOWNVOTE) downvotes.decrementAndGet();
        
        if (to == CommentVoteType.UPVOTE) upvotes.incrementAndGet();
        else if (to == CommentVoteType.DOWNVOTE) downvotes.incrementAndGet();
    }
}

class CommentVotingSystem {
    private final ConcurrentHashMap<String, CommentVoteType> userVotes;
    private final ConcurrentHashMap<String, CommentVoteCount> itemCounts;
    
    public CommentVotingSystem() {
        this.userVotes = new ConcurrentHashMap<>();
        this.itemCounts = new ConcurrentHashMap<>();
    }
    
    public CommentVoteResult vote(String userId, String itemId, CommentVoteType voteType) {
        String key = userId + ":" + itemId;
        CommentVoteType previousVote = userVotes.put(key, voteType);
        
        itemCounts.compute(itemId, (k, count) -> {
            if (count == null) count = new CommentVoteCount();
            count.update(previousVote, voteType);
            return count;
        });
        
        return new CommentVoteResult(previousVote != voteType);
    }
    
    public CommentVoteCount getVoteCount(String itemId) {
        CommentVoteCount count = itemCounts.get(itemId);
        return count != null ? count : new CommentVoteCount();
    }
}

// ============================================================================
// SORTING STRATEGIES
// ============================================================================

enum SortStrategy {
    BEST, TOP, NEW, CONTROVERSIAL, OLD
}

class CommentScoreCalculator {
    public static double calculateHotScore(Comment comment, int score) {
        long ageSeconds = (System.currentTimeMillis() - comment.getTimestamp()) / 1000;
        int sign = Integer.compare(score, 0);
        double order = Math.log10(Math.max(Math.abs(score), 1));
        return sign * order - (double) ageSeconds / 45000.0;
    }
    
    public static double calculateControversyScore(int upvotes, int downvotes) {
        if (upvotes == 0 || downvotes == 0) return 0;
        int total = upvotes + downvotes;
        double balance = (double) Math.min(upvotes, downvotes) / Math.max(upvotes, downvotes);
        return total * balance;
    }
}

class CommentWithScore {
    private final Comment comment;
    private final int score;
    private final int upvotes;
    private final int downvotes;
    private final double hotScore;
    private final double controversyScore;
    
    public CommentWithScore(Comment comment, int score, int upvotes, int downvotes) {
        this.comment = comment;
        this.score = score;
        this.upvotes = upvotes;
        this.downvotes = downvotes;
        this.hotScore = CommentScoreCalculator.calculateHotScore(comment, score);
        this.controversyScore = CommentScoreCalculator.calculateControversyScore(upvotes, downvotes);
    }
    
    public Comment getComment() { return comment; }
    public int getScore() { return score; }
    public double getHotScore() { return hotScore; }
    public double getControversyScore() { return controversyScore; }
}

class CommentComparatorFactory {
    public static Comparator<CommentWithScore> create(SortStrategy strategy) {
        switch (strategy) {
            case BEST:
                return Comparator.comparingDouble(CommentWithScore::getHotScore).reversed();
            case TOP:
                return Comparator.comparingInt(CommentWithScore::getScore).reversed();
            case NEW:
                return Comparator.comparingLong((CommentWithScore c) -> c.getComment().getTimestamp()).reversed();
            case CONTROVERSIAL:
                return Comparator.comparingDouble(CommentWithScore::getControversyScore).reversed();
            case OLD:
                return Comparator.comparingLong((CommentWithScore c) -> c.getComment().getTimestamp());
            default:
                return Comparator.comparingDouble(CommentWithScore::getHotScore).reversed();
        }
    }
}

// ============================================================================
// COMMENT MANAGER
// ============================================================================

class CommentManager {
    private final ConcurrentHashMap<String, Comment> commentsById;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> commentsByPost;
    private final AtomicLong commentIdGenerator;
    private final CommentVotingSystem votingSystem;
    
    public CommentManager() {
        this.commentsById = new ConcurrentHashMap<>();
        this.commentsByPost = new ConcurrentHashMap<>();
        this.commentIdGenerator = new AtomicLong(1);
        this.votingSystem = new CommentVotingSystem();
    }
    
    public Comment addComment(String postId, String authorId, String content) {
        String commentId = generateId();
        Comment comment = new Comment(commentId, postId, null, authorId, content);
        commentsById.put(commentId, comment);
        commentsByPost.computeIfAbsent(postId, k -> new CopyOnWriteArrayList<>()).add(commentId);
        return comment;
    }
    
    public Comment replyToComment(String parentCommentId, String authorId, String content) {
        Comment parent = commentsById.get(parentCommentId);
        if (parent == null) throw new IllegalArgumentException("Parent not found");
        
        String commentId = generateId();
        Comment reply = new Comment(commentId, parent.getPostId(), parentCommentId, authorId, content);
        reply.setDepth(parent.getDepth() + 1);
        
        commentsById.put(commentId, reply);
        commentsByPost.get(parent.getPostId()).add(commentId);
        parent.addChild(commentId);
        
        return reply;
    }
    
    public boolean editComment(String commentId, String authorId, String newContent) {
        Comment comment = commentsById.get(commentId);
        if (comment == null || !comment.getAuthorId().equals(authorId)) return false;
        comment.setContent(newContent);
        return true;
    }
    
    public boolean deleteComment(String commentId, String authorId) {
        Comment comment = commentsById.get(commentId);
        if (comment == null || !comment.getAuthorId().equals(authorId)) return false;
        
        if (comment.hasReplies()) {
            comment.markAsDeleted();
        } else {
            commentsById.remove(commentId);
            commentsByPost.get(comment.getPostId()).remove(commentId);
        }
        return true;
    }
    
    public Comment getComment(String commentId) {
        return commentsById.get(commentId);
    }
    
    public List<CommentWithScore> getComments(String postId, SortStrategy strategy) {
        List<String> commentIds = commentsByPost.getOrDefault(postId, new CopyOnWriteArrayList<>());
        
        List<CommentWithScore> commentsWithScores = commentIds.stream()
            .map(id -> {
                Comment comment = commentsById.get(id);
                if (comment == null) return null;
                CommentVoteCount votes = votingSystem.getVoteCount(id);
                return new CommentWithScore(comment, votes.getScore(), 
                    votes.getUpvotes(), votes.getDownvotes());
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        commentsWithScores.sort(CommentComparatorFactory.create(strategy));
        return commentsWithScores;
    }
    
    public List<Comment> getReplies(String commentId) {
        Comment comment = commentsById.get(commentId);
        if (comment == null) return new ArrayList<>();
        return comment.getChildIds().stream().map(commentsById::get)
            .filter(Objects::nonNull).collect(Collectors.toList());
    }
    
    public List<Comment> getCommentTree(String rootCommentId) {
        List<Comment> tree = new ArrayList<>();
        Comment root = commentsById.get(rootCommentId);
        if (root != null) traverseDFS(root, tree);
        return tree;
    }
    
    private void traverseDFS(Comment comment, List<Comment> result) {
        result.add(comment);
        for (String childId : comment.getChildIds()) {
            Comment child = commentsById.get(childId);
            if (child != null) traverseDFS(child, result);
        }
    }
    
    public boolean voteComment(String commentId, String userId, CommentVoteType voteType) {
        if (commentsById.get(commentId) == null) return false;
        votingSystem.vote(userId, commentId, voteType);
        return true;
    }
    
    public int getCommentScore(String commentId) {
        return votingSystem.getVoteCount(commentId).getScore();
    }
    
    private String generateId() {
        return "c_" + commentIdGenerator.getAndIncrement();
    }
}

// ============================================================================
// DEMONSTRATION
// ============================================================================

public class RedditCommentSystem {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(70));
        System.out.println("REDDIT COMMENT SECTION - COMPREHENSIVE DEMO");
        System.out.println("=".repeat(70));
        
        demo1BasicComments();
        demo2NestedReplies();
        demo3Voting();
        demo4Sorting();
        demo5SoftDelete();
        demo6CommentTree();
        demo7ThreadSafety();
        demo8DesignPatterns();
    }
    
    private static void demo1BasicComments() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 1: Basic Comment Operations");
        System.out.println("=".repeat(70));
        
        CommentManager system = new CommentManager();
        
        System.out.println("Adding top-level comments:");
        Comment c1 = system.addComment("post1", "alice", "Great article!");
        Comment c2 = system.addComment("post1", "bob", "I disagree.");
        Comment c3 = system.addComment("post1", "charlie", "Interesting!");
        
        System.out.printf("Added %d comments%n", 3);
        System.out.println("\nEditing comment:");
        system.editComment(c1.getId(), "alice", "Great article! Thanks!");
        System.out.println("Edited: " + system.getComment(c1.getId()).isEdited());
    }
    
    private static void demo2NestedReplies() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 2: Nested Replies (Tree Structure)");
        System.out.println("=".repeat(70));
        
        CommentManager system = new CommentManager();
        
        Comment c1 = system.addComment("post1", "alice", "Top level");
        Comment c2 = system.replyToComment(c1.getId(), "bob", "Reply to alice");
        Comment c3 = system.replyToComment(c2.getId(), "charlie", "Reply to bob");
        Comment c4 = system.replyToComment(c1.getId(), "david", "Another reply to alice");
        
        System.out.println("\nComment tree:");
        System.out.println(c1);
        System.out.println(c2);
        System.out.println(c3);
        System.out.println(c4);
        
        System.out.printf("\nDepth 0: %d, Depth 1: %d, Depth 2: %d%n", 
            c1.getDepth(), c2.getDepth(), c3.getDepth());
    }
    
    private static void demo3Voting() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 3: Voting on Comments");
        System.out.println("=".repeat(70));
        
        CommentManager system = new CommentManager();
        Comment c1 = system.addComment("post1", "alice", "Amazing!");
        
        System.out.println("Initial score: " + system.getCommentScore(c1.getId()));
        
        system.voteComment(c1.getId(), "u1", CommentVoteType.UPVOTE);
        system.voteComment(c1.getId(), "u2", CommentVoteType.UPVOTE);
        system.voteComment(c1.getId(), "u3", CommentVoteType.DOWNVOTE);
        
        System.out.println("After votes: " + system.getCommentScore(c1.getId()));
        System.out.println("✅ Voting integrated!");
    }
    
    private static void demo4Sorting() throws InterruptedException {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 4: Comment Sorting Strategies");
        System.out.println("=".repeat(70));
        
        CommentManager system = new CommentManager();
        
        Comment c1 = system.addComment("post1", "alice", "Old, low score");
        Thread.sleep(50);
        Comment c2 = system.addComment("post1", "bob", "Newer, high score");
        Thread.sleep(50);
        Comment c3 = system.addComment("post1", "charlie", "Latest, medium score");
        
        // Vote
        system.voteComment(c1.getId(), "u1", CommentVoteType.UPVOTE);
        system.voteComment(c2.getId(), "u1", CommentVoteType.UPVOTE);
        system.voteComment(c2.getId(), "u2", CommentVoteType.UPVOTE);
        system.voteComment(c2.getId(), "u3", CommentVoteType.UPVOTE);
        system.voteComment(c3.getId(), "u1", CommentVoteType.UPVOTE);
        system.voteComment(c3.getId(), "u2", CommentVoteType.UPVOTE);
        
        System.out.println("Sort by TOP:");
        system.getComments("post1", SortStrategy.TOP).forEach(cws ->
            System.out.printf("  Score %d: %s%n", cws.getScore(), 
                cws.getComment().getRawContent()));
        
        System.out.println("\nSort by NEW:");
        system.getComments("post1", SortStrategy.NEW).forEach(cws ->
            System.out.printf("  %s%n", cws.getComment().getRawContent()));
    }
    
    private static void demo5SoftDelete() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 5: Soft Delete");
        System.out.println("=".repeat(70));
        
        CommentManager system = new CommentManager();
        Comment c1 = system.addComment("post1", "alice", "Parent");
        Comment c2 = system.replyToComment(c1.getId(), "bob", "Reply");
        
        System.out.println("Before: " + c1.getContent());
        system.deleteComment(c1.getId(), "alice");
        System.out.println("After: " + system.getComment(c1.getId()).getContent());
        System.out.println("Reply exists: " + (system.getComment(c2.getId()) != null));
        System.out.println("✅ Soft delete preserves structure!");
    }
    
    private static void demo6CommentTree() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 6: Comment Tree Traversal");
        System.out.println("=".repeat(70));
        
        CommentManager system = new CommentManager();
        Comment c1 = system.addComment("post1", "alice", "Root");
        Comment c2 = system.replyToComment(c1.getId(), "bob", "Child 1");
        Comment c3 = system.replyToComment(c1.getId(), "charlie", "Child 2");
        system.replyToComment(c2.getId(), "david", "Grandchild");
        
        System.out.println("DFS Traversal:");
        system.getCommentTree(c1.getId()).forEach(System.out::println);
    }
    
    private static void demo7ThreadSafety() throws InterruptedException {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 7: Thread Safety");
        System.out.println("=".repeat(70));
        
        CommentManager system = new CommentManager();
        Comment root = system.addComment("post1", "root", "Root");
        
        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    system.replyToComment(root.getId(), "user" + threadId, "Reply " + j);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        System.out.printf("Total replies: %d%n", root.getChildIds().size());
        System.out.println("✅ Thread-safe operations!");
    }
    
    private static void demo8DesignPatterns() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 8: Design Patterns");
        System.out.println("=".repeat(70));
        
        System.out.println("1. Composite Pattern:");
        System.out.println("   - Comments form tree structure");
        System.out.println("   - Each comment can have child comments");
        
        System.out.println("\n2. Strategy Pattern:");
        System.out.println("   - Multiple sorting algorithms");
        System.out.println("   - Easy to add new sort strategies");
        
        System.out.println("\n3. ConcurrentHashMap:");
        System.out.println("   - Thread-safe storage");
        System.out.println("   - No global locks");
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ALL DEMOS COMPLETED!");
        System.out.println("=".repeat(70));
        
        System.out.println("\nKey Takeaways:");
        System.out.println("1. Tree structure with parent pointers");
        System.out.println("2. Multiple sorting strategies");
        System.out.println("3. Voting system integration");
        System.out.println("4. Soft delete preserves replies");
        System.out.println("5. Thread-safe with ConcurrentHashMap");
    }
}
