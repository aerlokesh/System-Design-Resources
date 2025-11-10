import java.util.*;
import java.time.*;

// ==================== OBSERVER PATTERN ====================
// Definition: Defines a one-to-many dependency between objects so that when one object 
// changes state, all its dependents are notified and updated automatically.
//
// REAL System Design Interview Examples:
// 1. Pub-Sub Messaging System (Kafka, RabbitMQ)
// 2. Event-Driven Microservices
// 3. Real-time Metrics Monitoring (Prometheus, Datadog)
// 4. Distributed Cache Invalidation
// 5. WebSocket/Server-Sent Events for real-time updates
//
// Interview Use Cases:
// - Design YouTube: Video upload completion notifies recommendation system, analytics
// - Design Twitter: Tweet post notifies followers' timelines
// - Design Uber: Driver location updates notify nearby riders
// - Design Stock Trading: Price updates notify all watching clients
// - Design Notification Service: System events trigger multiple notification channels

// ==================== EXAMPLE 1: PUB-SUB MESSAGING SYSTEM ====================
// Used in: Kafka, RabbitMQ, AWS SNS/SQS
// Interview Question: Design a distributed event streaming platform

interface MessageSubscriber {
    void onMessage(String topic, String message, Map<String, String> metadata);
    String getSubscriberId();
    Set<String> getSubscribedTopics();
    void subscribeTo(String topic);
}

class MicroserviceSubscriber implements MessageSubscriber {
    private String serviceId;
    private String serviceName;
    private Set<String> subscribedTopics;
    private Queue<String> messageQueue;

    public MicroserviceSubscriber(String serviceId, String serviceName) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.subscribedTopics = new HashSet<>();
        this.messageQueue = new LinkedList<>();
    }

    @Override
    public void onMessage(String topic, String message, Map<String, String> metadata) {
        String formattedMsg = String.format("[%s] Topic: %s | Message: %s", 
            serviceName, topic, message);
        messageQueue.offer(formattedMsg);
        System.out.println("📨 " + serviceName + " received: " + formattedMsg);
        
        // Simulate processing
        processMessage(topic, message);
    }

    private void processMessage(String topic, String message) {
        // Business logic based on topic
        switch (topic) {
            case "user.signup":
                System.out.println("   → " + serviceName + " processing new user signup");
                break;
            case "order.created":
                System.out.println("   → " + serviceName + " processing new order");
                break;
            case "payment.completed":
                System.out.println("   → " + serviceName + " processing payment confirmation");
                break;
        }
    }

    public void subscribeTo(String topic) {
        subscribedTopics.add(topic);
    }

    @Override
    public String getSubscriberId() {
        return serviceId;
    }

    @Override
    public Set<String> getSubscribedTopics() {
        return subscribedTopics;
    }
}

class MessageBroker {
    private Map<String, List<MessageSubscriber>> topicSubscribers;
    private Queue<String> messageLog;

    public MessageBroker() {
        this.topicSubscribers = new HashMap<>();
        this.messageLog = new LinkedList<>();
    }

    public void subscribe(String topic, MessageSubscriber subscriber) {
        topicSubscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(subscriber);
        subscriber.subscribeTo(topic);
        System.out.println("✅ " + subscriber.getSubscriberId() + " subscribed to topic: " + topic);
    }

    public void unsubscribe(String topic, MessageSubscriber subscriber) {
        List<MessageSubscriber> subscribers = topicSubscribers.get(topic);
        if (subscribers != null) {
            subscribers.remove(subscriber);
            System.out.println("❌ " + subscriber.getSubscriberId() + " unsubscribed from: " + topic);
        }
    }

