import java.util.*;

// ==================== COMMAND PATTERN ====================
// Definition: Encapsulates a request as an object, thereby letting you parameterize
// clients with different requests, queue or log requests, and support undoable operations.
//
// REAL System Design Interview Examples:
// 1. Task Queue / Job Scheduler - Encapsulate jobs as command objects
// 2. Distributed Transaction (Saga) - Each step is a command with undo
// 3. Text Editor / Collaborative Editing - Undo/redo operations
// 4. Database Migration System - Forward/rollback migrations
// 5. API Request Pipeline - Retry, queue, batch API calls
//
// Interview Use Cases:
// - Design Job Scheduler: Commands queued and executed asynchronously
// - Design Undo/Redo System: Command history with execute/undo
// - Design Saga Orchestrator: Compensating transactions
// - Design Database Migration Tool: Versioned up/down migrations
// - Design Smart Home System: Device commands queued and scheduled

// ==================== EXAMPLE 1: TASK QUEUE / JOB SCHEDULER ====================
// Used in: Celery, AWS SQS + Lambda, Sidekiq, Bull
// Interview Question: Design a distributed task queue

interface TaskCommand {
    void execute();
    void undo();
    String getTaskId();
    String getDescription();
    TaskStatus getStatus();
}

enum TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, ROLLED_BACK }

class SendEmailCommand implements TaskCommand {
    private String taskId;
    private String to;
    private String subject;
    private String body;
    private TaskStatus status = TaskStatus.PENDING;

    public SendEmailCommand(String to, String subject, String body) {
        this.taskId = UUID.randomUUID().toString().substring(0, 8);
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    @Override
    public void execute() {
        status = TaskStatus.RUNNING;
        System.out.printf("📧 Sending email to %s | Subject: %s%n", to, subject);
        status = TaskStatus.COMPLETED;
    }

    @Override
    public void undo() {
        System.out.printf("↩️  Marking email to %s as recalled%n", to);
        status = TaskStatus.ROLLED_BACK;
    }

    @Override
    public String getTaskId() { return taskId; }

    @Override
    public String getDescription() { return "SendEmail → " + to; }

    @Override
    public TaskStatus getStatus() { return status; }
}

class ProcessPaymentCommand implements TaskCommand {
    private String taskId;
    private String orderId;
    private double amount;
    private TaskStatus status = TaskStatus.PENDING;

    public ProcessPaymentCommand(String orderId, double amount) {
        this.taskId = UUID.randomUUID().toString().substring(0, 8);
        this.orderId = orderId;
        this.amount = amount;
    }

    @Override
    public void execute() {
        status = TaskStatus.RUNNING;
        System.out.printf("💳 Processing payment $%.2f for order %s%n", amount, orderId);
        status = TaskStatus.COMPLETED;
    }

    @Override
    public void undo() {
        System.out.printf("↩️  Refunding $%.2f for order %s%n", amount, orderId);
        status = TaskStatus.ROLLED_BACK;
    }

    @Override
    public String getTaskId() { return taskId; }

    @Override
    public String getDescription() { return "ProcessPayment $" + amount + " for " + orderId; }

    @Override
    public TaskStatus getStatus() { return status; }
}

class GenerateReportCommand implements TaskCommand {
    private String taskId;
    private String reportType;
    private String dateRange;
    private TaskStatus status = TaskStatus.PENDING;

    public GenerateReportCommand(String reportType, String dateRange) {
        this.taskId = UUID.randomUUID().toString().substring(0, 8);
        this.reportType = reportType;
        this.dateRange = dateRange;
    }

    @Override
    public void execute() {
        status = TaskStatus.RUNNING;
        System.out.printf("📊 Generating %s report for %s%n", reportType, dateRange);
        status = TaskStatus.COMPLETED;
    }

    @Override
    public void undo() {
        System.out.printf("↩️  Deleting generated %s report%n", reportType);
        status = TaskStatus.ROLLED_BACK;
    }

    @Override
    public String getTaskId() { return taskId; }

    @Override
    public String getDescription() { return "GenerateReport " + reportType; }

    @Override
    public TaskStatus getStatus() { return status; }
}

class TaskQueue {
    private Queue<TaskCommand> pendingQueue = new LinkedList<>();
    private List<TaskCommand> executedHistory = new ArrayList<>();
    private String queueName;

