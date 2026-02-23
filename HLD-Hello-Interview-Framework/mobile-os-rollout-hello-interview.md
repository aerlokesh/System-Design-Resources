# Design a Mobile OS Rollout System (OTA Updates) - Hello Interview Framework

> **Question**: Design a system that can securely distribute mobile OS updates to millions of devices worldwide, handling different device types, network conditions, and rollout strategies like staged deployments. Users see a notification, download the update under diverse network conditions, and install it safely with the ability to pause, resume, or defer. Release managers configure staged rollouts, target cohorts, and monitor health to pause or roll back.
>
> **Asked at**: Amazon, Google, Microsoft, Apple, Samsung
>
> **Difficulty**: Hard | **Level**: Staff/Principal

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
1. **Staged Rollout**: Release managers can configure phased rollouts (e.g., 1% → 5% → 25% → 100%) targeting cohorts by device model, region, carrier, and OS version. Rollouts can be paused, resumed, or rolled back.
2. **Secure Update Delivery**: Devices download OTA update packages from a global CDN, with cryptographic signature verification (code signing) to ensure authenticity and integrity.
3. **Update Check & Notification**: Devices periodically check for available updates. When an update is available, the device notifies the user, who can download immediately or defer.
4. **Reliable Download**: Support pause/resume downloads over unreliable networks (cellular, Wi-Fi). Use delta/incremental updates to minimize download size.
5. **Health Monitoring & Auto-Rollback**: Monitor device health metrics post-update (crash rates, boot loops, battery drain). Automatically pause rollout if anomaly thresholds are exceeded.

#### Nice to Have (P1)
- A/B testing of OS features via staged rollout to different cohorts.
- Forced updates for critical security patches (user cannot defer).
- Carrier-specific update approvals (carrier certification before rollout).
- Bandwidth-aware scheduling (prefer Wi-Fi, off-peak hours).
- Rollback to previous OS version on device if update causes boot loop.

#### Below the Line (Out of Scope)
- Building the actual OS update package (compilation, testing).
- App store / application updates (only OS-level updates).
- Device hardware provisioning or factory setup.
- MDM (Mobile Device Management) integration.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Scale** | 2 billion active devices worldwide | Android/iOS-scale global fleet |
| **Download Throughput** | 50+ PB distributed in a single rollout | OS update ~2-5 GB × 2B devices |
| **Update Check Latency** | < 500ms | Device check should be fast |
| **Staged Rollout Precision** | ±0.5% of target cohort percentage | 1% rollout means 1% ± 0.5%, not 5% |
| **Security** | Zero unauthorized updates installed | Supply chain attack = catastrophic |
| **Availability** | 99.99% for update check API | Devices must always be able to check |
| **Download Resilience** | Resume after any interruption | Cellular drops, battery dies, reboot |
| **Anomaly Detection** | Detect issues within 1 hour of rollout start | Fast feedback loop for rollback decisions |
| **Rollback Speed** | Pause rollout globally within 5 minutes | Halt bad updates before more devices affected |

### Capacity Estimation

```
Global Device Fleet:
  Active devices: 2 billion
  Device models: 10,000+ (fragmentation)
  Regions: 200+ countries
  Carriers: 500+

Update Package:
  Full OTA package: ~2-5 GB
  Delta (incremental) package: ~500 MB - 1.5 GB (from previous version)
  Packages per release: 100-500 (one per source_version × device_model combination)

Update Check Traffic:
  Devices check every 24 hours (default)
  2B devices / 86400 sec = ~23K checks/sec (evenly distributed)
  Peak (after release announcement): 10x → ~230K checks/sec
  Request size: ~200 bytes, Response: ~500 bytes
  Bandwidth: 230K × 700 bytes = ~160 MB/sec

Download Traffic (during active rollout):
  Stage: 1% rollout = 20M devices
  20M × 1 GB (delta) = 20 PB total download
  Spread over 7 days: 20 PB / 604800 sec = ~33 GB/sec
  100% rollout: 2B × 1 GB = 2 EB total → spread over 30 days = ~770 GB/sec peak
  CDN absorbs 99%+ of this traffic

Storage:
  Update packages: 500 packages × 5 GB = 2.5 TB per release
  Active releases: ~10 → 25 TB on CDN origin
  CDN edge cache: replicated across 200+ PoPs
  
  Device state (rollout membership): 2B × 50 bytes = ~100 GB
  Rollout configuration: ~1 MB per rollout
  Health metrics: 2B × 100 bytes/day = ~200 GB/day

Rollout Configuration:
  Active rollouts: ~50 (different OS versions, regions, models)
  Cohort targeting rules: ~100 rules per rollout
  Stage transitions: ~5-10 per rollout lifecycle
```

---

## 2️⃣ Core Entities

### Entity 1: Release
```java
public class Release {
    private final String releaseId;              // "android-15.1.0-2025.01"
    private final String osVersion;              // "15.1.0"
    private final String buildNumber;            // "AP1A.240905.004"
    private final Instant createdAt;
    private final String createdBy;              // Release manager
    private final ReleaseStatus status;          // DRAFT, STAGED, ACTIVE, PAUSED, COMPLETE, ROLLED_BACK
    private final List<UpdatePackage> packages;  // One per source_version × device_model
    private final RolloutConfig rolloutConfig;
    private final HealthThresholds healthThresholds;
}

public enum ReleaseStatus {
    DRAFT,        // Packages uploaded, not yet rolling out
    STAGED,       // Rollout configured, awaiting approval
    ACTIVE,       // Currently rolling out to devices
    PAUSED,       // Rollout paused (manual or auto)
    COMPLETE,     // 100% rollout achieved
    ROLLED_BACK   // Rollout stopped, devices directed to previous version
}
```

