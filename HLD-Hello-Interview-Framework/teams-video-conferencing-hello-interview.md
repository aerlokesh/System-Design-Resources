# Design Video Conferencing (Teams Meetings) — Hello Interview Framework

> **Question**: Design the backend for video calls and screen sharing in Microsoft Teams — ensuring low latency, high quality, and the ability to scale to thousands of concurrent conferences with hundreds of participants each.
>
> **Asked at**: Microsoft, Google, Amazon (Chime), Zoom
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
1. **1:1 and Group Video Calls**: Support calls from 2 to 1,000 participants. Video + audio for all participants. Toggle camera and microphone.
2. **Screen Sharing**: Share entire screen, specific window, or PowerPoint file. High resolution for text/code readability.
3. **Real-Time Audio/Video**: End-to-end latency < 200ms. Adaptive bitrate based on network conditions. Echo cancellation, noise suppression.
4. **Meeting Management**: Schedule meetings (calendar integration). Join via link. Lobby/waiting room. Admit/deny participants.
5. **Meeting Controls**: Mute/unmute participants (host). Pin video. Gallery view, speaker view, large gallery. Raise hand. Meeting reactions.
6. **Recording**: Record meetings to cloud (OneDrive/SharePoint). Automatic transcription. Meeting highlights.
7. **Chat in Meeting**: Text chat alongside video. Persists after meeting.

#### Nice to Have (P1)
- Live captions and subtitles (real-time transcription)
- Breakout rooms
- Together mode (virtual shared background)
- Background blur / virtual backgrounds
- PSTN dial-in (phone number for audio)
- Live events (broadcast to 10,000+ attendees)
- Whiteboard integration
- Polls and Q&A
- Meeting insights (AI-generated summary, action items)

#### Below the Line (Out of Scope)
- Calendar/scheduling system
- Chat outside meetings
- Phone system (PSTN calling outside meetings)
- Meeting room devices (hardware)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Concurrent meetings** | 50 million simultaneous | Peak business hours globally |
| **Participants per meeting** | Up to 1,000 (video), 10,000 (view-only) | Large org all-hands |
| **End-to-end latency** | < 150ms (audio), < 200ms (video) p95 | Conversational quality |
| **Video quality** | 720p default, 1080p for screen share | Balance quality vs bandwidth |
| **Audio quality** | 48kHz Opus codec | Crystal clear audio |
| **Availability** | 99.99% | Business-critical |
| **Join time** | < 3 seconds from click to connected | Fast meeting start |
| **Bandwidth per participant** | 1.5–4 Mbps (sending), 2–8 Mbps (receiving) | Adaptive |

### Capacity Estimation

