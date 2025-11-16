/**
 * LOW-LEVEL DESIGN: TIC TAC TOE GAME
 * 
 * This design implements a complete Tic Tac Toe game system with support for:
 * - Player vs Player (PvP)
 * - Player vs Computer (PvC) with AI
 * - Game state management
 * - Win detection
 * - Move validation
 * - Game history
 * 
 * KEY COMPONENTS:
 * 1. Board - Game board with cells
 * 2. Player - Human and Computer players
 * 3. Game - Game logic and state management
 * 4. AI Strategy - Different difficulty levels
 * 5. Game History - Move tracking and replay
 * 
 * DESIGN PATTERNS USED:
 * - Strategy Pattern: Different AI strategies
 * - State Pattern: Game state management
 * - Observer Pattern: Game event notifications
 * - Singleton Pattern: Game manager
 */

import java.util.*;
import java.time.LocalDateTime;

// ==================== ENUMS ====================

enum CellState {
    EMPTY, X, O
}

enum GameState {
    NOT_STARTED, IN_PROGRESS, X_WON, O_WON, DRAW, ABANDONED
}

enum PlayerType {
    HUMAN, COMPUTER
}

enum Difficulty {
    EASY, MEDIUM, HARD
}

// ==================== MODELS ====================

class Cell {
    private final int row;
    private final int col;
    private CellState state;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
        this.state = CellState.EMPTY;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public CellState getState() { return state; }
    
    public void setState(CellState state) { 
        this.state = state; 
    }
    
    public boolean isEmpty() { 
        return state == CellState.EMPTY; 
    }

    public void reset() {
        this.state = CellState.EMPTY;
    }

    @Override
    public String toString() {
        return switch (state) {
            case X -> "X";
            case O -> "O";
            case EMPTY -> " ";
        };
    }
}

class Board {
    private final int size;
    private final Cell[][] cells;

    public Board(int size) {
        this.size = size;
        this.cells = new Cell[size][size];
        initializeBoard();
    }

    private void initializeBoard() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                cells[i][j] = new Cell(i, j);
            }
        }
    }

    public int getSize() { return size; }

    public Cell getCell(int row, int col) {
        if (isValidPosition(row, col)) {
            return cells[row][col];
        }
        return null;
    }

    public boolean isValidPosition(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    public boolean isCellEmpty(int row, int col) {
        Cell cell = getCell(row, col);
        return cell != null && cell.isEmpty();
    }

    public boolean makeMove(int row, int col, CellState state) {
        if (isCellEmpty(row, col)) {
            cells[row][col].setState(state);
            return true;
        }
        return false;
    }

    public boolean isFull() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (cells[i][j].isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<Cell> getEmptyCells() {
        List<Cell> emptyCells = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (cells[i][j].isEmpty()) {
                    emptyCells.add(cells[i][j]);
                }
            }
        }
        return emptyCells;
    }

    public void reset() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                cells[i][j].reset();
            }
        }
    }

    public Board copy() {
        Board newBoard = new Board(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                newBoard.cells[i][j].setState(this.cells[i][j].getState());
            }
        }
        return newBoard;
    }

    public void display() {
        System.out.println("\n  0   1   2");
        for (int i = 0; i < size; i++) {
            System.out.print(i + " ");
            for (int j = 0; j < size; j++) {
                System.out.print(cells[i][j].toString());
                if (j < size - 1) System.out.print(" | ");
            }
            System.out.println();
            if (i < size - 1) {
                System.out.println("  -----------");
            }
        }
        System.out.println();
    }
}

class Move {
    private final int row;
    private final int col;
    private final CellState player;
    private final LocalDateTime timestamp;

    public Move(int row, int col, CellState player) {
        this.row = row;
        this.col = col;
        this.player = player;
        this.timestamp = LocalDateTime.now();
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public CellState getPlayer() { return player; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("%s at (%d, %d)", player, row, col);
    }
}

abstract class Player {
    protected final String name;
    protected final CellState symbol;
    protected final PlayerType type;

    public Player(String name, CellState symbol, PlayerType type) {
        this.name = name;
        this.symbol = symbol;
        this.type = type;
    }

    public String getName() { return name; }
    public CellState getSymbol() { return symbol; }
    public PlayerType getType() { return type; }

