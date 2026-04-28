import java.util.*;

/**
 * Chess Game - HELLO Interview Framework
 * 
 * Companies: Microsoft, Salesforce, Adobe, Amazon, Goldman Sachs +6 more
 * Pattern: Factory Pattern + Template Method
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Factory Pattern — PieceFactory.create(type, color) for clean piece creation
 * 2. Abstract Piece → 6 concrete subclasses with unique canMove() logic
 * 3. Simulation-based check detection — try move, verify king safety, revert
 * 4. Sliding piece path validation shared via helper method
 */

// ==================== Enums ====================

enum Color {
    WHITE, BLACK;
    public Color opposite() { return this == WHITE ? BLACK : WHITE; }
}

enum PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

enum GameStatus { ACTIVE, CHECK, CHECKMATE, STALEMATE, RESIGNED }

// ==================== Position ====================

class Position {
    final int row, col;
    Position(int row, int col) { this.row = row; this.col = col; }
    boolean isValid() { return row >= 0 && row < 8 && col >= 0 && col < 8; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position p)) return false;
        return row == p.row && col == p.col;
    }
    @Override
    public int hashCode() { return row * 8 + col; }
    @Override
    public String toString() { return "" + (char)('a' + col) + (8 - row); }
}

// ==================== Abstract Piece ====================

abstract class Piece {
    protected final Color color;
    protected final PieceType type;
    protected boolean hasMoved = false;

    Piece(Color color, PieceType type) {
        this.color = color;
        this.type = type;
    }

    public Color getColor() { return color; }
    public PieceType getType() { return type; }
    public boolean hasMoved() { return hasMoved; }
    public void setMoved() { this.hasMoved = true; }

    /** Can this piece move from→to? (Does NOT check if move leaves own king in check) */
    public abstract boolean canMove(Board board, Position from, Position to);

    /** Get all pseudo-legal moves (before check filtering) */
    public abstract List<Position> getValidMoves(Board board, Position from);

    /** Unicode symbol for display */
    public abstract char getSymbol();

    // ── Shared validation (Template Method) ──

    protected boolean basicValidation(Board board, Position from, Position to) {
        if (!to.isValid()) return false;
        if (from.equals(to)) return false;
        Piece target = board.getPiece(to.row, to.col);
        if (target != null && target.color == this.color) return false; // can't capture own
        return true;
    }

    /** Sliding piece helper: check no pieces between from and to */
    protected boolean isPathClear(Board board, Position from, Position to) {
        int rowDir = Integer.signum(to.row - from.row);
        int colDir = Integer.signum(to.col - from.col);
        int r = from.row + rowDir, c = from.col + colDir;
        while (r != to.row || c != to.col) {
            if (board.getPiece(r, c) != null) return false;
            r += rowDir; c += colDir;
        }
        return true;
    }

    @Override
    public String toString() { return "" + getSymbol(); }
}

// ==================== Concrete Pieces ====================

class King extends Piece {
    King(Color color) { super(color, PieceType.KING); }

    @Override
    public boolean canMove(Board board, Position from, Position to) {
        if (!basicValidation(board, from, to)) return false;
        int dr = Math.abs(to.row - from.row);
        int dc = Math.abs(to.col - from.col);
        return dr <= 1 && dc <= 1; // 1 square any direction
    }

    @Override
    public List<Position> getValidMoves(Board board, Position from) {
        List<Position> moves = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                Position to = new Position(from.row + dr, from.col + dc);
                if (canMove(board, from, to)) moves.add(to);
            }
        }
        return moves;
    }

    @Override
    public char getSymbol() { return color == Color.WHITE ? '♔' : '♚'; }
}

class Queen extends Piece {
    Queen(Color color) { super(color, PieceType.QUEEN); }

    @Override
    public boolean canMove(Board board, Position from, Position to) {
        if (!basicValidation(board, from, to)) return false;
        int dr = Math.abs(to.row - from.row);
        int dc = Math.abs(to.col - from.col);
        // Rook-like (straight) or Bishop-like (diagonal)
        boolean straight = (dr == 0 || dc == 0);
        boolean diagonal = (dr == dc);
        return (straight || diagonal) && isPathClear(board, from, to);
    }

