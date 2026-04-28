import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Leaderboard for Fantasy Teams - HELLO Interview Framework
 * 
 * Companies: Uber, Microsoft, WayFair, Dream11
 * Pattern: Observer + TreeMap for ranked ordering
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. TreeMap<Score, Set<PlayerId>> — O(log n) insert, O(log n) top-K
 * 2. HashMap<PlayerId, Score> — O(1) player lookup
 * 3. Observer — notify on rank changes, new leader
 * 4. ReadWriteLock — concurrent reads, exclusive writes
 */

// ==================== Player ====================

class LeaderboardPlayer {
    private final String id;
    private final String name;
    private int score;
    private int rank;

    LeaderboardPlayer(String id, String name) {
        this.id = id; this.name = name; this.score = 0; this.rank = -1;
    }

    String getId() { return id; }
    String getName() { return name; }
    int getScore() { return score; }
    int getRank() { return rank; }
    void setScore(int s) { this.score = s; }
    void setRank(int r) { this.rank = r; }

    @Override
    public String toString() { return String.format("#%d %s: %d pts", rank, name, score); }
}

// ==================== Observer ====================

interface LeaderboardObserver {
    void onScoreUpdate(LeaderboardPlayer player, int oldScore, int newScore);
    void onNewLeader(LeaderboardPlayer player);
    void onTopKChanged(List<LeaderboardPlayer> topK);
}

class ConsoleLeaderboardObserver implements LeaderboardObserver {
    @Override
    public void onScoreUpdate(LeaderboardPlayer p, int oldScore, int newScore) {
        System.out.printf("    📊 %s: %d → %d pts%n", p.getName(), oldScore, newScore);
    }

    @Override
    public void onNewLeader(LeaderboardPlayer p) {
        System.out.println("    👑 NEW LEADER: " + p.getName() + " with " + p.getScore() + " pts!");
    }

    @Override
    public void onTopKChanged(List<LeaderboardPlayer> topK) {
        // Only log when explicitly requested
    }
}

// ==================== Leaderboard ====================

class Leaderboard {
    private final Map<String, LeaderboardPlayer> players = new HashMap<>();
    // TreeMap: score → set of player IDs (descending order for scores)
    private final TreeMap<Integer, LinkedHashSet<String>> scoreBoard = new TreeMap<>(Comparator.reverseOrder());
    private final List<LeaderboardObserver> observers = new CopyOnWriteArrayList<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private String currentLeaderId = null;

    // ─── Observer ───
    void addObserver(LeaderboardObserver o) { observers.add(o); }

    // ─── Player Management ───

    void addPlayer(String id, String name) {
        rwLock.writeLock().lock();
        try {
            if (players.containsKey(id)) return;
            LeaderboardPlayer player = new LeaderboardPlayer(id, name);
            players.put(id, player);
            addToScoreBoard(id, 0);
        } finally { rwLock.writeLock().unlock(); }
    }

    /** Update player's score (absolute set) */
    void updateScore(String playerId, int newScore) {
        rwLock.writeLock().lock();
        try {
            LeaderboardPlayer player = players.get(playerId);
            if (player == null) return;

            int oldScore = player.getScore();
            if (oldScore == newScore) return;

            // Remove from old score bucket
            removeFromScoreBoard(playerId, oldScore);

            // Update and add to new bucket
            player.setScore(newScore);
            addToScoreBoard(playerId, newScore);

            // Check for new leader
            String topPlayerId = getTopPlayerId();
            boolean newLeader = topPlayerId != null && !topPlayerId.equals(currentLeaderId);
            currentLeaderId = topPlayerId;

            // Notify observers
            for (LeaderboardObserver o : observers) {
                o.onScoreUpdate(player, oldScore, newScore);
                if (newLeader) o.onNewLeader(players.get(topPlayerId));
            }
        } finally { rwLock.writeLock().unlock(); }
    }

    /** Add points to player's score (incremental) */
    void addPoints(String playerId, int points) {
        rwLock.readLock().lock();
        int currentScore;
        try {
            LeaderboardPlayer player = players.get(playerId);
            if (player == null) return;
            currentScore = player.getScore();
        } finally { rwLock.readLock().unlock(); }
        updateScore(playerId, currentScore + points);
    }

    // ─── Queries ───

    /** Get top K players */
    List<LeaderboardPlayer> getTopK(int k) {
        rwLock.readLock().lock();
        try {
            List<LeaderboardPlayer> result = new ArrayList<>();
            int rank = 1;
            for (Map.Entry<Integer, LinkedHashSet<String>> entry : scoreBoard.entrySet()) {
                for (String pid : entry.getValue()) {
                    LeaderboardPlayer p = players.get(pid);
                    p.setRank(rank);
                    result.add(p);
                    if (result.size() >= k) return result;
                    rank++;
                }
            }
            return result;
        } finally { rwLock.readLock().unlock(); }
    }

