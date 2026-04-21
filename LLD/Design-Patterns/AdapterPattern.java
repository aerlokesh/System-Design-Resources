import java.util.*;

// ==================== ADAPTER PATTERN ====================
// Definition: Converts the interface of a class into another interface clients expect.
// Lets classes work together that couldn't otherwise because of incompatible interfaces.
//
// REAL System Design Interview Examples:
// 1. Third-Party Payment Gateway Integration - Stripe, PayPal, Square unified interface
// 2. Database Driver Abstraction - SQL, NoSQL behind unified interface
// 3. Cloud Provider Abstraction - AWS, GCP, Azure behind common interface
// 4. Legacy System Integration - Wrapping old APIs with new interface
// 5. Message Format Conversion - XML ↔ JSON ↔ Protobuf adapters
//
// Interview Use Cases:
// - Design Payment System: Unify Stripe, PayPal, Braintree APIs
// - Design Multi-Cloud: Abstract AWS S3/GCP GCS/Azure Blob
// - Design Data Pipeline: Different source/sink formats
// - Design API Gateway: Adapt backend protocols (REST/gRPC/GraphQL)
// - Design Notification System: Adapt email/SMS/push providers

// ==================== EXAMPLE 1: PAYMENT GATEWAY ADAPTER ====================
// Used in: E-commerce platforms, Payment processing
// Interview Question: Design a payment processing system

interface UnifiedPaymentGateway {
    PaymentResult charge(String customerId, double amount, String currency);
    PaymentResult refund(String transactionId, double amount);
    PaymentStatus getStatus(String transactionId);
}

class PaymentResult {
    private String transactionId;
    private boolean success;
    private String message;
    private double amount;

    public PaymentResult(String transactionId, boolean success, String message, double amount) {
        this.transactionId = transactionId;
        this.success = success;
        this.message = message;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return String.format("[txn=%s, success=%s, amount=%.2f, msg=%s]",
            transactionId, success, amount, message);
    }
}

enum PaymentStatus { PENDING, COMPLETED, FAILED, REFUNDED }

// --- Stripe's legacy API (different interface) ---
class StripeAPI {
    public Map<String, Object> createCharge(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", "ch_stripe_" + UUID.randomUUID().toString().substring(0, 8));
        result.put("status", "succeeded");
        result.put("amount_cents", (int)((double)params.get("amount") * 100));
        System.out.println("   [Stripe Internal] createCharge called with params: " + params);
        return result;
    }

    public Map<String, Object> createRefund(String chargeId, int amountCents) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", "re_stripe_" + UUID.randomUUID().toString().substring(0, 8));
        result.put("status", "succeeded");
        result.put("charge", chargeId);
        System.out.println("   [Stripe Internal] createRefund for charge: " + chargeId);
        return result;
    }

    public String retrieveChargeStatus(String chargeId) {
        return "succeeded";
    }
}

// --- PayPal's legacy API (completely different interface) ---
class PayPalAPI {
    public String executePayment(String payerId, String paymentAmount, String currencyCode) {
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("   [PayPal Internal] executePayment: payer=" + payerId + ", amount=" + paymentAmount);
        return paymentId;
    }

    public boolean refundPayment(String paymentId, String amount) {
        System.out.println("   [PayPal Internal] refundPayment: " + paymentId);
        return true;
    }

    public String checkPaymentState(String paymentId) {
        return "approved";
    }
}

// --- Square's legacy API (yet another interface) ---
class SquareAPI {
    public Map<String, String> processTransaction(String locationId, String nonce, int amountMoney) {
        Map<String, String> result = new HashMap<>();
        result.put("transaction_id", "sq_txn_" + UUID.randomUUID().toString().substring(0, 8));
        result.put("status", "COMPLETED");
        System.out.println("   [Square Internal] processTransaction: location=" + locationId + ", amount=" + amountMoney);
        return result;
    }

    public boolean cancelTransaction(String transactionId) {
        System.out.println("   [Square Internal] cancelTransaction: " + transactionId);
        return true;
    }
}

// Adapters
class StripeAdapter implements UnifiedPaymentGateway {
    private StripeAPI stripeAPI;

    public StripeAdapter(StripeAPI stripeAPI) {
        this.stripeAPI = stripeAPI;
    }