```
Meetings:
  Concurrent meetings: 50M
  Average participants per meeting: 5
  Total concurrent participants: 250M
  Meetings started/sec: ~100K (peak hour)
  
Media Traffic:
  Per participant sending video: ~2 Mbps
  Per participant receiving (gallery of 9): ~8 Mbps
  Total sending bandwidth: 250M × 2 Mbps = 500 Pbps (distributed)
  Total receiving bandwidth: 250M × 8 Mbps = 2 Ebps (distributed)
  
  Note: most traffic is edge-to-participant, not centralized
  
Media Servers:
  SFU capacity: ~200 participants per media server
  Servers needed: 250M / 200 = 1.25M media server instances
  With overhead: ~2M media server instances globally
  
Signaling:
  Join/leave events: ~500K/sec
  Control messages (mute, video toggle): ~2M/sec
  Signaling servers: ~5,000 instances
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  Meeting         │────▶│  Participant      │────▶│  MediaStream       │
│                  │     │                   │     │                    │
│ meetingId        │     │ participantId     │     │ streamId           │
│ organizerId      │     │ meetingId         │     │ participantId      │
│ subject          │     │ userId            │     │ type (audio/video/ │
│ startTime        │     │ role (organizer/  │     │  screenShare)      │
│ endTime          │     │  presenter/       │     │ codec              │
│ joinUrl          │     │  attendee)        │     │ resolution         │
│ passcode         │     │ joinedAt          │     │ bitrate            │
│ lobbyEnabled     │     │ leftAt            │     │ active             │
│ recordingEnabled │     │ audioState (on/   │     │ mediaServerId      │
│ maxParticipants  │     │  off/muted)       │     │ ssrc               │
│ meetingType      │     │ videoState        │     └───────────────────┘
│ (scheduled/adhoc)│     │ screenSharing     │
└─────────────────┘     │ raisedHand        │     ┌───────────────────┐
                         │ deviceInfo        │     │  Recording         │
                         └──────────────────┘     │                    │
                                                    │ recordingId       │
┌─────────────────┐     ┌──────────────────┐     │ meetingId          │
│  MediaServer     │     │  SignalingSession │     │ status (active/   │
│  (SFU)           │     │                   │     │  processing/done) │
│                  │     │ sessionId         │     │ blobRef            │
│ serverId         │     │ participantId     │     │ transcriptRef      │
│ region           │     │ mediaServerId     │     │ duration           │
│ capacity         │     │ iceState          │     └───────────────────┘
│ currentLoad      │     │ sdpOffer          │
│ publicIP         │     │ sdpAnswer         │
│ participants[]   │     │ iceCandidates[]   │
└─────────────────┘     └──────────────────┘
```

---

## 3️⃣ API Design

### Join Meeting
```
POST /api/v1/meetings/{meetingId}/join
Authorization: Bearer <token>

Request:
{
  "deviceInfo": {
    "platform": "web",
    "browser": "Chrome",
    "capabilities": ["audio", "video", "screenShare"]
  },
  "mediaConstraints": {
    "audio": true,
    "video": { "width": 1280, "height": 720, "frameRate": 30 }
  }
}

Response: 200 OK
{
  "participantId": "part_abc",
  "signalingUrl": "wss://media-signal.teams.microsoft.com/ws",
  "mediaServerUrl": "turn:media-relay-eastus.teams.microsoft.com:443",
  "iceServers": [
    { "urls": "stun:stun.teams.microsoft.com:3478" },
    { "urls": "turn:turn.teams.microsoft.com:443", "credential": "temp_cred" }
  ],
  "meetingInfo": {
    "subject": "Sprint Planning",
    "participants": [ ...current participants... ],
    "recordingActive": false
  }
}
```

### Toggle Media
```
PUT /api/v1/meetings/{meetingId}/participants/{participantId}/media
Authorization: Bearer <token>

Request:
{
  "audio": { "enabled": false },     // mute
  "video": { "enabled": true, "resolution": "720p" },
  "screenShare": { "enabled": false }
}
```

### Start Screen Share
```
POST /api/v1/meetings/{meetingId}/screenShare
Authorization: Bearer <token>

Request:
{
  "type": "screen",            // screen | window | powerpoint
  "audioIncluded": true,       // system audio sharing
  "resolution": "1080p"
}
```

---

## 4️⃣ Data Flow

### Joining a Meeting & Establishing Media

```
User clicks "Join" → Teams Client
        │
        ├── 1. POST /meetings/{id}/join → Signaling Service
        │       Returns: signalingUrl, mediaServer, iceServers
        │
        ├── 2. WebSocket connect to Signaling Service
        │       → Receive participant list, meeting state
        │
        ├── 3. WebRTC negotiation:
        │       a. Client creates RTCPeerConnection
        │       b. Client generates SDP offer (codecs, streams)
        │       c. Send offer to Signaling Service → forward to Media Server (SFU)
        │       d. Media Server returns SDP answer
        │       e. ICE connectivity check:
        │          - Try direct P2P (STUN) → rare in enterprise (firewalls)
        │          - Fall back to TURN relay → media through relay server
        │       f. DTLS handshake → secure media channel established
        │
        ├── 4. Media flowing:
        │       Client → (encrypted RTP) → TURN Relay → Media Server (SFU)
        │       Media Server → (encrypted RTP) → TURN Relay → Other Participants
        │
        └── Total join time: < 3 seconds
```

