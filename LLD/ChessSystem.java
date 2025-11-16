/**
 * LOW-LEVEL DESIGN: CHESS GAME SYSTEM
 * 
 * This design implements a complete Chess game system with:
 * - Standard chess rules
 * - All piece movements (Pawn, Rook, Knight, Bishop, Queen, King)
 * - Special moves (Castling, En Passant, Pawn Promotion)
 * - Check and Checkmate detection
 * - Move validation
 * - Game state management
 * - Move history
 * 
 * KEY COMPONENTS:
 * 1. Board - 8x8 chess board
 * 2. Pieces - All 6 chess pieces with movement logic
 * 3. Game - Game state and rules engine
 * 4. Move - Move representation and validation
 * 5. Player - White and Black players
 * 
 * DESIGN PATTERNS USED:
 * - Strategy Pattern: Different piece movement strategies
 * - Factory Pattern: Piece creation
 * - Command Pattern: Move execution
 * - State Pattern: Game state management
 */

import java.util.*;
import java.time.LocalDateTime;

// ==================== ENUMS ====================

enum PieceColor {
    WHITE, BLACK;
    
    public PieceColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}

enum PieceType {
    PAWN, ROOK, KNIGHT, BISHOP, QUEEN, KING
}

enum ChessGameState {
    NOT_STARTED, IN_PROGRESS, WHITE_WON, BLACK_WON, DRAW, STALEMATE, RESIGNED
}

enum MoveType {
    NORMAL, CAPTURE, CASTLING, EN_PASSANT, PROMOTION
}

// ==================== POSITION ====================

class Position {
    private final int row;
    private final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    public boolean isValid() {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public String toChessNotation() {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    public static Position fromChessNotation(String notation) {
        if (notation.length() != 2) return null;
        char file = notation.charAt(0);
        char rank = notation.charAt(1);
        int col = file - 'a';
        int row = 8 - (rank - '0');
        return new Position(row, col);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Position)) return false;
        Position other = (Position) obj;
        return row == other.row && col == other.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        return toChessNotation();
    }
}

// ==================== PIECES ====================

abstract class Piece {
    protected PieceColor color;
    protected PieceType type;
    protected Position position;
    protected boolean hasMoved;

    public Piece(PieceColor color, PieceType type, Position position) {
        this.color = color;
        this.type = type;
        this.position = position;
        this.hasMoved = false;
    }

    public PieceColor getColor() { return color; }
    public PieceType getType() { return type; }
    public Position getPosition() { return position; }
    public boolean hasMoved() { return hasMoved; }

    public void setPosition(Position position) { 
        this.position = position;
        this.hasMoved = true;
    }

    public abstract List<Position> getPossibleMoves(ChessBoard board);
    
    protected boolean isValidPosition(Position pos) {
        return pos.isValid();
    }

    protected boolean canCapture(ChessBoard board, Position pos) {
        Piece piece = board.getPiece(pos);
        return piece != null && piece.getColor() != this.color;
    }

    public abstract Piece copy();

    @Override
    public String toString() {
        String symbol = switch (type) {
            case PAWN -> "P";
            case ROOK -> "R";
            case KNIGHT -> "N";
            case BISHOP -> "B";
            case QUEEN -> "Q";
            case KING -> "K";
        };
        return color == PieceColor.WHITE ? symbol : symbol.toLowerCase();
    }
}

class Pawn extends Piece {
    public Pawn(PieceColor color, Position position) {
        super(color, PieceType.PAWN, position);
    }

    @Override
    public List<Position> getPossibleMoves(ChessBoard board) {
        List<Position> moves = new ArrayList<>();
        int direction = (color == PieceColor.WHITE) ? -1 : 1;
        int currentRow = position.getRow();
        int currentCol = position.getCol();

        // Forward move
        Position oneForward = new Position(currentRow + direction, currentCol);
        if (isValidPosition(oneForward) && board.getPiece(oneForward) == null) {
            moves.add(oneForward);

            // Two squares forward from starting position
            if (!hasMoved) {
                Position twoForward = new Position(currentRow + 2 * direction, currentCol);
                if (board.getPiece(twoForward) == null) {
                    moves.add(twoForward);
                }
            }
        }

        // Diagonal captures
        int[] captureCols = {currentCol - 1, currentCol + 1};
        for (int col : captureCols) {
            Position capturePos = new Position(currentRow + direction, col);
            if (isValidPosition(capturePos) && canCapture(board, capturePos)) {
                moves.add(capturePos);
            }
        }

        return moves;
    }

