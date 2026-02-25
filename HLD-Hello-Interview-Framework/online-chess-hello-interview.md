# Design Online Chess (Chess.com) - Hello Interview Framework

> **Question**: Design a chess platform similar to Chess.com that supports multiplayer games across multiple devices, with features like game creation, matchmaking, leaderboards, move undo functionality, and persistent data storage. Players create or accept challenges, get matched by rating and time control, and review past games and analyses across devices.
>
> **Asked at**: Meta
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Real-Time Gameplay**: Two players play chess with move validation, turn enforcement, and real-time move synchronization (< 100ms latency). Server is the authority — no client can cheat.
2. **Matchmaking**: Match players by Elo rating (±100 range) and time control (bullet/blitz/rapid/classical). Queue-based matchmaking with expanding search radius over time.
3. **Game Clocks**: Server-authoritative chess clocks with time controls (e.g., 5+3 blitz = 5 minutes + 3 second increment). Clock runs on server to prevent tampering.
4. **Move History & Game Persistence**: Full move log stored per game. Players can review completed games move-by-move. PGN (Portable Game Notation) export.
5. **Leaderboard**: Global and per-time-control rating leaderboards. Top N players, user's rank, nearby players.
6. **Game State Recovery**: If a player disconnects mid-game, they can reconnect and resume. Game state persisted server-side.

#### Nice to Have (P1)
- Move undo / takeback requests (opponent must accept).
- Draw offers, resignation, abort (within first 2 moves).
- Spectator mode (watch live games).
- Game analysis (engine evaluation, best-move suggestions post-game).
- Tournaments (Swiss-system, round-robin brackets).
- Chat during games.
- Puzzle mode (tactics training from game positions).
- Friends list, challenges between friends.

#### Below the Line (Out of Scope)
- Chess engine (Stockfish) integration for move evaluation.
- Cheat detection ML pipeline.
- Payment system for premium memberships.
- Mobile app native implementation details.
- Community features (forums, clubs).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Move Latency** | < 100ms P99 (server processing + network) | Real-time feel; chess clocks run on server |
| **Clock Accuracy** | ±10ms drift from true time | Fair play; clock is the competitive resource |
| **Matchmaking Latency** | < 10 seconds for 90% of players | Players expect quick matches |
| **Availability** | 99.99% for active games | Dropping a live game = lost Elo = angry user |
| **Consistency** | Strong for game state (move ordering) | Moves must be strictly ordered; no split-brain |
| **Durability** | Zero game loss after move acknowledgment | Completed games are permanent records |
| **Scale** | 10M DAU, 500K concurrent games, 5M concurrent users | Chess.com-scale platform |
| **Reconnection** | < 5 seconds to resume after disconnect | Auto-rejoin with full game state |

### Capacity Estimation

```
Users:
  Total users: 100M
  Daily active users: 10M
  Peak concurrent users: 5M
  Peak concurrent games: 500K (each game = 2 players)

Games:
  Games per day: 10M
  Average moves per game: 40 (each player makes ~20 moves)
  Moves per second: 10M games × 40 moves / 86400 sec = ~4,600 moves/sec
  Peak moves/sec: ~50K (during peak hours, tournaments)
  
  Average game duration: 10 minutes
  Game metadata: 2 KB (players, ratings, time control, result)
  Move log per game: 40 moves × 20 bytes = 800 bytes
  Total per game: ~3 KB

Matchmaking:
  Players queuing per second: ~1,000
  Average queue time: 5 seconds
  Queue size at any moment: ~5,000 players

Leaderboard:
  Total rated players: 50M
  Leaderboard queries/sec: ~10K (page loads with rank display)

Storage:
  Games per year: 10M/day × 365 = 3.65B games
  Storage per year: 3.65B × 3 KB = ~11 TB/year
  Active game state (in-memory): 500K × 5 KB = ~2.5 GB
  Leaderboard: 50M players × 100 bytes = ~5 GB
```

---

## 2️⃣ Core Entities

### Entity 1: Game
```java
public class Game {
    private final String gameId;            // UUID
    private final String whitePlayerId;
    private final String blackPlayerId;
    private final TimeControl timeControl;  // e.g., 5+3 blitz
    private final GameStatus status;        // WAITING, IN_PROGRESS, COMPLETED, ABANDONED
    private final GameResult result;        // WHITE_WINS, BLACK_WINS, DRAW, null
    private final TerminationReason reason; // CHECKMATE, TIMEOUT, RESIGNATION, DRAW_AGREEMENT, STALEMATE
    private final String fen;               // Current board position (FEN string)
    private final List<Move> moves;         // Ordered move history
    private final ClockState whiteClock;    // Remaining time for white
    private final ClockState blackClock;    // Remaining time for black
    private final Color turn;               // Whose turn: WHITE or BLACK
    private final int moveNumber;           // Current full move number
    private final Instant createdAt;
    private final Instant startedAt;        // First move timestamp
    private final Instant endedAt;
    private final long version;             // For optimistic locking
}

public enum GameStatus {
    WAITING,        // Waiting for opponent (challenge/matchmaking)
    IN_PROGRESS,    // Active game
    COMPLETED,      // Game finished normally
    ABANDONED       // Player disconnected, timed out
}

public record TimeControl(int baseTimeSeconds, int incrementSeconds, String label) {}
// Examples: TimeControl(300, 3, "5+3 Blitz"), TimeControl(60, 0, "1+0 Bullet")
```

### Entity 2: Move
```java
public class Move {
    private final int moveIndex;            // 0-based sequential index
    private final Color color;              // WHITE or BLACK
    private final String uci;               // UCI notation: "e2e4", "g1f3"
    private final String san;               // Standard Algebraic: "e4", "Nf3"
    private final String fenAfter;          // Board state after this move
    private final long clockRemainingMs;    // Player's clock after this move
    private final Instant timestamp;        // Server timestamp when move was processed
}
```

