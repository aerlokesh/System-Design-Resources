import java.util.*;

// ==================== BRIDGE PATTERN ====================
// Definition: Decouples an abstraction from its implementation so that the two can
// vary independently. Separates "what" from "how".
//
// REAL System Design Interview Examples:
// 1. Notification System - Message type × Delivery channel
// 2. Payment Processing - Payment method × Payment processor
// 3. Data Export - Report type × Output format
// 4. Rendering Engine - Shape/UI × Rendering platform
// 5. Persistence Layer - Domain model × Storage backend
//
// Interview Use Cases:
// - Design Notification Service: Message types (alert/promo) × channels (email/push/SMS)
// - Design Cross-Platform App: UI logic × platform rendering
// - Design Payment System: Payment types × payment providers
// - Design Data Layer: Domain objects × different databases
// - Design Logging System: Log levels × output destinations

// ==================== EXAMPLE 1: NOTIFICATION SYSTEM ====================
// Used in: Any notification/messaging system
// Interview Question: Design a notification service
// Without Bridge: AlertEmail, AlertSMS, AlertPush, PromoEmail, PromoSMS, PromoPush = 6 classes
// With Bridge: 2 abstractions + 3 implementations = 5 classes, scales independently

// IMPLEMENTATION hierarchy — "how" to deliver
interface NotificationChannel {
    void send(String recipient, String title, String body, Map<String, String> metadata);
    String getChannelName();
}

class EmailChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String title, String body, Map<String, String> metadata) {
        System.out.printf("   ✉️  [Email] To: %s | Subject: %s | Body: %s%n", recipient, title, body);
    }
    @Override
    public String getChannelName() { return "Email"; }
}

class SMSChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String title, String body, Map<String, String> metadata) {
        String shortBody = body.length() > 160 ? body.substring(0, 157) + "..." : body;
        System.out.printf("   📱 [SMS] To: %s | Message: %s%n", recipient, shortBody);
    }
    @Override
    public String getChannelName() { return "SMS"; }
}

class PushNotificationChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String title, String body, Map<String, String> metadata) {
        System.out.printf("   🔔 [Push] Device: %s | Title: %s | Body: %s%n", recipient, title, body);
    }
    @Override
    public String getChannelName() { return "Push"; }
}

class SlackChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String title, String body, Map<String, String> metadata) {
        System.out.printf("   💬 [Slack] Channel: %s | *%s* %s%n", recipient, title, body);
    }
    @Override
    public String getChannelName() { return "Slack"; }
}

// ABSTRACTION hierarchy — "what" type of notification
abstract class Notification {
    protected NotificationChannel channel;

    public Notification(NotificationChannel channel) {
        this.channel = channel;
    }

    public abstract void notify(String recipient, Map<String, Object> data);
    public abstract String getNotificationType();
}

class AlertNotification extends Notification {
    public AlertNotification(NotificationChannel channel) { super(channel); }

    @Override
    public void notify(String recipient, Map<String, Object> data) {
        String severity = (String) data.getOrDefault("severity", "WARNING");
        String service = (String) data.getOrDefault("service", "unknown");
        String message = (String) data.getOrDefault("message", "");

        String title = "🚨 [" + severity + "] Alert: " + service;
        String body = message + " | Service: " + service + " | Severity: " + severity;

        System.out.printf("   📤 Sending %s alert via %s:%n", severity, channel.getChannelName());
        channel.send(recipient, title, body, Map.of("priority", "high", "type", "alert"));
    }

    @Override
    public String getNotificationType() { return "Alert"; }
}

class PromotionalNotification extends Notification {
    public PromotionalNotification(NotificationChannel channel) { super(channel); }

    @Override
    public void notify(String recipient, Map<String, Object> data) {
        String campaign = (String) data.getOrDefault("campaign", "default");
        String offer = (String) data.getOrDefault("offer", "");

        String title = "🎉 Special Offer: " + offer;
        String body = offer + " | Campaign: " + campaign + " | Use code: SAVE20";

        System.out.printf("   📤 Sending promo '%s' via %s:%n", campaign, channel.getChannelName());
        channel.send(recipient, title, body, Map.of("priority", "low", "type", "promo"));
    }

    @Override
    public String getNotificationType() { return "Promotional"; }
}

class TransactionalNotification extends Notification {
    public TransactionalNotification(NotificationChannel channel) { super(channel); }

