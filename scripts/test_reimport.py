from pathlib import Path
from unittest.mock import patch
import hashlib,io,json,tempfile,unittest
from types import SimpleNamespace
import ecs_reimport as migration


class ReplacementTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)
        (self.root/'.env').write_text('POSTGRES_DB=pcba_erp\nPOSTGRES_USER=pcba\nDB_HOST=127.0.0.1\n'
                                    'DB_PORT=5432\nDEPLOY_MODE=shared\nREDIS_PASSWORD=private-test-only\n')
        self.operation=migration.Replacement(self.root)
        counts={name:1 for name in ('sys_menu','sys_user','sys_role_menu','pdm_material')}
        self.manifest={'sha256':'a'*64,'key':'b'*64,'url':'https://bucket.oss-cn-test.aliyuncs.com/private',
                       'counts':counts,'fingerprints':{k:'1|'+'c'*32 for k in counts}}

    def stage_state(self):
        database,state=self.operation.validate_manifest(self.manifest)
        self.operation.save(state,{'status':'staged','database':database,'sha256':self.manifest['sha256']})
        return database,state

    def test_unrelated_or_remote_database_is_refused(self):
        for changed in ('POSTGRES_DB=other','DB_HOST=remote.example','DEPLOY_MODE=standalone'):
            key=changed.split('=')[0]
            text=(self.root/'.env').read_text()
            lines=[changed if line.startswith(key+'=') else line for line in text.splitlines()]
            (self.root/'.env').write_text('\n'.join(lines))
            with self.assertRaises(ValueError):migration.Replacement(self.root)
            (self.root/'.env').write_text(text)

    def test_identifiers_and_incomplete_verification_are_refused(self):
        for value in ('postgres; DROP DATABASE other','Other','../pcba'):
            with self.assertRaises(ValueError):migration.identifier(value)
        for change in ({'fingerprints':{}},{'counts':{'sys_menu':1}},
                       {'url':'http://bucket.aliyuncs.com/private'},
                       {'fingerprints':{k:'2|'+'c'*32 for k in self.manifest['counts']}}):
            with self.assertRaises(ValueError):self.operation.validate_manifest(dict(self.manifest,**change))

    def test_backup_failure_never_renames_database(self):
        self.stage_state();commands=[]
        with patch.object(self.operation,'verify'),patch.object(self.operation,'container',return_value='erp-only'),\
             patch.object(self.operation,'exists',return_value=False),\
             patch.object(self.operation,'snapshot',side_effect=RuntimeError('backup failed')),\
             patch.object(self.operation,'admin',side_effect=lambda q:commands.append(q)),\
             patch.object(self.operation,'wait_backend'),patch.object(migration,'run',return_value=''):
            with self.assertRaisesRegex(RuntimeError,'backup failed'):self.operation.activate(self.manifest)
        self.assertFalse(any('RENAME TO' in q for q in commands))

    def test_failed_health_check_restores_previous_database_and_cache(self):
        staged,state=self.stage_state();commands=[];calls=[]
        def execute(arguments,**kwargs):
            calls.append(arguments)
            if arguments[:2]==['docker','cp'] and arguments[-1].endswith('.rdb') and ':/' not in arguments[-1]:
                Path(arguments[-1]).write_bytes(b'old-cache')
            return ''
        with patch.object(self.operation,'verify'),patch.object(self.operation,'container',side_effect=lambda s:'erp-'+s),\
             patch.object(self.operation,'exists',return_value=False),patch.object(self.operation,'snapshot',return_value={'dump':'verified'}),\
             patch.object(self.operation,'redis',return_value='OK'),\
             patch.object(self.operation,'admin',side_effect=lambda q:commands.append(q)),\
             patch.object(self.operation,'wait_backend',side_effect=[RuntimeError('unhealthy'),None]),\
             patch.object(migration,'run',side_effect=execute):
            with self.assertRaisesRegex(RuntimeError,'unhealthy'):self.operation.activate(self.manifest)
        self.assertTrue(any('RENAME TO "'+staged+'"' in q for q in commands))
        self.assertTrue(any('RENAME TO "pcba_erp"' in q for q in commands))
        self.assertTrue(any(c[:2]==['docker','cp'] and c[-1]=='erp-redis:/data/dump.rdb' for c in calls))
        self.assertEqual('staged',json.loads(state.read_text())['status'])
        self.assertFalse(any('DROP DATABASE' in q for q in commands))

    def test_tampered_dump_cannot_create_staging_database(self):
        commands=[]
        def execute(arguments,**kwargs):
            if arguments[0]=='openssl':Path(arguments[arguments.index('-out')+1]).write_bytes(b'tampered')
            return ''
        with patch.object(self.operation,'exists',return_value=False),patch.object(self.operation,'snapshot',return_value={}),\
             patch.object(self.operation,'admin',side_effect=lambda q:commands.append(q)),\
             patch.object(migration,'urlopen',return_value=io.BytesIO(b'encrypted')),patch.object(migration,'run',side_effect=execute),\
             patch.object(migration.subprocess,'run',return_value=SimpleNamespace(stdout=b'',stderr=b'')):
            with self.assertRaisesRegex(RuntimeError,'checksum mismatch'):self.operation.stage(self.manifest)
        self.assertEqual([],commands)

    def test_existing_staging_database_is_never_overwritten(self):
        with patch.object(self.operation,'exists',return_value=True),patch.object(self.operation,'snapshot') as backup:
            with self.assertRaisesRegex(RuntimeError,'already exists'):self.operation.stage(self.manifest)
        backup.assert_not_called()


if __name__=='__main__':unittest.main()
