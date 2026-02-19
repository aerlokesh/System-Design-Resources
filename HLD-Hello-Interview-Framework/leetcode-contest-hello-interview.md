# Design LeetCode (Coding Contest Platform) - Hello Interview Framework

> **Question**: Design an online coding judge system for programming competitions where users can submit solutions, view real-time leaderboards, and participate in timed contests with automatic code evaluation.
>
> **Asked at**: Meta, Whatnot, LinkedIn, NVIDIA
>
> **Difficulty**: Hard | **Level**: Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Submit Code**: Users submit solutions in multiple languages (Java, Python, C++, Go, etc.) for a given problem.
2. **Judge Code**: The system compiles and runs user code against test cases in a sandboxed environment, returning verdict (Accepted, Wrong Answer, TLE, MLE, Runtime Error, Compilation Error).
3. **Real-Time Leaderboard**: During contests, show a live leaderboard ranked by problems solved and time penalties.
4. **Timed Contests**: Support contests with start/end times, problem sets, and participant registration. Late submissions are rejected.
5. **Problem Management**: Admins create problems with descriptions, test cases (visible + hidden), time/memory limits, and editorial solutions.

#### Nice to Have (P1)
- Code execution playground ("Run" without submitting).
- Custom test cases (user provides their own input).
- Submission history and diff viewer.
- Rating system (ELO-style, like Codeforces).
- Editorial / solution walkthrough after contest ends.
- Multiple judge strategies (special judge for floating-point, interactive problems).

#### Below the Line (Out of Scope)
- IDE features (autocomplete, debugging).
- Plagiarism detection (assume separate system).
- Discussion forums / comments.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Submission Throughput** | 10,000 submissions/min during contest peak | 100K users × 4 problems × burst at contest start/end |
| **Judge Latency** | < 30 seconds from submission to verdict (p95) | Users expect quick feedback |
| **Leaderboard Freshness** | < 5 seconds after verdict | Real-time competition experience |
| **Consistency** | Strong for submissions; eventual for leaderboard | A submission must never be lost; leaderboard can lag briefly |
| **Availability** | 99.99% during contests | Contest downtime = reputation damage |
| **Security** | User code runs in isolated sandbox; cannot escape | Untrusted code is the #1 security risk |
| **Scalability** | 100K concurrent users, 1M+ problems, 100M+ submissions | LeetCode-scale platform |
| **Fairness** | All users see the same problems at the same time; no early access | Contest integrity |

### Capacity Estimation

```
Users & Contests:
  Registered users: 10M
  Concurrent during contest: 100K
  Contests per week: 2-3
  Problems per contest: 4
  Average contest duration: 90 minutes

Submissions:
  Per user per contest: ~15 (multiple attempts per problem)
  Total per contest: 100K × 15 = 1.5M submissions
  Peak rate: 1.5M / 90 min = ~16,700 submissions/min
  Sustained: ~280 submissions/sec
  Peak burst (start/end): ~1,000 submissions/sec

Judge Execution:
  Average execution time per submission: 5 seconds (compile + run all test cases)
  At 280 submissions/sec: need 280 × 5 = 1,400 concurrent judge workers
  At peak 1,000/sec: need 5,000 concurrent workers

Storage:
  Per submission: ~5 KB (code) + 50 KB (judge result + logs)
  Per contest: 1.5M × 55 KB = 82 GB
  Total all-time: 100M submissions × 55 KB = 5.5 TB

Leaderboard:
  100K participants × 4 problems × 200 bytes = 80 MB (fits in Redis)
```

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ Contest       │ 1─* │ Problem      │ *─* │ TestCase     │
├──────────────┤     ├──────────────┤     ├──────────────┤
│ contest_id   │     │ problem_id   │     │ test_id      │
│ title        │     │ title        │     │ problem_id   │
│ starts_at    │     │ description  │     │ input        │
│ ends_at      │     │ time_limit   │     │ expected_out │
│ status       │     │ memory_limit │     │ is_hidden    │
│ problems     │     │ difficulty   │     │ weight       │
└──────────────┘     └──────────────┘     └──────────────┘
       │                    │
       │              ┌─────┴──────────┐
       └──────────────┤  Submission    │
                      ├────────────────┤
                      │ submission_id  │
                      │ user_id        │
                      │ problem_id     │
                      │ contest_id     │
                      │ language       │
                      │ code           │
                      │ verdict        │
                      │ runtime_ms     │
                      │ memory_kb      │
                      │ submitted_at   │
                      │ judged_at      │
                      └────────────────┘
```

### Entity 1: Problem
```sql
CREATE TABLE problems (
    problem_id      UUID PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     TEXT NOT NULL,          -- Markdown with examples
    difficulty      VARCHAR(10),            -- EASY, MEDIUM, HARD
    time_limit_ms   INT DEFAULT 2000,       -- Per test case
    memory_limit_kb INT DEFAULT 262144,     -- 256 MB
    judge_type      VARCHAR(20) DEFAULT 'STANDARD', -- STANDARD, SPECIAL, INTERACTIVE
    solution_code   TEXT,                   -- Editorial solution
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE test_cases (
    test_id         UUID PRIMARY KEY,
    problem_id      UUID REFERENCES problems(problem_id),
    input           TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    is_hidden       BOOLEAN DEFAULT true,   -- Hidden test cases not shown to users
    weight          INT DEFAULT 1,          -- For partial scoring
    order_index     INT NOT NULL
);
```

### Entity 2: Submission
```sql
CREATE TABLE submissions (
    submission_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    problem_id      UUID NOT NULL REFERENCES problems(problem_id),
    contest_id      UUID,                   -- NULL for practice mode
    language        VARCHAR(20) NOT NULL,    -- JAVA, PYTHON3, CPP, GO, RUST
    code            TEXT NOT NULL,
    
    -- Judge Result
    verdict         VARCHAR(30),            -- PENDING, JUDGING, ACCEPTED, WRONG_ANSWER, TLE, MLE, RE, CE
    runtime_ms      INT,
    memory_kb       INT,
    test_cases_passed INT DEFAULT 0,
    total_test_cases INT DEFAULT 0,
    judge_output    TEXT,                   -- Compilation errors, first failing test case
    
    submitted_at    TIMESTAMPTZ DEFAULT NOW(),
    judged_at       TIMESTAMPTZ,
    judge_worker_id VARCHAR(100)
);

CREATE INDEX idx_submissions_user ON submissions (user_id, submitted_at DESC);
CREATE INDEX idx_submissions_contest ON submissions (contest_id, problem_id, user_id);
CREATE INDEX idx_submissions_pending ON submissions (verdict) WHERE verdict = 'PENDING';
```

### Entity 3: Contest & Leaderboard
```sql
CREATE TABLE contests (
    contest_id      UUID PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) DEFAULT 'UPCOMING', -- UPCOMING, ACTIVE, ENDED
    problem_ids     UUID[] NOT NULL,
    registration_required BOOLEAN DEFAULT false
);

-- Leaderboard stored in Redis (real-time) and materialized to DB after contest
-- Redis key: leaderboard:{contest_id}
-- Redis sorted set: score = (problems_solved * 1000000) - total_penalty_minutes
-- Member: user_id
```

---

## 3️⃣ API Design

### 1. Submit Solution
```
POST /api/v1/problems/{problem_id}/submit

Headers:
  Authorization: Bearer <JWT>
  X-Contest-ID: contest_123 (optional)

Request:
{
  "language": "JAVA",
  "code": "class Solution { public int[] twoSum(int[] nums, int target) { ... } }"
}

Response (202 Accepted):
{
  "submission_id": "sub_abc123",
  "status": "PENDING",
  "position_in_queue": 42,
  "estimated_wait_seconds": 15
}
```

### 2. Get Submission Result (Polling or WebSocket)
```
GET /api/v1/submissions/{submission_id}

Response (200 OK — Judging):
{
  "submission_id": "sub_abc123",
  "status": "JUDGING",
  "progress": "Running test case 5/20"
}

Response (200 OK — Complete):
{
  "submission_id": "sub_abc123",
  "verdict": "ACCEPTED",
  "runtime_ms": 45,
  "memory_kb": 42000,
  "test_cases_passed": 20,
  "total_test_cases": 20,
  "language": "JAVA",
  "submitted_at": "2025-01-10T19:30:00Z",
  "judged_at": "2025-01-10T19:30:12Z"
}

Response (200 OK — Failed):
{
  "submission_id": "sub_abc123",
  "verdict": "WRONG_ANSWER",
  "test_cases_passed": 15,
  "total_test_cases": 20,
  "first_failing_test": {
    "input": "[2,7,11,15], target=9",
    "expected_output": "[0,1]",
    "your_output": "[1,0]"
  }
}
```

### 3. Get Contest Leaderboard
```
GET /api/v1/contests/{contest_id}/leaderboard?page=1&limit=50

Response (200 OK):
{
  "contest_id": "contest_123",
  "total_participants": 98432,
  "updated_at": "2025-01-10T20:15:03Z",
  "leaderboard": [
    {
      "rank": 1,
      "user": { "username": "tourist", "rating": 3800 },
      "problems_solved": 4,
      "total_penalty_minutes": 87,
      "problems": [
        { "problem_id": "p1", "solved": true, "attempts": 1, "time_minutes": 5 },
        { "problem_id": "p2", "solved": true, "attempts": 2, "time_minutes": 22 },
        { "problem_id": "p3", "solved": true, "attempts": 1, "time_minutes": 35 },
        { "problem_id": "p4", "solved": true, "attempts": 3, "time_minutes": 25 }
      ]
    }
  ]
}
```

### 4. WebSocket: Live Submission Updates
```
WebSocket: wss://ws.leetcode.com/v1/submissions/{submission_id}/live

Server → Client:
{ "type": "STATUS_UPDATE", "status": "COMPILING" }
{ "type": "STATUS_UPDATE", "status": "RUNNING", "test_case": 1, "total": 20 }
{ "type": "STATUS_UPDATE", "status": "RUNNING", "test_case": 10, "total": 20 }
{ "type": "VERDICT", "verdict": "ACCEPTED", "runtime_ms": 45, "memory_kb": 42000 }
```

---

## 4️⃣ Data Flow

### Flow 1: Submit → Judge → Verdict
```
1. User submits code: POST /api/v1/problems/{id}/submit
   ↓
2. Submission Service:
   a. Validate: contest is active, language is supported, code size < 50 KB
   b. If contest: check starts_at <= NOW() <= ends_at
   c. Write submission to DB: status = PENDING
   d. Enqueue to Kafka topic "submissions" (partition by problem_id)
   e. Return 202 Accepted with submission_id
   ↓
