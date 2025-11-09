import java.util.*;
import java.time.*;

// ==================== CHAIN OF RESPONSIBILITY PATTERN ====================
// Definition: Allows passing requests along a chain of handlers. Each handler decides
// whether to process the request or pass it to the next handler in the chain.
//
// Real-World Examples:
// 1. Email Spam Filtering - Multiple filters check for spam
// 2. Request Processing - Web server middleware
// 3. Support Ticket System - Different support levels
// 4. Logging System - Different log levels
// 5. ATM Money Dispenser - Different denomination handlers
//
// When to Use:
// - Multiple objects can handle a request
// - Handler isn't known in advance
// - Set of handlers should be specified dynamically
// - Request should be handled by one of several objects
//
// Benefits:
// 1. Decouples sender from receiver
// 2. Flexible assignment of responsibilities
// 3. Easy to add/remove handlers
// 4. Single Responsibility Principle - each handler does one thing

// ==================== EXAMPLE 1: EMAIL SPAM FILTER SYSTEM ====================

interface SpamFilter {
    void setNext(SpamFilter next);
    boolean filter(Email email);
}

class Email {
    private String subject;
    private String body;
    private String sender;
    private int attachmentCount;
    private long attachmentSize;

    public Email(String subject, String body, String sender, 
                 int attachmentCount, long attachmentSize) {
        this.subject = subject;
        this.body = body;
        this.sender = sender;
        this.attachmentCount = attachmentCount;
        this.attachmentSize = attachmentSize;
    }

    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getSender() { return sender; }
    public int getAttachmentCount() { return attachmentCount; }
    public long getAttachmentSize() { return attachmentSize; }

    @Override
    public String toString() {
        return "From: " + sender + " | Subject: " + subject;
    }
}

abstract class BaseSpamFilter implements SpamFilter {
    private SpamFilter next;

    @Override
    public void setNext(SpamFilter next) {
        this.next = next;
    }

    protected boolean checkNext(Email email) {
        if (next == null) {
            return false;  // Passed all filters
        }
        return next.filter(email);
    }
}

// Filter 1: Keyword Filter
class KeywordSpamFilter extends BaseSpamFilter {
    private Set<String> spamKeywords = new HashSet<>(Arrays.asList(
        "lottery", "winner", "free money", "click here", "urgent", 
        "congratulations", "act now", "limited time"
    ));

    @Override
    public boolean filter(Email email) {
        String content = (email.getSubject() + " " + email.getBody()).toLowerCase();
        
        for (String keyword : spamKeywords) {
            if (content.contains(keyword)) {
                System.out.println("❌ SPAM (Keyword): " + email);
                return true;
            }
        }
        
        System.out.println("✅ Passed keyword filter: " + email);
        return checkNext(email);
    }
}

// Filter 2: Sender Domain Filter
class DomainSpamFilter extends BaseSpamFilter {
    private Set<String> blockedDomains = new HashSet<>(Arrays.asList(
        "spam.com", "scam.net", "fake.org", "phishing.com"
    ));

    @Override
    public boolean filter(Email email) {
        String domain = email.getSender().substring(email.getSender().indexOf('@') + 1);
        
        if (blockedDomains.contains(domain)) {
            System.out.println("❌ SPAM (Domain): " + email);
            return true;
        }
        
        System.out.println("✅ Passed domain filter: " + email);
        return checkNext(email);
    }
}

// Filter 3: Attachment Filter
class AttachmentSpamFilter extends BaseSpamFilter {
    private static final int MAX_ATTACHMENTS = 5;
    private static final long MAX_SIZE_MB = 25;

    @Override
    public boolean filter(Email email) {
        if (email.getAttachmentCount() > MAX_ATTACHMENTS) {
            System.out.println("❌ SPAM (Too many attachments): " + email);
            return true;
        }
        
        long sizeMB = email.getAttachmentSize() / (1024 * 1024);
        if (sizeMB > MAX_SIZE_MB) {
            System.out.println("❌ SPAM (Attachments too large): " + email);
            return true;
        }
        
        System.out.println("✅ Passed attachment filter: " + email);
        return checkNext(email);
    }
}

// ==================== EXAMPLE 2: SUPPORT TICKET SYSTEM ====================

class SupportTicket {
    private String issue;
    private int severity;  // 1=Low, 2=Medium, 3=High, 4=Critical
    private String customer;

    public SupportTicket(String issue, int severity, String customer) {
        this.issue = issue;
        this.severity = severity;
        this.customer = customer;
    }

