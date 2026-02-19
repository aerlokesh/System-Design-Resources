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

## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Judge isolation** | Docker + gVisor (8-layer security) | User code = untrusted; defense-in-depth |
| **Judge queue** | Kafka + worker pool | Decouples submit from judge; handles burst |
| **Scaling** | K8s HPA on Kafka lag | 100 → 5000 workers in minutes |
| **Leaderboard** | Redis sorted set | O(log N) rank queries, real-time updates |
| **Contest start** | Atomic event via Kafka | All servers reveal problems simultaneously |
| **Verdict delivery** | WebSocket + polling fallback | Real-time UX; polling as safety net |

## 🎯 Interview Talking Points

1. **"Sandboxing is defense-in-depth"** — 8 security layers (Docker + gVisor + no-network + seccomp + PID limit + cgroup + timeout + readonly FS). Any one layer can fail and the system remains safe.
2. **"Kafka decouples submission from judging"** — Users get instant 202 Accepted. Judging happens asynchronously. Queue absorbs contest-start bursts.
3. **"Auto-scale judges on Kafka lag"** — 100 baseline workers → 5000 during contests. Scale-up in 30 seconds, scale-down after 5 minutes.
4. **"Leaderboard = Redis sorted set"** — Score = (solved × 1M) - penalty. ZREVRANGE for top-N, ZREVRANK for user's position. Both O(log N).
5. **"Atomic contest start"** — Problems pre-cached encrypted, single Kafka event reveals all. No user sees problems early.
6. **"Submit → Event → Async everything"** — Sync path: validate + enqueue (< 100ms). Async: judge + leaderboard + WebSocket + rating. Keeps user-facing latency low.

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Deep Dives**: 6 topics