3. Judge Orchestrator (consumes from Kafka):
   a. Pick a free Judge Worker (from worker pool)
   b. Send submission + test cases to worker
   c. Update submission: status = JUDGING
   ↓
4. Judge Worker (sandboxed container):
   a. Write user code to temp file in sandbox
   b. Compile: `javac Solution.java` (with timeout)
   c. If compile error → return CE verdict
   d. For each test case:
      - Run: `java Solution < input.txt > output.txt` (with time/memory limits)
      - Compare output.txt with expected_output
      - If wrong → return WA with first failing test
      - If timeout → return TLE
      - If memory exceeded → return MLE
      - If crash → return RE
   e. All tests passed → return ACCEPTED with max runtime, max memory
   ↓
5. Judge Orchestrator receives result:
   a. Update submission in DB: verdict, runtime, memory, judged_at
   b. Publish "submission-judged" event to Kafka
   ↓
6. Event Consumers:
   a. WebSocket Service → push verdict to user's browser
   b. Leaderboard Service → update contest leaderboard if ACCEPTED
   c. Rating Service → (after contest) recalculate ratings
   ↓
7. Total time: 5-30 seconds (compile + run all test cases)
```

### Flow 2: Leaderboard Update (on Accepted Submission)
```
1. "submission-judged" event with verdict = ACCEPTED, contest_id != null
   ↓
2. Leaderboard Service:
   a. Check if this is the FIRST accepted submission for this user + problem
      (ignore duplicate accepts for same problem)
   b. Calculate penalty: time_from_contest_start + (failed_attempts × 20 min)
   c. Update Redis sorted set:
      Key: leaderboard:{contest_id}
      Score: (problems_solved × 1,000,000) - total_penalty_minutes
      Member: user_id
   ↓
3. All clients viewing leaderboard get updates via WebSocket/SSE
   ↓
   Latency: verdict → leaderboard update: < 5 seconds
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                   │
│   [Web IDE]        [Mobile]        [API]                              │
│   REST + WebSocket for live updates                                   │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │    API Gateway / LB      │
              │  • Auth, Rate Limiting   │
              └────────────┬────────────┘
                           │
         ┌─────────────────┼────────────────────┐
         ▼                 ▼                     ▼
┌─────────────────┐ ┌────────────────┐  ┌────────────────┐
│  Submission      │ │  Problem       │  │  Contest        │
│  Service         │ │  Service       │  │  Service        │
│  • Accept code   │ │  • CRUD        │  │  • Start/End    │
│  • Enqueue       │ │  • Test cases  │  │  • Register     │
│  • Track status  │ │  • Editorials  │  │  • Leaderboard  │
└───────┬─────────┘ └────────────────┘  └───────┬────────┘
        │                                        │
        ▼                                        ▼
┌──────────────────────┐              ┌──────────────────┐
│  Kafka               │              │  Redis Cluster    │
│  "submissions" topic │              │  • Leaderboard    │
│  "judged" topic      │              │  • Live scores    │
└─────────┬────────────┘              │  • Submission     │
          │                           │    status cache   │
          ▼                           └──────────────────┘
┌──────────────────────────────────────────────────┐
│           JUDGE ORCHESTRATOR                      │
│  • Consume from Kafka                            │
│  • Assign to free worker                         │
│  • Track worker health                           │
│  • Handle timeouts & retries                     │
└─────────┬────────────────────────────────────────┘
          │
    ┌─────┼─────┬─────────┐
    ▼     ▼     ▼         ▼
┌──────┐┌──────┐┌──────┐┌──────┐
│Worker││Worker││Worker││Worker│  × 1000-5000
│  1   ││  2   ││  3   ││  N   │  (auto-scaled)
│      ││      ││      ││      │
│Docker││Docker││Docker││Docker│  Each worker runs
│Sand- ││Sand- ││Sand- ││Sand- │  user code in an
│box   ││box   ││box   ││box   │  isolated container
└──────┘└──────┘└──────┘└──────┘

Judge Workers: Kubernetes pods with:
  • gVisor/Firecracker for security isolation
  • CPU limit: 2 cores
  • Memory limit: 512 MB
  • Network: DISABLED (no internet access)
  • Filesystem: read-only (except /tmp)
  • Time limit: configurable per problem
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Submission Service** | Accept code, enqueue, track status | Java/Spring Boot | Stateless, horizontal |
| **Problem Service** | CRUD problems + test cases | Java/Spring Boot | Read replicas for serving |
| **Contest Service** | Contest lifecycle, leaderboard | Java/Spring Boot | Stateless |
| **Kafka** | Submission queue, decouples submit from judge | Apache Kafka | Partitioned by problem_id |
| **Judge Orchestrator** | Assign submissions to workers, retry failed | Java/Go | 2-3 instances (HA) |
| **Judge Workers** | Compile + run user code in sandbox | Docker + gVisor | Auto-scale 1K-5K pods |
| **Redis** | Leaderboard, submission status cache | Redis Cluster | Standard |
| **PostgreSQL** | Source of truth: problems, submissions, contests | PostgreSQL 15+ | Master + replicas |
| **S3** | Store submission code + judge output logs | AWS S3 | Managed |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Sandboxed Code Execution — Security

**The Problem**: Running untrusted user code is the #1 security risk. User code could: fork-bomb, access the network, read other submissions, escape the container, mine crypto.

```java
public class JudgeWorker {
    /**
     * Execute user code in a fully isolated sandbox.
     * Uses Docker + gVisor (or Firecracker) for multi-layer isolation.
     */
    public JudgeResult execute(Submission submission, List<TestCase> testCases) {
        // 1. Create isolated container
        ContainerConfig config = ContainerConfig.builder()
            .image(getLanguageImage(submission.getLanguage())) // e.g., "judge-java:17"
            .cpuLimit(2.0)              // 2 CPU cores max
            .memoryLimit("512m")        // 512 MB max
            .networkDisabled(true)      // NO internet access
            .readOnlyRootFilesystem(true)
            .tmpfsMount("/tmp", "64m")  // Writable /tmp, 64 MB max
            .seccompProfile("strict")   // Block dangerous syscalls
            .noNewPrivileges(true)      // Prevent privilege escalation
            .pidsLimit(100)             // Max 100 processes (prevent fork bomb)
            .build();
        
        String containerId = docker.createContainer(config);
        
        try {
            // 2. Copy user code into container
            docker.copyToContainer(containerId, "/tmp/solution" + getExtension(submission.getLanguage()),
                                   submission.getCode().getBytes());
            
            // 3. Compile (with timeout)
            ExecResult compileResult = docker.exec(containerId, 
                getCompileCommand(submission.getLanguage()),
                Duration.ofSeconds(30));
            
            if (compileResult.getExitCode() != 0) {
                return JudgeResult.compilationError(compileResult.getStderr());
            }
            
            // 4. Run each test case
            int passed = 0;
            long maxRuntimeMs = 0;
            long maxMemoryKb = 0;
            
            for (TestCase testCase : testCases) {
                docker.copyToContainer(containerId, "/tmp/input.txt", 
                                       testCase.getInput().getBytes());
                
                ExecResult runResult = docker.exec(containerId,
                    getRunCommand(submission.getLanguage()),
                    Duration.ofMillis(testCase.getProblem().getTimeLimitMs() * 2)); // 2x safety margin
                
                maxRuntimeMs = Math.max(maxRuntimeMs, runResult.getDurationMs());
                maxMemoryKb = Math.max(maxMemoryKb, runResult.getMemoryKb());
                
                if (runResult.isTimedOut()) {
                    return JudgeResult.timeLimitExceeded(passed, testCases.size(), testCase);
                }
                if (runResult.getMemoryKb() > testCase.getProblem().getMemoryLimitKb()) {
                    return JudgeResult.memoryLimitExceeded(passed, testCases.size(), testCase);
                }
                if (runResult.getExitCode() != 0) {
                    return JudgeResult.runtimeError(passed, testCases.size(), runResult.getStderr());
                }
                
                String actualOutput = runResult.getStdout().trim();
                String expectedOutput = testCase.getExpectedOutput().trim();
                
                if (!actualOutput.equals(expectedOutput)) {
                    return JudgeResult.wrongAnswer(passed, testCases.size(), testCase, actualOutput);
                }
                passed++;
            }
            
            return JudgeResult.accepted(passed, maxRuntimeMs, maxMemoryKb);
            
        } finally {
            docker.removeContainer(containerId); // Always cleanup
        }
    }
}
```

**Security Layers**:
```
Layer 1: Docker container isolation (namespace, cgroup)
Layer 2: gVisor / Firecracker (user-space kernel, blocks syscalls)
Layer 3: Network disabled (no outbound connections)
Layer 4: Read-only filesystem (can't read other submissions)
Layer 5: seccomp profile (block fork, exec, ptrace)
Layer 6: PID limit (prevent fork bombs)
Layer 7: CPU + memory cgroup limits (prevent resource exhaustion)
Layer 8: Execution timeout (killed after time limit)
```

---

### Deep Dive 2: Judge Queue & Worker Auto-Scaling

**The Problem**: During contest start, submissions spike from 0 to 1000/sec in seconds. We need to scale judge workers dynamically.

```java
public class JudgeOrchestrator {
    private final KafkaConsumer<String, Submission> consumer;
    private final WorkerPool workerPool;
    
    /** Main loop: consume submissions, assign to workers */
    public void run() {
        while (true) {
            ConsumerRecords<String, Submission> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, Submission> record : records) {
                Submission submission = record.value();
                
                // Find a free worker
                JudgeWorkerClient worker = workerPool.acquireWorker(Duration.ofSeconds(30));
                
                if (worker == null) {
                    // No workers available — re-enqueue with backoff
                    log.warn("No workers available, submission {} delayed", submission.getId());
                    metrics.counter("judge.queue.no_worker").increment();
                    continue; // Will be re-consumed (don't commit offset)
                }
                
                // Submit to worker asynchronously
                CompletableFuture.runAsync(() -> {
                    try {
                        updateStatus(submission.getId(), "JUDGING");
                        JudgeResult result = worker.judge(submission);
                        saveResult(submission.getId(), result);
                        publishJudgedEvent(submission, result);
                    } catch (Exception e) {
                        handleJudgeFailure(submission, e);
                    } finally {
                        workerPool.releaseWorker(worker);
                    }
                });
            }
        }
    }
}
```