### Entity 2: Update Package
```java
public class UpdatePackage {
    private final String packageId;              // UUID
    private final String releaseId;
    private final String targetDeviceModel;      // "Pixel 8 Pro", "Galaxy S24"
    private final String sourceOsVersion;        // "15.0.0" (delta from this version)
    private final String targetOsVersion;        // "15.1.0"
    private final PackageType type;              // FULL or DELTA
    private final long sizeBytes;                // 1.2 GB
    private final String sha256Checksum;
    private final String signatureChain;         // Code-signing certificate chain
    private final String cdnUrl;                 // https://cdn.os-updates.com/packages/{id}
    private final Instant uploadedAt;
}

public enum PackageType {
    FULL,    // Complete OS image (any source version → target)
    DELTA    // Incremental patch (specific source version → target)
}
```

### Entity 3: Rollout Configuration
```java
public class RolloutConfig {
    private final String releaseId;
    private final List<RolloutStage> stages;           // [1%, 5%, 25%, 50%, 100%]
    private final CohortTargeting targeting;            // Which devices are eligible
    private final Duration stageDuration;               // Min time at each stage before advancing
    private final boolean autoAdvance;                  // Auto-advance if health is good
    private final boolean forceUpdate;                  // User cannot defer (security patches)
}

public class RolloutStage {
    private final int stageNumber;
    private final double targetPercentage;              // 0.01, 0.05, 0.25, 0.50, 1.0
    private final Instant startedAt;
    private final Instant completedAt;
    private final StageStatus status;                   // PENDING, ACTIVE, COMPLETED, SKIPPED
    private final long devicesTargeted;
    private final long devicesUpdated;
    private final long devicesFailed;
}

public class CohortTargeting {
    private final Set<String> deviceModels;             // ["Pixel 8", "Pixel 8 Pro"] or ["*"]
    private final Set<String> regions;                  // ["US", "CA", "GB"] or ["*"]
    private final Set<String> carriers;                 // ["Verizon", "T-Mobile"] or ["*"]
    private final Set<String> sourceOsVersions;         // ["15.0.0", "15.0.1"]
    private final Set<String> excludeDeviceIds;         // Blacklisted devices (known issues)
    private final Set<String> betaUserGroups;           // ["dogfood", "beta-testers"]
}
```

### Entity 4: Device State
```java
public class DeviceState {
    private final String deviceId;                      // Unique device identifier
    private final String deviceModel;                   // "Pixel 8 Pro"
    private final String currentOsVersion;              // "15.0.0"
    private final String carrier;                       // "Verizon"
    private final String region;                        // "US"
    private final Instant lastCheckIn;                  // Last update check
    private final UpdateState updateState;              // NONE, DOWNLOADING, DOWNLOADED, INSTALLING, INSTALLED, FAILED
    private final String assignedReleaseId;             // Which release this device is assigned to
    private final double rolloutBucket;                 // 0.0-1.0 deterministic hash for staged rollout
}

public enum UpdateState {
    NONE,           // No pending update
    NOTIFIED,       // User notified of available update
    DOWNLOADING,    // Download in progress
    DOWNLOADED,     // Download complete, awaiting install
    INSTALLING,     // Installation in progress
    INSTALLED,      // Successfully updated
    FAILED,         // Update failed (download or install)
    DEFERRED        // User chose to defer
}
```

---

## 3️⃣ API Design

### 1. Check for Update (Device → Server)
```
POST /api/v1/updates/check

Request:
{
  "device_id": "dev_abc123",
  "device_model": "Pixel 8 Pro",
  "current_os_version": "15.0.0",
  "build_number": "AP1A.240805.003",
  "carrier": "Verizon",
  "region": "US",
  "network_type": "WIFI",
  "battery_level": 75,
  "storage_available_gb": 12.5
}

Response (200 OK — update available):
{
  "update_available": true,
  "release_id": "android-15.1.0",
  "target_os_version": "15.1.0",
  "package": {
    "package_id": "pkg_xyz789",
    "type": "DELTA",
    "size_bytes": 1200000000,
    "download_url": "https://cdn.os-updates.com/pkg/pkg_xyz789",
    "sha256": "a1b2c3d4e5f6...",
    "signature": "MEUCIQD...",
    "release_notes_url": "https://os-updates.com/notes/15.1.0"
  },
  "force_update": false,
  "defer_until": "2025-02-10T00:00:00Z"
}

Response (200 OK — no update):
{
  "update_available": false,
  "next_check_seconds": 86400
}
```

### 2. Report Download/Install Status (Device → Server)
```
POST /api/v1/updates/status

Request:
{
  "device_id": "dev_abc123",
  "release_id": "android-15.1.0",
  "package_id": "pkg_xyz789",
  "status": "INSTALLED",
  "new_os_version": "15.1.0",
  "install_duration_seconds": 420,
  "bytes_downloaded": 1200000000,
  "download_duration_seconds": 1800,
  "error_code": null
}

Response (200 OK):
{
  "acknowledged": true
}
```

