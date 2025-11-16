/**
 * LOW-LEVEL DESIGN: AUTHENTICATION SERVICE
 * 
 * This design implements a comprehensive authentication system with multiple authentication
 * strategies, session management, token-based authentication, and security features.
 * 
 * KEY COMPONENTS:
 * 1. User Management - User creation, storage, and retrieval
 * 2. Authentication Strategies - Password, Token, OAuth, MFA
 * 3. Session Management - Session creation, validation, and expiration
 * 4. Token Management - JWT generation and validation
 * 5. Password Security - Hashing, salting, and validation
 * 6. Authorization - Role-based access control (RBAC)
 * 
 * DESIGN PATTERNS USED:
 * - Strategy Pattern: Multiple authentication strategies
 * - Singleton Pattern: AuthenticationService instance
 * - Factory Pattern: Token and session creation
 * - Builder Pattern: User and session creation
 * - Observer Pattern: Authentication event notifications
 */

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

// ==================== ENUMS ====================

enum UserRole {
    ADMIN, USER, MODERATOR, GUEST
}

enum AuthenticationStatus {
    SUCCESS, FAILED, LOCKED, REQUIRES_MFA, EXPIRED
}

enum TokenType {
    ACCESS_TOKEN, REFRESH_TOKEN, RESET_TOKEN, VERIFICATION_TOKEN
}

enum SessionStatus {
    ACTIVE, EXPIRED, REVOKED, LOGGED_OUT
}

enum AuthenticationMethod {
    PASSWORD, TOKEN, OAUTH, BIOMETRIC, MFA
}

// ==================== MODELS ====================

class User {
    private final String userId;
    private String username;
    private String email;
    private String passwordHash;
    private String salt;
    private UserRole role;
    private boolean isActive;
    private boolean isEmailVerified;
    private boolean mfaEnabled;
    private String mfaSecret;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private Map<String, String> metadata;

    private User(Builder builder) {
        this.userId = builder.userId;
        this.username = builder.username;
        this.email = builder.email;
        this.passwordHash = builder.passwordHash;
        this.salt = builder.salt;
        this.role = builder.role;
        this.isActive = builder.isActive;
        this.isEmailVerified = builder.isEmailVerified;
        this.mfaEnabled = builder.mfaEnabled;
        this.createdAt = LocalDateTime.now();
        this.failedLoginAttempts = 0;
        this.metadata = new HashMap<>();
    }

    // Builder Pattern for User creation
    public static class Builder {
        private String userId;
        private String username;
        private String email;
        private String passwordHash;
        private String salt;
        private UserRole role = UserRole.USER;
        private boolean isActive = true;
        private boolean isEmailVerified = false;
        private boolean mfaEnabled = false;

        public Builder(String userId, String username, String email) {
            this.userId = userId;
            this.username = username;
            this.email = email;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder salt(String salt) {
            this.salt = salt;
            return this;
        }

        public Builder role(UserRole role) {
            this.role = role;
            return this;
        }

        public Builder mfaEnabled(boolean mfaEnabled) {
            this.mfaEnabled = mfaEnabled;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }

    // Getters and setters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public UserRole getRole() { return role; }
    public boolean isActive() { return isActive; }
    public boolean isEmailVerified() { return isEmailVerified; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    
    public void incrementFailedAttempts() { this.failedLoginAttempts++; }
    public void resetFailedAttempts() { this.failedLoginAttempts = 0; }
    public void lockAccount(int minutes) { 
        this.lockedUntil = LocalDateTime.now().plusMinutes(minutes); 
    }
    public void setLastLoginAt(LocalDateTime time) { this.lastLoginAt = time; }
    public void setEmailVerified(boolean verified) { this.isEmailVerified = verified; }
    public void setMfaSecret(String secret) { this.mfaSecret = secret; }
    public String getMfaSecret() { return mfaSecret; }
}

class Session {
    private final String sessionId;
    private final String userId;
    private final String deviceInfo;
    private final String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime expiresAt;
    private SessionStatus status;
    private Map<String, Object> attributes;

    public Session(String sessionId, String userId, String deviceInfo, 
                   String ipAddress, int expiryMinutes) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);
        this.status = SessionStatus.ACTIVE;
        this.attributes = new ConcurrentHashMap<>();
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public SessionStatus getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    
    public void updateLastAccessed() { 
        this.lastAccessedAt = LocalDateTime.now(); 
    }
    
    public void revoke() { 
        this.status = SessionStatus.REVOKED; 
    }
    
    public boolean isValid() {
        return status == SessionStatus.ACTIVE && 
               LocalDateTime.now().isBefore(expiresAt);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }
}

class AuthToken {
    private final String token;
    private final TokenType type;
    private final String userId;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiresAt;
    private final Map<String, String> claims;

