"""Runner-only tests with a disposable Python server; these are not game tests."""
import argparse
import contextlib
import io
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

import runner

FAKE_SERVER = r'''
import json,pathlib,sys,time,uuid,datetime
root=pathlib.Path.cwd()
if not (root/'not-ready').exists(): print('Done (0.01s)! For help, type help',flush=True)
for line in sys.stdin:
 command=line.strip()
 request_id=''
 if ' --request ' in command: command,request_id=command.rsplit(' --request ',1)
 if command=='stop':
  if (root/'ignore-stop').exists(): continue
  print('Stopping server',flush=True)
  if (root/'fail-stop').exists(): sys.exit(7)
  break
 if command.startswith('gtitlab'):
  if command.endswith('REJECTED'):
   print('[GTIT-LAB] REJECT request='+request_id+' reason=Laboratory busy',flush=True);continue
  status=command.split()[-1] if command.split()[-1] in ('PASS','FAIL','SKIP') else 'PASS'
  report_dir=root/'logs'/'gtit-testlab';report_dir.mkdir(parents=True,exist_ok=True)
  rid=str(uuid.uuid4()); report=report_dir/(rid+'.json')
  if command.endswith('OLD'):
   report=next(report_dir.glob('*.json'));rid=report.stem
  else:
   scenario=command.split()[-1] if command.split()[1]=='run' else 'fake-scenario'
   mode=command.split()[1];mode='suite' if mode=='refresh' else mode
   now=datetime.datetime.now(datetime.timezone.utc).isoformat()
   request_id=str(uuid.uuid4()) if command.endswith('RACE') else request_id
   report.write_text(json.dumps({'schema':1,'runId':rid,'mode':mode,'status':status,'startedAt':now,'finishedAt':now,'environment':{'requestId':request_id,'selectedScenarios':[scenario],'cleanupComplete':True,'ownedBlocksRemaining':0},'metrics':{'passed':int(status=='PASS'),'failed':int(status=='FAIL'),'skipped':int(status=='SKIP')},'cases':[{'scenario':scenario,'check':'fake-check','status':status,'detail':'runner-only fake report'}]}),encoding='utf-8')
  if command.endswith('MISMATCH'): report.write_text(json.dumps({'runId':'different','status':status}))
  if command.endswith('UNRELATED'): print('[GTIT-LAB] COMPLETE run='+str(uuid.uuid4())+' status=PASS report='+str(report),flush=True)
  if not command.endswith('NOSTART'): print('[GTIT-LAB] START run='+rid+' mode='+command.split()[1]+' tasks=1 request='+request_id,flush=True)
  print('[GTIT-LAB] COMPLETE run='+rid+' status='+status+' report='+str(report),flush=True)
'''

