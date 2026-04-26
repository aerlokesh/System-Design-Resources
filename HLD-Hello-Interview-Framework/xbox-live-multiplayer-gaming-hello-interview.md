# Design a Multiplayer Gaming Platform (Xbox Live) — Hello Interview Framework

> **Question**: Design the backend for a large-scale multiplayer gaming service — handling player sessions, real-time state synchronization, matchmaking, leaderboards, and social features for millions of concurrent gamers.
>
> **Asked at**: Microsoft, Amazon (GameLift), Google, Riot Games
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
1. **Matchmaking**: Match players into game sessions based on skill rating, latency, party size, and game mode. Support ranked and casual modes.
2. **Game Session Management**: Create, manage, and destroy game server instances. Track active sessions. Support 2–100 players per session.
3. **Real-Time State Sync**: Synchronize game state across all players in a session with minimal latency. Authoritative server model.
4. **Leaderboards**: Global and friends-only leaderboards. Ranked by score, wins, or custom stats. Real-time updates. Support millions of entries.
5. **Player Profiles & Stats**: Track player stats (wins, K/D ratio, playtime). Achievements/trophies. Player level/XP.
6. **Social Features**: Friends list, party system (group up before matchmaking), presence (online/in-game/away), text & voice chat in party.
7. **Player Progression**: XP, levels, season pass progress, unlockable items. Persist across sessions.

#### Nice to Have (P1)
- Cross-platform play (Xbox, PC, Mobile)
- Anti-cheat system
- Replay system (record and playback matches)
- Tournament/competitive ladder system
- Player reporting and moderation
- In-game economy (virtual currency, marketplace)
- Cloud game saves
- Spectator mode

#### Below the Line (Out of Scope)
- Game rendering / client engine
- Payment processing / store
- Content distribution (game downloads)
- Streaming (xCloud)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Concurrent players** | 50 million | Peak gaming hours |
| **Active game sessions** | 5 million concurrent | ~10 players avg per session |
| **Matchmaking latency** | < 30 seconds for 95% of players | Don't make players wait |
| **Game state sync latency** | < 50ms (intra-region) | Real-time gameplay |
| **Tick rate** | 30–128 Hz (game dependent) | Smooth gameplay |
| **Leaderboard update** | < 5 seconds for score to appear | Near real-time |
| **Availability** | 99.99% | Gaming is primary entertainment |
| **Player data durability** | Zero loss | Progress, purchases |

### Capacity Estimation

```
Players:
  Registered: 100M accounts
  DAU: 30M
  Concurrent peak: 50M
  
Sessions:
  Concurrent: 5M game sessions
  Sessions started/sec: ~50K
  Average duration: 15 minutes
  
State Sync:
  Per session: 10 players × 128 ticks/sec × 200 bytes = 256 KB/s
  Total: 5M sessions × 256 KB/s = 1.28 TB/s (distributed across game servers)
  
Matchmaking:
  Requests/sec: 50K (peak)
  Avg match time: 15 seconds
  Concurrent in queue: 750K players
  
Leaderboard:
  Updates/sec: 500K (end-of-match score submissions)
  Reads/sec: 2M (players checking leaderboards)
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  Player          │     │  GameSession      │     │  MatchTicket       │
│                  │     │                   │     │                    │
│ playerId (XUID)  │     │ sessionId         │     │ ticketId           │
│ gamertag         │     │ gameTitle         │     │ playerId/partyId   │
│ skillRating (MMR)│     │ gameMode          │     │ gameMode           │
│ level / XP       │     │ map               │     │ skillRating        │
│ stats{}          │     │ serverRegion      │     │ partySize          │
│ achievements[]   │     │ serverAddress     │     │ preferredRegions[] │
│ friends[]        │     │ players[]         │     │ maxLatency         │
│ presence         │     │ state (waiting/   │     │ createdAt          │
│ platform (Xbox/  │     │  active/ended)    │     │ status (queued/    │
│  PC/Mobile)      │     │ tickRate          │     │  matched/expired)  │
│ seasonPass       │     │ startedAt         │     └───────────────────┘
└─────────────────┘     │ maxPlayers        │
                         └──────────────────┘
                         
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  Party           │     │  Leaderboard      │     │  LeaderboardEntry  │
│                  │     │                   │     │                    │
│ partyId          │     │ leaderboardId     │     │ playerId           │
│ leaderId         │     │ gameTitle         │     │ leaderboardId      │
│ members[]        │     │ name              │     │ score              │
│ gameMode         │     │ type (global/     │     │ rank               │
│ state            │     │  friends/season)  │     │ metadata{}         │
│ voiceChannelId   │     │ sortOrder         │     │ updatedAt          │
└─────────────────┘     │ resetSchedule     │     └───────────────────┘
                         └──────────────────┘
```