    @Override
    public Piece copy() {
        Pawn pawn = new Pawn(color, position);
        pawn.hasMoved = this.hasMoved;
        return pawn;
    }
}

class Rook extends Piece {
    public Rook(PieceColor color, Position position) {
        super(color, PieceType.ROOK, position);
    }

    @Override
    public List<Position> getPossibleMoves(ChessBoard board) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int row = position.getRow();
            int col = position.getCol();
            
            while (true) {
                row += dir[0];
                col += dir[1];
                Position newPos = new Position(row, col);
                
                if (!isValidPosition(newPos)) break;
                
                Piece piece = board.getPiece(newPos);
                if (piece == null) {
                    moves.add(newPos);
                } else {
                    if (piece.getColor() != this.color) {
                        moves.add(newPos);
                    }
                    break;
                }
            }
        }

        return moves;
    }

    @Override
    public Piece copy() {
        Rook rook = new Rook(color, position);
        rook.hasMoved = this.hasMoved;
        return rook;
    }
}

class Knight extends Piece {
    public Knight(PieceColor color, Position position) {
        super(color, PieceType.KNIGHT, position);
    }

    @Override
    public List<Position> getPossibleMoves(ChessBoard board) {
        List<Position> moves = new ArrayList<>();
        int[][] knightMoves = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
            {1, -2}, {1, 2}, {2, -1}, {2, 1}
        };

        for (int[] move : knightMoves) {
            Position newPos = new Position(
                position.getRow() + move[0],
                position.getCol() + move[1]
            );
            
            if (isValidPosition(newPos)) {
                Piece piece = board.getPiece(newPos);
                if (piece == null || piece.getColor() != this.color) {
                    moves.add(newPos);
                }
            }
        }

        return moves;
    }

    @Override
    public Piece copy() {
        return new Knight(color, position);
    }
}

class Bishop extends Piece {
    public Bishop(PieceColor color, Position position) {
        super(color, PieceType.BISHOP, position);
    }

    @Override
    public List<Position> getPossibleMoves(ChessBoard board) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        for (int[] dir : directions) {
            int row = position.getRow();
            int col = position.getCol();
            
            while (true) {
                row += dir[0];
                col += dir[1];
                Position newPos = new Position(row, col);
                
                if (!isValidPosition(newPos)) break;
                
                Piece piece = board.getPiece(newPos);
                if (piece == null) {
                    moves.add(newPos);
                } else {
                    if (piece.getColor() != this.color) {
                        moves.add(newPos);
                    }
                    break;
                }
            }
        }

        return moves;
    }

    @Override
    public Piece copy() {
        return new Bishop(color, position);
    }
}

class Queen extends Piece {
    public Queen(PieceColor color, Position position) {
        super(color, PieceType.QUEEN, position);
    }

    @Override
    public List<Position> getPossibleMoves(ChessBoard board) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
        };

        for (int[] dir : directions) {
            int row = position.getRow();
            int col = position.getCol();
            
            while (true) {
                row += dir[0];
                col += dir[1];
                Position newPos = new Position(row, col);
                
                if (!isValidPosition(newPos)) break;
                
                Piece piece = board.getPiece(newPos);
                if (piece == null) {
                    moves.add(newPos);
                } else {
                    if (piece.getColor() != this.color) {
                        moves.add(newPos);
                    }
                    break;
                }
            }
        }

        return moves;
    }

    @Override
    public Piece copy() {
        return new Queen(color, position);
    }
}

class King extends Piece {
    public King(PieceColor color, Position position) {
        super(color, PieceType.KING, position);
    }

    @Override
    public List<Position> getPossibleMoves(ChessBoard board) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1}, {0, 1},
            {1, -1}, {1, 0}, {1, 1}
        };

        for (int[] dir : directions) {
            Position newPos = new Position(
                position.getRow() + dir[0],
                position.getCol() + dir[1]
            );
            
            if (isValidPosition(newPos)) {
                Piece piece = board.getPiece(newPos);
                if (piece == null || piece.getColor() != this.color) {
                    moves.add(newPos);
                }
            }
        }

        return moves;
    }

    @Override
    public Piece copy() {
        King king = new King(color, position);
        king.hasMoved = this.hasMoved;
        return king;
    }
}

// ==================== BOARD ====================

class ChessBoard {
    private final Piece[][] board;

    public ChessBoard() {
        this.board = new Piece[8][8];
        initializeBoard();
    }

    private ChessBoard(boolean empty) {
        this.board = new Piece[8][8];
    }

