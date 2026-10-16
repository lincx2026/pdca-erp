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
if [[ "$1" == inspect ]]; then
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
    def run_release(self,**flags):
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
        result=subprocess.run([BASH,"-c",'export PATH="$1:$PATH"; exec bash "$2" all',
            "--",posix(directory/"bin"),posix(directory/"deploy.sh")],env=env,
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

if __name__=="__main__": unittest.main()
