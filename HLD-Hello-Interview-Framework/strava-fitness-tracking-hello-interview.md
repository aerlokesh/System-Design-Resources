# Design Strava (Fitness Tracking App) - Hello Interview Framework

> **Question**: Design a fitness tracking application like Strava that allows users to record and share their physical activities (running, cycling, swimming), view real-time GPS tracks, see activity feeds from friends, compare performance on segments/leaderboards, and access detailed analytics. Support offline-first mobile tracking with sync on reconnect.
>
> **Asked at**: Strava, Garmin, Apple, Google, Nike
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Activity Recording**: Users record activities (run, cycle, swim) from mobile app with GPS tracking. Store GPS trace, duration, distance, pace/speed, elevation, heart rate (if available). Support offline recording with sync on reconnect.
2. **Activity Feed**: Show a social feed of friends' recent activities with stats, maps, and kudos (likes). Paginated, reverse-chronological.
3. **Segment Leaderboards**: Pre-defined route segments (e.g., a popular hill climb). When a user's GPS trace matches a segment, compute their time and rank them on the leaderboard.
4. **Activity Analytics**: Per-activity details (splits, pace chart, elevation profile, heart rate zones). Aggregated stats: weekly/monthly/yearly totals and personal records.
5. **Real-Time Tracking**: Friends can watch a user's live activity on a map as it happens (live location updates every 5 seconds).

#### Nice to Have (P1)
- Route planning and navigation.
- Training plans and goal setting.
- Clubs/groups with shared challenges.
- Integration with wearables (Garmin, Apple Watch, Fitbit).
- Photo/media attachments to activities.
- Heatmaps (aggregate of all your activities on a map).

#### Below the Line (Out of Scope)
- E-commerce (gear shop).
- Premium subscription billing.
- Social messaging/chat between users.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **GPS Upload Latency** | < 5 seconds (activity sync after completion) | User expects quick save after finishing activity |
| **Feed Latency** | < 2 seconds for feed load | Standard social feed expectation |
| **Live Tracking Update** | Every 5 seconds, < 2s delivery | Watchers need near-real-time position |
| **Segment Matching** | Within 10 minutes of activity upload | Leaderboard results don't need to be instant |
| **Offline Support** | Full activity recording without connectivity | Users run/cycle in areas with no signal |
| **Availability** | 99.9% | Users can always record activities locally; sync later |
| **Scale** | 100M users, 50M activities/week | Strava-scale user base |
| **GPS Data Precision** | Sub-5m accuracy, 1-second sampling | Accurate distance and segment matching |

### Capacity Estimation

```
Users:
  Total registered: 100M
  Monthly Active Users (MAU): 30M
  Daily Active Users (DAU): 10M
  
Activities:
  New activities per week: 50M
  Per day: ~7M activities/day
  Per second: ~80 activities/sec
  
  Average GPS points per activity: 3600 (1 point/sec for 1 hour)
  GPS point size: ~30 bytes (lat, lon, elevation, timestamp, heart_rate)
  Average activity GPS data: 3600 × 30 = ~108 KB
  Average activity metadata: ~500 bytes
  Total per activity: ~110 KB
  
  Daily activity data: 7M × 110 KB = ~770 GB/day
  Annual: ~280 TB/year

Feed:
  Feed reads per day: 30M (DAU × ~3 feed views)
  Feed QPS: ~350 reads/sec
  With caching: most served from cache

Live Tracking:
  Concurrent live activities: ~500K (peak hours)
  Watchers per live activity: ~3 average
  Location updates: 500K × 1 update/5sec = 100K updates/sec
  Delivery: 100K × 3 watchers = 300K deliveries/sec

Segments:
  Total segments: 30M (user-created + popular)
  Segment matches per activity: ~5-20 segments
  Segment matching QPS: 80 activities/sec × 10 segments = 800 matches/sec

Storage:
  GPS traces: ~280 TB/year (S3, compressed to ~100 TB)
  Activity metadata: 50M/week × 500B = 25 GB/week → ~1.3 TB/year
  User profiles: 100M × 2 KB = 200 GB
  Social graph: 100M × 100 friends × 16B = 160 GB
```

---

## 2️⃣ Core Entities

### Entity 1: Activity
```java
public class Activity {
    private final String activityId;           // UUID
    private final String userId;
    private final ActivityType type;           // RUN, RIDE, SWIM, HIKE, etc.
    private final Instant startTime;
    private final Instant endTime;
    private final Duration elapsedTime;        // Total time including pauses
    private final Duration movingTime;         // Time actually moving
    private final double distanceMeters;
    private final double elevationGainMeters;
    private final String gpsTraceUrl;          // S3 URL for full GPS data
    private final List<LatLng> polyline;       // Simplified polyline for map display
    private final String title;                // "Morning Run"
    private final String description;
    private final ActivityStats stats;         // Pace, speed, heart rate zones, splits
    private final Visibility visibility;       // PUBLIC, FOLLOWERS, PRIVATE
    private final Instant uploadedAt;
    private final DeviceInfo device;           // "iPhone 15", "Garmin Forerunner 255"
}

public enum ActivityType { RUN, RIDE, SWIM, HIKE, WALK, SKI, WORKOUT }
```

### Entity 2: GPS Trace
```java
public class GPSTrace {
    private final String activityId;
    private final List<TrackPoint> points;     // 1 per second, 3600 for 1-hour activity
}

public class TrackPoint {
    private final double latitude;             // -90 to 90
    private final double longitude;            // -180 to 180
    private final double elevationMeters;      // Altitude
    private final Instant timestamp;
    private final Integer heartRate;           // BPM (optional, from HR monitor)
    private final Double speedMps;             // Meters per second
    private final Double cadence;              // Steps/min or RPM
}
```

