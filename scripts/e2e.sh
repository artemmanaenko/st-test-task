#!/bin/bash

# End-to-end workflow: clean, build, start stack, load test data, run tests.
# Usage: ./scripts/e2e.sh
# Set SKIP_PRUNE=1 to skip docker system prune.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() { echo "[e2e] $*"; }

log "Step 1: Stop and clean containers"
docker compose down -v || true

if [[ "${SKIP_PRUNE:-0}" != "1" ]]; then
  log "Step 2: Prune docker system"
  docker system prune -f
else
  log "Step 2: Skip docker system prune (SKIP_PRUNE=1)"
fi

log "Step 3: Gradle clean"
./gradlew clean

log "Step 4: Build API image"
docker compose build api

log "Step 5: Start stack (api, db, redis, nginx)"
docker compose up -d

log "Step 6: Wait for health check"
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    log "Health check passed"
    break
  fi
  if [[ "$i" == "40" ]]; then
    log "Health check failed after waiting"
    exit 1
  fi
  sleep 2
done

log "Step 7: Generate test data"
./scripts/generate-test-data.sh

log "Step 8: Run integration checks"
./scripts/test-services.sh

log "Step 9: Run unit tests"
./gradlew test

log "E2E workflow completed successfully"