### Media Flow Through SFU

```
Participant A (sending video + audio)
        │
        │  RTP stream (VP8/VP9/H.264 video, Opus audio)
        ▼
┌──────────────────────────┐
│     Media Server (SFU)    │
│  Selective Forwarding Unit│
│                           │
│  Receives streams from    │
│  all participants.        │
│                           │
│  For each receiver:       │
│  - Select which streams   │
│    to forward (active     │
│    speaker, pinned)       │
│  - Select quality layer   │
│    (simulcast: high/      │
│    medium/low)            │
│  - Forward without        │
│    transcoding (fast!)    │
│                           │
│  Participant B watching   │
│  gallery of 9:            │
│  → Forward 1 high-res     │
│    (active speaker)       │
│  → Forward 8 low-res      │
│    (thumbnails)           │
└──────────┬───────────────┘
           │
           ├──▶ Participant B: receives 9 video streams
           ├──▶ Participant C: receives 9 video streams  
           ├──▶ Participant D: receives 9 video streams
           └──▶ ... (up to 1000 participants)
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ Desktop  │  │  Mobile  │  │   Web    │  │  Room    │           │
│  │(Electron)│  │(iOS/And) │  │ (WebRTC) │  │ Device   │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
│       └──────────────┴──────────────┴──────────────┘                 │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTPS + WebSocket + RTP/SRTP
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    SIGNALING LAYER                                    │
│                                                                      │
│  ┌────────────────────┐    ┌──────────────────────┐                 │
│  │  Azure Front Door  │    │  Signaling Service    │                 │
│  │  (Global LB)       │    │  (WebSocket)          │                 │
│  │                    │    │                       │                 │
│  │  Route to nearest  │    │  - Meeting state      │                 │
│  │  signaling node    │    │  - SDP exchange       │                 │
│  └────────────────────┘    │  - ICE candidates     │                 │
│                             │  - Participant events  │                 │
│                             │  - Media control       │                 │
│                             └───────────┬───────────┘                 │
└─────────────────────────────────────────┬───────────────────────────┘
                                          │
┌─────────────────────────────────────────┼───────────────────────────┐
│                    MEDIA LAYER           │                            │
│                                          ▼                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  TURN Relay  │  │  TURN Relay  │  │  TURN Relay  │              │
│  │  (Edge)      │  │  (Edge)      │  │  (Edge)      │  ...         │
│  │              │  │              │  │              │              │
│  │  NAT         │  │  NAT         │  │  NAT         │              │
│  │  traversal   │  │  traversal   │  │  traversal   │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         └──────────────────┴──────────────────┘                      │
│                            │                                          │
│  ┌─────────────────────────┴─────────────────────────────────┐      │
│  │              Media Servers (SFU Cluster)                    │      │
│  │                                                            │      │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐               │      │
│  │  │  SFU 1   │  │  SFU 2   │  │  SFU N   │               │      │
│  │  │          │  │          │  │          │               │      │
│  │  │ Meeting  │  │ Meeting  │  │ Meeting  │               │      │
│  │  │ A, B, C  │  │ D, E     │  │ F, G, H  │               │      │
│  │  │          │  │          │  │          │               │      │
│  │  │ Simulcast│  │ Simulcast│  │ Simulcast│               │      │
│  │  │ routing  │  │ routing  │  │ routing  │               │      │
│  │  └──────────┘  └──────────┘  └──────────┘               │      │
│  │                                                            │      │
│  │  For large meetings (>200):                                │      │
│  │  ┌──────────────────────────────────────────────┐         │      │
│  │  │  SFU Cascade: SFU-1 ←→ SFU-2 ←→ SFU-3      │         │      │
│  │  │  Participants split across SFUs,              │         │      │
│  │  │  SFUs relay streams to each other             │         │      │
│  │  └──────────────────────────────────────────────┘         │      │
│  └───────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    SUPPORTING SERVICES                                │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐      │
│  │ Meeting      │  │ Recording    │  │ Transcription        │      │
│  │ Service      │  │ Service      │  │ Service              │      │
│  │              │  │              │  │                      │      │
│  │ CRUD meeting │  │ Capture      │  │ Real-time speech-    │      │
│  │ Lobby mgmt   │  │ media →      │  │ to-text (Azure       │      │
│  │ Scheduling   │  │ encode →     │  │ Speech Services)     │      │
│  │              │  │ store in     │  │ Live captions        │      │
│  │              │  │ Azure Blob   │  │                      │      │
│  └──────────────┘  └──────────────┘  └──────────────────────┘      │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐      │
│  │ Media        │  │ Analytics    │  │ Quality Monitor      │      │
│  │ Processor    │  │ Service      │  │                      │      │
│  │              │  │              │  │ Track jitter, packet  │      │
│  │ Background   │  │ Call quality │  │ loss, latency per    │      │
│  │ blur, noise  │  │ dashboard    │  │ participant          │      │
│  │ suppression  │  │ Usage stats  │  │ Adaptive bitrate     │      │
│  │ (client-side)│  │              │  │ control signals      │      │
│  └──────────────┘  └──────────────┘  └──────────────────────┘      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: SFU Architecture & Simulcast

**Problem**: In a 9-person video call, without optimization each participant would need to encode their video 8 times (once per recipient) and receive 8 full-res streams. This doesn't scale. How does the SFU make this efficient?

**Solution: Selective Forwarding Unit with Simulcast**

```
Simulcast — Client sends multiple quality layers:

Each participant's client encodes video simultaneously at 3 quality levels:
  High:   1280×720, 30fps, ~1.5 Mbps  (for active speaker / pinned)
  Medium: 640×360,  15fps, ~500 Kbps   (for gallery view)
  Low:    320×180,  7fps,  ~150 Kbps   (for thumbnail / low bandwidth)

Client → SFU: sends all 3 layers as separate RTP streams (same SSRC group)

SFU Decision (per receiver):
  For Participant B watching gallery of 9:
    Active speaker (A) → forward HIGH layer
    Other 8 thumbnails → forward LOW layer
    Total receiving bandwidth: 1.5 + (8 × 0.15) = 2.7 Mbps ✓

  For Participant B watching pinned presentation:
    Pinned speaker (A) → forward HIGH layer
    Others → don't forward video (audio only)
    Total: 1.5 Mbps ✓

SFU Advantages (vs MCU):
  SFU (Selective Forwarding):
    ✅ No transcoding → low CPU, low latency
    ✅ Each receiver gets optimal quality
    ✅ Scales to 200+ participants per server
    ✗ Receiving bandwidth scales with # of visible streams
  
  MCU (Multipoint Control Unit):
    ✅ Single composited stream to each receiver (low bandwidth)
    ✗ Transcoding is CPU-intensive → expensive
    ✗ Added latency (decode → compose → re-encode)
    ✗ All receivers see same layout

SFU scalability per server:
  CPU: forwarding is near-zero (just packet routing)
  Bandwidth: main bottleneck
  Per server: 10 Gbps NIC → ~200 participants (50 Mbps each)
  Memory: minimal (metadata per stream)
```

---

### Deep Dive 2: Handling Large Meetings (Cascaded SFUs)

**Problem**: A single SFU handles ~200 participants. How do we support meetings with 1,000+ participants?

**Solution: SFU Cascading — Distribute Participants Across SFU Cluster**

```
Cascaded SFU Architecture:

Meeting with 800 participants:
  SFU-1: participants 1–200
  SFU-2: participants 201–400
  SFU-3: participants 401–600
  SFU-4: participants 601–800

Inter-SFU relay:
  SFU-1 ←→ SFU-2 ←→ SFU-3 ←→ SFU-4
  
  Each SFU forwards active speaker + top N video streams to other SFUs
  Not ALL streams — only relevant ones (active speakers, shared screen)

