import java.util.*;
import java.time.*;

// ==================== Enums ====================
// Why Enums? Type safety, prevents invalid notification types/priorities

enum NotificationType {
    EMAIL,
    SMS,
    PUSH_NOTIFICATION,
    IN_APP,
    SLACK,
    WEBHOOK
    // Easy to add new channels (Open/Closed Principle)
}

enum NotificationPriority {
    LOW(1),         // Promotional, marketing
    MEDIUM(2),      // Updates, reminders
    HIGH(3),        // Important alerts
    URGENT(4);      // Critical, immediate action needed

    private final int level;

    NotificationPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}

enum NotificationStatus {
    PENDING,        // Queued, not sent yet
    SENT,           // Successfully delivered
    FAILED,         // Delivery failed
    RETRYING,       // Retry in progress
    CANCELLED       // User cancelled
}

// ==================== User ====================
// Represents a user who can receive notifications

class User {
    private String userId;
    private String name;
    private String email;
    private String phoneNumber;
    private String deviceToken;  // For push notifications
    private NotificationPreferences preferences;

    public User(String userId, String name, String email, String phoneNumber) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.preferences = new NotificationPreferences();
    }

    // Getters
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getDeviceToken() { return deviceToken; }
    public NotificationPreferences getPreferences() { return preferences; }

    // Setters
    public void setDeviceToken(String token) { this.deviceToken = token; }

    @Override
    public String toString() {
        return "User[" + userId + ", " + name + "]";
    }
}

// ==================== Notification Preferences ====================
// User's notification settings

class NotificationPreferences {
    // Which channels user wants to receive notifications on
    private Set<NotificationType> enabledChannels;
    private boolean doNotDisturb;
    private LocalTime quietHoursStart;  // Don't send notifications during these hours
    private LocalTime quietHoursEnd;

    public NotificationPreferences() {
        this.enabledChannels = new HashSet<>();
        // Default: All channels enabled
        enabledChannels.add(NotificationType.EMAIL);
        enabledChannels.add(NotificationType.SMS);
        enabledChannels.add(NotificationType.PUSH_NOTIFICATION);
        enabledChannels.add(NotificationType.IN_APP);
        this.doNotDisturb = false;
    }

    public boolean isChannelEnabled(NotificationType type) {
        return enabledChannels.contains(type) && !doNotDisturb;
    }

    public void enableChannel(NotificationType type) {
        enabledChannels.add(type);
    }

    public void disableChannel(NotificationType type) {
        enabledChannels.remove(type);
    }

    public boolean isDuringQuietHours() {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        return now.isAfter(quietHoursStart) && now.isBefore(quietHoursEnd);
    }

    public void setQuietHours(LocalTime start, LocalTime end) {
        this.quietHoursStart = start;
        this.quietHoursEnd = end;
    }

    public void setDoNotDisturb(boolean dnd) {
        this.doNotDisturb = dnd;
    }
}

// ==================== Notification ====================
// Core notification entity

class Notification {
    private String notificationId;
    private User recipient;
    private String subject;
    private String message;
    private NotificationType type;
    private NotificationPriority priority;
    private NotificationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private int retryCount;
    private int maxRetries;

    public Notification(String notificationId, User recipient, String subject, 
                       String message, NotificationType type, NotificationPriority priority) {
        this.notificationId = notificationId;
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.type = type;
        this.priority = priority;
        this.status = NotificationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    // Getters
    public String getNotificationId() { return notificationId; }
    public User getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public NotificationType getType() { return type; }
    public NotificationPriority getPriority() { return priority; }
    public NotificationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }

    // Setters
    public void setStatus(NotificationStatus status) { this.status = status; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public void incrementRetryCount() { this.retryCount++; }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    @Override
    public String toString() {
        return String.format("Notification[%s, %s, %s, %s]",
            notificationId, type, priority, status);
    }
}

// ==================== Notification Channel (Strategy Pattern) ====================
// Why Strategy Pattern? Different sending mechanisms for each channel
// Benefits: Interchangeable, easy to add new channels

interface NotificationChannel {
    boolean send(Notification notification);
    NotificationType getChannelType();
}

// Email channel
class EmailChannel implements NotificationChannel {
    @Override
    public boolean send(Notification notification) {
        System.out.println("📧 Sending EMAIL to: " + notification.getRecipient().getEmail());
        System.out.println("   Subject: " + notification.getSubject());
        System.out.println("   Message: " + notification.getMessage());
        
        // Simulate email sending
        try {
            Thread.sleep(100);  // Network delay
            
            // Simulate 95% success rate
            if (Math.random() < 0.95) {
                System.out.println("   ✅ Email sent successfully");
                return true;
            } else {
                System.out.println("   ❌ Email failed to send");
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Override
    public NotificationType getChannelType() {
        return NotificationType.EMAIL;
    }
}

// SMS channel
class SmsChannel implements NotificationChannel {
    @Override
    public boolean send(Notification notification) {
        System.out.println("📱 Sending SMS to: " + notification.getRecipient().getPhoneNumber());
        System.out.println("   Message: " + notification.getMessage());
        
        // Simulate SMS sending (Twilio, AWS SNS, etc.)
        try {
            Thread.sleep(50);
            
            // Simulate 98% success rate
            if (Math.random() < 0.98) {
                System.out.println("   ✅ SMS sent successfully");
                return true;
            } else {
                System.out.println("   ❌ SMS failed to send");
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Override
    public NotificationType getChannelType() {
        return NotificationType.SMS;
    }
}

// Push notification channel
class PushNotificationChannel implements NotificationChannel {
    @Override
    public boolean send(Notification notification) {
        System.out.println("🔔 Sending PUSH to device: " + 
                         notification.getRecipient().getDeviceToken());
        System.out.println("   Title: " + notification.getSubject());
        System.out.println("   Body: " + notification.getMessage());
        
        // Simulate push notification (FCM, APNs)
        try {
            Thread.sleep(30);
            
            if (Math.random() < 0.99) {
                System.out.println("   ✅ Push notification sent");
                return true;
            } else {
                System.out.println("   ❌ Push notification failed");
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Override
    public NotificationType getChannelType() {
        return NotificationType.PUSH_NOTIFICATION;
    }
}

// In-app notification channel
class InAppChannel implements NotificationChannel {
    @Override
    public boolean send(Notification notification) {
        System.out.println("📬 Creating IN-APP notification for: " + 
                         notification.getRecipient().getUserId());
        System.out.println("   Message: " + notification.getMessage());
        
        // In real app: Store in database, user sees when they open app
        System.out.println("   ✅ In-app notification created");
        return true;  // Always succeeds (stored in database)
    }

    @Override
    public NotificationType getChannelType() {
        return NotificationType.IN_APP;
    }
}

// ==================== Notification Template ====================
// Template for reusable notification content

class NotificationTemplate {
    private String templateId;
    private String subjectTemplate;
    private String messageTemplate;
    private NotificationType defaultChannel;

    public NotificationTemplate(String templateId, String subjectTemplate, 
                               String messageTemplate, NotificationType defaultChannel) {
        this.templateId = templateId;
        this.subjectTemplate = subjectTemplate;
        this.messageTemplate = messageTemplate;
        this.defaultChannel = defaultChannel;
    }

    // Replace placeholders with actual values
    public String renderSubject(Map<String, String> params) {
        String rendered = subjectTemplate;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    public String renderMessage(Map<String, String> params) {
        String rendered = messageTemplate;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    public String getTemplateId() { return templateId; }
    public NotificationType getDefaultChannel() { return defaultChannel; }
}

// ==================== Notification Sender (Strategy Context) ====================
// Manages sending through appropriate channel

class NotificationSender {
    private Map<NotificationType, NotificationChannel> channels;

    public NotificationSender() {
        this.channels = new HashMap<>();
        registerChannels();
    }

    private void registerChannels() {
        // Register all available channels
        channels.put(NotificationType.EMAIL, new EmailChannel());
        channels.put(NotificationType.SMS, new SmsChannel());
        channels.put(NotificationType.PUSH_NOTIFICATION, new PushNotificationChannel());
        channels.put(NotificationType.IN_APP, new InAppChannel());
    }

    public boolean send(Notification notification) {
        // Check user preferences
        if (!notification.getRecipient().getPreferences()
                .isChannelEnabled(notification.getType())) {
            System.out.println("Channel " + notification.getType() + 
                             " disabled for user " + notification.getRecipient().getUserId());
            return false;
        }

        // Check quiet hours (except URGENT priority)
        if (notification.getPriority() != NotificationPriority.URGENT &&
            notification.getRecipient().getPreferences().isDuringQuietHours()) {
            System.out.println("During quiet hours, notification queued for later");
            return false;
        }

        // Get appropriate channel
        NotificationChannel channel = channels.get(notification.getType());
        if (channel == null) {
            System.out.println("Channel " + notification.getType() + " not available");
            return false;
        }

        // Send notification
        boolean success = channel.send(notification);
        
        if (success) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
        }
        
        return success;
    }
}

// ==================== Notification Queue (Priority Queue) ====================
// Why Priority Queue? Handle urgent notifications first
// Benefits: Automatic ordering by priority

class NotificationQueue {
    // Priority queue ordered by priority and timestamp
    private PriorityQueue<Notification> queue;

    public NotificationQueue() {
        this.queue = new PriorityQueue<>((n1, n2) -> {
            // First by priority (higher priority first)
            int priorityCompare = Integer.compare(
                n2.getPriority().getLevel(),
                n1.getPriority().getLevel()
            );
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // Then by timestamp (older first)
            return n1.getCreatedAt().compareTo(n2.getCreatedAt());
        });
    }

    public synchronized void enqueue(Notification notification) {
        queue.offer(notification);
        System.out.println("Queued: " + notification);
    }

    public synchronized Notification dequeue() {
        return queue.poll();
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}

// ==================== Notification Processor (Worker) ====================
// Background worker that processes notification queue

class NotificationProcessor implements Runnable {
    private NotificationQueue queue;
    private NotificationSender sender;
    private boolean running;

    public NotificationProcessor(NotificationQueue queue, NotificationSender sender) {
        this.queue = queue;
        this.sender = sender;
        this.running = true;
    }

    @Override
    public void run() {
        System.out.println("Notification Processor started");
        
        while (running) {
            if (!queue.isEmpty()) {
                Notification notification = queue.dequeue();
                
                System.out.println("\n--- Processing: " + notification + " ---");
                
                boolean success = sender.send(notification);
                
                // Handle failure with retry
                if (!success && notification.canRetry()) {
                    notification.incrementRetryCount();
                    notification.setStatus(NotificationStatus.RETRYING);
                    
                    System.out.println("Retry " + notification.getRetryCount() + 
                                     " of " + notification.getMaxRetries());
                    
                    // Re-queue with exponential backoff
                    try {
                        Thread.sleep(1000 * notification.getRetryCount());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    queue.enqueue(notification);
                }
            } else {
                // Queue empty, wait
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        
        System.out.println("Notification Processor stopped");
    }

    public void stop() {
        this.running = false;
    }
}

// ==================== Notification Service (Facade + Singleton) ====================
// Why Facade? Simple interface to complex notification system
// Why Singleton? Single notification service for entire application

class NotificationService {
    private static NotificationService instance;
    private NotificationQueue queue;
    private NotificationSender sender;
    private NotificationProcessor processor;
    private Thread processorThread;
    private Map<String, NotificationTemplate> templates;
    private int notificationCounter;

    private NotificationService() {
        this.queue = new NotificationQueue();
        this.sender = new NotificationSender();
        this.processor = new NotificationProcessor(queue, sender);
        this.templates = new HashMap<>();
        this.notificationCounter = 0;
        
        // Start background processor
        this.processorThread = new Thread(processor);
        this.processorThread.start();
        
        // Load templates
        loadTemplates();
    }

    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    private void loadTemplates() {
        // Welcome email template
        templates.put("welcome_email", new NotificationTemplate(
            "welcome_email",
            "Welcome to {{app_name}}, {{user_name}}!",
            "Hi {{user_name}},\n\nWelcome to {{app_name}}! We're excited to have you.",
            NotificationType.EMAIL
        ));

        // Password reset template
        templates.put("password_reset", new NotificationTemplate(
            "password_reset",
            "Password Reset Request",
            "Hi {{user_name}},\n\nClick here to reset your password: {{reset_link}}",
            NotificationType.EMAIL
        ));

        // Order confirmation template
        templates.put("order_confirm", new NotificationTemplate(
            "order_confirm",
            "Order Confirmation #{{order_id}}",
            "Your order #{{order_id}} has been confirmed. Total: ${{amount}}",
            NotificationType.EMAIL
        ));
    }

    // Send notification immediately
    public void sendNotification(User recipient, String subject, String message,
                                NotificationType type, NotificationPriority priority) {
        String notifId = "NOTIF-" + (++notificationCounter);
        Notification notification = new Notification(
            notifId, recipient, subject, message, type, priority
        );
        
        System.out.println("\nCreated: " + notification);
        queue.enqueue(notification);
    }

    // Send notification using template
    public void sendFromTemplate(User recipient, String templateId, 
                                Map<String, String> params, NotificationPriority priority) {
        NotificationTemplate template = templates.get(templateId);
        if (template == null) {
            System.out.println("Template not found: " + templateId);
            return;
        }

        String subject = template.renderSubject(params);
        String message = template.renderMessage(params);
        
        sendNotification(recipient, subject, message, 
                        template.getDefaultChannel(), priority);
    }

    // Send to multiple users (bulk notification)
    public void sendBulkNotification(List<User> recipients, String subject, 
                                    String message, NotificationType type, 
                                    NotificationPriority priority) {
        System.out.println("\nSending bulk notification to " + recipients.size() + " users");
        
        for (User recipient : recipients) {
            sendNotification(recipient, subject, message, type, priority);
        }
    }

    // Get queue status
    public void showQueueStatus() {
        System.out.println("\nQueue Status: " + queue.size() + " notifications pending");
    }

    // Shutdown gracefully
    public void shutdown() {
        System.out.println("\nShutting down notification service...");
        processor.stop();
        try {
            processorThread.join(5000);  // Wait max 5 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Notification service stopped");
    }
}

// ==================== Notification Builder (Builder Pattern) ====================
// Why Builder? Complex object construction with many optional parameters
// Benefits: Fluent API, readable, handles optional parameters

class NotificationBuilder {
    private User recipient;
    private String subject;
    private String message;
    private NotificationType type = NotificationType.EMAIL;  // Default
    private NotificationPriority priority = NotificationPriority.MEDIUM;  // Default

    public NotificationBuilder setRecipient(User recipient) {
        this.recipient = recipient;
        return this;
    }

    public NotificationBuilder setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public NotificationBuilder setMessage(String message) {
        this.message = message;
        return this;
    }

    public NotificationBuilder setType(NotificationType type) {
        this.type = type;
        return this;
    }

    public NotificationBuilder setPriority(NotificationPriority priority) {
        this.priority = priority;
        return this;
    }

    public void send() {
        NotificationService.getInstance().sendNotification(
            recipient, subject, message, type, priority
        );
    }
}

// ==================== Notification Observer (Observer Pattern) ====================
// Why Observer? Notify interested parties when notification sent
// Benefits: Loose coupling, multiple observers

interface NotificationObserver {
    void onNotificationSent(Notification notification);
    void onNotificationFailed(Notification notification);
}

// Analytics observer
class AnalyticsObserver implements NotificationObserver {
    @Override
    public void onNotificationSent(Notification notification) {
        System.out.println("[Analytics] Notification sent: " + notification.getType());
        // In real system: Track metrics, update dashboard
    }

    @Override
    public void onNotificationFailed(Notification notification) {
        System.out.println("[Analytics] Notification failed: " + notification.getType());
        // In real system: Alert ops team, track failure rate
    }
}

// Audit observer
class AuditObserver implements NotificationObserver {
    @Override
    public void onNotificationSent(Notification notification) {
        System.out.println("[Audit] Logged: " + notification);
        // In real system: Write to audit log
    }

    @Override
    public void onNotificationFailed(Notification notification) {
        System.out.println("[Audit] Failed notification logged");
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates notification system with various scenarios

public class NotificationSystem {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== Notification System Demo ==========\n");
        
        NotificationService service = NotificationService.getInstance();
        
        // Create test users
        User alice = new User("user_001", "Alice", "alice@example.com", "+1-555-0001");
        alice.setDeviceToken("device_abc123");
        
        User bob = new User("user_002", "Bob", "bob@example.com", "+1-555-0002");
        bob.setDeviceToken("device_def456");
        
        // ===== Demo 1: Simple Notifications =====
        System.out.println("===== Demo 1: Simple Notifications =====");
        
        service.sendNotification(
            alice,
            "Welcome!",
            "Welcome to our platform, Alice!",
            NotificationType.EMAIL,
            NotificationPriority.LOW
        );
        
        service.sendNotification(
            bob,
            "Security Alert",
            "New login detected from unknown device",
            NotificationType.SMS,
            NotificationPriority.HIGH
        );
        
        // Wait for processing
        Thread.sleep(2000);
        
        // ===== Demo 2: Priority Handling =====
        System.out.println("\n\n===== Demo 2: Priority Handling =====");
        
        // Send low priority first
        service.sendNotification(
            alice, "Newsletter", "Check out our latest updates",
            NotificationType.EMAIL, NotificationPriority.LOW
        );
        
        // Send urgent after (should process first!)
        service.sendNotification(
            alice, "URGENT: Account Security",
            "Suspicious activity detected",
            NotificationType.SMS, NotificationPriority.URGENT
        );
        
        service.showQueueStatus();
        Thread.sleep(2000);
        
        // ===== Demo 3: Template-Based Notifications =====
        System.out.println("\n\n===== Demo 3: Template-Based Notifications =====");
        
        Map<String, String> welcomeParams = new HashMap<>();
        welcomeParams.put("app_name", "NotificationApp");
        welcomeParams.put("user_name", "Alice");
        
        service.sendFromTemplate(alice, "welcome_email", welcomeParams, 
                                NotificationPriority.MEDIUM);
        
        Map<String, String> orderParams = new HashMap<>();
        orderParams.put("order_id", "12345");
        orderParams.put("amount", "99.99");
        
        service.sendFromTemplate(bob, "order_confirm", orderParams, 
                                NotificationPriority.HIGH);
        
        Thread.sleep(2000);
        
        // ===== Demo 4: Multiple Channels =====
        System.out.println("\n\n===== Demo 4: Multiple Channels =====");
        
        // Send same notification via multiple channels
        String criticalMsg = "Your account password was changed";
        
        service.sendNotification(alice, "Security Alert", criticalMsg,
                                NotificationType.EMAIL, NotificationPriority.URGENT);
        
        service.sendNotification(alice, "Security Alert", criticalMsg,
                                NotificationType.SMS, NotificationPriority.URGENT);
        
        service.sendNotification(alice, "Security Alert", criticalMsg,
                                NotificationType.PUSH_NOTIFICATION, NotificationPriority.URGENT);
        
        Thread.sleep(2000);
        
        // ===== Demo 5: User Preferences =====
        System.out.println("\n\n===== Demo 5: User Preferences =====");
        
        // Bob disables SMS
        bob.getPreferences().disableChannel(NotificationType.SMS);
        
        service.sendNotification(bob, "Test", "This should not be sent",
                                NotificationType.SMS, NotificationPriority.MEDIUM);
        
        // Bob enables email
        service.sendNotification(bob, "Test", "This should be sent",
                                NotificationType.EMAIL, NotificationPriority.MEDIUM);
        
        Thread.sleep(2000);
        
        // ===== Demo 6: Bulk Notifications =====
        System.out.println("\n\n===== Demo 6: Bulk Notifications =====");
        
        List<User> allUsers = Arrays.asList(alice, bob);
        service.sendBulkNotification(
            allUsers,
            "System Maintenance",
            "Scheduled maintenance on Sunday 2AM-4AM",
            NotificationType.EMAIL,
            NotificationPriority.LOW
        );
        
        Thread.sleep(2000);
        
        // ===== Demo 7: Builder Pattern =====
        System.out.println("\n\n===== Demo 7: Builder Pattern =====");
        
        new NotificationBuilder()
            .setRecipient(alice)
            .setSubject("Builder Test")
            .setMessage("Notification created with builder pattern")
            .setType(NotificationType.PUSH_NOTIFICATION)
            .setPriority(NotificationPriority.HIGH)
            .send();
        
        Thread.sleep(2000);
        
        // ===== Show Final Status =====
        service.showQueueStatus();
        
        // ===== Cleanup =====
        service.shutdown();
        
        System.out.println("\n========== Demo Complete ==========");
    }
}