    @Override
    public PaymentResult charge(String customerId, double amount, String currency) {
        System.out.println("💳 Stripe Adapter: Converting to Stripe format...");
        Map<String, Object> params = new HashMap<>();
        params.put("customer", customerId);
        params.put("amount", amount);
        params.put("currency", currency);

        Map<String, Object> result = stripeAPI.createCharge(params);
        return new PaymentResult(
            (String) result.get("id"),
            "succeeded".equals(result.get("status")),
            "Stripe charge processed",
            amount
        );
    }

    @Override
    public PaymentResult refund(String transactionId, double amount) {
        Map<String, Object> result = stripeAPI.createRefund(transactionId, (int)(amount * 100));
        return new PaymentResult((String) result.get("id"), true, "Stripe refund processed", amount);
    }

    @Override
    public PaymentStatus getStatus(String transactionId) {
        String status = stripeAPI.retrieveChargeStatus(transactionId);
        return "succeeded".equals(status) ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
    }
}

class PayPalAdapter implements UnifiedPaymentGateway {
    private PayPalAPI payPalAPI;

    public PayPalAdapter(PayPalAPI payPalAPI) {
        this.payPalAPI = payPalAPI;
    }

    @Override
    public PaymentResult charge(String customerId, double amount, String currency) {
        System.out.println("💳 PayPal Adapter: Converting to PayPal format...");
        String paymentId = payPalAPI.executePayment(customerId, String.valueOf(amount), currency);
        return new PaymentResult(paymentId, true, "PayPal payment executed", amount);
    }

    @Override
    public PaymentResult refund(String transactionId, double amount) {
        boolean success = payPalAPI.refundPayment(transactionId, String.valueOf(amount));
        return new PaymentResult(transactionId, success, "PayPal refund processed", amount);
    }

    @Override
    public PaymentStatus getStatus(String transactionId) {
        String state = payPalAPI.checkPaymentState(transactionId);
        return "approved".equals(state) ? PaymentStatus.COMPLETED : PaymentStatus.PENDING;
    }
}

class SquareAdapter implements UnifiedPaymentGateway {
    private SquareAPI squareAPI;
    private String locationId;

    public SquareAdapter(SquareAPI squareAPI, String locationId) {
        this.squareAPI = squareAPI;
        this.locationId = locationId;
    }

    @Override
    public PaymentResult charge(String customerId, double amount, String currency) {
        System.out.println("💳 Square Adapter: Converting to Square format...");
        Map<String, String> result = squareAPI.processTransaction(locationId, customerId, (int)(amount * 100));
        return new PaymentResult(result.get("transaction_id"), true, "Square transaction processed", amount);
    }

    @Override
    public PaymentResult refund(String transactionId, double amount) {
        boolean success = squareAPI.cancelTransaction(transactionId);
        return new PaymentResult(transactionId, success, "Square refund processed", amount);
    }

    @Override
    public PaymentStatus getStatus(String transactionId) {
        return PaymentStatus.COMPLETED;
    }
}

// ==================== EXAMPLE 2: CLOUD STORAGE ADAPTER ====================
// Used in: Multi-cloud architectures, Cloud migration
// Interview Question: Design a multi-cloud storage system

interface UnifiedCloudStorage {
    void uploadFile(String bucket, String key, byte[] data);
    byte[] downloadFile(String bucket, String key);
    void deleteFile(String bucket, String key);
    List<String> listFiles(String bucket, String prefix);
}

class AWSS3Client {
    public void putObject(String bucket, String key, byte[] content, Map<String, String> metadata) {
        System.out.printf("   [AWS S3] putObject: s3://%s/%s (%d bytes)%n", bucket, key, content.length);
    }

    public byte[] getObject(String bucket, String key) {
        System.out.printf("   [AWS S3] getObject: s3://%s/%s%n", bucket, key);
        return new byte[100];
    }

    public void deleteObject(String bucket, String key) {
        System.out.printf("   [AWS S3] deleteObject: s3://%s/%s%n", bucket, key);
    }

    public List<String> listObjectsV2(String bucket, String prefix) {
        System.out.printf("   [AWS S3] listObjectsV2: s3://%s/%s*%n", bucket, prefix);
        return Arrays.asList(prefix + "file1.txt", prefix + "file2.txt");
    }
}

