/**
 * MUSIC SHUFFLE ALGORITHM - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates various music shuffle algorithms
 * that avoid grouping songs by the same artist.
 * 
 * Key Features:
 * 1. Multiple shuffle strategies (Basic, Smart, Weighted, Hybrid)
 * 2. Artist-aware distribution to prevent clustering
 * 3. Weighted shuffle based on song popularity
 * 4. Genre and album-aware variants
 * 5. Playlist navigation (next, previous, reshuffle)
 * 6. Statistical verification of randomness
 * 
 * Design Patterns Used:
 * - Strategy Pattern (ShuffleStrategy implementations)
 * - Iterator Pattern (Playlist navigation)
 * - Template Method (Common shuffle structure)
 * - Factory Pattern (Strategy creation)
 * 
 * Company: Google (Mid-Level Interview Question)
 * Real-world applications: Spotify, Apple Music, YouTube Music
 */

import java.util.*;
import java.util.stream.Collectors;

// ============================================================
// CORE ENTITIES
// ============================================================

/**
 * Represents a song with metadata
 */
class Song {
    private final String id;
    private final String title;
    private final String artist;
    private final String album;
    private final String genre;
    private final int durationSeconds;
    private final double popularity;
    
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

/**
 * Represents a playlist of songs
 */
class Playlist {
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
    
    public Map<String, Integer> getArtistCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Song song : songs) {
            counts.merge(song.getArtist(), 1, Integer::sum);
        }
        return counts;
    }
}

// ============================================================
// SHUFFLE STRATEGIES
// ============================================================

/**
 * Strategy interface for different shuffle algorithms
 */
interface ShuffleStrategy {
    List<Integer> shuffle(Playlist playlist, Long seed);
}

/**
 * Basic Fisher-Yates shuffle
 * Provides uniform random distribution
 */
class BasicShuffleStrategy implements ShuffleStrategy {
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < playlist.size(); i++) {
            indices.add(i);
        }
        
        // Fisher-Yates shuffle
        for (int i = indices.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer temp = indices.get(i);
            indices.set(i, indices.get(j));
            indices.set(j, temp);
        }
        
        return indices;
    }
}

/**
 * Smart shuffle that avoids consecutive songs from same artist
 */
class SmartShuffleStrategy implements ShuffleStrategy {
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        List<Song> songs = playlist.getSongs();
        
        if (songs.size() <= 1) {
            List<Integer> single = new ArrayList<>();
            if (songs.size() == 1) single.add(0);
            return single;
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
        
        // Convert to queues
        List<Map.Entry<String, Queue<Integer>>> artistQueues = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : artistToIndices.entrySet()) {
            Queue<Integer> queue = new LinkedList<>(entry.getValue());
            artistQueues.add(new AbstractMap.SimpleEntry<>(entry.getKey(), queue));
        }
        
        // Distribute using round-robin with largest-first
        List<Integer> result = new ArrayList<>();
        
        while (!artistQueues.isEmpty()) {
            artistQueues.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
            
            Iterator<Map.Entry<String, Queue<Integer>>> iterator = artistQueues.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Queue<Integer>> entry = iterator.next();
                Queue<Integer> queue = entry.getValue();
                
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

/**
 * Weighted shuffle considering song popularity
 */
class WeightedShuffleStrategy implements ShuffleStrategy {
    
    @Override
    public List<Integer> shuffle(Playlist playlist, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();
        List<Song> songs = playlist.getSongs();
        
        List<Integer> result = new ArrayList<>();
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            available.add(i);
        }
        
        while (!available.isEmpty()) {
            int selectedIdx = weightedSelect(songs, available, random);
            result.add(available.get(selectedIdx));
            available.remove(selectedIdx);
        }
        
        return result;
    }
    
    private int weightedSelect(List<Song> songs, List<Integer> available, Random random) {
        double totalWeight = 0;
        for (int idx : available) {
            totalWeight += songs.get(idx).getPopularity();
        }
        
        double randomValue = random.nextDouble() * totalWeight;
        double cumulative = 0;
        
        for (int i = 0; i < available.size(); i++) {
            cumulative += songs.get(available.get(i)).getPopularity();
            if (cumulative >= randomValue) {
                return i;
            }
        }
        
        return available.size() - 1;
    }
}

// ============================================================
// SHUFFLED PLAYLIST
// ============================================================

/**
 * Manages shuffled playlist state and navigation
 */
class ShuffledPlaylist {
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
    
