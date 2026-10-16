"""One-time restore of a verified encrypted dump into an empty ERP database only."""
from pathlib import Path
import hashlib,json,os,re,subprocess,sys,tempfile
from urllib.request import urlopen
from urllib.parse import urlparse
from ecs_db import connection

def restore(environment_path,manifest_path):
    manifest=json.loads(Path(manifest_path).read_text(encoding='utf-8-sig'))
    environment=connection(environment_path)
    root=Path(environment_path).resolve().parent
    marker=root/'.deploy-backups/initial-data-import.sha256'
    expected=manifest['sha256']
    if not re.fullmatch(r'[a-f0-9]{64}',expected):raise ValueError('Invalid dump checksum')
    if marker.is_file() and marker.read_text().strip()==expected:
        print('Verified historical data was already imported; skipping restore.');return
    def query(sql):
        result=subprocess.run(['psql','-X','-w','-At','-v','ON_ERROR_STOP=1','-c',sql],env=environment,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
        if result.returncode:raise RuntimeError('Data import database check failed')
        return result.stdout.strip()
    count=query("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'")
    if count!='0':raise RuntimeError('Refusing to restore over a nonempty ERP database')
    url=manifest['url'];parsed=urlparse(url)
    if parsed.scheme!='https' or not (parsed.hostname or '').endswith('.aliyuncs.com'):
        raise ValueError('Encrypted dump must use an HTTPS OSS URL')
    if not re.fullmatch(r'[a-f0-9]{64}',manifest['key']):raise ValueError('Invalid dump encryption key')
    with tempfile.TemporaryDirectory(prefix='import-',dir=str(root/'.deploy-backups')) as temporary:
        encrypted=Path(temporary)/'dump.enc';dump=Path(temporary)/'dump.pg'
        os.chmod(temporary,0o700)
        with urlopen(url,timeout=60) as source,encrypted.open('wb') as output:
            total=0
            while True:
                block=source.read(1024*1024)
                if not block:break
                total+=len(block)
                if total>128*1024*1024:raise ValueError('Initial import exceeds 128 MiB limit')
                output.write(block)
        decrypt_environment=dict(os.environ,PCBA_IMPORT_PASSWORD=manifest['key'])
        decrypted=subprocess.run(['openssl','enc','-d','-aes-256-cbc','-pbkdf2','-iter','200000',
            '-pass','env:PCBA_IMPORT_PASSWORD','-in',str(encrypted),'-out',str(dump)],env=decrypt_environment,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        if decrypted.returncode or hashlib.sha256(dump.read_bytes()).hexdigest()!=expected:
            raise RuntimeError('Historical dump decryption or SHA-256 verification failed')
        restored=subprocess.run(['pg_restore','-w','--single-transaction','--exit-on-error','--no-owner',
            '--no-privileges','--dbname',environment['PGDATABASE'],str(dump)],env=environment,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        if restored.returncode:raise RuntimeError('Historical restore failed; transaction rolled back')
    counts=manifest['counts']
    for table,rows in counts.items():
        if not re.fullmatch(r'[a-z_][a-z0-9_]*',table):raise ValueError('Invalid verification table')
        if query('SELECT count(*) FROM public."'+table+'"')!=str(rows):
            raise RuntimeError('Historical row-count verification failed for '+table)
    marker.write_text(expected+'\n');os.chmod(str(marker),0o600)
    print('Historical data restored and verified: '+str(len(counts))+' tables, '+str(sum(counts.values()))+' rows.')

if __name__=='__main__':
    try:restore(sys.argv[1],sys.argv[2])
    except Exception as error:
        # URL exceptions may contain the private signed URL; keep diagnostics generic.
        reason=str(error) if isinstance(error,(RuntimeError,ValueError)) else type(error).__name__
        print('Historical import stopped: '+reason+'. No overwrite or clean was requested.')
        sys.exit(2)