Flow:
  Participant 150 speaks (on SFU-1):
    1. SFU-1 detects audio level → marks as active speaker
    2. SFU-1 forwards 150's high-res video to SFU-2, SFU-3, SFU-4
    3. Each SFU forwards to their local participants
    4. Total inter-SFU bandwidth: 3 × 1.5 Mbps = 4.5 Mbps (negligible)

  Participant 500 raises hand (on SFU-3):
    1. Signaling message → Meeting Service → broadcast to all SFUs
    2. Each SFU notifies local participants via signaling channel

SFU Assignment Algorithm:
  On participant join:
    1. Meeting Service determines which SFU has capacity
    2. Prefer SFU in same region as participant (lower latency)
    3. If all regional SFUs full → assign to cross-region SFU (higher latency)
    4. Return SFU address in join response

For Live Events (10,000+ viewers):
  Presenter → SFU (active participants, up to 250)
  SFU → CDN ingest point (RTMP/SRT)
  CDN → HLS/DASH streams to 10K+ viewers
  Viewers: 15-30 second delay (acceptable for broadcast)
  Q&A: via signaling channel (text), not media
```

---

### Deep Dive 3: Network Adaptation & Quality of Service

**Problem**: Participants have varying network conditions — some on office fiber, others on mobile 4G, some on congested home WiFi. How do we deliver the best possible quality for each participant?

**Solution: Bandwidth Estimation + Adaptive Bitrate + FEC**

```
Bandwidth Estimation (sender-side):
  
  Google Congestion Control (GCC) algorithm:
    1. Sender sends RTP packets with timestamp
    2. Receiver sends RTCP feedback: 
       - Receiver Report (RR): packet loss, jitter
       - REMB: receiver estimated max bitrate
       - Transport-CC: per-packet arrival time feedback
    3. Sender adjusts bitrate based on feedback:
       
       Low packet loss (<2%) + low jitter → increase bitrate
       High packet loss (>5%) → reduce bitrate by 20%
       Jitter increasing → reduce bitrate by 10%
       
    Bitrate adjustment range:
       Video: 150 Kbps (low) → 2.5 Mbps (high)
       Audio: always 32 Kbps Opus (non-negotiable quality)

SFU-Side Quality Adaptation:
  
  SFU monitors each receiver's feedback:
    Participant B on poor WiFi (estimated 1 Mbps):
      → Switch active speaker from HIGH to MEDIUM layer
      → Switch thumbnails from LOW to AUDIO-ONLY (no video)
      → Total: 500 Kbps + audio = ~550 Kbps ✓
    
    Participant C on fiber (estimated 10 Mbps):
      → All HIGH layers for gallery
      → Total: 9 × 1.5 = 13.5 Mbps... cap at 8 Mbps
      → Active speaker HIGH, others MEDIUM
      → Total: 1.5 + 8 × 0.5 = 5.5 Mbps ✓

Forward Error Correction (FEC):
  For lossy networks (3-5% packet loss):
    - Add FEC packets (Reed-Solomon or XOR-based)
    - 10% overhead → can recover up to 5% loss without retransmission
    - No added latency (unlike retransmission)
  
  For severe loss (>5%):
    - Reduce resolution (less data = fewer lost packets)
    - Switch to audio-only
    - Show banner: "Your network is unstable"

Audio Priority:
  Audio ALWAYS gets bandwidth priority over video.
  If bandwidth drops to 100 Kbps:
    → Video disabled
    → Audio at 32 Kbps Opus → still clear conversation
  
  Opus codec features:
    - Packet loss concealment (PLC): synthesize missing audio
    - Variable bitrate: 6–128 Kbps
    - Comfortable at 32 Kbps for speech
```

---

### Deep Dive 4: Meeting Recording & Storage

**Problem**: Millions of meetings are recorded daily. Each recording can be hours long, generating petabytes of video. How to capture, process, and store recordings efficiently?

**Solution: Server-Side Recording via SFU Media Tap**

```
Recording Architecture:

Recording Start:
  Organizer clicks "Record" → Signaling Service
  → Meeting Service: create Recording object { meetingId, status: "active" }
  → Instruct SFU: enable media tap for this meeting

SFU Media Tap:
  SFU copies outgoing media streams (already decoded packets)
  → Forward to Recording Ingester (same region)
  
  What's captured:
    - Active speaker video (highest quality layer)
    - All audio tracks (mixed and individual)
    - Screen share stream
    - Speaker identification metadata (who's speaking when)

Recording Ingester:
  1. Receive raw RTP streams
  2. Decode and compose into a single video:
     ┌──────────────────────┐
     │  ┌────────┐          │
     │  │ Active │          │
     │  │Speaker │          │
     │  │ (720p) │          │
     │  └────────┘          │
     │  ┌──┐┌──┐┌──┐┌──┐   │  Gallery view layout
     │  │P1││P2││P3││P4│   │  (switches with active speaker)
     │  └──┘└──┘└──┘└──┘   │
     └──────────────────────┘
  3. Encode: H.264 Main Profile, 1080p, 2 Mbps
  4. Audio: AAC, stereo, 128 Kbps
  5. Write to temporary Azure Blob (5-minute segments)

Post-Processing (after meeting ends):
  1. Concatenate segments into single MP4
  2. Generate transcript (Azure Speech → text):
     - Speaker diarization (who said what)
     - Timestamps aligned with video
  3. Generate timeline markers (speaker changes, screen shares)
  4. Upload final recording to user's OneDrive/SharePoint
  5. Create sharing link with meeting attendees
  6. Store transcript as .vtt (WebVTT) alongside video

Storage:
  Average meeting recording: 60 min × 2 Mbps = 900 MB
  Recordings/day: 10M meetings × 20% recorded = 2M recordings
  Daily storage: 2M × 900 MB = 1.8 PB/day
  → Storage tiering: Hot 30 days → Cool 90 days → Archive
```

---

### Deep Dive 5: Noise Suppression & Audio Processing

**Problem**: Participants join from noisy environments (home, café, open office). Background noise ruins meeting quality. How do we handle this?

**Solution: AI-Powered Noise Suppression (Client-Side)**

```
Audio Processing Pipeline (runs on client device):

Raw microphone input (48kHz PCM)
    │
    ├── 1. Acoustic Echo Cancellation (AEC):
    │       Problem: speaker output feeds back into microphone
    │       Solution: subtract estimated echo from mic signal
    │       Uses adaptive filter modeling the room's impulse response
    │
    ├── 2. Noise Suppression (AI model):
    │       Model: RNNoise-based deep learning model
    │       Input: spectrogram (STFT of audio frames, 20ms windows)
    │       Output: spectral mask (which frequencies are speech vs noise)
    │       
    │       Training data: 10,000+ hours of speech + 1,000+ noise types
    │         (keyboard, dog barking, traffic, construction, baby crying)
    │       
    │       Processing: per-frame (20ms) → 2ms latency
    │       CPU cost: ~5% of one core (lightweight for real-time)
    │       
    │       Result: removes background noise while preserving speech
    │
    ├── 3. Automatic Gain Control (AGC):
    │       Normalize volume levels across participants
    │       Quiet speaker → boost
    │       Loud speaker → reduce
    │       Target: consistent -20 dBFS
    │
    ├── 4. Voice Activity Detection (VAD):
    │       Detect when user is actually speaking
    │       When silent: transmit comfort noise (low bandwidth)
    │       Prevents transmitting ambient sound during pauses
    │
    └── 5. Opus Encoding:
            Encode processed audio → Opus codec
            32 Kbps for speech (VoIP mode)
            Send via RTP to SFU

Server-Side:
  Active Speaker Detection:
    SFU receives audio levels from all participants
    Rank by energy level → top 1-3 are "active speakers"
    Switch video focus to active speaker
    Send audio from active speakers at higher priority
```