### Entity 3: Segment
```java
public class Segment {
    private final String segmentId;
    private final String name;                 // "Hawk Hill Climb"
    private final List<LatLng> path;           // GPS coordinates defining the segment
    private final double distanceMeters;
    private final double elevationGainMeters;
    private final SegmentType type;            // CLIMB, SPRINT, FLAT
    private final String createdBy;
    private final int totalAttempts;           // How many times this segment has been ridden/run
}

public class SegmentEffort {
    private final String effortId;
    private final String segmentId;
    private final String activityId;
    private final String userId;
    private final Duration elapsedTime;        // User's time on this segment
    private final Instant startTime;
    private final int rank;                    // Position on leaderboard
    private final boolean personalRecord;      // Is this the user's best time?
}
```

### Entity 4: Feed Item
```java
public class FeedItem {
    private final String feedItemId;
    private final String activityId;
    private final String userId;
    private final String userName;
    private final String userAvatarUrl;
    private final ActivityType activityType;
    private final String title;
    private final double distanceMeters;
    private final Duration movingTime;
    private final String polylineEncoded;      // Encoded polyline for mini-map
    private final int kudosCount;
    private final int commentCount;
    private final Instant createdAt;
    private final List<String> photoUrls;      // Activity photos (up to 4)
}
```

---

## 3️⃣ API Design

### 1. Upload Activity (Mobile → Server)
```
POST /api/v1/activities

Request (multipart):
{
  "type": "RUN",
  "title": "Morning Run",
  "start_time": "2025-01-10T07:00:00Z",
  "end_time": "2025-01-10T07:45:00Z",
  "elapsed_time_seconds": 2700,
  "moving_time_seconds": 2580,
  "distance_meters": 8500.5,
  "elevation_gain_meters": 125.3,
  "visibility": "PUBLIC",
  "device": "iPhone 15 Pro",
  "gps_trace": "<binary GPS data — FIT/GPX/TCX format>"
}

Response (201 Created):
{
  "activity_id": "act_abc123",
  "status": "PROCESSING",
  "polyline": "encoded_polyline_string",
  "stats": {
    "avg_pace_min_per_km": 5.05,
    "avg_heart_rate": 155,
    "calories": 620
  },
  "segments_matched": "PENDING"
}
```

### 2. Get Activity Feed
```
GET /api/v1/feed?page=1&page_size=20

Response (200 OK):
{
  "feed": [
    {
      "activity_id": "act_abc123",
      "user": { "id": "usr_456", "name": "Alice", "avatar_url": "..." },
      "type": "RUN",
      "title": "Morning Run",
      "distance_meters": 8500.5,
      "moving_time_seconds": 2580,
      "avg_pace": "5:05 /km",
      "polyline": "encoded_polyline",
      "kudos_count": 12,
      "comment_count": 3,
      "created_at": "2025-01-10T07:50:00Z",
      "photos": []
    }
  ],
  "has_more": true,
  "next_cursor": "cursor_xyz"
}
```

### 3. Get Segment Leaderboard
```
GET /api/v1/segments/{segment_id}/leaderboard?filter=this_year&gender=all&page=1

Response (200 OK):
{
  "segment": { "id": "seg_789", "name": "Hawk Hill Climb", "distance": 2100, "elevation_gain": 180 },
  "leaderboard": [
    { "rank": 1, "user": "Bob", "time_seconds": 342, "date": "2025-01-05", "activity_id": "act_xyz" },
    { "rank": 2, "user": "Charlie", "time_seconds": 348, "date": "2025-01-08", "activity_id": "act_abc" },
    { "rank": 3, "user": "Alice", "time_seconds": 355, "date": "2025-01-10", "activity_id": "act_def" }
  ],
  "your_best": { "rank": 47, "time_seconds": 412, "date": "2024-12-20" },
  "total_attempts": 15234
}
```

### 4. Live Tracking
```
WebSocket: wss://live.strava.com/ws/track/{activity_id}

← Server pushes every 5 seconds:
{
  "type": "LOCATION_UPDATE",
  "activity_id": "act_abc123",
  "user_name": "Alice",
  "position": { "lat": 37.7749, "lng": -122.4194 },
  "elapsed_seconds": 1800,
  "distance_meters": 5200,
  "current_pace": "5:12 /km",
  "heart_rate": 162,
  "timestamp": "2025-01-10T07:30:00Z"
}
```

### 5. Give Kudos
```
POST /api/v1/activities/{activity_id}/kudos
Response (200 OK): { "kudos_count": 13 }
```

---

## 4️⃣ Data Flow

### Flow 1: Record Activity (Offline-First) → Upload → Process
```
1. User starts activity on mobile app
   ↓
2. MOBILE (OFFLINE-FIRST):
   a. GPS tracking starts: record 1 point/second
   b. All data stored locally (SQLite / file) — works without connectivity
   c. Heart rate, cadence collected from Bluetooth sensors
   d. User can see live stats (pace, distance, time) locally
   ↓
3. User stops activity
   ↓
4. UPLOAD (when connectivity available):
   a. Compress GPS trace (1 hour ≈ 108 KB → ~30 KB compressed)
   b. Upload to server: POST /activities with GPS data
   c. If upload fails → queue for retry (exponential backoff)
   d. Server responds with activity_id
   ↓
5. SERVER PROCESSING (async pipeline):
   a. Parse GPS data → compute stats (distance, pace, elevation, splits)
   b. Generate simplified polyline for map display
   c. Generate static map image (for feed cards)
   d. Match GPS trace against segments → compute segment efforts
   e. Update user's personal records (PR checks)
   f. Fan out activity to followers' feeds
   ↓
6. Followers see activity in their feed
```

