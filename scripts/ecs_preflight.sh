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
sys.path.insert(0,str(checker.parent))
from ecs_db import settings
config=settings(sys.argv[1])
if __import__('os').geteuid()==0:
    database=config.get('POSTGRES_DB','pcba_erp').replace("'","''")
    role=config.get('POSTGRES_USER','pcba').replace("'","''")
    query="SELECT 'Target database exists: '||EXISTS(SELECT FROM pg_database WHERE datname='"+database+"'); SELECT 'Target login role exists: '||EXISTS(SELECT FROM pg_roles WHERE rolname='"+role+"' AND rolcanlogin); SHOW server_version;"
    admin=subprocess.run(['runuser','-u','postgres','--','psql','-X','-At','-d','postgres','-c',query],stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
    print('Local PostgreSQL metadata query status: '+str(admin.returncode))
    if admin.returncode==0:print(admin.stdout.strip())
domain=sys.argv[2]
nginx=subprocess.run(['nginx','-T'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
print('Host Nginx configuration status: '+str(nginx.returncode))
if nginx.returncode:
    for line in nginx.stderr.splitlines():
        if '[emerg]' in line or '[error]' in line:print('Nginx diagnostic: '+line[:400])
configuration=nginx.stdout+nginx.stderr
names=re.findall(r'\bserver_name\s+([^;]+);',configuration)
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
values={}
for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key,value=line.split('=',1);values[key.strip()]=value.strip()
certificate=values.get('ERP_TLS_CERT','')
if certificate and Path(certificate).is_file():
    check=subprocess.run(['openssl','x509','-in',certificate,'-noout','-checkhost',domain],stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
    print('Configured TLS host check: '+str(check.returncode)+' '+check.stdout.strip())
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
docker ps -a --filter label=com.docker.compose.project=pcba-erp --format 'ERP service: {{.Label "com.docker.compose.service"}} {{.Status}}'
docker image ls --format '{{.Repository}} {{.Size}}' | while IFS= read -r image; do
  case "$image" in ghcr.io/lincx2026/pdca-erp-*) echo "ERP image available: $image" ;; esac
done
if pgrep -f '^docker pull ghcr.io/lincx2026/pdca-erp-' >/dev/null; then echo 'ERP registry image download is active.'; fi
if [[ -s "$ROOT/.deploy-backups/initial-data-import.sha256" ]]; then echo 'Historical data verification marker present.'; fi
