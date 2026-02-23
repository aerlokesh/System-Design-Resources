import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * NOTIFICATION SYSTEM - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Multi-channel delivery (Email, SMS, Push)
 * - Priority-based processing
 * - Retry with exponential backoff
 * - Rate limiting per user
 * - Template rendering
 * - User preferences (opt-in/opt-out per channel)
 * 
 * Interview talking points:
 * - Used by: Every major platform (Facebook, Uber, Amazon)
 * - Decouple via message queue (SQS/Kafka)
 * - Idempotency: Don't send duplicate notifications
 * - Rate limiting: Don't spam users
 * - Batching: Group notifications (e.g., "5 new likes")
 * - Priority: OTP > transactional > marketing
 */
class NotificationSystem {

    enum Channel { EMAIL, SMS, PUSH }
    enum Priority { CRITICAL, HIGH, MEDIUM, LOW }

    // ==================== NOTIFICATION ====================
    static class Notification {
        final String id;
        final String userId;
        final String title;
        final String body;
        final Channel channel;
        final Priority priority;
        final long createdAt;
        int retryCount;
        String status; // PENDING, SENT, FAILED, RATE_LIMITED

        Notification(String userId, String title, String body, Channel channel, Priority priority) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.userId = userId;
            this.title = title;
            this.body = body;
            this.channel = channel;
            this.priority = priority;
            this.createdAt = System.currentTimeMillis();
            this.status = "PENDING";
        }

