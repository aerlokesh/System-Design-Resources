# Chess Game - HELLO Interview Framework

> **Companies**: Microsoft, Salesforce, Adobe, Amazon, Goldman Sachs +6 more  
> **Difficulty**: Medium  
> **Primary Pattern**: Factory  
> **Time**: 35 minutes  
> **Reference**: [HelloInterview LLD Delivery](https://www.hellointerview.com/learn/low-level-design/in-a-hurry/delivery)

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions to Ask
- "Standard 8×8 chess or variants?" → Standard chess
- "Do we need to validate all move rules?" → Yes, each piece has specific movement rules
- "Checkmate detection?" → Yes, check and checkmate
- "Do we need special moves (castling, en passant, promotion)?" → P1, mention but not required
- "Two players or AI?" → Two human players, turn-based
- "Undo/redo?" → Out of scope (that's a separate Command pattern problem)

### Functional Requirements

#### Must Have (P0)
1. **Board Setup**
   - Standard 8×8 board with proper piece placement
   - White at bottom (rows 1-2), Black at top (rows 7-8)

2. **Piece Movement**
   - Each piece type has specific movement rules (King, Queen, Rook, Bishop, Knight, Pawn)
   - Validate moves before applying
   - Capture opponent pieces

3. **Turn Management**
   - Alternating turns (White starts)
   - Player can only move their own pieces
   - A turn consists of selecting a piece and a destination

4. **Check & Checkmate**
   - Detect when a king is in check
   - Player in check MUST resolve check
   - Detect checkmate (game over)
   - Detect stalemate (draw)

5. **Game State**
   - Track game status: ACTIVE, CHECK, CHECKMATE, STALEMATE, RESIGNED
   - Track captured pieces

#### Nice to Have (P1)
- Castling (king-rook swap)
- En passant (special pawn capture)
- Pawn promotion (pawn reaches opposite end)
- Move history / notation (algebraic notation)
- Timer / clock
- Undo/Redo (Command pattern)

### Non-Functional Requirements
- **Correctness**: All moves must follow standard chess rules
- **Performance**: Move validation < 5ms
- **Memory**: O(64) for board + O(32) for pieces

### Constraints & Assumptions
- 2 players, local game (same machine)
- No network/multiplayer
- Console-based interface
- Standard pieces only

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────────────────────────────────────┐
│                     Game                          │
│  - board: Board                                  │
│  - players: Player[2]                            │
│  - currentTurn: Color                            │
│  - status: GameStatus                            │
│  - makeMove(from, to): boolean                   │
└──────────────────────┬───────────────────────────┘
                       │ has-a
           ┌───────────┼───────────┐
           ▼           ▼           ▼
┌──────────────┐ ┌──────────┐ ┌──────────────┐
│    Board     │ │  Player  │ │  GameStatus  │
│  - grid[8][8]│ │  - name  │ │  ACTIVE      │
│  - getPiece()│ │  - color │ │  CHECK       │
│  - movePiece │ │          │ │  CHECKMATE   │
│  - isCheck() │ └──────────┘ │  STALEMATE   │
└──────┬───────┘              │  RESIGNED    │
       │ contains              └──────────────┘
       ▼
┌──────────────────────────────────┐
│         Piece (abstract)         │
│  - color: Color                  │
│  - position: Position            │
│  - canMove(board, from, to)      │
│  - getValidMoves(board): List    │
│  - getSymbol(): char             │
└──────────┬───────────────────────┘
           │ Factory creates
  ┌────────┼────────┬──────────┬──────────┬──────────┐
  ▼        ▼        ▼          ▼          ▼          ▼
 King    Queen    Rook      Bishop    Knight     Pawn
  ♚        ♛       ♜         ♝         ♞        ♟
```

### Enum: Color
```
WHITE, BLACK
```

### Enum: GameStatus
```
ACTIVE     → Game in progress, no check
CHECK      → Current player's king is in check
CHECKMATE  → Current player has no legal moves while in check (game over)
STALEMATE  → Current player has no legal moves but NOT in check (draw)
RESIGNED   → A player resigned
```

### Class: Position
| Attribute | Type | Description |
|-----------|------|-------------|
| row | int | 0-7 (0=top=black side, 7=bottom=white side) |
| col | int | 0-7 (0=a-file, 7=h-file) |

### Abstract Class: Piece
| Attribute | Type | Description |
|-----------|------|-------------|
| color | Color | WHITE or BLACK |
| hasMoved | boolean | For castling/pawn first move |

| Method | Description |
|--------|-------------|
| canMove(board, from, to) | Validate if this piece can move from→to |
| getValidMoves(board, from) | Return all legal destination squares |
| getSymbol() | Unicode symbol (♔♕♖♗♘♙ / ♚♛♜♝♞♟) |

> **Factory Pattern**: `PieceFactory.create(PieceType, Color)` creates the right subclass.

### Piece Movement Rules (Key)
| Piece | Movement | Special |
|-------|----------|---------|
| **King** | 1 square any direction | Cannot move into check |
| **Queen** | Any direction, any distance | Straight + diagonal |
| **Rook** | Horizontal or vertical, any distance | Castling (P1) |
| **Bishop** | Diagonal, any distance | — |
| **Knight** | L-shape (2+1), can jump | Only piece that jumps |
| **Pawn** | Forward 1 (or 2 from start), diagonal capture | En passant, promotion (P1) |

### Class: Board
| Attribute | Type | Description |
|-----------|------|-------------|
| grid | Piece[8][8] | The chess board |

| Method | Description |
|--------|-------------|
| getPiece(row, col) | Get piece at position |
| movePiece(from, to) | Execute a move |
| isCheck(color) | Is this color's king in check? |
| isCheckmate(color) | Is this color checkmated? |
| isStalemate(color) | Is this color stalemated? |
| getKingPosition(color) | Find where the king is |

### Class: Game
| Attribute | Type | Description |
|-----------|------|-------------|
| board | Board | The game board |
| players | Player[2] | White and Black players |
| currentTurn | Color | Whose turn it is |
| status | GameStatus | Current game state |
| moveHistory | List<Move> | Record of moves |

---

## 3️⃣ API Design

### Game (Public API)

```java
public class Game {
    /**
     * Make a move from one position to another
     * @param fromRow, fromCol  Source square
     * @param toRow, toCol      Destination square
     * @return true if move was valid and executed
     * @throws IllegalMoveException if move violates rules
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol);
    
    /** Get current game status */
    public GameStatus getStatus();
    
    /** Get whose turn it is */
    public Color getCurrentTurn();
    
    /** Display the board */
    public String displayBoard();
    
    /** Player resigns */
    public void resign();
    
    /** Check if game is over */
    public boolean isGameOver();
}
```

### Piece (Abstract — implemented by each piece type)

```java
public abstract class Piece {
    /**
     * Can this piece move from 'from' to 'to' on the given board?
     * Does NOT check if move leaves own king in check (Game handles that).
     */
    public abstract boolean canMove(Board board, Position from, Position to);
    
    /**
     * Get all squares this piece can theoretically move to
     * (before filtering for check)
     */
    public abstract List<Position> getValidMoves(Board board, Position from);
    
    /** Get display symbol */
    public abstract char getSymbol();
}
```

### PieceFactory

```java
public class PieceFactory {
    /**
     * Factory Method: creates the right piece subclass
     */
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
```

---

## 4️⃣ Data Flow

### Scenario 1: Valid Move (e.g., e2→e4 — Pawn opening)

```
Player → Game.makeMove(6, 4, 4, 4)   // e2 to e4
  │
  ├─ Validate: It's WHITE's turn ✓
  ├─ board.getPiece(6, 4) → White Pawn ✓
  ├─ Piece belongs to current player ✓
  │
  ├─ pawn.canMove(board, (6,4), (4,4))
  │     ├─ Direction: forward (row decreasing for white) ✓
  │     ├─ Distance: 2 squares from starting row ✓
  │     ├─ Path clear: (5,4) is empty ✓
  │     ├─ Destination: (4,4) is empty ✓
  │     └─ Returns: true
  │
  ├─ Simulate move: temporarily execute on board
  ├─ Check: is own king still safe? → board.isCheck(WHITE) = false ✓
  ├─ Confirm move: board.movePiece((6,4), (4,4))
  │
  ├─ Switch turn: currentTurn = BLACK
  ├─ Update status:
  │     ├─ board.isCheck(BLACK)? → if yes, status = CHECK
  │     ├─ board.isCheckmate(BLACK)? → if yes, status = CHECKMATE
  │     └─ board.isStalemate(BLACK)? → if yes, status = STALEMATE
  │
  └─ Returns: true
```

### Scenario 2: Invalid Move — Would Leave King in Check

```
Player → Game.makeMove(7, 3, 5, 3)   // Move piece that's pinned
  │
  ├─ Piece can physically move there ✓
  ├─ Simulate move on board
  ├─ Check: board.isCheck(WHITE) = TRUE  ✗
  │     └─ Moving this piece exposes king to attack!
  ├─ Revert simulation
  └─ Returns: false (IllegalMoveException: "Move leaves king in check")
```

### Scenario 3: Checkmate

```
After a series of moves... (Scholar's Mate)

Game.makeMove(Queen to h7)
  ├─ Move executed
  ├─ Switch turn to BLACK
  ├─ Check: isCheck(BLACK)? → YES (queen attacks king)
  ├─ Check: isCheckmate(BLACK)?
  │     ├─ Can king move? → No (all squares attacked or blocked)
  │     ├─ Can any piece block? → No
  │     ├─ Can any piece capture attacker? → No
  │     └─ Result: CHECKMATE
  ├─ status = CHECKMATE
  └─ Game over! White wins.
```

---

## 5️⃣ Design

### Design Pattern 1: Factory Pattern (Primary)

**Why Factory?**
- 6 different piece types, each with unique behavior
- Board initialization creates 32 pieces
- Clean creation without exposing subclass details
- Easy to extend (add new piece types for variants)

```java
// Without Factory: cluttered, knows all subclasses
Piece piece;
if (type == KING) piece = new King(color);
else if (type == QUEEN) piece = new Queen(color);
// ... 4 more if-else

// With Factory: clean, single point of creation
Piece piece = PieceFactory.create(type, color);
```

### Design Pattern 2: Template Method (in Piece)

Each piece overrides `canMove()` but shares common validation:
```
Piece.validateMove(board, from, to):
  1. Check bounds (template — shared)
  2. Check not same position (template — shared)
  3. Check not capturing own piece (template — shared)
  4. Check piece-specific rules (abstract — overridden)
```

### Design Pattern 3: Strategy (for Move Validation)

Sliding pieces (Queen, Rook, Bishop) share "path must be clear" logic:
```
SlidingPiece extends Piece:
  - isPathClear(board, from, to): check no pieces in between
  - Rook: horizontal/vertical directions
  - Bishop: diagonal directions
  - Queen: all 8 directions (Rook + Bishop combined)
```

### Move Validation — The Critical Logic

```
Game.makeMove(from, to):
  1. Basic validation (bounds, turn, piece exists, own piece)
  2. Piece-specific: piece.canMove(board, from, to)
  3. Simulate move on copy of board
  4. Check: does simulated board have own king in check?
     - YES → illegal move (revert)
     - NO  → execute move
  5. After move: check opponent's king status
     - In check + no legal moves → CHECKMATE
     - Not in check + no legal moves → STALEMATE
     - In check + has legal moves → CHECK
```

### Data Structure Choices

| Structure | Purpose | Why |
|-----------|---------|-----|
| `Piece[8][8]` | Board grid | O(1) access by position, natural 2D mapping |
| `List<Position>` | Valid moves list | Collected per piece, filtered for check |
| `List<Move>` | Move history | Append-only, for notation/undo |
| `enum PieceType` | 6 piece types | Used by Factory |

### Board Representation
```
     0   1   2   3   4   5   6   7
  ┌───┬───┬───┬───┬───┬───┬───┬───┐
0 │ ♜ │ ♞ │ ♝ │ ♛ │ ♚ │ ♝ │ ♞ │ ♜ │  8 (Black pieces)
  ├───┼───┼───┼───┼───┼───┼───┼───┤
1 │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │  7 (Black pawns)
  ├───┼───┼───┼───┼───┼───┼───┼───┤
2 │   │   │   │   │   │   │   │   │  6
  ├───┼───┼───┼───┼───┼───┼───┼───┤
3 │   │   │   │   │   │   │   │   │  5
  ├───┼───┼───┼───┼───┼───┼───┼───┤
4 │   │   │   │   │   │   │   │   │  4
  ├───┼───┼───┼───┼───┼───┼───┼───┤
5 │   │   │   │   │   │   │   │   │  3
  ├───┼───┼───┼───┼───┼───┼───┼───┤
6 │ ♙ │ ♙ │ ♙ │ ♙ │ ♙ │ ♙ │ ♙ │ ♙ │  2 (White pawns)
  ├───┼───┼───┼───┼───┼───┼───┼───┤
7 │ ♖ │ ♘ │ ♗ │ ♕ │ ♔ │ ♗ │ ♘ │ ♖ │  1 (White pieces)
  └───┴───┴───┴───┴───┴───┴───┴───┘
    a   b   c   d   e   f   g   h
```

---

### Complete Implementation

```java
// See ChessGame.java for full runnable implementation (~500 lines)
// Includes: all 6 piece types, Board, Game, PieceFactory, check/checkmate detection
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Check and Checkmate Detection

**isCheck(color)**:
```
1. Find king position of 'color'
2. For every opponent piece on board:
   a. Can it move to king's position?
   b. If YES → king is in check
3. Return true if any opponent threatens king
```
Time: O(P × M) where P = opponent pieces, M = max moves per piece

**isCheckmate(color)**:
```
1. Must be in check first
2. For every piece of 'color':
   a. For every valid move of that piece:
      i. Simulate the move
      ii. Is king still in check?
      iii. If NO → not checkmate (this move escapes)
3. If no move escapes check → CHECKMATE
```
Time: O(P × M × P × M) worst case — acceptable for 32 pieces

### Deep Dive 2: Sliding Piece Path Validation

Queen, Rook, Bishop share "path must be clear" logic:

```java
boolean isPathClear(Board board, Position from, Position to) {
    int rowDir = Integer.signum(to.row - from.row);  // -1, 0, or 1
    int colDir = Integer.signum(to.col - from.col);  // -1, 0, or 1
    
    int r = from.row + rowDir, c = from.col + colDir;
    while (r != to.row || c != to.col) {
        if (board.getPiece(r, c) != null) return false; // blocked!
        r += rowDir; c += colDir;
    }
    return true; // path is clear
}
```

### Deep Dive 3: Why Factory over Constructor

| Approach | Code | Problem |
|----------|------|---------|
| Direct construction | `new Rook(WHITE)` | Caller must know all 6 subclasses |
| Factory | `PieceFactory.create(ROOK, WHITE)` | Single creation point |
| Abstract Factory | `ChessSetFactory.create()` | Over-engineering for chess |

Factory benefits:
- Board initialization reads cleaner
- Piece type can be stored as enum (serializable)
- Easy to add variant pieces
- Centralized piece creation logic

### Deep Dive 4: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Move out of bounds | Position validation (0-7) |
| Move own piece to own piece | canMove checks destination color |
| King moves into check | Simulation catches this |
| Pinned piece (can't move) | Simulation detects king exposure |
| Stalemate (no legal moves, no check) | isStalemate() after each turn |
| Pawn at edge (promotion) | P1: prompt for piece choice |
| Game already over | makeMove returns false |

### Deep Dive 5: Extensibility

**Adding Special Moves** (P1):
- **Castling**: Add `canCastle()` to King, check `hasMoved` for King and Rook
- **En Passant**: Track last move in Game, check in Pawn's canMove
- **Promotion**: Check if Pawn reaches row 0/7, prompt for replacement piece

**Adding Undo/Redo** (Command Pattern):
```
interface MoveCommand {
    void execute();
    void undo();
}
class ChessMove implements MoveCommand { ... }
Stack<MoveCommand> undoStack, redoStack;
```

### Deep Dive 6: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| Get piece at position | O(1) | — |
| Validate piece move | O(N) where N=board size | O(1) |
| Generate valid moves | O(N) per piece | O(N) |
| Check detection | O(P×N) | O(1) |
| Checkmate detection | O(P²×N²) worst case | O(N²) for simulation |
| Total board space | — | O(64) = O(1) |

Where P = pieces (≤32), N = board dimension (8)

---

## 📋 Interview Checklist

- [ ] **Requirements**: Clarified scope (standard chess, 2 players, check/checkmate)
- [ ] **Factory Pattern**: PieceFactory creates correct subclass from enum
- [ ] **Piece Hierarchy**: Abstract Piece → 6 concrete subclasses
- [ ] **Move Validation**: Piece-specific rules + simulation for check
- [ ] **Check/Checkmate**: Explained detection algorithm
- [ ] **Board Representation**: 2D array, O(1) access
- [ ] **Edge Cases**: Pinned pieces, stalemate, out of bounds
- [ ] **Extensibility**: Discussed castling, en passant, undo/redo

### Time Spent:
| Phase | Target | Actual |
|-------|--------|--------|
| Requirements | 3-5 min | — |
| Core Entities | 5 min | — |
| API Design | 5 min | — |
| Design + Code | 15-20 min | — |
| Deep Dives | 5-10 min | — |
| **Total** | **~35 min** | — |
