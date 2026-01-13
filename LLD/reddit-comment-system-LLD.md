# Reddit Comment Section - Low-Level Design

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [API Design](#api-design)
5. [Tree Structure Design](#tree-structure-design)
6. [Sorting Algorithms](#sorting-algorithms)
7. [Thread Safety](#thread-safety)
8. [Edge Cases](#edge-cases)
9. [Extensibility](#extensibility)

---

## Understanding the Problem

### What is a Reddit Comment Section?

A Reddit comment section is a hierarchical discussion system where users can:
- Post top-level comments on a post
- Reply to any comment (creating nested threads)
- Upvote/downvote comments
- Sort comments by various criteria (best, top, new, controversial)
- Edit and delete their own comments

### Real-World Examples
- **Reddit**: Nested comments with voting and sorting
- **Hacker News**: Similar hierarchical discussion
- **YouTube**: Nested replies with likes
- **Facebook**: Nested comments on posts
- **Stack Overflow**: Answers with comments

### Core Challenges

1. **Tree Structure**: Comments form a tree (parent-child relationships)
2. **Sorting**: Multiple sorting algorithms (best, top, new, controversial, old)
3. **Voting**: Upvote/downvote system (leverage existing voting system)
4. **Thread Safety**: Concurrent users posting/voting
5. **Performance**: Fast retrieval for large comment trees

---

## Requirements

### Functional Requirements

1. **Comment Operations**
   - Post top-level comment
   - Reply to any comment (nested replies)
   - Edit own comment
   - Delete own comment (soft delete with [deleted] marker)

2. **Voting**
   - Upvote/downvote comments
   - Get vote count (score)
   - Prevent double voting per user

3. **Sorting**
   - **Best**: Reddit's default (hot score algorithm)
   - **Top**: By score (upvotes - downvotes)
   - **New**: By timestamp (newest first)
   - **Controversial**: High engagement but controversial
   - **Old**: By timestamp (oldest first)

4. **Comment Retrieval**
   - Get comment by ID
   - Get all replies for a comment
   - Get comment tree with specified depth limit
   - Pagination support

5. **Moderation**
   - Mark comment as deleted
   - Mark comment as removed by moderator
   - Collapse/expand comment threads

### Non-Functional Requirements

1. **Performance**: 
   - O(1) comment lookup by ID
   - O(n log n) sorting for n comments
   - Fast tree traversal

2. **Thread Safety**:
   - Multiple users commenting concurrently
   - Concurrent voting
   - Consistent comment counts

3. **Scalability**:
   - Handle 10K+ comments per post
   - Deep nesting (100+ levels theoretically)
   - Efficient memory usage

### Out of Scope

- Markdown rendering
- Mentions/notifications
- Awards/gilding
- Search within comments
- Real-time updates (WebSocket)
- Moderation queue

---

## Core Entities and Relationships

### 1. **Comment**
Represents a single comment in the system.

**Attributes:**
- `id`: Unique identifier
- `content`: Comment text
- `authorId`: User who posted
- `parentId`: Parent comment ID (null for top-level)
- `postId`: Post this comment belongs to
- `timestamp`: When comment was created
- `isDeleted`: Soft delete flag
- `isEdited`: Edit flag
- `depth`: Nesting level (0 for top-level)

### 2. **CommentTree**
Manages hierarchical structure of comments.

**Responsibilities:**
- Build tree structure from flat comments
- Navigate parent-child relationships
- Calculate depths
- Traverse tree efficiently

### 3. **CommentManager**
Main orchestrator for comment operations.

**Responsibilities:**
- Add/edit/delete comments
- Retrieve comments with sorting
- Manage comment-vote integration
- Handle pagination

### 4. **CommentSorter**
Implements different sorting algorithms.

**Strategies:**
- BestComparator (hot score)
- TopComparator (by score)
- NewComparator (by timestamp)
- ControversialComparator (engagement)
- OldComparator (oldest first)

### 5. **VotingSystem** (Integration)
Handles upvotes/downvotes (leverage existing system).

---

## API Design

### Core Interface

```java
public interface CommentSystem {
    /**
     * Add top-level comment
     */
    Comment addComment(String postId, String authorId, String content);
    
    /**
     * Reply to a comment
     */
    Comment replyToComment(String commentId, String authorId, String content);
    
    /**
     * Edit comment
     */
    boolean editComment(String commentId, String authorId, String newContent);
    
    /**
     * Delete comment (soft delete)
     */
    boolean deleteComment(String commentId, String authorId);
    
    /**
     * Get comment by ID
     */
    Comment getComment(String commentId);
    
    /**
     * Get all comments for a post with sorting
     */
    List<Comment> getComments(String postId, SortStrategy strategy);
    
    /**
     * Get replies for a comment
     */
    List<Comment> getReplies(String commentId);
    
    /**
     * Vote on comment
     */
    boolean voteComment(String commentId, String userId, VoteType voteType);
    
    /**
     * Get comment score
     */
    int getCommentScore(String commentId);
}
```

### Usage Examples

```java
CommentSystem system = new RedditCommentSystem();

// Post top-level comment
Comment c1 = system.addComment("post123", "user1", "Great article!");

// Reply to comment
Comment c2 = system.replyToComment(c1.getId(), "user2", "Thanks!");

// Reply to reply (nested)
Comment c3 = system.replyToComment(c2.getId(), "user1", "You're welcome!");

// Vote on comment
system.voteComment(c1.getId(), "user3", VoteType.UPVOTE);

// Get all comments sorted by best
List<Comment> comments = system.getComments("post123", SortStrategy.BEST);

// Get replies for a comment
List<Comment> replies = system.getReplies(c1.getId());
```

---

## Tree Structure Design

### Approach 1: Parent Pointer (Recommended)

**Structure:**
```java
class Comment {
    String id;
    String parentId;  // null for top-level
    List<String> childIds;  // Direct children
    int depth;
}
```

**Pros:**
- ✅ Easy to navigate up (parent pointer)
- ✅ Easy to get direct children
- ✅ Flexible for different traversals
- ✅ Simple to implement

**Cons:**
- ❌ Need to build tree for display
- ❌ Extra memory for child list

### Approach 2: Adjacency List

**Structure:**
```java
Map<String, List<String>> adjacencyList;  // commentId -> childIds
```

**Pros:**
- ✅ Simple representation
- ✅ Easy to add edges

**Cons:**
- ❌ No direct parent access
- ❌ Need separate parent map

### Approach 3: N-ary Tree Nodes

**Structure:**
```java
class CommentNode {
    Comment comment;
    List<CommentNode> children;
}
```

**Pros:**
- ✅ Natural tree structure
- ✅ Easy tree traversal
- ✅ Clear hierarchy

**Cons:**
- ❌ Harder to lookup by ID
- ❌ Need separate index map

**Recommended:** Approach 1 (Parent Pointer) - Best balance for Reddit-style comments

---

## Sorting Algorithms

### 1. Best (Hot Score) - Reddit's Default

**Formula:**
```
score = upvotes - downvotes
hot = log10(max(|score|, 1)) + (sign(score) × timestamp / 45000)

Where:
- log10(score) favors higher scores logarithmically
- timestamp / 45000 gives time-based decay
- 45000 seconds ≈ 12.5 hours (half-life)
```

**Implementation:**
```java
public double calculateHotScore(Comment comment) {
    int score = comment.getScore();
    long ageSeconds = (System.currentTimeMillis() - comment.getTimestamp()) / 1000;
    
    int sign = score > 0 ? 1 : (score < 0 ? -1 : 0);
    double order = Math.log10(Math.max(Math.abs(score), 1));
    
    return sign * order + (double) ageSeconds / 45000.0;
}
```

**Characteristics:**
- New + popular comments rise to top
- Old comments decay over time
- Logarithmic scaling prevents single popular comment dominating

### 2. Top (By Score)

**Formula:**
```
score = upvotes - downvotes
```

**Sort:** Descending by score

**Characteristics:**
- Highest voted comments first
- No time decay
- Good for "all-time best"

### 3. New (By Timestamp)

**Sort:** Descending by timestamp

**Characteristics:**
- Latest comments first
- Good for following active discussions
- No vote consideration

### 4. Controversial

**Formula:**
```
controversy = min(upvotes, downvotes) × (upvotes + downvotes)

OR Wilson Score with high engagement
```

**Characteristics:**
- High engagement but divisive
- Lots of both upvotes and downvotes
- Sparks discussion

### 5. Old (By Timestamp)

**Sort:** Ascending by timestamp

**Characteristics:**
- Oldest comments first
- Useful for reading discussion chronologically

---

## Thread Safety

### Challenge: Concurrent Comment Operations

**Scenarios:**
1. User A posts comment while User B reads comments
2. Multiple users vote on same comment simultaneously
3. User edits comment while others read it

### Solution 1: Synchronized Methods (Simple)

```java
public synchronized Comment addComment(String postId, String authorId, String content) {
    // All comment operations serialized
}
```

**Pros:** Simple, correct
**Cons:** Poor concurrency, global bottleneck

### Solution 2: ReadWriteLock (Better)

```java
private final ReadWriteLock lock = new ReentrantReadWriteLock();

public Comment getComment(String id) {
    lock.readLock().lock();
    try {
        return comments.get(id);
    } finally {
        lock.readLock().unlock();
    }
}

public Comment addComment(...) {
    lock.writeLock().lock();
    try {
        // Add comment
    } finally {
        lock.writeLock().unlock();
    }
}
```

**Pros:** Concurrent reads, exclusive writes
**Cons:** Writes still serialize

### Solution 3: ConcurrentHashMap + Per-Comment Locks (Best)

```java
private final ConcurrentHashMap<String, Comment> comments;
private final ConcurrentHashMap<String, ReentrantLock> commentLocks;

public boolean editComment(String commentId, String newContent) {
    Comment comment = comments.get(commentId);
    ReentrantLock lock = commentLocks.computeIfAbsent(commentId, 
        k -> new ReentrantLock());
    
    lock.lock();
    try {
        comment.setContent(newContent);
        return true;
    } finally {
        lock.unlock();
    }
}
```

**Pros:** Fine-grained locking, high concurrency
**Cons:** More complex, potential deadlocks if not careful

---

## Edge Cases

### 1. Deep Nesting
**Scenario:** 100+ levels of nested replies

**Solution:**
- Limit display depth (Reddit limits to ~10 visible levels)
- "Continue this thread" links for deeper nesting
- Pagination at each level

### 2. Deleted Comment with Replies
**Scenario:** User deletes comment that has replies

**Solution:**
- Soft delete: Show [deleted] placeholder
- Keep structure intact for replies
- Only remove if no replies (optional)

```java
public boolean deleteComment(String commentId, String authorId) {
    Comment comment = comments.get(commentId);
    
    if (comment.hasReplies()) {
        comment.markAsDeleted();  // Soft delete
        comment.setContent("[deleted]");
        comment.setAuthorId("[deleted]");
    } else {
        comments.remove(commentId);  // Hard delete
    }
    return true;
}
```

### 3. Rapid Voting
**Scenario:** User rapidly clicks upvote/downvote

**Solution:** Leverage idempotent voting system (already implemented)

### 4. Circular References
**Scenario:** Comment A replies to Comment B which replies to Comment A

**Solution:**
- Validate parent exists before creating reply
- Track comment lineage to prevent cycles
- Should never happen with proper validation

### 5. Editing Comment Multiple Times
**Scenario:** User edits comment repeatedly

**Solution:**
- Track edit history (optional)
- Show "edited" indicator
- Timestamp of last edit

---

## Extensibility

### Future Extensions

#### 1. **Comment Awards**
Reddit Gold, Silver, Platinum

```java
class CommentAward {
    String awardType;
    String grantedBy;
    long timestamp;
}

class Comment {
    List<CommentAward> awards;
    
    public void addAward(CommentAward award) {
        awards.add(award);
    }
}
```

#### 2. **Comment Collapse**
Auto-collapse low-scored or long comment chains

```java
class Comment {
    boolean isCollapsed;
    int threshold = -5;  // Auto-collapse if score < -5
    
    public boolean shouldAutoCollapse() {
        return getScore() < threshold;
    }
}
```

#### 3. **Controversial Badge**
Mark controversial comments

```java
public boolean isControversial(Comment comment) {
    VoteCount votes = votingSystem.getVoteCount(comment.getId());
    int upvotes = votes.getUpvotes();
    int downvotes = votes.getDownvotes();
    
    // High engagement but divisive
    return upvotes > 10 && downvotes > 10 &&
           Math.abs(upvotes - downvotes) < Math.max(upvotes, downvotes) * 0.3;
}
```

#### 4. **Comment Threading Limits**
"Continue this thread →" links

```java
public static final int MAX_DISPLAY_DEPTH = 10;

public List<Comment> getCommentsWithDepthLimit(String postId, int maxDepth) {
    return getAllComments(postId).stream()
        .filter(c -> c.getDepth() <= maxDepth)
        .collect(Collectors.toList());
}
```

#### 5. **Live Comment Updates**
WebSocket for real-time comments

```java
interface CommentObserver {
    void onCommentAdded(Comment comment);
    void onCommentEdited(Comment comment);
    void onCommentDeleted(String commentId);
    void onVoteChanged(String commentId, int newScore);
}
```

#### 6. **Comment Search**
Search within comments

```java
public List<Comment> searchComments(String postId, String query) {
    return comments.values().stream()
        .filter(c -> c.getPostId().equals(postId))
        .filter(c -> c.getContent().toLowerCase().contains(query.toLowerCase()))
        .collect(Collectors.toList());
}
```

---

## Design Patterns Used

1. **Composite Pattern**: Tree structure for nested comments
2. **Strategy Pattern**: Different sorting strategies
3. **Observer Pattern**: Comment notifications
4. **Factory Pattern**: Create comments with different types
5. **Builder Pattern**: Complex comment creation

---

## Data Structures

### Comment Storage

```java
// Fast lookup by ID
ConcurrentHashMap<String, Comment> commentsById;

// Group comments by post
ConcurrentHashMap<String, List<String>> commentsByPost;

// Parent-child relationships
ConcurrentHashMap<String, List<String>> childrenByParent;
```

### Tree Traversal

**Depth-First (for display):**
```java
public void traverseDFS(Comment root, Consumer<Comment> visitor) {
    visitor.accept(root);
    for (String childId : root.getChildIds()) {
        Comment child = commentsById.get(childId);
        traverseDFS(child, visitor);
    }
}
```

**Breadth-First (for level-order):**
```java
public void traverseBFS(Comment root, Consumer<Comment> visitor) {
    Queue<Comment> queue = new LinkedList<>();
    queue.offer(root);
    
    while (!queue.isEmpty()) {
        Comment current = queue.poll();
        visitor.accept(current);
        
        for (String childId : current.getChildIds()) {
            queue.offer(commentsById.get(childId));
        }
    }
}
```

---

## Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Add comment | O(1) | HashMap insert + list append |
| Get comment | O(1) | HashMap lookup |
| Reply to comment | O(1) | Add to parent's children |
| Edit comment | O(1) | Direct update |
| Delete comment | O(1) | Soft delete flag |
| Get all comments | O(n) | Retrieve all for post |
| Sort comments | O(n log n) | Standard sorting |
| Build tree | O(n) | Single pass through comments |
| Vote on comment | O(1) | Delegate to voting system |

---

## Interview Tips

1. **Start with Basics**: Implement Comment class and basic add/get first
2. **Tree Structure**: Explain parent-child relationship clearly
3. **Sorting**: Implement at least 2 sorting strategies (Top and New)
4. **Thread Safety**: Discuss ConcurrentHashMap and voting system integration
5. **Trade-offs**: Nested lists vs flat structure with parent pointers
6. **Scale Considerations**: Pagination, depth limits, caching

### Common Interview Questions

**Q: How do you handle deeply nested comments?**
A: Limit display depth (10 levels), use "Continue thread" links, lazy loading

**Q: How do you prevent circular references in comment tree?**
A: Validate parent exists and is not a descendant before creating reply

**Q: How do you efficiently get all comments for a post?**
A: Maintain post→commentIds index, O(1) lookup then O(n) retrieval

**Q: How would you implement "Load more comments"?**
A: Pagination with cursor-based approach, track last comment ID and depth

**Q: How do you calculate controversial comments?**
A: High upvotes + high downvotes + close to 50/50 ratio

---

## References

- Reddit Comment Ranking: https://redditblog.com/2009/10/15/reddits-new-comment-sorting-system/
- Tree Data Structures
- Composite Design Pattern
- Hot Score Algorithm: https://medium.com/hacking-and-gonzo/how-reddit-ranking-algorithms-work-ef111e33d0d9