        public String toString() {
            return String.format("[%s] %s->%s: %s (%s) [%s]", id, channel, userId, title, priority, status);
        }
    }

    // ==================== USER PREFERENCES ====================
    static class UserPreferences {
        final String userId;
        final Set<Channel> enabledChannels = EnumSet.allOf(Channel.class);
        boolean doNotDisturb = false;
        int maxNotificationsPerHour = 10;

        UserPreferences(String userId) { this.userId = userId; }

        void disableChannel(Channel ch) { enabledChannels.remove(ch); }
        void enableChannel(Channel ch) { enabledChannels.add(ch); }
        boolean isChannelEnabled(Channel ch) { return enabledChannels.contains(ch); }
    }

    // ==================== CHANNEL SENDERS ====================
    interface NotificationSender {
        boolean send(Notification notification);
        Channel getChannel();
    }

    static class EmailSender implements NotificationSender {
        private final double failureRate;
        private final Random random = new Random();
        private final AtomicInteger sent = new AtomicInteger();

        EmailSender(double failureRate) { this.failureRate = failureRate; }

        public boolean send(Notification n) {
            if (random.nextDouble() < failureRate) return false;
            sent.incrementAndGet();
            return true;
        }
        public Channel getChannel() { return Channel.EMAIL; }
        public int getSentCount() { return sent.get(); }
    }

    static class SMSSender implements NotificationSender {
        private final AtomicInteger sent = new AtomicInteger();
        public boolean send(Notification n) { sent.incrementAndGet(); return true; }
        public Channel getChannel() { return Channel.SMS; }
        public int getSentCount() { return sent.get(); }
    }

    static class PushSender implements NotificationSender {
        private final AtomicInteger sent = new AtomicInteger();
        public boolean send(Notification n) { sent.incrementAndGet(); return true; }
        public Channel getChannel() { return Channel.PUSH; }
        public int getSentCount() { return sent.get(); }
    }

    // ==================== RATE LIMITER ====================
    static class NotificationRateLimiter {
        private final Map<String, List<Long>> userTimestamps = new ConcurrentHashMap<>();
        private final int maxPerHour;

        NotificationRateLimiter(int maxPerHour) { this.maxPerHour = maxPerHour; }

        boolean allowNotification(String userId) {
            long now = System.currentTimeMillis();
            long oneHourAgo = now - 3600_000;
            List<Long> timestamps = userTimestamps.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
            timestamps.removeIf(t -> t < oneHourAgo);
            if (timestamps.size() >= maxPerHour) return false;
            timestamps.add(now);
            return true;
        }

        int getCount(String userId) {
            List<Long> ts = userTimestamps.get(userId);
            return ts != null ? ts.size() : 0;
        }
    }

    // ==================== TEMPLATE ENGINE ====================
    static class TemplateEngine {
        private final Map<String, String> templates = new HashMap<>();

        void registerTemplate(String name, String template) {
            templates.put(name, template);
        }

        String render(String templateName, Map<String, String> vars) {
            String template = templates.getOrDefault(templateName, templateName);
            for (Map.Entry<String, String> e : vars.entrySet()) {
                template = template.replace("{{" + e.getKey() + "}}", e.getValue());
            }
            return template;
        }
    }

    // ==================== NOTIFICATION SERVICE ====================
    static class NotificationService {
        private final Map<Channel, NotificationSender> senders = new HashMap<>();
        private final Map<String, UserPreferences> userPrefs = new ConcurrentHashMap<>();
        private final NotificationRateLimiter rateLimiter;
        private final TemplateEngine templateEngine = new TemplateEngine();
        private final PriorityBlockingQueue<Notification> queue;
        private final List<Notification> sentLog = new CopyOnWriteArrayList<>();
        private final List<Notification> failedLog = new CopyOnWriteArrayList<>();
        private final int maxRetries;

        NotificationService(int maxPerHour, int maxRetries) {
            this.rateLimiter = new NotificationRateLimiter(maxPerHour);
            this.maxRetries = maxRetries;
            this.queue = new PriorityBlockingQueue<>(100,
                    Comparator.comparingInt(n -> n.priority.ordinal()));
        }

        void registerSender(NotificationSender sender) {
            senders.put(sender.getChannel(), sender);
        }

        void setUserPreferences(UserPreferences prefs) {
            userPrefs.put(prefs.userId, prefs);
        }

        String send(Notification notification) {
            // Check user preferences
            UserPreferences prefs = userPrefs.get(notification.userId);
            if (prefs != null) {
                if (prefs.doNotDisturb && notification.priority != Priority.CRITICAL) {
                    notification.status = "DND_BLOCKED";
                    return "Blocked by Do Not Disturb";
                }
                if (!prefs.isChannelEnabled(notification.channel)) {
                    notification.status = "CHANNEL_DISABLED";
                    return "Channel disabled by user";
                }
            }

            // Rate limit (bypass for CRITICAL)
            if (notification.priority != Priority.CRITICAL) {
                if (!rateLimiter.allowNotification(notification.userId)) {
                    notification.status = "RATE_LIMITED";
                    return "Rate limited";
                }
            }

            // Send
            NotificationSender sender = senders.get(notification.channel);
            if (sender == null) {
                notification.status = "NO_SENDER";
                return "No sender for channel";
            }

            return attemptSend(notification, sender);
        }

        private String attemptSend(Notification notification, NotificationSender sender) {
            while (notification.retryCount <= maxRetries) {
                if (sender.send(notification)) {
                    notification.status = "SENT";
                    sentLog.add(notification);
                    return "Sent successfully";
                }
                notification.retryCount++;
                // Exponential backoff would go here in production
            }
            notification.status = "FAILED";
            failedLog.add(notification);
            return "Failed after " + maxRetries + " retries";
        }

        int getSentCount() { return sentLog.size(); }
        int getFailedCount() { return failedLog.size(); }
        TemplateEngine getTemplateEngine() { return templateEngine; }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) {
        System.out.println("=== NOTIFICATION SYSTEM - System Design Demo ===\n");

        // Setup
        NotificationService service = new NotificationService(5, 3);
        EmailSender emailSender = new EmailSender(0.3); // 30% failure rate
        SMSSender smsSender = new SMSSender();
        PushSender pushSender = new PushSender();
        service.registerSender(emailSender);
        service.registerSender(smsSender);
        service.registerSender(pushSender);

        // 1. Basic notifications
        System.out.println("--- 1. Multi-Channel Notifications ---");
        Notification n1 = new Notification("alice", "Order Shipped", "Your order #123 shipped", Channel.EMAIL, Priority.HIGH);
        Notification n2 = new Notification("alice", "OTP Code", "Your OTP: 123456", Channel.SMS, Priority.CRITICAL);
        Notification n3 = new Notification("bob", "New Follower", "Alice followed you", Channel.PUSH, Priority.LOW);

        for (Notification n : new Notification[]{n1, n2, n3}) {
            String result = service.send(n);
            System.out.printf("  %s -> %s%n", n, result);
        }

        // 2. User preferences
        System.out.println("\n--- 2. User Preferences ---");
        UserPreferences alicePrefs = new UserPreferences("alice");
        alicePrefs.disableChannel(Channel.SMS);
        service.setUserPreferences(alicePrefs);

        Notification n4 = new Notification("alice", "Marketing", "50% off sale!", Channel.SMS, Priority.LOW);
        String result4 = service.send(n4);
        System.out.printf("  SMS to alice (disabled): %s -> %s%n", n4.status, result4);

        Notification n5 = new Notification("alice", "Promo", "New features!", Channel.EMAIL, Priority.LOW);
        String result5 = service.send(n5);
        System.out.printf("  Email to alice (enabled): %s -> %s%n", n5.status, result5);

        // 3. Do Not Disturb
        System.out.println("\n--- 3. Do Not Disturb ---");
        UserPreferences bobPrefs = new UserPreferences("bob");
        bobPrefs.doNotDisturb = true;
        service.setUserPreferences(bobPrefs);

        Notification n6 = new Notification("bob", "Like", "Someone liked your post", Channel.PUSH, Priority.LOW);
        Notification n7 = new Notification("bob", "Security Alert", "New login detected", Channel.PUSH, Priority.CRITICAL);

        System.out.printf("  Low priority (DND): %s%n", service.send(n6));
        System.out.printf("  CRITICAL (bypasses DND): %s%n", service.send(n7));

        // 4. Rate limiting
        System.out.println("\n--- 4. Rate Limiting (max 5/hour) ---");
        for (int i = 0; i < 7; i++) {
            Notification n = new Notification("charlie", "Alert " + i, "Body " + i, Channel.PUSH, Priority.MEDIUM);
            String res = service.send(n);
            System.out.printf("  Notification %d: %s%n", i + 1, n.status);
        }

        // 5. Template rendering
        System.out.println("\n--- 5. Template Engine ---");
        TemplateEngine te = service.getTemplateEngine();
        te.registerTemplate("welcome", "Welcome {{name}}! Your account {{email}} is ready.");
        te.registerTemplate("order_shipped", "Hi {{name}}, your order #{{orderId}} has been shipped via {{carrier}}.");

        Map<String, String> vars1 = Map.of("name", "Alice", "email", "alice@test.com");
        Map<String, String> vars2 = Map.of("name", "Bob", "orderId", "5001", "carrier", "FedEx");

        System.out.printf("  Welcome: %s%n", te.render("welcome", vars1));
        System.out.printf("  Shipped: %s%n", te.render("order_shipped", vars2));

        // 6. Retry with failures
        System.out.println("\n--- 6. Retry (email sender has 30%% failure rate) ---");
        int emailAttempts = 20;
        int emailSuccess = 0;
        for (int i = 0; i < emailAttempts; i++) {
            Notification n = new Notification("user" + i, "Test", "Body", Channel.EMAIL, Priority.MEDIUM);
            service.send(n);
            if ("SENT".equals(n.status)) emailSuccess++;
        }
        System.out.printf("  %d/%d emails delivered (with up to 3 retries each)%n", emailSuccess, emailAttempts);

        // Stats
        System.out.println("\n--- 7. Stats ---");
        System.out.printf("  Total sent: %d, Total failed: %d%n", service.getSentCount(), service.getFailedCount());

        System.out.println("\n--- Design Considerations ---");
        System.out.println("  Queue:         Decouple sending via async message queue");
        System.out.println("  Idempotency:   Deduplicate using notification ID");
        System.out.println("  Rate Limiting:  Per-user limits prevent spam");
        System.out.println("  Priority:      CRITICAL > HIGH > MEDIUM > LOW");
        System.out.println("  Batching:      Group low-priority (e.g., '5 new likes')");
        System.out.println("  Analytics:     Track delivery, open, click rates");
    }
}
