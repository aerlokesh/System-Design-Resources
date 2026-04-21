import java.util.*;

// ==================== FACADE PATTERN ====================
// Definition: Provides a unified, simplified interface to a set of interfaces in a subsystem.
// Makes the subsystem easier to use by hiding its complexity.
//
// REAL System Design Interview Examples:
// 1. E-Commerce Order Facade - Orchestrates inventory, payment, shipping, notification
// 2. Cloud Deployment Facade - Simplifies AWS/infra provisioning
// 3. Media Processing Facade - Upload, transcode, thumbnail, CDN in one call
// 4. User Registration Facade - Account creation, email verify, profile setup
// 5. API Gateway - Single entry point hiding microservice complexity
//
// Interview Use Cases:
// - Design E-Commerce System: Single placeOrder() hiding 10+ subsystems
// - Design Video Upload (YouTube): One upload call triggers transcoding pipeline
// - Design Payment Checkout: Facade over fraud, payment, tax, receipt subsystems
// - Design User Onboarding: Single call for multi-step user setup
// - Design Cloud Deployment: One-click deployment hiding IaC complexity

// ==================== EXAMPLE 1: E-COMMERCE ORDER FACADE ====================
// Used in: Amazon, Shopify, any e-commerce checkout
// Interview Question: Design an e-commerce order processing system

class InventorySubsystem {
    public boolean checkAvailability(String productId, int quantity) {
        System.out.printf("   📦 [Inventory] Checking stock for %s (qty: %d)... Available%n", productId, quantity);
        return true;
    }

    public void reserveStock(String productId, int quantity) {
        System.out.printf("   📦 [Inventory] Reserved %d units of %s%n", quantity, productId);
    }

    public void releaseStock(String productId, int quantity) {
        System.out.printf("   📦 [Inventory] Released %d units of %s%n", quantity, productId);
    }
}

class PricingSubsystem {
    public double calculateTotal(String productId, int quantity, String promoCode) {
        double basePrice = 29.99;
        double discount = promoCode != null ? 0.1 : 0.0;
        double total = basePrice * quantity * (1 - discount);
        System.out.printf("   💰 [Pricing] Base: $%.2f × %d - %.0f%% discount = $%.2f%n",
            basePrice, quantity, discount * 100, total);
        return total;
    }

    public double calculateTax(double total, String state) {
        double taxRate = 0.08; // 8%
        double tax = total * taxRate;
        System.out.printf("   💰 [Pricing] Tax for %s: $%.2f (%.0f%%)%n", state, tax, taxRate * 100);
        return tax;
    }
}

class FraudDetectionSubsystem {
    public boolean checkFraud(String customerId, double amount, String paymentMethod) {
        System.out.printf("   🛡️  [Fraud] Checking customer %s, amount $%.2f... CLEAR%n", customerId, amount);
        return true; // true = no fraud
    }
}

class PaymentSubsystem {
    public String processPayment(String customerId, double amount, String paymentMethod) {
        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   💳 [Payment] Charged $%.2f via %s → %s%n", amount, paymentMethod, txnId);
        return txnId;
    }

    public void refund(String transactionId) {
        System.out.printf("   💳 [Payment] Refunded transaction %s%n", transactionId);
    }
}

class ShippingSubsystem {
    public String createShipment(String orderId, String address, String shippingMethod) {
        String trackingId = "TRACK-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   🚚 [Shipping] Created shipment for %s → %s via %s%n", orderId, address, shippingMethod);
        return trackingId;
    }

    public double calculateShippingCost(String address, String method) {
        double cost = "express".equals(method) ? 12.99 : 5.99;
        System.out.printf("   🚚 [Shipping] Cost to %s via %s: $%.2f%n", address, method, cost);
        return cost;
    }
}

class OrderNotificationSubsystem {
    public void sendConfirmation(String customerId, String orderId) {
        System.out.printf("   📧 [Notification] Order confirmation sent to %s for %s%n", customerId, orderId);
    }

    public void sendShippingUpdate(String customerId, String trackingId) {
        System.out.printf("   📧 [Notification] Shipping update sent: %s%n", trackingId);
    }
}

