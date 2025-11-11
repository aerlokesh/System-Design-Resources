import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Job Scheduler System - Low Level Design
 * 
 * Design a scheduler for a massively parallel distributed system.
 * The scheduler assigns incoming jobs to machines that it controls.
 * 
 * Requirements:
 * 1. Each machine has a set of capabilities
 * 2. Each job requires a set of required capabilities
 * 3. A job may only run on a machine that has all required capabilities
 * 4. Support for job queuing when no suitable machine is available
 * 5. Support for job priorities
 * 6. Thread-safe operations for concurrent access
 * 
 * Famous Microsoft LLD Question
 */

// ==================== Enums ====================

enum JobStatus {
    PENDING,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum JobPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);
    
    private final int value;
    
    JobPriority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
}

enum MachineStatus {
    AVAILABLE,
    BUSY,
    MAINTENANCE,
    OFFLINE
}

// ==================== Core Entities ====================

/**
 * Represents a capability that machines can have and jobs can require
 */
class Capability {
    private final String name;
    private final String version;
    
    public Capability(String name, String version) {
        this.name = name;
        this.version = version;
    }
    
    public String getName() {
        return name;
    }
    
    public String getVersion() {
        return version;
    }
    
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
 * Represents a job to be executed
 */
class Job {
    private final String jobId;
    private final String name;
    private final Set<Capability> requiredCapabilities;
    private final JobPriority priority;
    private final long estimatedDurationMs;
    private JobStatus status;
    private String assignedMachineId;
    private long submissionTime;
    private long startTime;
    private long endTime;
    
