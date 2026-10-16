#!/usr/bin/env bash
# Invoked over SSH by CI. Runtime .env, certificates and database data stay on ECS.
set -Eeuo pipefail
ROOT="${1:?deployment path required}"
REVISION="${2:?commit required}"
REPOSITORY="${3:?repository required}"
export BACKEND_IMAGE="${4:?backend digest required}"
export FRONTEND_IMAGE="${5:?frontend digest required}"
[[ "$ROOT" =~ ^/[a-zA-Z0-9_/-]+$ && "$ROOT" != / && "$ROOT" != *..* ]] || exit 2
[[ "$REVISION" =~ ^[a-f0-9]{40}$ && "$REPOSITORY" =~ ^[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+$ ]] || exit 2
for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
  [[ "$image" =~ ^ghcr\.io/[a-z0-9./_-]+@sha256:[a-f0-9]{64}$ ]] || exit 2
done
cd "$ROOT"
[[ -s .env && -s docker/nginx/ssl/fullchain.pem && -s docker/nginx/ssl/private.key ]] || {
  echo 'Provision .env and HTTPS certificates on ECS before enabling deployment.'; exit 2;
}
exec 9>.deploy.lock
flock -w 300 9
git diff --quiet && git diff --cached --quiet || {
  echo 'Tracked files on ECS have local changes; refusing to overwrite them.'; exit 2;
}
PREVIOUS="$(git rev-parse HEAD)"
git fetch --no-tags "https://github.com/$REPOSITORY.git" main
git merge-base --is-ancestor "$REVISION" FETCH_HEAD || {
  echo 'Requested revision is not part of main.'; exit 2;
}
[[ "$(git rev-parse FETCH_HEAD)" == "$REVISION" ]] || {
  echo 'main has advanced; rerun the workflow for its current commit.'; exit 2;
}
git checkout --detach "$REVISION"
if bash deploy.sh images; then
  printf '%s\n' "$REVISION" > .deploy-backups/revision
else
  result=$?
  # deploy.sh restores images; restore the matching configuration files as well.
  git checkout --detach "$PREVIOUS"
  if [[ -f .deploy-backups/current.env ]]; then
    unset BACKEND_IMAGE FRONTEND_IMAGE
    docker compose --env-file .env --env-file .deploy-backups/current.env \
      -f docker/docker-compose.yml up -d --no-build --no-deps --force-recreate backend frontend || true
  fi
  echo 'Deployment failed; source revision restored. Database migrations remain applied.'
  exit "$result"
fi