    public void publish(String topic, String message, Map<String, String> metadata) {
        System.out.println("\n🔔 PUBLISHING to topic '" + topic + "': " + message);
        messageLog.offer(topic + ": " + message);
        
        List<MessageSubscriber> subscribers = topicSubscribers.get(topic);
        if (subscribers != null && !subscribers.isEmpty()) {
            for (MessageSubscriber subscriber : subscribers) {
                subscriber.onMessage(topic, message, metadata);
            }
        } else {
            System.out.println("⚠️  No subscribers for topic: " + topic);
        }
    }
}

// ==================== EXAMPLE 2: DISTRIBUTED CACHE INVALIDATION ====================
// Used in: Redis, Memcached, CDN invalidation
// Interview Question: Design a caching system with cache invalidation

interface CacheInvalidationListener {
    void onCacheInvalidate(String key, String reason);
    String getInstanceId();
}

class CacheNode implements CacheInvalidationListener {
    private String nodeId;
    private String region;
    private Map<String, Object> localCache;

    public CacheNode(String nodeId, String region) {
        this.nodeId = nodeId;
        this.region = region;
        this.localCache = new HashMap<>();
    }

    public void put(String key, Object value) {
        localCache.put(key, value);
        System.out.println("💾 " + nodeId + " cached: " + key);
    }

    @Override
    public void onCacheInvalidate(String key, String reason) {
        if (localCache.containsKey(key)) {
            localCache.remove(key);
            System.out.println("🗑️  " + nodeId + " invalidated cache: " + key + " (Reason: " + reason + ")");
        }
    }

    @Override
    public String getInstanceId() {
        return nodeId + " [" + region + "]";
    }

    public int getCacheSize() {
        return localCache.size();
    }
}

class DistributedCacheCoordinator {
    private List<CacheInvalidationListener> cacheNodes;
    private Map<String, Long> keyVersions;

    public DistributedCacheCoordinator() {
        this.cacheNodes = new ArrayList<>();
        this.keyVersions = new HashMap<>();
    }

    public void registerNode(CacheInvalidationListener node) {
        cacheNodes.add(node);
        System.out.println("✅ Registered cache node: " + node.getInstanceId());
    }

    public void invalidateCache(String key, String reason) {
        System.out.println("\n🔄 CACHE INVALIDATION BROADCAST for key: " + key);
        keyVersions.put(key, System.currentTimeMillis());
        
        for (CacheInvalidationListener node : cacheNodes) {
            node.onCacheInvalidate(key, reason);
        }
    }

    public void invalidatePattern(String pattern, String reason) {
        System.out.println("\n🔄 PATTERN-BASED INVALIDATION: " + pattern);
        // In real system, would match keys by pattern
        invalidateCache("user:" + pattern, reason);
        invalidateCache("session:" + pattern, reason);
    }
}

// ==================== EXAMPLE 3: REAL-TIME METRICS MONITORING ====================
// Used in: Prometheus, Datadog, CloudWatch
// Interview Question: Design a monitoring and alerting system

interface MetricsObserver {
    void onMetricUpdate(String metricName, double value, Map<String, String> tags);
    String getObserverName();
}

class AlertingService implements MetricsObserver {
    private String serviceName;
    private Map<String, Double> thresholds;

    public AlertingService(String serviceName) {
        this.serviceName = serviceName;
        this.thresholds = new HashMap<>();
        thresholds.put("cpu_usage", 80.0);
        thresholds.put("memory_usage", 85.0);
        thresholds.put("error_rate", 5.0);
        thresholds.put("latency_p99", 1000.0);
    }

    @Override
    public void onMetricUpdate(String metricName, double value, Map<String, String> tags) {
        Double threshold = thresholds.get(metricName);
        
        if (threshold != null && value > threshold) {
            triggerAlert(metricName, value, threshold, tags);
        }
    }

    private void triggerAlert(String metric, double value, double threshold, Map<String, String> tags) {
        System.out.printf("🚨 ALERT [%s]: %s = %.2f (threshold: %.2f) | %s%n",
            serviceName, metric, value, threshold, tags);
    }