    public TaskQueue(String queueName) {
        this.queueName = queueName;
    }

    public void enqueue(TaskCommand task) {
        pendingQueue.offer(task);
        System.out.printf("📥 [%s] Enqueued: %s (id=%s)%n", queueName, task.getDescription(), task.getTaskId());
    }

    public void processNext() {
        TaskCommand task = pendingQueue.poll();
        if (task == null) {
            System.out.println("📭 Queue is empty");
            return;
        }
        System.out.printf("⚙️  [%s] Executing: %s%n", queueName, task.getDescription());
        task.execute();
        executedHistory.add(task);
    }

    public void processAll() {
        System.out.printf("%n🚀 [%s] Processing all %d tasks...%n", queueName, pendingQueue.size());
        while (!pendingQueue.isEmpty()) {
            processNext();
        }
    }

    public void undoLast() {
        if (executedHistory.isEmpty()) {
            System.out.println("⚠️  No tasks to undo");
            return;
        }
        TaskCommand lastTask = executedHistory.remove(executedHistory.size() - 1);
        System.out.printf("↩️  [%s] Undoing: %s%n", queueName, lastTask.getDescription());
        lastTask.undo();
    }

    public int getPendingCount() { return pendingQueue.size(); }
}

// ==================== EXAMPLE 2: SAGA ORCHESTRATOR ====================
// Used in: Distributed transactions, Microservice choreography
// Interview Question: Design a distributed transaction system (Saga pattern)

class SagaStep {
    private String stepName;
    private TaskCommand forwardCommand;
    private TaskCommand compensationCommand;

    public SagaStep(String stepName, TaskCommand forward, TaskCommand compensation) {
        this.stepName = stepName;
        this.forwardCommand = forward;
        this.compensationCommand = compensation;
    }

    public String getStepName() { return stepName; }
    public TaskCommand getForwardCommand() { return forwardCommand; }
    public TaskCommand getCompensationCommand() { return compensationCommand; }
}

class ReserveInventoryCommand implements TaskCommand {
    private String taskId = UUID.randomUUID().toString().substring(0, 8);
    private String productId;
    private int quantity;
    private TaskStatus status = TaskStatus.PENDING;

    public ReserveInventoryCommand(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    @Override
    public void execute() {
        status = TaskStatus.RUNNING;
        System.out.printf("📦 Reserving %d units of %s%n", quantity, productId);
        status = TaskStatus.COMPLETED;
    }

    @Override
    public void undo() {
        System.out.printf("↩️  Releasing %d units of %s%n", quantity, productId);
        status = TaskStatus.ROLLED_BACK;
    }

    @Override
    public String getTaskId() { return taskId; }
    @Override
    public String getDescription() { return "ReserveInventory " + productId; }
    @Override
    public TaskStatus getStatus() { return status; }
}

class ShipOrderCommand implements TaskCommand {
    private String taskId = UUID.randomUUID().toString().substring(0, 8);
    private String orderId;
    private String address;
    private TaskStatus status = TaskStatus.PENDING;
    private boolean shouldFail;

    public ShipOrderCommand(String orderId, String address, boolean shouldFail) {
        this.orderId = orderId;
        this.address = address;
        this.shouldFail = shouldFail;
    }

    @Override
    public void execute() {
        status = TaskStatus.RUNNING;
        if (shouldFail) {
            status = TaskStatus.FAILED;
            throw new RuntimeException("Shipping service unavailable");
        }
        System.out.printf("🚚 Shipping order %s to %s%n", orderId, address);
        status = TaskStatus.COMPLETED;
    }

    @Override
    public void undo() {
        System.out.printf("↩️  Cancelling shipment for order %s%n", orderId);
        status = TaskStatus.ROLLED_BACK;
    }

    @Override
    public String getTaskId() { return taskId; }
    @Override
    public String getDescription() { return "ShipOrder " + orderId; }
    @Override
    public TaskStatus getStatus() { return status; }
}

class SagaOrchestrator {
    private String sagaId;
    private List<SagaStep> steps = new ArrayList<>();
    private List<SagaStep> completedSteps = new ArrayList<>();