class GCPStorageClient {
    public void insertObject(String bucketName, String objectName, byte[] content) {
        System.out.printf("   [GCP GCS] insertObject: gs://%s/%s (%d bytes)%n", bucketName, objectName, content.length);
    }

    public byte[] readObject(String bucketName, String objectName) {
        System.out.printf("   [GCP GCS] readObject: gs://%s/%s%n", bucketName, objectName);
        return new byte[100];
    }

    public void removeObject(String bucketName, String objectName) {
        System.out.printf("   [GCP GCS] removeObject: gs://%s/%s%n", bucketName, objectName);
    }

    public List<String> listBlobs(String bucketName, String prefix) {
        System.out.printf("   [GCP GCS] listBlobs: gs://%s/%s*%n", bucketName, prefix);
        return Arrays.asList(prefix + "data1.json", prefix + "data2.json");
    }
}

class AzureBlobClient {
    public void uploadBlob(String container, String blobName, byte[] data) {
        System.out.printf("   [Azure Blob] uploadBlob: %s/%s (%d bytes)%n", container, blobName, data.length);
    }

    public byte[] downloadBlob(String container, String blobName) {
        System.out.printf("   [Azure Blob] downloadBlob: %s/%s%n", container, blobName);
        return new byte[100];
    }

    public void deleteBlob(String container, String blobName) {
        System.out.printf("   [Azure Blob] deleteBlob: %s/%s%n", container, blobName);
    }
}

class S3Adapter implements UnifiedCloudStorage {
    private AWSS3Client s3;

    public S3Adapter(AWSS3Client s3) { this.s3 = s3; }

    @Override
    public void uploadFile(String bucket, String key, byte[] data) {
        System.out.println("☁️  S3 Adapter: Translating upload...");
        s3.putObject(bucket, key, data, new HashMap<>());
    }

    @Override
    public byte[] downloadFile(String bucket, String key) {
        System.out.println("☁️  S3 Adapter: Translating download...");
        return s3.getObject(bucket, key);
    }

    @Override
    public void deleteFile(String bucket, String key) {
        s3.deleteObject(bucket, key);
    }

    @Override
    public List<String> listFiles(String bucket, String prefix) {
        return s3.listObjectsV2(bucket, prefix);
    }
}

class GCSAdapter implements UnifiedCloudStorage {
    private GCPStorageClient gcs;

    public GCSAdapter(GCPStorageClient gcs) { this.gcs = gcs; }

    @Override
    public void uploadFile(String bucket, String key, byte[] data) {
        System.out.println("☁️  GCS Adapter: Translating upload...");
        gcs.insertObject(bucket, key, data);
    }

    @Override
    public byte[] downloadFile(String bucket, String key) {
        System.out.println("☁️  GCS Adapter: Translating download...");
        return gcs.readObject(bucket, key);
    }

    @Override
    public void deleteFile(String bucket, String key) {
        gcs.removeObject(bucket, key);
    }

    @Override
    public List<String> listFiles(String bucket, String prefix) {
        return gcs.listBlobs(bucket, prefix);
    }
}

class AzureBlobAdapter implements UnifiedCloudStorage {
    private AzureBlobClient azure;

    public AzureBlobAdapter(AzureBlobClient azure) { this.azure = azure; }

    @Override
    public void uploadFile(String bucket, String key, byte[] data) {
        System.out.println("☁️  Azure Adapter: Translating upload...");
        azure.uploadBlob(bucket, key, data);
    }

    @Override
    public byte[] downloadFile(String bucket, String key) {
        System.out.println("☁️  Azure Adapter: Translating download...");
        return azure.downloadBlob(bucket, key);
    }

    @Override
    public void deleteFile(String bucket, String key) {
        azure.deleteBlob(bucket, key);
    }

    @Override
    public List<String> listFiles(String bucket, String prefix) {
        return Arrays.asList(); // Azure list not shown for brevity
    }
}

// ==================== EXAMPLE 3: MESSAGE FORMAT ADAPTER ====================
// Used in: API Gateways, ETL Pipelines, Message Brokers
// Interview Question: Design a data integration pipeline

