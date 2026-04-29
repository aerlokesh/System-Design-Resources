# Job Portal System - HELLO Interview Framework

> **Companies**: LinkedIn, Indeed, Microsoft, Amazon  
> **Difficulty**: Medium  
> **Pattern**: Strategy (matching/ranking) + Index-based Lookup + Top-K Selection  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Job Portal System?

A system that connects **job seekers** with **job postings** by matching user preferences (desired role, salary expectations) against available positions. The core challenge is designing efficient **matching logic** — given a user's interests, find the top K most relevant jobs without scanning every posting. This requires smart indexing by role and a ranking/selection mechanism for salary-based relevance.

**Real-world**: LinkedIn Jobs, Indeed, Glassdoor, Microsoft Careers, Naukri, Lever, Greenhouse ATS.

### For L63 Microsoft Interview

1. **Data modeling**: Clean Job and User entities with appropriate fields
2. **Index design**: Jobs indexed by role (HashMap<String, List<Job>>) for O(1) role lookup
3. **Matching logic**: Filter by role → rank by salary fit → return top K
4. **Ranking strategy**: Strategy pattern for different ranking algorithms (salary proximity, recency, composite score)
5. **Top-K selection**: PriorityQueue (min-heap) for efficient O(N log K) top-K extraction
6. **Edge cases**: No matching jobs, K > available matches, duplicate postings, salary range overlap

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "Job salary — single value or range?" → Range (minSalary, maxSalary) — more realistic
- "Matching criteria?" → Role must match exactly, salary must overlap with user's expectation
- "When multiple match, how to pick K?" → Rank by salary fit score (how well salary matches expectations), then recency as tiebreaker
- "Can a user have multiple interested roles?" → Yes, but start with single role for MVP (extensible to list)
- "Job deactivation?" → Jobs can be marked inactive/filled (P1)
- "User application tracking?" → Out of scope

### Functional Requirements (P0)
1. **PostJobs(jobId, jobRole, minSalary, maxSalary)**: Post a new job with unique ID, role, and salary range
2. **PostUsers(userId, interestedRole, salaryExpectation)**: Create user with ID, desired role, and salary expectation
3. **FetchJobs(userId, k)**: Return top K jobs matching user's role and salary expectations, ranked by relevance

### Functional Requirements (P1)
- Multiple interested roles per user
- Job expiry / deactivation
- Application tracking (user applied to job)
- Search by location, company, experience level

### Non-Functional
- O(1) job posting (index by role)
- O(M log K) for FetchJobs where M = jobs in matching role, K = requested count
- Thread-safe for concurrent postings and lookups
- Extensible ranking strategy

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────────────────────────────────────────────────────┐
│                        JobPortal                                  │
│  - jobs: Map<String, Job>                  (jobId → Job)         │
│  - users: Map<String, User>               (userId → User)       │
│  - roleIndex: Map<String, List<Job>>       (role → jobs)         │
│  - rankingStrategy: JobRankingStrategy                           │
│  - postJob(id, role, minSal, maxSal) → Job                      │
│  - postUser(id, role, salaryExp) → User                         │
│  - fetchJobs(userId, k) → List<Job>                             │
└──────────────┬───────────────────────────────────────────────────┘
               │ manages
     ┌─────────┼──────────────────┐
     ▼         ▼                  ▼
