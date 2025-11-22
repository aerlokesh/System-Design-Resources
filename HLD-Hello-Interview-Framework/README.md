# Hello Interview System Design Framework

This folder contains all system designs reorganized according to the **Hello Interview Framework** - a structured approach to system design interviews that ensures comprehensive coverage of all critical aspects.

## 📋 Framework Overview

The Hello Interview framework follows 6 sequential steps:

```
1. Requirements → 2. Core Entities → 3. API/Interface → 4. Data Flow → 5. High-Level Design → 6. Deep Dives
```

### Step 1: Requirements ✅
**Primary Goal: Satisfy Non-functional Requirements**
- **Functional Requirements**: What the system must do (features)
- **Non-Functional Requirements**: Quality attributes (scalability, availability, performance, consistency)
- **Capacity Estimation**: QPS, storage, bandwidth calculations

### Step 2: Core Entities 🔷
**Primary Goal: Satisfy Functional Requirements**
- Identify main domain objects/entities
- Define entity relationships
- Create data models and schemas

### Step 3: API or Interface 🔌
**Primary Goal: Satisfy Functional Requirements**
- Design RESTful/GraphQL APIs
- Define request/response formats
- Specify authentication/authorization
- Document all endpoints

### Step 4: Data Flow 🔄
**Primary Goal: Bridge Functional and Non-Functional Requirements**
- Map request-response flows
- Identify data movement patterns
- Show interaction between components
- Optional: Can be integrated into High-Level Design

### Step 5: High-Level Design 🏗️
**Primary Goal: Satisfy Non-Functional Requirements**
- Create architecture diagram
- Define major components and services
- Show data stores and caching layers
- Include load balancers, CDN, message queues

### Step 6: Deep Dives 🔍
**Primary Goal: Demonstrate Expertise**
- Explore critical components in detail
- Discuss trade-offs and alternatives
- Cover edge cases and failure scenarios
- Address scalability and optimization

## 📂 Document Structure

Each system design document follows this template:

```markdown
# [System Name] - Hello Interview Framework

## 1️⃣ Requirements
### Functional Requirements
- Feature 1
- Feature 2
...

### Non-Functional Requirements
- Scalability: X users, Y QPS
- Availability: Z% uptime
- Performance: P99 latency
- Consistency: Strong/Eventual

### Capacity Estimation
- Traffic estimates
- Storage estimates
- Bandwidth estimates
- Memory estimates

## 2️⃣ Core Entities
### Entity 1: [Name]
- Fields
- Relationships
- Constraints

### Entity 2: [Name]
...

## 3️⃣ API Design
### Endpoint 1
- Method: GET/POST/PUT/DELETE
- Path: /api/v1/resource
- Request/Response

### Endpoint 2
...

## 4️⃣ Data Flow
### Flow 1: [Operation Name]
1. Step 1
2. Step 2
...

### Flow 2: [Operation Name]
...

## 5️⃣ High-Level Design
### Architecture Diagram
[Diagram or description]

### Components
- Component 1: Purpose and design
- Component 2: Purpose and design
...

### Data Stores
- Database choices
- Caching strategy
- Storage solutions

## 6️⃣ Deep Dives
### Topic 1: [Critical Component]
- Detailed design
- Trade-offs
- Alternatives

### Topic 2: [Scalability Challenge]
...

### Additional Topics
- Failure scenarios
- Monitoring & alerting
- Security considerations
- Cost optimization
```

## 🎯 Key Benefits

1. **Structured Approach**: Systematic progression through design phases
2. **Comprehensive Coverage**: Ensures no critical aspect is missed
3. **Interview-Friendly**: Maps well to typical interview flow
4. **Clear Separation**: Distinguishes functional vs non-functional concerns
5. **Depth Control**: Allows diving deep where needed

## 📚 System Designs Included

This folder contains Hello Interview framework versions of:

- AB Testing Platform
- Amazon Product Search
- Calendar System
- Credit Card Processing
- Distributed Key-Value Store
- Distributed Logging & Monitoring
- Facebook Live Comments
- Global CDN
- Google Analytics
- Google Drive
- High-Definition Image Viewer
- Instagram
- Internationalization (i18n)
- Large Data Migration (GCP)
- Leaderboard
- Marketing Campaign System
- Metrics Collection
- Music Streaming Platform
- Netflix Video Streaming
- Notification System
- Rate Limiter
- Recommendation System
- Short Video Platform
- Social Feed with Ads
- Stock Price Viewing
- Stock Trading Platform
- Twitter
- URL Shortener
- User Authentication
- WhatsApp Messenger

## 🔄 Comparison with Original Structure

| Original Structure | Hello Interview Framework |
|-------------------|---------------------------|
| Problem Statement | 1. Requirements |
| Functional Requirements | Covered in Step 1 & 2 |
| Non-Functional Requirements | Covered in Step 1 & 5 |
| Capacity Estimation | Part of Step 1 |
| Core Components | Step 2 (Entities) + Step 5 (Components) |
| Database Design | Part of Step 2 (Entities) |
| API Design | Step 3 |
| High-Level Architecture | Step 5 |
| Deep Dives | Step 6 |

## 💡 Interview Tips

1. **Always start with requirements** - Clarify before designing
2. **Draw as you talk** - Visual diagrams are crucial
3. **Think out loud** - Explain your reasoning
4. **Ask questions** - Show you're considering trade-offs
5. **Time management**:
   - Requirements: 5-10 minutes
   - Core Entities & API: 10-15 minutes
   - High-Level Design: 15-20 minutes
   - Deep Dives: 15-20 minutes

## 🛠️ How to Use

1. **For Interviews**: Follow the 6 steps sequentially
2. **For Study**: Compare original vs Hello Interview versions
3. **For Practice**: Try converting a design yourself first
4. **For Review**: Focus on Deep Dives for advanced topics

## 📖 References

- Hello Interview Framework: Based on industry best practices
- Original HLD documents: Located in `/HLD` folder
- Design Patterns: Referenced throughout documents

---

**Created**: November 2025  
**Framework Version**: 1.0  
**Total System Designs**: 30+