    public AuthToken(String token, TokenType type, String userId, 
                     LocalDateTime expiresAt) {
        this.token = token;
        this.type = type;
        this.userId = userId;
        this.issuedAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.claims = new HashMap<>();
    }

    public String getToken() { return token; }
    public TokenType getType() { return type; }
    public String getUserId() { return userId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void addClaim(String key, String value) {
        claims.put(key, value);
    }

    public String getClaim(String key) {
        return claims.get(key);
    }
}

class AuthenticationResult {
    private final AuthenticationStatus status;
    private final User user;
    private final Session session;
    private final AuthToken accessToken;
    private final AuthToken refreshToken;
    private final String message;

    public AuthenticationResult(AuthenticationStatus status, User user, 
                                Session session, AuthToken accessToken, 
                                AuthToken refreshToken, String message) {
        this.status = status;
        this.user = user;
        this.session = session;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.message = message;
    }

    public AuthenticationStatus getStatus() { return status; }
    public User getUser() { return user; }
    public Session getSession() { return session; }
    public AuthToken getAccessToken() { return accessToken; }
    public AuthToken getRefreshToken() { return refreshToken; }
    public String getMessage() { return message; }
    public boolean isSuccess() { return status == AuthenticationStatus.SUCCESS; }
}

// ==================== INTERFACES ====================

interface AuthenticationStrategy {
    AuthenticationResult authenticate(Map<String, String> credentials);
}

interface PasswordHasher {
    String hash(String password, String salt);
    boolean verify(String password, String hash, String salt);
    String generateSalt();
}

interface TokenGenerator {
    AuthToken generateToken(User user, TokenType type);
    boolean validateToken(String token);
    AuthToken parseToken(String token);
}

interface SessionManager {
    Session createSession(User user, String deviceInfo, String ipAddress);
    boolean validateSession(String sessionId);
    void revokeSession(String sessionId);
    void revokeAllUserSessions(String userId);
    Session getSession(String sessionId);
}

interface UserRepository {
    void saveUser(User user);
    User findById(String userId);
    User findByUsername(String username);
    User findByEmail(String email);
    void updateUser(User user);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}

interface AuthenticationEventListener {
    void onLoginSuccess(User user, Session session);
    void onLoginFailure(String username, String reason);
    void onLogout(User user, Session session);
    void onAccountLocked(User user);
}

// ==================== IMPLEMENTATIONS ====================

class SHA256PasswordHasher implements PasswordHasher {
    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;

    @Override
    public String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update((salt + password).getBytes());
            byte[] hashedBytes = md.digest();
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    @Override
    public boolean verify(String password, String hash, String salt) {
        String computedHash = hash(password, salt);
        return computedHash.equals(hash);
    }

    @Override
    public String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
}

class JWTTokenGenerator implements TokenGenerator {
    private final String secretKey;
    private static final int ACCESS_TOKEN_EXPIRY_MINUTES = 15;
    private static final int REFRESH_TOKEN_EXPIRY_DAYS = 7;

