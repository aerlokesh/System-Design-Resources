import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * TASK SCHEDULER - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Delayed task execution
 * - Priority-based scheduling
 * - Recurring/periodic tasks
 * - Task dependencies
 * - Thread pool based execution
 * - Task status tracking and cancellation
 * 
 * Interview talking points:
 * - Used by: Kubernetes CronJobs, Celery, AWS Step Functions
 * - Storage: Priority queue (min-heap) sorted by execution time
 * - Distribution: Consistent hashing to assign tasks to workers
 * - Reliability: Persist tasks to DB, use leader election
 * - Idempotency: Tasks might run more than once
 * - At-least-once: Re-enqueue if worker dies before ACK
 */
class TaskScheduler {

    enum TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

    // ==================== TASK ====================
    static class Task implements Comparable<Task> {
        final String id;
        final String name;
        final Runnable action;
        final long scheduledTime;
        final int priority; // Lower = higher priority
        volatile TaskStatus status;
        long startedAt;
        long completedAt;
        int retryCount;
        final int maxRetries;
        String result;

        Task(String name, Runnable action, long delayMs, int priority, int maxRetries) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.name = name;
            this.action = action;
            this.scheduledTime = System.currentTimeMillis() + delayMs;
            this.priority = priority;
            this.maxRetries = maxRetries;
            this.status = TaskStatus.PENDING;
        }

        Task(String name, Runnable action, long delayMs) {
            this(name, action, delayMs, 5, 3);
        }

        public int compareTo(Task other) {
            int timeCompare = Long.compare(this.scheduledTime, other.scheduledTime);
            if (timeCompare != 0) return timeCompare;
            return Integer.compare(this.priority, other.priority);
        }

