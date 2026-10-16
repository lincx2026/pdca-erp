#!/usr/bin/env bash
# Read-only inventory: no environment values, database rows, or other projects' configuration.
set -Eeuo pipefail
ROOT="${1:?deployment path required}"
uname -sm
printf 'SSH account uid: '; id -u
docker version --format 'Docker server: {{.Server.Version}}'
docker compose version --short
awk '/MemAvailable:/{printf "Available RAM MB: %.0f\n", $2/1024}' /proc/meminfo
df -Pm /opt | awk 'NR==2{print "Available disk MB: "$4}'
listeners="$(ss -lntH | awk '{print $4}')"
for port in 80 443 3306 5432 6379 8080 18080 18081 18443 15432 16379; do
  if grep -Eq ":$port$" <<< "$listeners"; then
    echo "TCP $port: occupied"
  else
    echo "TCP $port: available"
  fi
done
docker ps --format '{{.Image}}' | while IFS= read -r image; do
  case "$image" in
    *postgres*) echo 'Running database container: PostgreSQL' ;;
    *mysql*|*mariadb*) echo 'Running database container: MySQL/MariaDB' ;;
    *redis*) echo 'Running cache container: Redis' ;;
    *nginx*|*caddy*|*traefik*) echo 'Running reverse proxy container detected' ;;
    *) : ;;
  esac
done
for client in psql mysql nginx; do
  if command -v "$client" >/dev/null; then echo "Host binary available: $client"; fi
done
for path in /etc/nginx/conf.d /etc/nginx/sites-enabled /www/server/panel/vhost/nginx "$ROOT"; do
  if [[ -d "$path" ]]; then echo "Existing directory: $path"; fi
done
for name in .env docker/nginx/ssl/fullchain.pem docker/nginx/ssl/private.key; do
  if [[ -s "$ROOT/$name" ]]; then echo "ERP prerequisite present: $name"; else echo "ERP prerequisite missing: $name"; fi
done
