# Music Streaming Platform - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [Streaming Flow](#streaming-flow)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a scalable music streaming platform (like Spotify, Apple Music) that supports:
- Music playback with high audio quality
- Personalized recommendations
- Playlist creation and management
- Social features (follow artists, share music)
- Offline downloads
- Multi-device synchronization
- Artist analytics
- Podcast support
- Real-time lyrics
- Cross-platform support (Web, iOS, Android, Desktop)

### Scale Requirements
- **500 million registered users**
- **100 million daily active users (DAU)**
- **50 million concurrent streams during peak hours**
- **100 million songs in catalog**
- **1 billion playlists**
- **10 billion listening sessions per month**
- **Peak bandwidth: 500 Gbps**
- **99.9% uptime**
- **Audio buffering: < 2 seconds**
- **Search latency: < 100ms**
- **Recommendation generation: < 500ms**

---

## Functional Requirements

### Must Have (P0)

#### 1. **Music Playback**
- Stream audio in multiple qualities (64kbps, 128kbps, 256kbps, 320kbps)
- Adaptive bitrate streaming
- Gapless playback between tracks
- Crossfade between songs
- Playback controls (play, pause, skip, seek, volume)
- Queue management
- Shuffle and repeat modes
- Audio normalization
- Equalizer settings

#### 2. **Music Catalog**
- Browse songs, albums, artists, genres
- Search with autocomplete
- Filter and sort options
- New releases section
- Charts (Top 100, Trending)
- Genre-based discovery
- Artist pages with discography
- Album pages with track listings

#### 3. **Playlist Management**
- Create, edit, delete playlists
- Add/remove songs from playlists
- Collaborative playlists
- Reorder tracks
- Playlist folders
- Import playlists from other platforms
- Public vs private playlists
- Playlist cover art

#### 4. **User Library**
- Save favorite songs
- Save albums
- Follow artists
- Recently played history
- Listening statistics
- Your top artists/songs
- Collection organization

#### 5. **Recommendations**
- Personalized homepage
- Discover Weekly playlist
- Daily Mix playlists
- Release Radar (new music from followed artists)
- Similar songs/artists
- Radio stations based on songs/artists
- AI-powered recommendations

#### 6. **Social Features**
- Follow other users
- Share songs/playlists/albums
- Collaborative playlists
- Friend activity feed
- Public profiles
- Social media integration
- Listening parties (group sessions)

#### 7. **Offline Mode**
- Download songs for offline listening
- Download playlists/albums
- Automatic download of liked songs
- Storage management
- Sync across devices

#### 8. **Search**
- Search songs, albums, artists, playlists, podcasts
- Voice search
- Search history
- Trending searches
- Search filters (by type, year, genre)
- Fuzzy matching
- Autocomplete suggestions

### Nice to Have (P1)
- Podcasts support
- Lyrics display (synced)
- Music videos
- Live concert streams
- Behind-the-scenes content
- Artist merchandise integration
- Concert ticket integration
- Canvas (looping visuals)
- Collaborative queue
- Car mode
- Sleep timer
- Karaoke mode
- DJ mode (smooth transitions)

---

## Non-Functional Requirements

### Performance
- **Audio buffering**: < 2 seconds on fast connections
- **Search latency**: < 100ms (p95)
- **Recommendation generation**: < 500ms
- **Playlist load time**: < 300ms
- **App startup**: < 1 second
- **Song metadata load**: < 50ms
- **CDN cache hit rate**: > 95%

### Scalability
- Support 500M users
- Handle 50M concurrent streams (peak)
- 100M songs in catalog
- 1B playlists
- 10B listening sessions/month
- Scale to 1B users in 3 years

### Availability
- **99.9% uptime** (8.76 hours downtime/year)
- Multi-region deployment
- CDN for audio delivery
- Graceful degradation
- Offline mode as fallback

### Audio Quality
- **Bitrates**: 64kbps (low), 128kbps (normal), 256kbps (high), 320kbps (premium)
- **Formats**: AAC, Ogg Vorbis, FLAC (lossless)
- **Sample rate**: 44.1 kHz standard, 48 kHz high-quality
- **Latency**: < 200ms from play to audio start
- **Buffer underrun**: < 0.1% of playback time

### Storage
- **Audio files**: Distributed across multiple regions
- **Metadata**: Highly available databases
- **User data**: Encrypted at rest
- **Cache**: Multi-tier (Memory, SSD, HDD)
- **Backup**: Daily snapshots, 30-day retention

### Security
- **Encryption**: TLS 1.3 for all connections
- **DRM**: Encrypted audio streaming
- **Authentication**: OAuth 2.0, JWT tokens
- **API rate limiting**: Per user, per IP
- **Content protection**: Watermarking
- **License management**: Track plays per license

### Consistency
- **User library**: Strong consistency
- **Playback state**: Eventual consistency across devices
- **Recommendations**: Eventual consistency (daily updates)
- **Play counts**: Eventual consistency

---

## Capacity Estimation

### Traffic Estimates
```
Registered Users: 500M
Daily Active Users (DAU): 100M (20% of total)
Concurrent Peak Users: 50M (peak hours)

Listening Sessions:
- Average session duration: 45 minutes
- Sessions per user per day: 3
- Total sessions/day: 100M × 3 = 300M
- Total sessions/month: 300M × 30 = 9B ≈ 10B

Songs Played:
- Average songs per session: 12 songs
- Total songs/day: 300M × 12 = 3.6B
- Songs/second (average): 3.6B / 86,400 ≈ 41,667
- Songs/second (peak - 3x): 125,000

Search Queries:
- Searches per user per day: 5
- Total searches/day: 100M × 5 = 500M
- Searches/second: 500M / 86,400 ≈ 5,787 QPS
- Peak (5x): 28,935 QPS

Recommendations:
- Generated per user per day: 1
- Total recommendations/day: 100M
- Recommendations/second: 100M / 86,400 ≈ 1,157 QPS
```

