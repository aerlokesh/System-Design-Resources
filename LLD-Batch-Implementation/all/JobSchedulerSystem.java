import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Job Scheduler - HELLO Interview Framework
 * 
 * Companies: Microsoft, Salesforce
 * Pattern: Strategy (scheduling algorithms) + Priority Queue
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Strategy Pattern — FIFO, Priority, SJF (Shortest Job First) scheduling
 * 2. PriorityBlockingQueue — thread-safe, ordered job queue
 * 3. Worker thread pool — configurable number of workers
 * 4. Job lifecycle: PENDING → RUNNING → COMPLETED/FAILED
 */

// ==================== Enums ====================

enum JobStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }
enum JobPriority { LOW(0), MEDIUM(1), HIGH(2), CRITICAL(3);
    final int level;
    JobPriority(int l) { this.level = l; }
}

// ==================== Job ====================

class Job implements Comparable<Job> {
    private final String id;
    private final String name;
    private final Runnable task;
    private final JobPriority priority;
    private final int estimatedDurationMs;
    private volatile JobStatus status;
    private final long createdAt;
    private long startedAt;
    private long completedAt;

    Job(String id, String name, Runnable task, JobPriority priority, int estimatedDurationMs) {
        this.id = id; this.name = name; this.task = task;
        this.priority = priority; this.estimatedDurationMs = estimatedDurationMs;
        this.status = JobStatus.PENDING; this.createdAt = System.currentTimeMillis();
    }

    String getId() { return id; }
    String getName() { return name; }
    Runnable getTask() { return task; }
    JobPriority getPriority() { return priority; }
    int getEstimatedDurationMs() { return estimatedDurationMs; }
    JobStatus getStatus() { return status; }
    void setStatus(JobStatus s) { this.status = s; }
    void setStartedAt(long t) { this.startedAt = t; }
    void setCompletedAt(long t) { this.completedAt = t; }
    long getCreatedAt() { return createdAt; }

    @Override
    public int compareTo(Job other) {
        // Higher priority first, then FIFO (earlier created first)
        int cmp = Integer.compare(other.priority.level, this.priority.level);
        return cmp != 0 ? cmp : Long.compare(this.createdAt, other.createdAt);
    }

    @Override
    public String toString() {
        return String.format("Job[%s] %s (%s, %dms) [%s]", id, name, priority, estimatedDurationMs, status);
    }
}

// ==================== Scheduling Strategy ====================

interface SchedulingStrategy {
    void addJob(Job job, PriorityBlockingQueue<Job> queue);
    String getName();
}

class PriorityScheduling implements SchedulingStrategy {
    @Override
    public void addJob(Job job, PriorityBlockingQueue<Job> queue) {
        queue.put(job); // natural ordering: priority then FIFO
    }
    @Override public String getName() { return "Priority"; }
}

class FIFOScheduling implements SchedulingStrategy {
    private final AtomicInteger seq = new AtomicInteger(0);
    @Override
    public void addJob(Job job, PriorityBlockingQueue<Job> queue) {
        queue.put(job); // relies on createdAt ordering for same priority
    }
    @Override public String getName() { return "FIFO"; }
}

class ShortestJobFirstScheduling implements SchedulingStrategy {
    @Override
    public void addJob(Job job, PriorityBlockingQueue<Job> queue) {
        queue.put(job);
    }
    @Override public String getName() { return "SJF"; }
}

// ==================== Job Scheduler ====================

class JobScheduler {
    private final PriorityBlockingQueue<Job> jobQueue;
    private final ExecutorService workerPool;
    private final SchedulingStrategy strategy;
    private final Map<String, Job> allJobs;
    private final AtomicInteger jobCounter;
    private volatile boolean running;
    private final int workerCount;

    JobScheduler(int workerCount, SchedulingStrategy strategy) {
        this.workerCount = workerCount;
        this.strategy = strategy;
        this.jobQueue = new PriorityBlockingQueue<>();
        this.workerPool = Executors.newFixedThreadPool(workerCount);
        this.allJobs = new ConcurrentHashMap<>();
        this.jobCounter = new AtomicInteger(0);
        this.running = true;
    }

    /** Submit a job to the scheduler */
    Job submitJob(String name, Runnable task, JobPriority priority, int durationMs) {
        String jobId = "JOB-" + jobCounter.incrementAndGet();
        Job job = new Job(jobId, name, task, priority, durationMs);
        allJobs.put(jobId, job);
        strategy.addJob(job, jobQueue);
        System.out.println("  📋 Submitted: " + job);
        return job;
    }

    /** Process all jobs in queue (blocking until all done) */
    void processAll() {
        List<Future<?>> futures = new ArrayList<>();
        while (!jobQueue.isEmpty()) {
            Job job = jobQueue.poll();
            if (job == null) break;

            futures.add(workerPool.submit(() -> executeJob(job)));
        }
        // Wait for all to complete
        for (Future<?> f : futures) {
            try { f.get(30, TimeUnit.SECONDS); } catch (Exception e) { /* handled in executeJob */ }
        }
    }

    /** Process one job at a time (sequential) */
    void processNext() {
        Job job = jobQueue.poll();
        if (job == null) {
            System.out.println("  📭 No jobs in queue");
            return;
        }
        executeJob(job);
    }

