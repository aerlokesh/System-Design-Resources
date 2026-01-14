import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Email System
 * Time to complete: 40-50 minutes
 * Focus: Email composition, folder management, search
 */

// ==================== Email ====================
class Email {
    private final String emailId;
    private final String from;
    private final List<String> to;
    private final String subject;
    private final String body;
    private final LocalDateTime timestamp;
    private boolean isRead;
    private boolean isStarred;

    public Email(String emailId, String from, List<String> to, 
                String subject, String body) {
        this.emailId = emailId;
        this.from = from;
        this.to = new ArrayList<>(to);
        this.subject = subject;
        this.body = body;
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
        this.isStarred = false;
    }

    public void markAsRead() {
        isRead = true;
    }

    public void toggleStar() {
        isStarred = !isStarred;
    }

    public boolean containsText(String searchText) {
        String lower = searchText.toLowerCase();
        return subject.toLowerCase().contains(lower) ||
               body.toLowerCase().contains(lower) ||
               from.toLowerCase().contains(lower);
    }

    public String getEmailId() { return emailId; }
    public String getFrom() { return from; }
    public String getSubject() { return subject; }
    public boolean isRead() { return isRead; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        String status = isRead ? "[Read]" : "[Unread]";
        String star = isStarred ? "⭐" : "";
        return status + star + " From: " + from + " | " + subject;
    }
}

// ==================== Folder ====================
class Folder {
    private final String folderId;
    private final String name;
    private final List<Email> emails;

    public Folder(String folderId, String name) {
        this.folderId = folderId;
        this.name = name;
        this.emails = new ArrayList<>();
    }

    public void addEmail(Email email) {
        emails.add(email);
        emails.sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()));
    }

    public boolean removeEmail(String emailId) {
        return emails.removeIf(e -> e.getEmailId().equals(emailId));
    }

    public List<Email> getEmails() {
        return new ArrayList<>(emails);
    }

    public String getName() { return name; }

    public int getUnreadCount() {
        return (int) emails.stream().filter(e -> !e.isRead()).count();
    }

    @Override
    public String toString() {
        return name + " (" + emails.size() + " emails, " + getUnreadCount() + " unread)";
    }
}

// ==================== Email Service ====================
class EmailService {
    private final Map<String, Folder> folders;
    private final Map<String, Email> allEmails;
    private final String userEmail;
    private int emailCounter;

    public EmailService(String userEmail) {
        this.userEmail = userEmail;
        this.folders = new HashMap<>();
        this.allEmails = new HashMap<>();
        this.emailCounter = 1;

        folders.put("inbox", new Folder("inbox", "Inbox"));
        folders.put("sent", new Folder("sent", "Sent"));
        folders.put("trash", new Folder("trash", "Trash"));
    }

    public Email sendEmail(List<String> to, String subject, String body) {
        String emailId = "E" + emailCounter++;
        Email email = new Email(emailId, userEmail, to, subject, body);
        
        allEmails.put(emailId, email);
        folders.get("sent").addEmail(email);
        
        System.out.println("✓ Sent: " + subject);
        return email;
    }

    public void receiveEmail(Email email) {
        allEmails.put(email.getEmailId(), email);
        folders.get("inbox").addEmail(email);
        System.out.println("✓ Received: " + email.getSubject());
    }

    public void moveEmail(String emailId, String targetFolderId) {
        Email email = allEmails.get(emailId);
        if (email == null) return;

        for (Folder folder : folders.values()) {
            folder.removeEmail(emailId);
        }

        Folder targetFolder = folders.get(targetFolderId);
        if (targetFolder != null) {
            targetFolder.addEmail(email);
            System.out.println("✓ Moved to: " + targetFolder.getName());
        }
    }

    public void deleteEmail(String emailId) {
        moveEmail(emailId, "trash");
    }

    public void markAsRead(String emailId) {
        Email email = allEmails.get(emailId);
        if (email != null) {
            email.markAsRead();
            System.out.println("✓ Marked as read");
        }
    }

    public List<Email> searchEmails(String searchText) {
        List<Email> results = new ArrayList<>();
        for (Email email : allEmails.values()) {
            if (email.containsText(searchText)) {
                results.add(email);
            }
        }
        return results;
    }

    public void displayFolder(String folderId) {
        Folder folder = folders.get(folderId);
        if (folder == null) return;

        System.out.println("\n=== " + folder.getName() + " ===");
        List<Email> emails = folder.getEmails();
        
        if (emails.isEmpty()) {
            System.out.println("No emails");
        } else {
            for (int i = 0; i < emails.size(); i++) {
                System.out.println((i + 1) + ". " + emails.get(i));
            }
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class EmailSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Email System Demo ===\n");

        EmailService service = new EmailService("user@example.com");

        // Send emails
        System.out.println("--- Sending Emails ---");
        service.sendEmail(Arrays.asList("alice@example.com"), 
            "Meeting Tomorrow", "Let's meet at 10 AM");

        service.sendEmail(Arrays.asList("bob@example.com", "charlie@example.com"),
            "Project Update", "Latest update");

        // Receive emails
        System.out.println("\n--- Receiving Emails ---");
        Email e1 = new Email("E100", "boss@example.com", 
            Arrays.asList("user@example.com"), 
            "Urgent: Report", "Send report by EOD");
        service.receiveEmail(e1);

        // Display
        service.displayFolder("inbox");
        service.displayFolder("sent");

        // Operations
        System.out.println("--- Operations ---");
        service.markAsRead("E100");
        service.deleteEmail("E100");
        
        service.displayFolder("inbox");
        service.displayFolder("trash");

        System.out.println("✅ Demo complete!");
    }
}
