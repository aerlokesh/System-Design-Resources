import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/**
 * API GATEWAY - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Request routing (path-based, header-based)
 * - Authentication/Authorization middleware
 * - Rate limiting per client
 * - Request/Response transformation
 * - Circuit breaker integration
 * - Request logging and metrics
 * - API versioning
 * 
 * Interview talking points:
 * - Used by: AWS API Gateway, Kong, Zuul, Envoy
 * - Single entry point for all microservices
 * - Cross-cutting concerns: Auth, rate limit, logging, CORS
 * - Protocol translation: REST to gRPC, WebSocket
 * - Request aggregation: Combine multiple backend calls
 * - Canary deployments: Route % of traffic to new version
 */
class APIGateway {

    // ==================== HTTP REQUEST/RESPONSE ====================
    static class HttpRequest {
        final String method;
        final String path;
        final Map<String, String> headers;
        final Map<String, String> queryParams;
        final String body;
        final String clientIP;
        final long timestamp;

        HttpRequest(String method, String path, String clientIP) {
            this.method = method;
            this.path = path;
            this.headers = new HashMap<>();
            this.queryParams = new HashMap<>();
            this.body = "";
            this.clientIP = clientIP;
            this.timestamp = System.currentTimeMillis();
        }

        HttpRequest withHeader(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public String toString() {
            return String.format("%s %s [%s]", method, path, clientIP);
        }
    }

    static class HttpResponse {
        int statusCode;
        final Map<String, String> headers = new HashMap<>();
        String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public String toString() {
            return String.format("HTTP %d: %s", statusCode, body.substring(0, Math.min(50, body.length())));
        }
    }

    // ==================== MIDDLEWARE (Filter Chain) ====================
    interface Middleware {
        HttpResponse handle(HttpRequest request, MiddlewareChain chain);
        String getName();
    }

    static class MiddlewareChain {
        private final List<Middleware> middlewares;
        private int index = 0;
        private final Function<HttpRequest, HttpResponse> finalHandler;

        MiddlewareChain(List<Middleware> middlewares, Function<HttpRequest, HttpResponse> handler) {
            this.middlewares = new ArrayList<>(middlewares);
            this.finalHandler = handler;
        }

        HttpResponse proceed(HttpRequest request) {
            if (index < middlewares.size()) {
                return middlewares.get(index++).handle(request, this);
            }
            return finalHandler.apply(request);
        }
    }

    // ==================== AUTHENTICATION MIDDLEWARE ====================
    static class AuthMiddleware implements Middleware {
        private final Set<String> validTokens = ConcurrentHashMap.newKeySet();
        private final Set<String> publicPaths = new HashSet<>(Arrays.asList("/health", "/login", "/register"));

        void addToken(String token) { validTokens.add(token); }

        public HttpResponse handle(HttpRequest request, MiddlewareChain chain) {
            if (publicPaths.contains(request.path)) return chain.proceed(request);
            String token = request.headers.get("Authorization");
            if (token == null || !validTokens.contains(token.replace("Bearer ", ""))) {
                return new HttpResponse(401, "{\"error\":\"Unauthorized\"}");
            }
            return chain.proceed(request);
        }
        public String getName() { return "Auth"; }
    }

    // ==================== RATE LIMITING MIDDLEWARE ====================
    static class RateLimitMiddleware implements Middleware {
        private final Map<String, long[]> clientWindows = new ConcurrentHashMap<>();
        private final int maxRequests;
        private final long windowMs;

        RateLimitMiddleware(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }

        public HttpResponse handle(HttpRequest request, MiddlewareChain chain) {
            String key = request.clientIP;
            long now = System.currentTimeMillis();
            long[] window = clientWindows.computeIfAbsent(key, k -> new long[]{now, 0});
            synchronized (window) {
                if (now - window[0] > windowMs) {
                    window[0] = now;
                    window[1] = 0;
                }
                window[1]++;
                if (window[1] > maxRequests) {
                    HttpResponse resp = new HttpResponse(429, "{\"error\":\"Rate limit exceeded\"}");
                    resp.headers.put("Retry-After", String.valueOf(windowMs / 1000));
                    return resp;
                }
            }
            return chain.proceed(request);
        }
        public String getName() { return "RateLimit"; }
    }

    // ==================== LOGGING MIDDLEWARE ====================
    static class LoggingMiddleware implements Middleware {
        private final List<String> accessLog = new CopyOnWriteArrayList<>();

