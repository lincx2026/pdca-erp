#!/usr/bin/env bash
# Shared transport for deployment and read-only ECS inspection; secrets stay on the runner.
set -Eeuo pipefail
operation="${1:?operation required}"
[[ "$operation" == deploy || "$operation" == inspect ]] || exit 2
for name in ECS_HOST ECS_USER ECS_SSH_KEY ECS_KNOWN_HOSTS; do
  [[ -n "${!name:-}" ]] || { echo "Missing deployment secret: $name"; exit 2; }
done
ECS_PORT="${ECS_PORT:-22}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/pcba-erp}"
[[ "$ECS_HOST" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]] || exit 2
[[ "$ECS_USER" =~ ^[a-zA-Z_][a-zA-Z0-9_-]*$ ]] || exit 2
[[ "$ECS_PORT" =~ ^[0-9]{1,5}$ && "$ECS_PORT" -ge 1 && "$ECS_PORT" -le 65535 ]] || exit 2
[[ "$DEPLOY_PATH" =~ ^/[a-zA-Z0-9_/-]+$ && "$DEPLOY_PATH" != / && "$DEPLOY_PATH" != *..* ]] || exit 2
key_dir="$RUNNER_TEMP/ecs-ssh"
install -d -m 700 "$key_dir"
printf '%s\n' "$ECS_SSH_KEY" > "$key_dir/key"
printf '%s\n' "$ECS_KNOWN_HOSTS" > "$key_dir/known_hosts"
chmod 600 "$key_dir/key" "$key_dir/known_hosts"
ssh_args=(-i "$key_dir/key" -p "$ECS_PORT" -o BatchMode=yes -o ConnectTimeout=20
  -o ServerAliveInterval=30 -o ServerAliveCountMax=5
  -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$key_dir/known_hosts")
target="$ECS_USER@$ECS_HOST"
auth_dir=''
cleanup() {
  if [[ "$auth_dir" =~ ^/tmp/pcba-erp-auth\.[a-zA-Z0-9]+$ ]]; then
    ssh "${ssh_args[@]}" "$target" "rm -rf -- $auth_dir" || true
  fi
  rm -f "$key_dir/key" "$key_dir/known_hosts"
  rm -f "$key_dir/source.bundle"
  if [[ -d "$key_dir/docker" ]]; then rm -rf -- "$key_dir/docker"; fi
}
trap cleanup EXIT
auth_dir="$(ssh "${ssh_args[@]}" "$target" 'mktemp -d /tmp/pcba-erp-auth.XXXXXXXX')"
[[ "$auth_dir" =~ ^/tmp/pcba-erp-auth\.[a-zA-Z0-9]+$ ]] || exit 2
if [[ -n "${ERP_ENV:-}" ]]; then
  printf '%s\n' "$ERP_ENV" | ssh "${ssh_args[@]}" "$target" "umask 077; cat > $auth_dir/runtime.env"
fi
ssh "${ssh_args[@]}" "$target" "cat > $auth_dir/ecs_db.py" < scripts/ecs_db.py
if [[ "$operation" == inspect ]]; then
  ERP_DOMAIN="${ERP_DOMAIN:-erp.thinkfiter.com}"
  [[ "$ERP_DOMAIN" =~ ^[a-zA-Z0-9.-]+$ ]] || exit 2
  ssh "${ssh_args[@]}" "$target" "bash -s -- $DEPLOY_PATH $auth_dir/runtime.env $ERP_DOMAIN" < scripts/ecs_preflight.sh
else
  latest="$(GH_TOKEN="$REGISTRY_TOKEN" gh api "repos/$GITHUB_REPOSITORY/commits/main" --jq .sha)"
  [[ "$latest" == "$GITHUB_SHA" ]] || { echo 'main has advanced; rerun the latest revision.'; exit 2; }
  git bundle create "$key_dir/source.bundle" HEAD
  ssh "${ssh_args[@]}" "$target" "cat > $auth_dir/source.bundle" < "$key_dir/source.bundle"
  if [[ -n "${ERP_DATA_IMPORT:-}" ]]; then
    printf '%s\n' "$ERP_DATA_IMPORT" | ssh "${ssh_args[@]}" "$target" "umask 077; cat > $auth_dir/data-import.json"
  fi
  for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
    [[ "$image" =~ ^ghcr\.io/[a-z0-9./_-]+@sha256:[a-f0-9]{64}$ ]] || exit 2
  done
  printf 'BACKEND_SOURCE=%s\nFRONTEND_SOURCE=%s\n' "$BACKEND_IMAGE" "$FRONTEND_IMAGE" | \
    ssh "${ssh_args[@]}" "$target" "cat > $auth_dir/source-images.env"
  install -d -m 700 "$key_dir/docker"
  printf '%s' "$REGISTRY_TOKEN" | docker --config "$key_dir/docker" login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
  for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do docker --config "$key_dir/docker" pull "$image"; done
  docker pull redis:7.2.4-alpine
  backend_id="$(docker image inspect --format '{{.Id}}' "$BACKEND_IMAGE")"
  frontend_id="$(docker image inspect --format '{{.Id}}' "$FRONTEND_IMAGE")"
  redis_id="$(docker image inspect --format '{{.Id}}' redis:7.2.4-alpine)"
  for id in "$backend_id" "$frontend_id" "$redis_id"; do [[ "$id" =~ ^sha256:[a-f0-9]{64}$ ]] || exit 2; done
  loaded_backend="pcba-erp/backend:ci-$GITHUB_SHA"; loaded_frontend="pcba-erp/frontend:ci-$GITHUB_SHA"
  loaded_redis="pcba-erp/redis:ci-$GITHUB_SHA"
  docker tag "$BACKEND_IMAGE" "$loaded_backend"; docker tag "$FRONTEND_IMAGE" "$loaded_frontend"
  docker tag redis:7.2.4-alpine "$loaded_redis"
  # A cancelled SSH session can leave its Docker pull client holding the old lock.
  ssh "${ssh_args[@]}" "$target" "pkill -TERM -f '^docker pull ghcr.io/lincx2026/pdca-erp-(backend|frontend)@sha256:[a-f0-9]{64}$' || true"
  docker save "$loaded_backend" "$loaded_frontend" "$loaded_redis" | gzip -1 | \
    ssh "${ssh_args[@]}" "$target" "bash -o pipefail -c 'gzip -d | docker load'"
  latest="$(GH_TOKEN="$REGISTRY_TOKEN" gh api "repos/$GITHUB_REPOSITORY/commits/main" --jq .sha)"
  [[ "$latest" == "$GITHUB_SHA" ]] || { echo 'main advanced during image transfer; no services changed.'; exit 2; }
  ssh "${ssh_args[@]}" "$target" \
    "PCBA_PRELOADED=1 BACKEND_IMAGE_ID=$backend_id FRONTEND_IMAGE_ID=$frontend_id ERP_REDIS_IMAGE=$loaded_redis ERP_REDIS_IMAGE_ID=$redis_id bash -s -- $DEPLOY_PATH $GITHUB_SHA $GITHUB_REPOSITORY $loaded_backend $loaded_frontend $auth_dir/runtime.env $auth_dir/source.bundle" \
    < scripts/ecs_release.sh
fi
