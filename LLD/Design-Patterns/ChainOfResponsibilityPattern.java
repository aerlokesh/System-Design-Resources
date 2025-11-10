import java.util.*;
import java.time.*;

// ==================== CHAIN OF RESPONSIBILITY PATTERN ====================
// Definition: Passes requests along a chain of handlers. Each handler decides whether
// to process the request or pass it to the next handler.
//
// REAL System Design Interview Examples:
// 1. API Gateway Request Processing - Auth, rate limiting, logging
// 2. Middleware Pipeline - Express.js, Spring filters, CORS
// 3. Exception Handling - Try multiple fallback strategies
// 4. Request Validation - Schema, business rules, permissions
// 5. Load Balancer Health Checks - Multiple validation stages
//
// Interview Use Cases:
// - Design API Gateway: Auth → Rate Limit → Request Validation → Route
// - Design Payment System: Fraud detection → Balance check → Payment processing
// - Design CDN: Origin check → Cache check → Edge server
// - Design Email System: Spam filter → Virus scan → Delivery
// - Design Request Router: Region check → Service discovery → Load balance

// ==================== EXAMPLE 1: API GATEWAY REQUEST PIPELINE ====================
// Used in: Kong, AWS API Gateway, Nginx, Envoy
// Interview Question: Design an API Gateway

class APIRequest {
    private String requestId;
    private String endpoint;
    private String method;
    private Map<String, String> headers;
    private String clientIp;
    private String body;
    private long timestamp;

    public APIRequest(String endpoint, String method, Map<String, String> headers, String clientIp) {
        this.requestId = UUID.randomUUID().toString();
        this.endpoint = endpoint;
        this.method = method;
        this.headers = headers;
        this.clientIp = clientIp;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getRequestId() { return requestId; }
    public String getEndpoint() { return endpoint; }
    public String getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public String getClientIp() { return clientIp; }
    public String getAuthToken() { return headers.get("Authorization"); }
    
    @Override
    public String toString() {
        return String.format("[%s] %s %s from %s", requestId.substring(0, 8), method, endpoint, clientIp);
    }
}

interface RequestHandler {
    void setNext(RequestHandler next);
    boolean handle(APIRequest request);
}

abstract class BaseRequestHandler implements RequestHandler {
    private RequestHandler next;

    @Override
    public void setNext(RequestHandler next) {
        this.next = next;
    }

    protected boolean passToNext(APIRequest request) {
        if (next == null) {
            return true; // All handlers passed
        }
        return next.handle(request);
    }
}

// Handler 1: Authentication
class AuthenticationHandler extends BaseRequestHandler {
    private Set<String> validTokens = new HashSet<>(Arrays.asList(
        "token-admin-123", "token-user-456", "token-service-789"
    ));

    @Override
    public boolean handle(APIRequest request) {
        System.out.println("🔐 Authentication: Checking " + request);
        
        String token = request.getAuthToken();
        if (token == null || !validTokens.contains(token)) {
            System.out.println("   ❌ REJECTED: Invalid or missing auth token");
            return false;
        }
        
        System.out.println("   ✅ PASSED: Valid authentication");
        return passToNext(request);
    }
}

// Handler 2: Rate Limiting
class RateLimitHandler extends BaseRequestHandler {
    private Map<String, Integer> requestCounts = new HashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    @Override
    public boolean handle(APIRequest request) {
        System.out.println("⏱️  Rate Limiting: Checking " + request);
        
        String clientIp = request.getClientIp();
        int count = requestCounts.getOrDefault(clientIp, 0);
        
        if (count >= MAX_REQUESTS_PER_MINUTE) {
            System.out.println("   ❌ REJECTED: Rate limit exceeded (" + count + " requests)");
            return false;
        }
        
        requestCounts.put(clientIp, count + 1);
        System.out.println("   ✅ PASSED: Rate limit OK (" + (count + 1) + "/" + MAX_REQUESTS_PER_MINUTE + ")");
        return passToNext(request);
    }
}

// Handler 3: Request Validation
class RequestValidationHandler extends BaseRequestHandler {
    private Set<String> allowedEndpoints = new HashSet<>(Arrays.asList(
        "/api/users", "/api/orders", "/api/products", "/api/payments"
    ));

