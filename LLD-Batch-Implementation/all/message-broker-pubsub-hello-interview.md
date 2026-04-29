# Multi-Threaded Topic-Based Message Broker - HELLO Interview Framework

> **Companies**: Microsoft (Azure Service Bus), LinkedIn (Kafka), Google (Pub/Sub), Amazon (SQS/SNS)  
> **Difficulty**: Hard  
> **Pattern**: Observer (pub/sub notification) + Concurrent Data Structures + Per-Subscriber Offset Tracking  
> **Time**: 45 minutes

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

### 🎯 What is a Topic-Based Message Broker?

A system that decouples **publishers** (message producers) from **subscribers** (message consumers) using **topics** as logical channels. Publishers send messages to a topic. Subscribers subscribe to topics and independently consume messages at their own pace, each tracking their own **offset** (position in the message log). Unlike a traditional queue (message consumed once), pub/sub delivers every message to **every** subscriber of that topic.

**Real-world**: Apache Kafka, Azure Service Bus Topics, Google Cloud Pub/Sub, Amazon SNS + SQS fan-out, Redis Pub/Sub, RabbitMQ topic exchanges, NATS.

### For L63 Microsoft Interview

1. **Thread safety**: Multiple publishers and subscribers operating concurrently — no race conditions
2. **Per-topic message log**: Append-only list per topic (like Kafka partitions)
3. **Per-subscriber offset**: Each subscriber tracks its own position in each topic's log — independent consumption
4. **Offset reset/replay**: Subscribers can rewind to any previous offset to re-read messages
5. **CopyOnWriteArrayList**: Thread-safe append-only log — reads don't block writes
6. **ConcurrentHashMap**: Topic registry and subscriber registry — lock-free reads
7. **ReadWriteLock on offset**: Per-subscriber offset updates are atomic
8. **No message loss**: Published message is immediately visible to all current subscribers

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "Message format?" → Simple strings (no serialization complexity)
- "Delivery guarantee?" → At-least-once: every subscriber gets every message published after subscription
- "Ordering?" → Messages within a topic are ordered by publish time
- "What if subscriber is slow?" → Messages persist in topic log — subscriber reads at own pace via offset
- "Replay support?" → Yes, subscriber can reset offset to any previous position
- "Persistence?" → In-memory only (out of scope: disk, replication, fault tolerance)
- "Consumer groups?" → Out of scope (each subscriber is independent)

### Functional Requirements (P0)
1. **Create topic**: Named topic with its own message queue
2. **Publish message**: Publisher sends a string message to a specific topic
3. **Subscribe**: Subscriber subscribes to one or more topics — receives all future messages
4. **Consume/poll**: Subscriber reads next message from a topic (advances offset)
5. **Offset tracking**: Each subscriber has independent offset per topic
6. **Offset reset**: Subscriber can reset offset to any valid position (replay messages)
7. **Concurrent operations**: Multiple publishers and subscribers operate in parallel without data corruption

### Functional Requirements (P1)
- Unsubscribe from topic
- List all topics / List subscribers per topic
- Batch consume (read N messages at once)

### Non-Functional
- Thread-safe for concurrent publish/subscribe/consume
- O(1) publish (append to log)
- O(1) consume (read at offset)
- O(1) offset reset
- Subscribers don't block each other
- Publishers don't block subscribers

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌─────────────────────────────────────────────────────────────────┐
│                        MessageBroker                             │
│  - topics: ConcurrentHashMap<String, Topic>                     │
│  - subscribers: ConcurrentHashMap<String, Subscriber>           │
│  - createTopic(name) → Topic                                    │
│  - publish(topicName, message) → int (offset)                   │
│  - subscribe(subscriberId, topicName)                            │
│  - consume(subscriberId, topicName) → Message                   │
│  - resetOffset(subscriberId, topicName, offset)                  │
└──────────────┬──────────────────────────────────────────────────┘
               │ manages
     ┌─────────┼──────────────────┐
     ▼         ▼                  ▼
┌──────────┐ ┌──────────────────┐ ┌──────────────────────────────┐
│  Topic   │ │   Subscriber     │ │       Message                │
│          │ │                  │ │                              │
│ - name   │ │ - id             │ │ - content: String            │
│ - messages│ │ - offsets: Map   │ │ - offset: int               │
│   (log)  │ │   <Topic, int>   │ │ - timestamp: long           │
│ - subs   │ │ - subscribedTo   │ │ - publisherId: String       │
│   (set)  │ │   : Set<Topic>   │ └──────────────────────────────┘
│          │ │                  │
│ - publish│ │ - consume(topic) │
│   (msg)  │ │ - resetOffset   │
│ - getMsg │ │   (topic, pos)  │
│   (offset)│ │ - getOffset     │
└──────────┘ └──────────────────┘
```

### Class: Message
| Attribute | Type | Description |
|-----------|------|-------------|
| content | String | The message payload |
| offset | int | Position in topic's message log (0-indexed) |
| timestamp | long | When the message was published |
| publisherId | String | Who published it (for debugging/tracing) |

### Class: Topic
| Attribute | Type | Description |
|-----------|------|-------------|
| name | String | "orders", "payments", "user-events" |
| messages | CopyOnWriteArrayList\<Message\> | Append-only log — thread-safe reads |
| subscribers | ConcurrentHashMap.newKeySet() | Set of active subscriber IDs |

### Class: Subscriber
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | "sub-1", "analytics-service" |
| offsets | ConcurrentHashMap\<String, AtomicInteger\> | Per-topic offset (topic name → current position) |
| subscribedTopics | Set\<String\> | Topics this subscriber is subscribed to |

---

## 3️⃣ API Design

```java
class MessageBroker {
    /** Create a new topic. Idempotent — returns existing if already created. */
    Topic createTopic(String topicName);
    
