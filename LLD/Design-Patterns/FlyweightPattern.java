import java.util.*;

// ==================== FLYWEIGHT PATTERN ====================
// Definition: Uses sharing to support large numbers of fine-grained objects efficiently.
// Separates intrinsic (shared) state from extrinsic (unique) state.
//
// REAL System Design Interview Examples:
// 1. Connection Pool - Reuse database/HTTP connections
// 2. Font/Character Rendering - Share glyph data across documents
// 3. Game Object Pool - Reuse bullet/particle objects
// 4. Thread Pool - Reuse worker threads
// 5. Cache Entries - Share immutable configuration objects
//
// Interview Use Cases:
// - Design Connection Pool: Reuse expensive DB connections
// - Design Text Editor: Share character formatting objects
// - Design Game Engine: Object pooling for performance
// - Design CDN: Share common response headers/metadata
// - Design Icon/Image System: Reuse shared image data

// ==================== EXAMPLE 1: DATABASE CONNECTION POOL ====================
// Used in: HikariCP, c3p0, DBCP, pgBouncer
// Interview Question: Design a database connection pool

class DatabaseConnection {
    private String connectionId;
    private String host;
    private int port;
    private String database;
    private boolean inUse = false;
    private long createdAt;

    public DatabaseConnection(String host, int port, String database) {
        this.connectionId = "conn-" + UUID.randomUUID().toString().substring(0, 6);
        this.host = host;
        this.port = port;
        this.database = database;
        this.createdAt = System.currentTimeMillis();
        System.out.printf("   🔌 [EXPENSIVE] Creating new connection: %s → %s:%d/%s%n",
            connectionId, host, port, database);
    }

    public String getConnectionId() { return connectionId; }
    public boolean isInUse() { return inUse; }
    public void setInUse(boolean inUse) { this.inUse = inUse; }

    public void executeQuery(String sql) {
        System.out.printf("   🔍 [%s] Executing: %s%n", connectionId, sql);
    }

    @Override
    public String toString() {
        return String.format("[%s → %s:%d/%s, inUse=%s]", connectionId, host, port, database, inUse);
    }
}

class ConnectionPool {
    private List<DatabaseConnection> pool = new ArrayList<>();
    private String host;
    private int port;
    private String database;
    private int maxSize;
    private int totalCreated = 0;
    private int totalReused = 0;

    public ConnectionPool(String host, int port, String database, int maxSize) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.maxSize = maxSize;
    }

    public DatabaseConnection acquire() {
        // Try to find a free connection (FLYWEIGHT REUSE)
        for (DatabaseConnection conn : pool) {
            if (!conn.isInUse()) {
                conn.setInUse(true);
                totalReused++;
                System.out.printf("   ♻️  Reusing connection: %s (saved creation cost!)%n", conn.getConnectionId());
                return conn;
            }
        }

        // Create new connection if pool not full
        if (pool.size() < maxSize) {
            DatabaseConnection newConn = new DatabaseConnection(host, port, database);
            newConn.setInUse(true);
            pool.add(newConn);
            totalCreated++;
            return newConn;
        }

        System.out.println("   ⚠️  Pool exhausted! All " + maxSize + " connections in use");
        return null;
    }

    public void release(DatabaseConnection conn) {
        conn.setInUse(false);
        System.out.printf("   🔓 Released connection: %s (back to pool)%n", conn.getConnectionId());
    }

    public void printStats() {
        long inUse = pool.stream().filter(DatabaseConnection::isInUse).count();
        System.out.printf("   📊 Pool Stats: size=%d, inUse=%d, free=%d | Created: %d, Reused: %d%n",
            pool.size(), inUse, pool.size() - inUse, totalCreated, totalReused);
    }
}

// ==================== EXAMPLE 2: ICON/IMAGE CACHE ====================
// Used in: Web browsers, UI frameworks, game engines
// Interview Question: Design an image/asset management system

class SharedIconData {
    // Intrinsic state (shared, immutable)
    private String iconName;
    private byte[] pixelData;
    private int width;
    private int height;
    private String format;

    public SharedIconData(String iconName, int width, int height, String format) {
        this.iconName = iconName;
        this.width = width;
        this.height = height;
        this.format = format;
        this.pixelData = new byte[width * height * 4]; // RGBA
        System.out.printf("   💾 [EXPENSIVE] Loading icon '%s' (%dx%d, %d bytes)%n",
            iconName, width, height, pixelData.length);
    }

    public String getIconName() { return iconName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getMemorySize() { return pixelData.length; }
}

class IconInstance {
    // Extrinsic state (unique per usage)
    private SharedIconData sharedData;
    private int x;
    private int y;
    private double scale;
    private float opacity;

    public IconInstance(SharedIconData sharedData, int x, int y, double scale, float opacity) {
        this.sharedData = sharedData;
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.opacity = opacity;
    }