    public JWTTokenGenerator(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public AuthToken generateToken(User user, TokenType type) {
        String token = generateJWT(user, type);
        LocalDateTime expiresAt = calculateExpiry(type);
        
        AuthToken authToken = new AuthToken(token, type, user.getUserId(), expiresAt);
        authToken.addClaim("username", user.getUsername());
        authToken.addClaim("role", user.getRole().name());
        
        return authToken;
    }

    @Override
    public boolean validateToken(String token) {
        try {
            // In real implementation, verify JWT signature and expiry
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AuthToken parseToken(String token) {
        // In real implementation, parse JWT and extract claims
        // This is a simplified version
        String userId = extractUserId(token);
        TokenType type = TokenType.ACCESS_TOKEN;
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        
        return new AuthToken(token, type, userId, expiresAt);
    }

    private String generateJWT(User user, TokenType type) {
        // Simplified JWT generation
        // In real implementation, use proper JWT library
        String header = Base64.getEncoder().encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        
        String payload = Base64.getEncoder().encodeToString(
            String.format("{\"userId\":\"%s\",\"username\":\"%s\",\"role\":\"%s\",\"type\":\"%s\"}", 
                user.getUserId(), user.getUsername(), user.getRole(), type).getBytes());
        
        String signature = generateSignature(header + "." + payload);
        
        return header + "." + payload + "." + signature;
    }

    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signature = mac.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Error generating signature", e);
        }
    }

    private LocalDateTime calculateExpiry(TokenType type) {
        return switch (type) {
            case ACCESS_TOKEN -> LocalDateTime.now().plusMinutes(ACCESS_TOKEN_EXPIRY_MINUTES);
            case REFRESH_TOKEN -> LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRY_DAYS);
            case RESET_TOKEN -> LocalDateTime.now().plusHours(1);
            case VERIFICATION_TOKEN -> LocalDateTime.now().plusDays(1);
        };
    }

    private String extractUserId(String token) {
        // Simplified extraction - in real implementation, parse JWT properly
        return UUID.randomUUID().toString();
    }
}

class DefaultSessionManager implements SessionManager {
    private final Map<String, Session> sessions;
    private final Map<String, Set<String>> userSessions; // userId -> sessionIds
    private static final int SESSION_EXPIRY_MINUTES = 30;

    public DefaultSessionManager() {
        this.sessions = new ConcurrentHashMap<>();
        this.userSessions = new ConcurrentHashMap<>();
    }

    @Override
    public Session createSession(User user, String deviceInfo, String ipAddress) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId, user.getUserId(), 
                                     deviceInfo, ipAddress, SESSION_EXPIRY_MINUTES);
        
        sessions.put(sessionId, session);
        userSessions.computeIfAbsent(user.getUserId(), k -> ConcurrentHashMap.newKeySet())
                   .add(sessionId);
        
        return session;
    }

    @Override
    public boolean validateSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null && session.isValid()) {
            session.updateLastAccessed();
            return true;
        }
        return false;
    }

    @Override
    public void revokeSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.revoke();
            String userId = session.getUserId();
            Set<String> userSessionSet = userSessions.get(userId);
            if (userSessionSet != null) {
                userSessionSet.remove(sessionId);
            }
        }
    }

    @Override
    public void revokeAllUserSessions(String userId) {
        Set<String> userSessionIds = userSessions.get(userId);
        if (userSessionIds != null) {
            for (String sessionId : userSessionIds) {
                Session session = sessions.get(sessionId);
                if (session != null) {
                    session.revoke();
                }
            }
            userSessionIds.clear();
        }
    }

    @Override
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }
}

class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> usersById;
    private final Map<String, User> usersByUsername;
    private final Map<String, User> usersByEmail;

    public InMemoryUserRepository() {
        this.usersById = new ConcurrentHashMap<>();
        this.usersByUsername = new ConcurrentHashMap<>();
        this.usersByEmail = new ConcurrentHashMap<>();
    }

    @Override
    public void saveUser(User user) {
        usersById.put(user.getUserId(), user);
        usersByUsername.put(user.getUsername().toLowerCase(), user);
        usersByEmail.put(user.getEmail().toLowerCase(), user);
    }

    @Override
    public User findById(String userId) {
        return usersById.get(userId);
    }

    @Override
    public User findByUsername(String username) {
        return usersByUsername.get(username.toLowerCase());
    }

    @Override
    public User findByEmail(String email) {
        return usersByEmail.get(email.toLowerCase());
    }

    @Override
    public void updateUser(User user) {
        saveUser(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return usersByUsername.containsKey(username.toLowerCase());
    }

    @Override
    public boolean existsByEmail(String email) {
        return usersByEmail.containsKey(email.toLowerCase());
    }
}

// ==================== AUTHENTICATION STRATEGIES ====================

class PasswordAuthenticationStrategy implements AuthenticationStrategy {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionManager sessionManager;
    private final TokenGenerator tokenGenerator;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 30;

