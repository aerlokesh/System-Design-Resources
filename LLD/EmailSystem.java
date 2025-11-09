import java.util.*;
import java.time.*;

// ==================== Enums ====================
// Why Enums? Type safety, self-documenting code, prevents invalid states
// Better than strings which can have typos and aren't validated at compile time

enum EmailStatus {
    DRAFT,          // Saved but not sent
    SENT,           // Successfully delivered
    FAILED,         // Delivery failed
    QUEUED,         // Waiting to be sent
    SCHEDULED       // Scheduled for future delivery
}

enum EmailPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

enum FolderType {
    INBOX,
    SENT,
    DRAFTS,
    SPAM,
    TRASH,
    CUSTOM          // User-created folders
}

enum AttachmentType {
    DOCUMENT,
    IMAGE,
    VIDEO,
    AUDIO,
    OTHER
}

// ==================== Email Address ====================
// Represents an email address with validation
// Value Object pattern - immutable, equality based on value

class EmailAddress {
    private final String address;
    private final String displayName;

    public EmailAddress(String address, String displayName) {
        if (!isValidEmail(address)) {
            throw new IllegalArgumentException("Invalid email address: " + address);
        }
        this.address = address.toLowerCase();  // Normalize to lowercase
        this.displayName = displayName;
    }

    public EmailAddress(String address) {
        this(address, address);
    }

    // Simple email validation
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public String getAddress() { return address; }
    public String getDisplayName() { return displayName; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EmailAddress)) return false;
        EmailAddress other = (EmailAddress) obj;
        return address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public String toString() {
        return displayName.equals(address) ? address : displayName + " <" + address + ">";
    }
}

// ==================== Attachment ====================
// Represents a file attached to an email

class Attachment {
    private String attachmentId;
    private String fileName;
    private long fileSize;              // in bytes
    private AttachmentType type;
    private byte[] content;             // In real system, would be file reference/URL
    private LocalDateTime uploadTime;

    public Attachment(String attachmentId, String fileName, long fileSize, 
                     AttachmentType type, byte[] content) {
        this.attachmentId = attachmentId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.type = type;
        this.content = content;
        this.uploadTime = LocalDateTime.now();
    }

    public String getAttachmentId() { return attachmentId; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public AttachmentType getType() { return type; }
    public byte[] getContent() { return content; }
    public LocalDateTime getUploadTime() { return uploadTime; }

    // Human-readable file size
    public String getFormattedSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
    }

    @Override
    public String toString() {
        return fileName + " (" + getFormattedSize() + ")";
    }
}

// ==================== Email ====================
// Core entity representing an email message

class Email {
    private String emailId;
    private EmailAddress from;
    private List<EmailAddress> to;
    private List<EmailAddress> cc;
    private List<EmailAddress> bcc;
    private String subject;
    private String body;
    private List<Attachment> attachments;
    private EmailStatus status;
    private EmailPriority priority;
    private LocalDateTime sentTime;
    private LocalDateTime receivedTime;
    private Set<String> labels;            // User-defined labels/tags
    private boolean isRead;
    private boolean isStarred;
    private boolean isSpam;
    private String threadId;               // For conversation threading
    private String replyToEmailId;         // Reference to email being replied to

    public Email(String emailId, EmailAddress from, List<EmailAddress> to, 
                String subject, String body) {
        this.emailId = emailId;
        this.from = from;
        this.to = new ArrayList<>(to);
        this.cc = new ArrayList<>();
        this.bcc = new ArrayList<>();
        this.subject = subject;
        this.body = body;
        this.attachments = new ArrayList<>();
        this.status = EmailStatus.DRAFT;
        this.priority = EmailPriority.NORMAL;
        this.labels = new HashSet<>();
        this.isRead = false;
        this.isStarred = false;
        this.isSpam = false;
        this.threadId = emailId;  // By default, each email is its own thread
    }

