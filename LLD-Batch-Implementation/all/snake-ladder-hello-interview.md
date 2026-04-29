# Snake and Ladder Game - HELLO Interview Framework

> **Companies**: Goldman Sachs, Amazon, Microsoft, Google, Flipkart +4 more  
> **Difficulty**: Medium  
> **Pattern**: Game Design + Configurable Board  
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

### 🎯 What is Snake and Ladder?

A classic board game: players roll dice, move forward on a numbered board (1-100). Landing on a **ladder bottom** teleports you UP to the top. Landing on a **snake head** slides you DOWN to the tail. First player to reach exactly cell 100 wins. Overshooting (rolling past 100) means you stay put.

**Real-world**: This is a popular interview question not because of the game itself, but because it tests **clean OOP design** — separating Dice, Board, Player, and Game into single-responsibility classes with proper validation.

### For L63 Microsoft Interview

1. **Clean OOP**: Each class has ONE responsibility (SRP)
2. **Map\<Integer, Integer\>** for snakes/ladders — not Snake/Ladder objects (KISS)
3. **Input validation**: Snake head must be > tail, no conflict on same cell
4. **Exact landing rule**: Overshoot = stay (not wrap around)
5. **Chain handling**: Ladder → snake head → slide down (chained effects)
6. **Configurable**: Board size, number of snakes/ladders, dice faces, player count

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "Board size?" → Configurable (default 100)
- "Number of players?" → 2-4
- "Overshoot rule?" → Stay at current position (standard rule)
- "Can a ladder land on a snake?" → Yes, chain until stable position
- "Multiple dice?" → P1, single 6-sided die for now

### Functional Requirements (P0)
1. **Board setup**: Configurable size, snakes (head→tail), ladders (bottom→top)
2. **Add snake/ladder**: With validation (snake goes DOWN, ladder goes UP, no conflicts)
3. **Roll dice**: Random 1-6, move player forward
4. **Snake/Ladder effects**: Apply chain of effects until stable position
5. **Win condition**: Exact landing on final cell (overshoot = no move)
6. **Turn management**: Cycle through players
7. **Game status**: Track turns, winner, player positions

### Non-Functional
- O(1) snake/ladder lookup (Map.containsKey)
- O(1) per dice roll and move
- Configurable board size, player count, dice faces

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌───────────────────────────────────────────────────────┐
│               SnakeAndLadderGame                      │
│  - board: SnakeBoard                                  │
│  - players: List<SnakePlayer>                         │
│  - dice: Dice                                         │
│  - currentPlayerIndex: int                            │
│  - gameOver: boolean                                  │
│  - winner: SnakePlayer                                │
│  - turnCount: int                                     │
│  - playTurn(): boolean                                │
│  - playToCompletion(maxTurns): SnakePlayer             │
└───────────────────┬───────────────────────────────────┘
                    │ has-a
        ┌───────────┼───────────┬──────────────┐
        ▼           ▼           ▼              ▼
┌──────────────┐ ┌─────────┐ ┌────────────────────────┐
│    Dice      │ │ Snake   │ │     SnakeBoard         │
│  - faces: 6  │ │ Player  │ │  - size: 100           │
│  - roll(): int│ │ - name  │ │  - snakes: Map<H→T>   │
│              │ │ - pos   │ │  - ladders: Map<B→T>   │
│              │ │ - hasWon│ │  - addSnake(h,t)       │
│              │ │         │ │  - addLadder(b,t)      │
└──────────────┘ └─────────┘ │  - getFinalPosition(p) │
                              └────────────────────────┘
```

### Class: Dice
| Attribute | Type | Description |
|-----------|------|-------------|
| faces | int | Number of sides (default 6) |

```java
class Dice {
    private final int faces;
    private final Random random;
    int roll() { return random.nextInt(faces) + 1; }
}
```

### Class: SnakeBoard
| Attribute | Type | Description |
|-----------|------|-------------|
| size | int | Total cells (default 100) |
| snakes | Map<Integer, Integer> | head → tail (goes DOWN) |
| ladders | Map<Integer, Integer> | bottom → top (goes UP) |

### Class: SnakePlayer
| Attribute | Type | Description |
|-----------|------|-------------|
| name | String | Player name |
| position | int | Current cell (starts at 0 = off board) |
| hasWon | boolean | True if reached final cell |

---

## 3️⃣ API Design

```java
class SnakeAndLadderGame {
    /** Play one turn for current player. Returns true if game continues. */
    boolean playTurn();
    
    /** Play until someone wins or maxTurns reached. */
    SnakePlayer playToCompletion(int maxTurns);
    
    /** Get current game status */
    String getStatus();
    
    boolean isGameOver();
    SnakePlayer getWinner();
}

class SnakeBoard {
    /** Add snake with validation: head > tail, no conflict */
    void addSnake(int head, int tail);
    
