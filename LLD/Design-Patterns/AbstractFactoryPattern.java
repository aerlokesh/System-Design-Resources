import java.util.*;

// ==================== ABSTRACT FACTORY PATTERN ====================
// Definition: Provides an interface for creating families of related objects without
// specifying their concrete classes. A factory of factories.
//
// REAL System Design Interview Examples:
// 1. Cloud Provider Factory - Create AWS/GCP/Azure resources as families
// 2. Database Factory - Create connection + query builder + migrator per DB type
// 3. UI Theme Factory - Create button + input + card per theme
// 4. Serialization Factory - Create serializer + deserializer + validator per format
// 5. Messaging Factory - Create producer + consumer + admin per message broker
//
// Interview Use Cases:
// - Design Multi-Cloud System: Swap cloud families (AWS ↔ GCP ↔ Azure)
// - Design Cross-Platform App: Platform-specific UI component families
// - Design Database Abstraction: DB-specific connection/query families
// - Design Message Queue Abstraction: Broker-specific families
// - Design Multi-Format Export: Format-specific serialization families

// ==================== EXAMPLE 1: CLOUD PROVIDER FACTORY ====================
// Used in: Multi-cloud platforms, Terraform providers, Pulumi
// Interview Question: Design a multi-cloud infrastructure platform

// Product interfaces (family of related objects)
interface ComputeService {
    String launchInstance(String name, String type);
    void terminateInstance(String instanceId);
    String getProviderName();
}

interface ObjectStorage {
    void uploadFile(String bucket, String key, byte[] data);
    byte[] downloadFile(String bucket, String key);
    String getProviderName();
}

interface QueueService {
    void sendMessage(String queue, String message);
    String receiveMessage(String queue);
    String getProviderName();
}

// ABSTRACT FACTORY
interface CloudFactory {
    ComputeService createComputeService();
    ObjectStorage createObjectStorage();
    QueueService createQueueService();
    String getCloudName();
}

// --- AWS Family ---
class AWSCompute implements ComputeService {
    @Override
    public String launchInstance(String name, String type) {
        String id = "i-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   🟠 [AWS EC2] Launching %s (%s) → %s%n", name, type, id);
        return id;
    }
    @Override
    public void terminateInstance(String instanceId) {
        System.out.printf("   🟠 [AWS EC2] Terminating %s%n", instanceId);
    }
    @Override
    public String getProviderName() { return "AWS EC2"; }
}

class AWSS3 implements ObjectStorage {
    @Override
    public void uploadFile(String bucket, String key, byte[] data) {
        System.out.printf("   🟠 [AWS S3] PutObject s3://%s/%s (%d bytes)%n", bucket, key, data.length);
    }
    @Override
    public byte[] downloadFile(String bucket, String key) {
        System.out.printf("   🟠 [AWS S3] GetObject s3://%s/%s%n", bucket, key);
        return new byte[100];
    }
    @Override
    public String getProviderName() { return "AWS S3"; }
}

class AWSSQS implements QueueService {
    @Override
    public void sendMessage(String queue, String message) {
        System.out.printf("   🟠 [AWS SQS] SendMessage to %s%n", queue);
    }
    @Override
    public String receiveMessage(String queue) {
        System.out.printf("   🟠 [AWS SQS] ReceiveMessage from %s%n", queue);
        return "sqs-message";
    }
    @Override
    public String getProviderName() { return "AWS SQS"; }
}

class AWSFactory implements CloudFactory {
    @Override
    public ComputeService createComputeService() { return new AWSCompute(); }
    @Override
    public ObjectStorage createObjectStorage() { return new AWSS3(); }
    @Override
    public QueueService createQueueService() { return new AWSSQS(); }
    @Override
    public String getCloudName() { return "AWS"; }
}

// --- GCP Family ---
class GCPCompute implements ComputeService {
    @Override
    public String launchInstance(String name, String type) {
        String id = "gce-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   🔵 [GCP GCE] Creating %s (%s) → %s%n", name, type, id);
        return id;
    }
    @Override
    public void terminateInstance(String instanceId) {
        System.out.printf("   🔵 [GCP GCE] Deleting %s%n", instanceId);
    }
    @Override
    public String getProviderName() { return "GCP Compute Engine"; }
}