    @Override
    public List<Position> getValidMoves(Board board, Position from) {
        List<Position> moves = new ArrayList<>();
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : dirs) addSlidingMoves(board, from, d[0], d[1], moves);
        return moves;
    }

    private void addSlidingMoves(Board board, Position from, int dr, int dc, List<Position> moves) {
        int r = from.row + dr, c = from.col + dc;
        while (r >= 0 && r < 8 && c >= 0 && c < 8) {
            Position to = new Position(r, c);
            Piece target = board.getPiece(r, c);
            if (target == null) { moves.add(to); }
            else {
                if (target.getColor() != this.color) moves.add(to);
                break;
            }
            r += dr; c += dc;
        }
    }

    @Override
    public char getSymbol() { return color == Color.WHITE ? '♕' : '♛'; }
}

class Rook extends Piece {
    Rook(Color color) { super(color, PieceType.ROOK); }

    @Override
    public boolean canMove(Board board, Position from, Position to) {
        if (!basicValidation(board, from, to)) return false;
        boolean straight = (from.row == to.row || from.col == to.col);
        return straight && isPathClear(board, from, to);
    }

    @Override
    public List<Position> getValidMoves(Board board, Position from) {
        List<Position> moves = new ArrayList<>();
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int r = from.row + d[0], c = from.col + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                Position to = new Position(r, c);
                Piece target = board.getPiece(r, c);
                if (target == null) { moves.add(to); }
                else { if (target.getColor() != color) moves.add(to); break; }
                r += d[0]; c += d[1];
            }
        }
        return moves;
    }

    @Override
    public char getSymbol() { return color == Color.WHITE ? '♖' : '♜'; }
}

class Bishop extends Piece {
    Bishop(Color color) { super(color, PieceType.BISHOP); }

    @Override
    public boolean canMove(Board board, Position from, Position to) {
        if (!basicValidation(board, from, to)) return false;
        boolean diagonal = Math.abs(to.row - from.row) == Math.abs(to.col - from.col);
        return diagonal && isPathClear(board, from, to);
    }

    @Override
    public List<Position> getValidMoves(Board board, Position from) {
        List<Position> moves = new ArrayList<>();
        int[][] dirs = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : dirs) {
            int r = from.row + d[0], c = from.col + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                Position to = new Position(r, c);
                Piece target = board.getPiece(r, c);
                if (target == null) { moves.add(to); }
                else { if (target.getColor() != color) moves.add(to); break; }
                r += d[0]; c += d[1];
            }
        }
        return moves;
    }

    @Override
    public char getSymbol() { return color == Color.WHITE ? '♗' : '♝'; }
}

class Knight extends Piece {
    private static final int[][] JUMPS = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};

    Knight(Color color) { super(color, PieceType.KNIGHT); }

    @Override
    public boolean canMove(Board board, Position from, Position to) {
        if (!basicValidation(board, from, to)) return false;
        int dr = Math.abs(to.row - from.row);
        int dc = Math.abs(to.col - from.col);
        return (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
    }

    @Override
    public List<Position> getValidMoves(Board board, Position from) {
        List<Position> moves = new ArrayList<>();
        for (int[] j : JUMPS) {
            Position to = new Position(from.row + j[0], from.col + j[1]);
            if (canMove(board, from, to)) moves.add(to);
        }
        return moves;
    }

    @Override
    public char getSymbol() { return color == Color.WHITE ? '♘' : '♞'; }
}

class Pawn extends Piece {
    Pawn(Color color) { super(color, PieceType.PAWN); }

    @Override
    public boolean canMove(Board board, Position from, Position to) {
        if (!basicValidation(board, from, to)) return false;

        int direction = (color == Color.WHITE) ? -1 : 1; // white moves up (row--)
        int startRow = (color == Color.WHITE) ? 6 : 1;
        int dr = to.row - from.row;
        int dc = to.col - from.col;

        // Forward 1
        if (dc == 0 && dr == direction && board.getPiece(to.row, to.col) == null)
            return true;

        // Forward 2 from start
        if (dc == 0 && dr == 2 * direction && from.row == startRow
                && board.getPiece(from.row + direction, from.col) == null
                && board.getPiece(to.row, to.col) == null)
            return true;

        // Diagonal capture
        if (Math.abs(dc) == 1 && dr == direction) {
            Piece target = board.getPiece(to.row, to.col);
            return target != null && target.getColor() != this.color;
        }

        return false;
    }

