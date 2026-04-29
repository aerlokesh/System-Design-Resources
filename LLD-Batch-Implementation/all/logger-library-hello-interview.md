# Logger Library - HELLO Interview Framework

> **Companies**: Microsoft, Amazon, Google, Uber  
> **Difficulty**: Medium  
> **Pattern**: Strategy (sink types) + Chain of Responsibility (log enrichment) + Singleton (logger instance) + Builder (configuration)  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Logger Library?

A configurable library that applications use to log messages to various **sinks** (console, file, database). Each message has a **level** (FATAL, ERROR, WARN, INFO, DEBUG), a **namespace** (which part of the app generated it), and **content**. The library routes messages to the correct sink based on level, filters messages below the configured threshold, and enriches them with metadata (timestamps, namespace) before writing.

**Real-world**: Log4j/SLF4J (Java), Serilog (.NET), Winston (Node.js), Python logging module, Azure Monitor, AWS CloudWatch Logs.

### For L63 Microsoft Interview

1. **Strategy Pattern**: Sink implementations (ConsoleSink, FileSink, DatabaseSink) — same interface, different output targets
2. **Chain of Responsibility**: Log enrichers pipeline — TimestampEnricher → NamespaceEnricher → ThreadEnricher → Sink
3. **Builder Pattern**: LoggerConfiguration — fluent API for setting up sinks, levels, formats
4. **Singleton**: Logger instance per application (or named loggers)
5. **Level-based filtering**: Priority ordering FATAL > ERROR > WARN > INFO > DEBUG — configurable threshold
6. **Level-to-Sink routing**: Each level maps to a specific sink — multiple levels can share one sink

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What sink types?" → Console, File, Database — extensible for custom sinks
- "How are levels mapped to sinks?" → Configuration: each level → one sink, multiple levels can share a sink
- "Level filtering?" → Configurable threshold: if set to INFO, only FATAL/ERROR/WARN/INFO logged (DEBUG filtered out)
- "Message enrichment?" → Automatically add timestamp, namespace, thread name before writing to sink
- "Thread safety?" → Yes — multiple threads may log concurrently
- "Multiple loggers?" → Support named loggers (e.g., "PaymentService", "AuthService") with different configs

### Functional Requirements (P0)
1. **Log messages** with content, level, and namespace
2. **Route to sink** based on message level (level → sink mapping from config)
3. **Filter by level** — only log messages at or above configured threshold
4. **Enrich messages** with timestamp, namespace, thread info before writing
5. **Multiple sink types** — Console, File, Database — extensible
6. **Configurable** — time format, logging level, sink type, sink details (file path, etc.)

### Functional Requirements (P1)
- Async logging (non-blocking writes)
- Log rotation (file size/date based)
- Structured logging (JSON output)
- Multiple sinks per level

### Non-Functional
- Thread-safe logging from multiple threads
- O(1) level filtering (priority comparison)
- O(1) sink routing (level → sink map lookup)
- Minimal performance overhead on the calling application
- SOLID principles throughout

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────────────────────────────────────────────────────┐
│                          Logger                                   │
│  - config: LoggerConfiguration                                   │
│  - levelSinkMap: Map<LogLevel, Sink>                             │
│  - enricherChain: LogEnricher (Chain of Responsibility)          │
│  - minLevel: LogLevel (threshold)                                │
│  - log(level, namespace, content)                                │
│  - fatal/error/warn/info/debug(namespace, content)               │
└──────────────┬───────────────────────────────────────────────────┘
               │ uses
     ┌─────────┼──────────┬──────────────┬──────────────┐
     ▼         ▼          ▼              ▼              ▼
┌──────────┐ ┌────────────────┐ ┌─────────────────┐ ┌────────────────┐
│ LogMessage│ │     Sink       │ │  LogEnricher    │ │LoggerConfig    │
│          │ │  (interface)   │ │  (Chain of Resp)│ │  (Builder)     │
│ - content│ ├────────────────┤ ├─────────────────┤ ├────────────────┤
│ - level  │ │ ConsoleSink    │ │ TimestampEnricher│ │ - timeFormat  │
│ - namesp.│ │ FileSink       │ │ NamespaceEnricher│ │ - minLevel    │
│ - timestamp│ │ DatabaseSink  │ │ ThreadEnricher  │ │ - sinkConfigs │
│ - thread │ │ AsyncSinkDeco  │ │ LevelTagEnricher│ │ - build()     │
│ - metadata│ └────────────────┘ └─────────────────┘ └────────────────┘
└──────────┘

