# Communication Protocols - Complete System Design Interview Guide

**COMPREHENSIVE GUIDE FOR FAANG-LEVEL INTERVIEWS**

> This guide covers all communication protocols needed for system design interviews with detailed examples, performance benchmarks, and real-world use cases. Total length: ~5,500 lines for thorough interview preparation.

## Table of Contents
- [Communication Protocols - Complete System Design Interview Guide](#communication-protocols---complete-system-design-interview-guide)
  - [Table of Contents](#table-of-contents)
  - [Introduction \& Overview](#introduction--overview)
    - [Why Protocol Selection is Critical in System Design](#why-protocol-selection-is-critical-in-system-design)
    - [The Protocol Selection Interview Framework](#the-protocol-selection-interview-framework)
    - [Quick Protocol Selection Cheat Sheet](#quick-protocol-selection-cheat-sheet)
  - [Server-Sent Events (SSE) - Unidirectional Push](#server-sent-events-sse---unidirectional-push)
    - [What is Server-Sent Events?](#what-is-server-sent-events)
    - [SSE Protocol Details](#sse-protocol-details)
    - [SSE Client Implementation](#sse-client-implementation)
    - [SSE Server Implementation](#sse-server-implementation)
    - [SSE Use Cases \& Patterns](#sse-use-cases--patterns)
    - [SSE Scalability \& Best Practices](#sse-scalability--best-practices)
    - [SSE vs Alternatives](#sse-vs-alternatives)
    - [When to Use SSE](#when-to-use-sse)
    - [Interview Talking Points for SSE](#interview-talking-points-for-sse)
  - [Long Polling - Periodic Updates](#long-polling---periodic-updates)
    - [What is Long Polling?](#what-is-long-polling)
    - [Long Polling vs Traditional Polling](#long-polling-vs-traditional-polling)
    - [Long Polling Implementation](#long-polling-implementation)

---

## Introduction & Overview

### Why Protocol Selection is Critical in System Design

In system design interviews, choosing the right communication protocol can make or break your architecture. The protocol you select affects:

```
Performance Impact:
├── Latency: 1ms (WebSocket) vs 100ms (REST)
├── Throughput: 1M msg/sec (Kafka) vs 10K req/sec (REST)
├── Bandwidth: 70% reduction with gRPC vs REST
└── Battery life: MQTT uses 1/10th power of HTTP polling

Cost Impact:
├── Infrastructure: Wrong choice = 10x more servers
├── Bandwidth: Inefficient protocol = $100K+/month extra
├── Development: Complex protocol = months of work
└── Maintenance: Protocol mismatch = constant firefighting

Scale Impact:
├── Real-time requirements: <100ms latency
├── High throughput: >100K requests/second
├── Concurrent connections: 1M+ WebSocket connections
└── Global distribution: CDN, edge computing
```

### The Protocol Selection Interview Framework

**What interviewers look for:**

```
1. Understanding of trade-offs
   ✓ "I'd use WebSocket for real-time bidirectional chat because..."
   ✗ "I'd use WebSocket because it's faster"

2. Matching protocol to use case
   ✓ "For microservices, gRPC provides 5-10x better performance than REST"
   ✗ "I'd use REST for everything"

3. Consideration of constraints
   ✓ "For mobile clients, we need HTTP/REST for battery efficiency"
   ✗ "WebSocket works everywhere"

4. Scalability awareness
   ✓ "At 1M concurrent connections, we need sticky sessions for WebSocket"
   ✗ "Just use load balancer"

5. Real-world experience
   ✓ "Twitter uses hybrid fanout - push for regular users, pull for celebrities"
   ✗ "Twitter uses WebSocket"
```

### Quick Protocol Selection Cheat Sheet

```
┌─────────────────────┬──────────────────────────────────┐
│ Use Case            │ Recommended Protocol             │
├─────────────────────┼──────────────────────────────────┤
│ Public REST API     │ HTTP/REST (JSON)                 │
│ Microservices       │ gRPC (Protocol Buffers)          │
│ Real-time Chat      │ WebSocket                        │
│ Live Notifications  │ WebSocket or SSE                 │
│ Video Streaming     │ HTTP/2 (adaptive), UDP (RTP)     │
│ IoT Sensors         │ MQTT                             │
│ Event Streaming     │ Apache Kafka                     │
│ Task Queues         │ RabbitMQ or AWS SQS              │
│ Mobile-Backend      │ HTTP/REST + WebSocket            │
│ Gaming (realtime)   │ UDP + TCP (hybrid)               │
└─────────────────────┴──────────────────────────────────┘
```

---

## Server-Sent Events (SSE) - Unidirectional Push

### What is Server-Sent Events?

```
SSE: HTTP-based protocol for server → client push
├── One-way: Server pushes, client receives
├── Built on HTTP (no special protocol)
├── Automatic reconnection
├── Text-based (event stream)
└── W3C Standard (2015)

Key Characteristics:
├── Simple HTTP GET request
├── Content-Type: text/event-stream
├── Long-lived connection
├── UTF-8 text only (no binary)
└── Browser native support (EventSource API)
```

### SSE Protocol Details

**Connection Establishment:**
```
Client Request:
GET /events HTTP/1.1
Host: example.com
Accept: text/event-stream
Cache-Control: no-cache

Server Response:
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

data: First message\n\n

data: Second message\n\n

data: {"type": "notification", "text": "New message"}\n\n

Event Format:
data: message content\n\n

With ID (for reconnection):
id: 123
data: message content\n\n

With Event Type:
event: userLoggedIn
data: {"userId": "abc"}\n\n

With Retry:
retry: 10000
data: connection info\n\n

Multi-line Data:
data: First line\n
data: Second line\n
data: Third line\n\n

Comments (for keep-alive):
: heartbeat\n\n
```

### SSE Client Implementation

**JavaScript (Browser):**
```javascript
// Create EventSource
const eventSource = new EventSource('/events');

// Listen to messages
eventSource.onmessage = (event) => {
  console.log('Message:', event.data);
  const data = JSON.parse(event.data);
  updateUI(data);
};

// Listen to specific event types
eventSource.addEventListener('userLoggedIn', (event) => {
  console.log('User logged in:', event.data);
});

eventSource.addEventListener('notification', (event) => {
  showNotification(JSON.parse(event.data));
});

// Handle connection opened
eventSource.onopen = () => {
  console.log('Connection established');
};

// Handle errors
eventSource.onerror = (error) => {
  console.error('SSE error:', error);
  if (eventSource.readyState === EventSource.CLOSED) {
    console.log('Connection closed');
  }
};

// Close connection
eventSource.close();

// Auto-reconnection:
// Browser automatically reconnects on disconnect
// Sends Last-Event-ID header to resume
// Default retry: 3 seconds (configurable by server)
```

**Advanced Client with Authentication:**
```javascript
class SSEManager {
  constructor(url, token) {
    this.url = `${url}?token=${token}`; // Token in query (not ideal but works)
    this.eventSource = null;
    this.handlers = {};
    this.connect();
  }
  
  connect() {
    this.eventSource = new EventSource(this.url);
    
    this.eventSource.onopen = () => {
      console.log('SSE Connected');
    };
    
    this.eventSource.onerror = (error) => {
      console.error('SSE Error:', error);
      // EventSource auto-reconnects, no need to manually reconnect
    };
    
    this.eventSource.onmessage = (event) => {
      this.handleMessage(event);
    };
  }
  
  on(eventType, handler) {
    if (!this.handlers[eventType]) {
      this.handlers[eventType] = [];
      this.eventSource.addEventListener(eventType, (event) => {
        this.handlers[eventType].forEach(h => h(JSON.parse(event.data)));
      });
    }
    this.handlers[eventType].push(handler);
  }
  
  handleMessage(event) {
    const data = JSON.parse(event.data);
    console.log('Received:', data);
  }
  
  close() {
    if (this.eventSource) {
      this.eventSource.close();
    }
  }
}

// Usage
const sse = new SSEManager('/api/events', 'JWT_TOKEN');

sse.on('notification', (data) => {
  showNotification(data);
});

sse.on('priceUpdate', (data) => {
  updatePriceDisplay(data);
});
```

### SSE Server Implementation

**Node.js/Express:**
```javascript
const express = require('express');
const app = express();

// Store connected clients
const clients = new Set();

app.get('/events', (req, res) => {
  // Set SSE headers
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*'
  });
  
  // Send initial connection message
  res.write('data: {"type": "connected"}\n\n');
  
  // Add client to active connections
  clients.add(res);
  
  // Send heartbeat every 30 seconds
  const heartbeat = setInterval(() => {
    res.write(': heartbeat\n\n');
  }, 30000);
  
  // Cleanup on disconnect
  req.on('close', () => {
    clearInterval(heartbeat);
    clients.delete(res);
    console.log('Client disconnected');
  });
});

// Broadcast to all clients
function broadcast(eventType, data) {
  const message = `event: ${eventType}\ndata: ${JSON.stringify(data)}\n\n`;
  
  clients.forEach(client => {
    client.write(message);
  });
}

// Send to specific client
function sendToClient(clientRes, eventType, data) {
  const message = `event: ${eventType}\ndata: ${JSON.stringify(data)}\n\n`;
  clientRes.write(message);
}

// Example: Broadcast notification
app.post('/notify', (req, res) => {
  broadcast('notification', {
    message: 'New update available',
    timestamp: Date.now()
  });
  res.json({ success: true });
});

app.listen(3000);
```

**Python/Flask:**
```python
from flask import Flask, Response, request
import json
import time
from queue import Queue
import threading

app = Flask(__name__)

# Store client queues
clients = {}

def event_stream(client_id):
    """Generate SSE stream for client"""
    queue = Queue()
    clients[client_id] = queue
    
    try:
        # Send connection message
        yield f"data: {json.dumps({'type': 'connected'})}\n\n"
        
        while True:
            # Check for new messages
            try:
                message = queue.get(timeout=30)
                yield f"data: {message}\n\n"
            except:
                # Send heartbeat
                yield ": heartbeat\n\n"
                
    finally:
        # Cleanup
        if client_id in clients:
            del clients[client_id]

@app.route('/events')
def events():
    client_id = request.args.get('client_id', str(time.time()))
    
    return Response(
        event_stream(client_id),
        content_type='text/event-stream',
        headers={
            'Cache-Control': 'no-cache',
            'X-Accel-Buffering': 'no'
        }
    )

def broadcast(event_type, data):
    """Broadcast to all connected clients"""
    message = json.dumps({'type': event_type, 'data': data})
    for queue in clients.values():
        queue.put(message)

@app.route('/notify', methods=['POST'])
def notify():
    data = request.json
    broadcast('notification', data)
    return {'success': True}

if __name__ == '__main__':
    app.run(threaded=True)
```

### SSE Use Cases & Patterns

**1. Live Notifications**
```
Pattern: Server → Client updates

Use Cases:
├── Social media notifications
├── System alerts
├── Breaking news
├── Order status updates
└── Dashboard metrics

Example - E-commerce:
Order placed → Server detects
  ↓
Send SSE event to user
  ↓
User sees "Order confirmed" notification

Benefits:
├── Instant delivery (<100ms)
├── No client polling
├── Simple implementation
└── Auto-reconnect on disconnect
```

**2. Live Feed Updates**
```
Pattern: Continuous data stream

Use Cases:
├── Stock price tickers
├── Sports scores
├── Twitter/news feeds
├── Server logs
└── Metrics dashboards

Example - Stock Ticker:
Server:
setInterval(() => {
  const price = getStockPrice('AAPL');
  broadcast('priceUpdate', {
    symbol: 'AAPL',
    price: price,
    timestamp: Date.now()
  });
}, 1000);

Client:
sse.addEventListener('priceUpdate', (event) => {
  const data = JSON.parse(event.data);
  updatePriceDisplay(data.symbol, data.price);
});

Performance:
├── 1 update/second per stock
├── Can stream 100+ stocks simultaneously
├── <100ms latency from server to client
└── Minimal bandwidth (compared to polling)
```

**3. Progress Tracking**
```
Pattern: Long-running operation updates

Use Cases:
├── File upload progress
├── Video processing status
├── Data export/import
├── Batch job progress
└── Installation progress

Example - File Upload:
Server (during upload processing):
function updateProgress(jobId, progress) {
  sendToClient(clientsByJobId[jobId], 'progress', {
    jobId: jobId,
    percent: progress,
    status: 'processing'
  });
}

Client:
sse.addEventListener('progress', (event) => {
  const data = JSON.parse(event.data);
  updateProgressBar(data.percent);
  
  if (data.percent === 100) {
    showCompleteMessage();
  }
});
```

### SSE Scalability & Best Practices

**1. Connection Management**
```
Server Limits:
├── Each SSE connection = one thread/process
├── Node.js: ~10K connections per server
├── Python: ~1K connections per server (without async)
├── Go: ~100K connections per server

Scaling Strategy:
┌─────────┐              ┌──────────────┐
│ Client  │────────────→ │   HAProxy    │
└─────────┘              └──────┬───────┘
                                │
        ┌───────────────────────┼───────────────────┐
        ↓                       ↓                   ↓
  [SSE Server 1]          [SSE Server 2]      [SSE Server 3]
  (10K connections)       (10K connections)   (10K connections)
        │                       │                   │
        └───────────────────────┼───────────────────┘
                                ↓
                         ┌──────────────┐
                         │ Redis Pub/Sub│
                         └──────────────┘

Implementation:
// Subscribe to Redis
redis.subscribe('notifications');

redis.on('message', (channel, message) => {
  broadcast('notification', JSON.parse(message));
});

// Publish from any server
redis.publish('notifications', JSON.stringify(data));

Benefits:
├── Scale horizontally
├── Shared state across servers
├── No sticky sessions needed (but recommended)
└── Can handle millions of connections
```

**2. Performance Optimization**
```
Heartbeat Strategy:
// Send comment-based heartbeat (no data to client)
setInterval(() => {
  clients.forEach(client => {
    client.write(': heartbeat\n\n');
  });
}, 30000);

Benefits:
├── Keeps connection alive
├── Detects dead connections
├── Proxy/firewall friendly
└── Minimal bandwidth

Event ID for Resumption:
Server:
let eventId = 0;
function sendEvent(data) {
  eventId++;
  clients.forEach(client => {
    client.write(`id: ${eventId}\ndata: ${JSON.stringify(data)}\n\n`);
  });
}

Client reconnects with:
GET /events
Last-Event-ID: 123

Server:
app.get('/events', (req, res) => {
  const lastEventId = req.headers['last-event-id'];
  
  if (lastEventId) {
    // Send missed events since lastEventId
    sendMissedEvents(res, lastEventId);
  }
  
  // Continue with normal stream
  setupEventStream(res);
});

Message Batching:
// Batch multiple updates
const buffer = [];
setInterval(() => {
  if (buffer.length > 0) {
    const message = `data: ${JSON.stringify(buffer)}\n\n`;
    clients.forEach(client => client.write(message));
    buffer.length = 0;
  }
}, 100);
```

**3. Error Handling**
```
Client-Side:
eventSource.addEventListener('error', (event) => {
  if (event.target.readyState === EventSource.CLOSED) {
    // Connection permanently closed
    showError('Connection lost');
  } else if (event.target.readyState === EventSource.CONNECTING) {
    // Reconnecting
    showReconnecting();
  }
});

Server-Side:
app.get('/events', (req, res) => {
  // Set timeout to close stale connections
  req.setTimeout(300000); // 5 minutes
  
  req.on('timeout', () => {
    res.end();
    clients.delete(res);
  });
  
  req.on('error', (error) => {
    console.error('Client error:', error);
    clients.delete(res);
  });
});

Retry Configuration:
Server sends:
retry: 5000
data: connection info\n\n

// Client will wait 5 seconds before reconnecting
```

### SSE vs Alternatives

```
SSE vs WebSocket:
┌──────────────┬──────────┬──────────────┐
│ Feature      │ SSE      │ WebSocket    │
├──────────────┼──────────┼──────────────┤
│ Direction    │ Server→C │ Bidirectional│
│ Protocol     │ HTTP     │ WebSocket    │
│ Complexity   │ Simple   │ More complex │
│ Auto-reconnect│ Built-in│ Manual       │
│ Binary Data  │ No       │ Yes          │
│ Browser API  │ Native   │ Native       │
│ Proxy Support│ Better   │ May block    │
│ Use Case     │ Updates  │ Chat, Gaming │
└──────────────┴──────────┴──────────────┘

SSE vs Long Polling:
┌──────────────┬──────────┬──────────────┐
│ Feature      │ SSE      │ Long Polling │
├──────────────┼──────────┼──────────────┤
│ Efficiency   │ High     │ Medium       │
│ Latency      │ Low      │ Medium       │
│ Complexity   │ Simple   │ Simple       │
│ Connection   │ 1 conn   │ Many req     │
│ HTTP/2       │ Yes      │ Yes          │
│ Server Load  │ Low      │ Higher       │
└──────────────┴──────────┴──────────────┘

Choose SSE when:
├── Server → Client only (one-way)
├── Text data (JSON, XML, plain text)
├── Want automatic reconnection
├── Simple implementation preferred
├── Good proxy/firewall support needed
└── Live feeds, notifications, dashboards

Choose WebSocket when:
├── Bidirectional communication needed
├── Binary data transfer required
├── Very low latency required (<50ms)
├── Complex messaging patterns
└── Chat, gaming, collaborative editing

Choose Long Polling when:
├── SSE not supported (very old browsers)
├── Need to support ancient proxies
├── Infrequent updates (every few seconds)
└── Fallback for WebSocket/SSE
```

### When to Use SSE

**✅ Perfect For:**
```
1. Live Dashboards:
   - Metrics updates
   - System monitoring
   - Analytics dashboards
   - Real-time charts
   
   Example: Grafana-style dashboards

2. News/Social Media Feeds:
   - Twitter feed updates
   - News articles
   - Blog posts
   - Activity streams
   
   Example: Facebook news feed

3. Notifications:
   - System alerts
   - User notifications
   - Status updates
   - Progress tracking
   
   Example: Gmail new email notifications

4. Live Scores/Prices:
   - Stock prices
   - Sports scores
   - Auction bids
   - Leaderboards
   
   Example: Yahoo Finance live ticker

5. Server Logs:
   - Application logs
   - Server monitoring
   - Error tracking
   - Audit trails
   
   Example: Tail -f style log streaming
```

**❌ Not Ideal For:**
```
1. Bidirectional Communication:
   Problem: Client can't send via SSE connection
   Better: WebSocket
   Example: Chat applications

2. Binary Data:
   Problem: Text-only protocol
   Better: WebSocket
   Example: Video/audio streaming

3. High Frequency (>10 msg/sec):
   Problem: JSON parsing overhead
   Better: WebSocket with binary
   Example: Gaming, high-frequency trading

4. Mobile Apps (Native):
   Problem: Background restrictions
   Better: Push notifications (APNs, FCM)
   Example: iOS/Android apps

5. Old Browser Support:
   Problem: IE 11 and older
   Better: Long polling fallback
   Example: Enterprise legacy apps
```

### Interview Talking Points for SSE

**When interviewer asks: "How would you implement real-time updates?"**

```
"I'd evaluate SSE vs WebSocket based on communication pattern:

Use SSE if:
1. Unidirectional (Server → Client):
   - Stock price updates
   - News feed
   - Notifications
   - Progress tracking
   
   Example: Dashboard showing live metrics
   ├── Server pushes updates every second
   ├── Client only displays, doesn't send
   ├── SSE is simpler than WebSocket
   └── Automatic reconnection built-in

2. Simplicity Preferred:
   - SSE uses standard HTTP
   - No special protocol
   - Works with existing infrastructure
   - Better proxy/firewall support

3. Implementation:
   Server (Node.js):
   app.get('/events', (req, res) => {
     res.writeHead(200, {
       'Content-Type': 'text/event-stream',
       'Cache-Control': 'no-cache'
     });
     
     clients.add(res);
     
     req.on('close', () => {
       clients.delete(res);
     });
   });
   
   Client:
   const sse = new EventSource('/events');
   sse.onmessage = (event) => {
     updateUI(JSON.parse(event.data));
   };

4. Performance:
   - Latency: 50-100ms (similar to WebSocket)
   - Throughput: 1-10 updates/second per client
   - Scale: 10K connections per Node.js server
   - Bandwidth: 90% less than polling

5. Reliability:
   - Auto-reconnect on disconnect
   - Event IDs for missed message recovery
   - Heartbeat to detect dead connections
   - Browser handles reconnection logic

Use WebSocket instead if:
- Bidirectional communication needed (client sends data)
- Binary data transfer required
- Very high frequency (>10 msg/sec)
- Gaming or real-time collaboration

For this use case [explain specific system]:
I recommend SSE because..."
```

---

## Long Polling - Periodic Updates

### What is Long Polling?

```
Long Polling: HTTP technique for near-real-time updates
├── Client makes request
├── Server holds request open until data available
├── Server responds with data
├── Client immediately makes new request
└── Repeat cycle

Key Characteristics:
├── Built on standard HTTP
├── No special protocol required
├── Works with any HTTP client
├── Higher latency than WebSocket/SSE (100-500ms)
└── More server resources than WebSocket/SSE
```

### Long Polling vs Traditional Polling

**Traditional Polling (Bad):**
```
Client makes request every X seconds regardless of updates:

Client:                 Server:
GET /api/updates ────→ { "updates": [] }  (no updates)
[wait 5 seconds]
GET /api/updates ────→ { "updates": [] }  (no updates)
[wait 5 seconds]
GET /api/updates ────→ { "updates": [] }  (no updates)
[wait 5 seconds]
GET /api/updates ────→ { "updates": [...] } (finally!)

Problems:
├── Wasted requests (99% return empty)
├── High server load (constant requests)
├── Increased latency (up to polling interval)
├── Bandwidth waste (HTTP overhead)
└── Battery drain on mobile

Latency:
- Average: Polling interval / 2
- Example: 5-second polling = 2.5s average latency
- Worst case: Full polling interval
```

**Long Polling (Better):**
```
Client makes request, server holds until data available:

Client:                 Server:
GET /api/updates ────→ [Server holds connection]
                       [Waiting for data...]
                       [Update arrives!]
                  ←──── { "updates": [...] }
GET /api/updates ────→ [Server holds connection]
                       [Waiting...]
                  ←──── { "updates": [...] }

Benefits:
├── No wasted requests
├── Lower latency (<1 second)
├── Less server load (fewer requests)
├── Better battery life
└── Still uses standard HTTP

Latency:
- Average: <500ms
- Much better than traditional polling
- But higher than WebSocket/SSE (<100ms)
```

### Long Polling Implementation

**Client-Side (JavaScript):**
```javascript
class LongPollingClient {
  constructor(url, options = {}) {
    this.url = url;
    this.timeout = options.timeout || 30000;
    this.retryDelay = options.retryDelay || 1000;
    this.maxRetryDelay = options.maxRetryDelay || 30000;
    this.currentRetryDelay = this.retryDelay;
    this.running = false;
    this.onMessage = options.onMessage || (() => {});
    this.onError = options.onError || (() => {});
  }
  
  start() {
    this.running = true;
    this.poll();
  }
  
  stop() {
    this.running = false;
  }
  
  async poll() {
    if (!this.running) return;
    
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), this.timeout);
      
      const response = await fetch(this.url, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal
      });
      
      clearTimeout(timeoutId);
      
      if (response.ok) {
        const data = await response.json();
        
        if (data.updates && data.updates.length > 0) {
          this.onMessage(data.updates);
        }
        
        // Reset retry delay on success
        this.currentRetryDelay = this.retryDelay;
        
        // Immediately start next poll
        this.poll();
      } else {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (error) {
      this.onError(error);
      
      // Exponential backoff
      setTimeout(() => {
        if (this.running) {
          this.poll();
        }
      }, this.currentRetryDelay);
      
      this.currentRetryDelay = Math.min(
        this.currentRetryDelay * 2,
        this.maxRetryDelay
      );
    }
  }
}

// Usage
const client = new LongPollingClient('/api/updates', {
  timeout: 30000,
  onMessage: (updates) => {
    updates.forEach(update => {
      console.log('Update:', update);
      updateUI(update);
    });
  },
  onError: (error) => {
    console.error('Polling error:', error);
  }
});

client.start();

// Stop when done
// client.stop();
```

**Server-Side (Node.js/Express):**
```javascript
const express = require('express');
const app = express();

// Store pending requests per user
const pendingRequests = new Map();

// Store updates per user
const userUpdates = new Map();

app.get('/api/updates', (req, res) => {
  const userId = req.query.userId;
  const timeout = 30000; // 30 seconds
  
  // Check if updates already available
  const updates = userUpdates.get(userId) || [];
  if (updates.length > 0) {
    res.json({ updates });
    userUpdates.set(userId, []);
    return;
  }
  
  // Store request to respond later
  const requestData = { res, userId };
  pendingRequests.set(userId, requestData);
  
  // Set timeout to respond even if no updates
  const timeoutId = setTimeout(() => {
    if (pendingRequests.has(userId)) {
      res.json({ updates: [] });
      pendingRequests.delete(userId);
    }
  }, timeout);
  
  // Cleanup on disconnect
  req.on('close', () => {
    clearTimeout(timeoutId);
    pendingRequests.delete(userId);
  });
});

// Function to push update to user
function pushUpdateToUser(userId, update) {
  const pending = pendingRequests.get(userId);
  
  if (pending) {
    // User has pending request, respond immediately
    pending.res.json({ updates: [update] });
    pendingRequests.delete(userId);
