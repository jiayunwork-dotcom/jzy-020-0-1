#!/usr/bin/env bash
#
# One command: build the image with a JDK 17 image present on this machine
# and start the container.
#
#   ./scripts/up.sh
#
# Overrides (when your local JDK 17 image carries a different tag):
#   BUILD_IMAGE=myrepo/jdk17-mvn RUNTIME_IMAGE=myrepo/jre17 ./scripts/up.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."

IMAGE="flametemp:1.0.0"
CONTAINER="flametemp"
PORT="${PORT:-8080}"
BUILD_IMAGE="${BUILD_IMAGE:-maven:3.9-eclipse-temurin-17}"
RUNTIME_IMAGE="${RUNTIME_IMAGE:-eclipse-temurin:17-jre}"

if ! command -v docker >/dev/null 2>&1; then
  echo "error: docker is required (no docker CLI on PATH)" >&2
  exit 1
fi

image_present() { docker image inspect "$1" >/dev/null 2>&1; }

# Prefer images already on this machine; only pull if docker can reach a registry.
for var in BUILD_IMAGE RUNTIME_IMAGE; do
  img="${!var}"
  if ! image_present "$img"; then
    echo ">> $img not local; attempting pull (set $var to use a local JDK 17 image)"
    docker pull "$img"
  fi
done

echo ">> building $IMAGE with $BUILD_IMAGE / $RUNTIME_IMAGE"
docker build \
  --build-arg BUILD_IMAGE="$BUILD_IMAGE" \
  --build-arg RUNTIME_IMAGE="$RUNTIME_IMAGE" \
  -t "$IMAGE" .

if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  echo ">> replacing existing container '$CONTAINER'"
  docker rm -f "$CONTAINER" >/dev/null
fi

echo ">> starting '$CONTAINER' on port $PORT (SQLite in volume flametemp-data)"
docker run -d \
  --name "$CONTAINER" \
  -p "$PORT:8080" \
  -v flametemp-data:/data \
  --restart unless-stopped \
  "$IMAGE"

echo ">> waiting for readiness"
for _ in $(seq 1 30); do
  if curl -sf "http://localhost:$PORT/api/jobs/1" >/dev/null 2>&1; then
    echo ">> ready: built-in methane stoichiometric demo job:"
    curl -s "http://localhost:$PORT/api/jobs/1" \
      | python3 -c 'import sys,json; d=json.load(sys.stdin); print("   job 1:", d["fuel"], "phi=",d["equivalenceRatio"], "Tin=",d["inletTemperatureK"], "T_flame=%.2f K"%d["finalTemperatureK"], "status="+d["status"])'
    exit 0
  fi
  sleep 1
done
echo ">> container did not become ready in time; inspect: docker logs $CONTAINER" >&2
exit 1
