"""Install only the ERP host-Nginx virtual host, preserving other applications."""
from pathlib import Path
import os,re,subprocess,sys
from ecs_db import settings

MARKER='# Managed by PCBA ERP\n'

def command(arguments):
    return subprocess.run(arguments,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)

def apply(path,target=Path('/etc/nginx/conf.d/pcba-erp.conf')):
    config=settings(path);domain=config['ERP_DOMAIN'];port=config.get('ERP_HTTP_PORT','18080')
    certificate=config['ERP_TLS_CERT'];key=config['ERP_TLS_KEY']
    if not re.fullmatch(r'[a-zA-Z0-9.-]+',domain) or not port.isdigit() or not 1024<int(port)<65536:
        raise ValueError('Invalid ERP host or port')
    for value in (certificate,key):
        if not re.fullmatch(r'/[a-zA-Z0-9_./-]+',value) or not Path(value).is_file():
            raise ValueError('ERP TLS file missing or unsafe path')
    match=command(['openssl','x509','-in',certificate,'-noout','-checkhost',domain])
    expires=command(['openssl','x509','-in',certificate,'-noout','-checkend','0'])
    if match.returncode or 'does match certificate' not in match.stdout or expires.returncode:
        raise ValueError('ERP certificate does not cover domain or has expired')
    previous=target.read_bytes() if target.exists() else None
    if previous is not None and not previous.startswith(MARKER.encode()):
        raise ValueError('Refusing to replace an unmanaged Nginx file')
    content=MARKER+'''map $http_upgrade $pcba_connection_upgrade { default upgrade; '' close; }
server {
    listen 80;
    server_name DOMAIN;
    return 301 https://DOMAIN$request_uri;
}
server {
    listen 443 ssl;
    server_name DOMAIN;
    ssl_certificate CERTIFICATE;
    ssl_certificate_key KEY;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 200m;
    location / {
        proxy_pass http://127.0.0.1:PORT;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $pcba_connection_upgrade;
        proxy_read_timeout 3600s;
    }
}
'''
    for name,value in [('DOMAIN',domain),('CERTIFICATE',certificate),('KEY',key),('PORT',port)]:content=content.replace(name,value)
    if previous is None:
        dump=command(['nginx','-T'])
        for names in re.findall(r'(?m)^\s*server_name\s+([^;]+);',dump.stdout+dump.stderr):
            if domain in names.split():raise ValueError('Domain already belongs to an existing virtual host')
    temporary=target.with_suffix('.conf.tmp');temporary.write_text(content,encoding='utf-8');os.chmod(str(temporary),0o644)
    os.replace(str(temporary),str(target))
    try:
        if command(['nginx','-t']).returncode or command(['nginx','-s','reload']).returncode:
            raise RuntimeError('Nginx configuration validation or reload failed')
        url='https://'+domain+'/api/actuator/health/readiness'
        probe=command(['curl','--noproxy','*','--fail','--silent','--show-error','--max-time','30',
            '--resolve',domain+':443:127.0.0.1',url])
        import json
        if probe.returncode or json.loads(probe.stdout).get('status')!='UP':
            raise RuntimeError('ERP HTTPS readiness check failed')
    except Exception:
        if previous is None:target.unlink()
        else:target.write_bytes(previous)
        command(['nginx','-t']);command(['nginx','-s','reload'])
        raise
    print('ERP host Nginx and HTTPS readiness: PASS')

if __name__=='__main__':
    try:apply(sys.argv[1])
    except Exception as error:
        print('ERP virtual host setup failed: '+str(error));sys.exit(2)