    public String getIssue() { return issue; }
    public int getSeverity() { return severity; }
    public String getCustomer() { return customer; }

    @Override
    public String toString() {
        return String.format("[SEV %d] %s - %s", severity, customer, issue);
    }
}

interface SupportHandler {
    void setNext(SupportHandler next);
    void handleTicket(SupportTicket ticket);
}

abstract class BaseSupportHandler implements SupportHandler {
    private SupportHandler next;
    protected int maxSeverity;
    protected String handlerName;

    public BaseSupportHandler(String handlerName, int maxSeverity) {
        this.handlerName = handlerName;
        this.maxSeverity = maxSeverity;
    }

    @Override
    public void setNext(SupportHandler next) {
        this.next = next;
    }

    @Override
    public void handleTicket(SupportTicket ticket) {
        if (ticket.getSeverity() <= maxSeverity) {
            System.out.println("✅ " + handlerName + " handling: " + ticket);
        } else if (next != null) {
            System.out.println("⬆️  " + handlerName + " escalating: " + ticket);
            next.handleTicket(ticket);
        } else {
            System.out.println("⚠️  No handler available for: " + ticket);
        }
    }
}

class JuniorSupport extends BaseSupportHandler {
    public JuniorSupport() {
        super("Junior Support", 1);
    }
}

class SeniorSupport extends BaseSupportHandler {
    public SeniorSupport() {
        super("Senior Support", 2);
    }
}

class TechnicalLead extends BaseSupportHandler {
    public TechnicalLead() {
        super("Technical Lead", 3);
    }
}

class EngineeringManager extends BaseSupportHandler {
    public EngineeringManager() {
        super("Engineering Manager", 4);
    }
}

// ==================== EXAMPLE 3: LOGGING SYSTEM ====================

enum LogLevel {
    DEBUG(1), INFO(2), WARNING(3), ERROR(4), FATAL(5);
    
    private int level;
    
    LogLevel(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
}

class LogMessage {
    private LogLevel level;
    private String message;
    private LocalDateTime timestamp;

    public LogMessage(LogLevel level, String message) {
        this.level = level;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s", 
            level, timestamp.format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME), message);
    }
}

interface LogHandler {
    void setNext(LogHandler next);
    void log(LogMessage message);
}

abstract class BaseLogHandler implements LogHandler {
    private LogHandler next;
    protected LogLevel handlerLevel;

    public BaseLogHandler(LogLevel level) {
        this.handlerLevel = level;
    }

    @Override
    public void setNext(LogHandler next) {
        this.next = next;
    }

    @Override
    public void log(LogMessage message) {
        if (message.getLevel().getLevel() >= handlerLevel.getLevel()) {
            write(message);
        }
        
        if (next != null) {
            next.log(message);
        }
    }

    protected abstract void write(LogMessage message);
}

class ConsoleLogger extends BaseLogHandler {
    public ConsoleLogger(LogLevel level) {
        super(level);
    }

    @Override
    protected void write(LogMessage message) {
        System.out.println("📺 Console: " + message);
    }
}

class FileLogger extends BaseLogHandler {
    private String filename;

    public FileLogger(LogLevel level, String filename) {
        super(level);
        this.filename = filename;
    }

    @Override
    protected void write(LogMessage message) {
        System.out.println("📝 File (" + filename + "): " + message);
    }
}

class EmailLogger extends BaseLogHandler {
    private String email;

    public EmailLogger(LogLevel level, String email) {
        super(level);
        this.email = email;
    }

    @Override
    protected void write(LogMessage message) {
        System.out.println("📧 Email (" + email + "): " + message);
    }
}

// ==================== EXAMPLE 4: ATM MONEY DISPENSER ====================

class WithdrawalRequest {
    private int amount;

    public WithdrawalRequest(int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}

interface Dispenser {
    void setNext(Dispenser next);
    void dispense(WithdrawalRequest request);
}

abstract class BillDispenser implements Dispenser {
    private Dispenser next;
    protected int denomination;

    public BillDispenser(int denomination) {
        this.denomination = denomination;
    }

    @Override
    public void setNext(Dispenser next) {
        this.next = next;
    }