class GCPStorage implements ObjectStorage {
    @Override
    public void uploadFile(String bucket, String key, byte[] data) {
        System.out.printf("   🔵 [GCP GCS] InsertObject gs://%s/%s (%d bytes)%n", bucket, key, data.length);
    }
    @Override
    public byte[] downloadFile(String bucket, String key) {
        System.out.printf("   🔵 [GCP GCS] ReadObject gs://%s/%s%n", bucket, key);
        return new byte[100];
    }
    @Override
    public String getProviderName() { return "GCP Cloud Storage"; }
}

class GCPPubSub implements QueueService {
    @Override
    public void sendMessage(String queue, String message) {
        System.out.printf("   🔵 [GCP Pub/Sub] Publish to topic %s%n", queue);
    }
    @Override
    public String receiveMessage(String queue) {
        System.out.printf("   🔵 [GCP Pub/Sub] Pull from subscription %s%n", queue);
        return "pubsub-message";
    }
    @Override
    public String getProviderName() { return "GCP Pub/Sub"; }
}

class GCPFactory implements CloudFactory {
    @Override
    public ComputeService createComputeService() { return new GCPCompute(); }
    @Override
    public ObjectStorage createObjectStorage() { return new GCPStorage(); }
    @Override
    public QueueService createQueueService() { return new GCPPubSub(); }
    @Override
    public String getCloudName() { return "GCP"; }
}

// --- Azure Family ---
class AzureCompute implements ComputeService {
    @Override
    public String launchInstance(String name, String type) {
        String id = "vm-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   🟢 [Azure VM] Creating %s (%s) → %s%n", name, type, id);
        return id;
    }
    @Override
    public void terminateInstance(String instanceId) {
        System.out.printf("   🟢 [Azure VM] Deleting %s%n", instanceId);
    }
    @Override
    public String getProviderName() { return "Azure VM"; }
}

class AzureBlob implements ObjectStorage {
    @Override
    public void uploadFile(String bucket, String key, byte[] data) {
        System.out.printf("   🟢 [Azure Blob] Upload %s/%s (%d bytes)%n", bucket, key, data.length);
    }
    @Override
    public byte[] downloadFile(String bucket, String key) {
        System.out.printf("   🟢 [Azure Blob] Download %s/%s%n", bucket, key);
        return new byte[100];
    }
    @Override
    public String getProviderName() { return "Azure Blob Storage"; }
}

class AzureServiceBus implements QueueService {
    @Override
    public void sendMessage(String queue, String message) {
        System.out.printf("   🟢 [Azure Service Bus] Send to %s%n", queue);
    }
    @Override
    public String receiveMessage(String queue) {
        System.out.printf("   🟢 [Azure Service Bus] Receive from %s%n", queue);
        return "servicebus-message";
    }
    @Override
    public String getProviderName() { return "Azure Service Bus"; }
}

class AzureFactory implements CloudFactory {
    @Override
    public ComputeService createComputeService() { return new AzureCompute(); }
    @Override
    public ObjectStorage createObjectStorage() { return new AzureBlob(); }
    @Override
    public QueueService createQueueService() { return new AzureServiceBus(); }
    @Override
    public String getCloudName() { return "Azure"; }
}

// ==================== EXAMPLE 2: DATABASE FACTORY ====================
// Used in: ORM frameworks, database abstraction layers
// Interview Question: Design a multi-database abstraction

interface DBConnection {
    void connect(String host, int port, String database);
    void disconnect();
    String getType();
}

interface DBQueryBuilder {
    String buildSelect(String table, String[] columns, String where);
    String buildInsert(String table, Map<String, Object> values);
    String getType();
}

interface DBMigrator {
    void createTable(String name, Map<String, String> columns);
    void dropTable(String name);
    String getType();
}

interface DatabaseFactory {
    DBConnection createConnection();
    DBQueryBuilder createQueryBuilder();
    DBMigrator createMigrator();
    String getDatabaseName();
}