### Storage Estimates

**Audio Files**:
```
Catalog size: 100M songs
Average song duration: 3.5 minutes
Average file sizes by quality:
- 64 kbps: 1.7 MB per song
- 128 kbps: 3.4 MB per song
- 256 kbps: 6.7 MB per song
- 320 kbps: 8.4 MB per song

Storage per quality:
- 64 kbps: 100M × 1.7 MB = 170 TB
- 128 kbps: 100M × 3.4 MB = 340 TB
- 256 kbps: 100M × 6.7 MB = 670 TB
- 320 kbps: 100M × 8.4 MB = 840 TB

Total storage (all qualities): 170 + 340 + 670 + 840 = 2,020 TB ≈ 2 PB

With replication (3x) and format variants: 6 PB
```

**Metadata**:
```
Song metadata:
{
  song_id: 16 bytes
  title: 200 bytes
  artist_id: 16 bytes
  album_id: 16 bytes
  duration: 4 bytes
  genre: 50 bytes
  year: 2 bytes
  lyrics_id: 16 bytes
  cover_art_url: 200 bytes
  audio_urls: 500 bytes (multiple qualities)
}
Total per song: ~1 KB

Total songs: 100M × 1 KB = 100 GB
With indexes (5x): 500 GB
With replication (3x): 1.5 TB
```

**User Data**:
```
User profile:
{
  user_id: 16 bytes
  username: 50 bytes
  email: 100 bytes
  subscription: 20 bytes
  preferences: 500 bytes
  created_at: 8 bytes
}
Total per user: ~700 bytes

Total users: 500M × 700 bytes = 350 GB
With replication: 1 TB

User library (saved songs):
- Average songs per user: 500
- Storage per song reference: 16 bytes
- Per user: 500 × 16 bytes = 8 KB
- Total: 500M × 8 KB = 4 TB
- With replication: 12 TB
```

**Playlists**:
```
Playlist metadata:
{
  playlist_id: 16 bytes
  name: 200 bytes
  description: 500 bytes
  owner_id: 16 bytes
  created_at: 8 bytes
  updated_at: 8 bytes
  cover_image: 200 bytes
}
Total per playlist: ~1 KB

Playlist tracks:
- Average tracks per playlist: 50
- Per track: 16 bytes (song_id)
- Per playlist: 50 × 16 = 800 bytes

Total per playlist: 1.8 KB
Total playlists: 1B × 1.8 KB = 1.8 TB
With replication: 5.4 TB
```

**Listening History**:
```
Play event:
{
  event_id: 16 bytes
  user_id: 16 bytes
  song_id: 16 bytes
  timestamp: 8 bytes
  duration_played: 4 bytes
  device: 50 bytes
  location: 50 bytes
}
Total per event: ~160 bytes

Daily plays: 3.6B × 160 bytes = 576 GB/day
Monthly: 576 GB × 30 = 17.3 TB/month
With 90-day retention: 51.9 TB
With replication: 155.7 TB
```

**Total Storage**:
```
Audio files: 6 PB
Song metadata: 1.5 TB
User data: 13 TB
Playlists: 5.4 TB
Listening history (90 days): 155.7 TB
Recommendations cache: 50 TB
Album art/images: 100 TB
───────────────────────────
Total: ~6.3 PB
```

### Bandwidth Estimates
```
Audio Streaming:
Average bitrate: 128 kbps (most common)
Concurrent users (peak): 50M
Bandwidth per stream: 128 kbps = 16 KB/s

Peak bandwidth: 50M × 16 KB/s = 800 GB/s ≈ 6.4 Tbps

Reality check with CDN:
- CDN cache hit rate: 95%
- Origin bandwidth: 6.4 Tbps × 5% = 320 Gbps
- CDN bandwidth: 6.4 Tbps × 95% = 6.08 Tbps

Daily bandwidth:
- Average concurrent users: 20M
- Bandwidth: 20M × 16 KB/s = 320 GB/s
- Daily: 320 GB/s × 86,400 = 27.6 PB/day
```

### CDN Estimates
```
CDN PoPs (Points of Presence): 200+ locations globally

Cache size per PoP:
- Hot content: 10 TB (top 1M songs)
- Warm content: 50 TB
- Total per PoP: 60 TB

Total CDN cache: 200 × 60 TB = 12 PB

Cache hit breakdown:
- Hot songs (top 10%): 80% of plays, 99% cache hit
- Warm songs: 15% of plays, 90% cache hit
- Cold songs: 5% of plays, 60% cache hit
- Overall cache hit rate: ~95%
```