### 3. Report Device Health (Device → Server, periodic)
```
POST /api/v1/health/report

Request:
{
  "device_id": "dev_abc123",
  "os_version": "15.1.0",
  "uptime_seconds": 3600,
  "crash_count_last_24h": 0,
  "anr_count_last_24h": 1,
  "battery_drain_percent_per_hour": 3.2,
  "boot_success": true,
  "boot_time_seconds": 25
}

Response (200 OK):
{ "acknowledged": true }
```

### 4. Create Rollout (Release Manager → Control Plane)
```
POST /api/v1/rollouts

Request:
{
  "release_id": "android-15.1.0",
  "stages": [
    { "percentage": 1, "min_duration_hours": 24 },
    { "percentage": 5, "min_duration_hours": 48 },
    { "percentage": 25, "min_duration_hours": 72 },
    { "percentage": 50, "min_duration_hours": 48 },
    { "percentage": 100 }
  ],
  "targeting": {
    "device_models": ["Pixel 8", "Pixel 8 Pro"],
    "regions": ["US", "CA"],
    "source_os_versions": ["15.0.0", "15.0.1"]
  },
  "health_thresholds": {
    "max_crash_rate_increase_percent": 5,
    "max_boot_failure_rate_percent": 0.1,
    "max_battery_drain_increase_percent": 20
  },
  "auto_advance": true,
  "force_update": false
}

Response (201 Created):
{
  "rollout_id": "roll_abc123",
  "status": "STAGED",
  "estimated_completion_days": 10,
  "eligible_devices": 18500000
}
```

### 5. Pause/Resume/Rollback Rollout
```
POST /api/v1/rollouts/{rollout_id}/pause
POST /api/v1/rollouts/{rollout_id}/resume
POST /api/v1/rollouts/{rollout_id}/rollback

Response (200 OK):
{
  "rollout_id": "roll_abc123",
  "status": "PAUSED",
  "paused_at": "2025-01-12T15:30:00Z",
  "devices_updated_so_far": 185000,
  "devices_remaining": 18315000
}
```

### 6. Rollout Dashboard
```
GET /api/v1/rollouts/{rollout_id}/status

Response (200 OK):
{
  "rollout_id": "roll_abc123",
  "release_id": "android-15.1.0",
  "status": "ACTIVE",
  "current_stage": { "number": 2, "percentage": 5 },
  "progress": {
    "eligible_devices": 18500000,
    "devices_notified": 925000,
    "devices_downloading": 45000,
    "devices_downloaded": 680000,
    "devices_installed": 620000,
    "devices_failed": 1200,
    "install_success_rate": 99.81
  },
  "health": {
    "crash_rate_before": 0.5,
    "crash_rate_after": 0.52,
    "crash_rate_increase_percent": 4.0,
    "boot_failure_rate": 0.01,
    "battery_drain_change_percent": 2.5,
    "status": "HEALTHY"
  },
  "next_stage_eligible_at": "2025-01-14T10:00:00Z"
}
```

---

## 4️⃣ Data Flow

### Flow 1: Device Checks for Update
```
1. Device wakes up (daily schedule + jitter)
   ↓
2. Device calls POST /updates/check with its device_id, model, OS version, region, carrier
   ↓
3. Update Check Service:
   a. Look up active rollouts for this device's targeting criteria
   b. For matching rollout: check if device falls within current rollout percentage
      → device.rolloutBucket <= rollout.currentStage.percentage ? ELIGIBLE : NOT_YET
   c. If eligible: find the correct update package (delta for this source version + model)
   d. Return package metadata (CDN URL, checksum, signature)
   ↓
4. Device shows notification to user: "Update available: OS 15.1.0 (1.2 GB)"
   ↓
5. User taps "Download" (or auto-download on Wi-Fi if configured)
   ↓
6. Device downloads from CDN:
   a. HTTP range requests for pause/resume support
   b. Verify SHA-256 checksum after download
   c. Verify code-signing signature against trusted certificate chain
   ↓
7. Device installs update (during reboot or A/B partition switch)
   ↓
8. Device reports status: POST /updates/status { status: "INSTALLED" }
   ↓
9. Device periodically reports health: POST /health/report { crash_count, boot_time, ... }
```

### Flow 2: Release Manager Creates Staged Rollout
```
1. Release team uploads update packages to Object Storage (S3)
   ↓
2. Packages distributed to CDN edge locations worldwide
   ↓
3. Release manager creates rollout via API:
   - Stages: 1% → 5% → 25% → 50% → 100%
   - Targeting: Pixel 8 devices in US/CA on OS 15.0.x
   - Health thresholds: crash rate increase < 5%, boot failure < 0.1%
   ↓
4. Rollout starts at Stage 1 (1%):
   - 1% of eligible devices will get the update on next check-in
   - Device assignment is DETERMINISTIC (hash(device_id, rollout_id) < 0.01)
   ↓
5. Health Monitor watches device health reports for 24 hours:
   - Compares crash rate of updated devices vs non-updated (control group)
   - If healthy → auto-advance to Stage 2 (5%)
   - If unhealthy → auto-PAUSE rollout, alert release manager
   ↓
6. Repeat stages until 100% or pause/rollback
```