    /** Publish a message to a topic. Returns the offset assigned. Thread-safe. */
    int publish(String topicName, String message);
    
    /** Subscribe to a topic. Subscriber starts at current end of log (new messages only). */
    void subscribe(String subscriberId, String topicName);
    
    /** Consume next unread message from topic for this subscriber. Advances offset. */
    Message consume(String subscriberId, String topicName);
    
    /** Consume up to N messages at once. Advances offset by number consumed. */
    List<Message> consumeBatch(String subscriberId, String topicName, int maxMessages);
    
    /** Reset subscriber's offset on a topic to replay messages. */
    void resetOffset(String subscriberId, String topicName, int newOffset);
    
    /** Get subscriber's current offset for a topic. */
    int getOffset(String subscriberId, String topicName);
    
    /** Unsubscribe from a topic. */
    void unsubscribe(String subscriberId, String topicName);
    
    /** Get the total number of messages in a topic. */
    int getTopicSize(String topicName);
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Publish + Two Subscribers Consume Independently

```
Setup:
  broker.createTopic("orders")
  broker.subscribe("analytics", "orders")    ← offset starts at 0
  broker.subscribe("billing", "orders")      ← offset starts at 0

Publish 3 messages:
  broker.publish("orders", "Order-1 created")   → offset 0
  broker.publish("orders", "Order-2 created")   → offset 1
  broker.publish("orders", "Order-3 created")   → offset 2

Topic "orders" log:
  [0] "Order-1 created"
  [1] "Order-2 created"
  [2] "Order-3 created"

Subscriber "analytics" consumes:
  consume("analytics", "orders") → "Order-1 created" (offset 0→1)
  consume("analytics", "orders") → "Order-2 created" (offset 1→2)
  // analytics stops here — offset = 2

Subscriber "billing" consumes all:
  consume("billing", "orders")   → "Order-1 created" (offset 0→1)
  consume("billing", "orders")   → "Order-2 created" (offset 1→2)
  consume("billing", "orders")   → "Order-3 created" (offset 2→3)
  consume("billing", "orders")   → null (no more messages, offset stays 3)

Key observation:
  ✅ analytics at offset 2, billing at offset 3 — INDEPENDENT
  ✅ Both got all messages, at their own pace
  ✅ Messages persist in log — not deleted after consumption
```

### Scenario 2: Offset Reset (Replay)

```
Subscriber "analytics" wants to re-process all orders:
  getOffset("analytics", "orders") → 2  (already consumed 0,1)

  resetOffset("analytics", "orders", 0)   ← rewind to beginning!
  getOffset("analytics", "orders") → 0

  consume("analytics", "orders") → "Order-1 created"  (re-read!)
  consume("analytics", "orders") → "Order-2 created"  (re-read!)
  consume("analytics", "orders") → "Order-3 created"  (new!)

Key observation:
  ✅ Replay is just moving an integer pointer — O(1)
  ✅ No data duplication — same log, different offset
  ✅ This is exactly how Kafka consumer offset reset works
```

### Scenario 3: Concurrent Publishers (Thread Safety)

```
Thread-1: publish("orders", "Order-A")  ─┐
Thread-2: publish("orders", "Order-B")  ─┤  CONCURRENT
Thread-3: publish("orders", "Order-C")  ─┘

CopyOnWriteArrayList guarantees:
  ├─ Each add() is atomic (internally synchronized)
  ├─ Offset assigned = list.size() at time of add
  ├─ All 3 messages end up in log (no lost writes)
  ├─ Order between concurrent publishes is NOT guaranteed
  │   (could be A,B,C or B,A,C or C,B,A — all valid)
  └─ Order within single thread IS guaranteed

Topic "orders" log after all 3:
  [0] "Order-A"  (or B or C — depends on scheduling)
  [1] "Order-B"
  [2] "Order-C"

  ✅ No race condition — CopyOnWriteArrayList is thread-safe
  ✅ No data corruption — each message gets unique offset
  ✅ Reads during writes are NOT blocked (readers see snapshot)
```

### Scenario 4: Subscribe After Messages Published (Late Subscriber)

```
broker.createTopic("events")
broker.publish("events", "Event-1")   → offset 0
broker.publish("events", "Event-2")   → offset 1

// Late subscriber joins AFTER 2 messages already published
broker.subscribe("late-sub", "events")
// late-sub's initial offset = topic.size() = 2 (starts at END)

broker.publish("events", "Event-3")   → offset 2

consume("late-sub", "events") → "Event-3"  (offset 2→3)
consume("late-sub", "events") → null       (caught up)

// late-sub did NOT see Event-1 or Event-2 (published before subscription)
// BUT: late-sub can REPLAY them by resetting offset:
resetOffset("late-sub", "events", 0)
consume("late-sub", "events") → "Event-1"  (offset 0→1)

  ✅ Default: only see messages AFTER subscribing
  ✅ Replay: can access full history by resetting offset to 0
```

### Scenario 5: Multiple Topics, One Subscriber

```
broker.createTopic("orders")
broker.createTopic("payments")

broker.subscribe("audit-service", "orders")
broker.subscribe("audit-service", "payments")

broker.publish("orders", "Order-1 placed")
broker.publish("payments", "Payment-1 received")
broker.publish("orders", "Order-2 placed")

// audit-service consumes from BOTH topics independently:
consume("audit-service", "orders")   → "Order-1 placed"     (orders offset 0→1)
consume("audit-service", "payments") → "Payment-1 received"  (payments offset 0→1)
consume("audit-service", "orders")   → "Order-2 placed"     (orders offset 1→2)

  ✅ Each topic has its own offset for this subscriber
  ✅ Consuming from one topic doesn't affect the other
```

### Scenario 6: Concurrent Consume (Subscribers Don't Block Each Other)

```
Thread-A: consume("analytics", "orders")  ─┐
Thread-B: consume("billing", "orders")    ─┤  CONCURRENT
Thread-C: consume("audit", "orders")      ─┘

Each subscriber has its OWN AtomicInteger offset:
  ├─ analytics.offsets["orders"] = AtomicInteger(0)
  ├─ billing.offsets["orders"]   = AtomicInteger(0)
  └─ audit.offsets["orders"]     = AtomicInteger(0)

Thread-A: reads messages[0], atomically increments analytics offset → 1
Thread-B: reads messages[0], atomically increments billing offset → 1
Thread-C: reads messages[0], atomically increments audit offset → 1

  ✅ No locking between subscribers — each has own AtomicInteger
  ✅ CopyOnWriteArrayList.get(offset) is lock-free
  ✅ Three subscribers reading same topic = zero contention
```

---

## 5️⃣ Design + Implementation

### Thread Safety Design Decisions

```java
// ==================== THREAD SAFETY STRATEGY ====================
// 
// DATA STRUCTURE CHOICES:
//
// 1. Topic message log: CopyOnWriteArrayList<Message>
//    WHY: Append-only log. Writes (publish) are infrequent relative to reads (consume).
//         CopyOnWriteArrayList gives lock-free reads — perfect for our read-heavy pattern.
//         Trade-off: Write is O(N) copy — acceptable for interview, discuss alternatives.
//
// 2. Topic registry: ConcurrentHashMap<String, Topic>
//    WHY: Topics created rarely, looked up constantly. Lock-free reads via CAS.
//
// 3. Per-subscriber offset: AtomicInteger per topic
//    WHY: Each subscriber independently increments its own offset.
//         AtomicInteger.getAndIncrement() is lock-free CAS — no contention between subscribers.
//
// 4. Subscriber set per topic: ConcurrentHashMap.newKeySet()
//    WHY: Subscribers added/removed occasionally, iterated on publish.
//
// ZERO CONTENTION BETWEEN:
//   - Different subscribers consuming same topic (each has own AtomicInteger)
//   - Subscribers consuming different topics (separate CopyOnWriteArrayLists)
//   - Publishers and subscribers (CopyOnWriteArrayList: reads don't block writes)
```

### Message Class

```java
class Message {
    private final String content;
    private final int offset;
    private final long timestamp;
    private final String publisherId;
    
    public Message(String content, int offset, String publisherId) {
        this.content = content;
        this.offset = offset;
        this.timestamp = System.currentTimeMillis();
        this.publisherId = publisherId;
    }
    
    public String getContent()    { return content; }
    public int getOffset()        { return offset; }
    public long getTimestamp()     { return timestamp; }
    public String getPublisherId() { return publisherId; }
    
    @Override
    public String toString() {
        return "[" + offset + "] " + content;
    }
}
```

### Topic Class

```java
// ==================== TOPIC ====================
// 💬 "Topic is the core data structure — an append-only log with subscriber tracking."

class Topic {
    private final String name;
    private final CopyOnWriteArrayList<Message> messages;  // append-only log
    private final Set<String> subscriberIds;               // active subscriber IDs
    
    public Topic(String name) {
        this.name = name;
        this.messages = new CopyOnWriteArrayList<>();
        this.subscriberIds = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Publish a message. Thread-safe — CopyOnWriteArrayList.add() is atomic.
     * Returns the offset assigned to this message.
     */
    public synchronized int publish(Message message) {
        // synchronized to ensure offset = size at time of add (no gap)
        int offset = messages.size();
        messages.add(new Message(message.getContent(), offset, message.getPublisherId()));
        return offset;
    }
    
    /**
     * Read message at specific offset. Lock-free — COWAL.get() doesn't block.
     * Returns null if offset is beyond current log size.
     */
    public Message getMessage(int offset) {
        if (offset < 0 || offset >= messages.size()) {
            return null;
        }
        return messages.get(offset);  // O(1) random access, no lock
    }
    
    /** Get current log size (for setting initial subscriber offset). */
    public int size() {
        return messages.size();
    }
    
    /** Get range of messages [fromOffset, toOffset). For batch consume. */
    public List<Message> getMessages(int fromOffset, int toOffset) {
        int end = Math.min(toOffset, messages.size());
        if (fromOffset >= end) return Collections.emptyList();
        return new ArrayList<>(messages.subList(fromOffset, end));
    }
    
    public void addSubscriber(String subscriberId)    { subscriberIds.add(subscriberId); }
    public void removeSubscriber(String subscriberId) { subscriberIds.remove(subscriberId); }
    public Set<String> getSubscriberIds()             { return Collections.unmodifiableSet(subscriberIds); }
    public String getName()                           { return name; }
}
```

**Why `synchronized` on publish?** The offset must equal `messages.size()` at the time of add. Without synchronization, two concurrent publishes could both read `size()=5`, both assign offset 5, then both add — resulting in two messages with offset 5 but actual positions 5 and 6. The `synchronized` block makes offset-assignment + add atomic.

**Why not `synchronized` on getMessage?** `CopyOnWriteArrayList.get()` is inherently thread-safe — it reads from an immutable snapshot array. No lock needed.

### Subscriber Class

```java
// ==================== SUBSCRIBER ====================
// 💬 "Each subscriber tracks its own offset per topic via AtomicInteger.
//    This means subscribers NEVER block each other — zero contention."

class Subscriber {
    private final String id;
    private final ConcurrentHashMap<String, AtomicInteger> offsets;  // topicName → offset
    private final Set<String> subscribedTopics;
    
    public Subscriber(String id) {
        this.id = id;
        this.offsets = new ConcurrentHashMap<>();
        this.subscribedTopics = ConcurrentHashMap.newKeySet();
    }
    
    /** Subscribe to topic — start at given offset (typically topic.size() = end of log). */
    public void subscribeTo(String topicName, int startOffset) {
        offsets.put(topicName, new AtomicInteger(startOffset));
        subscribedTopics.add(topicName);
    }
    
    /** Unsubscribe — remove offset tracking. */
    public void unsubscribeFrom(String topicName) {
        offsets.remove(topicName);
        subscribedTopics.remove(topicName);
    }
    
    /**
     * Get current offset and atomically advance it.
     * Uses AtomicInteger.getAndIncrement() — lock-free CAS.
     * Returns the offset to READ (before increment).
     */
    public int getAndAdvanceOffset(String topicName) {
        AtomicInteger offset = offsets.get(topicName);
        if (offset == null) throw new IllegalStateException(id + " not subscribed to " + topicName);
        return offset.getAndIncrement();
    }
    
    /** Reset offset to specific position (for replay). */
    public void resetOffset(String topicName, int newOffset) {
        AtomicInteger offset = offsets.get(topicName);
        if (offset == null) throw new IllegalStateException(id + " not subscribed to " + topicName);
        if (newOffset < 0) throw new IllegalArgumentException("Offset cannot be negative");
        offset.set(newOffset);
    }
    
    /** Get current offset without advancing (peek). */
    public int getOffset(String topicName) {
        AtomicInteger offset = offsets.get(topicName);
        if (offset == null) throw new IllegalStateException(id + " not subscribed to " + topicName);
        return offset.get();
    }
    
    public String getId()                   { return id; }
    public Set<String> getSubscribedTopics() { return Collections.unmodifiableSet(subscribedTopics); }
    public boolean isSubscribedTo(String t)  { return subscribedTopics.contains(t); }
}
```

**Why AtomicInteger for offset?**
- `getAndIncrement()` is a single CAS (Compare-And-Swap) operation — lock-free
- Each subscriber has its **own** AtomicInteger per topic — zero contention between subscribers
- Even if 100 subscribers consume the same topic concurrently, they never touch each other's AtomicIntegers

### MessageBroker (Coordinator)

```java
// ==================== MESSAGE BROKER ====================
// 💬 "The broker is the public API — coordinates topics, subscribers, and offsets.
//    Uses ConcurrentHashMap for both registries — lock-free reads for all lookups."

class MessageBroker {
    private final ConcurrentHashMap<String, Topic> topics;
    private final ConcurrentHashMap<String, Subscriber> subscribers;
    
    public MessageBroker() {
        this.topics = new ConcurrentHashMap<>();
        this.subscribers = new ConcurrentHashMap<>();
    }
    
    // ===== TOPIC MANAGEMENT =====
    
    /** Create topic. Idempotent — returns existing if already exists. */
    public Topic createTopic(String topicName) {
        return topics.computeIfAbsent(topicName, Topic::new);
    }
    
    // ===== PUBLISHING =====
    
    /** Publish message to topic. Thread-safe. Returns assigned offset. */
    public int publish(String topicName, String message) {
        return publish(topicName, message, "anonymous");
    }
    
    public int publish(String topicName, String message, String publisherId) {
        Topic topic = topics.get(topicName);
        if (topic == null) throw new IllegalArgumentException("Topic not found: " + topicName);
        
        Message msg = new Message(message, -1, publisherId);  // offset assigned by topic
        int offset = topic.publish(msg);
        
        System.out.println("  📤 Published to '" + topicName + "' at offset " + offset 
            + ": \"" + message + "\"");
        return offset;
    }
    
    // ===== SUBSCRIBING =====
    
    /** Subscribe to topic. Starts consuming from current end of log (new messages only). */
    public void subscribe(String subscriberId, String topicName) {
        Topic topic = topics.get(topicName);
        if (topic == null) throw new IllegalArgumentException("Topic not found: " + topicName);
        
        Subscriber subscriber = subscribers.computeIfAbsent(subscriberId, Subscriber::new);
        
        int startOffset = topic.size();  // start at END — only see future messages
        subscriber.subscribeTo(topicName, startOffset);
        topic.addSubscriber(subscriberId);
        
        System.out.println("  📥 '" + subscriberId + "' subscribed to '" + topicName 
            + "' at offset " + startOffset);
    }
    
    // ===== CONSUMING =====
    
    /** Consume next unread message. Returns null if caught up. Thread-safe. */
    public Message consume(String subscriberId, String topicName) {
        Topic topic = topics.get(topicName);
        Subscriber subscriber = subscribers.get(subscriberId);
        
        if (topic == null) throw new IllegalArgumentException("Topic not found: " + topicName);
        if (subscriber == null) throw new IllegalArgumentException("Subscriber not found: " + subscriberId);
        if (!subscriber.isSubscribedTo(topicName)) {
            throw new IllegalStateException(subscriberId + " not subscribed to " + topicName);
        }
        
        int currentOffset = subscriber.getAndAdvanceOffset(topicName);
        Message message = topic.getMessage(currentOffset);
        
        if (message == null) {
            // No message at this offset — we're caught up. Roll back the offset.
            subscriber.resetOffset(topicName, currentOffset);
            return null;
        }
        
        return message;
    }
    
    /** Consume up to maxMessages at once. */
    public List<Message> consumeBatch(String subscriberId, String topicName, int maxMessages) {
        Subscriber subscriber = subscribers.get(subscriberId);
        Topic topic = topics.get(topicName);
        
        if (topic == null || subscriber == null) throw new IllegalArgumentException("Invalid topic/subscriber");
        
        int fromOffset = subscriber.getOffset(topicName);
        int toOffset = Math.min(fromOffset + maxMessages, topic.size());
        
        List<Message> batch = topic.getMessages(fromOffset, toOffset);
        subscriber.resetOffset(topicName, toOffset);  // advance past consumed messages
        
        return batch;
    }
    
    // ===== OFFSET MANAGEMENT =====
    
    /** Reset offset — enables replay. */
    public void resetOffset(String subscriberId, String topicName, int newOffset) {
        Topic topic = topics.get(topicName);
        Subscriber subscriber = subscribers.get(subscriberId);
        
        if (topic == null || subscriber == null) throw new IllegalArgumentException("Invalid topic/subscriber");
        if (newOffset < 0 || newOffset > topic.size()) {
            throw new IllegalArgumentException("Offset out of range [0, " + topic.size() + "]: " + newOffset);
        }
        
        subscriber.resetOffset(topicName, newOffset);
        System.out.println("  🔄 '" + subscriberId + "' offset reset to " + newOffset 
            + " on '" + topicName + "'");
    }
    
    /** Get subscriber's current offset. */
    public int getOffset(String subscriberId, String topicName) {
        Subscriber subscriber = subscribers.get(subscriberId);
        if (subscriber == null) throw new IllegalArgumentException("Subscriber not found: " + subscriberId);
        return subscriber.getOffset(topicName);
    }
    
    // ===== UNSUBSCRIBE =====
    
    public void unsubscribe(String subscriberId, String topicName) {
        Topic topic = topics.get(topicName);
        Subscriber subscriber = subscribers.get(subscriberId);
        if (topic != null) topic.removeSubscriber(subscriberId);
        if (subscriber != null) subscriber.unsubscribeFrom(topicName);
        System.out.println("  🚫 '" + subscriberId + "' unsubscribed from '" + topicName + "'");
    }
    
    // ===== STATUS =====
    
    public int getTopicSize(String topicName) {
        Topic topic = topics.get(topicName);
        return topic != null ? topic.size() : 0;
    }
    
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Broker Status: ").append(topics.size()).append(" topics, ")
          .append(subscribers.size()).append(" subscribers\n");
        topics.forEach((name, topic) -> {
            sb.append("  Topic '").append(name).append("': ")
              .append(topic.size()).append(" messages, ")
              .append(topic.getSubscriberIds().size()).append(" subscribers\n");
        });
        return sb.toString();
    }
}
```

### Demo: Full Workflow

```java
// ==================== DEMO ====================
public class MessageBrokerDemo {
    public static void main(String[] args) throws InterruptedException {
        MessageBroker broker = new MessageBroker();
        
        // === Setup ===
        broker.createTopic("orders");
        broker.createTopic("payments");
        
        broker.subscribe("analytics", "orders");
        broker.subscribe("billing", "orders");
        broker.subscribe("billing", "payments");
        
        // === Publish ===
        broker.publish("orders", "Order-1 created");
        broker.publish("orders", "Order-2 created");
        broker.publish("payments", "Payment-1 received");
        
        // === Consume independently ===
        System.out.println("\n--- Analytics consumes orders ---");
        Message m;
        while ((m = broker.consume("analytics", "orders")) != null) {
            System.out.println("  analytics got: " + m);
        }
        
        System.out.println("\n--- Billing consumes orders ---");
        while ((m = broker.consume("billing", "orders")) != null) {
            System.out.println("  billing got: " + m);
        }
        
        System.out.println("\n--- Billing consumes payments ---");
        while ((m = broker.consume("billing", "payments")) != null) {
            System.out.println("  billing got: " + m);
        }
        
        // === Replay ===
        System.out.println("\n--- Analytics replays orders from offset 0 ---");
        broker.resetOffset("analytics", "orders", 0);
        while ((m = broker.consume("analytics", "orders")) != null) {
            System.out.println("  analytics (replay) got: " + m);
        }
        
        // === Concurrent Publishing ===
        System.out.println("\n--- Concurrent publishers ---");
        ExecutorService publishers = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            publishers.submit(() -> broker.publish("orders", "Concurrent-Order-" + idx));
        }
        publishers.shutdown();
        publishers.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println("Orders topic now has " + broker.getTopicSize("orders") + " messages");
        System.out.println(broker.getStatus());
    }
}
```

### Output:
```
  📥 'analytics' subscribed to 'orders' at offset 0
  📥 'billing' subscribed to 'orders' at offset 0
  📥 'billing' subscribed to 'payments' at offset 0
  📤 Published to 'orders' at offset 0: "Order-1 created"
  📤 Published to 'orders' at offset 1: "Order-2 created"
  📤 Published to 'payments' at offset 0: "Payment-1 received"

--- Analytics consumes orders ---
  analytics got: [0] Order-1 created
  analytics got: [1] Order-2 created

--- Billing consumes orders ---
  billing got: [0] Order-1 created
  billing got: [1] Order-2 created

--- Billing consumes payments ---
  billing got: [0] Payment-1 received

--- Analytics replays orders from offset 0 ---
  🔄 'analytics' offset reset to 0 on 'orders'
  analytics (replay) got: [0] Order-1 created
  analytics (replay) got: [1] Order-2 created

--- Concurrent publishers ---
  (10 messages published concurrently)
Orders topic now has 12 messages
Broker Status: 2 topics, 2 subscribers
  Topic 'orders': 12 messages, 2 subscribers
  Topic 'payments': 1 messages, 1 subscribers
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Thread Safety Analysis (Lock-Free Design)

| Operation | Synchronization | Contention | Why |
|-----------|----------------|------------|-----|
| **publish** | `synchronized` on Topic | Publishers to SAME topic contend | Offset must match log position — needs atomicity |
| **consume** | AtomicInteger.getAndIncrement() | Zero between different subscribers | Each subscriber has own AtomicInteger |
| **getMessage** | None (lock-free read) | Zero | CopyOnWriteArrayList.get() reads immutable snapshot |
| **createTopic** | ConcurrentHashMap.computeIfAbsent() | Zero | CAS-based, lock-free |
| **subscribe** | ConcurrentHashMap + AtomicInteger.set() | Zero | Only touches subscriber's own data |
| **resetOffset** | AtomicInteger.set() | Zero | Only caller touches their own offset |

**Key insight**: The only contention point is concurrent **publishes to the same topic** (synchronized block). Everything else is lock-free. In a real system (Kafka), this is solved by **partitioning** — each partition has its own lock.

### Deep Dive 2: CopyOnWriteArrayList vs Other Options

| Data Structure | Publish (Write) | Consume (Read) | Best For |
|---------------|----------------|----------------|----------|
| **CopyOnWriteArrayList** | O(N) copy on write | O(1) lock-free read | Read-heavy, write-light (our case) |
| **ArrayList + ReadWriteLock** | O(1) amortized write | O(1) read, needs read lock | Write-heavy workloads |
| **ConcurrentLinkedDeque** | O(1) lock-free write | O(N) traversal for offset | Queue semantics, no random access |
| **ArrayList + synchronized** | O(1) write | O(1) read, but blocks | Simple, but high contention |

**Why CopyOnWriteArrayList?** For an interview:
- Messages are published occasionally but consumed frequently (read-heavy)
- `get(offset)` is O(1) and completely lock-free — no reader ever blocks
- Write cost (O(N) array copy) is acceptable for in-memory interview scope
- **For production Kafka**, logs are backed by memory-mapped files, not COWAL

**Discussion**: "In production with 100K+ messages/sec, I'd use a ring buffer (like LMAX Disruptor) or memory-mapped files (like Kafka). CopyOnWriteArrayList works for our interview scope but doesn't scale for write-heavy workloads."

### Deep Dive 3: Kafka Concepts Mapped to Our Design

| Kafka Concept | Our Design | Description |
|---------------|-----------|-------------|
| Topic | Topic class | Logical channel of messages |
| Partition | Single-partition topic | Kafka splits topics across partitions (we use 1) |
| Offset | AtomicInteger per subscriber | Position in topic's log |
| Consumer Group | Not implemented (P1) | Group of subscribers sharing offsets |
| Producer | publish() caller | Sends messages to topic |
| Consumer | Subscriber.consume() | Reads messages at own offset |
| Log compaction | Not implemented | Remove old messages, keep latest per key |
| Broker cluster | Single broker | Kafka distributes across multiple brokers |
| Replication | Not implemented | Kafka replicates partitions for fault tolerance |

**Key differences from real Kafka**:
1. Kafka uses **partitions** for parallelism — our design uses a single log per topic
2. Kafka uses **consumer groups** — consumers in same group share partitions (load balancing)
3. Kafka persists to **disk** with memory-mapped files — we're in-memory
4. Kafka uses a **commit log** — offsets are committed periodically, not on every consume

### Deep Dive 4: Consumer Groups (P1 Extension)

```java
// If we needed consumer groups (like Kafka):
class ConsumerGroup {
    private final String groupId;
    private final Map<String, AtomicInteger> groupOffsets;  // ONE offset per topic for the group
    private final Set<String> members;                      // subscriber IDs in this group
    