### Entity 3: Player
```java
public class Player {
    private final String playerId;          // UUID
    private final String username;
    private final String displayName;
    private final Map<String, Integer> ratings; // "bullet": 1450, "blitz": 1520, "rapid": 1600
    private final Map<String, Integer> gamesPlayed; // Per time control
    private final PlayerStatus status;      // ONLINE, IN_GAME, IDLE, OFFLINE
    private final Instant lastActiveAt;
    private final Instant createdAt;
}

public enum PlayerStatus {
    ONLINE, IN_GAME, IDLE, OFFLINE
}
```

### Entity 4: Matchmaking Request
```java
public class MatchmakingRequest {
    private final String requestId;
    private final String playerId;
    private final String timeControlLabel;  // "blitz", "bullet", "rapid"
    private final int playerRating;         // Current Elo in this time control
    private final int ratingRange;          // Acceptable range (starts at ±50, expands over time)
    private final Instant queuedAt;
    private final MatchmakingStatus status; // QUEUED, MATCHED, CANCELLED, EXPIRED
}
```

### Entity 5: Leaderboard Entry
```java
public class LeaderboardEntry {
    private final String playerId;
    private final String username;
    private final int rating;
    private final int rank;                 // 1-based global rank
    private final int gamesPlayed;
    private final double winRate;
}
```

### Entity 6: Clock State
```java
public class ClockState {
    private final long remainingMs;         // Milliseconds remaining
    private final Instant lastUpdatedAt;    // Server timestamp of last update
    private final boolean isRunning;        // True when it's this player's turn
}
```

---

## 3️⃣ API Design

### 1. Find Game (Matchmaking)
```
POST /api/v1/matchmaking/queue

Request:
{
  "time_control": "blitz",
  "rating_range": 100
}

Response (202 Accepted):
{
  "request_id": "req_123",
  "status": "QUEUED",
  "estimated_wait_seconds": 5
}

--- When match found (via WebSocket push): ---
{
  "type": "MATCH_FOUND",
  "game_id": "game_456",
  "opponent": { "user_id": "user_789", "username": "GrandMaster42", "rating": 1520 },
  "color": "WHITE",
  "time_control": { "base": 300, "increment": 3, "label": "5+3 Blitz" }
}
```

### 2. Make Move (WebSocket Message)
```
Client → Server (WebSocket):
{
  "type": "MOVE",
  "game_id": "game_456",
  "move": "e2e4"
}

Server → Both Players (WebSocket):
{
  "type": "MOVE_MADE",
  "game_id": "game_456",
  "move": { "uci": "e2e4", "san": "e4", "move_index": 0 },
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "clock": { "white": 299500, "black": 300000 },
  "turn": "BLACK",
  "timestamp": "2025-01-10T10:00:01.500Z"
}

--- On invalid move: ---
Server → Sender Only:
{
  "type": "MOVE_REJECTED",
  "game_id": "game_456",
  "move": "e2e5",
  "reason": "ILLEGAL_MOVE"
}
```

### 3. Get Game State (REST — for reconnection)
```
GET /api/v1/games/{game_id}

Response (200 OK):
{
  "game_id": "game_456",
  "white": { "user_id": "user_123", "username": "Alice", "rating": 1450 },
  "black": { "user_id": "user_789", "username": "GrandMaster42", "rating": 1520 },
  "status": "IN_PROGRESS",
  "time_control": { "base": 300, "increment": 3 },
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "moves": [
    { "index": 0, "uci": "e2e4", "san": "e4", "clock_remaining_ms": 299500, "timestamp": "..." }
  ],
  "clock": { "white_remaining_ms": 299500, "black_remaining_ms": 300000 },
  "turn": "BLACK",
  "move_number": 1
}
```

### 4. Resign / Offer Draw / Request Takeback (WebSocket)
```
Client → Server:
{ "type": "RESIGN", "game_id": "game_456" }
{ "type": "OFFER_DRAW", "game_id": "game_456" }
{ "type": "REQUEST_TAKEBACK", "game_id": "game_456" }

Server → Opponent:
{ "type": "DRAW_OFFERED", "game_id": "game_456", "from": "user_123" }

Opponent → Server:
{ "type": "ACCEPT_DRAW", "game_id": "game_456" }
{ "type": "DECLINE_DRAW", "game_id": "game_456" }
```

### 5. Get Leaderboard
```
GET /api/v1/leaderboards/blitz?page=1&page_size=50

Response (200 OK):
{
  "time_control": "blitz",
  "entries": [
    { "rank": 1, "username": "Magnus", "rating": 3200, "games_played": 5000, "win_rate": 0.85 },
    { "rank": 2, "username": "Hikaru", "rating": 3180, "games_played": 8000, "win_rate": 0.78 },
    ...
  ],
  "my_rank": { "rank": 45231, "rating": 1520 },
  "total_players": 5000000
}
```

### 6. Get Game History
```
GET /api/v1/users/{user_id}/games?page=1&page_size=20&time_control=blitz

Response (200 OK):
{
  "games": [
    {
      "game_id": "game_456",
      "opponent": { "username": "GrandMaster42", "rating": 1520 },
      "result": "WIN",
      "termination": "CHECKMATE",
      "moves_count": 34,
      "time_control": "5+3 Blitz",
      "played_at": "2025-01-10T10:00:00Z",
      "rating_change": +12
    }
  ],
  "pagination": { "page": 1, "total": 1234 }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Matchmaking → Game Creation
```
1. Player clicks "Play" → POST /api/v1/matchmaking/queue
   ↓