interface MessageProcessor {
    Map<String, Object> process(Map<String, Object> message);
}

class XMLLegacyService {
    public String processXML(String xmlData) {
        System.out.println("   [XML Service] Processing XML: " + xmlData.substring(0, Math.min(50, xmlData.length())) + "...");
        return "<response><status>OK</status><id>12345</id></response>";
    }
}

class ProtobufService {
    public byte[] processProtobuf(byte[] protoData) {
        System.out.println("   [Protobuf Service] Processing " + protoData.length + " bytes of protobuf data");
        return new byte[]{1, 2, 3}; // Simulated protobuf response
    }
}

class XMLServiceAdapter implements MessageProcessor {
    private XMLLegacyService xmlService;

    public XMLServiceAdapter(XMLLegacyService xmlService) {
        this.xmlService = xmlService;
    }

    @Override
    public Map<String, Object> process(Map<String, Object> message) {
        System.out.println("🔄 XML Adapter: Converting JSON → XML...");
        // Convert Map to XML string
        StringBuilder xml = new StringBuilder("<request>");
        for (Map.Entry<String, Object> entry : message.entrySet()) {
            xml.append("<").append(entry.getKey()).append(">")
               .append(entry.getValue())
               .append("</").append(entry.getKey()).append(">");
        }
        xml.append("</request>");

        String xmlResponse = xmlService.processXML(xml.toString());

        // Convert XML response back to Map
        System.out.println("🔄 XML Adapter: Converting XML response → JSON...");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "OK");
        result.put("id", "12345");
        return result;
    }
}

class ProtobufServiceAdapter implements MessageProcessor {
    private ProtobufService protobufService;

    public ProtobufServiceAdapter(ProtobufService protobufService) {
        this.protobufService = protobufService;
    }

    @Override
    public Map<String, Object> process(Map<String, Object> message) {
        System.out.println("🔄 Protobuf Adapter: Converting JSON → Protobuf...");
        byte[] protoData = message.toString().getBytes(); // Simplified
        byte[] response = protobufService.processProtobuf(protoData);

        System.out.println("🔄 Protobuf Adapter: Converting Protobuf response → JSON...");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "OK");
        result.put("data", Arrays.toString(response));
        return result;
    }
}

// ==================== EXAMPLE 4: LOGGING FRAMEWORK ADAPTER ====================
// Used in: Logging abstraction layers (SLF4J)
// Interview Question: Design a logging framework

interface UnifiedLogger {
    void info(String message);
    void warn(String message);
    void error(String message, Exception e);
    void debug(String message);
}

class Log4jLogger {
    public void log(int level, String msg) {
        String[] levels = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};
        System.out.printf("   [Log4j] %s: %s%n", levels[level], msg);
    }

    public void logException(int level, String msg, Throwable t) {
        System.out.printf("   [Log4j] %s: %s | Exception: %s%n", "ERROR", msg, t.getMessage());
    }
}

class CloudWatchLogger {
    public void putLogEvent(String logGroup, String logStream, String message, String level) {
        System.out.printf("   [CloudWatch] %s/%s [%s]: %s%n", logGroup, logStream, level, message);
    }
}

class Log4jAdapter implements UnifiedLogger {
    private Log4jLogger log4j;

    public Log4jAdapter(Log4jLogger log4j) { this.log4j = log4j; }

    @Override
    public void info(String message) { log4j.log(2, message); }

    @Override
    public void warn(String message) { log4j.log(3, message); }

    @Override
    public void error(String message, Exception e) { log4j.logException(4, message, e); }

    @Override
    public void debug(String message) { log4j.log(1, message); }
}

class CloudWatchLoggerAdapter implements UnifiedLogger {
    private CloudWatchLogger cwLogger;
    private String logGroup;
    private String logStream;

    public CloudWatchLoggerAdapter(CloudWatchLogger cwLogger, String logGroup, String logStream) {
        this.cwLogger = cwLogger;
        this.logGroup = logGroup;
        this.logStream = logStream;
    }

    @Override
    public void info(String message) { cwLogger.putLogEvent(logGroup, logStream, message, "INFO"); }