### Server Estimates
```
API Servers:
- Handle metadata requests, searches, recommendations
- Each server: 5K QPS
- Search peak: 29K QPS → 6 servers
- With redundancy (5x): 30 servers per region
- 3 regions: 90 servers

Streaming Servers (Origin):
- Handle 5% of streams (CDN misses)
- Streams: 50M × 5% = 2.5M concurrent
- Each server: 10K concurrent streams
- Servers needed: 2.5M / 10K = 250 servers
- With redundancy (2x): 500 servers

Recommendation Engine:
- Process 1,157 recommendations/second
- Each server: 200 recommendations/second
- Servers needed: 6 servers
- With redundancy (3x): 18 servers

Search Indexing:
- Elasticsearch cluster
- 100M songs + metadata
- Each node: 10M documents
- Nodes needed: 10 nodes
- With replication (3x): 30 nodes

Total infrastructure:
- API servers: 90
- Streaming origin: 500
- Recommendation: 18
- Search: 30
- Database clusters: 50
- Total: ~700 servers

Cost estimation (AWS + CDN):
- Origin servers: 700 × $200/month = $140K/month
- CDN bandwidth: 27 PB/day × $0.02/GB = $16.2M/month
- Storage (S3): 6.3 PB × $23/TB = $145K/month
- Database: $100K/month
- Total: ~$16.6M/month
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                      CLIENTS                               │
│  Web Player | iOS App | Android App | Desktop App         │
└────────────────────────┬───────────────────────────────────┘
                         │ (HTTPS)
                         ↓
┌────────────────────────────────────────────────────────────┐
│                   CDN (CloudFront / Akamai)                │
│  - Audio file delivery (95% cache hit)                     │
│  - Album art, images                                       │
│  - 200+ PoPs globally                                      │
│  - DRM-encrypted streams                                   │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ↓
┌────────────────────────────────────────────────────────────┐
│                   LOAD BALANCER (ALB)                      │
│  - SSL termination                                         │
│  - Geographic routing                                      │
│  - Health checks                                           │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┬──────────────┐
          ↓              ↓              ↓              ↓
┌──────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────────┐
│   Metadata   │ │   Streaming  │ │   Search    │ │Recommendation│
│   Service    │ │   Service    │ │   Service   │ │   Service    │
│              │ │              │ │             │ │              │
│ - Songs      │ │ - Audio URLs │ │ - Songs     │ │ - ML models  │
│ - Albums     │ │ - Playback   │ │ - Artists   │ │ - User taste │
│ - Artists    │ │ - Quality    │ │ - Playlists │ │ - Similar    │
│ - Playlists  │ │ - CDN tokens │ │ - Elastic   │ │   songs      │
└──────┬───────┘ └──────┬───────┘ └─────┬───────┘ └──────┬───────┘
       │                │                │                │
       └────────────────┼────────────────┼────────────────┘
                        │                │
                        ↓                ↓
┌────────────────────────────────────────────────────────────┐
│              CACHE LAYER (Redis Cluster)                   │
│  - Song metadata (1 hour TTL)                              │
│  - User sessions (24 hour TTL)                             │
│  - Trending charts (15 min TTL)                            │
│  - Recommendation cache (1 day TTL)                        │
│  - Search autocomplete (1 hour TTL)                        │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┬──────────────┐
          ↓              ↓              ↓              ↓
┌──────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────────┐
│   Catalog    │ │     User     │ │  Listening  │ │   Playlist   │
│   Database   │ │   Database   │ │   Events    │ │   Database   │
│              │ │              │ │             │ │              │
│ (PostgreSQL) │ │ (PostgreSQL) │ │ (Cassandra) │ │ (PostgreSQL) │
│              │ │              │ │             │ │              │
│ - Songs      │ │ - Users      │ │ - Plays     │ │ - Playlists  │
│ - Albums     │ │ - Subs       │ │ - Skips     │ │ - Tracks     │
│ - Artists    │ │ - Library    │ │ - Likes     │ │ - Followers  │
│ - Genres     │ │ - Following  │ │ - Events    │ │ - Collabs    │
└──────────────┘ └──────────────┘ └─────────────┘ └──────────────┘

┌────────────────────────────────────────────────────────────┐
│                AUDIO STORAGE (S3 / Cloud Storage)          │
│  - Multi-region replication                                │
│  - Multiple bitrate versions (64/128/256/320 kbps)         │
│  - DRM-encrypted files                                     │
│  - Lifecycle policies (glacier for old content)            │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              RECOMMENDATION ENGINE                         │
│  - Collaborative filtering                                 │
│  - Content-based filtering                                 │
│  - Neural networks (deep learning)                         │
│  - Batch processing (Spark)                                │
│  - Real-time scoring                                       │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              ANALYTICS PIPELINE                            │
│  Kafka → Spark Streaming → Data Lake (S3) → Redshift      │
│  - Real-time play tracking                                 │
│  - Artist royalties                                        │
│  - User behavior analysis                                  │
│  - A/B testing metrics                                     │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              SEARCH ENGINE (Elasticsearch)                 │
│  - Full-text search                                        │
│  - Autocomplete                                            │
│  - Fuzzy matching                                          │
│  - Faceted search                                          │
│  - 30-node cluster                                         │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              MONITORING & OBSERVABILITY                    │
│  Prometheus | Grafana | ELK | Datadog | PagerDuty        │
│  - CDN performance                                         │
│  - Streaming quality metrics                               │
│  - Buffer underrun rate                                    │
│  - Search latency                                          │
└────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **CDN for Audio Delivery**
   - 95% cache hit rate
   - 200+ global PoPs
   - Reduces origin load by 95%
   - Lower latency for users
   - Cost-effective at scale

2. **Multiple Audio Qualities**
   - Adaptive bitrate streaming
   - 64kbps (low), 128kbps (normal), 256kbps (high), 320kbps (premium)
   - Pre-encoded and stored
   - Client selects based on network speed

3. **Microservices Architecture**
   - Independent scaling
   - Technology diversity
   - Fault isolation
   - Easier deployments

4. **Cassandra for Events**
   - Write-heavy workload (3.6B plays/day)
   - Time-series data
   - Fast writes
   - Linear scalability

5. **Elasticsearch for Search**
   - Full-text search
   - Autocomplete
   - Fast queries (< 100ms)
   - Fuzzy matching

6. **Redis for Caching**
   - Hot data caching
   - Session storage
   - Real-time features
   - Sub-millisecond latency

---

## Core Components

### 1. Metadata Service

**Purpose**: Manage and serve music catalog metadata

**Responsibilities**:
- Fetch song/album/artist information
- Serve recommendations
- Manage user library
- Track metadata updates

**API Endpoints**:

**Get Song Details**:
```http
GET /api/v1/songs/{song_id}