class MySQLConnection implements DBConnection {
    @Override
    public void connect(String host, int port, String database) {
        System.out.printf("   🐬 [MySQL] Connected to %s:%d/%s%n", host, port, database);
    }
    @Override
    public void disconnect() { System.out.println("   🐬 [MySQL] Disconnected"); }
    @Override
    public String getType() { return "MySQL"; }
}

class MySQLQueryBuilder implements DBQueryBuilder {
    @Override
    public String buildSelect(String table, String[] columns, String where) {
        String query = "SELECT " + String.join(", ", columns) + " FROM `" + table + "` WHERE " + where + " LIMIT 100";
        System.out.printf("   🐬 [MySQL] %s%n", query);
        return query;
    }
    @Override
    public String buildInsert(String table, Map<String, Object> values) {
        String query = "INSERT INTO `" + table + "` SET " + values;
        System.out.printf("   🐬 [MySQL] %s%n", query);
        return query;
    }
    @Override
    public String getType() { return "MySQL"; }
}

class MySQLMigrator implements DBMigrator {
    @Override
    public void createTable(String name, Map<String, String> columns) {
        System.out.printf("   🐬 [MySQL] CREATE TABLE `%s` (%s) ENGINE=InnoDB%n", name, columns);
    }
    @Override
    public void dropTable(String name) {
        System.out.printf("   🐬 [MySQL] DROP TABLE IF EXISTS `%s`%n", name);
    }
    @Override
    public String getType() { return "MySQL"; }
}

class MySQLFactory implements DatabaseFactory {
    @Override
    public DBConnection createConnection() { return new MySQLConnection(); }
    @Override
    public DBQueryBuilder createQueryBuilder() { return new MySQLQueryBuilder(); }
    @Override
    public DBMigrator createMigrator() { return new MySQLMigrator(); }
    @Override
    public String getDatabaseName() { return "MySQL"; }
}

class PostgresConnection implements DBConnection {
    @Override
    public void connect(String host, int port, String database) {
        System.out.printf("   🐘 [PostgreSQL] Connected to %s:%d/%s%n", host, port, database);
    }
    @Override
    public void disconnect() { System.out.println("   🐘 [PostgreSQL] Disconnected"); }
    @Override
    public String getType() { return "PostgreSQL"; }
}

class PostgresQueryBuilder implements DBQueryBuilder {
    @Override
    public String buildSelect(String table, String[] columns, String where) {
        String query = "SELECT " + String.join(", ", columns) + " FROM \"" + table + "\" WHERE " + where + " FETCH FIRST 100 ROWS ONLY";
        System.out.printf("   🐘 [PostgreSQL] %s%n", query);
        return query;
    }
    @Override
    public String buildInsert(String table, Map<String, Object> values) {
        String query = "INSERT INTO \"" + table + "\" VALUES " + values + " RETURNING id";
        System.out.printf("   🐘 [PostgreSQL] %s%n", query);
        return query;
    }
    @Override
    public String getType() { return "PostgreSQL"; }
}

class PostgresMigrator implements DBMigrator {
    @Override
    public void createTable(String name, Map<String, String> columns) {
        System.out.printf("   🐘 [PostgreSQL] CREATE TABLE \"%s\" (%s)%n", name, columns);
    }
    @Override
    public void dropTable(String name) {
        System.out.printf("   🐘 [PostgreSQL] DROP TABLE IF EXISTS \"%s\" CASCADE%n", name);
    }
    @Override
    public String getType() { return "PostgreSQL"; }
}

class PostgresFactory implements DatabaseFactory {
    @Override
    public DBConnection createConnection() { return new PostgresConnection(); }
    @Override
    public DBQueryBuilder createQueryBuilder() { return new PostgresQueryBuilder(); }
    @Override
    public DBMigrator createMigrator() { return new PostgresMigrator(); }
    @Override
    public String getDatabaseName() { return "PostgreSQL"; }
}

// ==================== CLIENT CODE ====================

class CloudInfraManager {
    private ComputeService compute;
    private ObjectStorage storage;
    private QueueService queue;
    private String cloud;

