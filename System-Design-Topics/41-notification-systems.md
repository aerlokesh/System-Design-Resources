# 🎯 Topic 41: Notification Systems

> **System Design Interview — Deep Dive**
> Async notification pipeline, channel-agnostic dispatch (push, email, SMS), aggregation to prevent fatigue, and user preference management.

---

## Architecture

```
Event Producers (Like, Comment, Follow Services)
  → Kafka "notification-events" topic
  → Notification Service consumes events
  → Resolves user preferences (push? email? SMS? all?)
  → Formats message per channel
  → Dispatches to channel gateways:
      APNS (iOS push), FCM (Android push), SendGrid (email), Twilio (SMS)
```

## Notification Aggregation (Prevent Fatigue)

```
Problem: Viral tweet gets 10,000 likes in an hour.
  → 10,000 individual push notifications → user's phone unusable.

Solution: Time-windowed aggregation.
  First like: "Alice liked your tweet" (immediate)
  Next 5 minutes: Batch into Redis sorted set
  After 5 minutes: "Alice and 47 others liked your tweet" (one notification)

Implementation:
  ZADD notif_batch:{user}:{tweet}:{type} {timestamp} {actor_id}
  Timer: After 5 minutes or 50 actors, emit aggregated notification
```

## Channel-Agnostic Design

```
Event: { type: "like", actor: "alice", target_user: "bob", content_id: "tweet_123" }

Notification Service:
  1. Lookup bob's preferences: { like: ["push"], follow: ["push", "email"] }
  2. Format: "Alice liked your tweet"
  3. Dispatch to APNS gateway (push)

Adding new channel (e.g., Slack): Just add a new gateway — no event producer changes.
Adding new event type: Just publish new event — notification service adapts.
```

## Interview Script
> *"The notification pipeline is entirely async and channel-agnostic. Event producers publish structured events to Kafka. The Notification Service resolves preferences, formats per channel, and dispatches. Aggregation prevents fatigue — 10,000 likes become 'Alice and 47 others liked your tweet' via 5-minute batching in Redis."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              NOTIFICATION SYSTEMS CHEAT SHEET                 │
├──────────────────────────────────────────────────────────────┤
│  Pipeline: Kafka events → Notification Service → Gateways    │
│  Channels: APNS (iOS), FCM (Android), SendGrid, Twilio      │
│  Aggregation: 5-min window → "Alice and 47 others liked..."  │
│  Preferences: Per-user, per-event-type channel selection     │
│  Extensible: New channel = new gateway. New event = new topic│
│  All async: No user-facing latency impact                    │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
