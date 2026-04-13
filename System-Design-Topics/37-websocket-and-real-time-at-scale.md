# 🎯 Topic 37: WebSocket & Real-Time at Scale

> **System Design Interview — Deep Dive**
> WebSocket gateway architecture, connection management at 10M+ users, presence (online/offline), cross-gateway messaging, and heartbeat mechanisms.

---

## Core Architecture

```
┌──────────┐     WebSocket     ┌──────────────┐
│  Client   │ ←──────────────→ │  Gateway     │ 65K connections/server
│ (Alice)   │                  │  Server 42   │
└──────────┘                  └──────┬───────┘
                                     │ Redis Pub/Sub
┌──────────┐     WebSocket     ┌─────┴────────┐
│  Client   │ ←──────────────→ │  Gateway     │
│ (Bob)     │                  │  Server 7    │
└──────────┘                  └──────────────┘

Alice sends message to Bob:
  Alice → WS → Gateway 42 → Message Service → Kafka 
  → Gateway 7 (lookup session:bob → gateway-7) → WS → Bob
  Latency: ~30ms end-to-end
```

## Key Design Points

### Connection Management
```
Per server: ~65K connections (file descriptor limit)
10M concurrent users: 10M / 65K = ~154 gateway servers
Non-blocking I/O (epoll): No thread per connection
Load balancing: Least-connections (not round-robin for long-lived connections)
```

### Presence (Online/Offline)
```
Every 30 seconds: Client sends heartbeat
Gateway: SET online:{user_id} 1 EX 70  (70-second TTL)

If client disconnects: Heartbeat stops → key expires after 70s → "offline"
Check presence: MGET online:{id1} online:{id2} ... (batch, < 2ms for 500 contacts)
Last seen: SET last_seen:{user_id} {timestamp} (on disconnect)
```

### Cross-Gateway Messaging
```
Alice on Gateway 42, Bob on Gateway 7.
How does Gateway 42 know Bob is on Gateway 7?

Redis mapping: session:bob:device1 → gateway-server-7
Gateway 42 → Redis Pub/Sub channel "gateway-7" → Gateway 7 → Bob's WebSocket
```

## Interview Script
> *"Each gateway server handles 65K concurrent WebSocket connections using epoll-based non-blocking I/O. Presence is handled via Redis keys with TTL — heartbeat every 30 seconds refreshes the key. Cross-gateway messaging uses Redis Pub/Sub. Total delivery latency: ~30ms."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│         WEBSOCKET & REAL-TIME AT SCALE CHEAT SHEET           │
├──────────────────────────────────────────────────────────────┤
│  Connections: 65K per server (epoll, non-blocking I/O)       │
│  10M users: ~154 gateway servers                             │
│  LB: Least-connections (not round-robin)                     │
│  Presence: Redis key + 70s TTL, heartbeat every 30s          │
│  Cross-gateway: Redis Pub/Sub or Kafka                       │
│  Delivery latency: ~30ms end-to-end                          │
│  Graceful degradation: WS → SSE → Long Polling               │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