### Flow 2: Activity Feed
```
1. User opens app → loads feed
   ↓
2. GET /feed → Feed Service
   ↓
3. Feed Service:
   a. Check Redis cache for user's pre-computed feed
   b. Cache hit → return cached feed (< 50ms)
   c. Cache miss → query feed table:
      SELECT * FROM feed WHERE user_id = ? ORDER BY created_at DESC LIMIT 20
   d. Hydrate: fetch activity details, user info, kudos counts
   ↓
4. Return feed items with encoded polylines, stats, social data
```

### Flow 3: Segment Matching
```
1. New activity uploaded with GPS trace
   ↓
2. Segment Matching Service (async worker):
   a. Load activity's GPS trace (list of lat/lon points)
   b. Query: which segments are geographically near this activity?
      → Geospatial index: find segments within bounding box of activity
   c. For each candidate segment:
      - Check if activity's GPS trace passes through the segment
      - If match: compute elapsed time for the segment portion
   d. Create SegmentEffort records
   ↓
3. Update segment leaderboards:
   a. Insert new effort into leaderboard (sorted by time)
   b. Check if this is user's personal record for this segment
   c. Check if this is a KOM/QOM (King/Queen of Mountain — overall record)
   ↓
4. Notify user: "New segment PR on Hawk Hill! 🏆 Ranked #47"
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     MOBILE APP (iOS / Android)                              │
│                                                                              │
│  • Offline-first GPS recording (SQLite local storage)                      │
│  • Activity upload when connectivity available                             │
│  • Feed display with cached data                                           │
│  • Live tracking broadcaster/viewer                                        │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ REST API + WebSocket (live tracking)
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      API GATEWAY / LOAD BALANCER                            │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
                ┌───────────────┼───────────────┐
                ▼               ▼               ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│  ACTIVITY SERVICE │ │  FEED SERVICE     │ │  LIVE TRACKING    │
│                   │ │                   │ │  SERVICE           │
│  • Upload/parse   │ │  • Generate feed  │ │                   │
│  • Compute stats  │ │  • Fan-out on     │ │  • WebSocket      │
│  • Store GPS      │ │    activity create│ │  • Location relay  │
│  • Trigger async  │ │  • Cursor-based   │ │  • 300K deliveries│
│    processing     │ │    pagination     │ │    /sec            │
└────────┬─────────┘ └────────┬─────────┘ └────────┬──────────┘
         │                    │                     │
    ┌────▼────┐         ┌────▼────┐          ┌────▼──────┐
    │ SEGMENT  │         │ KUDOS/   │          │ REDIS     │
    │ MATCHING │         │ COMMENTS │          │ PUB/SUB   │
    │ SERVICE  │         │ SERVICE  │          │           │
    │ (async)  │         │          │          │ Live pos  │
    └────┬─────┘         └──────────┘          │ updates   │
         │                                      └───────────┘
         ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                         DATA STORES                                         │
│                                                                              │
│  ┌──────────────┐  ┌────────────────┐  ┌──────────────────┐              │
│  │ PostgreSQL    │  │ S3 / Object    │  │ Redis             │              │
│  │               │  │ Storage        │  │                   │              │
│  │ • Activities  │  │ • GPS traces   │  │ • Feed cache      │              │
│  │ • Users       │  │ • Map images   │  │ • Leaderboard     │              │
│  │ • Segments    │  │ • Photos       │  │   (sorted sets)   │              │
│  │ • Efforts     │  │               │  │ • Live positions   │              │
│  │ • Feed table  │  │ ~100 TB/year  │  │ • Session cache    │              │
│  │ • Social graph│  │ (compressed)   │  │                   │              │
│  └──────────────┘  └────────────────┘  └──────────────────┘              │
│                                                                              │
│  ┌──────────────┐  ┌────────────────┐                                    │
│  │ PostGIS /     │  │ Kafka           │                                    │
│  │ Geospatial    │  │                 │                                    │
│  │ Index         │  │ • Activity      │                                    │
│  │               │  │   uploaded events│                                   │
│  │ • Segment     │  │ • Feed fanout   │                                    │
│  │   locations   │  │ • Notifications │                                    │
│  │ • Activity    │  │                 │                                    │
│  │   bounding box│  │                 │                                    │
│  └──────────────┘  └────────────────┘                                    │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Activity Service** | Upload, parse, compute stats, store | Java/Go on K8s | 20+ pods, stateless |
| **Feed Service** | Social feed generation, fan-out | Java/Go on K8s | 10+ pods, cached |
| **Live Tracking Service** | WebSocket relay for live positions | Go on K8s | 50+ pods for 500K connections |
| **Segment Matching Service** | Async GPS-to-segment matching | Python/Java workers | Auto-scaled, queue-based |
| **PostgreSQL** | Activity metadata, users, social graph | PostgreSQL + read replicas | Sharded by user_id |
| **S3** | GPS traces, map images, photos | S3 | ~100 TB/year |
| **Redis** | Feed cache, leaderboards, live positions | Redis Cluster | Sorted sets for leaderboards |
| **PostGIS** | Geospatial queries (segment matching) | PostgreSQL + PostGIS extension | Indexed by geography |
| **Kafka** | Activity events, feed fan-out, async jobs | Apache Kafka | 5+ partitions |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Offline-First Activity Recording

**The Problem**: Users run and cycle in areas with no cellular signal (mountains, forests, tunnels). The app must record the full GPS trace locally and sync when connectivity returns.

```java
public class OfflineActivityRecorder {