┌────────────────────┐
│    LogLevel (enum)  │
├────────────────────┤
│ FATAL (0) — highest│
│ ERROR (1)          │
│ WARN  (2)          │
│ INFO  (3)          │
│ DEBUG (4) — lowest │
└────────────────────┘
```

### Enum: LogLevel (Priority Order)
```
FATAL(0)  → System is unusable, immediate attention
ERROR(1)  → Error occurred, but system continues
WARN(2)   → Something unexpected, not an error yet
INFO(3)   → Informational — normal operation
DEBUG(4)  → Detailed diagnostic information

Filtering rule: if configured level = INFO (priority 3),
  log only levels with priority ≤ 3: FATAL, ERROR, WARN, INFO
  filter out: DEBUG (priority 4 > 3)
```

### Class: LogMessage
| Attribute | Type | Description |
|-----------|------|-------------|
| content | String | "User login successful" |
| level | LogLevel | INFO, ERROR, etc. |
| namespace | String | "com.app.auth.LoginService" |
| timestamp | String | "2024-03-15 14:30:45.123" (formatted) |
| threadName | String | "main", "worker-3" |
| metadata | Map\<String, String\> | Additional key-value enrichments |

### Class: Sink (Interface)
| Method | Description |
|--------|-------------|
| write(LogMessage) | Write the formatted message to destination |
| getName() | "Console", "File(/var/logs/app.log)" |
| close() | Clean up resources (close file handle, DB connection) |

### Class: LogEnricher (Chain of Responsibility)
| Method | Description |
|--------|-------------|
| enrich(LogMessage) | Add metadata, then pass to next enricher |
| setNext(LogEnricher) | Link to next enricher in chain |

---

## 3️⃣ API Design

```java
class Logger {
    /** Log a message at the specified level. */
    void log(LogLevel level, String namespace, String content);
    
    /** Convenience methods — the primary API for callers. */
    void fatal(String namespace, String content);
    void error(String namespace, String content);
    void warn(String namespace, String content);
    void info(String namespace, String content);
    void debug(String namespace, String content);
    
    /** Check if a level would be logged (for expensive message construction). */
    boolean isEnabled(LogLevel level);
    
    /** Get a named child logger with a fixed namespace. */
    Logger getLogger(String namespace);
    
    /** Flush all sinks. */
    void flush();
    
    /** Close all sinks and release resources. */
    void close();
}
```

### Configuration Builder API (Ease of Use)
```java
Logger logger = LoggerConfiguration.builder()
    .timeFormat("yyyy-MM-dd HH:mm:ss.SSS")
    .minLevel(LogLevel.INFO)
    .sink(LogLevel.FATAL, new FileSink("/var/logs/fatal.log"))
    .sink(LogLevel.ERROR, new FileSink("/var/logs/error.log"))
    .sink(LogLevel.WARN,  new ConsoleSink())
    .sink(LogLevel.INFO,  new ConsoleSink())         // WARN and INFO share ConsoleSink
    .sink(LogLevel.DEBUG, new ConsoleSink())
    .enricher(new TimestampEnricher("yyyy-MM-dd HH:mm:ss.SSS"))
    .enricher(new NamespaceEnricher())
    .enricher(new ThreadEnricher())
    .build();
```

---

## 4️⃣ Data Flow

### Scenario 1: Basic Logging Flow (INFO Level)

```
logger.info("com.app.auth", "User alice logged in successfully")

Logger.log(INFO, "com.app.auth", "User alice logged in successfully"):
  │
  ├─ Step 1: LEVEL FILTERING
  │   Configured minLevel = INFO (priority 3)
  │   Message level = INFO (priority 3)
  │   3 ≤ 3? ✅ PASS (at or above threshold)
  │
  ├─ Step 2: CREATE LOG MESSAGE
  │   LogMessage {
  │     content: "User alice logged in successfully",
  │     level: INFO,
  │     namespace: "com.app.auth"
  │   }
  │
  ├─ Step 3: ENRICHER CHAIN (Chain of Responsibility)
  │   ┌─ TimestampEnricher ──────────────────────────┐
  │   │  message.timestamp = "2024-03-15 14:30:45.123"│
  │   │  → pass to next                               │
  │   └───────────────┬──────────────────────────────┘
  │                   ▼
  │   ┌─ NamespaceEnricher ──────────────────────────┐
  │   │  message.metadata["namespace"] = "com.app.auth"│
  │   │  → pass to next                               │
  │   └───────────────┬──────────────────────────────┘
  │                   ▼
  │   ┌─ ThreadEnricher ─────────────────────────────┐
  │   │  message.threadName = "main"                  │
  │   │  → end of chain                               │
  │   └──────────────────────────────────────────────┘
  │
  ├─ Step 4: ROUTE TO SINK
  │   levelSinkMap.get(INFO) → ConsoleSink
  │
  ├─ Step 5: SINK WRITES
  │   ConsoleSink.write(message):
  │   → "[2024-03-15 14:30:45.123] [INFO] [main] [com.app.auth] User alice logged in successfully"
  │
  └─ Done. Message logged to console.