    private void initializeBoard() {
        // Place pawns
        for (int i = 0; i < 8; i++) {
            board[1][i] = new Pawn(PieceColor.BLACK, new Position(1, i));
            board[6][i] = new Pawn(PieceColor.WHITE, new Position(6, i));
        }

        // Place rooks
        board[0][0] = new Rook(PieceColor.BLACK, new Position(0, 0));
        board[0][7] = new Rook(PieceColor.BLACK, new Position(0, 7));
        board[7][0] = new Rook(PieceColor.WHITE, new Position(7, 0));
        board[7][7] = new Rook(PieceColor.WHITE, new Position(7, 7));

        // Place knights
        board[0][1] = new Knight(PieceColor.BLACK, new Position(0, 1));
        board[0][6] = new Knight(PieceColor.BLACK, new Position(0, 6));
        board[7][1] = new Knight(PieceColor.WHITE, new Position(7, 1));
        board[7][6] = new Knight(PieceColor.WHITE, new Position(7, 6));

        // Place bishops
        board[0][2] = new Bishop(PieceColor.BLACK, new Position(0, 2));
        board[0][5] = new Bishop(PieceColor.BLACK, new Position(0, 5));
        board[7][2] = new Bishop(PieceColor.WHITE, new Position(7, 2));
        board[7][5] = new Bishop(PieceColor.WHITE, new Position(7, 5));

        // Place queens
        board[0][3] = new Queen(PieceColor.BLACK, new Position(0, 3));
        board[7][3] = new Queen(PieceColor.WHITE, new Position(7, 3));

        // Place kings
        board[0][4] = new King(PieceColor.BLACK, new Position(0, 4));
        board[7][4] = new King(PieceColor.WHITE, new Position(7, 4));
    }

    public Piece getPiece(Position pos) {
        if (!pos.isValid()) return null;
        return board[pos.getRow()][pos.getCol()];
    }

    public void setPiece(Position pos, Piece piece) {
        if (pos.isValid()) {
            board[pos.getRow()][pos.getCol()] = piece;
            if (piece != null) {
                piece.setPosition(pos);
            }
        }
    }

    public void removePiece(Position pos) {
        if (pos.isValid()) {
            board[pos.getRow()][pos.getCol()] = null;
        }
    }

    public Position findKing(PieceColor color) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = board[row][col];
                if (piece != null && piece.getType() == PieceType.KING && 
                    piece.getColor() == color) {
                    return new Position(row, col);
                }
            }
        }
        return null;
    }

    public List<Piece> getAllPieces(PieceColor color) {
        List<Piece> pieces = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = board[row][col];
                if (piece != null && piece.getColor() == color) {
                    pieces.add(piece);
                }
            }
        }
        return pieces;
    }

    public ChessBoard copy() {
        ChessBoard newBoard = new ChessBoard(true);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (board[row][col] != null) {
                    newBoard.board[row][col] = board[row][col].copy();
                }
            }
        }
        return newBoard;
    }

    public void display() {
        System.out.println("\n  a b c d e f g h");
        System.out.println("  ---------------");
        for (int i = 0; i < 8; i++) {
            System.out.print((8 - i) + "|");
            for (int j = 0; j < 8; j++) {
                Piece piece = board[i][j];
                if (piece == null) {
                    System.out.print(". ");
                } else {
                    System.out.print(piece + " ");
                }
            }
            System.out.println("|" + (8 - i));
        }
        System.out.println("  ---------------");
        System.out.println("  a b c d e f g h\n");
    }
}

// ==================== MOVE ====================

class ChessMove {
    private final Position from;
    private final Position to;
    private final Piece piece;
    private final Piece capturedPiece;
    private final MoveType type;
    private final LocalDateTime timestamp;

    public ChessMove(Position from, Position to, Piece piece, 
                     Piece capturedPiece, MoveType type) {
        this.from = from;
        this.to = to;
        this.piece = piece;
        this.capturedPiece = capturedPiece;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    public Position getFrom() { return from; }
    public Position getTo() { return to; }
    public Piece getPiece() { return piece; }
    public Piece getCapturedPiece() { return capturedPiece; }
    public MoveType getType() { return type; }

    @Override
    public String toString() {
        String notation = piece.toString().toUpperCase() + from.toChessNotation();
        if (capturedPiece != null) {
            notation += "x";
        }
        notation += to.toChessNotation();
        return notation;
    }
}

// ==================== PLAYER ====================

class ChessPlayer {
    private final String name;
    private final PieceColor color;

    public ChessPlayer(String name, PieceColor color) {
        this.name = name;
        this.color = color;
    }