    /**
     * Offline-first strategy:
     * 
     * 1. ALL recording happens locally — never depends on network
     *    - GPS points saved to local SQLite database every second
     *    - Heart rate, cadence from Bluetooth sensors (local)
     *    - Live stats (pace, distance) computed on-device
     * 
     * 2. On activity completion:
     *    - Full activity saved to local DB (SQLite)
     *    - Queued for upload (background job)
     *    - User sees activity immediately in their local history
     * 
     * 3. Upload when connectivity available:
     *    - Background upload service monitors network state
     *    - Exponential backoff on failure (1s, 5s, 30s, 5m, 30m)
     *    - Resumable upload for large GPS files
     *    - Prioritize: newest activities first
     * 
     * 4. Conflict resolution:
     *    - Activity ID generated on device (UUID)
     *    - Server deduplicates by activity_id
     *    - If same activity uploaded twice → idempotent (ignored)
     */
    
    // On device: save GPS point every second
    public void recordPoint(TrackPoint point) {
        localDb.insertTrackPoint(currentActivityId, point);
        
        // Update running stats (on-device computation)
        updateLocalStats(point);
    }
    
    // On activity stop: save locally, queue upload
    public void finishActivity() {
        Activity activity = buildActivityFromLocal(currentActivityId);
        localDb.saveActivity(activity);
        
        // Queue for upload (will happen when network available)
        uploadQueue.enqueue(activity.getActivityId());
    }
    
    // Background: upload when connected
    public void processUploadQueue() {
        while (!uploadQueue.isEmpty()) {
            String activityId = uploadQueue.peek();
            Activity activity = localDb.getActivity(activityId);
            byte[] gpsData = localDb.getGPSTrace(activityId);
            
            try {
                apiClient.uploadActivity(activity, gpsData);
                uploadQueue.dequeue(); // Success: remove from queue
                localDb.markUploaded(activityId);
            } catch (NetworkException e) {
                // Retry later with backoff
                scheduleRetry(activityId, computeBackoff());
                return; // Stop processing queue, try later
            }
        }
    }
}
```

---

### Deep Dive 2: Segment Matching — GPS Trace to Segment Correlation

**The Problem**: When a user uploads a 1-hour cycling activity with 3600 GPS points, we must check if their route passes through any of 30M defined segments. This must be efficient — we can't compare against every segment.

```java
public class SegmentMatcher {

    /**
     * Segment matching pipeline:
     * 
     * 1. Compute activity's bounding box (min/max lat/lon)
     * 2. Geospatial query: find segments that INTERSECT with this bounding box
     *    → PostGIS: ST_Intersects(segment.geom, activity_bbox)
     *    → Reduces 30M segments to ~100-500 candidates
     * 
     * 3. For each candidate segment:
     *    a. Check if activity's trace follows the segment's path
     *       → Fréchet distance or Hausdorff distance < threshold
     *    b. If match: find the entry/exit points in the activity trace
     *    c. Compute elapsed time between entry and exit
     *    d. Create SegmentEffort with the time
     * 
     * 4. Update leaderboard for matched segments
     */
    
    public List<SegmentEffort> matchSegments(Activity activity, List<TrackPoint> trace) {
        // Step 1: Bounding box of activity
        BoundingBox bbox = computeBoundingBox(trace);
        
        // Step 2: Find candidate segments (PostGIS spatial query)
        List<Segment> candidates = segmentStore.findSegmentsInBoundingBox(bbox);
        // Typically 100-500 candidates for a 1-hour ride
        
        List<SegmentEffort> efforts = new ArrayList<>();
        
        for (Segment segment : candidates) {
            // Step 3: Check if trace matches segment path
            MatchResult match = matchTraceToSegment(trace, segment.getPath());
            
            if (match.isMatch()) {
                // Step 4: Compute effort time
                Duration effortTime = Duration.between(
                    trace.get(match.getEntryIndex()).getTimestamp(),
                    trace.get(match.getExitIndex()).getTimestamp());
                
                SegmentEffort effort = SegmentEffort.builder()
                    .segmentId(segment.getSegmentId())
                    .activityId(activity.getActivityId())
                    .userId(activity.getUserId())
                    .elapsedTime(effortTime)
                    .startTime(trace.get(match.getEntryIndex()).getTimestamp())
                    .build();
                
                efforts.add(effort);
                
                // Update leaderboard
                updateLeaderboard(segment.getSegmentId(), effort);
            }
        }
        
        return efforts;
    }
    
    /**
     * Segment matching algorithm:
     * Check if the activity trace "follows" the segment path.
     * 
     * - Segment is a polyline of N points
     * - Activity trace is a polyline of M points (M >> N typically)
     * - For each segment point, find the nearest activity point
     * - If ALL segment points have a nearby activity point (< 25m) in order → MATCH
     */
    private MatchResult matchTraceToSegment(List<TrackPoint> trace, List<LatLng> segmentPath) {
        int traceIdx = 0;
        int entryIdx = -1;
        int exitIdx = -1;
        
        for (int segIdx = 0; segIdx < segmentPath.size(); segIdx++) {
            LatLng segPoint = segmentPath.get(segIdx);
            boolean found = false;
            
            // Scan forward in trace for a point near this segment point
            for (int i = traceIdx; i < trace.size(); i++) {
                double dist = haversineDistance(segPoint, trace.get(i));
                if (dist < 25.0) { // Within 25 meters
                    if (entryIdx == -1) entryIdx = i;
                    exitIdx = i;
                    traceIdx = i + 1;
                    found = true;
                    break;
                }
            }
            
            if (!found) return MatchResult.noMatch(); // Didn't follow segment
        }
        
        return new MatchResult(true, entryIdx, exitIdx);
    }
}
```

---

### Deep Dive 3: Feed Fan-Out — Push vs Pull for Activity Feed

**The Problem**: When a user uploads an activity, it should appear in all followers' feeds. With 100M users and average 100 followers, how do we efficiently distribute activities?

```java
public class FeedService {