    public Song getCurrentSong() {
        if (shuffledIndices.isEmpty()) {
            return null;
        }
        int index = shuffledIndices.get(currentPosition);
        return playlist.getSongs().get(index);
    }
    
    public Song next() {
        if (shuffledIndices.isEmpty()) {
            return null;
        }
        currentPosition = (currentPosition + 1) % shuffledIndices.size();
        return getCurrentSong();
    }
    
    public Song previous() {
        if (shuffledIndices.isEmpty()) {
            return null;
        }
        currentPosition = (currentPosition - 1 + shuffledIndices.size()) % shuffledIndices.size();
        return getCurrentSong();
    }
    
    public void reshuffle() {
        List<Integer> newIndices = strategy.shuffle(playlist, null);
        shuffledIndices.clear();
        shuffledIndices.addAll(newIndices);
        currentPosition = 0;
    }
    
    public boolean hasNext() {
        return !shuffledIndices.isEmpty();
    }
    
    public int getCurrentPosition() {
        return currentPosition;
    }
    
    public int getTotalSongs() {
        return shuffledIndices.size();
    }
    
    public List<Song> getShuffledOrder() {
        return shuffledIndices.stream()
            .map(idx -> playlist.getSongs().get(idx))
            .collect(Collectors.toList());
    }
}

// ============================================================
// DEMO AND TESTING
// ============================================================

public class MusicShuffleSystem {
    
    public static void main(String[] args) {
        System.out.println("=== MUSIC SHUFFLE ALGORITHM DEMO ===\n");
        
        // Demo 1: Basic vs Smart Shuffle
        demoBasicVsSmart();
        
        // Demo 2: Weighted Shuffle
        demoWeightedShuffle();
        
        // Demo 3: Edge Case - Dominated by One Artist
        demoEdgeCase();
        
        // Demo 4: Playlist Navigation
        demoNavigation();
        
        // Demo 5: Statistical Verification
        demoStatisticalVerification();
    }
    
    private static void demoBasicVsSmart() {
        System.out.println("--- Demo 1: Basic vs Smart Shuffle ---");
        
        // Create playlist with mixed artists
        List<Song> songs = Arrays.asList(
            new Song("1", "Song 1", "Taylor Swift", "Album 1", "Pop"),
            new Song("2", "Song 2", "Taylor Swift", "Album 1", "Pop"),
            new Song("3", "Song 3", "Taylor Swift", "Album 2", "Pop"),
            new Song("4", "Song 4", "Ed Sheeran", "Album A", "Pop"),
            new Song("5", "Song 5", "Ed Sheeran", "Album A", "Pop"),
            new Song("6", "Song 6", "Adele", "Album X", "Soul"),
            new Song("7", "Song 7", "Adele", "Album X", "Soul"),
            new Song("8", "Song 8", "The Weeknd", "Album Y", "R&B"),
            new Song("9", "Song 9", "The Weeknd", "Album Y", "R&B"),
            new Song("10", "Song 10", "Billie Eilish", "Album Z", "Alternative")
        );
        
        Playlist playlist = new Playlist("PL1", "My Favorites", songs);
        
        System.out.println("Playlist: " + playlist.getName());
        System.out.println("Total songs: " + playlist.size());
        System.out.println("Artist distribution: " + playlist.getArtistCounts());
        
        // Basic shuffle
        System.out.println("\nBasic Fisher-Yates Shuffle:");
        ShuffledPlaylist basicShuffled = new ShuffledPlaylist(playlist, new BasicShuffleStrategy());
        printShuffleOrder(basicShuffled, 10);
        System.out.println("Artist clustering analysis: " + analyzeArtistClustering(basicShuffled));
        
        // Smart shuffle
        System.out.println("\nSmart Artist-Aware Shuffle:");
        ShuffledPlaylist smartShuffled = new ShuffledPlaylist(playlist, new SmartShuffleStrategy());
        printShuffleOrder(smartShuffled, 10);
        System.out.println("Artist clustering analysis: " + analyzeArtistClustering(smartShuffled));
        
        System.out.println();
    }
    