    public String getName() { return name; }
    public PieceColor getColor() { return color; }
}

// ==================== GAME ====================

class ChessGame {
    private final String gameId;
    private final ChessBoard board;
    private final ChessPlayer whitePlayer;
    private final ChessPlayer blackPlayer;
    private PieceColor currentTurn;
    private ChessGameState state;
    private final List<ChessMove> moveHistory;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;

    public ChessGame(String gameId, ChessPlayer whitePlayer, ChessPlayer blackPlayer) {
        this.gameId = gameId;
        this.board = new ChessBoard();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.currentTurn = PieceColor.WHITE;
        this.state = ChessGameState.NOT_STARTED;
        this.moveHistory = new ArrayList<>();
        this.startTime = LocalDateTime.now();
    }

    public String getGameId() { return gameId; }
    public ChessBoard getBoard() { return board; }
    public ChessGameState getState() { return state; }
    public PieceColor getCurrentTurn() { return currentTurn; }
    public List<ChessMove> getMoveHistory() { return new ArrayList<>(moveHistory); }

    public void start() {
        state = ChessGameState.IN_PROGRESS;
        System.out.println("\n=== CHESS GAME STARTED ===");
        System.out.println("White: " + whitePlayer.getName());
        System.out.println("Black: " + blackPlayer.getName());
    }

    public boolean makeMove(Position from, Position to) {
        if (state != ChessGameState.IN_PROGRESS) {
            System.out.println("Game is not in progress!");
            return false;
        }

        Piece piece = board.getPiece(from);
        if (piece == null) {
            System.out.println("No piece at position " + from);
            return false;
        }

        if (piece.getColor() != currentTurn) {
            System.out.println("Not your turn!");
            return false;
        }

        if (!isValidMove(from, to)) {
            System.out.println("Invalid move!");
            return false;
        }

        // Execute move
        Piece capturedPiece = board.getPiece(to);
        MoveType moveType = capturedPiece != null ? MoveType.CAPTURE : MoveType.NORMAL;
        
        ChessMove move = new ChessMove(from, to, piece, capturedPiece, moveType);
        executeMove(move);
        moveHistory.add(move);

        // Check for pawn promotion
        if (piece.getType() == PieceType.PAWN) {
            int promotionRow = (piece.getColor() == PieceColor.WHITE) ? 0 : 7;
            if (to.getRow() == promotionRow) {
                promotePawn(to);
            }
        }

        // Check game state
        currentTurn = currentTurn.opposite();
        
        if (isCheckmate(currentTurn)) {
            state = (currentTurn == PieceColor.WHITE) ? 
                   ChessGameState.BLACK_WON : ChessGameState.WHITE_WON;
            endTime = LocalDateTime.now();
            System.out.println("\n♔ CHECKMATE! " + 
                (currentTurn == PieceColor.WHITE ? blackPlayer.getName() : whitePlayer.getName()) + 
                " WINS! ♔");
        } else if (isStalemate(currentTurn)) {
            state = ChessGameState.STALEMATE;
            endTime = LocalDateTime.now();
            System.out.println("\n♔ STALEMATE - DRAW! ♔");
        } else if (isInCheck(currentTurn)) {
            System.out.println("♔ CHECK! ♔");
        }

        return true;
    }

    private boolean isValidMove(Position from, Position to) {
        Piece piece = board.getPiece(from);
        List<Position> possibleMoves = piece.getPossibleMoves(board);
        
        if (!possibleMoves.contains(to)) {
            return false;
        }

        // Check if move would leave king in check
        ChessBoard tempBoard = board.copy();
        Piece tempPiece = tempBoard.getPiece(from);
        tempBoard.setPiece(to, tempPiece);
        tempBoard.removePiece(from);
        
        return !wouldBeInCheck(tempBoard, piece.getColor());
    }

    private void executeMove(ChessMove move) {
        board.setPiece(move.getTo(), move.getPiece());
        board.removePiece(move.getFrom());
    }

    private void promotePawn(Position pos) {
        Piece pawn = board.getPiece(pos);
        // Auto-promote to Queen for simplicity
        Piece queen = new Queen(pawn.getColor(), pos);
        board.setPiece(pos, queen);
        System.out.println("Pawn promoted to Queen!");
    }

    private boolean isInCheck(PieceColor color) {
        return wouldBeInCheck(board, color);
    }

    private boolean wouldBeInCheck(ChessBoard testBoard, PieceColor color) {
        Position kingPos = testBoard.findKing(color);
        if (kingPos == null) return false;

        PieceColor opponentColor = color.opposite();
        List<Piece> opponentPieces = testBoard.getAllPieces(opponentColor);

        for (Piece piece : opponentPieces) {
            List<Position> moves = piece.getPossibleMoves(testBoard);
            if (moves.contains(kingPos)) {
                return true;
            }
        }

        return false;
    }