    public SagaOrchestrator(String sagaId) {
        this.sagaId = sagaId;
    }

    public void addStep(SagaStep step) {
        steps.add(step);
    }

    public boolean execute() {
        System.out.printf("%n🎭 SAGA [%s] Starting execution with %d steps...%n", sagaId, steps.size());

        for (SagaStep step : steps) {
            try {
                System.out.printf("  ▶️  Step: %s%n", step.getStepName());
                step.getForwardCommand().execute();
                completedSteps.add(step);
            } catch (Exception e) {
                System.out.printf("  ❌ Step '%s' FAILED: %s%n", step.getStepName(), e.getMessage());
                System.out.printf("  🔄 Initiating compensation (rollback)...%n");
                compensate();
                return false;
            }
        }

        System.out.printf("✅ SAGA [%s] completed successfully%n", sagaId);
        return true;
    }

    private void compensate() {
        Collections.reverse(completedSteps);
        for (SagaStep step : completedSteps) {
            System.out.printf("  ↩️  Compensating: %s%n", step.getStepName());
            step.getCompensationCommand().execute();
        }
        System.out.printf("🔄 SAGA [%s] rolled back%n", sagaId);
    }
}

// ==================== EXAMPLE 3: TEXT EDITOR UNDO/REDO ====================
// Used in: Google Docs, VS Code, any editor with undo/redo
// Interview Question: Design a collaborative text editor

interface EditorCommand {
    void execute();
    void undo();
    String describe();
}

class InsertTextCommand implements EditorCommand {
    private StringBuilder document;
    private int position;
    private String text;

    public InsertTextCommand(StringBuilder document, int position, String text) {
        this.document = document;
        this.position = position;
        this.text = text;
    }

    @Override
    public void execute() {
        document.insert(position, text);
        System.out.printf("✏️  Insert '%s' at position %d%n", text, position);
    }

    @Override
    public void undo() {
        document.delete(position, position + text.length());
        System.out.printf("↩️  Undo insert '%s' at position %d%n", text, position);
    }

    @Override
    public String describe() { return "Insert('" + text + "', pos=" + position + ")"; }
}

class DeleteTextCommand implements EditorCommand {
    private StringBuilder document;
    private int position;
    private int length;
    private String deletedText;

    public DeleteTextCommand(StringBuilder document, int position, int length) {
        this.document = document;
        this.position = position;
        this.length = length;
    }

    @Override
    public void execute() {
        deletedText = document.substring(position, position + length);
        document.delete(position, position + length);
        System.out.printf("🗑️  Delete '%s' at position %d%n", deletedText, position);
    }

    @Override
    public void undo() {
        document.insert(position, deletedText);
        System.out.printf("↩️  Undo delete, restored '%s' at position %d%n", deletedText, position);
    }

    @Override
    public String describe() { return "Delete(pos=" + position + ", len=" + length + ")"; }
}

class TextEditor {
    private StringBuilder document = new StringBuilder();
    private Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private Deque<EditorCommand> redoStack = new ArrayDeque<>();

    public void executeCommand(EditorCommand cmd) {
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear(); // Clear redo stack on new action
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            System.out.println("⚠️  Nothing to undo");
            return;
        }
        EditorCommand cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            System.out.println("⚠️  Nothing to redo");
            return;
        }
        EditorCommand cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);
    }

    public String getDocument() { return document.toString(); }
    public StringBuilder getBuffer() { return document; }

    public void printState() {
        System.out.printf("📄 Document: \"%s\" | Undo: %d | Redo: %d%n",
            document.toString(), undoStack.size(), redoStack.size());
    }
}

// ==================== EXAMPLE 4: DATABASE MIGRATION SYSTEM ====================
// Used in: Flyway, Liquibase, Rails ActiveRecord Migrations
// Interview Question: Design a database migration tool

interface MigrationCommand {
    void up();
    void down();
    String getVersion();
    String getDescription();
}

class CreateTableMigration implements MigrationCommand {
    private String version;
    private String tableName;
    private Map<String, String> columns;

    public CreateTableMigration(String version, String tableName, Map<String, String> columns) {
        this.version = version;
        this.tableName = tableName;
        this.columns = columns;
    }

