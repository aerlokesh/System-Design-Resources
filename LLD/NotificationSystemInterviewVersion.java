import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Notification System
 * Time to complete: 40-50 minutes
 * Focus: Observer pattern, multiple notification channels
 */

// ==================== Notification Types ====================
enum NotificationType {
    EMAIL, SMS, PUSH
}

// ==================== Notification ====================
class Notification {
    private final String id;
    private final String message;
    private final String recipient;
    private final NotificationType type;

    public Notification(String id, String message, String recipient, NotificationType type) {
        this.id = id;
        this.message = message;
        this.recipient = recipient;
        this.type = type;
    }

    public String getId() { return id; }
    public String getMessage() { return message; }
    public String getRecipient() { return recipient; }
    public NotificationType getType() { return type; }

    @Override
    public String toString() {
        return "[" + type + "] " + message + " → " + recipient;
    }
}

// ==================== Notification Channel Interface ====================
interface NotificationChannel {
    void send(Notification notification);
    NotificationType getType();
}

// ==================== Email Channel ====================
class EmailChannel implements NotificationChannel {
    @Override
    public void send(Notification notification) {
        System.out.println("📧 EMAIL: " + notification.getMessage() + 
                         " to " + notification.getRecipient());
    }

    @Override
    public NotificationType getType() {
        return NotificationType.EMAIL;
    }
}

// ==================== SMS Channel ====================
class SMSChannel implements NotificationChannel {
    @Override
    public void send(Notification notification) {
        System.out.println("📱 SMS: " + notification.getMessage() + 
                         " to " + notification.getRecipient());
    }

    @Override
    public NotificationType getType() {
        return NotificationType.SMS;
    }
}

// ==================== Push Channel ====================
class PushChannel implements NotificationChannel {
    @Override
    public void send(Notification notification) {
        System.out.println("🔔 PUSH: " + notification.getMessage() + 
                         " to " + notification.getRecipient());
    }

    @Override
    public NotificationType getType() {
        return NotificationType.PUSH;
    }
}

// ==================== Notification Service ====================
class NotificationService {
    private final Map<NotificationType, NotificationChannel> channels;
    private final ExecutorService executor;
    private int notificationCounter;

    public NotificationService() {
        this.channels = new HashMap<>();
        this.executor = Executors.newFixedThreadPool(5);
        this.notificationCounter = 1;
    }

    public void registerChannel(NotificationChannel channel) {
        channels.put(channel.getType(), channel);
        System.out.println("✓ Registered channel: " + channel.getType());
    }

    public void sendNotification(String message, String recipient, NotificationType type) {
        String id = "N" + notificationCounter++;
        Notification notification = new Notification(id, message, recipient, type);

        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            System.out.println("✗ No channel for: " + type);
            return;
        }

        // Send asynchronously
        executor.submit(() -> channel.send(notification));
    }

    public void sendToAll(String message, String recipient) {
        for (NotificationType type : channels.keySet()) {
            sendNotification(message, recipient, type);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}

// ==================== Demo ====================
public class NotificationSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Notification System Demo ===\n");

        NotificationService service = new NotificationService();

        // Register channels
        service.registerChannel(new EmailChannel());
        service.registerChannel(new SMSChannel());
        service.registerChannel(new PushChannel());

        // Test 1: Single channel
        System.out.println("\n--- Test 1: Email Notification ---");
        service.sendNotification(
            "Welcome to our platform!",
            "user@example.com",
            NotificationType.EMAIL
        );

        Thread.sleep(500);

        // Test 2: Multiple channels
        System.out.println("\n--- Test 2: SMS and Push ---");
        service.sendNotification(
            "Your order has shipped",
            "+1234567890",
            NotificationType.SMS
        );

        service.sendNotification(
            "New message from support",
            "device-token-123",
            NotificationType.PUSH
        );

        Thread.sleep(500);

        // Test 3: Send to all channels
        System.out.println("\n--- Test 3: Send to All Channels ---");
        service.sendToAll(
            "System maintenance tonight at 2 AM",
            "user@example.com"
        );

        Thread.sleep(1000);

        service.shutdown();
        System.out.println("\n✅ Demo complete!");
    }
}