    @Override
    public void warn(String message) { cwLogger.putLogEvent(logGroup, logStream, message, "WARN"); }

    @Override
    public void error(String message, Exception e) {
        cwLogger.putLogEvent(logGroup, logStream, message + " | " + e.getMessage(), "ERROR");
    }

    @Override
    public void debug(String message) { cwLogger.putLogEvent(logGroup, logStream, message, "DEBUG"); }
}

// ==================== DEMO ====================

public class AdapterPattern {
    public static void main(String[] args) {
        System.out.println("========== ADAPTER PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: PAYMENT GATEWAY ADAPTERS =====
        System.out.println("===== EXAMPLE 1: PAYMENT GATEWAY ADAPTERS =====\n");

        UnifiedPaymentGateway stripe = new StripeAdapter(new StripeAPI());
        UnifiedPaymentGateway paypal = new PayPalAdapter(new PayPalAPI());
        UnifiedPaymentGateway square = new SquareAdapter(new SquareAPI(), "LOC-001");

        // Same interface, different providers
        for (UnifiedPaymentGateway gateway : Arrays.asList(stripe, paypal, square)) {
            PaymentResult result = gateway.charge("CUST-123", 49.99, "USD");
            System.out.println("   Result: " + result + "\n");
        }

        // ===== EXAMPLE 2: CLOUD STORAGE ADAPTERS =====
        System.out.println("\n===== EXAMPLE 2: CLOUD STORAGE ADAPTERS (Multi-Cloud) =====\n");

        UnifiedCloudStorage aws = new S3Adapter(new AWSS3Client());
        UnifiedCloudStorage gcp = new GCSAdapter(new GCPStorageClient());
        UnifiedCloudStorage azure = new AzureBlobAdapter(new AzureBlobClient());

        byte[] fileData = "Hello, Cloud!".getBytes();

        // Same operation across all cloud providers
        for (UnifiedCloudStorage storage : Arrays.asList(aws, gcp, azure)) {
            storage.uploadFile("my-bucket", "data/file.txt", fileData);
            storage.downloadFile("my-bucket", "data/file.txt");
            System.out.println();
        }

        // ===== EXAMPLE 3: MESSAGE FORMAT ADAPTERS =====
        System.out.println("\n===== EXAMPLE 3: MESSAGE FORMAT ADAPTERS (API Gateway) =====\n");

        Map<String, Object> jsonMessage = new HashMap<>();
        jsonMessage.put("userId", "user-123");
        jsonMessage.put("action", "purchase");
        jsonMessage.put("amount", 99.99);

        MessageProcessor xmlAdapter = new XMLServiceAdapter(new XMLLegacyService());
        Map<String, Object> xmlResult = xmlAdapter.process(jsonMessage);
        System.out.println("   Result: " + xmlResult);

        System.out.println();

        MessageProcessor protoAdapter = new ProtobufServiceAdapter(new ProtobufService());
        Map<String, Object> protoResult = protoAdapter.process(jsonMessage);
        System.out.println("   Result: " + protoResult);

        // ===== EXAMPLE 4: LOGGING FRAMEWORK ADAPTERS =====
        System.out.println("\n\n===== EXAMPLE 4: LOGGING FRAMEWORK ADAPTERS (SLF4J-style) =====\n");

        UnifiedLogger log4jLogger = new Log4jAdapter(new Log4jLogger());
        UnifiedLogger cwLogger = new CloudWatchLoggerAdapter(
            new CloudWatchLogger(), "/app/my-service", "instance-001");

        System.out.println("Log4j Logger:");
        log4jLogger.info("Application started");
        log4jLogger.warn("Memory usage high");
        log4jLogger.error("Connection failed", new RuntimeException("Timeout"));

        System.out.println("\nCloudWatch Logger:");
        cwLogger.info("Application started");
        cwLogger.warn("Memory usage high");
        cwLogger.error("Connection failed", new RuntimeException("Timeout"));

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Payment processing (Stripe, PayPal, Square)");
        System.out.println("• Multi-cloud storage (S3, GCS, Azure Blob)");
        System.out.println("• API Gateway protocol translation (JSON/XML/Protobuf)");
        System.out.println("• Logging frameworks (SLF4J adapting Log4j, CloudWatch)");
    }
}