2. Matchmaking Service:
   a. Add player to queue: (rating, time_control, queued_at)
   b. Search for compatible opponent:
      - Same time control
      - Rating within ±range (starts ±50, expands by ±25 every 5 seconds)
   c. If match found:
      - Remove both players from queue
      - Create Game in DB (status=IN_PROGRESS)
      - Assign colors (random or alternate)
      - Assign Game Server (consistent hashing by game_id)
      - Notify both players via WebSocket: MATCH_FOUND
   d. If no match: wait, expand range, retry
   ↓
3. Both players connect via WebSocket to assigned Game Server
   ↓
4. Game Server loads game state, starts clocks on first move
```

### Flow 2: Making a Move (Critical Hot Path)
```
1. Player sends MOVE message via WebSocket
   ↓
2. Game Server (single authority for this game):
   a. Validate: is it this player's turn?
   b. Validate: is the move legal? (server-side chess engine)
   c. Stop player's clock, record elapsed time
   d. Apply move to board state (update FEN)
   e. Check for game-ending conditions:
      - Checkmate → game over, winner determined
      - Stalemate → draw
      - Insufficient material → draw
      - Threefold repetition → draw
      - 50-move rule → draw
   f. Start opponent's clock (add increment if applicable)
   g. Append move to move log
   h. Persist move to durable store (append-only)
   ↓
3. Broadcast to both players: MOVE_MADE (with updated FEN, clocks)
   ↓
4. If game over:
   a. Record result
   b. Update ratings (Elo calculation)
   c. Update leaderboard
   d. Persist completed game to Games DB
```

### Flow 3: Player Disconnection & Reconnection
```
1. Player's WebSocket connection drops
   ↓
2. Game Server detects disconnect (heartbeat timeout: 5 seconds)
   ↓
3. Start abandonment timer:
   - If player's turn: their clock continues running
   - If opponent's turn: opponent continues normally
   - After 60 seconds of disconnect: flag for possible abandonment
   ↓
4. Player reconnects:
   a. Authenticate
   b. Look up active game (player_id → game_id mapping)
   c. Connect to assigned Game Server via WebSocket
   d. Server sends full game state: GAME_STATE_SYNC
   e. Resume play from current position
   ↓
5. If clock runs out during disconnect: normal timeout loss
```

### Flow 4: Game End → Rating Update → Leaderboard
```
1. Game ends (checkmate, timeout, resignation, draw)
   ↓
2. Game Server:
   a. Mark game as COMPLETED in DB
   b. Publish "game-completed" event to Kafka
   ↓
3. Rating Service (Kafka consumer):
   a. Compute new Elo ratings for both players:
      new_rating = old_rating + K × (actual - expected)
      K = 20 (default), higher for new players
   b. Update player ratings in DB
   c. Publish "rating-updated" event
   ↓
4. Leaderboard Service:
   a. Update Redis sorted set: ZADD leaderboard:{time_control} <new_rating> <player_id>
   b. Leaderboard reflects new rankings immediately
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│                                                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                                 │
│  │ Web App   │  │ iOS App   │  │ Android  │                                 │
│  │ (React)   │  │           │  │ App      │                                 │
│  └─────┬─────┘  └─────┬────┘  └─────┬─────┘                                 │
│        │  WebSocket     │  WebSocket  │  WebSocket                           │
└────────┼───────────────┼────────────┼─────────────────────────────────────────┘
         │               │            │
         └───────────────┼────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY / LOAD BALANCER                                │
│                                                                               │
│  • REST API routing           • WebSocket connection routing                 │
│  • Auth (JWT)                 • Sticky sessions by game_id                   │
│  • Rate limiting              • Health checks                                │
└───────────┬───────────────────────────┬───────────────────────────────────────┘
            │ REST                      │ WebSocket
            ▼                           ▼
┌────────────────────┐    ┌──────────────────────────────────────────┐
│ REST API SERVICE    │    │ GAME SERVER CLUSTER                       │
│                     │    │ (Stateful — each server owns N games)     │
│ • Matchmaking API   │    │                                           │
│ • Game history API  │    │ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│ • Leaderboard API   │    │ │ Server 1  │ │ Server 2  │ │ Server N  │ │
│ • User profile API  │    │ │ ~1000     │ │ ~1000     │ │ ~1000     │ │
│                     │    │ │ active    │ │ active    │ │ active    │ │
│ Pods: 20-50         │    │ │ games     │ │ games     │ │ games     │ │
│ (stateless)         │    │ │           │ │           │ │           │ │
└────────┬────────────┘    │ │ In-memory │ │ In-memory │ │ In-memory │ │
         │                 │ │ game state│ │ game state│ │ game state│ │
         │                 │ │ + chess   │ │ + chess   │ │ + chess   │ │
         │                 │ │   engine  │ │   engine  │ │   engine  │ │
         │                 │ └──────────┘ └──────────┘ └──────────┘ │
         │                 │                                           │
         │                 │ Servers: 500 (500K games / 1000 per srv) │
         │                 └──────────────┬────────────────────────────┘
         │                                │
    ┌────┼────────────────────────────────┼──────────┐
    │    │                                │          │
    ▼    ▼                                ▼          ▼