    @Override
    public List<Position> getValidMoves(Board board, Position from) {
        List<Position> moves = new ArrayList<>();
        int dir = (color == Color.WHITE) ? -1 : 1;
        int startRow = (color == Color.WHITE) ? 6 : 1;

        // Forward 1
        Position fwd = new Position(from.row + dir, from.col);
        if (fwd.isValid() && board.getPiece(fwd.row, fwd.col) == null) {
            moves.add(fwd);
            // Forward 2 from start
            if (from.row == startRow) {
                Position fwd2 = new Position(from.row + 2 * dir, from.col);
                if (fwd2.isValid() && board.getPiece(fwd2.row, fwd2.col) == null)
                    moves.add(fwd2);
            }
        }
        // Diagonal captures
        for (int dc : new int[]{-1, 1}) {
            Position cap = new Position(from.row + dir, from.col + dc);
            if (cap.isValid()) {
                Piece target = board.getPiece(cap.row, cap.col);
                if (target != null && target.getColor() != color) moves.add(cap);
            }
        }
        return moves;
    }

    @Override
    public char getSymbol() { return color == Color.WHITE ? '♙' : '♟'; }
}

// ==================== PieceFactory ====================

class PieceFactory {
    public static Piece create(PieceType type, Color color) {
        return switch (type) {
            case KING   -> new King(color);
            case QUEEN  -> new Queen(color);
            case ROOK   -> new Rook(color);
            case BISHOP -> new Bishop(color);
            case KNIGHT -> new Knight(color);
            case PAWN   -> new Pawn(color);
        };
    }
}

// ==================== Board ====================

class Board {
    private final Piece[][] grid = new Piece[8][8];

    public Board() { initialize(); }

    private void initialize() {
        // Black pieces (row 0)
        PieceType[] backRow = {PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                               PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK};
        for (int c = 0; c < 8; c++) {
            grid[0][c] = PieceFactory.create(backRow[c], Color.BLACK);
            grid[1][c] = PieceFactory.create(PieceType.PAWN, Color.BLACK);
            grid[6][c] = PieceFactory.create(PieceType.PAWN, Color.WHITE);
            grid[7][c] = PieceFactory.create(backRow[c], Color.WHITE);
        }
    }

    public Piece getPiece(int row, int col) {
        if (row < 0 || row >= 8 || col < 0 || col >= 8) return null;
        return grid[row][col];
    }

    /** Execute a move (assumes already validated) */
    public Piece movePiece(Position from, Position to) {
        Piece piece = grid[from.row][from.col];
        Piece captured = grid[to.row][to.col];
        grid[to.row][to.col] = piece;
        grid[from.row][from.col] = null;
        if (piece != null) piece.setMoved();
        return captured;
    }

    /** Undo a move (for simulation) */
    public void undoMove(Position from, Position to, Piece captured) {
        Piece piece = grid[to.row][to.col];
        grid[from.row][from.col] = piece;
        grid[to.row][to.col] = captured;
    }

    /** Find the king's position for a given color */
    public Position getKingPosition(Color color) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (grid[r][c] != null && grid[r][c].getType() == PieceType.KING
                        && grid[r][c].getColor() == color)
                    return new Position(r, c);
        throw new RuntimeException("King not found for " + color); // should never happen
    }

    /** Is the given color's king currently in check? */
    public boolean isCheck(Color color) {
        Position kingPos = getKingPosition(color);
        Color opponent = color.opposite();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = grid[r][c];
                if (piece != null && piece.getColor() == opponent) {
                    if (piece.canMove(this, new Position(r, c), kingPos))
                        return true;
                }
            }
        }
        return false;
    }

    /** Does the given color have any legal move? */
    public boolean hasLegalMove(Color color) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = grid[r][c];
                if (piece != null && piece.getColor() == color) {
                    Position from = new Position(r, c);
                    for (Position to : piece.getValidMoves(this, from)) {
                        // Simulate move
                        Piece captured = movePiece(from, to);
                        boolean stillInCheck = isCheck(color);
                        undoMove(from, to, captured);
                        if (!stillInCheck) return true; // found at least one legal move
                    }
                }
            }
        }
        return false;
    }

    /** Is it checkmate for the given color? */
    public boolean isCheckmate(Color color) {
        return isCheck(color) && !hasLegalMove(color);
    }

    /** Is it stalemate for the given color? */
    public boolean isStalemate(Color color) {
        return !isCheck(color) && !hasLegalMove(color);
    }

    /** Display board with coordinates */
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n    a   b   c   d   e   f   g   h\n");
        sb.append("  ┌───┬───┬───┬───┬───┬───┬───┬───┐\n");
        for (int r = 0; r < 8; r++) {
            sb.append((8 - r)).append(" │");
            for (int c = 0; c < 8; c++) {
                Piece p = grid[r][c];
                sb.append(" ").append(p == null ? "·" : "" + p.getSymbol()).append(" │");
            }
            sb.append(" ").append(8 - r).append("\n");
            if (r < 7) sb.append("  ├───┼───┼───┼───┼───┼───┼───┼───┤\n");
        }
        sb.append("  └───┴───┴───┴───┴───┴───┴───┴───┘\n");
        sb.append("    a   b   c   d   e   f   g   h\n");
        return sb.toString();
    }
}

