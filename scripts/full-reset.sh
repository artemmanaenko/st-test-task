#!/bin/bash

# Full Reset Script - Clean everything and start from scratch
# Based on README.md instructions

set -e

echo "🧹 Full Reset - Cleaning Everything"
echo "===================================="
echo ""

# Step 1: Stop and remove containers + volumes
echo "📦 Step 1: Stopping containers and removing volumes..."
docker compose down -v 2>/dev/null || echo "No containers to stop"

# Step 2: Clean Docker system
echo "🗑️  Step 2: Cleaning Docker system..."
docker system prune -f 2>/dev/null || echo "Docker cleanup skipped"

# Step 3: Clean Gradle build
echo "🔨 Step 3: Cleaning Gradle build..."
./gradlew clean 2>/dev/null || echo "Gradle clean skipped"

# Step 4: Build and start services
echo "🚀 Step 4: Building and starting services..."
echo "   This may take a few minutes..."
docker compose build api
docker compose up -d

# Step 5: Wait for health check
echo "⏳ Step 5: Waiting for services to be ready..."
echo "   Checking health endpoint..."
for i in {1..60}; do
    if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        echo "   ✅ Services are UP!"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "   ⚠️  Services took too long to start. Check logs: docker compose logs"
        exit 1
    fi
    sleep 1
done

# Step 6: Generate test data
echo "📊 Step 6: Generating test data..."
./scripts/generate-test-data.sh

# Step 7: Run tests
echo "🧪 Step 7: Running service tests..."
./scripts/test-services.sh

echo ""
echo "===================================="
echo "✅ Full Reset Complete!"
echo "===================================="
echo ""
echo "Services are running at:"
echo "  - API: http://localhost:8080"
echo "  - Health: http://localhost:8080/actuator/health"
echo ""
echo "View logs: docker compose logs -f"