        public String toString() {
            return String.format("Task[%s|%s|%s|p%d]", id, name, status, priority);
        }
    }

    // ==================== RECURRING TASK ====================
    static class RecurringTask {
        final String name;
        final Runnable action;
        final long intervalMs;
        final int priority;
        volatile boolean active = true;

        RecurringTask(String name, Runnable action, long intervalMs, int priority) {
            this.name = name;
            this.action = action;
            this.intervalMs = intervalMs;
            this.priority = priority;
        }
    }

    // ==================== SCHEDULER ====================
    static class SimpleScheduler {
        private final PriorityBlockingQueue<Task> taskQueue = new PriorityBlockingQueue<>();
        private final ExecutorService workerPool;
        private final Map<String, Task> taskMap = new ConcurrentHashMap<>();
        private final List<RecurringTask> recurringTasks = new CopyOnWriteArrayList<>();
        private volatile boolean running = true;
        private final AtomicInteger completedCount = new AtomicInteger();
        private final AtomicInteger failedCount = new AtomicInteger();

        SimpleScheduler(int workerCount) {
            this.workerPool = Executors.newFixedThreadPool(workerCount);
            startSchedulerThread();
            startRecurringThread();
        }

        String schedule(Task task) {
            taskMap.put(task.id, task);
            taskQueue.offer(task);
            return task.id;
        }

        String scheduleDelayed(String name, Runnable action, long delayMs, int priority) {
            Task task = new Task(name, action, delayMs, priority, 3);
            return schedule(task);
        }

        void scheduleRecurring(RecurringTask recurringTask) {
            recurringTasks.add(recurringTask);
        }

        boolean cancel(String taskId) {
            Task task = taskMap.get(taskId);
            if (task != null && task.status == TaskStatus.PENDING) {
                task.status = TaskStatus.CANCELLED;
                return true;
            }
            return false;
        }

        TaskStatus getStatus(String taskId) {
            Task task = taskMap.get(taskId);
            return task != null ? task.status : null;
        }

        private void startSchedulerThread() {
            Thread scheduler = new Thread(() -> {
                while (running) {
                    try {
                        Task task = taskQueue.peek();
                        if (task == null) {
                            Thread.sleep(50);
                            continue;
                        }
                        if (task.status == TaskStatus.CANCELLED) {
                            taskQueue.poll();
                            continue;
                        }
                        long now = System.currentTimeMillis();
                        if (task.scheduledTime <= now) {
                            taskQueue.poll();
                            executeTask(task);
                        } else {
                            Thread.sleep(Math.min(50, task.scheduledTime - now));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            scheduler.setDaemon(true);
            scheduler.start();
        }

        private void startRecurringThread() {
            Thread recurring = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(100);
                        for (RecurringTask rt : recurringTasks) {
                            if (rt.active) {
                                Task task = new Task(rt.name, rt.action, 0, rt.priority, 1);
                                schedule(task);
                            }
                        }
                        // Sleep for minimum interval
                        long minInterval = recurringTasks.stream()
                                .filter(rt -> rt.active)
                                .mapToLong(rt -> rt.intervalMs)
                                .min().orElse(1000);
                        Thread.sleep(minInterval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            recurring.setDaemon(true);
            recurring.start();
        }

        private void executeTask(Task task) {
            task.status = TaskStatus.RUNNING;
            task.startedAt = System.currentTimeMillis();
            workerPool.submit(() -> {
                try {
                    task.action.run();
                    task.status = TaskStatus.COMPLETED;
                    task.completedAt = System.currentTimeMillis();
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    task.retryCount++;
                    if (task.retryCount < task.maxRetries) {
                        task.status = TaskStatus.PENDING;
                        taskQueue.offer(task);
                    } else {
                        task.status = TaskStatus.FAILED;
                        task.result = e.getMessage();
                        failedCount.incrementAndGet();
                    }
                }
            });
        }

        int getPendingCount() { return taskQueue.size(); }
        int getCompletedCount() { return completedCount.get(); }
        int getFailedCount() { return failedCount.get(); }

        void shutdown() {
            running = false;
            workerPool.shutdown();
        }
    }

    // ==================== DAG SCHEDULER (Task Dependencies) ====================
    static class DAGScheduler {
        private final Map<String, Runnable> tasks = new LinkedHashMap<>();
        private final Map<String, Set<String>> dependencies = new HashMap<>();
        private final Map<String, String> taskStatus = new ConcurrentHashMap<>();
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        void addTask(String name, Runnable action) {
            tasks.put(name, action);
            dependencies.putIfAbsent(name, new HashSet<>());
            taskStatus.put(name, "PENDING");
        }

        void addDependency(String task, String dependsOn) {
            dependencies.computeIfAbsent(task, k -> new HashSet<>()).add(dependsOn);
        }

        List<String> getExecutionOrder() {
            // Topological sort
            Map<String, Integer> inDegree = new HashMap<>();
            for (String t : tasks.keySet()) inDegree.put(t, 0);
            for (Set<String> deps : dependencies.values()) {
                for (String d : deps) inDegree.merge(d, 0, (a, b) -> a); // ensure exists
            }
            for (Map.Entry<String, Set<String>> e : dependencies.entrySet()) {
                for (String dep : e.getValue()) {
                    // dep must complete before e.getKey()
                }
                inDegree.put(e.getKey(), e.getValue().size());
            }

            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
                if (e.getValue() == 0) queue.add(e.getKey());
            }

            List<String> order = new ArrayList<>();
            while (!queue.isEmpty()) {
                String current = queue.poll();
                order.add(current);
                for (Map.Entry<String, Set<String>> e : dependencies.entrySet()) {
                    if (e.getValue().contains(current)) {
                        int newDegree = inDegree.merge(e.getKey(), -1, Integer::sum);
                        if (newDegree == 0) queue.add(e.getKey());
                    }
                }
            }
            return order;
        }

        void execute() {
            List<String> order = getExecutionOrder();
            for (String taskName : order) {
                Runnable action = tasks.get(taskName);
                if (action != null) {
                    taskStatus.put(taskName, "RUNNING");
                    try {
                        action.run();
                        taskStatus.put(taskName, "COMPLETED");
                    } catch (Exception e) {
                        taskStatus.put(taskName, "FAILED");
                    }
                }
            }
        }

        String getStatus(String task) { return taskStatus.getOrDefault(task, "UNKNOWN"); }
        void shutdown() { executor.shutdown(); }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== TASK SCHEDULER - System Design Demo ===\n");

        // 1. Delayed tasks
        System.out.println("--- 1. Delayed Task Execution ---");
        SimpleScheduler scheduler = new SimpleScheduler(4);
        List<String> executionLog = new CopyOnWriteArrayList<>();

        scheduler.scheduleDelayed("immediate-task", () -> executionLog.add("immediate"), 0, 5);
        scheduler.scheduleDelayed("100ms-task", () -> executionLog.add("100ms"), 100, 5);
        scheduler.scheduleDelayed("200ms-task", () -> executionLog.add("200ms"), 200, 5);

        Thread.sleep(500);
        System.out.printf("  Execution order: %s%n", executionLog);
        System.out.printf("  Completed: %d%n", scheduler.getCompletedCount());

        // 2. Priority tasks
        System.out.println("\n--- 2. Priority-Based Scheduling ---");
        List<String> priorityLog = new CopyOnWriteArrayList<>();
        // All scheduled at same time, but different priorities
        scheduler.scheduleDelayed("low-priority", () -> priorityLog.add("LOW(p=10)"), 0, 10);
        scheduler.scheduleDelayed("high-priority", () -> priorityLog.add("HIGH(p=1)"), 0, 1);
        scheduler.scheduleDelayed("medium-priority", () -> priorityLog.add("MED(p=5)"), 0, 5);

        Thread.sleep(300);
        System.out.printf("  Priority execution: %s%n", priorityLog);

        // 3. Task cancellation
        System.out.println("\n--- 3. Task Cancellation ---");
        String taskId = scheduler.scheduleDelayed("cancelable", () -> {}, 5000, 5);
        System.out.printf("  Scheduled: %s, status: %s%n", taskId, scheduler.getStatus(taskId));
        boolean cancelled = scheduler.cancel(taskId);
        System.out.printf("  Cancelled: %s, status: %s%n", cancelled, scheduler.getStatus(taskId));

        // 4. Retry on failure
        System.out.println("\n--- 4. Retry on Failure ---");
        AtomicInteger attempts = new AtomicInteger(0);
        Task failTask = new Task("flaky-task", () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) throw new RuntimeException("Fail attempt " + attempt);
        }, 0, 5, 3);
        scheduler.schedule(failTask);
        Thread.sleep(500);
        System.out.printf("  Attempts: %d, Final status: %s%n", attempts.get(), failTask.status);

        // 5. DAG Scheduler (dependencies)
        System.out.println("\n--- 5. DAG Scheduler (Task Dependencies) ---");
        DAGScheduler dag = new DAGScheduler();
        List<String> dagLog = new ArrayList<>();

        dag.addTask("fetch-data", () -> dagLog.add("1.fetch-data"));
        dag.addTask("parse-data", () -> dagLog.add("2.parse-data"));
        dag.addTask("validate", () -> dagLog.add("3.validate"));
        dag.addTask("transform", () -> dagLog.add("4.transform"));
        dag.addTask("save-to-db", () -> dagLog.add("5.save-to-db"));

        dag.addDependency("parse-data", "fetch-data");       // parse after fetch
        dag.addDependency("validate", "parse-data");          // validate after parse
        dag.addDependency("transform", "validate");           // transform after validate
        dag.addDependency("save-to-db", "transform");         // save after transform

        System.out.printf("  Execution order: %s%n", dag.getExecutionOrder());
        dag.execute();
        System.out.printf("  Executed: %s%n", dagLog);
        for (String t : dag.getExecutionOrder()) {
            System.out.printf("    %s: %s%n", t, dag.getStatus(t));
        }
        dag.shutdown();

        // 6. Stats
        System.out.println("\n--- 6. Scheduler Stats ---");
        System.out.printf("  Pending: %d, Completed: %d, Failed: %d%n",
                scheduler.getPendingCount(), scheduler.getCompletedCount(), scheduler.getFailedCount());

        scheduler.shutdown();

        System.out.println("\n--- Design Considerations ---");
        System.out.println("  Storage:      Min-heap (priority queue) sorted by execution time");
        System.out.println("  Distribution: Partition tasks across workers via consistent hashing");
        System.out.println("  Reliability:  Persist to DB, re-enqueue on worker failure");
        System.out.println("  Idempotency:  Tasks must be safe to run more than once");
        System.out.println("  Monitoring:   Track execution time, failure rates, queue depth");
    }
}