    public abstract Move makeMove(Board board);
}

class HumanPlayer extends Player {
    private final Scanner scanner;

    public HumanPlayer(String name, CellState symbol) {
        super(name, symbol, PlayerType.HUMAN);
        this.scanner = new Scanner(System.in);
    }

    @Override
    public Move makeMove(Board board) {
        while (true) {
            try {
                System.out.print(name + " (" + symbol + "), enter row (0-2): ");
                int row = scanner.nextInt();
                System.out.print(name + " (" + symbol + "), enter col (0-2): ");
                int col = scanner.nextInt();

                if (board.isValidPosition(row, col) && board.isCellEmpty(row, col)) {
                    return new Move(row, col, symbol);
                } else {
                    System.out.println("Invalid move! Cell is occupied or out of bounds.");
                }
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Please enter numbers 0-2.");
                scanner.nextLine(); // Clear buffer
            }
        }
    }
}

// ==================== AI STRATEGIES ====================

interface AIStrategy {
    Move selectMove(Board board, CellState aiSymbol);
}

class RandomAIStrategy implements AIStrategy {
    private final Random random = new Random();

    @Override
    public Move selectMove(Board board, CellState aiSymbol) {
        List<Cell> emptyCells = board.getEmptyCells();
        if (emptyCells.isEmpty()) {
            return null;
        }
        Cell cell = emptyCells.get(random.nextInt(emptyCells.size()));
        return new Move(cell.getRow(), cell.getCol(), aiSymbol);
    }
}

class MinimaxAIStrategy implements AIStrategy {
    
    @Override
    public Move selectMove(Board board, CellState aiSymbol) {
        CellState opponentSymbol = (aiSymbol == CellState.X) ? CellState.O : CellState.X;
        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;

        for (Cell cell : board.getEmptyCells()) {
            Board tempBoard = board.copy();
            tempBoard.makeMove(cell.getRow(), cell.getCol(), aiSymbol);
            
            int score = minimax(tempBoard, 0, false, aiSymbol, opponentSymbol);
            
            if (score > bestScore) {
                bestScore = score;
                bestMove = new Move(cell.getRow(), cell.getCol(), aiSymbol);
            }
        }

        return bestMove;
    }

    private int minimax(Board board, int depth, boolean isMaximizing, 
                       CellState aiSymbol, CellState opponentSymbol) {
        // Check terminal states
        if (checkWinner(board, aiSymbol)) {
            return 10 - depth;
        }
        if (checkWinner(board, opponentSymbol)) {
            return depth - 10;
        }
        if (board.isFull()) {
            return 0;
        }

        if (isMaximizing) {
            int bestScore = Integer.MIN_VALUE;
            for (Cell cell : board.getEmptyCells()) {
                Board tempBoard = board.copy();
                tempBoard.makeMove(cell.getRow(), cell.getCol(), aiSymbol);
                int score = minimax(tempBoard, depth + 1, false, aiSymbol, opponentSymbol);
                bestScore = Math.max(score, bestScore);
            }
            return bestScore;
        } else {
            int bestScore = Integer.MAX_VALUE;
            for (Cell cell : board.getEmptyCells()) {
                Board tempBoard = board.copy();
                tempBoard.makeMove(cell.getRow(), cell.getCol(), opponentSymbol);
                int score = minimax(tempBoard, depth + 1, true, aiSymbol, opponentSymbol);
                bestScore = Math.min(score, bestScore);
            }
            return bestScore;
        }
    }

    private boolean checkWinner(Board board, CellState symbol) {
        int size = board.getSize();
        
        // Check rows and columns
        for (int i = 0; i < size; i++) {
            if (checkLine(board, i, 0, 0, 1, symbol) || // Row
                checkLine(board, 0, i, 1, 0, symbol)) { // Column
                return true;
            }
        }
        
        // Check diagonals
        return checkLine(board, 0, 0, 1, 1, symbol) || // Main diagonal
               checkLine(board, 0, size - 1, 1, -1, symbol); // Anti-diagonal
    }