**Auto-Scaling**:
```yaml
# Kubernetes HPA for judge workers
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: judge-worker-hpa
spec:
  scaleTargetRef:
    kind: Deployment
    name: judge-workers
  minReplicas: 100      # Baseline: always 100 workers ready
  maxReplicas: 5000     # Peak: scale to 5K during contests
  metrics:
    - type: External
      external:
        metric:
          name: kafka_consumer_lag
          selector:
            matchLabels:
              topic: submissions
        target:
          type: Value
          value: "500"   # Scale up when > 500 pending submissions
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 500     # Add up to 500 workers at once
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300  # Wait 5 min before scaling down
```

---

### Deep Dive 3: Real-Time Leaderboard

```java
public class LeaderboardService {
    private final JedisCluster redis;
    
    /**
     * Update leaderboard when a submission is accepted.
     * Score = (problems_solved × 1,000,000) - total_penalty_minutes
     * Higher score = better rank.
     */
    public void onSubmissionAccepted(SubmissionJudgedEvent event) {
        if (event.getContestId() == null) return; // Practice mode, no leaderboard
        
        String userId = event.getUserId();
        String contestId = event.getContestId();
        String problemId = event.getProblemId();
        
        // Check if already solved this problem (idempotent)
        String solvedKey = "contest:" + contestId + ":solved:" + userId;
        if (redis.sismember(solvedKey, problemId)) {
            return; // Already solved, don't double-count
        }
        redis.sadd(solvedKey, problemId);
        
        // Calculate penalty for this problem
        Contest contest = contestService.getContest(contestId);
        long minutesFromStart = Duration.between(contest.getStartsAt(), event.getSubmittedAt()).toMinutes();
        int failedAttempts = getFailedAttemptCount(userId, contestId, problemId);
        long penalty = minutesFromStart + (failedAttempts * 20L); // 20 min per failed attempt
        
        // Update user's total score
        String penaltyKey = "contest:" + contestId + ":penalty:" + userId;
        redis.incrBy(penaltyKey, penalty);
        long totalPenalty = Long.parseLong(redis.get(penaltyKey));
        
        int problemsSolved = redis.scard(solvedKey).intValue();
        
        // Score: higher = better. problems_solved is primary, penalty is secondary (lower = better)
        double score = (problemsSolved * 1_000_000.0) - totalPenalty;
        
        redis.zadd("leaderboard:" + contestId, score, userId);
        
        // Publish leaderboard update for real-time push
        kafka.send(new ProducerRecord<>("leaderboard-updates", contestId,
            LeaderboardUpdate.builder()
                .contestId(contestId).userId(userId)
                .rank(getRank(contestId, userId))
                .problemsSolved(problemsSolved)
                .totalPenalty(totalPenalty)
                .build()));
    }
    
    /** Get top N participants */
    public List<LeaderboardEntry> getTopN(String contestId, int n) {
        Set<Tuple> topN = redis.zrevrangeWithScores("leaderboard:" + contestId, 0, n - 1);
        int rank = 1;
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Tuple t : topN) {
            String userId = t.getElement();
            entries.add(buildEntry(contestId, userId, rank++, t.getScore()));
        }
        return entries;
    }
    
    /** Get a user's current rank */
    public Long getRank(String contestId, String userId) {
        Long reverseRank = redis.zrevrank("leaderboard:" + contestId, userId);
        return reverseRank != null ? reverseRank + 1 : null;
    }
}
```

---

### Deep Dive 4: Contest Fairness — Atomic Problem Reveal

**The Problem**: All participants must see contest problems at exactly the same time. If problems are revealed 1 second early for some users, it's unfair.

```java
public class ContestStartService {
    
    /**
     * Contest start is a two-phase process:
     * 1. Pre-load: 5 minutes before start, load problems into cache (encrypted)
     * 2. Reveal: At exactly starts_at, publish the decryption key
     */
    @Scheduled(fixedRate = 1_000)
    public void checkContestStarts() {
        Instant now = Instant.now();
        List<Contest> starting = contestRepo.findByStatusAndStartsBefore("UPCOMING", now);
        
        for (Contest contest : starting) {
            // Atomic status update (prevent double-start)
            boolean updated = contestRepo.updateStatusIfCurrent(
                contest.getContestId(), "UPCOMING", "ACTIVE");
            if (!updated) continue;

            // Publish contest-started event
            kafka.send(new ProducerRecord<>("contest-events", contest.getContestId().toString(),
                ContestEvent.builder().type("STARTED").contestId(contest.getContestId())
                    .problemIds(contest.getProblemIds()).build()));
            
            log.info("Contest {} started with {} problems", contest.getTitle(), contest.getProblemIds().size());
        }
    }
}
```

**Fairness Measures**:
```
1. Problems cached server-side (encrypted) 5 min before start
2. Single atomic event ("contest-started") triggers reveal for all users simultaneously
3. All API servers receive the event at the same time via Kafka
4. Client-side countdown synced with server time (NTP)
5. Contest problems are NOT accessible via API until status = ACTIVE
6. Anti-cheat: no problem URLs are guessable (UUID-based)
```

---

### Deep Dive 5: Multi-Language Support & Compilation

```java
public class LanguageConfig {
    // Each language has specific compile + run commands
    private static final Map<String, LanguageSpec> LANGUAGES = Map.of(
        "JAVA", new LanguageSpec(
            "judge-java:17",
            "javac -encoding UTF-8 Solution.java",
            "java -Xmx256m -Xss64m Solution",
            ".java"),
        "PYTHON3", new LanguageSpec(
            "judge-python:3.11",
            null, // No compilation
            "python3 -u solution.py",
            ".py"),
        "CPP", new LanguageSpec(
            "judge-cpp:gcc13",
            "g++ -O2 -std=c++17 -o solution solution.cpp",
            "./solution",
            ".cpp"),
        "GO", new LanguageSpec(
            "judge-go:1.21",
            "go build -o solution solution.go",
            "./solution",
            ".go"),
        "RUST", new LanguageSpec(
            "judge-rust:1.75",
            "rustc -O -o solution solution.rs",
            "./solution",
            ".rs")
    );
}
```

**Language-Specific Challenges**:
```
Java:  JVM startup overhead (~500ms) → warm container pool
Python: Inherently slow → give 3-5x more time than C++
C++:   Undefined behavior → gVisor catches segfaults cleanly
Go:    goroutine limits → set GOMAXPROCS=2
Rust:  Long compile times → generous compile timeout (60s)
```

---

### Deep Dive 6: Observability & Metrics

```java
public class JudgeMetrics {
    Counter submissionsReceived = Counter.builder("judge.submissions.received").register(registry);
    Counter verdictAccepted    = Counter.builder("judge.verdict").tag("verdict", "AC").register(registry);
    Counter verdictWA          = Counter.builder("judge.verdict").tag("verdict", "WA").register(registry);
    Counter verdictTLE         = Counter.builder("judge.verdict").tag("verdict", "TLE").register(registry);
    Timer   judgeLatency       = Timer.builder("judge.latency_ms").register(registry);
    Timer   compileLatency     = Timer.builder("judge.compile_ms").register(registry);
    Gauge   queueDepth         = Gauge.builder("judge.queue.depth", ...).register(registry);
    Gauge   activeWorkers      = Gauge.builder("judge.workers.active", ...).register(registry);
    Gauge   leaderboardSize    = Gauge.builder("contest.leaderboard.size", ...).register(registry);
}
```

---

### Deep Dive 7: Rating System (ELO/Glicko-2)

**The Problem**: After each contest, every participant's rating must be recalculated. Codeforces uses a modified ELO system; LeetCode uses a similar approach. The rating must be fair (higher-rated users need stronger performance to gain points), stable (one bad contest shouldn't destroy your rating), and computed within minutes of contest end for 100K participants.

**Design Choice**: We use **Glicko-2**, which adds a "rating deviation" (RD) that captures uncertainty. New users start with high RD (volatile rating), experienced users have low RD (stable rating). This naturally handles inactive users (RD increases over time).