### Flow 3: Anomaly Detection → Auto-Pause
```
1. Health reports stream into Health Aggregation Service
   ↓
2. Stream processor computes per-rollout metrics:
   - Crash rate for updated devices (treatment group)
   - Crash rate for non-updated devices (control group)
   - Boot failure rate, battery drain, ANR rate
   ↓
3. Compare: if treatment_crash_rate > control_crash_rate * (1 + threshold%):
   → ANOMALY DETECTED
   ↓
4. Auto-pause rollout:
   - Set rollout status to PAUSED
   - New devices checking in will NOT get the update
   - Devices already downloaded but not installed → "Update postponed" notification
   ↓
5. Alert release manager via PagerDuty/Slack/email
   ↓
6. Release manager investigates → decide: resume, modify targeting, or rollback
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    2 BILLION MOBILE DEVICES                                 │
│                                                                              │
│  Daily: POST /updates/check → "Is there an update for me?"                 │
│  Download: GET cdn.os-updates.com/pkg/{id} → download OTA package          │
│  Report: POST /updates/status + POST /health/report                        │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                     GLOBAL CDN (CloudFront / Akamai)                        │
│                                                                              │
│  • 200+ edge PoPs worldwide                                                 │
│  • Caches OTA packages (~2.5 TB per release)                               │
│  • Handles 99% of download bandwidth                                        │
│  • HTTP range requests for pause/resume                                     │
│  • Peak: hundreds of GB/sec during 100% rollout                            │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ Cache miss → origin
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                     CONTROL PLANE (low-traffic, high-importance)            │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────┐          │
│  │              UPDATE CHECK SERVICE                             │          │
│  │              (Stateless, 50+ pods per region)                │          │
│  │                                                               │          │
│  │  For each device check-in:                                   │          │
│  │  1. Match device to eligible rollouts (targeting rules)      │          │
│  │  2. Check rollout stage: hash(device_id) < stage_percentage? │          │
│  │  3. If eligible: return package URL + checksum + signature   │          │
│  │  4. If not: return "no update" + next_check_seconds          │          │
│  │                                                               │          │
│  │  Cache: rollout configs cached in-memory (refreshed 30s)     │          │
│  └──────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────┐          │
│  │              ROLLOUT MANAGER                                  │          │
│  │              (Control plane for release managers)             │          │
│  │                                                               │          │
│  │  • Create/configure staged rollouts                          │          │
│  │  • Pause/resume/rollback rollouts                            │          │
│  │  • Auto-advance stages based on health                       │          │
│  │  • Dashboard: progress, health, metrics                      │          │
│  └──────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────┐          │
│  │              HEALTH MONITORING SERVICE                        │          │
│  │                                                               │          │
│  │  • Ingest health reports from devices (Kafka → Flink)        │          │
│  │  • Compute treatment vs control group metrics                │          │
│  │  • Detect anomalies (crash rate, boot failures, battery)     │          │
│  │  • Auto-pause rollout if thresholds exceeded                 │          │
│  │  • Alert release managers                                     │          │
│  └──────────────────────────────────────────────────────────────┘          │
└───────────────────────────────────────────────────────────────────────────────┘

                ┌────────────────────────────────────────┐
                │           DATA STORES                   │
                │                                          │
                │  ┌──────────────┐  ┌────────────────┐  │
                │  │ Rollout DB    │  │ Package Store   │  │
                │  │ (PostgreSQL)  │  │ (S3 / GCS)     │  │
                │  │               │  │                 │  │
                │  │ • Rollouts    │  │ • OTA packages  │  │
                │  │ • Stages      │  │ • 2.5 TB/release│  │
                │  │ • Device state│  │ • Signed, hashed│  │
                │  │ • Targeting   │  │                 │  │
                │  └──────────────┘  └────────────────┘  │
                │                                          │
                │  ┌──────────────┐  ┌────────────────┐  │
                │  │ Health Metrics│  │ Kafka           │  │
                │  │ (ClickHouse)  │  │                 │  │
                │  │               │  │ • Device health │  │
                │  │ • Crash rates │  │ • Status reports│  │
                │  │ • Boot metrics│  │ • Rollout events│  │
                │  │ • Per-rollout │  │                 │  │
                │  └──────────────┘  └────────────────┘  │
                └────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **CDN** | Distribute OTA packages globally | CloudFront / Akamai | 200+ PoPs, hundreds GB/sec |
| **Update Check Service** | Determine if device should receive update | Go/Java on K8s | 50+ pods/region, stateless |
| **Rollout Manager** | Configure/control staged rollouts | Java/Spring Boot | 3-node HA |
| **Health Monitor** | Aggregate health, detect anomalies, auto-pause | Flink + ClickHouse | Stream processing |
| **Rollout DB** | Rollout config, stage state, device assignment | PostgreSQL | Replicated, 3 regions |
| **Package Store** | OTA packages (origin for CDN) | S3 / GCS | Object storage, ~25 TB |
| **Kafka** | Device health reports, status events | Apache Kafka | Partitioned by device_id |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Deterministic Staged Rollout — Assigning Devices to Stages

**The Problem**: For a 1% rollout, exactly ~1% of eligible devices should receive the update. The assignment must be deterministic (same device always gets the same answer) and consistent (advancing from 1% to 5% should include all devices from the 1% stage plus new ones).

```java
public class StagedRolloutAssigner {

