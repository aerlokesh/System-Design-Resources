import java.util.*;
import java.time.*;

// ==================== BUILDER PATTERN ====================
// Definition: Separates the construction of a complex object from its representation,
// allowing the same construction process to create different representations.
//
// Real-World Examples:
// 1. House Builder - Build different types of houses with various features
// 2. Computer Builder - Assemble computers with different components
// 3. Meal Builder - Create meals with different items
// 4. Report Builder - Generate reports with various sections
// 5. Query Builder - Construct database queries
//
// When to Use:
// - Complex objects with many optional parameters
// - Want immutable objects
// - Need different representations of construction process
// - Object creation requires multiple steps
//
// Benefits:
// 1. Separates construction from representation
// 2. Finer control over construction process
// 3. Allows step-by-step object creation
// 4. Can vary internal representation of products

// ==================== EXAMPLE 1: HOUSE BUILDER ====================

class House {
    private String foundation;
    private String structure;
    private String roof;
    private boolean hasGarage;
    private boolean hasSwimmingPool;
    private boolean hasGarden;
    private int floors;
    private int rooms;

    private House(HouseBuilder builder) {
        this.foundation = builder.foundation;
        this.structure = builder.structure;
        this.roof = builder.roof;
        this.hasGarage = builder.hasGarage;
        this.hasSwimmingPool = builder.hasSwimmingPool;
        this.hasGarden = builder.hasGarden;
        this.floors = builder.floors;
        this.rooms = builder.rooms;
    }

    public static class HouseBuilder {
        private String foundation;
        private String structure;
        private String roof;
        private boolean hasGarage = false;
        private boolean hasSwimmingPool = false;
        private boolean hasGarden = false;
        private int floors = 1;
        private int rooms = 1;

        public HouseBuilder(String foundation, String structure, String roof) {
            this.foundation = foundation;
            this.structure = structure;
            this.roof = roof;
        }

        public HouseBuilder withGarage(boolean hasGarage) {
            this.hasGarage = hasGarage;
            return this;
        }

        public HouseBuilder withSwimmingPool(boolean hasSwimmingPool) {
            this.hasSwimmingPool = hasSwimmingPool;
            return this;
        }

        public HouseBuilder withGarden(boolean hasGarden) {
            this.hasGarden = hasGarden;
            return this;
        }

        public HouseBuilder floors(int floors) {
            this.floors = floors;
            return this;
        }

        public HouseBuilder rooms(int rooms) {
            this.rooms = rooms;
            return this;
        }

        public House build() {
            return new House(this);
        }
    }

    @Override
    public String toString() {
        return String.format("🏠 House: %s foundation, %s structure, %s roof, " +
                           "%d floors, %d rooms%s%s%s",
            foundation, structure, roof, floors, rooms,
            hasGarage ? ", Garage" : "",
            hasSwimmingPool ? ", Swimming Pool" : "",
            hasGarden ? ", Garden" : "");
    }
}

// ==================== EXAMPLE 2: COMPUTER BUILDER ====================

class Computer {
    private String cpu;
    private String ram;
    private String storage;
    private String gpu;
    private String motherboard;
    private String powerSupply;
    private String coolingSystem;
    private boolean hasWiFi;
    private boolean hasBluetooth;

    private Computer(ComputerBuilder builder) {
        this.cpu = builder.cpu;
        this.ram = builder.ram;
        this.storage = builder.storage;
        this.gpu = builder.gpu;
        this.motherboard = builder.motherboard;
        this.powerSupply = builder.powerSupply;
        this.coolingSystem = builder.coolingSystem;
        this.hasWiFi = builder.hasWiFi;
        this.hasBluetooth = builder.hasBluetooth;
    }

    public static class ComputerBuilder {
        // Required parameters
        private String cpu;
        private String ram;
        private String storage;

        // Optional parameters
        private String gpu = "Integrated";
        private String motherboard = "Standard";
        private String powerSupply = "500W";
        private String coolingSystem = "Air";
        private boolean hasWiFi = false;
        private boolean hasBluetooth = false;

        public ComputerBuilder(String cpu, String ram, String storage) {
            this.cpu = cpu;
            this.ram = ram;
            this.storage = storage;
        }

        public ComputerBuilder gpu(String gpu) {
            this.gpu = gpu;
            return this;
        }