BOOTSTRAP = r'''
import sys,subprocess
from unittest.mock import patch
sys.path.insert(0,sys.argv[1]);import runner
real_popen=subprocess.Popen
fake=sys.argv[2];sys.argv=[str(runner.__file__),'run',*sys.argv[3:]]
def launch(command,**kwargs): return real_popen([sys.executable,'-u',fake],**kwargs)
with patch.object(runner.subprocess,'Popen',side_effect=launch): raise SystemExit(runner.main())
'''


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.fake = self.root / 'fake.py'
        self.fake.write_text(FAKE_SERVER)
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            self.port = sock.getsockname()[1]
        (self.root / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=' + str(self.port) + '\nenable-rcon=false\nenable-query=false\n')
        (self.root / 'testlab-instance.json').write_text('{}')
        (self.root / 'eula.txt').write_text('eula=true\n')
        self.children = []
        self.outputs = []

    def tearDown(self):
        for child in self.children:
            if child.poll() is None:
                try:
                    runner.request(self.root, 'stop', 2)
                    child.wait(timeout=4)
                except Exception:
                    child.terminate()
                    child.wait(timeout=5)
        for stream in self.outputs:
            stream.close()
        self.temp.cleanup()

    def spawn(self, ready=True, startup=3, runtime=30):
        output = (self.root / ('out-' + str(len(self.children)))).open('w')
        self.outputs.append(output)
        child = subprocess.Popen([sys.executable, '-u', '-c', BOOTSTRAP, str(Path(runner.__file__).parent),
                                  str(self.fake), '--instance', str(self.root), '--startup-timeout', str(startup),
                                  '--max-runtime', str(runtime), '--stop-timeout', '0.4'],
                                 stdout=output, stderr=subprocess.STDOUT,
                                 creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        self.children.append(child)
        if ready:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                lock = self.root / '.testlab' / 'owner.json'
                if lock.exists() and runner.read_json(lock).get('phase') == 'ready':
                    return child
                if child.poll() is not None:
                    last = self.root / '.testlab' / 'last-run.json'
                    self.fail('Supervisor exited early: ' + (last.read_text() if last.exists() else (self.root / ('out-' + str(len(self.children)-1))).read_text()))
                time.sleep(.05)
            self.fail('Supervisor readiness timeout')
        return child

    def probe(self, suffix='PASS'):
        args = argparse.Namespace(instance=self.root, command=['gtitlab run ' + suffix], timeout=3)
        with contextlib.redirect_stdout(io.StringIO()):
            return runner.probe(args)

    def test_pid_creation_identity(self):
        own = runner.identity(os.getpid())
        self.assertTrue(runner.alive(own))
        self.assertFalse(runner.alive(own | {'birth': str(own['birth']) + '-reused'}))

    def test_lock_metadata_publication_retry(self):
        lock = self.root / 'partial-lock.json'
        lock.write_text('{')
        def publish():
            time.sleep(.05)
            runner.write_json(lock, {'token': 'published-owner'})
        writer = threading.Thread(target=publish);writer.start()
        self.assertEqual(runner.read_json(lock), {'token': 'published-owner'})
        writer.join()

    def test_windows_metadata_replace_retry(self):
        target = self.root / 'metadata.json';target.write_text('{}')
        replace = Path.replace
        attempts = []
        def temporarily_locked(path, destination):
            attempts.append(1)
            if len(attempts) == 1:
                raise PermissionError('simulated Windows reader holding destination')
            return replace(path, destination)
        with patch.object(Path, 'replace', temporarily_locked):
            runner.write_json(target, {'published': True})
        self.assertEqual(len(attempts), 2)
        self.assertTrue(runner.read_json(target)['published'])

    def test_report_rejects_nonobject_json(self):
        target = self.root / 'invalid-report.json';target.write_text('[]')
        with self.assertRaisesRegex(ValueError, 'JSON object'):
            runner.read_json(target)

    def test_recover_reused_pid_does_not_kill_process(self):
        base = self.root / '.testlab';base.mkdir()
        own = runner.identity(os.getpid())
        runner.write_json(base / 'owner.json', {'supervisor': own | {'birth': 'old-birth'}, 'java': None})
        with patch.object(sys, 'argv', ['runner.py', 'recover-lock', '--instance', str(self.root)]):
            self.assertEqual(runner.main(), 0)
        self.assertTrue(runner.alive(own))
        self.assertFalse((base / 'owner.json').exists())

    def test_recover_refuses_live_owner(self):
        base = self.root / '.testlab';base.mkdir()
        runner.write_json(base / 'owner.json', {'supervisor': runner.identity(os.getpid()), 'java': None})
        with patch.object(sys, 'argv', ['runner.py', 'recover-lock', '--instance', str(self.root)]), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(runner.main(), runner.EXIT_BUSY)
        self.assertTrue((base / 'owner.json').exists())

    def test_refuses_unowned_directory(self):
        (self.root / 'testlab-instance.json').unlink()
        with self.assertRaises(ValueError): runner.validate(self.root)

    def test_refuses_unaccepted_eula_without_writing_it(self):
        (self.root / 'eula.txt').write_text('eula=false\n')
        with self.assertRaises(ValueError): runner.validate(self.root)
        self.assertEqual((self.root / 'eula.txt').read_text(), 'eula=false\n')

    def test_refuses_public_bind(self):
        props = self.root / 'server.properties'
        props.write_text(props.read_text().replace('127.0.0.1', '0.0.0.0'))
        with self.assertRaises(ValueError): runner.validate(self.root)

    def test_refuses_occupied_port(self):
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', self.port));sock.listen()
            with self.assertRaises(OSError): runner.validate(self.root)

    def test_stdin_stop_and_reports(self):
        child = self.spawn()
        self.assertTrue(runner.request(self.root, 'say runner-test', 3)['ok'])
        runner.request(self.root, 'stop', 3)
        self.assertEqual(child.wait(timeout=5), 0)
        self.assertFalse((self.root / '.testlab' / 'owner.json').exists())
        report = runner.read_json(self.root / '.testlab' / 'last-run.json')
        self.assertEqual(report['status'], 'passed')
        self.assertEqual([c['command'] for c in report['commands']], ['say runner-test', 'stop'])
        self.assertIn('Stopping server', (Path(report['run']) / 'console.log').read_text())

    def test_atomic_lock_competition(self):
        first = self.spawn()
        second = self.spawn(ready=False)
        self.assertEqual(second.wait(timeout=5), runner.EXIT_BUSY)
        self.assertIsNone(first.poll())

    def test_timeout_normal_stop(self):
        (self.root / 'not-ready').touch()
        child = self.spawn(ready=False, startup=.3)
        self.assertEqual(child.wait(timeout=5), runner.EXIT_TIMEOUT)
        report = runner.read_json(self.root / '.testlab' / 'last-run.json')
        self.assertEqual(report['java_exit_code'], 0)
        self.assertNotIn('forced_termination', report)

    def test_forced_timeout_only_child(self):
        (self.root / 'ignore-stop').touch()
        child = self.spawn(runtime=.5)
        own = runner.identity(os.getpid())
        self.assertEqual(child.wait(timeout=5), runner.EXIT_TIMEOUT)
        self.assertTrue(runner.alive(own))
        self.assertTrue(runner.read_json(self.root / '.testlab' / 'last-run.json')['forced_termination'])

    def test_probe_pass_fail_skip(self):
        self.spawn()
        self.assertEqual(self.probe('PASS'), 0)
        self.assertEqual(self.probe('FAIL'), runner.EXIT_COMMAND)
        self.assertEqual(self.probe('SKIP'), runner.EXIT_COMMAND)

    def test_probe_requires_start_and_ignores_unrelated_completion(self):
        self.spawn()
        self.assertEqual(self.probe('UNRELATED'), 0)
        with self.assertRaises(TimeoutError):
            runner.probe(argparse.Namespace(instance=self.root, command=['gtitlab run NOSTART'], timeout=.4))

    def test_probe_rejects_delayed_old_start_even_with_matching_mode_and_scenario(self):
        # RACE simulates a previously enqueued server task whose START arrives after
        # this probe sent its command, while the new command is rejected as busy.
        self.spawn()
        with self.assertRaises(TimeoutError):
            runner.probe(argparse.Namespace(instance=self.root, command=['gtitlab run RACE'], timeout=.4))

    def test_probe_surfaces_nonce_correlated_rejection(self):
        self.spawn()
        with self.assertRaisesRegex(RuntimeError, 'Laboratory busy'):
            self.probe('REJECTED')

    def test_probe_require_scenario(self):
        self.spawn()
        with self.assertRaisesRegex(ValueError, 'Required scenarios missing'):
            runner.probe(argparse.Namespace(instance=self.root, command=['gtitlab suite'], timeout=3, require_scenario=['missing-native-scene']))

    def test_stop_propagates_server_failure(self):
        (self.root / 'fail-stop').touch()
        self.spawn()
        with patch.object(sys, 'argv', ['runner.py', 'stop', '--instance', str(self.root)]), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(runner.main(), runner.EXIT_START)

    def test_report_coverage_cleanup_and_metrics(self):
        now = runner.stamp()
        report = {'schema': 1, 'mode': 'suite', 'status': 'PASS', 'startedAt': now, 'finishedAt': now,
                  'environment': {'selectedScenarios': ['native-a'], 'cleanupComplete': True, 'ownedBlocksRemaining': 0},
                  'metrics': {'passed': 1, 'failed': 0, 'skipped': 0},
                  'cases': [{'scenario': 'native-a', 'check': 'placement', 'status': 'PASS', 'detail': 'Native operation checked'}]}
        self.assertEqual(runner.validate_report(report, 'gtitlab suite', ['native-a']), ['native-a'])
        self.assertEqual(runner.validate_report(report, 'gtitlab suite', [], ['native-a/placement']), ['native-a'])
        with self.assertRaisesRegex(ValueError, 'Required PASS checks missing'):
            runner.validate_report(report, 'gtitlab suite', [], ['native-a/missing-check'])
        with self.assertRaisesRegex(ValueError, 'requested scenario'):
            runner.validate_report(report | {'mode': 'run'}, 'gtitlab run native-b', [])
        for changes, message in [({'cleanupComplete': False}, 'cleanup'), ({'ownedBlocksRemaining': 1}, 'cleanup')]:
            with self.assertRaisesRegex(ValueError, message):
                runner.validate_report(report | {'environment': report['environment'] | changes}, 'gtitlab suite', [])
        skipped = report | {'metrics': {'passed': 1, 'failed': 0, 'skipped': 1}, 'cases': [report['cases'][0] | {'scenario': 'harness'}, report['cases'][0] | {'status': 'SKIP'}]}
        with self.assertRaisesRegex(ValueError, 'no concrete'):
            runner.validate_report(skipped, 'gtitlab suite', [])
        with self.assertRaisesRegex(ValueError, 'metrics disagree'):
            runner.validate_report(report | {'metrics': report['metrics'] | {'failed': 1}}, 'gtitlab suite', [])

    def test_probe_rejects_old_report(self):
        self.spawn()
        self.assertEqual(self.probe('PASS'), 0)
        with self.assertRaisesRegex(ValueError, 'existed before'): self.probe('OLD')

    def test_probe_rejects_mismatched_runid(self):
        self.spawn()
        with self.assertRaisesRegex(ValueError, 'disagree'): self.probe('MISMATCH')

    def test_probe_channel_lock(self):
        self.spawn()
        lock = self.root / '.testlab' / 'probe.lock'
        runner.write_json(lock, {'token': 'another-probe', 'client': runner.identity(os.getpid())})
        with self.assertRaises(FileExistsError): self.probe()
        with self.assertRaisesRegex(RuntimeError, 'Another probe'): runner.request(self.root, 'gtitlab suite', 3)
        lock.unlink()

    def test_request_rejects_owner_generation_change_without_enqueuing(self):
        self.spawn()
        owner = runner.read_json(self.root / '.testlab' / 'owner.json')
        for command in ('stop', 'gtitlab suite'):
            with self.assertRaisesRegex(RuntimeError, 'owner changed'):
                runner.request(self.root, command, 1, expected_owner_token='previous-generation')
        self.assertEqual(list((self.root / '.testlab' / 'queue').glob('*.json')), [])
        self.assertTrue(runner.alive(owner['supervisor']))

    def test_probe_pins_captured_owner_generation(self):
        self.spawn()
        real_request = runner.request
        def changed_owner(root, command, timeout, probe_token=None, expected_owner_token=None):
            self.assertIsNotNone(expected_owner_token)
            return real_request(root, command, timeout, probe_token, 'previous-generation')
        with patch.object(runner, 'request', side_effect=changed_owner):
            with self.assertRaisesRegex(RuntimeError, 'owner changed'):
                self.probe('PASS')
        self.assertEqual(list((self.root / '.testlab' / 'queue').glob('*.json')), [])

    def test_stop_pins_captured_owner_generation(self):
        child = self.spawn()
        real_request = runner.request
        def changed_owner(root, command, timeout, probe_token=None, expected_owner_token=None):
            self.assertIsNotNone(expected_owner_token)
            return real_request(root, command, timeout, probe_token, 'previous-generation')
        with patch.object(runner, 'request', side_effect=changed_owner), patch.object(sys, 'argv', ['runner.py', 'stop', '--instance', str(self.root)]), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(runner.main(), runner.EXIT_CONFIG)
        self.assertIsNone(child.poll())
        self.assertEqual(list((self.root / '.testlab' / 'queue').glob('*.json')), [])

    def test_command_ack_is_not_probe_pass(self):
        self.spawn()
        self.assertTrue(runner.request(self.root, 'say only-accepted', 3)['ok'])
        with self.assertRaisesRegex(ValueError, 'probe requires'):
            runner.probe(argparse.Namespace(instance=self.root, command=['say only-accepted'], timeout=.2))


if __name__ == '__main__':
    unittest.main()