```java
public class RatingService {

    /**
     * Glicko-2 rating calculation for competitive programming.
     * Called after contest ends with final standings.
     *
     * Key Parameters:
     *   μ (mu)    = skill rating (default 1500, mapped to Glicko-2 scale)
     *   φ (phi)   = rating deviation (uncertainty, default 350)
     *   σ (sigma) = volatility (how much rating fluctuates, default 0.06)
     */
    
    // Constants
    private static final double DEFAULT_RATING = 1500.0;
    private static final double DEFAULT_RD = 350.0;
    private static final double DEFAULT_VOLATILITY = 0.06;
    private static final double GLICKO2_SCALE = 173.7178; // 400 / ln(10)
    private static final double TAU = 0.5; // System constant controlling volatility change
    private static final double CONVERGENCE_TOLERANCE = 0.000001;

    /**
     * Recalculate ratings for all participants after a contest ends.
     * Runs as an async job triggered by "contest-ended" Kafka event.
     */
    @Async
    public void recalculateRatings(UUID contestId) {
        Contest contest = contestService.getContest(contestId);
        List<ContestStanding> standings = leaderboardService.getFinalStandings(contestId);
        
        if (standings.size() < 2) return; // Need at least 2 participants
        
        // Load current ratings for all participants
        Map<UUID, UserRating> currentRatings = ratingRepo.findByUserIds(
            standings.stream().map(ContestStanding::getUserId).toList()
        );
        
        // For each participant, compute new rating based on performance vs all others
        List<RatingUpdate> updates = new ArrayList<>();
        
        for (int i = 0; i < standings.size(); i++) {
            ContestStanding player = standings.get(i);
            UserRating playerRating = currentRatings.getOrDefault(
                player.getUserId(),
                new UserRating(player.getUserId(), DEFAULT_RATING, DEFAULT_RD, DEFAULT_VOLATILITY)
            );
            
            // Convert to Glicko-2 scale
            double mu = (playerRating.getRating() - DEFAULT_RATING) / GLICKO2_SCALE;
            double phi = playerRating.getRd() / GLICKO2_SCALE;
            double sigma = playerRating.getVolatility();
            
            // Compute expected vs actual outcomes against all opponents
            double varianceInverse = 0.0;
            double delta = 0.0;
            
            for (int j = 0; j < standings.size(); j++) {
                if (i == j) continue;
                
                ContestStanding opponent = standings.get(j);
                UserRating oppRating = currentRatings.getOrDefault(
                    opponent.getUserId(),
                    new UserRating(opponent.getUserId(), DEFAULT_RATING, DEFAULT_RD, DEFAULT_VOLATILITY)
                );
                
                double muJ = (oppRating.getRating() - DEFAULT_RATING) / GLICKO2_SCALE;
                double phiJ = oppRating.getRd() / GLICKO2_SCALE;
                
                // g(φ) reduces impact of opponents with uncertain ratings
                double gPhiJ = 1.0 / Math.sqrt(1.0 + 3.0 * phiJ * phiJ / (Math.PI * Math.PI));
                
                // Expected score E(μ, μj, φj)
                double expected = 1.0 / (1.0 + Math.exp(-gPhiJ * (mu - muJ)));
                
                // Actual score: 1.0 if player ranked higher, 0.0 if lower, 0.5 if tied
                double actual = player.getRank() < opponent.getRank() ? 1.0 :
                                player.getRank() > opponent.getRank() ? 0.0 : 0.5;
                
                varianceInverse += gPhiJ * gPhiJ * expected * (1.0 - expected);
                delta += gPhiJ * (actual - expected);
            }
            
            double variance = 1.0 / varianceInverse;
            delta = variance * delta;
            
            // Step 5: Compute new volatility σ' using Illinois algorithm
            double newSigma = computeNewVolatility(sigma, phi, delta, variance);
            
            // Step 6: Update phi with new volatility
            double phiStar = Math.sqrt(phi * phi + newSigma * newSigma);
            
            // Step 7: Compute new rating deviation
            double newPhi = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + varianceInverse);
            
            // Step 8: Compute new rating
            double newMu = mu + newPhi * newPhi * (delta / variance);
            
            // Convert back to original scale
            double newRating = newMu * GLICKO2_SCALE + DEFAULT_RATING;
            double newRd = newPhi * GLICKO2_SCALE;
            
            // Clamp rating to reasonable bounds
            newRating = Math.max(0, Math.min(4000, newRating));
            
            updates.add(RatingUpdate.builder()
                .userId(player.getUserId())
                .contestId(contestId)
                .oldRating(playerRating.getRating())
                .newRating(newRating)
                .oldRd(playerRating.getRd())
                .newRd(newRd)
                .volatility(newSigma)
                .rank(player.getRank())
                .build());
        }
        
        // Batch update all ratings in a single transaction
        ratingRepo.batchUpdateRatings(updates);
        
        // Publish rating-update events for UI
        for (RatingUpdate u : updates) {
            kafka.send(new ProducerRecord<>("rating-updates", u.getUserId().toString(), u));
        }
        
        log.info("Ratings updated for {} participants in contest {}", updates.size(), contestId);
    }
    
    /**
     * Illinois algorithm for computing new volatility.
     * Iteratively solves: f(x) = 0 where x = ln(σ'^2)
     */
    private double computeNewVolatility(double sigma, double phi, double delta, double variance) {
        double a = Math.log(sigma * sigma);
        double deltaSq = delta * delta;
        double phiSq = phi * phi;
        
        // Upper bound
        double A = a;
        double B;
        if (deltaSq > phiSq + variance) {
            B = Math.log(deltaSq - phiSq - variance);
        } else {
            int k = 1;
            while (volatilityFunction(a - k * TAU, phiSq, deltaSq, variance, a) < 0) {
                k++;
            }
            B = a - k * TAU;
        }
        
        double fA = volatilityFunction(A, phiSq, deltaSq, variance, a);
        double fB = volatilityFunction(B, phiSq, deltaSq, variance, a);
        
        // Illinois algorithm (modified regula falsi)
        while (Math.abs(B - A) > CONVERGENCE_TOLERANCE) {
            double C = A + (A - B) * fA / (fB - fA);
            double fC = volatilityFunction(C, phiSq, deltaSq, variance, a);
            
            if (fC * fB <= 0) {
                A = B;
                fA = fB;
            } else {
                fA /= 2.0;
            }
            B = C;
            fB = fC;
        }
        
        return Math.exp(A / 2.0);
    }
    
    private double volatilityFunction(double x, double phiSq, double deltaSq, double v, double a) {
        double ex = Math.exp(x);
        double d = phiSq + v + ex;
        return (ex * (deltaSq - d)) / (2.0 * d * d) - (x - a) / (TAU * TAU);
    }
}
```

**Rating Tiers** (displayed as badges):
```
┌──────────────┬────────────────┬──────────┐
│ Tier         │ Rating Range   │ Color    │
├──────────────┼────────────────┼──────────┤
│ Newbie       │ 0 - 1199       │ Gray     │
│ Pupil        │ 1200 - 1399    │ Green    │
│ Specialist   │ 1400 - 1599    │ Cyan     │
│ Expert       │ 1600 - 1899    │ Blue     │
│ Candidate    │ 1900 - 2099    │ Purple   │
│ Master       │ 2100 - 2299    │ Orange   │
│ Intl Master  │ 2300 - 2499    │ Orange   │
│ Grandmaster  │ 2500 - 2999    │ Red      │
│ Legendary    │ 3000+          │ Red+Star │
└──────────────┴────────────────┴──────────┘
```

**Post-Contest Workflow**:
```
1. Contest ends → "contest-ended" event published to Kafka
2. Rating Service consumes event (async, 30-60s delay)
3. For each of 100K participants:
   - Load current (μ, φ, σ) from DB
   - Compute performance against all opponents (O(N²) but parallelizable)
   - Calculate new (μ', φ', σ')
4. Batch-write all updates in single transaction
5. Publish per-user rating-update events → WebSocket → user sees "+42" or "-15"
6. Total time: ~2-3 minutes for 100K participants (parallelized across 16 threads)
```

---

### Deep Dive 8: Plagiarism Detection Pipeline

**The Problem**: In competitive programming, code copying is rampant. After a contest, we must detect pairs of suspiciously similar submissions to maintain contest integrity. This is a batch process that runs post-contest.

**Approach**: We use a two-stage pipeline: (1) fast winnowing-based fingerprinting to find candidate pairs, then (2) AST-based structural comparison for confirmation.

```java
public class PlagiarismDetectionService {

    /**
     * MOSS-inspired plagiarism detection using Winnowing algorithm.
     * Runs as a batch job after each contest ends.
     *
     * Stage 1: Fingerprint all submissions using k-gram winnowing
     * Stage 2: Compare fingerprint overlap between pairs
     * Stage 3: Confirm with AST-level structural comparison
     */
    
    private static final int K_GRAM_SIZE = 25;      // Token window size
    private static final int WINDOW_SIZE = 10;       // Winnowing window
    private static final double SIMILARITY_THRESHOLD = 0.70; // 70% fingerprint overlap → suspect
    private static final double AST_THRESHOLD = 0.80;        // 80% AST similarity → confirmed

    /**
     * Run plagiarism detection for a contest.
     * Triggered by "contest-ended" Kafka event (runs async, minutes after contest).
     */
    @Async
    public PlagiarismReport detectPlagiarism(UUID contestId) {
        Contest contest = contestService.getContest(contestId);
        List<PlagiarismCandidate> candidates = new ArrayList<>();
        
        // Process each problem independently
        for (UUID problemId : contest.getProblemIds()) {
            // Get all ACCEPTED submissions for this problem (best per user)
            List<Submission> accepted = submissionRepo.findBestAccepted(contestId, problemId);
            
            if (accepted.size() < 2) continue;
            
            // Stage 1: Compute fingerprints for all submissions
            Map<UUID, Set<Long>> fingerprints = new ConcurrentHashMap<>();
            accepted.parallelStream().forEach(sub -> {
                String normalized = normalizeCode(sub.getCode(), sub.getLanguage());
                Set<Long> fps = winnowingFingerprint(normalized);
                fingerprints.put(sub.getSubmissionId(), fps);
            });
            
            // Stage 2: Pairwise fingerprint comparison
            List<Submission> subList = new ArrayList<>(accepted);
            for (int i = 0; i < subList.size(); i++) {
                for (int j = i + 1; j < subList.size(); j++) {
                    Submission a = subList.get(i);
                    Submission b = subList.get(j);
                    
                    double similarity = jaccardSimilarity(
                        fingerprints.get(a.getSubmissionId()),
                        fingerprints.get(b.getSubmissionId())
                    );
                    
                    if (similarity >= SIMILARITY_THRESHOLD) {
                        candidates.add(new PlagiarismCandidate(a, b, similarity, problemId));
                    }
                }
            }
        }
        
        // Stage 3: Confirm with AST comparison (expensive, only for candidates)
        List<PlagiarismMatch> confirmedMatches = new ArrayList<>();
        for (PlagiarismCandidate candidate : candidates) {
            double astSimilarity = compareAST(candidate.getSubmissionA(), candidate.getSubmissionB());
            
            if (astSimilarity >= AST_THRESHOLD) {
                confirmedMatches.add(PlagiarismMatch.builder()
                    .submissionA(candidate.getSubmissionA().getSubmissionId())
                    .submissionB(candidate.getSubmissionB().getSubmissionId())
                    .userA(candidate.getSubmissionA().getUserId())
                    .userB(candidate.getSubmissionB().getUserId())
                    .problemId(candidate.getProblemId())
                    .fingerprintSimilarity(candidate.getSimilarity())
                    .astSimilarity(astSimilarity)
                    .build());
            }
        }
        
        // Save report and flag users
        PlagiarismReport report = PlagiarismReport.builder()
            .contestId(contestId)
            .totalSubmissions(/* total */)
            .candidatePairs(candidates.size())
            .confirmedMatches(confirmedMatches.size())
            .matches(confirmedMatches)
            .build();
        
        plagiarismRepo.save(report);
        
        // Notify admins for manual review
        if (!confirmedMatches.isEmpty()) {
            notificationService.notifyAdmins("Plagiarism detected in contest " + contestId,
                confirmedMatches.size() + " confirmed matches found");
        }
        
        return report;
    }
    
    /**
     * Normalize code: remove comments, whitespace, rename variables to canonical names.
     * This prevents trivial obfuscation like renaming variables or adding spaces.
     */
    private String normalizeCode(String code, String language) {
        // Step 1: Remove comments (// and /* */ for Java/C++, # for Python)
        code = removeComments(code, language);
        
        // Step 2: Tokenize (language-specific lexer)
        List<Token> tokens = tokenize(code, language);
        
        // Step 3: Replace variable names with canonical names (V1, V2, ...)
        Map<String, String> varMapping = new HashMap<>();
        int varCounter = 0;
        List<String> normalizedTokens = new ArrayList<>();
        
        for (Token token : tokens) {
            if (token.getType() == TokenType.IDENTIFIER && !isKeyword(token.getValue(), language)) {
                String canonical = varMapping.computeIfAbsent(token.getValue(), 
                    k -> "V" + (varCounter));
                normalizedTokens.add(canonical);
            } else if (token.getType() != TokenType.WHITESPACE) {
                normalizedTokens.add(token.getValue());
            }
        }
        
        return String.join(" ", normalizedTokens);
    }
    
    /**
     * Winnowing algorithm: select minimum hash from each window of k-gram hashes.
     * Produces a compact fingerprint that is robust to small insertions/deletions.
     */
    private Set<Long> winnowingFingerprint(String normalizedCode) {
        // Compute k-gram hashes
        List<Long> kgramHashes = new ArrayList<>();
        for (int i = 0; i <= normalizedCode.length() - K_GRAM_SIZE; i++) {
            String kgram = normalizedCode.substring(i, i + K_GRAM_SIZE);
            kgramHashes.add(rollingHash(kgram));
        }
        
        // Winnowing: select minimum hash from each window
        Set<Long> fingerprint = new HashSet<>();
        Deque<long[]> window = new ArrayDeque<>(); // [hash, position]
        
        for (int i = 0; i < kgramHashes.size(); i++) {
            long hash = kgramHashes.get(i);
            
            // Remove elements outside the window
            while (!window.isEmpty() && window.peekFirst()[1] <= i - WINDOW_SIZE) {
                window.pollFirst();
            }
            
            // Remove elements larger than current (they'll never be selected)
            while (!window.isEmpty() && window.peekLast()[0] >= hash) {
                window.pollLast();
            }
            
            window.addLast(new long[]{hash, i});
            
            // The front of the deque is the minimum in current window
            if (i >= WINDOW_SIZE - 1) {
                fingerprint.add(window.peekFirst()[0]);
            }
        }
        
        return fingerprint;
    }
    
    /** Jaccard similarity between two fingerprint sets */
    private double jaccardSimilarity(Set<Long> a, Set<Long> b) {
        Set<Long> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * AST comparison: parse both submissions into ASTs, then compute tree edit distance.
     * More expensive but catches structural plagiarism (reordered functions, different syntax).
     */
    private double compareAST(Submission a, Submission b) {
        if (!a.getLanguage().equals(b.getLanguage())) {
            return 0.0; // Cross-language comparison not supported
        }
        ASTNode treeA = parseToAST(a.getCode(), a.getLanguage());
        ASTNode treeB = parseToAST(b.getCode(), b.getLanguage());
        int editDistance = computeTreeEditDistance(treeA, treeB);
        int maxNodes = Math.max(treeA.nodeCount(), treeB.nodeCount());
        return maxNodes == 0 ? 0.0 : 1.0 - ((double) editDistance / maxNodes);
    }
}
```