┌──────────┐ ┌──────────────┐  ┌──────────────┐ ┌──────────────┐
│ MATCHMK  │ │ GAME STORE   │  │ PLAYER STORE │ │ LEADERBOARD  │
│ SERVICE  │ │              │  │              │ │ CACHE        │
│          │ │ PostgreSQL   │  │ PostgreSQL   │ │              │
│ Redis    │ │ Sharded      │  │              │ │ Redis        │
│ sorted   │ │ by game_id   │  │ • Profiles   │ │ Sorted Set   │
│ sets by  │ │              │  │ • Ratings    │ │ per time     │
│ rating   │ │ • Games      │  │ • Preferences│ │ control      │
│ + time   │ │ • Moves      │  │              │ │              │
│ control  │ │ • Results    │  │ ~500 GB      │ │ ~5 GB        │
│          │ │              │  │              │ │              │
│ ~100 MB  │ │ ~11 TB/year  │  │              │ │              │
└──────────┘ └──────────────┘  └──────────────┘ └──────────────┘

                    │
                    │ Kafka: "game-events"
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA                                                │
│                                                                               │
│  Topic: "game-events"       (move-made, game-completed, game-abandoned)      │
│  Topic: "rating-updates"    (Elo recalculation results)                      │
│  Topic: "spectator-feed"    (live move broadcast for spectators)             │
└──────────┬──────────────────────────┬────────────────────────────────────────┘
           │                          │
     ┌─────▼──────────────┐    ┌─────▼──────────────┐
     │ RATING SERVICE      │    │ ANALYTICS SERVICE   │
     │                     │    │                     │
     │ • Elo calculation   │    │ • Game statistics   │
     │ • K-factor by       │    │ • Player analytics  │
     │   experience level  │    │ • Popular openings  │
     │ • Update player DB  │    │                     │
     │ • Update leaderboard│    │ Pods: 5-10          │
     │                     │    └─────────────────────┘
     │ Pods: 5-10          │
     └─────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Game Servers** | Authoritative game state, move validation, clock management | Go/Rust on K8s (stateful) | 500 servers × 1000 games each |
| **REST API Service** | Matchmaking, history, leaderboards, profiles | Java/Go on K8s (stateless) | 20-50 pods |
| **Matchmaking Service** | Queue management, opponent matching by rating | Redis sorted sets + custom logic | 3-5 pods |
| **Game Store** | Persistent storage for games and moves | PostgreSQL (sharded by game_id) | ~11 TB/year |
| **Player Store** | User profiles, ratings, preferences | PostgreSQL | ~500 GB |
| **Leaderboard Cache** | Real-time ranking per time control | Redis Sorted Sets | ~5 GB |
| **Rating Service** | Elo computation on game completion | Kafka consumer (Go) | 5-10 pods |
| **Kafka** | Event streaming for async processing | Apache Kafka | 5+ brokers |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Server-Authoritative Game State — No Cheating

**The Problem**: In a competitive game, the server must be the single source of truth. Clients only suggest moves; the server validates and applies them. This prevents move tampering, clock manipulation, and illegal moves.

```java
public class GameEngine {

    /**
     * Server authority model:
     * 
     * 1. Client sends: { "move": "e2e4" } (just the move, no state)
     * 2. Server validates:
     *    a. Is it this player's turn?
     *    b. Is the move legal on the current board? (full chess rule engine)
     *    c. Does the player have time remaining on their clock?
     * 3. Server applies:
     *    a. Update board state (FEN)
     *    b. Stop player's clock, start opponent's clock
     *    c. Check for game-ending conditions
     *    d. Append to move log
     * 4. Server broadcasts: updated state to both players
     * 
     * The client NEVER modifies game state.
     * The client's board is a VIEW of the server state.
     * If client and server disagree → server wins, client re-syncs.
     * 
     * Chess rule validation library: server-side chess engine
     * (e.g., chess.js equivalent in Java/Go: chesslib, notnil/chess)
     * Validates: legal moves, check, checkmate, stalemate,
     *            en passant, castling rights, pawn promotion
     */
    
    private final ChessBoard board;         // Server-side board representation
    private final ClockManager clock;       // Server-authoritative clocks
    private final MoveLog moveLog;          // Append-only move history
    
    public MoveResult processMove(String gameId, String playerId, String uciMove) {
        Game game = getGame(gameId);
        
        // Validation 1: Is it this player's turn?
        if (!game.isPlayersTurn(playerId)) {
            return MoveResult.rejected("NOT_YOUR_TURN");
        }
        
        // Validation 2: Does player have time?
        if (game.getClockForPlayer(playerId).getRemainingMs() <= 0) {
            endGame(game, game.getOpponent(playerId), TerminationReason.TIMEOUT);
            return MoveResult.gameOver("TIMEOUT");
        }
        
        // Validation 3: Is the move legal?
        if (!board.isLegalMove(uciMove)) {
            return MoveResult.rejected("ILLEGAL_MOVE");
        }
        
        // Apply move
        board.applyMove(uciMove);
        String newFen = board.toFen();
        
        // Update clocks
        long elapsed = clock.stopAndGetElapsed(playerId);
        clock.addIncrement(playerId);
        clock.start(game.getOpponent(playerId));
        
        // Record move
        Move move = new Move(
            moveLog.size(), game.getColorForPlayer(playerId),
            uciMove, board.toSan(uciMove), newFen,
            clock.getRemaining(playerId), Instant.now());
        moveLog.append(move);
        
        // Persist move (durable, append-only)
        persistMove(gameId, move);
        
        // Check game-ending conditions
        if (board.isCheckmate()) {
            endGame(game, playerId, TerminationReason.CHECKMATE);
        } else if (board.isStalemate()) {
            endGame(game, null, TerminationReason.STALEMATE);
        } else if (board.isInsufficientMaterial()) {
            endGame(game, null, TerminationReason.INSUFFICIENT_MATERIAL);
        }
        
        return MoveResult.accepted(move, newFen, clock.getState());
    }
}
```

---

### Deep Dive 2: Server-Authoritative Chess Clocks

**The Problem**: Chess clocks must be accurate to ±10ms. If clocks ran on the client, players could cheat by slowing their clock. The server must be the single authority for time tracking.

