import java.util.*;
import java.time.*;

// ==================== SINGLETON PATTERN ====================
// Definition: Ensures a class has only one instance and provides a global point of
// access to it.
//
// Real-World Examples:
// 1. Database Connection Pool - Single pool for all connections
// 2. Configuration Manager - Single config instance
// 3. Logger - Single logging instance
// 4. Cache Manager - Single cache instance
// 5. Thread Pool - Single pool for all threads
//
// When to Use:
// - Exactly one instance of a class needed
// - Instance must be accessible from well-known access point
// - Instance should be extensible by subclassing
// - Controlled access to sole instance
//
// Benefits:
// 1. Controlled access to single instance
// 2. Reduced namespace pollution
// 3. Permits refinement of operations
// 4. Variable number of instances possible
//
// Implementation Types:
// 1. Eager Initialization - Thread-safe, instance created at class loading
// 2. Lazy Initialization - Instance created when first needed
// 3. Thread-Safe Lazy - Synchronized for multi-threading
// 4. Double-Checked Locking - Performance optimization
// 5. Bill Pugh (Inner Static Helper) - Best practice

// ==================== EXAMPLE 1: DATABASE CONNECTION POOL ====================

class DatabaseConnectionPool {
    private static DatabaseConnectionPool instance;
    private List<String> connections;
    private static final int MAX_POOL_SIZE = 10;
    
    // Private constructor prevents instantiation
    private DatabaseConnectionPool() {
        connections = new ArrayList<>();
        initializePool();
        System.out.println("🔌 Database Connection Pool initialized");
    }
    
    // Thread-safe getInstance with double-checked locking
    public static DatabaseConnectionPool getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnectionPool.class) {
                if (instance == null) {
                    instance = new DatabaseConnectionPool();
                }
            }
        }
        return instance;
    }
    
    private void initializePool() {
        for (int i = 1; i <= MAX_POOL_SIZE; i++) {
            connections.add("Connection-" + i);
        }
    }
    
    public String getConnection() {
        if (connections.isEmpty()) {
            return null;
        }
        String conn = connections.remove(0);
        System.out.println("✅ Getting connection: " + conn);
        return conn;
    }
    
    public void releaseConnection(String connection) {
        connections.add(connection);
        System.out.println("🔄 Released connection: " + connection);
    }
    
    public int getAvailableConnections() {
        return connections.size();
    }
}

// ==================== EXAMPLE 2: CONFIGURATION MANAGER ====================

class ConfigurationManager {
    private static ConfigurationManager instance;
    private Map<String, String> config;
    
    private ConfigurationManager() {
        config = new HashMap<>();
        loadConfiguration();
        System.out.println("⚙️ Configuration Manager initialized");
    }
    
    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }
    
    private void loadConfiguration() {
        // Simulate loading from file
        config.put("database.host", "localhost");
        config.put("database.port", "5432");
        config.put("app.name", "MyApp");
        config.put("app.version", "1.0.0");
        config.put("cache.enabled", "true");
    }
    
    public String getProperty(String key) {
        return config.getOrDefault(key, "");
    }
    
    public void setProperty(String key, String value) {
        config.put(key, value);
        System.out.println("📝 Updated config: " + key + " = " + value);
    }
    
    public void displayConfig() {
        System.out.println("\n📋 Current Configuration:");
        config.forEach((key, value) -> 
            System.out.println("   " + key + " = " + value));
    }
}

// ==================== EXAMPLE 3: LOGGER (Bill Pugh Implementation) ====================

class Logger {
    private List<String> logs;
    
    // Private constructor
    private Logger() {
        logs = new ArrayList<>();
        System.out.println("📝 Logger initialized");
    }
    
    // Bill Pugh Singleton - Inner static helper class
    private static class SingletonHelper {
        private static final Logger INSTANCE = new Logger();
    }
    
    public static Logger getInstance() {
        return SingletonHelper.INSTANCE;
    }
    
    public void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logEntry = String.format("[%s] [%s] %s", timestamp, level, message);
        logs.add(logEntry);
        System.out.println(logEntry);
    }
    
    public void info(String message) {
        log("INFO", message);
    }
    
    public void warning(String message) {
        log("WARNING", message);
    }
    
    public void error(String message) {
        log("ERROR", message);
    }
    
    public void displayLogs() {
        System.out.println("\n📜 Log History:");
        logs.forEach(log -> System.out.println("   " + log));
    }
    
    public int getLogCount() {
        return logs.size();
    }
}

// ==================== EXAMPLE 4: CACHE MANAGER (Eager Initialization) ====================

class CacheManager {
    // Eager initialization - thread-safe
    private static final CacheManager instance = new CacheManager();
    private Map<String, Object> cache;
    private int hitCount;
    private int missCount;
    
    private CacheManager() {
        cache = new HashMap<>();
        hitCount = 0;
        missCount = 0;
        System.out.println("💾 Cache Manager initialized");
    }
    
