# 🧩 DSA / Algorithm Interview Template — Microsoft

> **Time**: 45 minutes | **Language**: Java (or your preference)
> **Goal**: Demonstrate structured problem-solving, not just "get to the answer"

---

## ⏱️ Timeline (45 min)

| Phase | Time | What You Do |
|-------|------|-------------|
| **1. Understand & Clarify** | 5 min | Repeat problem, ask clarifying questions |
| **2. Pattern Match** | 3 min | Identify the pattern / data structure |
| **3. Approach & Walkthrough** | 5 min | Explain approach, dry-run on example |
| **4. Code** | 20 min | Write clean, correct code |
| **5. Test & Edge Cases** | 7 min | Walk through test cases, fix bugs |
| **6. Complexity & Follow-ups** | 5 min | State T/S complexity, discuss optimizations |

---

## Phase 1️⃣ — Understand & Ask Clarifying Questions (5 min)

> **Say**: "Before I start coding, let me make sure I fully understand the problem."

### 🔑 Clarifying Questions Checklist

#### Input / Output
- [ ] What is the **input format**? (array, string, tree, graph, matrix?)
- [ ] What is the **expected output**? (return value, print, modify in-place?)
- [ ] What are the **input constraints**? (size N, value range, sorted?)

#### Edge Cases
- [ ] Can the input be **empty or null**?
- [ ] Can there be **duplicates**?
- [ ] Can values be **negative**?
- [ ] What if there's **no valid answer**? (return -1, empty list, throw?)

#### Scale & Performance
- [ ] What is the **expected size of input**? (tells you if O(n²) is ok or need O(n log n))
- [ ] Are there **memory constraints**? (can I use extra space?)

#### Assumptions
- [ ] Can I **modify the input** in-place?
- [ ] Is the input **sorted** or do I need to sort it?
- [ ] Are there **concurrent** calls to this function? (thread-safety needed?)

### 📝 Template Statements After Clarifying

```
"So to summarize:
 - Input: [describe]
 - Output: [describe]
 - Constraints: N up to [X], values in range [Y]
 - Edge cases: I'll handle [empty input, no solution, duplicates]
 - I'll assume [sorted/unsorted, no concurrent access unless asked]"
```

---

## Phase 2️⃣ — Pattern Matching (3 min)

> **Say**: "Let me think about which pattern fits here..."

### 🧠 Pattern Recognition Cheat Sheet

| If You See... | Think Pattern | Common DS |
|--------------|--------------|-----------|
| "Subarray sum", "contiguous" | **Sliding Window** / Prefix Sum | Array, HashMap |
| "k-th largest/smallest" | **Heap** / QuickSelect | PriorityQueue |
| "Sorted array, find target" | **Binary Search** | Array |
| "All permutations/combinations" | **Backtracking** | Recursion + choices |
| "Shortest path, fewest steps" | **BFS** | Queue |
| "Connected components, cycles" | **DFS / Union-Find** | Stack, Graph |
| "Optimal substructure + overlapping" | **Dynamic Programming** | Array/HashMap memo |
| "Interval merge/overlap" | **Sorting + Sweep** | Intervals |
| "Frequency, anagram, duplicates" | **HashMap / Counting** | HashMap |
| "Next greater/smaller element" | **Monotonic Stack** | Stack |
| "Top K", "K most frequent" | **Heap** or **Bucket Sort** | PriorityQueue |
| "LinkedList manipulation" | **Two Pointers** (slow/fast) | LinkedList |
| "Tree path/depth/ancestor" | **DFS / Recursion** | Tree |
| "Trie/prefix matching" | **Trie** | Trie |
| "String matching" | **Two Pointers / Sliding Window** | String |
| "Design a data structure" | **HashMap + LinkedList** (LRU) | Composite |
| "Minimize/maximize with constraint" | **Binary Search on Answer** | Array |

### 📝 Template Statement

```
"I see [pattern indicator], so I think this is a [PATTERN NAME] problem.
 My approach will be:
 1. [Step 1]
 2. [Step 2]
 3. [Step 3]
 Time: O(___), Space: O(___)"
```

---

## Phase 3️⃣ — Approach & Dry Run (5 min)

> **Say**: "Let me walk through my approach with this example before coding."

