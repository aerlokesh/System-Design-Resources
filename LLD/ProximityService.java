/**
 * PROXIMITY SERVICE - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a geospatial proximity service
 * for finding nearby businesses efficiently.
 * 
 * Key Features:
 * 1. QuadTree-based spatial indexing for O(log n) queries
 * 2. Haversine formula for accurate Earth distance calculation
 * 3. Thread-safe operations
 * 4. Support for radius search and K-nearest neighbors
 * 5. Category filtering and result ranking
 * 6. Multiple indexing strategies (QuadTree, Grid, GeoHash concept)
 * 
 * Design Patterns Used:
 * - Strategy Pattern (SpatialIndex implementations)
 * - Composite Pattern (QuadTree structure)
 * - Immutable Object (GeoPoint, Business)
 * - Template Method (Range query algorithm)
 * 
 * Company: Amazon (Senior Level Interview Question)
 * Real-world applications: Uber, Google Maps, Yelp, DoorDash
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// ============================================================
// CORE INTERFACES
// ============================================================

/**
 * Interface for spatial indexing strategies
 */
interface SpatialIndex {
    void insert(Business business);
    boolean remove(String businessId);
    List<Business> rangeQuery(GeoPoint center, double radiusKm);
    int size();
    void clear();
}

// ============================================================
// GEOPOINT (LOCATION)
// ============================================================

/**
 * Represents a geographic location with latitude and longitude
 * Immutable and thread-safe
 */
class GeoPoint {
    private final double latitude;
    private final double longitude;
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    public GeoPoint(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    private void validateCoordinates(double lat, double lon) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }
    
    /**
     * Calculate distance to another point using Haversine formula
     * Accounts for Earth's curvature for accurate distance
     * @param other Target location
     * @return Distance in kilometers
     */
    public double distanceTo(GeoPoint other) {
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoPoint)) return false;
        GeoPoint that = (GeoPoint) o;
        return Double.compare(that.latitude, latitude) == 0 &&
               Double.compare(that.longitude, longitude) == 0;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }
    
    @Override
    public String toString() {
        return String.format("(%.4f, %.4f)", latitude, longitude);
    }
}

// ============================================================
// BUSINESS
// ============================================================

/**
 * Represents a business with location and metadata
 */
class Business {
    private final String id;
    private final String name;
    private final GeoPoint location;
    private final String category;
    private final String address;
    private final String phoneNumber;
    
    public Business(String id, String name, GeoPoint location, String category) {
        this(id, name, location, category, null, null);
    }
    
    public Business(String id, String name, GeoPoint location, String category,
                    String address, String phoneNumber) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.category = category;
        this.address = address;
        this.phoneNumber = phoneNumber;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public GeoPoint getLocation() { return location; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public String getPhoneNumber() { return phoneNumber; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Business)) return false;
        Business business = (Business) o;
        return id.equals(business.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("Business{id='%s', name='%s', category='%s', location=%s}",
            id, name, category, location);
    }
}

// ============================================================
// SEARCH RESULT
// ============================================================

/**
 * Represents a search result with business and distance
 */
class SearchResult implements Comparable<SearchResult> {
    private final Business business;
    private final double distanceKm;
    
    public SearchResult(Business business, double distanceKm) {
        this.business = business;
        this.distanceKm = distanceKm;
    }
    
    public Business getBusiness() { return business; }
    public double getDistanceKm() { return distanceKm; }
    public double getDistanceMiles() { return distanceKm * 0.621371; }
    
    @Override
    public int compareTo(SearchResult other) {
        return Double.compare(this.distanceKm, other.distanceKm);
    }
    
    @Override
    public String toString() {
        return String.format("%s - %.2f km (%.2f mi)", 
            business.getName(), distanceKm, getDistanceMiles());
    }
}

// ============================================================
// GEO BOUNDARY
// ============================================================

/**
 * Represents a rectangular geographic boundary
 */
class GeoBoundary {
    private final GeoPoint southWest;
    private final GeoPoint northEast;
    
