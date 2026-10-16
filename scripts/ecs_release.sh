#!/usr/bin/env bash
# Invoked over SSH by CI. Runtime .env, certificates and database data stay on ECS.
set -Eeuo pipefail
ROOT="${1:?deployment path required}"
REVISION="${2:?commit required}"
REPOSITORY="${3:?repository required}"
export BACKEND_IMAGE="${4:?backend digest required}"
export FRONTEND_IMAGE="${5:?frontend digest required}"
RUNTIME_ENV="${6:-}"
SOURCE_BUNDLE="${7:-}"
[[ "$ROOT" =~ ^/[a-zA-Z0-9_/-]+$ && "$ROOT" != / && "$ROOT" != *..* ]] || exit 2
[[ "$REVISION" =~ ^[a-f0-9]{40}$ && "$REPOSITORY" =~ ^[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+$ ]] || exit 2
if [[ "${PCBA_PRELOADED:-0}" == 1 ]]; then
  [[ "$BACKEND_IMAGE" == "pcba-erp/backend:ci-$REVISION" && "$FRONTEND_IMAGE" == "pcba-erp/frontend:ci-$REVISION" ]] || exit 2
  for id in "${BACKEND_IMAGE_ID:-}" "${FRONTEND_IMAGE_ID:-}"; do [[ "$id" =~ ^sha256:[a-f0-9]{64}$ ]] || exit 2; done
else
  for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
    [[ "$image" =~ ^ghcr\.io/[a-z0-9./_-]+@sha256:[a-f0-9]{64}$ ]] || exit 2
  done
fi
if [[ -n "$RUNTIME_ENV" ]]; then
  [[ "$RUNTIME_ENV" =~ ^/tmp/pcba-erp-auth\.[a-zA-Z0-9]+/runtime.env$ ]] || exit 2
fi
if [[ -n "$SOURCE_BUNDLE" ]]; then
  [[ "$SOURCE_BUNDLE" =~ ^/tmp/pcba-erp-auth\.[a-zA-Z0-9]+/source.bundle$ && -s "$SOURCE_BUNDLE" ]] || exit 2
fi
if [[ ! -d "$ROOT" ]]; then
  [[ -s "$RUNTIME_ENV" ]] || { echo 'New ECS deployment requires ERP_ENV.'; exit 2; }
  python3 "$(dirname "$RUNTIME_ENV")/ecs_db.py" "$RUNTIME_ENV" --prepare-local
  if [[ -n "$SOURCE_BUNDLE" ]]; then
    git init "$ROOT"
    git -C "$ROOT" fetch "$SOURCE_BUNDLE" HEAD
    [[ "$(git -C "$ROOT" rev-parse FETCH_HEAD)" == "$REVISION" ]] || exit 2
    git -C "$ROOT" checkout --detach "$REVISION"
    git -C "$ROOT" remote add origin "https://github.com/$REPOSITORY.git"
  else
    git clone --branch main "https://github.com/$REPOSITORY.git" "$ROOT"
  fi
fi
cd "$ROOT"
[[ -d .git ]] || { echo 'Deployment path is not a Git repository.'; exit 2; }
if [[ ! -s .env ]]; then
  [[ -s "$RUNTIME_ENV" ]] || { echo 'Provision .env or configure ERP_ENV.'; exit 2; }
  install -m 600 "$RUNTIME_ENV" .env
fi
DEPLOY_MODE="$(sed -n 's/^DEPLOY_MODE=//p' .env | tail -n 1)"
DEPLOY_MODE="${DEPLOY_MODE:-standalone}"
if [[ "$DEPLOY_MODE" != shared ]]; then
  [[ -s docker/nginx/ssl/fullchain.pem && -s docker/nginx/ssl/private.key ]] || {
    echo 'Provision HTTPS certificates before standalone deployment.'; exit 2;
  }
fi
exec 9>.deploy.lock
flock -w 300 9
git diff --quiet && git diff --cached --quiet || {
  echo 'Tracked files on ECS have local changes; refusing to overwrite them.'; exit 2;
}
PREVIOUS="$(git rev-parse HEAD)"
if [[ "$DEPLOY_MODE" == shared ]]; then
  checker="${RUNTIME_ENV%/runtime.env}/ecs_db.py"
  [[ -f "$checker" ]] || checker=scripts/ecs_db.py
  python3 "$checker" .env --prepare-local
fi
if [[ -n "$SOURCE_BUNDLE" ]]; then
  git fetch "$SOURCE_BUNDLE" HEAD
else
  git fetch --no-tags "https://github.com/$REPOSITORY.git" main
fi
git merge-base --is-ancestor "$REVISION" FETCH_HEAD || {
  echo 'Requested revision is not part of main.'; exit 2;
}
[[ "$(git rev-parse FETCH_HEAD)" == "$REVISION" ]] || {
  echo 'main has advanced; rerun the workflow for its current commit.'; exit 2;
}
git checkout --detach "$REVISION"
mkdir -p .deploy-backups
if [[ "$DEPLOY_MODE" == shared ]]; then
  python3 scripts/ecs_db.py .env --backup ".deploy-backups/database-before-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
fi
if [[ -n "$RUNTIME_ENV" && -s "${RUNTIME_ENV%/runtime.env}/data-import.json" ]]; then
  python3 scripts/ecs_import.py .env "${RUNTIME_ENV%/runtime.env}/data-import.json"
fi
release() {
  if [[ "$DEPLOY_MODE" == shared ]]; then
    python3 scripts/ecs_site.py .env --prepare-certificate || return $?
  fi
  if [[ "${PCBA_PRELOADED:-0}" == 1 ]]; then
    bash deploy.sh preloaded || return $?
  else
    bash deploy.sh images || return $?
  fi
  if [[ "$DEPLOY_MODE" == shared ]]; then
    python3 scripts/ecs_site.py .env || { bash deploy.sh rollback || true; return 1; }
  fi
}
if release; then
  if [[ -s "${RUNTIME_ENV%/runtime.env}/source-images.env" ]]; then
    cp "${RUNTIME_ENV%/runtime.env}/source-images.env" .deploy-backups/source-images.env
  fi
  printf '%s\n' "$REVISION" > .deploy-backups/revision
else
  result=$?
  # deploy.sh restores images; restore the matching configuration files as well.
  git checkout --detach "$PREVIOUS"
  if [[ -f .deploy-backups/current.env ]]; then
    unset BACKEND_IMAGE FRONTEND_IMAGE
    compose=(docker compose --env-file .env --env-file .deploy-backups/current.env -f docker/docker-compose.yml)
    if [[ "$DEPLOY_MODE" == shared ]]; then compose+=(-f docker/docker-compose.shared.yml); fi
    "${compose[@]}" up -d --no-build --no-deps --force-recreate backend frontend || true
  fi
  echo 'Deployment failed; source revision restored. Database migrations remain applied.'
  exit "$result"
fi
