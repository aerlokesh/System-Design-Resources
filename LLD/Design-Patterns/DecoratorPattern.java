import java.util.*;

// ==================== DECORATOR PATTERN ====================
// Definition: Attaches additional responsibilities to an object dynamically.
//
// REAL System Design Interview Examples:
// 1. HTTP Middleware - Request/Response transformation
// 2. Data Stream Processing - Encryption, Compression, Buffering
// 3. API Response Enrichment - Add metadata, pagination, HATEOAS links
// 4. Security Layers - Authentication, Authorization, Encryption
// 5. Logging & Monitoring - Add tracing, metrics, audit logs
//
// Interview Use Cases:
// - Design API Gateway: Add authentication, rate limiting, logging to requests
// - Design Streaming Service: Add encryption, compression to video streams
// - Design REST API: Enrich responses with metadata
// - Design File Storage: Add encryption, compression to file uploads
// - Design Middleware Pipeline: Chain multiple request/response transformations

// ==================== EXAMPLE 1: HTTP MIDDLEWARE PIPELINE ====================
// Used in: Express.js, Spring Boot, Django middleware
// Interview Question: Design an API with middleware pipeline

interface HTTPHandler {
    Response handle(Request request);
}

class Request {
    private String method;
    private String path;
    private Map<String, String> headers;
    private String body;

    public Request(String method, String path) {
        this.method = method;
        this.path = path;
        this.headers = new HashMap<>();
        this.body = "";
    }

    public String getMethod() { return method; }
    public String getPath() { return path; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public void setHeader(String key, String value) { headers.put(key, value); }
    public void setBody(String body) { this.body = body; }
}

class Response {
    private int statusCode;
    private Map<String, String> headers;
    private String body;

    public Response(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = new HashMap<>();
    }

    public int getStatusCode() { return statusCode; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public void setHeader(String key, String value) { headers.put(key, value); }
    public void setBody(String body) { this.body = body; }
}

class CoreHandler implements HTTPHandler {
    @Override
    public Response handle(Request request) {
        System.out.println("🎯 Core Handler: Processing " + request.getMethod() + " " + request.getPath());
        return new Response(200, "{\"message\": \"Success\", \"data\": [...]}");
    }
}

abstract class HTTPMiddleware implements HTTPHandler {
    protected HTTPHandler wrapped;

    public HTTPMiddleware(HTTPHandler wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Response handle(Request request) {
        return wrapped.handle(request);
    }
}

class AuthenticationMiddleware extends HTTPMiddleware {
    public AuthenticationMiddleware(HTTPHandler wrapped) {
        super(wrapped);
    }

    @Override
    public Response handle(Request request) {
        System.out.println("🔐 Auth Middleware: Validating token");
        
        String authHeader = request.getHeaders().get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("   ❌ Authentication failed");
            return new Response(401, "{\"error\": \"Unauthorized\"}");
        }
        
        System.out.println("   ✅ Authentication passed");
        request.setHeader("X-User-Id", "user-123"); // Add user context
        return wrapped.handle(request);
    }
}

class LoggingMiddleware extends HTTPMiddleware {
    public LoggingMiddleware(HTTPHandler wrapped) {
        super(wrapped);
    }

    @Override
    public Response handle(Request request) {
        long startTime = System.currentTimeMillis();
        System.out.println("📝 Logging: " + request.getMethod() + " " + request.getPath());
        
        Response response = wrapped.handle(request);
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("📝 Logged: %d status, %dms duration%n", response.getStatusCode(), duration);
        
        return response;
    }
}

class CORSMiddleware extends HTTPMiddleware {
    public CORSMiddleware(HTTPHandler wrapped) {
        super(wrapped);
    }

    @Override
    public Response handle(Request request) {
        System.out.println("🌐 CORS Middleware: Adding CORS headers");
        
        Response response = wrapped.handle(request);
        
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        return response;
    }
}

class CompressionMiddleware extends HTTPMiddleware {
    public CompressionMiddleware(HTTPHandler wrapped) {
        super(wrapped);
    }

    @Override
    public Response handle(Request request) {
        Response response = wrapped.handle(request);
        
        System.out.println("📦 Compression: Compressing response body");
        String compressed = "[GZIP compressed: " + response.getBody().length() + " bytes → " + 
            (response.getBody().length() / 2) + " bytes]";
        response.setBody(compressed);
        response.setHeader("Content-Encoding", "gzip");
        
        return response;
    }
}

// ==================== EXAMPLE 2: DATA STREAM PROCESSING ====================
// Used in: Video streaming, File upload, Network protocols
// Interview Question: Design a file upload/streaming service

interface DataStream {
    byte[] read();
    void write(byte[] data);
    void close();
}

class FileDataStream implements DataStream {
    private String filename;