┌──────────┐ ┌──────────────────┐ ┌──────────────────────────────┐
│   Job    │ │      User        │ │  JobRankingStrategy          │
│          │ │                  │ │  (interface)                 │
│ - id     │ │ - id             │ ├──────────────────────────────┤
│ - role   │ │ - interestedRole │ │ SalaryProximityRanking       │
│ - minSal │ │ - salaryExpect   │ │ RecencyRanking               │
│ - maxSal │ │ - createdAt      │ │ CompositeScoreRanking        │
│ - company│ │                  │ └──────────────────────────────┘
│ - postedAt│ │                  │
│ - active │ │                  │
└──────────┘ └──────────────────┘
```

### Class: Job
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique job ID ("JOB-001") |
| role | String | "Software Engineer", "Product Manager" |
| minSalary | int | Minimum salary offered (e.g., 100000) |
| maxSalary | int | Maximum salary offered (e.g., 150000) |
| company | String | "Microsoft", "Amazon" |
| postedAt | long | Timestamp for recency ranking |
| active | boolean | Is the job still open? |

### Class: User
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique user ID ("USR-001") |
| interestedRole | String | Desired job role |
| salaryExpectation | int | Expected salary (single value for simplicity) |
| createdAt | long | Registration timestamp |

### Matching Logic Summary
```
Step 1: FILTER  — Job.role == User.interestedRole (exact match)
Step 2: FILTER  — Job.minSalary <= User.salaryExpectation <= Job.maxSalary (salary overlap)
Step 3: RANK    — Score each matching job (salary proximity, recency, etc.)
Step 4: TOP-K   — Return top K by score (PriorityQueue min-heap)
```

---

## 3️⃣ API Design

```java
class JobPortal {
    /** Post a new job. Returns the created Job object. */
    Job postJob(String jobId, String role, int minSalary, int maxSalary);
    
    /** Post a new job with company name. */
    Job postJob(String jobId, String role, int minSalary, int maxSalary, String company);
    
    /** Register a new user. Returns the created User object. */
    User postUser(String userId, String interestedRole, int salaryExpectation);
    
    /** Fetch top K jobs matching user's interests. Sorted by relevance (best first). */
    List<Job> fetchJobs(String userId, int k);
    
    /** Set the ranking strategy for job recommendations. */
    void setRankingStrategy(JobRankingStrategy strategy);
    
    /** Get total jobs for a role. */
    int getJobCountForRole(String role);
    
    /** Deactivate a filled/expired job. */
    void deactivateJob(String jobId);
}
```

### Scoring Formula (Default: Salary Proximity)
```
salaryScore = 1.0 - |userExpectation - jobMidpoint| / jobMidpoint

  Example:
    User expects: 120K
    Job offers: 100K-150K → midpoint = 125K
    Score = 1.0 - |120K - 125K| / 125K = 1.0 - 0.04 = 0.96

  Higher score = closer salary match = more relevant
```

---

## 4️⃣ Data Flow

### Scenario 1: Post Jobs + Post User + Fetch Top 3

```
postJob("J1", "SDE", 100000, 150000, "Microsoft")
postJob("J2", "SDE", 120000, 180000, "Amazon")
postJob("J3", "SDE", 80000, 110000, "Startup Inc")
postJob("J4", "PM",  130000, 170000, "Google")
postJob("J5", "SDE", 200000, 250000, "Meta")

roleIndex after posting:
  "SDE" → [J1, J2, J3, J5]
  "PM"  → [J4]

postUser("U1", "SDE", 130000)

