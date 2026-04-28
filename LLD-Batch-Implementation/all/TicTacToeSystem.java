import java.util.*;

/**
 * Tic-Tac-Toe Game - HELLO Interview Framework
 * 
 * Companies: Google, Amazon, Apple, Microsoft +4 more
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. O(1) win check using row/col/diag sums (not O(n²) board scan)
 * 2. Board size configurable (3×3 default, extensible to n×n)
 * 3. Clean separation: Board (state), Game (rules), Player
 */

// ==================== Enums ====================

enum CellState { EMPTY, X, O }
enum TTTGameStatus { IN_PROGRESS, X_WINS, O_WINS, DRAW }

// ==================== Player ====================

class TTTPlayer {
    private final String name;
    private final CellState mark; // X or O
    private int wins;

    TTTPlayer(String name, CellState mark) {
        this.name = name; this.mark = mark; this.wins = 0;
    }

    String getName() { return name; }
    CellState getMark() { return mark; }
    int getWins() { return wins; }
    void addWin() { wins++; }

    @Override
    public String toString() { return name + "(" + mark + ")"; }
}

// ==================== Board ====================
// O(1) win detection using row/col/diagonal sums

class TTTBoard {
    private final int size;
    private final CellState[][] grid;
    private final int[] rowSums;    // +1 for X, -1 for O
    private final int[] colSums;
    private int diagSum;            // main diagonal (\)
    private int antiDiagSum;        // anti-diagonal (/)
    private int moveCount;

    TTTBoard(int size) {
        this.size = size;
        this.grid = new CellState[size][size];
        this.rowSums = new int[size];
        this.colSums = new int[size];
        this.diagSum = 0;
        this.antiDiagSum = 0;
        this.moveCount = 0;

        for (CellState[] row : grid) Arrays.fill(row, CellState.EMPTY);
    }

    /** Place mark. Returns true if this move wins. O(1) check! */
    boolean placeMark(int row, int col, CellState mark) {
        if (!isValidMove(row, col)) return false;

        grid[row][col] = mark;
        moveCount++;

        int val = (mark == CellState.X) ? 1 : -1;

        rowSums[row] += val;
        colSums[col] += val;
        if (row == col) diagSum += val;
        if (row + col == size - 1) antiDiagSum += val;

        // Check win: any sum reaches ±size means all cells in that line are same
        return Math.abs(rowSums[row]) == size
            || Math.abs(colSums[col]) == size
            || Math.abs(diagSum) == size
            || Math.abs(antiDiagSum) == size;
    }

    boolean isValidMove(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size
            && grid[row][col] == CellState.EMPTY;
    }

    boolean isFull() { return moveCount == size * size; }
    int getSize() { return size; }

    void reset() {
        for (CellState[] row : grid) Arrays.fill(row, CellState.EMPTY);
        Arrays.fill(rowSums, 0);
        Arrays.fill(colSums, 0);
        diagSum = 0;
        antiDiagSum = 0;
        moveCount = 0;
    }

    String display() {
        StringBuilder sb = new StringBuilder("\n");
        // Column headers
        sb.append("    ");
        for (int c = 0; c < size; c++) sb.append(" ").append(c).append("  ");
        sb.append("\n");
        sb.append("   ").append("┌───".repeat(size)).append("┐\n");

        for (int r = 0; r < size; r++) {
            sb.append(" ").append(r).append(" │");
            for (int c = 0; c < size; c++) {
                char ch = switch (grid[r][c]) {
                    case X -> 'X';
                    case O -> 'O';
                    case EMPTY -> '·';
                };
                sb.append(" ").append(ch).append(" │");
            }
            sb.append("\n");
            if (r < size - 1) sb.append("   ├───".repeat(1)).append("┼───".repeat(size - 1)).append("┤\n");
        }
        sb.append("   ").append("└───".repeat(size)).append("┘\n");
        return sb.toString();
    }
}

// ==================== Game ====================

class TicTacToeGame {
    private final TTTBoard board;
    private final TTTPlayer playerX;
    private final TTTPlayer playerO;
    private TTTPlayer currentPlayer;
    private TTTGameStatus status;
    private int gamesPlayed;

    TicTacToeGame(String player1Name, String player2Name, int boardSize) {
        this.board = new TTTBoard(boardSize);
        this.playerX = new TTTPlayer(player1Name, CellState.X);
        this.playerO = new TTTPlayer(player2Name, CellState.O);
        this.currentPlayer = playerX; // X always starts
        this.status = TTTGameStatus.IN_PROGRESS;
        this.gamesPlayed = 0;
    }

    TicTacToeGame(String p1, String p2) { this(p1, p2, 3); }