    @Override
    public String getObserverName() {
        return serviceName;
    }
}

class DashboardService implements MetricsObserver {
    private String dashboardId;
    private Map<String, List<Double>> metricHistory;

    public DashboardService(String dashboardId) {
        this.dashboardId = dashboardId;
        this.metricHistory = new HashMap<>();
    }

    @Override
    public void onMetricUpdate(String metricName, double value, Map<String, String> tags) {
        metricHistory.computeIfAbsent(metricName, k -> new ArrayList<>()).add(value);
        System.out.printf("📊 Dashboard [%s]: Updated %s = %.2f%n", dashboardId, metricName, value);
    }

    @Override
    public String getObserverName() {
        return "Dashboard-" + dashboardId;
    }
}

class TimeSeriesDB implements MetricsObserver {
    private String dbName;
    private int dataPointsStored;

    public TimeSeriesDB(String dbName) {
        this.dbName = dbName;
        this.dataPointsStored = 0;
    }

    @Override
    public void onMetricUpdate(String metricName, double value, Map<String, String> tags) {
        dataPointsStored++;
        System.out.printf("💾 TSDB [%s]: Stored %s = %.2f (Total points: %d)%n",
            dbName, metricName, value, dataPointsStored);
    }

    @Override
    public String getObserverName() {
        return "TSDB-" + dbName;
    }
}

class MetricsCollector {
    private List<MetricsObserver> observers;
    private String serviceName;

    public MetricsCollector(String serviceName) {
        this.serviceName = serviceName;
        this.observers = new ArrayList<>();
    }

    public void registerObserver(MetricsObserver observer) {
        observers.add(observer);
        System.out.println("✅ Registered observer: " + observer.getObserverName());
    }

    public void removeObserver(MetricsObserver observer) {
        observers.remove(observer);
        System.out.println("❌ Removed observer: " + observer.getObserverName());
    }

    public void emitMetric(String metricName, double value) {
        Map<String, String> tags = new HashMap<>();
        tags.put("service", serviceName);
        tags.put("timestamp", LocalDateTime.now().toString());
        
        System.out.println("\n📡 " + serviceName + " emitting metric: " + metricName);
        
        for (MetricsObserver observer : observers) {
            observer.onMetricUpdate(metricName, value, tags);
        }
    }
}

// ==================== EXAMPLE 4: WEBSOCKET REAL-TIME UPDATES ====================
// Used in: WebSocket servers, Server-Sent Events, Long polling
// Interview Question: Design a real-time collaboration system (Google Docs, Figma)

interface RealtimeClient {
    void onDocumentUpdate(String documentId, String userId, String change);
    void onUserJoined(String documentId, String userId);
    void onUserLeft(String documentId, String userId);
    String getClientId();
}

class WebSocketClient implements RealtimeClient {
    private String clientId;
    private String userId;
    private Set<String> activeDocuments;

    public WebSocketClient(String clientId, String userId) {
        this.clientId = clientId;
        this.userId = userId;
        this.activeDocuments = new HashSet<>();
    }

    @Override
    public void onDocumentUpdate(String documentId, String userId, String change) {
        if (!userId.equals(this.userId)) {  // Don't echo back to sender
            System.out.println("🔄 Client " + clientId + " received update on doc " + 
                             documentId + " from " + userId + ": " + change);
        }
    }

    @Override
    public void onUserJoined(String documentId, String userId) {
        System.out.println("👋 Client " + clientId + " notified: " + userId + 
                         " joined document " + documentId);
    }

    @Override
    public void onUserLeft(String documentId, String userId) {
        System.out.println("👋 Client " + clientId + " notified: " + userId + 
                         " left document " + documentId);
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    public void joinDocument(String documentId) {
        activeDocuments.add(documentId);
    }

    public void leaveDocument(String documentId) {
        activeDocuments.remove(documentId);
    }
}

class CollaborationServer {
    private Map<String, List<RealtimeClient>> documentSubscribers;
    private Map<String, Set<String>> activeUsers;