    // Getters
    public String getEmailId() { return emailId; }
    public EmailAddress getFrom() { return from; }
    public List<EmailAddress> getTo() { return to; }
    public List<EmailAddress> getCc() { return cc; }
    public List<EmailAddress> getBcc() { return bcc; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public List<Attachment> getAttachments() { return attachments; }
    public EmailStatus getStatus() { return status; }
    public EmailPriority getPriority() { return priority; }
    public LocalDateTime getSentTime() { return sentTime; }
    public LocalDateTime getReceivedTime() { return receivedTime; }
    public Set<String> getLabels() { return labels; }
    public boolean isRead() { return isRead; }
    public boolean isStarred() { return isStarred; }
    public boolean isSpam() { return isSpam; }
    public String getThreadId() { return threadId; }
    public String getReplyToEmailId() { return replyToEmailId; }

    // Setters
    public void setStatus(EmailStatus status) { this.status = status; }
    public void setSentTime(LocalDateTime sentTime) { this.sentTime = sentTime; }
    public void setReceivedTime(LocalDateTime receivedTime) { this.receivedTime = receivedTime; }
    public void setRead(boolean isRead) { this.isRead = isRead; }
    public void setStarred(boolean isStarred) { this.isStarred = isStarred; }
    public void setSpam(boolean isSpam) { this.isSpam = isSpam; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public void setReplyToEmailId(String replyToEmailId) { this.replyToEmailId = replyToEmailId; }
    public void setPriority(EmailPriority priority) { this.priority = priority; }

    // Add CC recipients
    public void addCc(EmailAddress address) {
        cc.add(address);
    }

    // Add BCC recipients
    public void addBcc(EmailAddress address) {
        bcc.add(address);
    }

    // Add attachment
    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
    }

    // Label management
    public void addLabel(String label) {
        labels.add(label);
    }

    public void removeLabel(String label) {
        labels.remove(label);
    }

    public boolean hasLabel(String label) {
        return labels.contains(label);
    }

    // Check if email has attachments
    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    // Get total attachment size
    public long getTotalAttachmentSize() {
        return attachments.stream()
            .mapToLong(Attachment::getFileSize)
            .sum();
    }

    @Override
    public String toString() {
        return String.format("Email[%s, From=%s, To=%s, Subject='%s', Status=%s]",
            emailId, from.getAddress(), 
            to.isEmpty() ? "none" : to.get(0).getAddress(),
            subject, status);
    }
}

// ==================== Folder (Composite Pattern) ====================
// Why Composite Pattern? Folders can contain emails and other folders
// Benefits: 1) Uniform treatment of files and folders 2) Tree structure
// 3) Easy to add nested folders 4) Recursive operations

interface MailComponent {
    String getName();
    int getEmailCount();
    List<Email> getEmails();
}

class Folder implements MailComponent {
    private String folderId;
    private String name;
    private FolderType type;
    private List<Email> emails;
    private List<Folder> subFolders;
    private Folder parentFolder;

    public Folder(String folderId, String name, FolderType type) {
        this.folderId = folderId;
        this.name = name;
        this.type = type;
        this.emails = new ArrayList<>();
        this.subFolders = new ArrayList<>();
    }

    public String getFolderId() { return folderId; }
    public String getName() { return name; }
    public FolderType getType() { return type; }
    public List<Folder> getSubFolders() { return subFolders; }
    public Folder getParentFolder() { return parentFolder; }

    @Override
    public List<Email> getEmails() {
        return new ArrayList<>(emails);
    }

    @Override
    public int getEmailCount() {
        int count = emails.size();
        // Recursively count emails in subfolders
        for (Folder subFolder : subFolders) {
            count += subFolder.getEmailCount();
        }
        return count;
    }

    // Add email to folder
    public void addEmail(Email email) {
        emails.add(email);
    }

    // Remove email from folder
    public void removeEmail(Email email) {
        emails.remove(email);
    }

    // Add subfolder
    public void addSubFolder(Folder folder) {
        folder.parentFolder = this;
        subFolders.add(folder);
    }

    // Get unread count
    public int getUnreadCount() {
        return (int) emails.stream()
            .filter(email -> !email.isRead())
            .count();
    }

    @Override
    public String toString() {
        return name + " (" + getEmailCount() + " emails, " + getUnreadCount() + " unread)";
    }
}

// ==================== Spam Filter (Chain of Responsibility) ====================
// Why Chain of Responsibility? Multiple spam detection rules
// Benefits: 1) Decouples sender from receiver 2) Flexible rule ordering
// 3) Easy to add/remove rules 4) Each rule is independent

interface SpamFilter {
    void setNext(SpamFilter next);
    boolean isSpam(Email email);
}

// Base spam filter with chain handling
abstract class BaseSpamFilter implements SpamFilter {
    private SpamFilter next;

    @Override
    public void setNext(SpamFilter next) {
        this.next = next;
    }

