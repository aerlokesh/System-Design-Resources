# 🔌 Topic 81: WebSockets & Real-Time Communication Java Implementation — Production Patterns

> Complete Java implementation guide for real-time systems using **Spring WebSocket**, **Netty**, **Socket.IO**, **SSE**, and **WebRTC signaling**. Every pattern includes production-ready code with connection management, scaling, heartbeats, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Spring WebSocket Setup](#1-spring-websocket-setup)
- [2. STOMP over WebSocket](#2-stomp-over-websocket)
- [3. Raw WebSocket Handler](#3-raw-websocket-handler)
- [4. Connection Manager (Track Active Users)](#4-connection-manager-track-active-users)
- [5. Heartbeat & Keep-Alive](#5-heartbeat--keep-alive)
- [6. Room/Channel Broadcasting](#6-roomchannel-broadcasting)
- [7. Scaling with Redis Pub/Sub](#7-scaling-with-redis-pubsub)
- [8. Authentication & Authorization](#8-authentication--authorization)
- [9. Server-Sent Events (SSE)](#9-server-sent-events-sse)
- [10. Binary Messages (Protobuf/CBOR)](#10-binary-messages-protobufcbor)
- [11. Rate Limiting WebSocket Messages](#11-rate-limiting-websocket-messages)
- [12. Presence System (Online/Offline)](#12-presence-system-onlineoffline)
- [13. Chat System Implementation](#13-chat-system-implementation)
- [14. Live Notifications](#14-live-notifications)
- [15. Real-Time Dashboard (Metrics Push)](#15-real-time-dashboard-metrics-push)
- [16. Netty WebSocket Server (High Performance)](#16-netty-websocket-server-high-performance)
- [17. WebRTC Signaling Server](#17-webrtc-signaling-server)
- [18. Collaborative Editing (OT/CRDT)](#18-collaborative-editing-otcrdt)
- [19. Reconnection & State Recovery](#19-reconnection--state-recovery)
- [20. Load Testing & Monitoring](#20-load-testing--monitoring)
- [🏆 Real-Time Pattern Cheat Sheet](#-real-time-pattern-cheat-sheet)

---

## 1. Spring WebSocket Setup

```java
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker for simple pub/sub
        config.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[]{10000, 10000})  // 10s server/client heartbeat
            .setTaskScheduler(taskScheduler());
        
        // Client-to-server prefix
        config.setApplicationDestinationPrefixes("/app");
        
        // User-specific destination prefix
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("https://myapp.com")
            .withSockJS()  // Fallback for browsers without WebSocket support
            .setHeartbeatTime(25000)
            .setDisconnectDelay(5000);
    }
    
    // For production with Redis broker (multi-instance scaling)
    // config.enableStompBrokerRelay("/topic", "/queue")
    //     .setRelayHost("redis-host")
    //     .setRelayPort(61613)
    //     .setClientLogin("guest")
    //     .setClientPasscode("guest");
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024);      // 64KB max message
        registration.setSendBufferSizeLimit(512 * 1024);   // 512KB send buffer
        registration.setSendTimeLimit(20 * 1000);           // 20s send timeout
    }
}
```

---

## 2. STOMP over WebSocket

```java
// ====== Controller for STOMP messaging ======
@Controller
public class ChatController {
    private final SimpMessagingTemplate messagingTemplate;
    
    // ====== Broadcast to topic (all subscribers) ======
    @MessageMapping("/chat.sendMessage")  // Client sends to /app/chat.sendMessage
    @SendTo("/topic/public")              // Broadcasts to all /topic/public subscribers
    public ChatMessage sendMessage(@Payload ChatMessage message, 
                                    SimpMessageHeaderAccessor headerAccessor) {
        message.setTimestamp(Instant.now());
        message.setSender(headerAccessor.getUser().getName());
        return message;
    }
    
    // ====== Send to specific user (private message) ======
    @MessageMapping("/chat.privateMessage")
    public void sendPrivateMessage(@Payload PrivateMessage message, Principal principal) {
        message.setSender(principal.getName());
        message.setTimestamp(Instant.now());
        
        // Sends to /user/{targetUserId}/queue/messages
        messagingTemplate.convertAndSendToUser(
            message.getRecipientId(),
            "/queue/messages",
            message
        );
    }
    
    // ====== Send to specific channel/room ======
    @MessageMapping("/chat.room.{roomId}")
    public void sendToRoom(@DestinationVariable String roomId, 
                           @Payload ChatMessage message, Principal principal) {
        message.setSender(principal.getName());
        message.setTimestamp(Instant.now());
        
        messagingTemplate.convertAndSend("/topic/room." + roomId, message);
    }
    
    // ====== Server-initiated push (from any service) ======
    @Autowired
    public void pushNotification(String userId, Notification notification) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", notification);
    }
}

// ====== Event listeners for connect/disconnect ======
@Component
public class WebSocketEventListener {
    
    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String userId = headers.getUser().getName();
        String sessionId = headers.getSessionId();
        
        log.info("User connected: userId={}, sessionId={}", userId, sessionId);
        presenceService.markOnline(userId, sessionId);
    }
    
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String userId = headers.getUser().getName();
        
        log.info("User disconnected: userId={}", userId);
        presenceService.markOffline(userId);
        
        // Notify others
        messagingTemplate.convertAndSend("/topic/presence", 
            new PresenceEvent(userId, "OFFLINE"));
    }
}
```

---

## 3. Raw WebSocket Handler

```java
// ====== Low-level WebSocket handler (more control, no STOMP overhead) ======
@Component
public class RawWebSocketHandler extends TextWebSocketHandler {
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        sessions.put(userId, session);
        log.info("WebSocket connected: userId={}, id={}", userId, session.getId());
        Metrics.gauge("ws.active_connections", sessions.size());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            WsMessage wsMessage = mapper.readValue(message.getPayload(), WsMessage.class);
            
            switch (wsMessage.getType()) {
                case "CHAT" -> handleChatMessage(session, wsMessage);
                case "TYPING" -> handleTypingIndicator(session, wsMessage);
                case "PING" -> session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
                case "SUBSCRIBE" -> handleSubscribe(session, wsMessage);
                default -> log.warn("Unknown message type: {}", wsMessage.getType());
            }
        } catch (Exception e) {
            log.error("Error handling message from session {}", session.getId(), e);
            sendError(session, "Invalid message format");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        sessions.remove(userId);
        log.info("WebSocket closed: userId={}, status={}", userId, status);
        Metrics.gauge("ws.active_connections", sessions.size());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(extractUserId(session));
    }
    
    // ====== Send to specific user ======
    public void sendToUser(String userId, Object message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String payload = mapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.error("Failed to send to user {}", userId, e);
                sessions.remove(userId);
            }
        }
    }
    
    // ====== Broadcast to all connected users ======
    public void broadcast(Object message) {
        String payload = mapper.writeValueAsString(message);
        TextMessage textMessage = new TextMessage(payload);
        
        sessions.values().parallelStream().forEach(session -> {
            try {
                if (session.isOpen()) {
                    synchronized (session) {  // WebSocketSession is not thread-safe
                        session.sendMessage(textMessage);
                    }
                }
            } catch (IOException e) {
                log.warn("Broadcast failed for session {}", session.getId());
            }
        });
    }
}

// Register the handler
@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rawWebSocketHandler(), "/ws/raw")
            .setAllowedOrigins("https://myapp.com")
            .addInterceptors(new AuthHandshakeInterceptor());
    }
}
```

---

## 4. Connection Manager (Track Active Users)

```java
@Service
public class WebSocketConnectionManager {
    // Local instance sessions
    private final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    // Cross-instance tracking via Redis
    private final StringRedisTemplate redis;
    
    public void registerConnection(String userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        
        // Track in Redis for cross-instance awareness
        redis.opsForSet().add("ws:connected:" + userId, getInstanceId() + ":" + session.getId());
        redis.expire("ws:connected:" + userId, Duration.ofHours(24));
        
        Metrics.gauge("ws.unique_users", userSessions.size());
    }
    
    public void removeConnection(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        redis.opsForSet().remove("ws:connected:" + userId, getInstanceId() + ":" + session.getId());
    }
    
    public boolean isUserOnline(String userId) {
        // Check local first (fast)
        if (userSessions.containsKey(userId)) return true;
        // Check Redis (cross-instance)
        Long count = redis.opsForSet().size("ws:connected:" + userId);
        return count != null && count > 0;
    }
    
    public Set<String> getOnlineUsers() {
        return userSessions.keySet();
    }
    
    public int getConnectionCount() {
        return userSessions.values().stream().mapToInt(Set::size).sum();
    }
}
```

---

## 5. Heartbeat & Keep-Alive

```java
@Service
public class HeartbeatService {
    private final ConcurrentHashMap<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    @PostConstruct
    public void startHeartbeatChecker() {
        // Check for dead connections every 30 seconds
        scheduler.scheduleAtFixedRate(this::checkDeadConnections, 30, 30, TimeUnit.SECONDS);
    }
    
    public void recordHeartbeat(String userId) {
        lastHeartbeat.put(userId, Instant.now());
    }
    
    private void checkDeadConnections() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(90));  // 3 missed heartbeats
        
        lastHeartbeat.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                log.warn("User {} missed heartbeat. Disconnecting.", entry.getKey());
                connectionManager.forceDisconnect(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    // Client-side heartbeat handling in WebSocket handler
    public void handlePing(WebSocketSession session) {
        String userId = extractUserId(session);
        recordHeartbeat(userId);
        
        try {
            session.sendMessage(new TextMessage("{\"type\":\"pong\",\"ts\":" + System.currentTimeMillis() + "}"));
        } catch (IOException e) {
            log.warn("Failed to send pong to {}", userId);
        }
    }
}
```

---

## 6. Room/Channel Broadcasting

```java
@Service
public class RoomService {
    private final ConcurrentHashMap<String, Set<String>> roomMembers = new ConcurrentHashMap<>();
    private final WebSocketConnectionManager connectionManager;
    
    // ====== Join room ======
    public void joinRoom(String roomId, String userId) {
        roomMembers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        
        // Notify room members
        broadcastToRoom(roomId, new RoomEvent("USER_JOINED", userId, roomId));
        log.info("User {} joined room {}", userId, roomId);
    }
    
    // ====== Leave room ======
    public void leaveRoom(String roomId, String userId) {
        Set<String> members = roomMembers.get(roomId);
        if (members != null) {
            members.remove(userId);
            if (members.isEmpty()) roomMembers.remove(roomId);
        }
        broadcastToRoom(roomId, new RoomEvent("USER_LEFT", userId, roomId));
    }
    
    // ====== Broadcast to all room members ======
    public void broadcastToRoom(String roomId, Object message) {
        Set<String> members = roomMembers.get(roomId);
        if (members == null || members.isEmpty()) return;
        
        String payload = mapper.writeValueAsString(message);
        
        for (String userId : members) {
            connectionManager.sendToUser(userId, payload);
        }
        
        Metrics.counter("ws.room.broadcast", "room", roomId).increment();
    }
    
    // ====== Broadcast to room except sender ======
    public void broadcastToRoomExcept(String roomId, String excludeUserId, Object message) {
        Set<String> members = roomMembers.get(roomId);
        if (members == null) return;
        
        String payload = mapper.writeValueAsString(message);
        members.stream()
            .filter(uid -> !uid.equals(excludeUserId))
            .forEach(uid -> connectionManager.sendToUser(uid, payload));
    }
}
```

---

## 7. Scaling with Redis Pub/Sub

```java
// ====== Scale WebSocket across multiple server instances ======
@Service
public class RedisWebSocketBridge {
    private final StringRedisTemplate redis;
    private final WebSocketConnectionManager localConnections;
    
    // ====== Publish message (from any instance to all instances) ======
    public void publishToChannel(String channel, Object message) {
        String payload = mapper.writeValueAsString(new BridgeMessage(channel, message));
        redis.convertAndSend("ws:broadcast:" + channel, payload);
    }
    
    // ====== Send to specific user (find which instance they're on) ======
    public void sendToUser(String userId, Object message) {
        // Try local first
        if (localConnections.isLocalUser(userId)) {
            localConnections.sendToUser(userId, message);
            return;
        }
        
        // Publish to Redis — the instance holding the connection will deliver
        String payload = mapper.writeValueAsString(new DirectMessage(userId, message));
        redis.convertAndSend("ws:direct:" + userId, payload);
    }
    
    // ====== Subscribe to Redis messages (receives from other instances) ======
    @Bean
    public RedisMessageListenerContainer redisMessageListener(LettuceConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        
        // Listen for broadcast messages
        container.addMessageListener((message, pattern) -> {
            BridgeMessage bridgeMsg = mapper.readValue(message.getBody(), BridgeMessage.class);
            // Deliver to local connections subscribed to this channel
            roomService.deliverToLocalMembers(bridgeMsg.getChannel(), bridgeMsg.getPayload());
        }, new PatternTopic("ws:broadcast:*"));
        
        // Listen for direct messages
        container.addMessageListener((message, pattern) -> {
            DirectMessage directMsg = mapper.readValue(message.getBody(), DirectMessage.class);
            localConnections.sendToUser(directMsg.getUserId(), directMsg.getPayload());
        }, new PatternTopic("ws:direct:*"));
        
        return container;
    }
}
```

---

## 8. Authentication & Authorization

```java
// ====== Authenticate WebSocket handshake ======
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenService jwtService;
    
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Extract token from query param or header
        String token = extractToken(request);
        
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        
        try {
            UserClaims claims = jwtService.validateToken(token);
            attributes.put("userId", claims.getUserId());
            attributes.put("roles", claims.getRoles());
            return true;
        } catch (InvalidTokenException e) {
            log.warn("WebSocket auth failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }
    
    private String extractToken(ServerHttpRequest request) {
        // Try query parameter: /ws?token=xxx
        String query = request.getURI().getQuery();
        if (query != null && query.contains("token=")) {
            return query.split("token=")[1].split("&")[0];
        }
        // Try header
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            return authHeaders.get(0).replace("Bearer ", "");
        }
        return null;
    }
    
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {}
}

// ====== Channel-level authorization ======
@Component
public class WebSocketSecurityInterceptor implements ChannelInterceptor {
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            String userId = accessor.getUser().getName();
            
            // Check if user can subscribe to this destination
            if (destination.startsWith("/topic/room.")) {
                String roomId = destination.replace("/topic/room.", "");
                if (!roomService.isMember(roomId, userId)) {
                    throw new AccessDeniedException("Not a member of room: " + roomId);
                }
            }
        }
        
        return message;
    }
}
```

---

## 9. Server-Sent Events (SSE)

```java
// ====== SSE: One-way server → client streaming (simpler than WebSocket) ======
@RestController
@RequestMapping("/api/events")
public class SseController {
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    // ====== Client subscribes to SSE stream ======
    @GetMapping(value = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String userId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());  // 30 min timeout
        emitters.put(userId, emitter);
        
        emitter.onCompletion(() -> {
            emitters.remove(userId);
            log.info("SSE completed: {}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            log.info("SSE timeout: {}", userId);
        });
        emitter.onError(ex -> {
            emitters.remove(userId);
            log.warn("SSE error for {}: {}", userId, ex.getMessage());
        });
        
        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"status\":\"connected\"}"));
        } catch (IOException e) {
            emitters.remove(userId);
        }
        
        return emitter;
    }
    
    // ====== Push event to specific user ======
    public void pushEvent(String userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name(eventName)
                    .data(mapper.writeValueAsString(data))
                    .reconnectTime(5000));  // Client retries after 5s
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
    }
    
    // ====== Broadcast to all connected users ======
    public void broadcast(String eventName, Object data) {
        String json = mapper.writeValueAsString(data);
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        });
    }
}

// When to use SSE vs WebSocket:
// SSE: Server → Client only, auto-reconnect, works through proxies, simpler
// WebSocket: Bidirectional, lower latency, binary support, more complex
// Use SSE for: notifications, live feeds, stock prices, progress updates
// Use WebSocket for: chat, gaming, collaborative editing, real-time interactions
```

---

## 10. Binary Messages (Protobuf/CBOR)

```java
// ====== Binary WebSocket for high-performance (gaming, media) ======
@Component
public class BinaryWebSocketHandler extends BinaryWebSocketHandler {
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer payload = message.getPayload();
        
        // Decode Protobuf
        GameEvent event = GameEvent.parseFrom(payload.array());
        
        switch (event.getType()) {
            case PLAYER_MOVE -> handlePlayerMove(session, event);
            case FIRE -> handleFire(session, event);
            case STATE_SYNC -> handleStateSync(session, event);
        }
    }
    
    public void sendBinary(WebSocketSession session, GameState state) {
        byte[] encoded = state.toByteArray();  // Protobuf serialization
        try {
            session.sendMessage(new BinaryMessage(ByteBuffer.wrap(encoded)));
        } catch (IOException e) {
            log.error("Binary send failed", e);
        }
    }
    
    // Binary is 2-10x smaller than JSON for structured data
    // Protobuf GameState: ~200 bytes vs JSON: ~800 bytes
    // At 60 updates/sec × 100 players = 1.2MB/sec (proto) vs 4.8MB/sec (JSON)
}
```

---

## 11. Rate Limiting WebSocket Messages

```java
@Component
public class WebSocketRateLimiter {
    // Per-user message rate tracking
    private final ConcurrentHashMap<String, AtomicInteger> messageCounters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService resetter = Executors.newSingleThreadScheduledExecutor();
    
    private static final int MAX_MESSAGES_PER_SECOND = 10;
    
    @PostConstruct
    public void init() {
        // Reset counters every second
        resetter.scheduleAtFixedRate(() -> {
            messageCounters.values().forEach(counter -> counter.set(0));
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    public boolean allowMessage(String userId) {
        AtomicInteger counter = messageCounters.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        
        if (count > MAX_MESSAGES_PER_SECOND) {
            Metrics.counter("ws.rate_limited", "user", userId).increment();
            return false;
        }
        return true;
    }
    
    // Usage in handler:
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = extractUserId(session);
        
        if (!rateLimiter.allowMessage(userId)) {
            sendError(session, "Rate limited. Max " + MAX_MESSAGES_PER_SECOND + " msg/sec");
            return;
        }
        
        processMessage(session, message);
    }
}
```

---

## 12. Presence System (Online/Offline)

```java
@Service
public class PresenceService {
    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messaging;
    
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(60);
    
    public void markOnline(String userId) {
        redis.opsForValue().set("presence:" + userId, 
            Instant.now().toString(), PRESENCE_TTL);
        redis.opsForSet().add("online_users", userId);
        
        // Broadcast presence change
        messaging.convertAndSend("/topic/presence", 
            new PresenceEvent(userId, "ONLINE", Instant.now()));
    }
    
    public void markOffline(String userId) {
        redis.delete("presence:" + userId);
        redis.opsForSet().remove("online_users", userId);
        
        messaging.convertAndSend("/topic/presence", 
            new PresenceEvent(userId, "OFFLINE", Instant.now()));
    }
    
    // Heartbeat refresh (extend TTL)
    public void refreshPresence(String userId) {
        redis.expire("presence:" + userId, PRESENCE_TTL);
    }
    
    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(redis.hasKey("presence:" + userId));
    }
    
    public Set<String> getOnlineUsers(Set<String> userIds) {
        // Check which friends are online
        return userIds.stream()
            .filter(this::isOnline)
            .collect(Collectors.toSet());
    }
    
    public Set<Object> getAllOnlineUsers() {
        return redis.opsForSet().members("online_users");
    }
}
```

---

## 13. Chat System Implementation

```java
@Service
public class ChatService {
    private final SimpMessagingTemplate messaging;
    private final MessageRepository messageRepo;
    private final RoomService roomService;
    
    // ====== Send message to chat room ======
    public ChatMessage sendMessage(String roomId, String senderId, String content) {
        // Validate
        if (!roomService.isMember(roomId, senderId)) {
            throw new AccessDeniedException("Not a member of room: " + roomId);
        }
        
        // Create message
        ChatMessage message = new ChatMessage(
            UUID.randomUUID().toString(),
            roomId, senderId, content,
            MessageType.TEXT, Instant.now()
        );
        
        // Persist
        messageRepo.save(message);
        
        // Deliver in real-time to all room members
        messaging.convertAndSend("/topic/room." + roomId, message);
        
        // Update unread counts for offline members
        Set<String> offlineMembers = roomService.getOfflineMembers(roomId);
        offlineMembers.forEach(userId -> 
            unreadCountService.increment(userId, roomId));
        
        Metrics.counter("chat.messages.sent", "room", roomId).increment();
        return message;
    }
    
    // ====== Typing indicator ======
    public void sendTypingIndicator(String roomId, String userId, boolean isTyping) {
        messaging.convertAndSend("/topic/room." + roomId + ".typing",
            new TypingEvent(userId, isTyping));
    }
    
    // ====== Read receipt ======
    public void markAsRead(String roomId, String userId, String lastMessageId) {
        messageRepo.markRead(roomId, userId, lastMessageId);
        unreadCountService.reset(userId, roomId);
        
        // Notify sender their message was read
        messaging.convertAndSend("/topic/room." + roomId + ".read",
            new ReadReceipt(userId, lastMessageId, Instant.now()));
    }
    
    // ====== Load message history (pagination) ======
    public List<ChatMessage> getHistory(String roomId, String beforeMessageId, int limit) {
        return messageRepo.findByRoomBefore(roomId, beforeMessageId, limit);
    }
}
```

---

## 14. Live Notifications

```java
@Service
public class NotificationPushService {
    private final SimpMessagingTemplate messaging;
    private final WebSocketConnectionManager connectionManager;
    
    // ====== Push notification to user (real-time if online, queue if offline) ======
    public void pushNotification(String userId, Notification notification) {
        // Save to DB regardless
        notificationRepo.save(notification);
        
        if (connectionManager.isUserOnline(userId)) {
            // Deliver immediately via WebSocket
            messaging.convertAndSendToUser(userId, "/queue/notifications", notification);
            Metrics.counter("notification.delivered.realtime").increment();
        } else {
            // Queue for push notification (FCM/APNs)
            pushNotificationQueue.enqueue(userId, notification);
            Metrics.counter("notification.queued.push").increment();
        }
    }
    
    // ====== Batch notifications (e.g., "5 people liked your post") ======
    public void pushBatchNotification(String userId, List<Notification> notifications) {
        BatchNotification batch = new BatchNotification(
            notifications.size() + " new notifications",
            notifications.subList(0, Math.min(5, notifications.size()))
        );
        
        messaging.convertAndSendToUser(userId, "/queue/notifications", batch);
    }
}
```

---

## 15. Real-Time Dashboard (Metrics Push)

```java
@Service
public class DashboardStreamService {
    private final SimpMessagingTemplate messaging;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @PostConstruct
    public void startStreaming() {
        // Push metrics every 5 seconds
        scheduler.scheduleAtFixedRate(this::pushMetrics, 0, 5, TimeUnit.SECONDS);
    }
    
    private void pushMetrics() {
        DashboardMetrics metrics = new DashboardMetrics(
            orderService.getOrdersPerMinute(),
            orderService.getActiveOrders(),
            paymentService.getRevenueToday(),
            systemMetrics.getCpuUsage(),
            systemMetrics.getMemoryUsage(),
            systemMetrics.getActiveConnections(),
            Instant.now()
        );
        
        messaging.convertAndSend("/topic/dashboard.metrics", metrics);
    }
    
    // ====== Push real-time order updates ======
    @EventListener
    public void onOrderStatusChange(OrderStatusChangedEvent event) {
        messaging.convertAndSend("/topic/dashboard.orders", 
            new OrderUpdate(event.getOrderId(), event.getNewStatus(), Instant.now()));
    }
}
```

---

## 16. Netty WebSocket Server (High Performance)

```java
// ====== Netty: Handle 1M+ concurrent connections ======
public class NettyWebSocketServer {
    
    public void start(int port) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();  // cores × 2 threads
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                        pipeline.addLast(new WebSocketFrameHandler());  // Custom handler
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
            
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("Netty WebSocket server started on port {}", port);
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

// Custom handler for WebSocket frames
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        allChannels.add(ctx.channel());
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String message = frame.text();
        // Broadcast to all connected clients
        allChannels.writeAndFlush(new TextWebSocketFrame("Echo: " + message));
    }
    
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        allChannels.remove(ctx.channel());
    }
}
```

---

## 17. WebRTC Signaling Server

```java
// ====== WebRTC Signaling: Coordinate peer-to-peer connections ======
@Service
public class WebRTCSignalingService {
    private final SimpMessagingTemplate messaging;
    private final ConcurrentHashMap<String, String> callSessions = new ConcurrentHashMap<>();
    
    // ====== Initiate call (send offer to callee) ======
    @MessageMapping("/webrtc.call")
    public void initiateCall(@Payload CallRequest request, Principal principal) {
        String callerId = principal.getName();
        String calleeId = request.getCalleeId();
        
        String callId = UUID.randomUUID().toString();
        callSessions.put(callId, callerId + ":" + calleeId);
        
        // Forward offer SDP to callee
        messaging.convertAndSendToUser(calleeId, "/queue/webrtc", 
            new SignalingMessage("offer", callId, callerId, request.getSdpOffer()));
        
        log.info("Call initiated: {} → {} (callId={})", callerId, calleeId, callId);
    }
    
    // ====== Callee answers (send answer back to caller) ======
    @MessageMapping("/webrtc.answer")
    public void answerCall(@Payload AnswerRequest request, Principal principal) {
        String calleeId = principal.getName();
        String callId = request.getCallId();
        String callerId = callSessions.get(callId).split(":")[0];
        
        // Forward answer SDP to caller
        messaging.convertAndSendToUser(callerId, "/queue/webrtc",
            new SignalingMessage("answer", callId, calleeId, request.getSdpAnswer()));
    }
    
    // ====== ICE candidate exchange (for NAT traversal) ======
    @MessageMapping("/webrtc.ice")
    public void exchangeIceCandidate(@Payload IceCandidateMessage ice, Principal principal) {
        String senderId = principal.getName();
        String callId = ice.getCallId();
        String session = callSessions.get(callId);
        
        // Forward ICE candidate to the other peer
        String targetId = session.split(":")[0].equals(senderId) 
            ? session.split(":")[1] 
            : session.split(":")[0];
        
        messaging.convertAndSendToUser(targetId, "/queue/webrtc",
            new SignalingMessage("ice-candidate", callId, senderId, ice.getCandidate()));
    }
    
    // ====== End call ======
    @MessageMapping("/webrtc.hangup")
    public void hangup(@Payload HangupRequest request, Principal principal) {
        String callId = request.getCallId();
        String session = callSessions.remove(callId);
        
        if (session != null) {
            String otherId = session.replace(principal.getName(), "").replace(":", "");
            messaging.convertAndSendToUser(otherId, "/queue/webrtc",
                new SignalingMessage("hangup", callId, principal.getName(), null));
        }
    }
}
```

---

## 18. Collaborative Editing (OT/CRDT)

```java
@Service
public class CollaborativeEditingService {
    private final SimpMessagingTemplate messaging;
    private final ConcurrentHashMap<String, DocumentState> documents = new ConcurrentHashMap<>();
    
    // ====== Apply operation and broadcast to other editors ======
    @MessageMapping("/doc.edit.{docId}")
    public void applyEdit(@DestinationVariable String docId, 
                          @Payload EditOperation operation, Principal principal) {
        String userId = principal.getName();
        DocumentState state = documents.computeIfAbsent(docId, k -> new DocumentState());
        
        // Transform operation against concurrent edits (OT)
        EditOperation transformed = state.transform(operation);
        
        // Apply to server state
        state.apply(transformed);
        
        // Broadcast to all editors EXCEPT sender
        transformed.setUserId(userId);
        transformed.setVersion(state.getVersion());
        
        messaging.convertAndSend("/topic/doc." + docId, transformed);
        
        log.debug("Edit applied to doc {}: type={}, pos={}, by={}", 
            docId, operation.getType(), operation.getPosition(), userId);
    }
    
    // ====== Cursor position broadcasting ======
    @MessageMapping("/doc.cursor.{docId}")
    public void updateCursor(@DestinationVariable String docId,
                             @Payload CursorPosition cursor, Principal principal) {
        cursor.setUserId(principal.getName());
        // Broadcast cursor position to other editors (no persistence needed)
        messaging.convertAndSend("/topic/doc." + docId + ".cursors", cursor);
    }
    
    // ====== Get current document state (on join) ======
    public DocumentSnapshot getDocument(String docId) {
        DocumentState state = documents.get(docId);
        if (state == null) return null;
        return new DocumentSnapshot(state.getContent(), state.getVersion(), state.getActiveEditors());
    }
}
```

---

## 19. Reconnection & State Recovery

```java
@Service
public class ReconnectionService {
    private final ConcurrentHashMap<String, List<QueuedMessage>> offlineBuffers = new ConcurrentHashMap<>();
    
    // ====== Buffer messages for disconnected users (short-term) ======
    public void bufferForUser(String userId, Object message) {
        offlineBuffers.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
            .add(new QueuedMessage(message, Instant.now()));
        
        // Limit buffer size
        List<QueuedMessage> buffer = offlineBuffers.get(userId);
        while (buffer.size() > 100) {
            buffer.remove(0);  // Drop oldest
        }
    }
    
    // ====== Deliver buffered messages on reconnect ======
    public void onReconnect(String userId, WebSocketSession session, long lastSeenTimestamp) {
        List<QueuedMessage> buffer = offlineBuffers.remove(userId);
        
        if (buffer != null && !buffer.isEmpty()) {
            // Filter: only messages after last seen
            Instant lastSeen = Instant.ofEpochMilli(lastSeenTimestamp);
            List<QueuedMessage> missed = buffer.stream()
                .filter(m -> m.getTimestamp().isAfter(lastSeen))
                .collect(Collectors.toList());
            
            log.info("Delivering {} buffered messages to reconnected user {}", 
                missed.size(), userId);
            
            for (QueuedMessage msg : missed) {
                connectionManager.sendToUser(userId, msg.getPayload());
            }
        }
    }
    
    // ====== Client reconnection protocol ======
    // Client sends: {"type":"RECONNECT","lastMessageId":"msg-abc","lastTimestamp":1705000000}
    // Server responds with missed messages since that point
    public void handleReconnect(WebSocketSession session, ReconnectRequest request) {
        String userId = extractUserId(session);
        
        // Get missed messages from persistent store
        List<ChatMessage> missed = messageRepo.findAfter(
            request.getRoomId(), request.getLastMessageId(), 100);
        
        // Send catch-up batch
        session.sendMessage(new TextMessage(mapper.writeValueAsString(
            new CatchUpResponse(missed, missed.size()))));
    }
}
```

---

## 20. Load Testing & Monitoring

```java
// ====== WebSocket metrics for production monitoring ======
@Component
public class WebSocketMetrics {
    private final MeterRegistry registry;
    private final LongAdder activeConnections = new LongAdder();
    private final LongAdder messagesSent = new LongAdder();
    private final LongAdder messagesReceived = new LongAdder();
    
    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("ws.connections.active", activeConnections, LongAdder::sum)
            .register(registry);
        
        FunctionCounter.builder("ws.messages.sent", messagesSent, LongAdder::sum)
            .register(registry);
        
        FunctionCounter.builder("ws.messages.received", messagesReceived, LongAdder::sum)
            .register(registry);
    }
    
    public void onConnect() { activeConnections.increment(); }
    public void onDisconnect() { activeConnections.decrement(); }
    public void onMessageSent() { messagesSent.increment(); }
    public void onMessageReceived() { messagesReceived.increment(); }
}
```

---

## 🏆 Real-Time Pattern Cheat Sheet

| Use Case | Technology | Why |
|---|---|---|
| Chat/messaging | WebSocket + STOMP | Bidirectional, low latency, room support |
| Notifications | SSE or WebSocket | SSE simpler if one-way only |
| Live dashboard | SSE | Server push only, auto-reconnect |
| Gaming/media | Netty + binary WS | Millions of connections, low latency |
| Video call | WebRTC + WS signaling | P2P media, WS for signaling only |
| Collaborative editing | WebSocket + OT/CRDT | Real-time sync with conflict resolution |
| Presence (online/offline) | WebSocket + Redis | Cross-instance, TTL-based |
| Multi-instance scaling | Redis Pub/Sub bridge | Broadcast across server instances |

| Scaling Strategy | Connections | Approach |
|---|---|---|
| < 10K connections | Single Spring Boot | In-memory, simple |
| 10K-100K | Multiple instances + Redis | Redis Pub/Sub bridge |
| 100K-1M | Netty + custom protocol | Event-loop based, minimal overhead |
| 1M+ | Dedicated WS gateway cluster | Separate from business logic |

| Performance Tips |
|---|
| ✅ Use binary (Protobuf) for high-frequency data (gaming, prices) |
| ✅ Batch messages when possible (send every 100ms, not per-event) |
| ✅ Heartbeat every 25-30s (detect dead connections) |
| ✅ Rate limit per user (prevent spam/abuse) |
| ✅ Buffer messages for short disconnects (< 2 min) |
| ❌ Don't broadcast to all users — use rooms/channels |
| ❌ Don't store WebSocket sessions in shared state |
| ❌ Don't send large payloads (>64KB) via WebSocket |

---

*All examples use Spring WebSocket/STOMP for standard cases and Netty for high-performance scenarios. For WebRTC, the signaling server is WebSocket-based but media flows peer-to-peer.*
