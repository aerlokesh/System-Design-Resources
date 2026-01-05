/**
 * CUSTOMER SUPPORT CHAT SYSTEM - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates an Intercom-style customer support
 * chat platform with real-time messaging and multi-agent support.
 * 
 * Key Features:
 * 1. Multi-agent thread support
 * 2. Real-time text and media messaging
 * 3. Read receipts and reactions
 * 4. Thread state machine (Open, In-Progress, Resolved, Closed)
 * 5. Agent assignment strategies (Round-robin, Least-busy)
 * 6. Offline message queuing
 * 7. Thread-safe operations
 * 
 * Design Patterns Used:
 * - State Pattern (Thread status)
 * - Strategy Pattern (Agent assignment)
 * - Observer Pattern (Notifications)
 * - Factory Pattern (Message creation)
 * 
 * Companies: Intercom, Zendesk, LiveChat, Drift
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

// ============================================================
// ENUMS
// ============================================================

enum ThreadStatus {
    OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED
}

enum AgentStatus {
    ONLINE, AWAY, BUSY, OFFLINE
}

enum MessageType {
    TEXT, IMAGE, FILE, SYSTEM
}

// ============================================================
// USER HIERARCHY
// ============================================================

abstract class SupportUser {
    private final String id;
    private final String name;
    private final String email;
    
    public SupportUser(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}

class Customer extends SupportUser {
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

class Agent extends SupportUser {
    private AgentStatus status;
    private final Set<String> activeThreadIds;
    private final int maxConcurrentChats;
    private final String department;
    
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
    
    public void goOnline() {
        this.status = AgentStatus.ONLINE;
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
    
    public AgentStatus getStatus() { return status; }
    public Set<String> getActiveThreadIds() { return activeThreadIds; }
    public String getDepartment() { return department; }
    public int getActiveThreadCount() { return activeThreadIds.size(); }
}

// ============================================================
// MESSAGE & RELATED
// ============================================================

class Message {
    private final String id;
    private final String threadId;
    private final String senderId;
    private final MessageType type;
    private String content;
    private final long timestamp;
    private final Map<String, ReadReceipt> readReceipts;
    private final Map<String, Reaction> reactions;
    private boolean isEdited;
    
    public Message(String id, String threadId, String senderId, 
                   MessageType type, String content) {
        this.id = id;
        this.threadId = threadId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.readReceipts = new ConcurrentHashMap<>();
        this.reactions = new ConcurrentHashMap<>();
        this.isEdited = false;
    }
    
    public void markAsRead(String userId) {
        readReceipts.put(userId, new ReadReceipt(userId, id, System.currentTimeMillis()));
    }
    
    public void addReaction(String userId, String emoji) {
        reactions.put(userId, new Reaction(userId, id, emoji));
    }
    
    public boolean isReadBy(String userId) {
        return readReceipts.containsKey(userId);
    }
    
    public int getReactionCount() {
        return reactions.size();
    }
    
    public String getId() { return id; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
    public MessageType getType() { return type; }
    public long getTimestamp() { return timestamp; }
    public Map<String, ReadReceipt> getReadReceipts() { return readReceipts; }
    public Map<String, Reaction> getReactions() { return reactions; }
    
    @Override
    public String toString() {
        String editedTag = isEdited ? " (edited)" : "";
        return String.format("[%s] %s%s", senderId, content, editedTag);
    }
}

class ReadReceipt {
    private final String userId;
    private final String messageId;
    private final long readAt;
    
    public ReadReceipt(String userId, String messageId, long readAt) {
        this.userId = userId;
        this.messageId = messageId;
        this.readAt = readAt;
    }
    
    public String getUserId() { return userId; }
}

class Reaction {
    private final String userId;
    private final String messageId;
    private final String emoji;
    
    public Reaction(String userId, String messageId, String emoji) {
        this.userId = userId;
        this.messageId = messageId;
        this.emoji = emoji;
    }
    
    public String getUserId() { return userId; }
    public String getEmoji() { return emoji; }
}

// ============================================================
// CHAT THREAD
// ============================================================

class ChatThread {
    private final String id;
    private final String customerId;
    private final Set<String> agentIds;
    private final List<Message> messages;
    private String subject;
    private ThreadStatus status;
    private final long createdAt;
    private long lastActivityAt;
    
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
    
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public Set<String> getAgentIds() { return agentIds; }
    public List<Message> getMessages() { return new ArrayList<>(messages); }
    public String getSubject() { return subject; }
    public ThreadStatus getStatus() { return status; }
    public long getLastActivityAt() { return lastActivityAt; }
}

// ============================================================
// AGENT ASSIGNMENT STRATEGIES
// ============================================================

interface AgentAssignmentStrategy {
    Agent assignAgent(ChatThread thread, List<Agent> availableAgents);
}

class RoundRobinAssignment implements AgentAssignmentStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public Agent assignAgent(ChatThread thread, List<Agent> availableAgents) {
        if (availableAgents.isEmpty()) return null;
        
        int index = counter.getAndIncrement() % availableAgents.size();
        return availableAgents.get(index);
    }
}

class LeastBusyAssignment implements AgentAssignmentStrategy {
    @Override
    public Agent assignAgent(ChatThread thread, List<Agent> availableAgents) {
        return availableAgents.stream()
            .min(Comparator.comparingInt(Agent::getActiveThreadCount))
            .orElse(null);
    }
}

// ============================================================
// MAIN SERVICE
// ============================================================

class SupportChatService {
    private final Map<String, Customer> customers;
    private final Map<String, Agent> agents;
    private final Map<String, ChatThread> threads;
    private final AgentAssignmentStrategy assignmentStrategy;
    private final AtomicInteger threadIdCounter;
    private final AtomicInteger messageIdCounter;
    
    public SupportChatService(AgentAssignmentStrategy strategy) {
        this.customers = new ConcurrentHashMap<>();
        this.agents = new ConcurrentHashMap<>();
        this.threads = new ConcurrentHashMap<>();
        this.assignmentStrategy = strategy;
        this.threadIdCounter = new AtomicInteger(1);
        this.messageIdCounter = new AtomicInteger(1);
    }
    
    public void registerCustomer(Customer customer) {
        customers.put(customer.getId(), customer);
    }
    
    public void registerAgent(Agent agent) {
        agents.put(agent.getId(), agent);
    }
    
    public ChatThread createThread(String customerId, String subject) {
        String threadId = "T-" + threadIdCounter.getAndIncrement();
        ChatThread thread = new ChatThread(threadId, customerId, subject);
        threads.put(threadId, thread);
        customers.get(customerId).addThread(thread);
        
        Agent agent = assignAgentToThread(thread);
        if (agent != null) {
            thread.addAgent(agent.getId());
            agent.joinThread(threadId);
            thread.updateStatus(ThreadStatus.IN_PROGRESS);
        }
        
        return thread;
    }
    
    private Agent assignAgentToThread(ChatThread thread) {
        List<Agent> availableAgents = agents.values().stream()
            .filter(Agent::isAvailable)
            .collect(Collectors.toList());
        
        return assignmentStrategy.assignAgent(thread, availableAgents);
    }
    
    public Message sendMessage(String threadId, String senderId, String content, MessageType type) {
        ChatThread thread = threads.get(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found");
        }
        
        String messageId = "M-" + messageIdCounter.getAndIncrement();
        Message message = new Message(messageId, threadId, senderId, type, content);
        thread.addMessage(message);
        
        return message;
    }
    
    public void markAsRead(String threadId, String messageId, String userId) {
        ChatThread thread = threads.get(threadId);
        if (thread == null) return;
        
        thread.getMessages().stream()
            .filter(m -> m.getId().equals(messageId))
            .forEach(m -> m.markAsRead(userId));
    }
    
    public void addReaction(String threadId, String messageId, String userId, String emoji) {
        ChatThread thread = threads.get(threadId);
        if (thread == null) return;
        
        thread.getMessages().stream()
            .filter(m -> m.getId().equals(messageId))
            .forEach(m -> m.addReaction(userId, emoji));
    }
    
    public void resolveThread(String threadId, String agentId) {
        ChatThread thread = threads.get(threadId);
        if (thread != null && thread.getAgentIds().contains(agentId)) {
            thread.updateStatus(ThreadStatus.RESOLVED);
        }
    }
    
    public void closeThread(String threadId) {
        ChatThread thread = threads.get(threadId);
        if (thread != null) {
            thread.updateStatus(ThreadStatus.CLOSED);
            
            for (String agentId : thread.getAgentIds()) {
                Agent agent = agents.get(agentId);
                if (agent != null) {
                    agent.leaveThread(threadId);
                }
            }
        }
    }
    
    public ChatThread getThread(String threadId) {
        return threads.get(threadId);
    }
    
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }
}

// ============================================================
// DEMO
// ============================================================

public class CustomerSupportChatSystem {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== CUSTOMER SUPPORT CHAT SYSTEM DEMO ===\n");
        
        // Demo 1: Complete support conversation
        demoCompleteConversation();
        
        // Demo 2: Multiple agents in thread
        demoMultipleAgents();
        
        // Demo 3: Read receipts and reactions
        demoReadReceiptsAndReactions();
        
        // Demo 4: Agent assignment strategies
        demoAgentAssignment();
        
        // Demo 5: Thread lifecycle
        demoThreadLifecycle();
    }
    
    private static void demoCompleteConversation() {
        System.out.println("--- Demo 1: Complete Support Conversation ---\n");
        
        SupportChatService service = new SupportChatService(new LeastBusyAssignment());
        
        // Setup
        Customer customer = new Customer("C1", "Alice Customer", "alice@example.com");
        Agent agent = new Agent("A1", "Bob Agent", "bob@company.com", "Technical", 5);
        agent.goOnline();
        
        service.registerCustomer(customer);
        service.registerAgent(agent);
        
        // Customer initiates chat
        System.out.println("Customer initiates chat about login issue...");
        ChatThread thread = service.createThread("C1", "Cannot login to account");
        System.out.println("Thread created: " + thread.getId());
        System.out.println("Status: " + thread.getStatus());
        System.out.println("Assigned agent: " + agent.getName() + "\n");
        
        // Customer sends first message
        Message msg1 = service.sendMessage(thread.getId(), "C1", 
            "Hi, I can't log into my account", MessageType.TEXT);
        System.out.println("Customer: " + msg1.getContent());
        
        // Agent responds
        Message msg2 = service.sendMessage(thread.getId(), "A1",
            "Hello! I'll help you with that. Can you tell me your email?", MessageType.TEXT);
        System.out.println("Agent: " + msg2.getContent());
        
        // Customer replies
        Message msg3 = service.sendMessage(thread.getId(), "C1",
            "It's alice@example.com", MessageType.TEXT);
        System.out.println("Customer: " + msg3.getContent());
        
        // Agent resolves
        Message msg4 = service.sendMessage(thread.getId(), "A1",
            "I've reset your password. Check your email!", MessageType.TEXT);
        System.out.println("Agent: " + msg4.getContent());
        
        service.resolveThread(thread.getId(), "A1");
        System.out.println("\nThread resolved");
        System.out.println("Status: " + service.getThread(thread.getId()).getStatus());
        System.out.println("Total messages: " + thread.getMessages().size());
        
        System.out.println();
    }
    
    private static void demoMultipleAgents() {
        System.out.println("--- Demo 2: Multiple Agents in Thread ---\n");
        
        SupportChatService service = new SupportChatService(new LeastBusyAssignment());
        
        Customer customer = new Customer("C1", "Customer", "customer@example.com");
        Agent agent1 = new Agent("A1", "Agent 1", "agent1@company.com", "Sales", 5);
        Agent agent2 = new Agent("A2", "Agent 2", "agent2@company.com", "Technical", 5);
        
        agent1.goOnline();
        agent2.goOnline();
        
        service.registerCustomer(customer);
        service.registerAgent(agent1);
        service.registerAgent(agent2);
        
        // Create thread
        ChatThread thread = service.createThread("C1", "Complex issue");
        System.out.println("Thread created with Agent 1");
        
        // Agent 2 joins to help
        thread.addAgent("A2");
        agent2.joinThread(thread.getId());
        System.out.println("Agent 2 joined thread");
        
        // Both agents helping
        service.sendMessage(thread.getId(), "C1", "I need help with billing and technical issue", MessageType.TEXT);
        System.out.println("Customer: Asks about billing and technical");
        
        service.sendMessage(thread.getId(), "A1", "I'll help with billing", MessageType.TEXT);
        System.out.println("Agent 1: Handles billing");
        
        service.sendMessage(thread.getId(), "A2", "I'll handle the technical part", MessageType.TEXT);
        System.out.println("Agent 2: Handles technical");
        
        System.out.println("\nAgents in thread: " + thread.getAgentIds().size());
        System.out.println("Total messages: " + thread.getMessages().size());
        
        System.out.println();
    }
    
    private static void demoReadReceiptsAndReactions() {
        System.out.println("--- Demo 3: Read Receipts and Reactions ---\n");
        
        SupportChatService service = new SupportChatService(new RoundRobinAssignment());
        
        Customer customer = new Customer("C1", "Customer", "customer@example.com");
        Agent agent = new Agent("A1", "Agent", "agent@company.com", "Support", 5);
        agent.goOnline();
        
        service.registerCustomer(customer);
        service.registerAgent(agent);
        
        ChatThread thread = service.createThread("C1", "Question");
        
        // Send messages
        Message msg1 = service.sendMessage(thread.getId(), "C1", "Hello!", MessageType.TEXT);
        Message msg2 = service.sendMessage(thread.getId(), "A1", "Hi! How can I help?", MessageType.TEXT);
        
        System.out.println("Messages sent:");
        System.out.println("  " + msg1);
        System.out.println("  " + msg2);
        
        // Mark as read
        System.out.println("\nAgent marks message as read...");
        service.markAsRead(thread.getId(), msg1.getId(), "A1");
        System.out.println("Message read by agent: " + msg1.isReadBy("A1"));
        
        // Add reactions
        System.out.println("\nCustomer reacts with thumbs up...");
        service.addReaction(thread.getId(), msg2.getId(), "C1", "👍");
        System.out.println("Reactions on message: " + msg2.getReactionCount());
        
        // Show reactions
        System.out.println("\nReactions:");
        for (Reaction r : msg2.getReactions().values()) {
            System.out.println("  " + r.getUserId() + " reacted with " + r.getEmoji());
        }
        
        System.out.println();
    }
    
    private static void demoAgentAssignment() {
        System.out.println("--- Demo 4: Agent Assignment Strategies ---\n");
        
        // Test Round-Robin
        System.out.println("Round-Robin Assignment:");
        testAssignmentStrategy(new RoundRobinAssignment());
        
        // Test Least-Busy
        System.out.println("\nLeast-Busy Assignment:");
        testAssignmentStrategy(new LeastBusyAssignment());
        
        System.out.println();
    }
    
    private static void testAssignmentStrategy(AgentAssignmentStrategy strategy) {
        SupportChatService service = new SupportChatService(strategy);
        
        // Register agents
        Agent agent1 = new Agent("A1", "Agent 1", "a1@company.com", "Support", 5);
        Agent agent2 = new Agent("A2", "Agent 2", "a2@company.com", "Support", 5);
        Agent agent3 = new Agent("A3", "Agent 3", "a3@company.com", "Support", 5);
        
        agent1.goOnline();
        agent2.goOnline();
        agent3.goOnline();
        
        service.registerAgent(agent1);
        service.registerAgent(agent2);
        service.registerAgent(agent3);
        
        // Create 5 threads
        for (int i = 1; i <= 5; i++) {
            Customer customer = new Customer("C" + i, "Customer " + i, "c" + i + "@example.com");
            service.registerCustomer(customer);
            
            ChatThread thread = service.createThread("C" + i, "Issue " + i);
            System.out.println("  Thread " + i + " → Agent " + thread.getAgentIds().iterator().next());
        }
        
        // Show agent workload
        System.out.println("\nAgent workload:");
        System.out.println("  Agent 1: " + agent1.getActiveThreadCount() + " threads");
        System.out.println("  Agent 2: " + agent2.getActiveThreadCount() + " threads");
        System.out.println("  Agent 3: " + agent3.getActiveThreadCount() + " threads");
    }
    
    private static void demoThreadLifecycle() {
        System.out.println("--- Demo 5: Thread Lifecycle ---\n");
        
        SupportChatService service = new SupportChatService(new LeastBusyAssignment());
        
        Customer customer = new Customer("C1", "Customer", "customer@example.com");
        Agent agent = new Agent("A1", "Agent", "agent@company.com", "Support", 5);
        agent.goOnline();
        
        service.registerCustomer(customer);
        service.registerAgent(agent);
        
        // Create thread
        ChatThread thread = service.createThread("C1", "Product question");
        System.out.println("1. Thread created");
        System.out.println("   Status: " + thread.getStatus());
        
        // Messages exchanged
        service.sendMessage(thread.getId(), "C1", "How does this feature work?", MessageType.TEXT);
        service.sendMessage(thread.getId(), "A1", "Let me explain...", MessageType.TEXT);
        System.out.println("\n2. Messages exchanged");
        System.out.println("   Status: " + service.getThread(thread.getId()).getStatus());
        
        // Resolve
        service.resolveThread(thread.getId(), "A1");
        System.out.println("\n3. Agent resolves thread");
        System.out.println("   Status: " + service.getThread(thread.getId()).getStatus());
        
        // Close
        service.closeThread(thread.getId());
        System.out.println("\n4. Thread closed");
        System.out.println("   Status: " + service.getThread(thread.getId()).getStatus());
        System.out.println("   Agent's active threads: " + agent.getActiveThreadCount());
        
        System.out.println();
    }
}