---

## 3️⃣ API Design

### Submit Matchmaking Request
```
POST /api/v1/matchmaking/tickets
Authorization: Bearer <token>

Request:
{
  "gameTitle": "halo-infinite",
  "gameMode": "ranked-4v4",
  "partyId": "party_abc",
  "players": [
    { "playerId": "xuid_123", "skillRating": 1450, "platform": "xbox" },
    { "playerId": "xuid_456", "skillRating": 1520, "platform": "pc" }
  ],
  "preferences": {
    "maxLatency": 80,
    "preferredRegions": ["us-east", "us-west"],
    "crossPlay": true
  }
}

Response: 202 Accepted
{
  "ticketId": "ticket_789",
  "status": "searching",
  "estimatedWaitSec": 15
}
```

### Get Leaderboard
```
GET /api/v1/leaderboards/halo-infinite/ranked-4v4/global?top=100&around=xuid_123

Response: 200 OK
{
  "topPlayers": [
    { "rank": 1, "gamertag": "ProGamer42", "score": 2847, "wins": 342 },
    { "rank": 2, "gamertag": "EliteSniper", "score": 2801, "wins": 330 }
  ],
  "aroundMe": [
    { "rank": 14521, "gamertag": "MyFriend", "score": 1449 },
    { "rank": 14522, "gamertag": "Me", "score": 1448, "isMe": true },
    { "rank": 14523, "gamertag": "RivalPlayer", "score": 1447 }
  ]
}
```

---

## 4️⃣ Data Flow

### Matchmaking Flow

```
Player/Party clicks "Find Match"
        │
        ▼
   POST /matchmaking/tickets → Matchmaking Service
        │
        ├── 1. Validate: party members online, game ownership verified
        ├── 2. Compute match criteria: average skill, party size, region
        ├── 3. Enqueue ticket to matchmaking pool (Redis sorted set)
        │       Score = skillRating, partitioned by gameMode + region
        │
        ▼
   Matchmaking Worker (runs continuously):
        │
        ├── 1. Pull candidates from pool (skill ± tolerance window)
        │       Start with tight window (±50 MMR), expand over time (±200)
        ├── 2. Check compatibility: party sizes fit, latency OK, cross-play OK
        ├── 3. Found enough players for a match (e.g., 8 for 4v4):
        │       ├── Balance teams: minimize skill difference between teams
        │       ├── Reserve game server in best region
        │       └── Create GameSession
        ├── 4. Notify all matched players via WebSocket:
        │       { "type": "matchFound", "sessionId": "...", "serverAddress": "..." }
        │
        └── 5. Players connect to game server → session begins

   Average match time: 15 seconds
   Timeout: 5 minutes → widen skill range or return "no match"
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                         GAME CLIENTS                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │  Xbox    │  │   PC     │  │  Mobile  │  │  Cloud   │           │
│  │ Console  │  │ (Steam/  │  │  (iOS/   │  │ (xCloud) │           │
│  │          │  │  MSStore) │  │  Android)│  │          │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
│       └──────────────┴──────────────┴──────────────┘                 │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ HTTPS + WebSocket + UDP (game traffic)
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    PLATFORM SERVICES                                  │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐     │
│  │ Matchmaking  │  │ Session      │  │ Player Service       │     │
│  │ Service      │  │ Manager      │  │                      │     │
│  │              │  │              │  │ Profiles, stats,     │     │
│  │ Queue mgmt   │  │ Create/      │  │ achievements, XP,    │     │
│  │ Skill-based  │  │ destroy      │  │ progression          │     │
│  │ matching     │  │ game server  │  │                      │     │
│  │ Team balance │  │ instances    │  │                      │     │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘     │
│         │                 │                      │                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐     │
│  │ Leaderboard  │  │ Social       │  │ Presence Service     │     │
│  │ Service      │  │ Service      │  │                      │     │
│  │              │  │              │  │ Online/InGame/Away   │     │
│  │ Global, per- │  │ Friends,     │  │ Current game/session │     │
│  │ season, per- │  │ parties,     │  │ Joinable status      │     │
│  │ friends      │  │ chat         │  │                      │     │
│  └──────────────┘  └──────────────┘  └──────────────────────┘     │
└──────┬──────────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    GAME SERVER FLEET                                  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Server Orchestrator (like Azure PlayFab / GameLift)          │  │
│  │                                                              │  │
│  │  - Provision game servers on demand                          │  │
│  │  - Auto-scale based on active sessions                       │  │
│  │  - Place servers in optimal regions                          │  │
│  │  - Health monitoring and replacement                         │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐      │
│  │ Game Svr  │  │ Game Svr  │  │ Game Svr  │  │ Game Svr  │      │
│  │ US-East   │  │ EU-West   │  │ Asia-SE   │  │ US-West   │      │
│  │           │  │           │  │           │  │           │      │
│  │ 10 active │  │ 10 active │  │ 10 active │  │ 10 active │      │
│  │ sessions  │  │ sessions  │  │ sessions  │  │ sessions  │      │
│  │ each      │  │ each      │  │ each      │  │ each      │      │
│  │           │  │           │  │           │  │           │      │
│  │ UDP       │  │ UDP       │  │ UDP       │  │ UDP       │      │
│  │ 128 tick  │  │ 128 tick  │  │ 128 tick  │  │ 128 tick  │      │
│  └───────────┘  └───────────┘  └───────────┘  └───────────┘      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐     │
│  │ Cosmos DB    │  │ Redis Cluster│  │ Azure Table /        │     │
│  │              │  │              │  │ Blob Storage          │     │
│  │ Player       │  │ Matchmaking  │  │                      │     │
│  │ profiles     │  │ queues       │  │ Game replays         │     │
│  │ Game history │  │ Leaderboard  │  │ Telemetry/analytics  │     │
│  │ Achievements │  │ Presence     │  │ Anti-cheat logs      │     │
│  │ Progression  │  │ Session map  │  │                      │     │
│  └──────────────┘  └──────────────┘  └──────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Matchmaking Algorithm

**Problem**: 750K players in queue simultaneously. Need to create balanced, fair matches quickly (<30s) while respecting skill, latency, party size, and cross-play preferences.

**Solution: Pool-Based Matchmaking with Expanding Skill Window**

```
Matchmaking Pool Structure (Redis):