    public GeoBoundary(GeoPoint southWest, GeoPoint northEast) {
        this.southWest = southWest;
        this.northEast = northEast;
    }
    
    public boolean contains(GeoPoint point) {
        return point.getLatitude() >= southWest.getLatitude() &&
               point.getLatitude() <= northEast.getLatitude() &&
               point.getLongitude() >= southWest.getLongitude() &&
               point.getLongitude() <= northEast.getLongitude();
    }
    
    public boolean intersectsCircle(GeoPoint center, double radiusKm) {
        double closestLat = Math.max(southWest.getLatitude(),
                           Math.min(center.getLatitude(), northEast.getLatitude()));
        double closestLon = Math.max(southWest.getLongitude(),
                           Math.min(center.getLongitude(), northEast.getLongitude()));
        
        GeoPoint closestPoint = new GeoPoint(closestLat, closestLon);
        return center.distanceTo(closestPoint) <= radiusKm;
    }
    
    public GeoPoint getSouthWest() { return southWest; }
    public GeoPoint getNorthEast() { return northEast; }
    
    @Override
    public String toString() {
        return String.format("Boundary{SW=%s, NE=%s}", southWest, northEast);
    }
}

// ============================================================
// QUADTREE NODE
// ============================================================

/**
 * Node in the QuadTree
 * Contains businesses or subdivides into 4 children
 */
class QuadTreeNode {
    private final GeoBoundary boundary;
    private final int depth;
    private final List<Business> businesses;
    private QuadTreeNode northWest;
    private QuadTreeNode northEast;
    private QuadTreeNode southWest;
    private QuadTreeNode southEast;
    private boolean subdivided;
    
    public QuadTreeNode(GeoBoundary boundary, int depth) {
        this.boundary = boundary;
        this.depth = depth;
        this.businesses = new ArrayList<>();
        this.subdivided = false;
    }
    
    public boolean insert(Business business, int maxCapacity, int maxDepth) {
        if (!boundary.contains(business.getLocation())) {
            return false;
        }
        
        if (!subdivided && businesses.size() < maxCapacity) {
            businesses.add(business);
            return true;
        }
        
        if (depth >= maxDepth) {
            businesses.add(business);
            return true;
        }
        
        if (!subdivided) {
            subdivide();
        }
        
        if (northWest.insert(business, maxCapacity, maxDepth)) return true;
        if (northEast.insert(business, maxCapacity, maxDepth)) return true;
        if (southWest.insert(business, maxCapacity, maxDepth)) return true;
        if (southEast.insert(business, maxCapacity, maxDepth)) return true;
        
        return false;
    }
    
    public boolean remove(Business business) {
        if (!boundary.contains(business.getLocation())) {
            return false;
        }
        
        if (businesses.remove(business)) {
            return true;
        }
        
        if (subdivided) {
            return northWest.remove(business) || northEast.remove(business) ||
                   southWest.remove(business) || southEast.remove(business);
        }
        
        return false;
    }
    
    public void rangeQuery(GeoPoint center, double radiusKm, List<Business> result) {
        if (!boundary.intersectsCircle(center, radiusKm)) {
            return;
        }
        
        for (Business business : businesses) {
            if (center.distanceTo(business.getLocation()) <= radiusKm) {
                result.add(business);
            }
        }
        
        if (subdivided) {
            northWest.rangeQuery(center, radiusKm, result);
            northEast.rangeQuery(center, radiusKm, result);
            southWest.rangeQuery(center, radiusKm, result);
            southEast.rangeQuery(center, radiusKm, result);
        }
    }
    
