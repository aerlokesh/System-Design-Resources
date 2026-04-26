# Design Outlook.com (Web Email Service) — Hello Interview Framework

> **Question**: Architect a web-based email system like Outlook.com that manages user mailboxes, sends/receives email at scale, provides search functionality, and handles spam filtering — serving hundreds of millions of users.
>
> **Asked at**: Microsoft, Google, Amazon, Yahoo
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
1. **Send Email**: Compose and send emails with To/CC/BCC, subject, body (rich text HTML), and attachments (up to 25 MB). Support SMTP/MIME standard.
2. **Receive Email**: Receive emails from any email server on the internet. Store in user's mailbox. Handle bounces and delivery failures.
3. **Mailbox Management**: Inbox, Sent, Drafts, Trash, Archive, Spam/Junk folders. Custom folders. Move/copy between folders. Mark as read/unread, flag, star.
4. **Search**: Full-text search across subject, body, sender, attachments. Filter by date, folder, has-attachment, from, to. Search suggestions and autocomplete.
5. **Spam Filtering**: Classify incoming email as spam/not-spam using ML models. User can mark as spam/not-spam (feedback loop). Phishing detection.
6. **Push Notifications**: Real-time notification when new email arrives. Badge counts. Desktop/mobile push.
7. **Contacts & Address Book**: Auto-complete recipient addresses. Contact list management. Integration with Azure AD directory for enterprise.

#### Nice to Have (P1)
- Conversation threading (group emails by subject/references)
- Rules/filters (auto-move, auto-label, forward)
- Focused Inbox (AI-prioritized important vs other)
- Calendar integration (meeting invites inline)
- Attachment preview (Office files, PDFs, images)
- Email scheduling (send later)
- Undo send (30-second delay option)
- Encryption (S/MIME, OME)
- Shared mailboxes and distribution lists
- Email aliases

#### Below the Line (Out of Scope)
- Calendar/scheduling system (separate design)
- Office Online integration (editing attachments)
- Admin compliance (retention, litigation hold)
- Migration from other email providers

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Registered users** | 400 million mailboxes | Outlook.com + M365 |
| **DAU** | 150 million | Active email users |
| **Emails sent/day** | 3 billion | ~20 emails per active user |
| **Emails received/day** | 50 billion (including spam) | ~80% is spam, filtered out |
| **Email delivery latency** | < 5 seconds (p95) intra-platform | Near-instant for Outlook→Outlook |
| **Search latency** | < 500ms | Responsive search |
| **Availability** | 99.99% | Email is mission-critical |
| **Durability** | Zero email loss | Regulatory + user trust |
| **Storage** | 50 GB per user (M365), 15 GB free | Large mailboxes |
| **Attachment size** | Up to 25 MB per email | Standard email limits |

### Capacity Estimation

```
Emails:
  Sent/day: 3B → 35K/sec avg, ~150K/sec peak
  Received/day: 50B (pre-spam filter) → 580K/sec
  After spam filter: 10B delivered → 116K/sec
  Average email size: 50 KB (headers + body + small attachments)
  Emails with large attachments: 10% → 5 MB avg

Storage:
  Daily new storage: 10B × 50 KB = 500 TB/day
  Attachments: 1B × 5 MB = 5 PB/day
  Yearly: ~2 EB (exabytes) total storage growth
  
  Per-user average mailbox: 5 GB
  Total: 400M × 5 GB = 2 EB

SMTP Connections:
  Inbound SMTP connections: ~100K concurrent
  Outbound SMTP connections: ~50K concurrent

Search Index:
  Total indexed emails: ~2 trillion
  Index size: ~500 PB (compressed)
  Search QPS: 50K queries/sec peak
```

---

## 2️⃣ Core Entities

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│  User        │────▶│  Mailbox     │────▶│  Folder        │
│              │     │               │     │                │
│ userId       │     │ mailboxId     │     │ folderId       │
│ email        │     │ userId        │     │ mailboxId      │
│ displayName  │     │ quotaUsed     │     │ name           │
│ tenantId     │     │ quotaLimit    │     │ type (system/  │
│ aliases[]    │     │ autoReply     │     │  custom)       │
└─────────────┘     │ rules[]       │     │ unreadCount    │
                     │ focusedInbox  │     │ totalCount     │
                     └──────────────┘     └───────────────┘
                                                  │
