import java.util.*;

/**
 * INTERVIEW-READY Proximity Service
 * Time to complete: 45-60 minutes
 * Focus: Geospatial search, distance calculation
 */

// ==================== Location ====================
class Location {
    private final String placeId;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final String category;

    public Location(String placeId, String name, double latitude, 
                   double longitude, String category) {
        this.placeId = placeId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
    }

    // Haversine formula for distance calculation
    public double distanceTo(double lat, double lon) {
        double R = 6371; // Earth radius in km
        
        double dLat = Math.toRadians(lat - latitude);
        double dLon = Math.toRadians(lon - longitude);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(lat)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    public String getPlaceId() { return placeId; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getCategory() { return category; }

    @Override
    public String toString() {
        return name + " (" + category + ") @ [" + latitude + ", " + longitude + "]";
    }
}

// ==================== Search Result ====================
class SearchResult {
    private final Location location;
    private final double distance;

    public SearchResult(Location location, double distance) {
        this.location = location;
        this.distance = distance;
    }

    public Location getLocation() { return location; }
    public double getDistance() { return distance; }

    @Override
    public String toString() {
        return location.getName() + " - " + 
               String.format("%.2f km", distance);
    }
}

// ==================== Proximity Service ====================
class ProximityService {
    private final List<Location> locations;

    public ProximityService() {
        this.locations = new ArrayList<>();
    }

    public void addLocation(Location location) {
        locations.add(location);
        System.out.println("✓ Added location: " + location);
    }

    public List<SearchResult> findNearby(double latitude, double longitude, 
                                        double radiusKm, String category) {
        List<SearchResult> results = new ArrayList<>();

        for (Location location : locations) {
            // Filter by category if specified
            if (category != null && !location.getCategory().equals(category)) {
                continue;
            }

            double distance = location.distanceTo(latitude, longitude);
            
            if (distance <= radiusKm) {
                results.add(new SearchResult(location, distance));
            }
        }

        // Sort by distance (nearest first)
        results.sort(Comparator.comparingDouble(SearchResult::getDistance));

        return results;
    }

    public List<SearchResult> findNearest(double latitude, double longitude, 
                                         int limit, String category) {
        List<SearchResult> allResults = new ArrayList<>();

        for (Location location : locations) {
            // Filter by category if specified
            if (category != null && !location.getCategory().equals(category)) {
                continue;
            }

            double distance = location.distanceTo(latitude, longitude);
            allResults.add(new SearchResult(location, distance));
        }

        // Sort by distance
        allResults.sort(Comparator.comparingDouble(SearchResult::getDistance));

        // Return top N
        return allResults.subList(0, Math.min(limit, allResults.size()));
    }

    public void displayLocations() {
        System.out.println("\n=== All Locations ===");
        System.out.println("Total: " + locations.size());
        for (Location loc : locations) {
            System.out.println("  " + loc);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class ProximityServiceInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Proximity Service Demo ===\n");

        ProximityService service = new ProximityService();

        // Add locations (San Francisco area)
        service.addLocation(new Location("L1", "Starbucks Downtown", 37.7749, -122.4194, "Coffee"));
        service.addLocation(new Location("L2", "Blue Bottle Coffee", 37.7751, -122.4185, "Coffee"));
        service.addLocation(new Location("L3", "Whole Foods Market", 37.7850, -122.4080, "Grocery"));
        service.addLocation(new Location("L4", "CVS Pharmacy", 37.7650, -122.4200, "Pharmacy"));
        service.addLocation(new Location("L5", "Peet's Coffee", 37.7755, -122.4175, "Coffee"));
        service.addLocation(new Location("L6", "Safeway", 37.7900, -122.4100, "Grocery"));

        service.displayLocations();

        // User location
        double userLat = 37.7750;
        double userLon = -122.4190;
        
        System.out.println("User location: [" + userLat + ", " + userLon + "]");

        // Test 1: Find all within 2km radius
        System.out.println("\n--- Test 1: Find All Within 2km ---");
        List<SearchResult> nearby = service.findNearby(userLat, userLon, 2.0, null);
        
        System.out.println("Found " + nearby.size() + " places:");
        for (SearchResult result : nearby) {
            System.out.println("  " + result);
        }

        // Test 2: Find 3 nearest coffee shops
        System.out.println("\n--- Test 2: Find 3 Nearest Coffee Shops ---");
        List<SearchResult> nearestCoffee = service.findNearest(userLat, userLon, 3, "Coffee");
        
        System.out.println("Nearest coffee shops:");
        for (int i = 0; i < nearestCoffee.size(); i++) {
            System.out.println((i + 1) + ". " + nearestCoffee.get(i));
        }

        // Test 3: Find nearest grocery stores
        System.out.println("\n--- Test 3: Find Nearest Grocery Stores ---");
        List<SearchResult> nearestGrocery = service.findNearest(userLat, userLon, 2, "Grocery");
        
        System.out.println("Nearest grocery stores:");
        for (int i = 0; i < nearestGrocery.size(); i++) {
            System.out.println((i + 1) + ". " + nearestGrocery.get(i));
        }

        System.out.println("\n✅ Demo complete!");
    }
}