### Steps
1. **State the algorithm in plain English** (2-3 sentences)
2. **Dry-run on the given example** step by step
3. **Confirm with interviewer**: "Does this approach sound good?"

### 📝 Approach Template

```
"My approach:
 1. I'll use a [HashMap / two pointers / BFS / etc.]
 2. First, I'll [initialize/preprocess]
 3. Then, I'll [iterate/recurse] doing [key operation]
 4. Finally, I'll [return/collect result]
 
 Let me trace through the example:
   Input: [example]
   Step 1: [trace]
   Step 2: [trace]
   Output: [expected result] ✓
   
 Time Complexity: O(___) because [reason]
 Space Complexity: O(___) because [reason]"
```

---

## Phase 4️⃣ — Code (20 min)

> **Key principle**: Write **clean, readable, correct** code. Not clever, not minimal — READABLE.

### 🏗️ Code Structure Template (Java)

```java
import java.util.*;

class Solution {

    /**
     * [Brief description of what this method does]
     * 
     * Pattern: [Sliding Window / BFS / DP / etc.]
     * Time:  O(___) 
     * Space: O(___)
     *
     * @param input  description of input
     * @return       description of output
     * @throws IllegalArgumentException if input is invalid
     */
    public ReturnType solve(InputType input) {
        // ========== Step 0: Input Validation ==========
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        if (input.length == 0) {
            return defaultValue; // or throw, or empty result — clarify!
        }

        // ========== Step 1: Initialize ==========
        // Declare data structures, variables
        
        // ========== Step 2: Core Logic ==========
        // Main algorithm loop/recursion
        
        // ========== Step 3: Return Result ==========
        return result;
    }
}
```

### ✅ Coding Best Practices for Microsoft

| Do This | Not This |
|---------|----------|
| Use descriptive variable names: `leftPointer`, `windowSum` | `l`, `s` |
| Add brief inline comments for key steps | No comments at all |
| Handle edge cases with clear checks at the top | Assume happy path |
| Use helper methods for complex sub-problems | One giant method |
| Use `final` for constants | Magic numbers |
| Throw meaningful exceptions | Return -1 silently |

---

## Phase 5️⃣ — Exception Handling & Concurrency (7 min)

### 🚨 Exception Handling Template

```java
public ReturnType solve(InputType input) {
    // ===== Input Validation (throw early) =====
    if (input == null) {
        throw new IllegalArgumentException("Input cannot be null");
    }
    if (input.length == 0) {
        throw new IllegalArgumentException("Input cannot be empty");
    }
    if (k < 0 || k > input.length) {
        throw new IllegalArgumentException(
            "k must be between 0 and " + input.length + ", got: " + k);
    }

    // ===== Boundary checks inside loops =====
    try {
        // core logic
    } catch (ArithmeticException e) {
        // handle overflow: e.g., use long instead of int
        throw new ArithmeticException("Integer overflow in computation");
    }
    
    // ===== No valid answer case =====
    // Option A: Return sentinel
    return -1; // "As discussed, returning -1 if no valid answer"
    
    // Option B: Return Optional
    return Optional.empty();
    
    // Option C: Throw  
    throw new NoSuchElementException("No valid solution found");
}
```

### 🧵 Concurrency Template (If Asked)

> **When to discuss**: If interviewer says "What if this is called concurrently?" or "Make it thread-safe"

```java
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

class ThreadSafeSolution {
    
    // ===== Option 1: synchronized (simplest) =====
    private final Object lock = new Object();
    
    public synchronized ReturnType solve(InputType input) {
        // entire method is thread-safe
    }
    
    // ===== Option 2: ReentrantLock (more control) =====
    private final ReentrantLock lock = new ReentrantLock();
    
    public ReturnType solve(InputType input) {
        lock.lock();
        try {
            // critical section
            return result;
        } finally {
            lock.unlock(); // ALWAYS in finally
        }
    }
    
    // ===== Option 3: ReadWriteLock (reads >> writes) =====
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public ReturnType read(InputType input) {
        rwLock.readLock().lock();
        try {
            return result; // multiple readers allowed
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    public void write(InputType input) {
        rwLock.writeLock().lock();
        try {
            // exclusive access
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    // ===== Option 4: ConcurrentHashMap (for shared maps) =====
    private final ConcurrentHashMap<Key, Value> map = new ConcurrentHashMap<>();
    
    // ===== Option 5: AtomicInteger (for counters) =====
    private final AtomicInteger counter = new AtomicInteger(0);
    
    // ===== Option 6: volatile (for flags) =====
    private volatile boolean isRunning = true;
}
```