    /** Get a player's rank */
    int getRank(String playerId) {
        rwLock.readLock().lock();
        try {
            int rank = 1;
            for (Map.Entry<Integer, LinkedHashSet<String>> entry : scoreBoard.entrySet()) {
                for (String pid : entry.getValue()) {
                    if (pid.equals(playerId)) return rank;
                    rank++;
                }
            }
            return -1;
        } finally { rwLock.readLock().unlock(); }
    }

    /** Get player's score */
    int getScore(String playerId) {
        rwLock.readLock().lock();
        try {
            LeaderboardPlayer p = players.get(playerId);
            return p != null ? p.getScore() : -1;
        } finally { rwLock.readLock().unlock(); }
    }

    int size() { return players.size(); }

    // ─── Internal ───

    private void addToScoreBoard(String playerId, int score) {
        scoreBoard.computeIfAbsent(score, k -> new LinkedHashSet<>()).add(playerId);
    }

    private void removeFromScoreBoard(String playerId, int score) {
        LinkedHashSet<String> set = scoreBoard.get(score);
        if (set != null) {
            set.remove(playerId);
            if (set.isEmpty()) scoreBoard.remove(score);
        }
    }

    private String getTopPlayerId() {
        if (scoreBoard.isEmpty()) return null;
        return scoreBoard.firstEntry().getValue().iterator().next();
    }

    /** Display leaderboard */
    String display(int topK) {
        List<LeaderboardPlayer> top = getTopK(topK);
        StringBuilder sb = new StringBuilder();
        sb.append("  ┌─────┬────────────────┬────────┐\n");
        sb.append("  │ Rank│ Player         │ Score  │\n");
        sb.append("  ├─────┼────────────────┼────────┤\n");
        for (LeaderboardPlayer p : top) {
            sb.append(String.format("  │ %-4d│ %-14s │ %-6d │%n", p.getRank(), p.getName(), p.getScore()));
        }
        sb.append("  └─────┴────────────────┴────────┘\n");
        return sb.toString();
    }
}

// ==================== Main Demo ====================

public class LeaderboardSystem {
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║  Leaderboard - Observer + TreeMap Ranked Ordering    ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        Leaderboard board = new Leaderboard();
        board.addObserver(new ConsoleLeaderboardObserver());

        // ── Setup players ──
        System.out.println("━━━ Setup: Adding players ━━━");
        String[][] teamData = {
            {"t1", "Mumbai Indians"}, {"t2", "Chennai Kings"},
            {"t3", "Royal Challengers"}, {"t4", "Knight Riders"},
            {"t5", "Sunrisers"}, {"t6", "Delhi Capitals"},
            {"t7", "Punjab Kings"}, {"t8", "Rajasthan Royals"}
        };
        for (String[] t : teamData) board.addPlayer(t[0], t[1]);
        System.out.println("  Added " + board.size() + " teams\n");

        // ── Scenario 1: Initial scores ──
        System.out.println("━━━ Scenario 1: Set initial scores ━━━");
        board.updateScore("t1", 150);
        board.updateScore("t2", 200);
        board.updateScore("t3", 180);
        board.updateScore("t4", 120);
        board.updateScore("t5", 160);
        board.updateScore("t6", 140);
        board.updateScore("t7", 110);
        board.updateScore("t8", 190);
        System.out.println(board.display(8));

        // ── Scenario 2: Score updates ──
        System.out.println("━━━ Scenario 2: Score updates (incremental points) ━━━");
        board.addPoints("t1", 80);    // Mumbai gains
        board.addPoints("t3", 50);    // RCB gains
        board.addPoints("t4", 100);   // KKR big gain
        System.out.println(board.display(5));

        // ── Scenario 3: New leader ──
        System.out.println("━━━ Scenario 3: New leader emerges ━━━");
        board.updateScore("t4", 500); // KKR takes the lead!
        System.out.println(board.display(5));

        // ── Scenario 4: Rank query ──
        System.out.println("━━━ Scenario 4: Individual rank queries ━━━");
        for (String[] t : teamData) {
            System.out.printf("  %s: Rank #%d, Score=%d%n",
                t[1], board.getRank(t[0]), board.getScore(t[0]));
        }
        System.out.println();

        // ── Scenario 5: Tied scores ──
        System.out.println("━━━ Scenario 5: Tied scores ━━━");
        board.updateScore("t5", 230);
        board.updateScore("t1", 230); // tie with t5
        System.out.println(board.display(5));

        // ── Scenario 6: Concurrent updates ──
        System.out.println("━━━ Scenario 6: Concurrent updates (8 threads) ━━━");
        var executor = Executors.newFixedThreadPool(8);
        var latch = new CountDownLatch(8);
        Random rand = new Random(42);

        for (String[] t : teamData) {
            executor.submit(() -> {
                for (int i = 0; i < 10; i++) {
                    board.addPoints(t[0], rand.nextInt(50));
                }
                latch.countDown();
            });
        }

        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {}
        executor.shutdown();

        System.out.println("  After 80 concurrent updates:");
        System.out.println(board.display(8));

        System.out.println("✅ All Leaderboard scenarios complete.");
    }
}