┌─────────────────────────────────────────────────┘
│
▼
┌───────────────┐     ┌──────────────┐     ┌───────────────┐
│  Email         │     │  Attachment   │     │  Recipient     │
│  (Message)     │     │               │     │                │
│ emailId        │     │ attachmentId  │     │ email          │
│ mailboxId      │     │ emailId       │     │ name           │
│ folderId       │     │ fileName      │     │ type (to/cc/   │
│ conversationId │     │ contentType   │     │  bcc)          │
│ internetMsgId  │     │ size          │     └───────────────┘
│ subject        │     │ blobRef       │
│ bodyPreview    │     │ isInline      │     ┌───────────────┐
│ body (HTML)    │     └──────────────┘     │  SpamVerdict   │
│ from           │                           │                │
│ to[]           │                           │ score          │
│ cc[]           │                           │ classification │
│ bcc[]          │                           │ spfResult      │
│ receivedAt     │                           │ dkimResult     │
│ sentAt         │                           │ dmarcResult    │
│ isRead         │                           │ phishing       │
│ isFlagged      │                           │ bulkMail       │
│ importance     │                           └───────────────┘
│ hasAttachments │
│ headers{}      │
│ size           │
└───────────────┘
```

---

## 3️⃣ API Design

### Send Email
```
POST /api/v1/me/sendMail
Authorization: Bearer <token>

Request:
{
  "message": {
    "subject": "Q1 Report",
    "body": {
      "contentType": "html",
      "content": "<p>Hi team, please find the Q1 report attached.</p>"
    },
    "toRecipients": [
      { "emailAddress": { "address": "alice@contoso.com", "name": "Alice" } }
    ],
    "ccRecipients": [
      { "emailAddress": { "address": "bob@contoso.com" } }
    ],
    "attachments": [
      {
        "name": "Q1-Report.pdf",
        "contentType": "application/pdf",
        "contentBytes": "<base64-encoded>"
      }
    ],
    "importance": "high"
  },
  "saveToSentItems": true
}

Response: 202 Accepted
{
  "messageId": "msg_abc123",
  "status": "queued"
}
```

### List Emails in Folder
```
GET /api/v1/me/mailFolders/{folderId}/messages?$top=50&$orderBy=receivedDateTime desc&$filter=isRead eq false
Authorization: Bearer <token>

Response: 200 OK
{
  "value": [
    {
      "id": "msg_123",
      "subject": "Q1 Report",
      "bodyPreview": "Hi team, please find the Q1 report...",
      "from": { "emailAddress": { "address": "bob@contoso.com", "name": "Bob" } },
      "receivedDateTime": "2025-01-15T10:30:00Z",
      "isRead": false,
      "hasAttachments": true,
      "importance": "high",
      "flag": { "flagStatus": "notFlagged" }
    }
  ],
  "@odata.nextLink": "...?$skipToken=xxx"
}
```

### Search Emails
```
GET /api/v1/me/messages?$search="Q1 report from:bob has:attachment"&$top=25
Authorization: Bearer <token>

Response: 200 OK
{
  "value": [ ...matching emails... ],
  "@odata.count": 142
}
```

---

## 4️⃣ Data Flow

### Sending an Email

```
User clicks Send → Outlook Web Client
        │
        ▼
   API Gateway (Azure Front Door)
        │
        ▼
   Composition Service
        │
        ├── 1. Validate recipients, check sending limits
        ├── 2. Store attachments in Blob Storage (if >3 MB, use reference link)
        ├── 3. Save copy to Sent Items folder (Mailbox DB)
        ├── 4. Enqueue to Outbound Queue (Service Bus)
        │
        ▼
   Outbound SMTP Service
        │
        ├── 1. Resolve recipient MX records (DNS lookup)
        ├── 2. For internal recipients (same platform):
        │       → Direct delivery to recipient's mailbox (bypass SMTP)
        ├── 3. For external recipients:
        │       ├── DKIM sign the email
        │       ├── Establish TLS connection to recipient MX
        │       ├── Send via SMTP (with retry on failure)
        │       └── If bounce → send Non-Delivery Report (NDR) to sender
        │
        └── Track delivery status (sent, delivered, bounced)
