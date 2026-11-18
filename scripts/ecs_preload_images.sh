#!/usr/bin/env bash
# Preload immutable images on ECS before changing any running services.
set -Eeuo pipefail
revision="$1"
[[ "$revision" =~ ^[a-f0-9]{40}$ ]] || exit 2

preload() {
  local service="$1" ref="$2" expected="$3"
  [[ "$expected" =~ ^sha256:[a-f0-9]{64}$ ]] || exit 2
  echo "ECS image preload: $service"
  if ! docker image inspect --format '{{.Id}}' "$expected" >/dev/null 2>&1; then
    docker pull "$ref"
  fi
  if [[ "$(docker image inspect --format '{{.Id}}' "$expected" 2>/dev/null)" != "$expected" ]]; then
    echo "ECS image ID verification failed: $service" >&2
    exit 1
  fi
  docker tag "$expected" "pcba-erp/$service:ci-$revision"
  echo "ECS image verified: $service $expected"
}

preload backend "$2" "$3"
preload frontend "$4" "$5"
preload redis redis:7.2.4-alpine "$6"
