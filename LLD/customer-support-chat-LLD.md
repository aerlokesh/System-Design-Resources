# Customer Support Chat System - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [Chat Thread State Machine](#chat-thread-state-machine)
5. [Class Design](#class-design)
6. [Message Delivery System](#message-delivery-system)
7. [Complete Implementation](#complete-implementation)
8. [Extensibility](#extensibility)
9. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Customer Support Chat System?**

A customer support chat system (like Intercom) is a B2C platform that enables real-time communication between customers and support agents. Customers initiate chat threads, and agents join to provide assistance. The system supports text messages, media sharing, read receipts, reactions, and maintains conversation history.

**Real-World Examples:**
- Intercom
- Zendesk Chat
- LiveChat
- Drift
- Freshchat
- Help Scout

**Core Workflow:**
```
Customer Opens Chat → Sends Message → System Routes to Available Agent
    ↓
Agent Receives Notification → Joins Thread → Replies
    ↓
Real-Time Message Exchange → Read Receipts → Reactions
    ↓
Issue Resolved → Thread Closed → Conversation Archived
```

**Key Challenges:**
- **Real-Time Messaging:** Sub-second message delivery
- **Agent Routing:** Assign threads to appropriate/available agents
- **Multi-Agent Support:** Multiple agents in same thread
- **Read Receipts:** Track who read what and when
- **Media Messages:** Handle images, files, links
- **Message Ordering:** Maintain chronological order
- **Offline Support:** Queue messages when users offline
- **Concurrent Access:** Multiple agents accessing same thread

---

## Requirements

### Functional Requirements

1. **User Management**
   - Register customers and agents
   - User authentication
   - Agent availability status (Online, Away, Offline)
   - Agent specialties/departments

2. **Chat Thread Management**
   - Customer creates new thread
   - Assign thread to agent(s)
   - Multiple agents can join thread
   - View active/closed threads
   - Search threads by customer/content

3. **Messaging**
   - Send text messages
   - Send media messages (images, files)
   - Real-time delivery
   - Message ordering (timestamp-based)
   - Edit messages (within time window)
   - Delete messages

4. **Read Receipts**
   - Track message read status
   - Show who read each message
   - Display read timestamp
   - Typing indicators

5. **Reactions**
   - React to messages (emoji reactions)
   - Multiple reactions per message
   - View who reacted

6. **Thread Status**
   - Open, In-Progress, Waiting, Resolved, Closed
   - Auto-close after inactivity
   - Reopen closed threads

7. **Notifications**
   - Notify agents of new threads
   - Notify participants of new messages
   - Push notifications for offline users

### Non-Functional Requirements

1. **Performance**
   - Message delivery: < 500ms
   - Support 10,000+ concurrent chats
   - Message history load: < 1 second

2. **Scalability**
   - Millions of customers
   - Thousands of agents
   - Billions of messages

3. **Availability**
   - 99.9% uptime
   - Message queue for offline delivery
   - Graceful degradation

4. **Consistency**
   - Message ordering guaranteed
   - No duplicate messages
   - No lost messages

### Out of Scope

- Video/audio calls
- Screen sharing
- Chatbot AI integration
- Advanced analytics and reporting
- Multi-language translation
- Mobile app implementation
- Email integration

---

## Core Entities and Relationships

### Key Entities

1. **User (Abstract)**
   - Customer: End user seeking support
   - Agent: Support representative

2. **ChatThread**
   - Thread ID, subject, status
   - Participants (customer + agents)
   - Messages
   - Created/updated timestamps

3. **Message**
   - Message ID, content, type
   - Sender, timestamp
   - Read receipts
   - Reactions
   - Reply-to reference (threading)

4. **ReadReceipt**
   - User ID, message ID
   - Read timestamp

5. **Reaction**
   - User ID, message ID
   - Emoji/reaction type
   - Timestamp

6. **Agent**
   - Availability status
   - Current threads
   - Department/specialty
   - Max concurrent chats

### Entity Relationships

```
┌─────────────┐
│  Customer   │
└──────┬──────┘
       │ creates
       ▼
┌─────────────┐      contains      ┌──────────────┐
│ ChatThread  │◄───────────────────│   Message    │
└──────┬──────┘                    └──────┬───────┘
       │                                  │
       │ assigned to                      │ has
       │                                  │
       ▼                                  ▼
┌─────────────┐                    ┌──────────────┐
│    Agent    │                    │ ReadReceipt  │
└─────────────┘                    └──────────────┘
                                         │
                                         │ has
                                         ▼
                                   ┌──────────────┐
                                   │   Reaction   │
                                   └──────────────┘
```

---

## Chat Thread State Machine

### Thread States

```
                customer creates
┌──────────┐   ─────────────────►  ┌──────────┐
│   NONE   │                       │   OPEN   │
└──────────┘                       └─────┬────┘
                                         │
                                    agent joins
                                         │
                                         ▼
                                   ┌──────────┐
                            ┌──────│IN_PROGRESS│
                            │      └─────┬────┘
                            │            │
                      waiting for        │ resolved
                       customer          │
                            │            ▼
                            │      ┌──────────┐
                            └─────►│ RESOLVED │
                                   └─────┬────┘
                                         │
                                    auto-close or
                                    manual close
                                         │
                                         ▼
                                   ┌──────────┐
                            ┌──────│  CLOSED  │
                            │      └──────────┘
                            │
                      customer replies
                            │
                            └────► OPEN (reopened)
```

### State Descriptions

| State | Description | Transitions |
|-------|-------------|-------------|
| OPEN | New thread, no agent assigned | → IN_PROGRESS (agent joins) |
| IN_PROGRESS | Agent(s) actively helping | → WAITING, RESOLVED |
| WAITING | Waiting for customer response | → IN_PROGRESS, RESOLVED |
| RESOLVED | Issue resolved, waiting to close | → CLOSED, IN_PROGRESS |
| CLOSED | Thread archived | → OPEN (if reopened) |

---

## Class Design

### 1. User Hierarchy

```java
/**
 * Abstract user base class
 */
public abstract class User {
    private final String id;
    private final String name;
    private final String email;
    
    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }
    
    // Getters
}

/**
 * Customer seeking support
 */
public class Customer extends User {
    private final List<ChatThread> threads;
    
    public Customer(String id, String name, String email) {
        super(id, name, email);
        this.threads = new ArrayList<>();
    }
    
    public void addThread(ChatThread thread) {
        threads.add(thread);
    }
    
    public List<ChatThread> getThreads() {
        return Collections.unmodifiableList(threads);
    }
}

/**
 * Support agent
 */
public class Agent extends User {
    private AgentStatus status;
    private final Set<String> activeThreadIds;
    private final int maxConcurrentChats;
    private final String department;
    
    public enum AgentStatus {
        ONLINE, AWAY, BUSY, OFFLINE
    }
    
    public Agent(String id, String name, String email, String department, int maxChats) {
        super(id, name, email);
        this.status = AgentStatus.OFFLINE;
        this.activeThreadIds = ConcurrentHashMap.newKeySet();
        this.maxConcurrentChats = maxChats;
        this.department = department;
    }
    
    public boolean isAvailable() {
        return status == AgentStatus.ONLINE && 
               activeThreadIds.size() < maxConcurrentChats;
    }
    
    public boolean joinThread(String threadId) {
        if (activeThreadIds.size() >= maxConcurrentChats) {
            return false;
        }
        return activeThreadIds.add(threadId);
    }
    
    public void leaveThread(String threadId) {
        activeThreadIds.remove(threadId);
    }
    
    // Getters and setters
}
```

### 2. ChatThread

```java
/**
 * Chat conversation thread
 */
public class ChatThread {
    private final String id;
    private final String customerId;
    private final Set<String> agentIds;
    private final List<Message> messages;
    private String subject;
    private ThreadStatus status;
    private final long createdAt;
    private long lastActivityAt;
    
    public enum ThreadStatus {
        OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED
    }
    
    public ChatThread(String id, String customerId, String subject) {
        this.id = id;
        this.customerId = customerId;
        this.agentIds = ConcurrentHashMap.newKeySet();
        this.messages = new CopyOnWriteArrayList<>();
        this.subject = subject;
        this.status = ThreadStatus.OPEN;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = createdAt;
    }
    
    public synchronized void addMessage(Message message) {
        messages.add(message);
        lastActivityAt = System.currentTimeMillis();
        
        // Auto-transition to IN_PROGRESS if was OPEN
        if (status == ThreadStatus.OPEN && !message.getSenderId().equals(customerId)) {
            status = ThreadStatus.IN_PROGRESS;
        }
    }
    
    public boolean addAgent(String agentId) {
        return agentIds.add(agentId);
    }
    
    public void removeAgent(String agentId) {
        agentIds.remove(agentId);
    }
    
    public void updateStatus(ThreadStatus newStatus) {
        this.status = newStatus;
        this.lastActivityAt = System.currentTimeMillis();
    }
    
    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }
    
    public List<Message> getMessagesSince(long timestamp) {
        return messages.stream()
            .filter(m -> m.getTimestamp() > timestamp)
            .collect(Collectors.toList());
    }
    
    // Getters
}
```

### 3. Message

```java
/**
 * Chat message
 */
public class Message {
    private final String id;
    private final String threadId;
    private final String senderId;
    private final MessageType type;
    private String content;
    private final String mediaUrl; // For IMAGE, FILE types
    private final long timestamp;
    private final Map<String, ReadReceipt> readReceipts;
    private final Map<String, Reaction> reactions;
    private boolean isEdited;
    private boolean isDeleted;
    
    public enum MessageType {
        TEXT, IMAGE, FILE, SYSTEM
    }
    
    public Message(String id, String threadId, String senderId, 
                   MessageType type, String content) {
        this(id, threadId, senderId, type, content, null);
    }
    
    public Message(String id, String threadId, String senderId,
                   MessageType type, String content, String mediaUrl) {
        this.id = id;
        this.threadId = threadId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.timestamp = System.currentTimeMillis();
        this.readReceipts = new ConcurrentHashMap<>();
        this.reactions = new ConcurrentHashMap<>();
        this.isEdited = false;
        this.isDeleted = false;
    }
    
    public void markAsRead(String userId) {
        readReceipts.put(userId, new ReadReceipt(userId, id, System.currentTimeMillis()));
    }
    
    public void addReaction(String userId, String emoji) {
        reactions.put(userId, new Reaction(userId, id, emoji, System.currentTimeMillis()));
    }
    
    public void removeReaction(String userId) {
        reactions.remove(userId);
    }
    
    public void edit(String newContent) {
        this.content = newContent;
        this.isEdited = true;
    }
    
    public void delete() {
        this.isDeleted = true;
        this.content = "[Message deleted]";
    }
    
    public boolean isReadBy(String userId) {
        return readReceipts.containsKey(userId);
    }
    
    // Getters
}
```

### 4. ReadReceipt & Reaction

```java
/**
 * Read receipt for message
 */
public class ReadReceipt {
    private final String userId;
    private final String messageId;
    private final long readAt;
    
    public ReadReceipt(String userId, String messageId, long readAt) {
        this.userId = userId;
        this.messageId = messageId;
        this.readAt = readAt;
    }
    
    // Getters
}

/**
 * Reaction to message
 */
public class Reaction {
    private final String userId;
    private final String messageId;
    private final String emoji;
    private final long timestamp;
    
    public Reaction(String userId, String messageId, String emoji, long timestamp) {
        this.userId = userId;
        this.messageId = messageId;
        this.emoji = emoji;
        this.timestamp = timestamp;
    }
    
    // Getters
}
```

### 5. Agent Assignment Strategy

```java
/**
 * Strategy for assigning threads to agents
 */
public interface AgentAssignmentStrategy {
    Agent assignAgent(ChatThread thread, List<Agent> availableAgents);
}

/**
 * Round-robin assignment
 */
public class RoundRobinAssignment implements AgentAssignmentStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public Agent assignAgent(ChatThread thread, List<Agent> availableAgents) {
        if (availableAgents.isEmpty()) {
            return null;
        }
        
        int index = counter.getAndIncrement() % availableAgents.size();
        return availableAgents.get(index);
    }
}

/**
 * Least busy agent assignment
 */
public class LeastBusyAssignment implements AgentAssignmentStrategy {
    @Override
    public Agent assignAgent(ChatThread thread, List<Agent> availableAgents) {
        return availableAgents.stream()
            .min(Comparator.comparingInt(a -> a.getActiveThreadIds().size()))
            .orElse(null);
    }
}

/**
 * Department-based assignment
 */
public class DepartmentBasedAssignment implements AgentAssignmentStrategy {
    @Override
    public Agent assignAgent(ChatThread thread, List<Agent> availableAgents) {
        // Filter by department if thread has category
        String category = thread.getSubject(); // Simplified
        
        List<Agent> departmentAgents = availableAgents.stream()
            .filter(a -> category == null || a.getDepartment().contains(category))
            .collect(Collectors.toList());
        
        if (departmentAgents.isEmpty()) {
            departmentAgents = availableAgents;
        }
        
        // Return least busy from filtered list
        return departmentAgents.stream()
            .min(Comparator.comparingInt(a -> a.getActiveThreadIds().size()))
            .orElse(null);
    }
}
```

---

## Message Delivery System

### Message Queue

```java
/**
 * Message delivery queue for offline users
 */
public class MessageQueue {
    private final Map<String, Queue<Message>> userQueues;
    
    public MessageQueue() {
        this.userQueues = new ConcurrentHashMap<>();
    }
    
    public void enqueue(String userId, Message message) {
        userQueues.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>())
                  .offer(message);
    }
    
    public List<Message> dequeueAll(String userId) {
        Queue<Message> queue = userQueues.get(userId);
        if (queue == null) {
            return Collections.emptyList();
        }
        
        List<Message> messages = new ArrayList<>();
        Message msg;
        while ((msg = queue.poll()) != null) {
            messages.add(msg);
        }
        
        return messages;
    }
}
```

### Real-Time Notification

```java
/**
 * Real-time notification service (WebSocket abstraction)
 */
public interface NotificationService {
    void notifyNewMessage(String userId, Message message);
    void notifyThreadAssigned(String agentId, ChatThread thread);
    void notifyTyping(String threadId, String userId);
    void notifyReadReceipt(String threadId, ReadReceipt receipt);
}

/**
 * WebSocket-based notification (mock)
 */
public class WebSocketNotificationService implements NotificationService {
    private final Map<String, WebSocketSession> activeSessions;
    
    public WebSocketNotificationService() {
        this.activeSessions = new ConcurrentHashMap<>();
    }
    
    @Override
    public void notifyNewMessage(String userId, Message message) {
        WebSocketSession session = activeSessions.get(userId);
        if (session != null && session.isOpen()) {
            session.send(new MessageEvent(message));
        }
        // Otherwise, message queued for later delivery
    }
    
    // Other methods...
}
```

---

## Complete Implementation

### CustomerSupportChatSystem - Main Service

```java
/**
 * Main customer support chat service
 */
public class CustomerSupportChatSystem {
    private final Map<String, Customer> customers;
    private final Map<String, Agent> agents;
    private final Map<String, ChatThread> threads;
    private final AgentAssignmentStrategy assignmentStrategy;
    private final MessageQueue messageQueue;
    private final AtomicInteger threadIdCounter;
    private final AtomicInteger messageIdCounter;
    
    public CustomerSupportChatSystem(AgentAssignmentStrategy strategy) {
        this.customers = new ConcurrentHashMap<>();
        this.agents = new ConcurrentHashMap<>();
        this.threads = new ConcurrentHashMap<>();
        this.assignmentStrategy = strategy;
        this.messageQueue = new MessageQueue();
        this.threadIdCounter = new AtomicInteger(1);
        this.messageIdCounter = new AtomicInteger(1);
    }
    
    // User Management
    public void registerCustomer(Customer customer) {
        customers.put(customer.getId(), customer);
    }
    
    public void registerAgent(Agent agent) {
        agents.put(agent.getId(), agent);
    }
    
    // Thread Creation
    public ChatThread createThread(String customerId, String subject) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Customer not found");
        }
        
        String threadId = "THREAD-" + threadIdCounter.getAndIncrement();
        ChatThread thread = new ChatThread(threadId, customerId, subject);
        threads.put(threadId, thread);
        customer.addThread(thread);
        
        // Auto-assign agent
        Agent agent = assignAgentToThread(thread);
        if (agent != null) {
            thread.addAgent(agent.getId());
            agent.joinThread(threadId);
            thread.updateStatus(ChatThread.ThreadStatus.IN_PROGRESS);
        }
        
        return thread;
    }
    
    // Agent Assignment
    private Agent assignAgentToThread(ChatThread thread) {
        List<Agent> availableAgents = agents.values().stream()
            .filter(Agent::isAvailable)
            .collect(Collectors.toList());
        
        return assignmentStrategy.assignAgent(thread, availableAgents);
    }
    
    // Messaging
    public Message sendMessage(String threadId, String senderId, 
                              String content, Message.MessageType type) {
        ChatThread thread = threads.get(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found");
        }
        
        String messageId = "MSG-" + messageIdCounter.getAndIncrement();
        Message message = new Message(messageId, threadId, senderId, type, content);
        
        thread.addMessage(message);
        
        // Notify all participants
        notifyParticipants(thread, message, senderId);
        
        return message;
    }
    
    private void notifyParticipants(ChatThread thread, Message message, String excludeUser) {
        // Notify customer
        if (!thread.getCustomerId().equals(excludeUser)) {
            System.out.println("[Notify] Customer " + thread.getCustomerId() + 
                             " - New message in thread " + thread.getId());
        }
        
        // Notify all agents
        for (String agentId : thread.getAgentIds()) {
            if (!agentId.equals(excludeUser)) {
                System.out.println("[Notify] Agent " + agentId + 
                                 " - New message in thread " + thread.getId());
            }
        }
    }
    
    // Read Receipts
    public void markAsRead(String threadId, String messageId, String userId) {
        ChatThread thread = threads.get(threadId);
        if (thread == null) {
            return;
        }
        
        Message message = thread.getMessages().stream()
            .filter(m -> m.getId().equals(messageId))
            .findFirst()
            .orElse(null);
        
        if (message != null) {
            message.markAsRead(userId);
        }
    }
    
    // Reactions
    public void addReaction(String threadId, String messageId, 
                           String userId, String emoji) {
        ChatThread thread = threads.get(threadId);
        if (thread == null) {
            return;
        }
        
        Message message = thread.getMessages().stream()
            .filter(m -> m.getId().equals(messageId))
            .findFirst()
            .orElse(null);
        
        if (message != null) {
            message.addReaction(userId, emoji);
        }
    }
    
    // Thread Management
    public void resolveThread(String threadId, String agentId) {
        ChatThread thread = threads.get(threadId);
        if (thread != null && thread.getAgentIds().contains(agentId)) {
            thread.updateStatus(ChatThread.ThreadStatus.RESOLVED);
        }
    }
    
    public void closeThread(String threadId) {
        ChatThread thread = threads.get(threadId);
        if (thread != null) {
            thread.updateStatus(ChatThread.ThreadStatus.CLOSED);
            
            // Remove from agents' active threads
            for (String agentId : thread.getAgentIds()) {
                Agent agent = agents.get(agentId);
                if (agent != null) {
                    agent.leaveThread(threadId);
                }
            }
        }
    }
    
    public void reopenThread(String threadId, String customerId) {
        ChatThread thread = threads.get(threadId);
        if (thread != null && thread.getCustomerId().equals(customerId)) {
            thread.updateStatus(ChatThread.ThreadStatus.OPEN);
            
            // Re-assign agent
            Agent agent = assignAgentToThread(thread);
            if (agent != null) {
                thread.addAgent(agent.getId());
                agent.joinThread(threadId);
                thread.updateStatus(ChatThread.ThreadStatus.IN_PROGRESS);
            }
        }
    }
    
    // Search
    public List<ChatThread> getThreadsByCustomer(String customerId) {
        return threads.values().stream()
            .filter(t -> t.getCustomerId().equals(customerId))
            .sorted(Comparator.comparingLong(ChatThread::getLastActivityAt).reversed())
            .collect(Collectors.toList());
    }
    
    public List<ChatThread> getThreadsByAgent(String agentId) {
        return threads.values().stream()
            .filter(t -> t.getAgentIds().contains(agentId))
            .filter(t -> t.getStatus() != ChatThread.ThreadStatus.CLOSED)
            .sorted(Comparator.comparingLong(ChatThread::getLastActivityAt).reversed())
            .collect(Collectors.toList());
    }
    
    public ChatThread getThread(String threadId) {
        return threads.get(threadId);
    }
}
```

---

## Extensibility

### 1. Typing Indicators

```java
public class TypingIndicatorManager {
    private final Map<String, Map<String, Long>> threadTyping;
    // Thread ID → (User ID → Last typing timestamp)
    
    private static final long TYPING_TIMEOUT_MS = 3000; // 3 seconds
    
    public void userTyping(String threadId, String userId) {
        threadTyping.computeIfAbsent(threadId, k -> new ConcurrentHashMap<>())
                    .put(userId, System.currentTimeMillis());
    }
    
    public List<String> getTypingUsers(String threadId) {
        Map<String, Long> typing = threadTyping.get(threadId);
        if (typing == null) {
            return Collections.emptyList();
        }
        
        long now = System.currentTimeMillis();
        return typing.entrySet().stream()
            .filter(e -> now - e.getValue() < TYPING_TIMEOUT_MS)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}
```

### 2. Auto-Close Inactive Threads

```java
public class ThreadAutoCloseManager {
    private final CustomerSupportChatSystem chatSystem;
    private final ScheduledExecutorService scheduler;
    private final long inactivityThresholdMs;
    
    public ThreadAutoCloseManager(CustomerSupportChatSystem system, long thresholdMs) {
        this.chatSystem = system;
        this.inactivityThresholdMs = thresholdMs;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    public void start() {
        scheduler.scheduleAtFixedRate(this::closeInactiveThreads,
            1, 1, TimeUnit.HOURS);
    }
    
    private void closeInactiveThreads() {
        long now = System.currentTimeMillis();
        
        chatSystem.getAllThreads().stream()
            .filter(t -> t.getStatus() == ChatThread.ThreadStatus.RESOLVED)
            .filter(t -> now - t.getLastActivityAt() > inactivityThresholdMs)
            .forEach(t -> chatSystem.closeThread(t.getId()));
    }
}
```

### 3. Message Templates

```java
public class MessageTemplate {
    private final String id;
    private final String name;
    private final String content;
    private final List<String> variables; // {name}, {order_id}, etc.
    
    public String fillTemplate(Map<String, String> values) {
        String result = content;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
```

### 4. Canned Responses

```java
public class CannedResponse {
    private final String id;
    private final String shortcut;
    private final String content;
    private final String category;
    
    public CannedResponse(String id, String shortcut, String content, String category) {
        this.id = id;
        this.shortcut = shortcut;
        this.content = content;
        this.category = category;
    }
    
    // When agent types "/hello", expand to full response
}

public class CannedResponseManager {
    private final Map<String, CannedResponse> responses;
    
    public String expandShortcut(String text) {
        if (text.startsWith("/")) {
            CannedResponse response = responses.get(text);
            if (response != null) {
                return response.getContent();
            }
        }
        return text;
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- How many agents per thread?
- Offline message delivery?
- Message edit/delete support?
- Media file size limits?
- Agent routing strategy?

**2. Identify Core Entities (5 minutes)**
- User types (Customer, Agent)
- ChatThread
- Message (with types)
- ReadReceipt, Reaction

**3. Design Thread Lifecycle (10 minutes)**
- Draw state machine
- Thread status transitions
- Agent assignment flow

**4. Implement Core Logic (20 minutes)**
- Thread creation and assignment
- Message sending and delivery
- Read receipts
- Thread state management

**5. Discuss Extensions (5 minutes)**
- Typing indicators
- Auto-close threads
- Templates and canned responses
- Analytics

### Common Interview Questions

**Q1: "How do you ensure message ordering?"**

**A:** Use timestamps and sequence numbers:
```java
class Message {
    private final long timestamp;
    private final long sequenceNumber; // Per-thread monotonic counter
}

// Sort by sequence number, timestamp as tie-breaker
messages.sort(Comparator
    .comparingLong(Message::getSequenceNumber)
    .thenComparingLong(Message::getTimestamp));
```

**Q2: "How to handle agent going offline mid-conversation?"**

**A:** Reassign thread to another agent:
```java
public void handleAgentDisconnect(String agentId) {
    // Find all active threads for this agent
    List<ChatThread> activeThreads = getThreadsByAgent(agentId);
    
    for (ChatThread thread : activeThreads) {
        // Remove agent from thread
        thread.removeAgent(agentId);
        agents.get(agentId).leaveThread(thread.getId());
        
        // Reassign if no other agents
        if (thread.getAgentIds().isEmpty()) {
            Agent newAgent = assignAgentToThread(thread);
            if (newAgent != null) {
                thread.addAgent(newAgent.getId());
                newAgent.joinThread(thread.getId());
            } else {
                // No agents available, mark as OPEN
                thread.updateStatus(ThreadStatus.OPEN);
            }
        }
    }
}
```

**Q3: "How to implement read receipts efficiently?"**

**A:** Track per message, update in batch:
```java
public void markThreadAsRead(String threadId, String userId, long upToTimestamp) {
    ChatThread thread = threads.get(threadId);
    
    thread.getMessages().stream()
        .filter(m -> m.getTimestamp() <= upToTimestamp)
        .filter(m -> !m.isReadBy(userId))
        .forEach(m -> m.markAsRead(userId));
}
```

**Q4: "How to prevent agent overload?"**

**A:** Check capacity before assignment:
```java
class Agent {
    private final int maxConcurrentChats = 5;
    
    public boolean isAvailable() {
        return status == AgentStatus.ONLINE && 
               activeThreadIds.size() < maxConcurrentChats;
    }
}
```

**Q5: "How to handle message delivery when user is offline?"**

**A:** Queue messages for later delivery:
```java
public void deliverMessage(String userId, Message message) {
    if (isUserOnline(userId)) {
        sendViaWebSocket(userId, message);
    } else {
        messageQueue.enqueue(userId, message);
    }
}

public void onUserComesOnline(String userId) {
    List<Message> pending = messageQueue.dequeueAll(userId);
    for (Message msg : pending) {
        sendViaWebSocket(userId, msg);
    }
}
```

### Design Patterns Used

1. **Observer Pattern:** Real-time message notifications
2. **Strategy Pattern:** Agent assignment algorithms
3. **State Pattern:** Thread status management
4. **Factory Pattern:** Creating messages and threads
5. **Command Pattern:** Message operations
6. **Queue Pattern:** Offline message delivery

### Expected Level Performance

**Junior Engineer:**
- Basic entities (Customer, Agent, Message)
- Simple thread creation
- Send/receive messages
- Basic read receipts

**Mid-Level Engineer:**
- Complete state machine
- Agent assignment strategies
- Read receipts and reactions
- Thread status management
- Multiple agents per thread
- Thread-safe operations

**Senior Engineer:**
- Production-ready implementation
- Real-time notifications (WebSocket)
- Typing indicators
- Auto-close inactive threads
- Message queue for offline
- Analytics and metrics
- Performance optimization

### Key Trade-offs

**1. Agent Assignment**
- **Round-robin:** Fair distribution, ignores workload
- **Least busy:** Better load balancing, more complex
- **Department-based:** Specialized support, may have bottlenecks

**2. Message Storage**
- **In-memory:** Fast, but data loss on restart
- **Database:** Persistent, adds latency
- **Hybrid:** Recent in-memory, old in DB

**3. Real-Time Delivery**
- **WebSocket:** True real-time, complex infrastructure
- **Long polling:** Simpler, higher latency
- **Server-sent events:** One-way, simpler than WebSocket

**4. Read Receipts**
- **Per message:** Accurate, more network calls
- **Batch updates:** Efficient, less precise
- **Lazy loading:** Only when requested

---

## Summary

This Customer Support Chat System demonstrates:

1. **Multi-Actor System:** Customers and multiple agents
2. **Real-Time Messaging:** Instant message delivery
3. **Thread Management:** Complete lifecycle with state machine
4. **Agent Assignment:** Multiple strategies (Round-robin, Least-busy, Department)
5. **Rich Features:** Read receipts, reactions, typing indicators
6. **Scalable Design:** Thread-safe, message queuing, concurrent operations

**Key Takeaways:**
- **State machine** manages thread lifecycle
- **Strategy pattern** for flexible agent assignment
- **Message queue** handles offline delivery
- **CopyOnWriteArrayList** for thread-safe message lists
- **ConcurrentHashMap** for read receipts and reactions
- **WebSocket** for real-time communication

**Related Systems:**
- Messaging apps (WhatsApp, Slack)
- Customer support (Zendesk, Freshdesk)
- Live chat widgets
- Helpdesk ticketing systems

---

*Last Updated: January 5, 2026*
*Difficulty Level: Medium*
*Key Focus: Real-Time Messaging, State Machine, Multi-Actor*
*Companies: Intercom, Zendesk, LiveChat, Drift*
*Interview Success Rate: High with proper state machine and messaging design*
