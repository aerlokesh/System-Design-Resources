
/**
 * INTERVIEW-READY Tic Tac Toe System
 * Time to complete: 30-40 minutes
 * Focus: Game state, win detection logic
 */

// ==================== Player ====================
enum Player {
    X, O, NONE
}

// ==================== Game State ====================
enum GameState {
    IN_PROGRESS, X_WON, O_WON, DRAW
}

// ==================== Board ====================
class Board {
    private final Player[][] grid;
    private final int size;

    public Board(int size) {
        this.size = size;
        this.grid = new Player[size][size];
        
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                grid[i][j] = Player.NONE;
            }
        }
    }

    public boolean makeMove(int row, int col, Player player) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            return false;
        }

        if (grid[row][col] != Player.NONE) {
            return false;
        }

        grid[row][col] = player;
        return true;
    }

    public Player getCell(int row, int col) {
        return grid[row][col];
    }

    public boolean isFull() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (grid[i][j] == Player.NONE) {
                    return false;
                }
            }
        }
        return true;
    }

    public void display() {
        System.out.println();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                char symbol = grid[i][j] == Player.NONE ? '-' : grid[i][j].toString().charAt(0);
                System.out.print(" " + symbol + " ");
                if (j < size - 1) System.out.print("|");
            }
            System.out.println();
            if (i < size - 1) {
                System.out.println("-----------");
            }
        }
        System.out.println();
    }

    public Player checkWinner() {
        // Check rows
        for (int i = 0; i < size; i++) {
            if (grid[i][0] != Player.NONE && 
                grid[i][0] == grid[i][1] && grid[i][1] == grid[i][2]) {
                return grid[i][0];
            }
        }

        // Check columns
        for (int j = 0; j < size; j++) {
            if (grid[0][j] != Player.NONE && 
                grid[0][j] == grid[1][j] && grid[1][j] == grid[2][j]) {
                return grid[0][j];
            }
        }

        // Check diagonals
        if (grid[0][0] != Player.NONE && 
            grid[0][0] == grid[1][1] && grid[1][1] == grid[2][2]) {
            return grid[0][0];
        }

        if (grid[0][2] != Player.NONE && 
            grid[0][2] == grid[1][1] && grid[1][1] == grid[2][0]) {
            return grid[0][2];
        }

        return Player.NONE;
    }
}

// ==================== Tic Tac Toe Game ====================
class TicTacToeGame {
    private final Board board;
    private Player currentPlayer;
    private GameState state;

    public TicTacToeGame() {
        this.board = new Board(3);
        this.currentPlayer = Player.X;
        this.state = GameState.IN_PROGRESS;
    }

    public boolean makeMove(int row, int col) {
        if (state != GameState.IN_PROGRESS) {
            System.out.println("✗ Game already over");
            return false;
        }

        if (!board.makeMove(row, col, currentPlayer)) {
            System.out.println("✗ Invalid move");
            return false;
        }

        System.out.println("✓ Player " + currentPlayer + " placed at (" + row + "," + col + ")");

        // Check for winner
        Player winner = board.checkWinner();
        if (winner != Player.NONE) {
            state = (winner == Player.X) ? GameState.X_WON : GameState.O_WON;
            System.out.println("🎉 Player " + winner + " wins!");
            return true;
        }

        // Check for draw
        if (board.isFull()) {
            state = GameState.DRAW;
            System.out.println("🤝 Game is a draw!");
            return true;
        }

        // Switch player
        currentPlayer = (currentPlayer == Player.X) ? Player.O : Player.X;
        return true;
    }

    public void display() {
        board.display();
    }

    public GameState getState() {
        return state;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }
}

// ==================== Demo ====================
public class TicTacToeInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Tic Tac Toe Demo ===\n");

        TicTacToeGame game = new TicTacToeGame();

        System.out.println("Initial board:");
        game.display();

        // Play game
        System.out.println("--- Playing Game ---");
        game.makeMove(0, 0);  // X
        game.display();

        game.makeMove(1, 1);  // O
        game.display();

        game.makeMove(0, 1);  // X
        game.display();

        game.makeMove(2, 2);  // O
        game.display();

        game.makeMove(0, 2);  // X wins!
        game.display();

        System.out.println("Final state: " + game.getState());

        System.out.println("\n✅ Demo complete!");
    }
}