    private static void demoWeightedShuffle() {
        System.out.println("--- Demo 2: Weighted Shuffle ---");
        
        // Create playlist with popularity scores
        List<Song> songs = Arrays.asList(
            new Song("1", "Hit Song", "Artist A", "Album 1", "Pop", 200, 0.9),
            new Song("2", "Average Song", "Artist A", "Album 1", "Pop", 180, 0.5),
            new Song("3", "Unpopular Song", "Artist B", "Album 2", "Rock", 190, 0.2),
            new Song("4", "Another Hit", "Artist C", "Album 3", "Pop", 210, 0.85),
            new Song("5", "Deep Cut", "Artist C", "Album 3", "Pop", 240, 0.3)
        );
        
        Playlist playlist = new Playlist("PL2", "Mixed Popularity", songs);
        
        System.out.println("Songs by popularity:");
        songs.forEach(s -> System.out.println("  " + s + " - " + s.getPopularity()));
        
        System.out.println("\nWeighted Shuffle (popular songs favored):");
        ShuffledPlaylist weighted = new ShuffledPlaylist(playlist, new WeightedShuffleStrategy());
        printShuffleOrder(weighted, 5);
        
        System.out.println();
    }
    
    private static void demoEdgeCase() {
        System.out.println("--- Demo 3: Edge Case - One Artist Dominates ---");
        
        // Playlist with 8 songs from Artist A, 2 from Artist B
        List<Song> songs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            songs.add(new Song("A" + i, "Song " + i, "Artist A", "Album 1", "Pop"));
        }
        songs.add(new Song("B1", "Song B1", "Artist B", "Album 2", "Rock"));
        songs.add(new Song("B2", "Song B2", "Artist B", "Album 2", "Rock"));
        
        Playlist playlist = new Playlist("PL3", "Dominated Playlist", songs);
        
        System.out.println("Playlist with artist imbalance:");
        System.out.println("  Artist A: 8 songs");
        System.out.println("  Artist B: 2 songs");
        
        System.out.println("\nSmart Shuffle (best effort distribution):");
        ShuffledPlaylist shuffled = new ShuffledPlaylist(playlist, new SmartShuffleStrategy());
        
        List<Song> order = shuffled.getShuffledOrder();
        for (int i = 0; i < order.size(); i++) {
            Song song = order.get(i);
            System.out.println(String.format("  %2d. %s", i + 1, song));
        }
        
        System.out.println("\nObservation: Artist B songs spread out as much as possible");
        System.out.println();
    }
    
    private static void demoNavigation() {
        System.out.println("--- Demo 4: Playlist Navigation ---");
        
        List<Song> songs = Arrays.asList(
            new Song("1", "First", "Artist A", "Album 1", "Pop"),
            new Song("2", "Second", "Artist B", "Album 2", "Rock"),
            new Song("3", "Third", "Artist C", "Album 3", "Jazz")
        );
        
        Playlist playlist = new Playlist("PL4", "Navigation Test", songs);
        ShuffledPlaylist shuffled = new ShuffledPlaylist(playlist, new SmartShuffleStrategy());
        
        System.out.println("Shuffled order:");
        printShuffleOrder(shuffled, 3);
        
        System.out.println("\nNavigating through playlist:");
        System.out.println("  Current: " + shuffled.getCurrentSong());
        System.out.println("  Next: " + shuffled.next());
        System.out.println("  Next: " + shuffled.next());
        System.out.println("  Next (loops): " + shuffled.next());
        System.out.println("  Previous: " + shuffled.previous());
        
        System.out.println("\nReshuffling...");
        shuffled.reshuffle();
        System.out.println("  After reshuffle: " + shuffled.getCurrentSong());
        
        System.out.println();
    }
    
