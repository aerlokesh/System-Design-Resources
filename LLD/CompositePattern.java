import java.util.*;

// ==================== COMPOSITE PATTERN ====================
// Definition: Composes objects into tree structures to represent part-whole hierarchies.
// Lets clients treat individual objects and compositions uniformly.
//
// Real-World Examples:
// 1. File System - Files and folders
// 2. Organization Structure - Employees and departments
// 3. UI Components - Single widgets and containers
// 4. Menu Systems - Menu items and submenus
// 5. Graphics - Shapes and groups of shapes
//
// When to Use:
// - Represent part-whole hierarchies
// - Treat individual objects and compositions uniformly
// - Implement tree structures
// - Need recursive structures
//
// Benefits:
// 1. Open/Closed Principle - easy to add new components
// 2. Simplifies client code - treats everything uniformly
// 3. Recursive composition - naturally handles nested structures
// 4. Single interface for leaves and composites

// ==================== EXAMPLE 1: FILE SYSTEM ====================

interface FileSystemComponent {
    String getName();
    long getSize();
    void display(String indent);
}

class File implements FileSystemComponent {
    private String name;
    private long size;

    public File(String name, long size) {
        this.name = name;
        this.size = size;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void display(String indent) {
        System.out.println(indent + "📄 " + name + " (" + formatSize(size) + ")");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

class Folder implements FileSystemComponent {
    private String name;
    private List<FileSystemComponent> children;

    public Folder(String name) {
        this.name = name;
        this.children = new ArrayList<>();
    }

    public void add(FileSystemComponent component) {
        children.add(component);
    }

    public void remove(FileSystemComponent component) {
        children.remove(component);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return children.stream()
            .mapToLong(FileSystemComponent::getSize)
            .sum();
    }

    @Override
    public void display(String indent) {
        System.out.println(indent + "📁 " + name + " (" + formatSize(getSize()) + ")");
        for (FileSystemComponent child : children) {
            child.display(indent + "  ");
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

// ==================== EXAMPLE 2: ORGANIZATION STRUCTURE ====================

interface OrganizationComponent {
    String getName();
    double getSalary();
    int getEmployeeCount();
    void display(String indent);
}

class Employee implements OrganizationComponent {
    private String name;
    private String position;
    private double salary;

    public Employee(String name, String position, double salary) {
        this.name = name;
        this.position = position;
        this.salary = salary;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getSalary() {
        return salary;
    }

    @Override
    public int getEmployeeCount() {
        return 1;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s👤 %s (%s) - $%.2f%n", indent, name, position, salary);
    }
}

class Department implements OrganizationComponent {
    private String name;
    private List<OrganizationComponent> members;

    public Department(String name) {
        this.name = name;
        this.members = new ArrayList<>();
    }

    public void add(OrganizationComponent member) {
        members.add(member);
    }

    public void remove(OrganizationComponent member) {
        members.remove(member);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getSalary() {
        return members.stream()
            .mapToDouble(OrganizationComponent::getSalary)
            .sum();
    }

    @Override
    public int getEmployeeCount() {
        return members.stream()
            .mapToInt(OrganizationComponent::getEmployeeCount)
            .sum();
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s🏢 %s (Total: %d employees, $%.2f)%n", 
            indent, name, getEmployeeCount(), getSalary());
        for (OrganizationComponent member : members) {
            member.display(indent + "  ");
        }
    }
}

// ==================== EXAMPLE 3: MENU SYSTEM ====================

interface MenuComponent {
    String getName();
    void display(String indent);
    void execute();
}

class MenuItem implements MenuComponent {
    private String name;
    private String action;

    public MenuItem(String name, String action) {
        this.name = name;
        this.action = action;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void display(String indent) {
        System.out.println(indent + "• " + name);
    }

    @Override
    public void execute() {
        System.out.println("Executing: " + action);
    }
}

class Menu implements MenuComponent {
    private String name;
    private List<MenuComponent> items;

    public Menu(String name) {
        this.name = name;
        this.items = new ArrayList<>();
    }

    public void add(MenuComponent item) {
        items.add(item);
    }

    public void remove(MenuComponent item) {
        items.remove(item);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void display(String indent) {
        System.out.println(indent + "▶ " + name);
        for (MenuComponent item : items) {
            item.display(indent + "  ");
        }
    }

    @Override
    public void execute() {
        System.out.println("Opening menu: " + name);
    }

    public List<MenuComponent> getItems() {
        return items;
    }
}

// ==================== EXAMPLE 4: GRAPHICS SYSTEM ====================

interface Graphic {
    void draw(String indent);
    double getArea();
}

class Circle implements Graphic {
    private String name;
    private double radius;

    public Circle(String name, double radius) {
        this.name = name;
        this.radius = radius;
    }

    @Override
    public void draw(String indent) {
        System.out.printf("%s⭕ Circle '%s' (radius: %.1f, area: %.2f)%n", 
            indent, name, radius, getArea());
    }

    @Override
    public double getArea() {
        return Math.PI * radius * radius;
    }
}

class Rectangle implements Graphic {
    private String name;
    private double width;
    private double height;

    public Rectangle(String name, double width, double height) {
        this.name = name;
        this.width = width;
        this.height = height;
    }

    @Override
    public void draw(String indent) {
        System.out.printf("%s▭ Rectangle '%s' (%.1fx%.1f, area: %.2f)%n", 
            indent, name, width, height, getArea());
    }

    @Override
    public double getArea() {
        return width * height;
    }
}

class GraphicGroup implements Graphic {
    private String name;
    private List<Graphic> graphics;

    public GraphicGroup(String name) {
        this.name = name;
        this.graphics = new ArrayList<>();
    }

    public void add(Graphic graphic) {
        graphics.add(graphic);
    }

    public void remove(Graphic graphic) {
        graphics.remove(graphic);
    }

    @Override
    public void draw(String indent) {
        System.out.printf("%s📦 Group '%s' (total area: %.2f)%n", indent, name, getArea());
        for (Graphic graphic : graphics) {
            graphic.draw(indent + "  ");
        }
    }

    @Override
    public double getArea() {
        return graphics.stream()
            .mapToDouble(Graphic::getArea)
            .sum();
    }
}

// ==================== EXAMPLE 5: PRODUCT CATALOG ====================

interface CatalogComponent {
    String getName();
    double getPrice();
    void display(String indent);
}

class Product implements CatalogComponent {
    private String name;
    private double price;
    private String sku;

    public Product(String name, double price, String sku) {
        this.name = name;
        this.price = price;
        this.sku = sku;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getPrice() {
        return price;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s🛒 %s - $%.2f (SKU: %s)%n", indent, name, price, sku);
    }
}

class Category implements CatalogComponent {
    private String name;
    private List<CatalogComponent> items;

    public Category(String name) {
        this.name = name;
        this.items = new ArrayList<>();
    }

    public void add(CatalogComponent item) {
        items.add(item);
    }

    public void remove(CatalogComponent item) {
        items.remove(item);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getPrice() {
        return items.stream()
            .mapToDouble(CatalogComponent::getPrice)
            .sum();
    }

    public int getItemCount() {
        int count = 0;
        for (CatalogComponent item : items) {
            if (item instanceof Product) {
                count++;
            } else if (item instanceof Category) {
                count += ((Category) item).getItemCount();
            }
        }
        return count;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s📂 %s (%d items, total: $%.2f)%n", 
            indent, name, getItemCount(), getPrice());
        for (CatalogComponent item : items) {
            item.display(indent + "  ");
        }
    }
}

// ==================== DEMO ====================

public class CompositePattern {
    public static void main(String[] args) {
        System.out.println("========== COMPOSITE PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: FILE SYSTEM =====
        System.out.println("===== EXAMPLE 1: FILE SYSTEM =====\n");
        
        Folder root = new Folder("root");
        Folder documents = new Folder("Documents");
        Folder pictures = new Folder("Pictures");
        
        documents.add(new File("resume.pdf", 1024 * 250));
        documents.add(new File("letter.docx", 1024 * 50));
        
        Folder vacation = new Folder("Vacation2024");
        vacation.add(new File("beach.jpg", 1024 * 1024 * 3));
        vacation.add(new File("sunset.jpg", 1024 * 1024 * 2));
        pictures.add(vacation);
        pictures.add(new File("profile.png", 1024 * 100));
        
        root.add(documents);
        root.add(pictures);
        root.add(new File("readme.txt", 500));
        
        root.display("");

        // ===== EXAMPLE 2: ORGANIZATION STRUCTURE =====
        System.out.println("\n\n===== EXAMPLE 2: ORGANIZATION STRUCTURE =====\n");
        
        Department company = new Department("TechCorp");
        
        Department engineering = new Department("Engineering");
        engineering.add(new Employee("Alice Smith", "Senior Engineer", 120000));
        engineering.add(new Employee("Bob Johnson", "Engineer", 90000));
        
        Department frontend = new Department("Frontend Team");
        frontend.add(new Employee("Charlie Brown", "Frontend Lead", 110000));
        frontend.add(new Employee("Diana Prince", "Frontend Developer", 85000));
        engineering.add(frontend);
        
        Department sales = new Department("Sales");
        sales.add(new Employee("Eve Wilson", "Sales Manager", 95000));
        sales.add(new Employee("Frank Miller", "Sales Rep", 70000));
        
        company.add(engineering);
        company.add(sales);
        company.add(new Employee("Grace Lee", "CEO", 200000));
        
        company.display("");

        // ===== EXAMPLE 3: MENU SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 3: MENU SYSTEM =====\n");
        
        Menu mainMenu = new Menu("Main Menu");
        
        Menu fileMenu = new Menu("File");
        fileMenu.add(new MenuItem("New", "Create new file"));
        fileMenu.add(new MenuItem("Open", "Open existing file"));
        fileMenu.add(new MenuItem("Save", "Save current file"));
        
        Menu editMenu = new Menu("Edit");
        editMenu.add(new MenuItem("Cut", "Cut selection"));
        editMenu.add(new MenuItem("Copy", "Copy selection"));
        editMenu.add(new MenuItem("Paste", "Paste from clipboard"));
        
        Menu viewMenu = new Menu("View");
        Menu zoomMenu = new Menu("Zoom");
        zoomMenu.add(new MenuItem("Zoom In", "Increase zoom"));
        zoomMenu.add(new MenuItem("Zoom Out", "Decrease zoom"));
        zoomMenu.add(new MenuItem("Reset Zoom", "Reset to 100%"));
        viewMenu.add(zoomMenu);
        viewMenu.add(new MenuItem("Full Screen", "Toggle full screen"));
        
        mainMenu.add(fileMenu);
        mainMenu.add(editMenu);
        mainMenu.add(viewMenu);
        
        mainMenu.display("");

        // ===== EXAMPLE 4: GRAPHICS SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 4: GRAPHICS SYSTEM =====\n");
        
        GraphicGroup drawing = new GraphicGroup("My Drawing");
        
        drawing.add(new Circle("Sun", 50));
        drawing.add(new Rectangle("House", 100, 80));
        
        GraphicGroup tree = new GraphicGroup("Tree");
        tree.add(new Rectangle("Trunk", 20, 60));
        tree.add(new Circle("Leaves", 40));
        drawing.add(tree);
        
        drawing.draw("");

        // ===== EXAMPLE 5: PRODUCT CATALOG =====
        System.out.println("\n\n===== EXAMPLE 5: PRODUCT CATALOG =====\n");
        
        Category store = new Category("Online Store");
        
        Category electronics = new Category("Electronics");
        electronics.add(new Product("Laptop", 999.99, "EL-001"));
        electronics.add(new Product("Mouse", 29.99, "EL-002"));
        
        Category phones = new Category("Phones");
        phones.add(new Product("iPhone", 899.99, "PH-001"));
        phones.add(new Product("Samsung Galaxy", 799.99, "PH-002"));
        electronics.add(phones);
        
        Category clothing = new Category("Clothing");
        clothing.add(new Product("T-Shirt", 19.99, "CL-001"));
        clothing.add(new Product("Jeans", 49.99, "CL-002"));
        clothing.add(new Product("Jacket", 89.99, "CL-003"));
        
        store.add(electronics);
        store.add(clothing);
        
        store.display("");

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
