import java.util.*;

// ==================== FACTORY PATTERN ====================
// Definition: Provides an interface for creating objects in a superclass, but allows
// subclasses to alter the type of objects that will be created.
//
// Real-World Examples:
// 1. Database Connection Factory - MySQL, PostgreSQL, MongoDB
// 2. Notification System - Email, SMS, Push notifications
// 3. Payment Processing - Credit Card, PayPal, Crypto
// 4. Document Generation - PDF, Word, Excel
// 5. Vehicle Manufacturing - Car, Truck, Motorcycle
//
// When to Use:
// - Don't know exact types of objects needed beforehand
// - Want to provide a library of products
// - Want to delegate object creation to subclasses
// - Need to add new product types easily
//
// Benefits:
// 1. Single Responsibility - creation logic in one place
// 2. Open/Closed Principle - easy to add new types
// 3. Loose coupling - code works with abstractions
// 4. Flexibility in product creation

// ==================== EXAMPLE 1: DATABASE CONNECTION FACTORY ====================

interface DatabaseConnection {
    void connect();
    void disconnect();
    void executeQuery(String query);
    String getType();
}

class MySQLConnection implements DatabaseConnection {
    private String host;
    private int port;

    public MySQLConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() {
        System.out.println("🔌 Connected to MySQL at " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 Disconnected from MySQL");
    }

    @Override
    public void executeQuery(String query) {
        System.out.println("📊 MySQL executing: " + query);
    }

    @Override
    public String getType() {
        return "MySQL";
    }
}

class PostgreSQLConnection implements DatabaseConnection {
    private String host;
    private int port;

    public PostgreSQLConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() {
        System.out.println("🔌 Connected to PostgreSQL at " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 Disconnected from PostgreSQL");
    }

    @Override
    public void executeQuery(String query) {
        System.out.println("📊 PostgreSQL executing: " + query);
    }

    @Override
    public String getType() {
        return "PostgreSQL";
    }
}

class MongoDBConnection implements DatabaseConnection {
    private String host;
    private int port;

    public MongoDBConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() {
        System.out.println("🔌 Connected to MongoDB at " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 Disconnected from MongoDB");
    }

    @Override
    public void executeQuery(String query) {
        System.out.println("📊 MongoDB executing: " + query);
    }

    @Override
    public String getType() {
        return "MongoDB";
    }
}

class DatabaseConnectionFactory {
    public static DatabaseConnection createConnection(String type, String host, int port) {
        switch (type.toLowerCase()) {
            case "mysql":
                return new MySQLConnection(host, port);
            case "postgresql":
                return new PostgreSQLConnection(host, port);
            case "mongodb":
                return new MongoDBConnection(host, port);
            default:
                throw new IllegalArgumentException("Unknown database type: " + type);
        }
    }
}

// ==================== EXAMPLE 2: NOTIFICATION SYSTEM ====================

interface Notification {
    void send(String recipient, String message);
    String getType();
}

class EmailNotification implements Notification {
    @Override
    public void send(String recipient, String message) {
        System.out.println("📧 Email to " + recipient + ": " + message);
    }

    @Override
    public String getType() {
        return "Email";
    }
}

class SMSNotification implements Notification {
    @Override
    public void send(String recipient, String message) {
        System.out.println("📱 SMS to " + recipient + ": " + message);
    }

    @Override
    public String getType() {
        return "SMS";
    }
}

class PushNotification implements Notification {
    @Override
    public void send(String recipient, String message) {
        System.out.println("🔔 Push notification to " + recipient + ": " + message);
    }

    @Override
    public String getType() {
        return "Push";
    }
}

class SlackNotification implements Notification {
    @Override
    public void send(String recipient, String message) {
        System.out.println("💬 Slack message to " + recipient + ": " + message);
    }

    @Override
    public String getType() {
        return "Slack";
    }
}

class NotificationFactory {
    public static Notification createNotification(String type) {
        switch (type.toLowerCase()) {
            case "email":
                return new EmailNotification();
            case "sms":
                return new SMSNotification();
            case "push":
                return new PushNotification();
            case "slack":
                return new SlackNotification();
            default:
                throw new IllegalArgumentException("Unknown notification type: " + type);
        }
    }
}

// ==================== EXAMPLE 3: PAYMENT PROCESSING ====================

interface PaymentProcessor {
    void processPayment(double amount, String currency);
    void refund(String transactionId, double amount);
    String getProcessorName();
}

class CreditCardProcessor implements PaymentProcessor {
    @Override
    public void processPayment(double amount, String currency) {
        System.out.printf("💳 Processing credit card payment: %.2f %s%n", amount, currency);
    }

