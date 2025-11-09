import java.util.*;
import java.time.*;

// ==================== OBSERVER PATTERN ====================
// Definition: Defines a one-to-many dependency between objects so that when one object 
// changes state, all its dependents are notified and updated automatically.
//
// Real-World Examples:
// 1. Email Notifications - Subscribe to newsletters, updates
// 2. Stock Market - Price changes notify traders
// 3. Weather Station - Temperature changes notify displays
// 4. Social Media - Post notifications to followers
// 5. Event Management - Event updates notify attendees
//
// When to Use:
// - When a change to one object requires changing others
// - When an object should notify other objects without knowing who they are
// - When you need loose coupling between objects
//
// Benefits:
// 1. Loose coupling - Subject and observers are loosely coupled
// 2. Open/Closed Principle - Add new observers without modifying subject
// 3. Dynamic relationships - Runtime subscription/unsubscription
// 4. Broadcast communication - One-to-many notification

// ==================== EXAMPLE 1: EMAIL NOTIFICATION SYSTEM ====================

interface EmailObserver {
    void onNewEmail(String subject, String sender);
    String getEmail();
}

class EmailAccount implements EmailObserver {
    private String email;
    private List<String> notifications;

    public EmailAccount(String email) {
        this.email = email;
        this.notifications = new ArrayList<>();
    }

    @Override
    public void onNewEmail(String subject, String sender) {
        String notification = "New email from " + sender + ": " + subject;
        notifications.add(notification);
        System.out.println("📧 " + email + " received: " + notification);
    }

    @Override
    public String getEmail() {
        return email;
    }

    public List<String> getNotifications() {
        return notifications;
    }
}

class EmailServer {
    private List<EmailObserver> subscribers;

    public EmailServer() {
        this.subscribers = new ArrayList<>();
    }

    public void subscribe(EmailObserver observer) {
        subscribers.add(observer);
        System.out.println("✅ " + observer.getEmail() + " subscribed to email notifications");
    }

    public void unsubscribe(EmailObserver observer) {
        subscribers.remove(observer);
        System.out.println("❌ " + observer.getEmail() + " unsubscribed");
    }

    public void sendEmail(String subject, String sender) {
        System.out.println("\n--- Email Server: Broadcasting new email ---");
        for (EmailObserver observer : subscribers) {
            observer.onNewEmail(subject, sender);
        }
    }
}

// ==================== EXAMPLE 2: STOCK MARKET SYSTEM ====================

interface StockObserver {
    void onPriceUpdate(String symbol, double oldPrice, double newPrice);
    String getName();
}

class Investor implements StockObserver {
    private String name;
    private double buyThreshold;
    private double sellThreshold;

    public Investor(String name, double buyThreshold, double sellThreshold) {
        this.name = name;
        this.buyThreshold = buyThreshold;
        this.sellThreshold = sellThreshold;
    }

    @Override
    public void onPriceUpdate(String symbol, double oldPrice, double newPrice) {
        double change = ((newPrice - oldPrice) / oldPrice) * 100;
        System.out.printf("📊 %s notified: %s %.2f -> %.2f (%.2f%%)%n", 
            name, symbol, oldPrice, newPrice, change);

        if (newPrice <= buyThreshold) {
            System.out.println("   🟢 " + name + " considers BUYING " + symbol);
        } else if (newPrice >= sellThreshold) {
            System.out.println("   🔴 " + name + " considers SELLING " + symbol);
        }
    }

    @Override
    public String getName() {
        return name;
    }
}

class StockExchange {
    private String symbol;
    private double currentPrice;
    private List<StockObserver> observers;
    private List<Double> priceHistory;

    public StockExchange(String symbol, double initialPrice) {
        this.symbol = symbol;
        this.currentPrice = initialPrice;
        this.observers = new ArrayList<>();
        this.priceHistory = new ArrayList<>();
        priceHistory.add(initialPrice);
    }

    public void addObserver(StockObserver observer) {
        observers.add(observer);
        System.out.println("✅ " + observer.getName() + " watching " + symbol);
    }

    public void removeObserver(StockObserver observer) {
        observers.remove(observer);
        System.out.println("❌ " + observer.getName() + " stopped watching " + symbol);
    }