```

### Scenario 2: Level Filtering (DEBUG Filtered Out)

```
Configured minLevel = INFO

logger.debug("com.app.db", "SQL query: SELECT * FROM users")

Logger.log(DEBUG, "com.app.db", "SQL query: SELECT * FROM users"):
  │
  ├─ Step 1: LEVEL FILTERING
  │   minLevel = INFO (priority 3)
  │   Message level = DEBUG (priority 4)
  │   4 ≤ 3? ❌ FAIL — DEBUG is BELOW threshold
  │
  └─ RETURN immediately. No enrichment, no sink write.
     ✅ Zero overhead for filtered messages (just one integer comparison)
```

### Scenario 3: Multiple Levels → Same Sink

```
Config:
  FATAL → FileSink("/var/logs/fatal.log")
  ERROR → FileSink("/var/logs/error.log")
  WARN  → ConsoleSink (shared)
  INFO  → ConsoleSink (shared)    ← same instance!
  DEBUG → ConsoleSink (shared)    ← same instance!

logger.warn("com.app.payment", "Payment retry #2")
  → levelSinkMap.get(WARN) → ConsoleSink → console output

logger.info("com.app.auth", "User logged in")
  → levelSinkMap.get(INFO) → ConsoleSink → console output (same sink!)

logger.error("com.app.payment", "Payment failed: timeout")
  → levelSinkMap.get(ERROR) → FileSink("/var/logs/error.log") → file output

  ✅ WARN and INFO share same ConsoleSink instance — no duplication
  ✅ ERROR goes to separate file — different destinations per level
```

### Scenario 4: FATAL Goes to Multiple Sinks (P1 — Multi-Sink)

```
Config:
  FATAL → [FileSink("/var/logs/fatal.log"), ConsoleSink, AlertSink(email)]

logger.fatal("com.app.core", "Out of memory — system shutting down")
  │
  ├─ Route: levelSinkMap.get(FATAL) → [FileSink, ConsoleSink, AlertSink]
  ├─ FileSink: writes to /var/logs/fatal.log
  ├─ ConsoleSink: prints to stderr
  └─ AlertSink: sends email to ops@company.com

  ✅ Critical messages logged everywhere simultaneously
```

### Scenario 5: Named Logger (Fixed Namespace)

```
// Create child logger with fixed namespace
Logger authLogger = logger.getLogger("com.app.auth");

authLogger.info("User alice logged in");
// Equivalent to: logger.info("com.app.auth", "User alice logged in")
// Namespace automatically set — caller doesn't repeat it every time

authLogger.error("Failed to validate token");
// → [ERROR] [com.app.auth] Failed to validate token

  ✅ Convenience: no need to pass namespace every call
  ✅ Common pattern in Log4j, SLF4J, Serilog
```

---

## 5️⃣ Design + Implementation

### LogLevel Enum

```java
// ==================== LOG LEVEL ====================
// 💬 "Priority as integer — lower number = higher severity = always logged.
//    Filtering is one integer comparison: message.level.priority <= config.minLevel.priority"

enum LogLevel {
    FATAL(0, "FATAL"),
    ERROR(1, "ERROR"),
    WARN(2, "WARN"),
    INFO(3, "INFO"),
    DEBUG(4, "DEBUG");
    
    private final int priority;
    private final String label;
    
    LogLevel(int priority, String label) {
        this.priority = priority;
        this.label = label;
    }
    
    public int getPriority()   { return priority; }
    public String getLabel()   { return label; }
    
    /** Check if this level should be logged given a minimum threshold. */
    public boolean isLoggable(LogLevel minLevel) {
        return this.priority <= minLevel.priority;
    }
}
```

### LogMessage

```java
// ==================== LOG MESSAGE ====================
class LogMessage {
    private final String content;
    private final LogLevel level;
    private final String namespace;
    private String timestamp;
    private String threadName;
    private final Map<String, String> metadata;
    private final long createdAt;
    
    public LogMessage(String content, LogLevel level, String namespace) {
        this.content = content;
        this.level = level;
        this.namespace = namespace;
        this.metadata = new LinkedHashMap<>();  // preserve insertion order
        this.createdAt = System.currentTimeMillis();
    }
    
    // Getters
    public String getContent()    { return content; }
    public LogLevel getLevel()    { return level; }
    public String getNamespace()  { return namespace; }
    public String getTimestamp()  { return timestamp; }
    public String getThreadName() { return threadName; }
    public long getCreatedAt()    { return createdAt; }
    public Map<String, String> getMetadata() { return metadata; }
    