fetchJobs("U1", 3):
  │
  ├─ Step 1: ROLE FILTER
  │   User.interestedRole = "SDE"
  │   roleIndex.get("SDE") → [J1, J2, J3, J5]
  │   (J4 excluded — it's a PM role)
  │
  ├─ Step 2: SALARY FILTER
  │   User.salaryExpectation = 130K
  │   J1: 100K-150K → 130K in range? ✅ YES
  │   J2: 120K-180K → 130K in range? ✅ YES
  │   J3: 80K-110K  → 130K in range? ❌ NO (130K > 110K max)
  │   J5: 200K-250K → 130K in range? ❌ NO (130K < 200K min)
  │   Matches: [J1, J2]
  │
  ├─ Step 3: RANK by salary proximity
  │   J1: midpoint = 125K, score = 1.0 - |130-125|/125 = 0.96
  │   J2: midpoint = 150K, score = 1.0 - |130-150|/150 = 0.867
  │   Ranked: [J1 (0.96), J2 (0.867)]
  │
  ├─ Step 4: TOP-K (k=3, but only 2 matches)
  │   Return: [J1, J2]  (return all available if < k)
  │
  └─ Result: [
       Job("J1", "SDE", 100K-150K, "Microsoft", score=0.96),
       Job("J2", "SDE", 120K-180K, "Amazon", score=0.867)
     ]
```

### Scenario 2: Multiple Users, Same Role, Different Expectations

```
postUser("U1", "SDE", 130000)  ← mid-level
postUser("U2", "SDE", 220000)  ← senior

Jobs: J1(100-150K), J2(120-180K), J3(80-110K), J5(200-250K)

fetchJobs("U1", 3):
  Salary filter: 130K → J1 ✅, J2 ✅, J3 ❌, J5 ❌
  Result: [J1 (score=0.96), J2 (score=0.867)]

fetchJobs("U2", 3):
  Salary filter: 220K → J1 ❌, J2 ❌, J3 ❌, J5 ✅
  Rank: J5 midpoint=225K, score = 1.0 - |220-225|/225 = 0.978
  Result: [J5 (score=0.978)]

  ✅ Same jobs, different results based on user's salary expectations
  ✅ Each user gets personalized recommendations
```

### Scenario 3: Edge Cases

```
fetchJobs("U3", 5) — User not found:
  → throw UserNotFoundException("U3")

fetchJobs("U1", 0) — k=0:
  → return empty list []

postUser("U4", "DataScientist", 150000)
fetchJobs("U4", 3) — No jobs for this role:
  → roleIndex.get("DataScientist") → null
  → return empty list []

postJob("J1", "SDE", 100000, 150000) — Duplicate job ID:
  → throw DuplicateJobException("J1")
```

### Scenario 4: Strategy Swap (Recency Ranking)

```
portal.setRankingStrategy(new RecencyRanking())

Jobs posted in order: J1 (yesterday), J2 (today), J6 (1 hour ago)
All match user U1's criteria.

fetchJobs("U1", 2):
  Rank by postedAt (descending — newest first):
    J6 (1 hour ago) → rank 1
    J2 (today)      → rank 2
    J1 (yesterday)  → rank 3
  
  Top 2: [J6, J2]

  ✅ Same data, different ranking — just swapped strategy
```

---

## 5️⃣ Design + Implementation

### Job Entity

```java
class Job {
    private final String id;
    private final String role;
    private final int minSalary;
    private final int maxSalary;
    private final String company;
    private final long postedAt;
    private boolean active;
    
    public Job(String id, String role, int minSalary, int maxSalary, String company) {
        if (minSalary > maxSalary) throw new IllegalArgumentException("minSalary > maxSalary");
        this.id = id;
        this.role = role.toLowerCase().trim();
        this.minSalary = minSalary;
        this.maxSalary = maxSalary;
        this.company = company;
        this.postedAt = System.currentTimeMillis();
        this.active = true;
    }
    
    /** Check if a user's salary expectation falls within this job's range. */
    public boolean matchesSalary(int salaryExpectation) {
        return salaryExpectation >= minSalary && salaryExpectation <= maxSalary;
    }
    
    /** Midpoint of salary range — used for scoring. */
    public int getSalaryMidpoint() {
        return (minSalary + maxSalary) / 2;
    }
    
    // Getters
    public String getId()       { return id; }
    public String getRole()     { return role; }
    public int getMinSalary()   { return minSalary; }
    public int getMaxSalary()   { return maxSalary; }
    public String getCompany()  { return company; }
    public long getPostedAt()   { return postedAt; }
    public boolean isActive()   { return active; }
    public void deactivate()    { this.active = false; }
    
    @Override
    public String toString() {
        return String.format("Job[%s, %s, %s, %dK-%dK]", 
            id, role, company, minSalary / 1000, maxSalary / 1000);
    }
}
```

### User Entity

```java
class User {
    private final String id;
    private final String interestedRole;
    private final int salaryExpectation;
    private final long createdAt;
    
    public User(String id, String interestedRole, int salaryExpectation) {
        this.id = id;
        this.interestedRole = interestedRole.toLowerCase().trim();
        this.salaryExpectation = salaryExpectation;
        this.createdAt = System.currentTimeMillis();
    }
    
    public String getId()              { return id; }
    public String getInterestedRole()  { return interestedRole; }
    public int getSalaryExpectation()  { return salaryExpectation; }
    public long getCreatedAt()         { return createdAt; }
    
    @Override
    public String toString() {
        return String.format("User[%s, role=%s, salary=%dK]", 
            id, interestedRole, salaryExpectation / 1000);
    }
}
```

### Ranking Strategy (Strategy Pattern)

```java
// ==================== RANKING STRATEGY ====================
// 💬 "Strategy Pattern because ranking criteria can change:
//    - Default: salary proximity (how close salary matches)
//    - Alternative: recency (newest jobs first)
//    - Production: composite score (salary + recency + company rating)
//    New ranking = new class, zero changes to JobPortal."

interface JobRankingStrategy {
    /** Score a job for a given user. Higher score = more relevant. */
    double score(Job job, User user);
    String getName();
}

// ==================== SALARY PROXIMITY RANKING ====================
// 💬 "Score based on how close the job's salary midpoint is to user's expectation."
class SalaryProximityRanking implements JobRankingStrategy {
    @Override
    public double score(Job job, User user) {
        int midpoint = job.getSalaryMidpoint();
        int expectation = user.getSalaryExpectation();
        
        // Score = 1.0 - normalized distance from midpoint
        // Closer to midpoint = higher score (max 1.0)
        double distance = Math.abs(expectation - midpoint);
        double normalizedDistance = distance / (double) midpoint;
        return Math.max(0.0, 1.0 - normalizedDistance);
    }
    
    @Override
    public String getName() { return "SalaryProximity"; }
}

// ==================== RECENCY RANKING ====================
// 💬 "Newer jobs ranked higher. Uses posting timestamp."
class RecencyRanking implements JobRankingStrategy {
    @Override
    public double score(Job job, User user) {
        // Normalize to [0,1] — more recent = higher score
        // Use millis since epoch; in practice, normalize against a time window
        return job.getPostedAt() / (double) System.currentTimeMillis();
    }
    
    @Override
    public String getName() { return "Recency"; }
}

// ==================== COMPOSITE SCORE RANKING ====================
// 💬 "Weighted combination: 60% salary fit + 40% recency."
class CompositeScoreRanking implements JobRankingStrategy {
    private final SalaryProximityRanking salaryRanker = new SalaryProximityRanking();
    private final RecencyRanking recencyRanker = new RecencyRanking();
    private final double salaryWeight;
    private final double recencyWeight;
    
    public CompositeScoreRanking(double salaryWeight, double recencyWeight) {
        this.salaryWeight = salaryWeight;
        this.recencyWeight = recencyWeight;
    }
    
    @Override
    public double score(Job job, User user) {
        double salaryScore = salaryRanker.score(job, user);
        double recencyScore = recencyRanker.score(job, user);
        return (salaryWeight * salaryScore) + (recencyWeight * recencyScore);
    }
    
    @Override
    public String getName() { return "Composite(salary=" + salaryWeight + ",recency=" + recencyWeight + ")"; }
}
```

### ScoredJob (Wrapper for Ranking)

```java
// ==================== SCORED JOB ====================
// 💬 "Wraps Job with a score for PriorityQueue ordering.
//    Comparable orders by score ascending (min-heap for top-K)."

class ScoredJob implements Comparable<ScoredJob> {
    private final Job job;
    private final double score;
    
    public ScoredJob(Job job, double score) {
        this.job = job;
        this.score = score;
    }
    
    public Job getJob()     { return job; }
    public double getScore() { return score; }
    
    @Override
    public int compareTo(ScoredJob other) {
        // MIN-HEAP: lower score gets evicted first (we keep highest scores)
        return Double.compare(this.score, other.score);
    }
    
    @Override
    public String toString() {
        return String.format("%s (score=%.3f)", job, score);
    }
}
```

### JobPortal (Coordinator)

```java
// ==================== JOB PORTAL ====================
// 💬 "JobPortal uses a role-based index for O(1) role lookup.
//    FetchJobs: filter by role → filter by salary → rank → top-K via PriorityQueue."

class JobPortal {
    private final Map<String, Job> jobs;                     // jobId → Job
    private final Map<String, User> users;                   // userId → User
    private final Map<String, List<Job>> roleIndex;          // role → List<Job>
    private JobRankingStrategy rankingStrategy;
    
    public JobPortal() {
        this.jobs = new HashMap<>();
        this.users = new HashMap<>();
        this.roleIndex = new HashMap<>();
        this.rankingStrategy = new SalaryProximityRanking();  // default
    }
    
    // ===== POST JOB =====
    
    public Job postJob(String jobId, String role, int minSalary, int maxSalary) {
        return postJob(jobId, role, minSalary, maxSalary, "Unknown");
    }
    
    public Job postJob(String jobId, String role, int minSalary, int maxSalary, String company) {
        if (jobs.containsKey(jobId)) {
            throw new IllegalArgumentException("Duplicate job ID: " + jobId);
        }
        
        Job job = new Job(jobId, role, minSalary, maxSalary, company);
        jobs.put(jobId, job);
        
        // Index by role for fast lookup
        roleIndex.computeIfAbsent(job.getRole(), k -> new ArrayList<>()).add(job);
        
        System.out.println("  📋 Posted: " + job);
        return job;
    }
    
    // ===== POST USER =====
    
    public User postUser(String userId, String interestedRole, int salaryExpectation) {
        if (users.containsKey(userId)) {
            throw new IllegalArgumentException("Duplicate user ID: " + userId);
        }
        
        User user = new User(userId, interestedRole, salaryExpectation);
        users.put(userId, user);
        
        System.out.println("  👤 Registered: " + user);
        return user;
    }
    
    // ===== FETCH JOBS =====
    
    /**
     * Fetch top K jobs matching user's interested role and salary expectation.
     * 
     * Algorithm:
     *   1. Lookup jobs by role (O(1) via roleIndex)
     *   2. Filter by salary match (O(M) where M = jobs for role)
     *   3. Score each matching job using ranking strategy (O(M))
     *   4. Extract top K using min-heap PriorityQueue (O(M log K))
     *   5. Return sorted by score descending (best first)
     *
     * Total: O(M log K) where M = jobs matching role
     */
    public List<Job> fetchJobs(String userId, int k) {
        // Validate
        User user = users.get(userId);
        if (user == null) throw new IllegalArgumentException("User not found: " + userId);
        if (k <= 0) return Collections.emptyList();
        
        // Step 1: Role filter (O(1) lookup)
        List<Job> roleJobs = roleIndex.getOrDefault(user.getInterestedRole(), Collections.emptyList());
        if (roleJobs.isEmpty()) {
            System.out.println("  🔍 No jobs for role: " + user.getInterestedRole());
            return Collections.emptyList();
        }
        
        // Step 2+3+4: Filter by salary + Score + Top-K (min-heap)
        PriorityQueue<ScoredJob> minHeap = new PriorityQueue<>();  // min-heap by score
        
        for (Job job : roleJobs) {
            if (!job.isActive()) continue;                         // skip deactivated
            if (!job.matchesSalary(user.getSalaryExpectation())) continue;  // salary filter
            
            double score = rankingStrategy.score(job, user);
            
            if (minHeap.size() < k) {
                minHeap.offer(new ScoredJob(job, score));
            } else if (score > minHeap.peek().getScore()) {
                // Current job has higher score than the worst in our top-K
                minHeap.poll();                                    // evict lowest
                minHeap.offer(new ScoredJob(job, score));          // add better one
            }
        }
        
        // Step 5: Extract and reverse (heap gives ascending, we want descending)
        List<Job> result = new ArrayList<>();
        List<ScoredJob> scored = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            scored.add(minHeap.poll());
        }
        Collections.reverse(scored);  // best score first
        
        for (ScoredJob sj : scored) {
            result.add(sj.getJob());
            System.out.println("    🎯 " + sj);
        }
        
        System.out.println("  📊 Fetched " + result.size() + " jobs for " + userId 
            + " (strategy: " + rankingStrategy.getName() + ")");
        return result;
    }
    
    // ===== CONFIGURATION =====
    
    public void setRankingStrategy(JobRankingStrategy strategy) {
        this.rankingStrategy = strategy;
        System.out.println("  ⚙️ Ranking strategy: " + strategy.getName());
    }
    
    public void deactivateJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job != null) {
            job.deactivate();
            System.out.println("  ❌ Deactivated: " + jobId);
        }
    }
    
    public int getJobCountForRole(String role) {
        return roleIndex.getOrDefault(role.toLowerCase().trim(), Collections.emptyList()).size();
    }
    
    public String getStatus() {
        return String.format("Portal: %d jobs, %d users, %d roles indexed",
            jobs.size(), users.size(), roleIndex.size());
    }
}
```

### Demo

```java
public class JobPortalDemo {
    public static void main(String[] args) {
        JobPortal portal = new JobPortal();
        
        // === Post Jobs ===
        System.out.println("=== Posting Jobs ===");
        portal.postJob("J1", "SDE", 100000, 150000, "Microsoft");
        portal.postJob("J2", "SDE", 120000, 180000, "Amazon");
        portal.postJob("J3", "SDE", 80000, 110000, "Startup Inc");
        portal.postJob("J4", "PM",  130000, 170000, "Google");
        portal.postJob("J5", "SDE", 200000, 250000, "Meta");
        portal.postJob("J6", "SDE", 125000, 160000, "Apple");
        
        // === Post Users ===
        System.out.println("\n=== Registering Users ===");
        portal.postUser("U1", "SDE", 130000);   // mid-level
        portal.postUser("U2", "SDE", 220000);   // senior
        portal.postUser("U3", "PM",  150000);   // PM
        
        // === Fetch Jobs (Salary Proximity) ===
        System.out.println("\n=== Fetch Jobs for U1 (SDE, 130K) — Top 3 ===");
        List<Job> u1Jobs = portal.fetchJobs("U1", 3);
        // Expected: J1 (125K mid, score≈0.96), J6 (142.5K mid, score≈0.91), J2 (150K mid, score≈0.87)
        
        System.out.println("\n=== Fetch Jobs for U2 (SDE, 220K) — Top 3 ===");
        List<Job> u2Jobs = portal.fetchJobs("U2", 3);
        // Expected: J5 (225K mid, score≈0.98) — only one matches 220K
        
        System.out.println("\n=== Fetch Jobs for U3 (PM, 150K) — Top 3 ===");
        List<Job> u3Jobs = portal.fetchJobs("U3", 3);
        // Expected: J4 (150K mid, score=1.0) — perfect match!
        
        // === Swap Strategy ===
        System.out.println("\n=== Switch to Recency Ranking ===");
        portal.setRankingStrategy(new RecencyRanking());
        List<Job> u1Recent = portal.fetchJobs("U1", 3);
        // Now ordered by posting time instead of salary fit
        
        // === Deactivate Job ===
        System.out.println("\n=== Deactivate J1, Re-fetch ===");
        portal.deactivateJob("J1");
        portal.setRankingStrategy(new SalaryProximityRanking());
        portal.fetchJobs("U1", 3);
        // J1 no longer appears
        
        System.out.println("\n" + portal.getStatus());
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Index Design — Why HashMap for Role Lookup

| Approach | PostJob | FetchJobs (role filter) | Space |
|----------|---------|----------------------|-------|
| **No index (scan all)** | O(1) | O(N) scan all jobs | O(N) |
| **HashMap<Role, List<Job>>** ✅ | O(1) add to list | O(1) lookup + O(M) filter | O(N) |
| **TreeMap<Role, List<Job>>** | O(log R) R=roles | O(log R) + O(M) | O(N) |

**We chose HashMap** because role lookup is exact match (not range or prefix). TreeMap would be useful for prefix search ("Soft" → "Software Engineer") but that's P1.

**Role normalization**: `role.toLowerCase().trim()` — "Software Engineer" and "software engineer" map to same bucket.

### Deep Dive 2: Top-K Algorithm — PriorityQueue Min-Heap

```
WHY PriorityQueue (min-heap) for Top-K?

Naive: Sort all M matches → O(M log M), take first K → wasteful if K << M
Better: Min-heap of size K → O(M log K)

Algorithm:
  1. Maintain a min-heap of size K (smallest score at top)
  2. For each job with score S:
     - If heap.size < K → add to heap
     - If S > heap.peek() → evict smallest, add S
  3. Heap contains K highest-scored jobs

WHY min-heap not max-heap?
  We need to evict the LOWEST score quickly.
  Min-heap.peek() gives us the worst item in our top-K.
  If new item beats it → swap.

Complexity: O(M log K) where M = jobs for role, K = requested count
  - Much better than O(M log M) sort when K << M
  - For 10,000 SDE jobs, top-10: O(10000 × log(10)) vs O(10000 × log(10000))
```

### Deep Dive 3: Salary Matching — Range vs Point

| User Salary | Job Range | Match? | Score |
|-------------|-----------|--------|-------|
| 130K | 100K-150K | ✅ In range | High (close to midpoint 125K) |
| 130K | 120K-180K | ✅ In range | Medium (far from midpoint 150K) |
| 130K | 80K-110K | ❌ Above max | N/A |
| 130K | 200K-250K | ❌ Below min | N/A |

**Design decision**: We filter strictly by range, then score by proximity to midpoint. Alternative approaches:

| Approach | Pros | Cons |
|----------|------|------|
| **Strict range** ✅ (our choice) | No false positives | Might miss jobs with slight mismatch |
| **Fuzzy range (±10%)** | More results | May show irrelevant jobs |
| **No salary filter, just score** | Maximum results | Low-quality matches |

### Deep Dive 4: Strategy Pattern Benefits

```
Ranking algorithms we can swap WITHOUT changing JobPortal:

1. SalaryProximityRanking — how close to user's expectation
2. RecencyRanking — newest jobs first
3. CompositeScoreRanking — weighted combination
4. PopularityRanking — most-applied-to jobs first (P1)
5. CompanyRatingRanking — highest-rated companies first (P1)
6. MachineLearningRanking — trained on user behavior (P2)

portal.setRankingStrategy(new CompositeScoreRanking(0.6, 0.4));
// Done! Zero changes to JobPortal, User, or Job classes.
```

### Deep Dive 5: Thread Safety (If Asked)

```java
// For concurrent PostJob + FetchJobs:
class ThreadSafeJobPortal {
    private final ConcurrentHashMap<String, Job> jobs;
    private final ConcurrentHashMap<String, User> users;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Job>> roleIndex;
    //                                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^
    // CopyOnWriteArrayList: FetchJobs iterates while PostJob may add
    // Reads (FetchJobs) are much more frequent than writes (PostJob) — perfect fit
    
    // Alternative: ReadWriteLock on roleIndex
    // ReadLock for FetchJobs (many concurrent readers)
    // WriteLock for PostJob (exclusive, infrequent)
}
```

### Deep Dive 6: Multiple Interested Roles (P1 Extension)

```java
class UserV2 {
    private final List<String> interestedRoles;  // ["SDE", "SDE-II", "Backend Engineer"]
    private final int minSalary;                 // range instead of single value
    private final int maxSalary;
}

List<Job> fetchJobsV2(String userId, int k) {
    UserV2 user = users.get(userId);
    PriorityQueue<ScoredJob> heap = new PriorityQueue<>();
    
    for (String role : user.getInterestedRoles()) {
        List<Job> roleJobs = roleIndex.getOrDefault(role, emptyList());
        for (Job job : roleJobs) {
            if (job.matchesSalaryRange(user.getMinSalary(), user.getMaxSalary())) {
                double score = rankingStrategy.score(job, user);
                addToHeap(heap, job, score, k);
            }
        }
    }
    return extractTopK(heap);
}
```

### Deep Dive 7: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| User not found | throw IllegalArgumentException |
| K = 0 | Return empty list |
| K > matching jobs | Return all matching (no padding) |
| No jobs for role | Return empty list |
| Duplicate job ID | throw IllegalArgumentException |
| Duplicate user ID | throw IllegalArgumentException |
| Job minSalary > maxSalary | throw IllegalArgumentException in Job constructor |
| Role with different cases ("SDE" vs "sde") | Normalize to lowercase |
| Deactivated job | Skipped in FetchJobs (active filter) |
| All jobs deactivated for a role | Return empty list |
| User salary = job min boundary | Included (>= check) |
| User salary = job max boundary | Included (<= check) |

### Deep Dive 8: Complexity Analysis

| Operation | Time | Space | Note |
|-----------|------|-------|------|
| postJob | O(1) | O(1) | HashMap put + ArrayList append |
| postUser | O(1) | O(1) | HashMap put |
| fetchJobs | O(M log K) | O(K) | M = jobs for role, K = heap size |
| deactivateJob | O(1) | O(1) | HashMap lookup + flag set |
| getJobCountForRole | O(1) | O(1) | List.size() |
| **Total space** | — | O(J + U + J) | J=jobs, U=users, roleIndex shares Job refs |

### Deep Dive 9: Production Enhancements

| Enhancement | Approach | Benefit |
|-------------|----------|---------|
| **Full-text search** | Elasticsearch index on job title/description | Search "backend java distributed" |
| **Location-based** | Geo-index (R-tree) + distance scoring | "Jobs within 30 miles" |
| **Collaborative filtering** | "Users like you also applied to..." | Better recommendations |
| **Real-time updates** | WebSocket push when new matching job posted | Instant notifications |
| **Application tracking** | Job → List\<Application\> with status (Applied, Interview, Offer, Rejected) | Full pipeline |
| **Dedup / similar jobs** | LSH (Locality-Sensitive Hashing) on job descriptions | Remove near-duplicate postings |
| **A/B test ranking** | Run two strategies, measure click-through rate | Data-driven ranking improvement |
| **Pagination** | Cursor-based with offset tracking | Efficient for large result sets |

---

## 📋 Interview Checklist (L63)

- [ ] **Data modeling**: Clean Job (id, role, salary range) and User (id, role, salary expectation) entities
- [ ] **Role index**: HashMap<role, List<Job>> for O(1) role lookup instead of scanning all jobs
- [ ] **Salary matching**: Range check (minSalary ≤ expectation ≤ maxSalary)
- [ ] **Scoring**: Salary proximity formula = 1.0 - |expectation - midpoint| / midpoint
- [ ] **Top-K**: PriorityQueue min-heap for O(M log K) — explain why min-heap, not sort
- [ ] **Strategy Pattern**: Swappable ranking (SalaryProximity, Recency, Composite) without changing portal
- [ ] **Edge cases**: No matches, K > results, duplicate IDs, deactivated jobs
- [ ] **Extensibility**: Multiple roles, location, full-text search — mention how index design supports it

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (Job, User, ScoredJob) | 5 min |
| Index Design + API | 5 min |
| Ranking Strategy + PriorityQueue | 10 min |
| JobPortal Implementation | 7 min |
| Deep Dives (top-K, thread safety, extensions) | 5 min |
| **Total** | **~35 min** |

### Design Pattern Summary

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **Strategy** | JobRankingStrategy (3 implementations) | Swappable ranking algorithms without modifying JobPortal |
| **Index** | roleIndex: HashMap<role, List<Job>> | O(1) role lookup instead of O(N) full scan |
| **Top-K (Min-Heap)** | PriorityQueue<ScoredJob> in fetchJobs | O(M log K) vs O(M log M) sort — critical for large job sets |

See `JobPortalSystem.java` for full implementation with posting, matching, ranking, and strategy swap demos.