    public static CacheManager getInstance() {
        return instance;
    }
    
    public void put(String key, Object value) {
        cache.put(key, value);
        System.out.println("✅ Cached: " + key);
    }
    
    public Object get(String key) {
        if (cache.containsKey(key)) {
            hitCount++;
            System.out.println("🎯 Cache HIT: " + key);
            return cache.get(key);
        } else {
            missCount++;
            System.out.println("❌ Cache MISS: " + key);
            return null;
        }
    }
    
    public void remove(String key) {
        cache.remove(key);
        System.out.println("🗑️ Removed from cache: " + key);
    }
    
    public void clear() {
        cache.clear();
        System.out.println("🧹 Cache cleared");
    }
    
    public void displayStats() {
        System.out.println("\n📊 Cache Statistics:");
        System.out.println("   Total items: " + cache.size());
        System.out.println("   Cache hits: " + hitCount);
        System.out.println("   Cache misses: " + missCount);
        double hitRate = (hitCount + missCount) > 0 
            ? (double) hitCount / (hitCount + missCount) * 100 
            : 0;
        System.out.printf("   Hit rate: %.2f%%%n", hitRate);
    }
}

// ==================== EXAMPLE 5: THREAD POOL MANAGER ====================

class ThreadPoolManager {
    private static volatile ThreadPoolManager instance;
    private int poolSize;
    private int activeThreads;
    private Queue<String> taskQueue;
    
    private ThreadPoolManager() {
        this.poolSize = 5;
        this.activeThreads = 0;
        this.taskQueue = new LinkedList<>();
        System.out.println("🧵 Thread Pool Manager initialized with " + poolSize + " threads");
    }
    
    // Double-checked locking with volatile
    public static ThreadPoolManager getInstance() {
        if (instance == null) {
            synchronized (ThreadPoolManager.class) {
                if (instance == null) {
                    instance = new ThreadPoolManager();
                }
            }
        }
        return instance;
    }
    
    public void submitTask(String task) {
        if (activeThreads < poolSize) {
            activeThreads++;
            System.out.println("🚀 Executing task: " + task + " (Active threads: " + activeThreads + ")");
            // Simulate task execution
            activeThreads--;
        } else {
            taskQueue.offer(task);
            System.out.println("⏳ Task queued: " + task + " (Queue size: " + taskQueue.size() + ")");
        }
    }
    
    public void processQueue() {
        while (!taskQueue.isEmpty() && activeThreads < poolSize) {
            String task = taskQueue.poll();
            activeThreads++;
            System.out.println("🚀 Processing queued task: " + task);
            activeThreads--;
        }
    }
    
    public void displayStatus() {
        System.out.println("\n🔧 Thread Pool Status:");
        System.out.println("   Pool size: " + poolSize);
        System.out.println("   Active threads: " + activeThreads);
        System.out.println("   Queued tasks: " + taskQueue.size());
    }
}

// ==================== EXAMPLE 6: COMPARISON OF IMPLEMENTATIONS ====================

// Eager Initialization
class EagerSingleton {
    private static final EagerSingleton instance = new EagerSingleton();
    
    private EagerSingleton() {
        System.out.println("✅ Eager Singleton created");
    }
    
    public static EagerSingleton getInstance() {
        return instance;
    }
}

// Lazy Initialization (Not thread-safe)
class LazySingleton {
    private static LazySingleton instance;
    
    private LazySingleton() {
        System.out.println("✅ Lazy Singleton created");
    }
    
    public static LazySingleton getInstance() {
        if (instance == null) {
            instance = new LazySingleton();
        }
        return instance;
    }
}

// Thread-Safe Lazy (Synchronized method)
class ThreadSafeLazySingleton {
    private static ThreadSafeLazySingleton instance;
    
    private ThreadSafeLazySingleton() {
        System.out.println("✅ Thread-Safe Lazy Singleton created");
    }
    
    public static synchronized ThreadSafeLazySingleton getInstance() {
        if (instance == null) {
            instance = new ThreadSafeLazySingleton();
        }
        return instance;
    }
}

// Double-Checked Locking
class DoubleCheckedLockingSingleton {
    private static volatile DoubleCheckedLockingSingleton instance;
    
    private DoubleCheckedLockingSingleton() {
        System.out.println("✅ Double-Checked Locking Singleton created");
    }
    
    public static DoubleCheckedLockingSingleton getInstance() {
        if (instance == null) {
            synchronized (DoubleCheckedLockingSingleton.class) {
                if (instance == null) {
                    instance = new DoubleCheckedLockingSingleton();
                }
            }
        }
        return instance;
    }
}

// Bill Pugh (Best Practice)
class BillPughSingleton {
    private BillPughSingleton() {
        System.out.println("✅ Bill Pugh Singleton created");
    }
    
    private static class SingletonHelper {
        private static final BillPughSingleton INSTANCE = new BillPughSingleton();
    }
    