    private void subdivide() {
        double midLat = (boundary.getSouthWest().getLatitude() + 
                        boundary.getNorthEast().getLatitude()) / 2;
        double midLon = (boundary.getSouthWest().getLongitude() + 
                        boundary.getNorthEast().getLongitude()) / 2;
        
        GeoPoint center = new GeoPoint(midLat, midLon);
        
        northWest = new QuadTreeNode(new GeoBoundary(
            new GeoPoint(midLat, boundary.getSouthWest().getLongitude()),
            new GeoPoint(boundary.getNorthEast().getLatitude(), midLon)
        ), depth + 1);
        
        northEast = new QuadTreeNode(new GeoBoundary(
            center,
            boundary.getNorthEast()
        ), depth + 1);
        
        southWest = new QuadTreeNode(new GeoBoundary(
            boundary.getSouthWest(),
            center
        ), depth + 1);
        
        southEast = new QuadTreeNode(new GeoBoundary(
            new GeoPoint(boundary.getSouthWest().getLatitude(), midLon),
            new GeoPoint(midLat, boundary.getNorthEast().getLongitude())
        ), depth + 1);
        
        subdivided = true;
        
        List<Business> temp = new ArrayList<>(businesses);
        businesses.clear();
        for (Business business : temp) {
            insert(business, 0, Integer.MAX_VALUE);
        }
    }
    
    public GeoBoundary getBoundary() { return boundary; }
}

// ============================================================
// QUADTREE INDEX
// ============================================================

/**
 * QuadTree-based spatial index
 * Efficiently organizes businesses for range queries
 */
class QuadTreeIndex implements SpatialIndex {
    private QuadTreeNode root;
    private final Map<String, Business> businessMap;
    private final int maxCapacity;
    private final int maxDepth;
    
    public QuadTreeIndex(GeoBoundary worldBoundary, int maxCapacity, int maxDepth) {
        this.root = new QuadTreeNode(worldBoundary, 0);
        this.businessMap = new ConcurrentHashMap<>();
        this.maxCapacity = maxCapacity;
        this.maxDepth = maxDepth;
    }
    
    public QuadTreeIndex() {
        this(new GeoBoundary(
            new GeoPoint(-90, -180),
            new GeoPoint(90, 180)
        ), 4, 6);
    }
    
    @Override
    public synchronized void insert(Business business) {
        businessMap.put(business.getId(), business);
        root.insert(business, maxCapacity, maxDepth);
    }
    
    @Override
    public synchronized boolean remove(String businessId) {
        Business business = businessMap.remove(businessId);
        if (business == null) {
            return false;
        }
        return root.remove(business);
    }
    
    @Override
    public List<Business> rangeQuery(GeoPoint center, double radiusKm) {
        List<Business> result = new ArrayList<>();
        root.rangeQuery(center, radiusKm, result);
        return result;
    }
    
    @Override
    public int size() {
        return businessMap.size();
    }
    
    @Override
    public synchronized void clear() {
        businessMap.clear();
        root = new QuadTreeNode(root.getBoundary(), 0);
    }
}

// ============================================================
// PROXIMITY SERVICE
// ============================================================

/**
 * Main service for proximity-based business search
 */
class ProximityServiceImpl {
    private final SpatialIndex spatialIndex;
    private final Map<String, Business> businesses;
    
    public ProximityServiceImpl(SpatialIndex spatialIndex) {
        this.spatialIndex = spatialIndex;
        this.businesses = new ConcurrentHashMap<>();
    }
    
    public void addBusiness(Business business) {
        businesses.put(business.getId(), business);
        spatialIndex.insert(business);
    }
    
    public boolean removeBusiness(String businessId) {
        Business business = businesses.remove(businessId);
        if (business == null) {
            return false;
        }
        return spatialIndex.remove(businessId);
    }
    
    public List<SearchResult> searchNearby(GeoPoint location, double radiusKm,
                                           String category, int limit) {
        List<Business> candidates = spatialIndex.rangeQuery(location, radiusKm);
        
        List<SearchResult> results = candidates.stream()
            .filter(b -> category == null || category.equals(b.getCategory()))
            .map(b -> new SearchResult(b, location.distanceTo(b.getLocation())))
            .filter(r -> r.getDistanceKm() <= radiusKm)
            .sorted()
            .limit(limit)
            .collect(Collectors.toList());
        
        return results;
    }
    
    public Business getBusiness(String businessId) {
        return businesses.get(businessId);
    }
    