    @Override
    public void notify(String recipient, Map<String, Object> data) {
        String orderId = (String) data.getOrDefault("orderId", "");
        String status = (String) data.getOrDefault("status", "");

        String title = "📦 Order " + orderId + " - " + status;
        String body = "Your order " + orderId + " has been " + status + ".";

        System.out.printf("   📤 Sending transaction update via %s:%n", channel.getChannelName());
        channel.send(recipient, title, body, Map.of("priority", "medium", "type", "transactional"));
    }

    @Override
    public String getNotificationType() { return "Transactional"; }
}

// ==================== EXAMPLE 2: PERSISTENCE LAYER ====================
// Used in: ORMs, Repository pattern, multi-database systems
// Interview Question: Design a storage abstraction layer

// IMPLEMENTATION — storage backends
interface StorageBackend {
    void save(String collection, String id, Map<String, Object> data);
    Map<String, Object> find(String collection, String id);
    List<Map<String, Object>> query(String collection, Map<String, Object> filters);
    void delete(String collection, String id);
    String getBackendName();
}

class PostgreSQLBackend implements StorageBackend {
    @Override
    public void save(String collection, String id, Map<String, Object> data) {
        System.out.printf("   🐘 [PostgreSQL] INSERT INTO %s (id, ...) VALUES ('%s', ...)%n", collection, id);
    }
    @Override
    public Map<String, Object> find(String collection, String id) {
        System.out.printf("   🐘 [PostgreSQL] SELECT * FROM %s WHERE id = '%s'%n", collection, id);
        return Map.of("id", id, "source", "postgresql");
    }
    @Override
    public List<Map<String, Object>> query(String collection, Map<String, Object> filters) {
        System.out.printf("   🐘 [PostgreSQL] SELECT * FROM %s WHERE %s%n", collection, filters);
        return Arrays.asList(Map.of("id", "1"), Map.of("id", "2"));
    }
    @Override
    public void delete(String collection, String id) {
        System.out.printf("   🐘 [PostgreSQL] DELETE FROM %s WHERE id = '%s'%n", collection, id);
    }
    @Override
    public String getBackendName() { return "PostgreSQL"; }
}

class MongoDBBackend implements StorageBackend {
    @Override
    public void save(String collection, String id, Map<String, Object> data) {
        System.out.printf("   🍃 [MongoDB] db.%s.insertOne({_id: '%s', ...})%n", collection, id);
    }
    @Override
    public Map<String, Object> find(String collection, String id) {
        System.out.printf("   🍃 [MongoDB] db.%s.findOne({_id: '%s'})%n", collection, id);
        return Map.of("_id", id, "source", "mongodb");
    }
    @Override
    public List<Map<String, Object>> query(String collection, Map<String, Object> filters) {
        System.out.printf("   🍃 [MongoDB] db.%s.find(%s)%n", collection, filters);
        return Arrays.asList(Map.of("_id", "1"), Map.of("_id", "2"));
    }
    @Override
    public void delete(String collection, String id) {
        System.out.printf("   🍃 [MongoDB] db.%s.deleteOne({_id: '%s'})%n", collection, id);
    }
    @Override
    public String getBackendName() { return "MongoDB"; }
}

class DynamoDBBackend implements StorageBackend {
    @Override
    public void save(String collection, String id, Map<String, Object> data) {
        System.out.printf("   ⚡ [DynamoDB] PutItem: table=%s, pk='%s'%n", collection, id);
    }
    @Override
    public Map<String, Object> find(String collection, String id) {
        System.out.printf("   ⚡ [DynamoDB] GetItem: table=%s, pk='%s'%n", collection, id);
        return Map.of("pk", id, "source", "dynamodb");
    }
    @Override
    public List<Map<String, Object>> query(String collection, Map<String, Object> filters) {
        System.out.printf("   ⚡ [DynamoDB] Query: table=%s, filters=%s%n", collection, filters);
        return Arrays.asList(Map.of("pk", "1"), Map.of("pk", "2"));
    }
    @Override
    public void delete(String collection, String id) {
        System.out.printf("   ⚡ [DynamoDB] DeleteItem: table=%s, pk='%s'%n", collection, id);
    }
    @Override
    public String getBackendName() { return "DynamoDB"; }
}

// ABSTRACTION — domain repositories
abstract class Repository {
    protected StorageBackend storage;
    protected String collectionName;

    public Repository(StorageBackend storage, String collectionName) {
        this.storage = storage;
        this.collectionName = collectionName;
    }

    public abstract void save(Map<String, Object> entity);
    public abstract Map<String, Object> findById(String id);
}

