import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Authentication System
 * Time to complete: 40-50 minutes
 * Focus: User registration, login, session management, JWT-like tokens
 */

// ==================== User ====================
class User {
    private final String userId;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final LocalDateTime createdAt;

    public User(String userId, String username, String email, String passwordHash) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now();
    }

    public boolean verifyPassword(String passwordHash) {
        return this.passwordHash.equals(passwordHash);
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }

    @Override
    public String toString() {
        return "User[" + userId + ", @" + username + ", " + email + "]";
    }
}

// ==================== Session ====================
class Session {
    private final String sessionId;
    private final String userId;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private boolean isActive;

    public Session(String sessionId, String userId, int durationMinutes) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusMinutes(durationMinutes);
        this.isActive = true;
    }

    public boolean isValid() {
        return isActive && LocalDateTime.now().isBefore(expiresAt);
    }

    public void invalidate() {
        isActive = false;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }

    @Override
    public String toString() {
        return "Session[" + sessionId + ", User:" + userId + 
               ", Valid:" + isValid() + "]";
    }
}

// ==================== Token (JWT-like) ====================
class Token {
    private final String tokenValue;
    private final String userId;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiresAt;

    public Token(String userId, int durationMinutes) {
        this.userId = userId;
        this.issuedAt = LocalDateTime.now();
        this.expiresAt = issuedAt.plusMinutes(durationMinutes);
        this.tokenValue = generateToken(userId);
    }

    private String generateToken(String userId) {
        // Simple token generation (in real system use JWT)
        return "TOKEN-" + userId + "-" + System.currentTimeMillis();
    }

    public boolean isValid() {
        return LocalDateTime.now().isBefore(expiresAt);
    }

    public String getTokenValue() { return tokenValue; }
    public String getUserId() { return userId; }

    @Override
    public String toString() {
        return tokenValue + " [" + (isValid() ? "Valid" : "Expired") + "]";
    }
}

// ==================== Authentication Service ====================
class AuthenticationService {
    private final Map<String, User> usersByUsername;
    private final Map<String, User> usersById;
    private final Map<String, Session> sessions;
    private final Map<String, Token> tokens;
    private final int sessionDurationMinutes;
    private int userCounter;

    public AuthenticationService() {
        this.usersByUsername = new ConcurrentHashMap<>();
        this.usersById = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
        this.tokens = new ConcurrentHashMap<>();
        this.sessionDurationMinutes = 30;
        this.userCounter = 1;
    }

    public User register(String username, String email, String password) {
        if (usersByUsername.containsKey(username)) {
            System.out.println("✗ Username already exists: " + username);
            return null;
        }

        String userId = "U" + userCounter++;
        String passwordHash = hashPassword(password);
        
        User user = new User(userId, username, email, passwordHash);
        usersByUsername.put(username, user);
        usersById.put(userId, user);

        System.out.println("✓ Registered: " + user);
        return user;
    }

    public Session login(String username, String password) {
        User user = usersByUsername.get(username);
        
        if (user == null) {
            System.out.println("✗ User not found: " + username);
            return null;
        }

        String passwordHash = hashPassword(password);
        if (!user.verifyPassword(passwordHash)) {
            System.out.println("✗ Invalid password for: " + username);
            return null;
        }

        // Create session
        String sessionId = "SESSION-" + UUID.randomUUID().toString();
        Session session = new Session(sessionId, user.getUserId(), sessionDurationMinutes);
        sessions.put(sessionId, session);

        System.out.println("✓ Login successful: " + username + " [" + sessionId + "]");
        return session;
    }

    public boolean logout(String sessionId) {
        Session session = sessions.get(sessionId);
        
        if (session == null) {
            System.out.println("✗ Session not found");
            return false;
        }

        session.invalidate();
        sessions.remove(sessionId);
        
        System.out.println("✓ Logged out session: " + sessionId);
        return true;
    }

    public boolean validateSession(String sessionId) {
        Session session = sessions.get(sessionId);
        
        if (session == null || !session.isValid()) {
            System.out.println("✗ Invalid session: " + sessionId);
            return false;
        }

        System.out.println("✓ Valid session: " + sessionId);
        return true;
    }

    public Token generateToken(String userId) {
        Token token = new Token(userId, sessionDurationMinutes);
        tokens.put(token.getTokenValue(), token);
        
        System.out.println("✓ Generated token for user: " + userId);
        return token;
    }

    public boolean validateToken(String tokenValue) {
        Token token = tokens.get(tokenValue);
        
        if (token == null || !token.isValid()) {
            System.out.println("✗ Invalid token");
            return false;
        }

        System.out.println("✓ Valid token for user: " + token.getUserId());
        return true;
    }

    private String hashPassword(String password) {
        // Simple hash for demo (in real system use BCrypt, Argon2)
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return password; // Fallback for demo
        }
    }

    public void displayUsers() {
        System.out.println("\n=== Registered Users ===");
        System.out.println("Total: " + usersByUsername.size());
        for (User user : usersByUsername.values()) {
            System.out.println("  " + user);
        }
        System.out.println();
    }

    public void displaySessions() {
        System.out.println("\n=== Active Sessions ===");
        long activeCount = sessions.values().stream().filter(Session::isValid).count();
        System.out.println("Active: " + activeCount + " / " + sessions.size());
        
        for (Session session : sessions.values()) {
            if (session.isValid()) {
                System.out.println("  " + session);
            }
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class AuthenticationSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Authentication System Demo ===\n");

        AuthenticationService auth = new AuthenticationService();

        // Register users
        System.out.println("--- User Registration ---");
        auth.register("alice", "alice@example.com", "password123");
        auth.register("bob", "bob@example.com", "securepass456");
        auth.register("alice", "alice2@example.com", "pass");  // Duplicate username

        auth.displayUsers();

        // Login
        System.out.println("--- User Login ---");
        Session session1 = auth.login("alice", "password123");
        Session session2 = auth.login("bob", "securepass456");
        
        // Failed login
        auth.login("alice", "wrongpassword");
        auth.login("charlie", "anypass");  // User doesn't exist

        auth.displaySessions();

        // Validate session
        System.out.println("--- Session Validation ---");
        if (session1 != null) {
            auth.validateSession(session1.getSessionId());
        }

        // Generate tokens
        System.out.println("\n--- Token Generation ---");
        Token token1 = auth.generateToken("U1");
        Token token2 = auth.generateToken("U2");

        // Validate tokens
        System.out.println("\n--- Token Validation ---");
        auth.validateToken(token1.getTokenValue());
        auth.validateToken("INVALID-TOKEN");

        // Logout
        System.out.println("\n--- User Logout ---");
        if (session1 != null) {
            auth.logout(session1.getSessionId());
        }

        // Try to validate logged out session
        if (session1 != null) {
            auth.validateSession(session1.getSessionId());
        }

        auth.displaySessions();

        System.out.println("✅ Demo complete!");
    }
}