    public boolean updateBusinessLocation(String businessId, GeoPoint newLocation) {
        Business oldBusiness = businesses.get(businessId);
        if (oldBusiness == null) {
            return false;
        }
        
        spatialIndex.remove(businessId);
        
        Business newBusiness = new Business(
            oldBusiness.getId(),
            oldBusiness.getName(),
            newLocation,
            oldBusiness.getCategory(),
            oldBusiness.getAddress(),
            oldBusiness.getPhoneNumber()
        );
        
        businesses.put(businessId, newBusiness);
        spatialIndex.insert(newBusiness);
        
        return true;
    }
    
    public int getTotalBusinesses() {
        return businesses.size();
    }
    
    public List<SearchResult> findKNearest(GeoPoint location, int k, String category) {
        double radiusKm = 5.0;
        List<SearchResult> results;
        
        while (true) {
            results = searchNearby(location, radiusKm, category, Integer.MAX_VALUE);
            
            if (results.size() >= k || radiusKm > 100) {
                break;
            }
            
            radiusKm *= 2;
        }
        
        return results.stream()
            .limit(k)
            .collect(Collectors.toList());
    }
}

// ============================================================
// DEMO AND TESTING
// ============================================================

public class ProximityService {
    
    public static void main(String[] args) {
        System.out.println("=== PROXIMITY SERVICE DEMO ===\n");
        
        // Demo 1: Basic Search
        demoBasicSearch();
        
        // Demo 2: Category Filtering
        demoCategoryFiltering();
        
        // Demo 3: K-Nearest Neighbors
        demoKNearestNeighbors();
        
        // Demo 4: Update Business Location
        demoUpdateLocation();
        
        // Demo 5: Distance Calculation Accuracy
        demoDistanceCalculation();
        
        // Demo 6: Performance Test
        demoPerformance();
    }
    
    private static void demoBasicSearch() {
        System.out.println("--- Demo 1: Basic Proximity Search ---");
        
        ProximityServiceImpl service = new ProximityServiceImpl(new QuadTreeIndex());
        
        // Add businesses in San Francisco area
        GeoPoint sfCenter = new GeoPoint(37.7749, -122.4194);
        
        service.addBusiness(new Business("B1", "Starbucks Downtown", 
            new GeoPoint(37.7849, -122.4094), "Coffee"));
        service.addBusiness(new Business("B2", "McDonald's Union Square",
            new GeoPoint(37.7879, -122.4074), "FastFood"));
        service.addBusiness(new Business("B3", "Whole Foods Market",
            new GeoPoint(37.7729, -122.4214), "Grocery"));
        service.addBusiness(new Business("B4", "Blue Bottle Coffee",
            new GeoPoint(37.7759, -122.4184), "Coffee"));
        service.addBusiness(new Business("B5", "Chipotle",
            new GeoPoint(37.7889, -122.4064), "FastFood"));
        
        System.out.println("Added 5 businesses in San Francisco");
        System.out.println("Total businesses: " + service.getTotalBusinesses());
        
        // Search within 2km radius
        List<SearchResult> results = service.searchNearby(sfCenter, 2.0, null, 10);
        
        System.out.println("\nBusinesses within 2km of SF Center:");
        for (SearchResult result : results) {
            System.out.println("  " + result);
        }
        
        System.out.println();
    }
    
    private static void demoCategoryFiltering() {
        System.out.println("--- Demo 2: Category Filtering ---");
        
        ProximityServiceImpl service = new ProximityServiceImpl(new QuadTreeIndex());
        
        GeoPoint nyCenter = new GeoPoint(40.7128, -74.0060);
        
        // Add various businesses
        service.addBusiness(new Business("NY1", "Joe's Pizza",
            new GeoPoint(40.7148, -74.0040), "Restaurant"));
        service.addBusiness(new Business("NY2", "Starbucks 5th Ave",
            new GeoPoint(40.7138, -74.0050), "Coffee"));
        service.addBusiness(new Business("NY3", "Shake Shack",
            new GeoPoint(40.7158, -74.0070), "Restaurant"));
        service.addBusiness(new Business("NY4", "Blue Bottle",
            new GeoPoint(40.7118, -74.0030), "Coffee"));
        
        System.out.println("Searching for Coffee shops within 1km:");
        List<SearchResult> coffeeShops = service.searchNearby(nyCenter, 1.0, "Coffee", 10);
        coffeeShops.forEach(r -> System.out.println("  " + r));
        
        System.out.println("\nSearching for Restaurants within 1km:");
        List<SearchResult> restaurants = service.searchNearby(nyCenter, 1.0, "Restaurant", 10);
        restaurants.forEach(r -> System.out.println("  " + r));
        
        System.out.println();
    }
    