    public PasswordAuthenticationStrategy(UserRepository userRepository,
                                         PasswordHasher passwordHasher,
                                         SessionManager sessionManager,
                                         TokenGenerator tokenGenerator) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public AuthenticationResult authenticate(Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        String deviceInfo = credentials.getOrDefault("deviceInfo", "Unknown");
        String ipAddress = credentials.getOrDefault("ipAddress", "0.0.0.0");

        User user = userRepository.findByUsername(username);
        
        if (user == null) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "Invalid username or password");
        }

        // Check if account is locked
        if (user.getLockedUntil() != null && 
            LocalDateTime.now().isBefore(user.getLockedUntil())) {
            return new AuthenticationResult(AuthenticationStatus.LOCKED, user, 
                null, null, null, "Account is temporarily locked");
        }

        // Verify password
        if (!passwordHasher.verify(password, user.getPasswordHash(), user.getSalt())) {
            user.incrementFailedAttempts();
            
            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.lockAccount(LOCKOUT_MINUTES);
                userRepository.updateUser(user);
                return new AuthenticationResult(AuthenticationStatus.LOCKED, user, 
                    null, null, null, "Account locked due to too many failed attempts");
            }
            
            userRepository.updateUser(user);
            return new AuthenticationResult(AuthenticationStatus.FAILED, user, 
                null, null, null, "Invalid username or password");
        }

        // Check if MFA is required
        if (user.isMfaEnabled()) {
            return new AuthenticationResult(AuthenticationStatus.REQUIRES_MFA, user, 
                null, null, null, "MFA verification required");
        }

        // Reset failed attempts on successful login
        user.resetFailedAttempts();
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateUser(user);

        // Create session and tokens
        Session session = sessionManager.createSession(user, deviceInfo, ipAddress);
        AuthToken accessToken = tokenGenerator.generateToken(user, TokenType.ACCESS_TOKEN);
        AuthToken refreshToken = tokenGenerator.generateToken(user, TokenType.REFRESH_TOKEN);

        return new AuthenticationResult(AuthenticationStatus.SUCCESS, user, 
            session, accessToken, refreshToken, "Authentication successful");
    }
}

class TokenAuthenticationStrategy implements AuthenticationStrategy {
    private final TokenGenerator tokenGenerator;
    private final UserRepository userRepository;

    public TokenAuthenticationStrategy(TokenGenerator tokenGenerator, 
                                      UserRepository userRepository) {
        this.tokenGenerator = tokenGenerator;
        this.userRepository = userRepository;
    }

    @Override
    public AuthenticationResult authenticate(Map<String, String> credentials) {
        String token = credentials.get("token");

        if (!tokenGenerator.validateToken(token)) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "Invalid or expired token");
        }

        AuthToken authToken = tokenGenerator.parseToken(token);
        
        if (authToken.isExpired()) {
            return new AuthenticationResult(AuthenticationStatus.EXPIRED, null, 
                null, null, null, "Token has expired");
        }

        User user = userRepository.findById(authToken.getUserId());
        
        if (user == null || !user.isActive()) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "User not found or inactive");
        }

        return new AuthenticationResult(AuthenticationStatus.SUCCESS, user, 
            null, authToken, null, "Token authentication successful");
    }
}

class OAuthAuthenticationStrategy implements AuthenticationStrategy {
    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    private final TokenGenerator tokenGenerator;

    public OAuthAuthenticationStrategy(UserRepository userRepository,
                                      SessionManager sessionManager,
                                      TokenGenerator tokenGenerator) {
        this.userRepository = userRepository;
        this.sessionManager = sessionManager;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public AuthenticationResult authenticate(Map<String, String> credentials) {
        String oauthProvider = credentials.get("provider");
        String oauthToken = credentials.get("oauthToken");
        String email = credentials.get("email");

        // In real implementation, verify OAuth token with provider
        // For now, simplified validation
        if (oauthToken == null || oauthToken.isEmpty()) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "Invalid OAuth token");
        }

        User user = userRepository.findByEmail(email);
        
        if (user == null) {
            // Create new user from OAuth profile
            String userId = UUID.randomUUID().toString();
            user = new User.Builder(userId, email, email)
                .role(UserRole.USER)
                .build();
            user.setEmailVerified(true);
            userRepository.saveUser(user);
        }