    /**
     * Hybrid fan-out strategy:
     * 
     * For NORMAL users (< 1000 followers): PUSH (fan-out on write)
     *   - When user uploads activity → write to each follower's feed table
     *   - Followers load feed with simple SELECT
     *   - Fast reads, higher write cost
     * 
     * For CELEBRITIES (> 1000 followers): PULL (fan-out on read)
     *   - Don't fan out on write (too expensive: 1M followers = 1M writes)
     *   - When follower loads feed → merge celebrity activities at read time
     *   - Slower reads, but avoids massive write amplification
     * 
     * Feed table: (user_id, activity_id, created_at) — one row per feed item per user
     */
    
    public void onActivityUploaded(Activity activity) {
        String userId = activity.getUserId();
        List<String> followers = socialGraph.getFollowers(userId);
        
        if (followers.size() < 1000) {
            // PUSH: fan out to all followers
            for (String followerId : followers) {
                feedStore.insertFeedItem(followerId, activity.getActivityId(), activity.getUploadedAt());
            }
        } else {
            // PULL: just store the activity; followers will fetch on read
            // Activity is already stored in activities table
            // Feed merge happens at read time
        }
        
        // Also: invalidate feed cache for followers
        for (String followerId : followers) {
            redis.del("feed_cache:" + followerId);
        }
    }
    
    public List<FeedItem> getFeed(String userId, String cursor, int limit) {
        // Step 1: Check cache
        List<FeedItem> cached = redis.get("feed_cache:" + userId);
        if (cached != null) return cached;
        
        // Step 2: Get pushed feed items
        List<FeedItem> pushed = feedStore.getFeedItems(userId, cursor, limit);
        
        // Step 3: Merge celebrity activities (pull)
        List<String> celebrities = socialGraph.getCelebrityFollowings(userId);
        if (!celebrities.isEmpty()) {
            List<FeedItem> celeb = activityStore.getRecentActivities(celebrities, cursor, limit);
            pushed = mergeSorted(pushed, celeb, limit);
        }
        
        // Step 4: Cache
        redis.setex("feed_cache:" + userId, 300, pushed); // 5 min TTL
        
        return pushed;
    }
}
```

---

### Deep Dive 4: Live Tracking — Real-Time Position Broadcasting

**The Problem**: When a user is on a live activity, their friends should see their position update every 5 seconds on a map. With 500K concurrent live activities and 3 watchers each, we need 300K deliveries/sec.

```java
public class LiveTrackingService {

    /**
     * Architecture:
     * 1. Active user's phone sends location update every 5 seconds via HTTP POST
     *    (not WebSocket — phone may have intermittent connectivity)
     * 2. Server stores latest position in Redis: key = "live:{activity_id}"
     * 3. Watchers connect via WebSocket, subscribe to activity
     * 4. Server pushes updates to watchers every 5 seconds via WebSocket
     * 
     * Why HTTP for upload (not WebSocket):
     *   Phone is moving, may switch cell towers, lose signal briefly.
     *   Stateless HTTP is more resilient than maintaining WebSocket while running.
     *   5-second interval is fine for HTTP (not high-frequency).
     */
    
    // Athlete's phone sends position update
    public void receiveLocationUpdate(String activityId, TrackPoint point) {
        // Store in Redis (overwrite previous — only latest matters)
        redis.set("live:" + activityId, serialize(point), Duration.ofMinutes(5));
        
        // Publish to watchers via Redis Pub/Sub
        redis.publish("live_channel:" + activityId, serialize(point));
    }
    
    // Watcher subscribes via WebSocket
    public void onWatcherConnected(WebSocket ws, String activityId) {
        // Send current position immediately
        String current = redis.get("live:" + activityId);
        if (current != null) ws.send(current);
        
        // Subscribe to updates
        redis.subscribe("live_channel:" + activityId, update -> {
            ws.send(update);
        });
    }
}
```

**Scaling**:
```
500K live activities × 1 update/5sec = 100K Redis writes/sec
100K updates × 3 watchers = 300K WebSocket deliveries/sec
Redis Pub/Sub handles this with 5-10 nodes

Each WebSocket server: ~10K connections → 30-50 servers for watchers
```

---

### Deep Dive 5: Leaderboard Design — Redis Sorted Sets for Segment Rankings

**The Problem**: Each of 30M segments has a leaderboard ranked by time. Users want to see top-10 overall, their personal rank, and filter by year/gender/age. Redis Sorted Sets provide O(log N) insert and O(K) top-K queries.

```java
public class SegmentLeaderboardService {

    /**
     * Redis Sorted Set per segment:
     *   Key: "leaderboard:{segment_id}:all_time"
     *   Score: elapsed_time_seconds (lower = better)
     *   Member: "{user_id}:{effort_id}"
     * 
     * Multiple sorted sets for filtered views:
     *   "leaderboard:{segment_id}:2025"         (this year)
     *   "leaderboard:{segment_id}:2025:male"    (this year, male)
     *   "leaderboard:{segment_id}:2025:female"  (this year, female)
     * 
     * Personal best per user per segment:
     *   Key: "pr:{user_id}:{segment_id}"
     *   Value: { effort_id, time_seconds, date }
     */
    