    public CloudInfraManager(CloudFactory factory) {
        this.cloud = factory.getCloudName();
        this.compute = factory.createComputeService();
        this.storage = factory.createObjectStorage();
        this.queue = factory.createQueueService();
        System.out.printf("   ☁️  Infrastructure initialized on %s%n", cloud);
    }

    public void deployApplication(String appName) {
        System.out.printf("%n   🚀 Deploying '%s' on %s:%n", appName, cloud);
        String instanceId = compute.launchInstance(appName + "-server", "medium");
        storage.uploadFile(appName + "-bucket", "config/app.yaml", "config data".getBytes());
        queue.sendMessage(appName + "-events", "deployment-started");
        System.out.printf("   ✅ '%s' deployed on %s%n", appName, cloud);
    }
}

// ==================== DEMO ====================

public class AbstractFactoryPattern {
    public static void main(String[] args) {
        System.out.println("========== ABSTRACT FACTORY PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: CLOUD PROVIDER =====
        System.out.println("===== EXAMPLE 1: MULTI-CLOUD INFRASTRUCTURE =====\n");

        // Same client code, different cloud providers!
        System.out.println("--- Deploying on AWS ---");
        CloudInfraManager awsManager = new CloudInfraManager(new AWSFactory());
        awsManager.deployApplication("my-app");

        System.out.println("\n--- Deploying on GCP ---");
        CloudInfraManager gcpManager = new CloudInfraManager(new GCPFactory());
        gcpManager.deployApplication("my-app");

        System.out.println("\n--- Deploying on Azure ---");
        CloudInfraManager azureManager = new CloudInfraManager(new AzureFactory());
        azureManager.deployApplication("my-app");

        // ===== EXAMPLE 2: DATABASE FACTORY =====
        System.out.println("\n\n===== EXAMPLE 2: MULTI-DATABASE ABSTRACTION =====\n");

        System.out.println("--- Using MySQL ---");
        DatabaseFactory mysqlFactory = new MySQLFactory();
        DBConnection mysqlConn = mysqlFactory.createConnection();
        DBQueryBuilder mysqlQuery = mysqlFactory.createQueryBuilder();
        DBMigrator mysqlMigrator = mysqlFactory.createMigrator();

        mysqlConn.connect("db.example.com", 3306, "myapp");
        mysqlMigrator.createTable("users", Map.of("id", "INT", "name", "VARCHAR(100)"));
        mysqlQuery.buildSelect("users", new String[]{"id", "name"}, "active = 1");
        mysqlQuery.buildInsert("users", Map.of("name", "Alice", "email", "alice@example.com"));

        System.out.println("\n--- Using PostgreSQL (same operations, different SQL) ---");
        DatabaseFactory pgFactory = new PostgresFactory();
        DBConnection pgConn = pgFactory.createConnection();
        DBQueryBuilder pgQuery = pgFactory.createQueryBuilder();
        DBMigrator pgMigrator = pgFactory.createMigrator();

        pgConn.connect("pg.example.com", 5432, "myapp");
        pgMigrator.createTable("users", Map.of("id", "SERIAL", "name", "VARCHAR(100)"));
        pgQuery.buildSelect("users", new String[]{"id", "name"}, "active = true");
        pgQuery.buildInsert("users", Map.of("name", "Bob", "email", "bob@example.com"));

        // Dynamic factory selection
        System.out.println("\n--- Dynamic Factory Selection ---");
        String cloudProvider = "gcp"; // Could come from config
        CloudFactory factory;
        switch (cloudProvider) {
            case "aws": factory = new AWSFactory(); break;
            case "gcp": factory = new GCPFactory(); break;
            case "azure": factory = new AzureFactory(); break;
            default: throw new IllegalArgumentException("Unknown provider: " + cloudProvider);
        }
        System.out.printf("   Selected: %s (from config)%n", factory.getCloudName());
        new CloudInfraManager(factory).deployApplication("config-driven-app");

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Multi-cloud platforms (Terraform, Pulumi, CloudFormation)");
        System.out.println("• Database abstraction (ORM frameworks, connection libraries)");
        System.out.println("• Cross-platform UI (React Native, Flutter)");
        System.out.println("• Message broker abstraction (Kafka/RabbitMQ/SQS)");
    }
}