    /**
     * Deterministic assignment using hash(device_id, rollout_id):
     * 
     * 1. Compute a deterministic bucket: hash(device_id + rollout_id) mod 10000
     *    → produces a value 0-9999 (0.01% granularity)
     * 2. Device is eligible if bucket < (stage_percentage × 10000)
     *    → 1% stage: bucket < 100
     *    → 5% stage: bucket < 500
     *    → 100% stage: bucket < 10000 (all devices)
     * 
     * Key properties:
     *   - DETERMINISTIC: same device, same rollout → always same answer
     *   - CONSISTENT: 1% devices are a subset of 5% devices (monotonic)
     *   - UNIFORM: hash distributes evenly → accurate percentages
     *   - INDEPENDENT: different rollouts assign different device subsets
     */
    
    private static final int BUCKET_COUNT = 10000; // 0.01% granularity
    
    public boolean isDeviceEligible(String deviceId, String rolloutId, double stagePercentage) {
        // Deterministic hash: same inputs → same output
        int bucket = computeBucket(deviceId, rolloutId);
        int threshold = (int) (stagePercentage * BUCKET_COUNT);
        return bucket < threshold;
    }
    
    private int computeBucket(String deviceId, String rolloutId) {
        String input = deviceId + ":" + rolloutId;
        byte[] hash = Hashing.murmurHash3_128().hashString(input, StandardCharsets.UTF_8).asBytes();
        // Use first 4 bytes as unsigned int, mod bucket count
        int hashInt = Math.abs(ByteBuffer.wrap(hash).getInt());
        return hashInt % BUCKET_COUNT;
    }
    
    /**
     * Full eligibility check during update check-in:
     */
    public UpdateCheckResult checkForUpdate(DeviceState device) {
        // Step 1: Find active rollouts matching this device's attributes
        List<Rollout> matchingRollouts = rolloutService.findMatchingRollouts(
            device.getDeviceModel(), device.getRegion(), 
            device.getCarrier(), device.getCurrentOsVersion());
        
        for (Rollout rollout : matchingRollouts) {
            if (rollout.getStatus() != RolloutStatus.ACTIVE) continue;
            
            // Step 2: Check if device falls within current stage percentage
            double currentPercentage = rollout.getCurrentStage().getTargetPercentage();
            if (isDeviceEligible(device.getDeviceId(), rollout.getRolloutId(), currentPercentage)) {
                
                // Step 3: Find correct package for this device
                UpdatePackage pkg = findPackage(rollout.getReleaseId(), 
                    device.getDeviceModel(), device.getCurrentOsVersion());
                
                if (pkg != null) {
                    return UpdateCheckResult.available(rollout, pkg);
                }
            }
        }
        
        return UpdateCheckResult.noUpdate();
    }
}
```

**Staged rollout consistency**:
```
Device "dev_abc" gets bucket 42 (out of 10000):
  1% stage (threshold 100):   42 < 100  → ELIGIBLE ✓
  5% stage (threshold 500):   42 < 500  → ELIGIBLE ✓ (still included!)
  25% stage (threshold 2500): 42 < 2500 → ELIGIBLE ✓
  100% stage:                 42 < 10000 → ELIGIBLE ✓

Device "dev_xyz" gets bucket 3750:
  1% stage:   3750 < 100  → NOT YET
  5% stage:   3750 < 500  → NOT YET
  25% stage:  3750 < 2500 → NOT YET
  50% stage:  3750 < 5000 → ELIGIBLE ✓ (first time!)
  100% stage: 3750 < 10000 → ELIGIBLE ✓

Key: advancing stages NEVER removes a device from eligibility.
     Devices that got the update at 1% are still "updated" at 5%.
```

---

### Deep Dive 2: Thundering Herd Prevention — Update Check Scheduling

**The Problem**: If 2B devices all check for updates at the same time (e.g., after a press announcement), the Update Check Service would be overwhelmed. We need to spread the check-in load evenly.

```java
public class UpdateCheckScheduler {

    /**
     * Anti-thundering-herd strategies:
     * 
     * 1. Randomized jitter: each device checks at random time within 24h window
     *    next_check = base_interval + random(0, jitter_window)
     * 
     * 2. Server-controlled interval: server tells device when to next check
     *    "next_check_seconds": 86400 + random(0, 3600)
     * 
     * 3. Exponential backoff on "no update" response:
     *    No update → check again in 24h
     *    Still no update → 48h, 72h... (up to 7 days)
     *    Update available → back to 24h after install
     * 
     * 4. Pre-announcement warm-up: before public announcement,
     *    start rollout to 0.1% so CDN edge caches are warm
     */
    
    public int computeNextCheckSeconds(DeviceState device, boolean updateAvailable) {
        if (updateAvailable) {
            // Check back in 1 hour if user deferred
            return 3600 + ThreadLocalRandom.current().nextInt(600); // 1h ± 10min
        }
        
        // Base interval: 24 hours
        int base = 86400;
        
        // Add jitter: ±2 hours
        int jitter = ThreadLocalRandom.current().nextInt(7200) - 3600;
        
        return base + jitter;
    }
}
```

**Check-in traffic pattern**:
```
Without jitter (bad):
  All 2B devices check at midnight UTC → 2B requests in ~1 minute → CRASH

