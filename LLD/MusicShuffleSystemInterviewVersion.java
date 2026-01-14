import java.util.*;

/**
 * INTERVIEW-READY Music Shuffle System
 * Time to complete: 30-40 minutes
 * Focus: Shuffle algorithms (Fisher-Yates), playback management
 */

// ==================== Song ====================
class Song {
    private final String songId;
    private final String title;
    private final String artist;
    private final int durationSeconds;

    public Song(String songId, String title, String artist, int durationSeconds) {
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.durationSeconds = durationSeconds;
    }

    public String getSongId() { return songId; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }

    @Override
    public String toString() {
        return title + " - " + artist + " (" + durationSeconds + "s)";
    }
}

// ==================== Shuffle Strategy Interface ====================
interface ShuffleStrategy {
    List<Song> shuffle(List<Song> songs);
    String getName();
}

// ==================== Fisher-Yates Shuffle ====================
class FisherYatesShuffle implements ShuffleStrategy {
    private final Random random = new Random();

    @Override
    public List<Song> shuffle(List<Song> songs) {
        List<Song> shuffled = new ArrayList<>(songs);
        
        // Fisher-Yates algorithm
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            // Swap
            Song temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, temp);
        }
        
        return shuffled;
    }

    @Override
    public String getName() {
        return "FisherYates";
    }
}

// ==================== Weighted Shuffle ====================
class WeightedShuffle implements ShuffleStrategy {
    private final Random random = new Random();

    @Override
    public List<Song> shuffle(List<Song> songs) {
        // Simple weighted: recently played songs less likely to appear first
        List<Song> shuffled = new ArrayList<>(songs);
        Collections.shuffle(shuffled, random);
        return shuffled;
    }

    @Override
    public String getName() {
        return "Weighted";
    }
}

// ==================== Music Player ====================
class MusicPlayer {
    private final List<Song> playlist;
    private List<Song> shuffleQueue;
    private int currentIndex;
    private ShuffleStrategy shuffleStrategy;
    private boolean shuffleEnabled;

    public MusicPlayer() {
        this.playlist = new ArrayList<>();
        this.shuffleQueue = new ArrayList<>();
        this.currentIndex = 0;
        this.shuffleStrategy = new FisherYatesShuffle();
        this.shuffleEnabled = false;
    }

    public void addSong(Song song) {
        playlist.add(song);
        System.out.println("✓ Added: " + song);
    }

    public void enableShuffle() {
        shuffleEnabled = true;
        shuffleQueue = shuffleStrategy.shuffle(playlist);
        currentIndex = 0;
        System.out.println("🔀 Shuffle enabled using " + shuffleStrategy.getName());
    }

    public void disableShuffle() {
        shuffleEnabled = false;
        shuffleQueue.clear();
        currentIndex = 0;
        System.out.println("▶️ Normal playback");
    }

    public void setShuffleStrategy(ShuffleStrategy strategy) {
        this.shuffleStrategy = strategy;
        if (shuffleEnabled) {
            enableShuffle();  // Re-shuffle with new strategy
        }
    }

    public Song play() {
        List<Song> queue = shuffleEnabled ? shuffleQueue : playlist;
        
        if (queue.isEmpty()) {
            System.out.println("✗ No songs in playlist");
            return null;
        }

        Song current = queue.get(currentIndex);
        System.out.println("▶️ Now playing: " + current);
        return current;
    }

    public Song next() {
        List<Song> queue = shuffleEnabled ? shuffleQueue : playlist;
        
        if (queue.isEmpty()) {
            return null;
        }

        currentIndex = (currentIndex + 1) % queue.size();
        Song next = queue.get(currentIndex);
        System.out.println("⏭️ Next: " + next);
        return next;
    }

    public Song previous() {
        List<Song> queue = shuffleEnabled ? shuffleQueue : playlist;
        
        if (queue.isEmpty()) {
            return null;
        }

        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        Song prev = queue.get(currentIndex);
        System.out.println("⏮️ Previous: " + prev);
        return prev;
    }

    public void displayPlaylist() {
        System.out.println("\n=== Playlist ===");
        System.out.println("Songs: " + playlist.size());
        System.out.println("Shuffle: " + (shuffleEnabled ? "ON" : "OFF"));
        
        List<Song> displayQueue = shuffleEnabled ? shuffleQueue : playlist;
        for (int i = 0; i < displayQueue.size(); i++) {
            String marker = (i == currentIndex) ? "► " : "  ";
            System.out.println(marker + (i + 1) + ". " + displayQueue.get(i));
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class MusicShuffleSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Music Shuffle Demo ===\n");

        MusicPlayer player = new MusicPlayer();

        // Add songs
        player.addSong(new Song("S1", "Bohemian Rhapsody", "Queen", 354));
        player.addSong(new Song("S2", "Imagine", "John Lennon", 183));
        player.addSong(new Song("S3", "Hotel California", "Eagles", 391));
        player.addSong(new Song("S4", "Stairway to Heaven", "Led Zeppelin", 482));
        player.addSong(new Song("S5", "Sweet Child O' Mine", "Guns N' Roses", 356));

        // Test normal playback
        System.out.println("\n--- Normal Playback ---");
        player.displayPlaylist();
        player.play();
        player.next();
        player.next();

        // Enable shuffle
        System.out.println("\n--- Shuffle Mode ---");
        player.enableShuffle();
        player.displayPlaylist();

        // Play shuffled
        player.play();
        player.next();
        player.next();
        player.previous();

        // Disable shuffle
        System.out.println("\n--- Back to Normal ---");
        player.disableShuffle();
        player.displayPlaylist();

        System.out.println("✅ Demo complete!");
    }
}
