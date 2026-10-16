"""Stage an encrypted verified PostgreSQL backup privately for one-time ECS import.

Requires oss2 from migration-requirements.txt, OpenSSL and authenticated gh CLI.
Secrets are sent to GitHub through stdin and never printed.
"""
from pathlib import Path
import argparse,hashlib,json,os,secrets,subprocess,tempfile,uuid
from ecs_db import settings

def main():
    import oss2
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file',default='.env')
    parser.add_argument('--dump')
    parser.add_argument('--verification')
    parser.add_argument('--checksum')
    parser.add_argument('--openssl',default='openssl')
    parser.add_argument('--state',required=True)
    parser.add_argument('--delete',action='store_true')
    args=parser.parse_args()
    config=settings(args.env_file)
    auth=oss2.StsAuth(config['OSS_ACCESS_KEY_ID'],config['OSS_ACCESS_KEY_SECRET'],config['OSS_SECURITY_TOKEN']) if config.get('OSS_SECURITY_TOKEN') else oss2.Auth(config['OSS_ACCESS_KEY_ID'],config['OSS_ACCESS_KEY_SECRET'])
    bucket=oss2.Bucket(auth,config['OSS_ENDPOINT'],config['OSS_BUCKET'])
    if args.delete:
        state=json.loads(Path(args.state).read_text())
        if not state['object'].startswith('_deployment-transfer/'):raise ValueError('Unsafe transfer object')
        bucket.delete_object(state['object'])
        subprocess.run(['gh','secret','delete','ERP_DATA_IMPORT','--repo','lincx2026/pdca-erp','--env','production'],check=True)
        print('Temporary encrypted OSS object and GitHub import secret removed.');return
    content=Path(args.dump).read_bytes();checksum=hashlib.sha256(content).hexdigest()
    if checksum!=Path(args.checksum).read_text().split()[0]:raise ValueError('Backup checksum mismatch')
    verified=json.loads(Path(args.verification).read_text())
    if not verified or not all(value['matches'] for value in verified.values()):raise ValueError('Backup verification report contains mismatches')
    key=secrets.token_hex(32)
    with tempfile.TemporaryDirectory(prefix='pcba-encrypted-transfer-') as temporary:
        encrypted=Path(temporary)/'backup.enc'
        help_result=subprocess.run([args.openssl,'enc','-help'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        salt_arguments=['-saltlen','8'] if b'-saltlen' in help_result.stdout+help_result.stderr else []
        subprocess.run([args.openssl,'enc','-aes-256-cbc','-pbkdf2','-iter','200000','-salt']+salt_arguments+[
            '-pass','env:PCBA_IMPORT_PASSWORD','-in',str(Path(args.dump).resolve()),'-out',str(encrypted)],
            env=dict(os.environ,PCBA_IMPORT_PASSWORD=key),check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        object_key='_deployment-transfer/'+uuid.uuid4().hex+'.pg.enc'
        bucket.put_object(object_key,encrypted.read_bytes(),headers={'x-oss-object-acl':'private','Content-Type':'application/octet-stream'})
    Path(args.state).write_text(json.dumps({'object':object_key,'sha256':checksum}),encoding='utf-8')
    manifest={'url':bucket.sign_url('GET',object_key,4*3600),'key':key,'sha256':checksum,'salt_length':8,
        'counts':{table:value['rows'] for table,value in verified.items()}}
    subprocess.run(['gh','secret','set','ERP_DATA_IMPORT','--repo','lincx2026/pdca-erp','--env','production'],
        input=json.dumps(manifest),universal_newlines=True,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    print('Verified backup encrypted and staged privately; one-time import secret configured.')

if __name__=='__main__':
    try:main()
    except Exception as error:
        print('Encrypted staging failed ('+type(error).__name__+').');raise SystemExit(2)