Per game mode + region:
  ZSET matchpool:ranked-4v4:us-east
    Score = MMR (e.g., 1450)
    Member = ticketId + metadata (JSON)

Algorithm (runs every 500ms per pool):

  1. SCAN pool from highest to lowest skill:
     For each ticket T:
       a. Find candidates within skill window:
          ZRANGEBYSCORE pool T.mmr-window T.mmr+window
       b. Initial window: ±50 MMR
          After 10s waiting: ±100 MMR
          After 20s: ±200 MMR
          After 30s: ±400 MMR (desperation mode)
       c. Filter: compatible party sizes, cross-play OK, latency OK
       d. Enough players found (e.g., 8 for 4v4)?
          YES → form match → proceed to team balancing
          NO → leave in pool, try next ticket
  
  2. Team Balancing (once 8 players matched):
     Goal: minimize |Team1_avg_MMR - Team2_avg_MMR|
     
     Algorithm: sort 8 players by MMR, use snake draft:
       Team A picks: 1st, 4th, 5th, 8th  (avg: ~1500)
       Team B picks: 2nd, 3rd, 6th, 7th  (avg: ~1500)
     
     Constraint: keep parties together on same team
     
  3. Server Selection:
     Pick region with lowest avg latency for all 8 players
     Reserve a game server instance in that region
     
  4. Notify players → connect to game server

Backfill (for casual modes):
  Player leaves mid-game → session reports vacancy
  Matchmaker places a solo player from queue into existing session
  Requirements: similar skill, acceptable latency
```

### Deep Dive 2: Real-Time Game State Synchronization

**Problem**: In a fast-paced FPS, 10 players need to see consistent game state (positions, health, bullets) updated 128 times per second with <50ms latency. How?

**Solution: Authoritative Server with Client-Side Prediction**

```
Architecture: Client-Server with UDP

Game Server (authoritative):
  - Runs game simulation at 128 Hz (128 ticks/second)
  - Processes player inputs (movement, shoot, ability)
  - Computes authoritative game state
  - Sends state updates to all clients