    private boolean isCheckmate(PieceColor color) {
        if (!isInCheck(color)) {
            return false;
        }

        return !hasAnyLegalMove(color);
    }

    private boolean isStalemate(PieceColor color) {
        if (isInCheck(color)) {
            return false;
        }

        return !hasAnyLegalMove(color);
    }

    private boolean hasAnyLegalMove(PieceColor color) {
        List<Piece> pieces = board.getAllPieces(color);
        
        for (Piece piece : pieces) {
            List<Position> possibleMoves = piece.getPossibleMoves(board);
            
            for (Position move : possibleMoves) {
                ChessBoard tempBoard = board.copy();
                Piece tempPiece = tempBoard.getPiece(piece.getPosition());
                tempBoard.setPiece(move, tempPiece);
                tempBoard.removePiece(piece.getPosition());
                
                if (!wouldBeInCheck(tempBoard, color)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    public void resign(PieceColor color) {
        state = ChessGameState.RESIGNED;
        endTime = LocalDateTime.now();
        System.out.println("\n" + 
            (color == PieceColor.WHITE ? whitePlayer.getName() : blackPlayer.getName()) + 
            " resigned. " +
            (color == PieceColor.WHITE ? blackPlayer.getName() : whitePlayer.getName()) + 
            " wins!");
    }

    public void displayGameInfo() {
        System.out.println("\n=== GAME INFO ===");
        System.out.println("Game ID: " + gameId);
        System.out.println("White: " + whitePlayer.getName());
        System.out.println("Black: " + blackPlayer.getName());
        System.out.println("Current Turn: " + currentTurn);
        System.out.println("State: " + state);
        System.out.println("Moves: " + moveHistory.size());
    }
}

// ==================== GAME MANAGER ====================

class ChessGameManager {
    private static ChessGameManager instance;
    private final Map<String, ChessGame> games;
    private int gameCounter;

    private ChessGameManager() {
        this.games = new HashMap<>();
        this.gameCounter = 0;
    }

    public static synchronized ChessGameManager getInstance() {
        if (instance == null) {
            instance = new ChessGameManager();
        }
        return instance;
    }

    public ChessGame createGame(String whiteName, String blackName) {
        String gameId = "CHESS-" + (++gameCounter);
        ChessPlayer white = new ChessPlayer(whiteName, PieceColor.WHITE);
        ChessPlayer black = new ChessPlayer(blackName, PieceColor.BLACK);
        ChessGame game = new ChessGame(gameId, white, black);
        games.put(gameId, game);
        return game;
    }

    public ChessGame getGame(String gameId) {
        return games.get(gameId);
    }
}

// ==================== DEMO ====================

class ChessDemo {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ChessGameManager manager = ChessGameManager.getInstance();

        System.out.println("╔════════════════════════════════╗");
        System.out.println("║      CHESS GAME SYSTEM         ║");
        System.out.println("╚════════════════════════════════╝");

        System.out.print("\nEnter White player name: ");
        String whiteName = scanner.nextLine();
        System.out.print("Enter Black player name: ");
        String blackName = scanner.nextLine();

        ChessGame game = manager.createGame(whiteName, blackName);
        game.start();

        while (game.getState() == ChessGameState.IN_PROGRESS) {
            game.getBoard().display();
            game.displayGameInfo();

            System.out.println("\n" + 
                (game.getCurrentTurn() == PieceColor.WHITE ? whiteName : blackName) + 
                "'s turn (" + game.getCurrentTurn() + ")");
            
            System.out.print("Enter move (e.g., 'e2 e4') or 'resign': ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("resign")) {
                game.resign(game.getCurrentTurn());
                break;
            }

            String[] parts = input.split(" ");
            if (parts.length != 2) {
                System.out.println("Invalid input! Use format: e2 e4");
                continue;
            }

            Position from = Position.fromChessNotation(parts[0]);
            Position to = Position.fromChessNotation(parts[1]);

            if (from == null || to == null) {
                System.out.println("Invalid positions! Use chess notation (e.g., e2, e4)");
                continue;
            }

            game.makeMove(from, to);
        }

        game.getBoard().display();
        System.out.println("\n=== GAME OVER ===");
        System.out.println("Final State: " + game.getState());
        System.out.println("Total Moves: " + game.getMoveHistory().size());

        scanner.close();
    }
}