// THE FACADE - Simplifies all the complexity above into one clean interface
class OrderFacade {
    private InventorySubsystem inventory = new InventorySubsystem();
    private PricingSubsystem pricing = new PricingSubsystem();
    private FraudDetectionSubsystem fraud = new FraudDetectionSubsystem();
    private PaymentSubsystem payment = new PaymentSubsystem();
    private ShippingSubsystem shipping = new ShippingSubsystem();
    private OrderNotificationSubsystem notification = new OrderNotificationSubsystem();

    public String placeOrder(String customerId, String productId, int quantity,
                             String promoCode, String paymentMethod,
                             String address, String shippingMethod) {

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("%n🛒 ORDER FACADE: Processing order %s%n", orderId);
        System.out.println("─".repeat(50));

        // Step 1: Check inventory
        if (!inventory.checkAvailability(productId, quantity)) {
            throw new RuntimeException("Product out of stock");
        }

        // Step 2: Calculate pricing
        double subtotal = pricing.calculateTotal(productId, quantity, promoCode);
        double tax = pricing.calculateTax(subtotal, "CA");
        double shippingCost = shipping.calculateShippingCost(address, shippingMethod);
        double total = subtotal + tax + shippingCost;
        System.out.printf("   💵 Total: $%.2f (subtotal) + $%.2f (tax) + $%.2f (shipping) = $%.2f%n",
            subtotal, tax, shippingCost, total);

        // Step 3: Fraud check
        if (!fraud.checkFraud(customerId, total, paymentMethod)) {
            throw new RuntimeException("Fraud detected");
        }

        // Step 4: Reserve inventory
        inventory.reserveStock(productId, quantity);

        // Step 5: Process payment
        String txnId = payment.processPayment(customerId, total, paymentMethod);

        // Step 6: Create shipment
        String trackingId = shipping.createShipment(orderId, address, shippingMethod);

        // Step 7: Send notifications
        notification.sendConfirmation(customerId, orderId);

        System.out.println("─".repeat(50));
        System.out.printf("✅ ORDER %s PLACED SUCCESSFULLY%n", orderId);
        return orderId;
    }
}

// ==================== EXAMPLE 2: MEDIA PROCESSING FACADE ====================
// Used in: YouTube, Netflix, TikTok upload pipeline
// Interview Question: Design a video upload and processing system

class VideoStorageService {
    public String storeRawVideo(String fileName, byte[] data) {
        String fileId = "VID-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   💾 [Storage] Stored raw video: %s (%d bytes) → %s%n", fileName, data.length, fileId);
        return fileId;
    }
}

class TranscodingService {
    public Map<String, String> transcode(String videoId, String[] resolutions) {
        Map<String, String> outputs = new HashMap<>();
        for (String res : resolutions) {
            String outputId = videoId + "-" + res;
            outputs.put(res, outputId);
            System.out.printf("   🎬 [Transcode] %s → %s%n", videoId, outputId);
        }
        return outputs;
    }
}

class ThumbnailService {
    public List<String> generateThumbnails(String videoId, int count) {
        List<String> thumbnails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String thumbId = videoId + "-thumb-" + i;
            thumbnails.add(thumbId);
        }
        System.out.printf("   🖼️  [Thumbnail] Generated %d thumbnails for %s%n", count, videoId);
        return thumbnails;
    }
}

class CDNService {
    public String distributeContent(String fileId) {
        String cdnUrl = "https://cdn.example.com/" + fileId;
        System.out.printf("   🌐 [CDN] Distributed %s → %s%n", fileId, cdnUrl);
        return cdnUrl;
    }
}

class ContentModerationService {
    public boolean moderateContent(String videoId) {
        System.out.printf("   🔍 [Moderation] Scanning %s for policy violations... CLEAN%n", videoId);
        return true;
    }
}

class MediaMetadataService {
    public void saveMetadata(String videoId, String title, String description, List<String> tags) {
        System.out.printf("   📝 [Metadata] Saved metadata for %s: title='%s', tags=%s%n",
            videoId, title, tags);
    }
}

class MediaProcessingFacade {
    private VideoStorageService storage = new VideoStorageService();
    private TranscodingService transcoder = new TranscodingService();
    private ThumbnailService thumbnailService = new ThumbnailService();
    private CDNService cdn = new CDNService();
    private ContentModerationService moderation = new ContentModerationService();
    private MediaMetadataService metadata = new MediaMetadataService();

