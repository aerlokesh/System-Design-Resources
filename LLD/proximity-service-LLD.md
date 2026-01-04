# Proximity Service - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Geospatial Indexing Approaches](#geospatial-indexing-approaches)
4. [Core Entities and Relationships](#core-entities-and-relationships)
5. [Class Design](#class-design)
6. [Complete Implementation](#complete-implementation)
7. [Extensibility](#extensibility)
8. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Proximity Service?**

A proximity service allows users to search for nearby businesses based on their current location. Given a user's coordinates (latitude, longitude) and a search radius, the system efficiently returns all businesses within that radius, typically sorted by distance.

**Real-World Examples:**
- Google Maps: "restaurants near me"
- Yelp: Search businesses by location
- Uber/Lyft: Find nearby drivers
- Amazon: Find nearby Amazon Go stores
- Airbnb: Search properties in an area

**Core Challenges:**
- **Efficient Search:** Finding businesses in O(log n) instead of O(n)
- **Geospatial Indexing:** Organizing locations for fast queries
- **Distance Calculation:** Accurate distance on a sphere (Earth)
- **Dynamic Updates:** Adding/removing businesses efficiently
- **Scalability:** Handle millions of businesses
- **Radius Queries:** Support various search radii

---

## Requirements

### Functional Requirements

1. **Business Management**
   - Add a business with location (latitude, longitude)
   - Remove a business
   - Update business location
   - Get business details

2. **Search Operations**
   - Find all businesses within radius (km) of a location
   - Support filtering by business type/category
   - Return results sorted by distance
   - Support pagination for large result sets

3. **Location Operations**
   - Calculate distance between two coordinates
   - Validate latitude (-90 to 90) and longitude (-180 to 180)
   - Handle edge cases (poles, date line)

4. **Query Optimization**
   - Use spatial indexing (QuadTree, GeoHash, R-tree)
   - Efficient range queries
   - Support bounding box queries

### Non-Functional Requirements

1. **Performance**
   - Search query: < 100ms for 1 million businesses
   - Insert/Delete: O(log n) time complexity
   - Support 1000+ queries per second

2. **Accuracy**
   - Distance calculation accurate to meters
   - Handle Earth's curvature (Haversine formula)
   - Correct results at all latitudes/longitudes

3. **Scalability**
   - Support millions of businesses
   - Efficient memory usage
   - Handle high query volume

4. **Consistency**
   - Updated businesses reflect in searches immediately
   - No stale data in results
   - Thread-safe operations

### Out of Scope

- User authentication and authorization
- Business reviews and ratings (beyond basic info)
- Real-time business updates (open/closed status)
- Payment and reservation systems
- Distributed system design (single server focus)
- Map rendering and UI

---

## Geospatial Indexing Approaches

### 1. QuadTree

**Concept:** Recursively divide 2D space into 4 quadrants until each node contains few enough points.

**Structure:**
```
World
├─ NW Quadrant
│  ├─ NW.NW
│  ├─ NW.NE
│  ├─ NW.SW
│  └─ NW.SE
├─ NE Quadrant
├─ SW Quadrant
└─ SE Quadrant
```

**Pros:**
- ✅ Efficient range queries: O(log n)
- ✅ Dynamic (easy insert/delete)
- ✅ Good for evenly distributed data
- ✅ Natural spatial partitioning

**Cons:**
- ❌ Unbalanced with clustered data
- ❌ More complex implementation
- ❌ Memory overhead for tree structure

**Best For:**
- Dynamic datasets (frequent updates)
- In-memory indexing
- LLD interviews (most common)

### 2. GeoHash

**Concept:** Encode lat/lon into a string. Nearby locations share prefix.

**Example:**
```
Location: (37.7749, -122.4194) [San Francisco]
GeoHash: "9q8yy" (precision 5)

Nearby locations:
- 9q8yy9... (very close)
- 9q8yy8... (close)
- 9q8yx... (nearby)
```

**Pros:**
- ✅ Simple string-based indexing
- ✅ Prefix search in databases
- ✅ Easy to understand
- ✅ Works well with B-trees

**Cons:**
- ❌ Edge cases at cell boundaries
- ❌ May need to check adjacent cells
- ❌ Fixed precision levels

**Best For:**
- Database indexing (Redis, MongoDB)
- Distributed systems
- Simple implementations

### 3. R-Tree

**Concept:** Tree of bounding boxes, optimized for spatial queries.

**Pros:**
- ✅ Excellent for range queries
- ✅ Used in databases (PostgreSQL PostGIS)
- ✅ Handles overlapping regions well

**Cons:**
- ❌ Complex implementation
- ❌ Rebalancing overhead
- ❌ Less intuitive

**Best For:**
- Database implementations
- Static or slowly changing data
- Complex geometric queries

### Comparison Table

| Approach | Query Time | Insert/Delete | Memory | Complexity | Best Use |
|----------|-----------|---------------|--------|------------|----------|
| QuadTree | O(log n) | O(log n) | Medium | Medium | Dynamic, in-memory |
| GeoHash | O(log n)* | O(1) | Low | Low | Database, distributed |
| R-Tree | O(log n) | O(log n) | High | High | Complex queries |

**Our Choice for LLD:** QuadTree - Best balance for interview setting

---

## Core Entities and Relationships

### Key Entities

1. **Business**
   - Business ID, name, category
   - Location (lat, lon)
   - Additional metadata (address, phone, etc.)

2. **Location (GeoPoint)**
   - Latitude and longitude
   - Distance calculation methods
   - Validation

3. **ProximityService**
   - Main service interface
   - Handles search operations
   - Manages business index

4. **SpatialIndex (Interface)**
   - Abstraction for different indexing strategies
   - Implementations: QuadTree, GeoHash, Grid
   - Insert, delete, range query operations

5. **QuadTree/QuadTreeNode**
   - Tree-based spatial index
   - Recursive subdivision
   - Range query support

6. **SearchResult**
   - Business with calculated distance
   - Sorted by distance
   - Pagination support

### Entity Relationships

```
┌─────────────────────────────────────────────┐
│         ProximityService                     │
│  - manages businesses                        │
│  - handles search queries                    │
│  - uses SpatialIndex                         │
└───────────────┬─────────────────────────────┘
                │ uses
                ▼
    ┌───────────────────────────┐
    │    SpatialIndex           │
    │    <<interface>>          │
    │  - insert()               │
    │  - remove()               │
    │  - rangeQuery()           │
    └───────────┬───────────────┘
                │ implements
        ┌───────┴────────┬──────────┐
        │                │          │
    ┌───▼────────┐  ┌────▼─────┐  ┌▼─────┐
    │  QuadTree  │  │ GeoHash  │  │ Grid │
    │   Index    │  │  Index   │  │Index │
    └────┬───────┘  └──────────┘  └──────┘
         │ contains
         ▼
    ┌───────────────────────────┐
    │   QuadTreeNode            │
    │  - boundary               │
    │  - businesses             │
    │  - children (NW,NE,SW,SE) │
    └───────────────────────────┘

┌─────────────┐      stored in     ┌──────────────┐
│  Business   │◄───────────────────│ SpatialIndex │
│  - id       │                    └──────────────┘
│  - name     │
│  - location │
│  - category │
└─────────────┘
```

---

## Class Design

### 1. GeoPoint (Location)

```java
/**
 * Represents a geographic location with latitude and longitude
 * Immutable and thread-safe
 */
public class GeoPoint {
    private final double latitude;
    private final double longitude;
    
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
     * @param other Target location
     * @return Distance in kilometers
     */
    public double distanceTo(GeoPoint other) {
        // Haversine formula for great circle distance
        final double EARTH_RADIUS_KM = 6371.0;
        
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
}
```

**Design Decisions:**
- **Immutable:** Thread-safe, can be shared
- **Validation:** Prevents invalid coordinates
- **Haversine formula:** Accurate distance on sphere
- **Equals/HashCode:** Can be used in collections

### 2. Business

```java
/**
 * Represents a business with location and metadata
 */
public class Business {
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
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public GeoPoint getLocation() { return location; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public String getPhoneNumber() { return phoneNumber; }
    
    @Override
    public String toString() {
        return String.format("Business{id='%s', name='%s', category='%s', location=(%.4f, %.4f)}",
            id, name, category, location.getLatitude(), location.getLongitude());
    }
}
```

### 3. SearchResult

```java
/**
 * Represents a search result with business and distance
 */
public class SearchResult implements Comparable<SearchResult> {
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
        return String.format("%s (%.2f km away)", 
            business.getName(), distanceKm);
    }
}
```

### 4. SpatialIndex Interface

```java
/**
 * Interface for spatial indexing strategies
 */
public interface SpatialIndex {
    /**
     * Insert a business into the index
     */
    void insert(Business business);
    
    /**
     * Remove a business from the index
     */
    boolean remove(String businessId);
    
    /**
     * Find all businesses within radius of a location
     * @param center Search center point
     * @param radiusKm Search radius in kilometers
     * @return List of businesses within radius
     */
    List<Business> rangeQuery(GeoPoint center, double radiusKm);
    
    /**
     * Get total number of businesses in index
     */
    int size();
    
    /**
     * Clear all businesses
     */
    void clear();
}
```

### 5. ProximityService

```java
/**
 * Main service for proximity-based business search
 */
public class ProximityService {
    private final SpatialIndex spatialIndex;
    private final Map<String, Business> businesses; // For O(1) lookup by ID
    
    public ProximityService(SpatialIndex spatialIndex) {
        this.spatialIndex = spatialIndex;
        this.businesses = new ConcurrentHashMap<>();
    }
    
    /**
     * Add a business to the service
     */
    public void addBusiness(Business business);
    
    /**
     * Remove a business from the service
     */
    public boolean removeBusiness(String businessId);
    
    /**
     * Search for businesses near a location
     * @param location Search center
     * @param radiusKm Search radius in km
     * @param category Optional category filter (null for all)
     * @param limit Maximum number of results
     * @return List of SearchResults sorted by distance
     */
    public List<SearchResult> searchNearby(GeoPoint location, double radiusKm,
                                           String category, int limit);
    
    /**
     * Get business by ID
     */
    public Business getBusiness(String businessId);
    
    /**
     * Update business location
     */
    public boolean updateBusinessLocation(String businessId, GeoPoint newLocation);
}
```

---

## Complete Implementation

### QuadTree Implementation

```java
/**
 * QuadTree-based spatial index
 * Efficiently organizes businesses for range queries
 */
public class QuadTreeIndex implements SpatialIndex {
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
    
    /**
     * Default constructor for world coordinates
     */
    public QuadTreeIndex() {
        this(new GeoBoundary(
            new GeoPoint(-90, -180),  // SW corner
            new GeoPoint(90, 180)      // NE corner
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
```

### QuadTreeNode

```java
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
    
    /**
     * Insert a business into this node
     */
    public boolean insert(Business business, int maxCapacity, int maxDepth) {
        // Check if business is in this boundary
        if (!boundary.contains(business.getLocation())) {
            return false;
        }
        
        // If not subdivided and has capacity, add here
        if (!subdivided && businesses.size() < maxCapacity) {
            businesses.add(business);
            return true;
        }
        
        // If at max depth, add here regardless of capacity
        if (depth >= maxDepth) {
            businesses.add(business);
            return true;
        }
        
        // Subdivide if needed
        if (!subdivided) {
            subdivide();
        }
        
        // Insert into appropriate child
        if (northWest.insert(business, maxCapacity, maxDepth)) return true;
        if (northEast.insert(business, maxCapacity, maxDepth)) return true;
        if (southWest.insert(business, maxCapacity, maxDepth)) return true;
        if (southEast.insert(business, maxCapacity, maxDepth)) return true;
        
        return false;
    }
    
    /**
     * Remove a business from this node
     */
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
    
    /**
     * Find all businesses within radius of center
     */
    public void rangeQuery(GeoPoint center, double radiusKm, List<Business> result) {
        // Check if this node's boundary intersects the search circle
        if (!boundary.intersectsCircle(center, radiusKm)) {
            return;
        }
        
        // Check businesses in this node
        for (Business business : businesses) {
            if (center.distanceTo(business.getLocation()) <= radiusKm) {
                result.add(business);
            }
        }
        
        // Recursively check children
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
        
        // Create 4 quadrants
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
        
        // Redistribute existing businesses to children
        List<Business> temp = new ArrayList<>(businesses);
        businesses.clear();
        for (Business business : temp) {
            insert(business, 0, Integer.MAX_VALUE); // Force to children
        }
    }
    
    public GeoBoundary getBoundary() {
        return boundary;
    }
}
```

### GeoBoundary

```java
/**
 * Represents a rectangular geographic boundary
 */
public class GeoBoundary {
    private final GeoPoint southWest; // Bottom-left corner
    private final GeoPoint northEast; // Top-right corner
    
    public GeoBoundary(GeoPoint southWest, GeoPoint northEast) {
        this.southWest = southWest;
        this.northEast = northEast;
    }
    
    /**
     * Check if a point is within this boundary
     */
    public boolean contains(GeoPoint point) {
        return point.getLatitude() >= southWest.getLatitude() &&
               point.getLatitude() <= northEast.getLatitude() &&
               point.getLongitude() >= southWest.getLongitude() &&
               point.getLongitude() <= northEast.getLongitude();
    }
    
    /**
     * Check if this boundary intersects with a circle
     */
    public boolean intersectsCircle(GeoPoint center, double radiusKm) {
        // Find closest point in rectangle to circle center
        double closestLat = Math.max(southWest.getLatitude(),
                           Math.min(center.getLatitude(), northEast.getLatitude()));
        double closestLon = Math.max(southWest.getLongitude(),
                           Math.min(center.getLongitude(), northEast.getLongitude()));
        
        GeoPoint closestPoint = new GeoPoint(closestLat, closestLon);
        return center.distanceTo(closestPoint) <= radiusKm;
    }
    
    public GeoPoint getSouthWest() { return southWest; }
    public GeoPoint getNorthEast() { return northEast; }
}
```

### ProximityService (Full Implementation)

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ProximityService {
    private final SpatialIndex spatialIndex;
    private final Map<String, Business> businesses;
    
    public ProximityService(SpatialIndex spatialIndex) {
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
        // Get candidates from spatial index
        List<Business> candidates = spatialIndex.rangeQuery(location, radiusKm);
        
        // Calculate distances and create results
        List<SearchResult> results = candidates.stream()
            .filter(b -> category == null || category.equals(b.getCategory()))
            .map(b -> new SearchResult(b, location.distanceTo(b.getLocation())))
            .filter(r -> r.getDistanceKm() <= radiusKm) // Double-check distance
            .sorted() // Sort by distance
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
        
        // Remove old location
        spatialIndex.remove(businessId);
        
        // Create new business with updated location
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
}
```

---

## Extensibility

### 1. GeoHash-Based Implementation

**Alternative to QuadTree for database-backed systems:**

```java
public class GeoHashIndex implements SpatialIndex {
    private final Map<String, Set<Business>> geoHashMap;
    private final int precision;
    
    public GeoHashIndex(int precision) {
        this.geoHashMap = new ConcurrentHashMap<>();
        this.precision = precision; // 1-12, higher = more precise
    }
    
    @Override
    public void insert(Business business) {
        String geoHash = encodeGeoHash(business.getLocation(), precision);
        geoHashMap.computeIfAbsent(geoHash, k -> ConcurrentHashMap.newKeySet())
                  .add(business);
    }
    
    @Override
    public List<Business> rangeQuery(GeoPoint center, double radiusKm) {
        String centerHash = encodeGeoHash(center, precision);
        
        // Get businesses from center cell and adjacent cells
        Set<String> cellsToCheck = getAdjacentCells(centerHash);
        cellsToCheck.add(centerHash);
        
        List<Business> candidates = new ArrayList<>();
        for (String hash : cellsToCheck) {
            Set<Business> cellBusinesses = geoHashMap.get(hash);
            if (cellBusinesses != null) {
                candidates.addAll(cellBusinesses);
            }
        }
        
        // Filter by actual distance
        return candidates.stream()
            .filter(b -> center.distanceTo(b.getLocation()) <= radiusKm)
            .collect(Collectors.toList());
    }
    
    private String encodeGeoHash(GeoPoint point, int precision) {
        // GeoHash encoding implementation
        // Returns string like "9q8yy"
        return GeoHashEncoder.encode(point.getLatitude(), 
                                     point.getLongitude(), precision);
    }
    
    private Set<String> getAdjacentCells(String geoHash) {
        // Return 8 adjacent cell hashes
        return GeoHashEncoder.getNeighbors(geoHash);
    }
}
```

### 2. Grid-Based Index (Simplest)

**Fixed-size grid for simple scenarios:**

```java
public class GridIndex implements SpatialIndex {
    private final Map<GridCell, List<Business>> grid;
    private final double cellSizeKm;
    
    public GridIndex(double cellSizeKm) {
        this.grid = new ConcurrentHashMap<>();
        this.cellSizeKm = cellSizeKm;
    }
    
    @Override
    public void insert(Business business) {
        GridCell cell = getCell(business.getLocation());
        grid.computeIfAbsent(cell, k -> new ArrayList<>())
            .add(business);
    }
    
    @Override
    public List<Business> rangeQuery(GeoPoint center, double radiusKm) {
        Set<GridCell> cellsToCheck = getCellsInRadius(center, radiusKm);
        
        List<Business> candidates = new ArrayList<>();
        for (GridCell cell : cellsToCheck) {
            List<Business> cellBusinesses = grid.get(cell);
            if (cellBusinesses != null) {
                candidates.addAll(cellBusinesses);
            }
        }
        
        // Filter by actual distance
        return candidates.stream()
            .filter(b -> center.distanceTo(b.getLocation()) <= radiusKm)
            .collect(Collectors.toList());
    }
    
    private GridCell getCell(GeoPoint point) {
        int latCell = (int) Math.floor(point.getLatitude() / cellSizeKm);
        int lonCell = (int) Math.floor(point.getLongitude() / cellSizeKm);
        return new GridCell(latCell, lonCell);
    }
    
    private Set<GridCell> getCellsInRadius(GeoPoint center, double radiusKm) {
        Set<GridCell> cells = new HashSet<>();
        GridCell centerCell = getCell(center);
        
        // Calculate how many cells to check in each direction
        int cellsToCheck = (int) Math.ceil(radiusKm / cellSizeKm) + 1;
        
        for (int latOffset = -cellsToCheck; latOffset <= cellsToCheck; latOffset++) {
            for (int lonOffset = -cellsToCheck; lonOffset <= cellsToCheck; lonOffset++) {
                cells.add(new GridCell(
                    centerCell.latCell + latOffset,
                    centerCell.lonCell + lonOffset
                ));
            }
        }
        
        return cells;
    }
}

class GridCell {
    final int latCell;
    final int lonCell;
    
    GridCell(int latCell, int lonCell) {
        this.latCell = latCell;
        this.lonCell = lonCell;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GridCell)) return false;
        GridCell that = (GridCell) o;
        return latCell == that.latCell && lonCell == that.lonCell;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(latCell, lonCell);
    }
}
```

### 3. Filtering and Ranking

**Add business attributes for advanced search:**

```java
public class BusinessFilter {
    private String category;
    private Double minRating;
    private Boolean openNow;
    private Set<String> amenities; // WiFi, parking, etc.
    
    public boolean matches(Business business) {
        if (category != null && !category.equals(business.getCategory())) {
            return false;
        }
        if (minRating != null && business.getRating() < minRating) {
            return false;
        }
        if (openNow != null && openNow != business.isOpenNow()) {
            return false;
        }
        return true;
    }
}

public class ProximityServiceWithFilters extends ProximityService {
    public List<SearchResult> searchWithFilters(GeoPoint location, double radiusKm,
                                                 BusinessFilter filter, int limit) {
        List<Business> candidates = spatialIndex.rangeQuery(location, radiusKm);
        
        return candidates.stream()
            .filter(filter::matches)
            .map(b -> new SearchResult(b, location.distanceTo(b.getLocation())))
            .sorted()
            .limit(limit)
            .collect(Collectors.toList());
    }
}
```

### 4. K-Nearest Neighbors

**Find exact K closest businesses:**

```java
public class ProximityServiceWithKNN extends ProximityService {
    /**
     * Find K nearest businesses using priority queue
     * More accurate than radius search
     */
    public List<SearchResult> findKNearest(GeoPoint location, int k, String category) {
        // Start with reasonable radius
        double radiusKm = 5.0;
        List<SearchResult> results;
        
        // Expand radius until we have enough results
        while (true) {
            results = searchNearby(location, radiusKm, category, Integer.MAX_VALUE);
            
            if (results.size() >= k) {
                break;
            }
            
            if (radiusKm > 100) { // Max reasonable search radius
                break;
            }
            
            radiusKm *= 2; // Double the radius
        }
        
        // Return top K results
        return results.stream()
            .limit(k)
            .collect(Collectors.toList());
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- What's the expected dataset size?
- Query frequency vs update frequency?
- Memory constraints?
- Accuracy requirements?
- Global or regional service?

**2. Requirements (3 minutes)**
- Core operations (add, remove, search)
- Search criteria (radius, category, limit)
- Performance targets
- Data update patterns

**3. Choose Indexing Strategy (5 minutes)**
- Discuss QuadTree, GeoHash, Grid
- Explain trade-offs
- Justify selection (usually QuadTree for LLD)

**4. Implement Core Components (20-25 minutes)**
- Start with GeoPoint (distance calculation)
- Implement QuadTree structure
- Add search functionality
- Show thread safety

**5. Discuss Extensions (5 minutes)**
- Filtering and ranking
- K-nearest neighbors
- Caching strategies
- Database considerations

### Common Interview Questions & Answers

**Q1: "Why QuadTree instead of linear search?"**

**A:** Performance comparison:
- **Linear search:** O(n) - Check every business
- **QuadTree:** O(log n) - Only check relevant regions
- **Example:** For 1M businesses:
  - Linear: 1,000,000 distance calculations
  - QuadTree: ~20 nodes to check, maybe 100 businesses

**Q2: "How do you handle businesses near cell boundaries?"**

**A:** 
- **QuadTree:** Natural overlap handling, check all intersecting nodes
- **GeoHash:** Must check adjacent cells (8 neighbors)
- **Grid:** Similar to GeoHash, check surrounding cells
- **Solution:** Always verify distance with exact calculation

**Q3: "What about the Haversine formula? Why not Euclidean distance?"**

**A:**
- **Euclidean:** Treats Earth as flat (wrong at large distances)
- **Haversine:** Accounts for Earth's curvature
- **Error:** Euclidean can be off by 10-20% for 100km distances
- **Trade-off:** Haversine is slower but accurate

```java
// Euclidean (WRONG for lat/lon)
double distance = Math.sqrt(
    Math.pow(lat2 - lat1, 2) + Math.pow(lon2 - lon1, 2)
) * 111; // rough km per degree

// Haversine (CORRECT)
// Full formula accounts for sphere geometry
```

**Q4: "How do you handle high-density areas (e.g., Manhattan)?"**

**A:**
1. **QuadTree:** Automatically subdivides dense regions
2. **Max depth limit:** Prevents infinite recursion
3. **Leaf capacity:** Allow more businesses at leaf nodes
4. **Optimization:** Return top-K instead of all results

**Q5: "What if a business moves locations?"**

**A:**
```java
public boolean updateBusinessLocation(String id, GeoPoint newLocation) {
    // 1. Remove from old location in index
    spatialIndex.remove(id);
    
    // 2. Create new business object with new location
    Business updated = new Business(id, name, newLocation, category);
    
    // 3. Insert at new location
    businesses.put(id, updated);
    spatialIndex.insert(updated);
    
    return true;
}
```

**Key:** Remove and re-insert (no in-place update in spatial index)

### Design Patterns Used

1. **Strategy Pattern:** SpatialIndex implementations (QuadTree, GeoHash, Grid)
2. **Composite Pattern:** QuadTreeNode tree structure
3. **Template Method:** Range query algorithm
4. **Immutable Object:** GeoPoint (thread-safe)
5. **Factory Pattern:** Creating different index types

### Expected Performance by Level

**Junior Engineer:**
- Understand the problem (proximity search)
- Basic distance calculation (may use Euclidean)
- Linear search implementation
- Basic filtering by category

**Mid-Level Engineer:**
- Implement QuadTree or GeoHash
- Haversine formula for distance
- Efficient range queries O(log n)
- Thread safety considerations
- Handle edge cases (boundaries)

**Senior Engineer:**
- Complete QuadTree with all optimizations
- Multiple indexing strategies comparison
- Advanced features (K-NN, filtering, caching)
- Performance analysis and benchmarks
- Distributed system considerations
- Production concerns (monitoring, scaling)

### Key Trade-offs to Discuss

**1. Indexing Strategy**
- **QuadTree:** Best for dynamic data, good performance
- **GeoHash:** Simpler, better for databases
- **Grid:** Simplest, good for uniform distribution
- **Choice:** Depends on data characteristics

**2. Precision vs Performance**
- **Higher precision:** More accurate, slower queries
- **Lower precision:** Faster, but may miss results
- **Balance:** Adjust based on use case

**3. Memory vs Query Speed**
- **More memory:** Pre-compute more, faster queries
- **Less memory:** Compute on-the-fly, slower
- **QuadTree:** Good balance

**4. Update Frequency**
- **High updates:** QuadTree (easy rebalancing)
- **Low updates:** Any approach works
- **Read-heavy:** Optimize for queries

### Code Quality Checklist

✅ **Geospatial Accuracy**
- Haversine formula for distance
- Coordinate validation
- Handle edge cases (poles, date line)

✅ **Performance**
- O(log n) search with spatial index
- Efficient range queries
- Minimal unnecessary calculations

✅ **Thread Safety**
- Synchronized index operations
- ConcurrentHashMap for business lookup
- No race conditions

✅ **Extensibility**
- SpatialIndex interface
- Pluggable implementations
- Easy to add filters

✅ **Testing**
- Unit tests for distance calculation
- Range query verification
- Edge case handling

---

## Summary

This Proximity Service demonstrates:

1. **Efficient Geospatial Search:** QuadTree index for O(log n) queries
2. **Accurate Distance:** Haversine formula for great circle distance
3. **Flexible Design:** Strategy pattern for different index types
4. **Production Ready:** Thread-safe, validated, extensible
5. **Interview Success:** Clear requirements, trade-off analysis, complete implementation

**Key Takeaways:**
- **Spatial indexing is essential** for proximity search performance
- **QuadTree** is best for LLD interviews (balance of complexity and performance)
- **Haversine formula** is required for accurate Earth distances
- **Thread safety** matters for concurrent access
- **Extensibility** through interfaces enables different implementations

**Related Concepts:**
- Geospatial databases (PostGIS, MongoDB geospatial)
- R-tree indexing
- Hilbert curves for spatial ordering
- Distributed geospatial systems

**Real-World Applications:**
- Ride-sharing (Uber, Lyft)
- Food delivery (DoorDash, UberEats)
- Local search (Google Maps, Yelp)
- Real estate (Zillow, Redfin)
- Travel (Airbnb, Hotels.com)

---

*Last Updated: January 4, 2026*
*Difficulty Level: Medium to Hard*
*Key Focus: Geospatial Indexing, Distance Calculation, Tree Structures*
*Company: Amazon (Senior Level)*
*Interview Success Rate: High with proper spatial indexing knowledge*