    @Override
    public void dispense(WithdrawalRequest request) {
        int amount = request.getAmount();
        
        if (amount >= denomination) {
            int count = amount / denomination;
            int remainder = amount % denomination;
            
            if (count > 0) {
                System.out.println("💵 Dispensing " + count + " x $" + denomination + " bill(s)");
            }
            
            if (remainder != 0 && next != null) {
                request.setAmount(remainder);
                next.dispense(request);
            }
        } else if (next != null) {
            next.dispense(request);
        }
    }
}

class HundredDollarDispenser extends BillDispenser {
    public HundredDollarDispenser() {
        super(100);
    }
}

class FiftyDollarDispenser extends BillDispenser {
    public FiftyDollarDispenser() {
        super(50);
    }
}

class TwentyDollarDispenser extends BillDispenser {
    public TwentyDollarDispenser() {
        super(20);
    }
}

class TenDollarDispenser extends BillDispenser {
    public TenDollarDispenser() {
        super(10);
    }
}

// ==================== EXAMPLE 5: AUTHENTICATION SYSTEM ====================

class AuthRequest {
    private String username;
    private String password;
    private String ipAddress;
    private int failedAttempts;

    public AuthRequest(String username, String password, String ipAddress, int failedAttempts) {
        this.username = username;
        this.password = password;
        this.ipAddress = ipAddress;
        this.failedAttempts = failedAttempts;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getIpAddress() { return ipAddress; }
    public int getFailedAttempts() { return failedAttempts; }

    @Override
    public String toString() {
        return "User: " + username + ", IP: " + ipAddress;
    }
}

interface AuthHandler {
    void setNext(AuthHandler next);
    boolean authenticate(AuthRequest request);
}

abstract class BaseAuthHandler implements AuthHandler {
    private AuthHandler next;

    @Override
    public void setNext(AuthHandler next) {
        this.next = next;
    }

    protected boolean checkNext(AuthRequest request) {
        if (next == null) {
            return true;  // All checks passed
        }
        return next.authenticate(request);
    }
}

class RateLimitHandler extends BaseAuthHandler {
    private static final int MAX_ATTEMPTS = 3;

    @Override
    public boolean authenticate(AuthRequest request) {
        if (request.getFailedAttempts() >= MAX_ATTEMPTS) {
            System.out.println("❌ Authentication failed: Too many attempts for " + request);
            return false;
        }
        
        System.out.println("✅ Rate limit check passed");
        return checkNext(request);
    }
}

class IpWhitelistHandler extends BaseAuthHandler {
    private Set<String> whitelist = new HashSet<>(Arrays.asList(
        "192.168.1.1", "192.168.1.2", "10.0.0.1"
    ));

    @Override
    public boolean authenticate(AuthRequest request) {
        if (!whitelist.contains(request.getIpAddress())) {
            System.out.println("❌ Authentication failed: IP not whitelisted " + request);
            return false;
        }
        
        System.out.println("✅ IP whitelist check passed");
        return checkNext(request);
    }
}

class CredentialsHandler extends BaseAuthHandler {
    private Map<String, String> validCredentials = new HashMap<>();

    public CredentialsHandler() {
        validCredentials.put("admin", "admin123");
        validCredentials.put("user", "pass123");
    }