    protected boolean checkNext(Email email) {
        if (next == null) {
            return false;
        }
        return next.isSpam(email);
    }
}

// Filter 1: Check for spam keywords in subject/body
class KeywordSpamFilter extends BaseSpamFilter {
    private Set<String> spamKeywords;

    public KeywordSpamFilter() {
        this.spamKeywords = new HashSet<>(Arrays.asList(
            "winner", "lottery", "free money", "click here", 
            "congratulations", "urgent", "act now"
        ));
    }

    @Override
    public boolean isSpam(Email email) {
        String text = (email.getSubject() + " " + email.getBody()).toLowerCase();
        
        for (String keyword : spamKeywords) {
            if (text.contains(keyword)) {
                return true;  // Spam detected
            }
        }
        
        return checkNext(email);  // Continue chain
    }
}

// Filter 2: Check for suspicious sender patterns
class SenderSpamFilter extends BaseSpamFilter {
    private Set<String> blockedDomains;

    public SenderSpamFilter() {
        this.blockedDomains = new HashSet<>(Arrays.asList(
            "spam.com", "scam.net", "fake.org"
        ));
    }

    @Override
    public boolean isSpam(Email email) {
        String senderEmail = email.getFrom().getAddress();
        String domain = senderEmail.substring(senderEmail.indexOf('@') + 1);
        
        if (blockedDomains.contains(domain)) {
            return true;  // Spam detected
        }
        
        return checkNext(email);  // Continue chain
    }
}

// Filter 3: Check for excessive attachments (common in spam)
class AttachmentSpamFilter extends BaseSpamFilter {
    private static final int MAX_ATTACHMENTS = 5;
    private static final long MAX_SIZE_MB = 25;

    @Override
    public boolean isSpam(Email email) {
        if (email.getAttachments().size() > MAX_ATTACHMENTS) {
            return true;  // Too many attachments
        }
        
        long totalSizeMB = email.getTotalAttachmentSize() / (1024 * 1024);
        if (totalSizeMB > MAX_SIZE_MB) {
            return true;  // Attachments too large
        }
        
        return checkNext(email);  // Continue chain
    }
}

// ==================== Search Strategy ====================
// Why Strategy Pattern? Different search algorithms for different needs
// Benefits: 1) Interchangeable algorithms 2) Encapsulated complexity
// 3) Easy to add new strategies 4) Runtime selection

interface SearchStrategy {
    List<Email> search(List<Email> emails, String query);
}

// Strategy 1: Search in subject
class SubjectSearchStrategy implements SearchStrategy {
    @Override
    public List<Email> search(List<Email> emails, String query) {
        List<Email> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (Email email : emails) {
            if (email.getSubject().toLowerCase().contains(lowerQuery)) {
                results.add(email);
            }
        }
        
        return results;
    }
}

// Strategy 2: Search in body
class BodySearchStrategy implements SearchStrategy {
    @Override
    public List<Email> search(List<Email> emails, String query) {
        List<Email> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (Email email : emails) {
            if (email.getBody().toLowerCase().contains(lowerQuery)) {
                results.add(email);
            }
        }
        
        return results;
    }
}

// Strategy 3: Search by sender
class SenderSearchStrategy implements SearchStrategy {
    @Override
    public List<Email> search(List<Email> emails, String query) {
        List<Email> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (Email email : emails) {
            if (email.getFrom().getAddress().toLowerCase().contains(lowerQuery) ||
                email.getFrom().getDisplayName().toLowerCase().contains(lowerQuery)) {
                results.add(email);
            }
        }
        
        return results;
    }
}

// Strategy 4: Search with attachments only
class AttachmentSearchStrategy implements SearchStrategy {
    @Override
    public List<Email> search(List<Email> emails, String query) {
        List<Email> results = new ArrayList<>();
        
        for (Email email : emails) {
            if (email.hasAttachments()) {
                // Optional: also filter by attachment filename
                if (query == null || query.isEmpty()) {
                    results.add(email);
                } else {
                    for (Attachment att : email.getAttachments()) {
                        if (att.getFileName().toLowerCase().contains(query.toLowerCase())) {
                            results.add(email);
                            break;
                        }
                    }
                }
            }
        }
        
        return results;
    }
}

// ==================== Email Account ====================
// Represents a user's email account

class EmailAccount {
    private String accountId;
    private EmailAddress emailAddress;
    private String password;  // In real system: hashed password
    private Map<String, Folder> folders;
    private Set<String> contacts;
    private LocalDateTime lastLogin;