    public CollaborationServer() {
        this.documentSubscribers = new HashMap<>();
        this.activeUsers = new HashMap<>();
    }

    public void subscribeToDocument(String documentId, RealtimeClient client) {
        documentSubscribers.computeIfAbsent(documentId, k -> new ArrayList<>()).add(client);
        activeUsers.computeIfAbsent(documentId, k -> new HashSet<>()).add(client.getClientId());
        
        System.out.println("✅ Client " + client.getClientId() + " subscribed to document: " + documentId);
        
        // Notify others about new user
        notifyUserJoined(documentId, client.getClientId());
    }

    public void unsubscribeFromDocument(String documentId, RealtimeClient client) {
        List<RealtimeClient> subscribers = documentSubscribers.get(documentId);
        if (subscribers != null) {
            subscribers.remove(client);
            activeUsers.get(documentId).remove(client.getClientId());
            notifyUserLeft(documentId, client.getClientId());
        }
    }

    public void broadcastDocumentChange(String documentId, String userId, String change) {
        System.out.println("\n📤 Broadcasting change to document: " + documentId);
        
        List<RealtimeClient> subscribers = documentSubscribers.get(documentId);
        if (subscribers != null) {
            for (RealtimeClient client : subscribers) {
                client.onDocumentUpdate(documentId, userId, change);
            }
        }
    }

    private void notifyUserJoined(String documentId, String userId) {
        List<RealtimeClient> subscribers = documentSubscribers.get(documentId);
        if (subscribers != null) {
            for (RealtimeClient client : subscribers) {
                if (!client.getClientId().equals(userId)) {
                    client.onUserJoined(documentId, userId);
                }
            }
        }
    }

    private void notifyUserLeft(String documentId, String userId) {
        List<RealtimeClient> subscribers = documentSubscribers.get(documentId);
        if (subscribers != null) {
            for (RealtimeClient client : subscribers) {
                client.onUserLeft(documentId, userId);
            }
        }
    }
}

// ==================== EXAMPLE 5: EVENT-DRIVEN MICROSERVICES ====================
// Used in: Event sourcing, CQRS, Domain events
// Interview Question: Design an e-commerce order processing system

interface EventListener {
    void onEvent(DomainEvent event);
    String getServiceName();
}

class DomainEvent {
    private String eventId;
    private String eventType;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;

    public DomainEvent(String eventType, Map<String, Object> payload) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.payload = payload;
        this.timestamp = LocalDateTime.now();
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public LocalDateTime getTimestamp() { return timestamp; }
}

class InventoryService implements EventListener {
    @Override
    public void onEvent(DomainEvent event) {
        if (event.getEventType().equals("ORDER_PLACED")) {
            String productId = (String) event.getPayload().get("productId");
            int quantity = (Integer) event.getPayload().get("quantity");
            System.out.println("📦 Inventory Service: Reserving " + quantity + " units of " + productId);
        } else if (event.getEventType().equals("ORDER_CANCELLED")) {
            System.out.println("📦 Inventory Service: Releasing reserved inventory");
        }
    }

    @Override
    public String getServiceName() {
        return "InventoryService";
    }
}

class PaymentService implements EventListener {
    @Override
    public void onEvent(DomainEvent event) {
        if (event.getEventType().equals("ORDER_PLACED")) {
            String orderId = (String) event.getPayload().get("orderId");
            double amount = (Double) event.getPayload().get("amount");
            System.out.println("💳 Payment Service: Processing payment of $" + amount + " for order " + orderId);
        }
    }

