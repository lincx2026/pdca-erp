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
}
trap cleanup EXIT
if [[ "$operation" == inspect ]]; then
  ssh "${ssh_args[@]}" "$target" "bash -s -- $DEPLOY_PATH" < scripts/ecs_preflight.sh
else
  for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
    [[ "$image" =~ ^ghcr\.io/[a-z0-9./_-]+@sha256:[a-f0-9]{64}$ ]] || exit 2
  done
  auth_dir="$(ssh "${ssh_args[@]}" "$target" 'mktemp -d /tmp/pcba-erp-auth.XXXXXXXX')"
  [[ "$auth_dir" =~ ^/tmp/pcba-erp-auth\.[a-zA-Z0-9]+$ ]] || exit 2
  printf '%s' "$REGISTRY_TOKEN" | ssh "${ssh_args[@]}" "$target" \
    "docker --config $auth_dir login ghcr.io -u $GITHUB_ACTOR --password-stdin"
  ssh "${ssh_args[@]}" "$target" \
    "DOCKER_CONFIG=$auth_dir bash -s -- $DEPLOY_PATH $GITHUB_SHA $GITHUB_REPOSITORY $BACKEND_IMAGE $FRONTEND_IMAGE" \
    < scripts/ecs_release.sh
fi
