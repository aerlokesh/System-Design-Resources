# 🐘 PostgreSQL Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how PostgreSQL is used in production at companies like Instagram, Stripe, Notion, GitHub, Shopify, and more. Every schema design, query pattern, and indexing decision is explained with the **WHY** behind it.

---

## 📋 Table of Contents

1. [Instagram — Social Graph & Feed Metadata](#1-instagram--social-graph--feed-metadata)
2. [Stripe — Payment Processing & Ledger](#2-stripe--payment-processing--ledger)
3. [Notion — Block-Based Document Storage](#3-notion--block-based-document-storage)
4. [GitHub — Repository & Issue Tracking](#4-github--repository--issue-tracking)
5. [Shopify — Multi-Tenant E-Commerce](#5-shopify--multi-tenant-e-commerce)
6. [Uber — Fare Calculation & Trip Metadata](#6-uber--fare-calculation--trip-metadata)
7. [Reddit — Posts, Comments & Voting (Recursive)](#7-reddit--posts-comments--voting-recursive)
8. [Airbnb — Listing Search & Booking](#8-airbnb--listing-search--booking)
9. [Twitch — Subscription & Revenue Tracking](#9-twitch--subscription--revenue-tracking)
10. [Robinhood — Stock Portfolio & Trade History](#10-robinhood--stock-portfolio--trade-history)
11. [Discord — Message Storage & Full-Text Search](#11-discord--message-storage--full-text-search)
12. [Slack — Workspace, Channels & Permissions](#12-slack--workspace-channels--permissions)
13. [LinkedIn — Job Postings & Applications](#13-linkedin--job-postings--applications)
14. [Calendly — Availability & Booking (Conflict Prevention)](#14-calendly--availability--booking-conflict-prevention)
15. [Duolingo — Analytics & Reporting (OLAP)](#15-duolingo--analytics--reporting-olap)
16. [16–25: Additional Real-World Applications (Summary)](#1625-additional-real-world-applications-summary)
17. [🏆 Cheat Sheet: PostgreSQL Feature Selection Guide](#-cheat-sheet-postgresql-feature-selection-guide)
18. [🎯 Interview Summary: "When Would You Use PostgreSQL?"](#-interview-summary-when-would-you-use-postgresql)

---

## 1. Instagram — Social Graph & Feed Metadata

### 🏗️ Architecture Context

Instagram started as a PostgreSQL shop and still uses it as its primary data store. User profiles, follows, likes, and post metadata all live in Postgres. They sharded across multiple Postgres instances using their open-source tool **pgpartman**.

### 📐 Schema Design

```sql
-- Users table
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(30) UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    bio         TEXT,
    avatar_url  TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    is_private  BOOLEAN DEFAULT FALSE
);

-- Follows (social graph)
CREATE TABLE follows (
    follower_id  BIGINT NOT NULL REFERENCES users(id),
    following_id BIGINT NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id)
);
CREATE INDEX idx_follows_following ON follows(following_id);  -- "Who follows me?"

-- Posts
CREATE TABLE posts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    media_url   TEXT NOT NULL,
    caption     TEXT,
    location    POINT,  -- PostGIS-style geolocation
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_posts_user_created ON posts(user_id, created_at DESC);

-- Likes (composite PK prevents double-liking)
CREATE TABLE likes (
    user_id     BIGINT NOT NULL REFERENCES users(id),
    post_id     BIGINT NOT NULL REFERENCES posts(id),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);
CREATE INDEX idx_likes_post ON likes(post_id);  -- "Who liked this post?"
```

### 💡 Why PostgreSQL?

- **Relational integrity**: Foreign keys ensure user_id in posts always references a valid user
- **Composite primary key** on likes: `(user_id, post_id)` prevents double-liking at the database level
- **Compound index** on posts: `(user_id, created_at DESC)` → get user's feed in one index scan
- Instagram scaled Postgres to **billions of rows** via sharding on user_id

### 🔧 Queries — Step by Step

```sql
-- Get user's feed (their own posts, newest first)
SELECT * FROM posts 
WHERE user_id = 123 
ORDER BY created_at DESC 
LIMIT 20;
-- Uses index: idx_posts_user_created → Index-Only Scan

-- Get follower count
SELECT COUNT(*) FROM follows WHERE following_id = 123;

-- Get home feed (posts from people I follow)
SELECT p.* FROM posts p
JOIN follows f ON p.user_id = f.following_id
WHERE f.follower_id = 123
ORDER BY p.created_at DESC
LIMIT 20;
-- For large follower lists: precompute feed in Redis/DynamoDB (fan-out)

-- Like a post (idempotent — ON CONFLICT)
INSERT INTO likes (user_id, post_id) VALUES (123, 456)
ON CONFLICT (user_id, post_id) DO NOTHING;  -- No error on duplicate

-- Get like count
SELECT COUNT(*) FROM likes WHERE post_id = 456;
-- At scale: use a counter cache column + trigger/application update
```

### 🎯 Why Postgres Over Others?

| Alternative | Problem |
|-------------|---------|
| MongoDB | No referential integrity; denormalized data gets inconsistent |
| DynamoDB | Can't do flexible JOINs for social graph traversal |
| MySQL | Postgres has better JSON, full-text search, and extensibility |

---

## 2. Stripe — Payment Processing & Ledger

### 🏗️ Architecture Context

Stripe processes billions of dollars in payments and uses PostgreSQL as the source of truth for financial data. Accuracy is non-negotiable — every cent must balance.

### 📐 Schema Design

```sql
-- Accounts (merchants)
CREATE TABLE accounts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    currency    VARCHAR(3) DEFAULT 'USD',
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Payments
CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    amount_cents    BIGINT NOT NULL CHECK (amount_cents > 0),  -- NEVER use FLOAT for money!
    currency        VARCHAR(3) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'succeeded', 'failed', 'refunded')),
    idempotency_key VARCHAR(255) UNIQUE,  -- Prevent duplicate charges
    metadata        JSONB,  -- Flexible key-value for customer data
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_payments_account_created ON payments(account_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments(status) WHERE status = 'pending';  -- Partial index!

-- Ledger entries (double-entry bookkeeping)
CREATE TABLE ledger_entries (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  UUID REFERENCES payments(id),
    account_id  UUID NOT NULL REFERENCES accounts(id),
    amount_cents BIGINT NOT NULL,  -- Positive = credit, negative = debit
    entry_type  VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
-- Double-entry: SUM of all entries should always be 0
```

### 💡 Why PostgreSQL for Payments?

| Feature | Why Critical |
|---------|-------------|
| **ACID transactions** | Payment + ledger entry must succeed together or not at all |
| **CHECK constraints** | `amount_cents > 0` enforced at DB level — can't corrupt data |
| **BIGINT for money** | NEVER use FLOAT/DOUBLE — use integer cents (`$19.99 = 1999`) |
| **UNIQUE on idempotency_key** | Duplicate charges impossible at DB level |
| **Partial index** | `WHERE status = 'pending'` → tiny index for just pending payments |
| **JSONB metadata** | Flexible per-payment data without schema migration |
| **Serializable isolation** | Prevents race conditions in balance calculations |

### 🔧 Queries

```sql
-- Process payment (ACID transaction)
BEGIN;
  INSERT INTO payments (account_id, amount_cents, currency, idempotency_key, status)
  VALUES ('acct_123', 1999, 'USD', 'idem_abc', 'succeeded');
  
  INSERT INTO ledger_entries (payment_id, account_id, amount_cents, entry_type)
  VALUES (CURRVAL('payments_id_seq'), 'acct_123', 1999, 'credit');
  
  INSERT INTO ledger_entries (payment_id, account_id, amount_cents, entry_type)
  VALUES (CURRVAL('payments_id_seq'), 'stripe_fees', -1999, 'debit');
COMMIT;
-- Both inserts succeed or BOTH rollback — money never disappears

-- Idempotent charge (retry-safe)
INSERT INTO payments (account_id, amount_cents, currency, idempotency_key)
VALUES ('acct_123', 1999, 'USD', 'idem_abc')
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING *;
-- First call: inserts. Retry calls: return existing, no duplicate charge.

-- Account balance
SELECT SUM(amount_cents) as balance 
FROM ledger_entries 
WHERE account_id = 'acct_123';

-- Monthly revenue report
SELECT DATE_TRUNC('month', created_at) AS month,
       SUM(amount_cents) / 100.0 AS revenue_usd,
       COUNT(*) AS num_payments
FROM payments
WHERE account_id = 'acct_123' AND status = 'succeeded'
GROUP BY DATE_TRUNC('month', created_at)
ORDER BY month DESC;
```

---

## 3. Notion — Block-Based Document Storage

### 🏗️ Architecture Context

Notion stores everything as "blocks" — paragraphs, headings, to-dos, tables. Each block can contain child blocks (recursive tree structure). PostgreSQL's recursive CTEs and JSONB make this elegant.

### 📐 Schema Design

```sql
CREATE TABLE blocks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id     UUID NOT NULL,
    parent_id   UUID REFERENCES blocks(id),  -- Self-referencing (tree structure)
    type        VARCHAR(50) NOT NULL,  -- 'paragraph', 'heading', 'todo', 'table', 'image'
    content     JSONB NOT NULL DEFAULT '{}',  -- Flexible per-type content
    position    INTEGER NOT NULL,  -- Order within parent
    created_by  UUID NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_blocks_page ON blocks(page_id);
CREATE INDEX idx_blocks_parent ON blocks(parent_id, position);
CREATE INDEX idx_blocks_content ON blocks USING GIN (content);  -- Search within JSONB
```

### 🔧 Recursive Query — Get Full Page Tree

```sql
-- Get entire page as nested tree
WITH RECURSIVE page_tree AS (
    -- Base: top-level blocks
    SELECT id, parent_id, type, content, position, 0 AS depth
    FROM blocks
    WHERE page_id = 'page_abc' AND parent_id IS NULL
    
    UNION ALL
    
    -- Recurse: children of each block
    SELECT b.id, b.parent_id, b.type, b.content, b.position, pt.depth + 1
    FROM blocks b
    JOIN page_tree pt ON b.parent_id = pt.id
)
SELECT * FROM page_tree ORDER BY depth, position;

-- Search across all pages for text content
SELECT page_id, type, content->>'text' AS text
FROM blocks
WHERE content->>'text' ILIKE '%quarterly report%';
-- Uses GIN index on JSONB for fast text search
```

### 💡 Why PostgreSQL?

- **Recursive CTEs** handle tree structures naturally (block → child blocks → nested blocks)
- **JSONB** stores different content shapes per block type without schema changes
- **GIN index on JSONB** enables searching inside block content efficiently
- **Foreign key (self-referencing)** ensures block tree integrity

---

## 4. GitHub — Repository & Issue Tracking

### 📐 Schema Design

```sql
CREATE TABLE repositories (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    is_private  BOOLEAN DEFAULT FALSE,
    stars_count INTEGER DEFAULT 0,
    language    VARCHAR(50),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(owner_id, name)
);

CREATE TABLE issues (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     BIGINT NOT NULL REFERENCES repositories(id),
    number      INTEGER NOT NULL,  -- Repo-scoped issue number
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    state       VARCHAR(10) DEFAULT 'open' CHECK (state IN ('open', 'closed')),
    author_id   BIGINT REFERENCES users(id),
    labels      TEXT[],  -- PostgreSQL array type!
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(repo_id, number)
);
CREATE INDEX idx_issues_labels ON issues USING GIN (labels);  -- Array search

CREATE TABLE issue_comments (
    id          BIGSERIAL PRIMARY KEY,
    issue_id    BIGINT NOT NULL REFERENCES issues(id),
    author_id   BIGINT NOT NULL REFERENCES users(id),
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

### 🔧 Key Queries

```sql
-- Search issues by label (using PostgreSQL arrays)
SELECT * FROM issues 
WHERE repo_id = 123 AND labels @> ARRAY['bug']  -- Contains 'bug' label
AND state = 'open'
ORDER BY created_at DESC;

-- Issue number auto-increment per repo (advisory lock)
INSERT INTO issues (repo_id, number, title, author_id)
VALUES (123, 
  (SELECT COALESCE(MAX(number), 0) + 1 FROM issues WHERE repo_id = 123),
  'Bug: login fails', 456);

-- Full-text search across issues
SELECT * FROM issues
WHERE to_tsvector('english', title || ' ' || COALESCE(body, '')) 
      @@ to_tsquery('english', 'login & fails')
ORDER BY ts_rank(to_tsvector('english', title || ' ' || body), 
                 to_tsquery('english', 'login & fails')) DESC;
```

### 💡 PostgreSQL-Specific Features Used

- **Array columns** (`TEXT[]`) for labels — no junction table needed, GIN-indexed
- **Full-text search** built-in — no Elasticsearch needed for basic search
- **Advisory locks** for sequence generation (issue number per repo)
- **UNIQUE constraints** enforce business rules (one "repo#42" per repository)

---

## 5. Shopify — Multi-Tenant E-Commerce

### 📐 Schema Design (Tenant Isolation)

```sql
-- Every table has shop_id for tenant isolation
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    shop_id     BIGINT NOT NULL REFERENCES shops(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
    inventory   INTEGER NOT NULL DEFAULT 0,
    tags        TEXT[],
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
-- Row-Level Security (RLS) for tenant isolation
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
CREATE POLICY shop_isolation ON products
    USING (shop_id = current_setting('app.current_shop_id')::BIGINT);

-- Orders with inventory check
CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    shop_id     BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    total_cents BIGINT NOT NULL,
    status      VARCHAR(20) DEFAULT 'pending',
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

### 🔧 Atomic Inventory Decrement

```sql
-- Purchase product — atomic inventory check + decrement
UPDATE products 
SET inventory = inventory - 1
WHERE id = 456 AND shop_id = 123 AND inventory > 0
RETURNING id;
-- If no rows returned → out of stock (0 rows updated)
-- No race condition — UPDATE locks the row
```

### 💡 Why PostgreSQL?

- **Row-Level Security (RLS)** → tenant isolation at the database level
- **CHECK constraints** → `inventory >= 0` enforced at DB level
- **UPDATE with WHERE** → atomic inventory decrement, no application-level locking

---

## 6. Uber — Fare Calculation & Trip Metadata

### 📐 Schema (PostGIS for Geospatial)

```sql
CREATE EXTENSION postgis;

CREATE TABLE trips (
    id              UUID PRIMARY KEY,
    rider_id        UUID NOT NULL,
    driver_id       UUID,
    pickup_point    GEOGRAPHY(POINT, 4326) NOT NULL,
    dropoff_point   GEOGRAPHY(POINT, 4326),
    route           GEOGRAPHY(LINESTRING, 4326),  -- GPS path
    distance_meters DOUBLE PRECISION,
    fare_cents      BIGINT,
    status          VARCHAR(20) NOT NULL,
    requested_at    TIMESTAMPTZ DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX idx_trips_pickup_geo ON trips USING GIST (pickup_point);

-- Find nearby drivers
SELECT driver_id, 
       ST_Distance(current_location, ST_MakePoint(-73.985, 40.748)::GEOGRAPHY) AS distance_m
FROM driver_locations
WHERE ST_DWithin(current_location, ST_MakePoint(-73.985, 40.748)::GEOGRAPHY, 5000)  -- 5km radius
ORDER BY distance_m
LIMIT 10;
```

### 💡 Why PostgreSQL + PostGIS?

- **PostGIS** = best-in-class geospatial engine (used by Uber, Mapbox, OpenStreetMap)
- **GIST indexes** on geography columns → spatial queries in milliseconds
- `ST_DWithin()` for radius search, `ST_Distance()` for exact distance
- Route stored as `LINESTRING` → calculate actual trip distance, not straight-line

---

## 7. Reddit — Posts, Comments & Voting (Recursive)

### 📐 Schema

```sql
CREATE TABLE comments (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT NOT NULL REFERENCES posts(id),
    parent_id   BIGINT REFERENCES comments(id),  -- Null = top-level
    author_id   BIGINT NOT NULL,
    body        TEXT NOT NULL,
    score       INTEGER DEFAULT 0,  -- upvotes - downvotes
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE votes (
    user_id     BIGINT NOT NULL,
    comment_id  BIGINT NOT NULL REFERENCES comments(id),
    direction   SMALLINT NOT NULL CHECK (direction IN (-1, 1)),
    PRIMARY KEY (user_id, comment_id)
);
```

### 🔧 Recursive Comment Thread

```sql
WITH RECURSIVE thread AS (
    SELECT id, parent_id, body, score, 0 AS depth
    FROM comments WHERE post_id = 789 AND parent_id IS NULL
    
    UNION ALL
    
    SELECT c.id, c.parent_id, c.body, c.score, t.depth + 1
    FROM comments c
    JOIN thread t ON c.parent_id = t.id
)
SELECT * FROM thread ORDER BY depth, score DESC;

-- Upvote (upsert — change vote or insert new)
INSERT INTO votes (user_id, comment_id, direction)
VALUES (123, 456, 1)
ON CONFLICT (user_id, comment_id)
DO UPDATE SET direction = EXCLUDED.direction;
```

---

## 8. Airbnb — Listing Search & Booking

### 🔧 Availability & Date Range Queries

```sql
-- Range type for date availability
CREATE TABLE availability (
    listing_id  BIGINT NOT NULL,
    date_range  DATERANGE NOT NULL,
    price_cents BIGINT NOT NULL,
    EXCLUDE USING GIST (listing_id WITH =, date_range WITH &&)  -- Prevent overlapping bookings!
);

-- Search available listings for date range
SELECT l.* FROM listings l
WHERE NOT EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.listing_id = l.id
    AND b.date_range && daterange('2024-06-15', '2024-06-20')  -- Overlap check
)
AND ST_DWithin(l.location, ST_MakePoint(-73.98, 40.74)::GEOGRAPHY, 10000);  -- 10km radius
```

### 💡 PostgreSQL Exclusive Features

- **Range types** (`DATERANGE`) with **exclusion constraints** → prevent overlapping bookings at DB level
- **`&&` operator** checks date range overlap — no complex application logic needed
- Combined with PostGIS spatial search in one query

---

## 9. Twitch — Subscription & Revenue Tracking

### 📐 Schema

```sql
CREATE TABLE subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT NOT NULL,
    streamer_id   BIGINT NOT NULL,
    tier          SMALLINT NOT NULL CHECK (tier IN (1, 2, 3)),
    amount_cents  BIGINT NOT NULL,
    started_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    is_gift       BOOLEAN DEFAULT FALSE,
    UNIQUE(subscriber_id, streamer_id)  -- One active sub per streamer
);

-- Revenue analytics
SELECT DATE_TRUNC('month', started_at) AS month,
       tier,
       COUNT(*) AS subs,
       SUM(amount_cents) / 100.0 AS revenue_usd
FROM subscriptions
WHERE streamer_id = 456
GROUP BY GROUPING SETS (
    (DATE_TRUNC('month', started_at), tier),  -- By month + tier
    (DATE_TRUNC('month', started_at)),         -- By month total
    ()                                          -- Grand total
)
ORDER BY month DESC, tier;
```

### 💡 Why?
- **GROUPING SETS** → multiple aggregation levels in one query (month+tier, month total, grand total)
- **UNIQUE constraint** prevents duplicate subscriptions at DB level

---

## 10. Robinhood — Stock Portfolio & Trade History

### 📐 Schema

```sql
CREATE TABLE trades (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    symbol      VARCHAR(10) NOT NULL,
    side        VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity    DECIMAL(18,8) NOT NULL CHECK (quantity > 0),
    price_cents BIGINT NOT NULL,
    executed_at TIMESTAMPTZ DEFAULT NOW()
);
-- Partitioned by month for performance
CREATE TABLE trades_2024_01 PARTITION OF trades 
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- Portfolio view (materialized for performance)
CREATE MATERIALIZED VIEW portfolio AS
SELECT user_id, symbol,
       SUM(CASE WHEN side = 'BUY' THEN quantity ELSE -quantity END) AS shares,
       SUM(CASE WHEN side = 'BUY' THEN quantity * price_cents ELSE -quantity * price_cents END) 
         / NULLIF(SUM(CASE WHEN side = 'BUY' THEN quantity ELSE -quantity END), 0) AS avg_cost_cents
FROM trades
GROUP BY user_id, symbol
HAVING SUM(CASE WHEN side = 'BUY' THEN quantity ELSE -quantity END) > 0;
```

### 💡 PostgreSQL Features

- **Table partitioning** by date → fast queries on recent trades, easy archival of old data
- **Materialized views** → precomputed portfolio calculations, refreshed periodically
- **DECIMAL** for precise share quantities (crypto can have 8 decimal places)

---

## 11. Discord — Message Storage & Full-Text Search

```sql
-- Messages partitioned by channel + time
CREATE TABLE messages (
    id          BIGINT PRIMARY KEY,  -- Snowflake ID (encodes timestamp)
    channel_id  BIGINT NOT NULL,
    author_id   BIGINT NOT NULL,
    content     TEXT,
    search_vec  TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at  TIMESTAMPTZ DEFAULT NOW()
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_messages_channel ON messages(channel_id, created_at DESC);
CREATE INDEX idx_messages_search ON messages USING GIN (search_vec);

-- Full-text search within a channel
SELECT * FROM messages
WHERE channel_id = 123 AND search_vec @@ to_tsquery('english', 'deployment & issue')
ORDER BY created_at DESC LIMIT 20;
```

---

## 12. Slack — Workspace, Channels & Permissions

```sql
-- RBAC with PostgreSQL
CREATE TABLE channel_members (
    channel_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    role        VARCHAR(20) DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member', 'guest')),
    joined_at   TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (channel_id, user_id)
);

-- Check permission (using EXISTS for performance)
SELECT EXISTS (
    SELECT 1 FROM channel_members
    WHERE channel_id = 123 AND user_id = 456 AND role IN ('owner', 'admin')
) AS is_admin;
```

---

## 13. LinkedIn — Job Postings & Applications

```sql
CREATE TABLE jobs (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    location    VARCHAR(255),
    salary_range INT4RANGE,  -- PostgreSQL range: [80000, 120000)
    skills      TEXT[],
    posted_at   TIMESTAMPTZ DEFAULT NOW(),
    expires_at  TIMESTAMPTZ
);
CREATE INDEX idx_jobs_skills ON jobs USING GIN (skills);
CREATE INDEX idx_jobs_salary ON jobs USING GIST (salary_range);

-- Find jobs matching skills AND salary range
SELECT * FROM jobs
WHERE skills && ARRAY['java', 'postgresql']  -- Has ANY of these skills
AND salary_range @> 100000  -- Range contains 100K
AND expires_at > NOW()
ORDER BY posted_at DESC;
```

---

## 14. Calendly — Availability & Booking (Conflict Prevention)

```sql
CREATE TABLE bookings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id     UUID NOT NULL,
    guest_email VARCHAR(255) NOT NULL,
    time_slot   TSTZRANGE NOT NULL,  -- [start, end)
    EXCLUDE USING GIST (host_id WITH =, time_slot WITH &&)  -- NO overlapping bookings!
);

-- This INSERT will FAIL if it overlaps any existing booking
INSERT INTO bookings (host_id, guest_email, time_slot)
VALUES ('host_abc', 'guest@email.com', tstzrange('2024-06-15 14:00', '2024-06-15 15:00'));
-- If overlap exists → exclusion constraint violation → no double booking possible
```

### 💡 Why This Is Brilliant
- **Exclusion constraints** enforce "no overlapping time ranges per host" AT THE DATABASE LEVEL
- No application-level race conditions — the DB prevents it with a GIST index
- This is impossible in DynamoDB or MongoDB without complex distributed locking

---

## 15. Duolingo — Analytics & Reporting (OLAP)

```sql
-- Window functions for streak analysis
SELECT user_id, 
       active_date,
       active_date - (ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY active_date))::INTEGER 
         AS streak_group
FROM daily_activity
WHERE user_id = 123;

-- Engagement funnel (CTEs for readability)
WITH signups AS (
    SELECT COUNT(*) as total FROM users WHERE created_at >= '2024-01-01'
),
first_lesson AS (
    SELECT COUNT(DISTINCT user_id) as total FROM lessons 
    WHERE user_id IN (SELECT id FROM users WHERE created_at >= '2024-01-01')
),
day7_retention AS (
    SELECT COUNT(DISTINCT user_id) as total FROM daily_activity
    WHERE active_date = created_at::DATE + 7
    AND user_id IN (SELECT id FROM users WHERE created_at >= '2024-01-01')
)
SELECT 
    s.total AS signups,
    f.total AS completed_first_lesson,
    ROUND(f.total::NUMERIC / s.total * 100, 1) AS activation_pct,
    d.total AS retained_day7,
    ROUND(d.total::NUMERIC / s.total * 100, 1) AS retention_pct
FROM signups s, first_lesson f, day7_retention d;
```

---

## 16–25: Additional Real-World Applications (Summary)

### 16. Heroku — Add-on Marketplace Billing
```sql
-- SERIALIZABLE transactions for billing calculations
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
-- Prevents phantom reads during invoice generation
```

### 17. GitLab — CI/CD Pipeline Tracking
```sql
-- State machine with CHECK constraint
status VARCHAR(20) CHECK (status IN ('pending','running','success','failed','canceled'))
-- Enum-like safety without enum type rigidity
```

### 18. Figma — Design Version History
```sql
-- JSONB for storing design tree diffs
-- JSONB patch operations: jsonb_set(), jsonb_insert(), ||
```

### 19. Datadog — Time-Series Metrics (with TimescaleDB)
```sql
-- TimescaleDB extension: hypertables for time-series
SELECT time_bucket('5 minutes', time) AS bucket, AVG(value)
FROM metrics WHERE time > NOW() - INTERVAL '1 hour'
GROUP BY bucket ORDER BY bucket;
```

### 20. Auth0 — Tenant Configuration
```sql
-- JSONB for flexible per-tenant settings
-- RLS policies for multi-tenant isolation
```

### 21. Plaid — Financial Account Linking
```sql
-- Encrypted columns with pgcrypto
SELECT pgp_sym_decrypt(account_number, 'encryption_key') FROM accounts;
```

### 22. Zendesk — Ticket Queue & SLA Tracking
```sql
-- Window functions for SLA breach detection
SELECT *, EXTRACT(EPOCH FROM NOW() - created_at) / 3600 AS hours_open,
       CASE WHEN NOW() > sla_deadline THEN true ELSE false END AS breached
FROM tickets WHERE status = 'open';
```

### 23. Canva — Template & Asset Management
```sql
-- Full-text search with ranking
SELECT *, ts_rank(search_vec, query) AS rank
FROM templates, to_tsquery('english', 'business & presentation') query
WHERE search_vec @@ query ORDER BY rank DESC;
```

### 24. Strava — Activity Tracking (Geospatial)
```sql
-- PostGIS for route analysis
SELECT ST_Length(route::GEOGRAPHY) AS distance_meters,
       ST_Envelope(route) AS bounding_box
FROM activities WHERE user_id = 123;
```

### 25. Square — POS Transactions (Multi-Location)
```sql
-- Partitioning by location_id for multi-store isolation
CREATE TABLE transactions (...) PARTITION BY LIST (location_id);
```

---

## 🏆 Cheat Sheet: PostgreSQL Feature Selection Guide

```
┌────────────────────────────────────────────────────────────────────────┐
│           POSTGRESQL FEATURE SELECTION GUIDE                           │
├────────────────────────────────────────────────────────────────────────┤
│ Need                          → PostgreSQL Feature                    │
│───────────────────────────────────────────────────────────────────────│
│ Flexible per-row schema       → JSONB columns + GIN index             │
│ Geospatial queries            → PostGIS extension (GIST index)        │
│ Full-text search              → tsvector/tsquery + GIN index          │
│ Prevent overlapping ranges    → Exclusion constraints (GIST)          │
│ Tree/recursive data           → Recursive CTEs (WITH RECURSIVE)       │
│ Multi-level aggregation       → GROUPING SETS / CUBE / ROLLUP        │
│ Ranking & analytics           → Window functions (ROW_NUMBER, RANK)   │
│ Multi-tenant isolation        → Row-Level Security (RLS)              │
│ Time-series data              → Table partitioning + TimescaleDB      │
│ Upsert (insert or update)     → ON CONFLICT DO UPDATE                 │
│ Array/tag data                → Array columns + GIN index             │
│ Precomputed results           → Materialized views                    │
│ Prevent duplicates            → UNIQUE constraints / ON CONFLICT      │
│ Money/financial data          → BIGINT cents + CHECK constraints      │
│ Encryption at rest            → pgcrypto extension                    │
│ Scalable time-range queries   → Table partitioning by date            │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary: "When Would You Use PostgreSQL?"

> *"I'd choose PostgreSQL when I need **ACID transactions, complex queries, joins, aggregations, or strong data integrity**. It's the best choice for financial systems (double-entry ledger), multi-tenant SaaS (Row-Level Security), geospatial applications (PostGIS), content with flexible schema (JSONB), and any system where data correctness is more important than raw write throughput. PostgreSQL's exclusion constraints, recursive CTEs, full-text search, and window functions eliminate the need for many external services. I'd pair it with Redis for caching hot data and consider DynamoDB only for specific access patterns that need unlimited horizontal scale with single-digit ms latency."*

### PostgreSQL vs Alternatives — When to Choose What

| Use Case | PostgreSQL ✅ | DynamoDB ✅ | MongoDB ✅ |
|----------|-------------|-----------|-----------|
| Financial ledger / payments | ✅ ACID, CHECK, double-entry | ❌ No JOINs or transactions across items | ❌ Weak transactions |
| Complex reporting & analytics | ✅ SQL, JOINs, window functions | ❌ Scan-only, no aggregates | ⚠️ Limited aggregation |
| Geospatial / maps | ✅ PostGIS (best in class) | ❌ No geo support | ⚠️ Basic 2dsphere |
| Multi-tenant SaaS | ✅ RLS, schemas | ⚠️ Partition by tenant | ⚠️ Application-level |
| High-scale key-value | ❌ Vertical scaling limits | ✅ Unlimited horizontal scale | ✅ Sharded |
| Flexible document store | ✅ JSONB (surprisingly good) | ✅ Native document model | ✅ Native |
| Session store | ⚠️ Possible but overkill | ✅ With TTL | ✅ With TTL |
| Full-text search | ✅ Built-in (good enough) | ❌ Not available | ⚠️ Atlas Search |
| Prevent overlapping bookings | ✅ Exclusion constraints | ❌ Complex application logic | ❌ Complex application logic |

---

> **Related Topics**: [PostgreSQL Deep Dive →](./50-postgresql-deep-dive.md) | [DynamoDB Real-World →](./49c-dynamodb-real-world-applications.md) | [Redis Real-World →](./40b-redis-real-world-applications.md) | [SQL vs NoSQL →](./03-sql-vs-nosql.md)