    @Override
    public boolean handle(APIRequest request) {
        System.out.println("✓ Validation: Checking " + request);
        
        if (!allowedEndpoints.contains(request.getEndpoint())) {
            System.out.println("   ❌ REJECTED: Endpoint not found");
            return false;
        }
        
        System.out.println("   ✅ PASSED: Valid endpoint");
        return passToNext(request);
    }
}

// Handler 4: Logging
class LoggingHandler extends BaseRequestHandler {
    @Override
    public boolean handle(APIRequest request) {
        System.out.println("📝 Logging: Recording " + request);
        System.out.println("   ✅ PASSED: Request logged");
        return passToNext(request);
    }
}

// ==================== EXAMPLE 2: PAYMENT PROCESSING PIPELINE ====================
// Used in: Stripe, PayPal, Payment gateways
// Interview Question: Design a payment processing system

class PaymentRequest {
    private String orderId;
    private String userId;
    private double amount;
    private String currency;
    private String paymentMethod;

    public PaymentRequest(String orderId, String userId, double amount, String currency, String paymentMethod) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethod() { return paymentMethod; }
    
    @Override
    public String toString() {
        return String.format("Order %s: $%.2f %s via %s", orderId, amount, currency, paymentMethod);
    }
}

interface PaymentHandler {
    void setNext(PaymentHandler next);
    boolean process(PaymentRequest payment);
}

abstract class BasePaymentHandler implements PaymentHandler {
    private PaymentHandler next;

    @Override
    public void setNext(PaymentHandler next) {
        this.next = next;
    }

    protected boolean passToNext(PaymentRequest payment) {
        if (next == null) {
            return true;
        }
        return next.process(payment);
    }
}

// Handler 1: Fraud Detection
class FraudDetectionHandler extends BasePaymentHandler {
    private Set<String> blacklistedUsers = new HashSet<>(Arrays.asList("user-999"));
    private static final double SUSPICIOUS_AMOUNT = 10000.0;

    @Override
    public boolean process(PaymentRequest payment) {
        System.out.println("🕵️ Fraud Detection: " + payment);
        
        if (blacklistedUsers.contains(payment.getUserId())) {
            System.out.println("   ❌ BLOCKED: User is blacklisted");
            return false;
        }
        
        if (payment.getAmount() > SUSPICIOUS_AMOUNT) {
            System.out.println("   ⚠️  FLAGGED: High amount transaction - additional verification needed");
            // In real system, might require 2FA or manual review
        }
        
        System.out.println("   ✅ PASSED: Fraud check OK");
        return passToNext(payment);
    }
}

// Handler 2: Balance Check
class BalanceCheckHandler extends BasePaymentHandler {
    private Map<String, Double> userBalances = new HashMap<>();

    public BalanceCheckHandler() {
        userBalances.put("user-123", 5000.0);
        userBalances.put("user-456", 150.0);
        userBalances.put("user-789", 10000.0);
    }

    @Override
    public boolean process(PaymentRequest payment) {
        System.out.println("💰 Balance Check: " + payment);
        
        double balance = userBalances.getOrDefault(payment.getUserId(), 0.0);
        
        if (balance < payment.getAmount()) {
            System.out.printf("   ❌ REJECTED: Insufficient funds ($%.2f < $%.2f)%n", balance, payment.getAmount());
            return false;
        }
        
        System.out.printf("   ✅ PASSED: Sufficient balance ($%.2f)%n", balance);
        return passToNext(payment);
    }
}

// Handler 3: Payment Gateway
class PaymentGatewayHandler extends BasePaymentHandler {
    @Override
    public boolean process(PaymentRequest payment) {
        System.out.println("💳 Payment Gateway: Processing " + payment);
        System.out.println("   ✅ COMPLETED: Payment successful");
        return passToNext(payment);
    }
}

// ==================== EXAMPLE 3: EXCEPTION HANDLING WITH FALLBACKS ====================
// Used in: Service mesh, Circuit breaker, Retry logic
// Interview Question: Design fault-tolerant service communication

class ServiceRequest {
    private String requestId;
    private String data;
    private int attemptCount;

    public ServiceRequest(String data) {
        this.requestId = UUID.randomUUID().toString();
        this.data = data;
        this.attemptCount = 0;
    }

    public String getRequestId() { return requestId; }
    public String getData() { return data; }
    public int getAttemptCount() { return attemptCount; }
    public void incrementAttempt() { attemptCount++; }
}

interface ServiceHandler {
    void setNext(ServiceHandler next);
    String execute(ServiceRequest request);
}

abstract class BaseServiceHandler implements ServiceHandler {
    private ServiceHandler next;
    protected String serviceName;