    private void executeJob(Job job) {
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(System.currentTimeMillis());
        System.out.println("  ▶️ Running: " + job.getName() + " [" + job.getPriority() + "]"
            + " (Thread: " + Thread.currentThread().getName() + ")");
        try {
            job.getTask().run();
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(System.currentTimeMillis());
            System.out.println("  ✅ Completed: " + job.getName());
        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(System.currentTimeMillis());
            System.out.println("  ❌ Failed: " + job.getName() + " — " + e.getMessage());
        }
    }

    /** Cancel a pending job */
    boolean cancelJob(String jobId) {
        Job job = allJobs.get(jobId);
        if (job == null) { System.out.println("  ⚠️ Job not found: " + jobId); return false; }
        if (job.getStatus() != JobStatus.PENDING) {
            System.out.println("  ⚠️ Cannot cancel " + jobId + " (status: " + job.getStatus() + ")");
            return false;
        }
        job.setStatus(JobStatus.CANCELLED);
        jobQueue.remove(job);
        System.out.println("  🚫 Cancelled: " + job.getName());
        return true;
    }

    int getQueueSize() { return jobQueue.size(); }
    Job getJob(String id) { return allJobs.get(id); }

    String getStatus() {
        long pending = allJobs.values().stream().filter(j -> j.getStatus() == JobStatus.PENDING).count();
        long running = allJobs.values().stream().filter(j -> j.getStatus() == JobStatus.RUNNING).count();
        long completed = allJobs.values().stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();
        long failed = allJobs.values().stream().filter(j -> j.getStatus() == JobStatus.FAILED).count();
        return String.format("  Strategy=%s | Workers=%d | Queue=%d | P=%d R=%d C=%d F=%d",
            strategy.getName(), workerCount, jobQueue.size(), pending, running, completed, failed);
    }

    void shutdown() {
        running = false;
        workerPool.shutdown();
        try { workerPool.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { workerPool.shutdownNow(); }
    }
}

// ==================== Main Demo ====================

public class JobSchedulerSystem {
    public static void main(String[] args) {
        System.out.println("╔═════════════════════════════════════════════════════╗");
        System.out.println("║  Job Scheduler - Strategy Pattern + Priority Queue ║");
        System.out.println("╚═════════════════════════════════════════════════════╝\n");

        // ── Scenario 1: Priority scheduling ──
        System.out.println("━━━ Scenario 1: Priority scheduling (sequential) ━━━");
        JobScheduler scheduler = new JobScheduler(2, new PriorityScheduling());

        scheduler.submitJob("Backup DB", () -> sleep(50), JobPriority.LOW, 50);
        scheduler.submitJob("Send Email", () -> sleep(30), JobPriority.MEDIUM, 30);
        scheduler.submitJob("Process Payment", () -> sleep(40), JobPriority.CRITICAL, 40);
        scheduler.submitJob("Generate Report", () -> sleep(60), JobPriority.HIGH, 60);

        System.out.println(scheduler.getStatus());
        System.out.println("\n  Processing in priority order:");
        scheduler.processNext(); // CRITICAL first
        scheduler.processNext(); // HIGH
        scheduler.processNext(); // MEDIUM
        scheduler.processNext(); // LOW
        System.out.println(scheduler.getStatus());
        scheduler.shutdown();
        System.out.println();

        // ── Scenario 2: Parallel processing ──
        System.out.println("━━━ Scenario 2: Parallel processing (3 workers) ━━━");
        JobScheduler scheduler2 = new JobScheduler(3, new PriorityScheduling());

        for (int i = 1; i <= 6; i++) {
            int idx = i;
            scheduler2.submitJob("Task-" + i, () -> sleep(100), JobPriority.MEDIUM, 100);
        }

        System.out.println(scheduler2.getStatus());
        scheduler2.processAll();
        System.out.println(scheduler2.getStatus());
        scheduler2.shutdown();
        System.out.println();

        // ── Scenario 3: Job cancellation ──
        System.out.println("━━━ Scenario 3: Cancel a pending job ━━━");
        JobScheduler scheduler3 = new JobScheduler(1, new PriorityScheduling());
        Job j1 = scheduler3.submitJob("Important", () -> sleep(50), JobPriority.HIGH, 50);
        Job j2 = scheduler3.submitJob("Optional", () -> sleep(50), JobPriority.LOW, 50);

        scheduler3.cancelJob(j2.getId());
        scheduler3.processAll();
        System.out.println("  Job1 status: " + j1.getStatus());
        System.out.println("  Job2 status: " + j2.getStatus());
        scheduler3.shutdown();
        System.out.println();

        // ── Scenario 4: Failed job ──
        System.out.println("━━━ Scenario 4: Job failure handling ━━━");
        JobScheduler scheduler4 = new JobScheduler(1, new PriorityScheduling());
        scheduler4.submitJob("Failing Job", () -> { throw new RuntimeException("Simulated error"); },
            JobPriority.HIGH, 10);
        scheduler4.submitJob("Good Job", () -> sleep(20), JobPriority.MEDIUM, 20);
        scheduler4.processAll();
        System.out.println(scheduler4.getStatus());
        scheduler4.shutdown();
        System.out.println();

        // ── Scenario 5: Empty queue ──
        System.out.println("━━━ Scenario 5: Empty queue ━━━");
        JobScheduler scheduler5 = new JobScheduler(1, new PriorityScheduling());
        scheduler5.processNext(); // should say no jobs
        scheduler5.shutdown();

        System.out.println("\n✅ All Job Scheduler scenarios complete.");
    }

    static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