    // Setters (used by enrichers)
    public void setTimestamp(String ts)    { this.timestamp = ts; }
    public void setThreadName(String tn)   { this.threadName = tn; }
    public void addMetadata(String k, String v) { metadata.put(k, v); }
    
    /** Format for output. Sinks call this to get the final string. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (timestamp != null) sb.append("[").append(timestamp).append("] ");
        sb.append("[").append(level.getLabel()).append("] ");
        if (threadName != null) sb.append("[").append(threadName).append("] ");
        sb.append("[").append(namespace).append("] ");
        sb.append(content);
        if (!metadata.isEmpty()) {
            sb.append(" | ");
            metadata.forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
        }
        return sb.toString().trim();
    }
}
```

### Sink Interface (Strategy Pattern)

```java
// ==================== SINK INTERFACE ====================
// 💬 "Strategy Pattern: Each sink writes to a different destination.
//    Same interface, different implementations. New sink type = new class."

interface Sink {
    /** Write a log message to this sink. */
    void write(LogMessage message);
    
    /** Sink name for display/debugging. */
    String getName();
    
    /** Flush buffered messages. */
    void flush();
    
    /** Release resources. */
    void close();
}

// ==================== CONSOLE SINK ====================
class ConsoleSink implements Sink {
    @Override
    public void write(LogMessage message) {
        if (message.getLevel() == LogLevel.ERROR || message.getLevel() == LogLevel.FATAL) {
            System.err.println(message.format());  // errors to stderr
        } else {
            System.out.println(message.format());  // info/debug to stdout
        }
    }
    
    @Override
    public String getName() { return "Console"; }
    
    @Override
    public void flush() { System.out.flush(); System.err.flush(); }
    
    @Override
    public void close() { flush(); }
}

// ==================== FILE SINK ====================
class FileSink implements Sink {
    private final String filePath;
    private final BufferedWriter writer;
    
    public FileSink(String filePath) {
        this.filePath = filePath;
        try {
            this.writer = new BufferedWriter(new FileWriter(filePath, true));  // append mode
        } catch (IOException e) {
            throw new RuntimeException("Cannot open log file: " + filePath, e);
        }
    }
    
    @Override
    public synchronized void write(LogMessage message) {
        try {
            writer.write(message.format());
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }
    
    @Override
    public String getName() { return "File(" + filePath + ")"; }
    
    @Override
    public synchronized void flush() {
        try { writer.flush(); } catch (IOException e) { /* ignore */ }
    }
    
    @Override
    public synchronized void close() {
        try { writer.flush(); writer.close(); } catch (IOException e) { /* ignore */ }
    }
}

// ==================== DATABASE SINK ====================
class DatabaseSink implements Sink {
    private final String connectionUrl;
    
    public DatabaseSink(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }
    
    @Override
    public void write(LogMessage message) {
        // In production: INSERT INTO logs (timestamp, level, namespace, content) VALUES (...)
        System.out.println("  [DB] INSERT: " + message.format());
    }
    
    @Override
    public String getName() { return "Database(" + connectionUrl + ")"; }
    
    @Override
    public void flush() { /* commit batch */ }
    
    @Override
    public void close() { /* close DB connection */ }
}
```

### Async Sink Decorator (Decorator Pattern)

```java
// ==================== ASYNC SINK DECORATOR ====================
// 💬 "Decorator wraps any sink to make it async.
//    Logging call returns immediately, actual write happens on background thread.
//    Prevents slow sinks (file, DB, network) from blocking the caller."

class AsyncSinkDecorator implements Sink {
    private final Sink wrappedSink;
    private final ExecutorService executor;
    
    public AsyncSinkDecorator(Sink sink) {
        this.wrappedSink = sink;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "async-logger-" + sink.getName());
            t.setDaemon(true);  // don't prevent JVM shutdown
            return t;
        });
    }
    
    @Override
    public void write(LogMessage message) {
        executor.submit(() -> wrappedSink.write(message));
        // Returns immediately — write happens on background thread
    }
    
    @Override
    public String getName() { return "Async(" + wrappedSink.getName() + ")"; }
    
    @Override
    public void flush() {
        // Submit flush and wait
        try {
            executor.submit(() -> wrappedSink.flush()).get(5, TimeUnit.SECONDS);
        } catch (Exception e) { /* timeout */ }
    }
    
    @Override
    public void close() {
        wrappedSink.flush();
        executor.shutdown();
        wrappedSink.close();
    }
}
```

### Log Enricher (Chain of Responsibility)

```java
// ==================== LOG ENRICHER (CHAIN OF RESPONSIBILITY) ====================
// 💬 "Each enricher adds one piece of metadata, then passes to next.
//    Order matters: Timestamp first, then namespace, then thread.
//    New enricher = new class, plug into chain — zero changes to Logger."

abstract class LogEnricher {
    protected LogEnricher next;
    