        Session session = sessionManager.createSession(user, 
            credentials.getOrDefault("deviceInfo", "OAuth Device"), 
            credentials.getOrDefault("ipAddress", "0.0.0.0"));
        
        AuthToken accessToken = tokenGenerator.generateToken(user, TokenType.ACCESS_TOKEN);
        AuthToken refreshToken = tokenGenerator.generateToken(user, TokenType.REFRESH_TOKEN);

        return new AuthenticationResult(AuthenticationStatus.SUCCESS, user, 
            session, accessToken, refreshToken, "OAuth authentication successful");
    }
}

// ==================== MAIN AUTHENTICATION SERVICE ====================

class AuthenticationService {
    private static AuthenticationService instance;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;
    private final SessionManager sessionManager;
    private final Map<AuthenticationMethod, AuthenticationStrategy> strategies;
    private final List<AuthenticationEventListener> eventListeners;

    private AuthenticationService() {
        this.userRepository = new InMemoryUserRepository();
        this.passwordHasher = new SHA256PasswordHasher();
        this.tokenGenerator = new JWTTokenGenerator("your-secret-key-change-in-production");
        this.sessionManager = new DefaultSessionManager();
        this.strategies = new HashMap<>();
        this.eventListeners = new ArrayList<>();
        
        initializeStrategies();
    }

    public static synchronized AuthenticationService getInstance() {
        if (instance == null) {
            instance = new AuthenticationService();
        }
        return instance;
    }

    private void initializeStrategies() {
        strategies.put(AuthenticationMethod.PASSWORD, 
            new PasswordAuthenticationStrategy(userRepository, passwordHasher, 
                                              sessionManager, tokenGenerator));
        strategies.put(AuthenticationMethod.TOKEN, 
            new TokenAuthenticationStrategy(tokenGenerator, userRepository));
        strategies.put(AuthenticationMethod.OAUTH, 
            new OAuthAuthenticationStrategy(userRepository, sessionManager, tokenGenerator));
    }