    @Override
    public void up() {
        System.out.printf("⬆️  [%s] CREATE TABLE %s (%s)%n", version, tableName, columns);
    }

    @Override
    public void down() {
        System.out.printf("⬇️  [%s] DROP TABLE %s%n", version, tableName);
    }

    @Override
    public String getVersion() { return version; }
    @Override
    public String getDescription() { return "Create table " + tableName; }
}

class AddColumnMigration implements MigrationCommand {
    private String version;
    private String tableName;
    private String columnName;
    private String columnType;

    public AddColumnMigration(String version, String tableName, String columnName, String columnType) {
        this.version = version;
        this.tableName = tableName;
        this.columnName = columnName;
        this.columnType = columnType;
    }

    @Override
    public void up() {
        System.out.printf("⬆️  [%s] ALTER TABLE %s ADD COLUMN %s %s%n", version, tableName, columnName, columnType);
    }

    @Override
    public void down() {
        System.out.printf("⬇️  [%s] ALTER TABLE %s DROP COLUMN %s%n", version, tableName, columnName);
    }

    @Override
    public String getVersion() { return version; }
    @Override
    public String getDescription() { return "Add column " + columnName + " to " + tableName; }
}

class AddIndexMigration implements MigrationCommand {
    private String version;
    private String tableName;
    private String indexName;
    private String columnName;

    public AddIndexMigration(String version, String tableName, String indexName, String columnName) {
        this.version = version;
        this.tableName = tableName;
        this.indexName = indexName;
        this.columnName = columnName;
    }

    @Override
    public void up() {
        System.out.printf("⬆️  [%s] CREATE INDEX %s ON %s(%s)%n", version, indexName, tableName, columnName);
    }

    @Override
    public void down() {
        System.out.printf("⬇️  [%s] DROP INDEX %s%n", version, indexName);
    }

    @Override
    public String getVersion() { return version; }
    @Override
    public String getDescription() { return "Add index " + indexName; }
}

class MigrationRunner {
    private List<MigrationCommand> migrations = new ArrayList<>();
    private List<MigrationCommand> appliedMigrations = new ArrayList<>();
    private String currentVersion = "0";

    public void addMigration(MigrationCommand migration) {
        migrations.add(migration);
    }

    public void migrateUp() {
        System.out.println("\n🔼 Running all pending migrations...");
        for (MigrationCommand migration : migrations) {
            if (!appliedMigrations.contains(migration)) {
                migration.up();
                appliedMigrations.add(migration);
                currentVersion = migration.getVersion();
            }
        }
        System.out.println("✅ Current version: " + currentVersion);
    }

    public void rollback(int steps) {
        System.out.printf("%n🔽 Rolling back %d migration(s)...%n", steps);
        for (int i = 0; i < steps && !appliedMigrations.isEmpty(); i++) {
            MigrationCommand lastMigration = appliedMigrations.remove(appliedMigrations.size() - 1);
            lastMigration.down();
        }
        currentVersion = appliedMigrations.isEmpty() ? "0" :
            appliedMigrations.get(appliedMigrations.size() - 1).getVersion();
        System.out.println("✅ Rolled back to version: " + currentVersion);
    }

    public void printStatus() {
        System.out.println("\n📋 Migration Status:");
        for (MigrationCommand m : migrations) {
            boolean applied = appliedMigrations.contains(m);
            System.out.printf("   %s [%s] %s%n", applied ? "✅" : "⬜", m.getVersion(), m.getDescription());
        }
    }
}

// ==================== EXAMPLE 5: SMART HOME / IoT COMMAND SYSTEM ====================
// Used in: IoT platforms, Home automation, Device management
// Interview Question: Design a smart home control system

interface DeviceCommand {
    void execute();
    void undo();
    String getDeviceName();
}

class ThermostatCommand implements DeviceCommand {
    private String deviceName;
    private int targetTemp;
    private int previousTemp;

    public ThermostatCommand(String deviceName, int targetTemp, int previousTemp) {
        this.deviceName = deviceName;
        this.targetTemp = targetTemp;
        this.previousTemp = previousTemp;
    }

    @Override
    public void execute() {
        System.out.printf("🌡️  %s: Setting temperature to %d°F%n", deviceName, targetTemp);
    }