    @Override
    public String getServiceName() {
        return "PaymentService";
    }
}

class NotificationService implements EventListener {
    @Override
    public void onEvent(DomainEvent event) {
        String customerId = (String) event.getPayload().get("customerId");
        
        switch (event.getEventType()) {
            case "ORDER_PLACED":
                System.out.println("📧 Notification Service: Sending order confirmation to " + customerId);
                break;
            case "ORDER_SHIPPED":
                System.out.println("📧 Notification Service: Sending shipment notification to " + customerId);
                break;
            case "PAYMENT_FAILED":
                System.out.println("📧 Notification Service: Sending payment failure alert to " + customerId);
                break;
        }
    }

    @Override
    public String getServiceName() {
        return "NotificationService";
    }
}

class AnalyticsService implements EventListener {
    private int totalOrders = 0;
    private double totalRevenue = 0;

    @Override
    public void onEvent(DomainEvent event) {
        if (event.getEventType().equals("ORDER_PLACED")) {
            totalOrders++;
            double amount = (Double) event.getPayload().get("amount");
            totalRevenue += amount;
            System.out.printf("📊 Analytics Service: Total orders: %d, Revenue: $%.2f%n", 
                totalOrders, totalRevenue);
        }
    }

    @Override
    public String getServiceName() {
        return "AnalyticsService";
    }
}

class EventBus {
    private List<EventListener> listeners;
    private Queue<DomainEvent> eventLog;

    public EventBus() {
        this.listeners = new ArrayList<>();
        this.eventLog = new LinkedList<>();
    }

    public void registerListener(EventListener listener) {
        listeners.add(listener);
        System.out.println("✅ " + listener.getServiceName() + " registered to event bus");
    }

