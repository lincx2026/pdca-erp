"""Exercise deploy.sh sequencing, failed builds and rollback without Docker mutations."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
BASH=os.environ.get("BASH_BIN") or ("D:/Program Files/Git/bin/bash.exe" if os.name=="nt" else "bash")

def posix(path):
    value=Path(path).as_posix()
    return "/"+value[0].lower()+value[2:] if os.name=="nt" else value

STUB=r'''#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$PCBA_TEST_LOG"
if [[ "$1" == run && "${PCBA_TEST_FAIL_BUILD:-0}" == 1 ]]; then exit 3; fi
if [[ "$1" == pull && "${PCBA_TEST_FAIL_PULL:-0}" == 1 ]]; then exit 4; fi
if [[ "$1" == image && "$2" == inspect ]]; then
  printf 'sha256:'; printf 'a%.0s' {1..64}; printf '\n'
elif [[ "$1" == inspect ]]; then
  if [[ "$*" == *Config.Image* ]]; then echo "pcba-erp/test:old"
  elif [[ "$*" == *State.Health* ]]; then
    if [[ -f "$PCBA_TEST_STATE" ]]; then echo exited; else echo healthy; fi
  else echo sha256:testold; fi
elif [[ "$1" == compose ]]; then
  if [[ "$*" == *'ps -q'* || "$*" == *'ps -aq'* ]]; then
    if [[ "${PCBA_TEST_PARTIAL:-0}" != 1 || "${@: -1}" != frontend ]]; then echo "test-${@: -1}"; fi
  elif [[ "$*" == *' up '* && "${@: -1}" == backend ]]; then
    if [[ "$*" == *previous.yml* ]]; then rm -f "$PCBA_TEST_STATE"
    elif [[ "${PCBA_TEST_FAIL_HEALTH:-0}" == 1 ]]; then touch "$PCBA_TEST_STATE"; fi
  fi
fi
'''

class DeploymentTests(unittest.TestCase):
    def run_release(self,scope="all",**flags):
        self.temp=tempfile.TemporaryDirectory(prefix="pcba-deploy-test-")
        self.addCleanup(self.temp.cleanup)
        directory=Path(self.temp.name)
        (directory/"bin").mkdir()
        shutil.copyfile(ROOT/"deploy.sh",directory/"deploy.sh")
        (directory/".env").write_text("TEST_ONLY=true\n",encoding="utf-8")
        stub=directory/"bin/docker"
        stub.write_text(STUB,encoding="utf-8",newline="\n"); stub.chmod(0o755)
        log=directory/"commands.log"
        if flags.get("PCBA_TEST_PARTIAL")=="1":
            (directory/".deploy-backups").mkdir()
            (directory/".deploy-backups/previous.yml").write_text("services: {}\n",encoding="utf-8")
        env={**os.environ,**flags,"PCBA_TEST_LOG":posix(log),"PCBA_TEST_STATE":posix(directory/"failed")}
        result=subprocess.run([BASH,"-c",'export PATH="$1:$PATH"; exec bash "$2" "$3"',
            "--",posix(directory/"bin"),posix(directory/"deploy.sh"),scope],env=env,
            stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=20)
        return result,log.read_text(encoding="utf-8"),directory

    def test_builds_finish_before_switching_containers(self):
        result,log,_=self.run_release()
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertLess(log.index("build frontend"),log.index("up -d postgres redis"))
        self.assertNotIn("mysql",log)

    def test_build_failure_does_not_switch_or_reuse_old_artifacts(self):
        result,log,_=self.run_release(PCBA_TEST_FAIL_BUILD="1")
        self.assertEqual(3,result.returncode,result.stdout)
        self.assertNotIn("up -d",log)

    def test_health_failure_restores_the_current_attempt_snapshot(self):
        result,log,directory=self.run_release(PCBA_TEST_FAIL_HEALTH="1")
        self.assertNotEqual(0,result.returncode)
        self.assertIn("previous.yml up -d --no-deps --force-recreate backend",log)
        self.assertIn("previous.yml up -d --no-deps --force-recreate frontend",log)
        snapshot=(directory/".deploy-backups/previous.yml").read_text(encoding="utf-8")
        self.assertIn("rollback-",snapshot)
        self.assertNotIn("POSTGRES_PASSWORD",snapshot)

    def test_partial_snapshot_does_not_restore_stale_previous_release(self):
        result,log,_=self.run_release(PCBA_TEST_FAIL_HEALTH="1",PCBA_TEST_PARTIAL="1")
        self.assertNotEqual(0,result.returncode)
        self.assertNotIn("previous.yml up",log)

    def test_ci_images_are_pulled_before_switching_and_persisted(self):
        backend="ghcr.io/test/erp-backend@sha256:"+"a"*64
        frontend="ghcr.io/test/erp-frontend@sha256:"+"b"*64
        result,log,directory=self.run_release(scope="images",BACKEND_IMAGE=backend,FRONTEND_IMAGE=frontend)
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertLess(log.index("pull "+frontend),log.index("up -d postgres redis"))
        self.assertNotIn("build backend",log)
        self.assertNotIn("run --rm",log)
        current=(directory/".deploy-backups/current.env").read_text()
        self.assertIn(backend,current)
        self.assertIn(frontend,current)

    def test_failed_image_pull_does_not_change_containers(self):
        image="ghcr.io/test/erp@sha256:"+"a"*64
        result,log,_=self.run_release(scope="images",BACKEND_IMAGE=image,FRONTEND_IMAGE=image,PCBA_TEST_FAIL_PULL="1")
        self.assertEqual(4,result.returncode,result.stdout)
        self.assertNotIn("up -d",log)

    def test_ci_images_reject_mutable_tags(self):
        result,log,_=self.run_release(scope="images",BACKEND_IMAGE="ghcr.io/test/erp:latest",FRONTEND_IMAGE="ghcr.io/test/erp:latest")
        self.assertEqual(2,result.returncode,result.stdout)
        self.assertNotIn("pull ",log)

    def test_preloaded_images_are_verified_without_registry_requests(self):
        revision='c'*40;identity='sha256:'+'a'*64
        result,log,directory=self.run_release(scope='preloaded',DEPLOY_MODE='shared',
            BACKEND_IMAGE='pcba-erp/backend:ci-'+revision,FRONTEND_IMAGE='pcba-erp/frontend:ci-'+revision,
            BACKEND_IMAGE_ID=identity,FRONTEND_IMAGE_ID=identity,
            ERP_REDIS_IMAGE='pcba-erp/redis:ci-'+revision,ERP_REDIS_IMAGE_ID=identity)
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertNotIn('pull ',log)
        self.assertLess(log.index('image inspect'),log.index('up -d redis'))
        self.assertIn('ERP_REDIS_IMAGE=pcba-erp/redis:ci-',(directory/'.deploy-backups/current.env').read_text())

    def test_preloaded_image_mismatch_cannot_switch_services(self):
        revision='c'*40;identity='sha256:'+'b'*64
        result,log,_=self.run_release(scope='preloaded',
            BACKEND_IMAGE='pcba-erp/backend:ci-'+revision,FRONTEND_IMAGE='pcba-erp/frontend:ci-'+revision,
            BACKEND_IMAGE_ID=identity,FRONTEND_IMAGE_ID=identity)
        self.assertNotEqual(0,result.returncode)
        self.assertNotIn('up -d',log)

    def run_ecs_release(self,**flags):
        _,_,directory=self.run_release(scope="status")
        (directory/".git").mkdir()
        log=directory/"commands.log";log.write_text("")
        certificates=directory/"docker/nginx/ssl";certificates.mkdir(parents=True)
        for name in ("fullchain.pem","private.key"):(certificates/name).write_text("test-only")
        shutil.copyfile(ROOT/"scripts/ecs_release.sh",directory/"ecs_release.sh")
        git=directory/"bin/git"
        git.write_text('''#!/usr/bin/env bash
set -eu
printf 'git %s\\n' "$*" >> "$PCBA_TEST_LOG"
if [[ "$1" == diff && "${PCBA_TEST_DIRTY:-0}" == 1 ]]; then exit 1; fi
if [[ "$1" == rev-parse ]]; then
  if [[ "$2" == HEAD || "${PCBA_TEST_STALE:-0}" == 1 ]]; then printf 'a%.0s' {1..40}
  else printf 'b%.0s' {1..40}; fi
  printf '\\n'
fi
''',encoding="utf-8",newline="\n");git.chmod(0o755)
        image="ghcr.io/test/erp@sha256:"+"c"*64
        env={**os.environ,**flags,"PCBA_TEST_LOG":posix(log),"PCBA_TEST_STATE":posix(directory/"failed")}
        result=subprocess.run([BASH,"-c",'export PATH="$1:$PATH"; exec bash "$2" "$3" "$4" test/erp "$5" "$5"',
            "--",posix(directory/"bin"),posix(directory/"ecs_release.sh"),posix(directory),"b"*40,image],
            env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=20)
        return result,log.read_text(),directory

    @unittest.skipIf(os.name=="nt","ECS script targets Linux and requires flock")
    def test_ecs_release_records_successful_revision(self):
        result,_,directory=self.run_ecs_release()
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertEqual("b"*40,(directory/".deploy-backups/revision").read_text().strip())

    @unittest.skipIf(os.name=="nt","ECS script targets Linux and requires flock")
    def test_ecs_release_restores_source_after_failed_health_check(self):
        result,log,_=self.run_ecs_release(PCBA_TEST_FAIL_HEALTH="1")
        self.assertNotEqual(0,result.returncode)
        self.assertIn("git checkout --detach "+"a"*40,log)
        self.assertIn("--no-build --no-deps --force-recreate backend frontend",log)

    @unittest.skipIf(os.name=="nt","ECS script targets Linux and requires flock")
    def test_ecs_release_rejects_dirty_checkout(self):
        result,log,_=self.run_ecs_release(PCBA_TEST_DIRTY="1")
        self.assertEqual(2,result.returncode,result.stdout)
        self.assertNotIn("git checkout",log)

    @unittest.skipIf(os.name=="nt","ECS script targets Linux and requires flock")
    def test_ecs_release_rejects_stale_commit(self):
        result,log,_=self.run_ecs_release(PCBA_TEST_STALE="1")
        self.assertEqual(2,result.returncode,result.stdout)
        self.assertNotIn("git checkout",log)

    def test_shared_mode_does_not_start_postgres(self):
        image="ghcr.io/test/erp@sha256:"+"a"*64
        result,log,_=self.run_release(scope="images",DEPLOY_MODE="shared",BACKEND_IMAGE=image,FRONTEND_IMAGE=image)
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertIn("docker-compose.shared.yml",log)
        self.assertIn("up -d redis",log)
        self.assertNotIn("up -d postgres",log)

if __name__=="__main__": unittest.main()