Client:
  - Captures player input → send to server immediately (UDP)
  - Applies input locally (client-side prediction) → instant feedback
  - Receives server state → reconcile with prediction (smooth correction)

Network Protocol (UDP):
  Why UDP not TCP?
  - TCP: reliable, ordered → retransmits lost packets → adds latency
  - UDP: unreliable, unordered → but games can tolerate packet loss
  - Missing a position update? Next update replaces it anyway.

Tick Processing:
  Server tick (every 7.8ms at 128Hz):
    1. Receive all player inputs since last tick
    2. Simulate: apply inputs, physics, collision, damage
    3. Generate state snapshot:
       {
         tick: 12345,
         players: [
           { id: "p1", x: 142.5, y: 0.0, z: 88.3, health: 75, weapon: "rifle" },
           { id: "p2", x: 200.1, y: 0.0, z: 50.0, health: 100, weapon: "pistol" }
         ],
         projectiles: [...],
         events: ["p1_fired_at_tick_12345"]
       }
    4. Delta compression: only send changes since last ACKed state
    5. Send to each client (UDP)

Client-Side Prediction:
  Player presses "move forward":
    1. Send input to server: { tick: 12345, input: "forward" }
    2. Immediately apply locally → player moves (no waiting for server)
    3. Server receives, simulates, sends authoritative position
    4. Client receives server position for tick 12345:
       If matches prediction → great, no correction needed
       If differs → smoothly interpolate to server position (1-2 frames)
    
  Result: player feels instant response despite 30-50ms round trip

Lag Compensation (for shooting):
  Player sees enemy at position X (which is 50ms in the past due to latency)
  Player shoots → server receives: "player shot at tick 12345"
  Server REWINDS to tick 12345 state → checks: was enemy at position X? YES → hit!
  → "Favor the shooter" philosophy

Bandwidth Optimization:
  Full state: 10 players × 200 bytes = 2 KB per tick
  Delta compression: ~200 bytes per tick (only changes)
  128 ticks × 200 bytes = 25.6 KB/s per player (very manageable)
```

### Deep Dive 3: Leaderboard at Scale

**Problem**: 100M players, 500K score updates/sec, 2M leaderboard reads/sec. How to maintain ranked leaderboard with real-time updates and support "rank around me" queries?

**Solution: Redis Sorted Sets with Tiered Architecture**

```
Leaderboard Storage (Redis Sorted Set):

Key: leaderboard:{gameTitle}:{mode}:{season}
Type: ZSET
  Score = player's rating/score
  Member = playerId
  
Operations:
  Update score: ZADD leaderboard:halo:ranked:s5 1450 xuid_123
    → O(log N) — fast even with 100M entries
  
  Top 100: ZREVRANGE leaderboard:halo:ranked:s5 0 99 WITHSCORES
    → O(log N + K) where K=100
  
  My rank: ZREVRANK leaderboard:halo:ranked:s5 xuid_123
    → O(log N)
  
  Around me (±5): 
    rank = ZREVRANK leaderboard:halo:ranked:s5 xuid_123
    ZREVRANGE leaderboard:halo:ranked:s5 (rank-5) (rank+5) WITHSCORES

Friends Leaderboard:
  Option A: On-demand computation
    friends = GET friends:xuid_123 → [xuid_456, xuid_789, ...]
    For each friend: ZSCORE leaderboard:halo:ranked:s5 {friendId}
    Sort locally → return top friends
    Works for <500 friends (fast enough)
  
  Option B: Materialized friend leaderboard
    Maintain per-user friend ZSET: friendboard:{userId}:{game}
    Update on score change → fan-out to friends' boards
    Faster reads, more storage + write amplification

Scaling:
  100M players in single Redis ZSET → ~10 GB memory
  Redis can handle this with a single shard
  
  For multiple leaderboards (per game, per mode, per season):
    Shard by leaderboard key across Redis cluster
    Hot leaderboards (Halo, CoD) on dedicated nodes with replicas

Leaderboard Reset (seasonal):
  End of season:
    1. Snapshot current leaderboard → archive to Cosmos DB
    2. Calculate season rewards based on final rank
    3. RENAME leaderboard:halo:ranked:s5 → archive:halo:ranked:s5
    4. Create new empty leaderboard for season 6
    5. Optionally: seed initial ratings from previous season (soft reset)
```