    public void setPrice(double newPrice) {
        System.out.println("\n--- Stock Exchange: " + symbol + " price update ---");
        double oldPrice = this.currentPrice;
        this.currentPrice = newPrice;
        priceHistory.add(newPrice);

        notifyObservers(oldPrice, newPrice);
    }

    private void notifyObservers(double oldPrice, double newPrice) {
        for (StockObserver observer : observers) {
            observer.onPriceUpdate(symbol, oldPrice, newPrice);
        }
    }

    public String getSymbol() { return symbol; }
    public double getCurrentPrice() { return currentPrice; }
}

// ==================== EXAMPLE 3: WEATHER STATION ====================

interface WeatherObserver {
    void onWeatherUpdate(float temperature, float humidity, float pressure);
    String getDisplayName();
}

class CurrentConditionsDisplay implements WeatherObserver {
    private String name;
    private float temperature;
    private float humidity;

    public CurrentConditionsDisplay(String name) {
        this.name = name;
    }

    @Override
    public void onWeatherUpdate(float temperature, float humidity, float pressure) {
        this.temperature = temperature;
        this.humidity = humidity;
        display();
    }

    private void display() {
        System.out.printf("🌡️  %s - Current: %.1f°C, %.1f%% humidity%n", 
            name, temperature, humidity);
    }

    @Override
    public String getDisplayName() {
        return name;
    }
}

class StatisticsDisplay implements WeatherObserver {
    private String name;
    private List<Float> temperatureHistory;
    private float maxTemp = Float.MIN_VALUE;
    private float minTemp = Float.MAX_VALUE;
    private float avgTemp;

    public StatisticsDisplay(String name) {
        this.name = name;
        this.temperatureHistory = new ArrayList<>();
    }

    @Override
    public void onWeatherUpdate(float temperature, float humidity, float pressure) {
        temperatureHistory.add(temperature);
        
        if (temperature > maxTemp) maxTemp = temperature;
        if (temperature < minTemp) minTemp = temperature;
        
        avgTemp = (float) temperatureHistory.stream()
            .mapToDouble(Float::doubleValue)
            .average()
            .orElse(0.0);
        
        display();
    }

    private void display() {
        System.out.printf("📈 %s - Stats: Avg: %.1f°C, Max: %.1f°C, Min: %.1f°C%n",
            name, avgTemp, maxTemp, minTemp);
    }

    @Override
    public String getDisplayName() {
        return name;
    }
}

class ForecastDisplay implements WeatherObserver {
    private String name;
    private float lastPressure;
    private float currentPressure;

    public ForecastDisplay(String name) {
        this.name = name;
    }

    @Override
    public void onWeatherUpdate(float temperature, float humidity, float pressure) {
        lastPressure = currentPressure;
        currentPressure = pressure;
        display();
    }

    private void display() {
        System.out.print("🌤️  " + name + " - Forecast: ");
        
        if (currentPressure > lastPressure) {
            System.out.println("Improving weather!");
        } else if (currentPressure < lastPressure) {
            System.out.println("Watch out for cooler, rainy weather");
        } else {
            System.out.println("More of the same");
        }
    }

    @Override
    public String getDisplayName() {
        return name;
    }
}

class WeatherStation {
    private List<WeatherObserver> observers;
    private float temperature;
    private float humidity;
    private float pressure;

    public WeatherStation() {
        this.observers = new ArrayList<>();
    }

    public void registerObserver(WeatherObserver observer) {
        observers.add(observer);
        System.out.println("✅ " + observer.getDisplayName() + " registered");
    }

    public void removeObserver(WeatherObserver observer) {
        observers.remove(observer);
        System.out.println("❌ " + observer.getDisplayName() + " removed");
    }

    public void setMeasurements(float temperature, float humidity, float pressure) {
        System.out.println("\n--- Weather Station: Measurements updated ---");
        this.temperature = temperature;
        this.humidity = humidity;
        this.pressure = pressure;
        notifyObservers();
    }

    private void notifyObservers() {
        for (WeatherObserver observer : observers) {
            observer.onWeatherUpdate(temperature, humidity, pressure);
        }
    }
}

// ==================== EXAMPLE 4: SOCIAL MEDIA NOTIFICATIONS ====================

interface SocialMediaObserver {
    void onPostPublished(String author, String content);
    void onCommentAdded(String postId, String commenter, String comment);
    String getUsername();
}

