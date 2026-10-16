"""Stage and replace only the dedicated ERP database, retaining the old database.

Runs as root on the shared ECS. The old production server is never contacted.
"""
from pathlib import Path
from urllib.request import urlopen
from urllib.parse import urlparse
import hashlib,json,os,re,subprocess,sys,tempfile,time
from ecs_db import settings,backup


def identifier(value):
    if not re.fullmatch(r'[a-z_][a-z0-9_]{0,62}',value):
        raise ValueError('Unsafe database identifier')
    return '"'+value+'"'


def fingerprint_sql(table):
    return ("SELECT count(*)::text || '|' || md5(COALESCE(string_agg("
            "md5(row_to_json(t)::text), '' ORDER BY md5(row_to_json(t)::text)), '')) "
            'FROM public.'+identifier(table)+' t')


def run(arguments,**kwargs):
    result=subprocess.run(arguments,stdout=subprocess.PIPE,stderr=subprocess.PIPE,**kwargs)
    if result.returncode:
        raise RuntimeError('Command failed: '+Path(arguments[0]).name)
    return result.stdout.decode('utf-8').strip()


class Replacement:
    def __init__(self,root):
        self.root=Path(root).resolve()
        self.config=settings(self.root/'.env')
        if (self.config.get('POSTGRES_DB')!='pcba_erp' or
                self.config.get('DB_HOST')!='127.0.0.1' or
                self.config.get('DEPLOY_MODE')!='shared'):
            raise ValueError('Replacement requires the dedicated local shared ERP database')
        self.database=self.config['POSTGRES_DB'];identifier(self.database)
        self.role=self.config['POSTGRES_USER'];identifier(self.role)
        self.port=self.config.get('DB_PORT','5432')
        if not self.port.isdigit():raise ValueError('Invalid PostgreSQL port')
        self.directory=self.root/'.deploy-backups'
        self.directory.mkdir(mode=0o700,exist_ok=True)

    def admin(self,query,database='postgres'):
        identifier(database)
        return run(['runuser','-u','postgres','--','psql','-X','-w','-At',
                    '-v','ON_ERROR_STOP=1','-p',self.port,'-d',database,
                    '-c',query],env=dict(os.environ,PGOPTIONS='-c timezone=Asia/Shanghai'))

    def exists(self,database):
        identifier(database)
        return self.admin("SELECT count(*) FROM pg_database WHERE datname='"+database+"'").splitlines()[-1]=='1'

    def container(self,service):
        ids=run(['docker','ps','-aq','--filter','label=com.docker.compose.project=pcba-erp',
                 '--filter','label=com.docker.compose.service='+service]).splitlines()
        if len(ids)!=1:raise RuntimeError('Expected one ERP '+service+' container')
        return ids[0]

    def redis(self,command):
        environment=dict(os.environ,REDISCLI_AUTH=self.config['REDIS_PASSWORD'])
        result=run(['docker','exec','-e','REDISCLI_AUTH',self.container('redis'),
                    'redis-cli','-n','0',command],env=environment)
        if result!='OK':raise RuntimeError('ERP Redis '+command+' failed')
        return result

    def save(self,path,state):
        temporary=path.with_suffix('.tmp')
        with temporary.open('w') as output:json.dump(state,output,indent=2)
        os.chmod(str(temporary),0o600);os.replace(str(temporary),str(path))

    def snapshot(self):
        name='database-before-reimport-'+time.strftime('%Y%m%dT%H%M%SZ',time.gmtime())+'-'+str(os.getpid())+'.dump'
        target=self.directory/name
        if not backup(self.root/'.env',str(target)):
            raise RuntimeError('Backup failed; active database unchanged')
        run(['pg_restore','--list',str(target)])
        result={'dump':str(target),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
        self.save(target.with_suffix('.json'),result)
        print('Verified current ERP backup: '+name,flush=True)
        return result

    def verify(self,database,manifest):
        for table,expected in manifest['fingerprints'].items():
            actual=self.admin(fingerprint_sql(table),database).splitlines()[-1]
            if actual!=expected:raise RuntimeError('Restored content mismatch: '+table)
        for table,count in manifest['counts'].items():
            actual=self.admin('SELECT count(*) FROM public.'+identifier(table),database).splitlines()[-1]
            if actual!=str(count):raise RuntimeError('Restored row count mismatch: '+table)
        version=self.admin('SELECT version FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1',database).splitlines()[-1]
        if version!='7':raise RuntimeError('Source-preserving Flyway baseline 7 is required')

    def validate_manifest(self,manifest):
        if not re.fullmatch(r'[a-f0-9]{64}',manifest.get('sha256','')):
            raise ValueError('Invalid dump checksum')
        if not re.fullmatch(r'[a-f0-9]{64}',manifest.get('key','')):
            raise ValueError('Invalid encryption key')
        parsed=urlparse(manifest.get('url',''))
        if parsed.scheme!='https' or not (parsed.hostname or '').endswith('.aliyuncs.com'):
            raise ValueError('Import requires a private HTTPS OSS URL')
        counts=manifest.get('counts',{});fingerprints=manifest.get('fingerprints',{})
        if not counts or not fingerprints or not set(counts)<=set(fingerprints):
            raise ValueError('Complete source counts and target fingerprints are required')
        if not {'sys_menu','sys_user','sys_role_menu','pdm_material'}<=set(counts):
            raise ValueError('Missing ERP source tables')
        for table,count in counts.items():
            identifier(table)
            if type(count)!=int or count<0:raise ValueError('Invalid source row count')
        for table,value in fingerprints.items():
            identifier(table)
            if not re.fullmatch(r'[0-9]+\|[a-f0-9]{32}',value):raise ValueError('Invalid content fingerprint')
            if table in counts and int(value.split('|')[0])!=counts[table]:
                raise ValueError('Inconsistent source count and fingerprint')
        suffix=manifest['sha256'][:12]
        return self.database+'_import_'+suffix,self.directory/('reimport-'+suffix+'.json')

    def stage(self,manifest):
        database,state_path=self.validate_manifest(manifest)
        if state_path.exists():
            state=json.loads(state_path.read_text())
            if state.get('status') not in ('staged','active'):raise RuntimeError('An interrupted stage needs inspection')
            self.verify(self.database if state['status']=='active' else database,manifest)
            print('Verified replacement already '+state['status']);return
        if self.exists(database):raise RuntimeError('Staging database already exists without a completed state')
        prior=self.snapshot()
        with tempfile.TemporaryDirectory(prefix='reimport-',dir=str(self.directory)) as temporary:
            encrypted=Path(temporary)/'dump.enc';dump=Path(temporary)/'dump.pg'
            with urlopen(manifest['url'],timeout=60) as source,encrypted.open('wb') as output:
                total=0
                while True:
                    block=source.read(1024*1024)
                    if not block:break
                    total+=len(block)
                    if total>128*1024*1024:raise ValueError('Replacement exceeds 128 MiB')
                    output.write(block)
            environment=dict(os.environ,PCBA_IMPORT_PASSWORD=manifest['key'])
            help_result=subprocess.run(['openssl','enc','-help'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            salt=['-saltlen','8'] if b'-saltlen' in help_result.stdout+help_result.stderr else []
            run(['openssl','enc','-d','-aes-256-cbc','-pbkdf2','-iter','200000']+salt+[
                '-pass','env:PCBA_IMPORT_PASSWORD','-in',str(encrypted),'-out',str(dump)],env=environment)
            if hashlib.sha256(dump.read_bytes()).hexdigest()!=manifest['sha256']:
                raise RuntimeError('Replacement dump checksum mismatch')
            run(['pg_restore','--list',str(dump)])
            self.admin('CREATE DATABASE '+identifier(database)+' OWNER '+identifier(self.role)+' TEMPLATE template0 ENCODING \'UTF8\'')
            with dump.open('rb') as source:
                run(['runuser','-u','postgres','--','pg_restore','-w','--single-transaction',
                     '--exit-on-error','--no-owner','--no-privileges','--role',self.role,
                     '-p',self.port,'--dbname',database],stdin=source)
        self.verify(database,manifest)
        self.save(state_path,{'status':'staged','database':database,'sha256':manifest['sha256'],'backup':prior})
        print('Replacement staged; every table content verified; active ERP unchanged.',flush=True)

    def disconnect(self,database):
        self.admin('ALTER DATABASE '+identifier(database)+' ALLOW_CONNECTIONS false')
        self.admin("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='"+database+"' AND pid<>pg_backend_pid()")

    def wait_backend(self,container):
        for _ in range(90):
            state=run(['docker','inspect','--format','{{.State.Health.Status}}',container])
            if state=='healthy':return
            time.sleep(2)
        raise RuntimeError('Replacement backend did not become healthy')

    def activate(self,manifest):
        staged,state_path=self.validate_manifest(manifest)
        if not state_path.exists():raise RuntimeError('Stage and verify the replacement first')
        state=json.loads(state_path.read_text())
        if state.get('status')=='active':print('Replacement already active');return
        if state.get('status')!='staged' or state.get('sha256')!=manifest['sha256']:
            raise RuntimeError('Unverified replacement state')
        self.verify(staged,manifest)
        backend=self.container('backend');self.container('redis')
        archived=self.database+'_before_'+time.strftime('%Y%m%dT%H%M%S',time.gmtime()).lower()+'_'+str(os.getpid())
        identifier(archived)
        if self.exists(archived):raise RuntimeError('Archive database already exists')
        renamed_old=False;renamed_new=False;stopped=False;redis_backup=None
        state.update(status='activating',archived_database=archived)
        self.save(state_path,state)
        try:
            run(['docker','stop','--time','40',backend]);stopped=True
            state['final_backup']=self.snapshot()
            self.redis('SAVE')
            redis_backup=self.directory/(archived+'.rdb')
            run(['docker','cp',self.container('redis')+':/data/dump.rdb',str(redis_backup)])
            os.chmod(str(redis_backup),0o600)
            self.disconnect(self.database)
            self.admin('ALTER DATABASE '+identifier(self.database)+' RENAME TO '+identifier(archived));renamed_old=True
            self.admin('ALTER DATABASE '+identifier(staged)+' RENAME TO '+identifier(self.database));renamed_new=True
            self.admin('ALTER DATABASE '+identifier(self.database)+' ALLOW_CONNECTIONS true')
            self.redis('FLUSHDB')
            run(['docker','start',backend]);self.wait_backend(backend)
            state.update(status='active',archived_database=archived,redis_backup=str(redis_backup))
            self.save(state_path,state)
            print('ERP database replaced successfully. Old database retained as '+archived,flush=True)
        except Exception:
            if stopped:
                run(['docker','stop','--time','40',backend])
                if renamed_new:
                    self.disconnect(self.database)
                    self.admin('ALTER DATABASE '+identifier(self.database)+' RENAME TO '+identifier(staged))
                    self.admin('ALTER DATABASE '+identifier(staged)+' ALLOW_CONNECTIONS true')
                if renamed_old:
                    self.admin('ALTER DATABASE '+identifier(archived)+' RENAME TO '+identifier(self.database))
                self.admin('ALTER DATABASE '+identifier(self.database)+' ALLOW_CONNECTIONS true')
                if renamed_new and redis_backup and redis_backup.is_file():
                    redis=self.container('redis')
                    run(['docker','stop','--time','30',redis])
                    run(['docker','cp',str(redis_backup),redis+':/data/dump.rdb'])
                    run(['docker','start',redis])
                state.update(status='staged')
                self.save(state_path,state)
                run(['docker','start',backend]);self.wait_backend(backend)
            raise


def main():
    import fcntl
    if os.geteuid()!=0:raise RuntimeError('Shared ECS database replacement requires root')
    root,operation,manifest_path=sys.argv[1:]
    replacement=Replacement(root)
    with (replacement.root/'.deploy.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        if operation=='backup':replacement.snapshot()
        elif operation in ('stage','activate'):
            manifest=json.loads(Path(manifest_path).read_text(encoding='utf-8-sig'))
            getattr(replacement,operation)(manifest)
        else:raise ValueError('Use backup, stage or activate')


if __name__=='__main__':
    try:main()
    except Exception as error:
        reason=str(error) if isinstance(error,(RuntimeError,ValueError)) else type(error).__name__
        print('ERP replacement stopped: '+reason,flush=True);sys.exit(2)