    // User Registration
    public AuthenticationResult registerUser(String username, String email, 
                                            String password, UserRole role) {
        // Validation
        if (userRepository.existsByUsername(username)) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "Email already registered");
        }

        // Create user
        String userId = UUID.randomUUID().toString();
        String salt = passwordHasher.generateSalt();
        String passwordHash = passwordHasher.hash(password, salt);

        User user = new User.Builder(userId, username, email)
            .passwordHash(passwordHash)
            .salt(salt)
            .role(role)
            .build();

        userRepository.saveUser(user);

        // Generate verification token
        AuthToken verificationToken = tokenGenerator.generateToken(user, 
            TokenType.VERIFICATION_TOKEN);

        return new AuthenticationResult(AuthenticationStatus.SUCCESS, user, 
            null, verificationToken, null, "User registered successfully");
    }

    // Login
    public AuthenticationResult login(AuthenticationMethod method, 
                                     Map<String, String> credentials) {
        AuthenticationStrategy strategy = strategies.get(method);
        
        if (strategy == null) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "Unsupported authentication method");
        }

        AuthenticationResult result = strategy.authenticate(credentials);

        // Notify listeners
        if (result.isSuccess() && result.getUser() != null) {
            notifyLoginSuccess(result.getUser(), result.getSession());
        } else {
            notifyLoginFailure(credentials.get("username"), result.getMessage());
        }

        return result;
    }

    // Logout
    public void logout(String sessionId) {
        Session session = sessionManager.getSession(sessionId);
        if (session != null) {
            User user = userRepository.findById(session.getUserId());
            sessionManager.revokeSession(sessionId);
            if (user != null) {
                notifyLogout(user, session);
            }
        }
    }

    // Refresh Token
    public AuthenticationResult refreshToken(String refreshToken) {
        if (!tokenGenerator.validateToken(refreshToken)) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "Invalid refresh token");
        }

        AuthToken oldToken = tokenGenerator.parseToken(refreshToken);
        User user = userRepository.findById(oldToken.getUserId());

        if (user == null) {
            return new AuthenticationResult(AuthenticationStatus.FAILED, null, 
                null, null, null, "User not found");
        }

        AuthToken newAccessToken = tokenGenerator.generateToken(user, TokenType.ACCESS_TOKEN);
        AuthToken newRefreshToken = tokenGenerator.generateToken(user, TokenType.REFRESH_TOKEN);

        return new AuthenticationResult(AuthenticationStatus.SUCCESS, user, 
            null, newAccessToken, newRefreshToken, "Token refreshed successfully");
    }

    // Verify Email
    public boolean verifyEmail(String verificationToken) {
        if (!tokenGenerator.validateToken(verificationToken)) {
            return false;
        }

        AuthToken token = tokenGenerator.parseToken(verificationToken);
        User user = userRepository.findById(token.getUserId());

        if (user != null) {
            user.setEmailVerified(true);
            userRepository.updateUser(user);
            return true;
        }

        return false;
    }

    // Reset Password
    public AuthToken generatePasswordResetToken(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return null;
        }

        return tokenGenerator.generateToken(user, TokenType.RESET_TOKEN);
    }

    public boolean resetPassword(String resetToken, String newPassword) {
        if (!tokenGenerator.validateToken(resetToken)) {
            return false;
        }

        AuthToken token = tokenGenerator.parseToken(resetToken);
        User user = userRepository.findById(token.getUserId());

        if (user != null) {
            String salt = passwordHasher.generateSalt();
            String passwordHash = passwordHasher.hash(newPassword, salt);
            
            // In real implementation, update password via repository
            userRepository.updateUser(user);
            
            // Revoke all sessions for security
            sessionManager.revokeAllUserSessions(user.getUserId());
            
            return true;
        }

        return false;
    }

    // Change Password
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId);
        
        if (user == null) {
            return false;
        }

        // Verify old password
        if (!passwordHasher.verify(oldPassword, user.getPasswordHash(), user.getSalt())) {
            return false;
        }

        // Set new password
        String salt = passwordHasher.generateSalt();
        String passwordHash = passwordHasher.hash(newPassword, salt);
        
        // Update user (in real implementation)
        userRepository.updateUser(user);
        
        return true;
    }

    // Enable MFA
    public String enableMFA(String userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            return null;
        }

        // Generate MFA secret
        String mfaSecret = generateMFASecret();
        user.setMfaSecret(mfaSecret);
        userRepository.updateUser(user);

        return mfaSecret;
    }

    // Verify MFA Code
    public boolean verifyMFACode(String userId, String code) {
        User user = userRepository.findById(userId);
        if (user == null || user.getMfaSecret() == null) {
            return false;
        }

        // In real implementation, verify TOTP code
        // For now, simplified validation
        return code != null && code.length() == 6;
    }

    // Validate Session
    public boolean validateSession(String sessionId) {
        return sessionManager.validateSession(sessionId);
    }

    // Get User by Session
    public User getUserBySession(String sessionId) {
        Session session = sessionManager.getSession(sessionId);
        if (session != null && session.isValid()) {
            return userRepository.findById(session.getUserId());
        }
        return null;
    }

    // Authorization check
    public boolean hasPermission(String userId, UserRole requiredRole) {
        User user = userRepository.findById(userId);
        if (user == null) {
            return false;
        }

        // Simple role hierarchy check
        return user.getRole().ordinal() <= requiredRole.ordinal();
    }

    // Event listener management
    public void addListener(AuthenticationEventListener listener) {
        eventListeners.add(listener);
    }

    private void notifyLoginSuccess(User user, Session session) {
        for (AuthenticationEventListener listener : eventListeners) {
            listener.onLoginSuccess(user, session);
        }
    }

    private void notifyLoginFailure(String username, String reason) {
        for (AuthenticationEventListener listener : eventListeners) {
            listener.onLoginFailure(username, reason);
        }
    }

    private void notifyLogout(User user, Session session) {
        for (AuthenticationEventListener listener : eventListeners) {
            listener.onLogout(user, session);
        }
    }

    private void notifyAccountLocked(User user) {
        for (AuthenticationEventListener listener : eventListeners) {
            listener.onAccountLocked(user);
        }
    }

    private String generateMFASecret() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Cleanup method for expired sessions
    public void cleanupExpiredSessions() {
        if (sessionManager instanceof DefaultSessionManager) {
            ((DefaultSessionManager) sessionManager).cleanupExpiredSessions();
        }
    }
}

// ==================== DEMO AND USAGE ====================