    public EmailAccount(String accountId, EmailAddress emailAddress, String password) {
        this.accountId = accountId;
        this.emailAddress = emailAddress;
        this.password = password;
        this.folders = new HashMap<>();
        this.contacts = new HashSet<>();
        
        // Initialize default folders
        createDefaultFolders();
    }

    private void createDefaultFolders() {
        folders.put("INBOX", new Folder("INBOX", "Inbox", FolderType.INBOX));
        folders.put("SENT", new Folder("SENT", "Sent", FolderType.SENT));
        folders.put("DRAFTS", new Folder("DRAFTS", "Drafts", FolderType.DRAFTS));
        folders.put("SPAM", new Folder("SPAM", "Spam", FolderType.SPAM));
        folders.put("TRASH", new Folder("TRASH", "Trash", FolderType.TRASH));
    }

    public String getAccountId() { return accountId; }
    public EmailAddress getEmailAddress() { return emailAddress; }
    public Map<String, Folder> getFolders() { return folders; }
    public Set<String> getContacts() { return contacts; }

    // Authenticate user
    public boolean authenticate(String password) {
        return this.password.equals(password);  // Simple comparison - use hashing in production
    }

    // Get folder by ID
    public Folder getFolder(String folderId) {
        return folders.get(folderId);
    }

    // Create custom folder
    public Folder createFolder(String name) {
        String folderId = "FOLDER-" + System.currentTimeMillis();
        Folder folder = new Folder(folderId, name, FolderType.CUSTOM);
        folders.put(folderId, folder);
        return folder;
    }

    // Add contact
    public void addContact(String emailAddress) {
        contacts.add(emailAddress);
    }

    // Update last login
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Account[" + emailAddress + "]";
    }
}

// ==================== Email Service (Singleton) ====================
// Why Singleton? Central email service coordinator
// Manages all accounts, email operations, delivery

class EmailService {
    private static EmailService instance;
    private Map<String, EmailAccount> accounts;
    private Map<String, Email> allEmails;
    private SpamFilter spamFilterChain;
    private int emailCounter;

    // Private constructor - Singleton
    private EmailService() {
        this.accounts = new HashMap<>();
        this.allEmails = new HashMap<>();
        this.emailCounter = 0;
        
        // Setup spam filter chain
        setupSpamFilters();
    }

    // Thread-safe singleton
    public static synchronized EmailService getInstance() {
        if (instance == null) {
            instance = new EmailService();
        }
        return instance;
    }

    // Setup spam filter chain
    private void setupSpamFilters() {
        KeywordSpamFilter keywordFilter = new KeywordSpamFilter();
        SenderSpamFilter senderFilter = new SenderSpamFilter();
        AttachmentSpamFilter attachmentFilter = new AttachmentSpamFilter();
        
        // Chain: Keywords -> Sender -> Attachments
        keywordFilter.setNext(senderFilter);
        senderFilter.setNext(attachmentFilter);
        
        this.spamFilterChain = keywordFilter;
    }

    // Create account
    public EmailAccount createAccount(String email, String displayName, String password) {
        EmailAddress emailAddress = new EmailAddress(email, displayName);
        String accountId = "ACC-" + System.currentTimeMillis();
        EmailAccount account = new EmailAccount(accountId, emailAddress, password);
        accounts.put(email, account);
        System.out.println("✅ Account created: " + email);
        return account;
    }

    // Get account
    public EmailAccount getAccount(String email) {
        return accounts.get(email);
    }

    // Compose email
    public Email composeEmail(EmailAddress from, List<EmailAddress> to, 
                             String subject, String body) {
        String emailId = "EMAIL-" + (++emailCounter);
        Email email = new Email(emailId, from, to, subject, body);
        allEmails.put(emailId, email);
        
        // Save to drafts
        EmailAccount account = accounts.get(from.getAddress());
        if (account != null) {
            account.getFolder("DRAFTS").addEmail(email);
        }
        
        return email;
    }