    private boolean checkLine(Board board, int startRow, int startCol, 
                             int rowInc, int colInc, CellState symbol) {
        int size = board.getSize();
        for (int i = 0; i < size; i++) {
            Cell cell = board.getCell(startRow + i * rowInc, startCol + i * colInc);
            if (cell == null || cell.getState() != symbol) {
                return false;
            }
        }
        return true;
    }
}

class ComputerPlayer extends Player {
    private final AIStrategy strategy;
    private final Difficulty difficulty;

    public ComputerPlayer(String name, CellState symbol, Difficulty difficulty) {
        super(name, symbol, PlayerType.COMPUTER);
        this.difficulty = difficulty;
        this.strategy = createStrategy(difficulty);
    }

    private AIStrategy createStrategy(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new RandomAIStrategy();
            case MEDIUM, HARD -> new MinimaxAIStrategy();
        };
    }

    @Override
    public Move makeMove(Board board) {
        System.out.println(name + " is thinking...");
        try {
            Thread.sleep(500); // Simulate thinking time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return strategy.selectMove(board, symbol);
    }

    public Difficulty getDifficulty() { return difficulty; }
}

// ==================== GAME LOGIC ====================

class Game {
    private final String gameId;
    private final Board board;
    private final Player player1;
    private final Player player2;
    private Player currentPlayer;
    private GameState state;
    private final List<Move> moveHistory;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;

    public Game(String gameId, Player player1, Player player2) {
        this.gameId = gameId;
        this.board = new Board(3);
        this.player1 = player1;
        this.player2 = player2;
        this.currentPlayer = player1;
        this.state = GameState.NOT_STARTED;
        this.moveHistory = new ArrayList<>();
        this.startTime = LocalDateTime.now();
    }

    public String getGameId() { return gameId; }
    public Board getBoard() { return board; }
    public GameState getState() { return state; }
    public Player getCurrentPlayer() { return currentPlayer; }
    public List<Move> getMoveHistory() { return new ArrayList<>(moveHistory); }

    public void start() {
        state = GameState.IN_PROGRESS;
        System.out.println("\n=== GAME STARTED ===");
        System.out.println(player1.getName() + " (" + player1.getSymbol() + ") vs " +
                         player2.getName() + " (" + player2.getSymbol() + ")");
    }

    public boolean playMove(Move move) {
        if (state != GameState.IN_PROGRESS) {
            System.out.println("Game is not in progress!");
            return false;
        }

        if (move.getPlayer() != currentPlayer.getSymbol()) {
            System.out.println("Not your turn!");
            return false;
        }

        if (board.makeMove(move.getRow(), move.getCol(), move.getPlayer())) {
            moveHistory.add(move);
            
            // Check game state
            if (checkWinner(currentPlayer.getSymbol())) {
                state = (currentPlayer.getSymbol() == CellState.X) ? 
                       GameState.X_WON : GameState.O_WON;
                endTime = LocalDateTime.now();
                System.out.println("\n🎉 " + currentPlayer.getName() + " WINS! 🎉");
            } else if (board.isFull()) {
                state = GameState.DRAW;
                endTime = LocalDateTime.now();
                System.out.println("\n🤝 GAME DRAW! 🤝");
            } else {
                switchPlayer();
            }
            
            return true;
        }
        
        return false;
    }

    private void switchPlayer() {
        currentPlayer = (currentPlayer == player1) ? player2 : player1;
    }

    private boolean checkWinner(CellState symbol) {
        // Check rows
        for (int i = 0; i < 3; i++) {
            if (board.getCell(i, 0).getState() == symbol &&
                board.getCell(i, 1).getState() == symbol &&
                board.getCell(i, 2).getState() == symbol) {
                return true;
            }
        }

        // Check columns
        for (int j = 0; j < 3; j++) {
            if (board.getCell(0, j).getState() == symbol &&
                board.getCell(1, j).getState() == symbol &&
                board.getCell(2, j).getState() == symbol) {
                return true;
            }
        }

        // Check diagonals
        if (board.getCell(0, 0).getState() == symbol &&
            board.getCell(1, 1).getState() == symbol &&
            board.getCell(2, 2).getState() == symbol) {
            return true;
        }

        if (board.getCell(0, 2).getState() == symbol &&
            board.getCell(1, 1).getState() == symbol &&
            board.getCell(2, 0).getState() == symbol) {
            return true;
        }

        return false;
    }