    /** Make a move. Returns game status after move. */
    TTTGameStatus makeMove(int row, int col) {
        if (status != TTTGameStatus.IN_PROGRESS) {
            System.out.println("  ⚠️ Game over: " + status);
            return status;
        }

        if (!board.isValidMove(row, col)) {
            System.out.println("  ✗ Invalid move: (" + row + "," + col + ")");
            return status;
        }

        boolean won = board.placeMark(row, col, currentPlayer.getMark());
        System.out.println("  " + currentPlayer + " → (" + row + "," + col + ")");

        if (won) {
            status = (currentPlayer.getMark() == CellState.X) ? TTTGameStatus.X_WINS : TTTGameStatus.O_WINS;
            currentPlayer.addWin();
            System.out.println("  🏆 " + currentPlayer.getName() + " WINS!");
            gamesPlayed++;
        } else if (board.isFull()) {
            status = TTTGameStatus.DRAW;
            System.out.println("  🤝 DRAW!");
            gamesPlayed++;
        } else {
            currentPlayer = (currentPlayer == playerX) ? playerO : playerX;
        }
        return status;
    }

    /** Reset for new game */
    void newGame() {
        board.reset();
        status = TTTGameStatus.IN_PROGRESS;
        currentPlayer = playerX;
        System.out.println("  🔄 New game started!");
    }

    String display() { return board.display(); }
    TTTGameStatus getStatus() { return status; }
    TTTPlayer getCurrentPlayer() { return currentPlayer; }
    boolean isGameOver() { return status != TTTGameStatus.IN_PROGRESS; }

    String getScoreboard() {
        return String.format("  Scoreboard: %s=%d | %s=%d | Draws=%d | Games=%d",
            playerX.getName(), playerX.getWins(),
            playerO.getName(), playerO.getWins(),
            gamesPlayed - playerX.getWins() - playerO.getWins(), gamesPlayed);
    }
}

// ==================== Main Demo ====================

public class TicTacToeSystem {
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║  Tic-Tac-Toe - O(1) Win Detection           ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        // ── Scenario 1: X wins (row) ──
        System.out.println("━━━ Scenario 1: X wins by row ━━━");
        TicTacToeGame game = new TicTacToeGame("Alice", "Bob");
        game.makeMove(0, 0); // X
        game.makeMove(1, 0); // O
        game.makeMove(0, 1); // X
        game.makeMove(1, 1); // O
        game.makeMove(0, 2); // X wins row 0
        System.out.println(game.display());

        // ── Scenario 2: O wins (diagonal) ──
        System.out.println("━━━ Scenario 2: O wins by diagonal ━━━");
        game.newGame();
        game.makeMove(0, 1); // X
        game.makeMove(0, 0); // O
        game.makeMove(0, 2); // X
        game.makeMove(1, 1); // O
        game.makeMove(2, 0); // X
        game.makeMove(2, 2); // O wins diagonal
        System.out.println(game.display());

        // ── Scenario 3: Draw ──
        System.out.println("━━━ Scenario 3: Draw ━━━");
        game.newGame();
        game.makeMove(0, 0); // X
        game.makeMove(0, 1); // O
        game.makeMove(0, 2); // X
        game.makeMove(1, 0); // O
        game.makeMove(1, 1); // X
        game.makeMove(2, 0); // O
        game.makeMove(1, 2); // X
        game.makeMove(2, 2); // O
        game.makeMove(2, 1); // X → draw
        System.out.println(game.display());

        // ── Scoreboard ──
        System.out.println(game.getScoreboard());
        System.out.println();

        // ── Scenario 4: Invalid moves ──
        System.out.println("━━━ Scenario 4: Invalid moves ━━━");
        game.newGame();
        game.makeMove(0, 0); // X
        game.makeMove(0, 0); // ✗ already occupied
        game.makeMove(5, 5); // ✗ out of bounds
        System.out.println();

        // ── Scenario 5: 5×5 board ──
        System.out.println("━━━ Scenario 5: 5×5 Board ━━━");
        TicTacToeGame bigGame = new TicTacToeGame("P1", "P2", 5);
        // X wins row 2
        for (int c = 0; c < 5; c++) {
            bigGame.makeMove(2, c); // X at row 2
            if (!bigGame.isGameOver() && c < 4)
                bigGame.makeMove(3, c); // O at row 3
        }
        System.out.println(bigGame.display());

        // ── Scenario 6: Move after game over ──
        System.out.println("━━━ Scenario 6: Move after game over ━━━");
        bigGame.makeMove(0, 0); // should warn

        System.out.println("\n✅ All Tic-Tac-Toe scenarios complete.");
    }
}
