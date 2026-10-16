"""Check production database guards and Nginx restoration without external changes."""
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import tempfile
import unittest
import json
import os
import shutil
import subprocess
import ecs_db
import ecs_site
import ecs_import
import io
import hashlib

class EcsTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.env=self.root/'runtime.env'
        self.env.write_text('ERP_DOMAIN=erp.example.com\nPOSTGRES_PASSWORD=test-private\n'
            'ERP_TLS_CERT='+str(self.root/'cert')+'\nERP_TLS_KEY='+str(self.root/'key')+'\n')
        for name in ('cert','key'):(self.root/name).write_text('test-only')

    def result(self,output='',code=0,error=''):
        return SimpleNamespace(returncode=code,stdout=output,stderr=error)

    def test_existing_unrelated_database_cannot_be_migrated(self):
        with patch.object(ecs_db.subprocess,'run',return_value=self.result('other|10|f|f\n')):
            self.assertFalse(ecs_db.check(self.env))
            self.assertTrue(ecs_db.check(self.env,inspect=True))

    def test_empty_or_known_erp_database_is_accepted(self):
        for row in ('erp|0|f|f\n','erp|100|t|t\n'):
            with patch.object(ecs_db.subprocess,'run',return_value=self.result(row)):
                self.assertTrue(ecs_db.check(self.env))

    def test_password_is_passed_only_in_process_environment(self):
        with patch.object(ecs_db.subprocess,'run',return_value=self.result(code=1,error='password authentication failed')) as run:
            self.assertFalse(ecs_db.check(self.env))
            self.assertNotIn('test-private',str(run.call_args.args))
            self.assertEqual('test-private',run.call_args.kwargs['env']['PGPASSWORD'])

    def test_failed_backup_removes_partial_dump(self):
        target=self.root/'backup.dump'
        with patch.object(ecs_db.subprocess,'run',return_value=self.result(code=1)):
            self.assertFalse(ecs_db.backup(self.env,str(target)))
        self.assertFalse(target.exists())

    def import_manifest(self,checksum=None):
        (self.root/'.deploy-backups').mkdir(exist_ok=True)
        manifest=self.root/'import.json'
        manifest.write_text(json.dumps({'url':'https://private.oss-cn-hangzhou.aliyuncs.com/private',
            'key':'a'*64,'sha256':checksum or hashlib.sha256(b'verified dump').hexdigest(),'counts':{'sys_user':1}}))
        return manifest

    def test_historical_import_refuses_existing_data_before_download(self):
        with patch.object(ecs_import.subprocess,'run',return_value=self.result('1')),patch.object(ecs_import,'urlopen') as download:
            with self.assertRaisesRegex(RuntimeError,'nonempty'):ecs_import.restore(self.env,self.import_manifest())
        download.assert_not_called()

    def test_historical_import_verifies_checksum_before_atomic_restore(self):
        for valid in (True,False):
            calls=[]
            def run(arguments,**kwargs):
                calls.append(arguments)
                if arguments[0]=='psql':return self.result('0' if 'information_schema' in arguments[-1] else '1')
                if arguments[0]=='openssl':Path(arguments[-1]).write_bytes(b'verified dump')
                return self.result()
            manifest=self.import_manifest(None if valid else 'b'*64)
            marker=self.root/'.deploy-backups/initial-data-import.sha256'
            if marker.exists():marker.unlink()
            with patch.object(ecs_import.subprocess,'run',side_effect=run),patch.object(ecs_import,'urlopen',return_value=io.BytesIO(b'encrypted')):
                if valid:ecs_import.restore(self.env,manifest)
                else:
                    with self.assertRaisesRegex(RuntimeError,'SHA-256'):ecs_import.restore(self.env,manifest)
            restores=[c for c in calls if c[0]=='pg_restore']
            self.assertEqual(1 if valid else 0,len(restores))
            if valid:
                self.assertIn('--single-transaction',restores[0])
                self.assertNotIn('--clean',restores[0])

    @unittest.skipIf(os.name=='nt','Local PostgreSQL setup targets Linux')
    def test_scoped_authentication_preserves_existing_rules_and_restores_on_failure(self):
        for successful in (True,False):
            target=self.root/'pg_hba.conf';previous=b'host all all 127.0.0.1/32 ident\n'
            target.write_bytes(previous)
            def run(arguments,**kwargs):
                query=arguments[-1]
                if arguments[0]=='psql':return self.result(code=1,error='Ident authentication failed')
                if query=='SHOW hba_file':return self.result(str(target))
                if 'pg_authid' in query:return self.result('scram-sha-256')
                return self.result('t')
            with patch.object(ecs_db.subprocess,'run',side_effect=run),patch.object(ecs_db.os,'geteuid',return_value=0),\
                patch.object(ecs_db.time,'sleep'),patch.object(ecs_db,'check',return_value=successful):
                self.assertEqual(successful,ecs_db.prepare_local(self.env))
            content=target.read_bytes()
            if successful:
                self.assertTrue(content.endswith(previous))
                self.assertIn(b'host "pcba_erp" "pcba" 127.0.0.1/32 scram-sha-256',content)
            else:self.assertEqual(previous,content)

    def nginx_command(self,arguments):
        if '-checkhost' in arguments:return self.result('Hostname erp.example.com does match certificate')
        if arguments[0]=='curl':return self.result('{"status":"UP"}')
        return self.result()

    @unittest.skipIf(__import__('os').name=='nt','Production certificate paths target Linux')
    def test_unmanaged_nginx_file_is_preserved(self):
        target=self.root/'nginx.conf';target.write_bytes(b'existing unrelated config')
        with patch.object(ecs_site,'command',side_effect=self.nginx_command):
            with self.assertRaisesRegex(ValueError,'unmanaged'):ecs_site.apply(self.env,target)
        self.assertEqual(b'existing unrelated config',target.read_bytes())

    @unittest.skipIf(__import__('os').name=='nt','Production certificate paths target Linux')
    def test_readiness_failure_restores_previous_virtual_host(self):
        target=self.root/'nginx.conf';previous=ecs_site.MARKER.encode()+b'previous config'
        target.write_bytes(previous)
        def command(arguments):
            return self.result(code=7) if arguments[0]=='curl' else self.nginx_command(arguments)
        with patch.object(ecs_site,'command',side_effect=command):
            with self.assertRaises(RuntimeError):ecs_site.apply(self.env,target)
        self.assertEqual(previous,target.read_bytes())

    @unittest.skipIf(__import__('os').name=='nt','Production certificate paths target Linux')
    def test_successful_virtual_host_uses_existing_certificate(self):
        target=self.root/'nginx.conf'
        with patch.object(ecs_site,'command',side_effect=self.nginx_command):ecs_site.apply(self.env,target)
        self.assertIn('proxy_pass http://127.0.0.1:18080',target.read_text())
        self.assertEqual('test-only',(self.root/'key').read_text())

    @unittest.skipIf(os.name=='nt','Automatic TLS targets Linux')
    def test_certificate_failure_restores_previous_erp_virtual_host(self):
        with self.env.open('a') as output:
            output.write('ERP_TLS_AUTO=true\nERP_TLS_CERT=/etc/letsencrypt/live/erp.example.com/fullchain.pem\n'
                'ERP_TLS_KEY=/etc/letsencrypt/live/erp.example.com/privkey.pem\n')
        target=self.root/'nginx.conf';previous=ecs_site.MARKER.encode()+b'old configuration'
        target.write_bytes(previous)
        def command(arguments):
            return self.result(code=1) if arguments[0]=='certbot' else self.nginx_command(arguments)
        with patch.object(ecs_site,'command',side_effect=command):
            with self.assertRaisesRegex(RuntimeError,'issuance failed'):ecs_site.prepare_certificate(self.env,target)
        self.assertEqual(previous,target.read_bytes())

    @unittest.skipIf(os.name=='nt' or not shutil.which('docker'),'Compose validation runs on Linux CI')
    def test_shared_compose_does_not_publish_existing_service_ports(self):
        root=Path(__file__).resolve().parents[1]
        environment=dict(os.environ,POSTGRES_PASSWORD='test-only',REDIS_PASSWORD='test-only',
            JWT_SECRET='test-only',BOOTSTRAP_ADMIN_PASSWORD='test-only')
        result=subprocess.run(['docker','compose','--env-file','/dev/null','-f',str(root/'docker/docker-compose.yml'),
            '-f',str(root/'docker/docker-compose.shared.yml'),'config','--format','json'],env=environment,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
        self.assertEqual(0,result.returncode,result.stderr)
        services=json.loads(result.stdout)['services']
        self.assertNotIn('postgres',services)
        self.assertEqual('16379',services['redis']['ports'][0]['published'])
        for name in ('backend','frontend'):
            self.assertEqual('host',services[name]['network_mode'])
            self.assertFalse(services[name].get('ports'))
        self.assertFalse(services['backend'].get('depends_on'))
        self.assertEqual('127.0.0.1',services['backend']['environment']['SERVER_ADDRESS'])
        self.assertEqual(1,len(services['frontend']['volumes']))

if __name__=='__main__':unittest.main()
