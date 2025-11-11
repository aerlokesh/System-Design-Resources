import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * JOB SCHEDULER SYSTEM - LOW LEVEL DESIGN
 * Famous Microsoft Interview Question
 * 
 * Design a scheduler for a massively parallel distributed system that assigns
 * incoming jobs to machines based on capability matching.
 * 
 * Core Requirements:
 * 1. Each machine has a set of capabilities
 * 2. Each job requires specific capabilities
 * 3. Job runs ONLY on machines with ALL required capabilities
 * 4. Handle job queuing, priorities, and concurrent execution
 * 
 * This implementation demonstrates:
 * - Strategy Pattern for scheduling algorithms
 * - Thread-safe concurrent operations
 * - Capability-based matching
 * - Priority queue management
 * - Resource allocation optimization
 */

// ==================== Enums ====================

enum JobStatus {
    PENDING,      // Waiting in queue
    SCHEDULED,    // Assigned to machine
    RUNNING,      // Currently executing
    COMPLETED,    // Successfully finished
    FAILED,       // Execution failed
    CANCELLED     // Manually cancelled
}

enum JobPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);  // Pages oncall, highest priority
    
    private final int value;
    
    JobPriority(int value) { this.value = value; }
    public int getValue() { return value; }
}

enum MachineStatus {
    AVAILABLE,    // Ready for new jobs
    BUSY,         // At max capacity
    MAINTENANCE,  // Under maintenance
    OFFLINE       // Not operational
}

// ==================== Core Entities ====================

/**
 * Capability: Represents a skill/feature that machines have and jobs require
 * 
 * Why separate Capability class?
 * - Type safety (can't pass random strings)
 * - Version control (Java 11 vs Java 17 matters!)
 * - Easy validation
 * - Can extend with metadata (release date, deprecation status)
 * 
 * Examples:
 * - Language: ("Java", "11"), ("Python", "3.9")
 * - Hardware: ("GPU", "CUDA-11"), ("CPU", "x86_64")
 * - Software: ("Docker", "20.10"), ("PostgreSQL", "14")
 */
class Capability {
    private final String name;
    private final String version;
    
    public Capability(String name, String version) {
        this.name = name;
        this.version = version;
    }
    
    public String getName() { return name; }
    public String getVersion() { return version; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Capability that = (Capability) o;
        return Objects.equals(name, that.name) && 
               Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }
    
    @Override
    public String toString() {
        return name + ":" + version;
    }
}

/**
 * Job: Represents a unit of work to be executed
 * 
 * Why track timestamps?
 * - SLA monitoring (how long in queue?)
 * - Performance metrics (execution time)
 * - Billing (duration-based pricing)
 * - Debugging (when did it fail?)
 * 
 * Why immutable capabilities?
 * - Thread safety (no modifications during execution)
 * - Prevents accidental changes
 * - Clear contract (requirements set at creation)
 */
class Job {
    private final String jobId;
    private final String name;
    private final Set<Capability> requiredCapabilities;  // MUST have ALL
    private final JobPriority priority;
    private final long estimatedDurationMs;
    private JobStatus status;
    private String assignedMachineId;
    private final long submissionTime;
    private long startTime;
    private long endTime;
    
    public Job(String jobId, String name, Set<Capability> requiredCapabilities, 
               JobPriority priority, long estimatedDurationMs) {
        this.jobId = jobId;
        this.name = name;
        this.requiredCapabilities = new HashSet<>(requiredCapabilities);  // Defensive copy
        this.priority = priority;
        this.estimatedDurationMs = estimatedDurationMs;
        this.status = JobStatus.PENDING;
        this.submissionTime = System.currentTimeMillis();
    }
    