    @Override
    public void undo() {
        System.out.printf("↩️  %s: Reverting temperature to %d°F%n", deviceName, previousTemp);
    }

    @Override
    public String getDeviceName() { return deviceName; }
}

class LightCommand implements DeviceCommand {
    private String deviceName;
    private boolean turnOn;

    public LightCommand(String deviceName, boolean turnOn) {
        this.deviceName = deviceName;
        this.turnOn = turnOn;
    }

    @Override
    public void execute() {
        System.out.printf("💡 %s: Turning %s%n", deviceName, turnOn ? "ON" : "OFF");
    }

    @Override
    public void undo() {
        System.out.printf("↩️  %s: Turning %s%n", deviceName, turnOn ? "OFF" : "ON");
    }

    @Override
    public String getDeviceName() { return deviceName; }
}

class MacroCommand implements DeviceCommand {
    private String macroName;
    private List<DeviceCommand> commands;

    public MacroCommand(String macroName, List<DeviceCommand> commands) {
        this.macroName = macroName;
        this.commands = commands;
    }

    @Override
    public void execute() {
        System.out.printf("🏠 Executing macro '%s' (%d commands):%n", macroName, commands.size());
        for (DeviceCommand cmd : commands) {
            cmd.execute();
        }
    }

    @Override
    public void undo() {
        System.out.printf("↩️  Undoing macro '%s':%n", macroName);
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    @Override
    public String getDeviceName() { return macroName; }
}

class SmartHomeController {
    private Deque<DeviceCommand> history = new ArrayDeque<>();
    private Map<String, List<DeviceCommand>> scheduledCommands = new HashMap<>();

    public void executeCommand(DeviceCommand cmd) {
        cmd.execute();
        history.push(cmd);
    }

    public void undoLast() {
        if (history.isEmpty()) {
            System.out.println("⚠️  No commands to undo");
            return;
        }
        DeviceCommand cmd = history.pop();
        cmd.undo();
    }

    public void scheduleCommand(String time, DeviceCommand cmd) {
        scheduledCommands.computeIfAbsent(time, k -> new ArrayList<>()).add(cmd);
        System.out.printf("⏰ Scheduled '%s' for %s%n", cmd.getDeviceName(), time);
    }