    public BaseServiceHandler(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void setNext(ServiceHandler next) {
        this.next = next;
    }

    protected String tryNext(ServiceRequest request) {
        if (next == null) {
            return null;
        }
        return next.execute(request);
    }
}

class PrimaryServiceHandler extends BaseServiceHandler {
    private boolean isHealthy = false; // Simulate unhealthy

    public PrimaryServiceHandler() {
        super("PrimaryService");
    }

    @Override
    public String execute(ServiceRequest request) {
        request.incrementAttempt();
        System.out.println("🎯 Trying " + serviceName + " (Attempt " + request.getAttemptCount() + ")");
        
        if (!isHealthy) {
            System.out.println("   ❌ FAILED: Primary service unavailable");
            return tryNext(request);
        }
        
        System.out.println("   ✅ SUCCESS: Processed by primary service");
        return "Response from " + serviceName;
    }
}

class SecondaryServiceHandler extends BaseServiceHandler {
    private boolean isHealthy = true; // Available

    public SecondaryServiceHandler() {
        super("SecondaryService");
    }

    @Override
    public String execute(ServiceRequest request) {
        request.incrementAttempt();
        System.out.println("🔄 Trying " + serviceName + " (Attempt " + request.getAttemptCount() + ")");
        
        if (!isHealthy) {
            System.out.println("   ❌ FAILED: Secondary service unavailable");
            return tryNext(request);
        }
        
        System.out.println("   ✅ SUCCESS: Processed by secondary service");
        return "Response from " + serviceName;
    }
}

class CacheServiceHandler extends BaseServiceHandler {
    public CacheServiceHandler() {
        super("CacheService");
    }

    @Override
    public String execute(ServiceRequest request) {
        request.incrementAttempt();
        System.out.println("💾 Trying " + serviceName + " (Attempt " + request.getAttemptCount() + ")");
        System.out.println("   ✅ SUCCESS: Served from cache (stale data)");
        return "Cached response from " + serviceName;
    }
}

// ==================== EXAMPLE 4: CDN REQUEST FLOW ====================
// Used in: CloudFlare, Akamai, AWS CloudFront
// Interview Question: Design a Content Delivery Network

class CDNRequest {
    private String url;
    private String clientRegion;

    public CDNRequest(String url, String clientRegion) {
        this.url = url;
        this.clientRegion = clientRegion;
    }

    public String getUrl() { return url; }
    public String getClientRegion() { return clientRegion; }
}

interface CDNHandler {
    void setNext(CDNHandler next);
    String serve(CDNRequest request);
}

abstract class BaseCDNHandler implements CDNHandler {
    private CDNHandler next;
    protected String handlerName;

    public BaseCDNHandler(String handlerName) {
        this.handlerName = handlerName;
    }

    @Override
    public void setNext(CDNHandler next) {
        this.next = next;
    }

    protected String passToNext(CDNRequest request) {
        if (next == null) {
            return null;
        }
        return next.serve(request);
    }
}

class EdgeCacheHandler extends BaseCDNHandler {
    private Set<String> cachedUrls = new HashSet<>(Arrays.asList(
        "/static/logo.png", "/static/style.css"
    ));

    public EdgeCacheHandler() {
        super("Edge Cache");
    }

    @Override
    public String serve(CDNRequest request) {
        System.out.println("🌐 " + handlerName + ": Checking for " + request.getUrl());
        
        if (cachedUrls.contains(request.getUrl())) {
            System.out.println("   ✅ HIT: Served from edge cache (0ms latency)");
            return "Content from Edge Cache";
        }
        
        System.out.println("   ❌ MISS: Not in edge cache");
        return passToNext(request);
    }
}

class RegionalCacheHandler extends BaseCDNHandler {
    private Set<String> cachedUrls = new HashSet<>(Arrays.asList(
        "/api/products"
    ));

    public RegionalCacheHandler() {
        super("Regional Cache");
    }

    @Override
    public String serve(CDNRequest request) {
        System.out.println("🗺️ " + handlerName + ": Checking for " + request.getUrl());
        
        if (cachedUrls.contains(request.getUrl())) {
            System.out.println("   ✅ HIT: Served from regional cache (20ms latency)");
            return "Content from Regional Cache";
        }
        
        System.out.println("   ❌ MISS: Not in regional cache");
        return passToNext(request);
    }
}

class OriginServerHandler extends BaseCDNHandler {
    public OriginServerHandler() {
        super("Origin Server");
    }

