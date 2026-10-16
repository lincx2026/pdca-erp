#!/usr/bin/env bash
# Build versioned images, migrate through Flyway, then switch containers.
# Historical db/init and db/patch are never executed here.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
command -v docker >/dev/null
docker compose version >/dev/null
[[ -f .env ]] || { echo 'Create .env with python scripts/init-env.py'; exit 2; }
COMPOSE=(docker compose --env-file "$ROOT/.env" -f "$ROOT/docker/docker-compose.yml")
BACKUPS="$ROOT/.deploy-backups"
mkdir -p "$BACKUPS"
RELEASE="$(date -u +%Y%m%dT%H%M%SZ)-$$"
SCOPE="${1:-all}"
SNAPSHOT_READY=0

image_for() {
  local id
  id="$("${COMPOSE[@]}" ps -q "$1")"
  [[ -n "$id" ]] && docker inspect --format '{{.Config.Image}}' "$id" || true
}

wait_healthy() {
  local service="$1" id status
  for ((i=0; i<90; i++)); do
    id="$("${COMPOSE[@]}" ps -aq "$service")"
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || true)"
    [[ "$status" == healthy ]] && return 0
    [[ "$status" == exited || "$status" == dead ]] && break
    sleep 2
  done
  echo "Service $service did not become healthy; inspect its logs."
  return 1
}

snapshot() {
  local service id tag count=0
  printf 'services:\n' > "$BACKUPS/pending.yml"
  for service in backend frontend; do
    id="$("${COMPOSE[@]}" ps -q "$service")"
    [[ -n "$id" ]] || continue
    tag="pcba-erp/$service:rollback-$RELEASE"
    docker tag "$(docker inspect --format '{{.Image}}' "$id")" "$tag"
    printf '  %s:\n    image: %s\n    pull_policy: never\n' "$service" "$tag" >> "$BACKUPS/pending.yml"
    count=$((count+1))
  done
  if [[ "$count" == 2 ]]; then
    mv "$BACKUPS/pending.yml" "$BACKUPS/previous.yml"
    SNAPSHOT_READY=1
  else
    rm -f "$BACKUPS/pending.yml"
  fi
}

rollback() {
  [[ -f "$BACKUPS/previous.yml" ]] || { echo 'No complete rollback snapshot.'; return 1; }
  "${COMPOSE[@]}" -f "$BACKUPS/previous.yml" up -d --no-deps --force-recreate backend
  wait_healthy backend
  "${COMPOSE[@]}" -f "$BACKUPS/previous.yml" up -d --no-deps --force-recreate frontend
  wait_healthy frontend
  echo 'Application images restored. Database migrations remain applied.'
}

build_backend() {
  docker volume create pcba-erp-m2-repo >/dev/null
  docker run --rm -v pcba-erp-m2-repo:/root/.m2/repository \
    -v "$ROOT/backend:/work" -w /work maven:3.9-eclipse-temurin-17 \
    mvn -B clean verify -pl pcba-erp-admin -am
  "${COMPOSE[@]}" build backend
}

build_frontend() {
  docker run --rm -e VITE_API_BASE=/api -v "$ROOT/frontend:/work" -w /work node:20-alpine \
    sh -ec 'npm ci --no-audit --no-fund && npm run build'
  "${COMPOSE[@]}" build frontend
}

case "$SCOPE" in
  status) "${COMPOSE[@]}" ps; exit 0 ;;
  rollback) rollback; exit 0 ;;
  sql) echo 'Add a versioned migration under admin/src/main/resources/db/migration, then deploy be. No legacy SQL is executed.'; exit 2 ;;
  all|be|fe) ;;
  *) echo 'Usage: bash deploy.sh all|be|fe|status|rollback'; exit 2 ;;
esac

export BACKEND_IMAGE="$(image_for backend)"
export FRONTEND_IMAGE="$(image_for frontend)"
if [[ "$SCOPE" != fe ]]; then
  export BACKEND_IMAGE="pcba-erp/backend:$RELEASE"
  build_backend
fi
if [[ "$SCOPE" != be ]]; then
  export FRONTEND_IMAGE="pcba-erp/frontend:$RELEASE"
  build_frontend
fi
[[ -n "$BACKEND_IMAGE" && -n "$FRONTEND_IMAGE" ]] || { echo 'First deployment requires all.'; exit 2; }
"${COMPOSE[@]}" config --quiet
snapshot
on_error() {
  local result=$?
  trap - ERR
  echo 'Release failed; restoring images when a complete snapshot exists.'
  if [[ "$SNAPSHOT_READY" == 1 ]]; then
    rollback || true
  fi
  exit "$result"
}
trap on_error ERR
"${COMPOSE[@]}" up -d postgres redis
wait_healthy postgres
wait_healthy redis
"${COMPOSE[@]}" up -d --no-deps backend
wait_healthy backend
# Recreate the proxy after backend changes so its upstream DNS is refreshed.
"${COMPOSE[@]}" up -d --no-deps --force-recreate frontend
wait_healthy frontend
trap - ERR
"${COMPOSE[@]}" ps
echo "Release $RELEASE is healthy."