        public ComputerBuilder motherboard(String motherboard) {
            this.motherboard = motherboard;
            return this;
        }

        public ComputerBuilder powerSupply(String powerSupply) {
            this.powerSupply = powerSupply;
            return this;
        }

        public ComputerBuilder coolingSystem(String coolingSystem) {
            this.coolingSystem = coolingSystem;
            return this;
        }

        public ComputerBuilder withWiFi(boolean hasWiFi) {
            this.hasWiFi = hasWiFi;
            return this;
        }

        public ComputerBuilder withBluetooth(boolean hasBluetooth) {
            this.hasBluetooth = hasBluetooth;
            return this;
        }

        public Computer build() {
            return new Computer(this);
        }
    }

    @Override
    public String toString() {
        return String.format("💻 Computer: CPU=%s, RAM=%s, Storage=%s, GPU=%s, " +
                           "Motherboard=%s, PSU=%s, Cooling=%s%s%s",
            cpu, ram, storage, gpu, motherboard, powerSupply, coolingSystem,
            hasWiFi ? ", WiFi" : "",
            hasBluetooth ? ", Bluetooth" : "");
    }
}

// ==================== EXAMPLE 3: MEAL BUILDER ====================

class Meal {
    private String mainCourse;
    private String sideDish;
    private String drink;
    private String dessert;
    private String appetizer;

    private Meal(MealBuilder builder) {
        this.mainCourse = builder.mainCourse;
        this.sideDish = builder.sideDish;
        this.drink = builder.drink;
        this.dessert = builder.dessert;
        this.appetizer = builder.appetizer;
    }

    public static class MealBuilder {
        private String mainCourse;
        private String sideDish;
        private String drink;
        private String dessert;
        private String appetizer;

        public MealBuilder mainCourse(String mainCourse) {
            this.mainCourse = mainCourse;
            return this;
        }

        public MealBuilder sideDish(String sideDish) {
            this.sideDish = sideDish;
            return this;
        }

        public MealBuilder drink(String drink) {
            this.drink = drink;
            return this;
        }

        public MealBuilder dessert(String dessert) {
            this.dessert = dessert;
            return this;
        }

        public MealBuilder appetizer(String appetizer) {
            this.appetizer = appetizer;
            return this;
        }

        public Meal build() {
            return new Meal(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("🍽️ Meal: ");
        List<String> items = new ArrayList<>();
        
        if (appetizer != null) items.add("Appetizer: " + appetizer);
        if (mainCourse != null) items.add("Main: " + mainCourse);
        if (sideDish != null) items.add("Side: " + sideDish);
        if (drink != null) items.add("Drink: " + drink);
        if (dessert != null) items.add("Dessert: " + dessert);
        
        return sb.append(String.join(", ", items)).toString();
    }
}

// ==================== EXAMPLE 4: REPORT BUILDER ====================

class Report {
    private String title;
    private String header;
    private String body;
    private String footer;
    private LocalDate date;
    private String author;
    private List<String> sections;

    private Report(ReportBuilder builder) {
        this.title = builder.title;
        this.header = builder.header;
        this.body = builder.body;
        this.footer = builder.footer;
        this.date = builder.date;
        this.author = builder.author;
        this.sections = builder.sections;
    }

    public static class ReportBuilder {
        private String title;
        private String header = "";
        private String body = "";
        private String footer = "";
        private LocalDate date = LocalDate.now();
        private String author = "Unknown";
        private List<String> sections = new ArrayList<>();

        public ReportBuilder(String title) {
            this.title = title;
        }

        public ReportBuilder header(String header) {
            this.header = header;
            return this;
        }

        public ReportBuilder body(String body) {
            this.body = body;
            return this;
        }

        public ReportBuilder footer(String footer) {
            this.footer = footer;
            return this;
        }

        public ReportBuilder date(LocalDate date) {
            this.date = date;
            return this;
        }

        public ReportBuilder author(String author) {
            this.author = author;
            return this;
        }

        public ReportBuilder addSection(String section) {
            this.sections.add(section);
            return this;
        }

        public Report build() {
            return new Report(this);
        }
    }

