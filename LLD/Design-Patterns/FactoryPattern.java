import java.util.*;

// ==================== FACTORY PATTERN ====================
// Definition: Provides an interface for creating objects without specifying exact classes.
//
// REAL System Design Interview Examples:
// 1. Database Connection Factory - Multi-database support
// 2. Message Queue Factory - Kafka, RabbitMQ, SQS abstraction
// 3. Storage Provider Factory - S3, GCS, Azure Blob
// 4. Cache Provider Factory - Redis, Memcached, DynamoDB
// 5. Logger Factory - Different log destinations
//
// Interview Use Cases:
// - Design Multi-Cloud System: Abstract cloud provider differences
// - Design Database Abstraction: Support multiple databases
// - Design Message Queue: Plugin different queue systems
// - Design Storage Service: Support multiple storage backends
// - Design Observability: Multiple logging/metrics backends

// ==================== EXAMPLE 1: DATABASE CONNECTION FACTORY ====================
// Used in: JDBC, Hibernate, Database abstraction layers
// Interview Question: Design a multi-database support system

interface DatabaseConnection {
    void connect(String connectionString);
    void disconnect();
    ResultSet executeQuery(String query);
    int executeUpdate(String query);
    void beginTransaction();
    void commit();
    void rollback();
    String getDatabaseType();
}

class MySQLConnection implements DatabaseConnection {
    private boolean connected = false;

    @Override
    public void connect(String connectionString) {
        System.out.println("🔌 MySQL: Connecting to " + connectionString);
        connected = true;
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 MySQL: Connection closed");
        connected = false;
    }

    @Override
    public ResultSet executeQuery(String query) {
        System.out.println("📊 MySQL: Executing query: " + query);
        return new ResultSet(Arrays.asList("row1", "row2"));
    }

    @Override
    public int executeUpdate(String query) {
        System.out.println("✏️ MySQL: Executing update: " + query);
        return 1;
    }

    @Override
    public void beginTransaction() {
        System.out.println("🔄 MySQL: BEGIN TRANSACTION");
    }

    @Override
    public void commit() {
        System.out.println("✅ MySQL: COMMIT");
    }

    @Override
    public void rollback() {
        System.out.println("↩️ MySQL: ROLLBACK");
    }

    @Override
    public String getDatabaseType() {
        return "MySQL";
    }
}

class PostgreSQLConnection implements DatabaseConnection {
    private boolean connected = false;

    @Override
    public void connect(String connectionString) {
        System.out.println("🔌 PostgreSQL: Connecting to " + connectionString);
        connected = true;
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 PostgreSQL: Connection closed");
        connected = false;
    }

    @Override
    public ResultSet executeQuery(String query) {
        System.out.println("📊 PostgreSQL: Executing query: " + query);
        return new ResultSet(Arrays.asList("row1", "row2", "row3"));
    }

    @Override
    public int executeUpdate(String query) {
        System.out.println("✏️ PostgreSQL: Executing update: " + query);
        return 1;
    }

    @Override
    public void beginTransaction() {
        System.out.println("🔄 PostgreSQL: START TRANSACTION");
    }

    @Override
    public void commit() {
        System.out.println("✅ PostgreSQL: COMMIT");
    }

    @Override
    public void rollback() {
        System.out.println("↩️ PostgreSQL: ROLLBACK");
    }

    @Override
    public String getDatabaseType() {
        return "PostgreSQL";
    }
}

class MongoDBConnection implements DatabaseConnection {
    private boolean connected = false;

    @Override
    public void connect(String connectionString) {
        System.out.println("🔌 MongoDB: Connecting to " + connectionString);
        connected = true;
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 MongoDB: Connection closed");
        connected = false;
    }

    @Override
    public ResultSet executeQuery(String query) {
        System.out.println("📊 MongoDB: Executing query: " + query);
        return new ResultSet(Arrays.asList("document1", "document2"));
    }

    @Override
    public int executeUpdate(String query) {
        System.out.println("✏️ MongoDB: Executing update: " + query);
        return 1;
    }

    @Override
    public void beginTransaction() {
        System.out.println("🔄 MongoDB: Starting session");
    }

    @Override
    public void commit() {
        System.out.println("✅ MongoDB: Commit transaction");
    }

    @Override
    public void rollback() {
        System.out.println("↩️ MongoDB: Abort transaction");
    }

