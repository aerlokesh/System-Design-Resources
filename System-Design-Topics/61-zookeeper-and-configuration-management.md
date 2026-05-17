# 🎯 Topic 61: ZooKeeper & Configuration Management

> **System Design Interview — Deep Dive**
> A comprehensive guide covering distributed coordination with ZooKeeper, configuration management patterns, leader election, distributed locks, service discovery, group membership, feature flags, dynamic config propagation, ZooKeeper internals (ZAB protocol, znodes, watches, sessions), etcd and Consul as alternatives, and production-grade interview scripts for any system that needs coordination or configuration at scale.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Distributed Coordination Is Hard](#why-distributed-coordination-is-hard)
3. [ZooKeeper Architecture](#zookeeper-architecture)
4. [ZooKeeper Data Model — Znodes](#zookeeper-data-model--znodes)
5. [Watches — Push-Based Notifications](#watches--push-based-notifications)
6. [Sessions and Ephemeral Nodes](#sessions-and-ephemeral-nodes)
7. [Leader Election with ZooKeeper](#leader-election-with-zookeeper)
8. [Distributed Locks with ZooKeeper](#distributed-locks-with-zookeeper)
9. [Service Discovery](#service-discovery)
10. [Group Membership](#group-membership)
11. [Configuration Management — The Core Pattern](#configuration-management--the-core-pattern)
12. [Feature Flags at Scale](#feature-flags-at-scale)
13. [Dynamic Config Propagation](#dynamic-config-propagation)
14. [ZAB Protocol — How ZooKeeper Achieves Consensus](#zab-protocol--how-zookeeper-achieves-consensus)
15. [ZooKeeper vs etcd vs Consul](#zookeeper-vs-etcd-vs-consul)
16. [Anti-Patterns and Pitfalls](#anti-patterns-and-pitfalls)
17. [Configuration Management Without ZooKeeper](#configuration-management-without-zookeeper)
18. [Observability and Monitoring](#observability-and-monitoring)
19. [Real-World Production Patterns](#real-world-production-patterns)
20. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
21. [Common Interview Mistakes](#common-interview-mistakes)
22. [ZooKeeper by System Design Problem](#zookeeper-by-system-design-problem)
23. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**ZooKeeper** is a distributed coordination service that provides primitives for building higher-level distributed patterns: leader election, distributed locks, configuration management, service discovery, and group membership. It's a small, replicated, hierarchical key-value store with strong consistency and push-based change notifications (watches).

**Configuration management** is the broader discipline of distributing, versioning, and dynamically updating application settings across a fleet of servers — from database connection strings to feature flags to rate limit thresholds — without redeploying code.

```
Why coordination and configuration matter:

  Without coordination:
    50 servers, each thinks it's the leader → split-brain → data corruption.
    
  Without centralized configuration:
    50 servers, each has a different config file → inconsistent behavior.
    Config change requires redeploying all 50 servers → slow, risky.

  With ZooKeeper + Config Management:
    One leader elected via ZooKeeper → consistent behavior.
    Config change → push to ZooKeeper → all 50 servers update within seconds.
    No redeployment. No inconsistency. No downtime.
```

### What ZooKeeper Is (and Isn't)

```
ZooKeeper IS:
  ✅ A coordination service (small metadata, strong consistency)
  ✅ A configuration store (small key-value pairs, push notifications)
  ✅ A lock service (distributed mutexes, leader election)
  ✅ A membership registry (which servers are alive)

ZooKeeper is NOT:
  ❌ A general-purpose database (not for large datasets)
  ❌ A message queue (not for high-throughput event streaming)
  ❌ A cache (not optimized for high read throughput of large values)
  ❌ A file system (despite the hierarchical namespace)

Rule of thumb: ZooKeeper stores METADATA about your system, not the DATA itself.
  ✅ "Which server is the Kafka leader for partition 5?" → ZooKeeper
  ❌ "Store 1 million user records" → Database
```

---

## Why Distributed Coordination Is Hard

### The Fundamental Problems

```
Problem 1: Leader Election
  50 servers need to agree on ONE leader.
  If network partitions, each partition might elect its own leader → split-brain.
  Requires consensus algorithm (Paxos, Raft, ZAB).

Problem 2: Distributed Locks
  Two servers want to update the same resource.
  Network delay means both might think they hold the lock.
  Requires fencing tokens or lease-based locks.

Problem 3: Configuration Consistency
  Config change on server A. How does server B know?
  Polling: Check every N seconds (delayed, wasteful).
  Push: ZooKeeper watches notify immediately.

Problem 4: Membership
  Server C crashes. How do others know?
  Heartbeat + timeout → eventually detect.
  ZooKeeper ephemeral nodes → automatic removal on session expiry.

All of these problems boil down to: CONSENSUS + NOTIFICATION.
ZooKeeper provides both via the ZAB protocol + watches.
```

---

## ZooKeeper Architecture

### Ensemble (Cluster)

```
ZooKeeper runs as an ENSEMBLE of 3, 5, or 7 servers (odd number).

  ┌────────────┐
  │ ZK Server 1 │ ← LEADER (handles all writes)
  │ (Leader)    │
  └──────┬─────┘
         │ Replication (ZAB)
  ┌──────┼──────────────────────┐
  │      │                      │
  ▼      ▼                      ▼
┌────────────┐  ┌────────────┐  ┌────────────┐
│ ZK Server 2 │  │ ZK Server 3 │  │ ZK Server 4 │
│ (Follower)  │  │ (Follower)  │  │ (Follower)  │
└────────────┘  └────────────┘  └────────────┘

Write path:
  Client → Any server → Forward to Leader → Leader proposes → 
  Majority ACK (quorum) → Committed → Response to client.

Read path:
  Client → Any server → Read local data → Response.
  (Reads are local — fast but may be slightly stale.)
  For strongly consistent reads: Use sync() before read.

Quorum: Majority of servers must agree.
  3 servers: quorum = 2 (survives 1 failure)
  5 servers: quorum = 3 (survives 2 failures)
  7 servers: quorum = 4 (survives 3 failures)

Why odd numbers? To maximize fault tolerance per server cost.
  3 servers and 4 servers both survive 1 failure → 3 is cheaper.
  5 servers and 6 servers both survive 2 failures → 5 is cheaper.
```

### Performance Characteristics

```
Write throughput: ~10K-20K writes/sec (limited by quorum consensus)
Read throughput:  ~100K-200K reads/sec (reads are local, parallelized)
Write latency:    2-10ms (quorum round-trip)
Read latency:     0.1-1ms (local read)

ZooKeeper is OPTIMIZED FOR READS.
  Reads: Served locally by any server. Sub-millisecond.
  Writes: Must go through leader + quorum. Milliseconds.

  Read/Write ratio in typical usage: 100:1 or higher.
  This makes ZooKeeper perfect for configuration (read-heavy)
  and coordination (infrequent writes).
```

---

## ZooKeeper Data Model — Znodes

### Hierarchical Namespace

```
ZooKeeper's data model is a TREE of znodes (like a filesystem):

  /
  ├── /config
  │   ├── /config/database
  │   │   ├── /config/database/host        → "db-primary.example.com"
  │   │   ├── /config/database/port        → "5432"
  │   │   └── /config/database/pool_size   → "20"
  │   ├── /config/cache
  │   │   ├── /config/cache/host           → "redis.example.com"
  │   │   └── /config/cache/ttl            → "300"
  │   └── /config/feature_flags
  │       ├── /config/feature_flags/dark_mode     → "true"
  │       └── /config/feature_flags/new_checkout  → "false"
  ├── /leaders
  │   ├── /leaders/scheduler     → "server-3" (ephemeral)
  │   └── /leaders/kafka-p0      → "broker-2" (ephemeral)
  ├── /locks
  │   └── /locks/inventory-update
  │       ├── /locks/inventory-update/lock-000000001 (ephemeral sequential)
  │       └── /locks/inventory-update/lock-000000002 (ephemeral sequential)
  └── /services
      ├── /services/user-service
      │   ├── /services/user-service/instance-1 → "10.0.1.5:8080" (ephemeral)
      │   ├── /services/user-service/instance-2 → "10.0.1.6:8080" (ephemeral)
      │   └── /services/user-service/instance-3 → "10.0.1.7:8080" (ephemeral)
      └── /services/order-service
          ├── /services/order-service/instance-1 → "10.0.2.5:8080" (ephemeral)
          └── /services/order-service/instance-2 → "10.0.2.6:8080" (ephemeral)
```

### Znode Types

```
Type 1: Persistent znode
  Created manually. Exists until explicitly deleted.
  Use: Configuration values, namespace structure.
  
  create /config/database/host "db-primary.example.com"
  # Survives server restarts, client disconnects.

Type 2: Ephemeral znode
  Tied to a client SESSION. Deleted when session expires.
  Use: Service discovery, leader election, group membership.
  
  create -e /services/user-service/instance-1 "10.0.1.5:8080"
  # If the server dies → session expires → znode deleted → others notified.

Type 3: Sequential znode
  ZooKeeper appends a monotonically increasing counter.
  Use: Distributed locks, queue ordering.
  
  create -s /locks/my-lock/lock- ""
  # Creates: /locks/my-lock/lock-0000000001
  # Next:    /locks/my-lock/lock-0000000002

Type 4: Ephemeral + Sequential
  Combines both: auto-deleted on session loss + auto-numbered.
  Use: Fair distributed locks (ordered by creation time).
```

### Znode Data Limits

```
Each znode can store up to 1 MB of data.

  ✅ Good for: Configuration values (a few bytes to a few KB)
    /config/database/host → "db-primary.example.com" (30 bytes)
    /config/feature_flags → '{"dark_mode": true, "new_checkout": false}' (60 bytes)
    /config/rate_limits → '{"default": 100, "premium": 10000}' (50 bytes)

  ❌ Bad for: Large data
    /data/user_profiles → 500 MB of user data → WAY over limit
    
  Rule: If your data is > 10 KB per znode, you're probably misusing ZooKeeper.
```

---

## Watches — Push-Based Notifications

### How Watches Work

```
A WATCH is a one-time trigger that fires when a znode changes.

  Client A: getData("/config/database/host", watch=true)
  → Returns: "db-primary.example.com"
  → Watch registered on /config/database/host

  Admin: setData("/config/database/host", "db-secondary.example.com")
  → ZooKeeper sends WatchEvent to Client A:
     type=NodeDataChanged, path=/config/database/host

  Client A receives the event → re-reads the znode → gets new value.
  Client A must RE-REGISTER the watch for future changes (one-time trigger).

  Flow:
    Client A            ZooKeeper           Admin
    ────────            ─────────           ─────
    getData(watch=true) ──→
    ←── "db-primary"
    (watch registered)
                                            setData("db-secondary")
    ←── WatchEvent ─────
    getData(watch=true) ──→
    ←── "db-secondary"
    (new watch registered)
```

### Watch Types

```
Data watches: Triggered by setData() or delete() on the znode.
  Registered via: getData(), exists()

Child watches: Triggered by create() or delete() of child znodes.
  Registered via: getChildren()

Examples:
  Watch /config/database/host (data watch):
    → Fires when the config VALUE changes.
    
  Watch /services/user-service (child watch):
    → Fires when a new instance registers or an existing one dies.
    → Use for service discovery: "Notify me when instances change."
```

### Watch Guarantees

```
1. Ordered: Client sees watch event BEFORE seeing new data via reads.
   (No chance of reading stale data after receiving a watch notification.)

2. One-time: Watch fires once, then must be re-registered.
   (Prevents event flood. Client controls when to re-watch.)

3. Session-scoped: If client session dies, watches are removed.
   (No dangling watches from dead clients.)
```

---

## Sessions and Ephemeral Nodes

### Session Lifecycle

```
Client connects to ZooKeeper → Session established (with session ID + timeout).

  Session timeout: Typically 10-30 seconds.
  Heartbeat: Client sends heartbeat every timeout/3 seconds.
  
  If ZooKeeper doesn't receive a heartbeat within the timeout:
    Session expires → ALL ephemeral znodes created by this session are DELETED.
    All watches set by this session are REMOVED.
    
  This is the mechanism behind:
    - Service discovery (server dies → ephemeral node deleted → others notified)
    - Leader election (leader dies → ephemeral node deleted → new election)
    - Distributed locks (holder dies → lock node deleted → next in line acquires)
```

### Session Events

```
CONNECTED:      Session established. Client can perform operations.
DISCONNECTED:   Client temporarily lost connection. Operations fail.
                Session is NOT expired yet — client can reconnect.
RECONNECTED:    Client reconnected. Session resumed. Ephemeral nodes still exist.
EXPIRED:        Session timed out. All ephemeral nodes deleted. Client must create new session.

Key insight:
  DISCONNECTED ≠ EXPIRED.
  A brief network glitch causes DISCONNECTED.
  The client reconnects within the timeout → RECONNECTED → everything fine.
  Only if the timeout passes without reconnection → EXPIRED → ephemeral nodes gone.
```

---

## Leader Election with ZooKeeper

### The Algorithm

```
Goal: Exactly ONE server is the leader at any time.

Step 1: Each candidate creates an ephemeral sequential znode:
  Server A: create -e -s /election/candidate- → /election/candidate-0000000001
  Server B: create -e -s /election/candidate- → /election/candidate-0000000002
  Server C: create -e -s /election/candidate- → /election/candidate-0000000003

Step 2: Each server checks: "Am I the lowest-numbered node?"
  Server A: getChildren(/election) → [candidate-01, candidate-02, candidate-03]
  Server A: I am candidate-01 (lowest) → I AM THE LEADER.
  Server B: I am candidate-02 (not lowest) → I am a follower. Watch candidate-01.
  Server C: I am candidate-03 (not lowest) → I am a follower. Watch candidate-02.

Step 3: Leader dies → ephemeral node deleted.
  Server A crashes → /election/candidate-0000000001 deleted.
  Server B was watching candidate-01 → WatchEvent fires.
  Server B: getChildren(/election) → [candidate-02, candidate-03]
  Server B: I am candidate-02 (now lowest) → I AM THE NEW LEADER.

Step 4: Server C is still watching candidate-02 (no change for C yet).

This is called the "HERD EFFECT AVOIDANCE" pattern:
  Each candidate only watches the node IMMEDIATELY BEFORE it.
  Not all candidates watch the leader (that would cause a thundering herd on leader death).
```

### Implementation

```python
class ZooKeeperLeaderElection:
    """Leader election using ZooKeeper ephemeral sequential nodes."""
    
    def __init__(self, zk_client, election_path="/election"):
        self.zk = zk_client
        self.election_path = election_path
        self.my_node = None
        self.is_leader = False
    
    def participate(self, server_id):
        """Join the election."""
        # Ensure election path exists
        self.zk.ensure_path(self.election_path)
        
        # Create ephemeral sequential node
        self.my_node = self.zk.create(
            f"{self.election_path}/candidate-",
            value=server_id.encode(),
            ephemeral=True,
            sequence=True
        )
        
        self._check_leadership()
    
    def _check_leadership(self):
        """Check if I'm the leader."""
        children = sorted(self.zk.get_children(self.election_path))
        my_name = self.my_node.split("/")[-1]
        
        if children[0] == my_name:
            # I am the lowest-numbered node → I am the leader
            self.is_leader = True
            self._on_become_leader()
        else:
            # Watch the node immediately before me
            my_index = children.index(my_name)
            predecessor = children[my_index - 1]
            
            # Set watch on predecessor
            self.zk.exists(
                f"{self.election_path}/{predecessor}",
                watch=self._predecessor_watcher
            )
    
    def _predecessor_watcher(self, event):
        """Called when predecessor node changes (probably deleted)."""
        self._check_leadership()
    
    def _on_become_leader(self):
        """Override this with your leader logic."""
        print(f"I am now the leader! Node: {self.my_node}")
```

---

## Distributed Locks with ZooKeeper

### The Recipe

```
Distributed lock using ephemeral sequential znodes:

Step 1: Create ephemeral sequential node under the lock path:
  Client A: create -e -s /locks/my-resource/lock- → lock-0000000001
  Client B: create -e -s /locks/my-resource/lock- → lock-0000000002

Step 2: Check if my node is the lowest:
  Client A: getChildren(/locks/my-resource) → [lock-01, lock-02]
  Client A: I am lock-01 (lowest) → I HOLD THE LOCK. Proceed.
  Client B: I am lock-02 (not lowest) → WAIT. Watch lock-01.

Step 3: When done, delete the node (release the lock):
  Client A: delete /locks/my-resource/lock-0000000001
  Client B's watch fires → B is now the lowest → B HOLDS THE LOCK.

Step 4: If lock holder crashes:
  Client A's session expires → ephemeral node auto-deleted.
  Client B's watch fires → B acquires the lock.
  No manual cleanup needed!

Fairness: Locks are granted in order of arrival (sequential numbers).
Safety: Ephemeral nodes prevent dead-lock from crashed clients.
```

### Lock vs Leader Election

```
Leader Election:
  One server is the "leader" indefinitely (until it dies).
  Multiple candidates. Leader does special work. Followers are passive.
  Ephemeral sequential nodes under /election/.

Distributed Lock:
  One client holds the lock for a short duration (do work, release).
  Multiple clients waiting. Lock holder does work, then releases.
  Ephemeral sequential nodes under /locks/{resource}/.

Same mechanism. Different semantics.
  Leader = long-lived lock.
  Lock = short-lived leader.
```

---

## Service Discovery

### Pattern: Ephemeral Nodes for Registration

```
Each service instance registers an ephemeral znode on startup:

Startup:
  create -e /services/user-service/10.0.1.5:8080 '{"weight": 1, "zone": "us-east-1a"}'
  # Ephemeral → auto-deleted when service dies.

Discovery:
  Other services: getChildren(/services/user-service) → list of instances.
  Watch: getChildren(/services/user-service, watch=true)
  → Notified when instances join or leave.

  Load balancer reads the list and distributes traffic.

Deregistration (graceful):
  Service shutting down: delete /services/user-service/10.0.1.5:8080

Deregistration (crash):
  Service crashes → session expires → ephemeral node auto-deleted.
  Watchers notified → remove dead instance from load balancer.
```

### Service Registry Structure

```
/services
├── /services/user-service
│   ├── instance-10.0.1.5:8080  → {"weight":1, "zone":"us-east-1a", "version":"2.3.1"}
│   ├── instance-10.0.1.6:8080  → {"weight":1, "zone":"us-east-1b", "version":"2.3.1"}
│   └── instance-10.0.1.7:8080  → {"weight":2, "zone":"us-east-1c", "version":"2.3.0"}
├── /services/order-service
│   ├── instance-10.0.2.5:8080  → {"weight":1, "zone":"us-east-1a"}
│   └── instance-10.0.2.6:8080  → {"weight":1, "zone":"us-east-1b"}
└── /services/payment-service
    └── instance-10.0.3.5:8080  → {"weight":1, "zone":"us-east-1a"}
```

---

## Group Membership

### Pattern: Know Who's Alive

```
Each member of a group creates an ephemeral znode:

  /groups/worker-pool
  ├── worker-1  (ephemeral) → {"host": "10.0.1.1", "status": "active"}
  ├── worker-2  (ephemeral) → {"host": "10.0.1.2", "status": "active"}
  ├── worker-3  (ephemeral) → {"host": "10.0.1.3", "status": "active"}

  Manager watches /groups/worker-pool (child watch):
    getChildren(/groups/worker-pool, watch=true) → [worker-1, worker-2, worker-3]

  Worker-2 crashes → session expires → worker-2 node deleted.
  Manager's watch fires → getChildren → [worker-1, worker-3].
  Manager: "Worker-2 left the group. Reassign its tasks."

Use cases:
  - Kafka broker membership (which brokers are alive)
  - Worker pool management (which workers are available)
  - Cluster membership (which DB replicas are healthy)
```

---

## Configuration Management — The Core Pattern

### Centralized Configuration Store

```
Store application configuration in ZooKeeper (or etcd/Consul):

  /config/app-name
  ├── /config/app-name/database
  │   ├── host       → "db-primary.example.com"
  │   ├── port       → "5432"
  │   ├── pool_size  → "20"
  │   └── timeout_ms → "5000"
  ├── /config/app-name/cache
  │   ├── host       → "redis.example.com"
  │   ├── ttl_sec    → "300"
  │   └── max_memory → "4gb"
  ├── /config/app-name/rate_limits
  │   ├── default    → "100"
  │   ├── premium    → "10000"
  │   └── admin      → "unlimited"
  └── /config/app-name/feature_flags
      ├── dark_mode      → "true"
      ├── new_checkout   → '{"enabled": true, "rollout_pct": 25}'
      └── ai_search      → '{"enabled": false}'
```

### Config Push Flow

```
1. Admin updates config via CLI or admin UI:
   set /config/app-name/rate_limits/default "200"

2. ZooKeeper propagates to the leader, which commits after quorum ACK.

3. All app servers watching this znode receive a WatchEvent:
   WatchEvent(type=NodeDataChanged, path=/config/app-name/rate_limits/default)

4. App servers re-read the value:
   getData(/config/app-name/rate_limits/default) → "200"
   Re-register watch for future changes.

5. App servers update their in-memory config:
   config.rate_limits.default = 200
   
6. Next request uses the new config. No restart. No redeployment.

  Timeline:
    T=0s:    Admin updates config in ZooKeeper.
    T=0.01s: ZooKeeper commits (quorum ACK).
    T=0.05s: Watch events delivered to all clients.
    T=0.1s:  All 50 servers have the new config.
    
    Total propagation: ~100 milliseconds.
```

### Configuration Client Implementation

```python
class ConfigClient:
    """Watches ZooKeeper for configuration changes."""
    
    def __init__(self, zk_client, config_path="/config/my-app"):
        self.zk = zk_client
        self.config_path = config_path
        self.config = {}
        self._load_all()
    
    def _load_all(self):
        """Load all config values and set watches."""
        children = self.zk.get_children(self.config_path, watch=self._children_watcher)
        
        for child in children:
            path = f"{self.config_path}/{child}"
            data, stat = self.zk.get(path, watch=self._data_watcher)
            self.config[child] = self._parse(data)
    
    def _data_watcher(self, event):
        """Called when a config value changes."""
        key = event.path.split("/")[-1]
        data, stat = self.zk.get(event.path, watch=self._data_watcher)  # Re-register watch
        old_value = self.config.get(key)
        new_value = self._parse(data)
        self.config[key] = new_value
        
        # Notify application of config change
        self._on_config_change(key, old_value, new_value)
    
    def _children_watcher(self, event):
        """Called when config keys are added or removed."""
        self._load_all()  # Reload everything
    
    def get(self, key, default=None):
        return self.config.get(key, default)
    
    def _parse(self, data):
        """Parse bytes to string or JSON."""
        text = data.decode('utf-8')
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return text
    
    def _on_config_change(self, key, old_value, new_value):
        """Override for custom change handling."""
        print(f"Config changed: {key} = {old_value} → {new_value}")
```

---

## Feature Flags at Scale

### Feature Flag Structure

```
/config/feature_flags
├── dark_mode        → '{"enabled": true, "rollout_pct": 100}'
├── new_checkout     → '{"enabled": true, "rollout_pct": 25, "allowlist": ["user:42"]}'
├── ai_search        → '{"enabled": false}'
└── redesigned_feed  → '{"enabled": true, "rollout_pct": 10, "region": "us-east"}'
```

### Feature Flag Evaluation

```python
class FeatureFlagService:
    """Evaluates feature flags from ZooKeeper config."""
    
    def __init__(self, config_client):
        self.config = config_client
    
    def is_enabled(self, flag_name, user_id=None, context=None):
        flag = self.config.get(f"feature_flags/{flag_name}")
        
        if not flag or not flag.get('enabled'):
            return False
        
        # Check allowlist (specific users always get the feature)
        if user_id and user_id in flag.get('allowlist', []):
            return True
        
        # Check blocklist
        if user_id and user_id in flag.get('blocklist', []):
            return False
        
        # Check region constraint
        if 'region' in flag and context:
            if context.get('region') != flag['region']:
                return False
        
        # Percentage rollout (deterministic based on user_id)
        rollout_pct = flag.get('rollout_pct', 100)
        if rollout_pct < 100 and user_id:
            # Hash user_id to get a stable 0-99 bucket
            bucket = hash(f"{flag_name}:{user_id}") % 100
            return bucket < rollout_pct
        
        return True

# Usage:
flags = FeatureFlagService(config_client)
if flags.is_enabled("new_checkout", user_id="user:42", context={"region": "us-east"}):
    render_new_checkout()
else:
    render_old_checkout()
```

### Feature Flag Lifecycle

```
1. CREATE (disabled):  set /config/feature_flags/new_feature '{"enabled": false}'
2. ENABLE for team:    set ... '{"enabled": true, "allowlist": ["user:dev1", "user:dev2"]}'
3. GRADUAL ROLLOUT:    set ... '{"enabled": true, "rollout_pct": 5}'    → 5% of users
4. EXPAND:             set ... '{"enabled": true, "rollout_pct": 25}'   → 25%
5. FULL ROLLOUT:       set ... '{"enabled": true, "rollout_pct": 100}'  → all users
6. PERMANENT:          Remove flag check from code. Delete ZK node.

Each change propagates to all servers within 100ms via watches.
No code deployment required for steps 1-5.
```

---

## Dynamic Config Propagation

### Propagation Methods Comparison

```
Method 1: ZooKeeper watches (push)
  Latency: ~100ms after write.
  Mechanism: Server-initiated push via watch callback.
  Pros: Near-instant, no polling, efficient.
  Cons: One-time watches require re-registration. ZK dependency.

Method 2: Polling a config service (pull)
  Latency: 0 to poll_interval (e.g., 30 seconds).
  Mechanism: Servers poll config endpoint every N seconds.
  Pros: Simple, no special infrastructure.
  Cons: Delay up to poll_interval. Wasteful if config rarely changes.

Method 3: Config reload via SIGHUP (manual)
  Latency: Manual trigger.
  Mechanism: Admin sends signal to each server to reload config.
  Pros: Explicit control.
  Cons: Doesn't scale. Must SSH into each server (or use orchestration).

Method 4: Config embedded in deployment (static)
  Latency: Minutes to hours (new deployment required).
  Mechanism: Config baked into container image or config file.
  Pros: Immutable, versioned, auditable.
  Cons: Slow to change. Requires redeployment.

Recommendation:
  Critical config (feature flags, rate limits): ZooKeeper watches (instant).
  Infrastructure config (DB host, ports): Polling or deployment-embedded.
  Secrets (API keys, passwords): Secrets manager (AWS Secrets Manager, Vault).
```

### Config Versioning and Rollback

```
ZooKeeper stores the VERSION of each znode (stat.version):

  set /config/rate_limits/default "100"   → version 0
  set /config/rate_limits/default "200"   → version 1
  set /config/rate_limits/default "500"   → version 2

  Conditional update (CAS):
    set /config/rate_limits/default "300" -v 2
    # Only succeeds if current version is 2. Prevents lost updates.

  Rollback: Set the value back to the previous one.
    set /config/rate_limits/default "200"   → version 3 (same value as v1)

For audit trail:
  ZooKeeper doesn't store history. Pair with:
    - Git-backed config repo (version-controlled source of truth)
    - Audit log in a database (who changed what, when)
    - ZK writes go through an admin service that logs changes before writing to ZK
```

---

## ZAB Protocol — How ZooKeeper Achieves Consensus

### ZAB (ZooKeeper Atomic Broadcast)

```
ZAB is ZooKeeper's consensus protocol (similar to Raft/Paxos).

Write flow:
  1. Client sends write to any server.
  2. If not the leader → forward to leader.
  3. Leader assigns a transaction ID (zxid) to the write.
  4. Leader broadcasts PROPOSAL to all followers.
  5. Followers write to disk, send ACK to leader.
  6. Leader waits for majority ACK (quorum).
  7. Leader broadcasts COMMIT to all followers.
  8. Followers apply the write to their in-memory data tree.
  9. Leader responds to client: SUCCESS.

  Timeline:
    Client → Server → Leader → Proposal → Quorum ACK → Commit → Response
    Total: 2-10ms (depending on network and disk)

Properties:
  - Total order: All writes are ordered globally (by zxid).
  - Atomic: A write either commits on all servers or none.
  - Durable: Committed writes survive server crashes (written to disk before ACK).
```

### Leader Election in ZAB

```
When the current leader dies:

  1. Followers detect leader loss (heartbeat timeout).
  2. Each follower proposes itself as leader with its highest zxid.
  3. Followers vote for the candidate with the highest zxid.
  4. Candidate with majority votes becomes the new leader.
  5. New leader synchronizes all followers to its state.
  6. Normal operation resumes.

  Election time: 200ms - 2 seconds (typically < 1 second).
  During election: No writes accepted. Reads may be stale.
```

---

## ZooKeeper vs etcd vs Consul

### Feature Comparison

| Feature | ZooKeeper | etcd | Consul |
|---|---|---|---|
| **Consensus** | ZAB | Raft | Raft |
| **Data model** | Hierarchical tree (znodes) | Flat key-value | Key-value + service catalog |
| **Watch mechanism** | One-time watches | Persistent watches (streams) | Blocking queries (long poll) |
| **Language** | Java | Go | Go |
| **Typical use** | Kafka, HBase, Hadoop | Kubernetes | Service mesh, HashiCorp stack |
| **API** | Custom TCP protocol | gRPC + HTTP/JSON | HTTP API |
| **Max value size** | 1 MB | 1.5 MB | 512 KB |
| **Transactions** | Multi-op | Multi-op (txn) | Check-and-set |
| **Service discovery** | Manual (ephemeral nodes) | Manual | Built-in (first-class) |
| **Health checks** | Session heartbeat | Lease + keepalive | Built-in health checks |

### When to Choose Which

```
Choose ZooKeeper when:
  ✅ Using Kafka, HBase, or Hadoop ecosystem (they require it)
  ✅ Need hierarchical data model
  ✅ Mature, battle-tested is priority
  ❌ Newer projects may prefer etcd or Consul

Choose etcd when:
  ✅ Using Kubernetes (etcd is the backing store)
  ✅ Need persistent watches (not one-time)
  ✅ Prefer Go ecosystem and gRPC API
  ✅ Modern greenfield projects

Choose Consul when:
  ✅ Need built-in service discovery + health checks
  ✅ Using HashiCorp stack (Nomad, Vault, Terraform)
  ✅ Need service mesh features
  ✅ Multi-datacenter support is critical
```

---

## Anti-Patterns and Pitfalls

### ❌ Anti-Pattern 1: Using ZooKeeper as a Database

```
Bad: Storing user profiles, product catalogs, or logs in ZooKeeper.
Why: ZooKeeper is optimized for small metadata (< 1 KB per znode).
     Large values degrade performance. 1 MB limit per znode.
Fix: Use ZooKeeper for coordination metadata only. Use a real database for data.
```

### ❌ Anti-Pattern 2: Using ZooKeeper as a Message Queue

```
Bad: Enqueueing/dequeueing messages via sequential znodes.
Why: ZooKeeper writes go through consensus (slow for high throughput).
     10K writes/sec max vs Kafka's millions/sec.
Fix: Use Kafka, SQS, or RabbitMQ for messaging. ZooKeeper for coordination.
```

### ❌ Anti-Pattern 3: Thundering Herd on Watches

```
Bad: All 500 clients watch the same znode. Config change → 500 simultaneous re-reads.
Why: ZooKeeper can handle it, but the sudden spike may look alarming.
Fix: Stagger re-reads. Use jitter: sleep(random(0, 1s)) before re-reading.
     Or use a local cache with short TTL + async watch refresh.
```

### ❌ Anti-Pattern 4: Forgetting Watch Re-Registration

```
Bad: Setting a watch, receiving the event, but not re-registering.
Why: Watches are ONE-TIME. After firing, you must re-register to catch the next change.
Fix: Always re-register the watch inside the watch callback handler.
```

### ❌ Anti-Pattern 5: Session Timeout Too Short

```
Bad: Session timeout = 5 seconds. A brief GC pause → session expires → leader lost!
Why: JVM garbage collection can pause for 2-5 seconds. Network blips happen.
Fix: Set session timeout to 10-30 seconds. Balance between detection speed and false positives.
```

---

## Configuration Management Without ZooKeeper

### Alternative Approaches

```
Approach 1: AWS Systems Manager Parameter Store
  Managed service. Hierarchical parameters. Encryption. IAM access control.
  No push notifications (polling or Lambda triggers).
  Best for: AWS-native apps, secrets management.

Approach 2: AWS AppConfig
  Feature flags, operational parameters, gradual rollout.
  Push-based with configurable deployment strategies.
  Best for: Feature flags with controlled rollout.

Approach 3: HashiCorp Consul KV
  Key-value store with blocking queries (long polling).
  Built-in service discovery and health checks.
  Best for: HashiCorp stack users.

Approach 4: Spring Cloud Config Server
  Git-backed config server. Spring ecosystem.
  Polling-based with webhook triggers.
  Best for: Spring Boot microservices.

Approach 5: Environment Variables + ConfigMaps (Kubernetes)
  Config injected at deployment time.
  Change requires pod restart (or ConfigMap reload).
  Best for: Kubernetes-native, when config changes are infrequent.

Approach 6: Database-Backed Config Table
  Simple table: (key, value, updated_at).
  Servers poll every N seconds.
  Best for: Simple systems without ZooKeeper/etcd dependency.
```

---

## Observability and Monitoring

### Key Metrics

```
ZooKeeper Health:
  zk.outstanding_requests     = gauge    # Requests queued at leader
  zk.avg_latency_ms           = gauge    # Average request latency
  zk.max_latency_ms           = gauge    # Max request latency
  zk.alive_connections        = gauge    # Active client sessions
  zk.watch_count              = gauge    # Total registered watches
  zk.znode_count              = gauge    # Total znodes in the tree
  zk.approximate_data_size    = gauge    # Total data in memory

Ensemble Health:
  zk.synced_followers         = gauge    # Followers in sync with leader
  zk.pending_syncs            = gauge    # Followers catching up
  zk.leader_election_time_ms  = histogram
  zk.quorum_size              = gauge    # Current quorum count

Configuration Management:
  config.propagation_time_ms  = histogram  # Time from write to client update
  config.watch_fire_count     = counter    # Watch events delivered
  config.stale_read_count     = counter    # Reads that hit stale data
```

### Alerting

```
CRITICAL:
  zk.synced_followers < quorum_size - 1    → Losing quorum
  zk.avg_latency_ms > 100                  → ZK overloaded
  zk.outstanding_requests > 1000           → Request backlog
  
WARNING:
  zk.alive_connections > 5000              → Too many clients
  zk.watch_count > 100000                  → Watch explosion
  config.propagation_time_ms.p99 > 5000    → Slow config propagation
```

---

## Real-World Production Patterns

### Pattern 1: Kafka + ZooKeeper

```
Apache Kafka uses ZooKeeper for:
  - Broker registration (/brokers/ids/{id}) — ephemeral nodes
  - Partition leader election (/brokers/topics/{topic}/partitions/{id}/state)
  - Topic configuration (/config/topics/{topic})
  - Controller election (one broker coordinates partition assignments)
  - Consumer group offsets (legacy, now stored in Kafka itself)

Note: Kafka is moving away from ZooKeeper (KRaft mode — Raft built into Kafka).
  KRaft replaces ZooKeeper's role entirely.
  ZooKeeper dependency removed in Kafka 3.5+.
```

### Pattern 2: HBase + ZooKeeper

```
Apache HBase uses ZooKeeper for:
  - Master election (one active HBase master)
  - Region server registration (which servers are alive)
  - Root table location (where to find metadata)
  - Distributed locks for schema changes
```

### Pattern 3: Feature Flags (Custom Implementation)

```
Many companies build custom feature flag systems on ZooKeeper/etcd:

  LaunchDarkly-style architecture:
    Admin UI → Config Service → ZooKeeper → SDK (in each app server)
    
    Admin toggles feature flag.
    Config Service writes to ZooKeeper.
    SDK (running in each app server) receives watch event.
    Flag evaluation uses local in-memory copy (sub-microsecond).
    
  Scale: 100K+ flag evaluations/sec per server (all local, no network).
  Propagation: ~100ms from admin toggle to all servers updated.
```

### Pattern 4: Database Failover Coordination

```
Custom database failover using ZooKeeper:

  Primary DB registers: /database/primary → "db-host-1:5432" (ephemeral)
  If primary crashes: Ephemeral node deleted.
  Standby watches /database/primary:
    Watch fires → standby promotes itself to primary.
    Creates new ephemeral node: /database/primary → "db-host-2:5432"
    App servers watching /database/primary get notified → update connection string.
    
  Automatic failover without human intervention.
```

### Pattern 5: Distributed Rate Limit Config

```
Rate limit thresholds stored in ZooKeeper:

  /config/rate_limits
  ├── /config/rate_limits/free       → '{"requests_per_min": 100}'
  ├── /config/rate_limits/premium    → '{"requests_per_min": 10000}'
  └── /config/rate_limits/emergency  → '{"global_rate": 50000}'

  Ops changes emergency rate limit during an incident:
    set /config/rate_limits/emergency '{"global_rate": 10000}'
    
  All API servers receive watch event within 100ms.
  Rate limiter immediately starts enforcing the new threshold.
  No deployment. No restart. Instant response to the incident.
```

---

## Interview Talking Points & Scripts

### Script 1: What ZooKeeper Is

> *"ZooKeeper is a distributed coordination service that provides building blocks like leader election, distributed locks, service discovery, and configuration management. It's a small, replicated key-value store with a hierarchical namespace, strong consistency for writes via the ZAB consensus protocol, and push-based change notifications via watches. It stores metadata about the system — not the data itself."*

### Script 2: Leader Election

> *"For leader election, I'd use ZooKeeper ephemeral sequential nodes. Each candidate creates a node under /election/ — ZooKeeper appends a monotonic sequence number. The candidate with the lowest sequence is the leader. If the leader dies, its ephemeral node is auto-deleted (session expires), and the next-lowest candidate becomes leader. Each candidate only watches the node immediately before it to avoid a thundering herd."*

### Script 3: Configuration Management

> *"I'd store application configuration in ZooKeeper znodes and use watches for push-based propagation. When an admin changes a config value, ZooKeeper commits it via quorum, then sends watch events to all subscribed clients within ~100ms. Each client re-reads the new value and re-registers the watch. This gives us dynamic config changes without redeployment — feature flags toggle in under a second across 50 servers."*

### Script 4: Service Discovery

> *"For service discovery, each instance creates an ephemeral znode under /services/{service-name}/ with its address and metadata. Clients watch the children of the service path. When a new instance starts, a child-created event fires. When an instance dies, its ephemeral node is deleted and a child-removed event fires. Load balancers update their instance list in real-time."*

### Script 5: When to Use ZooKeeper vs Alternatives

> *"ZooKeeper is the right choice when using Kafka or HBase, or when you need a battle-tested coordination service with hierarchical data. For Kubernetes environments, etcd is the natural choice since it's already the backing store. For service discovery with built-in health checks, Consul is better. For simple feature flags without a ZK dependency, AWS AppConfig or a database-backed polling approach works well."*

### Script 6: Feature Flags

> *"I'd implement feature flags as JSON values in ZooKeeper znodes. Each flag has enabled/disabled state, rollout percentage, and optional allowlist. The feature flag SDK runs in each app server, subscribes to watches, and maintains an in-memory copy. Flag evaluation is sub-microsecond (local memory lookup), and flag changes propagate to all servers in ~100ms. Rolling out a feature from 10% to 50% is a single ZooKeeper write — no deployment needed."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using ZooKeeper for large data

**Bad**: "We'll store user profiles in ZooKeeper."
**Fix**: ZooKeeper is for small metadata (< 1 KB). Use a database for data.

### ❌ Mistake 2: Confusing ZooKeeper with a message queue

**Bad**: "We'll use ZooKeeper to queue messages for workers."
**Fix**: ZooKeeper's write throughput (10K/sec) is 100x less than Kafka. Use Kafka/SQS for messaging.

### ❌ Mistake 3: Not mentioning watches for config propagation

**Bad**: "Servers poll ZooKeeper every 30 seconds for config changes."
**Fix**: ZooKeeper watches push changes to clients in ~100ms. Polling defeats the purpose of ZooKeeper.

### ❌ Mistake 4: Not explaining ephemeral nodes for liveness

**Bad**: "We'll write heartbeats to ZooKeeper."
**Fix**: Ephemeral nodes handle liveness automatically. When the session dies, the node is deleted. No explicit heartbeats needed (ZooKeeper handles session keepalives internally).

### ❌ Mistake 5: All clients watching the leader node

**Bad**: 500 candidates all watch /election/leader → leader dies → 500 simultaneous notifications.
**Fix**: Each candidate watches only the node immediately before it (herd avoidance pattern).

### ❌ Mistake 6: Not mentioning consensus/quorum

**Bad**: "ZooKeeper writes to one server and it's consistent."
**Fix**: ZooKeeper writes go through the ZAB protocol: leader proposes → majority ACK → committed. This is why it's consistent.

### ❌ Mistake 7: Ignoring session timeouts

**Bad**: "If the server dies, ZooKeeper instantly removes its nodes."
**Fix**: Ephemeral nodes are removed after the session timeout (10-30 seconds), not instantly. There's a detection delay.

### ❌ Mistake 8: Not considering alternatives

**Bad**: "We always use ZooKeeper for coordination."
**Fix**: etcd for Kubernetes environments, Consul for service mesh, AWS AppConfig for feature flags. Choose based on ecosystem.

---

## ZooKeeper by System Design Problem

| Problem | ZK Use Case | Pattern | Alternative |
|---|---|---|---|
| **Kafka** | Broker membership, leader election | Ephemeral + watches | KRaft (Kafka without ZK) |
| **Job Scheduler** | Scheduler leader election | Ephemeral sequential | Redis SETNX |
| **Rate Limiter** | Dynamic rate limit config | Config znodes + watches | AppConfig, DB polling |
| **Feature Flags** | Flag values + rollout | Config znodes + watches | LaunchDarkly, AppConfig |
| **Service Discovery** | Instance registration | Ephemeral + child watches | Consul, Kubernetes DNS |
| **Distributed Lock** | Resource exclusion | Ephemeral sequential | Redis Redlock, DB advisory locks |
| **Database Failover** | Primary/standby coordination | Ephemeral + watches | Patroni, Consul |
| **Cluster Membership** | Which nodes are alive | Ephemeral + child watches | Consul, gossip protocol |
| **Config Propagation** | Push config to N servers | Znodes + watches | etcd, Consul KV, polling |
| **Workflow Orchestration** | Step completion tracking | Persistent znodes | Temporal, Step Functions |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│       ZOOKEEPER & CONFIGURATION MANAGEMENT — CHEAT SHEET             │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ZOOKEEPER IS:                                                       │
│    Coordination service. Small metadata. Strong consistency.         │
│    NOT a database, message queue, or cache.                          │
│                                                                      │
│  DATA MODEL:                                                         │
│    Hierarchical tree of znodes. Max 1 MB per znode.                  │
│    Persistent: Exists until deleted.                                 │
│    Ephemeral: Deleted when client session expires.                   │
│    Sequential: Auto-incrementing suffix.                             │
│                                                                      │
│  WATCHES: Push-based, one-time trigger on znode change.              │
│    Re-register after each event. ~100ms propagation.                 │
│    Data watches: value changes. Child watches: children added/removed│
│                                                                      │
│  CONSENSUS (ZAB):                                                    │
│    Write → Leader → Proposal → Quorum ACK → Commit.                 │
│    Reads: Local (fast, may be slightly stale).                       │
│    Write: 2-10ms. Read: 0.1-1ms.                                    │
│                                                                      │
│  LEADER ELECTION:                                                    │
│    Ephemeral sequential nodes. Lowest number = leader.               │
│    Each candidate watches predecessor only (herd avoidance).         │
│    Leader dies → ephemeral node deleted → next candidate wins.       │
│                                                                      │
│  DISTRIBUTED LOCKS:                                                  │
│    Same as leader election. Ephemeral sequential.                    │
│    Lowest = lock holder. Release = delete node.                      │
│    Crash = session expires = node deleted = lock released.           │
│                                                                      │
│  SERVICE DISCOVERY:                                                  │
│    Each instance: ephemeral node under /services/{name}/.            │
│    Clients watch children → notified on join/leave.                  │
│                                                                      │
│  CONFIGURATION MANAGEMENT:                                           │
│    Store config in znodes. Watch for changes.                        │
│    Admin updates ZK → watches fire → all servers update.             │
│    Propagation: ~100ms. No deployment needed.                        │
│                                                                      │
│  FEATURE FLAGS:                                                      │
│    JSON in znodes: {enabled, rollout_pct, allowlist}.                │
│    SDK watches ZK, evaluates locally (sub-microsecond).              │
│    Toggle flag → ZK write → 100ms propagation → done.               │
│                                                                      │
│  ALTERNATIVES:                                                       │
│    etcd: Kubernetes, persistent watches, Go/gRPC.                    │
│    Consul: Service discovery + health checks, HashiCorp.             │
│    AppConfig: AWS feature flags, managed.                            │
│    DB polling: Simple, no ZK dependency, delayed.                    │
│                                                                      │
│  PERFORMANCE:                                                        │
│    Writes: 10-20K/sec (quorum-limited).                              │
│    Reads: 100-200K/sec (local).                                      │
│    Ensemble: 3/5/7 servers (odd number for quorum).                  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 32: Leader Election** — Leader election patterns in depth
- **Topic 4: Consistency Models** — Strong vs eventual consistency
- **Topic 40: Redis Deep Dive** — Redis as alternative for locks and config
- **Topic 54: Distributed Rate Limiting** — Dynamic rate limit config via ZK
- **Topic 59: Distributed Job Scheduling** — Leader election for the scheduler

---

*This document is part of the System Design Interview Deep Dive series.*