**Pipeline Architecture**:
```
Contest Ends → Kafka "contest-ended"
    ↓
Plagiarism Service (async batch job, ~5-10 min)
    ↓
┌─────────────────────────────────────────┐
│ Stage 1: Normalize + Fingerprint        │
│   • Remove comments, rename variables   │
│   • Winnowing k-gram fingerprints       │
│   • O(N) per submission, parallelized   │
├─────────────────────────────────────────┤
│ Stage 2: Pairwise Fingerprint Compare   │
│   • Jaccard similarity on fingerprints  │
│   • O(N²) but fast (set operations)     │
│   • Filter: keep pairs > 70% similar    │
├─────────────────────────────────────────┤
│ Stage 3: AST Structural Comparison      │
│   • Parse to AST, tree edit distance    │
│   • Only for candidate pairs (~0.1%)    │
│   • Confirm if > 80% structural match   │
└─────────────────────────────────────────┘
    ↓
Results → DB + Admin Notification
```

---

### Deep Dive 9: Special Judge — Floating-Point & Multiple Valid Answers

**The Problem**: Not all problems have a single correct output. Floating-point problems accept answers within ε tolerance. Graph problems may have multiple valid orderings. Interactive problems require bidirectional communication. We need a flexible judge framework.

```java
public class SpecialJudgeService {

    /**
     * Pluggable judge framework.
     * STANDARD: exact string match (default)
     * FLOAT_TOLERANCE: accept if |actual - expected| < epsilon
     * CUSTOM_CHECKER: run admin-provided checker program
     * UNORDERED_LINES: output lines can be in any order
     * PARTIAL_SCORING: weighted test cases with partial credit
     */
    
    public enum JudgeType {
        STANDARD,
        FLOAT_TOLERANCE,
        CUSTOM_CHECKER,
        UNORDERED_LINES,
        PARTIAL_SCORING
    }
    
    /**
     * Compare actual output with expected output using the appropriate judge type.
     */
    public JudgeVerdict compare(Problem problem, String actualOutput, 
                                 String expectedOutput, String userInput) {
        JudgeType type = problem.getJudgeType();
        
        return switch (type) {
            case STANDARD -> standardCompare(actualOutput, expectedOutput);
            case FLOAT_TOLERANCE -> floatCompare(actualOutput, expectedOutput, problem.getEpsilon());
            case CUSTOM_CHECKER -> customCheckerCompare(problem, userInput, actualOutput, expectedOutput);
            case UNORDERED_LINES -> unorderedCompare(actualOutput, expectedOutput);
            case PARTIAL_SCORING -> partialScoringCompare(actualOutput, expectedOutput, problem);
        };
    }
    
    /**
     * Standard judge: exact string match after trimming.
     * Each line is compared independently, trailing whitespace ignored.
     */
    private JudgeVerdict standardCompare(String actual, String expected) {
        String[] actualLines = actual.strip().split("\n");
        String[] expectedLines = expected.strip().split("\n");
        
        if (actualLines.length != expectedLines.length) {
            return JudgeVerdict.wrongAnswer(
                "Expected " + expectedLines.length + " lines, got " + actualLines.length);
        }
        
        for (int i = 0; i < expectedLines.length; i++) {
            if (!actualLines[i].strip().equals(expectedLines[i].strip())) {
                return JudgeVerdict.wrongAnswer(
                    "Line " + (i + 1) + ": expected '" + expectedLines[i].strip() 
                    + "', got '" + actualLines[i].strip() + "'");
            }
        }
        
        return JudgeVerdict.accepted();
    }
    
    /**
     * Float tolerance judge: for problems where output is floating point.
     * Accepts if |actual - expected| <= epsilon OR |actual - expected| / |expected| <= epsilon.
     * Uses both absolute and relative error to handle small and large numbers.
     */
    private JudgeVerdict floatCompare(String actual, String expected, double epsilon) {
        String[] actualTokens = actual.strip().split("\\s+");
        String[] expectedTokens = expected.strip().split("\\s+");
        
        if (actualTokens.length != expectedTokens.length) {
            return JudgeVerdict.wrongAnswer(
                "Token count mismatch: expected " + expectedTokens.length 
                + ", got " + actualTokens.length);
        }
        
        for (int i = 0; i < expectedTokens.length; i++) {
            try {
                double actualVal = Double.parseDouble(actualTokens[i]);
                double expectedVal = Double.parseDouble(expectedTokens[i]);
                
                double absoluteError = Math.abs(actualVal - expectedVal);
                double relativeError = expectedVal != 0.0 
                    ? Math.abs((actualVal - expectedVal) / expectedVal) 
                    : absoluteError;
                
                if (absoluteError > epsilon && relativeError > epsilon) {
                    return JudgeVerdict.wrongAnswer(String.format(
                        "Token %d: expected %.10f, got %.10f (abs_err=%.2e, rel_err=%.2e, eps=%.2e)",
                        i + 1, expectedVal, actualVal, absoluteError, relativeError, epsilon));
                }
            } catch (NumberFormatException e) {
                // Not a number — fall back to exact string match
                if (!actualTokens[i].equals(expectedTokens[i])) {
                    return JudgeVerdict.wrongAnswer(
                        "Token " + (i + 1) + ": expected '" + expectedTokens[i] 
                        + "', got '" + actualTokens[i] + "'");
                }
            }
        }
        
        return JudgeVerdict.accepted();
    }
    
    /**
     * Custom checker: admin uploads a checker program that reads:
     *   - input (the test case input)
     *   - expected output (the model answer)
     *   - contestant output (the user's answer)
     * and returns exit code 0 (accepted) or 1 (wrong answer) with explanation.
     *
     * Used for problems with multiple valid answers (e.g., "any valid topological sort").
     */
    private JudgeVerdict customCheckerCompare(Problem problem, String input,
                                               String actualOutput, String expectedOutput) {
        // Checker runs in the same sandbox environment
        ContainerConfig config = ContainerConfig.builder()
            .image("judge-checker:latest")
            .cpuLimit(1.0)
            .memoryLimit("256m")
            .networkDisabled(true)
            .readOnlyRootFilesystem(true)
            .tmpfsMount("/tmp", "32m")
            .build();
        
        String containerId = docker.createContainer(config);
        
        try {
            // Write checker program (pre-compiled by admin)
            docker.copyToContainer(containerId, "/tmp/checker", problem.getCheckerBinary());
            docker.copyToContainer(containerId, "/tmp/input.txt", input.getBytes());
            docker.copyToContainer(containerId, "/tmp/expected.txt", expectedOutput.getBytes());
            docker.copyToContainer(containerId, "/tmp/actual.txt", actualOutput.getBytes());
            
            // Run checker: checker <input> <expected> <actual>
            ExecResult result = docker.exec(containerId,
                List.of("/tmp/checker", "/tmp/input.txt", "/tmp/expected.txt", "/tmp/actual.txt"),
                Duration.ofSeconds(30));
            
            if (result.getExitCode() == 0) {
                return JudgeVerdict.accepted();
            } else {
                String reason = result.getStdout().isEmpty() 
                    ? "Checker rejected the answer" 
                    : result.getStdout().trim();
                return JudgeVerdict.wrongAnswer(reason);
            }
        } finally {
            docker.removeContainer(containerId);
        }
    }
    
    /**
    /**
     * Unordered lines: each line must appear in expected output but order doesn't matter.
     * Used for problems like "print all permutations" or "find all connected components".
     */
    private JudgeVerdict unorderedCompare(String actual, String expected) {
        List<String> actualLines = Arrays.stream(actual.strip().split("\n"))
            .map(String::strip).sorted().toList();
        List<String> expectedLines = Arrays.stream(expected.strip().split("\n"))
            .map(String::strip).sorted().toList();
        
        if (!actualLines.equals(expectedLines)) {
            return JudgeVerdict.wrongAnswer("Output lines don't match (order-independent comparison)");
        }
        return JudgeVerdict.accepted();
    }
    
    /**
     * Partial scoring: each test case has a weight. Score = sum(weight for passed tests) / total_weight.
     * Used in IOI-style contests where partial credit is awarded.
     */
    private JudgeVerdict partialScoringCompare(String actual, String expected, Problem problem) {
        String[] actualLines = actual.strip().split("\n");
        String[] expectedLines = expected.strip().split("\n");
        
        int totalWeight = 0;
        int earnedWeight = 0;
        
        for (int i = 0; i < expectedLines.length && i < actualLines.length; i++) {
            int weight = problem.getTestCaseWeight(i);
            totalWeight += weight;
            if (actualLines[i].strip().equals(expectedLines[i].strip())) {
                earnedWeight += weight;
            }
        }
        
        double score = totalWeight > 0 ? (double) earnedWeight / totalWeight : 0.0;
        
        if (score >= 1.0) {
            return JudgeVerdict.accepted();
        } else {
            return JudgeVerdict.partiallyAccepted(score, earnedWeight, totalWeight);
        }
    }
}
```

