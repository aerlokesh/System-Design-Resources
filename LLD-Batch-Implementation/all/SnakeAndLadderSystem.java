import java.util.*;

/**
 * Snake and Ladder Game - HELLO Interview Framework
 * 
 * Companies: Goldman Sachs, Amazon, Microsoft, Google, Flipkart +4 more
 * Pattern: State/Strategy for game rules, Observer for turn events
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Board with snakes/ladders as Map<Integer, Integer> (start → end)
 * 2. Turn-based with dice roll → move → check snake/ladder → next player
 * 3. Configurable board size, number of players, snakes, ladders
 * 4. Win condition: exact landing on last cell (or overshoot = no move)
 */

// ==================== Dice ====================

class Dice {
    private final int faces;
    private final Random random;

    Dice(int faces) { this.faces = faces; this.random = new Random(); }

    int roll() { return random.nextInt(faces) + 1; }

    /** Roll with fixed seed for testing */
    static Dice seeded(int faces, long seed) {
        Dice d = new Dice(faces);
        // Use reflection-free approach: just create with seed
        return new Dice(faces) {
            private final Random r = new Random(seed);
            @Override
            public int roll() { return r.nextInt(faces) + 1; }
        };
    }
}

// ==================== Player ====================

class SnakePlayer {
    private final String name;
    private int position;
    private boolean hasWon;

    SnakePlayer(String name) {
        this.name = name; this.position = 0; this.hasWon = false;
    }

    public String getName() { return name; }
    public int getPosition() { return position; }
    public boolean hasWon() { return hasWon; }
    void setPosition(int pos) { this.position = pos; }
    void setWon() { this.hasWon = true; }

    @Override
    public String toString() { return name + " @" + position; }
}

// ==================== Board ====================

class SnakeBoard {
    private final int size;               // total cells (e.g., 100)
    private final Map<Integer, Integer> snakes;   // head → tail
    private final Map<Integer, Integer> ladders;  // bottom → top

    SnakeBoard(int size) {
        this.size = size;
        this.snakes = new HashMap<>();
        this.ladders = new HashMap<>();
    }

    void addSnake(int head, int tail) {
        if (head <= tail) throw new IllegalArgumentException("Snake head must be > tail");
        if (head >= size || tail < 1) throw new IllegalArgumentException("Invalid snake position");
        if (ladders.containsKey(head)) throw new IllegalArgumentException("Cell " + head + " already has a ladder");
        snakes.put(head, tail);
    }

    void addLadder(int bottom, int top) {
        if (bottom >= top) throw new IllegalArgumentException("Ladder bottom must be < top");
        if (top > size || bottom < 1) throw new IllegalArgumentException("Invalid ladder position");
        if (snakes.containsKey(bottom)) throw new IllegalArgumentException("Cell " + bottom + " already has a snake");
        ladders.put(bottom, top);
    }

    /** Get final position after applying snakes/ladders (chain possible) */
    int getFinalPosition(int position) {
        int maxChain = 10; // prevent infinite loops from bad config
        int chain = 0;
        while (chain++ < maxChain) {
            if (snakes.containsKey(position)) {
                int newPos = snakes.get(position);
                System.out.println("    🐍 Snake! " + position + " → " + newPos);
                position = newPos;
            } else if (ladders.containsKey(position)) {
                int newPos = ladders.get(position);
                System.out.println("    🪜 Ladder! " + position + " → " + newPos);
                position = newPos;
            } else {
                break;
            }
        }
        return position;
    }

    int getSize() { return size; }

    boolean isSnake(int pos) { return snakes.containsKey(pos); }
    boolean isLadder(int pos) { return ladders.containsKey(pos); }

    String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Board (").append(size).append(" cells)\n");
        sb.append("  Snakes: ");
        snakes.forEach((h, t) -> sb.append(h).append("→").append(t).append(" "));
        sb.append("\n  Ladders: ");
        ladders.forEach((b, t) -> sb.append(b).append("→").append(t).append(" "));
        sb.append("\n");
        return sb.toString();
    }
}