    /** Add ladder with validation: bottom < top, no conflict */
    void addLadder(int bottom, int top);
    
    /** Apply all snakes/ladders from a position until stable */
    int getFinalPosition(int position);
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Normal Move (No Snake/Ladder)

```
Alice at position 15, rolls 4:
  ├─ newPos = 15 + 4 = 19
  ├─ 19 <= 100 (board size)? → YES, valid move
  ├─ 19 == 100? → NO, not a win
  ├─ board.getFinalPosition(19):
  │   ├─ snakes.containsKey(19)? → NO
  │   └─ ladders.containsKey(19)? → NO
  │   → return 19 (no effect)
  └─ Alice moves to 19. Next player's turn.
```

### Scenario 2: Land on Ladder

```
Bob at position 25, rolls 3:
  ├─ newPos = 25 + 3 = 28
  ├─ board.getFinalPosition(28):
  │   ├─ snakes.containsKey(28)? → NO
  │   ├─ ladders.containsKey(28)? → YES! ladders[28] = 84
  │   │     🪜 Ladder! 28 → 84
  │   ├─ Loop again at 84:
  │   │   ├─ snakes.containsKey(84)? → NO
  │   │   └─ ladders.containsKey(84)? → NO
  │   └─ return 84 (stable)
  └─ Bob jumps from 28 to 84!
```

### Scenario 3: Land on Snake

```
Charlie at position 95, rolls 4:
  ├─ newPos = 95 + 4 = 99
  ├─ board.getFinalPosition(99):
  │   ├─ snakes.containsKey(99)? → YES! snakes[99] = 12
  │   │     🐍 Snake! 99 → 12
  │   ├─ Loop again at 12:
  │   │   ├─ snakes.containsKey(12)? → NO
  │   │   └─ ladders.containsKey(12)? → NO
  │   └─ return 12 (stable)
  └─ Charlie slides from 99 down to 12! Devastating.
```

### Scenario 4: Overshoot (Stay Put)

```
Alice at position 97, rolls 5:
  ├─ newPos = 97 + 5 = 102
  ├─ 102 > 100 (board size)? → YES! OVERSHOOT!
  └─ Alice stays at 97. Must roll exactly 3 to win.
```

### Scenario 5: Exact Win

```
Alice at position 97, rolls 3:
  ├─ newPos = 97 + 3 = 100
  ├─ 100 == 100? → YES! 🏆 WINNER!
  └─ Game over. Alice wins.
```

### Scenario 6: Chain Effect (Ladder → Snake)

```
Board: ladder 10→50, snake 50→5

Player at position 7, rolls 3:
  ├─ newPos = 10
  ├─ board.getFinalPosition(10):
  │   ├─ ladders.containsKey(10)? → YES! 10 → 50  🪜
  │   ├─ Loop at 50: snakes.containsKey(50)? → YES! 50 → 5  🐍
  │   ├─ Loop at 5: no snake/ladder → stable
  │   └─ return 5
  └─ Player goes 7 → 10 → 50 → 5 (ladder then snake!)
```

---

## 5️⃣ Design + Implementation

### Why Map<Integer, Integer> for Snakes/Ladders?

```java
// A snake IS just a mapping: head → tail
Map<Integer, Integer> snakes = new HashMap<>();
snakes.put(99, 12);   // head at 99, tail at 12
snakes.put(87, 24);   // head at 87, tail at 24

// O(1) check and teleport:
if (snakes.containsKey(position)) {
    position = snakes.get(position);
}
```

**Why not `class Snake { int head; int tail; }`?** Because a Map entry IS a snake. Creating a class for a simple key-value pair adds unnecessary complexity. **KISS principle** — Keep It Simple.

### Board with Validation

```java
class SnakeBoard {
    private final int size;
    private final Map<Integer, Integer> snakes = new HashMap<>();
    private final Map<Integer, Integer> ladders = new HashMap<>();

    void addSnake(int head, int tail) {
        if (head <= tail)
            throw new IllegalArgumentException("Snake head must be > tail");
        if (head >= size || tail < 1)
            throw new IllegalArgumentException("Invalid position");
        if (ladders.containsKey(head))
            throw new IllegalArgumentException("Cell " + head + " already has a ladder");
        snakes.put(head, tail);
    }

    void addLadder(int bottom, int top) {
        if (bottom >= top)
            throw new IllegalArgumentException("Ladder bottom must be < top");
        if (top > size || bottom < 1)
            throw new IllegalArgumentException("Invalid position");
        if (snakes.containsKey(bottom))
            throw new IllegalArgumentException("Cell " + bottom + " already has a snake");
        ladders.put(bottom, top);
    }

    /** Apply snakes/ladders chain until position is stable */
    int getFinalPosition(int position) {
        int maxChain = 10;  // prevent infinite loops from bad config
        int chain = 0;
        while (chain++ < maxChain) {
            if (snakes.containsKey(position)) {
                position = snakes.get(position);     // slide DOWN
            } else if (ladders.containsKey(position)) {
                position = ladders.get(position);    // climb UP
            } else {
                break;  // no effect → stable position
            }
        }
        return position;
    }
}
```

### Game Turn Logic

```java
class SnakeAndLadderGame {
    boolean playTurn() {
        if (gameOver) return false;

        SnakePlayer player = players.get(currentPlayerIndex);
        int roll = dice.roll();
        turnCount++;
        int newPos = player.getPosition() + roll;

        // Rule 1: Overshoot = stay
        if (newPos > board.getSize()) {
            nextPlayer();
            return true;  // game continues, player doesn't move
        }

        // Rule 2: Exact win
        if (newPos == board.getSize()) {
            player.setPosition(newPos);
            player.setWon();
            gameOver = true;
            winner = player;
            return true;
        }

        // Rule 3: Apply snakes/ladders
        int finalPos = board.getFinalPosition(newPos);
        player.setPosition(finalPos);
        nextPlayer();
        return true;
    }

    private void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Validation — Why It Matters

| Validation | Without | With |
|-----------|---------|------|
| Snake head ≤ tail | Snake goes UP (breaks game logic) | Correct: snake always goes DOWN |
| Snake + ladder same cell | Player teleported twice (undefined behavior) | Error: only one effect per cell |
| Position out of range | ArrayIndexOutOfBounds | Clear error message |
| Board size 0 | Instant win / crash | Validated in constructor |

At L63, **defensive programming** matters. The interviewer wants to see you handle invalid inputs gracefully.

### Deep Dive 2: Chain Handling with Safety Limit

```java
int maxChain = 10;  // WHY?
// Without limit: if snake 50→10 and ladder 10→50 exist → INFINITE LOOP!
// With limit: loop stops after 10 iterations → returns current position
// In production: detect cycle during addSnake/addLadder (graph cycle check)
```

### Deep Dive 3: Probability Analysis

Expected turns to finish a 100-cell board with standard 6-sided die:
- **Without snakes/ladders**: ~29 turns per player (expected value)
- **With snakes/ladders**: ~33 turns (snakes extend game)
- **Worst case**: Technically infinite (keep hitting snakes near 100)

The `maxTurns` parameter in `playToCompletion()` prevents infinite games.

### Deep Dive 4: Extensibility

| Extension | Implementation |
|-----------|---------------|
| **Extra roll on 6** | If roll == faces, `playTurn()` again before switching player |
| **Multiple dice** | `roll() = dice1.roll() + dice2.roll()` (2-12 range) |
| **Power-ups** | Map<Integer, PowerUp> — skip turn, immunity, double roll |
| **Safe zones** | Set<Integer> safeZones — snakes don't apply here |
| **Online multiplayer** | WebSocket + server-side dice (anti-cheat) |
| **Board builder** | Random snake/ladder placement with constraint validation |

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Roll exactly to cell 100 | WINNER! |
| Overshoot past 100 | Stay at current position |
| Land on snake at cell 99 | Slide to snake's tail (heartbreaking!) |
| Chain: ladder → snake | Apply both (getFinalPosition loops) |
| All players at 0 | Game hasn't started, normal play |
| 1 player only | Valid but boring (always wins eventually) |
| Snake head at cell 100 | Not allowed (100 is the goal) |
| Ladder top at cell 100 | Allowed — instant win if you land on ladder bottom! |

### Deep Dive 6: Complexity

| Operation | Time | Space |
|-----------|------|-------|
| roll() | O(1) | O(1) |
| getFinalPosition | O(C) where C = chain length (≤10) | O(1) |
| playTurn | O(1) | O(1) |
| addSnake/addLadder | O(1) | O(1) per snake/ladder |
| Total board space | — | O(S + L) where S=snakes, L=ladders |
| Total game space | — | O(S + L + P) where P=players |

---

## 📋 Interview Checklist (L63)

- [ ] **Clean OOP**: Dice, Board, Player, Game — each single responsibility
- [ ] **Map\<Integer, Integer\>**: Justified over Snake/Ladder classes (KISS)
- [ ] **Validation**: Snake head > tail, no conflicts, range checks
- [ ] **Exact landing**: Overshoot = stay (standard rule)
- [ ] **Chain handling**: Loop with safety limit for ladder→snake chains
- [ ] **getFinalPosition**: While loop until stable, maxChain prevents infinite loop
- [ ] **Turn management**: Cycle through players with modulo
- [ ] **Edge cases**: Cell 100 snake, overshoot, chain effects

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (Board, Dice, Player, Game) | 5 min |
| Implementation (validation, turn logic, chain handling) | 15 min |
| Deep Dives (chain safety, extensibility, probability) | 10 min |
| **Total** | **~35 min** |

See `SnakeAndLadderSystem.java` for full implementation with 2-player, 4-player, small board, and edge case demos.