```java
public class ClockManager {

    /**
     * Server-side clock management:
     * 
     * Each game has two clocks (white, black).
     * Only one clock runs at a time (the active player's).
     * 
     * On move:
     *   1. Record server timestamp when move arrives
     *   2. Elapsed = now - last_move_timestamp
     *   3. Deduct elapsed from active player's clock
     *   4. Add increment (if applicable)
     *   5. Start opponent's clock
     * 
     * Network compensation:
     *   Problem: network latency between client→server adds artificial time
     *   Solution: measure RTT (round-trip time) per player
     *     → Compensate: elapsed_adjusted = elapsed - (RTT / 2)
     *     → Cap compensation at max 500ms to prevent gaming
     * 
     * Server clock tick:
     *   Passive: recalculate remaining time on each move
     *   Timeout detection: scheduled timer per game
     *     → If no move received within remaining_time + buffer → timeout
     */
    
    private long whiteRemainingMs;
    private long blackRemainingMs;
    private Instant activeClockStartedAt;   // When the current player's clock started
    private Color activeColor;
    private final int incrementMs;
    private final Map<String, Long> playerRttMs;  // Network RTT per player
    
    public long stopAndGetElapsed(String playerId) {
        Instant now = Instant.now();
        long rawElapsed = Duration.between(activeClockStartedAt, now).toMillis();
        
        // Network compensation: subtract half RTT
        long rtt = playerRttMs.getOrDefault(playerId, 0L);
        long compensation = Math.min(rtt / 2, 500);  // Cap at 500ms
        long adjustedElapsed = Math.max(0, rawElapsed - compensation);
        
        // Deduct from active player's clock
        if (activeColor == Color.WHITE) {
            whiteRemainingMs -= adjustedElapsed;
        } else {
            blackRemainingMs -= adjustedElapsed;
        }
        
        return adjustedElapsed;
    }
    
    public void addIncrement(String playerId) {
        if (activeColor == Color.WHITE) {
            whiteRemainingMs += incrementMs;
        } else {
            blackRemainingMs += incrementMs;
        }
    }
    
    public void start(String playerId) {
        activeClockStartedAt = Instant.now();
        activeColor = getColorForPlayer(playerId);
    }
    
    // Timeout detection: scheduled task per game
    public void scheduleTimeoutCheck(String gameId) {
        long remainingMs = activeColor == Color.WHITE ? whiteRemainingMs : blackRemainingMs;
        scheduler.schedule(() -> {
            if (getGame(gameId).getStatus() == GameStatus.IN_PROGRESS) {
                // Re-check: has a move been made since scheduling?
                long currentRemaining = calculateCurrentRemaining();
                if (currentRemaining <= 0) {
                    endGameByTimeout(gameId, activeColor);
                }
            }
        }, remainingMs + 100, TimeUnit.MILLISECONDS);  // +100ms buffer
    }
}
```

---

### Deep Dive 3: Matchmaking — Rating-Based Queue with Expanding Radius

**The Problem**: Match players of similar skill quickly. A 1500-rated player shouldn't wait 60 seconds for a perfect match when a 1550-rated player is also waiting. The search radius should expand over time.

```java
public class MatchmakingService {

    /**
     * Matchmaking algorithm:
     * 
     * Data structure: Redis Sorted Set per time control
     *   Key: matchmaking:{time_control}  (e.g., matchmaking:blitz)
     *   Score: player rating
     *   Member: player_id
     * 
     * On queue:
     *   1. ZADD matchmaking:blitz 1520 "user_123"
     *   2. Search for opponent: ZRANGEBYSCORE matchmaking:blitz 1420 1620 LIMIT 1
     *      (±100 initial range)
     *   3. If found: remove both, create game
     *   4. If not: wait, expand range by ±25 every 5 seconds
     * 
     * Expanding radius:
     *   t=0s:  ±50 rating
     *   t=5s:  ±75 rating
     *   t=10s: ±100 rating
     *   t=15s: ±150 rating
     *   t=30s: ±300 rating (accept anyone)
     * 
     * Fairness: when two players are matched, the one who waited longer
     *   gets preference for color choice (white/black alternation)
     */
    
    public MatchResult findMatch(MatchmakingRequest request) {
        String queueKey = "matchmaking:" + request.getTimeControlLabel();
        int rating = request.getPlayerRating();
        int range = request.getRatingRange();
        
        // Search for opponent within range
        Set<String> candidates = redis.zrangeByScore(
            queueKey, rating - range, rating + range);
        
        // Remove self from candidates
        candidates.remove(request.getPlayerId());
        
        if (!candidates.isEmpty()) {
            // Found a match! Pick closest rating
            String opponentId = findClosestRating(candidates, rating);
            
            // Atomically remove both from queue (Lua script for atomicity)
            boolean removed = redis.eval(ATOMIC_REMOVE_SCRIPT, 
                queueKey, request.getPlayerId(), opponentId);
            
            if (removed) {
                return createGame(request.getPlayerId(), opponentId, request.getTimeControlLabel());
            }
            // If removal failed (opponent was matched by another thread), retry
        }
        
        return MatchResult.NO_MATCH;
    }
    
    // Background: expand search radius for waiting players
    @Scheduled(fixedRate = 5000)
    public void expandSearchRadius() {
        for (String timeControl : TIME_CONTROLS) {
            String queueKey = "matchmaking:" + timeControl;
            Set<TypedTuple<String>> waiting = redis.zrangeWithScores(queueKey, 0, -1);
            
            for (var entry : waiting) {
                String playerId = entry.getValue();
                Instant queuedAt = getQueuedAt(playerId);
                long waitSeconds = Duration.between(queuedAt, Instant.now()).toSeconds();
                
                int expandedRange = 50 + (int)(waitSeconds / 5) * 25;
                expandedRange = Math.min(expandedRange, 500);  // Cap at ±500
                
                // Re-attempt match with expanded range
                MatchmakingRequest expanded = new MatchmakingRequest(
                    playerId, timeControl, (int)entry.getScore(), expandedRange);
                findMatch(expanded);
            }
        }
    }
}
```