With 24h jitter (good):
  2B / 86400 = ~23K req/sec → smooth, manageable

After press announcement (spike):
  Users manually trigger check → 100M extra checks in 1 hour → ~28K extra/sec
  Total: ~51K/sec → within capacity (50+ pods per region)
  
  Further mitigation: if server is overloaded, respond with longer next_check interval
  → natural backpressure
```

---

### Deep Dive 3: Security — Supply Chain Protection & Code Signing

**The Problem**: A compromised OTA update could brick 2B devices or install malware on every phone on earth. The update pipeline must be tamper-proof from build to device installation.

```java
public class UpdateSecurityManager {

    /**
     * Multi-layer security:
     * 
     * Layer 1: Code Signing (asymmetric cryptography)
     *   - Build system signs the OTA package with a private key (HSM-protected)
     *   - Device verifies signature using embedded public key (in bootloader, immutable)
     *   - Even if CDN is compromised, device rejects unsigned/tampered packages
     * 
     * Layer 2: Package Integrity (hash verification)
     *   - SHA-256 hash computed at build time
     *   - Device computes hash after download and compares
     *   - Any bit-flip → hash mismatch → reject
     * 
     * Layer 3: TLS for transport
     *   - All API calls and CDN downloads over TLS 1.3
     *   - Certificate pinning on device (prevent MITM)
     * 
     * Layer 4: Metadata signature
     *   - The update check response itself is signed
     *   - Prevents attacker from redirecting device to malicious URL
     * 
     * Layer 5: Build attestation
     *   - Verifiable Build provenance (SLSA Level 3+)
     *   - Build system produces cryptographic attestation of what was built and how
     */
    
    // Device-side verification (happens on device before installation):
    public boolean verifyUpdatePackage(byte[] packageData, UpdatePackage metadata) {
        // Step 1: Verify SHA-256 hash
        String actualHash = sha256(packageData);
        if (!actualHash.equals(metadata.getSha256Checksum())) {
            log.error("HASH MISMATCH: expected={}, actual={}", metadata.getSha256Checksum(), actualHash);
            return false; // Package corrupted or tampered
        }
        
        // Step 2: Verify code-signing signature
        boolean signatureValid = verifySignature(
            packageData, 
            metadata.getSignatureChain(), 
            trustedRootCertificate); // Embedded in bootloader
        
        if (!signatureValid) {
            log.error("SIGNATURE VERIFICATION FAILED for package {}", metadata.getPackageId());
            return false; // Package not signed by trusted authority
        }
        
        // Step 3: Verify target version (prevent downgrade attacks)
        if (metadata.getTargetOsVersion().compareTo(currentOsVersion) <= 0) {
            log.error("DOWNGRADE ATTEMPT: target={} <= current={}", 
                metadata.getTargetOsVersion(), currentOsVersion);
            return false;
        }
        
        return true;
    }
}
```

**Security threat model**:
```
Threat                          Mitigation                         Impact if Bypassed
──────                          ──────────                         ──────────────────
CDN compromise (serve bad pkg)  Code signing + hash verification   Devices reject bad package
MITM on download                TLS 1.3 + certificate pinning     Can't intercept traffic
Replay attack (old version)     Downgrade protection + nonce       Devices reject old versions
Build system compromise         HSM-protected signing keys +       Requires physical HSM access
                                multi-party approval
Metadata tampering              Signed update check responses      Devices reject bad metadata
Insider threat                  Multi-party signing approval +     No single person can sign
                                audit logging
```

---

### Deep Dive 4: Delta Updates — Minimizing Download Size

**The Problem**: A full OS image is 2-5 GB. For 2B devices, that's exabytes of bandwidth. Delta (incremental) updates reduce this to 500 MB - 1.5 GB by shipping only the diff.

```java
public class DeltaUpdateManager {

    /**
     * Delta update strategy:
     * 
     * Full update: complete OS image (any source → target). 2-5 GB.
     * Delta update: binary diff from specific source version → target. 500 MB - 1.5 GB.
     * 
     * We pre-compute deltas for the N most common source versions.
     * Example: if 80% of devices are on 15.0.0 and 15% on 15.0.1:
     *   - Delta from 15.0.0 → 15.1.0 (covers 80%)
     *   - Delta from 15.0.1 → 15.1.0 (covers 15%)
     *   - Full image for all others (covers 5% on older versions)
     * 
     * Tools: bsdiff (binary diff), Puffin (Google's OTA diff for compressed data)
     */
    