    public Job(String jobId, String name, Set<Capability> requiredCapabilities, 
               JobPriority priority, long estimatedDurationMs) {
        this.jobId = jobId;
        this.name = name;
        this.requiredCapabilities = new HashSet<>(requiredCapabilities);
        this.priority = priority;
        this.estimatedDurationMs = estimatedDurationMs;
        this.status = JobStatus.PENDING;
        this.submissionTime = System.currentTimeMillis();
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public String getName() {
        return name;
    }
    
    public Set<Capability> getRequiredCapabilities() {
        return new HashSet<>(requiredCapabilities);
    }
    
    public JobPriority getPriority() {
        return priority;
    }
    
    public long getEstimatedDurationMs() {
        return estimatedDurationMs;
    }
    
    public JobStatus getStatus() {
        return status;
    }
    
    public void setStatus(JobStatus status) {
        this.status = status;
    }
    
    public String getAssignedMachineId() {
        return assignedMachineId;
    }
    
    public void setAssignedMachineId(String assignedMachineId) {
        this.assignedMachineId = assignedMachineId;
    }
    
    public long getSubmissionTime() {
        return submissionTime;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    @Override
    public String toString() {
        return String.format("Job[id=%s, name=%s, priority=%s, status=%s, capabilities=%s]",
            jobId, name, priority, status, requiredCapabilities);
    }
}

/**
 * Represents a machine that can execute jobs
 */
class Machine {
    private final String machineId;
    private final String name;
    private final Set<Capability> capabilities;
    private MachineStatus status;
    private Job currentJob;
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
    
    public String getMachineId() {
        return machineId;
    }
    
    public String getName() {
        return name;
    }
    
    public Set<Capability> getCapabilities() {
        return new HashSet<>(capabilities);
    }
    
    public MachineStatus getStatus() {
        return status;
    }
    
    public void setStatus(MachineStatus status) {
        this.status = status;
    }
    
    public Job getCurrentJob() {
        return currentJob;
    }
    
    public void setCurrentJob(Job job) {
        this.currentJob = job;
    }
    
    public int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }
    
    public List<Job> getRunningJobs() {
        return new ArrayList<>(runningJobs);
    }
    
    public boolean canAcceptJob() {
        return status == MachineStatus.AVAILABLE && 
               runningJobs.size() < maxConcurrentJobs;
    }
    
    public void addRunningJob(Job job) {
        runningJobs.add(job);
        if (runningJobs.size() >= maxConcurrentJobs) {
            status = MachineStatus.BUSY;
        }
    }
    
    public void removeRunningJob(Job job) {
        runningJobs.remove(job);
        if (runningJobs.size() < maxConcurrentJobs && 
            status == MachineStatus.BUSY) {
            status = MachineStatus.AVAILABLE;
        }
    }
    
    /**
     * Check if this machine has all required capabilities for a job
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
 * Interface for different scheduling strategies
 */
interface SchedulingStrategy {
    Machine selectMachine(Job job, List<Machine> availableMachines);
}

/**
 * First-Fit Strategy: Select the first machine that can run the job
 */
class FirstFitStrategy implements SchedulingStrategy {
    @Override
    public Machine selectMachine(Job job, List<Machine> availableMachines) {
        for (Machine machine : availableMachines) {
            if (machine.hasCapabilities(job.getRequiredCapabilities())) {
                return machine;
            }
        }
        return null;
    }
}

/**
 * Best-Fit Strategy: Select the machine with least extra capabilities
 * (most suitable match)
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
 * Load-Balanced Strategy: Select the machine with least current load
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
 * Main Job Scheduler class that manages job assignment to machines
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
        this.jobQueue = new PriorityQueue<>(
            Comparator.comparing(Job::getPriority, 
                Comparator.comparingInt(JobPriority::getValue).reversed())
                .thenComparing(Job::getSubmissionTime)
        );
        this.schedulingStrategy = schedulingStrategy;
        this.executorService = Executors.newCachedThreadPool();
        this.schedulerService = Executors.newScheduledThreadPool(1);
        this.running = false;
    }
    
    /**
     * Register a machine with the scheduler
     */
    public synchronized void registerMachine(Machine machine) {
        machines.put(machine.getMachineId(), machine);
        System.out.println("Machine registered: " + machine);
    }
    
    /**
     * Unregister a machine from the scheduler
     */
    public synchronized void unregisterMachine(String machineId) {
        Machine machine = machines.remove(machineId);
        if (machine != null) {
            machine.setStatus(MachineStatus.OFFLINE);
            System.out.println("Machine unregistered: " + machineId);
        }
    }
    
    /**
     * Submit a job to the scheduler
     */
    public synchronized String submitJob(Job job) {
        jobs.put(job.getJobId(), job);
        jobQueue.offer(job);
        System.out.println("Job submitted: " + job);
        
        // Try to schedule immediately
        tryScheduleJobs();
        
        return job.getJobId();
    }
    
    /**
     * Cancel a job
     */
    public synchronized boolean cancelJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        
        if (job.getStatus() == JobStatus.PENDING) {
            jobQueue.remove(job);
            job.setStatus(JobStatus.CANCELLED);
            System.out.println("Job cancelled: " + jobId);
            return true;
        } else if (job.getStatus() == JobStatus.RUNNING) {
            job.setStatus(JobStatus.CANCELLED);
            Machine machine = machines.get(job.getAssignedMachineId());
            if (machine != null) {
                machine.removeRunningJob(job);
            }
            System.out.println("Running job cancelled: " + jobId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Try to schedule pending jobs
     */
    private synchronized void tryScheduleJobs() {
        List<Job> jobsToSchedule = new ArrayList<>();
        
        // Get available machines
        List<Machine> availableMachines = machines.values().stream()
            .filter(Machine::canAcceptJob)
            .collect(Collectors.toList());
        
        if (availableMachines.isEmpty()) {
            return;
        }
        
        // Try to schedule jobs from queue
        while (!jobQueue.isEmpty() && !availableMachines.isEmpty()) {
            Job job = jobQueue.peek();
            
            Machine selectedMachine = schedulingStrategy.selectMachine(job, availableMachines);
            
            if (selectedMachine != null) {
                jobQueue.poll();
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
                // No suitable machine found for this job
                break;
            }
        }
        
        // Execute scheduled jobs
        for (Job job : jobsToSchedule) {
            executeJob(job);
        }
    }
    
    /**
     * Execute a job on its assigned machine
     */
    private void executeJob(Job job) {
        executorService.submit(() -> {
            try {
                job.setStatus(JobStatus.RUNNING);
                job.setStartTime(System.currentTimeMillis());
                
                System.out.println("Job started: " + job.getJobId());
                
                // Simulate job execution
                Thread.sleep(job.getEstimatedDurationMs());
                
                // Job completed
                job.setStatus(JobStatus.COMPLETED);
                job.setEndTime(System.currentTimeMillis());
                
                System.out.println("Job completed: " + job.getJobId());
                
                // Release machine
                Machine machine = machines.get(job.getAssignedMachineId());
                if (machine != null) {
                    synchronized (this) {
                        machine.removeRunningJob(job);
                        // Try to schedule more jobs
                        tryScheduleJobs();
                    }
                }
                
            } catch (InterruptedException e) {
                job.setStatus(JobStatus.FAILED);
                job.setEndTime(System.currentTimeMillis());
                System.out.println("Job failed: " + job.getJobId());
                
                // Release machine
                Machine machine = machines.get(job.getAssignedMachineId());
                if (machine != null) {
                    synchronized (this) {
                        machine.removeRunningJob(job);
                    }
                }
            }
        });
    }
    
    /**
     * Start the scheduler
     */
    public void start() {
        if (!running) {
            running = true;
            // Periodic job scheduling check
            schedulerService.scheduleAtFixedRate(() -> {
                synchronized (this) {
                    tryScheduleJobs();
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            System.out.println("Job Scheduler started");
        }
    }
    
    /**
     * Stop the scheduler
     */
    public void stop() {
        if (running) {
            running = false;
            schedulerService.shutdown();
            executorService.shutdown();
            System.out.println("Job Scheduler stopped");
        }
    }
    
    /**
     * Get job status
     */
    public JobStatus getJobStatus(String jobId) {
        Job job = jobs.get(jobId);
        return job != null ? job.getStatus() : null;
    }
    
    /**
     * Get all jobs
     */
    public List<Job> getAllJobs() {
        return new ArrayList<>(jobs.values());
    }
    
    /**
     * Get all machines
     */
    public List<Machine> getAllMachines() {
        return new ArrayList<>(machines.values());
    }
    
    /**
     * Get pending jobs count
     */
    public int getPendingJobsCount() {
        return jobQueue.size();
    }
    
    /**
     * Set scheduling strategy
     */
    public synchronized void setSchedulingStrategy(SchedulingStrategy strategy) {
        this.schedulingStrategy = strategy;
        System.out.println("Scheduling strategy updated: " + strategy.getClass().getSimpleName());
    }
    
    /**
     * Print scheduler status
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
        
        // Create capabilities
        Capability java = new Capability("Java", "11");
        Capability python = new Capability("Python", "3.9");
        Capability docker = new Capability("Docker", "20.10");
        Capability gpu = new Capability("GPU", "CUDA-11");
        Capability database = new Capability("Database", "PostgreSQL");
        
        // Create machines with different capabilities
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
        
        // Create and submit jobs
        Job job1 = new Job("J1", "Java Build Job", 
            new HashSet<>(Arrays.asList(java)), JobPriority.HIGH, 2000);
        scheduler.submitJob(job1);
        
        Job job2 = new Job("J2", "Python ML Training", 
            new HashSet<>(Arrays.asList(python, gpu)), JobPriority.CRITICAL, 3000);
        scheduler.submitJob(job2);
        
        Job job3 = new Job("J3", "Docker Container Build", 
            new HashSet<>(Arrays.asList(docker)), JobPriority.MEDIUM, 1500);
        scheduler.submitJob(job3);
        
        Job job4 = new Job("J4", "Database Migration", 
            new HashSet<>(Arrays.asList(database, python)), JobPriority.HIGH, 2500);
        scheduler.submitJob(job4);
        
        Job job5 = new Job("J5", "GPU Processing", 
            new HashSet<>(Arrays.asList(gpu)), JobPriority.LOW, 2000);
        scheduler.submitJob(job5);
        
        Job job6 = new Job("J6", "Java+Docker Deploy", 
            new HashSet<>(Arrays.asList(java, docker)), JobPriority.MEDIUM, 1800);
        scheduler.submitJob(job6);
        
        // Wait for some jobs to start
        Thread.sleep(1000);
        
        // Print status
        scheduler.printStatus();
        
        // Test job cancellation
        System.out.println("--- Testing Job Cancellation ---\n");
        scheduler.cancelJob("J5");
        
        // Wait for jobs to complete
        Thread.sleep(5000);
        
        // Final status
        scheduler.printStatus();
        
        // Test strategy change
        System.out.println("--- Changing Strategy to Load Balanced ---\n");
        scheduler.setSchedulingStrategy(new LoadBalancedStrategy());
        
        // Submit more jobs
        Job job7 = new Job("J7", "Python Analysis", 
            new HashSet<>(Arrays.asList(python)), JobPriority.HIGH, 2000);
        scheduler.submitJob(job7);
        
        Job job8 = new Job("J8", "Java Service", 
            new HashSet<>(Arrays.asList(java)), JobPriority.MEDIUM, 1500);
        scheduler.submitJob(job8);
        
        // Wait for completion
        Thread.sleep(3000);
        
        // Final status
        scheduler.printStatus();
        
        // Stop scheduler
        scheduler.stop();
        
        System.out.println("=== Demo Completed ===");
    }
}