    public static BillPughSingleton getInstance() {
        return SingletonHelper.INSTANCE;
    }
}

// ==================== DEMO ====================

public class SingletonPattern {
    public static void main(String[] args) {
        System.out.println("========== SINGLETON PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: DATABASE CONNECTION POOL =====
        System.out.println("===== EXAMPLE 1: DATABASE CONNECTION POOL =====\n");
        
        DatabaseConnectionPool pool1 = DatabaseConnectionPool.getInstance();
        DatabaseConnectionPool pool2 = DatabaseConnectionPool.getInstance();
        
        System.out.println("Are both instances same? " + (pool1 == pool2));
        System.out.println("Available connections: " + pool1.getAvailableConnections());
        
        String conn1 = pool1.getConnection();
        String conn2 = pool2.getConnection();
        System.out.println("Available connections: " + pool1.getAvailableConnections());
        
        pool1.releaseConnection(conn1);
        pool2.releaseConnection(conn2);
        System.out.println("Available connections: " + pool1.getAvailableConnections());

        // ===== EXAMPLE 2: CONFIGURATION MANAGER =====
        System.out.println("\n\n===== EXAMPLE 2: CONFIGURATION MANAGER =====\n");
        
        ConfigurationManager config1 = ConfigurationManager.getInstance();
        ConfigurationManager config2 = ConfigurationManager.getInstance();
        
        System.out.println("Are both instances same? " + (config1 == config2));
        config1.displayConfig();
        
        config1.setProperty("app.version", "2.0.0");
        System.out.println("\nAfter update from config1:");
        config2.displayConfig(); // Should show updated value

        // ===== EXAMPLE 3: LOGGER =====
        System.out.println("\n\n===== EXAMPLE 3: LOGGER =====\n");
        
        Logger logger1 = Logger.getInstance();
        Logger logger2 = Logger.getInstance();
        
        System.out.println("Are both instances same? " + (logger1 == logger2));
        
        logger1.info("Application started");
        logger2.warning("Low memory detected");
        logger1.error("Connection timeout");
        
        logger1.displayLogs();
        System.out.println("\nTotal logs: " + logger2.getLogCount());

        // ===== EXAMPLE 4: CACHE MANAGER =====
        System.out.println("\n\n===== EXAMPLE 4: CACHE MANAGER =====\n");
        
        CacheManager cache1 = CacheManager.getInstance();
        CacheManager cache2 = CacheManager.getInstance();
        
        System.out.println("Are both instances same? " + (cache1 == cache2));
        
        cache1.put("user:123", "Alice");
        cache1.put("user:456", "Bob");
        
        cache2.get("user:123"); // Hit
        cache2.get("user:456"); // Hit
        cache1.get("user:789"); // Miss
        
        cache1.displayStats();

        // ===== EXAMPLE 5: THREAD POOL MANAGER =====
        System.out.println("\n\n===== EXAMPLE 5: THREAD POOL MANAGER =====\n");
        
        ThreadPoolManager pool = ThreadPoolManager.getInstance();
        
        for (int i = 1; i <= 8; i++) {
            pool.submitTask("Task-" + i);
        }
        
        pool.displayStatus();
        pool.processQueue();

        // ===== EXAMPLE 6: SINGLETON IMPLEMENTATIONS COMPARISON =====
        System.out.println("\n\n===== EXAMPLE 6: SINGLETON IMPLEMENTATIONS =====\n");
        
        System.out.println("1. Eager Initialization:");
        EagerSingleton eager1 = EagerSingleton.getInstance();
        EagerSingleton eager2 = EagerSingleton.getInstance();
        System.out.println("   Same instance? " + (eager1 == eager2));
        
        System.out.println("\n2. Lazy Initialization:");
        LazySingleton lazy1 = LazySingleton.getInstance();
        LazySingleton lazy2 = LazySingleton.getInstance();
        System.out.println("   Same instance? " + (lazy1 == lazy2));
        
        System.out.println("\n3. Thread-Safe Lazy:");
        ThreadSafeLazySingleton threadSafe1 = ThreadSafeLazySingleton.getInstance();
        ThreadSafeLazySingleton threadSafe2 = ThreadSafeLazySingleton.getInstance();
        System.out.println("   Same instance? " + (threadSafe1 == threadSafe2));
        
        System.out.println("\n4. Double-Checked Locking:");
        DoubleCheckedLockingSingleton dcl1 = DoubleCheckedLockingSingleton.getInstance();
        DoubleCheckedLockingSingleton dcl2 = DoubleCheckedLockingSingleton.getInstance();
        System.out.println("   Same instance? " + (dcl1 == dcl2));
        
        System.out.println("\n5. Bill Pugh (Best Practice):");
        BillPughSingleton billPugh1 = BillPughSingleton.getInstance();
        BillPughSingleton billPugh2 = BillPughSingleton.getInstance();
        System.out.println("   Same instance? " + (billPugh1 == billPugh2));

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