    public String uploadVideo(String fileName, byte[] data, String title,
                              String description, List<String> tags) {

        System.out.printf("%n🎥 MEDIA FACADE: Processing upload '%s'%n", title);
        System.out.println("─".repeat(50));

        // Step 1: Store raw video
        String videoId = storage.storeRawVideo(fileName, data);

        // Step 2: Content moderation
        if (!moderation.moderateContent(videoId)) {
            throw new RuntimeException("Content policy violation");
        }

        // Step 3: Transcode to multiple resolutions
        Map<String, String> transcoded = transcoder.transcode(videoId,
            new String[]{"1080p", "720p", "480p", "360p"});

        // Step 4: Generate thumbnails
        List<String> thumbnails = thumbnailService.generateThumbnails(videoId, 3);

        // Step 5: Distribute to CDN
        for (String transcodedFile : transcoded.values()) {
            cdn.distributeContent(transcodedFile);
        }

        // Step 6: Save metadata
        metadata.saveMetadata(videoId, title, description, tags);

        System.out.println("─".repeat(50));
        System.out.printf("✅ Video '%s' processed successfully (id: %s)%n", title, videoId);
        return videoId;
    }
}

// ==================== EXAMPLE 3: USER REGISTRATION FACADE ====================
// Used in: Any platform with user signup
// Interview Question: Design a user authentication/registration system

class UserAccountService {
    public String createAccount(String email, String hashedPassword) {
        String userId = "USR-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   👤 [Account] Created account for %s → %s%n", email, userId);
        return userId;
    }
}

class EmailVerificationService {
    public void sendVerificationEmail(String email, String userId) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   ✉️  [Email] Sent verification email to %s (token: %s)%n", email, token);
    }
}

class UserProfileService {
    public void createDefaultProfile(String userId, String displayName) {
        System.out.printf("   📋 [Profile] Created default profile for %s: name='%s'%n", userId, displayName);
    }
}

class UserPreferencesService {
    public void setDefaultPreferences(String userId) {
        System.out.printf("   ⚙️  [Preferences] Set defaults for %s: lang=en, theme=light, notifications=on%n", userId);
    }
}

class WelcomeService {
    public void sendWelcomeEmail(String email, String displayName) {
        System.out.printf("   🎉 [Welcome] Sent welcome email to %s%n", email);
    }

    public void createOnboardingTasks(String userId) {
        System.out.printf("   📝 [Welcome] Created onboarding checklist for %s%n", userId);
    }
}

class UserAnalyticsService {
    public void trackSignup(String userId, String source) {
        System.out.printf("   📊 [Analytics] Tracked signup: %s from %s%n", userId, source);
    }
}

class UserRegistrationFacade {
    private UserAccountService accountService = new UserAccountService();
    private EmailVerificationService emailService = new EmailVerificationService();
    private UserProfileService profileService = new UserProfileService();
    private UserPreferencesService preferencesService = new UserPreferencesService();
    private WelcomeService welcomeService = new WelcomeService();
    private UserAnalyticsService analytics = new UserAnalyticsService();

    public String registerUser(String email, String password, String displayName, String source) {
        System.out.printf("%n👤 REGISTRATION FACADE: Registering '%s'%n", email);
        System.out.println("─".repeat(50));

        // Step 1: Create account
        String hashedPassword = "hashed_" + password; // Simplified
        String userId = accountService.createAccount(email, hashedPassword);

        // Step 2: Send verification email
        emailService.sendVerificationEmail(email, userId);

        // Step 3: Create profile
        profileService.createDefaultProfile(userId, displayName);

        // Step 4: Set default preferences
        preferencesService.setDefaultPreferences(userId);

        // Step 5: Send welcome email
        welcomeService.sendWelcomeEmail(email, displayName);
        welcomeService.createOnboardingTasks(userId);

        // Step 6: Track analytics
        analytics.trackSignup(userId, source);

        System.out.println("─".repeat(50));
        System.out.printf("✅ User '%s' registered successfully (id: %s)%n", email, userId);
        return userId;
    }
}

// ==================== EXAMPLE 4: CLOUD DEPLOYMENT FACADE ====================
// Used in: AWS CloudFormation, Terraform, Kubernetes
// Interview Question: Design a cloud deployment system

class EC2Service {
    public String launchInstance(String instanceType, String ami, int count) {
        String instanceId = "i-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   🖥️  [EC2] Launched %d × %s (AMI: %s) → %s%n", count, instanceType, ami, instanceId);
        return instanceId;
    }
}