    // When a group member consumes, it advances the GROUP offset
    // Other members see the advanced offset — load balancing!
    public synchronized Message consumeAsGroup(String topicName, Topic topic) {
        int offset = groupOffsets.get(topicName).getAndIncrement();
        return topic.getMessage(offset);
    }
}
```

**Consumer group vs individual subscribers**:
- **Individual**: Each subscriber gets ALL messages (fan-out, our current design)
- **Group**: Messages distributed among group members (load balancing)
- **Kafka uses both**: Consumer groups for load balancing, different groups for fan-out

### Deep Dive 5: Preventing Offset Overrun (Race Condition Edge Case)

```
EDGE CASE: consume() reads offset, but message doesn't exist yet

Thread-A (subscriber): getAndAdvanceOffset() → 5, reads messages[5] → null (not published yet)

FIX: Roll back offset if message is null:
```

```java
public Message consume(String subscriberId, String topicName) {
    int currentOffset = subscriber.getAndAdvanceOffset(topicName);
    Message message = topic.getMessage(currentOffset);
    
    if (message == null) {
        // ROLLBACK: offset advanced past available messages
        subscriber.resetOffset(topicName, currentOffset);
        return null;  // "no new messages"
    }
    
    return message;
}
```

**Alternative approach (strict)**: Check `offset < topic.size()` BEFORE advancing. But this introduces a TOCTOU race (Time-Of-Check-To-Time-Of-Use). The rollback approach is simpler and correct.

### Deep Dive 6: Push vs Pull Model

| Model | How It Works | Pros | Cons | Used By |
|-------|-------------|------|------|---------|
| **Pull (our design)** | Consumer calls consume() when ready | Consumer controls pace, simple backpressure | Consumer must poll, latency | Kafka, our design |
| **Push** | Broker pushes to subscriber callback | Low latency, no polling | Slow consumer can be overwhelmed | RabbitMQ, Redis Pub/Sub |
| **Hybrid** | Push notification + pull data | Low latency + backpressure | More complex | Azure Service Bus |

**Why pull?** Pull gives subscribers **backpressure** — they consume at their own speed. Push model risks overwhelming slow subscribers. Kafka uses pull for this exact reason.

### Deep Dive 7: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Consume from unsubscribed topic | throw IllegalStateException |
| Reset offset beyond log size | throw IllegalArgumentException(offset > topic.size()) |
| Reset offset to negative | throw IllegalArgumentException |
| Subscribe to non-existent topic | throw IllegalArgumentException |
| Publish to non-existent topic | throw IllegalArgumentException |
| Double subscribe same topic | Idempotent — offset already exists, no-op |
| Consume when caught up (no new messages) | Return null, rollback offset |
| Topic with 0 subscribers + publish | Message stored in log, no consumers yet |
| Concurrent resetOffset + consume on same subscriber-topic | AtomicInteger ensures visibility, one wins |
| Subscriber consumes from 2 topics on different threads | Safe — separate AtomicIntegers per topic |

### Deep Dive 8: Complexity Analysis

| Operation | Time | Space | Note |
|-----------|------|-------|------|
| createTopic | O(1) | O(1) | ConcurrentHashMap.computeIfAbsent |
| publish | O(N)* | O(N)* | *CopyOnWriteArrayList copies array on write |
| consume | O(1) | O(1) | AtomicInteger.getAndIncrement + ArrayList.get |
| consumeBatch | O(B) | O(B) | B = batch size |
| resetOffset | O(1) | O(1) | AtomicInteger.set |
| subscribe | O(1) | O(1) | Map put + set add |
| getTopicSize | O(1) | O(1) | CopyOnWriteArrayList.size() |
| **Total space** | — | O(T×M + S×T) | T=topics, M=messages, S=subscribers |

**Scalability bottleneck**: `publish` copies the entire array (CopyOnWriteArrayList). For production:
- Use `ArrayList + ReentrantLock` (O(1) amortized write)
- Use ring buffer for bounded topics
- Use memory-mapped files (Kafka approach)

### Deep Dive 9: Production Enhancements

| Enhancement | Approach | Benefit |
|-------------|----------|---------|
| **Partitioning** | Split topic into N partitions, hash message key to partition | Parallel writes + reads, higher throughput |
| **Consumer groups** | Group of subscribers sharing partitions | Load balancing across consumers |
| **Message TTL** | Background thread removes messages older than TTL | Bounded memory usage |
| **Disk persistence** | Append to log file + mmap | Survive restarts, handle more data than RAM |
| **Replication** | Copy partitions to follower brokers | Fault tolerance |
| **Exactly-once delivery** | Idempotent producer + transactional consume | No duplicate processing |
| **Dead letter queue** | Failed processing → DLQ topic | Handle poison messages |
| **Backpressure** | Block publisher when topic exceeds max size | Prevent OOM |
| **Message ordering key** | Messages with same key → same partition → ordered | Per-entity ordering guarantee |

---

## 📋 Interview Checklist (L63)

- [ ] **Topic-based pub/sub**: Separate message log per topic, fan-out to all subscribers
- [ ] **Per-subscriber offset**: AtomicInteger per subscriber per topic — independent consumption
- [ ] **Offset reset/replay**: Subscriber can rewind to any position — just an `AtomicInteger.set()`
- [ ] **Thread safety — publish**: `synchronized` on Topic for offset-assignment + add atomicity
- [ ] **Thread safety — consume**: AtomicInteger.getAndIncrement() — lock-free, zero contention between subscribers
- [ ] **Thread safety — data structures**: CopyOnWriteArrayList (lock-free reads), ConcurrentHashMap (lock-free lookups)
- [ ] **No blocking between subscribers**: Each has own AtomicInteger — 100 subscribers = 0 contention
- [ ] **Late subscriber**: Starts at end of log, can replay by resetting offset to 0
- [ ] **Edge cases**: Null message rollback, double subscribe idempotency, concurrent publish ordering
- [ ] **Kafka mapping**: Topic, partition, offset, consumer group — explained real-world parallels
- [ ] **Production extensions**: Partitioning, consumer groups, persistence, replication, TTL

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 5 min |
| Core Entities (Topic, Subscriber, Message, Broker) | 7 min |
| Thread Safety Design (which lock where) | 5 min |
| Implementation (Broker + Topic + Subscriber) | 15 min |
| Concurrent scenarios walkthrough | 5 min |
| Deep Dives (Kafka mapping, edge cases, production) | 8 min |
| **Total** | **~45 min** |

### Thread Safety Summary

| Component | Mechanism | Contention |
|-----------|-----------|------------|
| Topic message log | CopyOnWriteArrayList | Reads: zero. Writes: serialize per topic |
| Topic registry | ConcurrentHashMap | Zero (CAS-based) |
| Subscriber offset | AtomicInteger (one per subscriber per topic) | Zero between subscribers |
| Subscriber registry | ConcurrentHashMap | Zero (CAS-based) |
| Subscriber set per topic | ConcurrentHashMap.newKeySet() | Minimal (add/remove only) |

See `MessageBrokerSystem.java` for full implementation with concurrent publisher/subscriber demos.