    // Send email
    public synchronized boolean sendEmail(Email email) {
        System.out.println("\n--- Sending Email ---");
        System.out.println("From: " + email.getFrom());
        System.out.println("To: " + email.getTo());
        System.out.println("Subject: " + email.getSubject());

        // Remove from drafts if present
        EmailAccount senderAccount = accounts.get(email.getFrom().getAddress());
        if (senderAccount != null) {
            senderAccount.getFolder("DRAFTS").removeEmail(email);
        }

        // Set sent time and status
        email.setSentTime(LocalDateTime.now());
        email.setStatus(EmailStatus.SENT);

        // Add to sender's sent folder
        if (senderAccount != null) {
            senderAccount.getFolder("SENT").addEmail(email);
        }

        // Deliver to recipients
        for (EmailAddress recipient : email.getTo()) {
            deliverEmail(email, recipient);
        }

        // Deliver to CC recipients
        for (EmailAddress recipient : email.getCc()) {
            deliverEmail(email, recipient);
        }

        System.out.println("✅ Email sent successfully!");
        return true;
    }

    // Deliver email to recipient
    private void deliverEmail(Email email, EmailAddress recipient) {
        EmailAccount recipientAccount = accounts.get(recipient.getAddress());
        if (recipientAccount == null) {
            System.out.println("⚠️  Recipient not found: " + recipient.getAddress());
            return;
        }

        // Create copy for recipient (in real system, would be same email with different metadata)
        Email deliveredEmail = email;  // Simplified - should create copy
        deliveredEmail.setReceivedTime(LocalDateTime.now());

        // Check spam
        if (spamFilterChain.isSpam(deliveredEmail)) {
            deliveredEmail.setSpam(true);
            recipientAccount.getFolder("SPAM").addEmail(deliveredEmail);
            System.out.println("📧 Email delivered to " + recipient.getAddress() + " (SPAM)");
        } else {
            recipientAccount.getFolder("INBOX").addEmail(deliveredEmail);
            System.out.println("📧 Email delivered to " + recipient.getAddress());
        }
    }

    // Reply to email
    public Email replyTo(Email originalEmail, EmailAddress from, String body) {
        String subject = originalEmail.getSubject();
        if (!subject.startsWith("Re: ")) {
            subject = "Re: " + subject;
        }

        List<EmailAddress> to = Arrays.asList(originalEmail.getFrom());
        Email reply = composeEmail(from, to, subject, body);
        reply.setReplyToEmailId(originalEmail.getEmailId());
        reply.setThreadId(originalEmail.getThreadId());  // Keep same thread

        return reply;
    }

    // Forward email
    public Email forward(Email originalEmail, EmailAddress from, 
                        List<EmailAddress> to, String additionalMessage) {
        String subject = originalEmail.getSubject();
        if (!subject.startsWith("Fwd: ")) {
            subject = "Fwd: " + subject;
        }

        String body = additionalMessage + "\n\n---------- Forwarded message ----------\n" +
                     "From: " + originalEmail.getFrom() + "\n" +
                     "Date: " + originalEmail.getSentTime() + "\n" +
                     "Subject: " + originalEmail.getSubject() + "\n\n" +
                     originalEmail.getBody();

        Email forwarded = composeEmail(from, to, subject, body);
        
        // Copy attachments
        for (Attachment att : originalEmail.getAttachments()) {
            forwarded.addAttachment(att);
        }

        return forwarded;
    }

    // Search emails
    public List<Email> searchEmails(EmailAccount account, SearchStrategy strategy, String query) {
        List<Email> allAccountEmails = new ArrayList<>();
        
        // Collect emails from all folders
        for (Folder folder : account.getFolders().values()) {
            allAccountEmails.addAll(folder.getEmails());
        }

        return strategy.search(allAccountEmails, query);
    }

    // Move email to folder
    public void moveEmail(Email email, Folder fromFolder, Folder toFolder) {
        fromFolder.removeEmail(email);
        toFolder.addEmail(email);
    }

    // Delete email (move to trash)
    public void deleteEmail(Email email, EmailAccount account) {
        for (Folder folder : account.getFolders().values()) {
            if (folder.getType() != FolderType.TRASH) {
                folder.removeEmail(email);
            }
        }
        account.getFolder("TRASH").addEmail(email);
    }

