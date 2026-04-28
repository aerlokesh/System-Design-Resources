# Snake and Ladder Game - HELLO Interview Framework

> **Companies**: Goldman Sachs, Amazon, Microsoft, Google, Flipkart +4 more  
> **Difficulty**: Medium  
> **Pattern**: Game Design + Configurable Board  
> **Time**: 35 minutes

## Understanding the Problem

Classic board game: roll dice, move forward, hit snake (go down) or ladder (go up). First to exact-land on cell 100 wins. Tests **OOP design** (Dice, Board, Player, Game separation), **configurable rules**, and **validation**.

### For L63 Microsoft
1. **Clean OOP**: Dice, Board, Player, Game — each with single responsibility
2. **Map\<Integer, Integer\>** for snakes/ladders: O(1) lookup, no need for Snake/Ladder objects
3. **Input validation**: snake head > tail, no snake+ladder on same cell, floor/ceiling range
4. **Exact landing**: overshoot = no move (standard rule)
5. **Chain handling**: ladder landing on snake's head (recursive check)

---

## Key Design

```
Dice — faces, roll() returns random 1-N
SnakeBoard — size, snakes: Map<head→tail>, ladders: Map<bottom→top>
  - addSnake(head, tail) — validated: head > tail, no conflict
  - addLadder(bottom, top) — validated: bottom < top, no conflict
  - getFinalPosition(pos) — chain: apply snakes/ladders until stable
SnakePlayer — name, position (starts at 0), hasWon
SnakeAndLadderGame — board, players, dice, turn management
  - playTurn() — roll → move → check overshoot → apply snake/ladder → check win
  - playToCompletion(maxTurns) — with safety limit
```

### Why Map\<Integer, Integer\> (Not Snake Objects)?

```java
// Snake IS just a mapping: head → tail
Map<Integer, Integer> snakes = new HashMap<>();
snakes.put(99, 12);  // head at 99, tail at 12

// O(1) check and jump:
if (snakes.containsKey(position)) {
    position = snakes.get(position);  // slide down!
}
```

No need for `class Snake { int head; int tail; }` — a Map entry IS a snake. KISS principle.

### Core Logic: playTurn()

```java
boolean playTurn() {
    int roll = dice.roll();
    int newPos = player.getPosition() + roll;
    
    if (newPos > board.getSize()) → stay (overshoot!)
    if (newPos == board.getSize()) → WINNER!
    
    int finalPos = board.getFinalPosition(newPos);  // apply snakes/ladders
    player.setPosition(finalPos);
    nextPlayer();
}
```

### Chain Handling

```java
int getFinalPosition(int position) {
    int maxChain = 10; // prevent infinite loops from bad config
    while (maxChain-- > 0) {
        if (snakes.containsKey(position)) position = snakes.get(position);
        else if (ladders.containsKey(position)) position = ladders.get(position);
        else break;  // no snake or ladder here → done
    }
    return position;
}
// Handles: land on ladder → ladder top is snake head → slide down
```

### Validations
- `snake.head > snake.tail` (snake goes DOWN)
- `ladder.bottom < ladder.top` (ladder goes UP)
- No snake and ladder on same cell
- Board size > 0, players ≥ 2

### Deep Dives
- **Probability**: Expected ~30 turns to finish 100-cell board with 1 die
- **Custom dice**: Multiple dice, weighted dice
- **Power-ups**: Skip turn, extra roll on 6, safe zones
- **Online**: WebSocket for multiplayer, server-side dice roll (anti-cheat)

See `SnakeAndLadderSystem.java` for full implementation with 2-player, 4-player, and small board demos.