    public UpdatePackage selectBestPackage(String deviceModel, String sourceVersion, 
                                            String targetVersion, String networkType) {
        // Try delta first (smaller download)
        UpdatePackage delta = packageStore.findDelta(deviceModel, sourceVersion, targetVersion);
        if (delta != null) {
            return delta;
        }
        
        // No delta available → fall back to full package
        UpdatePackage full = packageStore.findFull(deviceModel, targetVersion);
        
        // If on cellular, warn user about large download
        if ("CELLULAR".equals(networkType) && full.getSizeBytes() > 500_000_000L) {
            // Suggest waiting for Wi-Fi
            return full.withSuggestWifi(true);
        }
        
        return full;
    }
}
```

**Delta savings**:
```
Scenario: 20M devices updating in 1% stage
  Full image: 20M × 3 GB = 60 PB total
  Delta:      20M × 800 MB = 16 PB total
  Savings: 73% bandwidth reduction (~44 PB saved)

Annual CDN cost savings at $0.02/GB:
  44 PB × $0.02/GB × 1000 = $880,000 per rollout stage
  Over full rollout lifecycle: millions of dollars saved
```

---

### Deep Dive 5: Reliable Downloads — Pause, Resume, and Error Recovery

**The Problem**: Devices download 500 MB - 3 GB files over unreliable networks (cellular, spotty Wi-Fi, subway tunnels). Downloads must survive interruptions.

```java
public class ReliableDownloadManager {

    /**
     * Download resilience strategies:
     * 
     * 1. HTTP Range Requests: resume from last byte downloaded
     *    - Device stores: (package_id, bytes_downloaded, file_path)
     *    - On resume: GET /pkg/{id} with Range: bytes={downloaded}-
     *    - CDN must support range requests (all major CDNs do)
     * 
     * 2. Chunk-based verification: verify every 10 MB chunk
     *    - Package includes per-chunk SHA-256 hashes
     *    - If a chunk is corrupt → re-download just that chunk
     *    - Don't wait until end of 3 GB download to discover corruption
     * 
     * 3. Bandwidth-aware scheduling:
     *    - On Wi-Fi: download at full speed
     *    - On cellular: download at reduced rate (avoid data charges)
     *    - On metered network: pause and wait for Wi-Fi
     * 
     * 4. Battery-aware: don't download if battery < 20%
     * 
     * 5. Automatic retry with exponential backoff:
     *    - Network error → retry in 1min, 5min, 30min, 2h, 24h
     */
    
    public void startDownload(UpdatePackage pkg) {
        long existingBytes = getDownloadedBytes(pkg.getPackageId());
        
        HttpURLConnection conn = (HttpURLConnection) new URL(pkg.getCdnUrl()).openConnection();
        
        if (existingBytes > 0) {
            // Resume from where we left off
            conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
        }
        
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(downloadFile, true)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = existingBytes;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                
                // Checkpoint every 10 MB
                if (totalRead % (10 * 1024 * 1024) == 0) {
                    saveDownloadProgress(pkg.getPackageId(), totalRead);
                }
                
                // Check conditions
                if (getBatteryLevel() < 20) { pause("LOW_BATTERY"); return; }
                if (isMeteredNetwork() && !userAllowedCellular()) { pause("METERED"); return; }
            }
            
            // Verify complete download
            verifyAndInstall(pkg);
            
        } catch (IOException e) {
            // Save progress for resume
            saveDownloadProgress(pkg.getPackageId(), totalRead);
            scheduleRetry(pkg, getRetryDelay());
        }
    }
}
```

---

### Deep Dive 6: Health Monitoring — Treatment vs Control Comparison

**The Problem**: After rolling out to 1%, how do we know if the update is causing problems? We need to compare health metrics of updated devices (treatment) against non-updated devices (control) to detect regressions.

```java
public class HealthAnomalyDetector {

    /**
     * Treatment vs Control comparison:
     * 
     * Treatment group: devices that installed the update (1% of fleet)
     * Control group: devices that did NOT install (99% of fleet, same model/region)
     * 
     * Metrics compared:
     *   - Crash rate (crashes per device per day)
     *   - Boot failure rate (% of boots that fail)
     *   - ANR rate (Application Not Responding events)
     *   - Battery drain (% per hour)
     *   - Boot time (seconds)
     * 
     * Statistical test: compare treatment mean vs control mean
     * Trigger: if treatment metric > control metric * (1 + threshold)
     */
    
    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void checkRolloutHealth() {
        for (Rollout rollout : getActiveRollouts()) {
            HealthMetrics treatment = healthStore.getMetrics(
                rollout.getReleaseId(), rollout.getTargetOsVersion(), true);
            HealthMetrics control = healthStore.getMetrics(
                rollout.getReleaseId(), rollout.getSourceOsVersions(), false);
            
            // Compare crash rate
            double crashIncrease = (treatment.getCrashRate() - control.getCrashRate()) 
                / Math.max(control.getCrashRate(), 0.001) * 100;
            
            if (crashIncrease > rollout.getHealthThresholds().getMaxCrashRateIncrease()) {
                autoPauseRollout(rollout, "CRASH_RATE_ANOMALY",
                    String.format("Crash rate increased %.1f%% (threshold: %.1f%%)",
                        crashIncrease, rollout.getHealthThresholds().getMaxCrashRateIncrease()));
            }
            
            // Compare boot failure rate
            if (treatment.getBootFailureRate() > rollout.getHealthThresholds().getMaxBootFailureRate()) {
                autoPauseRollout(rollout, "BOOT_FAILURE_ANOMALY",
                    String.format("Boot failure rate: %.2f%% (threshold: %.2f%%)",
                        treatment.getBootFailureRate() * 100,
                        rollout.getHealthThresholds().getMaxBootFailureRate() * 100));
            }
        }
    }
    