        public HttpResponse handle(HttpRequest request, MiddlewareChain chain) {
            long start = System.nanoTime();
            HttpResponse response = chain.proceed(request);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            String log = String.format("%s %s -> %d (%dms)", request.method, request.path, response.statusCode, elapsed);
            accessLog.add(log);
            return response;
        }
        public String getName() { return "Logging"; }
        public List<String> getAccessLog() { return accessLog; }
    }

    // ==================== CORS MIDDLEWARE ====================
    static class CORSMiddleware implements Middleware {
        private final Set<String> allowedOrigins;

        CORSMiddleware(String... origins) { this.allowedOrigins = new HashSet<>(Arrays.asList(origins)); }

        public HttpResponse handle(HttpRequest request, MiddlewareChain chain) {
            HttpResponse response = chain.proceed(request);
            String origin = request.headers.getOrDefault("Origin", "");
            if (allowedOrigins.contains("*") || allowedOrigins.contains(origin)) {
                response.headers.put("Access-Control-Allow-Origin", origin.isEmpty() ? "*" : origin);
                response.headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE");
            }
            return response;
        }
        public String getName() { return "CORS"; }
    }

    // ==================== ROUTER ====================
    static class Router {
        private final Map<String, Map<String, Function<HttpRequest, HttpResponse>>> routes = new HashMap<>();

        void addRoute(String method, String path, Function<HttpRequest, HttpResponse> handler) {
            routes.computeIfAbsent(method.toUpperCase(), k -> new HashMap<>()).put(path, handler);
        }

        Function<HttpRequest, HttpResponse> match(HttpRequest request) {
            Map<String, Function<HttpRequest, HttpResponse>> methodRoutes = routes.get(request.method);
            if (methodRoutes == null) return null;

            // Exact match
            Function<HttpRequest, HttpResponse> handler = methodRoutes.get(request.path);
            if (handler != null) return handler;

            // Prefix match (for versioned APIs like /v1/users -> /users)
            for (Map.Entry<String, Function<HttpRequest, HttpResponse>> e : methodRoutes.entrySet()) {
                if (request.path.endsWith(e.getKey()) || request.path.matches(e.getKey().replace("*", ".*"))) {
                    return e.getValue();
                }
            }
            return null;
        }
    }

    // ==================== API GATEWAY ====================
    static class Gateway {
        private final List<Middleware> middlewares = new ArrayList<>();
        private final Router router = new Router();
        private final AtomicLong requestCount = new AtomicLong();
        private final Map<Integer, AtomicLong> statusCounts = new ConcurrentHashMap<>();

        void addMiddleware(Middleware middleware) { middlewares.add(middleware); }

        void addRoute(String method, String path, Function<HttpRequest, HttpResponse> handler) {
            router.addRoute(method, path, handler);
        }

        HttpResponse handleRequest(HttpRequest request) {
            requestCount.incrementAndGet();
            Function<HttpRequest, HttpResponse> handler = router.match(request);
            if (handler == null) {
                handler = req -> new HttpResponse(404, "{\"error\":\"Not Found\"}");
            }

            MiddlewareChain chain = new MiddlewareChain(middlewares, handler);
            HttpResponse response = chain.proceed(request);
            statusCounts.computeIfAbsent(response.statusCode, k -> new AtomicLong()).incrementAndGet();
            return response;
        }

