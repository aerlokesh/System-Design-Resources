import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Job Scheduler System
 * Time to complete: 45-60 minutes
 * Focus: Priority queue, scheduled execution
 */

// ==================== Job ====================
class Job implements Comparable<Job> {
    private final String id;
    private final String name;
    private final Runnable task;
    private final LocalDateTime scheduledTime;
    private final int priority;
    private boolean executed;

    public Job(String id, String name, Runnable task, 
               LocalDateTime scheduledTime, int priority) {
        this.id = id;
        this.name = name;
        this.task = task;
        this.scheduledTime = scheduledTime;
        this.priority = priority;
        this.executed = false;
    }

    public void execute() {
        if (!executed) {
            System.out.println("▶ Executing: " + name);
            task.run();
            executed = true;
            System.out.println("✓ Completed: " + name);
        }
    }

    public boolean isReadyToExecute() {
        return !executed && LocalDateTime.now().isAfter(scheduledTime);
    }

    @Override
    public int compareTo(Job other) {
        // Sort by time first, then priority
        int timeCompare = this.scheduledTime.compareTo(other.scheduledTime);
        if (timeCompare != 0) return timeCompare;
        return Integer.compare(other.priority, this.priority);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isExecuted() { return executed; }

    @Override
    public String toString() {
        return name + " [" + scheduledTime.toLocalTime() + 
               ", Priority=" + priority + ", Executed=" + executed + "]";
    }
}

// ==================== Job Scheduler ====================
class JobScheduler {
    private final PriorityQueue<Job> jobQueue;
    private final ScheduledExecutorService executor;
    private int jobCounter;

    public JobScheduler() {
        this.jobQueue = new PriorityQueue<>();
        this.executor = Executors.newScheduledThreadPool(3);
        this.jobCounter = 1;
    }

    public void scheduleJob(String name, Runnable task, 
                           LocalDateTime scheduledTime, int priority) {
        String jobId = "J" + jobCounter++;
        Job job = new Job(jobId, name, task, scheduledTime, priority);
        
        jobQueue.offer(job);
        System.out.println("✓ Scheduled: " + job);
    }

    public void scheduleJob(String name, Runnable task, int delaySeconds, int priority) {
        LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(delaySeconds);
        scheduleJob(name, task, scheduledTime, priority);
    }

    public void processJobs() {
        System.out.println("\n--- Processing Jobs ---");
        
        while (!jobQueue.isEmpty()) {
            Job job = jobQueue.peek();
            
            if (job.isReadyToExecute()) {
                jobQueue.poll();
                executor.submit(job::execute);
            } else {
                // Wait a bit and check again
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void displayQueue() {
        System.out.println("\n=== Job Queue ===");
        System.out.println("Pending jobs: " + jobQueue.size());
        for (Job job : jobQueue) {
            System.out.println("  " + job);
        }
        System.out.println();
    }

    public void shutdown() {
        executor.shutdown();
    }
}

// ==================== Demo ====================
public class JobSchedulerSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Job Scheduler Demo ===\n");

        JobScheduler scheduler = new JobScheduler();

        LocalDateTime now = LocalDateTime.now();

        // Schedule jobs with different priorities and times
        scheduler.scheduleJob(
            "Send Email",
            () -> System.out.println("   [Task] Sending email..."),
            now.plusSeconds(2),
            3
        );

        scheduler.scheduleJob(
            "Generate Report",
            () -> System.out.println("   [Task] Generating report..."),
            now.plusSeconds(3),
            2
        );

        scheduler.scheduleJob(
            "Backup Database",
            () -> System.out.println("   [Task] Backing up database..."),
            now.plusSeconds(1),
            5  // High priority
        );

        scheduler.scheduleJob(
            "Clean Logs",
            () -> System.out.println("   [Task] Cleaning old logs..."),
            now.plusSeconds(2),
            1  // Low priority, same time as "Send Email"
        );

        scheduler.displayQueue();

        // Process all jobs
        scheduler.processJobs();

        Thread.sleep(1000);

        scheduler.displayQueue();
        scheduler.shutdown();

        System.out.println("✅ Demo complete!");
    }
}