    public String getJobId() { return jobId; }
    public String getName() { return name; }
    public Set<Capability> getRequiredCapabilities() { 
        return new HashSet<>(requiredCapabilities);  // Return copy for safety
    }
    public JobPriority getPriority() { return priority; }
    public long getEstimatedDurationMs() { return estimatedDurationMs; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public String getAssignedMachineId() { return assignedMachineId; }
    public void setAssignedMachineId(String assignedMachineId) { 
        this.assignedMachineId = assignedMachineId; 
    }
    public long getSubmissionTime() { return submissionTime; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    
    /**
     * Calculate wait time in queue (for SLA monitoring)
     */
    public long getWaitTimeMs() {
        long referenceTime = (startTime > 0) ? startTime : System.currentTimeMillis();
        return referenceTime - submissionTime;
    }
    
    @Override
    public String toString() {
        return String.format("Job[id=%s, name=%s, priority=%s, status=%s, capabilities=%s]",
            jobId, name, priority, status, requiredCapabilities);
    }
}

/**
 * Machine: Represents a compute resource that can execute jobs
 * 
 * Why track running jobs instead of single job?
 * - Modern machines can handle multiple jobs (multi-core)
 * - Better resource utilization
 * - Isolation per job (containers/VMs)
 * 
 * Why capability set vs. boolean flags?
 * - Extensible (add new capabilities without code changes)
 * - Version-aware (Java 11 != Java 17)
 * - Flexible matching (can require multiple capabilities)
 * 
 * Concurrency Design:
 * - Sync on runningJobs for thread safety
 * - Status derived from running jobs count
 * - No race conditions between check and add
 */
class Machine {
    private final String machineId;
    private final String name;
    private final Set<Capability> capabilities;
    private MachineStatus status;
    private final int maxConcurrentJobs;
    private final List<Job> runningJobs;
    
    public Machine(String machineId, String name, Set<Capability> capabilities, 
                   int maxConcurrentJobs) {
        this.machineId = machineId;
        this.name = name;
        this.capabilities = new HashSet<>(capabilities);
        this.status = MachineStatus.AVAILABLE;
        this.maxConcurrentJobs = maxConcurrentJobs;
        this.runningJobs = new ArrayList<>();
    }
    
    public String getMachineId() { return machineId; }
    public String getName() { return name; }
    public Set<Capability> getCapabilities() { 
        return new HashSet<>(capabilities); 
    }
    public MachineStatus getStatus() { return status; }
    public void setStatus(MachineStatus status) { this.status = status; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public List<Job> getRunningJobs() { 
        return new ArrayList<>(runningJobs); 
    }
    
    /**
     * Check if machine can accept new job
     * 
     * Conditions:
     * 1. Status is AVAILABLE (not in maintenance)
     * 2. Under max concurrent job limit
     */
    public boolean canAcceptJob() {
        return status == MachineStatus.AVAILABLE && 
               runningJobs.size() < maxConcurrentJobs;
    }
    
    /**
     * Add job to running jobs list
     * Updates status if at capacity
     */
    public void addRunningJob(Job job) {
        runningJobs.add(job);
        if (runningJobs.size() >= maxConcurrentJobs) {
            status = MachineStatus.BUSY;
        }
    }
    
    /**
     * Remove completed job from running list
     * Updates status if now available
     */
    public void removeRunningJob(Job job) {
        runningJobs.remove(job);
        if (runningJobs.size() < maxConcurrentJobs && 
            status == MachineStatus.BUSY) {
            status = MachineStatus.AVAILABLE;
        }
    }
    
    /**
     * Core capability matching logic
     * 
     * Why containsAll?
     * - Job needs ALL capabilities
     * - Machine with extra capabilities is OK (superset)
     * - Machine missing even one capability → REJECT
     * 
     * Example:
     * Machine: [Java-11, Docker-20, GPU-CUDA]
     * Job needs: [Java-11, Docker-20] → MATCH ✓
     * Job needs: [Java-11, Python-3.9] → NO MATCH ✗ (missing Python)
     */
    public boolean hasCapabilities(Set<Capability> requiredCapabilities) {
        return capabilities.containsAll(requiredCapabilities);
    }
    
    @Override
    public String toString() {
        return String.format("Machine[id=%s, name=%s, status=%s, capabilities=%s, running=%d/%d]",
            machineId, name, status, capabilities, runningJobs.size(), maxConcurrentJobs);
    }
}

// ==================== Scheduling Strategies ====================

/**
 * Strategy Pattern: Different algorithms for selecting machines
 * 
 * Why Strategy Pattern?
 * - Easy to swap algorithms (just change strategy object)
 * - Each strategy testable in isolation
 * - Open/Closed Principle (add new strategies without changing existing)
 * - Runtime flexibility (can switch based on load/time/metrics)
 * 
 * Trade-offs of Each Strategy:
 * 
 * First-Fit:
 * ✓ Pros: Fast (O(n)), simple, predictable
 * ✗ Cons: Uneven load, hot spots, wastes capable machines
 * Use Case: Low traffic, simple requirements
 * 
 * Best-Fit:
 * ✓ Pros: Optimal resource usage, preserves specialized machines
 * ✗ Cons: Slower (O(n*m)), complex logic
 * Use Case: Limited specialized resources, cost optimization
 * 
 * Load-Balanced:
 * ✓ Pros: Even distribution, better throughput, no hot spots
 * ✗ Cons: May not be optimal for specialized jobs
 * Use Case: High traffic, homogeneous workload
 * 
 * Interview Tip: Always discuss trade-offs when presenting strategies!
 */
interface SchedulingStrategy {
    Machine selectMachine(Job job, List<Machine> availableMachines);
}

/**
 * First-Fit Strategy: Select first capable machine
 * 
 * Algorithm:
 * 1. Iterate machines in order
 * 2. Return first match
 * 3. Done!
 * 
 * Time Complexity: O(n) where n = number of machines
 * Space Complexity: O(1)
 * 
 * Problem: Hot Spot Formation
 * If machines ordered [M1, M2, M3], M1 gets most jobs
 * M1 becomes bottleneck while M2, M3 underutilized
 * 
 * Real Example:
 * 100 machines, alphabetically named
 * Machine "A-001" gets 10x more jobs than "Z-100"
 * Uneven wear, uneven performance
 * 
 * When to Use:
 * - Low traffic systems
 * - Machines are homogeneous
 * - Simplicity > optimization
 */
class FirstFitStrategy implements SchedulingStrategy {
    @Override
    public Machine selectMachine(Job job, List<Machine> availableMachines) {
        for (Machine machine : availableMachines) {
            if (machine.hasCapabilities(job.getRequiredCapabilities())) {
                return machine;
            }
        }
        return null;  // No suitable machine found
    }
}

/**
 * Best-Fit Strategy: Select machine with least extra capabilities
 * 
 * Algorithm:
 * 1. Find all capable machines
 * 2. Calculate extra capabilities for each
 * 3. Select machine with minimum extras
 * 
 * Time Complexity: O(n*m) where n = machines, m = avg capabilities
 * Space Complexity: O(1)
 * 
 * Why "Best"?
 * - Preserves specialized machines for specialized jobs
 * - Optimal resource allocation
 * - Cost-efficient (don't waste GPU machines on CPU jobs)
 * 
 * Example:
 * Job needs: [Java-11, Docker-20]
 * 
 * Machine A: [Java-11, Docker-20] → 0 extras → BEST!
 * Machine B: [Java-11, Docker-20, GPU] → 1 extra → OK
 * Machine C: [Java-11, Docker-20, GPU, Python] → 2 extras → Last choice
 * 
 * Result: Saves Machine C's GPU for GPU jobs
 * 
 * Trade-off:
 * ✓ Optimal allocation
 * ✗ Slower (must check all machines)
 * ✗ May leave capable machines idle
 * 
 * When to Use:
 * - Heterogeneous machine pool
 * - Limited specialized resources
 * - Cost is major concern
 */
class BestFitStrategy implements SchedulingStrategy {
    @Override
    public Machine selectMachine(Job job, List<Machine> availableMachines) {
        Machine bestMachine = null;
        int minExtraCapabilities = Integer.MAX_VALUE;
        
        for (Machine machine : availableMachines) {
            if (machine.hasCapabilities(job.getRequiredCapabilities())) {
                int extraCapabilities = machine.getCapabilities().size() - 
                                       job.getRequiredCapabilities().size();
                if (extraCapabilities < minExtraCapabilities) {
                    minExtraCapabilities = extraCapabilities;
                    bestMachine = machine;
                }
            }
        }
        return bestMachine;
    }
}

/**
 * Load-Balanced Strategy: Select least loaded machine
 * 
 * Algorithm:
 * 1. Find all capable machines
 * 2. Check current job count on each
 * 3. Select machine with fewest running jobs
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(1)
 * 
 * Why Load Balancing Matters:
 * - Prevents hot spots
 * - Better throughput (parallel execution)
 * - Even wear across machines
 * - Predictable performance
 * 
 * Problem Avoided: Hot Partition
 * Without load balancing:
 * - Machine 1: 100 jobs (overloaded!)
 * - Machine 2: 10 jobs (idle)
 * - Machine 3: 10 jobs (idle)
 * Result: Machine 1 bottleneck, poor overall throughput
 * 
 * With load balancing:
 * - Machine 1: 40 jobs
 * - Machine 2: 40 jobs
 * - Machine 3: 40 jobs
 * Result: Even load, optimal throughput
 * 
 * Real Example (Interview Story):
 * "In production, we had celebrities on Shard 1, regular users on Shard 2.
 *  Without load balancing, Shard 1 became hot spot.
 *  Solution: Load-balanced strategy + caching for hot data.
 *  Result: 10x throughput improvement"
 * 
 * When to Use:
 * - High traffic systems
 * - Homogeneous machines
 * - Throughput > optimization
 * - Prevent hot spots
 */
class LoadBalancedStrategy implements SchedulingStrategy {
    @Override
    public Machine selectMachine(Job job, List<Machine> availableMachines) {
        Machine bestMachine = null;
        int minLoad = Integer.MAX_VALUE;
        
        for (Machine machine : availableMachines) {
            if (machine.hasCapabilities(job.getRequiredCapabilities())) {
                int currentLoad = machine.getRunningJobs().size();
                if (currentLoad < minLoad) {
                    minLoad = currentLoad;
                    bestMachine = machine;
                }
            }
        }
        return bestMachine;
    }
}

// ==================== Job Scheduler ====================

/**
 * JobScheduler: Central orchestrator for job-to-machine assignment
 * 
 * Thread Safety Design:
 * - ConcurrentHashMap for machine/job storage (thread-safe reads)
 * - Synchronized methods for scheduling operations
 * - ExecutorService for concurrent job execution
 * - ScheduledExecutorService for periodic scheduling attempts
 * 
 * Why Separate Executor Services?
 * - Job execution: CachedThreadPool (grows as needed)
 * - Scheduling attempts: SingleThread (prevents race conditions)
 * 
 * Memory Management:
 * - Completed jobs kept in map (for history/debugging)
 * - In production: would archive to database after N hours
 * - Could add memory limits + LRU eviction
 * 
 * Failure Handling:
 * - Job fails → Machine freed immediately
 * - No cascading failures
 * - Automatic retry possible (add retry queue)
 */
class JobScheduler {
    private final Map<String, Machine> machines;
    private final Map<String, Job> jobs;
    private final PriorityQueue<Job> jobQueue;
    private SchedulingStrategy schedulingStrategy;
    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;
    private volatile boolean running;
    
    public JobScheduler(SchedulingStrategy schedulingStrategy) {
        this.machines = new ConcurrentHashMap<>();
        this.jobs = new ConcurrentHashMap<>();
        
        // Priority Queue: Critical jobs first, then by submission time
        // Why PriorityQueue?
        // - Automatic sorting by priority
        // - O(log n) insertion
        // - Always serves highest priority first
        // - Prevents starvation of critical jobs
        this.jobQueue = new PriorityQueue<>(
            Comparator.comparing(Job::getPriority, 
                Comparator.comparingInt(JobPriority::getValue).reversed())  // Higher value = higher priority
                .thenComparing(Job::getSubmissionTime)  // Older jobs first (FIFO within priority)
        );
        
        this.schedulingStrategy = schedulingStrategy;
        this.executorService = Executors.newCachedThreadPool();
        this.schedulerService = Executors.newScheduledThreadPool(1);
        this.running = false;
    }
    
    /**
     * Register machine with scheduler
     * 
     * Thread Safety: ConcurrentHashMap handles concurrent registrations
     */
    public synchronized void registerMachine(Machine machine) {
        machines.put(machine.getMachineId(), machine);
        System.out.println("Machine registered: " + machine);
    }
    
    /**
     * Remove machine from pool
     * 
     * Production Considerations:
     * - Drain running jobs first (graceful shutdown)
     * - Reschedule queued jobs
     * - Alert if jobs lost
     */
    public synchronized void unregisterMachine(String machineId) {
        Machine machine = machines.remove(machineId);
        if (machine != null) {
            machine.setStatus(MachineStatus.OFFLINE);
            System.out.println("Machine unregistered: " + machineId);
        }
    }
    
    /**
     * Submit job to scheduler
     * 
     * Process:
     * 1. Add to jobs map (for lookup)
     * 2. Add to priority queue (for scheduling)
     * 3. Try immediate scheduling
     * 
     * Why try immediate scheduling?
     * - Low latency for high-priority jobs
     * - Better resource utilization
     * - Periodic scheduler as backup only
     */
    public synchronized String submitJob(Job job) {
        jobs.put(job.getJobId(), job);
        jobQueue.offer(job);
        System.out.println("Job submitted: " + job);
        
        // Try to schedule immediately (don't wait for periodic check)
        tryScheduleJobs();
        
        return job.getJobId();
    }
    
    /**
     * Cancel pending job
     * 
     * Can cancel: PENDING jobs only
     * Cannot cancel: RUNNING jobs (would need job interruption)
     * 
     * Production Enhancement:
     * - Support killing running jobs
     * - Cleanup resources
     * - Notify dependent jobs
     */
    public synchronized boolean cancelJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return false;
        
        if (job.getStatus() == JobStatus.PENDING) {
            jobQueue.remove(job);
            job.setStatus(JobStatus.CANCELLED);
            System.out.println("Job cancelled: " + jobId);
            return true;
        }
        return false;
    }
    
    /**
     * Core scheduling logic: Match jobs to machines
     * 
     * Algorithm:
     * 1. Get available machines
     * 2. For each pending job (priority order):
     *    a. Use strategy to select machine
     *    b. If found: assign and execute
     *    c. If not found: stays in queue
     * 
     * Why synchronized?
     * - Prevents double-assignment
     * - Atomic check-and-assign
     * - Race condition prevention
     * 
     * Performance Considerations:
     * - Could batch jobs for efficiency
     * - Could parallelize machine checks
     * - Trade-off: correctness > speed here
     * 
     * Problem Avoided: Resource Starvation
     * Priority queue ensures critical jobs scheduled first
     * Load balancing prevents machine starvation
     */
    private synchronized void tryScheduleJobs() {
        List<Job> jobsToSchedule = new ArrayList<>();
        
        // Get available machines
        List<Machine> availableMachines = machines.values().stream()
            .filter(Machine::canAcceptJob)
            .collect(Collectors.toList());
        
        if (availableMachines.isEmpty()) {
            return;  // No machines available, jobs stay queued
        }
        
        // Try to schedule jobs from queue (priority order)
        while (!jobQueue.isEmpty() && !availableMachines.isEmpty()) {
            Job job = jobQueue.peek();
            
            // Use selected strategy to find best machine
            Machine selectedMachine = schedulingStrategy.selectMachine(job, availableMachines);
            
            if (selectedMachine != null) {
                jobQueue.poll();  // Remove from queue
                jobsToSchedule.add(job);
                
                // Assign job to machine
                job.setStatus(JobStatus.SCHEDULED);
                job.setAssignedMachineId(selectedMachine.getMachineId());
                selectedMachine.addRunningJob(job);
                
                System.out.println(String.format("Job %s scheduled to machine %s", 
                    job.getJobId(), selectedMachine.getMachineId()));
                
                // Update available machines list
                if (!selectedMachine.canAcceptJob()) {
                    availableMachines.remove(selectedMachine);
                }
            } else {
                // No suitable machine for this job
                // Job stays in queue (will try again later)
                break;
            }
        }
        
        // Execute scheduled jobs
        for (Job job : jobsToSchedule) {
            executeJob(job);
        }
    }
    
    /**
     * Execute job on assigned machine
     * 
     * Execution Model:
     * - Async execution (non-blocking)
     * - Simulated work (Thread.sleep)
     * - Automatic cleanup on completion
     * - Machine freed for next job
     * 
     * Error Handling:
     * - Try-catch for graceful failure
     * - Machine always freed (finally block)
     * - Job status reflects outcome
     * 
     * Production Enhancements:
     * - Actual job execution (containers/VMs)
     * - Resource limits (CPU/memory)
     * - Monitoring/logging
     * - Retry logic
     */
    private void executeJob(Job job) {
        executorService.submit(() -> {
            try {
                job.setStatus(JobStatus.RUNNING);
                job.setStartTime(System.currentTimeMillis());
                
                System.out.println("Job started: " + job.getJobId());
                
                // Simulate job execution
                Thread.sleep(job.getEstimatedDurationMs());
                
                // Job completed successfully
                job.setStatus(JobStatus.COMPLETED);
                job.setEndTime(System.currentTimeMillis());
                
                System.out.println("Job completed: " + job.getJobId());
                
            } catch (InterruptedException e) {
                job.setStatus(JobStatus.FAILED);
                job.setEndTime(System.currentTimeMillis());
                System.out.println("Job failed: " + job.getJobId());
            } finally {
                // Always free machine
                Machine machine = machines.get(job.getAssignedMachineId());
                if (machine != null) {
                    synchronized (this) {
                        machine.removeRunningJob(job);
                        // Try to schedule more jobs (machine now available)
                        tryScheduleJobs();
                    }
                }
            }
        });
    }
    
    /**
     * Start scheduler
     * 
     * Periodic Scheduling:
     * - Every 1 second, attempt scheduling
     * - Handles cases where jobs can't be scheduled immediately
     * - Backup mechanism (immediate scheduling is primary)
     * 
     * Why periodic?
     * - Machine might become available between submissions
     * - Jobs might become schedulable (dependency resolution)
     * - Self-healing (no jobs stuck forever)
     */
    public void start() {
        if (!running) {
            running = true;
            schedulerService.scheduleAtFixedRate(() -> {
                synchronized (this) {
                    tryScheduleJobs();
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            System.out.println("Job Scheduler started");
        }
    }
    
    /**
     * Stop scheduler gracefully
     * 
     * Shutdown Process:
     * 1. Stop accepting new jobs
     * 2. Wait for running jobs (or timeout)
     * 3. Cleanup resources
     */
    public void stop() {
        if (running) {
            running = false;
            schedulerService.shutdown();
            executorService.shutdown();
            System.out.println("Job Scheduler stopped");
        }
    }
    
    // ===== Query Methods =====
    
    public JobStatus getJobStatus(String jobId) {
        Job job = jobs.get(jobId);
        return job != null ? job.getStatus() : null;
    }
    
    public List<Job> getAllJobs() {
        return new ArrayList<>(jobs.values());
    }
    
    public List<Machine> getAllMachines() {
        return new ArrayList<>(machines.values());
    }
    
    public int getPendingJobsCount() {
        return jobQueue.size();
    }
    
    /**
     * Change scheduling strategy at runtime
     * 
     * Use Cases:
     * - Switch to load-balanced during high traffic
     * - Switch to best-fit during low traffic (cost optimization)
     * - A/B testing different strategies
     * - Adaptive scheduling based on metrics
     */
    public synchronized void setSchedulingStrategy(SchedulingStrategy strategy) {
        this.schedulingStrategy = strategy;
        System.out.println("Scheduling strategy updated: " + strategy.getClass().getSimpleName());
    }
    
    /**
     * Display system status
     * 
     * Useful for:
     * - Monitoring
     * - Debugging
     * - Capacity planning
     * - SLA tracking
     */
    public void printStatus() {
        System.out.println("\n===== Job Scheduler Status =====");
        System.out.println("Total Machines: " + machines.size());
        System.out.println("Total Jobs: " + jobs.size());
        System.out.println("Pending Jobs: " + getPendingJobsCount());
        
        long runningJobs = jobs.values().stream()
            .filter(j -> j.getStatus() == JobStatus.RUNNING)
            .count();
        System.out.println("Running Jobs: " + runningJobs);
        
        long completedJobs = jobs.values().stream()
            .filter(j -> j.getStatus() == JobStatus.COMPLETED)
            .count();
        System.out.println("Completed Jobs: " + completedJobs);
        
        System.out.println("\nMachines:");
        machines.values().forEach(m -> System.out.println("  " + m));
        System.out.println("================================\n");
    }
}

// ==================== Demo ====================

public class JobSchedulerSystem {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Job Scheduler System Demo ===\n");
        
        // Create capabilities (real-world examples)
        Capability java = new Capability("Java", "11");
        Capability python = new Capability("Python", "3.9");
        Capability docker = new Capability("Docker", "20.10");
        Capability gpu = new Capability("GPU", "CUDA-11");
        Capability database = new Capability("Database", "PostgreSQL");
        
        // Create machines with different capabilities
        // Why different capabilities?
        // - Real clusters have heterogeneous machines
        // - Some have GPUs, some don't
        // - Different software versions
        Machine machine1 = new Machine("M1", "Server-1", 
            new HashSet<>(Arrays.asList(java, docker)), 2);
        Machine machine2 = new Machine("M2", "Server-2", 
            new HashSet<>(Arrays.asList(python, docker, gpu)), 2);
        Machine machine3 = new Machine("M3", "Server-3", 
            new HashSet<>(Arrays.asList(java, python, database)), 1);
        Machine machine4 = new Machine("M4", "Server-4", 
            new HashSet<>(Arrays.asList(docker, gpu)), 2);
        
        // Create scheduler with Best-Fit strategy
        JobScheduler scheduler = new JobScheduler(new BestFitStrategy());
        
        // Register machines
        scheduler.registerMachine(machine1);
        scheduler.registerMachine(machine2);
        scheduler.registerMachine(machine3);
        scheduler.registerMachine(machine4);
        
        // Start scheduler
        scheduler.start();
        
        System.out.println("\n--- Submitting Jobs ---\n");
        
        // Submit jobs with different priorities and requirements
        Job job1 = new Job("J1", "Java Build", 
            new HashSet<>(Arrays.asList(java)), JobPriority.HIGH, 2000);
        scheduler.submitJob(job1);
        
        Job job2 = new Job("J2", "ML Training", 
            new HashSet<>(Arrays.asList(python, gpu)), JobPriority.CRITICAL, 3000);
        scheduler.submitJob(job2);
        
        Job job3 = new Job("J3", "Docker Build", 
            new HashSet<>(Arrays.asList(docker)), JobPriority.MEDIUM, 1500);
        scheduler.submitJob(job3);
        
        Job job4 = new Job("J4", "DB Migration", 
            new HashSet<>(Arrays.asList(database, python)), JobPriority.HIGH, 2500);
        scheduler.submitJob(job4);
        
        Thread.sleep(1000);
        scheduler.printStatus();
        
        // Test strategy change
        System.out.println("--- Changing to Load Balanced Strategy ---\n");
        scheduler.setSchedulingStrategy(new LoadBalancedStrategy());
        
        Job job5 = new Job("J5", "Python Analysis", 
            new HashSet<>(Arrays.asList(python)), JobPriority.HIGH, 2000);
        scheduler.submitJob(job5);
        
        Thread.sleep(3000);
        scheduler.printStatus();
        
        scheduler.stop();
        System.out.println("\n=== Demo Completed ===");
    }
}
