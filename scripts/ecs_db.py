"""Read-only PostgreSQL preflight, compatible with the ECS Python 3.6 runtime."""
from pathlib import Path
import os
import subprocess
import sys
import re
import time

def settings(path):
    result={}
    for line in Path(path).read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key,value=line.split('=',1);result[key.strip()]=value.strip()
    return result

def connection(path):
    config=settings(path)
    return dict(os.environ,PGHOST=config.get('DB_HOST','127.0.0.1'),PGPORT=config.get('DB_PORT','5432'),
        PGDATABASE=config.get('POSTGRES_DB','pcba_erp'),PGUSER=config.get('POSTGRES_USER','pcba'),
        PGPASSWORD=config.get('POSTGRES_PASSWORD',''),PGSSLMODE=config.get('DB_SSLMODE','disable'),PGCONNECT_TIMEOUT='10')

def check(path,inspect=False):
    environment=connection(path)
    query="BEGIN READ ONLY; SELECT current_database(), (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'),to_regclass('public.flyway_schema_history') IS NOT NULL,to_regclass('public.sys_user') IS NOT NULL; ROLLBACK;"
    result=subprocess.run(['psql','-X','-w','-At','-v','ON_ERROR_STOP=1','-c',query],env=environment,
        stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
    if result.returncode:
        reasons={'Ident authentication failed':'IDENT_POLICY_REJECTED','password authentication failed':'PASSWORD_REJECTED','does not exist':'DATABASE_OR_ROLE_MISSING',
            'no pg_hba.conf entry':'CONNECTION_POLICY_REJECTED','Connection refused':'CONNECTION_REFUSED','timeout':'CONNECTION_TIMEOUT'}
        reason=next((v for k,v in reasons.items() if k in result.stderr),'CONNECTION_FAILED')
        print('Configured PostgreSQL authentication: FAIL ('+reason+')')
        if reason=='CONNECTION_FAILED':
            detail=result.stderr
            if environment['PGPASSWORD']:detail=detail.replace(environment['PGPASSWORD'],'[REDACTED]')
            print('PostgreSQL diagnostic: '+detail[:600].strip())
        return False
    row=next(line for line in result.stdout.splitlines() if '|' in line)
    name,count,flyway,users=row.split('|')
    print('Configured PostgreSQL authentication: PASS')
    print('ERP database metadata (name|tables|Flyway|sys_user): '+row)
    if int(count)>0 and (flyway!='t' or users!='t') and not inspect:
        print('Refusing automatic schema changes in a populated database without Flyway history.')
        return False
    return True

def backup(path,target):
    descriptor=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(descriptor,'wb') as output:
        result=subprocess.run(['pg_dump','-w','-Fc'],env=connection(path),stdout=output,stderr=subprocess.PIPE)
    if result.returncode:
        Path(target).unlink()
        print('ERP database backup failed; deployment stopped.')
        return False
    print('ERP database backup completed.')
    return True

def prepare_local(path):
    """Replace Ident only for the configured local database/role; restore on failure."""
    environment=connection(path)
    probe=subprocess.run(['psql','-X','-w','-At','-c','SELECT 1'],env=environment,
        stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
    if probe.returncode==0:return check(path)
    if 'Ident authentication failed' not in probe.stderr or os.geteuid()!=0 or environment['PGHOST']!='127.0.0.1':
        return check(path)
    database=environment['PGDATABASE'];role=environment['PGUSER']
    if not all(re.fullmatch(r'[a-zA-Z0-9_]+',value) for value in (database,role)):
        print('Local authentication setup requires simple database and role names.');return False
    def admin(query):
        result=subprocess.run(['runuser','-u','postgres','--','psql','-X','-At','-v','ON_ERROR_STOP=1',
            '-d','postgres','-c',query],stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
        if result.returncode:raise RuntimeError('Local PostgreSQL administration failed')
        return result.stdout.strip()
    method=admin("SELECT CASE WHEN rolpassword LIKE 'SCRAM-SHA-256$%' THEN 'scram-sha-256' WHEN rolpassword LIKE 'md5%' THEN 'md5' ELSE '' END FROM pg_authid WHERE rolname='"+role+"' AND rolcanlogin AND EXISTS(SELECT FROM pg_database WHERE datname='"+database+"')")
    if method not in ('scram-sha-256','md5'):
        print('Target database or password-enabled role is missing.');return False
    target=Path(admin('SHOW hba_file'))
    previous=target.read_bytes()
    prefix=('# PCBA ERP local authentication\nhost "'+database+'" "'+role+'" 127.0.0.1/32 '+method+'\n').encode()
    saved=target.with_name(target.name+'.pcba-erp-before-'+str(time.time_ns() if hasattr(time,'time_ns') else int(time.time()*1000000)))
    descriptor=os.open(str(saved),os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(descriptor,'wb') as output:output.write(previous)
    try:
        if target.read_bytes()!=previous:raise RuntimeError('Authentication configuration changed concurrently')
        target.write_bytes(prefix+previous)
        admin('SELECT pg_reload_conf()');time.sleep(1)
        if not check(path):raise RuntimeError('ERP connection validation failed after scoped authentication setup')
    except Exception:
        if target.read_bytes()==prefix+previous:
            target.write_bytes(previous);admin('SELECT pg_reload_conf()')
        print('ERP authentication setup failed; original configuration restored.');return False
    print('ERP-specific local authentication configured; existing passwords unchanged.')
    return True

if __name__=='__main__':
    if '--prepare-local' in sys.argv[2:]:sys.exit(0 if prepare_local(sys.argv[1]) else 2)
    if len(sys.argv)>3 and sys.argv[2]=='--backup':sys.exit(0 if backup(sys.argv[1],sys.argv[3]) else 2)
    sys.exit(0 if check(sys.argv[1],'--inspect' in sys.argv[2:]) else 2)