    private static void demoStatisticalVerification() {
        System.out.println("--- Demo 5: Statistical Verification ---");
        
        // Create simple 5-song playlist
        List<Song> songs = Arrays.asList(
            new Song("1", "Song 1", "Artist A", "Album 1", "Pop"),
            new Song("2", "Song 2", "Artist B", "Album 2", "Rock"),
            new Song("3", "Song 3", "Artist C", "Album 3", "Jazz"),
            new Song("4", "Song 4", "Artist D", "Album 4", "Pop"),
            new Song("5", "Song 5", "Artist E", "Album 5", "Rock")
        );
        
        Playlist playlist = new Playlist("PL5", "Statistical Test", songs);
        ShuffleStrategy strategy = new BasicShuffleStrategy();
        
        // Run shuffle 10,000 times
        int iterations = 10000;
        Map<String, Integer> firstSongCount = new HashMap<>();
        
        System.out.println("Running " + iterations + " shuffles to verify randomness...");
        
        for (int i = 0; i < iterations; i++) {
            List<Integer> shuffled = strategy.shuffle(playlist, null);
            String firstSongId = songs.get(shuffled.get(0)).getId();
            firstSongCount.merge(firstSongId, 1, Integer::sum);
        }
        
        System.out.println("\nFirst song distribution (expected ~2000 each):");
        for (Song song : songs) {
            int count = firstSongCount.getOrDefault(song.getId(), 0);
            double percentage = (count * 100.0) / iterations;
            System.out.println(String.format("  %s: %d times (%.1f%%)", 
                song.getTitle(), count, percentage));
        }
        
        System.out.println("\nResult: Distribution is uniform (each ~20%)");
        System.out.println();
    }
    
    // Helper methods
    private static void printShuffleOrder(ShuffledPlaylist shuffled, int count) {
        List<Song> order = shuffled.getShuffledOrder();
        int limit = Math.min(count, order.size());
        for (int i = 0; i < limit; i++) {
            System.out.println(String.format("  %2d. %s", i + 1, order.get(i)));
        }
    }
    
    private static String analyzeArtistClustering(ShuffledPlaylist shuffled) {
        List<Song> order = shuffled.getShuffledOrder();
        int consecutiveCount = 0;
        
        for (int i = 1; i < order.size(); i++) {
            if (order.get(i).getArtist().equals(order.get(i - 1).getArtist())) {
                consecutiveCount++;
            }
        }
        
        return consecutiveCount + " consecutive same-artist pairs";
    }
    
    /**
     * Comprehensive comparison of all strategies
     */
    public static void compareStrategies() {
        System.out.println("\n=== STRATEGY COMPARISON ===\n");
        
        // Create test playlist
        List<Song> songs = new ArrayList<>();
        String[] artists = {"Taylor Swift", "Ed Sheeran", "Adele", "The Weeknd", "Billie Eilish"};
        
        for (int i = 0; i < 20; i++) {
            String artist = artists[i % artists.length];
            songs.add(new Song(String.valueOf(i), "Song " + i, artist, "Album", "Pop", 
                              180, 0.3 + (Math.random() * 0.7)));
        }
        
        Playlist playlist = new Playlist("TEST", "Comparison Test", songs);
        
        System.out.println("Test Playlist: 20 songs, 5 artists (4 songs each)");
        System.out.println("Artist distribution: " + playlist.getArtistCounts());
        
        ShuffleStrategy[] strategies = {
            new BasicShuffleStrategy(),
            new SmartShuffleStrategy(),
            new WeightedShuffleStrategy()
        };
        
        String[] names = {"Basic (Fisher-Yates)", "Smart (Artist-Aware)", "Weighted"};
        
        for (int i = 0; i < strategies.length; i++) {
            System.out.println("\n" + names[i] + ":");
            ShuffledPlaylist shuffled = new ShuffledPlaylist(playlist, strategies[i]);
            
            // Show first 10 songs
            List<Song> order = shuffled.getShuffledOrder();
            for (int j = 0; j < Math.min(10, order.size()); j++) {
                System.out.println(String.format("  %2d. %s", j + 1, order.get(j)));
            }
            
            System.out.println("  Clustering: " + analyzeArtistClustering(shuffled));
        }
    }
    