    @Override
    public void refund(String transactionId, double amount) {
        System.out.printf("💳 Refunding credit card: %.2f for transaction %s%n", amount, transactionId);
    }

    @Override
    public String getProcessorName() {
        return "Credit Card";
    }
}

class PayPalProcessor implements PaymentProcessor {
    @Override
    public void processPayment(double amount, String currency) {
        System.out.printf("🅿️ Processing PayPal payment: %.2f %s%n", amount, currency);
    }

    @Override
    public void refund(String transactionId, double amount) {
        System.out.printf("🅿️ Refunding PayPal: %.2f for transaction %s%n", amount, transactionId);
    }

    @Override
    public String getProcessorName() {
        return "PayPal";
    }
}

class CryptoProcessor implements PaymentProcessor {
    @Override
    public void processPayment(double amount, String currency) {
        System.out.printf("₿ Processing crypto payment: %.2f %s%n", amount, currency);
    }

    @Override
    public void refund(String transactionId, double amount) {
        System.out.printf("₿ Refunding crypto: %.2f for transaction %s%n", amount, transactionId);
    }

    @Override
    public String getProcessorName() {
        return "Cryptocurrency";
    }
}

class PaymentProcessorFactory {
    public static PaymentProcessor createProcessor(String type) {
        switch (type.toLowerCase()) {
            case "creditcard":
            case "credit_card":
                return new CreditCardProcessor();
            case "paypal":
                return new PayPalProcessor();
            case "crypto":
            case "cryptocurrency":
                return new CryptoProcessor();
            default:
                throw new IllegalArgumentException("Unknown payment processor: " + type);
        }
    }
}

// ==================== EXAMPLE 4: DOCUMENT GENERATION ====================

interface Document {
    void create(String title, String content);
    void save(String filename);
    String getFormat();
}

class PDFDocument implements Document {
    private String title;
    private String content;

    @Override
    public void create(String title, String content) {
        this.title = title;
        this.content = content;
        System.out.println("📄 Creating PDF document: " + title);
    }

    @Override
    public void save(String filename) {
        System.out.println("💾 Saving PDF to: " + filename + ".pdf");
    }

    @Override
    public String getFormat() {
        return "PDF";
    }
}

class WordDocument implements Document {
    private String title;
    private String content;

    @Override
    public void create(String title, String content) {
        this.title = title;
        this.content = content;
        System.out.println("📄 Creating Word document: " + title);
    }

    @Override
    public void save(String filename) {
        System.out.println("💾 Saving Word document to: " + filename + ".docx");
    }

    @Override
    public String getFormat() {
        return "Word";
    }
}

class ExcelDocument implements Document {
    private String title;
    private String content;

    @Override
    public void create(String title, String content) {
        this.title = title;
        this.content = content;
        System.out.println("📄 Creating Excel document: " + title);
    }

    @Override
    public void save(String filename) {
        System.out.println("💾 Saving Excel document to: " + filename + ".xlsx");
    }

    @Override
    public String getFormat() {
        return "Excel";
    }
}

class DocumentFactory {
    public static Document createDocument(String type) {
        switch (type.toLowerCase()) {
            case "pdf":
                return new PDFDocument();
            case "word":
            case "docx":
                return new WordDocument();
            case "excel":
            case "xlsx":
                return new ExcelDocument();
            default:
                throw new IllegalArgumentException("Unknown document type: " + type);
        }
    }
}

// ==================== EXAMPLE 5: VEHICLE MANUFACTURING ====================

interface Vehicle {
    void manufacture();
    void test();
    String getType();
    int getWheelCount();
}

class Car implements Vehicle {
    private String model;

    public Car(String model) {
        this.model = model;
    }

    @Override
    public void manufacture() {
        System.out.println("🚗 Manufacturing car: " + model);
    }

    @Override
    public void test() {
        System.out.println("🧪 Testing car safety and performance");
    }

    @Override
    public String getType() {
        return "Car";
    }

    @Override
    public int getWheelCount() {
        return 4;
    }
}

class Truck implements Vehicle {
    private String model;

    public Truck(String model) {
        this.model = model;
    }

    @Override
    public void manufacture() {
        System.out.println("🚚 Manufacturing truck: " + model);
    }

