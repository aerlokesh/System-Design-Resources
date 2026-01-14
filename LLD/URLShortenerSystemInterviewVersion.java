import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * INTERVIEW-READY URL Shortener System
 * Time to complete: 35-45 minutes
 * Focus: Base62 encoding, collision handling
 */

// ==================== Base62 Encoder ====================
class Base62Encoder {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(long number) {
        if (number == 0) return "0";

        StringBuilder result = new StringBuilder();
        while (number > 0) {
            int remainder = (int)(number % 62);
            result.append(BASE62.charAt(remainder));
            number = number / 62;
        }

        return result.reverse().toString();
    }
}

// ==================== URL Mapping ====================
class URLMapping {
    private final String shortCode;
    private final String longURL;
    private final long createdAt;
    private int clickCount;

    public URLMapping(String shortCode, String longURL) {
        this.shortCode = shortCode;
        this.longURL = longURL;
        this.createdAt = System.currentTimeMillis();
        this.clickCount = 0;
    }

    public void incrementClicks() {
        clickCount++;
    }

    public String getShortCode() { return shortCode; }
    public String getLongURL() { return longURL; }
    public int getClickCount() { return clickCount; }

    @Override
    public String toString() {
        return shortCode + " → " + longURL + " (clicks: " + clickCount + ")";
    }
}

// ==================== URL Shortener Service ====================
class URLShortenerService {
    private final Map<String, URLMapping> shortToLong;
    private final Map<String, String> longToShort;
    private final AtomicLong counter;
    private final String baseURL;

    public URLShortenerService(String baseURL) {
        this.shortToLong = new HashMap<>();
        this.longToShort = new HashMap<>();
        this.counter = new AtomicLong(1000);  // Start from 1000 for better looking codes
        this.baseURL = baseURL;
    }

    public String shortenURL(String longURL) {
        // Check if already shortened
        if (longToShort.containsKey(longURL)) {
            String existing = longToShort.get(longURL);
            System.out.println("✓ Already exists: " + baseURL + existing);
            return baseURL + existing;
        }

        // Generate short code
        long id = counter.getAndIncrement();
        String shortCode = Base62Encoder.encode(id);

        // Store mapping
        URLMapping mapping = new URLMapping(shortCode, longURL);
        shortToLong.put(shortCode, mapping);
        longToShort.put(longURL, shortCode);

        System.out.println("✓ Created: " + baseURL + shortCode + " → " + longURL);
        return baseURL + shortCode;
    }

    public String expandURL(String shortCode) {
        URLMapping mapping = shortToLong.get(shortCode);
        
        if (mapping == null) {
            System.out.println("✗ Not found: " + shortCode);
            return null;
        }

        mapping.incrementClicks();
        System.out.println("→ Redirect: " + shortCode + " → " + mapping.getLongURL());
        return mapping.getLongURL();
    }

    public void displayStats(String shortCode) {
        URLMapping mapping = shortToLong.get(shortCode);
        
        if (mapping == null) {
            System.out.println("✗ Not found: " + shortCode);
            return;
        }

        System.out.println("\n=== Stats for " + shortCode + " ===");
        System.out.println("Long URL: " + mapping.getLongURL());
        System.out.println("Clicks: " + mapping.getClickCount());
        System.out.println();
    }

    public void displayAllMappings() {
        System.out.println("\n=== All URL Mappings ===");
        System.out.println("Total URLs: " + shortToLong.size());
        for (URLMapping mapping : shortToLong.values()) {
            System.out.println("  " + mapping);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class URLShortenerSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== URL Shortener Demo ===\n");

        URLShortenerService service = new URLShortenerService("https://short.ly/");

        // Shorten URLs
        System.out.println("--- Shortening URLs ---");
        String short1 = service.shortenURL("https://www.example.com/very/long/url/path/to/resource");
        String short2 = service.shortenURL("https://www.google.com/search?q=system+design");
        String short3 = service.shortenURL("https://www.github.com/user/repository/issues/123");

        // Try duplicate
        String short4 = service.shortenURL("https://www.example.com/very/long/url/path/to/resource");

        service.displayAllMappings();

        // Expand URLs (simulate clicks)
        System.out.println("--- Expanding URLs (Redirects) ---");
        service.expandURL("g8");
        service.expandURL("g8");
        service.expandURL("g8");
        service.expandURL("g9");

        // Show stats
        service.displayStats("g8");

        System.out.println("✅ Demo complete!");
    }
}
