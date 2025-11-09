import java.util.*;
import java.io.*;
import java.time.*;
import java.time.format.*;

// ==================== Enums ====================
// Why Enums? Type safety, ordered severity levels, easy comparison

enum LogLevel {
    DEBUG(1),    // Detailed debug information
    INFO(2),     // General informational messages
    WARN(3),     // Warning messages
    ERROR(4),    // Error messages
    FATAL(5);    // Critical errors

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    // Check if this level should be logged
    public boolean shouldLog(LogLevel configuredLevel) {
        return this.severity >= configuredLevel.severity;
    }
}

// ==================== Log Message ====================
// Represents a single log entry with all metadata

class LogMessage {
    private LogLevel level;
    private String message;
    private String className;
    private String methodName;
    private LocalDateTime timestamp;
    private long threadId;
    private String threadName;
    private Throwable throwable;  // For exception logging

    public LogMessage(LogLevel level, String message, String className, String methodName) {
        this.level = level;
        this.message = message;
        this.className = className;
        this.methodName = methodName;
        this.timestamp = LocalDateTime.now();
        this.threadId = Thread.currentThread().getId();
        this.threadName = Thread.currentThread().getName();
    }

    // Constructor with exception
    public LogMessage(LogLevel level, String message, String className, 
                     String methodName, Throwable throwable) {
        this(level, message, className, methodName);
        this.throwable = throwable;
    }

    // Getters
    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public long getThreadId() { return threadId; }
    public String getThreadName() { return threadName; }
    public Throwable getThrowable() { return throwable; }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s.%s: %s",
            level, timestamp, className, methodName, message);
    }
}

// ==================== Log Formatter (Strategy Pattern) ====================
// Why Strategy Pattern? Different formatting styles
// Benefits: Interchangeable, easy to add new formats

interface LogFormatter {
    String format(LogMessage message);
}

// Simple text format
class SimpleFormatter implements LogFormatter {
    @Override
    public String format(LogMessage message) {
        return String.format("[%s] %s - %s",
            message.getLevel(),
            message.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_TIME),
            message.getMessage()
        );
    }
}