```

### Receiving an Email

```
External email server connects via SMTP → Port 25
        │
        ▼
   Inbound SMTP Gateway (Edge Transport)
        │
        ├── 1. TLS handshake
        ├── 2. Check sender IP reputation (IP blocklist)
        ├── 3. Verify SPF record (is sender authorized for domain?)
        ├── 4. Accept SMTP DATA → parse MIME message
        │
        ▼
   Anti-Spam Pipeline
        │
        ├── SPF check ──────────────────────┐
        ├── DKIM signature verification ────┤
        ├── DMARC policy check ─────────────┤
        ├── Content ML classifier ──────────┤──▶ Spam Score
        ├── URL reputation check ───────────┤     (0-100)
        ├── Attachment scan (malware) ──────┤
        └── Phishing detection ─────────────┘
        │
        ├── Score > 80 → Reject (550 error, don't deliver)
        ├── Score 50-80 → Deliver to Junk/Spam folder
        └── Score < 50 → Deliver to Inbox
        │
        ▼
   Delivery Service
        │
        ├── Resolve recipient mailbox (handle aliases, forwarding rules)
        ├── Store email in Mailbox DB (partition by mailboxId)
        ├── Store attachments in Blob Storage
        ├── Index email in Search (async)
        ├── Apply user rules (auto-move, auto-label)
        ├── Update folder counters (unreadCount++)
        └── Push notification to connected clients (WebSocket / push)
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                       │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐        │
│  │  Web App  │  │  Mobile   │  │  Desktop  │  │  IMAP/POP │        │
│  │ (React)   │  │(iOS/And)  │  │ (Outlook) │  │  Clients  │        │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘        │
│        └───────────────┴───────────────┴───────────────┘              │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ HTTPS / IMAP / ActiveSync
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│  ┌────────────────────┐    ┌────────────────────────┐                 │
│  │  Azure Front Door  │    │  API Gateway            │                 │
│  │  (Global LB)       │    │  (Auth, Rate Limit)     │                 │
│  └────────┬───────────┘    └──────────┬─────────────┘                 │
└───────────┴───────────────────────────┴───────────────────────────────┘
                                        │
     ┌──────────────────┬───────────────┼───────────────┬───────────────┐
     ▼                  ▼               ▼               ▼               ▼
┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────┐
│Compose   │  │ Mailbox      │  │ Search   │  │ Inbound SMTP │  │Outbound  │
│Service   │  │ Service      │  │ Service  │  │ Gateway      │  │SMTP Svc  │
│          │  │              │  │          │  │              │  │          │
│ Draft,   │  │ CRUD emails  │  │ Full-text│  │ Receive from │  │ Send to  │
│ send,    │  │ folders,     │  │ search   │  │ external     │  │ external │
│ validate │  │ rules,       │  │ Elastic- │  │ SMTP servers │  │ servers  │
│          │  │ read/flag    │  │ search   │  │ Port 25      │  │ via SMTP │
└────┬─────┘  └──────┬───────┘  └────┬─────┘  └──────┬───────┘  └────┬─────┘
     │               │               │               │               │
     ▼               ▼               ▼               ▼               ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     ANTI-SPAM / SECURITY LAYER                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐    │
│  │ Spam Filter  │  │ Malware      │  │ Authentication           │    │
│  │ (ML Model)   │  │ Scanner      │  │ (SPF/DKIM/DMARC)        │    │
│  │              │  │              │  │                          │    │
│  │ Content +    │  │ Scan attach- │  │ Verify sender identity   │    │
│  │ header +     │  │ ments,       │  │ prevent spoofing         │    │
│  │ reputation   │  │ URLs         │  │                          │    │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          DATA LAYER                                     │
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐       │
│  │ Mailbox DB   │  │ Azure Blob   │  │ Elasticsearch          │       │
│  │ (Cosmos DB / │  │ Storage      │  │                        │       │
│  │  Azure SQL)  │  │              │  │ Full-text search       │       │
│  │              │  │ Attachments  │  │ index per mailbox      │       │
│  │ Emails,      │  │ (large files │  │                        │       │
│  │ folders,     │  │  reference   │  │                        │       │
│  │ metadata     │  │  links)      │  │                        │       │
│  │              │  │              │  │                        │       │
│  │ Partition:   │  │              │  │                        │       │
│  │  mailboxId   │  │              │  │                        │       │
│  └──────────────┘  └──────────────┘  └────────────────────────┘       │
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐       │
│  │ Redis Cache  │  │ Service Bus  │  │ Notification Service   │       │
│  │              │  │ / Kafka      │  │                        │       │
│  │ Session,     │  │              │  │ Push (APNS/FCM/WNS)   │       │
│  │ folder       │  │ Email queues │  │ Real-time via WS       │       │
│  │ counts,      │  │ (inbound,    │  │ Badge counts           │       │
│  │ rate limits  │  │  outbound)   │  │                        │       │
│  └──────────────┘  └──────────────┘  └────────────────────────┘       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Spam Filtering Pipeline

**Problem**: 80% of inbound email is spam (40B+ spam emails/day). We must filter spam with >99.5% accuracy while keeping false positive rate <0.01% (legitimate email marked as spam is very costly).

**Solution: Multi-Layer Spam Classification**

```
Layer 1 — Connection-Level Filtering (before accepting email body):
  ├── IP Reputation: check sender IP against blocklists (Spamhaus, etc.)
  │   Block: known spam IPs → reject immediately (saves bandwidth)
  ├── Rate limiting: max 100 emails/min from single IP
  ├── Reverse DNS: sender IP must have valid PTR record
  └── Greylisting: temporarily reject first attempt from unknown senders
      (legitimate servers retry, spam bots usually don't)
  
  → Filters 60% of spam at connection level (zero storage cost)

Layer 2 — Authentication (after receiving headers):
  ├── SPF: check if sender IP is authorized for domain
  │   Result: pass / fail / softfail / none
  ├── DKIM: verify cryptographic signature on email headers
  │   Result: pass / fail / none
  └── DMARC: combine SPF + DKIM with domain policy
      Action: reject / quarantine / none
  
  → Catches spoofed emails, phishing attempts

Layer 3 — Content Analysis (ML model on full email):
  Features extracted:
    - Subject line patterns ("Congratulations!", "Act now!")
    - Body text (TF-IDF, word embeddings)
    - HTML structure (hidden text, image-to-text ratio)
    - URL analysis (shortened URLs, known malicious domains)
    - Attachment types (executable = high risk)
    - Sender history (first-time sender to this user?)
    - Recipient patterns (BCC to 1000+ users = bulk)
  
  Model: Gradient Boosted Trees (XGBoost) ensemble
    - Trained on billions of labeled examples
    - Updated daily with new spam patterns
    - User feedback loop: "Mark as spam" / "Not spam" retrains
  
  Output: spam_score (0-100)
    0-30: definitely not spam → Inbox
    30-50: probably not spam → Inbox (with caution banner if 40-50)
    50-80: probably spam → Junk folder
    80-100: definitely spam → Reject (don't even store)

Layer 4 — Phishing Detection (separate specialized model):
  ├── Check URLs against known phishing databases
  ├── Visual similarity to known brands (logo detection)
  ├── Urgency language detection ("your account will be suspended")
  └── Sender domain typosquatting detection (micros0ft.com)
  
  If phishing detected → Safety banner: "This email may be a phishing attempt"

Feedback Loop:
  User marks email as "Not Junk" → 
    1. Move to Inbox
    2. Add sender to safe senders list
    3. Send negative feedback to ML training pipeline
    4. If many users report same → adjust model globally
```

---

### Deep Dive 2: Email Storage & Mailbox Partitioning

**Problem**: 400M mailboxes, average 5 GB each, 2 exabytes total. How to store and serve this efficiently?

**Solution: Mailbox-Partitioned Storage with Tiered Architecture**

```
Storage Architecture:

Mailbox DB (Cosmos DB / Azure SQL):
  Partition Key: mailboxId
  
  Why mailboxId?
  - All queries are scoped to a single mailbox
  - Get inbox, search, folder counts — all within one partition
  - Bounded size: 50 GB quota per mailbox
  
  Email document (metadata only, body separate):
  {
    "id": "msg_789",
    "mailboxId": "mb_123",         // partition key
    "folderId": "inbox",
    "conversationId": "conv_456",
    "subject": "Q1 Report",
    "bodyPreview": "Hi team, please find...",
    "from": "bob@contoso.com",
    "receivedAt": "2025-01-15T10:30:00Z",
    "isRead": false,
    "hasAttachments": true,
    "size": 52400,
    "bodyRef": "blob://bodies/mb_123/msg_789",  // body stored in blob
    "spamScore": 12
  }

Body & Attachment Storage (Azure Blob):
  Container: email-bodies
    Blob: {mailboxId}/{emailId}/body.html
    Blob: {mailboxId}/{emailId}/body.txt  (plain text version)
  
  Container: email-attachments
    Blob: {mailboxId}/{emailId}/{attachmentId}/{filename}

Tiering:
  Recent emails (< 30 days): Hot storage (SSD-backed Cosmos DB)
  Older emails (30 days - 1 year): Warm tier (HDD-backed)
  Archive (> 1 year): Cold storage (Azure Blob, cheaper)
  
  On-demand hydration: user scrolls to old email → 
    fetch from cold storage, cache in hot for 24h
```

**Folder Counters (Critical for UI):**
```
Challenge: Inbox shows "Inbox (42)" — unread count must be exact and fast.

Solution: Materialized counters in Redis, backed by DB triggers.

Redis:
  HSET folders:mb_123:inbox unreadCount 42 totalCount 1847

On new email delivered:
  HINCRBY folders:mb_123:inbox unreadCount 1
  HINCRBY folders:mb_123:inbox totalCount 1

On email read:
  HINCRBY folders:mb_123:inbox unreadCount -1

Consistency: 
  Redis is source of truth for counters (fast)
  Background job reconciles with DB every hour (correctness)
  On cache miss: count from DB, populate Redis
```

---

### Deep Dive 3: SMTP Delivery & Reliability

**Problem**: We need to deliver 3B emails/day to external servers. SMTP is unreliable — servers can be down, throttle us, or reject our emails. How to ensure reliable delivery?

**Solution: Queue-Based SMTP with Retry & Reputation Management**

```
Outbound Delivery Pipeline:

Compose Service → Outbound Queue (partitioned by destination domain)
        │
        ▼
   SMTP Sender Workers (auto-scaled)
        │
        ├── 1. Resolve MX record for recipient domain
        │       DNS lookup: gmail.com → alt1.gmail-smtp-in.l.google.com
        │       Cache MX records (TTL from DNS)
        │
        ├── 2. Select sending IP (IP pool management)
        │       - Rotate IPs to avoid reputation damage
        │       - Warm up new IPs gradually (start with 100/day, increase)
        │       - Dedicated IPs for high-volume senders (enterprise)
        │
        ├── 3. Establish SMTP connection
        │       - STARTTLS (opportunistic encryption)
        │       - Connection pooling per destination domain
        │
        ├── 4. Send email (SMTP protocol)
        │       EHLO outlook.com
        │       MAIL FROM:<sender@outlook.com>
        │       RCPT TO:<recipient@gmail.com>
        │       DATA
        │       <email content with DKIM signature>
        │       .
        │       QUIT
        │
        └── 5. Handle response:
                250 OK → delivery success, log
                421 Try again later → requeue with backoff
                450 Mailbox busy → requeue, retry in 5 min
                550 User not found → bounce (send NDR to sender)
                552 Mailbox full → bounce
                554 Rejected (spam) → log, check our reputation

Retry Strategy:
  Attempt 1: immediate
  Attempt 2: 5 minutes
  Attempt 3: 30 minutes
  Attempt 4: 2 hours
  Attempt 5: 8 hours
  Attempt 6: 24 hours
  After 48 hours: give up, send NDR (Non-Delivery Report) to sender

IP Reputation Management:
  Monitor feedback loops (FBL) from major providers:
    - Gmail Postmaster Tools
    - Microsoft SNDS
    - Yahoo CFL
  
  If bounce rate > 5% for an IP → reduce volume, investigate
  If spam complaint rate > 0.1% → pause sending from that IP
  
  Sending rate per destination domain:
    Gmail: max 500/min per IP (their limit)
    Yahoo: max 100/min per IP
    Corporate: varies, respect their throttling signals
```

---

### Deep Dive 4: Search Architecture

**Problem**: Users search across potentially 100K+ emails spanning years. Search must be fast (<500ms), support complex queries, and return results ranked by relevance.

**Solution: Elasticsearch with Per-User Sharding**

```
Indexing Pipeline:

Email delivered → Kafka topic: "email-index"
        │
        ▼
   Search Indexer Service
        │
        ├── Extract searchable text:
        │   - Subject
        │   - Body (strip HTML, extract text)
        │   - Sender name and address
        │   - Recipient names and addresses
        │   - Attachment filenames
        │   - (Optional) OCR on image attachments
        │
        └── Index into Elasticsearch:
            Index: outlook-{shard_id}-{YYYY}
            
            Document:
            {
              "emailId": "msg_789",
              "mailboxId": "mb_123",
              "folderId": "inbox",
              "subject": "Q1 Report",
              "body": "Hi team, please find the Q1 report attached...",
              "from": "bob@contoso.com",
              "fromName": "Bob Smith",
              "to": ["alice@contoso.com"],
              "receivedAt": "2025-01-15T10:30:00Z",
              "hasAttachment": true,
              "attachmentNames": ["Q1-Report.pdf"],
              "isRead": false,
              "importance": "high",
              "conversationId": "conv_456"
            }

Shard Strategy:
  - Shard by mailboxId hash (co-locate all emails of a user)
  - 10,000 shards across ES cluster
  - Each shard: ~40M users / 10K shards = 4K users
  - Monthly index rotation for lifecycle management

Query Translation:
  User types: "Q1 report from:bob has:attachment before:2025-02-01"
  
  Translated to ES query:
  {
    "query": {
      "bool": {
        "must": [
          { "match": { "subject body": "Q1 report" } }
        ],
        "filter": [
          { "term": { "mailboxId": "mb_123" } },
          { "prefix": { "from": "bob" } },
          { "term": { "hasAttachment": true } },
          { "range": { "receivedAt": { "lt": "2025-02-01" } } }
        ]
      }
    },
    "sort": [
      { "_score": "desc" },
      { "receivedAt": "desc" }
    ],
    "size": 25,
    "highlight": { "fields": { "body": {}, "subject": {} } }
  }

Performance Optimizations:
  - Routing key = mailboxId → query hits single shard
  - Bloom filter on mailboxId → skip shards that don't have this user
  - Warm cache: pre-cache frequent queries ("unread", recent inbox)
  - Result snippets computed at query time (highlight)
```

---

### Deep Dive 5: Conversation Threading

**Problem**: Emails are individual messages, but users think in conversations (threads). How do we group related emails into conversation threads?

**Solution: References-Based Threading with Fallback**

```
Email Threading Algorithm:

1. RFC 5322 Threading (primary):
   Every email has headers:
     Message-ID: <unique@server.com>
     In-Reply-To: <parent-message-id@server.com>
     References: <msg1@server.com> <msg2@server.com> <msg3@server.com>
   
   Threading: group emails by References chain
   
   Thread structure:
     msg1 (original)
       └── msg2 (reply to msg1, References: msg1)
            ├── msg3 (reply to msg2, References: msg1, msg2)
            └── msg4 (reply to msg2, References: msg1, msg2)

2. Subject-Based Fallback:
   Some email clients strip References header.
   Fallback: group by normalized subject.
   
   Normalize: strip "Re:", "Fwd:", "RE:", "FW:", trim whitespace
   "RE: RE: FW: Q1 Report" → "Q1 Report"
   
   Group emails with same normalized subject + overlapping participants
   within a 30-day window.

3. Conversation ID Assignment:
   On email receipt:
     a. Check References → find existing conversation
     b. Check In-Reply-To → find parent email's conversation
     c. Check subject + participants → fuzzy match
     d. None found → create new conversation

   Store: conversationId on each email document
   
   Query conversation: 
     GET /messages?$filter=conversationId eq 'conv_456'&$orderBy=receivedDateTime asc

4. Conversation View in UI:
   ┌─────────────────────────────────┐
   │ Q1 Report                 (3)  │  ← conversation header
   │ Bob Smith                      │
   │ Hi team, please find the Q1... │  ← latest message preview
   │ 10:30 AM                       │
   └─────────────────────────────────┘
   
   Expand → shows all 3 emails in thread, newest first
```