**Judge Type Selection Guide**:
```
┌───────────────────────────┬────────────────────┬─────────────────────────────┐
│ Problem Type              │ Judge Type         │ Example                     │
├───────────────────────────┼────────────────────┼─────────────────────────────┤
│ Single correct answer     │ STANDARD           │ Two Sum, Binary Search      │
│ Floating-point output     │ FLOAT_TOLERANCE    │ Geometry, probability       │
│ Multiple valid answers    │ CUSTOM_CHECKER     │ Any topological sort        │
│ Set of items (any order)  │ UNORDERED_LINES    │ All permutations            │
│ IOI-style scoring         │ PARTIAL_SCORING    │ Optimization problems       │
└───────────────────────────┴────────────────────┴─────────────────────────────┘
```

---

### Deep Dive 10: Interactive Problems — Bidirectional Judge Communication

**The Problem**: Interactive problems require the judge to communicate with the user's program in real time. The user's program asks queries (e.g., "Is the number > 50?"), the judge responds, and the user's program narrows down the answer. This requires bidirectional pipe communication with strict protocol enforcement.

```java
public class InteractiveJudgeWorker {

    /**
     * Interactive judge: the user's program communicates with a judge program via stdin/stdout pipes.
     * 
     * Protocol:
     *   1. Judge writes initial data to user's stdin (e.g., "I have a hidden number 1-1000")
     *   2. User's program writes a query to stdout (e.g., "? 500")
     *   3. Judge reads query, computes response, writes to user's stdin (e.g., "HIGHER")
     *   4. Repeat until user outputs final answer (e.g., "! 742")
     *   5. Judge verifies answer and returns verdict
     */
    public JudgeResult executeInteractive(Submission submission, Problem problem, TestCase testCase) {
        String containerId = createSandboxContainer(submission.getLanguage());
        
        try {
            ExecResult compileResult = compileInContainer(containerId, submission);
            if (compileResult.getExitCode() != 0) {
                return JudgeResult.compilationError(compileResult.getStderr());
            }
            
            ProcessPipes userProcess = docker.execInteractive(containerId,
                getRunCommand(submission.getLanguage()));
            
            InteractorProgram interactor = loadInteractor(problem.getInteractorCode(), testCase);
            
            int queryCount = 0;
            int maxQueries = problem.getMaxQueries();
            Instant deadline = Instant.now().plusMillis(problem.getTimeLimitMs() * 2L);
            
            String initialMessage = interactor.getInitialMessage();
            if (initialMessage != null) {
                userProcess.writeToStdin(initialMessage + "\n");
            }
            
            while (Instant.now().isBefore(deadline)) {
                String userOutput = userProcess.readLineFromStdout(Duration.ofSeconds(5));
                
                if (userOutput == null) {
                    if (userProcess.hasExited()) {
                        return JudgeResult.runtimeError(0, 1, "Process exited without answer");
                    }
                    return JudgeResult.timeLimitExceeded(0, 1, testCase);
                }
                
                userOutput = userOutput.trim();
                
                if (userOutput.startsWith("!")) {
                    String answer = userOutput.substring(1).trim();
                    boolean correct = interactor.verifyAnswer(answer);
                    if (correct) {
                        return JudgeResult.accepted(1, 0, 0);
                    } else {
                        return JudgeResult.wrongAnswer(0, 1, testCase, 
                            "Wrong answer after " + queryCount + " queries");
                    }
                }
                
                if (userOutput.startsWith("?")) {
                    queryCount++;
                    if (queryCount > maxQueries) {
                        return JudgeResult.wrongAnswer(0, 1, testCase,
                            "Exceeded maximum queries: " + maxQueries);
                    }
                    
                    String query = userOutput.substring(1).trim();
                    String response = interactor.processQuery(query);
                    
                    if (response == null) {
                        return JudgeResult.wrongAnswer(0, 1, testCase, "Invalid query: " + query);
                    }
                    
                    userProcess.writeToStdin(response + "\n");
                } else {
                    return JudgeResult.wrongAnswer(0, 1, testCase,
                        "Expected '?' for query or '!' for answer, got: " + userOutput);
                }
            }
            
            return JudgeResult.timeLimitExceeded(0, 1, testCase);
        } finally {
            docker.removeContainer(containerId);
        }
    }
}

/**
 * Example interactor for "Guess the Number" problem:
 * Judge picks a number 1-N. User binary-searches with "? X" queries.
 * Judge responds "HIGHER", "LOWER", or "CORRECT".
 */
public class GuessNumberInteractor implements InteractorProgram {
    private final int hiddenNumber;
    private final int maxN;
    
    public GuessNumberInteractor(TestCase testCase) {
        this.maxN = Integer.parseInt(testCase.getInput().trim());
        this.hiddenNumber = Integer.parseInt(testCase.getExpectedOutput().trim());
    }
    
    @Override
    public String getInitialMessage() { return String.valueOf(maxN); }
    
    @Override
    public String processQuery(String query) {
        try {
            int guess = Integer.parseInt(query.trim());
            if (guess < 1 || guess > maxN) return null;
            if (guess < hiddenNumber) return "HIGHER";
            if (guess > hiddenNumber) return "LOWER";
            return "CORRECT";
        } catch (NumberFormatException e) { return null; }
    }
    
    @Override
    public boolean verifyAnswer(String answer) {
        try { return Integer.parseInt(answer.trim()) == hiddenNumber; }
        catch (NumberFormatException e) { return false; }
    }
}
```

**Interactive Problem Architecture**:
```
┌─────────────────┐     pipes      ┌─────────────────┐
│  User's Program  │ ←──stdin───── │  Interactor      │
│  (in sandbox)    │ ──stdout────→ │  (judge-side)    │
│                  │               │                  │
│  "? 500"  ──────────────────────→│  "HIGHER"        │
│  "? 750"  ──────────────────────→│  "LOWER"         │
│  "? 625"  ──────────────────────→│  "CORRECT"       │
│  "! 625"  ──────────────────────→│  ✓ Accepted      │
└─────────────────┘               └─────────────────┘

Constraints:
  • Max queries per test case (e.g., ceil(log2(N)) + 1)
  • Total time limit for entire interaction
  • User MUST flush stdout after each write
  • Invalid queries → instant Wrong Answer
```

---

### Deep Dive 11: Contest Replay & Virtual Contests

**The Problem**: Users who missed a live contest want to experience it as if it were real — same problems, same time limit, personal leaderboard. Virtual contests let users "replay" past contests with their own timer, getting a simulated rank at the end.

```java
public class VirtualContestService {

    /**
     * Virtual contest: a personal replay of a past contest.
     * The user starts their own timer, sees the same problems, and gets a simulated rank
     * based on how their performance compares to the original contest standings.
     */
    
    public VirtualContestSession startVirtual(UUID userId, UUID originalContestId) {
        Contest original = contestService.getContest(originalContestId);
        
        if (original.getStatus() != ContestStatus.ENDED) {
            throw new IllegalStateException("Can only create virtual contests for ended contests");
        }
        if (leaderboardService.wasParticipant(originalContestId, userId)) {
            throw new IllegalStateException("Cannot virtual a contest you participated in");
        }
        
        Duration contestDuration = Duration.between(original.getStartsAt(), original.getEndsAt());
        Instant virtualStart = Instant.now();
        Instant virtualEnd = virtualStart.plus(contestDuration);
        
        VirtualContestSession session = VirtualContestSession.builder()
            .sessionId(UUID.randomUUID())
            .userId(userId)
            .originalContestId(originalContestId)
            .virtualStartsAt(virtualStart)
            .virtualEndsAt(virtualEnd)
            .problemIds(original.getProblemIds())
            .status(VirtualStatus.ACTIVE)
            .build();
        
        virtualSessionRepo.save(session);
        
        // Personal leaderboard in Redis with TTL
        String sessionKey = "virtual:" + session.getSessionId();
        redis.set(sessionKey + ":start", virtualStart.toString());
        redis.expire(sessionKey + ":start", contestDuration.getSeconds() + 3600);
        
        // Schedule auto-end
        schedulerService.scheduleOnce("virtual-end:" + session.getSessionId(),
            virtualEnd, () -> endVirtualContest(session.getSessionId()));
        
        return session;
    }
    
    /** Handle submission during virtual contest — same judging, personal leaderboard. */
    public void onVirtualSubmissionJudged(SubmissionJudgedEvent event) {
        VirtualContestSession session = virtualSessionRepo.findActiveByUserId(event.getUserId());
        if (session == null || Instant.now().isAfter(session.getVirtualEndsAt())) return;
        if (!"ACCEPTED".equals(event.getVerdict())) return;
        
        String sessionKey = "virtual:" + session.getSessionId();
        String problemId = event.getProblemId();
        
        if (redis.sismember(sessionKey + ":solved", problemId)) return;
        redis.sadd(sessionKey + ":solved", problemId);
        
        long minutesFromStart = Duration.between(
            session.getVirtualStartsAt(), event.getSubmittedAt()).toMinutes();
        int failedAttempts = getVirtualFailedAttempts(session.getSessionId(), problemId);
        long penalty = minutesFromStart + (failedAttempts * 20L);
        redis.incrBy(sessionKey + ":penalty", penalty);
    }
    
    /**
     * End virtual contest and compute simulated rank.
     * Compares user's performance against original contest standings.
     */
    public VirtualContestResult endVirtualContest(UUID sessionId) {
        VirtualContestSession session = virtualSessionRepo.findById(sessionId);
        if (session.getStatus() == VirtualStatus.ENDED) return null;
        
        session.setStatus(VirtualStatus.ENDED);
        virtualSessionRepo.save(session);
        
        String sessionKey = "virtual:" + session.getSessionId();
        int problemsSolved = redis.scard(sessionKey + ":solved").intValue();
        String penaltyStr = redis.get(sessionKey + ":penalty");
        long totalPenalty = penaltyStr != null ? Long.parseLong(penaltyStr) : 0;
        
        double userScore = (problemsSolved * 1_000_000.0) - totalPenalty;
        
        // Where would this user have placed in the original contest?
        String originalLb = "leaderboard:" + session.getOriginalContestId();
        long betterCount = redis.zcount(originalLb, String.valueOf(userScore + 0.001), "+inf");
        long simulatedRank = betterCount + 1;
        long totalParticipants = redis.zcard(originalLb);
        
        double percentile = totalParticipants > 0
            ? ((totalParticipants - simulatedRank + 1.0) / totalParticipants) * 100.0 : 0.0;
        
        VirtualContestResult result = VirtualContestResult.builder()
            .sessionId(sessionId).userId(session.getUserId())
            .problemsSolved(problemsSolved).totalPenalty(totalPenalty)
            .simulatedRank(simulatedRank).totalParticipants(totalParticipants)
            .percentile(percentile).build();
        
        virtualResultRepo.save(result);
        redis.del(sessionKey + ":start", sessionKey + ":solved", sessionKey + ":penalty");
        return result;
    }
}
```