    public void updateLeaderboard(SegmentEffort effort) {
        String segmentId = effort.getSegmentId();
        String userId = effort.getUserId();
        double timeSeconds = effort.getElapsedTime().getSeconds();
        String member = userId + ":" + effort.getEffortId();
        
        // Update all-time leaderboard (keep only personal best per user)
        String prKey = "pr:" + userId + ":" + segmentId;
        String existingPR = redis.get(prKey);
        
        if (existingPR == null || timeSeconds < parsePRTime(existingPR)) {
            // New personal record!
            
            // Remove old entry from leaderboard
            if (existingPR != null) {
                redis.zrem("leaderboard:" + segmentId + ":all_time", 
                    userId + ":" + parsePREffortId(existingPR));
            }
            
            // Add new best
            redis.zadd("leaderboard:" + segmentId + ":all_time", timeSeconds, member);
            redis.set(prKey, effort.getEffortId() + ":" + timeSeconds);
            
            // Also update year-specific leaderboard
            String yearKey = "leaderboard:" + segmentId + ":" + Year.now();
            redis.zadd(yearKey, timeSeconds, member);
            redis.expire(yearKey, Duration.ofDays(400)); // TTL > 1 year
            
            effort.setPersonalRecord(true);
            
            // Check KOM/QOM
            Long rank = redis.zrank("leaderboard:" + segmentId + ":all_time", member);
            if (rank != null && rank == 0) {
                notifyKOM(effort); // King/Queen of the Mountain!
            }
        }
    }
    
    public LeaderboardResult getLeaderboard(String segmentId, String filter, int page, int pageSize) {
        String key = "leaderboard:" + segmentId + ":" + filter;
        int start = (page - 1) * pageSize;
        
        // Get top entries with scores
        Set<ZSetOperations.TypedTuple<String>> entries = 
            redis.zrangeWithScores(key, start, start + pageSize - 1);
        
        long totalEntries = redis.zcard(key);
        
        return new LeaderboardResult(entries, totalEntries, page, pageSize);
    }
    
    // Get user's rank on a segment
    public UserSegmentRank getUserRank(String userId, String segmentId) {
        String prKey = "pr:" + userId + ":" + segmentId;
        String pr = redis.get(prKey);
        if (pr == null) return null;
        
        String member = userId + ":" + parsePREffortId(pr);
        Long rank = redis.zrank("leaderboard:" + segmentId + ":all_time", member);
        
        return new UserSegmentRank(rank + 1, parsePRTime(pr));
    }
}
```

**Memory estimation**:
```
30M segments × average 100 leaderboard entries each = 3B entries
Each entry: ~50 bytes (member string + score)
Total: 3B × 50 bytes = ~150 GB

Filtered leaderboards (year, gender): 3x multiplier → ~450 GB
Personal records: 100M users × avg 50 segments = 5B entries × 30 bytes = ~150 GB

Total Redis memory: ~750 GB → Redis cluster with 25 nodes × 32 GB each
```

---

### Deep Dive 6: Activity Statistics Computation

**The Problem**: When a user uploads a GPS trace, we must compute: distance, average pace, splits (per-km/per-mile), elevation profile, heart rate zones, calories, and personal records. All within seconds of upload.

```java
public class ActivityStatsComputer {

    /**
     * Stats computed from GPS trace:
     * 
     * 1. Distance: sum of haversine distances between consecutive points
     * 2. Elevation gain: sum of positive elevation changes (filtered for noise)
     * 3. Moving time: exclude periods where speed < threshold (stopped at light)
     * 4. Pace/Speed: distance / moving_time
     * 5. Splits: per-km or per-mile distance/time breakdowns
     * 6. Heart rate zones: time in each HR zone (Z1-Z5)
     * 7. Calories: estimated from HR, weight, duration
     * 8. Personal records: check if this is user's best 1K, 5K, 10K, half, marathon
     */
    
    public ActivityStats computeStats(List<TrackPoint> trace, ActivityType type) {
        ActivityStats stats = new ActivityStats();
        
        // Distance (haversine between consecutive points)
        double totalDistance = 0;
        for (int i = 1; i < trace.size(); i++) {
            totalDistance += haversineDistance(trace.get(i-1), trace.get(i));
        }
        stats.setDistanceMeters(totalDistance);
        
        // Elevation gain (only positive changes, smoothed to reduce GPS noise)
        List<Double> smoothedElevation = smoothElevation(trace, 10); // 10-point moving average
        double elevGain = 0;
        for (int i = 1; i < smoothedElevation.size(); i++) {
            double diff = smoothedElevation.get(i) - smoothedElevation.get(i-1);
            if (diff > 0) elevGain += diff;
        }
        stats.setElevationGainMeters(elevGain);
        
        // Moving time (exclude stops)
        Duration movingTime = Duration.ZERO;
        double stopThreshold = type == ActivityType.RUN ? 0.5 : 1.0; // m/s
        for (int i = 1; i < trace.size(); i++) {
            double speed = haversineDistance(trace.get(i-1), trace.get(i)); // 1 second apart
            if (speed > stopThreshold) {
                movingTime = movingTime.plusSeconds(1);
            }
        }
        stats.setMovingTime(movingTime);
        
        // Splits (per kilometer)
        List<Split> splits = computeSplits(trace, 1000); // 1000m per split
        stats.setSplits(splits);
        
        // Heart rate zones
        if (hasHeartRate(trace)) {
            Map<HRZone, Duration> hrZones = computeHRZones(trace);
            stats.setHeartRateZones(hrZones);
            stats.setAvgHeartRate(computeAvgHR(trace));
            stats.setMaxHeartRate(computeMaxHR(trace));
        }
        
        return stats;
    }
    