    public LogEnricher setNext(LogEnricher next) {
        this.next = next;
        return next;  // fluent chaining
    }
    
    public LogMessage enrich(LogMessage message) {
        doEnrich(message);
        if (next != null) {
            return next.enrich(message);
        }
        return message;
    }
    
    protected abstract void doEnrich(LogMessage message);
    public abstract String getName();
}

// ==================== TIMESTAMP ENRICHER ====================
class TimestampEnricher extends LogEnricher {
    private final DateTimeFormatter formatter;
    
    public TimestampEnricher(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }
    
    @Override
    protected void doEnrich(LogMessage message) {
        String timestamp = LocalDateTime.now().format(formatter);
        message.setTimestamp(timestamp);
    }
    
    @Override
    public String getName() { return "Timestamp"; }
}

// ==================== NAMESPACE ENRICHER ====================
class NamespaceEnricher extends LogEnricher {
    @Override
    protected void doEnrich(LogMessage message) {
        // Namespace already on message — enrich by adding short name to metadata
        String fullNamespace = message.getNamespace();
        String shortName = fullNamespace.contains(".")
            ? fullNamespace.substring(fullNamespace.lastIndexOf('.') + 1)
            : fullNamespace;
        message.addMetadata("component", shortName);
    }
    
    @Override
    public String getName() { return "Namespace"; }
}

// ==================== THREAD ENRICHER ====================
class ThreadEnricher extends LogEnricher {
    @Override
    protected void doEnrich(LogMessage message) {
        message.setThreadName(Thread.currentThread().getName());
    }
    
    @Override
    public String getName() { return "Thread"; }
}

// ==================== CUSTOM KEY-VALUE ENRICHER ====================
class CustomEnricher extends LogEnricher {
    private final String key;
    private final String value;
    
    public CustomEnricher(String key, String value) {
        this.key = key;
        this.value = value;
    }
    
    @Override
    protected void doEnrich(LogMessage message) {
        message.addMetadata(key, value);
    }
    
    @Override
    public String getName() { return "Custom(" + key + ")"; }
}
```

### Logger (Core)

```java
// ==================== LOGGER ====================
// 💬 "Logger is the public API. It filters by level, enriches the message,
//    routes to the correct sink, and writes. Thread-safe via sink-level synchronization."

class Logger {
    private final Map<LogLevel, Sink> levelSinkMap;
    private final LogLevel minLevel;
    private final LogEnricher enricherChain;
    private final String defaultNamespace;
    
    Logger(Map<LogLevel, Sink> levelSinkMap, LogLevel minLevel,
           LogEnricher enricherChain, String defaultNamespace) {
        this.levelSinkMap = levelSinkMap;
        this.minLevel = minLevel;
        this.enricherChain = enricherChain;
        this.defaultNamespace = defaultNamespace;
    }
    
    // ===== CORE LOG METHOD =====
    
    public void log(LogLevel level, String namespace, String content) {
        // Step 1: Level filtering — O(1) integer comparison
        if (!level.isLoggable(minLevel)) {
            return;  // below threshold, skip everything
        }
        
        // Step 2: Create message
        LogMessage message = new LogMessage(content, level, namespace);
        
        // Step 3: Enrich (chain of responsibility)
        if (enricherChain != null) {
            enricherChain.enrich(message);
        }
        
        // Step 4: Route to sink
        Sink sink = levelSinkMap.get(level);
        if (sink != null) {
            sink.write(message);
        }
    }
    
    // ===== CONVENIENCE METHODS =====
    
    public void fatal(String namespace, String content) { log(LogLevel.FATAL, namespace, content); }
    public void error(String namespace, String content) { log(LogLevel.ERROR, namespace, content); }
    public void warn(String namespace, String content)  { log(LogLevel.WARN, namespace, content); }
    public void info(String namespace, String content)  { log(LogLevel.INFO, namespace, content); }
    public void debug(String namespace, String content) { log(LogLevel.DEBUG, namespace, content); }
    
    // ===== NAMED LOGGER (fixed namespace) =====
    
    /** Create a child logger with a fixed namespace — no need to pass it every call. */
    public NamedLogger getLogger(String namespace) {
        return new NamedLogger(this, namespace);
    }
    
    /** Check if a level would pass filtering (avoid expensive string building). */
    public boolean isEnabled(LogLevel level) {
        return level.isLoggable(minLevel);
    }
    
    public void flush() {
        levelSinkMap.values().stream().distinct().forEach(Sink::flush);
    }
    
    public void close() {
        levelSinkMap.values().stream().distinct().forEach(Sink::close);
    }
}

// ==================== NAMED LOGGER (convenience wrapper) ====================
class NamedLogger {
    private final Logger logger;
    private final String namespace;
    