---

### Deep Dive 4: Game Server Assignment & Fault Tolerance

**The Problem**: Each active game lives on exactly one Game Server (for strong consistency — single authority for move ordering). If that server crashes, the game must recover without data loss.

```java
public class GameServerManager {

    /**
     * Game → Server assignment:
     * 
     * Strategy: Consistent hashing by game_id
     *   - game_id → hash → assigned to Server S
     *   - All WebSocket connections for this game route to Server S
     *   - Server S holds game state in memory for low-latency move processing
     * 
     * Fault tolerance:
     *   1. Every move is persisted to durable store BEFORE acknowledgment
     *      → Write-ahead: append move to PostgreSQL/Kafka THEN broadcast
     *      → If server crashes: game state can be rebuilt from move log
     * 
     *   2. Heartbeat monitoring:
     *      → Control plane pings each Game Server every 2 seconds
     *      → If 3 consecutive missed heartbeats → declare server dead
     * 
     *   3. Game recovery:
     *      → Reassign dead server's games to healthy servers (consistent hashing)
     *      → New server loads game state from DB (move log replay)
     *      → Players' WebSocket connections fail → clients auto-reconnect
     *      → Reconnection routes to new server → game resumes
     * 
     *   4. State persistence:
     *      → Every move: append to "moves" table (durable)
     *      → Every 10 moves: snapshot full game state to "game_snapshots" (faster recovery)
     *      → On recovery: load latest snapshot + replay subsequent moves
     */
    
    // Game recovery from move log
    public Game recoverGame(String gameId) {
        // Load latest snapshot (if any)
        GameSnapshot snapshot = snapshotRepo.findLatest(gameId);
        
        Game game;
        int startFromMove;
        if (snapshot != null) {
            game = snapshot.toGame();
            startFromMove = snapshot.getMoveIndex() + 1;
        } else {
            game = gameRepo.findById(gameId);  // Load initial state
            startFromMove = 0;
        }
        
        // Replay moves since snapshot
        List<Move> moves = moveRepo.findByGameIdAfter(gameId, startFromMove);
        for (Move move : moves) {
            game.applyMove(move);
        }
        
        // Recalculate clock from move timestamps
        game.recalculateClocks();
        
        return game;
    }
}
```

---

### Deep Dive 5: Leaderboard — Redis Sorted Set for 50M Players

**The Problem**: 50M rated players, need to support: get top 50, get player's rank, get nearby players. All in < 50ms.

```java
public class LeaderboardService {

    /**
     * Redis Sorted Set per time control:
     * 
     * Key: leaderboard:blitz
     * Score: Elo rating (e.g., 1520)
     * Member: player_id
     * 
     * Operations:
     *   ZADD leaderboard:blitz 1520 "user_123"     — Update rating: O(log N)
     *   ZREVRANK leaderboard:blitz "user_123"       — Get rank: O(log N)
     *   ZREVRANGE leaderboard:blitz 0 49            — Top 50: O(K + log N)
     *   ZREVRANGEBYSCORE leaderboard:blitz 1530 1510 — Nearby players: O(K + log N)
     * 
     * Memory: 50M players × ~50 bytes = ~2.5 GB per time control
     * Total: 4 time controls × 2.5 GB = ~10 GB → fits in single Redis instance
     * 
     * Update frequency: ~10M games/day → 20M rating updates/day
     *   = ~230 ZADD/sec (very manageable for Redis)
     */
    
    public LeaderboardResponse getTopPlayers(String timeControl, int count) {
        String key = "leaderboard:" + timeControl;
        Set<TypedTuple<String>> top = redis.zrevrangeWithScores(key, 0, count - 1);
        
        int rank = 1;
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (var tuple : top) {
            entries.add(new LeaderboardEntry(
                tuple.getValue(), (int)tuple.getScore().doubleValue(), rank++));
        }
        return new LeaderboardResponse(entries);
    }
    
    public PlayerRank getPlayerRank(String timeControl, String playerId) {
        String key = "leaderboard:" + timeControl;
        Long rank = redis.zrevrank(key, playerId);  // 0-based
        Double score = redis.zscore(key, playerId);
        
        if (rank == null) return PlayerRank.UNRANKED;
        return new PlayerRank(rank + 1, score.intValue());  // 1-based
    }
    
    // Called by Rating Service after Elo update
    public void updateRating(String timeControl, String playerId, int newRating) {
        redis.zadd("leaderboard:" + timeControl, newRating, playerId);
    }
}
```

---

### Deep Dive 6: Move Persistence — Append-Only Log for Durability

**The Problem**: Every move must be durably stored so games can be recovered after crashes and replayed later. Moves are append-only (never edited). This is a natural fit for a log-structured storage pattern.