    // Check personal records (best efforts at standard distances)
    public List<PersonalRecord> checkPersonalRecords(String userId, List<TrackPoint> trace) {
        List<PersonalRecord> newPRs = new ArrayList<>();
        
        // Standard distances to check
        double[] checkDistances = {1000, 5000, 10000, 21097.5, 42195}; // 1K, 5K, 10K, HM, M
        
        for (double targetDist : checkDistances) {
            // Find fastest time for this distance within the trace
            Duration bestTime = findBestEffort(trace, targetDist);
            if (bestTime == null) continue; // Activity not long enough
            
            // Compare with user's existing PR
            PersonalRecord existingPR = prStore.getUserPR(userId, targetDist);
            if (existingPR == null || bestTime.compareTo(existingPR.getTime()) < 0) {
                PersonalRecord newPR = new PersonalRecord(targetDist, bestTime);
                prStore.updatePR(userId, targetDist, newPR);
                newPRs.add(newPR);
            }
        }
        
        return newPRs;
    }
}
```

---

### Deep Dive 7: GPS Data Storage & Polyline Encoding

**The Problem**: Storing 3600 GPS points (108 KB) per activity × 7M activities/day = 770 GB/day of raw GPS data. We need efficient storage and fast map rendering.

```java
public class GPSStorageManager {

    /**
     * Two representations of GPS data:
     * 
     * 1. FULL TRACE: stored in S3 as compressed binary (FIT/protobuf format)
     *    - All 3600 points with full precision
     *    - Used for: detailed activity view, stats recomputation, segment matching
     *    - Size: ~30 KB compressed (from 108 KB raw)
     *    - Stored in S3: cheap ($0.023/GB), durable, CDN-friendly
     * 
     * 2. SIMPLIFIED POLYLINE: stored in PostgreSQL as encoded string
     *    - Douglas-Peucker simplification: 3600 points → ~200 points
     *    - Google Encoded Polyline format: ~500 bytes
     *    - Used for: feed map thumbnails, activity list previews
     *    - Stored inline with activity metadata (fast query)
     */
    
    public GPSStorageResult storeGPSData(String activityId, List<TrackPoint> trace) {
        // Step 1: Store full trace in S3 (compressed protobuf)
        byte[] compressed = compressTrace(trace); // ~30 KB
        String s3Url = s3.putObject("gps-traces/" + activityId + ".pb.gz", compressed);
        
        // Step 2: Simplify for map display (Douglas-Peucker, tolerance ~20m)
        List<LatLng> simplified = douglasPeucker(trace, 20.0);
        // 3600 points → ~200 points
        
        // Step 3: Encode as Google Polyline (compact string)
        String encoded = GooglePolylineEncoder.encode(simplified);
        // ~200 points → ~500 character string
        
        // Step 4: Generate static map image for feed card
        String mapImageUrl = mapService.generateStaticMap(simplified, 300, 200);
        s3.putObject("map-images/" + activityId + ".png", mapImageUrl);
        
        return new GPSStorageResult(s3Url, encoded, mapImageUrl);
    }
    
    /**
     * Douglas-Peucker line simplification:
     * Recursively removes points that are within tolerance of the simplified line.
     * Reduces 3600 points to ~200 while preserving route shape.
     */
    private List<LatLng> douglasPeucker(List<TrackPoint> points, double toleranceMeters) {
        if (points.size() <= 2) return toLatLng(points);
        
        // Find point farthest from line between first and last
        double maxDist = 0;
        int maxIdx = 0;
        LatLng start = toLatLng(points.get(0));
        LatLng end = toLatLng(points.get(points.size() - 1));
        
        for (int i = 1; i < points.size() - 1; i++) {
            double dist = perpendicularDistance(toLatLng(points.get(i)), start, end);
            if (dist > maxDist) { maxDist = dist; maxIdx = i; }
        }
        
        if (maxDist > toleranceMeters) {
            // Recursively simplify both halves
            List<LatLng> left = douglasPeucker(points.subList(0, maxIdx + 1), toleranceMeters);
            List<LatLng> right = douglasPeucker(points.subList(maxIdx, points.size()), toleranceMeters);
            left.addAll(right.subList(1, right.size())); // Avoid duplicate at split point
            return left;
        } else {
            // All points within tolerance → just keep endpoints
            return List.of(start, end);
        }
    }
}
```

**Storage cost comparison**:
```
Approach A: Store all points in PostgreSQL
  7M activities/day × 3600 points × 30 bytes = 770 GB/day in DB
  Annual: 280 TB in PostgreSQL → extremely expensive ($$$)

Approach B: S3 for full trace + PostgreSQL for polyline (our choice)
  S3: 7M × 30 KB = 210 GB/day → $0.023/GB → $5/day
  PostgreSQL: 7M × 500 bytes = 3.5 GB/day → manageable
  Annual: S3 ~77 TB ($1,800/year), PostgreSQL ~1.3 TB
  
  100x cheaper than storing everything in PostgreSQL
```

---

### Deep Dive 8: Wearable Integration — Garmin, Apple Watch, Fitbit

**The Problem**: Many users record activities on wearables (Garmin watch, Apple Watch) and sync to Strava. Each device uses a different data format and sync protocol.

```java
public class WearableIntegrationService {