// ==================== Game ====================

class SnakeAndLadderGame {
    private final SnakeBoard board;
    private final List<SnakePlayer> players;
    private final Dice dice;
    private int currentPlayerIndex;
    private boolean gameOver;
    private SnakePlayer winner;
    private int turnCount;

    SnakeAndLadderGame(SnakeBoard board, List<String> playerNames, Dice dice) {
        this.board = board;
        this.dice = dice;
        this.players = new ArrayList<>();
        for (String name : playerNames) players.add(new SnakePlayer(name));
        this.currentPlayerIndex = 0;
        this.gameOver = false;
        this.turnCount = 0;
    }

    /** Play one turn for the current player */
    boolean playTurn() {
        if (gameOver) {
            System.out.println("  ⚠️ Game is already over!");
            return false;
        }

        SnakePlayer player = players.get(currentPlayerIndex);
        int roll = dice.roll();
        turnCount++;

        int oldPos = player.getPosition();
        int newPos = oldPos + roll;

        System.out.printf("  🎲 %s rolls %d: %d → %d", player.getName(), roll, oldPos, newPos);

        // Check overshoot — must land exactly on last cell
        if (newPos > board.getSize()) {
            System.out.println(" (overshoot! stays at " + oldPos + ")");
            nextPlayer();
            return true;
        }

        // Check win
        if (newPos == board.getSize()) {
            player.setPosition(newPos);
            player.setWon();
            gameOver = true;
            winner = player;
            System.out.println(" → 🏆 " + player.getName() + " WINS!");
            return true;
        }

        System.out.println();

        // Apply snakes/ladders
        int finalPos = board.getFinalPosition(newPos);
        player.setPosition(finalPos);

        if (finalPos != newPos) {
            System.out.println("    " + player.getName() + " lands on " + finalPos);
        }

        nextPlayer();
        return true;
    }

    /** Play until someone wins (with max turn limit) */
    SnakePlayer playToCompletion(int maxTurns) {
        while (!gameOver && turnCount < maxTurns) {
            playTurn();
        }
        if (!gameOver) System.out.println("  ⚠️ Max turns reached. No winner.");
        return winner;
    }

    private void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    // ─── Getters ───

    boolean isGameOver() { return gameOver; }
    SnakePlayer getWinner() { return winner; }
    int getTurnCount() { return turnCount; }