    @Override
    public String toString() {
        return String.format("📄 Report: '%s' by %s on %s\n   Header: %s\n   Sections: %d\n   Footer: %s",
            title, author, date, header.isEmpty() ? "None" : header, sections.size(),
            footer.isEmpty() ? "None" : footer);
    }
}

// ==================== EXAMPLE 5: SQL QUERY BUILDER ====================

class SQLQuery {
    private String table;
    private List<String> columns;
    private String whereClause;
    private String orderBy;
    private String groupBy;
    private Integer limit;
    private Integer offset;
    private List<String> joins;

    private SQLQuery(SQLQueryBuilder builder) {
        this.table = builder.table;
        this.columns = builder.columns;
        this.whereClause = builder.whereClause;
        this.orderBy = builder.orderBy;
        this.groupBy = builder.groupBy;
        this.limit = builder.limit;
        this.offset = builder.offset;
        this.joins = builder.joins;
    }

    public static class SQLQueryBuilder {
        private String table;
        private List<String> columns = new ArrayList<>();
        private String whereClause = "";
        private String orderBy = "";
        private String groupBy = "";
        private Integer limit = null;
        private Integer offset = null;
        private List<String> joins = new ArrayList<>();

        public SQLQueryBuilder from(String table) {
            this.table = table;
            return this;
        }

        public SQLQueryBuilder select(String... columns) {
            this.columns.addAll(Arrays.asList(columns));
            return this;
        }

        public SQLQueryBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public SQLQueryBuilder orderBy(String column) {
            this.orderBy = column;
            return this;
        }

        public SQLQueryBuilder groupBy(String column) {
            this.groupBy = column;
            return this;
        }

        public SQLQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SQLQueryBuilder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public SQLQueryBuilder join(String joinClause) {
            this.joins.add(joinClause);
            return this;
        }

        public SQLQuery build() {
            return new SQLQuery(this);
        }
    }

    public String toSQL() {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", columns));
        }
        
        sql.append(" FROM ").append(table);
        
        for (String join : joins) {
            sql.append(" ").append(join);
        }
        
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        
        if (!groupBy.isEmpty()) {
            sql.append(" GROUP BY ").append(groupBy);
        }
        
        if (!orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        
        if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }
        
        if (offset != null) {
            sql.append(" OFFSET ").append(offset);
        }
        
        return sql.toString();
    }

    @Override
    public String toString() {
        return "🗃️ " + toSQL();
    }
}

// ==================== EXAMPLE 6: USER PROFILE BUILDER ====================

class UserProfile {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private int age;
    private String phone;
    private String address;
    private String bio;
    private List<String> interests;

    private UserProfile(UserProfileBuilder builder) {
        this.username = builder.username;
        this.email = builder.email;
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.age = builder.age;
        this.phone = builder.phone;
        this.address = builder.address;
        this.bio = builder.bio;
        this.interests = builder.interests;
    }

    public static class UserProfileBuilder {
        // Required
        private String username;
        private String email;

        // Optional
        private String firstName = "";
        private String lastName = "";
        private int age = 0;
        private String phone = "";
        private String address = "";
        private String bio = "";
        private List<String> interests = new ArrayList<>();

        public UserProfileBuilder(String username, String email) {
            this.username = username;
            this.email = email;
        }

        public UserProfileBuilder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public UserProfileBuilder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public UserProfileBuilder age(int age) {
            this.age = age;
            return this;
        }

        public UserProfileBuilder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public UserProfileBuilder address(String address) {
            this.address = address;
            return this;
        }

        public UserProfileBuilder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public UserProfileBuilder addInterest(String interest) {
            this.interests.add(interest);
            return this;
        }

        public UserProfile build() {
            return new UserProfile(this);
        }
    }

