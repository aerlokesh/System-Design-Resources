# Design an Evaluation Framework for Ads Ranking — Hello Interview Framework

> **Question**: Design an evaluation framework for ads ranking — a system that measures, tests, and validates the quality of ad ranking models before and after deployment at scale.
>
> **Source**: [Exponent](https://www.tryexponent.com/questions/3095/design-evaluation-framework-ads-ranking)
>
> **Asked at**: Meta
>
> **Difficulty**: Hard | **Level**: Staff/Senior (ML Systems Design)

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## Context: What is Ads Ranking?

**The Ads Pipeline (simplified)**:
```
User opens Facebook/Instagram
    ↓
Ad Request: "Show me ads for this user on this page"
    ↓
STAGE 1: Candidate Selection (1M ads → 1000 ads)
  Filter by targeting (age, location, interests), budget, status
    ↓
STAGE 2: Ranking (1000 ads → 10 ads)
  ML model predicts: P(click), P(conversion), P(engagement)
  Score = bid × P(click) × quality_factor
  Select top-10 by score
    ↓
STAGE 3: Auction & Pricing
  Determine which ads win and how much advertiser pays
    ↓
STAGE 4: Serve ads to user
```

**What is an "Evaluation Framework"?** A system that answers: **"Is this new ranking model better than the current one?"** before you deploy it to billions of users and billions of dollars in ad revenue. Getting this wrong means either (a) shipping a bad model that loses revenue, or (b) not shipping a good model and leaving money on the table.

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Offline Evaluation**: Run a new ranking model against historical data (logged impressions/clicks) and compute quality metrics (AUC, logloss, calibration, NDCG) — without serving real traffic.
2. **Online A/B Testing**: Split live traffic between control (current model) and treatment (new model), measure business metrics (revenue, CTR, user engagement) with statistical significance.
3. **Counterfactual Evaluation**: Estimate how a new model *would have* performed on historical traffic without actually serving it (inverse propensity scoring, replay methods).
4. **Metrics Computation**: Compute and visualize ML metrics (AUC-ROC, logloss, calibration curves) and business metrics (revenue per 1000 impressions (RPM), CTR, conversion rate, user session time).
5. **Experiment Management**: Create, configure, monitor, and analyze A/B experiments with guardrail metrics, automatic early stopping, and statistical significance detection.
6. **Model Registry & Versioning**: Track model versions, training data, hyperparameters, and link to evaluation results for reproducibility.

#### Nice to Have (P1)
- Interleaving experiments (show ads from both models in the same session, compare head-to-head)
- Shadow mode (new model scores all requests but doesn't affect serving — log predictions for offline comparison)
- Automatic model promotion (if metrics pass, auto-deploy to production)
- Canary deployment (serve new model to 0.1% → 1% → 10% → 100% gradually)
- Segment-level analysis (evaluate per country, per ad category, per user segment)

#### Below the Line (Out of Scope)
- Building the ranking model itself (feature engineering, training)
- Ad serving infrastructure (load balancing, CDN)
- Billing and payment systems
- Creative optimization (ad image/text quality)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Offline eval latency** | < 1 hour for full eval on 1B impressions | Engineers iterate daily; slow evals block progress |
| **A/B test duration** | 1-2 weeks for statistical significance | Enough data for reliable p-values at 95% confidence |
| **Metrics freshness** | < 1 hour for offline; real-time for A/B | Engineers need fast feedback; business needs live monitoring |
| **Statistical rigor** | p < 0.05, power > 0.8, MDE < 0.5% | Must detect small revenue changes at Meta's scale |
| **Scale** | 10B ad impressions/day, 100M users | Meta's actual ads volume |
| **Correctness** | Zero bugs in metric computation | Wrong metrics → wrong deployment decisions → revenue loss |
| **Reproducibility** | Same inputs → same metrics, always | Debugging requires exact reproduction |

### Capacity Estimation

```
Meta Ads Scale:
  Daily ad impressions: ~10 billion
  Daily ad clicks: ~500 million (5% avg CTR)
  Daily revenue: ~$300M (ads)
  
  Active ranking models in evaluation: 10-50 at any time
  A/B experiments running concurrently: 20-30
  Engineers running offline evals: 50-100/day

Offline Evaluation:
  Historical data per eval: 1-7 days = 10-70B impressions
  Per-impression log: ~500 bytes (features, predictions, labels)
  Data per eval: 10B × 500 bytes = 5 TB (1 day)
  Spark/MapReduce cluster: 1000 nodes × 1 hour = 1000 node-hours per eval

A/B Test Traffic:
  Typical treatment group: 1% of traffic = 100M impressions/day
  Minimum test duration: 7 days = 700M impressions
  Metrics computed: 50+ metrics per experiment per day

Metric Storage:
  Per experiment per day: ~50 metrics × 100 segments = 5000 time series
  Total active: 30 experiments × 5000 = 150K time series
  Retention: 2 years for completed experiments
```

---

## 2️⃣ Core Entities

### Entity 1: Ranking Model
```java
public class RankingModel {
    private final String modelId;              // "ads_ranking_v42"
    private final String modelVersion;         // "v42.3.1"
    private final ModelType type;              // CTR_PREDICTION, CVR_PREDICTION, MULTI_TASK
    private final String trainingDataset;      // "ads_train_2025_q1"
    private final Map<String, String> hyperparameters;
    private final String artifactPath;         // S3 path to model binary
    private final Instant trainedAt;
    private final Map<String, Double> offlineMetrics;  // Pre-computed offline results
    private final ModelStatus status;          // TRAINING, EVALUATING, SHADOW, CANARY, PRODUCTION, RETIRED
}
```

### Entity 2: Evaluation Job
```java
public class EvaluationJob {
    private final String evalId;               // UUID
    private final String modelId;              // Model being evaluated
    private final EvalType evalType;           // OFFLINE, COUNTERFACTUAL, SHADOW
    private final String baselineModelId;      // Compare against this model
    private final DateRange dataRange;         // Historical data window
    private final List<String> metricsToCompute; // ["auc", "logloss", "calibration", "ndcg"]
    private final List<String> segments;       // ["country:US", "ad_type:video", "all"]
    private final EvalStatus status;           // PENDING, RUNNING, COMPLETED, FAILED
    private final Map<String, MetricResult> results;
    private final Instant submittedAt;
    private final Instant completedAt;
}
```

### Entity 3: A/B Experiment
```java
public class ABExperiment {
    private final String experimentId;         // "exp_2025_q1_ctr_model_v42"
    private final String name;
    private final String description;
    private final String controlModelId;       // Current production model
    private final String treatmentModelId;     // New candidate model
    private final double trafficPercent;       // 1.0 = 1% of traffic
    private final TrafficSplitMethod splitMethod; // USER_HASH, SESSION_HASH, REQUEST_HASH
    private final DateRange scheduledDuration;
    private final List<MetricDefinition> primaryMetrics;    // Revenue, CTR
    private final List<MetricDefinition> guardrailMetrics;  // User satisfaction, ad load
    private final StatisticalConfig statsConfig;            // Confidence level, MDE, power
    private final ExperimentStatus status;     // DRAFT, RUNNING, ANALYZING, COMPLETED, KILLED
    private final ExperimentResult result;     // INCONCLUSIVE, CONTROL_WINS, TREATMENT_WINS
}

public class StatisticalConfig {
    private final double confidenceLevel;      // 0.95 (95%)
    private final double minimumDetectableEffect; // 0.005 (0.5% change in revenue)
    private final double statisticalPower;     // 0.80
    private final CorrectionMethod multipleTestingCorrection; // BONFERRONI, FDR
    private final boolean enableSequentialTesting;  // ALWAYS_VALID_P_VALUES for peeking
}
```

### Entity 4: Metric Result
```java
public class MetricResult {
    private final String metricName;           // "revenue_per_mille"
    private final String segment;              // "all" or "country:US"
    private final double controlValue;
    private final double treatmentValue;
    private final double absoluteDiff;         // treatment - control
    private final double relativeDiff;         // (treatment - control) / control
    private final double pValue;
    private final double confidenceIntervalLow;
    private final double confidenceIntervalHigh;
    private final boolean isStatisticallySignificant;
    private final long sampleSizeControl;
    private final long sampleSizeTreatment;
}
```

### Entity 5: Impression Log (the raw data)
```java
public class ImpressionLog {
    private final String requestId;
    private final String userId;
    private final String adId;
    private final String advertiserId;
    private final int position;                // Slot position (1 = top)
    private final String modelVersion;         // Which model ranked this
    private final double predictedCTR;         // Model's P(click) prediction
    private final double predictedCVR;         // Model's P(conversion) prediction
    private final double bid;                  // Advertiser's bid
    private final double rankScore;            // Final ranking score
    private final boolean clicked;             // Label: did user click?
    private final boolean converted;           // Label: did user convert?
    private final double revenue;              // Actual revenue generated
    private final Instant timestamp;
    private final Map<String, String> context; // User features, ad features
    private final String experimentId;         // Which A/B experiment (if any)
    private final String experimentGroup;      // "control" or "treatment"
}
```

---

## 3️⃣ API Design

### 1. Submit Offline Evaluation
```
POST /api/v1/evaluations

Request:
{
  "model_id": "ads_ranking_v42",
  "baseline_model_id": "ads_ranking_v41_prod",
  "eval_type": "OFFLINE",
  "data_range": { "start": "2025-01-01", "end": "2025-01-07" },
  "metrics": ["auc_roc", "logloss", "calibration", "ndcg@10", "revenue_per_mille"],
  "segments": ["all", "country:US", "country:IN", "ad_type:video", "ad_type:static"]
}

Response (202 Accepted):
{
  "eval_id": "eval_abc123",
  "status": "PENDING",
  "estimated_completion_minutes": 45,
  "data_size_impressions": 70000000000
}
```

### 2. Get Evaluation Results
```
GET /api/v1/evaluations/{eval_id}/results

Response:
{
  "eval_id": "eval_abc123",
  "status": "COMPLETED",
  "model_id": "ads_ranking_v42",
  "baseline_model_id": "ads_ranking_v41_prod",
  "results": {
    "all": {
      "auc_roc": { "model": 0.7823, "baseline": 0.7801, "diff": "+0.28%", "significant": true },
      "logloss": { "model": 0.3412, "baseline": 0.3445, "diff": "-0.96%", "significant": true },
      "calibration": { "model": 1.012, "baseline": 0.998, "note": "Slightly over-predicting" },
      "ndcg@10": { "model": 0.654, "baseline": 0.648, "diff": "+0.93%" },
      "revenue_per_mille": { "model": 12.45, "baseline": 12.38, "diff": "+0.57%" }
    },
    "country:US": { ... },
    "ad_type:video": { ... }
  },
  "recommendation": "PROCEED_TO_AB_TEST",
  "warnings": ["Calibration slightly high (1.012 vs ideal 1.000)"]
}
```

### 3. Create A/B Experiment
```
POST /api/v1/experiments

Request:
{
  "name": "CTR Model v42 vs v41",
  "control_model_id": "ads_ranking_v41_prod",
  "treatment_model_id": "ads_ranking_v42",
  "traffic_percent": 1.0,
  "split_method": "USER_HASH",
  "duration_days": 14,
  "primary_metrics": [
    { "name": "revenue_per_mille", "direction": "INCREASE", "mde": 0.005 },
    { "name": "ctr", "direction": "INCREASE", "mde": 0.002 }
  ],
  "guardrail_metrics": [
    { "name": "user_session_time", "direction": "NO_DECREASE", "threshold": -0.01 },
    { "name": "ad_load_time_p99", "direction": "NO_INCREASE", "threshold": 0.05 },
    { "name": "advertiser_roi", "direction": "NO_DECREASE", "threshold": -0.02 }
  ],
  "stats_config": {
    "confidence_level": 0.95,
    "power": 0.80,
    "sequential_testing": true,
    "multiple_testing_correction": "BONFERRONI"
  }
}

Response (201):
{
  "experiment_id": "exp_xyz789",
  "status": "DRAFT",
  "required_sample_size_per_group": 50000000,
  "estimated_days_to_significance": 10,
  "start_command": "POST /api/v1/experiments/exp_xyz789/start"
}
```

### 4. Get Experiment Dashboard
```
GET /api/v1/experiments/{experiment_id}/dashboard

Response:
{
  "experiment_id": "exp_xyz789",
  "status": "RUNNING",
  "days_elapsed": 7,
  "primary_metrics": [
    {
      "name": "revenue_per_mille",
      "control": 12.38, "treatment": 12.52,
      "relative_diff": "+1.13%",
      "p_value": 0.023,
      "ci_95": ["+0.15%", "+2.11%"],
      "significant": true,
      "recommendation": "TREATMENT_BETTER"
    }
  ],
  "guardrail_metrics": [
    {
      "name": "user_session_time",
      "control": 342.5, "treatment": 341.8,
      "relative_diff": "-0.20%",
      "status": "PASS",
      "threshold": "-1.0%"
    }
  ],
  "sample_sizes": { "control": 35000000, "treatment": 35000000 },
  "overall_recommendation": "PROMISING — continue to planned end date"
}
```

---

## 4️⃣ Data Flow

### Flow 1: Offline Evaluation
```
1. Engineer submits offline eval: "Compare model v42 vs v41 on last 7 days"
   ↓
2. Eval Service creates job, submits to Spark cluster
   ↓
3. Spark job:
   a. Read 7 days of impression logs from data warehouse (Hive/HDFS)
      → 70B impressions, ~35 TB
   b. For each impression:
      - Load the ad features + user features from the log
      - Score with NEW model v42 → get new predicted CTR
      - Score with BASELINE model v41 → get baseline predicted CTR
      - Compare against actual label (did user click? did user convert?)
   c. Compute metrics per segment:
      - AUC-ROC: how well does the model rank clickers above non-clickers?
      - Logloss: how well calibrated are the probability predictions?
      - NDCG@10: how good is the ranking quality of the top 10 ads?
      - Calibration: predicted_CTR / actual_CTR (ideal = 1.0)
   d. Write results to metrics store
   ↓
4. Engineer views results in dashboard
   ↓
5. If offline metrics improve: proceed to A/B test
   If offline metrics degrade: iterate on model
```

### Flow 2: A/B Test (Online Evaluation)
```
1. Experiment created: control = v41, treatment = v42, 1% traffic
   ↓
2. Traffic splitting (at ad serving time):
   a. User requests ads
   b. Hash(user_id + experiment_id) % 100
   c. If hash < 1: → treatment group (model v42 ranks the ads)
   d. If hash >= 1: → control group (model v41 ranks the ads)
   e. Log: {request_id, experiment_id, group, model_version, predictions, labels}
   ↓
3. Impression/click/conversion events flow to Kafka → data warehouse
   ↓
4. Daily metric computation job (Spark):
   a. Group impressions by experiment_id + group (control vs treatment)
   b. Compute per-group metrics:
      - Revenue per 1000 impressions (RPM)
      - Click-through rate (CTR)
      - Conversion rate
      - User session time (engagement)
      - Ad load latency
   c. Run statistical tests:
      - Two-sample t-test or Mann-Whitney U for continuous metrics
      - Chi-squared test for proportions (CTR)
      - Check p-value < 0.05
      - Compute 95% confidence intervals
   d. Check guardrail metrics (did we harm user experience?)
   ↓
5. Results visible in experiment dashboard (updated hourly)
   ↓
6. After reaching statistical significance (typically 7-14 days):
   a. If treatment wins: promote model v42 to production
   b. If control wins: reject model v42, investigate why
   c. If inconclusive: extend experiment or increase traffic %
```

### Flow 3: Counterfactual (Off-Policy) Evaluation
```
1. Challenge: We have logged data from model v41, but want to estimate
   how model v42 WOULD HAVE performed — without actually serving it.
   ↓
2. Problem: Logged data has selection bias — model v41 chose which ads
   to show. Model v42 might have shown different ads.
   ↓
3. Solution: Inverse Propensity Scoring (IPS)
   a. For each logged impression:
      - p_old = probability that model v41 would show this ad (the propensity)
      - reward = actual click/revenue from this impression
      - Re-weight: adjusted_reward = reward / p_old
   b. For model v42:
      - Score all candidate ads with v42
      - See which ads v42 would have shown
      - Compute expected reward using IPS-weighted historical outcomes
   ↓
4. This gives an unbiased estimate of v42's performance WITHOUT serving it
   ↓
5. Less noisy than pure offline eval, but not as reliable as A/B test
   Used as a middle step: offline eval → counterfactual → A/B test
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                       AD SERVING PIPELINE                            │
│                                                                      │
│  User → Ad Request → Candidate Selection → RANKING → Auction → Serve│
│                                              │                       │
│                              ┌────────────────┘                      │
│                              │ Which model to use?                   │
│                              ▼                                       │
│                    ┌──────────────────┐                              │
│                    │ EXPERIMENT       │                              │
│                    │ ASSIGNMENT       │                              │
│                    │                  │                              │
│                    │ hash(user_id) →  │                              │
│                    │ control or       │                              │
│                    │ treatment        │                              │
│                    └────────┬─────────┘                              │
│                             │ Log: {experiment, group, predictions}  │
└─────────────────────────────┼───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    EVENT LOGGING (Kafka → HDFS/Hive)                 │
│                                                                      │
│  Impression logs: request_id, user_id, ad_id, predicted_ctr,        │
│  actual_click, revenue, model_version, experiment_id, group          │
│                                                                      │
│  Volume: 10B impressions/day → ~5 TB/day                            │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────────┐
│ OFFLINE EVAL   │ │ COUNTERFACTUAL │ │ ONLINE A/B         │
│ SERVICE        │ │ EVAL SERVICE   │ │ METRICS SERVICE    │
│                │ │                │ │                    │
│ Spark jobs     │ │ IPS/Replay     │ │ Daily Spark jobs   │
│ Score model    │ │ Re-weighting   │ │ Group by exp+group │
│ on historical  │ │ Estimate perf  │ │ Compute metrics    │
│ data           │ │ of new model   │ │ Statistical tests  │
│                │ │ w/o serving    │ │ p-values, CIs      │
│ Metrics: AUC,  │ │                │ │                    │
│ logloss, NDCG  │ │ Metrics: IPS-  │ │ Metrics: Revenue,  │
│ calibration    │ │ weighted RPM,  │ │ CTR, conversion,   │
│                │ │ SNIPS estimator│ │ engagement         │
└───────┬────────┘ └───────┬────────┘ └───────┬────────────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    METRICS STORE                                     │
│                                                                      │
│  Time-series DB (InfluxDB / Druid / internal)                       │
│  Per experiment, per day, per segment: all metric values             │
│  Retention: 2 years                                                 │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    EXPERIMENT DASHBOARD                              │
│                                                                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐   │
│  │ Metric Trends│ │ Significance │ │ Guardrail Monitor        │   │
│  │ (line charts)│ │ (p-values,   │ │ (user engagement,        │   │
│  │              │ │  CIs, power) │ │  latency, ad quality)    │   │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘   │
│                                                                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐   │
│  │ Segment      │ │ Auto-Kill    │ │ Ship Decision            │   │
│  │ Breakdown    │ │ Alerts       │ │ (SHIP / NO-SHIP /        │   │
│  │ (by country, │ │ (guardrail   │ │  EXTEND)                 │   │
│  │  ad type)    │ │  violation)  │ │                          │   │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    MODEL REGISTRY                                    │
│                                                                      │
│  model_id, version, training_data, hyperparameters,                 │
│  offline_metrics, experiment_results, status (shadow/canary/prod)    │
│  Artifact storage: S3                                               │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology |
|-----------|---------|-----------|
| **Experiment Assignment** | Deterministic user-to-group mapping at serving time | In-process library (hash-based) |
| **Event Logging** | Capture all impression/click/conversion events | Kafka → HDFS/Hive |
| **Offline Eval Service** | Score models on historical data, compute ML metrics | Spark on YARN/K8s |
| **Counterfactual Eval** | IPS-based estimation of new model performance | Spark + custom IPS library |
| **Online Metrics Service** | Compute A/B test metrics daily, run stat tests | Spark + StatsLib |
| **Metrics Store** | Store computed metrics per experiment/day/segment | Druid / InfluxDB / Scuba (Meta) |
| **Experiment Dashboard** | Visualize results, significance, guardrails | React + internal charting |
| **Model Registry** | Track model versions, link to eval results | PostgreSQL + S3 |
| **Auto-Kill Service** | Automatically stop experiments if guardrails violated | Cron job + alerting |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Offline Evaluation — ML Metrics That Matter

**The Problem**: How do you know if a new ranking model is better *before* running an A/B test?

**Key Metrics for Ads Ranking**:

```java
public class AdsRankingMetrics {

    /**
     * METRIC 1: AUC-ROC (Area Under ROC Curve)
     * 
     * What: How well does the model distinguish clickers from non-clickers?
     * Range: 0.5 (random) to 1.0 (perfect)
     * Typical for ads: 0.75-0.85
     * 
     * Why it matters: A model that can't separate likely-clickers from
     * non-clickers will waste impressions on users who won't click.
     * 
     * How computed: For every pair (positive, negative), what fraction
     * of the time does the model score the positive higher?
     */
    public double computeAUC(List<PredictionLabel> data) {
        // Sort by predicted score descending
        data.sort(Comparator.comparingDouble(PredictionLabel::getPrediction).reversed());
        
        long positives = data.stream().filter(PredictionLabel::isPositive).count();
        long negatives = data.size() - positives;
        
        long truePositivesSoFar = 0;
        double aucSum = 0.0;
        
        for (PredictionLabel pl : data) {
            if (pl.isPositive()) {
                truePositivesSoFar++;
            } else {
                // Each negative ranked below truePositivesSoFar positives
                aucSum += truePositivesSoFar;
            }
        }
        
        return aucSum / (positives * negatives);
    }

    /**
     * METRIC 2: LogLoss (Binary Cross-Entropy)
     * 
     * What: How well calibrated are the probability predictions?
     * Lower = better. Penalizes confident wrong predictions heavily.
     * 
     * Why it matters for ads: Revenue = bid × P(click). If P(click) is
     * wrong, we charge advertisers incorrectly and rank ads wrong.
     * 
     * Formula: -1/N × Σ [y·log(p) + (1-y)·log(1-p)]
     */
    public double computeLogLoss(List<PredictionLabel> data) {
        double sum = 0.0;
        for (PredictionLabel pl : data) {
            double p = Math.max(1e-15, Math.min(1 - 1e-15, pl.getPrediction()));
            if (pl.isPositive()) {
                sum += Math.log(p);
            } else {
                sum += Math.log(1 - p);
            }
        }
        return -sum / data.size();
    }

    /**
     * METRIC 3: Calibration
     * 
     * What: Is the predicted probability accurate? If model says P=0.05,
     * do 5% of those impressions actually get clicked?
     * 
     * Ideal value: 1.0 (predicted = actual)
     * > 1.0: over-predicting (model thinks CTR is higher than reality)
     * < 1.0: under-predicting
     * 
     * Why it matters: Directly affects auction pricing and advertiser trust.
     */
    public double computeCalibration(List<PredictionLabel> data) {
        double sumPredicted = data.stream().mapToDouble(PredictionLabel::getPrediction).sum();
        double sumActual = data.stream().filter(PredictionLabel::isPositive).count();
        return sumPredicted / sumActual; // Ideal = 1.0
    }

    /**
     * METRIC 4: NDCG@K (Normalized Discounted Cumulative Gain)
     * 
     * What: How good is the ranking order of the top K results?
     * Range: 0.0 to 1.0 (1.0 = perfect ordering)
     * 
     * Why it matters: We show 10 ads. NDCG measures whether the BEST ads
     * (highest expected revenue/engagement) are at the TOP positions.
     */
    public double computeNDCG(List<PredictionLabel> data, int k) {
        // Ideal ordering: sort by actual relevance (clicks/revenue)
        List<PredictionLabel> ideal = new ArrayList<>(data);
        ideal.sort(Comparator.comparingDouble(PredictionLabel::getActualRelevance).reversed());
        double idealDCG = computeDCG(ideal, k);
        
        // Actual ordering: sort by model's predicted score
        List<PredictionLabel> actual = new ArrayList<>(data);
        actual.sort(Comparator.comparingDouble(PredictionLabel::getPrediction).reversed());
        double actualDCG = computeDCG(actual, k);
        
        return idealDCG == 0 ? 0 : actualDCG / idealDCG;
    }
    
    private double computeDCG(List<PredictionLabel> ranked, int k) {
        double dcg = 0.0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            double relevance = ranked.get(i).getActualRelevance();
            dcg += relevance / (Math.log(i + 2) / Math.log(2)); // log2(rank+1)
        }
        return dcg;
    }
}
```

**Offline Eval Decision Criteria**:
```
PROCEED to A/B test if:
  ✅ AUC improved (even slightly, e.g., +0.1%)
  ✅ LogLoss decreased (better calibration)
  ✅ Calibration between 0.95 and 1.05
  ✅ NDCG@10 improved or neutral
  ✅ No segment has significant regression

REJECT (iterate on model) if:
  ❌ AUC decreased
  ❌ Calibration > 1.10 or < 0.90 (severely miscalibrated)
  ❌ Any major segment (e.g., US) regressed significantly
```

---

### Deep Dive 2: A/B Testing — Statistical Rigor at Scale

**The Problem**: At Meta's scale (10B impressions/day), even tiny bugs in statistical testing can lead to deploying bad models (losing millions in revenue) or not deploying good models (leaving money on the table).

**Key Statistical Concepts**:

```
CONCEPT 1: Statistical Significance (p-value)
  "What's the probability we'd see this difference by random chance?"
  p < 0.05 → "Less than 5% chance this is random" → statistically significant
  
CONCEPT 2: Power
  "If there IS a real difference, what's the probability we'll detect it?"
  Power > 0.80 → "80% chance we'll catch a real effect"
  
CONCEPT 3: Minimum Detectable Effect (MDE)
  "What's the smallest change we care about detecting?"
  MDE = 0.5% revenue change → need ~50M samples per group

CONCEPT 4: Confidence Interval
  "Treatment is +1.2% better, with 95% CI of [+0.3%, +2.1%]"
  → We're 95% sure the true effect is between +0.3% and +2.1%

CONCEPT 5: Multiple Testing Correction
  Testing 50 metrics → some will be "significant" by chance (5% = 2.5 metrics)
  Bonferroni: divide α by number of tests → p < 0.05/50 = 0.001
  FDR (Benjamini-Hochberg): less conservative, controls false discovery rate
```

**Sample Size Calculation**:
```
For a two-sided t-test detecting a 0.5% change in revenue:

n = 2 × (Z_α/2 + Z_β)² × σ² / δ²

Where:
  Z_α/2 = 1.96 (for 95% confidence)
  Z_β = 0.84 (for 80% power)
  σ = standard deviation of RPM ≈ $5
  δ = MDE × mean(RPM) = 0.005 × $12 = $0.06

n = 2 × (1.96 + 0.84)² × 25 / 0.0036 = ~109,000 per group

At 1% traffic (100M impressions/day): reach significance in ~1 day
At 0.1% traffic (10M/day): need ~11 days

In practice: run for 7-14 days to capture weekly patterns (weekday vs weekend)
```

**Guardrail Metrics — The Safety Net**:
```
Primary metrics: What we're trying to IMPROVE
  - Revenue per 1000 impressions (RPM) → INCREASE
  - Click-through rate (CTR) → INCREASE

Guardrail metrics: What we must NOT HARM
  - User session time → must not decrease > 1%
  - Ad load latency (p99) → must not increase > 50ms
  - Advertiser ROI → must not decrease > 2%
  - User hide/report rate → must not increase > 5%
  - Ad diversity → must not decrease > 10%

If ANY guardrail is violated → AUTO-KILL the experiment
Even if primary metrics look great — user harm is unacceptable
```

---

### Deep Dive 3: Counterfactual Evaluation — Estimating Without Serving

**The Problem**: A/B tests take weeks and risk real revenue. Can we estimate a model's performance from historical logged data without serving it?

```
The Fundamental Challenge: Selection Bias

Model v41 (current) chose to show ad A to user X.
We know: user X clicked ad A.

Would user X have clicked ad B (which model v42 would have shown)?
We DON'T know — it was never shown. This is the counterfactual.

Naive approach (just re-score with v42) doesn't work because:
  - v41 chose to show certain ads → we only have click data for THOSE ads
  - v42 might have shown different ads → we have NO click data for them
  - This creates a biased estimate
```

**Solution: Inverse Propensity Scoring (IPS)**:
```
Idea: Re-weight each observation by 1/probability it was shown.

For each logged impression:
  - We showed ad A with propensity p_old (probability v41 would show it)
  - Reward R (1 if clicked, 0 if not, or revenue amount)
  - IPS-weighted reward = R / p_old

Why this works:
  - Rare ads (low propensity) get HIGH weight when clicked
  - Common ads (high propensity) get LOW weight
  - In expectation, the weighted sum is an unbiased estimate

SNIPS (Self-Normalized IPS) — more stable:
  - IPS_estimate = Σ(R_i / p_i) / Σ(1 / p_i)
  - Normalizes by total weight → lower variance
```

**The Evaluation Ladder**:
```
CHEAPEST ←──────────────────────────────→ MOST RELIABLE

Offline Eval    →  Counterfactual   →  Shadow Mode   →  A/B Test
(historical     →  (IPS-weighted    →  (score live   →  (serve live
 data, re-score)    estimation)        traffic,         traffic,
                                       don't serve)     measure)

Time:   1 hour      4 hours            1 week           2 weeks
Cost:   $0          $0                 Compute only     Revenue risk
Risk:   None        None               None             Real users
Bias:   High        Medium             Low              None

USE ALL FOUR in sequence: each is a progressively stricter gate.
Only models that pass ALL stages get promoted to production.
```

---

### Deep Dive 4: Traffic Splitting — Avoiding Bias

**The Problem**: How do you split traffic between control and treatment without introducing bias?

```
METHOD 1: User-Level Hashing (preferred)
  group = hash(user_id + experiment_id) % 100
  If group < traffic_percent → treatment
  
  ✅ Same user always in same group (consistent experience)
  ✅ No cross-contamination between sessions
  ✅ Can track long-term effects (engagement over days)
  ❌ Cannot measure within-session effects

METHOD 2: Request-Level Hashing
  group = hash(request_id) % 100
  
  ✅ More sample diversity per time period
  ❌ Same user sees different models across requests
  ❌ Can't measure user-level effects
  ❌ Cross-contamination risk (user learns from one model, applies to other)

METHOD 3: Session-Level Hashing
  group = hash(user_id + session_id) % 100
  
  ✅ Consistent within a session
  ✅ User can be in different groups across sessions
  ❌ Harder to define session boundaries

For ads ranking: USER-LEVEL is standard.
  Revenue is ultimately a per-user metric.
  Users should have a consistent ad experience within and across sessions.
```

---

### Deep Dive 5: Model Deployment Pipeline — From Eval to Production

```
STAGE 1: OFFLINE EVAL (Gate: ML metrics must improve)
  Engineer trains model v42
  Submit offline eval against v41 baseline
  Check: AUC ↑, logloss ↓, calibration ≈ 1.0, NDCG ↑
  Pass → proceed. Fail → iterate.
    ↓
STAGE 2: COUNTERFACTUAL EVAL (Gate: estimated business metrics neutral/positive)
  Run IPS evaluation on 7 days of logged data
  Check: estimated RPM ≥ baseline, no segment regression
  Pass → proceed. Fail → investigate offline/online gap.
    ↓
STAGE 3: SHADOW MODE (Gate: latency and error rate acceptable)
  Deploy v42 alongside v41 in production
  v42 scores every request but DOES NOT affect serving
  Log v42's predictions for offline comparison
  Check: inference latency < 50ms p99, no errors
  Duration: 3-5 days
    ↓
STAGE 4: CANARY (Gate: no guardrail violations at 0.1% traffic)
  Serve v42 to 0.1% of users
  Monitor all metrics for 24-48 hours
  Check: no revenue drop, no latency increase, no user complaints
    ↓
STAGE 5: A/B TEST (Gate: statistically significant improvement)
  Serve v42 to 1% → 5% → 10% of users
  Run for 7-14 days
  Check: primary metrics improve with p < 0.05, guardrails pass
    ↓
STAGE 6: FULL ROLLOUT
  Ramp to 100% over 24-48 hours
  Keep v41 as instant rollback for 7 days
  Archive experiment results in model registry
```

---

### Deep Dive 6: Metrics That Conflict — Revenue vs User Experience

**The Problem**: A model that maximizes revenue might harm user experience (show more annoying ads, lower content quality). How do you balance?

```
SCENARIO: Model v42 increases revenue by +2% but decreases user session time by -0.5%

Option A: Ship it (revenue wins)
  +2% revenue = +$6M/day at Meta scale
  -0.5% session time = users spend slightly less time
  Short-term revenue gain, potential long-term user churn

Option B: Don't ship (user experience wins)
  Protect user engagement
  Miss $6M/day revenue opportunity
  May need to find alternative model that does both

Option C: Ship with constraints (balance)
  Ship v42 but cap ad load (max 1 ad per 5 content items)
  Add ad quality filters (minimum relevance score)
  Monitor user retention for 30 days post-launch
  If retention drops: rollback

META'S APPROACH:
  Revenue is the PRIMARY metric
  BUT user engagement is a GUARDRAIL with strict thresholds
  Model must prove it doesn't harm user experience significantly
  Long-term user value > short-term ad revenue
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Alternative | Why |
|----------|--------|-------------|-----|
| **Evaluation stages** | 4-stage pipeline (offline → counterfactual → shadow → A/B) | Direct A/B test only | Catches bad models early, saves real-traffic risk |
| **Traffic split** | User-level hashing | Request-level or session-level | Consistent user experience, measure long-term effects |
| **Statistical test** | Sequential testing with always-valid p-values | Fixed-horizon t-test | Safe to "peek" at results daily without inflating false positive rate |
| **Primary metric** | Revenue per mille (RPM) | CTR alone | RPM captures both click rate AND revenue; CTR ignores bid/conversion value |
| **Guardrail strategy** | Auto-kill on violation | Manual review only | Prevents harm to users/advertisers even on weekends |
| **Calibration check** | Must be within [0.95, 1.05] | No calibration check | Miscalibrated model breaks auction pricing → advertisers lose trust |
| **Multiple testing** | Bonferroni correction | No correction | Testing 50 metrics → ~2.5 false positives without correction |
| **Offline data** | 7-day window | 1-day or 30-day | 1 day = too noisy; 30 days = stale; 7 days captures weekly patterns |

## Interview Talking Points

1. **"Four-stage evaluation pipeline: offline → counterfactual → shadow → A/B"** — Each stage is progressively more expensive and more reliable. Most bad models are caught in offline eval (cheapest). Only proven models reach A/B testing (most expensive).

2. **"AUC measures ranking quality, calibration measures pricing accuracy"** — A model can have good AUC (ranks correctly) but bad calibration (P(click)=0.10 when actual CTR is 0.05). For ads, calibration directly affects what advertisers pay.

3. **"User-level hashing for traffic split"** — `hash(user_id + experiment_id) % 100` ensures each user has a consistent experience. Same user always sees the same model. Prevents cross-contamination.

4. **"Guardrail metrics are non-negotiable"** — Even if revenue increases 5%, if user session time drops 2%, we auto-kill the experiment. Long-term user retention > short-term revenue.

5. **"Counterfactual evaluation bridges offline and online"** — IPS re-weighting estimates how a new model would perform on logged data, without selection bias. Cheaper than A/B test, more reliable than pure offline eval.

6. **"Sequential testing allows safe peeking"** — Engineers check experiment dashboards daily. Fixed-horizon tests inflate false positives when you peek. Always-valid p-values (from sequential testing) allow checking anytime without bias.

7. **"Revenue per mille (RPM), not just CTR"** — CTR alone doesn't capture value. A model might increase clicks on low-bid ads (higher CTR, lower revenue). RPM = revenue / 1000 impressions captures the full picture.

8. **"Bonferroni correction for 50 metrics"** — Without correction, testing 50 metrics at α=0.05 gives ~2.5 false positives. Bonferroni adjusts α to 0.001 per metric. More conservative but prevents false ship decisions.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Ad Click Aggregator** | Same domain (ads), real-time counting | Aggregator counts events; evaluation framework judges model quality |
| **A/B Testing Platform** | Same experimentation pattern | Generic A/B tests any feature; this is specialized for ML model ranking |
| **Recommendation System** | Same ML ranking + evaluation | Recommendations optimize engagement; ads optimize revenue + engagement |
| **Feature Flag Service** | Same traffic splitting mechanism | Feature flags are boolean on/off; experiments measure continuous metrics |
| **ML Training Pipeline** | Upstream of evaluation | Training produces models; evaluation decides if they're good enough to ship |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Source**: [Exponent](https://www.tryexponent.com/questions/3095/design-evaluation-framework-ads-ranking)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (choose 2-3 based on interviewer interest)