```java
public class MovePersistenceService {

    /**
     * Storage pattern: append-only move log
     * 
     * Table: moves
     *   game_id (UUID), move_index (INT), color (VARCHAR),
     *   uci (VARCHAR), san (VARCHAR), fen_after (VARCHAR),
     *   clock_remaining_ms (BIGINT), timestamp (TIMESTAMP)
     *   PRIMARY KEY (game_id, move_index)
     * 
     * Write pattern: INSERT only (never UPDATE or DELETE during game)
     * Read pattern: SELECT * FROM moves WHERE game_id = ? ORDER BY move_index
     * 
     * Durability strategy:
     *   Option A: Synchronous PostgreSQL write before broadcasting move
     *     → Guarantees durability but adds ~5-10ms per move
     *     → Acceptable for chess (< 100ms total budget)
     *   
     *   Option B: Kafka append + async PostgreSQL write
     *     → Kafka write is fast (~1ms) and durable (replication)
     *     → Consumer writes to PostgreSQL asynchronously
     *     → Faster hot path but slightly more complex recovery
     *   
     *   Chosen: Option A (synchronous PostgreSQL) for simplicity
     *   At 50K moves/sec peak: PostgreSQL with connection pooling handles this
     * 
     * Partitioning: shard moves table by game_id (same shard as games table)
     *   → All data for a game co-located → efficient single-shard queries
     */
    
    public void persistMove(String gameId, Move move) {
        // Synchronous insert — must complete before broadcasting to players
        jdbc.update(
            "INSERT INTO moves (game_id, move_index, color, uci, san, fen_after, " +
            "clock_remaining_ms, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            gameId, move.getMoveIndex(), move.getColor().name(),
            move.getUci(), move.getSan(), move.getFenAfter(),
            move.getClockRemainingMs(), move.getTimestamp());
    }
}
```

---

### Deep Dive 7: Elo Rating System — Fair & Convergent

**The Problem**: After each game, both players' ratings must be updated. The Elo system must be fair (new players converge quickly, experienced players change slowly) and handle edge cases (draws, rating manipulation).

```java
public class EloRatingService {

    /**
     * Elo formula:
     *   expected = 1 / (1 + 10^((opponent_rating - player_rating) / 400))
     *   new_rating = old_rating + K × (actual - expected)
     * 
     *   actual: 1.0 for win, 0.5 for draw, 0.0 for loss
     *   K-factor: determines rating volatility
     *     - New players (< 30 games): K = 40 (fast convergence)
     *     - Regular players: K = 20
     *     - High-rated (> 2400): K = 10 (stable ratings)
     * 
     * Edge cases:
     *   - Rating floors: minimum rating = 100 (can't go below)
     *   - Provisional ratings: first 30 games use larger K-factor
     *   - Disconnection: treated as loss (clock runs out)
     *   - Abort (< 2 moves): no rating change
     */
    
    public RatingUpdate calculateNewRatings(Game game) {
        int whiteRating = playerService.getRating(game.getWhitePlayerId(), game.getTimeControl());
        int blackRating = playerService.getRating(game.getBlackPlayerId(), game.getTimeControl());
        
        int whiteK = getKFactor(game.getWhitePlayerId(), game.getTimeControl());
        int blackK = getKFactor(game.getBlackPlayerId(), game.getTimeControl());
        
        double whiteExpected = 1.0 / (1 + Math.pow(10, (blackRating - whiteRating) / 400.0));
        double blackExpected = 1.0 - whiteExpected;
        
        double whiteActual = switch (game.getResult()) {
            case WHITE_WINS -> 1.0;
            case BLACK_WINS -> 0.0;
            case DRAW -> 0.5;
            default -> throw new IllegalStateException();
        };
        double blackActual = 1.0 - whiteActual;
        
        int newWhiteRating = Math.max(100, 
            (int) Math.round(whiteRating + whiteK * (whiteActual - whiteExpected)));
        int newBlackRating = Math.max(100, 
            (int) Math.round(blackRating + blackK * (blackActual - blackExpected)));
        
        return new RatingUpdate(
            game.getWhitePlayerId(), whiteRating, newWhiteRating, newWhiteRating - whiteRating,
            game.getBlackPlayerId(), blackRating, newBlackRating, newBlackRating - blackRating);
    }
    
    private int getKFactor(String playerId, String timeControl) {
        int gamesPlayed = playerService.getGamesPlayed(playerId, timeControl);
        int rating = playerService.getRating(playerId, timeControl);
        
        if (gamesPlayed < 30) return 40;       // Provisional
        if (rating > 2400) return 10;           // Master-level stability
        return 20;                               // Standard
    }
}
```

---

### Deep Dive 8: Spectator Mode & Live Broadcasting

**The Problem**: Popular games (tournaments, top players) may have thousands of spectators watching live. Each move must be broadcast to all spectators with minimal delay, without overloading the Game Server.