    @Override
    public String toString() {
        return String.format("👤 Profile: %s (%s)\n   Name: %s %s\n   Age: %s\n   " +
                           "Phone: %s\n   Address: %s\n   Bio: %s\n   Interests: %s",
            username, email,
            firstName.isEmpty() ? "N/A" : firstName,
            lastName.isEmpty() ? "N/A" : lastName,
            age == 0 ? "N/A" : age,
            phone.isEmpty() ? "N/A" : phone,
            address.isEmpty() ? "N/A" : address,
            bio.isEmpty() ? "N/A" : bio,
            interests.isEmpty() ? "None" : String.join(", ", interests));
    }
}

// ==================== DEMO ====================

public class BuilderPattern {
    public static void main(String[] args) {
        System.out.println("========== BUILDER PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: HOUSE BUILDER =====
        System.out.println("===== EXAMPLE 1: HOUSE BUILDER =====\n");
        
        House simpleHouse = new House.HouseBuilder("Concrete", "Wood", "Shingle")
            .rooms(3)
            .build();
        System.out.println(simpleHouse);
        
        House luxuryHouse = new House.HouseBuilder("Reinforced Concrete", "Brick", "Tile")
            .floors(3)
            .rooms(8)
            .withGarage(true)
            .withSwimmingPool(true)
            .withGarden(true)
            .build();
        System.out.println(luxuryHouse);

        // ===== EXAMPLE 2: COMPUTER BUILDER =====
        System.out.println("\n\n===== EXAMPLE 2: COMPUTER BUILDER =====\n");
        
        Computer officePC = new Computer.ComputerBuilder("Intel i5", "8GB", "256GB SSD")
            .withWiFi(true)
            .build();
        System.out.println(officePC);
        
        Computer gamingPC = new Computer.ComputerBuilder("AMD Ryzen 9", "32GB", "1TB NVMe SSD")
            .gpu("NVIDIA RTX 4080")
            .motherboard("ASUS ROG")
            .powerSupply("850W")
            .coolingSystem("Liquid")
            .withWiFi(true)
            .withBluetooth(true)
            .build();
        System.out.println(gamingPC);

        // ===== EXAMPLE 3: MEAL BUILDER =====
        System.out.println("\n\n===== EXAMPLE 3: MEAL BUILDER =====\n");
        
        Meal lightMeal = new Meal.MealBuilder()
            .mainCourse("Salad")
            .drink("Water")
            .build();
        System.out.println(lightMeal);
        
        Meal fullMeal = new Meal.MealBuilder()
            .appetizer("Soup")
            .mainCourse("Steak")
            .sideDish("Mashed Potatoes")
            .drink("Red Wine")
            .dessert("Chocolate Cake")
            .build();
        System.out.println(fullMeal);

        // ===== EXAMPLE 4: REPORT BUILDER =====
        System.out.println("\n\n===== EXAMPLE 4: REPORT BUILDER =====\n");
        
        Report quarterlyReport = new Report.ReportBuilder("Q4 2024 Financial Report")
            .author("John Doe")
            .header("Quarterly Financial Summary")
            .date(LocalDate.of(2024, 12, 31))
            .addSection("Revenue Analysis")
            .addSection("Expense Breakdown")
            .addSection("Profit Margins")
            .footer("Confidential - Internal Use Only")
            .build();
        System.out.println(quarterlyReport);

        // ===== EXAMPLE 5: SQL QUERY BUILDER =====
        System.out.println("\n\n===== EXAMPLE 5: SQL QUERY BUILDER =====\n");
        
        SQLQuery simpleQuery = new SQLQuery.SQLQueryBuilder()
            .select("name", "email")
            .from("users")
            .where("age > 25")
            .orderBy("name")
            .limit(10)
            .build();
        System.out.println(simpleQuery);
        
        SQLQuery complexQuery = new SQLQuery.SQLQueryBuilder()
            .select("u.name", "u.email", "o.total")
            .from("users u")
            .join("INNER JOIN orders o ON u.id = o.user_id")
            .where("o.status = 'completed'")
            .groupBy("u.id")
            .orderBy("o.total DESC")
            .limit(20)
            .offset(10)
            .build();
        System.out.println(complexQuery);

        // ===== EXAMPLE 6: USER PROFILE BUILDER =====
        System.out.println("\n\n===== EXAMPLE 6: USER PROFILE BUILDER =====\n");
        
        UserProfile basicProfile = new UserProfile.UserProfileBuilder("alice123", "alice@example.com")
            .build();
        System.out.println(basicProfile);
        
        System.out.println();
        
        UserProfile completeProfile = new UserProfile.UserProfileBuilder("bob456", "bob@example.com")
            .firstName("Bob")
            .lastName("Smith")
            .age(30)
            .phone("+1-555-0123")
            .address("123 Main St, Anytown, USA")
            .bio("Software engineer passionate about clean code")
            .addInterest("Programming")
            .addInterest("Reading")
            .addInterest("Hiking")
            .build();
        System.out.println(completeProfile);

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
