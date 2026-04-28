# Tic-Tac-Toe - HELLO Interview Framework

> **Companies**: Google, Amazon, Apple, Microsoft +4 more  
> **Difficulty**: Medium  
> **Key Insight**: O(1) win detection using row/col/diagonal sums  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 Why Interviewers Ask This

Tic-Tac-Toe seems trivially simple — and that's the trap. The real test is: **Can you detect a winner in O(1) per move instead of O(N²)?**

- **Naive approach**: After every move, scan all rows, columns, and diagonals → O(N²)
- **Optimal approach**: Maintain running sums per row/col/diagonal → **O(1)** per move check

**For L63 Microsoft**: Show the O(1) optimization unprompted. Generalize to N×N. Discuss minimax for AI. Show clean separation of Board (state + win logic), Game (rules + turns), Player.

---

## 1️⃣ Requirements

### Functional (P0)
1. N×N board (configurable, default 3×3)
2. Two players: X and O, alternating turns
3. Place mark at (row, col) — validate empty, in bounds
4. **Win detection**: Row, column, or diagonal filled by one player
5. **Draw detection**: Board full with no winner
6. Game status: IN_PROGRESS, X_WINS, O_WINS, DRAW
7. Multiple games (new game with score tracking)

### Non-Functional
- O(1) win check per move
- O(N²) space for board
- O(1) move validation

---

## 2️⃣ Core Entities

```
┌────────────────────────────────────────────┐
│           TicTacToeGame                    │
│  - board: TTTBoard                         │
│  - playerX, playerO: TTTPlayer             │
│  - currentPlayer: TTTPlayer                │
│  - status: GameStatus                      │
│  - gamesPlayed, makeMove(), newGame()      │
└──────────────────┬─────────────────────────┘
                   │ has-a
                   ▼
┌────────────────────────────────────────────┐
│              TTTBoard                      │
│  - grid: CellState[N][N]                   │
│  - rowSums: int[N]    ← O(1) win check!   │
│  - colSums: int[N]    ← O(1) win check!   │
│  - diagSum: int        ← main diagonal     │
│  - antiDiagSum: int    ← anti-diagonal     │
│  - moveCount: int      ← for draw check    │
│  - placeMark(row, col, mark): boolean      │
│  - isValidMove(row, col): boolean          │
│  - isFull(): boolean                       │
└────────────────────────────────────────────┘
```

---

## 3️⃣ API Design

```java
class TicTacToeGame {
    /** Place mark and check for win. Returns game status. */
    TTTGameStatus makeMove(int row, int col);
    /** Reset board for new game */
    void newGame();
    /** Get scoreboard */
    String getScoreboard();
}

class TTTBoard {
    /** Place mark. Returns true if this move WINS. O(1)! */
    boolean placeMark(int row, int col, CellState mark);
    boolean isValidMove(int row, int col);
    boolean isFull();
}
```

---

## 4️⃣ Data Flow

### Scenario: X places at (0, 2) on 3×3 board

```
makeMove(0, 2)
  ├─ Validate: game not over ✓
  ├─ Validate: board.isValidMove(0, 2) → cell is EMPTY ✓
  │
  ├─ board.placeMark(0, 2, X):
  │   ├─ grid[0][2] = X
  │   ├─ val = +1 (X=+1, O=-1)
  │   ├─ rowSums[0] += 1     → rowSums[0] = 3?  ← CHECK!
  │   ├─ colSums[2] += 1     → colSums[2] = 3?
  │   ├─ (0 == 2)? NO → skip diagSum
  │   ├─ (0 + 2 == 2)? YES → antiDiagSum += 1
  │   │
  │   └─ |rowSums[0]| == 3? → YES! → return true (WIN!)
  │
  ├─ status = X_WINS
  └─ "Alice WINS!"
```

**Key**: Only 4 comparisons needed per move (rowSum, colSum, diagSum, antiDiagSum). O(1)!

---

## 5️⃣ Design + Implementation

### The O(1) Win Detection (Core Insight)

```java
class TTTBoard {
    private final int size;
    private final CellState[][] grid;
    private final int[] rowSums;      // track sum per row
    private final int[] colSums;      // track sum per column
    private int diagSum;              // main diagonal (\)
    private int antiDiagSum;          // anti-diagonal (/)
    private int moveCount;

    /**
     * Place mark. Returns TRUE if this move wins.
     * X contributes +1, O contributes -1.
     * If any sum reaches +N or -N, all N cells are the same player.
     */
    boolean placeMark(int row, int col, CellState mark) {
        if (!isValidMove(row, col)) return false;
        
        grid[row][col] = mark;
        moveCount++;
        
        int val = (mark == CellState.X) ? 1 : -1;
        
        rowSums[row] += val;
        colSums[col] += val;
        if (row == col) diagSum += val;                    // on main diagonal
        if (row + col == size - 1) antiDiagSum += val;     // on anti-diagonal
        
        // Win check: O(1) — just 4 absolute value comparisons
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
}
```

