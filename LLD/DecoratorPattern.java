import java.util.*;

// ==================== DECORATOR PATTERN ====================
// Definition: Attaches additional responsibilities to an object dynamically.
// Decorators provide a flexible alternative to subclassing for extending functionality.
//
// Real-World Examples:
// 1. Coffee Shop - Add milk, sugar, whipped cream to coffee
// 2. Text Formatting - Add bold, italic, underline to text
// 3. Stream I/O - BufferedInputStream wrapping FileInputStream
// 4. Pizza Toppings - Add cheese, pepperoni, mushrooms to pizza
// 5. Notification System - Add email, SMS, push to base notification
//
// When to Use:
// - Add responsibilities to individual objects dynamically
// - Extend functionality without affecting other objects
// - Withdrawal of responsibilities should be possible
// - Extension by subclassing is impractical
//
// Benefits:
// 1. More flexible than inheritance
// 2. Avoids feature-laden classes high in hierarchy
// 3. Adds responsibilities at runtime
// 4. Easy to add new decorators

// ==================== EXAMPLE 1: COFFEE SHOP ====================

interface Coffee {
    String getDescription();
    double getCost();
}

class SimpleCoffee implements Coffee {
    @Override
    public String getDescription() {
        return "Simple Coffee";
    }

    @Override
    public double getCost() {
        return 2.00;
    }
}

// Base decorator
abstract class CoffeeDecorator implements Coffee {
    protected Coffee decoratedCoffee;

    public CoffeeDecorator(Coffee coffee) {
        this.decoratedCoffee = coffee;
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription();
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost();
    }
}

class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Milk";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 0.50;
    }
}

class SugarDecorator extends CoffeeDecorator {
    public SugarDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Sugar";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 0.25;
    }
}

class WhippedCreamDecorator extends CoffeeDecorator {
    public WhippedCreamDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Whipped Cream";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 0.75;
    }
}

class CaramelDecorator extends CoffeeDecorator {
    public CaramelDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Caramel";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 0.60;
    }
}

// ==================== EXAMPLE 2: TEXT FORMATTING ====================

interface Text {
    String getContent();
}

class PlainText implements Text {
    private String content;

    public PlainText(String content) {
        this.content = content;
    }

    @Override
    public String getContent() {
        return content;
    }
}

abstract class TextDecorator implements Text {
    protected Text decoratedText;

    public TextDecorator(Text text) {
        this.decoratedText = text;
    }

    @Override
    public String getContent() {
        return decoratedText.getContent();
    }
}

class BoldDecorator extends TextDecorator {
    public BoldDecorator(Text text) {
        super(text);
    }

    @Override
    public String getContent() {
        return "<b>" + decoratedText.getContent() + "</b>";
    }
}

class ItalicDecorator extends TextDecorator {
    public ItalicDecorator(Text text) {
        super(text);
    }

    @Override
    public String getContent() {
        return "<i>" + decoratedText.getContent() + "</i>";
    }
}

class UnderlineDecorator extends TextDecorator {
    public UnderlineDecorator(Text text) {
        super(text);
    }

    @Override
    public String getContent() {
        return "<u>" + decoratedText.getContent() + "</u>";
    }
}

class ColorDecorator extends TextDecorator {
    private String color;

    public ColorDecorator(Text text, String color) {
        super(text);
        this.color = color;
    }

    @Override
    public String getContent() {
        return "<span color='" + color + "'>" + decoratedText.getContent() + "</span>";
    }
}

// ==================== EXAMPLE 3: PIZZA TOPPINGS ====================

interface Pizza {
    String getDescription();
    double getPrice();
}

class MargheritaPizza implements Pizza {
    @Override
    public String getDescription() {
        return "Margherita Pizza";
    }

    @Override
    public double getPrice() {
        return 8.00;
    }
}

class VeggiePizza implements Pizza {
    @Override
    public String getDescription() {
        return "Veggie Pizza";
    }

    @Override
    public double getPrice() {
        return 9.00;
    }
}

abstract class PizzaDecorator implements Pizza {
    protected Pizza decoratedPizza;

    public PizzaDecorator(Pizza pizza) {
        this.decoratedPizza = pizza;
    }

    @Override
    public String getDescription() {
        return decoratedPizza.getDescription();
    }

    @Override
    public double getPrice() {
        return decoratedPizza.getPrice();
    }
}

class CheeseDecorator extends PizzaDecorator {
    public CheeseDecorator(Pizza pizza) {
        super(pizza);
    }

    @Override
    public String getDescription() {
        return decoratedPizza.getDescription() + " + Extra Cheese";
    }

    @Override
    public double getPrice() {
        return decoratedPizza.getPrice() + 1.50;
    }
}

class PepperoniDecorator extends PizzaDecorator {
    public PepperoniDecorator(Pizza pizza) {
        super(pizza);
    }