    @Override
    public String getDatabaseType() {
        return "MongoDB";
    }
}

class ResultSet {
    private List<String> data;

    public ResultSet(List<String> data) {
        this.data = data;
    }

    public List<String> getData() {
        return data;
    }
}

class DatabaseFactory {
    public static DatabaseConnection createConnection(String dbType) {
        switch (dbType.toLowerCase()) {
            case "mysql":
                return new MySQLConnection();
            case "postgresql":
            case "postgres":
                return new PostgreSQLConnection();
            case "mongodb":
            case "mongo":
                return new MongoDBConnection();
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }
}

// ==================== EXAMPLE 2: MESSAGE QUEUE FACTORY ====================
// Used in: Event-driven architectures, Microservices
// Interview Question: Design a message queue abstraction layer

interface MessageQueue {
    void connect(String endpoint);
    void publish(String topic, String message);
    void subscribe(String topic, MessageHandler handler);
    void disconnect();
    String getQueueType();
}

interface MessageHandler {
    void handle(String message);
}

class KafkaQueue implements MessageQueue {
    @Override
    public void connect(String endpoint) {
        System.out.println("🔌 Kafka: Connected to broker at " + endpoint);
    }

    @Override
    public void publish(String topic, String message) {
        System.out.println("📤 Kafka: Publishing to topic '" + topic + "': " + message);
    }

    @Override
    public void subscribe(String topic, MessageHandler handler) {
        System.out.println("📥 Kafka: Subscribed to topic '" + topic + "'");
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 Kafka: Disconnected");
    }

    @Override
    public String getQueueType() {
        return "Kafka";
    }
}

class RabbitMQQueue implements MessageQueue {
    @Override
    public void connect(String endpoint) {
        System.out.println("🔌 RabbitMQ: Connected to " + endpoint);
    }

    @Override
    public void publish(String topic, String message) {
        System.out.println("📤 RabbitMQ: Publishing to exchange '" + topic + "': " + message);
    }

    @Override
    public void subscribe(String topic, MessageHandler handler) {
        System.out.println("📥 RabbitMQ: Subscribed to queue '" + topic + "'");
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 RabbitMQ: Disconnected");
    }

    @Override
    public String getQueueType() {
        return "RabbitMQ";
    }
}

class SQSQueue implements MessageQueue {
    @Override
    public void connect(String endpoint) {
        System.out.println("🔌 AWS SQS: Connected to queue " + endpoint);
    }

    @Override
    public void publish(String topic, String message) {
        System.out.println("📤 AWS SQS: Sending message to queue '" + topic + "': " + message);
    }

    @Override
    public void subscribe(String topic, MessageHandler handler) {
        System.out.println("📥 AWS SQS: Polling queue '" + topic + "'");
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 AWS SQS: Disconnected");
    }

    @Override
    public String getQueueType() {
        return "AWS SQS";
    }
}

class MessageQueueFactory {
    public static MessageQueue createQueue(String queueType) {
        switch (queueType.toLowerCase()) {
            case "kafka":
                return new KafkaQueue();
            case "rabbitmq":
            case "rabbit":
                return new RabbitMQQueue();
            case "sqs":
            case "aws-sqs":
                return new SQSQueue();
            default:
                throw new IllegalArgumentException("Unsupported queue type: " + queueType);
        }
    }
}

// ==================== EXAMPLE 3: CLOUD STORAGE FACTORY ====================
// Used in: Cloud-agnostic applications, Multi-cloud strategies
// Interview Question: Design a file storage service

interface StorageProvider {
    void upload(String bucket, String key, byte[] data);
    byte[] download(String bucket, String key);
    void delete(String bucket, String key);
    String generatePresignedUrl(String bucket, String key, int expirySeconds);
    String getProviderName();
}

class S3StorageProvider implements StorageProvider {
    @Override
    public void upload(String bucket, String key, byte[] data) {
        System.out.printf("☁️ AWS S3: Uploading %s to bucket %s (%d bytes)%n", key, bucket, data.length);
    }

    @Override
    public byte[] download(String bucket, String key) {
        System.out.println("📥 AWS S3: Downloading " + key + " from bucket " + bucket);
        return new byte[1024];
    }