class RDSService {
    public String createDatabase(String engine, String instanceClass, String dbName) {
        String dbId = "db-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   🗃️  [RDS] Created %s %s database '%s' → %s%n", engine, instanceClass, dbName, dbId);
        return dbId;
    }
}

class ELBService {
    public String createLoadBalancer(String name, List<String> instanceIds) {
        String lbId = "lb-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   ⚖️  [ELB] Created load balancer '%s' with %d targets → %s%n", name, instanceIds.size(), lbId);
        return lbId;
    }
}

class Route53Service {
    public void createDNSRecord(String domain, String target) {
        System.out.printf("   🌐 [Route53] Created DNS: %s → %s%n", domain, target);
    }
}

class SecurityGroupService {
    public String createSecurityGroup(String name, Map<Integer, String> rules) {
        String sgId = "sg-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("   🔒 [Security] Created security group '%s': %s → %s%n", name, rules, sgId);
        return sgId;
    }
}

class CloudDeploymentFacade {
    private EC2Service ec2 = new EC2Service();
    private RDSService rds = new RDSService();
    private ELBService elb = new ELBService();
    private Route53Service route53 = new Route53Service();
    private SecurityGroupService security = new SecurityGroupService();

    public void deployApplication(String appName, String domain, String env) {
        System.out.printf("%n☁️  DEPLOYMENT FACADE: Deploying '%s' to %s%n", appName, env);
        System.out.println("─".repeat(50));

        // Step 1: Security group
        Map<Integer, String> sgRules = new HashMap<>();
        sgRules.put(80, "0.0.0.0/0");
        sgRules.put(443, "0.0.0.0/0");
        sgRules.put(22, "10.0.0.0/8");
        String sgId = security.createSecurityGroup(appName + "-sg", sgRules);

        // Step 2: Launch EC2 instances
        List<String> instanceIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            instanceIds.add(ec2.launchInstance("t3.medium", "ami-12345678", 1));
        }

        // Step 3: Create database
        String dbId = rds.createDatabase("postgresql", "db.r5.large", appName + "_db");

        // Step 4: Create load balancer
        String lbId = elb.createLoadBalancer(appName + "-lb", instanceIds);

        // Step 5: Configure DNS
        route53.createDNSRecord(domain, lbId);

        System.out.println("─".repeat(50));
        System.out.printf("✅ Application '%s' deployed to %s at %s%n", appName, env, domain);
    }
}

// ==================== DEMO ====================

public class FacadePattern {
    public static void main(String[] args) {
        System.out.println("========== FACADE PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: E-COMMERCE ORDER =====
        System.out.println("===== EXAMPLE 1: E-COMMERCE ORDER FACADE =====");
        OrderFacade orderFacade = new OrderFacade();
        orderFacade.placeOrder(
            "CUST-123", "PROD-456", 2, "SUMMER20",
            "credit_card", "123 Main St, CA 94102", "standard"
        );

        // ===== EXAMPLE 2: MEDIA PROCESSING =====
        System.out.println("\n\n===== EXAMPLE 2: MEDIA PROCESSING FACADE (YouTube-style) =====");
        MediaProcessingFacade mediaFacade = new MediaProcessingFacade();
        mediaFacade.uploadVideo(
            "vacation.mp4", new byte[5000000],
            "My Vacation Video", "Trip to Hawaii",
            Arrays.asList("travel", "hawaii", "vacation")
        );

        // ===== EXAMPLE 3: USER REGISTRATION =====
        System.out.println("\n\n===== EXAMPLE 3: USER REGISTRATION FACADE =====");
        UserRegistrationFacade regFacade = new UserRegistrationFacade();
        regFacade.registerUser("alice@example.com", "securePassword123", "Alice", "google_ads");

        // ===== EXAMPLE 4: CLOUD DEPLOYMENT =====
        System.out.println("\n\n===== EXAMPLE 4: CLOUD DEPLOYMENT FACADE (AWS-style) =====");
        CloudDeploymentFacade deployFacade = new CloudDeploymentFacade();
        deployFacade.deployApplication("my-web-app", "app.example.com", "production");

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• E-commerce checkout (Amazon, Shopify)");
        System.out.println("• Video upload pipeline (YouTube, Netflix, TikTok)");
        System.out.println("• User registration/onboarding flows");
        System.out.println("• Cloud deployment (AWS CloudFormation, Terraform)");
    }
}