    @Override
    public String getDescription() {
        return decoratedPizza.getDescription() + " + Pepperoni";
    }

    @Override
    public double getPrice() {
        return decoratedPizza.getPrice() + 2.00;
    }
}

class MushroomDecorator extends PizzaDecorator {
    public MushroomDecorator(Pizza pizza) {
        super(pizza);
    }

    @Override
    public String getDescription() {
        return decoratedPizza.getDescription() + " + Mushrooms";
    }

    @Override
    public double getPrice() {
        return decoratedPizza.getPrice() + 1.25;
    }
}

class OlivesDecorator extends PizzaDecorator {
    public OlivesDecorator(Pizza pizza) {
        super(pizza);
    }

    @Override
    public String getDescription() {
        return decoratedPizza.getDescription() + " + Olives";
    }

    @Override
    public double getPrice() {
        return decoratedPizza.getPrice() + 1.00;
    }
}

// ==================== EXAMPLE 4: NOTIFICATION SYSTEM ====================

interface Notification {
    void send(String message);
}

class BasicNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("📢 Basic Notification: " + message);
    }
}

abstract class NotificationDecorator implements Notification {
    protected Notification decoratedNotification;

    public NotificationDecorator(Notification notification) {
        this.decoratedNotification = notification;
    }

    @Override
    public void send(String message) {
        decoratedNotification.send(message);
    }
}

class EmailNotificationDecorator extends NotificationDecorator {
    public EmailNotificationDecorator(Notification notification) {
        super(notification);
    }

    @Override
    public void send(String message) {
        decoratedNotification.send(message);
        sendEmail(message);
    }

    private void sendEmail(String message) {
        System.out.println("📧 Email sent: " + message);
    }
}

class SMSNotificationDecorator extends NotificationDecorator {
    public SMSNotificationDecorator(Notification notification) {
        super(notification);
    }

    @Override
    public void send(String message) {
        decoratedNotification.send(message);
        sendSMS(message);
    }

    private void sendSMS(String message) {
        System.out.println("📱 SMS sent: " + message);
    }
}

class PushNotificationDecorator extends NotificationDecorator {
    public PushNotificationDecorator(Notification notification) {
        super(notification);
    }

    @Override
    public void send(String message) {
        decoratedNotification.send(message);
        sendPushNotification(message);
    }

    private void sendPushNotification(String message) {
        System.out.println("🔔 Push notification sent: " + message);
    }
}

// ==================== EXAMPLE 5: CAR FEATURES ====================

interface Car {
    String getDescription();
    double getPrice();
}

class BasicCar implements Car {
    @Override
    public String getDescription() {
        return "Basic Car";
    }

    @Override
    public double getPrice() {
        return 20000.00;
    }
}

abstract class CarDecorator implements Car {
    protected Car decoratedCar;

    public CarDecorator(Car car) {
        this.decoratedCar = car;
    }

    @Override
    public String getDescription() {
        return decoratedCar.getDescription();
    }

    @Override
    public double getPrice() {
        return decoratedCar.getPrice();
    }
}

class SunroofDecorator extends CarDecorator {
    public SunroofDecorator(Car car) {
        super(car);
    }

    @Override
    public String getDescription() {
        return decoratedCar.getDescription() + " + Sunroof";
    }

    @Override
    public double getPrice() {
        return decoratedCar.getPrice() + 2000.00;
    }
}

class LeatherSeatsDecorator extends CarDecorator {
    public LeatherSeatsDecorator(Car car) {
        super(car);
    }

    @Override
    public String getDescription() {
        return decoratedCar.getDescription() + " + Leather Seats";
    }

    @Override
    public double getPrice() {
        return decoratedCar.getPrice() + 1500.00;
    }
}

class NavigationSystemDecorator extends CarDecorator {
    public NavigationSystemDecorator(Car car) {
        super(car);
    }

    @Override
    public String getDescription() {
        return decoratedCar.getDescription() + " + Navigation System";
    }

    @Override
    public double getPrice() {
        return decoratedCar.getPrice() + 1000.00;
    }
}

class PremiumSoundSystemDecorator extends CarDecorator {
    public PremiumSoundSystemDecorator(Car car) {
        super(car);
    }

    @Override
    public String getDescription() {
        return decoratedCar.getDescription() + " + Premium Sound System";
    }

    @Override
    public double getPrice() {
        return decoratedCar.getPrice() + 800.00;
    }
}

// ==================== DEMO ====================