**Virtual Contest UX**:
```
User clicks "Virtual Contest" on a past contest page
    ↓
Personal timer starts (same duration as original)
    ↓
User solves problems at their own pace
    ↓
Timer expires (or user clicks "End Early")
    ↓
"You would have placed #1,234 out of 98,432 (top 1.3%)"
    ↓
Note: Virtual contest does NOT affect your official rating
```

---

### Deep Dive 12: Code Playground — "Run" Mode Without Submitting

**The Problem**: Users want to test their code against custom inputs before submitting. This "Run" mode compiles and executes code against user-provided input (not hidden test cases) and returns the raw stdout/stderr. It's lighter than a full submission — no verdict, no leaderboard update, no permanent storage.

```java
public class PlaygroundService {

    /**
     * Run user code against custom input without submitting.
     * Lighter than full submission: no verdict, no DB write, no Kafka.
     * Uses a dedicated smaller worker pool to avoid starving the judge queue.
     */
    private final WorkerPool playgroundWorkerPool; // Separate pool, 200 workers
    
    public PlaygroundResult run(UUID userId, PlaygroundRequest request) {
        // Rate limit: prevent abuse (crypto mining, DoS)
        if (!rateLimiter.tryAcquire(userId, 5, Duration.ofMinutes(1))) {
            throw new RateLimitExceededException("Maximum 5 playground runs per minute");
        }
        
        if (request.getCode().length() > 50_000) {
            throw new ValidationException("Code size exceeds 50 KB limit");
        }
        if (request.getInput() != null && request.getInput().length() > 100_000) {
            throw new ValidationException("Custom input exceeds 100 KB limit");
        }
        
        JudgeWorkerClient worker = playgroundWorkerPool.acquireWorker(Duration.ofSeconds(10));
        if (worker == null) {
            throw new ServiceUnavailableException("Playground busy, try again shortly");
        }
        
        try {
            ContainerConfig config = ContainerConfig.builder()
                .image(getLanguageImage(request.getLanguage()))
                .cpuLimit(1.0)              // 1 core (vs 2 for judge)
                .memoryLimit("256m")        // 256 MB (vs 512 for judge)
                .networkDisabled(true)
                .readOnlyRootFilesystem(true)
                .tmpfsMount("/tmp", "32m")
                .pidsLimit(50)
                .build();
            
            String containerId = docker.createContainer(config);
            
            try {
                docker.copyToContainer(containerId,
                    "/tmp/solution" + getExtension(request.getLanguage()),
                    request.getCode().getBytes());
                
                ExecResult compileResult = docker.exec(containerId,
                    getCompileCommand(request.getLanguage()), Duration.ofSeconds(15));
                
                if (compileResult.getExitCode() != 0) {
                    return PlaygroundResult.builder()
                        .status("COMPILATION_ERROR")
                        .stderr(truncate(compileResult.getStderr(), 5000))
                        .compileDurationMs(compileResult.getDurationMs())
                        .build();
                }
                
                String input = request.getInput() != null ? request.getInput() : "";
                docker.copyToContainer(containerId, "/tmp/input.txt", input.getBytes());
                
                ExecResult runResult = docker.exec(containerId,
                    getRunCommand(request.getLanguage()) + " < /tmp/input.txt",
                    Duration.ofSeconds(10));
                
                return PlaygroundResult.builder()
                    .status(runResult.isTimedOut() ? "TIME_LIMIT_EXCEEDED" :
                            runResult.getExitCode() != 0 ? "RUNTIME_ERROR" : "SUCCESS")
                    .stdout(truncate(runResult.getStdout(), 10_000))
                    .stderr(truncate(runResult.getStderr(), 5_000))
                    .runtimeMs(runResult.getDurationMs())
                    .memoryKb(runResult.getMemoryKb())
                    .compileDurationMs(compileResult.getDurationMs())
                    .build();
            } finally {
                docker.removeContainer(containerId);
            }
        } finally {
            playgroundWorkerPool.releaseWorker(worker);
        }
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\n... (output truncated at " + maxLen + " chars)";
    }
}
```

**Playground vs Submit Comparison**:
```
┌─────────────────────┬──────────────────┬──────────────────┐
│ Aspect              │ Playground (Run) │ Submit (Judge)   │
├─────────────────────┼──────────────────┼──────────────────┤
│ Test cases          │ Custom (1 only)  │ All hidden tests │
│ Verdict             │ None (raw output)│ AC/WA/TLE/MLE/RE │
│ Leaderboard update  │ No               │ Yes (if contest) │
│ Stored in DB        │ No (ephemeral)   │ Yes (permanent)  │
│ Worker pool         │ Separate (200)   │ Main (1K-5K)     │
│ Time limit          │ 10 seconds       │ Per-problem limit│
│ CPU/Memory limit    │ 1 core / 256 MB  │ 2 cores / 512 MB│
│ Rate limit          │ 5/min per user   │ 20/min per user  │
│ Queue               │ Direct (no Kafka)│ Kafka queue      │
└─────────────────────┴──────────────────┴──────────────────┘
```

---

### Deep Dive 13: Submission Rate Limiting & Anti-Abuse

**The Problem**: Without rate limiting, a single user could: (1) DoS the judge by submitting thousands of times, (2) brute-force answers by trying all possibilities, (3) abuse the playground for crypto mining. We need multi-layer rate limiting that's fair during contests.

```java
public class SubmissionRateLimiter {

    /**
     * Multi-layer rate limiting for submissions.
     * 
     * Layer 1: Per-user global limit (fixed window)
     * Layer 2: Per-user per-problem limit (prevent brute-force)
     * Layer 3: Per-contest global limit (protect infrastructure)
     * Layer 4: Per-IP limit (prevent multi-account abuse)
     * 
     * Uses Redis for distributed rate limiting across all API servers.
     */
    private final JedisCluster redis;
    
    private static final int USER_GLOBAL_LIMIT = 20;          // 20 submissions per minute
    private static final int USER_PROBLEM_LIMIT = 5;          // 5 per problem per minute
    private static final int CONTEST_GLOBAL_LIMIT = 20_000;   // 20K per minute platform-wide
    private static final int IP_LIMIT = 30;                    // 30 per IP per minute
    
    public RateLimitResult checkSubmissionAllowed(SubmissionRequest request) {
        Instant now = Instant.now();
        String minuteWindow = String.valueOf(now.getEpochSecond() / 60);
        
        // Layer 1: Per-user global limit
        String userKey = "ratelimit:submit:user:" + request.getUserId() + ":" + minuteWindow;
        long userCount = redis.incr(userKey);
        if (userCount == 1) redis.expire(userKey, 120);
        
        if (userCount > USER_GLOBAL_LIMIT) {
            metrics.counter("ratelimit.rejected", "layer", "user_global").increment();
            return RateLimitResult.rejected(
                "Rate limit exceeded: max " + USER_GLOBAL_LIMIT + " submissions/min",
                retryAfterSeconds(now));
        }
        
        // Layer 2: Per-user per-problem limit (anti-brute-force)
        String problemKey = "ratelimit:submit:user_problem:"
            + request.getUserId() + ":" + request.getProblemId() + ":" + minuteWindow;
        long problemCount = redis.incr(problemKey);
        if (problemCount == 1) redis.expire(problemKey, 120);
        
        if (problemCount > USER_PROBLEM_LIMIT) {
            metrics.counter("ratelimit.rejected", "layer", "user_problem").increment();
            return RateLimitResult.rejected(
                "Too many submissions for this problem. Max " + USER_PROBLEM_LIMIT + "/min.",
                retryAfterSeconds(now));
        }
        
        // Layer 3: Platform-wide contest limit (circuit breaker)
        if (request.getContestId() != null) {
            String contestKey = "ratelimit:submit:contest:" + request.getContestId() + ":" + minuteWindow;
            long contestCount = redis.incr(contestKey);
            if (contestCount == 1) redis.expire(contestKey, 120);
            
            if (contestCount > CONTEST_GLOBAL_LIMIT) {
                metrics.counter("ratelimit.rejected", "layer", "contest_global").increment();
                log.error("Contest {} hit global rate limit: {}/min",
                    request.getContestId(), contestCount);
                return RateLimitResult.rejected(
                    "Platform experiencing high load. Please retry shortly.",
                    retryAfterSeconds(now));
            }
        }
        
        // Layer 4: Per-IP limit (anti-multi-account)
        String ipKey = "ratelimit:submit:ip:" + request.getClientIp() + ":" + minuteWindow;
        long ipCount = redis.incr(ipKey);
        if (ipCount == 1) redis.expire(ipKey, 120);
        
        if (ipCount > IP_LIMIT) {
            metrics.counter("ratelimit.rejected", "layer", "ip").increment();
            return RateLimitResult.rejected("Too many submissions from this IP.", retryAfterSeconds(now));
        }
        
        return RateLimitResult.allowed(
            USER_GLOBAL_LIMIT - (int) userCount,
            USER_PROBLEM_LIMIT - (int) problemCount);
    }
    
    private int retryAfterSeconds(Instant now) {
        long secondsUntilNextWindow = 60 - (now.getEpochSecond() % 60);
        return (int) Math.max(5, secondsUntilNextWindow);
    }
    
    /** During contest start burst, temporarily increase limits for 5 minutes. */
    public void onContestStarted(UUID contestId) {
        String burstKey = "ratelimit:burst:contest:" + contestId;
        redis.setex(burstKey, 300, "active");
        log.info("Contest {} — burst rate limits activated for 5 minutes", contestId);
    }
}
```