    @Override
    public void delete(String bucket, String key) {
        System.out.println("🗑️ AWS S3: Deleting " + key + " from bucket " + bucket);
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, int expirySeconds) {
        String url = "https://s3.amazonaws.com/" + bucket + "/" + key + "?expires=" + expirySeconds;
        System.out.println("🔗 AWS S3: Generated presigned URL: " + url);
        return url;
    }

    @Override
    public String getProviderName() {
        return "AWS S3";
    }
}

class GCSStorageProvider implements StorageProvider {
    @Override
    public void upload(String bucket, String key, byte[] data) {
        System.out.printf("☁️ Google Cloud Storage: Uploading %s to bucket %s (%d bytes)%n", key, bucket, data.length);
    }

    @Override
    public byte[] download(String bucket, String key) {
        System.out.println("📥 Google Cloud Storage: Downloading " + key + " from bucket " + bucket);
        return new byte[1024];
    }

    @Override
    public void delete(String bucket, String key) {
        System.out.println("🗑️ Google Cloud Storage: Deleting " + key + " from bucket " + bucket);
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, int expirySeconds) {
        String url = "https://storage.googleapis.com/" + bucket + "/" + key + "?expires=" + expirySeconds;
        System.out.println("🔗 Google Cloud Storage: Generated signed URL: " + url);
        return url;
    }

    @Override
    public String getProviderName() {
        return "Google Cloud Storage";
    }
}

class AzureBlobProvider implements StorageProvider {
    @Override
    public void upload(String bucket, String key, byte[] data) {
        System.out.printf("☁️ Azure Blob: Uploading %s to container %s (%d bytes)%n", key, bucket, data.length);
    }

    @Override
    public byte[] download(String bucket, String key) {
        System.out.println("📥 Azure Blob: Downloading " + key + " from container " + bucket);
        return new byte[1024];
    }

    @Override
    public void delete(String bucket, String key) {
        System.out.println("🗑️ Azure Blob: Deleting " + key + " from container " + bucket);
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, int expirySeconds) {
        String url = "https://mystorageaccount.blob.core.windows.net/" + bucket + "/" + key + "?expires=" + expirySeconds;
        System.out.println("🔗 Azure Blob: Generated SAS URL: " + url);
        return url;
    }

    @Override
    public String getProviderName() {
        return "Azure Blob Storage";
    }
}

class StorageFactory {
    public static StorageProvider createProvider(String providerType) {
        switch (providerType.toLowerCase()) {
            case "s3":
            case "aws":
                return new S3StorageProvider();
            case "gcs":
            case "google":
                return new GCSStorageProvider();
            case "azure":
            case "blob":
                return new AzureBlobProvider();
            default:
                throw new IllegalArgumentException("Unsupported storage provider: " + providerType);
        }
    }
}

// ==================== EXAMPLE 4: CACHE PROVIDER FACTORY ====================
// Used in: Caching layers, Session management
// Interview Question: Design a caching system

interface CacheProvider {
    void set(String key, String value, int ttlSeconds);
    String get(String key);
    void delete(String key);
    boolean exists(String key);
    void flush();
    String getProviderName();
}

class RedisCache implements CacheProvider {
    private Map<String, String> store = new HashMap<>();

    @Override
    public void set(String key, String value, int ttlSeconds) {
        store.put(key, value);
        System.out.printf("💾 Redis: SET %s = %s (TTL: %ds)%n", key, value, ttlSeconds);
    }

    @Override
    public String get(String key) {
        String value = store.get(key);
        System.out.println("💾 Redis: GET " + key + " → " + value);
        return value;
    }

    @Override
    public void delete(String key) {
        store.remove(key);
        System.out.println("🗑️ Redis: DEL " + key);
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }

    @Override
    public void flush() {
        store.clear();
        System.out.println("🧹 Redis: FLUSHALL");
    }

    @Override
    public String getProviderName() {
        return "Redis";
    }
}

class MemcachedCache implements CacheProvider {
    private Map<String, String> store = new HashMap<>();

    @Override
    public void set(String key, String value, int ttlSeconds) {
        store.put(key, value);
        System.out.printf("💾 Memcached: set %s = %s (expiration: %ds)%n", key, value, ttlSeconds);
    }

    @Override
    public String get(String key) {
        String value = store.get(key);
        System.out.println("💾 Memcached: get " + key + " → " + value);
        return value;
    }

    @Override
    public void delete(String key) {
        store.remove(key);
        System.out.println("🗑️ Memcached: delete " + key);
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }

    @Override
    public void flush() {
        store.clear();
        System.out.println("🧹 Memcached: flush_all");
    }

    @Override
    public String getProviderName() {
        return "Memcached";
    }
}

class DynamoDBCache implements CacheProvider {
    private Map<String, String> store = new HashMap<>();

    @Override
    public void set(String key, String value, int ttlSeconds) {
        store.put(key, value);
        System.out.printf("💾 DynamoDB: PutItem %s = %s (TTL: %ds)%n", key, value, ttlSeconds);
    }

    @Override
    public String get(String key) {
        String value = store.get(key);
        System.out.println("💾 DynamoDB: GetItem " + key + " → " + value);
        return value;
    }

    @Override
    public void delete(String key) {
        store.remove(key);
        System.out.println("🗑️ DynamoDB: DeleteItem " + key);
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }

    @Override
    public void flush() {
        store.clear();
        System.out.println("🧹 DynamoDB: Scan and delete all items");
    }

    @Override
    public String getProviderName() {
        return "DynamoDB";
    }
}

class CacheFactory {
    public static CacheProvider createCache(String cacheType) {
        switch (cacheType.toLowerCase()) {
            case "redis":
                return new RedisCache();
            case "memcached":
                return new MemcachedCache();
            case "dynamodb":
                return new DynamoDBCache();
            default:
                throw new IllegalArgumentException("Unsupported cache type: " + cacheType);
        }
    }
}

// ==================== EXAMPLE 5: LOGGER FACTORY ====================
// Used in: Observability, Distributed tracing
// Interview Question: Design a logging and monitoring system

interface Logger {
    void log(String level, String message, Map<String, String> context);
    void info(String message);
    void warn(String message);
    void error(String message);
    String getLoggerType();
}

class CloudWatchLogger implements Logger {
    private String logGroup;

    public CloudWatchLogger(String logGroup) {
        this.logGroup = logGroup;
    }

    @Override
    public void log(String level, String message, Map<String, String> context) {
        System.out.printf("📊 CloudWatch [%s]: [%s] %s | Context: %s%n", 
            logGroup, level, message, context);
    }

    @Override
    public void info(String message) {
        log("INFO", message, new HashMap<>());
    }

    @Override
    public void warn(String message) {
        log("WARN", message, new HashMap<>());
    }

    @Override
    public void error(String message) {
        log("ERROR", message, new HashMap<>());
    }

    @Override
    public String getLoggerType() {
        return "CloudWatch";
    }
}

class DatadogLogger implements Logger {
    private String service;

    public DatadogLogger(String service) {
        this.service = service;
    }

    @Override
    public void log(String level, String message, Map<String, String> context) {
        System.out.printf("🐶 Datadog [%s]: [%s] %s | Tags: %s%n", 
            service, level, message, context);
    }

    @Override
    public void info(String message) {
        log("INFO", message, new HashMap<>());
    }

    @Override
    public void warn(String message) {
        log("WARN", message, new HashMap<>());
    }

    @Override
    public void error(String message) {
        log("ERROR", message, new HashMap<>());
    }

    @Override
    public String getLoggerType() {
        return "Datadog";
    }
}

class SplunkLogger implements Logger {
    private String index;

    public SplunkLogger(String index) {
        this.index = index;
    }

    @Override
    public void log(String level, String message, Map<String, String> context) {
        System.out.printf("📈 Splunk [%s]: level=%s message=\"%s\" %s%n", 
            index, level, message, context);
    }

    @Override
    public void info(String message) {
        log("INFO", message, new HashMap<>());
    }

    @Override
    public void warn(String message) {
        log("WARN", message, new HashMap<>());
    }

    @Override
    public void error(String message) {
        log("ERROR", message, new HashMap<>());
    }