// Detailed format with class, method, thread info
class DetailedFormatter implements LogFormatter {
    @Override
    public String format(LogMessage message) {
        StringBuilder sb = new StringBuilder();
        sb.append(message.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
          .append(" [").append(message.getLevel()).append("]")
          .append(" [").append(message.getThreadName()).append("]")
          .append(" ").append(message.getClassName())
          .append(".").append(message.getMethodName())
          .append(" - ").append(message.getMessage());
        
        // Add exception stack trace if present
        if (message.getThrowable() != null) {
            sb.append("\n").append(getStackTrace(message.getThrowable()));
        }
        
        return sb.toString();
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}

// JSON format (for structured logging)
class JsonFormatter implements LogFormatter {
    @Override
    public String format(LogMessage message) {
        return String.format(
            "{\"timestamp\":\"%s\",\"level\":\"%s\",\"thread\":\"%s\",\"class\":\"%s\",\"method\":\"%s\",\"message\":\"%s\"}",
            message.getTimestamp(),
            message.getLevel(),
            message.getThreadName(),
            message.getClassName(),
            message.getMethodName(),
            message.getMessage().replace("\"", "\\\"")
        );
    }
}

// ==================== Log Appender (Chain of Responsibility) ====================
// Why Pattern? Multiple output destinations, process in chain
// Benefits: Can log to multiple places, easy to add new appenders

interface LogAppender {
    void append(LogMessage message);
    void setNextAppender(LogAppender next);
}

// Base appender with chain handling
abstract class BaseAppender implements LogAppender {
    protected LogFormatter formatter;
    protected LogAppender nextAppender;

    public BaseAppender(LogFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void setNextAppender(LogAppender next) {
        this.nextAppender = next;
    }

    protected void passToNext(LogMessage message) {
        if (nextAppender != null) {
            nextAppender.append(message);
        }
    }
}

// Console appender (writes to System.out)
class ConsoleAppender extends BaseAppender {
    public ConsoleAppender(LogFormatter formatter) {
        super(formatter);
    }

    @Override
    public void append(LogMessage message) {
        String formattedMessage = formatter.format(message);
        System.out.println(formattedMessage);
        passToNext(message);
    }
}

// File appender (writes to log file)
class FileAppender extends BaseAppender {
    private String filePath;
    private BufferedWriter writer;
    private long maxFileSize = 10 * 1024 * 1024;  // 10 MB
    private int maxBackupFiles = 5;

    public FileAppender(String filePath, LogFormatter formatter) {
        super(formatter);
        this.filePath = filePath;
        try {
            this.writer = new BufferedWriter(new FileWriter(filePath, true));
        } catch (IOException e) {
            System.err.println("Failed to open log file: " + e.getMessage());
        }
    }

    @Override
    public synchronized void append(LogMessage message) {
        try {
            String formattedMessage = formatter.format(message);
            writer.write(formattedMessage);
            writer.newLine();
            writer.flush();
            
            // Check if file rotation needed
            checkFileRotation();
            
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
        
        passToNext(message);
    }

    private void checkFileRotation() throws IOException {
        File file = new File(filePath);
        if (file.length() > maxFileSize) {
            rotateFiles();
        }
    }

    private void rotateFiles() throws IOException {
        writer.close();
        
        // Rotate existing backup files
        for (int i = maxBackupFiles - 1; i >= 1; i--) {
            File old = new File(filePath + "." + i);
            File newer = new File(filePath + "." + (i + 1));
            if (old.exists()) {
                old.renameTo(newer);
            }
        }
        
        // Move current to .1
        File current = new File(filePath);
        File backup = new File(filePath + ".1");
        current.renameTo(backup);
        
        // Create new file
        writer = new BufferedWriter(new FileWriter(filePath, true));
    }

    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("Failed to close log file: " + e.getMessage());
        }
    }
}

// Database appender (writes to database)
class DatabaseAppender extends BaseAppender {
    // Simulated database connection
    private boolean connected = true;

    public DatabaseAppender(LogFormatter formatter) {
        super(formatter);
    }

    @Override
    public void append(LogMessage message) {
        if (!connected) {
            System.err.println("Database not connected, skipping log");
            passToNext(message);
            return;
        }

        // Simulate database insert
        String formattedMessage = formatter.format(message);
        System.out.println("[DB] " + formattedMessage);
        
        // In real implementation:
        // INSERT INTO logs (timestamp, level, class, method, message)
        // VALUES (?, ?, ?, ?, ?)
        
        passToNext(message);
    }
}

// ==================== Logger Configuration ====================
// Stores logging configuration

class LoggerConfig {
    private LogLevel minimumLevel;
    private List<LogAppender> appenders;
    private boolean asyncLogging;

    public LoggerConfig() {
        this.minimumLevel = LogLevel.INFO;  // Default
        this.appenders = new ArrayList<>();
        this.asyncLogging = false;
    }

    public LogLevel getMinimumLevel() { return minimumLevel; }
    public void setMinimumLevel(LogLevel level) { this.minimumLevel = level; }
    
    public List<LogAppender> getAppenders() { return appenders; }
    public void addAppender(LogAppender appender) { appenders.add(appender); }
    
    public boolean isAsyncLogging() { return asyncLogging; }
    public void setAsyncLogging(boolean async) { this.asyncLogging = async; }
}

// ==================== Logger ====================
// Main logging interface used by application code

class Logger {
    private String className;
    private LoggerConfig config;

    public Logger(String className, LoggerConfig config) {
        this.className = className;
        this.config = config;
    }

    // Main logging methods
    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }

    public void fatal(String message) {
        log(LogLevel.FATAL, message, null);
    }

    public void fatal(String message, Throwable throwable) {
        log(LogLevel.FATAL, message, throwable);
    }

    // Core logging logic
    private void log(LogLevel level, String message, Throwable throwable) {
        // Check if level should be logged
        if (!level.shouldLog(config.getMinimumLevel())) {
            return;  // Skip logging (below configured level)
        }

        // Get calling method info
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String methodName = stackTrace.length > 3 ? stackTrace[3].getMethodName() : "unknown";

        // Create log message
        LogMessage logMessage = throwable == null ?
            new LogMessage(level, message, className, methodName) :
            new LogMessage(level, message, className, methodName, throwable);

        // Send to appenders
        for (LogAppender appender : config.getAppenders()) {
            if (config.isAsyncLogging()) {
                // Async logging (don't block application)
                new Thread(() -> appender.append(logMessage)).start();
            } else {
                // Sync logging
                appender.append(logMessage);
            }
        }
    }

    // Convenience methods for formatted logging
    public void info(String format, Object... args) {
        info(String.format(format, args));
    }

    public void debug(String format, Object... args) {
        debug(String.format(format, args));
    }

    public void warn(String format, Object... args) {
        warn(String.format(format, args));
    }

    public void error(String format, Object... args) {
        error(String.format(format, args));
    }
}

// ==================== Logger Factory (Factory Pattern) ====================
// Why Factory? Centralized logger creation, consistent configuration
// Benefits: Single point of configuration, easy to get logger

class LoggerFactory {
    private static LoggerFactory instance;
    private LoggerConfig config;
    private Map<String, Logger> loggers;  // Cache loggers per class