class LoggingAuthenticationListener implements AuthenticationEventListener {
    @Override
    public void onLoginSuccess(User user, Session session) {
        System.out.println("[LOGIN SUCCESS] User: " + user.getUsername() + 
                         ", SessionId: " + session.getSessionId());
    }

    @Override
    public void onLoginFailure(String username, String reason) {
        System.out.println("[LOGIN FAILED] Username: " + username + 
                         ", Reason: " + reason);
    }

    @Override
    public void onLogout(User user, Session session) {
        System.out.println("[LOGOUT] User: " + user.getUsername() + 
                         ", SessionId: " + session.getSessionId());
    }

    @Override
    public void onAccountLocked(User user) {
        System.out.println("[ACCOUNT LOCKED] User: " + user.getUsername());
    }
}

public class AuthenticationSystemDemo {
    public static void main(String[] args) {
        System.out.println("=== AUTHENTICATION SYSTEM DEMO ===\n");

        // Get authentication service instance
        AuthenticationService authService = AuthenticationService.getInstance();
        
        // Add event listener
        authService.addListener(new LoggingAuthenticationListener());

        // ===== 1. USER REGISTRATION =====
        System.out.println("1. USER REGISTRATION");
        System.out.println("-".repeat(50));
        
        AuthenticationResult registerResult = authService.registerUser(
            "john_doe", "john@example.com", "SecurePass123!", UserRole.USER);
        
        if (registerResult.isSuccess()) {
            System.out.println("✓ Registration successful");
            System.out.println("  User ID: " + registerResult.getUser().getUserId());
            System.out.println("  Verification Token: " + registerResult.getAccessToken().getToken());
        }
        System.out.println();

        // ===== 2. EMAIL VERIFICATION =====
        System.out.println("2. EMAIL VERIFICATION");
        System.out.println("-".repeat(50));
        
        if (registerResult.getAccessToken() != null) {
            boolean verified = authService.verifyEmail(registerResult.getAccessToken().getToken());
            System.out.println(verified ? "✓ Email verified" : "✗ Verification failed");
        }
        System.out.println();

        // ===== 3. PASSWORD LOGIN =====
        System.out.println("3. PASSWORD AUTHENTICATION");
        System.out.println("-".repeat(50));
        
        Map<String, String> loginCredentials = new HashMap<>();
        loginCredentials.put("username", "john_doe");
        loginCredentials.put("password", "SecurePass123!");
        loginCredentials.put("deviceInfo", "Chrome on MacOS");
        loginCredentials.put("ipAddress", "192.168.1.100");
        
        AuthenticationResult loginResult = authService.login(
            AuthenticationMethod.PASSWORD, loginCredentials);
        
        if (loginResult.isSuccess()) {
            System.out.println("✓ Login successful");
            System.out.println("  Access Token: " + loginResult.getAccessToken().getToken().substring(0, 50) + "...");
            System.out.println("  Session ID: " + loginResult.getSession().getSessionId());
            System.out.println("  Expires At: " + loginResult.getAccessToken().getExpiresAt());
        }
        System.out.println();

        // ===== 4. FAILED LOGIN ATTEMPTS =====
        System.out.println("4. FAILED LOGIN ATTEMPTS");
        System.out.println("-".repeat(50));
        
        Map<String, String> wrongCredentials = new HashMap<>();
        wrongCredentials.put("username", "john_doe");
        wrongCredentials.put("password", "WrongPassword");
        
        for (int i = 1; i <= 3; i++) {
            AuthenticationResult failedResult = authService.login(
                AuthenticationMethod.PASSWORD, wrongCredentials);
            System.out.println("  Attempt " + i + ": " + failedResult.getMessage());
        }
        System.out.println();

        // ===== 5. TOKEN AUTHENTICATION =====
        System.out.println("5. TOKEN AUTHENTICATION");
        System.out.println("-".repeat(50));
        
        if (loginResult.isSuccess()) {
            Map<String, String> tokenCredentials = new HashMap<>();
            tokenCredentials.put("token", loginResult.getAccessToken().getToken());
            
            AuthenticationResult tokenResult = authService.login(
                AuthenticationMethod.TOKEN, tokenCredentials);
            
            System.out.println(tokenResult.isSuccess() ? 
                "✓ Token authentication successful" : 
                "✗ Token authentication failed");
        }
        System.out.println();

        // ===== 6. REFRESH TOKEN =====
        System.out.println("6. REFRESH TOKEN");
        System.out.println("-".repeat(50));
        
        if (loginResult.isSuccess() && loginResult.getRefreshToken() != null) {
            AuthenticationResult refreshResult = authService.refreshToken(
                loginResult.getRefreshToken().getToken());
            
            if (refreshResult.isSuccess()) {
                System.out.println("✓ Token refreshed successfully");
                System.out.println("  New Access Token: " + 
                    refreshResult.getAccessToken().getToken().substring(0, 50) + "...");
            }
        }
        System.out.println();

        // ===== 7. SESSION VALIDATION =====
        System.out.println("7. SESSION VALIDATION");
        System.out.println("-".repeat(50));
        
        if (loginResult.isSuccess()) {
            String sessionId = loginResult.getSession().getSessionId();
            boolean isValid = authService.validateSession(sessionId);
            System.out.println("  Session valid: " + isValid);
            
            User sessionUser = authService.getUserBySession(sessionId);
            if (sessionUser != null) {
                System.out.println("  User from session: " + sessionUser.getUsername());
            }
        }
        System.out.println();

        // ===== 8. OAUTH LOGIN =====
        System.out.println("8. OAUTH AUTHENTICATION");
        System.out.println("-".repeat(50));
        
        Map<String, String> oauthCredentials = new HashMap<>();
        oauthCredentials.put("provider", "google");
        oauthCredentials.put("oauthToken", "mock-oauth-token-12345");
        oauthCredentials.put("email", "jane@example.com");
        oauthCredentials.put("deviceInfo", "Mobile App");
        
        AuthenticationResult oauthResult = authService.login(
            AuthenticationMethod.OAUTH, oauthCredentials);
        
        if (oauthResult.isSuccess()) {
            System.out.println("✓ OAuth login successful");
            System.out.println("  User: " + oauthResult.getUser().getEmail());
            System.out.println("  Email Verified: " + oauthResult.getUser().isEmailVerified());
        }
        System.out.println();

        // ===== 9. PASSWORD RESET =====
        System.out.println("9. PASSWORD RESET");
        System.out.println("-".repeat(50));
        
        AuthToken resetToken = authService.generatePasswordResetToken("john@example.com");
        if (resetToken != null) {
            System.out.println("✓ Reset token generated");
            System.out.println("  Token: " + resetToken.getToken().substring(0, 50) + "...");
            
            boolean resetSuccess = authService.resetPassword(
                resetToken.getToken(), "NewSecurePass456!");
            System.out.println("  Password reset: " + 
                (resetSuccess ? "✓ Success" : "✗ Failed"));
        }
        System.out.println();

        // ===== 10. AUTHORIZATION CHECK =====
        System.out.println("10. AUTHORIZATION CHECK");
        System.out.println("-".repeat(50));
        
        if (loginResult.isSuccess()) {
            String userId = loginResult.getUser().getUserId();
            System.out.println("  User has USER role: " + 
                authService.hasPermission(userId, UserRole.USER));
            System.out.println("  User has ADMIN role: " + 
                authService.hasPermission(userId, UserRole.ADMIN));
        }
        System.out.println();

        // ===== 11. LOGOUT =====
        System.out.println("11. LOGOUT");
        System.out.println("-".repeat(50));
        
        if (loginResult.isSuccess()) {
            String sessionId = loginResult.getSession().getSessionId();
            authService.logout(sessionId);
            
            boolean stillValid = authService.validateSession(sessionId);
            System.out.println("  Session after logout: " + 
                (stillValid ? "Still valid" : "✓ Revoked"));
        }
        System.out.println();

        // ===== 12. REGISTER ADMIN USER =====
        System.out.println("12. ADMIN USER REGISTRATION");
        System.out.println("-".repeat(50));
        
        AuthenticationResult adminResult = authService.registerUser(
            "admin_user", "admin@example.com", "AdminPass123!", UserRole.ADMIN);
        
        if (adminResult.isSuccess()) {
            System.out.println("✓ Admin user registered");
            System.out.println("  Role: " + adminResult.getUser().getRole());
        }
        System.out.println();

        System.out.println("=== DEMO COMPLETED ===");
    }
