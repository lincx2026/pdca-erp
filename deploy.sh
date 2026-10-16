#!/usr/bin/env bash
# Build versioned images, migrate through Flyway, then switch containers.
# Historical db/init and db/patch are never executed here.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
command -v docker >/dev/null
docker compose version >/dev/null
[[ -f .env ]] || { echo 'Create .env with python scripts/init-env.py'; exit 2; }
BACKUPS="$ROOT/.deploy-backups"
mkdir -p "$BACKUPS"
COMPOSE=(docker compose --env-file "$ROOT/.env")
if [[ -f "$BACKUPS/current.env" ]]; then
  COMPOSE+=(--env-file "$BACKUPS/current.env")
fi
COMPOSE+=(-f "$ROOT/docker/docker-compose.yml")
DEPLOY_MODE="${DEPLOY_MODE:-$(sed -n 's/^DEPLOY_MODE=//p' "$ROOT/.env" | tail -n 1)}"
DEPLOY_MODE="${DEPLOY_MODE:-standalone}"
[[ "$DEPLOY_MODE" == standalone || "$DEPLOY_MODE" == shared ]] || { echo 'Invalid DEPLOY_MODE'; exit 2; }
if [[ "$DEPLOY_MODE" == shared ]]; then
  COMPOSE+=(-f "$ROOT/docker/docker-compose.shared.yml")
fi
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
  "${COMPOSE[@]}" -f "$BACKUPS/previous.yml" up -d --no-deps --force-recreate backend || return 1
  wait_healthy backend || return 1
  "${COMPOSE[@]}" -f "$BACKUPS/previous.yml" up -d --no-deps --force-recreate frontend || return 1
  wait_healthy frontend || return 1
  BACKEND_IMAGE="$(image_for backend)"
  FRONTEND_IMAGE="$(image_for frontend)"
  persist_images || return 1
  echo 'Application images restored. Database migrations remain applied.'
}

persist_images() {
  [[ -n "$BACKEND_IMAGE" && -n "$FRONTEND_IMAGE" ]] || return 1
  printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$BACKEND_IMAGE" "$FRONTEND_IMAGE" > "$BACKUPS/current.env.tmp"
  mv "$BACKUPS/current.env.tmp" "$BACKUPS/current.env"
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
  all|be|fe|images) ;;
  *) echo 'Usage: bash deploy.sh all|be|fe|images|status|rollback'; exit 2 ;;
esac

if [[ "$SCOPE" == images ]]; then
  for image in "${BACKEND_IMAGE:-}" "${FRONTEND_IMAGE:-}"; do
    [[ "$image" =~ ^ghcr\.io/[a-z0-9./_-]+@sha256:[a-f0-9]{64}$ ]] || {
      echo 'images requires BACKEND_IMAGE and FRONTEND_IMAGE pinned to GHCR SHA-256 digests.'; exit 2;
    }
  done
  export BACKEND_IMAGE FRONTEND_IMAGE
  docker pull "$BACKEND_IMAGE"
  docker pull "$FRONTEND_IMAGE"
else
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
if [[ "$DEPLOY_MODE" == shared ]]; then
  "${COMPOSE[@]}" up -d redis
else
  "${COMPOSE[@]}" up -d postgres redis
  wait_healthy postgres
fi
wait_healthy redis
"${COMPOSE[@]}" up -d --no-deps backend
wait_healthy backend
# Recreate the proxy after backend changes so its upstream DNS is refreshed.
"${COMPOSE[@]}" up -d --no-deps --force-recreate frontend
wait_healthy frontend
persist_images
trap - ERR
"${COMPOSE[@]}" ps
echo "Release $RELEASE is healthy."