**Rate Limit Response Headers**:
```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1705348200
Retry-After: 23
Content-Type: application/json

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Maximum 20 submissions per minute. Please wait 23 seconds.",
  "retry_after_seconds": 23
}
```

**Rate Limit Layers Summary**:
```
┌─────────────────────┬───────────────┬──────────────────────────────────┐
│ Layer               │ Limit         │ Purpose                          │
├─────────────────────┼───────────────┼──────────────────────────────────┤
│ Per-user global     │ 20/min        │ Prevent single user DoS          │
│ Per-user/problem    │ 5/min         │ Prevent brute-force solving      │
│ Per-contest global  │ 20K/min       │ Infrastructure circuit breaker   │
│ Per-IP              │ 30/min        │ Prevent multi-account abuse      │
│ Playground          │ 5/min         │ Prevent crypto mining            │
│ Code size           │ 50 KB         │ Prevent memory abuse             │
│ Contest burst       │ 2x for 5 min  │ Accommodate contest start spike  │
└─────────────────────┴───────────────┴──────────────────────────────────┘
```

---

### Deep Dive 14: Judge Worker Health Monitoring & Fault Tolerance

**The Problem**: With 1,000-5,000 judge workers, some will inevitably fail — containers crash, nodes die, jobs hang. We need health monitoring to detect stuck workers, automatic retry for failed judgments, and circuit breaking to isolate bad nodes.

```java
public class JudgeWorkerHealthMonitor {

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_JUDGE_DURATION = Duration.ofMinutes(5);
    private static final double ERROR_RATE_THRESHOLD = 0.5;
    private static final int ERROR_WINDOW_SIZE = 20;
    
    private final ConcurrentMap<String, WorkerState> workerStates = new ConcurrentHashMap<>();
    
    @Scheduled(fixedRate = 5_000)
    public void checkWorkerHealth() {
        Instant now = Instant.now();
        
        for (Map.Entry<String, WorkerState> entry : workerStates.entrySet()) {
            String workerId = entry.getKey();
            WorkerState state = entry.getValue();
            
            // Check 1: Heartbeat timeout → mark as dead
            if (state.getLastHeartbeat() != null &&
                Duration.between(state.getLastHeartbeat(), now).compareTo(HEARTBEAT_TIMEOUT) > 0) {
                log.warn("Worker {} missed heartbeat, marking DEAD", workerId);
                state.setStatus(WorkerStatus.DEAD);
                if (state.getCurrentSubmissionId() != null) {
                    requeueSubmission(state.getCurrentSubmissionId(), "worker_dead");
                }
                metrics.counter("judge.worker.dead").increment();
                continue;
            }
            
            // Check 2: Stuck detection → kill and requeue
            if (state.getStatus() == WorkerStatus.BUSY && state.getJobStartedAt() != null &&
                Duration.between(state.getJobStartedAt(), now).compareTo(MAX_JUDGE_DURATION) > 0) {
                log.warn("Worker {} stuck > 5 min on {}, killing", workerId, state.getCurrentSubmissionId());
                killWorker(workerId);
                requeueSubmission(state.getCurrentSubmissionId(), "worker_stuck");
                state.setStatus(WorkerStatus.DEAD);
                metrics.counter("judge.worker.stuck").increment();
                continue;
            }
            
            // Check 3: Error rate → quarantine
            if (state.getRecentResults().size() >= ERROR_WINDOW_SIZE) {
                long failures = state.getRecentResults().stream()
                    .filter(r -> r == JudgeOutcome.INTERNAL_ERROR).count();
                double errorRate = (double) failures / state.getRecentResults().size();
                if (errorRate >= ERROR_RATE_THRESHOLD) {
                    log.warn("Worker {} error rate {}% — quarantined", workerId, (int)(errorRate*100));
                    state.setStatus(WorkerStatus.QUARANTINED);
                    metrics.counter("judge.worker.quarantined").increment();
                }
            }
        }
    }
    
    public void onHeartbeat(String workerId, WorkerHeartbeat heartbeat) {
        WorkerState state = workerStates.computeIfAbsent(workerId, k -> new WorkerState());
        state.setLastHeartbeat(Instant.now());
        state.setCpuUsage(heartbeat.getCpuPercent());
        state.setMemoryUsage(heartbeat.getMemoryPercent());
        if (state.getStatus() == WorkerStatus.DEAD) {
            state.setStatus(WorkerStatus.IDLE);
        }
    }
    
    public void onJudgeComplete(String workerId, JudgeOutcome outcome) {
        WorkerState state = workerStates.get(workerId);
        if (state == null) return;
        state.getRecentResults().add(outcome);
        if (state.getRecentResults().size() > ERROR_WINDOW_SIZE) {
            state.getRecentResults().removeFirst();
        }
        state.setStatus(WorkerStatus.IDLE);
        state.setCurrentSubmissionId(null);
    }
    
    /**
     * Requeue a submission that failed due to worker issues.
     * Exponential backoff, max 3 retries.
     */
    private void requeueSubmission(UUID submissionId, String reason) {
        Submission sub = submissionRepo.findById(submissionId);
        int retryCount = sub.getRetryCount() != null ? sub.getRetryCount() : 0;
        
        if (retryCount >= 3) {
            log.error("Submission {} failed after 3 retries → INTERNAL_ERROR", submissionId);
            sub.setVerdict("INTERNAL_ERROR");
            sub.setJudgeOutput("Judge failed after 3 attempts. Please resubmit.");
            submissionRepo.save(sub);
            return;
        }
        
        sub.setRetryCount(retryCount + 1);
        sub.setVerdict("PENDING");
        submissionRepo.save(sub);
        
        kafka.send(new ProducerRecord<>("submissions", sub.getProblemId().toString(), sub));
        log.info("Requeued submission {} (attempt {}/3, reason: {})",
            submissionId, retryCount + 1, reason);
        metrics.counter("judge.submission.requeued", "reason", reason).increment();
    }
}
```

**Worker Lifecycle State Machine**:
```
                    ┌─────────┐
                    │  IDLE   │◄────────────────┐
                    └────┬────┘                 │
                         │ assign job           │ judge complete
                         ▼                      │
                    ┌─────────┐                 │
                    │  BUSY   │─────────────────┘
                    └────┬────┘
                         │ heartbeat timeout / stuck / error rate
                         ▼
              ┌──────────┴──────────┐
              ▼                     ▼
         ┌─────────┐         ┌──────────────┐
         │  DEAD   │         │ QUARANTINED  │
         └────┬────┘         └──────┬───────┘
              │ heartbeat resumes   │ manual review
              ▼                     ▼
         ┌─────────┐         ┌─────────┐
         │  IDLE   │         │ REMOVED │
         └─────────┘         └─────────┘

Retry Policy:  Max 3 retries × exponential backoff (1s, 2s, 4s)
After 3 failures → INTERNAL_ERROR verdict → user notified
```

---

## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Judge isolation** | Docker + gVisor (8-layer security) | Untrusted code is #1 risk; defense-in-depth |
| **Judge queue** | Kafka + worker pool | Decouples submit from judge; handles burst; durable |
| **Worker scaling** | K8s HPA on Kafka lag | 100 → 5000 workers in minutes |
| **Leaderboard** | Redis sorted set | O(log N) rank queries; real-time updates |
| **Contest start** | Atomic Kafka event | All servers reveal problems simultaneously |
| **Verdict delivery** | WebSocket + polling fallback | Real-time UX; polling as safety net |
| **Rating system** | Glicko-2 | Handles uncertainty; inactive decay |
| **Plagiarism** | Winnowing → AST (two-stage) | Fast filtering then precise confirmation |
| **Special judge** | Pluggable framework (5 types) | Float, checker, unordered, partial, standard |
| **Interactive** | In-worker bidirectional pipes | Low latency; same sandbox security |
| **Playground** | Separate pool (200 workers) | Prevents starving judge queue |
| **Rate limiting** | 4-layer Redis | Per-user, per-problem, per-contest, per-IP |
| **Worker health** | Heartbeat + stuck + error rate | Auto-recovery with 3-retry policy |

## 🎯 Interview Talking Points

1. **"Sandboxing is defense-in-depth"** — 8 security layers. Any one layer can fail and the system remains safe.
2. **"Kafka decouples submission from judging"** — Users get instant 202. Queue absorbs contest-start bursts.
3. **"Auto-scale judges on Kafka lag"** — 100 baseline → 5000 during contests. Scale-up 30s, scale-down 5 min.
4. **"Leaderboard = Redis sorted set"** — Score = (solved × 1M) - penalty. ZREVRANGE for top-N, ZREVRANK for position.
5. **"Atomic contest start"** — Single Kafka event reveals all. No user sees problems early.
6. **"Submit → Event → Async everything"** — Sync path: validate + enqueue (< 100ms). Async: judge + leaderboard + WS.
7. **"Glicko-2 for fair ratings"** — RD captures uncertainty. New users volatile, veterans stable.
8. **"Winnowing + AST for plagiarism"** — Fingerprint O(N), pairwise Jaccard filters, AST confirms.
9. **"Interactive problems = bidirectional pipes"** — stdin/stdout between user program and judge interactor.
10. **"Virtual contests for practice"** — Personal replay, simulated rank against original standings.
11. **"Playground has its own worker pool"** — 200 dedicated workers don't starve the real judge queue.
12. **"4-layer rate limiting"** — Per-user (20/min), per-problem (5/min), per-contest (20K/min), per-IP (30/min).
13. **"Workers self-heal"** — Heartbeat, stuck detection, error rate quarantine. Max 3 retries.
14. **"5 comparison modes"** — STANDARD, FLOAT_TOLERANCE, CUSTOM_CHECKER, UNORDERED_LINES, PARTIAL_SCORING.

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 14 topics (choose 2-3 based on interviewer interest)