    @Override
    public void test() {
        System.out.println("🧪 Testing truck load capacity and durability");
    }

    @Override
    public String getType() {
        return "Truck";
    }

    @Override
    public int getWheelCount() {
        return 6;
    }
}

class Motorcycle implements Vehicle {
    private String model;

    public Motorcycle(String model) {
        this.model = model;
    }

    @Override
    public void manufacture() {
        System.out.println("🏍️ Manufacturing motorcycle: " + model);
    }

    @Override
    public void test() {
        System.out.println("🧪 Testing motorcycle stability and speed");
    }

    @Override
    public String getType() {
        return "Motorcycle";
    }

    @Override
    public int getWheelCount() {
        return 2;
    }
}

class VehicleFactory {
    public static Vehicle createVehicle(String type, String model) {
        switch (type.toLowerCase()) {
            case "car":
                return new Car(model);
            case "truck":
                return new Truck(model);
            case "motorcycle":
            case "bike":
                return new Motorcycle(model);
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }
}

// ==================== DEMO ====================

public class FactoryPattern {
    public static void main(String[] args) {
        System.out.println("========== FACTORY PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: DATABASE CONNECTIONS =====
        System.out.println("===== EXAMPLE 1: DATABASE CONNECTION FACTORY =====\n");
        
        DatabaseConnection mysql = DatabaseConnectionFactory.createConnection("mysql", "localhost", 3306);
        mysql.connect();
        mysql.executeQuery("SELECT * FROM users");
        mysql.disconnect();
        
        System.out.println();
        
        DatabaseConnection postgres = DatabaseConnectionFactory.createConnection("postgresql", "localhost", 5432);
        postgres.connect();
        postgres.executeQuery("SELECT * FROM orders");
        postgres.disconnect();
        
        System.out.println();
        
        DatabaseConnection mongo = DatabaseConnectionFactory.createConnection("mongodb", "localhost", 27017);
        mongo.connect();
        mongo.executeQuery("db.products.find()");
        mongo.disconnect();

        // ===== EXAMPLE 2: NOTIFICATION SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 2: NOTIFICATION FACTORY =====\n");
        
        List<String> notificationTypes = Arrays.asList("email", "sms", "push", "slack");
        
        for (String type : notificationTypes) {
            Notification notification = NotificationFactory.createNotification(type);
            notification.send("user@example.com", "Welcome to our platform!");
        }

        // ===== EXAMPLE 3: PAYMENT PROCESSING =====
        System.out.println("\n\n===== EXAMPLE 3: PAYMENT PROCESSOR FACTORY =====\n");
        
        PaymentProcessor creditCard = PaymentProcessorFactory.createProcessor("creditcard");
        creditCard.processPayment(99.99, "USD");
        creditCard.refund("TXN-123", 99.99);
        
        System.out.println();
        
        PaymentProcessor paypal = PaymentProcessorFactory.createProcessor("paypal");
        paypal.processPayment(149.50, "EUR");
        
        System.out.println();
        
        PaymentProcessor crypto = PaymentProcessorFactory.createProcessor("crypto");
        crypto.processPayment(0.005, "BTC");

        // ===== EXAMPLE 4: DOCUMENT GENERATION =====
        System.out.println("\n\n===== EXAMPLE 4: DOCUMENT FACTORY =====\n");
        
        String[] docTypes = {"pdf", "word", "excel"};
        
        for (String docType : docTypes) {
            Document doc = DocumentFactory.createDocument(docType);
            doc.create("Monthly Report", "This is the content of the report");
            doc.save("report_2024");
            System.out.println();
        }

        // ===== EXAMPLE 5: VEHICLE MANUFACTURING =====
        System.out.println("===== EXAMPLE 5: VEHICLE FACTORY =====\n");
        
        Vehicle car = VehicleFactory.createVehicle("car", "Tesla Model 3");
        car.manufacture();
        car.test();
        System.out.println("Wheels: " + car.getWheelCount());
        
        System.out.println();
        
        Vehicle truck = VehicleFactory.createVehicle("truck", "Ford F-150");
        truck.manufacture();
        truck.test();
        System.out.println("Wheels: " + truck.getWheelCount());
        
        System.out.println();
        
        Vehicle motorcycle = VehicleFactory.createVehicle("motorcycle", "Harley Davidson");
        motorcycle.manufacture();
        motorcycle.test();
        System.out.println("Wheels: " + motorcycle.getWheelCount());

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