class Follower implements SocialMediaObserver {
    private String username;
    private List<String> notifications;

    public Follower(String username) {
        this.username = username;
        this.notifications = new ArrayList<>();
    }

    @Override
    public void onPostPublished(String author, String content) {
        String notification = author + " posted: " + content;
        notifications.add(notification);
        System.out.println("👤 " + username + " sees: " + notification);
    }

    @Override
    public void onCommentAdded(String postId, String commenter, String comment) {
        String notification = commenter + " commented: " + comment;
        notifications.add(notification);
        System.out.println("💬 " + username + " sees: " + notification);
    }

    @Override
    public String getUsername() {
        return username;
    }

    public List<String> getNotifications() {
        return notifications;
    }
}

class SocialMediaUser {
    private String username;
    private List<SocialMediaObserver> followers;
    private List<String> posts;

    public SocialMediaUser(String username) {
        this.username = username;
        this.followers = new ArrayList<>();
        this.posts = new ArrayList<>();
    }

    public void addFollower(SocialMediaObserver follower) {
        followers.add(follower);
        System.out.println("✅ " + follower.getUsername() + " is now following " + username);
    }

    public void removeFollower(SocialMediaObserver follower) {
        followers.remove(follower);
        System.out.println("❌ " + follower.getUsername() + " unfollowed " + username);
    }

    public void publishPost(String content) {
        System.out.println("\n--- " + username + " publishing post ---");
        posts.add(content);
        notifyFollowersAboutPost(content);
    }

    public void addComment(String postId, String comment) {
        System.out.println("\n--- Comment on post " + postId + " ---");
        notifyFollowersAboutComment(postId, comment);
    }

    private void notifyFollowersAboutPost(String content) {
        for (SocialMediaObserver follower : followers) {
            follower.onPostPublished(username, content);
        }
    }

    private void notifyFollowersAboutComment(String postId, String comment) {
        for (SocialMediaObserver follower : followers) {
            follower.onCommentAdded(postId, username, comment);
        }
    }

    public String getUsername() {
        return username;
    }
}

// ==================== EXAMPLE 5: EVENT MANAGEMENT SYSTEM ====================

interface EventObserver {
    void onEventCreated(String eventName, LocalDateTime dateTime);
    void onEventUpdated(String eventName, String updateMessage);
    void onEventCancelled(String eventName, String reason);
    String getAttendeeName();
}

class Attendee implements EventObserver {
    private String name;
    private String email;
    private List<String> notifications;

    public Attendee(String name, String email) {
        this.name = name;
        this.email = email;
        this.notifications = new ArrayList<>();
    }

    @Override
    public void onEventCreated(String eventName, LocalDateTime dateTime) {
        String notification = "Event created: " + eventName + " at " + dateTime;
        notifications.add(notification);
        System.out.println("📅 " + name + " (" + email + "): " + notification);
    }

    @Override
    public void onEventUpdated(String eventName, String updateMessage) {
        String notification = "Event updated: " + eventName + " - " + updateMessage;
        notifications.add(notification);
        System.out.println("🔔 " + name + " (" + email + "): " + notification);
    }

    @Override
    public void onEventCancelled(String eventName, String reason) {
        String notification = "Event cancelled: " + eventName + " - " + reason;
        notifications.add(notification);
        System.out.println("❌ " + name + " (" + email + "): " + notification);
    }

    @Override
    public String getAttendeeName() {
        return name;
    }
}

class Event {
    private String eventName;
    private LocalDateTime dateTime;
    private String location;
    private List<EventObserver> attendees;

    public Event(String eventName, LocalDateTime dateTime, String location) {
        this.eventName = eventName;
        this.dateTime = dateTime;
        this.location = location;
        this.attendees = new ArrayList<>();
    }

    public void registerAttendee(EventObserver attendee) {
        attendees.add(attendee);
        System.out.println("✅ " + attendee.getAttendeeName() + " registered for " + eventName);
        attendee.onEventCreated(eventName, dateTime);
    }

    public void unregisterAttendee(EventObserver attendee) {
        attendees.remove(attendee);
        System.out.println("❌ " + attendee.getAttendeeName() + " unregistered from " + eventName);
    }