    /**
     * Integration patterns:
     * 
     * 1. DIRECT API PUSH (Garmin Connect, Wahoo):
     *    - Garmin syncs activity to Garmin Connect cloud
     *    - Garmin calls Strava webhook: POST /webhooks/garmin with activity data
     *    - Strava receives FIT file, processes same as mobile upload
     * 
     * 2. OAUTH PULL (Apple Health, Fitbit):
     *    - User authorizes Strava to read from Apple Health / Fitbit API
     *    - Strava periodically polls for new activities
     *    - Downloads activity in device-native format
     * 
     * 3. FILE IMPORT (manual):
     *    - User exports GPX/FIT/TCX file from device
     *    - Uploads to Strava via web/app
     * 
     * Common format handling:
     *   FIT (Garmin) → parse binary protocol → TrackPoint[]
     *   GPX (universal XML) → parse XML → TrackPoint[]
     *   TCX (Garmin legacy) → parse XML → TrackPoint[]
     *   Apple Health → HealthKit API → TrackPoint[]
     */
    
    // Webhook: Garmin pushes new activity
    public void onGarminWebhook(GarminWebhookPayload payload) {
        String userId = lookupUserByGarminId(payload.getGarminUserId());
        
        // Download FIT file from Garmin's CDN
        byte[] fitFile = garminClient.downloadActivity(payload.getActivityUrl());
        
        // Parse FIT → standard TrackPoint format
        List<TrackPoint> trace = FITParser.parse(fitFile);
        
        // Create activity (same pipeline as mobile upload)
        Activity activity = Activity.builder()
            .activityId(UUID.randomUUID().toString())
            .userId(userId)
            .type(mapActivityType(payload.getActivityType()))
            .device(new DeviceInfo("Garmin " + payload.getDeviceModel()))
            .build();
        
        activityService.processNewActivity(activity, trace);
    }
    
    // Deduplication: same activity synced from phone AND watch
    public boolean isDuplicateActivity(String userId, Instant startTime, double distance) {
        // Check if user already has an activity within 5 minutes and 10% distance
        List<Activity> recent = activityStore.getActivitiesByTime(userId, 
            startTime.minus(Duration.ofMinutes(5)), startTime.plus(Duration.ofMinutes(5)));
        
        for (Activity existing : recent) {
            double distanceDiff = Math.abs(existing.getDistanceMeters() - distance) / distance;
            if (distanceDiff < 0.1) {
                // Same activity from different source → skip
                return true;
            }
        }
        return false;
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Activity storage** | GPS traces in S3, metadata in PostgreSQL | GPS data is large (108 KB); S3 is cheap. Metadata needs queries (PostgreSQL) |
| **Offline recording** | SQLite on device, upload queue with backoff | Users need recording in airplane mode; device-generated UUIDs for dedup |
| **Segment matching** | PostGIS bounding box → sequential path matching | Reduces 30M segments to ~500 candidates via spatial index; 25m tolerance |
| **Feed** | Hybrid push (normal) + pull (celebrity) | Push for fast reads; pull avoids write amplification for celebrity athletes |
| **Leaderboard** | Redis Sorted Sets per segment | O(log N) insert, O(K) top-K query; natural ranking by time |
| **Live tracking** | HTTP upload (athlete) + WebSocket (watchers) + Redis Pub/Sub | HTTP is resilient for moving athlete; WebSocket for real-time watcher delivery |

## Interview Talking Points

1. **"Offline-first: all recording on device, sync later"** — SQLite stores GPS points every second. Upload queued with exponential backoff. UUID generated on device for dedup. User sees activity instantly in local history.
2. **"Segment matching: PostGIS bbox → 500 candidates → sequential scan"** — Spatial index reduces 30M segments to ~500 in bounding box. Then sequentially check if trace follows segment path within 25m tolerance.
3. **"Hybrid feed fan-out"** — Push for users with < 1000 followers (fast reads). Pull for celebrities (avoid 1M writes per activity). Merge at read time for celebrity activities.
4. **"Redis Sorted Sets for segment leaderboards"** — ZADD with time as score. ZRANGEBYSCORE for top-K. O(log N) insert. Per-segment leaderboard fits in memory.
5. **"HTTP for athlete location, WebSocket for watchers"** — Athlete's phone on unstable cellular → stateless HTTP every 5s is more resilient than WebSocket. Watchers on stable Wi-Fi → WebSocket for push delivery.
6. **"GPS traces in S3, metadata in PostgreSQL"** — 108 KB GPS trace per activity → S3 at $0.023/GB. Activity metadata (500 bytes) needs queries → PostgreSQL with indexes.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Instagram** | Same social feed pattern | Instagram is image-centric; Strava is GPS/activity-centric |
| **Uber** | Same real-time location tracking | Uber is point-to-point rides; Strava tracks full routes + segments |
| **Google Maps** | Same geospatial data | Maps is navigation; Strava is activity recording + social |
| **Leaderboard** | Same ranking pattern | Generic leaderboard is simpler; Strava has geospatial segment matching |
| **WhatsApp** | Same real-time delivery | WhatsApp is text messaging; Strava live tracking is location broadcasting |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **GPS storage** | S3 (object store) | DynamoDB | Smaller files; need indexed queries on GPS data |
| **Geospatial** | PostGIS | Elasticsearch geo queries | If also need full-text search on activities |
| **Leaderboard** | Redis Sorted Set | PostgreSQL with index | Simpler; fewer moving parts; smaller scale |
| **Feed** | PostgreSQL + Redis cache | Cassandra | Higher write throughput for fan-out at scale |
| **Live tracking** | Redis Pub/Sub | Kafka | If need replay; if watchers disconnect and reconnect |
| **Async jobs** | Kafka + workers | SQS + Lambda | Serverless; simpler ops; auto-scaling |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Strava Engineering Blog (Segment Matching Architecture)
- PostGIS Spatial Indexing Documentation
- Offline-First Mobile App Architecture Patterns
- Google Encoded Polyline Algorithm
- Fan-Out on Write vs Read (Instagram Engineering)
- Redis Sorted Sets for Real-Time Leaderboards