    public void play() {
        start();
        
        while (state == GameState.IN_PROGRESS) {
            board.display();
            
            Move move = currentPlayer.makeMove(board);
            if (move != null) {
                playMove(move);
            }
        }
        
        board.display();
        displayGameSummary();
    }

    private void displayGameSummary() {
        System.out.println("\n=== GAME SUMMARY ===");
        System.out.println("Game ID: " + gameId);
        System.out.println("Result: " + state);
        System.out.println("Total Moves: " + moveHistory.size());
        System.out.println("Duration: " + java.time.Duration.between(startTime, 
                          endTime != null ? endTime : LocalDateTime.now()).getSeconds() + "s");
        
        System.out.println("\nMove History:");
        for (int i = 0; i < moveHistory.size(); i++) {
            System.out.println((i + 1) + ". " + moveHistory.get(i));
        }
    }

    public void reset() {
        board.reset();
        moveHistory.clear();
        currentPlayer = player1;
        state = GameState.NOT_STARTED;
    }
}

// ==================== GAME MANAGER ====================

class TicTacToeGameManager {
    private static TicTacToeGameManager instance;
    private final Map<String, Game> games;
    private int gameCounter;

    private TicTacToeGameManager() {
        this.games = new HashMap<>();
        this.gameCounter = 0;
    }

    public static synchronized TicTacToeGameManager getInstance() {
        if (instance == null) {
            instance = new TicTacToeGameManager();
        }
        return instance;
    }

    public Game createPvPGame(String player1Name, String player2Name) {
        String gameId = "GAME-" + (++gameCounter);
        Player player1 = new HumanPlayer(player1Name, CellState.X);
        Player player2 = new HumanPlayer(player2Name, CellState.O);
        Game game = new Game(gameId, player1, player2);
        games.put(gameId, game);
        return game;
    }

    public Game createPvCGame(String playerName, Difficulty difficulty) {
        String gameId = "GAME-" + (++gameCounter);
        Player player1 = new HumanPlayer(playerName, CellState.X);
        Player player2 = new ComputerPlayer("Computer", CellState.O, difficulty);
        Game game = new Game(gameId, player1, player2);
        games.put(gameId, game);
        return game;
    }

    public Game getGame(String gameId) {
        return games.get(gameId);
    }

    public List<Game> getAllGames() {
        return new ArrayList<>(games.values());
    }

    public void removeGame(String gameId) {
        games.remove(gameId);
    }
}

// ==================== DEMO ====================

public class TicTacToeDemo {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        TicTacToeGameManager manager = TicTacToeGameManager.getInstance();

        System.out.println("╔════════════════════════════════╗");
        System.out.println("║   TIC TAC TOE GAME SYSTEM      ║");
        System.out.println("╚════════════════════════════════╝");

        while (true) {
            System.out.println("\n=== MAIN MENU ===");
            System.out.println("1. Player vs Player");
            System.out.println("2. Player vs Computer (Easy)");
            System.out.println("3. Player vs Computer (Hard)");
            System.out.println("4. Exit");
            System.out.print("Choose option: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Clear buffer

            if (choice == 4) {
                System.out.println("Thanks for playing!");
                break;
            }

            Game game;
            switch (choice) {
                case 1 -> {
                    System.out.print("Enter Player 1 name: ");
                    String p1 = scanner.nextLine();
                    System.out.print("Enter Player 2 name: ");
                    String p2 = scanner.nextLine();
                    game = manager.createPvPGame(p1, p2);
                }
                case 2 -> {
                    System.out.print("Enter your name: ");
                    String name = scanner.nextLine();
                    game = manager.createPvCGame(name, Difficulty.EASY);
                }
                case 3 -> {
                    System.out.print("Enter your name: ");
                    String name = scanner.nextLine();
                    game = manager.createPvCGame(name, Difficulty.HARD);
                }
                default -> {
                    System.out.println("Invalid choice!");
                    continue;
                }
            }

            game.play();

            System.out.print("\nPlay again? (y/n): ");
            String playAgain = scanner.nextLine();
            if (!playAgain.equalsIgnoreCase("y")) {
                break;
            }
        }

        scanner.close();
    }
}