    public void updateEvent(String updateMessage) {
        System.out.println("\n--- Event Update: " + eventName + " ---");
        for (EventObserver attendee : attendees) {
            attendee.onEventUpdated(eventName, updateMessage);
        }
    }

    public void cancelEvent(String reason) {
        System.out.println("\n--- Event Cancelled: " + eventName + " ---");
        for (EventObserver attendee : attendees) {
            attendee.onEventCancelled(eventName, reason);
        }
    }

    public String getEventName() {
        return eventName;
    }
}

// ==================== DEMO ====================

public class ObserverPattern {
    public static void main(String[] args) {
        System.out.println("========== OBSERVER PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: EMAIL NOTIFICATIONS =====
        System.out.println("===== EXAMPLE 1: EMAIL NOTIFICATION SYSTEM =====");
        EmailServer emailServer = new EmailServer();
        
        EmailAccount alice = new EmailAccount("alice@example.com");
        EmailAccount bob = new EmailAccount("bob@example.com");
        
        emailServer.subscribe(alice);
        emailServer.subscribe(bob);
        
        emailServer.sendEmail("Meeting Tomorrow", "manager@company.com");
        emailServer.sendEmail("Project Update", "team@company.com");
        
        emailServer.unsubscribe(bob);
        emailServer.sendEmail("Important Notice", "hr@company.com");

        // ===== EXAMPLE 2: STOCK MARKET =====
        System.out.println("\n\n===== EXAMPLE 2: STOCK MARKET SYSTEM =====");
        StockExchange apple = new StockExchange("AAPL", 150.00);
        
        Investor investor1 = new Investor("Warren", 140.00, 160.00);
        Investor investor2 = new Investor("Charlie", 145.00, 155.00);
        
        apple.addObserver(investor1);
        apple.addObserver(investor2);
        
        apple.setPrice(152.50);
        apple.setPrice(138.75);  // Below buy threshold
        apple.setPrice(161.25);  // Above sell threshold

        // ===== EXAMPLE 3: WEATHER STATION =====
        System.out.println("\n\n===== EXAMPLE 3: WEATHER STATION =====");
        WeatherStation weatherStation = new WeatherStation();
        
        CurrentConditionsDisplay currentDisplay = new CurrentConditionsDisplay("Main Display");
        StatisticsDisplay statsDisplay = new StatisticsDisplay("Stats Display");
        ForecastDisplay forecastDisplay = new ForecastDisplay("Forecast Display");
        
        weatherStation.registerObserver(currentDisplay);
        weatherStation.registerObserver(statsDisplay);
        weatherStation.registerObserver(forecastDisplay);
        
        weatherStation.setMeasurements(25.0f, 65.0f, 1013.0f);
        weatherStation.setMeasurements(27.0f, 70.0f, 1012.0f);
        weatherStation.setMeasurements(23.0f, 90.0f, 1010.0f);

        // ===== EXAMPLE 4: SOCIAL MEDIA =====
        System.out.println("\n\n===== EXAMPLE 4: SOCIAL MEDIA NOTIFICATIONS =====");
        SocialMediaUser influencer = new SocialMediaUser("TechGuru");
        
        Follower follower1 = new Follower("Alice");
        Follower follower2 = new Follower("Bob");
        Follower follower3 = new Follower("Charlie");
        
        influencer.addFollower(follower1);
        influencer.addFollower(follower2);
        influencer.addFollower(follower3);
        
        influencer.publishPost("Check out my latest tech review!");
        influencer.addComment("POST-001", "Thanks for all the feedback!");
        
        influencer.removeFollower(follower2);
        influencer.publishPost("New product launch tomorrow!");

        // ===== EXAMPLE 5: EVENT MANAGEMENT =====
        System.out.println("\n\n===== EXAMPLE 5: EVENT MANAGEMENT SYSTEM =====");
        Event conference = new Event(
            "Tech Conference 2025",
            LocalDateTime.of(2025, 6, 15, 9, 0),
            "Convention Center"
        );
        
        Attendee attendee1 = new Attendee("David", "david@email.com");
        Attendee attendee2 = new Attendee("Emma", "emma@email.com");
        
        conference.registerAttendee(attendee1);
        conference.registerAttendee(attendee2);
        
        conference.updateEvent("Venue changed to Grand Hall");
        conference.updateEvent("New speaker added: Elon Musk");

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
