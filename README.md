# Storyteller - Personalised Video Feeds Backend

This project implements a scalable backend for a personalized video feed application using **Java 21**, **Spring Boot 3.4**, **PostgreSQL 16**, and **Redis 7**. It features a personalized ranking engine, tenant isolation, and a robust caching strategy.

## Table of Contents

- [Quick Start](#quick-start)
  - [Prerequisites](#prerequisites)
  - [Run Services](#run-services)
- [Architecture](#architecture)
- [API Endpoints](#api-endpoints)
- [Ranking Algorithm & Mathematics](#ranking-algorithm--mathematics)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

### Prerequisites

This project requires Docker and Java 21. If you're on a fresh macOS machine, follow these steps:

#### 1. Install Homebrew (if not installed)
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

#### 2. Install Docker Desktop
**Option A: Download Installer (Recommended)**
- Download from: [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
- Install and start Docker Desktop
- Verify: `docker --version` and `docker compose version`

**Option B: Via Homebrew**
```bash
brew install --cask docker
# Open Docker Desktop from Applications
```

#### 3. Install Java 21 (Optional - only for local development)
**Option A: Via Homebrew (Recommended)**
```bash
brew install openjdk@21
# Add to PATH
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

**Option B: Download from Oracle**
- Download from: [https://www.oracle.com/java/technologies/downloads/#java21](https://www.oracle.com/java/technologies/downloads/#java21)

**Verify Installation:**
```bash
java -version  # Should show version 21.x.x
```

> **Note:** Java is optional because Docker handles the build. You only need Java for local IDE development.

### Run Services

Build and start the complete environment (API, DB, Redis, Nginx):

```bash
docker compose build api
docker compose up -d
```

The services will be available at:
- **API (Load Balanced)**: `http://localhost:8080`
- **Actuator Health**: `http://localhost:8080/actuator/health`

---

## Architecture

- **API Layer**: Spring Boot 3.4 application (3 replicas)
- **Load Balancer**: Nginx (Round Robin distribution)
- **Database**: PostgreSQL 16 (Partitioned `user_events` table)
- **Cache**: Redis 7 (UserProfile storage + JSON Feed Cache)
- **Asynchronous Processing**: `@Scheduled` tasks for User Profile Aggregation

---

## API Endpoints

### 1. Post User Event

Track user interactions with videos for personalization.

**Endpoint:** `POST /v1/events`

**Request Body:**
```json
{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "userIdHash": "a1b2c3d4e5f6...",
  "videoId": "a0000000-0000-0000-0000-000000000001",
  "eventType": "like",
  "metadata": {
    "device": "iOS",
    "app_version": "1.2.3"
  }
}
```

**Event Types:** `like`, `share`, `view`, `watch_completed`

**Example:**
```bash
curl -X POST http://localhost:8080/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "userIdHash": "'$(echo -n 'demo_user_1' | shasum -a 256 | cut -d' ' -f1)'",
    "videoId": "a0000000-0000-0000-0000-000000000001",
    "eventType": "like"
  }'
```

---

### 2. Get Personalized Feed

Returns a personalized video feed for a user based on their viewing history, preferences, and the tenant's ranking configuration.

**Endpoint:** `GET /v1/feed`

**Query Parameters:**
- `tenantId` (UUID, required) - Tenant identifier
- `userIdHash` (string, required) - SHA-256 hash of user ID
- `limit` (integer, optional) - Number of videos to return (default: 10, max: 50)

**Response:**
```json
{
  "items": [
    {
      "videoId": "a0000000-0000-0000-0000-000000000001",
      "contentUrl": "https://cdn.example.com/video1.mp4",
      "thumbnailUrl": "https://cdn.example.com/thumb1.jpg",
      "tags": ["cats", "funny"],
      "createdAt": "2025-12-06T10:00:00Z",
      "popularityScore": 85.5
    }
  ],
  "etag": "abc123..."
}
```

**Features:**
- **Personalized ranking** based on user's tag preferences (if personalization enabled)
- **Fallback to popularity** if personalization disabled or user has no history
- **Editorial boosts** applied to promoted content
- **Redis caching** for fast response times
- **ETag support** for efficient cache validation

**Example:**
```bash
# Generate user hash
USER_HASH=$(echo -n 'demo_user_1' | shasum -a 256 | cut -d' ' -f1)

# Get personalized feed
curl "http://localhost:8080/v1/feed?tenantId=11111111-1111-1111-1111-111111111111&userIdHash=$USER_HASH&limit=10"

# With ETag for cache validation
curl -H "If-None-Match: abc123..." \
  "http://localhost:8080/v1/feed?tenantId=11111111-1111-1111-1111-111111111111&userIdHash=$USER_HASH&limit=10"
```

**Ranking Formula:**
```
totalScore = (w_recency * recencyScore) +
             (w_popularity * popularityScore) +
             (w_affinity * affinityScore) +
             editorialBoost
```

See [Ranking Algorithm & Mathematics](#ranking-algorithm--mathematics) for detailed explanation.

---

### 3. Health Check

**Endpoint:** `GET /actuator/health`

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

---

### Internal Endpoints (Admin/Testing)

#### Global Kill-Switch
Instantly disable personalization for ALL tenants.
```bash
# Disable (Force Fallback)
curl -X POST "http://localhost:8080/internal/kill-switch?enabled=false"

# Enable (Restore Personalization)
curl -X POST "http://localhost:8080/internal/kill-switch?enabled=true"
```

#### Per-Tenant Feature Flag
Toggle personalization for a specific tenant.
```bash
# Disable for specific tenant
curl -X POST "http://localhost:8080/internal/tenant-flag?tenantId=11111111-1111-1111-1111-111111111111&enabled=false"
```

#### Cache Invalidation Webhook
Simulate a CMS update invalidating the cache.
```bash
curl -X POST http://localhost:8080/internal/cms-webhook \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "11111111-1111-1111-1111-111111111111"}'
```

#### Manual Aggregation Trigger
Manually trigger user profile aggregation (useful for testing).
```bash
curl -X POST http://localhost:8080/internal/trigger-aggregation
# Response: {"status":"completed","profiles_updated":5}
```

---

## Ranking Algorithm & Mathematics

The personalized video feed uses a **weighted sum** ranking algorithm that combines three signals: recency, popularity, and user affinity.

### Core Formula

```
totalScore = (w_recency * recencyScore) + 
             (w_popularity * popularityScore) + 
             (w_affinity * affinityScore) + 
             editorialBoost
```

Where:
- `w_recency`, `w_popularity`, `w_affinity` are configurable weights per tenant (default: 0.3, 0.4, 0.3)
- All component scores are normalized to [0, 1] range
- `editorialBoost` is an additive bonus from `EditorialBoost.boostFactor` (default: 0.0 if no boost)
- Videos are sorted by `totalScore` in descending order

### Component Scores

#### 1. Recency Score (Exponential Decay)

Uses **exponential decay** to favor newer content:

```
recencyScore = exp(-daysSinceCreation / RECENCY_DECAY_DAYS)
```

- **Default decay**: 7 days (`RECENCY_DECAY_DAYS = 7.0`)
- **Behavior**:
  - Video created today: `recencyScore ~ 1.0`
  - Video 7 days old: `recencyScore ~ 0.368`
  - Video 14 days old: `recencyScore ~ 0.135`
  - Older videos -> exponentially lower scores

**Example:**
```java
// Video created 3 days ago
daysSinceCreation = 3
recencyScore = exp(-3 / 7.0) ~ 0.651
```

#### 2. Popularity Score (Linear Normalization)

Normalized popularity metric:

```
popularityScore = video.popularityScore / POPULARITY_NORMALIZATION_FACTOR
```

- **Normalization factor**: 100.0 (`POPULARITY_NORMALIZATION_FACTOR`)
- **Assumption**: Popularity scores range from 0-100
- **Result**: Normalized to [0, 1] range

**Example:**
```java
// Video with popularity_score = 75
popularityScore = 75 / 100.0 = 0.75
```

#### 3. User Affinity Score (Tag-based Matching)

Based on user's tag preferences and video tags:

```
affinityScore = average(tagScores[videoTags]) / TAG_SCORE_NORMALIZATION_FACTOR
affinityScore = min(affinityScore, 1.0)  // Cap at 1.0
```

- **Tag scores**: Stored in `UserProfile.tagScores` (Map<String, Float>)
- **Normalization factor**: 10.0 (`TAG_SCORE_NORMALIZATION_FACTOR`)
- **Calculation**: Average of matching tag scores, normalized to [0, 1]
- **Fallback**: 0.5 if user profile or tags are missing

**Example:**
```java
// Video tags: ["cats", "funny"]
// User tag scores: {"cats": 8.5, "funny": 7.2, "cooking": 3.1}
averageTagScore = (8.5 + 7.2) / 2 = 7.85
affinityScore = min(7.85 / 10.0, 1.0) = 0.785
```

### User Profile Aggregation

User profiles are built from events using **decay + accumulation**:

```
newTagScore = (oldTagScore * PROFILE_DECAY_FACTOR) + eventScore
```

- **Decay factor**: 0.9 (`RankingDefaults.PROFILE_DECAY_FACTOR = 0.9f`)
- **Event scores** (from `RankingDefaults`):
  - `LIKE`: +1.0 (`EVENT_SCORE_LIKE = 1.0f`)
  - `SHARE`: +2.0 (`EVENT_SCORE_SHARE = 2.0f`)
  - `VIEW`: +0.1 (`EVENT_SCORE_VIEW = 0.1f`)
  - Unknown event types: default to `EVENT_SCORE_VIEW`
- **Process**: Runs every 3 minutes automatically, or can be triggered manually via `/internal/trigger-aggregation`

**Example:**
```java
// User has "cats" tag with score 5.0
// User likes a video tagged ["cats"]
oldScore = 5.0
decayedScore = 5.0 * 0.9 = 4.5
newScore = 4.5 + 1.0 = 5.5
```

### Weight Configuration

Each tenant can override default weights via `RankingWeights`:

```json
{
  "recency": 0.3,
  "popularity": 0.4,
  "user_affinity": 0.3
}
```

**Weights should sum to 1.0** for proper normalization (not enforced, but recommended).

### Editorial Boost

Editorial boosts allow content teams to promote specific videos:

```
editorialBoost = boostFactor (if active boost exists, else 0.0)
```

- **Active boost**: Boost exists AND (`expires_at` is NULL OR `expires_at` > now)
- **Boost factor**: Default 5.0, configurable per video
- **Effect**: Adds directly to final score (can significantly increase ranking)

**Example:**
```java
// Video has active editorial boost with boost_factor = 5.0
editorialBoost = 5.0

// Without boost: totalScore = 0.8
// With boost: totalScore = 0.8 + 5.0 = 5.8
```

### Complete Example

```java
// Video: created 2 days ago, popularity=80, tags=["cats", "funny"]
// User: tagScores={"cats": 9.0, "funny": 8.0}
// Weights: recency=0.3, popularity=0.4, affinity=0.3
// Editorial boost: boost_factor=5.0 (active)

recencyScore = exp(-2 / 7.0) ~ 0.751
popularityScore = 80 / 100.0 = 0.8
affinityScore = min((9.0 + 8.0) / 2 / 10.0, 1.0) = 0.85
editorialBoost = 5.0

baseScore = (0.3 * 0.751) + (0.4 * 0.8) + (0.3 * 0.85)
          = 0.225 + 0.32 + 0.255
          = 0.8

totalScore = baseScore + editorialBoost
           = 0.8 + 5.0
           = 5.8
```

### Fallback Feed

When personalization is disabled, videos are ranked by **popularity only**:

```
fallbackRank = videos sorted by popularityScore DESC
```

No user profile or weights are used.

---

## Testing

### Quick Test

```bash
# Health check
curl http://localhost:8080/actuator/health

# Generate test data
./scripts/generate-test-data.sh

# Run integration tests
./scripts/test-services.sh
```

### Integration Tests

The `test-services.sh` script runs 10 comprehensive tests:

1. **Health Check** - Verifies all services are UP
2. **FeedService** - Tests personalized feed generation
3. **RankingService** - Tests fallback (popularity-based) feed
4. **FeatureFlagService** - Tests kill-switch toggle
5. **FeedCacheService** - Tests cache hits and invalidation
6. **AggregationService** - Tests event ingestion and profile updates
7. **Popularity Score** - Tests normalization formula
8. **Recency Score** - Tests exponential decay
9. **User Affinity Score** - Tests tag-based matching
10. **Editorial Boost** - Tests boost application
11. **Weighted Sum** - Tests complete ranking formula

**Expected output:** All tests should show PASS

### Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run specific service tests
./gradlew test --tests "com.storyteller.service.RankingServiceTest"
./gradlew test --tests "com.storyteller.service.AggregationServiceTest"
```

### Complete E2E Workflow

Start from scratch:
```bash
# 1. Clean everything
docker compose down -v
docker system prune -f
./gradlew clean

# 2. Build and start
docker compose build api
docker compose up -d

# 3. Wait for health check
sleep 10 && curl http://localhost:8080/actuator/health

# 4. Generate test data
./scripts/generate-test-data.sh

# 5. Run all tests
./scripts/test-services.sh
./gradlew test
```

Total time: ~60 seconds from zero to fully tested system.

---

## Troubleshooting

**Docker not starting:**
```bash
# Check if Docker Desktop is running
docker ps
# If error, open Docker Desktop application
```

**Port 8080 already in use:**
```bash
# Find and kill process using port 8080
lsof -ti:8080 | xargs kill -9
```

**Database connection issues:**
```bash
# Check database logs
docker compose logs db

# Restart services
docker compose restart
```

**Clean rebuild:**
```bash
docker compose down -v
docker system prune -f
./gradlew clean
docker compose build --no-cache api
docker compose up -d
```

**Gradle wrapper issues:**
If you see errors like "gradlew: command not found" or empty gradlew file:
```bash
# Regenerate Gradle wrapper
gradle wrapper --gradle-version=8.10.2
chmod +x gradlew
```