    // Permanently delete (from trash)
    public void permanentlyDelete(Email email, EmailAccount account) {
        account.getFolder("TRASH").removeEmail(email);
        allEmails.remove(email.getEmailId());
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates complete email system functionality

public class EmailSystem {
    public static void main(String[] args) {
        // Initialize Email Service (Singleton)
        EmailService emailService = EmailService.getInstance();

        System.out.println("========== Email System Demo ==========\n");

        // ===== Create Accounts =====
        System.out.println("--- Creating Accounts ---");
        EmailAccount alice = emailService.createAccount(
            "alice@example.com",
            "Alice Smith",
            "password123"
        );

        EmailAccount bob = emailService.createAccount(
            "bob@example.com",
            "Bob Johnson",
            "password456"
        );

        EmailAccount charlie = emailService.createAccount(
            "charlie@example.com",
            "Charlie Brown",
            "password789"
        );

        // ===== Compose and Send Email =====
        System.out.println("\n--- Composing Email ---");
        Email email1 = emailService.composeEmail(
            alice.getEmailAddress(),
            Arrays.asList(bob.getEmailAddress()),
            "Project Update",
            "Hi Bob,\n\nHere's the latest update on our project.\n\nBest regards,\nAlice"
        );
        email1.setPriority(EmailPriority.HIGH);

        // Add attachment
        Attachment doc = new Attachment(
            "ATT-001",
            "project-report.pdf",
            1024 * 500,  // 500 KB
            AttachmentType.DOCUMENT,
            new byte[0]  // Empty array for demo
        );
        email1.addAttachment(doc);

        emailService.sendEmail(email1);

        // ===== Reply to Email =====
        System.out.println("\n--- Replying to Email ---");
        Email reply = emailService.replyTo(
            email1,
            bob.getEmailAddress(),
            "Hi Alice,\n\nThanks for the update! Looks great.\n\nBob"
        );
        emailService.sendEmail(reply);

        // ===== Forward Email =====
        System.out.println("\n--- Forwarding Email ---");
        Email forwarded = emailService.forward(
            email1,
            bob.getEmailAddress(),
            Arrays.asList(charlie.getEmailAddress()),
            "Charlie, FYI - see Alice's update below."
        );
        emailService.sendEmail(forwarded);

        // ===== Send Spam Email (will be filtered) =====
        System.out.println("\n--- Sending Spam Email ---");
        Email spam = emailService.composeEmail(
            new EmailAddress("spammer@spam.com", "Spammer"),
            Arrays.asList(alice.getEmailAddress()),
            "CONGRATULATIONS! You're a WINNER!",
            "Click here for free money! Act now! Urgent!"
        );
        emailService.sendEmail(spam);

        // ===== Check Folders =====
        System.out.println("\n--- Alice's Folders ---");
        for (Folder folder : alice.getFolders().values()) {
            System.out.println(folder);
        }

        System.out.println("\n--- Bob's Folders ---");
        for (Folder folder : bob.getFolders().values()) {
            System.out.println(folder);
        }

        // ===== Search Emails =====
        System.out.println("\n--- Searching Emails (Subject: 'Project') ---");
        List<Email> searchResults = emailService.searchEmails(
            bob,
            new SubjectSearchStrategy(),
            "Project"
        );
        System.out.println("Found " + searchResults.size() + " email(s)");
        for (Email email : searchResults) {
            System.out.println("  - " + email.getSubject());
        }

        // ===== Search by Sender =====
        System.out.println("\n--- Searching Emails (From: 'alice') ---");
        searchResults = emailService.searchEmails(
            bob,
            new SenderSearchStrategy(),
            "alice"
        );
        System.out.println("Found " + searchResults.size() + " email(s) from Alice");

        // ===== Mark as Read/Starred =====
        System.out.println("\n--- Managing Email Status ---");
        Email bobsFirstEmail = bob.getFolder("INBOX").getEmails().get(0);
        bobsFirstEmail.setRead(true);
        bobsFirstEmail.setStarred(true);
        bobsFirstEmail.addLabel("Important");
        System.out.println("Marked email as read and starred");

        // ===== Delete Email =====
        System.out.println("\n--- Deleting Email ---");
        Email toDelete = bob.getFolder("INBOX").getEmails().get(1);
        emailService.deleteEmail(toDelete, bob);
        System.out.println("Email moved to trash");

        // ===== Final Folder Status =====
        System.out.println("\n--- Final Folder Status ---");
        System.out.println("Bob's Inbox: " + bob.getFolder("INBOX").getEmailCount() + " emails");
        System.out.println("Bob's Trash: " + bob.getFolder("TRASH").getEmailCount() + " emails");

        System.out.println("\n========== Demo Complete ==========");
    }
}