    NamedLogger(Logger logger, String namespace) {
        this.logger = logger;
        this.namespace = namespace;
    }
    
    public void fatal(String content) { logger.fatal(namespace, content); }
    public void error(String content) { logger.error(namespace, content); }
    public void warn(String content)  { logger.warn(namespace, content); }
    public void info(String content)  { logger.info(namespace, content); }
    public void debug(String content) { logger.debug(namespace, content); }
    
    public boolean isEnabled(LogLevel level) { return logger.isEnabled(level); }
}
```

### LoggerConfiguration (Builder Pattern)

```java
// ==================== LOGGER CONFIGURATION (BUILDER) ====================
// 💬 "Builder Pattern for readable, validated configuration.
//    Fluent API — chain calls, build() validates and produces a Logger."

class LoggerConfiguration {
    
    public static LoggerConfigBuilder builder() {
        return new LoggerConfigBuilder();
    }
    
    static class LoggerConfigBuilder {
        private String timeFormat = "yyyy-MM-dd HH:mm:ss.SSS";
        private LogLevel minLevel = LogLevel.INFO;
        private final Map<LogLevel, Sink> sinkMap = new EnumMap<>(LogLevel.class);
        private final List<LogEnricher> enrichers = new ArrayList<>();
        private String defaultNamespace = "app";
        
        public LoggerConfigBuilder timeFormat(String format) {
            this.timeFormat = format;
            return this;
        }
        
        public LoggerConfigBuilder minLevel(LogLevel level) {
            this.minLevel = level;
            return this;
        }
        
        public LoggerConfigBuilder sink(LogLevel level, Sink sink) {
            sinkMap.put(level, sink);
            return this;
        }
        
        /** Map multiple levels to one sink. */
        public LoggerConfigBuilder sink(Sink sink, LogLevel... levels) {
            for (LogLevel level : levels) {
                sinkMap.put(level, sink);
            }
            return this;
        }
        
        public LoggerConfigBuilder enricher(LogEnricher enricher) {
            enrichers.add(enricher);
            return this;
        }
        
        public LoggerConfigBuilder defaultNamespace(String ns) {
            this.defaultNamespace = ns;
            return this;
        }
        
        public Logger build() {
            // Add default timestamp enricher if none configured
            if (enrichers.isEmpty()) {
                enrichers.add(new TimestampEnricher(timeFormat));
                enrichers.add(new ThreadEnricher());
            }
            
            // Build enricher chain
            LogEnricher chain = null;
            if (!enrichers.isEmpty()) {
                chain = enrichers.get(0);
                LogEnricher current = chain;
                for (int i = 1; i < enrichers.size(); i++) {
                    current = current.setNext(enrichers.get(i));
                }
            }
            
            // Default: if no sink configured for a loggable level, use ConsoleSink
            Sink defaultSink = new ConsoleSink();
            for (LogLevel level : LogLevel.values()) {
                if (level.isLoggable(minLevel) && !sinkMap.containsKey(level)) {
                    sinkMap.put(level, defaultSink);
                }
            }
            
            return new Logger(sinkMap, minLevel, chain, defaultNamespace);
        }
    }
}
```

### Demo

```java
public class LoggerDemo {
    public static void main(String[] args) {
        // === Configuration ===
        Sink consoleSink = new ConsoleSink();
        Sink errorFileSink = new AsyncSinkDecorator(new FileSink("/tmp/error.log"));
        
        Logger logger = LoggerConfiguration.builder()
            .timeFormat("yyyy-MM-dd HH:mm:ss.SSS")
            .minLevel(LogLevel.INFO)
            .sink(LogLevel.FATAL, errorFileSink)
            .sink(LogLevel.ERROR, errorFileSink)
            .sink(consoleSink, LogLevel.WARN, LogLevel.INFO)
            .enricher(new TimestampEnricher("yyyy-MM-dd HH:mm:ss.SSS"))
            .enricher(new NamespaceEnricher())
            .enricher(new ThreadEnricher())
            .enricher(new CustomEnricher("env", "production"))
            .build();
        
        // === Basic logging ===
        logger.info("com.app.startup", "Application started successfully");
        logger.warn("com.app.payment", "Payment service response slow: 2.5s");
        logger.error("com.app.auth", "Invalid token for user: bob");
        logger.debug("com.app.db", "SQL: SELECT * FROM users");  // filtered out! (DEBUG < INFO)
        
        // === Named logger ===
        NamedLogger authLog = logger.getLogger("com.app.auth");
        authLog.info("User alice logged in");
        authLog.error("Failed to validate token");
        
        // === Check before expensive operations ===
        if (logger.isEnabled(LogLevel.DEBUG)) {
            // Only build this expensive string if DEBUG is actually logged
            String expensiveDebugInfo = buildExpensiveDebugString();
            logger.debug("com.app.perf", expensiveDebugInfo);
        }
        
        // === Cleanup ===
        logger.flush();
        logger.close();
    }
}
```

### Output:
```
[2024-03-15 14:30:45.123] [INFO] [main] [com.app.startup] Application started successfully | component=startup env=production
[2024-03-15 14:30:45.125] [WARN] [main] [com.app.payment] Payment service response slow: 2.5s | component=payment env=production
[2024-03-15 14:30:45.126] [ERROR] [main] [com.app.auth] Invalid token for user: bob | component=auth env=production
  ← DEBUG filtered (minLevel=INFO, DEBUG priority 4 > 3) — not logged