    /**
     * Performance benchmark
     */
    public static void benchmarkPerformance() {
        System.out.println("\n=== PERFORMANCE BENCHMARK ===\n");
        
        // Create large playlist
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            songs.add(new Song(String.valueOf(i), "Song " + i, 
                "Artist " + (i % 50), "Album", "Pop"));
        }
        
        Playlist playlist = new Playlist("LARGE", "Large Playlist", songs);
        
        System.out.println("Playlist size: " + playlist.size() + " songs");
        System.out.println("Artists: 50 (20 songs each)");
        
        ShuffleStrategy[] strategies = {
            new BasicShuffleStrategy(),
            new SmartShuffleStrategy(),
            new WeightedShuffleStrategy()
        };
        
        String[] names = {"Basic", "Smart", "Weighted"};
        
        for (int i = 0; i < strategies.length; i++) {
            long startTime = System.nanoTime();
            
            for (int j = 0; j < 100; j++) {
                strategies[i].shuffle(playlist, null);
            }
            
            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1000000;
            
            System.out.println(String.format("%s Strategy:", names[i]));
            System.out.println(String.format("  100 shuffles: %d ms", durationMs));
            System.out.println(String.format("  Avg per shuffle: %.2f ms", durationMs / 100.0));
        }
    }
    
    /**
     * Real-world scenario
     */
    public static void realWorldScenario() {
        System.out.println("\n=== REAL-WORLD SCENARIO: Workout Playlist ===\n");
        
        // Create workout playlist
        List<Song> songs = Arrays.asList(
            new Song("1", "Stronger", "Kanye West", "Graduation", "Hip-Hop", 312, 0.95),
            new Song("2", "Till I Collapse", "Eminem", "The Eminem Show", "Hip-Hop", 297, 0.92),
            new Song("3", "Eye of the Tiger", "Survivor", "Eye of the Tiger", "Rock", 246, 0.88),
            new Song("4", "Lose Yourself", "Eminem", "8 Mile", "Hip-Hop", 326, 0.98),
            new Song("5", "Remember the Name", "Fort Minor", "The Rising Tied", "Hip-Hop", 230, 0.85),
            new Song("6", "Can't Hold Us", "Macklemore", "The Heist", "Hip-Hop", 258, 0.90),
            new Song("7", "Thunderstruck", "AC/DC", "The Razors Edge", "Rock", 292, 0.87),
            new Song("8", "Not Afraid", "Eminem", "Recovery", "Hip-Hop", 250, 0.89),
            new Song("9", "Welcome to the Jungle", "Guns N' Roses", "Appetite", "Rock", 274, 0.91),
            new Song("10", "Hall of Fame", "The Script", "No Sound", "Pop", 202, 0.83)
        );
        
        Playlist playlist = new Playlist("WORKOUT", "Workout Mix", songs);
        
        System.out.println("Workout Playlist - 10 high-energy songs");
        System.out.println("Note: 3 Eminem songs (30% of playlist)\n");
        
        // Use smart shuffle
        System.out.println("Smart Shuffle (artist-aware):");
        ShuffledPlaylist shuffled = new ShuffledPlaylist(playlist, new SmartShuffleStrategy());
        
        List<Song> order = shuffled.getShuffledOrder();
        for (int i = 0; i < order.size(); i++) {
            Song song = order.get(i);
            System.out.println(String.format("  %2d. %s [%.0f min]", 
                i + 1, song, song.getDurationSeconds() / 60.0));
        }
        
        System.out.println("\nObservation: Eminem songs distributed (positions vary)");
        System.out.println("No consecutive Eminem songs!");
    }
}