    public void publish(DomainEvent event) {
        System.out.println("\n🎯 EVENT BUS: Publishing " + event.getEventType());
        eventLog.offer(event);
        
        for (EventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}

// ==================== DEMO ====================

public class ObserverPattern {
    public static void main(String[] args) {
        System.out.println("========== OBSERVER PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: PUB-SUB MESSAGING SYSTEM =====
        System.out.println("===== EXAMPLE 1: PUB-SUB MESSAGING (Kafka/RabbitMQ) =====");
        
        MessageBroker broker = new MessageBroker();
        
        MicroserviceSubscriber emailService = new MicroserviceSubscriber("email-svc-01", "EmailService");
        MicroserviceSubscriber smsService = new MicroserviceSubscriber("sms-svc-01", "SMSService");
        MicroserviceSubscriber analyticsService = new MicroserviceSubscriber("analytics-svc-01", "AnalyticsService");
        
        broker.subscribe("user.signup", emailService);
        broker.subscribe("user.signup", analyticsService);
        broker.subscribe("order.created", emailService);
        broker.subscribe("order.created", smsService);
        broker.subscribe("order.created", analyticsService);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0");
        
        broker.publish("user.signup", "User alice@example.com registered", metadata);
        broker.publish("order.created", "Order #12345 created for $99.99", metadata);

        // ===== EXAMPLE 2: DISTRIBUTED CACHE INVALIDATION =====
        System.out.println("\n\n===== EXAMPLE 2: DISTRIBUTED CACHE INVALIDATION =====");
        
        DistributedCacheCoordinator coordinator = new DistributedCacheCoordinator();
        
        CacheNode usWest = new CacheNode("cache-us-west-1", "us-west-2");
        CacheNode usEast = new CacheNode("cache-us-east-1", "us-east-1");
        CacheNode europe = new CacheNode("cache-eu-1", "eu-west-1");
        
        coordinator.registerNode(usWest);
        coordinator.registerNode(usEast);
        coordinator.registerNode(europe);
        
        // Simulate caching
        usWest.put("user:1001", "User data");
        usEast.put("user:1001", "User data");
        europe.put("user:1001", "User data");
        
        System.out.println("\nCache sizes: US-West=" + usWest.getCacheSize() + 
                         ", US-East=" + usEast.getCacheSize() + ", Europe=" + europe.getCacheSize());
        
        // User updates profile - invalidate cache across all regions
        coordinator.invalidateCache("user:1001", "Profile updated");
        
        System.out.println("\nCache sizes after invalidation: US-West=" + usWest.getCacheSize() + 
                         ", US-East=" + usEast.getCacheSize() + ", Europe=" + europe.getCacheSize());

        // ===== EXAMPLE 3: REAL-TIME METRICS MONITORING =====
        System.out.println("\n\n===== EXAMPLE 3: REAL-TIME METRICS MONITORING (Prometheus/Datadog) =====");
        
        MetricsCollector collector = new MetricsCollector("api-server-01");
        
        AlertingService alerting = new AlertingService("PagerDuty");
        DashboardService dashboard = new DashboardService("grafana-01");
        TimeSeriesDB tsdb = new TimeSeriesDB("prometheus");
        
        collector.registerObserver(alerting);
        collector.registerObserver(dashboard);
        collector.registerObserver(tsdb);
        
        collector.emitMetric("cpu_usage", 75.5);
        collector.emitMetric("memory_usage", 90.0);  // Will trigger alert
        collector.emitMetric("error_rate", 2.5);
        collector.emitMetric("latency_p99", 1200.0);  // Will trigger alert

        // ===== EXAMPLE 4: WEBSOCKET REAL-TIME COLLABORATION =====
        System.out.println("\n\n===== EXAMPLE 4: WEBSOCKET REAL-TIME COLLABORATION (Google Docs) =====");
        
        CollaborationServer collab = new CollaborationServer();
        
        WebSocketClient client1 = new WebSocketClient("ws-001", "alice");
        WebSocketClient client2 = new WebSocketClient("ws-002", "bob");
        WebSocketClient client3 = new WebSocketClient("ws-003", "charlie");
        
        String documentId = "doc-12345";
        
        collab.subscribeToDocument(documentId, client1);
        collab.subscribeToDocument(documentId, client2);
        
        collab.broadcastDocumentChange(documentId, "alice", "Added paragraph to introduction");
        
        collab.subscribeToDocument(documentId, client3);
        
        collab.broadcastDocumentChange(documentId, "bob", "Fixed typo in section 2");
        
        collab.unsubscribeFromDocument(documentId, client2);

        // ===== EXAMPLE 5: EVENT-DRIVEN MICROSERVICES =====
        System.out.println("\n\n===== EXAMPLE 5: EVENT-DRIVEN MICROSERVICES (E-Commerce) =====");
        
        EventBus eventBus = new EventBus();
        
        InventoryService inventory = new InventoryService();
        PaymentService payment = new PaymentService();
        NotificationService notification = new NotificationService();
        AnalyticsService analytics = new AnalyticsService();
        
        eventBus.registerListener(inventory);
        eventBus.registerListener(payment);
        eventBus.registerListener(notification);
        eventBus.registerListener(analytics);
        
        // Simulate order placement
        Map<String, Object> orderPayload = new HashMap<>();
        orderPayload.put("orderId", "ORD-001");
        orderPayload.put("customerId", "CUST-123");
        orderPayload.put("productId", "PROD-456");
        orderPayload.put("quantity", 2);
        orderPayload.put("amount", 199.99);
        
        eventBus.publish(new DomainEvent("ORDER_PLACED", orderPayload));
        
        // Simulate order shipment
        Map<String, Object> shipPayload = new HashMap<>();
        shipPayload.put("orderId", "ORD-001");
        shipPayload.put("customerId", "CUST-123");
        shipPayload.put("trackingNumber", "TRK-789");
        
        eventBus.publish(new DomainEvent("ORDER_SHIPPED", shipPayload));

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Kafka/RabbitMQ for message distribution");
        System.out.println("• Redis/Memcached for cache invalidation");
        System.out.println("• Prometheus/Datadog for metrics monitoring");
        System.out.println("• WebSocket servers for real-time collaboration");
        System.out.println("• Event-driven microservices architecture");
    }
}