[2024-03-15 14:30:45.128] [INFO] [main] [com.app.auth] User alice logged in | component=auth env=production
[2024-03-15 14:30:45.129] [ERROR] [main] [com.app.auth] Failed to validate token | component=auth env=production
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: SOLID Principles Applied

| Principle | Where | How |
|-----------|-------|-----|
| **SRP** | LogMessage (data), Sink (output), Enricher (metadata), Logger (coordination) | Each class has one reason to change |
| **OCP** | New sink = new class implementing Sink, new enricher = new class extending LogEnricher | Extend without modifying existing code |
| **LSP** | ConsoleSink, FileSink, DatabaseSink all substitutable via Sink interface | Any sink works in the logger |
| **ISP** | Sink interface has only write/flush/close — minimal contract | Implementations don't depend on unused methods |
| **DIP** | Logger depends on Sink interface, not ConsoleSink/FileSink | High-level Logger doesn't know about low-level sinks |

### Deep Dive 2: Design Patterns Applied

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | Sink interface (3+ implementations) | Different output destinations, same write contract |
| **Chain of Responsibility** | LogEnricher pipeline | Each enricher adds one piece of metadata, independently testable |
| **Builder** | LoggerConfiguration.builder() | Complex configuration made readable and validated |
| **Decorator** | AsyncSinkDecorator wraps any Sink | Add async behavior without modifying existing sinks |
| **Singleton** | Global logger instance (optional) | One logger per application or per named scope |
| **Template Method** | LogEnricher.enrich() skeleton: doEnrich → pass to next | Shared chain logic, specific enrichment per subclass |

### Deep Dive 3: Level Filtering Performance

```
FILTERING COST: O(1) — single integer comparison

  if (!level.isLoggable(minLevel)) return;
  // Compiles to: if (level.priority > minLevel.priority) return;

WHY THIS MATTERS:
  A production app may call logger.debug() 10,000 times/sec.
  If DEBUG is filtered out, those 10,000 calls must be INSTANT.
  
  Our design: 1 comparison → return. No object creation, no string formatting.
  
  ANTI-PATTERN (what NOT to do):
    logger.debug("User " + user.getName() + " data: " + expensiveToString());
    // String concatenation happens BEFORE the level check!
    // Fix: if (logger.isEnabled(DEBUG)) { logger.debug(...); }
    // Better fix: Use lambda: logger.debug(() -> "User " + user.getName())
```

### Deep Dive 4: Thread Safety Analysis

| Component | Mechanism | Why |
|-----------|-----------|-----|
| **Logger.log()** | No shared mutable state — LogMessage created per call | Each call creates its own message — no contention |
| **ConsoleSink** | System.out/err are already synchronized | Built-in thread safety |
| **FileSink** | `synchronized` on write/flush/close | File writes must be serialized |
| **AsyncSinkDecorator** | Single-thread executor | All writes serialized on one background thread |
| **Enricher chain** | Stateless enrichers (no mutable fields) | Thread-safe by design — no shared state |
| **Level filtering** | Immutable enum comparison | No synchronization needed |

### Deep Dive 5: Async Logging Trade-offs

| Aspect | Synchronous | Asynchronous (Decorator) |
|--------|------------|-------------------------|
| **Caller blocked?** | Yes — waits for write to complete | No — returns immediately |
| **Log loss risk** | None — write confirmed | Possible on crash (buffered messages lost) |
| **Throughput** | Limited by slowest sink | High — caller never waits |
| **Ordering** | Guaranteed | Guaranteed (single-thread executor) |
| **Complexity** | Simple | Need flush/shutdown handling |
| **When to use** | Console, fast sinks | File, database, network sinks |

### Deep Dive 6: Log Rotation (P1 Extension)