    @Override
    public String serve(CDNRequest request) {
        System.out.println("🏢 " + handlerName + ": Fetching " + request.getUrl());
        System.out.println("   ✅ SUCCESS: Served from origin (200ms latency)");
        return "Content from Origin Server";
    }
}

// ==================== EXAMPLE 5: EMAIL SPAM FILTER ====================
// Used in: Gmail, Outlook, Email services
// Interview Question: Design an email system with spam detection

class Email {
    private String from;
    private String subject;
    private String body;
    private int attachmentCount;
    private long attachmentSize;

    public Email(String from, String subject, String body, int attachmentCount, long attachmentSize) {
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.attachmentCount = attachmentCount;
        this.attachmentSize = attachmentSize;
    }

    public String getFrom() { return from; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public int getAttachmentCount() { return attachmentCount; }
    public long getAttachmentSize() { return attachmentSize; }
    
    public String getDomain() {
        return from.substring(from.indexOf('@') + 1);
    }
}

interface SpamFilter {
    void setNext(SpamFilter next);
    boolean filter(Email email);
}

abstract class BaseSpamFilter implements SpamFilter {
    private SpamFilter next;

    @Override
    public void setNext(SpamFilter next) {
        this.next = next;
    }

    protected boolean checkNext(Email email) {
        if (next == null) {
            return false; // Passed all filters
        }
        return next.filter(email);
    }
}

class DomainReputationFilter extends BaseSpamFilter {
    private Set<String> blacklistedDomains = new HashSet<>(Arrays.asList(
        "spam.com", "phishing.net", "scam.org"
    ));

    @Override
    public boolean filter(Email email) {
        System.out.println("🔍 Domain Reputation: " + email.getFrom());
        
        if (blacklistedDomains.contains(email.getDomain())) {
            System.out.println("   ❌ SPAM: Blacklisted domain");
            return true;
        }
        
        System.out.println("   ✅ PASS: Domain reputation OK");
        return checkNext(email);
    }
}

class ContentAnalysisFilter extends BaseSpamFilter {
    private Set<String> spamKeywords = new HashSet<>(Arrays.asList(
        "lottery", "winner", "free money", "click here", "congratulations"
    ));

    @Override
    public boolean filter(Email email) {
        System.out.println("📄 Content Analysis: " + email.getSubject());
        
        String content = (email.getSubject() + " " + email.getBody()).toLowerCase();
        for (String keyword : spamKeywords) {
            if (content.contains(keyword)) {
                System.out.println("   ❌ SPAM: Suspicious keyword detected");
                return true;
            }
        }
        
        System.out.println("   ✅ PASS: Content looks legitimate");
        return checkNext(email);
    }
}

class AttachmentScanFilter extends BaseSpamFilter {
    private static final int MAX_ATTACHMENTS = 10;
    private static final long MAX_SIZE_MB = 25;

