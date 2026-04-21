import java.util.*;
import java.util.concurrent.*;

// ==================== PROXY PATTERN ====================
// Definition: Provides a surrogate or placeholder for another object to control access to it.
// Types: Remote Proxy, Virtual Proxy, Protection Proxy, Caching Proxy, Logging Proxy.
//
// REAL System Design Interview Examples:
// 1. Caching Proxy - Cache responses before hitting real service (CDN, Redis)
// 2. Rate Limiting Proxy - Control access rate to downstream services
// 3. Authentication/Authorization Proxy - API Gateway security check
// 4. Lazy Loading Proxy - Load heavy resources on demand
// 5. Logging/Monitoring Proxy - Intercept calls for observability
//
// Interview Use Cases:
// - Design CDN: Caching proxy in front of origin server
// - Design API Gateway: Auth + rate limit + logging proxy
// - Design Database Connection Pool: Virtual proxy for connections
// - Design Image Loading: Lazy proxy for heavy images
// - Design Service Mesh: Sidecar proxy (Envoy/Istio)

// ==================== EXAMPLE 1: CACHING PROXY ====================
// Used in: CDN, Redis cache layer, HTTP caching
// Interview Question: Design a caching layer / CDN

interface DataService {
    String fetchData(String key);
    void updateData(String key, String value);
}

class RealDatabaseService implements DataService {
    @Override
    public String fetchData(String key) {
        // Simulate expensive DB query
        System.out.printf("   🗄️  [DB] Querying database for key: %s (200ms latency)%n", key);
        return "db_value_for_" + key;
    }

    @Override
    public void updateData(String key, String value) {
        System.out.printf("   🗄️  [DB] Updating key: %s = %s%n", key, value);
    }
}

class CachingProxy implements DataService {
    private DataService realService;
    private Map<String, String> cache = new ConcurrentHashMap<>();
    private Map<String, Long> cacheTTL = new ConcurrentHashMap<>();
    private long ttlMs;
    private int cacheHits = 0;
    private int cacheMisses = 0;

    public CachingProxy(DataService realService, long ttlMs) {
        this.realService = realService;
        this.ttlMs = ttlMs;
    }

    @Override
    public String fetchData(String key) {
        // Check cache first
        if (cache.containsKey(key) && !isExpired(key)) {
            cacheHits++;
            System.out.printf("   ⚡ [Cache HIT] Key: %s (avoided DB call)%n", key);
            return cache.get(key);
        }

        // Cache miss - fetch from real service
        cacheMisses++;
        System.out.printf("   💨 [Cache MISS] Key: %s, fetching from DB...%n", key);
        String value = realService.fetchData(key);

        // Store in cache
        cache.put(key, value);
        cacheTTL.put(key, System.currentTimeMillis() + ttlMs);
        return value;
    }

    @Override
    public void updateData(String key, String value) {
        // Write-through: update cache and DB
        realService.updateData(key, value);
        cache.put(key, value);
        cacheTTL.put(key, System.currentTimeMillis() + ttlMs);
        System.out.printf("   🔄 [Cache] Updated cache for key: %s%n", key);
    }

    private boolean isExpired(String key) {
        Long expiry = cacheTTL.get(key);
        return expiry == null || System.currentTimeMillis() > expiry;
    }

    public String getStats() {
        int total = cacheHits + cacheMisses;
        double hitRate = total > 0 ? (cacheHits * 100.0 / total) : 0;
        return String.format("Hits: %d, Misses: %d, Hit Rate: %.1f%%", cacheHits, cacheMisses, hitRate);
    }
}

// ==================== EXAMPLE 2: RATE LIMITING PROXY ====================
// Used in: API Gateway, Nginx, Kong
// Interview Question: Design a rate limiter

interface APIService {
    String handleRequest(String clientId, String endpoint, Map<String, String> params);
}

class RealAPIService implements APIService {
    @Override
    public String handleRequest(String clientId, String endpoint, Map<String, String> params) {
        System.out.printf("   🔧 [API] Processing %s %s for %s%n", endpoint, params, clientId);
        return "response_200_ok";
    }
}

class RateLimitingProxy implements APIService {
    private APIService realService;
    private Map<String, Queue<Long>> requestWindows = new ConcurrentHashMap<>();
    private int maxRequests;
    private long windowMs;

