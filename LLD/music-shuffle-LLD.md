# Music Shuffle Algorithm - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Shuffle Algorithms](#shuffle-algorithms)
4. [Core Entities and Relationships](#core-entities-and-relationships)
5. [Class Design](#class-design)
6. [Complete Implementation](#complete-implementation)
7. [Extensibility](#extensibility)
8. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Music Shuffle Algorithm?**

A music shuffle algorithm randomizes the playback order of songs in a playlist while avoiding undesirable patterns, such as playing multiple songs by the same artist consecutively. Unlike simple random shuffling, "smart shuffle" considers artist distribution, genre diversity, and recently played songs to create a better listening experience.

**Real-World Examples:**
- Spotify's Smart Shuffle
- Apple Music's Shuffle
- YouTube Music's Shuffle
- Amazon Music's Shuffle

**Core Challenges:**
- **Artist Clustering:** Avoid consecutive songs from same artist
- **True Randomness:** Still feel random, not predictable
- **Edge Cases:** Playlist dominated by one artist
- **Performance:** Shuffle large playlists (1000+ songs) efficiently
- **Fairness:** Give all songs equal play opportunity
- **Repeatability:** Option to reshuffle vs continue existing shuffle

---

## Requirements

### Functional Requirements

1. **Shuffle Operations**
   - Generate shuffled playlist from original playlist
   - Avoid consecutive songs by same artist
   - Support re-shuffle (generate new order)
   - Get next song in shuffled order

2. **Artist Distribution**
   - Maximize spacing between same-artist songs
   - Handle playlists dominated by single artist
   - Gracefully degrade when perfect spacing impossible

3. **Playback Control**
   - Get current song
   - Get next song
   - Get previous song
   - Skip to specific position

4. **Shuffle Strategies**
   - Basic shuffle (Fisher-Yates)
   - Smart shuffle (artist-aware)
   - Weighted shuffle (consider popularity)
   - Genre-aware shuffle

5. **State Management**
   - Remember shuffle order
   - Track current position
   - Support multiple playlists

### Non-Functional Requirements

1. **Performance**
   - Shuffle operation: O(n) time
   - Next/Previous: O(1) time
   - Memory: O(n) for shuffled indices

2. **Quality**
   - Uniform distribution (all songs equally likely)
   - Avoid predictable patterns
   - Smooth artist transitions

3. **Usability**
   - Simple API (shuffle, next, previous)
   - Configurable constraints
   - Repeatable with seed

### Out of Scope

- Audio playback implementation
- Music streaming and downloading
- Playlist creation and management
- User preferences and learning
- Cross-device synchronization
- Social features (sharing playlists)

---

## Shuffle Algorithms

### 1. Fisher-Yates Shuffle (Basic)

**Algorithm:** Randomly swap elements to create uniform random permutation.

**Implementation:**
```
for i from n-1 down to 1:
    j = random(0, i)
    swap(array[i], array[j])
```

**Pros:**
- ✅ Perfectly uniform distribution
- ✅ O(n) time, O(1) extra space
- ✅ Simple and efficient
- ✅ Industry standard

**Cons:**
- ❌ May cluster same-artist songs
- ❌ No awareness of song attributes
- ❌ Purely random (no "smartness")

**Best For:**
- Quick implementation
- No constraints needed
- Small playlists

### 2. Artist-Aware Shuffle (Smart)

**Algorithm:** Group by artist, interleave artists, shuffle within groups.

**Steps:**
1. Group songs by artist
2. Sort groups by size (largest first)
3. Round-robin pick from groups
4. Add randomness within each group

**Pros:**
- ✅ Maximizes artist spacing
- ✅ Prevents consecutive same-artist songs
- ✅ Still feels random

**Cons:**
- ❌ More complex than Fisher-Yates
- ❌ May be too predictable
- ❌ Harder with highly imbalanced playlists

**Best For:**
- Production music apps
- User experience optimization
- Playlists with multiple artists

### 3. Weighted Shuffle

**Algorithm:** Consider song popularity/user preferences in shuffle.

**Implementation:**
```
Each song has weight (e.g., play count, likes)
Probability ∝ weight
Use weighted random sampling
```

**Pros:**
- ✅ Personalized experience
- ✅ Promotes discovery of liked songs
- ✅ Can combine with artist-aware

**Cons:**
- ❌ Less random feeling
- ❌ May over-play popular songs
- ❌ Requires weight data

**Best For:**
- Personalized playlists
- Discovery features
- Smart recommendations

### 4. Constrained Shuffle

**Algorithm:** Fisher-Yates with constraint checking and backtracking.

**Steps:**
1. Start with Fisher-Yates
2. Check if new position violates constraints
3. If violated, try different swap
4. Backtrack if needed

**Pros:**
- ✅ Handles arbitrary constraints
- ✅ Flexible and extensible
- ✅ Still maintains good randomness

**Cons:**
- ❌ May fail with impossible constraints
- ❌ Backtracking can be slow
- ❌ Complex implementation

**Best For:**
- Complex constraint requirements
- Multiple simultaneous constraints
- Research/experimental features

---

## Core Entities and Relationships

### Key Entities

1. **Song**
   - Track ID, title, artist, album
   - Duration, genre, popularity
   - Metadata for filtering

2. **Playlist**
   - List of songs
   - Original order
   - Metadata (name, creator)

3. **ShuffleStrategy (Interface)**
   - Different shuffle algorithms
   - Implementations: Basic, Smart, Weighted
   - Generate shuffled indices

4. **ShuffledPlaylist**
   - Manages shuffle state
   - Tracks current position
   - Provides navigation (next, previous)

5. **ArtistDistributor**
   - Helper for smart shuffle
   - Groups songs by artist
   - Distributes evenly

6. **ShuffleHistory**
   - Recently played songs
   - Avoid repetition across shuffles
   - Configurable history size

### Entity Relationships

```
┌─────────────────────────────────────────────┐
│        ShuffledPlaylist                      │
│  - original playlist                         │
│  - shuffled indices                          │
│  - current position                          │
│  - shuffle strategy                          │
└───────────────┬─────────────────────────────┘
                │ uses
                ▼
    ┌───────────────────────────┐
    │   ShuffleStrategy         │
    │   <<interface>>           │
    │  - shuffle()              │
    └───────────┬───────────────┘
                │ implements
        ┌───────┴────────┬──────────┬──────────┐
        │                │          │          │
    ┌───▼────────┐  ┌────▼─────┐  ┌▼─────┐  ┌─▼──────┐
    │  Basic     │  │  Smart   │  │Weight│  │Constr- │
    │  Shuffle   │  │  Shuffle │  │Shuffle  │ ained  │
    └────────────┘  └────┬─────┘  └──────┘  └────────┘
                         │ uses
                         ▼
                ┌────────────────┐
                │ ArtistDistrib- │
                │    utor        │
                └────────────────┘

┌─────────────┐      contained in    ┌──────────────┐
│    Song     │◄─────────────────────│   Playlist   │
│  - id       │                      └──────────────┘
│  - title    │
│  - artist   │
│  - genre    │
└─────────────┘
```

---

## Class Design

### 1. Song

```java
/**
 * Represents a song with metadata
 */
public class Song {
    private final String id;
    private final String title;
    private final String artist;
    private final String album;
    private final String genre;
    private final int durationSeconds;
    private final double popularity; // 0.0 to 1.0
    
    public Song(String id, String title, String artist, String album, String genre) {
        this(id, title, artist, album, genre, 180, 0.5);
    }
    
    public Song(String id, String title, String artist, String album, 
                String genre, int duration, double popularity) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.genre = genre;
        this.durationSeconds = duration;
        this.popularity = popularity;
    }
    
    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getGenre() { return genre; }
    public int getDurationSeconds() { return durationSeconds; }
    public double getPopularity() { return popularity; }
    
    @Override
    public String toString() {
        return String.format("%s - %s", title, artist);
    }
}
```

### 2. Playlist

```java
/**
 * Represents a playlist of songs
 */
public class Playlist {
    private final String id;
    private final String name;
    private final List<Song> songs;
    
    public Playlist(String id, String name, List<Song> songs) {
        this.id = id;
        this.name = name;
        this.songs = new ArrayList<>(songs);
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public List<Song> getSongs() { return Collections.unmodifiableList(songs); }
    public int size() { return songs.size(); }
    
    /**
     * Get artist distribution in playlist
     */
    public Map<String, Integer> getArtistCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Song song : songs) {
            counts.merge(song.getArtist(), 1, Integer::sum);
        }
        return counts;
    }
}
```

### 3. ShuffleStrategy Interface

```java
/**
 * Strategy interface for different shuffle algorithms
 */
public interface ShuffleStrategy {
    /**
     * Generate shuffled order of indices
     * @param playlist The playlist to shuffle
     * @param seed Random seed for reproducibility (optional)
     * @return List of shuffled indices
     */
    List<Integer> shuffle(Playlist playlist, Long seed);
}
```

### 4. ShuffledPlaylist

```java
/**
 * Manages shuffled playlist state and navigation
 */
public class ShuffledPlaylist {
    private final Playlist playlist;
    private final List<Integer> shuffledIndices;
    private int currentPosition;
    private final ShuffleStrategy strategy;
    
    public ShuffledPlaylist(Playlist playlist, ShuffleStrategy strategy) {
        this.playlist = playlist;
        this.strategy = strategy;
        this.shuffledIndices = strategy.shuffle(playlist, null);
        this.currentPosition = 0;
    }
    
    /**
     * Get current song
     */
    public Song getCurrentSong() {
        if (shuffledIndices.isEmpty()) {
            return null;
        }
        int index = shuffledIndices.get(currentPosition);
        return playlist.getSongs().get(index);
    }
    
    /**
     * Move to next song
     */
    public Song next() {
        if (shuffledIndices.isEmpty()) {
            return null;
        }
        
        currentPosition = (currentPosition + 1) % shuffledIndices.size();
        return getCurrentSong();
    }
    
    /**
     * Move to previous song
     */
    public Song previous() {
        if (shuffledIndices.isEmpty()) {
            return null;
        }
        
        currentPosition = (currentPosition - 1 + shuffledIndices.size()) % shuffledIndices.size();
        return getCurrentSong();
    }
    
    /**
     * Re-shuffle with same strategy
     */
    public void reshuffle() {
        List<Integer> newIndices = strategy.shuffle(playlist, null);
        shuffledIndices.clear();
        shuffledIndices.addAll(newIndices);
        currentPosition = 0;
    }
    
    /**
     * Check if more songs available
     */
    public boolean hasNext() {
        return !shuffledIndices.isEmpty();
    }
    
    public int getCurrentPosition() {
        return currentPosition;
    }
    
    public int getTotalSongs() {
        return shuffledIndices.size();
    }
}
```

---

## Complete Implementation

### 1. BasicShuffleStrategy (Fisher-Yates)

```java
/**
 * Basic Fisher-Yates shuffle
 * Provides uniform random distribution
 */
public class BasicShuffleStrategy implements ShuffleStrategy {
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        
        // Create array of indices [0, 1, 2, ..., n-1]
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < playlist.size(); i++) {
            indices.add(i);
        }
        
        // Fisher-Yates shuffle
        for (int i = indices.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            // Swap indices[i] and indices[j]
            Integer temp = indices.get(i);
            indices.set(i, indices.get(j));
            indices.set(j, temp);
        }
        
        return indices;
    }
}
```

### 2. SmartShuffleStrategy (Artist-Aware)

```java
/**
 * Smart shuffle that avoids consecutive songs from same artist
 * Groups songs by artist and distributes evenly
 */
public class SmartShuffleStrategy implements ShuffleStrategy {
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        List<Song> songs = playlist.getSongs();
        
        if (songs.size() <= 1) {
            return basicShuffle(playlist.size());
        }
        
        // Group songs by artist
        Map<String, List<Integer>> artistToIndices = new HashMap<>();
        for (int i = 0; i < songs.size(); i++) {
            String artist = songs.get(i).getArtist();
            artistToIndices.computeIfAbsent(artist, k -> new ArrayList<>()).add(i);
        }
        
        // Shuffle each artist's songs
        for (List<Integer> indices : artistToIndices.values()) {
            Collections.shuffle(indices, random);
        }
        
        // Convert to list of (artist, queue) sorted by queue size
        List<Map.Entry<String, Queue<Integer>>> artistQueues = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : artistToIndices.entrySet()) {
            Queue<Integer> queue = new LinkedList<>(entry.getValue());
            artistQueues.add(new AbstractMap.SimpleEntry<>(entry.getKey(), queue));
        }
        
        // Distribute songs using round-robin with largest-first strategy
        List<Integer> result = new ArrayList<>();
        
        while (!artistQueues.isEmpty()) {
            // Sort by queue size (largest first) to better distribute
            artistQueues.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
            
            // Take one song from each artist (round-robin)
            Iterator<Map.Entry<String, Queue<Integer>>> iterator = artistQueues.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Queue<Integer>> entry = iterator.next();
                Queue<Integer> queue = entry.getValue();
                
                if (!queue.isEmpty()) {
                    result.add(queue.poll());
                    
                    // Remove artist if no more songs
                    if (queue.isEmpty()) {
                        iterator.remove();
                    }
                }
            }
        }
        
        return result;
    }
    
    private List<Integer> basicShuffle(int size) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices);
        return indices;
    }
}
```

### 3. WeightedShuffleStrategy

```java
/**
 * Weighted shuffle considering song popularity
 */
public class WeightedShuffleStrategy implements ShuffleStrategy {
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        List<Song> songs = playlist.getSongs();
        
        List<Integer> result = new ArrayList<>();
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            available.add(i);
        }
        
        // Weighted sampling without replacement
        while (!available.isEmpty()) {
            int selectedIdx = weightedSelect(songs, available, random);
            result.add(available.get(selectedIdx));
            available.remove(selectedIdx);
        }
        
        return result;
    }
    
    private int weightedSelect(List<Song> songs, List<Integer> available, Random random) {
        // Calculate total weight of available songs
        double totalWeight = 0;
        for (int idx : available) {
            totalWeight += songs.get(idx).getPopularity();
        }
        
        // Random selection weighted by popularity
        double randomValue = random.nextDouble() * totalWeight;
        double cumulative = 0;
        
        for (int i = 0; i < available.size(); i++) {
            cumulative += songs.get(available.get(i)).getPopularity();
            if (cumulative >= randomValue) {
                return i;
            }
        }
        
        return available.size() - 1; // Fallback
    }
}
```

### 4. Hybrid Strategy (Smart + Weighted)

```java
/**
 * Combines artist-awareness with weighted sampling
 */
public class HybridShuffleStrategy implements ShuffleStrategy {
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        List<Song> songs = playlist.getSongs();
        
        // Group by artist
        Map<String, List<Integer>> artistGroups = new HashMap<>();
        for (int i = 0; i < songs.size(); i++) {
            artistGroups.computeIfAbsent(songs.get(i).getArtist(), 
                                        k -> new ArrayList<>()).add(i);
        }
        
        // Within each artist group, sort by popularity
        for (List<Integer> group : artistGroups.values()) {
            group.sort((a, b) -> Double.compare(
                songs.get(b).getPopularity(),
                songs.get(a).getPopularity()
            ));
        }
        
        // Distribute using smart shuffle logic
        List<Integer> result = new ArrayList<>();
        List<Queue<Integer>> queues = new ArrayList<>();
        
        for (List<Integer> group : artistGroups.values()) {
            queues.add(new LinkedList<>(group));
        }
        
        while (!queues.isEmpty()) {
            queues.sort((a, b) -> Integer.compare(b.size(), a.size()));
            
            Iterator<Queue<Integer>> iterator = queues.iterator();
            while (iterator.hasNext()) {
                Queue<Integer> queue = iterator.next();
                if (!queue.isEmpty()) {
                    result.add(queue.poll());
                    if (queue.isEmpty()) {
                        iterator.remove();
                    }
                }
            }
        }
        
        return result;
    }
}
```

---

## Extensibility

### 1. Genre-Aware Shuffle

**Avoid consecutive songs from same genre:**

```java
public class GenreAwareShuffleStrategy implements ShuffleStrategy {
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        List<Song> songs = playlist.getSongs();
        
        // Group by genre
        Map<String, List<Integer>> genreGroups = new HashMap<>();
        for (int i = 0; i < songs.size(); i++) {
            genreGroups.computeIfAbsent(songs.get(i).getGenre(), 
                                       k -> new ArrayList<>()).add(i);
        }
        
        // Shuffle within each genre
        for (List<Integer> group : genreGroups.values()) {
            Collections.shuffle(group, random);
        }
        
        // Distribute genres evenly
        return distributeGroups(genreGroups);
    }
}
```

### 2. History-Aware Shuffle

**Avoid recently played songs at start of shuffle:**

```java
public class HistoryAwareShuffleStrategy implements ShuffleStrategy {
    private final ShuffleStrategy baseStrategy;
    private final Set<String> recentlyPlayed;
    private final int historySize;
    
    public HistoryAwareShuffleStrategy(ShuffleStrategy baseStrategy, int historySize) {
        this.baseStrategy = baseStrategy;
        this.recentlyPlayed = new LinkedHashSet<>();
        this.historySize = historySize;
    }
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        List<Integer> shuffled = baseStrategy.shuffle(playlist, seed);
        List<Song> songs = playlist.getSongs();
        
        // Move recently played songs towards the end
        List<Integer> notRecent = new ArrayList<>();
        List<Integer> recent = new ArrayList<>();
        
        for (int idx : shuffled) {
            if (recentlyPlayed.contains(songs.get(idx).getId())) {
                recent.add(idx);
            } else {
                notRecent.add(idx);
            }
        }
        
        // Combine: not-recent first, then recent
        List<Integer> result = new ArrayList<>(notRecent);
        result.addAll(recent);
        
        return result;
    }
    
    public void addToHistory(String songId) {
        recentlyPlayed.add(songId);
        if (recentlyPlayed.size() > historySize) {
            // Remove oldest (first element in LinkedHashSet)
            Iterator<String> iterator = recentlyPlayed.iterator();
            iterator.next();
            iterator.remove();
        }
    }
}
```

### 3. Album-Aware Shuffle

**Keep album tracks together but shuffle albums:**

```java
public class AlbumAwareShuffleStrategy implements ShuffleStrategy {
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        List<Song> songs = playlist.getSongs();
        
        // Group by album
        Map<String, List<Integer>> albumGroups = new LinkedHashMap<>();
        for (int i = 0; i < songs.size(); i++) {
            String key = songs.get(i).getArtist() + ":" + songs.get(i).getAlbum();
            albumGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        
        // Shuffle album order
        List<String> albumKeys = new ArrayList<>(albumGroups.keySet());
        Collections.shuffle(albumKeys, random);
        
        // Keep tracks within albums in order
        List<Integer> result = new ArrayList<>();
        for (String albumKey : albumKeys) {
            result.addAll(albumGroups.get(albumKey));
        }
        
        return result;
    }
}
```

### 4. Constrained Shuffle with Backtracking

**Handle complex constraints:**

```java
public class ConstrainedShuffleStrategy implements ShuffleStrategy {
    private final int minArtistGap; // Minimum songs between same artist
    
    public ConstrainedShuffleStrategy(int minArtistGap) {
        this.minArtistGap = minArtistGap;
    }
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        
        List<Integer> result = new ArrayList<>();
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < playlist.size(); i++) {
            available.add(i);
        }
        
        Collections.shuffle(available, random);
        
        return backtrackShuffle(playlist, available, result, 0);
    }
    
    private List<Integer> backtrackShuffle(Playlist playlist, List<Integer> available,
                                           List<Integer> result, int depth) {
        if (available.isEmpty()) {
            return new ArrayList<>(result);
        }
        
        if (depth > 1000) { // Prevent infinite recursion
            // Fallback to basic shuffle
            result.addAll(available);
            return result;
        }
        
        List<Song> songs = playlist.getSongs();
        
        for (int i = 0; i < available.size(); i++) {
            int candidateIdx = available.get(i);
            
            if (canPlace(songs, result, candidateIdx)) {
                // Try placing this song
                result.add(candidateIdx);
                available.remove(i);
                
                List<Integer> solution = backtrackShuffle(playlist, available, result, depth + 1);
                if (solution != null) {
                    return solution;
                }
                
                // Backtrack
                result.remove(result.size() - 1);
                available.add(i, candidateIdx);
            }
        }
        
        return null; // No solution found
    }
    
    private boolean canPlace(List<Song> songs, List<Integer> result, int candidateIdx) {
        if (result.isEmpty()) {
            return true;
        }
        
        String candidateArtist = songs.get(candidateIdx).getArtist();
        
        // Check last N songs for same artist
        int checkCount = Math.min(minArtistGap, result.size());
        for (int i = result.size() - checkCount; i < result.size(); i++) {
            if (songs.get(result.get(i)).getArtist().equals(candidateArtist)) {
                return false;
            }
        }
        
        return true;
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- What defines "same artist"? (exact match, featuring, etc.)
- How to handle playlist with mostly one artist?
- Should shuffle be reproducible (seeded)?
- Performance requirements?
- Any other constraints (genre, recently played)?

**2. Start Simple (5 minutes)**
- Explain Fisher-Yates algorithm
- Discuss why basic shuffle insufficient
- Identify the artist clustering problem

**3. Design Smart Shuffle (15-20 minutes)**
- Group songs by artist
- Explain distribution strategy
- Implement with round-robin
- Handle edge cases

**4. Demonstrate (5 minutes)**
- Show example with sample playlist
- Walk through algorithm step-by-step
- Explain why it works

**5. Extensions (5 minutes)**
- Weighted shuffle
- Genre awareness
- History tracking
- Multiple constraints

### Common Interview Questions & Answers

**Q1: "Why not just use Fisher-Yates?"**

**A:** Fisher-Yates is perfect for uniform distribution but:
- **Problem:** May place same-artist songs consecutively
- **Example:** Playlist with 10 songs, 5 by Artist A
  - Random shuffle might give: [A1, A2, A3, B1, A4, C1, A5, ...]
  - User experience: Feels like broken shuffle
- **Solution:** Smart shuffle with artist awareness

**Q2: "What if playlist has 20 songs from Artist A and 5 from Artist B?"**

**A:** Perfect spacing impossible, gracefully degrade:
```
Strategy:
1. Place all Artist B songs first (spacing of 4-5 Artist A songs between)
2. Fill remaining with Artist A songs
3. Add slight randomization to avoid being too predictable

Result: Best possible spacing given constraints
```

**Q3: "How do you verify your shuffle is truly random?"**

**A:** Statistical tests:
```java
// Run shuffle 10,000 times
Map<String, Integer> firstSongCount = new HashMap<>();
for (int i = 0; i < 10000; i++) {
    List<Integer> shuffled = strategy.shuffle(playlist, null);
    String firstSong = playlist.getSongs().get(shuffled.get(0)).getId();
    firstSongCount.merge(firstSong, 1, Integer::sum);
}

// Each song should appear first ~1000 times (10000 / 10 songs)
// Check chi-squared test for uniform distribution
```

**Q4: "How do you handle 'next' after the last song?"**

**A:** Options:
1. **Loop:** Go back to first song (our implementation)
2. **Stop:** Return null, require manual reshuffle
3. **Auto-reshuffle:** Generate new shuffle order
4. **Smart loop:** Avoid playing same song twice consecutively

**Q5: "What about the Spotify 'Smart Shuffle' with discovery?"**

**A:** Extension of weighted shuffle:
```java
// Mix user's playlist with recommendations
List<Song> playlistSongs = getUserPlaylist();
List<Song> recommendations = getRecommendations(playlistSongs);

// Interleave with ratio (e.g., 80% playlist, 20% recommendations)
List<Song> mixed = interleave(playlistSongs, recommendations, 0.8, 0.2);

// Apply smart shuffle to mixed list
return smartShuffle(mixed);
```

### Design Patterns Used

1. **Strategy Pattern:** Different shuffle algorithms
2. **Template Method:** Common shuffle structure
3. **Builder Pattern:** Configuration objects
4. **Iterator Pattern:** Playlist navigation
5. **Factory Pattern:** Creating shuffle strategies

### Expected Level Performance

**Junior Engineer:**
- Implement Fisher-Yates shuffle
- Understand random permutation
- Basic next/previous navigation
- Simple song/playlist classes

**Mid-Level Engineer:**
- Implement artist-aware shuffle
- Handle edge cases (dominated by one artist)
- Strategy pattern for different algorithms
- Explain trade-offs clearly

**Senior Engineer:**
- Multiple shuffle strategies
- Weighted and genre-aware variants
- History tracking across shuffles
- Performance optimization
- Statistical verification
- Production considerations

### Key Trade-offs

**1. Randomness vs User Experience**
- **True Random:** May cluster artists (bad UX)
- **Smart Shuffle:** Better distribution, less random
- **Balance:** Smart shuffle with some randomness

**2. Perfect Distribution vs Performance**
- **Optimal:** Backtracking to find perfect spacing (slow)
- **Heuristic:** Round-robin distribution (fast, good enough)
- **Balance:** Smart shuffle with round-robin

**3. Simplicity vs Features**
- **Simple:** Fisher-Yates only
- **Complex:** Multiple constraints, weights, history
- **Balance:** Start simple, add features as needed

**4. Reproducibility vs Randomness**
- **Seeded:** Same shuffle every time (testing, debugging)
- **Non-seeded:** Different every time (better UX)
- **Support both:** Make seed optional

### Algorithm Comparison

| Algorithm | Time | Space | Artist Spacing | Randomness | Complexity |
|-----------|------|-------|----------------|------------|------------|
| Fisher-Yates | O(n) | O(1) | ❌ None | ✅ Perfect | Low |
| Smart Shuffle | O(n log k) | O(n) | ✅ Good | ✅ Good | Medium |
| Weighted | O(n²) | O(n) | ❌ None | ⚠️ Biased | Medium |
| Constrained | O(n!) worst | O(n) | ✅ Perfect | ✅ Good | High |

**Recommendation:** Smart Shuffle for production

### Code Quality Checklist

✅ **Correctness**
- Uniform distribution
- No duplicate songs
- All songs included

✅ **Performance**
- O(n) or better
- No unnecessary operations
- Efficient data structures

✅ **User Experience**
- No artist clustering
- Still feels random
- Handles edge cases

✅ **Extensibility**
- Strategy pattern
- Easy to add constraints
- Pluggable algorithms

✅ **Testing**
- Statistical tests
- Edge case handling
- Reproducible with seeds

---

## Summary

This Music Shuffle Algorithm demonstrates:

1. **Multiple Strategies:** Basic, Smart, Weighted, Hybrid algorithms
2. **Artist Distribution:** Intelligent spacing to avoid clustering
3. **Extensible Design:** Strategy pattern for different algorithms
4. **Production-Ready:** Handle edge cases, configurable, performant
5. **Interview Success:** Clear problem analysis, multiple solutions, trade-offs

**Key Takeaways:**
- **Fisher-Yates is foundation** but insufficient for good UX
- **Smart shuffle distributes artists** using round-robin
- **Handle edge cases** when one artist dominates
- **Strategy pattern** enables multiple algorithms
- **Balance randomness and constraints** for best experience

**Related Concepts:**
- Weighted random sampling
- Constraint satisfaction problems
- Reservoir sampling (streaming shuffle)
- Recommendation algorithms

**Real-World Applications:**
- Music streaming (Spotify, Apple Music)
- Video playlists (YouTube)
- Podcast queues
- Photo slideshows
- Carousel shuffling

---

*Last Updated: January 4, 2026*
*Difficulty Level: Medium*
*Key Focus: Algorithms, Constraint Satisfaction, User Experience*
*Company: Google (Mid-Level)*
*Interview Success Rate: High with proper algorithm analysis*