    private LoggerFactory() {
        this.config = new LoggerConfig();
        this.loggers = new HashMap<>();
        setupDefaultConfiguration();
    }

    public static synchronized LoggerFactory getInstance() {
        if (instance == null) {
            instance = new LoggerFactory();
        }
        return instance;
    }

    private void setupDefaultConfiguration() {
        // Default: INFO level, console output
        config.setMinimumLevel(LogLevel.INFO);
        config.addAppender(new ConsoleAppender(new SimpleFormatter()));
    }

    public Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    public Logger getLogger(String className) {
        // Cache loggers (don't create new for same class)
        return loggers.computeIfAbsent(className, 
            name -> new Logger(name, config));
    }

    public LoggerConfig getConfig() {
        return config;
    }

    // Configure logging programmatically
    public void configure(LogLevel level, List<LogAppender> appenders) {
        config.setMinimumLevel(level);
        config.getAppenders().clear();
        for (LogAppender appender : appenders) {
            config.addAppender(appender);
        }
    }
}

// ==================== Log Manager (Singleton) ====================
// Why Singleton? Single point of control for entire logging system
// Manages global configuration, log rotation, performance monitoring

class LogManager {
    private static LogManager instance;
    private LoggerFactory factory;
    private Map<String, Long> logCounts;  // Track logs per level
    private boolean enabled;

    private LogManager() {
        this.factory = LoggerFactory.getInstance();
        this.logCounts = new HashMap<>();
        this.enabled = true;
        
        // Initialize counters
        for (LogLevel level : LogLevel.values()) {
            logCounts.put(level.name(), 0L);
        }
    }

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public LoggerFactory getFactory() {
        return factory;
    }

    public void enable() {
        this.enabled = true;
        System.out.println("Logging enabled");
    }