    @Override
    public String getLoggerType() {
        return "Splunk";
    }
}

class LoggerFactory {
    public static Logger createLogger(String loggerType, String identifier) {
        switch (loggerType.toLowerCase()) {
            case "cloudwatch":
                return new CloudWatchLogger(identifier);
            case "datadog":
                return new DatadogLogger(identifier);
            case "splunk":
                return new SplunkLogger(identifier);
            default:
                throw new IllegalArgumentException("Unsupported logger type: " + loggerType);
        }
    }
}

// ==================== DEMO ====================

public class FactoryPattern {
    public static void main(String[] args) {
        System.out.println("========== FACTORY PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: DATABASE FACTORY =====
        System.out.println("===== EXAMPLE 1: DATABASE CONNECTION FACTORY =====\n");
        
        DatabaseConnection mysql = DatabaseFactory.createConnection("mysql");
        mysql.connect("jdbc:mysql://localhost:3306/mydb");
        mysql.beginTransaction();
        mysql.executeUpdate("INSERT INTO users VALUES (1, 'Alice')");
        mysql.commit();
        ResultSet results = mysql.executeQuery("SELECT * FROM users");
        mysql.disconnect();
        
        System.out.println();
        
        DatabaseConnection postgres = DatabaseFactory.createConnection("postgresql");
        postgres.connect("jdbc:postgresql://localhost:5432/mydb");
        postgres.executeQuery("SELECT * FROM orders WHERE status = 'pending'");
        postgres.disconnect();

        // ===== EXAMPLE 2: MESSAGE QUEUE FACTORY =====
        System.out.println("\n\n===== EXAMPLE 2: MESSAGE QUEUE FACTORY =====\n");
        
        MessageQueue kafka = MessageQueueFactory.createQueue("kafka");
        kafka.connect("localhost:9092");
        kafka.publish("user-events", "User registered: user-123");
        kafka.disconnect();
        
        System.out.println();
        
        MessageQueue rabbitmq = MessageQueueFactory.createQueue("rabbitmq");
        rabbitmq.connect("amqp://localhost:5672");
        rabbitmq.publish("order-events", "Order created: order-456");
        rabbitmq.disconnect();
        
        System.out.println();
        
        MessageQueue sqs = MessageQueueFactory.createQueue("sqs");
        sqs.connect("https://sqs.us-east-1.amazonaws.com/123456789/my-queue");
        sqs.publish("notifications", "Payment completed: payment-789");
        sqs.disconnect();

        // ===== EXAMPLE 3: CLOUD STORAGE FACTORY =====
        System.out.println("\n\n===== EXAMPLE 3: CLOUD STORAGE FACTORY =====\n");
        
        StorageProvider s3 = StorageFactory.createProvider("s3");
        s3.upload("my-bucket", "images/photo.jpg", new byte[1024]);
        s3.generatePresignedUrl("my-bucket", "images/photo.jpg", 3600);
        s3.download("my-bucket", "images/photo.jpg");
        
        System.out.println();
        
        StorageProvider gcs = StorageFactory.createProvider("gcs");
        gcs.upload("my-bucket", "documents/report.pdf", new byte[2048]);
        gcs.generatePresignedUrl("my-bucket", "documents/report.pdf", 3600);

        // ===== EXAMPLE 4: CACHE PROVIDER FACTORY =====
        System.out.println("\n\n===== EXAMPLE 4: CACHE PROVIDER FACTORY =====\n");
        
        CacheProvider redis = CacheFactory.createCache("redis");
        redis.set("session:abc123", "user-data", 3600);
        redis.get("session:abc123");
        redis.delete("session:abc123");
        
        System.out.println();
        
        CacheProvider memcached = CacheFactory.createCache("memcached");
        memcached.set("user:456", "profile-data", 1800);
        memcached.get("user:456");

        // ===== EXAMPLE 5: LOGGER FACTORY =====
        System.out.println("\n\n===== EXAMPLE 5: LOGGER FACTORY =====\n");
        
        Logger cloudwatch = LoggerFactory.createLogger("cloudwatch", "/aws/lambda/my-function");
        Map<String, String> context = new HashMap<>();
        context.put("request_id", "req-123");
        context.put("user_id", "user-456");
        cloudwatch.log("INFO", "Request processed successfully", context);
        
        System.out.println();
        
        Logger datadog = LoggerFactory.createLogger("datadog", "api-service");
        datadog.warn("High latency detected");
        datadog.error("Database connection failed");

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Database abstraction layers (JDBC, Hibernate)");
        System.out.println("• Message queue abstractions (Kafka, RabbitMQ, SQS)");
        System.out.println("• Cloud storage services (S3, GCS, Azure Blob)");
        System.out.println("• Cache providers (Redis, Memcached, DynamoDB)");
        System.out.println("• Logging systems (CloudWatch, Datadog, Splunk)");
    }
}