    @Override
    public boolean authenticate(AuthRequest request) {
        String expectedPassword = validCredentials.get(request.getUsername());
        
        if (expectedPassword == null || !expectedPassword.equals(request.getPassword())) {
            System.out.println("❌ Authentication failed: Invalid credentials");
            return false;
        }
        
        System.out.println("✅ Credentials check passed");
        return checkNext(request);
    }
}

// ==================== DEMO ====================

public class ChainOfResponsibilityPattern {
    public static void main(String[] args) {
        System.out.println("========== CHAIN OF RESPONSIBILITY PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: EMAIL SPAM FILTER =====
        System.out.println("===== EXAMPLE 1: EMAIL SPAM FILTER SYSTEM =====\n");
        
        KeywordSpamFilter keywordFilter = new KeywordSpamFilter();
        DomainSpamFilter domainFilter = new DomainSpamFilter();
        AttachmentSpamFilter attachmentFilter = new AttachmentSpamFilter();
        
        keywordFilter.setNext(domainFilter);
        domainFilter.setNext(attachmentFilter);
        
        Email email1 = new Email("Business Proposal", "Let's discuss the project", 
                                "john@company.com", 1, 1024 * 100);
        Email email2 = new Email("WINNER! Free Money!", "You won the lottery!", 
                                "spammer@spam.com", 0, 0);
        Email email3 = new Email("Invoice", "Attached invoice", 
                                "vendor@example.com", 10, 1024 * 1024 * 30);
        
        keywordFilter.filter(email1);
        System.out.println();
        keywordFilter.filter(email2);
        System.out.println();
        keywordFilter.filter(email3);

        // ===== EXAMPLE 2: SUPPORT TICKET SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 2: SUPPORT TICKET SYSTEM =====\n");
        
        JuniorSupport junior = new JuniorSupport();
        SeniorSupport senior = new SeniorSupport();
        TechnicalLead lead = new TechnicalLead();
        EngineeringManager manager = new EngineeringManager();
        
        junior.setNext(senior);
        senior.setNext(lead);
        lead.setNext(manager);
        
        SupportTicket ticket1 = new SupportTicket("Password reset", 1, "Customer A");
        SupportTicket ticket2 = new SupportTicket("Application crash", 2, "Customer B");
        SupportTicket ticket3 = new SupportTicket("Data corruption", 3, "Customer C");
        SupportTicket ticket4 = new SupportTicket("System outage", 4, "Customer D");
        
        junior.handleTicket(ticket1);
        junior.handleTicket(ticket2);
        junior.handleTicket(ticket3);
        junior.handleTicket(ticket4);

        // ===== EXAMPLE 3: LOGGING SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 3: LOGGING SYSTEM =====\n");
        
        ConsoleLogger consoleLogger = new ConsoleLogger(LogLevel.DEBUG);
        FileLogger fileLogger = new FileLogger(LogLevel.WARNING, "app.log");
        EmailLogger emailLogger = new EmailLogger(LogLevel.ERROR, "admin@company.com");
        
        consoleLogger.setNext(fileLogger);
        fileLogger.setNext(emailLogger);
        
        consoleLogger.log(new LogMessage(LogLevel.DEBUG, "Debug message"));
        consoleLogger.log(new LogMessage(LogLevel.INFO, "Info message"));
        consoleLogger.log(new LogMessage(LogLevel.WARNING, "Warning message"));
        consoleLogger.log(new LogMessage(LogLevel.ERROR, "Error message"));
        consoleLogger.log(new LogMessage(LogLevel.FATAL, "Fatal message"));

        // ===== EXAMPLE 4: ATM MONEY DISPENSER =====
        System.out.println("\n\n===== EXAMPLE 4: ATM MONEY DISPENSER =====\n");
        
        HundredDollarDispenser hundred = new HundredDollarDispenser();
        FiftyDollarDispenser fifty = new FiftyDollarDispenser();
        TwentyDollarDispenser twenty = new TwentyDollarDispenser();
        TenDollarDispenser ten = new TenDollarDispenser();
        
        hundred.setNext(fifty);
        fifty.setNext(twenty);
        twenty.setNext(ten);
        
        System.out.println("Withdrawal: $280");
        hundred.dispense(new WithdrawalRequest(280));
        
        System.out.println("\nWithdrawal: $370");
        hundred.dispense(new WithdrawalRequest(370));
        
        System.out.println("\nWithdrawal: $150");
        hundred.dispense(new WithdrawalRequest(150));

        // ===== EXAMPLE 5: AUTHENTICATION SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 5: AUTHENTICATION SYSTEM =====\n");
        
        RateLimitHandler rateLimit = new RateLimitHandler();
        IpWhitelistHandler ipWhitelist = new IpWhitelistHandler();
        CredentialsHandler credentials = new CredentialsHandler();
        
        rateLimit.setNext(ipWhitelist);
        ipWhitelist.setNext(credentials);
        
        AuthRequest auth1 = new AuthRequest("admin", "admin123", "192.168.1.1", 0);
        AuthRequest auth2 = new AuthRequest("admin", "wrongpass", "192.168.1.1", 0);
        AuthRequest auth3 = new AuthRequest("admin", "admin123", "192.168.1.1", 5);
        AuthRequest auth4 = new AuthRequest("admin", "admin123", "10.0.0.100", 0);
        
        System.out.println("Auth attempt 1:");
        boolean result1 = rateLimit.authenticate(auth1);
        System.out.println("Result: " + (result1 ? "✅ SUCCESS" : "❌ FAILED"));
        
        System.out.println("\nAuth attempt 2:");
        boolean result2 = rateLimit.authenticate(auth2);
        System.out.println("Result: " + (result2 ? "✅ SUCCESS" : "❌ FAILED"));
        
        System.out.println("\nAuth attempt 3:");
        boolean result3 = rateLimit.authenticate(auth3);
        System.out.println("Result: " + (result3 ? "✅ SUCCESS" : "❌ FAILED"));
        
        System.out.println("\nAuth attempt 4:");
        boolean result4 = rateLimit.authenticate(auth4);
        System.out.println("Result: " + (result4 ? "✅ SUCCESS" : "❌ FAILED"));

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