    public RateLimitingProxy(APIService realService, int maxRequests, long windowMs) {
        this.realService = realService;
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
    public String handleRequest(String clientId, String endpoint, Map<String, String> params) {
        Queue<Long> window = requestWindows.computeIfAbsent(clientId, k -> new LinkedList<>());
        long now = System.currentTimeMillis();

        // Remove expired entries
        while (!window.isEmpty() && now - window.peek() > windowMs) {
            window.poll();
        }

        if (window.size() >= maxRequests) {
            System.out.printf("   🚫 [Rate Limit] Client %s exceeded %d req/%dms — 429 Too Many Requests%n",
                clientId, maxRequests, windowMs);
            return "error_429_rate_limited";
        }

        window.offer(now);
        System.out.printf("   ✅ [Rate Limit] Client %s: %d/%d requests in window%n",
            clientId, window.size(), maxRequests);
        return realService.handleRequest(clientId, endpoint, params);
    }
}

// ==================== EXAMPLE 3: AUTH PROXY (Protection Proxy) ====================
// Used in: API Gateway, OAuth middleware, RBAC
// Interview Question: Design an API gateway with authentication

class AuthProxy implements APIService {
    private APIService realService;
    private Map<String, String> tokenToUser = new HashMap<>();
    private Map<String, Set<String>> userPermissions = new HashMap<>();

    public AuthProxy(APIService realService) {
        this.realService = realService;
        // Simulate token store
        tokenToUser.put("token_admin_123", "admin");
        tokenToUser.put("token_user_456", "reader");
        // Simulate permissions
        userPermissions.put("admin", new HashSet<>(Arrays.asList("read", "write", "delete")));
        userPermissions.put("reader", new HashSet<>(Arrays.asList("read")));
    }

    @Override
    public String handleRequest(String clientId, String endpoint, Map<String, String> params) {
        String token = params.get("auth_token");

        // Step 1: Authenticate
        if (token == null || !tokenToUser.containsKey(token)) {
            System.out.printf("   🔒 [Auth] DENIED: Invalid token for %s — 401 Unauthorized%n", clientId);
            return "error_401_unauthorized";
        }

        String role = tokenToUser.get(token);
        System.out.printf("   🔑 [Auth] Authenticated: %s (role: %s)%n", clientId, role);

        // Step 2: Authorize
        String requiredPermission = getRequiredPermission(endpoint);
        Set<String> permissions = userPermissions.getOrDefault(role, Collections.emptySet());

        if (!permissions.contains(requiredPermission)) {
            System.out.printf("   🚫 [Auth] FORBIDDEN: %s lacks '%s' permission — 403 Forbidden%n",
                role, requiredPermission);
            return "error_403_forbidden";
        }

        System.out.printf("   ✅ [Auth] Authorized: %s has '%s' permission%n", role, requiredPermission);
        return realService.handleRequest(clientId, endpoint, params);
    }

    private String getRequiredPermission(String endpoint) {
        if (endpoint.startsWith("GET")) return "read";
        if (endpoint.startsWith("POST") || endpoint.startsWith("PUT")) return "write";
        if (endpoint.startsWith("DELETE")) return "delete";
        return "read";
    }
}

// ==================== EXAMPLE 4: LOGGING/MONITORING PROXY ====================
// Used in: Service mesh (Istio/Envoy sidecar), APM tools
// Interview Question: Design a monitoring/observability system

class LoggingProxy implements APIService {
    private APIService realService;
    private List<Map<String, Object>> requestLog = new ArrayList<>();

    public LoggingProxy(APIService realService) {
        this.realService = realService;
    }

    @Override
    public String handleRequest(String clientId, String endpoint, Map<String, String> params) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        System.out.printf("   📝 [Log] REQUEST %s: %s %s from %s%n", requestId, endpoint, params, clientId);

        String response;
        try {
            response = realService.handleRequest(clientId, endpoint, params);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            System.out.printf("   📝 [Log] RESPONSE %s: ERROR in %dms — %s%n", requestId, duration, e.getMessage());
            logRequest(requestId, clientId, endpoint, duration, "ERROR");
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("   📝 [Log] RESPONSE %s: %s in %dms%n", requestId, response, duration);
        logRequest(requestId, clientId, endpoint, duration, "SUCCESS");
        return response;
    }

    private void logRequest(String requestId, String clientId, String endpoint, long duration, String status) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("requestId", requestId);
        entry.put("clientId", clientId);
        entry.put("endpoint", endpoint);
        entry.put("duration_ms", duration);
        entry.put("status", status);
        requestLog.add(entry);
    }

    public void printStats() {
        System.out.printf("   📊 Total requests logged: %d%n", requestLog.size());
        long totalDuration = requestLog.stream()
            .mapToLong(e -> (long) e.get("duration_ms")).sum();
        if (!requestLog.isEmpty()) {
            System.out.printf("   📊 Avg latency: %dms%n", totalDuration / requestLog.size());
        }
    }
}

// ==================== EXAMPLE 5: VIRTUAL/LAZY PROXY ====================
// Used in: Image loading, ORM lazy loading (Hibernate)
// Interview Question: Design a content delivery / image service

interface Image {
    void display();
    int getWidth();
    int getHeight();
}

class HighResImage implements Image {
    private String filename;
    private byte[] imageData;

    public HighResImage(String filename) {
        this.filename = filename;
        loadFromDisk(); // Expensive operation
    }