    @Override
    public boolean filter(Email email) {
        System.out.println("📎 Attachment Scan: " + email.getFrom());
        
        if (email.getAttachmentCount() > MAX_ATTACHMENTS) {
            System.out.println("   ❌ SPAM: Too many attachments");
            return true;
        }
        
        long sizeMB = email.getAttachmentSize() / (1024 * 1024);
        if (sizeMB > MAX_SIZE_MB) {
            System.out.println("   ❌ SPAM: Attachments too large");
            return true;
        }
        
        System.out.println("   ✅ PASS: Attachments OK");
        return checkNext(email);
    }
}

// ==================== DEMO ====================

public class ChainOfResponsibilityPattern {
    public static void main(String[] args) {
        System.out.println("========== CHAIN OF RESPONSIBILITY: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: API GATEWAY REQUEST PIPELINE =====
        System.out.println("===== EXAMPLE 1: API GATEWAY (Kong/AWS API Gateway) =====\n");
        
        AuthenticationHandler auth = new AuthenticationHandler();
        RateLimitHandler rateLimit = new RateLimitHandler();
        RequestValidationHandler validation = new RequestValidationHandler();
        LoggingHandler logging = new LoggingHandler();
        
        auth.setNext(rateLimit);
        rateLimit.setNext(validation);
        validation.setNext(logging);
        
        Map<String, String> headers1 = new HashMap<>();
        headers1.put("Authorization", "token-admin-123");
        APIRequest request1 = new APIRequest("/api/users", "GET", headers1, "192.168.1.1");
        auth.handle(request1);
        
        System.out.println();
        
        Map<String, String> headers2 = new HashMap<>();
        headers2.put("Authorization", "invalid-token");
        APIRequest request2 = new APIRequest("/api/orders", "POST", headers2, "192.168.1.2");
        auth.handle(request2);
        
        System.out.println();
        
        Map<String, String> headers3 = new HashMap<>();
        headers3.put("Authorization", "token-user-456");
        APIRequest request3 = new APIRequest("/api/invalid", "GET", headers3, "192.168.1.3");
        auth.handle(request3);

        // ===== EXAMPLE 2: PAYMENT PROCESSING PIPELINE =====
        System.out.println("\n\n===== EXAMPLE 2: PAYMENT PROCESSING (Stripe/PayPal) =====\n");
        
        FraudDetectionHandler fraud = new FraudDetectionHandler();
        BalanceCheckHandler balance = new BalanceCheckHandler();
        PaymentGatewayHandler gateway = new PaymentGatewayHandler();
        
        fraud.setNext(balance);
        balance.setNext(gateway);
        
        PaymentRequest payment1 = new PaymentRequest("ORD-001", "user-123", 99.99, "USD", "credit_card");
        fraud.process(payment1);
        
        System.out.println();
        
        PaymentRequest payment2 = new PaymentRequest("ORD-002", "user-456", 500.00, "USD", "credit_card");
        fraud.process(payment2);
        
        System.out.println();
        
        PaymentRequest payment3 = new PaymentRequest("ORD-003", "user-999", 50.00, "USD", "credit_card");
        fraud.process(payment3);

        // ===== EXAMPLE 3: SERVICE FALLBACK CHAIN =====
        System.out.println("\n\n===== EXAMPLE 3: SERVICE FALLBACK (Circuit Breaker) =====\n");
        
        PrimaryServiceHandler primary = new PrimaryServiceHandler();
        SecondaryServiceHandler secondary = new SecondaryServiceHandler();
        CacheServiceHandler cache = new CacheServiceHandler();
        
        primary.setNext(secondary);
        secondary.setNext(cache);
        
        ServiceRequest serviceReq = new ServiceRequest("Get user data");
        String response = primary.execute(serviceReq);
        System.out.println("📥 Final Response: " + response);

        // ===== EXAMPLE 4: CDN REQUEST FLOW =====
        System.out.println("\n\n===== EXAMPLE 4: CDN REQUEST FLOW (CloudFlare/Akamai) =====\n");
        
        EdgeCacheHandler edge = new EdgeCacheHandler();
        RegionalCacheHandler regional = new RegionalCacheHandler();
        OriginServerHandler origin = new OriginServerHandler();
        
        edge.setNext(regional);
        regional.setNext(origin);
        
        CDNRequest cdn1 = new CDNRequest("/static/logo.png", "us-west");
        edge.serve(cdn1);
        
        System.out.println();
        
        CDNRequest cdn2 = new CDNRequest("/api/products", "us-east");
        edge.serve(cdn2);
        
        System.out.println();
        
        CDNRequest cdn3 = new CDNRequest("/dynamic/page", "europe");
        edge.serve(cdn3);

        // ===== EXAMPLE 5: EMAIL SPAM FILTER =====
        System.out.println("\n\n===== EXAMPLE 5: EMAIL SPAM FILTER (Gmail/Outlook) =====\n");
        
        DomainReputationFilter domain = new DomainReputationFilter();
        ContentAnalysisFilter content = new ContentAnalysisFilter();
        AttachmentScanFilter attachment = new AttachmentScanFilter();
        
        domain.setNext(content);
        content.setNext(attachment);
        
        Email email1 = new Email("john@company.com", "Meeting Tomorrow", 
            "Let's discuss the project", 1, 1024 * 500);
        boolean isSpam1 = domain.filter(email1);
        System.out.println("Result: " + (isSpam1 ? "🚫 SPAM" : "✅ INBOX") + "\n");
        
        Email email2 = new Email("scammer@spam.com", "You won!", 
            "Congratulations! You won the lottery!", 0, 0);
        boolean isSpam2 = domain.filter(email2);
        System.out.println("Result: " + (isSpam2 ? "🚫 SPAM" : "✅ INBOX") + "\n");
        
        Email email3 = new Email("vendor@legit.com", "Invoice", 
            "Attached invoice", 15, 1024 * 1024 * 30);
        boolean isSpam3 = domain.filter(email3);
        System.out.println("Result: " + (isSpam3 ? "🚫 SPAM" : "✅ INBOX"));

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• API Gateways (Kong, AWS API Gateway, Nginx)");
        System.out.println("• Payment processing (Stripe, PayPal, Square)");
        System.out.println("• Service mesh fallback strategies");
        System.out.println("• CDN request routing (CloudFlare, Akamai)");
        System.out.println("• Email spam filtering (Gmail, Outlook)");
    }
}