Response (200 OK):
{
  "song_id": "song_123",
  "title": "Bohemian Rhapsody",
  "artist": {
    "artist_id": "artist_456",
    "name": "Queen"
  },
  "album": {
    "album_id": "album_789",
    "title": "A Night at the Opera",
    "year": 1975
  },
  "duration": 354,  // seconds
  "genre": "Rock",
  "explicit": false,
  "isrc": "GBUM71029604",
  "popularity": 95,
  "available_countries": ["US", "UK", "CA", ...],
  "audio_features": {
    "tempo": 144,
    "energy": 0.89,
    "danceability": 0.47
  }
}
```

**Get Album**:
```http
GET /api/v1/albums/{album_id}

Response:
{
  "album_id": "album_789",
  "title": "A Night at the Opera",
  "artist": {...},
  "release_date": "1975-11-21",
  "total_tracks": 12,
  "duration": 2586,
  "genres": ["Rock", "Progressive Rock"],
  "label": "EMI",
  "cover_art": {
    "small": "https://cdn.example.com/album_789_300x300.jpg",
    "medium": "https://cdn.example.com/album_789_640x640.jpg",
    "large": "https://cdn.example.com/album_789_1200x1200.jpg"
  },
  "tracks": [
    {
      "track_number": 1,
      "song_id": "song_122",
      "title": "Death on Two Legs",
      "duration": 223
    },
    ...
  ]
}
```

**Search**:
```http
GET /api/v1/search?q=queen&type=artist,album,track&limit=20

Response:
{
  "artists": [
    {
      "artist_id": "artist_456",
      "name": "Queen",
      "followers": 28500000,
      "image_url": "https://cdn.example.com/artist_456.jpg"
    }
  ],
  "albums": [...],
  "tracks": [...],
  "total": {
    "artists": 15,
    "albums": 48,
    "tracks": 320
  }
}
```

### 2. Streaming Service

**Purpose**: Handle audio playback requests and generate streaming URLs

**Responsibilities**:
- Generate CDN URLs with auth tokens
- Select appropriate bitrate
- Track playback sessions
- Handle DRM

**API Endpoints**:

**Get Stream URL**:
```http
POST /api/v1/stream/play

Request:
{
  "song_id": "song_123",
  "quality": "high",  // low, normal, high, premium
  "device_id": "device_xyz"
}

Response (200 OK):
{
  "stream_url": "https://cdn.example.com/audio/song_123_256.aac?token=xyz&expires=1234567890",
  "expires_at": "2024-01-08T11:00:00Z",
  "bitrate": 256,
  "format": "aac",
  "duration": 354,
  "session_id": "session_abc",
  "drm_token": "eyJhbGc..."
}
```

**Report Playback Progress**:
```http
POST /api/v1/stream/progress

Request:
{
  "session_id": "session_abc",
  "song_id": "song_123",
  "position": 45,  // seconds
  "duration_played": 45,
  "buffering_events": 0,
  "quality_switches": 1
}

Response (204 No Content)
```

**Adaptive Bitrate Logic**:
```
Network speed detection:
1. Measure download speed during first 5 seconds
2. Classify connection:
   - < 100 kbps: Low quality (64 kbps)
   - 100-500 kbps: Normal quality (128 kbps)
   - 500 kbps-2 Mbps: High quality (256 kbps)
   - > 2 Mbps: Premium quality (320 kbps)
3. Monitor buffer health:
   - Buffer < 10 seconds: Switch to lower quality
   - Buffer > 30 seconds: Try higher quality
4. Seamless quality transitions (no interruption)
```

### 3. Search Service

**Purpose**: Provide fast, relevant search results

**Implementation**: Elasticsearch cluster

**Features**:
- Full-text search
- Autocomplete
- Fuzzy matching
- Faceted search (filter by genre, year, etc.)
- Ranking by popularity and relevance

**Search Index Structure**:
```json
{
  "mappings": {
    "properties": {
      "song_id": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "autocomplete": {
            "type": "search_as_you_type"
          },
          "exact": {
            "type": "keyword"
          }
        }
      },
      "artist_name": {
        "type": "text",
        "analyzer": "standard"
      },
      "album_name": {"type": "text"},
      "genre": {"type": "keyword"},
      "year": {"type": "integer"},
      "duration": {"type": "integer"},
      "popularity": {"type": "float"},
      "play_count": {"type": "long"}
    }
  }
}
```

**Autocomplete Query**:
```
User types: "bohemi"

Elasticsearch query:
{
  "query": {
    "multi_match": {
      "query": "bohemi",
      "type": "bool_prefix",
      "fields": [
        "title.autocomplete",
        "artist_name.autocomplete"
      ]
    }
  },
  "size": 10
}

Response (< 50ms):
- "Bohemian Rhapsody" by Queen
- "Bohemian Like You" by The Dandy Warhols
- ...
```

### 4. Recommendation Engine

**Purpose**: Generate personalized music recommendations

**Approaches**:

**1. Collaborative Filtering**:
```
User-based:
- Find similar users based on listening history
- Recommend songs liked by similar users
- "Users who like X also like Y"

Item-based:
- Find similar songs based on co-occurrence
- "Songs similar to what you're listening to"