    String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Turn ").append(turnCount).append(" | ");
        for (SnakePlayer p : players) {
            sb.append(p.getName()).append("=").append(p.getPosition());
            if (p.hasWon()) sb.append("🏆");
            sb.append("  ");
        }
        return sb.toString();
    }

    /** Visual board display showing player positions */
    String displayBoard() {
        int size = board.getSize();
        int cols = 10;
        int rows = size / cols;
        StringBuilder sb = new StringBuilder();
        sb.append("\n");

        for (int r = rows - 1; r >= 0; r--) {
            sb.append("  ");
            // Alternate direction (boustrophedon / zigzag)
            boolean leftToRight = (r % 2 == 0);
            for (int c = 0; c < cols; c++) {
                int col = leftToRight ? c : (cols - 1 - c);
                int cellNum = r * cols + col + 1;

                // Check if any player is here
                String cellStr;
                boolean playerHere = false;
                for (SnakePlayer p : players) {
                    if (p.getPosition() == cellNum) {
                        cellStr = String.format("[%s]", p.getName().substring(0, 1));
                        sb.append(String.format("%-6s", cellStr));
                        playerHere = true;
                        break;
                    }
                }
                if (!playerHere) {
                    if (board.isSnake(cellNum)) cellStr = cellNum + "🐍";
                    else if (board.isLadder(cellNum)) cellStr = cellNum + "🪜";
                    else cellStr = String.valueOf(cellNum);
                    sb.append(String.format("%-6s", cellStr));
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

// ==================== Main Demo ====================

public class SnakeAndLadderSystem {
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  Snake and Ladder Game - Turn-based Board Game   ║");
        System.out.println("╚═══════════════════════════════════════════════════╝\n");

        // ── Setup Board ──
        SnakeBoard board = new SnakeBoard(100);

        // Snakes (head → tail)
        board.addSnake(99, 12);
        board.addSnake(87, 24);
        board.addSnake(62, 19);
        board.addSnake(54, 34);
        board.addSnake(36, 6);
        board.addSnake(17, 3);

        // Ladders (bottom → top)
        board.addLadder(2, 38);
        board.addLadder(7, 14);
        board.addLadder(8, 31);
        board.addLadder(21, 42);
        board.addLadder(28, 84);
        board.addLadder(51, 67);
        board.addLadder(71, 91);
        board.addLadder(80, 100);

        System.out.println(board.display());

        // ── Scenario 1: Simple game with 2 players ──
        System.out.println("━━━ Scenario 1: 2-Player Game ━━━");
        SnakeAndLadderGame game = new SnakeAndLadderGame(
            board, List.of("Alice", "Bob"), new Dice(6));

        SnakePlayer winner = game.playToCompletion(200);

        System.out.println("\n  " + game.getStatus());
        System.out.println("  Total turns: " + game.getTurnCount());
        if (winner != null)
            System.out.println("  Winner: " + winner.getName() + " 🎉");
        System.out.println();

        // ── Scenario 2: 4-Player Game ──
        System.out.println("━━━ Scenario 2: 4-Player Game ━━━");
        SnakeAndLadderGame game2 = new SnakeAndLadderGame(
            board, List.of("P1", "P2", "P3", "P4"), new Dice(6));

        SnakePlayer winner2 = game2.playToCompletion(500);

        System.out.println("\n  " + game2.getStatus());
        System.out.println("  Total turns: " + game2.getTurnCount());
        if (winner2 != null)
            System.out.println("  Winner: " + winner2.getName() + " 🎉");
        System.out.println();

        // ── Scenario 3: Small board (quick game) ──
        System.out.println("━━━ Scenario 3: Small board (20 cells, manual play) ━━━");
        SnakeBoard smallBoard = new SnakeBoard(20);
        smallBoard.addSnake(18, 5);
        smallBoard.addSnake(14, 3);
        smallBoard.addLadder(4, 16);
        smallBoard.addLadder(9, 19);

        System.out.println(smallBoard.display());

        SnakeAndLadderGame game3 = new SnakeAndLadderGame(
            smallBoard, List.of("X", "Y"), new Dice(6));

        // Play turn by turn
        for (int i = 0; i < 30 && !game3.isGameOver(); i++) {
            game3.playTurn();
        }
        System.out.println("\n  " + game3.getStatus());
        System.out.println();

        // ── Scenario 4: Edge case validation ──
        System.out.println("━━━ Scenario 4: Edge cases ━━━");

        // Invalid snake (head < tail)
        try {
            SnakeBoard badBoard = new SnakeBoard(100);
            badBoard.addSnake(5, 50); // invalid!
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Invalid snake caught: " + e.getMessage());
        }

        // Invalid ladder (bottom > top)
        try {
            SnakeBoard badBoard = new SnakeBoard(100);
            badBoard.addLadder(50, 5); // invalid!
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Invalid ladder caught: " + e.getMessage());
        }

        // Snake and ladder on same cell
        try {
            SnakeBoard badBoard = new SnakeBoard(100);
            badBoard.addSnake(50, 20);
            badBoard.addLadder(50, 80); // conflict!
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Conflict caught: " + e.getMessage());
        }

        // Game already over
        if (game.isGameOver()) {
            System.out.println("  ✓ Playing after game over:");
            game.playTurn(); // should print warning
        }

        System.out.println("\n✅ All Snake and Ladder scenarios complete.");
    }
}