class UserRepository extends Repository {
    public UserRepository(StorageBackend storage) { super(storage, "users"); }

    @Override
    public void save(Map<String, Object> entity) {
        String id = (String) entity.get("id");
        System.out.printf("   👤 UserRepository.save (via %s):%n", storage.getBackendName());
        storage.save(collectionName, id, entity);
    }

    @Override
    public Map<String, Object> findById(String id) {
        System.out.printf("   👤 UserRepository.findById (via %s):%n", storage.getBackendName());
        return storage.find(collectionName, id);
    }
}

class OrderRepository extends Repository {
    public OrderRepository(StorageBackend storage) { super(storage, "orders"); }

    @Override
    public void save(Map<String, Object> entity) {
        String id = (String) entity.get("id");
        System.out.printf("   📦 OrderRepository.save (via %s):%n", storage.getBackendName());
        storage.save(collectionName, id, entity);
    }

    @Override
    public Map<String, Object> findById(String id) {
        System.out.printf("   📦 OrderRepository.findById (via %s):%n", storage.getBackendName());
        return storage.find(collectionName, id);
    }

    public List<Map<String, Object>> findByUserId(String userId) {
        System.out.printf("   📦 OrderRepository.findByUserId (via %s):%n", storage.getBackendName());
        return storage.query(collectionName, Map.of("userId", userId));
    }
}

// ==================== DEMO ====================

public class BridgePattern {
    public static void main(String[] args) {
        System.out.println("========== BRIDGE PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: NOTIFICATION SYSTEM =====
        System.out.println("===== EXAMPLE 1: NOTIFICATION SYSTEM =====");
        System.out.println("(3 message types × 4 channels = 12 combos, only 7 classes!)\n");

        NotificationChannel email = new EmailChannel();
        NotificationChannel sms = new SMSChannel();
        NotificationChannel push = new PushNotificationChannel();
        NotificationChannel slack = new SlackChannel();

        // Alert via different channels
        Map<String, Object> alertData = Map.of("severity", "CRITICAL", "service", "payment-api",
            "message", "Error rate exceeded 5% threshold");
        new AlertNotification(email).notify("oncall@company.com", alertData);
        System.out.println();
        new AlertNotification(slack).notify("#incidents", alertData);
        System.out.println();
        new AlertNotification(sms).notify("+1-555-0123", alertData);

        System.out.println();
        // Promo via different channels
        Map<String, Object> promoData = Map.of("campaign", "Black Friday",
            "offer", "50% off all premium plans");
        new PromotionalNotification(email).notify("alice@example.com", promoData);
        System.out.println();
        new PromotionalNotification(push).notify("device-token-123", promoData);

        System.out.println();
        // Transactional via different channels
        Map<String, Object> txnData = Map.of("orderId", "ORD-12345", "status", "shipped");
        new TransactionalNotification(email).notify("bob@example.com", txnData);
        System.out.println();
        new TransactionalNotification(push).notify("device-token-456", txnData);

        // ===== EXAMPLE 2: PERSISTENCE LAYER =====
        System.out.println("\n\n===== EXAMPLE 2: PERSISTENCE LAYER (Repository Pattern) =====");
        System.out.println("(Same domain logic, different databases)\n");

        // Users in PostgreSQL, Orders in DynamoDB
        UserRepository pgUserRepo = new UserRepository(new PostgreSQLBackend());
        OrderRepository dynamoOrderRepo = new OrderRepository(new DynamoDBBackend());

        pgUserRepo.save(Map.of("id", "user-001", "name", "Alice", "email", "alice@example.com"));
        System.out.println();
        dynamoOrderRepo.save(Map.of("id", "ord-001", "userId", "user-001", "total", 99.99));
        System.out.println();
        pgUserRepo.findById("user-001");
        System.out.println();
        dynamoOrderRepo.findByUserId("user-001");

        // Easy to switch backends!
        System.out.println("\n--- Switching Users to MongoDB ---\n");
        UserRepository mongoUserRepo = new UserRepository(new MongoDBBackend());
        mongoUserRepo.save(Map.of("id", "user-002", "name", "Bob"));
        System.out.println();
        mongoUserRepo.findById("user-002");

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Notification systems (message type × channel)");
        System.out.println("• Persistence/Repository pattern (domain × database)");
        System.out.println("• Cross-platform UI (widget × rendering engine)");
        System.out.println("• Payment processing (payment type × processor)");
    }
}