// ==================== Player ====================

class Player {
    private final String name;
    private final Color color;
    private final List<Piece> capturedPieces = new ArrayList<>();

    Player(String name, Color color) { this.name = name; this.color = color; }
    public String getName() { return name; }
    public Color getColor() { return color; }
    public void addCaptured(Piece p) { capturedPieces.add(p); }
    public List<Piece> getCapturedPieces() { return capturedPieces; }

    @Override
    public String toString() { return name + " (" + color + ")"; }
}

// ==================== Move Record ====================

class Move {
    final Position from, to;
    final Piece piece;
    final Piece captured;
    final int moveNumber;

    Move(Position from, Position to, Piece piece, Piece captured, int moveNumber) {
        this.from = from; this.to = to; this.piece = piece;
        this.captured = captured; this.moveNumber = moveNumber;
    }

    @Override
    public String toString() {
        String cap = captured != null ? "x" + captured.getSymbol() : "";
        return moveNumber + ". " + piece.getSymbol() + from + "→" + to + cap;
    }
}

// ==================== Game ====================

class Game {
    private final Board board;
    private final Player whitePlayer;
    private final Player blackPlayer;
    private Color currentTurn;
    private GameStatus status;
    private final List<Move> moveHistory = new ArrayList<>();
    private int moveCount = 0;

    public Game(String whiteName, String blackName) {
        this.board = new Board();
        this.whitePlayer = new Player(whiteName, Color.WHITE);
        this.blackPlayer = new Player(blackName, Color.BLACK);
        this.currentTurn = Color.WHITE;
        this.status = GameStatus.ACTIVE;
    }