    public void render() {
        System.out.printf("   🖼️  Rendering '%s' at (%d,%d) scale=%.1f opacity=%.1f%n",
            sharedData.getIconName(), x, y, scale, opacity);
    }
}

class IconFactory {
    private Map<String, SharedIconData> iconCache = new HashMap<>();
    private int cacheMisses = 0;
    private int cacheHits = 0;

    public SharedIconData getIcon(String iconName, int width, int height, String format) {
        String key = iconName + "_" + width + "x" + height;
        if (iconCache.containsKey(key)) {
            cacheHits++;
            System.out.printf("   ♻️  Reusing cached icon '%s' (saved %d bytes!)%n",
                iconName, width * height * 4);
            return iconCache.get(key);
        }

        cacheMisses++;
        SharedIconData icon = new SharedIconData(iconName, width, height, format);
        iconCache.put(key, icon);
        return icon;
    }

    public void printStats() {
        int totalMemory = iconCache.values().stream().mapToInt(SharedIconData::getMemorySize).sum();
        System.out.printf("   📊 Icon Cache: %d unique icons, %d bytes | Hits: %d, Misses: %d%n",
            iconCache.size(), totalMemory, cacheHits, cacheMisses);
    }
}

// ==================== EXAMPLE 3: THREAD POOL ====================
// Used in: Java ThreadPoolExecutor, Nginx worker threads, Go goroutine pool
// Interview Question: Design a thread pool / task executor

class WorkerThread {
    private String threadId;
    private boolean busy = false;
    private int tasksCompleted = 0;

    public WorkerThread(String threadId) {
        this.threadId = threadId;
        System.out.printf("   🧵 [EXPENSIVE] Created worker thread: %s%n", threadId);
    }

    public void execute(Runnable task) {
        busy = true;
        System.out.printf("   ⚙️  [%s] Executing task...%n", threadId);
        task.run();
        tasksCompleted++;
        busy = false;
    }

    public boolean isBusy() { return busy; }
    public String getThreadId() { return threadId; }
    public int getTasksCompleted() { return tasksCompleted; }
}

class SimpleThreadPool {
    private List<WorkerThread> workers = new ArrayList<>();
    private Queue<Runnable> taskQueue = new LinkedList<>();
    private int poolSize;
    private int totalTasksSubmitted = 0;

    public SimpleThreadPool(int poolSize) {
        this.poolSize = poolSize;
        for (int i = 0; i < poolSize; i++) {
            workers.add(new WorkerThread("worker-" + (i + 1)));
        }
    }

    public void submit(Runnable task) {
        totalTasksSubmitted++;
        WorkerThread available = findAvailableWorker();
        if (available != null) {
            System.out.printf("   ♻️  Reusing thread %s for task%n", available.getThreadId());
            available.execute(task);
        } else {
            System.out.println("   📥 All workers busy, queuing task...");
            taskQueue.offer(task);
        }
    }

    private WorkerThread findAvailableWorker() {
        for (WorkerThread worker : workers) {
            if (!worker.isBusy()) return worker;
        }
        return null;
    }

    public void printStats() {
        System.out.printf("   📊 Thread Pool: %d workers, %d tasks submitted%n", poolSize, totalTasksSubmitted);
        for (WorkerThread w : workers) {
            System.out.printf("      %s: %d tasks completed%n", w.getThreadId(), w.getTasksCompleted());
        }
    }
}

// ==================== EXAMPLE 4: HTTP RESPONSE HEADER CACHE ====================
// Used in: Web servers, CDNs, API gateways
// Interview Question: Design a high-performance web server

class CachedResponseHeaders {
    // Intrinsic (shared) — common header sets
    private Map<String, String> headers;
    private String cacheKey;

    public CachedResponseHeaders(String cacheKey, Map<String, String> headers) {
        this.cacheKey = cacheKey;
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        System.out.printf("   📋 Created header template: '%s' (%d headers)%n", cacheKey, headers.size());
    }

    public Map<String, String> getHeaders() { return headers; }
    public String getCacheKey() { return cacheKey; }
}

class ResponseHeaderFactory {
    private Map<String, CachedResponseHeaders> headerCache = new HashMap<>();

    public ResponseHeaderFactory() {
        // Pre-create common header combinations
        Map<String, String> jsonHeaders = new LinkedHashMap<>();
        jsonHeaders.put("Content-Type", "application/json; charset=utf-8");
        jsonHeaders.put("X-Content-Type-Options", "nosniff");
        jsonHeaders.put("X-Frame-Options", "DENY");
        jsonHeaders.put("Strict-Transport-Security", "max-age=31536000");
        headerCache.put("json_secure", new CachedResponseHeaders("json_secure", jsonHeaders));

        Map<String, String> staticHeaders = new LinkedHashMap<>();
        staticHeaders.put("Cache-Control", "public, max-age=31536000");
        staticHeaders.put("X-Content-Type-Options", "nosniff");
        headerCache.put("static_cacheable", new CachedResponseHeaders("static_cacheable", staticHeaders));

        Map<String, String> corsHeaders = new LinkedHashMap<>();
        corsHeaders.put("Access-Control-Allow-Origin", "*");
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        corsHeaders.put("Access-Control-Max-Age", "86400");
        headerCache.put("cors_open", new CachedResponseHeaders("cors_open", corsHeaders));
    }