    private void autoPauseRollout(Rollout rollout, String reason, String details) {
        rolloutManager.pauseRollout(rollout.getRolloutId());
        alertService.sendAlert(AlertLevel.CRITICAL,
            "ROLLOUT AUTO-PAUSED: " + rollout.getReleaseId(),
            details + "\nDevices updated: " + rollout.getDevicesInstalled());
    }
}
```

**Health monitoring timeline**:
```
T=0h    Rollout starts at 1% (200K devices)
T=1h    20K devices installed → first health reports arriving
T=2h    50K devices installed → statistically significant sample
T=2h    Health check: crash_rate_treatment=0.52%, crash_rate_control=0.50%
        → 4% increase, threshold is 5% → HEALTHY ✓
T=6h    More data: crash_rate_treatment=0.58%, crash_rate_control=0.50%
        → 16% increase > 5% threshold → ANOMALY DETECTED ⚠️
T=6h    AUTO-PAUSE rollout. Alert sent to release manager.
T=6h    No new devices get the update. 100K devices already updated.
T=7h    Release manager investigates → finds bug in camera driver for Pixel 8
T=8h    Fix built, new release created → new rollout starts
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Rollout assignment** | Deterministic hash (device_id + rollout_id) | Consistent, monotonic (1% ⊂ 5%), no server state needed |
| **Update delivery** | CDN with HTTP range requests | Global distribution, pause/resume, handles exabyte scale |
| **Delta updates** | Pre-computed deltas for top N source versions | 73% bandwidth reduction; fallback to full for rare versions |
| **Security** | Multi-layer: code signing + hash + TLS + attestation | Device rejects tampered packages even if CDN is compromised |
| **Health monitoring** | Treatment vs control group comparison | Isolates update impact from global trends; statistical rigor |
| **Auto-pause** | Threshold-based anomaly detection (5-min intervals) | Fast feedback loop; stops bad rollout before more devices affected |
| **Thundering herd** | Jitter + server-controlled check interval | Spreads 2B check-ins evenly across 24h; backpressure via interval |
| **Control vs data plane** | Separate (control plane = check API, data plane = CDN) | Check API is low-bandwidth/high-importance; CDN is high-bandwidth/cacheable |

## Interview Talking Points

1. **"Deterministic hash for staged rollout"** — hash(device_id, rollout_id) mod 10000. Device gets same bucket every check. 1% stage = bucket < 100. Advancing to 5% = bucket < 500 (superset). No server-side device-to-stage mapping needed.
2. **"Control plane vs data plane separation"** — Check API is lightweight (200 bytes, ~23K QPS). CDN handles heavy lifting (hundreds GB/sec package downloads). CDN can fail without breaking check-ins.
3. **"Treatment vs control health comparison"** — Updated devices (treatment) compared against same-model non-updated devices (control). Isolates update impact from seasonal or global trends. Statistical significance needed before triggering pause.
4. **"Auto-pause within 5 minutes"** — Health check every 5 min. If crash rate increase exceeds threshold → pause rollout → no new devices get the update. Alert to release manager.
5. **"Delta updates save 73% bandwidth"** — Pre-compute binary diffs for the N most common source versions. 800 MB delta vs 3 GB full image. Fallback to full for rare source versions.
6. **"HTTP range requests for resume"** — Device saves bytes_downloaded checkpoint every 10 MB. On interruption, resumes from last checkpoint. CDN supports Range header natively.
7. **"Code signing prevents supply chain attacks"** — Private key in HSM, multi-party approval. Public key embedded in device bootloader (immutable). Even if CDN is hacked, devices reject unsigned packages.
8. **"Jitter prevents thundering herd"** — Each device adds ±2h random jitter to 24h check interval. Server can dynamically increase interval under load. Press announcements pre-warm CDN cache at 0.1%.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **CDN Design** | Same content distribution pattern | CDN is generic; OTA adds staged rollout + security + health monitoring |
| **Feature Flags / A-B Testing** | Same staged rollout concept | Feature flags are lightweight (config); OTA ships entire OS images |
| **App Store Updates** | Similar update distribution | App updates are smaller (~100 MB); OS updates are 1-5 GB with bootloader concerns |
| **Deployment Pipeline (K8s)** | Same canary/staged rollout | K8s deploys to servers; OTA deploys to 2B uncontrolled client devices |
| **Notification System** | Same push notification pattern | Notification is lightweight; OTA notification triggers multi-GB download |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **CDN** | CloudFront / Akamai | P2P distribution (BitTorrent-style) | Reduce CDN costs; works for non-sensitive updates |
| **Health metrics** | ClickHouse | Prometheus + Grafana | Smaller scale; existing monitoring stack |
| **Rollout DB** | PostgreSQL | DynamoDB | Serverless; global replication built-in |
| **Streaming** | Kafka → Flink | Kafka Streams | Simpler; avoid separate Flink cluster |
| **Check API** | REST (POST) | gRPC | Lower latency; smaller payload; binary protocol |
| **Signing** | RSA/ECDSA + X.509 chain | Sigstore/Cosign | Modern supply chain security; transparency logs |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Android OTA Update Architecture (AOSP documentation)