        long getRequestCount() { return requestCount.get(); }
        Map<Integer, Long> getStatusCounts() {
            Map<Integer, Long> result = new TreeMap<>();
            statusCounts.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) {
        System.out.println("=== API GATEWAY - System Design Demo ===\n");

        // Setup Gateway
        Gateway gateway = new Gateway();

        // Middlewares
        LoggingMiddleware logging = new LoggingMiddleware();
        AuthMiddleware auth = new AuthMiddleware();
        auth.addToken("token-123");
        auth.addToken("admin-token");

        RateLimitMiddleware rateLimit = new RateLimitMiddleware(5, 10000);
        CORSMiddleware cors = new CORSMiddleware("https://frontend.com", "http://localhost:3000");

        gateway.addMiddleware(logging);
        gateway.addMiddleware(cors);
        gateway.addMiddleware(rateLimit);
        gateway.addMiddleware(auth);

        // Routes (simulated backend services)
        gateway.addRoute("GET", "/health", req -> new HttpResponse(200, "{\"status\":\"UP\"}"));
        gateway.addRoute("GET", "/users", req -> new HttpResponse(200, "{\"users\":[\"alice\",\"bob\"]}"));
        gateway.addRoute("POST", "/users", req -> new HttpResponse(201, "{\"created\":\"charlie\"}"));
        gateway.addRoute("GET", "/orders", req -> new HttpResponse(200, "{\"orders\":[{\"id\":1}]}"));
        gateway.addRoute("GET", "/login", req -> new HttpResponse(200, "{\"token\":\"token-123\"}"));

        // 1. Public endpoint (no auth needed)
        System.out.println("--- 1. Public Endpoints ---");
        HttpResponse r1 = gateway.handleRequest(new HttpRequest("GET", "/health", "10.0.0.1"));
        System.out.printf("  GET /health -> %s%n", r1);
        HttpResponse r2 = gateway.handleRequest(new HttpRequest("GET", "/login", "10.0.0.1"));
        System.out.printf("  GET /login  -> %s%n", r2);

        // 2. Authenticated requests
        System.out.println("\n--- 2. Authentication ---");
        HttpResponse r3 = gateway.handleRequest(new HttpRequest("GET", "/users", "10.0.0.1"));
        System.out.printf("  GET /users (no token)    -> %s%n", r3);

        HttpRequest authedReq = new HttpRequest("GET", "/users", "10.0.0.1")
                .withHeader("Authorization", "Bearer token-123");
        HttpResponse r4 = gateway.handleRequest(authedReq);
        System.out.printf("  GET /users (valid token) -> %s%n", r4);

        HttpRequest badTokenReq = new HttpRequest("GET", "/users", "10.0.0.1")
                .withHeader("Authorization", "Bearer invalid");
        HttpResponse r5 = gateway.handleRequest(badTokenReq);
        System.out.printf("  GET /users (bad token)   -> %s%n", r5);

        // 3. Rate limiting
        System.out.println("\n--- 3. Rate Limiting (5 req per 10s) ---");
        for (int i = 0; i < 7; i++) {
            HttpRequest req = new HttpRequest("GET", "/health", "192.168.1.1");
            HttpResponse resp = gateway.handleRequest(req);
            System.out.printf("  Request %d from 192.168.1.1 -> HTTP %d%n", i + 1, resp.statusCode);
        }
        // Different IP is not rate-limited
        HttpResponse diffIP = gateway.handleRequest(new HttpRequest("GET", "/health", "192.168.1.2"));
        System.out.printf("  Request from 192.168.1.2 -> HTTP %d (different client)%n", diffIP.statusCode);

        // 4. 404 Not Found
        System.out.println("\n--- 4. Route Not Found ---");
        HttpResponse r6 = gateway.handleRequest(new HttpRequest("GET", "/unknown", "10.0.0.1"));
        System.out.printf("  GET /unknown -> %s%n", r6);

        // 5. CORS headers
        System.out.println("\n--- 5. CORS Headers ---");
        HttpRequest corsReq = new HttpRequest("GET", "/health", "10.0.0.1")
                .withHeader("Origin", "https://frontend.com");
        HttpResponse corsResp = gateway.handleRequest(corsReq);
        System.out.printf("  Origin: frontend.com -> CORS: %s%n",
                corsResp.headers.getOrDefault("Access-Control-Allow-Origin", "none"));

        // 6. Access logs
        System.out.println("\n--- 6. Access Log ---");
        for (String log : logging.getAccessLog()) {
            System.out.printf("  %s%n", log);
        }

        // 7. Metrics
        System.out.println("\n--- 7. Gateway Metrics ---");
        System.out.printf("  Total requests: %d%n", gateway.getRequestCount());
        System.out.println("  Status code distribution:");
        for (Map.Entry<Integer, Long> e : gateway.getStatusCounts().entrySet()) {
            System.out.printf("    HTTP %d: %d requests%n", e.getKey(), e.getValue());
        }

        System.out.println("\n--- Gateway Responsibilities ---");
        System.out.println("  Routing:         Path-based dispatch to backend services");
        System.out.println("  Authentication:  Validate tokens before forwarding");
        System.out.println("  Rate Limiting:   Protect backends from overload");
        System.out.println("  CORS:            Cross-origin resource sharing headers");
        System.out.println("  Logging:         Centralized access logging");
        System.out.println("  Transformation:  Request/response modification");
        System.out.println("  Load Balancing:  Distribute across service instances");
    }
}