    public FileDataStream(String filename) {
        this.filename = filename;
        System.out.println("📄 FileStream: Opened " + filename);
    }

    @Override
    public byte[] read() {
        System.out.println("📖 FileStream: Reading raw data");
        return new byte[1024];
    }

    @Override
    public void write(byte[] data) {
        System.out.println("✏️ FileStream: Writing " + data.length + " bytes");
    }

    @Override
    public void close() {
        System.out.println("🔒 FileStream: Closed " + filename);
    }
}

abstract class DataStreamDecorator implements DataStream {
    protected DataStream wrapped;

    public DataStreamDecorator(DataStream wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public byte[] read() {
        return wrapped.read();
    }

    @Override
    public void write(byte[] data) {
        wrapped.write(data);
    }

    @Override
    public void close() {
        wrapped.close();
    }
}

class EncryptionStreamDecorator extends DataStreamDecorator {
    public EncryptionStreamDecorator(DataStream wrapped) {
        super(wrapped);
        System.out.println("🔐 Added: AES-256 Encryption layer");
    }

    @Override
    public byte[] read() {
        byte[] data = wrapped.read();
        System.out.println("🔓 Decrypting data (" + data.length + " bytes)");
        return data; // Simulated decryption
    }

    @Override
    public void write(byte[] data) {
        System.out.println("🔐 Encrypting data (" + data.length + " bytes)");
        wrapped.write(data); // Simulated encryption
    }
}

class CompressionStreamDecorator extends DataStreamDecorator {
    public CompressionStreamDecorator(DataStream wrapped) {
        super(wrapped);
        System.out.println("📦 Added: Gzip Compression layer");
    }

    @Override
    public byte[] read() {
        byte[] data = wrapped.read();
        System.out.println("📂 Decompressing data (" + data.length + " bytes → " + (data.length * 2) + " bytes)");
        return new byte[data.length * 2]; // Simulated decompression
    }

    @Override
    public void write(byte[] data) {
        System.out.println("📦 Compressing data (" + data.length + " bytes → " + (data.length / 2) + " bytes)");
        wrapped.write(new byte[data.length / 2]); // Simulated compression
    }
}

class BufferedStreamDecorator extends DataStreamDecorator {
    private static final int BUFFER_SIZE = 8192;

    public BufferedStreamDecorator(DataStream wrapped) {
        super(wrapped);
        System.out.println("💾 Added: Buffering layer (buffer size: " + BUFFER_SIZE + " bytes)");
    }

    @Override
    public byte[] read() {
        System.out.println("💾 Buffered read (reducing I/O operations)");
        return wrapped.read();
    }

    @Override
    public void write(byte[] data) {
        System.out.println("💾 Buffered write (batching " + data.length + " bytes)");
        wrapped.write(data);
    }
}

// ==================== EXAMPLE 3: API RESPONSE ENRICHMENT ====================
// Used in: REST APIs, GraphQL, HATEOAS
// Interview Question: Design a REST API with rich responses

interface APIResponse {
    String toJSON();
}

class BasicAPIResponse implements APIResponse {
    private Map<String, Object> data;

    public BasicAPIResponse(Map<String, Object> data) {
        this.data = data;
    }

    @Override
    public String toJSON() {
        return "{\"data\": " + data + "}";
    }

    public Map<String, Object> getData() {
        return data;
    }
}

abstract class APIResponseDecorator implements APIResponse {
    protected APIResponse wrapped;

    public APIResponseDecorator(APIResponse wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public String toJSON() {
        return wrapped.toJSON();
    }
}

class PaginationDecorator extends APIResponseDecorator {
    private int page;
    private int size;
    private int total;

    public PaginationDecorator(APIResponse wrapped, int page, int size, int total) {
        super(wrapped);
        this.page = page;
        this.size = size;
        this.total = total;
    }

    @Override
    public String toJSON() {
        String base = wrapped.toJSON();
        String pagination = String.format(", \"pagination\": {\"page\": %d, \"size\": %d, \"total\": %d, \"pages\": %d}",
            page, size, total, (int) Math.ceil((double) total / size));
        return base.substring(0, base.length() - 1) + pagination + "}";
    }
}

class MetadataDecorator extends APIResponseDecorator {
    public MetadataDecorator(APIResponse wrapped) {
        super(wrapped);
    }