Matrix Factorization (ALS):
- User matrix: 500M users × 1000 latent factors
- Song matrix: 100M songs × 1000 latent factors
- Predict rating: U × S^T
- Update daily via Spark job
```

**2. Content-Based Filtering**:
```
Audio features:
- Tempo (BPM)
- Energy (0-1)
- Danceability (0-1)
- Valence (happiness, 0-1)
- Acousticness (0-1)
- Instrumentalness (0-1)
- Loudness (dB)
- Key and mode

Similarity:
- Cosine similarity on feature vectors
- K-nearest neighbors
- Find songs with similar audio profiles
```

**3. Deep Learning**:
```
Neural Collaborative Filtering:
- Input: User ID + Song ID
- Embedding layers (256 dim)
- Hidden layers (512 → 256 → 128)
- Output: Predicted rating (0-1)
- Trained on 100B historical plays
- Real-time inference (< 10ms)

Two-tower model:
- User tower: User features, history
- Song tower: Song features, metadata
- Dot product for similarity
- Efficient for large-scale retrieval
```

**Recommendation API**:
```http
GET /api/v1/recommendations/home?user_id=user_123

Response:
{
  "sections": [
    {
      "title": "Made For You",
      "type": "personalized_playlist",
      "items": [
        {
          "playlist_id": "discover_weekly_user_123",
          "title": "Discover Weekly",
          "description": "Your weekly mixtape of fresh music",
          "cover_art": "...",
          "updated_at": "2024-01-08"
        }
      ]
    },
    {
      "title": "Recently Played",
      "type": "recent_songs",
      "items": [...]
    },
    {
      "title": "Because you listened to Queen",
      "type": "similar_artists",
      "items": [
{
          "artist_id": "artist_789",
          "name": "Led Zeppelin",
          "followers": 15200000
        },
        {
          "artist_id": "artist_101",
          "name": "Pink Floyd",
          "followers": 12800000
        }
      ]
    }
  ]
}
```

---

## Database Design

### PostgreSQL Schema (Catalog & User Data)

```sql
-- Songs table
CREATE TABLE songs (
    song_id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    artist_id UUID NOT NULL REFERENCES artists(artist_id),
    album_id UUID REFERENCES albums(album_id),
    duration INT NOT NULL,  -- seconds
    genre VARCHAR(100),
    year INT,
    explicit BOOLEAN DEFAULT false,
    isrc VARCHAR(20) UNIQUE,
    popularity FLOAT DEFAULT 0,
    play_count BIGINT DEFAULT 0,
    audio_url_64 TEXT,
    audio_url_128 TEXT,
    audio_url_256 TEXT,
    audio_url_320 TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_songs_artist ON songs(artist_id);
CREATE INDEX idx_songs_album ON songs(album_id);
CREATE INDEX idx_songs_genre ON songs(genre);
CREATE INDEX idx_songs_popularity ON songs(popularity DESC);

-- Artists table
CREATE TABLE artists (
    artist_id UUID PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    bio TEXT,
    image_url TEXT,
    followers_count BIGINT DEFAULT 0,
    verified BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_artists_name ON artists(name);
CREATE INDEX idx_artists_followers ON artists(followers_count DESC);

-- Albums table
CREATE TABLE albums (
    album_id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    artist_id UUID NOT NULL REFERENCES artists(artist_id),
    release_date DATE,
    total_tracks INT,
    album_type VARCHAR(50),  -- album, single, compilation
    label VARCHAR(200),
    cover_art_small TEXT,
    cover_art_medium TEXT,
    cover_art_large TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_albums_artist ON albums(artist_id);
CREATE INDEX idx_albums_release ON albums(release_date DESC);

-- Users table
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    subscription_tier VARCHAR(50) DEFAULT 'free',  -- free, premium
    country VARCHAR(10),
    created_at TIMESTAMP DEFAULT NOW(),
    last_active TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_subscription ON users(subscription_tier);

-- User library (saved songs)
CREATE TABLE user_library (
    user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    song_id UUID REFERENCES songs(song_id) ON DELETE CASCADE,
    added_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, song_id)
);

CREATE INDEX idx_library_user ON user_library(user_id, added_at DESC);

-- Artist follows
CREATE TABLE artist_follows (
    user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    artist_id UUID REFERENCES artists(artist_id) ON DELETE CASCADE,
    followed_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, artist_id)
);

-- Playlists
CREATE TABLE playlists (
    playlist_id UUID PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES users(user_id),
    is_public BOOLEAN DEFAULT false,
    is_collaborative BOOLEAN DEFAULT false,
    cover_image TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_playlists_owner ON playlists(owner_id);
CREATE INDEX idx_playlists_public ON playlists(is_public) WHERE is_public = true;

-- Playlist tracks
CREATE TABLE playlist_tracks (
    playlist_id UUID REFERENCES playlists(playlist_id) ON DELETE CASCADE,
    song_id UUID REFERENCES songs(song_id) ON DELETE CASCADE,
    position INT NOT NULL,
    added_by UUID REFERENCES users(user_id),
    added_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (playlist_id, song_id, position)
);

CREATE INDEX idx_playlist_tracks_playlist ON playlist_tracks(playlist_id, position);

-- User follows
CREATE TABLE user_follows (
    follower_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    following_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    followed_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id)
);
```

### Cassandra Schema (Listening Events)

```cql
-- Play events (listening history)
CREATE TABLE play_events (
    user_id uuid,
    play_date text,  -- YYYY-MM-DD for partitioning
    play_time timestamp,
    event_id timeuuid,
    song_id uuid,
    duration_played int,  -- seconds
    completed boolean,
    device_type text,
    location text,
    quality text,
    PRIMARY KEY ((user_id, play_date), play_time, event_id)
) WITH CLUSTERING ORDER BY (play_time DESC, event_id DESC)
AND default_time_to_live = 7776000;  -- 90 days

-- Song play counts (for charts)
CREATE TABLE song_play_counts (
    song_id uuid,
    date text,  -- YYYY-MM-DD
    play_count counter,
    PRIMARY KEY (song_id, date)
);

-- User listening statistics
CREATE TABLE user_listening_stats (
    user_id uuid,
    period text,  -- monthly, yearly
    artist_id uuid,
    song_id uuid,
    play_count counter,
    total_duration counter,
    PRIMARY KEY ((user_id, period), artist_id, song_id)
);
```

---

## Streaming Flow

### Complete Audio Playback Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ MUSIC STREAMING FLOW - End to End                               │
└─────────────────────────────────────────────────────────────────┘

T=0ms: User clicks play on song

T=10ms: Client → Streaming Service
├─ POST /api/v1/stream/play
├─ Request: {song_id, quality, device_id}
└─ Validate user subscription tier

T=20ms: Streaming Service
├─ Check user entitlements (Redis cache)
│  └─ Free tier: 128 kbps max
│  └─ Premium tier: Up to 320 kbps
├─ Generate CDN URL with signed token
│  └─ Token includes: user_id, song_id, quality, expiry
│  └─ Token valid for 1 hour
├─ Log playback initiation (Kafka)
└─ Return stream URL

T=30ms: Response to Client
{
  "stream_url": "https://cdn.example.com/audio/xyz.aac?token=...",
  "bitrate": 256,
  "format": "aac",
  "duration": 354
}

T=40ms: Client starts downloading audio
├─ HTTP range request to CDN
│  └─ Range: bytes=0-1048575  // First 1MB
├─ CDN checks cache
│  └─ Cache hit (95% of requests)
│  └─ Serve from edge location
│  └─ Cache miss (5%): Fetch from origin → cache → serve

T=100ms: Audio decoding starts
├─ Download first chunks (256 KB)
├─ Decode AAC to PCM
├─ Build initial buffer (10 seconds)

T=200ms: Playback begins
├─ Audio starts playing
├─ Continue buffering in background
├─ Target buffer: 30 seconds

T=1s: Report playback started
├─ POST /api/v1/stream/progress
├─ Log to Kafka → Cassandra
├─ Increment play count (if played > 30 seconds)

T=30s: Scrobble threshold reached
├─ Count this play for artist royalties
├─ Add to user's listening history
├─ Update recommendations model

During playback:
├─ Monitor buffer health
├─ Adaptive quality switching if needed
├─ Report progress every 30 seconds
└─ Prefetch next song in queue

T=354s: Song completed
├─ Report completion
├─ Update user's recently played
├─ Trigger "up next" song
└─ Log completion event

Offline Mode:
1. User downloads song
2. Encrypted file stored locally
3. DRM license cached (renewable every 7 days)
4. Playback from local storage
5. Sync play counts when online
```

### Adaptive Bitrate Switching

```
Scenario: Network conditions change during playback

Initial state:
- Playing at 256 kbps (high quality)
- Buffer: 25 seconds
- Network: Stable

T=0s: Network degrades
├─ Download speed drops to 150 kbps
├─ Playing at 256 kbps consumes 32 KB/s
├─ Downloading at 150 kbps = 18.75 KB/s
└─ Buffer draining: 32 - 18.75 = 13.25 KB/s deficit

T=10s: Buffer reaches threshold
├─ Buffer down to 15 seconds
├─ Trigger quality switch
└─ Switch to 128 kbps (16 KB/s consumption)

T=10.5s: Fetch lower quality segment
├─ Request next segment at 128 kbps
├─ Continue playing current segment
└─ Seamless transition (no interruption)

T=11s: Playing lower quality
├─ Now consuming 16 KB/s
├─ Downloading at 18.75 KB/s
├─ Buffer starts recovering: 2.75 KB/s surplus
└─ Buffer building back up

T=40s: Network improves
├─ Download speed up to 500 kbps
├─ Buffer at 30 seconds (healthy)
└─ Try upgrading quality

T=40.5s: Switch to higher quality
├─ Request next segment at 256 kbps
├─ Monitor buffer health
└─ Successful upgrade

Result: Uninterrupted playback despite network changes
```

---

## Deep Dives

### 1. Content Delivery Network (CDN) Strategy

**Why CDN is Critical**:
```
Without CDN:
- 50M concurrent streams
- Each stream: 256 kbps = 32 KB/s
- Total bandwidth: 50M × 32 KB/s = 1.6 TB/s = 12.8 Tbps
- Origin servers: Massive infrastructure needed
- High latency for distant users
- Single point of failure

With CDN (95% cache hit):
- Origin serves: 5% × 12.8 Tbps = 640 Gbps
- CDN serves: 95% × 12.8 Tbps = 12.16 Tbps
- Lower origin infrastructure (95% reduction)
- Lower latency (edge locations near users)
- Better reliability (distributed)
- Cost savings (CDN cheaper than origin bandwidth)
```

**Cache Strategy**:
```
Hot Content (Top 1M songs - 10% of catalog):
- Accounts for 80% of plays (Pareto principle)
- Always cached at all PoPs
- Cache hit rate: 99%
- Total size: 10TB per PoP

Warm Content (Next 9M songs - 90% of catalog):
- Accounts for 15% of plays
- Cached on-demand at regional PoPs
- Cache hit rate: 90%
- Evicted based on LRU

Cold Content (Rarely played):
- Accounts for 5% of plays
- Fetched from origin on-demand
- Cache hit rate: 60%
- May not be cached at all PoPs

Cache Warming:
- Pre-populate new releases
- Predict trending songs (ML)
- Regional preferences (K-pop in Asia)
- Event-driven (Grammy winners)
```

**Geographic Distribution**:
```
CDN PoP Strategy:
- 200+ locations globally
- Density based on user concentration
  * North America: 50 PoPs
  * Europe: 60 PoPs
  * Asia: 60 PoPs
  * South America: 15 PoPs
  * Others: 15 PoPs

Routing:
- DNS-based geolocation
- Route to nearest PoP
- Fallback to next nearest if unavailable
- Sub-100ms latency for 95% of users
```

### 2. Recommendation System Deep Dive

**Hybrid Recommendation Approach**:

**1. Collaborative Filtering (CF)**:
```
Implicit Feedback Model:
- No explicit ratings, only play counts
- Binary: played (1) or not played (0)
- Confidence based on play frequency

Matrix Factorization:
Users (500M) × Songs (100M) = 50 quintillion possible interactions
Too sparse: 99.999% of entries are 0

Solution: Alternating Least Squares (ALS)
- User matrix U: 500M × 1000
- Song matrix S: 100M × 1000
- Predicted rating: U × S^T
- Optimize: min ||R - U × S^T||^2 + λ(||U||^2 + ||S||^2)

Implementation:
- Apache Spark for distributed training
- Daily full retrain (overnight batch job)
- Incremental updates every 6 hours
- 1000 latent factors
- Training time: 4 hours on 100 Spark workers
```

**2. Content-Based Filtering**:
```
Audio Feature Extraction:
- Librosa for audio analysis
- Features per song:
  * Tempo: 60-200 BPM
  * Energy: 0-1 (low energy = calm, high = energetic)
  * Danceability: 0-1 (rhythm stability)
  * Valence: 0-1 (musical positiveness)
  * Acousticness: 0-1 (acoustic vs electronic)
  * Instrumentalness: 0-1 (vocals presence)
  * Speechiness: 0-1 (spoken words)
  * Loudness: -60 to 0 dB

Feature Vector:
song_features = [tempo, energy, danceability, valence, 
                acousticness, instrumentalness, loudness]

Similarity:
similarity(song_a, song_b) = cosine(features_a, features_b)
                           = (A · B) / (||A|| × ||B||)

Example:
Song A: [120, 0.8, 0.7, 0.6, 0.2, 0.1, -5]
Song B: [125, 0.75, 0.68, 0.55, 0.25, 0.15, -6]
Similarity: 0.98 (very similar)
```

**3. Deep Learning Model**:
```
Two-Tower Neural Network:

User Tower:
Input: user_id + user_features
  ├─ user_id embedding (256 dim)
  ├─ age, gender, location embeddings
  ├─ listening history (recent 100 songs)
  └─ subscription tier
Layers:
  ├─ Dense(512) + ReLU
  ├─ Dense(256) + ReLU
  └─ Dense(128) output embedding

Song Tower:
Input: song_id + song_features
  ├─ song_id embedding (256 dim)
  ├─ audio features (8 dim)
  ├─ artist embedding (128 dim)
  ├─ genre embedding (64 dim)
  └─ popularity score
Layers:
  ├─ Dense(512) + ReLU
  ├─ Dense(256) + ReLU
  └─ Dense(128) output embedding

Prediction:
score = dot_product(user_embedding, song_embedding)
probability = sigmoid(score)

Training:
- Dataset: 100B user-song interactions
- Positive samples: Songs user played > 30s
- Negative samples: Random songs (10:1 ratio)
- Loss: Binary cross-entropy
- Optimizer: Adam, learning rate 0.001
- Batch size: 4096
- Training time: 48 hours on 50 GPUs
- Update: Weekly

Inference:
- Pre-compute song embeddings (offline)
- Compute user embedding (online, < 10ms)
- Approximate nearest neighbors (FAISS)
- Retrieve top-K songs (< 100ms)
```

**Combining Multiple Signals**:
```
Final score = weighted combination:
final_score = 0.5 × cf_score 
            + 0.2 × content_score
            + 0.2 × deep_learning_score
            + 0.05 × popularity_score
            + 0.05 × freshness_score

Ranking:
1. Generate candidate set (1000 songs)
2. Score each song using hybrid model
3. Diversify results (don't repeat artists)
4. Rerank based on user context (time of day)
5. Return top 50 songs

Personalized Playlists:
- Discover Weekly: 30 songs, updated Monday
  * 50% from collaborative filtering
  * 30% from similar artists
  * 20% from audio features
  * No songs user has heard before
  * Optimized for discovery

- Daily Mix: 6 playlists, updated daily
  * Clustered by genre/mood
  * Mix of familiar + new songs
  * 80% known songs, 20% new
  * Seamless listening experience
```

### 3. DRM and Content Protection

**Digital Rights Management**:
```
Encryption:
- Audio files encrypted with AES-128
- Different keys per bitrate/format
- Keys rotated monthly
- Stored in AWS KMS

Playback Flow:
1. User requests song
2. Client requests license
3. License server validates:
   - User subscription active
   - Geographic rights (available in user's country)
   - Device limit (max 5 devices)
4. Issue time-limited license (7 days)
5. Client decrypts and plays
6. License renewal required after expiry

Device Fingerprinting:
- Track device identifiers
- Limit concurrent streams (1 device at a time)
- Detect account sharing
- Revoke suspicious devices

Watermarking:
- Inaudible audio watermarks
- Embed user_id in audio stream
- Detect unauthorized sharing
- Track leaked content to source
```

---

## Scalability & Reliability

### Horizontal Scaling

**Database Sharding**:
```
User Database (PostgreSQL):
- Shard by user_id (hash mod 32)
- Each shard: ~15.6M users
- Shard routing: hash(user_id) % 32
- Cross-shard queries avoided

Catalog Database (PostgreSQL):
- Single database (read-heavy)
- Read replicas: 10 per region
- Write to master, read from replicas
- Replica lag: < 1 second

Cassandra (Listening Events):
- Partition by (user_id, date)
- Replication factor: 3
- Consistency: ONE (read/write)
- Linear scalability
```

**Auto-Scaling**:
```
Metrics:
- API servers: CPU > 70% → scale up
- Streaming servers: Bandwidth > 80% → scale up
- Search servers: Query latency > 200ms → scale up

Scaling Policy:
- Scale up: Add 20% more instances
- Scale down: Remove 10% instances
- Cool-down: 5 minutes
- Min instances: 10 per service
- Max instances: 500 per service
```

### High Availability

**Multi-Region Deployment**:
```
3 Regions: US-East, EU-West, AP-South

Traffic Distribution:
- US users → US-East (70%)
- EU users → EU-West (20%)
- Asia users → AP-South (10%)

Failover:
- Health checks every 10 seconds
- Unhealthy region detected in 30 seconds
- GeoDNS failover to nearest healthy region
- Automatic traffic rerouting
- User impact: Brief interruption (< 1 minute)
```

**Disaster Recovery**:
```
Backup Strategy:
- PostgreSQL: Daily snapshots + WAL archiving
- Cassandra: Daily snapshots
- Audio files: Multi-region replication
- Metadata: Cross-region replication

Recovery Scenarios:
1. Database corruption:
   - Restore from backup
   - Replay WAL
   - RTO: 2 hours, RPO: 1 hour

2. Region failure:
   - Failover to another region
   - RTO: 5 minutes, RPO: 0 (replicated)

3. Complete disaster:
   - Restore from backups
   - RTO: 8 hours, RPO: 24 hours
```

### Monitoring

**Key Metrics**:
```
Streaming Quality:
- Buffer underrun rate: < 0.1%
- Average bitrate delivered
- Quality switch frequency
- CDN cache hit rate: > 95%

Performance:
- API latency (p50, p95, p99)
- Search latency: < 100ms
- Recommendation generation: < 500ms
- Audio start latency: < 200ms

Business:
- Daily active users
- Total listening hours
- Retention rate
- Conversion rate (free → premium)
```

---

## Trade-offs & Alternatives

### 1. Storage: Object Storage vs CDN Origin

**Object Storage (S3)** (Chosen):
```
Pros:
+ Unlimited scalability
+ 99.999999999% durability
+ Multi-region replication
+ Lifecycle policies
+ Cost-effective ($0.023/GB/month)

Cons:
- Higher latency than local storage
- API calls add overhead
- Bandwidth costs

Use case: Perfect for massive audio catalog
```

**CDN Origin Servers**:
```
Pros:
+ Lower latency
+ No API overhead
+ Full control

Cons:
- Limited scalability
- Manual replication
- Higher operational cost
- No lifecycle management

Use case: Not suitable at 6PB scale
```

### 2. Recommendation: Batch vs Real-time

**Hybrid Approach** (Chosen):
```
Batch (Daily/Weekly):
+ Accurate, uses all data
+ Complex algorithms possible
+ Cost-effective

Real-time (Streaming):
+ Fresh recommendations
+ React to immediate behavior
+ Better user engagement

Implementation:
- Batch: Matrix factorization, deep learning
- Real-time: Similar songs, trending
- Combined: Best of both worlds
```

### 3. Search: Elasticsearch vs CloudSearch

**Elasticsearch** (Chosen):
```
Pros:
+ Full control
+ Rich query capabilities
+ Strong community
+ Free (open source)

Cons:
- Operational overhead
- Requires tuning
- Scaling complexity

When to use: When you need flexibility
```

**AWS CloudSearch**:
```
Pros:
+ Fully managed
+ Auto-scaling
+ Simple setup

Cons:
- Limited query features
- Vendor lock-in
- Higher cost
- Less control

When to use: Want managed service
```

### 4. Audio Format: AAC vs Ogg Vorbis vs Opus

**AAC** (Primary):
```
Pros:
+ Industry standard
+ Excellent quality-to-size ratio
+ Hardware decoding support
+ Patent-free (after 2025)

Cons:
- Higher complexity
- More CPU for encoding

Use for: Premium tier, general use
```

**Opus** (Alternative):
```
Pros:
+ Open source
+ Best quality at low bitrates
+ Low latency
+ Free from patents

Cons:
- Less hardware support
- Newer format

Use for: Future consideration
```

---

## Conclusion

This music streaming platform design provides:

**Performance**:
- Sub-2-second audio buffering
- <100ms search latency
- <500ms recommendations
- 95% CDN cache hit rate

**Scale**:
- 500M users, 100M DAU
- 50M concurrent streams
- 100M song catalog
- 10B monthly sessions
- 6.3PB total storage

**Reliability**:
- 99.9% uptime
- Multi-region deployment
- CDN with 200+ PoPs
- Automatic failover
- Comprehensive monitoring

**Features**:
- Multiple audio qualities (adaptive streaming)
- Personalized recommendations (AI-powered)
- Social features (follow, share, collaborate)
- Offline downloads
- Multi-device sync
- Rich search capabilities

The system balances user experience, cost efficiency, and technical excellence while remaining flexible enough to scale to 1B users.

---

**Document Status**: Complete ✓
