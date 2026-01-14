import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Logging Framework
 * Time to complete: 35-45 minutes
 * Focus: Strategy pattern for log levels and appenders
 */

// ==================== Log Level ====================
enum LogLevel {
    DEBUG(1), INFO(2), WARN(3), ERROR(4);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }
}

// ==================== Log Message ====================
class LogMessage {
    private final LogLevel level;
    private final String message;
    private final LocalDateTime timestamp;
    private final String source;

    public LogMessage(LogLevel level, String message, String source) {
        this.level = level;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.source = source;
    }

    public LogLevel getLevel() { return level; }

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] %s",
            timestamp.toString(),
            level,
            source,
            message
        );
    }
}

// ==================== Log Appender Interface ====================
interface LogAppender {
    void append(LogMessage message);
}

// ==================== Console Appender ====================
class ConsoleAppender implements LogAppender {
    @Override
    public void append(LogMessage message) {
        System.out.println("[CONSOLE] " + message);
    }
}

// ==================== File Appender ====================
class FileAppender implements LogAppender {
    private final String filename;

    public FileAppender(String filename) {
        this.filename = filename;
    }

    @Override
    public void append(LogMessage message) {
        System.out.println("[FILE:" + filename + "] " + message);
    }
}

// ==================== Logger ====================
class Logger {
    private final String name;
    private LogLevel minLevel;
    private final List<LogAppender> appenders;

    public Logger(String name, LogLevel minLevel) {
        this.name = name;
        this.minLevel = minLevel;
        this.appenders = new ArrayList<>();
    }

    public void addAppender(LogAppender appender) {
        appenders.add(appender);
    }

    public void setMinLevel(LogLevel level) {
        this.minLevel = level;
    }

    private void log(LogLevel level, String message) {
        if (level.getSeverity() >= minLevel.getSeverity()) {
            LogMessage logMessage = new LogMessage(level, message, name);
            
            for (LogAppender appender : appenders) {
                appender.append(logMessage);
            }
        }
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message);
    }
}

// ==================== Logger Factory ====================
class LoggerFactory {
    private static final Map<String, Logger> loggers = new HashMap<>();

    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, k -> {
            Logger logger = new Logger(name, LogLevel.INFO);
            logger.addAppender(new ConsoleAppender());
            return logger;
        });
    }
}

// ==================== Demo ====================
public class LoggingFrameworkInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Logging Framework Demo ===\n");

        Logger appLogger = LoggerFactory.getLogger("Application");
        Logger dbLogger = LoggerFactory.getLogger("Database");

        dbLogger.addAppender(new FileAppender("database.log"));

        System.out.println("--- Test 1: Different Log Levels ---");
        appLogger.debug("This is a debug message");
        appLogger.info("Application started");
        appLogger.warn("Low memory warning");
        appLogger.error("Failed to connect to service");

        System.out.println("\n--- Test 2: Database Logger ---");
        dbLogger.info("Database connection established");
        dbLogger.error("Query execution failed");

        System.out.println("\n--- Test 3: Change Log Level to DEBUG ---");
        appLogger.setMinLevel(LogLevel.DEBUG);
        appLogger.debug("Now debug messages will show");
        appLogger.info("Info message");

        System.out.println("\n✅ Demo complete!");
    }
}