### 🧵 Concurrency Discussion Points

| Scenario | Solution | Say This |
|----------|----------|----------|
| Simple shared state | `synchronized` | "For simplicity, I'll use synchronized. In production, I'd consider finer-grained locks." |
| Read-heavy workload | `ReadWriteLock` | "Since reads far outnumber writes, a ReadWriteLock gives better concurrency." |
| Counter / flag | `AtomicInteger` / `volatile` | "For a simple counter, CAS-based AtomicInteger avoids lock overhead." |
| Shared collection | `ConcurrentHashMap` | "ConcurrentHashMap provides thread-safe operations without locking the entire map." |
| Producer-consumer | `BlockingQueue` | "A BlockingQueue naturally handles synchronization between producer and consumer threads." |

---

## Phase 6️⃣ — Test Cases & Complexity (5 min)

### 🧪 Test Cases Template

```
"Let me walk through test cases:

 1. NORMAL CASE:    Input: [typical example]  → Expected: [result] ✓
 2. EDGE - EMPTY:   Input: []                 → Expected: [default/throw] ✓  
 3. EDGE - SINGLE:  Input: [one element]      → Expected: [result] ✓
 4. EDGE - LARGE:   Input: [max constraints]  → Expected: runs in time ✓
 5. EDGE - SPECIAL: Input: [all same / negative / overflow] → Expected: [result] ✓
"
```

### 📊 Complexity Analysis Template

```
"Time Complexity:  O(___) 
 - [Main loop iterates N times]
 - [Each iteration does O(log N) heap operation]
 - Total: O(N log N)

 Space Complexity: O(___)
 - [HashMap stores at most N entries]
 - [Recursion stack goes K levels deep]
 - Total: O(N + K)"
```

### Common Complexity Targets by Input Size

| N Range | Acceptable Complexity | Patterns |
|---------|----------------------|----------|
| N ≤ 20 | O(2^N), O(N!) | Backtracking, brute force |
| N ≤ 500 | O(N³) | DP 3D |
| N ≤ 5,000 | O(N²) | DP 2D, nested loops |
| N ≤ 100,000 | O(N log N) | Sorting, binary search, heap |
| N ≤ 10,000,000 | O(N) | Hash map, two pointers, sliding window |
| N > 10,000,000 | O(log N), O(1) | Binary search, math |

---

## 🎯 Microsoft-Specific Tips

### What Microsoft Interviewers Look For
1. **Communication**: Talk through your thought process out loud
2. **Clean code**: Readable, well-structured, good naming
3. **Edge case handling**: Null checks, empty inputs, overflow
4. **Testing mindset**: Trace through examples, verify correctness
5. **Scalability awareness**: Discuss time/space tradeoffs
6. **Concurrency awareness**: Know when/how to make things thread-safe

### 🗣️ Key Phrases to Use

| Moment | Say This |
|--------|----------|
| Starting | "Let me make sure I understand the problem correctly..." |
| After clarifying | "So the constraints are X, which tells me O(N²) won't work, I need O(N log N) or better." |
| Choosing approach | "I see this maps to a [pattern] problem because [reason]." |
| Before coding | "Let me outline my approach: I'll use [DS] to [strategy]. Sound good?" |
| While coding | "Here I'm [explaining current step]..." |
| Hit a bug | "I see the issue — [explain]. Let me fix this." |
| Done coding | "Let me trace through with this test case to verify..." |
| Complexity | "Time is O(___) because [reason]. Space is O(___) because [reason]." |
| Follow-up | "To optimize further, I could use [approach] which would give O(___), trading off [tradeoff]." |

---

## 📋 Quick Self-Check Before Submitting

- [ ] Did I handle **null / empty** input?
- [ ] Did I handle the **no valid answer** case?
- [ ] Are my variable names **descriptive**?
- [ ] Did I avoid **integer overflow** (use `long` if needed)?
- [ ] Did I state **time and space** complexity?
- [ ] Did I trace through **at least 2 test cases**?
- [ ] Can I explain what happens with **concurrent** access? (even if not coded)
- [ ] Is my code **modular** (helper methods for sub-tasks)?
