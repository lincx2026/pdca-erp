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
if [[ -s "${2:-}" ]]; then
  python3 - "$2" "${3:?domain required}" <<'PY'
import re,subprocess,sys,shlex
from pathlib import Path
checker=Path(sys.argv[1]).parent/'ecs_db.py'
subprocess.run(['python3',str(checker),sys.argv[1],'--inspect'])
domain=sys.argv[2]
nginx=subprocess.run(['nginx','-T'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
configuration=nginx.stdout+nginx.stderr
names=re.findall(r'(?m)^\s*server_name\s+([^;]+);',configuration)
print('Existing exact ERP virtual host: '+str(any(domain in line.split() for line in names)))
found=False
for match in re.finditer(r'(?m)^\s*ssl_certificate\s+([^;]+);',configuration):
    try:certificate=shlex.split(match.group(1))[0]
    except (IndexError,ValueError):continue
    if not Path(certificate).is_file():continue
    valid=subprocess.run(['openssl','x509','-in',certificate,'-noout','-checkhost',domain],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    expires=subprocess.run(['openssl','x509','-in',certificate,'-noout','-checkend','0'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if valid.returncode or b'does match certificate' not in valid.stdout or expires.returncode:continue
    key=re.search(r'(?m)^\s*ssl_certificate_key\s+([^;]+);',configuration[match.end():match.end()+2048])
    if not key:continue
    try:key_path=shlex.split(key.group(1))[0]
    except (IndexError,ValueError):continue
    if not Path(key_path).is_file():continue
    found=True
    print('Matching existing certificate: '+certificate)
    print('Matching existing key file: '+key_path)
print('Reusable ERP TLS certificate: '+('YES' if found else 'NO'))
print('Certbot available: '+str(subprocess.run(['sh','-c','command -v certbot >/dev/null']).returncode==0))
PY
fi
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