    public CachedResponseHeaders getHeaders(String type) {
        CachedResponseHeaders cached = headerCache.get(type);
        if (cached != null) {
            System.out.printf("   ♻️  Reusing header template: '%s'%n", type);
        }
        return cached;
    }

    public int getCacheSize() { return headerCache.size(); }
}

// ==================== DEMO ====================

public class FlyweightPattern {
    public static void main(String[] args) {
        System.out.println("========== FLYWEIGHT PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: CONNECTION POOL =====
        System.out.println("===== EXAMPLE 1: DATABASE CONNECTION POOL (HikariCP) =====\n");

        ConnectionPool pool = new ConnectionPool("db.example.com", 5432, "myapp", 3);

        // First 3 acquire create new connections
        DatabaseConnection c1 = pool.acquire();
        DatabaseConnection c2 = pool.acquire();
        DatabaseConnection c3 = pool.acquire();

        c1.executeQuery("SELECT * FROM users WHERE id = 1");
        c2.executeQuery("SELECT * FROM orders WHERE user_id = 1");

        // Release c1 back to pool
        pool.release(c1);

        // Next acquire REUSES c1 instead of creating new
        DatabaseConnection c4 = pool.acquire();
        c4.executeQuery("INSERT INTO logs VALUES ('login', NOW())");
        pool.release(c4);
        pool.release(c2);
        pool.release(c3);

        // Multiple reuses
        DatabaseConnection c5 = pool.acquire();
        pool.release(c5);
        pool.printStats();

        // ===== EXAMPLE 2: ICON CACHE =====
        System.out.println("\n\n===== EXAMPLE 2: ICON/IMAGE CACHE (UI/Browser) =====\n");

        IconFactory iconFactory = new IconFactory();

        // Same icon used in many places — only loaded ONCE
        SharedIconData heartIcon = iconFactory.getIcon("heart", 32, 32, "png");
        SharedIconData heartIcon2 = iconFactory.getIcon("heart", 32, 32, "png"); // Reused!
        SharedIconData starIcon = iconFactory.getIcon("star", 32, 32, "png");
        SharedIconData heartIcon3 = iconFactory.getIcon("heart", 32, 32, "png"); // Reused!

        // Each usage has different position (extrinsic state)
        List<IconInstance> instances = Arrays.asList(
            new IconInstance(heartIcon, 10, 20, 1.0, 1.0f),
            new IconInstance(heartIcon, 50, 20, 1.0, 1.0f),
            new IconInstance(heartIcon, 90, 20, 0.5, 0.8f),
            new IconInstance(starIcon, 200, 20, 1.5, 1.0f)
        );

        System.out.println("\nRendering all icons:");
        for (IconInstance inst : instances) {
            inst.render();
        }

        iconFactory.printStats();
        System.out.printf("   💡 Without flyweight: 4 icons × %d bytes = %d bytes%n",
            32 * 32 * 4, 4 * 32 * 32 * 4);
        System.out.printf("   💡 With flyweight: 2 unique × %d bytes = %d bytes (50%% savings!)%n",
            32 * 32 * 4, 2 * 32 * 32 * 4);

        // ===== EXAMPLE 3: THREAD POOL =====
        System.out.println("\n\n===== EXAMPLE 3: THREAD POOL (Java ThreadPoolExecutor) =====\n");

        SimpleThreadPool threadPool = new SimpleThreadPool(3);

        // Submit 6 tasks but only 3 threads created — reused!
        for (int i = 1; i <= 6; i++) {
            final int taskNum = i;
            threadPool.submit(() -> System.out.printf("      📝 Task %d completed%n", taskNum));
        }
        threadPool.printStats();

        // ===== EXAMPLE 4: RESPONSE HEADER CACHE =====
        System.out.println("\n\n===== EXAMPLE 4: HTTP RESPONSE HEADER CACHE (Web Server) =====\n");

        ResponseHeaderFactory headerFactory = new ResponseHeaderFactory();
        System.out.println("\nServing 1000 API responses with shared headers:");
        for (int i = 0; i < 5; i++) {
            CachedResponseHeaders headers = headerFactory.getHeaders("json_secure");
        }
        System.out.println("   💡 1000 responses share same header object — zero allocation!");

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Connection pools (HikariCP, c3p0, pgBouncer)");
        System.out.println("• Image/icon caching (browsers, game engines)");
        System.out.println("• Thread pools (Java ExecutorService, Nginx)");
        System.out.println("• HTTP header caching (web servers, CDNs)");
    }
}