public class DecoratorPattern {
    public static void main(String[] args) {
        System.out.println("========== DECORATOR PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: COFFEE SHOP =====
        System.out.println("===== EXAMPLE 1: COFFEE SHOP =====\n");
        
        Coffee coffee1 = new SimpleCoffee();
        System.out.printf("☕ %s - $%.2f%n", coffee1.getDescription(), coffee1.getCost());
        
        Coffee coffee2 = new MilkDecorator(new SimpleCoffee());
        System.out.printf("☕ %s - $%.2f%n", coffee2.getDescription(), coffee2.getCost());
        
        Coffee coffee3 = new WhippedCreamDecorator(
                            new CaramelDecorator(
                                new MilkDecorator(
                                    new SimpleCoffee())));
        System.out.printf("☕ %s - $%.2f%n", coffee3.getDescription(), coffee3.getCost());
        
        Coffee coffee4 = new SugarDecorator(
                            new MilkDecorator(
                                new SimpleCoffee()));
        System.out.printf("☕ %s - $%.2f%n", coffee4.getDescription(), coffee4.getCost());

        // ===== EXAMPLE 2: TEXT FORMATTING =====
        System.out.println("\n\n===== EXAMPLE 2: TEXT FORMATTING =====\n");
        
        Text text1 = new PlainText("Hello World");
        System.out.println("Plain: " + text1.getContent());
        
        Text text2 = new BoldDecorator(new PlainText("Hello World"));
        System.out.println("Bold: " + text2.getContent());
        
        Text text3 = new ItalicDecorator(
                        new BoldDecorator(
                            new PlainText("Hello World")));
        System.out.println("Bold + Italic: " + text3.getContent());
        
        Text text4 = new ColorDecorator(
                        new UnderlineDecorator(
                            new ItalicDecorator(
                                new BoldDecorator(
                                    new PlainText("Hello World")))), "red");
        System.out.println("All Formats: " + text4.getContent());

        // ===== EXAMPLE 3: PIZZA TOPPINGS =====
        System.out.println("\n\n===== EXAMPLE 3: PIZZA TOPPINGS =====\n");
        
        Pizza pizza1 = new MargheritaPizza();
        System.out.printf("🍕 %s - $%.2f%n", pizza1.getDescription(), pizza1.getPrice());
        
        Pizza pizza2 = new CheeseDecorator(new MargheritaPizza());
        System.out.printf("🍕 %s - $%.2f%n", pizza2.getDescription(), pizza2.getPrice());
        
        Pizza pizza3 = new PepperoniDecorator(
                            new MushroomDecorator(
                                new CheeseDecorator(
                                    new MargheritaPizza())));
        System.out.printf("🍕 %s - $%.2f%n", pizza3.getDescription(), pizza3.getPrice());
        
        Pizza pizza4 = new OlivesDecorator(
                            new MushroomDecorator(
                                new PepperoniDecorator(
                                    new CheeseDecorator(
                                        new VeggiePizza()))));
        System.out.printf("🍕 %s - $%.2f%n", pizza4.getDescription(), pizza4.getPrice());

        // ===== EXAMPLE 4: NOTIFICATION SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 4: NOTIFICATION SYSTEM =====\n");
        
        System.out.println("Notification 1: Basic only");
        Notification notification1 = new BasicNotification();
        notification1.send("Server is down!");
        
        System.out.println("\nNotification 2: Basic + Email");
        Notification notification2 = new EmailNotificationDecorator(
                                        new BasicNotification());
        notification2.send("Deployment completed");
        
        System.out.println("\nNotification 3: Basic + Email + SMS");
        Notification notification3 = new SMSNotificationDecorator(
                                        new EmailNotificationDecorator(
                                            new BasicNotification()));
        notification3.send("Critical alert!");
        
        System.out.println("\nNotification 4: All channels");
        Notification notification4 = new PushNotificationDecorator(
                                        new SMSNotificationDecorator(
                                            new EmailNotificationDecorator(
                                                new BasicNotification())));
        notification4.send("System maintenance scheduled");

        // ===== EXAMPLE 5: CAR FEATURES =====
        System.out.println("\n\n===== EXAMPLE 5: CAR FEATURES =====\n");
        
        Car car1 = new BasicCar();
        System.out.printf("🚗 %s - $%.2f%n", car1.getDescription(), car1.getPrice());
        
        Car car2 = new SunroofDecorator(new BasicCar());
        System.out.printf("🚗 %s - $%.2f%n", car2.getDescription(), car2.getPrice());
        
        Car car3 = new NavigationSystemDecorator(
                        new LeatherSeatsDecorator(
                            new SunroofDecorator(
                                new BasicCar())));
        System.out.printf("🚗 %s - $%.2f%n", car3.getDescription(), car3.getPrice());
        
        Car car4 = new PremiumSoundSystemDecorator(
                        new NavigationSystemDecorator(
                            new LeatherSeatsDecorator(
                                new SunroofDecorator(
                                    new BasicCar()))));
        System.out.printf("🚗 %s - $%.2f%n", car4.getDescription(), car4.getPrice());

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