```java
class RotatingFileSink implements Sink {
    private final String baseFilePath;
    private final long maxFileSizeBytes;
    private final int maxFiles;
    private BufferedWriter writer;
    private long currentSize;
    
    @Override
    public synchronized void write(LogMessage message) {
        String formatted = message.format() + "\n";
        currentSize += formatted.length();
        
        if (currentSize > maxFileSizeBytes) {
            rotate();  // app.log → app.log.1 → app.log.2 → ... → delete oldest
            currentSize = formatted.length();
        }
        
        writer.write(formatted);
    }
    
    private void rotate() {
        // app.log.2 → app.log.3 (if maxFiles > 3, delete)
        // app.log.1 → app.log.2
        // app.log   → app.log.1
        // Create new app.log
    }
}
```

### Deep Dive 7: Structured Logging (P1 Extension)

```java
class JsonSink implements Sink {
    @Override
    public void write(LogMessage message) {
        // Instead of: [2024-03-15] [INFO] [main] User logged in
        // Output:     {"timestamp":"2024-03-15","level":"INFO","thread":"main","message":"User logged in"}
        
        Map<String, String> json = new LinkedHashMap<>();
        json.put("timestamp", message.getTimestamp());
        json.put("level", message.getLevel().getLabel());
        json.put("thread", message.getThreadName());
        json.put("namespace", message.getNamespace());
        json.put("message", message.getContent());
        json.putAll(message.getMetadata());
        
        System.out.println(toJson(json));
    }
}

// ✅ Structured logging → easier to parse by log aggregators (ELK, Splunk, CloudWatch)
```

### Deep Dive 8: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Null content | Replace with "[null]" or throw IllegalArgumentException |
| Empty namespace | Use default namespace from config |
| Sink write fails (IOException) | Log to stderr, don't crash the application |
| All sinks closed, then log called | Silently skip (logger is closed) |
| Very long message content | Truncate at configurable max length in enricher |
| Log called from multiple threads | Thread-safe via per-sink synchronization |
| Enricher throws exception | Catch per-enricher, continue chain (isolation) |
| No sink configured for a level | Use fallback ConsoleSink (or skip) |
| DEBUG messages with expensive string building | Use isEnabled() check or lambda supplier |
| Shutdown without flush | AsyncSinkDecorator uses daemon thread — may lose last messages |

### Deep Dive 9: Complexity Analysis

| Operation | Time | Space | Note |
|-----------|------|-------|------|
| Level check | O(1) | O(1) | Integer comparison |
| Enricher chain | O(E) | O(1) | E = enricher count (typically 3-5, effectively O(1)) |
| Sink routing | O(1) | O(1) | EnumMap.get(level) |
| Sink write (console) | O(L) | O(L) | L = message length |
| Sink write (file, sync) | O(L) | O(L) | File I/O |
| Sink write (async) | O(1) for caller | O(L) queued | Background thread does actual write |
| Build enricher chain | O(E) | O(E) | One-time at config |
| Total per log call | O(E + L) | O(L) | E≈constant, L=message length |

---

## 📋 Interview Checklist (L63)

- [ ] **Strategy Pattern**: Sink interface with ConsoleSink, FileSink, DatabaseSink — different destinations, same contract
- [ ] **Chain of Responsibility**: Enricher pipeline — each adds one metadata, independently testable
- [ ] **Builder Pattern**: Fluent LoggerConfiguration — readable, validated configuration
- [ ] **Decorator**: AsyncSinkDecorator wraps any sink for non-blocking writes
- [ ] **Level filtering**: O(1) integer comparison — zero overhead for filtered messages
- [ ] **Level → Sink routing**: EnumMap lookup — each level maps to specific sink, levels can share sinks
- [ ] **Named logger**: Child logger with fixed namespace — convenience for callers
- [ ] **Thread safety**: Per-sink synchronization, stateless enrichers, per-call message creation
- [ ] **SOLID**: Every principle demonstrated with specific examples
- [ ] **Performance**: isEnabled() guard, async decorator, no string building for filtered messages

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (LogMessage, LogLevel, Sink, Enricher) | 5 min |
| Sink Implementations (Console, File, DB) | 5 min |
| Enricher Chain + Builder | 7 min |
| Logger Core + Named Logger | 8 min |
| Deep Dives (async, rotation, structured, edge cases) | 5 min |
| **Total** | **~35 min** |

### Design Pattern Summary

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **Strategy** | Sink interface (Console, File, DB) | Different output destinations, same write contract |
| **Chain of Responsibility** | LogEnricher (Timestamp, Namespace, Thread, Custom) | Pipeline of independent metadata enrichments |
| **Builder** | LoggerConfiguration.builder() | Complex config made readable and validated |
| **Decorator** | AsyncSinkDecorator wraps any Sink | Add async behavior without modifying existing sinks |
| **Template Method** | LogEnricher.enrich() → doEnrich() + pass-to-next | Shared chain traversal, custom enrichment per subclass |

See `LoggerLibrarySystem.java` for full implementation with multi-sink routing, enricher chain, async decorator, and named logger demos.