    public void executeScheduled(String time) {
        List<DeviceCommand> commands = scheduledCommands.get(time);
        if (commands != null) {
            System.out.printf("%n⏰ Executing scheduled commands for %s:%n", time);
            for (DeviceCommand cmd : commands) {
                executeCommand(cmd);
            }
        }
    }
}

// ==================== DEMO ====================

public class CommandPattern {
    public static void main(String[] args) {
        System.out.println("========== COMMAND PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: TASK QUEUE =====
        System.out.println("===== EXAMPLE 1: TASK QUEUE / JOB SCHEDULER (Celery/SQS) =====");

        TaskQueue queue = new TaskQueue("order-processing");
        queue.enqueue(new SendEmailCommand("alice@example.com", "Order Confirmation", "Your order is placed"));
        queue.enqueue(new ProcessPaymentCommand("ORD-001", 99.99));
        queue.enqueue(new GenerateReportCommand("sales", "2024-Q1"));
        queue.processAll();
        System.out.println("\nUndoing last task:");
        queue.undoLast();

        // ===== EXAMPLE 2: SAGA ORCHESTRATOR =====
        System.out.println("\n\n===== EXAMPLE 2: SAGA ORCHESTRATOR (Distributed Transactions) =====");

        // Successful saga
        SagaOrchestrator saga1 = new SagaOrchestrator("order-saga-001");
        saga1.addStep(new SagaStep("Reserve Inventory",
            new ReserveInventoryCommand("PROD-100", 2),
            new ReserveInventoryCommand("PROD-100", -2)));
        saga1.addStep(new SagaStep("Process Payment",
            new ProcessPaymentCommand("ORD-001", 199.99),
            new ProcessPaymentCommand("ORD-001", -199.99)));
        saga1.addStep(new SagaStep("Ship Order",
            new ShipOrderCommand("ORD-001", "123 Main St", false),
            new ShipOrderCommand("ORD-001", "123 Main St", false)));
        saga1.execute();

        // Failed saga with compensation
        SagaOrchestrator saga2 = new SagaOrchestrator("order-saga-002");
        saga2.addStep(new SagaStep("Reserve Inventory",
            new ReserveInventoryCommand("PROD-200", 1),
            new ReserveInventoryCommand("PROD-200", -1)));
        saga2.addStep(new SagaStep("Process Payment",
            new ProcessPaymentCommand("ORD-002", 59.99),
            new ProcessPaymentCommand("ORD-002", -59.99)));
        saga2.addStep(new SagaStep("Ship Order",
            new ShipOrderCommand("ORD-002", "456 Oak Ave", true), // This will fail
            new ShipOrderCommand("ORD-002", "456 Oak Ave", false)));
        saga2.execute();

        // ===== EXAMPLE 3: TEXT EDITOR UNDO/REDO =====
        System.out.println("\n\n===== EXAMPLE 3: TEXT EDITOR UNDO/REDO (Google Docs/VS Code) =====");

        TextEditor editor = new TextEditor();
        editor.executeCommand(new InsertTextCommand(editor.getBuffer(), 0, "Hello"));
        editor.printState();
        editor.executeCommand(new InsertTextCommand(editor.getBuffer(), 5, " World"));
        editor.printState();
        editor.executeCommand(new InsertTextCommand(editor.getBuffer(), 11, "!"));
        editor.printState();

        System.out.println("\nUndo twice:");
        editor.undo();
        editor.printState();
        editor.undo();
        editor.printState();

        System.out.println("\nRedo once:");
        editor.redo();
        editor.printState();

        // ===== EXAMPLE 4: DATABASE MIGRATION =====
        System.out.println("\n\n===== EXAMPLE 4: DATABASE MIGRATION (Flyway/Liquibase) =====");

        MigrationRunner runner = new MigrationRunner();

        Map<String, String> userColumns = new LinkedHashMap<>();
        userColumns.put("id", "BIGINT PRIMARY KEY");
        userColumns.put("email", "VARCHAR(255)");
        userColumns.put("name", "VARCHAR(100)");

        runner.addMigration(new CreateTableMigration("V001", "users", userColumns));

        Map<String, String> orderColumns = new LinkedHashMap<>();
        orderColumns.put("id", "BIGINT PRIMARY KEY");
        orderColumns.put("user_id", "BIGINT REFERENCES users(id)");
        orderColumns.put("total", "DECIMAL(10,2)");

        runner.addMigration(new CreateTableMigration("V002", "orders", orderColumns));
        runner.addMigration(new AddColumnMigration("V003", "users", "phone", "VARCHAR(20)"));
        runner.addMigration(new AddIndexMigration("V004", "orders", "idx_orders_user_id", "user_id"));

        runner.migrateUp();
        runner.printStatus();

        runner.rollback(2);
        runner.printStatus();

        // ===== EXAMPLE 5: SMART HOME =====
        System.out.println("\n\n===== EXAMPLE 5: SMART HOME / IoT COMMANDS =====");

        SmartHomeController home = new SmartHomeController();

        home.executeCommand(new LightCommand("Living Room Light", true));
        home.executeCommand(new ThermostatCommand("Main Thermostat", 72, 68));

        // Create a macro (batch of commands)
        List<DeviceCommand> goodNightCommands = Arrays.asList(
            new LightCommand("Living Room Light", false),
            new LightCommand("Bedroom Light", false),
            new ThermostatCommand("Main Thermostat", 65, 72)
        );
        MacroCommand goodNight = new MacroCommand("Good Night", goodNightCommands);
        home.executeCommand(goodNight);

        System.out.println("\nUndo the macro:");
        home.undoLast();

        // Schedule commands
        home.scheduleCommand("07:00", new LightCommand("Kitchen Light", true));
        home.scheduleCommand("07:00", new ThermostatCommand("Main Thermostat", 72, 65));
        home.executeScheduled("07:00");

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Task queues (Celery, SQS, Sidekiq)");
        System.out.println("• Saga pattern for distributed transactions");
        System.out.println("• Undo/Redo in editors (Google Docs, VS Code)");
        System.out.println("• Database migrations (Flyway, Liquibase)");
        System.out.println("• IoT and smart home automation");
    }
}