    @Override
    public String toJSON() {
        String base = wrapped.toJSON();
        String timestamp = java.time.LocalDateTime.now().toString();
        String metadata = String.format(", \"metadata\": {\"timestamp\": \"%s\", \"version\": \"v1\", \"server\": \"api-01\"}",
            timestamp);
        return base.substring(0, base.length() - 1) + metadata + "}";
    }
}

class HATEOASDecorator extends APIResponseDecorator {
    private String resourceId;

    public HATEOASDecorator(APIResponse wrapped, String resourceId) {
        super(wrapped);
        this.resourceId = resourceId;
    }

    @Override
    public String toJSON() {
        String base = wrapped.toJSON();
        String links = String.format(", \"_links\": {\"self\": \"/api/resource/%s\", \"update\": \"/api/resource/%s\", \"delete\": \"/api/resource/%s\"}",
            resourceId, resourceId, resourceId);
        return base.substring(0, base.length() - 1) + links + "}";
    }
}

// ==================== DEMO ====================

public class DecoratorPattern {
    public static void main(String[] args) {
        System.out.println("========== DECORATOR PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: HTTP MIDDLEWARE PIPELINE =====
        System.out.println("===== EXAMPLE 1: HTTP MIDDLEWARE (Express.js/Spring Boot) =====\n");
        
        Request request1 = new Request("GET", "/api/users");
        request1.setHeader("Authorization", "Bearer token123");
        
        HTTPHandler handler = new CoreHandler();
        HTTPHandler withAuth = new AuthenticationMiddleware(handler);
        HTTPHandler withLogging = new LoggingMiddleware(withAuth);
        HTTPHandler withCORS = new CORSMiddleware(withLogging);
        HTTPHandler withCompression = new CompressionMiddleware(withCORS);
        
        System.out.println("Pipeline: Compression → CORS → Logging → Auth → Core\n");
        Response response = withCompression.handle(request1);
        System.out.println("\n📥 Final Response: Status=" + response.getStatusCode());
        System.out.println("   Headers: " + response.getHeaders());
        System.out.println("   Body: " + response.getBody());

        // ===== EXAMPLE 2: DATA STREAM PROCESSING =====
        System.out.println("\n\n===== EXAMPLE 2: DATA STREAM PROCESSING (File Upload/Streaming) =====\n");
        
        System.out.println("Scenario 1: Encrypted + Compressed + Buffered file upload\n");
        DataStream fileStream = new FileDataStream("upload.dat");
        DataStream buffered = new BufferedStreamDecorator(fileStream);
        DataStream compressed = new CompressionStreamDecorator(buffered);
        DataStream encrypted = new EncryptionStreamDecorator(compressed);
        
        System.out.println("\nWriting data:");
        encrypted.write(new byte[4096]);
        
        System.out.println("\nReading data:");
        encrypted.read();
        
        System.out.println();
        encrypted.close();

        // ===== EXAMPLE 3: API RESPONSE ENRICHMENT =====
        System.out.println("\n\n===== EXAMPLE 3: API RESPONSE ENRICHMENT (REST API) =====\n");
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", 123);
        userData.put("name", "Alice");
        userData.put("email", "alice@example.com");
        
        APIResponse basic = new BasicAPIResponse(userData);
        System.out.println("Basic Response:");
        System.out.println(basic.toJSON());
        
        System.out.println("\nWith Pagination:");
        APIResponse withPagination = new PaginationDecorator(basic, 1, 20, 100);
        System.out.println(withPagination.toJSON());
        
        System.out.println("\nWith Metadata:");
        APIResponse withMetadata = new MetadataDecorator(withPagination);
        System.out.println(withMetadata.toJSON());
        
        System.out.println("\nWith HATEOAS Links:");
        APIResponse withHATEOAS = new HATEOASDecorator(withMetadata, "123");
        System.out.println(withHATEOAS.toJSON());

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• HTTP middleware pipelines (Express.js, Spring Boot)");
        System.out.println("• Stream processing (encryption, compression, buffering)");
        System.out.println("• REST API response enrichment");
        System.out.println("• Security layers (auth, encryption)");
        System.out.println("• Observability (logging, tracing, metrics)");
    }
}