    /**
     * Make a move. Returns true if valid and executed.
     * Updates game status (CHECK, CHECKMATE, STALEMATE).
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (isGameOver()) {
            System.out.println("  ⚠️ Game is over: " + status);
            return false;
        }

        Position from = new Position(fromRow, fromCol);
        Position to = new Position(toRow, toCol);

        // Validate from position
        Piece piece = board.getPiece(fromRow, fromCol);
        if (piece == null) {
            System.out.println("  ✗ No piece at " + from);
            return false;
        }
        if (piece.getColor() != currentTurn) {
            System.out.println("  ✗ Not your piece! It's " + currentTurn + "'s turn.");
            return false;
        }

        // Validate piece can move there
        if (!piece.canMove(board, from, to)) {
            System.out.println("  ✗ " + piece.getSymbol() + " cannot move " + from + "→" + to);
            return false;
        }

        // Simulate: does move leave own king in check?
        Piece captured = board.movePiece(from, to);
        if (board.isCheck(currentTurn)) {
            board.undoMove(from, to, captured);
            System.out.println("  ✗ Illegal: move leaves your king in check!");
            return false;
        }

        // Move is legal — record it
        moveCount++;
        Move move = new Move(from, to, piece, captured, moveCount);
        moveHistory.add(move);

        if (captured != null) {
            getCurrentPlayer().addCaptured(captured);
            System.out.println("  " + move + " (captured!)");
        } else {
            System.out.println("  " + move);
        }

        // Switch turn and update status
        currentTurn = currentTurn.opposite();
        updateStatus();
        return true;
    }

    private void updateStatus() {
        if (board.isCheckmate(currentTurn)) {
            status = GameStatus.CHECKMATE;
            System.out.println("  👑 CHECKMATE! " + currentTurn.opposite() + " wins!");
        } else if (board.isStalemate(currentTurn)) {
            status = GameStatus.STALEMATE;
            System.out.println("  🤝 STALEMATE! It's a draw.");
        } else if (board.isCheck(currentTurn)) {
            status = GameStatus.CHECK;
            System.out.println("  ⚠️ CHECK! " + currentTurn + "'s king is in danger!");
        } else {
            status = GameStatus.ACTIVE;
        }
    }

    public void resign() {
        status = GameStatus.RESIGNED;
        System.out.println("  🏳️ " + currentTurn + " resigns. " + currentTurn.opposite() + " wins!");
    }

    public boolean isGameOver() {
        return status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
            || status == GameStatus.RESIGNED;
    }

    public GameStatus getStatus() { return status; }
    public Color getCurrentTurn() { return currentTurn; }
    public String displayBoard() { return board.display(); }
    public List<Move> getMoveHistory() { return Collections.unmodifiableList(moveHistory); }

    private Player getCurrentPlayer() {
        return currentTurn == Color.WHITE ? whitePlayer : blackPlayer;
    }

    public String getStatusLine() {
        return String.format("Turn: %s | Status: %s | Moves: %d", currentTurn, status, moveCount);
    }
}

// ==================== Main Demo ====================

public class ChessGame {
    public static void main(String[] args) {
        System.out.println("╔═════════════════════════════════════════════╗");
        System.out.println("║  Chess Game - Factory Pattern + Check/Mate ║");
        System.out.println("╚═════════════════════════════════════════════╝");

        Game game = new Game("Alice", "Bob");
        System.out.println(game.displayBoard());
        System.out.println(game.getStatusLine());

        // ── Scholar's Mate (4-move checkmate) ──
        System.out.println("\n━━━ Playing Scholar's Mate (4-move checkmate) ━━━\n");

        // Move 1: e2→e4 (White pawn)
        System.out.println("White: e2→e4");
        game.makeMove(6, 4, 4, 4);

        // Move 1: e7→e5 (Black pawn)
        System.out.println("Black: e7→e5");
        game.makeMove(1, 4, 3, 4);

        // Move 2: Bf1→c4 (White bishop)
        System.out.println("White: Bf1→c4");
        game.makeMove(7, 5, 4, 2);

        // Move 2: Nb8→c6 (Black knight)
        System.out.println("Black: Nb8→c6");
        game.makeMove(0, 1, 2, 2);

        // Move 3: Qd1→h5 (White queen)
        System.out.println("White: Qd1→h5");
        game.makeMove(7, 3, 3, 7);

        // Move 3: Ng8→f6 (Black knight — tries to defend)
        System.out.println("Black: Ng8→f6");
        game.makeMove(0, 6, 2, 5);

        System.out.println(game.displayBoard());
        System.out.println(game.getStatusLine());

        // Move 4: Qh5→f7# (White queen takes f7 — checkmate!)
        System.out.println("White: Qh5xf7# (Scholar's Mate!)");
        game.makeMove(3, 7, 1, 5);

        System.out.println(game.displayBoard());
        System.out.println(game.getStatusLine());

        // ── Verify game is over ──
        System.out.println("\n━━━ Post-Game Verification ━━━");
        System.out.println("  Game over? " + game.isGameOver());
        System.out.println("  Status: " + game.getStatus());

        // Try to make a move after game over
        System.out.println("\n  Attempting move after game over:");
        game.makeMove(0, 0, 0, 1);

        // ── Move history ──
        System.out.println("\n━━━ Move History ━━━");
        for (Move m : game.getMoveHistory()) {
            System.out.println("  " + m);
        }

        // ── New game: test invalid moves ──
        System.out.println("\n━━━ Invalid Move Tests ━━━");
        Game game2 = new Game("Charlie", "Diana");

        // Try to move empty square
        System.out.println("Move empty square:");
        game2.makeMove(4, 4, 5, 4);

        // Try to move opponent's piece
        System.out.println("Move opponent's piece:");
        game2.makeMove(1, 0, 2, 0);

        // Try invalid pawn move
        System.out.println("Invalid pawn move (diagonal without capture):");
        game2.makeMove(6, 0, 5, 1);

        // Valid move
        System.out.println("Valid: a2→a4");
        game2.makeMove(6, 0, 4, 0);
        System.out.println(game2.displayBoard());

        // ── Test resignation ──
        System.out.println("━━━ Resignation Test ━━━");
        game2.resign();
        System.out.println("  Game over? " + game2.isGameOver());

        System.out.println("\n✅ All chess scenarios complete.");
    }
}