### Why This Works

```
3×3 board, X plays:

After X at (0,0): rowSums[0]=1, colSums[0]=1, diagSum=1
After X at (0,1): rowSums[0]=2, colSums[1]=1
After X at (0,2): rowSums[0]=3 → |3| == 3 → WIN!

For O: O at (1,0): rowSums[1]=-1, colSums[0]=0 (X and O cancel)

The math: 
  +1 for X, -1 for O
  Sum = +N → all X in that line
  Sum = -N → all O in that line
  Anything else → mixed or incomplete
```

### Game Class

```java
class TicTacToeGame {
    private final TTTBoard board;
    private final TTTPlayer playerX, playerO;
    private TTTPlayer currentPlayer;
    private TTTGameStatus status;

    TTTGameStatus makeMove(int row, int col) {
        if (status != TTTGameStatus.IN_PROGRESS) return status;
        if (!board.isValidMove(row, col)) return status; // invalid, no change

        boolean won = board.placeMark(row, col, currentPlayer.getMark());
        
        if (won) {
            status = (currentPlayer.getMark() == CellState.X) 
                ? TTTGameStatus.X_WINS : TTTGameStatus.O_WINS;
            currentPlayer.addWin();
        } else if (board.isFull()) {
            status = TTTGameStatus.DRAW;
        } else {
            currentPlayer = (currentPlayer == playerX) ? playerO : playerX;
        }
        return status;
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: O(1) vs O(N²) Comparison

| Approach | Check per move | Total for N moves | Space |
|----------|---------------|-------------------|-------|
| **Scan board** | O(N²) scan rows+cols+diags | O(N³) | O(1) |
| **Sum arrays** | O(1) check 4 values | **O(N)** | O(N) |

For N=3: Scan = 9 checks × 9 moves = 81 operations. Sum = 4 checks × 9 moves = 36 operations.
For N=100: Scan = 10,000 per move. Sum = 4 per move. **250× faster!**

### Deep Dive 2: N×N Generalization

Our solution works for ANY N:
- rowSums[N], colSums[N], diagSum, antiDiagSum
- Win condition: |sum| == N
- Space: O(N) for sum arrays + O(N²) for grid
- Works identically for 3×3, 5×5, 10×10, 100×100

### Deep Dive 3: Minimax AI (Extension)

For an AI opponent, use **minimax algorithm**:
```
int minimax(board, depth, isMaximizing):
    if terminal state: return score (+10 for X win, -10 for O win, 0 draw)
    
    if isMaximizing (X's turn):
        bestScore = -∞
        for each empty cell:
            make move
            score = minimax(board, depth+1, false)
            undo move
            bestScore = max(bestScore, score)
        return bestScore
    else (O's turn):
        bestScore = +∞
        for each empty cell:
            make move
            score = minimax(board, depth+1, true)
            undo move
            bestScore = min(bestScore, score)
        return bestScore
```

**With alpha-beta pruning**: Cuts search space by ~50%. For 3×3: 9! = 362,880 states without pruning, ~1000 with pruning.

### Deep Dive 4: Online Multiplayer (Extension)

For networked Tic-Tac-Toe:
- **WebSocket** for real-time moves
- **Game session** with unique ID
- **Turn validation** on server (prevent cheating)
- **Reconnection** handling (resume game state)

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Move on occupied cell | isValidMove returns false |
| Move out of bounds | isValidMove returns false |
| Move after game over | makeMove returns current status |
| Draw (full board, no winner) | moveCount == N², no win detected |
| Center cell on diagonal | Both diagSum AND antiDiagSum updated (3×3 center) |
| Corner cell | May be on main diagonal, anti-diagonal, or both |

### Deep Dive 6: Complexity

| Operation | Time | Space |
|-----------|------|-------|
| placeMark | O(1) | — |
| Win check | O(1) | — |
| Draw check | O(1) | — |
| Board space | — | O(N²) |
| Sum arrays | — | O(N) |
| isValidMove | O(1) | — |

---

## 📋 Interview Checklist (L63)

- [ ] **O(1) win detection**: Sum arrays, not board scanning
- [ ] **Math explanation**: +1 for X, -1 for O, |sum|==N means win
- [ ] **N×N generalization**: Works for any board size
- [ ] **Clean separation**: Board (state) vs Game (rules) vs Player
- [ ] **Draw detection**: moveCount == N² with no winner
- [ ] **Edge cases**: Occupied cell, out of bounds, game over
- [ ] **Minimax**: Mention for AI extension (alpha-beta pruning)

See `TicTacToeSystem.java` for full implementation with 3×3, 5×5, and multiple-game demos.