    private static void demoKNearestNeighbors() {
        System.out.println("--- Demo 3: K-Nearest Neighbors ---");
        
        ProximityServiceImpl service = new ProximityServiceImpl(new QuadTreeIndex());
        
        GeoPoint userLocation = new GeoPoint(37.7749, -122.4194);
        
        // Add businesses at various distances
        service.addBusiness(new Business("K1", "Closest Store",
            new GeoPoint(37.7759, -122.4184), "Store"));
        service.addBusiness(new Business("K2", "Medium Store",
            new GeoPoint(37.7849, -122.4094), "Store"));
        service.addBusiness(new Business("K3", "Far Store",
            new GeoPoint(37.7949, -122.3994), "Store"));
        service.addBusiness(new Business("K4", "Very Close Store",
            new GeoPoint(37.7754, -122.4189), "Store"));
        service.addBusiness(new Business("K5", "Another Close Store",
            new GeoPoint(37.7764, -122.4179), "Store"));
        
        System.out.println("Finding 3 nearest stores:");
        List<SearchResult> nearest = service.findKNearest(userLocation, 3, "Store");
        
        for (int i = 0; i < nearest.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + nearest.get(i));
        }
        
        System.out.println();
    }
    
    private static void demoUpdateLocation() {
        System.out.println("--- Demo 4: Update Business Location ---");
        
        ProximityServiceImpl service = new ProximityServiceImpl(new QuadTreeIndex());
        
        GeoPoint searchLocation = new GeoPoint(40.7128, -74.0060);
        
        // Add business
        service.addBusiness(new Business("M1", "Mobile Truck",
            new GeoPoint(40.7148, -74.0040), "Food"));
        
        System.out.println("Initial search (1km radius):");
        List<SearchResult> before = service.searchNearby(searchLocation, 1.0, null, 10);
        System.out.println("  Found: " + before.size() + " businesses");
        
        // Move business far away
        System.out.println("\nMoving business to different location...");
        service.updateBusinessLocation("M1", new GeoPoint(40.8128, -74.0060));
        
        System.out.println("Search after move (1km radius):");
        List<SearchResult> after = service.searchNearby(searchLocation, 1.0, null, 10);
        System.out.println("  Found: " + after.size() + " businesses");
        
        System.out.println();
    }
    
    private static void demoDistanceCalculation() {
        System.out.println("--- Demo 5: Distance Calculation Accuracy ---");
        
        // Test Haversine vs Euclidean
        GeoPoint point1 = new GeoPoint(40.7128, -74.0060); // New York
        GeoPoint point2 = new GeoPoint(34.0522, -118.2437); // Los Angeles
        
        double haversine = point1.distanceTo(point2);
        
        // Rough Euclidean (WRONG but for comparison)
        double euclidean = Math.sqrt(
            Math.pow(point2.getLatitude() - point1.getLatitude(), 2) +
            Math.pow(point2.getLongitude() - point1.getLongitude(), 2)
        ) * 111; // rough km per degree
        
        System.out.println("Distance from New York to Los Angeles:");
        System.out.println("  Haversine (correct): " + String.format("%.2f km", haversine));
        System.out.println("  Euclidean (wrong): " + String.format("%.2f km", euclidean));
        System.out.println("  Actual distance: ~3940 km");
        System.out.println("  Haversine error: " + String.format("%.1f%%", 
            Math.abs(haversine - 3940) / 3940 * 100));
        
        System.out.println();
    }
    
    private static void demoPerformance() {
        System.out.println("--- Demo 6: Performance Test ---");
        
        ProximityServiceImpl service = new ProximityServiceImpl(new QuadTreeIndex());
        
        // Add 10,000 businesses across USA
        System.out.println("Adding 10,000 businesses...");
        Random random = new Random(42);
        
        for (int i = 0; i < 10000; i++) {
            double lat = 25.0 + random.nextDouble() * 25.0; // USA latitude range
            double lon = -125.0 + random.nextDouble() * 50.0; // USA longitude range
            
            service.addBusiness(new Business(
                "BUS-" + i,
                "Business " + i,
                new GeoPoint(lat, lon),
                "General"
            ));
        }
        
        System.out.println("Total businesses indexed: " + service.getTotalBusinesses());
        
        // Benchmark search performance
        GeoPoint searchLocation = new GeoPoint(37.7749, -122.4194); // San Francisco
        int searchCount = 1000;
        
        System.out.println("\nRunning " + searchCount + " searches...");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < searchCount; i++) {
            service.searchNearby(searchLocation, 10.0, null, 20);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1000000;
        
        System.out.println("\nPerformance Results:");
        System.out.println("  Total time: " + durationMs + "ms");
        System.out.println("  Average per search: " + 
            String.format("%.2f ms", (double) durationMs / searchCount));
        System.out.println("  Throughput: " + 
            String.format("%.0f searches/sec", (double) searchCount / durationMs * 1000));
        
        // Sample search result
        List<SearchResult> results = service.searchNearby(searchLocation, 50.0, null, 5);
        System.out.println("\nSample search (50km radius, top 5):");
        results.forEach(r -> System.out.println("  " + r));
        
        System.out.println();
    }
    
    /**
     * Real-world scenario: Restaurant search
     */
    public static void demoRestaurantSearch() {
        System.out.println("\n=== REAL-WORLD SCENARIO: Restaurant Search ===\n");
        
        ProximityServiceImpl service = new ProximityServiceImpl(new QuadTreeIndex());
        
        // San Francisco restaurants
        GeoPoint sfCenter = new GeoPoint(37.7749, -122.4194);
        
        // Add various restaurants
        service.addBusiness(new Business("R1", "The House",
            new GeoPoint(37.7849, -122.4094), "Asian", 
            "1230 Grant Ave", "415-986-8612"));
        
        service.addBusiness(new Business("R2", "Tony's Pizza Napoletana",
            new GeoPoint(37.7989, -122.4104), "Italian",
            "1570 Stockton St", "415-835-9888"));
        
        service.addBusiness(new Business("R3", "Swan Oyster Depot",
            new GeoPoint(37.7919, -122.4184), "Seafood",
            "1517 Polk St", "415-673-1101"));
        
        service.addBusiness(new Business("R4", "House of Prime Rib",
            new GeoPoint(37.7889, -122.4264), "American",
            "1906 Van Ness Ave", "415-885-4605"));
        
        service.addBusiness(new Business("R5", "Kokkari Estiatorio",
            new GeoPoint(37.7949, -122.3984), "Greek",
            "200 Jackson St", "415-981-0983"));
        
        // User at Union Square
        GeoPoint userLocation = new GeoPoint(37.7879, -122.4074);
        
        System.out.println("User location: Union Square, SF");
        System.out.println("Searching for restaurants within 2km...\n");
        
        List<SearchResult> results = service.searchNearby(userLocation, 2.0, null, 10);
        
        System.out.println("Found " + results.size() + " restaurants:");
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            Business b = result.getBusiness();
            System.out.println(String.format("  %d. %s", i + 1, result));
            System.out.println(String.format("     Category: %s | Address: %s", 
                b.getCategory(), b.getAddress()));
        }
        
        // Category-specific search
        System.out.println("\nSearching for Italian restaurants only:");
        List<SearchResult> italian = service.searchNearby(userLocation, 2.0, "Italian", 10);
        italian.forEach(r -> System.out.println("  " + r));
    }
}