```java
public class SpectatorService {

    /**
     * Architecture for spectator mode:
     * 
     * 1. Game Server publishes each move to Kafka "spectator-feed" topic
     *    → Partitioned by game_id
     *    → Move event includes: game_id, move, FEN, clocks, timestamp
     * 
     * 2. Spectator Gateway Service:
     *    → Manages WebSocket connections for spectators
     *    → Subscribes to Kafka "spectator-feed" for games being watched
     *    → Broadcasts moves to all connected spectators of that game
     * 
     * 3. Scaling:
     *    → Popular game (10K spectators): single Kafka partition → one consumer → broadcast
     *    → Fan-out via WebSocket: 10K connections across multiple Spectator Gateway pods
     *    → Each pod handles ~5K connections
     *    → 10K spectators = 2-3 pods
     * 
     * 4. Separation from Game Server:
     *    → Game Server only handles 2 player connections (low load)
     *    → Spectator load is isolated to Spectator Gateway (separate scaling)
     *    → Game Server publishes to Kafka → decoupled from spectator count
     * 
     * 5. Latency: ~200-500ms from move to spectator display
     *    (Game Server → Kafka → Spectator Gateway → WebSocket → Client)
     *    Acceptable for spectating (not playing)
     */
    
    // Spectator Gateway: subscribe to game feed
    public void onSpectatorConnect(WebSocketSession session, String gameId) {
        spectatorSessions.computeIfAbsent(gameId, k -> new CopyOnWriteArrayList<>())
            .add(session);
        
        // Send current game state first
        Game game = gameService.getGame(gameId);
        session.send(new TextMessage(serialize(game)));
    }
    
    // Kafka consumer: broadcast moves to spectators
    @KafkaListener(topics = "spectator-feed")
    public void onMoveEvent(MoveEvent event) {
        List<WebSocketSession> sessions = spectatorSessions.get(event.getGameId());
        if (sessions != null) {
            String message = serialize(event);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.send(new TextMessage(message));
                }
            }
        }
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Game authority** | Server-authoritative (single Game Server per game) | Prevents cheating; single source of truth for moves and clocks; clients are views |
| **Clock management** | Server-side with RTT compensation | Fair play; client clocks can be manipulated; RTT compensation reduces network penalty |
| **Game state** | In-memory on Game Server + append-only move log in PostgreSQL | Low-latency moves (~1ms in-memory); durable move log for recovery and replay |
| **Server assignment** | Consistent hashing by game_id | Deterministic routing; easy reconnection; predictable failover |
| **Matchmaking** | Redis sorted sets with expanding radius | O(log N) rating-range queries; expand ±25 every 5 seconds; atomic matching via Lua scripts |
| **Leaderboard** | Redis sorted set per time control | O(log N) rank queries for 50M players; ~2.5 GB per time control; ZREVRANK for user's rank |
| **Rating system** | Elo with variable K-factor | Industry standard; K=40 for new players (fast convergence), K=10 for masters (stability) |
| **Spectators** | Kafka fan-out + separate Spectator Gateway | Decouples spectator load from Game Server; scales independently; ~200ms latency acceptable |
| **Move persistence** | Synchronous PostgreSQL write before broadcast | Guarantees durability; ~5ms overhead; recoverable from move log on server crash |
| **Reconnection** | Game state cached on server + full state sync on reconnect | < 5s resume; client gets full FEN + move history + clocks on reconnect |

## Interview Talking Points

1. **"Server-authoritative game state: clients suggest, server decides"** — Client sends only the move (e.g., "e2e4"). Server validates legality, applies to board, updates clocks, checks for checkmate, then broadcasts. Client's board is a read-only view. Prevents all categories of cheating.

2. **"Server-side chess clocks with RTT compensation"** — Server tracks elapsed time per move. Deducts from active player's clock. Adds increment after move. Compensates for network latency by subtracting half-RTT (capped at 500ms). Timeout detected via scheduled timer.

3. **"Consistent hashing for Game Server assignment"** — game_id → hash → Server S. All connections for a game route to the same server. On server crash: reassign to next server in ring, recover from move log. Players auto-reconnect.

4. **"Matchmaking: Redis sorted set with expanding radius"** — Players scored by rating in sorted set. ZRANGEBYSCORE finds opponents within ±range. Range expands ±25 every 5 seconds. Atomic dual-removal via Lua script prevents race conditions.

5. **"Append-only move log: synchronous PostgreSQL write before broadcast"** — Every move persisted before clients see it. On crash: replay move log to recover game state. Snapshots every 10 moves for faster recovery. ~5ms write overhead within 100ms budget.

6. **"Leaderboard: Redis ZSET with ZREVRANK for 50M players"** — One sorted set per time control. ZADD on rating change, ZREVRANK for rank lookup, ZREVRANGE for top-N. All O(log N). ~2.5 GB per time control.

7. **"Spectator mode: Kafka fan-out decoupled from Game Server"** — Game Server publishes moves to Kafka. Separate Spectator Gateway subscribes and broadcasts via WebSocket. Scales independently. Game Server always handles only 2 connections.

8. **"Elo with variable K-factor: K=40 for new players, K=20 standard, K=10 for masters"** — New players converge to true rating quickly. Masters' ratings are stable. Rating floor at 100. Abort before move 2 = no rating change.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Online Multiplayer Game** | Same real-time pattern | Chess is turn-based (simpler); FPS games are continuous (tick-rate, interpolation) |
| **Leaderboard System** | Same Redis sorted set pattern | Chess has multiple leaderboards (per time control); same ZSET approach |
| **Matchmaking (League of Legends)** | Same queue + rating matching | LoL matches 10 players with roles; chess matches exactly 2 |
| **Chat System** | Same WebSocket infrastructure | Chat is text broadcast; chess is structured game state |
| **Collaborative Editor** | Same conflict resolution | Editors use OT/CRDT; chess uses strict turn ordering (simpler) |
| **Notification System** | Same push pattern | Spectator broadcasts are similar to notification fan-out |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Game Server** | Go/Rust (stateful) | Erlang/Elixir (BEAM VM) | BEAM: built-in actor model, fault isolation per process, great for game servers |
| **Move persistence** | PostgreSQL (sync write) | Kafka (async) + PostgreSQL | Kafka: lower hot-path latency (~1ms vs ~5ms); slightly more complex recovery |
| **Matchmaking** | Redis Sorted Sets | Custom in-memory service | Custom: more complex algorithms (team matching, role preferences) |
| **Leaderboard** | Redis ZSET | DynamoDB / PostgreSQL materialized view | DynamoDB: serverless; PostgreSQL: if already have it, avoid Redis dependency |
| **WebSocket** | Native WebSocket | Socket.io / SignalR | Socket.io: auto-fallback to long-polling; SignalR: .NET ecosystem |
| **Game state recovery** | Move log replay + snapshots | Event sourcing (full CQRS) | Event sourcing: if game has complex undo/redo; overkill for chess |
| **Rating system** | Elo | Glicko-2 / TrueSkill | Glicko-2: accounts for rating uncertainty; TrueSkill: better for team games |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Chess.com Architecture (10M+ DAU Real-Time Platform)
- Server-Authoritative Game Architecture
- Elo Rating System (FIDE Implementation)
- Consistent Hashing for Stateful Game Servers
- Redis Sorted Sets for Leaderboards & Matchmaking
- WebSocket Connection Management at Scale
- Append-Only Log / Event Sourcing for Game State
- Network Latency Compensation in Online Games