    private void loadFromDisk() {
        System.out.printf("   💾 [Image] Loading high-res image '%s' from disk (500MB)...%n", filename);
        this.imageData = new byte[500_000_000]; // Simulated
    }

    @Override
    public void display() {
        System.out.printf("   🖼️  Displaying '%s' (%d bytes)%n", filename, imageData.length);
    }

    @Override
    public int getWidth() { return 4096; }
    @Override
    public int getHeight() { return 2160; }
}

class LazyImageProxy implements Image {
    private String filename;
    private HighResImage realImage; // Loaded on demand

    public LazyImageProxy(String filename) {
        this.filename = filename;
        System.out.printf("   📋 [Proxy] Created lazy proxy for '%s' (no disk I/O yet)%n", filename);
    }

    @Override
    public void display() {
        if (realImage == null) {
            System.out.printf("   ⏳ [Proxy] First access to '%s', loading now...%n", filename);
            realImage = new HighResImage(filename);
        }
        realImage.display();
    }

    @Override
    public int getWidth() { return 4096; } // Metadata can be returned without loading
    @Override
    public int getHeight() { return 2160; }
}

// ==================== DEMO ====================

public class ProxyPattern {
    public static void main(String[] args) {
        System.out.println("========== PROXY PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: CACHING PROXY =====
        System.out.println("===== EXAMPLE 1: CACHING PROXY (CDN / Redis) =====\n");

        CachingProxy cachingService = new CachingProxy(new RealDatabaseService(), 5000);
        cachingService.fetchData("user:1001");   // Miss
        cachingService.fetchData("user:1001");   // Hit
        cachingService.fetchData("user:1002");   // Miss
        cachingService.fetchData("user:1001");   // Hit
        cachingService.updateData("user:1001", "updated_value");
        cachingService.fetchData("user:1001");   // Hit (write-through)
        System.out.println("   📊 Cache Stats: " + cachingService.getStats());

        // ===== EXAMPLE 2: RATE LIMITING PROXY =====
        System.out.println("\n\n===== EXAMPLE 2: RATE LIMITING PROXY (API Gateway) =====\n");

        RateLimitingProxy rateLimiter = new RateLimitingProxy(new RealAPIService(), 3, 10000);
        for (int i = 0; i < 5; i++) {
            rateLimiter.handleRequest("client-A", "GET /users", new HashMap<>());
        }

        // ===== EXAMPLE 3: AUTH PROXY =====
        System.out.println("\n\n===== EXAMPLE 3: AUTHENTICATION PROXY (API Gateway) =====\n");

        AuthProxy authProxy = new AuthProxy(new RealAPIService());
        // No token
        authProxy.handleRequest("client-1", "GET /users", new HashMap<>());
        System.out.println();
        // Valid admin token
        Map<String, String> adminParams = new HashMap<>();
        adminParams.put("auth_token", "token_admin_123");
        authProxy.handleRequest("client-2", "DELETE /users/1", adminParams);
        System.out.println();
        // Reader trying to write
        Map<String, String> readerParams = new HashMap<>();
        readerParams.put("auth_token", "token_user_456");
        authProxy.handleRequest("client-3", "POST /users", readerParams);

        // ===== EXAMPLE 4: LOGGING PROXY =====
        System.out.println("\n\n===== EXAMPLE 4: LOGGING/MONITORING PROXY (Service Mesh) =====\n");

        LoggingProxy loggingProxy = new LoggingProxy(new RealAPIService());
        loggingProxy.handleRequest("svc-orders", "GET /products", Map.of("limit", "10"));
        System.out.println();
        loggingProxy.handleRequest("svc-users", "POST /auth", Map.of("username", "alice"));
        System.out.println();
        loggingProxy.printStats();

        // ===== EXAMPLE 5: LAZY LOADING PROXY =====
        System.out.println("\n\n===== EXAMPLE 5: LAZY LOADING PROXY (Image/ORM) =====\n");

        System.out.println("Creating image proxies (no disk I/O):");
        Image img1 = new LazyImageProxy("photo_4k_001.raw");
        Image img2 = new LazyImageProxy("photo_4k_002.raw");
        Image img3 = new LazyImageProxy("photo_4k_003.raw");

        System.out.println("\nDisplaying only img1 (loads on demand):");
        img1.display();

        System.out.println("\nimg2 and img3 never loaded — memory saved!");
        System.out.printf("   img2 dimensions: %dx%d (metadata only, no load)%n", img2.getWidth(), img2.getHeight());

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• CDN caching proxy (CloudFront, Varnish)");
        System.out.println("• API Gateway rate limiting (Kong, Nginx)");
        System.out.println("• Authentication proxy (OAuth2 middleware)");
        System.out.println("• Service mesh sidecar (Envoy, Istio)");
        System.out.println("• ORM lazy loading (Hibernate, JPA)");
    }
}