    public void disable() {
        this.enabled = false;
        System.out.println("Logging disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Track statistics
    public void incrementCount(LogLevel level) {
        synchronized (logCounts) {
            logCounts.put(level.name(), logCounts.get(level.name()) + 1);
        }
    }

    public void printStatistics() {
        System.out.println("\n=== Logging Statistics ===");
        for (LogLevel level : LogLevel.values()) {
            System.out.println(level + ": " + logCounts.get(level.name()) + " logs");
        }
    }

    // Shutdown gracefully (flush all appenders)
    public void shutdown() {
        System.out.println("Shutting down logging system...");
        // In real implementation: close all file handles, flush buffers
    }
}

// ==================== Demonstration Classes ====================
// Simulates real application using the logging framework

class UserService {
    // Get logger for this class
    private static final Logger logger = LoggerFactory.getInstance()
        .getLogger(UserService.class);

    public void createUser(String username) {
        logger.info("Creating user: %s", username);
        
        try {
            // Simulate user creation logic
            if (username == null || username.isEmpty()) {
                logger.warn("Username is empty!");
                throw new IllegalArgumentException("Username cannot be empty");
            }
            
            logger.debug("Validating username: %s", username);
            
            // Simulate database operation
            Thread.sleep(100);
            
            logger.info("User created successfully: %s", username);
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create user", e);
        } catch (InterruptedException e) {
            logger.error("Operation interrupted", e);
        }
    }

    public void deleteUser(String username) {
        logger.warn("Deleting user: %s", username);
        
        // Simulate critical operation
        try {
            Thread.sleep(50);
            logger.info("User deleted: %s", username);
        } catch (InterruptedException e) {
            logger.fatal("Critical error during user deletion", e);
        }
    }
}

class PaymentService {
    private static final Logger logger = LoggerFactory.getInstance()
        .getLogger(PaymentService.class);

    public void processPayment(double amount) {
        logger.info("Processing payment: $%.2f", amount);
        
        try {
            if (amount <= 0) {
                logger.error("Invalid payment amount: $%.2f", amount);
                return;
            }
            
            if (amount > 10000) {
                logger.warn("Large payment detected: $%.2f", amount);
            }
            
            // Simulate payment processing
            Thread.sleep(200);
            
            logger.info("Payment processed successfully: $%.2f", amount);
            
        } catch (Exception e) {
            logger.fatal("Payment processing failed", e);
        }
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates logging framework with various configurations

public class LoggingFramework {
    public static void main(String[] args) {
        System.out.println("========== Logging Framework Demo ==========\n");
        
        LoggerFactory factory = LoggerFactory.getInstance();
        LogManager manager = LogManager.getInstance();
        
        // ===== Demo 1: Simple Console Logging =====
        System.out.println("===== Demo 1: Simple Console Logging =====\n");
        
        // Default configuration (INFO level, console output)
        Logger logger1 = factory.getLogger("Demo1");
        
        logger1.debug("This won't show (below INFO level)");
        logger1.info("Application started");
        logger1.warn("This is a warning");
        logger1.error("This is an error");
        
        // ===== Demo 2: Multiple Log Levels =====
        System.out.println("\n\n===== Demo 2: All Log Levels =====\n");
        
        // Change to DEBUG level to see everything
        factory.getConfig().setMinimumLevel(LogLevel.DEBUG);
        Logger logger2 = factory.getLogger("Demo2");
        
        logger2.debug("Debug message - detailed info");
        logger2.info("Info message - general info");
        logger2.warn("Warn message - potential issue");
        logger2.error("Error message - error occurred");
        logger2.fatal("Fatal message - critical failure");
        
        // ===== Demo 3: Different Formatters =====
        System.out.println("\n\n===== Demo 3: Different Formatters =====\n");
        
        // Configure with detailed formatter
        ConsoleAppender detailedAppender = new ConsoleAppender(new DetailedFormatter());
        factory.configure(LogLevel.INFO, Arrays.asList(detailedAppender));
        
        Logger logger3 = factory.getLogger("Demo3");
        logger3.info("Using detailed formatter");
        logger3.error("Error with detailed format");
        
        // ===== Demo 4: Multiple Appenders (Chain) =====
        System.out.println("\n\n===== Demo 4: Multiple Appenders =====\n");
        
        // Log to both console and file
        ConsoleAppender consoleAppender = new ConsoleAppender(new SimpleFormatter());
        FileAppender fileAppender = new FileAppender("application.log", new DetailedFormatter());
        
        // Chain appenders
        consoleAppender.setNextAppender(fileAppender);
        
        factory.configure(LogLevel.INFO, Arrays.asList(consoleAppender));
        
        Logger logger4 = factory.getLogger("Demo4");
        logger4.info("This goes to both console and file");
        logger4.error("Error logged to multiple destinations");
        
        // ===== Demo 5: Exception Logging =====
        System.out.println("\n\n===== Demo 5: Exception Logging =====\n");
        
        Logger logger5 = factory.getLogger("Demo5");
        
        try {
            int result = 10 / 0;  // Cause exception
        } catch (ArithmeticException e) {
            logger5.error("Arithmetic error occurred", e);
        }
        
        // ===== Demo 6: Real Application Usage =====
        System.out.println("\n\n===== Demo 6: Real Application Services =====\n");
        
        UserService userService = new UserService();
        userService.createUser("alice");
        userService.createUser("");  // Will log warning and error
        userService.deleteUser("bob");
        
        System.out.println();
        
        PaymentService paymentService = new PaymentService();
        paymentService.processPayment(99.99);
        paymentService.processPayment(-10);  // Invalid, will log error
        paymentService.processPayment(15000);  // Large, will log warning
        
        // ===== Demo 7: JSON Formatted Logs =====
        System.out.println("\n\n===== Demo 7: JSON Formatted Logs =====\n");
        
        ConsoleAppender jsonAppender = new ConsoleAppender(new JsonFormatter());
        factory.configure(LogLevel.INFO, Arrays.asList(jsonAppender));
        
        Logger logger7 = factory.getLogger("Demo7");
        logger7.info("JSON formatted log entry");
        logger7.error("Error in JSON format");
        
        // ===== Show Statistics =====
        manager.printStatistics();
        
        // ===== Cleanup =====
        fileAppender.close();
        manager.shutdown();
        
        System.out.println("\n========== Demo Complete ==========");
    }
}
